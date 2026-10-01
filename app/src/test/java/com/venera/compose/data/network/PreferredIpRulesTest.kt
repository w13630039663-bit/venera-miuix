package com.venera.compose.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cloudflare 优选 IP 的**判据**：怎么读用户粘的东西、什么算"这台节点今天能用"、
 * 按什么顺序用、什么时候必须退回正常解析。
 *
 * 三条设计线在这里各有专门用例，因为它们各自的失败形态都很贵：
 * 1. **不硬编码**：候选一律来自用户输入，认不出的当场丢但要说得出丢了几项；
 * 2. **回退优先**：没开 / 不在适用表 / 候选全不通 —— 三种情况都必须交回系统解析，
 *    绝不能出现"绑了优选 IP 反而整站不可用"；
 * 3. **不通不等于连上**：403 与 TLS 失败都算不通（实测 `108.162.193.171` 对
 *    `safebooru.donmai.us` 稳定 403，而它 TCP 是通的）。
 */
class PreferredIpRulesTest {

    // ── 候选地址怎么读 ──

    @Test
    fun `候选地址支持三种分隔符并去重保序`() {
        val ips = PreferredIpRules.parseIps("104.16.1.1\n104.16.1.2, 104.16.1.1;172.66.1.1")
        assertEquals(listOf("104.16.1.1", "104.16.1.2", "172.66.1.1"), ips)
    }

    @Test
    fun `认不出的项丢掉但项数要说得出来`() {
        val raw = "104.16.1.1 example.com 1.2.3 1.2.3.4:5678 01.2.3.4"
        assertEquals(listOf("104.16.1.1"), PreferredIpRules.parseIps(raw))
        // "识别出 1 台（共 5 项）"这句话的两个数都来自这里，不静默吞掉用户的粘贴。
        assertEquals(5, PreferredIpRules.countIpTokens(raw))
    }

    @Test
    fun `IPv6 认标准形式并去方括号，带 zone id 的拒掉`() {
        assertEquals("2606:4700:4700::1111", PreferredIpRules.normalizeIp("[2606:4700:4700::1111]"))
        assertEquals("2606:4700::1111", PreferredIpRules.normalizeIp("2606:4700::1111"))
        assertNull(PreferredIpRules.normalizeIp("fe80::1%eth0"))
        assertNull(PreferredIpRules.normalizeIp("2606:4700:::1111"))
        // 不带压缩时必须正好 8 组。
        assertNull(PreferredIpRules.normalizeIp("2606:4700:0:0:0:0:0"))
        assertEquals(
            "0:0:0:0:0:0:0:1",
            PreferredIpRules.normalizeIp("0:0:0:0:0:0:0:1"),
        )
        // 末段允许点分四段（它占两个 16 位组）。
        assertEquals("64:ff9b::192.0.2.33", PreferredIpRules.normalizeIp("64:ff9b::192.0.2.33"))
    }

    @Test
    fun `候选数量有上限`() {
        val many = (1..30).joinToString("\n") { "104.16.0.$it" }
        assertEquals(PreferredIpRules.MAX_IPS_PER_HOST, PreferredIpRules.parseIps(many).size)
    }

    // ── 适用域名表怎么读 ──

    @Test
    fun `域名条目去 scheme 与端口并归一大小写`() {
        val hosts = PreferredIpRules.parseHosts(
            "https://Safebooru.Donmai.Us/posts.json\ngelbooru.com:443\n\ndonmai.us.",
        )
        assertEquals(listOf("safebooru.donmai.us", "gelbooru.com", "donmai.us"), hosts)
    }

    @Test
    fun `域名表里不接受 IP 与带凭据的串`() {
        assertNull(PreferredIpRules.normalizeHost("104.16.1.1"))
        assertNull(PreferredIpRules.normalizeHost("user@evil.example.com"))
        assertNull(PreferredIpRules.normalizeHost("exa mple.com"))
    }

    @Test
    fun `子域算覆盖而形近域不算`() {
        assertTrue(PreferredIpRules.covers("safebooru.donmai.us", "donmai.us"))
        assertTrue(PreferredIpRules.covers("donmai.us", "donmai.us"))
        // 反向必须不成立：条目 donmai.us 不该把 notdonmai.us 也接管过去。
        assertTrue(!PreferredIpRules.covers("notdonmai.us", "donmai.us"))
    }

    @Test
    fun `多条都盖住时最具体的那条赢`() {
        val entries = listOf("donmai.us", "safebooru.donmai.us")
        assertEquals("safebooru.donmai.us", PreferredIpRules.matchEntry("safebooru.donmai.us", entries))
        // 不在表里的域名一律不接管 —— 别的站不许被这层影响。
        assertNull(PreferredIpRules.matchEntry("www.pixiv.net", entries))
    }

    // ── 一次探活算不算通 ──

    private val api = PreferredIpTarget("safebooru.donmai.us", "/posts.json?limit=1")

    @Test
    fun `200 通过而 403 明确不通过`() {
        assertNull(PreferredIpRules.verdict(PreferredIpReading("ip", status = 200), api))
        val rejected = PreferredIpRules.verdict(PreferredIpReading("ip", status = 403), api)
        assertTrue(rejected!!.contains("403"))
    }

    @Test
    fun `TCP 通但业务不通的三种形态各说各话`() {
        assertTrue(
            PreferredIpRules.verdict(
                PreferredIpReading("ip", error = PreferredIpError.TLS),
                api,
            )!!.contains("TLS"),
        )
        assertTrue(
            PreferredIpRules.verdict(
                PreferredIpReading("ip", error = PreferredIpError.CONNECT),
                api,
            )!!.contains("连不上"),
        )
        assertTrue(
            PreferredIpRules.verdict(
                PreferredIpReading("ip", error = PreferredIpError.TIMEOUT),
                api,
            )!!.contains("超时"),
        )
    }

    @Test
    fun `超时文案里的秒数与实际超时常数是同一个`() {
        // 文案写 10 秒、客户端实际 5 秒那种两处真相，这里一次钉住。
        val seconds = PreferredIpRules.verdict(PreferredIpReading("ip", error = PreferredIpError.TIMEOUT), api)!!
        assertTrue(seconds.contains("${PreferredIpRules.PROBE_TIMEOUT_MS / 1000} 秒"))
    }

    @Test
    fun `只认业务答上的档位`() {
        // 404 对 API 档不是"通了"：那台节点可能只是把请求转给了一个空目录。
        assertTrue(PreferredIpRules.verdict(PreferredIpReading("ip", status = 404), api) != null)
        val rootOnly = PreferredIpTarget("cdn-msp.jmapinodeudzn.net", "/", require2xx = false)
        assertNull(PreferredIpRules.verdict(PreferredIpReading("ip", status = 404), rootOnly))
        // 但 5xx 永远算不通：那是节点自己坏了。
        assertTrue(PreferredIpRules.verdict(PreferredIpReading("ip", status = 503), rootOnly) != null)
    }

    // ── 候选顺序与观察窗 ──

    private fun state(latency: Long, passed: Boolean = true, probedAt: Long = 0L) =
        PreferredIpState(latencyMs = latency, passed = passed, probedAt = probedAt, detail = "$latency ms")

    @Test
    fun `健康候选按延迟升序，不通的一律不出列`() {
        val now = 1_000_000L
        val states = mapOf(
            "b.slow" to state(640, probedAt = now),
            "a.fast" to state(410, probedAt = now),
            "c.dead" to state(500, passed = false, probedAt = now),
        )
        assertEquals(
            listOf("a.fast", "b.slow"),
            PreferredIpRules.healthyIps(states, now),
        )
    }

    @Test
    fun `探活失败的那台不会到点自动算健康`() {
        // 它要等下一轮真实探活翻身 —— 否则一台稳定回 403 的节点会自己混回候选列表。
        val now = 1_000_000L
        val dead = mapOf("c.dead" to state(500, passed = false, probedAt = now))
        assertTrue(PreferredIpRules.healthyIps(dead, now + PreferredIpRules.PROBE_FRESH_MS * 2).isEmpty())
    }

    @Test
    fun `探活结果过期之后不再被采信`() {
        val now = 1_000_000L
        val stale = mapOf("a.fast" to state(410, probedAt = now))
        assertEquals(listOf("a.fast"), PreferredIpRules.healthyIps(stale, now + 1L))
        assertTrue(
            PreferredIpRules.healthyIps(stale, now + PreferredIpRules.PROBE_FRESH_MS + 1L).isEmpty(),
        )
    }

    @Test
    fun `延迟相同按 IP 串定序，避免同一笔反复换地址重建连接`() {
        val now = 1L
        val states = mapOf(
            "104.16.1.9" to state(400, probedAt = now),
            "104.16.1.2" to state(400, probedAt = now),
        )
        assertEquals(listOf("104.16.1.2", "104.16.1.9"), PreferredIpRules.healthyIps(states, now))
    }

    // ── 一笔请求走哪条路 ──

    private val healthy = mapOf("safebooru.donmai.us" to mapOf("104.21.91.168" to state(410, probedAt = 1_000_000L)))

    private fun snapshot(
        enabled: Boolean = true,
        entries: List<String> = listOf("safebooru.donmai.us"),
        statesByEntry: Map<String, Map<String, PreferredIpState>> = healthy,
        demoted: Map<String, Long> = emptyMap(),
    ) = PreferredIpSnapshot(enabled, entries, statesByEntry, demoted)

    @Test
    fun `关闭时任何域名都走系统解析`() {
        val plan = PreferredIpRules.plan("safebooru.donmai.us", snapshot(enabled = false), nowMs = 1_000_000L)
        assertEquals(PreferredIpPlan.SystemDns, plan)
    }

    @Test
    fun `不在适用表里的域名不受影响`() {
        val plan = PreferredIpRules.plan("www.pixiv.net", snapshot(), nowMs = 1_000_000L)
        assertEquals(PreferredIpPlan.SystemDns, plan)
    }

    @Test
    fun `没探活过或全不通时退回系统解析`() {
        // 从没探过：表里根本没有这个条目。
        assertTrue(
            PreferredIpRules.plan(
                "safebooru.donmai.us",
                snapshot(statesByEntry = emptyMap()),
                nowMs = 1_000_000L,
            ) is PreferredIpPlan.SystemDns,
        )
        // 探过了但全不通。
        val allDead = mapOf("safebooru.donmai.us" to mapOf("1.1.1.1" to state(1, passed = false, probedAt = 1_000_000L)))
        assertTrue(
            PreferredIpRules.plan("safebooru.donmai.us", snapshot(statesByEntry = allDead), 1_000_000L)
            is PreferredIpPlan.SystemDns,
        )
    }

    @Test
    fun `观察窗内退回系统解析，过了窗自己翻身`() {
        val until = 1_060_000L
        val demoted = snapshot(demoted = mapOf("safebooru.donmai.us" to until))
        assertTrue(PreferredIpRules.plan("safebooru.donmai.us", demoted, nowMs = 1_000_000L) is PreferredIpPlan.SystemDns)
        val after = PreferredIpRules.plan("safebooru.donmai.us", demoted, nowMs = until + 1L)
        assertEquals(
            PreferredIpPlan.Preferred("safebooru.donmai.us", listOf("104.21.91.168")),
            after,
        )
        // 剩下的窗要能报出来（UI 与日志都靠它，不是靠猜）。
        assertEquals(
            until - 1_000_000L,
            PreferredIpRules.demoteRemainingMs(demoted, "safebooru.donmai.us", nowMs = 1_000_000L),
        )
    }

    @Test
    fun `通的时候交出排好序的候选`() {
        val plan = PreferredIpRules.plan("safebooru.donmai.us", snapshot(), nowMs = 1_000_000L)
        assertEquals(PreferredIpPlan.Preferred("safebooru.donmai.us", listOf("104.21.91.168")), plan)
    }

    @Test
    fun `默认适用域名只含实测过的两条`() {
        // 这两条的探活路径都实测回 200；konachan.net 那种"TCP 通但恒 403"的不预置。
        assertEquals(
            listOf("safebooru.donmai.us", "cdn-msp.jmapinodeudzn.net"),
            PreferredIpRules.DEFAULT_TARGETS.map { it.host },
        )
        assertTrue(PreferredIpRules.DEFAULT_TARGETS.all { it.path.startsWith("/") })
    }

    // ── 自动选点：官方网段抽样 ──

    private fun ipToLong(ip: String): Long =
        ip.split('.').fold(0L) { acc, part -> (acc shl 8) or part.toLong() }

    private fun inCidr(ip: String, cidr: String): Boolean {
        val (base, prefix) = PreferredIpRules.parseCidr4(cidr)!!
        val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        return (ipToLong(ip) and mask) == base
    }

    @Test
    fun `官方网段表非空且每条都是合法 CIDR`() {
        assertTrue(PreferredIpRules.CLOUDFLARE_IPV4_RANGES.isNotEmpty())
        PreferredIpRules.CLOUDFLARE_IPV4_RANGES.forEach { cidr ->
            // 解析不出来就是表里混进了坏数据 —— 自动选点会整段哑火。
            assertTrue("非法 CIDR: $cidr", PreferredIpRules.parseCidr4(cidr) != null)
        }
    }

    @Test
    fun `抽样数量正确且都是合法 IPv4 字面量`() {
        val ips = PreferredIpRules.sampleCloudflareIps(count = 12, random = kotlin.random.Random(20261001))
        assertEquals(12, ips.size)
        assertEquals(ips.size, ips.toSet().size)
        ips.forEach { ip ->
            // 每一个都必须被现有判据认成合法 IPv4 —— 认不出的会被 parseIps 丢掉，
            // 抽样与落盘两处口径不一致就会出现"抽了 12 台只认出 9 台"的假读数。
            assertEquals(ip, PreferredIpRules.normalizeIp(ip))
        }
    }

    @Test
    fun `抽样地址全部落在官方网段之内`() {
        val ips = PreferredIpRules.sampleCloudflareIps(count = 30, random = kotlin.random.Random(7))
        ips.forEach { ip ->
            assertTrue(
                "$ip 不在官方网段里",
                PreferredIpRules.CLOUDFLARE_IPV4_RANGES.any { cidr -> inCidr(ip, cidr) },
            )
        }
    }

    @Test
    fun `小网段抽样不出界且有多少抽多少`() {
        val ips = PreferredIpRules.sampleCidr4("192.0.2.0/24", count = 5, random = kotlin.random.Random(1))
        assertEquals(5, ips.toSet().size)
        ips.forEach { ip -> assertTrue(inCidr(ip, "192.0.2.0/24")) }
        // /32 只有一个地址：要 5 个也只能给 1 个，不许死循环也不许重复凑数。
        assertEquals(
            listOf("192.0.2.7"),
            PreferredIpRules.sampleCidr4("192.0.2.7/32", count = 5, random = kotlin.random.Random(1)),
        )
    }

    @Test
    fun `CIDR 基址按前缀对齐，非法输入一律拒`() {
        // 基址没对齐时按前缀掩掉：1.5 落在 /8 里就是 1.0.0.0。
        assertEquals(0x01000000L to 8, PreferredIpRules.parseCidr4("1.5.0.0/8"))
        assertNull(PreferredIpRules.parseCidr4("104.16.0.0"))
        assertNull(PreferredIpRules.parseCidr4("104.16.0.0/33"))
        assertNull(PreferredIpRules.parseCidr4("example.com/24"))
        assertNull(PreferredIpRules.parseCidr4("104.16.0.0/-1"))
        // /0 的掩码是 0：任何地址都算网段内。
        assertEquals(0L to 0, PreferredIpRules.parseCidr4("1.2.3.4/0"))
    }

    @Test
    fun `抽样数量为 0 或负数时交空表`() {
        assertTrue(PreferredIpRules.sampleCloudflareIps(count = 0).isEmpty())
        assertTrue(PreferredIpRules.sampleCloudflareIps(count = -3).isEmpty())
    }

    // ── 线路测速页的「一条线」聚合（PreferredIpRules.summarizeLineStates）──

    private fun lineProbe(ip: String, passed: Boolean, latency: Long, detail: String = if (passed) "$latency ms" else "fail") =
        PreferredIpLineProbe(ip, passed, latency, detail)

    @Test
    fun `全过且快的节点算健康且排在最前`() {
        val input = mapOf(
            "safebooru.donmai.us" to listOf(lineProbe("1.1.1.1", true, 200)),
            "cdn-msp.jmapinodeudzn.net" to listOf(lineProbe("1.1.1.1", true, 250)),
        )
        val lines = PreferredIpRules.summarizeLineStates(input)
        assertEquals(1, lines.size)
        assertEquals(PreferredIpLineStatus.Healthy, lines[0].status)
        assertEquals("1.1.1.1", lines[0].ip)
        assertEquals(2, lines[0].totalEntries)
        assertEquals(2, lines[0].passedEntries)
        assertEquals(250, lines[0].slowestLatencyMs)
    }

    @Test
    fun `部分域名失败算降级且排在全过之后`() {
        // 同一域名下的各候选并列摆；partial.bad 过 a 但没过 b → 部分通过 → 降级（黄），不是失败。
        val input = mapOf(
            "a.com" to listOf(lineProbe("fast.ok", true, 100), lineProbe("partial.bad", true, 100)),
            "b.com" to listOf(lineProbe("fast.ok", true, 120), lineProbe("partial.bad", false, 0, "HTTP 403")),
        )
        val lines = PreferredIpRules.summarizeLineStates(input)
        // fast.ok 全过 → 排前；partial.bad 只过 a → 排后（降级）。
        assertEquals(listOf("fast.ok", "partial.bad"), lines.map { it.ip })
        assertEquals(PreferredIpLineStatus.Degraded, lines[1].status)
        assertEquals("HTTP 403", lines[1].failureReason)
    }

    @Test
    fun `全部失败沉底且标红，失败原因取第一条`() {
        val input = mapOf(
            "a.com" to listOf(lineProbe("dead", false, 0, "超时"), lineProbe("alive", true, 300)),
            "b.com" to listOf(lineProbe("dead", false, 0, "连不上"), lineProbe("alive", true, 300)),
        )
        val lines = PreferredIpRules.summarizeLineStates(input)
        assertEquals(listOf("alive", "dead"), lines.map { it.ip })
        assertEquals(PreferredIpLineStatus.Failing, lines[1].status)
        assertEquals("超时", lines[1].failureReason)
    }

    @Test
    fun `全过但偏慢算降级而不是健康`() {
        val input = mapOf(
            "a.com" to listOf(lineProbe("slow", true, 900)),
            "b.com" to listOf(lineProbe("slow", true, 900)),
        )
        assertEquals(PreferredIpLineStatus.Degraded, PreferredIpRules.summarizeLineStates(input)[0].status)
    }

    @Test
    fun `同档内按最慢延迟升序`() {
        val input = mapOf(
            "a.com" to listOf(lineProbe("slow.one", true, 600), lineProbe("fast.one", true, 110)),
            "b.com" to listOf(lineProbe("slow.one", true, 600), lineProbe("fast.one", true, 110)),
        )
        assertEquals(
            listOf("fast.one", "slow.one"),
            PreferredIpRules.summarizeLineStates(input).map { it.ip },
        )
    }

    @Test
    fun `多域名部分通过时通过数与最慢延迟都正确`() {
        val input = mapOf(
            "a.com" to listOf(lineProbe("p", true, 200)),
            "b.com" to listOf(lineProbe("p", false, 0, "HTTP 403")),
            "c.com" to listOf(lineProbe("p", true, 500)),
        )
        val line = PreferredIpRules.summarizeLineStates(input).single()
        assertEquals(3, line.totalEntries)
        assertEquals(2, line.passedEntries)
        assertEquals(500, line.slowestLatencyMs)
        assertEquals(PreferredIpLineStatus.Degraded, line.status)
        assertEquals("HTTP 403", line.failureReason)
    }

    // ── speedTestSources：测速目标跟着用户实际拥有的源走（装了才显示） ──

    @Test
    fun `测速目标跟随已安装漫画源_装了才显示`() {
        val sources = PreferredIpRules.speedTestSources(
            installedComicSourceKeys = listOf("jm", "picacg"),
            comicSourceNames = mapOf("jm" to "禁漫天堂", "picacg" to "哔咔漫画"),
            enabledGalleryRouteKeys = listOf("yandere", "safebooru"),
            userHosts = emptyList(),
        )
        // 漫画源按安装顺序在前，图库站在后。
        assertEquals(
            listOf("cdn-msp.jmapinodeudzn.net", "picaapi.picacomic.com", "yande.re", "safebooru.donmai.us"),
            sources.map { it.target.host },
        )
        // label 用源自己声明的名字，不是裸 key。
        assertEquals("禁漫天堂", sources[0].label)
        assertEquals("哔咔漫画", sources[1].label)
    }

    @Test
    fun `未安装的漫画源不出现`() {
        val sources = PreferredIpRules.speedTestSources(
            installedComicSourceKeys = listOf("jm"),
            comicSourceNames = mapOf("jm" to "禁漫天堂"),
            enabledGalleryRouteKeys = listOf("yandere", "safebooru"),
            userHosts = emptyList(),
        )
        val hosts = sources.map { it.target.host }
        assertTrue("哔咔没装就不该测 picaapi：$hosts", "picaapi.picacomic.com" !in hosts)
        assertTrue("nhentai 没装就不该测：$hosts", "nhentai.net" !in hosts)
        assertTrue("ehentai 没装就不该测：$hosts", "api.e-hentai.org" !in hosts)
    }

    @Test
    fun `哔咔判据是非5xx算通_实测200端点仍要求2xx`() {
        // 哔咔 API 对未签名请求回 400（实测 TLS 通、Server: cloudflare）——
        // 根路径不是业务端点，判"节点认不认 SNI"，所以 require2xx 必须放开。
        assertEquals(false, PreferredIpRules.COMIC_SOURCE_API_TARGETS.getValue("picacg").require2xx)
        // JM 图床与 safebooru 有实测 200 业务端点，保持业务级判据。
        assertEquals(true, PreferredIpRules.COMIC_SOURCE_API_TARGETS.getValue("jm").require2xx)
        assertEquals(true, PreferredIpRules.GALLERY_SPEED_TEST_SOURCES.getValue("safebooru").target.require2xx)
    }

    @Test
    fun `图库恒带yandere与safebooru_gelbooru有账号才出现`() {
        val withoutAccount = PreferredIpRules.speedTestSources(
            installedComicSourceKeys = emptyList(),
            comicSourceNames = emptyMap(),
            enabledGalleryRouteKeys = listOf("yandere", "safebooru"),
            userHosts = emptyList(),
        )
        val hosts = withoutAccount.map { it.target.host }
        assertTrue("yande.re" in hosts)
        assertTrue("safebooru.donmai.us" in hosts)
        assertTrue("没配账号 Gelbooru 不该出现：$hosts", "gelbooru.com" !in hosts)

        val withAccount = PreferredIpRules.speedTestSources(
            installedComicSourceKeys = emptyList(),
            comicSourceNames = emptyMap(),
            enabledGalleryRouteKeys = listOf("yandere", "gelbooru", "safebooru"),
            userHosts = emptyList(),
        )
        assertTrue("gelbooru.com" in withAccount.map { it.target.host })
    }

    @Test
    fun `同域名只出现一次_重复安装与手填都不重复`() {
        val sources = PreferredIpRules.speedTestSources(
            // jm 预装且与 copy_manga 双版本同 key 的形态由 distinct 兜住；
            // cdn-msp 又被用户手填了一遍 → 只留第一条。
            installedComicSourceKeys = listOf("jm", "jm"),
            comicSourceNames = mapOf("jm" to "禁漫天堂"),
            enabledGalleryRouteKeys = listOf("safebooru"),
            // 大小写不同的同域名也要去重。
            userHosts = listOf("SafeBooru.Donmai.us", "cdn-msp.jmapinodeudzn.net", "my.example.org"),
        )
        assertEquals(
            listOf("cdn-msp.jmapinodeudzn.net", "safebooru.donmai.us", "my.example.org"),
            sources.map { it.target.host },
        )
    }

    @Test
    fun `用户手填域名追加为custom条目且走根路径`() {
        val sources = PreferredIpRules.speedTestSources(
            installedComicSourceKeys = emptyList(),
            comicSourceNames = emptyMap(),
            enabledGalleryRouteKeys = listOf("yandere"),
            userHosts = listOf("my.example.org"),
        )
        val custom = sources.last()
        assertEquals("my.example.org", custom.target.host)
        assertEquals(PreferredIpRules.FALLBACK_PATH, custom.target.path)
        assertEquals("my.example.org", custom.label)
        assertTrue(custom.sourceKey.startsWith("custom:"))
    }

    @Test
    fun `knownTargetFor按域名查已知端点_未知返回null`() {
        // 默认表。
        assertEquals(
            "/posts.json?limit=1",
            PreferredIpRules.knownTargetFor("safebooru.donmai.us")?.path,
        )
        // 漫画源表（哔咔的 SNI 判据在这里也要能查到）。
        val pica = PreferredIpRules.knownTargetFor("picaapi.picacomic.com")
        assertEquals(false, pica?.require2xx)
        // 图库表。
        assertEquals("gelbooru.com", PreferredIpRules.knownTargetFor("gelbooru.com")?.host)
        // 大小写与尾点都归一。
        assertEquals("picaapi.picacomic.com", PreferredIpRules.knownTargetFor("PICAapi.Picacomic.COM.")?.host)
        // 未知域名返回 null（调用方走根路径判据）。
        assertEquals(null, PreferredIpRules.knownTargetFor("no.where.example"))
        assertEquals(null, PreferredIpRules.knownTargetFor(""))
    }

    // ── RESET 与 CONNECT 分档：被按域名阻断 ≠ 节点不可达 ──
    // 依据：2026-10-01 真机测速，同一台节点 Safebooru 通、yande.re 等"443 被拒"——
    // TCP 是通的，被掐的是带明文 SNI 的 TLS 握手。两句文案必须分开，否则用户以为换节点有救。

    @Test
    fun `连接被重置算失败且文案指明开代理`() {
        val failure = PreferredIpRules.verdict(
            PreferredIpReading(ip = "1.2.3.4", error = PreferredIpError.RESET),
            PreferredIpTarget("yande.re", "/"),
        )
        // 非 null = 失败；文案必须给出正解（开代理），而不是"换台节点再试"。
        assertNotNull(failure)
        assertTrue("文案要含开代理指引：$failure", failure!!.contains("开代理"))
        assertTrue("文案要指出是按域名阻断：$failure", failure.contains("阻断"))
    }

    @Test
    fun `连接被拒与被重置是两句不同的话`() {
        val connect = PreferredIpRules.verdict(
            PreferredIpReading(ip = "1.2.3.4", error = PreferredIpError.CONNECT),
            PreferredIpTarget("a.example", "/"),
        )!!
        val reset = PreferredIpRules.verdict(
            PreferredIpReading(ip = "1.2.3.4", error = PreferredIpError.RESET),
            PreferredIpTarget("a.example", "/"),
        )!!
        assertTrue(connect.contains("被拒或不可达"))
        // 被重置那句不该再出现"443 被拒"——那是误导（TCP 其实是通的）。
        assertTrue(!reset.contains("被拒或不可达"))
    }
}
