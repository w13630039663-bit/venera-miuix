package com.venera.compose.gallery

import com.venera.compose.gallery.domain.coverSourceRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 飞行体绘制时"源位图取哪一块"的判据（[coverSourceRect]）。
 *
 * 每一条用例都对着真机上的一个形态写的，出处见 `gallery-hero-transition-and-artist-profile-2026-10-01.md` §十三
 * 与 `GalleryFlyGeometry` 的头注：
 * - 墙上卡片：比例被夹在 0.4~2.5，而落点框按原图比例 —— 夹过的那一档就会不同构；
 * - 首页预览行：封面框固定 124×170，与图上真实比例无关。
 */
class GalleryFlyGeometryTest {

    /** 两端比例一致（墙上卡片的大多数）：取的就是**整张**位图。这条守的是"与老写法逐像素等价"。 */
    @Test
    fun `比例一致时取整张不裁`() {
        val rect = coverSourceRect(imageWidth = 400, imageHeight = 600, dstWidth = 200f, dstHeight = 300f)
        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertEquals(400, rect.width)
        assertEquals(600, rect.height)
    }

    /** 四舍五入带来的 ±1px 不能变成"悄悄裁掉一条边"：比例一致时宽高必须严格等于源尺寸。 */
    @Test
    fun `比例一致但不整除时也不裁边`() {
        // 1024 / 1536 = 0.6667；目标框 337.3 / 506 = 0.6667（同一比例、非整数）
        val rect = coverSourceRect(imageWidth = 1024, imageHeight = 1536, dstWidth = 337.3f, dstHeight = 506f)
        assertEquals(1024, rect.width)
        assertEquals(1536, rect.height)
        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
    }

    /** 预览行点横图：封面框是竖的（124×170），落点框是横的 → 该裁的是**上下**，左右取满。 */
    @Test
    fun `目标框比源更宽时裁上下居中`() {
        val rect = coverSourceRect(imageWidth = 400, imageHeight = 600, dstWidth = 400f, dstHeight = 200f)
        assertEquals(400, rect.width)
        assertEquals(200, rect.height)
        assertEquals(0, rect.left)
        assertEquals(200, rect.top)
    }

    /** 预览行点竖图：封面框与落点框都是竖的，但比例不同 → 该裁的是**左右**，上下取满。 */
    @Test
    fun `目标框比源更高时裁左右居中`() {
        val rect = coverSourceRect(imageWidth = 600, imageHeight = 400, dstWidth = 300f, dstHeight = 400f)
        assertEquals(300, rect.width)
        assertEquals(400, rect.height)
        assertEquals(150, rect.left)
        assertEquals(0, rect.top)
    }

    /** 墙上卡片被夹到 0.4~2.5 之后与落点框不同构的那一档：不许越出源位图，也不许取到 0 面积。 */
    @Test
    fun `取块永远落在源位图之内且不为空`() {
        val cases = listOf(
            Triple(900, 300, 300f to 900f), // 超宽图（被夹到 2.5）落到真实比例的竖框
            Triple(300, 900, 900f to 300f), // 超长竖图落到真实比例的横框
            Triple(1, 1, 500f to 500f),
            Triple(1000, 1, 1f to 1000f),
        )
        for ((sw, sh, dst) in cases) {
            val rect = coverSourceRect(sw, sh, dst.first, dst.second)
            assertTrue("width=${rect.width} 越界（源 $sw）", rect.width in 1..sw)
            assertTrue("height=${rect.height} 越界（源 $sh）", rect.height in 1..sh)
            assertTrue("left 越界：$rect", rect.left >= 0 && rect.left + rect.width <= sw)
            assertTrue("top 越界：$rect", rect.top >= 0 && rect.top + rect.height <= sh)
        }
    }

    /** 退化输入：框还没量出来（0×0）时交回整张，不崩、也不返回空块。 */
    @Test
    fun `落点为空时交回整张位图`() {
        val zero = coverSourceRect(400, 600, 0f, 0f)
        assertEquals(400, zero.width)
        assertEquals(600, zero.height)
        // 负值（理论上不该出现，但这一层不该是崩溃点）
        val negative = coverSourceRect(400, 600, -10f, 300f)
        assertEquals(400, negative.width)
    }

    /** 位图尺寸坏掉时也不许交回 0 面积 —— 调用侧拿它去 `drawImage` 会直接抛。 */
    @Test
    fun `位图尺寸坏掉时至少给出 1x1`() {
        val rect = coverSourceRect(0, 0, 200f, 300f)
        assertEquals(1, rect.width)
        assertEquals(1, rect.height)
    }

    /** 复用同一条算法量一个真样本（yande.re 常见的竖图 850×1200 → 一屏宽的画面框）。 */
    @Test
    fun `真样本竖图比例一致时整张不裁`() {
        // 画面框：screenHorizontal 两侧各留 16dp，1080 宽屏 → 1048 宽；按比例得高 1479.5
        val rect = coverSourceRect(imageWidth = 850, imageHeight = 1200, dstWidth = 1048f, dstHeight = 1479.5f)
        assertEquals(850, rect.width)
        assertEquals(1200, rect.height)
    }
}
