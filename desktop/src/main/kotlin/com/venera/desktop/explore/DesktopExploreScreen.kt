package com.venera.desktop.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraButton
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.contentMaxWidthFor
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.engineprobe.EngineSession

/**
 * 桌面探索屏 —— 对应安卓 `UnifiedExploreScreen`（1459 行）。
 *
 * ## 它同时是详情/阅读器的入口
 *
 * 阅读器接上之前，桌面上**没有任何地方能点开一本漫画**：首页只有统计与历史，
 * 画廊那一格是图片墙（不是漫画）。所以这一屏不只是"补一个 tab"，
 * 它还是把「详情 → 阅读器 → 写历史」那条链子**起点接上**的地方。
 *
 * ## 与安卓的差别（写明）
 *
 * 安卓那 1459 行有分区折叠、下拉刷新、手势返回、分区内横滑。
 * 大屏这里做成**一屏多分区、grid 铺开**。
 *
 * 做到：按源 `explore()` 取数据、网格铺开、点卡进详情。
 * 没做：翻页取下一页（`maxPage` 引擎已给，UI 还没挂）、切换源、按分区筛选。
 */
@Composable
internal fun DesktopExploreScreen(
    mode: WideScreenLayoutMode,
    /** 当前源名字，顶栏显示用。 */
    sourceKey: String,
    /** 各分区的漫画；空 + note 为空 = 还在读。 */
    sections: List<EngineSession.Section>,
    /** 读取中 / 失败成因。非空照原话上屏。 */
    note: String?,
    /** 打码判定：给一张卡，说要不要遮。判据在外面（`UxPlatformServices`），这里只问。 */
    maskedOf: (EngineSession.ComicCard) -> Boolean,
    onOpen: (EngineSession.ComicCard) -> Unit,
    onReload: () -> Unit,
    /**
     * 进源管理屏。
     *
     * 2026-10-21 补：源管理屏本身早就写好了（256 行、启停与两档守卫设置都接上了），
     * 但**全仓没有一处 UI 切得到那个路由** ⇒ 等于那屏没做。入口放这里而不是底栏：
     * 安卓的源管理也在「设置」里，底栏那五格是 tab 语义，塞不进二级页。
     *
     * ⚠️ **刻意不给默认值**：默认 `{}` 就是"按钮摆出来了而按下去什么都不发生"，
     * 那正是本仓「灰行不许留在屏上」那一族的形状（评审 R6 的 `onRetest = {}` 同一类）。
     */
    onOpenSourceManage: () -> Unit,
    /**
     * 进下载队列屏。
     *
     * 与 `onOpenSourceManage` 同一形状、同一位置（顶栏），而且**同样不许给默认值**：
     * 默认 `{}` 就是一颗按下去什么也不做的假开关。
     *
     * 入口也**不许**做成「只有队列里有任务才摆」。队列正在跑、卡住、或某一笔写失败的时候，
     * 用户最需要的是能进去看与停手；一切正常时空列表反而无所谓。
     */
    onOpenQueue: () -> Unit,
    /**
     * 进「本机已下载」屏。
     *
     * ⚠️ 唯一一处**可为 null** 的入口，而且不是忘了必填：它要的是下载根目录，而
     * `downloadsRoot == null` 时整个下载域在桌面上没有立足处（详情那枚把手整颗不摆、泵不起、
     * 这一屏连盘上的树都数不着）。摆一扇门而门后永远念不出东西，比没有那扇门更坏 ——
     * 与 `DesktopComicDetailScreen.onDownloadEpisode` 完全同一条口径（评审 R6 那一族）。
     *
     * 注意它**不是**「按队列里有没有任务摆」，也不是「按盘上有没有东西摆」：只要根目录拿得到就得进得去。
     */
    onOpenDownloads: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val maxWidth = contentMaxWidthFor(mode)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
                .fillMaxSize()
                .padding(horizontal = tokens.spacing.screenHorizontal),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
            ) {
                Text(
                    text = "探索",
                    fontSize = tokens.type.screenTitle,
                    color = tokens.color.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = sourceKey,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                )
                VeneraButton(onClick = onReload) { Text("重新取") }
                // 入口放在顶栏，因而**任何状态都在**：取失败时这一枚正是出路
                // （"取不到探索页"最常见的成因就是那颗源被停用了，而只有源管理屏能把它启回来）。
                // 若做成「只有取成功才摆」，坏了的时候用户反而进不去改 —— 那是最该能进的时候进不去。
                VeneraButton(onClick = onOpenSourceManage) { Text("源管理") }
                VeneraButton(onClick = onOpenQueue) { Text("下载队列") }
                // 与详情那枚「下载这一话」同一判据：没有那一跳的门就不摆。
                if (onOpenDownloads != null) {
                    VeneraButton(onClick = onOpenDownloads) { Text("本机已下载") }
                }
            }

            if (note != null) {
                Text(
                    text = note,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(bottom = tokens.spacing.space3),
                )
            }

            if (sections.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (note == null) "正在从「$sourceKey」取探索页…" else "",
                        fontSize = tokens.type.body,
                        color = tokens.color.textTertiary,
                    )
                }
                return@Column
            }

            // 一条 LazyVerticalGrid 铺所有分区。
            //
            // ⚠️ **不把 grid 嵌进 `verticalScroll`**：那会撞
            // `Vertically scrollable component was measured with an infinity maximum height`
            // （本仓记过这次真崩溃）。分区标题做成整行 span，而不是外层 Column 的一节。
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 132.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = tokens.spacing.space6),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
            ) {
                sections.forEach { section ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = section.title, count = section.comics.size)
                    }
                    items(section.comics) { card ->
                        ComicCell(
                            card = card,
                            masked = maskedOf(card),
                            onOpen = { onOpen(card) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    val tokens = VeneraTokens
    Column(modifier = Modifier.fillMaxWidth().padding(top = tokens.spacing.space2)) {
        Text(
            text = title.ifBlank { "未命名分区" },
            fontSize = tokens.type.sectionTitle,
            color = tokens.color.textPrimary,
        )
        Text(
            text = "$count 本",
            fontSize = tokens.type.caption,
            color = tokens.color.textTertiary,
        )
    }
}

@Composable
private fun ComicCell(
    card: EngineSession.ComicCard,
    masked: Boolean,
    onOpen: () -> Unit,
) {
    val tokens = VeneraTokens
    VeneraCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.72f)) {
                VeneraCover(
                    url = card.cover,
                    contentDescription = card.title,
                    mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                )
            }
            Spacer(Modifier.height(tokens.spacing.space1))
            Text(
                text = card.title,
                fontSize = tokens.type.caption,
                color = tokens.color.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.space2)
                    .padding(bottom = tokens.spacing.space2),
            )
        }
    }
}
