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
| 测试基线 | 裁决当天 101 颗 `.kt`（100 测试类 + `testsupport/RepoSources.kt`）/ 791 个 `@Test`；**B0 起每批加守卫类，B6 落地后实测 103 颗 `.kt` / 101 颗测试类 / 797 个用例** | `find app/src/test -name '*.kt' \| wc -l`；`grep -rho '@Test' app/src/test \| wc -l` |

**冻结面**：`FREEZE-STATEMENT.md`（在**仓根**，`docs/` 下没有）`:8-15` 的清单实测 **8 颗**。冻结屏内含穿透 **13 处**，导航保护域（`:32`）另含 **2 处** = 裁决时登记的 **15 处待解锁**，见 §六；B2 之后画廊偏好写口再加 **2 处**（W5）⇒ 长期条目共 **19 处**（B3 再加 2 处 W1 嵌套类型，见 §五 B3 与 §六）。

## 二、门面形状

```
app/src/main/java/com/venera/compose/data/api/          ← 漫画侧契约（本轮唯一新顶层子包）
    BusinessPorts.kt          端口容器 + install(platform, factory) / of(handle: Any?)
    ContentGuardApi.kt        ContentGuard(9) + GuardRuleBook(3)                      【B1】
    PreferencesApi.kt         Reader(20) + Appearance(20) + Comic(15) + Network(14)
                              + NamedStoreFactory(1)                                  【B2】
    LibraryApi.kt             ReadingHistory(5) + ReadingStats(5)                          【B4】
    SourceApi.kt              ComicContentApi(12) + SourceCatalog(8)                     【B3】
    NetworkApi.kt             NetworkHygiene(4) + HttpTextFetch(1)
    android/AndroidBusinessPorts.kt   全部适配器 + install()
app/src/main/java/com/venera/compose/gallery/data/
    GalleryPorts.kt           画廊自持：GalleryContentGuard(4) + GalleryPreferences(12，getter-only)
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

## 三、批次（B0–B4、B6 已落地）

| 批 | 内容 | 点位 | 状态 |
|---|---|---|---|
| **B0** | 骨架 + 守卫：`data/api/` 三颗契约文件 + 容器 + 适配层、`VeneraApp.kt` 一行 install、`BusinessApiBoundaryTest`（A-F 六条断言，白名单 = 当前全量） | 改引 **0** 处 | ✅ 已落地 |
| **B1** | `ContentGuard` + `GuardRuleBook` 改引（23 处 − 9 处冻结 = 14），画廊侧同时立 `gallery/data/GalleryPorts.kt` 那颗自持的 `GalleryContentGuard` | 14 | ✅ 已落地 |
| **B2** | 偏好五颗契约（`PreferencesApi.kt`：`Reader` / `Appearance` / `Comic` / `Network` + `stores`；画廊侧 `GalleryPreferences` 落在 `gallery/data/GalleryPorts.kt`，getter-only） | 23 + 2 处 `AndroidKeyValueStore(` | ✅ 已落地 |
| **B3** | `SourceApi.kt`（`ComicContentApi` 12 枚 + `SourceCatalog` 8 枚）+ 收 `feature/SearchViewModel.kt:167` 的 `as? JsComicSource`（收成 `tagSuggestionKeyword`） | 12 | ✅ 已落地 |
| **B4** | `ReadingHistory` + `ReadingStats`，含审计点名的 `reader/VeneraReaderScreen.kt:306,446` | 7 | ✅ 已落地 |
| B5 | `FavoriteLibrary` + `OfflineLibrary`（契约随本批与守卫同批落地） | 18 | 待做，**允许整批砍掉** |
| **B6** | `NetworkHygiene`（补 `httpCacheSizeBytes` / `clearHttpCache` 两枚）+ `ComicSourceViewModel.kt:569-571` 裸 okhttp 的 `HttpTextFetch` 外科手术 | 5 | ✅ 已落地 | |
| B7' | 画廊六颗契约（`gallery/data/GalleryPorts.kt`，含三腿 `when(site)` 表四遍→一遍） | 57 | 待做 |

**契约与守卫同批**：B2/B3/B5/B7' 的新契约文件与它们的白名单缩短在**同一颗提交**里 —— 沿用审计 `:230` 那条已写下的纪律「守卫用例必须和被守卫的动作同批落地，否则现在建它等于埋一条常红用例」。

**验收线**：业务穿透 `150 → 15`（甲裁决下的终值，**不是 0**）；静态取用的出处 `51 颗文件（+ MainActivity.kt）→ 装配根 + 适配层 + 实现类自身`；`BusinessApiBoundaryTest` 的四张白名单逐批只许缩短；`:app:testDebugUnitTest` 用例数只增不减。

**进度（B6 落地后）**：业务穿透 **93 处 / 38 颗文件**（起点 150 / 51），实现类 import **77 条 / 39 颗**（起点 125 / 56），类型引用位 **32 条 / 21 颗**（起点 49 / 33）。剩 B5（收藏库与下载本地 18 处，方案 §七 与 §三 都写着**允许整批砍掉**）与 B7'（画廊 57 处，含三腿 `when(site)` 表四遍→一遍），两批都需要真机验收；设备当前未连接（`adb devices` 空列表），已挂账。

## 四、守卫用例 `app/src/test/java/com/venera/compose/data/api/BusinessApiBoundaryTest.kt`

六条断言，底座是 `testsupport/RepoSources.kt` 的源码扫描（**定位不到仓根就 fail 并打印尝试过的路径**，`RepoSources.kt:11-13`：扫描类用例静默通过等于没有用例）：

| 断言 | 钉住的不变量 | B0 基线 | B1 后 | B2 后 | B3 后 | B4 后 | B6 后 |
|---|---|---|---|---|---|---|---|
| **A** | UI（四棵目录 + `MainActivity.kt`）不许 import 业务实现类 | 57 颗文件 / 128 条 | 56 / 119 | 45 / 91 | 42 / 85 | 40 / 79 | **39 / 77** |
| **B** | UI 不许 `实现类.getInstance`，含全限定内联（`feature/FavoritesScreen.kt:795` 那种写法） | 52 颗文件 / 152 处 | 50 / 138 | 45 / 115 | 41 / 103 | 39 / 96 | **38 / 93** |
| **C** | 不许把实现类当**类型**用（参数、字段、`is`/`as?`）—— 单行扫 `getInstance` 永远抓不到这一类 | 33 颗文件 / 49 条 | 32 / 48 | 23 / 36 | 22 / 33 | 22 / 33 | **21 / 32** |
| **D** | 非 `getInstance` 的直连五张名单（KeyValueStore / 熔断 / okhttp3 / 优选 IP / 存储根） | 3+3+1+5+15 处 | 同值 | 1+3+1+5+15 处 | 同值 | 同值 | **1+2+0+5+15 处** |
| **E** | 全仓静态取用的出处必须可数（装配根 / 适配层 / 基础设施目录 / 自身声明该 `getInstance` 的文件 / 存量名单），且**存量名单清空了却不删也红** | 52 颗 | 50 颗 | 45 颗 | 41 颗 | 39 颗 | **38 颗** |
| **F** | **白名单不许有无主条目**：每条要么归某一批，要么写明「解锁条件」，且条件必须带可复核的锚（`文件.kt:行号` / `W1..W6` / `FREEZE-STATEMENT`） | 全量 | 全量 | 全量 | 全量 | 全量 | 全量 |

三个已知坑都防住了（上一轮 A3/A5 首跑就是红在这三条上）：① 声明行不会命中（判据以 `.getInstance(` 与 `^import ` 为锚）；② 整行注释与 **尾随注释**都先切掉再判（`PreferenceStorageNamesTest.kt:80-87` 缺的正是第二步）；③ 跨两行的写法由 C 承担，它不依赖与 `getInstance` 同行。

覆盖面如实声明：`RepoSources` 只看 `app/src/main/java`，**扫不到 `desktop/src`、`app/src/test`、`res` 目录**。`desktop/src` 今天不含 UI，那一侧由 `:desktop:compileKotlin` 兜住。

## 五、读数回写

### B0（2026-10-03）

- 新增 `data/api/{BusinessPorts,ContentGuardApi,LibraryApi,NetworkApi}.kt` + `data/api/android/AndroidBusinessPorts.kt`（六颗契约、七颗适配器，全部一行转发）；`VeneraApp.kt:31-35` 加一行 `AndroidBusinessPorts.install()`（排在 `:37` 那颗 `VeneraNetworkClient.getInstance` **之前**，否则改造过的取用会撞上"未接线 ⇒ 抛"）。**改引 0 处**，没有一行 UI 代码被改。
- 新增 `BusinessApiBoundaryTest.kt` + `BusinessApiBaseline.kt`（基线与归属表）。
- `RepoSources` **未改**：六条断言只用现成的 `allMainLines()` 与 `kotlinFiles()`，不需要新访问器。
- 用例首跑红了 5 条，全部是**用例自己的错**而不是代码的错，逐条记：① B 忘了把行号拼进 key（基线是 `Sym:line`，扫描只给了 `Sym`）；② A 少登记了 `feature/sourcemanage/*` 的 `JsComicSource` import；③ C 的符号表来自归属表，比生成脚本多 `HostCircuitBreaker` 与 `JsComicSource` 两颗，多出 4 条真实命中（含 `MainActivity.kt:97` 那句诊断日志里的 `is JsComicSource`）；④ E 原设计只放过「基础设施目录 + 自身声明」，但 B0 不改引 ⇒ 51 颗 UI 文件全在命里，改成「存量名单 + 名单清空不删也红」；⑤ F 抓到 6 条长期条目的解锁条件**没有可复核的锚**（只写了文件名没写行号），逐条补上真实点位。
- `:app:compileDebugKotlin`、`:app:testDebugUnitTest --tests "*BusinessApiBoundaryTest*" --rerun-tasks`（**6 tests / 0 failures / 0 errors**，强制重跑非 UP-TO-DATE）、全套读数见提交信息。

### B1（2026-10-03）

**改引 14 处**（B 的站点总数 152 → **138**，A 128 → **119**，C 49 → **48**，E 出处 52 → **50 颗**）：漫画侧 9 处取 `BusinessPorts.of(ctx).contentGuard` / `.guardRuleBook`，画廊侧 6 处取 `GalleryPorts.of(ctx).contentGuard`（另 1 处是 `BlockingSettings` 里同一颗 ruleBook 被两个 `remember` 各取一次，所以新增的 `of(...)` 行比减掉的 `getInstance` 多一枚）。

| 落点 | 从 | 到 |
|---|---|---|
| `components/ComicCardContextMenu.kt:93` | `ContentGuardManager.getInstance(ctx).addRule(…)` | `BusinessPorts.of(context).guardRuleBook.addRule("COMIC_ID", comic.id)`（`>= 0` 的成功判据逐字保留） |
| `feature/ComicDetailScreen.kt:152,373` | 同上 + `getInstance` 后 `maskStateFor` | `guardRuleBook` / `contentGuard`，`detailMaskState(guard: ContentGuard, …)` 的参数类型随之从实现类换成契约 |
| `feature/FollowUpdatesScreen.kt:220` | 全限定内联 `com.venera.compose.security.guard.ContentGuardManager.getInstance(…)` | `BusinessPorts.of(LocalContext.current).contentGuard` |
| `feature/HomeViewModel.kt:208`、`feature/SearchViewModel.kt:84` | 字段/局部变量拿实现类 | `private val guardManager: ContentGuard = BusinessPorts.of(app).contentGuard`（VM 从这一天起字段里只有契约类型） |
| `feature/settings/BlockingSettings.kt:22,23,71` | `guard` + `manager`（规则本体的增删查） | `contentGuard` + `ruleBook: GuardRuleBook`，`rules by ruleBook.rules` |
| `gallery/ui/{GalleryArtistProfileScreen:146, GalleryDailyScreen:85, GalleryFavoritesBody, GalleryInfoSheet, GalleryPostScreen, GalleryScreen}`（含 `GalleryScreen` 的 4 条 `findGalleryBlockedRule` 调用点） | `ContentGuardManager.getInstance(…)` / `findGalleryBlockedRule(` | `GalleryPorts.of(context).contentGuard` / `blockedGalleryRule(` |

**画廊侧为什么不复用漫画侧那颗契约**：隔离口径要的是**各侧自持**（`project-gallery-module-isolation`），共用一个发布者等于让 `gallery/ui` 依赖 `data/api` 的漫画语义。所以 `gallery/data/GalleryPorts.kt` 里只有 UI 真要问的 4 枚成员（`nsfwMaskMode`、`rules`、`blockedGalleryRule`、`addRule`），适配器转发 `ContentGuardManager` 的同一对方法；`VeneraApp.kt` 加第二行 `AndroidGalleryPorts.install()`。它在 `gallery/data/` 目录下，天然落进守卫 E 的 `INFRA_DIRS`，不需要为它开豁免。

**基线形状本轮重做**（B0 那版是我的设计缺陷，不是迁移的代价）：B0 把白名单键写成 `符号:行号`，于是同一文件里加一行 import 就会让**别处**的行号全部漂移、白名单跟着假红。现在改成「文件 → 符号集合」+ 一个站点总数 `SITE_TOTAL_GET_INSTANCE`（D 五张表同理改成「文件 → 处数」），既钉得住「哪颗文件还直连着谁」也钉得住「重复几处」，且不受行号漂移影响 —— 这条写在 `BusinessApiBaseline.kt` 的文件级 KDoc 里，B2 之后每批照此复算。

**用例本轮三处自己的错**（全部是守卫变松，不是代码变红）：① `countsOf` 改签名时残留了上一版的函数体（编译期即红）；② `attribOf` 把键写成字面量 `"path#$sym"` 而不是 `"$path#$sym"` ⇒ 25 条站点覆写（冻结屏 11 + 导航保护域 2 + 画廊内凭据 4 + sourcemanage 4 + favoriteimages 3 + `MainActivity` 1）全部取不到值，甲裁决下逐条写来的解锁条件形同不存在；③ **A 的判据里 `attribOf(rel, sym) == null` 就 `return null`** —— 这是一条假绿口子：新增一颗实现类、只要它不在归属表里，它的 import 就会被静默丢掉，白名单再严也管不到它。现已删掉这一行，改由 `EXCLUDED_SYMBOLS`（`ComicSource` 是接口、`FavoriteItem`/`HistoryRecord`/`GuardRule` 是数据类）**显式**登记端口与真底层，将来漏登记就是红。

**未验（挂账，需用户点页面）**：屏蔽页三档遮罩与 AI 开关的即时生效、加/删规则后列表计数文案、详情页与关注更新列表的封面打码、搜索与首页的 HIDE 过滤、画廊四页（日推/收藏/详情/画师主页）的打码与"已屏蔽"提示。B1 是**同一行取用表达式的替换**，`remember` 边界不加不减、`collectAsState` 订阅对象未换，判据层已由 797 条用例兜住；观感仍要真机过一遍才算完。

### B2（2026-10-03）

**改引 23 处取用**（B 的站点总数 138 → **115**，A 119 → **91**，C 48 → **36**，E 出处 50 → **45 颗**，D 的 `AndroidKeyValueStore(` 3 颗/3 处 → **1 颗/1 处**）：`VeneraPreferences.getInstance` 从 UI 的 25 处降到 4 处（冻结屏 `SearchScreen:138`、保护域 `Navigation:491`、画廊写口那两行见下），`AndroidKeyValueStore(` 漫画侧两颗收进 `stores`。

**契约从四颗变五颗（与原计划的偏差，如实记）**：计划写的是 `Reader`/`Appearance`/`Comic` + 画廊 `GalleryPreferences`。逐颗调用点数完是 **99 枚**成员被 UI 摸（`VeneraPreferences` 公开面 101 枚），三颗漫画侧契约里 `ComicPreferences` 要吞 28 枚（代理三条 + CF 优选四条 + 下载并发 + HTTP 缓存 + 存储根 + 收藏四条 + 搜索默认值 + 防窥 + 检查更新），宽度接近实现的一半 ⇒ 把网络与存储那 14 枚单切一颗 `NetworkPreferences`（消费者正好是 `NetworkSettings`、`PreferredIpSettings`、`PreferredIpSpeedTestScreen`、`AppSettings` 四颗页）。**分域的判据是"照已有的页切"，不是发明分层**：切完四颗的宽度各 20 / 20 / 15 / 14 枚。

| 新契约 | 成员 | 消费面 |
|---|---|---|
| `ReaderPreferences` | 10 getter + 10 setter | `reader/VeneraReaderScreen.kt:182`、`feature/settings/ReaderSettings.kt:18`、`feature/ComicDetailScreen.kt:247`（章节倒序）、`feature/settings/ExploreSettings.kt`（同一枚） |
| `AppearancePreferences` | 10 + 10 | `feature/VeneraTheme.kt:51`、`AppearanceSettings`、`SettingsComponents:294`、`SettingsHome:106,190`、`SettingsQuoteCard:53`、`ExploreSettings`（启动页） |
| `ComicPreferences` | 8 + 7 | `MainActivity:167`（防窥）、`BlockingSettings:24`、`FavoritesViewModel:46`、`FollowUpdatesViewModel:20`、`ComicDetailViewModel:176`、`AboutSection:51`、`LocalFavoritesSettings:13`、`UpdateCheckUi:62` |
| `NetworkPreferences` | 9 + 5 | `AppSettings`（三处参数位）、`NetworkSettings`（两处）、`PreferredIpSettings`（三处）、`PreferredIpSpeedTestScreen:71` |
| `GalleryPreferences`（getter-only，`gallery/data/GalleryPorts.kt`） | 12 getter，**零 setter** | `gallery/ui` 的 10 处取用（`GalleryPostScreen:199`、`GalleryScreen:239,278,282,283,1520,1600`、`GalleryDailyScreen:88,95`、`GalleryFavoritesBody:99`、`GalleryArtistProfileScreen:148`） |
| `NamedStoreFactory`（`stores`） | 1 | `HomeViewModel:138`（推荐快照）、`SearchViewModel:74`（搜索历史）——存储名仍由调用点原样递进（`PREFS_HOME_RECOMMEND_CACHE` / VM 私有 const `PREFS`），收口不改数据文件 |

**为什么画廊那颗是 getter-only，以及因此留下的两行**：逐颗核过写口，15 枚 `setGalleryXxx` 的唯一消费者是 `feature/settings/GallerySettings.kt`（给画廊配开关的漫画侧页），`gallery/ui` 对这 12 枚只读。所以画廊契约按隔离口径立成只读，写口那一页今天仍抓 `VeneraPreferences`（`SettingsHost.kt:140` 的取用 + `GallerySettings.kt:60` 的参数位）—— 这是**两条新的长期条目**，各带 W5 解锁条件（W5 落 `gallery/data/GalleryPreferences.kt` 实现类接上这颗接口，本行随之改引）。§六 的"15 处"因此是 **17 处**。

**守卫新增一条自维持排除**（`BusinessApiBoundaryTest.portInterfaces()`）：本轮交付的契约后缀全都长得像实现类（`ReaderPreferences`、`NetworkPreferences`、`GalleryPreferences`… 命中的正是 `VeneraPreferences` 那条 `Preferences` 后缀），靠手写排除名单等于每落一批就要加几颗名字、漏一颗就是白名单自己假红。判据改成**回源码看它是不是 `interface`**（33 颗接口名自动豁免），A 的实测因此从"含契约 import"的 102 条回到 **91 条**（这 11 条是本轮新写的契约引用，本来就不该算穿透）。数据类（`FavoriteItem`/`HistoryRecord`/`GuardRule`）不是接口，仍走 `EXCLUDED_SYMBOLS`。

**顺带一处必要的外科**：`data/update/AppUpdateChecker.kt:52` 的 `shouldCheckOnStartup(prefs: VeneraPreferences)` 改成 `ComicPreferences` —— 它只有一个调用点（`UpdateCheckUi`），不收窄就会让一颗 infra 文件反过来把实现类类型塞回 UI。

**未验（挂账，与 B1 合并一次真机过）**：设置九页（外观 / 阅读 / 探索 / 图库 / 网络 / 优选 IP / 本地收藏 / 屏蔽 / 关于）每项拨动后即时生效且重启仍在、冷启动落回设定 Tab、阅读器内六条开关（模式/点击翻页/音量键/常亮/柔光/页间距）、详情页章节倒序、防窥开关（截图与最近任务缩略图）、画廊的列数档 / 画质档 / 自动播放 / 双层收起 / AI 两枚，以及首页推荐区冷启动仍能铺上次数据（`stores` 那条换了取用点，存储名未动）。

### B3（2026-10-03）

**改引 12 处**（B 站点 115 → **103**，A 91 → **85**，C 36 → **33**，E 出处 45 → **41 颗**）。`ComicSourceManager` 的公开面 77 枚，UI 真吃的只有 12 枚内容动作 + 8 枚清单动作，其余 30 余枚（装源/卸源/排序/仓库地址/登录态回写）今天全在 `feature/sourcemanage/` 那两颗文件里 —— 它们按 §七.1 判为「契约宽度≈实现宽度」，本轮不收。

| 新契约 | 成员 | 消费面 |
|---|---|---|
| `ComicContentApi` | `search` / `getComicDetails` / `getChapterPages` / `loadThumbnails` / `resolveThumbnailConfigs` / `resolveImageLoadingConfig` / `getSourceExplorations` / `loadExplorePage` / `loadCategoryComics` / `getCategoryComicsOptions` / `loadCategoryRanking` / `tagSuggestionKeyword` | `feature/ComicDetailViewModel`、`feature/HomeViewModel`、`feature/SearchViewModel`、`reader/VeneraReaderScreen`（含 `resolveDynamicPageUrl` 那颗私有函数的参数位）、四颗冻结屏（未迁，宽度已备） |
| `SourceCatalog` | `sourcesFlow` / `activeSourceKey` / `latencyMapFlow` / `availableUpdates` / `getSource` / `searchTargets` / `refreshPings` / `checkUpdates` | `MainActivity`、`feature/NetworkFavoritesViewModel`、`feature/ComicDetailViewModel`、`feature/SearchViewModel`、`feature/settings/ExploreSettings`、`reader/ChapterCommentsSheet` |

三处判据值得单独记：

1. **`resolveImageLoadingConfig` 是量出来的、不是计划里有的**：`reader/VeneraReaderScreen.kt` 有五处经 `ComicSourceManager.getInstance(context)` 把它递进 `resolveDynamicPageUrl`，参数 `nl` / `forceRefresh` 区分「首轮吃缓存」与「上一轮地址已失效必须重解」，适配器逐字转发、默认值留原位。
2. **`checkUpdates` 不参数化仓库地址**：实现那一颗的 `repoUrl` 默认值是它自己的私有常量，而用户在源管理页改的是 `repoUrl` 那条流 —— 契约若开这个参数，调用点传空串就会**绕过**用户设的那份。
3. **`as? JsComicSource` 收成 `tagSuggestionKeyword(sourceKey, namespace, raw)`**：`feature/SearchViewModel.kt:167` 原来是「拿接口 → 强转实现 → 调实现方法」两级，现在类型探测进适配器，UI 侧不再出现 `JsComicSource` 这个实现类名字（返回 null 的判据与原来同一：非 JS 源或源没声明那条规则）。

**两条新的长期条目（W1 那族嵌套类型）**：`feature/SearchViewModel.kt` 六处 `ComicSourceManager.SourceSearchResult`（39,286,299,316,325,327）与 `feature/settings/PreferredIpSpeedTestScreen.kt:88` 的 `installedMeta`（类型 `InstalledSourceMeta` 声明在 `source/ComicSourceManager.kt:61`）。这两颗 data class **嵌在实现类体里**，收进契约签名就等于让 `data/api` 去 import 实现类拿嵌套类型 —— 与本轮「不搬类型」的边界不是一件事，登记为与 W1 同批。§六 的计数因此 **17 → 19 处**。

**未验（挂账，与 B1/B2 合并一次真机过）**：详情页进入与翻页（`getComicDetails` / `getChapterPages` / `loadThumbnails`）、阅读器逐页加载与「下载失败重试要强制重解」那条链（`resolveImageLoadingConfig`）、搜索单源与「全部源」聚合（`searchTargets` / `search`）、点标签联想（Hitomi 那类 `series:` 语法转换，**必须验一次真转换成功**，它是本批唯一改了调用形状的行为面）、首页推荐区（`search` + `awaitSourceRegistered` 的有界等待）、评论面板（`getSource`）、探索设置页源列表。

### B4（2026-10-03）

**改引 7 处**（B 站点 103 → **96**，A 85 → **79**，E 出处 41 → **39 颗**，C 同值）：`HistoryDao` 与 `ReadingStatsManager` 在 UI 的站点**全部清空**。

| 落点 | 从 | 到 |
|---|---|---|
| `feature/ComicDetailViewModel.kt:179`、`feature/HomeViewModel.kt:79` | `HistoryDao.getInstance(app)` | `BusinessPorts.of(app).history` |
| `feature/HistoryViewModel.kt:22` | 同上（那颗句柄名叫 `dao`） | `BusinessPorts.of(application).history` |
| `feature/HomeViewModel.kt:210`、`feature/StatsScreen.kt:72` | `ReadingStatsManager.getInstance(…)` | `BusinessPorts.of(…).stats` |
| `reader/VeneraReaderScreen.kt:306` | `…ReadingStatsManager.getInstance(context).recordSession(` | `BusinessPorts.of(context).stats.recordSession(` |
| `reader/VeneraReaderScreen.kt:446` | `HistoryDao.getInstance(context).saveHistory(` | `BusinessPorts.of(context).history.saveHistory(` |

**这批的验收线是"调度形状一字未动"**：审计 `:237` 当年把阅读器那两处推给 Part B 的理由是「动它们必然改调度时序」，所以入场券写成 diff 里 `LaunchedEffect(` 与 `withContext(NonCancellable + Dispatchers.IO)` 两类行**零命中** —— 实测本批对 `VeneraReaderScreen.kt` 只有三条改动行：删 `HistoryDao` 的 import、306 与 446 各换一次接收者表达式；effect 的 key、`NonCancellable` 的位置、`statsFailureHandler` 那圈 `CoroutineExceptionHandler`（"本次阅读统计未能保存"那句 Toast 的落点）全在原处。

**契约侧同步三处（两处是我自己上一批写错的）**：① `ReadingHistory` 补 `clearAll()` —— B0 的 KDoc 断言它「UI 零命中」是**错的**，`feature/HistoryViewModel.kt:68` 的「清空历史」一直在调它；漏的原因那次复核按句柄名 `historyDao` 扫，而那颗文件里的句柄叫 `dao`。复核式已改成按成员名扫，并把这条错记进 KDoc。② `ReadingHistory.refresh()` 摘掉（真零命中：`.refresh()` 的命中全是 VM 自己的 `vm.refresh()`；`refresh` 是 `saveHistory` / `clearAll` 内部自己走的那一步）。③ `ReadingStats` 的四个读口改回与实现同串（`getSummary` / `getRecent14DaysTrend` / `getTopComics` / `getTagStats`）——这颗契约的参数与返回类型本来就已全中性，改名不换来任何窄化收益，只把「换从哪拿」扩成"再改四个调用点的方法名"，本批 diff 因此只动接收者。

**归因表两处不许删**：`HistoryDao` 与 `ReadingStatsManager` 现在零站点，条目仍保留 —— 断言 C 的 `IMPL_NAMES` 由归属表的键导出，删键等于给未来这两颗实现类的类型引用位开一口漏（这条写在表值里，免得下一个人当垃圾清掉）。

**未验（挂账，与 B1/B2/B3 合并一次真机过）**：读完一章退出后历史页出现该条且顺序正确、历史页单条删除与多选删除、「清空历史」整表清空后的空态文案、统计页四块读数（汇总 / 14 天趋势 / 榜单 / 题材分布）、切「近 30 天 / 近 1 年」只重算题材那一块、阅读器退出时统计写入失败仍弹那句 Toast。

### B6（2026-10-03）

**改引 5 处**（B 站点 96 → **93**，A 79 → **77**，C 33 → **32**，E 出处 39 → **38 颗**，D 的熔断表 3 颗 → **2 颗**、`okhttp3.` 表 1 颗 → **0 颗**）。

**这批含本轮唯一的缺陷修复，不是分层**。`feature/sourcemanage/ComicSourceViewModel.kt:569-571`（从 URL 装源）原来是：

```kotlin
val client = VeneraNetworkClient.getInstance(getApplication())
val req = okhttp3.Request.Builder().url(url).build()
val resp = client.okHttpClient.newCall(req).execute()
val content = resp.body?.string()
if (resp.isSuccessful && !content.isNullOrBlank()) { … } else { Result.failure(Exception("HTTP ${resp.code}: 获取脚本失败")) }
```

它绕过了 `VeneraNetworkClient.kt:46` 那一整套装配（`PersistentCookieJar`、限流拦截器、熔断拦截器、HTTP 缓存），后果可指认：从仓库 URL 装源拿不到 cookie、不受限流保护，失败原因被 okhttp 的原始异常替掉。现在走 `BusinessPorts.of(app).httpText.fetchText(url)`，判据逐条对拍：

- `ok = isSuccessful && !body.isNullOrBlank()` 就写在适配器里（`NetworkApi.kt` 的语义第 2 条），与原来那两个条件同义，**没有把 `ok`/`code` 吞成 `String`**；
- 失败分支仍拼 `HTTP ${resp.code}: 获取脚本失败`，一字未动；
- 同步 `execute()` 仍留在调用方那层 `withContext(Dispatchers.IO)` 里（语义第 1 条：实现里再切一次上下文就是凭空多一跳）；
- 抛出的异常仍被同一个 `catch (e: Exception)` 接住 → `Result.failure(e)`。

**`NetworkHygiene` 因此比 B0 立的宽两枚**：`feature/settings/AppSettings.kt` 那三行（缓存读数 / 清除缓存 / 重建客户端）里前两枚当时不在契约上。补进来时保持**非 suspend** —— 调用点自己包着 `withContext(Dispatchers.IO)`，改成 suspend 就是换调度位置。`rebuildClient()` → `rebuildHttpClient()` 是本批唯一改了方法名的调用点（两处：`AppSettings.kt:192`、`NetworkSettings.kt:148`）。

**为什么 `HostCircuitBreaker` 那两页不归本批**：`gallery/ui/GalleryDailyScreen.kt:107` 与 `GalleryScreen.kt:400` 的「刷新清熔断」按各侧自持要落 `gallery/data/GalleryPorts.kt` 的卫生口（与 B1 的 `GalleryContentGuard`、B2 的 `GalleryPreferences` 同一处），归 **B7'**。归属表里 `HostCircuitBreaker` 的默认因此从「B6」改成「B7'（漫画侧两处已收）」。

**两张表清零后不许删**：`BASELINE_RAW_OKHTTP` 现在是 `emptyMap()`，`VeneraNetworkClient` 在归属表里零站点 —— 保留的理由与 B4 那两颗同名（断言 C 的 `IMPL_NAMES` 由归属表键导出；而 D 那张 okhttp 表的存在本身就是「以后再有 UI 直接拼请求就红」）。

**未验（挂账，本轮真机项里优先级最高的一批）**：从 URL 装源**成功与失败各一次**（失败那条要看错误文案是否仍是 `HTTP <码>: 获取脚本失败`，这是本批唯一改了取径的行为面）、设置页「缓存 xx MB」读数与「清除缓存」后归零、改代理后重建客户端生效、网络设置页「重置网络状态」清熔断、画廊两页的刷新清熔断（这两处属 B7'，同批验）。

## 六、那 19 处为什么不收（已裁决：甲）

`FREEZE-STATEMENT.md:17-19` 写的是「允许修实际 Bug、修明确回归；**禁止架构重构**、顺手拆文件」，`:2493` 还记着「用户明确不给豁免」的先例。本轮按**甲档**执行：非冻结的照收，冻结屏 13 处 + 导航保护域 2 处 + B2 落地的画廊写口 2 处 + B3 落地的 W1 嵌套类型 2 处**留在白名单**，但每条都在 `BusinessApiBaseline.kt` 的 `SITE_OVERRIDE` 里写明**解锁条件**（这是断言 F 的检查对象，不是给人看的注释）。

| 点位 | 归哪颗契约 | 解锁条件 |
|---|---|---|
| `UnifiedExploreScreen:123,124`、`SourceSectionScreen:104,105`、`HomeScreen:134,610,796`、`SearchScreen:136,138`、`FavoritesScreen:795,913`、`HistoryScreen:269`、`NetworkFavoritesScreen:653`（13 处） | B1 `ContentGuard` / B2 `ComicPreferences` / B3 `SourceCatalog` 都已覆盖 | **只差一次点名豁免，零技术前提**：契约与适配器已就绪，这些行是同一行的取用替换。入场券已逐颗核实（`applicationContext` 归一：`ContentGuardManager.kt:511`、`ComicSourceManager.kt:1448`、`VeneraPreferences.kt:707`；`StateFlow` 交回同一实例；不加减 `remember`） |
| `feature/Navigation.kt:491`（`navigationBarStyle` + `startPage`） | B2 `AppearancePreferences` | 同文件改动按保护域口径需**重新评审一次**（`FREEZE-STATEMENT.md:32` 只管 Tab 枚举顺序 / 路由映射 / 顶栏齿轮入口这三项，本行不在其内） |
| `feature/Navigation.kt:286`（`ComicLinkResolver`） | **本轮连契约都不立** | **两段前提，缺一段做不动**：① 同上保护域重新评审；② 硬技术前提 = `ComicLinkResolver.Outcome` 是**嵌套在吃 `Context` 的类体里的 `sealed interface`**（`source/ComicLinkResolver.kt:3,23,25-36`），而 `feature/Navigation.kt:313` 逐条消费那四档 ⇒ 必须先做一次 W1 同族的类型搬家，本行才有契约可引 |
| `feature/SettingsHost.kt:140` 与 `feature/settings/GallerySettings.kt:60`（`VeneraPreferences`） | B2 的 `GalleryPreferences` 已立但**故意只读** | 与 **W5** 同批：`setGalleryXxx` 15 枚写口的唯一消费者是这一页（`feature/settings/GallerySettings.kt:107-284`），而 `gallery/ui` 对那 12 枚只读；W5 落 `gallery/data/GalleryPreferences.kt` 实现类接上这颗接口后，这两行才有的契约可引（零技术前提的另一半已在 `GalleryPorts.kt` 里） |
| `feature/SearchViewModel.kt:39,286,299,316,325,327`（`ComicSourceManager.SourceSearchResult`）与 `feature/settings/PreferredIpSpeedTestScreen.kt:88`（`installedMeta`） | B3 的 `ComicContentApi` / `SourceCatalog **不收这两枚类型**` | 与 **W1** 同批：两颗 data class 嵌套在实现类体里（`source/ComicSourceManager.kt:61` 一带），契约签名要它们就必须 import 实现类拿嵌套类型；搬进 `source/model/` 后这两行自动消失（`SearchViewModel` 的 7 处**取用**本批已全部改引，留的只是类型位） |

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
