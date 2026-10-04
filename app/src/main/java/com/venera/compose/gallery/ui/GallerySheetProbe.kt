package com.venera.compose.gallery.ui

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 「关于这张图」面板的**只读探针**（2026-10-04，诊断包专用，定死成因后就撤）。
 *
 * ## 它要回答的唯一一个问题（15:40 按用户第二次读数改过一档）
 *
 * 第一次读数我给的是"拖过面板之后才坏、松手后还在微动"，用户 15:40 整条推翻并重新说：
 * **第一次打开就全死、松手后没有任何微动、但面板能正常拖**。
 * 所以"位移不收敛 ⇒ 命中区每帧在变"那条假设**作废**（它解释不了"第一次打开就死"与"没有微动"），
 * 这一节留着只是记着我自己走过的那条错路。
 *
 * 新的形状只剩一条要分：手指那一下**到没到那枚 chip 的盒子**。
 * 两种结果对应两族完全不同的成因 ——
 * - **盒子收到了、clickable 没响**：事件在 chip 的盒子与它自己的 `combinedClickable` 之间被吃掉
 *   （被消费、抬手没回来、或 slop 被判定移动）。查的是**内**：VeneraChip 那条 modifier 链。
 * - **盒子根本没收到**：命中测试压根没走到这一支。查的是**外**：有一层透明覆盖物在它们上面，
 *   或者**画出来的位置与盒子位置不在一起**（面板是靠 `graphicsLayer { translationY = offset }` 挪的，
 *   而 `pointerInput` 的坐标换算要穿过这一层 —— 这一层与布局位置不一致时，就是"看得见、点不着"）。
 *
 * 所以每一枚标签药丸上额外挂一颗 [tapWitness]（只观察、不消费），它报的是"这个盒子被命中了没有"，
 * 而调用点那些 `到达 …` 报的是"它自己的 onClick/onLongClick 响了没有"。两颗对照就是二分。
 *
 * ## 三条自我约束
 *
 * 1. **不写任何 Compose 状态**。坐标存在 [SheetCoordsHolder] 这个普通类里，而不是
 *    `mutableStateOf` —— 探针若自己引发重组，我可能正在量它自己造出来的抖。
 * 2. **不改任何判据与手势链**。只 `Log`，且观察一律走 `PointerEventPass.Initial` 不消费。
 * 3. `positionInLayout()` 在这个 compose 版本上**不存在**（编译期实测），所以"画在哪 vs 布局在哪"
 *    这一对照拿不到。改成量另一条同样能造成"看得见点不着"的：`boundsInWindow()` 是**会被裁剪**的，
 *    而被裁掉的区域在 Compose 里同样收不到触摸 —— 它与 `boundsInWindow(clipBounds = false)`
 *    的差值就是这一条的直接读数。
 */
internal object GallerySheetProbe {

    const val TAG = "GallerySheetProbe"

    /** 60 × 120ms ≈ 7.2 秒，够覆盖「拖一下、松手、等它停」这一整个动作。 */
    private const val TICKS = 60
    private const val INTERVAL_MS = 120L

    fun log(what: String) {
        Log.i(TAG, what)
    }

    /**
     * 逐 tick 记面板当前的位置。**两档一起看才是重点**：
     *
     * - `画=` 是 `boundsInWindow()`（**会被裁剪**：官方源码写着"若这一层与窗口之间有裁剪不重叠，
     *   返回的可以是空矩形"，而**被裁掉的区域在 Compose 里也收不到触摸**）；
     * - `未裁=` 是 `boundsInWindow(clipBounds = false)` —— 同一块、忽略所有中间裁剪的裸位置；
     * - `差` 不为 0、或 `画` 突然变成 0（= 被裁空）而 `未裁` 还在正常位置，
     *   那就不是"抖不抖"的问题，而是**这一块被裁掉了 ⇒ 看不见也点不着**的形状。
     *
     * `dy` 仍然报：用户说没有微动，那就用它把这句话钉成数（应当全为 0）。
     */
    suspend fun sample(label: String, holder: SheetCoordsHolder) {
        var previous = Float.NaN
        var previousHeight = Float.NaN
        log("$label 采样开始 ticks=$TICKS interval=${INTERVAL_MS}ms")
        repeat(TICKS) { index ->
            val drawn = holder.topOrNull()
            if (drawn == null) {
                log("$label #$index 读不到位置（节点未挂载或已脱离）")
            } else {
                val dy = if (previous.isNaN()) Float.NaN else drawn - previous
                previous = drawn
                val unclipped = holder.unclippedTop()
                // 高度单独报差值：那条"内容高 ↔ 面板锚点互相推"的成因留下的指纹就是这一列在抖。
                val height = holder.heightOrNull()
                val dh = if (height.isNaN() || previousHeight.isNaN()) Float.NaN else height - previousHeight
                previousHeight = height
                log(
                    "$label #$index 画=$drawn 未裁=$unclipped 差=${drawn - unclipped} " +
                        "dy=$dy 高=$height dh=$dh",
                )
            }
            delay(INTERVAL_MS)
        }
        log("$label 采样结束")
    }

    /**
     * 挂在每一枚 chip 上的**只观察**见证：这一支的盒子有没有被命中、以及**事件到 Final 段时
     * 是不是已经被下面的人消费掉了**。
     *
     * 与调用点那些 `到达 …` 的分工是这颗探针的全部意义：
     * - 只有 `盒子收到`、没有 `到达` ⇒ 事件进了盒子却没成点击；
     * - 两条都没有 ⇒ 命中测试压根没走到这一支（上面有透明覆盖物，或盒子与画出来的位置不在一起）。
     *
     * ⚠️ 走 **Final** 段而不是 Initial：Initial 是父先子后，那时读 `isConsumed` 永远是 false，
     * 量不到"被下面的子节点吃了"这一种；Final 是子先父后，这里的 `被消费=true` 才是证据。
     * 16:53 那一读就是栽在这一点上 —— 它报了 `被消费=false`，而那个 false 什么也没说。
     *
     * `盒子顶`/`盒子高` 一起报：拿它减去见证里的 local y 就是触点在窗口里的位置，
     * 这样"我点的是第几行"能与"日志说是哪一枚"对得上号。
     */
    @Composable
    fun tapWitness(label: String): Modifier {
        val holder = SheetCoordsHolder()
        return Modifier
            .onGloballyPositioned { holder.coordinates = it }
            .pointerInput(label, holder) {
                awaitEachGesture {
                    val down = awaitFirstDown(false, PointerEventPass.Final)
                    val boxTop = holder.topOrNull()
                    val boxHeight = holder.heightOrNull()
                    var last = down.position
                    var moved = 0f
                    var pressed = true
                    var consumed = false
                    while (true) {
                        val change = awaitPointerEvent(pass = PointerEventPass.Final)
                            .changes.firstOrNull { it.id == down.id } ?: break
                        val dx = change.position.x - last.x
                        val dy = change.position.y - last.y
                        moved = max(moved, sqrt(dx * dx + dy * dy))
                        last = change.position
                        pressed = change.pressed
                        consumed = change.isConsumed
                        if (!pressed) break
                    }
                    log(
                        "$label 盒子收到 down=(${down.position.x.toInt()},${down.position.y.toInt()}) " +
                            "盒子顶=$boxTop 盒子高=$boxHeight " +
                            "触点窗口y=${if (boxTop == null) Float.NaN else boxTop + down.position.y} " +
                            "移动=${moved.toInt()}px 已抬手=${!pressed} 到Final已被消费=$consumed",
                    )
                }
            }
    }

    /**
     * 面板根节点上的**只观察**触摸读数：每次按下记一笔，抬手时记移动距离与面板姿态。
     *
     * 走 [PointerEventPass.Initial] 且**全程不消费**，所以子节点（每一枚 chip）照常收到
     * 同一个事件 —— 它不会把命中让给自己，也就不会自己造出"点不动"。
     *
     * 这一路报的是"手指在面板根节点上按了哪、挪了多少、那两下之间面板在哪"。
     * 它存在的理由是把"手指自己滑过了 touch slop"这一种解释量掉：若 `移动=` 只有几个像素
     * 而点击仍不到达，那就不是手的问题，是目标或链路的问题。
     */
    @Composable
    fun touchProbe(label: String, holder: SheetCoordsHolder): Modifier {
        val view = LocalView.current
        return remember(holder) {
            Modifier.pointerInput(holder) {
                awaitEachGesture {
                    // 参数名在这个版本是位置参（见 `components/backdrop/DragGestureInspector.kt:29`
                    // 同一写法），Initial pass + requireUnconsumed=false = 纯被动观察。
                    val down = awaitFirstDown(false, PointerEventPass.Initial)
                    val topAtDown = holder.topOrNull()
                    var last = down.position
                    var moved = 0f
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val dx = change.position.x - last.x
                        val dy = change.position.y - last.y
                        moved = max(moved, sqrt(dx * dx + dy * dy))
                        last = change.position
                        if (!change.pressed) break
                    }
                    // view.height 一起报：面板 top 与它之比就是"盖住了几成屏"，
                    // 这一档是上一轮那条"盖满屏之后才坏"的判据，得能对上数。
                    log(
                        "$label 触摸 down=(${down.position.x.toInt()},${down.position.y.toInt()}) " +
                            "移动=${moved.toInt()}px top按下=$topAtDown top抬起=${holder.topOrNull()} " +
                            "屏高=${view.height}",
                    )
                }
            }
        }
    }
}

/**
 * 面板内容根节点的坐标持有器。**刻意是普通类不是状态**，理由见 [GallerySheetProbe] 的头注。
 */
internal class SheetCoordsHolder {
    var coordinates: LayoutCoordinates? = null

    /** 面板此刻在**窗口**里的上沿（会被中间裁剪影响 = 屏上真能摸到的那一条边）。 */
    fun topOrNull(): Float? = coordinates?.takeIf { it.isAttached }?.boundsInWindow()?.top

    /**
     * 同一块、**忽略所有中间裁剪**的上沿。
     *
     * 与 [topOrNull] 一起用才说明问题：两者相等 = 这块没被裁；`topOrNull` 变成 0（官方源码写着
     * 完全被裁时返回 `Rect.Zero`）而这里还在正常位置 = **这块被裁掉了**，
     * 而被裁掉的区域在 Compose 里也收不到触摸 —— 那正是"看得见、点不着"的一条现成机制。
     */
    fun unclippedTop(): Float =
        coordinates?.takeIf { it.isAttached }?.boundsInWindow(clipBounds = false)?.top ?: Float.NaN

    /** 面板内容量出来的高度。它一抖，面板的锚点就跟着抖 —— 与 [topOrNull] 是两回事。 */
    fun heightOrNull(): Float = coordinates?.let { it.size.height.toFloat() } ?: Float.NaN
}
