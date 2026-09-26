package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.ui.GalleryViewerQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 大图页左右翻页的**交接槽**判据。
 *
 * 这块逻辑是纯内存的，所以能在单测里钉住 —— 而它恰好是"翻到一张从没见过的图"的守门人：
 * 深链进来、或工作区的列表已经是上一面墙的残留时，**必须返回空**让大图页退回单张模式，
 * 宁可不给翻页，也不能拿一组不相干的图当邻居。
 */
class GalleryViewerQueueTest {

    private fun post(site: GallerySite, id: Long) = GalleryPost(site = site, id = id)

    @Test
    fun `同墙的那条能取回整面墙且顺序不变`() {
        val wall = listOf(
            post(GallerySite.YANDERE, 11),
            post(GallerySite.YANDERE, 22),
            post(GallerySite.YANDERE, 33),
        )
        GalleryViewerQueue.set(wall)

        val got = GalleryViewerQueue.snapshot(GallerySite.YANDERE, 22)
        assertEquals(wall.map { it.uid }, got.map { it.uid })
    }

    @Test
    fun `不在这一组里就退回单张`() {
        GalleryViewerQueue.set(listOf(post(GallerySite.YANDERE, 11)))

        // 深链进来的那一条：工作区里剩下的是上次打开留下的列表，认不出它。
        assertTrue(GalleryViewerQueue.snapshot(GallerySite.YANDERE, 999).isEmpty())
    }

    @Test
    fun `换过站之后残留的列表不会被当成邻居`() {
        GalleryViewerQueue.set(listOf(post(GallerySite.YANDERE, 777)))

        // 两站 id 各自独立编号，逐站比对才挡得住"站变了、id 撞上了"。
        assertTrue(GalleryViewerQueue.snapshot(GallerySite.GELBOORU, 777).isEmpty())
    }

    @Test
    fun `两站同号互不串台`() {
        val wall = listOf(
            post(GallerySite.GELBOORU, 123),
            post(GallerySite.GELBOORU, 456),
        )
        GalleryViewerQueue.set(wall)

        assertEquals(2, GalleryViewerQueue.snapshot(GallerySite.GELBOORU, 123).size)
        assertTrue(GalleryViewerQueue.snapshot(GallerySite.YANDERE, 123).isEmpty())
    }

    @Test
    fun `空列表写进来等于没有可翻的上下文`() {
        GalleryViewerQueue.set(listOf(post(GallerySite.YANDERE, 5)))
        // 一面墙可能被过滤到只剩 0 张时打开（理论上不该发生），这时不该留下上一轮的残影。
        GalleryViewerQueue.set(emptyList())

        assertTrue(GalleryViewerQueue.snapshot(GallerySite.YANDERE, 5).isEmpty())
    }
}
