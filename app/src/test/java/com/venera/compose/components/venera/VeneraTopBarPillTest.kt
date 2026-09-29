package com.venera.compose.components.venera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 顶栏磨砂圆座的**能不能磨砂**判据。
 *
 * 单独抽出来测只为一件事：这一件组件有两个独立的"不能"原因，
 * 少了任何一个判据，就会出现"某台机器上顶栏按钮变成裸图标"这种静默降级 ——
 * 用户看到的不是"磨砂淡了一点"，而是"底板整个没了"。
 * 参考件（收藏页那颗）当年就是靠 `backdrop != null && isRuntimeShaderSupported()`
 * 两条一起挡的，这里把它写成可断言的函数，不留在组合里。
 */
class VeneraTopBarPillTest {

    @Test
    fun `没有采样层时不磨砂`() {
        assertFalse(veneraPillFrosted(backdropPresent = false, shaderSupported = true))
    }

    @Test
    fun `平台不支持运行时着色器时不磨砂`() {
        assertFalse(veneraPillFrosted(backdropPresent = true, shaderSupported = false))
    }

    @Test
    fun `两个条件都满足才磨砂`() {
        assertTrue(veneraPillFrosted(backdropPresent = true, shaderSupported = true))
    }
}
