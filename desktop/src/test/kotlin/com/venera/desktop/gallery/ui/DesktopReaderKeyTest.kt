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

    @Test
    fun 那一句的算式只在判据件里一处() {
        assertEquals(1, DesktopSourceTree.codeOccurrences(pagingFile, "internal fun desktopReaderKeyReading"))
        assertTrue("展示件没把那句念上屏", DesktopSourceTree.codeOccurrences(readerFile, "desktopReaderKeyReading(") >= 1)
    }

    // ── ③ 接线：链、焦点、与按钮共用同一颗步进 ───────────────────────────────

    @Test
    fun 阅读那一屏的链三件齐而焦点拿不到要说出来() {
        val text = DesktopSourceTree.codeText(readerFile)
        val start = text.indexOf(".focusRequester(")
        assertTrue("阅读那一屏根本没要焦点（没有焦点时按键送不进组合树）", start >= 0)
        val chain = text.substring(start, minOf(start + 500, text.length))
        assertTrue("少了 focusTarget()：节点不在焦点序里，requestFocus() 恒假", chain.contains(".focusTarget()"))
        assertTrue("少了 onKeyEvent：拿到焦点也没人读键", chain.contains(".onKeyEvent {"))
        assertTrue("事件没交给共享那颗摘读数的件（另写一份键位判据就是第二条真相）", chain.contains("asDesktopKeySignal()"))
        assertTrue("链没把这一枚键交给共享那张表（键表在 DesktopKeyNavigation，这里不许另立一张）", chain.contains("onDesktopReaderKey(signal)"))
        assertTrue("requestFocus() 的返回值被丢掉了 —— 拿不到焦点就是静默失败", text.contains("if (!granted)"))
        assertTrue("拿不到焦点那一句不在（KEYNAV 是这一条链唯一的可见证据）", text.contains("KEYNAV"))
        // 两条读数都要在：只念失败的那一句，每一帧就只能靠"没念"反推焦点（本机没有 OS 级输入，焦点是
        // 唯一能在真人窗口里量到的那半句 ⇒ 拿到也必须念出来，一次起窗就有一条读数）。
        assertTrue("没念「焦点=拿到」：那一格在窗口里就永远是反推出来的", text.contains("焦点=拿到"))
        assertTrue("没念「焦点=没拿到」", text.contains("焦点=没拿到"))
        // 那两句各带一截 ASCII（`granted=1` / `granted=0`）：交付包那一层的中文 stdout 全成乱码，
        // 只有 ASCII 那一截在包里读得出来 —— 而这一格偏偏要在真人机上量（起窗与包里两趟都靠它）。
        assertTrue("「焦点=拿到」那一句没带包里读得出的一截：", text.contains("granted=1"))
        assertTrue("「焦点=没拿到」那一句没带包里读得出的一截：", text.contains("granted=0"))
    }

    @Test
    fun 每一枚按下的键都念而不只念落到的那些() {
        // 这一条钉的是"量具"本身：那一句若被 `delta` 拦住，没落到的键就一个字都不念 ——
        // 于是「这枚键送到了这一层但我不接」与「这枚键根本没送到」压成同一种哑巴，
        // 音量键那一格就永远量不出来（这一批交的正是那把尺子，不是那条分支）。
        val text = DesktopSourceTree.codeText(readerFile)
        val gate = text.indexOf("if (signal.isKeyDown)")
        val note = text.indexOf("desktopReaderKeyReading(", gate)
        assertTrue("读不到「按下那一半」那一格（抬起的那一半不该念）", gate >= 0)
        assertTrue("那句读数没排在按下那一格之后", note > gate)
        assertFalse(
            "那一句被步进那一枚拦住了：不接的键就不念，音量键那一格永远量不出来",
            text.substring(gate, note).contains("delta"),
        )
    }

    @Test
    fun 键与按钮吃同一颗步进而那一颗只算一次() {
        val text = DesktopSourceTree.codeText(readerFile)
        assertEquals("步长算式出现第二处 = 键与按钮各有一份钳位：" + text, 1, DesktopSourceTree.codeOccurrences(readerFile, "desktopReaderStepTarget("))
        // 两枚入口（那两枚按钮 + 那四枚键）各调同一颗回调一次；出现第三处就是有人在中间另算了一份
        assertEquals("回调的调用点该是按钮与键各一处：" + text, 2, DesktopSourceTree.codeOccurrences(readerFile, "onStep(delta)"))
        // 两档各滚各的墙：这一批把步进从"只管 pager"改成"按档二选一"，两面墙都得各滚一次
        assertEquals(1, DesktopSourceTree.codeOccurrences(readerFile, "pagerState.scrollToPage(target)"))
        assertEquals(1, DesktopSourceTree.codeOccurrences(readerFile, "listState.scrollToItem(target)"))
    }

    @Test
    fun 键在两档都落而按钮仍只在整页翻那一档摆() {
        val text = DesktopSourceTree.codeText(readerFile)
        // 链挂在这一屏的根上（不在 `if (paged)` 那一支里）——纵向那一档按 ← → 也该走一页
        val chain = text.indexOf(".onKeyEvent {")
        val pagedBranch = text.indexOf("else if (desktopReaderIsPaged(mode))")
        assertTrue("读不到链（判式先失效）", chain >= 0)
        assertTrue("读不到整页翻那一支（判式先失效）", pagedBranch >= 0)
        assertTrue("链排在了整页翻那一支之后？纵向那一档会根本收不到键", chain < pagedBranch)
        // 按钮那一排仍然只有两枚，且没跟着键扩到纵向那一档
        assertEquals("‹ 上一页", 1, DesktopSourceTree.codeOccurrences(readerFile, "\"‹ 上一页\""))
        assertEquals("下一页 ›", 1, DesktopSourceTree.codeOccurrences(readerFile, "\"下一页 ›\""))
    }

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

    // ── ⑤ 离屏把键真送进组合树：接线与焦点的第一条路证 ──────────────────────

    private val reading = DesktopComicReading(
        "1461316", "某本漫画", "g", "第 1 话", placeholder = false, episodeIndex = 0, startPage = 0, coverUrl = "",
    )

    @Composable
    private fun KeyScreen(mode: DesktopReaderMode, steps: MutableList<Int>) {
        DesktopComicReaderContent(
            reading = reading,
            state = ComicReaderState.Ready(listOf("a.jpg", "b.jpg", "c.jpg")),
            sourceKey = "jm",
            listState = rememberLazyListState(),
            pagerState = rememberPagerState { 3 },
            mode = mode,
            seedNote = null,
            onModeChange = {},
            onStep = { delta -> steps.add(delta) },
            onBack = {},
            pageImage = { index, _ -> Box(Modifier.fillMaxSize()) { Text("页位$index") } },
        )
    }

    @Test
    fun 按一次右方向键落到后一页而不落两页() {
        val steps = mutableListOf<Int>()
        runComposeUiTest {
            setContent { KeyScreen(DesktopReaderMode.HORIZONTAL_LTR, steps) }
            onNodeWithText("共 3 页").performKeyInput {
                keyDown(Key.DirectionRight)
                keyUp(Key.DirectionRight)
            }
        }
        assertEquals("按一次 → 该且只该落一笔：$steps", listOf(1), steps)
    }

    @Test
    fun 纵向那一档也吃同一对键而落的是前一页() {
        val steps = mutableListOf<Int>()
        runComposeUiTest {
            setContent { KeyScreen(DesktopReaderMode.VERTICAL_CONTINUOUS, steps) }
            onNodeWithText("共 3 页").performKeyInput { keyDown(Key.PageUp) }
        }
        assertEquals("PageUp 在纵向那一档也要落到列表那面墙：$steps", listOf(-1), steps)
    }

    @Test
    fun 不相干的键不落也不吞() {
        val steps = mutableListOf<Int>()
        runComposeUiTest {
            setContent { KeyScreen(DesktopReaderMode.HORIZONTAL_RTL, steps) }
            onNodeWithText("共 3 页").performKeyInput { keyDown(Key.DirectionUp) }
        }
        assertTrue("不相干的键不该顺手翻页：$steps", steps.isEmpty())
    }

    @Test
    fun 日漫那一档也吃同一对键而不因为反排换含义() {
        val steps = mutableListOf<Int>()
        runComposeUiTest {
            setContent { KeyScreen(DesktopReaderMode.HORIZONTAL_RTL, steps) }
            onNodeWithText("共 3 页").performKeyInput { keyDown(Key.DirectionRight) }
        }
        // 这一档画面从右往左排，但 `→` 还是"后一页"：键跟语义而不是跟像素（见 onDesktopReaderKey 的注释）。
        // 三档各有一枚正向离屏用例，README 那句「三档都吃这一对键」才有对证。
        assertEquals("日漫那一档的 → 该落后一页：$steps", listOf(1), steps)
    }

    @Test
    fun 量具在事件那一路上真念得出而不只是算得出() {
        // 这一批交的是**读数**：那一句要在真事件经过链的时候念出来，而且不接的那些也要念。
        // 只靠"源码里那一句排在哪儿"判不够 —— 那条尺子拦得住写法，拦不住"这一屏压根没念"。
        val steps = mutableListOf<Int>()
        val captured = java.io.ByteArrayOutputStream()
        val previous = System.out
        try {
            runComposeUiTest {
                setContent { KeyScreen(DesktopReaderMode.HORIZONTAL_LTR, steps) }
                System.setOut(java.io.PrintStream(captured, true, "UTF-8"))
                onNodeWithText("共 3 页").performKeyInput {
                    keyDown(Key.Escape)
                    keyUp(Key.Escape)
                }
            }
        } finally {
            System.setOut(previous)
        }
        val text = captured.toString("UTF-8")
        assertTrue("那一句没在事件那一路上念出来（只算不念就是屏上什么都量不到）：$text", text.contains("落点=不接"))
        assertTrue("合成注入那一路要念得出原始等于无，念出数就是把它当成量过 AWT：$text", text.contains("原始=无"))
        assertEquals("抬起的那一半不该再念一行：$text", 1, text.lines().count { it.contains("D_按键") })
        assertTrue("不接的键不该落步进：$steps", steps.isEmpty())
    }
}
