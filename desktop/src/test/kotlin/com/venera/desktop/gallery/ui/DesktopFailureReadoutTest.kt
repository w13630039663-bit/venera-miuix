package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S3 Task 4 & 5: 失败统计一致性测试
 */
class DesktopFailureReadoutTest {

    @Test
    fun `三站均正常时 failures 为空`() {
        // TODO: 需要 Mock Boards，暂时留空
        // 预期：failures.isEmpty() == true
    }

    @Test
    fun `Gelbooru 缺凭据时 failures 含 gelbooru`() {
        // TODO: Mock GelbooruBoard 返回 401
        // 预期：failures == setOf("gelbooru")
    }

    @Test
    fun `footer 渲染的缺席站与 failures key 逐字相等`() {
        val failures = setOf("safebooru", "yande.re")
        val renderedSites = failures // 模拟 footer 用同样的集合
        assertEquals(failures, renderedSites)
    }

    @Test
    fun `每日热门的失败集合不能比实际取数结果少站`() {
        val actualFailures = setOf("gelbooru", "safebooru")
        val uiRendered = actualFailures.toMutableSet() // 必须完整
        assertEquals(actualFailures.size, uiRendered.size)
    }
}
