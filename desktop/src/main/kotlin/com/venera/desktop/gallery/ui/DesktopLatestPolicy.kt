package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySearchSource

/**
 * 「最新」那一屏的取数计划（桌面第十批 ⑤）。
 *
 * ## 为什么这一屏**不需要**任何排序参数
 *
 * 手册 §23.9 量完的读数：
 *
 * | 站的口 | 默认序 | 实测 |
 * |---|---|---|
 * | Safebooru（那颗 client 走的是 `safebooru.donmai.us`） | 最新在前 | ids `12315048, 12315046, 12315044` |
 * | yande.re | 最新在前 | ids `1269963, 1269962, 1269961`；且它的 `order:` 标签整族不换序 |
 * | Gelbooru | 没量 | 匿名一律 401（既有账），要 `api_key` + `user_id` |
 *
 * 而**空标签**在前两站都是合法查询（200 非空）。所以"最新"就是 `searchPosts("", page = 1, limit = 一页)`，
 * 共享接口一个字都不必改 —— 给它加 `sort` 会牵动 Android 侧的冻结面，而那一枚今天没有对象可喂。
 * 这条由 `DesktopLatestTest` ⑥ 钉着。
 *
 * ## 桌面这一侧一行都不排
 *
 * 只拿到每一站的第一页，本地再排一次排出来的是"三站第一页里的前 N 条"，
 * 那**不是**最新，而屏上却会读成最新。所以本地排序在这一屏是禁止项（判据 ④）。
 */
internal const val DESKTOP_LATEST_QUERY: String = ""

/**
 * 这一轮该发哪几条腿（**墙上通用**：「最新」与「排行榜」两屏同一份腿账，批 28 起不再叫 Latest）。
 *
 * Gelbooru 没配凭据时**发车前**就不发：匿名一律 401，发出去只是让用户白等一轮网络超时。
 * 次序恒为枚举自然序 —— 它同时是屏上点名那几行的读序，跟着数据飘会变成"每次进来进来次序不一样"。
 */
internal fun desktopWallLegs(gelbooruConfigured: Boolean): List<GallerySite> =
    GallerySite.entries.filter { it != GallerySite.GELBOORU || gelbooruConfigured }

/**
 * 没发出去的站，逐座点名 + 原因。
 *
 * 那句**逐字转交** [GallerySearchSource.NEEDS_ACCOUNT_REASON]：桌面不另写一份，
 * 否则同一件事在两端长成两种说法（这一族已经错过一回）。
 */
internal fun desktopWallSilentSites(gelbooruConfigured: Boolean): Map<GallerySite, String> =
    if (gelbooruConfigured) {
        emptyMap()
    } else {
        mapOf(GallerySite.GELBOORU to GallerySearchSource.NEEDS_ACCOUNT_REASON)
    }
