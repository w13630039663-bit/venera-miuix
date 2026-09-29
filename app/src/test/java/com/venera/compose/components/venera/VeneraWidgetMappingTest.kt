package com.venera.compose.components.venera

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批次 B4 新增/扩展转发件里的**映射层**。
 *
 * 每一段都对应一个"M3 与 miuix 说法不同"的参数。这类地方是假开关的产地：
 * 两边都编译得过、调用点都传得进去，但 miuix 档下屏幕上什么都没有。
 * 所以映射必须写成能被断言的纯函数，不能是混在组合里的一段 if。
 */
class VeneraWidgetMappingTest {

    // ---- Checkbox：M3 是 Boolean，miuix 是 ToggleableState ----

    @Test
    fun `勾选态映射到 miuix 的三态枚举`() {
        assertEquals(ToggleableState.On, veneraToggleState(true))
        assertEquals(ToggleableState.Off, veneraToggleState(false))
    }

    @Test
    fun `回调为空时 miuix 侧保持不可交互`() {
        // M3 用 `onCheckedChange = null` 表达"只展示不可点"，miuix 用 `onClick = null`。
        // 若这里把 null 换成 `{}`，多选框会在禁用态照样翻转状态 —— 那是行为回归，不是观感差异。
        assertNull(veneraCheckClick(checked = true, onCheckedChange = null))
    }

    @Test
    fun `有回调时点击要翻转当前值`() {
        val got = ArrayList<Boolean>()
        veneraCheckClick(checked = false) { got.add(it) }?.invoke()
        veneraCheckClick(checked = true) { got.add(it) }?.invoke()
        // 当前未勾选 → 点击应上报 true；当前已勾选 → 点击应上报 false
        assertEquals(listOf(true, false), got)
    }

    // ---- TextField：M3 有独立 placeholder 槽，miuix 只有 label ----

    @Test
    fun `标签与占位符同时存在时 miuix 侧只保标签`() {
        val slot = veneraFieldLabelSlot(label = "Repo URL", placeholder = "https://.../index.json")
        assertEquals("Repo URL", slot.text)
        assertFalse(slot.asPlaceholder)
    }

    @Test
    fun `没有标签时占位符顶上标签槽`() {
        val slot = veneraFieldLabelSlot(label = "", placeholder = "https://.../source.js")
        assertEquals("https://.../source.js", slot.text)
        assertTrue(slot.asPlaceholder)
    }

    @Test
    fun `两者都空就是没有标签`() {
        val slot = veneraFieldLabelSlot(label = "", placeholder = "")
        assertEquals("", slot.text)
        assertFalse(slot.asPlaceholder)
    }

    // ---- TextButton：破坏性操作用错误色（两家都认这个色）----

    private val normal = Color(0xFF333333)
    private val danger = Color(0xFFAA0000)

    @Test
    fun `破坏性按钮换成错误色`() {
        assertEquals(danger, veneraTextColor(normal = normal, destructiveColor = danger, destructive = true))
    }

    @Test
    fun `普通按钮保持常规色`() {
        assertEquals(normal, veneraTextColor(normal = normal, destructiveColor = danger, destructive = false))
    }
}
