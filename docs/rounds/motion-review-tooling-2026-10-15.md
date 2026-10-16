# 动效审查工具选型评估（2026-10-15）

## 一、缘起

上一轮评估了 `emilkowalski/skills`，结论是规则目录为 Web 专属，与本仓 Compose 栈错配，  
不可整包直接用（详见当日工作日志）。本轮回答「有没有别的项目能评估我们的动画」。

## 二、结论

推荐四层组合，按「先规则、再自动化、后运行时」推进：

| 层     | 选型                                                                        | 解决什么                    | 成本 |
| ----- | ------------------------------------------------------------------------- | ----------------------- | -- |
| 性能规则源 | `android/skills` 的 `jetpack-compose-performance`（Google 官方，第 4 章专讲动画）     | 相位延迟、共享元素、Animatable 驻留 | 极低 |
| 手感判据  | 沿用 emil 那套里 8 条平台无关标准，手工使用                                                | 缓动方向、时长、可打断、进出场不对称      | 极低 |
| 自动化回归 | Roborazzi + `ComposeTestRule.mainClock` 逐帧截图                              | 把「动画跑起来」变成可回归的基准图       | 中  |
| 运行时归因 | Macrobenchmark `FrameTimingMetric` / JankStats / `android-profiler` skill | 真机掉帧定量归因                | 中高 |

其中**第 1 项是对口度最高的发现**：Google 官方已经有专门覆盖 Compose 动画性能的 skill，  
正好补上 emil 那套完全缺失的性能维度。

## 三、逐项说明

### 1. android/skills（Google 官方 agent skills）

- 仓库 `github.com/android/skills`，遵循 agentskills.io 开放标准，Apache-2.0，  
  由 Google 维护（`last-updated` 已到 2026-10）。
- 根目录分类：build-system / camera / cars / device-ai / devtools / identity /  
  **jetpack-compose** / media / navigation / **performance** / play / **profilers** /  
  security / system / **testing** / tv / wear / xr。
- 与本议题最相关的三枚：

**`jetpack-compose/jetpack-compose-performance`**

描述原文：Diagnose and optimize Android Jetpack Compose UI code. Use this skill when  
analyzing performance bottlenecks of existing code to minimize recomposition overhead and  
prevent dropped frames and UI lag.

其**第 4 章标题即「Animation and shared transitions」**，指向 `references/animation.md`（289 行），内容分三块：

1. 动画的相位延~~迟（跳过 Composition，直达 Draw / Layout）~~
   - ~~A. alpha / rotation / scale → 用 `Modifier.graphicsLayer { }` 延迟到 Draw~~
   - ~~B. 动画位移 → 用 `Modifier.offset { }` 延迟到 Layout~~
   - ~~C. 动画尺寸 → 用 `Modifier.layout` 或 `Modifier.animateBounds`~~
   - ~~D. 自定义 composable 参数 → lambda providers（传 `() -> Float` 而非裸值）~~
2. ~~共享元素转场：快速滚动列表里的 `sharedBounds` / `sharedElement` 必须~~挂可见性门控，  
   否则被丢弃的预取项也会实例化转场作用域
3. 自定义 modifier 里驻留 `Animatable`（`Modifier.Node` + `onAttach` 里 launch）

**`profilers/android-profiler`**

录制与分析 Perfetto trace，专门做 UI jank / 掉帧 / ANR / 启动延迟归因，  
支持 CPU 调用栈、Binder 风暴、线程调度停顿，以及 A/B baseline trace 对比找回归。

**`testing/testing-setup`**

分析并搭建测试基建，明确覆盖单测 / UI test / **screenshot test（描述里点名 Roborazzi）**。

安装方式（三选一）：

```
android skills add jetpack-compose-performance --project=.
/plugin install android-skills --marketplace android/skills
git clone https://github.com/android/skills.git
```

### 2. Roborazzi + ComposeTestRule.mainClock —— 唯一成熟的「动画逐帧回归」方案

Google 官方示例仓库 `android/nowinandroid` 已有现成实现，可直接照抄：

`core/designsystem/src/test/kotlin/com/google/samples/apps/nowinandroid/core/designsystem/LoadingWheelScreenshotTests.kt`

```kotlin
@Test
fun loadingWheelAnimation() {
    composeTestRule.mainClock.autoAdvance = false
    composeTestRule.setContent {
        NiaTheme { NiaLoadingWheel(contentDesc = "") }
    }
    listOf(20L, 115L, 724L, 1000L).forEach { deltaTime ->
        composeTestRule.mainClock.advanceTimeBy(deltaTime)
        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/LoadingWheel/LoadingWheel_animation_$deltaTime.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }
}
```

关键机制：`mainClock.autoAdvance = false` 关掉自动推进，`advanceTimeBy` 手动跳到动画的任意时刻。  
这就是「逐帧看动画」的官方原语，**不需要真机、不需要模拟器**，跑在 Robolectric 上。

基准图提交进仓：`core/designsystem/src/test/screenshots/LoadingWheel/LoadingWheel_animation_{20,115,724,1000}.png`。  
对应命令是 `recordRoborazziDemoDebug`（录制）与 `verifyRoborazziDemoDebug`（校验）。

对本仓的意义：本仓已有 159 个 JVM 单测文件、1112 颗测试，**加 Roborazzi 不动现有基建**，  
只是多一条 test 依赖 + 一条 record/verify 命令。而且它天然能钉住仓内那条  
「升级后与改动前逐像素一致」的纪律。

### 3. Compose Preview Screenshot Testing（Google 官方 AGP 插件）

`com.android.compose.screenshot` 插件（写作时版本 `0.0.1-alpha15`），  
把 `@Preview` 变成截图测试输入，新增 `screenshotTest` 源集，  
配 `updateDebugScreenshotTest` / `validateDebugScreenshotTest` 两个任务。

**局限：只测静态帧，不能推进动画时间轴。** 适合钉住「各状态下的静态外观」，  
不适合审动画过程。与 Roborazzi 二选一即可；若目标就是审动画，Roborazzi 更对口。

### 4. 运行时性能度量

- **Macrobenchmark 的 `FrameTimingMetric`**：真机上量 `frameDurationCpuMs` /  
  `frameOverrunMs` 的 P50 / P90 / P99，能定量说清「这个动画掉了多少帧」。  
  需要新建 benchmark 模块 + 真机跑。
- **JankStats**：线上卡顿归因，把 jank 帧和当时的 UI 状态关联起来。
- **`android-profiler` skill**：把 trace 录制 + PerfettoSQL 查询 + A/B 对比交给 agent 做。
- **零依赖替代**：
  - `adb shell dumpsys gfxinfo <pkg> framestats` 直接拿帧耗时直方图
  - `adb shell settings put global animator_duration_scale 10` 把系统动画放慢 10 倍肉眼观察
- 注意：`android { testOptions { animationsDisabled = true } }` 是**测试时关掉动画**，  
  会掩盖问题，专门审动画时不要开。

### 5. 人眼逐帧（成本最低，往往最有效）

- `scrcpy --record=out.mp4` 或 `adb shell screenrecord` 录屏 →  
  `ffmpeg -i out.mp4 -vf "setpts=4*PTS" slow.mp4` 慢放 → 再抽帧成图。
- 本仓已有 `_qa/` 的截图流水线（124 张 PNG + uiautomator XML + ffmpeg 缩图），  
  **同一条管线加一步录屏就能扩成动效走查**，复用度很高。
- 慢放 / 逐帧是 emil 那套里唯一一条「当你无法从代码判断手感时该做什么」的建议，值得保留。

### 6. 自建规则（配合仓内纪律，杠杆最高）

本仓已有 `TokenBudgetFrozenTest` 冻结裸 dp / sp / Color。同理可加一条**动效冻结测试**：  
扫描 `app/src/main/java`，禁止出现 `tween(<字面量>)`、`spring(<位置参数>)`、  
私有 `*_DURATION_MS`，强制走 `VeneraTokens.motion.*`。

现状已核，这些裸写法仍在：

- `tween(120)`、`tween(200)`
- 多份各文件私有 `TAB_SLIDE_DURATION_MS` / `BOTTOM_BAR_HIDE_DURATION_MS` / `HANDOFF_MS`
- `spring(1f, 1000f, visibilityThreshold)` 这类位置参数写法

也可以写成自定义 Android Lint 规则（`lint.jar`），挂进 `./gradlew lint`。

## 四、本仓现状核对（可立即落地的线索）

| 检查项                                                | 现状                                                   | 官方 skill 的建议                                       |
| -------------------------------------------------- | ---------------------------------------------------- | -------------------------------------------------- |
| `graphicsLayer { }` 相位延迟                           | 18 个文件、约 28 处，已有意识在用                                 | 应覆盖所有 alpha / scale / rotation 动画                  |
| `Modifier.offset { }` lambda 版                     | 仅 2 处                                                | 动画位移一律走 lambda 版                                   |
| `animateDpAsState` / `animateOffsetAsState`        | 10 处                                                 | 会触发重组，是 `offset {}` / `animateBounds` 的替换对象        |
| `animateFloatAsState`                              | 16 处                                                 | 若用于 alpha / scale / rotation，应换 `graphicsLayer {}` |
| `animateBounds` / `sharedElement` / `sharedBounds` | 20 处，已用共享元素转场                                        | 快速滚动列表里要挂可见性门控                                     |
| 动效 token                                           | `VeneraMotionTokens` 已有（120 / 220 / 320 / 200 / 900） | 唯一残留问题是裸数字未收口                                      |

另：`animateDpAsState` 10 处 + `animateFloatAsState` 16 处合计 26 个调用点，  
是上面「相位延迟」这条规则的主要待查面，建议作为第一轮的扫描目标。

## 五、推荐落地顺序

1. 装 `android/skills` 的 `jetpack-compose-performance`（只读规则，零风险），  
   用它过一遍上面那 26 个 `animate*AsState` 调用点。
2. 把 emil 那 8 条平台无关标准手工套一遍，或按上一轮讨论的方案 B 落成项目级 skill。
3. 若要长期防回归，再上 Roborazzi 逐帧截图（照抄 NIA 的 `loadingWheelAnimation` 写法）。
4. 只有在真机确实看到掉帧时才上 Macrobenchmark / Perfetto，不要提前建这套重基建。

## 六、边界与风险

- `android/skills` 是官方仓库但**不接受公开贡献**，更新节奏由 Google 决定。  
  它自述「不覆盖 LLM 已经擅长的领域（如 Compose 基础最佳实践）」，  
  因此**它不提供手感判据，只提供性能与工作流判据** —— 手感那部分仍要靠 emil 那套或人工。
- Roborazzi 的基准图会随 Compose / 字体渲染版本漂移，需定期 `record` 重录，否则 CI 会误报。
- 装任何第三方 skill 前需走安全审计（本仓尚未安装任何东西）。
