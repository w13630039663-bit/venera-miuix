package com.venera.compose.gallery.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 「关于这张图」面板的**只读探针**（2026-10-04 起，诊断包专用，定死成因后整文件删）。
 *
 * ## 2026-10-05 v2：为什么重写了取数方式
 *
 * 上一版有两支量具，**都不可能在偶发场景里命中**：
 *
 * - `sample()` 是**定时轮询**（60 × 120ms，`LaunchedEffect(Unit)` 只跑一趟）——它报的
 *   `动画中=` 确实是有分叉力的那一列，但它①只在开面板后 7.2 秒内活着、②粒度 120ms、
 *   ③**与「哪一次按下」对不上时间轴**；
 * - `touchProbe()` 虽然是**每次按下**都产生一行，却**不带 `动画中=`**。
 *
 * ⇒ 「判死的那一列」与「每次按下都产生的那一行」从来不在同一行上。一个 7~100ms 级的偶发态
 * 落进一个只活 7.2 秒的 120ms 采样窗口，概率极低 —— 这是「取了 4~5 次日志都白取」的真正原因，
 * 不是运气差。
 *
 * v2 改成三条一起上：
 * 1. **把面板姿态并进「每一次按下」那一行**（[touchProbe] 收 `SheetState`，按下/抬手各读一次）
 *    —— 点得动的每一笔、点不动的每一笔，都自带答案，**不需要复现**；
 * 2. **落盘**（[install]）：每行同时追加到外部私有目录，`adb pull` 直取，不再依赖 logcat 的手速与缓冲区；
 * 3. **死档自动快照**（[dumpDeadTap]）：抬手时命中异常指纹就把最近 [RING_CAPACITY] 行整段 dump 成一个新文件
 *    —— 出事的那一秒自己留证，人甚至不需要注意到它出过事。
 *
 * ## 老的三条自我约束（仍然有效）
 *
 * 1. **不写任何 Compose 状态**。坐标存在 [SheetCoordsHolder] 这个普通类里，而不是 `mutableStateOf`
 *    —— 探针若自己引发重组，我可能正在量它自己造出来的抖。
 * 2. **不改任何判据与手势链**。全程只 `Log`、**一次都不 `consume()`**。
 * 3. `positionInLayout()` 在这个 compose 版本上**不存在**（编译期实测），所以「画在哪 vs 布局在哪」
 *    这一对照拿不到；改成量 `boundsInWindow()`（**会被裁剪**）与 `boundsInWindow(clipBounds = false)` 的差值。
 */
/**
 * 把**同一次按下**在 Initial / Main / Final 三段各读一遍消费标记。
 *
 * 三段各能夹住一族（同一次事件里三段是全节点依次跑完的：所有节点 Initial → 所有节点 Main → 所有节点 Final）：
 * - `初=true` ⇒ 在这一层之前就有**祖先**吃掉了它（Initial 段是父→子，所以是本节点的祖先）；
 * - `初=false 主=true` ⇒ 吃它的是**比这一层更深或 z 序更高的节点**，或者就是这一层自己的 clickable；
 * - `初=false 主=false` ⇒ 到 clickable 轮到时 down 还是干净的，失败在它自己的判定里。
 *
 * 这条判式的出处不是推测：foundation 1.12 `Clickable.kt:1113` 写的是
 * `if (pointerEvent.isChangedToDown(requireUnconsumed = true)) handleDownEvent(...)` ——
 * **down 只要被吃过，clickable 就根本不开始跟踪**：不消费抬手、不回调 onClick、不发 PressInteraction，
 * 于是看起来就是"看得见、点不着，但拖动正常"。
 *
 * ⚠️ 只能是**文件级的扩展**：挂在 object 上会被 Kotlin 判成"以 object 为接收者的受限调用"，
 * 报 `Restricted suspending functions can invoke member or extension suspending functions
 * only on their restricted coroutine scope`。
 */
private suspend fun AwaitPointerEventScope.consumeFlags(down: PointerInputChange): ConsumeFlags =
    ConsumeFlags(
        initial = down.isConsumed,
        main = awaitPointerEvent(PointerEventPass.Main)
            .changes.firstOrNull { it.id == down.id }?.isConsumed,
        final = awaitPointerEvent(PointerEventPass.Final)
            .changes.firstOrNull { it.id == down.id }?.isConsumed,
    )

/**
 * 同一次按下的三段消费标记。
 *
 * 以前是直接拼字符串的，判「这一笔是不是被祖先吃了」只能去 `startsWith("初=true")` 里对字面量 ——
 * 那是拿**日志格式**当判据，改一个字就静默失效。现在三段各自留一个字段，字符串只用于打印。
 */
private class ConsumeFlags(
    val initial: Boolean,
    val main: Boolean?,
    val final: Boolean?,
) {
    /** down 在本节点之前就已经被消费 ⇒ 吃它的是**祖先**。Initial 段父先于子，所以这条是本层的祖先。 */
    val eatenByAncestor: Boolean get() = initial

    override fun toString(): String = "初=$initial 主=$main 终=$final"
}

internal object GallerySheetProbe {

    const val TAG = "GallerySheetProbe"

    /**
     * 落盘目录与文件名。
     *
     * 走**外部私有目录** `getExternalFilesDir(null)` ⇒ `/sdcard/Android/data/<包名>/files/forensics/`，
     * `adb pull` 直取、**不需要 `run-as`**（对比 `filesDir` 必须 run-as）。
     * 如实记一笔：Android 11+ 的系统文件管理器**不能**浏览 `Android/data`，所以主路径是 adb pull。
     */
    private const val SINK_DIR = "forensics"
    private const val SINK_FILE = "sheet-probe.log"

    /**
     * 内存环形缓冲的行数。日志是**事件驱动**的（每次按下一条、每次状态翻转一条），不是逐帧，
     * 所以 500 行大致覆盖最近几分钟 —— 抬手命中异常时把这一段整段 dump 出去。
     */
    private const val RING_CAPACITY = 500

    /** 滚动截断：超过就整文件重开，避免无限长。 */
    private const val SINK_MAX_BYTES = 256L * 1024L

    /** 同一次死档会有面板根与 chip 两条见证同时报，dump 做一次节流。 */
    private const val DUMP_THROTTLE_MS = 2000L

    /** 只读见证认作「点击式手势」的最大移动量：超过它多半是滚动/拖动，不是点不动。 */
    private const val TAP_MOVE_SLOP_PX = 12f

    private val lock = Any()
    private val ring = ArrayDeque<String>()
    private val seq = AtomicInteger(0)
    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sheet-probe-sink").apply { isDaemon = true }
    }

    @Volatile
    private var sink: File? = null

    private var lastDumpAt = 0L

    /**
     * 装好落盘出口。**只在 GalleryInfoSheet 的 `LaunchedEffect` 里调一次**；
     * 之后所有 [log] 都会同时写到 logcat 与文件。探不到外部目录就回落应用内部目录（不静默）。
     */
    fun install(context: Context) {
        if (sink != null) return
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, SINK_DIR)
        val file = File(dir, SINK_FILE)
        sink = file
        io.execute { runCatching { dir.mkdirs(); if (!file.exists()) file.createNewFile() } }
        log("探针 落盘=${file.absolutePath}")
    }

    /**
     * 记一行。**同时**进 logcat、进内存环形缓冲、进落盘文件。
     *
     * 时间戳用 `String.format` 而不是共享的 `SimpleDateFormat` —— 这个方法会被主线程（手势回调）
     * 与 IO 线程同时调，共享 formatter 不是线程安全的。
     */
    fun log(what: String) {
        val line = "${stamp(System.currentTimeMillis())} #${seq.incrementAndGet()} $what"
        Log.i(TAG, line)
        synchronized(lock) {
            ring.addLast(line)
            while (ring.size > RING_CAPACITY) ring.removeFirst()
        }
        val file = sink ?: return
        io.execute {
            runCatching {
                if (file.length() > SINK_MAX_BYTES) file.writeText("")
                file.appendText(line + "\n")
            }
        }
    }

    /**
     * 死档快照：把内存里最近 [RING_CAPACITY] 行整段写成一个新文件，**不覆盖滚动日志**。
     *
     * 触发指纹有两条，任一命中即 dump（宽松取，宁多不漏）：
     * - **(a)** 面板根 down 在 **Initial 段就已被消费** —— 拖动层抢按下的直接指纹。
     *   健康态下 `BottomSheet.kt:329-334` 的 `anchoredDraggable` 走 `startDragImmediately =
     *   state.isAnimationRunning == false`，Initial 段**不该**有人消费；
     * - **(b)** chip 的只读见证「盒子收到 down」而抬手 `终=false`（且几乎没移动）——
     *   01:38 那份唯一落盘的读数里，死档 40+ 次全是这一条，活档是 `true`。
     */
    fun dumpDeadTap(reason: String) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (now - lastDumpAt < DUMP_THROTTLE_MS) return
            lastDumpAt = now
        }
        val dir = sink?.parentFile ?: return
        val name = String.format(
            Locale.US,
            "deadtap-%1\$tY%1\$tm%1\$td-%1\$tH%1\$tM%1\$tS.log",
            now,
        )
        val file = File(dir, name)
        val body = synchronized(lock) { ring.joinToString("\n") }
        io.execute {
            runCatching {
                dir.mkdirs()
                file.writeText(
                    "死档快照（面板点不动）\n" +
                        "原因：$reason\n" +
                        "时刻：${stamp(now)}\n" +
                        "以下为触发前最近 ${RING_CAPACITY} 行（含本行之后追加的落盘行）\n" +
                        "────────────\n" +
                        body + "\n",
                )
            }
        }
        log("死档 已 dump -> ${file.absolutePath} （$reason）")
    }

    private fun stamp(now: Long): String =
        String.format(Locale.US, "%1\$tH:%1\$tM:%1\$tS.%1\$tL", now)

    /**
     * 面板此刻的姿态，一行字符串。**这是本探针唯一有分叉力的那一列**：
     *
     * `动画中=` 就是 `SheetState.isAnimationRunning`，而它 ⇔ `AnchoredDraggableState.dragTarget != null`
     * （foundation `AnchoredDraggable.kt:935-936`）。面板拖动层的 `startDragImmediately` 用的正是它
     * （m3 `BottomSheet.kt:329-334` 没传该参 ⇒ 走默认值 `state.isAnimationRunning`），
     * 一旦为真，拖动层就在 **Initial 段**把每一笔 DOWN 直接 `consume()` 掉
     * （`Draggable.kt:812-820`，注释原话 "If we shouldn't await touch slop, we consume the event
     * immediately"）⇒ 面板里每一枚 clickable 都不开始跟踪
     * ⇒ **什么内容都点不了、长按也没反应，但拖得动**。
     *
     * `requireOffset()` 在「还没量到 / 已脱离」时会抛（官方就写着 offset 首帧才初始化），
     * 探针不许把宿主带崩，所以读不到就**报出来**、不静默。
     */
    @OptIn(ExperimentalMaterial3Api::class)
    fun poseOf(state: SheetState): String = runCatching {
        val offset = runCatching { state.requireOffset() }.getOrDefault(Float.NaN)
        "动画中=${state.isAnimationRunning} 偏移=$offset " +
            "current=${state.currentValue} target=${state.targetValue}"
    }.getOrElse { "姿态读不到（$it）" }

    /**
     * 每一枚标签药丸上的**只观察**见证：这一支的盒子有没有被命中、以及**事件到 Final 段时
     * 是不是已经被下面的人消费掉了**。
     *
     * 与调用点那些 `到达 …` 的分工是这颗探针的全部意义：
     * - 只有 `盒子收到`、没有 `到达` ⇒ 事件进了盒子却没成点击；
     * - 两条都没有 ⇒ 命中测试压根没走到这一支。
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
                    // **Initial** 段取 down：这是这一层能最早读到"被吃了没有"的那一段。
                    val down = awaitFirstDown(false, PointerEventPass.Initial)
                    val flags = consumeFlags(down)
                    val boxTop = holder.topOrNull()
                    val boxHeight = holder.heightOrNull()
                    val boxWidth = holder.widthOrNull()
                    val downAt = android.os.SystemClock.uptimeMillis()
                    var last = down.position
                    var moved = 0f
                    var pressed = true
                    var consumed = false
                    var events = 0
                    while (true) {
                        val change = awaitPointerEvent(pass = PointerEventPass.Final)
                            .changes.firstOrNull { it.id == down.id } ?: break
                        events++
                        val dx = change.position.x - last.x
                        val dy = change.position.y - last.y
                        moved = max(moved, sqrt(dx * dx + dy * dy))
                        last = change.position
                        pressed = change.pressed
                        consumed = change.isConsumed
                        if (!pressed) break
                    }
                    // 抬手时再量一次这一支自己的盒子：**按下与抬起之间它自己挪了或变了尺寸**，
                    // 就是"离开命中路径 ⇒ 手势被静默取消"的直接证据。
                    val upTop = holder.topOrNull()
                    val upHeight = holder.heightOrNull()
                    val upWidth = holder.widthOrNull()
                    val inBox = last.x >= 0f && last.y >= 0f && last.x < upWidth && last.y < upHeight
                    log(
                        "$label 盒子收到 down=(${down.position.x.toInt()},${down.position.y.toInt()}) " +
                            "盒子顶=$boxTop 盒子高=$boxHeight " +
                            "触点窗口y=${if (boxTop == null) Float.NaN else boxTop + down.position.y} " +
                            "移动=${moved.toInt()}px 已抬手=${!pressed} 抬手被消费=$consumed " +
                            "按下三段 $flags 事件数=$events " +
                            "按住=${android.os.SystemClock.uptimeMillis() - downAt}ms " +
                            "抬手时盒顶=$upTop 抬手时宽高=${upWidth}x$upHeight " +
                            "盒顶变化=${(upTop ?: Float.NaN) - (boxTop ?: Float.NaN)} " +
                            "高变化=${upHeight - boxHeight} 宽变化=${upWidth - boxWidth} " +
                            "抬手local=(${last.x.toInt()},${last.y.toInt()}) 在盒内=$inBox",
                    )
                    // 指纹 (b)：盒子收到了、抬手却没人消费，而且几乎没移动 ⇒ 点击被静默丢弃。
                    if (!pressed && !consumed && moved <= TAP_MOVE_SLOP_PX) {
                        dumpDeadTap("$label 盒子收到 down、抬手未被消费、移动=${moved.toInt()}px")
                    }
                }
            }
    }

    /**
     * 内容区滚动状态的**事件驱动**读数（2026-10-05 03:xx 新增）。
     *
     * ## 为什么必须补这支
     *
     * 上一轮的量具只量了**面板**（`touchProbe` 量 top、`isAnimationRunning`、`current/target`），
     * 而**内容区自己的滚动量从来没有被量过** —— `grep scrollState sheet-probe.log` = 0 行。
     *
     * 于是 03:xx 那批日志里出现了一个当时读不懂的现象：
     * 面板 `top` 逐笔都是 247（纹丝不动）、面板偏移 = 0，
     * **但 chip 见证里的 `盒顶变化` 却在 −1799 ~ +1211 之间来回**（`高变化=0 宽变化=0` 全为 0
     * ⇒ 内容形状没变，是整块在窗口里**平移**）。
     *
     * 平移量只能来自两处之一：**面板 top**（日志已排除）或**内容区 `scrollState.value`**。
     * 既然面板没动，那就只能是内容区在**自己滚**。这一支就是把那个"隐含推论"改成"直接读数"：
     * 谁在滚、滚了多少、`maxValue` 有没有跟着变（`maxValue` 变 = 内容高被重新量过）。
     *
     * ## 触发方式与「点不动」那条教训对齐
     *
     * 不轮询：`scrollState.value` / `maxValue` / `isScrollInProgress` 都是状态
     * （foundation 里是 `mutableIntStateOf`），`snapshotFlow` 收得到每一次变化。
     * 合成一个三元组 `distinctUntilChanged`，**只有真变了才落一行**，既不漏帧也不刷屏。
     *
     * 一起报 `maxValue` 是刻意的：`consumedFromDrag` 的 `contentAtBottom` 判的是
     * `maxValue > 0 && value >= maxValue`，而 `maxValue` 会随内容重新量高而变
     * （`verticalScroll` 在内容变短时**主动夹取** `value` —— `ScrollState` 的 `value` setter
     * 里就写着 `coerceIn(0, maxValue)`）。只看 `value` 分不出「是人在滚」还是「被夹的」，
     * 而这两种在屏上都是"内容猛地一跳"。
     */
    fun onBodyScroll(what: String, value: Int, maxValue: Int, inProgress: Boolean) {
        log("$what 滚动 value=$value max=$maxValue 滚中=$inProgress")
    }

    /**
     * 面板根节点上的**只观察**触摸读数：每次按下记一笔，抬手时记移动距离、面板姿态与按下三段。
     *
     * 走 [PointerEventPass.Initial] 且**全程不消费**，所以子节点（每一枚 chip）照常收到
     * 同一个事件 —— 它不会把命中让给自己，也就不会自己造出"点不动"。
     *
     * ⚠️ `state` 形参是 2026-10-05 加的，**这是这一版最重要的一处改动**：
     * 面板姿态（`动画中=`）必须**跟每一次按下同时刻**落进同一行，而不是靠另一个 120ms 的轮询去撞。
     */
    // `SheetState` 这个**类型本身**是 experimental 的 ⇒ 只要签名里出现它，整个函数都要 opt-in
    // （上一版 `sample()` 也是这么注的；只注 `poseOf` 不够，报错会落在「把 state 传下去」那几行上）。
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun touchProbe(label: String, holder: SheetCoordsHolder, state: SheetState): Modifier {
        val view = LocalView.current
        return remember(holder, state) {
            Modifier.pointerInput(holder, state) {
                awaitEachGesture {
                    // 参数名在这个版本是位置参（见 `components/backdrop/DragGestureInspector.kt:29`
                    // 同一写法），Initial pass + requireUnconsumed=false = 纯被动观察。
                    val down = awaitFirstDown(false, PointerEventPass.Initial)
                    val flags = consumeFlags(down)
                    val topAtDown = holder.topOrNull()
                    val poseAtDown = poseOf(state)
                    var last = down.position
                    var moved = 0f
                    // 有符号的累计纵向位移（`2026-10-05 03:xx` 补）。`moved` 是无符号距离，
                    // 它回答不了"这一笔是**往下拉**还是**往上滑**"——而这两者在嵌套滚动里
                    // 走的是**两条不同的路**（`SheetDefaults.kt:643-644` 只在上滑 `delta<0` 时
                    // 面板先吃）。缺了符号，`#299 抬时[偏移=83.86]` 那种读数就只能猜方向。
                    // 约定与 Compose 一致：**向下为正**（y 轴向下）。
                    var dyTotal = 0f
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val dx = change.position.x - last.x
                        val dy = change.position.y - last.y
                        moved = max(moved, sqrt(dx * dx + dy * dy))
                        dyTotal += dy
                        last = change.position
                        if (!change.pressed) break
                    }
                    val poseAtUp = poseOf(state)
                    // dy总 的符号即手势方向：正 = 手指下移（下拉），负 = 手指上移（上滑）。
                    // view.height 一起报：面板 top 与它之比就是"盖住了几成屏"。
                    log(
                        "$label 触摸 down=(${down.position.x.toInt()},${down.position.y.toInt()}) " +
                            "移动=${moved.toInt()}px dy总=${dyTotal.toInt()} " +
                            "top按下=$topAtDown top抬起=${holder.topOrNull()} " +
                            "屏高=${view.height} 按下三段 $flags " +
                            "下时[$poseAtDown] 抬时[$poseAtUp]",
                    )
                    // 指纹 (a)：面板根在 Initial 段就已经被祖先消费。
                    if (flags.eatenByAncestor) {
                        dumpDeadTap("$label 根节点 down 在 Initial 段已被祖先消费（拖动层抢按下）")
                    }
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

    /**
     * 同一块的宽度。与 [heightOrNull] 成对用：按住期间它变了，说明**这一支自己**在
     * 被重新排版（FlowRow 换行/重组会改宽），那就是"离开命中路径"的另一半证据。
     */
    fun widthOrNull(): Float = coordinates?.let { it.size.width.toFloat() } ?: Float.NaN
}
