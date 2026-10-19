package com.venera.compose.gallery.data

/**
 * pixiv 一位画师的**头部读数**（2026-10-19 · pixiv 画师页）。
 *
 * 一条匿名腿就够：`GET https://www.pixiv.net/ajax/user/{id}?full=1`（本机实测 200，
 * 不带任何头即可读 JSON —— 与 `/ajax/illust/{id}` 同一条口径）。
 *
 * ## 为什么单开一颗模型，不并进 [PixivUser]
 *
 * [PixivUser] 是**关注 / 推荐横排里的一行**（只有 id / 名字 / 账号 / 头像 / 静音位），
 * 那条腿（`user_previews[]`）根本不回背景图、简介、外链、关注数。反过来这颗里的
 * `backgroundUrl` 在横排那一格上永远空着 —— 屏上读起来就是"这一位没有背景"，
 * 而那是站方压根没在这条腿里给。两件事给两个模型，与 [PixivWork] / [PixivWorkDetail]
 * 那条分法同源（本仓为"可空格子"这一类假读数付过账）。
 *
 * ⚠️ 与 [PixivUser] 同 id 时是**同一个人**，不是两本账：画师页拿 [id] 去读关注态、
 * 去读收藏，横排拿 [PixivUser.id] 跳进来，两处共用一个 `user_id`。
 *
 * ## 每一格"取不到"写成 null，不写成空串、不写成 0
 *
 * `background` 整个键为 `null` 是站方的**合法答复**（这位没设置背景），
 * 那时 [backgroundUrl] 为 null，由调用方回退到首张作品（见画师页的回退链）。
 * 而"这一跳没答上"是另一回事，由取数层的 `Result.failure` 承担 —— 两者不许在屏上同形。
 */
data class PixivUserHead(
    val id: Long,
    /** 站方显示的画师名（`name`）。可能与 [account] 不同。 */
    val name: String,
    /** 站方的 `@handle`。老账号可能没设过 ⇒ 空串（不是 null：键一定在，只是值空）。 */
    val account: String,
    /**
     * 头像档（`imageBig` 优先，170×170 级；缺档退 `image` 的 50px 级）。
     *
     * null = 站方这一条没有可用地址 ⇒ 界面退首字母座，**不拿别人的头像顶**
     * （口径与 [PixivUser.avatarUrl] 逐字同一条）。
     */
    val avatarUrl: String?,
    /**
     * 作者**自己设置**的主页背景，站方已裁成 1920×960 的横档
     * （实测形如 `…/c/1920x960_80_a2_g5/background/img/…png`）。
     *
     * null = 这位没设置（站方回 `"background": null`）⇒ 画师页回退到首张作品。
     * **不是空串**：空串会让"没设置"与"地址坏掉"在屏上同形。
     */
    val backgroundUrl: String?,
    /** 自我简介（`comment`，纯文本；HTML 那版在 `commentHtml`，我们不用它 —— 见下）。 */
    val comment: String,
    /**
     * 站方登记的外链（`social.{twitter,pawoo}.url` + `webpage`），已经过一个筛：
     * 只留 http(s) 开头的。认不出的平台由 `GalleryArtistLinks` 那一层再判
     * （那是全仓唯一一份平台判定，这里不抢它的活）。
     */
    val links: List<String>,
    /**
     * 这位画师**自己关注了多少人**（`following`）。null = 这条腿没给。
     *
     * ⚠️ 这不是粉丝数：粉丝数（`total_follow_users`）只有带票据的
     * `/v1/user/detail` 才给。两件事在屏上是两个格子，混用就是编数。
     */
    val followingCount: Int?,
    /** 站方标的会员位（`premium`）。只作读数，不影响本页任何取数。 */
    val premium: Boolean,
)
