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

/**
 * 图库域「最新」那一行的目的地（第十批 ⑤ 把它点亮，第二十三批 批 23 接上续页）。
 *
 * ## 取数链一条都是现成的
 *
 * | 事 | 出处（桌面一行判据都不重写） |
 * |---|---|
 * | 该发哪几条腿、没发的逐座点名 | [desktopWallLegs] / [desktopWallSilentSites] |
 * | 每站一笔时间预算 + 缺席原因不吞 | `GalleryLegGuard.guard`（与日榜、搜索、发现同一份） |
 * | 跨站保序混排 + 跨页去重 + 滤不可上屏 | `GallerySearchMerge.interleave` |
 * | 第几页、哪些站到底、去重键累计到哪儿 | [DesktopGalleryBatch] 那颗持有件（与发现、搜索、单人页同一颗） |
 * | 墙上过不过分级与屏蔽 | `DesktopGalleryWallPolicy.of`（第四批那一层） |
 * | 卡面取哪一档、几列、AI 角标 | 第五批那三枚偏好，与另三面墙同一套档 |
 *
 * ## 「最新」= 站方默认序，桌面一行都不排
 *
 * 查询串恒为空串（[DESKTOP_LATEST_QUERY]）。两站的默认序实测就是最新在前，
 * 而本地再排一次排出来的是"三站第一页里的前 N 条"，那不是最新 —— 判据 ④ 把本地排序判死。
 *
 * ## 批 23 之前屏上那句是假的
 *
 * 页尾那一句从前写着"往下翻还没有（因为桌面缺那一层状态）"，而那一层状态件早就立着、
 * 另三屏都在吃 —— 这是手册 §22.3 那条"能力接上了而那句『还没有』还活着"的**镜像**：
 * 判据全绿而屏上说话是假的。修法只有两条（把那句改成实话 / 把那一屏接上），本批接上，
 * 因为缺的从来是接线而不是能力。
 */
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

@Composable
internal fun DesktopLatestPane(ports: GalleryPorts) {
    val maskMode by ports.contentGuard.nsfwMaskMode.collectAsState()
    val guardRules by ports.contentGuard.rules.collectAsState()
    val blockAi by ports.prefs.galleryBlockAi.collectAsState()
    val columnMode by ports.prefs.galleryColumnMode.collectAsState()
    val quality by ports.prefs.galleryPreviewQuality.collectAsState()
    val aiBadge by ports.prefs.galleryAiBadge.collectAsState()
    // 点卡的接收者住在应用根（选中态只有一颗），这里只取句柄。
    val onPick = LocalDesktopWallPick.current
    val scope = rememberCoroutineScope()

    // ⚠️ 这里**不许**写 `?: return`：接线没给桌面凭据件时那是一条静默的空屏 ——
    // 用户看到的是"这一域什么都没有"，而真相是"接线缺一件"。缺件要说出来。
    val credentials = ports.credentials as? DesktopGalleryCredentials
    if (credentials == null) {
        Text(
            "这一屏需要桌面凭据件（判断 Gelbooru 配没配），当前接线没给：${ports.credentials::class.java.simpleName}",
            color = DesktopTheme.AccentBeni,
            fontSize = 13.sp,
            modifier = Modifier.padding(DesktopGalleryMetrics.pageHorizontalPadding),
        )
        return
    }
    val identity by credentials.gelbooru.identity.collectAsState()
    val hasGelbooru = identity != null

    var round by rememberSaveable { mutableStateOf(0) }
    // inputs 与下面那枚取数 effect 的 key **同一组**（批 5）：这样"Ready"才等价于"为这一组输入取到手"，
    // 那道跳过守卫才不会把该重取的那一笔挡掉。从前这里少一枚 `ports`，是"看起来一样"而不是"一样"。
    var state by rememberSaveable(ports, hasGelbooru, round) {
        mutableStateOf<DesktopLatestState>(DesktopLatestState.Loading)
    }
    // 「下一页」在飞的那一笔吃那颗共享相位（批 23：发现、单人页、最新三屏同一颗）。
    var morePhase by rememberSaveable(round) {
        mutableStateOf<DesktopGalleryMorePhase>(DesktopGalleryMorePhase.Idle)
    }

    LaunchedEffect(ports, hasGelbooru, round) {
        if (state is DesktopLatestState.Ready) return@LaunchedEffect
        val legs = desktopWallLegs(hasGelbooru).associateWith { DESKTOP_LATEST_QUERY }
        val silent = desktopWallSilentSites(hasGelbooru)
        // 第一轮也由那颗持有件记账：`queries` 里就是这一屏那几条腿，`atEnd` 与页尾那句都以它为准。
        val base = DesktopGalleryBatch(seed = 0L, queries = legs)
        state = fetchWallPage(ports, base, legs).fold(
            onSuccess = {
                DesktopLatestState.Ready(
                    batch = it,
                    silent = silent,
                    // 时刻戳在**取完数那一刻**取，不是渲染时现取 —— 否则那句话回答的是
                    // "我什么时候看的屏"，而不是"墙上这批是哪一轮拿的"。
                    fetchedAtMillis = System.currentTimeMillis(),
                )
            },
            onFailure = { DesktopLatestState.Failed("${it::class.java.simpleName}: ${it.message?.take(200)}") },
        )
    }

    val pad = DesktopGalleryMetrics.pageHorizontalPadding
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(start = pad, end = pad, top = 10.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("最新", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "各站按它自己的默认序给一整页 · 桌面不重排",
                color = DesktopTheme.TextTertiary,
                fontSize = 12.5.sp,
            )
            Button(onClick = { round += 1 }) { Text("重取", fontSize = 13.sp) }
        }

        Box(Modifier.weight(1f).padding(start = pad, end = pad, bottom = 10.dp)) {
            when (val current = state) {
                DesktopLatestState.Loading -> Text(
                    "正在向各站要第一批…",
                    color = DesktopTheme.TextSecondary,
                    fontSize = 13.sp,
                )

                is DesktopLatestState.Failed -> Text(
                    "这一轮没取成：${current.message} —— 这是取数出了事，与「某一站没答上」是两句话。",
                    color = DesktopTheme.AccentBeni,
                    fontSize = 13.sp,
                )

                is DesktopLatestState.Ready -> {
                    val batch = current.batch
                    Column(Modifier.fillMaxSize()) {
                        val wall = remember(batch.posts, maskMode, guardRules, blockAi) {
                            DesktopGalleryWallPolicy.of(batch.posts, maskMode, blockAi, ports.contentGuard)
                        }
                        if (wall.cards.isEmpty()) {
                            Text(
                                "这一轮取回了 ${batch.posts.size} 张，全被挡在屏前：" +
                                    desktopWallGuardReading(wall).ifBlank { "没有能上屏的条目" },
                                color = DesktopTheme.TextSecondary,
                                fontSize = 13.sp,
                            )
                        } else {
                            // ⚠️ 这一面墙**不许**被包进纵向滚动里：`LazyVerticalGrid` 收到无限高会当场抛
                            // （整屏崩，不是滚不动）。成因与两种正解见 `DesktopWallScrollNestingTest`。
                            DesktopGalleryPostWall(
                                wall = wall,
                                // 段头（标题 +「重取」）要常驻，不进滚动容器。
                                lead = {},
                                columnMode = columnMode,
                                quality = quality,
                                aiBadge = aiBadge,
                                // 点卡的接收者住在应用根（选中态只有一颗），这里只取句柄并**显式传给墙**。
                                onPick = onPick,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            )
                        }
                        DesktopLatestReadout(
                            state = current,
                            wall = wall,
                            morePhase = morePhase,
                            onMore = {
                                val from = current.batch
                                morePhase = DesktopGalleryMorePhase.Loading
                                val legs = from.queries
                                scope.launch {
                                    fetchWallPage(ports, from, legs).fold(
                                        onSuccess = {
                                            state = DesktopLatestState.Ready(it, current.silent, System.currentTimeMillis())
                                            morePhase = DesktopGalleryMorePhase.Idle
                                        },
                                        onFailure = {
                                            morePhase = DesktopGalleryMorePhase.Failed(
                                                "${it::class.java.simpleName}: ${it.message?.take(200)}",
                                            )
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 页尾那几句读数。次序是刻意的：**先说少了谁**（发不出去比取到多少更要紧），再说计数，最后才是翻页。
 *
 * ⚠️ 每一句都是"有内容才说"，但**不许因为没内容就不留判据**：
 * 一站没答上时必须点名是哪一站、为什么 —— 三源静默退化成两源是这一屏最难发现的一种错。
 */
@Composable
private fun DesktopLatestReadout(
    state: DesktopLatestState.Ready,
    wall: DesktopWall,
    morePhase: DesktopGalleryMorePhase,
    onMore: () -> Unit,
) {
    val batch = state.batch
    val taken = batch.posts.size
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val missing = desktopMissingReading(batch.queries.keys, state.silent + batch.failures)
        if (missing != null) {
            Text(
                missing,
                color = DesktopTheme.AccentBeni,
                fontSize = 11.5.sp,
            )
        }
        // 各站条数 / 滤掉的 / 视频的，三段各可能为空 —— 上一版把各站那一段**无条件** add 进列表，
        // 于是它为空时屏上读到的是**开头**悬着的一枚「 · 」（而 `counts.isNotEmpty()` 恒真，挡不住）。
        val counts = desktopWallReading(
            desktopPerSiteReading(batch.posts.groupingBy { it.site }.eachCount(), unit = "条"),
            if (batch.droppedDuplicates > 0) "去掉 ${batch.droppedDuplicates} 张跨站重复的" else null,
            if (batch.droppedUnusable > 0) "滤掉 ${batch.droppedUnusable} 张不能上屏的" else null,
            batch.posts.count { it.isVideo }.takeIf { it > 0 }?.let { "其中 $it 张是视频条目，只摆静帧" },
        )
        if (counts.isNotEmpty()) {
            Text(counts, color = DesktopTheme.TextTertiary, fontSize = 11.5.sp)
        }
        DesktopWallGuardLine(wall)
        // 翻页那一档：正在取 / 失败 → 页尾那句；没到底 → 那枚按钮；到底 → 点名哪几站给完了。
        val moreReading = desktopGalleryMoreReading(morePhase, taken = taken, unit = "条")
        if (moreReading != null) {
            Text(
                moreReading,
                color = if (morePhase is DesktopGalleryMorePhase.Failed) DesktopTheme.AccentBeni else DesktopTheme.TextSecondary,
                fontSize = 11.5.sp,
            )
        }
        when {
            // 到底就不摆那枚钮（按不动的按钮比没有按钮更坏，而灰钮是它最省事的写法）。
            batch.atEnd -> Text(
                desktopExhaustedReading(batch.sitesDone.map { it.displayName }).orEmpty(),
                color = DesktopTheme.TextTertiary,
                fontSize = 11.5.sp,
            )

            // 失败那一档**照旧摆那枚钮**（按下去就是重试）：页尾那句已经说了没取成、
            // 而已经摆出来的那些一张不少 —— 再补一枚「再取一页」就是把同一件事说两遍。
            morePhase !is DesktopGalleryMorePhase.Loading -> Button(onClick = onMore) {
                Text(desktopGalleryMoreLabel(taken, unit = "条"), fontSize = 13.sp)
            }
        }
    }
}
