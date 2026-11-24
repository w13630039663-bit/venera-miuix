package com.venera.compose.components.venera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 选项行行首标记的纯映射（控件体系统一 · 阶段 2）。
 *
 * 规则钉在这里：**选中态不靠颜色表达** —— 选中与否必须换图标，颜色（primary）只是加强。
 * 单测源集没有 material-icons-extended，图标常量本身不可引用，
 * 所以这里钉的是规则的两条不变量：① 选中/未选必须是**两枚不同图标**；
 * ② 同一组合重复取值必须稳定（映射是纯的，不随重组状态漂移）。
 */
class VeneraOptionMarkerTest {

    @Test
    fun `Radio 选中与未选必须是两枚不同图标`() {
        assertNotEquals(
            veneraOptionMarker(VeneraOptionMode.Radio, selected = true),
            veneraOptionMarker(VeneraOptionMode.Radio, selected = false),
        )
    }

    @Test
    fun `Checkbox 选中与未选必须是两枚不同图标`() {
        assertNotEquals(
            veneraOptionMarker(VeneraOptionMode.Checkbox, selected = true),
            veneraOptionMarker(VeneraOptionMode.Checkbox, selected = false),
        )
    }

    @Test
    fun `Radio 与 Checkbox 的标记互不混用`() {
        assertNotEquals(
            veneraOptionMarker(VeneraOptionMode.Radio, selected = true),
            veneraOptionMarker(VeneraOptionMode.Checkbox, selected = true),
        )
        assertNotEquals(
            veneraOptionMarker(VeneraOptionMode.Radio, selected = false),
            veneraOptionMarker(VeneraOptionMode.Checkbox, selected = false),
        )
    }

    @Test
    fun `映射是纯的（同参数两次取值一致）`() {
        for (mode in VeneraOptionMode.entries) {
            for (selected in listOf(true, false)) {
                assertEquals(
                    veneraOptionMarker(mode, selected),
                    veneraOptionMarker(mode, selected),
                )
            }
        }
    }
}
