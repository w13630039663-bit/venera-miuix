package com.venera.desktop.gallery.ui

import androidx.compose.runtime.Composable
import com.venera.compose.gallery.data.GalleryPorts

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

