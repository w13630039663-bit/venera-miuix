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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPost
import com.venera.desktop.gallery.data.DesktopDecoTier
import io.github.composefluent.component.Text

internal data class DesktopSegment(val label: String, val reason: String?)

/**
 * 三档：推荐 / 最新 / 排行。
 *
 * ⚠️ 哪几档的 `reason` 为 null **不在这里写死**：它由那张六项表的 `content` 现数（有内容件 ⇒ null）。
 * 批 28 起三档全为 null（「排行」那一屏点亮之后，段头不再有带原因的档）。
 * 这一格由 `DesktopStaleClaimsTest` ⑫ **真跑**钉着 —— 改那张表时这里会红，而不是让注释漂着。
 * 原因串只有一个来源，这不是巧合而是同一件事的两种说法。
 */
internal fun desktopGallerySegments(): List<DesktopSegment> {
    val rows = desktopGalleryHomeRows()
    fun keyed(key: String, label: String): DesktopSegment = DesktopSegment(label, rows.byKey(key).reason)
    return listOf(
        keyed(DesktopGalleryHomeSectionKey.DAILY, "推荐"),
        keyed(DesktopGalleryHomeSectionKey.LATEST, "最新"),
        keyed(DesktopGalleryHomeSectionKey.RANKING, "排行"),
    )
}

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
