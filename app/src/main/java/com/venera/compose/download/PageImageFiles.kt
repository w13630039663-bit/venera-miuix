package com.venera.compose.download

import java.io.File

/**
 * 一页漫画能用的扩展名 —— **唯一出处**。
 *
 * 为什么要收成一处：这个白名单原本在四个地方各抄了一份
 * （`LocalComicManager` 的两处扫描、`DownloadManager` 的"下完了没"与"读离线页"），
 * 而**导入侧**另写一份更宽的（多收 `.jpeg`）。两边不一致的后果是确定性的：
 * 一本纯 JPEG 的书导入报成功、写成 `0001.jpeg`，扫描侧不认这个扩展名 →
 * 书架上根本没有这本书，也不会有任何报错。
 *
 * ⚠️ 刻意**没有**加 `gif` / `jpe`：导入侧也不收它们，加了只是把"静默丢页"的口子
 * 从"扩展名不一致"换成"两边都丢"。要收就得导入与扫描同时改，并先量一下现有 CBZ 里
 * 到底有没有这两种页 —— 那是另一件事，不混在本次修复里。
 */
internal val pageImageExtensions: Set<String> = setOf("jpg", "jpeg", "png", "webp")

/**
 * 这个文件名是不是一页图。大小写不敏感（真机见过 `0001.JPG`）。
 *
 * 只判名字，不判"存不存在、空不空" —— 那两条要碰文件系统，留在调用点上，
 * 好让这个函数能当纯函数被单测钉住。
 */
internal fun isPageImage(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in pageImageExtensions

/** 磁盘上一个真实存在、非空、且扩展名可当一页图的的文件。 */
internal fun File.isPageImageFile(): Boolean = isFile && length() > 0 && isPageImage(name)

/**
 * 导入落盘时统一用的扩展名：`jpeg` 归一成 `jpg`。
 *
 * 归一而不是"两边都收 jpeg"，是因为文件名要参与 `sortedBy { name }` 的页序 ——
 * 同一本书里混着 `.jpg` 与 `.jpeg` 两种后缀，将来任何按后缀分支的代码都是隐患。
 */
internal fun pageImageExtensionOf(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return if (ext == "jpeg") "jpg" else ext
}
