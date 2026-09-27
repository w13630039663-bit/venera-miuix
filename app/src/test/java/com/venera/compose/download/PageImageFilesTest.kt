package com.venera.compose.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「一页图」判据的行为锁。
 *
 * 这一层要钉的是**一致性**：导入侧与扫描侧必须是同一份白名单。
 * 两边不一致过一次，长相就是"导入报成功、书架上没有这本书"，而且全程不报错
 * （`LocalComicManager` 收 `.jpeg` 并落成 `0001.jpeg`，扫描侧只认 jpg/png/webp）。
 */
class PageImageFilesTest {

    @Test
    fun `jpeg 与大小写都算一页图`() {
        listOf("0001.jpg", "0001.jpeg", "0001.JPG", "0001.JPEG", "a/b/页.png", "x.webp").forEach {
            assertTrue("$it 应该算一页图", isPageImage(it))
        }
    }

    @Test
    fun `压缩包 说明文件与无扩展名都不算页`() {
        listOf("comic_info.json", "cover.zip", "notes.txt", ".nomedia", "README", "page.gif", "page.jpe").forEach {
            assertFalse("$it 不该被当成一页图", isPageImage(it))
        }
    }

    @Test
    fun `白名单就是那四个 加一档要同时改导入与扫描`() {
        // 钉住集合本身：将来加 gif/jpe 必须是**一处**改动，而不是再抄出第四份判据。
        assertEquals(setOf("jpg", "jpeg", "png", "webp"), pageImageExtensions)
    }

    @Test
    fun `导入落盘把 jpeg 归一成 jpg`() {
        assertEquals("jpg", pageImageExtensionOf("PAGE.JPEG"))
        assertEquals("jpg", pageImageExtensionOf("0001.jpg"))
        assertEquals("png", pageImageExtensionOf("0002.png"))
        assertEquals("webp", pageImageExtensionOf("0003.webp"))
    }

    @Test
    fun `目录名与隐藏文件不会被误判成页`() {
        // substringAfterLast 的默认值那条路：没有点号时不能拿整串当扩展名。
        assertFalse(isPageImage("chapter"))
        assertFalse(isPageImage(""))
        assertFalse(isPageImage("dir.jpg/"))
    }
}
