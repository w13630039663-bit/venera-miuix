package com.venera.desktop.gallery.ui

import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.KeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.gallery.domain.decodeGallerySearchHistory
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十三批 批 9 · 搜索那一屏的**查询历史**。
 *
 * ## 这一屏不写第二份编解码
 *
 * encode / decode / push 三枚判据早就在共享面 `gallery/domain/GallerySearch.kt`（v2 行格式、
 * 同键并站、新的排最前、封顶 `HISTORY_MAX`、认不出的站丢掉而不是硬归某个站），
 * 落盘件 `DesktopStoreFactory` 也早就接上了。桌面这一批要做的只有三件事：
 * **读一次、第 1 页成功后写回、屏上那一排给三个真动作**。所以这里 most 判据钉的是"转交"而不是"算式"。
 *
 * ## 能真跑的全真跑
 *
 * ①⑦ 用临时目录里那颗**真 store** 走一整圈（写盘 → 新实例读回 → 解码），因为这一族最坏的
 * 失败形状是"内存里看着对、盘上那份不是对岸那份"：键名、prefs 名、清空语义（**删掉这个键**
 * 而不是写成版本哨兵）三样里任何一样偏了，用户在手机上搜过的历史在桌面上就是看不见，
 * 而两端各自看起来都正常工作。
 *
 * ## 每条负向扫描都配一条"锚点真在"的对照
 *
 * 批 8 记下的两条规矩在这里都用上了：**扫整串表达式而不是标识符**（⑤⑥），
 * **区域切片要先断言那两个锚点抓到了东西**，否则锚点写错时判式恒真（③⑤）。
 */
class DesktopSearchHistoryTest {

    private class TempPaths(root: File) : PathProvider {
        override val dataRoot: File = root
        override val cacheRoot: File = File(root, "cache").also { it.mkdirs() }
    }

    private fun freshStore(): Pair<KeyValueStore, File> {
        val root = Files.createTempDirectory("search-history").toFile().also { it.deleteOnExit() }
        return JsonKeyValueStore(PreferenceKeys.PREFS_GALLERY_SEARCH, TempPaths(root)) to root
    }

    private fun countIn(text: String, token: String): Int = text.split(token).size - 1

    /** ① 那份盘上的件与对岸是同一份：prefs 名、键名两样都逐字回核 Android 的调用点。 */
    @Test
    fun 键名与那份键值件逐字回核对岸() {
        val holder = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopSearchHistory.kt"))
        assertEquals(
            "桌面自己写死了一枚 prefs 名（对岸改名时这边不会跟着）",
            0, countIn(holder, "\"venera_gallery_search\""),
        )
        assertTrue("落盘没走 PreferenceKeys.PREFS_GALLERY_SEARCH", holder.contains("PreferenceKeys.PREFS_GALLERY_SEARCH"))
        assertEquals("那一枚键名不叫 history（对岸读的就是这个键）", "history", DESKTOP_SEARCH_HISTORY_KEY)
        // 对岸那一行今天还写着这两个字（改了这边要跟着改，而不是让判据红在措辞上）。
        val vm = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/ui/GallerySearchViewModel.kt"))
        listOf(
            "stores.open(PreferenceKeys.PREFS_GALLERY_SEARCH)",
            "getString(\"history\", null)",
            "put(\"history\", encodeGallerySearchHistory(",
            "remove(\"history\")",
        ).forEach { line ->
            assertTrue("对岸那一句不在了（桌面这枚判据的前提变了）：$line", vm.contains(line))
        }
    }

    /** ② 没发车 / 空串都不记一条 —— 那颗条目构造对空腿是 require 抛，不是静默空表。 */
    @Test
    fun 没发车的那一轮不记历史() {
        assertNull("一站都没发出去也要记一条 ⇒ 那条历史点开来没有腿（且构造会抛）", desktopSearchHistoryEntry(emptyList(), "1girl"))
        assertNull("空串不该进历史（屏上根本不发车）", desktopSearchHistoryEntry(listOf(GallerySite.YANDERE), "   "))
        val entry = desktopSearchHistoryEntry(listOf(GallerySite.YANDERE, GallerySite.SAFEBOORU), "  1girl  ")
        assertNotNull("正常那一轮没记上", entry)
        assertEquals("记的串没裁两端", "1girl", entry?.query)
        assertEquals(
            "记的是**这一轮真发过的腿**（未配账号那一站没搜过，不该出现在那条历史的站名里）",
            setOf(GallerySite.YANDERE, GallerySite.SAFEBOORU), entry?.sites,
        )
        // 对照：这条判据是**承重**的 —— 空腿构造真的抛，不是我想当然。
        val threw = runCatching { GallerySearchEntry(emptyList(), "1girl") }.exceptionOrNull()
        assertTrue("对照组失效：空腿构造不再抛了（那上面那条判空就该撤）：$threw", threw is IllegalArgumentException)
    }

    /** ③ 只有第 1 页那一笔写历史；续页那一笔不许再记（同一轮会被记成两条）。 */
    @Test
    fun 续页那一笔不另记一条() {
        val pane = File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGallerySearchPane.kt").readText()
        val from = pane.indexOf("appending = true")
        val to = pane.indexOf("disabled = appending")
        assertTrue("锚点没抓到（续页那格的形状变了，先回读代码再改判据）from=$from to=$to", from in 0 until to)
        val block = pane.substring(from, to)
        assertFalse("续页那一笔也在写历史 ⇒ 翻两页就成了两条历史", block.contains("desktopHistoryPush("))
        assertEquals("写历史的地方不止一处", 1, countIn(pane, "desktopHistoryPush("))
        // 对照：第 1 页那一笔确实写了（否则上面两条是恒真的空判式）。
        assertTrue("第 1 页成功后没落历史（屏上那一排永远是空的）", pane.contains("desktopSearchHistoryEntry(dispatched, query)"))
    }

    /** ④ 点一条历史落哪一档：多站 ⇒ 全部，单站 ⇒ 那一站（与对岸 applyHistory 同一条判据）。 */
    @Test
    fun 一条历史点开落哪一档() {
        val both = desktopHistorySourceOf(GallerySearchEntry(listOf(GallerySite.YANDERE, GallerySite.SAFEBOORU), "a"))
        assertTrue("两站并成的那条点开来不是「全部」档", both.isMulti)
        val single = desktopHistorySourceOf(GallerySearchEntry(GallerySite.GELBOORU, "a"))
        assertEquals("单站那条点开来回到了别站", listOf(GallerySite.GELBOORU), single.sites)
        // 对岸那一行逐字回核（这条判据的依据是它的形状，不是我抄一遍）。
        val vm = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/ui/GallerySearchViewModel.kt"))
        assertTrue(
            "对岸的落档判据变了（先回代码核对再改这边）",
            vm.contains("if (entry.sites.size > 1) GallerySearchSource.ALL else GallerySearchSource.single(entry.site)"),
        )
        // 对照：落点必须在来源轴那张表里，否则 sourceIndexOf 会 require 抛。
        GallerySite.entries.forEach { site ->
            assertTrue("单站档不在轴里：${site.routeKey}", GallerySearchSource.options.contains(GallerySearchSource.single(site)))
        }
        assertTrue("「全部」档不在轴里", GallerySearchSource.options.contains(GallerySearchSource.ALL))
    }

    /**
     * ⑤ 点一条 = **三样一起写**：条件、档位、另起一轮。
     *
     * 少第三样就是"点了没反应" —— 那颗取数 effect 键着 (sourceIndex, round, query)，
     * 连点同一条时前两样都没变，会被批 5 那道跳过守卫整笔挡掉（对岸 2026-09-26 的改判说的正是这个死局）。
     */
    @Test
    fun 点一条历史是三步而不是一步() {
        val landing = desktopHistoryLanding(GallerySearchEntry(listOf(GallerySite.YANDERE, GallerySite.SAFEBOORU), "b -a"))
        assertEquals("条件没按标签表序拼回（同一串换个书写顺序该并成一条）", "b -a", landing.query)
        assertTrue("档位没落到「全部」", landing.source.isMulti)
        val pane = File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGallerySearchPane.kt").readText()
        val from = pane.indexOf("onPick = { entry ->")
        val to = pane.indexOf("onRemove =")
        assertTrue("锚点没抓到（那一格的形状变了）from=$from to=$to", from in 0 until to)
        val block = pane.substring(from, to)
        listOf("query = landing.query", "sourceIndexOf(sources, landing.source)", "round += 1").forEach { line ->
            assertTrue("点一条历史少了这一步（$line）—— 屏上就成了「点了没反应」", block.contains(line))
        }
        // 对照：对岸那句改判还在（"点历史不直接开搜"那条已作废，别在桌面重述一遍）。
        // ⚠️ 这一句与下面 ⑧ 那句都住在**注释**里，所以走 readText 而不是 codeText ——
        // 批 7 记过的那条坑在这里咬了我自己一口：codeText 滤掉注释行，拿它扫"对岸那句依据"恒红。
        val vm = DesktopSourceTree.androidSource("gallery/ui/GallerySearchViewModel.kt").readText()
        assertTrue("对岸那句改判依据不在了", vm.contains("并立刻按它搜一遍"))
    }

    /** ⑥ 编解码一颗都不在桌面重述（行格式只有共享面那一份）。 */
    @Test
    fun 行格式只有共享面那一份() {
        val holder = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopSearchHistory.kt"))
        listOf("decodeGallerySearchHistory(", "encodeGallerySearchHistory(", "pushGallerySearchHistory(").forEach {
            assertTrue("没转交共享面那颗 $it", holder.contains(it))
        }
        // 版本哨兵与字段分隔符是那颗件的私产：桌面重述一份就是第二处口径。
        assertFalse("桌面自己写了 v2 哨兵", holder.contains("\"v2\""))
        assertFalse("桌面自己拼了制表符行格式", holder.contains("'\t'"))
        // 对照：三枚确实在共享面，而且哨兵住在那边。
        val face = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/domain/GallerySearch.kt"))
        assertTrue("对照组失效：哨兵不住在共享面了", face.contains("private const val HISTORY_V2 = \"v2\""))
    }

    /** ⑦ 三个动作各有真落点，而且真写到盘上（临时目录里那颗真 store 走一整圈）。 */
    @Test
    fun 三个动作真落到盘上() {
        val (store, root) = freshStore()
        assertEquals("新机器上读历史不该抛", emptyList<GallerySearchEntry>(), desktopHistoryRead(store))

        val one = desktopHistoryPush(
            store, emptyList(),
            desktopSearchHistoryEntry(listOf(GallerySite.YANDERE), "1girl"),
        )
        assertEquals("push 之后内存里没拿到新表", 1, one.size)
        val reopened = JsonKeyValueStore(PreferenceKeys.PREFS_GALLERY_SEARCH, TempPaths(root))
        val onDisk = decodeGallerySearchHistory(reopened.getString("history", null))
        assertEquals("盘上那份读不回来（键名或编码偏了）", listOf(GallerySearchEntry(GallerySite.YANDERE, "1girl")), onDisk)

        // 同一批标签换个站：并进那一条而不是多出一条（这条判据在共享面，桌面只负责转交）。
        val merged = desktopHistoryPush(
            store, one,
            desktopSearchHistoryEntry(listOf(GallerySite.GELBOORU), "1girl"),
        )
        assertEquals("同键没并成一条", 1, merged.size)
        assertEquals("两站没并进同一条", setOf(GallerySite.YANDERE, GallerySite.GELBOORU), merged.single().sites)

        val afterRemove = desktopHistoryRemoveOne(store, merged, merged.single())
        assertEquals("删一条没生效", emptyList<GallerySearchEntry>(), afterRemove)
        assertTrue("删一条没写盘", desktopHistoryRead(reopened).isEmpty())

        // 清空：对岸走的是 remove（这个键**不在盘上**），不是写成版本哨兵 —— 两份语义不同。
        desktopHistoryPush(store, emptyList(), desktopSearchHistoryEntry(listOf(GallerySite.SAFEBOORU), "touhou"))
        val cleared = desktopHistoryClearAll(store)
        assertEquals("清空之后内存里还剩东西", emptyList<GallerySearchEntry>(), cleared)
        assertFalse("清空写成了空表而不是删掉这个键（对岸 remove 的语义被换了）", reopened.keys().contains("history"))
    }

    /** ⑧ 发现那一屏不许往历史里灌 —— 机器生成的串每轮都变，会把用户自己敲的那几行挤没。 */
    @Test
    fun 发现那一屏不写历史() {
        val forYou = File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopForYouPane.kt").readText()
        assertFalse("发现那一屏在写搜索历史", forYou.contains("desktopHistory"))
        assertFalse("发现那一屏在写搜索历史（直接吃了共享面那三枚）", forYou.contains("pushGallerySearchHistory"))
        // 对照：这条禁令的对岸依据还在（它点名的正是「最近搜索」那几行被挤没 —— 那句住在注释里，走 readText）。
        val svm = DesktopSourceTree.androidSource("gallery/ui/GalleryForYouViewModel.kt").readText()
        assertTrue("对岸那句依据变了", svm.contains("最近搜索"))
    }

    /** ⑨ 屏上那一排的词逐字回核 Android，而那一排的三枚动作都是必填参数。 */
    @Test
    fun 那一排的词与三个动作都在屏上() {
        val pane = File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGallerySearchPane.kt").readText()
        assertTrue("那一排没在界面里声明", pane.contains("private fun DesktopHistoryRow("))
        assertTrue("那一排没被真调（历史读了却不摆上屏 = 只接了一半）", pane.contains("DesktopHistoryRow("))
        listOf("onPick = ", "onRemove = ", "onClearAll = ").forEach {
            assertTrue("那一排缺了一枚动作（$it）—— 没做的动作不许摆，做了的不许只摆一半", pane.contains(it))
        }
        val area = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/ui/GallerySearchArea.kt"))
        listOf("最近搜索", "清空").forEach { word ->
            assertTrue("对岸屏上没有「$word」这两个字（桌面自造了新词）", area.contains("\"$word\""))
        }
        // 对照：那颗件收的动作一律必填（带默认值的回调参数由 DesktopCallbackParamLedgerTest 整仓钉着，
        // 这里只钉"这一排真收三枚动作、而且没有一枚被合并"，少一枚那面整仓尺子也盖不住语义合并）。
        val from = pane.indexOf("private fun DesktopHistoryRow(")
        val to = pane.indexOf(") {", from)
        assertTrue("锚点没抓到（那一排的声明形状变了）from=$from to=$to", from in 0 until to)
        val sig = pane.substring(from, to)
        listOf("onPick:", "onRemove:", "onClearAll:").forEach {
            assertTrue("那一排的签名里没有 $it —— 三枚动作合并成了一枚（屏上就分不出「再搜一遍」和「删掉」）", sig.contains(it))
        }
    }
}
