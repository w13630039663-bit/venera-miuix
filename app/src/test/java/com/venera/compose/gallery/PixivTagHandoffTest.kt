package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pixiv 标签那三件（2026-10-20，用户报「作品详情页的 tag 点击后应该进入 tag 搜索，但是没有任何反应，
 * 我返回后反而首页出现了搜索框但是没有任何词」＋「作品标签加入 pixiv 的多语言/翻译 tag 和
 * 长按标签可以屏蔽 复制 搜索的选项」）。
 *
 * ## 这一颗钉的到底是什么
 *
 * 那次"点了没反应"不是某一颗件坏了，而是**两条按设计执行的语义撞在一起**：
 * 1. 交接（`acceptHandoff`）从前**一律**把标签塞进 booru 的 `filters`（胶囊）；
 * 2. 而 pixiv 那一域读的是 `pixivTerm`（一个词）—— 于是 `runPixivSearch` 读到空串，
 *    走了"先打一个词"那条早退，**一笔请求都没发**；
 * 3. 更阴的一半：作品页那颗只写交接槽、**没有离开本屏**，而槽的消费方住在画廊那一屏 ——
 *    那一屏要么被压着（paused）、要么组合压根没了（从收藏页点进来）。槽写进去没人读。
 *
 * 所以判据分三层，各钉一件：
 * - **域分流**：交接按"这张图属于哪个站"分流，pixiv 那一支必须落到 `openPixivContext`
 *   （装 `pixivTerm` + 开搜），不许再把词塞进 `filters`；
 * - **快照保真**：快照必须带上域与那个词，否则弹栈回来只剩"一片结果 + 一个空框"
 *   （弹栈**不发请求**，那个词永远补不回来）；
 * - **离屏出口**：作品页两个宿主（滚动流 + 抽屉）点标签都必须写槽**并退掉本层**。
 *
 * ⚠️ 这是源码尺：本仓无 Robolectric，ViewModel 那层摸不到（同 `GalleryPixivWorkPageWiringTest`）。
 */
class PixivTagHandoffTest {

    private fun src(rel: String): String {
        val f = File(RepoSources.appMain, rel)
        assertTrue("找不到 $rel，量具失效", f.isFile)
        return f.readText().replace("\r\n", "\n")
    }

    /** 注释行不参与匹配（与 `GalleryPixivAiBadgeAndSyncTest` 同一把尺子）。 */
    private fun code(rel: String): List<String> = src(rel).lines().map { it.trim() }
        .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private fun List<String>.countWith(needle: String): Int = count { it.contains(needle) }

    private val vm = "com/venera/compose/gallery/ui/GallerySearchViewModel.kt"
    private val screen = "com/venera/compose/gallery/ui/PixivWorkScreen.kt"
    private val context = "com/venera/compose/gallery/domain/GallerySearchContext.kt"
    private val feedParse = "com/venera/compose/gallery/data/PixivFeedParse.kt"
    private val appParse = "com/venera/compose/gallery/data/PixivAppParse.kt"
    private val feedClient = "com/venera/compose/gallery/data/PixivClient.kt"
    private val appClient = "com/venera/compose/gallery/data/PixivAppClient.kt"

    // ── 一、交接按站点分流：pixiv 那一支走自己的域，不塞 booru 的胶囊 ──────────

    @Test
    fun `交接按站点分流，pixiv 那一支落到 openPixivContext`() {
        val body = code(vm)
        // 分流只有一处、且判的是**站**（不是"当前这一屏是什么域"）：用户点的是一枚 pixiv 标签时，
        // 屏上恰好停在哪个域不该影响这一跳。
        assertEquals("交接入口里只许有一处站点分流", 1, body.countWith("if (site == GallerySite.PIXIV) {"))
        assertEquals("pixiv 那一支必须走 openPixivContext", 1, body.countWith("openPixivContext(tags.firstOrNull().orEmpty())"))
        // 定义只有一颗：两处各写一遍就是两套清场 / 装词顺序。
        assertEquals("openPixivContext 只许定义一次", 1, body.countWith("private fun openPixivContext("))
    }

    /**
     * ⚠️ 这一条钉的是**改名与形状**，不是"有没有某个词"：从前那版是
     * `pixivTerm = ...; runPixivSearch(1)` 直接写在 `applyHistory` 里 —— 于是"点历史"与
     * "点标签"各有各的清场顺序，改一处漏一处。现在两者必须共用同一颗入口。
     */
    @Test
    fun `点历史与点标签共用同一颗入口`() {
        val body = code(vm)
        // 一处定义 + 两处调用（交接 + 历史）= 3：这个组合本身就说明"只有一颗入口、两条路都在用"。
        assertEquals("openPixivContext 只许一颗入口、两条路都走它", 3, body.countWith("openPixivContext("))
        assertEquals("其中只有一处是定义", 1, body.countWith("private fun openPixivContext("))
        // 历史那一支不许再自己装词开搜（自写一遍 = 第二处真相）。
        // 判据只切在 `applyHistory` 这颗函数体里 —— `submitPixivTerm` 那一处
        // `runPixivSearch(1)` 是**键盘上那枚搜索键**，与这条路径无关，不该被这把尺子咬到。
        val start = body.indexOfFirst { it.startsWith("fun applyHistory(") }
        assertTrue("量具失效：找不到 applyHistory", start >= 0)
        val stop = (start + 1 until body.size).firstOrNull { body[it].startsWith("fun ") } ?: body.size
        val fn = body.subList(start, stop)
        assertEquals("历史那一支不许再自己 runPixivSearch", 0, fn.countWith("runPixivSearch(1)"))
        assertEquals("历史那一支也不许自己装词", 0, fn.countWith("pixivTerm = entry.query"))
    }

    /**
     * pixiv 那一支必须**自己把 mode 收到"看墙"**。
     *
     * 不收条的后果正是用户报的那一句：从作品页退回来，屏上一张展开的输入卡 + 键盘，
     * 看起来就是"只剩一个搜索框"。而 `openContext` 那一档把收不收交给调用方（它经手的情形
     * 用户还要接着改条件），所以这一条不能靠"顺手复用同一句"。
     */
    @Test
    fun `pixiv 交接装完就收成看墙，不留一张空输入卡`() {
        val body = code(vm)
        val start = body.indexOfFirst { it.contains("private fun openPixivContext(") }
        assertTrue("量具失效：找不到 openPixivContext", start >= 0)
        val stop = (start + 1 until body.size).firstOrNull { body[it].startsWith("private fun ") } ?: body.size
        val fn = body.subList(start, stop)
        assertEquals("收条那一句只许一处", 1, fn.countWith("mode = GallerySearchMode.RESULTS"))
        // 空词那一档仍要退回输入态（没有"这一轮"可装）—— 两句都在才算分档正确。
        assertEquals("空词那一档仍要退回输入态", 1, fn.countWith("mode = GallerySearchMode.INPUT"))
        // 先收条再开搜：反过来的话 onSearch 那一刻 mode 已经被 beginSearch 之外的路径改回 INPUT。
        val results = fn.indexOfFirst { it.contains("mode = GallerySearchMode.RESULTS") }
        val search = fn.indexOfFirst { it.contains("runPixivSearch(1)") }
        assertTrue("必须先收条再开搜（顺序反了就是一帧空输入卡）", results in 0 until search)
    }

    // ── 二、快照保真：弹栈回来那个词必须还在 ────────────────────────────────

    @Test
    fun `快照带上域与那个词，弹栈能原样抄回来`() {
        val ctx = code(context)
        assertEquals("快照要有域这一格", 1, ctx.countWith("val domain: GalleryHomeDomain = GalleryHomeDomain.BOORU"))
        assertEquals("快照要有词这一格", 1, ctx.countWith("val pixivTerm: String = \"\""))
        assertEquals("快照要有翻页游标那一格", 1, ctx.countWith("val pixivNextUrl: String? = null"))

        val body = code(vm)
        assertEquals("拍快照时要把域带上", 1, body.countWith("domain = domain,"))
        assertEquals("拍快照时要把词带上", 1, body.countWith("pixivTerm = pixivTerm,"))
        assertEquals("拍快照时要把游标带上", 1, body.countWith("pixivNextUrl = pixivNextUrl,"))
        // 弹栈侧：三格都要抄回去，少抄一格就是"结果回来了、词没了"。
        assertEquals("弹栈要把域抄回去", 1, body.countWith("domain = ctx.domain"))
        assertEquals("弹栈要把词抄回去", 1, body.countWith("pixivTerm = ctx.pixivTerm"))
        assertEquals("弹栈要把游标抄回去", 1, body.countWith("pixivNextUrl = ctx.pixivNextUrl"))
        // 对照组：上面那串 1 不是"文件读空了"。
        assertTrue("对照组失效：没读到弹栈那颗", body.any { it.startsWith("fun popContext()") })
    }

    /**
     * `openContext` 那一条（booru）也要把域摆回去。
     *
     * 反向那一条同病：在 pixiv 内容域里点一枚 **booru** 标签时，`source` 换成单站 booru 而
     * `domain` 仍停在 PIXIV ⇒ `runSearch` 按域路由到 pixiv 那一腿，读的是上一轮那个词（或空）。
     * booru 的来源轴**永远不含 pixiv**（`GallerySearchSource.ALL` 显式剥了它），所以走到这里
     * 就意味着这一轮是 booru 的。
     */
    @Test
    fun `booru 交接也会把域摆回 booru`() {
        val body = code(vm)
        assertTrue(
            "openContext 要把域摆回 BOORU",
            body.countWith("domain = GalleryHomeDomain.BOORU") == 1,
        )
        // ⚠️ 不许走 setDomain：它会把 mode 拍回 INPUT 并且顺手清一遍，而这里紧跟着就要装条件
        // ——拆成两句就是同一轮两次重组、先闪一帧空卡。
        assertTrue("换域不清场（清场是这一颗自己的事）", body.none { it.contains("setDomain(GalleryHomeDomain") })
    }

    // ── 三、离屏出口：作品页两个宿主都要写槽并退本层 ─────────────────────────

    /**
     * 作品页点标签 → 写槽 + 退本层。
     *
     * 为什么"退本层"也是判据：槽的消费方住在画廊那一屏，而这一屏压在它上面 ——
     * 只写不退，槽就没人读（用户点了没反应，直到手动返回才被读到，那时屏上还停在空词上）。
     * 两个宿主各钉一次：滚动流末尾那一版与右下「更多信息」抽屉那一版。
     */
    @Test
    fun `作品页两个宿主点标签都写槽并退本层`() {
        val body = code(screen)
        assertEquals("写槽两处（流里 + 抽屉）", 2, body.countWith("GallerySearchHandoff.postTag(GallerySite.PIXIV, tag)"))
        // 每一处后面都跟着 `back()`：一处漏掉就退成"半接线"（点了没反应那一族）。
        val postTagLines = body.withIndex().filter { it.value.contains("GallerySearchHandoff.postTag(GallerySite.PIXIV, tag)") }
        for ((index, _) in postTagLines) {
            val window = body.subList(index, (index + 4).coerceAtMost(body.size))
            assertTrue("写槽之后必须退本层（否则没人读那个槽）：$window", window.any { it == "back()" })
        }
        // 抽屉那一版还要先收掉抽屉（留着它会盖在退场动画上）。
        assertTrue("抽屉那一版要先收抽屉", body.countWith("sheetOpen = false") >= 2)
        // 旧那颗私有 TagChip 整颗撤掉：长按菜单与双显都改走共用的 `GalleryTagChip`。
        assertEquals("旧那颗私有 TagChip 撤干净", 0, body.countWith("private fun TagChip("))
        assertEquals("标签改走共用那颗", 1, body.countWith("GalleryTagChip("))
    }

    /** 标签那一支要**三件事一起接**（显示 / 复制 / 屏蔽），少一件就是半接线。 */
    @Test
    fun `作品页标签接上译名双显与长按三项`() {
        val body = code(screen)
        assertTrue("显示文本走站方译名（只改显示）", body.any { it.contains("text = tagDisplayLabel(tag, detail.tagTranslations)") })
        // 点、复制、屏蔽三枚都传**原串** `tag`（不是译名）：译文会随词表变，检索与屏蔽只认原名。
        assertTrue("点 = 按原名搜", body.any { it.contains("onClick = onSearchTag?.let { search -> { search(tag) } }") })
        assertTrue("复制 = 原名", body.countWith("onCopy = { copyTagText(chipContext, tag) }") == 1)
        assertTrue("屏蔽 = 原名", body.countWith("onBlock = { tagScope.launch { blockTagText(chipContext, tag) } }") == 1)
        // 屏蔽落的是 TAG 规则（pixiv 的 `post.tagList` 就是这些作品标签，实测挡得住）。
        assertTrue("屏蔽落 TAG 规则", body.any { it.contains("guard.addRule(\"TAG\", tag) >= 0") })
    }

    // ── 四、译名两条腿各读各的字段，且必须带 Accept-Language ─────────────────

    /**
     * **不挂 `Accept-Language`，译名那一路永远是空表**。
     *
     * 依据（两条外部事实，不是猜）：pixivpy `set_accept_language` 的注释原文就是
     * 「用于获取 tags 的对应语言 `translated_name`」；而它 issue #76 记着"缺这枚头时字段恒为 None"。
     * 所以这一条钉两件事：两腿都挂上了那一枚头、且取的是同一个常量（两处各写一个字面量迟早漂）。
     */
    @Test
    fun `两条腿都挂 Accept-Language 且共用一个常量`() {
        assertEquals("匿名腿要挂", 1, code(feedClient).countWith(""".header("Accept-Language", PixivAppClient.PIXIV_TAG_LANGUAGE)"""))
        assertEquals("登录腿要挂", 1, code(appClient).countWith(""".header("Accept-Language", PIXIV_TAG_LANGUAGE)"""))
        assertEquals("常量只声明一处", 1, code(appClient).countWith("internal const val PIXIV_TAG_LANGUAGE"))
    }

    /**
     * 两条腿的字段名**本来就不同**（站方的形状）：匿名是 `translation` 对象、登录是 `translated_name`。
     * 各读各的，别把一族的键名套到另一族上（那种错不报错，只让译名恒为空）。
     */
    @Test
    fun `两条腿的译名各读自己的字段`() {
        assertTrue("匿名腿读 translation 对象", code(feedParse).any { it.contains("item[\"translation\"] as? JsonObject") })
        assertTrue("登录腿读 translated_name", code(appParse).any { it.contains("item[\"translated_name\"].textOrNull()") })
        // 键一律小写：消费点 `tagDisplayLabel` 按 `tag.lowercase()` 查，存原名就永远取不到。
        assertTrue("匿名腿的键要小写", code(feedParse).any { it.contains("out[name.lowercase()] = label") })
        assertTrue("登录腿的键要小写", code(appParse).any { it.contains("name.lowercase() to label") })
        // 站方把译名照原名再写一遍的那一档要挡掉：否则屏上会长出「女の子 (女の子)」。
        assertTrue(
            "与原名相同的译名要丢掉",
            code(feedParse).any { it.contains("firstOrNull { !it.equals(name, ignoreCase = true) }") },
        )
        assertTrue(
            "登录腿也要丢掉与原名相同的",
            code(appParse).any { it.contains("if (label.equals(name, ignoreCase = true)) null") },
        )
    }

    /** 模型那一格必须有默认值：两条腿之外的构造点（种子、播放器面板的测试夹具）不该被迫改。 */
    @Test
    fun `译名那一格有默认值，两条腿之外不必逐处补`() {
        val model = code("com/venera/compose/gallery/data/PixivWorkDetail.kt")
        assertEquals(
            "译名那一格要给空表默认值",
            1,
            model.countWith("val tagTranslations: Map<String, String> = emptyMap(),"),
        )
        // 种子那一格不承担译名（它连标签都是空的，见 `PixivWorkPage.seedDetailOf` 头注）。
        assertFalse(
            "种子不许自己编一份译名",
            code("com/venera/compose/gallery/domain/PixivWorkPage.kt").any { it.contains("tagTranslations = ") },
        )
    }
}
