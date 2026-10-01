package com.venera.compose.gallery

import com.venera.compose.gallery.ui.galleryCoverKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 画廊封面共享元素那对 key 的判据锁。
 *
 * 为什么值得单独钉：两端 key 必须是**同一个串**，差一个字共享元素就不触发，
 * 屏上只会各自淡入淡出 —— 而它看起来"像动画没做好"，不会报错（记忆
 * 「封面共享元素转场现状与硬约束」第 1 条）。所以把"用什么当身份"钉在这里。
 *
 * 关键一条：**不能用 `post.id`**。两站各自编号，同 id 是两张不同的图 ——
 * 撞 key 会让飞行体落到另一站的图上（`GalleryScreen.kt:1313` 那条「撞 key 会直接崩」
 * 是同一个事实的 LazyList 版本）。`uid` 带站键，不会撞。
 */
class GallerySharedTransitionTest {

    @Test
    fun `两站同 id 的两张图 key 必须不同`() {
        val yande = galleryCoverKey("yande.re:1234")
        val gelbooru = galleryCoverKey("gelbooru:1234")
        assertNotEquals("两站同 id 撞了 key：飞行会落到另一站的图上", yande, gelbooru)
    }

    @Test
    fun `uid 里带冒号不影响 key 的稳定`() {
        assertEquals(
            galleryCoverKey("yande.re:1234"),
            galleryCoverKey("yande.re:1234"),
        )
    }

    @Test
    fun `key 带画廊前缀不与漫画侧那套撞名`() {
        // 漫画侧用的是 ComicSharedTransition.coverKey(sourceKey, comicId)，形如 "cover-jm-123"。
        // 画廊这一串若与它同形，同一屏上两边就会互相认领飞行体。
        assertNotEquals("cover-jm-123", galleryCoverKey("jm:123"))
    }
}
