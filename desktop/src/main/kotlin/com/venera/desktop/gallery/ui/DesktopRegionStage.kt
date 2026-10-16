package com.venera.desktop.gallery.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.venera.desktop.gallery.data.DesktopContentRect
import com.venera.desktop.gallery.data.DesktopRegionDecoder
import com.venera.desktop.gallery.data.DesktopRegionGain
import com.venera.desktop.gallery.data.DesktopVisibleRegion
import com.venera.desktop.gallery.data.desktopCoilBytes
import com.venera.desktop.gallery.data.desktopRegionBytesField
import com.venera.desktop.gallery.data.destinationRect
import net.engawapg.lib.zoomable.ZoomState
import kotlin.math.roundToInt

/**
 * 批 89 · 大图那一屏的**分块那一层**：放大到某一档以上时盖在 `.zoomable` 那层上面，
 * 摆的是「按当前看得见的那一块现解出来的那份源像素」。
 *
 * ## 为什么是"盖在上面"而不是"换掉底下那张"
 *
 * 底下那层（Coil 解好的、按屏宽降过样的那张）与 `.zoomable` 的变换是绑死的；而分块图**本身就已经是
 * 看得见的那一块**，再吃一遍同一个变换会位移与倍率都错两回。所以这一层自己按
 * [DesktopVisibleRegion.destinationRect] 摆位置，而那个矩形与 [DesktopVisibleRegion.of] 裁的块
 * **同源于 [DesktopVisibleRegion.contentRect]** —— 两处各算一遍就是"摆歪了而屏上看不出来"那一族。
 *
 * 底下那张不撤：分块图没到位之前（第一帧、换档、平移中）屏上仍是它，**永远不留白屏**。
 *
 * ## 三档退化各一句，且都出声
 *
 * 字节没进缓存 / 读不动尺寸 / 解这一档抛了 —— 三句不一样，因为修法不一样。每一档都念一行 `D_分块`：
 * 不念的话，"分块从没生效"与"这一屏本来就糊"在屏上是同一张脸。
 *
 * ⚠️ **`offsetX` 的符号约定仍未由图证定论**（判据只钉得住算式，钉不住"内容往右 = 看见更靠左"这一句）。
 * 那一帧拍到之前，这一层不许当成"已经生效"来写文档。
 */
@Composable
internal fun DesktopRegionOverlay(
    url: String,
    zoomState: ZoomState,
    tier: String,
    /** 退化那一句；分块图真摆上了会交回 null 把上一句清掉（留着它就是假读数）。 */
    onFallback: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var placed by remember(url) { mutableStateOf<DesktopPlacedRegion?>(null) }
    val scale = zoomState.scale
    val ox = zoomState.offsetX
    val oy = zoomState.offsetY

    LaunchedEffect(url, viewport, scale, ox, oy) {
        if (url.isBlank() || viewport.width < 1 || viewport.height < 1) return@LaunchedEffect
        // 起手一句**可取消**的等停：拖平移时每帧都在改 `offsetX/offsetY`，而这个效果一重启就取消上一笔 ——
        // 不解这一句的话每帧一次 JPEG 区域解码（`withContext` 里的活不可打断，会跑到完才丢结果），
        // 拖一下就是掉帧。底下那张始终在屏上（这一层只往上盖），所以等停那一段屏上不是空白。
        delay(SETTLE_MILLIS)
        val bytes = desktopCoilBytes(context, url)
        if (bytes == null) {
            placed = null
            onFallback("这一档的字节还没进缓存，屏上是降过样的那一版")
            println("D_分块 退化 档=$tier 因=缓存没它")
            return@LaunchedEffect
        }
        // 两跳都离开 UI 线程：效果跑在组合那颗 dispatcher（桌面 = AWT）上，
        // 而读头部与解一块都是 CPU/磁盘活（`desktopCoilBytes` 自己在 `Dispatchers.IO` 里，只有这两跳没有）。
        val size = withContext(Dispatchers.Default) { DesktopRegionDecoder.sizeOf(bytes) }
        if (size == null) {
            placed = null
            onFallback("这一档的字节读不动尺寸（不是图像，或这台机器没有对应的 reader）")
            println("D_分块 退化 档=$tier 因=读不动尺寸")
            return@LaunchedEffect
        }
        val content = DesktopVisibleRegion.contentRect(
            imageW = size.width,
            imageH = size.height,
            viewportW = viewport.width,
            viewportH = viewport.height,
            scale = scale,
            offsetX = ox,
            offsetY = oy,
        )
        val request = runCatching {
            DesktopVisibleRegion.of(
                imageW = size.width,
                imageH = size.height,
                viewportW = viewport.width,
                viewportH = viewport.height,
                scale = scale,
                offsetX = ox,
                offsetY = oy,
                // 目标就是**屏幕那一档**：屏装不下的像素解出来也看不见。
                targetPx = viewport.width.coerceAtLeast(1),
            )
        }.getOrElse { why ->
            // 偏移还不是数（首帧那一档）或可见区与图不相交：退回去渲染底下那张，且念清是哪一句。
            placed = null
            val whyText = why.message?.take(160) ?: why.javaClass.simpleName
            onFallback("这一块算不出来：$whyText")
            println("D_分块 退化 档=$tier 因=算不出这一块 $whyText")
            return@LaunchedEffect
        }
        // 换轨的门槛不是某个造出来的倍率，而是一条可算的账：**这一块解出来的像素数是否多于
        // 底下那张在同一区域能给的**（见 DesktopRegionGain）。不多就一句都不必说 —— 但**必须念一句**，
        // 否则"源里本来没细节"与"这一层坏了"在屏上是同一张脸（第一版就栽在这里：整趟一个字没念）。
        val gain = DesktopRegionGain.of(request.sourceW, request.sample, size.width, viewport.width)
        if (gain <= 1.0) {
            placed = null
            onFallback(null)
            println("D_分块 不换轨 档=$tier 增益=" + "%1.2f".format(gain) + " " + desktopRegionBytesField(bytes) + " " + request.describe())
            return@LaunchedEffect
        }
        // 解这一块那一跳也离开 UI 线程。**取消必须原样抛回去**：
        // `runCatching` 会把 `CancellationException` 一起吃掉，于是"用户已经翻页了"那一笔
        // 还会把上一位置的退化那句念出来并写进状态 —— 屏上就会出现"念的是上一张的错"。
        val region = try {
            withContext(Dispatchers.Default) { DesktopRegionDecoder.decode(bytes, request) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (why: Throwable) {
            placed = null
            val whyText = why.message?.take(160) ?: why.javaClass.simpleName
            onFallback("解这一档失败：$whyText")
            println("D_分块 退化 档=$tier 因=$whyText")
            return@LaunchedEffect
        }
        placed = DesktopPlacedRegion(
            bitmap = region.image.toComposeImageBitmap(),
            rect = DesktopVisibleRegion.destinationRect(request, content),
        )
        onFallback(null)
        println(
            "D_分块 生效 档=$tier 增益=" + "%1.2f".format(gain) +
                " " + desktopRegionBytesField(bytes) + " ${request.describe()} 视口=${viewport.width}x${viewport.height} 偏移=" + ox + "," + oy + " 倍=" + scale + " 内容=" + content.left + "," + content.top + "," + content.width + "x" + content.height,
        )
    }

    Box(modifier.fillMaxSize().clipToBounds().onSizeChanged { viewport = it }) {
        placed?.let { p ->
            Image(
                bitmap = p.bitmap,
                contentDescription = "分块解出的这一块",
                // FillBounds：这一块与那个矩形出自同一处算式，拉伸比恒为 1 —— 摆歪才会看出来。
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .offset { IntOffset(p.rect.left, p.rect.top) }
                    .pxSize(p.rect.width, p.rect.height),
            )
        }
    }
}

/**
 * 这一层**默认开**（2026-10-12 批 89 下半改判；前一版写着"未钉住摆位前只许关"，
 * 那条判据已由 `DesktopRegionDecodeTest.任何倍率与平移下摆位都盖住内容与视口的交` 钉住）。
 *
 * ## 前一版那句"符号未定论"是错的，这里把账记全
 *
 * 帧 C（`_qa/b89_frameC.log`，2.5 倍、无平移）念的是 `块=0,0,600x643 全图=1500x1125 视口=766x821`。
 * 当时判成"`offsetX` 的符号与本层假设不符"，因为按屏心该念 `块=450,241`。
 * 2026-10-12 用 `DesktopZoomableOffsetTest` 真挂库量出来三条，把那句推翻了：
 * - 偏移单位就是**屏幕像素**、枢轴在变换那一格的中心（库里 `graphicsLayer` 没设 pivot）；
 * - 焦点给左上角时偏移是**正**的，实测 `(600×800 视口, 2.5 倍)` → `450.0, 600.0`
 *   = `(倍率-1)×节点尺寸/2` —— 与本层假设同向，符号没错；
 * - **库里没有任何地方调用 `setContentSize`**（sources 里只有 `setLayoutSize`），
 *   所以平移的边界按**那一格**定而不是按图定 ⇒ 长图的黑边也算内容，顶到界时图上边缘会落到屏面下方。
 *
 * 于是真正的缺陷只有一处，且是跨度而不是符号：`DesktopVisibleRegion.of` 从前用**未折边的两端**相减，
 * 把"屏上根本看不见的那几十行"一起算进跨度，再拉伸到露出来的那一条上 ⇒ 两层对不上号。
 * 修法是先折边再相减，并把 `destinationRect` 改成向外取整（不留 1 px 缝）。
 *
 * ⚠️ 翻回 `false` 只有一种情形：那条对齐不变量的判据被改坏或被撤。两处必须同进同退。
 */
internal const val desktopRegionOverlayLands = true

/**
 * 起手那一句等停（毫秒）。取这一档的理由：拖一下平移时手势每帧都在改偏移量，
 * 而一次区域解码要几毫秒到几十毫秒 —— 90 毫秒比一帧长、比"用户觉得卡"短，
 * 且底下那张一直在屏上，等停那一段不是空白。
 */
private const val SETTLE_MILLIS = 90L

/**
 * 纯像素摆放。偏移那一枚本来就是 `IntOffset`（px），尺寸若改走 dp，
 * 在 Windows 的显示缩放档上两层单位不一致就会错位 —— 图证第一版抓到的正是这个。
 */
private fun Modifier.pxSize(w: Int, h: Int): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0)))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

/** 解出来的一块与它该落在屏上的哪个矩形 —— 成对递到渲染那一格，不许两处各算一遍。 */
internal class DesktopPlacedRegion(val bitmap: ImageBitmap, val rect: DesktopContentRect)
