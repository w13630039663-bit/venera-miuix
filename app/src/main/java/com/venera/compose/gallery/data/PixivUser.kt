package com.venera.compose.gallery.data

/**
 * pixiv 的一位**画师**（站方叫 user）。
 *
 * 与 [com.venera.compose.gallery.data.FollowedArtistRef] **不是同一件事**，两本账不要互相顶：
 * - 那一颗是**图库站**的关注名单，键是 `routeKey:标签名`（三站里同一名字会合并成一枚）；
 * - 这一颗是 pixiv 的 `user.id`，站方那一侧的唯一身份。
 *
 * 2026-10-17 第八轮（用户「关注画师你参考 pixez-miuix 是怎么拿到的」）落地：
 * 取数就两条 app-api 腿，两条都**必须带票据**：
 * - 关注列表 `GET /v1/user/following?filter=for_android&user_id={自己}&restrict=public`
 * - 推荐用户 `GET /v1/user/recommended?filter=for_android`
 *
 * 两者的答复**同一个形状**（`user_previews[]` + `next_url`），所以共用 [PixivUserPage]。
 */
data class PixivUser(
    val id: Long,
    val name: String,
    /** 站方给的那个 `@handle`。可能为空（老账号没设过）。 */
    val account: String,
    /**
     * 头像地址（`profile_image_urls.medium`，px_170x170）。
     * 空串 = 站方这一次没给 —— 界面按"首字母圆座"降级，**不拿别人的头像顶**。
     */
    val avatarUrl: String,
    /** 站方标的"已静音"。只作读数，不影响这一栏摆不摆。 */
    val muted: Boolean,
)

/**
 * `user_previews` 的一页。
 *
 * [nextUrl] 为 null 只有一种含义：**到底了**（站方没给 `next_url`）。
 * 与 [PixivAppPage] 同一条口径 —— 翻页只认站方给的那串地址，不自己拼 `offset`。
 */
data class PixivUserPage(
    val users: List<PixivUser>,
    val nextUrl: String?,
)
