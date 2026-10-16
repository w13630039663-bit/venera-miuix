package com.venera.desktop

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第一百零七批 · 四颗取证种子的号段吃不下 slug 号。
 *
 * ## 这一批治的是哪一条（包内读数，不是推测）
 *
 * 批 106 那一趟 `--comic=/manga/pandajunheleisajun-hiyaku --detail` 念出的是
 * `D_落点 … 详情=开(启动) 种子=detail+comic` 而紧跟着 `D_详情 源=goda 号=C:` ——
 * **递进去的号与屏上念出的号不是同一串**。成因读到行级：那四颗判读切的是**第一个**裸斜杠
 * （`ForensicFlags.kt:568/590/659/765`），而 goda / nhentai 那一族的漫画号自己就以 `/manga/` 开头 ⇒ 号段恒空。
 *
 * ## 定档：先按裸斜杠切，再把每一段百分号解码
 *
 * 号里要放斜杠就写 `%2F`。数字号那一族串里没有 `%` ⇒ 一字不变（② 里那一枚"向后兼容"的断言钉的就是这件事）。
 * ⚠️ `%25` 必须**最后**解：先解就会把 `%252F` 解成 `%2F` 再解成斜杠 —— 那是"字面的 %2F"被读成"分隔符"，
 * 而这一档只有 ④ 那一枚样例咬得住（"两两不同"那类判式会被自己另一格喂饱，本仓踩过）。
 *
 * ## 为什么 `--artist` 与 `--ranking` 明列不改
 *
 * 那两颗的第一段装的是站点 routeKey 与枚举名，**不是漫画号**（同名字段≠同语义那一族）；
 * ⑤ 那两枚负向断言钉的就是"别把 A 站的形状发给 B 站"。
 */
class DesktopForensicSeedSlugIdsTest {

    private fun code(): String =
        DesktopSourceTree.codeText(DesktopSourceTree.desktopMainSources().first { it.name == "ForensicFlags.kt" })

    private fun occurrences(hay: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var n = 0
        var at = hay.indexOf(needle)
        while (at != -1) {
            n += 1
            at = hay.indexOf(needle, at + needle.length)
        }
        return n
    }

    /** ① `--comic`：编码过的 slug 号解回原串，而数字号那一形一字不变。 */
    @Test
    fun 详情种子的号段认得斜杠号() {
        val slug = ForensicFlags.comic(listOf("--comic=%2Fmanga%2Fpandajunheleisajun-hiyaku"))
        assertNotNull("编码号必须被认下来（旧那一形在这里回 null：号段被切成了空串）", slug)
        assertEquals("解完之后必须与递进去的逐字相同（这一格就是包内 `号=C:` 被抓到的那一条）：",
            "/manga/pandajunheleisajun-hiyaku", slug!!.comicId)
        assertEquals("没给题名时按既有规矩用号充题名（不许拿斜杠切出来的那半截当题名）：",
            "/manga/pandajunheleisajun-hiyaku", slug.title)
        val numeric = ForensicFlags.comic(listOf("--comic=1461316"))
        assertEquals("数字号那一族一字不变：" , "1461316", numeric!!.comicId)
        val numericTitled = ForensicFlags.comic(listOf("--comic=1461316/某一本"))
        assertEquals("号与题名之间那道裸斜杠照旧生效：" , "1461316", numericTitled!!.comicId)
        assertEquals("题名那一截：" , "某一本", numericTitled.title)
    }

    /** ② `--download` 与 `--delete`：号段带编码斜杠而话号段是裸的那一形。 */
    @Test
    fun 落盘与删除种子的号段认得斜杠号() {
        assertEquals("download 的号段：", "/manga/abc", ForensicFlags.download(listOf("--download=%2Fmanga%2Fabc/1686882001"))!!.first)
        assertEquals("download 的话号段：", "1686882001", ForensicFlags.download(listOf("--download=%2Fmanga%2Fabc/1686882001"))!!.second)
        assertEquals("delete 的号段：", "/manga/abc", ForensicFlags.delete(listOf("--delete=%2Fmanga%2Fabc/1686882001"))!!.first)
        assertEquals("向后兼容（数字号那一形不许被解码改动）：",
            "1461316" to "1461318", ForensicFlags.download(listOf("--download=1461316/1461318")))
        assertNull("旧的那一档仍然要念得出「不是两截」：话号段为空", ForensicFlags.download(listOf("--download=%2Fmanga%2Fabc/")))
    }

    /** ③ `--read`：三段形状里号可编码，而**章名那一截仍然允许带裸斜杠**。 */
    @Test
    fun 阅读种子的三段形状() {
        val reading = ForensicFlags.read(listOf("--read=%2Fmanga%2Fabc/1/第 3 话/特别篇"), startPage = null)
        assertNotNull("三段形状必须被认下来", reading)
        val shape = reading.toString()
        assertTrue("号段解回斜杠号（读的是那颗 data class 的 toString，字段名跟着它走）：" + shape,
            shape.contains("comicId=/manga/abc"))
        assertTrue("话号那一格：" + shape, shape.contains("episodeId=1"))
        assertTrue("章名里那道裸斜杠是**内容**不是分隔符（`drop(2).joinToString(\"/\")` 那一行不许被顺手改掉）：" + shape,
            shape.contains("第 3 话/特别篇"))
    }

    /** ④ 解码次序：`%252F` 解出来是字面的 `%2F`，不是斜杠。 */
    @Test
    fun 百分号本身要最后才解() {
        val target = ForensicFlags.comic(listOf("--comic=%252Fmanga"))
        assertEquals("先解 `%25` 再解 `%2F` 就会把这一串解成 `/manga` —— 那是把字面当分隔符：" ,
            "%2Fmanga", target!!.comicId)
    }

    /** ⑤ 越界负向：那两颗第一段不是号的判读没吃到这颗助手。 */
    @Test
    fun 站点与榜单那两颗不许跟着改() {
        val code = code()
        // 切段要避开那颗助手本身：它就长在 `fun artist(` 与 `fun comic(` 之间，
        // 拿 `fun comic(` 当右界会把助手的定义行算进 artist 那一格（判式咬到自己，本仓那一族第六次）。
        val artist = code.substringAfter("fun artist(").substringBefore("private fun desktopForensicSegment")
        assertEquals("`--artist` 的第一段是站点 routeKey，不是漫画号：", 0, occurrences(artist, "desktopForensicSegment("))
        val ranking = code.substringAfter("fun ranking(")
        assertEquals("`--ranking` 的第一段是枚举名，不是漫画号：", 0, occurrences(ranking, "desktopForensicSegment("))
        // 第一百一十一批：`--repair=` 是第五颗吃号段的判读（同一族：号里带斜杠就得写 `%2F`）⇒ 五处 + 定义 = 六处。
        assertEquals("五颗判读各接一跳，加上那颗助手自己的定义共六处：", 6, occurrences(code, "desktopForensicSegment("))
    }

    /** ⑥ 提示那几句要跟着改准：每颗都念得出 `%2F` 那一档（参数对了而提示还在教错的，就是没改完）。 */
    @Test
    fun 四句提示都念得出编码那一档() {
        val code = code()
        assertEquals("五颗的「要写成 …」各念一次这一档：", 5, occurrences(code, "号里要放斜杠就写 %2F"))
    }
}
