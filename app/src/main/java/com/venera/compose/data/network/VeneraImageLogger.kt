package com.venera.compose.data.network

import android.util.Log
import coil3.util.Logger

/**
 * Coil 的失败日志出口。
 *
 * 没有它，图片加载失败在 logcat 里是**完全静默**的：Coil 只在装了 Logger 时才写日志，
 * 而 AsyncImage 又没配 errorPainter，界面上就只剩一个空白框。上一轮「历史记录整页封面不显示」
 * 排查了半天，卡点正是没有任何一条日志能把失败定性（最终查出来是域名熔断被图片超时打开口了）。
 *
 * minLevel 定在 [Logger.Level.Error]：只放失败，不放 Coil 的成功/取消流水
 * （那些是 Info 级，会带出整屏图片 URL，刷满 logcat 还白耗性能）。
 */
object VeneraImageLogger : Logger {

    private const val Tag = "VeneraImage"

    override var minLevel: Logger.Level = Logger.Level.Error

    override fun log(tag: String, level: Logger.Level, message: String?, throwable: Throwable?) {
        val text = message ?: throwable?.message ?: return
        Log.e(Tag, "$text${throwable?.let { " | ${it.javaClass.simpleName}: ${it.message}" } ?: ""}", throwable)
    }
}
