package com.venera.desktop.gallery.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import com.venera.desktop.gallery.data.DesktopVisibleRegion
import net.engawapg.lib.zoomable.ZoomState
import net.engawapg.lib.zoomable.zoomable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 89 · 把 [ZoomState] 的 `offsetX/offsetY` **量清楚**的那一把尺子。
 *
 * ## 为什么这一颗必须真挂库、而不是拿假数据喂算式
 *
 * 分块那一层的对错全押在一条约定上：「偏移量的单位是什么、原点在哪、往哪边是正」。
 * 那条约定**不是本层定的**，是库定的 —— 所以判据不能拿"我以为的库"去自证，
 * 必须真挂一次 `Modifier.zoomable` 再读它自己算出来的那两个数。
 * 这正是手册里"新接带平台依赖的对象时图证不是可选项"那一格的**进程内版本**：
 * 不起窗、不动用户光标，也能把库的行为量出来（比截图可复算、可比对）。
 *
 * ## 三次读数各量哪一格（2026-10-12 实测，全部来自本文件）
 *
 * - **形状与视口同比例**：偏移 = `(倍率-1) × 视口 / 2` 且**符号为正**
 *   （焦点给左上角 ⇒ 内容被推向右下 ⇒ 看得见的是图的左上角）。实测 `450.0, 600.0`。
 * - **横向长图**与**竖向长图**：读数与等比例那一档**完全相同** ⇒ 边界由变换那一格定，不由图定
 *   （库没人调 `setContentSize`，黑边也算内容）。
 * - **首帧那一档**：`LaunchedEffect(Unit)` 直接跑 `changeScale` 时偏移实测是 **NaN**
 *   —— 因为 `fitContentSize` 要等第一次测量才被写进 `ZoomState`。
 *   所以本层两处都键在尺寸之后，且拿不到有限数时**出声退化**而不是算出一个"看着对"的块。
 *
 * 单位是**像素**（不是 dp）：UI 测试那一侧 density 实测不是 1（800dp → 768px），
 * 这一族夹具有 px 精确的 `pxBox` 与生产那层同一个理由。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopZoomableOffsetTest {

    /** 真挂一层 `.zoomable`，把 `changeScale` 走完，交回「视口 px + 库算出来的偏移」。 */
    private class Probe(
        val viewport: IntSize,
        val offsetX: Float,
        val offsetY: Float,
    )

    private fun measureZoomOffset(imageW: Int, imageH: Int, scale: Float): Probe {
        lateinit var state: ZoomState
        var viewport = IntSize.Zero
        var settled = false
        runComposeUiTest {
            setContent {
                state = remember { ZoomState() }
                // 尺寸要进**快照状态**：写成普通 var 的话 `LaunchedEffect(尺寸)` 永远只看得到首帧那个零，
                // 效果不重跑 ⇒ 读数停在"没跑完"（本批第一版就栽在这一格，与生产无关，是量具自己的坑）。
                var sizePx by remember { mutableStateOf(IntSize.Zero) }
                Box(
                    modifier = Modifier
                        // px 精确：UI 测试那一侧的 density 不是 1（实测 800dp → 768px），
                        // 而这条量具要核的是"库给的偏移等于哪个 px 数"，单位一混就量不出真数。
                        .pxBox(600, 800)
                        .onSizeChanged {
                            viewport = it
                            sizePx = it
                        }
                        .zoomable(state),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = ImageBitmap(imageW, imageH),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // 键在"量到尺寸之后"：库里 `calculateNewOffset` 要除 `fitContentSize`，
                // 而它由 `Modifier.zoomable` 在**第一次测量**时才写进 ZoomState ——
                // 首帧之前那两个偏移实测是 **NaN**（本批第一版就把它当成了"库的行为"）。
                LaunchedEffect(sizePx) {
                    if (sizePx.width < 1 || sizePx.height < 1) return@LaunchedEffect
                    // 与生产取证种子同一条路：公开的 changeScale + tween(0)（不要初值参数）。
                    state.changeScale(scale, Offset.Zero, tween(0))
                    settled = true
                }
            }
            waitForIdle()
            assertTrue("LaunchedEffect 没跑完，读数不作数", settled)
            waitForIdle()
        }
        return Probe(viewport, state.offsetX, state.offsetY)
    }

    private fun Modifier.pxBox(w: Int, h: Int): Modifier = layout { measurable, _ ->
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }

    /**
     * 焦点给左上角时，偏移必须是**正**的且正好顶在 `(s-1)×视口/2` 那一档。
     * 符号反了，分块就会解成左右上下对称错位的那一块 —— 屏上看着"变清楚了"，清楚的不是那一处。
     */
    @Test
    fun 焦点在左上角时偏移是正的且顶在倍率差那一档() {
        val p = measureZoomOffset(imageW = 600, imageH = 800, scale = 2.5f)
        println("D_摆位量具 等比例 视口=" + p.viewport.width + "x" + p.viewport.height + " 偏移=" + p.offsetX + "," + p.offsetY)
        assertEquals("视口没量到", 600, p.viewport.width)
        assertEquals(800, p.viewport.height)
        val boundX = (2.5f - 1f) * p.viewport.width / 2f
        val boundY = (2.5f - 1f) * p.viewport.height / 2f
        assertTrue("偏移 X 该为正（正=内容往右=看见更靠左的源像素），实测 ${p.offsetX}", p.offsetX > 0f)
        assertEquals("边界该是 (倍率-1)×视口宽/2 = $boundX", boundX, p.offsetX, 1.5f)
        assertEquals("边界该是 (倍率-1)×视口高/2 = $boundY", boundY, p.offsetY, 1.5f)
    }

    /**
     * 有黑边时那两档**读数与等比例那一档相同**：库的边界按变换那一格定，不按图定。
     *
     * 这条是本批量出来的最要紧的一条 —— 库里从头到尾**没有人调用 `setContentSize`**
     * （sources 里只有 `setLayoutSize`），所以长图的黑边被当成内容一起参与平移边界：
     * 顶到界时图的上边缘会落到屏面下方，屏上留一条黑边。
     * 后果由分块那一层承担：可见跨度必须"先折边再相减"，见
     * `DesktopRegionDecodeTest.黑边那一档只解屏上真露出来的那几行`。
     */
    @Test
    fun 长图带黑边时边界仍按那一格定而不是按图定() {
        val wide = measureZoomOffset(imageW = 1200, imageH = 400, scale = 2.5f)
        val tall = measureZoomOffset(imageW = 400, imageH = 1200, scale = 2.5f)
        println("D_摆位量具 横向长图 偏移=" + wide.offsetX + "," + wide.offsetY)
        println("D_摆位量具 竖向长图 偏移=" + tall.offsetX + "," + tall.offsetY)
        val boundX = (2.5f - 1f) * wide.viewport.width / 2f
        val boundY = (2.5f - 1f) * wide.viewport.height / 2f
        assertEquals("横图的黑边不该改变边界", boundX, wide.offsetX, 1.5f)
        assertEquals(boundY, wide.offsetY, 1.5f)
        assertEquals("竖图同理", boundX, tall.offsetX, 1.5f)
        assertEquals(boundY, tall.offsetY, 1.5f)
    }

    /**
     * 拿**真读数**喂一遍本层的算式：顶在左上角那一档时，"看得见的那一块"必须是图的**左上角**。
     *
     * 这条是符号约定的落点：量具那一格只证库自己给正数，这一格证"正数进了我们的算式之后
     * 解的确实是左上角"。两处合起来才是一句可引用的定论。
     */
    @Test
    fun 顶在左上角那一档时算式解的是图的左上角() {
        val p = measureZoomOffset(imageW = 600, imageH = 800, scale = 2.5f)
        val request = DesktopVisibleRegion.of(
            imageW = 1500,
            imageH = 2000,
            viewportW = p.viewport.width,
            viewportH = p.viewport.height,
            scale = 2.5f,
            offsetX = p.offsetX,
            offsetY = p.offsetY,
            targetPx = 100,
        )
        println("D_摆位量具 左上角那一块 " + request.describe())
        assertEquals("偏移为正时该解 x=0 那一侧，实测 ${request.describe()}", 0, request.x)
        assertEquals(0, request.y)
    }

    /** 未放大时不该有任何平移：那一档整图都在屏上，块必须是整图。 */
    @Test
    fun 未放大时算式解的是整图() {
        val request = DesktopVisibleRegion.of(
            imageW = 1500,
            imageH = 2000,
            viewportW = 600,
            viewportH = 800,
            scale = 1f,
            offsetX = 0f,
            offsetY = 0f,
            targetPx = 100,
        )
        assertEquals("1 倍时该从图角起解", 0, request.x)
        assertEquals(0, request.y)
        assertEquals("1 倍时该把整张图都算进可见区", 1500, request.sourceW)
        assertEquals(2000, request.sourceH)
    }
}
