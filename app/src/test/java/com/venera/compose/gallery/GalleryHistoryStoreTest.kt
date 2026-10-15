package com.venera.compose.gallery

import com.venera.compose.data.platform.PathProvider
import com.venera.compose.gallery.data.GalleryHistoryStore
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 画廊浏览历史的**落盘链**（[GalleryHistoryStore]）。
 *
 * 规格逐条对着 `GalleryArtistFollowsStore` 那份立：构造期同步读一次、写盘走 tmp 改名、
 * 解不开先留档再开空档。这里钉的都是"错了不会有人发现"那几档：
 * ① 第二次写入必须真替换盘上那份 —— 从前用 `renameTo`，Windows 上目标已存在时它**返回 false
 * 而不替换**，于是从第二张图起就再没写进过盘，而屏上一切正常；
 * ② 坏档不许静默覆盖（覆盖了就是把用户攒的名单删了）；
 * ③ 认不出的站点键那行必须当场摘掉 —— 留着它，那一格永远点不开。
 */
class GalleryHistoryStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class TempPaths(root: File) : PathProvider {
        override val dataRoot: File = root
        override val cacheRoot: File = root
    }

    private val file get() = File(tmp.root, "gallery_browse_history.json")

    private fun store(): GalleryHistoryStore = GalleryHistoryStore(TempPaths(tmp.root))

    private fun post(
        site: GallerySite = GallerySite.YANDERE,
        id: Long = 1L,
        w: Int = 1000,
        h: Int = 2000,
    ) = GalleryPost(
        site = site,
        id = id,
        previewUrl = "https://cdn/$site-$id.jpg",
        // 1000×2000 = 0.5 是二进制精确的那一档，比例回环断言才能不带容差。
        width = w,
        height = h,
    )

    @Test
    fun `记一笔就落盘，重开一份读得回来`() {
        runBlocking { store().recordView(post(id = 7L), at = 1_700_000_000_000L) }

        assertTrue("盘上没有那份档", file.exists())
        val reopened = store()
        assertEquals(1, reopened.rows.value.size)
        assertEquals(7L, reopened.rows.value.single().postId)
        assertEquals(1_700_000_000_000L, reopened.rows.value.single().viewedAt)
    }

    @Test
    fun `同一张反复看只挪时刻，不多占一行`() {
        runBlocking {
            val s = store()
            s.recordView(post(id = 3L), at = 100L)
            s.recordView(post(id = 3L), at = 200L)
        }

        val reopened = store()
        assertEquals(1, reopened.rows.value.size)
        assertEquals(200L, reopened.rows.value.single().viewedAt)
    }

    @Test
    fun `第二笔真替换了盘上那一份`() {
        // 这一条就是 Windows 那格的尺子：改名不替换时，屏上两张都在、盘上只有一张，
        // 重启之后丢的那张没有任何报错。
        runBlocking {
            val s = store()
            s.recordView(post(id = 1L), at = 100L)
            s.recordView(post(id = 2L), at = 200L)
        }

        assertEquals(2, store().rows.value.size)
    }

    @Test
    fun `清空是写空名单，重开还是空`() {
        runBlocking {
            val s = store()
            s.recordView(post(id = 1L), at = 100L)
            s.clear()
        }

        assertEquals(emptyList<Any>(), store().rows.value)
        assertTrue("清空之后档本身还在（读侧要能认出这是空档不是坏档）", file.exists())
    }

    @Test
    fun `坏档先留档再开空档，那句话只说一次`() {
        file.writeText("{这半个 JSON 是我手抖写的")

        val s = store()

        assertEquals(emptyList<Any>(), s.rows.value)
        val notice = s.consumeNotice()
        assertNotNull("坏档必须说出来", notice)
        assertNull("读走即清：同一句不许说第二遍", s.consumeNotice())
        // 留档改名必须真发生 —— 那份就是用户找回记录的唯一去处。
        val backups = tmp.root.listFiles { f: File -> f.name.contains(".corrupt-") }
        assertEquals("没有留下坏档副本", 1, backups?.size)
        assertEquals("留档里就是原来那串", "{这半个 JSON 是我手抖写的", backups!!.single().readText())
    }

    @Test
    fun `认不出的站点键那一行当场摘掉`() {
        // 老档写过别的键、或被人手工改坏：留着那一行，历史墙上就有一格永远点不开。
        file.writeText(
            """[{"site":"danbooru","id":9,"thumb":"t9","viewed_at":5},
               {"site":"yandere","id":8,"thumb":"t8","viewed_at":6}]"""
                .trimIndent(),
        )

        val rows = store().rows.value

        assertEquals(1, rows.size)
        assertEquals(GallerySite.YANDERE, rows.single().site)
        assertEquals(8L, rows.single().postId)
    }

    @Test
    fun `比例跟着走，重开还是那一档`() {
        runBlocking { store().recordView(post(id = 11L), at = 100L) }

        val saved = store().rows.value.single()
        // 站方那三档尺寸里挑出来的一个数（1000×2000 = 0.5），不是两个数。
        assertEquals(0.5f, saved.ratio)
        assertEquals(0.5f, store().rows.value.single().ratio)
    }

    @Test
    fun `昨天那种没有 ratio 键的档照读，那一格算 0`() {
        // 2026-10-15 才加的字段：加之前写下的档必须照读，不能因为缺键就整档判坏留档重来
        // （那等于把用户攒的历史打回空名单，代价与"读不出来"同档）。
        file.writeText(
            """[{"site":"yandere","id":5,"thumb":"t5","viewed_at":70}]""",
        )

        val rows = store().rows.value

        assertEquals(1, rows.size)
        assertEquals(0f, rows.single().ratio)
        assertEquals(70L, rows.single().viewedAt)
    }

    @Test
    fun `按 uid 摘掉选中的那几行，其余照旧落盘`() {
        runBlocking {
            val s = store()
            s.recordView(post(site = GallerySite.YANDERE, id = 1L), at = 10L)
            s.recordView(post(site = GallerySite.GELBOORU, id = 2L), at = 20L)
            s.recordView(post(site = GallerySite.SAFEBOORU, id = 3L), at = 30L)
            s.removeAll(listOf("gelbooru:2"))
        }

        val reopened = store().rows.value
        assertEquals("摘多了或摘歪了", listOf(3L, 1L), reopened.map { it.postId })
        // 与 clear() 是两条路：整表清空与按 uid 摘不能互相顶替。
        assertTrue("档本身还在（只摘了一行，不是清空）", file.exists())
    }

    @Test
    fun `摘一份空集合不碰盘`() {
        runBlocking {
            val s = store()
            s.recordView(post(id = 1L), at = 10L)
        }
        val before = file.readText()

        runBlocking { store().removeAll(emptyList()) }

        assertEquals("removeAll 收了空名单还去写盘", before, file.readText())
    }

    @Test
    fun `盘上同一条的两行读进来收成一份，留最近那次`() {
        file.writeText(
            """[{"site":"yandere","id":4,"thumb":"old","viewed_at":10},
               {"site":"yandere","id":4,"thumb":"new","viewed_at":90}]"""
                .trimIndent(),
        )

        val rows = store().rows.value

        assertEquals(1, rows.size)
        assertEquals("new", rows.single().thumbUrl)
        assertEquals(90L, rows.single().viewedAt)
    }
}
