package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GallerySite

/**
 * 一位被关注的画师。
 *
 * 只有**名字与站点**两样事实，别的都不存：
 * - 不存 pixiv 用户 id 与头像 URL —— 那些是会变的（实测老 id `1360347` 在 pixiv 那边已经取不到，
 *   `error=true`）。存下来就会在半年后把一个过期地址当"这位的头像"摆出来。
 *   元数据永远在需要时现取一次（用户 2026-09-30 拍板「面板打开才取」）。
 * - 不存"这位有几张新图"—— 仓库里没有任何更新来源，那个数只能编。
 *
 * @param addedAt 关注那一刻的毫秒时间戳，用来定序（[GalleryArtistFollows.sorted]）。
 */
data class GalleryArtistFollow(val site: GallerySite, val name: String, val addedAt: Long) {

    /** 身份键，口径与收藏那边一致：`站点的 routeKey` + 名字。 */
    val uid: String get() = uidOf(site, name)

    companion object {
        /**
         * 名字**按小写比对**：画师标签在这两站本来就是小写下划线形态，而交接路径上
         * 可能出现大小写不一致的写法（从别处复制的一串）。比对不认大小写，
         * 但**摆出来与发出去用的仍是添加时那个原样** —— 篡改用户看到的字是另一类错。
         */
        fun uidOf(site: GallerySite, name: String): String = "${site.routeKey}:${name.trim().lowercase()}"
    }
}

/**
 * 关注名单的纯判据（批次 L）。
 *
 * 两条决定名单形状的判据：
 *
 * 1. **跨站同名是两条，不并成一条**。两站的画师标签词表只有部分重合（批次 J 量到头部画师
 *    22/25 同名，剩下 16~20% 分歧），并成一条就会出现「从 yande.re 那条点过去、
 *    实际搜的是 Gelbooru 的那位」这种串台 —— 而屏上看着是同一个人。
 *    [com.venera.compose.gallery.data.GalleryPost.uid] 用站点前缀做身份是同一个理由。
 *    ⚠️ 这一条只管**存储与身份**（关注/取关/去重都按站各算一条）。批次 M · M5 之后，
 *    首页那一栏在**摆**的时候会把跨站同名并成一条、点进去搜全部来源 ——
 *    那是显示层的口径，见 [GalleryFollowedArtists]；两处不是矛盾，是两层各自的选择。
 * 2. **重复关注不重掷时间戳**。用户在名单里点"再关注一次"（或连点两下）不该把人顶到最前 ——
 *    那会让排序跟着手指飘。所以取"较早那次"，幂等。
 *
 * 排序定死为"最近关注的在前，同一时刻按名字"。不依赖 `LinkedHashSet` 或 map 的迭代顺序 ——
 * 那种顺序读起来像 bug，又没有任何一条错误会指向它。
 */
object GalleryArtistFollows {

    fun isFollowing(entries: List<GalleryArtistFollow>, site: GallerySite, name: String): Boolean {
        val uid = GalleryArtistFollow.uidOf(site, name)
        return entries.any { it.uid == uid }
    }

    /** 按 uid 去重，同一条留**最早**那次关注（判据 2）。 */
    fun dedupe(entries: List<GalleryArtistFollow>): List<GalleryArtistFollow> {
        val byUid = LinkedHashMap<String, GalleryArtistFollow>()
        for (entry in entries) {
            val prev = byUid[entry.uid]
            if (prev == null || entry.addedAt < prev.addedAt) byUid[entry.uid] = entry
        }
        return byUid.values.toList()
    }

    /** 加一位：已存在就原样交回（幂等，不重掷时间戳），否则追加。 */
    fun withAdded(entries: List<GalleryArtistFollow>, entry: GalleryArtistFollow): List<GalleryArtistFollow> =
        if (entries.any { it.uid == entry.uid }) entries else entries + entry

    /** 取关：只删这一格，别的站上的同名条目原样留着（判据 1）。 */
    fun removed(entries: List<GalleryArtistFollow>, site: GallerySite, name: String): List<GalleryArtistFollow> {
        val uid = GalleryArtistFollow.uidOf(site, name)
        return entries.filterNot { it.uid == uid }
    }

    /** 上屏顺序：最近关注的在前；同一时刻按名字定序，不许跟着集合实现飘。 */
    fun sorted(entries: List<GalleryArtistFollow>): List<GalleryArtistFollow> =
        entries.sortedWith(compareByDescending<GalleryArtistFollow> { it.addedAt }.thenBy { it.name.lowercase() })
}
