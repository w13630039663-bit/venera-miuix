# 桌面端复用安卓大屏 UI —— 三条路线对照与裁决建议

> 状态：**A0 探针已跑完（2026-10-19） + 写法 A 落地（2026-10-20 重做）**
>
> ⚠️ **2026-10-20 更正**：先前这条状态写的是"独立桌面大屏模块（`com.venera.desktop.ui`）
> 复用 Android 大屏断点与侧栏" —— 那**不是**用户要的东西，也已按用户裁决撤掉：
> 那是一条**新的平行线**（照着大屏断点重画的 9 个 pane），而用户原话是
> 「之前做的平行线就不要了，按照大屏端复用」，裁决「直接写法 A」。
>
> 现在落地的是**写法 A**：`desktop/build.gradle.kts` 用 `srcDir` 把 `:app` 的
> **11 个源目录**真接进 `:desktop` 编译面 —— **两端编译同一份源码**：
>
> | 目录 | 颗数 | 说明 |
> |---|---|---|
> | `components/` | 51 | 侧栏、顶栏、卡片、封面、宽屏判据 —— 零 `import android.` |
> | `ui/tokens/` | 8 | 色板 / 间距 / 字号 / 形状 |
> | `source/model/` | 1 | 309 行纯 data class（`Comic` 等） |
> | `gallery/ui/` | 9 | 46 颗里与 Android 宿主无关的那些 |
> | `feature/` | 8 | 68 颗里与 Android 宿主无关的那些 |
> | `gallery/data` `gallery/domain` `data/platform` `data/db` `security/guard` | 131 | 数据层，早先批次已共享 |
>
> 桌面侧自持的只有窗口/进程/数据接线，以及**一颗**装配文件
> `VeneraDesktopLargeScreen.kt`（12 个 UI 符号全部 import 自共享面）。
>
> ## ⚠️ 必须说清的一件事：复用的是**零件**，不是**那几屏**
>
> 2026-10-20 用户当场抓到：「你这个首页怎么和安卓端的完全不一样？」
> —— 抓得对。当时桌面壳把**画廊那面图片墙**摆在了 `HOME` 位置，`else` 也兜回它，
> 于是五个 tab 里有四个长得一模一样；而安卓端是：
>
> | Tab | 安卓真屏 | 行数 | 当时桌面 |
> |---|---|---|---|
> | 首页 | 漫画推荐页（阅读统计 · 可能你感兴趣 · 历史） | 1124 | 画廊墙 ❌ |
> | 收藏 | 漫画收藏 | 1337 | 画廊墙 ❌ |
> | 搜索 | 搜索 | 1830 | `else` → 画廊墙 ❌ |
> | 探索 | 探索 | — | `else` → 画廊墙 ❌ |
> | 画廊 | `GalleryScreen` | 3453 | 一面简化墙 ⚠️ |
>
> 已修：**只有 GALLERY 一格是真墙**（真取数：yande.re / safebooru 各 100 条，
> 混排去重后 171 张上屏）；其余四格走 `DesktopNotWiredPane` 如实报「这一屏还没接」，
> 并逐一点名**不用 `else` 兜底**（`DesktopTabOwnershipTest` 三笔钉住：
> 五个 tab 各有分支、图片墙只许挂 GALLERY、`NotWiredPane` 只给那四屏）。
> 假装比空白更糟 —— 空白会让人问「怎么没有」，假装会让人以为「内容全错了」。
>
> ## 2026-10-20 第 4 轮：首页也改成真零件（用户第二次抓到的那一颗）
>
> 第 3 轮把首页改成「如实报未接」—— 诚实了，但**首页仍是桌面自己画的**，一个零件都没复用。
> 这一轮把它做成：
>
> | 做了什么 | 结果 |
> |---|---|
> | 
eature/HomeScreen.kt 切成两半 | 上半 AndroidHomeScreen（绑 ViewModel，留 :app）；下半 12 颗子件 → 新文件 
eature/HomeHomeWidgets.kt |
> | 4 处平台耦合收进 UxPlatformServices | ContentGuardManager ×2 → coverMaskStateOf(…)；LocalView.systemGestureExclusionRects → setGestureExclusionRects(…) |
> | 新文件进 :desktop 编译面 | 零 import android.、零 LocalContext，两端**同一份源码** |
> | 桌面首页改用这批零件 | DesktopHomePane 用 StatCell / StatDivider / SourceStatusRowHome / healthOf / StatBarRow 摆出安卓首页的结构 |
>
> **结构是安卓的，数据是待接的** —— 每一格显示 0 / 「未测速」，不编数字。
> 真要接数据得先做 BusinessPorts 那 17 个成员的桌面实现（量过：单放行
> HomeViewModel 一颗就 100 处错）。
>
> 新钉两条尺：DesktopHomeUsesSharedWidgetsTest（首页必须 import ≥4 颗共享零件、
> 不许出现图片墙零件、HomeHomeWidgets.kt 零平台耦合）。
> BusinessApiBoundaryTest 的存量按「只许缩短」同步：ContentGuardManager 从
> 
eature/HomeScreen.kt 迁走 2 处（站点总数 33 → 31），ADAPTERS 加上
> platform/AndroidUxPlatformServices.kt（平台适配器，与另外两颗同类）。
>
> ## 2026-10-20 第 5 轮：接真数据（用户：「接真数据吧」）
>
> 首页那三格从「恒 0 + 待接」变成**真的读库**。截图 `_qa/home-real-data.png`：
> 今日 42 页 / 本周 74 页 / 连续 3 天 / 本周逐日柱状图，全部来自
> `%LOCALAPPDATA%\venera\db\venera_core.db`，与直接跑 SQL 算出来的一致。
>
> ### 为什么没走 `HomeViewModel` / `BusinessPorts`
>
> 量过：`BusinessPorts` 那 17 个接口共 **175 个必须实现的方法**，而它们的实现在
> Android 侧绑 Context / 通知渠道 / assets。为了首页三格数字去敷衍 175 个空壳，
> 是把「接真数据」做成「接一堆空壳」。
>
> 而首页要的数字**本来就在库里**，`ReadingStatsStore` / `DatabasePorts` 早在
> `data/db` 那一组 `srcDir` 里 —— 于是走**只读一条真通路**：桌面侧自己开库、自己查，
> **口径与安卓首页不同**（2026-10-21 更正：此前这里写「同一张表、同一句 SQL」，
> 经逐行核对**不成立**，已改）：
>
> | 维度 | 安卓首页（`HomeViewModel.build()`） | 桌面（`DesktopHomeStatsSource`） |
> |---|---|---|
> | 数据源 | `comic_history`（`:109` combine → `historyFlow`） | `reading_stats` 表 |
> | 今日页数 | `lastPageIndex + 1`（`:482`，**当前进度**） | `pages_read`（**累计阅读量**） |
> | 日界 | `now / dayMs * dayMs` = **UTC**（`:481`） | `LocalDate.now()` = **本地时区** |
> | 本周 | `now - 7*dayMs` = **滚动 7 天**（`:483`） | 周一起的**自然周** |
> | 连续天数 | `activeDays` 内联循环（`:485-494`） | `ReadingStatsStore.streakDays` |
>
> 选的桌面口径：`reading_stats.pages_read` 记的是**真累计**，而 `lastPageIndex+1` 只是
> **当前读到第几页** —— 同一本读 3 次，前者涨 3 次、后者只记最后位置。
> 所以桌面显示的数字**会与手机不同**，不是 bug，是口径不同；用户若对比两端需知道这点。
> 要对齐安卓得把 `build()` 从 `private` 抽成纯函数（动 `app/` 侧，撞「安卓一行不改」）。
>
> ### 顺带做的三件零件搬家（都是「纯数据蹭在 Android 件里」）
>
> | 搬什么 | 从哪 → 到哪 | 为什么 |
> |---|---|---|
> | `RepoIndexEntry` | `ComicSourceManager` 内嵌 → `source/model/ComicSourceModels.kt` | 6 个 String 字段，零耦合 |
> | `UpdateDownload` | `ApkDownloader.kt` → `ReleaseFeed.kt` | 纯 sealed interface，为它拖整颗 354 行下载器不值得 |
> | `displayVersionOf` | `AppUpdateChecker` → `ReleaseFeed.kt` 顶层 | 零依赖字符串处理，依赖方向反了 |
>
> ### 编译面
>
> 新增 7 个 `srcDir`：`data/api` / `stats` / `download` / `data/update` / `source` /
> `source/explore` / `feature/sourcemanage`；`auto_exclude` 收敛出 24 颗排除。
> `BusinessPorts` 现在**在桌面编译面里**（探针验证过），是后续接真数据的路基。
>
> ### 新钉两条尺
>
> `DesktopHomeStatsSourceTest`：
>  - 造一颗临时库、写确定数据 → 断言三个数字与直接算的一致（**变异测试验过会红**：
>    把实现改成返回 `Empty`，两条全红）；
>  - 库打不开时必须给 `note`，不许悄悄返回全 0 —— 全 0 会被读成「今天读了 0 页」，
>    而实际是「库打不开」，两件事。
>
> 另清掉一条冗余排除：`**/AndroidBusinessPorts.kt` —— 它在 `android/` 子目录，
> 已被 `hasAndroidParent()` 按目录挡掉，单独点名只会让账本说不出成因。
>
> > ## 2026-10-21：处置「钉着已删 UI」的 ruler（删 7 颗，逐颗记账）
>
> 背景：上一轮按用户要求删掉平行线 UI（旧 Fluent 那条线 + `com.venera.desktop.ui` 七个 pane），
> 但**只删了 UI、没处置钉它的 ruler**。那批 `@Test` 一度被清成空壳（0 笔，JUnit 不报错、
> 静静通过），让 `:desktop:test` 计数虚高。这是自造的账，本轮修。
>
> 处置：**整颗删文件**，不留空壳。删前逐颗核 —— 它断言的那个 `@Composable`
> 在 `desktop/src/main` 里已无定义（不是"暂时红"，是"守的东西没了"）。
>
> | 删掉的文件 | 原本钉什么 | 依据 |
> |---|---|---|
> | `DesktopComicHistoryWiringTest.kt` | 漫画历史件与收藏同口径懒装载、接应用根 | 断言的 `DesktopComicHistoryWiringTest`, `DesktopComicReader`, `DesktopComicDetail` 已无定义 |
> | `DesktopDailyScrollTest.kt` | 日报那一屏不许出现第二个滚动容器 | 断言的 `DesktopGalleryPostWall`, `DesktopDailyScrollTest`, `DesktopDailyPane` 已无定义 |
> | `DesktopForYouPaneTest.kt` | For You 那一屏的装配与读数 | 断言的 `DesktopForYouPlanTest`, `DesktopForYouPaneTest`, `DesktopForYouPane` 已无定义 |
> | `DesktopGalleryViewerTest.kt` | 画廊查看器的页面切换与缩放 | 断言的 `DesktopGalleryViewer`, `DesktopGalleryViewerTest`, `DesktopGalleryDetailPanel` 已无定义 |
> | `DesktopLandingMoverTest.kt` | 落地页的搬运逻辑 | 断言的 `DesktopLandingMoverTest` 已无定义 |
> | `DesktopLandingReadoutTest.kt` | 落地页的读数 | 断言的 `DesktopLandingReadoutTest` 已无定义 |
> | `DesktopWallScrollNestingTest.kt` | 那一面墙不许被包进纵向滚动（由一次真崩溃换来） | 断言的 `DesktopGalleryPostWall`, `DesktopForYouPaneTest`, `DesktopComicPane` 已无定义 |
>
> **代价说清楚**：这批里有真守卫，尤其 `DesktopWallScrollNestingTest` 钉的是一次真崩溃
> （`LazyVerticalGrid` 进 `verticalScroll` ⇒ `IllegalStateException: Vertically scrollable
> component was measured with an infinity maximum height`）。将来若再写回那种形状，
> 这条守卫已经不在了。替代它的是行为层验证：起窗看到墙滚得动、不崩。
> 这是从"静态扫描"退到"人眼+运行"，是净损失，记账在此。
>
> 
> ### 补：另外 3 颗同类 ruler（同日处置）
>
> 这三颗引用的**测试目标文件**还在，所以第一版判据漏了它们；但逐颗核过，断言的
> **实现**同样已随平行线 UI 删除：
>
> | 删掉的文件 | 原本钉什么 | 依据 |
> |---|---|---|
> | `DesktopComicCardOfflineTest.kt` | 离线卡上「已下载」标记（读 `DesktopComicOfflineMark.kt`） | 实现已随平行线 UI 删除（文件不存在 / 声明 0 处） |
> | `DesktopDetailTierSeedTest.kt` | 详情面板分档钳档（读 `DesktopGalleryViewer.kt`） | 实现已随平行线 UI 删除（文件不存在 / 声明 0 处） |
> | `DesktopGalleryViewerStateTest.kt` | 查看器档位唯一声明（`VeneraDesktopApp.kt` 的 `var tier by remember`） | 实现已随平行线 UI 删除（文件不存在 / 声明 0 处） |
>
> 处置口径与上表一致：整颗删、逐条记账、不留空壳。
>
> ## 2026-10-21 第 6 轮：专家团评审后修的四件事 + 首页历史区接真数据
>
> 用户 @专家团（技术方案评审团）要求「把首页按照安卓端做完」。评审过程中，
> 验收工程师逐行核源码，**抓到本喵自造的三处真问题**和一处**文档谎言**。这些先修，再谈做新功能。
>
> ### 修了什么
>
> | # | 问题 | 怎么修的 |
> |---|---|---|
> | R1 | **53 个 `@Test` 被清成空壳**。上一轮删平行线 UI 时只删了 UI，钉它的 10 颗测试类变成 0 笔测试 —— JUnit 对 0 笔不报错，静静通过，于是 `:desktop:test` 的计数虚高 | 逐颗核过：断言的 `@Composable` 在 `main` 里已无定义 ⇒ **整颗删文件**（不留空壳），并在本文档逐条记账 |
> | R3 | **桌面内容遮罩整体失效**。`LocalUxPlatformServices` 从未 provide ⇒ 吃 `NoOp` ⇒ `coverMaskStateOf` 恒返回 `"VISIBLE"` ⇒ 共享零件里的封面**永不打码** | 新增 `DesktopUxPlatformServices`，接到已有的 `DesktopGalleryContentGuard`（`nsfwMaskMode` 流 + `GalleryBlockMatch.blocked` 黑名单），并在壳里 provide |
> | R14 | **文档写了谎**：「与安卓端同一张表、同一句 SQL」不成立 | 改成本文第 5 轮那张口径差异表（5 个维度逐条列出） |
> | — | 首页**历史区**没做（安卓有，桌面没有） | 新增 `DesktopHomeHistorySection` + `DesktopHomeHistorySource`，读真库 `comic_history` |
>
> ### 修 R1 时的一个诚实说明
>
> 删掉的 53 笔里有真守卫。尤其 `DesktopWallScrollNestingTest` 钉的是一次**真崩溃**：
> `LazyVerticalGrid` 进 `verticalScroll` ⇒ `IllegalStateException: Vertically scrollable
> component was measured with an infinity maximum height`。
> 将来若再写回那种形状，**这条守卫已经不在了**。替代它的是行为层验证（起窗看墙滚得动、不崩）。
> 这是从「静态扫描」退到「人眼+运行」，是净损失，记在这儿而不是悄悄咽下。
>
> ### 历史区踩的两个坑（都靠实测截图抓到，不是靠想）
>
> **坑一**：`HistoryDao.refresh()` 是 `scope.launch { }`（`HistoryDao.kt:48-50`），发起即返回，
> 而 `_historyFlow` 初值是 `emptyList()`。写成 `refresh()` 紧接 `.value` ⇒ 读到初值 ⇒
> 屏上说「还没有阅读记录」，而库里明明有 3 条（`_qa/home-with-history.png` 是证据）。
>
> **坑二**（修了第一坑后仍然错）：改用 `historyFlow.first()` —— 但 `MutableStateFlow.first()`
> 返回的是**当前值**，初值 `emptyList()`，根本没等异步那步。屏上与没修一模一样
> （`_qa/home-history-fixed.png`）。正确写法是 `first { it.isNotEmpty() }`，跳过初值。
>
> 修好后实测：`read() → records=3 loaded=true note=null`，屏上三张卡
> （迷雾中的她 / 夜行电车 / 海边图书馆）—— 见 `_qa/home-history-real.png`。
>
> **这两坑的形状是「把没读到当成没有」**，比编造数字更难查：编的数字至少看着像数字，
> 而"还没有阅读记录"看着像**功能正确**。已钉 `DesktopHomeHistoryRaceTest` 三条，
> 并做变异验证（把实现换回旧写法，两条判据全红）。
>
> ### 两处内容安全缺口（今天最严重）
>
> **B · 桌面画廊墙一张图都不打码**
>
> 三条独立证据：① `DesktopGalleryWallPolicy.of(...)` 是真判据，但在 `desktop/src/main` 里
> **零调用点**（只有单测在调）；② 生产取数路走 `desktopWallCardsOf(...)`，而
> `desktopWallCardOf` 的 `masked` 形参**默认 `false`**；③ 大屏照 `card.masked` 上遮罩 ⇒ 恒 false。
>
> ⇒ **无论档位是 OFF / BLUR / HIDE，墙上都不会遮一张。** 不报错、不变慢、屏上看不出来。
>
> 修法：`desktopWallFirstPage` 加 `guard` / `maskMode` / `blockAi` 三个形参，取数处走
> `DesktopGalleryWallPolicy.of`，把 `DesktopWallCard.masked` 搬进 `DesktopWallCardModel.masked`
> （只搬，不重判）；壳签名加 `wallGuardOf` / `wallBlockAiOf`，装配点把真守卫递进去。
> **接口改完不等于修好 —— 调用点不传就还是老路。** 所以另钉一条
> `DesktopWallPolicyWiredTest` 专门管"取数路真的调了 policy"。
>
> **C · 首页历史卡恒不遮**
>
> 两个成因：① 装配点 lambda 写的是 `{ _, _, author, tags -> }`，第一个 `_` 就是 `title`
> ⇒ 用户的 `KEYWORD` 规则**命中标题也不生效**；② 历史记录**不带 tags**
> （`HomeHomeWidgets.kt:165` 调 `coverMaskStateOf` 时只传 sourceKey/title/author/comicId），
> 而桌面判据只认 tags ⇒ `looksAdult(emptyList())` 恒 false ⇒ **历史卡永远 VISIBLE**。
>
> 安卓不会这样：它 `maskHit` 的第二道就是**源级预设表**
> （`ContentGuardManager.kt:441-443`：`nsfw`/`mixed` ⇒ 命中）。桌面缺这一整道。
>
> 修法：① 加 `DesktopSourceContentPresets` 读 `source_content_warning.json`（桌面此前
> **没把它放进 resources include 清单**，已补），`nsfw`/`mixed` ⇒ 命中，与安卓那句
> `||` 逐字一致；② `title` 不再丢弃。
>
> ### 四条 P0（架构师逐行核出来的）
>
> | # | 问题 | 处置 |
> |---|---|---|
> | R3 | `note` 算了但不上屏 ⇒ 读失败时落到「还没有阅读记录」，**把「库打不开」谎报成「你没读过书」** | section 加 `note` 形参，`when` 里排在最前 |
> | R4 | 统计 `read()` 的 `DatabasePorts.of` 在 try **外** ⇒ 未接线时异常绕过 `note` 分支 | 改 `runCatching{}.fold` 兜住取 core |
> | R5 | **引证错位**：本喵把 `HomeScreen.kt:171-174`（讲 `item(key="stats")` 第 0 项的锚位跳动）当作"历史区该无条件渲染"的依据。 历史在列表**中部**，锚位机制不同，且**安卓对历史就是条件渲染的** | 换成真理由（桌面 `produceState(initialValue=Loading)` 竞态更长），两处注释都改，并明写"安卓侧一字不动" |
> | R1 | **写侧真空**：`comic_history` / `reading_stats` 在桌面**都没有写入者** | 见下 |
>
> ### R1 为什么**没有**硬修
>
> `desktopWriteReadingHistory`（写口）是完整的、带 `NonCancellable` 与取消重抛语义，
> 但**零调用点**。本喵核过一个关键事实：
>
> > 桌面此刻 `desktop/src/main` 只剩 **12 个 `@Composable`，没有阅读器 UI**
> > （平行线那条按用户 2026-10-20 裁决删掉了）。
>
> 没有"翻页"这个动作，"翻一页记一行"就没有落点。所以这**不是漏接，是结构性缺失**。
> 硬造一个调用点等于编一条不存在的行为 —— 比缺着更坏。
>
> 处置：钉 `DesktopHistoryWriteSideLedgerTest` 两条 ——
> ① 记下"现在没人写"这个事实，**阅读器 UI 一回来就自动变红**要求接上；
> ② 钉住写口本身的取消语义（不许把"没干完"记成"干失败了"）。
> 这条尺守的是「账不许糊」，不是「功能已完成」。
>
> ### C 的第二半：源级预设表（本喵差点又犯「写了没接」的错）
>
> C 的两个成因里，改 `title` 只解决一半。另一半是**桌面根本没有源级预设表**这道判据。
>
> 本喵第一遍的处理是：写了 `DesktopSourceContentPresets.kt`、往 `build.gradle.kts` 的
> resources include 清单里补了 `source_content_warning.json` —— 然后就以为改完了。
> **实际上两条都没落盘**（脚本报错后被清理掉，文件根本不存在）。
>
> 是复核时一条 grep 抓到的：`DesktopSourceContentPresets` 在 `desktop/src/main` 里
> **零命中** —— 文件不存在，自然也谈不上接进判据。这正是安全评审点过的那类
> 「实现写好、零调用」（`ProvideDesktopUxPlatformServices` 是同款）。
>
> **这条失误与本文件上一轮记的「历史区竞态踩两层」是同一类**：把"我写了这段代码"
> 当成"这个功能生效了"。两者都靠 grep / 截图这类**外部证据**才抓到，靠自省抓不到。
>
> 补完之后：
> - `build.gradle.kts` 里那句「同理不带 …`source_content_warning.json`」改成**带上**，
>   并写清为什么改（旧注释当时是真的，是判据需求变了）；
> - `DesktopAssetsClasspathTest` 里那条断言「包内不该有 `/source_content_warning.json`」
>   **从「不许有」改成「必须有」** —— 那条旧尺正是照旧口径写的，它现在红了是对的；
>   改尺的同时把理由写进注释，不留一条"被改过的尺"没有解释。
>
> 钉住的尺：`DesktopSourceContentPresetsTest` 五条，其中三条做**变异验证** ——
> 把 `source_content_warning.json` 从 include 清单里摘掉，立刻 3 条红。
> 这条尺真的会咬人，不是摆设。
>
> ### 本轮计数
>
> `:app` **1795 / 0 红**（安卓侧逻辑未改）；`:desktop` **1075 / 0 红**。
> 净增 26 条真守卫（B 四条、C 五条、R3/R4 形状尺、R5 引证尺、
> 写侧账本两条、UxPlatformServices 三条、历史区三条）。
>
> ### 仍然没做完的（如实列，不粉饰）
>
> | 项 | 状态 |
> |---|---|
> | 分区 2.5 推荐区 | **阻断，不做** —— 需主人拍板语义 + 安全侧两条件 |
> | 分区 4 的**延迟测速** | 未做，屏上如实显示"未测速"（`healthOf(null)` = Unknown） |
> | 分区 5 本地漫画 | 未做 —— 桌面下载树**从不写 `comic_info.json`**，读侧却以它为门槛；要先补写侧 |
> | 写侧（R1） | **结构性缺失** —— 桌面没有阅读器 UI，"翻页记一行"无处落地 |
> | 桌面「本周阅读」柱状图 | 桌面自加，安卓首页没有这一区。留或删待定，**不假装它是安卓的** |
> | `nsfw_mode` 桌面怎么改 | 未找到可用入口（`setNsfwMaskMode` 零调用点）。即使 provider 挂对了，用户不改键值文件遮罩也永远不开 |
>
> > # 桌面 vs 安卓：全局功能对账（2026-10-21，主人要求不只看首页）
>
> 口径：**逐个数出来的，不是估的。**
> 判据用「`desktop/src/main` 里有没有对应的 `@Composable` 屏」——不看文件是否存在。
> 因为上一轮删平行线时，`@Composable` 被删掉、文件壳留了下来
> （14 个 Pane 文件的 `@Composable` 全部为 0），**文件在 ≠ 界面在**。
>
> ## 一、家的底数
>
> | | 安卓 | 桌面 |
> |---|---|---|
> | 屏级 UI 入口（`*Screen`/`*Pane`/`*Page`） | **34** | **2**（首页 + 画廊墙） |
> | `@Composable` 总数 | 未逐个数（34 个屏内部还有大量局部件） | **12** |
> | 其中：真屏 | 34 | **2** |
> | 其中：占位页（"未接线"） | 0 | **1**（`DesktopNotWiredPane`，收藏/搜索/探索共用） |
> | 其中：诊断探针（非产品 UI） | 0 | **4**（`BlurCostProbe`×2 / `ImeProbe` / `ReaderProbe` / `SpikeWindow`） |
> | 编译面挂载的安卓目录 | — | **17 个**（数据层/逻辑层大量已通） |
> | 底栏格数 | 5 | 5（**只有 2 格是真的**） |
>
> **一句话**：桌面现在是「**数据层基本通了、界面层基本没有**」。
> 那 34 个屏里，桌面能真渲染的只有 2 个。
>
> ## 二、底栏那一排（最直观的差距）
>
> | 格 | 安卓 | 桌面 |
> |---|---|---|
> | 首页 | ✅ 5 个分区 | ⚠️ **3/5**（统计 ✅ / 历史 ✅ / 源状态半 / 推荐 ❌ / 本地 ❌） |
> | 收藏 | ✅ `FavoritesScreen` | ❌ **占位页**「未接线」 |
> | 搜索 | ✅ `SearchScreen` | ❌ **占位页**「未接线」 |
> | 画廊 | ✅ 6 个子屏 | ⚠️ **只有墙**（浏览/搜索/收藏夹/每日/ForYou/浏览历史都无界面） |
> | 探索 | ✅ `UnifiedExploreScreen` | ❌ **占位页**「未接线」 |
>
> ## 三、逐屏对账（34 个安卓屏）
>
> ### A. 底栏 4 个主屏
>
> | 安卓屏 | 桌面 | 差距说明 |
> |---|---|---|
> | `HomeScreen` | ⚠️ 3/5 分区 | 见上表 |
> | `FavoritesScreen` | ❌ | 空 |
> | `SearchScreen` | ❌ | 空 |
> | `UnifiedExploreScreen` | ❌ | 空 |
>
> ### B. 漫画域二级页（**最重的一块**）
>
> | 安卓屏 | 行数 | 桌面 |
> |---|---|---|
> | `ComicDetailScreen` | **2322** | ❌ 无界面（`DesktopComicPane.kt` 302 行只剩逻辑） |
> | `VeneraReaderScreen` | **2529** | ❌ 无界面（**这就是写侧真空的根因**） |
> | `LocalComicScreen` | 953 | ❌ 且**读法都不对**：桌面下载树从不写 `comic_info.json`，而它是读侧门槛 |
> | `StatsScreen` | 770 | ❌ 无界面（数据层 `ReadingStatsStore` 已在编译面） |
> | `FavoriteImagesScreen` | 565 | ❌ |
> | `SyncBackupScreen` | 489 | ❌ |
> | `HistoryScreen` | 425 | ❌ 无独立页（首页那一格有，二级页没有） |
> | `DownloadScreen` | 395 | ❌ |
> | `FollowUpdatesScreen` | 301 | ❌ |
> | `LogViewerScreen` | 228 | ❌ |
> | `CoverViewerScreen` | 170 | ❌ |
> | `NetworkFavoritesScreen` | 756 | ❌ |
>
> ⚠️ 两屏加起来 **4851 行**（详情 + 阅读器），是安卓体量最大的两块，桌面**两块都空**。
>
> ### C. 源管理（已挂进编译面，但没界面）
>
> | 安卓屏 | 桌面 |
> |---|---|
> | `ComicSourceScreen` | ❌ 无界面（`feature/sourcemanage` 已在 17 个 srcDir 里） |
> | `SourceEditScreen` | ❌ |
> | `SourceSectionScreen` | ❌ |
> | `PreferredIpSpeedTestScreen` | ❌（这也是分区 4 延迟测速没做的根因） |
> | `WebLoginScreen` | ❌ |
> | `SettingsScreen` | ❌（17 行，是壳；真设置在别处） |
>
> ### D. 画廊域（6 个子屏）
>
> | 安卓屏 | 桌面 |
> |---|---|
> | `GalleryScreen` | ⚠️ **只有墙**（`DesktopGalleryWallPane`） |
> | `GalleryDailyScreen` | ❌ |
> | `GalleryForYouScreen` | ❌ |
> | `GalleryHistoryScreen` | ❌ |
> | `GalleryPostScreen` | ❌（点卡片看大图那一步没有） |
> | `GalleryBookmarksHubScreen` | ❌ |
> | `GalleryArtistProfileScreen` | ❌ |
>
> 注：桌面有 `DesktopGalleryDetailPanel.kt` / `DesktopGalleryViewerPolicy.kt` 等逻辑件，
> 但 `DesktopGalleryViewer.kt` **已被删**、`DesktopGalleryDetailPanel` 的 `@Composable` 为 0。
>
> ### E. Pixiv 域（5 个屏）
>
> `PixivWorkPage` / `PixivAccountScreen` / `PixivArtistScreen` / `PixivWorkScreen` / `WatercolorPainterScreen`
> —— 桌面**全部为 0**。
>
> 准确说：`PixivAccountScreen.kt` / `PixivArtistScreen.kt` / `PixivWorkScreen.kt` /
> `GalleryPixiv*.kt` 这几颗**在 `desktop/build.gradle.kts` 的 exclude 清单里**（目录挂了但被排除），
> 所以连编译都不进。要补这块，先把它们从 exclude 里拿出来并解决依赖。
>
> ## 四、已通的部分（别忘了这些）
>
> 桌面**不是从零**。17 个 srcDir 挂在编译面，以下能力**已经通了**：
>
> | 能力 | 状态 |
> |---|---|
> | 数据库（`venera_core.db`） | ✅ 读写通，实测读出真数据 |
> | 源脚本执行（GraalJS） | ✅ 34 个源能装载、能搜索（实测 172 张图） |
> | 画廊三站取数（yande.re / Safebooru / Gelbooru） | ✅ 实测 `腿=2 答上=2 YANDERE=100 SAFEBOORU=100` |
> | 图像缓存 | ✅ 有通路 |
> | 内容守卫（分级/黑名单/AI 标记） | ✅ 已接通（本轮修完两处缺口） |
> | 阅读统计读侧 | ✅ `ReadingStatsStore` |
> | 历史读写件 | ✅ 写件完整（但**无人调用**，因为没阅读器 UI） |
> | 下载树读写件 | ✅ 章节目录能读能删（缺 `comic_info.json` 写侧） |
> | 存图 / 剪贴板 / 词典（79415 行） | ✅ 有通路 |
>
> ## 五、量的估算（给主人的判断用）
>
> 按「安卓有多少行 UI、桌面要补多少」粗算：
>
> | 块 | 安卓行数（仅屏文件） | 桌面缺口 |
> |---|---|---|
> | 详情 + 阅读器 | ~4850 | ❌❌ **最重**，且阅读器不做首页就是空屏 |
> | 漫画域其余 10 屏 | ~3850 | ❌ |
> | 画廊域 7 屏 | **8136** | ❌（墙已有；`GalleryScreen` 3453 + `GalleryPostScreen` 2120 是大头） |
> | 源管理 5 屏 | **3209** | ❌（逻辑已在编译面，只缺界面） |
> | Pixiv 5 屏 | **6450** | ❌（`PixivAccountScreen`/`PixivArtistScreen`/`PixivWorkScreen`/`GalleryPixiv*` 在 exclude 清单里，连编译都不进） |
> | 底栏 4 主屏 | **5239** | 首页 3/5，其余 3 格是占位页 |
> | **合计** | **32964 行（实测）** | **桌面已真渲染其中 1 个屏** |
>
> 这个 32964 是 `Get-Content | .Count` 把 34 个屏文件加总出来的实测值，不是估的。
> 它**只算屏文件本身**，不含各自拆出去的局部件、ViewModel、以及 `components/` 里被调用的共享件。
>
> **口径提醒**：不能拿"安卓 34 个屏"直接压给桌面 —— 手机屏（窄屏）与大屏是两种布局
> （`WideScreenPolicy` 那三条断点），大屏版本来就该有别屏的合并与拆分。
> 但**功能面**（能做什么）应当对齐，那才是主人要的。
>
> ## 六、建议的补齐顺序（按"能解锁什么"排，不按行数排）
>
> | 顺序 | 补什么 | 为什么排这个位置 |
> |---|---|---|
> | 1 | **阅读器 + 详情** | 它是**写侧的上游**。不做它，首页统计/历史永远是空的（现在靠手工插库才有数据可看）；顺带解锁 4851 行的最大一块 |
> | 2 | **源管理界面** | 逻辑已在编译面、只缺界面，性价比最高；顺带补上分区 4 的延迟测速（`PreferredIpSpeedTestScreen` 对应物） |
> | 3 | **收藏 / 搜索 / 探索** 三格 | 底栏 3 格现在都是占位页，是最直观的"没做完" |
> | 4 | 画廊子屏（Post 大图 / Daily / ForYou / 收藏夹） | 墙已有，补子屏是顺延 |
> | 5 | 本地漫画 | 要先补 `comic_info.json` 写侧，不然读侧永远空 |
> | 6 | Pixiv 域 | 相关文件在 exclude 清单里、连编译都不进，最远 |
> | — | 首页 2.5 推荐区 | **卡在主人拍板语义**（安全侧阻断），不在技术顺序里 |
>
> > ### 首页对等性现状（"按安卓做完"还差多少）
>
> 安卓 5 分区 / 桌面已实现 3 个：
>
> | 安卓分区 | 桌面 | 说明 |
> |---|---|---|
> | `stats` 阅读统计 | ✅ | 真库 `reading_stats`（口径与安卓不同，见第 5 轮表） |
> | `recommend` 可能你感兴趣 | ❌ | 安全侧**给了阻断**，见下 |
> | 历史记录 | ✅ | 真库 `comic_history`，实测读出 3 条（`_qa/home-history-real.png`） |
> | 漫画源网络状态 | ⚠️ 半 | 源清单能读，**延迟测速未做**，屏上如实显示"未测速" |
> | 本地 本地漫画 | ❌ | 需先解决 `comic_info.json` 写侧（桌面下载树从不写它，而读侧以它为门槛） |
>
> ### 分区 2.5（推荐区）为什么被阻断 —— 需要主人拍板
>
> 安全侧给的是**阻断**，两条互相独立的理由：
> 1. 它把本地 30 天阅读聚合出的**题材名当搜索词**发给 `jm`（禁漫天堂）等成人站
>    ⇒ 本地阅读偏好外发；
> 2. 即便不论隐私，桌面上 `source_content_warning.json` 先前**没进资源清单**、
>    `ContentGuardManager` 在 exclude 里 ⇒ **开着遮罩也会让成人站封面裸奔**。
>
> 架构侧的补充：走 (a)（复用图片墙）**类型对不上** —— `DesktopWallCardModel` 与
> `Comic` 没有可映射的共同字段；走 (b) 可行但 `EngineSession.search` 缺 `sortOptions`
> 通道，且标签聚合那行引了排除清单里的 `ReadingStatsManager.TAG_SEPARATOR`。
>
> **所以这一格本批不做。** 要做得先定语义（是"漫画推荐"还是"图片精选"），
> 定了之后再补预设表与隐私方案。
>
> ### 本喵这一轮自己的失误（记下来）
>
> 1. **历史区竞态踩了两层**：先写成 `refresh()` 紧接 `.value`（读到初值），
>    改完**还是错** —— 换成 `historyFlow.first()`，但 `MutableStateFlow.first()`
>    返回的是**当前值**（初值），根本没等异步那步。实测两次截图都是
>    「还没有阅读记录」而库里 3 条都在。正确写法是 `first { it.isNotEmpty() }`。
>    这条形状（把"没读到"当成"没有"）比编数字更难查 —— 编的数字至少看着像数字，
>    而"还没有阅读记录"看着像**功能正确**。
> 2. **引证错位**（R5）：拿 stats 的理由给 history 背锅，会误导下一个人去改安卓。
> 3. 写测试时两次猜错 `GalleryPost` 字段（以为成人标记看 tags，实际看 `rating`），
>    编译红了才发现 —— 该先读结构再写。
>
> ### 计数变化
>
> `:app` **1795 笔 / 0 红**（未动安卓逻辑）；`:desktop` **1070 笔 / 0 红**
> （删 53 颗空壳 + 新增 21 颗真守卫）。
>
> ⚠️ 已知偶发：`DesktopAccentFillTest` / `DesktopReaderPagingTest` 的离屏渲染用例
> 在跨套件合跑时**偶尔**红，单独跑与重跑均绿。与本轮改动无关，但是噪声，已在账上。
>
> > **这一批真正的交付边界**（`gallery/ui/` 的图片墙那一路）到「零件级复用 + 一屏」为止；
> 那四屏要接，缺的是 `BusinessPorts` 那 17 个成员门面 + `AndroidViewModel` 的桌面实现
> —— 实验量过：单放行 `HomeViewModel` 一颗就 100 处错（`Application` / `AndroidViewModel`
> / `BusinessPorts`）。那是独立一批，不在本批伪装成已完成。
>
> 数字（2026-10-20 实测）：`:app:testDebugUnitTest` **1795 笔 0 红**；
> `:desktop:test` **1050 笔 0 红**；起窗图证
> `_qa/b-gallery-real.png`（画廊真图）与 `_qa/a-home-honest.png`（首页如实报未接）。
> 桌面测试数从 1528 降到 1050 是**有意**的：旧 Fluent 平行线的界面层被删掉后，
> 只钉那些界面的 ruler 一并撤除（按方法粒度摘，不是整颗删文件）。
> 日期：2026-10-18　分支：`compose-migration`　基线 commit：`cecae29`
>
> **这一批把平台边界又推了一格**：`UxPlatformServices` 新增
> `openUrl` / `shareImage` / `openSettingsSubScreen` / `copyImageToClipboard` 四枚
> （原先是 `gallery/ui` 与 `feature/` 里 55 处 `startActivity(Intent(ACTION_VIEW,…))`、
> 分享面板、剪贴板位图那几族），`GalleryNotices` 因此变成干净叶子并进了共享面。
>

> A0 落地物：新增 `:shared-probe`（KMP，commonMain 只有 `Hello()`）+ 两端各一行 `project` 依赖；
> 四条判据 **3 绿 1 部分红**（红在"解析结果一字不变"那一格的 compose 小版本，详见 §6.5 判据 4）
> 用户原话（2026-10-18）：
> > 「之前我自己做了一定量的桌面端，但是我觉得这样维护太麻烦了，能不能重新做一个桌面端直接复用安卓端大屏，最原生的方案？」
> 追问后用户定下的目标口径：**「最原生」= 代码上别再维护两份**（不是 Fluent/Mica 那种 Windows 观感）。
> 用户第二个裁决：**先不写代码，要一份完整方案对照文档**（即本文）。
>
> 本文遵守 `desktop-port-scope-verdicts-2026-10-10.md` §0 的三条纪律，尤其是第一条：
> **`[术]` = 技术上做不到或有硬缺口（必须能指出是哪一行 import / 哪颗平台服务）；`[值]` = 技术上便宜但桌面场景价值不足（那是我们的选择）。**
> 两者**不许混写**。本文每一条 `[术]` 都带可 grep 的锚点；拿不出锚点的一律降级为 `[值]` 或标「未查证」。

---

## 0. 一句话结论

**你的想法在代码上已经成立一半了，而且比我上一版写的路线 A 轻得多。**

用户 2026-10-18 第二轮澄清：

> 「就是我想的是，我可以尽可能更新大屏版本来适配电脑端，而不是自己做新的 ui」

**这不是路线 A（搬迁 UI），而是路线 A′（收口判据层）**：不搬界面，而是把"大屏档位"从 `LocalConfiguration`（安卓专有）解耦成一个**可注入的宽度源**，让同一份大屏 UI 在两端各自读到自己的窗口宽。

关键发现（本文 §1.5）：**这层收口已经做了一半**——`WideScreenPolicy.kt`（235 行纯函数，零 Android 依赖）+ `ImageWallColumns.kt`（唯一取宽入口）说明判据层早就被抽出来了，**只是取宽的入口还绑在 `LocalConfiguration` 上**。

⇒ **改 21 处取值 + 建 1 个 Local，大屏 UI 就能上桌面，不需要重写 58,448 行。**

---

## 1. 现状账单（全部现量，2026-10-18）

### 1.1 两端体量

| 模块 | 文件 | 行数 | 备注 |
| --- | --- | --- | --- |
| `app/`（安卓） | 406 | **93,999** | 其中 267 个文件**零** `android.*` import |
| `desktop/`（你做的那份） | 309 | **52,902** | |
| `engine-probe/`（S0 探针，桌面与 :app 共享 handler） | 10 | 1,465 | |

### 1.2 桌面 52,902 行的去向 —— 这是「维护太麻烦」的真实构成

| 区域 | 文件 | 行数 | 占比 | 性质 |
| --- | --- | --- | --- | --- |
| `desktop/src/test/` | 181 | **30,019** | **56.7%** | **判据/尺子**，其中大量是"扫源码文本"型回归测试 |
| `desktop/gallery/ui/` | 72 | **15,496** | 29.3% | **被迫平行重写的 UI** ← 真正的维护痛点 |
| `desktop/gallery/data/` | 39 | 3,699 | 7.0% | 取数/落盘适配 |
| `desktop/platform/` | 6 | 567 | 1.1% | JDBC/SQLite/HTTP 平台件 |
| `desktop/` 根（壳 + 探针） | 11 | 3,121 | 5.9% | `VeneraDesktopApp.kt` 71KB 等 |

**关键判断**：疲劳感不是来自"桌面端写得多"，而是来自**同一块界面手写两遍**。桌面 UI 的五个巨件就是证据：

| 文件 | 大小 | 安卓侧对应物 |
| --- | --- | --- |
| `VeneraDesktopApp.kt` | 71 KB | `feature/VeneraApp.kt` + `Navigation.kt` |
| `DesktopGallerySearchPane.kt` | 52.2 KB | `SearchScreen.kt` / 画廊搜索 |
| `DesktopGalleryDetailPanel.kt` | 51 KB | 详情面板 |
| `DesktopComicDetail.kt` | 44.8 KB | `ComicDetailScreen.kt` |
| `DesktopComicPane.kt` | 44.3 KB | 漫画域 |

### 1.3 安卓侧的大屏适配 —— 已经落地，不是待办

用户在 `.tablet-audit/shots/10-expanded-1707dp-home.png`（1707dp / 2560×1600）上的现状**已经是桌面可用形态**：左侧 224dp 展开态 NavigationRail、6 列自适应卡片墙、阅读统计、横向滚动行。

已落地的口径（`app/src/main/java/com/venera/compose/components/WideScreenPolicy.kt`，235 行，全仓唯一出处）：

| 能力 | 函数 / 常量 | 值 | 来源 |
| --- | --- | --- | --- |
| 三档断点 | `wideScreenLayoutMode` | 600 / 1300 | master `changePoint` / `changePoint2` |
| 侧栏宽 | `sideBarWidthFor` | 0 / 72 / 224 | master `_kFoldedSideBarWidth` / `_kSideBarWidth` |
| 底栏让位 | `bottomBarClearanceFor` | 76 / 0 / 0 | 由 `sideBarActive` 同源驱动 |
| 换侧栏判据 | `sideBarReplacesBottomBar` | 宽窗且非液态玻璃 | 2026-10-12 新增样式轴 |
| 内容限宽 | `contentMaxWidthFor` | null / 720 / 840 | 720≈中文 45~50 字/行 |
| 图片墙列数 | `imageWallColumnCount` | 200dp/列，下限 3 | 详情页预览格同尺 |
| 通栏 chrome | `wideScreenChromeMaxWidth` | min(540, 宽−48) | master `nav:148` |

> 这份文件的价值在于：**"大屏好不好看"这个问题已经解决了**。路线 A 不需要重新设计桌面布局，它只是让这份已经调好的布局**能在 JVM 上跑**。

### 1.4 安卓侧的耦合分布（决定能不能共享）

`androidx.compose` **3,361** 处 import、`com.venera` 1,770、`kotlinx.coroutines` 431、**`top.yukonga`（Miuix）仅 269**。

按文件计：

| 依赖 | 消费文件数 | CMP 下是否有对应物 |
| --- | --- | --- |
| `androidx.compose.*` | 150 | ✅ 有（`org.jetbrains.compose.*`） |
| `androidx.lifecycle.*` | **31** | ✅ 有（`org.jetbrains.androidx.lifecycle`） |
| `androidx.activity.*` | 22 | ❌ 安卓专有，须留在 `:app` |
| `androidx.navigation.compose` | **2** | ✅ 有 `navigation-compose` 的 KMP 版 |
| `me.saket.telephoto` | 2 | ⚠️ **未查证** CMP 变体 |
| `io.github.kyant0`（backdrop） | 17 处 import | ✅ 该库本身是 Compose Multiplatform（`desktop/build.gradle.kts:175` 已在消费） |

再按目录：

| 目录 | 文件 | 行数 |
| --- | --- | --- |
| `feature/` | 68 | 26,362 |
| `gallery/ui/` | 42 | 22,556 |
| `components/` | 47 | 6,149 |
| `reader/` | 8 | 3,381 |
| `gallery/data/` | 44 | 6,071 |
| `data/` | 81 | 9,615 |
| `engine/` | 8 | 1,639 |
| `source/` | 14 | 5,199 |

**139 个文件** import 了 `android.*`。这些是**永远进不了 commonMain** 的部分（`[术]`，锚点在 §5.3 逐颗点名）。

---

## 1.5 A′ 路线的技术抓手（本文核心）

### 1.5.1 判据层已经抽出来了

`components/WideScreenPolicy.kt` 235 行，**全部是纯函数**：

| 函数 | 签名 | Android 依赖 |
| --- | --- | --- |
| `wideScreenLayoutMode` | `(Dp) -> WideScreenLayoutMode` | **零** |
| `sideBarWidthFor` | `(mode) -> Dp` | **零** |
| `bottomBarClearanceFor` | `(mode, Bool) -> Dp` | **零** |
| `sideBarReplacesBottomBar` | `(mode, Bool) -> Boolean` | **零** |
| `contentMaxWidthFor` | `(mode) -> Dp?` | **零** |
| `imageWallColumnCount` | `(Dp, mode, Dp) -> Int` | **零** |
| `wideScreenChromeMaxWidth` | `(Dp) -> Dp?` | **零** |

文件头注释自称"宽屏档所有通栏悬浮部件的唯一宽度口径"，并且**已经能单测**（`WideScreenPolicyTest`，`stage2-plan` §8.4 记录 9 用例 0 失败）。

⇒ **这部分今天就能直接搬进 commonMain，一行不改。**

### 1.5.2 唯一的绑定点：取宽入口

`components/ImageWallColumns.kt`（30 行）是全仓**唯一**把"取窗口宽"收口的地方：

```kotlin
@Composable
fun rememberImageWallColumnCount(): Int {
    val windowWidth = LocalConfiguration.current.screenWidthDp.dp   // ← 唯一安卓依赖
    return imageWallColumnCount(windowWidth, wideScreenLayoutMode(windowWidth), imageWallHorizontalPadding())
}
```

它的 KDoc 已经把**为什么必须只有一处**写清楚了（`ba3e207` 的事故：四颗页各抄四行，单测传字面量 24.dp，改 token 不会红）。

**这正好是 A′ 的落点**：把 `LocalConfiguration.current.screenWidthDp` 换成自己定义的 `LocalWindowWidth`，安卓侧由 `LocalConfiguration` 供给，桌面侧由 Compose Window 尺寸供给。

### 1.5.3 改造面量化（除 24 处之外没有别的）

现量全仓 `LocalConfiguration.current` 共 **24 处 / 20 个文件**：

| 类别 | 处数 | 处置 |
| --- | --- | --- |
| 只取 `screenWidthDp` | **21** | **换成 `LocalWindowWidth.current`**，其余一字不动 |
| 取 `screenHeightDp` | **2**（`GalleryInfoSheet.kt:311`、`GalleryPostScreen.kt:455`） | 桌面高度语义不同（窗口可任意拉伸），需单列判断，**不能用同一个 Local 硬套** |
| 组合取用 | 1 | 同 `screenWidthDp` 一并处理 |

**这就是 A′ 的全部改动面：21 处一行式替换 + 1 个新 Local + 2 处高度特判。**

### 1.5.4 为什么这比路线 A 便宜得多

| | 路线 A（搬迁 UI） | **路线 A′（收口判据）** |
| --- | --- | --- |
| 改动面 | 58,448 行迁 commonMain | **21 处替换 + 1 个 Local** |
| 是否要拆 `androidx.activity` / WebView | 要 | **不要** |
| 是否要动 FROZEN 页面结构 | 要 | **不要**（只换取宽语句） |
| 是否要废弃 `desktop/gallery/ui` 15,496 行 | 要 | **渐进**：先让大屏 UI 能跑，再逐屏替换 |
| 首版周期 | 周 | **天** |

**A′ 不追求"一行代码跑两端"，它追求"大屏这一层两端通用"。** 这恰好匹配用户原话："尽可能更新大屏版本来适配电脑端" —— 主体仍是安卓大屏，桌面是它的宽窗形态。

### 1.5.5 A′ 的天花板（必须诚实说清）

`[术]`：**21 处收口只能让"判据"跨端，不能让"界面"跨端。**

`wideScreenLayoutMode` 返回 `Expanded` 之后，画那个侧栏、那个 6 列墙、那个详情面板的 `@Composable` **仍在 `:app` 里**。要让它们真在桌面上画出来，`feature/` 26,362 行 + `gallery/ui/` 22,556 行最终还是要进 commonMain —— 也就是路线 A。

⇒ **A′ 与 A 不是二选一，A′ 是 A 的第一步且是唯一无痛的第一步。**
A′ 做完之后，那 21 处已经是可注入的了，届时把 `:shared` 建起来、逐目录搬 UI，**不需要回头再改判据层**。

---

## 2. 两处自我更正（写本文时当场证伪的假设）

### 2.0 第一条：我说错过 Miuix

我在给用户的**口头判断**里写过一句：

> 「Miuix 是 androidx 变体，88 个文件用 `top.yukonga.miuix`，桌面侧必须换 `miuix-ui-desktop`，两套 Compose 版本域不能共存。」

**这句话是错的。** 现量证据：

### 2.1 `miuix-ui` 是一个真正的 KMP 构件

Maven Central 的 `miuix-ui-0.9.4-rc01.module` 里，**同一个版本号**同时发布了六组变体：

`androidApiElements` / `desktopApiElements` / `jsApiElements` / `wasmJsApiElements` / `iosArm64…` / `macosArm64…`

这说明 Miuix **不是**"安卓库 + 另做一个桌面库"，而是**从一开始就按多平台设计**的。

### 2.2 桌面变体直接依赖 JetBrains 的 Compose，不碰 androidx

`miuix-ui-desktop-0.9.4-rc01.module` 的 `desktopApiElements-published`：

```
top.yukonga.miuix.kmp:miuix-core:0.9.4-rc01
top.yukonga.miuix.kmp:miuix-squircle:0.9.4-rc01
org.jetbrains.compose.foundation:foundation:1.12.0-rc01   ← JetBrains 构件
org.jetbrains.kotlin:kotlin-stdlib:2.4.10
```

**零条 `androidx.compose.*`。** 而 `kotlin-stdlib` 要求的 `2.4.10` 与本仓 `gradle/libs.versions.toml:6` 的 `kotlin = "2.4.10"` **逐字相同**；`foundation 1.12.0-rc01` 与本仓 `composeMultiplatform = "1.12.1"` 同属 1.12 线。

### 2.3 material3 版本域：也已查实，不是死结

§5.2 记的那个"唯一真正的技术门禁"，进一步现量后同样是**可解**的。

`org.jetbrains.compose.material3:material3:1.12.0-alpha03` 的 `.module` 里，`dependencyConstraints` 明确写着：

```json
{ "group": "androidx.compose.material3", "module": "material3-lint",
  "version": { "requires": "1.5.0-alpha22" },
  "reason": "material3 is in atomic group androidx.compose.material3" }
```

这条约束的含义是决定性的：**JetBrains 的 material3 `1.12.0-alpha03` 与 androidx 的 `1.5.0-alpha22` 被声明在同一个 atomic group 内**，两者是对齐的同一代 API。也就是说 ——

- `:app` 今天钉的 `androidx.compose.material3:material3:1.5.0-alpha22`（`gradle/libs.versions.toml:15`）；
- 桌面侧要用的 `org.jetbrains.compose.material3:material3:1.12.0-alpha03`；

**这两个不是两个版本域，而是同一代的两种坐标。** 且后者的 `androidApiElements` 直接 `available-at` 重定向到 `material3-android-1.12.0-alpha03.aar` ⇒ **`:app` 完全可以改用 JetBrains 坐标**，在 Android 上落地成的是同一个 AAR。

另外两条同源读数：

- 该版本依赖 `org.jetbrains.compose.foundation:1.12.0-beta01`，而 Miuix 要求 `1.12.0-rc01` ⇒ Gradle 取高者 `1.12.0-rc01`，**同属 1.12 线，与本仓 `composeMultiplatform = "1.12.1"` 一致**；
- `kotlin-stdlib` 要求 `2.2.20`，本仓 `kotlin = "2.4.10"` 取高者，无冲突。

### 2.4 这条更正改变了什么

我把"方案 A 的最大障碍"判错了。原以为要跨两道坎（Miuix 变体 + material3 钉版），实测**两道都不成立**：

- **Miuix 那道坎不存在** —— 它本来就是六平台共享 UI 库，这正是它被选中的价值；
- **material3 那道坎是可解的对齐关系**，不是两个互斥的版本域。

⇒ 路线 A 的 **`[术]` 门禁显著低于我最初的判断**。剩下的是**工期与工程量**问题，不是可行性问题。

### 2.5 顺带发现：仓库里有一句已被证伪的断言

`desktop/build.gradle.kts:170` 现在写着：

```kotlin
// 只是解析探针：桌面侧最终不需要 Miuix，这里量的是它能否与 fluent-desktop 同域共存
```

**这句话是错的**，且它的错法与本仓一号原则冲突：

- 它不是"一个未经验证的计划"，而是**一句既成断言**——"桌面侧最终不需要 Miuix"。
- 现量事实：Miuix 是六平台共享 UI 库（§2.1），安卓侧 88 个文件消费它。**若路线 A 成立，桌面侧恰恰最需要 Miuix**，因为桌面 UI 将由安卓那份 UI 直接驱动。
- 把 `miuix-ui-desktop` 只当"解析探针"这个定性，会让后来者以为"删掉它无损失"。

⚠️ **本批不动这句**（`desktop/build.gradle.kts` 是判定"桌面编译面"的活文件，且 P1 未拍板）。**登记在此**，待 P1 拍板后与探针结论一并修正。这属于"文档与实现不同轨"的静默缺陷，与 `DesktopStaleClaimsTest` 那类尺子管的是同一件事。

🔧 **A0 之后这句已经不只是"错"，而是被读数反向证伪**（§6.5 判据 3）：桌面侧现在**真在运行期装载并组合 Miuix** —— `:shared-probe` 那颗 `Hello()` 里的 `MiuixTheme` 在桌面 JVM 上跑通了（`A0_DESKTOP_COMPOSED_OK` + 语义树里有 `probe` 那一行 + 窗口图证）。所以"只是解析探针"这个定性从今天起是**过时的**，而"桌面侧最终不需要 Miuix"这一句在路线 A/A′ 下不成立。**仍按上面的口径不动那颗文件**，等 P1 拍板时一起改。

---

## 3. 路线对照（四选一）

### 总表

| | **A′：更新大屏层适配桌面**（用户第二轮选定的方向） | **A：CMP 全量共享 UI** | **B：APK 跑在 Windows** | **C：维持现状 + 降维护成本** |
| --- | --- | --- | --- | --- |
| 达成「不再维护两份」 | 🟡 **大屏这一层不再两份** | ✅ 全部 | ⚠️ 表面达成（等于不做桌面） | ❌ |
| 桌面观感 | 中（= 安卓大屏） | 中 | 中（模拟器） | 高（现 Fluent 路线） |
| 改动面 | **21 处替换 + 1 个 Local** | 58,448 行迁 commonMain | 零代码 | 中 |
| 首版成本 | **天（§1.5.3）** | 周（§7） | 极低 | 低 |
| 触碰 FROZEN 页面结构 | **不碰**（只换取宽语句） | 要 | 不碰 | 不碰 |
| 长期维护 | 中（判据一份，界面仍两份直到 A） | **最低** | 最低 | 高 |
| **裁决** | **推荐第一步** | A′ 之后的长期目标 | 否 | 保底 |

### 3.0 路线 A′：更新大屏层适配桌面（用户选定方向）

**做法**：不新建 UI，不搬 UI。只做一件事 —— **把"窗口宽从哪来"这一个问题，从安卓专有 API 上解耦。**

```
现在： LocalConfiguration.current.screenWidthDp  →  wideScreenLayoutMode()  →  UI
改成： LocalWindowWidth.current                 →  wideScreenLayoutMode()  →  UI
         ├─ 安卓侧供给：LocalConfiguration.current.screenWidthDp（值不变）
         └─ 桌面侧供给：Compose Window 的实际宽度
```

**为什么必须先做它**：`WideScreenPolicy.kt` 那 7 个纯函数**已经零 Android 依赖**，`ImageWallColumns.kt` **已经把取宽收成唯一一处**。也就是说，这层架构早就为跨端准备好了，**唯一的绊索是那 21 行取值**。解开它，判据层就是两端通用资产。

**它不做什么（避免误解）**：
- 不删 `desktop/` 任何代码；
- 不动 FROZEN 页面结构；
- 不承诺"界面一份" —— 那是 §3.1 路线 A 的事。

**判据**：`WideScreenPolicyTest` 现有用例全绿 + 手机真机档逐像素不变（21 处替换在安卓侧取到的**仍是同一个数**）。

### 3.1 路线 A：Compose Multiplatform 全量共享 UI

**做法**：把 `app` 的 UI 层搬到 `:shared`（commonMain），`:app` 变成 Android 入口壳，`:desktop` 砍掉 15,496 行平行 UI 只留 `main()`。

**为什么它符合用户目标**：用户要的是"代码上别再维护两份"。只有让**同一份 `@Composable` 同时驱动两端**才能达成；其他任何方案都只是把"两份"换个地方放。

**为什么它"最原生"**：CMP 在桌面侧用 Skia 直接画到 JVM 窗口，产出是**原生 exe/msi，不装任何运行时/模拟器**，这一点比方案 B 强。虽不等同 WinUI 的观感，但用户已明确"观感可以让步，代码别两份"。

**它的代价必须说清**：`feature/` 26,362 + `gallery/ui/` 22,556 + `components/` 6,149 + `reader/` 3,381 ≈ **58,448 行**要逐批迁进 commonMain，且其中 139 个文件的 `android.*` 依赖要拆成 expect/actual 或窄接口。这是**以周计**的工作，不是一次重构。

### 3.2 路线 B：APK 跑在 Windows

`[术]` 缺口：**WSA（Windows Subsystem for Android）微软已于 2025-03-05 终止支持**，Win11 上不再有免装第三方安卓运行时的路。剩下 `[值]` 判断：第三方模拟器跑的是 ARM 翻译层，大图墙滚动必然掉帧，且"装一个模拟器"本身就不是桌面软件。

**裁决：否。** 它满足"代码一份"，但不满足"桌面端"。

### 3.3 路线 C：维持现状 + 降维护成本

不动 UI 双轨，只把桌面侧重复的**判据**收掉。可操作性有一条硬支撑：**桌面 30,019 行测试里，大量是"扫描源码文本"的回归尺子**（`DesktopStaleClaimsTest` 44KB、`DesktopBlurCostProbeTest` 40KB、`ForensicFlagsTest` 37.7KB 等）。这些在共享 UI 之后**大部分会自动失去意义**，因为被扫的"对岸"不再是另一份实现。

**裁决：保底。** 若探针红，且不愿投路线 A 的周期，这是唯一可行的降本方向。

---

## 4. 路线 A 的分批路径（每批两端都能跑）

原则：**永远保持 `:app` 可发布**。任何一批的失败都能停在"上一批绿"的状态。

| 批次 | 内容 | 产出行数变化 | 判据 |
| --- | --- | --- | --- |
| **A0** | 探针（§6）—— **已跑完（2026-10-19 凌晨），四条判据 3 绿 1 部分红，读数与两条量具改判在 §6.5** | 新增 **197 行 / 6 颗新件**（`:shared-probe` 四颗 121 行 + 桌面入口 49 行 + 桌面语义树判据 27 行）；现有文件**只加行、不动结构**：`settings.gradle.kts` +1 颗 include、`:app`/`:desktop` 各 +1 行 `project` 依赖、目录 +2 颗插件别名、两个构建脚本各加注释 | 见 §6.3（含 §6.5 那两条当场改判） |
| **A1** | 建 `:shared`，把**纯逻辑**搬入（`gallery/data` 6,071 + `data/db` 等零 android 部分），`:app` 与 `:desktop` 同时依赖 | 无 UI 变化 | 两端编译 + 现有测试全绿 |
| **A2** | `components/` 6,149 搬入 commonMain（这是 UI 底座，Miuix 消费最集中） | — | 两端起窗 |
| **A3** | `gallery/ui/` 22,556 搬入；**同时删掉 `desktop/gallery/ui` 15,496** | **−15,496** | 桌面截图对拍安卓大屏 |
| **A4** | `feature/` 26,362 + `reader/` 3,381 搬入 | — | 同上 |
| **A5** | 清理：`desktop/src/test` 中"扫源码"型尺子批量退役 | **−20,000 量级** | 测试全绿 |

**A3 是拐点**：从这一批起，你的界面只存在一份，后面每加一个功能都不再需要写第二遍。A5 是第二笔回收。

预计终态：`desktop/` 从 52,902 行降到 **约 5,000 行**（壳 + 平台件 + 必要尺子）。

---

## 5. 硬缺口清单（逐颗点名，`[术]`）

### 5.1 已确认可共享（无缺口）

- Miuix：§2 已证，六平台同版本发布，桌面变体零 androidx。
- Backdrop / kyant-shapes（17 处 import）：`desktop/build.gradle.kts:175-176` 已在消费，本身即 CMP 库。
- `androidx.compose.*` → `org.jetbrains.compose.*`：**import 包名不改**，仅靠依赖替换即可（Compose Multiplatform API 与 androidx 同源）。这是迁移相对便宜的原因。
- `androidx.lifecycle`（31 文件）：`org.jetbrains.androidx.lifecycle` 提供 `ViewModel` / `collectAsStateWithLifecycle`。

### 5.2 版本域：已查实为「可对齐」，不是冲突

`gradle/libs.versions.toml:13-15` 记的那次事故是真的：

> material3 必须与 Miuix 运行期实际解析到的版本一致。原先写 1.3.1，但 `miuix-ui 0.9.4-rc01` 只在 runtimeClasspath 上把 material3 顶到 `1.5.0-alpha22` ⇒ 编译期/运行期 API 不一致，参数列表变过的组件（如 `ModalBottomSheet`）会在运行时抛 `NoSuchMethodError` 闪退。

但它是**版本漂移事故**，不是"两端不能共存"。§2.3 的 `.module` 读数说明：

| 坐标 | 版本 | 关系 |
| --- | --- | --- |
| `androidx.compose.material3:material3` | `1.5.0-alpha22` | 本仓 `:app` 今天钉的 |
| `org.jetbrains.compose.material3:material3` | `1.12.0-alpha03` | **同一 atomic group**，其 `dependencyConstraints` 反过来锁 `material3-lint:1.5.0-alpha22` |

⇒ **`:app` 可以整体改用 `org.jetbrains.compose.material3` 坐标**，Android 侧解析到 `material3-android-1.12.0-alpha03.aar`。**两端从此是同一个版本，`libs.versions.toml:13-15` 那条"编译期/运行期不一致"的地雷反而被结构性拆掉了。**

⚠️ 但这需要**实测确认** `material3-android-1.12.0-alpha03` 与 `miuix-ui-android` 在 `:app` 的 runtimeClasspath 上解析出的 material3 是同一个版本 —— 这正是 A0 探针判据 4 要量的东西。**在探针绿之前，本条按"高置信度推断"对待，不按"已验证"引用。**

✅ **A0 已跑（§6.5），这一条要按读数改判成两半**，不许整条当"已验证"引用：

- **量到了**：一颗吃 `org.jetbrains.compose.material3:material3:1.12.0-alpha03` 的共享模块挂上 `:app` 之后 —— material3 **仍解析为 `1.5.0-alpha22`**、`:app:checkDebugDuplicateClasses` **过**（两条坐标族同挂而不撞同名类）、APK 照样出包。基线那半更要紧：`:app` **今天就已经**经由 `com.materialkolor:material-kolor:5.0.1` 把 JetBrains 那颗 material3 带在 runtimeClasspath 上（`_qa/a0/baseline_app_deps.txt:887`），所以"两颗坐标共存"不是 A0 新引入的风险，是既有事实。
- **没量到**：本节上面那句"`:app` 可以整体改用 `org.jetbrains.compose.material3` 坐标、落地成同一个 AAR" —— 那是**换坐标**这个动作的结果，A0 没做这个动作（A0 只加了一颗消费者）。仍按推断对待，等 A1 真换时再量。
- **量出来的是另一件事**：抬走 `:app` 版本域的不是 material3，而是 CMP 插件的 `compose.*` 访问器 —— 它把 `androidx.compose.*` 全家从 1.12.0 抬到 1.12.1。见 §6.5 判据 4 与 §8 新增的 P9。

### 5.3 永远不能进 commonMain 的部分（`[术]`，锚点逐颗）

| 能力 | 锚点 | 桌面处置 |
| --- | --- | --- |
| WebView 网页登录 / CF 绕过 | `data/network/CloudflareBypassActivity.kt`（14 处）、`feature/sourcemanage/WebLoginScreen.kt`（25 处）、`engine/VeneraJsEngine.kt`（30 处） | **已知缺口**，`desktop-port-scope-verdicts` #9b 已登记 |
| MediaStore / ContentResolver 落盘 | `gallery/data/GallerySaver.kt`、`reader/VeneraReaderScreen.kt`（2 处） | 换"选目录"（同档 #12） |
| WorkManager 追更 | `data/db/FollowUpdatesWorker.kt`（61 行） | 砍（同档 #11） |
| 运行时权限 / 通知渠道 | `download/ComicStorageRoot.kt` 等 | 砍（同档 #14） |
| 分享 `ACTION_SEND` | — | 已有替代（同档 #13） |

**结论**：这些是**平台能力缺口，不是 UI 缺口**。它们各自都是窄接口（expect/actual 或已有的 `PathProvider` 式门面），**不构成"UI 不能共享"的理由**。用户要共享的是"大屏界面"，那部分恰好是最干净的一层。

---

## 6. A0 探针（建议的第一步）

### 6.1 目的

用**最小代价**回答一个问题：**`:shared` 能否同时被 `:app`（androidJvm）和 `:desktop`（jvm）依赖并起窗。**

不回答任何 UI 设计问题，不迁移任何现有代码。

### 6.2 内容

1. 新建 `:shared-probe` KMP 模块，`commonMain` 只放：

   ```kotlin
   @Composable
   fun Hello() { MiuixTheme { Text("probe") } }
   ```

2. `commonMain` 依赖：`miuix-ui` + `lifecycle-viewmodel-compose`（KMP 版）+ `material3`（**这一颗是探针要量的靶子**）。
3. `:app` 加 `implementation(project(":shared-probe"))`；`:desktop` 同。
4. 两端各挂一个入口渲出 `Hello()`。

### 6.3 判据（四条，全绿才算过）

| # | 判据 | 失败含义 |
| --- | --- | --- |
| 1 | `:shared-probe:compileKotlinMetadata` 通过 | KMP 结构本身有问题 |
| 2 | `:app:assembleDebug` 出包 | 安卓侧解析不了 |
| 3 | `:desktop:run` 起窗并显示 `probe` | 桌面侧解析/运行不了 |
| 4 | **`:app` 的 material3 仍解析为 `1.5.0-alpha22`**，且 `app/build/outputs/apk/**` 的包体清单与探针前**逐字一致**（除新增模块） | **版本域被打穿** —— 这条是沿用 `desktop/build.gradle.kts:7-9` 的既有判据："APK 与 material3 解析结果一字不变" |

> 判据 4 是复制本仓既有的纪律（`windows-r1f-s0-spike` 里 S0-1 用的正是这条）。它防的正是 material3 那种"编译过了、运行时 `NoSuchMethodError`"的静默失败 —— 也是 `libs.versions.toml:13-15` 已经栽过一次的坑。

⚠️ **跑的时候当场改判的两条量具口径**（改的是尺子不是结论，读数见 §6.5）：

- 判据 1 的字面那颗 `:shared-probe:compileKotlinMetadata` 在"只有 JVM 族 target"的配置里**恒 `SKIPPED`**，拿它当判据是假开关 ⇒ 改判成 `:shared-probe:build` 全绿 + 两端各落一颗 `HelloKt.class`。
- 判据 3 的字面 `:desktop:run` 今天指向的是**真 App**（`mainClass = VeneraDesktopKt`），要在那一屏摆出 `probe` 就得动 71KB 的壳，与 §6.4"不改现有文件结构"直接冲突 ⇒ 按本仓 S0-2 的既有手法改走独立任务 `:desktop:sharedprobe`（**同一颗 `runtimeClasspath`**，只换 main class，与 `spike`/`ime`/`mica` 同一族）。

### 6.4 成本与回退

约 A0 半天量级；不改任何现有文件的结构，只新增一个模块 + 两行依赖。红了就删模块，回到今天的状态。

### 6.5 A0 读数（2026-10-19 凌晨现量，全部落在 `_qa/a0/`）

取数脚本 `_qa/a0/run_criteria.sh`（每笔一份日志 + `EXIT=` 行），基线在**改任何构建文件之前**取。

#### 判据 1 —— 绿（但字面那颗任务是假的）

| 读数 | 值 |
| --- | --- |
| `:shared-probe:build` | `BUILD SUCCESSFUL in 33s`，`EXIT=0`，28 颗任务全 executed（无 UP-TO-DATE 混进来） |
| `compileKotlinMetadata` / `compileCommonMainKotlinMetadata` | 两颗都 `SKIPPED` |
| `build/libs/shared-probe-metadata.jar` | 773 B，里面只有 `META-INF/kotlin-project-structure-metadata.json`，**零 class** |
| `compileKotlinJvm` | executed ⇒ `build/classes/kotlin/jvm/main/com/venera/sharedprobe/HelloKt.class` + `shared-probe-jvm.jar`(4,597 B) |
| `compileAndroidMain` | executed ⇒ `build/classes/kotlin/android/main/.../HelloKt.class` + `outputs/aar/shared-probe.aar` |

**结论**：同一份 `commonMain` 源码在两端各编出一颗 `HelloKt.class`，KMP 结构本身没问题。
`SKIPPED` 的成因不是"没找到源"（两端 class 都落盘了），是 KGP 在只有 JVM 族 target 时不跑 common metadata 编译 —— 将来 A1 若真发 klib 给非 JVM 族用，那颗任务才会活。

**探针抓到的第一条 `[术]`（会咬住 A1）**：`com.android.library` + `org.jetbrains.kotlin.multiplatform` **在 AGP 9 起互斥**，起手就红：

> The 'com.android.library' plugin is not compatible with the 'org.jetbrains.kotlin.multiplatform' plugin since AGP 9.0. Solution: Replace the 'com.android.library' plugin with the 'com.android.kotlin.multiplatform.library' plugin

⇒ 建正式 `:shared` 时没有"library 模块 + kotlin-android"那条老路，只能吃 `com.android.kotlin.multiplatform.library`；它的 DSL 也不同（`kotlin { android { namespace/compileSdk/minSdk } }`，manifest 默认在 `src/androidMain/AndroidManifest.xml`，**没有** `sourceSets["main"].manifest.srcFile(...)` 那行老写法）。AGP 9.3.2 的这两条是从缓存里那颗 `gradle-9.3.2-sources.jar` 读出来的，不是猜的。

#### 判据 2 —— 绿

`:app:assembleDebug` `BUILD SUCCESSFUL in 3m 59s`，`EXIT=0`，四份 ABI APK 出包（universal 101,930,110 B）。
`:app:checkDebugDuplicateClasses` 这一格**过了** —— 两条坐标族（`androidx.compose.material3` 与 `org.jetbrains.compose.material3`）同时挂在 runtimeClasspath 上而没有同名类冲突。

#### 判据 3 —— 绿（三路证据：stdout + 图证 + 语义树）

| 路 | 读数 |
| --- | --- |
| `:desktop:sharedprobe` stdout | `A0_SHARED_PROBE_START jvm=21.0.12.1` / `CLASS_FROM=…/shared-probe/build/libs/shared-probe-jvm.jar` / `MATERIAL3_FROM=…/material3-desktop-1.12.0-alpha03.jar` / `A0_DESKTOP_COMPOSED_OK`，`EXIT=0` |
| 窗口图证（WGC，occlusion-safe，`isForeground=false` 没抢前台） | 420×260 那扇 "Venera A0 shared-probe" 窗左上角就是 `probe` 那一行 |
| 进程内语义树 `:desktop:test --tests DesktopSharedProbeTest` | `tests="1" skipped="0" failures="0" errors="0"` —— Miuix 的 `Text` 在桌面 JVM 上把 `"probe"` 露进了语义树 |

`MATERIAL3_FROM` 那行是判据 4 在桌面侧的另一半：运行期真正装载的 material3 类来自 JetBrains 那颗 `-desktop` jar，不是编译期签名对不上而运行期静默换脸。

#### 判据 4 —— **部分红**

守住的那格：`androidx.compose.material3:material3` 在 `:app` 的 `debugRuntimeClasspath` 上**仍解析为 `1.5.0-alpha22`**（`_qa/a0/post_app_deps.txt:1023`），加上判据 2 那格 `checkDebugDuplicateClasses` 过 ⇒ **§2.3 那条"同一 atomic group、可对齐"从高置信度推断升为包内实测**。

破掉的那格（这就是判据 4 要抓的东西）：

| 变化 | 数量 | 成因 |
| --- | --- | --- |
| `androidx.compose.*` 全家 `1.12.0 → 1.12.1` | 54 行坐标（`ui`/`foundation`/`animation`/`runtime`/`ui-text`…） | `:shared-probe` 的 commonMain 吃的是 CMP 插件 1.12.1 的 `compose.*` 访问器，它把 androidx 那棵树整体抬了一个补丁号 |
| `org.jetbrains.androidx.lifecycle:*` `2.9.6 → 2.11.0` 并新增 `-android`/`-jvm` 各若干颗 | 新增 19 颗坐标（456 → 475） | 探针递的那颗 KMP lifecycle 版本比现量那族高 |

包体清单那一半（`_qa/a0/{baseline,post}_apk_entries.txt`，逐条目 `长度|CRC|名字`）：

- 条目 296 → 298；非 dex 条目里 **274 颗 CRC 逐字相同**；
- 未压缩总字节 105,704,232 → 105,723,295（**+19,063 B**，就是探针自己的 class 加一颗新增 `META-INF/…/lifecycle-common/LICENSE.txt`）；
- dex 从 21 颗变 22 颗、编号整体漂一位（`classes11.dex` 这类名字变了，内容按上面那格算账）。

⚠️ **未归因的一格，登记不猜**：APK 落盘字节数 111,996,729 → 101,930,110（**−10,066,619**），而未压缩内容只多 19KB ⇒ 差在压缩/对齐那一层。本笔读数没量到成因（要归因得回退改动重打一次基线，逐条目比 `Stored`/`Defl:N` 分布）。**这一条不许被引用成"探针让包体变小了"**。

#### 四条合起来的裁决

**A0 通过**：`:shared` 能同时被 `:app` 与 `:desktop` 依赖并起窗，且两端渲染的是同一颗 class。
**代价是一格新的钉版问题**：共享模块一吃 CMP 插件的 `compose.*` 访问器，就把对岸的 androidx Compose 从 1.12.0 抬到 1.12.1。A1 起手要先拍这一条（要么 `:app` 认 1.12.1 并把它写进 `libs.versions.toml`，要么共享模块改成逐字钉 androidx 坐标、不用访问器）。

#### 既有尺子回归（A0 不许把谁弄红）

两套全跑，不是只跑新增那颗：

| 套 | 类数 | 笔数 | 失败/错误 |
| --- | --- | --- | --- |
| `:app:testDebugUnitTest` | 199 | **1,670** | 0 / 0 |
| `:desktop:test` | 175 | **1,523** | 0 / 0 |

`desktop` 那 1,523 = 上一批手册记的 1,522 **+ 本批新增的 1 笔**（`DesktopSharedProbeTest`），逐笔数过而不是只看 `BUILD SUCCESSFUL`；两笔任务都是 executed 而非 UP-TO-DATE。读数：`_qa/a0/r1_app_unit_tests.log`、`_qa/a0/r2_desktop_tests.log`。

#### 待真机那一格（本笔没量，也不许当成量过）

安卓侧只量到"APK 出得来 + 入口已合并进清单"（`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml` 里现数到 1 处 `com.venera.sharedprobe.ProbeActivity`）。**屏上有没有那一行 `probe`，安卓侧今天没有读数** —— 本机 adb 自验是禁的（手册 §8.1），要用户装包后：

```
adb shell am start -n com.github.w13630039663bit.venera.miuix/com.venera.sharedprobe.ProbeActivity
```

（那颗 Activity 声明在 `:shared-probe` 自己的 manifest 里、`exported=true`，就是为了这一跳能从 shell 走到。）




---

## 7. 成本估算（区间，非承诺）

| 路线 | 首版 | 长期 |
| --- | --- | --- |
| A（含 A0–A5） | **多轮周**；A0 半天，A1~A4 按层推进 | **最低**：一份 UI，新增功能两端口同时具备 |
| B | 约 0 | 不成立 |
| C | 数天（只砍判据与重复层） | 高：两份继续漂 |

**A 的成本不是"重写桌面"，而是"把已有的 58,448 行搬一个目录"** —— 这笔账和重写完全不同，也是它比现状便宜的原因。

---

## 8. 待拍板项

> 用户第二轮已定方向：**"尽可能更新大屏版本适配电脑端，不做新 UI"** ⇒ 路线 A′。
> 因此下表以 A′ 为主线重排，路线 A 的项降为后续。

| # | 项 | 建议 |
| --- | --- | --- |
| **P1** | 是否落 `LocalWindowWidth`（21 处替换） | **建议做**。这是 A′ 的全部内容，改动面已量化到 §1.5.3，且**手机档逐值不变**（安卓侧供给值仍是 `screenWidthDp`） |
| **P2** | `LocalWindowWidth` 的安卓侧供给 | 建议**直接转发 `LocalConfiguration.current.screenWidthDp`**，不改任何现有取值语义 ⇒ 9 个 `WideScreenPolicyTest` 用例必须全绿 |
| **P3** | 两处 `screenHeightDp`（`GalleryInfoSheet.kt:311`、`GalleryPostScreen.kt:455`） | 建议**本次不动**，单列一项。桌面窗口可任意拉伸，高度语义与手机不同，硬套会做出"平板能看、桌面崩"的形态 |
| **P4** | 是否把 `WideScreenPolicy.kt` + `ImageWallColumns.kt` 搬进 `:shared` | 建议**先只在 `:app` 内落 A′**，`:shared` 留到 A′ 验完再说 —— 避免同时动两件事 |
| **P5** | `desktop/` 那 15,496 行平行 UI 的处置 | 建议**保留**，A′ 不删任何桌面代码。等大屏 UI 真能在桌面画出第一屏，再逐屏替换 |
| **P6** | 30,019 行桌面测试 | 暂不动。A′ 不改变任何桌面行为 |
| **P7** | 方案 B（APK 跑 Windows） | 建议登记出局理由（WSA 已终止 = `[术]`） |
| **P8** | 长期是否仍走路线 A（UI 搬 commonMain） | **A′ 之后必答**。§1.5.5 已说明 A′ 的天花板，但 A′ 是 A 的无痛前置，不冲突 |
| **P9** | **A0 新撞出来的那一格**：共享模块吃 CMP 插件的 `compose.*` 访问器，会把 `:app` 的 `androidx.compose.*` 从 1.12.0 抬到 1.12.1（§6.5 判据 4） | **A1 起手必答，二选一**：① `:app` 认 1.12.1（把 `libs.versions.toml:8` 那颗 `compose = "1.7.8"` 一并改成真实解析值，让目录说真话）；② 共享模块不用访问器，逐字钉 androidx 坐标（`androidx.compose.ui:ui` 等），代价是 commonMain 里 `compose.*` 的写法要换。**建议 ①**：`:app` 今天实际跑的就是 1.12.0，抬一位补丁号的改动面比"每颗共享件都手写坐标"小 |

---

## 9. 与既有文档的关系（防打架）

| 文档 | 与本文关系 |
| --- | --- |
| `windows-port-feasibility-2026-10.md` | 那份评的是 R1-F（**平行重写**，方案 3.3 的 C 路线前身）。本文提出的是它没评估的第四条：**共享 UI**。不推翻它，只补充 |
| `desktop-port-scope-verdicts-2026-10-10.md` | **本文尊重其全部裁决**（平台件不搬、WebView 已知缺口、后台追更砍）。本文的 `[术]` 项与它逐条对应 |
| `large-screen-adaptation-stage2-plan-2026-10-02.md` §0.1 | 那份冻结 R1-F 的理由是"两边都建侧栏 = 第二套布局系统"。**本文路线 A 正是对那个理由的正面回答**：共享 UI 后只有一套侧栏 |
| `FREEZE-STATEMENT.md` | 8 个 FROZEN 页面在 A3/A4 需要解冻评审。**A0/A1 不触碰 FROZEN 任何文件** |
| `desktop-ruler-catalog.md` | A5 的输入 |

---

## 10. 未查证项（诚实登记，不许当结论用）

### 已查实的（从"未查证"里划掉，两处都是当场自我更正）

- ~~CMP material3 与 androidx 1.5.0-alpha22 是否同源同版~~ ⇒ **§2.3 已查实：同一 atomic group，可对齐**（置信度高，但**仍需 A0 判据 4 实测**后才算"已验证"）。**A0 已实测（§6.5）：共存这一半成立（解析值不动、`checkDebugDuplicateClasses` 过）；"换坐标落地成同一份 AAR"那一半 A0 没做那个动作，仍留在下面的未查证清单第 6 条。**
- ~~Miuix 是否 android/desktop 两套变体~~ ⇒ **§2.1 已查实：六平台同版本单一 KMP 构件**。A0 补了运行期那一格：桌面侧真装载并组合成功（`A0_DESKTOP_COMPOSED_OK` + 语义树 + 图证）。

### 仍未见底的

1. `me.saket.telephoto` 有无 CMP 变体（2 个文件消费：`reader/VeneraReaderScreen.kt` 等）。
2. `androidx.activity` 那 22 个文件的 `rememberLauncherForActivityResult` / `CustomTabs` 等，在共享层要换成什么形状（预计走 `PathProvider` 式门面，**具体接口未查证**）。
3. 桌面侧 `Window` 语义（`VeneraPreferences` 里与窗口/沉浸式相关项）在 CMP 下的落点。
4. `androidx.compose.material.icons.extended`（本仓 `:app` 在用）在 CMP 下是否有对等构件 —— **本文未量**。
5. `desktop/gallery/data` 那 3,699 行里，有多少与 `app/gallery/data` 的**判据逻辑重复**（A1 能直接删掉的比例）—— 未逐颗对账。
6. **A0 新登记的两条**：
   - `:app` 把 material3 坐标整体换成 `org.jetbrains.compose.material3` 之后，Android 侧落地的是不是同一份 AAR —— A0 只新增了消费者，没换 `:app` 的坐标（§2.3 那一半仍未验）。
   - **APK 落盘字节少了 10,066,619 B 而成未压缩内容只多 19,063 B** —— 差在压缩/对齐那一层，本笔没量成因（要归因得回退改动重打基线，逐条目比 `Stored`/`Defl:N` 分布）。**在归因之前，这一条不许被引用成"共享模块让包体变小"**。
7. **A0 抬版那一格的下游**：`androidx.compose.*` 若真按 P9 的 ① 抬到 1.12.1，现量读数以 `1.12.0` 署名的那两处行为断言（`OverscrollDisablePolicyTest` 的 foundation 注释、`DesktopKeyNavigationTest` 的 `FocusOwnerImpl` 行号引用）要不要跟着重测 —— 未量。

**以上任何一条都不允许在没有 grep / 实测的情况下被引用为"做不到"。**
