package com.venera.compose.gallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 一张瓦片**散开**的那套画法（2026-10-15，历史墙多选「删除」用）。
 *
 * ## 出处与改动的地方
 *
 * 思路照 `tbahlai/animations` 里 `gallery_disintegration/DisintegrationEffect.kt`（本地留了一份在
 * `_qa/jm-ref/`，SHA 见手册那一行）：把画面拍进一张位图 → 按竖条切开、每条带自己的延时（于是
 * 消散是从左往右**扫**过去的，不是整块一起淡掉）→ 再从位图上**按像素采样**出一批带原色的小圆，
 * 让它们各自斜着飘走。颜色是从那张图自己取的，所以散掉的永远是"这一张图"，不是通用的灰粒子。
 *
 * 抄过来改了**四处**，每处都是本仓的约束逼出来的，不是随手简化：
 *
 * 1. **粒子数按面积自适应**（参考件写死 `step = 10`）。这里给每格定一个颗数预算再反推步长 ——
 *    因为本仓这一屏是**多选批量删**，一次可能同时有 6~8 格在散，而参考件那一屏永远只有一张在散。
 *    算术：一格约 540×900 像素（3x 屏、竖图），`step = 10` 会采样出 ~4,800 颗；八格同散就是
 *    每帧 ~38,000 发 `drawCircle`。那一档帧率这台机器上的画廊墙从来没被这样压过。
 *    ⚠️ **这条只是算术，不是读数**：真机帧率没量过，上机第一件事就是看这一格（见手册"真机待验"）。
 * 2. **时长不吃参考件那个 2500**。演示用的慢放是为了单张好看；删除连做几十格时 2.5 秒读起来
 *    像应用卡住。这里吃 `VeneraTokens.motion.disintegrationMs`（那一档的出处写在 token 自己的注释里）。
 * 3. **位图先 copy 成 ARGB_8888 再逐点读**：`toImageBitmap()` 拿回来的可能是硬件位图，
 *    对它 `getPixel` 直接抛（参考件也是这么绕的，留注释是因为这条不像显而易见的那一步）。
 * 4. **拍画面之前先等一帧**：参考件由按钮触发，那一刻内容早就画过千百帧了；本仓这一格是
 *    「出现在屏上就散」，按下 `disintegrate()` 那一帧 `drawWithContent` 还没跑过，
 *    直接拍拿到的是空位图 ⇒ 条与粒子各 0 颗，那一格会**透明着悬满整个时长**
 *    （2026-10-15 真机就是这么读的：「删除一次动画都没有」—— 漏的其实不止等一帧，
 *    屏上那支根本没按 `disintegrate()`，两处一起才成）。
 */
@Stable
class GalleryDisintegrationState internal constructor(internal val durationMs: Int) {

    /** 已经按下去、正在散的那一格的读数。`false` = 正常画内容。 */
    var triggered by mutableStateOf(false)
        internal set

    internal var progress by mutableFloatStateOf(0f)
    internal var effect by mutableStateOf<DisintegrationEffect?>(null)
    internal var onDone: (() -> Unit)? = null

    /**
     * 让这一格散开。[onComplete] 在动画跑完之后回调，**默认空**：本仓这一处不用它落库 ——
     * 删除的数据在动画起手之前就已写盘（判式「删除先落库消散只是观感」），
     * 挂在这颗回调上就变成「动画没跑完等于没删」。
     */
    fun disintegrate(onComplete: () -> Unit = {}) {
        if (triggered) return
        onDone = onComplete
        triggered = true
    }

    internal fun finish() {
        val done = onDone
        triggered = false
        progress = 0f
        effect = null
        onDone = null
        done?.invoke()
    }
}

@Composable
fun rememberGalleryDisintegrationState(durationMs: Int): GalleryDisintegrationState =
    remember(durationMs) { GalleryDisintegrationState(durationMs) }

/**
 * 把 [content] 包起来，`state.disintegrate()` 之后画的是散开的粒子与竖条，不再是那张图。
 *
 * ⚠️ 这一颗**总是**把内容录进一层 graphicsLayer（与参考件同形）。代价是这一格多一层离屏缓冲；
 * 收益是不触发时画出来的东西与包之前逐像素相同。多选态里没被选中的格子不包它 —— 别为了少写一个
 * 分支把整面墙都套上离屏层。
 */
@Composable
fun GalleryDisintegration(
    state: GalleryDisintegrationState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()

    LaunchedEffect(state.triggered) {
        if (!state.triggered) return@LaunchedEffect
        /*
         * 先等一帧再拍这一张。这一格是「出现在屏上就散」那种用法（与参考件的「点按钮才散」不同）：
         * 按下那一刻 `drawWithContent` 还一次都没跑过，直接 `toImageBitmap()` 拿回来的是**空位图**，
         * 条与粒子都是 0 颗 —— 那一格会变成「透明着悬满 900ms」，比不演还怪。
         * 等的这一帧里 `effect` 仍是 null，所以画的是原样内容，`record` 正好有了货。
         */
        withFrameMillis { }
        val source = runCatching { layer.toImageBitmap() }.getOrNull()
        if (source == null) {
            // 拍不到就别硬演"已经删掉了"：直接走完回调，让调用方照常落库。
            // 屏上那一格会**整块消失**而不是散开 —— 那是退路，不是假动作（数据真的删了）。
            state.finish()
            return@LaunchedEffect
        }
        val built = withContext(Dispatchers.Default) { buildDisintegration(source) }
        if (built.strips.isEmpty()) {
            // 拍到了却切不出条（0×0、层被回收）与拍不到是同一件事：走整块消失那条退路，
            // 不许悬着一格什么都不画 —— 那正是「按了删除但什么都没发生」的观感。
            state.finish()
            return@LaunchedEffect
        }
        state.effect = built
        val startedAt = withFrameMillis { it }
        while (true) {
            val elapsed = withFrameMillis { it } - startedAt
            val p = (elapsed.toFloat() / state.durationMs).coerceAtMost(1f)
            state.progress = p
            if (p >= 1f) break
        }
        state.finish()
    }

    Modifier
        .then(modifier)
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            val effect = state.effect
            if (!state.triggered || effect == null) {
                drawLayer(layer)
            } else {
                drawStrips(effect.strips, state.progress)
                drawParticles(effect.particles, state.progress)
            }
        }
        .let { hostModifier ->
            // 宿主自己是一层 Box：内容画进这一层，`record` 才拍得到它。
            Box(modifier = hostModifier) { content() }
        }
}

internal class StripBitmap(
    val bitmap: ImageBitmap,
    val x: Int,
    val width: Int,
    val height: Int,
    val delay: Float,
)

internal class DustParticle(
    val x: Float,
    val y: Float,
    val color: Color,
    val radius: Float,
    val delay: Float,
    val cosAngle: Float,
    val sinAngle: Float,
    val speed: Float,
    val wobble: Float,
)

internal class DisintegrationEffect(
    val strips: List<StripBitmap>,
    val particles: List<DustParticle>,
)

private fun DrawScope.drawStrips(strips: List<StripBitmap>, p: Float) {
    val paint = Paint()
    drawIntoCanvas { canvas ->
        for (strip in strips) {
            val localP = ((p - strip.delay) / FADE_SPAN).coerceIn(0f, 1f)
            val alpha = 1f - localP
            if (alpha <= 0f) continue
            paint.alpha = alpha
            canvas.drawImageRect(
                image = strip.bitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(strip.bitmap.width, strip.bitmap.height),
                dstOffset = IntOffset(strip.x, 0),
                dstSize = IntSize(strip.width, strip.height),
                paint = paint,
            )
        }
    }
}

private fun DrawScope.drawParticles(particles: List<DustParticle>, p: Float) {
    for (i in particles.indices) {
        val pt = particles[i]
        if (p <= pt.delay) continue
        val localP = ((p - pt.delay) / (1f - pt.delay)).coerceAtMost(1f)
        val drift = pt.speed * localP
        val wx = sin(localP * WOBBLE_FREQ + pt.wobble) * WOBBLE_AMPLITUDE * localP
        val alpha = 1f - localP * localP
        if (alpha <= 0f) continue
        drawCircle(
            color = pt.color,
            radius = pt.radius,
            center = Offset(pt.x + wx + pt.cosAngle * drift, pt.y + pt.sinAngle * drift),
            alpha = alpha,
        )
    }
}

/** 从左往右扫的那道延时上限：条与粒都用它，两套动作才是同一次扫。 */
private const val SWEEP_SPAN = 0.6f

/** 每一条自己的淡出跨度（0.3 之内走完，与参考件同形）。 */
private const val FADE_SPAN = 0.3f

private const val STRIP_COUNT = 20

/** 一颗粒子的漂法：角度夹在 -0.8~-0.3 弧度 = 往**右上**飘，与参考件同一档。 */
private const val ANGLE_FROM = -0.8f
private const val ANGLE_SPAN = 0.5f
private const val SPEED_FROM = 120f
private const val SPEED_SPAN = 200f
private const val WOBBLE_FREQ = 12f
private const val WOBBLE_AMPLITUDE = 20f

/** 每格的粒子预算（颗）。见文件头注那条算术：这是"八格同散"还能画得动的那一档。 */
private const val PARTICLE_BUDGET = 450

/** 太暗的像素不出粒子：alpha 低于这一档多半是透明边角，画出来是一圈脏边。 */
private const val MIN_PIXEL_ALPHA = 50

private fun buildDisintegration(source: ImageBitmap): DisintegrationEffect {
    val hardware = source.asAndroidBitmap()
    val bmp = hardware.copy(Bitmap.Config.ARGB_8888, false)
    val w = bmp.width
    val h = bmp.height
    if (w <= 0 || h <= 0) return DisintegrationEffect(emptyList(), emptyList())

    val stripWidth = (w / STRIP_COUNT).coerceAtLeast(1)
    val strips = buildList {
        var i = 0
        while (i * stripWidth < w) {
            val x = i * stripWidth
            // 最后一条吃剩下的宽度：整除余数不留就会在右缘缺一道。
            val sw = if (x + stripWidth >= w) w - x else stripWidth
            if (sw > 0) {
                add(
                    StripBitmap(
                        bitmap = Bitmap.createBitmap(bmp, x, 0, sw, h).asImageBitmap(),
                        x = x,
                        width = sw,
                        height = h,
                        delay = x.toFloat() / w * SWEEP_SPAN,
                    ),
                )
            }
            i++
        }
    }

    // 步长按面积反推到预算：step = sqrt(面积 / 预算)，最少 1。
    val step = kotlin.math.sqrt(w.toFloat() * h / PARTICLE_BUDGET).toInt().coerceAtLeast(1)
    val particles = buildList {
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val pixel = bmp.getPixel(x, y)
                if (android.graphics.Color.alpha(pixel) < MIN_PIXEL_ALPHA) continue
                val angle = ANGLE_FROM + Random.nextFloat() * ANGLE_SPAN
                add(
                    DustParticle(
                        x = x.toFloat(),
                        y = y.toFloat(),
                        color = Color(pixel),
                        radius = step / 2f + Random.nextFloat() * 2f,
                        delay = x.toFloat() / w * SWEEP_SPAN + Random.nextFloat() * 0.1f,
                        cosAngle = cos(angle),
                        sinAngle = sin(angle),
                        speed = SPEED_FROM + Random.nextFloat() * SPEED_SPAN,
                        wobble = Random.nextFloat() * 6.28f,
                    ),
                )
            }
        }
    }
    return DisintegrationEffect(strips, particles)
}
