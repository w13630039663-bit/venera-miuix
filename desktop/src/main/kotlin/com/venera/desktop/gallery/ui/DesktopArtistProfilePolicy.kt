package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryForYouMerge
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GalleryRankings
import com.venera.compose.gallery.domain.GalleryTagFilter
import java.time.LocalDate

/**
 * 桌面画师单人页的**纯判据面**（第十九批 批 19 · D 那一件）。
 *
 * ## 这一屏一条口径都不是桌面定的
 *
 * 对岸那一屏（`app/.../gallery/ui/GalleryArtistProfileScreen.kt:153-169`）的取数算式住在 `gallery/domain`，
 * 而那一整个目录在桌面编译面上 ⇒ 这里只是**转交**：查询串由 [GalleryRankings.searchQuery] 算，
 * 档位用 `GalleryRanking.ALL`（对岸注释钉着"`ALL` 本身就是全站按分数"），桌面不自拼串、不另造参数、不互抄写法。
 *
 * ## 这一屏今天是单腿的
 *
 * 目的地自带一站（名单那一行本来就带着 `site`），所以这里只有**一条腿** ——
 * 与搜索屏"全部 = 三腿"不是一件事，读数不能共用"三站"那套词（那会把一站的话说成三站的）。
 *
 * ## 这一轮不做（点名，不写进屏上）
 *
 * 别名解析（批次 J：站方给 0 行时按该站 alias 换正名重搜）与外链 / credits 都不在这条路上 ——
 * 零张时桌面**不猜**、不自动换名重搜，屏上也永远不摆"这位没有 X"那种话。
 */
internal data class DesktopArtistTarget(val site: GallerySite, val name: String)

/**
 * 名单那一行 → 目的地的取法。**一条腿，取那一行站点次序里的第一站**。
 *
 * 这一屏的形状是"一站 + 一位"（[DesktopArtistTarget] 只有那两枚字段），而名单行可以带多站 ——
 * 与其一次发多站（那要新造一份"哪一站答上了"的账，而对岸同屏也不这么干），
 * 不如把第一站定死并把**是哪一站写在段头上**：用户看得见自己正在看的是哪一站，不存在"以为看了全部"。
 * 零站的那种行（名单档坏到连站点都没了）交回 `null` ⇒ 屏上那枚入口整枚不摆，
 * 比摆一枚按下去只会说"没有目的地"的按钮好。
 */
internal fun desktopArtistProfileTarget(name: String, sites: List<GallerySite>): DesktopArtistTarget? =
    sites.firstOrNull()?.let { DesktopArtistTarget(it, name) }

/**
 * 一次取几张。**值不是桌面造的**：对岸同屏那枚 `SLOT_FETCH_SIZE = 18`，
 * 判据 ③ 逐字回核那颗文件的那一行（与"观感尺寸用现成口径"同一条纪律）。
 */
internal const val DESKTOP_ARTIST_SLOT_SIZE: Int = 18

/**
 * 查"这一位的图"的查询串 —— 整个算式转交对岸那颗，桌面一份都不重写。
 *
 * `anchor = null`：单人页不按日期锚（那是日榜那一档的事）。
 */
internal fun desktopArtistProfileQuery(
    target: DesktopArtistTarget,
    todayUtc: LocalDate,
): String = GalleryRankings.searchQuery(
    site = target.site,
    filters = listOf(GalleryTagFilter(target.name)),
    ranking = GalleryRanking.ALL,
    anchor = null,
    todayUtc = todayUtc,
)

/** "发了而这一站没答上"那句：一次抖动不等于"这位没有图"，两句话必须分开念。 */
internal fun desktopArtistFailureReading(target: DesktopArtistTarget, message: String): String =
    "向 ${target.site.displayName} 要「${target.name}」的图没答上：$message —— 这一屏只有这一站，它没答上就是今天没有读数。"

/** "答上了而一张都没有"那句。不猜成因、不换名重搜（别名那一层今天不在这条路上）。 */
internal fun desktopArtistEmptyReading(target: DesktopArtistTarget): String =
    "${target.site.displayName} 上没有以「${target.name}」为标签的图。"

/**
 * 段头那一句。今天这一屏取的是"站方按分数序"的一页一页，**每页几张是对岸那枚预算**（[DESKTOP_ARTIST_SLOT_SIZE]）。
 *
 * ⚠️ 这一句从前写的是"前 18 张、翻不动"那一版边界说明，批 21 接上续页之后同一句话会变成假话
 * （承诺一件已经做到的事），所以边界换成"每页几张"这件**今天仍然成立**的事实。
 * 那句下架由 `DesktopStaleClaimsTest` ⑱ 钉着（对照是"续页真接上了"那三件事）。
 */
internal fun desktopArtistHeadline(target: DesktopArtistTarget): String =
    "${target.site.displayName} · 「${target.name}」按分数序 · 每页 $DESKTOP_ARTIST_SLOT_SIZE 张"

/**
 * 页尾那三档（按钮文案、正在取、没取成）今天住在共享那颗件里（[desktopGalleryMoreLabel] 与
 * [desktopGalleryMoreReading]）—— 批 23 之前它是两颗：发现屏与这一屏各抄了一份，
 * 只有量词不同。第四面墙要接上时那一族就非收不可了（判据 ① 数着声明处）。
 */


/**
 * 到底那一句：**发过的那一站答完了**才念。
 *
 * 它报的是"这一屏一共摆了几张"，不是"这一位一共有几张" —— 站方从不告诉总数
 * （手册里那笔"数据面天花板"的账：搜索总数没有一站提供），所以这里只能说屏上这些。
 */
internal fun desktopArtistEndReading(target: DesktopArtistTarget, taken: Int): String =
    "${target.site.displayName} 上没有更多以「${target.name}」为标签的图了 —— 这一屏摆的是全部 $taken 张。"

/**
 * 这一屏**下一笔要发第几页**；交回 `null` = 那一条腿已经到底，没有可发的了。
 *
 * 整颗转交 [desktopGalleryBatchNextPages]（里面就是共享面那颗 `nextPagesForAppend`）：
 * "缺席的那条腿从第 1 页补"这一句一旦被这里抄成 `page + 1`，出现的不是崩溃而是**少一批图**。
 * 抽成一颗函数是为了它能被真跑（⑰–⑳），而不只是被扫一行字。
 */
internal fun desktopArtistNextPage(base: DesktopGalleryBatch, target: DesktopArtistTarget, query: String): Int? =
    desktopGalleryBatchNextPages(
        base,
        desktopGalleryBatchLegs(base, mapOf(target.site to query)).keys.toList(),
    )[target.site]

/**
 * 一页回来后落地成新一颗持有件 —— **这一颗不发网络**，所以判据能直接真跑它。
 *
 * 为什么要有这一颗（批 22）：续页会错的三件事全在这里（该要第几页、谁答上了、到底没），
 * 而它们住在 `fetchArtistPage` 里时只能靠"扫表达式"钉 —— 扰动第一次跑出绿就说明那把尺不够。
 *
 * - **`answered = false` 时那一页不进页码账**：给没答上的那一站记一页，就是把"永久漏掉那一页"写进账本；
 * - **去重键吃 `base.seenKeys`**：站方翻页不保证边界稳定，两页重叠一张是常态 ⇒ 不带上就摆两遍；
 * - **缺席不算到底**：三处判据都从"答上了几张"出发（共享那颗 reducer 的规矩，桌面不重述）。
 */
internal fun desktopArtistBatchAfter(
    base: DesktopGalleryBatch,
    target: DesktopArtistTarget,
    page: Int,
    posts: List<GalleryPost>,
    answered: Boolean,
    reason: String?,
): DesktopGalleryBatch {
    val site = target.site
    val pools = if (answered) mapOf(site to posts) else emptyMap()
    val merged = GalleryForYouMerge.page(
        pools = pools,
        seed = base.seed,
        // 混排节奏只吃这一笔的页码（它唯一的用途是让相邻两轮的交错序列不同）。
        page = page,
        seenKeys = base.seenKeys,
        // 这一屏**不剔收藏**：那是"为你推荐"那一路的判据，对岸同屏也不剔。传空集就是明说不剔。
        favouriteUids = emptySet(),
    ).getOrThrow()
    // 这一屏只有一条腿，所以"这一轮问过的"就是它自己：答上了 ⇒ 上一轮那句销掉；又失败 ⇒ 换成这一轮那句。
    // 从前这里写的是 `base.failures.toMutableMap()` 再 put —— 只有又失败才改得动，
    // 于是"第一笔抖了、第二笔答上"之后页尾会一边摆着它的图一边说它没答上（批 33 数到的第四处）。
    val reasons = desktopFailureLedgerAfter(
        previous = base.failures,
        asked = setOf(site),
        fresh = reason?.let { mapOf(site to it) }.orEmpty(),
    )
    return desktopGalleryBatchApply(
        current = base,
        round = base.seed,
        pages = mapOf(site to page),
        returnedBySite = if (answered) mapOf(site to posts.size) else emptyMap(),
        reasons = reasons,
        // ⚠️ 这把"到底"的尺吃的就是发出去的那枚 limit（两处写成两个数，要么第一页之后就消失、
        // 要么一直重复要同一页）。判据 ⑭ 与 ⑲ 各钉一头。
        pageSizeOf = { DESKTOP_ARTIST_SLOT_SIZE },
        paged = merged,
    )
}
