package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivPage
import com.venera.compose.gallery.data.PixivWorkDetail
import com.venera.compose.gallery.domain.PixivWorkPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 批 B⑥「不等落点」的算式级判据（2026-10-19）。
 *
 * 真机旧病：慢首开详情 700ms > 等落点预算 600ms ⇒ 排行榜那一格"点卡进去压根不飞"
 * （§9.3 读数里 `起飞` 那行压根没打）。poxez 那边的凭据（§4.3 第 1 条）是
 * **首帧就有真比例，落点第一帧量得出来**。
 *
 * 这颗尺钉的是这条凭据在本仓的形状：种子那一格（`seedDetailOf`）与真详情第 0 页
 * **键逐字相等**、**比例同源**，于是"等网络"被搬到了打开之前，其余一张脸都不许借种子顶。
 */
class GalleryEntrySeedTest {

    private fun post(
        w: Int = 2400,
        h: Int = 4000,
        preview: String = "https://i.pximg.net/c/250x250/img-master/150944479_p0_master1200.jpg",
    ) = GalleryPost(
        site = GallerySite.PIXIV,
        id = 150944479L,
        author = "jaymonoto",
        width = w,
        height = h,
        previewUrl = preview,
        fileUrl = "https://i.pximg.net/img-original/img/2026/10/01/15/12/34/150944479_p0.png",
    )

    private fun detail(pages: List<PixivPage>) = PixivWorkDetail(
        post = post(),
        title = "標題",
        authorId = 12956047L,
        authorAccount = "jaymonoto",
        pages = pages,
        pageCount = pages.size,
        tags = listOf("原神", "女の子"),
        viewCount = 851,
        uploadDate = "2026-10-01T06:12:00+00:00",
        commentCount = 6,
        commentsOff = false,
        ugoira = false,
    )

    // 仓内既有量具的口径（见 `GalleryPixivPageAxisTest` / `GalleryPixivWorkPageWiringTest`）
    // 是 `RepoSources.appMain` 之类。这颗尺要扫的屏文件在 repo 根下四层，
    // 所以借 `RepoSources.root` 那颗（仓里只此一份根定位，不另写第二把）。
    private val repoRoot: File = com.venera.compose.testsupport.RepoSources.root

    private fun src(rel: String): List<String> {
        val f = File(repoRoot, rel)
        check(f.isFile) { "找不到 $rel（root=${repoRoot.absolutePath}）" }
        return f.readText().replace("\r\n", "\n").lines()
            .map { it.trim() }
            .filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }
    }

    private val screen = "app/src/main/java/com/venera/compose/gallery/ui/PixivWorkScreen.kt"
    private val domain = "app/src/main/java/com/venera/compose/gallery/domain/PixivWorkPage.kt"

    // ── 一、键同源：种子与真详情第 0 页的逐页键逐字相等 ──────────────────

    @Test
    fun `种子与真详情第零页的逐页键逐字相等`() {
        val seed = PixivWorkPage.seedDetailOf(post())
        val real = detail(listOf(PixivPage("https://m/p0.jpg", "https://o/p0.png", 2400, 4000)))
        assertEquals(
            "键不相等的话，等落点与登记落点就是两处真相",
            PixivWorkPage.pageWallOf(real).first().viewerKey,
            PixivWorkPage.pageWallOf(seed).first().viewerKey,
        )
        assertEquals("pixiv:150944479:p0", PixivWorkPage.pageWallOf(seed).first().viewerKey)
    }

    @Test
    fun `种子的第零页吃列表腿预览档，原图档留给HD`() {
        val p = post()
        val seed = PixivWorkPage.seedDetailOf(p)
        assertEquals(p.previewUrl, seed.pages.first().mediumUrl)
        assertEquals("原图档仍交真原图（详情永不到而点了首帧那一格时，播放器 HD 是真的）",
            p.fileUrl, seed.pages.first().originalUrl)
    }

    @Test
    fun `种子的比例就是站方报的原图比例，与中档同形`() {
        val seed = PixivWorkPage.seedDetailOf(post())
        val aspect = PixivWorkPage.placeholderAspect(seed.pages.first().width, seed.pages.first().height)
        assertEquals(2400f / 4000f, aspect!!, 0.0001f)
        // 与真详情第 0 页同比例 ⇒ 格子不跳、落点稳（"大图到位是变清晰，不是跳一下"）
        val real = detail(listOf(PixivPage("https://m/p0.jpg", "https://o/p0.png", 1200, 2000)))
        val realAspect = PixivWorkPage.placeholderAspect(
            real.pages.first().width, real.pages.first().height,
        )
        assertEquals(aspect!!, realAspect!!, 0.0001f)
    }

    @Test
    fun `站方没报宽高就不预留比例`() {
        val seed = PixivWorkPage.seedDetailOf(post(w = 0, h = 0))
        assertNull(
            "拿 1:1 顶一个就是替站方编一个形状",
            PixivWorkPage.placeholderAspect(seed.pages.first().width, seed.pages.first().height),
        )
    }

    // ── 二、不许借种子顶任何一张"读数的脸" ──────────────────────────────

    @Test
    fun `种子不借标题计数标签页数任何一格`() {
        val seed = PixivWorkPage.seedDetailOf(post())
        assertEquals("", seed.title)
        assertTrue("三格计数一格格都不许出现", seed.viewCount == null && seed.uploadDate == null)
        assertTrue("评论那一节压根不摆", seed.commentCount == null)
        assertTrue("标签节没有可摆的枚数", seed.tags.isEmpty())
        assertTrue("关注与看这位作品两把手都不摆", seed.authorId == null)
        assertTrue("ugoira 判据只认真详情", !seed.ugoira)
    }

    @Test
    fun `种子的页数取列表腿真值，站方没给就零`() {
        val real = post().copy(pageCount = 3)
        assertEquals(3, PixivWorkPage.seedDetailOf(real).pageCount)
        assertEquals(
            "0 会让页码语义交空串，屏上不编『共几页』",
            0,
            PixivWorkPage.seedDetailOf(post()).pageCount,
        )
    }

    // ── 三、接线尺：详情不再是起飞前置，但读数仍只认真详情 ────────────────

    @Test
    fun `作品页等落点不再把详情当前置`() {
        val lines = src(screen)
        val code = lines.joinToString("\n")
        assertEquals(
            "详情不再是起飞前置（批 B⑥：首帧那一格由种子摆出来）",
            0,
            code.split("if (detail == null) return@LaunchedEffect").size - 1,
        )
        // 但详情到 / 不到这件事本身仍在屏上：详情段、ugoira 分流、页码语义三处都只认真那份
        assertTrue(
            "详情段仍只认真 detail（不许借空标题空计数摆一张脸）",
            lines.any { it.contains("if (detail != null && detailsInFlow)") },
        )
        assertTrue(lines.any { it.contains("if (detail?.ugoira == true)") })
    }

    @Test
    fun `种子只单开一枚入参，且只在没有详情时递进流里`() {
        val lines = src(screen)
        assertEquals(
            "种子单独一枚参数（复用 detail 会把详情段那一支也顶出来）",
            1,
            lines.count { it.contains("seedDetail: PixivWorkDetail? = null,") },
        )
        assertEquals(
            "宿主只在这一槽没有详情时递种子",
            1,
            lines.count { it.contains("val slotSeed = if (details[slotId] != null) null") },
        )
    }

    @Test
    fun `页那一支画种子，页码语义与点击出口仍只认真详情`() {
        val lines = src(screen)
        // 画哪几页：详情或种子二选一
        assertEquals(
            1,
            lines.count { it.contains("val pages = detail?.pages.orEmpty().ifEmpty {") },
        )
        // 页码语义不许借种子念"共几页"
        assertEquals(
            1,
            lines.count { it.contains("description = if (detail != null) pageSemantic(index, pages.size) else \"\"") },
        )
    }

    @Test
    fun `页轴种子只在进水印槽不换锚：归零只认真页`() {
        val lines = src(screen)
        assertEquals(
            "锚归零的条件要挡住种子那一格（否则详情到手时不归零 = 第一页被顶出屏外）",
            1,
            lines.count { it.contains("pages.isNotEmpty() && !pagesAppeared && detail != null") },
        )
    }

    @Test
    fun `种子页的落点键由同一颗domain算式长，屏上不另拼`() {
        val lines = src(domain)
        assertEquals(
            "路线图那颗：种子走 pageWallOf 同一颗算式（屏上不许拼 \":p0\"）",
            1,
            lines.count { it.contains("fun seedDetailOf(post: GalleryPost): PixivWorkDetail") },
        )
        // 落点键与登记键同一颗函数 ⇒ 批 B⑥ 的"键逐字相等"不是巧合而是构造出来的
        assertTrue(lines.any { it.contains("PixivWorkDetail(") })
    }
}
