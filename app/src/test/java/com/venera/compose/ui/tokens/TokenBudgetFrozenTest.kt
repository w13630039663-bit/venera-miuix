package com.venera.compose.ui.tokens

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 令牌存量的**冻结尺子**：`ui/tokens/` 之外的裸 `dp` / `sp` / `Color(0x…)` 只许减、不许增。
 *
 * ── 为什么是"封顶"而不是"清零" ──
 *
 * `Spacing.kt` 的头注从前写着"除本文件外，任何 UI 代码不得出现 `xx.dp`"，而 2026-10-14 现量是
 * **616 处 dp / 133 处 sp / 27 处 Color(0x…)** —— 那句是一条屏上说一套、代码做另一套的旧话，
 * 已经把它改写成"新代码不许再抄、存量已冻结"（见那颗文件的头注）。
 *
 * 一次性迁移等于大重构：几百处取值逐处要判断"这一档对应哪枚语义 token"，
 * 而两套字阶（MD3 / Miuix）下同一枚 token 的数值还不一样 —— 回归风险远大于收益。
 * 所以这把尺子的作用是把**现状钉住**：谁新写一处，谁就红在这里；往下的每一批都可以把
 * 这三个数往下带，带多少算多少。
 *
 * ⚠️ 计数口径必须与立案时一致，否则"只许缩短"会红在口径上而不是红在新代码上：
 * 跳过注释行（双斜杠起头、星号起头、斜杠星号起头三种），并整目录排除 `ui/tokens/`
 * （那才是允许写裸值的地方）。
 */
class TokenBudgetFrozenTest {

    private val spPattern = Regex("""[0-9]+(\.[0-9]+)?\.sp""")
    private val dpPattern = Regex("""[0-9]+(\.[0-9]+)?\.dp""")
    private val colorPattern = Regex("""Color\(0x""")

    private data class Budget(val sp: Int, val dp: Int, val color: Int, val where: List<String>)

    private fun measure(): Budget {
        var sp = 0
        var dp = 0
        var color = 0
        val hits = mutableListOf<String>()
        RepoSources.kotlinFiles("com/venera/compose")
            .filter { it.absolutePath.replace('\\', '/').contains("/ui/tokens/").not() }
            .forEach { file ->
                val body = file.readLines()
                    .map { it.trim() }
                    .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
                    .joinToString("\n")
                val a = spPattern.findAll(body).count()
                val b = dpPattern.findAll(body).count()
                val c = colorPattern.findAll(body).count()
                sp += a
                dp += b
                color += c
                if (a + c > 0) hits += "${file.name} sp=$a color=$c"
            }
        return Budget(sp, dp, color, hits)
    }

    @Test
    fun `裸 sp 存量已封顶只许缩短`() {
        val got = measure().sp
        assertTrue(
            "裸 sp 从 $SP_CEILING 涨到 $got —— 新写的字阶请吃 VeneraTokens.type 那十档之一" +
                "（两套字阶会随 appearance 一起换）。清单：\n" + measure().where.joinToString("\n"),
            got <= SP_CEILING,
        )
    }

    @Test
    fun `裸 dp 存量已封顶只许缩短`() {
        val got = measure().dp
        assertTrue(
            "裸 dp 从 $DP_CEILING 涨到 $got —— 间距/尺寸请吃 VeneraTokens.spacing 那十一档与具名常量",
            got <= DP_CEILING,
        )
    }

    @Test
    fun `写死的颜色存量已封顶只许缩短`() {
        val got = measure().color
        assertTrue(
            "Color(0x…) 从 $COLOR_CEILING 涨到 $got —— 颜色请吃 VeneraTokens.color 的槽位：" +
                "写死的十六进制不跟壁纸取色，换一张深色壁纸就会与整屏脱节" +
                "（悬浮底栏那枚 0xFFFCFCFD 就是这么修的）。清单：\n" + measure().where.joinToString("\n"),
            got <= COLOR_CEILING,
        )
    }

    /** 对照：三个上限本身不许被顺手抬高 —— 只许往下带。 */
    @Test
    fun `冻结的三个数是往下带的而不是往上抬`() {
        assertTrue("sp 上限被抬高了", SP_CEILING <= 133)
        assertTrue("dp 上限被抬高了", DP_CEILING <= 615)
        assertTrue("color 上限被抬高了", COLOR_CEILING <= 27)
    }

    private companion object {
        /**
         * 2026-10-14 手机档整改批 6 落地后现量的存量（此前：sp 134 / color 28 / 那枚写死的近白在底栏）。
         * dp 再往下带一枚是批 6b：顶栏药丸那枚裸 `48.dp` 改吃 `VeneraIconButtonTouchSize`（616 → 615）。
         */
        const val SP_CEILING = 133
        const val DP_CEILING = 615
        const val COLOR_CEILING = 27
    }
}
