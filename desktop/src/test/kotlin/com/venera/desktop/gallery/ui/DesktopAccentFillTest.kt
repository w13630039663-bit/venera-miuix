package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.darkColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 95 · `darkColors(accent = …)` 里那枚 `accent` 参数对「填充那一格」是**假开关**（进程内量，零窗口）。
 *
 * ## 怎么撞上的
 *
 * 批 94 的包内图证：详情那一屏顶上那枚 `AccentButton` 主胶囊拍出来是**天蓝**，而同一窗里
 * `DesktopTheme.AccentFuji` 的描边与文字都是藤紫。集成方那一行确实是
 * `FluentTheme(colors = DesktopTheme.colors())`（`VeneraDesktop.kt:502`），而 `colors()` 写的是
 * `darkColors(accent = AccentFuji)`（`DesktopTheme.kt:161`，注释还写着"accent 走藤紫"）。
 *
 * ## 量到的成因（`javap -c` 到字节码级 + ③ 现数表内容）
 *
 * `FluentThemeKt.darkColors-8_81llA(long accent)` → `ColorsKt.generateShades-8_81llA(long)`，那颗件是：
 *
 * ```
 * 0: invokestatic  getAccentShades:()Ljava/util/Map;
 * 7: Map.get(accent)
 * 15: dup / ifnonnull 23
 * 20: invokestatic  getDefaultShades()          // ← 不在表里就整条回落
 * ```
 *
 * 只看这一段会得出"我们那枚恰好不在表里 ⇒ 换一枚色就行"，而那是**错的**：
 * `getAccentShades()` 自己的字节码是 `mapOf(0xFF0078D4 to Shades(...))` ⇒ **一颗键**（③ 现数 `表大小=1`），
 * 且 `getDefaultShades()` 返回 `accentShades.entries.first().value` ⇒ **回落那一份就是这颗键自己的值**。
 * 两句合起来：**深色档上 `fillAccent.default` 恒为 `#60CCFE`，递进任何 accent 都动不了它**（④ 六枚现量全同）。
 *
 * ⇒ 这一批的处置只能「**不经过那条链**」：详情那枚主胶囊改用我们自己的 `DesktopTheme.AccentFill`
 * （稿 `--accent-fill` = `#5B47A8`），钉死在 `DesktopComicContinueReadingTest` ⑩/⑪。
 *
 * ⚠️ ①~④ 这四条判据钉的是**现状**（库 v0.1.0 的行为，不是我们的选择）：哪天升库或改成显式 `Colors(...)`，
 * ①/③/④ 要跟着改判成"填色真的跟着 accent 走"，而不是把尺子删掉了事（本仓那一族"改判时把尺子一起删"）。
 * ⑤ 是另一族：它钉的是**量具能力边界**（离屏渲染量不到像素），红了要升级而不是删。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopAccentFillTest {

    private fun hex(color: Color): String = "0x" + color.value.toString(16).padStart(16, '0')

    /** ① 现状读数：递进藤紫，`fillAccent.default` 交回的是库默认那一枚蓝。 */
    @Test
    fun 递进藤紫而填充那一格交回的是库默认那枚蓝() = runComposeUiTest {
        var fill = Color.Unspecified
        var textAccent = Color.Unspecified
        setContent {
            FluentTheme(colors = DesktopTheme.colors()) {
                fill = FluentTheme.colors.fillAccent.default
                textAccent = FluentTheme.colors.text.accent.primary
            }
        }
        println("D_主题 fill=" + hex(fill) + " 文字强调=" + hex(textAccent) + " 递进去的=" + hex(DesktopTheme.AccentFuji))
        assertEquals(
            "`darkColors(accent = 藤紫)` 递进去而 `fillAccent.default` 没跟着变 —— 这条钉的是**库的现状**：" +
                "不在预置表里的 accent 整条回落到默认那套蓝，而表里只有那一枚蓝（③ 现数），成因见类注释那段字节码。" +
                "哪天改好，这条要改判而不是删掉：",
            Color(0xFF60CCFE),
            fill,
        )
        assertNotEquals(
            "文字强调那一格同样没吃到（屏上那几处藤紫全是直接吃 `DesktopTheme` 的 token，不是走主题槽）：",
            DesktopTheme.AccentFuji,
            textAccent,
        )
    }

    /**
     * ② 回落不是"藤紫恰好特殊"：换另一枚表里多半没有的色，交回的**还是同一格蓝**。
     *
     * 这一条是上一句的对照组 —— 只量一枚的话，"没生效"与"这一枚正好被映射到默认那格"两种解释打不掉（坑 ⑲ 那一族）。
     */
    @Test
    fun 换一枚没见过的色而那一格还是同一份默认() = runComposeUiTest {
        var first = Color.Unspecified
        var second = Color.Unspecified
        setContent {
            FluentTheme(colors = darkColors(accent = Color(0xFFCBB6FF))) {
                first = FluentTheme.colors.fillAccent.default
            }
            FluentTheme(colors = darkColors(accent = Color(0xFF12AB34))) {
                second = FluentTheme.colors.fillAccent.default
            }
        }
        println("D_主题 两枚没见过的色 fill=" + hex(first) + "/" + hex(second))
        assertEquals(
            "两枚不同的 accent 交回同一格 ⇒ 那一格根本没读递进来的值（回落同一份默认 Shades）：" +
                "若哪天它开始跟着变，说明接法换了，① 那条现状判据要跟着改判：",
            first,
            second,
        )
    }

    /**
     * ③ 表的**内容**单独数一遍：`accentShades` 只有一颗键，而那枚键正是库默认那枚蓝。
     *
     * 为什么这一条要单独立：② 只证明"两枚陌生的色交回同一格"，从它推不出「表里其实有能动的枚、我们没踩上」
     * 还是「这张表从头到尾只有一个值」—— 前者的处置是换一枚色，后者的处置是**必须绕开那条链**，
     * 而 ②① 的读数在这两种解释下同形。表的 size 与键要用它自己的 `containsKey` 现数（新坑 ㊬）。
     * 现数出来 `表大小=1`，唯一那枚键命中 `#0078D4`，我们的藤紫不命中 ⇒ 命中与否拿回的是同一份 Shades。
     */
    @Test
    fun 那张预置表只有一颗键而我们的色不命中() {
        val table = Class.forName("io.github.composefluent.ColorsKt")
            .getDeclaredMethod("getAccentShades")
            .invoke(null) as Map<*, *>
        // 这一版 `Color.toString()` 念的是 RGB 浮点而不是 hex，摆在读数里会被读成"键不是那枚蓝"，
        // 所以只念 size 与两枚 `containsKey` —— 键的身份由下面那两条断言各自钉。
        println(
            "D_主题 表大小=" + table.size +
                " 命中默认蓝=" + table.containsKey(Color(0xFF0078D4)) +
                " 命中藤紫=" + table.containsKey(DesktopTheme.AccentFuji),
        )
        assertEquals(
            "这张表只有一颗键 —— 不止一颗就说明库换脸了，①/④ 那两条现状判据要一起改判：",
            1,
            table.size,
        )
        assertTrue(
            "唯一那枚键就是库默认那枚蓝，它自己的值正是回落的那一份（`getDefaultShades = entries.first().value`）：",
            table.containsKey(Color(0xFF0078D4)),
        )
        assertFalse(
            "我们的藤紫不命中 —— 但这一条**不是**「换一枚色就能修」的依据，④ 量到命中那枚也还是同一格蓝：",
            table.containsKey(DesktopTheme.AccentFuji),
        )
    }

    /**
     * ④ 行为那一端：六枚 accent 各递一遍（含 ③ 里唯一能命中的那枚蓝），那一格**恒**是 `#60CCFE`。
     *
     * 这一条是③ 的对照组 —— 只数表的话，"命中了就跟着变、我们没命中"这一种解释还活着；
     * 现在把命中那枚真递一遍，它交回的还是同一格 ⇒ 两种解释只剩一种。
     */
    @Test
    fun 递进六枚候选而那一格恒是同一枚蓝() = runComposeUiTest {
        val candidates = listOf(
            "我们的藤紫" to 0xFFCBB6FFL,
            "表里唯一那枚蓝" to 0xFF0078D4L,
            "红" to 0xFFC42B1CL,
            "绿" to 0xFF0F7B0FL,
            "黄" to 0xFFFCE100L,
            "棕" to 0xFF9D5D00L,
        )
        val fills = LinkedHashMap<String, Color>()
        setContent {
            candidates.forEach { (name, argb) ->
                FluentTheme(colors = darkColors(accent = Color(argb))) {
                    fills[name] = FluentTheme.colors.fillAccent.default
                }
            }
        }
        println("D_主题 候选逐枚 fill=" + fills.entries.joinToString(" ") { it.key + "=" + hex(it.value) })
        assertEquals(
            "每一枚候选都该量到一个具体的色（缺一枚 = 那一档根本没组合到）：",
            candidates.size,
            fills.size,
        )
        fills.forEach { (name, color) ->
            assertEquals(
                name + " 递进去而 `fillAccent.default` 没跟着变（深色档上这枚参数对该槽恒无效，成因见类注释）：",
                Color(0xFF60CCFE),
                color,
            )
        }
    }

    /**
     * ⑤ 探针（本批新量具的能力边界那一格）：`captureToImage()` 在桌面这条测试路上到底给不给像素。
     *
     * 为什么先立探针再立判据：批 94/95 两批的"色值那一格"都只能靠窗口捕获那条路看图，而那条路今天**不落盘**
     * （`CopyFromScreen` 拍回来的是壁纸，`get_window_state` 只回内联图）⇒ 屏上像素没有可数的读数。
     * 如果离屏渲染能拿到位图，色值这一格就能从"argb 判据 + 人眼看帧"升成"真画出来的那一枚像素"。
     */
    /**
     * ⑤ 量具能力边界那一格（本批现量）：桌面这条测试路上 `captureToImage()` **尺寸对而像素恒 0**。
     *
     * 为什么立这一条：批 94/95 两批"色值那一格"只能靠窗口捕获那条路看图，而那条路今天不落盘
     * （`CopyFromScreen` 拍回来的是壁纸 —— 那张已改名 `_qa/b95_frame_accent_REJECTED_wallpaper.png`；
     * `get_window_state` 只回内联图，没有可采样的 PNG）⇒ 屏上像素没有可数的读数。
     * 本批试了第三条路：离屏渲染拿位图。读数 `宽=40 高=40 采样 0x0=0x0 20x20=0x0 39x39=0x0` ——
     * **对象给得出、尺寸对，而每一个像素都是透明黑**（skiko 在无头测试里没真画）。
     *
     * ⚠️ 这条钉的是"量不到"，不是"色值不对"（色值由 `DesktopCardOverlayTest` 那颗 argb 断言 + ⑩ 那枚接线判据两头的链钉着）。
     * 哪天它**红了**，说明离屏像素可量了 ⇒ 把它**升成**"那枚胶囊画出来就是 `#5B47A8`"的真判据，而不是删掉了事。
     */
    @Test
    fun 离屏渲染给得出尺寸而像素量不到() = runComposeUiTest {
        setContent {
            Box(Modifier.size(40.dp).background(DesktopTheme.AccentFill))
        }
        val map = onRoot().captureToImage().toPixelMap()
        val samples = listOf(0 to 0, 20 to 20, 39 to 39, (map.width - 1) to (map.height - 1))
            .map { (x, y) -> x to (map[x, y].value.toLong() and 0xFFFFFFFFL) }
        val readout = samples.joinToString(" ") { (x, argb) -> "$x:0x" + argb.toULong().toString(16) }
        println("D_主题 像素探针 宽=" + map.width + " 高=" + map.height + " 采样=" + readout)
        assertEquals("对象给得出尺寸（这一格若红而像素仍全 0，说明改的是渲染尺寸那一层）：", 40, map.width)
        assertEquals("对象给得出尺寸：", 40, map.height)
        samples.forEach { (x, argb) ->
            assertEquals(
                "像素 $x 量到了非零值 ⇒ 离屏 readback 在这条路上**可用了**，这一条该升成真判据（不许删）：",
                0L,
                argb,
            )
        }
    }
}
