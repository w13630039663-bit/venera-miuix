package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import java.util.Random

/**
 * 把两站的**热门池**合成一屏：滤掉不能摆的 → 去重 → 各抽 20 → 打乱。
 *
 * 2026-09-25 用户第四次改判（方案 §十三）：落地流不再是"两站最新交错"，而是
 * 两站各取一批热门、各抽 20 张然后**打乱**。所以这一版：
 * - **删掉了交错**（旧 `RUN_PATTERN` 那套"允许单源连出 1~3 张"的额度排法）——
 *   打乱之后连出几张是随机的自然结果，不再控制；
 * - **删掉了翻页**（旧 `exclude`）—— 热门池是一屏到底的固定池子
 *   （实测 yande.re 40 条 / Gelbooru 取 100 条里抽 20），
 *   留着翻页机制就必然出现"到底了还显示加载更多"，与搜索页 §7 同一类缺陷。
 *
 * ⚠️ 两站的"热门"口径不同（yande.re 是官方日榜，Gelbooru 是 `sort:score` 全站高分池），
 * 这一层**不关心**那个差别 —— 它只负责合并、抽样、打乱。差别在 `GalleryFeedSource` 那边说明。
 *
 * 只剩两条判据是有实测依据、不能删的：
 * - **扩展名白名单**：站方的列表里混着不能摆的东西（实测混过 `zip` 这类压缩包，
 *   而站方的 `type:jpg` 服务端过滤**返回 0 条** → 只能在客户端滤）。
 *   白名单**含 `mp4`/`webm`**（用户点名要引进视频）；
 * - **去重**：键仍是 uid / md5 / 规范化出处（跨站实测交集为 0，但零成本，留着防将来）。
 *
 * 随机**只来自注入的种子**：同一个种子必得同一个序列。这不是洁癖 —— 本项目导航条目被覆盖时
 * 组合会销毁，"点进大图再返回"会重跑这段逻辑，裸 `Random()` 会让整屏换序
 * （见记忆「导航条目会重建组合」）。种子由 `GalleryViewModel` 持有，只有刷新才换。
 */
object GalleryMerge {

    /** 每站取多少张进屏。用户给的数：「各取 20 张」。 */
    const val PER_SITE = 20

    /** 一屏满量 = 两站各 [PER_SITE]。 */
    const val SCREEN_SIZE = PER_SITE * 2

    /**
     * 能摆上屏的扩展名：静图 + 视频。
     *
     * 视频是用户点名要的。`zip` 这类继续留在门外（站方那是漫画压缩包，不是可摆的图）。
     */
    private val DISPLAYABLE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif") + GalleryPost.VIDEO_EXTS

    /**
     * 这条**能不能摆上屏** —— 站方给了行、但没给可摆的图，就不算内容。
     *
     * 为什么要有这道判据（这一条是通用健壮性，不是某一站的专属规则）：
     * 站方有时会给出一行**没有可用图片地址**的条目，照摆就是一串
     * 尺寸正确、内容全空的灰卡。历史上搜索结果那条路漏过这道判据，
     * 用户看到的现象是"搜得出条数、屏上一张都没有"—— 最难查的一类。
     * 热门池那条路一直没事，就是因为它过这里。
     *
     * ⚠️ 判据只看"扩展名可摆 **且** 缩略图非空"这两件事本地的、可验证的性质，
     * **不去猜站方为什么没给图** —— 那个原因随站方规则变化，而这条判据不必跟着变。
     */
    fun isDisplayable(post: GalleryPost): Boolean =
        post.fileExt.lowercase() in DISPLAYABLE_EXTS && post.previewUrl.isNotBlank()

    data class Merged(
        val posts: List<GalleryPost>,
        /** 各站实际进屏的条数。少了就要在页面上说出来，不能装成正常。 */
        val perSite: Map<GallerySite, Int>,
        val droppedUnusable: Int,
        val droppedDuplicates: Int,
    ) {
        val videos: Int get() = posts.count { it.isVideo }
    }

    fun mix(
        pools: Map<GallerySite, List<GalleryPost>>,
        seed: Long,
    ): Result<Merged> {
        var droppedUnusable = 0
        val usable = pools.mapValues { (site, list) ->
            list.filter { post ->
                val ok = post.site == site && isDisplayable(post)
                if (!ok) droppedUnusable++
                ok
            }
        }
        // 站方明明给了内容，滤完却一条不剩 = 判据错了（比如白名单漏了一档扩展名），必须炸出来。
        for ((site, list) in pools) {
            if (list.isNotEmpty() && usable.getValue(site).isEmpty()) {
                return Result.failure(
                    IllegalStateException("${site.displayName} 给了 ${list.size} 条，滤完 0 条可用（扩展名白名单判据要复查）")
                )
            }
        }

        val seen = HashSet<String>()
        var droppedDuplicates = 0
        val posts = ArrayList<GalleryPost>(SCREEN_SIZE)
        // GallerySite.entries 的顺序即"先去重的优先序"，固定下来才不会因为 HashMap 无序而飘。
        for (site in GallerySite.entries) {
            val kept = ArrayList<GalleryPost>(PER_SITE)
            // 每站一个**独立且可复现**的抽样流：种子 + 站点序号。不然两站会抽到同一段随机数，
            // 观感上就是"两站的排序长得像"。
            val random = Random(seed + site.ordinal)
            for (post in usable.getValue(site).shuffled(random)) {
                val keys = post.dedupKeys()
                if (keys.any { it in seen }) {
                    droppedDuplicates++
                    continue
                }
                seen += keys
                kept += post
                if (kept.size == PER_SITE) break
            }
            posts += kept
        }
        if (posts.isEmpty()) {
            return Result.failure(IllegalStateException("两站都没有可用内容"))
        }
        posts.shuffle(Random(seed))

        return Result.success(
            Merged(
                posts = posts,
                perSite = posts.groupingBy { it.site }.eachCount(),
                droppedUnusable = droppedUnusable,
                droppedDuplicates = droppedDuplicates,
            )
        )
    }

    /** 站内身份 → 文件指纹 → 上游出处，三个都是零成本的键。 */
    private fun GalleryPost.dedupKeys(): List<String> =
        listOf(uid, if (md5.isNotBlank()) "md5:$md5" else "", if (sourceKey.isNotBlank()) "src:$sourceKey" else "")
            .filter { it.isNotEmpty() }
}
