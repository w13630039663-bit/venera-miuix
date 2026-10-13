# 画廊 hero 与预测式返回：pixez 对照 + 批 1 地基（2026-10-13）

## 0. 触发

用户点名研究 <https://github.com/137458/pixez-miuix> 的「返回手势预测 + hero 共享元素」怎么做、与我们画廊差在哪。
研究完判定只有一格是真缺口（**我们的返回不跟手**），并把它切成两批；本文记调研结论、并行冲突核查、以及**批 1 已落地**的那部分。

## 1. 它的机制（HEAD @ 10-07，v0.9.109.5；10-13 逐文件读过）

全仓 `SharedTransitionLayout` / `sharedElement` / `PixelCopy` **零命中** —— 它的 hero 不是共享元素 API，也不是截图，
而是**一个标量 `expansion` 把目的地页面自己缩放**：

- 载体：`Modifier.cardExpandLayer`（`animation/CardExpandLayer.kt:234-280`）挂 `graphicsLayer`，飞的是**活组合**。
- 一个标量四条源：push/pop 弹簧的 `factor`、系统预测返回的 `progress`（`predictiveBackCardExpandExpansion(p) = 1 - p`，
  `CardExpandLayer.kt:409`）、应用内左缘拖拽的 `progress` —— 全部换算成同一个 expansion，喂同一个 `cardExpandFrame`。
- 几何：可见窗口矩形严格 = `lerp(卡片矩形, 容器矩形, expansion)`；`baseScale = maxOf(liveScaleX, liveScaleY)` 等比无畸变
  + 宽高**双向比例裁切**；宽度被裁时窗口按 `horizontalBias = 0.5` 绕页面中心收窄；本地圆角除以 `uniformScale` 逆补偿。
- 两层同相：顶层缩放、底层只叠 scrim（≤0.24），`|factor|` 互补；列表 `BACKDROP_MIN_SCALE = 1.0f` 恒不缩（防浅色底白边）。
- 重影靠**算式**不靠调参：源卡片 alpha 在 expansion `[0,0.05]` 恒 1.0（保证 SRC_OVER 合成恒等于 1），
  `[0.05,0.10]` 在顶层遮蔽下隐到 0；详情页附属 chrome 从 0.65 起淡 ⇒ 收缩后半程只剩作品本体。
- 锚点账本 `SharedBoundsRegistry`：卡片每次布局上报 `boundsInWindow()` + 圆角（`components/IllustCard.kt:196`）；
  转场中锁住正在飞的那颗；0 尺寸只忽略不注销；底层被自己变换过就存 `toStationaryBounds()`；
  滚出容器可视 75% ⇒ 判无锚，**优雅回退默认侧滑**；页内左右滑切过 ⇒ 按「当前展示 → 本次打开」两级候选找锚。
- 系统返回事件三道门闩（`MiuixPredictiveBackAnimation.kt:289-412`）：必须先 `onBackStarted` 后到进度才激活双层；
  `isBackConfirmed` 挡澎湃乱序补发；`onBackCancelled` 等回弹播完再回落单层，期间补发确认从当前位置连续收起；
  双层期挂 `InputConsumingOverlay` 吞输入。进度用 stiffness 3000 无回弹弹簧平滑（系统派发稀疏时不跳位）。
- 10-05 之后（**我们 10-04 那轮调研之后**才有的）：自研 `SeekableStackAnimation` + `StackTransitionScheduler`
  替掉 Decompose 的排队模型（push 转场中收到返回 ⇒ 两层互换方向、`p→1-p` 连续转向；只有 pop 链排队，队列长 1）；
  常规出入栈从 `tween(340ms)` 换成临界阻尼弹簧；应用内左缘 24dp 拖拽关页（全程 `PointerEventPass.Initial`、
  修饰符整页存续期挂载、阈值位移 0.35 或 2000px/s、跟手期 snap 1:1 直驱）。
- 一条崩溃教训（`0922d3d1`）：`cardExpandFrame` 里 `content(...)` 必须**单一调用点**，按「有无锚点」在两个调用点间切
  ⇒ movableContent 同帧双迁移源 ⇒ `SaveableStateProvider`「Key used multiple times」，桌面快速进出 3 轮内必崩。

## 2. 对照（只留判定所需的）

| 维度 | pixez | 我们画廊 | 判定 |
|---|---|---|---|
| 飞的载体 | 目的地页面本体 | `PixelCopy` 截图，真页面另起淡入 | 跨 Activity 结构性搬不过来 |
| 进度来源 | 一个 expansion，四路同式 | `fly`/`exitFly` 两条独立弹簧 + `tween(160)` | **缺口** |
| 跟手/可打断 | 跟手，能中途回弹、反向连续翻转 | 松手才播 | **缺口** |
| 锚点账本 | `SharedBoundsRegistry` | `GalleryFlyIn.noteCardBounds/cardBoundsOf/forgetCardBounds` | 已有等价物 |
| 落点裁切 | `maxOf` 等比 + 双向裁切 | `GalleryFlyGeometry.coverSourceRect` | 已有等价物（批 H 判过逐像素等价） |
| 交棒不重影 | 两档 alpha 窗口 [0,0.05]/[0.05,0.10] | `GalleryHandoff.kt` 把总不透明度钉成恒 1 | 已有等价物（另一种解法） |
| chrome 早退 | `DETAIL_CHROME_FADE_START = 0.65` | 原 `FLIGHT_FADE_FRACTION = 0.25` | 同一意图，已有 |
| 无锚回退 | 优雅回退默认侧滑 | 直接 `onBack()`，交系统跨 Activity 关闭动画 | 形状不同，都「不硬飞」 |

### 两处旧账改判（覆盖 `reference-pixez-hero-round-findings` 里的说法）

1. 「`a603ddee` 全幅对齐 / `32be4be2` 收缩窗口只露作品本体 被 revert 后从未重做」**已过期**：
   现 HEAD 的 `windowHorizontalBias = 0.5` 居中收窄（`cd6961d`）+ `DETAIL_CHROME_FADE_START = 0.65` 就是它的重做版。
2. 「它靠 Decompose 排队模型」不再成立：10-05 起换自研可打断调度层。

## 3. 并行施工核查（10-13 现量，第二个 agent = 会话 `47e55691`「阅读器续章与增强方案」）

核查时（14:0x）它是 `running`；**14:20 现查已是 `ready` + 上一回合 `interrupted`**（用户说「暂停了」），批 2 据此开工。
但它那批**至今未 commit** ⇒ 工作树仍压着未完成的改动，见 §9 那两条欠账。

**文件面零重叠**：我这批只碰 `GalleryPostScreen.kt`（开工前干净）、新增 `GalleryExitExpansion.kt`、新增
`GalleryExitExpansionTest.kt`；开工前 `git status` 里对方的 gallery M 名单是 `data/{GalleryBusinessApi,GalleryFeedCache,GalleryPorts,GelbooruClient}.kt`、
`data/android/{GalleryAdaptersAndroid,GalleryPortsAndroid}.kt`、`domain/{GalleryRanking,GallerySearchSource}.kt`、
`ui/{GalleryArtistProfileScreen,GalleryDailyScreen,GalleryForYouScreen,GalleryForYouViewModel,GalleryScreen,GallerySearchArea,GallerySearchViewModel,GalleryViewModel}.kt`，
新增 `domain/GalleryNotices.kt`、`domain/GelbooruWindowBoundary.kt`、`ui/GalleryNotices.kt`，另外在改 `app/build.gradle.kts`（正式证书重签 debug 包）与 `feature/favorites/FavoritesSplitPane*`。

真冲突在三处共享写面，避让办法：

1. **编译面**：`compileDebugKotlin` 会把对方未完成的改动一起编。⇒ 本批不产 APK、不碰 `outputs/`、不用 `--rerun-tasks`；
   判式红先对开工前快照看红在谁手里，红在对方施工面就记账不修。
2. **落点表的写入方在他们手里**：`noteCardBounds` 的两个调用点在 `GalleryScreen.kt:1612-1664` 与
   `GalleryDailyScreen.kt:192-224`，那两颗正在被改。批 1 不受影响（纯函数只吃矩形），**批 2 开工前必须现查那两处还在原位**。
3. **真机与 `_qa/android-apk-*`**：对方正在出 debug 包往设备装（MuMu `127.0.0.1:16384` 等三台在列）。批 2 要装机读数 ⇒ 错开时段。

顺带量到、不属于本批：`domain/GalleryNotices.kt` 与 `ui/GalleryNotices.kt` 同名两颗都在未跟踪列表里。不动、不判。

## 4. 批 1 已落地：离场那一趟收成一颗纯函数

新增 `gallery/domain/GalleryExitExpansion.kt`（105 行，纯 Kotlin，**刻意不碰 Compose/Android 类型**，
与 `GalleryFlyGeometry.kt` 同一条口径，能脱 gradle 单跑）：

- `exitExpansion(progress, from, to, sourceWidthPx, sourceHeightPx): GalleryExitFrame`
  —— 帧里装 `dst`（落点框）+ `source`（cover 源裁切，复用 `coverSourceRect`）+ `cornerRadiusScale`。
- `exitPageAlpha(progress, fadeFraction = EXIT_FLIGHT_FADE_FRACTION)` —— 页面与幕布共用那一个 alpha。
- `FlyRect` 是 domain 侧的矩形；`Rect → FlyRect` 的换算留在调用侧（`GalleryPostScreen.kt` 尾部那颗私有扩展）。
- 轴的语义：`progress 0 = 还站在画面框原位`，`1 = 完全收进卡片`。与 pixez 的 `expansion` 同轴反向（`1 - p`）。

两处刻意保留原样，不是笔误：**几何吃钳过的进度、alpha 吃原始进度**（原来是两句话，合流会改掉两端各自越界时的观感）；
**帧里不装 alpha**（幕布与整页原来读的是同一个 `exitFadeAlpha()`，拆成两格就是凭空造第二个真相）。

`GalleryPostScreen.kt` 侧净差 **+38/−27**（1772 行那颗文件）：离场 `drawBehind` 那四行插值 + 源裁切 + 圆角
换成一次 `exitExpansion(...)` 调用；`exitFadeAlpha()` 换成 `exitPageAlpha(...)`；私有常量
`FLIGHT_FADE_FRACTION` 连同它的历史说明搬进 domain 成 `EXIT_FLIGHT_FADE_FRACTION`。**行为逐位不变，观感零变化**。

## 5. 判式与读数

新增 `app/src/test/java/com/venera/compose/gallery/GalleryExitExpansionTest.kt`（237 行，10 颗 `@Test`）。
分两类：**与搬走之前的就地表达式逐位对表**（把原 UI 里那几句复刻成 `legacy()`，`pageAlpha` 那格**写死字面量 0.25**
而不复用常量 —— 复用会让"改分数"两边一起走、对表永远绿），以及**独立于复刻的行为规格**（起点/终点逐像素重合、
宽度单调走向落点且不越界不出 0、倒着走回同一进度得到同一帧、页面让开只在首程、退化输入不产负尺寸、
位图尺寸拿不到交回整张）。三档输入形状：竖图收回竖卡、横图收回竖卡（cover 会裁）、宽屏那档**落点比起点还宽**。

跑法走 `_probe/l0/run-judgment-tests.sh`（脱 gradle，正是为"别人把单测源集编坏"准备的这颗量具）：

| 笔 | 时间 | 终点状态 | 读数 |
|---|---|---|---|
| 基线 | 14:08:51 | 还原态 | `compile rc=0`，**OK (185 tests)**，0 失败 |
| P1 分数 `0.25f → 0.3f` | 14:09:36 | 已还原 | `Tests run: 185, Failures: 2` |
| P2 摘掉几何钳制 | 14:10:19 | 已还原 | `Tests run: 185, Failures: 1` |
| P3 圆角分母换成起点宽 | 14:11:59 | 已还原 | `Tests run: 185, Failures: 3` |
| UI 侧编译 | 14:12 | — | `:app:compileDebugKotlin` rc=0（不产 APK） |
| 还原后复跑 | 14:15:00 | 已还原 | `compile rc=0`，**OK (185 tests)**，0 失败；`grep marker` 零命中 |
| 入库那条路 | 14:14 | 不在本批 | `:app:testDebugUnitTest --tests "com.venera.compose.gallery.*"` **BUILD FAILED**，25 处 `e:` 全在 `app/src/test/.../ui/tokens/{SurfaceMaterialPolicyTest,TopBarFloorGuardTest}.kt`，**零处提到 gallery** |

那 25 处的形状是"主源里明明有的符号，单测编译取不到"：`SurfaceMaterialPolicy.kt` 里
`enum class VeneraGlassTier/VeneraGlassRole/VeneraGlassHighlight` 与 `object SurfaceMaterialPolicy` 逐条在位
（`:36/:48/:68/:82`），而 `components/venera/VeneraCard.kt:9` 正常 import 同一个符号、`:app:compileDebugKotlin` rc=0。
⇒ 读成**共享 build 目录里 main 输出停在半态**（对方现场那笔 `packageDebug` 因 APK 被独占而 "Failed to clean up output files" 之后），
不是判式内容红。按避让纪律不 `--rerun-tasks`、不清 `app/build`，等对方那批收口后复跑这条入库路再认账。
这条正是那颗量具头注早已记过的同族事件（"别人正在改，整个单测源集当前编不过"）。

第一遍这把尺子**咬到的是我自己**：`宽度随进度单调收缩` 在第三档（600 → 900，落点更宽）当场红
（读数 `宽度不单调：600.0 -> 603.0 @0.01`）。要钉的是「单调**走向落点**、不跑出起点与落点之间、始终有面积」，
不是方向 —— 已按这条重写。

## 6. 量具改动（`_probe/` 被 `.gitignore:38` 忽略，这三行登记不入库）

- `kotlin-reflect` 的 pin 从 `2.2.10` 改到 `2.2.0`：缓存里 2.2.10 已经不存在（现量 2.2.x 只有 2.2.0），
  脚本上一次是**坏的** —— `find` 失败后 `RF` 成空串，classpath 拼坏，`java` 只回一句找不到主类，
  读起来像判式红（`compile rc=1` 而零输出）。
- 加了一道身份闸：九枚 jar 任一枚取不到就打印「缺 jar: 变量名」并 `exit 2`，不再把"量具坏了"演成"码坏了"。

## 7. 批 2 已落地（同日 14:20–14:46）：跟手返回

对方暂停（会话 `47e55691` 状态 `ready`、上一回合 `interrupted`）后开工。
**它的三道闸门实际只过了两条**：文件面零重叠 + 真机时段空出来了，但那批**仍未 commit**
⇒ 工作树至今压着他们的 `gallery/data/*`、`Gelbooru*`、`ui/tokens/*` 未提交改动。别把这段读成"闸门全过"。

**Activity 侧一行没动** —— 页内就够：`androidx.activity.compose.PredictiveBackHandler`
在 `ComponentActivity` + manifest `enableOnBackInvokedCallback="true"` 下直接给得到系统进度。
顺带纠正 §关键文件 里的一处假路径：大图页宿主是**根包**的 `app/src/main/java/com/venera/compose/GalleryPostActivity.kt`，
**不在 `gallery/ui/` 下**（那是子代理报的，我抄进了方案）。

- 新增 `gallery/domain/GalleryExitGesture.kt`（186 行）：`Idle/Arming/Flying/Committed` 四档
  × 七类事件 × 五条指令，把 pixez 那三道门闩 + 我们自己那两条退路收成纯函数（不碰 Android/Compose 类型）。
  四道闸门：① 只有稳态能被起手激活，乱序 / 杂散进度不激活；② `Arming` 期间视觉钉在 0（像素没到不许让开）；
  ③ 截不到帧、或没帧就被确认 ⇒ 直接 finish，不硬飞；④ `Committed` 之后补发的起手 / 进度 / 取消全丢。
- 新增判式 `GalleryExitGestureTest.kt`（204 行，14 颗 `@Test`），含"一整笔手势从头走到提交"的轨迹用例
  （`idle→arming→arming→flying×4→committed`）与"回弹之后还能重新起手"。
- 页内侧（`GalleryPostScreen.kt`，两批合计 **+179/−44**）：
  `BackHandler(enabled = !infoOpen) { leavePage() }` → `PredictiveBackHandler(enabled = !infoOpen) { events -> … }`；
  `leavePage()` **保留**给没有进度事件那一笔（三键点返回、`adb` 送按键）；两条路共用 `exitRectsOrNull()` 守卫 +
  `clearChromeForExit()` + 同一颗 `exitExpansion`。sheet 那条 `BackHandler(enabled = infoOpen)` 原样。
- 跟手用 `spring(dampingRatio = NoBouncy, stiffness = 3000f, visibilityThreshold = 0.0005f)` **追赶**、不 `snapTo`
  （澎湃 / MIUI 派发稀疏时 snap 就是整页跳位）。`components/PredictiveBack.kt` 那 600f 是回弹档，两档刻意没共数。
- 回弹收尾次序由决策层保证：`SetVisualTarget(0f)` → `Restore`（玻璃 `addFlags` 挂回、状态栏图标翻回、
  `leaving = false`、**并清掉 `exitFrame/exitStart/exitTarget`** —— 批 2 之前 `leaving` 是单行道所以不必清，
  现在回弹后人还能继续翻页，不清就是**旧那一张的截屏压在新那一张上面**）。
- **下滑关闭那一支一个字没动**（`:791-792` 记着用户拍过的决定）。

三笔自审都是**判式先红、码后改**：

1. `BackWithoutProgress` 原本交 `[Prepare, FlySelfDriven]`：`Prepare` 先把 `leaving` 置真，
   而 `leavePage()` 开头就是 `if (leaving) return` ⇒ **三键点返回一帧都不飞**。
   读数 `expected:<[FlySelfDriven]> but was:<[Prepare, FlySelfDriven]>`（14:36:17，199 里唯一红的一条）。
2. 本地函数不能前向引用：备料与事件分派互相引用编不过（3 处 `Unresolved reference`，14:29）
   ⇒ 备料并进 `Prepare` 那一支、只回调自己。
3. 一跑报 `OK (185 tests)` 与上一笔**一模一样** —— 新判式只登记进 `_probe` 的 `TEST` 而漏了 `CLASSES`，
   于是**被编译但没被跑**，是假全绿。补登记后才是 `OK (199 tests)`。
   ⇒ 加判式后第一件事是**用例数必须比上一跑大**（大多少 = 新那颗的 `@Test` 数，`grep -c "@Test"` 现数）。

| 跑法 | 时间 | 读数 |
|---|---|---|
| 脱 gradle 独立跑（含批 2 决策层） | 14:39:46 | `compile rc=0`，**OK (199 tests)**，0 失败 |
| 入库路 `:app:testDebugUnitTest --tests "…gallery.*"` | 14:41:52 | BUILD SUCCESSFUL；`GalleryExitGestureTest` **14/0**、`GalleryExitExpansionTest` 10/0 |
| 收尾再复跑（清场那次改动之后） | 14:46:21 | BUILD SUCCESSFUL + **OK (199 tests)** |

### §5 那 25 处红的归属改判

14:14 那 25 处 `Unresolved reference`（符号在主源逐条在位、`:app:compileDebugKotlin` 却 rc=0）——
**成因已由对方日志自证**：它删过那颗坏掉的 `app/build/.../classes.jar` 并强制重出，与我那趟单测重叠
（它原话："两个怪现象合到一处了：并行的另一路 AI 在同一条 `app/build` 上跑构建"）。
⇒ 不是"半态猜测"而是**已证实的并发构建互踩**；14:20 复跑即 BUILD SUCCESSFUL。本批全程没动别人的文件、没清 `app/build`。

## 8. 真机待验清单（批 2 只能屏上定案）

1. 侧滑拖到一半停住 —— 图是否停在**对应收缩量**上（不跳、不滞后）。
2. 拖到一半松开回弹 —— 是否连续、背景玻璃**挂得回来**、回弹后**翻页不再出现旧截屏**。
3. 拖到底提交 —— 最后一帧是否与卡片四边重合；有没有**叠第二套窗口动画**（系统与我们的架谁在播，
   看 logcat `TransitionRequestInfo { type = PREDICTIVE_BACK` 与 `BackAnimationController$BackTransitionHandler`）。
4. 三键导航点返回 —— 应当还是原来那趟自驱弹簧。
5. 无落点那一笔（翻到墙外、打码、收藏页进来的那张）—— 现在交回系统关闭动画，观感要现量。
6. `PixelCopy` 那 1~2 帧的 `Arming` 是否真看不出来（手指快甩时会不会出现"帧到了才动"的滞后）。
   本机 `navigation_mode = 2` 只有左缘 ⇒ 右缘那分支在这台机上**物理不可达**，"没复现"不等于"没问题"。

## 9. 欠账

- **真机那一格没做**：批 1 观感零变化可以不做，批 2 必须做 ⇒ 见 §8 那六条。包由用户试，本批不产 APK。
- `docs/HANDOFF-2026-10-04.md` 批次流水那一行**仍未追**：对方那批（Gelbooru 定界 + 平板分栏 + debug 包重签）
  至今未 commit，工作树还压着他们的 `gallery/data/*` 与 `ui/tokens/*` 未提交改动。等落地后**现数表尾**再写。
- 开工闸门三条实际只过了两条（文件面零重叠 + 对方已停笔 = **暂停而非收口**）。这条如实记着，别读成闸门全过。
- `_probe` 那三处登记（`kotlin-reflect` 版本回退 + 新增的「缺 jar 就报身份」那道闸）**不入库**：
  `.gitignore:38` 忽略 `_probe/` ⇒ 换机器的人拿不到，入库那条 gradle 路才是唯一持久的判式口。

