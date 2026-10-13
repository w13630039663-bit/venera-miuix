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
 * 首页「可能你感兴趣」那一横排在宽屏档排几张。
 *
 * 为什么不用 [comicListColumnCount]：那一把尺是给**整面网格**用的（每 220dp 一列，卡片越大越好吃信息），
 * 而推荐只有**一行**，横在屏幕顶上。1280×800 的平板上它给 4 列 ⇒ 每张约 232dp 宽、
 * 封面约 318dp 高 —— 半屏多高全是封面，用户读到的就是"卡太大、一屏放不下几本"（2026-10-13 反馈）。
 *
 * 这一把改成**按卡片自身应有的宽倒推列数**，而那个宽用的是应用内现成的口径
 * [com.venera.compose.ui.tokens.VeneraSpacing.historyCardWidth]（历史行那 124dp，
 * 而推荐行的骨架 `RecommendSkeleton` 本来也是按这枚画的 —— 之前只有真数据那一支没照它走）。
 *
 * 取 floor 而不是 round/ceil：**每张的实宽永远不窄于 token**（宁可把除不尽的余宽摊给每张），
 * 而且越宽越贴近 token —— 960dp 上 7 张、每张 126.9dp（比 token 多 2.9）。
 * 反过来取 ceil 就会在窄一档时把卡压到 98dp 那种"比历史行还小"的观感。
 * ⚠️ 窄端不成立：320dp 上 floor 只给 2 张、每张 154dp —— 那一档由调用点的
 * `comicListColumnCount(...) >= 3` 闸门挡着（手机档根本不走这一排），所以不影响观感，
 * 但别把"实宽 ≤ token + 一枚间距"当成这条函数的性质写进别处。
 */
fun recommendStripColumns(availableWidth: Dp, cardWidth: Dp, gap: Dp): Int {
    val slot = cardWidth.value + gap.value
    if (slot <= 0f) return 1
    return ((availableWidth.value + gap.value) / slot).toInt().coerceAtLeast(1)
}

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
