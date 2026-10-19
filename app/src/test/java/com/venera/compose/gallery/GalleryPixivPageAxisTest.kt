package com.venera.compose.gallery

import androidx.compose.ui.unit.dp
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivPage
import com.venera.compose.gallery.data.PixivWorkDetail
import com.venera.compose.gallery.domain.PixivWorkPage
import com.venera.compose.gallery.ui.GalleryPageAxis
import com.venera.compose.gallery.ui.galleryCacheKey
import com.venera.compose.gallery.ui.galleryNativeZoomFactor
import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 批 228-B：pixiv 作品页「点图进大图」那一整条路（2026-10-18）。
 *
 * 用户口径三句：
 * - 「点击图片单独显示高清点的图片（复用图库的大图播放器就行），支持左右滑动切换本作品的多张图片，
 *   **不能切换其他作品**，HD 才显示原图，点信息面板用回为 p 站做的信息面板」；
 * - 「大图模式下能看出当前是第几页」（页码那一枚复用现成的那颗，不另画）；
 * - 「双击放大图片应该是放大整个图片而不是限制在一个大小里放大」⇒ 档位循环「适应屏宽 ↔ 原图像素 1:1」。
 *
 * 这一条路最难错的地方是**四页共用同一个作品号**：收藏、历史、去重都按 `uid` 记（那是"这一条作品"，
 * 正确），而缓存键、HD 那一档、"这一张正在放大"那三处必须**逐页**分开 —— 混一处就是
 * "翻到第 2 页画的还是第 1 页"或者"第 1 页开了 HD，第 2 页也跟着亮但画的是中档"。
 */
class GalleryPixivPageAxisTest {

    private val appMain = RepoSources.appMain

    private fun src(rel: String): String {
        val f = File(appMain, rel)
        assertTrue("找不到 $rel，量具失效", f.isFile)
        return f.readText().replace("\r\n", "\n")
    }

    /** 注释行不参与匹配（与 `TokenBudgetFrozenTest` 同一把尺子）。 */
    private fun code(rel: String): List<String> = src(rel).lines().map { it.trim() }
        .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private fun List<String>.countWith(needle: String): Int = count { it.contains(needle) }

    private val screen = "com/venera/compose/gallery/ui/PixivWorkScreen.kt"
    private val viewer = "com/venera/compose/gallery/ui/GalleryPostScreen.kt"

    private val head = GalleryPost(
        site = GallerySite.PIXIV,
        id = 150320368L,
        author = "jaymonoto",
        width = 1365,
        height = 920,
        previewUrl = "https://i.pximg.net/c/250x250/img-master/150320368_p0_master1200.jpg",
        largeUrl = "https://i.pximg.net/c/1200x1200/img-master/150320368_p0_master1200.jpg",
        fileUrl = "https://i.pximg.net/img-original/img/150320368_p0.png",
        fileExt = "png",
    )

    private fun detail(pages: List<PixivPage>) = PixivWorkDetail(
        post = head,
        title = "標題",
        authorId = 12956047L,
        authorAccount = "jaymonoto",
        pages = pages,
        pageCount = pages.size,
        tags = emptyList(),
        viewCount = null,
        uploadDate = null,
        commentCount = null,
        commentsOff = false,
        ugoira = false,
    )

    private val threePages = listOf(
        PixivPage("https://m/150320368_p0.webp", "https://o/150320368_p0.png", 1365, 920),
        PixivPage("https://m/150320368_p1.webp", "https://o/150320368_p1.jpg", 1365, 920),
        PixivPage("https://m/150320368_p2.webp", "https://o/150320368_p2.png", 900, 1200),
    )

    // ── 一、页轴那一面墙怎么从详情模型长出来 ────────────────────────────

    @Test
    fun `每一页各成一枚行模型，中档走 large 档而原图只住 fileUrl`() {
        val wall = PixivWorkPage.pageWallOf(detail(threePages))
        assertEquals(3, wall.size)
        val second = wall[1]
        // 这一屏默认画的就是中档：fast/large 两档都指它，HD 才碰 fileUrl。
        assertEquals(threePages[1].mediumUrl, second.fastUrl)
        assertEquals(threePages[1].mediumUrl, second.largeUrl)
        assertEquals(threePages[1].mediumUrl, second.previewUrl)
        assertEquals(threePages[1].originalUrl, second.fileUrl)
        assertNotEquals("HD 那一档必须真的换到原图地址", second.largeUrl, second.fileUrl)
        // 逐页宽高：占位与 1:1 那一档都吃它，拿作品首叶的尺寸顶到第 3 页就是假比例。
        assertEquals(900, wall[2].width)
        assertEquals(1200, wall[2].height)
    }

    @Test
    fun `扩展名逐页从地址尾巴取，不沿用第 0 页那一档`() {
        // 现量：同一位画师的两张原图一张 `.png`、另一张 `.jpg` —— 写死会把 jpg 存成 png。
        assertEquals("jpg", PixivWorkPage.pageWallOf(detail(threePages))[1].fileExt)
        assertEquals("png", PixivWorkPage.pageWallOf(detail(threePages))[2].fileExt)
    }

    @Test
    fun `页位逐枚标上而身份仍是那一条作品`() {
        val wall = PixivWorkPage.pageWallOf(detail(threePages))
        assertEquals(listOf(0, 1, 2), wall.map { it.pageOrdinal })
        assertEquals(listOf(3, 3, 3), wall.map { it.pageCount })
        // 收藏 / 历史 / 去重吃的都是 `uid`：翻到第 3 页不等于收藏了第 3 页。
        assertEquals(listOf("pixiv:150320368", "pixiv:150320368", "pixiv:150320368"), wall.map { it.uid })
        // 而播放器里"逐张"的那几处（HD 记忆、放大态）吃 `viewerKey` —— 四页必须四个值。
        assertEquals(3, wall.map { it.viewerKey }.distinct().size)
    }

    @Test
    fun `非页轴的那一枚 viewerKey 就是 uid（图库三站逐字不变）`() {
        assertEquals(head.uid, head.viewerKey)
        assertNull(head.pageOrdinal)
    }

    // ── 二、缓存键：页位进键，而旧串一个字都不动 ────────────────────────

    @Test
    fun `同一作的四页不吃同一枚缓存键`() {
        val wall = PixivWorkPage.pageWallOf(detail(threePages))
        assertEquals(3, wall.map { galleryCacheKey("large", it) }.distinct().size)
        assertEquals(3, wall.map { galleryCacheKey("file", it) }.distinct().size)
    }

    @Test
    fun `没有页位的那些算出来的键与从前逐字相同`() {
        // 这条钉的是"booru 零变化"：水彩那颗、三档叠画那颗与历史那几处都吃这一颗函数，
        // 一旦旧串漂了，全站已落盘的图片缓存会集体当新键重下一遍。
        assertEquals("gallery-large-pixiv-150320368", galleryCacheKey("large", head))
        assertEquals("gallery-file-yandere-7", galleryCacheKey("file", head.copy(site = GallerySite.YANDERE, id = 7L)))
    }

    // ── 三、交接那颗：认不出就不给，别拿不相干的页当邻居 ────────────────

    @Test
    fun `页轴只在同一站同一号上应答，认不出退回 null`() {
        val wall = PixivWorkPage.pageWallOf(detail(threePages))
        GalleryPageAxis.open(
            GalleryPageAxis.Wall(
                site = GallerySite.PIXIV,
                postId = 150320368L,
                startIndex = 2,
                pages = wall,
                panelData = null,
                infoPanel = null,
            ),
        )
        val got = GalleryPageAxis.peek(GallerySite.PIXIV, 150320368L)
        assertTrue("同站同号要答上", got != null)
        assertEquals(3, got!!.pages.size)
        assertEquals(2, got.startIndex)
        // 别的号 / 别的站都不许答：同号码在另一站是另一幅画（这一族判据在 GallerySite 上写过）。
        assertNull(GalleryPageAxis.peek(GallerySite.PIXIV, 999L))
        assertNull(GalleryPageAxis.peek(GallerySite.YANDERE, 150320368L))
    }

    // ── 四、双击那一档：原图像素 1:1，算不出来就不摆 ─────────────────────

    @Test
    fun `1 比 1 那一档是原图宽除以屏上那一框的宽`() {
        assertEquals(2000f / 1080f, galleryNativeZoomFactor(2000, 1080), 0.0001f)
        // 原图比屏还小 ⇒ 算出来 ≤1：那一档就是"当前这一屏"，双击按下去没变化 = 假开关，撤。
        assertTrue(galleryNativeZoomFactor(800, 1080) <= 1f)
    }

    @Test
    fun `退化输入退 1 倍，不许漏出除零与负数`() {
        listOf(
            galleryNativeZoomFactor(0, 1080),
            galleryNativeZoomFactor(2000, 0),
            galleryNativeZoomFactor(-5, 1080),
            galleryNativeZoomFactor(2000, -1),
        ).forEach { got ->
            assertTrue("退化输入报出了 $got", got.isFinite() && got >= 1f)
        }
    }

    // ── 五、接线尺 ─────────────────────────────────────────────────────

    /** 作品页那一支挂点击，并且页轴在起跳前写好；原图档仍只在 domain 那一颗里出现。 */
    @Test
    fun `作品页那一支点得进播放器，页轴在起跳前写好`() {
        val body = code(screen)
        // 2026-10-18 hero 那一轮：点击要把**这一页此刻的窗口矩形**一起交出去（去程起点重新截），
        // lambda 多了一枚 `rect`。**同日第四批**按用户那句「只留竖着连续翻页模式」撤掉横向那一档 ⇒
        // 两支成一档，条数从 2 改到 1（意图没变：屏上那几页每一页都得点得开）。
        //
        // 2026-10-19：动图那一支也摆上了第一帧（用户「整屏上半是白的」），而那一帧复用**同一颗**
        // `PixivPageImage`、同样交 `openPages` ⇒ 点击出口成两处。意图没变（每一页都点得开），
        // 所以这里是**数出口**而不是数行：钉的是"每一支挂载点都交同一个出口"，不是"只有一支"。
        assertEquals("连读那一档的每一页都得能点开", 2, body.countWith("onClick = { rect -> openPages("))
        assertEquals("交接只写一处", 1, body.countWith("GalleryPageAxis.open("))
        assertEquals("那一面墙由 domain 那颗纯函数长出来", 1, body.countWith("PixivWorkPage.pageWallOf("))
        // 屏上仍不碰原图档（第一批那条尺子的延续）：页轴的 fileUrl 由共用那颗函数填。
        assertEquals("作品页自己不许取原图档", 0, body.countWith("originalUrl"))
    }

    /** 播放器吃页轴优先，邻居墙那一路逐字不变；页码与下载复用现成那两颗。 */
    @Test
    fun `播放器在页轴时只翻本作品这几页`() {
        val body = code(viewer)
        assertEquals("页轴只读一次", 1, body.countWith("GalleryPageAxis.peek("))
        assertEquals("页轴优先于邻居墙 —— 翻到别条作品就把「不能切换其他作品」那句违反了",
            1, body.countWith("axis?.pages ?: GalleryViewerQueue.snapshot("))
        assertEquals("落点用交接带来的那一页", 1, body.countWith("axis?.startIndex"))
        // 定义一枚 + 调用一枚：分开数才看得见"又多画了一枚页码"那种错。
        assertEquals("页码那颗只定义一次", 1, body.countWith("private fun GalleryViewerPageCounter("))
        assertEquals("页码仍复用现成那颗（不另画一枚）", 1, body.countWith("page = pagerState.currentPage"))
        assertEquals("信息面板取自交接那一颗（只读一次）", 1, body.countWith("val axisPanel = axis?.infoPanel"))
        assertEquals("面板那一版由交接方画，参数由本页现场给", 1, body.countWith("GalleryPageAxis.PanelParams("))
        assertEquals("booru 那一版仍在（页轴没把旧屏顶掉）", 1, body.countWith("GalleryInfoSheet("))
        // 逐张记 HD 与放大态：从前按 uid 记，四页会一起亮。
        assertEquals("HD 记忆要按页", 1, body.countWith("hdUids.contains(currentViewerKey)"))
        assertEquals("放大态要按页", 1, body.countWith("zoomedPages[currentViewerKey]"))
    }

    /** 双击换成 1:1 那一档；上限取铺满与 1:1 里大的那一档再乘头数。 */
    @Test
    fun `双击换成原图 1 比 1 那一档，上限跟着抬`() {
        val body = code(viewer)
        assertEquals("双击仍只有一处", 1, body.countWith("DoubleClickToZoomListener.cycle(nativeZoom)"))
        assertEquals("旧的「铺满那一档」整条撤走", 0, body.countWith("DoubleClickToZoomListener.cycle(coverToFill)"))
        assertEquals("1:1 由纯函数算，屏上不自己除一次", 1, body.countWith("val nativeZoom = galleryNativeZoomFactor("))
        assertEquals("那颗纯函数只定义一次", 1, body.countWith("internal fun galleryNativeZoomFactor("))
        assertEquals(
            "上限不抬到 1:1 之上，双击就会被上限截住（用户那句「限制在一个大小里放大」）",
            1,
            body.countWith("ZoomSpec(maxZoomFactor = maxOf(coverToFill, nativeZoom) * GALLERY_ZOOM_OVERCOVER_FACTOR)"),
        )
        // 这一屏的点击仍只归 chrome 显隐（放大态之外不多挂一层）。
        assertEquals("缩放层仍只有一颗", 1, body.countWith(".zoomable("))
    }

    /** 存档没落页数 ⇒ 收藏复原的那枚不许被补成 1P（第一条尺在 GalleryCardPagesBadgeTest，这里钉接线）。 */
    @Test
    fun `默认档的下载文件名带上页位，免得四页落成同名靠括号区分`() {
        // `<site>-<id>.<ext>` 那一档四页同串，落盘要靠 `uniqueName` 补 " (1)" ——
        // 屏上读起来像"存了四张一样的"。带页位之后第 3 页就是 `pixiv-150320368-p2.png`。
        val wall = PixivWorkPage.pageWallOf(detail(threePages))
        val naming = com.venera.compose.gallery.domain.GallerySaveNaming.SITE_ID
        assertEquals(
            "pixiv-150320368-p2.png",
            com.venera.compose.gallery.domain.GallerySettingsModel.fileName(wall[2], naming, null),
        )
        // 非页轴的那枚逐字不变（图库三站的命名不许跟着动）。
        assertEquals("pixiv-150320368.png", com.venera.compose.gallery.domain.GallerySettingsModel.fileName(head, naming, null))
    }

    /** 页轴不该多出第二颗画面件：两支共用同一颗渲染层（第一批那条"不许为横向另写一颗"的延续）。 */
    @Test
    fun `画面件仍只有一颗`() {
        assertEquals(1, code(viewer).countWith("private fun GalleryViewerMedia("))
    }
}
