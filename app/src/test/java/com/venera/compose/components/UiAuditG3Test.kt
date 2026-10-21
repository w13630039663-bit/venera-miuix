package com.venera.compose.components

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-15 问题清单 G3 批的尺子：**节头的动作贴标题** + **收口只给非墙块**。
 *
 * 两条的形状不同，判据的写法就不同：
 * ① 是共用件（`feature/SectionHeader.kt`）里**恒一档**：动作与箭头都贴右缘
 *    （2026-10-17 用户拍板「贴右缘 + 箭头，两站都改成这样」，推翻了 2026-10-15 那份
 *    「动作贴标题」的探针口径，见 `_qa/ui-verify-2026-10-15/section_header_gap.txt` 那批读数
 *     —— 它们现在是"上一版口径"的证据，不是判据）；
 * ② 是接线（`contentMaxWidthFor`）—— 接线类按仓内纪律**数道数**，同时给墙那一族立**负向名单**：
 *    清单 §一-4 那句"墙本体不限宽"没有尺子守着的话，下一轮顺手把墙也收了口，
 *    症状会从"一行读不完"变成"节头比墙窄、左缘对不齐"，是拿一个新错位换一个旧错位。
 *
 * ⚠️ 手机档 `Compact` 恒 `null` 那一钉**这里不重复立** —— 已有 `WideScreenPolicyTest.phoneBandNeverGetsAContentWidthCap`，
 * 同一件事只许一把尺子（这条纪律本仓红过五次）。
 */
class UiAuditG3Test {

    private fun codeText(dir: String, name: String): String =
        RepoSources.kotlinFiles(dir)
            .first { it.name == name }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
            .joinToString("\n")

    /**
     * ① 行尾**恒一档**：真按钮／箭头／切换器都贴右缘（2026-10-17 第十九轮改判）。
     *
     * 这条尺子原本钉的是**上一版口径** —— "行尾有真按钮时不许 SpaceBetween，必须
     * `if (trailing == null) … else spacedBy(space4)`"，依据是 2026-10-15 那份探针读数
     * （平板上「正在关注的画师」与「另有 2 位」隔 1642px，手机上 577/639/670px）。
     *
     * 2026-10-17 用户拿稿拍板「① 贴右缘 + 箭头，两站都改成这样」⇒ 那一版口径被推翻，
     * 两档分叉随之作废。**不是把断言改松**，是把判据换到新契约上，并把旧分叉立成负向名单
     * （它复活就等于有人把 10-15 的读数又当成了现行口径）。
     *
     * 另加一条：切换器必须留在标题那一格内（用户「已关注和发现选择器放在画师标题的右侧」）——
     * 它若跑到右缘，`SpaceBetween` 会把标题、切换器、动作三样拉成两两隔开的一行。
     */
    @Test
    fun `节头一档排法：动作贴右缘，切换器跟标题`() {
        val header = codeText("com/venera/compose/feature", "SectionHeader.kt")
        assertEquals(
            "行尾排法恒一档：SpaceBetween 应恰好一处（2026-10-17 用户拍板「查看全部 贴右缘」）",
            1,
            Regex("""horizontalArrangement = Arrangement\.SpaceBetween""").findAll(header).count(),
        )
        assertEquals(
            "旧的两档分叉不许复活（那是 2026-10-15 的读数，已被用户推翻）",
            0,
            Regex("""horizontalArrangement = if \(trailing == null\)""").findAll(header).count(),
        )
        assertEquals(
            "切换器只许挂一处，且挂在标题那一格里（不是另起一行）",
            1,
            Regex("""titleTrailing\(\)""").findAll(header).count(),
        )
        val atTitle = header.indexOf("titleTrailing()")
        val atTrailing = header.indexOf("trailing()")
        assertTrue(
            "切换器必须先于行尾动作出现 ⇒ 它坐在标题那个 Row 内，不贴右缘",
            atTitle >= 0 && (atTrailing < 0 || atTitle < atTrailing),
        )
    }

    /**
     * ② 首页各节各自表态：收口的那几节逐个点名、墙那一节不收。
     *
     * 2026-10-17 从 2/1 改成 6/1：首页次序从一颗常量变成"内容域"的纯函数，多了一排内容域
     * 与 pixiv 那三节（都不是墙 ⇒ 都收口）。计数**按新排布重数过**（`grep -c` 现量 6 与 1），
     * 没有放宽成 `<=` —— 这一族放宽就等于"谁都可以不再表态"。
     *
     * 同日第二轮改 5/2：用户点名「pixiv 站的推荐应该做成和图库站猜你喜欢那种的，不是现在的卡片状」，
     * 于是 `section-pixiv-recommend` 从"一排卡"变成**脚下那面墙** ⇒ 它不再收口（收了会比墙窄、
     * 左缘对不齐）。两个数都是重新数出来的，不是把断言改松。
     *
     * 同日第三轮改 **4/2**：内容域那一排从"自己占一行的一节"搬进搜索那一行的左格
     * （用户稿「看下站源切换能不能做成这样」）⇒ 收口那一档少一节。`true` 仍是两节（两档各一面主墙）。
     * 又是重新 `grep -c` 数出来的，不是把断言改松。
     */
    @Test
    fun `首页各节对收口必须逐个表态`() {
        val sections = codeText("com/venera/compose/gallery/ui", "GalleryHomeSections.kt")
        // 必填参数：三处调用点各写一次，谁忘了就编译不过（所以这里只数次数，不改判成"至少一处"）。
        assertEquals("`wallAdjacent = false` 应恰好四节（图库站两节 + pixiv 两节；域那一排已搬进搜索行）", 4, Regex("""wallAdjacent = false""").findAll(sections).count())
        assertEquals(
            "`wallAdjacent = true` 应恰好两节（两档域各一面主墙：猜你喜欢 / pixiv 推荐）",
            2,
            Regex("""wallAdjacent = true""").findAll(sections).count(),
        )
        // 对照：外壳真的去吃现成口径，而不是自己写一个数。
        //
        // 2026-10-20 改判：取宽的入口从 `LocalConfiguration.current.screenWidthDp.dp`
        // 换成了 `LocalWindowWidthDp`（写法 A：桌面复用安卓大屏 UI 的那道闸门）。
        // **判据的意图一字未改** —— 它钉的始终是「这一行吃现成判据、不自造数字」，
        // 形状仍是 `contentMaxWidthFor(wideScreenLayoutMode(<取宽>))` 三层嵌套，
        // 只是第三层由 AndroidX 那颗件换成了跨端的读口。
        // ⚠️ 别把这条改成"包含 contentMaxWidthFor 就算过" —— 那会让
        // 「自己写个数绕过判据」重新变成绿的，正是这条尺子当初要抓的缺陷。
        assertEquals(
            1,
            Regex("""contentMaxWidthFor\(wideScreenLayoutMode\(LocalWindowWidthDp\)\)""")
                .findAll(sections).count(),
        )
        assertTrue("收口之后要居中，否则 720 那一档会贴左缘空出右半屏", sections.contains("contentAlignment = Alignment.TopCenter"))
    }

    /**
     * 每一池交给大图页的那一处，都必须是**屏上那一池**（2026-10-17 补，同日第二轮改形状）：
     * 排行榜那排点进去却把推荐的池子设进队列，读起来就是「卡上这张、滑过去全是别的」。
     * 数出现次数而不是只查"有没有" —— 只查有的话，两节写成同一池也照样绿。
     *
     * 第二轮之后推荐那一池**走首页主墙**（用户点名要"猜你喜欢那种"），所以它的交接点
     * 从 `GalleryHomeSections.kt` 搬到了 `GalleryScreen.kt` 的 `onOpen`：
     * 那里从前写死 `forYouWall`，切到 Pixiv 域就会变成"卡是 pixiv 的、滑过去全是 booru 的" ——
     * 与上面那条是同一族缺陷，所以两道判式一起钉：**交接点引的是读数那一份池子，且旧的那句零残留**。
     */
    @Test
    fun `pixiv 两节各自把整池交给大图页`() {
        val sections = codeText("com/venera/compose/gallery/ui", "GalleryHomeSections.kt")
        assertEquals("排行榜那一节要交 ranking 那池", 1,
            Regex("""pixiv\.onOpen\(pixiv\.ranking, post\)""").findAll(sections).count())
        assertEquals("推荐那一池已经改走主墙，节里不许再留它的交接点", 0,
            Regex("""pixiv\.onOpen\(pixiv\.recommend""").findAll(sections).count())
        assertEquals("不许再有把裸 onOpen 交出去的那一处", 0,
            Regex("""onOpen = pixiv\.onOpen,""").findAll(sections).count())
        val screen = codeText("com/venera/compose/gallery/ui", "GalleryScreen.kt")
        assertEquals("首页那面墙的队列必须是屏上这一池（由读数给）", 1,
            Regex("""GalleryViewerQueue\.set\(homeWallReading\.wall\.cards\.map \{ it\.post \}\)""")
                .findAll(screen).count())
        assertEquals("写死猜你喜欢那一池的旧交接点不许有残留", 0,
            Regex("""GalleryViewerQueue\.set\(forYouWall\.cards""").findAll(screen).count())
    }

    /**
     * 首页那面墙的读数只有一处来源（2026-10-17 第二轮）：页里不许再直接读 `fvm`。
     *
     * 立它的理由不是洁癖 —— 这一面墙现在由两档内容域共用，而 `fvm` 那本账（按站各几张、
     * 第几页、排掉几张已收藏）**只属于图库站**。页里留着直接的 `fvm.` 读数，Pixiv 域就会
     * 出现"屏上是 pixiv 那一池、页尾念 booru 两站的张数"这种最难的假读数。
     */
    @Test
    fun `首页那面墙只读一份读数，不在页里翻对岸的账`() {
        // 走 codeText 同一把口径：注释行不参与匹配（那一页里"从前这里是 fvm.refresh()"这类
        // 说明留了好几处，按原文扫会把解释当成违例）。
        val all = codeText("com/venera/compose/gallery/ui", "GalleryForYouScreen.kt")
        val pageStart = all.indexOf("internal fun GalleryForYouPage(")
        val factoryStart = all.indexOf("internal fun forYouWallReading(")
        assertTrue("两处锚点都要在（量具失效就等于没有判据）", pageStart >= 0 && factoryStart > pageStart)
        val page = all.substring(pageStart, factoryStart)
        assertEquals(
            "页里直接读 fvm 的地方（读数该从 GalleryHomeWallReading 拿）：\n" +
                page.lines().filter { it.contains("fvm.") }.joinToString("\n"),
            0,
            page.lines().count { it.contains("fvm.") },
        )
        // 这三样**从读数拿**这一条只在这里钉（来源）；"它们各摆在哪一格"由
        // [空档与加载档不许把域切换那一排摘走] 钉（形状），两处判式不同、不重复措辞。
        assertTrue("页尾要从读数取", page.contains("-> reading.footer"))
        assertTrue("按站提示要从读数取", page.contains("galleryNotices(reading.notices)"))
        assertTrue("加载档也要从读数取", page.contains("reading.pending"))
    }

    /**
     * 空档与加载档**只换"卡片那一格"**，不许把 chrome / 头图 / 首页那几节一起摘走
     * （2026-10-17 用户「现在切换到 pixiv 会这样」+ 截图：Pixiv 那一轮没回图时整页只剩一句
     * "稍后再刷一次"，而「图库站 / Pixiv」那颗切换器就住在节里 —— 用户被关在了 Pixiv 域）。
     *
     * 判式数道数而不是查"有没有"：只查有的话，再补一条带自己那套 header 的分支也照样绿，
     * 而"两条形各摆各的节"正是这次的病根。两条旧判式各数一次零残留，
     * 免得将来有人把整页换脸那种写法挪个名字加回来。
     */
    @Test
    fun `空档与加载档不许把域切换那一排摘走`() {
        val all = codeText("com/venera/compose/gallery/ui", "GalleryForYouScreen.kt")
        val pageStart = all.indexOf("internal fun GalleryForYouPage(")
        val nextDeclaration = all.indexOf("internal class ForYouEmptyCopy(")
        assertTrue("两处锚点都要在（量具失效就等于没有判据）", pageStart >= 0 && nextDeclaration > pageStart)
        val body = all.substring(pageStart, nextDeclaration)
        assertEquals("墙只有一条形状：整页换脸那两个居中 Box 不许回来", 1,
            body.lines().count { it.contains("GalleryCardsGrid(") })
        assertEquals("首页那几节只交给那一颗墙（多一处就是又起了一条不带节的分支）", 1,
            body.lines().count { it.contains("sections = sections") })
        assertEquals("chrome 只在那一条形状里", 1,
            body.lines().count { it.contains("chrome?.invoke()") })
        assertEquals("头图只在那一条形状里", 1,
            body.lines().count { it.contains("hero?.let") })
        assertTrue("没卡时卡片那一格给空态", body.contains("else -> emptySlot"))
        assertTrue("还没判过档时那一格给环", body.contains("reading.pending -> loadingSlot"))
        assertTrue("有卡时页尾仍从读数取", body.contains("cards.isNotEmpty() -> reading.footer"))
        assertEquals("空态只许一处，且它就在墙尾那一格", 1,
            body.lines().count { it.contains("VeneraEmptyView(") })
        assertEquals("整页换脸的旧判式零残留", 0,
            body.lines().count { it.contains("cards.isEmpty() ->") })
        assertEquals("整页换脸的旧判式零残留", 0,
            body.lines().count { it.contains("cards.isEmpty() && reading.pending") })
    }
    /**
     * Pixiv 推荐那一池：种子取回的图**必须真的进池**（2026-10-17 真机读数抓到的缺陷）。
     *
     * 病：那一行写成了 `found.take(N)` —— 表达式算完就丢，`picked` 恒空，
     * 于是 Pixiv 域的墙**永远**是空的，而屏上还配了一句说得像真的的空态（"这一轮没回图"）。
     * 单条标识符判式（"take 在不在"）看不见这种死分支，所以钉**整串赋值表达式**，
     * 并把"独立成一行、结果没人接"的那个形状钉成零。
     */
    @Test
    fun `pixiv 推荐那一池的种子必须真的进池`() {
        val screen = codeText("com/venera/compose/gallery/ui", "GalleryScreen.kt")
        assertEquals("每枚种子的取数要累加进 picked（取完就丢 = 这一池恒空）", 1,
            screen.lines().count { it.contains("picked += found.take(GalleryPixivHome.RECOMMEND_PER_SEED)") })
        assertEquals("不许有结果没人接的那一行", 0,
            screen.lines().count { it.startsWith("found.take(") })
        assertEquals("整池要按 uid 去重后交给 pixivRecommend", 1,
            screen.lines().count { it.contains("pixivRecommend = picked.distinctBy { it.post.uid }") })
    }

    /**
     * 域切换那一排的形状（2026-10-17 第二轮带稿点名「现在是左对齐太浪费空间了」+ 第三轮
     * 「看下站源切换能不能做成这样」= 摆进搜索那一行的左格）。
     *
     * 各条钉的是：
     *  - **单容器**：只有容器那一层涂 `surfaceVariant`，**格子自己一颗都不涂**（旧版每枚各自涂，
     *    两枚在抢同一行的注意力，也就读不出"哪个是选中的"）；
     *  - **底色会滑**：底色是容器 `drawBehind` 里画的一枚矩形 + 两条 `animateFloatAsState`
     *    （左边界与宽），不是各格自涂（那样切换是"换色"，不是稿上那句"滑动底色"）；
     *  - **位置只从"量宽 + 前缀和"来**：2026-10-17 真机截图上底色停在**未选中**那一格
     *    （左格是 `primaryContainer` 那一档底，右格字的颜色却是 `onPrimaryContainer` 那一档红），
     *    所以 `parentCoordinates` / `localBoundingBoxOf` 这条量法**不许回来** —— 它是第二套坐标，
     *    而格子是 Row 里依次摆的，前缀和是恒等式；
     *    ⚠️ 2026-10-17 第二十七轮（用户「点到 pixiv 里面的胶囊不会过去」）才真正落地：
     *    此前那条**断言一直要求 `localBoundingBoxOf` 在场**，与上面这句 KDoc 自相矛盾 ——
     *    判据钉的是没修的那一版，所以它一直是绿的。现在断言改钉"每格报自己的尺寸 + 前缀和"，
     *    并把父子坐标那一条换成**负向尺**（回来就红）。
     *  - **按字长收**：第三轮起它住在搜索条左边那一格 ⇒ 外框**不许**再 `fillMaxWidth`
     *    （第二轮那句"居中"钉的是外层 `Box(fillMaxWidth, Center)`，而 `contentAlignment = Center`
     *    这颗文件里**每格**都在用 —— 那只钉得住"有一句 Center"，第三轮把外框摘掉它照样绿。
     *    所以这轮改判成负向尺：数 `fillMaxWidth` 的**道数**，零才算收住）。
     * 高度吃现成的 `segmentedHeight` 那一档，本文件里出现裸 `dp` 就是自造数字。
     */
    @Test
    fun `域切换是下拉，不铺满也不自造数字，材质跟着全局那一轴`() {
        // 2026-10-17 第九轮先改成「胶囊 + 下拉」，同日晚些时候用户又改回来：
        // 「站源选择器还是改回前面版本的分段切换器吧，这样好看点」。
        // 判据钉的四件事两版都适用：
        //  ① 按字长收（这一行右边是搜索条，主件在那儿，左边不许铺满）；
        //  ② 条目表取伴生里那一份实例（每帧新建会把状态洗一遍）；
        //  ③ 高度吃现成的 `segmentedHeight`（与搜索条同档 = 那一行唯一的对齐契约）；
        //  ④ 不许出现裸 dp（数字一律走 token）。
        // 另加两条这一族的样子：
        //  ⑤ 材质必须走**同一条全局轴**（`veneraGlassEnabled()` + `veneraGlassSurface`），
        //     且实色档要落回 `surfaceVariant` 轨道 —— 那正是用户说"好看"的那一版，
        //     "全局切回 MD3 时这一排跟着回退"是靠这一条守的；
        //  ⑥ 必须是**真分段器**：底色画在 `drawBehind` 里、位置由每格报上来的矩形给、
        //     点另一档真回调 —— 这三样缺一条就退化成"两枚看着能点的字"。
        val raw = RepoSources.kotlinFiles("com/venera/compose/gallery/ui")
            .first { it.name == "GalleryPixivHome.kt" }
            .readLines()
        val from = raw.indexOfFirst { it.contains("internal fun GalleryHomeDomainChips(") }
        val to = raw.indexOfFirst { it.contains("internal fun PixivArtistTabRow(") }
        assertTrue("两处锚点都要在（量具失效就等于没有判据）", from >= 0 && to > from)
        val body = raw.subList(from, to)
        val code = body.map { it.trim() }.filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
        val text = code.joinToString("\n")
        assertEquals(
            "这一排不许再铺满整行（它坐在搜索条左边那一格）：\n" +
                code.filter { it.contains("fillMaxWidth") }.joinToString("\n"),
            0,
            code.count { it.contains("fillMaxWidth") },
        )
        assertTrue("条目表要取伴生里那一份实例（每帧新建会把状态洗一遍）", text.contains("GalleryHomeDomain.HOME_OPTIONS"))
        assertTrue("高度要吃现成的分段器那一档（与搜索条同档）", text.contains("tokens.spacing.segmentedHeight"))
        // ⑤ 材质：跟随全局轴 + 实色档落回原来的轨道色。
        assertTrue("材质要读全局那一轴（veneraGlassEnabled）", text.contains("veneraGlassEnabled()"))
        assertTrue("玻璃档要真挂上去（veneraGlassSurface）", text.contains("veneraGlassSurface("))
        assertTrue(
            "实色档要落回 surfaceVariant 轨道（那是用户点名'好看'的前一版）",
            text.contains("tokens.color.surfaceVariant"),
        )
        // ⑥ 真分段器：滑动胶囊那三样。
        assertTrue("选中胶囊要画在 drawBehind 里（不是叠一块底）", text.contains("drawBehind"))
        assertTrue("胶囊要滑动而不是硬切", text.contains("animateFloatAsState"))
        assertTrue("点另一档要真回调", text.contains("onPick(item)"))
        // ⑥-b 位置只从「量宽 + 前缀和」来（第二十七轮，用户「点到 pixiv 里面的胶囊不会过去」）。
        assertTrue(
            "每格必须报**自己的尺寸**（`onSizeChanged`），不牵涉父坐标：\n$text",
            text.contains("onSizeChanged {"),
        )
        assertTrue(
            "胶囊左缘必须是前缀和（`.take(selectedIndex).fold(0f)`）—— 格子是 Row 里依次摆的，" +
                "这是一条恒等式，整条路上没有坐标换算：\n$text",
            text.contains(".take(selectedIndex).fold(0f)"),
        )
        assertEquals(
            "`parentCoordinates` / `localBoundingBoxOf` 这条父子坐标量法**不许回来**" +
                "（2026-10-17 真机上正是它让底色停在未选中那一格）：\n" +
                code.filter { it.contains("localBoundingBoxOf") || it.contains("parentCoordinates") }
                    .joinToString("\n"),
            0,
            code.count {
                it.contains("localBoundingBoxOf") || it.contains("parentCoordinates")
            },
        )
        val rawDp = code.filter { Regex("""[0-9]+(\.[0-9]+)?\.dp""").containsMatchIn(it) }
        assertTrue("这一排不许出现裸 dp：\n$rawDp", rawDp.isEmpty())
    }

    /** ③ 墙那一族恒不被收口（负向名单）+ 接线道数只许加长。 */
    @Test
    fun `墙本体恒不参与宽度收口`() {
        val walls = listOf(
            "GalleryScreen.kt",          // 首页搜索墙 + 日榜二级页墙 + 共用件 GalleryCardsGrid
            "GalleryForYouScreen.kt",    // 首页主墙（猜你喜欢）
            "GalleryFavoritesBody.kt",   // 画廊收藏墙
            "GalleryHistoryScreen.kt",   // 画廊历史墙
            "GalleryReverseResults.kt",  // 反搜结果墙
        )
        val offenders = walls.filter { name ->
            codeText("com/venera/compose/gallery/ui", name).contains("contentMaxWidthFor")
        }
        assertTrue("这几面墙被套上了收口（清单 §一-4：墙本体不限宽）：$offenders", offenders.isEmpty())
        // 正向对照：**名单内的每一颗都还得在**（这才是"只许加长"的真形状 —— 用 assertEquals 冻住整张表
        // 会让"合法新增一颗屏"变红，那种红会把人教成"别加尺子"，而不是"加完来登记"）。
        // 新增接线照常欢迎，但要在这里补一行并按 T1a 那把尺子数一遍道数。
        val consumers = RepoSources.allMainLines()
            .filter { !it.first.endsWith("components/WideScreenPolicy.kt") }
            .filter { it.third.contains("contentMaxWidthFor(") }
            .map { it.first }
            .distinct()
        listOf(
            "com/venera/compose/feature/settings/SettingsComponents.kt",
            "com/venera/compose/feature/settings/SettingsHome.kt",
            "com/venera/compose/gallery/ui/GalleryArtistProfileScreen.kt",
            "com/venera/compose/gallery/ui/GalleryHomeSections.kt",
        ).forEach { missing ->
            assertTrue("这一处收口接线没了：$missing（掉一处 = 那一屏重新变成满宽拉伸）", consumers.contains(missing))
        }
    }
}
