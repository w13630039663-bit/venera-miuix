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
 * Gelbooru 实测**四档都有**（`general` / `sensitive` / `questionable` / `explicit`），
 * 也就是这两页天然就是成人内容页。判据写歪一格，要么把应用里现成的
 * 「成人内容处理」开关变成摆设，要么把用户显式加的黑名单变成摆设 —— 两个都是假开关。
 *
 * ⚠️ 两站的分级**字形不同**（yande.re 单字母、Gelbooru 单词），
 * 而 [GalleryPost.isAdultMarked] 要同时吃两套 —— 下面两组用例分别钉住。
 */
class GalleryGuardTest {

    private fun post(rating: String) = GalleryPost(site = GallerySite.YANDERE, id = 1, rating = rating)

    private fun gelbooruPost(rating: String) =
        GalleryPost(site = GallerySite.GELBOORU, id = 1, rating = rating)

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
    fun `Gelbooru 的 general 与 sensitive 两档算安全`() {
        // ⚠️ Gelbooru 的分级是**单词**，与 yande.re 的单字母不是同一套字形。
        // 判据若只认 "s"，这一站最干净的两档会被**误打码**（错打码方向，
        // 但同样是守卫判据失真，要有测试钉住）。
        for (rating in listOf("general", "sensitive")) {
            val p = gelbooruPost(rating)
            assertEquals(GalleryGuard.VISIBLE, GalleryGuard.maskStateFor(p, "BLUR", false))
            assertEquals(GalleryGuard.VISIBLE, GalleryGuard.maskStateFor(p, "HIDE", false))
        }
    }

    @Test
    fun `Gelbooru 的 questionable 与 explicit 照常算成人`() {
        // 反向也要钉：单词那两档不能因为"只认单字母的成人档"而漏放。
        for (rating in listOf("questionable", "explicit")) {
            val p = gelbooruPost(rating)
            assertEquals(GalleryGuard.BLURRED, GalleryGuard.maskStateFor(p, "BLUR", false))
            assertEquals(GalleryGuard.HIDDEN, GalleryGuard.maskStateFor(p, "HIDE", false))
        }
    }

    @Test
    fun `用户黑名单排在分级模式之前`() {
        // 分级模式关掉时，用户自己拉的黑名单照样要生效 —— 否则一个开关管两件事。
        assertEquals(GalleryGuard.HIDDEN, GalleryGuard.maskStateFor(post("s"), "OFF", true))
        // BLUR 模式下黑名单也不是"打个码就完"：黑名单语义是剔除。
        assertEquals(GalleryGuard.HIDDEN, GalleryGuard.maskStateFor(post("e"), "BLUR", true))
    }
}
