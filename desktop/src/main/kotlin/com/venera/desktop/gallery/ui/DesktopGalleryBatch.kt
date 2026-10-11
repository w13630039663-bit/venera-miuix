package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryForYouMerge
import com.venera.compose.gallery.domain.GallerySearchMerge
import com.venera.compose.gallery.domain.nextPagesForAppend

/**
 * 桌面「这一批卡」的持有件（第十批 ③）：一颗**不可变的值** + 两颗纯函数。
 *
 * ## 为什么现在要立这一颗
 *
 * 第九批把「发现」那一屏转正时刻意只做第一轮的第一页，屏上那句写着"翻到更多还没有"。
 * 补续页缺的不是网络那一层（`searchPosts(query, page, limit)` 今天就能翻页），
 * 缺的是**这一屏的状态**：第几页、哪些站已经到底、去重键累计到哪儿、这一轮的种子是谁。
 * Android 那一侧这些住在 `GalleryForYouViewModel` 的可变字段里，而桌面没有那一层 ——
 * 所以这里立一颗**值 + reducer**，而不是在 composable 里散着放五枚 `mutableStateOf`：
 * 散着放的形状下，"换种子要同时清哪四样"这种话只能靠记性，漏一枚就是
 * "换了批次却像没换"（去重键没清）或"下一笔按第 2 页追加进新批次"（页码没清）。
 *
 * ## 这颗同时是给详情面板用的那一颗
 *
 * 方案文档（`desktop-detail-panel-plan-2026-10-04.md` §7 P1）里"这一批卡要在桌面侧有一份
 * **可寻址**的持有件"是同族的一笔债：面板要的是"哪几张、当前第几张"，续页要的是
 * "第几页、哪些站到底"。两边都从 `posts` 这一枚出发，所以先立一颗，
 * 详情面板那一批往这里加字段，而不是再造第二颗。
 *
 * ## 五条行为一律照 Android 那颗 ViewModel（`applyPage`），桌面不另定
 *
 * 混摆、去重键、到底三处判据都**转交**共享件（[GalleryForYouMerge] / [GallerySearchMerge]），
 * 这里只落地"一页之后状态长什么样"。两处各定一遍的漂法是想得出的：
 * 同一屏在手机上是"到底了"，在桌面上还留着两页可翻，而用户只会读成"桌面卡了"。
 */
internal data class DesktopGalleryBatch(
    /** 这一轮的种子。换一批就是换它，而它同时是 reducer 的**作废闸**（见 [desktopGalleryBatchApply]）。 */
    val seed: Long,
    /**
     * **每条腿各自落到第几页**。第十三批 批 8 起页码只有这一份账 ——
     * 从前这里是一枚全局 `page`，而"所有腿同一页"这句话是假的：上一笔超时的那一站
     * 从没拿到过第 1 页，下一笔却跟着别腿跳到第 2 页 ⇒ **那一页的图永久漏掉**。
     * 判据本身在共享面 [nextPagesForAppend]，桌面不重述。
     */
    val pageBySite: Map<GallerySite, Int> = emptyMap(),
    /** 这一轮真发出去的标签串。`atEnd` 与页尾那句都以它为准，不以"三站"为准。 */
    val queries: Map<GallerySite, String> = emptyMap(),
    val posts: List<GalleryPost> = emptyList(),
    /** 前面各页**已上屏**条目的去重键（下一轮混摆拿它剔重）。 */
    val seenKeys: Set<String> = emptySet(),
    /** 已经到底的站。**只记真答上过而不足一页的**（缺席不算到底）。 */
    val sitesDone: Set<GallerySite> = emptySet(),
    /** 逐站点名的缺席原因（没标签 / 没凭据 / 超时 / 预算到点）。 */
    val failures: Map<GallerySite, String> = emptyMap(),
    val excludedFavourite: Int = 0,
    val droppedUnusable: Int = 0,
    val droppedDuplicates: Int = 0,
) {

    /** 取到的最深那一页 —— 只给读数用（"已翻到第 N 页"），**不许**拿它算下一笔（那是第二份页码）。 */
    val page: Int get() = pageBySite.values.maxOrNull() ?: 0

    /**
     * 这一轮到底了没 —— 判据是「**发过的**站全到底」，不是「三站全到底」。
     *
     * 两枚下限都是被形状教出来的：
     * - `queries` 空 ⇒ 不叫到底，那叫空屏（一句"已经到底"会把"什么都没发出去"说成"内容看完了"）；
     * - 只发过两站时第三站不该参与结论（它今天没有能推荐的标签，压根没发过车）。
     */
    val atEnd: Boolean get() = queries.isNotEmpty() && sitesDone.containsAll(queries.keys)
}

/**
 * 下一轮该发哪几条腿：已经到底的站不再问。
 *
 * 刻意**不**在这里判 Gelbooru 凭据与"这一站没标签" —— 那是发车前的闸（在 pane 那一侧），
 * 与"到底没"是两件事：前者是"这一轮不该问它"，后者是"问过了而它给完了"。
 */
internal fun desktopGalleryBatchLegs(
    batch: DesktopGalleryBatch,
    runnable: Map<GallerySite, String>,
): Map<GallerySite, String> = runnable.filterKeys { it !in batch.sitesDone }

/**
 * 下一笔**每条腿各自要第几页** —— 整颗转交共享面那颗 [nextPagesForAppend]，桌面一处都不重述。
 *
 * 为什么值得单独包一层：两屏（发现 / 搜索）都要算它，而"缺席的腿从第 1 页补"这一句
 * 一旦被哪一屏抄成 `page + 1`，出现的就不是崩溃而是**少一批图**——那种缺陷只有对照账才看得见。
 */
internal fun desktopGalleryBatchNextPages(
    batch: DesktopGalleryBatch,
    dispatched: List<GallerySite>,
): Map<GallerySite, Int> = nextPagesForAppend(batch.pageBySite, batch.sitesDone, dispatched)

/**
 * 一页回来之后，那本「谁缺席了」的账怎么并 —— **整个 `gallery/ui` 只许这一处并**。
 *
 * 规则只有一句：**这一轮答过的腿只看这一轮的答案**，上一轮的旧账只留给这一轮压根没被问的那几条腿
 * （到底的那一站）。并集方向写反不崩、不报错，只有屏上那一句是假的 ——
 * 「墙上摆着那一站的图，同一行说它没答上」。这一条规则从前被写了四遍而两处方向是反的：
 * 批 31 在 [fetchWallPage] 抓到第一处、批 32 在 [fetchForYouPage] 抓到第二处，
 * 本批顺着"还有谁自己并了一遍"数到搜索屏（`base.failures + 这一轮的原因`）与单人页
 * （`base.failures.toMutableMap()` 再 put，只有又失败才改得动）。
 *
 * ⚠️ [asked] 要交**这一轮真发出去的那几条腿**（答上的没答上的都算），不是"失败的那几腿"——
 * 交后者等于把销账条件写成"又失败了才销"，正是本批修掉的那条假话。
 */
internal fun desktopFailureLedgerAfter(
    previous: Map<GallerySite, String>,
    asked: Set<GallerySite>,
    fresh: Map<GallerySite, String>,
): Map<GallerySite, String> = buildMap {
    fresh.forEach { (site, reason) -> put(site, reason) }
    previous.forEach { (site, reason) -> if (site !in asked) put(site, reason) }
}

/**
 * 把一页落地。
 *
 * - **[round] 与 [DesktopGalleryBatch.seed] 不符 ⇒ 整颗原样交回**：中途换过一批的话，
 *   这一笔的标签串与打乱流都属于上一轮，落地就会出现"卡片写着新串、图来自旧串"
 *   （Android 那颗 ViewModel 里同一句 `if (round != seed) return` 是这么来的）。
 * - **各腿全是第 1 页才替换、否则追加**：换种子之后还追加，摆出来的就是两批混排的一屏。
 * - **页码只给答上了的那一站记**（[returnedBySite] 里没有的腿 = 这一笔它没给数）：
 *   给没答上的那一站记一页，就是把"永久漏掉那一页"写进账本。
 * - **缺席不算到底**：三处判据都从 `returnedBySite` 这一枚出发，失败的那一站根本不在表里 ——
 *   把失败记成"回了 0 条"就会把那一站判成到底、从此这轮不再问它，而页尾写着「已经到底」。
 * - 三枚剔掉的张数**逐页累加**：只留最后一页的数，页尾那句"排掉 N 张你收藏过的图"就少报了。
 */
internal fun desktopGalleryBatchApply(
    current: DesktopGalleryBatch,
    round: Long,
    pages: Map<GallerySite, Int>,
    returnedBySite: Map<GallerySite, Int>,
    reasons: Map<GallerySite, String>,
    pageSizeOf: (GallerySite) -> Int,
    paged: GalleryForYouMerge.Paged,
): DesktopGalleryBatch {
    if (round != current.seed) return current
    val firstPage = pages.values.all { it == 1 }
    return current.copy(
        pageBySite = current.pageBySite + pages.filterKeys { it in returnedBySite },
        posts = if (firstPage) paged.posts else current.posts + paged.posts,
        seenKeys = current.seenKeys + paged.posts.flatMap { GalleryForYouMerge.keysOf(it) },
        sitesDone = current.sitesDone + GallerySearchMerge.exhaustedSites(returnedBySite, pageSizeOf),
        failures = reasons,
        excludedFavourite = current.excludedFavourite + paged.excludedFavourite,
        droppedUnusable = current.droppedUnusable + paged.droppedUnusable,
        droppedDuplicates = current.droppedDuplicates + paged.droppedDuplicates,
    )
}
