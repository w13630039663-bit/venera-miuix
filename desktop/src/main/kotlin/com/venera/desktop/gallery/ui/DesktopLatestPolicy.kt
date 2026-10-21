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
 * 桌面**真接了线**的画廊站全集 = `GallerySite.entries` 剥掉 `PIXIV`。
 *
 * 2026-10-20 补回：这颗原先在 deco 那批改动里（`docs/HANDOFF-2026-10-04.md:1121` 第 212 条
 * 逐字记着「新增桌面侧 `DESKTOP_GALLERY_SITES`（`DesktopLatestPolicy.kt`，= `entries` 剥掉 pixiv）」），
 * 本轮 `git checkout` 时与那批一起丢了，于是 [desktopWallLegs] 退回到 `GallerySite.entries` ——
 * 而那会把 pixiv 也发出去，可桌面侧的 pixiv 取数腿至今**零调用点**（同一份 HANDOFF 的挂账①）。
 *
 * ⚠️ 这里必须剥 pixiv 而不是"发出去再说"：pixiv 的三条腿在桌面侧是 `notWired()`，
 * 发车等于让用户白等一轮异常。同理，`DesktopForYouPlan.silentSites` 也走这一颗。
 *
 * ⚠️ **「哪几站失败了」那一本账照旧收全集**（`GallerySite.entries`）—— 收窄等于把
 * 没接线那站的失败藏起来。两族同字面不同门，这是 HANDOFF 里那条规则的原话。
 */
internal val DESKTOP_GALLERY_SITES: List<GallerySite> =
    GallerySite.entries.filter { it != GallerySite.PIXIV }

/**
 * 这一轮该发哪几条腿（**墙上通用**：「最新」与「排行榜」两屏同一份腿账，批 28 起不再叫 Latest）。
 *
 * Gelbooru 没配凭据时**发车前**就不发：匿名一律 401，发出去只是让用户白等一轮网络超时。
 * 次序恒为枚举自然序 —— 它同时是屏上点名那几行的读序，跟着数据飘会变成"每次进来进来次序不一样"。
 */
internal fun desktopWallLegs(gelbooruConfigured: Boolean): List<GallerySite> =
    DESKTOP_GALLERY_SITES.filter { it != GallerySite.GELBOORU || gelbooruConfigured }

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
