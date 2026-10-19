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

    /**
     * pixiv：**只做图片，且只有榜单**（2026-10-16 选型 ⑤）。与上面三站不是同一类东西，
     * 三处结构性差别都要在调用点显式表态，别按 booru 那套的形状顺手接：
     *
     * 1. **`ranking.php` 那一腿没有 `rating` 格**（现量：`"rating" in contents[0] === false`）⇒ 不能沿用
     *    [GalleryPost.isAdultMarked] 那句「不在安全白名单里就算成人」——空串会让**每张 p 站卡**
     *    被判成成人档，现象是一面打码的黑墙。这一站的分级材料在 `illust_content_type`
     *    （13 枚旗标）+ `attr` + `is_masked`，且其中 `sexual` 是**计数**不是档位 ⇒ 只能当启发式。
     * 2. **不进来源轴**（选型 ⑤：首页那一格是**内容域**切换，不是第五枚来源 chip）。
     *    ⚠️ 2026-10-16 深夜改判：**匿名搜索其实是通的** —— `www.pixiv.net/ajax/search/artworks/{词}`
     *    直连 200 / 每页 60 条 / `p=2` 真换页。先前那句"五路全灭"是同一个**错路径名**打了五遍
     *    （`ajax/search/{popular,top,newest,illust}` 与 `web/v1/search/illust` 都不存在），作废。
     *    不进来源轴的真实理由是**查询语法不同轴**：booru 那三站吃 `tags=a+b order:score`，
     *    pixiv 吃 `word` + 站内那串 `s_mode/order/type`（现成写法在答复的 `suggestChips[].url` 里），
     *    并进「全部」就是"半面墙按标签、半面墙按另一套"那种假读数。
     * 3. **载荷里 `illust_type` 与 `illust_page_count` 是字符串**（`"0"` / `"8"`），
     *    声明成 `Int` 会当场反序列化失败。且 `illust_type` **分不开单图与多页**
     *    （日榜现量：type=0 的 34 条里 12 条是多页）⇒ 用户 2026-10-16 定档「多页取第 0 页当图」。
     *
     * 量通的三跳（同日复测，真号 150822243）：`ranking.php?mode=daily&format=json` 200 / 50 条 /
     * 分页真生效；`/ajax/illust/{id}` 200、`/pages` 200 且每页给到 `original`；
     * 图床 `i.pximg.net` 无 `Referer` 403、带则 200 —— 后者已被
     * `data/network/ImageHeaderPolicy.kt` 的 `"pximg.net"` 规则覆盖，取流层不用改。
     *
     * 2026-10-16 深夜对 `ajax/search/artworks/{词}` 的现量（直连无代理，词=1girl / all / 猫）：
     * 载荷**自带**卡片要的一切（`url` 就是 `i.pximg.net` 缩略，**零额外请求**）、`tags[]` 是纯字符串、
     * 有 `xRestrict`（⇒ 上面坑 1 只咬 `ranking.php` 那一腿）、有 `aiType`（60 条里 12 枚 =1）、
     * 有 `pageCount`（49/60 单页）；默认序**就是** `createDate` 降序（六份样本全部单调），
     * 而 `sort=` / `order=` 五种变体首三条一字不差 ⇒ 挂在这条路上那些参数是被忽略的。
     * 两处结构性缺口记在这儿：**没有"全站最新"**（`word` 一律当标签名解释，`all` 命中的是同名标签，
     * `canonical` 就是 `/tags/all`）；**「这位画师的新作」匿名取不到**（`ajax/user/{id}/illustrations`
     * 与 `/illusts` 分别 404 / 400，四种参数都是 400，`ajax/illusts/search` 404）——
     * 所以「关注画师」那一节只有名单与头像，不带新图墙。
     *
     * ⚠️ 名字撞车：`gallery/domain/GalleryArtistLinks.kt` 已把 `PIXIV` 用作**画师外链平台**，
     * 而关注关系的键是「标签名身份」（`GalleryArtistFollows.uidOf`），这一站的画师身份是
     * `user_id` —— 两本账不同轨，别把这两个 PIXIV 当同一件事。
     */
    PIXIV("Pixiv", "https://www.pixiv.net/artworks/", "pixiv", "www.pixiv.net"),
    ;

    companion object {
        /** 未知 key 返回 null，由调用方**明说**而不是悄悄退回某一站。 */
        fun fromRouteKey(key: String?): GallerySite? = entries.firstOrNull { it.routeKey == key }

        /**
         * pixiv 那道共用的门（2026-10-16 选型 ⑤ 的第一批）。
         *
         * 放在 companion 而不是顶层函数：调用点散在 `gallery/domain` 与 `gallery/ui` 两处包，
         * 而那两处都已经 import 了 [GallerySite] —— 省掉 14 行 import，也省掉 FQN 内联链。
         *
         * **为什么现在可以抛**：这一批只让 pixiv 进枚举、让 14 处穷尽 `when` 逐条表态，
         * **还没有给它任何取数路径** —— 没有 pixiv 的卡能上墙、能进历史、能被收藏，
         * 所以走到这里在构造上不可能。宁可在这里炸并说出为什么，也不要退回"当成 yande.re 打"
         * 那种静默交错（`GalleryRanking.suffix` 从前那枚 `else -> YANDERE_SCORE` 就是这个形状）。
         *
         * 下一批接上榜单腿与"按标签"那一跳之后，**可达的那几处要逐处换成真调用**（大图页与历史按号取、
         * 卡片来源徽标），来源轴那几处继续留在这里 —— 不是没来得及接，是不该接（选型 ⑤ 走内容域）。
         */
        fun notWired(via: String): Nothing = throw UnsupportedOperationException(
            "pixiv 走到「$via」这一路，但它的取数还没接线：2026-10-16 选型⑤ 只开榜单与" +
                "按标签那一跳，pixiv 也不进来源轴（与 booru 的 tags= 语法不同轴，是选择不是数据墙）。" +
                "走到这里说明有东西把 pixiv 塞进了它不该进的面。",
        )
    }
}
