package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryLegGuard
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GallerySearchMerge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 桌面墙的**第一页真取数** —— 把 `GalleryPorts` 拉回来的东西变成能上屏的卡片。
 *
 * ## 为什么这颗在 `gallery/ui/` 而不在集成方
 *
 * 这里是**判据**（哪几站发车、失败怎么记、怎么去重），不是 UI 也不是平台接线。
 * 放集成方会让它落进 `VeneraDesktop.kt`（那个文件 600+ 行，混着窗口与进程），
 * 而放这里能被单测直接调 —— 与 `fetchWallPage` / `desktopWallCardsOf` 同一层。
 *
 * ## 与 `fetchWallPage` 的关系
 *
 * `fetchWallPage`（`DesktopLatestPane.kt`）是**续页**那一半：吃一个已有的
 * `DesktopGalleryBatch` 算下一笔。这一颗是第一页 —— 造查询表、发第一轮、把结果转成卡片。
 * 混排与去重都转交共享面 `GallerySearchMerge.interleave`，桌面不重述第二份判据。
 *
 * ## 失败怎么处理
 *
 * 逐站失败**不抛也不吞**：`GalleryLegGuard` 已经把它翻成 `answered = false` + `reason`，
 * 这里照实念进日志、且**只把真答上的腿**交给混排。一站点被墙了不该让整面墙空着，
 * 但"没答上"与"答上了没图"必须能分开（前者不进混排，就还它们各自的页数账）。
 */
internal suspend fun desktopWallFirstPage(
    ports: GalleryPorts,
    gelbooruConfigured: Boolean,
    quality: GalleryPreviewQuality = GalleryPreviewQuality.PREVIEW,
    /**
     * 内容守卫。**给了才会判遮罩**（安全评审 B：此前生产路径从不判，
     * `DesktopGalleryWallPolicy.of` 成了死代码，于是墙上**一张图都不遮**）。
     *
     * 缺省 `null` 时刻意**打印一行读数**而不是静默放行 —— 让"这一屏没走守卫"
     * 在日志里看得见，而不是让读的人以为它判过了。
     */
    guard: GalleryContentGuard? = null,
    /** 分级档原值（`OFF`/`BLUR`/`HIDE`）。只有 [guard] 非空时才有意义。 */
    maskMode: String = "OFF",
    /** `prefs.galleryBlockAi`。同样只在 [guard] 非空时才有意义。 */
    blockAi: Boolean = false,
): List<DesktopWallCardModel> = withContext(Dispatchers.IO) {
    // 最新那一档的查询词：三站都用空串 —— 空串在共享面就是"该站的热门/最新"，
    // 与安卓首页那一格同一条路。这不是漏填。
    val sites = desktopWallLegs(gelbooruConfigured)
    val silent = desktopWallSilentSites(gelbooruConfigured)
    if (silent.isNotEmpty()) {
        println("D_取数 不发车 " + silent.entries.joinToString(" ") { (s, why) -> "${s.name}=$why" })
    }
    runFirstPage(ports, sites.associateWith { "" }, quality, guard, maskMode, blockAi)
}

/**
 * 同上，但由调用方点名各站的查询词（搜索那一屏用）。
 *
 * `queries` 里没点到的站**不发车** —— 这是刻意的："这次只问这两站"与"三站都问但第三站
 * 用空串"是两件事，后者会往墙上混进一屏不相关的东西。
 */
internal suspend fun desktopWallFirstPage(
    ports: GalleryPorts,
    queries: Map<GallerySite, String>,
    quality: GalleryPreviewQuality = GalleryPreviewQuality.PREVIEW,
    guard: GalleryContentGuard? = null,
    maskMode: String = "OFF",
    blockAi: Boolean = false,
): List<DesktopWallCardModel> = withContext(Dispatchers.IO) {
    runFirstPage(ports, queries, quality, guard, maskMode, blockAi)
}

private suspend fun runFirstPage(
    ports: GalleryPorts,
    queries: Map<GallerySite, String>,
    quality: GalleryPreviewQuality,
    guard: GalleryContentGuard?,
    maskMode: String,
    blockAi: Boolean,
): List<DesktopWallCardModel> {
    if (queries.isEmpty()) return emptyList()

    // 每站一手并发，守卫在腿里（它负责"超预算就记原因"，不把异常冒到墙上）。
    val outcomes = coroutineScope {
        queries.map { (site, query) ->
            val board = boardOf(ports, site)
            site to async {
                GalleryLegGuard.guard(site) {
                    board.searchPosts(query, page = 1, limit = board.pageSize)
                }
            }
        }.map { (site, deferred) -> site to deferred.await() }
    }.toMap()

    // 只把**真答上的**腿交给混排：给没答上那一站记一页就是把"永久漏掉那一页"写进账本
    // （`fetchWallPage` 的同一条理由，那里写在 `answered` 那一行上）。
    val answered = outcomes.filterValues { it.answered }.mapValues { it.value.posts }
    val reasons = outcomes.mapNotNull { (site, o) -> o.reason?.let { site to it } }.toMap()

    println(
        "D_取数 首屏 腿=${queries.size} 答上=${answered.size} " +
            answered.entries.joinToString(" ") { (s, p) -> "${s.name}=${p.size}" } +
            if (reasons.isEmpty()) "" else " 缺席=" + reasons.entries.joinToString(" ") { (s, r) -> "${s.name}($r)" },
    )

    if (answered.isEmpty()) return emptyList()

    // 混排去重走共享面那一颗（与续页同一条路）。
    // 它返回 `Result.failure` 时**不静默退成按站拼接**：那颗的失败多半是"给了货却滤成 0 条"，
    // 而按站拼接会把这个判据失效藏起来。这里照实念原因并交空墙 ——
    // 空墙 + 一行日志比一屏来路不明的图更容易查。
    val merged = GallerySearchMerge.interleave(answered, emptySet()).getOrElse { error ->
        println("D_取数 混排失败 ${error::class.java.simpleName}: ${error.message?.take(200)}")
        return emptyList()
    }

    // ── 内容守卫（2026-10-21 修安全评审 B）──
    //
    // 此前这里是 `desktopWallCardsOf(merged.posts, quality)`，而 `desktopWallCardOf`
    // 的 `masked` 形参**默认 false** ⇒ 墙上**一张图都不遮**，`DesktopGalleryWallPolicy.of`
    // 成了零调用点的死代码。这条不会报错、不会变慢、屏上也看不出来。
    //
    // 现在：有守卫就走真判据（黑名单 > `GalleryGuard.maskStateFor` > 三档），
    // `HIDDEN` 那批由 policy 直接**不 add**（= 不取图，见 `DesktopWallCard` 类注释）。
    if (guard == null) {
        // 缺省不静默：日志里要能看见"这一屏没走守卫"。
        println("D_守卫 未判 理由=调用点没给 guard 张数=${merged.posts.size}")
        return desktopWallCardsOf(merged.posts, quality)
    }
    val wall = DesktopGalleryWallPolicy.of(
        posts = merged.posts,
        maskMode = maskMode,
        blockAi = blockAi,
        guard = guard,
    )
    println(
        "D_守卫 判了 档=$maskMode 入=${merged.posts.size} 出=${wall.cards.size} " +
            "遮=${wall.cards.count { it.masked }} 按分级藏=${wall.hiddenByRating} " +
            // ⚠️ 这里原写的是 `cards.size - 遮 - 藏`，算出来是胡说：实测 175 张
            // 一张没遮、一张没藏，却报「按规则挡=175」—— 那个式子把
            // 「既没遮也没藏」全归成了「按规则挡」。**假读数比没有读数更坏**，
            // 它会让查问题的人以为有 175 张被规则挡掉了。
            // 真值在 `wall.blockedRules`（命中黑名单的**规则**集合，是规则不是张数）。
            "命中规则数=${wall.blockedRules.size}" +
            (if (wall.blockedRules.isEmpty()) "" else " 规则=${wall.blockedRules}"),
    )
    // 把 `DesktopWallCard.masked` 搬进 `DesktopWallCardModel.masked` —— 只搬，不重判。
    return wall.cards.map { desktopWallCardOf(it.post, quality, masked = it.masked) }
}

/**
 * 桌面侧的 `gelbooruConfigured` 判据。
 *
 * gelbooru 要账号（`GallerySearchSource.NEEDS_ACCOUNT_REASON`），没配就**不发车**，
 * 而不是发了等它 401 —— 后者会在墙上留下一条"没答上"的噪声。
 *
 * 读的是 `gelbooruIdentity` 那条流（契约面唯一与它有关的事实来源）；
 * 流还没出值就当"没配"，那是这一侧的真实状态，不是缺省猜测。
 */
internal fun desktopGelbooruConfigured(ports: GalleryPorts): Boolean =
    runCatching { ports.credentials.gelbooruIdentity.value != null }.getOrDefault(false)
