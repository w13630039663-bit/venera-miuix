package com.venera.compose.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S8 冒烟测试：图片管道核心纯逻辑。
 *
 * - [ImagePipelinePolicy.calculateJmScrambleNum] 与 jm.js 的 modifyImage 算法 1:1 对齐，
 *   这是禁漫源图片能否正常渲染的命门，算法回归必须锁定。
 * - [ImagePipelinePolicy.parseCropRange] 是 E-Hentai 雪碧图裁切指令的解析器。
 *
 * 注：calculateJmScrambleNum 内部引用 android.util.Log，JVM 单测默认会抛
 * "not mocked"——gradle.properties 已开 unitTests.isReturnDefaultValues 时可直跑；
 * 若未开启，这两个用例需放 androidTest。当前项目开启 returnDefaultValues。
 */
class ImagePipelinePolicyTest {

    // ---------- JM 分块计算（对齐 jm.js） ----------

    @Test
    fun `jm epId 低于 220980 不混淆`() {
        val url = "https://cdn.mhimg.net/media/photos/100000/000001.jpg"
        assertEquals(0, ImagePipelinePolicy.calculateJmScrambleNum(url))
    }

    @Test
    fun `jm epId 处于 220980-268850 固定 10 块`() {
        val url = "https://cdn.mhimg.net/media/photos/250000/000001.jpg"
        assertEquals(10, ImagePipelinePolicy.calculateJmScrambleNum(url))
    }

    @Test
    fun `jm 高位段分块数在 2-16 之间`() {
        // epId > 421926: num = md5尾字符%8*2+2 ∈ [2,16]
        val url = "https://cdn.mhimg.net/media/photos/500000/abc123.jpg"
        val num = ImagePipelinePolicy.calculateJmScrambleNum(url)
        assertTrue("num=$num 越界", num in 2..16)
    }

    @Test
    fun `jm 中位段分块数在 2-20 之间`() {
        // 268850 <= epId <= 421926: num = md5尾字符%10*2+2 ∈ [2,20]
        val url = "https://cdn.mhimg.net/media/photos/300000/xyz789.jpg"
        val num = ImagePipelinePolicy.calculateJmScrambleNum(url)
        assertTrue("num=$num 越界", num in 2..20)
    }

    @Test
    fun `jm gif 永不混淆`() {
        val url = "https://cdn.mhimg.net/media/photos/500000/abc123.gif"
        assertEquals(0, ImagePipelinePolicy.calculateJmScrambleNum(url))
    }

    @Test
    fun `jm 非 photos 路径不混淆`() {
        assertEquals(0, ImagePipelinePolicy.calculateJmScrambleNum("https://example.com/other/1.jpg"))
    }

    @Test
    fun `同 URL 计算结果确定性一致`() {
        val url = "https://cdn.mhimg.net/media/photos/520000/xyz789.jpg"
        val a = ImagePipelinePolicy.calculateJmScrambleNum(url)
        val b = ImagePipelinePolicy.calculateJmScrambleNum(url)
        assertEquals(a, b)
    }

    // ---------- EH 雪碧图裁切指令解析 ----------

    @Test
    fun `cropRange 完整 xy 区间`() {
        val r = ImagePipelinePolicy.parseCropRange("x=100-300&y=0-140")
        assertNotNull(r)
        assertEquals(100, r!!.x1)
        assertEquals(300, r.x2)
        assertEquals(0, r.y1)
        assertEquals(140, r.y2)
    }

    @Test
    fun `cropRange 仅 x 时 y 取全高`() {
        val r = ImagePipelinePolicy.parseCropRange("x=5-25")
        assertNotNull(r)
        assertEquals(5, r!!.x1)
        assertEquals(25, r.x2)
        assertEquals(0, r.y1)
        assertEquals(Int.MAX_VALUE, r.y2)
    }

    @Test
    fun `cropRange 无指令返回 null`() {
        assertNull(ImagePipelinePolicy.parseCropRange("foo=1&bar=2"))
    }

    @Test
    fun `cropRange 非法数字忽略该字段`() {
        // x 区间非法被忽略，y 仍有效 => 返回仅含 y 的区间（x 取默认全宽）
        val r = ImagePipelinePolicy.parseCropRange("x=a-b&y=10-20")
        assertNotNull(r)
        assertEquals(0, r!!.x1)
        assertEquals(Int.MAX_VALUE, r.x2)
        assertEquals(10, r.y1)
        assertEquals(20, r.y2)
    }
}
