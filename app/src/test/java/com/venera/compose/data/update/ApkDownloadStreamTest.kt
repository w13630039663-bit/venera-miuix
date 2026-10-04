package com.venera.compose.data.update

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下载那一层的流内核（批 2）。
 *
 * 这里要钉的不是"能不能下下来"，而是**散列到底算对了没有**——因为算错的表现是"校验失败"，
 * 读起来像镜像在使坏，没人会怀疑到自己的 `update(buffer, 0, read)` 写成了 `update(buffer)`。
 * 用例故意造两种形状：整块对齐的、和**尾块不满**的（33 000 字节），后者才抓得住重复计入上一块尾巴那一类错。
 */
class ApkDownloadStreamTest {

    /** 造一段可复现的伪随机字节（不用 Random(seed) 是因为要跟独立计算的期望值比，内容本身无所谓）。 */
    private fun bytes(size: Int): ByteArray = ByteArray(size) { i -> ((i * 31 + 7) % 251).toByte() }

    private fun sha256Hex(input: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { "%02x".format(it) }

    @Test
    fun `整块对齐的输入全部落盘且全部进散列`() {
        val payload = bytes(32 * 1024 * 4)
        val digest = MessageDigest.getInstance("SHA-256")
        val sink = ByteArrayOutputStream()
        val marks = mutableListOf<Long>()

        val written = pumpWithDigest(ByteArrayInputStream(payload), sink, digest) { marks += it }

        assertEquals(payload.size.toLong(), written)
        assertArrayEqualsCompat(payload, sink.toByteArray())
        assertEquals(sha256Hex(payload), digest.hexLower())
        assertEquals(payload.size.toLong(), marks.last())
    }

    @Test
    fun `尾块不满时散列仍然等于对整段字节独立算出来的那个`() {
        // 这一条是本次抽取的核心：33 000 = 一整块 32 768 + 尾块 232。
        // 写成 digest.update(buffer) 的话，尾块那一步会把上一块整块重复计入，散列必错。
        val payload = bytes(33_000)
        val digest = MessageDigest.getInstance("SHA-256")
        val sink = ByteArrayOutputStream()

        val written = pumpWithDigest(ByteArrayInputStream(payload), sink, digest) {}

        assertEquals(33_000L, written)
        assertEquals(sha256Hex(payload), digest.hexLower())
        assertArrayEqualsCompat(payload, sink.toByteArray())
    }

    @Test
    fun `空响应不回调进度也不抛`() {
        val digest = MessageDigest.getInstance("SHA-256")
        val sink = ByteArrayOutputStream()
        var calls = 0

        val written = pumpWithDigest(ByteArrayInputStream(ByteArray(0)), sink, digest) { calls++ }

        assertEquals(0L, written)
        assertEquals(0, calls)
        assertTrue(sink.size() == 0)
        // 空输入的散列是那个固定常数，写死在这里当"确实算过一遍"的证据。
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            digest.hexLower(),
        )
    }

    @Test
    fun `回调的是累计字节且单调不减`() {
        val payload = bytes(100_000)
        val marks = mutableListOf<Long>()
        pumpWithDigest(ByteArrayInputStream(payload), ByteArrayOutputStream(), MessageDigest.getInstance("SHA-256")) {
            marks += it
        }
        assertEquals(payload.size.toLong(), marks.last())
        assertTrue("进度读数倒退了：$marks", marks.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("块数对不上（应为 4 块）：${marks.size}", marks.size == 4)
    }

    @Test
    fun `字节真落到磁盘上而不是只留在内存`() {
        val payload = bytes(5_000)
        val tmp = File.createTempFile("pump-", ".part").apply { deleteOnExit() }
        val digest = MessageDigest.getInstance("SHA-256")
        tmp.outputStream().buffered().use { out ->
            assertEquals(5_000L, pumpWithDigest(ByteArrayInputStream(payload), out, digest) {})
        }
        assertArrayEqualsCompat(payload, tmp.readBytes())
        assertEquals(sha256Hex(payload), digest.hexLower())
        tmp.delete()
    }

    @Test
    fun `总长未知时进度是不确定态而不是停在零`() {
        assertEquals(-1, progressPercent(done = 1_000L, total = 0L))
        assertEquals(-1, progressPercent(done = 0L, total = -1L))
        assertEquals(50, progressPercent(5_000L, 10_000L))
        // 镜像给的 Content-Length 比实际小是有可能的（改写过头），夹住而不是报出 137%。
        assertEquals(100, progressPercent(13_700L, 10_000L))
        assertEquals(0, progressPercent(0L, 10_000L))
    }

    @Test
    fun `总长优先信响应头缺失才退到发布通道那份`() {
        assertEquals(14_430_084L, resolveTotal("14430084", fallbackBytes = 999L))
        assertEquals(999L, resolveTotal(null, fallbackBytes = 999L))
        assertEquals(999L, resolveTotal("abc", fallbackBytes = 999L))
        assertEquals(999L, resolveTotal("-1", fallbackBytes = 999L))
        assertEquals(999L, resolveTotal("0", fallbackBytes = 999L))     // 0 长度不是长度
        assertEquals(0L, resolveTotal(null, fallbackBytes = -5L))        // 两边都不可信时不报负数
    }

    @Test
    fun `失败读数念的是该问责的那个域名`() {
        assertEquals(
            "github.com",
            hostOf("https://github.com/w13630039663-bit/venera-miuix/releases/download/v2.0.3-miuix/a.apk"),
        )
        // 镜像的形状是"前缀里面又套一条完整 URL"，念出内层域名等于把别人的失败算到 GitHub 头上。
        assertEquals(
            "gh-proxy.com",
            hostOf("https://gh-proxy.com/https://github.com/w13630039663-bit/venera-miuix/releases/download/a.apk"),
        )
        assertEquals("example.com", hostOf("https://example.com"))
    }

    private fun assertArrayEqualsCompat(expected: ByteArray, actual: ByteArray) {
        assertEquals("字节数就不等", expected.size, actual.size)
        assertTrue(
            "内容在第 ${expected.indices.firstOrNull { i -> expected[i] != actual[i] } ?: -1} 个字节上不等",
            expected.contentEquals(actual),
        )
    }
}
