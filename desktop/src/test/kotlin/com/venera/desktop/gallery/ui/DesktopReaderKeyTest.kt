package com.venera.desktop.gallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.runComposeUiTest
import com.venera.desktop.gallery.data.DesktopReaderMode
import io.github.composefluent.component.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第八十七批·下半：阅读那一屏的**键位**。
 *
 * ## 这一批要把哪两件事分开
 *
 * 「这一枚键该落到哪一步」是纯算式（离线直接喂 [DesktopKeySignal]）；
 * 「这一枚键今天到不到得了这一层」是接线 + 焦点，只有把键真的送进组合树才知道。
 * 下半那一格第一次有了第三条路：`performKeyInput` 走的是测试那份输入调度。
 *
 * ⚠️ **这一条路的量程是台架量出来的，不是推断出来的**（`_qa/b87low_perturb.js` 的 P10：
 * 把 `.focusTarget()` 从阅读那一屏的链上撤掉，下面那三条离屏用例**照样全绿**，
 * 红掉的只有源码文本那两把尺子）⇒ 这一路证的是「链挂在树上、事件进得来、落到 `onStep`、抬起不重复」，
 * 它**量不到焦点**。真人窗口里那一格由 `KEYNAV=… 焦点=拿到/没拿到` 那句读数说（起窗那一趟），
 * 而 AWT 原始码那一格这一路根本够不着（合成事件没有 `java.awt.event.KeyEvent` 可取，
 * `D_按键` 在这里必然念 `原始=无` —— 那正是这一格要念的内容，不是缺陷）。
 * 别把"离屏按得动"写成"用户那儿能按"：那是本仓记过的"我这儿能按、用户那儿按不动"那一形。
 *
 * ## 音量键为什么反过来钉一条「不许上屏」
 *
 * 对岸那一族的形状是：`pref_volume_key_turn` **默认关**，且设置页有一枚看得见的开关。
 * 桌面这两条都凑不齐：设置页今天没有那一枚（摆上去就是没人按得动的假开关），
 * 而 VK 175 到不到得了这一层**一次都没量过**（本机无 OS 级输入，手册 §8 那条硬边界）。
 * 所以这一批交的是**量具**（`D_按键` 那句把原始码念出来），不是那条分支。
 * 判式扫 main（判据文件自己写着那几个名字，扫整仓会咬到自己）。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopReaderKeyTest {

    private val readerFile = DesktopSourceTree.desktopUiSource("DesktopComicReader.kt")
    private val navFile = DesktopSourceTree.desktopUiSource("DesktopKeyNavigation.kt")
    private val pagingFile = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopReaderPaging.kt")

    private fun down(key: Key) = DesktopKeySignal(key, isKeyDown = true)
    private fun up(key: Key) = DesktopKeySignal(key, isKeyDown = false)

    // ── ① 纯算式：键 → 步进 ────────────────────────────────────────────────

    @Test
    fun 方向键与翻页键各落一步而方向跟着按钮() {
        // ‹ 在左、› 在右，键必须同向（与浮层那一族 ⑬ 同一条口径）：← = 前一页、→ = 后一页。
        assertEquals(-1, onDesktopReaderKey(down(Key.DirectionLeft)))
        assertEquals(1, onDesktopReaderKey(down(Key.DirectionRight)))
        // PageUp 往上、PageDown 往下：与方向键同向，不是"页面往哪边滑"而是"读到哪一页"。
        assertEquals(-1, onDesktopReaderKey(down(Key.PageUp)))
        assertEquals(1, onDesktopReaderKey(down(Key.PageDown)))
    }

    @Test
    fun 抬起的那一半不落也不吞() {
        assertNull("抬起也算一次翻页 ⇒ 按住不放就是连翻两页", onDesktopReaderKey(up(Key.DirectionRight)))
        assertNull(onDesktopReaderKey(up(Key.PageDown)))
    }

    @Test
    fun 不归这一屏的键交回空而不是一() {
        // 交回 0 会被调用方读成"翻成了但页没动"；交回 null 才是"这枚键不归我"。
        listOf(Key.Escape, Key.DirectionUp, Key.DirectionDown, Key.Enter, Key.Spacebar, Key.Zero).forEach {
            assertNull("$it 不该落到翻页上", onDesktopReaderKey(down(it)))
        }
    }

    @Test
    fun 键表只在判据件里一处定义() {
        assertEquals("键表出现第二处就是第二条真相", 1, DesktopSourceTree.codeOccurrences(navFile, "internal fun onDesktopReaderKey"))
        // 对照：那一族的两位邻居还在同一颗件里（读空会让上面那条恒真）
        assertTrue("对照组失效：同一颗件里读不到浮层那颗键判据", DesktopSourceTree.codeText(navFile).contains("internal fun onDesktopOverlayKey"))
    }

    // ── ② 那一读数：每一枚到得了这一层的键都要念得出 ─────────────────────────

    @Test
    fun 落到的那一格念得出落点而没落到的念不接() {
        val next = desktopReaderKeyReading(Key.DirectionRight, rawKeyCode = 39, delta = 1)
        assertTrue("那一句要带 D_按键 那个前缀：$next", next.startsWith("D_按键"))
        assertTrue("落点后一页要说出来：$next", next.contains("落点=后一页"))
        assertTrue("原始码要一起念，音量键那一格靠它才有读数：$next", next.contains("原始=39"))
        assertTrue("落点=前一页 那一档：${desktopReaderKeyReading(Key.DirectionLeft, 37, -1)}",
            desktopReaderKeyReading(Key.DirectionLeft, rawKeyCode = 37, delta = -1).contains("落点=前一页"))
        assertTrue(
            "不归这一屏的键必须念得出「不接」而不是不吭声：${desktopReaderKeyReading(Key.Escape, 27, null)}",
            desktopReaderKeyReading(Key.Escape, rawKeyCode = 27, delta = null).contains("落点=不接"),
        )
    }

    @Test
    fun 合成那一路念得出原始等于无() {
        // 离屏注入没有 AWT 事件可取；那一句必须说"原始=无"，否则读起来像量过 AWT 那一层
        assertTrue(
            desktopReaderKeyReading(Key.DirectionRight, rawKeyCode = null, delta = 1).contains("原始=无"),
        )
    }

    // ── ③ 接线：链、焦点、与按钮共用同一颗步进 ───────────────────────────────

    // ── ④ 音量键：今天不许上屏，等一次真读数 ────────────────────────────────

    @Test
    fun 音量键那一路今天不许长出代码来() {
        val banned = listOf("pref_volume_key_turn", "VK_VOLUME", "VolumeUp", "VolumeDown", "175", "176")
        val offenders = DesktopSourceTree.desktopMainSources().flatMap { file ->
            banned.filter { token -> DesktopSourceTree.codeOccurrences(file, token) > 0 }.map { "${file.name}:$it" }
        }
        assertEquals(
            "音量键要先有一次真按的读数才许接（这一批交的是 D_按键 那句量具）：$offenders",
            emptyList<String>(),
            offenders,
        )
        // 对照组：那几个名字在对岸确实存在，否则上面是一条恒真的空判式
        val against = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("data/prefs/VeneraPreferences.kt"))
        assertTrue("对照组失效：对岸读不到 pref_volume_key_turn", against.contains("pref_volume_key_turn"))
        val reader = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("reader/VeneraReaderScreen.kt"))
        assertTrue("对照组失效：对岸阅读那一屏读不到 KEYCODE_VOLUME_DOWN", reader.contains("KEYCODE_VOLUME_DOWN"))
        // 对岸那一枚默认关：桌面既没有开关、又没量过原始码，接上就是两条都缺的那一档
        assertTrue(
            "对岸默认值改了这条要重判（现在按『默认关』写的）",
            Regex("""getBoolean\(KEY_VOLUME_KEY_TURN, (true|false)\)""").find(against)?.groupValues?.get(1) == "false",
        )
    }

    @Test
    fun 阅读那一族的偏好今天只有一枚键() {
        // 音量键那枚偏好要接，先要有消费点与一次真读数；在那之前偏好件里不许长出第二枚键名
        // （"屏上还没有而盘上先写"那一族：写进去没人读，读的人又没人写，就是两本对不上的账）。
        val prefsFile = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicReaderPreferences.kt")
        assertEquals(
            "那一颗件里的键名数量：" + DesktopSourceTree.codeText(prefsFile),
            1,
            Regex("""private const val \w+_KEY""").findAll(DesktopSourceTree.codeText(prefsFile)).count(),
        )
        // 对照：那条正则在这颗件里确实读得到东西（读空会让上面那条"恰一处"恒真）
        assertTrue(
            "对照组失效：那颗件里读不到模式那枚键的声明",
            DesktopSourceTree.codeText(prefsFile).contains("private const val READING_MODE_KEY"),
        )
    }

}
