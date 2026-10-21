package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「UI 问引擎的那几跳都过 `desktopEngineCall`」—— 进度清单 §1 因 4 那笔账的源码那一半。
 *
 * ## 缺口长什么样（端到端量出来的，不是推的）
 *
 * `DesktopJsHost` 与 `EngineSession` **零同步**（那颗文件里没有一枚 `synchronized`/`Mutex`/`withLock`）。
 * 接上界面之前桌面只有一颗引擎的一个使用者（取页那一条，它自己上了锁）；接上探索 / 详情 /
 * 阅读器 / 搜索之后同一颗引擎同时有 4 个调用点，于是实测翻车：
 * `IllegalStateException 装载后取不到 source key: jm`（`EngineSession.kt:52`）——
 * 两趟 `load()` 交错抢走 `window['temp_source']`，回读 `.key` 拿到 null。
 *
 * 修法是 `DesktopEngineCall.kt`。清单里那条 ⚠️ 明写着「已改码 + 编译通过，但没重跑验证」，
 * 所以这里补一把源码尺：**新增一处裸调就红**。锁本身的串行性不在这里重钉 ——
 * 那由 `DesktopComicPageFetcherTest` 那三颗（同键不重叠 / 异键可并行 / 每键一把）钉着。
 *
 * ## 为什么是源码尺而不是行为尺
 *
 * 交错只在**真并发 + 真源 + 真 JS** 那一路才露出来，离屏 `runComposeUiTest` 够不到 GraalJS。
 * 这一层能量到的只有"调用点有没有过那一层"这个形状，而这正是会漂的那一格。
 *
 * ## 距离 ≤ 2 不是猜的（2026-10-21 数出来的）
 *
 * 按**剥掉注释与空行之后的行号**量：每一处被护住的「问引擎」距它上面那枚
 * `desktopEngineCall(` 都是 1 或 2（写法固定是 `desktopEngineCall(e) {` → `e.load(...)` → `e.details(...)`，
 * 搜索那一处同样 1 / 2）。所以这一判读的是"就在同一块里"，不是"隔得不太远"。
 */
class DesktopEngineCallCoverageTest {

    /** `e.load(` / `engine.details(` 这一族：问引擎的那几跳。 */
    private val askEngine = Regex("""^(e|engine)\s*\.\s*(load|details|pages|explore|search|pageImage)\s*\(""")

    /** 剥注释、剥空行之后的代码行（`DesktopSourceTree.codeLinesOf` 已 trim）。 */
    private fun codeOnly(fileName: String): List<String> =
        DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopSourceOf(fileName))
            .filter { it.isNotEmpty() }

    private data class Ask(val index: Int, val line: String, val distanceFromGuard: Int, val ownEngineNearby: Boolean)

    /** 一处「问引擎」合法的两条形状：过那一层调用，或它问的是**自己刚造的那颗**引擎（探针那颗）。 */
    private fun asksOf(fileRelative: String): List<Ask> {
        val lines = codeOnly(fileRelative)
        val out = mutableListOf<Ask>()
        var lastGuard = -1
        lines.forEachIndexed { i, line ->
            if (line.contains("desktopEngineCall(")) lastGuard = i
            if (!askEngine.containsMatchIn(line)) return@forEachIndexed
            val own = lines.subList(maxOf(0, i - 4), minOf(lines.size, i + 6))
                .any { "val engine = EngineSession(host, assetDir)" in it }
            out += Ask(i, line, if (lastGuard < 0) Int.MAX_VALUE else i - lastGuard, own)
        }
        return out
    }

    /**
     * 壳里那 6 跳 + 装配点那 4 跳 = 10 跳；其中 8 跳紧跟在一枚 `desktopEngineCall` 后面（距离 1 或 2），
     * 剩下 2 跳属于启动探针那颗 —— 它**自己造自己关**一颗引擎（`val engine = EngineSession(host, assetDir)`），
     * 不与 UI 那颗共享，所以不需要那把锁。
     *
     * ⚠️ 合法判据按**形状**判而不按变量名判：只允许"这一跳附近有新造的那颗引擎"这一条形状。
     *    谁要是把常驻那颗 `uiEngine` 裸递进某条循环，这里没有新造的那颗 ⇒ 直接红。
     *
     * 计数一并钉住：10 跳是数出来的现状。多一处就得同时改这里 —— 那正是"新接一屏忘了上锁"
     * 会被读出来的时刻（而不是等用户碰到那句 `取不到 source key` 才知道）。
     */
    @Test
    fun UI问引擎的每一跳都紧跟在一层调用之后() {
        val all = asksOf("VeneraDesktopLargeScreen.kt") + asksOf("VeneraDesktop.kt")
        assertEquals(
            "「问引擎」的调用点数变了（现状 10 跳 = 壳 6（详情 load+details、取页 load+pages、探索 load+explore）" +
                " + 装配点 4（搜索 load+search、启动探针 load+explore））：",
            10, all.size,
        )
        val naked = all.filter { it.distanceFromGuard > 2 && !it.ownEngineNearby }
        assertTrue(
            "这些调用点既没过 `desktopEngineCall`、附近也没有它自己新造的那颗引擎" +
                "（同一颗引擎两处裸进 = 实测那种 `取不到 source key`）：\n" +
                naked.joinToString("\n") { "  距离=${it.distanceFromGuard}  ${it.line.take(90)}" },
            naked.isEmpty(),
        )
        // 走"自己那颗"这一条形状的恰好 2 跳，全在装配点那颗**一次性探针**函数里
        // （变量就叫 `engine`）；壳里那颗常驻的叫 `uiEngine`、永远走 `desktopEngineCall` ⇒ 0 跳。
        val own = all.filter { it.ownEngineNearby }
        assertEquals("自己造引擎那一条形状的跳数变了（要重判那颗是不是还与 UI 那颗共享）：", 2, own.size)
        assertTrue(
            "壳里出现了「自己新造一颗引擎」那种裸调 ⇒ 桌面上有了第二颗常驻引擎（每次重组新建就是几百 MB）：" +
                own.map { it.line },
            own.all { it.line.startsWith("engine.") },
        )
    }

    /** 调用点分布：两处 UI 文件、3 + 1 枚。 */
    @Test
    fun desktopEngineCall的调用点恰好是那四处() {
        val counts = DesktopSourceTree.desktopMainSources()
            .filter { it.name != "DesktopEngineCall.kt" }
            .map { it.name to DesktopSourceTree.codeOccurrences(it, "desktopEngineCall(") }
            .filter { it.second > 0 }
            .toMap()
        assertEquals(
            "`desktopEngineCall` 的分布变了：多一处 = 新接一屏没写进这份口径；少一处 = 有人把某条链的锁撤了。",
            mapOf("VeneraDesktopLargeScreen.kt" to 3, "VeneraDesktop.kt" to 1),
            counts,
        )
    }

    /**
     * 那 2 跳（探针的 load + explore）走的是"自己新造一颗"那条形状，**且那颗用完就关**。
     *
     * 清单 §1 写的「探针那颗 `finally { close() }`，不能给 UI 用」就是这里；而 UI 那颗是常驻的
     * （`remember` + `DisposableEffect` 收尾）—— 两件事各自钉一条，谁漂了读得出来。
     */
    @Test
    fun 探针那颗自己造自己关而UI那颗是常驻的() {
        val naked = asksOf("VeneraDesktop.kt").filter { it.distanceFromGuard > 2 }
        assertEquals("探针那两跳（load + explore）的数量变了：", 2, naked.size)
        val lines = codeOnly("VeneraDesktop.kt")
        naked.forEach { ask ->
            val own = lines.subList(maxOf(0, ask.index - 4), minOf(lines.size, ask.index + 6))
                .joinToString("\n")
            assertTrue(
                "${ask.line.take(60)} 这附近没有 `EngineSession(host, assetDir)` ⇒ 它现在用的是共享那颗，" +
                    "必须过 desktopEngineCall：",
                "EngineSession(host, assetDir)" in own,
            )
        }
        // UI 那颗是常驻的（remember + DisposableEffect 关），不是探针那颗。
        val resident = lines.joinToString("\n")
        assertTrue("常驻那颗不再是 remember 出来的（每次重组新建 JS 宿主就是几百 MB）：", "val uiEngine = remember {" in resident)
        assertTrue("常驻那颗没有 dispose 收尾：", "runCatching { uiEngine?.close() }" in resident)
    }

    /**
     * 锁键公式**只有一处**出处（清单里那条"一处，不重写"）。
     *
     * 漂了的形状是"取页上了锁、取章节没上锁" —— 那等于没有锁，而且只在并发时露出来。
     */
    @Test
    fun 锁键公式只有一处出处() {
        val defs = DesktopSourceTree.desktopMainSources().filter {
            DesktopSourceTree.codeOccurrences(it, "internal fun desktopEngineLockKey") > 0
        }.map { it.name }
        assertEquals("`desktopEngineLockKey` 有了第二份定义：", listOf("DesktopComicPageFetcher.kt"), defs)

        val call = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("DesktopEngineCall.kt"))
        assertTrue("`DesktopEngineCall` 没复用 `desktopEngineLockKey`（自己再算一份 identityHashCode）", "desktopEngineLockKey(" in call)
        assertTrue("`DesktopEngineCall` 没真取锁（只包了 withContext）", "withDesktopEngineLock(" in call)
        assertTrue("没切走 IO（阻塞的 JS 轮询会占住组合线程）", "withContext(Dispatchers.IO)" in call)
        // 引擎缺席要念得出成因，不能静默回空。
        assertTrue("引擎没起来那一路没交回 failure", "IllegalStateException(\"引擎没起来\")" in call)
    }

    /**
     * 下载那条泵**至今没接进大屏壳**（如实记账，不是"不需要锁"）。
     *
     * 量到的事实：`desktopComicStartQueuePump` 在 `desktop/src/main` 里只有它自己的定义那颗文件，
     * 零调用点 ⇒ 那条循环里的 `engine.pages(...)` / `engine.pageImage(...)` 今天碰不到 UI 那颗引擎。
     *
     * ⚠️ 哪天有人把它接上 `uiEngine`，那两跳就与 UI 抢同一颗 Context ⇒ **必须**改过
     * `desktopEngineCall`。这一判把这件事钉成一条会红的读数，而不是等运行时翻车。
     */
    @Test
    fun 下载那条泵还没接上UI那颗引擎() {
        val refs = DesktopSourceTree.desktopMainSources().filter {
            DesktopSourceTree.codeOccurrences(it, "desktopComicStartQueuePump(") > 0
        }.map { it.name }.sorted()
        assertEquals(
            "泵接上了新地方 ⇒ 它里面那几跳（pages / pageImage）现在与 UI 共享同一颗 Context，" +
                "必须改过 desktopEngineCall 再改这里：",
            listOf("DesktopComicQueueRunner.kt"),
            refs,
        )
        // 那颗文件里的确有两跳裸问引擎（今天无害，因为没人递那颗共享引擎）。
        // ⚠️ 判式不锚行首：两跳一处写作 `val pages = engine.pages(...)`、
        // 一处藏在 lambda 里 `{ page -> engine.pageImage(...) }`（第一版锚了行首，数出 0 跳）。
        val naked = DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopComicQueueRunner.kt"))
            .filter { Regex("""\bengine\s*\.\s*(pages|pageImage)\s*\(""").containsMatchIn(it) }
        assertEquals("下载那颗问引擎的跳数变了（要重判它是否共享 UI 那颗）：", 2, naked.size)
    }
}
