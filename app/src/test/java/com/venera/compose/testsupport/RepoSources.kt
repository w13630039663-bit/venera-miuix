package com.venera.compose.testsupport

import java.io.File

/**
 * 源码扫描类用例的共用底座：定位仓库根、按目录取 `.kt` 源文件。
 *
 * 为什么需要它：有几条不变量只能在**源码层面**钉 —— "这张表里的名字不许再出现内联字面量"、
 * "这条地板值不许再被逐页抄一遍"，编译器与运行时都管不到"新写了一处"这种形态。
 *
 * ⚠️ 用它的用例都必须**找不到源文件就直接 fail 并打印尝试过的路径**，
 * 不许静默跳过 —— 扫描类用例静默通过等于没有用例（gradle 的工作目录在
 * 模块目录与仓库根之间会变，`app/` 与 `<root>` 两种起法都要能跑）。
 */
object RepoSources {

    /** 仓库根（含 `settings.gradle.kts` 的那一层）。 */
    val root: File by lazy {
        var dir: File? = File("").absoluteFile.canonicalFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return@lazy dir
            dir = dir.parentFile
        }
        error(
            "定位不到仓库根：从 ${File("").absolutePath} 向上没有找到 settings.gradle.kts。" +
                "源码扫描用例因此无法运行 —— 这是配置问题，不能当作通过。",
        )
    }

    /** `app/src/main/java` 这一棵树。 */
    val appMain: File by lazy {
        File(root, "app/src/main/java").also {
            check(it.isDirectory) { "找不到源码目录：${it.absolutePath}" }
        }
    }

    /**
     * 相对 `app/src/main/java` 的包目录路径下的所有 `.kt` 文件，按路径排序（用例失败时要能对上号）。
     * 例：[packagePath] = `com/venera/compose/data/platform`
     */
    fun kotlinFiles(packagePath: String): List<File> {
        val dir = File(appMain, packagePath)
        check(dir.isDirectory) { "找不到包目录：${dir.absolutePath}" }
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList().sortedBy { it.path }
    }

    /**
     * 某个包目录（含子目录）下每行的出处：`Triple(相对路径, 行号, 行文本)`。
     * 扫描类用例断言的就是这张表的子集，报错时直接把命中行列出来。
     */
    fun linesOf(packagePath: String): List<Triple<String, Int, String>> {
        val base = appMain.canonicalFile
        return kotlinFiles(packagePath).flatMap { file ->
            val rel = file.relativeTo(base).path.replace('\\', '/')
            file.readLines().mapIndexed { index, line -> Triple(rel, index + 1, line) }
        }
    }

    /** 整棵 `app/src/main/java` 的逐行出处。 */
    fun allMainLines(): List<Triple<String, Int, String>> = linesOf("com")
}
