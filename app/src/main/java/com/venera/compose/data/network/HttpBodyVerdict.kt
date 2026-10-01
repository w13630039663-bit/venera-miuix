package com.venera.compose.data.network

/**
 * 站方回了非 2xx。异常文案里只放「方法 + 去掉查询串的地址 + 状态码」——
 * 查询串可能挂着过盾票据与临时签名，而那句会流进日志与界面文案。
 */
class HttpRejectedException(val code: Int, method: String, urlPath: String) :
    Exception("$method $urlPath 返回 HTTP $code")

/**
 * 「这一笔 HTTP 响应算不算成功」的唯一判据。
 *
 * 存在的理由：[VeneraNetworkClient] 的 `get`/`post`/`downloadBytes` 曾经不看状态码，
 * 把 403 的 Cloudflare 挑战页当正文交回上游 —— 漫画源那边表现为「无结果」或一句 gson 错
 * （真相是源被封），画廊另存那边更坏：一段 HTML 以非空正文通过 `bytes.isEmpty()` 那道关，
 * 落盘成一个叫 `.jpg` 的网页，而提示说的是「已保存」。
 * 本仓其余每条 HTTP 路早就判了状态（`DownloadManager`、[com.venera.compose.data.network.VeneraImageFetcher]、
 * `ImagePipelinePolicy`、`AppUpdateChecker`、`WebDavClient`），这一处是漏的。
 *
 * 判据刻意做成纯函数（只吃状态码整数）：与被判的那笔请求无关，也因此能脱离 gradle 单跑。
 * 用例见 `HttpBodyVerdictTest`。
 */
object HttpBodyVerdict {

    /**
     * @param read 只在 2xx 时才被调用 —— 非 2xx 时正文压根不该读进来
     *   （`downloadBytes` 那条路上它可能是几百 MB 的原片）。
     */
    fun <T> body(code: Int, method: String, url: String, read: () -> T): T {
        // 与 OkHttp 的 Response.isSuccessful 同一条区间（200..299），包括 204：
        // 空正文是站方如实说的"没有内容"，不是失败。
        if (code !in 200..299) throw HttpRejectedException(code, method, url.substringBefore('?'))
        return read()
    }
}
