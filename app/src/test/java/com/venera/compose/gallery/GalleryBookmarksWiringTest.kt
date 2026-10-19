package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「收藏 hub + Pixiv 云端书签」这一轮的接线尺子（源码层面，2026-10-17 第三十一轮）。
 *
 * 用户两条原话：
 * ① 「在搜索和历史记录两个圆形按钮之间，新增一个书签按钮」；
 * ② 「按照这个方式去做收藏」—— 稿上两颗卡：本地收藏（♡）/ Pixiv 书签（🔖），
 *    卡下一行说明把两套账的边界说死。
 *
 * ## 这批尺子拦的都是**静默**缺陷
 *
 * - 端点三枚参数少任何一枚，站方回 400/191/401 —— 屏上只是"书签没取上"，
 *   读不出是参数错了（这一族本仓犯过：`user/recommended` 缺 `filter=for_android` 量到 400/191）；
 * - 顶栏那枚位置放错（放历史右边、或放搜索左边）照样能点，只是不符合用户点名的那句原话；
 * - 两条路由漏了 `tabIndex` / `currentTab` 的映射 ⇒ 进了子页底栏消失，页面照常渲染；
 * - 书签页闸门少一道 ⇒ 甩到尾连发三四跳；不去重 ⇒ 同一张图连着出现两遍。
 *
 * ⚠️ 注释行一律不参与匹配（`codeLines` 那把口径）：这些文件里大段注释都写着同样的符号名，
 * 不过滤就等于判据读的是我们自己的解释。
 */
class GalleryBookmarksWiringTest {

    private val client = codeLines("com/venera/compose/gallery/data", "PixivAppClient.kt")
    private val parse = codeLines("com/venera/compose/gallery/data", "PixivAppParse.kt")
    private val business = codeLines("com/venera/compose/gallery/data", "GalleryBusinessApi.kt")
    private val adapters = codeLines("com/venera/compose/gallery/data/android", "GalleryAdaptersAndroid.kt")
    private val hub = codeLines("com/venera/compose/gallery/ui", "GalleryBookmarksHubScreen.kt")
    // 2026-10-19 拍板 ⑦：独立那一屏收进了 **pixiv 账号页**的收藏段（共用件 = `PixivTwoLegWall`）。
    // 这一族判据问的是"那面墙怎么翻页、按什么去重、跟谁共用一把尺"，与它住在哪颗文件无关 ⇒ 跟着搬锚点。
    private val bookmarks = codeLines("com/venera/compose/gallery/ui", "PixivAccountScreen.kt")
    // 2026-10-19 起"切屏要留住数据" ⇒ 墙的三道闸门与在途状态搬进进程级会话仓；
    // 这一族判据问的仍是同一件事，只是第三道闸门的读数现在从仓里来。
    private val sessionStore = codeLines("com/venera/compose/gallery/ui", "PixivAccountSessionStore.kt")
    private val screen = codeLines("com/venera/compose/gallery/ui", "GalleryScreen.kt")
    private val navigation = codeLines("com/venera/compose/feature", "Navigation.kt")

    private fun codeLines(dir: String, name: String): List<IndexedValue<String>> {
        val file = RepoSources.kotlinFiles(dir).firstOrNull { it.name == name }
            ?: error("找不到 $name（目录 $dir，根：${RepoSources.root.absolutePath}）")
        return file.readLines().withIndex().filter { (_, line) ->
            val t = line.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }
    }

    private fun countIn(lines: List<IndexedValue<String>>, regex: String): Int =
        Regex(regex).findAll(lines.joinToString("\n") { it.value }).count()

    private fun firstIndexOf(lines: List<IndexedValue<String>>, fragment: String): Int =
        lines.firstOrNull { it.value.contains(fragment) }?.index
            ?: error("找不到 `$fragment`")

    // ── 一、端点：三枚参数一枚都不能少 ───────────────────────────────────────

    @Test
    fun `书签端点带齐三枚参数，而档位由调用点给`() {
        val body = client.filter {
            it.index >= firstIndexOf(client, "suspend fun bookmarkIllusts(")
        }.takeWhile { it.value != "}" }.joinToString("\n") { it.value }

        // 这三枚是同族端点（following / recommendedUsers）现量出来的口径：
        // 少 filter 回 400/191、少 user_id 按匿名回 401、少 restrict 只回一部分。
        listOf(
            """/v1/user/bookmarks/illust""" to "端点地址",
            """addQueryParameter\("filter", "for_android"\)""" to "filter（缺它回 400/191）",
            """addQueryParameter\("user_id", userId\)""" to "user_id（自己的号，书签是账号维度）",
            """addQueryParameter\("restrict", restrict\.param\)""" to "restrict（2026-10-19 起是形参：写死哪一档，另一档就永远读不到）",
        ).forEach { (regex, what) ->
            assertTrue("书签端点必须带$what：\n$body", Regex(regex).containsMatchIn(body))
        }
        // "只读公开"这条界从**端点**搬到了**调用点**（账号页要读非公开，端点不能再替所有人做主）。
        // 2026-10-19 拍板 ⑦ 之后独立那一屏已撤，现在读这一腿的调用点就是账号页的收藏段 ——
        // 所以这里钉的是"档位在账号页里是**交出去**的"：写死任何一档，另一档就永远读不到。
        // 「看别人的画师页只许走公开」由 `PixivAccountFeedTest` 那一颗钉（同一件事不许两把尺）。
        val wall = bookmarks.joinToString("\n") { it.value }
        assertTrue(
            "账号页收藏段必须把档位递给端点：\n$wall",
            wall.contains("currentWorks.bookmarkedIllusts(bearerOf(account), userId, restrict, next)"),
        )
        assertTrue(
            "账号页里不许出现写死的档位字面量：\n$wall",
            !wall.contains("PixivRestrict.Public") && !wall.contains("PixivRestrict.Private"),
        )
        // 翻页只认站方的 next_url（它自带 max_bookmark_id 游标），不自己拼 offset。
        assertTrue(
            "翻页只认 nextUrl（自己拼 offset 就是猜站方分页）：\n$body",
            body.contains("PixivAppParse.illustrationBookmarks(execute(url, bearer))"),
        )
    }

    @Test
    fun `书签答复复用同一颗行解析，不另起一份`() {
        // 与推荐 / 榜单同一个 illusts[] 形状（无 rank）⇒ 复用 page()。
        // 另写一份解析器的话，站方哪天加一档字段就得记着改两处（本仓为这类分叉付过账）。
        val declAt = parse.firstOrNull { it.value.contains("fun illustrationBookmarks(") }?.index
            ?: error("找不到 illustrationBookmarks")
        // 声明与它的函数体是两行（`= page(...)` 换行排版）⇒ 看这两行的窗口。
        val window = parse.filter { it.index in declAt..(declAt + 1) }.joinToString("\n") { it.value }
        assertTrue(
            "书签解析必须落回 page()，且 withRank = false（书签这一腿没有名次）：\n$window",
            window.contains("fun illustrationBookmarks(") &&
                window.contains("page(body,") && window.contains("withRank = false"),
        )
        assertEquals(
            "行解析只能有一颗（page 是私服，三个入口都走它）",
            1,
            countIn(parse, """private fun page\("""),
        )
    }

    @Test
    fun `契约与 Android 实现都接上了书签这一跳`() {
        // 契约层（gallery/data）与实现层（gallery/data/android）各一半：
        // 少契约 = UI 调不到；少实现 = 编译过得去、一跑就 notWired。
        assertTrue(
            "GalleryPixivWorks 必须有 bookmarkedIllusts（票据 + userId + restrict + nextUrl 四枚）：\n" +
                business.joinToString("\n") { it.value },
            business.any { it.value.contains("suspend fun bookmarkedIllusts(") } &&
                business.any { it.value.contains("bearer: String,") } &&
                business.any { it.value.contains("userId: String,") } &&
                business.any { it.value.contains("restrict: PixivRestrict,") } &&
                business.any { it.value.contains("nextUrl: String? = null,") },
        )
        assertTrue(
            "AndroidGalleryPixivWorks 必须把它委托给 PixivAppClient：\n" +
                adapters.joinToString("\n") { it.value },
            adapters.any { it.value.contains("appClient.bookmarkIllusts(bearer, userId, restrict, nextUrl)") },
        )
    }

    // ── 二、hub：两颗卡 + 那行说明（用户稿原文） ─────────────────────────────

    @Test
    fun `收藏 hub 是两颗卡，标题副标题逐字对稿`() {
        val body = hub.joinToString("\n") { it.value }
        // 颗数：正好两颗（第三颗卡 = 又一个入口，而入口一多"图存在哪儿"就又说不清了）。
        // 数 `icon = Icons.Outlined.` 而不是 `HubCard(`：后者会把那颗私有件的声明也数进去。
        assertEquals("hub 上必须正好两颗卡", 2, countIn(hub, """icon = Icons\.Outlined\."""))
        assertTrue("卡件本身要在这儿：\n$body", body.contains("private fun HubCard("))
        // 左卡：本地收藏。
        assertTrue("左卡标题：\n$body", body.contains("title = \"本地收藏\""))
        assertTrue("左卡副标题：\n$body", body.contains("subtitle = \"Venera 保存的图片\""))
        assertTrue("左卡图标是空心爱心：\n$body", body.contains("Icons.Outlined.FavoriteBorder"))
        // 右卡：Pixiv 书签。
        assertTrue("右卡标题：\n$body", body.contains("title = \"Pixiv 书签\""))
        assertTrue("右卡副标题：\n$body", body.contains("subtitle = \"Pixiv 账号的云端书签\""))
        assertTrue("右卡图标是空心书签：\n$body", body.contains("Icons.Outlined.BookmarkBorder"))
        // 卡下那行说明：**逐字**对稿（改一个字就是改用户对"图存在哪儿"的理解）。
        assertTrue(
            "卡下说明必须逐字是稿上那一句：\n$body",
            body.contains("本地收藏继续统一展示 Yande、Gelbooru、Safebooru 和已接入的 Pixiv 作品；") &&
                body.contains("Pixiv 书签则单独读取账号数据，不与本地收藏混为一谈。"),
        )
        // 两颗卡都可点，且按压反馈走 card 那一档（VeneraCard 默认 None ⇒ 不传就是假开关）。
        assertTrue(
            "卡必须挂 pressFeedback = VeneraPressFeedback.card：\n$body",
            body.contains("pressFeedback = VeneraPressFeedback.card"),
        )
    }

    // ── 三、顶栏那枚 🔖：落在搜索与历史之间 ─────────────────────────────────

    @Test
    fun `书签按钮在顶栏搜索与浏览历史之间，且调的是 onOpenBookmarks`() {
        // 用户原话「在搜索和历史记录两个圆形按钮之间」。位置判据用**行号**比：
        // 三枚都在 actions = { } 里，按源码顺序即是屏上从左到右的顺序。
        val searchToggle = firstIndexOf(
            screen,
            "imageVector = if (svm.active) Icons.Outlined.Close else Icons.Outlined.Search,",
        )
        val bookmark = firstIndexOf(screen, "imageVector = Icons.Outlined.BookmarkBorder,")
        val history = firstIndexOf(screen, "imageVector = Icons.Outlined.History,")

        assertTrue(
            "顺序必须是 搜索 → 书签 → 历史，实际是 search=$searchToggle bookmark=$bookmark history=$history",
            searchToggle < bookmark && bookmark < history,
        )
        // 点它必须真调 onOpenBookmarks（交出去的是导航动作，不是页内状态）。
        val pill = screen.filter { it.index in (bookmark - 12)..bookmark }.joinToString("\n") { it.value }
        assertTrue("书签那一枚的 onClick 必须调 onOpenBookmarks()：\n$pill", pill.contains("onOpenBookmarks()"))
        // 参数有默认值：其余调用点（若有）不传即零改动。
        assertTrue(
            "onOpenBookmarks 必须有默认值（加性参数）：\n" + screen.joinToString("\n") { it.value },
            screen.any { it.value.contains("onOpenBookmarks: () -> Unit = {},") },
        )
    }

    // ── 四、路由：两条都登记成画廊 Tab 的子页 ───────────────────────────────

    @Test
    fun `两条路由里只剩 hub 那条，书签那颗改跳账号页`() {
        val body = navigation.joinToString("\n") { it.value }
        val route = "GalleryBookmarksHubRoute"
        // 两处映射各一次：少一处就会出现"底栏点亮了、切页动画却没有方向"（tabIndex 头注那条）。
        // ⚠️ 括号必须转义：`hasRoute(...)` 里的 `(` 在正则里是分组元字符，
        // 不转义就会去匹配"hasRouteGallery…"（没有括号的那串）—— 数出来恒 0，判据自己坏掉。
        assertEquals(
            "$route 必须在 tabIndex 与 currentTab 两处都登记（现在 ${countIn(navigation, """hasRoute\($route::class\)""")} 处）",
            2,
            countIn(navigation, """hasRoute\($route::class\)"""),
        )
        assertTrue("$route 必须声明成 @Serializable data object：\n$body", body.contains("@Serializable data object $route"))
        assertTrue("$route 必须有 composable 注册：\n$body", body.contains("composable<$route>"))
        // 旧那条独立路由**整条撤掉**：它现在是账号页的一个档位，两条路并存 = 同一件事两个入口两套读数。
        // 判式扫的是**整个 main**，不是只扫 Navigation.kt —— 注释里留一个名字不算残留，真引用才算。
        val leftovers = RepoSources.kotlinFiles("com/venera/compose").filter { file ->
            file.readLines().any { line ->
                val t = line.trim()
                val code = !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
                code && t.contains("GalleryPixivBookmarksRoute")
            }
        }
        assertTrue("GalleryPixivBookmarksRoute 应当零残留，实测：${leftovers.map { it.name }}", leftovers.isEmpty())
        // hub 上两颗卡各走各的路：本地收藏去 FAVORITES 主 Tab（不复制一面墙到画廊侧），
        // pixiv 书签那颗开**账号页 Activity** 并落在收藏段。
        val hubBlock = navigation.filter {
            it.index >= firstIndexOf(navigation, "composable<GalleryBookmarksHubRoute>")
        }.takeWhile { it.value != "}" }.joinToString("\n") { it.value }
        assertTrue("本地收藏那颗必须导航去 FavoritesRoute：\n$hubBlock", hubBlock.contains("navController.navigate(FavoritesRoute)"))
        assertTrue(
            "pixiv 书签那颗必须开账号页的收藏段（交的是档位键，不是对象 —— rememberSaveable 那族坑）：\n$hubBlock",
            hubBlock.contains("openPixivAccountPage(PixivAccountSectionEntry.BOOKMARKS)"),
        )
    }

    // ── 五、书签页：三道闸门 + 去重 + 同一把过滤判据 ─────────────────────────

    @Test
    fun `书签墙的续页走同一套三道闸门，并按 uid 去重`() {
        val body = bookmarks.joinToString("\n") { it.value }
        // 闸门三件事（GalleryLoadMoreState）：墙得是活的、到底了不判、在取时不并发。
        assertTrue("闸门必须复用 GalleryLoadMoreState：\n$body", body.contains("GalleryLoadMoreState("))
        assertTrue("wallActive 判的是墙非空：\n$body", body.contains("wallActive = merged.isNotEmpty(),"))
        assertTrue(
            "到底必须问 domain 那把尺（每一趟都到底才算到底），不许在这一屏现算第一条腿：\n$body",
            body.contains("exhausted = exhausted,") && body.contains("pixivFeedExhausted(cursors)"),
        )
        val storeBody = sessionStore.joinToString("\n") { it.value }
        assertTrue(
            "在取时不并发（闸门读仓里的 busy，仓的 busy 要覆盖三段在途状态）：\n$body\n---\n$storeBody",
            body.contains("busy = session.busy,") &&
                storeBody.contains("val busy: Boolean get() = loading || refreshing || loadingMore") &&
                storeBody.contains("if (!asked || job?.isActive == true) return"),
        )
        // 追加去重走那颗共用判据（按 uid）：书签列表跨页会重复，不去重就是同一张图连着出现两遍。
        assertTrue(
            "合屏必须走 mergePixivLegs 并按 uid 去重：\n$body",
            body.contains("mergePixivLegs(legs.map { pages[it]?.works ?: emptyList() }) { it.post.uid }"),
        )
        // 续页只认站方游标，不自己拼页码。
        assertTrue(
            "收藏段那一腿必须打 bookmarkedIllusts(bearer, me, restrict, next)（next 是站方的 next_url）：\n$body",
            body.contains("currentWorks.bookmarkedIllusts(bearerOf(account), userId, restrict, next)"),
        )
        // 续页失败不清屏、不置 exhausted（用户再划一下就是重试）。这一段现在住在仓里。
        assertTrue(
            "续页失败要留着一墙并记一句：\n$storeBody",
            storeBody.contains("moreError = results.firstOrNull()?.second?.exceptionOrNull()?.readableWallError()"),
        )
    }

    @Test
    fun `书签墙与推荐墙同一把过滤判据、同一颗网格件`() {
        val body = bookmarks.joinToString("\n") { it.value }
        // 打码 / 屏蔽规则 / AI 过滤必须同一把尺（buildGalleryWall）—— 分叉了最难解释。
        assertTrue(
            "必须走 buildGalleryWall（与首页推荐墙同一把过滤判据）：\n$body",
            body.contains("buildGalleryWall(merged.map { it.post }"),
        )
        // 卡就是 GalleryCardsGrid 那一颗：两处都是瀑布流，卡长两个样正是这轮要消掉的东西。
        assertTrue("必须复用 GalleryCardsGrid：\n$body", body.contains("GalleryCardsGrid("))
        // 站方标题与作者头像按 uid 摊平递进去（同推荐墙那两张表）。
        assertTrue("站方标题表：\n$body", body.contains("stationTitles = titles"))
        assertTrue("作者头像表：\n$body", body.contains("authorAvatars = avatars"))
    }

    @Test
    fun `未登录与没记下用户号各念一句，而按钮只问那一枚判据`() {
        val body = bookmarks.joinToString("\n") { it.value }
        assertTrue("未登录空态：\n$body", body.contains("PixivAccountGate.NotSignedIn -> \"还没登录 pixiv\""))
        assertTrue(
            "老登录记录没存用户号要单独念（不能也说\"你还没登录\"）：\n$body",
            body.contains("PixivAccountGate.MissingUserId -> \"这个登录没记下用户号\""),
        )
        // ⚠️ 摆不摆「重试」在这一屏**只问** `retryIsMeaningful` 那一枚（哪两档没出路已由 domain 钉死，
        // 判据在 `PixivAccountGateTest`）。这里再抄一遍"未登录不给按钮"就是第二把尺 ——
        // 两把尺迟早给出不一样的一句话，而那族错的形状正是"给一档摆一枚按下去还是同一句话的重试"。
        assertEquals(
            "tone 必须问那一枚判据，且只问一次：\n$body",
            1,
            countIn(bookmarks, """tone = if \(gate\.retryIsMeaningful\)"""),
        )
        assertEquals(
            "actionText 也必须问同一枚，且只问一次：\n$body",
            1,
            countIn(bookmarks, """actionText = if \(gate\.retryIsMeaningful\) """),
        )
        assertTrue("按钮按下去要真重读票据：\n$body", body.contains("onAction = if (gate.retryIsMeaningful) onRetry else null"))
        // 墙自己失败那一档另给一枚重试（与闸门那两档是两件事：那一档按下去确实会重发趟）。
        // 2026-10-19 起"再发一趟"不再是推一枚计数，而是直接调仓的 refresh（取数由仓发车）。
        assertTrue(
            "失败档要有重试、且按下去真重发一趟：\n$body",
            body.contains("actionText = \"重试\"") &&
                body.contains("onAction = refresh,") &&
                body.contains("session.refresh(legs, fetch)"),
        )
    }

    @Test
    fun `云端书签与本地收藏是两套账：不合并、不互删`() {
        // 用户稿卡下那行说明的代码侧对应物：账号页只读 app-api 的书签腿，
        // 不许出现任何把两边并起来的写法（合并就意味着"删了这边那边也没了"）。
        val body = bookmarks.joinToString("\n") { it.value }
        assertTrue(
            "账号页不许碰本地收藏那本账：\n$body",
            !body.contains("favorites.toggle") && !body.contains("GalleryFavorites"),
        )
        // hub 上本地收藏那颗去的是 FavoritesRoute（主 Tab 上那份），不是新复制一面墙。
        val hubBody = hub.joinToString("\n") { it.value }
        assertTrue(
            "hub 不自己摆墙（两颗卡都是入口）：\n$hubBody",
            !hubBody.contains("GalleryCardsGrid") && !hubBody.contains("LazyVertical"),
        )
    }
}
