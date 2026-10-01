---
name: project-miuix-widget-counterparts
description: miuix 0.9.4-rc01 控件面与 material3 的对应物对账（哪些能换后端、哪些是结构性缺口）+ 2026-09-30 迁移收口状态：有对应物的已清零、剩余每处都有不迁的理由；换统计脚本后新旧总数不可比
metadata:
  type: project
---

2026-09-29 做批次 B4 前逐字读了 gradle 缓存里的 `miuix-ui-android-0.9.4-rc01-sources.jar`
（解法见 [[reference-gradle-cache-sources-jars]]）。**用途**：`Venera*` 转发件的"能不能双后端"
必须先查这张表，否则会写出"编译得过、miuix 档偷偷忽略一个参数"的假开关。

## 有对应物（可真双后端）

| 控件 | miuix 出处 | 与 M3 的差别（映射必须显式写） |
|---|---|---|
| Switch / Slider / IconButton / TextButton | `basic/*.kt` | Slider 的 `steps` **两家都有**（早期判断"miuix 没 steps"是错的）；TextButton 以 `text: String` 起头、**没有 slot** |
| Checkbox | `basic/Checkbox.kt:60` | 模型不同：`state: ToggleableState` + `onClick: (() -> Unit)?`（M3 是 `checked: Boolean` + `onCheckedChange: ((Boolean)->Unit)?`）⇒ 两个纯函数 `veneraToggleState` / `veneraCheckClick`，null 回调**必须原样传**（换成 `{}` 会让禁用态照样翻转） |
| HorizontalDivider | `basic/Divider.kt:30` | 连名字带参数（`modifier/thickness/color`）都同形 ⇒ **直接换 import 即可**，不必建转发件；内部是 `fillMaxWidth()` 的 Canvas 线，默认 0.75dp |
| TextField | `basic/TextField.kt:294`（`value: String` 那个重载，共 3 个重载） | `label: String` 而非槽位、**没有 placeholder 槽**（靠 `useLabelAsPlaceholder`）；有 `visualTransformation`（密码框靠它，丢了=明文上屏）；没有 `isError` |
| Button | `basic/Button.kt:49` | content 是 `RowScope.() -> Unit`，**与 M3 同形 = 真交集** ⇒ 唯一能塞"按钮里带进度条"的那件 |

## 结构性缺口（没有对应物，只做登记、不硬凑）

- **描边按钮**：`ButtonColors` 只有 `color/disabledColor/contentColor/disabledContentColor` 四位，
  **没有描边位** ⇒ M3 `OutlinedButton` 换过去会连描边一起丢。
- **带图标的文字按钮**：miuix `TextButton` 没有 slot ⇒ M3 那种 `TextButton { Icon; Text }` 无处映射。
- **锚点式菜单**：`basic/Dropdown.kt` 只是 ListPopup 的行渲染件（`DropdownImpl`/`DropdownItem`），
  没有 `DropdownMenu(anchor)` 语义。
- **底部面板**：只有 `layout/BottomSheetContentLayout.kt`（内容布局），没有窗口级的 `ModalBottomSheet`。
- **对话框**：没有 `AlertDialog`，公开件是 `window/WindowDialog`；`OverlayDialog` 需要 miuix `Scaffold`
  里的 `MiuixPopupHost` 才画得出来（见 [[project-miuix-popup-host-and-glass-window-limits]]）。
- `DatePicker`（只有 `NumberPicker`）、日期时间选择、m3 的 `TopAppBar` 几何参数不等价。
- 主题侧：miuix `ColorScheme` **没有 `error` 位** ⇒ 破坏性色只能用 M3 的 `colorScheme.error`，
  两家共用这一个来源色，别各写一套。

**How to apply:** 新建/扩参数前先回到这张表；扩了必须在两家都有落点，且映射写成可断言的纯函数
（先例：`VeneraWidgetMappingTest`、`veneraFieldBorderColor`）。`TextButtonColors` 是 data class 能 `.copy()`，
**M3 的 `ButtonColors` 不是 data class、没有 copy** ⇒ M3 侧只能调工厂传参。

## 迁移收口状态（2026-09-30 复查，别再当"还有没迁的"）

**有对应物的那批（Switch / Slider / Checkbox / IconButton / Button / TextButton / TextField / Dialog /
Card / Divider）调用点已清零。** 剩下的每一处 material3 直连都能说出"为什么不能迁"，不是漏迁：
图标本体与文字本体（两家同名件，刻意保留）、波浪环三件（见 [[project-loading-indicator-direction]]）、
锚点下拉菜单、`ModalBottomSheet`、"图标+文字"的文字按钮、`OutlinedButton`、带 error 色的输入框、
`Scaffold`（同名件参数面不同 = 重做一层窗口内布局）、`DatePicker`、以及 MD3 档取色的主题桥与
转发件自己的 MD3 后端（这两类**必须**留 M3）。

**⚠️ 口径**：这类总数换脚本后**新旧不可比**。2026-09-30 我把清单脚本升级（符号去重、`Md3*` 别名并入）后，
同一份代码从"336"读成"322"—— 那是尺子变了，不是又迁掉了 14 条。要么复跑旧口径要么重报基线，
不要直接相减（同 [[feedback-probe-numbers-self-audit]]）。

**要真往前推只有三条路**（都不是"继续迁"）：① 补两个结构性缺口（ModalBottomSheet / 锚点下拉菜单）
得自建件并独立立项；② 搜索页三枚弹窗换 `VeneraDialog` 会把它那套紧凑排版改写成 58×40dp 胶囊，
属**观感决策**，一直挂着等用户拍板；③ 把已迁件的真机观感账补完。
顶栏那一族在批次 H 后改走 `VeneraTopBarPill`（见 [[project-topbar-pill-unification]]）。
