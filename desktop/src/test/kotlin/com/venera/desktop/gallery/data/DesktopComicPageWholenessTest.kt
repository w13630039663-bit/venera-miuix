package com.venera.desktop.gallery.data

import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第一百一十批 · 「这一页在不在」四处各数各的，收成一把：跳过前先读头。
 *
 * ## 治的是哪一条（现量，不是推测）
 * 批 102 那道魔数闸只管**新写的字节**，管不到**已经在盘上的**：四处判"这页有了"的地方
 * （续跑跳过 `DesktopComicChapterDownload.kt:263`、屏上「已下全 / 缺 N 页」 `DesktopComicDownloadedList.kt:106`、
 * 队列那一行 `DesktopComicDownloadQueue.kt:116`、那颗判齐件 `DesktopComicChapterDownload.kt:148`）全是「名字 + 非零长度」。
 * 于是闸装好之前落盘的那一类文件（过盾页 HTML、错误信封 JSON、Brotli/GZIP 压缩包）被四处一致当成"已有"——
 * 下载跳过它、屏上念「已下全」、阅读器打开是破图，而**重试永远治不了**。对岸同一格早就改过：
 * `DownloadManager.kt:446-455` 那句「跳过前读取文件头做魔数校验，非图片一律重新下载覆盖」。
 *
 * ## 为什么四处一起换而不是只换下载那一处
 * 同一屏同一个列名（`已写/应写`）吃两把尺子就是口径分裂；而卡上那枚「已下载 N 话」吃的正是屏上那一颗读侧。
 * 第四处（`desktopComicChapterIsComplete` 的逐页循环）是本批**自审时补逮到的**：判式 ⑤ 原先只钉
 * `.length() > 0L` 那一形，而这一处写的是 `.length() <= 0L` —— 标识符看不见，那条"已下全"正是屏上那句话的
 * 唯一来源。⇒ 判式 ⑤ 现在两种拼法都禁，判式 ⑧ 把这一处的**行为**也钉住（光钉形状会再漏第三种拼法）。
 */
class DesktopComicPageWholenessTest {

    private fun jpeg(): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
    )

    private fun png(): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 1, 2, 3,
    )

    private fun gif(): ByteArray = byteArrayOf(
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0, 1, 2, 3, 4, 5,
    )

    private fun webp(): ByteArray = byteArrayOf(
        0x52, 0x49, 0x46, 0x46, // RIFF
        0, 0, 0, 0,             // 长度那四格
        0x57, 0x45, 0x42, 0x50, // WEBP
    )

    private fun html(): ByteArray = ("<html><head><title>Just a moment...</title></head><body>cf</body></html>")
        .toByteArray()

    private fun json(): ByteArray = ("{\"error\":{\"code\":1015,\"name\":\"Too Many Requests\"}}").toByteArray()

    /** 把 [head] 补到 [total] 字节写成第 [index] 页；返回那颗文件。 */
    private fun writePage(dir: File, index: Int, head: ByteArray, total: Int): File {
        val bytes = head.copyOf(maxOf(total, head.size))
        val f = File(dir, desktopComicChapterPageFileName(index))
        f.writeBytes(bytes)
        return f
    }

    private fun tempRoot(): File = Files.createTempDirectory("b110").toFile().apply {
        deleteRecursively(); mkdirs()
    }

    @Test
    fun 四档图像头且够大那一页算已经有了() {
        val dir = tempRoot()
        try {
            listOf("JPEG" to jpeg(), "PNG" to png(), "GIF" to gif(), "WebP" to webp()).forEachIndexed { index, (name, head) ->
                writePage(dir, index, head, 2048)
                assertTrue("$name 那一档该算已有：", desktopComicPageLooksWhole(dir, index))
            }
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun 头部是过盾页或错误信封那一页不算已有() {
        // 这一档就是本批的病根：批 102 之前落盘的坏页名字对、尺寸也够大，只有头部会说实话。
        val dir = tempRoot()
        try {
            writePage(dir, 0, html(), 4096)
            writePage(dir, 1, json(), 4096)
            assertFalse("HTML 过盾页不算已有（要重取覆盖）：", desktopComicPageLooksWhole(dir, 0))
            assertFalse("JSON 错误信封不算已有：", desktopComicPageLooksWhole(dir, 1))
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun 头部对而不足一千字节也不算已有() {
        // `> 1024` 照抄对岸那把下界 ⇒ 这是**结论**而不是巧合，必须由判式钉住：
        // 代价是"小于 1 KB 的合法页每趟重取"，对岸同样如此（它那句注释就是拿 1 KB 当坏图下界）。
        val dir = tempRoot()
        try {
            writePage(dir, 0, jpeg(), 500)
            writePage(dir, 1, jpeg(), 1024)
            writePage(dir, 2, jpeg(), 1025)
            assertFalse("500 字节不算：", desktopComicPageLooksWhole(dir, 0))
            assertFalse("恰好 1024 也不算（对岸是严格大于）：", desktopComicPageLooksWhole(dir, 1))
            assertTrue("1025 才算：", desktopComicPageLooksWhole(dir, 2))
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun 缺货那一族三档都算没有而不抛() {
        // 读盘判式不许把"没有"读成"炸"：续跑开头要按页数一遍，任何一页缺都得只是"缺"。
        val dir = tempRoot()
        try {
            assertFalse("文件不存在：", desktopComicPageLooksWhole(dir, 7))
            File(dir, desktopComicChapterPageFileName(8)).writeBytes(byteArrayOf())
            assertFalse("零字节：", desktopComicPageLooksWhole(dir, 8))
            File(dir, "0009.tmp").writeBytes(jpeg().copyOf(2048))
            assertFalse("名字不合的那一颗（同目录的临时档）不算一页：", desktopComicPageLooksWhole(dir, 9))
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun 三处消费点都改吃那颗纯件而旧那一形零处() {
        val all = DesktopSourceTree.desktopMainSources()
        assertEquals("声明恰一处", 1, all.sumOf { DesktopSourceTree.codeOccurrences(it, "fun desktopComicPageLooksWhole(") })
        // 5 = 那一颗声明 + 四处调用（续跑跳过 / 屏上读侧 / 队列那一行 / 那颗判齐件的逐页循环）
        assertEquals("声明加四处调用共五处", 5, all.sumOf { DesktopSourceTree.codeOccurrences(it, "desktopComicPageLooksWhole(") })
        for (name in listOf(
            "DesktopComicChapterDownload.kt",
            "DesktopComicDownloadedList.kt",
            "DesktopComicDownloadQueue.kt",
        )) {
            val f = DesktopSourceTree.desktopSourceOf("gallery/data/$name")
            // 两种拼法都要钉：只钉 `> 0L` 会放过 `<= 0L`（判齐件那一处最初就是这么漏网的 —— rounds §9）。
            assertEquals(
                "$name 里不许再留「非零长度」那一形（留着就是第五种齐不齐）",
                0,
                DesktopSourceTree.codeOccurrences(f, ".length() > 0L") +
                    DesktopSourceTree.codeOccurrences(f, ".length() <= 0L"),
            )
        }
    }

    @Test
    fun 全仓只有一颗魔数判式() {
        val all = DesktopSourceTree.desktopMainSources()
        assertEquals("魔数判式只许一颗", 1, all.sumOf { DesktopSourceTree.codeOccurrences(it, "fun desktopComicBytesLookLikeImage(") })
        // 反面的那一半：不许有人就地再写一段头部比较（那正是"第五种齐不齐"长出来的样子）。
        val magicSites = all.filter { DesktopSourceTree.codeText(it).contains("0xD8") }.map { it.name }
        assertEquals("图像魔数只许出现在那一颗文件里", listOf("DesktopComicChapterDownload.kt"), magicSites)
    }

    @Test
    fun 跳过那一跳读的是前缀而不是整份() {
        // 对岸 `isValidImageBytes(targetFile.readBytes())` 整份读；桌面**故意不抄** ——
        // 一次续跑要判 30×N 页，整份读会把几十 MB 全过一遍堆。这一枚就是"故意的改进 != 漂"的证据。
        for (name in listOf(
            "DesktopComicChapterDownload.kt",
            "DesktopComicDownloadedList.kt",
            "DesktopComicDownloadQueue.kt",
        )) {
            val f = DesktopSourceTree.desktopSourceOf("gallery/data/$name")
            assertEquals("$name 里不许整份读页档", 0, DesktopSourceTree.codeOccurrences(f, "readBytes()"))
        }
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicChapterDownload.kt"))
        assertTrue("那颗纯件必须只取前缀（`readNBytes(12)` 那一形）", code.contains("readNBytes(12)"))
    }

    @Test
    fun 清单说齐而其中一颗是过盾页时那一话不算齐() {
        // 判式 ⑤ 只管"形状"，这一枚管**行为**：屏上「已下全」那句话唯一的来源就是这颗判齐件，
        // 而它当初正是被 `.length() <= 0L` 那种拼法从判式 ⑤ 眼底漏过去的（rounds §9）。
        val dir = tempRoot()
        try {
            (0 until 3).forEach { DesktopComicPageFixtures.writeWholePage(dir, it) }
            desktopComicWriteChapterManifest(
                dir, chapterId = "c1", order = 0, title = "t", expectedPages = 3, complete = true,
            )
            assertTrue("三页都好而清单说齐：", desktopComicChapterIsComplete(dir, 3))
            DesktopComicPageFixtures.writeBrokenPage(dir, 1)
            assertFalse("中间那一页换成过盾页 HTML 之后不许再说齐：", desktopComicChapterIsComplete(dir, 3))
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun 删那一屏数的是要删掉的颗数而不改用齐不齐那把尺子() {
        // 那条边界也是本批的一部分：`DesktopComicChapterDelete.kt` 里那颗 `pageCountOf` 同是"数页"，
        // 而它数的是**刚才在那儿、马上要没了的颗数**。换成这把尺子就会念「删掉了 2 页」而盘上少 3 颗 ——
        // 把释放说小，正是批 98 立"只信回读盘"时要防的那一族。行为那一半在 `DesktopComicChapterDeleteTest` ⑧b。
        val f = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicChapterDelete.kt")
        assertEquals("删那一屏不许吃这把新尺子", 0, DesktopSourceTree.codeOccurrences(f, "desktopComicPageLooksWhole("))
        assertEquals("它自己那一判仍按页名那张正则数颗数", 1, DesktopSourceTree.codeOccurrences(f, "matches(DESKTOP_COMIC_PAGE_NAME)"))
    }
}
