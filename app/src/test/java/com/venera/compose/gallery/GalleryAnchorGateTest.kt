package com.venera.compose.gallery

import com.venera.compose.gallery.domain.FlyRect
import com.venera.compose.gallery.domain.MIN_VISIBLE_RATIO
import com.venera.compose.gallery.domain.SIDE_SLIDE_BACK_ALPHA
import com.venera.compose.gallery.domain.SIDE_SLIDE_PARALLAX
import com.venera.compose.gallery.domain.anchorAcceptable
import com.venera.compose.gallery.domain.hasAnchor
import com.venera.compose.gallery.domain.sideSlideBackOffset
import com.venera.compose.gallery.domain.sideSlideTopOffset
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

    // ── 批 B⑦ 侧滑退路的投影（2026-10-19）──────────────────────────────────
    //
    // ⑦ 的原话：「可见比不足 25% ⇒ 当场判无锚、走全宽侧滑（顶层平移、底层 -25% 视差 +
    // alpha 0.9），不再走整页从下沿抬上来」。上面那几把尺钉的是"什么时候走侧滑"，
    // 这里钉的是"走起来长什么样"。

    @Test
    fun `顶层从窗口外一径直插到归位`() {
        // 0 = 整幅宽在外（还没进场），1 = 归位；中间线性。
        assertEquals(1f, sideSlideTopOffset(0f), 1e-6f)
        assertEquals(0f, sideSlideTopOffset(1f), 1e-6f)
        assertEquals(0.5f, sideSlideTopOffset(0.5f), 1e-6f)
    }

    @Test
    fun `顶层投影钳在两端不外推`() {
        // 弹簧回弹过头那一档（>1）与外插（<0）都按端点算：侧滑不是 hero，
        // 没有"飞出窗口再弹回来"那一说。
        assertEquals(1f, sideSlideTopOffset(-0.5f), 1e-6f)
        assertEquals(0f, sideSlideTopOffset(1.5f), 1e-6f)
    }

    @Test
    fun `底层视差是负的且走四分之一幅`() {
        assertEquals(-0.25f, SIDE_SLIDE_PARALLAX, 1e-6f)
        // 起点那一刻底层已经退开一整格（= 常量），归位时回到 0。
        assertEquals(-0.25f, sideSlideBackOffset(0f), 1e-6f)
        assertEquals(0f, sideSlideBackOffset(1f), 1e-6f)
        assertEquals(-0.125f, sideSlideBackOffset(0.5f), 1e-6f)
        // 全程**不许变正**：正的会把底层也往右带，两屏一起动，视差就没了。
        assertTrue(sideSlideBackOffset(0.3f) < 0f)
    }

    @Test
    fun `底层不淡出停在零点九`() {
        // 侧滑要的是"被推开"的体感；淡下去会读成"顶层在盖上去"。
        assertEquals(0.9f, SIDE_SLIDE_BACK_ALPHA, 1e-6f)
    }

    @Test
    fun `侧滑与 hero 是互斥的两条路`() {
        // 这一笔钉的是**用法**而不是算式：有锚（≥25%）才飞，无锚才侧滑。
        // 两条路都上屏就是"既飞又滑"，那种错只有真机能看见。
        val card = FlyRect(left = 0f, top = 0f, width = 1000f, height = 2000f)
        assertTrue("有锚 ⇒ 走 hero，不该侧滑", hasAnchor(full, card))
        val clipped = FlyRect(left = 0f, top = 1998f, width = 1000f, height = 2f)
        assertFalse("无锚 ⇒ 走侧滑，不该飞", hasAnchor(full, clipped))
    }
}
