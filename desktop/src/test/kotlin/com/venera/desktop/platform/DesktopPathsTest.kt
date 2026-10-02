package com.venera.desktop.platform

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPathsTest {
    @Test fun `LOCALAPPDATA 缺失时抛错而不是退回临时目录`() {
        // DesktopPaths 收 env 提供者，测试里喂空 —— 不为可测性引入全局可变状态
        val err = runCatching { DesktopPaths.create { null } }.exceptionOrNull()
        assertNotNull(err)
        assertTrue(err!!.message!!.contains("LOCALAPPDATA"))
    }

    @Test fun `目录建得出且文件写得进读得出`() {
        val root = File(System.getProperty("java.io.tmpdir"), "r1f-paths-${System.nanoTime()}")
        val paths = DesktopPaths.create { root.path }
        val f = paths.subDir("comic_source").resolve("probe.txt")
        f.parentFile.mkdirs()
        f.writeText("ok")
        assertEquals("ok", f.readText())
        root.deleteRecursively()
    }
}
