package com.venera.compose.gallery.data

/**
 * pixiv **动图（ugoira）**的一整条逐帧数据（2026-10-23，用户报「app pixiv 源的动图无法播放」）。
 *
 * ## 为什么需要这一颗，而不是复用 [GalleryPost] 的某一档地址
 *
 * pixiv 的动图**不是** GIF / 动图 WebP —— 站方给的是「一张静帧 + 一个逐帧 zip + 一份帧延时表」。
 * 三样分开看（现量真号 151160372 的 `/ajax/illust/{id}/ugoira_meta`）：
 *
 * - `src` / `originalSrc`：zip 的两个分辨率档（`…_ugoira600x600.zip` / `…_ugoira1920x1080.zip`）；
 * - `frames[]`：逐帧的 `{file: "000000.jpg", delay: 1000}`（毫秒），顺序就是播放顺序；
 * - 静帧：走的是普通的 `image_urls` / `urls`（第一帧），任何解码器解那张都只有一帧。
 *
 * 所以 [GalleryAnimationGate]（只管 GIF/动图 WebP 的 Coil 闸门）对 ugoira 永远为假 —— 那种字节
 * 压根不是动图格式。要播就得：下 zip、解出逐帧、按 delay 翻帧。这颗模型装的就是"翻帧"需要的全部。
 *
 * ## 档位
 *
 * [src] 给作品页那一格与播放器默认这一屏（600 级，省流量、够铺一屏）；
 * [originalSrc] 给播放器 HD 那一档（1920 级）。两档 zip 的 `frames[]` 表**逐帧相同**（同一批延时、
 * 同一批文件名），只有分辨率差 —— 所以翻帧进度、帧数对账只需认一份表。
 */
data class PixivUgoira(
    /** 小档 zip 地址（`src`）。作品页那一格吃它。空串 = 站方没给 ⇒ 这一条没法播。 */
    val src: String,
    /** 原档 zip 地址（`originalSrc`）。播放器 HD 那一档吃它；缺档退回 [src]。 */
    val originalSrc: String,
    /** 帧内图的编码（现量恒 `image/jpeg`，也见过 `image/png`）：决定解出来的每帧是什么扩展名。 */
    val mimeType: String,
    /** 逐帧延时（毫秒），**顺序即播放顺序**。空表 = 站方没给 ⇒ 没法播。 */
    val frames: List<PixivUgoiraFrame>,
) {
    /** 总时长（毫秒）。给"这一作大约几秒"那一格用；帧数不足两帧时无意义，交 0 由调用方不摆。 */
    val totalDelayMillis: Int
        get() = if (frames.size < 2) 0 else frames.sumOf { it.delay.coerceAtLeast(0) }
}

/**
 * 一帧：zip 里那个文件名 + 这一帧停多久。
 *
 * [file] 是 zip 内的**相对名**（`"000000.jpg"`），不是地址 —— zip 解出来就在本地那堆文件里，
 * 按这个名字取。[delay] 站方单位是毫秒（现量 100 / 125 / 750 / 1000 / 1500 都出现过），
 * 0 与负数一律夹到 [MIN_DELAY_MILLIS]（站方偶有 0，当 0 会翻帧闪死）。
 */
data class PixivUgoiraFrame(
    val file: String,
    val delay: Int,
)

/** 单帧延时的地板值（毫秒）：站方给 0 时不至于把整条动画变成"每帧 0ms 空转"。 */
const val UGOIRA_MIN_DELAY_MILLIS = 20
