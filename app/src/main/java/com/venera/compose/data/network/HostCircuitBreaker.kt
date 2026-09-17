package com.venera.compose.data.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * 域名级熔断器（Fast-Fail Circuit Breaker）
 *
 * ## 为什么必须要有它
 *
 * 全网聚合搜索会同时向 30+ 个漫画源发起请求。在未配置代理的国内网络下，
 * MangaDex / Picacg / e-hentai / nhentai / hitomi / komik 等相当一部分源是**不可达**的。
 * 若不做熔断，每次搜索都要为每个死域名付出：
 *
 * ```
 * connect timeout(15s) × 重试次数(3) + 退避(1s+2s) ≈ 48s
 * ```
 *
 * 这会让整轮搜索极长时间无法收敛，用户看到的现象就是"搜不到东西 / 一直转圈"。
 *
 * 熔断后：连续失败达到阈值的 host 在 [OPEN_DURATION_MS] 内直接快速失败（毫秒级），
 * 让可达的源（包子漫画等）立即返回结果。同时该状态也可反哺"源可用性测试"UI。
 */
object HostCircuitBreaker {

    /** 连续失败多少次后打开熔断 */
    private const val FAILURE_THRESHOLD = 2

    /** 熔断保持时长：期间该 host 快速失败 */
    private const val OPEN_DURATION_MS = 60_000L

    private class State {
        var consecutiveFailures: Int = 0
        var openUntil: Long = 0L
    }

    private val states = ConcurrentHashMap<String, State>()

    private fun stateOf(host: String) = states.computeIfAbsent(host.lowercase()) { State() }

    /** 该 host 当前是否处于熔断（应快速失败） */
    fun isOpen(host: String): Boolean {
        val state = states[host.lowercase()] ?: return false
        synchronized(state) {
            if (state.openUntil == 0L) return false
            if (System.currentTimeMillis() < state.openUntil) return true
            // 熔断到期，半开：允许重试
            state.openUntil = 0L
            state.consecutiveFailures = 0
            return false
        }
    }

    fun recordSuccess(host: String) {
        val state = stateOf(host)
        synchronized(state) {
            state.consecutiveFailures = 0
            state.openUntil = 0L
        }
    }

    fun recordFailure(host: String) {
        val state = stateOf(host)
        synchronized(state) {
            state.consecutiveFailures++
            if (state.consecutiveFailures >= FAILURE_THRESHOLD) {
                state.openUntil = System.currentTimeMillis() + OPEN_DURATION_MS
                state.consecutiveFailures = 0
            }
        }
    }

    /** 距离熔断恢复还剩多少毫秒（未熔断返回 0） */
    fun remainingOpenMs(host: String): Long {
        val state = states[host.lowercase()] ?: return 0L
        synchronized(state) {
            val remain = state.openUntil - System.currentTimeMillis()
            return if (remain > 0) remain else 0L
        }
    }

    /** 当前处于熔断中的 host 快照，用于"源可用性测试"面板展示 */
    fun openHosts(): Set<String> =
        states.entries.filter { (_, state) ->
            synchronized(state) { System.currentTimeMillis() < state.openUntil }
        }.map { it.key }.toSet()

    /** 手动清除某个 host 的熔断（用户点击"重试"时） */
    fun reset(host: String) {
        states.remove(host.lowercase())
    }

    fun resetAll() {
        states.clear()
    }
}

/**
 * 熔断拦截器：必须挂在拦截器链的**最外层**，
 * 这样死域名的快速失败发生在限速/重试/过盾之前，不会浪费线程与时间。
 *
 * ⚠️ 计数口径刻意收窄为「真正的连不上」（DNS 解析失败 / 连接被拒 / 读写超时）。
 * 若把任意 IOException 或 5xx 都计为失败，图片 CDN 偶尔几张图 404/超时就会
 * 触发整站熔断，反而造成大面积的图片加载失败。
 */
class HostCircuitBreakerInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        val remain = HostCircuitBreaker.remainingOpenMs(host)
        if (remain > 0) {
            throw IOException("Host $host 暂时不可达（熔断中，${remain / 1000}s 后重试）")
        }

        return try {
            val response = chain.proceed(request)
            // 能拿到 HTTP 响应即说明网络层是通的（哪怕是 4xx/5xx）
            HostCircuitBreaker.recordSuccess(host)
            response
        } catch (e: IOException) {
            if (isUnreachable(e)) HostCircuitBreaker.recordFailure(host)
            throw e
        }
    }

    private fun isUnreachable(e: IOException): Boolean = when (e) {
        is java.net.UnknownHostException -> true
        is java.net.ConnectException -> true
        is java.net.NoRouteToHostException -> true
        is java.net.PortUnreachableException -> true
        is java.net.SocketTimeoutException -> true
        else -> false
    }
}
