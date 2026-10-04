package com.venera.desktop.gallery.ui

import androidx.compose.runtime.Composable
import com.venera.compose.gallery.data.GalleryPorts

/**
 * 收藏域与搜索域的页条 —— 2026-10-04 第二批把这两域从坦白页转成真页面时落的。
 *
 * ## 为什么这两域各只有**一行**
 *
 * 缺席域那一行是"占位"，真页面的行数由**这一域真有几页**决定：
 * - 收藏域只有一份数据（`GalleryFavorites.favorites`），按站分组、筛选、排序都是同一份名单的视图 ——
 *   摆成三行就是给同一个 StateFlow 装三个入口，而其中两个必然是假开关。
 * - 搜索域同理：一次搜索的结果就是一面墙，**历史**那一页本批**不做**（存储在 `gallery/ui`，
 *   不在桌面编译面内，做它得先给桌面一份落盘件 —— 那是独立一批）。
 *
 * 判据 `DesktopDomainNavigationTest` ⑤ 只要求"每域至少一行且 key 不重复"，它**不**要求行数对齐。
 * 那一行是"这个域有没有东西"的立足点，不是"这个域应该有几页"的规格。
 *
 * ## 缺席转真之后，那张 `wired` 清单去了哪
 *
 * 原先五枚缺席域各带一张"已经接好、只是没界面去看"的清单（`DesktopAbsence.wired`），
 * 坦白页逐颗念出来。转成真页面之后**清单随缺席一起消失** —— 它不是被删了，是它要回答的问题
 * （"差的是哪一层"）已经没有答案了：现在差的是"还没做的功能"，那属于页内的缺席读数与文档，
 * 不属于域级缺席。硬留着它会变成"这一域有内容，同时又说缺三层界面"的自相矛盾。
 */
internal object DesktopFavoritesSectionKey {
    const val WALL = "gallery-favorites-wall"
}

internal object DesktopSearchSectionKey {
    const val RESULTS = "gallery-search-results"
}

/** 收藏域的那一行。 */
internal fun desktopFavoritesRows(): List<DesktopGalleryHomeRow> = listOf(
    DesktopGalleryHomeRow(
        key = DesktopFavoritesSectionKey.WALL,
        title = "图片收藏",
        glyph = "图",
        content = DesktopGallerySectionContent.GalleryFavorites,
        reason = null,
    ),
)

/** 搜索域的那一行。 */
internal fun desktopSearchRows(): List<DesktopGalleryHomeRow> = listOf(
    DesktopGalleryHomeRow(
        key = DesktopSearchSectionKey.RESULTS,
        title = "标签搜索",
        glyph = "索",
        content = DesktopGallerySectionContent.GallerySearch,
        reason = null,
    ),
)

/**
 * 两个新域的 body。
 *
 * ⚠️ `when` 不留 else：内容件是 sealed 的，加一档忘了在这里出形状 ⇒ 编译不过。
 * 下面那两枚 `error` 与 `DesktopGalleryHomeBody` / `DesktopComicBody` 里的同一条纪律：
 * **域路不许串**。别的域的行流到这一颗 body 那一刻就崩，而不是静默画出一屏少了一半的东西。
 */
@Composable
internal fun DesktopGalleryFavoritesBody(row: DesktopGalleryHomeRow, ports: GalleryPorts) {
    val rendered: Unit = when (row.content) {
        DesktopGallerySectionContent.GalleryFavorites -> DesktopGalleryFavoritesPane(ports)
        DesktopGallerySectionContent.Daily,
        DesktopGallerySectionContent.FollowedArtists,
        DesktopGallerySectionContent.ComicExplore,
        DesktopGallerySectionContent.ComicFavorites,
        DesktopGallerySectionContent.GallerySearch,
        -> error("「${row.title}」不是收藏域的页 —— 域路的分岔在 VeneraDesktopApp.DesktopDomainBody")
        null -> error("收藏域今天没有未实现的行，「${row.title}」却带了空的 content 位")
    }
}

@Composable
internal fun DesktopGallerySearchBody(row: DesktopGalleryHomeRow, ports: GalleryPorts) {
    val rendered: Unit = when (row.content) {
        DesktopGallerySectionContent.GallerySearch -> DesktopGallerySearchPane(ports)
        DesktopGallerySectionContent.Daily,
        DesktopGallerySectionContent.FollowedArtists,
        DesktopGallerySectionContent.ComicExplore,
        DesktopGallerySectionContent.ComicFavorites,
        DesktopGallerySectionContent.GalleryFavorites,
        -> error("「${row.title}」不是搜索域的页 —— 域路的分岔在 VeneraDesktopApp.DesktopDomainBody")
        null -> error("搜索域今天没有未实现的行，「${row.title}」却带了空的 content 位")
    }
}
