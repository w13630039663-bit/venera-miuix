package com.venera.desktop.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.domain.GalleryLegGuard
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 画师单人页（第十九批 批 19 · D 那一件；第二十一批 批 21 接上续页）。
 *
 * ## 一条口径都不是这一屏定的
 *
 * 查询串、取多少张都转交 [DesktopArtistProfilePolicy]（它自己再转交对岸那颗 `GalleryRankings.searchQuery`），
 * 墙面、守卫、偏好档位与另三面墙同一套件 —— 这一屏新增的只有"目的地是谁"这一件事。
 *
 * ## 今天它是单腿的，而这一点写在屏上
 *
 * 目的地自带一站，所以只有[一条腿](desktopArtistHeadline)：那一站没答上就是今天没有读数，
 * 不能说成"这位没有图"，也不能借多腿那套措辞（页尾那几句每一句都只点名一站）。
 *
 * ## 翻页吃的是共享那颗持有件，桌面这边没有第二本页码账
 *
 * 第几页、去重键、到底三本账全在 [DesktopGalleryBatch] 上（发现屏与搜索屏同一颗），
 * 而"下一笔各腿要第几页"整颗转交 [desktopGalleryBatchNextPages]。
 * 这一屏只有一条腿，手抄一份 `page + 1` 看起来比多腿那两屏更无害 ——
 * 而**发现屏当初也是这么想的**，那一笔的代价是某一页的图永久漏掉（手册 §0 坑 ㉓）。
 */
internal sealed interface DesktopArtistProfileState {
    data object Loading : DesktopArtistProfileState

    /** 整条链出事（判据本身炸了那一类），与"这一站没答上"是两句话。 */
    data class Failed(val message: String) : DesktopArtistProfileState

    /**
     * 交回的是**那颗持有件**而不是一张裸的张数表：续页是往同一屏上**追加**，
     * 谁去过重、谁到底、下一笔第几页都得有人记账 —— 那些都在 [DesktopGalleryBatch] 上。
     */
    data class Ready(val batch: DesktopGalleryBatch) : DesktopArtistProfileState
}

/**
 * 取**一页**并落地成新一颗持有件。第一轮与第 N 轮走的是同一颗函数，
 * 差别只在传进来的 `base` —— 两条路各写一份取数就是"第一页能翻而续页判据漂"的成因。
 *
 * 这一颗只剩"网络那一跳"：该要第几页交 [desktopArtistNextPage]，回来后怎么落地交
 * [desktopArtistBatchAfter]（两颗都不发网络，所以判据 ⑰–⑳ 真跑得到）。
 * 页码、去重键、到底三本账全在 [DesktopGalleryBatch] 上，桌面这边不另造一份（§0 坑 ㉓）。
 */
private suspend fun fetchArtistPage(
    ports: GalleryPorts,
    target: DesktopArtistTarget,
    base: DesktopGalleryBatch,
): Result<DesktopGalleryBatch> = withContext(Dispatchers.IO) {
    runCatching {
        val query = desktopArtistProfileQuery(target, LocalDate.now(ZoneOffset.UTC))
        val page = desktopArtistNextPage(base, target, query)
            // 这一腿已经到底 ⇒ 这一笔没有腿可发。屏上那枚按钮此时本该已经消失了，真走到这里
            // 就把原样交回 —— 重复取同一页会看着"翻得动"而其实一直在换同一批。
            ?: return@runCatching base
        val outcome = GalleryLegGuard.guard(target.site) {
            boardOf(ports, target.site).searchPosts(query, page = page, limit = DESKTOP_ARTIST_SLOT_SIZE)
        }
        desktopArtistBatchAfter(base, target, page, outcome.posts, outcome.answered, outcome.reason)
    }
}

@Composable
internal fun DesktopArtistProfilePane(
    ports: GalleryPorts,
    target: DesktopArtistTarget,
    /** 回到名单那一句。必填、不给默认值 —— 覆盖层与二层目的地都按这条走（缺了它这屏就是出不去）。 */
    onBack: () -> Unit,
) {
    val maskMode by ports.contentGuard.nsfwMaskMode.collectAsState()
    val guardRules by ports.contentGuard.rules.collectAsState()
    val blockAi by ports.prefs.galleryBlockAi.collectAsState()
    val columnMode by ports.prefs.galleryColumnMode.collectAsState()
    val quality by ports.prefs.galleryPreviewQuality.collectAsState()
    val aiBadge by ports.prefs.galleryAiBadge.collectAsState()
    val onPick = LocalDesktopWallPick.current
    val scope = rememberCoroutineScope()

    var state by rememberSaveable(ports, target) {
        mutableStateOf<DesktopArtistProfileState>(DesktopArtistProfileState.Loading)
    }
    // 「下一页」在飞的那一笔。它**不进** `state`：那一枚的档位是"这一屏整体上是什么状态"，
    // 而续页期间第一页的内容还在屏上、只是下面多了一行"正在取下一页"。
    // 这颗相位与发现屏、最新屏同一份（批 23 收成一颗 —— 从前是同一颗抄了两遍）。
    var morePhase by rememberSaveable(target) {
        mutableStateOf<DesktopGalleryMorePhase>(DesktopGalleryMorePhase.Idle)
    }

    LaunchedEffect(ports, target) {
        if (state is DesktopArtistProfileState.Ready) return@LaunchedEffect
        // 第一轮也走那颗 reducer：`queries` 里就是这一屏那一条腿，`atEnd` 与页尾那句都以它为准。
        val base = DesktopGalleryBatch(
            seed = 0L,
            queries = mapOf(target.site to desktopArtistProfileQuery(target, LocalDate.now(ZoneOffset.UTC))),
        )
        state = fetchArtistPage(ports, target, base).fold(
            onSuccess = { DesktopArtistProfileState.Ready(it) },
            onFailure = { DesktopArtistProfileState.Failed("${it::class.java.simpleName}: ${it.message?.take(200)}") },
        )
    }

    val pad = DesktopGalleryMetrics.pageHorizontalPadding
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = pad, end = pad, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onBack) { Text("← 画师名单", fontSize = 13.sp) }
            Text(target.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text(
            desktopArtistHeadline(target),
            color = DesktopTheme.TextTertiary,
            fontSize = 12.5.sp,
            modifier = Modifier.padding(start = pad, end = pad, top = 4.dp),
        )

        Box(Modifier.weight(1f).padding(start = pad, end = pad, top = 8.dp, bottom = 10.dp)) {
            when (val current = state) {
                DesktopArtistProfileState.Loading -> Text(
                    "正在向 ${target.site.displayName} 要「${target.name}」的图…",
                    color = DesktopTheme.TextSecondary,
                    fontSize = 13.sp,
                )

                is DesktopArtistProfileState.Failed -> Text(
                    "这一轮没取成：${current.message} —— 这是取数出了事，与「这一站没答上」是两句话。",
                    color = DesktopTheme.AccentBeni,
                    fontSize = 13.sp,
                )

                is DesktopArtistProfileState.Ready -> {
                    val batch = current.batch
                    val failure = batch.failures[target.site]
                    if (failure != null) {
                        // 没答上就念没答上：这一屏只有一条腿，缺席不能伪装成"这位只有这几张"。
                        Text(
                            desktopArtistFailureReading(target, failure),
                            color = DesktopTheme.AccentBeni,
                            fontSize = 13.sp,
                        )
                    } else if (batch.posts.isEmpty()) {
                        Text(
                            desktopArtistEmptyReading(target),
                            color = DesktopTheme.TextSecondary,
                            fontSize = 13.sp,
                        )
                    } else {
                        val wall = remember(batch.posts, maskMode, guardRules, blockAi) {
                            DesktopGalleryWallPolicy.of(batch.posts, maskMode, blockAi, ports.contentGuard)
                        }
                        Column(Modifier.fillMaxSize()) {
                            Text(
                                desktopWallReadingLine("摆上屏 ${wall.cards.size} 张", "取回 ${batch.posts.size} 张"),
                                color = DesktopTheme.TextTertiary,
                                fontSize = 12.5.sp,
                            )
                            if (wall.cards.isEmpty()) {
                                Text(
                                    "取回了 ${batch.posts.size} 张，全被挡在屏前：" +
                                        desktopWallGuardReading(wall).ifBlank {
                                            "分级与屏蔽规则把这一批全挡下了（当前档位读自本机存档，「设置」那一屏能改它）。"
                                        },
                                    color = DesktopTheme.TextSecondary,
                                    fontSize = 13.sp,
                                )
                            } else {
                                DesktopWallGuardLine(wall)
                                // ⚠️ 这一面墙**不许**被包进纵向滚动里：`LazyVerticalGrid` 收到无限高会当场抛
                                // （整屏崩，不是滚不动）。成因与两种正解见 `DesktopWallScrollNestingTest`。
                                DesktopGalleryPostWall(
                                    wall = wall,
                                    lead = {},
                                    columnMode = columnMode,
                                    quality = quality,
                                    aiBadge = aiBadge,
                                    onPick = onPick,
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                )
                            }
                            // 页尾那一档只有三种话可说：正在取下一页 / 还能再取（那枚钮）/ 那一站到底了。
                            val moreReading = desktopGalleryMoreReading(morePhase, taken = batch.posts.size, unit = "张")
                            if (moreReading != null) {
                                Text(moreReading, color = DesktopTheme.TextSecondary, fontSize = 11.5.sp)
                            }
                            when {
                                // 到底就不摆那枚钮（按不动的按钮比没有按钮更坏，而灰钮是它最省事的写法）。
                                batch.atEnd -> Text(
                                    desktopArtistEndReading(target, taken = batch.posts.size),
                                    color = DesktopTheme.TextTertiary,
                                    fontSize = 11.5.sp,
                                )

                                morePhase !is DesktopGalleryMorePhase.Loading -> Button(
                                    onClick = {
                                        val from = current.batch
                                        morePhase = DesktopGalleryMorePhase.Loading
                                        scope.launch {
                                            fetchArtistPage(ports, target, from).fold(
                                                onSuccess = {
                                                    state = DesktopArtistProfileState.Ready(it)
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
                                ) {
                                    Text(desktopGalleryMoreLabel(batch.posts.size, unit = "张"), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
