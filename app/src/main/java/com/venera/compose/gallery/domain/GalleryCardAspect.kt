package com.venera.compose.gallery.domain

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite

/**
 * **卡片框比例**的唯一出处（2026-10-20，主人「限制 pixiv 站点图片卡片的显示比例，
 * 要整张卡全部显示出来而不是被裁掉一点」）。
 *
 * ## 它治的是哪一格：两把尺量同一张图
 *
 * 同一个作品、同一张封面，屏上两侧从前用的是**两把不同的尺**：
 *
 * | 那一侧 | 从前怎么算 | 夹不夹 |
 * |---|---|---|
 * | 墙上那张卡（`GalleryScreen`） | `post.cardRatio.coerceIn(0.4f, 2.5f)` | **夹** |
 * | 作品页那一页图块（`PixivWorkScreen`） | `PixivWorkPage.placeholderAspect(w, h)` | **不夹** |
 *
 * 而封面一律按 `ContentScale.Crop` 铺 —— 于是**框比例 ≠ 图比例就裁掉一条**：
 * - 竖长条（pixiv 跨页 `1080×4000` ⇒ 0.27）被夹成 0.4 ⇒ 卡上**裁掉左右两条**；
 * - 横宽图（跨页大图 3.0）被夹成 2.5 ⇒ 卡上**裁掉上下两条**。
 *
 * 裁本身只是"少看一点"，真正贵的是它把**飞行体**带歪了：离场起点是那一页图块（完整 0.27），
 * 落点是墙上那张卡（夹过 0.4），两端比例不同 ⇒ `flightFrame` 的几何必须**变形成另一个比例**，
 * 读起来就是"飞行动画乱变"。主人那句"这样能解决很多飞行动画乱变的问题"说的正是这条因果。
 *
 * ## 为什么不是把 `Crop` 改成 `Fit`
 *
 * `Fit` 能让整图可见，但框里会**留白条**；而飞行体那套算式（[GalleryFlightFrame]）是按
 * **cover 语义**写的 —— 它假定"图铺满框"。卡一旦留白，那套算式的假设就崩，
 * 等于把"乱变"换个样子演回来。
 *
 * 所以正解是反过来：**让框比例等于图比例**。那时 `Crop` 与 `Fit` 完全等价（既不裁也不留白），
 * 飞行算式也就精确了。
 *
 * ## 为什么区间是 0.2~4.0 而不是完全不夹
 *
 * 不夹会让极端长条把整列撑出屏幕（一张 10:1 的长卷会变成一条细带，墙的网格随之崩）。
 * pixiv 最常见的两档都落在 0.2~4.0 内 —— 竖向跨页 `0.25~0.5`、横向跨页/大图 `2.5~3.2` ——
 * 所以这一档**覆盖绝大多数 pixiv 图不裁**，只夹真正极端的那些。
 *
 * ## 为什么按站点分岔（三站保持原样）
 *
 * `GalleryScreen` 那面墙是**三站共用**的（用户 2026-10-18 点名「两个隔离开」那一条只说界面，
 * 但这面墙的观感口径是三站一起定的）。改夹取区间会同时动三站的整齐度，而主人这次点的是
 * **pixiv 站点**。所以新口径只对 [GallerySite.PIXIV] 生效，其余三站沿用既有的 `0.4~2.5`
 * （`LEGACY_*` 那两颗），不动它们一根手指。
 */

/** pixiv 的框比例区间（B 档：覆盖竖向跨页与横向大图两族，极端长条才夹）。 */
const val PIXIV_CARD_RATIO_MIN = 0.2f
const val PIXIV_CARD_RATIO_MAX = 4.0f

/** 图库三站沿用既有区间 —— 本次**不动**它们（理由见文件头注最后一段）。 */
const val LEGACY_CARD_RATIO_MIN = 0.4f
const val LEGACY_CARD_RATIO_MAX = 2.5f

/**
 * 这张卡的**框**该按什么比例摆（返回值 = 宽 / 高）。
 *
 * @param post 墙上那一张（比例取 [GalleryPost.cardRatio]：优先 large 档，退 preview，再退 width/height）。
 * @param fallback 站方一个尺寸都没给时用的占位比例（调用侧传自己的 token，本层不认识主题）。
 *
 * 三件事按序：
 * 1. 站方没报比例（`cardRatio == 0`）⇒ 交 [fallback]（不替站方编一个形状）；
 * 2. 按站点挑区间：pixiv 用 [PIXIV_CARD_RATIO_MIN]..[PIXIV_CARD_RATIO_MAX]，其余用 legacy 那一对；
 * 3. 夹进区间 —— 夹了才裁，所以**夹取区间就是"裁多少"的唯一旋钮**。
 */
fun cardBoxRatio(post: GalleryPost, fallback: Float): Float {
    val natural = post.cardRatio.takeIf { it > 0f } ?: return fallback
    val (min, max) = if (post.site == GallerySite.PIXIV) {
        PIXIV_CARD_RATIO_MIN to PIXIV_CARD_RATIO_MAX
    } else {
        LEGACY_CARD_RATIO_MIN to LEGACY_CARD_RATIO_MAX
    }
    return natural.coerceIn(min, max)
}

/**
 * 这张卡的框**是不是被夹过**（= 屏上会裁掉一条）。
 *
 * 纯读数，给取证探针与尺子用：`true` 就是"这一张在裁"。判断写成一颗函数而不是各调用侧
 * 自己比 —— 否则"哪儿算被夹"会长出第二个定义。
 */
fun cardBoxRatioClamped(post: GalleryPost, fallback: Float): Boolean {
    val natural = post.cardRatio.takeIf { it > 0f } ?: return false
    return natural != cardBoxRatio(post, fallback)
}
