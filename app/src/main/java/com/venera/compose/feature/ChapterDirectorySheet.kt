package com.venera.compose.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.download.ChapterOffline
import com.venera.compose.source.model.ChapterGroup
import com.venera.compose.source.model.ComicChapter
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 完整章节目录面板（设计稿 `_design/chapter-list-redesign.html` 屏 01 的落地）。
 *
 * ## 它解的题
 *
 * 235+ 话的本子在详情页里是「FlowRow 胶囊截断 80 + 显示全部」：截断是当初为了
 * 不一次组合几百颗件（会拖垮详情页），但展平后单列近 60 屏，第 180 话要滑半分钟，
 * 且没有任何定位机制 —— 没有分卷、没有索引轨、特典混在话号序列里。
 *
 * ## 形态为什么是「面板里的网格」而不是「把详情页那一段换成网格」
 *
 * 详情页整页就是一颗 LazyColumn —— **嵌套 Lazy 网格会拿到无限高度直接崩**。
 * 所以轻量胶囊留在页内（首屏性能红线不动），完整目录挪进本面板：
 * 面板 own 自己的 LazyVerticalGrid，两列等宽，一屏约 24 话，纵向距离砍到原来的 1/3。
 *
 * ## 分卷带
 *
 * 源给多分组（单行本 / 连载中 / 番外篇）时按分组切带；只有一组时**每 50 话虚拟分卷**。
 * 带是普通网格项（占满整行）而不是 stickyHeader —— Lazy 网格没有粘性头，
 * 位置感交给右侧索引轨：轨与网格共享同一个 [LazyGridState]，滚动位置反推当前带，
 * 点带序号 `scrollToItem` 直达（整列表千级以内，随机访问便宜）。
 *
 * ## 状态点而不是徽章
 *
 * 已下载 = 绿点、未读 = 琥珀点（`ComicChapter.isRead` 有真值时才给，别造第二种颜色撒谎），
 * 一行一颗「已下载」徽章在两百行里就是两百份视觉噪音。特典徽章保留 ——
 * 它是「这不是第 N 话」的身份声明，不是状态。
 *
 * ## 字号与尺寸纪律
 *
 * 字全部吃 `VeneraTokens.type`（上一批统一的字阶真相），行高不动；
 * 尺寸来自既有 spacing token 与具名常量（格高 = `chapterGridCellHeight`），无页内抄数。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChapterDirectorySheet(
    chapters: List<ComicChapter>,
    groups: List<ChapterGroup>,
    selectedGroupIndex: Int,
    reversed: Boolean,
    onReverse: () -> Unit,
    onSelectGroup: (Int) -> Unit,
    currentChapterId: String?,
    offlineMap: Map<String, ChapterOffline>?,
    onDismiss: () -> Unit,
    onPickChapter: (ComicChapter) -> Unit,
) {
    val tokens = VeneraTokens
    val sheetScope = rememberCoroutineScope()

    val columns = 2
    val bandSize = 50
    var filter by remember { mutableIntStateOf(0) } // 0 全部 / 1 只看未读 / 2 未下载
    val hasReadTruth = remember(chapters) { chapters.any { it.isRead } }
    val state = rememberLazyGridState()
    val hasDownloadTruth = offlineMap != null
    // 「已下载」= 齐（COMPLETE）。截断的 PARTIAL 不算已下载，与下载面板同一档位口径。
    fun isComplete(id: String) = (offlineMap?.get(id) ?: ChapterOffline.NONE) == ChapterOffline.COMPLETE

    val displayed = remember(chapters, reversed, filter, offlineMap) {
        val base = if (reversed) chapters.asReversed() else chapters
        when (filter) {
            1 -> base.filterNot { it.isRead }
            2 -> if (hasDownloadTruth) base.filterNot { isComplete(it.id) } else base
            else -> base
        }
    }
    val currentCell = currentChapterId?.let { id -> displayed.indexOfFirst { it.id == id } } ?: -1

    // ── 带表：band → (它的第一个网格项 index)；网格项 = 每带 1 条带行 + ceil(n/2) 行格子 ──
    val bandsInfo = remember(displayed, groups, columns) {
        val chunks: List<DirBand> = if (groups.size > 1) {
            // 分组切带：用 id 集合认领 displayed 里的章（跨组重 id 极罕见，认领先到先得）。
            val claimed = BooleanArray(displayed.size)
            groups.map { g ->
                val ids = g.chapters.map { it.id }.toSet()
                var lo = -1
                var cnt = 0
                displayed.forEachIndexed { n, ch ->
                    if (!claimed[n] && ch.id in ids) {
                        claimed[n] = true
                        if (lo < 0) lo = n
                        cnt++
                    }
                }
                if (lo < 0 || cnt == 0) null else DirBand(g.name, lo, cnt)
            }.filterNotNull()
        } else {
            virtualBandRanges(displayed.size, bandSize).map { (lo, cnt) ->
                DirBand("第 " + (lo / bandSize + 1) + " 卷", lo, cnt)
            }
        }
        bandItemTable(chunks, columns)
    }

    val cellSlotOf = remember(bandsInfo, columns) {
        val table = HashMap<Int, Int>()
        for ((band, itemStart) in bandsInfo) {
            for (j in 0 until band.count) {
                table[band.start + j] = itemStart + 1 + j / columns * columns + j % columns
            }
        }
        table
    }

    fun jumpTo(cellIndex: Int) {
        val slot = cellSlotOf[cellIndex] ?: return
        sheetScope.launch { state.scrollToItem(slot) }
    }
    LaunchedEffect(Unit) { if (currentCell >= 0) jumpTo(currentCell) }

    val activeBand by remember(state, bandsInfo) {
        derivedStateOf {
            val first = state.firstVisibleItemIndex
            var cur = 0
            bandsInfo.forEachIndexed { i, (_, itemStart) -> if (itemStart <= first) cur = i }
            cur
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MiuixTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.space7)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "章节目录",
                        fontSize = tokens.type.itemTitle,
                        fontWeight = tokens.type.weightBold,
                        color = tokens.color.textPrimary,
                    )
                    val specCount = remember(chapters) { chapters.count { isSpecialTitle(it.title) } }
                    val downCount = if (hasDownloadTruth)
                        chapters.count { (offlineMap?.get(it.id) ?: ChapterOffline.NONE) == ChapterOffline.COMPLETE } else -1
                    Text(
                        text = buildString {
                            append("共 ").append(chapters.size).append(" 话")
                            if (specCount > 0) append(" · 含 ").append(specCount).append(" 特典")
                            if (downCount >= 0) append(" · 已下载 ").append(downCount)
                        },
                        fontSize = tokens.type.caption,
                        color = tokens.color.textSecondary,
                    )
                }
                VeneraChip(
                    text = if (reversed) "倒序 ↓" else "正序 ↑",
                    selected = false,
                    onClick = onReverse,
                )
            }

            if (groups.size > 1) {
                Spacer(modifier = Modifier.height(tokens.spacing.space4))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4)) {
                    items(groups.indices.toList()) { gi ->
                        VeneraChip(
                            text = groups[gi].name,
                            selected = selectedGroupIndex == gi,
                            onClick = { onSelectGroup(gi) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(tokens.spacing.space5))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
            ) {
                VeneraChip(text = "全部", selected = filter == 0, onClick = { filter = 0 })
                if (hasReadTruth) {
                    VeneraChip(text = "只看未读", selected = filter == 1, onClick = { filter = 1 })
                }
                if (hasDownloadTruth) {
                    VeneraChip(text = "未下载", selected = filter == 2, onClick = { filter = 2 })
                }
                Spacer(modifier = Modifier.weight(1f))
                if (currentCell >= 0) {
                    VeneraChip(text = "定位在读", selected = false, onClick = { jumpTo(currentCell) })
                }
            }

            Spacer(modifier = Modifier.height(tokens.spacing.space6))
            if (displayed.isEmpty()) {
                VeneraEmptyView(title = "这一筛没有话", message = "换回「全部」看看")
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 定高视口，不用 fillMaxHeight(0.74f)：面板内容列在 ModalBottomSheet 里
                        // 是**无界高**，分数填充在无界父上等于无界 —— Lazy 网格拿到无限高度会把
                        // 无限行高被挤成一坨（「右边的标被压得太小」）。定高之后网格才真懒。
                        .height(440.dp),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = state,
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                        contentPadding = PaddingValues(top = tokens.spacing.space3, bottom = tokens.spacing.space11),
                    ) {
                        bandsInfo.forEachIndexed { bi, (band, itemStart) ->
                            item(key = "band-$bi") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = if (bi > 0) tokens.spacing.space4 else 0.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = band.name,
                                        fontSize = tokens.type.sectionTitle,
                                        fontWeight = tokens.type.weightMedium,
                                        color = tokens.color.textSecondary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = band.count.toString() + " 话",
                                        fontSize = tokens.type.overline,
                                        color = tokens.color.textDisabled,
                                    )
                                }
                            }
                            val lo = band.start
                            val cnt = band.count
                            // key 吃章节 id 而不是筛选后的下标 —— 换筛选时同一章的格子身份不乱，
                            // 滚动位置与动画才连得上。
                            itemsIndexed((lo until lo + cnt).toList(), key = { _, n -> "ch-" + displayed[n].id }) { _, n ->
                                val ch = displayed[n]
                                ChapterGridCell(
                                    chapter = ch,
                                    isCurrent = n == currentCell,
                                    isSpecial = isSpecialTitle(ch.title),
                                    downloaded = hasDownloadTruth && isComplete(ch.id),
                                    unreadTruth = hasReadTruth,
                                    onClick = { onPickChapter(ch) },
                                )
                            }
                        }
                    }
                    if (bandsInfo.size > 1) {
                        DirectoryRail(
                            count = bandsInfo.size,
                            active = activeBand,
                            onItemClick = { bi ->
                                sheetScope.launch { state.scrollToItem(bandsInfo[bi].second) }
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(tokens.spacing.space11))
        }
    }
}

/** 网格单元：一行话 —— 标题、特典身份徽章、状态点；在读格描主色边。 */
@Composable
private fun ChapterGridCell(
    chapter: ComicChapter,
    isCurrent: Boolean,
    isSpecial: Boolean,
    downloaded: Boolean,
    unreadTruth: Boolean,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Surface(
        shape = RoundedCornerShape(tokens.shape.extraSmall),
        color = if (isCurrent) tokens.color.primaryContainer.copy(alpha = 0.5f)
        else MiuixTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .height(tokens.spacing.chapterGridCellHeight)
            .border(
                width = tokens.spacing.hairline,
                color = if (isCurrent) tokens.color.primary else Color.Transparent,
                shape = RoundedCornerShape(tokens.shape.extraSmall),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.shape.extraSmall))
                .clickable(onClick = onClick)
                .padding(horizontal = tokens.spacing.space4),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chapter.title,
                    fontSize = tokens.type.caption,
                    fontWeight = if (isCurrent) tokens.type.weightBold else tokens.type.weightRegular,
                    color = if (isCurrent) tokens.color.primary else tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isSpecial) {
                    Spacer(modifier = Modifier.width(tokens.spacing.space2))
                    Surface(
                        shape = RoundedCornerShape(tokens.shape.extraSmall),
                        color = tokens.color.primary.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = "特典",
                            fontSize = tokens.type.badge,
                            color = tokens.color.primary,
                            modifier = Modifier.padding(
                                horizontal = tokens.spacing.badgeHorizontalPadding,
                                vertical = tokens.spacing.badgeVerticalPadding,
                            ),
                        )
                    }
                }
            }
            // 状态点一行：已下载盖过未读（离线后「未读」不再是行动提示）；没有真值就不给点，不撒谎。
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = tokens.spacing.space1, bottom = tokens.spacing.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (downloaded || (unreadTruth && !chapter.isRead)) {
                    Box(
                        modifier = Modifier
                            .size(tokens.spacing.statusDotSize)
                            .clip(RoundedCornerShape(tokens.shape.extraSmall))
                            .background(if (downloaded) StatusColors.Healthy else StatusColors.Degraded),
                    )
                }
            }
        }
    }
}

/** 右侧索引轨：一格一带，当前带实心；点击直达。 */
@Composable
private fun DirectoryRail(
    count: Int,
    active: Int,
    onItemClick: (Int) -> Unit,
) {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier
            .width(tokens.spacing.directoryRailWidth)
            .fillMaxHeight()
            .padding(start = tokens.spacing.space3, end = tokens.spacing.space1, top = tokens.spacing.space4),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(count) { i ->
            val on = i == active
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tokens.spacing.directoryRailWidth)
                    .clip(RoundedCornerShape(tokens.shape.extraSmall))
                    .background(if (on) tokens.color.primary else tokens.color.primary.copy(alpha = 0.08f))
                    .clickable { onItemClick(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${i + 1}",
                    fontSize = tokens.type.overline,
                    fontWeight = tokens.type.weightMedium,
                    color = if (on) tokens.color.onPrimary else tokens.color.textSecondary,
                )
            }
        }
    }
}

/**
 * 「这不是第 N 话」的判据：正篇标题都带「话/話/回/章/EP」量词，
 * 没有量词的（特典、番外小剧场、OAD 剧本）即特殊条目。
 * 只认身份词面、不做数字插位 —— 插位会猜，猜错就把第 10 话挪到第 9 话前头。
 */
internal fun isSpecialTitle(title: String): Boolean {
    val t = title.trim()
    if (t.isEmpty()) return false
    if (SpecialTitleMarks.any { t.contains(it) }) return true
    return !ChapterQuantifierPattern.containsMatchIn(t)
}

private val SpecialTitleMarks = listOf("特典", "番外", "小剧场", "OAD", "SP", "剧场版")
private val ChapterQuantifierPattern = Regex("(话|話|回|卷|巻|章|ep|EP)")

/** 目录带：名称 + 在 displayed 序列中的起点 + 章数。虚拟分卷与源分组共用这一枚形状。 */
data class DirBand(val name: String, val start: Int, val count: Int)

/**
 * 虚拟分卷：每 `size` 话一带，尾带不足整带。
 *
 * 为什么按 50：索引轨要在半屏高里点得着（500 话 = 10 带刚好），
 * 20 带一路点下去就是滚动条，30 带又回到「滑半分钟」。
 */
internal fun virtualBandRanges(total: Int, size: Int = 50): List<Pair<Int, Int>> =
    if (total <= 0) emptyList()
    else (0 until total step size).map { lo -> lo to minOf(size, total - lo) }

/** 带 → 该带第一条网格项的 index；带行占一格，格行占 ceil(count/columns) 格。 */
internal fun bandItemTable(bands: List<DirBand>, columns: Int): List<Pair<DirBand, Int>> {
    val out = ArrayList<Pair<DirBand, Int>>(bands.size)
    var cursor = 0
    for (band in bands) {
        out += band to cursor
        cursor += 1 + (band.count + columns - 1) / columns
    }
    return out
}
