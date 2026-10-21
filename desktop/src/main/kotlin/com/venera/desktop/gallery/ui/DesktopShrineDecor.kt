package com.venera.desktop.gallery.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.desktop.gallery.data.DESKTOP_DECO_DOT_RADIUS_PX
import com.venera.desktop.gallery.data.DesktopDecoTier
import com.venera.desktop.gallery.data.desktopDecoDotTileForArea
import com.venera.desktop.gallery.data.desktopDecoRailKanji
import io.github.composefluent.component.Text
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

fun desktopDecoShowsDotGrid(tier: DesktopDecoTier): Boolean = tier != DesktopDecoTier.PLAIN

/** 鸟居建筑剪影与 rail 底部那一组（稿 `:263`：`body:not([data-deco="shrine"]) …{display:none}`）。 */
fun desktopDecoShowsTorii(tier: DesktopDecoTier): Boolean = tier == DesktopDecoTier.SHRINE

/** rail 底部那组里的竖排汉字（同稿 `:263` 的第二枚选择器）。 */
fun desktopDecoShowsRailKanji(tier: DesktopDecoTier): Boolean = tier == DesktopDecoTier.SHRINE

/** 整窗那一层微粒（稿 `:270` `body[data-deco="shrine"] .frame::after`）。 */
fun desktopDecoShowsWindowGrain(tier: DesktopDecoTier): Boolean = tier == DesktopDecoTier.SHRINE

/** hero 高。稿 `:147` 基础 330、`:268` 神社档 360 —— 数值留在 Metrics，这里只决定用哪一枚。 */
fun desktopDecoHeroHeight(tier: DesktopDecoTier): Dp =
    if (tier == DesktopDecoTier.SHRINE) DesktopGalleryMetrics.heroHeightShrine else DesktopGalleryMetrics.heroHeight

/**
 * 平面纹样那一层（五瓣樱、注连绳那类 `.deco`）画不画。
 *
 * 稿 `:257` 打底的 `.deco{display:none}` 与 `:258` 给 pattern/shrine 放开的两行就是这一条的全部出处。
 * 三档是**累积**的（`:262`：素=零装饰／纹样=平面纹样／神社=再加建筑剪影、竖排与背景画），
 * 所以神社档也走这里，不是"神社档换成建筑、纹样那层就撤"。
 */
fun desktopDecoShowsPatternMotif(tier: DesktopDecoTier): Boolean = tier != DesktopDecoTier.PLAIN

/**
 * 当前筛选那一枚 chip 涂不涂博丽朱。稿 `:680` 把「和色强调（当前筛选 chip 用博丽朱）」列在
 * **纹样档的新增**里，而 `:679` 写「素 = 零装饰件」，所以素档退中性底。
 *
 * ⚠️ 这里是**跟差量表而不跟稿的 CSS**：`:139 .chip.beni` 与 `:254 .gyoku` 在稿上没有 `data-deco`
 * 门控，浏览器里三档都涂朱。那一处不相符记在 rounds §8.3，不改成本地"看着办"的第三种答案。
 */
fun desktopDecoChipBeni(tier: DesktopDecoTier, isFirst: Boolean): Boolean =
    tier != DesktopDecoTier.PLAIN && isFirst

/**
 * pane 选中态那一条底的两枚停靠点。稿 `:82` 基础是 `linear-gradient(90deg,#3A2A55,#2C2C2C 78%)`，
 * `:267` 在素档覆盖成 `background:var(--layer2)`。
 *
 * 素档换的是**颜色**而不是形状：两枚停靠点照旧给 0 与 0.78，只是那一枚色退成 #2C2C2C
 * （稿 `:10` 的 `--layer2`，与 `--card` 同值，桌面已有 [DesktopTheme.CardBackground]）。
 * 停靠点跟着档跳就会动到栅格观感，而稿 `:683` 禁的正是这个。
 */
fun desktopDecoNavSelectionStops(tier: DesktopDecoTier): List<Pair<Float, Color>> =
    if (tier == DesktopDecoTier.PLAIN) {
        listOf(0f to DesktopTheme.CardBackground, 0.78f to DesktopTheme.CardBackground)
    } else {
        listOf(0f to DesktopTheme.SelectionGradientStart, 0.78f to DesktopTheme.SelectionGradientEnd)
    }

/** 遮罩的一枚 stop：[offset] 是横向位置（0~1），[alpha] 是那一点压多狠。 */
data class DesktopDecoVeilStop(val offset: Float, val alpha: Float)

private val veilBase = listOf(DesktopDecoVeilStop(0f, .86f), DesktopDecoVeilStop(.46f, .42f), DesktopDecoVeilStop(1f, .10f))
private val veilShrine = listOf(DesktopDecoVeilStop(0f, .90f), DesktopDecoVeilStop(.48f, .46f), DesktopDecoVeilStop(1f, .12f))

/**
 * hero 那层遮罩的三枚 stop。稿 `:149` 是基础组、`:269` 是神社档覆盖组。
 *
 * 神社档压得更狠是**有原因的覆盖**而不是审美加码：那一档左下要摆标题、右边要摆鸟居剪影，
 * 而稿 `:687` 那条约束写着正文对比度要守 4.5:1 —— 剪影占掉的正是原本最透的那一段。
 */
fun desktopDecoVeilStops(tier: DesktopDecoTier): List<DesktopDecoVeilStop> =
    if (tier == DesktopDecoTier.SHRINE) veilShrine else veilBase

/**
 * 背景画那三段色（稿 `:148` 的 `linear-gradient(150deg,#2A1F47 0%,#6B4BC8 38%,var(--beni) 100%)`）。
 *
 * ⚠️ 这一格**不收档位形参**：稿上 `.hero .art` 在 DOM 里没有 `.deco` 类，三档都画。
 * 第三段引仓里那枚博丽朱 [DesktopTheme.AccentBeni] 而不是再写一遍 `#D6463C` ——
 * 同一枚红在仓里有两处出处，改主题时就会有一半跟着变。
 */
fun desktopDecoHeroArtColors(): List<Color> =
    listOf(DesktopTheme.DecoHeroArtDeep, DesktopTheme.DecoHeroArtFuji, DesktopTheme.AccentBeni)

/** 背景画三段的位置读数（稿 0% / 38% / 100%）。 */
fun desktopDecoHeroArtStopFractions(): List<Float> = listOf(0f, .38f, 1f)

/** 背景画角度。稿 `:148` 的 150deg。 */
const val desktopDecoHeroArtAngleDeg: Float = 150f

// ── 几何换算（CSS 与 SVG 的两笔账）──────────────────────────────────────

/**
 * CSS `linear-gradient(<angle>deg)` 的端点换算（Compose 的 `Brush.linearGradient` 只收两个点）。
 *
 * 按 CSS 规范：角度从"朝上"顺时针量 ⇒ 方向向量 d=(sinθ, −cosθ)（屏幕 y 轴朝下）；
 * 渐变线长 `L = |w·sinθ| + |h·cosθ|`；两端关于框的中心对称。
 * 这三条互成约束（0deg 必竖直、90deg 必水平、长度必等于规范式），抄错任一个符号都会红。
 */
fun desktopCssGradientEndpoints(width: Float, height: Float, angleDeg: Float): Pair<Offset, Offset> {
    val rad = Math.toRadians(angleDeg.toDouble())
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    val length = abs(width * dx) + abs(height * dy)
    val half = length / 2f
    val cx = width / 2f
    val cy = height / 2f
    return Offset(cx - dx * half, cy - dy * half) to Offset(cx + dx * half, cy + dy * half)
}

/** `#t-torii` 那颗 symbol 的 viewBox 边长（稿那颗 symbol 是 24×24 正方）。 */
const val DESKTOP_TORII_VIEWBOX: Float = 24f

/**
 * 鸟居在盒子里按 SVG 的 `preserveAspectRatio="xMidYMid meet"` 等比缩放该用多大的比例。
 *
 * viewBox 是正方而稿给的盒是 150×110 —— meet 语义取**短边**贴合（110），于是真画出来的是
 * 110×110、左右各空 20。按盒子非等比拉会把鸟居压成一块横板。
 */
fun desktopToriiFitScale(boxWidth: Float, boxHeight: Float): Float =
    min(boxWidth, boxHeight) / DESKTOP_TORII_VIEWBOX

/** 上面那枚缩放对应的左上角偏移（长边方向居中）。 */
fun desktopToriiOrigin(boxWidth: Float, boxHeight: Float): Offset {
    val drawn = DESKTOP_TORII_VIEWBOX * desktopToriiFitScale(boxWidth, boxHeight)
    return Offset((boxWidth - drawn) / 2f, (boxHeight - drawn) / 2f)
}

// ── 窄窗降档（稿 :714）──────────────────────────────────────────────────

private val designColumnCount: Int =
    desktopWallColumnCount(DesktopGalleryMetrics.designContentWidthDp, GalleryColumnMode.AUTO)

/**
 * 神社档还留得住的那一格**最小内容宽**。
 *
 * 不写死 1280、也不写死某个推导出来的整数：从 1440 档的内容宽往回走，走到列数刚少一格的前一格停下。
 * 于是这一枚阈值与列数算式**同一把尺子** —— 有人改了列预算（那一格 200），这一枚自己跟着动。
 */
val desktopDecoMinWidthForShrine: Float by lazy {
    var width = DesktopGalleryMetrics.designContentWidthDp
    while (width > 1f && desktopWallColumnCount(width - 1f, GalleryColumnMode.AUTO) >= designColumnCount) {
        width -= 1f
    }
    width
}

/**
 * 窄窗把神社档降成纹样档（稿 `:714`）。
 *
 * ⚠️ 只许**往下**走：素档不会因为窗变窄而"升"成纹样，纹样也不会升成神社 ——
 * 用户选了低浓度档而窗口一拉大就自己冒出鸟居，是本仓最忌的那种"屏上的东西自己改自己"。
 *
 * 为什么这一格值得降：神社档比纹样档多 30dp 的 hero 高（稿 `:268`）与一整层点阵，
 * 而窗窄到已经少一列的时候，那 30dp 是从**已经更少的那几列**头上再扣一行的可见区。
 */
fun desktopDecoTierForContentWidth(requested: DesktopDecoTier, contentWidthDp: Float): DesktopDecoTier {
    if (requested != DesktopDecoTier.SHRINE) return requested
    val columns = desktopWallColumnCount(contentWidthDp, GalleryColumnMode.AUTO)
    return if (columns >= designColumnCount) DesktopDecoTier.SHRINE else DesktopDecoTier.PATTERN
}

// ── 画法 ────────────────────────────────────────────────────────────────

/**
 * 稿 `#t-torii` 那颗 symbol 的几何（viewBox 0 0 24 24，共五笔）：
 * 笠木（两端上翘的顶梁，两条贝塞尔围出的一块）、贯（第二道横梁）、两根柱子、中间那一枚小柱。
 *
 * ⚠️ 坐标是**逐字从稿上抄的**（那颗 symbol 的 `d` 与四枚 `rect`），这里只做一次相似变换
 * （乘 scale 加平移），没有任何"看着差不多"的改动。稿上四枚 `rect` 的 `rx` 都是 `.6`，跟着同一个 scale 走。
 */
internal fun toriiVector(scale: Float, dx: Float, dy: Float): List<Path> {
    fun x(v: Float) = dx + v * scale
    fun y(v: Float) = dy + v * scale

    val lid = Path().apply {
        moveTo(x(2f), y(5.4f))
        cubicTo(x(6f), y(3.6f), x(18f), y(3.6f), x(22f), y(5.4f))
        lineTo(x(20.4f), y(7.6f))
        cubicTo(x(16.6f), y(6f), x(7.4f), y(6f), x(3.6f), y(7.6f))
        close()
    }
    val corner = CornerRadius(0.6f * scale, 0.6f * scale)
    fun bar(left: Float, top: Float, width: Float, height: Float) = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(Offset(x(left), y(top)), Size(width * scale, height * scale)),
                topLeft = corner,
                topRight = corner,
                bottomLeft = corner,
                bottomRight = corner,
            ),
        )
    }
    return listOf(
        lid,
        bar(4.4f, 9f, 15.2f, 1.9f),
        bar(5.6f, 6.6f, 2f, 14.4f),
        bar(16.4f, 6.6f, 2f, 14.4f),
        bar(11f, 5.2f, 2f, 3.8f),
    )
}

/**
 * 鸟居标记。稿上三处 `<use>` 同一颗 symbol、尺寸与色各异，所以这两样由调用点给，几何不在这里重复。
 *
 * 盒内按 [desktopToriiFitScale] / [desktopToriiOrigin] 的 meet 语义摆位。
 */
internal const val DESKTOP_DECO_MAX_DOTS_PER_LAYER = 12_000

/** 一层点阵（hero 网点与整窗微粒共用这一画法，只换步长与色）。 */
private fun DrawScope.drawDotGrid(tilePx: Float, color: Color) {
    val stride = desktopDecoDotTileForArea(tilePx, size.width, size.height, DESKTOP_DECO_MAX_DOTS_PER_LAYER)
    var y = 0f
    while (y <= size.height) {
        var x = 0f
        while (x <= size.width) {
            drawCircle(color, radius = DESKTOP_DECO_DOT_RADIUS_PX, center = Offset(x, y))
            x += stride
        }
        y += stride
    }
}

/** 网点那一层（稿 `:150-118`：9px 步长、`rgba(255,255,255,.20)`、整层 `opacity:.5`）。 */
private const val DESKTOP_DECO_DOT_LAYER_OPACITY = 0.5f

/** hero 的遮罩（稿 `:149` 基础组 / `:269` 神社组），90deg = 从左往右压。 */
internal const val DESKTOP_GLYPH_VIEWBOX = 16f

/** 花心那枚 `circle r=1.3`（稿 `:322` 末了那一笔）。 */
internal const val DESKTOP_SAKURA_HEART_RADIUS = 1.3f

/** 椭圆转四段贝塞尔的标准系数。 */
private const val ELLIPSE_KAPPA = 0.5522847498f

// 稿 `:322` 那枚 ellipse 的四参数，逐字抄：cx 8、cy 4.4、rx 2.5、ry 3.4。
private const val SAKURA_TEMPLATE_CX = 8f
private const val SAKURA_TEMPLATE_CY = 4.4f
private const val SAKURA_PETAL_RX = 2.5f
private const val SAKURA_PETAL_RY = 3.4f

/**
 * 一枚花瓣：`(cx, cy)` 是**转完之后**它在 viewBox 里的位置，`angleDeg` 是它自身绕花心转过的角度。
 *
 * 五枚共用同一支模板花瓣，只差 `i×72` 度 —— 稿上就是五枚同样的 `ellipse` 加 `rotate(72 8 8)`。
 */
data class DesktopSakuraPetal(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val angleDeg: Float)

/** 把 viewBox 里的一个点绕花心 (8,8) 转 [deg] 度，再按 scale/dx/dy 映射到像素。 */
private fun rotateAboutFlower(x: Float, y: Float, deg: Float, scale: Float, dx: Float, dy: Float): Offset {
    val rad = deg * PI / 180.0
    val ox = x - 8f
    val oy = y - 8f
    return Offset(
        dx + (8f + (ox * cos(rad) - oy * sin(rad))).toFloat() * scale,
        dy + (8f + (ox * sin(rad) + oy * cos(rad))).toFloat() * scale,
    )
}

fun desktopSakuraPetals(): List<DesktopSakuraPetal> = List(5) { i ->
    val deg = i * 72f
    val rad = deg * PI / 180.0
    val ox = SAKURA_TEMPLATE_CX - 8f
    val oy = SAKURA_TEMPLATE_CY - 8f
    DesktopSakuraPetal(
        cx = (8f + (ox * cos(rad) - oy * sin(rad))).toFloat(),
        cy = (8f + (ox * sin(rad) + oy * cos(rad))).toFloat(),
        rx = SAKURA_PETAL_RX,
        ry = SAKURA_PETAL_RY,
        angleDeg = deg,
    )
}

/**
 * 五瓣樱的画法：五枚花瓣（各自绕花心转 `i×72` 度）加一枚花心，共**六笔**，与稿 `:322` 的图元数同。
 *
 * 花瓣走四段三次贝塞尔逼近（系数 [ELLIPSE_KAPPA]）。之所以手写旋转而不用 `Matrix`／`DrawScope.rotate`：
 * 这一颗要在**离屏判据里被直接调用**（`desktopSakuraPetals()` 那五枚的方位是钉死的），
 * 而"把模板点集绕 (8,8) 转一遍"这条算式就是判据读的那一条，中间再多一层库的变换就成两份答案了。
 */
internal fun sakuraVector(scale: Float, dx: Float, dy: Float): List<Path> {
    val cx = SAKURA_TEMPLATE_CX
    val cy = SAKURA_TEMPLATE_CY
    val rx = SAKURA_PETAL_RX
    val ry = SAKURA_PETAL_RY
    val ax = rx * ELLIPSE_KAPPA
    val ay = ry * ELLIPSE_KAPPA
    // 模板花瓣按 (右→下→左→上) 四段；每段是 (起点, 两枚控制点, 终点)。
    val segs = listOf(
        arrayOf(Offset(cx + rx, cy), Offset(cx + rx, cy + ay), Offset(cx + ax, cy + ry), Offset(cx, cy + ry)),
        arrayOf(Offset(cx, cy + ry), Offset(cx - ax, cy + ry), Offset(cx - rx, cy + ay), Offset(cx - rx, cy)),
        arrayOf(Offset(cx - rx, cy), Offset(cx - rx, cy - ay), Offset(cx - ax, cy - ry), Offset(cx, cy - ry)),
        arrayOf(Offset(cx, cy - ry), Offset(cx + ax, cy - ry), Offset(cx + rx, cy - ay), Offset(cx + rx, cy)),
    )
    val petals = List(5) { i ->
        val deg = i * 72f
        Path().apply {
            val start = rotateAboutFlower(segs.first()[0].x, segs.first()[0].y, deg, scale, dx, dy)
            moveTo(start.x, start.y)
            segs.forEach { seg ->
                val c1 = rotateAboutFlower(seg[1].x, seg[1].y, deg, scale, dx, dy)
                val c2 = rotateAboutFlower(seg[2].x, seg[2].y, deg, scale, dx, dy)
                val end = rotateAboutFlower(seg[3].x, seg[3].y, deg, scale, dx, dy)
                cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
            }
            close()
        }
    }
    val hr = DESKTOP_SAKURA_HEART_RADIUS
    val heart = Path().apply {
        addOval(
            Rect(
                Offset(dx + (8f - hr) * scale, dy + (8f - hr) * scale),
                Size(2f * hr * scale, 2f * hr * scale),
            ),
        )
    }
    return petals + heart
}

/** viewBox 16 见方的 meet 语义：等比缩到盒内并居中（与鸟居那一颗同一条路）。 */
internal fun desktopGlyphScale(widthPx: Float, heightPx: Float): Float =
    min(widthPx, heightPx) / DESKTOP_GLYPH_VIEWBOX

internal fun desktopGlyphOrigin(widthPx: Float, heightPx: Float): Offset {
    val s = desktopGlyphScale(widthPx, heightPx)
    val drawn = DESKTOP_GLYPH_VIEWBOX * s
    return Offset((widthPx - drawn) / 2f, (heightPx - drawn) / 2f)
}

data class DesktopGyokuShape(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val arcCenterX: Float,
    val arcCenterY: Float,
    val startDeg: Float,
    val sweepDeg: Float,
    val ringStroke: Float,
)

fun desktopGyokuShape(): DesktopGyokuShape {
    val r = 6.4f
    val chordX = 8f
    val topY = 1.6f
    val bottomY = 12.8f
    val half = (bottomY - topY) / 2f
    val back = sqrt(r * r - half * half)
    val acx = chordX - back
    val acy = (topY + bottomY) / 2f
    val halfAngle = asin(half / r) * 180.0 / PI
    return DesktopGyokuShape(
        centerX = chordX, centerY = 8f, radius = r,
        arcCenterX = acx, arcCenterY = acy,
        startDeg = (-halfAngle).toFloat(), sweepDeg = (2 * halfAngle).toFloat(),
        ringStroke = 0.8f,
    )
}

internal fun gyokuVector(scale: Float, dx: Float, dy: Float): List<Path> {
    val 玉 = desktopGyokuShape()
    fun oval(cx: Float, cy: Float, r: Float) = Rect(
        Offset(dx + (cx - r) * scale, dy + (cy - r) * scale),
        Size(2f * r * scale, 2f * r * scale),
    )
    val body = Path().apply { addOval(oval(玉.centerX, 玉.centerY, 玉.radius)) }
    val cap = Path().apply {
        moveTo(dx + 玉.centerX * scale, dy + 1.6f * scale)
        arcTo(oval(玉.arcCenterX, 玉.arcCenterY, 玉.radius), 玉.startDeg, 玉.sweepDeg, forceMoveTo = false)
        close()
    }
    val ring = Path().apply { addOval(oval(玉.centerX, 玉.centerY, 玉.radius)) }
    return listOf(body, cap, ring)
}

/**
 * 阴阳玉标记：朱球 + 和纸白那片 + 一圈墨色描边。
 *
 * 三枚色各有出处（稿 `:321`：`fill="var(--beni)"`、`fill="var(--washi)"`、
 * `stroke="rgba(0,0,0,.35)" stroke-width=".8"`），所以这一颗**不接 color 形参** ——
 * 与 [ToriiGlyph]／[SakuraGlyph] 不同，它不是一枚单色 glyph。
 */
