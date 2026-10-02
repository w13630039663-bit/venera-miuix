package com.venera.compose.data.api

/**
 * 网络卫生：熔断器与取流客户端的**运维动作**口。
 *
 * 为什么 UI 需要它：页面上的「刷新 / 重试」要清掉这一站的熔断状态，而今天它是 Composable
 * 直抓 `object HostCircuitBreaker` —— 审计 §二 第 8 行点名的两处
 * （`gallery/ui/GalleryScreen.kt:400`、`GalleryDailyScreen.kt:107`），外加
 * `feature/settings/NetworkSettings.kt:70` 与同文件 `:148` 的代理重建客户端。
 *
 * ⚠️ 漫画侧**今天已经有这条门面的先例**：`source/ComicSourceManager.kt:1274-1276` 的
 * `unreachableHosts()` / `resetNetworkBreaker()` 就是把 `HostCircuitBreaker` 包了一层。
 * 本契约不是新造抽象，是把那层"只有漫画侧有"的包装补成两侧共用的一处。
 *
 * 不收 `isOpen` / `recordSuccess` / `recordFailure` / `remainingOpenMs`：那是拦截器与
 * 取流层用的，UI 目录零命中。
 */
interface NetworkHygiene {

    /** 清掉**一个**主机的熔断（画廊那两页按 `GallerySite.apiHost` 逐站清）。 */
    fun resetBreaker(host: String)

    /** 清空全部熔断（设置页那颗「重置网络状态」）。 */
    fun resetAllBreakers()

    /** 当前处于熔断中的主机集合。 */
    fun unreachableHosts(): Set<String>

    /** 代理/UA 类设置改完后的重建：让后续请求用上新的 OkHttpClient 装配。 */
    fun rebuildHttpClient()

    /**
     * HTTP 缓存当前占用字节数（设置页那行「缓存 xx MB」的读数）。
     *
     * **非 suspend**：调用点自己包在 `withContext(Dispatchers.IO)` 里（`feature/settings/AppSettings.kt:48`），
     * 契约这边改成 suspend 就等于把那次的调度位置换掉。
     */
    fun httpCacheSizeBytes(): Long

    /** 清空 HTTP 缓存，返回清掉的字节数（同一颗页那颗「清除缓存」按钮）。 */
    fun clearHttpCache(): Long
}

/**
 * 一次「取一段文本」的 HTTP 动作。
 *
 * 为什么只有这一颗契约是给**缺陷**立的、不是给分层立的：
 * `feature/sourcemanage/ComicSourceViewModel.kt:569-571` 在 ViewModel 里
 * `VeneraNetworkClient.getInstance(...)` 之后**自己拼 `okhttp3.Request.Builder()`** 发同步请求。
 * `VeneraNetworkClient.kt:46` 装配出来的那一整套（`PersistentCookieJar`、限流拦截器、
 * 熔断拦截器、HTTP 缓存）**一条都不在这笔请求上**。后果是可指认的：从仓库 URL 装源时
 * 拿不到 cookie、不受限流保护、失败原因被 okhttp 的原始异常替掉，排障时看起来像"那个源脚本坏了"。
 *
 * ⚠️ 语义不许在实现里放宽：
 * 1. **不做协程上下文切换** —— 同步 `execute()` 留在调用方的调度器上。今天的调用点已经在
 *    `withContext(Dispatchers.IO)` 里，实现里再套一层就是给它凭空多一次上下文切换。
 * 2. 返回 [HttpTextResponse] 而不是 `String` —— 调用点那句 `resp.isSuccessful && !content.isNullOrBlank()`
 *    与失败分支里的 `"HTTP ${resp.code}"` 都是判据的一部分；吞掉 `ok`/`code` 等于改行为。
 */
interface HttpTextFetch {

    suspend fun fetchText(url: String): HttpTextResponse
}

/**
 * @param ok HTTP 层成功（2xx）**且**正文非空非空白 —— 与改造前调用点那两个条件逐字同义。
 * @param code 原始状态码；失败分支要把它拼进给用户看的那句"获取脚本失败"。
 * @param body 正文；`null` 表示站方没给 body。
 */
data class HttpTextResponse(val ok: Boolean, val code: Int, val body: String?)
