package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第四十八批 批 48 · **先修量具**：应用根自己念一行"此刻落在哪一屏"。
 *
 * 起因是批 47 那三帧：`-PstartDomain=SETTINGS` 两次都没落在「设置」，而 stdout 只念了
 * 「认成「设置」」—— 那句是**参数判读**，不是**落点读数**。于是"到没到那一域"只剩像素一条路，
 * 而像素要人判（手册 §76.2 记着：已排除"盘上持久状态"与"构建脚本写死"两条，成因不许写）。
 *
 * 这一族病本仓不是第一次撞：`D_` 念过 ≠ 屏上动了（批 36），反过来"屏上动了"也没有谁在念。
 * 所以这一行读数**必须从状态现取**：`domain` / `selectedRow` / `selected` 三枚，
 * 而不是把 flag 再念一遍 —— 从 flag 回读等于拿请求当结果，那正是本仓忌的那一句。
 */
class DesktopLandingReadoutTest {

    private val app = DesktopSourceTree.desktopSourceOf("gallery/ui/VeneraDesktopApp.kt")
    private val code = DesktopSourceTree.codeText(app)

    /** ① 那一行读数在，而且念的是**状态**三枚，不是参数。 */
    @Test
    fun 落点读数念的是状态而不是请求() {
        assertEquals("落点读数只许一行（两行会各说一句）", 1, codeOccurrences("D_落点"))
        val line = code.lines().firstOrNull { it.contains("D_落点") }.orEmpty()
        listOf(
            "domain." to "域那一枚没从状态现取（那这句就是在复述请求）",
            "selectedRow." to "行那一枚没从状态现取（域对了而行没对，屏上仍是别的东西）",
            "selected" to "浮层开没开没从状态现取（那一层是覆盖层，域对了它也可能没在）",
        ).forEach { (token, why) ->
            assertTrue("$why（那一行里没有「${token.trimEnd('.')}」：$line）", line.contains(token))
        }
        // 反面对照：从 flag 回读就是拿请求当结果 —— 这一行不许出现那两枚参数名
        listOf("startDomain", "flagDomain", "startSection").forEach { token ->
            assertFalse("落点读数从请求回读了「$token」：$line", line.contains(token))
        }
    }

    /** ② 读数住在 effect 里，不许在组合期发（组合期 println 会随重组重复念，念出来的是一堆同句）。 */
    @Test
    fun 落点读数住在效应里而不在组合期() {
        val at = code.indexOf("D_落点")
        assertTrue("没有落点读数这一行", at >= 0)
        val before = code.substring(0, at)
        val opens = Regex("LaunchedEffect\\(").findAll(before).count()
        val closes = Regex("\n\\s{0,20}\\}\\n").findAll(before).count()
        assertTrue("落点读数不在 LaunchedEffect 里面（组合期 println 会随重组重复念）：LaunchedEffect 出现 $opens 次", opens >= 1)
        // 钥匙必须是那三枚状态本身：钥匙写 `Unit` 的话换域不再念，读数就停在第一帧那一句
        val keyLine = before.substring(before.lastIndexOf("LaunchedEffect("))
        listOf("domain", "selected").forEach { token ->
            assertTrue("那颗效应的钥匙里没有「$token」（换域或开关浮层就不会重念）：$keyLine", keyLine.contains(token))
        }
    }

    /** ③ 那一行必须把"开/关"两档都念得出来（浮层那一档只写"开"的话，关着时这句就没读数）。 */
    @Test
    fun 浮层那一档两态都念得出来() {
        val line = code.lines().firstOrNull { it.contains("D_落点") }.orEmpty()
        assertTrue("浮层那一档缺「开」：$line", line.contains("开"))
        assertTrue("浮层那一档缺「关」（只念一态 = 关着时没有读数）：$line", line.contains("关"))
    }

    private fun codeOccurrences(literal: String): Int = DesktopSourceTree.codeOccurrences(app, literal)
}
