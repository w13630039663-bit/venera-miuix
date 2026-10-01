package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GallerySite

/**
 * 头像地址缓存的**纯判据**：查没查过、存哪一条、超出上限丢谁。
 *
 * 为什么单独立一个文件：这几条判断与 Android、与磁盘格式都无关，要能脱离 gradle 单跑；
 * 而"落盘"那一半（读写文件、改名留档）只在 [com.venera.compose.gallery.data.GalleryArtistAvatarStore]。
 *
 * ## 三档语义，缺任何一档都会长出怪事
 *
 * - **没有条目** = 从没查过 → 该发请求；
 * - **条目里是空串** = 查过了、站方确实没给脸 → **不许再查**，
 *   否则每一位没头像的画师都会在每次冷启动被重问一遍（这条口径来自原来那份进程内缓存）；
 * - **条目里有地址** = 直接拿来画，图字节由画廊那份 Coil 磁盘缓存负责（实测缩略档
 *   `Cache-Control: max-age=31536000`，回看不回网）。
 *
 * ## 键为什么带出处
 *
 * 与 [GalleryArtistCreditsKey] 同一个键、同一条理由：那一枚 pixiv 入口有时是**从出处反查**来的，
 * 同一位的两张图挂不同出处时反查到的未必是同一个人 —— 混用一份会给这一位挂上另一位的脸。
 */
internal object GalleryArtistAvatars {

    /** 盘上最多留多少条。画师头像地址一条几十字节，600 条量级在几十 KB —— 再大就是囤整站。 */
    const val MAX_ENTRIES = 600

    /**
     * 一条已解析过的头像地址。[avatar] 为**空串**表示"查过了、没有"，不是"没查过"。
     *
     * [resolvedAt] 只在超上限时用来挑最旧的那几条丢，不参与任何"过期"判断 ——
     * 这一档是用户 2026-10-01 拍的「落盘、不加 TTL」：实测 pixiv 的头像地址
     * 是 `i.pximg.net/user-profile/img/<上传日期>/…_170.jpg` 这种**不带过期参数**的静态路径
     * （一条 2017 年的头像今天仍回 200，且 `max-age=31536000`）。
     */
    data class Entry(
        val site: GallerySite,
        val name: String,
        val source: String,
        val avatar: String,
        val resolvedAt: Long,
    ) {
        val key: String get() = GalleryArtistCreditsKey.of(site, name, source)
    }

    /** 查一次缓存：null = 从没查过；空串 = 查过、没有；其余 = 可直接画的地址。 */
    fun lookup(byKey: Map<String, Entry>, site: GallerySite, name: String, source: String): String? =
        byKey[GalleryArtistCreditsKey.of(site, name, source)]?.avatar

    /**
     * 写入一条并盖掉同键的旧值（同址重复解析不该攒两份），随后按 [MAX_ENTRIES] 截断。
     *
     * 丢的是 [resolvedAt] 最早的那批 —— 它们最不可能还在这一屏上。
     */
    fun put(entries: List<Entry>, entry: Entry, max: Int = MAX_ENTRIES): List<Entry> {
        val withoutOldKey = entries.filterNot { it.key == entry.key }
        return (withoutOldKey + entry)
            .sortedByDescending { it.resolvedAt }
            .take(max)
    }

    /** 把盘上读回来的一堆整成查找表；同键重复行留 [resolvedAt] 最新的那一条。 */
    fun index(entries: List<Entry>): Map<String, Entry> =
        entries.sortedBy { it.resolvedAt }.associateBy { it.key }
}
