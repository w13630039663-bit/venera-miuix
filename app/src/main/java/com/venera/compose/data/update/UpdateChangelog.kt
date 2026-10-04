package com.venera.compose.data.update

/**
 * 发布日志（GitHub 手写的 Markdown）→ 一组**只含四种形状**的块，UI 侧按块摆，不用再碰 Markdown。
 *
 * ## 为什么不原样贴
 *
 * 本包 v2.0.3 那份 body 实测 3070 字符，用到的形状就六类：`#`/`##` 标题、`-` 列表、`|` 三列表格、
 * `>` 引用块、`**粗体**`（24 处）、行内反引号（12 处），另有 2 条链接和 1 段围栏代码。
 * 原样贴进界面，用户看到的是 `# venera-miuix v2.0.3` 和一格一竖线 —— 那是源码不是日志。
 *
 * ## 为什么自己写这几十个字符的解析而不引库
 *
 * 引 Markdown 库要么带 Android 依赖（`markwon`）要么带一整条渲染栈，而这里要的只是
 * "去掉记号、认出块"。判据也全部可脱机钉住，不需要 Robolectric。
 *
 * ## 明确不做的
 *
 * 表格**降成一行**（各列用「 — 」连），不做真表格 —— 那三列在窄屏上必然挤，而挤出来的
 * 比丢掉的更多信息。围栏代码**保留原字**、只把两侧的 ``` 那两行去掉。
 * 加粗/行内码**只剥记号不做字形变化**：不为日志单独再造一套字阶（口径见方案文档 §0.7 那条"不造新数"）。
 */
internal sealed interface ChangelogBlock {
    /** [level] 夹在 1..3；UI 用它选一档现成字号。 */
    data class Heading(val text: String, val level: Int) : ChangelogBlock
    data class Bullet(val text: String) : ChangelogBlock
    data class Quote(val text: String) : ChangelogBlock
    data class Paragraph(val text: String) : ChangelogBlock
}

/**
 * 逐行扫描。连续的同前缀行**并成一块**（Markdown 的 soft-wrap 语义，GitHub 自己也是这么渲的），
 * 空行、以及任何换了前缀的行都断开。围栏内不解析、不剥记号。
 */
internal fun changelogBlocks(markdown: String): List<ChangelogBlock> {
    val out = mutableListOf<ChangelogBlock>()
    val run = mutableListOf<String>()
    var runKind: RunKind? = null

    fun flush() {
        if (runKind != null && run.isNotEmpty()) {
            // 围栏里的行按原字保留换行；正文的 soft-wrap 才并成一句。
            val text = if (runKind == RunKind.LITERAL) run.joinToString("\n") else run.joinToString(" ")
            when (runKind) {
                RunKind.PARAGRAPH -> out += ChangelogBlock.Paragraph(text)
                RunKind.QUOTE -> out += ChangelogBlock.Quote(text)
                RunKind.LITERAL -> out += ChangelogBlock.Paragraph(text)
                null -> Unit
            }
        }
        run.clear()
        runKind = null
    }

    var inFence = false
    for (raw in markdown.replace("\r\n", "\n").split("\n")) {
        val line = raw.trimEnd()
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            // 围栏本身不进正文；里面的行按原字留着，不剥记号也不并句。
            flush()
            inFence = !inFence
            continue
        }
        if (trimmed.isEmpty()) {
            flush()
            continue
        }
        if (inFence) {
            run += trimmed
            runKind = RunKind.LITERAL
            continue
        }

        if (trimmed.all { it in MARKER_CHARS }) {
            // 只剩记号的行不进正文：`---` / `***` 这类分隔线、`##`（只有井号没有标题）、孤立的 `-`。
            // 它们既打断上下文，又不该在界面上留下一行没人看得懂的符号。
            flush()
            continue
        }
        val heading = HEADING.matchEntire(trimmed)
        if (heading != null) {
            flush()
            val level = heading.groupValues[1].length
            val text = markdownText(heading.groupValues[2])
            if (text.isNotEmpty()) out += ChangelogBlock.Heading(text, level.coerceAtMost(3))
            continue
        }
        if (BULLET.matches(trimmed)) {
            flush()
            val text = markdownText(trimmed.substring(2).trim())
            if (text.isNotEmpty()) out += ChangelogBlock.Bullet(text)
            continue
        }
        if (trimmed.startsWith("|")) {
            flush()
            tableRow(trimmed)?.let { cells ->
                if (cells.isNotEmpty()) out += ChangelogBlock.Paragraph(cells.joinToString(" — "))
            }
            continue
        }
        if (trimmed.startsWith(">")) {
            val text = markdownText(trimmed.drop(1).trim())
            if (text.isEmpty()) {
                flush()
            } else {
                if (runKind != RunKind.QUOTE) flush()
                run += text
                runKind = RunKind.QUOTE
            }
            continue
        }
        // 普通段落行。
        val text = markdownText(trimmed)
        if (text.isEmpty()) {
            flush()
        } else {
            if (runKind != RunKind.PARAGRAPH) flush()
            run += text
            runKind = RunKind.PARAGRAPH
        }
    }
    flush()
    return out
}

private enum class RunKind { PARAGRAPH, QUOTE, LITERAL }

/**
 * 表格一行 → 各列文本；分隔行（`| --- | --- |`）返回 null 表示"这行不进正文"。
 *
 * 首尾各有一个空串（`|` 之前的半截与最后的半截），靠 `dropLast(1)` 与 `drop(1)` 削掉 ——
 * 所以这里对"以 `|` 开头且以 `|` 结尾"是有前提的；GitHub 的表格恒如此，
 * 不成立时（手工拼的半截行）`cells.all { separator }` 也只会把它降成一行文字，不会抛。
 */
private fun tableRow(line: String): List<String>? {
    val cells = line.split('|').let { if (it.size >= 2 && it.first().isBlank() && it.last().isBlank()) it.drop(1).dropLast(1) else it }
        .map { markdownText(it.trim()) }
    if (cells.all { it.isEmpty() || it.all { c -> c == '-' || c == ':' } }) return null
    return cells.filter { it.isNotEmpty() }
}

/** 剥掉行内记号。顺序有意：先链接再粗体再行内码，避免 `[**x**](y)` 这类嵌套被剥花。 */
private fun markdownText(input: String): String = input
    .replace(LINK, "$1 ($2)")
    .replace(BOLD, "$1")
    .replace(ITALIC, "$1")
    .replace(CODE, "$1")
    .trim()

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET = Regex("^[-*]\\s+\\S.*")

/** 只有这些字符的行，剥完记号就没有内容。`|` 不在其中 —— 表格行有它自己的分支与判式。 */
private val MARKER_CHARS = setOf('#', '*', '-', '_', ':', ' ', '\t')
private val LINK = Regex("\\[([^]]*)]\\(([^)]*)\\)")
private val BOLD = Regex("\\*\\*([^*]+)\\*\\*")
private val ITALIC = Regex("(?<!\\*)\\*([^*\\s][^*]*)\\*(?!\\*)")
private val CODE = Regex("`([^`]*)`")
