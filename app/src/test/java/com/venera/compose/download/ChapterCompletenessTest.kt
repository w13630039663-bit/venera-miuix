package com.venera.compose.download

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「这一章下完了没」的行为锁。
 *
 * 旧判据是"目录里有图就算完"，而 `chapter.json` 在**开下载之前**就写 ——
 * 于是"下到 5/40 被取消"与"下完 40/40"在磁盘上长得一模一样。
 * 三处消费点（详情页绿色徽章、批量下载的默认选中、阅读器直接开离线）一起把
 * 一本截断的书当成完整书，而且因为挂着"已下载"，它永远不会被重下。
 */
class ChapterCompletenessTest {

    @Test
    fun `只下一半不算已下载`() {
        assertEquals(
            ChapterOffline.PARTIAL,
            chapterOfflineState(expectedPages = 40, complete = false, pageCount = 5),
        )
    }

    @Test
    fun `页够数就算完 不必等 complete 标记`() {
        // complete 是加速路径；数得够就是齐的（老版本下完的章没有这个标记）。
        assertEquals(
            ChapterOffline.COMPLETE,
            chapterOfflineState(expectedPages = 40, complete = false, pageCount = 40),
        )
        assertEquals(
            ChapterOffline.COMPLETE,
            chapterOfflineState(expectedPages = 40, complete = false, pageCount = 42),
        )
    }

    @Test
    fun `complete 标记在 页也齐`() {
        assertEquals(
            ChapterOffline.COMPLETE,
            chapterOfflineState(expectedPages = 40, complete = true, pageCount = 40),
        )
    }

    @Test
    fun `两个标记都没有的老数据保持今日语义`() {
        // ⚠️ 这条是迁移判据：没有 expectedPages 的章是这次改动之前下的，
        // 判成 PARTIAL 会让设备上**所有现存离线章**一夜之间变成"未下载"，
        // 用户看到的是"我的离线书全没了 / 又要重下一遍"。
        assertEquals(
            ChapterOffline.COMPLETE,
            chapterOfflineState(expectedPages = null, complete = false, pageCount = 3),
        )
        // expectedPages = 0 同样不可信（页列表都没取到就写了元数据）。
        assertEquals(
            ChapterOffline.COMPLETE,
            chapterOfflineState(expectedPages = 0, complete = false, pageCount = 3),
        )
    }

    @Test
    fun `一页都没有就是没下 不管标记写了什么`() {
        assertEquals(ChapterOffline.NONE, chapterOfflineState(expectedPages = 40, complete = false, pageCount = 0))
        // complete=true 但目录空：文件被用户手动删过。这时"没下"比"下完了"诚实。
        assertEquals(ChapterOffline.NONE, chapterOfflineState(expectedPages = 40, complete = true, pageCount = 0))
    }
}
