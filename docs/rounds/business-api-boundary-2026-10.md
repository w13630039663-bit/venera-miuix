# 业务 API 固定化（2026-10-03 起）

> 目标（用户原话）：**把 Venera 的"业务 API"固定下来，让 UI 不再直接穿透到各种 Manager、DAO、网络和上游 Source。**
>
> 本轮整改的是 `responsibility-dependency-audit-2026-10-03.md` §二 违规表第 8 行「UI 直连基础设施」。
> 那轮审计把它推给了 Part B，理由是「拆它们要动渲染时序」（§七 `:237`）；本方案的答案是
> **转发而不改时序** —— 手法不是新发明的，是 `data/db/LocalFavoritesManager.kt:829-836`
> （「时机与改造前完全一致」）与 `data/platform/android/AndroidKeyValueStore.kt:60-62`
> （「不改它们的签名，就不动 UI 层」）在这个仓里已经落地过两次的同一条口径。

## 一、现状读数（2026-10-03 实测，逐条可复算）

| 判据 | 实测 | 复核式 |
|---|---|---|
| UI 目录的 `*.getInstance` 命中 | **153 行 / 53 颗文件**；扣掉 3 行平台 API（`feature/sourcemanage/WebLoginScreen.kt:130,230` 的 `android.webkit.CookieManager`、`components/backdrop/Coroutines.kt:14` 的 `Choreographer`）⇒ **业务穿透 150 处 / 51 颗文件** | `grep -rEn '[A-Za-z]+\.getInstance' app/src/main/java/com/venera/compose/{feature,gallery/ui,reader,components} --include=*.kt` |
| 逐目录 | `feature` 76/37 · `gallery/ui` 63/11 · `reader` 9/2 · `components` 2/1 | 同上，逐目录跑 |
| UI import 实现类 | **125 条 / 56 颗文件 / 33 个符号**（`VeneraPreferences` 29、`ComicSourceManager` 13、`ContentGuardManager` 12、`LocalFavoritesManager` 7…） | 见 `BusinessApiBaseline.kt` 的 `BASELINE_IMPORT` |
| 非 `getInstance` 的直连 | `AndroidKeyValueStore(` 3、`HostCircuitBreaker.` 3、`okhttp3.` 1、`PreferredIpRuntime.` 5、`ComicStorageRoot.` 15 | 同上，`BASELINE_*` 五张表 |
| 全仓 `fun getInstance(` | 35 处声明 | `grep -rn "fun getInstance(" --include=*.kt app/src/main` |
| ViewModel | 12 颗 `AndroidViewModel(app)` + 3 颗裸 `ViewModel()`（`feature/explore/ExploreViewModel.kt:25`、`:91`、`feature/Navigation.kt:322`，三颗今天就零穿透）；`viewModel()` 真调用点 18 处（另有 3 处在注释里）；**全仓零 `ViewModelProvider.Factory`** | `grep -rnE "^class [A-Za-z]+ViewModel" --include=*.kt app/src` |
| 测试基线 | 101 颗 `.kt`（100 测试类 + `testsupport/RepoSources.kt`）/ 791 个 `@Test` | `find app/src/test -name '*.kt' \| wc -l`；`grep -rho '@Test' app/src/test \| wc -l` |

**冻结面**：`FREEZE-STATEMENT.md`（在**仓根**，`docs/` 下没有）`:8-15` 的清单实测 **8 颗**。冻结屏内含穿透 **13 处**，导航保护域（`:32`）另含 **2 处** = **15 处待解锁**，见 §六。

## 二、门面形状

```
app/src/main/java/com/venera/compose/data/api/          ← 漫画侧契约（本轮唯一新顶层子包）
    BusinessPorts.kt          端口容器 + install(platform, factory) / of(handle: Any?)
    ContentGuardApi.kt        ContentGuard(9) + GuardRuleBook(3)
    LibraryApi.kt             ReadingHistory(5) + ReadingStats(5)
    NetworkApi.kt             NetworkHygiene(4) + HttpTextFetch(1)
    android/AndroidBusinessPorts.kt   全部适配器 + install()
```

**契约宽度由消费面决定**，不由实现决定 —— 抄 `data/db/DatabasePorts.kt:32-44` 的 `FavoritesPreferences`（只暴露 3 个 getter 而不是整个 `VeneraPreferences`）：`ContentGuardManager` 公开面 16 个成员，契约只留 UI 真要问的 9 个，`loadRules()`（只被自己调）、`registerSourceNameAliases()`（只有装配根 `VeneraApp.kt:46` 一带）、`isComicBlocked()`（UI 零命中）三颗**不收**，收进来就等于没做收口。

三条形状纪律：

1. **`of(handle: Any?)` 的句柄只能是 `Any?`**（抄 `LocalFavoritesManager.kt:836`）—— 本层不出现 `android.content.Context`，句柄原样交给平台装的 factory 解释。改造因此是**同一行的取用表达式替换**：`XxxManager.getInstance(ctx)` → `BusinessPorts.of(ctx).<契约>`，`remember{}` 边界一个不加不减。
2. **`object` 里不存 Context**。若改成 `install(app)` 后提供无参全局口，就是**用一条新的进程级 Context 持有**去换掉 150 条旧穿透 —— 正是审计 §二 第 9 行批评的形态。
3. **适配器的构造不许调 `getInstance`**，只在成员方法体里调（`private val manager get() = XxxManager.getInstance(context)`）⇒「谁第一次真正用到才建那颗单例」的时机与改造前逐点相同，`VeneraApp.kt:31-38` 的 `StartupTrace` 冷启动读数不换人付账。

**落点为什么不选别处**：
- 不放 `data/platform/` 或 `data/db/` —— 那两棵树整体在 `:desktop` 的 `srcDir` 编译面里（`desktop/build.gradle.kts:21,43`），契约落进去就必须先让每个被引用的类型桌面可编，等于把 Part B 的 W1/W2 类型搬家原地吸进本轮；而 `include` 收不回范围（同文件 `:39-42` 记过「滤错文件仍 BUILD SUCCESSFUL」的静默假绿）。本层的 `android/` 子包布局照 `data/platform/android/`，将来真要共享只需加一行 `srcDir` 并按子包点名排除实现那一半。
- 不并进 `components/` —— 那是 UI 层，而这里是给 UI 用的业务口。
- 附带收益：**本任务一行不动 `desktop/build.gradle.kts`** ⇒ 天然满足 Part B 的共同前置（审计 `:224`「不许与 Windows 线并行编辑那颗文件」）。

**不用 CompositionLocal**（五条理由，都对着仓内实测）：① 本仓 11 颗 Local 里 4 颗注入过行为对象（`LocalCoverTransitionScopes`、`LocalGlassBackdrop`、`LocalTopBarBackdrop`、`LocalLiquidBottomTabScale`），但语义全是「读不到就退回普通渲染」的可空降级（`components/ComicSharedTransition.kt:71-73`），而业务端口**没有合法的「读不到」退路**（`DatabasePorts.kt:90-99`：不许返回 null 让上层拿空库继续跑）；② 两处根部 provider 一处是主题根（`feature/VeneraTheme.kt:124-133`）、一处是导航保护域（`feature/Navigation.kt:592`）；③ 换成 Local 后源码扫描失去牙齿 —— 读取发生在没有任何 provider 的文件里，钉不住；④ `compositionLocalOf` 换 provide 触发全部读取方重组、`staticCompositionLocalOf` 换值不触发重组，**两条路都是新的重组时序**；⑤ 业务端口无预览实现，所有 `@Preview` 要造 stub = 新增一整个面。

**VM 侧本轮不做构造注入**，改成字段一次性持有契约（`private val api = BusinessPorts.of(application)` + `private val prefs: ComicPreferences = api.comicPrefs`）。三条理由：18 处 `viewModel()` 里 **7 处落在冻结屏**（而 VM 文件本身一颗都不在冻结清单里 ⇒ 改动留在 VM 内是零豁免成本）；3 颗裸 `ViewModel()` 今天已零穿透；全仓零 `ViewModelProvider.Factory`，引入它等于新造一整个 DI 形状。**病因分两段**：①「实现类的名字出现在消费层」（125 行 import）本轮治；②「对象来自进程全局」只有构造注入能治，改完之后一颗 VM 的依赖恰好等于它字段声明里那几颗契约类型 —— **「能不能一眼列出依赖」就是本轮与下一轮的分界**。

## 三、批次（B0 已落地）

| 批 | 内容 | 点位 | 状态 |
|---|---|---|---|
| **B0** | 骨架 + 守卫：`data/api/` 三颗契约文件 + 容器 + 适配层、`VeneraApp.kt` 一行 install、`BusinessApiBoundaryTest`（A-F 六条断言，白名单 = 当前全量） | 改引 **0** 处 | ✅ 已落地 |
| B1 | `ContentGuard` + `GuardRuleBook` 改引（23 处 − 9 处冻结 = 14） | 14 | 待做 |
| B2 | 偏好四颗契约（`PreferencesApi.kt`：Reader/Appearance/Comic + 画廊 `GalleryPreferences`）+ 3 处 `AndroidKeyValueStore(` 归位 | 24 | 待做 |
| B3 | `SourceApi.kt`（`ComicContentApi` + `SourceCatalog`）+ 收 `SearchViewModel.kt:166` 的 `as? JsComicSource` | 12 | 待做 |
| B4 | `ReadingHistory` + `ReadingStats`，含审计点名的 `reader/VeneraReaderScreen.kt:306,446` | 7 | 待做 |
| B5 | `FavoriteLibrary` + `OfflineLibrary`（契约随本批与守卫同批落地） | 18 | 待做，**允许整批砍掉** |
| B6 | `NetworkHygiene` + `ComicSourceViewModel.kt:569-571` 裸 okhttp 的 `HttpTextFetch` 外科手术 | 5 | 待做 |
| B7' | 画廊六颗契约（`gallery/data/GalleryPorts.kt`，含三腿 `when(site)` 表四遍→一遍） | 57 | 待做 |

**契约与守卫同批**：B2/B3/B5/B7' 的新契约文件与它们的白名单缩短在**同一颗提交**里 —— 沿用审计 `:230` 那条已写下的纪律「守卫用例必须和被守卫的动作同批落地，否则现在建它等于埋一条常红用例」。

**验收线**：业务穿透 `150 → 15`（甲裁决下的终值，**不是 0**）；静态取用的出处 `51 颗文件（+ MainActivity.kt）→ 装配根 + 适配层 + 实现类自身`；`BusinessApiBoundaryTest` 的四张白名单逐批只许缩短；`:app:testDebugUnitTest` 用例数只增不减。

## 四、守卫用例 `app/src/test/java/com/venera/compose/data/api/BusinessApiBoundaryTest.kt`

六条断言，底座是 `testsupport/RepoSources.kt` 的源码扫描（**定位不到仓根就 fail 并打印尝试过的路径**，`RepoSources.kt:11-13`：扫描类用例静默通过等于没有用例）：

| 断言 | 钉住的不变量 | B0 基线 |
|---|---|---|
| **A** | UI（四棵目录 + `MainActivity.kt`）不许 import 业务实现类 | 57 颗文件 / 128 条 |
| **B** | UI 不许 `实现类.getInstance`，含全限定内联（`feature/FavoritesScreen.kt:795` 那种写法） | 52 颗文件 / 152 处 |
| **C** | 不许把实现类当**类型**用（参数、字段、`is`/`as?`）—— 单行扫 `getInstance` 永远抓不到这一类 | 33 颗文件 / 49 条 |
| **D** | 非 `getInstance` 的直连五张名单（KeyValueStore / 熔断 / okhttp3 / 优选 IP / 存储根） | 3+3+1+5+15 处 |
| **E** | 全仓静态取用的出处必须可数（装配根 / 适配层 / 基础设施目录 / 自身声明该 `getInstance` 的文件 / 存量名单），且**存量名单清空了却不删也红** | 52 颗 |
| **F** | **白名单不许有无主条目**：每条要么归某一批，要么写明「解锁条件」，且条件必须带可复核的锚（`文件.kt:行号` / `W1..W6` / `FREEZE-STATEMENT`） | 全量 |

三个已知坑都防住了（上一轮 A3/A5 首跑就是红在这三条上）：① 声明行不会命中（判据以 `.getInstance(` 与 `^import ` 为锚）；② 整行注释与 **尾随注释**都先切掉再判（`PreferenceStorageNamesTest.kt:80-87` 缺的正是第二步）；③ 跨两行的写法由 C 承担，它不依赖与 `getInstance` 同行。

覆盖面如实声明：`RepoSources` 只看 `app/src/main/java`，**扫不到 `desktop/src`、`app/src/test`、`res` 目录**。`desktop/src` 今天不含 UI，那一侧由 `:desktop:compileKotlin` 兜住。

## 五、读数回写

### B0（2026-10-03）

- 新增 `data/api/{BusinessPorts,ContentGuardApi,LibraryApi,NetworkApi}.kt` + `data/api/android/AndroidBusinessPorts.kt`（六颗契约、七颗适配器，全部一行转发）；`VeneraApp.kt:31-35` 加一行 `AndroidBusinessPorts.install()`（排在 `:37` 那颗 `VeneraNetworkClient.getInstance` **之前**，否则改造过的取用会撞上"未接线 ⇒ 抛"）。**改引 0 处**，没有一行 UI 代码被改。
- 新增 `BusinessApiBoundaryTest.kt` + `BusinessApiBaseline.kt`（基线与归属表）。
- `RepoSources` **未改**：六条断言只用现成的 `allMainLines()` 与 `kotlinFiles()`，不需要新访问器。
- 用例首跑红了 5 条，全部是**用例自己的错**而不是代码的错，逐条记：① B 忘了把行号拼进 key（基线是 `Sym:line`，扫描只给了 `Sym`）；② A 少登记了 `feature/sourcemanage/*` 的 `JsComicSource` import；③ C 的符号表来自归属表，比生成脚本多 `HostCircuitBreaker` 与 `JsComicSource` 两颗，多出 4 条真实命中（含 `MainActivity.kt:97` 那句诊断日志里的 `is JsComicSource`）；④ E 原设计只放过「基础设施目录 + 自身声明」，但 B0 不改引 ⇒ 51 颗 UI 文件全在命里，改成「存量名单 + 名单清空不删也红」；⑤ F 抓到 6 条长期条目的解锁条件**没有可复核的锚**（只写了文件名没写行号），逐条补上真实点位。
- `:app:compileDebugKotlin`、`:app:testDebugUnitTest --tests "*BusinessApiBoundaryTest*" --rerun-tasks`（**6 tests / 0 failures / 0 errors**，强制重跑非 UP-TO-DATE）、全套读数见提交信息。

## 六、那 15 处为什么不收（已裁决：甲）

`FREEZE-STATEMENT.md:17-19` 写的是「允许修实际 Bug、修明确回归；**禁止架构重构**、顺手拆文件」，`:2493` 还记着「用户明确不给豁免」的先例。本轮按**甲档**执行：非冻结的 135 处照收，冻结屏 13 处 + 导航保护域 2 处**留在白名单**，但每条都在 `BusinessApiBaseline.kt` 的 `SITE_OVERRIDE` 里写明**解锁条件**（这是断言 F 的检查对象，不是给人看的注释）。

| 点位 | 归哪颗契约 | 解锁条件 |
|---|---|---|
| `UnifiedExploreScreen:123,124`、`SourceSectionScreen:104,105`、`HomeScreen:134,610,796`、`SearchScreen:136,138`、`FavoritesScreen:795,913`、`HistoryScreen:269`、`NetworkFavoritesScreen:653`（13 处） | B1 `ContentGuard` / B2 `ComicPreferences` / B3 `SourceCatalog` 都已覆盖 | **只差一次点名豁免，零技术前提**：契约与适配器已就绪，这些行是同一行的取用替换。入场券已逐颗核实（`applicationContext` 归一：`ContentGuardManager.kt:511`、`ComicSourceManager.kt:1448`、`VeneraPreferences.kt:707`；`StateFlow` 交回同一实例；不加减 `remember`） |
| `feature/Navigation.kt:491`（`navigationBarStyle` + `startPage`） | B2 `AppearancePreferences` | 同文件改动按保护域口径需**重新评审一次**（`FREEZE-STATEMENT.md:32` 只管 Tab 枚举顺序 / 路由映射 / 顶栏齿轮入口这三项，本行不在其内） |
| `feature/Navigation.kt:286`（`ComicLinkResolver`） | **本轮连契约都不立** | **两段前提，缺一段做不动**：① 同上保护域重新评审；② 硬技术前提 = `ComicLinkResolver.Outcome` 是**嵌套在吃 `Context` 的类体里的 `sealed interface`**（`source/ComicLinkResolver.kt:3,23,25-36`），而 `feature/Navigation.kt:313` 逐条消费那四档 ⇒ 必须先做一次 W1 同族的类型搬家，本行才有契约可引 |

**将来兑现解锁时的豁免记录形状**（照 `:51-63` 与 `:78` 的真实先例：**点名 + 明列未动项**）—— 未动清单至少要含：探索闭环判定链（`:23`）、`ContentGuardManager.kt` 本体（仍 FROZEN，从外面包、未进一颗字符）、打码与分页、行级虚拟化契约、手风琴状态机、下拉刷新与续页闸门、顶栏大标题折叠 + 毛玻璃页内自治、`statusBarTop + topBarFloor` 地板、`LocalBottomBarClearance` 底栏避让、Tab 枚举顺序与路由映射表与顶栏齿轮入口。

## 七、明确不做（12 条，每条给判据）

1. **`feature/sourcemanage/ComicSourceViewModel.kt` 的 33 处**（源安装 / 仓库同步 / 源脚本编辑 / Web 登录 Cookie / 逐源测试）—— 它实吃 `ComicSourceManager` 44 个公开成员里的 **32 个** ⇒ **契约宽度≈实现宽度**，窄接口在这颗上不成立；且源管理页**就是来操作基础设施的界面**，要保护的是消费业务的页面。**唯一例外**：`:569-571` 那笔裸 okhttp（B6 用 `HttpTextFetch` 外科手术收掉 —— 它绕过了 cookie jar / 限流 / 熔断 / 缓存四件套，是可指认的排障成本，不是架构偏好）。解锁：与 W2 同批。
2. **`FollowUpdatesRepository`**（`feature/FollowUpdatesViewModel.kt:19`）—— 已被 W6 点名（构造参数注入 + 三行 exclude 一行不撤）；再包一层会让同一 DAO 成员被转发两次。
3. **`PreferredIpRuntime`（5 处）/ `PreferredIpProbe`** —— 它不是 §二 #8，是 **§二 #9（进程级可变全局）**：`data/network/PreferredIpRuntime.kt:24,41-52` 两颗 `@Volatile var`，`configure/recordProbe/demote/clearReadings/targetFor` 全是静态突变。加转发接口 = 给 #9 套层包装然后结案，全局状态一个字节没少。
4. **`ComicStorageRoot`（15 处）** —— 已是单源对象、审计未列为重复；**落点上一轮刚裁过**（A3 提交信息：`download/ComicStorageRoot` 是 `gallery/data/GallerySaver.kt:11` 已欠的边，不再加一条）；15 处集中在 `feature/settings/AppSettings.kt` 一颗文件，不构成散落。
5. **`BackupManager` + `WebDavSyncManager`**（`feature/SyncBackupScreen.kt:57,58`）—— 消费面 2 处对成员 7 枚，无收效；`sync/BackupManager.kt:19-20` 持画廊两颗 store，端口要么跨侧要么 import feature；且备份是**用户数据面**，出错代价是丢数据。
6. **`FavoriteImagesManager`（2 处）** —— 返回类型 `FavoriteImageItem` 声明在 `feature/favoriteimages/FavoriteImagesManager.kt:92` ⇒ 契约要 import `feature.`，正是 W3 要禁的边。解锁：与 W1 同批搬家。
7. **`TagTranslationManager`（2）与 `ChineseVariantConverter`（1）** —— `data/tags/TagTranslationManager.kt` 是并行线在飞的脏文件，碰它 = 抢改。
8. **`GelbooruAccount`（4）/ `SauceNaoAccount`（2）** —— 3 处在漫画侧，建契约就新增 `feature → gallery` 边；审计 `:39,:43` 的隔离裁决不豁免凭据。画廊内 2 处 B7' 顺路收成一枚派生的「凭据已配置」判据。
9. **`GalleryFeedSource` 的类型收口（2 处）** —— 它今天已是聚合端口（三站并发 + 每站时间预算 + `GalleryLegGuard` 都在里面），只做装配收口。
10. **`engine/*`** —— UI 目录实测 0 处 `getInstance`，没有可治的穿透点。
11. **把 `BusinessPorts` provide 进 CompositionLocal** —— §二 五条理由。
12. **12 颗 VM 的构造注入 / 第一颗 `ViewModelProvider.Factory`** —— §二 三条理由，硬约束是 7/18 调用点在冻结屏内。

## 八、与 Part B（W1-W6）的关系

- **W1/W2 是本方案 4 条「不收」的唯一先决条件**：`InstalledSourceMeta`（`source/ComicSourceManager.kt:61`）、`FavoriteImageItem`、`ComicLinkResolver.Outcome` 搬进 `source/model` 之后，§2.2 的「故意不收」与 §七.1/§六 的第三行当场可摘 —— 这是 W1/W2 收益的具体化。
- **W3（`LayeringEdgeTest`）** 与本方案的守卫**同源不同向**（那条管「下层不许向上层伸手」，这条管「上层不许直连下层实现」），**必须共享同一份实现类符号表**；本方案先落地会让 W3 的基线从「几乎全仓」缩到可指名的一小撮。
- **W5**：本方案把 `GalleryPreferences` 的**接口**先立在 `gallery/data/GalleryPorts.kt`，W5 落实现类时接上它 —— 两批共用「存储名与键名逐字不变 + 化石用例」的同一条口径。
- **W6**：`FollowUpdatesRepository` 让路给它的构造注入，本方案不碰。
- 共同前置自动满足：**一行不动 `desktop/build.gradle.kts`**。
