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

- **首页「可能你感兴趣」推荐区（用户点名新增，2026-09-21 第四批）**：读最近 30 天读得最多的题材桶 → 禁漫天堂搜索出 10 本，做成 MD3 标准轮播。
  - **HomeScreen.kt（FROZEN，用户点名豁免）**：新增分区 2.5（推荐轮播 + 换一批 + 空态一行说明）、历史记录由两行网格降为单行最多 4 张、刷新期间走灰骨架呼吸。轮播用 material3 自带 `HorizontalMultiBrowseCarousel`（**不引入第三方库、不做真 3D 变换**）。**未动**：顶栏大标题折叠 + 毛玻璃页内自治架构、分区 2/4/5 结构、底栏避让契约（`bottomBarClearance`）。
  - **Navigation.kt（保护域）**：只给 `HomeRoute` 条目套 `CoverTransitionHost`，与 09-20 已提交的搜索/收藏/历史/探索各条目同类。**未改** Tab 枚举顺序、路由映射表与顶栏齿轮入口。
  - **ComicSourceManager.kt**：内置开箱源清单加 `jm.js`；同时把「只在首次启动跑一次」的 bootstrap 改成**已 bootstrap 过的设备走增量补装** —— 不改这里，老设备上禁漫永远不会被预装，推荐区会永久判「未启用该源」。用户主动删过的源仍由 `deletedBuiltinKeys` 拦住，不会被塞回。**未动**：安装/卸载/更新/禁用与解析链路。
  - 题材统计侧配合：`TagNormalizer.EXCLUDED_TAG_VALUES` 补语言系与汉化值（禁漫把语言塞在兜底 namespace `Tag:` 下，namespace 级排除拦不住），否则「中文」会当题材进偏好榜并被推荐区当关键词搜。单测 `languageAndTranslationValuesNeverBecomeTopicBuckets` 锁住这条。
  - 刷新语义：`loadRecommend(force)` 走并发锁；启动应用与从详情页返回经条目生命周期（ON_START/ON_RESUME）触发 `autoRefreshRecommend()`，非首次按 **2 分钟**节流。
  - 真机第一轮反馈的 5 条（同批）：① 顶栏冲突 —— 首页内容首屏避让改为**实测顶栏展开高度**与 `statusBarTop+104dp` 取大（大标题是 32sp，字体缩放后 104dp 会被压破；取大保证不会比其他主 Tab 更靠上）；② 冷启动仍报「未启用该源」—— JS 源是异步注册的，加 10s 有界等待，同时把退出前那批推荐落盘（`home_recommend_cache`）先铺上；③ 节流 60s→120s；④ 刷新期间用 `VeneraShimmer` 灰骨架呼吸（复用全站既有骨架，不新造动画）；⑤ 轮播横滑区域登记 `systemGestureExclusionRects`，让全面屏侧滑返回不再吃掉抽卡手势。
  - 真机第二轮反馈（同批）：推荐轮播从 `HorizontalMultiBrowseCarousel` 换成 **`HorizontalCenteredHeroCarousel`（中央 Hero + 两侧缩窄）**，尺寸口径照 material3 官方 sample（`maxItemWidth` 不指定、侧卡压在 64~96dp、两侧各让开 24dp）；轮播两侧内缩 24dp 兼作「让横拖落点离开系统边缘返回热区」。**共享组件 `VeneraCover` 加 `preserveAspectRatio: Boolean = true`** —— 轮播槽位宽高都由外部给，封面再自持 3:4 会在固定高度里横向留缝；默认值保持全站既有行为不变，只有轮播传 false。
  - 真机第三轮反馈（同批，**修实际 Bug，非视觉改动**）：「左右滑轮播却切了主 Tab / 有时完全没反应」的根因不在系统手势，而在**应用自己的** `Modifier.tabSwipePager`（挂在 NavHost，任意位置横滑切 5 个主 Tab）。它的接管阈值只有 6px，远小于子组件（轮播/横向列表）自己的 18dp 触摸斜率阈值，于是父级先 claim 再把横向位移 consume 掉。改法三条，全部收敛在 `TabSwipePager.kt` + `Navigation.kt` 各一处：
    - 手势读取整条挪到 `PointerEventPass.Final`（foundation 的 `drag()` 没有 pass 参数，故自写读取循环）：孩子消费过的位移到这一趟是 consumed 状态，父级直接让位。
    - 新增 `TabSwipeExclusionRegistry` + `LocalTabSwipeExclusions` + `Modifier.tabSwipeExcluded()`：页面把自己吃横滑的整块登记成「左右滑不切页」的让位带（随滚动实时更新、组件卸载即反注册）。首页推荐分区整块挂上它。
    - `Navigation.kt` 只做两件事：`remember` 一个登记表实例、`CompositionLocalProvider` provide 给页面并显式传给 `tabSwipePager(exclusions = ...)`。**未改** Tab 枚举顺序、路由映射表与顶栏齿轮入口。
  - 真机第四轮反馈（同批）：推荐区整体调大 + 标题/作者居中 —— 新增 token `recommendHeroHeight = 220dp`、`recommendSideMinWidth/MaxWidth = 64/88dp`（Hero 实宽 ~143dp，配 170dp 高是 0.84 的矮胖比例，封面本身 0.72 竖幅）；焦点卡标题与作者行改 `TextAlign.Center` + 居中 Column；骨架屏同步用新高度。
  - **顶栏避让换实现（第三轮的修法被第四轮改动带回旧症状）**：不再用「顶栏 `onSizeChanged` 回写状态」，改为按 miuix 自己的几何算 —— `systemBars 顶 inset + TopAppBarDefaults.CollapsedHeight + (-state.heightOffsetLimit) + LargeTitleBottomPadding`，并与 `statusBarTop + 104dp` 取大。三处口径纠正：① 页面原先读 `statusBars` 而顶栏读 `systemBars`，状态栏可见性未同步的那几帧两者会错开；② `heightOffsetLimit` 是**负值**；③ 布局期回写尺寸会和同帧其他 `onSizeChanged`（手势排除区、让位带）互相顶掉，所以整条改成纯 composition 期读取快照值。
  - 真机第五轮（同批）：`heightOffsetLimit` 首帧可能还没被 miuix 写进来 → 再加一项**按主题字号推算**的兜底（`title1.fontSize` 即 32sp，sp→dp 自带系统字体缩放，CJK 行高取 1.4 倍余量），与实测值取大后再加到避让高度里。推荐区**冷启动不再自动刷新**：`hydrateRecommendFromCache()` 把缓存当作「刚拉过」（置 `recommendLoaded` + 刷新时间戳），首屏只铺上次退出前那批；回到首页超过 2 分钟节流窗口才自动刷新，无缓存的首次安装仍会拉一次。
  - **顶栏避让定性（真机第六轮，「滑一下就好」这条反馈是关键证据）**：`TopAppBarState.heightOffsetLimit` 在 miuix 里是**普通 `var`、不是快照状态**，它被写进真实值时不会通知任何重组 —— 所以页面 composition 期读到的永远是首帧的 0，算出的避让值要等到下一次无关重组（用户滚动列表）才生效。修法：`LaunchedEffect` 主动跟最多 10 帧，把 `-heightOffsetLimit` 抄进本页自己的 `mutableStateOf`（抄到 ≥ 字号兜底值即收工），字号兜底继续作为下限保证首帧就有基本正确的留白。同时保留 NaN 防护（contentPadding 为 NaN 时 LazyList 会当作没有留白）。临时诊断 overlay 与为它挂的 `listState` 已删除。
  - **顶栏重叠的最终定性与收口（真机第七轮，屏幕上打诊断行才查出来的）**：诊断读数 `sb=45 bars=45 room=44 pad=149 idx=1 off=0 ho=0 co=0` —— 留白 149dp 是对的、顶栏 `ho=0` 完全展开，**唯一异常是列表首屏停在 index 1**。真因是 LazyColumn 的滚动锚定漂移：冷启动 `ui.todayPages` 还是 0，`if (todayPages > 0 || weekPages > 0)` 那个第 0 项当时不存在；Room 数据到达后它插到列表头部，LazyList 为保持「当前首屏那一项不变」把视口锚到新 index 1，真正的第 0 项于是被画进 `contentPadding` 留白里（LazyList 不在 contentPadding 处裁剪）→ 观感就是「阅读统计压在首页大标题上」，而手动滑回顶部就好了。其他四个主 Tab 用同一块 `statusBarTop + 104.dp` 地板不重叠，也反证了「地板不够大」这个前提出错了。
    - 收口：首页避让口径**回退到与其他 Tab 完全一致的 `statusBarTop + 104.dp`**；上一轮为测顶栏高度引入的那一整套（`heightOffsetLimit` 跟帧抄值、字号推算兜底、NaN 防护、`systemBars` 换源）**全部删除**，只留注释说明为什么不动它。
    - 真正的修复：显式持有 `rememberLazyListState()`，用 `snapshotFlow { isScrollInProgress }` 记「用户是否自己滚过」，未滚过之前任何结构变化（统计/推荐/历史数据到达）都 `scrollToItem(0)` 钉回顶部。诊断 overlay 已删。
    - **钉顶方案已换成根治方案**（真机第八轮：钉顶发生在漂移之后，差一帧 → 观感是「阅读统计先贴在顶栏上、再自己跳下来」那一闪）：改成让「阅读统计」这一 **LazyColumn 项恒定存在**（`item(key = "stats")`），把判空条件移到项**内部**去决定内容。头部不再出现「插入项」这个动作，锚点自然不动，闪也无从发生；`listState` / `userScrolled` / `scrollToItem` 那套全部删掉。代价是「今天没读」时列表顶部多一个 0 高项 + 14dp 间距，肉眼不可见。

## 预测式返回转场 + 返回过渡模糊（2026-09-22，用户点名；详设与证据见 `predictive-back-transition-2026-09.md`）

> 本节最初写入的四条结论有三条是错的，已在实测后更正；下面同时记下**错在哪**，避免有人再按旧结论推理。

- **最终形态：设置子树与首页顶栏四个入口改成真·跨 Activity，动画由系统施加**，不是自绘。
  `SettingsActivity`（设置主页）+ `SettingsSubActivity`（7 个分区、规则子页、源管理/下载/本地漫画/统计/收藏图/防窥/同步/日志）共用基类 `VeneraSubActivityBase`；
  目标 SDK 37（≥36）时系统自动对 back-to-home / 跨任务 / **跨 activity** 施加预测式返回动画，`enableOnBackInvokedCallback="true"` 已在 `<application>`。
  返回模糊走**窗口 blur-behind**（18dp，封顶 150px，随 `isCrossWindowBlurEnabled` 开关），不是内容层 `renderEffect`。
  首页右上角 统计 / 本地漫画 / 收藏图 / 源管理 四个入口与齿轮同口径（此前是「同一页两套动画」）。
- **依赖升级保留**：`navigationCompose` 2.8.9 → **2.10.1**，拖动 `activity-compose 1.9.3 → 1.13.0`、`lifecycle → 2.11.0`，新增 `navigationevent-compose`。
  升级理由是**要 `predictivePop{Enter,Exit}Transition` 那个 `swipeEdge: Int` 入参**，让退出方向跟随手势边缘。
  ❌旧记录说「2.9.8 一个 predictive 符号都没有」——错，那是只 grep 了 `navigation-runtime` 没 grep `navigation-compose`；2.9.8 已是真·进度驱动（`SeekableTransitionState.seekTo`），2.10.0 加的只是边缘参数。
- **包体不是代价**：❌旧记录「85MB → 125MB 是升级代价」——错。逐 zip 条目实测两包压缩内容 78.4 vs 78.9 MiB 几乎没变，差的 39.59 MiB 全是条目间 0 填充（脏增量），`--rerun-tasks` 干净重建是 79.0 MiB。判包体增减要按压缩内容合计比。
- **「退出方向跟随手势做不到」是错的**：❌旧记录那句作废。2.10 的 `swipeEdge`（`NavigationEvent.EDGE_LEFT=0 / EDGE_RIGHT=1`）就是方向，MainActivity 侧 `veneraPredictiveEnter/Exit` 已在用；跨 Activity 与返回模糊也**不互斥**，本仓库现在两者同时成立。
- **删掉的东西**：自绘的返回模糊层（`BlurEffect(26dp→0)` + 0.92 缩放 + 400ms 那套常量为一次误判加码，已连常量一起删）、`SettingsHome` 的内部页栈 `PredictiveBackStack`（注意 `PredictiveBack.kt` 文件仍在用，另两个符号被 3 个页面消费）。
- **真机已证实（02:20–02:38，只读 logcat/dumpsys）**：设置侧跳转 `type = PREDICTIVE_BACK` + `BackTransitionHandler` + `FLAG_BACK_GESTURE_ANIMATED`；`SettingsActivity` 窗口 `blurBehindRadius=47`、`fl=BLUR_BEHIND`；0 崩溃 0 掉帧；用户口头验收「过渡动画没问题了」。
- **尚未证实，别当已完成**：`SettingsSubActivity` 窗口的模糊属性没实测到；三条越界出口（本地漫画→阅读器 / →详情 / 统计→题材下钻）未走过；右边缘手势分支物理不可达（设备 `navigation_mode=2` 只注册左边缘）；首页入口改跨 Activity 后「点一本漫画会重建外壳 + 任务栈里两个 MainActivity」这笔代价未经真机确认。

## 详情页标签长按菜单（2026-09-21 第五批，用户点名新增）

- **共享组件 `VeneraChip` / `VeneraTagChip` 加可选 `onLongClick: (() -> Unit)? = null`**（默认 null，12 处既有调用点源码兼容）。交互判定从 `onClick != null && enabled` 放宽为 `(onClick != null || onLongClick != null) && enabled`，`clickable` 换成 `combinedClickable`（`onClick` 为空时传 `{}` 占位）。按压反馈仍由 Chip 统一持有，页面不得自行叠 `combinedClickable`（`VeneraChip.kt:45` 既有约定）。
  - 查证结论：Chip 的容器是 miuix `Surface`（内部就是普通 `Box` + `surface(shape,…)`），**没有 `squircleSurface`** —— 只有 miuix `Card` 有。所以「外层叠 combinedClickable 会被 squircle 裁剪吞掉长按」那条真机教训（本文件第三批）**不适用于 Chip**，这里叠在内层 modifier 上是安全的。
- **`ComicDetailScreen.kt`**（页面显示层，不在冻结表内）：两处标签 `FlowRow` 的药丸改走本页私有 `DetailTagChip`，长按弹 material3 `DropdownMenu`（锚点逐项各挂一个，共用锚会从第一个药丸位置弹出），菜单两项：
  - **复制**：复制**显示名**（所见即所复制），走应用既有 `ClipboardManager` + `Toast` 口径。
  - **屏蔽该标签**：`ContentGuardManager.addRule("TAG", 站点原值)` —— 存原值而非译文（TAG 规则对 `ns:value` 与裸 `value` 双路命中，存译文会随字典更新失效）；调用点先按 `type + pattern` 去重（`addRule` 本身不去重，与 `BlockingSettings.kt:95-107` 同一口径）。
  - **未动 `ContentGuardManager.kt`（FROZEN）**，也**未动 `ComicDetailViewModel`（保护域）**：直接从页面拿守卫单例 + `rememberCoroutineScope()`，与 `ComicDetailScreen.kt:233` 既有用法同类。
  - 已核实不是假开关：用户规则命中在 `filterComicModels` 里**任何遮蔽模式下都物理剔除**（`ContentGuardManager.kt:245` 起的注释与实现），且遮蔽判定链 `:348` 也会因用户规则直接判真。

## 搜索页「从详情页返回时被刷新」（2026-09-21 第五批，用户点名的既存缺陷）

- 症状：详情页点标签 → 标签搜索页 → 再进某本漫画详情 → 返回，搜索页整页重搜并闪回加载态。根因是本项目已定性过的**导航条目组合重建**（见记忆 `project-nav-entry-recomposition`）：进详情会销毁搜索条目的组合，返回时 `LaunchedEffect(initialQuery, initialSourceName, initialTag)` 与 `LaunchedEffect(Unit)` 重新执行，把「进页一次性动作」又做了一遍。
- **SearchViewModel.kt（保护域，用户点名修 Bug）**：新增 `shouldApplyEntryParam(key)`（内部 `mutableSetOf<String>.add`，同名只认第一次）与 `listAnchor: Pair<Int, Int>?`。没有改任何搜索/翻页/聚合逻辑。
- **SearchScreen.kt（FROZEN，同类豁免）**：两处进页 effect 各加一行守卫（`"default-target"` / `"drill-down"`）；另把滚动锚点存在 ViewModel 里并在重建后 `scrollToItem` 恢复 —— 导航条目没开 `saveState`，`rememberLazyListState()` 本身没有恢复来源，不补就会跳回顶部。恢复用的值先 `remember` 取快照，避免记录器的首次发射把待恢复值冲成 (0,0)。
- **未动**：`Navigation.kt`（不给条目加 `saveState`，那是路由表层面的改动）、`SearchViewModel` 的搜索与聚合实现、结果卡片与网格结构。

## 详情页预览图裂成横条（2026-09-22，用户点名的既存缺陷，待真机确认）

- 症状：部分 JM 漫画详情页「预览 (N 页)」整片呈横条撕裂，每段各带一个水印、段与段之间边界对不上；同一本进阅读器却正常。
- 根因：**同一个「JM 混淆块数」在本仓库有两份算法，且会分叉**。源脚本 `assets/sources/jm.js:848-853` 的 `pictureName` 取的是「最后一个 `/` 之后、整条 URL 末尾往前数 5 个字符之前」—— 无条件砍 5 个尾字符，**不是去扩展名**；`.webp`（含点正好 5 字符）两种切法等价，`.jpg`（4 字符）会多砍掉一位数字。Kotlin 那份 `data/network/ImagePipelinePolicy.kt` 用的是 `substring(0, lastDot)`。两者喂进 MD5 的串不同 → 块数不同 → 还原按错的边界重排，观感就是条状。分叉概率实测：`%8` 段（epId>421926）86.7%、`%10` 段 89.1%。
- 为什么只有预览受害：阅读器与下载都先调 `comic.onImageLoad`，`source/js/JsComicSource.kt:716-722` 把 JS 那份 `const num = N` 抠出来 `registerScramble`，而 `VeneraImageFetcher.kt:85` 查表优先 → 拿到的是权威值。预览是 `ComicDetailViewModel.kt:617-634` 直接把第一话的原始页 URL 当缩略图，**从没走 onImageLoad** → 全程吃「URL 推导」那份。
- 为什么只是"有些"漫画：epId<220980 不混淆、220980~268850 恒为 10，两份一致；只有 ≥268850 走 MD5 **且**页面扩展名不是 5 字符（`.jpg`/`.png`）时才分叉。
- **改法 A（对齐权威口径）**：`ImagePipelinePolicy.calculateJmScrambleNum` 的 `pictureName` 改成与 jm.js 逐字同串 —— `url.substring(lastSlash + 1, (url.length - 5).coerceAtLeast(lastSlash + 1))`。照抄"砍 5 个字符"不是复刻笔误，而是官方 Venera 线上生效的口径。5 个样例 URL 对拍：新写法与 JS 全等，旧写法在 `.jpg` / `.png` / 短名三例上不等。
- **改法 C（防锁死 + 防漂）**：新增 `ImagePipelinePolicy.cacheKeyFor(url)`，预览的 `AsyncImage` 改传带块数的 `memoryCacheKey` / `diskCacheKey`，两种还原结果各占一条缓存、互不覆盖；`registerScramble` 在「源脚本值 ≠ 推导值」时打一条 `Log.w("ImagePipeline", "JM 块数分叉 …")` —— 将来 jm.js 再改算法会留下可见证据，而不是又退化成一次"图裂了"。
- **公开纠正一处我先前给过的推论**：我说过"先看预览会污染阅读器那一页"。查过 key 构造后确认**不成立**：阅读器用 `ComicPageSource.cacheKey`（`sourceKey@comicId@epId@imageKey`），预览用 URL 串，两套 key 不重叠，最多各解码一次，不存在互相锁死。C 那条因此是"防将来"而非"修当下"。
- **未动**：`descrambleJmImage` 的切块与重排几何（与 JS 的 `modifyImage` 逐行同构，本来就没错）、`VeneraImageFetcher` 的取字节与重试链、`DownloadManager`、jm.js 本体。
- 尚未证实：真机还没看过修复后的预览（设备未连接）。验证口径 —— 同一本 `.jpg` 的 JM 漫画，预览第 1 张应与阅读器第 1 页完全一致；若仍撕裂，`adb logcat -s ImagePipeline:W` 应出现「JM 块数分叉」，那说明还存在第三份口径。

## 掉帧排查 Tier 1（2026-09-22，用户点名「页面切换和动画还是会有频繁掉帧」）

- 先记下**排除掉的怀疑**，避免有人重走：①返回过渡的模糊不是本进程的 `RenderEffect`，是系统跨窗口的 `blurBehindRadius`（`VeneraSubActivityBase.kt:65-76`），我们这边没有可优化的绘制层；②Coil 会把布局约束正向传给解码器，预览小图本来就是降过采样的，不存在"缩略图解成 7MiB 全屏图"；③"没有 `composeCompiler {}` 块 → 没开 Strong Skipping → 卡片全量重组"这条推论作废：Kotlin 已是 2.4.10（`gradle/libs.versions.toml:7`），强跳过自 2.0.20 起默认开，真正的雷形状是**每次重组都新分配实例**（引用相等失效），不是 lambda 捕获。
- **Tier 1-1 阅读器取图链被反复重启**（`reader/VeneraReaderScreen.kt`）。两个 `DynamicNetwork` 分支把 `ImageRequest.Builder(...).build()` 直接写在 `model =` 上。`ImageRequest` 没有值相等语义 → 每次重组 Coil 都判"模型换了" → 取消在飞请求重发，翻页时"下载 + 去混淆"整条链被反复重启。改为 `remember(resolvedUrl, page.cacheKey) { … }`（`ReaderSinglePageItem` 与 `ReaderTelephotoPageItem` 各一处）。
  - 踩过的两个坑：`ImageRequest.Builder` 在 Coil 3 **没有 `.key()`**，只有 `memoryCacheKey` / `diskCacheKey`；`LocalContext.current` 是 @Composable 取值，不能出现在 `remember {}` 里，得先在提升为局部变量。
- **Tier 1-2 去混淆路径的编解码往返**（`data/network/ImagePipelinePolicy.kt` + `data/network/VeneraImageFetcher.kt`）。旧链路：解码 → 画布重排 → **JPEG 重编码** → 交回 Coil → **再解码**。省掉这一次往返后直接 `return ImageFetchResult(image = bitmap.asImage(), …)`，Coil 的 `EngineInterceptor` 认这个类型、不再走解码器。
  - 副作用必须一起补：位图短路**绕过了 Coil 解码器，降采样也就没了**，所以新增 `sampleSizeFor()` 按 `options.size`（像素盒子）自己算 2 的幂。盒子不是像素尺寸时返回 1，宁可大也别裁错。降采样后块边界最多漂移一个采样单位，缩略图尺寸下肉眼不可见。
  - `BitmapImage` 在 Coil 3 是 **internal**，公开入口是 `Bitmap.asImage()` 扩展（`coil3/Image_androidKt`）。
  - `descrambleJmImage`（字节版）**保留**：`download/DownloadManager.kt:509` 要的是落盘的编码字节，显示路径不再复用它。重排几何抽成私有 `reorderBlocksBottomUp`，两处共用，与 jm.js 的 `modifyImage` 仍逐行同构，并补了 `blockSize <= 0` 的守卫（高比块数还小时旧实现会画出空块）。
  - `cropSprite` 改 `cropToBitmap`：同样不再重编码，返回 null 表示"这次短路没做成"，调用方按原始字节继续走 Coil —— 旧实现在失败时把**未裁剪的整张雪碧图**当缩略图交出去，那是错的图；现在宁可不短路也不交错的。
- **Tier 1-3 内容守卫每次调用现场 new Regex**（`security/guard/ContentGuardManager.kt:380`）。`match()` 的调用密度是「列表每项 × 每条规则 × 每个字段」，用户正则规则在滚动里被反复编译 —— 与本文件既有的「判定在列表滚动里每帧都跑，绝不能在调用点 new Regex」这条约定（`explicitPatterns` 的注释）自相矛盾。改为 `ConcurrentHashMap<String, Regex>` 按 pattern 记忆。该文件 FROZEN，此改动是**性能修复不动判定语义**（命中结果完全一致）。
- **未动**：`Navigation.kt`（保护域，Tier 3 的两处全屏 backdrop 采样它才有份）、`ComicDetailScreen` 展开后的预览大网格（Tier 2）、各 ViewModel 的 UIState 结构（Tier 2）、`RateLimitingInterceptor` 锁内 `Thread.sleep`（Tier 2）。
- **尚未证实（设备一整天未连接）**：以上只是**结构性**修复 —— 三条都是"每帧/每重组必然发生的额外工作"被去掉，但**没有测过一次帧时间**。本轮也没有"观感变好"的凭据，不许当成已验收。下一步该测的口径：`adb shell dumpsys gfxinfo com.venera.compose reset` → 用户自己切 10 次页 → 读 `framestats` 的 `Janky frames` 前后对比。

### Tier 1-2 把详情页预览打回条状（同日，用户读 diff 抓出，已修）

> **公开纠正**：上一节写完我就报告"三条已落地"。用户只读代码就指出「你这修了之后，详情预览图**又**会变成之前那种条状」——**成立**，Tier 1-2 确实把本节开头刚修的 JM 预览缺陷重新打开了。我当时的自查只跑了"正常输入下逻辑对不对"，没跑退化输入。

- 机制：新增的 `VeneraImageFetcher.sampleSizeFor()` 用「降采样后仍覆盖请求盒子」作 while 条件，**盒子为 0（首帧未测量、动画起手的 0 高容器）或负数时条件恒真**，`sample` 一路翻倍到 Int 溢出 → 要么 `sample*2` 归零后**除零崩**，要么把负数送进 `inSampleSize` 解不出图 → `decodeAndDescramble` 返回 null → 旧写法 `if (descrambled != null)` 于是**什么也不做，往下走到 `SourceFetchResult(rawBytes)`**，Coil 原样解码一张**没还原**的图 = 条状撕裂；更糟的是这张错图还会按 `cacheKeyFor` 的 key 进内存缓存被锁住。
- 关键点：**旧实现完全不依赖盒子**（永远原尺寸重排再重编码），所以这是新代码引入的**依赖面**，不是旧 bug 复发。凡按请求尺寸自适应的降级路径，都会把"尺寸"变成正确性输入。
- 修的是三层，缺一层还会以别的形状复现：
  1. `sampleSizeFor()`：盒子非正数直接返回 1；封顶 8（BitmapFactory 只有 1/2/4/8 走 IDCT 真降采样，且把边界漂移钉住）；降完还要求 `num` 个块各留 ≥ 8 像素，否则逐档回退。
  2. `reorderBlocksBottomUp()`：**高度不够切 num 块时返回 null**，不再"原样交回未还原位图"（那是把错图当成功）。
  3. `fetch()`：去混淆失败先按 `sampleSize=1` 重试一次（= 改动前的行为），再失败就 `throw IOException` 让 Coil 走错误态。**任何情况下都不把未还原字节往下交**。
- 现在的量化口径（node 复算，1024×1500 / num=18）：box=0 与 box=-1 → sample=1（旧写法在这两例都溢出）；预览 box=350×286 → sample=2，位图 5.86→1.46 MiB，块高 41px、边界漂移 ≤2px 原图 = 块高的 2.4%；阅读器整屏 box → sample=1，与本节改动前逐像素一致。约束 3 保证了漂移恒 ≤ 块高的 1/8。
- **仍未真机验证**（设备整天没连上）。验证口径不变：同一本 `.jpg` 的 JM 漫画预览第 1 张应与阅读器第 1 页一致；`adb logcat -s ImagePipeline:W` 出现「JM 块数分叉」说明还有第三份口径，出现 `JM 去混淆失败` 说明还原链有洞 —— 后者是这次新增的、宁可报错也不交错图的哨兵。

## 掉帧排查 Tier 2（2026-09-22，用户批准「做 Tier 2」）

- **Tier 2-A 详情页展开后的预览大网格**（`feature/ComicDetailScreen.kt:154-166` + `721` + `822-890`）。旧写法：折叠时取 `PREVIEW_LIMIT`(10) 张，**一旦展开就把整个列表 `chunked(3)` 全挂进组合**。详情自带全量预览的源（nhentai / hitomi）一次返回整本、动辄两三百张，于是几百个取图请求同时下发，且每张未加载完的封面各带一个 `VeneraShimmer` **无限动画**（每帧都在跑）。
  - 改法是**挂载窗口**，不是虚拟化：`previewMount` 初值 10，「查看更多预览」每点一次翻倍（10→20→40→…，`coerceAtMost` 收在总数）。要写清楚这点，是因为在 `LazyColumn` 的 `item{}` 里嵌 `LazyVerticalGrid` 才是"真虚拟化"，而嵌套滚动 + 高度测量会引入新的观感问题，收益不确定 —— 这里只把**每帧的组合量与并发取图数**限定在"和上一轮同量级"。
  - `previewRows = remember(previewThumbnails) { … .chunked(3) }` 必须 remember：`chunked` 每次都新建 List，不记忆就等于每次重组换引用，把这一整块的强跳过打掉 —— 这正是 Tier 1 记下的那个雷形状。
  - 踩到的编译器边界：`remember` / `mutableIntStateOf` **不能**写在 LazyColumn 内容 lambda 的位置（`@Composable invocations can only happen from the context of a @Composable function`），所以窗口状态与派生列表都提升到 `AndroidComicDetailScreen` 函数顶部，网格处按捕获引用读。
- **顺手修掉一个真死控件**（同一段页脚）。三种"还有更多"以前只认一种：预览已展开、本地窗口放完之后，按钮还在，但 onClick 走的 `expandThumbnails()` 在**已展开时直接 return**（`ComicDetailViewModel.kt:659`）→ 对 EH 这类分页源，**第 3 页及以后的预览永远拿不到**，且按了没有任何反馈。现在按 `moreMounted`（本地翻倍，不发请求）/ `collapsed`（展开并补该源分页）/ `canLoadMore`（`loadThumbnails(loadMore = true)`）分流，文案分别说清是"本地还有 N 张没显示"还是"源还有下一页"；「收起」同时把窗口拉回 `PREVIEW_LIMIT`，否则下次展开会直接落回上一轮的大窗口，等于没收起。
- **Tier 2-B 范围收窄，并且放弃原计划**：原计划是"拆 UIState 整对象收集"。查过之后判定不划算 —— `feature/HomeScreen.kt:129-131` 确实整对象收集 `uiState`/`uiStateExtra`/`recommend`（`uiStateExtra` 还复用了 `HomeUiState`，只读其中 7 个字段），但要收益凭据就得连 `SearchViewModel` 与多张 FROZEN 屏一起动，**没有任何一次测量支持这个收益**，按"不加码"的口径不做。
  - 改做的是同一份排查里确定性更高的那条：进首页的静默 `checkUpdates()`（`HomeScreen.kt:136`）以前每次冷启动/每次重建条目都可能再打一次 `index.json`。现在在 `ComicSourceManager.kt:751-765` 加节流：`AtomicLong` 记上次触发时刻，最小间隔 `SILENT_UPDATE_CHECK_INTERVAL_MS = 120_000L`（`1296`，口径直接对齐 `HomeViewModel.RECOMMEND_AUTO_REFRESH_INTERVAL_MS`（`feature/HomeViewModel.kt:344`，不新造数字）；**先 `compareAndSet` 占坑再发请求**，并发进首页只有一个调用通过。冷启动 `prev = 0`，第一次必检，不改变"开机即知有更新"的行为。
  - 用户点「检查更新」必须绕过节流：`feature/sourcemanage/ComicSourceViewModel.kt:470` 传 `force = true`。原因写进注释 —— 被跳过时返回 `0`，而下面的分支会把 `0` 播报成"已是最新版本"，那是一次**假成功**（用户对假开关零容忍）。
- **Tier 2-C 限流器把全域名排在一个 monitor 上**（`data/network/RateLimitingInterceptor.kt:51-70`）。旧写法 `synchronized(hostLastRequestTime){ … Thread.sleep(waitTime) … }` —— 睡眠**在锁内**，期间**所有域名**（不只被限流那一个）的限速判定都被堵住，图片与元数据请求一起排在同一把锁上。改为锁内**预约**槽位（`slot = max(now, last + interval)`，等待量与旧写法逐笔等价）、等待挪到锁外。注意口径：这只消除了一个**确定存在的串行化点**，不等于掉帧变少了，没有前后数据。
- **Tier 2-D 每次读 `tokens.color` 重抄一遍色板**（`ui/tokens/VeneraTokens.kt:140` 旧 getter、`:164` 缓存位、`:178` 抽出的构造函数）。`VeneraColorTokens` 23 个字段，全仓 **412 个读取点**，列表里一张卡片就读 3~5 次 —— 原先每次读取都新建一份实例。抽出 `buildVeneraColorTokens(m, miuixSurface, miuixOnSurface, fixedActions)`（表达式与旧 getter **逐字相同**，包括 `textSecondary = onSurface.copy(0.70f)` 与 `badgeTint` 那个 miuix/material 二选一），由两个主题根各算一次并 provide：`feature/VeneraTheme.kt:90-115`（key 用 `materialColors, miuixColors, appearance` —— 不能只 key `appearance`，壁纸取色与配置变化会原地换色板）、`ui/tokens/VeneraPreviewTheme.kt:58-69`。
  - 缓存成立的前提**已核实**：全仓除这两个主题根外没有任何局部 `MaterialTheme(...)` / `MiuixTheme(...)` 覆盖。新增主题根时必须同样 provide。
  - 用 `compositionLocalOf` 而非 `staticCompositionLocalOf`：static 的提供者换值**不触发**读取方重组，切深色/切风格会留下一屏旧颜色。getter 保留 `?: 现算` 兜底，没走主题根的路径（预览、单测）拿不到 null。
- **未动**：各 ViewModel 的 UIState 结构（见 2-B 的放弃理由）、`Navigation.kt`（保护域，Tier 3 的两处全屏 backdrop 它才有份）、`ComicDetailViewModel` 里两个没人读的死 getter（`visibleThumbnails` / `thumbnailsCollapsed`，屏幕侧已各自算自己的 —— 保护域内、无功能收益，不顺手删）。
- **尚未证实（设备一整天未连接）**：Tier 2 与 Tier 1 一样只有结构性依据，**没有一次帧时间测量**。验收口径不变：`adb shell dumpsys gfxinfo com.venera.compose reset` → 用户自己切 10 次页 → 读 `framestats` 的 `Janky frames` 前后对比；预览这一段另需真机确认「展开后逐批放出、`点查看更多预览` 每次都能多出一批、EH 漫画能翻到第 3 页预览」。
  - 口径修正：换包名之后这条命令要用 **`com.github.w13630039663bit.venera.miuix`**（见下节），`com.venera.compose` 那串只剩旧包能用。旧文档里按包名操作的真机命令（本文件、`venera-stage-plan.md:145` 的 `adb shell run-as … cat shared_prefs/…`）同理：装新包后要用新包名，**但那些已经记录下来的证据不改写** —— 它们是旧包在跑的时段留下的日志。

## 发布身份换成 venera-miuix（2026-09-22，用户点名；桌面图标 / 包名 / 版本 / 检查更新通道）

- 「这个项目」= **本仓库的 `origin`**（`w13630039663-bit/venera-miuix`），`master` 分支就是用户自己那版 Flutter venera-miuix —— 图标与更新逻辑都在本地 `.git` 里，`git show master:<path>` 直接读，没有 clone 外部仓库。
- **图标**：从 `master:android/app/src/main/res/mipmap-*` 取 5 档 `ic_launcher` + `ic_launcher_{background,foreground,monochrome}` + `mipmap-anydpi-v26/ic_launcher.xml`，原样落进 `app/src/main/res/`；设置页大图标用 `master:assets/app_icon.png`（1024×1024）→ `res/drawable-nodpi/app_icon.png`，替掉原来那个 96dp 主色块里写「薇」字的**占位假图标**（尺寸与 28dp 圆角照 master `_buildMiuix()` 的观感，没自己定数）。
  - `AndroidManifest.xml` 原本**根本没有 `android:icon`** —— 桌面图标一直是系统默认图标。补 `android:icon="@mipmap/ic_launcher"`，`android:label` 从 "Venera Compose" 改成 master 用的 `venera-miuix`。
  - 产物已核（`aapt2 dump badging`）：`package: name='com.github.w13630039663bit.venera.miuix' versionCode='1800' versionName='1.8'`、`application-label:'venera-miuix'`、六个密度全部指向 `res/mipmap-anydpi-v26/ic_launcher.xml`。
- **包名**：`applicationId` → `com.github.w13630039663bit.venera.miuix`（与 master 逐字一致，接管它的发布线）。**`namespace` 保持 `com.venera.compose` 不动** —— 改它等于把 48 个提交的 `package` 声明全洗一遍。`FileProvider` 用的是 `${applicationId}.fileprovider`，跟着走，无需改代码。
- **版本号单一来源**：`versionName 1.8 / versionCode 1800` 之后，原本还在说 1.0.0 的三处按 master 的口径收口：新增 `AppUpdateChecker.localVersion()` 读安装包；`VeneraJsEngine.NativeBridge.getVersion()` 改用它；`assets/venera-shim.js` 的全局 `var appVersion` 由字面量 `"1.0.0"` 改成 `_venera.getVersion()`（口径同 master `lib/foundation/js_engine.dart:96` 的 `setGlobalFunc(["appVersion", App.version])`；shim 走 `evaluateBlocking`，那里已 await WebView ready，桥在求值时必然可用）。
  - **故意没动** `UserAgentPolicy.DEFAULT_USER_AGENT` 里的 `Venera/1.0.0`：那是给第三方站点看的浏览器伪装串，改成新版本号等于在真机验收前换掉 33 个源的网络身份，收益不明。
- **检查更新通道**（原设置页那两行 `UnsupportedSetting` 假开关换成真控件）：新增 `data/update/AppUpdateChecker.kt` + `feature/settings/UpdateCheckUi.kt`，照 `master:lib/pages/settings/about.dart:200-338` 的口径实现 —— 同一 slug、GitHub `releases/latest` 的 `tag_name`、逐段数字比较（容 `v` 前缀、`-miuix` 后缀、`1.7` 与 `1.7.0` 段数不齐），启动检查 24 小时间隔照 master 的 `implicitData['lastCheckUpdate']`，弹框延后 2 秒也照 master 的 `delay`。偏好新增 `checkUpdateOnStart`（**默认 false**，与 master `appdata.dart:238` 一致）。
  - 只保留 releases 一条通道：master 的主通道是 jsDelivr 上的 `pubspec.yaml`，Compose 包没有这个文件，照抄就是一条永远失败的请求。
  - `Failed` 与 `UpToDate` 分成两种结果：拿不到远端版本时**不能**播"已是最新版本"。
  - 版本比较用 node 复算过 8 个用例：`1.10 > 1.8`（按段取数，不是字典序）、`v1.8.0-miuix` 与 `1.8` 相等、`v2 > 1.8`。
  - `StartupUpdateHost()` 挂在 `MainActivity` 的 `VeneraTheme{}` 里、`VeneraComposeApp()` 之外 —— 全应用一个弹窗位，不必改导航层。

### 换包名带出的三条后果（必须先知道，不是代码问题）

1. **旧包不会被覆盖**：新旧是两个安装身份，旧 `com.venera.compose` 连同数据仍在手机上（也还能用来测帧数）。
2. **检查更新现在是"诚实的无更新"**：该仓库最新 release 是 `v1.6.6-miuix`，本包 1.8 更高，所以点「检查更新」会报"已是最新版本" —— 要等用户往这个发布线推 ≥1.9 才会真弹。若发布页仍挂 Flutter 产物，点「更新」跳过去下载的同包名 APK **签名与本包不同（本包 release 用 debug keystore 兜底签名），覆盖安装必然失败**，得先卸载。
3. **备份只带得走 4 张表**：`BackupManager.exportBackup()` 导出的是 history / favorite / reading_stats / content_guard_rules；主题与阅读偏好（SharedPreferences）、已装漫画源、下载队列**不在包里**。所以"先备份再换"能救回收藏与历史，但外观与源要在新包里重配（源可在「漫画源」页按仓库索引一键重装）。导出落点见下一节 —— 2026-09-22 之后由用户在系统「保存为」面板里自选位置与文件名，不再是"缓存 + 分享面板"。

### 尚未真机验证

设备整天没连上。新包上要亲眼确认：桌面/最近任务里是真图标（不是系统默认）、设置顶部大图标是 `app_icon`、版本号显示 1.8、「检查更新」按钮真发出请求并按结果播报、打开漫画源脚本仍能拿到 appVersion。

## 存储目录自选与导入导出位置自选（2026-09-22，用户点名；方案文档 storage-path-selection-2026-09.md）

需求是三条：本地漫画存储路径改成用户自选目录、导入/导出数据改成用户自选位置、下载管理跟着同一个路径走。

- **机制（用户从三条里点的第 1 条）**：系统目录选择器 `ACTION_OPEN_DOCUMENT_TREE` 选目录 → tree uri 的 documentId（`primary:Download/漫画` / `<uuid>:/path` / `raw:/abs/path`）换算成**真实路径** → 用 `java.io.File` 直接读写。代价是公共目录要「所有文件访问」（`MANAGE_EXTERNAL_STORAGE`，manifest 已声明）。纯 SAF 那条被否：下载链全是 `File` 语义（`length()` + 魔数断点判定、`.tmp`→`renameTo`、`listFiles` 扫描、`ZipFile(File)`），改 `DocumentFile` 要重写整层且大库扫描变慢。
- **可写性判定不信权限位**：`ComicStorageRoot.probeWritable()` 真写一个 `.venera_write_probe` 再读回删除。分区存储下「有授权」≠「写得进」，且用户随时能在系统设置里撤销。探针不过**一律不切换**，弹框讲清代价后给唯一出口（`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`，抛异常退化到总列表页），并把待切换路径记下来 —— 从系统页 ON_RESUME 回来自动重测并接着走，不让用户重挑一遍目录。
- **单一事实源 `download/ComicStorageRoot.kt`**：`DownloadManager`（每次现读，换目录不必重启进程）、`LocalComicManager`（书架扫描与 CBZ 导入落点）、设置页三处共用 `resolve()`。`.nomedia` 随根目录一起注入。
  - **`download_tasks.json` 恒定留在内置私有目录**（`tasksFile()`）：换根不该让进行中的队列整体失联。搬家后由 `DownloadManager.relocateTasks(old,new)` 重写任务里的 `directoryPath` 前缀 —— 不重写的话 `isChapterDownloaded` 会把已完成章节判成未下载，点一下直接重下一遍。
  - **顺序刻意**：`applyStoragePath` 先迁移、后写偏好。迁移抛错时根目录压根没换过，不会留下「一半在新、一半在旧」。失败 Toast 明写「存储目录未改动」。
  - **`rejectReason()` 守卫**：整块存储根（`.nomedia` 会糊满全盘）与 `Android/` 及其子树（系统保护区）直接拒绝并说明理由；换算不出真实路径的虚拟根也拒绝。raw 与 canonical 两种形态都比一遍 —— 绑定挂载机型上 `/storage/emulated/0` 规范化后可能变成 `/mnt/...`，只比一种会误判。
- **切换语义（用户点的「切换时问我」）**：旧根非空才弹框，两个选项的差别按实话说 —— 选「仅对新下载生效」时旧目录内容**不再出现在本地书架**、已下载判定转 false、再点会重下。旧根为空则直接切。自定义态多给一行「改回应用私有目录」。
- **导入导出**：新增 `sync/BackupTransfers.kt`（`exportBackupTo(uri)` / `importBackupFrom(uri)`，两端都先过一遍私有缓存再解包/打包，因为 `ZipFile` 只能读真实文件）。设置页「导出数据」= 系统「保存为」面板（`CreateDocument("application/octet-stream")`，不填 zip 以免面板强改 `.venera` 扩展名），「导入数据」= `OpenDocument`，两行不再只是往云备份页跳转。`SyncBackupScreen` 的导入删掉手写副本、改调同一 helper。这条路径**不需要任何存储权限**（单文件走 ContentResolver 流）。
- **未动**：`Navigation.kt`（保护域，本轮根本不需要它）、`LocalComicScreen` 的 CBZ 导出（仍 cacheDir + 分享面板，不在需求里）、备份包的表范围、`WebDavSyncManager`。
- **尚未证实（设备未连）**：真机要看 ①ColorOS 选择器能否选到目标公共目录、探针是否一次通过；②「所有文件访问」那两个 intent 在 ColorOS 上落到哪一页、回来重测是否识别到已授予；③外置卡卷的 uuid 换算；④迁移在几十章规模下的耗时（`copyRecursively` 没有进度反馈）；⑤`CreateDocument` 面板里能否新建文件夹。这些在拿到设备前都只是静态推断。


## 2026-09-22 追加：动了一个 FROZEN 文件 + 删掉一个已冻结的页面

### 1. `ContentGuardManager.kt`（清单里标 **FROZEN**）被改，为的是「屏蔽 AI 生成漫画」

用户明确要这个功能，而判定链只有这一个落点，所以动了它。改动内容：

- 新增偏好 `block_ai`（与 `nsfw_mode` 同一份 `venera_guard_prefs`）+ `blockAiComics` StateFlow + `setBlockAiComics()`。
- 新增判据 `AiTagKeys` / `isAiTagValue()`、`AiTitlePatterns` / `isAiTitleMarked()`（均文件级纯函数，可单测），
  以及 `isAiMarked(title, tags)`（标签先繁转简再等值比对，或标题命中预编译词组）。
- `filterComicModels` 加 `aiHide` 一路；`filterExploreParts` 早退守卫补上该条件。
- 三个 `coverMaskStateFor` 重载在 `"OFF"` 早退**之前**判 AI 并返回 `HIDDEN`。

**既有判定链的语义未变**：用户规则 > 源级预设 > 显式 R18 正则 > 默认 safe 的顺序、LRU 缓存、
别名解析全部原样；AI 是一条**独立的**附加剔除，不参与 R18 那三档模式。
标题判据是后补的（e-hentai 把 `[AI Generated]` 写在标题、tags 里没有 ai，纯标签判据整批漏掉），
只用「词组 + 括号独立词」，不匹配裸 `ai`。
单测：`app/src/test/.../security/guard/AiTagPredicateTest.kt`（标签与标题各锁正反两面，8 例）。

### 2. `ContentGuardScreen.kt` 已删除，「内容屏蔽与分级守卫」二级页取消

原因：它和 `BlockingSettings` 里的「成人内容处理」暴露的是**同一个偏好** `nsfwMaskMode`，
两份控件、两套措辞（不处理/模糊封面/隐藏条目 vs 不过滤/封面打码/彻底隐藏）。

处置：删「成人内容处理」，把守卫页的两项（R18 遮罩模式、AI 屏蔽开关）搬进
`feature/settings/BlockingSettings.kt` 的「内容守卫」分组，沿用守卫页那套措辞；
`SettingsSubScreen.GUARD` 路由与 `onGuard` 参数一并移除。

**顺带补回一个会被弄丢的入口**：`KEYWORD`（关键词屏蔽）的规则管理原先**只有守卫页能进**，
`BlockingSettings` 的分组只列了 TAG/AUTHOR/COMIC_ID。合并时把 KEYWORD 加进了分组循环，
否则已保存的关键词规则将无法查看与删除。

文件可从 `7c0cc68` 取回：`git checkout 7c0cc68 -- app/src/main/java/com/venera/compose/feature/ContentGuardScreen.kt`

## 信息架构改判：历史从主 Tab 降回二级页（2026-09-23 决策 / 2026-09-24 落地，用户点名）

本节就是第二批冻结里那条「Tab 枚举顺序、路由映射与顶栏齿轮入口**不允许顺手变更**（改动需重新评审）」所要求的**那次重新评审**。
用户原话：「我觉得可以把底栏的历史去掉放回二级界面，然后新增一个画廊的页面」。本轮只做**腾位**这一步；
画廊本体按 `gallery-module-isolation-plan-2026-09.md` 由另一条线实现。

### 反掉的是第二批冻结里的两条
- ❌「信息架构决策记录：历史 = 高频主 Tab」 → ✅ 历史是**二级页**：底栏不常驻、有返回语义。
- ❌ Tab 枚举顺序 `HOME → HISTORY → FAVORITES → SEARCH → EXPLORE` → ✅ `HOME → FAVORITES → SEARCH → EXPLORE`（4 项）。
  枚举顺序同时是**底栏顺序**与 `tabSwipePager` 左右横滑翻页顺序（单一真相 `VeneraNavTab.entries`）。
  **第 3 位（收藏右侧）预留给画廊** —— 插入位已写进 `VeneraFloatingNavBar.kt` 的注释，别插成第 5 位。
- `HISTORY` 是**从枚举里物理删除**，不是留着不用。留着它，`VeneraNavTab.valueOf("HISTORY")` 就还是合法值，
  旧存档会被静默当成「用户还要历史页当启动页」，那是一条没人会发现的死分支。
- **顶栏齿轮入口未动**（那三项里的第三项）。

### 改了哪些文件（8 个）
| 文件 | 改动 | 冻结身份 |
|---|---|---|
| `components/VeneraFloatingNavBar.kt` | 删 `HISTORY` 枚举项 + 记改判出处与画廊插入位 | 导航层（保护域） |
| `feature/Navigation.kt` | `routeFor`/`titleFor` 去 HISTORY 分支；`currentTab` 不再把 `HistoryRoute` 认作主 Tab；首页分区头 `gotoTab(HISTORY)` → `navigate(HistoryRoute)`（压栈、可返回）；`composable<HistoryRoute>` 补 `onBack`；`ReaderRoute` 传 `onOpenHistory` | 保护域 |
| `feature/HistoryScreen.kt` | 加 `onBack` 参数 + 顶栏返回箭头（多选态下该位置的动作仍是「退出多选」） | 🧊 **FROZEN**，用户点名本轮必需 |
| `reader/VeneraReaderScreen.kt` | `VeneraReaderScreen`/`ReaderSessionContent` 加 `onOpenHistory`；顶部胶囊岛在「模式快捷胶囊」左侧加一个 40dp 圆形 History 位（几何照返回键） | 未冻 |
| `feature/SettingsHost.kt` | 新增 `SettingsEscape.OpenHistory` + `consumeSettingsEscape` 分支 + 宿主回调 | 保护域 |
| `feature/SettingsScreen.kt` | `AndroidSettingsScreen` 透传 `onOpenHistory` | — |
| `feature/settings/SettingsHome.kt` | 抽出 `SettingsEntryRow`（七个分区行与历史行同一几何）；新增**单独一组**「阅读历史」 | — |
| `feature/settings/ExploreSettings.kt` | 「启动页面」下拉去掉 `HISTORY` 选项 | — |

三处刻意的设计选择：
- **阅读器那条入口放顶栏、不放底部功能键行**：那一行已经是 6 个 `weight(1f)` 键（自动播放/目录/存图/插图/分享/设置），
  第 7 个会把「自动播放」这种四字标签压断行。
- **设置里的历史必须走越界交接**：历史页只挂在 MainActivity 的图上，设置主页是另一个 Activity，`navigate` 不到 ——
  与本地漫画 / 阅读器 / 题材下钻那三条出口同一条路（填槽 + 拉起 MainActivity，消费即清见 `consumeSettingsEscape`）。
- **历史不混进那七个「设置分区」**：它不是偏好项，点下去是跳回 MainActivity，与「在本 Activity 内换页」语义不同，所以单独一组。

### 存量 `"HISTORY"` 启动偏好的处置（用户拍板：静默回落）
- 启动侧：`runCatching { VeneraNavTab.valueOf(startTab) }.getOrDefault(HOME)`（`Navigation.kt:485`）→ 回落首页。
  **这条 runCatching 是唯一防线**，正因为枚举项已删它才成立。
- 设置侧：那一行显示现成的「未识别的已保存值：HISTORY」（`SettingsComponents.kt:218`），**不做静默改写** ——
  用户改过的偏好被动了要说得出来。
- 「启动页面」这一项本身留着：在 4 个主 Tab 里选启动页，功能仍在，不是假开关。

### 未动
- 底栏组件的几何与玻璃层、`bottomBarClearance` 契约。历史页网格的 `bottom = bottomBarClearance`
  与下载 / 图片收藏 / 本地漫画 / 统计那四个二级页同口径，**不改**（改了反而不一致）。
- `HistoryViewModel` / `HistoryDao`（数据层，本来也未冻）。
- 首页「历史记录」分区的取数与卡片结构：只换了那条落点的导航语义。
- `tabSwipePager` 实现本体、顶栏齿轮入口。

### 已知代价（未真机验证，别当已验收）
**阅读器 → 历史 → 返回**是这条新路径上唯一有状态风险的。阅读器留在栈里，`NavBackStackEntry` 的 SavedStateRegistry
保住的是 `rememberSaveable` 那批：控制层显隐、抽屉态、以及 `rememberLazyListState` / `rememberPagerState`（**页码会回来**）。
保不住的是纯 `remember`：**`currentChapterIndex`（`VeneraReaderScreen.kt:170`）**与 `chaptersState` 里按需补进来的页码表。
→ 预测症状：**跳过话之后**再去看历史，回来会掉回「开书时那一话」，页码却按掉回前的页号定位；同话内看历史不受影响。
本轮**不**顺手改：把 `currentChapterIndex` 转 `rememberSaveable` 之前，得先确认「恢复到的那一话页码表尚未加载」时
`switchToChapter` 的加载链会不会被绕过 —— 那是进度记忆逻辑，超出腾位范围。真机若证实，按「导航条目组合重建」那条老根因另开一轮。

### 验收口径（真机，只读不代点）
1. 底栏 4 个 Tab，顺序 首页 / 收藏 / 搜索 / 探索。
2. 首页「历史记录」分区头 → 历史页 → **返回能回首页**（与旧行为最大差别：以前是切 Tab，返回等于退应用）。
3. 历史页底栏**隐藏**；多选态下点返回箭头 = 退出多选，再点才返回。
4. 设置首页「阅读历史」→ 设置 Activity 收起、落到 MainActivity 的历史页；从那里返回应回**首页**，不是回设置。
5. 阅读器顶栏 History 图标 → 历史页 → 返回 → 回阅读器，同话内页码不跳（跨话那条见上「已知代价」）。
6. 横滑切主 Tab 只在 4 个之间循环；落在历史页时横滑不切页（`currentTab == null`）。
7. 存过 `"HISTORY"` 的设备冷启动落首页，且设置里那行显示「未识别的已保存值：HISTORY」。
8. 构建侧（2026-09-24）：`:app:compileDebugKotlin` / `:app:testDebugUnitTest` / `:app:assembleDebug` 全绿。

## 2026-09-24 追加：底栏第 5 项 GALLERY（画廊 / yande.re）—— 同一处冻结项的第二次评审

用户点名「先把 yande.re 的源接入项目，就拿画廊，放在搜索右侧第四个导航页，先显示人气，按瀑布流做卡片」。
本节接上一节：上一节腾出来的那个真位，本轮由画廊填上，所以**评审对象仍是 `:32` 那条**
（Tab 枚举顺序、路由映射、顶栏齿轮入口）。顶栏齿轮入口本轮未碰。

**改口记录**：09-23 腾位时我在上一节写的是「第 3 位（收藏右侧）预留给画廊」，
09-24 用户改成「放搜索右侧第四个」。**以本节为准**，上一节那句按此重读；旧正文不改写。
最终顺序：`HOME → FAVORITES → SEARCH → GALLERY → EXPLORE`（5 项）。

### 改了哪些文件（7 个，其中 3 个是导航层保护域）
| 文件 | 改动 |
|---|---|
| `components/VeneraFloatingNavBar.kt` | 枚举加 `GALLERY("画廊", Icons.Outlined.Image, Icons.Filled.Image)` 于 **SEARCH 之后**；更正上一轮那条「第 3 位预留」注释 |
| `feature/Navigation.kt` | 新增 `GalleryRoute` / `GalleryPostRoute(postId: Long)`；`routeFor`/`titleFor` 各加分支（`titleFor` 现在是零调用点的穷举负担，仍加）；`currentTab` 认 `GalleryRoute`；两个 composable 条目（大图页在同一个壳图里，返回走 `popBackStack`） |
| `feature/settings/ExploreSettings.kt` | 「启动页面」下拉加 `"GALLERY" to "画廊"` —— **不加就是假开关**（选了不能生效），加了才是 5 个主 Tab 选启动页 |
| `components/venera/VeneraCover.kt` | 加可选 `imageLoader: ImageLoader? = null`（null = 应用级单例，与现状逐字等价）。画廊自带缓存预算，不能把几百张 preview 灌进漫画侧的默认缓存 |
| `gallery/**`（7 个新文件，全在独立包） | `data/GalleryPost.kt` `data/YandeReClient.kt` `data/GalleryImageLoader.kt` `domain/GalleryGuard.kt` `ui/GalleryScreen.kt` `ui/GalleryViewModel.kt` `ui/GalleryPostScreen.kt` |
| `test/.../gallery/GalleryGuardTest.kt` `GalleryPostParsingTest.kt` | 判据与解析口径各锁一组 |

**漫画侧数据层零改动**（隔离裁决的验收标准）：不碰 `VeneraDatabase` / `favorite_images` /
`ComicSource` / JS 源脚本链 / `DownloadManager` / 阅读器。本轮**没建任何表** —— 浏览不写库，
建了没人读就是「实现了但零调用点」。

### 三条实测换来的设计（不是推断，探针记录在 gallery 方案 §五）
- `/post/hot.json`（Danbooru 的人气端点）在 yande.re **404** → 人气只能 `tags=order:score`，实测翻到 700 页有效。
- `/post/{id}.json` **404**、`/post.json?id=N` **200 但参数被静默忽略**（回一整页别人的图）
  → 单条只能 `tags=id:N`。这条如果照 Danbooru 抄，会做成"点一张看一屏无关图"且没有任何报错。
- 人气前 120 条 `e=84/q=30/s=6` → **分级守卫从 P1 提前到本轮**：只读共享那份「成人内容处理」偏好
  与用户屏蔽规则，判据 `rating != "s"`（未知值宁可错打码），黑名单排在分级模式之前。

### 一处被单测抓到的自身缺陷（已修，记口径）
第一版 `isAdultMarked = rating == "e" || rating == "q"` → 未知/空 rating **静默放行**。
`GalleryGuardTest` 断言失败暴露它，改成 `rating != "s"`。方向性判据：在一个天然出成人内容的站上，
未知值默认按成人处理，而不是默认安全。

### 未动
- 顶栏齿轮入口；`tabSwipePager` 实现本体（它按 `VeneraNavTab.entries` 取，自动多一格）；
  `VeneraApp.newImageLoader()` 那一行（漫画侧继续吃 Coil 默认预算，一个字没改）。
- `FavoriteImagesScreen` 的收藏墙：画廊卡片复用的是 `VeneraCover` + miuix `Card` + 瀑布流几何口径，
  没有从收藏页搬代码，也就没有改到它。

### 尚未真机验证（别当已验收）
1. 底栏 **5 项**、画廊在第 4 格；横滑切主 Tab 的循环里多了画廊。
2. 进画廊 Tab 首屏出**人气瀑布流**（两列、大图 3 列），卡片高度**不跳动**（比例来自 JSON 的 jpeg 档宽高，不是加载后回填）。
3. 触底自动翻页；失败时页脚是「加载失败：原因 · 点击重试」，不是静默变短列表。
4. 点卡片 → 大图页只出**那一张**（若出一屏别的图，就是 `tags=id:` 那条又写错了）；大图可双指缩放。
5. 「成人内容处理」三档逐一验：不过滤=全部可见；封面打码=网格与大图都打码且带 R18 角标；彻底隐藏=`e`/`q` 条目整条消失，且**空了要说"被你的规则挡完了"**而不是"没有图"。
6. 加一条 TAG 屏蔽规则（值取某张人气图的 tag）→ 该条在画廊里消失，且此时把分级模式调成「不过滤」**仍然消失**（黑名单不被分级开关覆盖）。
7. 设置「启动页面」出现「画廊」，选它冷启动落画廊。
8. 隔离核对：`adb shell run-as <包名> du cache` 下应出现独立的 `gallery_img/` 目录，漫画封面缓存不受画廊刷图影响。
9. 构建侧（2026-09-24）：`compileDebugKotlin` / `testDebugUnitTest`（134 例）/ `assembleDebug` 全绿。

### 同日追加：点图改成**三级**（照 PixEz 的图片播放器）

用户点名「参考 pixez 这个图片播放器，首页点击图片后跳转到二级播放器，再点击一次跳转到三级高清大图」，
并逐条拍了四个板（三级吃原图 / 二级照 pixez 的信息密度 / 顶栏先不放收藏与下载 / 标签先不可点）。
比对过的 PixEz 源码就在 `build/pixez/`（`compose-miuix/shared/.../ui/screens/IllustDetailScreen.kt`
与 `ui/components/IllustFullScreenViewer.kt`）。

| 档 | 我们的实现 | PixEz 对应 | 地址档位（实测字节） |
|---|---|---|---|
| 一级 | `GalleryScreen` 瀑布流 | `IllustStaggeredGrid` | `preview_url` ~20 KB |
| 二级 | `GalleryPostScreen`（**路由** `GalleryPostRoute`） | `IllustDetailScreen`（真路由） | `jpeg_url` 0.7~1.4 MB |
| 三级 | `GalleryFullViewer`（**overlay，不是路由**） | `IllustFullScreenViewer`（同样不是路由） | `file_url` 原图 4~32 MB |

**三级为什么是 overlay 而不是路由** —— 这条与 PixEz 的选择一致，但理由在我们仓库里更硬：
压路由会把本页的组合销毁,裸 `remember` 的状态全没（记忆「导航条目会重建组合」）。
overlay 天然保住二级滚动位置与信息卡,系统返回用 `BackHandler(enabled = hdOpen)` 抢先关三级。

**原图那 87 MB 的三条防护**（口径抄 PixEz 的注释理由，落在 `galleryFileRequest`）：
`size(4096)+INEXACT` 挡巨幅瞬间撑爆堆（注意 Coil 的 `size` 是**下限**语义，别指望它降内存）；
`memoryCachePolicy(READ_ONLY)` 让 87 MB 位图**不进内存缓存**、页面一关即可回收；
`placeholderMemoryCacheKey(jpeg key)` 拿二级那张当过渡底图,所以三级**刻意没有加载指示器**
（telephoto 那支 `ZoomableAsyncImage` 也没暴露 `onSuccess` 可挂 —— 造观察不到的状态就是假控件）。

**二级信息卡只摆站方真给的字段**：`score` / `width×height` / `rating` / `author` / `source` / `file_size` / `md5`。
参考截图那行「👁 9289」是 pixiv 的浏览量，**yande.re 响应里没有这个字段**，不照抄一个假数。

新增/改动文件：`gallery/ui/GalleryFullViewer.kt`（新）、`gallery/ui/GalleryPostScreen.kt`（重写为二级 + 三级宿主）。
漫画侧本轮零改动；`VeneraCover` 未再动。构建：`compileDebugKotlin` / `testDebugUnitTest` / `assembleDebug` 全绿（2026-09-24）。
真机待验：一级点图→二级只出这一张、二级点图→三级无 chrome 可缩放、三级单击切顶栏、
三级按系统返回只关三级不弹二级、开原图后回二级不重刷、分级打码档在二三级同样生效。

### 真机第一轮反馈（2026-09-25，用户截图三条：图片问题很大 / 没有骨架 / 顶栏要统一）

三条都成立，根因如下（**其中第二条是我上一轮自己做的错误决定**）：

1. **三级图被顶到屏幕上方、下面一大片黑**。根因：`ZoomableAsyncImage` 我**没给 `fillMaxSize`**，
   它不像 `AsyncImage` 那样自带填充语义，于是那一层退化成"按位图尺寸包一层"、贴在 Box 顶部。
   修：两层图都显式 `Modifier.fillMaxSize()`。
2. **加载没有骨架**。根因不是漏画，是我上一轮的判断错了：telephoto 那支 `ZoomableAsyncImage`
   **不转发 `onLoading`/`onSuccess` 回调**（从 0.19.0 sources 核过），我当时把"拿不到回调"
   当成了"所以不该挂指示器"，还写进注释自洽。正解是**换实现而不是砍反馈**：
   改用 `Modifier.zoomable(state, onClick)` + 自家 `AsyncImage(onLoading/onSuccess)`，
   于是二级铺全站骨架 `VeneraShimmer`、三级在底图上叠原图并在右下挂波浪环。
   连带撤掉 `placeholderMemoryCacheKey`：placeholder 会让 `onSuccess` 在底图命中缓存时**先响一次**，
   骨架状态就再也读不准 —— 改成自己铺一层二级 jpeg 当底图，语义明确。
3. **顶栏不是本应用样式**。根因：我照 PixEz 搭了一层半透明黑浮层（`Color.Black.copy(0.35f)` + 白图标），
   而本应用所有页面早已是"页内自治的 `VeneraTopAppBar`（大标题 + 折叠 + 毛玻璃背板）"。
   修：二级换成 `VeneraTopAppBar` + `rememberVeneraTopAppBarBehavior` + `rememberTopBarBackdrop`
   + `blurBackdropSource`，内容避让 `statusBarTop + 104.dp`，与首页/探索/历史同一口径。
   三级仍保持黑底浮层药丸（那是全屏看图，与阅读器控制岛同类），但补了 `statusBarsPadding`
   —— 截图里那颗返回键压在状态栏上。

顺带一处措辞：信息卡的「上传」改成「作者」。
一级网格的骨架本来就有（走 `VeneraCover` 内置 shimmer）；若反馈指的是那一屏，需要再看一张一级截图。
构建：`compileDebugKotlin` / `testDebugUnitTest` / `assembleDebug` 全绿（2026-09-25 00:12 出包）。
**仍未真机验证**：二级顶栏观感是否与首页/探索一致、三级图是否居中、原图加载期间右下的环、返回只关三级。

## 2026-09-25 追加：画廊接第二站（Danbooru）+ 落地流改「月榜池 + 带权随机」

用户点名的一轮。方案与全部实测数据在 `gallery-dual-source-hot-pool-plan-2026-09.md`，这里只记冻结面与判断。

### 三条实测换来的设计（探针 2026-09-25）

1. **UA 会把整站挡死**。`UserAgentPolicy.DEFAULT_USER_AGENT` 是移动端 Chrome 串，
   而 Danbooru 挂在 Cloudflare 上：拿它去打，**API 和 CDN 都回 403 + `cf-mitigated: challenge`**
   （连 180×180 缩略图都是 5.9 KB 的挑战页 HTML）。换 `Venera/1.0 (Android)` 同一 URL 就 200。
   → 图片那一路走 `ImageHeaderPolicy.builtin` 加一条 `"donmai.us"`（与仓内既有的
   `"picacg.com" → okhttp/3.8.1` 同型），JSON 那一路由 `DanbooruClient` 自己带。
   **故意没动** `UserAgentPolicy` 的全局默认：那是 33 个漫画源共用的网络身份。
2. **两站的"月度热门"形态完全不同**：Danbooru `order:rank` 实测 200 条 `created_at` **全在当月**、可深翻、
   带 `fav_count`；yande.re `popular_by_month` **固定 40 条 / 忽略 limit / 无 page**，而且
   **整个字段表里没有 `fav_count`**。→ 加权必须在**各站池内**算分位（实测分数中位 731 vs 30，差 24 倍，
   绝对分跨站比会把一站清零），且缺维**摊权重**而不是当 0（当 0 = 把整站当成"零收藏"）。
3. **`rating` 多一档 `g`**（general，yande.re 只有 s/q/e）。第一轮判据 `rating != "s"` 接第二站后
   会把最干净的一档**误打码** → 改成只放行 `s`/`g`，未知值仍然宁可打码。

### 用户要求里被实测推翻的一条（已确认不做）

「最好加图片感知哈希」：跨站 160×200 条比三种键 **md5 / 规范化出处链接 / pixiv_id 交集全是 0**，
Danbooru 自家月榜 200 条两两 128-bit `pixel_hash` Hamming **≤14 的对数也是 0**，
而 yande.re **根本没有** `pixel_hash` 字段（单边有、无法互比）。自建哈希要为每张候选图额外下载
2.8~31.9 MB 原图，换来的实测收益是 0 条。用户看后选「不做 pHash」，去重只留零成本的三键。

### 改了哪些文件（12 个）

| 文件 | 动作 | 冻结面 |
|---|---|---|
| `gallery/data/GallerySite.kt` | 新增：站点身份（显示名 / 单页前缀 / 路由键） | 画廊内 |
| `gallery/data/GalleryPost.kt` | 由"站方字段直译"改成**归一模型**（site / favCount 可空 / large 档 / uid / sourceKey） | 画廊内 |
| `gallery/data/YandeReClient.kt` | 站方字段名收进 `YandeReDto`；加 `fetchMonthlyHot()` | 画廊内 |
| `gallery/data/DanbooruClient.kt` | 新增：月榜 + 单条 + DTO；403 带 CF 特征时报"被 Cloudflare 拦截（需要非浏览器 UA）" | 画廊内 |
| `gallery/domain/GalleryPool.kt` | 新增（纯函数）：滤非图片 / 三键去重 / 站内分位 / ES 带权不放回 / 动态配比夹 0.3~0.7 / 首屏锁头部 / 两站交错 | 画廊内 |
| `gallery/domain/GalleryFeedSource.kt` | 新增：两站并发一轮 + 缺哪站的原因 | 画廊内 |
| `gallery/ui/GalleryViewModel.kt` | `round` / `seedBase` / `sourceNotice` / `shownKeys()` / `accept()` | 画廊内 |
| `gallery/ui/GalleryScreen.kt` | 双源取数、`uid` 做列表 key、左下来源胶囊、单源退化提示条、空态文案按两站实测改写 | 画廊内 |
| `gallery/ui/GalleryPostScreen.kt` / `GalleryFullViewer.kt` | `site` 进签名与**缓存 key**；信息卡"收藏"一行只在站方给了才摆；`g` 档标签 | 画廊内 |
| `feature/Navigation.kt` | `GalleryPostRoute` 由 `(postId)` 变 `(siteKey, postId)` | **保护域**。只给一个既有路由加必填参数 + 一个 import，**未改** Tab 枚举顺序、路由映射表与顶栏齿轮入口（与 09-19 加 `sourceName` 同类） |
| `data/network/ImageHeaderPolicy.kt` | 内置表加一条 `"donmai.us"` | 漫画侧共用基础设施，但这是该表设计出来就有的用途（"按 host 绑头"），未改任何判定逻辑 |
| `gallery/ui/GalleryScreen.kt` 内 `GallerySourcePill` | 画廊私有胶囊 | **没有**动被漫画侧 6 页共用的 `VeneraSourceBadge`（那枚口径是左上小圆角） |

### 两处被自己抓住的缺陷（写代码当期就修，未进单测）

1. 去重表初版只把 `uid` 登记进 map、却用 `keys.any { it in deduped }` 查 md5/source —— **永远查不中**，
   等于跨站去重整条是摆设。改成维护一个收录全部键的 `seenKeys` 集合。
2. 交错函数初版每一拍 `out.count { … }`，是 O(n²)；换成"取当前剩余最长队列"。
   另外 `allocateQuota` 里一个装站点列表的变量被我叫成 `rnd`，与随机数无关，已改名。

### 一处与方案的刻意偏差

§6.3 原写的"新鲜度衰减"**没实现**：Danbooru 那一路本身就是当月池（衰减是空操作），
yande.re 那一路是全站历史人气（一衰减就把"人气"静默改成"本月人气"），两站语义不对称，
加这层只会把混合池推向一边。要做要先统一"月龄"口径，留待有需求。

### 未动

漫画侧一切数据层与 UI；`ContentGuardManager`（只读共享）；`UserAgentPolicy` 全局默认；
`VeneraSourceBadge`；`build/` 下任何第三方参考工程；画廊**仍未建表**（浏览不写盘，P3 收藏那条还悬着）。

### 尚未真机验证（别当已验收）

构建面 `compileDebugKotlin` / `testDebugUnitTest`（156 条，画廊 32 条）/ `assembleDebug` 全绿。
但本轮**一条真机项都没跑过**，尤其是：
1. 两站的图在设备上是否都出得来（UA 那条只在开发机出口验过，手机出口 IP 不同）；
2. 连点两次"换一批"是否两屏不同、且都是眼熟的好图（带权随机 + 头部锁定）；
3. 上滑翻页不出现重复图（`exclude` 与种子序列）；
4. 卡片左下胶囊两站都显示、压深色图仍可辨；
5. 点 Danbooru 的图进二级/三级，标题里的站点与 id 是否就是那一张（`siteKey` 进路由之后）；
6. 打码模式下 `g` 不被糊、`e`/`q` 被糊；
7. 只有一站给上内容时，页面是否真的把缺的那站说出来（不是变成单源还看不出来）。

### 同日第二次修正：月榜换成用户点名的 Explore 端点

用户反馈"我看还是没有 donmai 的源"并给出 `danbooru.donmai.us/explore/posts/popular?...&scale=month`。
两问分开答：

- **为什么没看到**：`adb devices` 为空，手机上装的还是这一轮之前的包（本轮包 02:16 才出）。
  这既不是代码坏的证据、也不是代码没坏的证据 —— 真机一条都没跑过。
- **端点实测（换成它了）**：`/explore/posts/popular.json?date=<当天>&scale=month&page=&limit=200`
  匿名 200、返回**与 `/posts.json` 同一套字段**的完整 post 数组（现有 DTO 零改动复用）、
  page 1/2/7 **相邻两页零重叠**、第 1 页 score 827~299 / `fav_count` 274~793。
  比原先的 `posts.json?tags=order:rank` 更对：那份是排名副产物（当月分数尾巴 9 分的也带进来），
  这份是站方 Explore 页**明写的"这个月最火的"**口径，正是用户原话要的"月度热门排行"。
- **两条新实测**：explore 月榜第 1 页 200 条里 **`mp4` 占 40 条（20%，不是 order:rank 那份的 4%）**、
  且 **52 条没有 `sample` 变体** → 客户端扩展名白名单与"sample → `large_file_url`"两级兜底都不是可选的。
- **连带改的**：yande.re 月榜实测固定 40 条不可翻页，所以第一轮改取**当月 + 上月两份**（80 条）
  —— 只取当月会在配比里被 Danbooru 的 200 条压没，"两站混合"就名存实亡了。
- 方案文档 §一/§二/§6.1/§6.2 里基于 `order:rank` 的数值已全部改成 explore 的；
  原先那句"两站分数差 24 倍"随端点失效，改成"两个不是同一个量（全站历史累计分 vs 当月位次分）"，
  **加权必须走池内分位**这条结论不变。
- 代码面：`DanbooruClient.fetchMonthlyHot` 换端点、`YandeReClient.fetchMonthlyHot(monthsAgo)` 加参数、
  `GalleryFeedSource` 第一轮多取一份上月。漫画侧与 UI 层零改动。
- 构建：`testDebugUnitTest`（156 条，0 失败）/ `assembleDebug` 全绿（02:16 出包）。真机仍未验。

### 同日第三次改判：权重与月榜**全部删掉**，改成两站最新混搭

用户原话两条：「删掉之前我说的权重，和月度榜，改成 `danbooru.donmai.us` 和 `yande.re/post`
这两个最新图片的混搭，比例均匀点就行」，以及「**不采用严格交替**。总体数量比例保持接近均衡，
单一来源允许连续出现 3~4 张，但避免长时间连续来自同一来源」。

- **作废面**：候选池 / 站内分位归一 / 四维加权 / Efraimidis–Spirakis 抽样 / 首屏锁头部 /
  动态配比夹 0.3~0.7 / 随机种子 / 三个月度端点调用（Danbooru `explore/posts/popular.json`、
  `posts.json?tags=order:rank`、yande.re `popular_by_month.json`）。
  `domain/GalleryPool.kt` 与其 14 条单测整体删除，换成 `domain/GalleryMerge.kt`。
  方案文档 §五~§六 从此只是证据，不是实现依据。
- **现行排法**：两站各 30 条，每一拍给两边**同样长**的一段（段长固定序列 `[2,1,3,1,2,3,1,2]`，
  平均 2、最长 3）→ 总量恒等、有连出但不长。拍内固定"先 yande 后 Danbooru"是**有意的**：
  改成两站轮流先出会在拍缝拼出 4 连（被 `单源连出不超过三张` 那条单测抓到，已按此定型）。
  **无随机、无分数** → 同输入必得同序列，翻页/返回重建/重试都是同一屏。
- **仍然保留的两条判据**（与权重无关，都是实测换来的）：扩展名白名单
  （Danbooru 列表会混 `mp4`，月榜那份实测 20%；站方 `type:jpg` 实测返回 0 条 → 只能客户端滤，
  且整站被滤光时报错而不是交回空列表）；去重三键 `uid`/`md5`/规范化 `source` + 翻页 `exclude`
  （最新流是新图往前插的，第 2 页与第 1 页会漂移重叠）。
- **`g` 放行这一版更要紧**：实测 Danbooru **最新 30 条里 `g` 占 15 条**，沿用第一轮
  `rating != "s"` 会白糊半屏。`fav_count` 不再参与排序，只留二级信息卡那一行（站方给了才摆）。
- 新实测（各 30 条 × 2 页）：两站默认列表都是 `created_at`/`id` 双递减、相邻两页零重叠；
  Danbooru 分级 g15/e7/q3/s5、扩展名 jpg16/png14；yande.re 分级 e6/q12/s12、扩展名 png19/jpg10/webp1。
- 漫画侧与 UI 结构零改动；底栏枚举未动（仍然 `HOME → FAVORITES → SEARCH → GALLERY → EXPLORE`）。
- 构建：`testDebugUnitTest` 全仓 154 条 0 失败 / `assembleDebug` 全绿（02:34 出包）。**真机仍未验**。

## 2026-09-25 追加：离开画廊线，修两条既有缺口（备份漏真实收藏库 / 评分假成功）

用户原话：「**先修下其他吧，图片先不理了**」。画廊线（含"设备上 Danbooru 基本没有"的排查、
`GalleryTagResolver` 的可行性后续）**整条挂起**，本节与图片无关。

候选来源：让子代理把根目录 30+ 份方案/审计文档里的待修项扫成清单并**逐条回代码核实**
（本项目有过"文档写已落地但代码里没有"的先例），得 8 条非画廊缺口；用户勾选两条先修。

### 一、备份/恢复走真实收藏库（`official-gap-analysis-round2.md` G-1）

- **核实**：`grep comic_favorite` 只剩三类命中 —— 建表/迁移、`FavoriteDao`（**自身零调用点**，
  注释还写着"详情页现在写进动态夹表"）、`BackupManager` 一出一入。即备份读的是迁移后被
  `migrateLegacyFavorites()` 清空的死表 → 导出恒 0 条收藏，恢复也写进没人读的表，全程不报错。
- **改法**：收藏这一路整体改走 `LocalFavoritesManager` 公开 API，归档升到 **v4**
  （`favorites.json` 每行带 `folder`；新增 `favorite_folders.json` 存夹子清单 + 网络夹绑定）。
  为备份新增的唯一公开接口是 `currentFolders()`（实时读库；`folders` 缓存刚启动时是空的，
  导出用它就会"备份 0 个夹子"，与本条要修的毛病同源）。
- **自己抓住的一处设计回退**：第一版恢复是裸 SQL 直插动态表。两处会静默坏：
  ① 不调 `notifyChanged()` → 恢复完的收藏要重启才看得见，等于把"看不见"往后推一格；
  ② 追更那三列是 `prepareTableForFollowUpdates` 按需 ALTER 的，新设备的夹子没这些列，
  照备份原样写会抛 `has no column named ...` 把**整笔导入**回滚。改走 Manager 后两条一起消失。
- **旧格式必须继续认**：v3 的 `favorite.json` 是旧单表列名（`comic_id`/`cover_url`/`source_name`/
  `folder_name`/`created_at`）。不认的后果不是报错而是"导入成功 0 条收藏"（`id` 取不到 → 整行跳掉）。
  判别用「有 `comic_id` 且没有 `id`」，两套列名实测互不重叠。
  旧行**不给 `display_order`**（交 `null` 给 `addComic` 按偏好递增），硬填 0 会让一次导入的所有条目同值。
- **能测的都抽出来了**：新增 `sync/FavoriteBackupRows.kt`（纯 JVM 编解码判据）+ 9 条单测。
  理由写在类注释里 —— 本项目单测**没有 Robolectric**，android 的 `org.json`/SQLite 在 JVM 测试里
  全是返回默认值的桩，逻辑一沾它们就测不了；而"列名对不上"恰恰是不抛异常、只恢复出空白条目的那种错。
- **刻意没做**（不静默扩面）：`favorite_images` 不入备份（库里只有本地路径，归档没有图片文件，
  导进去就是一列表打不开的图）；`comic_source`/设置/Cookie 仍缺（= G-10 未闭合）；
  跨库仍是两笔事务（收藏在 `local_favorite.db`，其余在 `venera_core.db`，做不到同一事务），
  缓解是"整包先解析进内存再动库"。导入语义是**合并**：本机已有而备份里没有的夹子排到后面，不删任何东西。

### 二、漫画评分假成功（G-14 里的 star 一条）

三层叠出来的，一处比一处隐蔽：`JsComicSource.starRating` ① 丢了 `evaluateAsync` 的返回值恒回 `true`
（源没声明 `comic.star` 也算成功）、② `catch` 里再 `Result.success(true)`；③ 接口默认实现本身
就写 `Result.success(true)`；④ `ComicDetailViewModel.rateComic` 乐观写入 `userRating` 后
**连结果都不看**，不回滚不提示。
- **修法全部用仓内既有写法**，不发明新机制：JS 侧照同文件已修好的 `likeComic`
  （没声明接口 → `false`；异常 → `failure`）；接口默认改回 `false`；ViewModel 照 `toggleLike` 的
  "乐观更新 + 失败/不支持回滚 + 分三种情况如实提示"。
- **同族未修，等点名**：`ComicSource.kt:143-156` 里 `voteComment` 与 `likeComic` 的**默认实现**
  仍回 `success(true)` —— 非 JS 源（`BaoziMangaSource` 不覆写它们）点赞/评论投票会报成功而什么都没发生。

### 未动

底栏枚举、`Navigation.kt`、画廊包、`comic_favorite` 旧表本身（老设备升级还要靠它做一次性迁移）。

### 构建

`:app:testDebugUnitTest` **163 条 0 失败**（新增 9 条）/ `:app:assembleDebug` BUILD SUCCESSFUL（10:27 出包）。
**真机仍未验** —— 且备份这一条本来就验不了闭环：要么两台设备，要么手上一份真 `.venera` 归档。
用户侧要看的点是：详情页给一本**不支持评分**的书打分，是否立刻收到"该源不支持作品评分"且星数退回。

## 2026-09-25 追加二：搜索页「到底了还显示加载更多」

用户报「到底后还是显示加载更多,无法分析是已经加载完了还是需要继续加载」,点名搜索页。
**先更正我自己**：我给用户的候选清单表里,搜索页被标成"三态齐全、唯一的正解先例"—— 那是照文档
`search-page-loading-pagination-2026-09.md` §1 第 6 行的措辞读的,没回代码看判据。三态**在**,
但其中一态的**进入条件**是错的。

- **根因**（详版见该方案文档 §7）：翻页判定写成 `page.maxPage?.let { nextPage < it } ?: comics.isNotEmpty()`,
  源一声明页数,「这一页是空的」这条硬证据就被否决；而 §3.1 的实测早就给出 `maxPage` 是
  `ceil(total/页长)` 的**上界**、33 个源里 0 个给总数。翻到真尽头时 `nextPage < maxPage` 仍成立,
  又因 `currentPage` 只在非空时推进 → 点「加载更多」原地重复同一页请求。
- **修法**：判据抽成纯函数 `feature/SearchPagination.kt`,三条按证据强度排 —— 零新条目即到底；
  页数只用来提前收手、不否决前者；游标型源只能靠前者。首页与翻页共用它。
- **顺带抓住的一处误判方向相反的错**：翻页结果是「先过屏蔽规则、后排重」,于是 HIDE 规则剔空一整页
  会被当成"这页没新东西"而**误判到底**。改成先排重、判据取守卫前的新条目数。
- **代价如实记**：源虚报页数时现在**会**多发一次返回空的需求 —— 它是判定到底的唯一证据,
  方案文档 §4 第 10 条那句"不会再多发一次空请求"已按此改写,不是我把要求降低了。
- `ss-end` 那一行加了 `results.isNotEmpty()`,搜索无结果时不再和「没有搜索结果」叠成两行。
- 构建：`testDebugUnitTest` 全仓 **170 条 0 失败**（新增 `SearchPaginationTest` 7 例）/
  `assembleDebug` 绿（10:52 出包）。**真机未验**；「HIDE 恰好剔空整页」那条真机难构造,只由单测守住。
- **同族未修,等点名**：探索页排行榜把 `maxPage` 丢在接口类型上（`ComicSource.kt:101`）、
  详情页评论页数未知时用 `Int.MAX_VALUE`（`ComicDetailViewModel.kt:50`）、
  冻结页 `SourceSectionScreen.kt:133-137` 故意压 null。

## 2026-09-25 追加三：画廊落地流改成「两站上一天热门各 20 张打乱」+ 引入 mp4

用户原话：「画廊改成 `yande.re/post/popular_recent?period=1d` 和 `danbooru/explore/posts/popular?date=&scale=day`
上一天的热门 **各取 20 张然后打乱**，如果 donmai 有 mp4 就引入 mp4」。
上一节刚说"图片先不理"，这节是用户主动重新点名，不算自作主张扩面。详版在
`gallery-dual-source-hot-pool-plan-2026-09.md` §十三（含全量实测表与落地记录）。

- **实测换来的三条**：① yande.re `popular_recent.json` **固定 40 条**、`limit`/`page` 无效、
  不认的 `period` 值静默退回 `1d`（`1d` 与 `1mo` 实测 40/40 同一批，而 `1w` 只重叠 2 条）；
  ② Danbooru `scale=day` **不带 `date` 静默回 0 条**（状态仍 200）→ 两站都把空列表当失败抛出；
  ③ 视频字段与图片不同：`has_large=false`、`large_file_url` 就是原片 mp4，
  `variants` 只有静帧 + 原片、**没有更小的视频转码档**（样本 16.1 MB / 42.8 秒），
  时长只在 `media_asset.duration`，顶层连 `type` 键都没有。
- **翻译层必须为视频改道**：中间档换成 `720x720` 静帧，否则二级把 `.mp4` 交给 Coil，
  观感就是"一张永远加载失败的图"。
- **删掉的**：两站 `fetchLatest(page)`、`GalleryMerge` 的交错额度与 `RUN_PATTERN`、翻页 `exclude`、
  `hasMore/isLoadingMore/loadMoreError/round/shownKeys`、页尾「上滑加载更多」。
  日榜是一屏到底的固定池子，留着那套状态机必然产出「到底了还显示加载更多」——
  与搜索页刚修的是同一个病，这次从源头没有下一页。
- **新增的可见读数**：页尾一行「{日期} 的热门已全部显示（yande.re 20 · Danbooru 20，含 N 个视频）」，
  样式照搜索页 `ss-end`；卡片右下角 `▶ N″` 角标（不打标它就是一张看着正常、点开不会动的图）。
- **打乱锁种子**：种子存在 `GalleryViewModel`，组合重建后重跑 `mix` 得到同一序列；只有「刷新」换种子。
  裸 `shuffle()` 会变成"点进大图再返回整屏换序"（本项目导航条目会重建组合）。
- **新依赖**：`androidx.media3:media3-exoplayer` + `media3-ui` **1.11.1**（版本号现查 maven-metadata）。
  APK（universal）93,710,048 → 98,110,389 = **+4.20 MB**（11:34 那次出包是 97,459,067，差 651 KB 是 dex 重排的抖动，以最终包为准）。播放器按站点带 UA
  （Danbooru 只认非浏览器串；yande.re 走全局默认串），因为 media3 不过 `VeneraNetworkClient`
  也拿不到 `ImageHeaderPolicy`。
- 构建：`testDebugUnitTest` **178 条 0 失败**（`GalleryMergeTest` 重写 13 条、Danbooru 解析 7→11、
  新增 `GalleryFeedDateTest` 3 条）/ `assembleDebug` 绿。**真机未验**：视频能否播、
  yande.re 的 `webm` 那侧 UA 假设、移动网络下首帧等待、打码态视频画面是否真被糊。

## 2026-09-25 追加四：画廊「yande 只 13 张 / Danbooru 零张」的归因与判据修正

用户真机反馈两个数（13 / 0）并附一个油猴脚本怀疑站方藏图。**两个都不是我原先猜的网络问题**，
拉了设备的库与两站当天日榜回放才定位：

- 设备 `nsfw_mode = OFF`（不是分级隐藏）；但 `content_guard_rules` 里有 4 条启用规则，含**非正则关键字 `ai`**。
  `isComicBlocked` 的非正则分支是 `contains()`，画廊把整条 tag 列表喂进去 →
  `long_hair`(144) `hair_ornament`(49) `tail`(39) 全部命中。
  实测回放：yande **12/40** 被挡、Danbooru **199/200** 被挡；抽样期望上屏 14.1 / 0.1，与真机 13 / 0 对上。
  **两站请求都成功**，所以顶部来源提示为空 —— 这解释了"为什么没有任何报错"。
- 油猴脚本实测**零网络请求**，只是 `classList.remove` 放掉 HTML 列表里被 CSS 藏掉的条目；
  对应字段是 JSON 里的 `is_shown_in_index`（今天 40 条中有 2 条 `false`）。我们走 JSON 且**从不读该字段**
  → 那 2 张本来就上屏，客户端无事可做。`is_banned` 一类匿名响应里根本没有，站方没给就取不回。

**改了什么（用户拍板：只有画廊走整词，漫画侧一字不动）**：
新增 `GalleryBlockMatch`（tag 整串或 `: _ - 空格` 词元整词相等；正则用 `matches()` 整词；author 仍子串；
坏正则返回 false；`COMIC_ID` 不参与），经 `ContentGuardManager.findGalleryBlockedRule` 返回**命中的那条规则**，
一级与二级同源（`GalleryPostScreen` 一起换掉，避免"墙上被挡、点进来全裸"的分叉）。
预期：同一批规则下 yande 0/40、Danbooru 0/200 被挡 → **20 + 20 全上屏**。

**公开纠正我自己上一条判断**：第十三节我写的「真机未验②两站日榜是否都真给上内容」，
以及本轮我先入为主的"Danbooru 可能是 CF 按 IP 挑战/域名熔断"，都是**错的** ——
站侧探针（yande 40 条全 png/jpg、Danbooru 200 条字段齐全）与设备 HTTP 缓存里零 donmai 条目
（`Cache-Control: max-age=0, private, must-revalidate` 本就不可缓存，那条"证据"无效）已经足够否掉。
成因一直在我自己的判定链里。

**同时修掉一条假读数**：`GalleryFeedEnd` 原先统计守卫**之前**的 `vm.posts`，
所以只摆 13 张时它写「yande.re 20 · Danbooru 20」，而且某站为 0 时被 `mapNotNull` 整段省略 ——
唯一线索就这样被抹掉了。现在吃新的 `GalleryWall`：报**实际落屏**条数（含 0 照报）、
并按成因分开报「N 张命中屏蔽规则 {原文}」与「M 张按成人内容处理收起」；
全被挡完的空态把规则原文念出来。

构建：`testDebugUnitTest` **185 条 0 失败**（新增 `GalleryBlockMatchTest` 7 条）/
`assembleDebug` 绿，universal 包 98,110,389 → **98,118,361**。
**待真机复看**：画廊应显示 20 + 20，且页尾那行数得与屏上一致。

## 2026-09-25 追加五：画廊线恢复，连做四轮（工具条+InfoSheet / 满屏合并 / 独立 Activity / 搜索）

「先修下其他吧，图片先不理了」那条挂起**已被用户自己解除**（同日回来复看真机并继续提需求）。
本节只更正状态，细节全在两份文档里：

- `gallery-viewer-toolbar-and-infosheet-2026-09.md` §八~§十二：
  二级+三级合并成**一层满屏播放器**、Dock 一条（HD/下载/信息/分享）、`(i)` 拉 `GalleryInfoSheet`
  取代原来那两张滚动卡、大图页搬进**独立 Activity**（实时 blur-behind + 跨 activity 预测式返回 +
  进场**向上滑入**）、HD 撞 Cloudflare 改为"图片流量不弹过盾 + 失败如实报"、
  状态栏黑带改由本仓库第一份自有主题 `Theme.Venera.GlassOverlay` 处理。
- `gallery-search-2026-09.md`：**画廊搜索**（入口=顶栏右上角图标）。§〇 那张两站检索面实测表是这一节的根据：官方 autocomplete 路由两站都 404、
  前缀参数写错会被**静默给错数据**、Danbooru 匿名只有 2 枚标签预算、两站都没有总数端点、
  `[]`+200 是合法的"查无此标签"。
  **形态已被真机第二轮改判两次**：先拍成全高 `ModalBottomSheet` 并照那样落地，用户看过后改成
  **画廊页内的搜索头**（顶栏整条换成搜索头部、结果墙与日榜同一面、可退回原画廊），见其 §一 / §十。

状态账：`Navigation.kt` 与底栏枚举仍然一个字没动（搜索是画廊页内的一种模式，大图页是 Activity）；
`GalleryPostRoute` 这个目的地已在追加四之后删除。
构建：`testDebugUnitTest` **209 条 0 失败** / `assembleDebug` 绿。
**待真机验**的清单分别写在 `gallery-viewer-toolbar-and-infosheet-2026-09.md` §12.6 与
`gallery-search-2026-09.md` §十。

## 2026-09-25 追加六：真机二/三轮反馈三条（Danbooru 不出图 / 搜索改内嵌头 / 标签可交互）

三条反馈与处置都记在文档里，本节只登账与两处**公开纠正**：

1. **「Danbooru 搜索不出图」不是网络问题**：站方对匿名请求会把 `rating=e` 那批行的
   `file_url / large_file_url / preview_file_url / md5` **四支键整条抹掉**（实测 `tags=loli` 4 条全 `e`
   且四键全缺；yande.re 同查询键齐全）。结果侧现在按日榜那把同一判据 `GalleryMerge.isDisplayable`
   过滤 + **计数报出来** + 给一个**由用户点**的「排掉成人分级再搜一次」出口（不自动往查询里塞条件）。
2. **纠正我自己中途的判断**：我以为 `posts.json?search[tags]=…` 是官方给的解法（它返回带全 URL 的行），
   拿不存在的 tag 与 `id:` 复跑才发现它**根本不过滤** —— 是红鲱鱼，用它做检索会得到"搜什么都是一屏"，
   比空屏更坏。已写进 `gallery-search-2026-09.md` §〇 那张表防伪踩。
3. **纠正我自己写的 fixture**：`GalleryDanbooruParsingTest` 那条 fixture 的 `tag_string` 原本按
   "它只是 general 那一串"的想当然少写了两支。实测 60 条 / 五桶 2280 个 tag 串**没有一个**不在
   `tag_string` 里 —— 这条差别不是整理癖：它是 InfoSheet 那个「屏蔽」按钮**不是假开关**的前提
   （`TAG` 规则只拿 `post.tagList` 比）。已按实测校正并上单测钉住。
4. **大图页 `(i)` 里的标签现在可交互**：点击=回画廊搜这一枚（跨 Activity 走新增的一次性交接槽
   `GallerySearchHandoff`），长按=「搜索 / 复制 / 屏蔽」菜单，画法照漫画详情页 `DetailTagChip`。
   `GalleryInfoSheet` 里那句"chips 刻意不可点，因为 `/tag.json` 还没实测"的前提已作废。

状态账：`Navigation.kt`、底栏枚举仍然一个字没动。第一版那份全高 sheet
`GallerySearchSheet.kt` 按可逆清理路径移到 `build/_trash-from-repo/`（没有硬删）。
构建：`testDebugUnitTest` **210 条 0 失败** / `assembleDebug` 绿，增量 universal 包 101,825,633
（**增量数不可与干净基线相减**，口径见 `gallery-viewer-toolbar-and-infosheet-2026-09.md` §10.4）。
**待真机验**：`gallery-search-2026-09.md` §十三（12 条）、
`gallery-viewer-toolbar-and-infosheet-2026-09.md` §13.5（3 条）。


## 2026-09-25 追加七：画廊搜索区按 MD3 重做 —— 一次**可见的 token 层与基础组件契约扩张**

第四轮真机反馈：「现在的搜索页是一个单独的页面或占满了顶部空间…搜索按钮（淡紫色）对比度太低」，
并给了逐条 MD3 口径。形态从"替换标题栏的自绘头部 + Hero 模糊底"改成
**顶栏常驻 + 顶栏 `bottomContent` 里的内联展开搜索区**；细节全在 `gallery-search-2026-09.md` §十四/§十五。

本节单列一条：**这一轮动了 token 层与 `VeneraChip`**，属于组件契约变更，必须在这里看得见 ——

- `ui/tokens/Color.kt`：`VeneraColorTokens` 增 **3 个语义槽位**
  `surfaceContainerHigh` / `secondaryContainer` / `onSecondaryContainer`。
  取值只在 `buildVeneraColorTokens(m = MaterialTheme.colorScheme)` 一处填，
  两套主题都桥得上（`feature/ThemeColorBridge.kt` 的 `toMiuixColors` / `toMaterialColors`
  **双向都已映射** `surfaceContainerHigh` 与 `secondaryContainer`，所以 MIUIX 模式下不会拿到
  MD3 默认紫灰）；页面一律走槽位，没有一处越层直接读 `MaterialTheme.colorScheme`。
- `ui/tokens/VeneraTokens.kt`：`VeneraElevationTokens` 增 `attached = 3.dp`（MD3 elevation3，
  给"贴附在内容之上的输入面"）。
- `ui/tokens/Spacing.kt`：增 `dockedSearchBarHeight = 56.dp`（MD3 docked search bar 原值）。
  与既有 `searchFieldHeight = 48.dp` **分档**：那枚是表单里的单行输入框，这枚是搜索条本体，
  合成一个数会让某一侧的观感不对。
- `components/venera/VeneraChip.kt`：**加性**扩张 —— `VeneraChipVariant` 增 `Filter`
  （未选 `outline` 描边 / 选中 `secondaryContainer` 填充，MD3 filter chip 那一档）与 `Neutral`
  （无描边、填 `surfaceContainerHigh`）；新增可选参数 `leadingText`（chip 里的次要前缀，
  小一档 + tertiary，用于历史 chip 的站点名）。**默认值与既有分支一字未改**，
  仓内其余调用点行为不变。之所以扩展它而不是新写一枚 chip：组件自己的 KDoc 明写
  "这是唯一的 Chip 实现，禁止再复制一套 UI"。

三条拍板（AskUserQuestion）：Hero 模糊底**去掉**；收起入口=搜索框右侧 X **兼任**
（有字先清空、空了再点收起）；源切换**仍单选**。

一处**没有照抄用户清单**：上一版头部右上角的 ✕ 动作是「清空全部条件」，这一轮那个位置被输入框的
清空/收起占了 —— 动作搬进 chips 行末尾一枚「清空」Assist chip。**换地方可以，丢掉不行**
（yande.re 不限标签数，只能一枚一枚长按删是惩罚用户）。

一处**刻意收掉**的东西：搜索态下顶栏不再摆「换一批」。它只作用于日榜那片池子，
搜索结果在屏时按它看不见任何效果 = 假按钮。

构建：`testDebugUnitTest` **210 条 0 失败** / `assembleDebug` 绿。
`gallery/ui/GallerySearchHeader.kt`（第二轮那版）按可逆清理路径移到
`build/_trash-from-repo/GallerySearchHeader.kt.replaced`。
**待真机验** 10 条见 `gallery-search-2026-09.md` §十五（重点：网格被"平滑推下去"而不是跳、
提交钮可点/不可点两态的对比度、历史点击只填框不自动搜、`imePadding` 换了所在层之后的键盘避让）。

## 2026-09-25 追加八：画廊搜索第五轮 —— 胶囊进框 + 同步搜索，`VeneraChip` 第二次加性扩张

用户拿三张真机截图判「效果有点差」，并给了新口径：
**「不要这样搜索的方法，弹出预测词选中后直接在搜索框内用胶囊显示标签，同步搜索」**。
细节全在 `gallery-search-2026-09.md` §十六。**先量再改**：搜索区实测 **≈201dp**（约屏幕 1/4）、
顶栏玻璃带与搜索区不透明底之间那道硬边切在 **y≈268px≈97dp**、一屏 **两枚 ✕** 职责还不同。

四条改判（AskUserQuestion 全选推荐项）：去掉提交钮（加/删标签即搜）；胶囊自带 × + 退格删最后一枚；
收起只归顶栏 ✕（框里那枚只管往回退）；历史改单行横滑 + 提示行只在必要时。
另外**撤掉搜索区自己的不透明底**，让顶栏那层 progressive blur 一路延续 —— 硬边的根因是同一块面板涂了两种材质。

**本节单列一条必须看见的：基础组件契约第二次扩张。**

- `components/venera/VeneraChip.kt` 新增可选参数 **`onRemoveClick`** —— 给 `trailingIcon` 单独一个动作。
  成因：这一形态下**整枚胶囊的点击被"改成排除"占了**，"删掉这枚"只能挂在 × 那一小块上；
  否则要么两个动作打架，要么画一个按不动的 ×（假开关）。× 的触达位往外扩一档
  （`chipIconSize` 原尺寸零内边距在真机上基本按不准，会连整枚点击一起误触发）。
  默认 `null` 时行为与之前**一字不差**，仓内其余调用点不受影响。
- 同文件 `Row` 的横向间隙在带 `leadingText` 时收一档（`space2 → space1`）：
  那两段文字是一体的（站点名 + 关键词），留出"两个独立元素"的间距会把一枚 chip 读成两枚。

**推翻上一轮自己的一颗钮**：追加七 刚按用户口径把提交钮做成 MD3 Filled Button，第五轮该口径被覆盖
—— 那颗钮整颗去掉，提交由状态变化自己完成（防抖 250ms，且第 1 页**可打断**在跑的那一笔，
否则连选两枚标签时屏上留下上一串的结果，看着就是"点了没反应"）。

**删掉一处零调用点**：`GallerySearchViewModel.clearConditions`（「清空」chip 随条件行一起消失，
每枚胶囊自带 × 之后它不再值一行高度）。

**数据面收窄一条**：补全上限 `SUGGEST_ROWS` 8 → 6，且候选行从"套一枚 VeneraCard（≈56dp）"
改成单行紧凑项（≈32dp）—— 8 行卡片会把这块内联区顶到半屏以上。

保护域仍然一个字没动：`Navigation.kt`、底栏枚举。
构建：`testDebugUnitTest` **210 条 0 失败** / `assembleDebug` 绿，并已在包内 dex 逐串复核新文案
（「删掉最后一枚标签」「点标签改成排除」「最近搜索」）。
**待真机验** 10 条见 `gallery-search-2026-09.md` §16.4。
