package com.venera.compose.data.network

import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 优选 IP 的**探活**：对每台候选节点发一次**真实业务请求**，看它今天能不能替这个域名服务。
 *
 * ## 为什么必须发业务请求，不能 ping / 不能只看"连上了"
 *
 * 本机实测（2026-10-01）两组读数把这件事钉死了：
 * - `konachan.net` 走 CF、TCP 443 **通**，HTTP 层恒 **403**（CF 挑战页）—— 只看"连上了"会把它当可用节点；
 * - 同一个 `safebooru.donmai.us`，四台 CF 节点里 `108.162.193.171` 稳定 **403**、另三台 **200**（0.41~0.66 s）。
 *
 * 所以判据是"这台节点替这个 hostname 回了一个业务上答上了的响应"：路径按
 * [PreferredIpRules.DEFAULT_TARGETS] 取该站实测可用的那条端点，其余域名走根路径且只认 2xx/3xx。
 *
 * ## 三条刻意的设计
 *
 * 1. **自建客户端，不走共享那套拦截器链**。共享链上有域名熔断、限速与交互式过盾：
 *    探活失败被计入熔断会把一个正常源拉黑 60 秒，而过盾会在探活线程上等一个人机交互。
 *    从共享链只继承一份东西 —— **UA 判据**（[userAgentFor]），因为"这台节点认不认这串 UA"
 *    本来就是真实结果的一部分。
 * 2. **强制 `Proxy.NO_PROXY`，且挂了代理就整轮不探**（[PreferredIpRuntime.Config.proxyInUse]）：
 *    代理在的话 OkHttp 只用 `Dns` 解析代理主机、目标域名由代理解析，量到的延迟不是用户这台机器的。
 * 3. **并发探**，一台候选一个客户端：串行探 12 台在弱网下要半分钟，用户盯着转圈等不到。
 */
internal object PreferredIpProbe {

    /** 一台候选的一行读数（设置页直接摆）。 */
    data class Row(
        val ip: String,
        /** true = 这一轮通了，可以进候选列表。 */
        val passed: Boolean,
        val latencyMs: Long,
        /** 通过时是"HTTP 200 · 412 ms"，失败时是失败原因。 */
        val detail: String,
    )

    @Volatile
    private var refreshing = false

    /**
     * 后台补一轮探活：只在"开启了、两张表都非空、且从没探过或已过 freshness"时发起，
     * 同一时刻最多一轮（[refreshing] 单飞）。
     *
     * 由 [PreferredIpFallbackInterceptor] 在退回系统解析之后调用 —— 那说明表里有节点死了。
     * 它**不挡当前这一笔**：当前这笔已经走系统解析了。
     */
    fun refreshInBackground() {
        if (!PreferredIpRuntime.needsProbe(System.currentTimeMillis())) return
        synchronized(this) {
            if (refreshing) return
            refreshing = true
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    probeAll()
                } finally {
                    refreshing = false
                }
            }
        }
    }

    /**
     * 完整跑一轮：每个适用域名 × 每台候选，全部并发。
     *
     * [requireEnabled] = false 时总开关没开也照探（**探活与开关是两件事**：
     * 设置页的「自动测速选点」要在开关没开时也能先量出"哪台通"给用户看）。
     * 后台补探那一路保持默认 true，口径不变。
     *
     * 返回 `条目 → 各候选读数`，供设置页逐台摆；写回 [PreferredIpRuntime] 与返回**同步**做，
     * 所以"探完立刻生效"不依赖 UI 什么时候去读。
     */
    suspend fun probeAll(requireEnabled: Boolean = true): Map<String, List<Row>> = withContext(Dispatchers.IO) {
        val config = PreferredIpRuntime.currentConfig()
        if ((requireEnabled && !config.enabled) || config.ips.isEmpty() || config.entries.isEmpty()) {
            return@withContext emptyMap()
        }
        if (config.proxyInUse) {
            // 挂了代理：这一轮量不到用户这台机器，已有读数一并作废。
            PreferredIpRuntime.clearReadings()
            return@withContext emptyMap()
        }
        val clients = config.ips.associateWith { ip -> newProbeClient(ip) }
        try {
            coroutineScope {
                config.entries.map { entry ->
                    val target = PreferredIpRuntime.targetFor(entry)
                    async {
                        entry to config.ips.map { ip ->
                            async { probeOne(entry, target, ip, clients.getValue(ip)) }
                        }.awaitAll()
                    }
                }.awaitAll().toMap()
            }
        } finally {
            clients.values.forEach { it.connectionPool.evictAll() }
            PreferredIpRuntime.finishProbeRound()
        }
    }

    /**
     * 探一台、写回一份读数（[probeAll] 用）。
     *
     * ⚠️ `verdict` 的"通过"形态是 **null**，而屏上那句 [Row.detail] 永远非空 ——
     * 两者必须分开传。把 `detail.isEmpty()` 当"通没通"会让整台列表永远算不出可用节点，
     * 于是"开了优选 IP"静默退化成"什么都没开"（写这版时踩过一次，故记在这里）。
     */
    private suspend fun probeOne(
        entry: String,
        target: PreferredIpTarget,
        ip: String,
        client: OkHttpClient,
    ): Row {
        val row = probeOneRow(entry, target, ip, client)
        PreferredIpRuntime.recordProbe(
            entry = entry,
            ip = ip,
            latencyMs = row.latencyMs,
            passed = row.passed,
            detail = row.detail,
            nowMs = System.currentTimeMillis(),
        )
        return row
    }

    /**
     * 只读探一台：发真实请求、判定通过与否、出一行读数，**不写回** [PreferredIpRuntime]。
     *
     * 与 [probeOne] 唯一差别就是少了 `recordProbe` 那一句 —— 抽出来是为了让测速页
     * （[com.venera.compose.feature.settings.PreferredIpSpeedTestScreen]）能"看一眼"而不污染
     * 用户已落盘的配置：测速只是瞄一眼，不能因为用户瞄了一眼就把设置页那一屏的探活结论覆盖掉。
     */
    private suspend fun probeOneRow(
        entry: String,
        target: PreferredIpTarget,
        ip: String,
        client: OkHttpClient,
    ): Row = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val outcome = runCatching { execute(client, target, ip) }
        val latencyMs = ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(0L)
        val reading = outcome.getOrElse { error ->
            PreferredIpReading(ip = ip, status = null, error = classify(error))
        }
        val failure = PreferredIpRules.verdict(reading, target)
        val detail = failure ?: "HTTP ${reading.status} · $latencyMs ms"
        Row(ip = ip, passed = failure == null, latencyMs = latencyMs, detail = detail)
    }

    /**
     * 测速页用的**只读**探活：拿一批候选 IP × 一批域名，并发打一遍真实请求，
     * 返回 `条目 → 各候选读数`，**不写回 [PreferredIpRuntime]** —— 测速页只是「看一眼」，
     * 不能因为用户瞄了一眼就把他已落盘的配置结论覆盖掉（那会让设置页的探活读数变成测速页的临时结论）。
     *
     * 与 [probeAll] 同一条底层：[newProbeClient] 强制 `NO_PROXY`、按 [PreferredIpRuntime.targetFor]
     * 取真实业务路径、用 [userAgentFor] 那串 UA。挂了代理也不拦：探针本来就是直连，
     * 量到的就是这台机器到边缘节点的真实距离（正好是该页要看的东西）。
     */
    suspend fun probeIps(ips: List<String>, hosts: List<String>): Map<String, List<Row>> =
        withContext(Dispatchers.IO) {
            if (ips.isEmpty() || hosts.isEmpty()) return@withContext emptyMap()
            val clients = ips.associateWith { newProbeClient(it) }
            try {
                coroutineScope {
                    hosts.map { entry ->
                        val target = PreferredIpRuntime.targetFor(entry)
                        async {
                            entry to ips.map { ip ->
                                async { probeOneRow(entry, target, ip, clients.getValue(ip)) }
                            }.awaitAll()
                        }
                    }.awaitAll().toMap()
                }
            } finally {
                clients.values.forEach { it.connectionPool.evictAll() }
            }
        }

    /**
     * 发一次真实请求：URL 用**域名**（Host、SNI、证书校验都按它走），连到哪台由这个客户端的 Dns 钉死。
     *
     * 正文一行都不读 —— 这一轮要的是状态码；但响应必须关掉，否则连接会一直占着。
     */
    private fun execute(client: OkHttpClient, target: PreferredIpTarget, ip: String): PreferredIpReading {
        val url = "https://${target.host}${target.path}"
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        // 与共享客户端同一份 UA 判据：先按 host 策略给，策略里没有再补默认串。
        builder.header("User-Agent", userAgentFor(url.toHttpUrl()))
        ImageHeaderPolicy.headersFor(url).forEach { (name, value) ->
            if (!name.equals("User-Agent", ignoreCase = true)) builder.header(name, value)
        }
        val started = System.currentTimeMillis()
        client.newCall(builder.build()).execute().use { response ->
            return PreferredIpReading(
                ip = ip,
                status = response.code,
                latencyMs = System.currentTimeMillis() - started,
            )
        }
    }

    /**
     * 失败形态分类 —— 屏上"连不上"与"403"是两句话，混起来用户就无从下手。
     *
     * [PreferredIpError.RESET] 单独成档的依据（2026-10-01 真机测速读数）：同一台节点上
     * Safebooru 通（证明 TCP 443 可达、节点活着），yande.re/gelbooru/nhentai/ehentai 却
     * 失败且异常形态是连接被重置 —— TCP 握手成功、TLS ClientHello 带着明文 SNI 出去就被掐，
     * 是**按域名阻断**的典型形态。它与"这台节点不可达"（[ConnectException]，独立子类，
     * **必须先判**，否则真被拒的那台会被错标成被阻断）是两回事，解法也不同：
     * 前者开代理，后者换节点。
     */
    private fun classify(error: Throwable): PreferredIpError = when (error) {
        is java.net.SocketTimeoutException -> PreferredIpError.TIMEOUT
        is javax.net.ssl.SSLException -> PreferredIpError.TLS
        is java.net.UnknownHostException -> PreferredIpError.UNKNOWN_HOST
        is java.net.ConnectException -> PreferredIpError.CONNECT
        // 连接被重置：TCP 通了、带着这个 SNI 的流量被中途掐断（不是 SSLException 的形态，
        // GFW 直接 RST 时 Java 侧抛的是裸 SocketException）。
        is java.net.SocketException -> PreferredIpError.RESET
        // 其余 IO 形态（协议层断等）都归"连不上这一台"，不影响别的候选。
        is java.io.IOException -> PreferredIpError.CONNECT
        // 不是 IO 形态的意外（配置串写坏之类）：宁可报"连不上"，绝不冒充"这台节点通"。
        else -> PreferredIpError.UNKNOWN_HOST
    }

    /**
     * 一台候选一个客户端：`Dns` 里钉死那一个地址。
     *
     * 刻意**不带**共享客户端的拦截器链、CookieJar 与 HTTP 缓存（理由见文件头第 1 条）。
     */
    private fun newProbeClient(ip: String): OkHttpClient {
        // 一台候选一个客户端：Dns 里钉死那一个地址。用显式对象而不是 SAM lambda ——
        // okhttp3.Dns 是 Java 接口，Kotlin 侧对它的 SAM 转换在这里推不出参数类型。
        val pinned = object : okhttp3.Dns {
            override fun lookup(hostname: String): List<InetAddress> = listOf(InetAddress.getByName(ip))
        }
        return OkHttpClient.Builder()
            .dns(pinned)
            .proxy(java.net.Proxy.NO_PROXY)
            .cache(null)
            .retryOnConnectionFailure(false)
            .connectTimeout(PreferredIpRules.PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PreferredIpRules.PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(PreferredIpRules.PROBE_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS)
            .build()
    }
}
