package com.venera.compose.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WideScreenPolicyTest {
    @Test fun phoneBandNeverGetsAWidthCap() {
        // 手机档必须返回 null：一旦返回数值，顶栏/底栏/控制岛都会被悄悄改掉。
        assertNull(wideScreenChromeMaxWidth(360.dp))
        assertNull(wideScreenChromeMaxWidth(411.dp))
        assertNull(wideScreenChromeMaxWidth(600.dp))
    }

    @Test fun wideBandIsCappedByMasterGlassBarContract() {
        // master: min(_kGlassBarMaxWidth = 540, 窗口宽 - 24*2)，阈值 changePoint = 600。
        assertEquals(540.dp, wideScreenChromeMaxWidth(601.dp))
        assertEquals(540.dp, wideScreenChromeMaxWidth(1280.dp))
        assertEquals(540.dp, wideScreenChromeMaxWidth(2000.dp))
    }

    /**
     * 「窗口宽 - 48」这一项在 >600dp 档里永远赢不了 540（600-48=552 > 540），
     * 也就是说 master 原式在手机上其实退化成了定宽 540。这里把它钉住，
     * 免得后人以为宽窗变窄时收口会跟着缩 —— 不会，除非同时改阈值。
     */
    @Test fun windowTermCannotBeatTheCapAboveTheThreshold() {
        for (width in 601..2000 step 37) {
            assertEquals(540.dp, wideScreenChromeMaxWidth(width.dp))
        }
    }
}
