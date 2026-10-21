package com.venera.desktop

import com.venera.desktop.gallery.data.desktopEngineLockKey
import com.venera.desktop.gallery.data.withDesktopEngineLock
import com.venera.engineprobe.EngineSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 所有「问引擎」的调用点都必须过这一层。
 *
 * ## 为什么必须有这颗（2026-10-21，端到端量出来的）
 *
 * `DesktopJsHost` **零同步** —— 这不是推测：`withDesktopEngineLock` 那段注释里写着
 * 「`evaluateAsync` 是发出去 + `Thread.sleep(2)` 轮询，全文件没有一枚 `synchronized`/`Mutex`」，
 * 而 `EngineSession` 自己也没有任何同步（刚查过：零处 `synchronized`/`Mutex`/`withLock`）。
 *
 * 在这批界面接上之前，桌面只有一颗引擎的**一个**使用者（取页那一条，它自己上了锁）。
 * 接上探索 / 详情 / 阅读器 / 搜索之后，同一颗引擎同时有 4 个调用点，
 * 于是实测翻车：探索屏报 `IllegalStateException 装载后取不到 source key: jm`
 * （`EngineSession.kt:52`）—— 两趟 `load()` 交错，`window['temp_source']` 被对方抢走，
 * 回读 `.key` 时拿到 null。
 *
 * ## 为什么锁键公式不在这里重写
 *
 * 复用 [desktopEngineLockKey]（与取页那一条同一个函数）。
 * 在这里再写一遍 `identityHashCode` 就是"两份判据一定会漂"的形状 ——
 * 漂了之后的表现是"取页上了锁、取章节没上锁"，正是要避免的那件事。
 *
 * ## 为什么 `withContext(IO)`
 *
 * `EngineSession` 的每一跳都是阻塞的（JS 求值 + 轮询）。
 * 不切走会把组合线程占住，屏直接卡死。
 *
 * ## 为什么交回 [Result] 而不是自己抛
 *
 * 四个调用点都要"失败就把成因念出来"。在这里统一 `runCatching`，
 * 少一处忘了包就是一颗静默的空屏。
 */
internal suspend fun <T> desktopEngineCall(
    engine: EngineSession?,
    block: () -> T,
): Result<T> {
    if (engine == null) {
        return Result.failure(IllegalStateException("引擎没起来"))
    }
    return withDesktopEngineLock(desktopEngineLockKey(engine)) {
        withContext(Dispatchers.IO) { runCatching { block() } }
    }
}
