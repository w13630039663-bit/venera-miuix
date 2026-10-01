---
name: project-compose-jank-audit
description: 掉帧审计的既有结论：三个已排除的怀疑（含「没开 Strong Skipping」这条错误推论）、Tier 1 已改而 Tier 2/3 未做、2026-09-22 冷启动/首帧基线与免费测帧口径、真机"到底以多少 Hz 出帧"的正确读法（dumpsys display 会骗人）
metadata:
  type: project
---

2026-09-22 用户报「页面切换和动画还是会有频繁掉帧」，做了全项目审计。**Tier 1 三处已改，但一次帧时间都没测过** —— 全是「每帧/每重组必然发生的额外工作被去掉」这类结构性修复，不等于已验收。

**Why:** 掉帧这类问题最容易凭直觉加码（自造性能数字、优化根本不存在的开销），先把排除掉的怀疑记下来，别让下一个人重走。

**How to apply:** 再做性能/掉帧排查时，先复用下面三条已排除项与 Tier 2/3 清单；任何"快了多少 ms"的说法没有 `framestats` 实测就不要写进代码注释或文档（本轮我自己删掉了 150-250ms 那类估算值，只保留机制和可算的字节算术）。

- **已排除，别再怀疑**：
  - 返回过渡的模糊是**系统跨窗口** `blurBehindRadius`，本进程里没有可优化的 `RenderEffect` / 绘制层（与 [[project-navigation-predictive-back-facts]] 一致）。
  - Coil 会把布局约束**正向传给解码器**，详情页预览小图本来就是降过采样的，不存在「缩略图解成 7MiB 全屏图」。
  - ❌「没有 `composeCompiler {}` 块 → 没开 Strong Skipping → 卡片全量重组」这条推论**作废**：Kotlin 已是 2.4.10（`gradle/libs.versions.toml`），强跳过自 2.0.20 起默认开。真正的雷形状是**每次重组新分配实例**（引用相等失效）—— 未 `remember` 的 `ImageRequest.Builder(...).build()`、内联 `.reversed()` / `.filter{}.distinct()`。Coil 认"模型换了"就取消在飞请求重发，翻页时整条取图链被反复重启。
- **Tier 1（2026-09-22 已改，真机未验证，设备整天未连接）**：阅读器 `ImageRequest` 进 `remember`；JM 去混淆路径去掉「JPEG 重编码 → Coil 再解码」往返（改 `ImageFetchResult` 短路）；`ContentGuardManager` 的用户正则按 pattern 记忆，不再每次调用 `new Regex`。
- **Tier 2（未做）**：详情页展开后的预览大网格非虚拟化（`Column { chunked(3).forEach }`）、UIState 整对象收集（含 `SearchViewModel`）、`RateLimitingInterceptor` 锁内 `Thread.sleep`、每帧构造 `tokens.color`。
- **Tier 3（未做）**：动画期间暂停两处全屏 `rememberLayerBackdrop { drawContent() }` 采样、滚动时降级封面 `Modifier.blur`。
- **测帧口径（2026-09-22 更新：不需要用户配合，旧结论作废）**：
  - `adb -s emulator-5554 shell am start -W -n <pkg>/com.venera.compose.MainActivity` 取 `TotalTime`；
  - `adb logcat -d | grep -E "Davey|Skipped"` 取单帧耗时与跳帧数（`AnimationStart→DrawStart` 的差 ≈ Compose 组合+测量+布局）。
  两条都是纯读取，`force-stop` 后连跑三次就有基线。**别再写「测帧必须用户配合」**。
  `dumpsys gfxinfo framestats` 仍可用，但要用户滚动，优先级低于上面两条。
- **2026-09-22 实测基线（Pixel Tablet 模拟器，packageId com.github.w13630039663bit.venera.miuix）**：
  冷启动 `TotalTime` = 7598 / 4220 / 3519 ms；首帧 `Davey duration=2533ms / 2679ms`，其中约 1.4s 在组合+布局；
  单进程最多 `Skipped 250 帧`。SettingsActivity +438ms、SettingsSubActivity +342ms（说明代价集中在 MainActivity 首帧，不是通用启动）。
  两个具体嫌疑：① `VeneraApp.onCreate` 在主线程同步 `ContentGuardManager.getInstance`，其 `init` 里
  `loadRules()` 会 `readableDatabase` 同步开库跑建表/迁移、`loadSourcePresets()` 同步解析 assets JSON；
  ② 首帧一次性组合整张首页（统计卡 + 推荐网格 + 历史行 + 漫画源列表 + 液态玻璃底栏）。

- **真机"到底以多少 Hz 出帧"的正确读法（2026-09-29 PJZ110 实测，`dumpsys display` 会骗人）**：
  - `dumpsys display` 里的 `modeId 4 / renderFrameRate 120.00001 / presDeadline 11333333` 只是 **DisplayManager 侧的"渲染档位"名字**，不代表面板此刻的出帧率。同一时刻 `dumpsys SurfaceFlinger` 的 `activeMode={id=5, vsyncRate=60.00 Hz}` 与 `mWorkDuration=16.67` 才说明**空闲时实际在 60Hz 出帧**。
  - 判定"有没有被系统降档"要看三条：SF 的 `activeMode` vsyncRate、`mWorkDuration`（=每帧死线，11.33/16.67ms 分别对应 120/60）、以及 `OPlusRefreshRateSelector` 下的 `AppRequestRefreshRates`。**这台机器实测应用自己的投票已经就是 120Hz**（`AppRequestRefreshRates: [120.00 Hz - modeId 4]`、`PrimaryRefreshRates` 同值、`OPlus ADFR Mode:1`、`MinFps:1 IdleFps:-1`、`FakeFrame:1`）。
  - **2026-09-29 已证实升档会发生，"强制 120Hz 开关"确定不做**：应用静默/焦点在 `NotificationShade` 时 SF 报 `activeMode={id=5, 60.00 Hz}`；一旦本应用回到前台并有绘制，同一命令立刻报 `activeMode={id=4, 120.00 Hz}`、`--latency` 表头 `8333333`。⇒ 空闲 60Hz 是厂商 ADFR 的正常省电行为，不是"本应用被降档"，没有任何开关可强制的东西。**别再为这件事起采样循环**。
  - 同一份 dump 里 `mOverrideDisplayInfo` 是"此刻真跑的"（当时 mode 5 / presDeadline 16666666），顶层 `mBaseDisplayInfo` 是"渲染档位名"（mode 4 / 11333333）—— 只看后者会误判成恒 120Hz，只看前者的空闲值会误判成恒 60Hz。
  - `dumpsys SurfaceFlinger --latency "VRI-<pkg>/<Activity>#<id>"`：第一行就是当前刷新周期（`16666667`=60Hz / `8333333`=120Hz），后面才是每帧 (appStart, gpuComplete, present) 三元组，差值即真实 present 间隔。**应用静默时只打印第一行**（没有新帧），所以这个数必须在用户持续滚动的窗口里循环采样才有效；层名里的 `#<id>` 随窗口实例变，每轮重新 `--list | grep <pkg>` 取。
  - `--latency`/`gfxinfo` 的读数只有在**同一动作窗口内清零后单独跑**才算一次测量；跨段累计会让第二段仍是累计值（分位数直接作废）。
- **2026-09-29 用户裁决：性能基线不测了**（「算了别测这个了，我感觉还行」）⇒ 之后的玻璃/视觉轮次**不再以 framestats 当每批的闸门**；
  同时明确**不许建"没有实测值"的基线文档**（计划里那份 `miuix-glass-perf-baseline-2026-09.md` 因此没落地，只把结论写进方案文档）。
- **采样窗口要先确认"设备上真的在动"**：那轮两次 60~120 秒的循环采样全部零读数，原因是 `mCurrentFocus=NotificationShade`
  与随后熄屏被厂商冻结（`OplusHansManager … scene: |StrictMode-1|LcdOff`）。开测前先读一眼 `mCurrentFocus`，
  读完立刻在同一条命令里清零；用户没明确开始动之前别起长循环。

相关：[[project-jm-image-scramble-authority]]（去混淆往返那条链的算法权威口径）、[[project-nav-entry-recomposition]]
