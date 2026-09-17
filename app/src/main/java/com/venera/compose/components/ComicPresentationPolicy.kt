package com.venera.compose.components

/** Invalid/legacy modes fall back to the supported two-column layout. */
fun normalizeComicDisplayMode(mode: String?): String = if (mode == "detailed") "detailed" else "brief"

fun comicListColumnCount(mode: String?): Int = if (normalizeComicDisplayMode(mode) == "detailed") 1 else 2

/** Zero is valid when explicitly supplied. Missing, non-finite and negative values are absent. */
fun comicMetricsText(rating: Double?, likesCount: Int?): String = buildList {
    rating?.takeIf { it.isFinite() && it >= 0.0 }?.let {
        add("评分 "+ java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString())
    }
    likesCount?.takeIf { it >= 0 }?.let { add("点赞 $it") }
}.joinToString(" · ")
