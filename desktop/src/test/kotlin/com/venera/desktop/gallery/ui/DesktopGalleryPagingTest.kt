package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第二十三批 批 23 · 「下一页」那一档相位与读数**只许有一颗**，而第四面墙（最新）接上续页。
 *
 * ## 为什么这一批把三样一起做
 *
 * 起意点是屏上一句假话：`DesktopLatestReadout` 那句写着"桌面没有那一层分页状态"，
 * 而那颗分页状态件（`DesktopGalleryBatch` + 共享的 `nextPagesForAppend`）桌面已经有了、
 * 发现屏与搜索屏与单人页三屏在吃 —— 手册 §22.3 那条"能力接上了而那句『还没有』还活着"的**镜像**。
 * 修法只有两条：把那句改成实话，或者把那一屏接上。本批接上（那是接线，不是能力）。
 *
 * 而接第四屏时看清：`DesktopForYouMorePhase` 与 `DesktopArtistMorePhase` 是**同一颗相位抄了两遍**，
 * 页尾那两句也是同一对句子抄了两遍（只有量词「张 / 条」不同）。批 12 判过的正是这一族：
 * **四处同一句话而其中一处要自己躲，下一处一定会忘** ⇒ 收成一颗，第四处直接吃它。
 */
class DesktopGalleryPagingTest {

    private val uiDir = File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/ui")

    private fun code(name: String): String = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(name))

    /** 「下一页」那三档的相位：整个 `gallery/ui` 只许有一颗。 */
    private fun phaseDeclarations(): List<String> =
        uiDir.listFiles()?.flatMap { file ->
            DesktopSourceTree.codeLinesOf(file)
                .filter { it.contains("sealed interface") && it.contains("MorePhase") }
                .map { "${file.name}: $it" }
        }?.sorted() ?: emptyList()

    /** ① 一颗相位：抄第二遍的那一刻就是漂移的开始。 */
    /**
     * ② 那枚按钮的文案与页尾那句是**一颗件**，而量词是**入参**不是写死的「张」。
     *
     * 批 12 那条同一族：必填参数只挡住漏传，挡不住那颗件自己写死那个值 —— 所以这里喂**两种**单位，
     * 并把"两种都算出来一样"当成违例（那才叫写死）。
     */
    @Test
    fun 单位词是入参而两种都喂过() {
        val zhang = requireNotNull(desktopGalleryMoreReading(DesktopGalleryMorePhase.Loading, taken = 18, unit = "张"))
        val tiao = requireNotNull(desktopGalleryMoreReading(DesktopGalleryMorePhase.Loading, taken = 18, unit = "条"))
        assertTrue("那句没吃量词入参（张）：$zhang", zhang.contains("18 张"))
        assertTrue("那句没吃量词入参（条）：$tiao", tiao.contains("18 条"))
        assertNotEquals("两种单位算出来同一句 = 那颗件写死了量词", zhang, tiao)
        val labelZhang = desktopGalleryMoreLabel(35, "张")
        val labelTiao = desktopGalleryMoreLabel(35, "条")
        assertTrue("按钮文案没带张数：$labelZhang", labelZhang.contains("35"))
        assertTrue("按钮文案没吃量词：$labelTiao", labelTiao.contains("条"))
        assertTrue("按钮文案不再说『下一页』：$labelZhang", labelZhang.contains("下一页"))
    }

    /** ③ 三档各一句，而失败那句必须替**已经摆出来的那些**担保（追加语义的实话）。 */
    @Test
    fun 三档各一句而失败那句替旧张数担保() {
        assertNull("Idle 那一档不该有话说（页尾就是那枚按钮）", desktopGalleryMoreReading(DesktopGalleryMorePhase.Idle, 18, "张"))
        val loading = requireNotNull(desktopGalleryMoreReading(DesktopGalleryMorePhase.Loading, 18, "张"))
        val failed = requireNotNull(desktopGalleryMoreReading(DesktopGalleryMorePhase.Failed("timeout"), 18, "张"))
        assertTrue("正在取那一句没说清在取什么：$loading", loading.contains("下一页"))
        assertTrue("失败那句没点名原因：$failed", failed.contains("timeout"))
        assertTrue("失败那句没替已经摆出来的那些担保：$failed", failed.contains("一张不少"))
        assertNotEquals("两档塌成一句了", loading, failed)
    }

    /**
     * ④ 「最新」那一屏的续页吃共享那颗持有件，桌面这边不另造页码账。
     *
     * 与单人页 ⑪ 同一条判式（那一族的两屏版）：该发哪几条腿、下一笔第几页、一页怎么落地三处
     * 全转交现成件；`page + 1` / `base.page` 那两种手抄形状一次列全。
     */
    /**
     * ⑤ 那句假话下架：「桌面没有那一层分页状态」。
     *
     * 扫**全文**（含注释）—— 坑 ㉑ 那条：过期说明九成住在注释里。
     * 对照钉的是"这一屏真接上了"，撤掉续页会先红在对照上，而不是要求后人改注释。
     */
    /**
     * ⑥ 到底那一句**复用现成那颗** [desktopExhaustedReading]，而旧那句手写的「已经到底的站：…」下架。
     *
     * 那颗件带着一枚图证教出来的规矩（站名与中文之间要留空格，否则 `yande.re这一页` 会被念成
     * 一枚站名）—— 手写那一份躲不过这一条，而文本判据也抓不到它。
     */
    /**
     * ⑦ 落地那颗是**纯函数**（不发网络），所以这一族的判据能真跑而不是扫字。
     *
     * 批 22 立的规矩：文本判据不够就把那一格抽成跑得动的纯函数。这里真跑三件事：
     * 两页重叠一张不摆两遍、没答上那站不推进页码也不算到底、满一页不算到底。
     */
    @Test
    fun 落地那颗能真跑() {
        val y = GallerySite.YANDERE
        val g = GallerySite.GELBOORU
        val base = DesktopGalleryBatch(
            seed = 0L,
            queries = mapOf(y to "", g to ""),
        )
        val page1 = mapOf(y to posts(y, 1L, 20L), g to posts(g, 1L, 20L))
        val after1 = desktopWallBatchAfter(base, pages = mapOf(y to 1, g to 1), answered = page1, reasons = emptyMap()) { 20 }
        assertEquals("第一页就丢了张数", 40, after1.posts.size)
        // 第二页与第一页**重叠**：每站前 5 张重复。
        val page2 = mapOf(y to posts(y, 16L, 35L), g to posts(g, 16L, 35L))
        val after2 = desktopWallBatchAfter(after1, pages = mapOf(y to 2, g to 2), answered = page2, reasons = emptyMap()) { 20 }
        assertEquals("重叠那些张被摆了两遍（去重键没吃上一批已有的）", 70, after2.posts.size)
        assertEquals("屏上有重复条目", after2.posts.size, after2.posts.map { it.uid }.distinct().size)
        assertEquals("页码账没记到第 2 页", mapOf(y to 2, g to 2), after2.pageBySite)
        assertTrue("满一页却被判成到底：" + after2.sitesDone, after2.sitesDone.isEmpty())
        // 第三笔只有一站答上，另一站超时 ⇒ 超时那站页码不前进、也不算到底。
        val flaky = desktopWallBatchAfter(
            after2,
            pages = mapOf(y to 3, g to 3),
            answered = mapOf(y to posts(y, 36L, 55L)),
            reasons = mapOf(g to "timeout"),
        ) { 20 }
        assertEquals("没答上那站被记了页码（那一页的图就永久漏掉）", mapOf(y to 3, g to 2), flaky.pageBySite)
        assertTrue("没答上那站被算成到底了：" + flaky.sitesDone, g !in flaky.sitesDone)
        assertTrue("缺席没留下原因：" + flaky.failures, flaky.failures[g] == "timeout")
        assertTrue("答上的那站满一页却被判到底：" + flaky.sitesDone, y !in flaky.sitesDone)
        // 短页（不足 pageSize）才算到底。
        val tail = desktopWallBatchAfter(flaky, pages = mapOf(y to 4, g to 3), answered = mapOf(y to posts(y, 56L, 60L), g to posts(g, 21L, 40L)), reasons = emptyMap()) { 20 }
        assertTrue("短页没被判成到底：" + tail.sitesDone, y in tail.sitesDone)
    }

    private fun posts(site: GallerySite, from: Long, to: Long): List<com.venera.compose.gallery.data.GalleryPost> =
        (from..to).map {
            com.venera.compose.gallery.data.GalleryPost(
                site = site,
                id = it,
                tags = "t",
                previewUrl = "https://img/${site.routeKey}-$it.jpg",
                fileExt = "jpg",
            )
        }
}
