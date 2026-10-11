package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.PathProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 83 · 「哪几颗源被停用」落在盘上的那一格。
 *
 * ## 为什么这一份是桌面本地而不是共享表
 *
 * 现量过两份存储（不是凭印象）：Android 的 `comic_source` 表**首次运行播种五条写死的默认源**
 * （`ComicSourceDao.kt:41-47`：copymanga / picacg / mangadex / ehentai / nhentai），
 * 而桌面列表的真值是**随包的 34 颗 js**（`EngineAssets.listSourceKeys`）—— 两者不是一件事：
 * 那张表是"手机上装过哪些源"的元数据（含图标、排序、config_json），这一份是"桌面这台机器上
 * 哪几颗不参与选源"。并成一颗等于把 A 站的凭据发给 B 站（本仓量过一次的那一族），
 * 所以**刻意不同轨**，并且把这句话写进读数与文档，不让它看起来像漏了同步。
 */
class DesktopSourceStateTest {

    private class TempPaths(root: File) : PathProvider {
        override val dataRoot: File = root
        override val cacheRoot: File = File(root, "cache")
    }

    private fun tempRoot(name: String): File =
        File(System.getProperty("java.io.tmpdir"), "b83_$name${System.nanoTime()}").apply { mkdirs() }

    @Test
    fun 停用清单写下去再换新实例读得回来() {
        val paths = TempPaths(tempRoot("roundtrip"))
        val state = DesktopSourceState(paths)
        state.setDisabled(setOf("jm", "goda"))
        assertEquals("读写必须同轨（不同轨的表现是屏上关了而重启又开着）", setOf("goda", "jm"), DesktopSourceState(paths).disabled())
    }

    @Test
    fun 全新数据是空表而不是抛() {
        val state = DesktopSourceState(TempPaths(tempRoot("fresh")))
        assertEquals("没有这份文件 = 一颗都没停用，是合法的全新状态", emptySet<String>(), state.disabled())
    }

    @Test
    fun 全部启用之后读到的是空表而不是旧值() {
        // 「再点一次开」那一档的落盘面：旧值留在盘上就是永远关不掉。
        val paths = TempPaths(tempRoot("clear"))
        val state = DesktopSourceState(paths)
        state.setDisabled(setOf("jm"))
        state.setDisabled(emptySet())
        assertEquals(emptySet<String>(), DesktopSourceState(paths).disabled())
    }

    @Test
    fun 落盘文件名自证是桌面本地那份而不是共享表() {
        val paths = TempPaths(tempRoot("name"))
        DesktopSourceState(paths).setDisabled(setOf("jm"))
        val file = File(paths.subDir("prefs"), "venera_desktop_sources.json")
        assertTrue("停用状态要落在桌面自己那份文件里（共享表是另一件事）：${paths.dataRoot.absolutePath}", file.isFile)
    }

    @Test
    fun 那句读数念得出停用几颗与本地口径() {
        val line = DesktopSourceState.reading(disabled = setOf("jm"), total = 34)
        assertTrue("要念得出停用数：$line", line.contains("停用=1"))
        assertTrue("要念得出清单总数：$line", line.contains("清单=34"))
        assertTrue("要把「这一份只影响桌面」写进读数，免得被读成两端不同步的 bug：$line", line.contains("桌面本地"))
    }
}
