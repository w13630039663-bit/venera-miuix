package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pixiv **个人账号页**那一屏的接线尺子（方案 `docs/rounds/gallery-pixiv-account-page-2026-10-19.html`，2026-10-19）。
 *
 * 这一族的缺陷全是"屏上看不出来"的那一类，所以判据落在源码文本上（`app/` 没有 Robolectric）：
 *
 * - 「全部」被悄悄收成一趟（端点写死一档 / 只发 `legs.first()`）—— 表现是私密那批永远不出现，而下拉还亮着；
 * - 页脚只看第一条腿就念"都摆完了"；
 * - 二级那一行漏挂 chrome（挂进内容列）—— 滚一下就没了，用户以为切页失败；
 * - 三个数拿 0 顶住"—"（站方没答与"真的零张"被读成同一件事）；
 * - 已关注那一屏忘了每位再发一跳（那三张预览恒空，而这一档是用户点名要的那一档）。
 *
 * ⚠️ 注释行不参与匹配（`codeLines` 那把口径）：这些文件的大段注释里写着同样的符号名。
 */
class GalleryPixivAccountPageWiringTest {

    private fun codeLines(dir: String, name: String): List<IndexedValue<String>> {
        val file = RepoSources.kotlinFiles(dir).firstOrNull { it.name == name }
            ?: error("找不到 $name（目录 $dir，根：${RepoSources.root.absolutePath}）")
        return file.readLines().withIndex().filter { (_, line) ->
            val t = line.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }
    }

    private val page = codeLines("com/venera/compose/gallery/ui", "PixivAccountScreen.kt")
    private val store = codeLines("com/venera/compose/gallery/ui", "PixivAccountSessionStore.kt")
    private val activity = codeLines("com/venera/compose", "PixivAccountActivity.kt")
    private val body = page.joinToString("\n") { it.value }

    private fun count(needle: String): Int = countIn(page, needle)

    private fun countIn(lines: List<IndexedValue<String>>, regex: String): Int =
        Regex(regex).findAll(lines.joinToString("\n") { it.value }).count()

    /**
     * 屏上直接印出来的那串字（`text = "…"` 与 `title = "…"`），注释与标识符都不算。
     *
     * ⚠️ 先把 `${…}` 那几段插值**整段摘掉**再交给判据：不摘的话 `"看：${scope.screenLabel}"`
     * 会被读成一句带驼峰标识符的话 —— 而屏上印出来的是"看：全部"。量具读错对象，红就是假的。
     */
    private fun screenStrings(): List<String> = page.flatMap { entry ->
        Regex("""(?:text|title)\s*=\s*"([^"]+)"""").findAll(entry.value)
            .map { it.groupValues[1].replace(Regex("""\$\{[^}]*\}"""), "").replace(Regex("""\$[A-Za-z][A-Za-z0-9_]*"""), "") }
            .toList()
    }

    // ── 一、档位与腿 ────────────────────────────────────────────────────────

    @Test
    fun `三段与二级三档的屏上话逐字对稿且不重复`() {
        val primary = Regex("""\("([^"]+)"\),""")
            .findAll(body.substringAfter("internal enum class PixivAccountSection")
                .substringBefore("internal enum class PixivAccountFeedTab"))
            .map { it.groupValues[1] }.toList()
        assertEquals(listOf("动态", "收藏", "已关注"), primary)
        val secondary = Regex("""\("([^"]+)"\),""")
            .findAll(body.substringAfter("internal enum class PixivAccountFeedTab")
                .substringBefore("private object PixivWorkCategory"))
            .map { it.groupValues[1] }.toList()
        assertEquals(listOf("我发布的", "关注动态", "账号管理"), secondary)
    }

    @Test
    fun `restrictApplies 是穷尽 when 而不带 else`() {
        // 有 else 就等于给"新加的一档"一个默认答案 —— 那一档会静默带上（或静默不带）restrict。
        val fn = body.substringAfter("fun restrictApplies(tab: PixivAccountFeedTab): Boolean = when (this) {")
            .substringBefore("\n    }\n}")
        assertFalse("restrictApplies 不许出现 else：\n$fn", fn.contains("else ->"))
        assertTrue("三段必须各表一次态：\n$fn", listOf("DYNAMIC ->", "BOOKMARKS ->", "FOLLOWING ->").all { fn.contains(it) })
    }

    @Test
    fun `三面墙与已关注都按档位发趟，没有一处写死档`() {
        // 「全部」= 两趟。腿的清单来自档位 `legs`，不是某一处自己列了一串。
        assertEquals("动态那面墙交一次腿清单", 1, count("""legs = wallScope\.legs"""))
        assertEquals("收藏与已关注各交一次腿清单", 2, count("""legs = pageScope\.legs"""))
        // 发趟收在会话仓里（切屏不走网络）：第一页每一条腿各发一趟。
        assertEquals(
            "会话仓按 legs 发趟（第一页）",
            1,
            countIn(store, """legs\.map \{ restrict -> restrict to runLeg \{ fetch\(restrict, null\) \} \}"""),
        )
        assertFalse("这一屏不许出现写死的档位字面量", body.contains("PixivRestrict.Public") || body.contains("PixivRestrict.Private"))
    }

    @Test
    fun `两处到底都走 domain 那颗判据，这一屏没有一处自己算到底`() {
        // 只问第一条腿 = 私密那批永远进不来而页脚已经在念"都摆完了"（本仓那族假读数）。
        // 所以这里钉的不是"只许出现一次"，而是"**每一处** `exhausted` 都必须等于那颗共用判据" ——
        // 新加一面墙时顺手写一句 `nextUrl == null` 才是这一族真正的坏法。
        val assigns = page.filter {
            val t = it.value.trim()
            t.startsWith("exhausted = ") || t.startsWith("val exhausted = ")
        }
        assertTrue("量具失效：一处到底都没找到", assigns.isNotEmpty())
        for (line in assigns) {
            val t = line.value.trim()
            assertTrue(
                "第 ${line.index} 行自己算了到底（$t）—— 要走 pixivFeedExhausted：",
                t == "val exhausted = pixivFeedExhausted(cursors)" || t == "exhausted = exhausted,",
            )
        }
        assertEquals("墙与已关注各算一次（都走同一颗）", 2, count("""val exhausted = pixivFeedExhausted\(cursors\)"""))
        assertEquals("会话仓两面墙的续页也各走一次共用判据", 2, countIn(store, """pixivFeedExhausted\(cursors\)"""))
        assertEquals("页脚两处都要每一趟答过才算到底", 2, count("""arrived && exhausted"""))
        assertTrue("没答过的腿不许算进账：\n$body", body.contains("pages[restrict]?.let { PixivLegCursor(restrict, it.nextUrl) }"))
    }

    // ── 二、chrome 三行 ─────────────────────────────────────────────────────

    @Test
    fun `两行分段器都挂在顶栏 chrome 里，而不是内容列`() {
        // 口径同「收藏页二级分段器必须挂顶栏 chrome」那条拍板：挂进内容列的话滚一下就没了。
        val chrome = body.substringAfter("bottomContent = {").substringBefore("modifier = Modifier.align(Alignment.TopCenter)")
        assertEquals("一级那一行必须在 chrome 里", 1, Regex("""PixivAccountSegmentTrack\(""").findAll(chrome).count())
        assertEquals("二级那一行的高度只用一次", 1, Regex("""height\(tokens\.spacing\.pixivAccountSecondaryRow\)""").findAll(chrome).count())
        assertTrue("二级那一行必须只在动态段渲染：\n$chrome", chrome.contains("if (section == PixivAccountSection.DYNAMIC)"))
        // 一级那颗自持轨道：它自己的高度只许出现一次（整屏只这一条轨道）。
        assertEquals("一级轨道的高度只在轨道件里出现一次", 1, count("""height\(tokens\.spacing\.pixivAccountPrimaryRow\)"""))
        // 稿 §1 的 `.seg` / `.sub2`：轨道留 12dp 屏边、二级行再留 8dp 上距，三颗二等分（不是各自内容宽）。
        assertTrue("chrome 两行要留屏边：\n$chrome", chrome.contains("start = tokens.spacing.screenHorizontal"))
        assertTrue("二级行与轨道之间要留 8dp：\n$chrome", chrome.contains("padding(top = tokens.spacing.space4)"))
        // 三档共用一颗调用（forEach），所以钉的是"那一颗调用带 weight(1f)"——三等分由它保证。
        assertTrue("三颗二级药丸要三等分：\n$chrome", Regex("""Modifier\s*\.weight\(1f\)\s*\.fillMaxHeight\(\)""").containsMatchIn(chrome))
    }

    @Test
    fun `附加高写在屏里，没去动顶栏那枚地板常量`() {
        // `topBarFloor` 那把尺只钉"地板"；各页的附加高自留（改了它等于改全站 20 屏）。
        assertTrue("这一屏要引用地板常量来算避让：\n$body", body.contains("VeneraSpacing.topBarFloor"))
        // 让位的那笔账必须与 chrome 的实高一致：轨道 + 8dp 上距 + 二级行 + 8dp 收尾。
        // 少了任何一节，内容第一行就会被两行药丸压住（2026-10-19 真机读数就是这么来的）。
        assertTrue(
            "让位没按 chrome 实高算：\n$body",
            body.contains(
                "tokens.spacing.pixivAccountPrimaryRow + tokens.spacing.space4 + " +
                    "tokens.spacing.pixivAccountSecondaryRow + tokens.spacing.space4",
            ),
        )
        val spacing = File(RepoSources.appMain, "com/venera/compose/ui/tokens/Spacing.kt").readText()
        assertTrue("topBarFloor 必须还是 104dp：\n", spacing.contains("val topBarFloor: Dp = 104.dp"))
    }

    // ── 三、已关注那一屏 ────────────────────────────────────────────────────

    @Test
    fun `已关注那一屏每位再发一跳取三张预览`() {
        // 2026-10-19 拍板 ②。不接这一跳的话那一屏只有头像与名字，与稿上那三张对不上。
        assertEquals("取预览只发一处", 1, count("""fetchCovers\(user\.id\.toString\(\)\)"""))
        assertTrue("预览张数钉在 3：\n$body", body.contains("private const val PixivFollowingPreviewCount = 3"))
        // 取不到不空一格：那一行退成只有头像与名字（不摆三张空位、也不弹错误）。
        assertTrue("到货即填、取不到就什么都不说：\n$body", body.contains("if (previews.isNotEmpty()) return@LaunchedEffect"))
    }

    @Test
    fun `四个数退横杠不拿零顶`() {
        // 站方没答那一格与"这位真的零张"是两件事，写成 0 就是替站方编一个它没给过的结论。
        // 稿上是**四格**（粉丝 / 作品 / 关注 / 收藏）：2026-10-19 真机读数里只有三格，
        // 少的那一格是「关注」—— 它出自匿名那一腿（`PixivUserHead.followingCount`），
        // 与另外三格不同腿，所以最容易漏。
        assertEquals("只有一处把 null 翻成「—」，四格共用它", 1, count("""value\?\.toString\(\) \?: "—""""))
        // 数**调用点**：声明那一行也以 `PixivAccountStat(` 结尾，按行首认才只数到四格。
        assertEquals("四格各调一次读数件", 4, page.count { it.value.trim().startsWith("PixivAccountStat(") })
        assertEquals("四格之间三道竖线（首尾不摆）", 3, page.count { it.value.trim().startsWith("PixivAccountStatDivider()") })
        assertTrue("「关注」那一格必须吃匿名腿而不是票据腿：\n$body", body.contains("head?.followingCount"))
    }

    // ── 四、入口与承载 ──────────────────────────────────────────────────────

    @Test
    fun `账号页有独立 Activity 且注册进清单`() {
        // 只有跨过 Activity 边界，系统才施加 AOSP 跨 activity 预测式返回；玻璃也才透得出后面那屏。
        assertTrue("宿主必须继承那一颗基类：\n${activity.joinToString("\n") { it.value }}",
            activity.any { it.value.contains("class PixivAccountActivity : VeneraSubActivityBase()") })
        assertTrue("这一页要铺不透明氛围底（整页不再透后面那屏）：\n", activity.any { it.value.contains("opaqueAmbientBackground: Boolean = true") })
        assertTrue("窗口 blur-behind 必须关：\n", activity.any { it.value.contains("blurBehindEnabled: Boolean = false") })
        val manifest = File(RepoSources.root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue("清单里必须注册那颗 Activity", manifest.contains("android:name=\".PixivAccountActivity\""))
        assertTrue("实底承载要用那颗不透明主题", Regex("""\.PixivAccountActivity"[\s\S]{0,160}Theme\.Venera"""").containsMatchIn(manifest))
    }

    @Test
    fun `退出的那一枚把手排在确认之后且全屏只此一处`() {
        // 破坏性动作两步确认（既有口径）。只钉"存在"会被"置个旗、真退出写在别处"骗过去 ⇒ 钉顺序。
        assertEquals("退出只许一处调用点", 1, count("""account\?\.signOut\(\)"""))
        val confirm = page.indexOfFirst { it.value.contains("onConfirm = {") }
        val signOut = page.indexOfFirst { it.value.contains("account?.signOut()") }
        assertTrue("退出没紧跟在确认之后（confirm=$confirm signOut=$signOut）", signOut == confirm + 1)
        assertTrue("确认那颗必须是破坏色", body.contains("confirmDestructive = true"))
    }

    // ── 五、文案尺 ──────────────────────────────────────────────────────────

    @Test
    fun `屏上那些话不带机器味`() {
        val strings = screenStrings()
        assertTrue("量具失效：一颗屏上字符串都没扫到", strings.isNotEmpty())
        for (forbidden in listOf("`", "*", "#", "Bearer", "restrict", "app-api", "next_url", "401", "null", "TODO", "Activity")) {
            val hits = strings.filter { it.contains(forbidden) }
            assertFalse("$forbidden 不许出现在屏上那句话里：$hits", hits.isNotEmpty())
        }
        // 内部符号名不许漏到屏上（`PixivTwoLegWall` 这类词一旦印出来就是实现笔记进界面）。
        val leaked = strings.filter { Regex("[A-Za-z]+[A-Z][a-z]+").containsMatchIn(it) && !it.contains("pixiv") }
        assertTrue("屏上句子混进了驼峰标识符：$leaked", leaked.isEmpty())
    }


    // ── 六、2026-10-19 真机读数的改判（五屏截图逐条对稿）────────────────────

    @Test
    fun `顶栏主行不重复摆画师名`() {
        // 真机读数：顶栏 largeTitle 一份、头部一份、账号管理身份卡一份 —— 同一屏三个"极东的Djehuty"。
        // 稿上主行是紧凑的「返回 + 小头像 + 名字」，而这一屏走的是全站那套大标题 chrome，
        // 所以主行只许报"这是哪一屏"。
        assertTrue("主行要报屏名：\n$body", body.contains("largeTitle = \"我的 pixiv\""))
        assertFalse("主行不许再吃登录名：\n$body", body.contains("largeTitle = signed"))
        assertFalse("主行不许再吃站方画师名：\n$body", body.contains("largeTitle = head"))
    }

    @Test
    fun `私密档那句在内容第一行，不在顶栏副标题`() {
        // 稿 §1 的 ⑧：这句话是**内容**的事实（这一批里有没有私密条目），挂在顶栏副标题上
        // 读起来像"这一屏的标题叫含非公开"。
        assertEquals("这一屏不再用顶栏副标题说话", 0, count("""subtitle = """))
        assertEquals("常驻那一行只在墙的第一行摆一次", 1, count("""PixivPrivateScopeLine\(scope\)"""))
        assertTrue("那一行要按档位说两句不同的话：\n$body", body.contains("这一档只有你标为非公开的"))
    }

    @Test
    fun `段名后面那个数只在公开档摆`() {
        // 站方给的 `total_illust_bookmarks_public` 与匿名主页那格关注数数的都是**公开**那份。
        // 切到「全部」还继续摆它，就是把公开数说成合计数 —— 拿不到合计数就不带数。
        assertEquals("两处计数各钉一次公开档", 2, count("""takeIf \{ scope == PixivReadScope\.Public \}"""))
        assertTrue("动态段压根不带数：\n$body", body.contains("PixivAccountSection.DYNAMIC to null"))
    }

    @Test
    fun `已关注那一行的三格在名字上方且整行三等分`() {
        // 稿 §1 的 ③：三张封面拼成一条压在上方。旧版挤在行尾，竖/方/横三种比例参差成锯齿。
        val row = body.substringAfter("private fun PixivFollowingRow(").substringBefore("private const val PixivFollowingPreviewCount")
        val strip = row.indexOf("pixivAccountCoverStripHeight")
        val meta = row.indexOf("pixivAccountMiniAvatar")
        assertTrue("量具失效：没找到那一行（strip=$strip meta=$meta）", strip >= 0 && meta >= 0)
        assertTrue("三格必须排在头像与名字**上方**（strip=$strip meta=$meta）", strip < meta)
        assertTrue("每格按 weight 三等分，不由图自己定宽：\n$row", Regex("""\.weight\(1f\)""").containsMatchIn(row))
        assertEquals("整条只钉一个高度", 1, count("""pixivAccountCoverStripHeight"""))
        // 右端那枚是**状态**：整页每一位都在关注列表里，它不是把手（点了不该什么都不发生）。
        assertTrue("右端要报「已关注」：\n$row", row.contains("PixivFollowStatePill(text = \"已关注\")"))
        val pill = body.substringAfter("private fun PixivFollowStatePill(").substringBefore("private const val PixivFollowingPreviewCount")
        assertFalse("状态胶囊不许挂点击：\n$pill", pill.contains("clickable"))
    }

    @Test
    fun `账号管理每一行的把手各说各的`() {
        // 真机读数：三行右端**全都写「打开」**，包括退出登录那一行 —— 那是本仓最典型的假把手
        // （把手说的动作与行本身的动作对不上）。
        val handles = Regex("""PixivAccountHandle\(\s*text = "([^"]+)"""").findAll(body).map { it.groupValues[1] }.toList()
        assertEquals("五枚把手都在：$handles", listOf("复制账号", "重新登录", "贴进来", "打开", "退出"), handles)
        assertEquals("把手不许重名：$handles", handles.size, handles.distinct().size)
        assertEquals("两行报值不报动词（默认范围 / 显示非公开）", 2, page.count { it.value.trim().startsWith("PixivAccountValue(") })
        // 破坏性那一枚必须自己表态：`destructive` 没有默认值，每一行都得写。
        assertEquals("每一枚把手都表一次态", handles.size, count("""destructive = (true|false)"""))
        assertTrue("退出那一枚必须是破坏色：\n$body", body.contains("destructive = true"))
    }

    @Test
    fun `总闸关掉时下拉只剩一档且两枚键都有写口`() {
        // 「关掉后不列出来」要是真的：不发那一趟、也不给按得动的第二档（灰着 = 假把手）。
        val picker = body.substringAfter("private fun pixivScopeChoices(").substringBefore("@Composable")
        assertTrue("闸关 = 只剩公开那一档：\n$picker", picker.contains("listOf(PixivReadScope.Public)"))
        assertFalse("不许用 enabled 把另两档灰着：\n$picker", picker.contains("enabled"))
        assertEquals("默认范围有下落", 2, count("""setRestrictDefault\("""))
        assertEquals("总闸有下落", 1, count("""setShowPrivate\("""))
        // 屏上那句"含非公开"与下拉档位吃的是同一份派生，不是第二枚开关。
        assertTrue("范围是派生量，不是第二份状态：\n$body", body.contains("val scope = if (showPrivate) pickedScope else PixivReadScope.Public"))
    }

    @Test
    fun `头部那颗头像压进 hero 半颗`() {
        // 稿 §1 的 ④：压在照片上才有"这一位的主视觉"那句读感，掉到 hero 之下就是两张不相干的图。
        assertTrue("压边量 = 半颗头像：\n$body", body.contains("tokens.spacing.pixivArtistAvatarSize / 2"))
        assertTrue("头像必须向下偏移压进照片下沿：\n$body", body.contains("offset(y = avatarOverlap)"))
        assertTrue("正文要让出那半颗：\n$body", body.contains("pixivArtistHeroMinHeight + avatarOverlap"))
    }

    @Test
    fun `身份读数只取一次，三处共用`() {
        // 从前头部与账号管理各发一趟 `/v1/user/detail`：同一屏对同一端点要两次，
        // 两趟之间站方改了数就会读到两个答案。
        assertEquals("票据腿一处", 1, count("""works\.userDetail\(bearer, userId\)"""))
        assertEquals("匿名腿一处", 1, count("""works\.userHead\(id\)"""))
        // 头部那一份与身份卡那一份各收一次 —— 另有两次是两面墙把读数交给头部助手。
        assertTrue("头部与身份卡各收一份现成读数", count("""head = head,""") >= 4)
    }

    // ── 七、2026-10-19 六条改版（留存 / 下拉 / 翻页 / 实底 / 大按钮）──────────

    @Test
    fun `数据活在进程级会话仓，切屏不重取`() {
        // 用户点名 ①：退出应用之前切屏幕都要留住数据。
        assertTrue("墙要读会话仓：\n$body", body.contains("PixivAccountSessionStore.wall(sessionKey)"))
        assertTrue("已关注要读会话仓：\n$body", body.contains("PixivAccountSessionStore.following(sessionKey)"))
        assertEquals("身份读数也只取一次（会话仓）", 1, count("""PixivAccountSessionStore\.identity\(it\)"""))
        assertEquals("墙的首次取数只挂 ensure", 1, count("""session.ensure\(legs, fetch\)"""))
        assertEquals("已关注的首次取数只挂 ensure", 1, count("""session.ensure\(legs, fetchUsers\)"""))
        assertFalse("这一屏不许再出现 reloadTick 那种「重来一遍」的开关", body.contains("reloadTick"))
        assertFalse("这一屏不许再用按 requestKey 重建的数据槽", body.contains("remember(requestKey)"))
        // 滚动位置也归会话仓：两面内容都从那儿接着摆，且状态跟着档位键重建
        // （换范围 = 另一面墙，不重建就会把上一档的位置带过去）。
        assertEquals("网格墙从会话仓接着摆", 1, count("""LazyStaggeredGridState\(session.scrollIndex, session.scrollOffset\)"""))
        assertEquals("已关注从会话仓接着摆", 1, count("""LazyListState\(session.scrollIndex, session.scrollOffset\)"""))
        assertEquals("两面都带档位键重建", 2, count("""rememberSaveable\(sessionKey, saver = """))
        assertEquals("写回只留一处写法（墙与已关注各一）", 2, count("""session.scrollIndex = index"""))
    }

    @Test
    fun `只有主动下拉刷新才重新取数`() {
        assertEquals("两面内容各一颗下拉刷新", 2, count("""PullToRefreshBox\("""))
        assertEquals("刷新的指示器各摆一颗", 2, count("""PullToRefreshDefaults\.Indicator\("""))
        assertEquals("墙的 refresh 只有一处调用", 1, count("""session.refresh\(legs, fetch\)"""))
        assertEquals("已关注的 refresh 只有一处调用", 1, count("""session.refresh\(legs, fetchUsers\)"""))
        // 下拉刷新与错误页的「重试」共用同一颗 refresh：两条出路、一套动作。
        assertEquals("错误页的重试也走 refresh", 2, count("""onAction = refresh"""))
    }

    @Test
    fun `三段横滑、二级三档方向化滑入滑出`() {
        assertTrue("三段要走 Pager：\n$body", body.contains("HorizontalPager("))
        assertTrue("点药丸滚过去：\n$body", body.contains("pagerState.animateScrollToPage(target.ordinal)"))
        assertTrue("当前段从 Pager 派生：\n$body", body.contains("val section = PixivAccountSection.entries[pagerState.currentPage]"))
        // 用户点名 ③：新页从右侧滑入、旧页向左滑出；倒着切反过来。
        assertTrue("新页方向随切换方向：\n$body", body.contains("slideInHorizontally(initialOffsetX = { if (forward) it else -it })"))
        assertTrue("旧页往反方向退：\n$body", body.contains("slideOutHorizontally(targetOffsetX = { if (forward) -it else it })"))
        assertTrue("转场不裁剪：\n$body", body.contains("using SizeTransform(clip = false)"))
    }

    @Test
    fun `顶栏实底且内容接上 nestedScroll`() {
        // 用户点名 ④「顶栏…不要透过」：玻璃采的是内容层，传 backdrop 就一定透。
        assertTrue("顶栏要走实底那一路：\n$body", body.contains("opaqueChrome = true"))
        assertFalse("这一屏不许再传玻璃采样源", body.contains("backdrop = topBarBackdrop") || body.contains("blurBackdropSource("))
        // 三处可滚内容都要接 nestedScroll，否则内容从顶栏底下直穿。
        assertEquals("墙、已关注、账号管理各接一次", 3, count("""nestedScroll\(scrollBehavior.nestedScrollConnection\)"""))
    }

    @Test
    fun `整页背景不模糊，窗口不挂 blur-behind`() {
        // 用户点名 ⑤：整页背景不该是模糊背景。
        assertTrue("这一页要铺不透明氛围底", activity.any { it.value.contains("opaqueAmbientBackground: Boolean = true") })
        assertTrue("窗口 blur-behind 要关", activity.any { it.value.contains("blurBehindEnabled: Boolean = false") })
        assertFalse("不再指定 blur-behind 半径", activity.any { it.value.contains("blurBehindDp") })
        val manifest = File(RepoSources.root, "app/src/main/AndroidManifest.xml").readText()
        assertFalse(
            "这一页不许再用那层透明玻璃窗口主题",
            Regex("""\.PixivAccountActivity"[\s\S]{0,160}Theme\.Venera\.GlassOverlay""").containsMatchIn(manifest),
        )
    }

    @Test
    fun `大按钮加高后仍分两级`() {
        // 用户点名 ⑥：大按钮太矮了。一级 48dp（与全站分段器同高）、二级 38dp（仍低一档）。
        val spacing = File(RepoSources.appMain, "com/venera/compose/ui/tokens/Spacing.kt").readText()
        assertTrue("一级那一行要加高到 48dp：\n", spacing.contains("val pixivAccountPrimaryRow: Dp = 48.dp"))
        assertTrue("二级仍是低一档：\n", spacing.contains("val pixivAccountSecondaryRow: Dp = 38.dp"))
    }
}
