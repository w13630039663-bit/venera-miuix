package com.venera.compose.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.venera.compose.components.venera.VeneraGallerySourceMark
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.domain.GalleryCardTitle
import com.venera.compose.gallery.domain.GalleryTitleTag
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 卡片底部那一行：**来源标识 + 标题**（对齐参考图的形态）。
 *
 * 来源摆**这一行**（不再压在封面角上）：参考图里那枚站标与标题同处封面下方的一行，
 * 而压角方案在两站混排时要用户从图的四个角里找，反而更难读。
 *
 * 标题为 `null` 时**整行不画**（不是画一行空文字）：回退链四档（作品·角色 → 单档 → 画师名 →
 * 通用标签译名，见 `GalleryCardTitle`）都没命中的图就不该有那一行，
 * 摆一行空的会在横滑行里把封面高度参差拉开。
 *
 * @param title 已经算好的标题（见 [rememberGalleryCardTitles]）；null = 这一张没有可显示的标题。
 */
@Composable
internal fun GalleryCardCaption(
    site: com.venera.compose.gallery.data.GallerySite,
    title: String?,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        VeneraGallerySourceMark(
            site = site,
            size = tokens.spacing.sourceMarkSize,
        )
        if (title != null) {
            Text(
                text = title,
                // 回到 caption（批次 N · N4）。批次 M · M7 那一句原文是：
                // 「降一档：这一行的主体是封面，标题是署名级别的补充；与旁边那枚站标（18dp）同一量级才读成一行」——
                // 当时用户说的是"图标太大、字体也大"，我把两样一起降了，结果标题比参考图还小一档。
                // 图标那一半（18dp）是对的、留着；字号这一半今天回改。
                fontSize = tokens.type.caption,
                color = tokens.color.textPrimary,
                maxLines = 1,
                // 一张画可能挂着好几个作品/角色档的标签，标题会很长 —— 省略号收口，
                // 卡片宽度是定死的（横滑行里每一枚都同宽）。
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 这一批条目的**卡片标题**（按 uid 索引）。
 *
 * ── 为什么是"整批查一次"而不是"每张卡各查一次" ──
 *
 * 标题来自离线词典（`GalleryTagLexicon.titleTags`），而它是要开 SQLite 的：
 * 一屏几十张卡各查一次 = 几十趟查询 + 几十次游标开关。整批摊平去重后查一次，
 * 成本与"一屏多少张"基本无关（名字去重之后也就几百上千枚，那层还有分批）。
 *
 * ── 为什么 `null` 与空 map 要分得开 ──
 *
 * - **`null`（词典打不开）**：屏上表现为"所有卡都没有标题行"。这是**我们的故障**，
 *   与"这些图确实没有作品/角色标签"在屏幕上长得一模一样 —— 所以打一条 warn 日志，
 *   别让它静默（同 `GalleryTagDictionary.openDatabase` 那条口径）。
 * - **空 map**：正常的"这一批图都没有作品/角色标签"，不打日志。
 *
 * @return `uid → 标题`；没有标题的那些 uid **不会出现在表里**（调用方据此不画那一行）。
 */
@Composable
internal fun rememberGalleryCardTitles(posts: List<GalleryPost>): Map<String, String> {
    val context = LocalContext.current
    // 摊平这批图的所有标签去重：它是查询键，也是 remember 的键 ——
    // 键错了就会拿着上一批的名字画这一批的标题（"卡上写着 A 串、点进去是 B"那类返祖）。
    val names = remember(posts) { posts.flatMap { it.tagList }.map { it.lowercase() }.distinct() }
    var labels by remember(names) { mutableStateOf<Map<String, GalleryTitleTag>>(emptyMap()) }
    LaunchedEffect(names) {
        if (names.isEmpty()) {
            labels = emptyMap()
            return@LaunchedEffect
        }
        val found = GalleryPorts.of(context).lexicon.titleTags(names)
        if (found == null) {
            // 词典打不开：这一批卡都不会有标题行。这条必须响 —— 静默的话它和
            // "这些图没有作品标签"完全同形（理由见上面那段）。
            android.util.Log.w(TAG, "画廊标签词典打不开，卡片标题这一批全部不可用（${names.size} 个名字）")
            labels = emptyMap()
        } else {
            labels = found
        }
    }
    // 只有"这一批图 + 这一份词典"都定了才重算，避免每次重组重拼字符串。
    return remember(posts, labels) {
        if (labels.isEmpty()) {
            emptyMap()
        } else {
            posts.mapNotNull { post ->
                GalleryCardTitle.titleOf(post.tagList, labels)?.let { post.uid to it }
            }.toMap()
        }
    }
}

private const val TAG = "GalleryCardTitle"
