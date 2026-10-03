package com.venera.desktop.gallery.ui

import java.io.File

/**
 * 只读源码树的位置 —— 两条"文本核对"的用例都靠它，所以它本身必须与工作目录无关。
 *
 * Gradle 跑测试时的工作目录是**模块目录**（`desktop/`），不是仓库根；这里不假设那一点，
 * 而是从当前目录向上找到 `settings.gradle.kts` 所在的那一层。这样 `:desktop:test` 无论
 * 从 worktree 还是从主检出跑、无论工作目录是模块根还是仓库根，读到的都是同一棵树。
 */
internal object DesktopSourceTree {

    val repoRoot: File = run {
        var cursor: File? = File("").absoluteFile
        while (cursor != null) {
            if (File(cursor, "settings.gradle.kts").isFile) return@run cursor
            cursor = cursor.parentFile
        }
        error("没找到仓库根（向上都没有 settings.gradle.kts）：起点 ${File("").absolutePath}")
    }

    /** `desktop/src/main` 下的全部 .kt（隔离与口径漂移两条尺子都扫这一片）。 */
    fun desktopMainSources(): List<File> = walkFiles(File(repoRoot, "desktop/src/main"))

    /** `app/src/main` 下的全部 .kt（反向隔离：Android 侧不许 import 桌面）。 */
    fun androidMainSources(): List<File> = walkFiles(File(repoRoot, "app/src/main"))

    fun androidSource(relative: String): File =
        File(repoRoot, "app/src/main/java/com/venera/compose/$relative").also {
            check(it.isFile) { "读不到 Android 侧的对岸文件：${it.path}" }
        }

    /** 桌面那颗同名文件的绝对路径，用于负向断言（"这颗文件里不许出现某个 token"）。 */
    fun desktopUiSource(name: String): File =
        File(repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/$name").also {
            check(it.isFile) { "读不到桌面 UI 文件：${it.path}" }
        }

    /**
     * **代码行**里某个字面量的出现次数：跳过注释行。
     *
     * 为什么要跳注释：口径账的注释里**必须**能点名对岸那个数（`ExpandedSideBarWidth = 224.dp`），
     * 含进注释就会把"唯一出处"这条尺子读成 2 处，逼着后人把说明删掉 —— 那是把文档当重复计数。
     */
    fun codeOccurrences(file: File, literal: String): Int =
        codeLines(file).sumOf { line -> line.split(literal).size - 1 }

    /**
     * 去掉注释行之后的文件正文。
     *
     * 「这颗件里不许出现某个手势修饰」这类负向断言必须只看代码：口径注释里**本来就该**能点名
     * 那个被禁的 token（比如"连 `clickable` 都没 import"），含进注释就把说明当成了违例。
     */
    fun codeText(file: File): String = codeLines(file).joinToString("\n")

    /**
     * 去注释后的代码行 —— 对外可见（色值扫描器 [DesktopSurfaceLiterals] 复用同一口径，
     * 别处再写一份"怎么算代码行"就会与这里漂）。
     */
    fun codeLinesOf(file: File): List<String> = codeLines(file)

    private fun codeLines(file: File): List<String> =
        file.readText().lines()
            .map { it.trim() }
            .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }

    private fun walkFiles(root: File): List<File> {
        check(root.isDirectory) { "源码目录不存在：${root.path}" }
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }
}
