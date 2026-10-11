package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画廊收藏那一屏的「清空」（批 77 · 上半）。
 *
 * ## 这一格是什么
 *
 * 共享面 `GalleryFavorites` 的三枚写动作 `toggle` / `removeAll` / `clear` 全在，桌面那份
 * `DesktopGalleryFavorites` 也逐枚转交了（`gallery/data/DesktopGalleryFavorites.kt:24-25`），
 * 而屏上只接了 `toggle` —— 那一屏自己写着"本屏没有移除/清空按钮"。收藏多了清不掉，
 * 而存档是用户一张张攒起来的：**不可重建**，所以这一格的本体不是那颗钮，是它前面那道确认。
 *
 * ## 为什么是"屏上两步"而不是弹层
 *
 * 桌面今天没有 `ContentDialogHost`（全仓只有 `SpikeWindow` 那枚探针用过 `FluentDialog`），
 * 为这一格去应用根新铺一层宿主是把两件事捆成一件。而本仓已经有两处现成的形状：
 * 设置那一屏删屏蔽规则**明写"没有确认那一步"**（代价低、敲回来一遍就行），搜索历史那枚「清空」直接可点
 * （只是历史）。收藏不属于那两类 ⇒ 用**同一屏上的两步**：第一按只武装并改口念条数，第二按才真清。
 *
 * ## 五条纪律各有一颗尺子
 *
 * 1. 两档各一句且都点名条数（① ②）—— 不点名条数的确认等于"确认一个未知量"。
 * 2. **条数变了就退回未武装**（②）—— 武装着去别处翻了一页再按，删的就不是刚才那批。
 * 3. 只有第二按真清（③ ④ 屏上次序钉）。
 * 4. 没东西可清时那枚**不摆**（⑤）—— 空屏上一枚"清空全部 0 条"是最像真的假动作。
 * 5. 清完**读回存档还剩几条**（⑥）—— 只念"已清空"而读回非 0，就是把没生效说成生效。
 */
class DesktopGalleryFavoritesClearTest {

    private val pane = DesktopSourceTree.desktopUiSource("DesktopGalleryFavoritesPane.kt")
    private val clearFile = DesktopSourceTree.desktopUiSource("DesktopGalleryFavoritesClear.kt")

    /** ① 两档各一句、都带条数、两两不许相同。 */
    @Test
    fun 两档各念一句都带条数() {
        val idle = desktopFavoritesClearLabel(DesktopFavoritesClearStage.IDLE, 12)
        val armed = desktopFavoritesClearLabel(DesktopFavoritesClearStage.ARMED, 12)
        println("B77_$idle / $armed")
        assertTrue("未武装那句要念得出条数：" + idle, idle.contains("12"))
        assertTrue("武装那句要念得出条数：" + armed, armed.contains("12"))
        assertFalse("两档撞成一句就没有「第二按才是真清」这回事了：$idle vs $armed", idle == armed)
        assertTrue("武装那句得是问句或带「确认」：" + armed, armed.contains("确认") || armed.contains("？"))
    }

    /** ② 条数变了 ⇒ 退回未武装（删的对象换了，先前那一按不能算数）。 */
    @Test
    fun 条数变了就退回未武装() {
        assertEquals("同一批再按就是真清", DesktopFavoritesClearStage.FIRE,
            desktopFavoritesClearNext(DesktopFavoritesClearStage.ARMED, countAtArm = 12, countNow = 12))
        assertEquals("武装之后条数变了（翻了一页 / 别处加了收藏），这一按只许退回未武装",
            DesktopFavoritesClearStage.ARMED,
            desktopFavoritesClearNext(DesktopFavoritesClearStage.ARMED, countAtArm = 12, countNow = 11))
        assertEquals("第一按只武装", DesktopFavoritesClearStage.ARMED,
            desktopFavoritesClearNext(DesktopFavoritesClearStage.IDLE, countAtArm = 0, countNow = 12))
    }

    /** ③ 摆不摆只看**当下这一屏的条数**，纯函数。 */
    @Test
    fun 没东西可清时那枚不摆() {
        assertFalse("空存档上摆一枚「清空全部 0 条」就是假动作", desktopFavoritesClearVisible(0))
        assertTrue("有条数就该给入口", desktopFavoritesClearVisible(1))
        assertTrue("整枚入口只在清完之前摆着", desktopFavoritesClearVisible(12))
    }

    /** ④ 屏上次序钉：真清那一支排在武装之后，且屏上不许绕过那颗件直接 `clear(`。 */
    @Test
    fun 屏上那颗走的是同一枚动作() {
        val code = DesktopSourceTree.codeText(pane)
        assertEquals("屏上调的那枚清空动作不是正好一处：" +
                DesktopSourceTree.codeOccurrences(pane, "desktopClearGalleryFavorites("),
            1, DesktopSourceTree.codeOccurrences(pane, "desktopClearGalleryFavorites("))
        assertEquals("两档那句只许摆一处", 1,
            DesktopSourceTree.codeOccurrences(pane, "desktopFavoritesClearLabel("))
        assertEquals(
            "那一屏绕过那颗件自己按了共享面的 clear（读数与确认层就都管不到它）：" +
                DesktopSourceTree.codeLinesOf(pane).filter { it.contains("favorites.clear(") }.joinToString(" ｜ "),
            0, DesktopSourceTree.codeOccurrences(pane, "favorites.clear("),
        )
        // 两步确认得**能反悔**：武装之后只剩"再按就删"一条路，那就不叫确认层，叫多一步误删。
        // 两处各一次：真清之后复位、与那枚「先不清了」。
        assertEquals("复位那一格出现次数不对（FIRE 之后 + 反悔那枚，各一处）：" +
                DesktopSourceTree.codeOccurrences(pane, "armedAt = null"),
            2, DesktopSourceTree.codeOccurrences(pane, "armedAt = null"))
        // 对照：共享面那三枚写动作确实都在（读空了上面几条会恒真）
        val api = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/data/GalleryBusinessApi.kt"))
        assertTrue("对照组失效：共享面的 clear 声明没了", api.contains("suspend fun clear()"))
        assertTrue("对照组失效：桌面那份转交没了",
            DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryFavorites.kt"))
                .contains("override suspend fun clear()"))
    }

    /** ⑤ 清完要**读回存档还剩几条**，那句不许把"没生效"说成"已清空"。 */
    @Test
    fun 读回非零不许说已清空() {
        val ok = desktopFavoritesClearReading(DesktopClearOutcome.Cleared(cleared = 12, left = 0))
        println("B77_$ok")
        assertTrue("清干净那句要念得出条数：" + ok, ok.contains("12"))
        val suspect = desktopFavoritesClearReading(DesktopClearOutcome.Cleared(cleared = 12, left = 3))
        println("B77_$suspect")
        assertFalse("还剩 3 条时那句绝不能出现「已清空」：" + suspect, suspect.contains("已清空"))
        assertTrue("要念得出还剩几条：" + suspect, suspect.contains("3"))
        val failed = desktopFavoritesClearReading(
            DesktopClearOutcome.Failed("这台机器不给写档", IllegalStateException("这台机器不给写档")),
        )
        assertTrue("三句不许撞成两句：" + listOf(ok, suspect, failed).distinct().size, 3 == listOf(ok, suspect, failed).distinct().size)
    }

    /** ⑥ 没成那一行念得出成因、类型与栈顶，且键值进不了读数（复用批 53 那三颗原子）。 */
    @Test
    fun 失败念得出未成那一行() {
        val thrown = IllegalStateException("disk locked")
        val line = desktopFavoritesClearRefusalReading(DesktopClearOutcome.Failed("disk locked", thrown))
        println("B77_$line")
        assertTrue("前缀不在：" + line, line.startsWith("D_收藏清空 未成 因="))
        assertTrue("类型名没念出来：" + line, line.contains("IllegalStateException"))
        assertTrue("栈那一格：" + line, Regex("栈=([A-Za-z0-9.<>]+\\.kt:-?\\d+|没有)").containsMatchIn(line))
        val keyed = IllegalStateException("https://x/save?api_key=SECRETVALUE")
        val scrubbed = desktopFavoritesClearRefusalReading(
            DesktopClearOutcome.Failed(desktopFavoritesClearFailureReason(keyed), keyed),
        )
        println("B77_$scrubbed")
        assertFalse("键值进了读数：" + scrubbed, scrubbed.contains("SECRETVALUE"))
        assertTrue("键名要留着：" + scrubbed, scrubbed.contains("api_key=***"))
        assertTrue("对照组失效：普通成因也被抹平了",
            !desktopFavoritesClearFailureReason(thrown).contains("***"))
    }

    /** ⑦ 写口两步：成功与没成各有一行 stdout，且各只打印一处。 */
    @Test
    fun 成功与没成都念得出一行() {
        val done = desktopFavoritesClearReadout(DesktopClearOutcome.Cleared(cleared = 12, left = 0))
        println("B77_$done")
        assertTrue("成功那行前缀不在：" + done, done.startsWith("D_收藏清空"))
        assertTrue("要念得出清了几条与还剩几条：" + done, done.contains("清=12") && done.contains("读回=0"))
        val stillThere = desktopFavoritesClearReadout(DesktopClearOutcome.Cleared(cleared = 12, left = 3))
        assertFalse("还剩几条时那行不许念成皆大欢喜：" + stillThere, stillThere.contains("已清空"))
        assertEquals("成功读数打印了两处或没打印", 1,
            DesktopSourceTree.codeOccurrences(clearFile, "println(desktopFavoritesClearReadout("))
        assertEquals("没成读数打印了两处或没打印", 1,
            DesktopSourceTree.codeOccurrences(clearFile, "println(desktopFavoritesClearRefusalReading("))
    }

    /** ⑧ 那颗件不许自己建第二份"怎么清"：写口只走转交那枚，读回只吃流上的现值。 */
    @Test
    fun 那颗件不重抄写链() {
        val code = DesktopSourceTree.codeText(clearFile)
        assertEquals("直接碰了 store 或档面（绕过共享写口）：" +
                DesktopSourceTree.codeLinesOf(clearFile).filter { it.contains("GalleryFavoritesStore") }.joinToString(" ｜ "),
            0, DesktopSourceTree.codeOccurrences(clearFile, "GalleryFavoritesStore"))
        assertTrue("写那一笔走的是共享面那颗 clear", code.contains(".clear()"))
        assertTrue("读回吃的是收藏流（不是自己再开一次档）", code.contains(".favorites.value.size"))
        assertTrue("它走到 IO 线程（清档是写盘）", code.contains("withContext(Dispatchers.IO)"))
    }

    /** ⑨ 旧那句"本屏没有移除/清空按钮"必须随入口一起下架（§22.3 那条回查）。 */
    @Test
    fun 旧那句没有按钮不许残留() {
        val offenders = DesktopSourceTree.desktopUiSources().flatMap { file ->
            DesktopSourceTree.codeLinesOf(file)
                .filter { it.contains("没有移除") || it.contains("刻意不摆它们的入口") }
                .map { "${file.name}: ${it.trim()}" }
        }
        assertTrue("还写着本屏没有清空入口：" + offenders.joinToString(" ｜ "), offenders.isEmpty())
        // 对照：那句理由撤了，而**这件事本身**还在代码里 —— 两步那一步必须是屏上的一处判断，
        // 不是只写在注释里（`codeText` 跳注释，拿"两步""武装"这类词当对照等于恒假）。
        val paneCode = DesktopSourceTree.codeText(pane)
        assertTrue(
            "两步那一步只剩说明了（屏上不再按档判断）：",
            paneCode.contains("desktopFavoritesClearNext(") && paneCode.contains("DesktopFavoritesClearStage.ARMED"),
        )
    }

    /**
     * ⑩ 那颗件**真跑得动盘**：三张 → 清 → 档里 0 张。
     *
     * 为什么要有这一条：① ~ ⑨ 全是纯函数与源码扫描，它们证明得了"两步的形状对"，
     * 证明不了"按下去那笔真能把用户的档清掉"。禁鼠标 ⇒ 最后一跳（真点击）等用户，
     * 但**写链本身**不必等 —— 这里吃真的 `GalleryFavoritesStore` 与真临时目录，不是替身。
     */
    @Test
    fun 那颗件真清得动档() = kotlinx.coroutines.runBlocking {
        val root = java.nio.file.Files.createTempDirectory("b77clear").toFile()
        val paths = object : com.venera.compose.data.platform.PathProvider {
            override val dataRoot: java.io.File = root
            override val cacheRoot: java.io.File = java.io.File(root, "cache")
        }
        // 自己构造一颗，**不吃 getInstance**：那颗是进程级单例，整棵跑时先被 `SharedStoreAtomicWriteTest`
        // 按它的临时目录建好了 ⇒ 我这里写的会是别人的档（单跑绿、全量红 —— 本机实测过一次）。
        val store = com.venera.compose.gallery.data.GalleryFavoritesStore(paths)
        val file = java.io.File(root, "gallery_favorites.json")
        store.clear()
        listOf(1L, 2L, 3L).forEach { id ->
            store.toggle(
                com.venera.compose.gallery.data.GalleryPost(
                    site = com.venera.compose.gallery.data.GallerySite.YANDERE,
                    id = id,
                    previewUrl = "https://img/" + id + ".jpg",
                ),
            )
        }
        assertEquals("起手就该有三张（不然下面那句是空的）", 3, store.favorites.value.size)
        val outcome = desktopClearGalleryFavorites(StoreAsApi(store), clearedHint = 3)
        assertTrue(
            "清干净那一档没走到：" + desktopFavoritesClearReading(outcome),
            outcome is DesktopClearOutcome.Cleared && outcome.left == 0 && outcome.cleared == 3,
        )
        assertEquals("档里必须真剩 0 张：" + file.readText().take(80), "[]", file.readText().trim())
        root.deleteRecursively()
        Unit // runBlocking 的返回值就是最后一笔：deleteRecursively 交回 Boolean 会让这颗用例不是 void（JUnit 报 should be void）
    }
}

/**
 * 把真 store 交回成那颗件吃的接口形状（逐字照 `DesktopGalleryFavorites` 那五枚转交）。
 *
 * 为什么不让产品代码直接收 store：屏上那一屏手里只有 `ports.favorites`（接口），
 * 写口去认实现类就是把分层倒过来一次 —— 与 ⑧ 那条「不许重抄写链」是同一条纪律。
 */
private class StoreAsApi(
    private val store: com.venera.compose.gallery.data.GalleryFavoritesStore,
) : com.venera.compose.gallery.data.GalleryFavorites {
    override val favorites
        get() = store.favorites
    override suspend fun toggle(post: com.venera.compose.gallery.data.GalleryPost): Boolean = store.toggle(post)
    override suspend fun removeAll(uids: Collection<String>) = store.removeAll(uids)
    override suspend fun clear() = store.clear()
    override fun consumeNotice(): String? = store.consumeNotice()
}

