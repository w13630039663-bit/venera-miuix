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
 * 把源脚本抛出的原始错误翻成「先说人话、再给技术细节」的一段说明。
 *
 * 源脚本的错误只有状态码，而那个状态码的真实含义往往被丢掉了：
 *
 * | 源抛出的信息 | 服务端实际返回 | 用户该怎么做 |
 * |---|---|---|
 * | `Invalid status code: 400` | `{"code":400,"error":"1023","message":"too many requests"}` | 限流，稍后重试 / 检查代理出口 IP |
 * | `Failed to login`（picacg 把非 200 一律压成这句） | 同上 | 同上，**并非账号密码错误** |
 * | `Invalid status code: 401` | `unauthorized` | 需要登录或凭证失效 |
 *
 * ⚠️ 2026-10-14 第三轮改口径：**人话在前，技术细节在后**。
 * 旧版把 `raw` 原样拼在开头（"不可用：Invalid status code: 400"），屏上第一眼就是代码 ——
 * 与「界面文案不许带实现笔记」那条硬约束冲突。现在改成：第一句是用户能读懂的动作，
 * 原始报文收在「技术细节」括号里。
 *
 * 这一处**故意保留**原始报文（与其他页面不同）：调用点只有「源可用性测试」和
 * 「源登录失败」两个位置，都属于**源管理这颗诊断屏** —— 用户到那儿就是为了排查，
 * 原始报文是他能贴给源作者的唯一凭据，删掉等于把可诊断性一起删掉。
 */
fun explainSourceFailure(raw: String): String {
    val status = STATUS_CODE_REGEX.find(raw)?.groupValues?.get(1)?.toIntOrNull()
    val server = SourceHttpDiagnostics.recent(status)

    val human = when {
        server != null && server.message.contains("too many", ignoreCase = true) ->
            "源站这会儿在限流 ⏳ 等几分钟再试；走代理的话，多半是代理出口 IP 被限"
        status == 429 -> "源站这会儿在限流 ⏳ 等几分钟再试；走代理的话，多半是代理出口 IP 被限"
        status == 401 || status == 403 -> "这颗源要先登录，或者登录凭证过期了 🔑"
        status != null && status >= 500 -> "源站自己出了点状况 🛠 跟你没关系，过会儿再来"
        raw.contains("not logged in", ignoreCase = true) -> "这颗源要先登录才能搜索 🔑"
        else -> "这次没能取到内容 🥲 稍后再试，或换一颗源"
    }

    return buildString {
        append(human)
        append("（技术细节：").append(raw)
        if (server != null) append(" · 服务端 ").append(server.code).append(": ").append(server.message)
        append("）")
    }
}
