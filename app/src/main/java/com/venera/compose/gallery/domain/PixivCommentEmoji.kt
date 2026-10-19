package com.venera.compose.gallery.domain

/**
 * pixiv 评论里的**小表情**（`(name)` 那一种）与站方图床的对应关系（2026-10-19 批 233-3）。
 *
 * ## 为什么这张表必须写死在代码里
 *
 * 站方不在评论答复里带表情清单 —— 它只把 `(normal)` 这样的字面串放进 `comment`，
 * 由客户端自己认。所以"认不出来的那一串"与"站方哪天新增一枚"是同一件事：
 * **未命中就原样念出 `(name)`**（屏上看到的还是站方给的那一串字），不猜、不吞、不换成方框。
 *
 * ## 这张表的出处（三家独立实现互证 + 本鱼自证 CDN）
 *
 * - 名字↔号：`ultranity/Pix-EzViewer`（Apache-2.0，`EmojiUtil.kt`，注明抄自 Pixiv-Shaft 的 `Emoji.java`）、
 *   `HookedBehemoth/pixiv-proxy`（`comment.rs` 的 `EMOJI_LOOKUP`）、`pikura-app/pikura`
 *   （`InlineArtworkViewer.axaml.cs`，那份注释还专门说明"这些**精确的**短码才是站方渲染器认的写法"）
 *   —— 三家的 38 条逐字一致；
 * - 地址：`https://s.pximg.net/common/images/emoji/{id}.png`（多家同串）；
 * - 本鱼 2026-10-19 现量：`101.png` → **200 / 848 字节**、`503.png` → **200 / 402 字节**、
 *   `999.png` → **404** ⇒ 号段是真的、不存在的号确实回 404（未命中那一档在屏上因此不会画成破图）。
 *
 * 号段一共 38 枚：101–108、201–209、301–310、401–408、501–503。
 *
 * ⚠️ 这一族与**贴纸**（stamp）不是一件事：贴纸是站方在答复里直接给一张完整图地址的大表情
 * （见 `data/PixivComment.stampUrl`），不需要这张表。
 */

/** 表情名 → 站方图号。未收录的名字交 null（调用点原样念出那一串字）。 */
fun pixivEmojiId(name: String): Int? = PIXIV_EMOJI_IDS[name]

/** 表情图地址。只在 [pixivEmojiId] 命中之后调用。 */
fun pixivEmojiUrl(id: Int): String = "https://s.pximg.net/common/images/emoji/$id.png"

/** 评论正文切成一段段：普通文字与表情交替。 */
sealed interface PixivCommentSegment {
    /** 原样搬的一段文字（含未命中的 `(xxx)` 那一串 —— 那是真话，不是没解析出来）。 */
    data class Text(val value: String) : PixivCommentSegment

    /** 一枚表情，[name] 是站方那一对圆括号里的短码（不含括号）。 */
    data class Emoji(val name: String) : PixivCommentSegment
}

/**
 * 按站方那一条短码形状切正文。
 *
 * 形状只认 `\(([a-z][a-z0-9]*)\)`：小写字母开头、后接字母或数字。
 * 这既挡住了"把 `(笑)`、`(1)` 这种也当表情"的误伤，也让**表里没有的**那一串
 * 走 [PixivCommentSegment.Text] 原样上屏（站方哪天新增一枚，屏上读到的是它的字面短码，
 * 而不是一块破图或一个空格）。
 */
fun splitPixivComment(text: String): List<PixivCommentSegment> {
    if (text.isEmpty()) return emptyList()
    val out = ArrayList<PixivCommentSegment>(4)
    var cursor = 0
    PIXIV_EMOJI_TOKEN_PATTERN.findAll(text).forEach { match ->
        if (match.range.first > cursor) out += PixivCommentSegment.Text(text.substring(cursor, match.range.first))
        out += PixivCommentSegment.Emoji(match.groupValues[1])
        cursor = match.range.last + 1
    }
    if (cursor < text.length) out += PixivCommentSegment.Text(text.substring(cursor))
    return out
}

private val PIXIV_EMOJI_TOKEN_PATTERN = Regex("""\(([a-z][a-z0-9]*)\)""")

private val PIXIV_EMOJI_IDS: Map<String, Int> = mapOf(
    "normal" to 101, "surprise" to 102, "serious" to 103, "heaven" to 104,
    "happy" to 105, "excited" to 106, "sing" to 107, "cry" to 108,
    "normal2" to 201, "shame2" to 202, "love2" to 203, "interesting2" to 204,
    "blush2" to 205, "fire2" to 206, "angry2" to 207, "shine2" to 208, "panic2" to 209,
    "normal3" to 301, "satisfaction3" to 302, "surprise3" to 303, "smile3" to 304,
    "shock3" to 305, "gaze3" to 306, "wink3" to 307, "happy3" to 308, "excited3" to 309, "love3" to 310,
    "normal4" to 401, "surprise4" to 402, "serious4" to 403, "love4" to 404,
    "shine4" to 405, "sweat4" to 406, "shame4" to 407, "sleep4" to 408,
    "heart" to 501, "teardrop" to 502, "star" to 503,
)
