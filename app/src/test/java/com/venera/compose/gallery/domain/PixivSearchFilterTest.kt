package com.venera.compose.gallery.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pixiv 搜索那一排条件的判据（2026-10-17 方案 C；深夜按 pixez 的口径补齐五段）。
 *
 * 钉五件事：
 * 1. **默认态一枚参数都不发** —— 升级到这一版、什么都不点，与"没有这块面板"是同一串请求；
 * 2. **只有站方真的认的那几枚会发出去**（读数见 `PixivSearchFilter` 类注释里那张表）；
 * 3. **两条腿各发各的**：人气 / 收藏只在登录腿（`app-api`）上，匿名腿发了等于假装筛过；
 * 4. **作品与比例在本地筛**，而"宽高都没给"的那几张**不许**被筛掉；
 * 5. **起止颠倒时对调**，而不是拒绝 —— 用户先填止、再回头填起是最常见的顺序。
 */
class PixivSearchFilterTest {

    @Test
    fun `默认态一枚参数都不发`() {
        assertEquals(emptyList<Pair<String, String>>(), PixivSearchFilter.DEFAULT.webParams())
        assertTrue(PixivSearchFilter.DEFAULT.isDefault)
        assertEquals(0, PixivSearchFilter.DEFAULT.activeCount)
        assertFalse(PixivSearchFilter.DEFAULT.needsAccount)
    }

    @Test
    fun `登录腿默认只发它必填的那两枚`() {
        // app-api 的 `sort` / `search_target` 没有"不发就是默认"的约定，
        // 所以默认态在登录腿上不是空串 —— 但**默认值本身**仍与站方默认同义。
        assertEquals(
            listOf("sort" to "date_desc", "search_target" to "partial_match_for_tags"),
            PixivSearchFilter.DEFAULT.appParams(premium = false),
        )
    }

    @Test
    fun `最新那一档是站方默认序，不发 order`() {
        val params = PixivSearchFilter(order = PixivSearchOrder.NEWEST).webParams()
        assertTrue(params.none { it.first == "order" })
    }

    @Test
    fun `旧序发 order=date`() {
        val params = PixivSearchFilter(order = PixivSearchOrder.OLDEST).webParams()
        assertEquals(listOf("order" to "date"), params)
    }

    @Test
    fun `标签完全发 s_mode，部分不发`() {
        assertEquals(
            listOf("s_mode" to "s_tag_full"),
            PixivSearchFilter(tagMode = PixivTagMode.FULL).webParams(),
        )
        assertTrue(PixivSearchFilter(tagMode = PixivTagMode.PARTIAL).webParams().isEmpty())
    }

    @Test
    fun `比例两档各发一枚，方向不能反`() {
        assertEquals(
            listOf("ratio" to "0.5"),
            PixivSearchFilter(ratio = PixivRatio.LANDSCAPE).webParams(),
        )
        assertEquals(
            listOf("ratio" to "-0.5"),
            PixivSearchFilter(ratio = PixivRatio.PORTRAIT).webParams(),
        )
    }

    @Test
    fun `时间窗发 scd 与 ecd，日期按 ISO 写`() {
        val params = PixivSearchFilter(
            from = LocalDate.of(2026, 10, 1),
            to = LocalDate.of(2026, 10, 5),
        ).webParams()
        assertEquals(
            listOf("scd" to "2026-10-01", "ecd" to "2026-10-05"),
            params,
        )
    }

    @Test
    fun `只填起始时只发 scd，不把止补成那一天`() {
        // 现量：`scd=2026-10-10` 单独发 ⇒ total 936（那天之后）；
        // 补一个 ecd 会静默变成"就这一天"，而屏上那一格明明是空的。
        val filter = PixivSearchFilter(from = LocalDate.of(2026, 10, 1))
        assertEquals(LocalDate.of(2026, 10, 1) to null, filter.bounds())
        assertEquals(listOf("scd" to "2026-10-01"), filter.webParams())
        assertEquals(listOf("最新", "2026-10-01 起"), filter.summary())
    }

    @Test
    fun `只填结束时只发 ecd`() {
        val filter = PixivSearchFilter(to = LocalDate.of(2026, 10, 12))
        assertEquals(listOf("ecd" to "2026-10-12"), filter.webParams())
        assertEquals(listOf("最新", "截至 2026-10-12"), filter.summary())
    }

    @Test
    fun `起止颠倒时对调`() {
        val filter = PixivSearchFilter(
            from = LocalDate.of(2026, 10, 9),
            to = LocalDate.of(2026, 10, 2),
        )
        assertEquals(LocalDate.of(2026, 10, 2) to LocalDate.of(2026, 10, 9), filter.bounds())
    }

    @Test
    fun `筛选那枚数字只数面板里动过的段，排序与标签模式不算`() {
        // 排序与标签模式都在屏上看得见（排序常驻一行、标签模式在框里），
        // 混进这个数之后「2」到底是哪两件事，用户得逐段点开找。
        assertEquals(
            0,
            PixivSearchFilter(
                order = PixivSearchOrder.OLDEST,
                tagMode = PixivTagMode.FULL,
            ).activeCount,
        )
        assertEquals(1, PixivSearchFilter(ratio = PixivRatio.PORTRAIT).activeCount)
        assertEquals(
            2,
            PixivSearchFilter(
                ratio = PixivRatio.PORTRAIT,
                from = LocalDate.of(2026, 1, 1),
            ).activeCount,
        )
    }

    @Test
    fun `摘要第一枚恒是排序，后面的按比例时间排`() {
        val summary = PixivSearchFilter(
            order = PixivSearchOrder.OLDEST,
            tagMode = PixivTagMode.FULL,
            ratio = PixivRatio.LANDSCAPE,
            from = LocalDate.of(2026, 1, 1),
            to = LocalDate.of(2026, 1, 31),
        ).summary()
        assertEquals(listOf("旧序", "标签完全", "横图", "2026-01-01 至 2026-01-31"), summary)
    }

    @Test
    fun `默认态摘要只有排序那一枚`() {
        assertEquals(listOf("最新"), PixivSearchFilter.DEFAULT.summary())
    }

    @Test
    fun `参数顺序固定，便于对请求串做断言`() {
        val params = PixivSearchFilter(
            order = PixivSearchOrder.OLDEST,
            tagMode = PixivTagMode.FULL,
            ratio = PixivRatio.PORTRAIT,
            from = LocalDate.of(2026, 1, 1),
            to = LocalDate.of(2026, 1, 31),
        ).webParams()
        assertEquals(listOf("order", "s_mode", "ratio", "scd", "ecd"), params.map { it.first })
    }

    @Test
    fun `同一组条件与默认值不相等`() {
        assertFalse(PixivSearchFilter(ratio = PixivRatio.LANDSCAPE).isDefault)
    }

    // ── 收藏（会员发参数，其余走词尾标签）────────────────────────────────

    @Test
    fun `收藏在会员腿上发 bookmark_num_min，非会员一枚都不发`() {
        val filter = PixivSearchFilter(bookmarks = PixivBookmark.P1000)
        // 匿名腿的 `blt/bgt` 现量被站方静默忽略 —— 发出去等于假装筛过。
        assertTrue(filter.webParams().none { it.first.startsWith("bookmark") })
        assertEquals(
            listOf(
                "sort" to "date_desc",
                "search_target" to "partial_match_for_tags",
                "bookmark_num_min" to "1000",
            ),
            filter.appParams(premium = true),
        )
        // 非会员**不发**这一枚：站方把它划在付费墙里，发过去只会被静默忽略 ——
        // 那正是 2026-10-18「收藏 xxx+ 的筛选没有生效」的主因（用户当时明确说了自己是登录态，
        // 所以成因不是登录、更不是票据，而是会员档）。改判：那一档改走下面的词尾标签。
        assertTrue(filter.appParams(premium = false).none { it.first == "bookmark_num_min" })
        // 「全部」= 0 与不传同义，多发一枚只是噪音。
        assertTrue(
            PixivSearchFilter.DEFAULT.appParams(premium = true).none { it.first == "bookmark_num_min" },
        )
    }

    /**
     * 非会员那一档的真发法 = 把门槛做成**词尾标签**。
     * 现量（2026-10-18，词=`猫`）：不带它 60 条里只有 2 条带收藏数标签，
     * 带上 `100users入り` 之后 58 条里 **47 条**带它 ⇒ 这条路**匿名就真生效**。
     */
    @Test
    fun `非会员把收藏门槛做成词尾标签，会员原样发词`() {
        val filter = PixivSearchFilter(bookmarks = PixivBookmark.P100)
        assertEquals("猫 100users入り", filter.searchWord("猫", premium = false))
        // 会员发真参数时**不往词里加东西**：两处都筛就是双重筛，冷门门槛会空墙。
        assertEquals("猫", filter.searchWord("猫", premium = true))
        // 「全部」那一档任何时候都不加词尾 —— 加了就是替用户多筛一层。
        assertEquals("猫", PixivSearchFilter.DEFAULT.searchWord("猫", premium = false))
    }

    /**
     * 排除 AI 两条腿各发自己那一枚（2026-10-18 用户「一起加」）。
     * 现量（词=`猫`）：`ai_type=0` 回 21 条 `aiType=2`、`ai_type=1` 回 **0 条** ⇒ 匿名腿也是真过滤。
     */
    @Test
    fun `排除 AI 在两条腿各发一枚，默认态一枚都不发`() {
        val on = PixivSearchFilter(excludeAi = true)
        assertEquals(listOf("ai_type" to "1"), on.webParams())
        assertEquals(
            listOf(
                "sort" to "date_desc",
                "search_target" to "partial_match_for_tags",
                "search_ai_type" to "1",
            ),
            on.appParams(premium = false),
        )
        assertTrue(PixivSearchFilter.DEFAULT.webParams().none { it.first == "ai_type" })
        assertTrue(PixivSearchFilter.DEFAULT.appParams(premium = true).none { it.first == "search_ai_type" })
    }

    @Test
    fun `收藏与作品都动过时那枚数字是 2`() {
        assertEquals(
            2,
            PixivSearchFilter(
                bookmarks = PixivBookmark.P500,
                workType = PixivWorkType.UGOIRA,
            ).activeCount,
        )
    }

    @Test
    fun `只有人气还算需要账号，收藏已经不算了`() {
        assertTrue(PixivSearchFilter(order = PixivSearchOrder.POPULAR).needsAccount)
        // 改判（2026-10-18）：收藏从前也算在这里，是因为它只发 `bookmark_num_min`（会员档）。
        // 现在非会员走词尾 `users入り`，**匿名也筛得动** ⇒ 再把它算成"要账号"就是屏上多拦一道没必要的门。
        assertFalse(PixivSearchFilter(bookmarks = PixivBookmark.P100).needsAccount)
        assertFalse(PixivSearchFilter(workType = PixivWorkType.NO_UGOIRA).needsAccount)
        assertFalse(PixivSearchFilter(ratio = PixivRatio.LANDSCAPE).needsAccount)
        assertFalse(PixivSearchFilter(excludeAi = true).needsAccount)
    }

    @Test
    fun `人气只在登录腿上有 sort，匿名腿不发 order`() {
        val filter = PixivSearchFilter(order = PixivSearchOrder.POPULAR)
        // www 那一跳对 `order=popular*` 是静默忽略的（现量逐条一致）⇒ 一枚都不发，
        // 由调用点拦住这一档（非会员点它只说一句，不改条件）。
        assertEquals(emptyList<Pair<String, String>>(), filter.webParams())
        assertEquals("popular_desc", filter.appParams(premium = true).first { it.first == "sort" }.second)
        assertTrue(filter.order.needsPremium)
        assertFalse(PixivSearchOrder.NEWEST.needsPremium)
        assertFalse(PixivSearchOrder.OLDEST.needsPremium)
    }

    // ── 作品类型（本地筛）──────────────────────────────────────────────

    @Test
    fun `作品那一档两条腿都不发参数，只本地筛`() {
        val filter = PixivSearchFilter(workType = PixivWorkType.UGOIRA)
        assertTrue(filter.webParams().isEmpty())
        assertEquals(
            listOf("sort" to "date_desc", "search_target" to "partial_match_for_tags"),
            filter.appParams(premium = false),
        )
    }

    @Test
    fun `仅动图只留动图，排除动图只留静的`() {
        val only = PixivSearchFilter(workType = PixivWorkType.UGOIRA)
        assertTrue(only.keeps(ugoira = true, width = 100, height = 200))
        assertFalse(only.keeps(ugoira = false, width = 100, height = 200))
        val not = PixivSearchFilter(workType = PixivWorkType.NO_UGOIRA)
        assertTrue(not.keeps(ugoira = false, width = 100, height = 200))
        assertFalse(not.keeps(ugoira = true, width = 100, height = 200))
    }

    @Test
    fun `比例在本地筛的方向与站方 ratio 那一刀同义`() {
        val wide = PixivSearchFilter(ratio = PixivRatio.LANDSCAPE)
        assertTrue(wide.keeps(ugoira = false, width = 3479, height = 2268))
        assertFalse(wide.keeps(ugoira = false, width = 1204, height = 1820))
        val tall = PixivSearchFilter(ratio = PixivRatio.PORTRAIT)
        assertTrue(tall.keeps(ugoira = false, width = 1204, height = 1820))
        assertFalse(tall.keeps(ugoira = false, width = 3479, height = 2268))
    }

    @Test
    fun `宽高都没给的那几张不许被比例筛掉`() {
        // 两条腿都可能缺这两格。筛掉等于替站方断言"这不是横图"，而我们手里没有那一格数据。
        val wide = PixivSearchFilter(ratio = PixivRatio.LANDSCAPE)
        assertTrue(wide.keeps(ugoira = false, width = 0, height = 0))
        val tall = PixivSearchFilter(ratio = PixivRatio.PORTRAIT)
        assertTrue(tall.keeps(ugoira = false, width = 0, height = 0))
    }

    // ── 摘要顺序 ────────────────────────────────────────────────────

    @Test
    fun `摘要里收藏排在作品与比例之前`() {
        val summary = PixivSearchFilter(
            bookmarks = PixivBookmark.P500,
            workType = PixivWorkType.UGOIRA,
            ratio = PixivRatio.PORTRAIT,
        ).summary()
        assertEquals(listOf("最新", "收藏 500+", "仅动图", "竖图"), summary)
    }
}
