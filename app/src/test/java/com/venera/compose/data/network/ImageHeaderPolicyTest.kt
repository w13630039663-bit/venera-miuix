package com.venera.compose.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ImageHeaderPolicy.headersFor` 的 host 匹配口径。
 *
 * 为什么值得单测：2026-09-26 真机「Gelbooru 搜得出图、屏上全是灰卡」
 * 的根因就在这个函数里 —— 旧 `hostOf` 多写了一步 `substringBefore("?", "")`，
 * 而 host 在被 `substringBefore("/")` 截过之后**不可能再含 `?`**，
 * 那一步于是对所有正常 URL 都返回 `""` → null → `headersFor` 恒回空表，
 * 导致**整张防盗链表从来没生效过**（带 Referer 的站全部取不到图，
 * 而症状是"解不出图"，不是"请求被拒"，极难定位）。
 *
 * 下面既钉住 Gelbooru 这一条，也钉住"别的表项同样要生效" ——
 * 只测新加的那一条，挡不住这个 bug 下次在别处复发。
 */
class ImageHeaderPolicyTest {

    @Test
    fun `Gelbooru 图片三档都要带 Referer`() {
        for (u in listOf(
            "https://img4.gelbooru.com/thumbnails/94/99/thumbnail_x.jpg",
            "https://img4.gelbooru.com/samples/37/3b/sample_x.jpg",
            "https://img4.gelbooru.com/images/6e/e9/x.mp4",
        )) {
            val h = ImageHeaderPolicy.headersFor(u)
            assertTrue("$u 缺 Referer，实际=$h", h.containsKey("Referer"))
            assertEquals("https://gelbooru.com/", h["Referer"])
        }
    }

    @Test
    fun `不带路径只有 query 的 URL 也能认出 host`() {
        // 这条钉住旧 bug 的另一半：host 后面直接跟 `?`、没有 `/` 的形态。
        val h = ImageHeaderPolicy.headersFor("https://i.nhentai.net?x=1")
        assertTrue("缺 Referer，实际=$h", h.containsKey("Referer"))
    }

    @Test
    fun `原本就有的其它站表项同样要生效`() {
        // 只测新加的那条挡不住复发 —— 这些是表里**原本就有**的，
        // 它们一起过，才能说明"匹配逻辑"本身是通的。
        assertTrue(ImageHeaderPolicy.headersFor("https://cdn.donmai.us/original/x.jpg")
            .containsKey("User-Agent"))
        assertTrue(ImageHeaderPolicy.headersFor("https://i.nhentai.net/galleries/x/1.jpg")
            .containsKey("Referer"))
        assertTrue(ImageHeaderPolicy.headersFor("https://mangadex.org/x.jpg")
            .containsKey("Referer"))
    }

    @Test
    fun `认不出的 URL 回空表而不是崩`() {
        assertTrue(ImageHeaderPolicy.headersFor("").isEmpty())
        assertTrue(ImageHeaderPolicy.headersFor("not-a-url").isEmpty())
        assertTrue(ImageHeaderPolicy.headersFor("https://example.com/x.jpg").isEmpty())
    }
}
