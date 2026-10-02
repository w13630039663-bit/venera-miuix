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

    @Test fun `根路径落在已存在的普通文件下时抛错且消息含该目录路径`() {
        // 用真实手段触发"建不出目录"：LOCALAPPDATA 指到一个普通文件，File(base, "venera") 的 mkdirs 必失败
        val tmp = File(System.getProperty("java.io.tmpdir"), "r1f-paths-blocked-${System.nanoTime()}")
        val blockingFile = File(tmp, "not-a-directory")
        tmp.mkdirs()
        blockingFile.writeText("x")
        val err = runCatching { DesktopPaths.create { blockingFile.path } }.exceptionOrNull()
        assertNotNull(err)
        assertTrue("应为 IllegalStateException，实际 ${err!!::class.java.name}", err is IllegalStateException)
        assertTrue(err!!.message!!.contains(File(blockingFile, "venera").path))
        tmp.deleteRecursively()
    }

    @Test fun `探测写入被挡时收敛为 IllegalStateException 且消息含目录路径`() {
        // .write-probe 预占成目录：writeText 必抛 IOException（本机打包 exe 在探测上抛过"拒绝访问"），
        // 这条错误面要和其它支路同类、同措辞、带具体目录路径，不许裸传文件异常
        val base = File(System.getProperty("java.io.tmpdir"), "r1f-paths-probe-${System.nanoTime()}")
        val root = File(base, "venera")
        File(root, ".write-probe").mkdirs()
        val err = runCatching { DesktopPaths.create { base.path } }.exceptionOrNull()
        assertNotNull(err)
        assertTrue("应为 IllegalStateException，实际 ${err!!::class.java.name}", err is IllegalStateException)
        assertTrue(err!!.message!!.contains(root.path))
        base.deleteRecursively()
    }
}
