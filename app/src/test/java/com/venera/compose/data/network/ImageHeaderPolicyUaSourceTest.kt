package com.venera.compose.data.network

import com.venera.compose.gallery.data.GelbooruClient
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 2026-10-03 字面量收口带出的两条不变量。单独成文件而不是塞进 `ImageHeaderPolicyTest`：
 * 后者判的是 host 匹配口径，这里判的是**表里那两枚 UA 串的同源性与逐字值**，是两件事。
 *
 * 收口前同一条产品串在 `ImageHeaderPolicy`（`donmai.us` 与 `gelbooru.com` 两颗 host）与
 * `GelbooruClient.API_USER_AGENT` 各写一遍，而表里那句注释自陈「UA 一并带上只为与接口侧保持一致」
 * —— 这条不变量当时只有注释级证据。改歪任一处的后果不是编译错误，是那一站整站取不到图
 * （403 / 302 到防盗链页，读起来像地域封禁，很容易顺着网络出口那条死路查下去）。
 */
class ImageHeaderPolicyUaSourceTest {

    @Test
    fun `产品串在表内与接口侧是同一枚常量`() {
        assertEquals("Venera/1.0 (Android)", UserAgentPolicy.APP_USER_AGENT)
        assertEquals(UserAgentPolicy.APP_USER_AGENT, GelbooruClient.API_USER_AGENT)
        assertEquals(
            UserAgentPolicy.APP_USER_AGENT,
            ImageHeaderPolicy.headersFor("https://img4.gelbooru.com/images/6e/e9/x.mp4")["User-Agent"],
        )
        assertEquals(
            UserAgentPolicy.APP_USER_AGENT,
            ImageHeaderPolicy.headersFor("https://cdn.donmai.us/original/x.jpg")["User-Agent"],
        )
    }

    @Test
    fun `E-Hentai 家族四颗 host 共用同一枚桌面串`() {
        // 这条串在表里原本逐字抄了四遍。四颗 host 同属一家族、站方按 UA 放行，
        // 改歪任一颗就是「封面出、内页不出」那种半残读数。
        val desktop = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        for (u in listOf(
            "https://ehgt.org/t/ab/cdef.webp",
            "https://e-hentai.org/s/abc-1",
            "https://exhentai.org/s/abc-1",
            "https://hath.network/h/abc",
        )) {
            assertEquals("四颗 host 的桌面串不该各写一份：$u", desktop, ImageHeaderPolicy.headersFor(u)["User-Agent"])
        }
        // ⚠️ `engine/JsHttpHandler.kt:62` 那颗是 Chrome/119：JS 侧源脚本走另一条链路、另一个值，
        // 不参与收口。它要是出现在这张表里，就说明有人把两枚不同的值合掉了。
    }
}
