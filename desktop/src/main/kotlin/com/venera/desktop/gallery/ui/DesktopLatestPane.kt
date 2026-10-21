package com.venera.desktop.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryForYouMerge
import com.venera.compose.gallery.domain.GalleryLegGuard
import com.venera.compose.gallery.domain.GallerySearchMerge
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface DesktopLatestState {
    data object Loading : DesktopLatestState

    /** 整条链出事（判据本身炸了那一类），与"某一站没答上"是两句话。 */
    data class Failed(val message: String) : DesktopLatestState

    data class Ready(
        /** 累计到这一轮各腿最深那一页的那一批（页尾那几句全部从它念，桌面不另记张数账）。 */
        val batch: DesktopGalleryBatch,
        /** 发车前就没发的腿（Gelbooru 没配凭据那一档）。 */
        val silent: Map<GallerySite, String>,
        val fetchedAtMillis: Long,
    ) : DesktopLatestState
}

/**
 * 把「最新」的一页落地成新一颗持有件（**纯函数**：不发网络，所以判据能真跑而不是扫字）。
 *
 * 三处判据一律转交：跨站混排 + 跨页去重 → `GallerySearchMerge.interleave`（第二入参就是上一批
 * 已上屏的去重键，漏传同一张会摆两遍）；到底/页码/张数累加 → [desktopGalleryBatchApply]。
 *
 * ⚠️ `interleave` 那一句在 [runCatching] 之外（调用方那一颗之内）是**判据**：它在"给货却滤完 0 条"
 * 时抛，那属于判据出错，必须冒到错误页，不能被腿级失败咽成空墙。
 */
internal fun desktopWallBatchAfter(
    base: DesktopGalleryBatch,
    pages: Map<GallerySite, Int>,
    answered: Map<GallerySite, List<GalleryPost>>,
    reasons: Map<GallerySite, String>,
    pageSizeOf: (GallerySite) -> Int,
): DesktopGalleryBatch {
    val merged = GallerySearchMerge.interleave(answered, base.seenKeys).getOrThrow()
    return desktopGalleryBatchApply(
        current = base,
        round = base.seed,
        pages = pages,
        // 只有真答上的腿进这张表：给没答上那一站记一页，就是把"永久漏掉那一页"写进账本。
        returnedBySite = answered.mapValues { (_, posts) -> posts.size },
        reasons = reasons,
        pageSizeOf = pageSizeOf,
        // 最新这一路**不排收藏**（那一档只在「猜你喜欢」上有意义），所以那枚计数恒 0 ——
        // 交一个 0 而不是不给：少一个入参的形状与"这里确实不排"是两件事。
        paged = GalleryForYouMerge.Paged(
            posts = merged.posts,
            perSite = merged.perSite,
            excludedFavourite = 0,
            droppedUnusable = merged.droppedUnusable,
            droppedDuplicates = merged.droppedDuplicates,
        ),
    )
}

/**
 * 取**一页**最新：第一轮与「下一页」同一颗（发现、搜索、单人页同一族）。
 *
 * 两条路各写一份取数，就是"第一页能翻而续页的判据漂"那种形状的成因。三处转交：
 * 哪些腿不再问 → [desktopGalleryBatchLegs]（到底的站排除在外）；各腿要第几页 →
 * [desktopGalleryBatchNextPages]（缺席的那一站从第 1 页补，不跟着别腿跳页）；怎么落地 → [desktopWallBatchAfter]。
 */
internal suspend fun fetchWallPage(
    ports: GalleryPorts,
    base: DesktopGalleryBatch,
    legs: Map<GallerySite, String>,
): Result<DesktopGalleryBatch> = withContext(Dispatchers.IO) {
    runCatching {
        val runnable = desktopGalleryBatchLegs(base, legs)
        val pages = desktopGalleryBatchNextPages(base, runnable.keys.toList())
        val outcomes = coroutineScope {
            runnable.map { (site, query) ->
                val board = boardOf(ports, site)
                site to async {
                    GalleryLegGuard.guard(site) {
                        board.searchPosts(query, page = pages.getValue(site), limit = board.pageSize)
                    }
                }
            }.map { (site, deferred) -> site to deferred.await() }
        }.toMap()
        // 没答上的腿不参与混排，也不许被算进"到底"（一次抖动就永久踢掉站是错的）。
        val answered = outcomes.filterValues { it.answered }.mapValues { it.value.posts }
        // 缺席名单**以这一轮的答案为准**（规则本身住在 [desktopFailureLedgerAfter]，四处共用一颗）：
        // 这一轮答上的那一站，上一轮那句"没答上"必须一起销掉，否则页尾会一边摆着它的图、
        // 一边说它没答上（第三十一批 批 31 的 fake 端口第一次真跑抓到的）。
        val reasons = desktopFailureLedgerAfter(
            previous = base.failures,
            asked = outcomes.keys,
            fresh = outcomes.mapNotNull { (site, outcome) -> outcome.reason?.let { site to it } }.toMap(),
        )
        desktopWallBatchAfter(base, pages, answered, reasons) { site -> boardOf(ports, site).pageSize }
    }
}

