package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画廊浏览历史这一轮的**接线尺子**（源码层面，2026-10-14）。
 *
 * 编译器管不到"这一行还在不在"，运行期又只在人真点到大图页时才走得通 —— 而这轮换掉的正是
 * 顶栏那一枚钮，两条缺陷都是**静默**的：
 * ① 记「看过」的那一行如果跑到 `maskedOf` 守卫之外，被屏蔽规则挡住的图也会进历史，
 *    屏上看不出来（历史墙里那些瓦片本来就是从档里读的）；
 * ② 顶栏那枚从「换一批」换成「浏览历史」时，如果顺手把节头那枚一起删了，
 *    用户就没了"我要新内容"的出口 —— 而页面照常渲染。
 */
class GalleryHistoryWiringTest {

    private fun codeLines(dir: String, name: String): List<IndexedValue<String>> {
        val file = RepoSources.kotlinFiles(dir).firstOrNull { it.name == name }
            ?: error("找不到 $name（尝试过的根：${RepoSources.root.absolutePath}）")
        return file.readLines().withIndex().filter { (_, line) ->
            val t = line.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }
    }

    private fun stripped(line: String): String = line.replace(Regex("\"[^\"]*\""), "")

    /** 这一屏本身：后面六条尺子都钉它，注释行不参与匹配（扫描类的口径与 TokenBudgetFrozenTest 同一把）。 */
    private fun screenLines(): List<IndexedValue<String>> =
        codeLines("com/venera/compose/gallery/ui", "GalleryHistoryScreen.kt")

    /** 从 `actions = {` 数到大屏那一行，栈深必须正好是"只在 actions 里"（1）。 */
    private fun depthBefore(lines: List<IndexedValue<String>>, openIdx: Int, targetIdx: Int): Int {
        var depth = 0
        lines.filter { it.index in openIdx until targetIdx }.forEach {
            depth += stripped(it.value).count { c -> c == '{' } - stripped(it.value).count { c -> c == '}' }
        }
        return depth
    }

    /** ① 写点落在守卫之内：`recordView` 恰一处，且同块里 `maskedOf` 那句排在它前面。 */
    @Test
    fun `大图页只在守卫之内记看过`() {
        val lines = codeLines("com/venera/compose/gallery/ui", "GalleryPostScreen.kt")
        val writes = lines.filter { it.value.contains("recordView(") }
        assertEquals(
            "记「看过」那一行在大图页里必须恰好一处（写点就是这一处，七条开卡路径都汇到这里）：" +
                writes.map { it.index + 1 to it.value.trim() },
            1,
            writes.size,
        )
        val writeAt = writes.single().index
        val blockStart = lines.lastOrNull {
            it.index < writeAt && it.value.trim().startsWith("LaunchedEffect(currentUid) {")
        }
        assertNotNull("写点没住在按 currentUid  keyed 的那个块里：翻页就记不上", blockStart)
        val guards = lines.filter {
            it.index in blockStart!!.index..writeAt &&
                it.value.contains("if (maskedOf(post)) return@LaunchedEffect")
        }
        assertEquals("挡住的图也要记 = 假历史，守卫那句必须在同一块里", 1, guards.size)
        assertTrue(
            "守卫必须排在记之前，否则记完才挡 —— 那一笔已经进档了",
            guards.single().index < writeAt,
        )
    }

    /** ② 顶栏换成历史入口之后，节头那枚「换一批」的接线仍在。 */
    @Test
    fun `首页换一批仍在猜你喜欢节头`() {
        val lines = codeLines("com/venera/compose/gallery/ui", "GalleryScreen.kt")
        val remix = lines.filter { it.value.contains("onRemixForYou = { retryForYouFromUser() }") }
        assertEquals(
            "猜你喜欢节头那枚「换一批」的把手没了 —— 顶栏那枚让给历史之后，它是首页唯一的出口",
            1,
            remix.size,
        )
    }

    /**
     * ③ 顶栏那两枚「收藏 / 历史」**只在画廊首页摆**，搜索态收起来。
     *
     * ⚠️ 2026-10-18 改判（用户：「顶栏收藏和历史图标只需要出现在画廊首页就行，其他二级页面不要出现」）。
     * 这条用例原先叫「搜索态顶栏也摆得出历史入口」，钉的正是相反的那件事（`depth == 1`，不许套 if）。
     * 真机上四颗图标并排在搜索态读起来是"顶栏被塞满"，而那一态的主角是搜索卡 ——
     * 所以现在**必须**套一层 `if (!svm.active)`（`depth == 2`），并且两枚共用同一道门。
     */
    @Test
    fun `收藏与历史入口只在画廊首页摆，搜索态收起来`() {
        val lines = codeLines("com/venera/compose/gallery/ui", "GalleryScreen.kt")
        val actions = lines.single { it.value.contains("actions = {") }
        listOf("浏览历史", "收藏").forEach { name ->
            val label = lines.filter { it.value.contains("contentDescription = \"$name\"") }
            assertEquals("顶栏那枚「$name」不见了", 1, label.size)
            val at = label.single().index
            val pill = lines.lastOrNull {
                it.index < at && it.value.trim().startsWith("VeneraTopBarPill(onClick = {")
            }
            assertNotNull("「$name」那枚图标没装在药丸里", pill)
            assertEquals(
                "「$name」没套搜索态门控（搜索态顶栏会变成四颗图标并排）—— " +
                    "depth=${depthBefore(lines, actions.index, pill!!.index)}",
                2,
                depthBefore(lines, actions.index, pill.index),
            )
        }
        assertEquals("导航动作只许交给这一枚", 1, lines.count { it.value.contains("onOpenHistory()") })
        assertEquals("收藏入口只许接这一处", 1, lines.count { it.value.contains("onOpenBookmarks()") })
    }

    /** ④ 设置里那一行不是光杆标题：它真调 clear()。 */
    @Test
    fun `设置里清空画廊浏览历史是真把手`() {
        val lines = codeLines("com/venera/compose/feature/settings", "GallerySettings.kt")
        val row = lines.filter { it.value.contains("\"清空画廊浏览历史\"") }
        assertEquals(1, row.size)
        val clears = lines.filter { it.value.contains("store.clear()") }
        assertEquals("那一行没有真的清档", 1, clears.size)
        assertTrue("清档调用必须在标题之后（同一处把手）", clears.single().index > row.single().index)
        // 计数吃的是存储件那份名单，不是另起一本账。
        assertTrue(
            "张数没有订阅名单本身（清了不会归零）",
            lines.any { it.value.contains("history?.rows?.collectAsState()") },
        )
    }

    /** ⑤ 路由接通：顶栏那枚按下去导航一次，导航壳里有一面对应这路由的屏。 */
    @Test
    fun `历史屏挂在导航壳上`() {
        val lines = codeLines("com/venera/compose/feature", "Navigation.kt")
        assertEquals("路由声明一次", 1, lines.count { it.value.contains("data object GalleryHistoryRoute") })
        assertEquals("目的地一次", 1, lines.count { it.value.contains("composable<GalleryHistoryRoute>") })
        assertEquals(
            "从顶栏进历史的导航动作恰一处",
            1,
            lines.count { it.value.contains("navController.navigate(GalleryHistoryRoute)") },
        )
    }

    // ── 2026-10-15 那一轮（瀑布比例 / 长按多选 / 三把手 / 消散）的尺子 ──

    /** ⑥ 导出前先过守卫：复核那句必须排在交付之前。 */
    @Test
    fun `导出前逐张复核守卫且排在交付之前`() {
        val lines = screenLines()
        val checks = lines.filter { it.value.contains("galleryPostWouldBeMasked(post, maskMode") }
        assertEquals("复核判式必须就在那一圈取数里，且只有一处", 1, checks.size)
        val hands = lines.filter {
            it.value.contains("GallerySaver.save(context, post)") || it.value.contains("shareGalleryPosts(context, usable)")
        }
        assertTrue("没有交付动作可钉（三把手断了）", hands.isNotEmpty())
        assertTrue(
            "复核排在了交付之后 —— 那就是先把原档交出去再问该不该挡",
            hands.all { it.index > checks.single().index },
        )
    }

    /** ⑦ 批量共享走一次 chooser，不许在圈里按单张那颗。 */
    @Test
    fun `批量共享不连开系统面板`() {
        val screen = screenLines()
        assertEquals(
            "多选里在循环里按单张那颗 = 选几张开几扇面板",
            0,
            screen.count { it.value.contains("shareGalleryPost(context,") },
        )
        assertEquals(1, screen.count { it.value.contains("shareGalleryPosts(context, usable)") })
        // 对照：那颗批量件自己在，且真的用 SEND_MULTIPLE（不是把单张那颗改了个名）。
        val share = codeLines("com/venera/compose/gallery/ui", "GalleryImageShare.kt")
        assertTrue("批量那颗没走 ACTION_SEND_MULTIPLE", share.any { it.value.contains("ACTION_SEND_MULTIPLE") })
        assertTrue("单张那颗还在（大图页与长按仍用它）", share.any { it.value.contains("fun shareGalleryPost(") })
    }

    /** ⑧ 删除落库不挂在动画回调上：演到一半退出这一屏，记录也必须真少了。 */
    @Test
    fun `删除先落库消散只是观感`() {
        val lines = screenLines()
        val removes = lines.filter { it.value.contains("history.removeAll(") }
        assertEquals(1, removes.size)
        /*
         * 区间两边都用**原始行号**（`it.index`）。这一条本鱼先错过一次：
         * `indexOfFirst` 交出的是"过滤后列表里的第几个"，而注释行已经被摘掉，
         * 那个数比行号小一截 —— 拿它跟 `it.index` 比，区间就是个空区间，
         * 于是"正确不红"读成"正确不红"、其实那条断言恒真。
         */
        val timer = lines.first { it.value.contains("LaunchedEffect(dissolving) {") }.index
        val timerEnd = lines.first { it.index > timer && it.value.contains("frozenRows = null") }.index
        assertTrue(
            "没找到那个时长定时器（消散与删除的解耦就没人守了）",
            timer in 1 until timerEnd,
        )
        // 先自证区间真的是那颗块：区间里必须看得见等时长那一句，否则下面的"不含 removeAll"是空区间恒真。
        assertTrue(
            "区间没框住定时器（等时长那句不在里面）",
            lines.any { it.index in timer..timerEnd && it.value.contains("delay(disintegrationMs") },
        )
        assertTrue(
            "落库被挂在了动画完成那一路 —— 动画没跑完就等于没删",
            lines.none { it.index in timer..timerEnd && it.value.contains("removeAll") },
        )
    }

    /** ⑨ 瓦片按画廊那一面的错落比例摆，不再钉正方。 */
    @Test
    fun `历史瓦片吃站方比例而不是等高一档`() {
        val lines = screenLines()
        assertEquals("正方那一档已经撤掉了", 0, lines.count { it.value.contains(".aspectRatio(1f)") })
        assertEquals(1, lines.count { it.value.contains("GalleryBrowseRows.tileRatio(") })
        // 判据本身住在 domain（可单测），屏上只递兜底那一档。
        val domain = codeLines("com/venera/compose/gallery/domain", "GalleryBrowseHistory.kt")
        assertTrue("夹界那句不在 domain 里", domain.any { it.value.contains("coerceIn(0.4f, 2.5f)") })
    }

    /**
     * ⑩ 瓦片的按压反馈走**全画廊那一档**（下沉 0.94）—— 2026-10-16 口径变更。
     *
     * 此前这一处钉的是 material3 的 `ripple()`，理由是"长按要那一圈从触点扩开的东西"。
     * 那条理由在整面图墙统一成"沉一下"之后不成立了：全画廊只剩这一格泛涟漪，
     * 而涟漪盖的又恰好是用户正在看的画面（这一格整个就是一张图）。
     *
     * 判据钉**出处**而不是符号：`indication` 必须取 `VeneraPressFeedback.indication`
     * （唯一出处），且 material3 那颗涟漪工厂**不得**再出现在这一屏。
     */
    @Test
    fun `历史瓦片按压反馈走全画廊同一档下沉`() {
        val lines = screenLines()
        assertEquals(
            "瓦片的 indication 没走唯一出处",
            1,
            lines.count { it.value.contains("indication = VeneraPressFeedback.indication") },
        )
        assertEquals(
            "material3 涟漪还在这一屏里（口径变更没落干净）",
            0,
            lines.count { it.value.contains("ripple()") || it.value.contains("material3.ripple") },
        )
        assertTrue("长按没接上（只有单击那一档）", lines.any { it.value.contains("onLongClick = onLongPress") })
    }

    /** ⑪ 底栏让位那条契约：这一屏只消费 LocalBottomBarClearance，不再自行叠条高。 */
    @Test
    fun `多选条浮在底边而不额外加一行让位`() {
        val lines = screenLines()
        val bottoms = lines.filter { it.value.contains("bottom = LocalBottomBarClearance.current") }
        assertEquals(1, bottoms.size)
        assertTrue(
            "又自己叠了一层条高（口径写在 ui/tokens/Spacing.kt:214-215）",
            bottoms.single().value.contains("+").not(),
        )
    }

    /** ⑫ 消散那格真的被按下：只包不按 = 原样画满时长再整块摘掉（真机读过到的一次「没有动画」）。 */
    @Test
    fun `包裹消散的那一支自己按下 disintegrate`() {
        val lines = screenLines()
        val open = lines.filter { it.value.contains("if (dissolvingThis) {") }
        assertEquals("分支自己没了（消散整个失效）", 1, open.size)
        val from = open.single().index
        val close = lines.firstOrNull { it.index > from && it.value.contains("} else {") }
        assertNotNull("分支没有 else（那就不分是否在散）", close)
        val branch = lines.filter { it.index in from..close!!.index }
        // 先自证区间真的是那支：里面必须看得见被包的瓦片，否则下面的两条是空区间恒真。
        assertEquals("区间没框住瓦片（锚点选岔了）", 1, branch.count { it.value.contains("tile()") })
        assertEquals("那一支没有消散包裹", 1, branch.count { it.value.contains("GalleryDisintegration(") })
        assertEquals(
            "包上了但从没按下 disintegrate —— triggered 恒 false，画的就是原样内容",
            1,
            branch.count { it.value.contains(".disintegrate(") },
        )
    }

    /** ⑬ 取景排在等帧之后：「一出现就散」这种用法不先等一帧，拍回来的是空位图。 */
    @Test
    fun `消散取景之前先等一帧`() {
        val lines = codeLines("com/venera/compose/gallery/ui", "GalleryDisintegration.kt")
        val shoot = lines.filter { it.value.contains("layer.toImageBitmap()") }
        assertEquals("取景只有一处", 1, shoot.size)
        val at = shoot.single().index
        val gate = lines.firstOrNull { it.index < at && it.value.contains("if (!state.triggered) return@LaunchedEffect") }
        assertNotNull("取景没住在 triggered 那个块里（那就不是一次性的）", gate)
        // 自证区间：从守卫到取景之间必须真的等过一帧。
        assertTrue(
            "取景前没等帧 —— 这一格刚出现就拍，拿到空位图，条与粒子各 0 颗",
            lines.any { it.index in gate!!.index..at && it.value.contains("withFrameMillis { }") },
        )
        // 另一条退路也要在：拍到空位图时不许悬着一格不画。
        assertTrue(
            "切不出条时没有退路（那一格会透明着悬满时长）",
            lines.any { it.value.contains("if (built.strips.isEmpty())") },
        )
    }
}
