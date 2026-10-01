package com.venera.compose.data.network

import java.net.InetAddress
import okhttp3.Interceptor
import okhttp3.Dns
import okhttp3.Response

/**
 * 优选 IP 的**落地点**：一笔请求到底连哪个地址（[PreferredIpDns]），
 * 以及连不上时怎么退回正常解析（[PreferredIpFallbackInterceptor]）。
 *
 * ## 为什么这两个件必须一起看
 *
 * 绑 IP 这件事本身只改**连接目标**：URL 里的域名不动，所以 Host 头、TLS SNI、
 * 证书校验对象全都还是原域名（2026-10-01 本机实测：`--resolve safebooru.donmai.us:443:104.21.91.168`
 * 回 200 且 `ssl_verify_result=0`；OkHttp 侧走的就是这条官方路子的自定义 `Dns`，
 * 不需要自造 `SocketFactory`）。
 *
 * 但**退回不是自动的**：共享客户端今天是 `retryOnConnectionFailure(false)`
 * （由 HEAD 的 `VeneraNetworkClient.kt` 钉着，为的是死域名不被反复消耗），
 * OkHttp 因此不会在同一笔里换下一个候选地址。所以回退必须有人显式做第二次 ——
 * 这一层的拦截器重投一次 `proceed()` 正好可以：应用层拦截器每重投一次，
 * 下游会重新做路由规划，也就**重新问一次 [PreferredIpDns]**，而此时观察窗已经写好，
 * 判据会把这一条域名交回 `Dns.SYSTEM`。
 *
 * ## 只管三件事，其余一律不碰
 *
 * - 不在适用表里的域名 → 原样 `Dns.SYSTEM`，别的站不受影响；
 * - 没开启 → 同上，等于这层没挂；
 * - **配了代理时**：OkHttp 只会用 `Dns` 解析**代理主机**，目标域名由代理解析 ——
 *   也就是说优选 IP 只在"无代理直连"这一档生效。UI 必须把这句说出来，
 *   否则用户"开了优选 IP 又挂着代理"会以为功能坏了。
 */
internal class PreferredIpDns(private val delegate: Dns = Dns.SYSTEM) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        // 冷启动/清读数后的死锁修复：读数空表时 plan 永远交 SystemDns，回退拦截器因此永远
        // 不触发，refreshInBackground 也就永远没人叫 —— 优选层整段静默失效（真机实证：
        // 「旧自动选点」能用是因为它当场跑了 probeAll，「使用最优线路」写回后读数是空的）。
        // 这里在「配置了但没读数/已过期」时发起后台补探（needsProbe 判据 + refreshing 单飞），
        // 当前这一笔照旧走 delegate —— 补探不挡请求，探完后续请求自然钉上。
        if (PreferredIpRuntime.needsProbe(System.currentTimeMillis())) {
            PreferredIpProbe.refreshInBackground()
        }
        val plan = PreferredIpRules.plan(hostname, PreferredIpRuntime.snapshot(), System.currentTimeMillis())
        if (plan is PreferredIpPlan.Preferred) {
            // 候选串在 PreferredIpRules.parseIps 里已经严格校验成 IP 字面量，
            // 所以这里 getByName 不会再发 DNS（那是这条路的全部意义）。
            val pinned = plan.ips.mapNotNull { literal ->
                runCatching { InetAddress.getByName(literal) }.getOrNull()
            }
            if (pinned.isNotEmpty()) return pinned
        }
        return delegate.lookup(hostname)
    }
}

/**
 * 连接层失败后的**一次**重投：先把该条目的优选档推进观察窗，再走一遍正常解析。
 *
 * 只重投幂等方法。POST 不重投不是保守 —— 源脚本里的登录/签到类请求重一次就是提交两次，
 * 而"这一笔也许用系统解析能成"不值得拿用户的账号状态去换。POST 这一次失败就照实失败，
 * 观察窗照样生效，下一笔自然走正常解析。
 *
 * 计数与 [HostCircuitBreaker] 不共用（理由见 [PreferredIpRules.PreferredIpState]）。
 * 挂的位置也要讲究：必须在熔断拦截器**内侧**，这样两次尝试只有最终那一次结果进熔断，
 * 不会"优选 IP 试一次、系统解析再试一次"就把一个正常源计成两笔失败。
 */
internal class PreferredIpFallbackInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val now = System.currentTimeMillis()
        val plan = PreferredIpRules.plan(
            request.url.host,
            PreferredIpRuntime.snapshot(),
            now,
        )
        if (plan !is PreferredIpPlan.Preferred) return chain.proceed(request)

        return try {
            chain.proceed(request)
        } catch (failure: java.io.IOException) {
            if (!isConnectLevel(failure) || !isIdempotent(request.method)) throw failure
            PreferredIpRuntime.demote(plan.entry, System.currentTimeMillis() + PreferredIpRules.DEMOTE_MS)
            PreferredIpProbe.refreshInBackground()
            chain.proceed(request)
        }
    }

    /** 只认"真连不上"这一类：与 [HostCircuitBreakerInterceptor] 同一组判据 + TLS 握手失败。 */
    private fun isConnectLevel(e: java.io.IOException): Boolean = when (e) {
        is java.net.SocketTimeoutException -> true
        is java.net.ConnectException -> true
        is java.net.NoRouteToHostException -> true
        is java.net.PortUnreachableException -> true
        is javax.net.ssl.SSLException -> true
        else -> false
    }

    private fun isIdempotent(method: String): Boolean = method == "GET" || method == "HEAD"
}
