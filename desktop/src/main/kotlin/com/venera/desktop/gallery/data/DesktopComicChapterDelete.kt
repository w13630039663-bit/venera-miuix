package com.venera.desktop.gallery.data

import java.io.File

/**
 * 批 98 · 「本机已下载」那一屏的**删除**那颗件。
 *
 * ## 这一颗要顶住的是对岸那一笔真缺陷（逐行现量，rounds `desktop-comic-downloads-delete-2026-10-12.md` §1）
 *
 * `app/…/download/LocalComicManager.kt:302` 写的是 `root.deleteRecursively()` —— **返回值丢弃**，下一行 `:304` 恒 `true`。
 * 而 `deleteRecursively` 不抛异常、只返回 false ⇒ 半树删失败、文件被占用、甚至目录压根不存在（`:301` 为假）都说"删掉了"，
 * 调用点（`feature/LocalComicScreen.kt:317-318`）接都不接就 Toast「已删除本地漫画」。
 * 本件因此**不吃 `deleteRecursively` 的返回值**：删之后回读盘，盘上真没了才交回 [DesktopComicDeleteOutcome.Deleted]，
 * 而「本来就没了」与「真删掉了」是两档（并成一档就等于把"没做过"说成"做成了"）。
 *
 * ## 「只删这一屏自己认出的那一目录」是字面实现的
 *
 * 本件不自己解析清单，而是把那一棵树交给批 97 那颗读侧（[desktopComicDownloadedChapters]）**再认一遍**：
 * 认不出来的（没有清单 / 清单与目录名不同轨 / 号与站名对不上）一律 [DesktopComicDeleteOutcome.Rejected]。
 * 于是"删错东西"这一类在结构上就进不到删除那一跳 —— 用户手工放进 `downloads` 的裸目录永远删不掉，那是**故意的**。
 *
 * ## 三条回核，一条都不许省
 *
 * 1. `canonicalPath` 前缀：目标必须落在 `downloads` 根之内（对岸**零**这一道；同目录 `ComicStorageRoot.kt:61-63`
 *    明写着"准入判定里再按 canonical 比一次"，而删除那条路没吃它）。站名键 `..` 不在净化那张字符表里，
 *    拼出来就是父目录 —— 那一档正是这一道要挡的。
 * 2. 读侧认得出这一话（上一节）。
 * 3. 删完回读盘（第一节的理由）。
 *
 * `delete` 那一格是**测试缝**（默认就是 `File.deleteRecursively`）：单测要能造出"删不动"那一档，
 * 而本机真占用文件的形状在别的机器上不一定复现 —— 判据 ⑥ 因此钉的是"还剩几颗、多少字节从盘上现数"，与缝无关。
 */

/** 删那一笔走到哪一档。四档各一句，**不许并**：「本来就没了」与「真删掉了」不是一件事。 */
internal sealed interface DesktopComicDeleteOutcome {
    /** 回读盘确认那一目录不在了。[pages] 与 [freedBytes] 都是**删前从盘上现量**的。 */
    data class Deleted(val pages: Int, val freedBytes: Long) : DesktopComicDeleteOutcome

    /** 盘上本来就没有那一目录（没做过，也就没说成做成了）。 */
    data object Absent : DesktopComicDeleteOutcome

    /** 拒绝动手：目标不在下载根之内，或这一屏认不出它。 */
    data class Rejected(val reason: String) : DesktopComicDeleteOutcome

    /** 删不动：回读盘还在，带**残留**文件数与字节。 */
    data class Failed(val leftFiles: Int, val leftBytes: Long, val reason: String) : DesktopComicDeleteOutcome
}

private val DESKTOP_COMIC_PAGE_NAME = Regex("\\d{4,}\\.jpg")

private fun filesOf(dir: File): List<File> = if (!dir.isDirectory) emptyList() else
    dir.walkTopDown().filter { it.isFile }.toList()

private fun pageCountOf(dir: File): Int = filesOf(dir).count { it.name.matches(DESKTOP_COMIC_PAGE_NAME) && it.length() > 0L }

/**
 * 删掉盘上那一话（连页带清单）。**顺序**：回核 → 认 → 现量 → 动手 → 回读盘。
 *
 * 现量放在动手**之前**：交回给用户看的那两个数（几页、释放多少字节）必须是"刚才在那儿的东西"，
 * 事后从残骸里数会把半删那一档说小。
 */
internal fun desktopComicDeleteDownloadedChapter(
    root: File,
    chapter: DesktopComicDownloadedChapter,
    delete: (File) -> Boolean = { it.deleteRecursively() },
): DesktopComicDeleteOutcome {
    val dir = desktopComicChapterDir(root, chapter.sourceKey, chapter.comicId, chapter.order, chapter.title)
    val rootCanonical = runCatching { root.canonicalPath }.getOrNull()
        ?: return DesktopComicDeleteOutcome.Rejected("读不出下载根的规范路径")
    val dirCanonical = runCatching { dir.canonicalPath }.getOrNull()
        ?: return DesktopComicDeleteOutcome.Rejected("读不出目标目录的规范路径")
    if (dirCanonical != rootCanonical && !dirCanonical.startsWith(rootCanonical + File.separator)) {
        return DesktopComicDeleteOutcome.Rejected("目标不在下载根之内：$dirCanonical")
    }
    if (!dir.exists()) return DesktopComicDeleteOutcome.Absent
    val recognized = desktopComicDownloadedChapters(root).any {
        it.sourceKey == chapter.sourceKey && it.comicId == chapter.comicId &&
            it.order == chapter.order && it.title == chapter.title
    }
    if (!recognized) return DesktopComicDeleteOutcome.Rejected("这一屏认不出那一目录（没有清单，或清单与目录名不同轨）")
    val pages = pageCountOf(dir)
    val freed = filesOf(dir).sumOf { it.length() }
    delete(dir)
    if (!dir.exists()) return DesktopComicDeleteOutcome.Deleted(pages = pages, freedBytes = freed)
    val left = filesOf(dir)
    return DesktopComicDeleteOutcome.Failed(
        leftFiles = left.size,
        leftBytes = left.sumOf { it.length() },
        reason = "回读盘仍在（多半是那几页正被占用）",
    )
}

/** 取证读数那一行：四档各念各的结果值（"没删成"与"没东西可删"要分得开）。 */
internal fun desktopComicDeleteReading(
    chapter: DesktopComicDownloadedChapter,
    outcome: DesktopComicDeleteOutcome,
): String = "D_删除 号=${chapter.comicId} 话=${chapter.order} 结果=" + when (outcome) {
    is DesktopComicDeleteOutcome.Deleted -> "删掉了 释放=${outcome.freedBytes} 页=${outcome.pages}"
    DesktopComicDeleteOutcome.Absent -> "本来就没了 释放=0 页=0"
    is DesktopComicDeleteOutcome.Rejected -> "拒绝 释放=0 页=0 因=${outcome.reason}"
    is DesktopComicDeleteOutcome.Failed -> "删不动 释放=0 页=0 剩=${outcome.leftFiles} 字节=${outcome.leftBytes} 因=${outcome.reason}"
}
