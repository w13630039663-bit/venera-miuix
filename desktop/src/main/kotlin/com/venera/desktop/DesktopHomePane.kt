package com.venera.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.venera.compose.components.contentMaxWidthFor
import com.venera.compose.components.wideScreenLayoutMode
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.feature.SourceStatusRowHome
import com.venera.compose.feature.StatBarRow
import com.venera.compose.feature.StatCell
import com.venera.compose.feature.StatDivider
import com.venera.compose.feature.healthOf
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.components.venera.VeneraCard

/**
 * 桌面首页 —— **用安卓首页那一批零件拼的**，不是另画一面墙。
 *
 * ## 与上一版的差别（2026-10-20 用户当场抓到的那一颗）
 *
 * 上一版把**画廊的图片墙**摆在了首页位置，用户原话：「你这个首页怎么和安卓端的完全不一样？」
 * 安卓首页是漫画推荐页（`feature/HomeScreen.kt:114-565` 那一颗 `AndroidHomeScreen`），
 * 结构是：阅读统计 / 可能你感兴趣 / 历史分区 / 漫画源状态。
 *
 * 这一版把那些**零件**（`feature/HomeHomeWidgets.kt`，与安卓同一份源码）摆成同样的结构：
 *  - 阅读统计 = `StatCell` × 3 + `StatDivider`（同一把尺，不是重画的格子）
 *  - 漫画源状态 = `SourceStatusRowHome` + `healthOf`（同一套健康度判据）
 *  - 分区标题行 = 屏标题 + `PillButton`（同一个按钮件）
 *
 * ## 还差什么（不粉饰）
 *
 * 这几格的**数据**在桌面上还是空的（`DesktopHomeStats.Empty`）：真实数字来自
 * `HomeViewModel` → `BusinessPorts`（`ReadingStats` / `SourceCatalog`），
 * 而那 17 个成员的门面还没有桌面实现 —— 那是独立一批，不在本批伪装成已完成。
 *
 * 所以这里每一格都**如实显示"没有数据"**（`StatCell(0, …)` 与"未测速"），
 * 而不是编一批数字。**结构是安卓的，数据是待接的** —— 这两件事分开说清楚，
 * 比"看起来像"更有用。
 */
@Composable
internal fun DesktopHomePane(
    mode: WideScreenLayoutMode,
    stats: DesktopHomeStats,
    /**
     * 历史那一格的数据。默认 [DesktopHomeHistorySnapshot.Loading]（还没读），
     * **不是**"没有历史" —— 这两件事屏上要说不同的话。
     */
    history: DesktopHomeHistorySnapshot = DesktopHomeHistorySnapshot.Loading,
    onOpenHistory: (com.venera.compose.data.db.HistoryRecord) -> Unit = {},
) {
    val tokens = VeneraTokens
    val maxWidth = contentMaxWidthFor(mode)

    LazyColumn(
        modifier = Modifier
            .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
            .fillMaxSize(),
        contentPadding = PaddingValues(
            start = tokens.spacing.screenHorizontal,
            end = tokens.spacing.screenHorizontal,
            top = tokens.spacing.space6,
            bottom = tokens.spacing.space6,
        ),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        item(key = "title") {
            Text(
                text = "首页",
                fontSize = tokens.type.screenTitle,
                color = tokens.color.textPrimary,
            )
        }

        // ── 分区：阅读统计 ──
        // 结构与安卓 `HomeScreen.kt:196-232` 同一形状：标题行 + 一张卡里三格 + 两条竖分隔。
        item(key = "stats") {
            Column {
                Text(
                    text = "阅读统计",
                    fontSize = tokens.type.sectionTitle,
                    color = tokens.color.textPrimary,
                )
                Spacer(Modifier.height(tokens.spacing.space2))
                VeneraCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = tokens.spacing.rowVertical),
                    ) {
                        StatCell(
                            value = stats.todayPages,
                            label = "今日页数",
                            modifier = Modifier.weight(1f),
                        )
                        StatDivider()
                        StatCell(
                            value = stats.weekPages,
                            label = "本周累计",
                            modifier = Modifier.weight(1f),
                        )
                        StatDivider()
                        StatCell(
                            value = stats.streakDays,
                            label = "连续天数",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // ── 分区：历史记录（与安卓 `HomeScreen.kt:335` 对应）──
        //
        // 位置照安卓：统计之后、漫画源之前。
        //
        // ⚠️ 这一格 **item 恒定存在**，只有内容按数据判空。
        //
        // 这是**桌面侧的决定，不是抄安卓**。早先这里引过 `HomeScreen.kt:171-174` 说
        // "安卓踩过这个坑"，那是错引（架构评审 R5）：那段注释在 `item(key="stats")` 正上方、
        // 讲的是**第 0 项**；历史在列表中部，锚位机制不同，而且安卓对历史**就是**条件渲染的。
        //
        // 真实理由是桌面的竞态：数据由 `produceState(initialValue = Loading)` 起步、
        // 异步读后才到值 ⇒ `if` 会让 item 在第二帧插入列表中部，下部整片下移一次。
        item(key = "history") {
            DesktopHomeHistorySection(
                history = history.records,
                loaded = history.loaded,
                // 读失败的成因必须上屏 —— 不传就等于把「库打不开」说成「没有记录」。
                note = history.note,
                onOpen = onOpenHistory,
            )
        }

        // ── 分区：漫画源状态 ──
        // 安卓那一屏在 `HomeScreen.kt` 的 `SourceStatusRowHome` 一节；这里摆同一颗件，
        // 健康度判据也是同一颗 `healthOf`（`null` = 未测速，不是"坏了"）。
        if (stats.sources.isNotEmpty()) {
            item(key = "sources") {
                Column {
                    Text(
                        text = "漫画源",
                        fontSize = tokens.type.sectionTitle,
                        color = tokens.color.textPrimary,
                    )
                    Spacer(Modifier.height(tokens.spacing.space2))
                    VeneraCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(tokens.spacing.space3)) {
                            stats.sources.forEach { source ->
                                SourceStatusRowHome(
                                    name = source.name,
                                    latency = source.latencyMs,
                                    onRetest = {},
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 分区：阅读量按周分布 ──
        if (stats.weekBar.isNotEmpty()) {
            item(key = "bars") {
                Column {
                    Text(
                        text = "本周阅读",
                        fontSize = tokens.type.sectionTitle,
                        color = tokens.color.textPrimary,
                    )
                    Spacer(Modifier.height(tokens.spacing.space2))
                    VeneraCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(tokens.spacing.space3)) {
                            val peak = stats.weekBar.maxOrNull() ?: 1
                            stats.weekBar.forEachIndexed { i, n ->
                                StatBarRow(label = "周${i + 1}", count = n, maxCount = peak)
                            }
                        }
                    }
                }
            }
        }

        // ── 读不到时的成因 ──
        // 只有 `DesktopHomeStatsSource.read()` 抛了才出这一行，且显示的是**真实异常**。
        // 读得到时这里什么都不画 —— 数字已经在那三格里了，不需要再解释自己。
        stats.note?.let { msg ->
            item(key = "note") {
                Text(
                    text = msg,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.widthIn(max = 620.dp),
                )
            }
        }
    }
}

/**
 * 首页那些数字的**桌面侧取值口**。
 *
 * 今天只有 [Empty] 一个实现：`ReadingStats` / `SourceCatalog` 是 `BusinessPorts` 的成员，
 * 而那份门面还没有桌面实现。摆这个口子而不是直接写死 `0` 的理由：
 * 接真数据时只改这一处，不用回头改 UI —— 而 UI 那一侧的形状已经和安卓一致了。
 */
internal data class DesktopHomeStats(
    val todayPages: Int,
    val weekPages: Int,
    val streakDays: Int,
    val sources: List<DesktopHomeSource>,
    val weekBar: List<Int>,
    /** 非空 = 这一屏的数字读不到，成因摆在这儿（屏上照原话说，不改写成 0）。 */
    val note: String? = null,
) {
    /** 源状态那一行：延迟 `null` = 还没测过（`healthOf` 会判成「未测速」）。 */
    data class DesktopHomeSource(val name: String, val latencyMs: Long?)

    companion object {
        /** 桌面侧今天能报的全部事实：**一个数字都没有**。 */
        val Empty = DesktopHomeStats(
            todayPages = 0,
            weekPages = 0,
            streakDays = 0,
            sources = emptyList(),
            weekBar = emptyList(),
        )
    }
}
