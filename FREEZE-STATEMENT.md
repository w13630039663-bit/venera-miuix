# 页面冻结声明（Stage 9 Freeze）

> 冻结日期：2026-09-18 ｜ 冻结 commit：7e681cc
> 真机验收：探索页打码与分页 ✅ ｜ 源级内容守卫（搜索/收藏/历史/追更）✅ ｜ 搜索网格行级虚拟化性能修复 ✅

| 对象 | 状态 |
|---|---|
| UnifiedExploreScreen.kt | **FROZEN** |
| SourceSectionScreen.kt | **FROZEN** |
| SearchScreen.kt | **FROZEN** |
| ContentGuardManager.kt | **FROZEN** |
| HistoryScreen.kt | 🧊 **FROZEN**（2026-09-18 第二批） |
| FavoritesScreen.kt | 🧊 **FROZEN**（2026-09-18 第三批） |
| NetworkFavoritesScreen.kt | 🧊 **FROZEN**（2026-09-18 第三批） |
| HomeScreen.kt | 🧊 **FROZEN**（2026-09-18 第四批） |

允许：修实际 Bug、修明确回归。
禁止：无明确需求的视觉重构、架构重构、顺手拆文件、顺手改其他页面。
冻结目的：防止「改视觉 → 顺手改 Host → 功能回归 → Crash → 再修」循环（手册第 9 节）。

## 冻结范围说明
- 探索闭环：阶段 0~9 全流程完成（审计/设计/实施/QA/归档），文档见 explore-audit-checklist.md、explore-capability-matrix.md、explore-design-mapping.md。
- 源级守卫判定链：用户规则 > 源级预设（source_content_warning.json，33 源）> 显式 R18 正则兜底，LRU 缓存 + 别名解析。
- 后续模块（ComicSource / History / Detail 等页面重构）按手册标准流程另行启动。
- 顶栏大标题折叠 + 毛玻璃效果：已确认采用「页内自治」架构，不在外壳挂载；待后续模块实施。

## 第二批冻结（2026-09-18，commit：见 git log chore(freeze) 第二条）

> 真机验收：历史提升主 Tab ✅ ｜ 设置收口顶栏齿轮 ✅ ｜ 首页清理今日推荐 ✅ ｜ 历史页右上角对齐/空态/卡片 Token 化/多选删除预测返回 ✅

- **HistoryScreen.kt**：Batch 1 主 Tab 化接线 + Batch 2 规范化重构（VeneraEmptyView / VeneraCard / Token 化 / AnimatedContent 工具条）全部完成并验收。
- **导航层（Navigation.kt / VeneraFloatingNavBar.kt）**：Tab 枚举顺序 HOME → HISTORY → FAVORITES → SEARCH → EXPLORE 为本轮信息架构决策结果；枚举顺序、路由映射与顶栏齿轮入口**不允许顺手变更**（改动需重新评审）。
- **信息架构决策记录**：历史 = 高频主 Tab；设置 = 低频操作收口顶栏齿轮（子页形态，外层返回 + 内部 PredictiveBackStack 双层返回）。
- HistoryViewModel / HistoryDao 数据层未冻结但未改动；后续如需动历史数据结构，须连带评审守卫别名解析链（sourceName 显示名 → sourceKey）。

## 第三批冻结（2026-09-18）

> 真机验收：网络收藏手风琴原地展开 ✅ ｜ 横向源药丸切源 + 登录态圆点 ✅ ｜ 多文件夹源自动进默认分组（多夹假空态修复）✅ ｜ 布局切换按钮合并进工具行 ✅ ｜ 本地收藏多选工具条上移（不再被悬浮底栏遮挡）✅ ｜ 底栏双震动修复 ✅

- **FavoritesScreen.kt**：本地收藏规范化完成（多选工具条迁顶部分段区、Segmented Control 模式切换、预测返回跟手淡出、Token 化）并验收冻结。
- **NetworkFavoritesScreen.kt**：网络收藏「三级整屏下钻 → 单开手风琴」信息架构升级完成（唯一 LazyColumn + 行级虚拟化、触底自动加载、长按移除二次确认、VeneraEmptyView/Token 化）并验收冻结；配套 NetworkFavoritesViewModel 的 expandSource/collapseSource 手风琴状态机一并冻结。
- **共享组件连带改动**：VeneraCard 增加 onLongClick（miuix Card 官方可点击重载，外层叠 combinedClickable 会被 squircle 裁剪吞掉长按——真机实测教训）；ComicTileDetailed 增加 onLongClick；VeneraLiquidGlassNavBar 宿主回写抑制标志修复双震动。
- 至此**底栏 5 大主 Tab 页面（首页 / 历史 / 收藏 / 搜索 / 探索）全部实现冻结闭环**。

## 第四批冻结（2026-09-18）

> 真机验收：首页卡片全量 VeneraCard 化 ✅ ｜ 源健康度 StatusColors 四态 ✅ ｜ miuix Card 残留清零 ✅

- **HomeScreen.kt**：阅读统计/漫画源状态/本地/图片收藏卡片全部换装 `VeneraCard`（可点击卡走 onClick 重载）；源连通性 Connected/Degraded/Failing/Unknown 四态严格对齐 `StatusColors`（Healthy/Degraded/Failing/Unknown）。至此**底栏 5 大主 Tab 全部冻结闭环**（首页加入后闭环完成）。

## 冻结豁免记录（2026-09-18）

- **NetworkFavoritesScreen.kt（豁免评审通过）**：用户明确要求新增「ON_RESUME 自动刷新」能力（详情页收藏/取消收藏后返回即见最新，无需手动刷新）。仅追加生命周期观察器（DisposableEffect + LifecycleEventObserver），手风琴结构与既有交互零改动。

## 冻结豁免记录（2026-09-19）

- **NetworkFavoritesScreen.kt（用户点名豁免，交互架构改动）**：下拉刷新重做 —— 取消 M3 顶栏圆形箭头，改为「源栏正下方一行 `CircularWavyProgressIndicator`」，行高由下拉进度 1:1 跟手驱动，放手刷新、完成收回。
  - 同时修掉两处**既存缺陷**（这两条本身可归入「修实际 Bug」）：① `PullToRefreshState()` 未 remember，每次重组都新建；② `isRefreshing` 直接吃 VM 的 `isLoading`，而该标志同时被 `loadMore` 复用（VM:242 / :274），会把「滚到底加载更多」点亮成刷新。
  - **未动**：手风琴状态机（expandSource / collapseSource）、NetworkFavoritesViewModel、行级虚拟化契约（单 LazyColumn、chunked 行 = LazyItem）、长按删除二次确认、触底自动加载。
  - 刷新期间用屏幕侧快照保留旧卡片（VM 的 `refresh()` 会立即 `comics = emptyList()`，VM:165），避免「卡片消失 → 转圈 → 卡片回来」的硬切。
  - 方案与真机 QA 清单：`network-favorites-pull-refresh-2026-09.md`。

- **SearchScreen.kt + Navigation.kt（用户点名豁免，2026-09-19 第二批）**：搜索页加载态与翻页落点改造。
  - `SearchScreen.kt`：`ResultSkeleton`（双列 shimmer 空卡）整体换成 `SearchLoadingIndicator`（M3 波浪环，5 个调用点）；新增可见的「加载更多」落点按钮；**修一处既存缺陷** —— `ui.error` 原先只在 `results` 为空时渲染，导致「已有一屏结果后翻页失败」的原因被整个吞掉。
  - `Navigation.kt`：`TagSearchRoute` 增加可选字段 `sourceName`（默认空串），详情页点标签时携带，搜索页据此把目标切到该漫画所属源。**未改** Tab 枚举顺序、路由映射表与顶栏齿轮入口 —— 冻结声明里禁止的是那三项，本改动是给一个既有路由加可选参数。
  - **未动**：`SearchViewModel`（翻页 / 聚合 / 守卫过滤逻辑一律原样）、`ComicSourceManager`、`TagSearchPolicy`。
  - 方案与遗留项：`search-page-loading-pagination-2026-09.md`。

- **标签统计移植（用户显式授权保护域，2026-09-20 第三批）**：把 `master`（Flutter venera-miuix）的题材统计半区接到 compose。授权范围由用户逐条点选（D1~D5 全按推荐），因此下列**保护域改动是获准的**，不是越界：
  - **Reader**：`reader/ComicPageSource.kt` 的 `ReaderSession` 增加 `tags: List<String> = emptyList()`、`createLiveSession` 增加同名参数并透传；`reader/VeneraReaderScreen.kt` 的落库调用补 `tags = session.tags`。**只加字段与传参，未动翻页 / 加载 / 切片 / 进度记忆逻辑。**
  - **ViewModel**：`feature/ComicDetailViewModel.kt` 构造会话时补 `tags = comic.tags` 一行；`feature/SearchViewModel.kt` 为客户端标签过滤接上简繁那一级（新增一个 `ChineseVariantConverter` 实例字段 + 3 处 `filterByTagsWithFallback` 调用补第三参数）。
  - **Navigation**：`TagSearchRoute` 增加 3 个可选字段（`tagNamespace` / `tagRaw` / `tagLabel`，默认空串）。与 2026-09-19 那批同类 —— **未改** Tab 枚举顺序、路由映射表与顶栏齿轮入口。
  - **SearchScreen.kt**：新增 `initialTag: SearchTag? = null` 参数，在既有的下钻 `LaunchedEffect` 里先挂标签再搜。
  - `StatsScreen.kt` 不在冻结清单内（它不是底栏 5 大主 Tab），本轮删除了那张「题材偏好热度」死卡（`getTopTags()` 因写入侧从未喂值而恒空，永不渲染），换成范围切换 / 题材占比 / 本命题材 + 题材云 / 追漫轨迹四块。
  - 方案、决策与实测数据：`tag-statistics-port-2026-09.md`。
