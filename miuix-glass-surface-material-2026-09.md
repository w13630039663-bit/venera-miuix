# Miuix 液态玻璃第三轴（SurfaceMaterial）+ 组件族迁移 · 第 1 轮 B1~B3

日期：2026-09-29　分支：`compose-migration`　计划档：`~/.qoder-cn/plans/humble-river-moose.md`

## 一、这一轮把"三轴"补齐了

| 轴 | 偏好键 | 管什么 | 关/默认档的后果 |
|---|---|---|---|
| `AppearanceStyle` | `pref_appearance_style` | 色板 + 形状 + 字号 + **控件后端**（M3 ⇄ Miuix） | 默认 MIUIX ⇒ 控件从此真的是 Miuix 形态 |
| `NavigationBarStyle` | `pref_navigation_bar_style` | 只决定底栏形态 | 与材质轴**互不联动** |
| `SurfaceMaterial`（新） | `pref_surface_material` | 卡片/分组/胶囊/按钮是否浮在采样自氛围光的玻璃上 | 默认 **SOLID** ⇒ 现状零变化 |

判据层是零 Android 依赖的纯函数（`ui/tokens/SurfaceMaterialPolicy.kt`），角色→三档、明暗→描边预设都在那里，
所以能在 `app/src/test` 里断言（本项目没有 Robolectric，判据不是纯函数就测不到）。
材质值经 `LocalSurfaceMaterial`（`compositionLocalOf`）下发，**没有**塞进 `VeneraTokenSet` ——
塞了就得同步改 `VeneraTheme.kt` 的 `remember(appearance)` 键，漏改正是 token 层注释警告过的"切档留旧值"。
顺带清掉一处真缺口：`LocalAppearanceStyle` 全仓零读取点，已删除。

## 二、采样源：为什么是静态氛围层

`components/VeneraAmbientBackground.kt` 里那张 Canvas 与 `content()` 是**兄弟节点**（Canvas 在底下），
所以卡片采它不会自引用；反过来采"页面内容层"就是 `feature/Navigation.kt:504` 注释记过的形状
（玻璃挂在录制层的后代里 ⇒ RenderNode 无限递归），而且底栏玻璃关着时它是 null、改它还等于动保护域。
氛围光不随滚动变 ⇒ 录制层不失效 ⇒ **滚动时不重捕**，这是"整站贴玻璃还不忘减帧"成立的前提。

代价如实记：该文件头注自称的"零离屏纹理"性质**只在 LIQUID_GLASS 档失去**，所以录制器条件安装，实色档一份都不建。
两个调用点（`Navigation.kt`、`VeneraSubActivityBase.kt`）签名不变 ⇒ **不需要保护域豁免**。

玻璃层只有一份 `Modifier.veneraGlass(role, shape)`（`components/venera/VeneraGlass.kt`），三处取值全部
注明出处、不自造：容器 alpha 0.22/0.28 抄底栏，染色 0.16 抄顶栏，模糊半径用库默认 `BlurDefaults.BlurRadius`。
**关闭档必须返回入参 Modifier 本身**（单测钉住引用同一性）。

## 三、分档判据：该透的才透

| 档 | 用于 | 库调用 | 一屏预算 |
|---|---|---|---|
| `CONTAINER` | 卡片 / 设置分组卡 | `textureEffect`（模糊+染色+Middle 描边） | ≤6 |
| `CONTROL` | 按钮 / 未选中的胶囊 Chip / 筛选胶囊 | `textureEffect` + Small 描边 | 少 |
| `INLINE` | 角标 / 只读标签 | `drawBackdrop` 里**只 blendColors、不 blur** | 二十个也不建模糊 |

三条**刻意不上玻璃**，都写在组件 KDoc 里防后来者"补统一"：

1. **选中态与禁用态的 Chip / 筛选胶囊**：选中靠 `primaryContainer`／实心主题色的**实底**表达"这条已生效"，
   半透明容器会把它洗成"看起来没选"——比不统一更糟。
2. **`VeneraSourceBadge`**：它那块固定深色底板的唯一职责是"压在任意封面图上都可读"，
   换成半透明采样层=用观感换掉对比度。
3. **所有弹窗**：`WindowDialog` 的内容跑在独立的 `androidx.compose.ui.window.Dialog` 窗口里，
   玻璃采的是**宿主窗口**的 RenderNode，跨窗口取不到样 ⇒ 硬贴只会是一块空白底板。物理限制，不是漏做。

## 四、四条纠正（相对批准稿，逐条查过出处）

| 批准稿 | 落地事实 |
|---|---|
| `VeneraDialog` 用 `OverlayDialog` | **接不通**。`MiuixPopupUtils.DialogLayout:166` 只把 `DialogState` 注册进 `LocalDialogStates`，真正绘制由 miuix `Scaffold` 内的 `MiuixPopupHost():544` 负责；而设置页链路 `VeneraSettingsHost`→`AndroidSettingsScreen` 没有 miuix Scaffold ⇒ 假弹窗。改用窗口级的 `WindowDialog`（KDoc 原话 "rendered at window level without `Scaffold`"）。 |
| "底栏 `VeneraLiquidGlassNavBar.kt` 是全站唯一 Kyant 岛" | 实为**一个目录两个文件**（还有 `InteractiveHighlight.kt`）。规则改为"Kyant 只准出现在 `components/backdrop/`"。 |
| 「强制 120Hz 开关」 | **不做，前提被证伪**（见 §六）。 |
| B1「设置域 5 个收口点」 | 实际漏了 4 处，本轮补完（见 §五）。 |

另有一条**自查方法上的错**要记：查"有没有文件混 import 两家 blur"时用 `grep com\.kyant`，
会把 `androidx.compose.material3.Text` 里的包名片段算成命中，我自己据此误报了一次违规。
按 `^import com\.kyant` 匹配才是干净的，实查结果：无文件混用。

## 五、设置域收口现状

已走转发件（覆盖全部设置子页）：`SettingsGroup` / `SettingsFutureGroup` 贴 `SETTINGS_GROUP` 玻璃；
`SettingsToggle→VeneraSwitch`、`SettingsSlider→VeneraSlider`、`SettingsSelect`→`VeneraDialog`+`VeneraTextButton`+`VeneraIconButton`；
`SettingsHome:96` 返回键→`VeneraIconButton`；`AppearanceSettings` 与 `BlockingSettings` 的按钮→`VeneraTextButton`；
`BlockingSettings` 删除确认→`VeneraDialog`；`AppSettings` 两枚（所有文件访问 / 切换存储目录）与 `UpdateCheckUi` 一枚→`VeneraDialog`。

**登记在册的两条不做，带理由**：

1. `NetworkSettings` 的代理**表单弹窗**整枚留在 M3 `AlertDialog`（含它自己的两颗 `TextButton`，让它内部自洽）。
   它正文里嵌 `SettingsSelect`，外层再换 `VeneraDialog` 就成了 Dialog 套 Dialog，
   预测式返回与焦点归属在真机上没测过——拿"UI 统一"去赌一条没人验过的窗口链是本仓禁止的假统一。
2. `OutlinedTextField` 的 `VeneraTextField` 转发件未建：miuix `TextField` 无 `isError`、label 模型不同，
   映射要单独一批带真机验。

**B3 待拍板的一条**：主框架最后一处 M3 控制族直连是 `components/ComicTileLayout.kt:190` 的 `IconButton`（布局切换钮）。
组件本身不在 FROZEN 名单，但调用点含**探索页 `UnifiedExploreScreen:552` 与历史页 `HistoryScreen:201`**（都在名单内），
而转发件默认按 `AppearanceStyle.MIUIX` 换后端 ⇒ 换它=改冻结屏观感。按"本轮不给豁免"没动。

## 六、性能这条为什么收住

用户裁决：「算了别测这个了，我感觉还行」。收住前测清的一件事写下来，免得下一个人又以为面板只有 60Hz：

- `dumpsys display` 顶层 `modeId 4 / renderFrameRate 120 / presDeadline 11333333` 只是 DisplayManager 侧的**渲染档位名**；
  同一时刻 `dumpsys SurfaceFlinger` 的 `activeMode` 空闲时报 `{id=5, 60.00 Hz}`（`mWorkDuration=16.67`），
  **应用回前台并有绘制后立刻变 `{id=4, 120.00 Hz}`**，`--latency` 表头同步从 16666666 变 8333333。
- 应用自身投票本来就是 120（`AppRequestRefreshRates: [120.00 Hz - modeId 4]`，`OPlus ADFR Mode:1`，`FakeFrame:1`）
  ⇒ 空闲降 60 是厂商省电行为，不存在"被降档"可强制。**"强制 120Hz 开关"确定不做。**
- 滚动段的 framestats / present 间隔**没有数**，所以计划里的 `miuix-glass-perf-baseline-2026-09.md` **没建**
  ——不往里放没测过的值。每批"framestats 当闸"随之作废，改为：每批跑 `testDebugUnitTest`+`assembleDebug`，
  观感与掉帧的真机判定列在 §八。

## 七、验证读数

- `:app:testDebugUnitTest` **53 套 / 389 条 / 0 失败 / 0 错误**（同一批时间戳，无遗留 XML 混入）。
  新增 `SurfaceMaterialPolicyTest` 8 条：默认档 SOLID、只有 LIQUID_GLASS 开玻璃、角色→三档、
  明暗×档→四枚描边预设、点缀档不描边、四枚 `libName` 逐字钉住。
- `:app:assembleDebug` BUILD SUCCESSFUL。
- `debugRuntimeClasspath` 里 material3 仍 `1.5.0-alpha22`、miuix 仍 `0.9.4-rc01` ⇒ 没为玻璃动版本，
  `ModalBottomSheet` 那条 `NoSuchMethodError` 风险没被触发。
- 混库自查：无文件同时 `import miuix.kmp.blur` 与 `import com.kyant`。
- 预览面：`VeneraComponentsPreview` 从 4 张扩到 **8 张**（{MIUIX,MD3}×{SOLID,LIQUID_GLASS}×{浅,深}），
  根节点改套 `VeneraAmbientBackground`（不套就没有采样源，两档看起来一样会被误读成"玻璃没生效"）。
  预览能证明的只有"SOLID 与改动前同形 + 玻璃档不崩不改度量"；**模糊透不透证明不了** ——
  `drawBackdrop` 内部先过 `isRuntimeShaderSupported()`，layoutlib 有没有 RuntimeShader 我没实测过。

## 八、待真机验清单（本轮一次都没在真机上看过）

1. LIQUID_GLASS × MIUIX/MD3 × 浅/深：设置页分组卡与主框架卡片**透得出氛围光、不泛白、标题读得清**。
2. SOLID 档与改动前逐屏截图无差异（材质轴的零回归凭据）。
3. 外观轴换到 MIUIX 档：开关/滑条/图标按钮/弹窗**换成 Miuix 形态后是否还认得出、点得动**
   （这一条是 §一那句"控件后端跟外观轴"的直接代价，与材质轴的零回归是两件事）。
4. 设置里两枚玻璃开关（界面材质 / 导航栏）任意组合：不互采、不叠崩。
5. 长按卡片仍能进多选（`VeneraCard` 的 onClick/onLongPress 走 miuix 官方重载，玻璃挂外壳不进裁剪层）。
6. 切回 SOLID 后**无旧玻璃残影**（切档不重组那类病）。
7. 弹窗（`VeneraDialog`）在 MIUIX 档：进出场、点外部取消、返回键归属都正常；
   尤其 `SettingsSelect` 在 `BlockingSettings` 表单区里弹起时不重叠错位。

## 九、挂账

- 前置机械 PR：9 个 `import androidx.compose.material3.*` 通配拆显式 import（未做，动的是 B4~B6 的文件）。
- B4 详情+源管理 ~107 处；B5 首页/收藏/历史/搜索/图片收藏 ~39 处（全部 FROZEN，本轮未获豁免）；
  B6 阅读器+画廊 ~53 处（含 3 个 `ModalBottomSheet`，全仓最高风险）。
- `DropdownMenu`（8 文件/21 项）、`DatePicker`、`SegmentedControl` 三条**没有 miuix 对应物**，只做外层贴玻璃、不换后端。
- 采氛围层拿到的玻璃 = **模糊 + 染色 + 描边高光，不含折射透镜**（`lens` 在 miuix-blur 里不存在，只有底栏用 Kyant 实现）。
  对外描述别说内容区会"折"。设备倾斜高光本轮不上（新增传感器与每帧重算，与减帧目标反着走）。
