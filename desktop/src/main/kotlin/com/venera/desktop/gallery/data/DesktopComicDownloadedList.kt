package com.venera.desktop.gallery.data

import java.io.File

/**
 * 批 97 · 「本机已下载的那一屏」的**读侧**：数盘上那一棵树，而不是再拼一次名字。
 *
 * ## 这一格补的是哪条缺口
 *
 * 批 96 把整话写进 `数据根/downloads/<站名键>/<号>/<话目录>/{NNNN.jpg, chapter.json}` 并在交付物里量到
 * 89 颗 / 24,905,629 B —— 而**那一刻**桌面还没有一处读这棵树（`desktopComicChapterDir(` 的调用点全在写那一侧）。
 * 于是用户按了长按、盘上多了 24 MB 而看不见 —— **本文件就是补上的那一格**（第九十七批），
 * 所以下面写的都是"批 96 收工时缺的那一屏"，不是今天的状态（今天它接在漫画域「本机已下载」那一行）。
 *
 * ## 为什么"齐不齐"那一判要吃盘上
 *
 * 对岸两颗读侧本身就不同口径：`DownloadManager.stateOfChapterDir` 吃 `expectedPages` + `complete` + `listFiles` **计数**，
 * 而 `LocalComicManager` 只看有没有图（`images.isNotEmpty()`）。桌面沿批 96 立的那一档并把它做硬：
 * **清单说 `complete:true` 且逐页查盘**（`desktopComicChapterIsComplete` 那一颗，两处共用而不是各写一份），
 * 另数一份"盘上非空的那几页"给屏上说缺页。屏上那一句是用户据以删文件的话，**宁可念少不许念多**。
 *
 * ## 三条不静默
 *
 * 清单读不出、目录名与清单里的 `order`+`title` 拼不出同串、目录里没有清单 —— 三档都不算"已下载"，但都进 `…Skipped` 那一格计数
 * （静默跳过就是"下了而不见了"，而那正是这一屏要治的病）。
 */

/** 盘上一条已下载的整话。`writtenPages` 与 `bytes` 都是**从盘上数出来的**。 */
internal data class DesktopComicDownloadedChapter(
    val sourceKey: String,
    val comicId: String,
    /** 源站那一枚**话号**（清单里那一格，对岸按它互认整棵目录树；行号会漂而它不会）。 */
    val chapterId: String,
    val order: Int,
    val title: String,
    val expectedPages: Int,
    val writtenPages: Int,
    val bytes: Long,
    val complete: Boolean,
)

/** 同一本的一格。`label` 念的是**号**：盘上没有本子的题名（清单存的是话的题名），造一个就是假话。 */
internal data class DesktopComicDownloadedGroup(
    val comicId: String,
    val sourceKey: String,
    val chapters: List<DesktopComicDownloadedChapter>,
) {
    val label: String get() = comicId
    val completeCount: Int get() = chapters.count { it.complete }
    val pageCount: Int get() = chapters.sumOf { it.writtenPages }
    val bytes: Long get() = chapters.sumOf { it.bytes }
}

private val FIELD_CHAPTER_ID = Regex("\"chapterId\"\\s*:\\s*\"([^\"]*)\"")
private val FIELD_TITLE = Regex("\"title\"\\s*:\\s*\"([^\"]*)\"")
private val FIELD_ORDER = Regex("\"order\"\\s*:\\s*(-?\\d+)")
private val FIELD_EXPECTED = Regex("\"expectedPages\"\\s*:\\s*(-?\\d+)")

/** 那一棵树里能认出清单的整话，次序是 站 → 号 → order。读不出的那些由 [desktopComicDownloadedSkipped] 数。 */
internal fun desktopComicDownloadedChapters(root: File): List<DesktopComicDownloadedChapter> =
    parsedDirs(root).mapNotNull { readChapter(it) }

/** 认不出的那几条（没有清单 / 清单读不出 / 号与目录对不上），给屏上那句"跳过 N 条"用。 */
internal fun desktopComicDownloadedSkipped(root: File): Int =
    parsedDirs(root).count { readChapter(it) == null }

/**
 * 一趟数完两格：认得出的那些 + 认不出的条数（给「本机已下载」那一屏的读侧用）。
 *
 * ## 为什么不叫上面那两颗各跑一次
 *
 * 那两颗各自把整棵树走一遍，而"认得出"那一趟要为**每一页**读一次文件头（第一百一十批那把尺子）——
 * 走两遍就是每一页多读一次。第一百一十三批刚为"每刷一页就全量读一遍盘是空转"改掉一次同类空转，
 * 这一格不该在门口把它重新添回来。
 *
 * ⚠️ 两格是**同一次**遍历的结果，所以「认不出 N 条」与「这些是认得出的」永远出自同一份盘上状态；
 *    分两趟读的话，中间落了盘就会念出自相矛盾的两格。
 */
internal fun desktopComicDownloadedScan(root: File): Pair<List<DesktopComicDownloadedChapter>, Int> {
    val dirs = parsedDirs(root)
    val found = dirs.mapNotNull { readChapter(it) }
    return found to (dirs.size - found.size)
}

/** `<根>/<站>/<号>/<话目录>` 那一层的目录（第三层才是话；散文件与更深的层不参与）。 */
private fun parsedDirs(root: File): List<File> {
    if (!root.isDirectory) return emptyList()
    val out = mutableListOf<File>()
    root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { source ->
        source.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { comic ->
            comic.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { out += it }
        }
    }
    return out
}

/**
 * 一条目录 → 那一话。认不出时交回 `null`（**不抛**：这一屏要在用户机器上数他攒了三年的东西，
 * 里面什么都可能有 —— 手工放的书、导进来的裸目录、半截的清单；抛出去就是一屏空白）。
 */
private fun readChapter(dir: File): DesktopComicDownloadedChapter? {
    val manifest = File(dir, "chapter.json")
    if (!manifest.isFile) return null
    val text = runCatching { manifest.readText() }.getOrNull() ?: return null
    // 那一格装的是**话号**（对岸 `DownloadManager.kt:505` `put("chapterId", task.chapterId)`）⇒ 它只用来判"这条清单点名了哪一话"。
    // 本子的号住在**目录名**里（`<站>/<号>/<order补零>_<题名>`）—— 拿清单那一格当号就是第九十七批 §6 那条缺陷的形状（两端互认全断）。
    val chapterId = FIELD_CHAPTER_ID.find(text)?.groupValues?.get(1) ?: return null
    val order = FIELD_ORDER.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    val expected = FIELD_EXPECTED.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    val title = FIELD_TITLE.find(text)?.groupValues?.get(1) ?: return null
    val comicDir = dir.parentFile ?: return null
    val sourceDir = comicDir.parentFile ?: return null
    if (chapterId.isBlank() || order < 0) return null
    // 整名回核：目录名必须等于 `order 补零 _ 净化题名`（对岸 `:156` 就是这个拼法）。
    // 这一枚同时抓住"前缀与 order 对不上"与"题名与目录名对不上"两档 —— 认下来会把 A 话的清单套到 B 话的页上。
    if (dir.name != desktopComicChapterDirName(order, title)) return null
    val comicId = comicDir.name
    var bytes = 0L
    var written = 0
    for (index in 0 until expected) {
        val page = File(dir, desktopComicChapterPageFileName(index))
        // 体积那一格仍按"占了多大地方"数（坏页也占地方），而"齐不齐"只认那颗共用判式 —— 两件事不许互相冒充。
        if (page.isFile) bytes += page.length()
        if (desktopComicPageLooksWhole(dir, index)) written += 1
    }
    return DesktopComicDownloadedChapter(
        sourceKey = sourceDir.name,
        comicId = comicId,
        chapterId = chapterId,
        order = order,
        title = title,
        expectedPages = expected,
        writtenPages = written,
        bytes = bytes,
        complete = desktopComicChapterIsComplete(dir, expected),
    )
}

/** 按本子分组（同一号的几话并一格），次序跟着 站 → 号。 */
internal fun desktopComicChaptersByComic(chapters: List<DesktopComicDownloadedChapter>): List<DesktopComicDownloadedGroup> =
    chapters.sortedWith(compareBy({ it.sourceKey }, { it.comicId }, { it.order }))
        .groupBy { it.sourceKey to it.comicId }
        .map { (key, rows) -> DesktopComicDownloadedGroup(comicId = key.second, sourceKey = key.first, chapters = rows) }

/**
 * 翻开那一屏要的三格：那一话在本机的**目录**、盘上算一整页的颗数、清单说的应写颗数。
 *
 * 第一百一十六批（离线阅读）用它把阅读器接到本机那一棵树。
 */
internal data class DesktopComicLocalChapter(
    val dir: File,
    /** 盘上**算一整页**的颗数（坏页与缺页都不在内 —— 吃的就是那颗共用判式）。 */
    val writtenPages: Int,
    val expectedPages: Int,
)

/**
 * 在盘上那一棵树里认出「这一话」的那一条目录（`chapterId` 是**源站那一枚话号**，与清单里那一格同轨）。
 *
 * ## 为什么按话号而不按行号
 *
 * 行号（`order`）会漂：源站中间插一话，`0001_xxx` 就指到别的话上去（本仓 §「阅读续读」那条限制）。
 * 而清单里的 `chapterId` 正是对岸用来互认整棵目录树的那一枚（`DesktopComicDownloadedList.kt:32` 的 KDoc），
 * 阅读器手上有的也正是这一枚（`EngineSession.Episode.id`）⇒ 两端吃同一枚才认得出同一话。
 *
 * ## 为什么复用 [desktopComicDownloadedChapters] 而不是直接拼目录名
 *
 * 拼名字要 `order` + `title` 两格，而阅读器手上只有话号 —— 拼出来必然要靠行号，那就漂。
 * 走读侧那条路等于**同一次读盘**顺带做完「目录名回核」与「逐页判整页」，认下来的那一话就是屏上念的那一话；
 * 认不到就是 `null`（本机没有这一话），而不是猜一条目录。
 *
 * ⚠️ [sourceKey] / [comicId] 要比的是**盘上目录那一形**（净化过的）。这里不替调用方净化：
 * 读侧那颗件一旦自己算净化，第一百一十二批那条纪律（「屏上念已下全」不跟着表与净化口径改口）就破了。
 * 归一住在 `DesktopComicOfflineReading.kt` 的 [desktopComicOfflineLocalChapter]（第一百一十六批）。
 * 话号那一格不参与净化：它住在**清单里**而不是目录名里，两端本来就是同一枚字符串。
 *
 * ⚠️ 代价：这一颗把整棵树走一遍并为每页读一次头部。阅读器**只在进那一屏时问一次**（不在逐页里问），
 *    所以它不会变成第一百一十三批刚改掉的那种空转。
 */
internal fun desktopComicLocalChapter(
    root: File,
    sourceKey: String,
    comicId: String,
    chapterId: String,
): DesktopComicLocalChapter? =
    parsedDirs(root).asSequence()
        .mapNotNull { dir -> readChapter(dir)?.let { dir to it } }
        .firstOrNull { (_, chapter) ->
            chapter.sourceKey == sourceKey && chapter.comicId == comicId && chapter.chapterId == chapterId
        }
        ?.let { (dir, chapter) ->
            DesktopComicLocalChapter(dir, chapter.writtenPages, chapter.expectedPages)
        }

/** 那一屏的表头读数：本子 / 话 / 齐 / 缺 / 跳过 / 字节六格，全从状态现取。 */
internal fun desktopComicDownloadedHeaderReading(
    groups: List<DesktopComicDownloadedGroup>, skipped: Int,
): String {
    val chapters = groups.flatMap { it.chapters }
    val complete = chapters.count { it.complete }
    return "D_本机下载 本=" + groups.size +
        " 话=" + chapters.size +
        " 齐=" + complete +
        " 缺=" + (chapters.size - complete) +
        " 跳=" + skipped +
        " 字节=" + chapters.sumOf { it.bytes }
}
