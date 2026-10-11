package com.venera.desktop.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
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
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/**
 * 图库域「排行榜」那一行的目的地（桌面第二十八批 批 28 把它点亮）。
 *
 * ## 这一屏自己定不了任何一件事
 *
 * 六档、每档的窗口、哪一站兑现到哪一档、发出去那一串 —— 全部转交 [DesktopRankingPolicy.kt] 里那几颗，
 * 而它们又逐颗转交共享面的 `GalleryRankings`。这一屏新写的只有两样：档行怎么摆、页尾念哪几句。
 *
 * ## 换档 = 换一轮（不是往同一批上追加）
 *
 * 取数状态那三本账（页码 / 去重键 / 到底名单）都住在 [DesktopGalleryBatch] 里，而它的**作废闸**是种子：
 * 这里把 `landing` 放进 effect 的 key，换档那一刻 `rememberSaveable(…, landing, …)` 交回一颗新的
 * `Loading`，于是上一档的账不会混进这一档（"点了周榜、屏上混着昨天的最新"就是这么来的）。
 *
 * ## 历史期回看（批 29 补上那一层，但不画日历）
 *
 * 共享面开着三条判据（`supportsHistory` 只对月/年放开、`supportsPeriodPickerAll` 要求每一条腿都给得出窗口、
 * `windowBounds` 把锚点夹回今天）。桌面这一侧**不新造控件层**：只摆「上一期 / 下一期 / 回到本期」三枚，
 * 且只在上面那两条判据为真时摆 —— 不给那一层时屏上要念出为什么少（"没摆"与"判过而不给"是两句话）。
 *
 * ## 形参 [seed]：那一层从此拍得到（批 30）
 *
 * 三枚按钮是"把档拨到月/年才出现的那一层"，而鼠标自验是禁的（手册 §8）—— 批 29 的判据全绿，
 * 图证那一格只能写"没拍成"。这颗种子把"拨到哪一档、哪一期"变成命令行上的读数
 * （`--ranking=MONTH/2024-03`），于是同一批判据第一次能配上帧。
 * 它**只作两枚初值**：档与期各自走 [desktopRankingSeedRanking] / [desktopRankingSeedAnchor]，
 * 而这一屏真发出去哪一档仍由 [desktopRankingLanding] 判 —— 种子不越过兑现判据（带 Gelbooru 的
 * 那一组腿里种月档会照原样落回默认档，并且屏上念出落回那一句）。
 */
internal sealed interface DesktopRankingState {
    data object Loading : DesktopRankingState

    /** 整条链出事，与"某一站没答上"是两句话。 */
    data class Failed(val message: String) : DesktopRankingState

    data class Ready(
        val batch: DesktopGalleryBatch,
        /** 这一轮**真发出去**的那一档（可能与用户点的那一档不同 —— 不同就必须出声）。 */
        val landing: GalleryRanking,
        /**
         * 这一批是**哪一期**取的那一句（`本期 2026-10-01 ~ 2026-10-07` / `2024-03 …`；不设窗口的档为空串）。
         *
         * 存句子而不是存锚点：它与 `fetchedAtMillis` 同一刻算出，于是那句话回答的是
         * "墙上这批是哪一期"，而不是"我现在把下拉停在哪一期"。
         */
        val periodReading: String,
        /** 发车前就没发的腿（Gelbooru 没配凭据那一档）。 */
        val silent: Map<GallerySite, String>,
        val fetchedAtMillis: Long,
    ) : DesktopRankingState
}

@Composable
internal fun DesktopRankingPane(ports: GalleryPorts, seed: DesktopRankingSeed?) {
    val maskMode by ports.contentGuard.nsfwMaskMode.collectAsState()
    val guardRules by ports.contentGuard.rules.collectAsState()
    val blockAi by ports.prefs.galleryBlockAi.collectAsState()
    val columnMode by ports.prefs.galleryColumnMode.collectAsState()
    val quality by ports.prefs.galleryPreviewQuality.collectAsState()
    val aiBadge by ports.prefs.galleryAiBadge.collectAsState()
    val onPick = LocalDesktopWallPick.current
    val scope = rememberCoroutineScope()

    // ⚠️ 这里**不许**写 `?: return`：接线没给桌面凭据件时那是一条静默的空屏（用户读成"这一域什么都没有"，
    // 而真相是"接线缺一件"）。缺件要说出来 —— 与「最新」那一屏同一句形状。
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

    // "本期"取一次就够：每帧现取会让窗口读数跟着秒针飘，而站方那一路按 UTC 日切。
    val todayUtc = remember { LocalDate.now(ZoneOffset.UTC) }
    val legs = desktopWallLegs(hasGelbooru)
    val choices = desktopRankingChoices(legs, todayUtc)
    val hidden = desktopRankingHidden(legs)

    var selected by rememberSaveable { mutableStateOf(desktopRankingSeedRanking(seed)) }
    var round by rememberSaveable { mutableStateOf(0) }
    // 兑现不了就落回默认档，而那一句必须上屏（静默落回 = 点按周看到最新）。
    val landing = desktopRankingLanding(legs, selected)
    val note = desktopRankingNote(selected, landing)
    // 历史期锚点（批 29）：null = 本期。跟着档走 —— 换档那一刻它自己落回 null（判据 ⑥⑦）。
    var anchor by rememberSaveable(landing) {
        mutableStateOf<LocalDate?>(desktopRankingSeedAnchor(seed, landing))
    }
    // 开不开"回看历史期"那一层，问的是这一组腿 + 这一档（两条判据都在共享面）。
    val historyOpen = desktopRankingHistoryAllowed(legs, landing)
    // 逐腿的查询串：两站的分数串写法不同，所以**每一腿各算一次**，不给一个全局串。
    val queries = legs.associateWith { desktopRankingQuery(it, landing, anchor, todayUtc) }

    // inputs 与下面那枚取数 effect 的 key **同一组**（批 5）：这样"Ready"才等价于"为这一组输入取到手"。
    // 锚点也在这一组里 ⇒ **换期 = 换一轮**：上一期的页码账、去重键与到底名单都不该跟过来。
    var state by rememberSaveable(ports, hasGelbooru, landing, anchor, round) {
        mutableStateOf<DesktopRankingState>(DesktopRankingState.Loading)
    }
    var morePhase by rememberSaveable(landing, anchor, round) {
        mutableStateOf<DesktopGalleryMorePhase>(DesktopGalleryMorePhase.Idle)
    }

    LaunchedEffect(ports, hasGelbooru, landing, anchor, round) {
        if (state is DesktopRankingState.Ready) return@LaunchedEffect
        val base = DesktopGalleryBatch(seed = 0L, queries = queries)
        state = fetchWallPage(ports, base, queries).fold(
            onSuccess = {
                DesktopRankingState.Ready(
                    batch = it,
                    landing = landing,
                    periodReading = desktopRankingAnchorReading(landing, anchor, todayUtc),
                    silent = desktopWallSilentSites(hasGelbooru),
                    fetchedAtMillis = System.currentTimeMillis(),
                )
            },
            onFailure = { DesktopRankingState.Failed("${it::class.java.simpleName}: ${it.message?.take(200)}") },
        )
    }

    val pad = DesktopGalleryMetrics.pageHorizontalPadding
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(start = pad, end = pad, top = 10.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("排行榜", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            // 档行：只摆这一组腿真兑现的那几档（摆不出来的下一行逐档点名是谁拦的）。
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                choices.forEach { choice ->
                    val picked = choice.ranking == selected
                    Button(onClick = { selected = choice.ranking; anchor = null }) {
                        Text(
                            choice.label,
                            fontSize = 13.sp,
                            fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            // 期次那一行：三枚按钮只在共享那两条判据说"这一组腿这一档给得出窗口"时摆。
            if (historyOpen) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            desktopRankingStep(landing, anchor, backwards = true, todayUtc = todayUtc)?.let { anchor = it }
                        },
                    ) { Text("上一期", fontSize = 13.sp) }
                    Button(
                        onClick = { anchor = desktopRankingStep(landing, anchor, backwards = false, todayUtc = todayUtc) },
                    ) { Text("下一期", fontSize = 13.sp) }
                    Button(onClick = { anchor = null }) { Text("回到本期", fontSize = 13.sp) }
                }
            }
            val reading = desktopRankingAnchorReading(landing, anchor, todayUtc)
            Text(
                desktopWallReading(
                    reading.ifBlank { "这一档不设时间窗口，也就没有期次" },
                    if (historyOpen && anchor != null) "这是回看的历史期，不是本期" else null,
                    note?.let { "注意：$it" },
                ),
                color = if (note != null || anchor != null) DesktopTheme.AccentBeni else DesktopTheme.TextTertiary,
                fontSize = 12.5.sp,
            )
            // 不给那一层时也要说清为什么少 —— "没摆"与"判过而不给"是两句话。
            if (!historyOpen && landing != GalleryRanking.NEWEST) {
                Text(
                    "这一档不给回看历史期：要么它不设时间窗口，要么这一组腿里有一站给不出窗口",
                    color = DesktopTheme.TextTertiary,
                    fontSize = 11.5.sp,
                )
            }
            if (hidden.isNotEmpty()) {
                Text(
                    desktopWallReading(
                        hidden.entries.joinToString("；") { (ranking, blockers) ->
                            "「${ranking.label}」摆不出——${blockers.joinToString("、") { it.displayName } + "认不了时间窗口" }"
                        },
                    ),
                    color = DesktopTheme.TextTertiary,
                    fontSize = 11.5.sp,
                )
            }
            Button(onClick = { round += 1 }) { Text("重取", fontSize = 13.sp) }
        }

        Box(Modifier.weight(1f).padding(start = pad, end = pad, bottom = 10.dp)) {
            when (val current = state) {
                DesktopRankingState.Loading -> Text(
                    "正在向各站要第一批…",
                    color = DesktopTheme.TextSecondary,
                    fontSize = 13.sp,
                )

                is DesktopRankingState.Failed -> Text(
                    "这一轮没取成：${current.message} —— 这是取数出了事，与「某一站没答上」是两句话。",
                    color = DesktopTheme.AccentBeni,
                    fontSize = 13.sp,
                )

                is DesktopRankingState.Ready -> {
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
                            // ⚠️ 这一面墙**不许**被包进纵向滚动里（`LazyVerticalGrid` × 无限高 = 整屏崩）。
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
                        DesktopRankingReadout(
                            state = current,
                            wall = wall,
                            morePhase = morePhase,
                            onMore = {
                                val from = current.batch
                                morePhase = DesktopGalleryMorePhase.Loading
                                scope.launch {
                                    fetchWallPage(ports, from, from.queries).fold(
                                        onSuccess = {
                                            state = DesktopRankingState.Ready(
                                                it,
                                                current.landing,
                                                current.periodReading,
                                                current.silent,
                                                System.currentTimeMillis(),
                                            )
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
 * 页尾那几句：先说少了谁，再说这一榜数的是哪一批张，最后才是翻页。
 *
 * 单位是「张」不是「条」—— 这一屏数的是墙真摆出来的那些张（批 13 定的那一份主语）。
 */
@Composable
private fun DesktopRankingReadout(
    state: DesktopRankingState.Ready,
    wall: DesktopWall,
    morePhase: DesktopGalleryMorePhase,
    onMore: () -> Unit,
) {
    val batch = state.batch
    val taken = batch.posts.size
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val missing = desktopMissingReading(batch.queries.keys, state.silent + batch.failures)
        if (missing != null) {
            Text(missing, color = DesktopTheme.AccentBeni, fontSize = 11.5.sp)
        }
        Text(
            desktopWallReading(
                "「${state.landing.label}」这一榜进屏 $taken 张",
                state.periodReading.takeIf { it.isNotBlank() },
            ),
            color = DesktopTheme.TextSecondary,
            fontSize = 11.5.sp,
        )
        val counts = desktopWallReading(
            desktopPerSiteReading(batch.posts.groupingBy { it.site }.eachCount(), unit = "张"),
            if (batch.droppedDuplicates > 0) "去掉 ${batch.droppedDuplicates} 张跨站重复的" else null,
            if (batch.droppedUnusable > 0) "滤掉 ${batch.droppedUnusable} 张不能上屏的" else null,
        )
        if (counts.isNotEmpty()) {
            Text(counts, color = DesktopTheme.TextTertiary, fontSize = 11.5.sp)
        }
        DesktopWallGuardLine(wall)
        val moreReading = desktopGalleryMoreReading(morePhase, taken = taken, unit = "张")
        if (moreReading != null) {
            Text(
                moreReading,
                color = if (morePhase is DesktopGalleryMorePhase.Failed) DesktopTheme.AccentBeni else DesktopTheme.TextSecondary,
                fontSize = 11.5.sp,
            )
        }
        when {
            // 到底就不摆那枚钮（按不动的按钮比没有按钮更坏）。
            batch.atEnd -> Text(
                desktopExhaustedReading(batch.sitesDone.map { it.displayName }).orEmpty(),
                color = DesktopTheme.TextTertiary,
                fontSize = 11.5.sp,
            )

            morePhase !is DesktopGalleryMorePhase.Loading -> Button(onClick = onMore) {
                Text(desktopGalleryMoreLabel(taken, unit = "张"), fontSize = 13.sp)
            }
        }
    }
}
