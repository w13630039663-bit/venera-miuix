package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite

/**
 * 墙上那一行读数的**唯一一处**拼法：段与段、站与站各一枚分隔符，空段一律丢掉。
 *
 * ## 为什么收进一颗件（批 11 那两帧图证抓到的）
 *
 * 「进屏 N 张 · 各站 x 张」这一串在四处各写了一份，其中三处是手拼的，而零张那一档真会走到
 * （全被分级挡掉、或站方答上但 0 条）—— 于是屏上读到的是**末尾悬着的一枚「·」**；
 * 最新那一屏把空段 `add` 进列表，那一枚悬着的还跑到**开头**。第四处（收藏）自己写了个
 * `if (isEmpty) ""` 才躲过去 —— 同一句话四处各写一遍、其中一处要自己躲，那就是"下一处会忘"的形状。
 *
 * ## 单位词是必填参数，不是默认值
 *
 * 三面墙念「N 张」（摆上屏的图），最新那一屏念「N 条」（取回的条目，它下面另有一行"滤掉 N 张不能上屏的"）。
 * 带默认值时漏传不报错，而漏传的形状恰好是"那一屏悄悄换了单位" —— 所以 [desktopPerSiteReading] 的 `unit` 必填。
 */

/** 段与段之间那枚分隔符。 */
internal const val DESKTOP_READING_SEPARATOR: String = " · "

/** 站与站之间那枚分隔符。用顿号而不是空格：第八批那帧图证读到「yande.re Gelbooru」被念成一枚站名。 */
internal const val DESKTOP_SITE_SEPARATOR: String = "、"

/** 各段按传入顺序拼一句；空串与 null 段丢掉，一段都没有 ⇒ 空串（调用方据此**整格不画**）。 */
internal fun desktopWallReading(vararg segments: String?): String =
    segments.filterNotNull().filter { it.isNotBlank() }.joinToString(DESKTOP_READING_SEPARATOR)

/**
 * [desktopWallReading] 的**续行**那一档：接在标题（「词」/「精选推荐」/「画廊收藏」）后面，
 * 所以非空时才带那一枚前导分隔符 —— 全空时交回空串，而不是屏上一枚孤零零的「·」。
 */
internal fun desktopWallReadingLine(vararg segments: String?): String {
    val joined = desktopWallReading(*segments)
    return if (joined.isEmpty()) "" else "$DESKTOP_READING_SEPARATOR$joined"
}

/** 各站那一小串（`yande.re 74 张、Gelbooru 97 张`）。空表 ⇒ 空串，由调用方那一句丢掉。 */
internal fun desktopPerSiteReading(counts: Map<GallerySite, Int>, unit: String): String =
    counts.entries.joinToString(DESKTOP_SITE_SEPARATOR) { "${it.key.displayName} ${it.value} $unit" }

/**
 * 这一面墙**真摆出来**的那些张。
 *
 * 段头那两句（「进屏 N 张」与「各站 x 张」）**都从这里数** —— 从前一处数墙、另一处数过关之前的
 * merge，于是屏上会写出"进屏 8 张 · yande.re 12 张、Gelbooru 6 张"这种两句加不起来的话（批 13）。
 *
 * ⚠️ `BLURRED`（遮挡档）**算摆出来**：它在屏上占一格、带着「分级遮挡」那枚角标，用户数得到；
 * 而 `HIDDEN` 不落屏，它由下面那行「N 张按…收起」单独说 —— 两句话各有各的数，谁也不冒领谁。
 */
internal fun desktopWallPosts(wall: DesktopWall): List<GalleryPost> = wall.cards.map { it.post }

/**
 * 那两笔缺席合成一行时那枚连接符 —— **逐字照对岸**（`gallery/ui/GalleryScreen.kt:1817` 的
 * `append("；另有 …")`）。批 37 之前桌面是四处各拼一遍，两处在 `" · "` 两处在 `"；"`，
 * 同一件事在两头念成两种节奏，而字号与颜色也各两档。
 */
internal const val DESKTOP_GUARD_FRAGMENT_JOINER: String = "；"

/**
 * 墙上那两笔"少了谁"（命中屏蔽 / 分级收起）合成一行。空表 ⇒ **空串**，调用方整格不画。
 *
 * 收成一颗件（批 37）：拼法此前住在各 pane 里，于是"这一面墙说没说"要看它有没有手写那一段 ——
 * 单人页只在手写的那一格里说，而那一格的条件是"整墙空了"，非空时取回与摆上屏两个数就对不上而屏上无话。
 * 现在各 pane 只许调 [DesktopWallGuardLine]（它内部走这一颗），拼法这一处是唯一出处。
 */
internal fun desktopWallGuardReading(wall: DesktopWall): String =
    wall.guardFragments().joinToString(DESKTOP_GUARD_FRAGMENT_JOINER)

/**
 * 页尾那句「少了谁」—— 名单里每个主语各自带**自己的**动词。
 *
 * ## 为什么 [dispatched] 要单独传
 *
 * 「没答上」与「没发车」是两件事，而发现屏从前把两样混进同一张 `failures` 名单、统一念成
 * 「没发出去的站」—— 于是超时/403 那一站（恰恰是**发出去了**的那一站）在屏上被说成没发出去（第三十二批 批 32）。
 * 主语不另立一本账：[DesktopGalleryBatch.queries] 的口径本来就是"这一轮真发出去的标签串"，
 * 所以"发没发过"由它答。最新与排行两屏把两样分开存（`silent` 与 `failures`），喂给这颗的是
 * 两份合并后的名单 + 同一枚 `queries.keys` —— 两屏的形状不同而句子只有一处拼法（批 12 定过的那条）。
 *
 * ## 次序跟着站表，不跟着传入名单
 *
 * 两段的先后从前由"哪一段先写"定，而并发取数回来的 `failures` 顺序本就不稳 ⇒ 同一件事在两帧里换了位置。
 * 按 `GallerySite.entries` 走一遍，屏上那句就是可对照的。
 *
 * ⚠️ 名字与动词之间**不加空格**（「yande.re 没答上」会被念成"yande.re 没答上"两拍，
 * 与第八批"空格分隔站名被读成一枚站名"同一族），而站与站之间用「；」不用「、」——
 * 「、」是**同一件事的名单**（到底了的那几站），这里是**各带一句理由的清单**。
 */
internal fun desktopMissingReading(dispatched: Set<GallerySite>, failures: Map<GallerySite, String>): String? {
    if (failures.isEmpty()) return null
    val segments = GallerySite.entries.mapNotNull { site ->
        val reason = failures[site] ?: return@mapNotNull null
        if (site in dispatched) "${site.displayName}没答上——$reason" else "${site.displayName}没发车——$reason"
    }
    return segments.joinToString("；").takeIf { it.isNotEmpty() }
}

/**
 * 页尾那句「已经取到底…」。
 *
 * ⚠️ 站名与中文之间那枚空格是**读出来的**：`yande.re这一页` 会被念成站名叫"yande.re这一页"，
 * 与第八批那条"空格分隔站名被读成一枚站名"同一族 —— 文本判据抓不到，只有图证抓得到。
 * 名单为空时交回 null（**没话就不念**，而不是「已经取到底：（这一轮发过的站都到底了）」那种空壳）。
 */
internal fun desktopExhaustedReading(done: List<String>): String? {
    if (done.isEmpty()) return null
    return "已经取到底：" + done.joinToString(DESKTOP_SITE_SEPARATOR) { "$it 这一页之后没有更多" } +
        "（这一轮发过的站都到底了）"
}

/**
 * 「下一页」那一笔的相位 —— **整个 `gallery/ui` 只许这一颗**（判据 ① 数着声明处）。
 *
 * 批 23 之前它是两颗：`DesktopForYouMorePhase`（发现）与 `DesktopArtistMorePhase`（单人页），
 * 页尾那两句也各写了一遍。第四面墙（最新）要接上时看清：同一颗相位抄两遍、同一对句子抄两遍，
 * 只有量词「张 / 条」不同 —— 批 12 判过的正是这一族（四处同一句话，下一处一定会忘）。
 *
 * 它**不进** [DesktopGalleryBatch]：那一枚记的是"这一批的数据"，而这是 UI 的等待形状。
 * 也不并进各屏的取数态：续页期间上一批内容还在屏上，整屏换成 Loading 会把它们抹掉。
 */
internal sealed interface DesktopGalleryMorePhase {
    data object Idle : DesktopGalleryMorePhase
    data object Loading : DesktopGalleryMorePhase

    /** 下一页没取成 —— 已经摆出来的那些一张不少，那句必须这么说。 */
    data class Failed(val message: String) : DesktopGalleryMorePhase
}

/**
 * 那枚按钮的文案。[unit] 是**必填**量词：三面墙念「张」（摆上屏的图），最新那一屏念「条」
 * （取回的条目）—— 写死其一的形状与 [desktopPerSiteReading] 那条同一族（判据 ② 喂两种值）。
 */
internal fun desktopGalleryMoreLabel(taken: Int, unit: String): String = "下一页（已取 $taken $unit）"

/**
 * 页尾那一句（Idle 交回 `null` = 不念，那里就是那枚按钮）。
 *
 * 「没取成」那句的后半不是修辞：这几屏都是**追加**，失败只掉这一笔 ——
 * 不点名那一句就会被读成整屏失败，然后用户整屏重进，把已经看过的那一批换掉。
 */
internal fun desktopGalleryMoreReading(phase: DesktopGalleryMorePhase, taken: Int, unit: String): String? =
    when (phase) {
        DesktopGalleryMorePhase.Idle -> null
        DesktopGalleryMorePhase.Loading -> "正在取下一页…（这一屏已经摆着 $taken $unit）"
        is DesktopGalleryMorePhase.Failed -> "下一页没取成：${phase.message} —— 已经摆出来的这些一张不少。"
    }
