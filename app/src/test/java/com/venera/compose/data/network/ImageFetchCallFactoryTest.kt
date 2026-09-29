package com.venera.compose.data.network

import okhttp3.Request
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 图片请求的 `ImageFetchTag` 盖章判据。
 *
 * 2026-09-29 把普通图片（画廊、封面）从 `VeneraImageFetcher` 挪给 Coil 自带的
 * `OkHttpNetworkFetcherFactory` 之后，这条链上**再没有别的地方**会给请求打这个标记
 * （Coil 自己构造的 `okhttp3.Request` 只带 url/method/headers，`extras` 不会映射成 tag）。
 * 而两个拦截器都只认它：
 * - [HostCircuitBreakerInterceptor]：没有标记就把 host 拉黑 60 秒（满屏红）；
 * - [CloudflareBypassInterceptor]：没有标记就可能整屏弹人机验证（画廊明确要求永不弹）。
 * 所以盖章这件事本身要有用例锁着，而不是靠"看代码里有"。
 */
class ImageFetchCallFactoryTest {

    @Test
    fun `普通图片请求盖上取流标记`() {
        val stamped = Request.Builder()
            .url("https://img3.gelbooru.com/images/aa/bb/aabbcc.gif")
            .build()
            .withImageFetchTag()
        assertNotNull(stamped.tag(ImageFetchTag::class.java))
    }

    @Test
    fun `已有标记的请求原样返回`() {
        val original = Request.Builder()
            .url("https://files.yande.re/jpeg/1269641/11ea8a.jpg")
            .tag(ImageFetchTag::class.java, ImageFetchTag())
            .build()
        assertSame(original, original.withImageFetchTag())
    }
}
