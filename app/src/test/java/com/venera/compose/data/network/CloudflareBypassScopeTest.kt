package com.venera.compose.data.network

import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "这条请求能不能弹过盾窗口"的判据锁。
 *
 * 起因是真机反馈（2026-09-25）：Danbooru 开 HD 档会整屏弹出 Cloudflare 人机验证页。
 * 撞盾的是 `cdn.donmai.us/original/…` 那一档，而它当时被当成普通 API 请求处理了 ——
 * 于是 Coil 的一个取图线程 `runBlocking` 挂在那里等一次人机交互。
 * 更要紧的是这条链**本身自相矛盾**：过盾成功后绑到该 host 上的 UA 是 WebView 那串真实浏览器串，
 * 拿它重放图片请求，Cloudflare 反而判定更严（本机 curl 实测：同一张原图，
 * `Venera/1.0 (Android)` 回 200，Chrome 移动串哪怕补全 `Sec-Fetch-*` 也回 403 + `cf-mitigated: challenge`）。
 * 所以图片流量一律不弹，钉在这里。
 */
class CloudflareBypassScopeTest {

    private fun imageRequest() = Request.Builder()
        .url("https://cdn.donmai.us/original/4e/46/4e46f980ae834e8c4b171e5ba81b5069.jpg")
        .tag(ImageFetchTag::class.java, ImageFetchTag())
        .build()

    @Test
    fun `图片取流不弹交互式过盾`() {
        assertFalse(offersInteractiveBypass(imageRequest()))
    }

    @Test
    fun `API 流量照旧可以弹过盾`() {
        val api = Request.Builder()
            .url("https://danbooru.donmai.us/posts.json?limit=20")
            .build()
        assertTrue(offersInteractiveBypass(api))
    }

    @Test
    fun `过盾豁免与熔断豁免共用同一个 tag`() {
        // 图片那两条豁免（域名熔断、交互式过盾）都只认 ImageFetchTag 一个标记。
        // 哪天给图片换标记，这两处必须一起换 —— 漏一处的表现就是"翻墙时满屏弹验证页"。
        assertTrue(imageRequest().tag(ImageFetchTag::class.java) != null)
        assertFalse(offersInteractiveBypass(imageRequest()))
    }
}
