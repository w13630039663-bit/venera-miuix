package com.venera.compose

import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * 冷启动归因打点（不参与任何业务判定，也不改变任何行为）。
 *
 * 读数口径：`cost` 是这一段自身的毫秒数，`at` 是它起点距 [anchor] 的毫秒数，
 * `thread` 用来区分"主线程同步等着"和"后台并发抢 CPU"——这两类的修法完全不同。
 */
object StartupTrace {

    const val TAG = "VeneraStartup"

    @Volatile
    private var anchorMs = -1L

    /** 把 0 点定在冷启动链路的最入口（Application.onCreate 第一行）。 */
    fun anchor() {
        if (anchorMs < 0L) anchorMs = SystemClock.elapsedRealtime()
    }

    fun mark(label: String) {
        record(label, 0L, SystemClock.elapsedRealtime())
    }

    /** 起点已在外部取过时间戳时用这一条（组合期里不想再多取一次 elapsedRealtime）。 */
    fun recordElapsed(label: String, startedMs: Long) {
        record(label, SystemClock.elapsedRealtime() - startedMs, startedMs)
    }

    /**
     * 只在**第一次**调用时打一行：用来确认"这段到底有没有跑在首帧之前的主线程上"，
     * 而不用把每帧都调用的判定方法变成日志刷屏源。
     */
    fun once(label: String) {
        if (seen.add(label)) record(label, 0L, SystemClock.elapsedRealtime())
    }

    private val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun <R> timed(label: String, block: () -> R): R {
        val started = SystemClock.elapsedRealtime()
        try {
            return block()
        } finally {
            val now = SystemClock.elapsedRealtime()
            record(label, now - started, started)
        }
    }

    private fun record(label: String, costMs: Long, startedMs: Long) {
        val offset = if (anchorMs >= 0L) startedMs - anchorMs else 0L
        val onMain = Looper.myLooper() != null && Looper.myLooper() == Looper.getMainLooper()
        Log.i(TAG, "$label cost=${costMs}ms at=+${offset}ms thread=${if (onMain) "main" else "bg"}")
    }
}
