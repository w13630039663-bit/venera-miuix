package com.venera.desktop.architecture

import com.venera.desktop.desktopEngineCall
import com.venera.engineprobe.DesktopJsHost
import com.venera.engineprobe.EngineSession
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §1 因 4 的**补验**（2026-10-21）：清单那条 ⚠️ 写的是「已改码 + 编译通过，但没重跑验证」，
 * 这一颗就是那次重跑 —— 真引擎、真并发、真 `load()`，不再是源码尺。
 *
 * ## 要复现的那次翻车
 *
 * 清单原文：接上界面后同一颗引擎有 4 个调用点，实测
 * `IllegalStateException 装载后取不到 source key: jm`（`EngineSession.kt:52`）——
 * 两趟 `load()` 交错抢走 `window['temp_source']`，回读 `.key` 时拿到 null。
 *
 * ## 为什么这一档行为尺写得出来
 *
 * `load()` 那一路**不发网络**：取 js → eval → 回读 key → 注册进 `ComicSource.sources`。
 * js 已在桌面测试 classpath 上（`processResources` 把 `app/src/main/assets` 挂进来，
 * `DesktopAssetsClasspathTest` 量的正是这一条）⇒ 离屏能起真 GraalJS、真并发、真交错。
 *
 * ⚠️ 这一颗**不证"用户那儿不再翻车"**：真人窗口那一趟的翻车是清单里记的实测读数
 * （这里不重跑真窗口 —— 主人要求后台验证）。它证的是**修法本身作不作数**。
 *
 * ## 两条配对，谁都不许单独算数
 *
 *  - ① `过那一层的四路并发抢不走`：样本数恰好 24 + 峰值并发恰好 1 + 每趟在锁内回读到
 *    **自己刚装载的那一颗** + 零抛；
 *  - ② `撤掉那一层的对照组只交读数`：同一条负载裸跑。**不断言它必须翻**
 *    （把一次竞态写死成必然 = 在别人机器上随机红，本仓「谁被撞全凭顺序运气」那一族的反面），
 *    但它把样本数钉住，并在"一次都没翻"时明写出**①是空转的**这件事。
 *    没有②，①那种绿有两种可能：锁真挡住了，或"这 24 趟本来就撞不上"。
 *
 * ## 2026-10-21 第一次跑出来的读数（本机）
 *
 * ```
 * D_串行   样本=24 峰值并发=1 抢走=0 抛=0
 * D_裸调对照 样本=24 抢走=0 抛=18
 *   site=1: IllegalStateException: 装载后取不到 source key: jm
 *   site=4: IllegalStateException: 装载后取不到 source key: copy_manga
 * ```
 *
 * ⇒ ①**不是空转**：同一份负载撤掉那一层就抛 18/24，而抛的那一句**逐字就是清单 §1 因 4
 * 记的那一条**（`EngineSession.kt:52`）；装上之后 24 趟全过、峰值并发恰好 1。
 *
 * ## ⚠️ 这一颗欠过一笔账：它把不相干的测试撞红了（同一族第二次）
 *
 * 全量回归 1130 条里 `[max text to keep 512 chars omitted]`，
 * 而那颗与引擎**毫无关系**。因果做过（不是猜）：把本文件移开跑全量 ⇒ 全绿；放回去 ⇒ 又红。
 * 成因是 ② 故意裸调抛出去的那十几笔 —— 线程把异常交给**默认未捕获处理器**，
 * 而 kotlinx 那颗测试扩展装的就是全局处理器，于是它把账记到"下一颗排到的测试"头上
 * （谁被撞全凭执行顺序运气，正是本仓记过的那一族）。
 *
 * 修法是**收在本颗里**，而不是删掉 ② 那格证据：两颗都跑在自己造的线程池上，
 * 每枚线程带自己的 `UncaughtExceptionHandler`（线程自己的处理器优先于默认的），
 * 逃逸的那笔一律**并进读数队列**—— 既不让它漏给别人，也不悄悄吞掉。
 * 所以 `D_*` 里若出现 `逃逸到线程处理器` 那一句，就是它真的堵到过东西。
 */
class DesktopEngineCallConcurrencyTest {

    private fun realEngine(): EngineSession {
        val dir = Files.createTempDirectory("enginecall-").toFile()
        return EngineSession(DesktopJsHost(dir, dir, null, emptyList()), dir)
    }

    /** 两颗都在包内的源；`load()` 不发网络，所以两趟交错只由那颗锁决定先后。 */
    private val sourceKeys = listOf("jm", "copy_manga")

    private val sites = 4
    private val rounds = 6
    private val totalSamples get() = sites * rounds

    /**
     * 自己造线程池，而不是 `newFixedThreadPoolContext`：这里要的是**每枚线程带自己的
     * 未捕获处理器**（kotlinx 那颗拿不到这个口子）。逃逸进来的每一笔都交给 `sink`，
     * 由调用处并进读数队列 —— 收在本颗里，不漏给下一颗测试。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun isolatedPool(name: String, sink: (String) -> Unit): Pair<ExecutorService, CoroutineDispatcher> {
        val factory = ThreadFactory { r ->
            Thread(r, name).apply {
                isDaemon = true
                setUncaughtExceptionHandler { t, e ->
                    sink("逃逸到线程处理器 ${t.name}: ${e.javaClass.simpleName}: ${e.message?.take(120)}")
                }
            }
        }
        val pool = Executors.newFixedThreadPool(sites, factory)
        return pool to pool.asCoroutineDispatcher()
    }

    /**
     * ① 四路并发都过那一层：抢不走状态、不重叠、不抛。
     *
     * `block` 不是 suspend（`desktopEngineCall` 自己 `withContext(Dispatchers.IO)` 再跑它），
     * 所以拉开窗口用 `Thread.sleep` —— 与 `DesktopJsHost.evaluateAsync` 那枚
     * 「发出去 + `Thread.sleep(2)` 轮询」是同一个线程形状，不是本批新造的写法。
     */
    @Test
    fun 四路并发过那一层之后抢不走同一颗引擎的状态() = runBlocking {
        val leaked = ConcurrentLinkedQueue<String>()
        val (pool, dispatcher) = isolatedPool("enginecall-guarded") { leaked += it }
        val engine = realEngine()
        try {
            val inside = AtomicInteger(0)
            val peak = AtomicInteger(0)
            val wrongKey = mutableListOf<String>()
            val failures = ConcurrentLinkedQueue<String>()
            val done = AtomicInteger(0)

            val jobs = (1..sites).map { site ->
                async(dispatcher) {
                    (1..rounds).map { round ->
                        val asked = sourceKeys[(site + round) % sourceKeys.size]
                        val got = desktopEngineCall(engine) {
                            val live = inside.incrementAndGet()
                            peak.updateAndGet { if (it > live) it else live }
                            try {
                                engine.load(asked)
                                // 在**同一块里**回读：锁若没作用，这一句会被另一趟的 load 换掉。
                                if (engine.key != asked) wrongKey += "site=$site round=$round 要=$asked 得=${engine.key}"
                                Thread.sleep(4) // 把窗口拉开：没有锁的时候这一点就够被抢。
                            } finally {
                                inside.decrementAndGet()
                            }
                        }
                        if (got.isFailure) {
                            val t = got.exceptionOrNull()
                            failures += "site=$site round=$round: ${t?.javaClass?.simpleName}: ${t?.message?.take(120)}"
                        }
                        done.incrementAndGet()
                    }
                }
            }
            jobs.awaitAll()

            // 先钉样本数：24 趟只跑了 20 趟的话，下面那三条的绿是"没撞过"换来的。
            assertEquals("过那一层这一路的样本数不对：", totalSamples, done.get())
            assertEquals("同一颗引擎上同时跑进过多于一段（那颗锁没串住真活儿）：", 1, peak.get())
            assertTrue("锁内回读到的 key 与自己刚 load 的那颗不同（实测那种被抢走）：$wrongKey", wrongKey.isEmpty())
            assertTrue(
                "并发这一路抛了 —— 清单里那句『已改码但没重跑验证』到这一颗才算验过：\n" +
                    failures.joinToString("\n"),
                failures.isEmpty(),
            )
            // 这一条钉"堵住了"：有任何一笔逃逸到线程处理器都会被记进 leaked 并在这里念出来，
            // 而不是漏到全局处理器去撞红下一颗不相干的测试。
            assertTrue(
                "①这一路有异常逃逸到线程处理器（读数记着，本颗不收在外面）：$leaked",
                leaked.isEmpty(),
            )
            println("D_串行 样本=${done.get()} 峰值并发=${peak.get()} 抢走=${wrongKey.size} 抛=${failures.size} 逃逸=${leaked.size}")
        } finally {
            runCatching { engine.close() }
            pool.shutdownNow()
        }
    }

    /**
     * ② 撤掉那一层的对照组：**只交读数，不假装必然**。
     *
     * 裸跑同一条负载（`load()` → `key`，不走 `desktopEngineCall`）。这一颗不断言翻，
     * 只钉两件事：样本数完整（否则①那条对照的读数是残缺的），以及**没翻时出声**。
     * ①的绿配上这里"翻了 N 次"才算有牙；配上"一次都没翻"就只是一句待查的读数。
     */
    @Test
    fun 撤掉那一层的对照组把翻没翻念出来而不把竞态写成必然() = runBlocking {
        val wrong = ConcurrentLinkedQueue<String>()
        val threw = ConcurrentLinkedQueue<String>()
        val (pool, dispatcher) = isolatedPool("enginecall-naked") { threw += it }
        val engine = realEngine()
        try {
            val done = AtomicInteger(0)
            val jobs = (1..sites).map { site ->
                async(dispatcher) {
                    (1..rounds).map { round ->
                        val asked = sourceKeys[(site + round) % sourceKeys.size]
                        val outcome = runCatching {
                            withContext(Dispatchers.IO) {
                                engine.load(asked)
                                Thread.sleep(4)
                                engine.key
                            }
                        }
                        outcome.fold(
                            onSuccess = { if (it != asked) wrong += "site=$site round=$round 要=$asked 得=$it" },
                            onFailure = { threw += "site=$site: ${it.javaClass.simpleName}: ${it.message?.take(100)}" },
                        )
                        done.incrementAndGet()
                    }
                }
            }
            jobs.awaitAll()

            assertEquals("裸调这一路的样本数不对（那 D_裸调对照 那句读数就是残缺的）：", totalSamples, done.get())
            val nakedFailures = threw.count { it.startsWith("site=") }
            val escaped = threw.count { it.startsWith("逃逸到线程处理器") }
            println("D_裸调对照 样本=${done.get()} 抢走=${wrong.size} 抛=$nakedFailures 收进本颗=$escaped")
            threw.take(3).forEach { println("D_裸调对照   $it") }
            if (wrong.isEmpty() && nakedFailures == 0) {
                println(
                    "D_裸调对照 这一趟一次都没翻 ⇒ 上面①那三条在此刻**空转**（窗口没拉开），" +
                        "不能读成『修法治好了』；要拉开窗口或换真机读数才算数。",
                )
            }
        } finally {
            runCatching { engine.close() }
            pool.shutdownNow()
        }
    }
}
