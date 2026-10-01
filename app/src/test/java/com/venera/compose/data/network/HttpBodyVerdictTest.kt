package com.venera.compose.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 「HTTP 这一笔到底算不算成功」的判据锁。
 *
 * 起因：[VeneraNetworkClient] 的 `get`/`post`/`downloadBytes` 三个取数口子当时**不看状态码**，
 * 把 403 的 Cloudflare 挑战页当正文原样交回上游。两条具体后果：
 * - 漫画源的解析器拿到一段 HTML，屏上呈现成「无结果」或一句 gson 错，真相是那个源被封了；
 * - 画廊另存把那段 HTML 当成图片落盘，用户相册里多出一个叫 `.jpg` 的网页，而提示说的是「已保存」。
 *
 * 而本仓其余每条 HTTP 路早就判了状态并 `use{}` 关响应（`DownloadManager`、
 * [com.venera.compose.data.network.VeneraImageFetcher]、`ImagePipelinePolicy`、
 * `AppUpdateChecker`、`WebDavClient`），不判的只有那一个类。所以这不是引入新口味，
 * 是补上本仓自己的既有约定。
 *
 * 本判据**刻意不碰 OkHttp**：只吃状态码整数，所以能脱离 gradle 单跑。
 */
class HttpBodyVerdictTest {

    private val challengeUrl = "https://cdn.donmai.us/sample/ab/cd/sample-abcd-1.jpg"

    @Test
    fun `非 2xx 必须抛而不是把正文交回上游`() {
        try {
            val handedBack = HttpBodyVerdict.body(403, "GET", challengeUrl) { "挑战页 HTML" }
            fail("403 不该把正文交回去，却交回了：$handedBack")
        } catch (expected: HttpRejectedException) {
            assertEquals(403, expected.code)
        }
    }

    @Test
    fun `2xx 原样交回正文`() {
        assertEquals("真图字节", HttpBodyVerdict.body(200, "GET", challengeUrl) { "真图字节" })
        // 204 也是成功：空正文是站方如实说的"没有内容"，不是失败。
        assertEquals("", HttpBodyVerdict.body(204, "GET", challengeUrl) { "" })
    }

    @Test
    fun `报错文案要说得出是哪个地址`() {
        val message = rejectMessage(500, "POST", "https://api.mangadex.org/manga/1234")
        assertTrue("文案里要有方法：$message", message.contains("POST"))
        assertTrue("文案里要有路径：$message", message.contains("api.mangadex.org/manga/1234"))
        assertTrue("文案里要有状态码：$message", message.contains("500"))
    }

    /**
     * 查询串里可能挂着过盾票据与临时签名（pixiv 的 `p=` 那类），一句会流进日志与
     * 界面文案的话不该带着它们。
     */
    @Test
    fun `报错文案不许带出查询串`() {
        val message = rejectMessage(403, "GET", "$challengeUrl?cf_clearance=SECRET&token=SECRET2")
        assertFalse("查询串漏出来了：$message", message.contains("?"))
        assertFalse("票据漏出来了：$message", message.contains("SECRET"))
        assertTrue("去掉查询串后路径要在：$message", message.contains("sample-abcd-1.jpg"))
    }

    private fun rejectMessage(code: Int, method: String, url: String): String =
        try {
            HttpBodyVerdict.body(code, method, url) { "" }
            error("这笔本该抛")
        } catch (e: HttpRejectedException) {
            requireNotNull(e.message)
        }
}
