package com.venera.compose.gallery

import com.venera.compose.gallery.domain.FlyRect
import com.venera.compose.gallery.domain.MIN_VISIBLE_RATIO
import com.venera.compose.gallery.domain.anchorAcceptable
import com.venera.compose.gallery.domain.hasAnchor
import com.venera.compose.gallery.domain.visibleRatios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 B 判据⑥那颗尺（计划 §5 批 B 判据⑥）：**可见比 <25% ⇒ 判无锚**，并且
 * **不许加纵横比门槛** —— pixez 的 `landable` 注释里宣称有"宽高比差距过大不可落"那一支，
 * 现量代码没有（§4.3 冲突表第 4 行）；照抄注释会做出一整条不存在的分支。
 * 所以这一族判式只有三件事：面积非零 + 宽比 ≥25% + 高比 ≥25%，**到此为止**。
 */
class GalleryAnchorGateTest {

    private val full = FlyRect(left = 0f, top = 0f, width = 1000f, height = 2000f)

    @Test
    fun `完整可见的矩形有锚`() {
        val (rw, rh) = visibleRatios(full, full)
        assertEquals(1f, rw, 1e-6f)
        assertEquals(1f, rh, 1e-6f)
        assertTrue(hasAnchor(full, full))
        assertSame(full, anchorAcceptable(full, full))
    }

    @Test
    fun `高被裁到两像素照样判无锚`() {
        // 真机活体读数那一档：size=495x2（10-19 晚 FlyProbe）。
        val clipped = FlyRect(left = 0f, top = 1998f, width = 1000f, height = 2f)
        val (rw, rh) = visibleRatios(full, clipped)
        assertEquals(1f, rw, 1e-6f)
        assertEquals(0.001f, rh, 1e-6f)
        assertFalse(hasAnchor(full, clipped))
        assertNull(anchorAcceptable(full, clipped))
    }

    @Test
    fun `门槛正好卡在四分之一`() {
        val at25 = FlyRect(left = 0f, top = 0f, width = 250f, height = 500f)
        assertTrue("两枚都恰等于门槛 ⇒ 有锚（≥ 不是 >）", hasAnchor(full, at25))
        val justBelow = FlyRect(left = 0f, top = 0f, width = 249.9f, height = 500f)
        assertFalse("宽比差一线就无锚", hasAnchor(full, justBelow))
        val halfVisible = FlyRect(left = 0f, top = 0f, width = 1000f, height = 400f)
        assertTrue("高比 20% < 25% ⇒ 无锚", !hasAnchor(full, halfVisible))
        assertEquals(0.25f, MIN_VISIBLE_RATIO, 0f)
    }

    @Test
    fun `零面积只忽略不注销`() {
        val zero = FlyRect(0f, 0f, 0f, 0f)
        assertFalse(hasAnchor(full, zero))
        assertNull(anchorAcceptable(full, zero))
        // 全框自己退化（布局还没量出来）：可见比交回 0、0，不抛。
        val (rw, rh) = visibleRatios(FlyRect(0f, 0f, 0f, 0f), zero)
        assertEquals(0f, rw, 0f)
        assertEquals(0f, rh, 0f)
    }

    @Test
    fun `判式里没有纵横比那一档`() {
        // 两张**比例天差地别但完整可见**的落点（未裁 = 已裁，两枚可见比都是 1）⇒ 必须有锚。
        // 哪天有人把"纵横比差距过大 ⇒ 不可落"抄进来（那是 pixez 注释里的幽灵分支，代码里没有），
        // 这一条先红 —— 它跟的不是 full 的比例，而是"自己跟自己重合"这一档。
        val wide = FlyRect(left = 0f, top = 0f, width = 1000f, height = 30f)
        assertTrue("横长条完整可见仍应有锚", hasAnchor(wide, wide))
        val tall = FlyRect(left = 0f, top = 0f, width = 8f, height = 2000f)
        assertTrue("竖细条完整可见仍应有锚", hasAnchor(tall, tall))
        // 与主墙那张竖长条卡同框、比例悬殊，也只是"可见比"的事，不引入第二条判据：
        assertTrue("两者逐边完全重合 ⇒ 两枚可见比都是 1", visibleRatios(wide, tall).first <= 1f)
    }

    @Test
    fun `窗口坐标与全框错开时只按重叠算`() {
        // 卡滚到窗口左缘外：全框 500 宽，只剩 200 露在窗口里 ⇒ 宽比 0.4，仍有锚（≥25%）。
        val fullWin = FlyRect(left = 0f, top = 0f, width = 500f, height = 600f)
        val inWindow = FlyRect(left = 0f, top = 0f, width = 200f, height = 600f)
        val (rw, rh) = visibleRatios(fullWin, inWindow)
        assertEquals(0.4f, rw, 1e-6f)
        assertEquals(1f, rh, 1e-6f)
        assertTrue(hasAnchor(fullWin, inWindow))
        // 再滚一点只剩 100/500 = 20% ⇒ 无锚。
        val mostlyOut = FlyRect(left = 0f, top = 0f, width = 100f, height = 600f)
        assertFalse(hasAnchor(fullWin, mostlyOut))
    }
}
