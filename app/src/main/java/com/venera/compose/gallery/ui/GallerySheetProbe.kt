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
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 「关于这张图」面板的**只读探针**（2026-10-04，诊断包专用，定死成因后就撤）。
 *
 * ## 它要回答的唯一一个问题
 *
 * 三条读数已经把范围收得很窄：
 * - 拖过面板**本体**之后，面板里所有点击与长按一起失效，但上下滑动照旧；
 * - 只在内容区里滚（面板没被拖过）则一切正常；
 * - 松手之后能看到面板还在微动 / 回弹。
 *
 * 三条合起来指向一条机制：面板位移在松手后**不收敛**，而面板是靠
 * `graphicsLayer { translationY = offset }` 挪的 —— 位移每帧在变，命中区就每帧在变，
 * 手指按下到抬起之间目标挪过了 touch slop，Compose 于是把这一次当成"移动"而不是"点击"，
 * **单击与长按一起作废**；而拖动本来就在移动东西，所以照旧可用。
 *
 * ## 为什么必须量而不是继续推
 *
 * 上一轮我把这条症状判成"底栏那枚钮被挤挪位"（提交 2002e9f），修的是完全另一条链路，
 * 所以你看着"完全没改"是对的说法。这次先拿读数。
 *
 * ## 两条自我约束
 *
 * 1. **不写任何 Compose 状态**。坐标存在 [SheetCoordsHolder] 这个普通类里，而不是
 *    `mutableStateOf` —— 探针若自己引发重组，我可能正在量它自己造出来的抖。
 * 2. **不改任何判据与手势链**。只 `Log`。
 *
 * 读数用 `LayoutCoordinates.boundsInWindow()`：它**已经把图层变换算进去**，
 * 所以量到的就是命中区真正在的地方，而不是布局里的名义位置。
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
     * 逐 tick 记面板当前的窗口位置。
     *
     * `dy` 是这一 tick 相对上一 tick 挪了多少像素，读数怎么念：
     * - 松手后 `dy` 很快归 0 ⇒ 面板收敛 ⇒ "点不动"的成因不在这条上，假设作废、换方向查；
     * - 松手后 `dy` 长期不为 0（哪怕只有零点几像素）⇒ 坐实"位移不收敛"；
     * - `dy` 在几个值之间来回跳 ⇒ 是在两个锚点之间反复回弹，那是更具体的一条成因。
     */
    suspend fun sample(label: String, holder: SheetCoordsHolder) {
        var previous = Float.NaN
        var previousHeight = Float.NaN
        log("$label 采样开始 ticks=$TICKS interval=${INTERVAL_MS}ms")
        repeat(TICKS) { index ->
            val top = holder.topOrNull()
            val height = holder.heightOrNull()
            if (top == null) {
                log("$label #$index 读不到位置（节点未挂载或已脱离）")
            } else {
                val dy = if (previous.isNaN()) Float.NaN else top - previous
                // 高度单独报差值：上一轮那条"面板自己推自己"的成因（内容高 ↔ 面板锚点互相推）
                // 留下的指纹就是这一列在抖。只看 top 分不出"面板在动"还是"面板在变高"。
                val dh = if (height.isNaN() || previousHeight.isNaN()) Float.NaN else height - previousHeight
                previous = top
                previousHeight = height
                log("$label #$index top=$top dy=$dy 高=$height dh=$dh")
            }
            delay(INTERVAL_MS)
        }
        log("$label 采样结束")
    }

    /**
     * 面板根节点上的**只观察**触摸读数：每次按下记一笔，抬手时记移动距离与面板姿态。
     *
     * 走 [PointerEventPass.Initial] 且**全程不消费**，所以子节点（每一枚 chip）照常收到
     * 同一个事件 —— 它不会把命中让给自己，也就不会自己造出"点不动"。
     *
     * 这一路读数用来拆掉另一种解释：若"点不动"是手指自己滑过了 touch slop，
     * 那 `移动=` 会是个几百像素的数；若手指没动（`移动=` 只有零点几）而点击仍不到达，
     * 那就只剩"目标在手指底下挪了"这一条，也就是 [sample] 那一路要证的位移不收敛。
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

    /** 面板此刻在**窗口**里的上沿；节点没挂载（还没量到 / 已经撤了）就是 null。 */
    fun topOrNull(): Float? = coordinates?.takeIf { it.isAttached }?.boundsInWindow()?.top

    /** 面板内容量出来的高度。它一抖，面板的锚点就跟着抖 —— 与 [topOrNull] 是两回事。 */
    fun heightOrNull(): Float = coordinates?.let { it.size.height.toFloat() } ?: Float.NaN
}
