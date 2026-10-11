package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.domain.GalleryDailyFeed
import io.github.composefluent.component.Text
import androidx.compose.foundation.layout.fillMaxSize

/**
 * Hero 区（稿 `.top`，`docs/designs/windows-gallery-home-touhou-2026-10-03.html:146`）：
 * **左侧 1fr 大位 + 右侧两张小卡**。
 *
 * ## 这一版比稿上少三样，原因是它们今天**不存在**
 *
 * - 稿上 hero 是「今日推荐 · 今日の一枚」，带作品名、画师、标签 chip、三个动作钮；
 *   动作钮（查看原图/收藏/下载）**一个都不画** —— 画成能点的样子就是三枚假开关。
 *   ⚠️ 这一条的理由**换过一次**：从前写的是"桌面没有大图页也没有详情态"，而第十一批 P0
 *   已经把详情浮层接上了（[DesktopGalleryDetailPanel]，那三枚里的两枚在浮层里是真出口）。
 *   hero 上仍然不画，是因为"在最大那一格上摆两枚能点、一枚不能点"比一枚都不摆更难读 ——
 *   要摆就连下载那枚一起摆齐，那是 P2（桌面落盘件）之后的另一批。
 * - 稿上右侧第一张小卡是「热门画师 · 作品 1.2 万 · Pixiv」；本仓从来没有热门画师榜
 *   （`GalleryFollowedArtists` 走的是**本机关注**，见 `DesktopFollowedArtistsPane`），
 *   所以这里摆的是**本机关注的画师**，且不给作品数。
 * - 稿上第二张是「热门标签」；`BoardSource` 没有 count 成员、词典 sqlite 也不随包分发，
 *   所以这里摆**当页分类计数**并标「当页」，不给"12.4 万作品"那种全站数。
 *
 * ## 拿哪张当大位：**种子序第一张**，不是"分数最高"
 *
 * 墙上那 40 张已经被 `GalleryMerge` 按种子打乱过（`gallery/domain/GalleryMerge.kt:27`），hero 若另按分数挑
 * 一张，就等于**在用户已经看到的集合之外多出一个选择** —— 点了却没有下一页可翻。
 * 所以 hero 取的就是墙上那 40 张里的第一张：大位与墙是同一个集合，不多不少。
 */
@Composable
internal fun DesktopDailyHero(
    daily: GalleryDailyFeed.Daily,
    wall: DesktopWall,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // hero 取的是**过了分级那一关**的第一张。原来它直接取 `daily.merged.posts.firstOrNull()`，
    // 于是"墙上第一张被挡下了"这件事在 hero 上根本不成立 —— 而 hero 拿的还是 `backdropUrl`
    // （中档大图，比墙上的缩略档更清楚），等于把该挡的内容摆在最大最清楚的那一格。
    val lead: GalleryPost? = wall.cards.firstOrNull()?.post
    val leadBackdrop = heroBackdropOf(wall.cards.firstOrNull())
    Row(
        modifier.fillMaxWidth().height(DesktopGalleryMetrics.heroHeight),
        horizontalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.heroGap),
    ) {
        if (lead == null) {
            HeroEmptySlot(Modifier.weight(1f))
        } else {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DesktopTheme.CardBackground),
            ) {
                if (leadBackdrop == null) {
                    // 遮挡档**不发起取图**（判据见 [heroBackdropOf]）：留一块底色 + 一句为什么，
                    // 与卡位那四档同一说法 —— 不许出现"什么都没有"的那一档，
                    // 那与"图坏了"同形，用户会以为今天没取到内容。
                    Box(Modifier.fillMaxSize().background(DesktopTheme.ImagePlaceholder))
                    Text(
                        "分级遮挡",
                        color = DesktopTheme.TextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.Center).padding(12.dp),
                    )
                } else {
                    AsyncImage(
                        // ⚠️ **不许用 `previewUrl`**：它是网格缩略档（300~360px），
                        // 铺到这块 ≈900×330 的大位上等于放大 3 倍 —— 观感就是"糊成一片色块"，
                        // 认不出画的是什么。
                        //
                        // 走 `GalleryPost.backdropUrl`：它就是为"当背景底图"开的那一档
                        // （非视频取中档 `largeUrl`，视频整条转给 `videoPosterUrl` 那道
                        // "中档被兜底成原片 mp4 就退缩略图"的闸门）。画师介绍页的 hero
                        // 用的是同一条判据 —— **不在这里写第二份**。
                        //
                        // 本仓 2026-10-01 已经报过一次同类事故（用户原话「模糊过头」），
                        // 记录就在 `GalleryPost.backdropUrl` 的 KDoc 里。
                        model = leadBackdrop,
                        contentDescription = "今日第一张",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                // 与作品卡同一条遮罩逻辑，压住底部那两行。
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.5f to Color.Transparent,
                            1.0f to DesktopTheme.CardOverlayEnd,
                        ),
                    ),
                )
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
                ) {
                    Text(
                        lead.author.ifBlank { "站方没给画师" },
                        color = DesktopTheme.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(lead.site.displayName, desktopCardMetricLine(lead)).joinToString(" · "),
                        color = DesktopTheme.TextPrimary,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
                // 「换一批」是真动作（`loadDaily(seed)` 换种子本地重排，**不再联网**），
                // 所以它可以点 —— 与稿上那些画不出来的按钮不同，这一枚背后有真行为。
                Text(
                    "重排",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DesktopTheme.OverlayScrim)
                        .clickable(onClick = onShuffle)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    color = DesktopTheme.TextPrimary,
                    fontSize = 11.sp,
                )
            }
        }

        // 右列**固定 320dp**（[DesktopGalleryMetrics.heroSideColumnWidth]），不是第二个 `weight`。
        // 稿 `.top{grid-template-columns:1fr 320px}`（`:146`）—— 两个等权会把非对称压成 1:1，
        // 而「1fr + 固定 320」正是稿 `:657` 标注的非对称来源。
        Column(
            Modifier.width(DesktopGalleryMetrics.heroSideColumnWidth).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.heroGap),
        ) {
            DailyHeroSideCard(
                title = "三站的热门不是同一件事",
                body = "yande.re 是真日榜（${daily.date}）；Gelbooru 与 Safebooru 没有日榜端点，取的是全站高分池。",
                modifier = Modifier.weight(1f),
            )
            DailyHeroSideCard(
                title = "屏上的头三条",
                // 走 [wall]（已过分级那一关）而不是 `daily.merged.posts`：原来这里也直接从**原始池**
                // 取前三条报名，于是墙上写着「3 张被挡下」、这一格却还在念那三条的站名与分数。
                // 只报站名与分数确实不算把内容摆出去，但同一屏上两句互相打脸的话也不该留。
                // 全被挡下时 `wall.cards` 为空，落进下面那句"没有可摆的内容" —— 那正是当时的实情。
                body = wall.cards.take(3)
                    .joinToString("\n") {
                        // 这一格只有分数没有标签（三条各带一串标签就没法在一枚小卡里读），
                        // 但"零分不摆"那条判据共用同一颗件 —— 口径不许在这里被重写一遍。
                        listOfNotNull(it.post.site.displayName, desktopScoreFragment(it.post)).joinToString(" · ")
                    }
                    .ifBlank { "这一轮没有可摆的内容。" },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DailyHeroSideCard(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DesktopTheme.CardBackground)
            .padding(12.dp),
    ) {
        Text(title, color = DesktopTheme.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(
            body,
            color = DesktopTheme.TextSecondary,
            fontSize = 11.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一屏都没有内容时的占位：**说清是"没有"而不是"画不出来"**（本仓最忌的两句合并）。 */
@Composable
private fun HeroEmptySlot(modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(DesktopTheme.CardBackground),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "这一轮没有可摆的内容",
            color = DesktopTheme.TextSecondary,
            fontSize = 13.sp,
        )
    }
}
