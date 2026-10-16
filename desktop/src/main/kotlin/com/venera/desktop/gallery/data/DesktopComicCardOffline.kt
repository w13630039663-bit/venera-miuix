package com.venera.desktop.gallery.data

/**
 * 批 99 · 漫画卡上那一枚「已下载 / 离线不全」。
 *
 * ⚠️ 这一族在一条线上一共存着**三把"齐不齐"的口径**：对岸 `DownloadManager.stateOfChapterDir`
 * （`app/…/download/DownloadManager.kt:307-318`，吃 `expectedPages` + 清单 `complete` + `listFiles` 的颗数）、
 * 对岸另一颗 `LocalComicManager`（`:71`、`:139`，只看有没有图）、以及桌面批 96 立的第三口径
 * （清单 `complete:true` **且**逐页查盘非空）。这一颗**只吃第三口径已经算好的那一格**（`chapter.complete`），
 * 自己一页都不数 —— 在旁边再写一种，两屏就会对同一棵目录念出两句不同的话。
 */
internal sealed interface DesktopComicOfflineBadge {
    /** 这一本在本机的每一话都齐。 */
    data class Downloaded(val chapters: Int) : DesktopComicOfflineBadge

    /** 有话不齐（哪怕只有一话齐也算这一档 —— 「有得看」不等于「下全了」）。 */
    data class Partial(val complete: Int, val total: Int) : DesktopComicOfflineBadge
}

/**
 * 那一枚标记的键：**站 + 号**。
 *
 * 盘上那棵树本来就按站分目录（`downloads/<站名键>/<号>/…`），而卡上只有 `card.id` ——
 * 只拿号当键就会把 A 站那本已下的标记贴到 B 站同号的本子上（与批 96 那枚 `peek` 传空串是同一族）。
 */
internal fun desktopComicOfflineKey(sourceKey: String, comicId: String): String = "$sourceKey/$comicId"

/** 一次数完：`comicId` 那一本在本机存过几话、齐没齐。没存过的那本**不进表**（不给零话编一枚标记）。 */
internal fun desktopComicOfflineBadges(
    chapters: List<DesktopComicDownloadedChapter>,
): Map<String, DesktopComicOfflineBadge> {
    val grouped = LinkedHashMap<String, MutableList<DesktopComicDownloadedChapter>>()
    chapters.forEach { chapter ->
        grouped.getOrPut(desktopComicOfflineKey(chapter.sourceKey, chapter.comicId)) { mutableListOf() }.add(chapter)
    }
    return grouped.mapValues { (_, list) ->
        val complete = list.count { it.complete }
        if (complete == list.size) DesktopComicOfflineBadge.Downloaded(chapters = list.size)
        else DesktopComicOfflineBadge.Partial(complete = complete, total = list.size)
    }
}

/** 屏上那一句（短到能压在封面那一角而不吃掉半张封面）。 */
internal fun desktopComicOfflineBadgeLabel(badge: DesktopComicOfflineBadge): String = when (badge) {
    is DesktopComicOfflineBadge.Downloaded -> "已下载 ${badge.chapters} 话"
    is DesktopComicOfflineBadge.Partial -> "离线不全 ${badge.complete}/${badge.total} 话"
}

/** 取证那句：三格各归各的，空表那一档也念得出零（"没念"与"念了零"是两件事）。 */
internal fun desktopComicOfflineReading(sourceKey: String, badges: Map<String, DesktopComicOfflineBadge>): String {
    val full = badges.values.count { it is DesktopComicOfflineBadge.Downloaded }
    return "D_卡标 站=$sourceKey 卡=${badges.size} 全齐=$full 不全=${badges.size - full}"
}

/**
 * 墙上那排卡里**贴得出来**几枚。
 *
 * ⚠️ 盘上数得着 ≠ 屏上贴得出：那一棵树里的号可能一张都不在墙上（包内那一帧就是这一档 —— 墙上 30 张而盘上那本不在里面）。
 * 没有这一句，"一枚都没贴"与"贴上了而我没拍到"就分不开。
 */
internal fun desktopComicOfflineHits(
    badges: Map<String, DesktopComicOfflineBadge>,
    sourceKey: String,
    cardIds: List<String>,
): Int = cardIds.count { badges.containsKey(desktopComicOfflineKey(sourceKey, it)) }

/** `样例=` 只念前 5 枚：读数不是日志，整墙贴出来就没法一眼看完（下一趟要摆哪一棵，看这五枚就够了）。 */
internal fun desktopComicOfflineHitReading(
    sourceKey: String,
    badges: Map<String, DesktopComicOfflineBadge>,
    cardIds: List<String>,
): String = "D_卡标命中 站=$sourceKey 墙=${cardIds.size} 贴上=${desktopComicOfflineHits(badges, sourceKey, cardIds)} " +
    "盘上=${badges.size} 样例=${cardIds.take(5).joinToString(",")}"
