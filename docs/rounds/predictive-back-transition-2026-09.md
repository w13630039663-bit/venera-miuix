# 设置入口转场 / 预测式返回：事实核对与优化方案

日期：2026-09-22 ｜ 分支：compose-migration ｜ 状态：待用户拍板，未动代码

---

## 0. 这份文档解决什么

上一会话（chat 8ca6a55d）留下的结论里，有 **4 条与库源码/包体实测不符**。其中两条直接决定了"要不要升 2.10.1"和"退出方向能不能跟手"，所以先把事实钉住，再谈优化。所有结论都来自 gradle 缓存里的 classes.jar 与从 Google Maven 拉的 sources jar，不靠猜签名。

工作区现状：4 个文件未提交（`FREEZE-STATEMENT.md` / `Navigation.kt` / `HomeScreen.kt` / `libs.versions.toml`），46 笔本地提交未 push，00:16 打过一版 debug 包但**未验收**，真机当前**未连接**（`adb devices` 空）。

---

## 1. 事实核对（逐条给证据）

### 1.1 ❌「2.9.x 一个 predictive 符号都没有」——错

grep 两个版本的 `navigation-compose` classes.jar：

| 符号 | 2.9.8 | 2.10.1 |
|---|---|---|
| `predictiveBackHandler` / `predictiveBackCancelAnimation` | ✅ 有 | — |
| `predictivePopEnterTransition` / `predictivePopExitTransition` | ❌ 无 | ✅ 有 |

`navigation-runtime` 两版都干净——上一会话大概只 grep 了 runtime，没看 compose 产物。

**2.9.8 的 `NavHost.kt` 已经是真·进度驱动**（源码 511–545 行 + 927–933 行）：
`PredictiveBackHandler(...)` 收 `Flow<BackEventCompat>` → `progress = it.progress` → `transitionState.seekTo(progress, previousEntry)`。拖动期间走 `inPredictiveBack` 分支，用的是 **`popEnterTransition` / `popExitTransition`**。

→ 「跟手」这个能力 2.9.8 就有，且不需要 activity 1.13、不需要 navigationevent。

### 1.2 ❌「手势进度是标量，拿不到左右边缘信息，退出方向跟随手势做不到」——错，而且错在 API 面上

2.10.1 的签名把边缘**直接作为 lambda 参数给你**（`DefaultNavTransitions.android.kt`）：

```kotlin
public actual val predictivePopExitTransition:
    AnimatedContentTransitionScope<NavBackStackEntry>.(swipeEdge: Int) -> ExitTransition
```

`NavHost.kt:869–890` 实际调用 `predictivePopExitTransition.invoke(this, swipeEdge)`；`swipeEdge` 由 `NavHostEventHandler` 从 `NavigationEvent.swipeEdge` 采集，常量在 `androidx.navigationevent.NavigationEvent`：`EDGE_LEFT = 0` / `EDGE_RIGHT = 1` / `EDGE_NONE = -1`。

→ **用户原始的「退出方向跟随手势」是可实现的。** 当前工作区代码写成 `predictivePopExitTransition = { ... }`，把 `swipeEdge` 整个丢掉了，等于花了一次升级的代价没用上。

### 1.3 ✅ 但 2.10.1 确实强制抬高 activity，这条记录是对的

`navigation-compose-android` 各版本 `.module` 声明的 runtime 依赖：

| 依赖 | 2.9.8 要求 | 2.10.1 要求 | 项目 toml 钉值 | 实际解析 |
|---|---|---|---|---|
| activity-compose | 1.8.0 | **1.13.0** | 1.9.3 | 1.13.0 |
| lifecycle-* | 2.9.0 | 2.9.1 | 2.8.7 | 2.11.0 |
| navigationevent-compose | — | **1.1.2（新增）** | — | 1.1.2 |
| Compose | 1.7.2 | 1.10.5 | 已被 miuix 抬到 1.12.0 | 1.12.0（未被 2.10.1 拖动） |

`dependencyInsight` 证实 `activity-compose:1.13.0` 这条边来自 `navigation-compose-android:2.10.1`。

**注意新增的 navigationevent 链路**：2.10.1 的 NavHost 换成 `NavHostEventHandler : NavigationEventHandler`，起手就 `checkNotNull(LocalNavigationEventDispatcherOwner.current)`。而本项目的 `FREEZE-STATEMENT` / `SearchScreen.kt:1435,1578` 记过一次**真机复现**的同族崩溃：miuix `OverlayBottomSheet` 内容在独立窗口，CompositionLocal 不跨窗口传播 → `IllegalStateException`。现在 NavHost 也吃这个 CompositionLocal，属于同一风险面。缓解事实：`LocalNavigationEventDispatcherOwner` 是 `compositionLocalWithHostDefaultOf(...)`，回落到 `View.findViewTreeNavigationEventDispatcherOwner()`，而 `setViewTreeNavigationEventDispatcherOwner` 由 **activity 1.13.0 的 `ComponentActivity` / `ComponentDialog`** 写入（已在 aar 里 grep 到），`MainActivity : ComponentActivity`，所以根 Composition 不会崩。**跨窗口的老风险没有因此消失**，仍需真机覆盖 Dialog/Sheet。

### 1.4 ❌「debug 包体 85MB → 125MB 是这次升级的代价」——观察真、归因错

逐个 zip 条目实测（解析中央目录 + local header extra）：

| | 09-20 03:55 `venera-arm64-debug.apk` | 09-22 00:16 `app-arm64-v8a-debug.apk` |
|---|---|---|
| 文件 | 79.5 MiB | 118.9 MiB |
| **压缩内容合计** | **78.4 MiB** | **78.9 MiB** |
| 条目间填充 | 0.78 MiB | **39.59 MiB** |
| 最大单处填充 | 535 KiB（`classes13.dex` 前） | 29.3 MiB（`classes5.dex` 前） |

抽样验证那 29.3 MiB 区段：1 MiB 内仅 160 个非零字节 → **纯 0 填充**。`classes.dex` 40.4 → 40.7 MB、assets 3.3 → 3.3 MB、lib 0.8 → 0.8 MB 基本没动。

→ 这次升级的**内容增量约 0.5 MiB 量级**（其中还混着 09-20 以来 16 笔提交的自有代码），40 MiB 是打包期对齐填充，与依赖升级无因果关系。基线也不对：拿的是两天前、跨 16 笔提交的包。

**收尾实证（2026-09-22 01:42）**：`--rerun-tasks` 全量重跑（44/44 executed）后，`app-arm64-v8a-debug.apk` = **79.0 MiB**，与升级前 09-20 基线 79.5 MiB 基本相等。00:16 那版的 118.9 MiB 是**脏增量构建**留下的对齐填充。结论定死：包体没有因为 navigation 2.10.1 增长；今后比包体一律用全量重跑，不要比增量产物。

---

## 2. 三处越界改动（已逐条判定，见末列）

| # | 改动 | 越界在哪 | 判定 |
|---|---|---|---|
| A | `libs.versions.toml`：navigationCompose 2.8.9 → **2.10.1** | 此前批准范围只到 2.9.x；连带 activity 1.13.0 + lifecycle 2.11.0 + 新引入 navigationevent 链路 | **追认保留**（理由换成 1.2 的 swipeEdge，不是原记录那条） |
| B | `Navigation.kt`：NavHost 级 `predictivePop*Transition` | 你点名的是**设置入口**，但这是 NavHost 全局参数，**详情页、漫画源页等所有子页返回**一起变 | **保留并改为方向横滑**（影响面仍是全局，见 §6-3 要真机确认） |
| C | `Navigation.kt`：内容层全屏 `BlurEffect` 返回模糊 | 判据是「任一子页 → 主 Tab」，等于每次离开详情页都全屏糊一下；且未经批准新增全局 GPU 开销 | **已撤销** |

`HomeScreen.kt` 右上角三枚图标换 miuix `IconButton` 一条你已确认保留，不计入。

（另：工作区有未跟踪的 `debug.log`（09-19 的 crashpad 日志）与 `.dsh-vision-router/`，与本轮无关，仅报告，未动。）

---

## 3. 当前实现里一个具体的观感缺陷（源码级，不依赖真机）

2.10.1 `NavHost.kt:865–900` 是三分支：

```
inPredictiveBack            → predictivePop{Enter,Exit}
else isPop                  → pop{Enter,Exit}
else                        → enter/exit
```

`onBackCompleted()` 里 `resetState()` 把 `inPredictiveBack` 置 false，同时 `popBackStack()` 让 `isPop` 变 true——也就是**松手提交那一帧，转场从 predictive* 切到 pop***。

当前工作区两者不同构：predictive 是 `fade + scale 0.92 / 400ms`，pop 是 `slide + fade / 300ms`。所以跟手拖到一半没问题，**松手瞬间会从"缩放"跳成"横滑"**。这大概率就是"结果尚未验收"的那个违和感来源。

→ 优化第一原则：**predictive 与 pop 必须同一族**，差异只允许在方向上。

另外 1.2 提到 `0.92 / 400ms` 两个数字：既不是 AOSP 系统值，也不是 AndroidX 默认值（库默认是 `fadeIn(spring 1.0f/1600)` + `scaleOut(0.7f)`），也没有用应用内现成口径（本文件 `tween(300)`、`VeneraMotionTokens` 120/220/320）。按既有约定应换成现成口径。

---

## 4. 路线变更：B 已落地 → 改走 A（真跨 Activity）

### 4.1 为什么换

按用户要求去查外部实现后，**推翻了我自己上一轮的一条断言**。我当时说「真·跨 Activity 拿不到返回模糊，两者互斥」，并据此把用户导向 B —— 这条断言是错的：

- Android 16（API 36）起，**targetSdk ≥ 36 的应用由系统默认施加 back-to-home / cross-task / cross-activity 三套预测动画**，应用零代码，且系统不再调用 `onBackPressed`、不再派发 `KEYCODE_BACK`。本项目 targetSdk = **37**，正落在里面。
- 跨窗口模糊有系统通道：`WindowManager.LayoutParams.setBlurBehindRadius` + `FLAG_BLUR_BEHIND`。已在本地 `android-37.0/android.jar` 逐个 class 核实存在：`WindowManager` 侧有 `isCrossWindowBlurEnabled` / `addCrossWindowBlurEnabledListener` / `removeCrossWindowBlurEnabledListener`，`LayoutParams` 侧有 `get/setBlurBehindRadius`。AOSP《Window blurs》明确普通应用可用、无权限要求、建议半径 ≤150px。
- 也就是说 **A 能同时给出用户要的三样，而且缩放/圆角/遮罩是系统原值，一个数字都不用我们写**；B 只能给复刻。

### 4.2 A 的前置与已定形态

A 做不到一步到位：设置子树有 **3 个越界出口**指向 MainActivity 独有的目的地 ——
`LocalComicRoute` → `ReaderRoute`（要传 `ReaderSession`，**含非序列化对象**，仓库里早有结论：「阅读会话含非序列化对象，交给宿主 ViewModel 暂存」）、`LocalComicRoute` → `DetailRoute(...)`、`StatsRoute` → `TagSearchRoute(...)`。

彻底不妥协需要 Detail/Reader 也 Activity 化（整 App 架构迁移）。用户选定当前形态：**9 个目的地全搬进 SettingsActivity 自持的 NavHost，3 个越界出口 `startActivity` 回 MainActivity**（默认 launchMode 新建实例压在本页之上，返回仍落回设置；代价是外壳重建一次）。

### 4.3 实施清单（全部编译/单测/打包通过）

| 文件 | 改动 |
|---|---|
| `SettingsActivity.kt`（新） | `enableEdgeToEdge` + `VeneraTheme { VeneraSettingsHost() }`；blur-behind 走 `addCrossWindowBlurEnabledListener`（onStart 注册 / onStop 注销），设备不支持时**不挂**，不画假的；防窥偏好订阅 |
| `feature/SettingsHost.kt`（新） | `VeneraSettingsHost()` 承载 9 个目的地；`SettingsEscape` sealed + `SettingsEscapeHandoff` 一次性进程内槽 + `consumeSettingsEscape()` |
| `Navigation.kt` | 齿轮改 `startActivity(SettingsActivity)`；删 `composable<SettingsRoute>`；**把横滑转场配方抽成 `veneraEnter/Exit/PopEnter/PopExit/PredictiveEnter/PredictiveExit` 一组 internal 函数**，两个 NavHost 共用同一份（配方复制两份必然漂）；消费越界交接槽 |
| `MainActivity.kt` | 防窥 `FLAG_SECURE` 订阅从私有方法提成 `ComponentActivity.applySecureScreenPreference()` 扩展，两个宿主共用 |
| `AndroidManifest.xml` | 注册 `.SettingsActivity`（`exported=false`）；`enableOnBackInvokedCallback="true"` 在 `<application>` 上，自动覆盖新 Activity |

blur-behind 半径取 **18dp**：沿用应用内既有的「内容重度模糊」口径（`ComicTileLayout` 打码封面 `blur(18.dp)`），不造新数字，且远在 150px 建议上限内。

齿轮改跨 Activity 后，MainActivity 图里的 `ContentGuardRoute` / `SyncBackupRoute` / `LogViewerRoute` 三个目的地**再无任何入口**（只剩 SettingsHost 侧有），已连带删除；`DownloadRoute` 仍被首页侧的本地漫画入口使用、`ComicSourceManage`/`FavoriteImages`/`Stats`/`LocalComic` 仍被首页顶栏直接使用，故两边图各自保留。另：`Navigation.kt` 顶部有几个 import（`Icons`/`Icon`/`IconButton`/`PaddingValues`/`systemBars` 等）**在 HEAD 上就已经只剩 import、没有使用点**，已逐字比对确认不是本轮造成的，不顺手清理（`getValue` 看着未用但 `by` 委托必需，不能删）。

### 4.4 一处顺带补回的功能回归

`FLAG_SECURE` 原先只挂 MainActivity。本地漫画 / 收藏图这两页会露封面，搬进 SettingsActivity 后若不同时挂，「设置 → 屏蔽与过滤」里那个开关对它们就**静默失效**。已抽成共用扩展补上。

### 4.5 上一轮 B 的成果去哪了

方向横滑那套没有作废 —— 它被抽成 §4.3 里的 `veneraXxx` 共用配方，现在**同时服务两个 NavHost**：MainActivity 里详情页等子页的返回、以及 SettingsActivity **内部**子页之间的返回，仍然是跟手 + 方向感知。跨过 Activity 边界那一下（齿轮进 / 返回出）才交给系统。

返回模糊也不再是自绘：改由 blur-behind 承担。形态与之前不同 —— 是「设置窗口在，背后就糊；窗口缩离即清晰」，由系统合成，**不是**一条随手势进度回抽的 220ms 收束动画。这一点必须真机看是否符合预期。

---

## 4.6 第二批：设置二级页也改成系统跨 activity（用户验收后追加）

用户对齿轮进/出设置的动画判定「没问题」，要求「设置里的全部二级界面都改成这样」。

同一条规则决定了实现形态：**设置二级页原先同在 `SettingsActivity` 的 NavHost 里，系统不认为是换页，拿不到那套动画。** 要给，就必须每页真的跨过 Activity 边界。

不必写 8 个类：

| 文件 | 改动 |
|---|---|
| `VeneraSubActivityBase.kt`（新） | 抽象基类：边到边契约 + 防窥订阅 + blur-behind 生命周期（onStart 注册 / onStop 注销）+ `VeneraTheme` 包裹，子类只提供一个 `@Composable abstract fun SubScreen()` |
| `SettingsSubActivity.kt`（新） | **8 个二级页共用这一个类**，靠 `EXTRA_SCREEN` 传 `SettingsSubScreen` 屏名分发；缺 extra 直接 `error()` 炸出来，不给兜底默认值（避免静默停在错误的页上） |
| `SettingsActivity.kt` | 瘦成 `override fun SubScreen() = VeneraSettingsHost()`，其余逻辑上收基类 |
| `feature/SettingsHost.kt` | `VeneraSettingsHost` **不再需要 NavHost** —— 8 个入口全改成 `openSettingsSubScreen(...)`；新增 `SettingsSubScreen` 枚举 + `Context.openSettingsSubScreen` + `VeneraSettingsSubHost(screen)` 分发器 |
| `AndroidManifest.xml` | 注册 `.SettingsSubActivity` |

几何沿用同一份契约：二级页 composable 本身不吃 `modifier` 参数，所以外面包一层 `Box` 消费 `navigationBars` 底部 inset，与之前挂在 NavHost 上的 padding 等价。

### 4.6.1 这一批带来的两处不一致，得说明白

1. **同一页两套动画**：`源管理 / 收藏图 / 统计 / 本地漫画 / 下载` 也能从**首页顶栏**直接进，那条路径仍在 MainActivity 的 NavHost 里，走的还是自绘横滑。用户点名的是「设置里的二级页」，所以首页侧没动。→ **四处入口已全部收口**（§4.8 源管理、§4.9 统计/本地漫画/收藏图），首页顶栏不再有自绘横滑的直达目的地；`下载` 只剩设置侧一个入口。
2. **二级页之间互跳也是跨 Activity**：例如下载页跳本地漫画，会再叠一个 `SettingsSubActivity` 实例。返回顺序与原来一致，但每一跳都是一个真 Activity 实例，栈深时内存占用比 NavHost 目的地高。

`veneraXxx` 那组转场配方现在只剩 MainActivity 一个消费者（SettingsActivity 已无 NavHost），保留是为了跨 Activity 之后仍要给主页内子页（详情等）返回用。

### 4.6.2 ⚠ 上一节打错了层级：设置真正的「二级界面」是页面内部栈

用户验收后指出：**设置主页进各分区、以及分区返回的动画仍然生硬，且没有模糊**。真机只读探测给出的定位链条：

- 用户停在「屏蔽与过滤」时，`topResumedActivity` 仍是 `SettingsActivity`，且整段日志里 `SettingsSubActivity` 的 START 总数没有增加 → **那一屏根本没有换 Activity**。
- 读 `feature/settings/SettingsHome.kt` 才看到真相：设置有一张**自己的内部导航栈**，与 `Navigation.kt` 的目的地表无关：

```kotlin
private val categories = listOf(  // 7 个分区：探索 / 屏蔽与过滤 / 阅读 / 外观 / 本地收藏 / 应用 / 网络
var stack by rememberSaveable { mutableStateOf(listOf("home")) }
fun push(page: String) { ... }
PredictiveBackStack(entries = stack, onBack = { stack = stack.dropLast(1) }) { route -> when { ... } }
// 行点击： .clickable { onPush(category.route) }
```

  `components/PredictiveBack.kt` 里那个自绘栈做的是 `translationX = size.width * p * direction`（下层 `-0.25f` 视差）的**纯横滑**——所以观感生硬，而且窗口层面没有换页，`blur-behind` 无从生效。

- **我上一轮搬进 `SettingsSubActivity` 的 9 个路由是第三层**（分区页内部的行再往下钻才到），不是用户点名的第二层。失误原因：我只读了 `Navigation.kt` 的目的地清单就认定那是设置的页面结构，没去读设置页自己的导航实现。

### 4.7 改法一落地：7 个分区 + 规则子页改成跨 Activity

| 文件 | 改动 |
|---|---|
| `feature/SettingsHost.kt` | `SettingsSubScreen` 由 8 项扩到 16 项（7 分区 + `BLOCKING_RULES` + 8 叶子页）；新增 `EXTRA_ARG` 通道；`VeneraSettingsSubHost(screen, arg)` 统一分发，跳转回调就地用 `context.openSettingsSubScreen` 构造 |
| `feature/settings/SettingsHome.kt` | **删掉内部栈**：不再持有 `stack` / `PredictiveBackStack`，只剩首页内容 + 外层顶栏；`SettingsCategory.route: String` 改成 `screen: SettingsSubScreen`（去掉字符串路由这层间接）；顶栏条件 `if (stack.last()=="home")` 随之消失 |
| `feature/SettingsScreen.kt` | `AndroidSettingsScreen` 的 8 个 `onNavigateToX` 回调变成死参数，删掉，签名收成 `AndroidSettingsScreen(onBack)` |
| `SettingsSubActivity.kt` | 加 `EXTRA_ARG`；`BLOCKING_RULES` 缺 arg 直接 `require` 炸，不给兜底 |
| `components/PredictiveBack.kt` | 只删已无使用者的 `PredictiveBackStack` 函数及其 3 个孤儿 import。**整个文件不能删**：`rememberPredictiveBackState` / `PredictiveBackOverlay` 仍被收藏页、关注更新页、历史页的选择模式使用 |

几何与状态等价性：分区 composable 自带顶栏与返回箭头，所以子页只需给 `onBack = finish()`；底部 `navigationBars` inset 由宿主外层的 `Box` 消费，与之前挂在 NavHost 上的 padding 等价；首页滚动位置原先靠栈内「保持组合」留存，现在由被 stop 的 Activity 实例留存。

一处**刻意没改**的观感细节：首页点分区原先没有触觉反馈（`push()` 里没有 haptic），现在也没加 —— 保持原样，不顺手改维度。要加是一行的事。

### 4.7.1 仍需真机判定

`PredictiveBackStack` 原本已经用了 `state.fromRightEdge`（方向感知），说明旧自绘栈并非完全无方向。改成跨 Activity 后方向交由系统，**这一层是否更贴合预期必须真机看**：7 个分区逐个进出、`屏蔽与过滤 → 关键词/标签/作者规则页`（带 arg 的那条）、以及回到设置主页后顶栏与滚动位置是否正常。

### 4.7.2 真机反馈：动画对了，背景色错了（已修）

用户装 03:00 版复验：**过渡动画没问题了，但背景颜色变得很奇怪** —— 截图表现为浅色卡片浮在一片死灰上，外观页「外观与主题」标题深字压深底几乎不可读。

根因是我建 `VeneraSubActivityBase` 时漏了一层：

- `MainActivity` 的内容包在 `VeneraAmbientBackground { … }` 里，它用 `Canvas` 铺**不透明**氛围光底色（`drawVeneraAmbient(primary, secondary, surface, isDark)`）。
- 我只写了 `VeneraTheme { SubScreen() }`，**没有这层** → 页面没画满的地方露出 Activity 主题 `@android:style/Theme.Material.NoActionBar` 的**平台深色窗口底**。
- 设置页原先在 MainActivity 的 NavHost 内就是铺在这层氛围光之上，所以这是纯漏项、不是设计选择。

修法：基类里补 `VeneraTheme { VeneraAmbientBackground { SubScreen() } }` —— 复用现成组件，不新造颜色、不加 token。QA 通过（新包 11:34）。

**下一个大概会先被看到的点（尚未改，等实测）**：这些 Activity 的主题窗口底仍是平台深色，所以每次进入设置/分区时，系统的 starting window 可能闪一下深色底。首页冷启动同样如此（一直没人提），但设置是每次进都触发。若真机看到闪黑，修法是给这两个 Activity 单独指定 `android:windowBackground` 为应用 surface 色。

---

### 4.8 第三批：首页右上角「漫画源设置」也走跨 Activity（用户追加）

补齐 §4.6.1 第 1 条里用户点名的那个入口。**零新 Activity、零新代码路径**：

- `Navigation.kt` 的 `onOpenSourceManage` 由 `navController.navigate(ComicSourceManageRoute)`
  改为 `context.openSettingsSubScreen(SettingsSubScreen.SOURCE_MANAGE)` —— 这个屏在
  `VeneraSettingsSubHost` 里早就存在（`ComicSourceScreen(onNavigateBack = ::back)`），
  `VeneraSubActivityBase` 自带系统跨 activity 转场与 blur-behind。
- 首页两个顶栏（MD3 `HomeScreen.kt:349` / MIUIX `:556`）共用同一个回调，一处改动两边同时生效。
- 顺带清掉三处死码：`composable<ComicSourceManageRoute>` 目的地、`@Serializable data object ComicSourceManageRoute`、
  以及上一轮 Activity 化之后遗留的 `SettingsRoute`（全仓库只剩一处注释提到它），外加 `ComicSourceScreen` 的未用 import。

**为什么搬得安全（已核）**：`ComicSourceScreen` 只取 `viewModel()`（`LocalViewModelStoreOwner` 由 `ComponentActivity` 提供）
和 `VeneraTokens` / `LocalAppearanceStyle` / `LocalVeneraDarkTheme`（三者都有 `staticCompositionLocalOf` 默认值，
且基类已包 `VeneraTheme`），不依赖任何 MainActivity 专有的 CompositionLocal。

**还剩一层没动**：源脚本编辑页 `SourceEditScreen` 是 `ComicSourceScreen` 内 `editTarget` 状态驱动的覆盖层
（`ComicSourceScreen.kt:1603-1620`），既不是 NavHost 目的地也不是 Activity，仍是无动画的直接切换。

---

### 4.9 第四批：首页顶栏 统计 / 本地漫画 / 收藏图 一并跨 Activity（用户追加）

与 §4.8 同一做法、同一宿主，零新代码路径 —— 三屏在 `VeneraSettingsSubHost` 里本来就有：

| 首页回调 | 原实现 | 现实现 |
|---|---|---|
| `onOpenStats` | `navigate(StatsRoute)` | `openSettingsSubScreen(STATS)` |
| `onOpenLocal` | `navigate(LocalComicRoute)` | `openSettingsSubScreen(LOCAL_COMICS)` |
| `onOpenImageFavorites` | `navigate(FavoriteImagesRoute)` | `openSettingsSubScreen(FAVORITE_IMAGES)` |

四个入口上方合并成一条注释说明为什么跨 Activity，避免同一句话重复四遍。

**顺带清掉**：`StatsRoute` / `FavoriteImagesRoute` 两个目的地与路由对象（改完即全仓库零引用），
外加上一轮 Activity 化后遗留、这轮才被发现的三个死路由对象 `ContentGuardRoute` / `SyncBackupRoute` / `LogViewerRoute`
（上一轮只删了 `composable<>` 块，没删 `@Serializable data object` 声明）。

**⚠ 这一批带来的真实代价，必须写明（不是假想）**：
从**首页**进本地漫画后点一本漫画，走的不再是同图内 `navigate(DetailRoute)`，而是
`SettingsEscape.OpenComic` → `escape()` → `startActivity(MainActivity)`。已核两处事实：
`AndroidManifest.xml` 的 `MainActivity` **没有声明 launchMode**（默认 standard），
且 `consumeSettingsEscape` 里 `OpenComic` 是 `shell.selectedComic = comic; navigate(DetailRoute(...))`。
所以代价是：① 外壳重建、首页数据重拉；② 任务栈里出现**两个 MainActivity 实例**（新的压在 `SettingsSubActivity` 之上，
旧的那个还在更底下）；③ 从详情返回落在新实例上。统计页的「题材下钻」、本地漫画的「打开阅读器」同理。
这是 §4.6/4.7 为设置子树已经拍板接受过的同一代价，只是这一次落在**首页主流程**上，感受会更明显。

**因此留下的一对可选项**（未动，等拍板）：
`DownloadRoute` 与 `LocalComicRoute` 在 MainActivity 的图里已经成了**孤岛** —— 全仓库无深链（`AndroidManifest.xml`
只有 LAUNCHER 一个 intent-filter，代码里零 `navDeepLink`），两者只剩互相跳转、再没有入口。
没有顺手删的原因：孤岛里那条 `shell.selectedComic` → `navigate(DetailRoute)` 恰好是**不重建外壳**的那条路径，
详情页转场（§4.10）的方案还没定，可能用得上。

---

## 5. Build QA 结果

| 项 | 结果 |
|---|---|
| `:app:compileDebugKotlin` | ✅ |
| `:app:testDebugUnitTest` | ✅ |
| `:app:assembleDebug` | ✅ |
| 全量重跑（`--rerun-tasks`） | ✅ BUILD SUCCESSFUL in 1m 20s，**44/44 tasks executed**（排除 UP-TO-DATE 蒙混） |
| 合并后 manifest | ✅ 含 `com.venera.compose.SettingsActivity` 与 `com.venera.compose.SettingsSubActivity` |
| 第二批（二级页跨 Activity） | ✅ 三项 QA 通过，44 tasks：13 executed / 31 up-to-date |
| 第三批（首页源管理入口，§4.8） | ✅ `assembleDebug` 43s + `:app:testDebugUnitTest` 13s 全过；新包 12:05:41。设备此刻未连接，真机项待验 |
| 第四批（统计/本地漫画/收藏图，§4.9） | ✅ `assembleDebug` + `:app:testDebugUnitTest` 15s 全过；新包 12:16:12。同样待真机 |

`navController.graph = graph` 在 2.10.1 里是 NavHost 组合体内的直接语句，而 `consumeSettingsEscape` 跑在 `LaunchedEffect` 里（组合结束后才执行），所以**不存在「图还没装就 navigate」的崩溃**。已核源码确认。

**以上只证明代码正确性，不证明观感与行为正确性。**

---

## 6. 真机 QA 清单（设备恢复连接后）

### 6.1 2026-09-22 02:20–02:38 实测结果（logcat 全程采集，只读不代操作）

**已证实：**

| 结论 | 证据 |
|---|---|
| 齿轮↔设置是**系统级**预测返回，不是自绘 | `TransitionRequestInfo { type = PREDICTIVE_BACK }` → `request handled by BackAnimationController$BackTransitionHandler`；两窗口带 `FLAG_BACK_GESTURE_ANIMATED`，设置窗口 `m=CHANGE`、首页 `m=TO_FRONT` |
| **二级页也拿到了同一套** | 2 次 `SettingsSubActivity` START，两次都各自出现 `t=PREDICTIVE_BACK` + `FLAG_BACK_GESTURE_ANIMATED`（涉及二级页的转场 2 条）；`Displayed +51ms / +56ms` |
| blur-behind **确实落在设置窗口上** | 停在 SettingsActivity 时读 `dumpsys window windows`：`Window #13 ...com.venera.compose.SettingsActivity` 的 `mAttrs` 带 `blurBehindRadius=47`（= 18dp × 密度 2.625），MainActivity 窗口**没有**该字段；系统侧 `mBlurEnabled=true` |
| 系统会施加这套动画的前提成立 | `ro.build.version.sdk=37`（Android 17）、`navigation_mode=2`（左边缘手势）、三项 animation scale 均 1.0 |
| ColorOS 不劫持我们的返回 | `OplusPredictiveBackController: should not HookOnBackInvokedCallbackEnabled for application com.venera.compose.SettingsActivity hasOnBackInvokedCallBackEnabled` + `initPredictiveBackConfig ... mShouldInterceptKeyEvent false` |
| 装的就是本轮新代码 | 设备 `base.apk` 与本地 02:15 构建 **md5 逐字节一致**（`29e2f045…`），dex 内含 `com/venera/compose/SettingsSubActivity` |
| 无崩溃 | 全程无 `AndroidRuntime` / `ActivityNotFound` / `未知的设置二级页`；0 条 `Skipped frames` |

**仍未覆盖（不是失败，是没跑到）：**

- 8 个二级页只进了 2 个（02:30:46、02:30:48）；此后整段日志 `SettingsSubActivity` START 增量为 **0**。
- **3 个越界出口一个都没走**：本地漫画→阅读器、本地漫画→详情、统计→题材下钻。这是本批改动里唯一带「新建 MainActivity 实例 + 静态交接槽」的路径，返回落点 / 重复推页 / 外壳重建白屏三个风险全部未验。
- 下载 ↔ 本地漫画 互跳未走。
- **二级页窗口的 `blurBehindRadius` 未单独读过**（只证明了设置主页窗口有；两者共用 `VeneraSubActivityBase`，但没实测过二级页那一层）。
- 右边缘分支在这台机上物理不可达（`navigation_mode=2` 仅左边缘），§4.2-1 那条风险无法在本机验证。

**两条 ROM 侧噪声，与我们无关但记下来免得下次误判：**

- `OplusPredictiveBackController` 抛 `NoSuchMethodException`（E 级，ROM 内部反射失败）。
- `OplusScrollToTopManager: unregisterSystemUIBroadcastReceiver failed ... Receiver not registered`（D 级，来自 pid 2729 系统进程，只是日志里带我们的窗口名，不是我们的异常）。

**关于 GPU 报表**：02:22:54→02:23:54 连续 4 个 10s 窗口 `GpuWorkeBPF` 把 98.6–99% 记在 `com.venera.compose` 名下（且当时人在 MainActivity）。各家对该数语义不同（占比 or 占用），**不据此下结论**；若有发热/掉帧体感，这是第一条要查的线索。

### 6.2 原清单剩余项

设备：一加 PJZ110（8bfdaeb5）。**只读取 logcat 与 dumpsys，不代你操作界面。**

其余回归：
5. 设置内部 9 页逐个进出，确认内部转场仍是跟手横滑。
6. 首页顶栏四个入口（统计/本地漫画/收藏图/源管理）已全部改跨 Activity（§4.8、§4.9），**这一项变成要验的点**：
   逐个点开应各起一个 `SettingsSubActivity`，返回都是与设置各分区同构的系统预测式横滑 + 返回模糊；
   并**重点验 §4.9 那笔代价**：首页 → 本地漫画 → 点一本漫画，看是否出现外壳重建、首页数据重拉，
   以及 `dumpsys activity activities` 里任务栈是否真有两个 `com.venera.compose.MainActivity` 实例。
7. 防窥开关：在设置里打开后，检查 SettingsActivity 的截图与最近任务缩略图是否被遮。
8. Dialog/Sheet 回归（navigationevent 新链路）：搜索「添加条件」、搜索选项、收藏长按、阅读器设置面板 —— `SearchScreen.kt:1435,1578` 记过的 miuix 独立窗口崩溃。
9. LIGHT / DARK 各一遍。

---

## 7. 本轮未做、需要另行安排

- 46 笔未 push 提交：GitHub 直连失败，需要你的代理端口或网络方案。（注：`api.github.com` / `raw.githubusercontent.com` 实测 HTTP 200 可达，只有 push 不通。）
- `FREEZE-STATEMENT.md` 第 98–101 行 4 条错账的订正（按决策 4 推迟到真机之后）。
- `NavigationEvent` 仍是传递依赖（miuix 以 api 暴露，navigation 2.10.1 只在 runtime 声明 `navigationevent-compose:1.1.2`）。要显式写进 `libs.versions.toml` 需另行批准。
- A 的彻底形态（Detail/Reader/TagSearch 也 Activity 化，消除 §4.2 那 3 个越界出口的外壳重建）是独立专题。
- 工作区未跟踪的 `debug.log`（09-19 crashpad 日志）与 `.dsh-vision-router/`：与本轮无关，仅报告，未动。
