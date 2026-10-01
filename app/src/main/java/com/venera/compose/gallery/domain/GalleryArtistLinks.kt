package com.venera.compose.gallery.domain

/**
 * 画师的一条外链被认成**哪个平台**。
 *
 * [OTHER] 不是一个"待办分类"，而是一个**结论**：画师行（[GalleryArtistLinks.iconPlan]）只摆
 * PIXIV / TWITTER / FANBOX 三家，其余一枚都不摆。理由是屏上那两枚图标带的是"pixiv / 推 / fanbox"
 * 的具体含义，把它们换成一个通用链接图标就等于宣称"这是这位画师的 pixiv"而实际不是 —— 那类错没人会来报。
 *
 * 后三家（[INSTAGRAM] / [TUMBLR] / [YOUTUBE]）是批次 Q+R 为**画师介绍页**加的：那一页摆的是全平台，
 * 一枚都要摆、认不出的也摆（标签给域名尾巴），所以判定面必须跟着扩。
 * 画师行那一侧**不因此变宽** —— `iconPlan` 的挑选条件与 `MAX_ICONS` 一条都没动。
 *
 * 计划里原本还有一档 `WEB`（"看着像个人网站根"的未知域名），量过现成用例后**刻意不加**：
 * `rainboy.jp/`、`yunting.artstation.com/` 这类站点根在 2026-09-30 就被判死为 OTHER
 * （"认不出的服务一律 OTHER 不猜"，`GalleryArtistLinksTest` 钉着），而它与 OTHER 在屏上
 * 长成同一个样子（标签都是域名），加一档只会把一条已经拍过的判据劈成两份。
 */
enum class GalleryArtistLinkPlatform {
    PIXIV,
    TWITTER,
    INSTAGRAM,
    FANBOX,
    TUMBLR,
    YOUTUBE,
    OTHER,
}

/**
 * 认出来的一个平台入口。
 *
 * [url] 保持站方给的原样（不规范化、不改写成 https），**作品页反查出来的那一条除外** ——
 * 那条是我们自己拼的 `users/{id}`，因为出处给的是作品页，而入口要落在"这个人"上。
 * [account] 只有反查那一条带：站方登记的外链本来就是这位自己的主页，而反查来的是
 * "这张图的作者的主页"，两者在屏上必须能分辨（用户 2026-09-30 拍板"照摆，胶囊带账号"）。
 */
data class GalleryArtistLink(
    val platform: GalleryArtistLinkPlatform,
    val url: String,
    val account: String? = null,
)

/**
 * 画师外链的平台判定（批次 L，判据层的唯一一份）。
 *
 * 全部形态来自 2026-09-30 的真读数（yande.re `artist.json` 的 `urls`、Gelbooru 原生画师记录页、
 * 以及 SauceNAO 那侧混着给的同一批脏串），三条最不显然的：
 *
 * - **老式 pixiv 图床目录不算个人主页**：站方在 `member.php?id=…` 旁边就挨着给
 *   `img42.pixiv.net/img/tehu48/` 与 `i1.pixiv.net/img25/img/xerd008ss/`，那是一台镜像上的
 *   目录列表，不是"这位本人"。按域名尾巴判 `pixiv.net` 就会把它认成 pixiv 主页。
 * - **作品页的编号不是用户编号**：`/artworks/1234567` 里那串是作品 id，拿它去要头像
 *   就是**把别人的脸挂到这位名下**，而且全程不会报错。
 * - **`fanbox` 的 host 尾巴是 `pixiv.net` 时也要先认 fanbox**（`www.pixiv.net/fanbox/creator/…`）。
 *
 * 与 [com.venera.compose.gallery.data.isDirectImageUrl]（SauceNAO 那头"这条是不是裸图"）
 * 刻意不合并：那一层问的是"打开来源该跳页面还是图片"，这一层问的是"这条链接是不是**这个人**"。
 * 同一条 `img42.pixiv.net/img/tehu48/` 在那边算页面、在这边不算主页 —— 合并就会两边都判错。
 */
object GalleryArtistLinks {

    /** 画师行里最多摆几枚图标（用户 2026-09-30 定：pixiv 恒第一，第二枚推优先、无推则 fanbox）。 */
    const val MAX_ICONS = 2

    /** 站方给的"个人主页"形态里，这些 pixiv 子域**只托管图或目录**，不是人。 */
    private val PIXIV_IMAGE_ONLY_SUBDOMAINS = Regex("^(img\\d*|i\\d+)$")

    /**
     * 裸图文件名 → 作品号（批次 M · M4）。
     *
     * 中间那一段用 `[^.]+` 而不是 `.+`：文件名里 `_p0`、`_p0_square1200`、`_ugoira600x600`
     * 都要吃进去，但**不能吃掉扩展名前的那个点**，否则 `147950802_p0.jpg` 会整体匹配失败。
     */
    private val PIXIV_IMAGE_FILE_ARTWORK_ID =
        Regex("^(\\d+)(?:_[^.]+)?\\.(?:jpg|jpeg|png|gif|webp|zip|bmp)$")

    /** 推特自家页面（不是某个人），把它们挂到画师行上就是把平台页冒充成画师主页。 */
    private val TWITTER_RESERVED = setOf(
        "home", "intent", "search", "share", "hashtag", "i", "settings", "explore",
        "notifications", "messages", "bookmarks", "compose", "login", "signup",
    )

    /** instagram 的保留段。与 [TWITTER_RESERVED] 同一目的：那些是平台页，不是某位本人。 */
    private val INSTAGRAM_RESERVED = setOf(
        "p", "reel", "reels", "stories", "share", "explore", "accounts", "about", "legal",
        "developer", "api", "direct", "home", "s", "signup", "login", "business", "web",
        "embeds", "directory",
    )

    /** 站方规则：字母、数字、点、下划线，最长 30。留一点余量，超出的本来也不是账号页。 */
    private val INSTAGRAM_HANDLE = Regex("^[A-Za-z0-9._]{1,34}$")

    /** `www` / `secure` / `assets` 这几台子域是 tumblr 自己的，不是某个创作者的 blog。 */
    private val TUMBLR_RESERVED_SUBDOMAINS = setOf("www", "secure", "assets", "api", "stage")

    private val TUMBLR_RESERVED = setOf(
        "dashboard", "explore", "login", "signup", "about", "help", "press", "policies",
        "themes", "staff", "embed", "buttons", "badge",
    )

    private val TUMBLR_BLOG = Regex("^[A-Za-z0-9][A-Za-z0-9-]{1,62}$")

    fun platformOf(url: String): GalleryArtistLinkPlatform {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return GalleryArtistLinkPlatform.OTHER
        val host = hostOf(trimmed)
        if (host.isEmpty()) return GalleryArtistLinkPlatform.OTHER
        val path = pathOf(trimmed)

        // fanbox 两种写法都认（子域那台是站方常给的，www.pixiv.net/fanbox 那台尾巴是 pixiv.net）。
        if (isUnder(host, "fanbox.cc")) return GalleryArtistLinkPlatform.FANBOX
        if (isUnder(host, "pixiv.net") && path.startsWith("fanbox/")) return GalleryArtistLinkPlatform.FANBOX

        if (isUnder(host, "pixiv.me")) return GalleryArtistLinkPlatform.PIXIV
        if (isUnder(host, "pixiv.net")) {
            if (isPixivImageMirror(host)) return GalleryArtistLinkPlatform.OTHER
            return if (path.startsWith("artworks/")) GalleryArtistLinkPlatform.OTHER else GalleryArtistLinkPlatform.PIXIV
        }
        if (isUnder(host, "pximg.net")) return GalleryArtistLinkPlatform.OTHER

        if (isTwitterHost(host)) {
            // 只有认得出 handle 的那几条才算"这个人的主页"；`/home`、`/intent/…` 那些是平台页。
            return if (twitterHandle(trimmed) != null) GalleryArtistLinkPlatform.TWITTER else GalleryArtistLinkPlatform.OTHER
        }
        if (isUnder(host, "instagram.com")) {
            return if (instagramHandle(trimmed) != null) {
                GalleryArtistLinkPlatform.INSTAGRAM
            } else {
                GalleryArtistLinkPlatform.OTHER
            }
        }
        if (isUnder(host, "tumblr.com")) {
            return if (tumblrBlog(host, trimmed) != null) GalleryArtistLinkPlatform.TUMBLR else GalleryArtistLinkPlatform.OTHER
        }
        if (isUnder(host, "youtube.com")) {
            return if (youtubeChannel(trimmed) != null) GalleryArtistLinkPlatform.YOUTUBE else GalleryArtistLinkPlatform.OTHER
        }
        // `youtu.be/xxxx` 那台短链**只指向一条视频**，没有"这个人"的形状可用。
        return GalleryArtistLinkPlatform.OTHER
    }

    /**
     * 这条 pixiv 链接里的**用户 id**。拿不到就 null（不是 0，也不是"换个形态再猜"）。
     *
     * 认得出的三种（今天都实测出现过）：`/users/7075448`、`/en/users/49622591`、
     * `member.php?id=1360347`（Gelbooru 原生记录里 14/14 都是这最后一种）。
     * 明确不要的：`/artworks/N`（作品号）、`stacc`、`sketch`、`pixiv.me` 短链、图床目录。
     */
    fun pixivUserId(url: String): Long? {
        if (platformOf(url) != GalleryArtistLinkPlatform.PIXIV) return null
        val path = pathOf(url)
        if (path.isEmpty()) return null
        // member.php?id=NNN
        if (path.startsWith("member.php")) {
            val raw = queryOf(url).split('&', ';')
                .firstOrNull { it.startsWith("id=", ignoreCase = true) }
                ?.substringAfter('=') ?: return null
            return raw.toLongOrNull()?.takeIf { it > 0 }
        }
        val segments = path.trimEnd('/').split('/')
        val usersIndex = segments.indexOfLast { it == "users" }
        // `stacc/q18607`、`@seoyul` 这类：整条路径里没有 users 那一段，就是没有可取的编号。
        if (usersIndex < 0) return null
        val candidate = segments.getOrNull(usersIndex + 1) ?: return null
        val id = candidate.toLongOrNull() ?: return null
        return id.takeIf { it > 0 }
    }

    /** 推特 handle（不含 @，**保持站方给的大小写**）。认不出人的一律 null。 */
    fun twitterHandle(url: String): String? {
        val host = hostOf(url)
        if (!isTwitterHost(host)) return null
        val first = rawPathOf(url).trimEnd('/').substringBefore('/')
        if (first.isEmpty() || first.lowercase() in TWITTER_RESERVED) return null
        // 站方规则：1–15 个字符，只允许字母数字与下划线。不合这个就不当它是账号页。
        return if (first.matches(Regex("^[A-Za-z0-9_]{1,15}$"))) first else null
    }

    /**
     * instagram 上"这位本人"的地址（批次 Q+R 为介绍页加的档）。
     *
     * **只认单段 handle**：真样本里出现过的三种写法（`instagram.com/jyaian3399`、
     * `www.instagram.com/pavanioficial/`、`…/zerotwo_ov/`）全是这一种，而 `/reel/`、`/stories/`、
     * `/share/profile/` 一条都没有。多段的一律不认 —— 那些是**内容页或分享页**，
     * 挂到画师行上就是"这张图"冒充"这位人"（与 `/artworks/N` 判 OTHER 同一条口径）。
     */
    fun instagramHandle(url: String): String? {
        if (!isUnder(hostOf(url), "instagram.com")) return null
        val segments = rawPathOf(url).trim('/').split('/').filter { it.isNotEmpty() }
        if (segments.size != 1) return null
        val first = segments[0]
        if (first.lowercase() in INSTAGRAM_RESERVED) return null
        return if (first.matches(INSTAGRAM_HANDLE)) first else null
    }

    /**
     * tumblr 上"这位本人的 blog"。两种形状指向同一个东西：**子域或末段就是创作者 id**。
     *
     * 子域那种是真样本里唯一出现过的（`j-pegillustmain.tumblr.com/`）；`www.tumblr.com/<id>`
     * 是同语义的第二种写法，认它不会挂错人。平台自家页（dashboard / explore / 根）一律 null。
     */
    fun tumblrBlog(host: String, url: String): String? {
        if (!isUnder(host, "tumblr.com")) return null
        val sub = host.removeSuffix(".tumblr.com")
        if (sub.isNotEmpty() && sub !in TUMBLR_RESERVED_SUBDOMAINS) return sub
        if (host != "tumblr.com" && host != "www.tumblr.com") return null
        val segments = rawPathOf(url).trim('/').split('/').filter { it.isNotEmpty() }
        if (segments.size != 1) return null
        val first = segments[0]
        if (first.lowercase() in TUMBLR_RESERVED) return null
        return if (first.matches(TUMBLR_BLOG)) first else null
    }

    /**
     * youtube 的**频道页**。只认结构上唯一的两条：`@handle` 与 `channel/UC…`。
     *
     * 真样本里一条 youtube 都没有，所以这里不铺形状：`user/<名>`、`c/<名>` 那些老写法
     * 与 `shorts`、`watch`、`youtu.be` 一起留 OTHER —— 认错的代价是把一条视频摆成一个人，
     * 认不出的代价只是少一枚胶囊。
     */
    fun youtubeChannel(url: String): String? {
        if (!isUnder(hostOf(url), "youtube.com")) return null
        val segments = rawPathOf(url).trim('/').split('/').filter { it.isNotEmpty() }
        val first = segments.firstOrNull() ?: return null
        if (first.startsWith("@") && first.length > 1) return first
        if (first == "channel" && segments.getOrNull(1)?.startsWith("UC") == true) return segments[1]
        return null
    }

    /** 认不出平台时胶囊上摆的那串：域名尾巴（`www.dlsite.com` 这种读得出是哪家的东西）。 */
    fun hostLabel(url: String): String = hostOf(url).ifEmpty { url.trim() }

    /**
     * 胶囊上那一小截平台名。用站方自己的写法，不造新词（`X` 是 it.com 现在的主名）。
     *
     * 只有**作品页反查来的那一条**带账号（`pixiv tokenbox`）：站方登记的画师外链本来就是这位的主页，
     * 而反查来的是"这张图的作者"的主页 —— 实测两者只有 4/11 同名，把账号写在屏上，
     * 张冠李戴就是看得见的，而不是要点开才发现。
     */
    fun chipLabel(link: GalleryArtistLink): String = when (link.platform) {
        GalleryArtistLinkPlatform.PIXIV -> link.account?.let { "pixiv $it" } ?: "pixiv"
        GalleryArtistLinkPlatform.TWITTER -> "X"
        GalleryArtistLinkPlatform.INSTAGRAM -> "Instagram"
        GalleryArtistLinkPlatform.FANBOX -> "FANBOX"
        GalleryArtistLinkPlatform.TUMBLR -> "Tumblr"
        GalleryArtistLinkPlatform.YOUTUBE -> "YouTube"
        GalleryArtistLinkPlatform.OTHER -> hostLabel(link.url)
    }

    /**
     * 这条 pixiv 链接里的**作品号**。认不出来一律 null。
     *
     * 它是"要不要多花一笔请求去反查作者"的闸门：出处给的可能是作品页、也可能是**裸图**，
     * 两种都带着作品号（页面上那串数字 / 文件名 `<作品号>_p<页>.<扩展名>`），而站方的
     * `ajax/illust/{号}` 匿名答得上作者。
     *
     * 认得出的四类：`/artworks/N`（含 `/en/artworks/N`）、`member_illust.php?…&illust_id=N`、
     * `i.pximg.net` 的裸图、老式图床子域（`img.pixiv.net` / `img42.pixiv.net` / `i1.pixiv.net`）
     * 下 `img/<账号>/` 里的裸图。后两类是批次 M · M4 加进来的：用户报「这类出处的画师信息
     * 有时能显示有时不能」，"有时不能"就是出处只给了裸图的那些帖子。
     *
     * 明确不认：`/users/N` 与 `member.php?id=N`（那是用户号，本来就是"这个人"）、
     * 图床上的**目录**（`img42.pixiv.net/img/tehu48/`，站方把它当个人主页给，里面没有号）、
     * 别家图床（推特的文件名里也可能有一串数字，拿它去问 pixiv 就是把别人的作品号当成这件的）。
     */
    fun pixivArtworkId(url: String): Long? {
        val trimmed = url.trim()
        val host = hostOf(trimmed)
        val imageHost = isUnder(host, "pximg.net") || (isUnder(host, "pixiv.net") && isPixivImageMirror(host))
        if (imageHost) return artworkIdFromFileName(rawPathOf(trimmed))
        if (!isUnder(host, "pixiv.net")) return null
        val path = pathOf(trimmed)
        Regex("artworks/(\\d+)").find(path)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }?.let { return it }
        if (!path.startsWith("member_illust.php")) return null
        return queryOf(trimmed).split('&')
            .firstOrNull { it.startsWith("illust_id=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
    }

    /**
     * 从裸图地址的**最后一段**里取作品号。
     *
     * 只看最后一段是必须的：pixiv 图床的路径里 `img/2026/08/03/00/00/35/` 那几段全是纯数字，
     * "取路径里第一串数字"会稳定取到年份。目录地址（以 `/` 收尾）最后一段是空串，自然落 null。
     */
    private fun artworkIdFromFileName(rawPath: String): Long? {
        val file = rawPath.substringAfterLast('/', "").lowercase()
        if (file.isEmpty()) return null
        return PIXIV_IMAGE_FILE_ARTWORK_ID.matchEntire(file)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 }
    }

    /**
     * 画师行上那最多两枚图标，按 **pixiv > 推 > fanbox** 的固定优先级摆。
     *
     * 两条不显然的挑选规则：
     * - **pixiv 那枚优先挑带用户 id 的那条**（头像要从它取）。站方常同时给 `stacc` 与 `users/NNN`，
     *   跟着原序取第一条就会拿一条要不到头像的地址去摆头像。
     * - **同一个 handle 的 twitter 与 x.com 两条只摆一枚**（实测 lichiko 就是两条并给），
     *   摆两枚在屏上读起来是"这里挂了两位"。
     * 认不出平台的一条都不进结果。
     *
     * 三档来源的**优先次序是写死的**：站方登记的画师记录 > 这条帖子的出处（[source]）>
     * 作品页反查出的作者主页（[artworkAuthorUrl]）。前一条的理由见 [fromSource]；最后一条只在
     * **前三枚里根本没有 pixiv** 时才补，因为它是"这张图的作者"而不是"这位画师"——
     * 站方画师档与作品作者实测只有 4/11 同名，能不用就不用。
     */
    fun iconPlan(
        urls: List<String>,
        source: String? = null,
        artworkAuthorUrl: String? = null,
        artworkAuthorAccount: String? = null,
        maxIcons: Int = MAX_ICONS,
    ): List<GalleryArtistLink> {
        val picks = ArrayList<GalleryArtistLink>(3)
        pixivPick(urls)?.let { picks.add(it) }
        twitterPick(urls)?.let { picks.add(it) }
        fanboxPick(urls)?.let { picks.add(it) }
        addIfRoom(picks, fromSource(source), maxIcons)
        if (picks.none { it.platform == GalleryArtistLinkPlatform.PIXIV }) {
            artworkAuthorUrl?.takeIf { pixivUserId(it) != null }?.let { authorUrl ->
                addIfRoom(
                    picks,
                    GalleryArtistLink(GalleryArtistLinkPlatform.PIXIV, authorUrl, artworkAuthorAccount),
                    maxIcons,
                )
            }
        }
        return picks.take(maxIcons)
    }

    private fun addIfRoom(
        picks: MutableList<GalleryArtistLink>,
        candidate: GalleryArtistLink?,
        maxIcons: Int,
    ) {
        if (candidate == null || picks.size >= maxIcons) return
        if (picks.any { it.platform == candidate.platform }) return
        picks.add(candidate)
    }

    /**
     * 从**这条帖子自带的出处**（[source]）认出一个"这个人"的地址（批次 L · L9）。
     *
     * 为什么需要它：Gelbooru 侧没有任何匿名可读的画师外链端点，唯一供体 Danbooru 当天卡盾，
     * 而出处那一栏实测覆盖 18/20，且是站方逐条记在帖子上的。
     *
     * 只认**指向人**的形状：`pixiv.net/member.php?id=N` 与 `/users/N`（直接给号）、
     * 推特/ X 的 `/<handle>/status/N`（账号段就是发帖人，规范化到主页）、
     * `<子域>.fanbox.cc/…`（子域就是创作者，规范化到创作者页）、`pixiv.me/xxx` 短链。
     *
     * 明确不认的：`/artworks/N` 与 `member_illust.php?illust_id=N`（**作品号不是用户号**，
     * 拿它去要头像就是把别人的脸挂到这位名下）、`i.pximg.net` 的裸图、dlsite / mega / bilibili
     * 那些与"这位是谁"无关的出处。认不出回 null，宁缺。
     */
    fun fromSource(source: String?): GalleryArtistLink? {
        val url = source?.trim().orEmpty()
        if (url.isEmpty()) return null
        val host = hostOf(url)
        if (host.isEmpty()) return null

        if (host.endsWith(".fanbox.cc")) {
            val creator = host.removeSuffix(".fanbox.cc")
            // www 那一档不是子域创作者（`www.pixiv.net/fanbox/creator/N` 走的是另一条形状，认不出）。
            return if (creator.isEmpty() || creator == "www") null
            else GalleryArtistLink(GalleryArtistLinkPlatform.FANBOX, "https://$creator.fanbox.cc/")
        }
        if (isTwitterHost(host)) {
            val handle = twitterHandle(url) ?: return null
            return GalleryArtistLink(GalleryArtistLinkPlatform.TWITTER, "https://x.com/$handle")
        }
        if (isUnder(host, "pixiv.me")) return GalleryArtistLink(GalleryArtistLinkPlatform.PIXIV, url)
        if (isUnder(host, "pixiv.net") && pixivUserId(url) != null) {
            return GalleryArtistLink(GalleryArtistLinkPlatform.PIXIV, url)
        }
        return null
    }

    private fun pixivPick(urls: List<String>): GalleryArtistLink? {
        val pixivs = urls.mapNotNull { url ->
            val trimmed = url.trim()
            GalleryArtistLinkPlatform.PIXIV.takeIf { platformOf(trimmed) == GalleryArtistLinkPlatform.PIXIV }?.let { trimmed }
        }
        if (pixivs.isEmpty()) return null
        val preferred = pixivs.firstOrNull { pixivUserId(it) != null } ?: pixivs.first()
        return GalleryArtistLink(GalleryArtistLinkPlatform.PIXIV, preferred)
    }

    private fun twitterPick(urls: List<String>): GalleryArtistLink? {
        val seenHandles = HashSet<String>()
        for (url in urls) {
            val trimmed = url.trim()
            if (platformOf(trimmed) != GalleryArtistLinkPlatform.TWITTER) continue
            val handle = twitterHandle(trimmed)?.lowercase() ?: continue
            if (!seenHandles.add(handle)) continue
            return GalleryArtistLink(GalleryArtistLinkPlatform.TWITTER, trimmed)
        }
        return null
    }

    private fun fanboxPick(urls: List<String>): GalleryArtistLink? = urls
        .map { it.trim() }
        .firstOrNull { platformOf(it) == GalleryArtistLinkPlatform.FANBOX }
        ?.let { GalleryArtistLink(GalleryArtistLinkPlatform.FANBOX, it) }

    private fun isTwitterHost(host: String): Boolean =
        isUnder(host, "twitter.com") || isUnder(host, "x.com")

    /** 域名"在这家名下"= 恰好等于或以 `.` 为界的上级。裸 `endsWith("pixiv.net")` 会认下 `pixiv.net.evil.com`。 */
    private fun isUnder(host: String, domain: String): Boolean =
        host == domain || host.endsWith(".$domain")

    private fun isPixivImageMirror(host: String): Boolean {
        val sub = host.removeSuffix(".pixiv.net").substringBefore('.')
        if (sub.isEmpty()) return false
        if (sub == "www" || sub == "sketch" || sub == "accounts" || sub == "factory") return false
        return sub.matches(PIXIV_IMAGE_ONLY_SUBDOMAINS)
    }

    /**
     * host（小写、去端口、去 userinfo）。认不出 scheme 时退回"整串当 host"的粗读法。
     *
     * `substringBefore('/')` **用 Kotlin 的默认值**（无分隔符时交回整串），不写 `""`：
     * `https://mochida.tumblr.com` 这种只有 authority、没有路径段的地址是站方真给的形态
     * （批次 Q+R 的 tumblr 子域那条），给了 `""` 会让 authority 整个读空、
     * 于是这位本人的 blog 被静默判成 OTHER —— 屏上就是"少一枚胶囊"，谁也不会来报。
     */
    private fun hostOf(url: String): String {
        val afterScheme = url.substringAfter("://", url)
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val withoutCreds = authority.substringAfterLast('@', "")
        val host = (withoutCreds.ifBlank { authority }).substringBefore(':').lowercase()
        return if (url.contains("://") || host.contains('.')) host else ""
    }

    /** 路径（小写）：平台判定只看形状，大小写无关。 */
    private fun pathOf(url: String): String = rawPathOf(url).lowercase()

    /** 路径原样：handle 是要拿去摆屏的，`CheLA_777` 压成小写就把人叫错了。 */
    private fun rawPathOf(url: String): String =
        url.substringAfter("://", url).substringAfter('/', "").substringBefore('?').substringBefore('#')

    private fun queryOf(url: String): String =
        url.substringAfter('/', "").substringAfter('?').substringBefore('#')
}
