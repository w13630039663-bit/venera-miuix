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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryForYouMerge
import com.venera.compose.gallery.domain.GalleryLegGuard
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * rail「发现」那一域的目的地（第九批转正，第十批 ③ 接上续页）。
 *
 * ## 这一屏的取数链一条都是现成的
 *
 * | 事 | 出处（全在桌面编译面内，桌面一行判据都不重写） |
 * |---|---|
 * | 每站抽哪几个标签、抽不出算哪一档 | [desktopForYouPlan] → `GalleryRecommendations.recommendBySite` |
 * | 标签集 → 发出去的那一串 | `GallerySearch.queryOf`（经计划那颗） |
 * | 每站一笔时间预算 + 缺席原因不吞 | `GalleryLegGuard.guard`（与日榜、搜索同一份） |
 * | 混摆、剔已收藏、滤不可上屏、跨页去重 | `GalleryForYouMerge.page` |
 * | 第几页 / 哪些站到底 / 去重键累计 | [DesktopGalleryBatch]（第十批 ③ 立的这颗持有件） |
 * | 墙上过不过分级与屏蔽 | `DesktopGalleryWallPolicy.of`（第四批那一层） |
 * | 卡面取哪一档、几列、AI 角标 | 第五批那三枚偏好，与另两面墙同一套档 |
 *
 * ## 这一屏**仍然**刻意没做的一件事，在屏上点名
 *
 * **跨进程沿用上次那一屏**：快照那颗件 `GalleryForYouCache` 吃 `Context`，
 * 在 `desktop/build.gradle.kts` 的排除清单里（摘它的两条判据在那儿写着）。
 * ⚠️ 从前这里与页尾那句都写着"每…都重抽"，而批 5 之后那是假的：换域换回来还是同一批
 * （种子与取数态都在 `rememberSaveable` 上，由那一层 `SaveableStateProvider` 按"域 + 行"定域）。
 * 今天真的只剩"关掉程序再开没有那份快照可沿用"，页尾那句按这一档说。
 *
 * ⚠️ 从前这里还点名第二件"没做"的是**续页**。第十批 ③ 把它接上了，所以那句撤掉 ——
 * 本仓最容易留的一种假读数就是"能力接上了而那句'还没有'还活着"（手册 §22.3 那条活了四批的同类）。
 *
 * ## 种子归这一屏自己持有，且**换一批是真换**
 *
 * 种子在 `rememberSaveable` 里生成：同一屏内重组不换（否则墙会自己换序，那是"导航条目会重建组合"那一族），
 * 而**换域换回来也不换**（批 5 那一层，`DesktopDomainStateMemoryTest` ④ 钉着它）——
 * 只有按「换一批」那枚钮与冷启动才换，页尾那句说的是后一种。
 * 「换一批」那枚按钮换的就是这颗种子，而计划那颗的用例 ⑤ 钉着"换种子必换串"，所以它不是假按钮。
 * ⚠️ 换种子同时**换新的一颗** [DesktopGalleryBatch]：页码、去重键、到底清单三样必须跟着清零，
 * 漏一枚的形状是"换了批次却像没换"（去重键没清 ⇒ 新一批被整批剔空）或
 * "第一笔按第 2 页追加进新批次"（页码没清）。
 */
internal sealed interface DesktopForYouState {
    /** 正在取第一轮。 */
    data object Loading : DesktopForYouState

    /** 一条都发不出去（没收藏 / 标签全被滤掉），那句由计划那颗给。 */
    data class Blank(val reason: String) : DesktopForYouState

    /** 判据或合成出了事 —— 与"某一站没答上"是两件事，不许互相替。 */
    data class Failed(val message: String) : DesktopForYouState

    /**
     * 取到了。[batch] 里是**累计到这一轮第 page 页**的那一批，
     * 页尾那几句读数（发出去的串、没答上的站、剔掉了几张）全部从它念。
     */
    data class Ready(val batch: DesktopGalleryBatch) : DesktopForYouState
}

// 「下一页」那一笔的相位住在 DesktopGalleryMorePhase（批 23 收成一颗：它不进 DesktopGalleryBatch，
// 那是 UI 的等待形状，不是这一批的数据 —— 发现、单人页、最新三屏吃同一颗）。

@Composable
internal fun DesktopForYouPane(ports: GalleryPorts) {
    val favorites by ports.favorites.favorites.collectAsState()
    val maskMode by ports.contentGuard.nsfwMaskMode.collectAsState()
    val guardRules by ports.contentGuard.rules.collectAsState()
    val blockAi by ports.prefs.galleryBlockAi.collectAsState()
    val columnMode by ports.prefs.galleryColumnMode.collectAsState()
    val quality by ports.prefs.galleryPreviewQuality.collectAsState()
    val aiBadge by ports.prefs.galleryAiBadge.collectAsState()
    // 点卡的接收者住在应用根（选中态只有一颗），这里只取句柄并**显式传给墙**。
    val onPick = LocalDesktopWallPick.current

    // Gelbooru 配没配：既是"发不发车"的判据，也是 UI 形状的判据。
    // ⚠️ 这里**不许**写 `?: return`：接线没给桌面凭据件时那是一条静默的空屏 ——
    // 用户看到的是"这一域什么都没有"，而真相是"接线缺一件"。缺件要说出来。（与搜索那一屏同一条。）
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

    var seed by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    // 黑名单那一问与 Android 那侧同一口径：`blockedGalleryRule(author = "", tags = listOf(tag))`。
    // `guardRules` 只当 remember 的键（改了规则要重抽，但不必重发网络已经够 —— 墙那一层会自己重算）。
    val plan = remember(favorites, guardRules, seed) {
        desktopForYouPlan(favorites, seed) { tag ->
            ports.contentGuard.blockedGalleryRule(author = "", tags = listOf(tag)) != null
        }
    }

    // ⚠️ 这一屏的 state 与下面那枚取数 effect 吃**同一组 inputs**（批 5）：从前 effect 的 key 是 `plan`，
    // 而 `DesktopForYouPlan` 是普通 class（不是 data class）⇒ 换回来时那是一份新对象、按身份比较就是"变了"，
    // 状态会被重掷成 Loading、那一笔会重发。改成喂它的那几样（种子 + 收藏 + 屏蔽规则 + 有没有 Gelbooru），
    // 那几样才是"这一轮"的真实身份；`plan` 仍然从它们现算，只是不再当 key。
    var state by rememberSaveable(seed, favorites, guardRules, hasGelbooru) {
        mutableStateOf<DesktopForYouState>(DesktopForYouState.Loading)
    }
    var morePhase by rememberSaveable(seed) { mutableStateOf<DesktopGalleryMorePhase>(DesktopGalleryMorePhase.Idle) }
    val scope = rememberCoroutineScope()
    val favouriteUids = favorites.map { it.uid }.toSet()

    LaunchedEffect(seed, favorites, guardRules, hasGelbooru) {
        // 组合重新进来时 effect 一定重跑 ⇒ 这一道守卫负责"这一轮已经有结果就别再发一笔"（批 5）。
        if (state is DesktopForYouState.Ready) return@LaunchedEffect
        if (!plan.canDispatch) {
            state = DesktopForYouState.Blank(plan.blankScreenReason ?: "这一轮没有能发出去的标签")
            return@LaunchedEffect
        }
        // 发车前就拦：Gelbooru 匿名一律 401，发出去只是让用户等一轮网络再读一句凭据错误。
        val runnable = plan.queries.filter { (site, _) -> site != GallerySite.GELBOORU || hasGelbooru }
        val blocked = plan.queries.keys - runnable.keys
        val reasons = HashMap<GallerySite, String>()
        blocked.forEach { site -> reasons[site] = GallerySearchSource.NEEDS_ACCOUNT_REASON }
        plan.silentSites.forEach { site -> plan.reasonFor(site)?.let { reasons[site] = it } }
        state = fetchForYouPage(
            ports = ports,
            reasons = reasons,
            base = DesktopGalleryBatch(seed = seed, queries = runnable),
            favouriteUids = favouriteUids,
        ).fold(
            onSuccess = { DesktopForYouState.Ready(it) },
            onFailure = { DesktopForYouState.Failed("${it::class.java.simpleName}: ${it.message?.take(200)}") },
        )
    }

    val pad = DesktopGalleryMetrics.pageHorizontalPadding
    Column(Modifier.fillMaxWidth()) {
        // ⚠️ 这一屏**不挂** `verticalScroll`：下面那颗墙自己就是滚动容器（`LazyVerticalGrid`），
        // 套进纵向滚动会拿到无限高并当场抛 —— 整屏崩，不是滚不动。
        // 这条由 `DesktopWallScrollNestingTest` 扫着（成因与真崩溃读数都在那颗类注释里）。
        Column(
            Modifier
                .padding(start = pad, end = pad, top = 10.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("猜你喜欢", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            val page = (state as? DesktopForYouState.Ready)?.batch?.page ?: 0
            Text(
                "本轮从 ${favorites.size} 条收藏里抽标签 · 每站各取一页" +
                    if (page > 1) " · 已给到第 $page 页" else "",
                color = DesktopTheme.TextTertiary,
                fontSize = 12.5.sp,
            )
            val batch = (state as? DesktopForYouState.Ready)?.batch
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // ⚠️ fluent 的这颗收 `onClick` + **composable 的 `content`**（末位 lambda），
                // 不收 String —— 判法同第七批那条：`javap -v` 的 `d2`。
                Button(onClick = { seed = System.currentTimeMillis() }) {
                    Text("换一批", fontSize = 13.sp)
                }
                // 「下一页」只在**还没到底**时摆：到底了还摆一枚按下去什么都不发生的按钮，
                // 就是本仓禁的那枚假开关 —— 那种形状下"到底了"由 [DesktopForYouReadout] 那句念。
                if (batch != null && !batch.atEnd) {
                    Button(
                        onClick = {
                            if (morePhase is DesktopGalleryMorePhase.Loading) return@Button
                            morePhase = DesktopGalleryMorePhase.Loading
                            // `batch` 是**点下去那一刻**的那一颗：`queries` 已经过 Gelbooru 那道闸，
                            // 所以续页不必再判一次凭据（再判一遍就是两处各定一次同一件事）。
                            val base = batch
                            scope.launch {
                                fetchForYouPage(ports, base.failures, base, favouriteUids)
                                    .fold(
                                        onSuccess = {
                                            state = DesktopForYouState.Ready(it)
                                            morePhase = DesktopGalleryMorePhase.Idle
                                        },
                                        onFailure = { failure ->
                                            morePhase = DesktopGalleryMorePhase.Failed(
                                                "${failure::class.java.simpleName}: ${failure.message?.take(200)}",
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

        Box(Modifier.weight(1f).padding(start = pad, end = pad, bottom = 10.dp)) {
            when (val current = state) {
                DesktopForYouState.Loading -> Text("正在抽这一轮的标签并取第一页…", color = DesktopTheme.TextSecondary, fontSize = 13.sp)
                is DesktopForYouState.Blank -> Text(current.reason, color = DesktopTheme.TextSecondary, fontSize = 13.sp)
                is DesktopForYouState.Failed -> Text(
                    "这一轮没取成：${current.message} —— 这是取数出了事，与「某一站没答上」是两句话。",
                    color = DesktopTheme.AccentBeni,
                    fontSize = 13.sp,
                )

                is DesktopForYouState.Ready -> Column(Modifier.fillMaxSize()) {
                    val wall = remember(current.batch.posts, maskMode, guardRules, blockAi) {
                        DesktopGalleryWallPolicy.of(current.batch.posts, maskMode, blockAi, ports.contentGuard)
                    }
                    if (wall.cards.isEmpty()) {
                        Text(
                            "这一轮取回了 ${current.batch.posts.size} 张，全被挡在屏前：" +
                                desktopWallGuardReading(wall).ifBlank { "没有能上屏的条目" },
                            color = DesktopTheme.TextSecondary,
                            fontSize = 13.sp,
                        )
                    } else {
                        DesktopGalleryPostWall(
                            wall = wall,
                            // 段头（标题 +「换一批」/「下一页」）要常驻，不进滚动容器。
                            lead = {},
                            columnMode = columnMode,
                            quality = quality,
                            aiBadge = aiBadge,
                            onPick = onPick,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                    DesktopForYouReadout(current, wall, morePhase)
                }
            }
        }
    }
}

/**
 * 取**一页**并落地成新一颗 [DesktopGalleryBatch]。第一轮与第 N 轮走的是同一颗，
 * 差别只在传进来的 `base` —— 两条路各写一份取数就是"第一页能翻而续页判据漂"的成因。
 *
 * **每腿要第几页不在这里定**：转交 [desktopGalleryBatchNextPages]（它里面就是共享面那颗
 * `nextPagesForAppend`）。这一屏从前对所有腿发同一个 `base.page + 1`，于是上一笔超时的那一站
 * 被跳走一页、而它从没拿到过第 1 页 ⇒ 那一页的图永久漏掉（屏上只有"那一站没答上"，没人会数页）。
 *
 * [returnedBySite] 只记**答上了的**站（张数）：失败的那一站根本不进这张表，
 * 所以 [GallerySearchMerge.exhaustedSites] 不会把它判成到底（「缺席不算到底」那条）。
 *
 * ## [reasons] 那份旧账怎么并（批 32 抓到，批 33 收进一颗件）
 *
 * 续页那一笔传进来的是**上一轮的整份名单**（里面既有"没发车"也有上一轮"没答上"）。
 * 从前这里写 `HashMap(reasons)` 再往上覆盖 ⇒ 只有"这一轮又失败了"才改得动它，而这一轮**答上了**的
 * 那一站根本不会被覆盖，上一轮那句「没答上」就活了下来：墙上摆着它的图、同一行说它没答上
 * （与批 31 在 [fetchWallPage] 里抓到的同一族，那一屏改了一处，这一屏是另一处）。
 * 现在四屏共用一颗 [desktopFailureLedgerAfter]（规则：这一轮答过的腿只看这一轮的答案）。
 */
internal suspend fun fetchForYouPage(
    ports: GalleryPorts,
    reasons: Map<GallerySite, String>,
    base: DesktopGalleryBatch,
    favouriteUids: Set<String>,
): Result<DesktopGalleryBatch> = withContext(Dispatchers.IO) {
    runCatching {
        val legsToRun = desktopGalleryBatchLegs(base, base.queries)
        val pages = desktopGalleryBatchNextPages(base, legsToRun.keys.toList())
        val legs = coroutineScope {
            legsToRun.map { (site, query) ->
                val board = boardOf(ports, site)
                async {
                    site to GalleryLegGuard.guard(site) {
                        board.searchPosts(query, page = pages.getValue(site), limit = board.pageSize)
                    }
                }
            }.map { it.await() }
        }
        val outcomes = desktopFailureLedgerAfter(
            previous = reasons,
            asked = legsToRun.keys,
            fresh = legs.mapNotNull { (site, outcome) -> outcome.reason?.let { site to it } }.toMap(),
        )
        val pools = legs.filter { it.second.answered }.associate { (site, outcome) -> site to outcome.posts }
        val returned = pools.mapValues { (_, posts) -> posts.size }
        val merged = GalleryForYouMerge.page(
            pools = pools,
            seed = base.seed,
            // 混排节奏只吃**最深那一页**（它的唯一用途是让相邻两轮的交错序列不同）。
            // 两腿页码不齐时这个数会短暂复用一个序列 —— 那不打紧：重复上屏由 `seenKeys` 挡，
            // 而这里给的从来不是"第几页"这件事的本体（本体在 base.pageBySite）。
            page = pages.values.maxOrNull() ?: 1,
            seenKeys = base.seenKeys,
            favouriteUids = favouriteUids,
        ).getOrThrow()
        desktopGalleryBatchApply(
            current = base,
            round = base.seed,
            pages = pages,
            returnedBySite = returned,
            reasons = outcomes,
            pageSizeOf = { site -> boardOf(ports, site).pageSize },
            paged = merged,
        )
    }
}

/**
 * 页尾那句"什么时候才会重新抽一轮"，单独提成常量是为了让它**可被点名**：
 * ⚠️ 第二十四批改判：旧那一版写着"每…都重抽"，而批 5 之后换回来还是同一批 ⇒ 那是假话
 * （转述而不复制原文，理由见手册 §0 坑 ㉑：禁串判据会把"引用那句假话"也算成违例）。
 * 从前它只是读函数里的一行字面量，判据只能拿"第一页"三个字去扫，
 * 而那三个字在"正在取第一页…"里也有 —— 接上续页之后那条判据照样绿着，那句却已经是假话了。
 */
internal const val DESKTOP_FOR_YOU_SNAPSHOT_LINE: String =
    "离开这一屏再回来还是这一批 —— 只有关掉程序再开才会重新抽，那一份快照桌面没有。"

/**
 * 页尾那几句读数。次序是刻意的：**先说抽到了什么串**（这一轮的身份），再说少了谁，最后才是计数。
 *
 * ⚠️ 每一句都是"有内容才说"，但**不许因为没内容就不留判据**：
 * 一站没答上时必须点名是哪一站、为什么 —— 双源混摆静默退化成单源是这一屏最难发现的一种错。
 */
@Composable
private fun DesktopForYouReadout(state: DesktopForYouState.Ready, wall: DesktopWall, morePhase: DesktopGalleryMorePhase) {
    val batch = state.batch
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "这一轮发出去的标签：" + batch.queries.entries.joinToString("；") { (site, query) ->
                "${site.displayName} $query"
            },
            color = DesktopTheme.TextTertiary,
            fontSize = 11.5.sp,
        )
        // 主语按"这一轮发没发过车"分档（批 32）：从前这里统一写着「没发出去的站」，
        // 而名单里恰恰混着发出去而没答上的那一站 —— 屏上那句是假的。
        val missing = desktopMissingReading(batch.queries.keys, batch.failures)
        if (missing != null) {
            Text(missing, color = DesktopTheme.AccentBeni, fontSize = 11.5.sp)
        }
        val moreReading = desktopGalleryMoreReading(morePhase, taken = batch.posts.size, unit = "张")
        if (moreReading != null) {
            // 句子住在件里，颜色留在这里判：失败那一句要红色，等待那一句不要。
            Text(
                moreReading,
                color = if (morePhase is DesktopGalleryMorePhase.Failed) DesktopTheme.AccentBeni else DesktopTheme.TextSecondary,
                fontSize = 11.5.sp,
            )
        }
        val counts = buildList {
            if (batch.excludedFavourite > 0) add("已排掉 ${batch.excludedFavourite} 张你收藏过的图")
            if (batch.droppedUnusable > 0) add("滤掉 ${batch.droppedUnusable} 张不能上屏的")
            if (batch.droppedDuplicates > 0) add("去掉 ${batch.droppedDuplicates} 张重复的")
            val videos = batch.posts.count { it.isVideo }
            if (videos > 0) add("其中 $videos 张是视频条目，只摆静帧")
        }
        if (counts.isNotEmpty()) {
            Text(counts.joinToString(" · "), color = DesktopTheme.TextTertiary, fontSize = 11.5.sp)
        }
        DesktopWallGuardLine(wall)
        // 到底那句只在真到底时说：没到底就念"已经到底"是拿一句假话结束一整屏。
        if (batch.atEnd) {
            Text(
                "这一轮发出去的站都到底了 —— 再翻也不会有新的，要换一批内容请按「换一批」。",
                color = DesktopTheme.TextTertiary,
                fontSize = 11.5.sp,
            )
        }
        Text(
            DESKTOP_FOR_YOU_SNAPSHOT_LINE,
            color = DesktopTheme.TextTertiary,
            fontSize = 11.5.sp,
        )
    }
}
