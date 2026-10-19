package com.venera.compose.gallery.data

/**
 * 一条 pixiv **评论**（2026-10-19 · 批 231-F）。
 *
 * ## 它只能从登录腿拿到
 *
 * 匿名那条走不通（2026-10-19 本机四形状全 404，与仓内 2026-10-18 的记录一致）：
 * `www.pixiv.net/ajax/illust/{id}` 只给得出**条数** `commentCount`，给不出正文与作者。
 * 所以这一族的唯一来处是 `app-api /v3/illust/comments`（带票据）。
 * 版本号那条是**现量定的**：`/v1/illust/comments` 无票据回 404（路由不存在），
 * `/v3/illust/comments` 回 400（路由在、缺凭据）—— 判据与日期记在
 * [PixivAppClient.illustComments] 头注。
 *
 * ## 每一格"站方没给"都留 null / 空串，不编
 *
 * - [userName] / [userAccount] / [userAvatarUrl]：`user` 可能是 `{}`（**删号**）——
 *   那时这三格都是空，而这条评论**照样要摆**（少一条真评论比多一格空白更糟）。
 *   头像空串时由界面退首字母座，**不拿别人的脸顶**。
 * - [date]：站方给 ISO 串（带时区）。**不做本地化换算**（换算与截日在 `PixivWorkPage` 那一层判，
 *   本模型只负责原样搬运）。
 * - [replyToId]：站方用 `parent_comment: {}` 表达"没有父评论" ⇒ 这里为 null。
 */
data class PixivComment(
    /** 站方的评论号。用于列表 key 与去重（翻页时同一批可能重叠）。 */
    val id: Long,
    /** 正文（`comment`，纯文本；站方那一格**不含** HTML）。 */
    val text: String,
    /** 发布时间的 ISO 串（`date`，如 `2026-10-01T06:12:00+09:00`）。 */
    val date: String?,
    val userName: String,
    val userAccount: String,
    /** 评论人头像（`user.profile_image_urls.medium`）。空串 = 没给 / 删号。 */
    val userAvatarUrl: String,
    /** 回复的是哪一条（`parent_comment.id`）。null = 顶级评论。 */
    val replyToId: Long?,
    /**
     * 贴纸（stamp）那张图的地址（`stamp.stamp_url`）。空串 = 这一条没贴贴纸。
     *
     * 2026-10-19 用户要"正常显示评论图和表情" —— 先把这一格说清楚：**pixiv 评论不能附图**。
     * 六家实现（Shaft / pixiv-client / Flare / pixnya / pixez / XTA）的评论字段表里都只有
     * `stamp`，没有 `pics` / `images` / `attachment`；用户在站方看到的"评论带图"就是这一枚贴纸。
     * 地址是**完整可用的那一档**（实测只有 `generated-stamps/{id}_s.jpg`，换 `_m` / `_l` / 去后缀都 404），
     * 所以这里原样搬，不拼、不换尺寸段。
     *
     * ⚠️ 光有贴纸、正文为空的那一条**也要留下**（见 [text]）：站方允许纯贴纸评论，
     * 把它丢掉就是"屏上条数比站方少"那一族假话。
     */
    val stampUrl: String,
)

/**
 * 评论的一页。
 *
 * [nextUrl] 为 null 只有一种含义：**到底了**（站方没给 `next_url`）。
 * 翻页只认这一串，不自己拼 `offset` —— 与推荐 / 榜单 / 书签同一条纪律。
 *
 * [totalCount] 是站方在这一页答复里报的总条数（`total_comments`；**我们不带任何额外参数**，
 * v3 那一腿只发 `illust_id`）。null = 这一条没给 ⇒ 屏上不念那个数
 * （它本来就与作品详情腿的 `commentCount` 同源，念两遍还会对不上）。
 */
data class PixivCommentPage(
    val comments: List<PixivComment>,
    val nextUrl: String?,
    val totalCount: Int?,
)
