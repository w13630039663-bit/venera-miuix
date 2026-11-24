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
     * 官方 wiki `howto:api` 写明要 `&api_key=...&user_id=...`）。
     *
     * 所以这一站**没有账号就一张图都取不到** —— 不是"少几档权限"，是用不了。
     * 凭据见 [GelbooruAccount]，取法见 [GelbooruClient]。
     * UI 在未配账号时必须**直说这件事**，不能让它长成"这一站今天没图"。
     */
    GELBOORU("Gelbooru", "https://gelbooru.com/index.php?page=post&s=view&id=", "gelbooru", "gelbooru.com"),

    /**
     * Konachan：**Moebooru 系**（与 [YANDERE] 同一内核，不是 Danbooru 系），Anime/Manga 图站。
     *
     * - `konachan.net/post.json` 对 okhttp / curl / 浏览器 UA 一律 **200**，走 Cloudflare；
     * - `konachan.com` 挂 **JS 挑战盾**（"Just a moment"，各种 UA 都 403）—— 而画廊取数全走
     *   [com.venera.compose.data.platform.NoInteractiveBypassTag]（不弹过盾窗），对盾站等于永久失败；
     * - .net 还是**全年龄镜像**（实测 500 条样本 rating 全 `s`，`rating:e` 回 0 条）——
     *   与撤下的 Safebooru 同一档内容定位，顶替得干净。
     *
     * 与同系的 [YANDERE] 关键差别（都实测过）：
     * - 有 `order:score` 高分池（`tags=order:score` 实测真按分数降序：1718/1570/1349…）；
     * - ⚠️ `order:rank` 与 `order:fav_percent` **被静默忽略**（回的就是默认 id 序）——
     *   照抄旧 Safebooru 那颗的 `order:rank` 会拿到"一屏新图假装是高分"，正是本仓最忌的静默交错；
     * - `date:A..B order:score` 时间窗**真生效**（实测 2020 窗口回的全是 2020 的图），同 [YANDERE]；
     * - 有 `artist.json`（带 `urls`，实测精确命中）与 `/artist/show/{id}` → wiki 302 别名链，
     *   画师端点**本站自给**，不用借 danbooru；
     * - 没有 `fav_count`（与 [YANDERE] 同）；分级单字母 `s`/`q`/`e`，没有 Danbooru 系的 `g`；
     * - 端点形状同 [YANDERE]：`post.json?tags=id:N` 取单条，`/post/{id}.json` 实测 **404**。
     *
     * ⚠️ routeKey 由 `safebooru` 换成 `konachan`：两站 **id 池互不相干**（同号必是不同图），
     * 老 Safebooru 的收藏/历史行在读取处按"认不出的站键"整行摘除（[GallerySite.fromRouteKey]
     * 返回 null 那条既有口径），不迁移、不混显。
     */
    KONACHAN("Konachan", "https://konachan.net/post/show/", "konachan", "konachan.net"),

    /**
     * 三处结构性差别都要在调用点显式表态，别按 booru 那套的形状顺手接：
     *
     * 1. **`ranking.php` 那一腿没有 `rating` 格**（现量：`"rating" in contents[0] === false`）⇒ 不能沿用
     *    [GalleryPost.isAdultMarked] 那句「不在安全白名单里就算成人」——空串会让**每张 p 站卡**
     *    被判成成人档，现象是一面打码的黑墙。这一站的分级材料在 `illust_content_type`
     *    （13 枚旗标）+ `attr` + `is_masked`，且其中 `sexual` 是**计数**不是档位 ⇒ 只能当启发式。
     * 2. **不进来源轴**（选型 ⑤：首页那一格是**内容域**切换，不是第五枚来源 chip）。
     *    直连 200 / 每页 60 条 / `p=2` 真换页。先前那句"五路全灭"是同一个**错路径名**打了五遍
     *    （`ajax/search/{popular,top,newest,illust}` 与 `web/v1/search/illust` 都不存在），作废。
     *    不进来源轴的真实理由是**查询语法不同轴**：booru 那三站吃 `tags=a+b order:score`，
     *    pixiv 吃 `word` + 站内那串 `s_mode/order/type`（现成写法在答复的 `suggestChips[].url` 里），
     *    并进「全部」就是"半面墙按标签、半面墙按另一套"那种假读数。
     * 3. **载荷里 `illust_type` 与 `illust_page_count` 是字符串**（`"0"` / `"8"`），
     *    声明成 `Int` 会当场反序列化失败。且 `illust_type` **分不开单图与多页**
     *
     * 分页真生效；`/ajax/illust/{id}` 200、`/pages` 200 且每页给到 `original`；
     * 图床 `i.pximg.net` 无 `Referer` 403、带则 200 —— 后者已被
     * `data/network/ImageHeaderPolicy.kt` 的 `"pximg.net"` 规则覆盖，取流层不用改。
     *
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
