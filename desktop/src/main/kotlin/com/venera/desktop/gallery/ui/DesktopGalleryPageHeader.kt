package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPost
import io.github.composefluent.component.Text

/**
 * 主区段头（稿 `.phead`：CSS `:123-129`，HTML `:387-395`）——
 * 「标题 + 子标题 ／ 分段控件 ／ 标签 chips + 换一批」三簇，高 52 固定。
 *
 * ## 每一簇里哪些数是真的（这一版**没有一颗是照稿抄的假读数**）
 *
 * | 位置 | 稿上是 | 这一版给的是 | 出处 |
 * |---|---|---|---|
 * | 主标题 | 「图库 · 今日」 | 同文案 | 域 + 视图名，描述本身就是真的 |
 * | 子标题 | 「10-03 · 三站并行 · 每站 12s 预算」 | `${date} · 三站并行 · 每站 ${budgetLabel}` | 日期是 `GalleryDailyFeed.Daily.date` 原值；预算由 `PER_SITE_TIMEOUT_MS` 换算 |
 * | chips | 东方Project / 少女 / 4k / +6 | **当页作品的标签出现频次前 N** | [desktopTopTags]，`+N` 是剩余 distinct 标签数 |
 * | 换一批 | 一枚刷新的小钮 | 同枚，**背后真有动作** | `loadDaily` 换种子本地重排，不再联网 |
 *
 * ⚠️ 子标题里的「三站并行」描述的是**请求形状**（`loadDaily` 里 `coroutineScope` 起三笔 `async`，
 * 见 `GalleryDailyFeed.kt:73-78`），不是说三站都答上了 —— 有几站真给内容由状态栏报
 * （[DesktopGalleryStatusBar]），那一句才是结果读数。
 *
 * ⚠️ 日期不做成 `10-03` 那种短写：稿那枚短写看着像"今天"，而 `Daily.date` 实际是**昨日**
 * （`GalleryDailyFeed.yesterdayString()`）。写成全 `yyyy-MM-dd` 免得被读成当日。
 *
 * ## 分段控件三档：**只有「推荐」今天真的有东西**
 *
 * 「最新」与「排行」在稿上画出来了，但桌面这两个视图不存在 —— pane 里它们就是两行未实现行。
 * 按本仓"不许有假开关"的纪律，正确做法不是把两项画灰（那是"未实现"的形状，读起来像点进去能看到说明），
 * 而是 **点就把原因说出来**：原因串**逐字复用** [desktopGalleryHomeRows] 里那两行的 `reason`
 * （不去重写第二份 —— 重写过一遍就会漂，这是本仓记过的那一类）。
 *
 * 所以调用方拿到的是 [DesktopSegment.reason]：非空 ⇒ 不要切换选中项，把这句话交给用户。
 *
 * ## 色值与稿的三处收敛（不许再开第六档中性灰）
 *
 * 稿给的三枚 secondary 面色（`.chip` `#333333`、`.seg` `#2A2A2A`、`.seg i.on` `#383838`）
 * 与既有
 * [DesktopTheme.SurfaceRaised]（`#353535`）/ [DesktopTheme.SidebarBackground]（`#272727`）
 * **各差 2~3 个色号，肉眼无法分辨**。按 `DesktopTheme` 那条「五档是上限、不许再加差不多灰」，
 * 这里一律取既有那一档，不新增：
 *
 * | 稿值 | 采用 | 差 |
 * |---|---|---|
 * | `.chip` `#333333` | [DesktopTheme.SurfaceRaised] `#353535` | 2 |
 * | `.seg` `#2A2A2A` | [DesktopTheme.SidebarBackground] `#272727` | 3 |
 * | `.seg i.on` `#383838` | [DesktopTheme.SurfaceRaised] `#353535` | 3 |
 *
 * 唯一新增的两枚是 beni 族 chip（稿 `.chip.beni` `#4A2220` / `#FFB4A8`）—— 它们有明确色相，
 * 不属于那条禁令管的"中性灰"。
 */
@Composable
internal fun DesktopGalleryPageHeader(
    date: String,
    budgetLabel: String,
    tags: List<String>,
    hiddenTagCount: Int,
    selectedIndex: Int,
    onSegment: (DesktopSegment) -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(DesktopGalleryMetrics.pageHeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.pageHeaderGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "图库 · 今日",
                color = DesktopTheme.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "$date · 三站并行 · 每站 $budgetLabel 预算",
                modifier = Modifier.padding(start = DesktopGalleryMetrics.pageHeaderSubMargin),
                color = DesktopTheme.TextTertiary,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        DesktopSegmented(
            items = desktopGallerySegments(),
            selectedIndex = selectedIndex,
            onSelect = onSegment,
        )

        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            tags.forEachIndexed { index, tag ->
                DesktopChip(tag, beni = index == 0)
            }
            if (hiddenTagCount > 0) {
                DesktopChip("+$hiddenTagCount", beni = false)
            }
            DesktopSwitchBatchButton(onShuffle)
        }
    }
}

/** 分段控件的一档。[reason] 非空 ⇒ 这一档今天接不上（原因串来自 pane 注册表，不是这里另写）。 */
internal data class DesktopSegment(val label: String, val reason: String?)

/**
 * 三档：推荐 / 最新 / 排行。
 *
 * ⚠️ 「推荐」的 [DesktopSegment.reason] 必为 `null`（它在 pane 里是有内容件的一行）；
 * 另两档必非 `null`。这不是巧合而是同一件事的两种说法 —— 原因串就是从一个来源来的。
 */
internal fun desktopGallerySegments(): List<DesktopSegment> {
    val rows = desktopGalleryHomeRows().associateBy { it.key }
    fun keyed(key: String, label: String): DesktopSegment {
        val row = rows[key] ?: error("pane 注册表里没有 key「$key」—— 段头与 pane 必须对同一份六项表说话")
        return DesktopSegment(label, row.reason)
    }
    return listOf(
        keyed(DesktopGalleryHomeSectionKey.DAILY, "推荐"),
        keyed(DesktopGalleryHomeSectionKey.LATEST, "最新"),
        keyed(DesktopGalleryHomeSectionKey.RANKING, "排行"),
    )
}

@Composable
private fun DesktopSegmented(
    items: List<DesktopSegment>,
    selectedIndex: Int,
    onSelect: (DesktopSegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = DesktopGalleryMetrics
    Box(
        modifier
            .background(DesktopTheme.SidebarBackground, RoundedCornerShape(metrics.controlRadius))
            .border(1.dp, DesktopTheme.SurfaceRaised, RoundedCornerShape(metrics.controlRadius))
            .padding(metrics.segmentedInnerPadding),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.segmentedItemGap)) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                Text(
                    item.label,
                    modifier = Modifier
                        .background(
                            color = if (selected) DesktopTheme.SurfaceRaised else Color.Transparent,
                            shape = RoundedCornerShape(metrics.segmentedItemRadius),
                        )
                        // ⚠️ 三项都可点：接不上的那两档点了给原因，而不是画成灰的让人去猜能不能点。
                        //    同一条纪律 rail 上那两枚域按键已经在用（`DesktopGalleryDomain.notWiredReason`）。
                        .clickable { onSelect(item) }
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
 * 一枚标签 chip（稿 `.chip` `:137`）。
 *
 * [beni] 只给**第一枚**：稿把首枚做成 beni 强调。⚠️ 它**不代表那枚标签有什么特殊权重** ——
 * 这一版的 chips 按出现频次排，第一位只是排第一，给它强调色纯属视觉引导，
 * 理由写在这里是因为"看起来被标红了"很容易被读成"它是重点/被选中"。
 */
@Composable
private fun DesktopChip(text: String, beni: Boolean, modifier: Modifier = Modifier) {
    val metrics = DesktopGalleryMetrics
    Text(
        text,
        modifier = modifier
            .background(
                color = if (beni) DesktopTheme.ChipBeniBackground else DesktopTheme.SurfaceRaised,
                shape = RoundedCornerShape(metrics.chipHeight / 2),
            )
            .padding(horizontal = metrics.chipHorizontalPadding),
        color = if (beni) DesktopTheme.ChipBeniContent else DesktopTheme.TextSecondary,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 「换一批」（稿 `.phead .right .btn.sm` `:393`）。背后是真动作 —— 换种子本地重排。 */
@Composable
private fun DesktopSwitchBatchButton(onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val metrics = DesktopGalleryMetrics
    Text(
        "换一批",
        modifier = modifier
            .height(metrics.smallButtonHeight)
            // 常态 `#2C2C2C`（稿 `.btn` 的 `#2D2D2D`，差 1 个色号 —— 收为既有 CardBackground），
            // 悬停上一档到 SurfaceRaised，与 pane 行的 hover 同一种语言。
            .background(
                color = if (hovered) DesktopTheme.SurfaceRaised else DesktopTheme.CardBackground,
                shape = RoundedCornerShape(metrics.controlRadius),
            )
            .border(1.dp, DesktopTheme.SurfaceRaised, RoundedCornerShape(metrics.controlRadius))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onShuffle)
            .padding(horizontal = metrics.smallButtonHorizontalPadding),
        color = DesktopTheme.TextSecondary,
        fontSize = 12.5.sp,
        maxLines = 1,
    )
}

/**
 * 段头 chips 的数据：**当页**（也就是这一屏 `posts`）里标签的出现频次前 [limit] 名。
 *
 * ⚠️ 这**不是全站热度**：三站站方既不提供标签热度也不提供全站计数，
 * 只有这一屏那几十张各自的 `tagList`。稿 `.phead` 那几枚 chip 是手摆的占位词，
 * 而 `.side` 那块 minicard 自己也写了「这些数是当页分类计数，不是全站数」（`:434`）——
 * 所以这里取当页频次，是本仓"摆这一条路真拿得到的东西"的同一个办法。
 *
 * 排序在计数之后按**标签名字典序**再稳一层：仅按 count 排时同频次的名次会随 `tagList` 顺序抖，
 * 每次重排都换次序，看着就像"页面自己动了"。
 *
 * @return 前 [limit] 个标签，以及**还剩多少个 distinct 标签**（给那个 `+N` chip）。
 */
internal fun desktopTopTags(posts: List<GalleryPost>, limit: Int = 4): Pair<List<String>, Int> {
    val counts = HashMap<String, Int>()
    posts.forEach { post ->
        post.tagList.forEach { tag ->
            if (tag.isNotBlank()) counts[tag] = (counts[tag] ?: 0) + 1
        }
    }
    val ordered = counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }
    return ordered.take(limit) to (ordered.size - limit).coerceAtLeast(0)
}
