package com.venera.compose.gallery.domain

/** 探针端点是**哪一家**派生出来的 —— 取数层照它选解析器，不靠嗅 host。 */
enum class GalleryAvatarEndpointKind { FANBOX, MASTODON }

/**
 * 探针派生出来的一个公开头像端点。
 *
 * [origin] 不是装饰：**fanbox 那台 api 只认带 `Origin` 的请求**（2026-09-30 14:05 逐档实测，
 * 不带 / 只带 Referer 一律 400 `general_error`），而 Mastodon 那边匿名直取就 200、所以留 null。
 * 取数层照这一字段决定带不带请求头 —— 探针只给地址就是给不出可用的东西。
 */
data class GalleryAvatarProbeEndpoint(
    val endpoint: String,
    val origin: String?,
    val kind: GalleryAvatarEndpointKind,
)

/**
 * 从出处地址派生**公开、无需凭据**的头像端点（批次 L · L9）。
 *
 * 只有两条，而且是今天逐条量过才留下的（2026-09-30 下午，本机代理出口）：
 * - **fanbox**：`https://api.fanbox.cc/creator.get?creatorId=<子域>` 带 `Origin` 时回 200，
 *   `body.user.iconUrl` 是 160×160（那台图床实测不挡防盗链，`pixiv.pximg.net`）。
 *   注意 `creatorId` 要的是**子域那一段**（`setmen`），不是 pixiv 用户号 —— 同一端点传数字号实测回 400。
 * - **Mastodon 系**（baraag / pawoo）：`https://<实例>/api/v1/accounts/lookup?acct=<handle>` 回 200 带 `avatar`。
 *
 * 明确**不做**的：X / Twitter 没有匿名头像路（站方 og:image 是占位图，公开代理 unavatar 回的是一张
 * 568 字节的占位 SVG，"有值"但根本不是脸 —— 摆上屏就是拿一张通用图冒充这位）。
 *
 * ⚠️ 这里原来还写着一句"pixiv 也没法从作品号反查作者"，**已被 L10 推翻**：当时只量了
 * `/ajax/user/<作品号>`（确实 `error=true`），没量 `/ajax/illust/<作品号>` —— 后者匿名可读，
 * 回真用户号与账号（见 `PixivClient.artworkAuthor`）。留着这句是提醒后人：
 * **判"做不到"要量掉所有候选端点，不是量掉最像的那一个。**
 * 真取不到就是取不到，面板那一格退回首字母座，不硬凑。
 *
 * 实例用**白名单**而不是"任何 Mastodon 域名"：派生出来的地址会被 app 直接发出去，而 OkHttp 那侧的
 * CookieJar 按 host 共享 —— 拿一条来路不明的 host 去请求，等于把该 host 上攒下的 cookie 一并带出去。
 * 目前只放进量过的这两台，以后要加就得先量。
 */
object GalleryArtistAvatarProbe {

    private val MASTODON_INSTANCES = setOf("baraag.net", "pawoo.net")

    /** fanbox 子域（创作者 id）的形状：字母数字 + `-` `_`，1–63 位。不合形状的不派生。 */
    private val FANBOX_CREATOR = Regex("^[A-Za-z0-9_-]{1,63}$")

    private val MASTODON_HANDLE = Regex("^[A-Za-z0-9._-]{1,64}$")

    /**
     * [sourceUrl] 是 fanbox 的创作者页（或其下任意一页）时，返回取头像的公开端点；否则 null。
     * 只认 `https` 的子域写法（`<子域>.fanbox.cc`）—— `www.pixiv.net/fanbox/creator/N` 那一条
     * 里的 N 是数字号，而数字号在这台端点上实测 400，所以整条不回。
     */
    fun fanboxCreator(sourceUrl: String): GalleryAvatarProbeEndpoint? {
        val (scheme, host, _) = parts(sourceUrl) ?: return null
        if (scheme != "https") return null
        if (!host.endsWith(".fanbox.cc")) return null
        val creator = host.removeSuffix(".fanbox.cc")
        if (creator == "www" || !creator.matches(FANBOX_CREATOR)) return null
        return GalleryAvatarProbeEndpoint(
            endpoint = "https://api.fanbox.cc/creator.get?creatorId=$creator",
            origin = "https://$creator.fanbox.cc",
            kind = GalleryAvatarEndpointKind.FANBOX,
        )
    }

    /**
     * [sourceUrl] 在白名单实例上、且路径带 handle 时，返回 `accounts/lookup` 端点；否则 null。
     * handle 保持原样大小写（`@BittyCat` 压小写会查错人）。状态页 `/@handle/<id>` 取第一段。
     *
     * **必须带 `@` 那一段**：Mastodon 的实例上 `/about`、`/explore`、`/auth/login` 这些路径同样是首段，
     * 少了这道门就会把站方页面名当成账号去查，而 `acct=about` 在那台实例上真有账号时就是**认错人**。
     */
    fun mastodonLookup(sourceUrl: String): GalleryAvatarProbeEndpoint? {
        val (scheme, host, path) = parts(sourceUrl) ?: return null
        if (scheme != "https") return null
        if (host !in MASTODON_INSTANCES) return null
        val first = path.substringBefore('/')
        if (!first.startsWith("@")) return null
        val handle = first.removePrefix("@")
        if (handle.isEmpty() || !handle.matches(MASTODON_HANDLE)) return null
        return GalleryAvatarProbeEndpoint(
            endpoint = "https://$host/api/v1/accounts/lookup?acct=$handle",
            origin = null,
            kind = GalleryAvatarEndpointKind.MASTODON,
        )
    }

    /**
     * scheme（小写）/ host（小写、去端口、去 userinfo）/ 路径（不含前导斜杠）。
     *
     * 手工切而不是 `java.net.URI`：fanbox 的子域**允许下划线**（`a-b_c.fanbox.cc` 是站方真发的形状），
     * 而 `URI.host` 按主机名字符集校验，遇到下划线整个返回 null —— 于是真出处被判成"认不出"。
     * 解析不了一律 null，不猜。
     */
    private fun parts(urlString: String): Triple<String, String, String>? {
        val url = urlString.trim()
        if (!url.contains("://")) return null
        val scheme = url.substringBefore("://").lowercase()
        if (scheme.isEmpty()) return null
        val rest = url.substringAfter("://")
        val authority = rest
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
            .substringBefore(':')
            .lowercase()
        if (!authority.contains('.')) return null
        val path = rest.substringAfter('/', "").substringBefore('?').substringBefore('#')
        return Triple(scheme, authority, path)
    }
}
