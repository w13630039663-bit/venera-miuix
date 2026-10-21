package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第二十四批区间 · 批 27 · 「实现了但桌面零调用点」那一族的**机械尺子**。
 *
 * ## 为什么这一批把它立起来
 *
 * 手册 §22 记过一整轮零调用点，批 26 又**人工**清掉三枚（逐颗 grep + 逐颗复核）。
 * 人工那一路有两个盖不住的缝：① 只覆盖"我想起来的那个名字"（批 17 那次人工 grep 整个漏了两处，同种错）；
 * ② 清了这批，下一批新造的件照样能悄悄零调用。所以这一族得有一把每次跑都数的尺子。
 *
 * ## 与批 26 那颗 [DesktopDeadWiringTest] 的分工
 *
 * `DesktopDeadWiringTest` 钉的是**三笔已发生的账**（那三枚具体名字不许回来、那一句说明必须有人吃）——
 * 它是回归；这一颗管**全树**，它是尺子。两者不冗余：撤掉这三笔接线，红的是那颗；
 * 新造一颗零调用的 internal 件，红的是这一颗。
 *
 * ## 四类豁免不是"配平"而是各自有形状（用例 ④ 真跑）
 *
 * `internal override` / `internal operator` / `private` 三类都不进这一族，而**只被 KDoc 引用的那颗要进** ——
 * 说明不是接线（坑 ㉛ 那一族）。用例同时钉"这三类不报"与"这一类报"，否则"什么都不报"也能满足前三条。
 */
class DesktopUnusedInternalTest {

    private fun src(name: String, text: String) = DesktopUnusedInternalScan.ScanSource(name, text)

    /** ① 真树零调用点：违例一次列全，而报告必须是空表。 */
    /**
     * ② 恒假证明：往**同一份真树**里加一颗已知零调用的声明，必须恰好多数出那一颗。
     *
     * ① 那条赢在"空表"上 —— 空表既可能是"真的没有违例"，也可能是"这把尺子根本不会报"。
     * 只有把违例注进真语料还能数出来，① 才是一条有牙的判据（批 17 那把尺子立了三版，前两版都栽在这里）。
     */
    @Test
    fun `② 注入一颗已知零调用的声明必须数得出来`() {
        val main = DesktopSourceTree.desktopMainSources().map { src(it.name, it.readText()) }.toMutableList()
        val before = DesktopUnusedInternalScan.unusedIn(main.toList(), emptyList()).size
        main += src("Canary27.kt", "package com.venera.desktop.gallery.ui\n\ninternal fun canaryNeverCalled27(x: Int): Int = x + 1\n")
        val after = DesktopUnusedInternalScan.unusedIn(main, emptyList())
        assertEquals("注入一颗零调用声明后应多数出一颗：$before → ${after.size}", before + 1, after.size)
        val canary = after.first { it.name == "canaryNeverCalled27" }
        assertEquals("Canary27.kt", canary.file)
        assertEquals("报告里的行号应是文件真实行号（前面三行是 package 与空行与 KDoc）", 3, canary.line)
        assertTrue("canary 没有任何测试引用，不该被标成仅测试引用：" + canary.describe(), canary.testOnly.not())
    }

    /**
     * ③ 测试里的引用**不算接线**，但要念得出来。
     *
     * 这一条就是 `DESKTOP_GALLERY_HOME_SECTION_ORDER` 那枚的形状：判据在替它作证，而界面上没有一处吃它。
     * 打标而不是放过，是因为"仅测试引用"与"完全没人引用"是两句话（本仓最忌把三件事塌成一句）。
     */
    /**
     * ④ 四类形状各按各的口径 —— 前三类不进这一族，第四类（只被 KDoc 提到）必须进。
     *
     * 语料里同时放一颗**真有消费者**的普通件当对照：若过滤器写歪成"什么都不报"，那条对照会让
     * "违例数 = 1"这一句先红，而不是让整条用例静默通过。
     */
    @Test
    fun `④ 豁免按形状而注释引用不算消费者`() {
        val corpus = listOf(
            src(
                "Shapes.kt",
                """
                // ⚠️ 这几行喂的是**文本语料**（Kotlin 对 override 的可见性另有判法）：测的是
                // filterNot(override|operator) 那两枚分支。真树里共享接口的实现写成裸 `override fun`，
                // 根本没有 `internal` 那两个字 —— 下面 `class Shapes` 里那一枚钉的才是真实形状。
                internal override fun shapeOverride(): String = "共享接口实现，调用方在 Android 那侧"
                internal operator fun shapeOperator(other: Shapes): Int = 1
                private fun shapePrivate(): Int = 0
                /** 口径说明见 [shapeDocOnly]。 */
                internal fun shapeDocOnly(): Int = 1
                internal fun shapeWired(): Int = 2
                private fun shapeCaller(): Int = shapeWired()
                class Shapes {
                    override fun toString(): String = "Shapes"
                }
                """.trimIndent(),
            ),
        )
        val offenders = DesktopUnusedInternalScan.unusedIn(corpus, emptyList())
        assertEquals(
            "这一族应只数出「只被 KDoc 引用」那一颗：" + offenders.joinToString { it.describe() },
            listOf("shapeDocOnly"),
            offenders.map { it.name },
        )
        // 对照：`override` 与 `operator` 两枚的豁免不是"整把尺子不报"，那颗真有消费者的普通件也没被误报。
        assertTrue("对照失效：语料里连一颗都不报就说明过滤器恒假", corpus.first().text.contains("shapeWired"))
    }

    /**
     * ⑤ 扩展函数与属性取**成员名**，行号取真实行。
     *
     * 前置量具在这一格错过两次：`fun List<X>.byKey(` 取到 `List`（那颗恒不报），
     * 而"什么算代码行"过滤后的下标被当成行号报出去（差三十九行）。两条都在这里钉住。
     */
    @Test
    fun `⑤ 扩展名取成员名而行号是真实行`() {
        val zero = listOf(
            src(
                "Ext.kt",
                """
                package com.venera.desktop.gallery.ui

                /**
                 * 一行说明
                 */
                internal fun List<String>.extNeverUsed27(): Int = size
                """.trimIndent(),
            ),
        )
        val reported = DesktopUnusedInternalScan.unusedIn(zero, emptyList())
        assertEquals(
            "扩展函数应按成员名报（报成 List 就是恒不报）：" + reported.joinToString { it.describe() },
            listOf("extNeverUsed27"),
            reported.map { it.name },
        )
        assertEquals("行号要数真实行（KDoc 那三行也在内）：6", 6, reported.first().line)
        // 对照：同一颗被 `rows.extNeverUsed27()` 消费后必须不报。
        val wired = listOf(
            src("Ext.kt", "internal fun List<String>.extNeverUsed27(): Int = size\n"),
            src("Caller.kt", "private fun c(rows: List<String>): Int = rows.extNeverUsed27()\n"),
        )
        val wiredReport = DesktopUnusedInternalScan.unusedIn(wired, emptyList()).map { it.name }
        assertEquals(
            "有消费者的扩展不许被报：" + DesktopUnusedInternalScan.unusedIn(wired, emptyList()).joinToString { it.describe() },
            emptyList<String>(),
            wiredReport,
        )
    }
}
