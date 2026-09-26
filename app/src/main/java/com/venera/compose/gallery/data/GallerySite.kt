package com.venera.compose.gallery.data

/**
 * 画廊接入的图片站。
 *
 * **站别是身份的一部分**：两站的 `id` 各自独立编号（实测 yande.re 才到 37 万级，
 * Danbooru 已到 1225 万级），同号必定是两张不同的图。所以路由、列表 key、
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
    DANBOORU("Danbooru", "https://danbooru.donmai.us/posts/", "danbooru", "danbooru.donmai.us"),
    ;

    companion object {
        /** 未知 key 返回 null，由调用方**明说**而不是悄悄退回某一站。 */
        fun fromRouteKey(key: String?): GallerySite? = entries.firstOrNull { it.routeKey == key }
    }
}
