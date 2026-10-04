package com.venera.compose.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本比较与"这次要不要打扰"的判据（批 1）。
 *
 * `isNewer` / `displayVersion` 是既有实现，本批之前**一条用例都没有**（它们当时只吃 `org.json`，
 * JVM 单测里解不出东西）。这一批把解析挪到 [parseLatestRelease] 之后，整条判定链终于能脱机钉住，
 * 所以顺手把那两条的边界一并钉上 —— 特别是 tag 带 `-miuix` 后缀、段数不齐这两种本包实际在用的形状。
 */
class AppUpdateRuleTest {

    @Test
    fun `逐段数字比较覆盖本包真实用过的几种 tag`() {
        assertTrue(AppUpdateChecker.isNewer("2.0.3", "2.0.2"))
        assertTrue(AppUpdateChecker.isNewer("2.1", "2.0.9"))
        assertTrue(AppUpdateChecker.isNewer("3.0", "2.99"))
        assertFalse(AppUpdateChecker.isNewer("2.0.3", "2.0.3"))    // 相等不是更新
        assertFalse(AppUpdateChecker.isNewer("2.0.2", "2.0.3"))
        // 段数不齐：缺的那一段当 0，所以 1.7 与 1.7.0 是同一版。
        assertFalse(AppUpdateChecker.isNewer("1.7", "1.7.0"))
        assertFalse(AppUpdateChecker.isNewer("1.7.0", "1.7"))
        assertTrue(AppUpdateChecker.isNewer("1.7.1", "1.7"))
    }

    @Test
    fun `预发布后缀不参与比较但也不许把版本号带脏`() {
        assertEquals("2.0.3", AppUpdateChecker.displayVersion("v2.0.3-miuix"))
        assertEquals("2.0.3", AppUpdateChecker.displayVersion("V2.0.3+build7"))
        assertEquals("2.0.3", AppUpdateChecker.displayVersion("2.0.3"))
        // tag 是本仓库作者手打的，一次手滑不该把"检查更新"整条打断。
        assertFalse(AppUpdateChecker.isNewer("2.0.3-miuix", "2.0.3"))
        assertTrue(AppUpdateChecker.isNewer("2.0.4-beta", "2.0.3"))
    }

    @Test
    fun `以后再说只压住被跳过的那一版`() {
        assertTrue(AppUpdateChecker.shouldAnnounce("2.0.4", null))
        assertTrue(AppUpdateChecker.shouldAnnounce("2.0.4", ""))
        assertTrue(AppUpdateChecker.shouldAnnounce("2.0.4", "   "))
        assertFalse(AppUpdateChecker.shouldAnnounce("2.0.4", "2.0.4"))   // 同一版不再弹
        assertTrue(AppUpdateChecker.shouldAnnounce("2.0.5", "2.0.4"))    // 出了更高的一档恢复提醒
        // 账本比线上还高（用户先跳过 2.1、后来发布通道回退到 2.0）：压住，别拿一次异常把打扰放大。
        assertFalse(AppUpdateChecker.shouldAnnounce("2.0", "2.1"))
    }

    @Test
    fun `启动检查的间隔是二十四小时而不是每次冷启动`() {
        assertEquals(24 * 60 * 60 * 1000L, AppUpdateChecker.STARTUP_CHECK_INTERVAL_MS)
    }
}
