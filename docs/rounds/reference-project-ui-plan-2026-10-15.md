# 参考项目对照计划 · UI 动效 / 加载 / 内容（2026-10-15）

> 题面：用户「全面看下 app 端的项目，再看下 `137458/pixez-miuix` 和 `mihonapp` 对我们有什么帮助，包括 UI 动效、加载和内容方面，看好之后给个计划」。
> 范围：**只排 `app/`（手机档）**。`desktop/**` 一行不排（那条线此刻有另一位在飞，工作区现量 114 颗文件 +5548/−1768 未提交）。
> 本文是**方案文档**，未动任何代码。每条都标了证据出处：参考侧给「文件:行」，我方给「文件:行」。
> ⚠️ 两份参考报告的数字来自三路并行取证，其中标注「我复核」的条目是我自己现量重数过的；其余为取证结果，开工前那批要回代码再核一次（本仓有过子代理编读数的事故）。

---

## 一、两个参考项目各自值什么

| | `137458/pixez-miuix` | `mihonapp/mihon` |
|---|---|---|
| 是什么 | pixiv 第三方客户端 pixez 的 **KMP + Compose + miuix** 重制版（Dart 旧码在 `archive/`） | Tachiyomi 官方续作，漫画/图源聚合阅读器 |
| 规模（取证） | `compose-miuix/` 下 411 个 `.kt/.kts`；commonMain 229 文件 / 51,888 行 | 17 模块 / 920 个 `.kt` / 97,686 行；`ui/reader` 单独 10,376 行 |
| 与我们同栈的程度 | **最高**：Compose 1.12.0、miuix 0.9.4、Coil 3.6.3、同样的"图站瀑布流 + 大图页 + 搜索 + 收藏"场景 | 中：Coil 3.6.3、Compose BOM 2026.10.00；但**阅读器仍是 View 体系**（RecyclerView + 自研 Pager），UI 库不是 miuix |
| 该抄它哪一层 | **动效语言**（HyperOS 那套缓动/跟手/容器变形）+ **图片载管管线** | **行为层**（预加载窗口、进度落库防抖、逐源反馈、追更排程、队列重排） |
| 不该抄 | 与 Decompose `ChildStack/Direction` 强耦合的转场调度；OS2/OS3 动态背景 shader | 扩展体系（Kotlin 源 vs 我们的 JS 脚本源）、SQLDelight、Metro DI、Voyager 导航、WebGPU 渲染器（2,064 行 + `androidx.webgpu`，我们双后端跑不了） |

**两条"我们已领先"的发现**（别倒退）：
1. Mihon 全仓**没有骨架屏/shimmer**（只有 `LoadingScreen` 转圈），我们有 `VeneraShimmer` + `VeneraCover` 占位。
2. Mihon 全仓**0 处 `SharedTransition`**（封面放大走独立 Dialog，`MangaCoverDialog.kt:177`），我们有 `ComicSharedTransition.kt` 单点口径。

---

## 二、我方现状地图（本轮排工的基准）

### 动效层：半统一
- 令牌唯一真源在 `ui/tokens/Spacing.kt:539-563`：`VeneraMotionTokens(short=120 / medium=220 / long=320 / imageFadeIn=200 / disintegration=900)` + `shimmerPeriodMillis=3000`（`Spacing.kt:438`）。
- **画廊侧吃 token，漫画侧半手抄**（我复核：`spring(` / `StiffnessMedium*` 共 **30 处引用 / 12 颗文件**，`GalleryPostScreen.kt` 一颗 6 行、`DampedDragAnimation.kt` 5 行）。
- 散抄的 `tween` 毫秒值：400（`ComicSharedTransition.kt:43`，共享元素**没进 token**）、300 与 220（`Navigation.kt:471/485`，后者与 token `medium` **同值双写**）、240（`ReaderZoomState.kt:73`）、200（`ComicDetailScreen.kt:2296`）、160（`GalleryPostScreen.kt:1614` `HANDOFF_MS`）。
- 级联进场（stagger）**不存在** —— grep 命中的全是 `LazyVerticalStaggeredGrid` 布局名。
- 预测返回有统一件 `components/PredictiveBack.kt`（回弹 spring 600f + tween 120），但大图页的下滑关闭是自研 `.draggable` + `PredictiveBackHandler`（`GalleryPostScreen.kt:940,1099`）—— 两套手感。

### 加载层：基本统一，有明确漏网
- 波浪环是全站口径，但**裸 material3 环仍在**（我复核：两种指示器共 **41 处引用 / 13 颗文件**，其中 `feature/sourcemanage/ComicSourceScreen.kt` 一颗占 12 行；其余 LocalComic 5、DownloadScreen 3、`ComicDetailScreen.kt:1886/1940`、Stats/SyncBackup/WebLogin/SourceEdit/GalleryAccountCard/ChapterCommentsSheet/UpdateCheckUi/CloudflareBypassActivity 各 1~2）。
- 骨架只有 3 屏真用（`HomeScreen.kt:902`、`ComicDetailScreen.kt:914`、`GalleryPostScreen.kt:1874`），其余是整块重拉替换。
- 空/错/重试已统一在 `VeneraEmptyView`（65 处 / 20 文件）。
- 冷启动先铺旧数据属实：2 分钟节流 `HomeViewModel.kt:416` + 先铺退出快照 `:385`。
- 图片管线**双 loader**：App 级 `VeneraApp.kt:78` **没有显式缓存参数**（吃 Coil 默认），画廊级 memory 64MB（`GalleryImageLoader.kt:46`）/ disk 512MB（`:56`）。全仓**没有 `crossfade`**。
- 大图页是**三档叠画**（preview→sample→原图，`GalleryPostScreen.kt:1619-1623`），且**故意不开** `placeholderMemoryCacheKey` —— 理由写在 `:1625`："它会让 `onSuccess` 在底图命中缓存时先响一次，加载态读数再也准不了（真机反馈『加载时没有骨架图』的根因）"。
- 聚合搜索不支持翻页是**写明的取舍**（`SearchViewModel.kt:413`：各源默认选项 + 30s 超时，追加语义不成立）。

### 内容层：卡片已统一，标记层最薄
- 列表卡唯一形态 `ComicListPresentation.kt:95-107`（封面 + 标题 2 行 + 副标题 + metadata + 标签 ≤5，简介批 5 已撤）。
- 标记：R18 有 ContentGuard 三档（BLUR 卡片打码）；**AI 是"屏蔽"不是"展示"**（漫画侧物理剔除 `ContentGuardManager.kt:133`、画廊侧 `AI_BLOCKED_RULE` `GalleryScreen.kt:1787`）；动图/视频在墙上换静帧。**屏上没有角标体系**。
- 章节表有已读/未读（`ComicDetailScreen.kt:1179`），但**没有"这一话读到第几页"**、也没有"缺 N 话"的表达。
- 收藏有排序菜单与筛选胶囊（`FavoritesScreen.kt:326/717/1283`），但**不记住每个分类各自的选中项**。
- 阅读器预加载是**单向**：`VeneraReaderScreen.kt:443-476` 并发发 `N+1..N+preloadImageCount`，**不向后预取、不取消过期那一笔、不跨话**。
- 阅读进度**每翻一页全量写一次库**（`VeneraReaderScreen.kt:479-497`，`LaunchedEffect(currentChapterIndex, currentPageIndex)` 里 `withContext(NonCancellable + Dispatchers.IO) saveHistory(...)`）。

---

## 三、计划（四批 · 每条带证据、代价、判据）

纪律沿用本轮 UI 审查那套：每批**判据 + 扰动台架**（改坏那一行必须红），改完回写 `docs/HANDOFF`，真机成对截图（MuMu `127.0.0.1:16384`）。观感基准沿用已定口径：**层次走填充差，不补一圈描边**；尺寸吃现成口径（官方原值 / 仓内既有 token），不造新数字。

### 批 A · 小而纯收益（不动结构，建议先做）

| # | 做什么 | 证据 | 代价 |
|---|---|---|---|
| A1 | **阅读进度写回防抖**：从"每页一次全量写"改成 `PROGRESS_SAVE_DELAY = 2.seconds` 那形 —— 延迟落库 + 换章先补写 `pendingProgressChapter` + `Mutex` 保序 + 写在应用作用域（ViewModel 销毁仍落盘） | Mihon `ReaderViewModel.kt:1149`（`PROGRESS_SAVE_DELAY = 2.seconds`，我复核在）、`:204/664-685`（pending 补写与保序） | 小 |
| A2 | **指示器漏网收口**：`ComicSourceScreen.kt` 那颗文件的 12 行改吃波浪环/骨架；其余 9 颗逐颗判"语义正当（`LinearProgressIndicator` 表示百分比）还是漏网" | 我方 `ComicSourceScreen.kt`（我复核 41 处/13 文件那把尺子）；参考侧 pixez 无骨架、Mihon 也无 —— **这条是补我们自己的破口，不是抄它** | 小 |
| A3 | **动效时长令牌吃满**：6 处手抄 `tween` 毫秒 + `ComicSharedTransition` 的 400ms 归进 `VeneraMotionTokens`；同值多抄的 `spring` 收成命名片段（`StiffnessMedium` 4 处同值、`StiffnessMediumLow` 5 处同值） | 我方 `Spacing.kt:539-563`（尺子已有）；pixez `MiuixNavAnimation.kt:15` 把 `HyperOSDecelerateEasing = CubicBezier(0.2,0,0,1)` 做成常量单一来源 | 小 |
| A4 | **逐源搜索反馈分档**：把"哪源有结果 / 0 条 / 失败 / 还在跑"排成有结果浮顶 → 固定源其次 → 名称，并补 `progress/total`；同 query 换筛选时复用旧结果不空屏 | Mihon `SearchViewModel.kt:50 limitedParallelism(5)`、`:244 SearchItemResult.{Loading,Error,Success(isEmpty)}`、`:62 sortComparator`、`:226 progress/total`、`:131` 同 query 复用 | 小-中 |

**A 批判据草案**：① 令牌尺子扩到"禁 `ui/tokens/` 之外的裸 `tween(<数字>)`"（沿用 `TokenBudgetFrozenTest` 的白名单只许缩短那形）；② 指示器尺子按文件钉**道数**（批 7 那条教训：存在性判式会被同屏另一处合法调用喂饱）；③ 进度防抖要**行为测**：连点 5 页只许落 1 次、换章那一笔必须补写、退出作用域后仍落盘 —— 不许只扫源码字符串。

⚠️ A1 有一条本仓已知的坑要防：`NonCancellable` 会换掉 `coroutineContext[Job]`（仓内记录在案），"只清自己这一笔"的守卫放进 `withContext(NonCancellable)` 里会恒假 —— 防抖改造时那枚守卫要留在外层。

### 批 B · 观感提升最明显（中代价，动到转场）

| # | 做什么 | 证据 | 代价 |
|---|---|---|---|
| B1 | **卡片 ⇄ 详情的容器变形**（不是淡入淡出，是真的从卡片矩形长成容器矩形）：登记卡片窗口矩形 → 转场期锚点锁定 → 对宿主层的视差/纵深缩放做**逆变换归一** → 可见面积 <25% 优雅回退侧滑；圆角**除以 scale 逆补偿**，终点像素级重合 | pixez `SharedBoundsRegistry.kt:118-137,393-411`、`IllustTransitionBounds.kt:25`、`CardExpandLayer.kt:42-107` | 中 |
| B2 | **跟手返回统一成一套手感**：左缘 24dp 拖拽走 `PointerEventPass.Initial` 抢占（在 pager 之前截获）、进度 ≥0.35 或速度 ≥2000px/s 确认；跟手弹簧 `DampingRatioNoBouncy + stiffness 3000`（近 1:1），finish `tween(240)` / cancel `tween(200)` 配 `CubicBezier(0.2,0,0,1)`（专治 OEM 进度派发稀疏） | pixez `DragBackGesture.kt:58-115`、`MiuixPredictiveBackAnimation.kt:38-48`、`MiuixNavAnimation.kt:15` | 中 |
| B3 | **转场那一瞬不白板**：打开大图/阅读时首帧**同步从 Coil 内存缓存取**已有位图，再挂网络请求；有占位时 `crossfade(false)`（防中点 0.75 不透明度闪白）；真实错误静默重试一次、`Cancellation` 不重试 | pixez `PixivAsyncImage.kt:265-276`（首帧同步取缓存）、`:224`（有占位禁 crossfade）、`:66-74`（静默重试一次）、`:215 placeholderMemoryCacheKey`（我复核 `:215`、`:55` 在） | 中 |
| B4 | **章节行左滑动作 + 长表快速滚动条**：滑动露出"读/收藏/下载"且动作可配；2,374 行章节表与阅读器加 `VerticalFastScroller` | Mihon `MangaChapterListItem.kt:85`（`me.saket.swipe.SwipeableActionsBox`）+ `ChapterSwipeAction` 偏好、`presentation-core/.../VerticalFastScroller` | 小-中 |

⚠️ **B1/B3 各撞一条我们已有的裁决，开工前必须先复量，不许照抄**：
- B1 的前提是"两端 key 同串、同构形状"，而本仓已知跨 Activity 时共享元素会把压在卡上的自有 chrome 截进飞行体（画廊走 PixelCopy 那一坑）。→ **先出设计稿 + 真机图证，等点头再动**；`desktop/**` 不参与。
- B3 的 `placeholderMemoryCacheKey` 我们**曾经试过并判负**，负结论现在钉在 `GalleryPostScreen.kt:1625`（"onSuccess 先响一次 → 骨架再也不准"）。这条要么被复量推翻、要么只搬它另外两半（首帧同步取缓存 + 禁双 crossfade），**别把已判负那半演回来**。

### 批 C · 阅读器手感（结构性，代价中到大，建议排 B 之后）

| # | 做什么 | 证据 | 代价 |
|---|---|---|---|
| C1 | **预加载窗口改成优先级序 + 可淘汰**：顺序"当前 → 前 1 → 后 1 → 向后全部 → 再倒序向前"，块大小 1 涨到 5；每次翻页 `cancel` 上一轮 chunk；网络受限时把窗口压成 `[当前-1, 当前+4]` | Mihon `HttpPageLoader.kt:160-203 loadByRollingChunked / :203 rollingChunked / :262 LIMITED_PAGES_AHEAD = 4 / :168 canLoadAhead()`（我复核全在） | 中 |
| C2 | **跨话预取 + 跨话连翻**：先等本章每一页落终局（Ready/Error）再 `preload(next)`，单飞不重入；连翻是把 prev/next 章 pages 拼进同一列表 + 章节边界哨兵项，翻页不重载 | Mihon `ReaderViewModel.kt:466 preloadNextChapterAfter`（我复核在）、`PagerViewerAdapter.kt:51-104`（`ChapterTransition.Prev/Next` 哨兵） | 大 |
| C3 | **长条漫分条解码**：按字节预算切条逐条上传（`STRIP_BYTES=4MB` / `MAX_PAGE_BYTES=128MB` / `MAX_STRIP_WIDTH=4096` / `Semaphore(1)`），整张位图不再常驻内存 | Mihon `data/coil/ImageDecoder.kt:136 decodeStrips`；对照我方 `ImagePipelinePolicy.decodeAndDescramble:178` + `reorderBlocksBottomUp:208`（整张 Bitmap） | 中-大 |
| C4 | **章节页表也进缓存**（不只是页图片）：退出时回写，二次进阅读器秒开 | Mihon `ChapterCache.kt:238`（100MB DiskLruCache）+ `putPageListToCache`、`HttpPageLoader.kt:110-125` 回写 | 中 |
| C5 | **切块/坏块的对照结论**：Mihon 只有 `splitInHalf/splitAndMerge/rotateImage`（双联跨页用），N 块拼一张交回源侧 —— **我们这条更强**（`onImageLoad` + nl 换源 5 轮重试链 `VeneraReaderScreen.kt:1937-2010`）。只借它的**页状态机 + `retryPage` 自动重新入队**那一形 | Mihon `ImageUtil.kt:116/137/155`、`Page.State.Error` + `retryPage:96` | 小 |

⚠️ C3 与切块去混淆**有顺序依赖**：分条解码发生在"还原块序之前"还是"之后"决定了能不能直接搬。开工第一件是先量 `decodeAndDescramble` 那一条链的形状，再决定搬不搬 —— 别默认它可切。

### 批 D · 内容层新能力（每条先量数据面，再决定做不做）

| # | 做什么 | 证据 / 前置量 | 代价 |
|---|---|---|---|
| D1 | **追更排程**：固定轮询换成"每本的发布间隔 + 周/月 pattern + lateness 补偿 + 休刊退避" | Mihon `ReleaseSchedule.kt:28 nextCheck`、`FetchInterval.kt:27`；前置：我方 `FollowUpdatesRepository.kt` 现在的轮询口径 | 大 |
| D2 | **收藏筛选/排序面板三页化** + 记住每个分类各自的选中项（`LastSelection`） | Mihon `LibrarySettingsDialog.kt:61/625`；我方 `FavoritesScreen.kt:326/717` 已有菜单与胶囊，缺"记住选中" | 小-中 |
| D3 | **未读进度与缺章表达**：章节行加"读到第 N 页"（仅未读且 >0 才显示）+ "缺 N 话"插行 | Mihon `MangaScreen.kt:754`、`ChapterList.MissingCount` → `MissingChapterCountListItem`；我方 `ComicDetailScreen.kt:1179` 只有已读/未读 | 小 |
| D4 | **重复翻译组收敛**（按话号分组优先当前源 + 可配显示组 + 排除表） | Mihon `ChapterRemoveDuplicates.kt:8`、`excluded_scanlator` 表、`ScanlatorFilterDialog.kt`；前置：**JS 源给不给 scanlator/分组信息要先现量**，给不出这条就不成立 | 小-中 |
| D5 | **模糊搜索 / 站内搜索语法**：换源与链接解析用归一化编辑距离（阈值 0.4）+ 去括号/去话号；书架内搜索支持 and/or/not/字段前缀 | Mihon `BaseSmartSearchEngine.kt:15`（`NormalizedLevenshtein`, `MIN_ELIGIBLE_THRESHOLD=0.4`）、`LibrarySearchLexer/Parser/Ast.kt`；对照我方 `ComicLinkResolver` | 中-大 |
| D6 | **内容标记上屏**：AI 生成 / 动图 / 视频 / 页数 做成卡片角标体系 | pixez `IllustCard.kt:78`（AI 标）、`Illust.kt:125-130`（isR18 三重判定）；⚠️ **与既有裁决对撞**：本仓对 AI 的口径是"刻意不屏蔽 AI 汉化/翻译"（已登记裁决），"展示档"要不要做、在哪一层展示属**受保域**，必须先拍板再动 | 中 |
| D7 | 阅读时长统计对照验收（`history.read_duration` + `restartReadTimer`） | Mihon `ReaderViewModel.kt:732`、`StatsViewModel.kt`；对照我方 `stats/ReadingStatsManager.kt` 现量 | 小 |

---

## 四、明确不搬（避免下一轮有人当待办）

- **Mihon**：Kotlin 扩展体系（`ExtensionManager` / tachiyomix / injekt）——我们是 JS 脚本源，问题域不同；它的 `quickjs` 只用于破 Cloudflare（`core/common/.../network/JavaScriptEngine.kt`），**不是源脚本引擎**，别当成"它也有 JS 源体系"来引。SQLDelight / Metro / Voyager / Glance widget / Shizuku / baseline-profile 同样不换。
- **Mihon 的加载态**（纯转圈）：我们 shimmer 已更好，不对齐。
- **WebGPU 高清渲染器**（2,064 行 + 新平台绑定）：miuix + Desktop 双后端跑不了；只借它的"条带字节预算"思想（已在 C3）。
- **pixez**：`SeekableStackAnimation` / `StackTransitionScheduler`（Decompose 栈强耦合，只借"进度连续翻转"这一思想）；`DampedDragAnimation` / `InteractiveHighlight`（我们 `components/backdrop/` 已有同源移植，重复）；`ui/effect/BgEffect*` 与 `ui/libs/liquid`（依赖 RuntimeShader 兼容层 + backdrop，非本轮刚需）；Ugoira zip 播放（pixiv 专有格式，我们的图站场景未确认需要 —— **未取证**）。

## 五、待补的取证缺口（开工前那一批要先量）

1. pixez 的"缓存优先冷启动"只在 repository 层没找到，可能在 Screen 层 —— 影响我们是否把 A3/B3 合并成一件事。
2. Mihon `BrowseBadges.kt` / `LibraryBadges.kt` 只列了文件未读内容 —— D6 的角标体系要拿它当对照就得先读。
3. Mihon WebGPU 降级判式未找到（只见到 `highQualityRenderer` 分流 `ReadingMode.kt:80`）。
4. `image-decoder` / `bitmap.kt` 两仓内部实现未读 —— 若 C3 判定要搬，先补这两颗。
5. 我方 `ui-fixes-2026-10-14.md:196-223` 那张表还挂着 15 条"留下一轮/不做本轮/未定位"（2.1-P1 图标成套、2.4-P3 每日热门标题截断、2.5-P2 节奏、2.6-P2/P3 关于页、2.8-P2×2 标签墙与详情页五枚色、2.8-P3、2.2-P1 空态居中、2.7-P3 源名主题色那格未定位）。**这些是排期内既有账，优先级高于本文件的 B/C 批** —— 排工时先清它，再上新能力。

## 六、建议的开工次序与理由

1. **先批 A 全部（A1→A4）**：四条都是小代价纯收益，A1 那条是我们自己屏上跑得最勤的一条 IO，且 Mihon 的常数（2 秒）是现成口径，不造新数字。
2. **接着清 `ui-fixes` 那 15 条既有账**（第五节第 5 条）—— 那是上一轮明确挂下的，不该被新参考项挤掉。
3. **然后 B2 + B3**（跟手一套手感 + 转场不白板）：B3 必须先复量 `GalleryPostScreen.kt:1625` 那条判负还成立不成立。
4. **B1 单独一轮**：要设计稿 + 真机图证 + 跨 Activity 那条硬约束的处置方案，等点头再动。
5. **C 批在 B 之后**（C1 先，C3 要先解顺序依赖），**D 批除 D3/D7 外都要先量数据面**再立项。

> ⚠️ 工作区现量 114 颗文件未提交（+5548/−1768）+ 102 颗未跟踪：开工前先把那批归账或提交，否则扰动台架的还原与"包内 jar 与源码同步"那两把尺子都会失真。
