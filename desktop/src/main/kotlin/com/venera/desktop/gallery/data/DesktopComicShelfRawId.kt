package com.venera.desktop.gallery.data

/**
 * 第一百一十二批 · 盘上那一形 → 站点原号的**回收**。
 *
 * 批 111 量到的结构差：「本机已下载」的行模型里 `comicId` 那格取自**目录名**
 * （`DesktopComicDownloadedList.kt:99`），而目录名是净化后的那一形
 * （`desktopComicSafePathKey` 把 `/ \ : * ? " < > |` 替成 `_`），`chapter.json` 里没有 comicId 那格
 * （`DesktopComicChapterDownload.kt:24`）。盘上唯一还留着原号的地方是队列表 `download_tasks.json`
 * （`:198` 读、`:324` 写的是站点原号）。
 *
 * ⚠️ 现量过的真缺陷面是 3/34 档源（`ehentai` / `ikmmh` / `manhuaren` —— 号本身就是整条 URL 而又被拼进 URL），
 * 其余各档要么号是数字/uuid（净化恒等），要么压根不吃这枚号（goda 取章节只用 `epId`，rounds
 * `desktop-shelf-repair-handle-2026-10-14.md` §9 的 B 趟实测）。所以这一颗件**不猜**：查得到就还原号，
 * 查不到就沿用行里那一格并把"用的是哪一形"念出来。
 * ⚠️ 表只吃队列表不吃 `comic_history`：历史那格是"打开过的那一本"，与"盘上这一本"没有一一对应，
 * 拿它当反查键会把 A 本的页写进 B 本的目录。
 */
internal class DesktopComicShelfRawIdIndex private constructor(private val byKey: Map<String, String>) {

    /** 那一行在本机有没有留下过原号（`comicDirName` 是盘上那一形，即行模型里的那格）。 */
    fun rawIdOf(sourceKey: String, comicDirName: String): String? = byKey[desktopComicShelfRawIdKey(sourceKey, comicDirName)]

    companion object {
        /**
         * 建键：站那一档也走同一次净化 —— 目录名用的就是净化后的 `sourceKey`（`DesktopComicChapterDownload.kt:51`）。
         *
         * 同一枚盘上形可能留下两笔：一笔是**原号**（详情那一路排进去的），一笔是**盘上形自己**
         * （批 111 那枚把手排的就是行模型那一格）。认原号那一笔优先，其次才按 `updateTime` 取最近 ——
         * 反过来说"最近的就是原号"会把批 111 留下的那条残留当成依据，等于刚补的这一批把上一条补不掉。
         */
        fun of(tasks: List<DesktopComicQueueTask>): DesktopComicShelfRawIdIndex {
            val best = HashMap<String, DesktopComicQueueTask>()
            for (task in tasks) {
                val key = desktopComicShelfRawIdKey(task.sourceKey, desktopComicSafePathKey(task.comicId))
                val current = best[key]
                if (current == null || desktopComicShelfRawIdScore(task) > desktopComicShelfRawIdScore(current) ||
                    (desktopComicShelfRawIdScore(task) == desktopComicShelfRawIdScore(current) &&
                        task.updateTime > current.updateTime)
                ) best[key] = task
            }
            return DesktopComicShelfRawIdIndex(best.mapValues { it.value.comicId })
        }

        /** 这一笔像不像"见过原号"：净化前后不同串 ⇒ 它带着的确实是原号（1），否则只是目录那一形（0）。 */
        private fun desktopComicShelfRawIdScore(task: DesktopComicQueueTask): Int =
            if (task.comicId != desktopComicSafePathKey(task.comicId)) 1 else 0
    }
}

private fun desktopComicShelfRawIdKey(sourceKey: String, comicDirName: String): String =
    desktopComicSafePathKey(sourceKey) + "/" + comicDirName

/** 回收那一步的交值：哪一枚号 + 是不是从本机记录还回来的（那一句要念得出用的是哪一形）。 */
internal fun desktopComicShelfRepairComicId(
    chapter: DesktopComicDownloadedChapter, index: DesktopComicShelfRawIdIndex,
): Pair<String, Boolean> {
    val raw = index.rawIdOf(chapter.sourceKey, chapter.comicId)
    // 「还原」只在确实换了串时才算：表里那一行本来就能是盘上形（批 111 的把手留下的正是那一形），
    // 那种笔认下来等于当着日志的面谎称「从本机记录还原」。
    return (raw ?: chapter.comicId) to (raw != null && raw != chapter.comicId)
}

/**
 * 排出去的那八格：号换成回收到的那一枚，其余照旧。
 *
 * ⚠️ 递原号不会长出第二棵树：`enqueue` 造目录时同样走 `desktopComicSafePathKey`
 * （`DesktopComicDownloadQueue.kt:93-98`），净化回来的正是盘上那一颗目录。判式 ① 钉的就是这两句一起成立。
 */
internal fun desktopComicShelfRepairArgsOf(
    chapter: DesktopComicDownloadedChapter, index: DesktopComicShelfRawIdIndex,
): DesktopComicRepairArgs {
    val (comicId, _) = desktopComicShelfRepairComicId(chapter, index)
    return DesktopComicRepairArgs(
        sourceKey = chapter.sourceKey,
        comicId = comicId,
        comicTitle = comicId,
        comicCover = "",
        chapterId = chapter.chapterId,
        chapterTitle = chapter.title,
        chapterOrder = chapter.order,
        placeholder = false,
    )
}

/** 那一句的后半：还回原号与沿用目录形两种果各有各的话，不许合成一句含糊的。 */
internal fun desktopComicShelfRepairIdNote(comicId: String, resolved: Boolean): String =
    if (resolved) " · 号按本机记录还原成 $comicId" else " · 本机没有这号的原始记录，排的是目录那一形 $comicId"
