package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryLegGuard
import com.venera.compose.gallery.domain.GalleryLegOutcome
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.GallerySearchMerge
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.gallery.domain.GalleryTagFilter
import com.venera.compose.gallery.domain.GalleryDailyFeed
import com.venera.compose.gallery.domain.GalleryForYouMerge
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

private val SearchBudgetMs = GalleryDailyFeed.PER_SITE_TIMEOUT_MS

/**
 * 一笔落地后的三样读数（第一轮与续页同一颗）。
 *
 * `fetchedAtMillis` 在**取完数那一刻**取，不是渲染时现取 —— 否则状态栏那句回答的是
 * "我什么时候看的屏"，而不是"墙上这批是哪一轮拿的"。
 */
internal class DesktopSearchLanding(
    val batch: DesktopGalleryBatch,
    val legs: List<GalleryLegOutcome>,
    val fetchedAtMillis: Long,
)

/**
 * 取**一页**搜索。第一轮与「下一页」走的是同一颗 —— 两条路各写一份取数，
 * 就是"第一页能翻而续页的判据漂"那种形状的成因（发现那一屏同一族，见 `fetchForYouPage`）。
 *
 * 三条转交，一条都不在桌面重述：
 * - 各腿要第几页 → [desktopGalleryBatchNextPages]（里面就是共享面那颗 `nextPagesForAppend`，
 *   缺席的那一站从第 1 页补，不跟着别腿跳页）；
 * - 哪些腿不再问 → [desktopGalleryBatchLegs]（到底的站排除在外）；
 * - 混排、跨页去重、到底判据 → [GallerySearchMerge]，落地成新一颗状态 → [desktopGalleryBatchApply]。
 *
 * ⚠️ `interleave` 包在 `runCatching` 之内是**判据**而不是习惯：它自己在"给货却滤完 0 条"时抛，
 * 那属于判据出错，必须冒到错误页，不能被腿级失败咽成空墙。
 *
 * 失败名单那颗账走 [desktopFailureLedgerAfter]（批 33：这一屏从前自己写的是
 * `base.failures + 这一轮的原因` —— 并集方向反了，于是抖过一次而后来答上的那一站
 * 永久挂在页尾那句「没答上」里）。改成 `internal` 是为了让判据能真跑它（与批 31/32 同一族）。
 */
internal suspend fun fetchSearchPage(
    ports: GalleryPorts,
    base: DesktopGalleryBatch,
): Result<DesktopSearchLanding> = withContext(Dispatchers.IO) {
    runCatching {
        val legsToRun = desktopGalleryBatchLegs(base, base.queries)
        val pages = desktopGalleryBatchNextPages(base, legsToRun.keys.toList())
        val outcomes = coroutineScope {
            legsToRun.map { (site, tags) ->
                val board = boardOf(ports, site)
                site to async {
                    GalleryLegGuard.guard(site) {
                        board.searchPosts(tags, page = pages.getValue(site), limit = board.pageSize)
                    }
                }
            }.map { (site, deferred) -> site to deferred.await() }
        }.toMap()
        val legs = outcomes.values.toList()
        // 合成前先把"没答上的腿"剔掉：它们不参与混排，也不该被算进"到底"
        // （`GallerySearchMerge` 的类注释第 ③ 条：缺席腿当成到底，一次抖动就永久踢掉一站）。
        val answered = outcomes.filterValues { it.answered }.mapValues { it.value.posts }
        val returned = answered.mapValues { (_, posts) -> posts.size }
        val merged = GallerySearchMerge.interleave(answered, base.seenKeys).getOrThrow()
        val landed = desktopGalleryBatchApply(
            current = base,
            round = base.seed,
            pages = pages,
            returnedBySite = returned,
            reasons = desktopFailureLedgerAfter(
                previous = base.failures,
                asked = outcomes.keys,
                fresh = outcomes.mapNotNull { (site, outcome) -> outcome.reason?.let { site to it } }.toMap(),
            ),
            pageSizeOf = { site -> boardOf(ports, site).pageSize },
            // 搜索这一路**不排收藏**（那一档只在「猜你喜欢」上有意义），所以那枚计数恒 0 ——
            // 交一个 0 而不是不给：少一个入参的形状与"这里确实不排"是两件事。
            paged = GalleryForYouMerge.Paged(
                posts = merged.posts,
                perSite = merged.perSite,
                excludedFavourite = 0,
                droppedUnusable = merged.droppedUnusable,
                droppedDuplicates = merged.droppedDuplicates,
            ),
        )
        DesktopSearchLanding(landed, legs, System.currentTimeMillis())
    }
}

/** 来源轴（复用段头那颗分段控件的形状，但数据面是 `GallerySearchSource`，不是 pane 的六项）。 */
internal fun boardOf(ports: GalleryPorts, site: GallerySite) = when (site) {
    GallerySite.YANDERE -> ports.boards.yandere
    GallerySite.GELBOORU -> ports.boards.gelbooru
    GallerySite.SAFEBOORU -> ports.boards.safebooru
    // 到不了这里：桌面的来源档同样由 `GallerySearchSource.options` 拼，而那张表 2026-10-16 起
    // 显式剥掉 pixiv（选型⑤：它只从首页那一格=内容域进，与 booru 的 tags= 语法不同轴，
    // 并进「全部」就是两套语法一张墙。⚠️ 理由不是"匿名搜索不通"——那一跳现量是 200）。
    // 留一枚会说话的抛，而不是给一条 else 悄悄返回 null。
    GallerySite.PIXIV -> throw UnsupportedOperationException(
        "桌面不接 pixiv：它不进来源轴，boardOf 不该收到这一站（调研稿 §八）",
    )
}

/**
 * 来源档 → 分段控件的索引（那颗控件吃索引，而交接载荷交的是**哪一档**）。
 *
 * 换算只留一处，而找不到就**当场说清**而不是钳回 0：`options` 恒由「全部 + 每一站的单站档」
 * 拼成（`GallerySearchSource.kt:92-93`），种子那两颗出处也在同一张表里取，
 * 所以真到了 -1 只能是有人在表外造了一枚来源 —— 那该炸，而不是悄悄落到「全部」再让用户以为
 * "我点的是那一站"。
 */
private fun sourceIndexOf(sources: List<GallerySearchSource>, source: GallerySearchSource): Int =
    sources.indexOfFirst { it == source }.also {
        require(it >= 0) { "来源轴里没有「$source」这一档 —— 交接载荷是在表外造的来源" }
    }

/** 补全候选最多摆几枚。取 8 是"一行放得下"的那一档，不是从一个更大的数里砍下来。 */
private const val SUGGESTION_ROWS = 8

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Failed(val reason: String) : SearchState

    /** 标签数超出某站预算 —— **我们自己拦下的**，不是站方 422。 */
    data class Blocked(val reason: String) : SearchState

    data class Ready(
        /** 累计的那批卡 + 各腿页码 + 到底的站 + 逐站点名的原因（第十三批 批 8 起这一屏也用它）。 */
        val batch: DesktopGalleryBatch,
        /** **最近一笔**各腿的落点：状态栏那句"几站没答上"读的是它，累计的账在 [batch] 里。 */
        val legs: List<GalleryLegOutcome>,
        val fetchedAtMillis: Long,
    ) : SearchState
}
