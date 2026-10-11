package com.venera.desktop

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「应用内模糊在桌面上到底画不画得出来」那一枚探针的尺子（第六十二批）。
 *
 * ## 这批要答的问题与为什么从前答过一半
 *
 * S0-7 量的是 **gate**（`BlurEffect.isSupported() = true`、两枚 `isRuntimeShaderSupported()` 都真），
 * 而它自己写着「gate 说 true 不等于画面对，还差一次像素级确认」—— 那一步一直没做。
 * 批 61 把 `galleryBackdrop` 接上消费点时，`GLASS` 那一档因此只能画实心底并在标签上明说。
 * 这一批就是去补那一步：**同一扇窗里摆两枚棋盘，一枚糊、一枚不糊**，拍到帧就知道画不画得出来。
 *
 * ## 为什么探针也要有尺子
 *
 * 探针最容易犯的错不是崩，而是**静默地什么都没量**：两枚棋盘都没加 blur（"看着一样"会被读成"blur 不生效"）、
 * 或者 blur 加在了一枚空 `Box` 上（同上），或者窗根本没开（WGC 拍到的是别的窗）。
 * 下面三条各钉一种"什么都没量"的形状。
 */
class DesktopBlurRenderProbeTest {

    private val probe = DesktopSourceTree.desktopSourceOf("BlurRenderProbe.kt")

    /** ① 两枚棋盘：恰好一枚吃 blur、另一枚不吃（对照组必须存在，否则"没区别"读不出任何事）。 */
    @Test
    fun 两枚棋盘只有一枚吃模糊() {
        assertEquals("blur 那一枚必须恰好加在一枚棋盘上（现在是 ${DesktopSourceTree.codeOccurrences(probe, "Modifier.blur(")} 处）",
            1, DesktopSourceTree.codeOccurrences(probe, "Modifier.blur("))
        // 声明一颗 + 两枚棋盘各一颗 = 三处；少一颗就是"只摆了一枚"，那帧读不出对照。
        assertEquals("棋盘必须摆两枚（糊的与不糊的并排才叫对照）",
            3, DesktopSourceTree.codeOccurrences(probe, "Board("))
        // blur 必须吃在**棋盘**上：加在一枚空 Box 上，"没糊"会被读成"blur 不生效"。
        assertEquals("那一枚 blur 没吃在棋盘上",
            1, DesktopSourceTree.codeOccurrences(probe, "Board(Modifier.blur("))
    }

    /** ② 半径非零、且写在屏上：读数与画面同源，别拿"我传了 24"当屏上那枚是 24。 */
    @Test
    fun 半径非零而且写在屏上() {
        val code = DesktopSourceTree.codeText(probe)
        assertTrue("半径没写成显式那一枚（0 档会把\"没糊\"读成\"blur 不生效\"）", code.contains("private const val RADIUS_DP = 24"))
        assertTrue("blur 那一枚吃的是半径常量而不是别的", code.contains("Modifier.blur(radius = RADIUS_DP.dp"))
        assertTrue("半径没上屏（屏上那一行才是这帧的口径）", code.contains("半径 ") && code.contains("RADIUS_DP"))
    }

    /** ③ 窗要真开、要念一行归属：WGC 靠标题里的 PID 认帧，没这行就不知道拍到的是谁。 */
    @Test
    fun 开一扇窗并念一行归属() {
        val code = DesktopSourceTree.codeText(probe)
        assertTrue("没开窗（WGC 无从拍起）", code.contains("application {") && code.contains("Window("))
        assertTrue("标题没带 PID（帧归属核不了）", code.contains("ProcessHandle.current().pid()"))
        assertTrue("没念归属那一行", code.contains("S07B_窗口 已开"))
    }
}
