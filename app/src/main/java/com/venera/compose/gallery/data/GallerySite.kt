package com.venera.compose.gallery.data

/**
 * 画廊接入的图片站。
 *
 * **站别是身份的一部分**：各站的 `id` 各自独立编号（实测 yande.re 才到 37 万级，
 * Gelbooru 已到 1497 万级），同号必定是两张不同的图。所以路由、列表 key、
 * 图片缓存 key 全部要带站点，否则"点 A 站这张"会静默取到 B 站的另一张。
 */
enum class GallerySite(
    /** 卡片左下角来源胶囊上的显示名（站方自己的写法，不是我们造的词）。 */
    val displayName: String,
    /** 单页地址前缀，拼上 [GalleryPost.id] 就是站方那条作品的网页地址。 */
    val pageUrlPrefix: String,
    /** 枚举名当路由参数用（导航对枚举的支持不稳，这里显式走字符串键）。 */
    val routeKey: String,
    /** API 域名。用户点"刷新/重试"时要清掉这一站的域名熔断（见 `GalleryScreen`）。 */
    val apiHost: String,
) {
    YANDERE("yande.re", "https://yande.re/post/show/", "yandere", "yande.re"),

    /**
     * ⚠️ Gelbooru 与 yande.re 有一个**根本差别**：它的 DAPI **匿名一律 401**
     * （实测 2026-09-26：`s=post` 与 `s=tag` 两条端点不带凭据都是 401，
     * 官方 wiki `howto:api` 写明要 `&api_key=...&user_id=...`）。
     *
     * 所以这一站**没有账号就一张图都取不到** —— 不是"少几档权限"，是用不了。
     * 凭据见 [GelbooruAccount]，取法见 [GelbooruClient]。
     * UI 在未配账号时必须**直说这件事**，不能让它长成"这一站今天没图"。
     */
    GELBOORU("Gelbooru", "https://gelbooru.com/index.php?page=post&s=view&id=", "gelbooru", "gelbooru.com"),
    ;

    companion object {
        /** 未知 key 返回 null，由调用方**明说**而不是悄悄退回某一站。 */
        fun fromRouteKey(key: String?): GallerySite? = entries.firstOrNull { it.routeKey == key }
    }
}
