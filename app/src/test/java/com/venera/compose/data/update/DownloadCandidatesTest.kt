package com.venera.compose.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 候选下载顺序（批 1）。这条表钉的是方案文档 §1.2 那条硬规则，不是"拼字符串对不对"。
 *
 * 顺序与"什么时候不许有第二条"两件事都必须钉住：
 * - **官方永远在第一位**（可达就别换源）；
 * - **没有可信 sha256 就只许有官方这一条**。这条判式如果反过来写（无哈希时"照样试镜像，
 *   只是不校验"），就等于把 14 MB 可执行包的来源交给别人的域名 ——
 *   表现是"更新成功了"，而那可能不是我们的包。这种形状在本仓的叫法是假开关。
 */
class DownloadCandidatesTest {

    private val official =
        "https://github.com/w13630039663-bit/venera-miuix/releases/download/v2.0.3-miuix/venera-miuix-2.0.3.apk"
    private val sha = "b".repeat(64)

    @Test
    fun `拿到哈希时官方永远排第一`() {
        val candidates = downloadCandidates(official, sha)
        assertEquals(official, candidates.first())
        assertEquals(1 + UPDATE_MIRROR_PREFIXES.size, candidates.size)
    }

    @Test
    fun `镜像条目是前缀拼官方整链而不是改写域名`() {
        downloadCandidates(official, sha).drop(1).forEach { candidate ->
            val prefix = UPDATE_MIRROR_PREFIXES.first { candidate.startsWith(it) }
            assertEquals(prefix + official, candidate)
            assertTrue("镜像顺序必须与表一致", candidate.length > prefix.length)
        }
    }

    @Test
    fun `没有哈希就只剩官方一条`() {
        assertEquals(listOf(official), downloadCandidates(official, null))
        assertEquals(listOf(official), downloadCandidates(official, ""))
        assertEquals(listOf(official), downloadCandidates(official, "   "))
    }

    @Test
    fun `非 github 的直链不许被拼成任意代理`() {
        // 镜像的语义是"帮我取 github 上那个文件"。喂它一条别的域名，它就成了任意转发器，
        // 而那已经不是本功能授权它做的事。
        assertEquals(
            listOf("https://example.com/a.apk"),
            downloadCandidates("https://example.com/a.apk", sha),
        )
        assertEquals(
            listOf("http://github.com/a.apk"),
            downloadCandidates("http://github.com/a.apk", sha),   // 明文一律不加前缀
        )
    }

    @Test
    fun `内置表里没有那条实测连不上的前缀`() {
        // mirror.ghproxy.com 本机 HEAD 读数 http=000（方案文档 §0.5），不该占一个必然失败的位次。
        assertTrue(UPDATE_MIRROR_PREFIXES.none { "mirror.ghproxy" in it })
        assertEquals(UPDATE_MIRROR_PREFIXES, UPDATE_MIRROR_PREFIXES.distinct())
        assertTrue(UPDATE_MIRROR_PREFIXES.all { it.startsWith("https://") && it.endsWith("/") })
    }
}
