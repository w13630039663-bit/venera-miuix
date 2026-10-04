package com.venera.compose.components

import androidx.compose.ui.geometry.Rect
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「这张卡此刻到底被没被自有 chrome 压住」这条判据的用例。
 *
 * 它决定 `GalleryFlyIn.capture` 要不要为这一次截帧把 chrome 收成透明：
 * 判宽了 = 每次点卡都闪一下底栏；判窄了 = 那条带继续被截进飞行体（本轮修的那个症状）。
 * 两个方向都是看得见的错，所以四角与贴边都得钉住。
 */
class ChromeCaptureSuppressTest {

    @After
    fun tearDown() {
        ChromeCaptureSuppress.forgetBand(CHROME_BAND_BOTTOM_BAR)
        ChromeCaptureSuppress.forgetBand(CHROME_BAND_TOP_BAR)
    }

    private fun band(left: Float, top: Float, right: Float, bottom: Float) {
        ChromeCaptureSuppress.reportBand(CHROME_BAND_BOTTOM_BAR, Rect(left, top, right, bottom))
    }

    @Test
    fun `下沿探进底栏一像素就算压住`() {
        band(0f, 2200f, 1080f, 2376f)
        assertTrue(ChromeCaptureSuppress.obstructs(Rect(100f, 1500f, 900f, 2201f)))
    }

    @Test
    fun `正好贴边不算压住`() {
        band(0f, 2200f, 1080f, 2376f)
        // 卡的下沿落在栏的上沿上：那一行像素还是卡的，不该为此闪一下 chrome。
        assertFalse(ChromeCaptureSuppress.obstructs(Rect(100f, 1500f, 900f, 2200f)))
    }

    @Test
    fun `整张卡在底栏下方`() {
        band(0f, 2200f, 1080f, 2376f)
        assertFalse(ChromeCaptureSuppress.obstructs(Rect(100f, 2400f, 900f, 2600f)))
    }

    @Test
    fun `整张卡在底栏上方`() {
        band(0f, 2200f, 1080f, 2376f)
        assertFalse(ChromeCaptureSuppress.obstructs(Rect(100f, 400f, 900f, 1200f)))
    }

    @Test
    fun `横向错开就不算压住`() {
        // 悬浮胶囊两侧有边距，靠边那半屏其实没东西压着 —— 判宽了会白闪一下。
        band(60f, 2200f, 1020f, 2376f)
        assertFalse(ChromeCaptureSuppress.obstructs(Rect(0f, 2100f, 60f, 2300f)))
    }

    @Test
    fun `零面积的栏不参与判定`() {
        // 一个项被撤掉之前的最后一次布局回调给的就是 0×0（与本仓 `noteCardBounds` 同一条噪声）。
        // 照单收下的话，零高的栏与任何跨到那一行的卡都算"相交"，于是每次点卡都白闪一下。
        band(0f, 2200f, 1080f, 2200f)
        assertFalse(ChromeCaptureSuppress.obstructs(Rect(100f, 1500f, 900f, 2300f)))
        ChromeCaptureSuppress.forgetBand(CHROME_BAND_BOTTOM_BAR)
        band(0f, 2200f, 0f, 2376f)
        assertFalse(ChromeCaptureSuppress.obstructs(Rect(100f, 1500f, 900f, 2300f)))
    }

    @Test
    fun `撤了登记的栏不再参与判定`() {
        band(0f, 2200f, 1080f, 2376f)
        assertTrue(ChromeCaptureSuppress.obstructs(Rect(100f, 1500f, 900f, 2300f)))
        ChromeCaptureSuppress.forgetBand(CHROME_BAND_BOTTOM_BAR)
        assertFalse(ChromeCaptureSuppress.obstructs(Rect(100f, 1500f, 900f, 2300f)))
    }

    @Test
    fun `两块栏任一命中即算压住`() {
        ChromeCaptureSuppress.reportBand(CHROME_BAND_TOP_BAR, Rect(0f, 0f, 1080f, 260f))
        band(0f, 2200f, 1080f, 2376f)
        assertTrue(ChromeCaptureSuppress.obstructs(Rect(100f, 100f, 900f, 900f)))
        assertTrue(ChromeCaptureSuppress.obstructs(Rect(100f, 2100f, 900f, 2300f)))
        assertFalse(ChromeCaptureSuppress.obstructs(Rect(100f, 900f, 900f, 1200f)))
    }
}
