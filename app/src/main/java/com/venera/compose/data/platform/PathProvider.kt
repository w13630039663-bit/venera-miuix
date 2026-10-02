package com.venera.compose.data.platform

import java.io.File

/** 所有落盘路径的唯一来源。桌面侧不许出现 `java.io.tmpdir`，Android 侧不许出现硬编码 `/storage/`。 */
interface PathProvider {
    val dataRoot: File
    val cacheRoot: File
    fun subDir(vararg parts: String): File {
        val dir = parts.fold(dataRoot) { acc, p ->
            require(p.isNotBlank() && p != ".." && !p.contains('/') && !p.contains('\\')) { "路径段不合法：$p" }
            File(acc, p)
        }
        if (!dir.mkdirs() && !dir.isDirectory) throw IllegalStateException("建不出目录 $dir")
        return dir
    }
}
