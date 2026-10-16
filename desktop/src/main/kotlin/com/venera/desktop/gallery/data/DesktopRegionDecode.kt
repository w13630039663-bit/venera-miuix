package com.venera.desktop.gallery.data

import java.awt.Rectangle
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam
import javax.imageio.ImageReader
import javax.imageio.stream.ImageInputStream
import kotlin.math.roundToInt

/**
 * 批 89 · 大图那一屏的**分块解码层**（JVM 侧自研，零新依赖）。
 *
 * ## 为什么是"自研"而不是"接现成品"
 *
 * 排期那一行从前写着"Telephoto 那一颗"。2026-10-12 量完把它推翻了两回：
 * `me.saket.telephoto:sub-sampling-image-desktop` 确实存在（29,887 B / 13 颗 class），
 * 但它的 sources jar 里**只有 commonMain 那 5 份网格数学**，非 `internal` 的类 **0 颗** ——
 * 也就是**没有可调用面**；真解码那颗（`AndroidImageRegionDecoder`、`SubSamplingImage.kt`）
 * 全在 `-android` 那一颗里（sources 32,406 B 对桌面 7,455 B）。而 `zoomable-image-coil3` 更早就判过没有桌面变体。
 * ⇒ 桌面这一格只能自己写。好消息是 JVM 这条路是**现成的**：交付镜像里 `java.desktop` 在
 * （`jimage list` 现量 imageio 条目 449 条），所以既不用补 `modules(...)`，也不用引库。
 *
 * ## 这一层只回答两件事
 *
 * 1. **这张图多大**（[sizeOf]）：只读头部，一个像素都不解 —— 大图那一屏要先知道尺寸才排得下块。
 * 2. **把这一小块解出来**（[decode]）：区域 + 采样档一起给，交回的位图尺寸由 [DesktopRegionPlan] 算得，
 *    而**算不到就对不上号 ⇒ 立刻抛**（见下）。
 *
 * ## 那条"尺寸对不上就抛"是本层存在的理由
 *
 * `ImageReadParam.setSourceRegion` 是**建议性的**：reader 完全可以忽略它而交回整图，而且**不报错**。
 * 那种形状在界面上长得和"分块生效"一模一样（图照样出、只是内存没省）—— 正是本仓那一族**假开关**。
 * 探针实测（`_qa/tmp_b89/Probe.java`，2000×1500 的样图）：JPEG 与 PNG 两支 reader 在
 * `sample=1/2/4` 下都交回 `300×400 / 150×200 / 75×100`（真裁），但**这是实测出来的性质，不是 API 的承诺**，
 * 所以每次解码都把它当断言核一遍：换格式、换 JDK、reader 退化，第一趟就红而不是悄悄吞掉。
 *
 * ## 为什么这里不碰 Skia
 *
 * 交回 `BufferedImage` 而不是 `ImageBitmap`：这一层要能在**不开组合、不载原生库**的纯 JVM 判据里逐条量，
 * 而 `toComposeImageBitmap()` 那一步属于 UI 边缘（大图那一屏的接线做）。两件事分开，
 * 判据才不需要为"解码算式对不对"去付一次 Skia 的钱。
 */
internal object DesktopRegionDecoder {

    /**
     * 只读头部拿尺寸。不是图像（或这台 JDK 没有对应的 reader）交回 **null** ——
     * 那既不是"图坏了"也不是"图是空的"，是"这一档我读不动"，调用点要说第三句话。
     *
     * ⚠️ 不许改成"读不动就整张解一遍看看"：那正好把这一层要省的内存花在最坏的那张图上。
     */
    fun sizeOf(bytes: ByteArray): DesktopImageSize? {
        if (bytes.isEmpty()) return null
        return withReader(bytes) { reader, _ ->
            runCatching { DesktopImageSize(reader.getWidth(0), reader.getHeight(0)) }.getOrNull()
        }
    }

    /**
     * 解一块。参数坏、格式读不动、**或交回的尺寸与算式不符**都抛 —— 绝不原样交回整图。
     *
     * @param plan 由 [DesktopRegionPlan.of] 算出来的那一份（区域 + 采样档都在里面，已折过边界）。
     */
    fun decode(bytes: ByteArray, plan: DesktopRegionRequest): DecodedRegion {
        val expectedW = DesktopRegionPlan.decodedSpan(plan.sourceW, plan.sample)
        val expectedH = DesktopRegionPlan.decodedSpan(plan.sourceH, plan.sample)
        val image = withReader(bytes) { reader, stream ->
            val param = ImageReadParam()
            // 采样档在前、区域在后：两支都得给，只给区域会解全分辨率那一块，只给采样档会解整张缩略。
            param.setSourceSubsampling(plan.sample, plan.sample, 0, 0)
            param.setSourceRegion(Rectangle(plan.x, plan.y, plan.sourceW, plan.sourceH))
            reader.read(0, param)
        } ?: throw IllegalStateException("D_分块 解不出这一档 区域=${plan.describe()}")
        // 这一条是假开关闸：reader 忽略 setSourceRegion 时交回的是整图，尺寸必然对不上。
        if (image.width != expectedW || image.height != expectedH) {
            throw IllegalStateException(
                "D_分块 没生效：请求 ${plan.describe()} 期望 ${expectedW}x$expectedH，" +
                    "而 reader 交回 ${image.width}x${image.height}",
            )
        }
        return DecodedRegion(image = image, plan = plan)
    }

    /**
     * 开一颗能读这份字节的 reader，用完关流。找不到 reader 交回 null（不抛 —— 那是"读不动"那一档）。
     *
     * ⚠️ `createImageInputStream` **自己会抛**（`IIOException: Can't create cache file!`）：
     * 交付包那一趟实测就死在这里（`_qa/b90_pkgC.log` 的 `D_异常 线程=AWT-EventQueue-0`，
     * 而屏上连一句 `D_分块 退化` 都没有 —— 因为异常从 `sizeOf` 逃出了那个效果）。
     * 这一档语义上就是"读不动"，所以**折成 null** 交回给调用点那第三句话，而不是把异常送出去。
     */
    private fun <T> withReader(bytes: ByteArray, block: (ImageReader, ImageInputStream) -> T?): T? {
        val stream = runCatching { ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
        return try {
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            try {
                reader.setInput(stream, true, false)
                block(reader, stream)
            } finally {
                reader.dispose()
            }
        } finally {
            runCatching { stream.close() }
        }
    }
}

/** 只读头部拿到的尺寸。 */
internal data class DesktopImageSize(val width: Int, val height: Int)

/**
 * 一次分块请求：图内那一块 + 采样档。区域在这里就**已经折进图内**，
 * 所以解码层不必再判越界，判据也不必猜谁负责边界。
 */
internal data class DesktopRegionRequest(
    val x: Int,
    val y: Int,
    val sourceW: Int,
    val sourceH: Int,
    val sample: Int,
    /** 整张图的原尺寸，只用于念读数与自证（不参与解码算式）。 */
    val imageW: Int,
    val imageH: Int,
) {
    fun describe(): String = "块=$x,$y,${sourceW}x$sourceH 档=$sample 全图=${imageW}x$imageH"
}

/**
 * `D_分块` 那两行里的「这一档一共拿了多少字节」那一格。
 *
 * 为什么要这一格：这一层省下来的是**解码与内存**，带宽仍是整张 —— 字节由 `desktopCoilBytes`
 * 一次取整份（Coil 盘缓存的口径就是整份编码文件），而 `块=` 念的只是解出来的那一片。
 * 那句话原本只是从代码读出来的推断，念成读数才有数可核：同一行里 `字节=` 与 `块=` 并排，
 * "整张 13.4 MB 下来而只解了 1600x675" 一眼看得见，也不必再靠人去翻缓存目录。
 */
internal fun desktopRegionBytesField(bytes: ByteArray): String = "字节=${bytes.size}"

/**
 * 排块的算式（纯函数，不碰解码）。
 *
 * 采样档只取 **2 的幂**：JPEG 的 DCT 块本身就是 8×8，非 2 的幂的采样在两支 reader 上代价差很多，
 * 而"够屏幕用"这件事本来就不需要精确档 —— 取不超过需要的那一档，宁可略糊也不解双份。
 */
internal object DesktopRegionPlan {

    /** 一个采样档把 sourceSpan 折成多少像素（向上取整，与 reader 的语义同轨）。 */
    fun decodedSpan(sourceSpan: Int, sample: Int): Int {
        require(sample >= 1) { "采样档必须 >=1，收到 $sample" }
        require(sourceSpan >= 1) { "源跨度必须 >=1，收到 $sourceSpan" }
        return (sourceSpan + sample - 1) / sample
    }

    /**
     * 要在一块 [sourceSpan] 原像素上凑出至少 [targetPx] 个解码像素，该用哪一档采样。
     *
     * ⚠️ [targetPx] 必须 >=1：0 或负数不是"要最小的块"，是调用点算错了（比如视图还没量出高度）。
     * 那种输入**必须抛** —— 悄悄折成 1 会让这一屏每次都去解最大档而没人知道。
     *
     * ⚠️ 翻倍那一圈**必须被 sourceSpan 兜住**：`decodedSpan` 对任何 `sample >= sourceSpan` 都恒为 1，
     * 所以不设上界时 `targetPx = 1` 会让它一路翻倍到 Int 溢出（判据 ⑩ 专门量这一格）。
     */
    fun sampleFor(sourceSpan: Int, targetPx: Int): Int {
        require(targetPx >= 1) { "目标像素必须 >=1，收到 $targetPx" }
        require(sourceSpan >= 1) { "源跨度必须 >=1，收到 $sourceSpan" }
        var sample = 1
        while (sample * 2 <= sourceSpan && decodedSpan(sourceSpan, sample * 2) >= targetPx) sample *= 2
        return sample
    }

    /**
     * 把"想看的这一块"折进图内并配好采样档。
     *
     * 部分越界**折回**而不抛（视图比图大是常态：小图放大到满屏）；
     * 而**整块都在图外**是调用点的 bug，必须出声 —— 悄悄 `coerceIn` 会把它折成边缘那 1 像素，
     * 屏上看着"这一档解出来了"而内容是图角上的一条边，正是本仓那一族静默交错。
     */
    fun of(
        imageW: Int,
        imageH: Int,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        targetPx: Int,
    ): DesktopRegionRequest {
        require(imageW >= 1 && imageH >= 1) { "图尺寸必须 >=1，收到 ${imageW}x$imageH" }
        if (x >= imageW || y >= imageH || x.toLong() + w <= 0 || y.toLong() + h <= 0) {
            throw IllegalArgumentException("D_分块 请求这一块整个在图外 请求=$x,$y,${w}x$h 全图=${imageW}x$imageH")
        }
        val left = x.coerceIn(0, imageW - 1)
        val top = y.coerceIn(0, imageH - 1)
        val sourceW = w.coerceAtLeast(1).coerceAtMost(imageW - left)
        val sourceH = h.coerceAtLeast(1).coerceAtMost(imageH - top)
        return DesktopRegionRequest(
            x = left,
            y = top,
            sourceW = sourceW,
            sourceH = sourceH,
            sample = sampleFor(minOf(sourceW, sourceH), targetPx),
            imageW = imageW,
            imageH = imageH,
        )
    }
}

/** 解出来的一块：位图 + 它是按哪一份算式解的（UI 那一层要拿算式摆位置）。 */
internal class DecodedRegion(
    val image: java.awt.image.BufferedImage,
    val plan: DesktopRegionRequest,
) {
    val width: Int get() = image.width
    val height: Int get() = image.height
}

/**
 * 「屏上看得见的那一块」→「源图的哪一块」（纯函数，不碰解码、不碰组合）。
 *
 * ## 为什么这一颗要单独存在并单独钉
 *
 * 分块到底省不省内存、细节到不到得了，全押在这一步的算式上：拿不到平移量就只能整图降样
 * （每屏像素上的细节一个都不多，那是装饰）；而**符号弄反**会解出一块左右上下对称错位的地方 ——
 * 屏上看着"变清楚了"，只是清楚的不是你看的那一处。判据钉得住算式（含镜像用例），
 * **只有图证钉得住那枚符号** ⇒ 所以 [offsetX] 的约定在判据里写成断言而不是注释。
 */
internal object DesktopVisibleRegion {

    /** 图按 `ContentScale.Fit` 摆进视口后（未放大）占多大 —— 与 Compose 那一步同一条算式。 */
    fun fittedSize(imageW: Int, imageH: Int, viewportW: Int, viewportH: Int): Pair<Int, Int> {
        require(imageW >= 1 && imageH >= 1 && viewportW >= 1 && viewportH >= 1) {
            "图与视口的尺寸都必须 >=1，收到 图=${imageW}x$imageH 视口=${viewportW}x$viewportH"
        }
        // 用乘法比而不是除法比：小图（1xN）在除法里会先丢精度。
        return if (viewportW.toLong() * imageH <= viewportH.toLong() * imageW) {
            viewportW to (viewportW.toLong() * imageH / imageW).toInt().coerceAtLeast(1)
        } else {
            (viewportH.toLong() * imageW / imageH).toInt().coerceAtLeast(1) to viewportH
        }
    }

    /**
     * 约定：内容以视口中心放大 [scale] 倍，再平移 [offsetX]/[offsetY] 个**屏幕像素**；
     * [offsetX] 为正 = 内容往右走 = 看得见的是更靠左的源像素。
     *
     * ⚠️ [targetPx] 直接递下去，采样档由 [DesktopRegionPlan.of] 定 —— 这一颗只管"哪一块"，
     * 不管"解多细"，两件事分家才各自钉得住。
     */
    /** 内容（放大后的整张图）落在屏上的那个矩形，单位是**像素**。 */
    fun contentRect(
        imageW: Int,
        imageH: Int,
        viewportW: Int,
        viewportH: Int,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ): DesktopContentRect {
        val (fitW, fitH) = fittedSize(imageW, imageH, viewportW, viewportH)
        val s = scale.coerceAtLeast(1f)
        val displayedW = fitW * s
        val displayedH = fitH * s
        return DesktopContentRect(
            left = (viewportW / 2f - displayedW / 2f + offsetX).roundToInt(),
            top = (viewportH / 2f - displayedH / 2f + offsetY).roundToInt(),
            width = displayedW.roundToInt(),
            height = displayedH.roundToInt(),
        )
    }

    fun of(
        imageW: Int,
        imageH: Int,
        viewportW: Int,
        viewportH: Int,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        targetPx: Int,
    ): DesktopRegionRequest {
        require(scale.isFinite() && offsetX.isFinite() && offsetY.isFinite()) {
            // 首帧真拿得到 NaN：库里 `changeScale` 若在 `setLayoutSize` 之前跑，
            // `fitContentSize` 还是零，`calculateNewOffset` 就除了个零（本批那把量具量到的）。
            throw IllegalArgumentException(
                "D_分块 偏移还不是数 倍=$scale 偏移=$offsetX,$offsetY 视口=${viewportW}x$viewportH",
            )
        }
        val rect = contentRect(imageW, imageH, viewportW, viewportH, scale, offsetX, offsetY)
        // 源列 = (屏上那一列 - 内容左边缘) / 内容宽 * 源图宽；屏上 0 与 viewportW 是可见区的两条边。
        val x0 = (-rect.left.toFloat() / rect.width * imageW).roundToInt().coerceIn(0, imageW)
        val x1 = ((viewportW - rect.left) / rect.width.toFloat() * imageW).roundToInt().coerceIn(0, imageW)
        val y0 = (-rect.top.toFloat() / rect.height * imageH).roundToInt().coerceIn(0, imageH)
        val y1 = ((viewportH - rect.top) / rect.height.toFloat() * imageH).roundToInt().coerceIn(0, imageH)
        // **两条边各自折进图内，跨度再由折过的两端相减** —— 顺序反了就把"屏上根本看不见的那一段"
        // 一起算进跨度（批 89 帧 C 的真实形状：内容被顶到黑边那一档，屏上只露出图的前 401 行，
        // 而旧写法交回 643 行并把它们拉伸到同一段屏上 ⇒ 分块图与底下那张对不上）。
        if (x1 <= x0 || y1 <= y0) {
            throw IllegalArgumentException(
                "D_分块 可见区与图不相交 可见列=$x0..$x1 可见行=$y0..$y1 " +
                    "内容=$rect 视口=${viewportW}x$viewportH",
            )
        }
        return DesktopRegionPlan.of(
            imageW = imageW,
            imageH = imageH,
            x = x0,
            y = y0,
            w = x1 - x0,
            h = y1 - y0,
            targetPx = targetPx,
        )
    }
}

/** 内容矩形（像素）。overlay 那一层就按它摆，所以裁的块与摆的位置**出自同一处算式**。 */
internal data class DesktopContentRect(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * 换轨划不划算（纯函数）。**这一颗是本批第二值钱的判据**（第一值是"交回的是算式那一块"）。
 *
 * 底下那张（Coil 按视口宽解的）在整幅 `imageW` 上只有 `min(imageW, viewportW)` 个像素，
 * 所以它给「看得见的那 `sourceW` 列」提供的细节只有 `min(imageW,viewportW) * sourceW / imageW` 个像素；
 * 而分块那一轨给的是 `decodedSpan(sourceW, sample)` 个。**只有前者小于后者，换轨才是在挣细节**，
 * 否则换轨只是白解一遍（屏上一样糊，读数却念"生效" —— 那是假开关）。
 *
 * 两个反例各钉一条判据：
 * - 小图放大（`imageW < viewportW`）：源里本来就没那么多像素，任何倍率下都不该换轨；
 * - 大图未放大：源里确实还有细节，换轨能多给一档（`sample` 把它折到屏幕装得下的那一档）。
 */
internal object DesktopRegionGain {

    /** 底下那张在可见区里实际提供的细节像素数。 */
    fun baselinePx(sourceW: Int, imageW: Int, viewportW: Int): Double =
        minOf(imageW, viewportW).toDouble() / imageW * sourceW

    fun of(sourceW: Int, sample: Int, imageW: Int, viewportW: Int): Double {
        val base = baselinePx(sourceW, imageW, viewportW)
        return if (base <= 0.0) 0.0 else DesktopRegionPlan.decodedSpan(sourceW, sample) / base
    }

    /** 大于 1 才是"多挣到细节"；等于 1 就是原地换轨（白解一遍）。 */
    fun worthSwitching(sourceW: Int, sample: Int, imageW: Int, viewportW: Int): Boolean =
        of(sourceW, sample, imageW, viewportW) > 1.0
}

/**
 * 这一块解出来之后该摆在屏上的哪里 —— 与 [of] 互为正反两问，因此必须同源于 [contentRect]。
 *
 * 单独钉这一颗的理由：`of` 算错会"看错了地方"，而它算错会"摆歪了地方"。
 * 两者都糊在屏上，但**只有摆歪是单测永远抓不到而图证一眼看到的**那一类。
 */
internal fun DesktopVisibleRegion.destinationRect(
    request: DesktopRegionRequest,
    content: DesktopContentRect,
): DesktopContentRect {
    // **向外取整**：近边向下、远边向上。按最近取整会让矩形比"内容与视口的交"少掉 1 px，
    // 那一条缝在屏上是底下那张的高清图与分块图之间的一根 hairline —— 恰好是这一层要消灭的东西，
    // 不能由自己造出来。多覆盖那一像素无害（画的是同一份源像素）。
    fun screenX(sourceX: Int, roundUp: Boolean): Int {
        val raw = sourceX.toFloat() / request.imageW * content.width
        return content.left + if (roundUp) raw.ceilToInt() else raw.floorToInt()
    }

    fun screenY(sourceY: Int, roundUp: Boolean): Int {
        val raw = sourceY.toFloat() / request.imageH * content.height
        return content.top + if (roundUp) raw.ceilToInt() else raw.floorToInt()
    }

    val left = screenX(request.x, roundUp = false)
    val top = screenY(request.y, roundUp = false)
    val right = screenX(request.x + request.sourceW, roundUp = true)
    val bottom = screenY(request.y + request.sourceH, roundUp = true)
    return DesktopContentRect(
        left = left,
        top = top,
        width = (right - left).coerceAtLeast(1),
        height = (bottom - top).coerceAtLeast(1),
    )
}

private fun Float.floorToInt(): Int = kotlin.math.floor(this).toInt()

private fun Float.ceilToInt(): Int = kotlin.math.ceil(this).toInt()
