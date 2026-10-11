package com.venera.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.composefluent.FluentTheme
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import javax.imageio.ImageIO
import kotlinx.coroutines.delay
import java.nio.file.Path
import java.nio.file.Paths

/**
 * S0-6 改道候选 a 的可行性探针：**不引 window-styler，自己用 FFM（`java.lang.foreign`）调 DWM**。
 *
 * 量四件事，全打 stdout：
 * 1. 能不能拿到 Compose 窗口背后那颗 HWND（走 `sun.awt.AWTAccessor`，运行期要 `--add-exports`）；
 * 2. 设 `DWMWA_SYSTEMBACKDROP_TYPE` = Mica 的 HRESULT；
 * 3. **回读**该属性 —— 设上≠系统认账，回读值才算；
 * 4. 截图两帧（设前/设后）落 `_qa/`，Mica 有没有真透出来由图判定，前三条全绿不代表画面变了。
 */
/** 标题必须是纯 ASCII：本探针用 `FindWindowA`（ANSI 版）拿 HWND */
private const val TITLE = "Venera S0-6a FFM Mica probe"

/**
 * 属性编号先记一次纠错：`DWMWA_SYSTEMBACKDROP_TYPE` 是 **38**，
 * 我第一版写的 33 其实是 `DWMWA_WINDOW_CORNER_PREFERENCE`。以回读为准。
 */
private const val ATTR_BACKDROP = 38
private const val ATTR_IMMERSIVE_DARK = 20

/** DWM 材质的前提条件之一：窗口不能有重定向位图（AWT/skiko 默认是有的） */
private const val GWL_EXSTYLE = -20
private const val WS_EX_NOREDIRECTIONBITMAP = 0x00200000L
private const val SWP_NOSIZE = 0x0001
private const val SWP_NOMOVE = 0x0002
private const val SWP_NOZORDER = 0x0004
private const val SWP_FRAMECHANGED = 0x0020

fun main() {
    println("S06a_环境 os.version=${System.getProperty("os.version")} java=${System.getProperty("java.version")} vm=${System.getProperty("java.vm.vendor")}")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = TITLE,
            state = rememberWindowState(width = 620.dp, height = 420.dp),
            // 实测：`transparent=true` 是 CMP 1.12 的**公开参数**（不必反射 skiko 内部），
            // 但它硬要求 `undecorated=true` —— 否则组合期直接抛
            // `IllegalStateException: Transparent window should be undecorated!`
            transparent = true,
            undecorated = true,
        ) {
            FluentTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        // 这一版**不涂任何底色**：截图里的像素就是 DWM 画出来的东西本身，
                        // 免得半透明叠加层把"平不平"这个判据污染掉（上一版 0x80 底就是这么糊过去的）
                        .padding(24.dp),
                ) {
                    BasicText("S0-6a：DWM backdrop 自接探针", style = androidx.compose.ui.text.TextStyle(color = Color.White))
                    LaunchedEffect(Unit) {
                        delay(1800) // 等窗口真的映射出来
                        val hwnd = findHwnd(TITLE)
                        println("S06a_HWND=${if (hwnd == null) "null（AWTAccessor/WComponentPeer 这条路不通）" else "0x${hwnd.toString(16)}"}")
                        if (hwnd != null) {
                            // 先取一笔**窗口之外的桌面**当参照：Mica 的色调是从壁纸算出来的，
                            // 没有这条参照就没法判断"平的那片"到底是材质还是 fallback
                            captureOutside(Paths.get("_qa/s06a-wallpaper.png"), hwnd)
                            capture(Paths.get("_qa/s06a-0-none.png"), hwnd)
                            setAttr(hwnd, ATTR_IMMERSIVE_DARK, 1)
                            extendFrame(hwnd)
                            // backdrop 四档扫一遍：AUTO=0 / NONE=1 / MICA=2 / ACRYLIC=3 / TABBED=4。
                            // 判据不是"看着像不像"，而是**Acrylic 会实时模糊桌面内容** ——
                            // 它若画出高方差画面，就说明 backdrop 通道是活的，此时 Mica 的"平"才是真材质；
                            // 它若和 Mica 一样平，说明 DWM 根本没在画，我们看到的只是 fallback。
                            for ((name, v) in listOf("none" to 1, "mica" to 2, "acrylic" to 3, "tabbed" to 4)) {
                                val hr = setAttr(hwnd, ATTR_BACKDROP, v)
                                val back = getAttr(hwnd, ATTR_BACKDROP)
                                delay(900)
                                capture(Paths.get("_qa/s06a-A$v-$name.png"), hwnd, tag = "A档 $v-$name set=$hr 回读=$back")
                            }
                            // A 档读数：三档材质全是**纯色填充**（mica 与 tabbed 逐字节相同、方差≈10 且
                            // 完全不带壁纸色偏）⇒ 材质通道没启用。Win32 上 DWM 材质要求窗口**没有重定向位图**，
                            // 所以 B 档补上 `WS_EX_NOREDIRECTIONBITMAP` 再扫一遍。
                            addNoRedirectionBitmap(hwnd)
                            for ((name, v) in listOf("mica" to 2, "acrylic" to 3)) {
                                val hr = setAttr(hwnd, ATTR_BACKDROP, v)
                                delay(900)
                                capture(Paths.get("_qa/s06a-B$v-$name.png"), hwnd, tag = "B档 $v-$name set=$hr")
                            }
                        }
                        delay(300)
                        kotlin.system.exitProcess(0)
                    }
                }
            }
        }
    }
}

/**
 * 拿 HWND 的两条路，本探针走**第二条**：
 * 1. ~~`sun.awt.AWTAccessor` → `WComponentPeer.getHashCode()`~~ —— 实测**要走不通**：
 *    accessor 的运行期类是 `java.awt` 包里的匿名实现（`java.awt.Component$1`），
 *    该类所在的包 java.desktop **不导出**，反射直接 `IllegalAccessException`；
 *    而 `sun.awt.ComponentAccessor` 这个接口名在 JDK 21 里**已不存在**（`ClassNotFoundException`）。
 *    要修得靠 `--add-opens java.desktop/java.awt=ALL-UNNAMED`，且绑死 JDK 内部结构。
 * 2. **`user32.FindWindowW(null, title)`** —— 零 JDK 内部依赖、零 add-opens，代价是标题要唯一。
 *    ⚠️ 必须是 **W（UTF-16）** 版：`FindWindowA` 走 ANSI 码页，**带中文的标题匹配不到**，
 *    实测返回 0 —— 下游若拿 AWT bounds 兜底就会把桌面壁纸当成窗口截（本轮废掉三张图）。
 */
internal fun findHwnd(title: String): Long? {
    println("S06a_AWT Frame 标题=${Frame.getFrames().map { it.title }}")
    return runCatching {
        Arena.ofConfined().use { arena ->
            val fn = SymbolLookup.libraryLookup("user32", arena).find("FindWindowW").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                )
            }.orElse(null) ?: return null
            // JDK 21 的 SegmentAllocator 既没有 `allocateUtf16String`，`allocateFrom(JAVA_CHAR, s)`
            // 也还没到位 —— 手工分配 UTF-16 缓冲区，跨 21/22/23 都能编
            val wTitle = arena.allocate((title.length + 1) * 2L)
            title.forEachIndexed { i, ch -> wTitle.set(ValueLayout.JAVA_CHAR, i * 2L, ch) }
            wTitle.set(ValueLayout.JAVA_CHAR, title.length * 2L, Char(0))
            val hwnd = (fn.invokeExact(MemorySegment.NULL, wTitle) as MemorySegment).address()
            if (hwnd == 0L) null else hwnd
        }
    }.getOrElse {
        println("S06a_HWND FAIL ${it::class.java.simpleName}: ${it.message?.take(200)}")
        null
    }
}

/**
 * 给窗口加上 `WS_EX_NOREDIRECTIONBITMAP` 并 `SetWindowPos(SWP_FRAMECHANGED)` 生效，
 * 返回**回读到的新扩展样式**（-1 = 这条 FFM 通道本身没打通）。
 */
private fun addNoRedirectionBitmap(hwnd: Long): Long = runCatching {
    Arena.ofConfined().use { arena ->
        val u = SymbolLookup.libraryLookup("user32", arena)
        val ptr = ValueLayout.ADDRESS
        val get = u.find("GetWindowLongPtrW").map {
            Linker.nativeLinker().downcallHandle(it, FunctionDescriptor.of(ptr, ptr, ValueLayout.JAVA_INT))
        }.orElse(null) ?: return -1
        val set = u.find("SetWindowLongPtrW").map {
            Linker.nativeLinker().downcallHandle(it, FunctionDescriptor.of(ptr, ptr, ValueLayout.JAVA_INT, ptr))
        }.orElse(null) ?: return -1
        val pos = u.find("SetWindowPos").map {
            Linker.nativeLinker().downcallHandle(
                it,
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ptr, ptr,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                ),
            )
        }.orElse(null) ?: return -1
        val h = MemorySegment.ofAddress(hwnd)
        val old = (get.invokeExact(h, GWL_EXSTYLE) as MemorySegment).address()
        // `invokeExact` 的**静态返回类型必须与句柄一致**：直接丢弃返回值会编译成 void，
        // 运行期抛 WrongMethodTypeException（B 档第一版就这么白跑了一轮）
        val prev = set.invokeExact(h, GWL_EXSTYLE, MemorySegment.ofAddress(old or WS_EX_NOREDIRECTIONBITMAP)) as MemorySegment
        println("S06a_SetWindowLongPtrW 返回旧值=0x${prev.address().toString(16)}")
        val moved = pos.invokeExact(
            h, MemorySegment.NULL, 0, 0, 0, 0,
            SWP_NOSIZE or SWP_NOMOVE or SWP_NOZORDER or SWP_FRAMECHANGED,
        ) as Int
        println("S06a_SetWindowPos=$moved（非 0 才算生效）")
        val back = (get.invokeExact(h, GWL_EXSTYLE) as MemorySegment).address()
        println("S06a_exstyle old=0x${old.toString(16)} 回读=0x${back.toString(16)} 位已上=${(back and WS_EX_NOREDIRECTIONBITMAP) != 0L}")
        back
    }
}.getOrElse {
    println("S06a_exstyle FAIL ${it::class.java.simpleName}: ${it.message?.take(200)}")
    -2
}

private fun dwmFn(lookup: SymbolLookup, name: String): MethodHandle? =    lookup.find(name).map {
        Linker.nativeLinker().downcallHandle(
            it,
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ),
        )
    }.orElse(null)

private fun setAttr(hwnd: Long, attr: Int, value: Int): Int = runCatching {    Arena.ofConfined().use { arena ->
        val fn = dwmFn(SymbolLookup.libraryLookup("dwmapi", arena), "DwmSetWindowAttribute")
            ?: return -99
        val pv = arena.allocate(ValueLayout.JAVA_INT, value)
        fn.invokeExact(MemorySegment.ofAddress(hwnd), attr, pv, ValueLayout.JAVA_INT.byteSize().toInt()) as Int
    }
}.getOrElse {
    println("S06a_set($attr) FAIL ${it::class.java.simpleName}: ${it.message?.take(200)}")
    -98
}

/**
 * `DwmExtendFrameIntoClientArea(hwnd, MARGINS{-1,-1,-1,-1})` —— 把玻璃帧铺满客户区。
 * 这是"内容不透明 ⇒ Mica 看不见"那一步的候选解药（window-styler 当年用反射做的正是等价的事）。
 */
private fun extendFrame(hwnd: Long): Int = runCatching {
    Arena.ofConfined().use { arena ->
        val fn = SymbolLookup.libraryLookup("dwmapi", arena).find("DwmExtendFrameIntoClientArea").map {
            Linker.nativeLinker().downcallHandle(
                it,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            )
        }.orElse(null) ?: return -99
        val margins = arena.allocate(16L)
        for (i in 0 until 4) margins.set(ValueLayout.JAVA_INT, i * 4L, -1)
        fn.invokeExact(MemorySegment.ofAddress(hwnd), margins) as Int
    }
}.getOrElse {
    println("S06a_extendFrame FAIL ${it::class.java.simpleName}: ${it.message?.take(200)}")
    -98
}

private fun getAttr(hwnd: Long, attr: Int): Int = runCatching {
    Arena.ofConfined().use { arena ->
        val fn = dwmFn(SymbolLookup.libraryLookup("dwmapi", arena), "DwmGetWindowAttribute")
            ?: return -99
        val pv = arena.allocate(ValueLayout.JAVA_INT, 0)
        val hr = fn.invokeExact(
            MemorySegment.ofAddress(hwnd), attr, pv, ValueLayout.JAVA_INT.byteSize().toInt(),
        ) as Int
        val v = pv.get(ValueLayout.JAVA_INT, 0)
        println("S06a_get($attr) HRESULT=$hr 值=$v")
        if (hr != 0) hr else v
    }
}.getOrElse {
    println("S06a_get($attr) FAIL ${it::class.java.simpleName}: ${it.message?.take(200)}")
    -98
}

private fun capture(out: Path, hwnd: Long, tag: String = "") {
    val rect = runCatching { screenRectOf(hwnd) }.getOrElse { null }
    if (rect == null) {
        println("S06a_帧[$tag] FAIL 拿不到窗口矩形")
        return
    }
    captureRect(out, rect, tag)
}

/** 窗口之外、同尺寸的一笔桌面（右侧优先，放不下就左侧），当 Mica 的色调参照 */
private fun captureOutside(out: Path, hwnd: Long) {
    val r = runCatching { screenRectOf(hwnd) }.getOrElse { null } ?: return
    val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
    val x = (r.x + r.width + 20).coerceAtMost(screen.width - r.width - 5).coerceAtLeast(5)
    val y = r.y.coerceAtMost(screen.height - r.height - 5).coerceAtLeast(5)
    captureRect(out, Rectangle(x, y, r.width, r.height), "桌面参照")
}

private fun captureRect(out: Path, rect: Rectangle, tag: String) {
    runCatching {
        val img: BufferedImage = Robot().createScreenCapture(rect)
        ImageIO.write(img, "png", out.toFile())
        var sr = 0L; var sg = 0L; var sb = 0L
        var s1 = 0.0; var s2 = 0.0
        val step = 3
        var n = 0
        for (y in 0 until img.height step step) for (x in 0 until img.width step step) {
            val p = img.getRGB(x, y)
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            sr += r; sg += g; sb += b
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            s1 += lum; s2 += lum * lum; n++
        }
        val mean = s1 / n
        val sd = kotlin.math.sqrt((s2 / n - mean * mean).coerceAtLeast(0.0))
        println(
            "S06a_帧[$tag] ${out.fileName} ${rect.width}x${rect.height} " +
                "均值RGB=(${sr / n},${sg / n},${sb / n}) 亮度=$mean 标准差=${"%.1f".format(sd)} " +
                "png字节=${out.toFile().length()}"
        )
    }.onFailure { println("S06a_截图 FAIL ${it::class.java.simpleName}: ${it.message?.take(140)}") }
}

internal fun screenRectOf(hwnd: Long): Rectangle? = runCatching {
    Arena.ofConfined().use { arena ->
        val fn = SymbolLookup.libraryLookup("user32", arena).find("GetWindowRect").map {
            Linker.nativeLinker().downcallHandle(
                it,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            )
        }.orElse(null) ?: return null
        // RECT = 4 个 LONG(32 位)：必须按**字节数**分配，别用 allocate(JAVA_INT, 16) —— 那会被当成 int 初值
        val rect = arena.allocate(16L)
        val ok = fn.invokeExact(MemorySegment.ofAddress(hwnd), rect) as Int
        if (ok == 0) return null
        val l = rect.get(ValueLayout.JAVA_INT, 0)
        val t = rect.get(ValueLayout.JAVA_INT, 4)
        val r = rect.get(ValueLayout.JAVA_INT, 8)
        val b = rect.get(ValueLayout.JAVA_INT, 12)
        Rectangle(l.toInt(), t.toInt(), (r - l).toInt(), (b - t).toInt())
    }
}.getOrElse {
    println("S06a_矩形 FAIL ${it::class.java.simpleName}: ${it.message?.take(140)}")
    null
}

/**
 * 该点最前面的窗口是不是我们这扇（或其**祖先链上的任一子窗**）。
 * **锁屏 / 切到别的虚拟桌面时，`Robot` 照样能截出一张纯壁纸**，而 Win32 的 visible 位仍是 true
 * —— 只有这一招能识破，所以自截图前必须先过这道闸。
 *
 * ⚠️ 这一颗原来**只走 `GetParent` 一层**：CMP 桌面窗的画布嵌得更深
 * （Frame → 画布 → Skia 面板那种形状），于是"其实是我们这扇窗"会被判成"不是"，
 * **每一张取证图都会被这道闸拒掉**（2026-10-04 实测：抬成 topmost 之后仍然拒）。
 * 现在沿父链往上走，上限 [ANCESTOR_WALK_LIMIT] 层（防环；真到上限也没到本窗就是不在）。
 */
internal fun isPointOnWindow(hwnd: Long, x: Int, y: Int): Boolean {
    val at = windowAtPoint(x, y) ?: return false
    if (at == hwnd) return true
    var current = at
    repeat(ANCESTOR_WALK_LIMIT) {
        current = windowParent(current) ?: return false
        if (current == hwnd) return true
    }
    return false
}

/** 父链走几层就罢：环与畸形形状都按"不在本窗"处理，不硬凑成"在" */
private const val ANCESTOR_WALK_LIMIT = 6

/** `WS_EX_TOPMOST`：抬窗那颗位。回读它才算数，"我调用了 SetWindowPos" 不算 */
private const val WS_EX_TOPMOST = 0x8L

private const val HWND_TOPMOST_VALUE = -1L
private const val HWND_NOTOPMOST_VALUE = -2L
// SWP_NOSIZE / SWP_NOMOVE 这颗文件顶部就已声明（`addNoRedirectionBitmap` 在用），不重复登记
private const val SWP_NOACTIVATE = 0x10

/**
 * 把这扇窗抬成（或落回）topmost，返回**一句带证据的读数**。
 *
 * 为什么不能用 AWT 的 `setAlwaysOnTop` 就算数（2026-10-04 实测）：AWT 那头报
 * `isAlwaysOnTop=true 支持=true visible=true`，而 `WindowFromPoint` 仍认用户的 Chrome 窗 ——
 * `SunAwtFrame` 上那一层样式**没落到 Win32**。所以这里直接 `SetWindowPos(HWND_TOPMOST)`，
 * 并且**回读 `GWL_EXSTYLE` 的 0x8 位**写进读数：抬窗这件事要么有位要么没有，不接受"我调用了"。
 *
 * 只带 `SWP_NOSIZE|SWP_NOMOVE|SWP_NOACTIVATE`：取证不需要改尺寸位置，而**不抢焦点**是刻意的 ——
 * 抢前台已经是用户批准的例外，再顺手抢输入焦点就是另一件事了。
 */
internal fun setTopmost(hwnd: Long?, on: Boolean): String {
    if (hwnd == null) return "hwnd 取不到，未抬"
    return runCatching {
        Arena.ofConfined().use { arena ->
            val u = SymbolLookup.libraryLookup("user32", arena)
            val get = u.find("GetWindowLongPtrW").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                )
            }.orElse(null) ?: return "GetWindowLongPtrW 取不到"
            val pos = u.find("SetWindowPos").map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                    ),
                )
            }.orElse(null) ?: return "SetWindowPos 取不到"
            val h = MemorySegment.ofAddress(hwnd)
            val old = (get.invokeExact(h, GWL_EXSTYLE) as MemorySegment).address()
            val moved = pos.invokeExact(
                h,
                MemorySegment.ofAddress(if (on) HWND_TOPMOST_VALUE else HWND_NOTOPMOST_VALUE),
                0, 0, 0, 0,
                SWP_NOSIZE or SWP_NOMOVE or SWP_NOACTIVATE,
            ) as Int
            val back = (get.invokeExact(h, GWL_EXSTYLE) as MemorySegment).address()
            "on=$on SetWindowPos=$moved（非 0 才算生效）位已上=${(back and WS_EX_TOPMOST) != 0L} " +
                "exstyle 0x${old.toString(16)}→0x${back.toString(16)}"
        }
    }.getOrElse { "抬窗抛了 ${it::class.java.simpleName}: ${it.message?.take(100)}" }
}

/** `WindowFromPoint`：那点最前面的那扇窗（返回 0 或取不到都算 `null`） */
internal fun windowAtPoint(x: Int, y: Int): Long? = runCatching {
    Arena.ofConfined().use { arena ->
        val fn = SymbolLookup.libraryLookup("user32", arena).find("WindowFromPoint").map {
            Linker.nativeLinker().downcallHandle(
                it,
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
            )
        }.orElse(null) ?: return null
        val hwnd = (fn.invokeExact(x, y) as MemorySegment).address()
        if (hwnd == 0L) null else hwnd
    }
}.getOrElse {
    println("S06a_WindowFromPoint FAIL ${it::class.java.simpleName}: ${it.message?.take(140)}")
    null
}

/** `GetParent`：直接父窗（顶层窗没有父窗，取不到与没有都回 `null`） */
internal fun windowParent(hwnd: Long): Long? = runCatching {
    Arena.ofConfined().use { arena ->
        val fn = SymbolLookup.libraryLookup("user32", arena).find("GetParent").map {
            Linker.nativeLinker().downcallHandle(
                it,
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            )
        }.orElse(null) ?: return null
        val parent = (fn.invokeExact(MemorySegment.ofAddress(hwnd)) as MemorySegment).address()
        if (parent == 0L) null else parent
    }
}.getOrElse {
    println("S06a_GetParent FAIL ${it::class.java.simpleName}: ${it.message?.take(140)}")
    null
}

/**
 * 那扇窗的**标题与类名**，只为诊断。原来拒图那句写的是"多半已锁屏/切到别的虚拟桌面"—— 那是**猜**的；
 * 取证时要的是"中心点到底被谁占着"。取不到就照实说取不到，不拿"未知"冒充"我们这扇"。
 */
internal fun windowDescribe(hwnd: Long): String = runCatching {
    Arena.ofConfined().use { arena ->
        val u = SymbolLookup.libraryLookup("user32", arena)
        // arena.allocate 是零填充的，所以 GetWindowTextW 没写满时 getUtf16String 也会在 NUL 处收住
        val buf = arena.allocate(2L * 512)
        fun text(of: String): String {
            val fn = u.find(of).map {
                Linker.nativeLinker().downcallHandle(
                    it,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                    ),
                )
            }.orElse(null) ?: return "?"
            val n = fn.invokeExact(MemorySegment.ofAddress(hwnd), buf, 512) as Int
            return if (n <= 0) {
                ""
            } else {
                // 手工按 UTF-16 读：`getUtf16String` 在这套编译 JDK 上不解析（实测报 Unresolved reference），
                // 而 `get(ValueLayout.JAVA_CHAR, offset)` 是各版本都有的最低公分母
                val sb = StringBuilder(n)
                var i = 0
                while (i < n) {
                    val ch = buf.get(ValueLayout.JAVA_CHAR, i * 2L)
                    if (ch.code == 0) break
                    sb.append(ch)
                    i++
                }
                sb.toString()
            }
        }
        "标题「${text("GetWindowTextW")}」类名「${text("GetClassNameW")}」"
    }
}.getOrElse { "描述取不到（${it::class.java.simpleName}）" }
