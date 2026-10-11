package com.venera.desktop.gallery.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * 按键落到动作上的那一族判据：浮层与大图（Esc 关、← / → 步进、+ − 0 缩放）与阅读那一屏（← → PageUp PageDown）。
 *
 * ## 为什么判据在这里而不是在修饰符上
 *
 * 按键事件是**外部输入**，它该落到哪一个动作上是一条可以离线判的规矩；而"这一枚键今天落到了哪里"
 * 要真按一次才知道。这一颗把两者切开：这里是纯函数（单测直接喂 [DesktopKeySignal]），
 * 修饰符那一格只做"摘读数 + 交回去"，见 `VeneraDesktopApp` 与 `DesktopComicReader` 里那两条 `.onKeyEvent` 链
 * （判据 ⑥ 钉**一屏一处**，第八十七批·下半把"全模块一处"改成这一条，理由在那条判据的注释里）。
 * 第五十批把大图那一层挂进浮层**同一条**链，所以那一屏内仍是"一处链服务两层"。
 *
 * ## 抬起的那一半一律不消费
 *
 * Windows 上按住不放会连发 `KeyDown`，抬起时发一枚 `KeyUp`。如果 `KeyUp` 也被当成一次动作，
 * "按一次 →"在屏上就是翻两页；而把 `KeyUp` `return true` 吞掉，别的接收者（比如以后接上的
 * 输入框）就再也看不到这枚键 —— 所以这里 `return false`，让它按系统的方式走完。
 *
 * ## 焦点是这一条链的前提，不是可选项
 *
 * `FocusOwnerImpl.dispatchKeyEvent`（`ui:1.12.0` commonMain:352-373）在没有焦点时走
 * `rootFocusNode.nearestAncestor(Nodes.KeyInput)`，而 `rootFocusNode` 是焦点系统自己造的
 * 那一颗（同文件 `:75`），它上面没有祖先 ⇒ **什么都没聚焦时，按键送不进组合树**。
 * 所以每一屏出现时必须由 `FocusRequester` 把焦点要过来（拿不到会在 stdout 念一行 `KEYNAV=`，
 * 判据 ⑤ 钉着这一条，第八十七批·下半把同一条要求接到阅读那一屏），
 * 而不是把修饰符挂到最外层那格上"等着" —— 那种写法看起来接了，其实收不到。
 */
internal data class DesktopKeySignal(
    val key: Key,
    /** `true` = 按下；`false` = 抬起（或这枚事件根本不是按键状态变化）。 */
    val isKeyDown: Boolean,
)

/** 从真 `KeyEvent` 上只摘这两枚读数 —— 判据要的输入面越小，越不容易与浮层抢别的东西。 */
internal fun KeyEvent.asDesktopKeySignal(): DesktopKeySignal =
    DesktopKeySignal(key = key, isKeyDown = type == KeyEventType.KeyDown)

/**
 * 把一枚键落到浮层与大图那一层的动作上（第五十批起是**两层**）。
 *
 * 两层次序（判据 ⑩⑪ 钉着）：大图那一层开着时 `Esc` **只**关它，再按一次才关浮层。
 * 反过来写（两枚都调）的形状是"点进去一下全没了" —— 用户按一次 Esc 就从墙上直接回到墙。
 *
 * `←` / `→` **两层共用同一对 `onStep`**（判据 ⑬）：查看器不因自己开着就换一份算式，
 * 而两份算式的第一处差别总是落在"一边钳位一边不钳位"上。
 *
 * @return `true` 表示这枚键已被消费（系统不会再处理它）；不相干的键与抬起的那一半都返回 `false`。
 */
internal fun onDesktopOverlayKey(
    signal: DesktopKeySignal,
    viewerOpen: Boolean,
    onCloseViewer: () -> Unit,
    onClose: () -> Unit,
    onStep: (Int) -> Unit,
): Boolean = when {
    !signal.isKeyDown -> false
    signal.key == Key.Escape -> {
        if (viewerOpen) onCloseViewer() else onClose()
        true
    }
    // ← 是上一张、→ 是下一张：与面板顶上那两枚按钮同向（‹ 在左、› 在右），反了就是键与鼠标两套真相。
    signal.key == Key.DirectionLeft -> { onStep(-1); true }
    signal.key == Key.DirectionRight -> { onStep(1); true }
    else -> false
}

/** 键盘缩放那一档落到哪一个动作上（第六十六批）。 */
internal enum class DesktopZoomIntent { IN, OUT, RESET }

/**
 * 大图那一屏的缩放键 → 动作。
 *
 * ## 为什么另起一颗而不是塞进 [onDesktopOverlayKey]
 *
 * 那颗的契约是"关与翻页"，签名已经五枚参数；再塞两枚进去，调用点就要为**每一层**都递缩放态，
 * 而缩放只属于大图那一层。另起一颗让"哪几枚键算缩放"这件事单独可测（判据 ①），
 * 链上仍是**那一屏的一处** `.onKeyEvent {`（判据 ③ 钉着；第八十七批·下半把判据 ⑥ 从"全模块一处"
 * 改成"一屏一处"，因为阅读那一屏是另一层、与浮层不共存，理由钉在那条判据的注释里）。
 *
 * ## 三档各认两到三枚键
 *
 * 主键盘上 `+` 与 `=` 是同一颗键的两个码点（Shift 与否），小键盘另有 `NumPadAdd` ——
 * 只认其中一枚的形状就是"我这儿能按，用户那儿按不动"。`0` 与 `NumPad0` 同一颗道理。
 *
 * ⚠️ 这一格**没有图证**：按键属可见影响（本 agent 不许碰），所以"按下去屏上动没动"
 * 只有离线喂键（判据 ①）与接线（判据 ③⑥）两条路作证，真机那一步挂在手册 §17 第 2 项那笔用户回执上。
 * 第八十七批·下半给这一族开出了**第三条路**（离屏把键送进组合树，见 [onDesktopReaderKey] 的注释），
 * 缩放那一族的用例留在那一批之后补：换了尺子不补用例就是让旧读数继续管事。
 */
internal fun onDesktopZoomKey(signal: DesktopKeySignal): DesktopZoomIntent? = when {
    !signal.isKeyDown -> null
    signal.key == Key.Plus || signal.key == Key.Equals || signal.key == Key.NumPadAdd -> DesktopZoomIntent.IN
    signal.key == Key.Minus || signal.key == Key.NumPadSubtract -> DesktopZoomIntent.OUT
    signal.key == Key.Zero || signal.key == Key.NumPad0 -> DesktopZoomIntent.RESET
    else -> null
}

/**
 * 第八十七批·下半：阅读那一屏的键 → 步进（`+1` = 后一页、`-1` = 前一页，`null` = 这枚键不归这一屏）。
 *
 * ## 方向跟着那两枚按钮而不是跟着画面往哪边滑
 *
 * `‹ 上一页` 在左、`下一页 ›` 在右，所以 `←` 落前一页、`→` 落后一页 —— 与浮层那一族 ⑬ 同一条口径。
 * 日漫那一档（`reverseLayout`）里画面是从右往左排，但"前一页"还是"前一页"：
 * 键跟着**语义**走，不跟着像素走；否则切一档就换一次键的含义，那才是真的两套真相。
 * `PageUp` / `PageDown` 同向（读到哪一页，不是页面往哪边滑）。
 *
 * ## 这一族第一次有了"键真送进组合树"那条路证
 *
 * 判据 ① 只喂 [DesktopKeySignal]，它证的是算式；接线（链在不在、焦点要没要到）从前只有源码文本可判。
 * `performKeyInput` 走的是测试那份输入调度 ⇒ 它**能**证「焦点要到了、链在收、收完落到 `onStep`」，
 * 它**不能**证 AWT 那一侧的原始码送不送得进来（合成事件没有 `java.awt.event.KeyEvent` 可取，
 * 所以 `D_按键` 那一句在这一路必然念 `原始=无`）。**音量键就卡在这第二条上**：
 * 本机没有 OS 级输入（手册 §8），而 VK 175 到不到得了这一层一次都没量过 ⇒
 * 这一批交的是量具（[desktopReaderKeyReading] 那句把原始码念出来），不是那条分支；
 * 不许上屏那一条由 `DesktopReaderKeyTest` ④ 钉着（含"对岸默认关"那条对照）。
 */
internal fun onDesktopReaderKey(signal: DesktopKeySignal): Int? = when {
    !signal.isKeyDown -> null
    signal.key == Key.DirectionLeft || signal.key == Key.PageUp -> -1
    signal.key == Key.DirectionRight || signal.key == Key.PageDown -> 1
    else -> null
}
