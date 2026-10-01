---
name: reference-bilipai-as-ui-donor
description: 外观/主题/设置类功能的现成参考实现 = jay3-yy/BiliPai（同为 Miuix + MaterialKolor 栈）；色板表与取值口径可逐字抄，但要分清它哪条路是壁纸取色
metadata:
  type: reference
---

用户拿来做 UI 参照的 app 之一是 **`jay3-yy/BiliPai`**（GitHub，Kotlin/Compose 哔哩哔哩第三方客户端，4.6k star）。
它和本仓是**同一套组件栈**（`top.yukonga.miuix.kmp` + `com.materialkolor`），所以它的做法比一般参考项目更可抄。

读法（本机没有 `gh`，也没有 clone）：

- 文件树：`https://api.github.com/repos/jay3-yy/BiliPai/git/trees/HEAD?recursive=1`
- 单文件：`https://raw.githubusercontent.com/jay3-yy/BiliPai/HEAD/<path>`，用 node 的 `fetch` 取（WebFetch 会把代码摘要掉、拒绝原样输出）

已核实可复用的口径：

- **预设种子色板 25 条**在 `design-system/src/main/java/com/android/purebilibili/core/theme/Color.kt`
  （`ThemeColors` + `ThemeColorNames` 两条按下标对齐的表）。存的是**种子色本身**，不是网格渐变的渲染值 ——
  樱花粉 `0xFFFA7298` 与它设置页上显示的 `#FA7298` 逐字相同，这就是校准点。
- 种子色→色板用 `dynamicColorScheme(seedColor, isDark, isAmoled, style, specVersion)`（它锁 4.1.1）。
- 色板网格：5 列、`CircleShape`、色块 36.dp。
- 取色来源枚举 `Md3ColorSource { FOLLOW_WALLPAPER("跟随系统壁纸"), CUSTOM("自定义颜色") }`。

**别抄错的一条**：它的 `WallpaperPaletteStore.kt` 是给首页卡片玻璃着色用的（Coil 解码 + `androidx.palette`
切 5 条水平带），**主题**的壁纸动态色走的是 AndroidX `dynamicLight/DarkColorScheme(context)`，
外面包 `key(systemWallpaperRefreshToken)`。那个 token 才是壁纸取色的真难点
（`addOnColorsChangedListener` 可能早于 Monet RRO 生效，所以还要 `ACTION_WALLPAPER_CHANGED` + `ON_RESUME` 复读 + 二次补刷）。

**可借鉴清单（2026-09-22 按文件名扫过 4032 个路径，未逐个读实现，用前要先核）**：
动效预算体系 `core/ui/adaptive/MotionTier(Policy)` + `core/ui/motion/ReduceMotion` + `blur/BlurBudgetPolicy` + 各页 `*MotionBudgetPolicy`；
`baselineprofile/` 下的帧时 benchmark（`BiliPaiBottomPagerFrameTimingBenchmark` 等）；
预测返回与共享元素成套 —— `navigation3/predictiveback/`（`AospNavTransition` / `Classic` / `Scale` / `NoPredictiveBack` 四种可切换样式 + `NavTransitionEasing`）与 `core/ui/transition/PinSourcePageDuringSharedTransition`、`VideoCardNativeSnapshot`、`VideoCardReturnTimeline`；
首页双列捏合改密度 `HomeFeedPinchZoomPolicy`；液态分段控件与形变指示器 `AppSegmentedControl`（miuix / material3 两套 renderer）、`LiquidIndicator`、`MatchedLiquidIndicatorGeometry`；
玻璃可读性兜底 `LiquidGlassReadabilityMode` / `LiquidGlassAdaptiveReadability`；骨架屏规格 `HomeFeedSkeletonVisualSpec` / `SkeletonBreathing`。
**不能抄的**：播放器 / 弹幕 / 直播 / 一键三连（域不同），以及壁纸池与启动屏（本仓已明确主动不做）。

**它的 Miuix 桥与本仓取舍不同**：BiliPai 用 `MiuixThemeBridgePolicyTest` 钉住**只有 `primary` 来自 Material，中性色与全部 `disabled*` 保持 Miuix 原值**；本仓 `feature/ThemeColorBridge.kt` 把 `secondary` / `tertiaryContainer` / `disabled*` 也一起覆盖了。两种都能自圆其说，是取舍不是 bug，但值得在设备上对比一眼。

**Why:** 2026-09-22 做「调色板控制取色」时，我先假设 MaterialKolor 自带官方预设色板并把这条当成选项抛给用户，
用户选了之后才查清真伪（该库根本没有命名预设表）。真凶是参考 app 的表，报出 app 名一句就定位到了。

**How to apply:** 用户给设置页/主题类截图说"照这个做"时，先问是哪个 app、直接读它的源码拿常量，
而不是从截图采样反推数值。**另外：用户问"有什么可以借鉴"时，问的是功能与动效，不是工程实践**
（2026-09-22 我先答了"把约定写成测试"，被他「不是不是，我是说…」纠正了一次）。
相关：[[feedback-verify-capability-claims]]、[[feedback-mirror-official-values]]。
