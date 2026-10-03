package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GallerySite

/**
 * 这一栏**空着的原因**。只两档，而且这两句话在屏上必须不同：
 * 「一个都还没关注」是用户还没做，「关注了但收藏里没他的图」根本不是空栏（那位照样摆，卡面换首字母座）。
 */
enum class GalleryFollowedArtistsGap { NONE, NO_FOLLOWS }

/**
 * 首页那一栏里的一位画师。
 *
 * @param sites 这位在**哪几站被关注过**。跨站同名并成一条之后这里可能有两枚（批次 M · M5），
 *   次序恒为枚举自然序（yande.re 在前），不跟着关注时刻飘 —— 同一个人两次进栏摆出的徽标次序
 *   不一样，读起来像"换了个人"。
 * @param previewUrl 这几站该名下**最近一张有缩略图的收藏**；空串 = 收藏里没有他名下的图。
 *   圆座次序是「现取到的真头像 > 这一张 > 首字母座」，见 [faceOf]（用户 2026-10-01 改的次序：
 *   这一栏的语义是"人"，只关注没收藏的人不该因此只剩一个字母）。
 * @param count 这几站里有几张收藏带着这个名字。0 就别说那一行字。
 */
data class FollowedArtistRef(
    val sites: List<GallerySite>,
    val name: String,
    val previewUrl: String,
    val count: Int,
)

data class GalleryFollowedArtistsReadout(
    val artists: List<FollowedArtistRef>,
    val gap: GalleryFollowedArtistsGap,
    val distinctTotal: Int,
) {
    /** 行里摆不下的那几位。念得出来才不装成"就这么些人"。 */
    val overflow: Int get() = (distinctTotal - artists.size).coerceAtLeast(0)
}

/**
 * 首页「正在关注的画师」那一栏的判据（批次 L · L11）。
 *
 * 这一栏**从前叫过这个名字**，后来因为仓库里没有任何关注数据源而被改成「收藏里的画师」；
 * 批次 L 有了关注名单存储，那名重新配得上，用户 2026-09-30 拍板换回来。三条定死的口径：
 *
 * - **只有名单里的人进栏**。收藏不再自己往上挤 —— 这一栏的语义是"你挑过的人"，
 *   混进"我们替你数出来的"就没人能分辨自己看到的是哪一种。
 * - **张数与备用卡面仍从收藏里取**：名单只存「站别 + 名字 + 关注时刻」（会过期的元数据一律不落库，
 *   见 [GalleryArtistFollow]），所以这两位只能从已有的收藏里找。
 *   圆座的**第一位**是现取的真头像（[faceOf]，用户 2026-10-01 改的）—— 那是"需要时现取一次"，
 *   与批次 L 那条"不落库"并不冲突：落库承诺的是"离线也看得到"，而这条兑现不了。
 * - **跨站同名并成一条**（批次 M · M5，用户 2026-09-30 拍板"y 站和 g 站合并显示不要分站显示"）。
 *   这一条**推翻**了批次 L 在 [GalleryArtistFollows] 里定的判据 1（"跨站同名是两条"）：
 *   那条担心的是串台 —— 从 yande.re 那条点过去、实际搜的是 Gelbooru 的那位。合并之后串台的
 *   出口被换掉了：点进去搜的是**全部来源**，两站的图一起出，卡上那两枚徽标说的正是这件事。
 *   名单那侧（存储、去重、取关）仍是"一站一条"，改的只是这一栏怎么摆。
 *
 * 排序直接复用 [GalleryArtistFollows.sorted]（最近关注的在前，同一时刻按名字）：合并后那条的
 * 位置取组里**最近那次关注**，所以"我刚关注的人在最前"这句话在合并之后仍然成立。
 * 不在这里再定一次"什么叫新" —— 两处排序不一致的话，用户只会看到"我关注的人换了地方"。
 */
object GalleryFollowedArtists {

    /** 横卡一行摆几位。与推荐横卡同一档（写死，超出的用 [GalleryFollowedArtistsReadout.overflow] 说）。 */
    const val ROW_SIZE = 8

    fun rowOf(
        follows: List<GalleryArtistFollow>,
        favorites: List<GalleryFavorite>,
        rowSize: Int = ROW_SIZE,
    ): GalleryFollowedArtistsReadout {
        val entries = GalleryArtistFollows.sorted(GalleryArtistFollows.dedupe(follows))
        if (entries.isEmpty()) {
            return GalleryFollowedArtistsReadout(emptyList(), GalleryFollowedArtistsGap.NO_FOLLOWS, 0)
        }
        // 先把收藏按「站:名字」归一次：否则每位画师都要重扫整份收藏（名单长了就是 N×M）。
        val byOwner = HashMap<String, MutableList<GalleryFavorite>>()
        for (favorite in favorites) {
            val site = favorite.site ?: continue
            // 认不出站键的收藏丢掉而不是硬归某站：硬归会出现"卡上是 A 站、点进去搜的是 B 站"。
            for (tag in GalleryRecommendations.tagsOf(favorite).distinct()) {
                byOwner.getOrPut(GalleryArtistFollow.uidOf(site, tag)) { mutableListOf() } += favorite
            }
        }
        // 跨站同名并成一条：分组键就是名字（忽略大小写），与 uid 那侧同一套比对口径。
        // entries 已经是"最近关注在前"，所以每组的第一条既是这条的排位，也是显示名的来源。
        val grouped = LinkedHashMap<String, MutableList<GalleryArtistFollow>>()
        for (entry in entries) {
            grouped.getOrPut(entry.name.trim().lowercase()) { mutableListOf() } += entry
        }
        val refs = grouped.values.map { group ->
            // 张数与卡面从收藏里取，且只取**这位被关注过的那几站**：
            // 没关注 g 站那位却把 g 站的同名图算进来，卡上就会摆一枚"你没关注过"的徽标。
            val owned = group.flatMap { byOwner[it.uid].orEmpty() }
            FollowedArtistRef(
                sites = GallerySite.entries.filter { site -> group.any { it.site == site } },
                name = group.first().name,
                // 最近那一张**有缩略图的**收藏：老收藏里偶有空 preview_url，跟着"最近"就交出一张白圆。
                previewUrl = owned.filter { it.previewUrl.isNotBlank() }.maxByOrNull { it.savedAt }?.previewUrl.orEmpty(),
                count = owned.size,
            )
        }
        return GalleryFollowedArtistsReadout(refs.take(rowSize), GalleryFollowedArtistsGap.NONE, refs.size)
    }

    /**
     * 圆座这一档摆哪一张：现取到的**真头像**优先，其次名下最近一张收藏，两者都没有才交回 null
     * 由 UI 摆首字母座。空串与空白都不算值 —— 老收藏里偶有空 `preview_url`，认了它就等于交出一张白圆。
     *
     * 次序为什么这样排（用户 2026-10-01 报"关注了却只有英文字母"）：这一栏从前只认"名下最近一张收藏"，
     * 于是**只关注没收藏**的人必然落首字母座。可这一栏的语义是"人"，而详情页给的是脸 ——
     * 拿两处一比就读成缺图。作品缩略图留在第二位，是为了头像那一路没走通时仍有东西可摆，
     * 不至于把已经有收藏的人降级成一个字母。
     */
    fun faceOf(avatar: String?, previewUrl: String): String? =
        avatar?.takeUnless { it.isBlank() } ?: previewUrl.takeUnless { it.isBlank() }

    /**
     * 组合重建时这一栏的**初值**：地址档里已经答上过真地址的那几位，直接进脸表，不等异步。
     *
     * 为什么要单列一颗纯函数（2026-10-03 真机读数）：切走再回来会把这一栏的组合销毁，`faces`
     * 回到空表 ⇒ 第一帧只能摆名下收藏图，等异步那一路跑完才换脸；而探针量到**档里本来就有地址**
     * （`首页档=有地址` 后面仍跟着 `外链耗时=349~1032ms`），那段换脸的时长全花在已经知道的答案上。
     *
     * [peek] 的三档语义照 [GalleryArtistAvatars]：
     * 空串 = 查过、站方确实没给脸 —— 它**不算答案也不算缺**，不进表（没脸可摆），
     * 但外面也不该为它重发请求（那条在 `artistAvatarOnSite` 侧，UI 层，本函数管不着）。
     */
    fun seedFaces(
        artists: List<FollowedArtistRef>,
        peek: (GallerySite, String) -> String?,
    ): Map<String, String> = artists.mapNotNull { artist ->
        artist.sites.firstNotNullOfOrNull { site ->
            peek(site, artist.name)?.takeUnless { it.isBlank() }
        }?.let { artist.name to it }
    }.toMap()
}
