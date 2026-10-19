# pixiv 站点 hero 动画改造计划（2026-10-19）

题面 = 用户 2026-10-19：「我们这边 app 项目的 hero 动画很乱，我叫 workbuddy 修改了一轮但效果也不好……画廊 pixiv 站点的 hero 动画元素应该怎么改成 pixez-miuix 的效果」。

**术语先划清**：本文件里的 hero 指**封面飞入/离场那趟转场**（共享元素那一族），**不是**画廊首页顶部那张"头图"（`galleryCoverKey` 之外那套 `SettingsHero*`；手册行 204-205 的"hero 三修"说的是那张图的顶栏大字，与本计划无关）。

行号都是 2026-10-19 对工作区现状现量的（工作区有大量未提交改动，含 workbuddy 那一轮，**未 commit**）。

---

## 1. 一句话结论

pixez 的顺，不是因为它的曲线或时长调得好，而是因为它**飞的是那块"活的"画面**：一幅真图、一条标量、图像空间里插值裁切窗口，内容**从头到尾没被拉伸过**，所以它**不需要交棒、不需要淡出、也不需要等落点**。
我们飞的是**一块截图**，于是必须回答"截图和真图重叠的那几帧怎么办"——这一族问题催生了一条又一条 alpha 曲线，每一条都在治上一条的副作用。**"乱"是架构的必然结果，不是参数没调好。**
两处选错了对象：① 作品页离场飞的是**整屏**那一帧（不是那一页画面）；② 飞行期该藏起来的是**源卡**，我们却把**飞行体**淡了（§4.3 第 6 条）。
观感上最刺眼的两处错位还各有一条能当场复现的代码成因（§2.2 的"半接线"与 §3 的"矩形被窗口裁过"）。

---

## 2. 现状测绘（pixiv 这一条链，逐格 file:line）

> 这一节同时是本族的一份**窄域审计**（只审 hero 这一条链，不是整仓打分）：**Performance 5/10** —— 进场那三处与播放器那两处都把动画值读在 `drawBehind` 里（这条是对的，别改），但**作品页离场被共享件的 `Float` 形参逼回组合期**（§2.7，每帧重组）；**State 3/10** —— 同一份"此刻飞到哪一档"散在六条 alpha 曲线 + 三套曲线参数 + 一枚进程内单例里，且**端与端不同轨**（§2.2、§2.6）；**Side effects 5/10** —— `LaunchedEffect` 的键与预算本身合理，病在效果层承担了本该由状态层表达的"进行到哪一档"；**Composable API 4/10** —— 同一件事写了四遍（§2.1），钉它不漂的那把尺子**只存在于注释里**；另加一条 **纪律 2/10**：`boundsInWindow()` 那一档是"默认参数会把定位数据裁坏"的坑，18 处调用零处显式（§3）。

### 2.1 飞行体的渲染被写了四遍

同一个动作"把一块位图画进一个从 A 框变到 B 框的圆角矩形"，现在有**四份抄本**：

| 哪一份 | 位置 | 谁在用 |
|---|---|---|
| 共享件 `GalleryExitFlyBody` | `gallery/ui/GalleryExitFlyBody.kt:56-109` | **只有**作品页离场（`PixivWorkScreen.kt:993`） |
| 大图播放器进场（就地写） | `gallery/ui/GalleryPostScreen.kt:1412`、`:1437` | 播放器进场 |
| 大图播放器离场（就地写） | `gallery/ui/GalleryPostScreen.kt:1492`、`:1503` | 播放器离场/跟手 |
| 作品页进场（就地写） | `gallery/ui/PixivWorkScreen.kt:935-974` | 作品页进场 |

`GalleryExitFlyBody.kt:116` 的头注声称"两边同形由 `GalleryExitFlyBodyTest` 钉着 —— 一处漂了当场红"。**那颗测试文件全仓不存在**（`Grep -l GalleryExitFlyBody` 只命中 `PixivWorkScreen.kt`、`GalleryExitFlyBody.kt`、`docs/rounds/pixiv-artist-page-impl-2026-10-19.md`）。也就是说：**共享件是 2026-10-19 抽的，收口只做了一半，而钉它"不漂"的那把尺子是一句空话。**
这正是 workbuddy 自己报告里 P2 那格（`docs/anim-audit-2026-10-19/REPORT.md:207-211`）明写"要做"而没做完的那件。

### 2.2 起点/落点的语义按宿主各定各的（**这就是"很乱"的主体**）

去程起点 = 点击那一刻 `GalleryFlyIn.capture(view, rect)`；回程落点 = 各宿主自己 `noteCardBounds(键, 矩形)`。两件事**每个宿主各答一次**，答案不一致：

| 入口（卡片件） | 去程截不截起点 | 落点登记用的键 | 登记的矩形是**哪一块** | 屏上读出来 |
|---|---|---|---|---|
| 主墙/搜索/收藏那面墙 `GalleryPostCard`（`GalleryScreen.kt:3134`） | ✅（`:2824`） | `post.uid` | **封面那一块** ✅ | 进会飞、出会飞 |
| 首页预览行 `GalleryPosterRow`（`GalleryHomeSections.kt:392`/`:413`） | ✅ | `card.post.uid` | 封面那一块，但槽位是**定宽定高 124×170**（`VeneraSpacing.historyCardWidth/historyCoverHeight`），与图真实比例无关 | 进会飞、出会飞，中段比例漂 |
| **pixiv 首页三节（排行榜/推荐/关注那排）`PixivWorkCard`** | ❌ 根本没截（宿主 `GalleryScreen.kt:1543-1546` 只设队列） | `work.post.viewerKey`（`GalleryPixivHome.kt:1020`） | **整张卡**（`Modifier.onGloballyPositioned` 挂在 `VeneraCard` 上，`GalleryPixivHome.kt:1016-1022`，含下面那行文字区） | **进不飞、出会飞到一张比封面高一截的框** |
| 画廊历史那一格（`GalleryHistoryScreen.kt:384`） | ❌（`:241` 只开页） | `row.uid` | 行内那一格 | 同上，半接线 |
| 画师页/画师介绍页（`PixivArtistScreen.kt:570-583`、`GalleryArtistProfileScreen.kt:437`） | ✅ | 复用 `GalleryPostCard` 内部登记 = `uid` | 封面那一块 ✅ | 全通 |
| 作品页点某一页 → 大图播放器（`PixivWorkScreen.kt:287-302`） | ✅ 重新截（判式钉了顺序：`GalleryViewerHeroWiringTest.点图那一刻重新截起点，顺序钉死`） | 逐页键 `uid:pN`（`PixivWorkScreen.kt:1586`） | 那一页图块 ✅ | 全通 |

两条口径叠在一起的历史：
- 手册行 213（`docs/HANDOFF-2026-10-04.md:1122`）规则④原话：**「这一排卡不接共享元素飞行（没接 capture），表现是走整页抬升那条既有退路，不是缺陷。」**
- 手册行 232（`:1141`）附近的批 231-E 又给**同一排卡**补了**回程落点登记**（`GalleryPixivHome.kt:1020`），但没补 capture、也没把矩形收成封面块。
⇒ 今天这一族是"**去程不飞、回程飞到一个更高的框**"，比两种自洽状态都难看。**10-17 那句"不是缺陷"已经被 10-19 的半接线推翻了**——它是一句当时的诊断被冻成了规格，不是需求（这类"措辞锚点把诊断冻成规格"本仓已经栽过五次）。

### 2.3 作品页**离场飞的是整页**，不是封面

- 起点 `fullFrameBox` = **整屏**矩形（`PixivWorkScreen.kt:588`、`:727-729`），`captureRegion` 截的是整屏那一帧（`:676`）；
- 于是飞行体里装着"竖着连排的多页图 + 动作条 + 标签 + 评论"，收进一张封面；
- 因为两份**不是同一张画**，只能在末段把飞行体淡掉（`:1003` `alpha = exitBodyAlpha(exitFly.value)`，算式在 `GalleryExitExpansion.kt:106-115`）——**淡出是在给架构错误打补丁**。
- 对照样板：大图播放器那一屏的离场起点是**画面框**（`GalleryPostScreen.kt:848-856`），所以它不需要这一档补丁。
⇒ pixiv 那一屏读起来的"乱"，最直接的就是这个：一整页缩成一个小方块并化掉，而 pixez 是**只有那块画面在变形**。

### 2.4 一条转场挂了六条 alpha 曲线

`pageAlpha` / `detailEntryAlpha`（`PixivWorkScreen.kt:450-473`）、`handoffPageAlpha` / `handoffBodyAlpha`（`GalleryHandoff.kt:59-64`）、`exitPageAlpha` / `exitBodyAlpha`（`GalleryExitExpansion.kt:72-115`），再加播放器那侧的 `backdropEntryAlpha`（`GalleryPostScreen.kt:833-840`）。
每一条都能追到一次真机报障（`GalleryHandoff.kt:6-33` 的"暗一下 + 重影"推导、`REPORT.md` 的 D1-D5）。**它们存在的唯一理由是"飞行体是截图、落点是真图"这个前提。**前提一撤，这六条里**三条整条删掉**（`handoffPageAlpha`、`handoffBodyAlpha`、`exitBodyAlpha` —— 交棒那一档与两处"淡出补丁"），另外三条（页面、内容区、幕布）**收成同一枚标量的三个投影**（一个 `expansion` 出三个数，而不是三条各自计时的钟）。

### 2.5 三套时长/曲线在同一族转场里并存

| 哪一段 | 用的什么 | 出处 |
|---|---|---|
| 作品页进场、播放器进/出 | `spring(stiffness = Spring.StiffnessMediumLow)` | `PixivWorkScreen.kt:541`、`GalleryPostScreen.kt:676`、`:918` |
| 跟手那一段 | `spring(NoBouncy, stiffness = 3000f)` | `GalleryPostScreen.kt:938-942` |
| 页内那对真共享元素 | `tween(400, CubicBezier(0.2,0,0,1))`（M-bezier） | `components/ComicSharedTransition.kt:41-46` |
| 交棒 | `tween(160)` | `GalleryPostScreen.kt:681` |

pixez 那一侧 10-19 现量（§4.3 冲突表第 1 行）：**栈转场是临界阻尼弹簧 `stiffness = 450f`（NoBouncy）**，贝塞尔只用在手势的 finish/cancel，跟手那一档是 `stiffness = 3000f`——最后这一枚与我们播放器里那档跟手弹簧**逐字同值**（`GalleryPostScreen.kt:938-942`）。
⇒ **我们的"跨 Activity 那一趟"和"页内那一趟"根本不是同一条曲线**，同一个 App 里两种手感；而对手给我们留的口径其实是"飞行用 450 那一档、跟手用 3000 那一档"两把，不是一把。

### 2.6 两端不同轨的那对键（现状没错，但脆）

- 墙上登记 `post.uid`，播放器与作品页查 `post.viewerKey`（`GalleryPostScreen.kt:853`、`PixivWorkScreen.kt:631`）。
- 对**列表腿**取到的作品，`pageOrdinal == null` ⇒ `viewerKey` 逐字等于 `uid`（`gallery/data/GalleryPost.kt:270`），所以现在**碰巧**能查到（D4 那笔探针 `target=Rect(32,1103,528,1379)` 查到了，`REPORT.md:98`）。
- 但这是"靠两个字段此刻相等"成立的接线：一旦列表腿某一站开始给 `pageOrdinal`，或某处用页轴生成的 post 去登记，主墙那条回程当场静默失效。workbuddy 报告 P0-3（`:174-183`）点的就是这一格，**它自己没改**（`GalleryScreen.kt:3134` 今天仍是 `uid`）。

### 2.7 共享件的形参形状把动画值读回了**组合期**（唯一一处每帧重组）

- 播放器那两处都读在绘制阶段：进场 `GalleryPostScreen.kt:1412` 区、离场 `:1467` `exitExpansion(progress = exitFly.value, …)` **在 `drawBehind` 里**取值 ⇒ 不拖重组。✅ 这是本仓该守的口径。
- 作品页离场却把动画值当**实参**递给共享件：`PixivWorkScreen.kt:994` `progress = exitFly.value`、`:1003` `alpha = exitBodyAlpha(exitFly.value)` —— `GalleryExitFlyBody` 的签名是 `progress: Float` / `alpha: Float`（`GalleryExitFlyBody.kt:57/64`），于是**每帧重组那一层**，动画值在组合期就被读了一次。
- ⇒ **批 A 第④件（把播放器离场换成共享件）必须先改形状**，否则是把播放器那两处较好的写法降级成较差的那一种。改法：形参收 `progress: () -> Float`（或直接把 `Animatable`/`State<Float>` 递进去），在 `drawBehind` 里调用一次；调用点保持 `drawBehind` 语义不变。判据：一把尺钉"`GalleryExitFlyBody(` 的调用点实参里不许出现 `.value`"。

---

## 3. 一条被所有人忽略的地基坑：`boundsInWindow()` **默认裁到窗口内**

Compose **1.12.0** 源码原文（`androidx.compose.ui:ui-android:1.12.0-sources`，`commonMain/androidx/compose/ui/layout/LayoutCoordinates.kt:196`、`:209`）：

```kotlin
fun LayoutCoordinates.boundsInWindow(clipBounds: Boolean = true): Rect {
    ...
    // KDoc：If `true`, any clipping that occurs between this layout and the window will affect
    // the returned bounds, and can even result in an empty rectangle ...
    // If the bounds are completely clipped, returns Rect.Zero.
```

现量本仓：`app/src/main/java/com/venera/compose` 下 `boundsInWindow` 共 **18 处**，**0 处**显式传 `clipBounds` ⇒ **每一处拿到的都是"被窗口裁过的那一块"**。

这对 pixiv 恰好是最坏的一族，因为 pixiv 的页是**竖长条**：手册行 233-②（`HANDOFF:1150-1153`）的真机读数——详情腿 `width 2400 / height 4000`，1080 宽应当 **1800** 高，而 uiautomator 那一格只有 `[0,0][1080,979]` ⇒ **约 821px 在窗口外**。
于是：
- 进场**落点**（`PixivWorkScreen.kt:1586` 登记的那格）是一幅被裁短的真图矩形 ⇒ 飞行体落在一个比画面矮的框上，交棒那一帧画面高度对不上（读起来就是"错位/压一下"）；
- 被完全裁掉时它**直接给 `Rect.Zero`**，而 `noteCardBounds`（`GalleryFlyIn.kt:234-235`）看到零尺寸只忽略不注销 ⇒ 那一格从此**没有落点**，那一趟退化成"整页抬回去"，屏上分不出是"没等到"还是"等到了没飞"（`GalleryFlyIn.kt:207-222` 那条改判只解决了"等什么叫醒等待者"，没解决"值本身就是错的"）。
- 截图那一头**必须**是裁过的（PixelCopy 只能读窗口内的表面），所以正确形状是：**裁过的那一块只用来截像素，没裁过的那一块才用来定位**。pixez 正是这么分的（它存的是窗口矩形 + 对宿主视差做逆变换归一，另有一档"可见面积 <25% 就判无锚、优雅回退侧滑"——10-13 现量）。

---

## 4. pixez-miuix 的 hero 到底怎么做的（机制层）

> 本节 §4.1-§4.2 是 2026-10-04 与 2026-10-13 两轮逐 commit 读 diff 的冻结结论（`137458/pixez-miuix`，GPL-3.0 ⇒ **B 档：只取机制与表，一行 `.kt` 都不搬**，见 `docs/rounds/gallery-pixiv-donor-projects-2026-10-16.md` 的许可纪律）。§4.3 是 2026-10-19 对现 HEAD 的重读，**里面标了"改判"的四条覆盖上面两节的旧话**。⚠️ 仓库的**默认分支是 `master`**，`compose-miuix` 只是仓内那个源码目录名（旧记录把两者混过）。

### 4.1 它的 hero 不是共享元素，也不是截图
全仓 `SharedTransitionLayout` / `sharedElement` / `PixelCopy` **零命中**。做法是**单 Activity** 内 Decompose `Children(stack)` + 自写 `StackAnimator`：**一个 `expansion: Float` 驱动封面层的变形，同时定遮罩与各段淡入的时机**（⚠️ 10-19 现量改判：底层页面**已经不再缩放**，只叠一层遮罩，见 §4.3 冲突表第 2 行）。卡片把窗口矩形与自己的圆角登记进 `SharedBoundsRegistry`，动画器每帧算 `baseScale = maxOf(liveScaleX, liveScaleY)` + **双向比例裁切** + **圆角除以 scale 逆补偿** + 水平居中偏置。
关键推论：**它没有"交棒"这个概念**——飞行体与落点是同一个视图，`expansion=1` 那一刻就是最终画面，所以没有"两份同一张画重叠"，也就不需要任何淡出。而且它**从不等落点**（§4.3 第 1 条）。

### 4.2 一标量四源 + 四条 registry 纪律（10-13 现量 @ v0.9.109.5；②③ 已被 §4.3 改判）
- push/pop 的 `factor`、系统预测返回的 `progress`、应用内左缘拖拽的 `progress` 全部换算成同一个 `expansion` 喂同一个帧渲染器 ⇒ 手势路与自动路不分裂，中途回弹不需要第二套算式。
- registry 四条：① 转场中锁住正在飞的那颗不再更新；② ~~底层被自己变换过时存 `toStationaryBounds()` 逆变换~~（**10-19：这条在卡片路上已成死码**，底层不再变换；只有侧滑回退的位移还活着）；③ 卡片滚出可视 **75%** ⇒ 判无锚、优雅回退侧滑（**不是**回退"整页抬上来"；10-19 现量的尺子是"宽、高两枚可见比都 ≥25% 才算有锚"）；④ 页内左右滑切过 ⇒ 按「当前展示的作品 → 本次打开的作品」两级候选找锚。
- `cardExpandFrame` 里 `content(...)` 必须**单一调用点**（`0922d3d1` 那笔崩溃教训：双调用点 = movableContent 同帧双迁移源 ⇒ "Key used multiple times"）。
- 详情 chrome 分阶段：`DETAIL_CHROME_FADE_START = 0.65` ⇒ 收缩后半程只剩作品本体。
- 转场期挂 `InputConsumingOverlay` 吞输入。

### 4.3 2026-10-19 现量（现 HEAD `fc21b44`，2026-10-16 09:19Z；**默认分支是 `master`，`compose-miuix` 是目录名**）

**先记一件"10-13 之后又长了什么"的答案：hero/cardExpand 这一族 0 变更。** 10-13 之后只有 5 笔（图标 / 以图搜图 / OverlayDialog），animation 目录 19 个 .kt 与 10-11 快照逐 blob 全等。⚠️ 但本机 `%LOCALAPPDATA%\pixez-src` 那份缓存 shallow 到 `26621f288`（v0.9.109.5，10-07）——**不是 10-13 的代码**；10-07→10-11 窗口里这一族确实动过（`acaaf0a0b` `ChildStack<Config,Config>`、`d3658492e` IllustDetail 子组件化、`257744d59` CompositionLocal 单通道），后果是**作品 id 只在 `configuration` 上、child 实例不再承载 id**，取锚点的那一路全改了形状。

**四条与旧结论冲突，就地改判（旧结论划掉不删）：**

| 旧结论（10-04 / 10-13 冻结） | 现量 |
|---|---|
| ~~栈转场 340ms + `CubicBezierEasing(0.2,0,0,1)`~~ | **`spring(dampingRatio = NoBouncy, stiffness = 450f, visibilityThreshold = 0.0025f)`**；那枚贝塞尔只服务手势收尾 `tween(240)` / 取消 `tween(200)`；跟手那档是 `stiffness = 3000f`；**340ms 只活在注释里**，navigation 目录内 `tween(` 零命中 |
| ~~一个 expansion 同相驱动两层（页面层 + 封面层各自缩放）~~ | **底层已经不再缩放**：`BACKDROP_MIN_SCALE = 1.0f`，那个"底层缩放态"的解析函数**生产代码零调用者**（只有测试引）；底层现在**只叠一层遮罩**（`0.24 × expansion`）。⇒ "两层"是真的，"两层都变形"是旧的 |
| ~~底层被自己变换过时存 `toStationaryBounds()` 逆变换~~ | 该逆变换分支在卡片这条路上**已成死码**（因为底层不再变换），只有侧滑回退那条 `listTranslationX` 还活着 |
| ~~落点判据含"容器纵横比与卡片差距过大 ⇒ 判不可落"~~ | 那颗 `landable` 判式**现值只有尺寸合法性检查，没有纵横比门槛**，而它旁边两处注释仍在宣称有 ⇒ **照抄注释会做出一整条不存在的分支**（我们自己的落点判式要按"屏上真发生的"写，不按它的注释写） |

**复核成立的旧结论**（不再重查）：hero 不是 `SharedTransitionLayout`/`sharedElement`（全仓 `SharedTransition`=0、`sharedElement`=0、`PixelCopy`=0）；转场中锁住正在飞的那颗锚点不再更新；可见性门槛（宽、高两枚可见比都 ≥ **25%** 才算有锚）；零尺寸上报只忽略不注销；"当前展示的作品 → 本次打开的作品"两级候选找锚；`content(...)` 单一调用点（+ 换层时同 key 同 instance 复用旧引用，防 "Key used multiple times"）。

**这轮真正新的六条（是我们需要的形状）：**

1. **它从不"等落点"。** 卡片每次布局都刷新登记，帧渲染器**每帧现取**，取不到就**立刻**走侧滑退路——没有 await、没有预算、没有"放弃档"。（对照我们：`PixivWorkScreen.kt:501-520` 的 `withTimeoutOrNull(ENTRY_WAIT_MS)` + `entryGaveUp` + 三条"不飞了"退路。）
   它凭什么不用等：详情页**第一帧就有真实比例**——数据侧同步命中缓存（`produceState(initialValue = 缓存里那条作品)`），布局侧 `if (pageIndex == 0 || !pageLoaded) Modifier.aspectRatio(作品比例)`，所以落点在第一帧就量得出来。**"等网络"这件事被数据缓存挪到了打开之前。**
2. **图源同构由"一颗函数"保证。** 列表与详情共用同一个 URL 解析件，只有一个显式参数不同（列表允许方图占位档、详情不允许，因为详情按作品真实比例布局）；主图另有一枚独立缓存键后缀。首帧像素走**同步直取 Coil 内存缓存**（拿到就直接当 Painter 用），并且"有占位时把 crossfade 关掉"。
   ⇒ 这条就是批 B 那件"活画面"的现成形状。⚠️ 同一个文件里那枚 `placeholderMemoryCacheKey` 我们**已判负**（`GalleryPostScreen` 底图命中缓存时 `onSuccess` 先响一次 ⇒ 加载态读数不准），只搬另外两半。
3. **多图作品的 hero 只飞第 0 页。** 详情是一列竖向全展开的流（页与页同屏），`page 0` 那一格按作品比例布局；页内左右滑**不是 hero 场景**（点图另开全屏看图器，那一跳没有封面变形）；真正会改锚点的是"左右滑切**作品**"，靠两级候选把返回落点解析回**用户当初点进来的那张卡**，注释原话是"保证收回终点始终落在用户点进来的那张卡片上"。
4. **无锚的降级是"全宽侧滑"，不是淡入也不是直接跳。** 顶层 100% 宽平移、底层 -25% 视差 + alpha 掉到 0.9、遮罩最深 0.5；触发条件只有两枚（非详情路由/未登记，含直达与进程恢复；滚出可视 <25%）。另外 `landable` 为假当帧就显式清掉激活态，不留"半飞"状态。
5. **系统栏完全不参与。** 一次性 edge-to-edge、容器铺满整窗口（含状态栏那一段），详情大图"从屏幕最顶端开始渲染"；页面 chrome（玻璃顶栏 + 信息卡）按 expansion 淡出，且**已经滚离顶部时强制不淡**、alpha ≤ 0.001 时顶栏整格不进组合。**没有"截帧前先收两帧 alpha"这类 hack**——因为它不截屏。
6. **源卡在转场期被主动 alpha 掉**（在 expansion 0.05→0.10 那一小段里隐去）。⇒ 全程屏上**只有一份**画面：变形体是唯一副本，落位时卡片在**它正下方同矩形**重新亮起来，交换看不出来。
   我们恰好做反了：墙上那张卡一直是亮的，于是末段只能把**飞行体**淡掉（`PixivWorkScreen.kt:1003` `exitBodyAlpha`）——**淡的对象选错了一格**。

**算式形状**（只记参数与语义，许可 B 档不搬码；细节见 `CardExpandLayer`/`SharedBoundsRegistry` 那两颗文件）：等比 scale 由"更吃像素那一方向"定（`maxOf(宽比, 高比)`）并在路程上从该值走到 1；裁切窗口是**卡片矩形与容器矩形的逐边插值**；宽度驱动时窗口贴左缘（bias=0），高度驱动（宽度被裁）时居中收窄（bias=0.5）并反向补偿位移；圆角 = **lerp(卡片圆角 → 设备物理屏幕圆角) ÷ 当帧 scale**（`DEFAULT_CARD_CORNER_RADIUS_DP = 16f`，与列表卡实传的 16 同值）。分阶段常量：顶层淡入 [0,0.05]、源卡隐去 [0.05,0.10]、chrome 淡出起点 0.65、遮罩最深 0.24、阴影 16→0。**"页面内容从头到尾没有被拉伸过"是这一族的根**，比例差异全部由裁切窗吸收。

**跨 Activity 的硬前提**（搬不过来的那四条，别在计划里再撞一次）：① 卡片与容器同窗口、同一把 `boundsInWindow` 坐标；② 两层在同一组合树同帧共存（顶层要能"透视"底层并给它叠遮罩）；③ Decompose `ChildStack` 的 `Direction`/`factor` 语义；④ 预测返回由 app 自绘、进度归 app 拿。**可以搬的两条**：进程级 Coil 内存缓存 + 进程级数据缓存同步可读（Application 级，跨 Activity 一样命中）；圆角/玻璃/尺寸那些与 Activity 无关的口径。

### 4.4 逐条对照：差别到底在哪一层

| 维度 | pixez（10-19 现量） | 我们（pixiv 这一条链） | 差在哪一层 |
|---|---|---|---|
| 载体 | 单 Activity + 自写可打断栈层 | 跨 Activity + 截图交接 | **架构**（结构性） |
| 飞的东西 | **那块活的画面**（同一视图从卡片矩形变形到容器矩形，内容**从不被拉伸**） | 一块 `PixelCopy` 截图（作品页那趟还是**整屏**） | 架构 |
| 驱动量 | **一个** `expansion` 定封面层的形 + 各段淡入时机；底层**只叠遮罩**（0.24），不再缩放 | 六条 alpha 曲线 + 三套弹簧/补间 | 参数无法弥合 |
| 时长曲线 | 飞行 `spring(450, NoBouncy)`；跟手 `spring(3000)`；贝塞尔只在手势 finish/cancel | 飞行 `spring(StiffnessMediumLow)`；跟手 `spring(3000)`；页内那对是 `tween(400)` | **跟手那一档我们与他逐字同值**，飞行那一档不同 |
| 交棒 | **不需要**（同一视图） | 必须有，且必须淡（否则重影/暗一下） | 架构的必然代价 |
| 落点要不要等 | **不等**：每帧现取，取不到立刻侧滑；第一帧就有真实比例（数据同步命中缓存） | 等详情接口 + 600ms 预算 + `entryGaveUp` 放弃档 + 三条退路 | 可改（批 B 第⑥件） |
| 比例不同构 | 裁切窗 = 卡片矩形与容器矩形的逐边插值，`baseScale = maxOf(宽比, 高比)` ⇒ 差异被另一方向的裁切吸收 | `coverSourceRect` 每帧**按当帧目标框重算居中裁** ⇒ 途中主体会漂 | 算式（可改） |
| 圆角 | `lerp(卡片圆角 → 设备物理屏幕圆角) ÷ 当帧 uniformScale` | `payloadCornerRadiusPx`（× dst/src）再乘两档 token 之间的 lerp | **同族不同锚**：他们锚"当帧等比 scale"与"设备物理圆角"，我们锚"终点框宽度比"与"我们自己的两档 token" ⇒ 批 B 要换成前者，否则末段角上还会补白 |
| 矩形被窗口裁过 | **同样会被裁**（他也是 `boundsInWindow()`），但他的处置是"**可见比 <25% ⇒ 判无锚 ⇒ 侧滑**" | 只有"零尺寸只忽略不注销"那半截 ⇒ **裁过但非零**的那一档被当真相用了（§3） | 语义（可改，两半都要） |
| 无锚降级 | 全宽**侧滑**（顶层 100% 平移、底层 -25% 视差 + alpha 0.9、遮罩 0.5） | **整页从下沿抬上来** | 可改，成本低 |
| 飞行期谁被藏起来 | **源卡**被 alpha 掉（0.05→0.10 那一小段）⇒ 全程只有一份画面 | 落点那张卡一直亮着 ⇒ 末段只能把**飞行体**淡掉（`exitBodyAlpha`） | **淡的对象选错了一格** |
| 多图作品 | hero **只飞第 0 页**；页内左右滑不是 hero 场景（点图另开全屏看图器，不变形） | 作品页点某一页 → 播放器**自己重截一帧再飞**（第二套转场） | 可简化 |
| 跟手返回 | 系统进度 + 应用内左缘拖拽都汇进同一标量 | 播放器那屏有（批 2，真机六格未关）；**作品页刻意没接**（`PixivWorkScreen.kt:706` 用 `BackHandler`） | 可改，但要先关批 2 那六格 |
| chrome 让位 | 系统栏完全不参与；页面 chrome 按 expansion 淡出，**已滚离顶部时强制不淡**，alpha≤0.001 整格不进组合 | `ChromeCaptureSuppress` 截帧前收两帧 alpha（还挂着"返回程那半没处理"的旧账） | 我们有截图才有这问题；换成活画面整格消失 |

### 4.5 已判负、不许在本计划里重开（逐条带出处）
1. **跨 Activity 搬"飞活组合"的载体** —— 结构性搬不过来（`docs/rounds/gallery-hero-predictive-back-vs-pixez-2026-10-13.md:38`；animation 1.12 无跨窗口共享元素 API，平台 `transitionName` 明确「No interoperability between Views and Compose」）。⇒ 本计划要么在跨窗口里把**画面身份**换成活图源（批 B，自研），要么整屏搬进同窗口（批 C，动保护域）。
2. **`view.draw(canvas)` 截帧** —— 卡面走液态玻璃 `RuntimeShader`，软件 canvas 抛异常且被 `runCatching` 静默吞（`gallery-hero-transition-and-artist-profile-2026-10-01.md:291-295`）。
3. **把等待预算调长（400→600ms）** —— 病是姿态不是时长（同上 `:319`）。
4. **去程两层同时半透明** —— 总不透明度 `1-f+f²` 在 0.5 掉到 0.75 ⇒ "图先暗一下"（同上 `:620-648`，判式 `handoffCoverage` 钉着）。
5. **改 `exitPageAlpha` 那枚 0.25 归零点** —— `REPORT.md:124-126`（三份同图同时半透明）。
6. **`placeholderMemoryCacheKey`** —— 已试过并判负（`GalleryPostScreen`：底图命中缓存时 `onSuccess` 先响一次 ⇒ 加载态读数不准；那一条判负记在参考仓能力边界的旧账里）。批 B 要用"首帧同步读 Coil 内存缓存"那一半，**不许**把这半演回来。
7. **pixez 的 `SeekableStackAnimation`/`StackTransitionScheduler`、`DampedDragAnimation`、OS2/OS3 shader、Ugoira** —— 不搬（Decompose 栈强耦合 / 我们 `components/backdrop/` 已有同源移植）。

---

## 5. 改造批次（按"每批可独立验收"切，不捆票）

> 切法照 pixez 自己那笔教训：`f49367c9` 那捆 +1011/−66 当天整轮 revert，10-02 重做时拆成两笔可独立验收的（10-04 现量）。

### 批 A · 把口径收成一个（**低风险，先做，不改观感架构**）
| 项 | 内容 |
|---|---|
| 目标 | 消除"半接线"与"两种矩形"，让所有入口**要么都飞要么都不飞**，且落的都是**同一块封面** |
| 改哪 | ① `GalleryPixivHome.kt:1016-1022`：登记改为**封面那一块**（学 `GalleryScreen.kt:3122-3134` 的 `onGloballyPositioned` 挂法），并在 `onOpen` 那一条补 `GalleryFlyIn.capture`（宿主 `GalleryScreen.kt:1543-1546`）；② `GalleryHistoryScreen.kt:384`/`:241` 同一对处理；③ 三处登记统一走 `post.viewerKey`（收 §2.6 那对键，改 `GalleryScreen.kt:3134`）；④ 新增**一颗共享件 `GalleryEntryFlyBody`**，把 `PixivWorkScreen.kt:935-974` 与 `GalleryPostScreen.kt:1412-1437` 两处就地抄本换成它，并把 `GalleryPostScreen.kt:1492-1503` 换成既有 `GalleryExitFlyBody`（**换之前先按 §2.7 把那颗的形参改成 lambda**，否则是把播放器较好的绘制阶段取值降级成组合期取值） |
| 判据 | ① 一把**负向扫描**尺：`gallery/ui/**` 里 `drawImage(` + `addRoundRect(` 的成对出现次数 **恰好 = 2**（进场 1 + 离场 1），多一处就红；② 补上 `GalleryExitFlyBody.kt:116` 空头注承诺的那颗 `GalleryExitFlyBodyTest`，钉"进出两趟在同一进度上出同一 dst/src/radius"；③ 一把接线尺：数 `GalleryFlyIn.capture(` 与 `noteCardBounds(` 在各宿主文件里的**成对性**（有落点无 capture = 红），把 pixez 那条"两端同轨"纪律变成尺子而不是注释；④ 键尺：登记侧与查询侧都只许出现 `viewerKey`；⑤ 形状尺（§2.7）：`GalleryExitFlyBody(` 调用点实参里不许出现 `.value`，且**这颗改形状要排在"把播放器两处换成它"之前** |
| 规模 | 4 颗 UI 文件 + 1 颗新共享件，约 ±250 行；测试 +3 颗文件 |
| 风险 | 低。改的都是"谁截、截哪一块、飞体由谁画"，不动 alpha 曲线 |
| 真机验收 | 同一部 pixiv 作品分别从**主墙 / 首页排行榜那排 / 历史那一格**进入，四格读数都要一样：进会飞、出会飞、落点四角与卡封面重合 |

### 批 B · 语法对齐 pixez：**一条 expansion + 一幅真图 + 图像空间裁切**（核心，跨窗口仍成立）
| 项 | 内容 |
|---|---|
| 目标 | 把"飞截图"换成"飞**那块画面本身**"，从而**整批删掉淡出补丁** |
| 机制（8 件，逐件对着 §4.3/§4.4 的那一行改） | ① **飞行体换成活画面**：变形框里画的不再是一块位图，而是**与墙上那张卡同一颗 URL 解析件、同一个 `cardRatio`、同一档圆角**的封面（`GallerySettingsModel.wallUrl` + `VeneraCover`）；这一族必须只有**一颗**决定"这一张画用哪个地址"的函数（pixez 就是靠单函数保证两端同构，只在"允许不允许方图占位档"这一枚参数上分岔）。② **内容永不拉伸**：算式从"每帧重算居中裁"换成"当帧等比 scale = `maxOf(宽比, 高比)` 从该值走到 1" + "裁切窗 = 卡片矩形与落点矩形的逐边插值"，宽度驱动时贴左缘、高度驱动时居中收窄并反向补偿位移。③ **圆角锚点换掉**：`lerp(封面圆角 → 当帧那档容器圆角) ÷ 当帧 uniformScale`（不再是 `dst.width / to.width` 那一把）；设备物理圆角那一档我们没有现成件，先按"两档 token"落地，真机若还有角上补白再抬这一件。④ **一个标量两层**：`expansion`（0 = 卡片那一格，1 = 落位）同时定封面形、遮罩深、chrome 淡出与各段淡入；**底层不再缩放**（pixez 自己已经把那一半删成"只叠遮罩"，我们跟着走这一档更简单）；进场 / 离场 / 跟手 / 下滑四条路都换算到同一个标量（§4.2 的"一标量四源"）。⑤ **淡的对象换过去**：飞行期把**源卡**按 expansion 前 5%→10% 那一段 alpha 掉（跨窗口也能做——走 `ChromeCaptureSuppress` 那同一颗进程内快照件），于是屏上全程只有一份画面，`exitBodyAlpha` 与 `handoffBodyAlpha` **整族删掉**。⑥ **不等落点**：作品页第一帧就要有真实比例——详情未到时用**列表腿那条 post 自带的 `width`/`height`** 先算出第 0 页那一格（pixez 是同形做法：数据同步命中缓存 ⇒ 首帧就有真比例），网络回来后若比例被校正就 1~2 帧收敛；`withTimeoutOrNull(ENTRY_WAIT_MS)` + `entryGaveUp` 那两档随"等"这件事一起消失。⑦ **无锚的退路换成侧滑**：可见比（宽、高两枚各自）不足 25% ⇒ 当场判无锚、走全宽侧滑（顶层平移、底层 -25% 视差 + alpha 0.9），**不再**走"整页从下沿抬上来"；`landable` 为假那一帧要显式清激活态。⑧ **截图那一路降级为备胎**：只有活画面首帧真取不到（内存缓存没命中）才用，且 `FlyProbe` 要念得出走了哪条；失败不许静默交回（"宁可错慢不可静默交错"那条仓库纪律）。 |
| 顺带收掉 | ① §3 的 `clipBounds`：**定位一律 `boundsInWindow(clipBounds = false)`**（未裁的那一块才叫位置），截像素仍用裁过的那一块；② §2.3 的"整页缩成方块"当场消失（离场起点 = 那一页图块，不是 `fullFrameBox`）；③ §2.6 那对键；④ §2.4 那六条 alpha 曲线：**三条整条删**（`handoffPageAlpha`/`handoffBodyAlpha`/`exitBodyAlpha`）、**三条换成同一枚 `expansion` 的三个投影**；⑤ 播放器那一跳"页内左右滑不是 hero 场景"这一档要不要跟着撤（§4.3 第 3 条）——**这条留给用户定，默认不动** |
| 判据 | ① **纯函数尺**（能脱 gradle 单跑，走 `_probe/l0/run-judgment-tests.sh`）：新的那颗 `flightFrame(expansion, card, dst, imgW, imgH)` 钉"两端逐像素重合 + 全程等比不产拉伸 + 宽高两向覆盖都不足 ⇒ 另一向被裁 + 倒着走回同一帧 + 退化输入不产负尺寸 + 圆角随 scale 逆补偿单调"，写法照 `GalleryExitExpansionTest` 那 10 颗 + 扰动三笔流程；② 一把尺钉"作品页离场起点必须是那一页图块，不许是 `fullFrameBox`"；③ 一把尺钉"main 里不许出现无参 `boundsInWindow()`"（负向扫描一次列全违例，白名单只许缩短）；④ **接线成对尺**（批 A 那把的延长）：每个有落点登记的宿主都必须有 capture，反之亦然；⑤ 负向尺：`exitBodyAlpha` / `handoffBodyAlpha` 的调用次数 → 0（删掉的曲线不许被演回来）；⑥ 落点判式尺钉"可见比 <25% ⇒ 判无锚"并且**不许加纵横比门槛**（§4.3 冲突表第 4 行那条"注释里有、代码里没有"的陷阱，别照抄进来） |
| 规模 | 1 颗新纯函数 + `GalleryFlyIn` 换通道 + 两屏接线 + 1 颗跨窗让位件，约 **±600 行**（比初稿那档 400 行大，因为⑤⑥⑦三件是这轮现量之后才补的）；**这是唯一动"机制"的一批**，真机帧证必须逐档过 |
| 风险 | 中。① 活画面首帧可用性依赖缓存命中——列表腿与详情腿的地址**不同源**是已知的（预览档 vs `_p0` 中档），所以要按⑥把"第一帧用哪张"定死；② 源卡 alpha 掉那一档是跨窗口写别人的组合（进程内快照件），要防"忘了恢复 = 卡永久不见了"（`ChromeCaptureSuppress` 那两条复位路的同一族坑，复位必须有闹钟兜底）；③ 侧滑退路要新画一层，别把播放器那屏的既有退路改坏 |
| 真机验收 | 复用现成量具：`_probe/fly_bbox_track.py`、`fly_measure_2026-10-01.py`、`fly_corners_2026-10-01.py`、`_probe/anim/` 那条 3× `animator_duration_scale` 链路（`REPORT.md:215-222` 三条判据），**再加两条**：飞行段内不得出现"两份同图"的帧差；**整条淡出段在慢放里必须不存在**（这是本批的验收主语，不是副产物） |

### 批 C · 单窗口化（决定性那一步，**要用户点头才做**）
| 项 | 内容 |
|---|---|
| 内容 | 把 pixiv 作品页从 `PixivWorkActivity` 搬成 MainActivity NavHost 里的一枚目的地，于是 hero 可以是**同一窗口内的一个标量两层**（真 `sharedElement` 或我们那套 `GalleryExitExpansion` 直用），跟手返回/应用内拖拽/入出站共用一条算式，`PixelCopy` 整族可退场 |
| 依据 | 页内真共享元素在本仓**已经跑通**（首页预览行 ↔ 每日热门二级页那对，`GalleryScreen.kt:3359-3370` + `GalleryHomeSections.kt:417`），窗口内玻璃采样也有现成件（`rememberLayerBackdrop` / `blurBackdropSource`，`VeneraTopAppBar.kt:417-440`；墙被标成采样源那一行就在 `GalleryScreen.kt:1801`）⇒ **不需要新依赖，也不需要丢模糊** |
| 代价 | ① 要动 `feature/Navigation.kt`（**保护域**：路由映射表那一项，按仓库纪律要**显式授权**才动）；② 丢的是**窗口级** blur-behind 与**跨 Activity** 那套系统预测返回，换成页内自绘（我们有 `components/PredictiveBack.kt`，但手感要重验）；③ 已知两族坑会回来：`rememberSaveable` 载荷只认 Bundle 类型（平板分栏那次闪退）、导航条目退场重组（返回被打回默认值那一族）；④ 作品页与旧看图器两屏的隔离口径（用户 10-18 点名"两个隔离开"）要重新表述 |
| 判据 | 先出**一屏对照 demo**（只搬 hero 那一族，别的功能不搬），真机 A/B 录屏与批 B 同判据对比；用户点头才整批落地 |
| 规模 | 若做：`PixivWorkScreen` 1996 行的宿主换一层 + Navigation 一处 + 载荷/滚动回归，**这是本表里唯一"周"级的一批**，其余都是日级 |

### 批 D · 收尾（与任一批同行，不单独占一轮）
- 撤 `FlyProbe` 那批临时探针（`GalleryFlyIn.kt:134/189/239`、`PixivWorkScreen.kt:478/507/513/544/651/670`、`GalleryPostScreen.kt:606/645/677/701/709/916/919/969`）——手册行 233 说清"验收完一起撤"，**撤的前提是 §C 那六格真机读数已归档**；
- workbuddy 那一轮（2026-10-19 03:00-04:38，产出 `docs/anim-audit-2026-10-19/`）**既没 commit 也没进手册**：本计划落地时按批次把它的 D1-D5 与"未做两格（P0-3、P2）"一并入账并就地标注哪些被批 A/B 取代；
- `docs/rounds/gallery-hero-transition-and-artist-profile-2026-10-01.md`、`gallery-hero-predictive-back-vs-pixez-2026-10-13.md` 里被本计划改判的句子（尤其"这一排卡不接飞行不是缺陷"）要**划掉不删**。

---

## 6. 明确不做
- 不给 hero 加"开/关"设置项（本仓零容忍假开关；pixiv 与图库三站同一条路，不做站点特例）。
- 不引第三方转场库（Decompose / material-motion 都不为这一族引；页内那对已用 `BoundsTransform` 自持曲线）。
- 不动打码那一条红线：**打码命中两边都不飞**（`GalleryScreen.kt:3132-3134` 那处"不回写"是刻意的）。
- 不把 pixiv 作品页与旧看图器合并（用户 2026-10-18 点名隔离）。
- 不动下滑关闭那一支（用户拍过的"页面已跟手滑出就别再让图飞回来"）。

---

## 7. 待用户拍板的四件事（不动代码，先答这四件就能开批 A）

1. **口径改判**：手册行 213 那句"这一排卡不接飞行，不是缺陷"今天已被半接线推翻 —— 是把 pixiv 首页那一排**接上飞**（批 A 推荐），还是把它的落点登记**撤掉**让回程也不飞（两档自洽，观感不同，本鱼推荐前者，因为那排是 pixiv 域的主入口）。
2. **批 B 那一步要不要真机同步验**：批 B 改机制，屏上是"少了一整族淡出"的观感差。交付包 = APK + `adb logcat -d -s FlyProbe` + 三步操作（主墙进 / 排行榜那排进 / 返回），和以前一样按帧编号回贴。
3. **批 C 是否排在批 B 之后**：本计划的判断是**先批 B**（跨窗口也能拿到 pixez 的观感主体，且不动保护域），批 C 只在批 B 的"活画面"仍被"跨窗口"卡住某一格时才上——批 B 做完会知道答案，批 C 现在决定不了。
4. **作品页点某一页 → 大图播放器那一跳还要不要 hero**：pixez 把"点图进全屏看图器"当成**非 hero 场景**（那一跳不变形，只有列表⇄详情飞封面；§4.3 第 3 条）。我们那一跳现在是自己重截一帧再飞（批 230 修错位时接的，有尺子钉着顺序）。两个选择都自洽：留着 = 多一套转场但连贯；撤掉 = 少一套、少一处维护，代价是那一跳退回"抬上来"。**默认不动**，等批 B 做完再定（那时这一跳的实现会自动跟着换成活画面）。

---

## 8. 参考与出处
- 现量代码：`app/src/main/java/com/venera/compose/gallery/ui/{GalleryFlyIn,GalleryExitFlyBody,GalleryPostScreen,PixivWorkScreen,GalleryScreen,GalleryHomeSections,GalleryPixivHome,GalleryHistoryScreen,PixivArtistScreen}.kt`、`gallery/domain/{GalleryExitExpansion,GalleryHandoff,GalleryFlyGeometry}.kt`、`components/ComicSharedTransition.kt`、`components/ChromeCaptureSuppress.kt`
- 现量库 API：`androidx.compose.ui:ui-android:1.12.0-sources` → `commonMain/androidx/compose/ui/layout/LayoutCoordinates.kt:196/209`（`boundsInWindow(clipBounds = true)`）
- workbuddy 那一轮：`docs/anim-audit-2026-10-19/REPORT.md`（D1-D5、§四 P0-1..P2、§七自称五格全过）+ `.workbuddy-ai/memory/2026-10-19.md`
- 手册账：`docs/HANDOFF-2026-10-04.md:1122`（行 213）、`:1139`（行 230）、`:1140-1141`（行 231-232）、`:1142/1162`（行 233-⑤）
- pixez 机制：`docs/rounds/gallery-hero-predictive-back-vs-pixez-2026-10-13.md`、`docs/rounds/explore-and-gallery-remediation-2026-10-04.md`（批 H 对照全表）；许可纪律 `docs/rounds/gallery-pixiv-donor-projects-2026-10-16.md`（GPL-3.0 = B 档，只出结论不搬码）
