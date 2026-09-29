package com.venera.compose.gallery.domain

/**
 * 「缓存那一屏还能不能用、要不要再联网取一次」的判据。
 *
 * 用户 2026-09-29 的口径是「现在每次进入应用画廊都会刷新加载，很不好」——
 * 屏上明明有内容，却每次进来都转一圈圈重取。但日榜的内容按天换，
 * 所以"能用缓存"的边界只能是**同一天**：跨天了必须补拉，否则这一屏永远是旧的。
 *
 * 补拉是**后台**补：屏上先摆着缓存那一屏，新的一片到了再整片换掉
 * （页面那侧的判据是 `isLoading && !hasPosts` 才摆加载环，见 `GalleryDailyPage`）。
 */
object GalleryFeedRefreshPolicy {

    /**
     * @param savedEpochDay 缓存写入那天（`LocalDate.toEpochDay`）；null = 根本没有缓存。
     * @param todayEpochDay 今天（同一口径，调用方用 UTC 与日榜取数的"那一天"对齐）。
     */
    fun shouldRefetch(savedEpochDay: Long?, todayEpochDay: Long): Boolean =
        savedEpochDay == null || savedEpochDay != todayEpochDay
}
