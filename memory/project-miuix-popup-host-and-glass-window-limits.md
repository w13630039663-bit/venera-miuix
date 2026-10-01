---
name: project-miuix-popup-host-and-glass-window-limits
description: miuix 弹层（OverlayDialog/ListPopup）必须有 miuix Scaffold 才绘制，设置页链路没有 ⇒ 要用窗口级 WindowDialog；且玻璃采样跨不了窗口，弹窗/独立窗口永远拿不到 Liquid Glass
metadata:
  type: project
---

miuix 0.9.4-rc01 的**弹层不是自绘的**：`MiuixPopupUtils.DialogLayout`（`utils/MiuixPopupUtils.kt:166`）只做一件事
——把 `DialogState` 注册进 `LocalDialogStates` / `LocalRootDialogStates`；真正画出来的是 `MiuixPopupHost()`（同文件 `:544`），
而它由 miuix `Scaffold` 挂载。`OverlayDialog` / `OverlayListPopup` / `OverlayBottomSheet` 全走这条路。

**Why:** 2026-09-29 做 `VeneraDialog` 转发件时，方案（含已批准稿）认定 miuix 侧对应件是 `OverlayDialog`。
按它写完就是**点了没反应的假弹窗**——设置页整条链 `VeneraSettingsHost` → `AndroidSettingsScreen` 里**没有 miuix Scaffold**
（仓内只有 `Navigation.kt`、`ComicDetailScreen`、`ComicSourceScreen` 三处 import 了它）。
能用的窗口级件是 `top.yukonga.miuix.kmp.window.WindowDialog`（`window/WindowDialog.kt:53`，
KDoc 原话 "rendered at window level without `Scaffold`"，内部自己起 `androidx.compose.ui.window.Dialog`）。

**How to apply:**
- 接 miuix 弹层前先确认**这一屏的祖先链里有没有 miuix `Scaffold`**；没有就用 `window/` 那一族，别用 `overlay/` 那一族。
  签名可用面：`show / modifier / title / summary / backgroundColor / enableWindowDim / onDismissRequest /
  onDismissFinished / outsideMargin / insideMargin / defaultWindowInsetsPadding / maxWidth / largeScreen / cornerRadius / content`。
  没有 `DialogButton` 这个件，`DialogContentLayout` / `DialogContent` 都是 internal，按钮自己排进 `content`。
- **Dialog 套 Dialog 要当真风险**：正文里含 `SettingsSelect`（已是 VeneraDialog）的那种"表单弹窗"，
  外层换成 `VeneraDialog` 就成窗口嵌套，预测式返回与焦点归属未实测 ⇒ 宁可整枚留 M3 并保持内部自洽。
- **玻璃（`Modifier.veneraGlass` / miuix `drawBackdrop`）采的是宿主窗口的 RenderNode**，
  独立 `Dialog` 窗口里取不到样 ⇒ 弹窗在 LIQUID_GLASS 档**只能保持实底**。这是物理限制，写进了
  `components/venera/VeneraControls.kt` 的 KDoc，别当"漏统一"去补。
- 查"有没有文件混 import 两家 blur"必须按 `^import com\.kyant` 匹配；`grep com\.kyant` 会把
  `androidx.compose.material3.Text` 的包名片段算成命中（我自己误报过一次）。Kyant 的合法出处是
  `components/backdrop/` 目录（`VeneraLiquidGlassNavBar.kt` + `InteractiveHighlight.kt`），不是一个文件。

读库真体的办法见 [[reference-gradle-cache-sources-jars]]；本轮其余落地口径见仓库
`docs/rounds/miuix-glass-surface-material-2026-09.md` 与 `FREEZE-STATEMENT.md` 的批次 D 节。
