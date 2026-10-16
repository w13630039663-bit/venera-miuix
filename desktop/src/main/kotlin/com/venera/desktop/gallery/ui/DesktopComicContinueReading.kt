package com.venera.desktop.gallery.ui

import com.venera.compose.data.db.HistoryRecord

/**
 * 批 94 ·「开始/继续阅读」那一枚主胶囊的四颗算式（纯函数，不碰库也不碰组合）。
 *
 * 题面是量出来的两条，都在 `app/.../feature/ComicDetailScreen.kt`：
 * ① 对岸详情有一枚大号胶囊（`:552-600`），有历史时文案换「继续阅读」并多一行小字「继续 <那一话的标题> · 第 N 页」，
 *    底边还有一条进度线；桌面详情到今天只有「共 N 话」加那一列话号，按下恒从第 0 页起。
 * ② **对岸那枚胶囊自己是半接线**：文案说「继续 第 8 页」而 `onClick` 走 `onTriggerRead()`（`:564`），
 *    后者打开的是 `chs.first()`（`:266-269` ⇒ `launchChapter(..., initialPage = 0)`）。
 *
 * 所以这一批的口径是：**字面逐字回核对岸**（两端同一句话写成两个串就是漂，三条回核尺子在
 * `DesktopComicContinueReadingTest` ①②⑦），而**动作按那句文案承诺的那样接上**——
 * 摆一句做不到的话在本仓是假开关（宁可少摆一格，不许摆假的）。`app/` 是受保域，本批一字未动；
 * 那条"文案与动作不同轨"的既有缺陷登记在手册与 rounds，等用户点头才去修对岸。
 */

/** 主胶囊那句：没历史是「开始阅读」，有历史是「继续阅读」（对岸 `:585` 同一枚三元）。 */
internal fun desktopComicReadButtonLabel(hasHistory: Boolean): String =
    if (hasHistory) "继续阅读" else "开始阅读"

/**
 * 那行小字（对岸 `:592`）。页号 **0 基数转 1 基数**：库里存的是索引，人话从 1 数起，
 * 少加那一枚 1 就是屏上永远少一页 —— 与历史那一屏「第 N 话 - 第 M 页」同一口径（批 82 定过）。
 */
internal fun desktopComicContinueCaption(lastChapterTitle: String, lastPageIndex: Int): String =
    "继续 " + lastChapterTitle + " · 第 " + (lastPageIndex + 1) + " 页"

/**
 * 进度线那一格（对岸 `:554-556`）。**没有分母的百分比是假数据**：话数为 0 时交回 0f，
 * 那一屏据此整条线都不摆（而不是画一条 0% 的线装作读过头了）。
 */
internal fun desktopComicReadProgress(lastChapterIndex: Int, totalChapters: Int): Float =
    if (totalChapters <= 0) 0f else ((lastChapterIndex + 1f) / totalChapters).coerceIn(0f, 1f)

/**
 * 按下落到哪：交回 **(第几话, 从第几页)**，一话都没有时交回 `null`（那一枚胶囊按下去没对象就是不摆）。
 *
 * 三条 deliberate 的口径：
 * - 记录里的话号**夹进现有目录**而不是抛：源站删了那一话是常态，抛会让整屏挂掉，
 *   而夹住之后屏上那行小字仍念的是库里那一话的标题（用户看得见差在哪）；
 * - **夹话号时不动页号**（页号是那一话内部的进度，清零等于悄悄丢用户读到的位置）；
 * - 无记录 ⇒ 第一话第 0 页。**那一列话的各行按下仍从该话开头读**（照对岸 `:1206`），
 *   只有这一枚胶囊才续读 —— 把行也改成续读是我们自己加语义，本批不许。
 */
internal fun desktopComicResumeReading(record: HistoryRecord?, episodeIds: List<String>): Pair<Int, Int>? {
    if (episodeIds.isEmpty()) return null
    val chapter = record?.lastChapterIndex?.coerceIn(0, episodeIds.lastIndex) ?: 0
    return chapter to (record?.lastPageIndex?.coerceAtLeast(0) ?: 0)
}

/** 那一话正是库里读过的那一话时挂的标记说明（对岸 `:1218` 同一串，给读屏与工具用）。 */
internal const val DESKTOP_HISTORY_CHAPTER_MARK_DESCRIPTION: String = "上次读到这里"
