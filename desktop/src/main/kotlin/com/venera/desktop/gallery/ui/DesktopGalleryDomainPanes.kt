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
 * - 搜索域同理：一次搜索的结果就是一面墙，**历史**那一页本批**不做** —— 差的也是界面那一段接线，
 *   判据与落盘件都在桌面编译面上（同一句账写在 `DesktopGallerySearchPane` 那颗文件的类注释里）。
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

internal object DesktopSettingsSectionKey {
    const val GALLERY_DISPLAY = "gallery-display-settings"
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
 * 设置域的那一行（第七批：这一域从坦白页转正）。
 *
 * 只有一行 —— 这一屏就是"墙上那四枚档"，不是一堆设置分类。
 * ⚠️ 别为了"看起来像个设置页"去加行：加出来的每一行都得有真写侧，
 * 而那四枚之外今天没有第五枚能改（账在 `DesktopGallerySettingsSpec`，③ 那条加法钉着）。
 */
internal fun desktopSettingsRows(): List<DesktopGalleryHomeRow> = listOf(
    DesktopGalleryHomeRow(
        key = DesktopSettingsSectionKey.GALLERY_DISPLAY,
        title = "画廊显示档",
        glyph = "档",
        content = DesktopGallerySectionContent.GalleryDisplay,
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
internal fun DesktopGalleryFavoritesBody(
    row: DesktopGalleryHomeRow,
    ports: GalleryPorts,
    /** 那份收藏档读不出来时 store 留的那一句，由应用根消费一次后递下来（必填、不给默认值）。 */
    favoritesNotice: String?,
) {
    val rendered: Unit = when (row.content) {
        DesktopGallerySectionContent.GalleryFavorites -> DesktopGalleryFavoritesPane(ports, favoritesNotice = favoritesNotice)
        DesktopGallerySectionContent.Daily,
        DesktopGallerySectionContent.FollowedArtists,
        DesktopGallerySectionContent.ComicExplore,
        DesktopGallerySectionContent.ComicFavorites,
        DesktopGallerySectionContent.ComicHistory,
        DesktopGallerySectionContent.ComicSources,
        DesktopGallerySectionContent.ComicDownloads,
        DesktopGallerySectionContent.ComicQueue,
        DesktopGallerySectionContent.GallerySearch,
        DesktopGallerySectionContent.GalleryDisplay,
        DesktopGallerySectionContent.Downloads,
        DesktopGallerySectionContent.BrowseHistory,
        DesktopGallerySectionContent.ArtistsWall,
        DesktopGallerySectionContent.ForYouWall,
        DesktopGallerySectionContent.Latest,
        DesktopGallerySectionContent.Ranking,
        -> error("「${row.title}」不是收藏域的页 —— 域路的分岔在 VeneraDesktopApp.DesktopDomainBody")
        null -> error("收藏域今天没有未实现的行，「${row.title}」却带了空的 content 位")
    }
}

@Composable
internal fun DesktopGallerySearchBody(
    row: DesktopGalleryHomeRow,
    ports: GalleryPorts,
    searchSeed: DesktopSearchSeed,
    /** `--reverse-file=` 那一枚取证种子（必填、不给默认值，理由见 `DesktopGallerySearchPane` 的签名）。 */
    seedLocalReverse: String?,
) {
    val rendered: Unit = when (row.content) {
        DesktopGallerySectionContent.GallerySearch -> DesktopGallerySearchPane(ports, searchSeed, seedLocalReverse)
        DesktopGallerySectionContent.Daily,
        DesktopGallerySectionContent.FollowedArtists,
        DesktopGallerySectionContent.ComicExplore,
        DesktopGallerySectionContent.ComicFavorites,
        DesktopGallerySectionContent.ComicHistory,
        DesktopGallerySectionContent.ComicSources,
        DesktopGallerySectionContent.ComicDownloads,
        DesktopGallerySectionContent.ComicQueue,
        DesktopGallerySectionContent.GalleryFavorites,
        DesktopGallerySectionContent.GalleryDisplay,
        DesktopGallerySectionContent.Downloads,
        DesktopGallerySectionContent.BrowseHistory,
        DesktopGallerySectionContent.ArtistsWall,
        DesktopGallerySectionContent.ForYouWall,
        DesktopGallerySectionContent.Latest,
        DesktopGallerySectionContent.Ranking,
        -> error("「${row.title}」不是搜索域的页 —— 域路的分岔在 VeneraDesktopApp.DesktopDomainBody")
        null -> error("搜索域今天没有未实现的行，「${row.title}」却带了空的 content 位")
    }
}

/** 画师域的那一行（第八批：这一域从坦白页转正）。 */
internal object DesktopArtistsSectionKey {
    const val FOLLOWS = "gallery-artists-follows"
}

/**
 * 画师域的那一行。名单是这一域的第一层，"这位画师的图"是第二层 ——
 * 批 19 把那一层做出来了（[DesktopArtistProfilePane]），所以这一行可以摆一枚真有目的地的入口。
 */
internal fun desktopArtistsRows(): List<DesktopGalleryHomeRow> = listOf(
    DesktopGalleryHomeRow(
        key = DesktopArtistsSectionKey.FOLLOWS,
        title = "关注画师",
        glyph = "绘",
        content = DesktopGallerySectionContent.ArtistsWall,
        reason = null,
    ),
)

/** 发现域的那一行（第九批：最后一枚坦白页转正）。 */
internal object DesktopDiscoverSectionKey {
    const val WALL = "gallery-discover-for-you"
}

/** 发现域的那一行：一轮抽样、每站各取一页、混摆成一面墙。 */
internal fun desktopDiscoverRows(): List<DesktopGalleryHomeRow> = listOf(
    DesktopGalleryHomeRow(
        key = DesktopDiscoverSectionKey.WALL,
        title = "猜你喜欢",
        glyph = "发",
        content = DesktopGallerySectionContent.ForYouWall,
        reason = null,
    ),
)

@Composable
internal fun DesktopGalleryArtistsBody(
    row: DesktopGalleryHomeRow,
    ports: GalleryPorts,
    /** 名单档读不出来时 store 留的那一句，由应用根消费一次后递下来（必填、不给默认值）。 */
    followsNotice: String?,
    /** 那一行「看他的图」按下去要走到的目的地（必填：漏传的形状是一枚按不动的入口）。 */
    onOpenArtist: (DesktopArtistTarget) -> Unit,
) {
    val rendered: Unit = when (row.content) {
        DesktopGallerySectionContent.ArtistsWall ->
            DesktopArtistsPage(ports, followsNotice = followsNotice, onOpenArtist = onOpenArtist)
        DesktopGallerySectionContent.Daily,
        DesktopGallerySectionContent.FollowedArtists,
        DesktopGallerySectionContent.ComicExplore,
        DesktopGallerySectionContent.ComicFavorites,
        DesktopGallerySectionContent.ComicHistory,
        DesktopGallerySectionContent.ComicSources,
        DesktopGallerySectionContent.ComicDownloads,
        DesktopGallerySectionContent.ComicQueue,
        DesktopGallerySectionContent.GalleryFavorites,
        DesktopGallerySectionContent.GallerySearch,
        DesktopGallerySectionContent.GalleryDisplay,
        DesktopGallerySectionContent.Downloads,
        DesktopGallerySectionContent.BrowseHistory,
        DesktopGallerySectionContent.ForYouWall,
        DesktopGallerySectionContent.Latest,
        DesktopGallerySectionContent.Ranking,
        -> error("「${row.title}」不是画师域的页 —— 域路的分岔在 VeneraDesktopApp.DesktopDomainBody")
        null -> error("画师域今天没有未实现的行，「${row.title}」却带了空的 content 位")
    }
}

/**
 * 发现域的 body。
 *
 * 与另三条域路同一条纪律：`when` 不留 `else`，别的域的行流到这里那一刻就 `error`，
 * 而不是静默画出一屏少了一半的东西。
 */
@Composable
internal fun DesktopGalleryDiscoverBody(row: DesktopGalleryHomeRow, ports: GalleryPorts) {
    val rendered: Unit = when (row.content) {
        DesktopGallerySectionContent.ForYouWall -> DesktopForYouPane(ports)
        DesktopGallerySectionContent.Daily,
        DesktopGallerySectionContent.FollowedArtists,
        DesktopGallerySectionContent.ComicExplore,
        DesktopGallerySectionContent.ComicFavorites,
        DesktopGallerySectionContent.ComicHistory,
        DesktopGallerySectionContent.ComicSources,
        DesktopGallerySectionContent.ComicDownloads,
        DesktopGallerySectionContent.ComicQueue,
        DesktopGallerySectionContent.GalleryFavorites,
        DesktopGallerySectionContent.GallerySearch,
        DesktopGallerySectionContent.GalleryDisplay,
        DesktopGallerySectionContent.Downloads,
        DesktopGallerySectionContent.BrowseHistory,
        DesktopGallerySectionContent.ArtistsWall,
        DesktopGallerySectionContent.Latest,
        DesktopGallerySectionContent.Ranking,
        -> error("「${row.title}」不是发现域的页 —— 域路的分岔在 VeneraDesktopApp.DesktopDomainBody")
        null -> error("发现域今天没有未实现的行，「${row.title}」却带了空的 content 位")
    }
}
