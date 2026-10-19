package com.venera.compose.gallery

import androidx.compose.ui.unit.dp
import com.venera.compose.gallery.ui.galleryCoverZoomFactor
import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 大图页放大这一轮的尺子（2026-10-15）。
 *
 * 题面是用户的真机读数：「现在图片缩放都是在固定大小上缩放的，应该缩放的时候可以任意大小缩放到全屏」。
 * 两根因叠在一起，而且**都在库里**：
 * ① `Modifier.zoomable` 默认 `clipToBounds = true` —— 它裁的就是自己那一层，
 *    而这一层当时挂在"图的那一框"（定框算式的产物）上，于是放大后被剪回原框；
 * ② `rememberZoomableState()` 的默认上限是 **2×**（`ZoomSpec(maxZoomFactor = 2f)`），
 *    宽幅图的框本来只有屏高的一小条，2× 也够不着整屏。
 * 所以这一颗要钉的不是"能不能编过"，是**接线挂在哪一层、上限由谁算**——
 * 这两条改回旧写法时代码照样编译、屏上照样动，只有眼睛与尺子能逮到。
 */
class GalleryViewerZoomTest {

    private fun codeLines(dir: String, name: String): List<IndexedValue<String>> {
        val file = RepoSources.kotlinFiles(dir).firstOrNull { it.name == name }
            ?: error("找不到 $name（尝试过的根：${RepoSources.root.absolutePath}）")
        return file.readLines().withIndex().filter { (_, line) ->
            val t = line.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }
    }

    private fun postScreen() = codeLines("com/venera/compose/gallery/ui", "GalleryPostScreen.kt")

    // ── 纯函数：`covered` 那一档到底是多少倍 ──

    /** 竖图：高的那一轴留白，按高算（1080×2160 的框铺进 1080×2400 的视口）。 */
    @Test
    fun `竖图按留白那一轴算`() {
        assertEquals(
            2400f / 2160f,
            galleryCoverZoomFactor(1080.dp, 2160.dp, 1080.dp, 2400.dp),
            0.0001f,
        )
    }

    /** 宽幅：这才是要 ×10 那档口径兜住的那一类（1080×270 要 8.9 倍才铺得满）。 */
    @Test
    fun `宽幅图按高度那一轴放大`() {
        assertEquals(
            2400f / 270f,
            galleryCoverZoomFactor(1080.dp, 270.dp, 1080.dp, 2400.dp),
            0.0001f,
        )
    }

    /** 框本来就铺满视口：报 1，不许报小于 1 的数（那会把上限压到默认档以下）。 */
    @Test
    fun `已铺满时就是 1 倍`() {
        assertEquals(1f, galleryCoverZoomFactor(1080.dp, 2400.dp, 1080.dp, 2400.dp), 0f)
        assertEquals(1f, galleryCoverZoomFactor(2000.dp, 3000.dp, 1080.dp, 2400.dp), 0f)
    }

    /** 退化输入不许漏出 Inf/NaN/负数：上限一旦是 NaN，捏合直接失灵（静默失效那一族）。 */
    @Test
    fun `零与负的框退回 1 倍而不是除零`() {
        listOf(0.dp, (-10).dp).forEach { bad ->
            listOf(
                galleryCoverZoomFactor(bad, 2160.dp, 1080.dp, 2400.dp),
                galleryCoverZoomFactor(1080.dp, bad, 1080.dp, 2400.dp),
                galleryCoverZoomFactor(1080.dp, 2160.dp, bad, 2400.dp),
                galleryCoverZoomFactor(1080.dp, 2160.dp, 1080.dp, bad),
            ).forEach { got ->
                assertTrue("退化输入报出了 $got", got.isFinite() && got >= 1f)
            }
        }
    }

    // ── 接线尺子 ──

    /** 缩放层吃整屏，图那一框是它的孩子（挂在框上 = 放大被框剪回原样）。 */
    @Test
    fun `缩放层挂在整屏那一层而不是图那一框`() {
        val lines = postScreen()
        val zoomables = lines.filter { it.value.contains(".zoomable(") }
        assertEquals("缩放只该有一处（大图页的画面层）", 1, zoomables.size)
        val z = zoomables.single().index
        val up = lines.filter { it.index in (z - 8)..z }
        // 自证窗口真的框住了那条 modifier 链的起点：`Box(` 与 `Modifier` 都要看得见。
        assertTrue("窗口没框住宿主那一层（锚点选岔了）", up.any { it.value.trim() == "Modifier" })
        assertTrue(
            "缩放层又不吃整屏了 —— 放大出来的画面会被 telephoto 自己的 clipToBounds 剪回那一框",
            up.any { it.value.contains(".fillMaxSize()") },
        )
        assertTrue(
            "缩放层的链上又出现定框尺寸（那就是回到旧写法）",
            up.none { it.value.contains(".size(boxWidth") },
        )
        val down = lines.filter { it.index in z..(z + 20) }
        assertTrue(
            "图那一框不在缩放层里面了（变换不再带着图走）",
            down.any { it.value.contains(".size(boxWidth, boxHeight)") },
        )
    }

    /** 上限由几何算，不吃 telephoto 默认那 2×；且创建点住在几何旁边（一处真相）。 */
    @Test
    fun `放大上限吃几何算出的那档而不是默认两倍`() {
        val lines = postScreen()
        assertEquals(
            "裸的 `rememberZoomableState()` = 默认 2×，正是「缩到一半就捏不动」那一档",
            0,
            lines.count { it.value.contains("rememberZoomableState()") },
        )
        val creates = lines.filter { it.value.contains("rememberZoomableState(") }
        assertEquals(1, creates.size)
        assertEquals(
            "上限没有吃算出来的那一档（写死一个数就是第二处真相）",
            1,
            lines.count {
                it.value.contains("ZoomSpec(maxZoomFactor = maxOf(coverToFill, nativeZoom) * GALLERY_ZOOM_OVERCOVER_FACTOR)")
            },
        )
        val cover = lines.single { it.value.contains("val coverToFill = galleryCoverZoomFactor(") }.index
        assertTrue(
            "创建点排在几何之前（那时候还量不到框的尺寸）",
            cover < creates.single().index,
        )
        // 创建点必须在媒体那一颗里面：宿主那一层没有定框尺寸可算。
        val media = lines.first { it.value.contains("private fun GalleryViewerMedia(") }.index
        assertTrue("创建点被挪回了 GalleryViewerPage（它算不出那一框）", creates.single().index > media)
    }

    /**
     * 双击那一枚走**原图像素 1:1**，不再是"铺满整屏"那一档。
     *
     * 2026-10-18 改判（用户真机：「双击放大图片应该是放大整个图片而不是限制在一个大小里放大」，
     * 并在三枚候选档位里点了「循环：适应屏宽 ↔ 原图像素 1:1」）：
     * `coverToFill` 那一档是"把这一框裁到铺满视口"，宽幅图放大后左右两头都在屏外，
     * 而它比原图像素还大（1080 宽的屏把 2000 px 的图拉到 3200 px）⇒ 放大出来是糊的。
     * 从前那一版钉的是 `cycle(coverToFill)`，这条尺子就是它被换掉的现场。
     */
    @Test
    fun `双击走原图 1 比 1 那一档并且算不出就撤掉`() {
        val lines = postScreen()
        assertEquals(
            "双击仍只该有一处",
            1,
            lines.count { it.value.contains("DoubleClickToZoomListener.cycle(nativeZoom)") },
        )
        assertEquals(
            "「铺满那一档」这一版已换掉：它裁掉屏外两头，比 1:1 更差",
            0,
            lines.count { it.value.contains("DoubleClickToZoomListener.cycle(coverToFill)") },
        )
        assertTrue(
            "算不出 1:1（站方没给宽高）时还摆着双击 = 按下去没变化的假开关",
            lines.any { it.value.contains("if (nativeZoom > COVER_TOLERANCE)") },
        )
        assertEquals(
            "×10 那档口径要有一个出处，写死在屏上就成了本仓自己造的数",
            1,
            lines.count { it.value.contains("GALLERY_ZOOM_OVERCOVER_FACTOR = 10f") },
        )
    }

    /** 放大读数仍回报宿主：禁下滑与 chrome 让位都吃这一档，创建点挪了它也得跟着。 */
    @Test
    fun `放大读数仍回报给宿主`() {
        val lines = postScreen()
        val flow = lines.firstOrNull { it.value.contains("snapshotFlow { (zoomState.zoomFraction ?: 0f) > 0f }") }
        assertNotNull("放大读数不再外传：放大后下滑会跟 telephoto 抢手势", flow)
        val media = lines.first { it.value.contains("private fun GalleryViewerMedia(") }.index
        assertTrue("读数留在了宿主那边（缩放状态已经在这颗里建）", flow!!.index > media)
        assertEquals(1, lines.count { it.value.contains("collect { onZoomedChange(it) }") })
        assertEquals(
            "那颗形参还叫 zoomState（缩放状态的所有权没换干净）",
            0,
            lines.count { it.value.contains("zoomState: ZoomableState") },
        )
    }

    /** 垫帧与加载 chrome 不跟着放大：它们住在缩放层外面的另一框里。 */
    @Test
    fun `chrome 不跟着画面一起放大`() {
        val lines = postScreen()
        val z = lines.single { it.value.contains(".zoomable(") }.index
        // 缩放层的关闭在 `if (cardFrame` 之前，而垫帧之后还有一层同尺寸的框。
        val pad = lines.first { it.index > z && it.value.contains("if (cardFrame != null && !largeSettled)") }
        val overlay = lines.filter {
            it.index in z..pad.index && it.value.contains(".size(boxWidth, boxHeight)")
        }
        assertEquals(
            "垫帧又回到了缩放层里（放大时骨架与环会一起涨）：框层数=${overlay.size}",
            2,
            overlay.size,
        )
        assertTrue(
            "那一层没跟画面同心（打码提示与骨架会错位）",
            lines.any { it.value.contains(".align(Alignment.Center)") },
        )
    }
}
