# Jetpack Compose Audit Report

Target: D:\venera-compose\app
Date: 2026-10-19
Scope: `app/src/main/java`（418 个 `.kt`：gallery 148 / data 81 / feature 68 / components 48，其余 73）+ Compose 编译器报告（经技能 init 脚本注入生成）
Excluded from scoring: `app/src/test`（210 颗判据文件，其中 ~24 颗是源码扫描尺）、`desktop/`（工作树里未提交的桌面侧改动，用户口径"管好安卓端"）、`shared-probe/`（探针模块）
Confidence: **High** — 全模块逐类扫描 + 编译器报告 + 代表性文件逐行读取（hero 链路四屏、主墙、设置、阅读器、收藏）
Overall Score: **58/100**（加权：6×35 + 5×25 + 7×20 + 5×20 = 575 → 57.5 → 就近 58）

## Scorecard

| Category | Score | Weight | Status | Notes |
|----------|-------|--------|--------|-------|
| Performance | 6/10 | 35% | needs work | Strong Skipping ON、具名件可跳过率 88%、动画值读在绘制期是样板级；主要扣分在 182 处非生命周期感知 `collectAsState` 与几处组合期静默吞错 |
| State management | 5/10 | 25% | needs work | typed state factory 纪律全仓 0 违例、状态上提清楚；hero 这一族"进度散成六条曲线 + 进程内可变单例跨窗共享"是最重的 State 债（本仓自己的窄域审计打分 3/10） |
| Side effects | 7/10 | 20% | solid | `LaunchedEffect` 键控正确、登记/注销成对且有源码尺；扣分在吞错与复位只靠注释约定 |
| Composable API quality | 5/10 | 20% | needs work | `modifier`/lambda 事件/无 `MutableState` 形参三项全干净；巨件（3400+ 行的屏）、UI 文案硬编码无资源路径、preview 只覆盖两件旧屏 |

## Critical Findings

1. **State/Performance: hero 转场把"此刻飞到哪一档"散成六条 alpha 曲线 + 三套时长 + 一枚进程内单例**
   - Why it matters: 同一份进度语义写在 `exitPageAlpha`/`exitBodyAlpha`（`GalleryExitExpansion.kt:72,114`）、`handoffPageAlpha`/`handoffBodyAlpha`（`GalleryHandoff.kt:59-64`）、两端各自的历史常量与 `GalleryFlyIn` 单例里；每条曲线都在治上一条的副作用，动一处要人肉检查六处。屏上"乱"是这一族的必然结果（计划文档 §2.4/§4.4 的真机帧证支持这句判断）。
   - Evidence: `gallery/domain/GalleryHandoff.kt:59-64`、`gallery/domain/GalleryExitExpansion.kt:72,114`、`gallery/ui/PixivWorkScreen.kt:459-482`（`pageAlpha()` 三段拼接）、`gallery/ui/GalleryPostScreen.kt:800-829`
   - Fix direction: 批 B 机制换体——一条 expansion 标量两层；`flightFrame` 判据已落 L0 并有尺（`GalleryFlightFrameTest`，2026-10-19）。删三条曲线用判据⑤负向尺送终。
   - References: <https://developer.android.com/develop/ui/compose/performance/states>, <https://developer.android.com/develop/ui/compose/architecture>

2. **State: 跨窗口交接靠 `object GalleryFlyIn` 可变单例，"读一次就翻档"的时序全靠调用方自觉**
   - Why it matters: `payload/origin/stage` 是进程内快照状态；对面 Activity 组合期读 `origin == null` 判"深链不飞"（`GalleryPostScreen.kt:604-616`），漏一次 `consume` 就是"拿上一跳留下的旧矩形飞"（仓内已有判式专门钉这个：`GalleryViewerHeroWiringTest`）。
   - Evidence: `gallery/ui/GalleryFlyIn.kt:78-97`、`gallery/ui/GalleryFlyIn.kt:188-194`（consume）、`components/ChromeCaptureSuppress.kt:41-86`（第二枚同族单例）
   - Fix direction: 批 C（作品页搬进单窗口）是决定性那一步（计划 §5 批 C，要用户点头）；批 B 先以"接线成对尺"+无锚侧滑退路把"静默交错"的面收小。
   - References: <https://developer.android.com/develop/ui/compose/architecture>

3. **Performance: 182 处 `collectAsState()` vs 42 处 `collectAsStateWithLifecycle()`，混用无口径**
   - Why it matters: app-scope 偏好流代价不大，但按页的 store 流（`store.favorites`、`account.authState`、`downloadManager.tasks`）在 STOPPED 继续收集是纯浪费；且 `feature/FavoritesScreen.kt:806` 写成 `collectAsState().value` 现流现取，读法最差的一档。
   - Evidence: `feature/DownloadScreen.kt:68`、`gallery/ui/GalleryFavoritesBody.kt:92`、`gallery/ui/GalleryPixivAccount.kt:92`、`feature/FavoritesScreen.kt:806`
   - Fix direction: 按页流一律 `collectAsStateWithLifecycle()`；prefs 流二选一并写成仓内纪律（要么全转 lifecycle-aware，要么留 collectAsState 但停止混用）。
   - References: <https://developer.android.com/topic/architecture#lifecycle-aware-collection>

4. **Composable API: 巨件三颗 + 屏显文案硬编码内联（无资源路径）+ preview 只覆盖两件旧屏**
   - Why it matters: `GalleryScreen.kt` 3433 行 / `PixivWorkScreen.kt` 2162 行 / `GalleryPostScreen.kt` 2118 行——批 A 那次"同一个动作写了四遍"就是巨件的直接产物；全仓 UI 中文（含 `contentDescription` 拼串）无 `stringResource` 通道，i18n/改文案要翻源码；`@Preview` 实测只有 `HomeScreen.kt:1102-1120` 与 `VeneraComponentsPreview.kt` 两片，gallery 全部新件（飞行体、锚闸门、画师页）0 preview。
   - Evidence: `gallery/ui/GalleryScreen.kt:3138`（`CoverNode` 内联文案）、`gallery/ui/GalleryPixivHome.kt:1043`、`gallery/ui/PixivWorkScreen.kt:313`（失败句子内联）
   - Fix direction: 继续按既有先例拆（`PixivWorkPage.kt`/`GalleryPageAxis.kt` 是拆对了的样子）；文案收进单一常量表是本仓现状下最便宜的第一步；给共享件补 preview。
   - References: <https://developer.android.com/develop/ui/compose/api-guidelines>, <https://developer.android.com/guide/topics/resources/string-resource>, <https://developer.android.com/develop/ui/compose/tooling/previews>

5. **Side effects: 组合期副作用静默吞错**
   - Why it matters: `feature/HomeScreen.kt:140-142` 的 `LaunchedEffect(Unit){ runCatching{ sourceManager.checkUpdates() } }` 失败无日志无提示——本仓自己的纪律（"失败要回答一句人话"）在这类 effect 上破例；`data/update/ApkDownloader.kt:225-254` 通知三处 `runCatching` 吞掉同理。
   - Fix direction: 吞错处最少 `Log.w` 带原因；用户可感路径（下载/安装/通知）接回仓内既有的说话口径。
   - References: <https://developer.android.com/develop/ui/compose/side-effects#launchedeffect>

## Adjacent Findings

### Android Launch UX

- Android 12+ splash icon status: **not configured** — `app/src/main/res` 无 `windowSplashScreenAnimatedIcon`/`postSplashScreenTheme`（0 命中）。minSdk 33 ⇒ 系统用默认应用图标渲染静态 splash，本仓无自配矢量/动画资源 ⇒ **无模糊矢量风险，也无自定义启动图**。若将来接入，按"API 31+ 解析到 `<animated-vector>` wrapper（空 animator 即可）且 wrapper 引用另名矢量"的规范来。
- References: <https://developer.android.com/develop/ui/views/launch/splash-screen>, <https://issuetracker.google.com/issues/520672537>

## Category Details

### Performance — 6/10

**Ceiling check**

- Strong Skipping: **on**（`compose_audit/debug/app-module.json` → `featureFlags.StrongSkipping=true`）
- Ceiling table applied: SSM-on（重启只由参数变化或快照读驱动）
- Module-wide `skippable%`: 1323/2280 restartable = 58%（含匿名件）
- Named-only `skippable%`: 428/486 = **88%（binding 的那枚）**——`app-composables.csv` 只列具名件；58 颗非跳过件抽样全是 `remember*` 返回值助手（`rememberContentWidth`/`coverSharedElement`/`veneraCoverCornerRadius` 等），按形状本应 non-skippable，不构成 churn 证据
- Unstable shared types from compiler: inferred-unstable 116/644 类；与具名件签名交叉对表 = **0 颗以参数进 UI**（`knownUnstableArguments` 112/34199 = 0.3%）
- SSM-on binding evidence: 无系统性实例重造；`markedStableClasses` 12（`@Stable` 用在 `GalleryFlyIn`/`ChromeCaptureSuppress` 等快照件）
- Qualitative score: 6/10；Ceiling: none；Applied: 6/10

**What is working**（都有行号可查）
- 动画值读在绘制期：`GalleryEntryFlyBody.kt:77-118`、`GalleryExitFlyBody.kt:77-119`（批 A 把 `Float` 实参翻回 `() -> Float`，消掉了唯一一处每帧重组——§2.7 的病灶）
- Lazy 键：`GalleryScreen.kt:2817` `items(cards, key = { it.post.uid })`、`GalleryHistoryScreen.kt:376`
- 布局回调写状态的 equals 闸门：`GalleryScreen.kt:3163`、`GalleryPixivHome.kt:1062`
- `mutableStateOf<Int/Float/Long/Double>` 0 违例（typed factory 全仓干净）
- `Modifier.offset(x.value…)` 非 lambda 形态 0；`composed{}` 0；`GlobalScope` 0；`animateItemPlacement` 0

**Issues**
- Critical 3 的 182 处 `collectAsState()` — <https://developer.android.com/develop/ui/compose/side-effects#collect-as-state>
- `contentType` 全仓 0 处（主墙混排 Pixiv/图库三站卡片，异构回收复用可能错位；`key` 已按 uid 钉，实际风险低，记为 Nit）— <https://developer.android.com/develop/ui/compose/lists>
- `GalleryHomeSections.kt:425` 每张卡各订阅一次 `prefs.galleryAiBadge`（同屏 5 卡 5 订阅，Nit）

### State management — 5/10

**What is working**
- 纯函数决策层收状态时序：`GalleryExitGesture.kt`（四道门闩 + 指令序列，UI 只执行）——本仓 State 类最好的一块
- 单一真相收口件：`GallerySettingsModel.wallUrl`（一处决定封面地址档位）
- `rememberSaveable` 用于跨重组身份（`currentId`、多选态），键正确

**Issues**
- Critical 1、2（六条曲线 + 单例）
- `GalleryFlyIn` 的四档 stage 语义靠"对面等离开 WAITING"约定，`PixivWorkScreen.kt:497-528` 两条"已放弃不再起飞"补丁是这一族的时序债读法 — <https://developer.android.com/develop/ui/compose/performance/states>
- 无 DI：state holder 手搓、`getInstance` 单例遍布（网络/DB/偏好）——与本仓无 Hilt 的选型一致，记限制不重扣，但它正是 Critical 2 的温床

### Side effects — 7/10

**What is working**
- 登记/注销成对有**源码尺**钉（`GalleryFlyWiringPairTest`：有 `noteCardBounds` 的宿主必须同文件有 `capture` 与 `forgetCardBounds`，违例列全）——同类仓少见的纪律强度
- `LaunchedEffect` 键控普遍带身份键（`postId`/`entryKey`/`retryTick`），重进重取模式清楚
- `snapshotFlow` 只读不写；`requestFocus()` 全部在 `LaunchedEffect`/点击 lambda 内（`GallerySearchArea.kt:252` 还注释了"要等 attach 完成"——组合期焦点请求 0 违例）
- `rememberUpdatedState` 用在长按持续路径

**Issues**
- Critical 5（吞错）；`ChromeCaptureSuppress` 两条复位路只靠注释约定（`GalleryFlyIn.kt:180-181`），症状=底栏永久消失，值得一颗尺 — <https://developer.android.com/develop/ui/compose/side-effects>
- 导航是**多 Activity** 制（`PixivWorkActivity`/`GalleryPostActivity`…，`backStack.add`/Nav3 0 命中）⇒ 无"组合期导航"风险，但也无 `dropUnlessResumed` 一类防重复投递守卫可用（跨 Activity 由系统转场承担；记录为架构约束）

### Composable API quality — 5/10

**What is working**
- 具名件 `modifier: Modifier = Modifier` 齐备且应用在根节点；事件收 `() -> Unit`（仓纪律"按钮件只收事件"）
- 0 处 `MutableState`/`State` 形参；deferred read 用 `() -> Float`（批 A 收口）
- slot 件带 `ColumnScope`；`internal` 件不跨包泄实现
- `LayeringEdgeTest` 把 components→gallery 反向依赖当 blocker（源码尺）

**Issues**
- Critical 4（巨件/硬编码/preview 缺口）
- `GalleryPostScreen.kt` 的 `onImageBounds` 刚换成"只许没裁过那把"的契约，类型仍是裸 `Rect`，语义活在注释里（本刀新账，建议 value class 或改名 `fullBounds`）
- `VeneraTopAppBar.kt:145-166,325` 四处注释自述"连接不挂 ⇒ 恒 0"的 miuix 上游缺陷绕行——绕行本身有测试钉（`VeneraTopAppBarHeroCollapseTest`），记债不重扣 — <https://developer.android.com/develop/ui/compose/api-guidelines>

## Notes And Limits

- 权重 35/25/20/20 默认制，无 renormalization。
- 本仓是 Flutter→Compose 迁移中：无 UI 测试源集（`app/src/androidTest` 不存在）、判据全靠 210 颗 JVM 源文件尺——按技能口径记 coverage 不折分；hero 这类只能真机量的面，仓内已建立 FlyProbe + 帧差量具口径（批 A/B 验收在案）。
- `desktop/` 未审（工作树 ≥30 处未提交，用户口径排除）。
- KMP 源集只在 `shared-probe`（探针），不触发 KMP 扣分。
- 编译器报告来自 `:app:compileDebugKotlin`（debug 档，一次 `--rerun-tasks` 生成）。

## Suggested Follow-Up

- `compose-agent focus on animation`：批 B 机制换体按文件级改写做完（计划 §5 批 B ①③④⑤⑥⑦ + §9.3 剩余清单：一标量两层、删三曲线、活画面通道、源卡让位、不等落点、侧滑退路）。
- `compose-agent focus on testing`：gallery 新件的 preview 补齐；`collectAsState` 混用若收口，配一颗源码尺（仓内已有同型先例）。
- `compose-agent focus on focus`：`GallerySearchArea.kt:408` 展开即要焦点那档需要一次行为核对（键鼠路径）。
- 无需 material-3 复查：本仓自有 `VeneraTokens` 体系，非 M3。
