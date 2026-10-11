package com.venera.desktop.gallery.ui

/**
 * 桌面自有件的**零调用点**扫描器（批 27 的尺子，测试侧工具件 —— 不进产品代码）。
 *
 * ## 这一把管的是什么
 *
 * 手册 §55 那三枚是**人工**量出来的（逐颗 grep）。这一把把它机械化：
 * `desktop/src/main` 里写了 `internal` 的声明，如果**产品代码**里除了声明那一行以外一处都不提它，
 * 它就是"实现了但没接线"。测试代码里的引用**单独打标而不算接线** ——
 * 那正是 `DESKTOP_GALLERY_HOME_SECTION_ORDER` 与 `byKey` 这两颗的形状：判据在替它们作证，屏上没有一处吃它们。
 *
 * ## 三条口径（都是这一族踩过的）
 *
 * - **什么算代码行**与 [DesktopSourceTree.codeLinesOf] 同一把（跳过以双斜杠、星号、或块注释开头的那几行），
 *   但行号按**文件真实行号**报 —— 本批的前置量具第一版把过滤后的下标当行号报出去，差了三十九行。
 *   KDoc 里那句 `[名字]` 引用因此**不算消费者**（坑 ㉛ 那族：说明不是接线）。
 * - `override` 不进这一族：共享接口的实现天生"桌面零调用点"（调用方在 Android 那侧），
 *   把它们报成死件就是本批词典那条误判的重演。`operator` 同理 —— `a + b` 里根本不出现 `plus`。
 * - `private` 交给编译器：未使用的 private 本来就有 warning，这把尺子不重复它。
 *
 * ## 已知的漏报方向（只漏不误红）
 *
 * 与别处同名的声明会被算成"有人用"（`state`、`row` 这类常见词几乎恒漏）；
 * 内部类里没写修饰符的 public 成员不在这一族（要显式写了 `internal` 才进）。
 * ⇒ 这一把保证的是**下界**：新造的、名字独特的 internal 件必须有产品消费者。
 */
internal object DesktopUnusedInternalScan {

    /** 一份待扫的源：文件名（报告用）+ 全文。 */
    internal data class ScanSource(val name: String, val text: String)

    /** 一颗零调用点的声明。[testOnly] = 产品代码没人提它，但测试代码提了。 */
    internal data class Unused(val name: String, val file: String, val line: Int, val testOnly: Boolean) {
        fun describe(): String = "$name @ $file:$line" + if (testOnly) "（仅测试引用）" else ""
    }

    private data class Decl(val name: String, val file: String, val line: Int, val modifiers: String)

    private val MODIFIERS = "(?:internal|private|public|protected|open|abstract|override|suspend|inline|const|operator|infix|tailrec|external|expect|actual|data|sealed|value|annotation)"

    /**
     * 行首（允许缩进）的声明：修饰符+ 关键字 名字。
     *
     * ⚠️ 接收者那一型**可以带自己的泛型参数**（`fun List<Row>.byKey(`）—— 第 3 组后面那枚可选的
     * `<…>` 就是为它写的。少了它，`List<Row>.byKey` 会被读成一颗叫 `List` 的声明，
     * 而 `List` 在 main 里到处都有 ⇒ 那颗 `byKey` 永远不会被报（本批第一版就是这么漏掉它的）。
     */
    private val DECL = Regex(
        """^\s*($MODIFIERS[\s$MODIFIERS]*)\s*(fun|val|var|object|class|interface)\s+(?:<[^>]*>\s*)?([A-Za-z_][A-Za-z0-9_]*)(?:<[^>]*>)?(?:\.([A-Za-z_][A-Za-z0-9_]*))?""",
    )

    private fun isCodeLine(line: String): Boolean {
        val t = line.trim()
        return !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
    }

    private fun declarations(source: ScanSource): List<Decl> =
        source.text.lines().mapIndexed { index, line ->
            val m = DECL.find(line) ?: return@mapIndexed null
            // `fun Receiver.name(` 的名字是最后一个点之后那一段；`fun name(` 时第 4 组为空。
            val name = m.groupValues[4].ifBlank { m.groupValues[3] }
            Decl(name, source.name, index + 1, m.groupValues[1])
        }.filterNotNull()

    /** 名字在代码行里的出现次数；[decl] 那一行（声明自己）不算，`decl` 为 null 时全数。 */
    private fun occurrences(name: String, sources: List<ScanSource>, decl: Decl?): Int {
        val re = Regex("\\b" + Regex.escape(name) + "\\b")
        return sources.sumOf { source ->
            source.text.lines().mapIndexed { index, line -> Pair(index + 1, line) }
                .filter { (_, line) -> isCodeLine(line) }
                .filterNot { (lineNo, _) -> decl != null && source.name == decl.file && lineNo == decl.line }
                .sumOf { (_, line) -> re.findAll(line).count() }
        }
    }

    /**
     * 扫 main：产品代码零调用的 internal 声明，按文件、再按行排。
     *
     * [testSources] 只用来打标 —— 测试里的出现**永远不算接线**，但那一句"仅测试引用"要念得出来，
     * 因为这一族最要紧的形态正是"判据在替一件界面上不存在的事作证"。
     */
    fun unusedIn(mainSources: List<ScanSource>, testSources: List<ScanSource> = emptyList()): List<Unused> =
        mainSources.flatMap(::declarations)
            .filter { Regex("""\binternal\b""").containsMatchIn(it.modifiers) }
            .filterNot { Regex("""\b(override|operator)\b""").containsMatchIn(it.modifiers) }
            .distinctBy { "${it.file}:${it.line}" }
            .filter { occurrences(it.name, mainSources, it) == 0 }
            .map { Unused(it.name, it.file, it.line, occurrences(it.name, testSources, null) > 0) }
            .sortedWith(compareBy({ it.file }, { it.line }))

    /** 真树的取样：main 与 test 各一份，读法同一把（口径别在两处写）。 */
    fun fromTree(): List<Unused> {
        val main = DesktopSourceTree.desktopMainSources().map { ScanSource(it.name, it.readText()) }
        val test = DesktopSourceTree.repoRoot.resolve("desktop/src/test")
            .walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { ScanSource(it.name, it.readText()) }.toList()
        return unusedIn(main, test)
    }
}
