package com.venera.compose.gallery.data

import com.venera.compose.gallery.domain.PixivImageQuality

/**
 * pixiv **作品页**那一屏的一张作品全貌（2026-10-18 落地，站源独用：图库三站仍走
 * [com.venera.compose.gallery.ui.GalleryPostScreen]，两颗互不吃对方的模型）。
 *
 * 为什么不塞进 [GalleryPost]：那颗是**三站共用**的行模型，多开一格 pixiv 才有的字段，
 * 另外两站就永远带着一个恒 null 的格子 —— 而"格子里有名字"在屏上的表现就是"这一站没有"，
 * 与"站方没给"分不开（同一族错记在 `PixivWork` 的类注释里）。
 *
 * 为什么也不复用 [PixivWork]：那一颗是**列表里的一行**，只有卡上那几格；作品页要的多页原图、
 * 浏览数、发布时间、评论数在列表腿上根本不存在。给列表模型开这些格子，屏上长出来的就是
 * "永远空着的那一格"。
 *
 * 出处：每一格都来自 2026-10-18 现量的匿名腿 `/ajax/illust/{id}`（多页时再打一跳
 * `/ajax/illust/{id}/pages`）。**可空 = 站方这一条没给** ⇒ 屏上那一格随之不摆，
 * 不拿 0、不拿占位文本顶（用户 2026-10-18「虚假数据去掉」）。
 */
data class PixivWorkDetail(
    /**
     * 共用那颗行模型：收藏状态、下载、分享、屏蔽、AI 角标、分级守卫**全部继续吃它**，
     * 作品页不另开第二套账。
     */
    val post: GalleryPost,
    /**
     * 站方原题（`title`）。booru 那三站的标题是从标签**算**出来的，pixiv 这一格是画师自己起的，
     * 所以它只能待在这颗里，不能并回 [GalleryPost]。
     */
    val title: String,
    /** 作者用户号（`userId`，现量是字符串）。null = 这一条没给 ⇒ 关注与"看这位的作品"两个把手都不摆。 */
    val authorId: Long?,
    /** 站方 `userAccount`：屏上不摆，只在"去看这位画师的作品"那条出口里当地址段用。 */
    val authorAccount: String,
    /**
     * 每一页的两档地址 + 站方报的宽高，**顺序就是阅读顺序**。
     *
     * 不变式：`pages.size == pageCount`（由取数那一层保证，见 [PixivClient.workDetail]）——
     * 不等就抛，绝不交"站方说 4 页而手里只有 1 页"的半份：屏上写着共 4 页却只画得出第 1 页就是假话。
     *
     * 2026-10-18 第二轮改口径：从前这一格只有原图一档，于是**作品页一开屏就在拉原图**
     * （用户报"加载图片应该用低清点的图片"），而且没有宽高比 ⇒ 图回来之前占不了位（"没骨架"）。
     * 两档一起带才两件事都成立：中档给这一屏连读，原图档给大图播放器那一层的 HD 档。
     */
    val pages: List<PixivPage>,
    /** 站方说这一作有几页（`pageCount`，现量是数字）。 */
    val pageCount: Int,
    /**
     * 作者打的标签，**逐枚一条**，顺序照站方给的来（重排会把"作者自己排在最前那枚"改掉）。
     *
     * 为什么不是 [GalleryPost.tags] 那颗空格串：pixiv 的标签里**本来就有带空格的**
     * （现量一例 `キャラ配布(コイカツ)` 同族），并成串再切回来就会把一枚标签劈成两枚。
     */
    val tags: List<String>,
    /**
     * 站方给的**标签译名**（`标签原名 → 译文`），2026-10-20 补。
     *
     * 两条腿的字段名不同，取值口径也不同：
     * - 匿名腿 `/ajax/illust/{id}`：每枚标签是 `{"tag": "...", "translation": {"en": "..."}}` ——
     *   那个对象的**键跟着请求的 `Accept-Language` 走**（zh-cn 就回 `zh-cn`），所以读的时候
     *   不按固定键捞，见 [PixivFeedParse.tagTranslationOf]；
     * - 登录腿 `/v1/illust/detail`：每枚是 `{"name": "...", "translated_name": "..."}`，
     *   同样只在带了 `Accept-Language` 时才非空（pixivpy 的 `set_accept_language` 就是为这一格）。
     *
     * **只改显示**：点标签去搜的、复制的、落屏蔽表的都必须是 [tags] 里那颗原串
     * （译文会随站方词表变，检索引擎只认原名）。空表 = 站方这次没给译文 ⇒ 屏上只摆原名，
     * 不拿离线词典顶（那份是三站共用词表，对 pixiv 的日文标签覆盖很低，见 `GalleryTagChip` 的注释）。
     */
    val tagTranslations: Map<String, String> = emptyMap(),
    /** 浏览量（`viewCount`，现量 851）。 */
    val viewCount: Int?,
    /** 站方原样 ISO 串带时区（`uploadDate`，现量 `2026-10-01T06:12:00+00:00`）；换算与截日在 `domain/PixivWorkPage` 判。 */
    val uploadDate: String?,
    /** 评论条数（`commentCount`，现量 6）。**只有数**——评论列表匿名取不到（四形状现量 404）。 */
    val commentCount: Int?,
    /** 站方 `commentOff`：作者关了评论 ⇒ 那一节整块不摆，而不是摆一枚点了没反应的把手。 */
    val commentsOff: Boolean,
    /** 动图（ugoira，现量 `illustType == 2`）：作品页只做静图与多页组图，这一条由入口分流回旧看图器放。 */
    val ugoira: Boolean,
    /** 站方是否关注该作者（登录腿 /v1/illust/detail user.is_followed）。null = 匿名腿或未提供 */
    val isAuthorFollowed: Boolean? = null,
    val isBookmarked: Boolean? = null,
)

/**
 * 一张作品的**其中一页**：两档画质 + 站方报的宽高。
 *
 * 两档各住在哪一层（别在作品页拿原图、也别在播放器拿中档充 HD）：
 * - [mediumUrl] = 站方 `urls.regular`，作品页连读摆这一档；
 * - [originalUrl] = 站方 `urls.original`，只有大图播放器那一层的 HD 档才取它。
 *
 * 缺档**不许静默丢页**（丢一枚就是"站方说 4 页而手里只有 3 页"那一族假话）：
 * 站方只给其中一档时，另一档退回它 —— 两档同一串地址在屏上是"这一页没有更高清可看"，
 * 那是真话；而少画一页不是。两档都没有才算丢页，由 [PixivFeedParse.workPages] 的对账抛。
 *
 * [width]/[height] 可空：站方这一页没报宽高 ⇒ 屏上**不预留比例**（拿 1:1 顶就是替站方编一个形状）。
 */
data class PixivPage(
    val mediumUrl: String,
    val originalUrl: String,
    val width: Int?,
    val height: Int?,
    val largeUrl: String = mediumUrl,
) {
    fun urlFor(quality: PixivImageQuality): String = when (quality) {
        PixivImageQuality.MEDIUM -> mediumUrl.ifBlank { largeUrl }.ifBlank { originalUrl }
        PixivImageQuality.LARGE -> largeUrl.ifBlank { mediumUrl }.ifBlank { originalUrl }
        PixivImageQuality.ORIGINAL -> originalUrl.ifBlank { largeUrl }.ifBlank { mediumUrl }
    }
}
