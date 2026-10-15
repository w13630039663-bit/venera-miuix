package com.venera.compose.gallery

import com.venera.compose.gallery.ui.GalleryExportTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 多选导出那一句读数的尺子（`GalleryExportTally.readout`）。
 *
 * 立它的理由不是"文案要有人看着"，是**四类失败必须各归各的**：
 * 没取回来（网络/凭据）、站方说没这条（本子被删或合并）、被规则挡下（用户自己的屏蔽设置）、
 * 没存进去（空间/权限）—— 这四件事的修法完全不同，合成一句"部分没成功"就读不出该动哪一手。
 * 而屏上只有一行 Toast，谁会不会被合并、零数那一档会不会噪声化，全靠这几条钉住。
 */
class GalleryExportTallyTest {

    private fun tally(
        handed: Int,
        fetchFailed: Int = 0,
        notOnSite: Int = 0,
        blocked: Int = 0,
        saveFailed: Int = 0,
        asDownload: Boolean = true,
    ) = GalleryExportTally(
        handed = handed,
        fetchFailed = fetchFailed,
        notOnSite = notOnSite,
        blocked = blocked,
        saveFailed = saveFailed,
        asDownload = asDownload,
    )

    @Test
    fun `全成功时只念成功数，不带一句失败尾巴`() {
        assertEquals("存好 3 张", tally(handed = 3).readout())
        assertEquals("带上 3 张", tally(handed = 3, asDownload = false).readout())
    }

    @Test
    fun `下载与共享是两个动词，不许都念成保存`() {
        // 共享那一路没有"存"这件事：念成"存好"就是把用户带偏（他会去相册找那几张）。
        assertEquals("带上 2 张", tally(handed = 2, asDownload = false).readout())
        assertEquals("存好 2 张", tally(handed = 2, asDownload = true).readout())
    }

    @Test
    fun `四类失败各念各的，一条不漏`() {
        val line = tally(
            handed = 1,
            fetchFailed = 1,
            notOnSite = 2,
            blocked = 3,
            saveFailed = 4,
        ).readout()

        assertEquals(
            "存好 1 张 · 没带上的 10 张：1 张没取回来、2 张站方说没这条、3 张被你的屏蔽规则挡下、4 张没存进去",
            line,
        )
    }

    @Test
    fun `零数那一档不进句子`() {
        val line = tally(handed = 2, blocked = 1).readout()

        // "0 张没取回来"这种话是噪声；只念真有的那一档。
        assertFalse(line, line.contains("没取回来"))
        assertFalse(line, line.contains("站方说没这条"))
        assertFalse(line, line.contains("没存进去"))
        assertEquals("存好 2 张 · 没带上的 1 张：1 张被你的屏蔽规则挡下", line)
    }

    @Test
    fun `尾巴上的总数等于四类之和`() {
        // 这句是"到底少了几张"的唯一读数；它要是和分项对不上，用户按哪一项都不对。
        val line = tally(handed = 0, fetchFailed = 2, notOnSite = 3, blocked = 4, saveFailed = 5).readout()

        assertEquals("没带上的 14 张", line.substringAfter("· ").substringBefore("："))
    }
}
