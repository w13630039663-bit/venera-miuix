package com.venera.desktop.platform

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住 PathProvider.subDir 的非法路径段判据（`..`、斜杠、反斜杠、空白），
 * 防止路径穿越防护被后续改动悄悄放宽。非法段在校验阶段就抛，不落任何真实文件。
 */
class PathProviderSubDirTest {
    private val paths = FakePaths(File(System.getProperty("java.io.tmpdir"), "r1f-subdir-${System.nanoTime()}"))

    private fun assertRejected(segment: String) {
        val err = runCatching { paths.subDir(segment) }.exceptionOrNull()
        assertNotNull("路径段 \"$segment\" 应被拒绝", err)
        assertTrue("应为 IllegalArgumentException，实际 ${err!!::class.java.name}", err is IllegalArgumentException)
        assertTrue(err!!.message!!.contains("路径段不合法"))
    }

    @Test fun `subDir 拒绝父目录穿越段`() = assertRejected("..")

    @Test fun `subDir 拒绝含正斜杠的段`() = assertRejected("a/b")

    @Test fun `subDir 拒绝含反斜杠的段`() = assertRejected("a\\b")

    @Test fun `subDir 拒绝全空白段`() = assertRejected("   ")
}
