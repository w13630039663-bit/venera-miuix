package com.venera.compose.testsupport

import java.io.File

/**
 * **桌面共享面**的读法：`:desktop` 到底从 `:app` 借了哪些目录、又按文件名排除了哪些。
 *
 * 为什么单独一颗：桌面侧的两条不变量（"排除清单与禁引集合双向对得上"、"共享面不许点名 Android SDK"）
 * 都要先回答"哪些文件真的在桌面编译面里"这个问题，而答案写在 `desktop/build.gradle.kts` 的
 * `srcDir` / `exclude` 里。两处各抄一份解析式，迟早有一份漂。
 *
 * ⚠️ 这两条用例放在 **`:app` 的测试**里而不是 `:desktop` 的测试里，是刻意的：
 * 它们要钉的失败形态之一是"`:desktop:compileKotlin` 炸了"，而那次跑之后 `:desktop:test`
 * 根本不会执行 —— 判据就只在最该说话的时候缺席。放在 `:app` 侧，桌面编不过时它照样红、
 * 并且说得出是哪一个文件哪一行。
 *
 * ⚠️ 往这一族文件的 **KDoc** 里写 glob 字面量时必须避开"斜杠紧跟星号"那两字符连写（要提 `:desktop`
 * 排除清单里那颗 android 目录 glob，就写"android 子目录"，别照 gradle 里那串原样抄）：
 * Kotlin 的块注释可以**嵌套**，注释里出现那两字符连写就开了第二层，于是整颗文件从那一行起被吞掉，
 * 读数是一句毫不相关的 `Missing '}'`。本批踩了三次，连这条提醒本身都要避开那个写法。
 */
object DesktopFace {

    private const val GRADLE = "desktop/build.gradle.kts"

    val gradleText: String by lazy {
        File(RepoSources.root, GRADLE).readText().also {
            check(it.isNotBlank()) { "读不到 $GRADLE：${File(RepoSources.root, GRADLE).absolutePath}" }
        }
    }

    /**
     * `srcDir("../app/src/main/java/com/...")` → `app/src/main/java/com/...`（相对仓库根）。
     *
     * ⚠️ 只收 `app/src/main/java/` 起手的：那颗 `resources.srcDir("../app/src/main/assets")`
     * 行形状一模一样，但它不是 Kotlin 源目录，算进来就是一张假账。
     */
    val srcDirs: List<String> by lazy {
        Regex("""srcDir\("\.\./(.+?)"\)""")
            .findAll(gradleText)
            .map { it.groupValues[1] }
            .filter { it.startsWith("app/src/main/java/") }
            .toList()
    }

    /**
     * `exclude(…)` 块里点名的文件名（glob 不收 —— `android` 子目录那一族由 [sharedFiles] 按目录判）。
     *
     * 为什么要走状态机而不是全局正则：`sourceSets["main"].resources.include(…)` 里那些条目的
     * 行形状与排除项**一模一样**（resources 清单里那几串 js 与 init 的 glob），
     * 全局抓会把 include 清单算成排除清单。
     */
    val excludedNames: Set<String> by lazy { excludeEntries().map { it.name }.toSet() }

    /** 排除项连同它在 gradle 里的行号（"每条排除都要说得出成因"那条要按行号回看上面的理由）。 */
    data class ExcludeEntry(val name: String, val lineNo: Int)

    /**
     * 排除清单的条目。**含 glob 的也收**（2026-10-20 改）。
     *
     * 原实现把带 `*` 的条目直接丢掉，理由是"`android` 子目录那一族由 [sharedFiles] 按目录判"。
     * 那条仍然成立，但它把**另一类** glob 一起丢了：`**` 加斜杠再跟文件名那种
     * 「点名某个子目录里的单颗文件」的写法。于是那颗文件在账本里永远算作
     * "该排除而未排除"，而 gradle 那边其实真的排掉了 —— 判据与事实相反。
     * （本仓 2026-10-20 真的踩到了：`VeneraGallerySourceMark.kt` 在 `venera/` 子目录，
     *  裸文件名排不掉它，只能写 glob，而 glob 又被解析器丢掉。）
     *
     * 现在分类记：
     *  - 不含 `*` 的 ⇒ 按裸文件名匹配（与既有口径一致）；
     *  - 含 `*` 的 ⇒ 按 glob 匹配**相对 srcDir 根**的路径（见 [isExcluded]）。
     */
    fun excludeEntries(): List<ExcludeEntry> {
        val out = mutableListOf<ExcludeEntry>()
        var inside = false
        gradleText.lines().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.startsWith("exclude(")) {
                inside = true
                val inline = Regex("""exclude\("([^"]+)"\)""").find(line)
                if (inline != null) {
                    inside = false
                    out += ExcludeEntry(inline.groupValues[1], index + 1)
                    return@forEachIndexed
                }
            }
            if (!inside) return@forEachIndexed
            if (line == ")") {
                inside = false
                return@forEachIndexed
            }
            if (line.isEmpty() || line.startsWith("//")) return@forEachIndexed
            val name = Regex(""""([^"]+)"""").find(line)?.groupValues?.get(1) ?: return@forEachIndexed
            out += ExcludeEntry(name, index + 1)
        }
        check(out.isNotEmpty()) { "没从 $GRADLE 解析出任何排除项 —— 判式失效，不能当作通过" }
        return out
    }

    /** 裸文件名那一档（不含 `*`）。 */
    val excludedFileNames: Set<String> by lazy {
        excludeEntries().map { it.name }.filterNot { it.contains('*') }.toSet()
    }

    /**
     * **文件级** glob 条目（含 `*` 且末尾落在某个 `.kt` 上）。
     *
     * `android` 子目录那一族（斜杠接双星）**不在此列** —— 它由 [candidateFiles] 的
     * `hasAndroidParent()` 按目录判，不是"点名某一颗文件"，所以：
     *  - 放进 [excludedFileNames] 会让"每颗排除都要说得出成因"那条去找一颗名叫
     *    `android` 的文件而永远找不到（假红）；
     *  - 而它确实**是**一条排除项，所以 [excludeEntries] 仍要收它（用于完整性核对）。
     *
     * ⚠️ 本段注释刻意不写那两个字符连写（斜杠紧跟星号）：Kotlin 块注释可嵌套，
     * 写出来就开了第二层，整颗文件从那一行起被吞掉（本仓已踩过三次，见类头注）。
     */
    val excludedFileGlobs: Set<String> by lazy {
        excludeEntries().map { it.name }
            .filter { it.contains('*') && it.endsWith(".kt") }
            .toSet()
    }

    /** 目录级 glob（目前只有 `android` 那一族）。只用于把"目录排除"与"文件排除"分开记。 */
    val excludedDirGlobs: Set<String> by lazy {
        excludeEntries().map { it.name }.filter { it.contains('*') && !it.endsWith(".kt") }.toSet()
    }

    /**
     * 某颗文件是否真的被排除。两道判：
     *  1. 裸文件名命中（无 glob 的条目）—— 与改判前逐值等价；
     *  2. 含 `*` 的条目按 glob 匹配**相对 srcDir 根**的路径。
     */
    fun isExcluded(file: File): Boolean {
        if (file.name in excludedFileNames) return true
        val rel = srcDirs.firstNotNullOfOrNull { dir ->
            file.relativeToOrNull(File(RepoSources.root, dir))?.path?.replace('\\', '/')
        } ?: return false
        return excludeEntries().any { it.name.contains('*') && globMatches(it.name, rel) }
    }

    /** 极简 glob：`**` 加斜杠 ⇒ 任意层级（含零层），`**` ⇒ 任意串，`*` ⇒ 段内任意，`?` ⇒ 单字符。 */
    private fun globMatches(pattern: String, path: String): Boolean {
        val sb = StringBuilder("^")
        var i = 0
        while (i < pattern.length) {
            when {
                pattern.startsWith("**/", i) -> { sb.append("(?:.*/)?"); i += 3 }
                pattern.startsWith("**", i) -> { sb.append(".*"); i += 2 }
                pattern[i] == '*' -> { sb.append("[^/]*"); i++ }
                pattern[i] == '?' -> { sb.append("[^/]"); i++ }
                else -> { sb.append(Regex.escape(pattern[i].toString())); i++ }
            }
        }
        sb.append('$')
        return Regex(sb.toString()).matches(path)
    }

    /**
     * srcDir 下**所有**参与这本账的 `.kt`（被点名排除的那些也在里面）——
     * "清单与禁引双向对得上"与"每颗排除要说得出成因"两条都要按名字回查它，
     * 所以走这一份共享的树遍历，而不是每条用例各走一遍。
     */
    val candidateFiles: List<File> by lazy {
        srcDirs
            .flatMap { rel ->
                val root = File(RepoSources.root, rel)
                check(root.isDirectory) { "srcDir 指向不存在的目录：${root.absolutePath}" }
                root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            }
            .filterNot { it.hasAndroidParent() }
            .sortedBy { it.path }
    }

    /**
     * 真的进了桌面编译面的那些 `:app` 源文件：在 [srcDirs] 之下、不是 `android/` 子目录里的、
     * 且没被排除清单点名的（裸文件名与 glob 两种写法都认，见 [isExcluded]）。
     */
    fun sharedFiles(): List<File> = candidateFiles.filterNot { isExcluded(it) }

    /** 桌面自己的源（`desktop/src/main`）。 */
    fun ownFiles(): List<File> =
        File(RepoSources.root, "desktop/src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList().sortedBy { it.path }
            .also { check(it.isNotEmpty()) { "找不到 desktop/src/main 下的源，判式失效" } }

    /** 相对仓库根的路径，报错时用它——`relativeTo` 在 Windows 上还会留 `\`，统一成正斜杠。 */
    fun relOf(file: File): String =
        file.relativeTo(RepoSources.root).path.replace('\\', '/')

    /** `android/` 子目录（包名等于父目录、目录名叫 android）里的文件不参与这本账。 */
    private fun File.hasAndroidParent(): Boolean =
        absolutePath.replace('\\', '/').contains("/android/")
}
