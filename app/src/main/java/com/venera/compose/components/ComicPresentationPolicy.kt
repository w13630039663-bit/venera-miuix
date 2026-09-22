package com.venera.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Invalid/legacy modes fall back to the supported two-column layout. */
fun normalizeComicDisplayMode(mode: String?): String = if (mode == "detailed") "detailed" else "brief"

/**
 * 列表列数：与 master 的 `SliverGridDelegateWithComics` 逐字同口径。
 *
 *  - `brief` = `max(2, width ~/ 220)`（master 注释：手机双列；宽屏按 220dp 一列自适应加列）
 *  - `detailed` = `max(1, width ~/ 360)`（`getDetailedModeLayout`）
 *
 * 宽度传**网格自身**的可用宽，不传屏幕宽：分屏与自由窗口下两者不等，
 * 而且左右内边距各页不同（12/14dp），拿屏幕宽会在 220 的整数倍附近算错一档。
 */
fun comicListColumnCount(mode: String?, availableWidth: Dp): Int =
    if (normalizeComicDisplayMode(mode) == "detailed") {
        (availableWidth.value / 360f).toInt().coerceAtLeast(1)
    } else {
        (availableWidth.value / 220f).toInt().coerceAtLeast(2)
    }

/**
 * 详情页预览列数：master `thumbnails.dart` 用
 * `SliverGridDelegateWithMaxCrossAxisExtent(maxCrossAxisExtent: 200)`，即格宽上限 200dp。
 *
 * 下限取 3：手机端本轮之前就是三列，观感已定稿，大屏只许加列不许减。
 */
fun comicPreviewColumnCount(availableWidth: Dp): Int =
    kotlin.math.ceil(availableWidth.value / 200f).toInt().coerceAtLeast(3)

/**
 * 量宿主自身的可用宽。
 *
 * 返回 `(去掉左右内边距后的可用宽, 需挂在宿主 modifier 链上的测量修饰符)`。
 * 首帧用窗口宽兜底：实测宽要等一次布局完成，若从 0 起步，平板落地那一帧会先按
 * 最少列数铺满、再炸成多列 —— 就是你反复否过的那种「落地闪」。
 */
@Composable
fun rememberContentWidth(horizontalContentPadding: Dp = 0.dp): Pair<Dp, Modifier> {
    val density = LocalDensity.current
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val padding = horizontalContentPadding.value
    var width by remember(padding) { mutableFloatStateOf((windowWidth - horizontalContentPadding).value) }
    return width.dp to Modifier.onSizeChanged {
        width = with(density) { it.width.toDp() }.value - padding
    }
}

/** Zero is valid when explicitly supplied. Missing, non-finite and negative values are absent. */
fun comicMetricsText(rating: Double?, likesCount: Int?): String = buildList {
    rating?.takeIf { it.isFinite() && it >= 0.0 }?.let {
        add("评分 "+ java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString())
    }
    likesCount?.takeIf { it >= 0 }?.let { add("点赞 $it") }
}.joinToString(" · ")
