package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import kotlin.math.max

/**
 * 一张墙上卡片的**渲染输入** —— 只有画得出来需要的字段，没有业务判断。
 *
 * ## 为什么要这一颗而不是直接把 `GalleryPost` 递进 UI
 *
 * `GalleryPost` 有 20+ 个字段，其中大半是取数语义（`rating` / `score` / `favCount` /
 * `largeWidth` / `pageUrl` …）。卡片只吃四样：图、标题/作者、纵横比、要不要打码。
 * 直接递 `GalleryPost` 会让 UI 与取数模型耦合 —— 那是两端共享面最不想要的那种耦合
 * （对岸 `GalleryCardCaption` 就因为同样的理由接的是算好的标题与作者，不是 `GalleryPost`）。
 *
 * ## 为什么它不是纯 UI 件
 *
 * 它同时承载**判据**：图片 URL 该取哪一档（`preview` / `fast` / `large`）、
 * 打码该不该打、纵横比怎么算。这三件事有单测钉着（`DesktopWallCardProjectionTest`），
 * 所以它住在 `gallery/ui/` 而**不含 `@Composable`** —— 能被单测直接取值。
 */
data class DesktopWallCardModel(
    /** 卡片标题：作者名优先，没有作者时用站点名 + 编号。 */
    val title: String,
    /** 上屏的图片 URL（已按画质档挑过）。 */
    val url: String,
    /** 卡片纵横比（宽/高），摆瀑布流用。 */
    val aspect: Float,
    /** 要不要打码。 */
    val masked: Boolean,
)

/**
 * `GalleryPost` → 卡片。
 *
 * @param quality 画质档：`PREVIEW` 取 `previewUrl`、`LARGE` 取 `largeUrl`；
 *   两档都缺时逐级回退，**不返回空串** —— 空串会让 `VeneraCover` 走"加载失败"那条路，
 *   而"这一站没给这一档"与"图挂了"是两件事，不该长得一样。
 * @param masked 内容守卫判过的结果。判据在 `DesktopGalleryContentGuard`，这里只搬。
 */
internal fun desktopWallCardOf(
    post: GalleryPost,
    quality: GalleryPreviewQuality = GalleryPreviewQuality.PREVIEW,
    masked: Boolean = false,
): DesktopWallCardModel {
    // 取图那一档：按 quality 优先，逐级回退。
    // 顺序与共享面 `tileImageRequest` 同一份口径（preview 先行，因为墙上是缩略图），
    // 但**不是**转交它：那颗要 `GalleryPreviewQuality` + `masked` 两枚且返回 `String?`，
    // 而这里要保证"给得出一张图"（墙上一格空着比一张低档图难看得多）。
    val url = when (quality) {
        GalleryPreviewQuality.LARGE -> post.largeUrl.ifBlank { post.fastUrl }
        GalleryPreviewQuality.PREVIEW -> post.previewUrl.ifBlank { post.fastUrl }
    }.ifBlank { post.largeUrl }

    // 纵横比：优先用**这张图自己的**尺寸（预览档优先，因为上屏的就是它）；
    // 没有尺寸时退到 3:4 —— 那是一张竖版同人图最常见的比例，
    // 退到 1:1 会让瀑布流整面变方，"退档"要退得像一点。
    val (w, h) = when {
        post.previewWidth > 0 && post.previewHeight > 0 -> post.previewWidth to post.previewHeight
        post.width > 0 && post.height > 0 -> post.width to post.height
        post.largeWidth > 0 && post.largeHeight > 0 -> post.largeWidth to post.largeHeight
        else -> 3 to 4
    }
    val aspect = (w.toFloat() / max(h, 1)).coerceIn(0.5f, 2.0f)

    val title = post.author.ifBlank { siteLabelOf(post.site) } + " #" + post.id

    return DesktopWallCardModel(
        title = title,
        url = url,
        aspect = aspect,
        masked = masked,
    )
}

/** 站点名（卡片没有作者时用它兜底）。 */
internal fun siteLabelOf(site: GallerySite): String = when (site) {
    GallerySite.YANDERE -> "yande.re"
    GallerySite.GELBOORU -> "gelbooru"
    GallerySite.SAFEBOORU -> "safebooru"
    GallerySite.PIXIV -> "pixiv"
}

/**
 * 一批 `GalleryPost` → 一面墙的卡片。
 *
 * 单独一颗而不是在调用点 `map`：**去重**这件事必须做（三站混排时同 id 不同站是常态，
 * 而"同一张图摆两遍"是墙最显眼的错）。去重键与共享面 `GallerySearchMerge` 同一口径：
 * `站点:id`，不是裸 id —— 裸 id 会把不同站上恰好同号的图误并成一张。
 */
internal fun desktopWallCardsOf(
    posts: List<GalleryPost>,
    quality: GalleryPreviewQuality = GalleryPreviewQuality.PREVIEW,
): List<DesktopWallCardModel> {
    val seen = HashSet<String>()
    return posts.mapNotNull { post ->
        if (!seen.add("${post.site}:${post.id}")) {
            null
        } else {
            runCatching { desktopWallCardOf(post, quality) }.getOrNull()
        }
    }
}
