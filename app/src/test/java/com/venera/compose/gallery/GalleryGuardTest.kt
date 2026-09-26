package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryGuard
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 画廊侧遮罩判定的口径锁。
 *
 * 为什么值得单测：yande.re 实测**人气前 120 条里 e=84 / q=30 / s=6**，
 * Danbooru 月榜 200 条里 **e=85 / s=75 / q=39 / g=1**，
 * 也就是这两页天然就是成人内容页。判据写歪一格，要么把应用里现成的
 * 「成人内容处理」开关变成摆设，要么把用户显式加的黑名单变成摆设 —— 两个都是假开关。
 */
class GalleryGuardTest {

    private fun post(rating: String) = GalleryPost(site = GallerySite.YANDERE, id = 1, rating = rating)

    @Test
    fun `站方安全档永远可见`() {
        assertEquals(GalleryGuard.VISIBLE, GalleryGuard.maskStateFor(post("s"), "OFF", false))
        assertEquals(GalleryGuard.VISIBLE, GalleryGuard.maskStateFor(post("s"), "BLUR", false))
        assertEquals(GalleryGuard.VISIBLE, GalleryGuard.maskStateFor(post("s"), "HIDE", false))
    }

    @Test
    fun `explicit 与 questionable 同档处理`() {
        for (rating in listOf("e", "q")) {
            assertEquals(GalleryGuard.VISIBLE, GalleryGuard.maskStateFor(post(rating), "OFF", false))
            assertEquals(GalleryGuard.BLURRED, GalleryGuard.maskStateFor(post(rating), "BLUR", false))
            assertEquals(GalleryGuard.HIDDEN, GalleryGuard.maskStateFor(post(rating), "HIDE", false))
        }
    }

    @Test
    fun `未知分级值按成人内容处理而不是放行`() {
        // 站方将来加新值（或返回空串）时宁可打码：静默放行 = 把守卫关掉而没人知道。
        assertEquals(GalleryGuard.BLURRED, GalleryGuard.maskStateFor(post("x"), "BLUR", false))
        assertEquals(GalleryGuard.BLURRED, GalleryGuard.maskStateFor(post(""), "BLUR", false))
    }

    @Test
    fun `Danbooru 的 general 档算安全`() {
        // `g` 是接 Danbooru 才多出的一档（yande.re 只有 s/q/e）。
        // 沿用第一轮 `rating != "s"` 会把这档最干净的内容**误打码** —— 那是错打码方向，
        // 但同样是守卫判据失真，要有测试钉住。
        val g = GalleryPost(site = GallerySite.DANBOORU, id = 2, rating = "g")
        assertEquals(GalleryGuard.VISIBLE, GalleryGuard.maskStateFor(g, "BLUR", false))
        assertEquals(GalleryGuard.VISIBLE, GalleryGuard.maskStateFor(g, "HIDE", false))
    }

    @Test
    fun `用户黑名单排在分级模式之前`() {
        // 分级模式关掉时，用户自己拉的黑名单照样要生效 —— 否则一个开关管两件事。
        assertEquals(GalleryGuard.HIDDEN, GalleryGuard.maskStateFor(post("s"), "OFF", true))
        // BLUR 模式下黑名单也不是"打个码就完"：黑名单语义是剔除。
        assertEquals(GalleryGuard.HIDDEN, GalleryGuard.maskStateFor(post("e"), "BLUR", true))
    }
}
