package com.venera.compose.engine

/**
 * 源请求失败时保留的「服务端响应快照」，用于把源抛出的笼统错误补全成可执行提示。
 *
 * ## 为什么需要它
 *
 * 源脚本抛出的错误只带状态码，例如 picacg.js:416 的
 * `throw 'Invalid status code: ' + res.status`。用户最终看到的是：
 *
 * ```
 * picacg：不可用 —— Invalid status code: 400
 * ```
 *
 * 而服务端那次 400 的真实原因是（实测抓包）：
 *
 * ```json
 * {"code":400,"error":"1023","message":"too many requests"}
 * ```
 *
 * 也就是「被限流」——与签名、请求格式、代码逻辑都无关。信息在源脚本里被丢掉了，
 * 只能由 HTTP 层补记：[JsHttpHandler] 在拿到 4xx/5xx 时调用 [record]，
 * 「源可用性测试」失败时再用 [recent] 取回来拼进提示。
 *
 * 只保留最近若干条并带时间戳，避免长时间运行后无界增长，也避免把很久以前的
 * 失败错误地归因到当前这次测试。
 */
object SourceHttpDiagnostics {

    data class Entry(val code: Int, val message: String, val at: Long)

    /** 最多保留条数，超出即淘汰最旧的 */
    private const val MAX_ENTRIES = 16

    /** 判定「同一次操作」的时间窗 */
    const val DEFAULT_WINDOW_MS = 20_000L

    private val entries = ArrayDeque<Entry>()

    /** 记录一次失败响应；[body] 为该响应的响应体（可能为空） */
    @Synchronized
    fun record(code: Int, body: String?) {
        if (code < 400) return
        entries.addLast(Entry(code, extractMessage(body), System.currentTimeMillis()))
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    /**
     * 取最近 [withinMs] 毫秒内的一条失败记录。
     *
     * [preferredCode] 非空时优先匹配相同状态码 —— 这样源抛出的
     * `Invalid status code: 400` 能对齐到服务端那次 400 的原文，
     * 而不会被中间某次 401 抢走。
     */
    @Synchronized
    fun recent(preferredCode: Int? = null, withinMs: Long = DEFAULT_WINDOW_MS): Entry? {
        val now = System.currentTimeMillis()
        val alive = entries.filter { now - it.at <= withinMs }
        if (alive.isEmpty()) return null
        if (preferredCode == null) return alive.last()
        return alive.lastOrNull { it.code == preferredCode } ?: alive.last()
    }

    @Synchronized
    fun clear() = entries.clear()

    /**
     * 从响应体里抽人类可读的原因：优先 JSON 的 message/msg/error 字段，
     * 否则回退到响应体开头（压缩空白后截断）。
     */
    private fun extractMessage(body: String?): String {
        val text = body?.trim().orEmpty()
        if (text.isEmpty()) return "无响应体"
        if (text.startsWith("{")) {
            for (key in listOf("message", "msg", "error_description", "error")) {
                val v = Regex("\"$key\"\\s*:\\s*\"([^\"]{1,160})\"")
                    .find(text)?.groupValues?.get(1)
                if (!v.isNullOrBlank()) return v
            }
        }
        return text.replace(Regex("\\s+"), " ").take(120)
    }
}

/** 源脚本错误里的状态码，形如 `Invalid status code: 400` / `status code:401` */
private val STATUS_CODE_REGEX = Regex("""status code:?\s*(\d{3})""", RegexOption.IGNORE_CASE)

/**
 * 把源脚本抛出的原始错误补全成可执行的说明。
 *
 * 源脚本的错误只有状态码，而那个状态码的真实含义往往被丢掉了：
 *
 * | 源抛出的信息 | 服务端实际返回 | 用户该怎么做 |
 * |---|---|---|
 * | `Invalid status code: 400` | `{"code":400,"error":"1023","message":"too many requests"}` | 限流，稍后重试 / 检查代理出口 IP |
 * | `Failed to login`（picacg 把非 200 一律压成这句） | 同上 | 同上，**并非账号密码错误** |
 * | `Invalid status code: 401` | `unauthorized` | 需要登录或凭证失效 |
 *
 * 所以这里把 [SourceHttpDiagnostics] 记录到的服务端原文与建议一起拼出来。
 * 「不可用：Invalid status code: 400」这种没有信息量的提示正是排查困难的根源。
 */
fun explainSourceFailure(raw: String): String {
    val status = STATUS_CODE_REGEX.find(raw)?.groupValues?.get(1)?.toIntOrNull()
    val server = SourceHttpDiagnostics.recent(status)

    val hint = when {
        server != null && server.message.contains("too many", ignoreCase = true) ->
            "服务端限流，请稍后重试；若走代理，多半是代理出口 IP 被限"
        status == 429 -> "服务端限流，请稍后重试；若走代理，多半是代理出口 IP 被限"
        status == 401 || status == 403 -> "需要登录，或登录凭证已失效"
        status != null && status >= 500 -> "源站服务器故障"
        raw.contains("not logged in", ignoreCase = true) -> "该源需要先登录后才能搜索"
        else -> null
    }

    return buildString {
        append(raw)
        if (server != null) append("（服务端 ").append(server.code).append(": ").append(server.message).append("）")
        if (hint != null) append("｜").append(hint)
    }
}
