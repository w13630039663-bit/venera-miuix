package com.venera.compose.data.platform

import java.io.File

/**
 * `cacheDir` 下那几个**要经 `FileProvider` 暴露给外部应用**的目录名 —— 单一出处。
 *
 * 为什么这三枚名字必须集中，而且必须与 `res/xml/file_paths.xml` 成对：
 * `FileProvider` 只认那张 XML 表里配过的根。代码把文件写进一个表里没有的目录，
 * 写入侧**完全成功**（`File.outputStream()` 不知道有 FileProvider 这回事），
 * 到 `getUriForFile()` 才抛 `IllegalArgumentException: Failed to find configured root`。
 * 这正好是最坏的一种形状：
 * - 编译期连不成一条线（一侧是 Kotlin、一侧是 XML）；
 * - 只在真机、只在点分享那一刻出现；
 * - 而历史上的两个调用点还把它 `catch (_: Exception) {}` 吞了 ——
 *   即 `file_paths.xml` 顶部记的 G-2：「导出成功」的 Toast 弹了、分享面板从没出现过。
 *
 * 所以：名字在这里，XML 那一行由
 * `data/platform/CacheDirsFileProviderContractTest` 读 XML 逐字对账（三条全覆盖，
 * 少对一条就是重演 G-2 的另外两行）。
 *
 * 落点选 `data/platform/` 而不是别处：
 * - 不放 `download/ComicStorageRoot` —— 画廊侧已经欠它一条 `GallerySaver.kt:11`，不再加一条；
 * - 不放 `feature/` 或 `reader/` —— 画廊侧不许 import 漫画侧；
 * - 不放 `components/` —— 那是 UI 层，而这里要落的是 `cacheDir` 路径。
 * `data/platform/` 是两侧本来就都 import 的中性基建，且本文件零 `android.*`
 * （整个目录在桌面 srcDir 编译面里，加了平台类型会直接把桌面构建打挂）。
 */
object CacheDirs {

    /** 阅读器分享单页、画廊分享原图/原片。 */
    const val SHARED_IMAGES = "shared_images"

    /** 本地漫画导出 CBZ 后分享（`feature/LocalComicScreen`）。 */
    const val EXPORTS = "exports"

    /** 备份导出后分享（`sync/SyncBackupScreen` 走 `BackupManager`）。 */
    const val BACKUPS = "backups"

    /** 取（并按需创建）`cacheDir` 下的那个分享目录。传 `context.cacheDir` 与本页要用的名字。 */
    fun inCache(cacheRoot: File, name: String): File = File(cacheRoot, name).apply { if (!exists()) mkdirs() }
}
