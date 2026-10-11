package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagSuggestion
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十五批 批 11 · 搜索那一屏的**标签补全**。
 *
 * ## 这一批改的是两件事，都不是"加功能"
 *
 * 1. **候选没复检**。对岸那条链走共享件 [refineGalleryTagSuggestions]：拿回来的每一条都按原始
 *    输入复检一次前缀，对不上的直接丢 —— 因为两站的"前缀匹配"参数**都可能被静默忽略**，
 *    而忽略之后回的还是 200 + 一个像样的数组（`GalleryTag.kt` 的注释记着实测形状）。
 *    桌面这一侧原先是自己 `map { it.name }.distinct().take(8)`，于是站方给脏数据时
 *    屏幕上摆的是**不相干的标签**，而它看起来像"你要搜的那个"。
 * 2. **失败被吞成"没有"**。那一腿原先是 `searchTags(term).getOrDefault(emptyList())` ——
 *    这一站没答上、这一站真没有这个词、我们根本没问这三件事在屏上念成了同一句（沉默）。
 *    现在三句话分开：全腿失败 = 对岸那句「标签补全失败」；部分腿失败 = 点名哪几站没答上；
 *    答上了但 0 条 = 「X 没有以「词」开头的标签」。
 *
 * ## 能真跑的全真跑
 *
 * ①..⑨ 全是纯函数的真输入真输出（假腿数据直接喂），没有一条靠"读源码里有没有某个词"来证明行为。
 * 源码类只留三处，各自的角色都写明：**⑩ 是负向**（那个吞失败的形状不许回来）、
 * **⑪ 是接线**（pane 真调这颗件、这颗件真调那颗共享件）、**⑫⑬ 是回核**（对岸的原句与原值）。
 *
 * ## 为什么点名站数而不是照抄对岸那句「两个站都没有」
 *
 * 对岸那句写死了「两个站」—— 它的来源档最多两腿，那句今天是对岸的真话。
 * 桌面「全部」档是**三腿**，照抄会在三腿那档念错数（⑧ 钉住这一条）。
 * 所以桌面改成按发车顺序点名站名：单站那一档仍与对岸**逐字同串**（⑦ 配 ⑫ 的对照）。
 */
class DesktopSearchSuggestionTest {

    private fun suggestion(
        site: GallerySite,
        name: String,
        count: Int,
        deprecated: Boolean = false,
    ) = GalleryTagSuggestion(site, name, count, 0, deprecated)

    private fun ok(list: List<GalleryTagSuggestion>) = Result.success(list)

    private fun failed() = Result.failure<List<GalleryTagSuggestion>>(IllegalStateException("probe-leg-failed"))

    private fun leg(site: GallerySite, outcome: Result<List<GalleryTagSuggestion>>) = site to outcome

    private fun names(outcome: DesktopSuggestionOutcome): List<String> = outcome.candidates.map { it.name }

    /** ① 脏数据被复检丢掉，顺序按那颗共享件（未废弃在前 → 张数降序 → 名字兜平）。 */
    @Test
    fun 候选按原始输入复检前缀_站方给脏数据时不相干的标签摆不上屏() {
        val raw = listOf(
            suggestion(GallerySite.YANDERE, "hime_cut", 5),
            suggestion(GallerySite.YANDERE, "himeko", 3),
            // 站方静默忽略参数时会回这种东西：前缀对不上 ⇒ 必须丢。
            suggestion(GallerySite.YANDERE, "inuyasha", 999),
            // 站方真有 `name:""` 这种脏数据（对岸注释点名过），一并滤掉。
            suggestion(GallerySite.YANDERE, "", 7),
            suggestion(GallerySite.YANDERE, "hime", 9, deprecated = true),
        )
        val outcome = desktopMergeSuggestionLegs("hime", listOf(leg(GallerySite.YANDERE, ok(raw))), limit = 8)
        assertEquals(listOf("hime_cut", "himeko", "hime"), names(outcome))
    }

    /** ② 空格按对岸那一样算：输入里的空格就是站方的下划线（`hime cut` 命中的是 `hime_cut`）。 */
    @Test
    fun 带空格的输入按站方的下划线写法复检() {
        val raw = listOf(
            suggestion(GallerySite.YANDERE, "hime_cut", 5),
            suggestion(GallerySite.YANDERE, "hime", 4),
        )
        val outcome = desktopMergeSuggestionLegs("hime cut", listOf(leg(GallerySite.YANDERE, ok(raw))), limit = 8)
        assertEquals(listOf("hime_cut"), names(outcome))
    }

    /** ③ 同名候选保留先到的那条腿（站表序在前的那一腿），与对岸同一句语义。 */
    @Test
    fun 两腿同名时保先到那条而不是按张数挑大的() {
        val outcome = desktopMergeSuggestionLegs(
            "fox",
            listOf(
                leg(GallerySite.YANDERE, ok(listOf(suggestion(GallerySite.YANDERE, "fox_ears", 10)))),
                // 后到的那一腿同名但张数大得多 —— 若这里读回 Gelbooru 就是 merge 写错了。
                leg(
                    GallerySite.GELBOORU,
                    ok(listOf(suggestion(GallerySite.GELBOORU, "fox_ears", 999), suggestion(GallerySite.GELBOORU, "fox_tail", 4))),
                ),
            ),
            limit = 8,
        )
        assertEquals(listOf("fox_ears", "fox_tail"), names(outcome))
        assertEquals(GallerySite.YANDERE, outcome.candidates.first().site)
    }

    /**
     * ④ 上限是**并完再封**：每一腿自己复检时都已经按上限截过一刀，两腿并起来就必然超 ——
     * 那一刀落在 merge 之后才算数。
     *
     * ⚠️ 这一条第一版只喂了一腿，扰动"把 `take(limit)` 撤掉"照样全绿：单腿时候选本来就 ≤ 上限，
     * 那条判式恒真。喂两腿才是它要钉的形状（封顶只在每一腿 ⇒ 并完摆 6 枚）。
     */
    @Test
    fun 候选数封顶在给定上限() {
        fun ten(site: GallerySite, prefix: String) = (1..12).map { suggestion(site, "${prefix}_$it", 100 - it) }
        val outcome = desktopMergeSuggestionLegs(
            "hime",
            listOf(
                leg(GallerySite.YANDERE, ok(ten(GallerySite.YANDERE, "hime_a"))),
                leg(GallerySite.GELBOORU, ok(ten(GallerySite.GELBOORU, "hime_b"))),
            ),
            limit = 3,
        )
        assertEquals(3, outcome.candidates.size)
    }

    /** ⑤ 上限 0 或负是**调用方写错**，不是"站里没有" ⇒ 当场抛，不许静默交回空候选。 */
    @Test
    fun 上限不是正数时抛错而不是长成零条候选() {
        val raw = listOf(suggestion(GallerySite.YANDERE, "hime", 5))
        for (limit in listOf(0, -1)) {
            val thrown = runCatching {
                desktopMergeSuggestionLegs("hime", listOf(leg(GallerySite.YANDERE, ok(raw))), limit = limit)
            }
            assertTrue("limit=$limit 应当抛（空候选会被读成「站里没有这个词」）", thrown.isFailure)
        }
    }

    /** ⑥ 每一腿答没答上分别记账，点名顺序 = 发车顺序。 */
    @Test
    fun 哪几站没答上是读得出来的() {
        val outcome = desktopMergeSuggestionLegs(
            "hime",
            listOf(
                leg(GallerySite.SAFEBOORU, failed()),
                leg(GallerySite.YANDERE, ok(listOf(suggestion(GallerySite.YANDERE, "hime", 5)))),
                leg(GallerySite.GELBOORU, failed()),
            ),
            limit = 8,
        )
        assertEquals(listOf("Safebooru", "Gelbooru"), outcome.unansweredSites)
        assertEquals(listOf("Safebooru", "yande.re", "Gelbooru"), outcome.askedSites)
    }

    /** ⑦ 三句话之一：**全部**腿没答上 ⇒ 对岸那句逐字。 */
    @Test
    fun 全部腿失败时念对岸那句() {
        val outcome = desktopMergeSuggestionLegs(
            "hime",
            listOf(leg(GallerySite.YANDERE, failed()), leg(GallerySite.GELBOORU, failed())),
            limit = 8,
        )
        assertEquals("标签补全失败", desktopSuggestionReading("hime", outcome))
    }

    /** ⑧ 三句话之二：部分腿没答上 ⇒ 点名，而它不等于"补全失败"也不等于"没有"。 */
    @Test
    fun 部分腿失败时点名那几站() {
        val outcome = desktopMergeSuggestionLegs(
            "hime",
            listOf(
                leg(GallerySite.YANDERE, ok(listOf(suggestion(GallerySite.YANDERE, "hime_cut", 5)))),
                leg(GallerySite.GELBOORU, failed()),
            ),
            limit = 8,
        )
        assertEquals("Gelbooru 没答上这一问，候选只数了答上的那几站", desktopSuggestionReading("hime", outcome))
    }

    /** ⑧′ 点名那句要**排在**"站里没有"前面：答上的那腿恰好 0 条时，不许把我们的失败说成站方的空结果。 */
    @Test
    fun 部分腿失败而答上的那腿零条时不许说成站里没有() {
        val outcome = desktopMergeSuggestionLegs(
            "hime",
            listOf(
                leg(GallerySite.YANDERE, ok(emptyList())),
                leg(GallerySite.GELBOORU, failed()),
            ),
            limit = 8,
        )
        val reading = requireNotNull(desktopSuggestionReading("hime", outcome))
        assertEquals("Gelbooru 没答上这一问，候选只数了答上的那几站", reading)
        assertFalse("把没答上的那站说成了「没有」：$reading", reading.contains("没有以"))
    }

    /** ⑨ 三句话之三：答上了、但站里真没有这个词（单站那一档与对岸逐字同串）。 */
    @Test
    fun 答上了但零条候选时说的是站里没有() {
        val single = desktopMergeSuggestionLegs("hime", listOf(leg(GallerySite.YANDERE, ok(emptyList()))), limit = 8)
        assertEquals("yande.re 没有以「hime」开头的标签", desktopSuggestionReading("hime", single))
    }

    /** ⑩ 同上但三腿：桌面「全部」档是三站，照抄对岸那句「两个站都没有」会念错数。 */
    @Test
    fun 三腿零条时按发车顺序点名站名() {
        val outcome = desktopMergeSuggestionLegs(
            "hime",
            listOf(
                leg(GallerySite.YANDERE, ok(emptyList())),
                leg(GallerySite.GELBOORU, ok(emptyList())),
                leg(GallerySite.SAFEBOORU, ok(emptyList())),
            ),
            limit = 8,
        )
        val reading = requireNotNull(desktopSuggestionReading("hime", outcome))
        assertEquals("yande.re、Gelbooru、Safebooru 都没有以「hime」开头的标签", reading)
        assertFalse("三腿不许念成两个站：$reading", reading.contains("两个站"))
    }

    /** ⑪ 两句话不许混：答上了有候选 ⇒ **没有**读数；根本没问（0 腿）⇒ 也没有读数。 */
    @Test
    fun 有候选与没发问都不编句子() {
        val answered = desktopMergeSuggestionLegs(
            "hime",
            listOf(leg(GallerySite.YANDERE, ok(listOf(suggestion(GallerySite.YANDERE, "hime", 5))))),
            limit = 8,
        )
        assertNull(desktopSuggestionReading("hime", answered))
        val none = desktopMergeSuggestionLegs("hime", emptyList(), limit = 8)
        assertNull(desktopSuggestionReading("hime", none))
        assertEquals(emptyList<GalleryTagSuggestion>(), none.candidates)
    }

    /** ⑫ 负向：那个把失败吞成空列表的形状不许回来（全文件只此一处用过，整文件扫）。 */
    @Test
    fun 补全那一腿不再把失败吞成空列表() {
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGallerySearchPane.kt"))
        // ⚠️ 这条从前扫的是整颗文件里有没有 `getOrDefault(`（批 39 当场被咬一次）：
        // 它要防的是**网络候选那一路**把"没答上"吞成"没有"，那一种吞的形状是空**表**；
        // 本机词典那一问（`lexicon.translations`）失败时退回"没有译名"是另一种形状（空映象），
        // 它没有"答上了/没有"两档可分（那颗契约交回的就是非空 Map）。所以判式钉到那一枚字面形状为止。
        assertFalse("`getOrDefault(emptyList())` 把「没答上」吞成「没有」", pane.contains("getOrDefault(emptyList())"))
        assertTrue("补全那一段还在（撤了它这条负向就没对象了）", pane.contains("searchTags(term)"))
    }

    /** ⑬ 接线：pane 调这颗件，而这颗件调的正是对岸那颗共享复检件。 */
    @Test
    fun 桌面这一侧不再自己拼候选() {
        val holder = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopSearchSuggestion.kt"))
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGallerySearchPane.kt"))
        // 只钉"调的是哪颗件"到括号这一层：参数写法以后怎么排版都不该红，
        // 而"桌面自己又拼一份前缀过滤"这一族必须由行为判据（①..⑥）之外再挡一次。
        assertTrue("那颗件没走共享复检件", holder.contains("refineGalleryTagSuggestions("))
        assertTrue("pane 没接 merge", pane.contains("desktopMergeSuggestionLegs("))
        assertTrue("pane 没接读数", pane.contains("desktopSuggestionReading("))
    }

    /** ⑭ 回核：对岸那三句原话仍在（我们抄的是它，不是自己造的词）。 */
    @Test
    fun 三句话的出处逐字回核对岸() {
        val area = File(DesktopSourceTree.repoRoot, "app/src/main/java/com/venera/compose/gallery/ui/GallerySearchArea.kt")
        val model = DesktopSourceTree.androidSource("gallery/ui/GallerySearchViewModel.kt")
        val text = model.readText()
        assertTrue("对岸那句「标签补全失败」变了：桌面这句得跟着改", text.contains("suggestError = \"标签补全失败\""))
        assertTrue("对岸单站那句模板变了", text.contains(" 没有以「\$term」开头的标签\""))
        assertTrue("对岸 merge 用 putIfAbsent（保先到）", text.contains("merged.putIfAbsent(suggestion.name, suggestion)"))
        assertTrue("对岸候选行读的是那颗 error 字段", area.readText().contains("error = svm.suggestError"))
    }

    /** ⑮ 回核：候选摆几枚，两端同一个数（值逐字回核，不是"看起来差不多"）。 */
    @Test
    fun 两端候选上限是同一个数() {
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGallerySearchPane.kt"))
        val model = DesktopSourceTree.androidSource("gallery/ui/GallerySearchViewModel.kt").readText()
        val desktop = Regex("SUGGESTION_ROWS = (\\d+)").find(pane)?.groupValues?.get(1)
        val android = Regex("SUGGEST_ROWS = (\\d+)").find(model)?.groupValues?.get(1)
        assertEquals(android, desktop)
        assertEquals("8", desktop)
    }
}
