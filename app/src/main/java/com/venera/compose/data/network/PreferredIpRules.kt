package com.venera.compose.data.network

import kotlin.random.Random

/**
 * Cloudflare 优选 IP 的**纯判据**：怎么读用户给的东西、怎么算"这台边缘节点今天能用"、
 * 按什么顺序用、什么时候必须退回正常解析。
 *
 * 这个文件刻意**不 import OkHttp、不 import Android** —— 判据要能脱离 gradle 单跑
 * （见 `_probe/l0/run-judgment-tests.sh`），而"真的连出去一次"那半在
 * [com.venera.compose.data.network.PreferredIpProbe]。
 *
 * ## 三条来自实测的硬口径（2026-10-01，本机直连）
 *
 * 1. **绑 IP 不绑域名会改变证书校验的对象吗？不会。** `curl --resolve safebooru.donmai.us:443:104.21.91.168`
 *    回 200 且 `ssl_verify_result=0` —— 连的是那台边缘节点，SNI / Host / 证书校验全都仍按原域名走。
 *    OkHttp 侧对应"自定义 `Dns` 返回 IP"这一条官方推荐路子，不需要自造 `SocketFactory`。
 * 2. **TCP 通 ≠ 能用。** 同一个 `safebooru.donmai.us`，`108.162.193.171` 曾稳定回 **403**、
 *    另三台回 200。⚠️ 但 403 是 CF 防火墙行为，**会随时间与 UA 漂**：2026-10-01 晚间复测，
 *    同一台边缘对 `konachan.net` 已回 200（当天早些时候恒 403）。所以探活**必须发真实业务
 *    请求并看状态码**，只看"连上了"会把 403 那台当好节点用；而 verdict 对 403 的保守判法
 *    （一律不算通）不变 —— 宁可这台这轮不参与，也不把被防火墙挡的节点当健康。
 * 3. **延迟差是真的**：同一域名同一时刻四台节点 0.41s / 0.62s / 0.64s / 0.66s —— 排序有意义，
 *    但不值得为它牺牲可用性，所以**健康的排前面，全失败必须退回系统解析**。
 *
 * ## 为什么默认关闭、且关闭时一行行为都不变
 *
 * 未开启时 [PreferredIpRules.plan] 对任何域名都交 `SystemDns`，而 `SystemDns` 就是
 * OkHttp 原本的 `Dns.SYSTEM` —— 挂上去的这层等于没挂。
 */
internal object PreferredIpRules {

    /** 一站最多认多少台候选。再多就是用户自己粘着玩，我们不替他攒。 */
    const val MAX_IPS_PER_HOST = 12

    /** 探活结果多久算过期（过期后首次用到该域名会在后台重探，不挡当前这一笔）。 */
    const val PROBE_FRESH_MS = 30 * 60 * 1000L

    /** 一次连接失败后，该域名的优选档停用多久 —— 这段时间走系统解析。 */
    const val DEMOTE_MS = 60 * 1000L

    /**
     * 单台候选的连接 + 读超时（毫秒）。
     *
     * 放在判据层是因为 [verdict] 那句"超时"要说得出秒数：文案与实际超时是两个地方写的常数，
     * 迟早会漂成"屏上写 10 秒、实际 5 秒"。探活那侧（`PreferredIpProbe`）用同一个常量建客户端。
     */
    const val PROBE_TIMEOUT_MS = 5_000L

    /**
     * 已实测过真实业务路径的域名（其余适用域名用根路径）。
     *
     * ⚠️ 只列**实测回 200** 的两条：
     * - `safebooru.donmai.us/posts.json?limit=1` → 200（JSON）
     * - `cdn-msp.jmapinodeudzn.net/`（JM 图床）→ 200，正文是节点标识串
     * 反面例子也在：`cdn.donmai.us`（图片 CDN）根路径实测 **403** —— 图片站的根路径不是
     * 有效业务端点，不能拿它当判据，所以它不进默认档。
     */
    val DEFAULT_TARGETS: List<PreferredIpTarget> = listOf(
        PreferredIpTarget("safebooru.donmai.us", "/posts.json?limit=1"),
        PreferredIpTarget("cdn-msp.jmapinodeudzn.net", "/"),
    )

    /**
     * 漫画源 → 该源 API 域名的探活目标。
     *
     * **域名逐字取自 assets/sources 下各源 JS 顶层声明的 API 域**（官方语义是唯一标准，
     * 不从 `ComicSource.url` 取 —— 那是源 JS 的更新仓库地址，不是站点 API）。
     * 只有「装了该源」才会进测速列表（见 [speedTestSources]），所以这里多备几条没有成本。
     *
     * 判据分两档：
     * - `require2xx = true`：有实测 200 业务端点的（jm 图床）；
     * - `require2xx = false`：只有 API 域名、没有实测业务路径的 —— 根路径对 API 站不是业务端点，
     *   探它问的是"这台边缘节点认不认这个 SNI"，所以非 5xx 应答（哔咔的 400 / 404 等）都算通。
     *   这与 [FALLBACK_PATH] 的哲学同源，只是把 require2xx 显式放开。
     *
     * 已知事实（2026-10-01 直连实测）：`picaapi.picacomic.com` 在 CF 后（TLS 握手成功、
     * 未签名请求回 400）；`nhentai.net` **不在 CF 后**（边缘 RST）—— 保留它是为了让测速页
     * 如实告诉你"这条线不通"，而不是假装这个源不存在。
     */
    val COMIC_SOURCE_API_TARGETS: Map<String, PreferredIpTarget> = mapOf(
        "picacg" to PreferredIpTarget("picaapi.picacomic.com", "/", require2xx = false),
        "jm" to PreferredIpTarget("cdn-msp.jmapinodeudzn.net", "/"),
        "copy_manga" to PreferredIpTarget("api.copy-manga.com", "/", require2xx = false),
        "baozi" to PreferredIpTarget("appcn.baozimh.com", "/", require2xx = false),
        "manga_dex" to PreferredIpTarget("api.mangadex.org", "/", require2xx = false),
        "nhentai" to PreferredIpTarget("nhentai.net", "/", require2xx = false),
        "ehentai" to PreferredIpTarget("api.e-hentai.org", "/", require2xx = false),
    )

    /**
     * 画廊站点 → 测速目标。key 是 [com.venera.compose.gallery.data.GallerySite] 的 routeKey
     * （判据层不 import 那边的类型，用字符串走），label 用**站方写法**（与 displayName 同源）。
     *
     * 三站的域名/路径全部来自画廊客户端本体的 BASE 常量：
     * - `yande.re` 根路径实测 200/301（都在 2xx..3xx 判通带内）；
     * - `safebooru.donmai.us/posts.json?limit=1` 实测 200（与 [DEFAULT_TARGETS] 同一条）；
     * - `gelbooru.com` 实测**不在 CF 后**（边缘 RST）—— 与 nhentai 同理，如实显示失败。
     */
    val GALLERY_SPEED_TEST_SOURCES: Map<String, PreferredIpSpeedTestSource> = mapOf(
        "yandere" to PreferredIpSpeedTestSource(
            "yandere", "yande.re", PreferredIpTarget("yande.re", "/"),
        ),
        "gelbooru" to PreferredIpSpeedTestSource(
            "gelbooru", "Gelbooru", PreferredIpTarget("gelbooru.com", "/"),
        ),
        "safebooru" to PreferredIpSpeedTestSource(
            "safebooru", "Safebooru",
            PreferredIpTarget("safebooru.donmai.us", "/posts.json?limit=1"),
        ),
    )

    /** 按域名精确查已知探活端点（默认表 → 漫画源表 → 图库表）。查不到返回 null。 */
    fun knownTargetFor(host: String): PreferredIpTarget? {
        val h = host.lowercase().trimEnd('.')
        if (h.isEmpty()) return null
        return DEFAULT_TARGETS.firstOrNull { it.host == h }
            ?: COMIC_SOURCE_API_TARGETS.values.firstOrNull { it.host == h }
            ?: GALLERY_SPEED_TEST_SOURCES.values.firstOrNull { it.target.host == h }?.target
    }

    /**
     * 推导**这一台设备**的测速目标列表 —— 目标跟着用户实际拥有的源走：
     * 装了才显示，没装不出现。
     *
     * @param installedComicSourceKeys 已安装**且启用**的漫画源 key（调用方已按
     *   `InstalledSourceMeta.enabled` 过滤；key 可能与 `copy_manga` 双版本重名，内部去重）。
     * @param comicSourceNames 源 key → 屏上显示名（来自 `InstalledSourceMeta.name`，
     *   即源 JS 自己声明的名字，如「哔咔漫画」「禁漫天堂」）。缺名时退回 key。
     * @param enabledGalleryRouteKeys 可用的画廊站点 routeKey（图库是内置功能恒可用；
     *   Gelbooru 没配账号时不传 —— 与 `GallerySearchSource.availableLegs` 同一判据）。
     * @param userHosts 用户在「适用域名」里手填的条目（已规范化）。已在本列表里的域名不重复；
     *   新条目按根路径判据追加，来源标为 custom。
     *
     * 顺序：漫画源（安装顺序）→ 图库站（站表序）→ 自定义域名。同域名只保留第一条。
     */
    fun speedTestSources(
        installedComicSourceKeys: List<String>,
        comicSourceNames: Map<String, String>,
        enabledGalleryRouteKeys: List<String>,
        userHosts: List<String>,
    ): List<PreferredIpSpeedTestSource> {
        // 去重的身份是**域名**不是 sourceKey：custom 条目的 key 带 `custom:` 前缀，
        // 拿 key 比对会把"手填了已在列表里的域名"这种重复全放进来。
        val out = LinkedHashMap<String, PreferredIpSpeedTestSource>()
        val seenHosts = LinkedHashSet<String>()
        fun putIfHostAbsent(source: PreferredIpSpeedTestSource) {
            val host = source.target.host.lowercase().trimEnd('.')
            if (host.isEmpty() || host in seenHosts) return
            seenHosts.add(host)
            out[source.sourceKey] = source
        }
        installedComicSourceKeys.distinct().forEach { key ->
            COMIC_SOURCE_API_TARGETS[key]?.let { target ->
                putIfHostAbsent(
                    PreferredIpSpeedTestSource(
                        sourceKey = key,
                        label = comicSourceNames[key]?.takeIf { it.isNotBlank() } ?: key,
                        target = target,
                    ),
                )
            }
        }
        enabledGalleryRouteKeys.forEach { routeKey ->
            GALLERY_SPEED_TEST_SOURCES[routeKey]?.let(::putIfHostAbsent)
        }
        userHosts.forEach { raw ->
            val host = raw.trim().lowercase().trimEnd('.')
            if (host.isNotBlank()) {
                putIfHostAbsent(
                    PreferredIpSpeedTestSource(
                        sourceKey = "custom:$host",
                        label = host,
                        target = PreferredIpTarget(host, FALLBACK_PATH),
                    ),
                )
            }
        }
        return out.values.toList()
    }

    /** 没实测过业务路径的域名，只能用根路径去问"这台节点认不认这个 SNI"。 */
    const val FALLBACK_PATH = "/"

    /**
     * Cloudflare 官方公布的全部 IPv4 网段（cloudflare.com/ips-v4，2026-10-01 抄录）。
     *
     * **自动选点**的抽样池：候选节点不该让用户自己满世界找 IP 来粘 ——
     * 系统从这些网段里抽样，拿「适用域名」的真实端点逐台探活，通过的按延迟排序填入。
     * 这份表是站方公开事实，不是猜的；Cloudflare 调整网段时按官方页更新。
     */
    val CLOUDFLARE_IPV4_RANGES: List<String> = listOf(
        "173.245.48.0/20",
        "103.21.244.0/22",
        "103.22.200.0/22",
        "103.31.4.0/22",
        "141.101.64.0/18",
        "108.162.192.0/18",
        "190.93.240.0/20",
        "188.114.96.0/20",
        "197.234.240.0/22",
        "198.41.128.0/17",
        "162.158.0.0/15",
        "104.16.0.0/13",
        "104.24.0.0/14",
        "172.64.0.0/13",
        "131.0.72.0/22",
    )

    /**
     * 自动选点一轮抽多少台。与 [MAX_IPS_PER_HOST] 同值：
     * runtime 配置按 [parseIps] 的上限裁剪，抽多了也只探得进前 12 台。
     */
    const val AUTO_SAMPLE_COUNT = MAX_IPS_PER_HOST

    /** 解析一个 IPv4 CIDR（`104.16.0.0/13`）：返回对齐后的网络基址（0..2^32-1）与前缀长度。 */
    fun parseCidr4(cidr: String): Pair<Long, Int>? {
        val slash = cidr.indexOf('/')
        if (slash <= 0) return null
        val octets = normalizeIp(cidr.substring(0, slash))?.split('.')?.map { it.toLong() } ?: return null
        val prefix = cidr.substring(slash + 1).trim().toIntOrNull() ?: return null
        if (prefix !in 0..32) return null
        val value = (octets[0] shl 24) or (octets[1] shl 16) or (octets[2] shl 8) or octets[3]
        val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        return (value and mask) to prefix
    }

    /** 32 位整数 → 点分十进制 IPv4 字面量。 */
    fun ipv4FromLong(value: Long): String =
        "${(value ushr 24) and 0xFF}.${(value ushr 16) and 0xFF}.${(value ushr 8) and 0xFF}.${value and 0xFF}"

    /**
     * 从一个 CIDR 网段里随机抽 [count] 个地址（去重；网段比 count 还小时有多少抽多少）。
     *
     * 随机源从外面进：单测用固定种子钉住行为，UI 侧用默认随机。
     */
    fun sampleCidr4(cidr: String, count: Int, random: Random): List<String> {
        val (base, prefix) = parseCidr4(cidr) ?: return emptyList()
        val size = 1L shl (32 - prefix)
        val out = LinkedHashSet<String>()
        var guard = 0
        while (out.size < count.coerceAtMost(size.toInt()) && guard < count * 20 + 20) {
            guard++
            val offset = (random.nextDouble() * size).toLong().coerceIn(0L, size - 1L)
            out.add(ipv4FromLong(base + offset))
        }
        return out.toList()
    }

    /**
     * 自动选点的抽样：从官方网段里随机抽 [count] 个**去重**的 IPv4 字面量。
     *
     * 只保证"地址属于 Cloudflare"，不保证"这台今天对某个域名可用" —— 后者只能靠真实探活，
     * 抽样与探活是两步，谁也不能替谁。
     */
    fun sampleCloudflareIps(count: Int = AUTO_SAMPLE_COUNT, random: Random = Random.Default): List<String> {
        if (count <= 0 || CLOUDFLARE_IPV4_RANGES.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        var guard = 0
        while (out.size < count && guard < count * 20 + 20) {
            guard++
            val cidr = CLOUDFLARE_IPV4_RANGES[random.nextInt(CLOUDFLARE_IPV4_RANGES.size)]
            sampleCidr4(cidr, 1, random).firstOrNull()?.let(out::add)
        }
        return out.toList()
    }

    /** 用户粘了多少项（不管认不认得出），UI 用它说"识别出 N 台（共 M 项）"。 */
    fun countIpTokens(raw: String): Int = raw.split('\n', '\r', ',', ';', '\t', ' ')
        .count { it.isNotBlank() }

    /**
     * 解析用户粘进来的一串 IP：换行 / 逗号 / 分号 / 空格都算分隔符，顺序保留、重复去掉。
     *
     * **认不出的直接丢，不报错也不猜**：粘进一个域名或一个 `1.2.3.4:5678` 时，
     * 把它当 IP 用会让该域名的每一笔请求都连错地方；丢掉只是"这一台不参与"。
     * 丢掉了几台由 UI 那侧的"识别出 N 台（共 M 项）"说出来，不静默。
     */
    fun parseIps(raw: String, limit: Int = MAX_IPS_PER_HOST): List<String> {
        val out = LinkedHashSet<String>()
        raw.split('\n', '\r', ',', ';', '\t', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { token ->
                normalizeIp(token)?.let { if (out.size < limit) out.add(it) }
            }
        return out.toList()
    }

    /** 规范化一个 IP 字面量；不是合法 IP 时返回 null。 */
    fun normalizeIp(token: String): String? {
        var s = token.trim()
        // 用户从浏览器/工具里抄 IPv6 时可能带方括号（URL 里必须带），这里去掉再判。
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length - 1)
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        return if (s.contains(':')) {
            if (isIpv6Literal(s)) s.lowercase() else null
        } else if (isIpv4Literal(s)) {
            s
        } else {
            null
        }
    }

    /** 严格四段点分十进制，每段 0~255 且不留前导零歧义（`01.2.3.4` 拒掉）。 */
    private fun isIpv4Literal(s: String): Boolean {
        val parts = s.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            part.isNotEmpty() &&
                part.length <= 3 &&
                part.all { it.isDigit() } &&
                (part.length == 1 || part[0] != '0') &&
                part.toInt() in 0..255
        }
    }

    /** IPv6 的一个 16 位组：1~4 位十六进制。 */
    private val ipv6Group = Regex("^[0-9a-fA-F]{1,4}$")

    /** IPv6 字面量：只接受标准冒号分段形式，带 zone id（`%eth0`）与压缩外的写法都拒。 */
    private fun isIpv6Literal(s: String): Boolean {
        if (s.contains('%') || s.contains('[') || s.contains(']') || s.contains(":::")) return false
        if (s.any { c -> (c != ':' && c != '.' && !c.isLetterOrDigit()) }) return false
        val compression = s.split("::")
        if (compression.size > 2) return false
        val hasCompression = compression.size == 2
        if (!hasCompression && (s.startsWith(":") || s.endsWith(":"))) return false
        val joined = if (hasCompression) compression[0] + ":" + compression[1] else s
        val pieces = joined.split(':').filter { it.isNotEmpty() }
        var groups = 0
        for (piece in pieces) {
            if (piece.contains('.')) {
                // 末段可以是点分四段（`64:ff9b::192.0.2.33`），它占两个 16 位组。
                if (!isIpv4Literal(piece)) return false
                if (piece != pieces.last()) return false
                groups += 2
            } else {
                if (!piece.matches(ipv6Group)) return false
                groups += 1
            }
        }
        // `::` 至少替掉一组，所以带压缩时显式组数最多 7；不带压缩必须正好 8 组。
        return if (hasCompression) groups in 0..7 else groups == 8
    }

    /**
     * 解析"适用域名"表：与 IP 同一套分隔符，去 scheme / 端口 / 大小写 / 尾点。
     *
     * 丢掉的两种：**IP 字面量**（那是解析结果而不是域名，绑上去等于把 IP 当域名解析）
     * 和**空白**。
     */
    fun parseHosts(raw: String): List<String> {
        val out = LinkedHashSet<String>()
        raw.split('\n', '\r', ',', ';', '\t', ' ')
            .mapNotNull { normalizeHost(it) }
            .forEach { out.add(it) }
        return out.toList()
    }

    /** 规范化一个域名条目；不是域名（含 IP 字面量）时返回 null。 */
    fun normalizeHost(token: String): String? {
        var s = token.trim().lowercase()
        if (s.isEmpty()) return null
        if ('/' in s) s = s.substringAfter("//").substringBefore('/')
        if (s.contains('@')) return null
        if (s.contains(':')) s = s.substringBefore(':')
        s = s.trimEnd('.')
        if (s.isEmpty()) return null
        if (normalizeIp(s) != null) return null
        val labels = s.split('.')
        if (labels.any { it.isEmpty() || it.any { c -> !(c.isLetterOrDigit() || c == '-') } }) return null
        return s
    }

    /**
     * 这一条请求的域名归不归这份优选 IP 管。
     *
     * 精确相等，或**作为后缀落在条目之内**（条目 `example.com` 管 `api.example.com`）。
     * 反过来不行：条目 `example.com` 不该管 `notexample.com`，所以比对时要带上前导点。
     */
    fun covers(host: String, entry: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        val e = entry.lowercase().trimEnd('.')
        if (h.isEmpty() || e.isEmpty()) return false
        return h == e || h.endsWith(".$e")
    }

    /** 在条目表里挑出**最长**的那条匹配（多条都盖住时，最具体的赢）。 */
    fun matchEntry(host: String, entries: List<String>): String? =
        entries.filter { covers(host, it) }.maxByOrNull { it.length }

    /**
     * 一次探活的结论：**null = 这台节点对这个域名可用**，否则是一句能摆到屏上的失败原因。
     *
     * 判据按 [PreferredIpTarget.require2xx] 分两档，但**403 一律算失败**：
     * 实测 403 是"这台 CF 节点不服务这个 hostname"那一类（也是 konachan.net 的挡法），
     * 拿它当"通了"就是给自己造一个整站不可用的开关。
     *
     * [PreferredIpError.RESET] 与 [PreferredIpError.CONNECT] 必须分开说：
     * RESET 是 **TCP 通了、TLS 握手带着这个域名的明文 SNI 出去就被重置** —— 2026-10-01 真机测速
     * 证实（同一台节点 Safebooru 1069 ms 通、yande.re 报"443 被拒"），这是**按域名阻断**的形态，
     * 换哪台节点都一样（优选 IP 只换 IP，换不掉明文 SNI）；把它写成"节点不可达"会引导用户
     * 以为换台节点还有救，其实解法只有开代理。
     */
    fun verdict(reading: PreferredIpReading, target: PreferredIpTarget): String? = when {
        reading.error == PreferredIpError.UNKNOWN_HOST -> "连不上：这台地址解析不出路由"
        reading.error == PreferredIpError.CONNECT -> "连不上：TCP 443 被拒或不可达"
        reading.error == PreferredIpError.RESET ->
            "连接被重置：该域名在你的网络多半被按域名阻断，优选 IP 绕不过，建议开代理"
        reading.error == PreferredIpError.TIMEOUT -> "超时：${PROBE_TIMEOUT_MS / 1000} 秒内没连上或没读完"
        reading.error == PreferredIpError.TLS -> "TLS 失败：这台节点没给出该域名的证书"
        reading.status == null -> "没拿到 HTTP 响应"
        reading.status == 403 -> "HTTP 403：这台边缘节点不服务该域名"
        reading.status in 200..399 -> null
        target.require2xx -> "HTTP ${reading.status}：这一档不是业务上的答上了"
        reading.status < 500 -> null
        else -> "HTTP ${reading.status}"
    }

    /**
     * 该域名这一笔该走哪条路。
     *
     * 三种情形全部退回系统解析，一条都不硬撑：
     * 1. 用户没开启；
     * 2. 这个域名不在适用表里（**别的站一律不受影响**）；
     * 3. 命中的条目要么没有通过的候选、要么整条刚连失败过一次。
     */
    fun plan(host: String, snapshot: PreferredIpSnapshot, nowMs: Long): PreferredIpPlan {
        if (!snapshot.enabled) return PreferredIpPlan.SystemDns
        val entry = matchEntry(host, snapshot.entries) ?: return PreferredIpPlan.SystemDns
        if (snapshot.demotedByEntry[entry].orZero() > nowMs) return PreferredIpPlan.SystemDns
        val healthy = healthyIps(snapshot.statesByEntry[entry].orEmpty(), nowMs)
        if (healthy.isEmpty()) return PreferredIpPlan.SystemDns
        return PreferredIpPlan.Preferred(entry, healthy)
    }

    /**
     * 健康候选：**这一轮探活通过**、且结果还没过期的那些，按延迟升序。
     *
     * 两条都不满足的一律不出列 —— 特别是"探活失败的那台不会到点自动算健康"：
     * 它要等下一轮真实探活翻身。否则观察窗一过，一台稳定回 403 的节点就又混进候选列表里，
     * 等于自己把红线还回去。
     *
     * 延迟相等时按 IP 串定序，避免同一次解析在不同调用间给出不同顺序（那会让连接池反复新建连接）。
     */
    fun healthyIps(states: Map<String, PreferredIpState>, nowMs: Long): List<String> =
        states.filterValues { it.passed && !probeStale(it.probedAt, nowMs) }
            .entries.sortedWith(compareBy({ it.value.latencyMs }, { it.key }))
            .map { it.key }

    /** 距离"可以再试一次优选 IP"还剩多少毫秒（0 = 现在就能用）。 */
    fun demoteRemainingMs(snapshot: PreferredIpSnapshot, entry: String, nowMs: Long): Long =
        (snapshot.demotedByEntry[entry].orZero() - nowMs).coerceAtLeast(0L)

    /** 探活结果多久算过期。 */
    fun probeStale(probedAt: Long, nowMs: Long): Boolean = nowMs - probedAt > PROBE_FRESH_MS

    /**
     * 把「条目 → 各候选读数」折成「候选 IP → 一条线」，并按约定排序。
     *
     * 排序（从前往后就是屏上从上到下）：**可用优先 → 全过优先 → 最慢延迟升序 → IP**。
     * - 可用优先：至少过了一个域名的排在前，一个都没过的（红）沉底；
     * - 全过优先：能替这组域名全勤的排在有短板的之前；
     * - 延迟升序：用**最慢**那台的延迟代表这条线（保守，避免「某站快某站慢」被平均骗过去）；
     * - IP：同档同延迟时定序，避免每次重组顺序乱跳（否则连接池会反复新建连接）。
     *
     * 纯函数（与 [verdict] / [plan] 同一条纪律：时间 / Android / OkHttp 一律不进），
     * 所以能脱离 gradle 在 `_probe/l0` 里单跑。输入类型 [PreferredIpLineProbe] 也是判据层自有，
     * 不依赖 [PreferredIpProbe.Row]（那一侧 import 了 OkHttp）。
     */
    fun summarizeLineStates(
        perEntryRows: Map<String, List<PreferredIpLineProbe>>,
    ): List<PreferredIpLine> {
        val byIp = LinkedHashMap<String, MutableList<PreferredIpLineEntry>>()
        perEntryRows.forEach { (entry, rows) ->
            rows.forEach { r ->
                // 条目名从外层 map 的键来：读数本身只带 IP，不重复塞 entry。
                byIp.getOrPut(r.ip) { mutableListOf() }.add(
                    PreferredIpLineEntry(entry = entry, passed = r.passed, latencyMs = r.latencyMs, detail = r.detail),
                )
            }
        }
        return byIp.map { (ip, entries) ->
            val total = entries.size
            val passed = entries.count { it.passed }
            val slowest = entries.maxOf { it.latencyMs }
            val status = when {
                passed == 0 -> PreferredIpLineStatus.Failing
                passed == total && slowest < LINE_HEALTHY_LATENCY_MS -> PreferredIpLineStatus.Healthy
                else -> PreferredIpLineStatus.Degraded
            }
            PreferredIpLine(
                ip = ip,
                totalEntries = total,
                passedEntries = passed,
                slowestLatencyMs = slowest,
                status = status,
                failureReason = entries.firstOrNull { !it.passed }?.detail,
                entries = entries,
            )
        }.sortedWith(
            compareBy(
                { it.passedEntries == 0 },                     // 可用优先（过了的排前面）
                { it.passedEntries != it.totalEntries },        // 全过优先（有短板的往后）
                { it.slowestLatencyMs },                       // 最慢延迟升序
                { it.ip },                                     // 同档定序
            ),
        )
    }

    private fun Long?.orZero() = this ?: 0L
}

/**
 * 判据要看的那份快照：配置 + 探活结果 + 观察窗。
 *
 * 抽出来是为了让 [PreferredIpRules.plan] 是个纯函数 —— 时间从外面进来，
 * 单测才能钉住"过了观察窗要自己翻身"这一类只靠时钟成立的行为。
 */
internal data class PreferredIpSnapshot(
    val enabled: Boolean = false,
    /** 用户配的适用域名条目（已规范化）。 */
    val entries: List<String> = emptyList(),
    /** 条目 → 候选 IP → 状态。没探过的条目在这里根本没有键。 */
    val statesByEntry: Map<String, Map<String, PreferredIpState>> = emptyMap(),
    /** 条目 → 观察窗截止时间（连失败过一次之后，这一条整体暂停用）。 */
    val demotedByEntry: Map<String, Long> = emptyMap(),
)

/** 一个适用域名的探活目标：拿这条**真实业务路径**去问候选节点。 */
internal data class PreferredIpTarget(
    val host: String,
    val path: String,
    /** true = 只有 2xx/3xx 算通；false = 只要边缘节点给出任何非 5xx 应答就算通。 */
    val require2xx: Boolean = true,
)

/**
 * 测速列表里的一条目标：**它来自哪个源、屏上叫什么、拿哪条端点去探**。
 *
 * [sourceKey] 是来源身份：漫画源 key（`picacg`/`jm`…）、画廊 routeKey（`yandere`…）、
 * 或用户手填的 `custom:<host>`。[label] 是源自己声明的显示名（漫画源取 `InstalledSourceMeta.name`，
 * 图库用站方写法），测速页把它当主标题 —— 用户看到的是「装了的那个源」，不是一串裸域名。
 */
internal data class PreferredIpSpeedTestSource(
    val sourceKey: String,
    val label: String,
    val target: PreferredIpTarget,
)

/** 一次探活的原始读数。[status] 与 [error] 恰好一个是 null。 */
internal data class PreferredIpReading(
    val ip: String,
    val status: Int? = null,
    val error: PreferredIpError? = null,
    val latencyMs: Long = 0L,
)

/** 探活失败的形态分类 —— UI 上要说清是"连不上"还是"连上了但站方不认"。 */
internal enum class PreferredIpError { TIMEOUT, TLS, CONNECT, RESET, UNKNOWN_HOST }

/**
 * 一台候选节点的一轮探活读数。
 *
 * [passed] 与 [probedAt] 一起决定它今天能不能被选中（见 [PreferredIpRules.healthyIps]）；
 * 这套状态刻意**独立于** [com.venera.compose.data.network.HostCircuitBreaker] 的计数 ——
 * 那套管的是"这个源真死了要不要快败"，这套管的是"这台边缘节点今天能不能替这个域名服务"，
 * 混用会互相把对方的计数污染（一次图片超时就把整站拉黑 60 秒那种事不能再来一遍）。
 */
internal data class PreferredIpState(
    val latencyMs: Long = 0L,
    val passed: Boolean = false,
    val probedAt: Long = 0L,
    /** 直接能摆到屏上的那句话：通过时是"200 · 412 ms"，失败时是失败原因。 */
    val detail: String = "",
)

/** 一笔请求的解析路线。 */
internal sealed interface PreferredIpPlan {
    /** 走系统解析：没开启 / 不在适用表里 / 候选全不可用。 */
    data object SystemDns : PreferredIpPlan

    /** 走优选：[entry] 是命中的条目，[ips] 已按延迟排好。 */
    data class Preferred(val entry: String, val ips: List<String>) : PreferredIpPlan
}

// ─────────────────────────────────────────────────────────────────────────────
// 线路测速页的「一条线」聚合（与 [verdict] / [plan] 同一条纪律：时间 / Android / OkHttp 一律不进，
// 所以能脱离 gradle 在 `_probe/l0` 里单跑）。
//
// 探活是「条目 × 候选」逐台打的（见 [PreferredIpProbe]），但测速页要给用户看的是
// **「这台节点今天整体行不行」**：它要穿过所有适用域名才算可用，只过一两个不算。
// ─────────────────────────────────────────────────────────────────────────────

/** 一条测速读数的最小单元（判据层自有，不依赖 [PreferredIpProbe.Row]：那一侧 import 了 OkHttp）。 */
internal data class PreferredIpLineProbe(
    val ip: String,
    val passed: Boolean,
    val latencyMs: Long,
    val detail: String,
)

/** 一条线的健康度（对应屏上的色点；测过的节点没有「未知」这一档）。 */
internal enum class PreferredIpLineStatus { Healthy, Degraded, Failing }

/** 一台候选节点在一台域名下的结果（屏上逐域名摆）。 */
internal data class PreferredIpLineEntry(
    val entry: String,
    val passed: Boolean,
    val latencyMs: Long,
    val detail: String,
)

/**
 * 一台候选节点折完所有适用域名之后的一条线。
 *
 * [status] 的判法：
 * - 一个域名都没过 → [Failing]（红）「这台节点今天对这组域名没一个答上话」；
 * - 全过且最慢 < [LINE_HEALTHY_LATENCY_MS] → [Healthy]（绿）；
 * - 全过但最慢偏慢，或只过了一部分 → [Degraded]（黄）：能用但不稳 / 不快。
 */
internal data class PreferredIpLine(
    val ip: String,
    val totalEntries: Int,
    val passedEntries: Int,
    val slowestLatencyMs: Long,
    val status: PreferredIpLineStatus,
    /** 失败条目里的第一句失败原因（摆「为什么不通」用；全过时 null）。 */
    val failureReason: String?,
    val entries: List<PreferredIpLineEntry>,
)

/** 一条线算「健康」的延迟上限（ms）。与 [StatusColors.Healthy] 的口径「< 400ms」逐字一致。 */
internal const val LINE_HEALTHY_LATENCY_MS = 400L
