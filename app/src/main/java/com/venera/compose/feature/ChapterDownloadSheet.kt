package com.venera.compose.feature

import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraCheckbox
import com.venera.compose.download.ChapterOffline
import com.venera.compose.source.model.ComicChapter
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 勾选网格里可见的逐话窗口上限；超出部分由区间芯片整段负责（见组件头注）。 */
private const val DOWNLOAD_PICK_WINDOW = 60

/** 中段视口定高。面板自撑：底栏跟着视口下沿走，任何机型都不会把它顶出屏。 */
private val DOWNLOAD_VIEWPORT_HEIGHT = 320.dp

/**
 * 批量下载选择面板（设计稿 `_design/chapter-list-redesign.html` 屏 02 的落地）。
 *
 * ## 它替换的是什么、为什么换
 *
 * 原先的「选择下载章节」是居中 AlertDialog + 单列勾选行：117 话就是 117 个勾，
 * 要么「全选未下载」一锅端，要么逐个点 —— 中间档（「这次只想再存 30 话」）不存在。
 * 面板给三档抓手：**下一批**（从在读位置 +30，追更的标准动作）、**区间芯片**
 * （按 50 话一段整取整放，边框报全选/部分状态）、**逐话微调**（三列勾选网格，
 * 可在格子上按住拖动圈选一段）。
 *
 *
 * - **底栏必须钉住**：整段内容直接顶满时，面板量不到视口底，最后一行会被裁到屏外 ——
 *   「开始下载」就此消失。现在中段是有界滚动视口，动作行是它的**兄弟**、常驻底部，
 *   内容多高都裁不到按钮。
 * - **磁盘态不许在组合里现扫**：`chapterOffline` 每次调用都 `listFiles()` 整本目录，
 *   235 话 × 每颗格 = 几百回磁盘 IO 摁在主线程上，打开就是卡。改吃调用方在 IO 线程
 *   预扫好的快照 Map，这里只做 O(1) 查表；扫描没回来前面板挂「正在核对离线文件」，
 *   不自作主张猜选中集。
 *
 * ## 逐话区是「窗口」而不是懒列表
 *
 * 区间芯片负责成百的整取整放；逐话区只管微调附近 [DOWNLOAD_PICK_WINDOW] 话。
 * 窗口直接组合换来指针级拖动圈选（Lazy 网格与拖起手互相抢事件），窗口外的章
 * 由区间芯片按**全量**兜住，一条都不会丢。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChapterDownloadSheet(
    chapters: List<ComicChapter>,
    offlineMap: Map<String, ChapterOffline>?,
    presetOffset: Int,
    storagePath: java.io.File,
    onDismiss: () -> Unit,
    onConfirm: (List<ComicChapter>) -> Unit,
) {
    val tokens = VeneraTokens
    val ready = offlineMap != null

    // 选中集：就绪前为空集（不猜）；就绪时一次性初始化成「未齐的」。
    var picked by remember(chapters) { mutableStateOf<Set<String>>(emptySet()) }
    var initialized by remember(chapters) { mutableStateOf(false) }
    if (ready && !initialized) {
        initialized = true
        picked = chapters
            .filter { (offlineMap[it.id] ?: ChapterOffline.NONE) != ChapterOffline.COMPLETE }
            .map { it.id }
            .toSet()
    }
    val undownloaded = remember(offlineMap, chapters) {
        if (!ready) emptySet()
        else chapters.filter { (offlineMap!![it.id] ?: ChapterOffline.NONE) != ChapterOffline.COMPLETE }
            .map { it.id }.toSet()
    }

    val window = remember(chapters) { chapters.take(DOWNLOAD_PICK_WINDOW) }
    val ranges = remember(chapters) { virtualBandRanges(chapters.size, 50) }

    // 三列格宽 = 约束宽换算（BoxWithConstraints 在组合期读约束）。
    // 而且上一版正是它：包裹列没有 fillMaxWidth，量到的是格子自己的窄宽，
    val columns = 3
    val gap = tokens.spacing.space3

    var dragHint by remember { mutableStateOf<String?>(null) }
    val cellLayouts = remember { HashMap<Int, Pair<IntOffset, IntSize>>() }

    fun cellAt(pos: Offset): Int? {
        for ((n, layout) in cellLayouts) {
            val (topLeft, size) = layout
            if (pos.x >= topLeft.x && pos.x < topLeft.x + size.width &&
                pos.y >= topLeft.y && pos.y < topLeft.y + size.height
            ) return n
        }
        return null
    }
    fun applyPick(id: String, want: Boolean) {
        val cur = picked
        picked = if (want) cur + id else cur - id
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MiuixTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.space7),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "下载章节",
                        fontSize = tokens.type.itemTitle,
                        fontWeight = tokens.type.weightBold,
                        color = tokens.color.textPrimary,
                    )
                    val doneCount = if (ready) chapters.count { offlineMap!![it.id] == ChapterOffline.COMPLETE } else 0
                    Text(
                        text = if (!ready) "正在核对离线文件…" else buildString {
                            append("共 ").append(chapters.size).append(" 话 · 已齐 ").append(doneCount).append(" 话")
                            if (window.size < chapters.size) append(" · 逐话区显示前 ").append(window.size).append(" 话")
                        },
                        fontSize = tokens.type.caption,
                        color = tokens.color.textSecondary,
                    )
                }
                VeneraChip(text = "全不选", selected = false, onClick = { picked = emptySet() })
            }

            Spacer(modifier = Modifier.height(tokens.spacing.space5))
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                // 唯一强调预设：追更的标准动作是「从读到地方再往下存一批」。
                VeneraChip(
                    text = "下一批 +30",
                    selected = true,
                    enabled = ready,
                    onClick = {
                        val ids = chapters.drop((presetOffset + 1).coerceAtLeast(0)).take(30).map { it.id }
                        picked = picked + ids.toSet()
                    },
                )
                VeneraChip(
                    text = "全部未下载",
                    selected = false,
                    enabled = ready,
                    onClick = { picked = undownloaded },
                )
                VeneraChip(
                    text = "全选",
                    selected = false,
                    enabled = ready,
                    onClick = { picked = chapters.map { it.id }.toSet() },
                )
            }

            // ── 中段：有界滚动视口。它是底栏的兄弟而不是同列顺延 —— 内容再高也只裁这里。──
            Column(
                modifier = Modifier
                    .height(DOWNLOAD_VIEWPORT_HEIGHT)
                    .verticalScroll(rememberScrollState())
                    .padding(top = tokens.spacing.space6),
            ) {
                if (window.isNotEmpty()) {
                    Text(
                        text = "按区间整选 · 再点整放",
                        fontSize = tokens.type.overline,
                        fontWeight = tokens.type.weightMedium,
                        color = tokens.color.textDisabled,
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space3))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        ranges.forEach { (lo, cnt) ->
                            val hi = lo + cnt - 1
                            val pickedIn = chapters.subList(lo, hi + 1).count { it.id in picked }
                            val full = pickedIn == cnt
                            val part = pickedIn > 0 && !full
                            Surface(
                                shape = RoundedCornerShape(tokens.shape.extraSmall),
                                color = if (full) tokens.color.primaryContainer.copy(alpha = 0.5f)
                                else MiuixTheme.colorScheme.background,
                                modifier = Modifier
                                    .padding(bottom = gap)
                                    .border(
                                        width = tokens.spacing.hairline,
                                        color = when {
                                            full -> tokens.color.primary
                                            part -> StatusColors.Degraded.copy(alpha = 0.6f)
                                            else -> Color.Transparent
                                        },
                                        shape = RoundedCornerShape(tokens.shape.extraSmall),
                                    )
                                    .clip(RoundedCornerShape(tokens.shape.extraSmall))
                                    .clickable {
                                        val ids = chapters.subList(lo, hi + 1).map { it.id }.toSet()
                                        picked = if (!full) picked + ids else picked - ids
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = tokens.spacing.space4,
                                        vertical = tokens.spacing.space3,
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${lo + 1}–${hi + 1}",
                                        fontSize = tokens.type.caption,
                                        fontWeight = tokens.type.weightMedium,
                                        color = if (full) tokens.color.primary else tokens.color.textPrimary,
                                    )
                                    Spacer(modifier = Modifier.width(tokens.spacing.space3))
                                    Text(
                                        text = if (full) "全 $cnt" else if (pickedIn > 0) "$pickedIn/$cnt" else "$cnt 话",
                                        fontSize = tokens.type.overline,
                                        color = tokens.color.textSecondary,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(tokens.spacing.space3))
                    Text(
                        text = dragHint ?: "逐话微调 · 在格子上按住拖动可圈选一段",
                        fontSize = tokens.type.overline,
                        fontWeight = if (dragHint != null) tokens.type.weightBold else tokens.type.weightRegular,
                        color = if (dragHint != null) tokens.color.primary else tokens.color.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space3))

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        // 约束宽换算：读组合约束，不回写状态（跨相位回写是官方反模式，
                        // 也是上一版格子塌成 2dp 的起点 —— 量到的其实是窄内容的自包裹宽）。
                        val cellWidth = (maxWidth - gap * (columns - 1)) / columns
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(window) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val anchor = cellAt(down.position)
                                    if (anchor == null) return@awaitEachGesture
                                    val want = window[anchor].id !in picked
                                    val base = picked
                                    val outside = base.filterNot { id -> window.any { it.id == id } }
                                    var last = anchor
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (!change.pressed) break
                                        val idx = cellAt(change.position)
                                        if (idx != null && idx != last) {
                                            last = idx
                                            val lo = minOf(anchor, idx)
                                            val hi = maxOf(anchor, idx)
                                            val ids = window.subList(lo, hi + 1).map { it.id }.toSet()
                                            val inside = if (want) base + ids else base - ids
                                            picked = inside + outside
                                            dragHint = "圈选 ${lo + 1}–${hi + 1} 格 · 共 ${hi - lo + 1} 话"
                                        }
                                        change.consume()
                                    }
                                    dragHint = null
                                }
                            }
                    ) {
                        window.chunked(columns).forEachIndexed { rowIdx, rowChs ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                rowChs.forEachIndexed { col, ch ->
                                    val n = rowIdx * columns + col
                                    val checked = ch.id in picked                                    // 三档显式：齐 = 绿点、不全 = 琥珀点（可重勾补下）、无 = 不给点。
                                    // 查表不查磁盘 —— 快照由调用方在 IO 线程预扫。
                                    val off = offlineMap?.get(ch.id) ?: ChapterOffline.NONE
                                    val dot = when (off) {
                                        ChapterOffline.COMPLETE -> StatusColors.Healthy
                                        ChapterOffline.PARTIAL -> StatusColors.Degraded
                                        ChapterOffline.NONE -> null
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(tokens.shape.extraSmall),
                                        color = if (checked) tokens.color.primaryContainer.copy(alpha = 0.45f)
                                        else MiuixTheme.colorScheme.background,
                                        modifier = Modifier
                                            .width(cellWidth)
                                            .padding(bottom = gap)
                                            .onGloballyPositioned { l ->
                                                val pos = l.positionInRoot()
                                                cellLayouts[n] = IntOffset(pos.x.toInt(), pos.y.toInt()) to l.size
                                            }
                                            .border(
                                                width = tokens.spacing.hairline,
                                                color = if (checked) tokens.color.primary else Color.Transparent,
                                                shape = RoundedCornerShape(tokens.shape.extraSmall),
                                            ),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(tokens.shape.extraSmall))
                                                .clickable { applyPick(ch.id, !checked) }
                                                .padding(horizontal = tokens.spacing.space3, vertical = tokens.spacing.space3),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            VeneraCheckbox(
                                                checked = checked,
                                                onCheckedChange = { applyPick(ch.id, it) },
                                            )
                                            Spacer(modifier = Modifier.width(tokens.spacing.space2))
                                            Text(
                                                text = ch.title,
                                                fontSize = tokens.type.caption,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                                color = tokens.color.textPrimary,
                                            )
                                    if (dot != null) {
                                        Box(
                                            modifier = Modifier
                                                .size(tokens.spacing.statusDotSize)
                                                .clip(RoundedCornerShape(tokens.shape.extraSmall))
                                                .background(dot),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
                }
            }

            // ── 底栏：中段的兄弟，钉在面板底部，永远够得着（被裁掉的「开始下载」不许再发生）。──
            Spacer(modifier = Modifier.height(tokens.spacing.space4))
            val freeWarn = remember(storagePath, picked.size, ready) {
                if (!ready) null
                else runCatching {
                    val stat = StatFs(storagePath.absolutePath)
                    val freeMb = stat.availableBytes / (1024L * 1024L)
                    if (freeMb < 400) "存储空间仅剩 ${freeMb} MB，可能装不下" else null
                }.getOrNull()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = tokens.spacing.space11),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "已选 ${picked.size} 话",
                        fontSize = tokens.type.statNumber,
                        fontWeight = tokens.type.weightBold,
                        color = if (picked.isEmpty()) tokens.color.textDisabled else tokens.color.primary,
                    )
                    if (freeWarn != null) {
                        Text(
                            text = freeWarn,
                            fontSize = tokens.type.overline,
                            color = StatusColors.Degraded,
                        )
                    }
                }
                Button(
                    onClick = { onConfirm(chapters.filter { it.id in picked }) },
                    enabled = ready && picked.isNotEmpty(),
                ) {
                    Text(
                        text = "开始下载",
                        fontSize = tokens.type.caption,
                        fontWeight = tokens.type.weightBold,
                    )
                }
            }
        }
    }
}
