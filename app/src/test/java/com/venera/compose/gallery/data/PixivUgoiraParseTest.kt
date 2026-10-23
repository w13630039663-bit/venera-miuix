package com.venera.compose.gallery.data

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * pixiv 动图元信息解析（`/ajax/illust/{id}/ugoira_meta`）的单测。
 */
class PixivUgoiraParseTest {

    private val realUgoiraSample = """
        {
          "error": false,
          "message": "",
          "body": {
            "src": "https://i.pximg.net/img-zip-ugoira/img/2026/10/22/01/02/03/151160372_ugoira600x600.zip",
            "originalSrc": "https://i.pximg.net/img-zip-ugoira/img/2026/10/22/01/02/03/151160372_ugoira1920x1080.zip",
            "mime_type": "image/jpeg",
            "frames": [
              {"file": "000000.jpg", "delay": 1000},
              {"file": "000001.jpg", "delay": 500},
              {"file": "000002.jpg", "delay": 10}
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `正常解析 ugoira 元信息，字段逐一映射且 delay 夹地板值`() {
        val result = PixivUgoiraParse.ugoiraMeta(realUgoiraSample)
        assertNotNull(result)
        assertEquals("https://i.pximg.net/img-zip-ugoira/img/2026/10/22/01/02/03/151160372_ugoira600x600.zip", result!!.src)
        assertEquals("https://i.pximg.net/img-zip-ugoira/img/2026/10/22/01/02/03/151160372_ugoira1920x1080.zip", result.originalSrc)
        assertEquals("image/jpeg", result.mimeType)
        assertEquals(3, result.frames.size)
        assertEquals("000000.jpg", result.frames[0].file)
        assertEquals(1000, result.frames[0].delay)
        assertEquals("000001.jpg", result.frames[1].file)
        assertEquals(500, result.frames[1].delay)
        assertEquals("000002.jpg", result.frames[2].file)
        // 10ms 低于地板值 20ms，应被夹到 UGOIRA_MIN_DELAY_MILLIS (20ms)
        assertEquals(UGOIRA_MIN_DELAY_MILLIS, result.frames[2].delay)
        assertEquals(1000 + 500 + UGOIRA_MIN_DELAY_MILLIS, result.totalDelayMillis)
    }

    @Test
    fun `error 为 true 时抛出 IOException`() {
        val errorJson = """
            {"error":true,"message":"指定されたIDはうごイラではありません"}
        """.trimIndent()
        try {
            PixivUgoiraParse.ugoiraMeta(errorJson)
            fail("非 ugoira 应该抛 IOException")
        } catch (e: IOException) {
            // expected
        }
    }

    @Test
    fun `空 frames 回 null（退回静帧）`() {
        val emptyFramesJson = """
            {
              "error": false,
              "message": "",
              "body": {
                "src": "https://i.pximg.net/sample.zip",
                "originalSrc": "https://i.pximg.net/original.zip",
                "mime_type": "image/jpeg",
                "frames": []
              }
            }
        """.trimIndent()
        val result = PixivUgoiraParse.ugoiraMeta(emptyFramesJson)
        assertNull(result)
    }

    @Test
    fun `缺 originalSrc 时退回 src`() {
        val noOriginalJson = """
            {
              "error": false,
              "message": "",
              "body": {
                "src": "https://i.pximg.net/sample.zip",
                "mime_type": "image/jpeg",
                "frames": [
                  {"file": "000000.jpg", "delay": 125}
                ]
              }
            }
        """.trimIndent()
        val result = PixivUgoiraParse.ugoiraMeta(noOriginalJson)
        assertNotNull(result)
        assertEquals("https://i.pximg.net/sample.zip", result!!.src)
        assertEquals("https://i.pximg.net/sample.zip", result.originalSrc)
    }
}
