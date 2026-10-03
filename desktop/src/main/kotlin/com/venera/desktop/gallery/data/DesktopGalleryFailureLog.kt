package com.venera.desktop.gallery.data

import com.venera.desktop.platform.DesktopLogger

/**
 * 桌面侧的"同一件事只说一次"。
 *
 * 存在的理由：桌面这边有几条降级路是**每次都命中**的 —— 最典型的是标签词典没随包分发，
 * 墙上每一排卡片都会问一次。照 Android 侧那样每次打一行 logcat 还算正常（那边 logcat 有滚动与过滤），
 * 桌面这边是 stdout：一次会话刷几千行，"这条本来要人说出来的缺席"就混成了噪声，
 * 而它的反面（干脆不打）就是本仓最忌的静默交错。所以：**首次出声，后续只计数**，
 * 计数在下一句里带出来，缺席的规模不会被藏掉。
 */
object DesktopGalleryFailureLog {

    private val seen = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>()

    /** 第一次原样打；之后再命中只在那句尾带上"（已第 N 次）"。 */
    fun warn(tag: String, message: String) {
        val count = seen.getOrPut(message) { java.util.concurrent.atomic.AtomicLong() }.incrementAndGet()
        DesktopLogger.warn(tag, if (count == 1L) message else "$message（同一句已第 $count 次）")
    }

    /** 用例与集成核对用：清掉"已经说过"的簿记。 */
    fun reset() {
        seen.clear()
    }
}
