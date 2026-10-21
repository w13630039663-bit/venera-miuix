package com.venera.desktop.gallery.ui

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 大图那一屏的**键盘缩放**（第六十六批）。
 *
 * ## 这一格补的是什么
 *
 * 那层有滚轮缩放（批 50）与「回到原大小」那一枚按钮（批 55），而那条键链今天只认三枚键
 * （`Esc` / `←` / `→`）⇒ 键盘上没有任何一档能放大、缩小、复位。这一批把那三档接上。
 *
 * ## 为什么尺子长这样
 *
 * 键盘是**外部输入**，本 agent 不许碰（动鼠标与按键都算可见影响）⇒ "按下去屏上有没有动"
 * 这一格今天只有两条路：纯函数那一半离线喂键（判据 ①②），以及**同一套算式**的取证把手那一半上屏拍帧（判据 ⑥）。
 * 两条都留着，才不致于把"接了"读成"通了"（同族：`-Pdetail` 那次三环缺一环）。
 */
class DesktopViewerZoomKeysTest {

    private val policy = DesktopSourceTree.desktopUiSource("DesktopGalleryViewerPolicy.kt")
    private val app = DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt")

    private fun down(key: Key) = DesktopKeySignal(key = key, isKeyDown = true)
    private fun up(key: Key) = DesktopKeySignal(key = key, isKeyDown = false)

    /** ① 六枚键各落到一个意图；抬起的那一半与不相干的键一律不消费。 */
    @Test
    fun 六枚键各落到一个意图() {
        // 主键盘上 `+` 与 `=` 是同键两枚码点（Shift 与否），小键盘另有一枚 ⇒ 三枚都算放大，
        // 只认其中一枚的形状是"我这儿能按，用户那儿按不动"。
        assertEquals("主键盘 Shift+= 那一枚要能放大", DesktopZoomIntent.IN, onDesktopZoomKey(down(Key.Plus)))
        assertEquals("主键盘 `=` 那一枚要能放大", DesktopZoomIntent.IN, onDesktopZoomKey(down(Key.Equals)))
        assertEquals("小键盘 `+` 那一枚要能放大", DesktopZoomIntent.IN, onDesktopZoomKey(down(Key.NumPadAdd)))
        assertEquals("主键盘 `-` 那一枚要能缩小", DesktopZoomIntent.OUT, onDesktopZoomKey(down(Key.Minus)))
        assertEquals("小键盘 `-` 那一枚要能缩小", DesktopZoomIntent.OUT, onDesktopZoomKey(down(Key.NumPadSubtract)))
        assertEquals("主键盘 `0` 那一枚要能复位", DesktopZoomIntent.RESET, onDesktopZoomKey(down(Key.Zero)))
        assertEquals("小键盘 `0` 那一枚要能复位", DesktopZoomIntent.RESET, onDesktopZoomKey(down(Key.NumPad0)))
        // 抬起的那一半不消费（那颗件里已写着这条规矩：按住连发 KeyDown，抬起只一枚 KeyUp）
        assertNull("抬起的那一半不许被消费", onDesktopZoomKey(up(Key.Plus)))
        assertNull("不相干的键留给原有那条链", onDesktopZoomKey(down(Key.Escape)))
        assertNull("方向键不归这一支", onDesktopZoomKey(down(Key.DirectionRight)))
    }

    /** ② 步进是**乘**而不是加，且两头都夹住；算式全树只在一处。 */
    @Test
    fun 步进是乘法而且两头夹住() {
        assertEquals("按一档到 1.25", 1.25f, desktopViewerZoomStep(1f, 10f, 1), 0.0001f)
        assertEquals(
            "连按两档必须是乘出来的 1.5625（加法会念成 1.5 —— 库的滚轮也是乘的）",
            1.5625f, desktopViewerZoomStep(desktopViewerZoomStep(1f, 10f, 1), 10f, 1), 0.0001f,
        )
        assertEquals("缩到底再缩还是 1（不许缩到看不见）", 1f, desktopViewerZoomStep(1f, 10f, -1), 0.0001f)
        assertEquals("顶到上限再放大就停在那儿", 10f, desktopViewerZoomStep(9.5f, 10f, 1), 0.0001f)
        assertEquals("上限给 0 也不能把倍数夹成 0（那是整层看不见）", 1f, desktopViewerZoomStep(1f, 0f, -1), 0.0001f)
        assertEquals(
            "步进算式全树只许一处定义（现在是 ${DesktopSourceTree.codeOccurrences(policy, "internal fun desktopViewerZoomStep")} 处）",
            1,
            DesktopSourceTree.codeOccurrences(policy, "internal fun desktopViewerZoomStep"),
        )
    }

    /** ③ 那条链仍只有一处，且缩放那一支排在原有那一支之前（次序反了 `+` 会被前一支吞不掉但读起来像坏了）。 */
    /** ④⑤ 复位走库那颗 `reset()`（与那枚按钮同一颗）；放大与缩小**两支**都吃活的倍数。 */
    /**
     * ⑥ 那一颗判读件必须有调用点：本仓那一族「实现了但零调用点」不许再添一枚。
     *
     * ⚠️ 这一格**没有图证**：按键属可见影响（禁），所以"按下去屏上动没动"今天只有两条路 ——
     * 纯函数那一半离线喂键（判据 ①）与这条"接线在不在"（判据 ⑥）。两半都钉住，才不致于把"接了"读成"通了"。
     */
    /** ⑦ 屏上那两句说明得跟着改：键既然接上了，"只有滚轮能缩放"那类句子就是假话。 */
    @Test
    fun 屏上那句不许再说键盘不能缩放() {
        val ui = DesktopSourceTree.desktopUiSources().filter { it.name.startsWith("DesktopGalleryViewer") }
        val offenders = ui.flatMap { file ->
            DesktopSourceTree.codeLinesOf(file).filter { line ->
                line.contains("只有滚轮") || line.contains("键盘不能") || line.contains("不能用键盘")
            }.map { "${file.name}: $it" }
        }
        assertTrue("屏上/说明里还留着这种句子：$offenders", offenders.isEmpty())
    }
}
