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
 * Hero 区（稿 `.top`，`docs/designs/windows-gallery-home-touhou-2026-10-03.html:364`）：
 * **左侧 1fr 大位 + 右侧两张小卡**。
 *
 * ## 这一版比稿上少三样，原因是它们今天**不存在**
 *
 * - 稿上 hero 是「今日推荐 · 今日の一枚」，带作品名、画师、标签 chip、三个动作钮；
 *   桌面**没有大图页也没有详情态**（`DesktopDailyPane` 的类注释里那条理由），所以
 *   动作钮（查看原图/收藏/下载）**一个都不画** —— 画成能点的样子就是三枚假开关。
 * - 稿上右侧第一张小卡是「热门画师 · 作品 1.2 万 · Pixiv」；本仓从来没有热门画师榜
 *   （`GalleryFollowedArtists` 走的是**本机关注**，见 `DesktopFollowedArtistsPane`），
 *   所以这里摆的是**本机关注的画师**，且不给作品数。
 * - 稿上第二张是「热门标签」；`BoardSource` 没有 count 成员、词典 sqlite 也不随包分发，
 *   所以这里摆**当页分类计数**并标「当页」，不给"12.4 万作品"那种全站数。
 *
 * ## 拿哪张当大位：**种子序第一张**，不是"分数最高"
 *
 * 墙上那 40 张已经被 `GalleryMerge` 按种子打乱过（`GalleryMerge.kt:26`），hero 若另按分数挑
 * 一张，就等于**在用户已经看到的集合之外多出一个选择** —— 点了却没有下一页可翻。
 * 所以 hero 取的就是墙上那 40 张里的第一张：大位与墙是同一个集合，不多不少。
 */
@Composable
internal fun DesktopDailyHero(
    daily: GalleryDailyFeed.Daily,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lead: GalleryPost? = daily.merged.posts.firstOrNull()
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
                AsyncImage(
                    model = lead.previewUrl.ifBlank { lead.largeUrl },
                    contentDescription = "今日第一张",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
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
                        "${lead.site.displayName} · 分数 ${lead.score} · 标签 ${lead.tagList.size} 枚",
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
                title = "这一轮取到了什么",
                body = daily.merged.posts.take(3)
                    .joinToString("\n") { "${it.site.displayName} · 分数 ${it.score}" }
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
