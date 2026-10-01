---
name: project-navigation-predictive-back-facts
description: 预测式返回真相：AOSP 跨 activity 动画只在真换 Activity 时系统施加；返回模糊走 blur-behind；swipeEdge 是 2.10 入参；包体 +40MB 是脏增量填充；真机怎么反证生效；二级页无模糊是未决 bug
metadata:
  type: project
---

## A. 要 AOSP 那套就必须真的换 Activity（2026-09-22 定案）

- **Android 16（API 36）起，targetSdk ≥ 36 的应用由系统默认施加 back-to-home / cross-task / cross-activity 三套预测动画**，零代码，且系统不再调用 `onBackPressed`、不再派发 `KEYCODE_BACK`；`android:enableOnBackInvokedCallback="false"` 可临时关掉。**Venera targetSdk = 37**，落在里面。
- **单 Activity 内的 NavHost 目的地，系统不认为是换页** → 拿不到那套动画，只能自绘复刻。
- **返回模糊有系统通道，不必自己画**：`WindowManager.LayoutParams.setBlurBehindRadius` + `FLAG_BLUR_BEHIND`（Android 12+）。已在本地 `android-37.0/android.jar` 逐 class 核实 `WindowManager` 侧有 `isCrossWindowBlurEnabled` / `addCrossWindowBlurEnabledListener` / `removeCrossWindowBlurEnabledListener`。AOSP《Window blurs》：**普通应用可用、无权限要求、建议半径 ≤150px**。形态是「上层窗口在则背后糊、窗口缩离即清晰」，**不是**一条随手势进度收束的动画。
- 坑：`addFlags`/`clearFlags` 在 **`Window`** 上，`LayoutParams` 只有 `setBlurBehindRadius`，写反编译不过。
- **要让 N 个子页都有这套动画，不必写 N 个类**：一个 `SettingsSubActivity` + `EXTRA_SCREEN` 屏名枚举分发，配一个抽象基类 `VeneraSubActivityBase` 收走边到边契约 / 防窥订阅 / blur-behind 生命周期 / 主题包裹，子类只给一个 `@Composable abstract fun SubScreen()`。缺 extra 用 `error()` 炸，不给兜底默认值（否则会静默停在错误的页上）。**同页多入口会因此出现两套动画**（从设置进=跨 Activity，从首页进=NavHost 自绘），这是该形态的固有代价，要显式说明不要藏。

## B. navigation-compose 版本能力边界

- **2.9.8 的 `navigation-compose` 已经是真·进度驱动预测式返回**：`NavHost` 用 `androidx.activity.compose.PredictiveBackHandler` 收 `Flow<BackEventCompat>` → `SeekableTransitionState.seekTo(progress, previousEntry)`，拖动期间用 **`popEnter/popExitTransition`**。
- **`predictivePop{Enter,Exit}Transition` 是 2.10.0 才加的**，签名 `AnimatedContentTransitionScope<NavBackStackEntry>.(swipeEdge: Int) -> Enter/ExitTransition` —— **方向是 lambda 入参**（`androidx.navigationevent.NavigationEvent.EDGE_LEFT=0 / EDGE_RIGHT=1 / EDGE_NONE=-1`）。「手势进度是标量、拿不到左右边缘」是错的。
- **2.10.1 `NavHost.kt` 转场选择是三分支**：`inPredictiveBack → predictive*`，`else isPop → pop*`，`else → enter/exit`。`onBackCompleted()` 同时翻转这两个标志，**提交帧会换分支** → predictive 与 pop 必须逐参数同构，否则松手瞬间跳形。
- **目的地级 predictive 拿不到**：`ComposeNavigator.Destination.predictivePop*Transition` 是 `internal` 且全源码无赋值处，公开 DSL 只有 enter/exit/popEnter/popExit。按页区分只能借 NavHost 级 lambda 的接收者判 `initialState.destination.hasRoute(XRoute::class)`。
- **升级代价分级**：2.9.8 只要 activity-compose 1.8.0 / lifecycle 2.9.0，不碰 navigationevent；2.10.1 要求 **activity-compose 1.13.0** 并新增 **navigationevent-compose 1.1.2**（2.9.8 走 `androidx.activity.compose.PredictiveBackHandler` 老链路）。2.10.1 的 NavHost 起手 `checkNotNull(LocalNavigationEventDispatcherOwner.current)` —— 与本项目 `SearchScreen.kt:1435,1578` 记过的 miuix 独立窗口崩溃同族。缓解：该 local 是 `compositionLocalWithHostDefaultOf`，回落 view tree，由 **activity 1.13.0 的 `ComponentActivity`/`ComponentDialog`** 写入，根 Composition 不崩，跨窗口风险仍在。
- `navController.graph = graph` 在 2.10.1 是 NavHost **组合体内的直接语句**，所以在 `LaunchedEffect` 里 `navigate()` 不会撞上「图还没装」。

## C. 包体测量

「APK 85MB → 125MB」**不是依赖升级造成的**。逐 zip 条目实测两包压缩内容 78.4 vs 78.9 MiB（几乎没变），差的 39.59 MiB 全在条目间填充（最大一处 `classes5.dex` 前 29.3 MiB，抽样 1MiB 内仅 160 个非零字节 = 纯 0）。
**收尾实证**：`--rerun-tasks` 全量重跑后 arm64 debug 包 = **79.0 MiB**，与升级前基线 79.5 MiB 相等 → 那 40 MiB 是**脏增量构建**的对齐填充。判包体一律 `--rerun-tasks`，且按「压缩内容合计」比，不看文件字节数。

## D. 真机判定这套东西是否真的在跑（2026-09-22 一加 PJZ110 / Android 17 实测）

只看代码和编译无法证明系统动画生效，用这三处读数判：

- **logcat**：`TransitionRequestInfo { type = PREDICTIVE_BACK` + `request handled by BackAnimationController$BackTransitionHandler` = 系统自己接管，不是我们画的。转场 change 里两扇窗口带 `FLAG_BACK_GESTURE_ANIMATED`，上层是 `m=CHANGE`（被缩小/圆角那层）、下层 `m=TO_FRONT`；提交后再来一条合并的 `t=CLOSE`。全日志 `t=PREDICTIVE_BACK` 的条数 = 手势次数。
- **`dumpsys window windows`**：请求成功会在那扇窗口的 `mAttrs` 里留 `blurBehindRadius=<px>`（18dp × 密度 2.625 = 47）。**只有设置那几扇窗有、MainActivity 窗口没有**，才说明请求精确落在目标窗口、没污染主界面。系统总开关读 `dumpsys window` 的 `mBlurEnabled=true`。
- **`OplusPredictiveBackController`**：`should not HookOnBackInvokedCallbackEnabled for application ... hasOnBackInvokedCallBackEnabled` + `initPredictiveBackConfig ... mShouldInterceptKeyEvent false` = **ColorOS 不劫持、交回应用侧**，所以系统跨 activity 动画才有机会生效。

**边界条件**：这台机 `settings get secure navigation_mode` = **2（仅左边缘）**，所以「右边缘起手镜像」那条分支在这台机上**物理不可达**，§B 记的同构风险无法在本机验证 —— 别把"没复现"当成"没问题"。

## E. 未决 bug（2026-09-22 报，截至本记录仍未修）

用户实测：**设置主页那一跳有模糊（与之前效果一致），但二级页 `SettingsSubActivity` 没有模糊。** 两者共用 `VeneraSubActivityBase`，所以不是漏挂代码那么简单。判法已经摆好、还差一个读数：**停在二级页时看它窗口的 `blurBehindRadius` 是不是 47**（量过一次但用户已退回主页，设置窗口已销毁，量不到）。

- 没设上 → 基类里 `onStart` 注册 `addCrossWindowBlurEnabledListener` 这条路径对二级页没生效，是我方代码问题。
- 设上了却看不到 → 属性到位但系统不渲染这个窗口对：二级页背后是 SettingsActivity，**它自己也是 blur-behind 的请求者**，且已被 stopped、可能只以冻结快照参与合成。这种就不是应用侧能解的。

**Why:** 上一会话把 A/B/C 里多条记成了相反的样子并写进 `FREEZE-STATEMENT.md`，据此得出的「要不要升 2.10.1」「方向能不能跟随」「模糊能否与跨 Activity 共存」「包体涨了多少」四个结论全被带偏；我自己也曾断言「跨 Activity 拿不到模糊」并据此误导了一次路线选择。D 节的存在就是因为：这套东西"生效了没有"只能从系统日志和窗口属性反证，代码看起来对不算数。

**How to apply:** 碰导航/转场/系统动效前先套这份事实，别重新 grep 猜。判库 API 存在性一律同时 grep `*-compose` 与 `*-runtime` 两个产物（只查 runtime 会漏）；判某方法在不在 SDK 里，用 `unzip` 取出 class 再 `grep -a`（本机无 python、别用 javap 猜签名）；查版本要求用 `node` 解析 `.module` 的 `version.requires`。观感参数优先用系统通道或应用内现成口径，见 [[feedback-mirror-official-values]]；批量逐层决策出现跨层互斥时要回抛不要自行调和，见 [[feedback-design-review-then-code]]。
