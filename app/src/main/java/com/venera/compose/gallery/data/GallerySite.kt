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

    /**
     * Safebooru：Danbooru 官方的**全年龄镜像**，与 [GELBOORU] 同属 Danbooru 系
     * （`/posts.json`、`tag_string_*`、`media_asset.variants` 都是同一套字形），
     * 但它是**匿名可用**的 —— 不像 Gelbooru 那样 DAPI 匿名一律 401。
     *
     * ⚠️ 与 [GELBOORU] 的关键差别（都实测过，见 `cloudflare-ip-gallery-sources-2026-10-01.md`）：
     * - 它**走 Cloudflare**（解析到 `104.26.x` / `104.21.x` CF 段），国内裸连可达；
     *   而 `danbooru.donmai.us` 本身**不走 CF**、国内不可达。
     *   也就是说：**同一个 donmai.us，全年龄那个镜像能直连，主站不能** ——
     *   这正是"封锁按 hostname、不按 IP"那条实测的又一个例证。
     * - 图床 `cdn.donmai.us` 同样直连可达（实测 TCP 443 通），所以图片能真的显示出来，
     *   不是"API 通但图加载不出来"那种半残状态。
     * - 分级是 Danbooru 系的**单字母** `g`/`s`/`q`/`e`（同 yande.re 字形，
     *   与 Gelbooru 的单词 `general`/`sensitive` 不同）。
     * - 它有 `fav_count` 与 `tag_string_*` —— 现有另两站都没有，见 [GalleryPost] 的更新注释。
     */
    SAFEBOORU("Safebooru", "https://safebooru.donmai.us/posts/", "safebooru", "safebooru.donmai.us"),
    ;

    companion object {
        /** 未知 key 返回 null，由调用方**明说**而不是悄悄退回某一站。 */
        fun fromRouteKey(key: String?): GallerySite? = entries.firstOrNull { it.routeKey == key }
    }
}
