package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 顶栏那颗 pixiv 账号头像的**接线尺子**（方案 `docs/rounds/gallery-pixiv-account-pkce-2026-10-17.md` §四 · P3/P4）。
 *
 * 本仓 `app/` 没有 Robolectric，composable 的组合测不到 —— 所以判据全落在**源码文本**上。
 * 这一族的缺陷恰好都是"屏上看不出来"的那一类：外壳换成自绘的 40dp（与右侧那排差 4dp 却没人报）、
 * 未登录摆一枚空圈（读起来像图挂了）、退出挂在点击上（一触即发）、
 * 回跳判定在界面里自拼一次 scheme（改了常量只坏一半）。每条都钉得住。
 *
 * 扫描口径与 `TokenBudgetFrozenTest` / `GalleryHistoryWiringTest` 同一把：注释行不参与匹配。
 */
class GalleryPixivAccountWiringTest {

    private fun codeLines(dir: String, name: String): List<IndexedValue<String>> {
        val file = RepoSources.kotlinFiles(dir).firstOrNull { it.name == name }
            ?: error("找不到 $name（目录 $dir，尝试过的根：${RepoSources.root.absolutePath}）")
        return file.readLines().withIndex().filter { (_, line) ->
            val t = line.trim()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }
    }

    /** 画廊界面那一目录的简写（这个类的大部分判据都在它里面）。 */
    private fun codeLines(name: String): List<IndexedValue<String>> =
        codeLines("com/venera/compose/gallery/ui", name)

    private val account = codeLines("GalleryPixivAccount.kt")
    private val screen = codeLines("GalleryScreen.kt")

    /**
     * 返回**文件里的行号**（0 基），不是过滤后列表里的位置 —— 下面几条判据要拿它跟
     * `IndexedValue.index`（也是文件行号）比大小，两套坐标混一次就会把"下一行"算到几百行之前。
     */
    private fun List<IndexedValue<String>>.indexOfContaining(needle: String, who: String): Int {
        val hit = firstOrNull { it.value.contains(needle) }
        assertTrue("$who 里找不到「$needle」：\n" + joinToString("\n") { l -> "${l.index}:${l.value}" }, hit != null)
        return hit!!.index
    }

    private fun List<IndexedValue<String>>.linesWith(needle: String): List<IndexedValue<String>> =
        filter { it.value.contains(needle) }

    @Test
    fun `头像外壳就是右侧那排用的同一颗，尺寸不自己抄`() {
        // "和其他按钮对齐"靠的是共用外壳（触达 48 / 视觉 40 由 VeneraTopBarPill 自己带），
        // 不是本鱼把那对数字再抄一遍 —— 抄了之后原件改档，这颗会静默差 4dp。
        account.indexOfContaining("VeneraTopBarPill(", "外壳")
        account.indexOfContaining(".size(tokens.spacing.iconButtonSize)", "视觉档")
        val rawDp = account.filter { Regex("""[0-9]+(\.[0-9]+)?\.dp""").containsMatchIn(it.value) }
        assertTrue("顶栏件里不许出现裸 dp：\n" + rawDp.joinToString("\n") { "${it.index}:${it.value}" }, rawDp.isEmpty())
    }

    @Test
    fun `未登录与两档没登上各念一句不一样的话`() {
        // 空圈会被读成"这张图挂了"；而"登过/试过但没成"若念成"没登录"就是方案 §三 那条静默退回。
        // 三档各念一句，靠的是**穷尽 when**（不留 else）：加第四档会编译不过，
        // 而写 else 的话新档会静默并进"登录 pixiv 账号"那一句。
        account.indexOfContaining("PixivAuthState.Anonymous -> \"登录 pixiv 账号\"", "未登录那句")
        account.indexOfContaining("is PixivAuthState.NeedsRelogin, is PixivAuthState.LoginRejected -> \"重新登录 pixiv 账号\"", "两档没登上那句")
        account.indexOfContaining("is PixivAuthState.SignedIn -> \"pixiv 账号\"", "穷尽 when 的已登录那一支")
        // 两句要真是两句话（同一句就不叫"说得出是哪一种"了）。
        assertTrue(
            "两档的字面量不许写成同一串",
            account.linesWith("\"登录 pixiv 账号\"").size == 1 &&
                !account.linesWith("\"登录 pixiv 账号\"")[0].value.contains("重新"),
        )
        // 那一颗 when 不许带 else（带了就等于第四档可以静默并进来）。
        val hint = account.indexOfContaining("val loginHint = when (state) {", "登录态那一问")
        val whenBody = account.filter { it.index > hint && it.index < hint + 6 }
        assertTrue(
            "这一颗 when 里出现了 else，新加一档会静默并进旧话：\n" + whenBody.joinToString("\n") { it.value },
            whenBody.none { it.value.trim().startsWith("else ->") },
        )
    }

    @Test
    fun `回跳判定只有一处，全仓不许出现第二份 pixiv scheme`() {
        // 回跳地址的值只在 `PixivPkce.REDIRECT_URI` 一处，判定只在 `isLoginCallbackUrl` 一处。
        // 界面或 Activity 各拼一份 scheme = 第二把尺子：改了常量只坏一半，表现是
        // "登录页照常打开、回跳永远不算回跳"，而屏上一句报错都没有。
        // 只数**代码行**：这一族有五处注释本来就在解释"不许再拼一份 scheme"，
        // 拿原文扫会把解释 itself 当成违例（本仓那把"名字在不在"的尺子咬到自己）。
        val hits = RepoSources.allMainLines().filter {
            val t = it.third.trim()
            t.contains("pixiv://") && !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) &&
                it.first != "com/venera/compose/gallery/data/PixivPkce.kt"
        }
        assertTrue(
            "除算式那颗之外还有一份字面量：\n" + hits.joinToString("\n") { "${it.first}:${it.second}:${it.third.trim()}" },
            hits.isEmpty(),
        )
        val main = codeLines("com/venera/compose", "MainActivity.kt")
        assertEquals("入口分流只问那一颗静态判据，且只此一处", 1,
            main.linesWith("PixivCredentialState.isLoginCallbackUrl(").size)
        val asked = main.indexOfContaining("PixivCredentialState.isLoginCallbackUrl(", "回跳那一问")
        val comic = main.indexOfContaining("EntryIntent.ComicLink(url)", "交给漫画链接那一支")
        assertTrue("回跳必须排在漫画链接之前（反了就是授权码被念成一句链接错误）：问在第 $asked 行、交在第 $comic 行",
            asked < comic)
    }

    @Test
    fun `退出登录搬进账号页之后仍排在确认那一格里，点头像只做导航`() {
        // 2026-10-19：那颗「这是谁 + 退出」弹层整颗撤掉，退出搬进账号页的「账号管理」那一子页。
        // 两步确认这条纪律**不随搬家放松**：破坏性小把手恒要两步（既有口径）。
        val page = codeLines("com/venera/compose/gallery/ui", "PixivAccountScreen.kt")
        val signOuts = page.linesWith("account?.signOut()")
        assertTrue("退出只许一处调用点，实测 ${signOuts.size} 处", signOuts.size == 1)
        val confirm = page.indexOfContaining("onConfirm = {", "确认格")
        val next = page.first { it.index > confirm }
        assertTrue(
            "退出没紧跟在确认之后（第 ${next.index} 行：${next.value.trim()}）",
            next.value.contains("account?.signOut()"),
        )
        page.indexOfContaining("confirmDestructive = true", "破坏色")
        // 旧那一颗里**一颗退出都不许留**：两颗入口做同一件破坏动作，用户永远说不清按的是哪一颗。
        assertTrue(
            "顶栏那颗件里不许再有 signOut：\n" + account.joinToString("\n") { it.value },
            account.none { it.value.contains("signOut(") },
        )
        // 点头像已登录那一支交的是导航（不是弹层、不是页内状态）。
        account.indexOfContaining("signedIn != null -> context.openPixivAccountPage()", "头像导航")
    }

    @Test
    fun `没配客户端凭据那一支排在起会话之前`() {
        // 顺序反过来就是假开关：会话起不来却已经把登录页开出去了。
        val guarded = account.indexOfContaining("!account.isClientConfigured", "凭据闸门")
        val begun = account.indexOfContaining("account.beginLogin()", "起会话")
        assertTrue("凭据闸门（第 $guarded 行）必须排在 beginLogin（第 $begun 行）之前", guarded < begun)
    }

    /**
     * 登录**交给外部浏览器**（P6，用户「要有跳转到外部网页登录的授权登录和 google 账号登录的办法」）。
     *
     * 2026-10-17 P4 那版判的是"自持一颗 WebView、在里面截回跳"，当时的读数否掉了复用
     * `WebLoginScreen`（它的判定只发在 `onPageFinished` 三处，自定义 scheme 交回 WebView
     * 不会有"页面加载完成"）。那条判据**方向仍然对、宿主已经换**：Google 在自己的 OAuth 页上
     * 认得出嵌入式 WebView 的 user-agent 并直接回 `disallowed_useragent`，所以"用 Google 登录"
     * 那一枚在 WebView 里按下去必然是一页报错 —— 用户点名要的那一档在旧宿主里物理不可达。
     * 于是这里的尺子改成三件事：真走 `ACTION_VIEW`、这一颗件里再没有 WebView、也没借用漫画源那颗。
     *
     * 2026-10-17 第五轮：交出去那一跳多了 Custom Tab（先）与裸 VIEW（兜底）两档 —— 判据跟着拆成两处，
     * 只钉"地址是传进来那一条、没有再拼一份"（`Uri.parse(authorizeUrl)`）。
     */
    @Test
    fun `登录页交给外部浏览器，且这一颗件里没有 WebView 宿主`() {
        account.indexOfContaining("CustomTabsIntent.Builder()", "交给 Chrome 那一跳")
        account.indexOfContaining("Intent(Intent.ACTION_VIEW, uri)", "退回裸浏览器那一档")
        // 两档都必须吃**传进来那一条**地址：自己再 `Uri.parse` 一次别的串就是第二把尺子。
        account.indexOfContaining("val uri = Uri.parse(authorizeUrl)", "地址来源")
        account.indexOfContaining("openPixivLoginInBrowser(context, challenge.authorizeUrl)", "点头像那一跳")
        // 起会话必须排在交出去之前（没配 client 凭据时 beginLogin 回 null，那一页根本不该开）。
        val begun = account.indexOfContaining("account.beginLogin()", "起会话")
        val opened = account.indexOfContaining("openPixivLoginInBrowser(context, challenge.authorizeUrl)", "交出去")
        assertTrue("beginLogin（第 $begun 行）要排在交出去（第 $opened 行）之前", begun < opened)
        for (needle in listOf("WebView", "shouldOverrideUrlLoading", "WebLoginScreen", "AndroidView")) {
            val hits = account.linesWith(needle)
            assertTrue("件里还留着 $needle：\n" + hits.joinToString("\n") { "${it.index}:${it.value}" }, hits.isEmpty())
        }
        // 打不开浏览器必须念得出话，不许静默（runCatching 的返回值要进 notice）。
        account.indexOfContaining("exceptionOrNull()", "打不开浏览器那一档")
    }

    /**
     * 清单里接回跳的那**两条**，必须与算式那侧的常量**同一个值**（P6 新立，第四轮扩成两条）。
     *
     * 这一族是最贵的静默失败：值不一致时浏览器里登录成功、系统找不到接这条地址的 Activity，
     * 于是回到 app 毫无反应，屏上一句报错都没有，用户只会以为"登录没用"。
     * 判据不抄字面量：从常量里把 scheme 与 host 取出来，再去清单里找对应那颗 intent-filter。
     *
     * 2026-10-17 第四轮：站方 App 登录页登完可能直接跳 `pixiv://account/…`，也可能先落到它登记的
     * https 回跳（`REDIRECT_URI`，换 token 时要回填的那一条），所以**两条都得在清单里接住**。
     */
    @Test
    fun `清单里接回跳的那两条与算式里的两个地址同一个值`() {
        val file = java.io.File("src/main/AndroidManifest.xml")
        assertTrue("找不到清单：${file.absolutePath}", file.exists())
        val manifest = file.readText()
        val pairs = listOf(
            "换 token 回填的那一条（https）" to com.venera.compose.gallery.data.PixivPkce.REDIRECT_URI,
            "站方 App 跳回来的那一条（scheme）" to com.venera.compose.gallery.data.PixivPkce.DEEP_LINK_PREFIX,
        )
        for ((who, redirect) in pairs) {
            val scheme = redirect.substringBefore(":")
            val host = redirect.removePrefix("$scheme://").substringBefore("/").substringBefore("?")
            assertTrue("常量本身读不出 scheme/host：$redirect", scheme.isNotBlank() && host.isNotBlank())
            assertTrue(
                "清单里没有接「$redirect」（$who）的 intent-filter —— 浏览器登录成功也回不到 app，" +
                    "而且一句报错都不会有",
                Regex("""android:scheme="$scheme"\s+android:host="$host"""").containsMatchIn(manifest),
            )
        }
    }

    /** 回跳落到 Activity 之后，换票据那一跳只许有一个消费点（P6 接线）。 */
    @Test
    fun `回跳进来之后只有一个换票据的消费点`() {
        val nav = codeLines("com/venera/compose/feature", "Navigation.kt")
        assertEquals("入口意图要有一档专门认 pixiv 回跳", 1, nav.linesWith("is EntryIntent.PixivLoginCallback ->").size)
        assertEquals("换票据只此一处（多一处就是两条路各自换票、state 只够一枚）", 1,
            nav.linesWith("account?.completeLogin(intent.url)").size)
        assertEquals("漫画那两档不许被 pixiv 回跳顶掉", 1, nav.linesWith("is EntryIntent.ComicLink ->").size)
    }

    @Test
    fun `关注画师那一节的话跟登录态同源，且那句「还没接上」已经拔掉`() {
        // 「还没有 X」这类整句状态词会把自己冻成规格：账号系统一落地，那句当场变假话
        // （本仓那句假话活过四批的教训记在 `project-implemented-but-unwired`）。
        val sections = codeLines("GalleryHomeSections.kt")
        val stale = sections.linesWith("还没有 pixiv 账号系统")
        assertTrue("假话还挂在屏上：\n" + stale.joinToString("\n") { "${it.index}:${it.value}" }, stale.isEmpty())
        // 2026-10-17 第八轮：这一栏已经真接上 `/v1/user/following` 与 `/v1/user/recommended`
        // （参数照 pixez-miuix 的 `UserRepository`），「那一条取数还没接上」当场变成假话 —— 同样收进禁令。
        val staleWiring = sections.linesWith("那一条取数还没接上")
        assertTrue(
            "取数已经接上，这句必须消失：\n" + staleWiring.joinToString("\n") { "${it.index}:${it.value}" },
            staleWiring.isEmpty(),
        )
        // 登录态只从 authState 那一条流读，不在节里另算"看着像登过"。
        sections.indexOfContaining("pixiv.credentials?.authState?.collectAsState()", "同源那一读")
        sections.indexOfContaining("登录 pixiv 后，关注和推荐画师会出现在这里。", "未登录那一档")
        // 那一档要念的是判据件里那一份 `screenLine`（P6 之后它是屏上唯一的失败读点）。
        assertTrue(
            "未登录/要重登那一档要用 screenLine 同源念，不在节里另抄一句",
            sections.linesWith("auth.screenLine").isNotEmpty(),
        )
        val copied = sections.linesWith("站方那一页要登录才给")
        assertTrue("节里还抄着一句登录话（应与 screenLine 同源）：\n" + copied.joinToString("\n") { "${it.index}:${it.value}" },
            copied.isEmpty())
    }

    @Test
    fun `首页顶栏真把那颗件挂进了左槽`() {
        // 上一轮那批 notWired 的教训：件写好了却没人挂 = 屏上什么都没有，而测试全绿。
        val nav = screen.indexOfContaining("navigationIcon = {", "顶栏左槽")
        val wired = screen.linesWith("GalleryPixivAccountButton(")
        assertTrue(
            "GalleryPixivAccountButton 只许挂在左槽那一格里（左槽在第 $nav 行，调用在第 " +
                wired.joinToString(",") { "${it.index}" } + " 行）",
            wired.size == 1 && wired[0].index > nav,
        )
    }

    /**
     * 非会员点「人气」改走**人气预览**那一跳（2026-10-19 · 批 233-4，
     * 用户「参考原版 pixez 是怎么做无会员能用这个人气榜的」）。
     *
     * ## 这一族缺陷长什么样
     *
     * 上一版的形状是"非会员点人气 → 拒一句『要会员』并且**连条件都不改**"。那是对的（当时只有会员腿），
     * 但用户要的出口在另一条腿上。接上之后新增的不是"少一个 if"，而是**三条必须落屏的代价**：
     * 只有这一页、词里不带收藏门槛、面板里那几枚压根没发出去。
     * 少了任何一条，屏上就是"日期窗亮着、请求里没有它"—— 本仓零容忍的那一族。
     *
     * 每条各钉一枚，全部钉**那一行的原文**而不是词频（词频会认错邻居）。
     */
    @Test
    fun `非会员点人气走预览腿而丢掉的几枚必须说上屏`() {
        val vm = codeLines("GallerySearchViewModel.kt")

        // ① 选腿的判据：票 + 非会员 + 这一档要人气，三样同时成立才走预览（会员照旧走搜索腿）。
        assertEquals(
            "选腿判据只有一处", 1,
            vm.linesWith("val previewLeg = bearer != null && !premium && filterAtRequest.order.needsPremium").size,
        )
        // ② 两条腿各一处调用，且**不混发**（混起来 = 这一页按一套参数、下一页按另一套）。
        assertEquals("预览腿调用一处", 1, vm.linesWith("port.popularPreview(bearer, query).getOrThrow()").size)
        assertEquals(
            "搜索腿调用一处", 1,
            vm.linesWith("port.searchIllust(bearer, query, filterAtRequest, premium, cursor).getOrThrow()").size,
        )
        // ③ 不接游标：预览腿回的那一枚 next_url 一律不吃（吃了就是第二页按没量过的口径）。
        assertEquals("游标按选腿分岔", 1, vm.linesWith("nextCursor = if (previewLeg) null else page.nextUrl").size)
        // ④ 词里不带收藏门槛：`Nusers入り` 是给搜索腿做的，这一条不认收藏 ⇒ 塞进词里就是"看起来筛过"。
        assertEquals(
            "预览腿用原词", 1,
            vm.linesWith("val query = if (previewLeg) word else filterAtRequest.searchWord(word, premium)").size,
        )
        // ⑤ 读数上屏：走这一条腿的那一刻就把话说掉，而不是等用户自己发现日期窗没生效。
        assertEquals(
            "notice 按选腿分岔", 1,
            vm.linesWith("notice = if (previewLeg) PIXIV_PREVIEW_NOTICE else pixivAccountNotice(bearerReady)").size,
        )
        val noticeLine = vm.linesWith("非会员的人气预览")
        assertEquals("那句读数只有一处声明", 1, noticeLine.size)
        val said = noticeLine[0].value
        for (dropped in listOf("只有这一页", "日期窗", "收藏门槛", "排除 AI", "标签模式")) {
            assertTrue("屏上那句必须点名「$dropped」：$said", said.contains(dropped))
        }
        // ⑥ 门禁只看票：会员那一档的拒法整条撤了（撤不干净就变成"非会员能点而话还说不要会员"）。
        assertEquals(
            "门禁只问票", 1,
            vm.linesWith("if (!pixivLoggedIn || pixivBearerReady == false) PIXIV_NEEDS_ACCOUNT else null").size,
        )
        // 删除类判据按本仓口径扫**全文含注释**：那句"要会员"漂进 KDoc 也算没撤干净。
        val leftovers = RepoSources.allMainLines().filter { (_, _, line) -> line.contains("PIXIV_NEEDS_PREMIUM") }
        assertTrue("会员那一档的话不许留下（含注释）：\n" + leftovers.joinToString("\n") { "${it.first}:${it.third}" },
            leftovers.isEmpty())
    }

    /**
     * 预览腿**三层各接一次**，全树没有第二个调用点（2026-10-19）。
     *
     * 为什么数出现次数而不是数符号名：上一批 notWired 那一族的形状就是"件写好了没人挂"，
     * 而"有人挂了两次"同样读不出 —— 只有逐文件计数能把"契约 1 + 适配 1 + 实现 1 + 解析 1 + 调用 1"
     * 这一张表钉住（合计 7 处，多一处就是第二个调用点，少一处就是断线）。
     */
    @Test
    fun `人气预览三层各接一次而调用点只有搜索那一处`() {
        fun hitsIn(dir: String, name: String, needle: String): Int =
            codeLines(dir, name).linesWith(needle).size

        val data = "com/venera/compose/gallery/data"
        assertEquals("契约那一枚声明", 1, hitsIn(data, "GalleryBusinessApi.kt", "suspend fun popularPreview("))
        assertEquals("适配那一枚转发", 1, hitsIn(data, "GalleryAdaptersAndroid.kt", "appClient.popularPreview(bearer, word)"))
        assertEquals("端点那一枚声明", 1, hitsIn(data, "PixivAppClient.kt", "suspend fun popularPreview("))
        assertEquals("解析那一枚声明", 1, hitsIn(data, "PixivAppParse.kt", "fun popularPreview(body: String)"))
        assertEquals("取数腿只有搜索那一处调用", 1, hitsIn("com/venera/compose/gallery/ui", "GallerySearchViewModel.kt", "port.popularPreview("))

        val total = RepoSources.allMainLines().count { (_, _, line) ->
            val t = line.trim()
            !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*") && t.contains("popularPreview(")
        }
        assertEquals("全树共七处（四处声明 + 两处转发 + 一处调用）", 7, total)
    }
}
