package com.venera.compose.data.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 限速、并发去重与 429 退避重试拦截器。
 *
 * 功能：
 * 1. 同 URL 并发互斥去重 (Prevent-Parallel)：防止同一张漫画图片或元数据在极短时间内被多个协程重复高并发打入网络。
 * 2. 域名速率限制 (Rate Limiting)：对特定敏感源（如 MangaDex 限制 API 速率）执行滑动窗口间隔控制。
 * 3. 429 (Too Many Requests) 退避重试：读取 Retry-After 头，并按指数退避最多重试 3 次。
 */
class RateLimitingInterceptor : Interceptor {

    private val tag = "RateLimiting"

    /** 针对 Host 的最后一次请求时间戳，用于控制域名最小时间间隔 */
    private val hostLastRequestTime = ConcurrentHashMap<String, Long>()

    /** 特定域名的最小请求间隔（毫秒），例如 MangaDex API 限制 */
    private val hostMinIntervalMs = mapOf(
        "api.mangadex.org" to 300L,
        "api.copymanga.tv" to 200L,
        "api.copymanga.org" to 200L
    )

    /** 同 URL 并发锁，防止同一时刻多个相同请求并发撞车 */
    private val activeUrlLocks = ConcurrentHashMap<String, ReentrantLock>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val host = request.url.host.lowercase()

        // 1. 域名速率限制 (Rate Limit)
        val minInterval = hostMinIntervalMs.entries
            .firstOrNull { host.endsWith(it.key) }?.value ?: 0L

        if (minInterval > 0) {
            synchronized(hostLastRequestTime) {
                val lastTime = hostLastRequestTime[host] ?: 0L
                val now = System.currentTimeMillis()
                val diff = now - lastTime
                if (diff < minInterval) {
                    val waitTime = minInterval - diff
                    try {
                        Thread.sleep(waitTime)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
                hostLastRequestTime[host] = System.currentTimeMillis()
            }
        }

        // 2. 针对 GET 请求进行同 URL 并发互斥（仅对同 URL 请求进行串行化排队）
        val isGet = request.method.equals("GET", ignoreCase = true)
        val urlLock = if (isGet) {
            activeUrlLocks.computeIfAbsent(url) { ReentrantLock() }
        } else null

        if (urlLock != null) {
            urlLock.lock()
        }

        try {
            var attempt = 0
            val maxAttempts = 3
            var response: Response? = null

            while (attempt < maxAttempts) {
                attempt++
                try {
                    response = chain.proceed(request)
                } catch (e: IOException) {
                    if (attempt >= maxAttempts) throw e
                    try {
                        Thread.sleep(1000L * attempt)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw e
                    }
                    continue
                }

                // 3. 捕获 429 Too Many Requests
                if (response.code == 429) {
                    if (attempt >= maxAttempts) {
                        return response
                    }
                    val retryAfterSec = response.header("Retry-After")?.toLongOrNull() ?: (1L shl (attempt - 1))
                    val sleepMs = TimeUnit.SECONDS.toMillis(retryAfterSec).coerceIn(1000L, 10000L)
                    Log.w(tag, "HTTP 429 for $url. Waiting ${sleepMs}ms before retry #$attempt")
                    response.close()
                    try {
                        Thread.sleep(sleepMs)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return response
                    }
                    continue
                }

                // 非 429，正常返回
                return response
            }

            return response ?: throw IOException("Failed to execute request after $maxAttempts attempts")
        } finally {
            if (urlLock != null) {
                urlLock.unlock()
                // 如果没有其他线程在排队该锁，及时清理防止内存泄漏
                if (!urlLock.hasQueuedThreads()) {
                    activeUrlLocks.remove(url, urlLock)
                }
            }
        }
    }
}
