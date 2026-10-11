package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryForYouMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面「这一批」持有件（第十批 ③）—— 发现域续页要的那颗状态件，**纯值 + 纯 reducer**。
 *
 * ## 为什么是值 + reducer 而不是一个可变类
 *
 * 分页这套的五条行为（谁不再问、追加还是替换、去重键怎么累计、缺席算不算到底、换种子作废）
 * 每一条都能**真跑**取值 —— 前提是它不挂在组合或协程上。做成可变 ViewModel 形状的话，
 * 这五条就全退化成"扫源码看有没有那一句"，而扫源码钉不住语义
 * （`seenKeys += ...` 写错了顺序、或者写成了赋值，扫出来一模一样）。
 *
 * ## 五条行为逐条对着 Android 那颗 ViewModel（`GalleryForYouViewModel.applyPage`）
 *
 * 桌面这边**不重述判据**：混摆、去重键、到底判据分别转交 `GalleryForYouMerge` 与
 * `GallerySearchMerge.exhaustedSites`，这颗文件只管"一页落地之后状态长什么样"。
 * 之所以要照抄那五条而不是自己定，是"同一屏在两端翻出不同页码语义"这种漂最难被发现。
 *
 * ⚠️ 其中**「缺席不算到底」**那一条（②）是被真缺陷教出来的：请求失败也记成"回了 0 条"
 * 就会把那一站判成到底、从此这轮不再问它，而页尾写着「已经到底」——
 * 那是拿一句假话结束一整屏。
 */
class DesktopGalleryBatchTest {

    private fun post(site: GallerySite, id: Long) = GalleryPost(
        site = site,
        id = id,
        tags = "artist_x",
        previewUrl = "https://img/$site-$id.jpg",
        fileExt = "jpg",
    )

    private fun paged(vararg sites: GallerySite) = GalleryForYouMerge.Paged(
        posts = sites.map { post(it, 10L + it.ordinal) },
        perSite = sites.groupingBy { it }.eachCount(),
        excludedFavourite = 0,
        droppedUnusable = 0,
        droppedDuplicates = 0,
    )

    private fun batch(seed: Long = 7L) = DesktopGalleryBatch(
        seed = seed,
        queries = mapOf(GallerySite.YANDERE to "a", GallerySite.SAFEBOORU to "b"),
    )

    /** ① 第一页是**替换**、第二页起是**追加**，页码跟着走。 */
    @Test
    fun 第一页替换而之后追加() {
        val start = batch()
        val first = desktopGalleryBatchApply(
            current = start.copy(posts = listOf(post(GallerySite.YANDERE, 999L))),
            round = 7L,
            pages = mapOf(GallerySite.YANDERE to 1, GallerySite.SAFEBOORU to 1),
            returnedBySite = mapOf(GallerySite.YANDERE to 20, GallerySite.SAFEBOORU to 20),
            reasons = emptyMap(),
            pageSizeOf = { 20 },
            paged = paged(GallerySite.YANDERE, GallerySite.SAFEBOORU),
        )
        assertEquals("第 1 页必须把旧的那一批换掉（换种子之后还追加就是两批混摆）", 2, first.posts.size)
        assertEquals(1, first.page)

        val second = desktopGalleryBatchApply(
            current = first,
            round = 7L,
            pages = mapOf(GallerySite.YANDERE to 2, GallerySite.SAFEBOORU to 2),
            returnedBySite = mapOf(GallerySite.YANDERE to 20, GallerySite.SAFEBOORU to 20),
            reasons = emptyMap(),
            pageSizeOf = { 20 },
            paged = paged(GallerySite.YANDERE, GallerySite.SAFEBOORU),
        )
        assertEquals("第 2 页是追加，不是替换（替换的话用户看到的是同一屏换了内容）", 4, second.posts.size)
        assertEquals(2, second.page)
    }

    /** ② 到底判据只吃**答上了的**那些站；没答上的不算到底，也不许被当成"回了 0 条"。 */
    @Test
    fun 缺席不算到底而到底的站不再发车() {
        val answered = mapOf(GallerySite.YANDERE to 5) // 5 < 20 ⇒ yande.re 真到底了
        val landed = desktopGalleryBatchApply(
            current = batch(),
            round = 7L,
            pages = mapOf(GallerySite.YANDERE to 1, GallerySite.SAFEBOORU to 1),
            returnedBySite = answered,
            reasons = mapOf(GallerySite.SAFEBOORU to "超时"),
            pageSizeOf = { 20 },
            paged = paged(GallerySite.YANDERE),
        )
        assertEquals("答上而不足一页的那一站才算到底", setOf(GallerySite.YANDERE), landed.sitesDone)
        assertEquals("没答上的那一站要留着原因，而不是被记成到底", "超时", landed.failures[GallerySite.SAFEBOORU])
        assertEquals(
            "发过两站而只有一站到底 ⇒ 还没到底（此时页尾不许说「已经到底」）",
            false,
            landed.atEnd,
        )

        val legs = desktopGalleryBatchLegs(landed, mapOf(GallerySite.YANDERE to "a", GallerySite.SAFEBOORU to "b"))
        assertEquals("已到底的那一站下一轮不该再发车", listOf(GallerySite.SAFEBOORU), legs.keys.toList())
    }

    /** ③ 去重键跨页累计，且只累计**真上屏那批**的键。 */
    @Test
    fun 去重键只累计上屏那批而跨页累计() {
        val first = desktopGalleryBatchApply(
            current = batch(),
            round = 7L,
            pages = mapOf(GallerySite.YANDERE to 1, GallerySite.SAFEBOORU to 1),
            returnedBySite = mapOf(GallerySite.YANDERE to 20, GallerySite.SAFEBOORU to 20),
            reasons = emptyMap(),
            pageSizeOf = { 20 },
            paged = paged(GallerySite.YANDERE, GallerySite.SAFEBOORU),
        )
        assertEquals(
            "去重键必须就是「上屏那批」的键（多算 = 下一轮把没摆过的也剔掉；少算 = 会摆出重复卡）",
            first.posts.flatMap { GalleryForYouMerge.keysOf(it) }.toSet(),
            first.seenKeys,
        )
        val second = desktopGalleryBatchApply(
            current = first,
            round = 7L,
            pages = mapOf(GallerySite.YANDERE to 2, GallerySite.SAFEBOORU to 2),
            returnedBySite = mapOf(GallerySite.YANDERE to 20, GallerySite.SAFEBOORU to 20),
            reasons = emptyMap(),
            pageSizeOf = { 20 },
            paged = paged(GallerySite.YANDERE),
        )
        assertEquals(
            "第二页的键并进来，而第一页的键一个不许掉",
            first.seenKeys + second.posts.flatMap { GalleryForYouMerge.keysOf(it) },
            second.seenKeys,
        )
        assertTrue(second.seenKeys.containsAll(first.seenKeys))
    }

    /** ④ 中途换过种子 ⇒ 这一笔整笔作废（否则卡片写着新串而图来自旧串）。 */
    @Test
    fun 换过种子之后落地的那一笔整笔作废() {
        val current = batch(seed = 11L)
        val stale = desktopGalleryBatchApply(
            current = current,
            round = 4L,
            pages = mapOf(GallerySite.YANDERE to 2, GallerySite.SAFEBOORU to 2),
            returnedBySite = mapOf(GallerySite.YANDERE to 20),
            reasons = emptyMap(),
            pageSizeOf = { 20 },
            paged = paged(GallerySite.YANDERE),
        )
        assertEquals("种子不符时状态必须原样交回（连页码都不许动）", current, stale)
    }

    /** ⑤ 三枚计数逐页**累加**，不是取最后一页的。 */
    @Test
    fun 剔掉的张数逐页累加() {
        val counters = GalleryForYouMerge.Paged(
            posts = listOf(post(GallerySite.YANDERE, 1L)),
            perSite = mapOf(GallerySite.YANDERE to 1),
            excludedFavourite = 3,
            droppedUnusable = 2,
            droppedDuplicates = 1,
        )
        val first = desktopGalleryBatchApply(batch(), 7L, mapOf(GallerySite.YANDERE to 1), mapOf(GallerySite.YANDERE to 20), emptyMap(), { 20 }, counters)
        val second = desktopGalleryBatchApply(first, 7L, mapOf(GallerySite.YANDERE to 2), mapOf(GallerySite.YANDERE to 20), emptyMap(), { 20 }, counters)
        assertEquals("「排掉 N 张你收藏过的图」取的是累计数（只留最后一页会把上一轮的账抹掉）", 6, second.excludedFavourite)
        assertEquals(4, second.droppedUnusable)
        assertEquals(2, second.droppedDuplicates)
    }

    /** ⑥ 发过的站全部到底才算到底；一站都没发（空屏）不算"到底了"。 */
    @Test
    fun 到底那一档只在发过的站全到底时才亮() {
        val none = DesktopGalleryBatch(seed = 1L)
        assertFalse("没发过任何东西 ⇒ 不叫到底（那叫空屏，是另一句话）", none.atEnd)
        val allDone = batch().copy(
            sitesDone = setOf(GallerySite.YANDERE, GallerySite.SAFEBOORU),
            queries = mapOf(GallerySite.YANDERE to "a", GallerySite.SAFEBOORU to "b"),
        )
        assertTrue(allDone.atEnd)
        val extra = allDone.copy(sitesDone = allDone.sitesDone + GallerySite.GELBOORU)
        assertTrue("多记了一枚没发过的站也不该改变结论（判据是「发过的都到底」）", extra.atEnd)
    }

    /** ⑦ 换一批 = 全新的一颗：页码、去重键、到底、卡片四样一起清零。 */
    @Test
    fun 换一批把这一颗整颗换新() {
        val used = desktopGalleryBatchApply(
            current = batch(seed = 1L),
            round = 1L,
            pages = mapOf(GallerySite.YANDERE to 1, GallerySite.SAFEBOORU to 1),
            returnedBySite = mapOf(GallerySite.YANDERE to 20, GallerySite.SAFEBOORU to 20),
            reasons = emptyMap(),
            pageSizeOf = { 20 },
            paged = paged(GallerySite.YANDERE, GallerySite.SAFEBOORU),
        )
        val fresh = DesktopGalleryBatch(seed = 2L)
        assertEquals("新一轮的页码必须从 0 起（带着上一轮的页码，第一笔就会按第 2 页追加进新批次）", 0, fresh.page)
        assertTrue(fresh.posts.isEmpty() && fresh.seenKeys.isEmpty() && fresh.sitesDone.isEmpty())
        assertEquals("新一轮不许继承上一轮的失败原因", emptyMap<GallerySite, String>(), fresh.failures)
        // 对照组：上面那几条不是恒真 —— 用过的那一颗确实攒到了东西。
        assertEquals(2, used.posts.size)
        assertEquals(1, used.page)
        assertTrue(used.seenKeys.isNotEmpty())
    }

    /**
     * ⑧ **每条腿各自在第几页**由共享那颗 `nextPagesForAppend` 算，而这一颗把结果记账。
     *
     * 钉的是桌面第十批 ③ 留下的一处缺陷：发现那一屏从前对**所有腿**发同一个 `base.page + 1`，
     * 于是上一笔超时的那一站被跳到第 2 页 —— 它从没拿到过第 1 页，而这一轮的账里也没有它，
     * **那一页的图就永久漏掉了**（屏上只写着"这一站没答上"，没人会去数少了一页）。
     * `nextPagesForAppend` 写的就是这条判据（缺席的腿从第 1 页补），只是桌面没吃它。
     */
    @Test
    fun 缺席的那一站下一笔从第一页补() {
        val landed = desktopGalleryBatchApply(
            current = batch(),
            round = 7L,
            pages = mapOf(GallerySite.YANDERE to 1, GallerySite.SAFEBOORU to 1),
            // Safebooru 这一笔没答上 ⇒ 它根本不在 returnedBySite 里（缺席不算到底，见 ②）。
            returnedBySite = mapOf(GallerySite.YANDERE to 20),
            reasons = mapOf(GallerySite.SAFEBOORU to "超时"),
            pageSizeOf = { 20 },
            paged = paged(GallerySite.YANDERE),
        )
        assertEquals(
            "只有**答上了的**那一站记到第 1 页；没答上的那一站不许记账（记了就等于承认它看过第 1 页）",
            mapOf(GallerySite.YANDERE to 1),
            landed.pageBySite,
        )

        val legs = desktopGalleryBatchLegs(landed, mapOf(GallerySite.YANDERE to "a", GallerySite.SAFEBOORU to "b"))
        val pages = desktopGalleryBatchNextPages(landed, legs.keys.toList())
        assertEquals(
            "下一笔：到底的那站不再问（不在表里），答过的 yande.re 要第 2 页，没答上的 safebooru 补第 1 页",
            mapOf(GallerySite.YANDERE to 2, GallerySite.SAFEBOORU to 1),
            pages,
        )
        // 对照：那颗共享件真在（桌面不许自己写页码算式）。
        val face = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/domain/GallerySearchSource.kt"))
        assertTrue("对照组失效：共享面没有那颗页码算式", face.contains("fun nextPagesForAppend("))
        val holder = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryBatch.kt"))
        assertTrue("落地件没转交那颗算式（自己写一份就是第二处口径）", holder.contains("nextPagesForAppend("))
    }

    /** ⑨ 页码只有一处状态：`page` 是从 `pageBySite` 导出的读数，不留第二份可写的。 */
    @Test
    fun 页码不留第二份状态() {
        // 用**字段表**而不是构造参数名：JVM 默认不落参数名（要 `javaParameters` 才有），
        // 而 data class 的属性字段一定在。这里判的就是"有没有第二枚存页码的字段"。
        val fields = DesktopGalleryBatch::class.java.declaredFields.map { it.name }
        assertTrue("页码那份账不在了（现在叫什么？）：" + fields, fields.contains("pageBySite"))
        assertFalse(
            "又存了一枚可直接写的 `page` 字段 ⇒ 两页一套判据，迟早漂（追加时按哪一份算？）",
            fields.contains("page"),
        )
        // 对照：读数 `page` 还在（页尾那句要用它），只是它不再是能写的那份账。
        assertTrue(
            "读数 `page` 没了（页尾那句要用它）",
            DesktopGalleryBatch::class.java.methods.any { it.name == "getPage" && it.parameterCount == 0 },
        )
        // 对照：`page` 仍是读数，而它由上面那份账导出 —— 两页落地之后读到 2。
        val first = desktopGalleryBatchApply(
            current = batch(), round = 7L,
            pages = mapOf(GallerySite.YANDERE to 1, GallerySite.SAFEBOORU to 1),
            returnedBySite = mapOf(GallerySite.YANDERE to 20, GallerySite.SAFEBOORU to 20),
            reasons = emptyMap(), pageSizeOf = { 20 },
            paged = paged(GallerySite.YANDERE, GallerySite.SAFEBOORU),
        )
        val second = desktopGalleryBatchApply(
            current = first, round = 7L,
            pages = mapOf(GallerySite.YANDERE to 2, GallerySite.SAFEBOORU to 2),
            returnedBySite = mapOf(GallerySite.YANDERE to 20, GallerySite.SAFEBOORU to 20),
            reasons = emptyMap(), pageSizeOf = { 20 },
            paged = paged(GallerySite.YANDERE, GallerySite.SAFEBOORU),
        )
        assertEquals(1, first.page)
        assertEquals(2, second.page)
    }
}
