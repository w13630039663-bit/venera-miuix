package com.venera.desktop.gallery.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十三批 批 8 · 搜索那一屏的**续页**。
 *
 * ## 这一批补的是"只取第 1 页"那笔欠账
 *
 * 搜索那一屏从第六批起就是页码写死的，页尾挂着一句「（翻页还没接）」。
 * 缺的不是网络那一层（`searchPosts(query, page, limit)` 一直能翻页），缺的是**这一屏的状态**：
 * 各腿第几页、哪些站到底、去重键累计到哪儿 —— 而那几样在第十批 ③ 已经立成一颗
 * [DesktopGalleryBatch]（发现那一屏在用）。所以这一批不是"再造一套分页"，是**把第二屏接到同一颗上**：
 * 两处各定一遍"缺席的腿算不算到底"，早晚会漂成"同一串标签在两端翻出不同数量"。
 *
 * ## 顺手修掉一处缺陷（长在发现那一屏上）
 *
 * 那颗 holder 从前只存一枚全局页码，而发现屏对**所有腿**发同一个 `page + 1` ——
 * 上一笔超时的那一站从没拿到过第 1 页，下一笔却被跳到第 2 页 ⇒ 那一页的图**永久漏掉**，
 * 而屏上只写着"那一站没答上"。共享面早写了这条判据（`nextPagesForAppend`：缺席的腿从第 1 页补），
 * 桌面没吃它。现在页码是一份逐腿的账，算式转交那颗共享件（判据在 [DesktopGalleryBatchTest] ⑧⑨）。
 *
 * ## 这一屏仍然没有的（逐条点名，不留假开关）
 *
 * 滚到页尾**不**自动续页（要点那枚钮）；没有"跳到第 N 页"；查询历史还不存（那是批 9）。
 */
class DesktopSearchPagingTest {

    private val pane = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGallerySearchPane.kt",
    )

    /** 代码行（跳注释）—— 判"接线在不在"用这一把。 */
    private fun code(): String = DesktopSourceTree.codeText(pane)

    /** 全文（含注释）—— 判"那句说明还成不成立"用这一把（批 7 那条方法：过期说明住在注释里）。 */
    private fun full(): String = pane.readText()

    /** ① 「下一页」真摆出来了，而它外面有三道闸。 */
    @Test
    fun 那一枚钮只在还能再取时摆() {
        val code = code()
        assertTrue("屏上没有「下一页」", code.contains("Text(\"下一页\""))
        assertTrue("没到底才摆那枚钮（到底了还摆就是假开关）", code.contains("!batch.atEnd"))
        assertTrue(
            "在飞的时候没闸：连点会并发两笔续页，而那句「正在取下一页」会说不清是哪一笔",
            code.contains("disabled = appending"),
        )
        assertTrue("在飞时没先立起那枚闸", code.contains("appending = true"))
        // 对照：到底那一句仍在，而三档各有各的分支（不许挤成一句）。
        // ⚠️ 批 12 起那句的字面量住在 `DesktopWallReadings.kt`（四处手拼的读数收进一颗件），
        // 所以这里两头各钉一次：**pane 真调那颗件** + **那句原话还在那颗件里**。
        // 从前钉的是"pane 源码含「已经取到底」"—— 那是 §0 坑 ㉖ 那一族（把某一版实现当规格）。
        assertTrue("页尾那一档没走那颗件", code.contains("desktopExhaustedReading("))
        val readings = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopWallReadings.kt"))
        assertTrue("找不到那句「已经取到底」", readings.contains("已经取到底"))
        assertTrue(
            "正在取 / 还能再取 / 已到底 三档没分开（屏上会同时摆出按钮和「已经到底」）",
            code.contains("appending ->") && code.contains("!batch.atEnd ->") && code.contains("else ->"),
        )
    }

    /** ② 那句"翻页还没接"下架；而这一屏的**边界**要换成新的那句说实话。 */
    @Test
    fun 说翻页没做的那句下架而边界仍写明() {
        listOf("（翻页还没接）", "不挂「加载更多」", "只取第 1 页").forEach { 句 ->
            assertFalse("这句已经不成立了还留着：「$句」", full().contains(句))
        }
        assertTrue(
            "撤掉旧那句之后要说明白的仍然在：滚到页尾不自动续页（要点那枚钮）",
            full().contains("自动续页"),
        )
    }

    /** ③ 页码只有一处算式：转交那颗共享件，屏上不自写。 */
    @Test
    fun 页码算式只有一处() {
        val code = code()
        assertTrue("续页没转交那颗页码算式", code.contains("desktopGalleryBatchNextPages("))
        assertFalse("屏上又写死了一枚 `page = 1`（那正是这一批要撤的东西）", code.contains("page = 1"))
        assertFalse("屏上自己推页码（那是「所有腿同一页」的旧形状）", code.contains("page + 1"))
        assertTrue("取数没吃算出来的那一页", code.contains("page = pages.getValue(site)"))
        // 对照：那颗共享件真在桌面编译面上（读空会让上面几条恒真）。
        val face = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/domain/GallerySearchSource.kt"))
        assertTrue("对照组失效：共享面读不到那颗算式", face.contains("fun nextPagesForAppend("))
    }

    /** ④ 第一轮与续页**共用同一颗取数件**：各写一份就是"第一页能翻而续页判据漂"的成因。 */
    @Test
    fun 两轮共用同一颗取数件() {
        // 排除声明按"是不是那颗的声明行"判，不按可见性关键字判（批 32 同一族：
        // `private suspend fun` 那种写法把**修饰符钉成了要求**，改成 internal 就红在搬家上）。
        val calls = code().lines().count {
            it.contains("fetchSearchPage(") && !it.contains("fun fetchSearchPage(")
        }
        assertEquals("第一轮与「下一页」没共用同一颗取数件（现在数到 $calls 处调用）", 2, calls)
        assertTrue("落地没走那颗共享 reducer", code().contains("desktopGalleryBatchApply("))
    }

    /** ⑤ 跨页去重的键要从上一笔带过来 —— 漏了就是"翻一页，三张里两张是刚才那两张"。 */
    @Test
    fun 续页拿的是累计的去重键() {
        val code = code()
        assertTrue(
            "混排没吃上一笔攒下的键（站方翻页窗口会重发同一批，不带去就重摆）",
            code.contains("interleave(answered, base.seenKeys)"),
        )
        assertTrue(
            "第一轮没把这一轮的标签串记在批次上（`queries` 是 atEnd 的判据来源，续页也从它取）",
            code.contains("queries = dispatched.associateWith { tags }"),
        )
    }

    /** ⑥ 续页落地有一道闸，而被丢掉的那一笔要出声（不许静默"点了没反应"）。 */
    @Test
    fun 丢掉的那一笔续页要出声() {
        val code = code()
        // 钉的是**那道式子**而不是里面的两个名字：扰动把条件换成 `if (true)` 时，
        // 两个名字都还在（else 分支成了死码，文本判式看不见），只有整串表达式会消失。
        assertTrue(
            "续页落地没比对种子（中途换过查询就会把旧那一批摆上新那一屏）",
            code.contains("if (now == landed.batch.seed)"),
        )
        assertTrue(
            "丢掉那一笔时屏上什么都不说而 stdout 也没有：用户读到的是「按了没反应」，" +
                "而真相是「那一笔属于上一批」 —— 那行读数是唯一的凭据",
            code.contains("APPEND=丢掉一笔续页"),
        )
        assertTrue("取数失败那一档没留读数（只把已经取到的那批留在屏上是不够的）", code.contains("APPEND=续页没取成"))
    }

    /** ⑦ 反向钉：这一批**不该**动的东西 —— 三档缺席各自那一句话还在。 */
    @Test
    fun 三档缺席的读数没被顺手改掉() {
        val notices = code()
        assertTrue("「没配凭据」那一档的逐站点名不在了", notices.contains("missing.entries.joinToString"))
        assertTrue("「这一轮没答上的站」那一句不在了", notices.contains("这一轮没答上的站"))
        assertTrue("预算那道闸那句不在了", notices.contains("""当前 ${'$'}{filters.size} 枚"""))
        // 对照组：上面那三条不是恒真 —— 换一枚不存在的句子过同一条判式。
        assertFalse("对照组失效：拼串判式抓不到不存在的句子", notices.contains("这一轮没答上的星系"))
    }
}
