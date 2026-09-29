package com.venera.compose.components.venera

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `VeneraTextField` 的 `isError` 映射。
 *
 * 为什么单独把这个抽出来测：`isError` 是 **M3 有、miuix 没有**的参数
 * （miuix 0.9.4-rc01 的 `TextField` 连 `TextFieldColors` 里都没有 error 位）。
 * 转发件最容易在这里出事 —— 两家都能编译通过、界面上看不出差别，
 * 但 miuix 档下"输入非法"这件事**没有任何视觉表达**，用户只看到"点不动/没反应"。
 * 这正是本仓禁止的假开关形状，所以映射必须是一个能被断言的函数，不是一段 if 混在组合里。
 */
class VeneraTextFieldMappingTest {

    private val base = Color(0xFF111111)
    private val error = Color(0xFFAA0000)

    @Test
    fun `没有错误时保持默认描边`() {
        assertEquals(base, veneraFieldBorderColor(base, error, isError = false))
    }

    @Test
    fun `错误态必须换成错误色（miuix 后端不许静默忽略）`() {
        assertEquals(error, veneraFieldBorderColor(base, error, isError = true))
    }

    @Test
    fun `错误态压过禁用态的描边`() {
        // 代理表单在"保存中"会同时 enabled=false 与 isError=true，调用点因此会把
        // "禁用灰"当作 default 传进来。此时错误色必须仍然赢 ——
        // 否则用户分不清"是我填错了"还是"只是暂时不能编辑"。
        val disabledGray = Color(0xFF888888)
        assertEquals(error, veneraFieldBorderColor(disabledGray, error, isError = true))
    }
}
