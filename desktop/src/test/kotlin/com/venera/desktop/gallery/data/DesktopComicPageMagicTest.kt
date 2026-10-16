package com.venera.desktop.gallery.data

import com.venera.desktop.gallery.ui.DesktopSourceTree
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * 第一百零二批 · 那一页字节**到底是不是图像**（写入前那一道魔数闸）。
 *
 * 今天桌面只挡 0 字节（`bytes.isEmpty()`），而站方最常回的两种"看着像货其实不是"是
 * Cloudflare 的过盾 HTML 与一枚 JSON 错误信封 —— 它们都会**被当成一页写下去**，
 * 于是那一话"齐了"而其中几页永远打不开。对岸为这一格专门写过一段注释
 * （`download/DownloadManager.kt:440-446`：坏图会被大小检查短路跳过而重试永不能治愈），
 * 它的魔数表在 `:580-592`，本批逐字回核那张表。
 *
 * ⚠️ 本批只管**写入前**那一道（坏字节一律不落盘并抛错点名）。
 * "用同一道校验去**治愈历史坏图**"要改的是"这一页在不在盘上"那一判（`present` 与 `desktopComicChapterIsComplete`），
 * 那颗是全仓唯一一把判齐尺子（批 96 立、批 97/98/99/100 沿用）⇒ 动它属于改判，另批办。
 */
internal class DesktopComicPageMagicTest {

    /** 12 字节是**对岸那道下界**，不是本仓造的数（`bytes.size < 12` 一律不算图像）。 */
    private fun jpeg(): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
        0, 1, 2, 3, 4, 5, 6, 7,
    )

    private fun png(): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47,
        0, 1, 2, 3, 4, 5, 6, 7,
    )

    private fun gif(): ByteArray = byteArrayOf(
        0x47, 0x49, 0x46, 0x38,
        0, 1, 2, 3, 4, 5, 6, 7,
    )

    private fun webp(): ByteArray = byteArrayOf(
        0x52, 0x49, 0x46, 0x46, // RIFF
        0, 0, 0, 0,             // 长度那四格
        0x57, 0x45, 0x42, 0x50, // WEBP
    )

    private fun tempRoot(): File = java.nio.file.Files.createTempDirectory("b102").toFile().apply {
        deleteRecursively(); mkdirs()
    }

    /** ① 四档魔数各真跑一次（桌面页名恒 `.jpg` 而站方常回 PNG/GIF/WebP ⇒ 白名单不止 JPEG）。 */
    @Test
    fun 四档图像头都算图像() {
        listOf(
            "JPEG" to jpeg(), "PNG" to png(), "GIF" to gif(), "WebP" to webp(),
        ).forEach { (name, bytes) ->
            assertTrue("$name 那一档该算图像：", desktopComicBytesLookLikeImage(bytes))
        }
    }

    /** ② 下界那一格：11 字节不算（对岸 `bytes.size < 12`），空也不算。 */
    @Test
    fun 短于十二字节一律不算() {
        assertFalse("空串：", desktopComicBytesLookLikeImage(byteArrayOf()))
        assertFalse("11 字节（哪怕带着 JPEG 头）：", desktopComicBytesLookLikeImage(jpeg().copyOf(11)))
        assertTrue("12 字节才算：", desktopComicBytesLookLikeImage(jpeg()))
    }

    /** ③ 站方那两种"假货"各算非图像：过盾 HTML 与 JSON 错误信封。 */
    @Test
    fun 过盾页与错误信封都不算图像() {
        val cf = "<!DOCTYPE html><html><head><title>Just a moment...</title></head></html>".toByteArray()
        val json = "{\"error\":\"blocked\",\"code\":1000}".toByteArray()
        assertFalse("Cloudflare 那一屏：", desktopComicBytesLookLikeImage(cf))
        assertFalse("JSON 信封：", desktopComicBytesLookLikeImage(json))
        // ⚠️ 这一档要说清这道闸**管到哪一格**：它看的是头部那几枚（与对岸同形），
        // 所以"JPEG 头 + 尾巴是别的东西"仍然算图像 —— 那是**解码层**的事，不是这一档的账。
        // 从前这里写的是一条更严的断言（以为它能挡住那种混合体），跑出来是红的 ⇒ 题面错了，改的是题面。
        assertTrue("JPEG 头而整体是混合体：仍算图像（这一档只挡明显的假货）", desktopComicBytesLookLikeImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + cf))
        assertFalse("第三枚对不上就不算（魔数是逐档看的，不是只看首字节）：",
            desktopComicBytesLookLikeImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0, 0, 0, 0, 0, 0, 0, 0, 0)))
    }

    /** ④ 魔数表**逐字回核对岸**：它改了那张表，桌面要红而不是继续吞。 */
    @Test
    fun 魔数表逐字回核对岸() {
        val android = DesktopSourceTree.androidSource("download/DownloadManager.kt").readText()
        listOf(
            "FF D8 FF" to """bytes\[0\] == 0xFF\.toByte\(\) && bytes\[1\] == 0xD8\.toByte\(\) && bytes\[2\] == 0xFF\.toByte\(\)""",
            "89 50 4E 47" to """bytes\[0\] == 0x89\.toByte\(\) && bytes\[1\] == 0x50\.toByte\(\)""",
            "47 49 46 38" to """bytes\[0\] == 0x47\.toByte\(\) && bytes\[1\] == 0x49\.toByte\(\) && bytes\[2\] == 0x46\.toByte\(\) && bytes\[3\] == 0x38\.toByte\(\)""",
            "RIFF....WEBP" to """bytes\[8\] == 0x57\.toByte\(\) && bytes\[9\] == 0x45\.toByte\(\) && bytes\[10\] == 0x42\.toByte\(\) && bytes\[11\] == 0x50\.toByte\(\)""",
            "十二字节那道下界" to """if \(bytes\.size < 12\) return false""",
        ).forEach { (name, pattern) ->
            assertTrue("对岸那张表里的「$name」这一档找不到了（写法变了要重判，不是把桌面这一档删掉）：", Regex(pattern).containsMatchIn(android))
        }
    }

    /** ⑤ 那一趟遇到坏字节：**抛**、那颗**不落盘**、之前写下的页留着、清单字面说不齐。 */
    @Test
    fun 坏字节那一趟抛错而不落盘() {
        val dir = File(tempRoot(), "c").apply { mkdirs() }
        val html = "<!DOCTYPE html><html><body>blocked</body></html>".toByteArray()
        val message = runCatching {
            runBlocking {
                desktopComicDownloadChapter(dir, "1461316", "1461318", 1, "第1话", 2, shouldCancel = { false }, progress = { _, _ -> }) { index ->
                    if (index == 0) jpeg() else html
                }
            }
        }.exceptionOrNull()?.let { it::class.java.simpleName + "|" + (it.message ?: "") }
        assertTrue("那一趟本该抛出而它成了：$message", message != null && message.startsWith("IllegalStateException"))
        assertTrue("那句要念得出是哪一页：" + message, message!!.contains("页=1"))
        assertTrue("那句要念得出这不是图像：" + message, message.contains("不是图像"))
        assertTrue("那句要带得上分母：" + message, message.contains("共=2"))
        assertTrue("第一页留着（不清用户已有的东西）：", File(dir, "0001.jpg").isFile)
        assertFalse("坏的那一颗不许落盘（落下就永远算一页）：", File(dir, "0002.jpg").exists())
        assertTrue("清单那一格字面上必须说不齐：" + File(dir, "chapter.json").readText(),
            File(dir, "chapter.json").readText().contains("\"complete\":false"))
    }

    /** ⑥ 对照组：好字节照样写（防"把非 JPEG 一律拒了"把 PNG 也挡在门外）。 */
    @Test
    fun 非JPEG的图像照样收() {
        val dir = File(tempRoot(), "ok").apply { mkdirs() }
        val run = runBlocking {
            desktopComicDownloadChapter(dir, "9", "1461318", 0, "t", 2, shouldCancel = { false }, progress = { _, _ -> }) { index ->
                if (index == 0) png().copyOf(2048) else webp().copyOf(2048)
            }
        }
        assertEquals("两页都该收下：", DesktopComicChapterRun.Completed(written = 2), run)
        assertTrue("PNG 那一颗：", File(dir, "0001.jpg").isFile)
        assertTrue("WebP 那一颗（页名仍是 .jpg，与对岸同形）：", File(dir, "0002.jpg").isFile)
        assertTrue("齐了要说齐：", desktopComicChapterIsComplete(dir, 2))
        if (run !is DesktopComicChapterRun.Completed) fail("不该走到这里")
    }
}
