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

---

## 2026-09-28 追加：换掉分段控制器的形态（动了一个 FROZEN 文件的注释）

用户点名把分段选择器改成 [pixez-miuix](https://github.com/137458/pixez-miuix) 动态页那排
「全部 / 公开 / 私密」的效果。改的是**共享组件** `components/venera/VeneraSegmentedButton.kt`，
所以四个调用点同时换形态：画廊两页切换、收藏页一级三枚（网络/图片/本地）、
图片收藏二级两枚（漫画/画廊）、搜索卡里的站切换。用户口径：**一次改到位**，
不要同一屏出现两种分段器。

### 为什么换

旧形态是 MD3 的"药丸中的药丸"：整条大药丸描一根 0.5dp `outlineVariant` hairline，里面一颗实心块滑动。
这个结构在**实时模糊的玻璃顶栏**上不成立 —— 容器描边贴在亮画作上时几乎看不见，
整条读起来像一个断掉的框（真机截图两处都这样：画廊两页切换、图片收藏那一行）。
每颗自己带底之后不再依赖描边，背后是什么内容都读得清。

### 保留的既有口径（历轮真机反馈定的，一条没动）

- 高度档 `segmentedHeight` 48dp / 宽屏 `segmentedHeightWide` 56dp，字号宽屏升 `itemTitle`；
- 单元间隙 `segmentedGap` = space5（10dp，px/dp 对账那段历史仍成立，只是不再兼任分隔线居中量）；
- **整条宽度仍由调用方按"单段占屏宽 25%"推导**（用户「太宽太散」那条），组件不自己定宽；
- 选中仍是实心 `primary` + `onPrimary`（动态色板驱动，不写死色值）。

新增的只有：未选中档 = `surfaceVariant` 叠现成的 `selectedSurfaceAlpha`（0.5，VeneraChip 禁用态同档）。
刻意半透明而非实底 —— 玻璃层上涂不透明底会拉出一条横贯硬边（本文件既有条目）。
旧那颗滑动的果冻块依附容器才成立，随容器一起取消，换成每颗自己的底色淡变 + 0.96 弹性缩放，
阻尼仍取 0.7 那一族。

### 冻结面

`FavoritesScreen.kt`（2026-09-18 冻结）被改，**只改了一行注释**（`FavoritesModeToggle` 头那行
还在描述已不存在的"药丸中的药丸"）。页面逻辑、状态机、几何常量、调用参数一字未动。
`Spacing.kt` 里 `segmentedGap` 的文档同步改了措辞（值不变）。

构建：`testDebugUnitTest` **265 条 0 失败** / `assembleDebug` 绿。
**待真机验**：三档主题（MD3 / MIUIX / 深色）下未选中那几颗的对比度，以及贴在亮画作上是否仍读得出"这是一排可点的段"。

## 2026-09-28 追加二：画廊标签三项（分桶画师栏位 / 标签汉化词典 / 点标签返回逐级回退）

方案与实测底账在 `gallery-tag-category-translation-and-nav-2026-09.md`（§〇 底账、§一~§三 设计、
§七 落地记录，含 §7.4 那份**探针误判与更正**）。这一节只记冻结面。

### 保护域

`Navigation.kt`、底栏枚举、`currentTab` / 主 Tab 横滑**一个字没动**。
第 2 项评估过"搜索另开一页"（用户提的），结论是不加 `GallerySearchRoute` ——
要修的"返回回到上一轮"靠 `GallerySearchViewModel` 里的上下文栈就够，
理由与那三条硬墙记在 `gallery-search-2026-09.md` §不变结论那一段（2026-09-28 补注）。
改动全在 `gallery/` 包 + 两份 assets + 一个构建脚本。

### 新增的资产与许可（体积账要认）

- `app/src/main/assets/gallery_tags_79415.sqlite` —— ffdkj Danbooru 汉英词典表（**MIT**，
  `post_count≥100` 或本身是中文/画师档的那 79,415 行），落盘 3.26 MB、进包 1.74 MB（Deflate 47%）。
  文件名带行数 = 版本：换表就是换文件名，运行期按"文件名里的行数取最新"挑，旧的副本自然失效。
- `app/src/main/assets/licenses/ehtagtranslation-LICENSE.md`（CC BY-NC-SA 3.0 中国大陆）、
  `.../ffdkj-danbooru-tags-LICENSE.txt` —— `tags.json` 一直在包里而许可**从没登记过**，这笔漏记与
  选不选它无关，一起补了。
- `scripts/build_tag_dictionaries.mjs` —— 两份词典的**构建期出处**（线上库 → assets），
  头注写死"`tags_tw.json` 刻意不刷新"（EhTag 的仓库只有简体）。
- `assets/tags.json` 34,956 → **44,344** 条（刷新线上库）。⚠️ 这条会同时改变漫画侧：
  题材统计命中的键变多。下一轮如果有人报"题材统计数字变了"，先想到这里。

### 归一模型的一处删减（有意，不是遗漏）

`GalleryPost.tagGroups` 这个字段**删了**，`galleryTagGroup()` 那个 helper 一起删。
理由：两站 post 端点都不给分类（实测 yande.re 44 个键无分类字段、Gelbooru 只有一串平铺 tags），
留着它就只有两种写法 —— 解析期硬造假分组，或永远填一桶「标签」让下一个人以为分类数据不存在。
分类的真出处在那张帖的 HTML（站方给每枚标签标了 `tag-type-*` 类名），改由
`GalleryTagCategories` 在「关于这张图」打开时另取一笔、渲染期 `buildGalleryTagBuckets(...)` 分桶。
降级口径守住"宁可少摆不可摆错"：认不出类名 → 交 null → 只用词典兜「画师」一栏，
其余一律进「标签」而**不是**「通用」。

### 搜索返回的语义变更（本轮唯一的行为改判）

"换一轮搜索上下文"从**整片覆盖**改为**压栈**，返回逐级回退（wowoguni → touhou → 每日推荐）；
三条入口（大图页点标签 / 点历史 / 点推荐标签行）收敛到 `openContext()` 一个落点。
`closeSearch()` 与顶栏 ✕ 的既有拍板没动（chips 与结果不清、✕ 只关一层不清栈）。
配套把 `runSearch` 落地那道闸从"只比站点"改成 `isStaleContext(轮次, 站, …)` ——
**认轮次也认站点**，站点那一半留着是因为顶栏换站不转轮次。
判据全在新增的 `gallery/domain/GallerySearchContext.kt`（9 条单测锁），
ViewModel 只做抄字段那层薄接线。

构建：`testDebugUnitTest` **307 条 0 失败** / `compileDebugKotlin` 绿 / `assembleDebug` 绿。
**待真机验**：第 1 项的画师行与分桶、第 3 项的「译名 (原词)」双显示，以及第 2 项那一串逐级返回
（完整清单在方案文档 §六 与 §7.4.3；设备由用户操作，我只读截图与 logcat）。

## 2026-09-29 追加：画廊搜索的时间窗口排行（六档 + Gelbooru 定界器）

> ⚠️ **这一节已被同日「第二轮」那一节取代**（Gelbooru 撤档、新增月/年选期）。
> 留着不删是历史，但里面的"六档""326 条""定界器"都不是当前形状，别照它读代码。

方案、实测底账与落地记录都在 `gallery-ranking-windows-2026-09.md`（§〇 五批探针、
§0.4 记着我自己的三处探针设计错误、§十 偏离与 QA）。这一节只记冻结面与行为改判。

### 保护域

`Navigation.kt`、底栏枚举、`currentTab` / 主 Tab 横滑**照旧一个字没动**。
改动全在 `gallery/` 包内（新增 2 个域文件 + 1 个 data 文件 + 3 处接线）。

### 顶栏 chrome：一行都没加

排行控件挂在**已有的**胶囊行末尾（`VeneraChip` 的 `Filter` 变体，探索页快捷筛选同一件），
点开是 `DropdownMenu`（收藏页 `FavoritesSortMenu` 同式）。
玻璃那三条既有硬约束原样适用：不涂不透明底板、避让只用常量、`bottomContent` 那格不加第三支。
下滑后的**吸附胶囊层刻意不加**这一颗 —— 那一行宽度封在屏宽 34% 内、与居中标题抢位。

### 两条行为改判（用户 2026-09-29 拍板）

1. **`GallerySearchContext` 快照多一个 `ranking` 字段**：弹栈要连档级一起带回。
   上一轮那条"快照只存搜什么"的口径因此扩了一点 —— 不扩就会出现
   「按周排行看着 → 点标签 → 返回 → 屏上还是那批图而档变回默认」那种读不出原因的错位。
2. **历史仍只存用户那一排条件**（`visibleQuery`），排序与窗口伪标签**不进历史、不进胶囊**。
   页尾与空态改念"发出去的是哪一串 + 当前档的日期区间"，两串各有一条单测锁着。

### 一处刻意的"能做但先不装"

`GelbooruClient.stampBefore` 依赖 **DAPI 认 `id:`** 这个前提，而它本机验不了
（匿名一律 401，§0.2 的证据来自站内 HTML）。功能照用户要求本轮就上，但失败必须有出口：
定不出边界就**不发那一笔**，屏上是「这一档没开起来」的解释空态，
绝不"当没设窗口"发一次全站查询。真机第一次点 Gelbooru 时间档就是这一前提的判决（方案 §八.6）。

构建：`testDebugUnitTest` **326 条 0 失败** / `compileDebugKotlin` 绿 / `assembleDebug` 绿。
**待真机验**：方案 §八 那 9 条（含第 1 项画师栏位、第 3 项标签汉化那两条仍未验的）。

## 2026-09-29 追加（第二轮）：Gelbooru 时间档撤下 + 月/年可选具体一期

同一份方案的第二轮（真机读数之后），细节在 `gallery-ranking-windows-2026-09.md` §十一、§十二。

### 保护域

`Navigation.kt`、底栏枚举、`currentTab` / 主 Tab 横滑**照旧一个字没动**。
排行与选期都挂在**已有的**那一排胶囊末尾，日期弹层是模态的、不占内容高度 ——
玻璃顶栏那三条硬约束（不涂不透明底、`bottomContent` 不加第三支、避让只用常量）原样适用。

### 行为改判（三条，全部用户当场拍板）

1. **Gelbooru 只留「默认」与「全部排行」**。天/周/月/年四行在下拉里留着但点不动，写「本站没有」。
   判据一把在 `GalleryRankings.supports(site, ranking)`，菜单画行 / `setRanking` / 换站三处共用 ——
   出现"点得动、发出去是全站结果"的那种假开关，就是因为这类判据散成多把。
2. **月/年档可以选具体哪一期**（yande.re 原生 `date:A..B`，任意历史期都是一笔请求的成本）。
   入口是同一个下拉的第七行 → 日期弹层（粒度两枚 + 日历，年份范围 2007..今年）。
   `GallerySearchContext` 快照再加一项 `periodAnchor`：翻到 2024-03 之后点一枚标签再返回，
   屏上若还是那批三年前的图而读数写着本期，就是上一轮 `ranking` 那条错位的重演。
3. **每日推荐的空态不再默认甩锅屏蔽规则**。原来只要墙空着就写「这一屏被你的规则挡完了」，
   池子本来就空的时候还会补一句"全被判为成人内容"——那是凭空编的成因。
   现在按实际张数说：`blockedCount > 0` 才提规则、`hiddenByRating > 0` 才提分级，
   两个都是 0 就说「站方这一轮没有回内容」。

### 一处公开纠正（影响过决策，必须留字）

撤档时我在代码注释里写过"DAPI 真机读数不认 `id:>=`"—— **那句是我替用户补的，不是他的读数**。
他的原话是"周和全部正常，月年不行"，即 DAPI **认** `id:>=`，机制是通的；
月/年失败是我把定界器的探测预算定在 24 次（那站约 1.2 万 id/天，月档跨 ~36 万 id，
拓界 6 + 二分 22 ≈ 28 次才收口）。撤下的代码镜像在
`_trash/gallery-gelbooru-boundary-2026-09-29/`（含 README 写明恢复只需改那一个常数）。
用户是在"以为整条机制死了"的前提下面选的撤档 —— 这一条下次拍板前必须先说清。

### 仍未真机验的

日期弹层（M3 `DatePicker` 第一次进本项目）的**观感**没验：弹层高度在小屏上是否要滚、
粒度那两枚芯片与日历的间距、以及"点任意一天=取它所在那一期"这条口径读不读得懂。
构建：`testDebugUnitTest` **325 条 0 失败 0 错误**（45 套件）/ `compileDebugKotlin` 绿 /
`assembleDebug` 绿，universal 103,669,063 B（无新资产，尺寸与上一轮同一字节数）。

## 2026-09-29 追加（第三轮）：主页面内容区横滑切 tab 整体撤下（保护域豁免）

用户原话：「取消所有主页面的任意位置左右滑动切换页面导航栏的，但是要保留导航栏的左右滑动」。
**这是对 `Navigation.kt` 保护域的显式授权变更**，不是顺手改 —— 记录在此。

### 撤了什么

- `feature/TabSwipePager.kt` 整个文件（`tabSwipePager` 修饰符 + `TabSwipeExclusionRegistry`
  + `LocalTabSwipeExclusions` + `tabSwipeExcluded`）→ 镜像到 `_trash/tab-swipe-pager-2026-09-29/`。
- `Navigation.kt` 里 NavHost 上唯一那一处 `.tabSwipePager(...)` 调用，以及只为它存在的
  `swipeExclusions` 登记表与外层 `CompositionLocalProvider`、`navigationInsets` 局部量。
- `HomeScreen.kt` 首页推荐轮播那一块的 `Modifier.tabSwipeExcluded()`（登记方没了，让位带也无意义）。
- 两处**注释**里"枚举顺序就是左右横滑翻页顺序"的口径改写（`VeneraFloatingNavBar.kt`、
  `FavoritesScreen.kt`）—— 留着会指到一个已经不存在的实现。

### 留了什么（一条都没动）

- **底栏自己那条拖动**：`VeneraLiquidGlassNavBar` 的 `DampedDragAnimation` → `onTabSelected` → `gotoTab`。
  它与刚撤掉的那条本来就是两套实现（旧的还专门写了"起手点在底栏区域内就不接管"来避开它）。
- `VeneraNavTab` **枚举顺序、路由映射表、顶栏齿轮入口**：一个字没动（冻结面原样有效）。
- 所有**页内**pager：收藏三段、画廊日榜/推荐两页、大图页左右翻、阅读器 LTR/RTL/双页、首页推荐轮播。

### 同轮另外两条（不涉保护域）

- 排行菜单的状态从芯片里提到 `GallerySearchArea`：修的是"展开态点排行，菜单闪一下就没了"
  （组合位置一变 = 新实例 = `remember` 归零），方案 §13.1。
- 「关于这张图」改两列：画师/角色/作品与尺寸卡并排，`ArtistRow` 删除，方案 §13.2。

构建：`testDebugUnitTest` **325 条 0 失败 0 错误** / `compileDebugKotlin` 绿 / `assembleDebug` 绿。

## 2026-09-29 追加（第四轮）：切 Tab 方向化滑入（保护域第二次豁免）+ 画廊设置分区

细节在 `gallery-round4-and-settings-2026-09.md`。用户一次回 8 条，其中两条落在保护域或冻结面上。

### 保护域第二次点名豁免：`Navigation.kt` 的转场方向

用户原话：「切换页面的时候水平平滑滑动效果：如果从"首页"切到"收藏"，页面从右侧滑入；
从"收藏"切回"首页"，页面从左侧滑出。Tab 顺序与滑动方向一一对应」。

改的只有 `NavHost` 的 `enterTransition` / `exitTransition` 两行 → 各加一个分支：
两端**都是主 Tab** 时走整页 `slideIn/OutHorizontally`（方向 = 目标序号 − 起始序号），
其余目的地仍走官方 shared axis X（列表→详情是"同一本书换个容器"，封面在按自己的曲线飞）。

- `VeneraNavTab` 枚举顺序、路由映射表、底栏拖动：**照旧一个字没动**。
- `popEnter/popExit` 与预测式返回那四个 lambda：没动。
- 与第三轮那条不冲突：第三轮撤的是**手势**，这一轮加的是**动画**。

### 冻结文件的外观改动（走"记豁免"那条路）

- `feature/settings/SettingsComponents.kt`：`SettingsGroup` 的标题那一段抽成
  `SettingsGroupTitle`（样式仍只有一份实现）。为的是画廊设置里"账号与密钥"那一组摆的
  两张卡本身已是 `VeneraCard`，再套一层 Card 就是卡里嵌卡。
- `feature/settings/AppSettings.kt`：`DirChoice` + `evaluatePickedDir` 从 `private` 提 `internal`，
  画廊的「下载目录」复用那三道关，而不是抄第二份（漂的那一半通常是被抄的那份）。

### 上一轮那条修法的另一半要补一句

第三轮把 `rankingMenuOpen` / `periodPickerOpen` 提到两种形态之上，堵的是
"组合位置一变 = 新实例 = `remember` 归零"。真机第四轮反馈"还是直接消失"——
**另一半从来不是状态归属**：弹层抢焦点导致 IME 下落 → 那道"键盘收起就收成一条"的 effect
把整块搜索区移出组合 → **菜单的锚点没了**。本轮把判据抽成 `GallerySearchCollapse` 并钉了用例。

构建：`testDebugUnitTest` **48 套 / 344 条 / 0 失败 / 0 错误** / `compileDebugKotlin` 绿 / `assembleDebug` 绿。
新增依赖 `io.coil-kt.coil3:coil-gif:3.6.2`（只挂画廊那把 ImageLoader，漫画侧不动）。

## 2026-09-29 追加（第五轮）：图片取流改道（全应用面）+ Gelbooru 排行只列两档

用户指令：「Gelbooru 排行只留『默认』与『全部排行』，其他删掉，另外 gif 同屏加载过多会闪退，
检查下，处理好了直接推进 批次B」。（批次 B 本会话内已落完，见上一节，所以这条不产生新工作。）

### 撤掉的是"行"，不是判据

`RankingChip` 里 Gelbooru 那四档时间窗从"置灰 + 写'本站没有'"改成**不列出**（第四轮我为那条
置灰写法在文档里留过理由，当晚被否：四行点不动比少四行更烦）。
`GalleryRankings.supports` 一个字没改；末行「选具体哪一期」新增按站点收口
（`supportsPeriodPicker(site) = supports(site, MONTH)`，定义挂在原判据上，不会漂成某站多一个假入口）。

### 图片取流：一次动了**漫画与画廊两条链**的改道

真机 OOM 读数（adb 只读）：`target footprint 268435456 / growth limit 268435456`、
`<1% of heap free after GC`，而打开 `GalleryPostActivity` 那一刻只剩 52 MB ——
堆是被**墙上的编码字节**吃光的，栈顶那个 104 字节的 Compose 分配只是受害者。

- 旧状：`VeneraImageFetcher` 接管所有 http 图片，一张图在 Java 堆里留三份编码字节
  （`body.bytes()` + `Buffer().write` + 动图解码器 `squashToDirectByteBuffer` 那份直连缓冲），
  并且它用**同步** `execute()`，绕开 OkHttp `Dispatcher` 的每主机并发闸。
- 改后：只有**真的需要改字节**的两条路（JM 去混淆 / EH 雪碧图裁剪）还归它，判据抽成
  `ImagePipelinePolicy.needsBytePipeline(url)`；其余图片交回 Coil 的 `OkHttpNetworkFetcherFactory`
  —— 流式写盘、文件源解码（可降采样）、异步调用（并发有上限）。
- 接管条件一收窄，`ImageFetchTag` 就没人打了（Coil 自己造的 Request 不带 tag，`extras` 不映射成
  tag），所以新增 `ImageFetchCallFactory` 这一层专门补标记。**没有**把图片放回熔断/过盾口径。
- 与冻结声明早前那条「`VeneraApp.newImageLoader()` 那一行未动」（画廊初建轮的记录）的关系：
  本轮**动了它的 components**，但**预算仍然一个字没改**（漫画侧照吃 Coil 默认值）。
  动的只有：自定义 fetcher 的接管范围、以及那行 `callFactory` 换成带标记的包装。
- 顺带一条 `wallUrl` 的自身缺陷：批次 B 那枚「清晰预览」把 `largeUrl` 搬上了墙，
  而视频条目两站都不给更小的转码档（Gelbooru 的 `sample_url` 空串、翻译时兜底成原片 16~26 MB）
  → 现补 `post.isVideo` 一律走 `videoPosterUrl`（静帧），与大图页底图同一把判据。

### 公开纠正一条影响过验收的假开关

批次 B 交付时写的「独立目录 + 独立预算 / 缓存上限档位」**当时只成立一半**：
`GalleryImageLoader` 那把 `DiskCache` 没有任何路径往里写（写盘是 `NetworkFetcher` 的活，
而图片全被自定义 fetcher 截在前面），所以"当前占用"恒 0、档位恒无效果。本轮改道后才第一次真生效。

构建：`testDebugUnitTest` **49 套 / 356 条 / 0 失败 / 0 错误** / `assembleDebug` 绿。
详见 `gallery-round4-and-settings-2026-09.md` §五。

## 2026-09-29 追加（批次 C1）：大图页四条行为档位

用户「批次c」→ 四条拍板（背景不做取色 / 动图三档只管动图 / AI 那把画廊与漫画分开 / 分两批先做低风险的）
→「同意，按 C1 方案开工」。方案文档：`gallery-batch-c-viewer-2026-09.md`（含 C2 已定口径）。

新增设置组「大图页」（`GallerySettings.kt:191` 起）+ 四条偏好（`VeneraPreferences.kt:177-227`）：
屏幕常亮（默认开）、音量键翻页（**默认关**）、自动连播间隔（默认 0=关）、智能预加载（默认 NEXT=今天的实际行为）。

- **没有动**：冻结文件、保护域（`Navigation.kt` 与底栏枚举）、`GalleryImageLoader` 的预算、依赖表。
- 判据全抽进 `gallery/domain/GalleryViewerPolicies.kt`（12 条用例），composable 里一律不自己算。
- 音量键默认关的理由要说清：阅读器那把默认开，但画廊这页在**透明玻璃窗 Activity** 里、
  根节点此前没有任何 focus 件 —— 默认开等于把系统音量键抢过来走一条没在真机验过的链。
  这条是 C1 里唯一"照抄了但环境不同"的，真机待验第一位。
- 预加载复用 `galleryLargeRequest`（带着 memory/disk 双 cacheKey，视频换 `poster` 键），
  不另造 `ImageRequest`：两个键 = 白下一遍。
- `GalleryPreloadMode.OFF` 的 summary 明写"**不等于没有流量**"（关的是主动预取与邻居预组合，
  当前页自己的三档照旧发）—— 不写这一句它就是假开关。
- 常亮那条没有算式可抽 → **没有单测点**，这条如实记在方案 §一.1，不含糊过去。

构建：`testDebugUnitTest` **50 套 / 368 条 / 0 失败 / 0 错误** / `assembleDebug` 绿。

## 2026-09-29 追加（C1 真机第六轮）：一条恒假守卫 = 三条读数

用户带截图回：连播翻页停在半页、底栏要能控连播、两枚加载图标 + 下拉环转不停、
"猜你喜欢他只摆了 40 张"。

- **一条根因**：`withContext(NonCancellable)` 把 `coroutineContext[Job]` 换成 `NonCancellable` 本身，
  于是 `if (loadJob === coroutineContext[Job]) isLoading = false` **恒不成立** →
  在途标志永不清零 → 环转不停 + 页尾第二枚环 + `loadMore()` 那道闸永远进不去（40 = 两站各 20）。
  库行为钉在 `NonCancellableJobIdentityTest`；修法是在进块之前把自身 Job 抓成局部量。
  同形写法在 `GallerySearchViewModel` 里没这个毛病（裸 `finally`），所以只有猜你喜欢停住 —— 这个对照是定位关键。
- **另一条独立成因**（我上一轮引入的）：连播那个 `LaunchedEffect` 的 key 含 `currentPage`，
  动画滚过 50% 时 key 变 → effect 重启 → **把自己发起的 `animateScrollToPage` 取消了** → pager 冻在半页。
  改成"计时归 effect、动画归页面 scope"。
- `isRefreshing` 原先绑两页的并集（`onRefresh` 却只派发当前页）→ 按当前页绑。
- 底栏加第 6 颗 `▶/⏸`（点=开关、长按=dock 上方展开 1~15 秒滑条）。
  **速度只管本次这一屏**（用户拍板）：不写回 `pref_gallery_autoplay_sec`，退出即回到设置值。
- "无限滑动"没做成新需求：翻页链路本来就在，卡住的就是那个永真标志；真到底时页尾照旧如实念"已经到底"。

构建：`testDebugUnitTest` **51 套 / 369 条 / 0 失败 / 0 错误** / `assembleDebug` 绿。
详见 `gallery-batch-c-viewer-2026-09.md` §六。

## 2026-09-29 追加（批次 C2）：动图三档 / 背景四档 / 画廊侧 AI 两枚

用户「做好后接着做 c2」。四条口径全部沿用 §〇 的拍板，没有重开问题。
判据仍在 `gallery/domain`（`GalleryMotion` / `GalleryAi` / `GalleryViewerBackdrop`，12 条用例）。

三处**动了以往冻结面**的事实，单独列出来，别被"只是加设置项"这个印象盖过去：

- **manifest 新增 `ACCESS_NETWORK_STATE`**（普通权限、不弹运行时窗）：`ConnectivityManager` 全仓此前
  零使用，"仅 Wi-Fi 那一档"没有现成读数可借。拿不到读数一律按**计费**处理（宁可不动，不偷跑流量）。
- **`GalleryImageLoader` 的解码链换人**：不再裸注册 `AnimatedImageDecoder.Factory()`，
  链上坐的是 `GalleryAnimationGate`（请求没表态就返回 `null`）。这条是"从不"能成立的前提 ——
  若闸门在 `never` 档改派一个静态解码器，两个内置静态解码器对动图会**双双拒绝**，
  请求顺链仍会落到动图解码器上，"从不"当场变成假开关。
- **动/静只分内存键不分磁盘键**（`large` ↔ `large-a`、`file` ↔ `file-a`）：磁盘那份是编码字节，
  分两份就是白占一倍 512 MB 预算；内存那份是解出来的 `AnimatedImage` 或位图，
  共用一条键就会出"从『始终』改到『从不』之后那张图还在动"。

其余口径：

- 墙上卡片**恒静帧**，与设置里那三档无关（一屏动图同时解动画就是当天那条 OOM 读数的形状）；
  视频照旧点击才播，`animated` 对视频条目一律不表态。
- 网络读数**每屏只问一次**（`remember`）：逐张问的后果是同一面墙里忽动忽静。
- 背景四档**没有取色那一档**（2026-09 做过一次被真机否）；深灰取全仓唯一现成口径 `0xFF121212`。
  三档不透明底靠"盖住"模糊、不靠运行时改 `FLAG_BLUR_BEHIND`（那两个覆写项是 Activity 起来时读一次的），
  代价如实写进设置页文案：**改回"现状"要重进大图页才恢复模糊**。
- AI：**分家的是开关，不是词表**（`AiTagKeys` 改 `internal`，画廊侧以它为准**只收窄**）。
  命中的 AI 条目按"命中一条屏蔽规则"记账，不新增第三个计数器；角标复用既有 `GalleryCornerPill`。
  四面墙（日榜/搜索/推荐/收藏）同一把 `buildGalleryWall`，`blockAi` 进 `remember` 键。
- **撤回一条本轮自己记错的读数**：`GalleryAi` 头注原先写"实测 yande.re 只有 `ai-generated`（83 条）"，
  收尾复探针**复现不出来** —— yande.re 上 `ai-generated` / `ai_generated` / `generated_by_ai` / `ai_drawn`
  四种写法现在**全是 0 条**（不带 tags 的对照请求正常返回数据）。而那一站的裸标签 `ai` 有 19 条，
  标签串是 `ai maid ... suzuhira_hiro tick_tack` = **角色名**（《Artery Gear》的 AI）。
  所以画廊侧判据**剔掉裸 `ai`**（漫画侧保留，EH 那系要用），钉在两条断言上。
  Gelbooru 两种写法各 9 张卡片、阴性对照 0 张 → `_`↔`-` 归一保留。
  完整读数表在方案 §七。

构建：`testDebugUnitTest` **52 套 / 381 条 / 0 失败 / 0 错误** / `assembleDebug` 绿。
过程中一次先红后绿：`buildGalleryWall` 的新参数加在函数类型参数**后面**，
四处尾随 lambda 因此不再绑到 `blockedRuleOf`（尾随 lambda 只能绑最后一个参数）→ 四个调用点全炸，改放到前面。
C2 四条**没有一条在真机上跑过**，待验清单见方案 §七末尾（其中"墙上恒静帧"与"改档后那张图立刻静下来"
是这一批里最可能做错的两句）。


## 2026-09-29 追加（批次 D · B1~B3）：外观第三轴 `SurfaceMaterial` + 设置域控件收口

用户两句诉求叠在一起：「让整个项目的 UI 完全符合 Miuix 设计规范」+「优化界面性能，目前一卡一卡的」。
方案档 `miuix-glass-surface-material-2026-09.md`，批准稿在 `~/.qoder-cn/plans/humble-river-moose.md`。
本轮深度按拍板只做 B1~B3，B4~B6 挂账；FROZEN 名单一个都没碰（用户明确不给豁免）。

**这是组件契约变更，必须看得见**：

- 新增第三轴 `SurfaceMaterial { SOLID, LIQUID_GLASS }`（键 `pref_surface_material`，**默认 SOLID**）。
  判据抽成零 Android 依赖的纯函数 `ui/tokens/SurfaceMaterialPolicy.kt`（本项目无 Robolectric，不是纯函数就测不到），
  新单测 8 条。`LocalSurfaceMaterial` 用 `compositionLocalOf`，**没有**给 `VeneraTokenSet` 加字段——
  加了就必须同步改 `VeneraTheme.kt` 的 `remember(appearance)` 键，漏改正是 token 注释警告过的"切档留旧值"。
- **顺手清掉一处悬空 Local**：`LocalAppearanceStyle` 全仓零读取点，已删除（不留没读者的 Local）。
- 全站玻璃唯一修饰符 `components/venera/VeneraGlass.kt`；唯一采样源装在 `VeneraAmbientBackground`
  那张静态 Canvas 上（**兄弟节点、不采内容层** ⇒ 不递归、滚动不重捕）。
  `Navigation.kt` 与 `VeneraSubActivityBase.kt` 两个调用点签名不变 ⇒ **未获也未需要保护域豁免**。
  该文件自称的"零离屏纹理"性质只在 LIQUID_GLASS 档失去，录制器条件安装，实色档一份都不建。
- **`VeneraChip` / `VeneraFilterPill` 的判据从"全贴"改成"该透的才透"**：选中态与禁用态**不上玻璃**
  （它们的语义就靠实底板表达），`Tag` 降为点缀档（只染色不建模糊），`Neutral` 保持 tonal 底色。
  `VeneraSourceBadge` **整枚不参与材质轴**——那块固定深色底板是"压在任意封面图上都可读"的唯一凭据。
- **设置域控件从此跟外观轴走**（`SettingsComponents.kt` 头注原有那句"容器用 miuix、控件用 M3 的既有边界"已改写）：
  这是 2026-09-29 用户改判「一起迁，分批推」的结果。代价说清楚：`AppearanceStyle` 默认 MIUIX，
  所以 Miuix 档下开关/滑条/图标按钮/弹窗**确实换成 Miuix 形态**；"逐像素零变化"现在只对
  **材质轴的 SOLID 档**与 **MD3 档**成立，不再覆盖外观轴切到 MIUIX 那一格。
- **动过冻结先例的位置**（沿 `:1048` 那条"外观改动记豁免"的做法补记）：`feature/settings/SettingsComponents.kt`
  两张分组卡贴玻璃、`SettingsSelect` 的弹层与两颗按钮改走转发件；`SettingsHome.kt` 返回键改 `VeneraIconButton`；
  `AppearanceSettings.kt` 插入「界面材质」组（三轴各管什么写进 summary）；`BlockingSettings` / `AppSettings` ×2 /
  `UpdateCheckUi` 的确认框改走 `VeneraDialog`。

三条**主动不做**，都是"宁可空着也不假统一"：

1. `NetworkSettings` 的代理表单弹窗整枚留在 M3 `AlertDialog`（含它内部两颗按钮，保持一枚弹窗自洽）：
   它正文里嵌 `SettingsSelect`，外层再换 `VeneraDialog`（miuix 后端 = 独立 `Dialog` 窗口）就成了 Dialog 套 Dialog，
   预测式返回与焦点归属真机未验。
2. `VeneraTextField` 转发件未建（miuix `TextField` 无 `isError`、label 模型不同）。
3. `components/ComicTileLayout.kt:190` 的 M3 `IconButton` 是主框架最后一处控制族直连，
   但调用点含探索页 `:552` 与历史页 `:201`（FROZEN）⇒ **按"本轮不给豁免"没动，待拍板**。

一条**纠正已批准稿**的记录：miuix 弹窗后端最初判成 `OverlayDialog`，实为接不通——
`MiuixPopupUtils.DialogLayout` 只注册状态，绘制由 miuix `Scaffold` 内的 `MiuixPopupHost()` 承担，
而设置页链路没有 miuix Scaffold ⇒ 会得到"点了没反应的假弹窗"。能用的窗口级件叫 `WindowDialog`。
同时记一条**物理限制**防后来者补统一：玻璃采宿主窗口的 RenderNode，弹窗在独立窗口 ⇒ 玻璃档的弹窗只能保持实底。

性能这条按用户裁决收住（「算了别测这个了，我感觉还行」），但测清了一件事，别再往反方向查：
`dumpsys display` 的 `modeId 4 / renderFrameRate 120` 只是 DisplayManager 侧的渲染档位名；SF 空闲报
`activeMode={id=5, 60.00 Hz}`，**应用回前台有绘制后立刻变 `{id=4, 120.00 Hz}`**，且应用投票本来就是 120
（`AppRequestRefreshRates: [120.00 Hz - modeId 4]`）⇒ **"强制 120Hz 开关"没有对象，确定不做**。
滚动段没有读数，所以计划里的 `miuix-glass-perf-baseline-2026-09.md` **没建**（不放没测过的值）。

构建：`testDebugUnitTest` **53 套 / 389 条 / 0 失败 / 0 错误** / `assembleDebug` 绿 /
`debugRuntimeClasspath` 里 material3 仍 `1.5.0-alpha22`、miuix 仍 `0.9.4-rc01`（**没为玻璃动版本**，
`ModalBottomSheet` 那条 `NoSuchMethodError` 风险未触发）/ 全仓无文件混 import 两家 blur。
预览面 4 张 → **8 张**（材质 × 明暗 × 外观），根节点改套 `VeneraAmbientBackground`，
否则没有采样源、两档看起来一样会被误读成"玻璃没生效"。
**本轮 B1~B3 一次都没在真机上看过**：七条待验清单（含"玻璃透不透""切回 SOLID 无残影""长按还能进多选"
"MIUIX 档弹窗的返回键归属"）列在方案 §八。

## 2026-09-29 追加（修复）：禁漫正文页全失败 = 源 JS 的 `Accept-Encoding` 关掉了 OkHttp 透明解压

用户读数：**只有禁漫坏，进阅读器后全部页加载失败**，别的源正常。

**根因从来不在混淆算法**。定死它的是第一轮成因日志（`2758c2d` 那笔只加读数、没改行为）：
`Content-Type=image/webp` 而**首 4 字节 = `1f8b08`**（gzip 魔数）、`inJustDecodeBounds` 读出 `-1x-1`。
禁漫源 JS 的 `getImgHeaders` 照抄浏览器头，内含 `Accept-Encoding: gzip`；OkHttp 只在
**这个头是它自己补的**时才在响应侧拆掉 `Content-Encoding`，调用方自己写上就等于宣告"编码我自己负责"，
于是 gzip 字节原样落到 `BitmapFactory` → 解不出尺寸 → `decodeAndDescramble` 两条调用都返回 null
→ 取流层按"绝不把未还原字节交下去"抛错 → 全本每页失败。
**为什么只有禁漫坏**：下载侧 2026 年已为同一类病修过（`DownloadManager:544` 那句
"绝不能向 OkHttp 传入显式 `Accept-Encoding`"），当时**取图侧三个贴头点没跟着改**。

修在**唯一出口** `ImageHeaderPolicy.headersFor`，不在三处各写一遍过滤（`VeneraImageFetcher:57`、
`ImagePipelinePolicy:331`、`ImageHeaderInterceptor:172` 都从它取；"各写一遍"正是上一次漏修的原因）。
`headersFor` 仍保留 `Referer`/`User-Agent`——UA 优先级链（`VeneraNetworkClient:168`）依赖这张表取 UA。

TDD 走的是先红后绿：新增两条用例（`源 JS 发布的 Accept-Encoding 不能流进图片请求`、
`剔除不分大小写`）先跑红，实现后转绿；原有 4 条 host 匹配用例没被误伤。
构建：**53 套 / 391 条 / 0 失败 / 0 错误** / `assembleDebug` 绿。
真机（PJZ110，`lastUpdateTime 22:49:43`）：用户判读"正常了"，且 22:49 之后**新进程 0 条**"去混淆失败"
（旧 200 条全在 22:40:58 那个修复前进程上）。

### 批次 D 续（2026-09-29 深夜）：控件族最后两件 —— 布局切换钮与输入框

**这一条是冻结面外观豁免**（沿 `:1048` 与批次 D 节同一做法）：
`components/ComicTileLayout.kt` 的 `ComicLayoutToggleButton` 由 M3 `IconButton` 改走
`VeneraIconButton`。组件本身不在名单内，但它的调用点含**探索页 `UnifiedExploreScreen:552`
与历史页 `HistoryScreen:201`**（都在 FROZEN 名单），而转发件默认按 `AppearanceStyle.MIUIX`
换后端 ⇒ 这一改**会改变两个冻结屏的观感**。豁免来自用户当句「该动的就动吧」（2026-09-29）。
批次 D 原先"按不给豁免没动"的那条待拍板项，至此清账。

`VeneraTextField` 落地，纠正批准稿一处 + 记一次自己的失误：

- miuix 那个文件里其实有**三个 `TextField` 重载**：`state: TextFieldState`(`:82`)、
  `value: TextFieldValue`(`:188`)、`value: String`(`:294`)。签名能对上的是**第三个**，
  且 `label` 是 `String` 不是槽位 ⇒ 转发件统一用 `label: String`，空串表示无标签
  （否则调用点要各写一套 null 判断）。
- `isError` **只有 M3 有**。没有把它"两家各传一遍、miuix 那边默默吃掉"，
  而是抽成纯函数 `veneraFieldBorderColor(default, error, isError)` 映射到 `colors.borderColor`
  （`TextFieldColors` 是 data class，`borderColor` 是它的字段），
  并先写 3 条单测跑红再实现 —— 这是本仓最反对的那类假开关的**唯一防线**：编译得过、
  参数传得进去、屏幕上什么都没有。
- 自己的失误留痕：加 `VeneraTextField` 那次 Edit 用整个 `VeneraTextButton` 函数体当
  `old_string`，把它**整块替换掉了**。靠 `grep "fun Venera"` 数函数才发现并补回。
  教训：往文件里插东西时，`old_string` 要用"锚点注释行"，不要拿邻居函数全体当锚。
- 连带不做（保持一枚弹窗内部自洽）：`NetworkSettings` 代理表单弹窗**内的两个输入框**
  跟着它自己的两颗按钮一起留在 M3 —— 见上一节那条"Dialog 套 Dialog"的理由。

构建：`testDebugUnitTest` **54 套 / 394 条 / 0 失败 / 0 错误** / `assembleDebug` 绿。
真机仍**未验**：新增待验两条 —— ① MIUIX 档下布局切换钮在探索页/历史页顶栏的位置与触达
（转发件的 content 是普通 lambda，M3 侧没塞 `Row`，测量应与改动前同）；
② `VeneraTextField` 在 miuix 后端的 **label 呈现口径**（浮动标签 vs 占位符）与 M3 的
outlined 轮廓不同，种子色输入框与"添加屏蔽项"那两行要在真机上看过才算数。

---

## 批次 E（2026-09-29 深夜）：B4 详情页 + 源管理控件族迁移

### 计数对账（先把自己方案的数字纠正一遍）

三方数字都对不上，现用同一口径重数（**按符号逐词匹配调用点**，不含注解 opt-in，
`SourceSettingItem.Switch` 这类成员访问已排除）：

| 口径 | 数值 | 说明 |
|---|---|---|
| 全站 M3 直连调用点 | **418**（迁移前 474） | 方案里写的"267"不可复现 —— 那个数是更早一次**只数交互件**的结果，不是同一口径 |
| B4 域 gross（6 个文件） | **147** → 迁移后 **82** | 详情页 36→20、ComicSourceScreen 78→39、SourceEdit 17→13、GalleryAccount 9→5、WebLogin 5→4、SauceNao 2→1 |
| B4 域去掉 `Icon` | **107** | 与方案里"~107"吻合，说明那个数的口径本来就是"排除图标件" |
| 用户给的 127 | — | 未复现。最接近的是 gross 147 与去 Icon 107 之间，差值正好是 `Icon`(39)/`CircularProgressIndicator` 一类；已按现数上报，不凑数 |

### 对我方案「无对应物清单」的一处公开纠正

方案里写"`Checkbox`、`Divider` 之类要看 miuix 有没有对应物"，实际读了 0.9.4-rc01 的 sources jar：

- `basic/Checkbox.kt:60` **有** —— 只是模型不同（`state: ToggleableState` + `onClick: (() -> Unit)?`，
  M3 是 `checked: Boolean` + `onCheckedChange`）⇒ 新建 `VeneraCheckbox`，映射写成两个纯函数
  （`veneraToggleState` / `veneraCheckClick`）。
- `basic/Divider.kt:30` **有 `HorizontalDivider`**，且名字与参数（`modifier/thickness/color`）同形 ⇒
  直接换 import，不建转发件（两家都画 0.75dp 一条线，调用点本来就自己给了 thickness+color）。
- `basic/Dropdown.kt` 只是 ListPopup 的行渲染件，**没有**锚点式 `DropdownMenu` ⇒ 方案这条判断成立，保留登记。
- miuix `ButtonColors`/`TextButtonColors` **没有描边位** ⇒ M3 的 `OutlinedButton`（源管理登录弹窗 1 处）
  换过去会连描边一起丢 ⇒ 登记，不换。

新增第三件转发件 `VeneraButton`（两家 content **都是 `RowScope` 槽**，真交集）：
建它的唯一动因是"提交按钮里要塞进度条"，`VeneraTextButton` 走 `text: String` 表达不了。

### 转发件本次扩的参数（全部两家都认，不是单侧静默忽略）

- `VeneraTextButton(destructive = …)`：M3 侧 `textButtonColors(contentColor = error)`、
  miuix 侧 `textButtonColors().copy(textColor = error)`。常规色**各回各的默认**（primary /
  onSecondaryVariant），所以 `ButtonColors` 不是 data class 这一点不会咬到 M3 侧。
- `VeneraDialog(confirmDestructive, buttonsEnabled)`：原站点用 `StatusColors.Failing`/硬编码
  `0xFFE53935`/`actionFavorite` 三种色表达"这是破坏性动作"，现统一到 `colorScheme.error` 一条口径。
- `VeneraTextField(placeholder, visualTransformation, textStyle)`：
  ① miuix 只有 label 一个文本槽 ⇒ `veneraFieldLabelSlot` 定规则（有标签用标签；无标签时占位符顶上去）；
  ② `visualTransformation` 两家同形，密码/密钥三类输入全靠它，丢了就是明文上屏；
  ③ `textStyle` 可空是刻意的 —— 两家的默认字面各来自自己主题，转发件不替调用点决定默认值。

### 本批留下的缺口（全部有理由，不是漏）

- 详情页：**DropdownMenu**（标签长按菜单）、**ModalBottomSheet**（评论 sheet，含它自己的
  `TextField`+`TextFieldDefaults` 评论框）、**章节下载 AlertDialog**（标题槽里挂了"全选未下载"
  这颗交互按钮，而 miuix 对话框标题是 `String`，换过去会静默丢件）⇒ 三处整块留 M3，
  登记注释就写在 ComicDetailScreen 的导入区。弹窗**内部**的两颗按钮与列表复选框已经走转发件。
- 源管理：`OutlinedButton` 1 处、带图标的 `TextButton` 1 处（miuix 的文字按钮没有槽位）。
- 源脚本编辑页的"放弃修改"确认框：它本身就是**弹窗套弹窗**（整屏是一个 `Dialog` 窗口），
  与 NetworkSettings 代理表单同一条裁决 —— `WindowDialog` 叠窗口的预测式返回与焦点归属
  未真机验过，先保持 M3。
- 全仓的 `Icon`(156) 与加载指示器不在本批口径内：玻璃挂外壳不挂图标；指示器沿用
  「统一走波浪环」的既有裁决。
- **容器级玻璃本批不扩**：详情页 15 处 `VeneraCard` 已经自动带 CONTAINER 玻璃（批次 D 的封装件），
  但页面里还有 ~12 枚 miuix `Surface(` 面板；给它们贴玻璃会把单屏 CONTAINER 数量推到
  方案风险表写的"每屏 ≤6"之外。批次 D 的 9 条真机待验还没跑，先不把观感风险面摊大。

### 自查教训（本轮自己的错）

- node 脚本 `split("\r\n")` 遇上**纯 LF 文件**会得到"整个文件一行"，于是 `findIndex` 返回 -1、
  `splice(0,0,…)` 把 import 插到了 `package` 之前，而 `filter` 又没删掉该删的导入 —— 
  SourceEditScreen 被这样处理过一次，靠 `sed -n '1,26p'` 现形才修回。
  **规则：脚本改文件前先 `.replace(/\r\n/g,"\n")` 再 split，写回后打印首行确认。**
- 通配 import 拆分**不是行为中性**的（批次 D 已记一次），本轮再确认一次：ComicSourceScreen 的
  `Button`/`Text`/`Scaffold`/`Surface` 一直解析到 miuix，所以那几件**本来就已经是 Miuix 后端**，
  计数里不该把它们当 M3 —— 上面的 gross 147 是按显式 M3 导入逐符号数出来的，已排除这种情况。

构建：`testDebugUnitTest` **55 套 / 402 条 / 0 失败 / 0 错误**（新增 `VeneraWidgetMappingTest` 8 条，
先红后绿：红的时候报的就是 5 个未实现的映射名）/ `assembleDebug` 绿；
`material3 1.5.0-alpha22`、`miuix 0.9.4-rc01` 版本未动。

真机**未验**（本批 4 条新口径）：① 源管理列表开关被显式约束成 44×28dp，而 miuix 原生是 49×28dp，
Miuix 档下形态要看一眼；② 评论 sheet 里三颗 `VeneraTextButton` 的原生最小尺寸 58×40dp 会不会把
列表撑疏；③ 破坏性按钮统一到 error 色后，"清除/注销/删除"三处的观感；④ 详情页评分弹窗与新建
收藏夹弹窗改走 `WindowDialog` 后的进出场与遮挡。

---

## 批次 F（2026-09-30）：B6 阅读器 + 画廊迁移，外加"按钮小了"两处成因

### 用户看图报的两条尺寸成因（先记账，因为它们是全仓性的）

| 症状 | 根因（一句话） | 修法 |
|---|---|---|
| 图标按钮偏小 | `VeneraIconButton` 吃了 miuix 的 `IconButtonDefaults.MinWidth/MinHeight = 40dp`，而 M3 `IconButton` 默认 **48dp**（也是 Android 触达位下限）⇒ 换后端那一刻全仓每一颗图标按钮触摸区各缩 8dp | 尺寸钉在**唯一出口**（转发件内部传 `minWidth/minHeight = 48.dp`），不在 14+ 个调用点各写一遍 |
| 源管理那颗启用开关被压扁 | 调用点把它显式约束成 `44×28dp`（M3 开关的压缩版），而 miuix 轨道是 `49×28dp` | 删掉约束，尺寸交回各家原生 |

同族第三条：画廊搜索区的 4 处 `clickable(indication = ripple())` —— **`MiuixTheme` 自己
provide 了 `LocalIndication = MiuixIndication`**（`theme/MiuixTheme.kt:36`），显式写 `ripple()`
等于在 Miuix 主题上强按一层 M3 水波。改成不传，交回环境。

### 转发件本次扩的色位（两家各有落点，不是单侧忽略）

- `VeneraSwitch(checkedThumbColor: Color? = null)`：M3 `SwitchDefaults.colors(checkedThumbColor=…)`
  ↔ miuix `switchColors().copy(checkedThumbColor=…)` —— **两家字段同名**，这是运气好。
- `VeneraSlider(thumbColor / activeTrackColor / inactiveTrackColor)`：M3 的 active/inactive
  就是 miuix 的 `foregroundColor`/`backgroundColor`（名字不同、语义同）。
  实现细节记一笔：miuix 的 `SliderColors`/`SwitchColors` 字段是 **`private val`**，
  读不到 `base.thumbColor`，所以只能"逐位 copy"，不能 `?: base.xxx`；
  M3 侧反过来可以直接把 null 翻成 `Color.Unspecified`（它的工厂认这个哨兵）。
- null 一律 = 用各家默认，转发件**不自带任何色值**，否则另一档会被连带改脸。

### 迁移量与剩余缺口

- 画廊：`IconButton` 11 → `VeneraIconButton`、`Slider` 1 → `VeneraSlider`、
  收藏墙两枚 `AlertDialog` → `VeneraDialog`（"移除/清空"原来各用 `actionFavorite` 色，
  现统一到 `colorScheme.error`，与批次 E 那三颗同一条口径）、`Surface` 1 → 换 miuix import。
- 阅读器：`Switch` 4 + `Slider` 2 → 转发件（带上面的色位），并清掉一枚没人用的 `Tab` 导入。
- 登记保留（各有理由，写在文件导入区）：
  阅读器 3 枚 `ModalBottomSheet`；画廊信息 sheet 的 `ModalBottomSheet` + `DropdownMenu`；
  "看哪一期"弹窗的 `DatePicker`（miuix 只有 `NumberPicker`，日历没有对应物，整枚弹窗含内部两颗按钮一起留）；
  `ChapterCommentsSheet` 整块（阅读器自绘深色浮层，边框/文字色全靠 M3 的 `shape`/`colors` 覆盖表达）；
  各页 `Icon` 与加载指示器沿用既有裁决。
- 全站 M3 直连（同一逐词口径）：**474 → 418 → 384**。B4/B6 域内已无可迁件，剩余全是登记缺口 + `Icon` + 指示器。

### 规则更正一条

`VeneraGlass.kt` 头注写的"Kyant 只准出现在 `components/backdrop/`"与实况不符：
保护域 `feature/Navigation.kt` 自 `4270487` 起就 import Kyant（底栏录制层接线）。
已把头注改成"唯一在册例外"，并写明**别拿它当先例往别的文件加 Kyant、也别当违规去清理**。

### 自查又犯一次的那类错

批量脚本插 import 时用了 `findIndex(...) < 0 ? 0 : i` 这类兜底 —— 锚点没找到就往**文件头/文件尾**插，
`CoverViewerScreen.kt` 被插进函数体第 124 行、`VeneraReaderScreen.kt` 被插到 `package` 之前。
编译期才现形。已写一条全仓扫描（"import 行不得出现在 import 块之外"）并顺手发现：
`ComicSourceDao.kt` / `PersistentCookieJar.kt` / `ReaderZoomState.kt` 三个文件带 **UTF-8 BOM**，
按 `^package` 匹配会把它们误判成异常 —— 扫描脚本要容忍 BOM。

构建：`testDebugUnitTest` **55 套 / 402 条 / 0 失败 / 0 错误** / `assembleDebug` 绿；
material3 与 miuix 版本未动；全仓无文件同时 import 两家 blur。
真机待验在批次 E 那 4 条之上再加 3 条：① 图标按钮回到 48dp 后，大图页工具栏五颗并排是否挤；
② 阅读器开关滑块改为主题主色（Miuix 档）后的对比度；③ 画廊收藏墙两枚确认弹窗的进出场。

---

## 批次 G（2026-09-30）：B5 冻结主 Tab 屏 + 外围收口（用户给豁免："直接做剩下的吧"）

### 豁免记录

用户原话「直接做剩下的吧」，上下文是我上一条明确列出"只剩 B5，全部在 FROZEN 名单里，要给豁免才动"。
⇒ **首页 / 收藏 / 历史 / 搜索 / 图片收藏 / 本地漫画 六屏的外观改动豁免记在此处**，
改动性质与前几批同：控件后端从 M3 换成转发件（Miuix 档下形态会变），**路由、`VeneraNavTab` 枚举、
`Navigation.kt` 一律未动**（保护域这条没被这次豁免覆盖）。

### 迁了什么

- `IconButton` → `VeneraIconButton` **29 颗**：本地漫画 7、图片收藏 3、收藏 3、搜索 2、下载 4、
  同步备份 1、日志 3、历史 2、首页 3、探索子页 2+1、统计 1。
  首页那 3 颗本来就是 miuix 的（文件里 M3 导入是**死导入**），一并删掉。
- `TextButton` → `VeneraTextButton` 8 颗（本地 2、图片收藏 3、下载 0、Cloudflare 过盾页 1、其余在弹窗改写里）；
  图片收藏「移除」那颗原本用 `AccentBadge` 色，现与批次 E/F 同一条口径走 `destructive`（error 色）。
- `OutlinedTextField` → `VeneraTextField` 5 处（同步备份 4 含 WebDAV 账号/密码/路径、日志 1）。
- `AlertDialog` → `VeneraDialog` 1 枚（本地漫画章节选择）。
- `HorizontalDivider` → miuix import 2 处。
- 全站 M3 直连（同一逐词口径）：**474 → 418 → 384 → 336**。

### 本轮新登记保留（各有理由，注释在文件导入区）

- **搜索页三枚 `AlertDialog`**：标题是定制排版（`itemTitle` 字号 + semibold + textPrimary），
  按钮是文件内自绘的 `SearchTextAction`（caption 字号、无最小触达高）。`VeneraDialog` 的标题只是
  `String`、按钮固定 `VeneraTextButton`（miuix 侧 58×40dp 胶囊）⇒ 换过去是把搜索弹窗的紧凑口径改掉，
  那是**观感决策不是迁移**，等真机看过 D~F 再定，不静默改。
- 图片收藏页 3 颗 + 下载页 4 颗「图标+文字」的 `TextButton`：miuix 文字按钮没有槽位。
- `Scaffold` 3 处（图片收藏 / 同步备份 / 日志）：miuix 同名件参数面不同，换它=重做一层窗口内布局。
- `DropdownMenu` 各页、`DatePicker`、`ModalBottomSheet`、`LinearProgressIndicator`/`Circular*ProgressIndicator`、
  `Icon`/`Text` —— 全部沿用前几批已记的裁决。
- 保护域 `Navigation.kt` 的 M3 `IconButton`/Kyant 导入：未动。

### 本轮自己的两次错（都记下来防复发）

1. **写了个"通用折叠调用点"的脚本**去把 `TextButton(...) { Text("…") }` 压成一行，
   结果把 `if (…)` 表达式截半、并把多行 lambda 压平后**让注释吃掉了后面的代码**
   （`FavoriteImagesScreen.kt` 两行变成语法上能过、语义上全错的垃圾）。
   处置：`git checkout HEAD --` 该文件后只做安全的 IconButton 替换重做。
   **规则：批量改写只允许"逐形状白名单"匹配，认不出就跳过并报告；注释密集的文件不要压平。**
2. 字符串替换写成 `join("import …VeneraTextButton")` **漏了行尾 `\n`**，与下一条 import 粘成一行
   （`CloudflareBypassActivity.kt:28`）。同类错还有一次：漏跑 `FavoritesScreen.kt`（清单靠手抄，不是从名单生成）。
   **规则：批量文件清单要从口径脚本直接产出，不要手抄。**

构建：`testDebugUnitTest` **55 套 / 402 条 / 0 失败 / 0 错误** / `assembleDebug` 绿；
material3 与 miuix 版本未动；无文件同时 import 两家 blur。
真机待验在批次 F 的 16 条之上再加 3 条：① 六个主 Tab 屏顶栏图标按钮换 Miuix 后端后的按压反馈
（Miuix 是 `pressable`/SinkFeedback，不是水波）；② 同步备份页四枚输入框在 Miuix 档的标签/占位符呈现；
③ 本地漫画章节选择弹窗换成 `WindowDialog` 后，360dp 高的章节列表滚动与关闭位置。

---

## 批次 H（2026-09-30）：顶栏图标按钮统一成磨砂圆座 `VeneraTopBarPill`

### 用户诉求与原话里的两处不实前提（已当面纠正）

原话：「所有页面顶栏的按钮，能不能都改成收藏 网络收藏 右上角那种按钮样式，带有模糊的而且动画效果，自己看下核实下」。
核实结果两条与提问不符：

1. **「收藏和网络收藏右上角那颗」不是同一颗的两份实现，而是只有一份**：`FavoritesScreen` 里私有的
   `FloatingLayoutToggle`（未导出）。网络收藏（`NetworkFavoritesScreen`）用的是 `components/ComicLayoutToggleButton`，
   无背板、无 morph、不吃采样层。同名页面两种形态本身就是不一致。
2. **「顶栏的按钮」不是同质的东西**：分两族。**A 族 13 屏**走 `VeneraTopAppBar`（内部已有栏级
   `progressiveTextureBlur`，且已 `rememberTopBarBackdrop()` 挂到列表上）；**B 族 5 处自绘 chrome**
   （阅读器顶胶囊岛、封面查看、追更页、源编辑页、网页登录页）**根本没有采样层**，磨砂在那里物理上拿不到
   —— 玻璃采的是**录制层**不是屏幕像素，给它们套外壳只会得到一层假磨砂。

### 三条拍板（AskUserQuestion，全选推荐项）

统一面 = A 族 13 屏 + 网络收藏那颗（B 族 5 处保持现状）；层数 = 顶栏每颗都磨砂（内容区图标按钮不动）；
触达 = **视觉 40dp + 触摸区 48dp**（上一轮刚因"按钮小了"被退回，这次不能再缩）。

### 落了什么

- 新组件 `components/venera/VeneraTopBarPill.kt`：磨砂圆座，配方**逐字抄参考件**不自造数字
  （圆座 `tokens.spacing.iconButtonSize` 40dp、`textureBlur blurRadius = 10f`、补底 `surface @ 0.16f`
  与 `VeneraTopAppBar` 栏级背板同档、描边 `hairline` + `outlineVariant`）。
  判据抽成纯函数 `veneraPillFrosted(backdropPresent, shaderSupported)`，回落态是**半透明底 + 描边**，
  绝不静默变裸图标。按压自己做（scale 1→0.96、alpha 1→0.88，与 `VeneraChip` 同口径）并把 `indication` 关掉：
  默认高亮画在 48dp 外层会溢到 40dp 圆座外圈成一层光晕。
- `VeneraTopAppBar.kt` 新增 `LocalTopBarBackdrop`（`compositionLocalOf<Backdrop?>`）并在 `TopAppBar(...)`
  外面 provide 一次。走 CompositionLocal 而不是逐屏传参的理由：`navigationIcon`/`actions`/`bottomContent`
  本来就是**在它内部求值**的 composable lambda，于是 13 个调用点一行都不用改。
  用非 static 是因为这个值会随能力检测与 `enableBlur` 在 null↔非 null 之间切。
- 布局切换钮**收口成一份**：`components/ComicTileLayout.kt` 的 `ComicLayoutToggleButton` 内部改用
  `VeneraTopBarPill` 当底座，并把参考件的 `AnimatedContent` 图标 morph（阻尼 0.62 spring、
  `scaleIn(0.4f)` togetherWith `scaleOut(1.6f)`）+ 累积 90° 弹簧旋转 + 长按 `Popup` 提示搬进去；
  `FavoritesScreen` 那份 private 复制件（116 行）**删除**，调用点改指统一实现。
  形变动画只挂在这颗上：返回键没有第二态，给其它按钮硬造 morph 就是假动画。
- A 族顶栏 **27 颗调用点**换成圆座（另有 1 处是 `ComicLayoutToggleButton` 内部挂的外壳本体）：
  首页 3（原来是**裸 miuix `IconButton`**，绕过转发件，一并收口）、
  收藏 2+排序 1、历史 2、搜索 2、下载 1、本地书架 6、统计 1、统一探索 1、探索子页 2、
  图库 3、源管理 1、设置首页 1、设置子页 1（`SettingsComponents` 一处覆盖全部设置子页）；
  另有 7 处布局切换钮调用点通过上面那份实现自动收口。
- 预览矩阵 4 张（`VeneraComponentsPreview.kt`）：有采样层 / 无采样层(回落) × 明 / 暗，
  含普通 / 低频 / 高亮 / 禁用 / 带角标五种。

### 回落面（哪几颗没有磨砂，原因写死在这）

- **网络收藏那颗**：核实后发现它压根不是独立屏——`AndroidNetworkFavoritesScreen` 只有一个调用点
  （`FavoritesScreen.kt:286`，`FavoritesMode.Network`），而且传的是 `showLayoutToggle = false`，
  即**它自己那颗内联钮今天是根本不渲染的**，页面右上角那颗一直就是收藏页顶栏的公共件。
  ⇒ 本轮它随 `ComicLayoutToggleButton` 收口自动获得同一形态，**不需要接采样层**；
  方案文档里"要给网络收藏接 `rememberTopBarBackdrop()`"那条前提被实况推翻，未执行。
- **追更页 `FollowUpdatesScreen.kt:87`**：B 族自绘 chrome，`LocalTopBarBackdrop` 在那里是 null
  ⇒ 它得到的是**可见回落**（半透明底 + 描边 + 同一套 morph 动画），不是假磨砂。
  该页确实有 `rememberTopBarBackdrop()`，但它没有 `VeneraTopAppBar` 那层栏级背板，
  单独给一颗浮在自绘标题行上的磨砂和全站观感不一致，故不补 provide（属 B 族保持现状那条拍板）。
- B 族其余 4 处（阅读器顶胶囊岛 / 封面查看 / 源编辑 / 网页登录）：未动，逐像素不变。

### 与批准方案的一处偏离

方案里 `VeneraTopBarPill` 签名带了 `badge: String?`。实现时**没有加**：两个带角标的调用点
（首页"源更新"、本地书架"下载中心"）本来就是 `Box { pill; Surface 角标 }` 的写法且已经对齐，
加一个当前无人使用的 `badge` 参数是给组件添不需要的形状。角标仍由调用点叠。

### 本轮自己的两次错

1. **两次批量删 import 的脚本都把"在用的"当成"没用"删了**：判据用 `new RegExp('\b'+name+'\s*[(.]')`，
   在 bash 单引号里写成 `'\\b'` 时正则退化成"字面反斜杠 + b"，于是**永不匹配**、全部误判为未用。
   误删 `VeneraIconButton` 于 6 个仍在用的文件，靠编译失败暴露、逐文件补回。
   **规则：批量脚本里的正则不要经 shell 二次转义，写成 `.cjs` 文件再跑。**
2. **`grep -r` 在这台机器上给了我一次假空结果**（`MiuixIcon`、`topBarBackdrop` 明明在文件里却返回 0 行），
   我据此差点判定"文件里没有引用"。改用 Grep 工具（ripgrep）复查后结论反转。
   **规则：判定"某符号无引用"必须以 Grep 工具的结果为准，不要用 shell 裸 grep 的返回值当证据。**
   另有一次 Edit 把 `) {` 后的换行吃掉，让紧随的 `//` 注释吃掉一行 —— 与批次 G 第 1 条同形，已复查全站无残留。

### 验证

- `veneraPillFrosted` 三条单测先红后绿（无采样层 / 平台不支持 / 两者都满足）。
- `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline` 双绿：
  **56 套 / 405 条 / 0 失败 / 0 错误**（基线 55 套 / 402 条，+1 套 +3 条即本轮新增）。
- 逐形状差异审计：16 个改动文件里"无法归类的变更行"只有三处预期改写（圆座本体、采样层 provide、
  删掉的 private 复制件）+ 首页注释 2 行，其余全是 `VeneraIconButton( → VeneraTopBarPill(` 的对称替换
  ⇒ 内容区与 B 族没有被动到。
- 真机包已推（`lastUpdateTime=2026-09-30 01:17:58`）。磨砂与按压态**只能在真机判**：
  预览里 layoutlib 的 RuntimeShader 未验证、pressed 需要真实指针。

---

## 批次 I（2026-09-30）：设置页文案去 AI 味 + 「尚未实现」灰行整体撤销

### 用户原话与两条拍板

「设置页面你看下所有的文本，把 ai 编的或者很像 ai 写的东西去掉，换成正常表达」。
AskUserQuestion 两问，答：**全量按短句重写** ｜ 未实现项**连标题一起撤掉**。

判据是三条：① `Text()` 不解析 Markdown，屏幕上的字面 `**星号**` 就是"AI 吐出来没人校对"的铁证；
② 只说用户能感知的后果，不提我们的实现状态（方法名、库名、枚举值、依赖版本、"仓库里做过一次真机否了"）；
③ 引用系统里的真实名称（「所有文件访问」「内部存储」）的直角引号**保留**，那不算 AI 腔。

### 撤掉的（12 条灰行 + 两个只为它们存在的件）

`UnsupportedSetting` 调用点 12 处：阅读器 3、本地收藏 3、屏蔽 2、网络 2（代理弹窗内）、应用 1、探索 1。
连带 `SettingsFutureGroup`（底部「尚未实现」折叠区）与 `UnsupportedSetting` 本身一起从
`SettingsComponents.kt` 删除 —— 零调用点不留尸体。
**这推翻了 `settings-audit-2026-09.md` 里两条旧裁决**（"未实现项统一收进折叠区""代理弹窗那两条保留原位"），
已在那份文档文末追加更正并把状态图例那行就地标注。

### 改了哪些字（形态计数，脚本 `Temp/settings_text_smell.cjs`，口径=只扫非注释行的中文字面量）

**424 条中文串 / 71 条命中 → 改后 370 条 / 15 条**。剩下 15 条是误报级别的合理项：
6 条精确引用系统名称、8 条格式举例（`站名-编号（yandere-1234567.png）`）、1 条组标题「故障排查」。
分形态：星号 14→0、反引号 2→0、`——` 8→0、内部黑话 15→0、自述式设计理由 8→0、
直角引号 33→6、长括号 21→8。
最大的一处重写是 `AppearanceSettings` 的「界面材质」说明：原来 6 行讲"两枚开关互不隶属、
四种组合都成立、玻璃的录制层整个不建"，现在 3 句讲"管透明度、和风格互不影响、两处都开低端机可能掉帧"。
顺带把源管理页横幅的 `⚠️ …其源已自动熔断跳过` 改成 `N 个站点连不上，已经跳过这些源`（去 emoji 去黑话）。

### 范围纪律

只动 `feature/settings/` 12 个文件 + 源管理页那一条横幅。
设置树点进去的 `SyncBackupScreen` / `LogViewerScreen` / `FavoriteImagesScreen` 用同一把尺量过 **命中 0**，
一个字没动。代码注释一律不碰（本轮唯一改的注释是"折叠区还在"这类已经不成立的话）。

### 本轮我自己的三次错

1. **两次在 Kotlin 字符串里用英文双引号包短语**（`"…显示成"译文 (原文)"，…"`）—— 直接截断字面量。
   一次编译抓到，一次我自己回看时抓到。**规则：中文文案里要引用东西就用「」或整句改写，不要用 `"`。**
2. **把 `.replace(/\r/g,"\n")` 当成 CRLF→LF 归一化**用在一个 CRLF 文件上：
   `\r\n` 变成 `\n\n`，随后写回又 `\n`→`\r\n`，`SettingsComponents.kt` 整个文件**每两行之间多出一个空行**
   （diff 报 +374 −0）。靠 `git checkout HEAD -- 该文件` 恢复（当时该文件除这次损坏外没有别的未提交改动，
   先确认过 `git diff --numstat` 才动手）。**规则：归一化行尾要 `replace(/\r\n/g,"\n")`，不是删 `\r`。**
3. **`bash -e` 内联 node 里的反引号**（模板串与 Markdown 代码跨度混用）把脚本自身打断，
   改成先用 Write 工具落一个临时 `.md`/`.cjs` 再由 node 读入 —— 与批次 H 第 1 条同族，这次是反向踩。

### 验证与「这轮证明不了什么」

`:app:compileDebugKotlin` 绿；`testDebugUnitTest` **56 套 / 405 条 / 0 失败**（与批次 H 完全同数
⇒ 反过来说明**这批文案没有任何单测覆盖**，编译绿只证明没改坏语法，**不证明字改对了**）；
`assembleDebug` 绿，真机包 `lastUpdateTime=2026-09-30 01:56:17`。

**真机待验（批次 H 那 14 屏之外新增）**：
① 设置首页 → 应用 / 外观 / 阅读 / 网络 / 探索 / 本地收藏 / 屏蔽 / 画廊 八页逐页读字，
重点看「界面材质」「预加载」「动图自动播放」「大图页背景」「文件名规则」这几段有没有被截断或串行的痕迹；
② 确认 6 页底部**不再有「尚未实现」折叠区**，且代理弹窗里只剩类型 + 主机 + 端口三项；
③ 源管理页顶部那条"连不上"横幅（要把某个站点搞成不可达才能看到，看不到就记"未验"）；
④ 被删的三条阅读器项（双击缩放、自动裁剪白边、章节评论默认展开）设置页里确实不再出现。

---

## 批次 K（2026-09-30）：画廊首页合一屏 + 搜索浮层三档来源（批次 J 同轮并入）

### 用户诉求与原话要点

「以新版效果图的信息架构、内容密度、模块组织方式和视觉层级为主要参考，但不要直接照搬它的 Neon / 蓝紫发光风格」；
「非常重要：不要重写整个 Gallery 模块。先检查当前 GalleryScreen、搜索组件、现有 Venera Components 和 Tokens，尽量复用现有组件」；
「保留现有以图搜图 / SauceNAO 功能，并将其融入新的搜索界面，不要删除或单独拆成独立页面……点击后让搜索面板平滑 morph 为以图搜图模式，而不是重新 Navigation」。

### 七条拍板（AskUserQuestion 逐条问定，不再讨论）

1. 首页**合成一屏**，撤掉第二页 Pager（左右滑那一轴整块消失）。
2. 「正在关注的画师」**改成「收藏里的画师」**，用真数据（从收藏条目抽画师标签），
   **零新请求、零新存储**；"今日更新 N"那种编造读数一律不上。
3. 来源改成「全部 / Yande.re / Gelbooru」，**默认全部 = 两腿并发**；未配 Gelbooru 账号时**默认档不变**，
   那一腿显式念「这一站还没配置」，另一腿照常出图。
4. 「每日热门」的换一批 = **留存两站原始池在内存，只换 seed 本地重排，不重新请求**。
5. 「猜你喜欢」**保留**，位置在每日热门下面，**样式与每日热门同为横向图片卡行**。
6. 底部那条墙 = **可切换的详情区**，由节点头的「查看全部」决定它当前装日榜全量还是推荐全量。
7. 冷启动**串行错开**：先进日榜两腿，落地后再取推荐两腿（推荐那一行有骨架占位，不许空着再突然插进来顶滚动）。

### 三条轴各落了什么

**A 来源选择（搜索腿）** —— 新 `gallery/domain/GallerySearchSource.kt`（`ALL` / `single` / `options` /
`availableLegs` / `missingLegs` / `label` / `gallerySourceMarkText`）。`GallerySearchViewModel` 的 `site`
轴换成 `source`，`var site` 留作 `source.sites.first()` 的只读派生（约 15 处读取点零改动）。
随轴改的状态：`page`→`pageBySite`、`exhausted`→`exhaustedBySite`、去重键 `seenKeys` 跨腿共用、
`legFailures` 按腿挂原因。排行菜单过 `GalleryRankings.supportsAll`（**「全部」档只列每一条腿都有的档**，
两站形态不同时选期入口整块收起 —— 那是"假开关"那一类，补了两条单测）。
`GallerySearchContext` 存来源与两条腿的游标，`GalleryContextPlan.plan` 的第二判据从"同站"改成"同来源"
（换来源 = 换一轮，返回能回到刚才那个「全部」档）。点卡片进大图页仍传 `post.site`（真站点），
队列物理上不可能收到"全部"。

**B 墙源** —— 新 `gallery/domain/GalleryWallFeed.kt`（`DAILY` / `FOR_YOU`；label 就是节标题，
一条口径两处用）。`HorizontalPager` 整块撤下，`PullToRefreshBox` 直接包 `when(wallFeed)`；
`isRefreshing` / `onRefresh` 从"按 pager 当前页分发"改成"按 `wallFeed` 分发"
（**同一条教训原样适用：绑并集会造出"已经加载好了还在转"和第二枚环**）。
`GalleryPageTabs` / `GalleryPage` / 分段器那一行撤下，顶栏 `bottomContent` 现在只住搜索区，
`gridTopPadding` 关搜索时落回地板（不是落回那一行的高度）。三把独立 gridState（搜索 / 日榜 / 推荐）
各记各的滚动位置。

**C sections 进墙** —— 新 `gallery/ui/GalleryHomeSections.kt`：四条节头 + 两条横卡行 + 画师行 + Tag Chip 行，
作为**恒定 key 的 FullLine item** 进 `GalleryCardsGrid`（新增 `sections` 参数）。
判空全部移到项内部 —— 这是首页顶栏重叠那次真机缺陷（锚定漂移）的根治法；不做常驻头部，
因为那要新增"顶栏地板 + 节头高"两个常量，正是冻结声明警告过的"常量留空带 / 量高度留硬边"两种失败。
横卡用固定 token（`historyCardWidth × historyCoverHeight`），`Row + horizontalScroll` 而不是 LazyRow，
条目上限 8 枚，超出只在节头念"另有 N 位"。

### 收口与归一（三处重复的取数包装）

新 `gallery/domain/GalleryLegGuard.kt`：`guardedWithBudget` 原先在日榜、猜你喜欢、搜索三处各写一遍，
收口成一份 `GalleryLegOutcome(site, posts, answered, reason)`。
**收口时查到一条真缺陷**：猜你喜欢那份在请求失败那一支照样把 `returned` 记成 0，
于是 `exhaustedSites` 会把一次 401 永久判成"这一站到底了"，而页尾写着「已经到底」——
现象是"少了一个站的图"，读起来像站里本来就没图，且全程不报错。
`answered` 这一格就是"站方回了空表"与"这一腿没答上"的永久分界，配一条专门单测。
`exhaustedSites` 也从 `GalleryForYouMerge` 挪进 `GallerySearchMerge`（「全部」档要的就是"缺席不算到底"），
旧的 3 条用例改指新家、一条没删。

### 换一批 = 本地重排（拍板 4）

`GalleryFeedSource.Daily` 多带一份 `pools`（**过滤之前的原始池**），`GalleryViewModel.remix()` 只换 `seed`
重排；`GalleryMerge.canRemix(pools)` 判"池子供不出新排列"，回 false 时调用方退回真取一次
（按下去屏上纹丝不动就是假按钮）。冷启动那一屏是缓存铺出来的、根本没有池，所以这一档是常态不是异常。

### 批次 J 并入（某条腿回 0 行时按该站画师记录换正名重跑那一腿）

判据 `gallery/domain/GalleryArtistAlias.kt` + 取数 `YandeReClient.resolveArtistAlias`（两笔：
`artist.json?name=` 按全等取记录 → 跟一次 `artist/show/<alias_id>` 重定向、只读落点 URL 的 `title`）。
**只有站方明确给出关联才重搜，否则保持 0 结果**；胶囊与历史存用户原词，换名那句话在展开卡、页尾、
空态三处念出来，并随 `GallerySearchContext.aliasNotice` 一起过弹栈。
细则、比方案多出来的两道闸、以及今天复跑的原始响应见 `gallery-artist-alias-2026-09.md` §九。

### 共用件契约变更（都是加性）

1. `feature/SectionHeader.kt`：`MiuixSectionHeader(title, onTap: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null)`。
   `trailing != null` 时**不画那枚「›」且整行不再可点**（行里已经有按钮了，两个动作打架，
   而用户没法从外观判断哪个能点）。`HomeScreen.kt`（FROZEN）那 6 个调用点一行未改。
2. `gallery/data/GalleryTagDictionary.kt`：加 `lookupArtists(names): Set<String>?`，
   **null = 离线库打不开**（与"查了、确实没有画师"必须分两档，否则就是把自己的故障说成用户没干活）；
   旧 `artistNames` 委托过去并回落空表，`GalleryPostScreen` 的调用点零改动。
3. 玻璃登记状态：`VeneraGlassRole.PANEL` 与 `SEGMENTED` 两档**登记而不启用**（全仓零调用点），
   理由见下面偏离第 3 条。搜索卡本体与一级卡片继续不上玻璃（冻结声明那条理由原样有效）。

### 与批准方案的偏离（7 条，逐条为什么）

1. **没建 `gallery/ui/GallerySourceMark.kt`**：字母小徽标用既有 `VeneraSourceBadge` 就够，
   domain 里只留 `gallerySourceMarkText(sites)`（`Y` / `G` / `Y·G`，按 `GallerySite.entries` 序）。
   站方 logo 资产不进包里。
2. **`GalleryWallAnchors.kt` 与它那 4 条单测建了又撤**（可逆归档 `_trash/gallery-wall-anchors-2026-09-30/`）：
   计划里"卡序 ↔ grid index 换算"的前提是三面墙共用一把 state；实际改成三把独立 state 之后换算函数
   **零调用点**，而"返回不丢进度"那条老账在搜索那把上仍是原样直接记 index。留一个没人读的换算是负债。
3. **`PANEL` 玻璃没给四条节点头**：节点头跟着墙滚，滚到屏幕中部时采样源还是顶栏那一片，
   拿到的像素与它所在的位置无关 = 假磨砂（与批次 H 查 B 族那次的同一条物理限制）。
4. **`GalleryForYouEnd` 没撤**（计划说撤）：详情区那一面墙仍然要"各站几许 / 剔了几张已收藏 / 到底了没"
   那一行读数。`emptyCopyOf` 也**没搬进 domain** —— 它读的全是 fvm 的快照状态，搬过去等于把 VM 的形状
   塞进判据层；横卡行与画师行各有各的空态成因（`GalleryArtistsGap` 四档），不是同四种。
5. **计划风险 1 的 `extendWall` 增量派生没做**。整片重算只有 O(张数) 次字符串判定（屏蔽规则 × 作者/标签），
   一次续页 ≤ 400 张；卡片的真实成本（Coil 解码与位图）本来就走缓存，且只重绘可见项。
   **这条是判断，不是实测** —— 真机若在续页那一刻看见掉帧，`extendWall` 仍是既定退路。
6. **remix 刻意不滚回顶部**：`LaunchedEffect(seed)` 那种重置会在每次条目重建组合时再触发一次
   （记忆「导航条目会重建组合」），把用户从他看到的位置拽走。
7. **历史双列 chip 丢了旧 `HistoryRow` 的「排除项灰显」**：`VeneraChip` 没有 `annotatedText` 入口，
   本轮不为它给共用件加形状。恢复法与那条判据的代码都留在 `_trash/gallery-history-row-2026-09-30/`。

### 本轮我自己的错（按"哪种检查抓不到它"排序）

1. **`GalleryForYouPage` 忘了传 `sections`** —— 切到「猜你喜欢」那面墙时四节整片消失，
   还顺带违反"节数恒定"这条硬约束。**编译绿、464 条单测全绿都抓不到它**；
   成因是 `sections` 带默认值 `emptyList()`。**规则：加了必填语义的参数就不要给默认值，或者逐个调用点回读。**
2. **一次 Edit 把 `onOpen = { post ->` 与 `GalleryViewerQueue.set(…)` 两行吃掉**：old_string 跨了我要插入的
   位置。这次编译也没来得及抓（下一轮编译在补回之后才跑）—— 是"改完立刻回读那一段"抓到的。
3. **想删一行却插成两遍**（`val legs = attempts.map { it.outcome }`）：old_string 只写了它下面那行注释。
   这一条是编译报"重复声明 / 冲突声明"才暴露的。
4. **协程里的新局部变量取名 `first`，把外层 `val first = nextPage == 1` 遮蔽了**：编译过、语义差点错。
   改名 `byOriginal`。**规则：同一个 lambda 里不要复用外层判据的名字。**
5. **注释里写了一个没量过的数字**（「每格仍有 ~116dp」）—— 换成"未量，真机复核"。
6. **合一屏时把一段 KDoc 挂到了错的声明上**（`runSearch` 的说明长在 `private var searchJob` 头上），
   还留下一句"两面墙仍然同时组合"的错话、以及三处"第 1 页 / 两页"的旧说法。都已改回。
7. **把本文件的追加写成了 latin1**：脚本里读旧内容用 `'latin1'`（为了不改字节），却把新段落
   先用 `'utf8'` 读成字符串、再按 `'latin1'` 写出去 —— 中文字符一律被截成低字节，
   `FREEZE-STATEMENT.md` 当场变成非法 UTF-8（165 行追加内容全是碎字节）。
   处置：先确认 append 之前该文件**不在 `git status` 的修改列表里**（也就是除我这次追加之外没有别人的
   未提交改动），再 `git checkout HEAD -- 该文件` 恢复，用 UTF-8 重读重写。
   **规则：一个文件里读与写的 encoding 必须成对；跨编码拼字符串的脚本要现验 UTF-8 往返是否等长**
   （`Buffer.from(s,'utf8').length === raw.length` 这一行就是这次的探测器，写进脚本末尾常备）。

### 验证

- `./gradlew :app:testDebugUnitTest :app:assembleDebug --offline` 双绿：
  **62 套 / 464 条 / 0 失败 / 0 错误**（批次 H/I 基线 56 套 / 405 条 ⇒ +6 套 +59 条，满足"净增 ≥5 套"）。
  新增测试文件 6 个：`GallerySearchSourceTest` / `GallerySearchMergeTest` / `GallerySearchHistoryTest` /
  `GalleryLegGuardTest` / `GalleryArtistsTest` / `GalleryArtistAliasTest`；撤下 1 个
  （`GalleryWallAnchorsTest`，随本体一起归档）。
- 先红后绿的判据逐条（共 8 条新用例文件里的对应项）：来源三档顺序（全部在最前、其余按站表序）；
  两腿展开与每腿页宽各按本站；未配账号时可用腿只剩一站且缺席腿带原因；换来源算换一轮 /
  同来源同条件幂等不压栈；`isStaleLeg` 只丢不匹配那一腿；两腿按本站原序轮转混排；
  跨腿重复只留前者并计数；缺席腿不判到底；`nextPagesForAppend` 到底的腿不再要页；
  `canRemix` 只在池子真够时放行；「全部」档只列每条腿都有的排行档、且不摆选期入口；
  画师表只收词典判为画师的名 / 降序定序 / 跨站同名并一条带两站标记 / 黑名单词不进表 /
  零收藏与零画师分两档 / 首见预览图；历史 v1 两站同串读回合成一条 / v2 编解码幂等 /
  push 命中并入站集合顶到最前 / 认不出的 routeKey 丢掉；`answered` 把"回了空表"与"请求失败"分开；
  `resolveToken` 的四档门。
- 静态视觉判据六条逐条过（对象 = 本轮 22 个 main 文件：7 新建 + 15 修改）：
  ① `Color(0x` **0 处**；② 新写 `.copy(alpha =` **0 处**（唯一那处 `StatusColors.BadgeSurface.copy(alpha = 0.72f)`
  在 HEAD 里就有，用 `git show HEAD:` 核过，不是本轮新增）；③ `Brush.` 渐变描边 **0 处**，
  `shadowElevation` 只有一处且 `= tokens.elevation.attached`；④ **新增行**里出现的颜色只有文本色四项
  （`textPrimary` / `textSecondary` / `textTertiary` / `onSurfaceVariant`；口径 = 7 个新文件全文 +
  15 个改动文件的 `git diff` 新增行，两边各跑一遍取并集），**没有新增强调色、没有 alpha 压色**；
  ⑤ 尺寸全部引用既有 token 名（`historyCardWidth` / `historyCoverHeight` / `sectionGap` / `space1~6` /
  `type.badge` / `type.caption`），新增 dp 字面量 **0 处**（`104.dp` 那处顶栏地板是既有值）；
  ⑥ 界面文案去 AI 味：非注释行的反引号 / 星号 **0 处**，本轮清掉两条串里的 `` `long_hair` `` 那种写法
  （`GalleryScreen` 空态与推荐页 `NO_USABLE_TAGS` 各一条）。
- 保护域核对：`Navigation.kt`、`VeneraNavTab`、路由映射、顶栏齿轮入口 **零改动**，本轮不新增目的地；
  `GallerySite` **没有**加第三个枚举值（这是本批护栏，各处 `when` 穷举零改动）。
- 真机包状态：**待推**（本轮不 commit，等用户点名）。

### 真机待验（一加 PJZ110，页面由用户点，我只推包与读日志）

① 首屏层级与"不显得空"（四节 + 瀑布流一屏能看见几节）；② 两条横卡的换一批各自是否秒出 ——
每日热门必须**零请求**（logcat 计数）；③ 冷启动是否真的"先日榜再推荐"（`logcat -d` 数请求顺序）；
④ 「全部」档搜一个只有 yande 有的词、和一个只有 Gelbooru 有的词，看部分失败读数是否真念；
⑤ 未配 Gelbooru 账号时默认档是否不变、那一腿的话是否准；⑥ 历史同一个词只出现一条、双列、
小 × 好点（触达 48dp）；⑦ 点卡片进大图页再返回：滚动位置与队列站点是否正确（队列绝不能出现"全部"）；
⑧ 节点头「查看全部」来回切日榜 / 推荐，各自滚动位置互不污染，**且四节在两面上都在**
（本轮第 1 条错就出在这一格）；⑨ 反搜（以图搜图）形变在输入法弹起状态下不被打断；
⑩ 三轴截图（MIUIX/M3 × 明/暗 × SOLID/GLASS）人工看过：玻璃档不出现描边发光、实色档无残影；
⑪ 批次 J 那四条见 `gallery-artist-alias-2026-09.md` §9.5；
⑫ 在日榜那面墙上点推荐节头的「换一批」，再切到推荐那面墙：应落在顶部而不是刚才的深度
（本轮给那个 effect 补上 `wallFeed` 键就是为它 —— 合一屏之后另一面墙不在组合，`scrollToItem` 会变成空操作）。

## 批次 L（2026-09-30）：画廊「关注画师」系统 + 详情面板画师行

### 用户诉求与原话要点

「你先看下能怎么做关注系统，在关于这个张图页面可以显示画师的推和pixiv的图标（如果有的话）并关注画师，并显示画师的pixiv头像（如果有）」；
「可以先开工，等首页改完后dsh在改，dsh改完后你接入主页」。

### 七条拍板（AskUserQuestion 逐条问定）

1. 关注的语义 = **名单 + 直达入口**。两站都没有匿名可写的关注端点，所以不与站方发生任何关注关系；
   名单只在我们这一侧成立。
2. 详情页形态 = **每位画师一行：头像 + 平台入口 + 关注**。
3. 取数与 Referer = **面板打开才取**；pixiv 的 Referer **进共享取流层**（不在页面里各写一遍）。
4. Gelbooru 侧的供体 = **「还是要 Danbooru，走过盾链」**（用户点名 `danbooru.donmai.us/artists/183883`）。
5. 平台图标 = **「站方没推就不摆推，改成摆 fanbox」**（次序 pixiv > 推 > fanbox）。
6. 过盾形态 = **「只静默试一次，失败就什么都不摆」**（不为这一条造任何 UI）。
7. 关注那一节位置 = **「不进首页，只留入口」**。

### L1 取证：三条旧口径被当场推翻

| 事项 | 当天读数 | 结论 |
|---|---|---|
| Gelbooru 画师页按**名字**寻址 | `s=list&name=setmen` / 不存在的名字 / 不带参数 —— 三次返回**逐字节相同**（17621 字节、同样 64 条链接） | `name=` 根本不生效，名字寻址是死的。我此前判它"可用"，判据是"页面里出现了这个名字"，而那是**壳页回显输入** —— 错的判据 |
| Gelbooru 画师页按**编号**寻址 | `s=show&id=1` → 8781 字节、正文两处 `member.php?id=9311`；`s=show&id=183883` → 2969 字节空壳 | 编号寻址成立（183883 是 Danbooru 的编号，Gelbooru 没这条），但**从画师标签到那个编号之间没有匿名可走的一跳** |
| Danbooru 可达性 | danbooru / safebooru / testbooru 三站同测全部 **403 + `Just a moment...`** | 拿不到真样例。于是**不押一种字段形态**：`url_string` / `urls` 字符串数组 / `urls` 对象数组三种全吃，每种各一条单测 |
| pixiv `/ajax/user/{id}` | 不带 UA、带 UA、带 Referer 三档对照 → 都是 200、同一份 687 字节 | 这个 JSON 端点匿名零头即可 |
| pixiv 头像图本体（`i.pximg.net`） | 不带 Referer → 403（146 字节）；带 `Referer: https://www.pixiv.net/` → 200、5149 字节真图 | 这条进了 `ImageHeaderPolicy` 的内置表（一个 host 一行），并确认画廊的 ImageLoader 走的就是挂着 `ImageHeaderInterceptor` 的那台共享 client |

### 落地

- **判据层（新增，可单测）**：`gallery/domain/GalleryArtistLinks.kt`（平台判定 + 图标摆放次序 +
  handle/用户编号提取）、`GalleryArtistFollows.kt`（名单幂等/去重/排序，两站同名是两条 ⚠️ 这一条只管
  **存储与身份**；批次 M · M5 之后首页那一栏在**摆**的时候并成一条，两处不是矛盾，是两层各自的选择）、
  `GalleryArtistUrls.kt`（`url_string` 切分 + 站方 `-` 停用前缀剔除 + 头像两档挑选）。
- **形状兼容层（新增）**：`gallery/data/GalleryArtistEndpointParse.kt` —— 手写逐字段走而不是给 DTO 押形态，
  理由是"押错 = 安静地什么都不显示"。统一口径只有一条：**认不出就抛错，绝不静默交回空表**。
- **取数层（新增）**：`gallery/data/PixivClient.kt`、`DanbooruArtistClient.kt`（打
  `NoInteractiveBypassTag` = 不弹过盾窗，但仍吃共享 CookieJar 里那份按 host 生效的 `cf_clearance`，
  这就是拍板 6 的"走过盾链、只静默试一次"）；`YandeReClient` 的 `YandeReArtistDto` 加 `urls`、
  新增 `artistLinks(name)`（与批次 J 的 `resolveArtistAlias` 同端点同记录，只取另一个字段）。
- **存储层（新增）**：`gallery/data/GalleryArtistFollowsStore.kt` —— 照收藏那份规格
  （构造时同步读盘 / 先写 `*.tmp` 再改名 / 坏档另存 `*.corrupt-<ts>` 并挂 notice）。
  **只存站别 + 名字 + 时刻**：pixiv 编号与头像地址都会过期（实测旧编号回 `error=true`），
  存了就等于承诺兑现不了的东西。
- **UI**：`gallery/ui/GalleryArtistRows.kt`（详情面板一行=头像 + 平台入口 + 关注，面板打开才发请求）；
  `GalleryInfoSheet.kt` 把画师那一组从右列胶囊里摘出来换成这一叠行（角色/作品仍是胶囊），并新增
  `imageLoader` 入参；`GallerySearchArea.kt` 搜索卡空框态加「关注的画师」一排（拍板 7 的那个"入口"）
  ⚠️ 这一排**当天就被 L11 撤掉**：入口改由首页那一栏「正在关注的画师」承担，同一份名单不在两处长两样。
- **共享层**：`data/network/ImageHeaderPolicy.kt` 内置表加 `pximg.net → Referer` 一行。

### 三条红线（都是"两种状态在屏上长得一样"的那类）

1. **取不到 = 什么都不摆，且不说"没有"**。`failure` 只在日志里留话（tag `GalleryArtistRows`），
   界面上永远不出现"这位没有外链"这种我们没资格断言的句子。
2. **头像取不到 = 首字母圆座**，不是空白圆（空白圆读起来像图挂了）。
3. **过盾页必须算 failure 而不是"0 条结果"** —— 卡盾与"这位真没外链"必须是两句话。

### 偏差与自记错误

- 上面表格里"名字寻址可用"那条是**我自己上一轮的错报**，本轮用正负例差分推翻并已在批次 J 文档 §四.2
  与 `(plan)` 里回改。教训：**判参数生效只能比正负例的输出差异**，"返回 200 + 页面里含有那个词"不是证据。
- 平台入口做成**文字胶囊**（pixiv / X / FANBOX）而不是站方 logo：与首页画师行那排字母小徽标同一条理由
  —— 引第三方标识要往 APK 里塞人家的资产。
  ⚠️ 这句的后半段**当天就被 L12 推翻**（首页画师行与卡片角那两枚已换成站方真图标，见下面「批次 L 增补」）。
  文字胶囊本身保留，但理由换成另一条：平台是 pixiv / X / FANBOX / Mastodon 若干家，每家都要塞一份
  第三方品牌资产还得跟着人家改版走；而来源标识只有两站、各有一枚现成原档。两笔账不是一笔。
- 中途 dsh 正在改首页，`:app:compileDebugKotlin` 与整个单测源集**一段时间编不过**
  （`GalleryScreen.kt` 缺 import、`GalleryHomeSectionsTest.kt` 引用已被删的 `CHIPS`/`RECENT`）。
  判据层是纯 Kotlin，于是临时用 kotlinc + JUnitCore 单跑（`_probe/l0/run-judgment-tests.sh`），
  这样 RED/GREEN 不必等别人收口。**没有去改 dsh 那一屏的文件**。

### 验证

- 判据层 25 条全绿：`GalleryArtistLinksTest` 12 + `GalleryArtistUrlsTest` 6 + `GalleryArtistEndpointParseTest` 7。
- 全量单测 **510 条 / 0 失败 / 0 错误**（dsh 收口后跑通），`:app:assembleDebug` BUILD SUCCESSFUL。
- 首页文件本轮只在 `GallerySearchArea.kt` 的搜索卡内加了一排入口，**没有新增节**（拍板 7 + 锚定漂移那条口径）。

### 真机清单（用户点页面，我只读数）

1. yande.re 一位有 pixiv 的画师 → 详情面板那一行应出现**真头像**（这一步同时验 L4 的 Referer 生效）。
2. 换一个 pixiv 编号已失效 / 站方没给外链的画师 → 头像位是**首字母圆座**，不是空白、也不是转圈不止。
3. 平台入口最多两枚、pixiv 恒第一；那位只有 pixiv + fanbox 时第二枚是 FANBOX（不是"少一枚"）。
4. 点 pixiv/X/FANBOX 胶囊 → 外部浏览器打开的是**站方给的原串**。
5. 关注连点两次 → 名单不长出第二条；杀进程重进 → 名单还在，次序按关注时刻倒序。
6. 搜索卡空框态出现「关注的画师」一排，chip 前带 Y/G；点一枚 → 立刻按**那一站**那位开搜。
   ⚠️ **已作废（L11）**：这一排整个撤掉了，同一份名单只在首页那一栏摆。别再照这条点验。
7. 名单为空时那一排**整块不摆**（连"关注的画师"这个标题都不摆）。
   ⚠️ **已作废（L11）**，换成：首页那一节**照样摆**，内容是一句引导「在大图上点关注，这一栏会列出你关注的人」
   （节的数量恒定是懒列表锚点那条红线，撤一节会让整屏漂）。
8. Gelbooru 侧画师行：本机未通关 danbooru 时应只有"名字 + 关注"，一枚平台入口都不摆；
   `adb logcat -s GalleryArtistRows` 里要能看到那一句失败原因（403/卡盾），屏上不出现任何解释文案。
9. 手工把 `files/gallery_artist_follows.json` 改成非 JSON → 重进画廊应另存 `*.corrupt-<ts>` 并提示一句，
   而不是静默开一张空名单。
10. 两站同名各关注一次 → 搜索卡那一排应是**两枚**（带不同字母），点进去搜的是各自那一站。
    ⚠️ **已作废（L11 + M5）**：现在是首页那一栏**并成一条**、名字下**两枚站标并排**，
    点进去按这个名字**跨两站搜**（存储与身份仍是一站一条，见批次 M · M5）。

## 批次 L 增补（2026-09-30）：L9–L12

L1–L8 落地当天，用户接着提了三句话，逐条问定后做了四步：
「pixiv 的还是无法获取，你看下」「另外首页的收藏里的画师也改成正在关注的画师」
「另外，你看下能不能获取 yande 网站的图标，不用色块用图标」。

### L9 从「帖子自带的出处」派生画师入口与头像

新增 `gallery/domain/GalleryArtistAvatarProbe.kt`（判据层，可单测）+
`gallery/data/GalleryArtistProbeClient.kt`（取数）+ `GalleryArtistEndpointParse` 两个解析器。

只有两条派生路，而且是**逐档量过才留下的**（2026-09-30 下午，本机代理出口）：

| 平台 | 端点 | 实测 |
| --- | --- | --- |
| fanbox | `api.fanbox.cc/creator.get?creatorId=<子域>` | 带 `Origin` → 200，`body.user.iconUrl` 是 160×160；**不带 / 只带 Referer 一律 400 `general_error`** |
| Mastodon（baraag / pawoo） | `<实例>/api/v1/accounts/lookup?acct=<handle>` | 匿名直取 200 带 `avatar`，所以 `origin` 留 null |

于是"探针只给地址"是不够的 —— `GalleryAvatarProbeEndpoint` 带着 `origin` 与 `kind` 两个字段，
取数层照它决定带不带请求头、用哪个解析器，**不靠嗅 host**。

三条刻意不做 / 不放宽的：

1. **X / Twitter 没有匿名头像路**。站方 og:image 是占位图，公开代理 unavatar 回的是一张
   **568 字节的占位 SVG** —— "有值"但根本不是脸，摆上屏就是拿一张通用图冒充这位。
2. **Mastodon 实例走白名单**，不是"任何 Mastodon 域名"。派生出来的地址会被 app 直接发出去，
   而 OkHttp 那侧的 CookieJar 按 host 共享 —— 拿一条来路不明的 host 去请求，
   等于把该 host 上攒下的 cookie 一并带出去。目前只放进量过的两台，以后要加就得先量。
3. **`acct` 必须来自带 `@` 的那一段**。实例上 `/about`、`/explore`、`/auth/login` 同样是路径首段，
   少了这道门就会把站方页面名当账号去查，而 `acct=about` 在那台实例上真有账号时就是**认错人**。

两处实现上的坑（都写在 KDoc 里了）：

- `parts()` **手工切 URL 而不用 `java.net.URI`**：fanbox 的子域允许下划线（`a-b_c.fanbox.cc` 是站方真发的形状），
  而 `URI.host` 按主机名字符集校验，遇到下划线**整个返回 null** ⇒ 真出处被判成"认不出"。
- fanbox 的 `creatorId` 要的是**子域那一段**（`setmen`），不是 pixiv 用户号 ——
  同一端点传数字号实测回 400。所以 `www.pixiv.net/fanbox/creator/N` 那一条整条不回。

### L10 pixiv 作品页反查作者（胶囊带账号）

`PixivClient.artworkAuthor(illustId)` → `ajax/illust/{号}`，把作品号换成真用户号与账号。

存在的理由：出处常写成 `pixiv.net/artworks/N`，那串是**作品号** —— 拿它当用户号去要头像必然
`error=true`（实测）。`GalleryArtistRows` 里的次序是：先按"站方记录 + 出处"排一次，
**只有这一次里没有 pixiv 那一档**才值得再花这一笔请求。

`failure` = 作品被删 / 被锁（**实测 15 条出处里 4 条站方直接 404**）或读不懂 ⇒ 什么都不摆，
只在日志留话，屏上不出现"这位没有 pixiv"。这条与 L 批次那三条红线是同一条。

⚠️ **L10 推翻了 L9 KDoc 里的一句话。** 那句原文是"pixiv 也没法从作品号反查作者
（`/ajax/user/<作品号>` 回 `error=true`）"—— 当时只量了 `/ajax/user/`，**没量 `/ajax/illust/`**。
原句没有删，改写成一条教训留在 `GalleryArtistAvatarProbe.kt` 上：
**判"做不到"要量掉所有候选端点，不是量掉最像的那一个。**

### L11 首页那一栏换成「正在关注的画师」

新增 `gallery/domain/GalleryFollowedArtists.kt`（判据层）+ `GalleryFollowedArtistsTest`；
首页那一栏从「收藏里的画师」换成「正在关注的画师」，**搜索卡里那一排「关注的画师」整个撤掉**
（用户拍板"撤掉搜索卡那行"）—— 同一份名单不在两处长两样。

四条口径：

1. 卡面 = 那位名下**最近一张被收藏的图**，裁圆；`ContentScale.Crop` + 正方形容器，
   保证圆内始终填满不留白边（站方缩略图是任意比例，圆外像素会被裁掉）。
2. 张数是**真数**（"有几张你收藏的图带着这个名字"），不是编出来的更新数。
3. 关注了、收藏里却没有他名下的图 ⇒ **照样摆**，卡面换**首字母圆座**（用户拍板）。
   把他挑掉等于让"关注"这件事在没收藏他的图时凭空失效；而"0 张收藏"那行字**不摆** —— 没有的读数不占屏。
4. 一个都没关注 ⇒ 这一节**照样摆**，内容换成一句引导「在大图上点关注，这一栏会列出你关注的人」
   （用户拍板"摆一句引导"）。它说的是"你还没做"，不是"这里坏了"。
   节的数量恒定这条既有红线不受影响（撤一节会让懒列表锚点漂，见记忆「首页顶栏重叠的真根因」）。

旧实现 `gallery/domain/GalleryArtists.kt` + `GalleryArtistsTest.kt` 已镜像到
`_trash/gallery-artists-from-favorites-2026-09-30/`，**等用户点头才硬删**（可逆清理那条口径）。
它的 KDoc 里写着"这一栏刻意不叫「正在关注的画师」，仓库里没有任何关注数据源"—— 那是当时的真话
（L3 的存储层还没落地），今天已经作废，但**跟着文件一起进 `_trash`**，不在活代码里留一句自相矛盾的话。

### L12 站别徽标换成网站图标

新增 `components/venera/VeneraGallerySourceMark.kt`；**两处点位全换**（用户拍板"全站点位都换"）：
卡片角（`GalleryCardCaption`）与首页画师行（`GalleryHomeSections`）。
资产由 `scripts/build_source_icons.mjs` 从站方原档转出，两站各拿到什么**不对等，而这不是漏做**：

| 站 | 拿到的原档 | 落地形态 |
| --- | --- | --- |
| Gelbooru | 站上有 `layout/gelbooru-logo.svg`（360×360 单路径） | **矢量** `ic_source_gelbooru.xml`，任意密度都锐利。原档填充 #FFFFFF（为站方彩色页头设计），改成品牌蓝 #006FFA |
| yande.re | 站方**没有方形站标**，唯一可用的方图是 favicon 那枚 **16×16** —— 而它其实是一张裁自插画的粉脸（256 像素全不透明，主色是皮肤与头发色调） | **位图** `drawable-nodpi/ic_source_yandere.png`。摆到 24dp（本机 ≈72 物理像素）必然发糊 |

yande.re 那一枚是**知情选择**：把"只有 16px、放大必糊、它其实是一张粉脸"这三件事当面讲给用户之后，
用户仍选它（拍板"还是摆 16px 脸"）—— 这张脸就是 yande.re 在浏览器标签页上的标识，
**与站方一致优先于好看**。两枚同尺寸同圆角，并排时视觉重量一致；
差别只在"一个是矢量、一个是 16px 位图"，那是**数据面的事实，不是设计选择**。

三条实现口径：

- Gelbooru 那枚底下**垫固定白底**：站标 path 的形状是"实心 G"，直接压在封面上会因为透明度消失；
  白底既托住图形，也与它原本的设计底色（站方彩色页头）同族。
- `tint = Color.Unspecified`：那是站方的图，染成我们的主色就不是它了。
- **刻意不参与「界面材质 = 液态玻璃」这一轴**（与 `VeneraSourceBadge` 同一条）：
  它背后是颜色不可控的封面，底板存在的理由就是"压在任何图上都可读"。

⚠️ 这一条**收窄了 L 批次那条"不引站方标识"的口径**：现在它**只对文字位成立**
（`gallerySourceMarkText` 那排 Y/G 字母仍在最近搜索的 chip 上，那里只有两三个字符的宽度，摆图标会把关键词挤没）。
`GallerySearchSource.kt` 与 `GalleryArtistRows.kt` 上各有一段 ⚠️ 互相指路，
免得后人拿其中一条去否决另一处。

### 增补部分的自记错误

- 上面 L10 那条：把"量了一个端点不通"写成"这件事做不到"，是**同一类错误的第二次**
  （第一次是 L1 表格里"名字寻址可用"那条错报）。两次的共同点是**只量了最像的那一个候选**。
- L12 落地后有两处注释**当场漂了**（`GallerySearchSource.kt` 说 yande.re"只能用字母"、
  `GalleryArtistRows.kt` 说平台胶囊与首页字母徽标"同一条理由"），本轮写文档时逐字回核才发现，已改。
  教训：**换了实现就要 grep 一遍旧理由**，注释里的"同一条口径"是最容易过期的一类话。

## 批次 M（2026-09-30）：画廊首页六条真机反馈

用户原话六条（配两张截图：图一 = 参考的深色玻璃首页，图二 = 当前浅色首页）：

1. 「感觉图二的质感还是和图一差距有点大，图标太大了字体也大，整个页面要做 miuix 的悬浮玻璃风格的每组卡片层次分明，质感要更贴合图一」
2. 「取消掉整个画廊的下拉刷新，猜你喜欢的换一批不要整个页面刷新，只刷新下面推荐的内容就行」
3. 「之前做了上下滑收起顶和底栏，但是没有效果」
4. 「关注的画师 y 站和 g 站合并显示不要分站显示」
5. 「部分图片没有标题就不要完全空着，显示角色和其他 tag 了」
6. 「http://img.pixiv.net/img 这部分网站的画师出处有时候可以显示有时候无法显示」

第 3、6 两条是**缺陷**，第 2、4、5 条是**推翻既有判据**，第 1 条是**观感重做**。
根因与口径逐条记在 `gallery-home-round3-2026-09-30.md`（§1–§6 根因、§7 拍板、§8 落地顺序、§9 落地状态），
这里只记结论与红线。

### 四条拍板（AskUserQuestion）

| # | 问 | 定 |
| --- | --- | --- |
| 1 | 换一批重取期间推荐区怎么显示 | **保留上一批 + 压一层轻指示** |
| 2 | 关注画师跨站合并后徽标与点击 | **两枚徽标并排，点击 = 全部来源搜** |
| 3 | 卡片无标题时回退到什么 | **画师名 → 通用标签译名** |
| 4 | 首页尺寸收到哪一档 | **徽标 18 / 头像 44 / 标题降一档** |

### M1（第 3 条）：收栏没效果 —— 根因是取数位置，不是判据

`GalleryScreen.kt` 那枚 `NestedScrollConnection` 挂的是 **`onPostScroll`** 读 `available.y`。
post-scroll 的语义是"子级消费完之后剩下的那一截"，而首页那面瀑布流只要还能滚就把整笔位移吃干净
⇒ 到达这一层的 `available.y` **恒为 0** ⇒ `delta < 0f` 不成立、`accumulated + 0 >= threshold` 永远不成立
⇒ **永远不收**。反方向倒是通的（在顶部往上推时子级吃不下），所以屏上表现正好是
"只会展开、从不收起"= 用户说的"没有效果"。

第二处成因在同一枚连接上：它挂在 `topBarBehavior.nestedScrollConnection` 的**外面**
（链上靠后的更贴近子级），miuix 顶栏折叠在 pre 段就把位移吃掉 ⇒ 就算改成 pre 段读，
也要等顶栏完全折平之后才开始累计。

修法两条，**判据层一行不改**（`GalleryChromeHidePolicy` 含 8 条单测原地不动，符号与阈值口径都没变）：
改读 **`onPreScroll`** 的 `available.y`（那才是这一笔的真实位移）；
把这枚连接挪到**最内层**（它恒回 `Offset.Zero`，不抢任何位移，所以"谁先看到"决定"谁拿到原始值"）。

⚠️ 这一条的根因是**读码结论，不是实测读数** —— 只能靠真机验（清单第 1 条）。

### M2（第 2 条前半）：两层下拉刷新都撤

用户说的是"整个画廊"，所以两层 `PullToRefreshBox` 都换成普通 `Box`（各自的 `nestedScroll` 链保留）：
首页主墙（`GalleryScreen`）与每日热门二级页（`GalleryDailyScreen`）。

这与 2026-09-29 第四轮**加**下拉刷新时的理由（"同一天之内不再自动联网，得有个说得出口的动作"）
不冲突 —— 那个动作现在由两枚真按钮承担：猜你喜欢节头的「换一批」、二级页顶栏的「换一批」。

### M3（第 2 条后半）：换一批只刷推荐区

根因：`GalleryForYouViewModel.refresh()` 会 `posts = emptyList()` + `stage = IDLE`，
于是那一档"整屏波浪环"命中 ⇒ **三节跟着一起消失**，这就是"整个页面刷新"的观感来源。

改法：`refresh()` **不再清空 `posts`**（其余字段照旧清），旧卡片留在屏上；
"整屏波浪环"档只在 `posts` **真的是空的**时候才命中（冷启动第一轮）。

⚠️ 这一改的正反两面：不清 `posts` 就意味着**旧一批与新第一批之间有一个共存窗口**，
所以轻指示是必须的（用户看得见"正在换"），而且 **`seenKeys` 仍要清空** ——
否则新一批会被当成"已经摆过"而整批剔空，那会读成"换了一批结果啥也没变"。
这是"宁可错慢不可静默交错"那条口径在本轮的具体形状。

### M4（第 6 条）：裸图出处里的作品号

判据层把 pixiv 的**图床子域**一律判成 OTHER，`pixivArtworkId()` 对同一批 host 直接 `return null`。
那条口径本来是为"站方把 `img42.pixiv.net/img/tehu48/` 这种**目录列表**登记成个人主页"立的，没错；
错在把**带作品号的裸图**一起挡掉了。

实测（`curl -x http://127.0.0.1:7890` 取 yande.re `post.json?limit=100` + 设备 `gallery_feed_cache.json`）：

| 出处形状 | 条数（100 条里） | 改前判定 |
| --- | --- | --- |
| `www.pixiv.net/artworks/…`、`/en/artworks/…` | 26 | 认出作品号 → 反查作者 → **能显示** |
| `i.pximg.net/img-original/img/<日期>/<号>_p0.jpg` | 8 | **认不出** |
| `http://img.pixiv.net/img/…`（老式，用户截图里那种） | 本批 0，历史池里有 | **认不出** |

"认不出"的那批只有在这帖**恰好带站方画师标签**时才有画师信息 ⇒ 用户读到的就是
"同一类出处，有时可以显示有时无法显示"。

修法只放宽 `pixivArtworkId()`：host 是 pixiv 图床子域时，从**路径最后一段**取
`<数字>_<后缀>.<扩展名>` 里那串数字当作品号（`_p0` / `_square1200` / `_ugoira600x600.zip` 都算），
后面接的 L10 反查**一行不改**。

三条边界钉进了测试：**只看最后一段**（路径里 `img/2026/08/03/00/00/35/` 全是纯数字，
按"路径里第一串数字"取会取到日期）；`tehu48/` `xerd008ss/` 这类**目录串仍然一律 null**（旧口径继续有效）；
`fromSource()` **一行未动** —— 裸图**不是**"这个人"的主页，不能直接当入口摆，
它只是"要不要多花一笔反查请求"的闸门，反查回来的 `users/{id}` 才是入口。

### M5（第 4 条）：关注画师跨站合并

与批次 L 判据 1 正面冲突，用户当面推翻 ⇒ **显示层合并、存储层不合并**：

- `FollowedArtistRef` 由 `site: GallerySite` 改成 `sites: List<GallerySite>`（有货的那几站）；
- 合并键 = 名字**忽略大小写**（既有那条"不认大小写但摆原样"保留）；
- `count` 跨站合计，`previewUrl` 取两站里**最近一张非空**的收藏；
- 徽标：`sites` 里有哪站摆哪枚，**两枚并排**（拍板）—— 只摆一枚就等于把另一站藏起来，
  用户点进去看到两站的图会觉得莫名其妙；
- 点击：新增 `GallerySearchViewModel.acceptHandoffAllSites(tags)`（恒落 `GallerySearchSource.ALL`）。
  既有 `acceptHandoff(site, tags)` **不动**（大图页标签、搜索卡还在用），
  而且**只在一站关注过的人仍走旧入口** —— 另一站的同名那位本来就没被认下，
  替他扩大范围就是替用户做决定。
- ⚠️ 不碰保护域（`Navigation.kt` / `VeneraNavTab` / 路由映射 / 齿轮入口），本轮不新增目的地。

`GalleryArtistFollows` 的判据 1 原文**没有删**，加了一段 ⚠️ 指向 `GalleryFollowedArtists`，
写明"两处不是矛盾，是两层各自的选择"。

### M6（第 5 条）：无标题回退

先说一条事实（当面提醒过用户）：**角色档本来就在标题里**（现口径是「作品 · 角色」），
所以真正走到"没标题"的那批是**既无作品档也无角色档**的图 —— 用户说的"显示角色和其他 tag"里，
"角色"那一半其实已经生效，能新增的只有画师名与通用标签。

回退链改成四档，前一档有就停：作品 · 角色 → 只有作品 / 只有角色 →
**画师名**（词典 artist 档）→ **第一枚有中文译名的通用标签**。四档都没有才 `null`（整行不画）。

三条边界：**元数据档（5）永不进回退**；通用档**只在有中文译名时进表**
（没译名就只剩 `shirt_lift` 这种原词，比空着还难读）；画师档**没译名则摆原词**
（专有名词，音译一个反而没人认得出）。数据面 `GalleryTagDictionary.titleTags()` 相应放宽到四档，
分批与"打不开回 null"的既有口径不变。

`GalleryCardTitle` 判据 4 原文（「一个都没有 → null，**不拿通用标签或画师名顶包**」）**没有删**，
原地写明推翻它的那句用户原话与当初为什么那么定。

### M7（第 1 条）：尺寸与层次

三个数字，全部有来由（`Spacing.kt` 里逐条写着）：

- `sourceMarkSize` 24dp → **18dp**（取现成的 `badgeIconSize` 同值）。
  ⚠️ 这是一枚**共用 token**，搜索结果网格的卡片行也跟着变小。刻意不另立"首页专用一档"：
  同一枚站标在两处尺寸不同，读起来像漏改。
- `artistAvatarSize` 56dp → **44dp**、`artistAvatarSlotWidth` 88dp → **72dp**（同比例收，名字仍读得出一行）。
  44dp 是**本轮唯一的新数字**（现成档只有 34dp 的 `sourceAvatarSize`，跨度太大），理由写进 token。
- 画师名与卡标题字号 `type.caption` → **`type.badge`**（降一档）。

层次：三节各自套一层背板（新增私有件 `GalleryHomeSectionCard`，里面就是 `VeneraCard`）。
⚠️ 这**推翻**了第二轮那条「节与节之间不加分割线、不加分区底色」的结构决定，
理由就是用户这轮的原话「每组卡片层次分明」；旧文字留在 KDoc 里没有删。

走 `VeneraCard` 而不是裸 miuix Card 的理由：全站分组卡的玻璃判据在
`veneraGlassSurface` / `veneraGlassCardColors` 那一处，这里不留第二份判断 ——
**材质轴开着是玻璃、关着是实色，两档都有背板**（否则"层次分明"就变成只在一个开关下成立，那是一种假开关）。

### 与拍板文字不一致的一处（如实记）

拍板 1 的选项文字是"保留上一批 + **压一层轻指示**"，我当时在选项里写的是
"整片压一个半透明遮罩 + 小波浪环"。**实际落地的是节头「换一批」旁一枚 `loaderInline`(28dp)
波浪环 + 芯片禁用**，没有整片遮罩。

理由：仓库里没有可用的遮罩透明度口径 —— `maskScrimAlpha = 0.65f` 的语义是**封面打码**、
`selectedSurfaceAlpha = 0.5f` 是**次级表面**，拿任一个来压推荐区都是把语义不同的 token 挪位；
新造一个数字又违反"观感尺寸用现成口径"。而指示摆在节头正是**用户刚点下去的位置**，
"这一节正在换"这件事说得比整片遮罩更准，也不挡住已经摆好的那批图。
真机若嫌它太弱，改法是把这一枚环换成推荐区顶部一条进度条量级的指示，**而不是回头去造遮罩 alpha**。

### 验证

- 判据层三处**先红后绿**，红灯读数逐条记在 `gallery-home-round3-2026-09-30.md` §9 的表里
  （M4 `expected:<147950802> but was:<null>`；M5 `Unresolved reference 'sites'` ×2 —— 新 API，编译级红；
  M6 四条 `AssertionError`）。M1–M3、M7 没有判据层可红（接线与观感），验证只能落在真机清单上。
- 全量单测 **545 条 / 0 失败 / 0 错误**，`:app:assembleDebug` 通过（APK 已出）。
- 判据层独立跑法仍是 `_probe/l0/run-judgment-tests.sh`（本轮收录 4 个测试类共 43 条）。
- **装机未完成**：`adb devices` 空列表，等设备接上。

### 本轮踩到的一处工具坑（会影响后面所有人的编辑）

**显示层会把日期形路径里的 `/` 渲染成 `-`**：`img/2026/08/03/00/00/35` 在 Read 与 grep 的输出里
都显示成 `img/2026-08-03-00-00-35`，磁盘上是真斜杠。后果是 Edit 的 `old_string` 若照着屏幕抄，
多行匹配**恒 0 处**，而且看起来像"文件里没有这段"。
排查时先后怀疑过 CRLF、中文字符、多行匹配，全是错的方向；**只有 `od -c` 给出的是真字节**。
修法：`old_string` 里**手写真斜杠**，落盘后用 `grep -c "img/2026-08-03-00-00-35"`（=8）反向验证。

### 真机清单（用户点页面，我只装机与读数）

1. 画廊设置里打开两枚"滚动收起"，下滑过搜索框 → 顶栏与底栏**各自**收起；上滑任意幅度 → 立即回来；双击空白 → 复位。
2. 首页与每日热门二级页都**不再有**下拉刷新那一圈；"我要新内容"只剩两枚「换一批」。
3. 点「换一批」→ 三节不动、上一批卡片留着、节头出现那枚小波浪环且芯片禁用；新数据到了才整批替换
   （**不许**出现"换了一批结果啥也没变" —— 那是 `seenKeys` 没清的症状）。
4. 关注一位两站同名的画师 → 首页只出现**一条**、名字下**两枚徽标并排**；点它 → 按这个名字**跨两站搜**。
   只在一站关注过的人 → **一枚**徽标，点进去仍只搜那一站。
5. 找一张既无作品档也无角色档的图 → 卡上摆**画师名**（或通用标签译名），不再只剩一枚站标；
   四档都没有的图仍然**整行不画**（不是摆一行空白）。
6. 出处是 `i.pximg.net/…/<号>_p0.jpg` 或 `http://img.pixiv.net/img/…` 的图 →
   「关于这张图」里出现**带账号**的 pixiv 胶囊；删帖 / 锁帖仍会没有，
   那一档已如实留日志（`adb logcat -s GalleryArtistRows`），屏上不出现任何解释文案。
7. 首页整体观感对照图一：徽标 18、头像 44、标题降一档、每节一层背板（材质轴开 / 关两档都要看）。
   ⚠️ **这一条的三个半句当天就被批次 N 推翻两个**：「每节一层背板」撤掉（表面下放到条目）、
   「标题降一档」回改（badge → caption）。徽标 18 与头像 44 留着。
   照这条点验前先读 `gallery-home-round4-2026-09-30.md`。
8. 搜索结果网格的卡片行也跟着变小了（`sourceMarkSize` 是共用 token）—— 这一处**是刻意的**，
   若真机读起来太小，改法是给首页单独立一档，而不是把共用值改回去。

## 批次 N（2026-09-30 下午）：两条被真机读数推翻的口径

批次 M 落地装机后用户两句反馈，各暴露一条根因：
「我之前设置有个下滑收起顶底栏，**搞反了**，应该是上滑收起」；
「**开了玻璃就是这样，感觉就是不如图一那种**」—— 后一句直接推翻了"打开材质轴就能接近参考图"这个前提。
取证、实测读数、改法与**没解决的部分**全记在 `gallery-home-round4-2026-09-30.md`，这里只钉结论。

### N0 收栏方向：符号假设错了，而测试把它钉住了

`androidx.compose.material3` 1.5.0-alpha22 `AppBar.kt:3899` 的
`// Don't intercept if scrolling down.` 配 `if (available.y > 0f) return Offset.Zero`，
而收起要求 `heightOffset` 往负走 ⇒ **负 = 手指向上滑（往下浏览）、正 = 手指向下滑（回滚）**。
旧判据正好用反（上滑展开、下滑收起）。同一条错假设写在四处（policy 类头、`@param delta`、
`GalleryScreen` 的连接注释、**测试类注释**）—— 测试当时按错约定写，所以它一直绿着把缺陷钉在屏上。

- TDD：先把 7 条用例按取证方向改号 → **3 条红**（`过了阈值就该收` / 收不起来 / `已经收着就保持收着`），
  三条红的都是"方向反了"这一件事 → 改 `afterScroll` → 7 条绿。
  另外 4 条（零位移、复位、成对清、小幅来回）在两种约定下同形，所以不红。
- 顺带纠正批次 M §1 的一处错报：旧写法（`onPostScroll`）的屏上表现**不是**"只会展开、从不收起"，
  而是"日常滑动整条判据根本不参与"。M1 那次只修了取数位置（对的），符号没动，所以修完仍然反。
- 文案：两枚开关 → 「上滑时收起顶栏 / 底栏」，说明一律改写成不会读反的说法
  （"上滑/下滑"本身就有手指方向与内容走向两种读法，这轮翻车的语言面成因）。
  **键名没跟着改** —— 改了会让已打开过这一枚的用户静默回到默认档。

### N1 玻璃档在内容区不产生提亮：抬亮量由"叠哪个色"决定

玻璃档实测：页面底 rgb(20,19,24) → 节背板 rgb(22,21,27)（**+2**）→ 条目卡边缘 rgb(27,26,31)（**+7**）；
同屏顶栏/搜索框 rgb(47,45,58)/(43,41,54)（**+27/+23**）。参考图：底 rgb(6,13,26) → 卡 rgb(27,34,62)（+21/+21/+36，冷蓝）。

CONTAINER 档原先容器色与染色**都取 `surface`**，而这套深色方案的 `surface` 与页面底是同一个近黑
⇒ 模糊近黑再叠 0.28 的近黑 = 零提亮。反证在同一屏：chrome 亮是因为它走 `surfaceContainerHigh`。

- 改法只换槽位：CONTAINER 档改取 `surfaceContainerHigh`；**三个 alpha（0.28/0.22/0.16）一个都不动**
  （它们是 `VeneraLiquidGlassNavBar.kt:119` / `VeneraTopAppBar.kt:100` 上真机验证过的读数）。
- CONTROL / INLINE 两档**不跟着抬**：芯片按钮一屏二十个，要的是与所在面板区分，不是比页面亮一档。
- `veneraGlassCardColors()` 的**实色档**不再回落 `CardDefaults.defaultColors()` 的近黑，显式给
  `surfaceContainerHigh` —— 这一支才是本轮真正吃到 +27 的地方；只在玻璃档加层次就是假开关。
- ⚠️ **没解决的部分如实记**：换槽位后玻璃档抬亮只到 ≈ +7.5（alpha 才是那一档的上限）。
  要往参考图量级走只有两条路，本轮都没走：抬容器 alpha（与"玻璃要透"那条读数直接冲突）、
  或改录制层 `Navigation.kt:547` 的 `surfaceBase`（**保护域**）。留给真机读数再拍。
- 另一条不属于本轮：参考图是深蓝底 + 冷蓝卡，我们是暖黑底 + 紫氛围光 —— 那是主题种子色的事，
  换槽位解决"抬多亮"，解决不了"色相偏暖"。

### N2 分层方向：表面该在条目上，不在节上（推翻 M7）

参考图没有"节"这一层表面，抬亮发生在**每一条**上。M7 把背板加在节上同时错两处、而且**两节错得还不一样**：
每日热门 = 卡里嵌卡（两层实测只差一小截，糊成一层）；正在关注的画师 = 节卡里裸摆条目（一排图标，不是四个人的卡）。

- 撤 `GalleryHomeSectionCard` → `GalleryHomeSection`（只剩宽度与节间距）；画师行每位一张 `VeneraCard`；
  海报行本来就有卡、**一行不改**；猜你喜欢那节只剩裸节头（它下面那条墙就是它的全量，套不进卡）。
- M7 那段"一节一块背板是分组在屏上唯一的说法"**没删**，原样搬进 `GalleryHomeSection` 注释，后面注着被谁推翻。
- 三节的**数量与次序恒不变**这条红线未动（锚定漂移），只改分支内部。
- 骨架 `GalleryRowSkeleton` 改成套同一个 `VeneraCard`：实卡多出上下两层 8dp 内衬，
  骨架不套同容器就差 32dp，到货那一帧仍然跳。
- **自记一处算术错**：计划里写"72dp 槽 − 16dp 内衬 = 56dp，两枚站标 38dp 装得下"——
  漏算了同一行的「N 张收藏」文字（一枚站标 + 那串字 ≈ 60dp，**连一枚都装不进 56dp**）。
  于是 `artistAvatarSlotWidth` 72 → **88dp**（M7 之前的原值，登记过的回摆、不是新数字）。
  两枚站标那位在 88dp 下仍会挤掉一两个字，改法是"两枚时不摆张数"而不是再放大这一格。

### N4 字号：M7 降过头，回一档

M7 把卡内标题与画师名从 `caption`(13sp) 一次降到 `badge`(11sp)。用户当时说的是"图标太大、字体也大"，
**我把两样一起降了**。图标那半（24→18、56→44）是对的、留着；字号这半回 `caption`。
两处旧注释里 M7 的降档理由原样保留并注上被本轮推翻。「N 张收藏」仍留 badge（补充读数，不是署名）。

### 验证

- 判据层：`GalleryChromeHidePolicyTest` 7 条**先红后绿**（红的读数抄在上面，三条、全是方向反了）。
- N1/N2/N4 **没有判据层可红**（无 Robolectric，`GalleryHomeSectionsTest` 只落在 key 与次序上）——
  纯真机验收项，没有硬造断言，只在该测试类里写明"背板撤除属布局层，此处测不到"。
- 全量单测 **545 条 / 0 失败 / 0 错误**（70 个结果文件），`:app:compileDebugKotlin` 与
  `:app:assembleDebug` 通过（APK 17:42）。**装机未完成**：`adb devices` 空列表。

### 真机清单（页面由用户点，我只装机与读数；材质两档各拍一张）

1. **收栏方向**（N0 硬指标）：向上滑（往下浏览）累计过搜索框位置两栏各自收起；向下滑任意幅度立刻回来；
   双击顶栏复位；且与 miuix 顶栏自带折叠**同向**、不再互相拉扯。
2. **抬亮比**（N1 硬指标）：PowerShell 取页面底与条目卡面。**实色档目标 ≥ +18**（改前 +12）；
   玻璃档预期只到 ≈ +7.5。**实色档测不出抬亮就说明 N1 做成了假开关。**
3. 每日热门不再卡中卡；正在关注的画师每位一张卡；猜你喜欢的节头不再像孤立药丸。
4. 加载 → 到货那一行不跳高（骨架与实卡同容器）。
5. 画师名 + 「N 张收藏」在 88dp 格里：一枚站标那位读得完整；两枚站标那位挤掉几个字 ——
   要不要"两枚时不摆张数"，看完再拍。
6. 卡内标题回到 13sp 后与 18dp 站标的比例是否顺眼。
7. 从详情页返回：内容不画进 `contentPadding`（锚点未漂）。
8. **顺带扫设置页**：`veneraGlassCardColors()` 是全站分组卡的同一处判据，那边会一起抬亮。

### 没动的两处，等读数再拍

- 节头没有图标位（`MiuixSectionHeader` 只有 title + trailing），而参考图两节各有一枚；
  「查看全部」是描边药丸、是整节最亮的元素。抬亮之后它可能就不抢了 —— 先看截图再定。
- 卡下第二行热度读数（参考图的 🔥12.4k）：用户拍"这轮先不加"。`score` 是真有的
  （`_probe/day_posts.json` 实测 yande.re 80/64/63/52…），但 Gelbooru 有 `score:0` 的帖子，
  加之前得先钉"0 就不摆"。

## 批次 O（2026-09-30 晚）：切回画廊 Tab 就整页重取

用户原话：「每次进入画廊整个页面都要重新加载，你整个上次已经加载好的数据，不刷新的话就不要主动刷新新内容了」。
取证、拍板与三条落地口径全在 `gallery-tab-reload-2026-09-30.md`，这里钉结论。

### 根因（两条叠在一起，缺一条都不会有这个症状）

1. **结构因**：切 Tab 走 `gotoTab` = `popUpTo(startDestination){saveState=true}`，画廊那条目的地是**被 pop 掉的**。
   `androidx.navigation` 2.10.1 `NavBackStackEntry` 类头 KDoc 明写 pop 时
   "the lifecycle will be destroyed, state will no longer be saved, and **ViewModels will be cleared**"，
   而 VMStore 按条目随机 `id` 取 ⇒ **`saveState`/`restoreState` 只救 `rememberSaveable`，救不了 `viewModel()`**。
   切回来那四个画廊 VM 全是新实例。
2. **这一屏从来没接上同日门**：日榜那头 2026-09-29 就为同一件事存过快照 + 配了
   `shouldAutoLoad()`，**猜你喜欢漏了** —— 无缓存、无门、无节流，`loadedKey` 只是"同一次组合"的守卫，
   新 VM 上恒不成立。外加 `seed = System.currentTimeMillis()`，新 VM 连推荐标签都重抽一遍
   ⇒ 用户读到的不只是"重新加载"，是"内容还换了一批"。

**一句概括：这不是回归，是 09-29 那条既定口径（"同一天之内不再自动联网"）当时只落在日榜那一屏。**

### 三条拍板（AskUserQuestion）

| # | 问 | 定 |
| --- | --- | --- |
| 1 | 修法 | **A：照日榜那套补快照 + 同日门**（不碰保护域、判据可单测） |
| 2 | 有效期 | **同一天不自动取**，跨天补拉一次 |
| 3 | 搜索上下文栈一起治？ | **先不动**，本轮只治首页重取 |

否掉的两条留档：**B** 把画廊 VM 提到壳层常驻（取 owner 要动保护域 `Navigation.kt`，两站池子常驻内存）；
**C** 改 `gotoTab` 不弹栈（一处改完五个 Tab 都不重建，但返回栈语义/返回键/底栏 `currentTab` 判据/切 Tab 动画全跟着变）。

### 落地时定死的三条口径

1. **`seenKeys` 不落盘，从恢复出来的那一屏重算** —— 它本来就是从这些 post 累出来的，
   存第二份等于给"两份不一致"留位置（存了旧键、屏上换了新条，去重就会漏剔或误剔）。
2. **空快照一律不算"已铺好"** —— 这一条比看上去重要：空屏若被认成已铺好，同日门恒关，
   **一屏空白而门是关着的、永远不再取**，那比"每次都刷新"坏得多。落盘那头只存在到内容的那一轮，这是第二道闸。
3. **`stage` 也要铺成 READY** —— 只恢复 `posts` 不恢复 `stage`，页面第一帧念 IDLE，
   而 IDLE 那一档摆的是整屏加载环，快照就白存了。

另两条形状选择：`init` 读盘必须**同步**（异步读抢不过"这一轮要不要联网"那次判断）；
"按站一份"的几列存**行式** `List<GalleryForYouSiteRow>` 而不是 `Map<枚举, String>` ——
不押 kotlinx 对枚举键的编码口径，且站点认不出时可逐行摘掉而非整档作废。

### 自记错误（两处，都是编译抓的）

- **`init` 块放错位置**：先写在 `generation` 之后，而要赋的 `seenKeys`/`sitesDone` 声明在更下面 ⇒
  `Variable cannot be initialized before declaration`。教训：**这个类里已经有一个 `init`（订阅 Gelbooru 账号），
  多个 init 块是按声明位置串起来的，不是随便放哪都一样。**
- **两个 import 漏了**：`kotlinx.serialization.json.Json`（照抄 `GalleryFeedCache` 时漏一行）
  与 `toPost`/`toFavorite`（那两个扩展在 `gallery.data` 包里，日榜同包不用 import，VM 在 `gallery.ui` 就得显式引）。

### 验证

- 判据层 8 条**先红后绿**（红是编译级：`Unresolved reference 'GalleryForYouRefreshPolicy'`，新 API 不可避免，
  与批次 M · M5 同一档）。
- 全量单测 **553 条 / 0 失败 / 0 错误**（545 + 新 8），`:app:assembleDebug` 通过（APK 20:42）。
- **装机未完成**：`adb devices` 空列表。

### 真机清单（用户点页面，我只读数）

1. **主判据**：进画廊取到内容 → 切首页 → 切回 ⇒ 不转圈、还是那一屏、次序没变；
   `adb logcat` 这一轮不该出现画廊取数的网络日志。
2. 「换一批」**仍然真换**（同日门不许把它拦成假按钮）：节头出小波浪环、芯片禁用、新第一批到货才整批替换。
3. 翻两三页到底 → 切走切回：停在**已取到的那一屏**（不是退回第 1 页），
   页尾"累计 X 张 / 已排除 N 张"**没归零**。
4. 跨天首进补拉一次（这是对的，不是 bug）。
5. 手工把 `files/gallery_for_you_cache.json` 改成非 JSON → 应**照旧联网取一次**，不是停在空屏。
6. 冷启动第一帧不该是整屏加载环（同步读盘）。
7. 双源退化成一源那句**可见**提示，切回 Tab 之后**还在**（`failures` 存进快照了）。

### 本轮没治的同一根因症状

搜索那面墙的上下文栈（`GallerySearchViewModel`）切 Tab 全丢 —— 用户拍"先不动"。
---

## 批次 N（2026-09-30）：冷启归因实测 —— 优化计划里四条被量掉，只落一条

### 起点

用户给的《Venera-Compose 性能优化计划（基于真机三页面滑动复测）》，主张：冷启首页是唯一真卡，根因是
`VeneraApp.onCreate` 的同步初始化链（网络引擎 / 内容守卫 / 源管理器），并把 A1–A3 记作 "~60% 收益"。
按"先量再改"的口径开工：先在启动链每一段插打点，再拿真机读数逐条对账。

### 实测读数（PJZ110，同一份用户数据；协议 = force-stop → 1.5s → gfxinfo reset → `am start -W` → 睡 3s → dump）

| 形态 | TotalTime | 帧数 | janky | 50th | 99th | GPU 99th |
|---|---|---|---|---|---|---|
| debug 基线（4 轮） | 973 / 1017 / 1012 / 1055ms | 8–11 | 63–75% | 109–150ms | 550–600ms | 8–14ms |
| debug + `cmd package compile -m speed -f` | 744 / 796 / 813ms | 8 | 75% | 113–150ms | 550–600ms | — |
| **release（真用户形态）** | **260 / 263 / 283 / 304ms** | 8–9 | 37–62% | 11–32ms | **125–150ms** | 8–22ms |
| release + 本轮基线画像 | 261 / 275 / 280 / 302ms | 9–159 | 4–44% | 10–29ms | 101–200ms | — |

打点分段读数（debug）：`App.onCreate` 全程 **74–95ms**；其中 `VeneraNetworkClient.getInstance` **61–77ms**
（`PersistentCookieJar()` 33–50ms，全是 `loadFromPrefs` 的 prefs 全表读 + 每 host 一次 gson；`buildClient()` 14–24ms）、
`ContentGuardManager` **5ms**、`ComicSourceManager` **8–13ms**。首帧窗口：主题色就绪 +203~236ms →
首个 comic 判定 +378~412ms（主线程组合期内）→ **first traversal +880~921ms**。

### 真因（两条硬测量）

1. **主线程状态**（atrace `sched`，进程主线程 tid 全量重建）：那 1280ms 窗口里
   **running=1147ms、runnable(等 CPU)=4ms、sleep(阻塞)=128ms** ⇒ 不是抢不到核，也不是等 IO，是它自己在跑代码。
2. **它在跑什么**（simpleperf，`--call-graph fp`，主线程 2863 样本）：
   **82.7% 周期在 `libart.so`**（`ExecuteSwitchImplCpp` 17%、`DoCall` 8.3%、`MaybeDoOnStackReplacement`+`MethodEntered`+`PrepareForOsr` 11%、
   `DexFileVerifier::Check*` / `ClassLinker::LoadClass/FindClass` / `MethodVerifier::Verify*` 若干），
   真正画东西的 `libhwui.so` 只有 **0.8%**。全进程 CPU：主线程 32% / **Jit thread pool 28%** /
   DefaultDispatch 14.5%（QuickJS `init+loadStandardLib` 实测 1050–1401ms）/ **EmojiCompatInit 3.3%** / RenderThread 2.4%。

⇒ debug 包不能 AOT，冷启那点卡**主要是打包形态的成本**；业务代码在这条链上的占比远小于计划里的估计。

### 被量掉的四条（保留计划编号，逐条附读数）

- **A2 守卫装载时机**：全构造 **5ms**（`loadRules` 1–2ms、`loadSourcePresets` 1–2ms）。更关键的是
  `Guard: first coverMaskStateFor(Comic)` 落在 **+378~412ms 的主线程组合期内** —— 被动 `ensureLoaded()`
  不是把这 5ms 搬走，是把它**搬进首帧**。另有一条真风险：`rules` 这条 StateFlow 被
  `UnifiedExploreScreen.kt:124` 与 `SourceSectionScreen.kt:105`（均 FROZEN）收集，纯延迟会让它们在第一次判定之前看到空规则表。
  ⇒ **不做**，用户给的豁免未动用；只留打点。
- **A3 内置源构造**：三个源构造合计 **1–2ms**（`MangaDexSource/CopyMangaSource/BaoziMangaSource` 的 `networkClient` 本来就已 `by lazy`）⇒ 零收益，不做。
- **A5 collect 协程延后**：JS 抢 CPU 这条被 **E1 实验证伪**（把 `loadInstalledJsSources` 整段推迟 4s：
  first traversal 886/919/921ms vs 基线 902ms，TotalTime 1071/1101/1015 vs 973–1055 ⇒ 无变化）。
  `FollowUpdatesScheduler.enable` 在本机从未触发（`followUpdatesFolder=null` 走 disable 分支，无读数）。
  ⇒ 不为它延后任何东西；且别名注册那条 collect **不能**延后（历史卡存源显示名，延后=把成人源的打码窗口拉长）。
- **C2 收藏流改造**：`HomeVM.getAllComics` 实测 cost **157–331ms**，起点 +625~+751ms、结果落在 +908~+986ms
  （正好顶在首帧那一刻），看起来很像成因。但把设备上的 `local_favorite.db` 拉下来用 node:sqlite 读数：
  **只有 `默认` 一个收藏夹、0 行数据** ⇒ 这 200–300ms 全是"首次开库 + 冷进程首次走代码路径"，不是查询量。
  另外计划点名的 `favoritesManager.favorites` **不存在**（数据层只有 `folders`/`counts`/`version`，
  `getAllComics()` 是 suspend 且内部已 `withContext(Dispatchers.IO)`），"组合期间同步全表扫描"这个机理本身不成立。⇒ 不做。
- **B2 / C1**：首帧之前没有导航发生（`currentTab` 的 derivedStateOf 只影响切页）；LazyColumn 的 `key` 不影响首次组合。
  且 B2 按计划写法（`derivedStateOf` 块内读已在外部取好的 `destination`）会**永久读到第一次组合的旧值**，
  要写对必须块内读 `backStackEntryState.value`。⇒ 对冷启零收益，撤下；切页/复用另开一轮。

### 落了的一条（唯一有凭据）

`VeneraNetworkClient`：`cookieJar` / `prefs` / `OkHttpClient` 三条改延迟构造，由 `VeneraApp` 在 IO 线程预热
（不预热的话这笔钱改由首图那次 `newImageLoader` 在主线程付）。读数：`App: VeneraNetworkClient.getInstance`
**61–77ms → 2–3ms**，`Application.onCreate` 全程 **74–95ms → 27–28ms**，TotalTime −20ms（在噪声边缘，不写成收益）。

两条刻意不跟着延迟的理由写在代码注释里：

1. `UserAgentPolicy.init` 留在构造期 —— `CloudflareBypassManager` 写 host 绑定 UA 那条路不保证先碰过
   OkHttpClient，而未 init 时它的 `prefs` 是 null，`setCustomUserAgentForHost` 会只在内存记一笔、**静默丢掉持久化**
   （`cf_clearance` 配不上 UA，正是本文件 2026-09-26 那条"从来没生效过"的同型坑）。
2. `httpCacheSizeBytes()` / `clearHttpCache()` 改走 `okHttpClient` 取值器 —— 读 null 会报"缓存 0 字节"的假读数。

### 试了又撤的一条：基线画像

机制走通：把真机 ART 自己记录的 startup profile 用 `profgen dumpProfile` 导成 HRF，落成
`app/src/main/baseline-prof.txt`（31294 行，自有类 725 条 + compose/miuix/materialkolor/coil 等），
`compileReleaseArtProfile` 正常产出 `assets/dexopt/baseline.prof`。**按"新装用户第一次冷启"口径实测无收益**
（见上表第 4 行，与未加画像的 release 完全重叠），且编译后的画像反而从 16129B 缩到 14697B
（有顶掉库 AAR 原有画像的风险）。⇒ 文件已移 `_trash/baseline-prof.measured-no-gain.txt`，
重生成三步写在它的文件头注释里。手写 `src/main/baseline-prof.txt` **不需要新依赖也不需要 macrobenchmark**，
这条已查实，后续要重启很便宜。

### 计划里"验收口径"的两条订正

- `首屏 3 秒 Total frames rendered ≥ 25`：帧数不是流畅度指标。App 画完首屏就空闲，release 上
  8–9 帧是正常的；同一包若推荐区在窗口内到货就是 149–159 帧。判据应看 50th/99th 与 janky 比例。
- `TotalTime ≤ 930ms`：release 天然 260–304ms，debug 天然 970–1090ms，用哪一包当分母要先说清。

### 交互基线（用户点页面，我只读数；同一轮 = 4 次切 Tab + 首页快滑 8 下）

debug：1407 帧 / janky **6.54%**（legacy 45.91%）/ 50th 11ms / 95th 32ms / **99th 77ms**，
尾部 125ms×5、133ms×2、150/200/300/350ms 各 1。⇒ 切页与快滑这一面是顺的，legacy 高是算法对接近 60Hz 帧过敏感。
**release 侧同一轮还没跑**（包已换过，需要用户再点一轮）。

### 测量边界（下一个人别再撞）

- `app/proguard-rules.pro:74` 有 `-assumenosideeffects class android.util.Log { v,d,i }` ⇒ **release 上 VeneraStartup 整条消失**，
  只能用 `am start -W` + `dumpsys gfxinfo` 两个数。
- 非 debuggable 让 `run-as` 与 `simpleperf` 都进不去 ⇒ 函数级采样只能做在 debug 包上。
- Git Bash 里 `adb pull /data/...` 会被路径转换吃掉，要写 `//data/...`；`atrace -z` 输出是 gzip 且头两行是文本头，
  直接去掉 `-z` 拿文本最省事；`profgen dumpProfile --output` 不给带目录的路径会 NPE。
- 数据安全性：`install -r` 同签名可来回切 debug/release，**不丢数据**；本轮全程用 `-r`，
  并在切换前用 `run-as PKG tar` 备份过 `files/databases/shared_prefs`（18MB，`_qa/startup-baseline/appdata-backup.tar`）。
  注意反向不成立：一旦装成 release，`run-as` 就进不去、备份通道也关掉了。

### 冻结面与被碰记录

- **ContentGuardManager.kt（FROZEN，用户点名豁免"A2 装载时机"）**：最终**只加打点**
  （`timed` 包 `loadRules`/`loadSourcePresets`、`once` 探三个判定入口首次调用）。判定链、规则初值、
  verdictCache、预编译正则、`explicitPatterns` 一律未动。豁免给了但没用作语义改动，A2 本身撤下。
- **HomeScreen.kt（FROZEN）**：**一行没动**（C1 撤下，未申请豁免）。
- 其余改动都在非冻结文件，且除 `VeneraNetworkClient` 那三条 lazy 外全部是日志与耗时记录：
  `VeneraApp.kt`、`MainActivity.kt`、`PersistentCookieJar.kt`、`VeneraPreferences.kt`、`ComicSourceManager.kt`、
  `HomeViewModel.kt`、`VeneraTheme.kt` + 新文件 `StartupTrace.kt`。
  其中每条 JS 源解析的耗时打点（`SourceMgr: parse jm.js cost=…`）就是计划 **A4** 要的常驻诊断。
- 未引入新依赖（基线画像那条最终撤了）。

### 验证

`:app:testDebugUnitTest` **559 条 / 0 失败 / 0 错误**；`:app:assembleDebug` 与 `:app:assembleRelease` 均通过。
读数与 trace 原件在 `_qa/startup-baseline/`（`trace-*.txt`、`gfxinfo-*.txt`、`e1-*`、`rel-*`、`relbp-*`、
`atrace-cold2.txt`、`sp2.data`、`parse-sched.cjs`）。

### 未做 / 待拍板

1. release 侧"切页 + 快滑"复测还没跑（要用户点页面）。
2. 收藏页快滑里那 2 次 200ms 尖峰：不动 FROZEN 屏结构无解 —— 仍挂账。
3. `EmojiCompatInit` 在启动窗口占 3.3% CPU（androidx.emoji2 自启），值不值得查未定。
4. `StartupTrace` 是否常驻：本轮倾向留（它就是 A4 要的常驻诊断，release 上日志被自身规则抹掉、只剩取时间戳）。
5. 若真要把 debug 包的冷启也压下来，方向只剩"**首屏少碰类**"（少解释执行一些）与"**冷库/冷 dex 提前在后台摸一遍**"，
   两者都不在原计划范围内。

---

## 批次 P（2026-10-01）：全项目代码深挖 —— 行为缺陷 + 死代码 + 去 AI 味

详档：`code-audit-batch-p-2026-10-01.md`。**与上一轮（首屏性能）零交集**：
`VeneraApp.onCreate` / `MainActivity.kt` / `VeneraApp.kt` 一行未碰。

### 落地的三条行为缺陷

1. **P1** `VeneraNetworkClient` 的 `get`/`post`/`downloadBytes` 不判 HTTP 状态 —— 403 的挑战页被当正文
   交回上游。最坏的一条不是"源解析出错"，是**画廊另存会落盘一个叫 `.jpg` 的网页而提示说"已保存"**。
   判据抽成纯函数 `data/network/HttpBodyVerdict.kt`（非 2xx 抛 `HttpRejectedException`，
   文案带方法 + **去掉查询串**的地址 + 状态码），三处补 `use`。
   **随的是本仓自己的既有约定**：`DownloadManager`、`VeneraImageFetcher`、`ImagePipelinePolicy`、
   `AppUpdateChecker`、`WebDavClient` 六处、`ComicSourceViewModel` 早就都判了。
   波及面量清：**19 处调用 / 5 个文件**，全部包在 `try{}` / `runCatching{}` 里 —— 没有把任何空白变成崩溃；
   JS 源那一整条路（`JsHttpHandler` 自己拿 `okHttpClient`）**不受影响**，别当成"全网关都修了"。
   错误确实上屏：`SourceSearchResult.error` → `SearchScreen.kt:1030` 直接渲染。
2. **P2** 正则可编译性判据抽到 `security/guard/GuardRulePattern.kt`（用 `match()` 真正会用的那组选项编译一次），
   `BlockingSettings` 改调用；**恢复备份那半边原来没拦** —— `BackupManager` 现在跳过编译不过的行，
   条数经新增的 `BackupSummary.guardRulesSkipped`（**刻意不给默认值**）在**三处**恢复完成的提示里说得出。
3. **P3** `GalleryArtistFollowsStore.notice` 全仓零收集者（类 KDoc 自己承诺"并通过 notice 说一句"）：
   档坏了用户只看到"关注的画师全没了"，而 `*.corrupt-<时间戳>` 留档其实已经做了。
   接点选在**首页那一栏**（`GalleryScreen.kt`）—— 空引导那里才是"必须说得出为什么空"的位置。

### 审计中被我自己推翻的三条（记下来免得再翻）

「用户能存进永不命中的正则」**不成立**（设置页写库前早拦了）；「`addRule` 的 `catch → -1L` 静默失效」
**不成立**（四个调用点全检查返回值）；「波及 23 个调用点」**数字错**（那是引用文件数，真调用是 19 处）。

### 死代码：全走可逆路径

`reader/BitmapSliceHelper.kt`（整 object 零引用，长图防 OOM 切片一整套）与
`gallery/domain/GalleryArtists.kt` + 它的测试 —— **都是用户当场点头"归档"**，
镜像在 `_trash/reader-bitmap-slice-2026-10-01/`（新 README 写了"要接线得先补的三件事"）与
`_trash/gallery-artists-from-favorites-2026-09-30/`（原件删除前先 `diff -q` 确认镜像一字不差，
并改正了那份 README 写错的用例数：实际 **10 条**，不是 8 条）。
`VeneraPreferences.doubleTapZoom`（值 + setter + key）撤 —— 它的注释声称"telephoto 已在用"，
而阅读器确实用 telephoto，**却从没有把这个开关递过去**，是条断线；
设备上的 `pref_double_tap_zoom` 键**留着没删**。

### 注释：只删拍定的三类

说错话的横幅两条（「3. 搜索页 1:1 复刻」压在文件尾、「5. 探索页 1:1 复刻」压在收藏排序菜单上）；
内部轮次编号与共夸措辞**共 80 行注释**（三遍：29 / 32 / 19，每行前后文逐条打印核对；
**脚本只在注释行下手**）；`VeneraFloatingNavBar` 的「1:1 复刻原版 Flutter」改成留去向、去成绩式措辞。
论证式 KDoc、`⚠️`、`##` 分节、被推翻的旧文字**一字未清**。

### 验证

判据层先红（6 条新用例里 4 条按预期断言失败）后绿 `OK (49 tests)`；
`:app:testDebugUnitTest` **549 / 0 失败 / 0 错误**，且与在场 `@Test` 注解静态总数**正好吻合**；
`:app:assembleDebug` 通过。上一轮记录的 559 与本轮差额**不做核算**（逐类结果 XML 已被覆盖，
不拿不可复现的读数当基线）。真机四条复现待设备。

---

## 批次 Q + R（2026-10-01）：画廊 hero 转场 · 跳转落点 · 画师介绍页

详档：`gallery-hero-transition-and-artist-profile-2026-10-01.md`。

### `Navigation.kt` 这次的豁免具体给了哪几处

用户给的是**行为级豁免**，范围只有两处，别的都算越界：

1. `composable<GalleryRoute>` 与 `composable<GalleryDailyRoute>` 各包一层
   `CoverTransitionHost(animatedVisibilityScope = this) { … }`（写法照首页/搜索/探索那几处抄）；
2. 宿主层一条 `LaunchedEffect(GallerySearchHandoff.pending, destination)`：pending 非空
   **且当前目的地不是 `GalleryRoute`** 时 `navController.gotoTab(VeneraNavTab.GALLERY)`。

**没动**：`VeneraNavTab` 枚举、路由映射、顶栏齿轮入口、任何新增目的地。

### 落地清单

- **R1 页内 hero**：新增 `gallery/ui/GallerySharedTransition.kt`（`galleryCoverKey(uid)`，
  两站同 id 不撞 key），预览行 ↔ 每日热门二级页两端各挂 `coverSharedElement`，打码那档恒
  `allowFly=false`。`GalleryCardsGrid` / `GalleryPostCard` 加**可选** `sharedElementKey`，
  **只有 `GalleryDailyPage` 传**（其余三面墙对面没有同 key 的落地槽位）。
- **R2 取证**：`GalleryFlyIn.capture` 两个静默出口 + `GalleryPostScreen.canFly` 分支
  共**临时** `FlyProbe` 日志五条。**读数拿到就撤**，不留进正式代码。
- **批量 3**：预览行补 `onGloballyPositioned` + `GalleryFlyIn.capture` ——
  不补这条，点预览行的图 `canFly` 恒假、必然整页抬上来。
- **④**：收藏页 → 大图页 → 点 tag 现在直接落在画廊搜索。真实成因不是"收藏卡没 tag"，
  是消费交接槽的那句 effect 挂在画廊 Tab 自己的组合里而被覆盖 = 组合销毁 ⇒ 槽位写了没人取。
- **②**：详情面板画师行改成**头像 + 名字整块可点**（按压缩放沿用 `VeneraChip` 那档 0.96/0.88，
  走向标沿用节标题那枚 `›`），落点从"搜这一枚标签"改判为**进介绍页**；原动作由介绍页的
  「看 TA 全部作品」承接。首页「正在关注的画师」**单击仍直接搜**，长按才进介绍页。
- **③**：新建 `GalleryArtistProfileActivity` + `gallery/ui/GalleryArtistProfileScreen.kt` +
  `gallery/domain/GalleryArtistProfile.kt`，注册进 `AndroidManifest.xml`（`exported=false`，
  `Theme.Venera.GlassOverlay`，blur-behind 32dp）。返回栈 Main → Post → Profile：
  「看 TA 全部作品」`setResult` 后 Post 收到也自行 finish，**只返回一次**。
  卡用墙上的 `GalleryPostCard`（`private` → `internal`），屏蔽分级走**同一把** `buildGalleryWall`。

### 别名这一档：探针量完，yande.re 不上列表

`_probe/yande_re_alias_coverage.cjs`（读数同目录 `.txt`）。18 个横跨字母表的真名里
**同批凑得出别名表 = 0/18**，结构天花板（别名与正名同前缀）= **10/111 ≈ 9%**
⇒ yande.re 的别名行**整块不摆**，屏上永不出现"这位没有别名"；那侧只保留批次 J 已有的
"站方记的正名是 X"一句。别名列表只上 **Gelbooru 腿**（danbooru 记录的 `other_names`，
真样本 `_probe/hub/dan_setmen.json`），走同一笔现有请求、零新端点。
第一版探针从 `post.json` 取 `tag_string_artist` 当语料，**那个键根本不存在**（键表里只有 `tags`），
320 帖只捞出 1 个名 —— 那份 0/1 读数作废。另外钉死两条：`artist.json` 的 **`limit=` 被静默忽略**
（`limit=1` 与 `limit=40` 都回 25 条），`tag.json?category=` 也不生效。

### 顺带修掉的既有隐患

`GalleryArtistLinks.hostOf` 对**没有路径段**的地址（`https://mochida.tumblr.com` 这种站方真给的形态）
把 authority 读成空串（那行写的是 `substringBefore('/', "")`），后果是整条静默判成 OTHER、
屏上只少一枚胶囊。改回默认值并补一条用例钉住不带路径那一形。

### 与批准计划的偏离（逐条，详档 §七）

没建 `GalleryArtistProfileViewModel.kt`（取数归页面，与详情面板同一既有口径）；
`popularSlots` / `followState` / `sectionsToHide` 三条判据没抽层（理由各写明，其中
`GalleryCard` 活在 Compose 侧文件、不该为一句 `getOrNull` 拖进可单跑的判据层）；
介绍页顶栏右侧"更多菜单"没做（当下没有可放的动作）；
`WEB` 枚举档**刻意不加**（与 OTHER 在屏上同形，且会把 2026-09-30 已拍死的"认不出就 OTHER"劈成两半）；
**平台图标（批量 6）尚未落地**，两处胶囊现在仍是文字，那条已拍板记进了 `GalleryArtistRows.kt` 头注。

### 验证

判据层先红后绿：`bash _probe/l0/run-judgment-tests.sh` → **OK (69 tests)**（红的时候 10 failures）。
`:app:testDebugUnitTest` → **569 / 0 失败 / 0 错误**（上轮基线 549，只涨没红）。
`:app:compileDebugKotlin` / `:app:assembleDebug` 通过。真机验收清单在详档 §十。


### 本轮没碰的（保护域与避让）

`Navigation.kt` 里 5 处轮次编号注释**没清** —— 保护域，连注释级改动也该单独豁免；
`MainActivity.kt` / `VeneraApp.kt` 各 2 处**没清** —— dsh 正在改那两个文件，避让。
另记一条口子：备份恢复是直接写库、写完没有 `ContentGuardManager.invalidate()`，本轮未扩范围。
