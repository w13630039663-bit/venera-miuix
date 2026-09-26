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
 * 打上这个 tag 的请求 = 取图片字节流，**整个绕过域名熔断**。
 *
 * 熔断器是为「聚合搜索 30+ 个源里那些真死域名」造的（见上面那段），而图片与它口径相反：
 * 图片 CDN 偶发几张超时是常态，一旦计入就按域名拉黑 60s —— 真机表现是历史页/收藏页整屏
 * 空白封面（`AsyncImage` 没有 error 占位，抛错就等于什么都不画），且半开后再次被两笔超时拉黑，
 * 变成"经常显示不出图"。
 * 反向也被污染：图片请求成功会 `recordSuccess` 把该 host 的失败计数清零，
 * 等于让真死的源重新慢起来 —— 所以图片既不快败、也不计数。
 */
class ImageFetchTag

/**
 * 打上这个 tag 的请求**不弹** Cloudflare 交互式过盾窗口，撞盾就原样失败。
 *
 * ⚠️ 与 [ImageFetchTag] 是**两件事**，别合并：
 * - [ImageFetchTag] = "图片取流"，同时关掉过盾**和**域名熔断（图片不该把 host 拉黑）；
 * - 本 tag = **只**关过盾，熔断照旧参与。
 *
 * 用途是画廊日榜那类"整屏等结果"的 API 流量（2026-09-26）：
 * 过盾是 `runBlocking { bypass() }` 等一个人机交互，而那个 `await()` 没有超时 ——
 * 用户若 Home 掉过盾窗口而不是点"取消"，取数线程会一直挂住，
 * 屏上就是永远的波浪环（OkHttp 的 callTimeout 关 socket 唤醒不了 parked 的线程）。
 * 但这类流量**仍然要参与熔断** —— 否则连续失败不会把 host 拉黑，
 * 「刷新 / 重试」的 `resetBreakers()` 也就没了对象。
 *
 * 所以：撞盾时直接失败并说一句话（代价是要手动重试），
 * 但"连不上"仍然走熔断快败（保住重试按钮的语义）。
 */
class NoInteractiveBypassTag

/**
 * 熔断拦截器：必须挂在拦截器链的**最外层**，
 * 这样死域名的快速失败发生在限速/重试/过盾之前，不会浪费线程与时间。
 *
 * ⚠️ 计数口径刻意收窄为「真正的连不上」（DNS 解析失败 / 连接被拒 / 读写超时）。
 * 任意 IOException 或 5xx 都不算 —— 站点偶尔一个 404 就把整站拉黑，代价远大于收益。
 * 图片取流则**整条不参与**，见 [ImageFetchTag]。
 */
class HostCircuitBreakerInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        // 图片取流不参与熔断（见 [ImageFetchTag]）。
        if (request.tag(ImageFetchTag::class.java) != null) return chain.proceed(request)

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
