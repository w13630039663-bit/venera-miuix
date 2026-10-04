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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.venera.compose.gallery.domain.GallerySearchMerge
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.gallery.domain.GalleryTagFilter
import com.venera.compose.gallery.domain.GalleryDailyFeed
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 画廊搜索域：标签串 → 三站（或单站）并发搜 → [GallerySearchMerge.interleave] 合成一面墙。
 *
 * ## 取数链**一步都不新造**，逐颗点名
 *
 * | 这一屏做的事 | 用的是哪一颗 |
 * |---|---|
 * | 发车前算哪些腿能发 | `GallerySearchSource.availableLegs` / `missingLegs` |
 * | 每条腿一笔超时 + 原因不吞 | `GalleryLegGuard.guard`（与日榜同一份） |
 * | 合成一面墙（保序 + 跨站去重） | `GallerySearchMerge.interleave` |
 * | 哪些站到底了 | `GallerySearchMerge.exhaustedSites` + `GallerySearch.isExhausted` |
 * | 续页时每条腿各自要第几页 | `nextPagesForAppend` |
 * | 每站能写几枚标签 | `GallerySearch.tagBudget` / `fitsTagBudget` |
 *
 * ⚠️ 这张表是本屏的**全部**理由。搜索的坑几乎全在"三条腿的异步结果怎么拼"上 ——
 * 而那些坑 Android 侧已经踩过一遍并把判据提到了 `gallery/domain`（整颗目录在桌面编译面内）。
 * 桌面再写一份 `async { … }.await()` 的手抄拼装，等于把"两腿都空要不要炸""缺席腿算不算到底"
 * 这两条判据复制一份，而它们一旦分叉，用户看到的是"同一串标签在两端搜出不同数量的图"。
 *
 * ## 三条不能省的读数
 *
 * 1. **缺席的腿要逐站点名 + 原因**（`GalleryLegGuard.outcomeOf` 的 `reason`），
 *    混成"部分站点无结果"就是本仓最忌的静默降级。
 * 2. **没配 Gelbooru 账号时它**根本不算缺席，而是"发不出去"** ——
 *    `GallerySearchSource.missingLegs` 那句 `NEEDS_ACCOUNT_REASON` 直接上屏。
 *    这两件事在屏上必须长得不一样：一个是站方没给数，一个是我们压根没发车。
 * 3. **标签预算在发车前拦住**（`fitsTagBudget`）。Safebooru 匿名只吃 2 枚，超了站方直接 422 ——
 *    让它 422 再把那句话翻译成"搜索失败"就是把站方的读数念错了。
 *
 * ## 桌面这一屏**没有**的东西，逐条说清为什么（不留假开关）
 *
 * - **标签补全（`searchTags`）**：接了，但只作为**输入框下方的候选行**，点候选 = 把词接进输入串。
 *   不做成"打字时自动弹"是因为桌面没有那套防抖状态机。
 * - **翻页**：本屏第一版**只取第 1 页**。墙里若某站回了少于 `pageSize` 条，页尾会明说
 *   "那一站这一页到底了"，**不挂「加载更多」** —— 没做的按钮不许摆。
 * - **历史**：Android 侧那份 `GallerySearchHistory` 的存储在 `gallery/ui`（不在桌面编译面），
 *   本屏**不**存历史。要存得先给桌面一份落盘件，那是独立一批。
 */
@Composable
internal fun DesktopGallerySearchPane(ports: GalleryPorts) {
    val pad = DesktopGalleryMetrics.pageHorizontalPadding
    val metrics = DesktopGalleryMetrics

    // 来源轴：默认「全部」（用户 2026-09-30 在 Android 侧拍板过，桌面沿用同一口径）。
    val sources = remember { GallerySearchSource.options }
    var sourceIndex by remember { mutableStateOf(0) }
    val source = sources[sourceIndex]

    var query by remember { mutableStateOf("") }
    var round by remember { mutableStateOf(0) }
    var state: SearchState by remember { mutableStateOf(SearchState.Idle) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    // Gelbooru 配没配：既是"发不发车"的判据，也是 UI 形状的判据（没配时那一格要念出原因）。
    //
    // ⚠️ 这里**不许**写 `?: return`：接线没给桌面凭据件时那是一条静默的空屏 ——
    // 用户看到的是"这一域什么都没有"，而真相是"接线缺一件"。缺件要说出来。
    val credentials = ports.credentials as? DesktopGalleryCredentials
    if (credentials == null) {
        Text(
            "这一屏需要桌面凭据件（判断 Gelbooru 配没配），当前接线没给：${ports.credentials::class.java.simpleName}",
            color = DesktopTheme.AccentBeni,
            fontSize = 13.sp,
            modifier = Modifier.padding(pad),
        )
        return
    }
    val identityState by credentials.gelbooru.identity.collectAsState()
    val hasGelbooru = identityState != null

    val dispatched = remember(source, hasGelbooru) { source.availableLegs(hasGelbooru) }
    val missing = remember(source, hasGelbooru) { source.missingLegs(hasGelbooru) }
    val budgetNotice = remember(dispatched) {
        dispatched.firstNotNullOfOrNull { GallerySearch.budgetNotice(it) }
    }

    // 标签预算**在发车前**判：站方 422 是站方的读数，翻译成"搜索失败"就是把它念错了。
    val filters = remember(query) { GallerySearch.parseQuery(query) }
    val overBudget = remember(dispatched, filters.size) {
        dispatched.firstOrNull { !GallerySearch.fitsTagBudget(it, filters.size) }
    }

    // 一条腿的取数。三站并发 + 每站一笔超时，判据全在 GalleryLegGuard 里。
    LaunchedEffect(ports, sourceIndex, round, query) {
        val tags = GallerySearch.queryOf(GallerySearch.parseQuery(query))
        if (tags.isBlank()) {
            state = SearchState.Idle
            return@LaunchedEffect
        }
        if (dispatched.isEmpty()) {
            state = SearchState.Idle
            return@LaunchedEffect
        }
        if (overBudget != null) {
            state = SearchState.Blocked(
                GallerySearch.budgetNotice(overBudget) ?: "标签数超出这一站的预算",
            )
            return@LaunchedEffect
        }
        state = SearchState.Loading
        // ⚠️ `runCatching` 包的是**整段**（含 interleave）：`GallerySearchMerge.interleave` 自己在
        // 「三站都给货却滤完 0 条」时会 `getOrThrow`，那属于判据出错而不是某一站缺席 ——
        // 它必须冒到 [SearchState.Failed] 让这一屏变成错误页，而不是被腿级 catch 咽成空墙。
        // 而每一腿内部的失败由 `GalleryLegGuard.guard` 各自接走（它交回 `answered=false` + 原因），
        // 所以腿级失败永远不会走到这里 —— 屏上会看到的是"那一站没答上"而不是"搜索失败"。
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val legs = coroutineScope {
                    dispatched.map { site ->
                        val board = boardOf(ports, site)
                        async {
                            GalleryLegGuard.guard(site) {
                                board.searchPosts(tags, page = 1, limit = board.pageSize)
                            }
                        }
                    }.map { it.await() }
                }
                // 合成前先把"没答上的腿"剔掉：它们不参与混排，也不该被算进"到底"
                // （`GallerySearchMerge` 的类注释第 ③ 条：缺席腿当成到底，一次抖动就永久踢掉一站）。
                val answered = legs.filter { it.answered }.associate { it.site to it.posts }
                val merged = GallerySearchMerge.interleave(answered).getOrThrow()
                val returned = legs.associate { it.site to it.posts.size }
                val exhausted = GallerySearchMerge.exhaustedSites(returned) { boardOf(ports, it).pageSize }
                SearchState.Ready(
                    legs = legs,
                    merged = merged,
                    exhausted = exhausted,
                    // 时刻戳在**取完数那一刻**取，不是渲染时现取 ——
                    // 否则那句话回答的是"我什么时候看的屏"，而不是"墙上这批是哪一轮拿的"。
                    fetchedAtMillis = System.currentTimeMillis(),
                )
            }
        }
        state = outcome.getOrElse { SearchState.Failed("${it::class.java.simpleName}: ${it.message?.take(200)}") }
    }

    // 标签补全：只在词够长（≥2）时才发车，避免每敲一键就出一笔请求。
    LaunchedEffect(ports, sourceIndex, query) {
        val term = query.trim()
        suggestions = if (term.length < 2 || dispatched.isEmpty()) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                dispatched.map { site ->
                    async { boardOf(ports, site).searchTags(term).getOrDefault(emptyList()) }
                }.map { it.await() }
            }.flatMap { it }.map { it.name }.distinct().take(SUGGESTION_ROWS)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        // ① 段头：标题 + 来源轴（`GallerySearchSource.options`：全部 + 三站，顺序是 domain 定的）。
        Column(Modifier.padding(horizontal = pad)) {
            Row(
                Modifier.fillMaxWidth().height(metrics.pageHeaderHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.pageHeaderGap),
            ) {
                Text(
                    "搜索",
                    color = DesktopTheme.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                DesktopSearchSourceAxis(
                    sources = sources,
                    selectedIndex = sourceIndex,
                    // 没配账号的那一格**照样可点**，点了由下面那条缺席行念出原因 ——
                    // 画成灰格会让人以为"不可用"是这一站坏了，而不是"你没配"。
                    onSelect = { sourceIndex = sources.indexOfFirst { s -> s.label == it.label } },
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    // ⚠️ 第二个位置参数是 `enabled`、trailing lambda 形状 ——
                    // 与 `DesktopGelbooruCredentialPanel` 里那两处同形，别照 material 那套写。
                )
                AccentButton(
                    onClick = { round += 1 },
                    disabled = query.isBlank() || dispatched.isEmpty() || overBudget != null,
                ) { Text("搜索") }
            }

            // ② 三档缺席，三档长得不一样（这是本屏最容易做坏的地方）。
            SearchNotices(
                state = state,
                source = source,
                dispatched = dispatched,
                missing = missing,
                budgetNotice = budgetNotice,
                filters = filters,
            )

            // ③ 标签补全候选：点一枚 = 接进输入串。这是一条**真动作**（会改 query ⇒ 触发重搜）。
            if (suggestions.isNotEmpty() && query.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    suggestions.forEach { name ->
                        Text(
                            name,
                            modifier = Modifier
                                .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(999.dp))
                                // 点候选 = 把它当一枚**普通**标签接在后面（不是替换输入框的内容）。
                                // ⚠️ 走 `GalleryTagFilter.of` 而不是直接 `+ name`：这一串是站方语法，
                                // 候选名里带 `-` 的（少数站拿它当分隔）必须还原成排除项，
                                // 直接拼会在下一次 parse 时被切成两枚 —— 屏幕上就变成"搜了个没打过的标签"。
                                .clickable {
                                    GalleryTagFilter.of(name)?.let { tag ->
                                        query = (filters + tag).joinToString(" ") { it.token }
                                    }
                                }
                                .padding(horizontal = metrics.chipHorizontalPadding, vertical = 3.dp),
                            color = DesktopTheme.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // ④ 内容本体。状态栏与它**平级**才贴得住窗底。
        Box(Modifier.weight(1f).padding(start = pad, end = pad, bottom = 10.dp)) {
            when (val current = state) {
                SearchState.Idle -> SearchIdle(source, dispatched.isEmpty(), missing.isNotEmpty())
                SearchState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("正在向 ${dispatched.size} 个站发车", color = DesktopTheme.TextSecondary, fontSize = 13.sp)
                    ProgressBar()
                }

                is SearchState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "这一轮没有取到：${current.reason}",
                        color = DesktopTheme.AccentBeni,
                        fontSize = 13.sp,
                    )
                    Text(
                        "重试",
                        color = DesktopTheme.AccentFuji,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { round += 1 },
                    )
                }

                is SearchState.Ready -> Column(Modifier.fillMaxWidth()) {
                    val perSite = current.merged.perSite.entries.joinToString("、") {
                        "${it.key.displayName} ${it.value} 张"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "「${query.trim()}」",
                            color = DesktopTheme.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            " · 进屏 ${current.merged.posts.size} 张 · $perSite",
                            color = DesktopTheme.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (current.merged.posts.isEmpty()) {
                        Text(
                            "站方答上了这一轮，但没有可摆上屏的内容。",
                            color = DesktopTheme.TextSecondary,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        DesktopGalleryPostWall(current.merged.posts, Modifier.fillMaxWidth())
                    }
                    // 到底的那几站**明说**，不挂「加载更多」—— 本屏没做续页（见类注释）。
                    if (current.exhausted.isNotEmpty()) {
                        Text(
                            "已经取到底：" + current.exhausted.joinToString("、") { "${it.displayName}这一页之后没有更多" } +
                                "（翻页还没接）",
                            color = DesktopTheme.TextTertiary,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                is SearchState.Blocked -> Text(
                    current.reason,
                    color = DesktopTheme.AccentBeni,
                    fontSize = 13.sp,
                )
            }
        }

        if (state is SearchState.Ready) {
            val current = state as SearchState.Ready
            val dropped = listOfNotNull(
                current.merged.droppedUnusable.takeIf { it > 0 }?.let { "不可摆 $it 条" },
                current.merged.droppedDuplicates.takeIf { it > 0 }?.let { "重复 $it 条" },
            ).joinToString(" · ")
            DesktopGalleryStatusBar(
                siteTotal = dispatched.size,
                // ⚠️ 这里传的是**本轮真正答上的腿数**减 1？—— 不。`missingCount` 的语义是
                // "缺席的腿数"，而"答上了但回空表"不是缺席（`GalleryLegOutcome.answered == true`）。
                // 两者混起来就是"这一站明明没出错却被算成缺席"，所以只数 `answered == false`。
                missingCount = current.legs.count { !it.answered },
                fetchedAtMillis = current.fetchedAtMillis,
                budgetMs = SearchBudgetMs,
                droppedLabel = dropped,
            )
        }
    }
}

/** 搜索屏的每站预算：**引日榜那一枚**，不另写一个 12_000。 */
private val SearchBudgetMs = GalleryDailyFeed.PER_SITE_TIMEOUT_MS

/** 来源轴（复用段头那颗分段控件的形状，但数据面是 `GallerySearchSource`，不是 pane 的六项）。 */
@Composable
private fun DesktopSearchSourceAxis(
    sources: List<GallerySearchSource>,
    selectedIndex: Int,
    onSelect: (GallerySearchSource) -> Unit,
) {
    val metrics = DesktopGalleryMetrics
    Box(
        Modifier
            .background(DesktopTheme.SidebarBackground, RoundedCornerShape(metrics.controlRadius))
            .padding(metrics.segmentedInnerPadding),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.segmentedItemGap)) {
            sources.forEachIndexed { index, source ->
                val selected = index == selectedIndex
                Text(
                    source.label,
                    modifier = Modifier
                        .background(
                            color = if (selected) DesktopTheme.SurfaceRaised else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(metrics.segmentedItemRadius),
                        )
                        .clickable { onSelect(source) }
                        .padding(
                            horizontal = metrics.segmentedItemHorizontalPadding,
                            vertical = metrics.segmentedItemVerticalPadding,
                        ),
                    color = if (selected) DesktopTheme.TextPrimary else DesktopTheme.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 三档缺席，三档**长得不一样**。
 *
 * 混淆这三者是搜索屏最容易做坏的地方：
 * - **发不出去**（没配 Gelbooru 账号）：我们自己没发车 ⇒ 摆 `NEEDS_ACCOUNT_REASON` 与配置入口。
 * - **没答上**（超时 / 401 / 解析失败）：站方或链路的问题 ⇒ 逐站点名 + `reason`。
 * - **预算挡住**：我们自己拦下的 ⇒ 说清是几枚标签的预算，不是"搜索失败"。
 *
 * 三条在屏上挤成一句"部分站点无结果"是本仓最忌的那种静默降级。
 */
@Composable
private fun SearchNotices(
    state: SearchState,
    source: GallerySearchSource,
    dispatched: List<GallerySite>,
    missing: Map<GallerySite, String>,
    budgetNotice: String?,
    filters: List<GalleryTagFilter>,
) {
    // ① 发不出去的那些腿
    if (missing.isNotEmpty()) {
        Text(
            missing.entries.joinToString(" · ") { "${it.key.displayName}：${it.value}" },
            color = DesktopTheme.AccentBeni,
            fontSize = 12.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
    // ② 预算那道闸：即便还没点搜索也要说（用户在输入框里就能看到自己装不下）。
    if (filters.isNotEmpty() && budgetNotice != null) {
        val wouldBlock = dispatched.firstOrNull { !GallerySearch.fitsTagBudget(it, filters.size) }
        if (wouldBlock != null) {
            Text(
                "$budgetNotice（当前 ${filters.size} 枚）",
                color = DesktopTheme.TextTertiary,
                fontSize = 11.5.sp,
            )
        }
    }
    // ③ 本轮没答上的腿
    if (state is SearchState.Ready) {
        val unanswered = state.legs.filter { !it.answered }
        if (unanswered.isNotEmpty()) {
            Text(
                "这一轮没答上的站：" + unanswered.joinToString(" · ") { leg ->
                    "${leg.site.displayName}：${leg.reason ?: "没给原因"}"
                },
                color = DesktopTheme.AccentBeni,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 没搜过的那一屏。**不居中**（理由与收藏屏那条一致）。 */
@Composable
private fun SearchIdle(source: GallerySearchSource, noLegs: Boolean, hasMissing: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "还没有搜过",
            color = DesktopTheme.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            when {
                noLegs && hasMissing -> "这一档一条腿都发不出去，先把上面那一条处理掉。"
                noLegs -> "这一档没有可发的站。"
                else -> "在上面的框里输入标签（空格分隔，`-` 开头是排除），按「搜索」向 ${source.label} 发车。"
            },
            color = DesktopTheme.TextSecondary,
            fontSize = 12.5.sp,
        )
    }
}

/** 站 → board。`when` 穷尽：加站时编译器顶出来，而不是给一条 else 悄悄返回 null。 */
private fun boardOf(ports: GalleryPorts, site: GallerySite) = when (site) {
    GallerySite.YANDERE -> ports.boards.yandere
    GallerySite.GELBOORU -> ports.boards.gelbooru
    GallerySite.SAFEBOORU -> ports.boards.safebooru
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
        val legs: List<GalleryLegOutcome>,
        val merged: GallerySearchMerge.Legged,
        val exhausted: Set<GallerySite>,
        val fetchedAtMillis: Long,
    ) : SearchState
}
