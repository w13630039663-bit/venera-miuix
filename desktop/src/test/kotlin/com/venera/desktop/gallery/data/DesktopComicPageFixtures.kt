package com.venera.desktop.gallery.data

import java.io.File

/**
 * 第一百一十批 · 测试用的"页桩"工厂 —— 只有一处定义，四颗尺子共用。
 *
 * ## 为什么要抽这一颗
 * 这批把「这一页在不在」从"名字 + 非零长度"换成"名字 + 尺寸 + **头部是图**"，
 * 于是仓库里那几处随手 `writeBytes(byteArrayOf(7))` 的假页**一律不算已有**了。
 * 如果逐颗文件各补各的桩，下一批改判又会漏掉某一颗（本仓那一族"三处各数各的"就是这么长出来的）。
 *
 * ## 两档桩各自管什么
 * - [wholePageBytes]：算"已有"的那一档 —— JPEG 头 + 补到 2048 字节（对岸那把下界是 `> 1024`）。
 * - [brokenPageBytes]：名字对、尺寸也够，而**头部不是图**（过盾页 HTML）—— 这一档就是本批的病根。
 */
internal object DesktopComicPageFixtures {

    private const val JPEG_HEAD: Byte = 0xFF.toByte()

    /** 一颗算"已有"的页桩：JPEG 魔数开头 + 补到 [total] 字节。 */
    fun wholePageBytes(total: Int = 2048): ByteArray {
        val bytes = ByteArray(total)
        byteArrayOf(JPEG_HEAD, 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 'J'.code.toByte(), 'F'.code.toByte(),
            'I'.code.toByte(), 'F'.code.toByte(), 0, 1, 2, 3).copyInto(bytes)
        return bytes
    }

    /** 名字对、尺寸够，而内容是一张过盾页 —— 新尺子必须把它判成"没有"。 */
    fun brokenPageBytes(): ByteArray =
        "<html><head><title>Just a moment...</title></head><body>cf</body></html>".toByteArray().copyOf(4096)

    /** 按**页序号**（0 基数，与写侧同一口径）写一颗好页。 */
    fun writeWholePage(dir: File, index: Int, total: Int = 2048): File =
        File(dir, desktopComicChapterPageFileName(index)).also { it.writeBytes(wholePageBytes(total)) }

    /** 按**页序号**写一颗坏页（过盾页）。 */
    fun writeBrokenPage(dir: File, index: Int): File =
        File(dir, desktopComicChapterPageFileName(index)).also { it.writeBytes(brokenPageBytes()) }

    /**
     * "站方回的那一串字节"桩 —— 给 `desktopComicDownloadChapter` 的取页闭包用。
     *
     * 与 [wholePageBytes] 同一档（图像头 + 够大），只是把序号留在第 4 格，好让逐页断言还能分得出是谁。
     * ⚠️ 这一档从前是 12 字节：批 110 把"这一页在不在"抬到"名字 + 尺寸 + 头部是图"之后，
     * 12 字节那一串**写下去也不算已有** ⇒ 凡是要走到"清单说齐"的趟子都必须吃这一颗。
     */
    fun stubPageBytes(index: Int, total: Int = 2048): ByteArray {
        val bytes = wholePageBytes(total)
        byteArrayOf(JPEG_HEAD, 0xD8.toByte(), 0xFF.toByte(), index.toByte()).copyInto(bytes)
        return bytes
    }
}
