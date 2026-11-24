package com.venera.compose.components.venera

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `VeneraButton` 五变体的颜色规格映射（控件体系统一 · 阶段 1）。
 *
 * 这份映射是 MIUIX 后端消费的唯一真源；MD3 后端走各原生变体（Button / FilledTonalButton /
 * OutlinedButton / TextButton / Button+error 色位），两者语义必须一致 —— 一致性就钉在这里：
 * 每条断言都写成"spec 的这一档 = MD3 原生变体的默认语义"。
 * 映射要是混进组合函数里变成一段 if，"MIUIX 档静默丢了描边/危险色"这类假开关就没人能当场抓住了。
 */
class VeneraButtonSpecTest {

    private val primary = Color(0xFF005CBB)
    private val onPrimary = Color(0xFFFFFFFF)
    private val secondaryContainer = Color(0xFFD6E3FF)
    private val onSecondaryContainer = Color(0xFF001B3F)
    private val error = Color(0xFFBA1A1A)
    private val onError = Color(0xFFFFFFFF)
    private val outline = Color(0xFF76777F)
    private val disabledContainer = Color(0xFFE0E0E0)
    private val disabledContent = Color(0xFF9E9E9E)

    private fun spec(variant: VeneraButtonVariant) = veneraButtonSpec(
        variant = variant,
        primary = primary,
        onPrimary = onPrimary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        error = error,
        onError = onError,
        outline = outline,
        disabledContainer = disabledContainer,
        disabledContent = disabledContent,
    )

    @Test
    fun `Primary = 实底主色（MD3 Button 默认语义）`() {
        val s = spec(VeneraButtonVariant.Primary)
        assertEquals(primary, s.container)
        assertEquals(onPrimary, s.content)
        assertNull(s.borderColor)
    }

    @Test
    fun `Secondary = tonal 容器实底（MD3 FilledTonalButton 默认语义）`() {
        val s = spec(VeneraButtonVariant.Secondary)
        assertEquals(secondaryContainer, s.container)
        assertEquals(onSecondaryContainer, s.content)
        assertNull(s.borderColor)
    }

    @Test
    fun `Outline = 透明底 + 主色内容 + outline 描边（MD3 OutlinedButton 默认语义）`() {
        val s = spec(VeneraButtonVariant.Outline)
        assertEquals(Color.Transparent, s.container)
        assertEquals(primary, s.content)
        assertEquals(outline, s.borderColor)
    }

    @Test
    fun `Ghost = 透明底 + 主色内容 + 无描边（MD3 TextButton 默认语义）`() {
        val s = spec(VeneraButtonVariant.Ghost)
        assertEquals(Color.Transparent, s.container)
        assertEquals(primary, s.content)
        assertNull(s.borderColor)
    }

    @Test
    fun `Destructive = error 实底（MD3 Button+error 色位语义）`() {
        val s = spec(VeneraButtonVariant.Destructive)
        assertEquals(error, s.container)
        assertEquals(onError, s.content)
        assertNull(s.borderColor)
    }

    @Test
    fun `Outline 与 Ghost 禁用时只降内容色，不往底上加灰`() {
        // 这两个变体的底本来就是"没有底"；禁用往底上加灰会让幽灵按钮变成灰饼。
        assertEquals(Color.Transparent, spec(VeneraButtonVariant.Outline).disabledContainer)
        assertEquals(Color.Transparent, spec(VeneraButtonVariant.Ghost).disabledContainer)
        assertEquals(disabledContent, spec(VeneraButtonVariant.Outline).disabledContent)
    }

    @Test
    fun `实底三变体禁用时走主题禁用色位`() {
        for (v in listOf(VeneraButtonVariant.Primary, VeneraButtonVariant.Secondary, VeneraButtonVariant.Destructive)) {
            val s = spec(v)
            assertEquals(disabledContainer, s.disabledContainer)
            assertEquals(disabledContent, s.disabledContent)
        }
    }

    @Test
    fun `尺寸档映射到对应高度`() {
        val compact = 36.dp
        val medium = 48.dp
        val large = 56.dp
        val pad = 16.dp
        assertEquals(compact, veneraButtonMetrics(VeneraButtonSize.Compact, compact, medium, large, pad).minHeight)
        assertEquals(medium, veneraButtonMetrics(VeneraButtonSize.Medium, compact, medium, large, pad).minHeight)
        assertEquals(large, veneraButtonMetrics(VeneraButtonSize.Large, compact, medium, large, pad).minHeight)
        assertEquals(pad, veneraButtonMetrics(VeneraButtonSize.Compact, compact, medium, large, pad).horizontalPadding)
    }
}
