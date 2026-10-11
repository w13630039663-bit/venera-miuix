package com.venera.desktop.gallery.data

import com.venera.desktop.gallery.ui.DesktopSourceTree
import com.venera.engineprobe.DesktopJsHost
import com.venera.engineprobe.EngineSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * 批 81 · 阅读那一屏的页取数件（纯判据）。
 *
 * ## ① 为什么"缓存键里不许掺引擎身份"是本批最要紧的一条
 *
 * 一话的页要真去问源站（GraalJS + 网络），单页解码后按 800×1200 算是 3.8 MB 位图。Coil 的内存缓存
 * 吃的就是 `Keyer` 算出的那枚键 —— 而 `EngineSession` 没有 `equals`，默认按对象身份。
 * 于是"把 session 掺进键里"的形态是：换一颗引擎（重取一次、或换域再回来）⇒ 整话的键全变 ⇒
 * 一份缓存都用上，屏上看起来像"缓存是假的"。这条判据钉的是**那一行的入参表**，不是它今天算得对不对。
 *
 * ## ② 为什么"没有引擎就不取"要有判据
 *
 * 引擎还没装载时那一格该走成错误态（屏上念"这一页没取成"）。反过来，若 fetcher 硬造一颗再到 fetch
 * 里抛，形态上是"转了一下才红"，看起来像网络坏了 —— 而真相是没装载。
 * 这里钉的是那颗**谓词**（可纯跑），并另由接线判据钉工厂真的吃它。
 *
 * ## ③ 为什么锁要"按键各自一份"
 *
 * `DesktopJsHost` 零同步（`evaluateAsync` 是发出去 + `Thread.sleep(2)` 轮询），而 Coil 会在自己的
 * 调度器上并发跑多枚 fetcher。全局一条锁会把两本漫画的页排成一队；每键一把才只挡同一颗引擎。
 */
class DesktopComicPageFetcherTest {

    private fun page() = DesktopComicPage(
        sourceKey = "jm",
        comicId = "1461316",
        episodeId = "g",
        imageKey = "1760845530.1.jpg#crop",
        page = 0,
        engine = null,
    )

    @Test
    fun 缓存键是那四枚字符串的确定性算式() {
        assertEquals(
            "键必须是「comic|源|号|话|页键」：同输入同键，跨进程也同键",
            "comic|jm|1461316|g|1760845530.1.jpg#crop",
            page().cacheKey,
        )
        assertEquals("同一枚页两次算出的键必须相等（内存缓存靠的就是这一条）", page().cacheKey, page().cacheKey)
    }

    @Test
    fun 四枚入参任换一枚键就换() {
        val base = page()
        assertNotEquals("换源必须换键（不同源下的同号不是同一页）", base.cacheKey, base.copy(sourceKey = "goda").cacheKey)
        assertNotEquals("换漫画号必须换键", base.cacheKey, base.copy(comicId = "9").cacheKey)
        assertNotEquals("换话号必须换键", base.cacheKey, base.copy(episodeId = "h").cacheKey)
        assertNotEquals("换页键必须换键", base.cacheKey, base.copy(imageKey = "b.jpg").cacheKey)
        assertNotEquals("页序不参与键（同页键即同内容）", false, base.copy(page = 7).cacheKey == base.cacheKey)
    }

    @Test
    fun 缓存键那一行不许掺引擎身份() {
        val text = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicPageFetcher.kt").readText()
        val line = text.lines().first { it.contains("val cacheKey") }
        assertTrue(
            "cacheKey 那一行出现引擎或身份哈希就意味着「换引擎 = 缓存全废」：$line",
            !line.contains("engine") && !line.contains("identityHashCode"),
        )
        assertNotEquals(
            "锁键与缓存键必须是两枚（同一枚的话锁会把缓存键的身份要求带进来）",
            page().cacheKey,
            page().lockKey,
        )
    }

    @Test
    fun 换引擎时缓存键不变而锁键必须变() {
        // 两枚**真**引擎（不是替身）：这一条要钉的失败形态正是"按对象身份算键"，用 null 或替身都量不到。
        // 缓存键掺身份 = 重取一次引擎整话缓存全废；锁键不掺身份 = 两颗 Context 抢同一把锁，
        // 于是"两本漫画的页排成一条队"。两条都在这一条里量。
        withEngines { a, b ->
            assertEquals(
                "同一页在两颗引擎上必须是同一个缓存键（否则换引擎 = 内存缓存全废）",
                page().copy(engine = a).cacheKey,
                page().copy(engine = b).cacheKey,
            )
            assertNotEquals(
                "两颗引擎必须是两把锁（一颗 Context 单线程，锁的粒度就该按引擎来）",
                page().copy(engine = a).lockKey,
                page().copy(engine = b).lockKey,
            )
        }
    }

    @Test
    fun 引擎缺席时那一格判为不可取() {
        assertTrue("没有引擎就不许造 fetcher", !desktopComicPageCanFetch(page()))
        withEngines { a, _ ->
            assertTrue("有引擎就该判为可取", desktopComicPageCanFetch(page().copy(engine = a)))
        }
    }

    /** 起两枚真引擎喂给块，用完关掉。构造 `DesktopJsHost` 不发网络（只是建 GraalJS Context），所以单测里跑得动。 */
    private fun withEngines(block: (EngineSession, EngineSession) -> Unit) {
        val a = engineSession("a")
        val b = engineSession("b")
        try {
            block(a, b)
        } finally {
            a.close()
            b.close()
        }
    }

    private fun engineSession(name: String): EngineSession {
        val dir = File(System.getProperty("java.io.tmpdir"), "b81-engine-$name-${System.nanoTime()}")
        dir.mkdirs()
        return EngineSession(DesktopJsHost(dir, dir, null, emptyList()), dir)
    }

    @Test
    fun 同一把锁下的两段不许重叠() = runBlocking {
        val live = AtomicInteger(0)
        val maxLive = AtomicInteger(0)
        val jobs = (1..3).map {
            launch {
                withDesktopEngineLock("b81-same") {
                    val now = live.incrementAndGet()
                    maxLive.updateAndGet { if (it > now) it else now }
                    delay(20)
                    live.decrementAndGet()
                }
            }
        }
        jobs.joinAll()
        assertEquals(
            "同一颗引擎上的多页必须串行（GraalJS 那颗 Context 单线程，并发进去就是批 80 那种 cancelled）",
            1,
            maxLive.get(),
        )
    }

    @Test
    fun 不同锁的两段允许并行() = runBlocking {
        val running = AtomicInteger(0)
        val maxRunning = AtomicInteger(0)
        val jobs = listOf("b81-a", "b81-b").map { key ->
            launch {
                withDesktopEngineLock(key) {
                    val now = running.incrementAndGet()
                    maxRunning.updateAndGet { if (it > now) it else now }
                    delay(30)
                    running.decrementAndGet()
                }
            }
        }
        jobs.joinAll()
        assertEquals("不同引擎不许互相挡：挡了就是「两本漫画的页排成一条队」", 2, maxRunning.get())
    }

    @Test
    fun 锁是按键各自一份而不是全局一条() {
        assertTrue("同键必须复用同一把（每次新建一把等于没锁）", desktopEngineMutex("k1") === desktopEngineMutex("k1"))
        assertTrue("不同键不许共用一把（共用了就是全局串行）", desktopEngineMutex("k1") !== desktopEngineMutex("k2"))
    }
}
