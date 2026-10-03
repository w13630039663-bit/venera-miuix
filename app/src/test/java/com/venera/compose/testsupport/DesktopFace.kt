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
                    inline.groupValues[1].takeUnless { it.contains('*') }?.let { out += ExcludeEntry(it, index + 1) }
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
            if (!name.contains('*')) out += ExcludeEntry(name, index + 1)
        }
        check(out.isNotEmpty()) { "没从 $GRADLE 解析出任何排除项 —— 判式失效，不能当作通过" }
        return out
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
     * 且没被 [excludedNames] 点名的。
     */
    fun sharedFiles(): List<File> = candidateFiles.filterNot { it.name in excludedNames }

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
