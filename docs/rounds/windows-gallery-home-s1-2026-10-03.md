# Windows 图库首页 · S1「脐带」落地（2026-10-03）

> 方案：`~/.qoder-cn/plans/wistful-inlet-snipe.md`（S1–S5 五批，本文件只结 S1）
> 设计稿：`docs/designs/windows-gallery-home-touhou-2026-10-03.html`（回改清单在方案里，属 S5）
> 上一批桌面读数：`docs/rounds/windows-r1f-stage1-plan-2026-10-02.md`

## 这批的唯一目标

让 `gallery/data` 与 `gallery/domain` 里有桌面价值的半边**脱离 Android 类型**，好把它们整颗开进
`:desktop` 的编译面。硬约束是用户原话那两条：

- **物理与逻辑分离**：Android 端 UI 代码零改动；桌面 UI 壳独立编写，只经共享的 `gallery/data`
  或 `GalleryPorts` 拿数据；桌面侧严禁 `import android.*`；两端 UI 严禁互相耦合。
- **行为零变更**：这一批不许动任何取数语义、取消时机、日志级别与凭据口径。

两条的交集决定了解法形状：**不能改调用点**（调用点大半在 UI），所以只能让"被调的东西"改名换形而
"调它的那一行"一字不动。

## 落地清单

### 新增平台原语（`data/platform`，全部 android-free，桌面侧已有 srcDir）

| 名字 | 为什么必须有 |
|---|---|
| `HttpEngine` | 十颗客户端实际只用 `.okHttpClient` 这一枚成员；**不为 `GallerySaver` 的 `downloadBytes` 放宽接口**（那颗本来就要排除） |
| `Logger` | `GalleryArtistAvatarStore` 有两条 `Log.w`、`GalleryTagCategories` 有一条 `Log.i`（见下面第 4 个坑）。删掉就成了本仓最忌的静默交错 |
| `LruMap` | 替 `android.util.LruCache`（`YandeReClient` 与 `GalleryTagCategories` 各一处） |
| `NoInteractiveBypassTag` | 从 `data/network/HostCircuitBreaker.kt:129` 搬出来；八颗客户端 import 它，而 `data.network` 是禁引族 |
| `UserAgentStrings` | 同上：UA 两枚常量原先住在 `data/network/UserAgentPolicy.kt`，改成别名指过来，**那颗文件与常量值一字未改** |
| `android/AndroidHttpEngine` `android/AndroidLogger` | 平台实现。落 `android/` 子目录即被那颗全局排除免费挡在桌面外 |

### 关键手法：同包扩展函数

`fun getInstance(context: Context)` 从各类的伴生对象里**搬出去**，落成
`gallery/data/android/GalleryClientsAndroid.kt` 里的同包扩展（文件包名仍写
`com.venera.compose.gallery.data` —— Kotlin 允许包名不等于目录名）：

- 同包声明不需要 import ⇒ `GalleryBusinessApi` 的适配器、客户端彼此、`gallery/ui` 那一路，
  **调用点一行未动**；
- 目录名 `android/` 被 `desktop/build.gradle.kts` 已有的 `exclude("android/**")` 挡在桌面外 ⇒
  桌面看到 `getInstance(engine)`，Android 看到 `getInstance(context)`，无需新增排除项。

### 契约与接线分家

- `GalleryPorts.kt` / `GalleryBusinessApi.kt` 各拆两半：容器 + 契约留原目录，
  `AndroidGalleryPorts` 与十三颗 `Android*` 适配器搬进
  `gallery/data/android/GalleryPortsAndroid.kt` / `GalleryAdaptersAndroid.kt`。
- 契约面新补两枚实测存在的成员：`YandeReBoard.fetchDailyPopular()`、
  新立 `ScoredBoard : BoardSource { fetchTopScored() }`，并把 `GalleryBoards.gelbooru` /
  `.safebooru` 的声明类型收窄到它。**这两枚都不许进公共 `BoardSource`**：
  `fetchTopScored` 只住在 Gelbooru/Safebooru 两颗 client、`fetchDailyPopular` 只住在 yande.re，
  进公共口就是逼另一侧写一枚"永远失败"的假实现。
- `GelbooruAccount` / `SauceNaoAccount` **没有**脱 Context：设置页三处 UI 直接拿它们的
  `identity` / `hasKey` 流在渲染。改法是两边各让一步 —— client 认新抽的
  `GelbooruCredentials` / `SauceNaoCredentials` 窄接口，account 实现它，UI 一字不动。
- `GelbooruIdentity` 从 `GelbooruAccount.kt` 移进 `GelbooruClient.kt`：契约
  `GalleryCredentials.gelbooruIdentity` 的返回类型里有它，而 account 整颗进排除清单。
  同包移动 ⇒ 所有 import 路径不变。

### 取数编排搬进共享面

新增 `gallery/domain/GalleryDailyFeed.kt`（android-free，吃 `GalleryBoards` 契约）：三腿并发、
每站一笔时间预算、`failures` 出参、`pools` 出参与 `Daily` 整颗照搬。
`GalleryFeedSource` 退化成 Android 门面，只留 `getInstance(context)` + 两枚转发
（`PER_SITE_TIMEOUT_MS` 转发不能写 `const`，改成属性 getter；`yesterdayString` 本来就是 fun）。
转发的存在理由不是省事：`GalleryFeedBudgetTest` 四条、`GalleryFeedDateTest` 五处、
`GalleryLegGuard` 两枚默认参数与 `GalleryForYouViewModel` 的注释都点名着
`GalleryFeedSource` —— 门面必须继续答得出这两个名字，而**数只有一处**，不会分叉。

同批把那颗 KDoc 通篇写"两站"的陈旧说明按实测三站重写（yande.re 官方日榜 / Gelbooru
`sort:score:desc` / Safebooru `order:rank`，后两站都没有日榜端点）。

### 冻结文件的一处定点豁免

`GuardRule` 与 `AiTagKeys` 从 🧊`ContentGuardManager.kt` 搬进同包的 `security/guard/GuardVocabulary.kt`
（同包 ⇒ 全仓那五处 `import ...security.guard.GuardRule` 一字未改）。豁免记录在
`FREEZE-STATEMENT.md` 末尾。搬走的原因是判据而非顺手：`GalleryContentGuard` 契约的返回类型有
`GuardRule`，而那颗文件要进桌面编译面。

## 判据读数

| 项 | 读数 |
|---|---|
| `:app:testDebugUnitTest` | **814 tests / 0 failures / 0 errors**，106 份 XML（本批前基线 810/104，净新增 4 条=两颗桌面账本用例 ×2） |
| `:desktop:test` | 156 tests / 0 failures / 0 errors，20 份 XML（本批未加桌面用例，见下面"为什么住在 `:app`"） |
| `:desktop:compileKotlin` | BUILD SUCCESSFUL（`gallery/data` + `gallery/domain` + `security/guard` 三颗目录已进编译面） |
| `:app:assembleDebug` | BUILD SUCCESSFUL，APK 出包 |
| **约束 6 的机器核对** | `git diff --name-only` 在 `feature/` `gallery/ui/` `reader/` `components/` `MainActivity.kt` 命中 **0 颗** |
| 冻结清单命中 | 只有 🧊`ContentGuardManager.kt` 一颗，且是已记录的定点豁免（−24/+2，纯删除 + 一句指路注释） |
| 白名单 | **一条未动**。`BusinessApiBoundaryTest` A–G 七条断言按精确相等跑绿 ⇒ `SITE_TOTAL_GET_INSTANCE = 33` 与 A/B/C/D 四张表读数不变（这批改的是"端口从哪来"，不是"穿透还剩几处"） |
| `:desktop:app` 端到端 | 源装载（34 颗源 + shim + init + `jm.js`）、封面取图 **30/72**、章节列表 47 条、阅读器页数 8、窗口建出 —— **stdout 读数齐全，PNG 没出**：程序自报"需在桌面可见/有焦点时截图"，`WindowFromPoint` 那条前台限制拦住了。这条不作为"界面没坏"的证据，只作为"运行链路没断"的证据 |
| README 双语 | 各 3 处同步 106→109 文件 / 104→106 类 / 810→814 用例 |

## 三件"方案里写了、这批故意没做"

不是漏，是判据：

1. **`DesktopHttpEngine` 没写**。它要"保留 UA / `HostCircuitBreaker` / `RateLimitingInterceptor`
   三条 android-free 判据"，而那三颗今天全住在 `data.network`（禁引族）。要么把 `data/network`
   里那几颗 android-free 的件也开进共享面、要么桌面自研一条降级链 —— 那是一个有自己决策点的活，
   归 **S2**。半做等于在桌面装一条"看着像网络层、实际少了过盾与限流"的假路。
2. **桌面的 `Logger` 实现件没写**。桌面今天没有任何一行代码调 `getInstance`，实现了也没人消费；
   S2 建 `DesktopGalleryStoreFactory` / 头像档那一路时一起落。
3. **pane 六项与「未实现」标记没出图** —— 那是 S2 的判据，本批桌面 UI 零改动，出图也看不出差别。

## 四个坑（每个都留了机器化后果）

1. **Kotlin 块注释可以嵌套**，所以 KDoc 里写那串 android 目录的 glob（斜杠紧跟两个星号）会当场
   开第二层注释，整颗文件从那一行起被吞掉，读数是一句毫不相关的 `Missing '}'` /
   `Unclosed comment`。**S1 这一段一共踩出七处，分三轮才被抓完**：第一轮四颗文件一次报出
   （`GalleryPortsAndroid` / `GalleryAdaptersAndroid` / `GalleryBusinessApi` / `GalleryPorts`），
   第二轮是账本那颗文件里的两处（android 目录的 glob + 一串 js 的 glob），第三轮最讽刺 ——
   连"提醒别再这么写"的那段注释本身又把整颗文件吞了。判据：提那颗 glob 就写"android 子目录"，
   或者写进 `//` 行注释（行注释不嵌套），别在 KDoc 里照抄 gradle 那串。
2. **按 import 扫会把内联 FQN 扫漏**。`GalleryTagCategories.kt` 整颗文件一句 `import android.*`
   都没有，却写着一句 `android.util.Log.i(...)` —— "脱 Context"那批逐颗查过 import 之后仍给它
   留了过去，直到 `:desktop:compileKotlin` 报 `Unresolved reference 'android'` 才被抓回来。
   与既有那条"`getInstance` 跨行内联链单行判式扫不到"同族。后果已机器化：
   `DesktopNoAndroidImportTest` 扫的是**代码行里的 `android.<sdk 包>`**，不只是 import。
3. **脚本插队会挤孤儿 KDoc**。`interface GelbooruCredentials` 连同它的 KDoc 被插在"类 KDoc"与
   "类声明"之间，文档只挂在紧邻声明的那一颗上 ⇒ 那颗类长达二十行的实测口径成了孤儿（编译不红、
   测试不红，只有人会读错）。修完复扫：全仓"KDoc 紧接 KDoc"由 18 处回到 16 处，剩的 16 处
   都在本批未动的文件里（含 UI 与冻结文件），**不顺手修**。复算脚本 `_qa/s1-orphan-kdoc.cjs`。
4. **`Calendar.getInstance()` 会被当成"新增静态取用出处"**。`BusinessApiBoundaryTest` 的 E 条按
   名字判 `X.getInstance(`，而"自己声明过 getInstance 的文件整体放过"——`yesterdayString` 从前住在
   `GalleryFeedSource`（它就是声明者），搬进 `GalleryDailyFeed`（不再是声明者）之后，一句 JDK
   取时钟被算成了业务穿透。处置是加进既有的 `PLATFORM_ONLY`（那颗集合的存在理由就是挡这种
   名字撞规的假阳性），**没有**去放宽 A/B/C 任何一张白名单，也没有把 `Calendar` 之外的名字塞进去。

## 新增的两条桌面守卫（以及为什么住在 `:app`）

`app/src/test/java/com/venera/compose/desktop/` + `testsupport/DesktopFace.kt`。

- `DesktopSharedFaceLedgerTest`（2 条）：共享面的**禁引文件集合**与 gradle 的**排除清单**必须
  双向对得上 —— ①有禁引没排除、②已无禁引成因却还排着、③排除清单里点了源码树没有的名字，
  三种漂各报一段。第二条用例要求每颗被排出的文件都说得出**被哪一族禁引挡住**（钉的是"有人
  往禁引集合里加减一族"——那会让整张清单换个意思而桌面照样绿）。
  判据是**禁引集合**（`^import android` ∪ `androidx.` ∪ `coil` ∪ `data.prefs` ∪ `data.network`
  ∪ `….android.`）而不是只看 android import，成因见上面第 2 个坑。
- `DesktopNoAndroidImportTest`（2 条）：共享面 + 桌面自己的源不许点名 Android SDK（含内联 FQN）；
  以及两端 UI 不许互引（`feature` / `gallery.ui` / `reader` / `components` / `MainActivity`）。
  ⚠️ 第二张表与 `BusinessApiBoundaryTest` 的 UI 口径**故意同串**，两份各管一头：那边管"UI 不许直连
  实现"，这边管"桌面不许连上 UI"。
- **为什么放 `:app` 而不是 `:desktop`**：这两条要钉的失败形态之一就是"`:desktop:compileKotlin` 炸了"，
  而那次跑之后 `:desktop:test` 根本不会执行 —— 判据只在最该说话的时候缺席。放在 `:app` 侧，
  桌面编不过时它照样红，并且打印到**文件:行**（编译器只给一句 `Unresolved reference`）。
  teeth 实测：往排除清单插一颗 `NoSuchFileOnPurpose.kt` ⇒ 两条用例同时红，还原后绿。

## `:desktop` 的构建面改动（两处，都不是可有可无）

- `srcDir` 三颗 + 逐颗点名排除十三颗，每条排除上面都带一行成因（写"为什么上不去"而不是归类）。
- 新增 `alias(libs.plugins.kotlin.serialization)`：`gallery/data` 里那三份磁盘档
  （收藏 / 关注 / 头像）都是 `@Serializable`，没有这颗编译器插件就编不过 —— 桌面原先只引了
  serialization-json 的**运行时**，从没编过带注解的源。

## S2 的开工点（本批留下的接口）

1. `DesktopHttpEngine`（含它与 `data.network` 那三条判据的关系怎么拍）。
2. 桌面侧 `GalleryPorts` 接线：容器是 **12 个必填构造参数**，桌面要一次给满 12 颗实现件
   （做不了的也必须以**存在的对象**形态进构造、返回显式失败，不许给 null）。
   其中 `prefs` 那半边 12 枚读成员此前被漏算过，已记进方案。
3. pane 六项 + 四项「未实现」标记上屏；出图判据回到人眼。
4. 桌面 `Logger` 实现件（头像档那一路要它）。
