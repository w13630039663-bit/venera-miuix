package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 排行榜那排卡的**形状判据**（2026-10-17 第二十二轮）。
 *
 * 触发这一轮的是用户那句「卡面太小了，就好像压扁了那样，排行榜也没画师头像，缩到一行只同时显示两个半吧」。
 * 三件事里前两件是**数据与几何**，第三件是**数量**，它们各自都能悄悄退回去：
 *
 * 1. **图区不许再钉死高**。`historyCoverHeight`(170dp) 是给历史那一排 124dp 宽的卡定的比例，
 *    搬到排行榜上（曾只有 102dp 宽）就成 102×172 的竖条 —— 竖构图被裁得只剩中间一条，
 *    屏上读作"压扁"。正解是 `aspectRatio` 让高度跟着卡宽走（稿上 389×369 ≈ 1.05）。
 * 2. **文字区必须有作者头像**，且地址取自记录里那格 `user.profile_image_urls`
 *    ——不许按 userId 再发一次请求（一排 10 张 = 10 次额外往返），
 *    也不许在没有地址时拿别人的头像顶。
 * 3. **一行露 2.5 张**是用户拍板的数量，和"摆几张"（`RANKING_PREVIEW_ITEMS`）是两个语义，
 *    合成一个数就会有一处先被改坏。
 *
 * 为什么用源码判据而不是组合测试：`app/` 没有 Robolectric，这几条全是"形状/有无"。
 * ⚠️ 注释行不参与匹配（`codeLines` 那把口径）：这几个文件里大段注释都写着同样的符号名。
 */
class GalleryRankCardShapeTest {

    private val home = codeLines("com/venera/compose/gallery/ui", "GalleryPixivHome.kt")
    private val sections = codeLines("com/venera/compose/gallery/ui", "GalleryHomeSections.kt")
    private val screen = codeLines("com/venera/compose/gallery/ui", "GalleryScreen.kt")
    private val account = codeLines("com/venera/compose/gallery/ui", "GalleryPixivAccount.kt")
    private val card = codeLines("com/venera/compose/components/venera", "VeneraCard.kt")
    private val parse = codeLines("com/venera/compose/gallery/data", "PixivAppParse.kt")
    private val work = codeLines("com/venera/compose/gallery/data", "PixivWork.kt")

    private fun codeLines(dir: String, name: String): List<IndexedValue<String>> {
        val file = RepoSources.kotlinFiles(dir).firstOrNull { it.name == name }
            ?: error("找不到 $name（目录 $dir，根：${RepoSources.root.absolutePath}）")
        return file.readLines().withIndex().filter { (_, line) ->
            val t = line.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }
    }

    /** 按**函数体**切（理由见记忆：按文件切会被同文件的合法调用喂饱）。 */
    private fun bodyOf(lines: List<IndexedValue<String>>, signature: String): String {
        val start = lines.firstOrNull { it.value.contains(signature) }?.index
            ?: error("找不到 `$signature`")
        val rest = lines.dropWhile { it.index < start }.drop(1)
        val end = rest.indexOfFirst { it.value.startsWith("}") }
        if (end < 0) error("`$signature` 的函数体没找到收尾的顶层 `}`")
        return rest.take(end).joinToString("\n") { it.value }
    }

    private fun countIn(text: String, pattern: String): Int = Regex(pattern).findAll(text).count()

    /**
     * 切**局部函数**的体（收尾是缩进 4 列的那枚 `}`）。
     *
     * 不能复用 [bodyOf]：那把按"第 0 列的 `}`"收尾，是给顶层函数用的（函数体内部的括号都缩进）。
     * 局部函数正好反过来 —— 它的收尾带缩进，而内部的 `}` 更深。
     */
    private fun bodyOfLocal(lines: List<IndexedValue<String>>, signature: String): String {
        val start = lines.firstOrNull { it.value.contains(signature) }?.index
            ?: error("找不到 `$signature`")
        val rest = lines.dropWhile { it.index < start }.drop(1)
        val end = rest.indexOfFirst { Regex("""^\s{4}\}""").containsMatchIn(it.value) }
        if (end < 0) error("`$signature` 的函数体没找到收尾")
        return rest.take(end).joinToString("\n") { it.value }
    }

    // ── 一、图区不许再钉死高 ────────────────────────────────────────────────

    @Test
    fun `排行榜卡的图区按卡宽算高，不再钉死 170dp`() {
        val card = bodyOf(home, "private fun PixivWorkCard(")

        assertEquals(
            "封面必须挂 `.aspectRatio(GalleryPixivHome.RANKING_COVER_ASPECT)`（稿上图区是近方形 1.05）：\n$card",
            1,
            countIn(card, """\.aspectRatio\(GalleryPixivHome\.RANKING_COVER_ASPECT\)"""),
        )
        assertEquals(
            "这张卡里不许再出现 `historyCoverHeight`：它是历史那一排 124dp 卡的定高，" +
                "搬过来会把 126dp 的宽撑成 0.59 的竖条（用户报「压扁了」的真因）：\n$card",
            0,
            countIn(card, """historyCoverHeight"""),
        )
        assertTrue(
            "那个比例必须是有名字的常量，不许裸数字（形状判据要能一眼看出改没改）：\n" +
                home.filter { it.value.contains("RANKING_COVER_ASPECT =") }.joinToString("\n"),
            home.any {
                it.value.contains("RANKING_COVER_ASPECT =") &&
                    Regex("""RANKING_COVER_ASPECT = \d\.\d+f""").containsMatchIn(it.value)
            },
        )
    }

    // ── 二、作者头像 ────────────────────────────────────────────────────────

    @Test
    fun `文字区挂作者头像，取记录里那格地址，空串降级为首字母座`() {
        val card = bodyOf(home, "private fun PixivWorkCard(")

        assertEquals(
            "文字区左侧必须有一枚作者头像（用户「排行榜也没画师头像」）：\n$card",
            1,
            countIn(card, """model = work\.authorAvatarUrl"""),
        )
        assertTrue(
            "取图器要显式传画廊那一份（同 `PixivArtistRail` 的理由：默认那枚是漫画的）：\n$card",
            card.contains("work.authorAvatarUrl") && Regex("""imageLoader = imageLoader""").containsMatchIn(card),
        )
        assertTrue(
            "站方没给地址时必须降级成首字母座，**不许拿别人的头像顶**（同画师那一排的纪律）：\n$card",
            card.contains("work.authorAvatarUrl.isNotBlank()") &&
                card.contains("work.post.author.take(1)"),
        )
    }

    @Test
    fun `作者头像走记录里已有的那一格，不另发请求`() {
        assertTrue(
            "解析器必须从 `user.profile_image_urls.medium` 取（就在同一个响应里）：\n" +
                parse.filter { it.value.contains("authorAvatarUrl") }.joinToString("\n"),
            parse.any {
                it.value.contains("authorAvatarUrl") &&
                    Regex("""profile_image_urls""").containsMatchIn(it.value)
            },
        )
        assertTrue(
            "不许在这条腿上按 userId 再请求一次头像（`avatarUrl(` 是一条网络往返）：\n" +
                parse.filter { it.value.contains("avatarUrl(") }.joinToString("\n"),
            countIn(parse.joinToString("\n") { it.value }, """avatarUrl\(""") == 0,
        )
        assertTrue(
            "`PixivWork.authorAvatarUrl` 必须默认空串（那条腿没给时要能编译得过、且屏上如实降级）：\n" +
                work.filter { it.value.contains("authorAvatarUrl") }.joinToString("\n"),
            work.any {
                it.value.contains("val authorAvatarUrl: String = \"\"")
            },
        )
    }

    // ── 三、一行露几张 ──────────────────────────────────────────────────────

    @Test
    fun `收起态一行露两张半，且张数与宽度是两个语义`() {
        val grid = bodyOf(home, "internal fun PixivRankedWorkGrid(")

        assertTrue(
            "卡宽分母必须读 `RANKING_PREVIEW_SLOTS`（用户拍板 2.5 张），不许写回 `/ 3` 或 `/ 2`：\n$grid",
            grid.contains("(maxWidth - gap * 2) / GalleryPixivHome.RANKING_PREVIEW_SLOTS"),
        )
        assertTrue(
            "2.5f 这个数必须落在常量上（改数量时只动一处）：\n" +
                home.filter { it.value.contains("RANKING_PREVIEW_SLOTS =") }.joinToString("\n"),
            home.any { it.value.contains("const val RANKING_PREVIEW_SLOTS = 2.5f") },
        )
        assertTrue(
            "「露几张」与「摆几张」必须仍拆开（合成一个数的话改一处就会改坏另一处）：\n" +
                home.filter { it.value.contains("RANKING_PREVIEW_ITEMS") }.joinToString("\n"),
            home.any { it.value.contains("const val RANKING_PREVIEW_ITEMS = 10") },
        )
    }

    // ── 四、节间距收窄只作用在排行榜那一节 ──────────────────────────────────

    @Test
    fun `节尾间距可单节收窄，默认仍是页面级那一档`() {
        val section = bodyOf(sections, "private fun GalleryHomeSection(")

        assertTrue(
            "`GalleryHomeSection` 必须有可覆盖的节尾间距参数（相邻两节各自带内衬时中段要让位）：\n$section",
            section.contains("gapAfter: Dp? = null") &&
                section.contains("gapAfter ?: VeneraTokens.spacing.sectionGap"),
        )
        assertTrue(
            "默认那一档仍要是页面级 `sectionGap`，不许在函数里写死一个裸 dp：\n$section",
            !section.contains("padding(bottom = 14.dp)") &&
                !section.contains("padding(bottom = 4.dp)"),
        )
        assertTrue(
            "排行榜那一节要显式收窄到 `space2`（用户「压缩下推荐和排行榜的间距」）：\n" +
                sections.filter { it.value.contains("PIXIV_RANKING -> GalleryHomeSection") }
                    .joinToString("\n"),
            sections.any {
                it.value.contains("gapAfter = VeneraTokens.spacing.space2")
            },
        )
    }

    // ── 五、封面通栏（第二十三轮「感觉还是太小了」里最直接的一笔）──────────

    @Test
    fun `封面通栏：内衬归零，且文字区自己补回水平内衬`() {
        assertTrue(
            "`VeneraCard` 的「内容通栏」必须做成**布尔语义**，不是 `contentPadding: Dp` —— " +
                "后者要表达「不要内衬」就得在调用点写 `0.dp`，而裸 `N.dp` 是本仓封顶存量：" +
                "\n" + card.filter { it.value.contains("edgeToEdge") }.joinToString("\n"),
            card.any { it.value.contains("edgeToEdge: Boolean = false") } &&
                !card.any { it.value.contains("contentPadding") },
        )

        val cardBody = bodyOf(home, "private fun PixivWorkCard(")
        assertTrue(
            "排行榜卡必须传 `edgeToEdge = true` —— 实测那 8dp 让封面两侧各缩一截，" +
                "而设计稿上的封面是顶到卡边的（四个圆角就是封面的四个角）：\n$cardBody",
            cardBody.contains("edgeToEdge = true"),
        )
        assertTrue(
            "内衬归零的代价必须自己付：文字区要补水平内衬，否则标题会顶到卡边：\n$cardBody",
            Regex("""\.padding\(horizontal = tokens\.spacing\.space3, vertical = tokens\.spacing\.space2\)""")
                .containsMatchIn(cardBody),
        )
    }

    // ── 六、三枚标签胶囊是同一档尺寸 ─────────────────────────────────────────

    @Test
    fun `两排筛选药丸同档，而换一批胶囊单独放大一档`() {
        // 筛选器（日榜/周榜… 与 已关注/发现）是**一排多档**，彼此必须恒等高。
        val filters = bodyOf(home, "private fun <T> PixivPillRow(") +
            "\n" + bodyOf(home, "private fun <T> PixivSegmentedRow(")

        assertEquals(
            "两排筛选药丸都用 `caption`（档名那排原本是 `itemTitle`、比画师选择器大一号，" +
                "同一屏里两个分段器两种尺寸会被读成「不是一套东西」）：\n$filters",
            2,
            countIn(filters, """fontSize = tokens\.type\.caption"""),
        )
        assertEquals(
            "垂直内衬统一收一档 `space1`（用户「按钮可以再扁一点」），两处不许各写各的：\n$filters",
            2,
            countIn(filters, """vertical = tokens\.spacing\.space1"""),
        )

        // ⚠️ 「换一批」是节头那一行的**唯一动作**，不是筛选器 —— 用户第二十四轮报它「太小了」。
        // 它放大一档是**故意的**，所以这条判据刻意把三者的关系写反：宁可钉住"不许跟着筛选器缩回去"，
        // 也不让它混进上面那两条的计数里（混进去过一次，然后被用户打回）。
        val chip = bodyOf(home, "internal fun PixivRemixChip(")
        assertTrue(
            "换一批必须比筛选药丸大一档（`itemTitle` + `space3`）：\n$chip",
            chip.contains("fontSize = tokens.type.itemTitle") &&
                Regex("""vertical = tokens\.spacing\.space3""").containsMatchIn(chip),
        )
        assertTrue(
            "换批期间要换文案并禁按（不换文案也不禁按 = 按下去像卡住了）：\n$chip",
            chip.contains("if (loading) \"正在换\" else \"换一批\"") &&
                Regex("""enabled = !loading""").containsMatchIn(chip),
        )
    }

    // ── 七、头像描边 ────────────────────────────────────────────────────────

    @Test
    fun `三处头像挂同一颗描边，共用件不许各处手写`() {
        assertTrue(
            "画师那一排的 64dp 圆头像要描边（裸圆压在白卡上只有色差、没有边）：\n" +
                bodyOf(home, "internal fun PixivArtistRail("),
            bodyOf(home, "internal fun PixivArtistRail(")
                .contains("veneraAvatarRing(CircleShape)"),
        )
        assertTrue(
            "卡里那枚小头像要描边，且用圆角方形（照抄站方原样，不强行改圆）：\n" +
                bodyOf(home, "private fun PixivWorkCard("),
            Regex("""\.veneraAvatarRing\(RoundedCornerShape\(tokens\.shape\.small\)\)""")
                .containsMatchIn(bodyOf(home, "private fun PixivWorkCard(")),
        )
        assertTrue(
            "顶栏那颗头像也要描边（它在头图的彩色区域上，没有边就只是一块颜色）：\n" +
                bodyOf(account, "internal fun GalleryPixivAccountButton("),
            bodyOf(account, "internal fun GalleryPixivAccountButton(")
                .contains("veneraAvatarRing(CircleShape)"),
        )
    }

    // ── 八、换一批：真换，且是"替换" ────────────────────────────────────────

    @Test
    fun `换一批替换整墙而不是追加，且换完滚回顶`() {
        val remix = bodyOfLocal(screen, "fun remixPixivRecommend()")

        assertTrue(
            "必须**替换**整墙（`pixivRecommend = page.works`）—— 追加的话按下去只多出十几张旧图，" +
                "读起来是「加载更多」而不是「换一批」：\n$remix",
            remix.contains("pixivRecommend = page.works"),
        )
        assertTrue(
            "不许出现 `pixivRecommend +`（那是续页的算法，不是换批的）：\n$remix",
            !remix.contains("pixivRecommend +"),
        )
        assertTrue(
            "换完要把墙滚回顶：留在原滚动位置的话，用户看到的是「中间某段忽然变成别的图」，读不出那一下：\n$remix",
            remix.contains("forYouGridState.scrollToItem(0)"),
        )
        assertTrue(
            "失败时必须保留原来那一墙并念原话（换批失败就清屏 = 按一下删了一屏内容）：\n$remix",
            remix.contains("pixivRecommendCaption =") &&
                !remix.contains("pixivRecommend = emptyList()"),
        )
    }

    @Test
    fun `换一批胶囊按腿判能不能摆，不给假把手`() {
        assertTrue(
            "能不能摆由调用点按腿判（站方腿看 `next_url`、种子腿看有没有种子）：\n" +
                screen.filter { it.value.contains("recommendRemixable =") }.joinToString("\n"),
            screen.any {
                it.value.contains("recommendRemixable = if (pixivRecommendStation)") &&
                    Regex("""pixivRecommendNext != null""").containsMatchIn(
                        screen.dropWhile { !it.value.contains("recommendRemixable =") }
                            .take(6).joinToString("\n") { it.value }
                    )
            },
        )
        assertTrue(
            "节头必须按 `recommendRemixable` 决定摆不摆（不是无条件摆一枚按了没反应的）：\n" +
                bodyOf(sections, "internal fun galleryHomeSections("),
            bodyOf(sections, "internal fun galleryHomeSections(")
                .contains("if (pixiv.recommendRemixable)"),
        )
        assertTrue(
            "`PixivHomeState` 那一袋里这三格默认 `false` / 空实现 —— 漏接时是「不摆」而不是「摆一枚死的」：\n" +
                sections.filter { it.value.contains("recommendRemix") }.joinToString("\n"),
            sections.any { it.value.contains("val recommendRemixable: Boolean = false") } &&
                sections.any { it.value.contains("val onRemixRecommend: () -> Unit = {}") },
        )
    }
}
