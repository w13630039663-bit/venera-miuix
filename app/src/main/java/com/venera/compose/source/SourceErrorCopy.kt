package com.venera.compose.source

import kotlinx.coroutines.CancellationException

/*
 * 源 / 网络异常 → 屏上文案的**唯一出口**。
 *
 * 为什么要有这一层（2026-10-14 真机读数）：源脚本的失败 message 是写给开发者看的
 * （"Invalid status code: 210"、"Login expired"、"未登录"），屏上直接出现它就是实现
 * 笔记漏到界面上 —— 用户看不懂 210 是什么，只知道自己被拦住了。
 *
 * 这一层的三条硬规矩：
 *  1. **绝不上屏原始 message**。翻不出来时给一句通用的，原文只进日志。
 *     认不出来的串照样能是"代码"（异常类名、URL、正则），"原样保留便于排查"那条旧口径
 *     在真机上换来的是一屏黑话，排查靠日志，不靠用户截图。
 *  2. **先说人话，再说下一步**。每条都要能回答"为什么"和"我该干什么"。
 *  3. **不甩锅**。源站的问题说成源站的问题（"源站自己出了点状况，跟你没关系"），
 *     用户设备的问题给一条能自检的动作（"看一眼连接"）。
 */

/** 源站明确要求登录/登录态失效时，源脚本里出现的原话（各源用词不统一，全收）。 */
private val LOGIN_HINTS = listOf(
    "login expired", "please login", "not logged", "unauthorized",
    "未登录", "未登入", "請先登錄", "请先登录", "請重新登錄", "请重新登录", "登錄失敗", "登录失败",
)

/** 请求太频繁 / 被限流。哔咔与拷贝用非标准的 210 表示"访问过于频繁"。 */
private val RATE_LIMIT_HINTS = listOf(
    "too frequent", "too many requests", "rate limit",
    "过于频繁", "太频繁", "访问频繁", "retry",
)

/** 源脚本解析不动源站新页面（源站改版）。 */
private val PARSE_HINTS = listOf(
    "invalid data format", "cannot read", "is not a function", "undefined is not",
    "接口未返回", "invalid page", "click tag error", "id error",
)

/** 网络层连不上（DNS / 连接被拒 / 连接中断）。 */
private val NETWORK_HINTS = listOf(
    "unable to resolve host", "unknownhost", "connectexception", "sockettimeout",
    "socketexception", "connection reset", "connection refused", "econnrefused",
    "failed to connect", "network is unreachable", "no address associated",
)

/** 超时。 */
private val TIMEOUT_HINTS = listOf("timeout", "timed out", "超时")

/** 内容不存在 / 被删。 */
private val NOT_FOUND_HINTS = listOf("404", "not found", "找不到", "不存在")

/** 取消是控制流，不是错误。见 [isCancellation] 与各处 rethrow。 */
private val CANCELLATION_HINTS = listOf(
    "left the composition", "was cancelled", "job was cancelled", "scope was cancelled",
    "cancellationexception",
)

/**
 * 这次异常是不是**取消**（页面走了 / 切了源 / 按了刷新）。
 *
 * 取消必须原样抛回去，不能变成一句屏上文案 —— 真机上那条
 * 「The coroutine scope left the composition」就是这么漏出来的：
 * 内容 effect 的 `catch (e: Exception)` 把 `CancellationException` 一起收了
 * （它确实继承自 Exception），于是"用户自己切走了"被读成"加载失败"。
 */
fun isCancellation(t: Throwable?): Boolean {
    var cursor = t
    var guard = 0
    while (cursor != null && guard < 8) {
        if (cursor is CancellationException) return true
        val message = cursor.message.orEmpty().lowercase()
        if (CANCELLATION_HINTS.any { message.contains(it) }) return true
        cursor = cursor.cause
        guard++
    }
    return false
}

/**
 * 把源 / 网络异常翻成屏上文案。
 *
 * @param raw 源脚本或网络层给的原始 message（**不会被原样上屏**，只用来判类型）。
 * @param loginable 这颗源是否支持登录（`SourceAccountInfo.hasAccount`）。
 *   为 true 时 401/403 判成"要先登录" —— 这类源拒绝访问十有八九就是这个原因，
 *   说成"源站防护/网络波动"等于把用户往错误的方向指（真机读数：包子漫画）。
 */
fun humanizeSourceError(raw: String?, loginable: Boolean = false): String {
    val msg = raw.orEmpty().lowercase()
    return when {
        // 取消：不该走到这儿（调用方必须先 rethrow）。真漏进来也只给通用句，
        // 绝不能把 "left the composition" 这种内部话说给用户听。
        CANCELLATION_HINTS.any { msg.contains(it) } -> GENERIC

        LOGIN_HINTS.any { msg.contains(it) } -> "登录状态过期了 🔑 到「漫画源」里重新登录一下就好"

        // 403 / 401：先看这颗源支不支持登录。支持 → 大概率就是要登录；
        // 不支持 → 只能是源站自己拦了，别把锅甩给用户的网络。
        msg.contains("403") || msg.contains("401") ->
            if (loginable) {
                "这个源要先登录才能看 🔒 到「漫画源」里登录一下，回来就能用了"
            } else {
                "这个源暂时没放行这次请求 🚧 过一会儿再试，或换一颗源"
            }

        // 210 是哔咔/拷贝自己的"访问过于频繁"码，和 429 归一类。
        msg.contains("210") || msg.contains("429") || RATE_LIMIT_HINTS.any { msg.contains(it) } ->
            "访问得有点急，源站让歇一会儿 ⏳ 等几分钟再来"

        // 5xx：源站自己的问题，明确告诉用户"不怪你"。
        // ⚠️ 必须排在 NETWORK / TIMEOUT 之前：`504 Gateway Timeout` 同时含 "timeout"，
        // 顺序反了就会被读成"用户网络慢"，把源站的锅甩回给用户。
        msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504") ||
            msg.contains("server error") -> "源站自己出了点状况 🛠 跟你没关系，过会儿再来"

        NOT_FOUND_HINTS.any { msg.contains(it) } -> "这个源可能改版了，内容找不到了 🧩 换一颗源试试"

        NETWORK_HINTS.any { msg.contains(it) } -> "网络好像没连上 📡 看一眼连接，再试一次"

        TIMEOUT_HINTS.any { msg.contains(it) } -> "等了好久没等到回应 😴 检查下网络，或者稍后再试"

        PARSE_HINTS.any { msg.contains(it) } -> "这个源跟不上源站的改版了 🧩 换一颗源，或等它更新"

        else -> GENERIC
    }
}

/** [humanizeSourceError] 的 Throwable 版本；取消按"不该显示"处理，给通用句。 */
fun humanizeSourceError(t: Throwable?, loginable: Boolean = false): String =
    humanizeSourceError(t?.message, loginable)

/** 翻不出来时的兜底：短、中性、给出下一步，不含任何内部信息。 */
const val GENERIC = "这次没能取到内容 🥲 稍后再试，或换一颗源"
