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
