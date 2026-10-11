package com.venera.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.venera.desktop.gallery.ui.DesktopWallBlurDp
import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * S0-9 探针：**那一层糊每帧花多少钱**（第六十四批，纯探针，零产品行为改动）。
 *
 * ## 这一颗要回答的那一句话
 *
 * 批 62 量到应用内模糊在桌面上**画得出来**，批 63 把它接成了真行为：浮层开着时
 * `Modifier.blur(18.dp)` 挂在**底下那面墙那棵子树**上。两句之间少了一笔账 ——
 * 好看与贵是两件事：一层高斯挂在会出帧的墙上，代价按**面积**走而不是按瓦片数走，
 * 而"墙在浮层背后还在出帧"这件事本批不假设成立也不假设不成立，只量。
 *
 * ## 量的形状（以及它不量什么）
 *
 * **四相配对**在同一进程里跑：不糊 → 糊 → 不糊 → 糊，差值从**两组的均值**算。
 * 为什么不是一相不糊一相糊再拿第一相当基线：这台机器的**相 0 恒最慢**（起步那一段的布局与着色器预热
 * 落在它身上），单基线相减会把这笔序偏置算进代价里，而方向正好**偏袒糊** —— 批 64 就是这样把一笔
 * 噪声读成"重载下三跑同向 +1.4ms"，批 65 改成配对后两跑反号（见 `docs/rounds/desktop-blur-overlay-phase-2026-10-09.md`）。
 * 另念两行让这类事以后自己出声：两枚不糊相自己相比（= 这把尺子的序偏置）、两枚糊相自己相比（= 重复性）。
 * 每一相各跑一次「预热 → 回到第 0 项 → 取 `FRAMES_PER_PHASE` 帧」，帧时刻从**帧时钟**取
 * （`withFrameNanos`），不是拿墙钟前后夹：后者量的是这个循环转了多久，不是屏幕出了几帧。
 *
 * ⚠️ 墙上的瓦片是本机生成的 4×4 硬边棋盘，**不是**网络上真下来的图 ⇒ 这读数说的是
 * 「糊这一层的合成开销」，不是「真墙在真网络下的帧时间」。写结论时别把前者写成后者。
 * 半径吃的是产线那一枚 `DesktopWallBlurDp`（18dp）而不是探针自己造的数，
 * 否则"18dp 花多少"这个结论盖的是别的那个半径。
 *
 * 跑法：`sh gradlew :desktop:blurcost`（取完数自己走，读数全在 stdout）。
 * 一帧都不出时**不再静默挂着**：每相一道看门狗，超时念一行 `S09C_挂` 并以退出码 2 收尸（第六十九批，
 * 起因是批 68 那一跑锁屏下十分钟零输出而 15 条判据全绿）。
 */
private const val TILES = 900
private const val TILE_PX = 256
private const val FRAME_W = 1_160
private const val FRAME_H = 780
private const val FRAMES_PER_PHASE = 180
private const val WARMUP_FRAMES = 40
private const val SCROLL_STEP_PX = 40f
private const val DROP_MS = 16.7

/**
 * 一相最多允许"多少毫秒不出帧"（第六十九批）。超过就念 `S09C_挂` 收尸走人，不再往下跑。
 *
 * 为什么需要这一枚：批 68 那一跑 `-Pstep=400` 起了窗、念完 `S09C_相0起手` 之后**十分钟零输出** ——
 * 会话不可见（锁屏）时 `withFrameNanos` 根本不返回，帧时钟不走。那时进程活着、窗口开着、
 * 什么读数都不念，而判据全绿（它们钉的是"探针代码写没写对"，从不跑真探针）。
 * ⇒ 挂死与全绿能同时成立，所以"挂了"这件事必须由探针自己念出来。
 * 60 秒对稳态那一档（每相约 1.2 秒）宽了 50 倍，为的是**不误伤慢**：真在画而画得慢的那一跑，
 * 只要还在出帧就不该报警；报警只管"一帧都不出"。
 */
private const val FrameWaitMs = 60000L

/**
 * 不糊 → 糊 → 不糊 → 糊：**配对**着跑，而不是"一相不糊一相糊再一相不糊"。
 *
 * ⚠️ 为什么从三相改成四相（批 65 改判批 64 的算法）：这台机器的**第一相总是最慢**
 * （批 64 七笔 + 批 65 两笔全一样：起步那一段的布局与着色器预热落在相 0 上）。
 * 拿相 0 当基线算差值，等于把"相位序偏置"算进"糊的代价"里 —— 方向还正好**偏袒糊**（差值念成负的）。
 * 重载那一档（`-Pstep=400`）这笔账最明显：对相 0 算是 +1.34~+1.99ms，对相 2 算是 +2.0~+3.4ms。
 * ⇒ 四相配对：两相不糊、两相糊，差值从**两组的均值**算，序偏置在两组里各占一半就抵消了；
 *   而相 2 − 相 0（两枚都是不糊）单独念一行，它就是这把尺子自己的序偏置有多大。
 */
/**
 * 不糊 → 糊 → 糊 → 不糊（**ABBA**）：**相邻两对**配对，而不是 ABAB 的两组均值（第七十一批改判批 65 那一版）。
 *
 * ⚠️ 为什么从 ABAB 改成 ABBA（批 71 改判批 65 的算法第二次）：批 70 的真读数说这台机器
 * **整跑都在稳态化**（留下的四相一路往下：off 11.12→7.93、on 9.99→7.35），不是"只有第一相带预热"。
 * ABAB 拿两组均值相减时，那笔趋势按"每组的第二相比第一相快多少"整笔留在差值里（`回退 −3.18ms` 就是它），
 * 于是地板被撑到 3ms 而信号 0.85ms 永远盖不过去。
 * ⇒ ABBA：两对**相邻**配对 `on1−off1` 与 `on2−off2` 里的线性趋势**等量而反号**，取均值抵消；
 *   而这两对估计**自己相差多少**就是这把尺子的重复性 —— 那才是地板（同一批读数下散布 0.55ms 而非 3.18ms）。
 *   地板变小是设计的性质，不是挑出来的数：跨整跑那两枚自比仍然念（它是"趋势还在不在"的凭据），只是不再拿它当门槛。
 */
private val PHASES = listOf(false, true, true, false)

/**
 * 配对那四相**之前**先跑几相扔掉（第七十批，本批改判批 65 的抵消假设）。
 *
 * 批 65 写下"序偏置在两组里各占一半就抵消"时，没人去复核这句话。批 69 的第一笔真读数把它推翻了：
 * `S09C_回退 两相不糊相比 p95 −3.01ms` —— 相 0（不糊组）比相 2 慢 3ms，那笔起步惩罚**整笔落在第一相**，
 * 不是各占一半；方向正好**对糊不利**（把对照组抬高 ⇒ 差值被压小）。
 * ⇒ 配对只是"少算一半"，扔掉一相才是把这笔从账上摘掉。扔掉的那一相走**同一个循环**（所以也吃 ⑯ 那枚看门狗、
 * 也回同一个起点），但绝不进 `reads` 那本账，且自己念一行 p95 让"惩罚吸收到位没有"可复核。
 */
private const val DISCARD_PHASES = 1

/** 实际跑的相序：先扔掉的那些 + 配对那四相。编号从这一串取，`S09C_相N` 的 N 就是这里的位序。 */
private val ALL_PHASES = List(DISCARD_PHASES) { false } + PHASES

/**
 * `hold=<毫秒>`：每一相起手时真等那么久，让 WGC 拍得到那一相的屏。
 *
 * 为什么需要这一档：这一颗的产物是**毫秒数**，而毫秒数证明不了"那一相真的糊了" ——
 * 如果那层 `Modifier.blur` 在探针里静默没吃到，读数会一模一样地写着"糊不要钱"。
 * 默认 0（取数那一跑不许白等）。⚠️ 只在起手等，等完再回起点取样 ⇒ 等待不落在被量的那段里。
 */
private var holdMs = 0L

/**
 * `step=<像素>`：每帧推进多少。**这是那枚正对照的把手** ——
 * 三相读数一模一样时，只有两种成因："糊真的不要钱"与"这把尺子压根不认账"（帧间隔跟的是时钟不是绘制）。
 * 把步进从 40 调到 400，每帧要新铺的瓦片多一个量级，读数若一动不动就是后者，那份"没代价"不许写进结论。
 * 默认 40 与 `SCROLL_STEP_PX` 同一个数 ⇒ 不传参数时量的还是稳态慢滚那一档。
 */
private var stepPx = SCROLL_STEP_PX

/**
 * `overlay=anim`：让**覆盖层**每帧换内容（墙自己不动）。默认 `false` ⇒ 静帧，批 64 那两跑今天仍然可比。
 *
 * 为什么要这一档：批 64 量的是"墙自己在动"那一档，而生产里墙是静帧、动图在浮层里播（批 51）。
 * 那两种形状下"身后那层糊要不要每帧重算"完全可以不一样 —— 若墙那棵被缓存成一层、只有覆盖层重绘，
 * 糊就是**一次性的钱**；若整棵跟着覆盖层一起重合成，糊就是每帧的钱。默认不改是因为默认一变，
 * 旧读数就全成了另一件事的读数。
 */
private var overlayAnim = false

/**
 * `rounds=<N>`：整副 ABBA 连着跑几轮（第七十二批）。**默认 1** —— 默认一变，批 71 那一跑就成了另一件事的读数。
 *
 * 为什么需要这一档：批 71 那句"地板 0.16ms"本身是**一笔单跑的散布**，拿它去否决同一把尺子另一笔单跑的差值，
 * 等于用一次噪声判另一次噪声。要么承认"一轮说不出话"，要么真的跑几轮 —— 而每轮必须**独立**算地板、
 * **独立**下判词：把两轮的相并成一串再算，得到的是一笔看着样本更多的数，它恰好掩盖了唯一有用的那件事
 * （几轮各自的判词一致不一致）。
 */
private var rounds = 1

/**
 * 人**请求**的那一枚轮次，原样念法（第七十八批）。
 *
 * 只念生效值会把"参数没生效"说成"跑了 8 轮"：`-Prounds=12` 被上面那枚 `coerceIn(1, 8)` 夹成 8，
 * 而归属行写 `轮次=8` 时，那一跑的读数是"8 轮各自判了什么"，真相是"请求的 12 没吃到"。
 * 判读抽成纯函数而不是留在 `main()` 里（手册 §0 那条：留在里面就没有尺子 —— 四格各喂一种才量得出没合并）。
 */
internal fun roundsRequestReading(raw: String?): String {
    if (raw == null) return "未给（默认 1）"
    val wanted = raw.toIntOrNull() ?: return "读不出（请求=$raw）"
    val clamped = wanted.coerceIn(1, 8)
    return if (clamped == wanted) "原样=$wanted" else "夹过（请求=$wanted→$clamped）"
}

private var roundsRequest = roundsRequestReading(null)

fun main(args: Array<String>) {
    holdMs = args.firstOrNull { it.startsWith("hold=") }?.substringAfter("=")?.toLongOrNull() ?: 0L
    stepPx = args.firstOrNull { it.startsWith("step=") }?.substringAfter("=")?.toFloatOrNull() ?: SCROLL_STEP_PX
    overlayAnim = args.any { it.startsWith("overlay=") && it.substringAfter("=") == "anim" }
    roundsRequest = roundsRequestReading(args.firstOrNull { it.startsWith("rounds=") }?.substringAfter("="))
    rounds = args.firstOrNull { it.startsWith("rounds=") }?.substringAfter("=")?.toIntOrNull()?.coerceIn(1, 8) ?: 1
    val pid = ProcessHandle.current().pid()
    println(
        "S09C_窗口 已开 pid=$pid 半径=${DesktopWallBlurDp}dp 瓦片=${TILES}x${TILE_PX}px " +
            "每相帧数=${FRAMES_PER_PHASE} 预热=${WARMUP_FRAMES} 丢弃相=${DISCARD_PHASES} 相序=${PHASES.joinToString("/")} " +
            "起手等待=${holdMs}ms 步进=${stepPx}px 覆盖层=${if (overlayAnim) "动" else "静"} 轮次=${rounds}（请求=${roundsRequest}）"
    )
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Venera 模糊代价探针 #$pid",
            state = rememberWindowState(width = FRAME_W.dp, height = FRAME_H.dp),
        ) {
            BlurCostProbe()
        }
    }
}

/** 一相的读数：分位、丢帧数、堆。 */
private class PhaseRead(
    val index: Int,
    val blurred: Boolean,
    val p50: Double,
    val p95: Double,
    val max: Double,
    val dropped: Int,
    val frames: Int,
    val usedMb: Double,
    val maxMb: Double,
)

@Composable
private fun BlurCostProbe() {
    val tile = remember { tileBitmap() }
    val gridState = rememberLazyGridState()
    var blurOn by remember { mutableStateOf(PHASES.first()) }
    // 屏上那一枚自报家门：帧不带时间戳，靠它认"这一帧是哪一相"。
    // ⚠️ 必须写在那枚 `blurOn` 的**同一处** —— 两枚分开写就会有一相牌对了画面错了（批 48 那颗戳的同族）。
    var phaseTag by remember { mutableStateOf("相0 糊=off") }
    // 覆盖层那一枚逐帧在走的"动图替身"（只在 `overlay=anim` 那一档跑）
    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(overlayAnim) {
        if (!overlayAnim) return@LaunchedEffect
        // ⚠️ 走帧时钟而不是 `delay` 定步：两套节拍不同相，读数就会把"等"算进"画"。
        while (true) { withFrameMillis { t -> tick = t } }
    }

    LaunchedEffect(tile) {
        // 每一轮**独立一本账、独立算地板、独立下判词**（批 72）：跨轮并堆只会得到一笔看着样本更多的数，
        // 而它掩盖的正是唯一有用的那件事 —— 几轮各自的判词一致不一致。
        val verdicts = ArrayList<String>()
        // 跨轮并堆的是**每轮产出的那两对估计**（同一件事的 2N 枚观测），**不是各轮的相** ——
        // 并相就废掉了"每轮独立一本账"，也就把"几轮各自判了什么"重新埋进一堆数里（批 73）。
        val pairEstimates = ArrayList<Double>()
        for (round in 1..rounds) {
        val reads = ArrayList<PhaseRead>()
        println("S09C_轮${round}起手 共=${rounds}轮 相序=${PHASES.joinToString("/")} 丢弃相=${DISCARD_PHASES}")
        for ((i, on) in ALL_PHASES.withIndex()) {
            blurOn = on
            phaseTag = "相$i 糊=${if (on) "on" else "off"} 半径 ${DesktopWallBlurDp}dp 步进 ${stepPx}px 覆盖层=${if (overlayAnim) "动" else "静"}${if (i < DISCARD_PHASES) " 丢弃（不进账）" else ""}"
            println(
                "S09C_相${i}起手 进账=${if (i < DISCARD_PHASES) "否" else "是"} 糊=${if (on) "on" else "off"} " +
                    "起手等待=${holdMs}ms 看门狗=${FrameWaitMs + holdMs}ms"
            )
            // 整相套在超时里，**含起手那段等待** ⇒ 门槛是 `FrameWaitMs + holdMs`，否则 hold 一长就假报警。
            // 挂死那一格（锁屏时帧时钟不走）就落在这一段里不到账，由下面那支念出来并当场收尸。
            val frames = withTimeoutOrNull(FrameWaitMs + holdMs) {
                if (holdMs > 0L) delay(holdMs)
                sampleFrames(gridState, WARMUP_FRAMES)
                gridState.scrollToItem(0)
                sampleFrames(gridState, FRAMES_PER_PHASE)
            } ?: run {
                println("S09C_挂 相=$i 等帧超时=${FrameWaitMs + holdMs}ms（会话不可见？锁屏？）⇒ 帧时钟没走，这一跑没有读数")
                kotlin.system.exitProcess(2)
            }
            // 扔掉的那一相只念一行数（复核"起步那笔惩罚真被吃掉了没有"），**绝不进那本账** ——
            // 进了就等于把第一相的慢重新请回不糊组，批 69 那 3.01ms 的偏置就又回来了。
            if (i < DISCARD_PHASES) {
                val d = readOf(i, on, frames)
                println(
                    "S09C_丢弃相$i p95=%.2fms max=%.2fms 丢帧=%d/%d（这一行不进账，只答「起步那一段今天有没有被吃掉」）"
                        .format(d.p95, d.max, d.dropped, d.frames)
                )
            } else {
                reads.add(readOf(i, on, frames))
            }
        }
        for (r in reads) {
            println(
                "S09C_相${r.index} 糊=${if (r.blurred) "on" else "off"} 帧=${r.frames} " +
                    "p50=%.2fms p95=%.2fms max=%.2fms 丢帧=%d/%d(>%.1fms) 堆已用=%.0fMB/上限=%.0fMB"
                    .format(r.p50, r.p95, r.max, r.dropped, r.frames, DROP_MS, r.usedMb, r.maxMb)
            )
        }
        val onReads = reads.filter { it.blurred }
        val offReads = reads.filter { !it.blurred }
        fun pairDelta(off: PhaseRead, on: PhaseRead, sel: (PhaseRead) -> Double) = sel(on) - sel(off)
        // 两对**相邻**配对：ABBA 里 `相1(off)→相2(on)` 与 `相4(off)←相3(on)`，同一趋势在两对里等量反号 ⇒ 均值抵消。
        val pairA = pairDelta(offReads[0], onReads[0]) { it.p95 }
        val pairB = pairDelta(offReads[1], onReads[1]) { it.p95 }
        val pairA50 = pairDelta(offReads[0], onReads[0]) { it.p50 }
        val pairB50 = pairDelta(offReads[1], onReads[1]) { it.p50 }
        println("S09C_对1 相邻两对里的第一对（糊相${onReads[0].index} 减 不糊相${offReads[0].index}）p95 %+.2fms".format(pairA))
        println("S09C_对2 相邻两对里的第二对（糊相${onReads[1].index} 减 不糊相${offReads[1].index}）p95 %+.2fms".format(pairB))
        pairEstimates.add(pairA)
        pairEstimates.add(pairB)
        val delta = (pairA + pairB) / 2.0
        println(
            "S09C_差值 相邻两对取均值（ABBA，趋势在两对里反号抵消） p50 %+.2fms p95 %+.2fms 丢帧 %+d 帧"
                .format(
                    (pairA50 + pairB50) / 2.0, delta,
                    onReads.sumOf { it.dropped } - offReads.sumOf { it.dropped },
                )
        )
        // 跨整跑那两枚同条件自比**仍然念** —— 它是"趋势还在不在"的凭据；只是批 71 起不再拿它当门槛
        // （拿它当门槛就是批 70 那一读的错：趋势已被 ABBA 抵消掉了，门槛里却还留着整笔趋势）。
        val trendOff = offReads[1].p95 - offReads[0].p95
        val trendOn = onReads[1].p95 - onReads[0].p95
        val tolerance = maxOf(2.0, offReads[0].p95 * 0.2)
        println(
            "S09C_回退 跨整跑两枚同条件自比（趋势残量，只作凭据不当门槛） 不糊 %+.2fms 糊 %+.2fms 容差=%.2fms 判定=%s"
                .format(
                    trendOff, trendOn, tolerance,
                    if (kotlin.math.abs(trendOff) <= tolerance && kotlin.math.abs(trendOn) <= tolerance) "这跑可比" else "这跑仍在稳态化（趋势靠 ABBA 抵消）",
                )
        )
        println("S09C_重复 同一件事量了两遍 p95 %+.2fms（两对估计差太多 ⇒ 这把尺子自己就不稳）".format(pairB - pairA))
        // 地板 = 那两对估计自己的散布：与差值同一把尺、同一个形状，不需要人对着三行数字 eyeball。
        val noiseFloor = kotlin.math.abs(pairA - pairB)
        val verdict = if (kotlin.math.abs(delta) > noiseFloor) "量得出" else "量不出"
        println(
            "S09C_判 差值|p95|=%.2fms 噪声地板=%.2fms 判定=%s 轮=$round"
                .format(
                    kotlin.math.abs(delta), noiseFloor,
                    if (kotlin.math.abs(delta) > noiseFloor) "量得出（差值盖过这把尺子自己的重复性）" else "量不出（差值没盖过噪声地板；这一格今天仍未知）",
                )
        )
        verdicts.add(verdict)
        println("S09C_轮$round 收尾 本轮判定=$verdict")
        }
        val same = verdicts.distinct().size == 1
        if (pairEstimates.size < 4) {
            println(
                "S09C_总判 轮数=${verdicts.size} 各轮=${verdicts.joinToString("/")} 一致=${if (same) "是" else "否"} " +
                    "枚估计=${pairEstimates.size} 判定=轮数不够（少于 4 枚估计 ⇒ 散布算不出来，这一格今天说不出话，加 `-Prounds=2` 以上）"
            )
        } else {
            // 两枚数算不出散布（批 72 轮 2 的地板就是 0.00ms）；4 枚以上才有"极差"这一说。
            // ⚠️ 但**极差是单枚观测的函数**：批 73 那枚 `跨轮地板=2.66ms` 整笔由轮 1 的一格野数撑起
            //   （其余 7 枚都在 ±0.1ms 量级）⇒ "量不出"读的其实是"有一轮出过野数"。稳健化靠**换估计量**（四分位距），
            //   不靠筛数 —— 筛数就是挑数。两把都上读数：只念一把就看不见另一把在说什么。
            val poolFloor = pairEstimates.max() - pairEstimates.min()
            val iqrFloor = quartile(pairEstimates, 0.75) - quartile(pairEstimates, 0.25)
            val poolDelta = pairEstimates.sum() / pairEstimates.size
            val byRange = kotlin.math.abs(poolDelta) > poolFloor
            val byIqr = kotlin.math.abs(poolDelta) > iqrFloor
            println(
                "S09C_总判 轮数=${verdicts.size} 各轮=${verdicts.joinToString("/")} 一致=${if (same) "是" else "否"} " +
                    "枚估计=${pairEstimates.size} 跨轮均值=%+.2fms 极差地板=%.2fms IQR地板=%.2fms 判定=%s"
                    .format(
                        poolDelta, poolFloor, iqrFloor,
                        if (byRange != byIqr) "两把地板给的话不一样（极差说${if (byRange) "量得出" else "量不出"}而 IQR 说${if (byIqr) "量得出" else "量不出"}）⇒ 有一枚野估计撑大了极差，要引用就得带着这句"
                        else if (byIqr) "量得出（盖过了稳健那把地板；仍要看各轮说法一致不）"
                        else "量不出（没盖过稳健那把地板；这一格今天仍未知）",
                    )
            )
        }
        println("S09C_DONE")
        kotlin.system.exitProcess(0)
    }

    val wall = if (blurOn) Modifier.blur(radius = DesktopWallBlurDp.dp) else Modifier
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize().then(wall),
        ) {
            items(TILES) {
                Image(bitmap = tile, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            }
        }
        // 动图替身：与牌同一侧 —— 挂在糊那层**外面**（生产里浮层那张图是锐利的，糊的是它背后那面墙）
        if (overlayAnim) AnimatingOverlay(tick, Modifier.align(Alignment.TopEnd).padding(90.dp))
        // 牌挂在糊那层的**外面**（它是给拍帧的人认相用的，糊掉了就读不出）
        BasicText(
            text = phaseTag,
            style = TextStyle(color = Color.White, fontSize = 22.sp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/**
 * 动图替身：一枚每帧换位置与色相的方块（200×200dp 那一块区域里跑）。
 *
 * 它**不读** `blurOn` —— 生产里浮层那张图也不吃糊（吃糊的是它背后那面墙）。
 * 每帧都换内容 ⇒ 每帧都有一次真重绘，这才是"动图在播时那层糊要不要跟着重算"的那一问。
 */
@Composable
private fun AnimatingOverlay(tick: Long, modifier: Modifier) {
    val phase = (tick / 16L).toFloat()
    Canvas(modifier.size(220.dp)) {
        val k = ((phase % 60f) / 60f)
        drawRect(
            color = Color.hsl(k * 360f, 0.85f, 0.55f),
            topLeft = Offset(size.width * k, size.height * (1f - k)),
            size = androidx.compose.ui.geometry.Size(size.width * 0.45f, size.height * 0.45f),
        )
        drawRect(color = Color.White.copy(alpha = 0.85f), topLeft = Offset(0f, size.height * k), size = androidx.compose.ui.geometry.Size(size.width * 0.2f, size.height * 0.2f))
    }
}

/** 一相的取样：每帧推进 [stepPx]，时刻从帧时钟取。第一帧没有前帧可比，丢掉。 */
private suspend fun sampleFrames(gridState: LazyGridState, frames: Int): List<Long> {
    val out = ArrayList<Long>(frames)
    var prev = 0L
    repeat(frames) {
        withFrameNanos { t ->
            if (prev != 0L) out.add(t - prev)
            prev = t
        }
        gridState.scrollBy(stepPx)
    }
    return out
}

/**
 * 那串估计的第 [q] 分位（第七十四批）：排序后取最近的一枚，不做插值 ——
 * `n` 只有个位数，插值不会改变结论，而**稳健性**才是这里要的那件事（一枚野数撑不满四分位距）。
 */
private fun quartile(xs: List<Double>, q: Double): Double {
    val sorted = xs.sorted()
    return sorted[((sorted.size - 1) * q).roundToInt()]
}

private fun readOf(index: Int, blurred: Boolean, frames: List<Long>): PhaseRead {
    val sorted = frames.sorted()
    fun pct(p: Double) = sorted[(sorted.size * p).roundToInt().coerceAtMost(sorted.size - 1)] / 1_000_000.0
    val rt = Runtime.getRuntime()
    return PhaseRead(
        index, blurred,
        p50 = pct(0.50), p95 = pct(0.95), max = pct(1.0),
        dropped = frames.count { it > DROP_MS * 1_000_000 }, frames = frames.size,
        usedMb = (rt.totalMemory() - rt.freeMemory()) / 1048576.0, maxMb = rt.maxMemory() / 1048576.0,
    )
}

/**
 * 本机造一张 256×256 的**硬边棋盘**瓦片（4×4 格、每格 64px）。
 *
 * ⚠️ 刻意不用随机噪声：噪声在 108dp 的格子里被缩采样成一团软糊之后，"糊了"与"没糊"在帧上**读不出差别**
 * （本批头一跑就是这样：两帧一软一锐，而软的那帧来自 `糊=off` 那一相 —— 分不清是谁在骗人）。
 * 硬边大格子的对比度是低频的，18dp 那层一上就必然把格边抹掉 ⇒ 那一帧才答得出"这一相真糊了没有"。
 */
private fun tileBitmap(): androidx.compose.ui.graphics.ImageBitmap {
    val img = BufferedImage(TILE_PX, TILE_PX, BufferedImage.TYPE_3BYTE_BGR)
    val g = img.createGraphics()
    val cell = TILE_PX / 4
    for (y in 0 until 4) {
        for (x in 0 until 4) {
            g.color = if ((x + y) % 2 == 0) java.awt.Color.BLACK else java.awt.Color.WHITE
            g.fillRect(x * cell, y * cell, cell, cell)
        }
    }
    g.dispose()
    val bos = java.io.ByteArrayOutputStream()
    javax.imageio.ImageIO.write(img, "png", bos)
    return org.jetbrains.skia.Image.makeFromEncoded(bos.toByteArray()).use { it.toComposeImageBitmap() }
}
