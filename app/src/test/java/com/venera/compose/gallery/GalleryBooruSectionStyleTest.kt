package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图库站「正在关注的画师」与「每日热门」两节对齐 pixiv 那两节风格的判据（2026-10-17 第三十轮）。
 *
 * 用户原话：「关注画师和每日热门 做成相同风格的ui」。
 * 这是长期挂着的 U1 遗留项（「图库站 ARTISTS/DAILY 两节补 surface = true」）的正式落地。
 *
 * ## 判据钉的是"两边同档"，不是"这两节长这样"
 *
 * 单看一边没有意义 —— 这条需求本身就是** parity **（两站读起来是同一个 UI）。
 * 所以每条断言都在两个文件里各量一次：`GalleryHomeSections.kt`（图库站）与
 * `GalleryPixivHome.kt`（pixiv 站），量的是同一颗表达式。
 * 刻意**不**合成一颗 composable：数据（[com.venera.compose.gallery.domain.FollowedArtistRef] vs
 * PixivUser）、手势（单击直接搜 + 长按进介绍页 vs 单击开站方页）、头像件都不同，
 * 同步的是形状。合成一颗的代价见 [GalleryArtistRow] 头注末尾那条 ⚠️。
 *
 * ## 量法（踩过的坑，照 `GalleryPixivHomeWiringTest` 那条教训）
 *
 * - 锚点钉 **`when` 分支**那两行（带 `->`），不能钉次序清单里那两行 ——
 *   `GalleryHomeSectionKey.ARTISTS,` 在 `galleryHomeSectionOrder` 里先出现一次，
 *   `to` 紧随其后就是同一张清单的下一行，那样切出来的"一节"只有两行声明；
 * - `codeLines` 返 `IndexedValue`，`indexOfFirst{…}` 是**位置**、`it.index` 是**行号**，
 *   混用切出空区间 ⇒ 判据红在空串上而代码是对的（已发作过一次）；
 * - 注释行一律不参与匹配（`codeLines` 过滤 `//`、`*`、块注释起始行）—— 这些文件里大段
 *   注释都写着同样的符号名，不过滤就等于判据读的是我们自己的解释。
 */
class GalleryBooruSectionStyleTest {

    private val sections = codeLines("com/venera/compose/gallery/ui", "GalleryHomeSections.kt")
    private val pixiv = codeLines("com/venera/compose/gallery/ui", "GalleryPixivHome.kt")
    private val artistRows = codeLines("com/venera/compose/gallery/ui", "GalleryArtistRows.kt")

    private fun codeLines(dir: String, name: String): List<IndexedValue<String>> {
        val file = RepoSources.kotlinFiles(dir).firstOrNull { it.name == name }
            ?: error("找不到 $name（目录 $dir，根：${RepoSources.root.absolutePath}）")
        return file.readLines().withIndex().filter { (_, line) ->
            val t = line.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }
    }

    /** 按**顶层函数体**切一段：从含 [header] 的那一行，到其后第一条顶格 `}`（含）。 */
    private fun bodyOf(lines: List<IndexedValue<String>>, header: String): String {
        val start = lines.firstOrNull { it.value.contains(header) }?.index
            ?: error("找不到 `$header`")
        val end = lines.firstOrNull { it.index > start && it.value == "}" }?.index
            ?: error("找不到 `$header` 的收尾括号")
        return lines.filter { it.index in start..end }.joinToString("\n") { it.value }
    }

    /** 按 `when` 分支切一节：从 `key ->` 那一行到下一个分支（不含）。 */
    private fun region(fromKey: String, toKey: String): String {
        val from = sections.firstOrNull { it.value.contains("$fromKey ->") }?.index
            ?: error("找不到分支 `$fromKey ->`")
        val to = sections.firstOrNull { it.index > from && it.value.contains("$toKey ->") }?.index
            ?: error("找不到分支 `$toKey ->`（$fromKey 那一节的收尾）")
        return sections.filter { it.index in from until to }.joinToString("\n") { it.value }
    }

    private fun countIn(body: String, regex: String): Int = Regex(regex).findAll(body).count()

    private val artists get() = region("GalleryHomeSectionKey.ARTISTS", "GalleryHomeSectionKey.DAILY")
    private val daily get() = region("GalleryHomeSectionKey.DAILY", "GalleryHomeSectionKey.FOR_YOU")
    private val forYou get() = region("GalleryHomeSectionKey.FOR_YOU", "GalleryHomeSectionKey.PIXIV_ARTISTS")

    // ── 一、两节都包进白卡，节尾间距与 pixiv 同档 ─────────────────────────────

    @Test
    fun `图库站两节都吃到 surface 白卡与同一档节尾间距`() {
        // 用户原话「做成相同风格的ui」的**外壳**那一半：节与 pixiv 那两节同形状
        // （实色卡 + 收窄的节尾间距），见 `GalleryHomeSection` 的 `surface` / `gapAfter`。
        listOf("画师" to artists, "每日热门" to daily).forEach { (name, body) ->
            assertTrue("$name 这一节必须吃到 surface = true：\n$body", body.contains("surface = true"))
            assertTrue(
                "$name 这一节的节尾间距必须与 pixiv 同档（space2 = 4dp，配卡底 8dp = 12dp）：\n$body",
                body.contains("gapAfter = VeneraTokens.spacing.space2"),
            )
            assertTrue(
                "$name 这一节仍是 wallAdjacent = false（宽窗收口那一档，不许跟着白卡改）：\n$body",
                body.contains("wallAdjacent = false"),
            )
        }
    }

    @Test
    fun `pixiv 那两节也是同一档：两站节尾间距逐字相同`() {
        // parity 的另一半：只把图库站改成"看起来像"不够 —— 间距值必须逐字同源，
        // 否则两站切来切去会读出 12dp 与 14dp 两种节距。
        val pixivArtists = regionOf("GalleryHomeSectionKey.PIXIV_ARTISTS", "GalleryHomeSectionKey.PIXIV_RANKING")
        val pixivRanking = regionOf("GalleryHomeSectionKey.PIXIV_RANKING", "GalleryHomeSectionKey.PIXIV_RECOMMEND")
        listOf("pixiv 画师" to pixivArtists, "pixiv 排行榜" to pixivRanking).forEach { (name, body) ->
            assertTrue("$name 这一节必须吃到 surface = true：\n$body", body.contains("surface = true"))
            assertTrue("$name 这一节的节尾间距必须是 space2：\n$body", body.contains("gapAfter = VeneraTokens.spacing.space2"))
        }
    }

    /** 同 [region]，但切的是 pixiv 侧那几节（同一个 `when` 块，锚点同形）。 */
    private fun regionOf(fromKey: String, toKey: String): String {
        val from = sections.firstOrNull { it.value.contains("$fromKey ->") }?.index
            ?: error("找不到分支 `$fromKey ->`")
        val to = sections.firstOrNull { it.index > from && it.value.contains("$toKey ->") }?.index
            ?: error("找不到分支 `$toKey ->`（$fromKey 那一节的收尾）")
        return sections.filter { it.index in from until to }.joinToString("\n") { it.value }
    }

    @Test
    fun `猜你喜欢那一节不许被顺手包进白卡`() {
        // 负向：这一节**就是脚下那面墙**（wallAdjacent = true），包卡会让节头比墙窄。
        // 本轮只点了「关注画师和每日热门」两节，第三节不动 —— 钉住这条防顺手。
        assertTrue("猜你喜欢仍不收口：\n$forYou", forYou.contains("wallAdjacent = true"))
        assertTrue("猜你喜欢不许吃 surface = true：\n$forYou", !forYou.contains("surface = true"))
    }

    @Test
    fun `贴墙那一节的节头补的是卡内衬那一档，左缘与有卡那两节对齐`() {
        // 用户 2026-10-18 第四批的读数：「画廊 pixiv 页的推荐文字和图标都还是没有对齐上面的排行榜」。
        // 有卡那两节的节头左缘 = 卡内衬 space6(12dp) + 节头自己那 4dp = 16dp；
        // 无卡（wallAdjacent）那一支从前一圈内衬都不加 ⇒ 节头只吃那 4dp，**偏左整 12dp**。
        // 补的就那 12dp：同一颗 space6，不另造数，也不去动节头那颗件（它两站共用）。
        val body = bodyOf(sections, "private fun GalleryHomeSection(")
        assertEquals(
            "无卡那一支必须自己成一分支（else if (wallAdjacent)）：\n$body",
            1,
            countIn(body, """\} else if \(wallAdjacent\) \{"""),
        )
        assertEquals(
            "补的内衬必须就是卡那一颗 space6：\n$body",
            1,
            countIn(body, """Modifier\.padding\(horizontal = VeneraTokens\.spacing\.space6\)"""),
        )
        // 顺序钉死：surface → wallAdjacent → 满宽无内衬。摆反了，贴墙那一节会去吃卡面（整节被读成一块面），
        // 而有卡那两节会掉进无内衬那一支（就是本轮报的那处偏左）。
        val iSurface = body.indexOf("if (surface) {")
        val iWall = body.indexOf("} else if (wallAdjacent) {")
        // 从 wallAdjacent 那一支`往后`找收尾那一支：这一形在这颗件里还有一处更早的合法用途（contentMaxWidth 那枚三元），
        // 全串取第一个会把那处读成第三支 —— 尺子当场红在自己写的顺序上。
        val iPlain = body.indexOf("} else {", iWall)
        assertTrue(
            "三支的次序必须是 surface → wallAdjacent → 无内衬（现量 $iSurface/$iWall/$iPlain）：\n$body",
            iSurface in 0 until iWall && iWall < iPlain,
        )
        // 对照组：上面那几枚不是"这颗件压根没读到"
        assertTrue("对照组失效：没读到 GalleryHomeSection 本体", body.contains("contentAlignment = Alignment.TopCenter"))
    }

    // ── 二、节头：每日热门补火焰图标，画师节仍是 Person ──────────────────────

    @Test
    fun `每日热门节头带上火焰图标，与画师节头成对`() {
        // 节头带图标是 pixiv 那两节的既有形态（画师 = Person、排行榜 = EmojiEvents），
        // 图库站这两节从前没有。火焰 = 热门（统计页「连续打卡」同枚）。
        assertTrue(
            "每日热门节头必须带火焰图标：\n$daily",
            daily.contains("icon = Icons.Outlined.LocalFireDepartment"),
        )
        assertTrue(
            "画师节头保持 Person（两站都是这一枚）：\n$artists",
            artists.contains("icon = Icons.Outlined.Person"),
        )
        // 节头不许长出 chevron：这两节都没有全量落地页（画师节全量在收藏页、
        // 每日热门的「查看全部」走 trailing 胶囊），画一枚点不动的箭头就是假把手。
        assertTrue("画师节头不许有 chevron：\n$artists", !artists.contains("chevron = true"))
    }

    // ── 三、画师行：白卡三道防抖 + 双端渐隐（与 pixiv 那一排同档） ────────────

    @Test
    fun `画师行把第二十二十一轮那两道防抖一并搬进白卡`() {
        val body = bodyOf(sections, "private fun GalleryArtistRow(")

        // ① 行高写死（治源头）：不写的话行高跟着这一行用到的字形走（拉丁 / CJK 差 1~2px）。
        assertEquals(
            "名字那一行必须写死 lineHeight（`TEXT_LINE_HEIGHT.em`）：\n$body",
            1,
            countIn(body, """lineHeight = TEXT_LINE_HEIGHT\.em"""),
        )
        // ② 外框下限（治传播）：懒行交叉轴报高 = 已测项最大高，不钉下限的话
        //    "视口里换了谁"就成了白卡的高度。
        assertEquals(
            "外框必须挂 `.heightIn(min = railHeight)`：\n$body",
            1,
            countIn(body, """\.heightIn\(min = railHeight\)"""),
        )
        assertEquals(
            "必须是 heightIn 而不是 height（`LineHeightStyle` 默认 Trim.None ⇒ 钉死会裁字尾）：\n$body",
            0,
            countIn(body, """\.height\(railHeight\)"""),
        )
        assertTrue(
            "railHeight 必须从 token 派生（头像直径 + space1 + 名字行高），不许落裸数字：\n$body",
            body.contains("artistAvatarSize + tokens.spacing.space1 + nameLineHeight") &&
                body.contains("(tokens.type.caption * TEXT_LINE_HEIGHT).toDp()"),
        )
        // ③ 禁过度滚动（治甩动）：这一排五六位，一次用力甩必然顶到尽头，
        //    平台默认 stretch 会把圆头像往卡面圆角外挤。
        assertEquals(
            "必须 overscrollEffect = null：\n$body",
            1,
            countIn(body, """overscrollEffect = null"""),
        )
    }

    @Test
    fun `画师行与 pixiv 那一排共用同一颗两端渐隐与同一档间距`() {
        val booru = bodyOf(sections, "private fun GalleryArtistRow(")
        val rail = bodyOf(pixiv, "internal fun PixivArtistRail(")

        // 渐隐是**一颗**（`horizontalRailFades`），不是两站各写一版：
        // 左端跟滚动位置、右端恒亮，container 传卡面色（这一节进了白卡）。
        assertEquals(
            "画师行必须走 `.horizontalRailFades(...)`（与 pixiv 那一排同一颗件）：\n$booru",
            1,
            countIn(booru, """\.horizontalRailFades\("""),
        )
        assertTrue(
            "渐隐底色必须是卡面色 surface（坐在白卡里，不是页面底 background）：\n$booru",
            booru.contains(".horizontalRailFades(railScroll, tokens.color.surface)"),
        )
        // 间距 4dp → 12dp：第十九轮用户在 pixiv 那边定的档（「拉大画师头像的间隔」）。
        assertEquals(
            "画师行间距必须是 space6(12dp)：\n$booru",
            1,
            countIn(booru, """Arrangement\.spacedBy\(tokens\.spacing\.space6\)"""),
        )
        // 尾部内衬换成渐隐那一档 16dp（"渐隐与尾部内衬成对"那条纪律）。
        assertEquals(
            "画师行必须配 hScrollEndContentPadding()：\n$booru",
            1,
            countIn(booru, """contentPadding = hScrollEndContentPadding\(\)"""),
        )
        // 旧端距退役：从前是 `PaddingValues(end = screenHorizontal)`。
        assertTrue(
            "旧的 screenHorizontal 端距必须已经退役（换成渐隐那一档 16dp）：\n$booru",
            !booru.contains("screenHorizontal"),
        )
        assertTrue(
            "画师行不许再出现 PaddingValues(（尾部内衬走 hScrollEndContentPadding）：\n$booru",
            !booru.contains("PaddingValues("),
        )
        // 与 pixiv 那一排逐字同档（同一颗件、同一个间距档、同一个内衬函数）。
        listOf(
            """\.horizontalRailFades\(""" to 1,
            """Arrangement\.spacedBy\(tokens\.spacing\.space6\)""" to 1,
            """contentPadding = hScrollEndContentPadding\(\)""" to 1,
            """overscrollEffect = null""" to 1,
            """lineHeight = TEXT_LINE_HEIGHT\.em""" to 1,
        ).forEach { (regex, expected) ->
            assertEquals(
                "pixiv 那一排也必须同档（$regex 期望 $expected 处）：\n$rail",
                expected,
                countIn(rail, regex),
            )
        }
    }

    // ── 四、海报行与骨架行：右端渐隐 + 尾部内衬 ──────────────────────────────

    @Test
    fun `每日热门的海报行与骨架行都配右端渐隐与尾部内衬`() {
        // 骨架那一条不只是"跟实卡对齐"：骨架 → 真数据切换那一瞬，如果只有实卡带渐隐，
        // 屏上就是"渐隐凭空冒出来 / 凭空消失"—— 那一帧本该是纯填充。
        listOf("GalleryPosterRow" to "private fun GalleryPosterRow(", "GalleryRowSkeleton" to "private fun GalleryRowSkeleton(")
            .forEach { (name, signature) ->
                val body = bodyOf(sections, signature)
                assertEquals(
                    "$name 必须配 `.hScrollEndFade(container = tokens.color.surface)`" +
                        "（这一行进了白卡，渐隐底色是卡面色不是页面底）：\n$body",
                    1,
                    countIn(body, """\.hScrollEndFade\(container = tokens\.color\.surface\)"""),
                )
                assertEquals(
                    "$name 必须配 `HScrollEndSpacer()`（`Row` 没有 contentPadding 参数，" +
                        "尾部占位与渐隐带宽同档 —— \"渐隐与尾部内衬成对\"那条纪律的 Row 形态）：\n$body",
                    1,
                    countIn(body, """HScrollEndSpacer\(\)"""),
                )
                // 普通横滑行按仓库既有口径（Favorites / Search / ComicDetail）只配右端：
                // 左端那一道要跟滚动位置动，得有自己的 LazyListState，而这里是裸 ScrollState 的 Row。
                assertTrue(
                    "$name 是裸 Row + horizontalScroll，不许挂左端渐隐：\n$body",
                    !body.contains("hScrollStartFade") && !body.contains("horizontalRailFades"),
                )
            }
    }

    // ── 五、共用件：头像补发丝描边，两处令牌只有一份声明 ──────────────────────

    @Test
    fun `头像共用件补上发丝描边，且排在底色之后`() {
        val body = bodyOf(artistRows, "internal fun GalleryArtistAvatar(")
        assertTrue(
            "共用头像件必须带 `.veneraAvatarRing(CircleShape)`（第二十三轮「给页面所有的头像都加一层" +
                "淡淡的描边」漏了这颗共用件，本轮进了白卡正撞上那条理由）：\n$body",
            body.contains(".veneraAvatarRing(CircleShape)"),
        )
        // 顺序：clip → background → ring。描边画的是"它内侧所有内容"之上的那一圈，
        // 排在底色之前会被底色盖住（等于没画）。
        val clip = body.indexOf(".clip(CircleShape)")
        val ring = body.indexOf(".veneraAvatarRing(CircleShape)")
        assertTrue("clip 与 ring 都要在：\n$body", clip >= 0 && ring >= 0)
        assertTrue(
            "描边必须排在 clip / background 之后（否则被底色盖住）：\n$body",
            clip < ring,
        )
    }

    @Test
    fun `两端渐隐件与行高倍数各只有一份声明，且是 internal`() {
        // "两站同档"的根基是这两样东西只有一处声明：任一边私有化，另一边就只能
        // 复制一份 —— 而复制品迟早会漂（这正是第二十一轮修完一边、另一边犯同一病的原因）。
        val fade = pixiv.filter { it.value.contains("fun Modifier.horizontalRailFades(") }
        assertEquals(
            "`horizontalRailFades` 必须只有一份声明（在 GalleryPixivHome.kt）：\n${fade.joinToString("\n") { it.value }}",
            1,
            fade.size,
        )
        assertTrue(
            "它必须是 internal（图库站画师行要用同一颗）：\n${fade.joinToString("\n") { it.value }}",
            fade.single().value.contains("internal fun Modifier.horizontalRailFades("),
        )
        val lineHeight = pixiv.filter { it.value.contains("const val TEXT_LINE_HEIGHT") }
        assertEquals(
            "`TEXT_LINE_HEIGHT` 必须只有一份声明：\n${lineHeight.joinToString("\n") { it.value }}",
            1,
            lineHeight.size,
        )
        assertTrue(
            "它必须是 internal（两站共用）：\n${lineHeight.joinToString("\n") { it.value }}",
            lineHeight.single().value.contains("internal const val TEXT_LINE_HEIGHT"),
        )
    }
}
