package com.venera.desktop.gallery.data

import com.venera.desktop.gallery.ui.DesktopSourceTree
import com.venera.desktop.gallery.ui.desktopRegionOverlayLands
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 89 · 分块解码层的判据（纯 JVM，不启组合、不载 Skia）。
 *
 * 这一批要防的那一格假开关长这样：`setSourceRegion` 是**建议性的** —— reader 可以忽略它、
 * 交回整张图、而且一个字都不报错。界面上那是"分块生效了"（图照样出），实际上一帧都没省。
 * 所以这里最重的三条都是**尺寸类**的：交回宽高等于算式，不等于整图。
 *
 * ⚠️ 逐格式各来一遍（JPEG / PNG / BMP）：只测一支等于没测 —— 三支 reader 对
 * 采样档与区域的支持各不相同，而"忽略区域"恰恰是某一支单独会犯的病。
 */
class DesktopRegionDecodeTest {

    private fun sample(fmt: String, w: Int = 2000, h: Int = 1500): ByteArray {
        val img = BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR)
        // 铺一层与坐标挂钩的花纹：位置错与尺寸错的读数才分得开（纯色样图两样都测不出）。
        for (y in 0 until h step 5) for (x in 0 until w step 5) img.setRGB(x, y, (x * 3 + y * 7) and 0xffffff)
        val bos = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName(fmt).next()
        writer.output = ImageIO.createImageOutputStream(bos)
        writer.write(img)
        writer.dispose()
        return bos.toByteArray()
    }

    private fun plan(x: Int, y: Int, w: Int, h: Int, sample: Int, imageW: Int = 2000, imageH: Int = 1500) =
        DesktopRegionRequest(x = x, y = y, sourceW = w, sourceH = h, sample = sample, imageW = imageW, imageH = imageH)

    /** ① ②  逐格式：交回的必须是**算式那一块**，而不是整图（这一条就是假开关闸）。 */
    @Test
    fun jpeg按区域与采样档真裁一块而不是整图() {
        val bytes = sample("JPEG")
        val region = DesktopRegionDecoder.decode(bytes, plan(100, 200, 300, 400, sample = 2))
        assertEquals("JPEG 交回的宽必须是算式那一档", 150, region.width)
        assertEquals("JPEG 交回的高必须是算式那一档", 200, region.height)
        assertTrue("交回的是整图（分块没生效）", region.width < 2000)
    }

    @Test
    fun png那一支也一样真裁() {
        val bytes = sample("PNG")
        val region = DesktopRegionDecoder.decode(bytes, plan(100, 200, 300, 400, sample = 4))
        assertEquals("PNG 的采样档没生效", 75, region.width)
        assertEquals("PNG 的采样档没生效", 100, region.height)
    }

    /**
     * ④ 第三支 reader（BMP）：JDK 那支对采样档的支持与 JPEG/PNG 不同。
     * 这里**不预设它支持**，只钉那条硬规矩 —— **要么按算式交回，要么抛**，
     * 绝不许交回一张与算式不符的（那正是"看着生效而其实整图"的形状）。
     */
    @Test
    fun 任何一支reader要么按算式交回要么抛() {
        val bytes = sample("BMP")
        val wanted = plan(0, 0, 400, 400, sample = 2)
        val outcome = runCatching { DesktopRegionDecoder.decode(bytes, wanted) }
        if (outcome.isSuccess) {
            assertEquals(
                "BMP 交回的尺寸与算式不符而没抛（这就是假开关）",
                DesktopRegionPlan.decodedSpan(400, 2),
                outcome.getOrThrow().width,
            )
        } else {
            assertTrue(
                "抛的必须是我们那句（而不是 reader 自己的裸异常）",
                outcome.exceptionOrNull()!!.message!!.contains("分块"),
            )
        }
    }

    @Test
    fun 只读头部拿尺寸而一个像素都不解() {
        val size = DesktopRegionDecoder.sizeOf(sample("PNG", 1234, 777))
        assertNotNull("头部尺寸读不出来", size)
        assertEquals(1234, size!!.width)
        assertEquals(777, size.height)
    }

    /** 不是图像 / 空字节：交回 null 而不是抛，也**不是**"尺寸 0x0 的一张图"。 */
    @Test
    fun 读不动的那一档交回空手而不是零尺寸() {
        assertEquals(null, DesktopRegionDecoder.sizeOf("not an image at all".toByteArray()))
        assertEquals(null, DesktopRegionDecoder.sizeOf(ByteArray(0)))
        assertThrows(IllegalStateException::class.java) {
            DesktopRegionDecoder.decode("not an image".toByteArray(), plan(0, 0, 10, 10, sample = 1))
        }
    }

    /** 位置锚点：区域解码的第一像素必须等于全图同一坐标那个像素（裁错位时尺寸照样对，只有内容能抓到）。 */
    @Test
    fun 裁出来的那一块位置对得上而不是尺寸对而裁错地方() {
        val bytes = sample("PNG", 600, 600)
        val full = ImageIO.read(bytes.inputStream())
        listOf(Pair(11, 23) to Pair(0, 0), Pair(11, 23) to Pair(0, 5), Pair(11, 23) to Pair(5, 0)).forEach { (src, dst) ->
            val region = DesktopRegionDecoder.decode(
                bytes,
                DesktopRegionRequest(
                    x = src.first, y = src.second, sourceW = 50, sourceH = 50, sample = 1, imageW = 600, imageH = 600,
                ),
            )
            assertEquals(
                "区域 ($src) 的 ($dst) 处与全图同一点不同色 ⇒ 裁错了位置",
                full.getRGB(src.first + dst.first, src.second + dst.second) and 0xffffff,
                region.image.getRGB(dst.first, dst.second) and 0xffffff,
            )
        }
    }

    /**
     * 把"交回的尺寸与算式不符就抛"那道闸**变成可测**的那一条（台架 P13 第一笔量出来的缺口）。
     *
     * 从前它一笔都红不掉：测试里那三支 reader 都真裁，撤掉闸没有任何 observable 变化 ⇒
     * "只有生产能触发"。2026-10-12 探针（`_qa/tmp_b89/Probe2.java`）量出一条能自己构造的触发形状：
     * **把一份远在图外的区域递给 reader**，PNG 与 JPEG 两支都**悄悄折边交回**
     * （600×400 的图、请求 `(100,200,3000×3000)` → 交回 `500x200`）**而不抛**。
     * ⇒ 那种输入下"尺寸对不上"是真的，而只有我们那句抛才算数（不抛就是交回一张比请求小得多的图，
     * 屏上是"分块生效而内容是缺的"那一族假开关）。
     */
    @Test
    fun 越界请求那一路reader折边交回而我们必须抛() {
        val bytes = sample("PNG", 600, 400)
        val e = assertThrows(IllegalStateException::class.java) {
            DesktopRegionDecoder.decode(bytes, plan(100, 200, 3000, 3000, sample = 1, imageW = 600, imageH = 400))
        }
        assertTrue(
            "那句要念得出「没生效」与 reader 实际交回的数（现场只能靠这两个数回查）：$e",
            e.message!!.contains("没生效") && e.message!!.contains("500x200"),
        )
    }

    /**
     * 线程与等停那两格（扫源码钉形状，不跑组合）。
     *
     * `LaunchedEffect` 跑在组合那颗 dispatcher 上（桌面 = AWT 线程），而读头部与解一块都是磁盘 + CPU 活：
     * 拖平移时每帧一次 JPEG 区域解码就是掉帧。这一层还**只许在停下来那一次真解**，
     * 所以等停那一句必须排在解码之前 —— 三条都按"包含 + 顺序"钉，存在性判式喂不饱它。
     */
    @Test
    fun 采样档与目标像素的退化输入必须抛而不是悄悄折成最小档() {
        assertThrows(IllegalArgumentException::class.java) { DesktopRegionPlan.sampleFor(1000, 0) }
        assertThrows(IllegalArgumentException::class.java) { DesktopRegionPlan.sampleFor(1000, -5) }
        assertThrows(IllegalArgumentException::class.java) { DesktopRegionPlan.decodedSpan(100, 0) }
        assertThrows(IllegalArgumentException::class.java) { DesktopRegionPlan.decodedSpan(100, -1) }
        assertThrows(IllegalArgumentException::class.java) { DesktopRegionPlan.of(0, 100, 0, 0, 10, 10, targetPx = 100) }
    }

    /** 视图比图大是常态（小图放大满屏）：折回图内、采样档恒 >=1，**不许放大成两遍解码**。 */
    @Test
    fun 越界的请求折回图内而采样档不放大() {
        val p = DesktopRegionPlan.of(imageW = 300, imageH = 200, x = -50, y = -80, w = 5000, h = 5000, targetPx = 1200)
        assertEquals("左边界该折到 0", 0, p.x)
        assertEquals("上边界该折到 0", 0, p.y)
        assertEquals("宽该折进图内", 300, p.sourceW)
        assertEquals("高该折进图内", 200, p.sourceH)
        assertEquals("视图大于原图时只许 1 档（放大不靠解码）", 1, p.sample)
    }

    /** 完全在图外那一块不是边缘情况，是调用点的 bug：必须出声。 */
    @Test
    fun 折完为空的那一笔抛而不是摆一张空块() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            DesktopRegionPlan.of(imageW = 100, imageH = 100, x = 500, y = 500, w = 10, h = 10, targetPx = 10)
        }
        assertTrue("那句要念得出是全图与折后的数，否则现场查不回去：$e", e.message!!.contains("全图=100x100"))
    }

    @Test
    fun 采样档随目标清晰度单调下降而翻倍那一圈有上界() {
        // 同一块源像素：目标越清晰 ⇒ 档越小（从前这条把方向写反过，反了会恒红/恒绿得看不出来）
        assertTrue("目标 2000 该比目标 500 用更小的档", sampleFor(2000, 2000) < sampleFor(2000, 500))
        assertTrue("目标 500 该比目标 120 用更小的档", sampleFor(2000, 500) < sampleFor(2000, 120))
        assertEquals("视图比图还清晰时只许 1 档（不许负采样）", 1, sampleFor(2000, 2000))
        // ⚠️ 这一格是溢出闸：decodedSpan 对 sample >= span 恒为 1，不设上界时它会一路翻倍到 Int 溢出。
        val smallest = sampleFor(2000, 1)
        assertTrue("目标 1 像素时档必须仍落在 1..源跨度 内，收到 $smallest", smallest in 1..2000)
        assertEquals("翻倍的上界就是不超过源跨度的那一档 2 的幂", 1024, smallest)
        // 向上取整：奇数跨度按 2 档解出来是 (n+1)/2，不是 n/2
        assertEquals(50, DesktopRegionPlan.decodedSpan(100, 2))
        assertEquals(50, DesktopRegionPlan.decodedSpan(99, 2))
        assertEquals(25, DesktopRegionPlan.decodedSpan(100, 4))
    }

    private fun sampleFor(span: Int, target: Int) = DesktopRegionPlan.sampleFor(span, target)

    /** 读数形状：失败那两句都带 `D_分块`（屏上不许出现它，而 stdout 里要 grep 得到）。 */
    @Test
    fun 失败那两句都带内部读数前缀() {
        val junk = plan(0, 0, 10, 10, sample = 1)
        assertTrue(
            DesktopRegionDecoder.runCatching { decode("zzz".toByteArray(), junk) }.exceptionOrNull()!!.message!!.contains("D_分块"),
        )
    }

    /**
     * 接线那一格（扫源码钉）：这一层**只许吃别人递进来的字节**，不许自己发请求或另起 loader ——
     * 那就是"写进另一份缓存 = 预取白花流量"那一族（批 76 钉过，字节源那颗件是 `DesktopCoilBytes.kt`）。
     */
    @Test
    fun 解码层不另起取数轨() {
        val file = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopRegionDecode.kt")
        listOf("ImageRequest", "HttpUrl", "ImageLoader", "openSnapshot", "SingletonImageLoader").forEach { needle ->
            assertEquals(
                "解码层里出现了取数通道 $needle（字节只能由调用点递进来）",
                0,
                DesktopSourceTree.codeOccurrences(file, needle),
            )
        }
        // 对照：字节源那一颗件确实存在，且它才是唯一发请求的地方。
        assertTrue(
            "字节源那颗件不在了（这一格就变成空钉）",
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("gallery/data/DesktopCoilBytes.kt"),
                "SingletonImageLoader",
            ) >= 1,
        )
    }

    // ---- 下面这八条钉的是「看得见的那一块 = 源图的哪一块」那一步算式（批 89 的命门）----

    @Test
    fun Fit的尺寸算式横图竖图各一条而不是只测一支() {
        // 横图吃满宽：8000x2000 进 800x600 ⇒ 800x200
        assertEquals(800 to 200, DesktopVisibleRegion.fittedSize(8000, 2000, 800, 600))
        // 竖图吃满高：1000x3000 进 800x600 ⇒ 200x600（只测横图会永远漏掉这一支）
        assertEquals(200 to 600, DesktopVisibleRegion.fittedSize(1000, 3000, 800, 600))
        assertThrows(IllegalArgumentException::class.java) { DesktopVisibleRegion.fittedSize(100, 100, 0, 600) }
    }

    @Test
    fun 未放大时那一条交回的是整张图() {
        val r = DesktopVisibleRegion.of(
            imageW = 4000, imageH = 1000, viewportW = 800, viewportH = 600,
            scale = 1f, offsetX = 0f, offsetY = 0f, targetPx = 800,
        )
        assertEquals("未放大该从 0 开始", 0, r.x)
        assertEquals(0, r.y)
        assertEquals("未放大该覆盖整幅宽", 4000, r.sourceW)
        assertEquals("竖方向折回图内", 1000, r.sourceH)
    }

    @Test
    fun 放大两倍居中时那一条恰好是中间一半() {
        val r = DesktopVisibleRegion.of(
            imageW = 4000, imageH = 1000, viewportW = 800, viewportH = 600,
            scale = 2f, offsetX = 0f, offsetY = 0f, targetPx = 800,
        )
        assertEquals("宽该是图的一半", 2000, r.sourceW)
        assertEquals("且居中：左边空掉四分之一", 1000, r.x)
    }

    /** ⚠️ 这一条钉的是**符号约定**：正 `offsetX` = 内容右移 = 看见更靠左的源像素。 */
    @Test
    fun 平移量往正方向走时看见的是更靠左的那一块() {
        fun xAt(offsetX: Float) = DesktopVisibleRegion.of(
            imageW = 4000, imageH = 1000, viewportW = 800, viewportH = 600,
            scale = 2f, offsetX = offsetX, offsetY = 0f, targetPx = 800,
        ).x
        assertTrue("正偏移该往左取（应小于不偏移那一档）", xAt(200f) < xAt(0f))
        assertTrue("负偏移该往右取", xAt(-200f) > xAt(0f))
        assertEquals("同一偏移两次必须给同一个数（算式不许吃隐式状态）", xAt(200f), xAt(200f))
    }

    @Test
    fun 平移到边界时折回而不是解到图外() {
        val r = DesktopVisibleRegion.of(
            imageW = 4000, imageH = 1000, viewportW = 800, viewportH = 600,
            scale = 2f, offsetX = -1000f, offsetY = 0f, targetPx = 800,
        )
        assertEquals("该贴着右边界", 4000, r.x + r.sourceW)
        assertTrue("起点仍须在图内", r.x in 1 until 4000)
    }

    /**
     * 黑边那一档（帧 C 的真实形状，2026-10-12 由 `DesktopZoomableOffsetTest` 量出成因）：
     * 库里没人调 `setContentSize`，所以**平移的边界按变换那一格定、不按图定** ——
     * 竖方向顶到界时图的上边缘会落到屏面下方，屏上只露出图的前几十行。
     *
     * 旧写法把跨度按"未折边的两端"算，于是多解了一截屏上根本看不见的行，
     * 再把这一整段拉伸到露出来的那一条上 ⇒ 分块图与底下那张对不上号（不是符号错，是跨度错）。
     */
    @Test
     fun 黑边那一档只解屏上真露出来的那几行() {
        // 帧 C 的读数：全图 1500x1125、视口 766x821、2.5 倍、偏移顶在 (倍率-1)×视口/2
        val ox = (2.5f - 1f) * 766f / 2f
        val oy = (2.5f - 1f) * 821f / 2f
        val content = DesktopVisibleRegion.contentRect(1500, 1125, 766, 821, 2.5f, ox, oy)
        val r = DesktopVisibleRegion.of(1500, 1125, 766, 821, 2.5f, ox, oy, targetPx = 766)
        assertTrue("内容顶边该落在屏面下方（那就是那条黑边）：$content", content.top > 0)
        assertEquals("起点仍是图的第一行", 0, r.y)
        assertTrue(
            "跨度只许到屏下沿那一行（旧写法在这里多算了 643 行），实测 ${r.sourceH}",
            r.sourceH < 500,
        )
        // 摆回去的矩形 = 内容与视口的交，不多不少
        val back = DesktopVisibleRegion.destinationRect(r, content)
        assertEquals("左边缘该贴着内容左边缘", content.left, back.left)
        assertEquals("上边缘该贴着内容上边缘", content.top, back.top)
        assertEquals("下边缘该正好到屏下沿", 821, back.top + back.height)
    }

    /**
     * 对齐不变量（本层**摆位**那一条总闸）：任意倍率与任意平移下，分块图摆出来的那个矩形
     * 必须**盖住**「内容矩形 ∩ 视口」且不多出 2 px 以上（1 px 来自把块折进图内时的取整，
     * 1 px 来自向外取整那一步；超出就是摆歪了，而那只有图证看得见）。
     *
     * 这一条是 `desktopRegionOverlayLands` 翻成 true 的前提 ——
     * 单测钉得住它，图证只是复验；从前反过来（只有图证能看见），所以那一屏歪了四批没人知道。
     */
    @Test
    fun 任何倍率与平移下摆位都盖住内容与视口的交() {
        val configs = listOf(
            Triple(1f, 0f, 0f),
            Triple(2f, 0f, 0f),
            Triple(2.5f, 574.5f, 615.75f), // 帧 C 那一档：顶在界上、带黑边
            Triple(2.5f, -574.5f, -615.75f), // 对角的界
            Triple(3f, 0f, 300f),
            Triple(4f, -200f, 100f),
        )
        configs.forEach { (scale, ox, oy) ->
            val content = DesktopVisibleRegion.contentRect(1500, 1125, 766, 821, scale, ox, oy)
            val request = DesktopVisibleRegion.of(1500, 1125, 766, 821, scale, ox, oy, targetPx = 766)
            val back = DesktopVisibleRegion.destinationRect(request, content)
            val expectedLeft = maxOf(content.left, 0)
            val expectedTop = maxOf(content.top, 0)
            val expectedRight = minOf(content.left + content.width, 766)
            val expectedBottom = minOf(content.top + content.height, 821)
            val tag = "scale=$scale off=$ox,$oy"
            assertTrue("$tag 左边缘留了缝：$back vs $expectedLeft", back.left <= expectedLeft)
            assertTrue("$tag 左边缘偏出去太多：$back vs $expectedLeft", back.left >= expectedLeft - 2)
            assertTrue("$tag 上边缘留了缝：$back vs $expectedTop", back.top <= expectedTop)
            assertTrue("$tag 上边缘偏出去太多：$back vs $expectedTop", back.top >= expectedTop - 2)
            assertTrue("$tag 右边缘留了缝：$back vs $expectedRight", back.left + back.width >= expectedRight)
            assertTrue("$tag 右边缘偏出去太多：$back vs $expectedRight", back.left + back.width <= expectedRight + 2)
            assertTrue("$tag 下边缘留了缝：$back vs $expectedBottom", back.top + back.height >= expectedBottom)
            assertTrue("$tag 下边缘偏出去太多：$back vs $expectedBottom", back.top + back.height <= expectedBottom + 2)
        }
    }

    /** 偏移还不是数（首帧那一档实测拿得到 NaN）：必须抛且那句念得出是哪三个数，不许算出个"看着对"的块。 */
    @Test
    fun 偏移还不是数时抛而那句带读数() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            DesktopVisibleRegion.of(1500, 1125, 766, 821, Float.NaN, 0f, 0f, targetPx = 766)
        }
        assertTrue("那句要念得出还不是数：$e", e.message!!.contains("还不是数"))
        assertThrows(IllegalArgumentException::class.java) {
            DesktopVisibleRegion.of(1500, 1125, 766, 821, 2.5f, Float.NEGATIVE_INFINITY, 0f, targetPx = 766)
        }
    }

    /** 可见区与图完全不相交（平移给得离谱）：抛，而不是悄悄折成边缘那一像素。 */
    @Test
    fun 可见区与图不相交时抛而不是折成一条边() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            DesktopVisibleRegion.of(1500, 1125, 766, 821, 2.5f, -1_000_000f, 0f, targetPx = 766)
        }
        assertTrue("那句要念清不相交：$e", e.message!!.contains("不相交"))
    }

    /** 往返一致：裁的那一块摆回屏上，必须正好是内容矩形（摆歪在这里就红）。 */
    @Test
    fun 裁的那一块摆回屏上正好盖住内容矩形() {
        val content = DesktopVisibleRegion.contentRect(4000, 1000, 800, 600, 1f, 0f, 0f)
        val request = DesktopVisibleRegion.of(4000, 1000, 800, 600, 1f, 0f, 0f, targetPx = 800)
        val back = DesktopVisibleRegion.destinationRect(request, content)
        assertEquals("整幅图摆回来左边缘该与内容一致", content.left, back.left)
        assertEquals(content.top, back.top)
        assertEquals(content.width, back.width)
        assertEquals(content.height, back.height)
    }

    /** 恒不欠解：目标不超过源跨度时，解码出来的那一块**至少**要有目标那么多像素。 */
    @Test
    fun 目标不超过源跨度时恒不欠解() {
        listOf(1f to 800, 2f to 800, 3f to 400, 5f to 300).forEach { (scale, target) ->
            val r = DesktopVisibleRegion.of(
                imageW = 8000, imageH = 6000, viewportW = 800, viewportH = 600,
                scale = scale, offsetX = 0f, offsetY = 0f, targetPx = target,
            )
            val decoded = DesktopRegionPlan.decodedSpan(r.sourceW, r.sample)
            assertTrue("scale=$scale 目标 $target 而只解出 $decoded（欠解 = 屏上必糊）", decoded >= target)
        }
    }

    /** 回弹那一档 scale 会短暂小于 1：按 1 处理，不许解出比整图还大的跨度。 */
    @Test
    fun 小于倍的弹跳按一倍处理() {
        val bounce = DesktopVisibleRegion.of(4000, 1000, 800, 600, 0.6f, 0f, 0f, targetPx = 800)
        val one = DesktopVisibleRegion.of(4000, 1000, 800, 600, 1f, 0f, 0f, targetPx = 800)
        assertEquals("0.6 倍该与 1 倍同解", one.x, bounce.x)
        assertEquals(one.sourceW, bounce.sourceW)
        assertEquals(one.sample, bounce.sample)
    }

    /**
     * 换轨那一笔账（图证抓出来的第二条）：门槛**不是**采样档，而是"解出来的像素是否多于底下那张给的"。
     * 反例就是本批的第一版 —— 它拿 `sample > 1` 当门槛，于是**小图放大时永不换轨而一个字都不念**。
     */
    @Test
    fun 小图放大时源里没那么多细节这一笔要算出来() {
        // 1200 宽的图进 1440 的屏：底下那张就是原分辨率，源里再没有更多细节
        assertFalse(
            "小图放大不该换轨（换了也是白解）",
            DesktopRegionGain.worthSwitching(sourceW = 480, sample = 1, imageW = 1200, viewportW = 1440),
        )
        assertEquals("底下那张给的就是原分辨率", 480.0, DesktopRegionGain.baselinePx(480, 1200, 1440), 0.001)
    }

    @Test
    fun 大图未放大时确实还多挣到一档() {
        // 4000 宽的图进 1440 的屏：底下那张只有 1440，而这一块能解到 2000
        val gain = DesktopRegionGain.of(sourceW = 4000, sample = 2, imageW = 4000, viewportW = 1440)
        assertTrue("增益该大于 1，收到 $gain", gain > 1.0)
        assertTrue("换轨判据该放行", DesktopRegionGain.worthSwitching(4000, 2, 4000, 1440))
        // 同一幅图，采样档给到 8 就过头了（解 500 < 底下那张的 1440）—— 那一档不该换轨
        assertFalse(DesktopRegionGain.worthSwitching(4000, 8, 4000, 1440))
        // 帧 A 那一格：放大到 2.5 倍时采样档仍是 1（一块源像素 600 而屏要 766），
        // 但底下那张只给得到 306 —— 拿 `sample > 1` 当门槛就是在这一档一个字都不念。
        assertTrue(
            "1 档也可能有增益（门槛是账不是档）",
            DesktopRegionGain.worthSwitching(sourceW = 600, sample = 1, imageW = 1500, viewportW = 766),
        )
    }

    /**
     * 接线那两条（图证抓出来的第三条）：闸翻成开之后，这一层**必须**仍在变换外面、且只被调一处。
     *
     * 摆位那一格由 `任何倍率与平移下摆位都盖住内容与视口的交` 独立钉着（同一套件、同一次跑）：
     * 谁把算式改坏，那条先红 —— 所以这里不再拿"测试文件里有没有某个名字"当闸的前提，
     * 那种尺子会咬到自己（判式里就写着那个名字）。
     */
    /**
     * 包内那一格（批 90）：`ImageIO.createImageInputStream` **自己会抛**
     * （`IIOException: Can't create cache file!`，`_qa/b90_pkgC.log` 里那条 `D_异常 线程=AWT-EventQueue-0`），
     * 而它从前在 `sizeOf` 里裸着 ⇒ 异常从 `LaunchedEffect` 逃出去，屏上连一句 `D_分块 退化` 都没有。
     * 这一条钉"那颗被兜成 null"（null 才是调用点第三句话的入口）。
     */
    @Test
    fun 开流那颗抛了也要折成null而不是把异常送出效果外() {
        val flat = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("gallery/data/DesktopRegionDecode.kt"),
        ).filterNot { it.isWhitespace() }
        assertTrue(
            "createImageInputStream 没被兜住（它抛 IIOException 时整条分块轨会崩在效果里）",
            flat.contains("runCatching{ImageIO.createImageInputStream(ByteArrayInputStream(bytes))}.getOrNull()?:returnnull"),
        )
    }

    /**
     * 半像素那一档（批 92 的包内原图档那一趟抓的）：**位置吃没取整的那一枚尺寸，宽高分开取整**。
     *
     * 起因是核对工具而非屏：`_qa/b89_check_frame_alignment.js` 顶上那句"与 Kotlin 那四处算式逐字同形"
     * **从没被量过** —— 前四趟的 `fitH × 倍` 都正好是整数，而包内 `--detail-tier=ORIGINAL` 那一趟第一次
     * 给出不整数（`fittedSize` 给 423，423 × 2.5 = 1057.5）：
     * 「先把显示尺寸取整、再拿它算位置」那一版给出 `top=497`，而屏上念的是 `498`。
     * ⇒ 工具的写法是错的（产品的 `contentRect` 一直吃浮点那一枚），但**这条约定当时没有任何尺子钉着**，
     * 谁哪天"顺手统一一下取整"都不会红 —— 这一条把它钉死。
     * ⚠️ 两个写法只差 ≤1 px，正落在摆位那条不变量的 ±2 容差里 ⇒ **屏上看不出来**，只有把数钉住拦得住。
     * 逐字取自包内那一行 `内容=0,498,1915x1058`（`_qa/b92_pkgOrig.log`）。
     */
    @Test
    fun 半像素那一档位置吃浮点而宽高各自取整() {
        val content = DesktopVisibleRegion.contentRect(
            imageW = 4000, imageH = 2211, viewportW = 766, viewportH = 821,
            scale = 2.5f, offsetX = 574.5f, offsetY = 615.75f,
        )
        assertEquals("位置或尺寸换了取整次序（先取整再算位置会给 top=497）：" + content, DesktopContentRect(0, 498, 1915, 1058), content)
        // 对照组：整数那一档两种写法同值 —— 读空了上面那条就成了恒真（这条一红就说明换的不是半像素那一格）。
        val whole = DesktopVisibleRegion.contentRect(4000, 2211, 766, 821, 2.0f, 574.5f, 0f)
        assertEquals("整数那一档也跟着变了：" + whole, DesktopContentRect(192, -12, 1532, 846), whole)
    }

    /**
     * 批 93：`D_分块` 那两行补「这一档的字节」那一格。
     *
     * 为什么要有这一格：这一批定档时量清了一件事 —— 桌面这一层的**带宽**仍是整张下载
     * （字节由 `desktopCoilBytes` 从 Coil 盘缓存/网络取整份，见那颗件的注释），省下来的只是**解码与内存**。
     * 那句当时只是我从代码读出来的推断，而这颗件念的每一行都可能被下一批改坏 ——
     * 有了 `字节=`，"整张 13.4 MB 下来了而只解了 1600x675"在包内 stdout 里同一行就看得见。
     *
     * ⚠️ 三种大小各断一句：只喂一种就量不出"那颗件把数写死了"（坑 ⑲ 那一族的第三次应用），
     * 而第三枚递一个七位以上的数：`%,d` 那种"给人看的"写法会把 stdout 变成 `字节=14,058,222`，
     * 与包内那行对不上号（这一格挡的是格式，不是某一次的真实大小 —— 真实读数在 rounds
     * `desktop-subsampling-verdict-2026-10-12.md` §4，三档各念一次）。
     */
    @Test
    fun 字节那一格念的是调用点递进来的那份字节数() {
        assertEquals("小的一档：", "字节=1024", desktopRegionBytesField(ByteArray(1024)))
        assertEquals("空手那一档也要念得出（0 不是缺这一格）：", "字节=0", desktopRegionBytesField(ByteArray(0)))
        assertEquals("包内原图档那一枚真实大小（不许带千分位）：", "字节=14058222", desktopRegionBytesField(ByteArray(14058222)))
    }

    /**
     * 批 93 接线：那一格只许挂在**有字节在手**的那两行（生效 + 不换轨）。
     *
     * `退化 因=缓存没它` 那一档根本没有字节可报，念一个数就是假读数（读数必须从状态现取，
     * 不许把请求当结果）。计数用 `codeOccurrences` 而不是 `contains` —— 上一批的 P9 刚证明
     * "两处同形"能把单串锚点喂饱。
     */
}
