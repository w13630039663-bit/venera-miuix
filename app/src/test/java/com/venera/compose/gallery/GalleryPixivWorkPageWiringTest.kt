package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pixiv 作品页的接线判据（2026-10-18）。
 *
 * 这一屏的三条红线各钉一把尺：
 * 1. **分流只有一处** —— 漏掉任何一个入口，pixiv 就会落进旧看图器，表现是"只有那一面墙点开不对"；
 * 2. **两屏互不吃对方** —— 用户点名"图库源的用回之前的，两个隔离开"；
 * 3. **屏上不许有假数** —— 稿子里那些编出来的数字一个都不许进代码（用户「虚假数据去掉」）。
 *
 * 计数行三格的读数、占位比例、逐页页轴墙、标签 8 枚上限都在 `PixivWorkPageDataTest` 里钉，
 * 这里只管**接没接上**。
 */
class GalleryPixivWorkPageWiringTest {

    private val appMain = RepoSources.appMain

    private fun src(rel: String): String {
        val f = File(appMain, rel)
        assertTrue("找不到 $rel，量具失效", f.isFile)
        return f.readText().replace("\r\n", "\n")
    }

    /** 注释行不参与匹配（与 `GalleryPixivAiBadgeAndSyncTest` 同一把尺子）。 */
    private fun code(rel: String): List<String> = src(rel).lines().map { it.trim() }
        .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private fun List<String>.countWith(needle: String): Int = count { it.contains(needle) }

    private val screen = "com/venera/compose/gallery/ui/PixivWorkScreen.kt"
    private val infoPanel = "com/venera/compose/gallery/ui/PixivWorkInfoPanel.kt"

    /** 「更多信息」那一层现在有两个宿主（作品页 + 播放器那一版面板），装配住在共用那颗里。 */
    private fun hosts(): List<String> = code(screen) + code(infoPanel)
    private val activity = "com/venera/compose/PixivWorkActivity.kt"
    private val postActivity = "com/venera/compose/GalleryPostActivity.kt"
    private val oldScreen = "com/venera/compose/gallery/ui/GalleryPostScreen.kt"
    private val client = "com/venera/compose/gallery/data/PixivClient.kt"
    private val feedParse = "com/venera/compose/gallery/data/PixivFeedParse.kt"
    private val contract = "com/venera/compose/gallery/data/GalleryPorts.kt"
    private val adapter = "com/venera/compose/gallery/data/android/GalleryPortsAndroid.kt"
    private val prefsImpl = "com/venera/compose/data/prefs/VeneraPreferences.kt"
    private val settings = "com/venera/compose/feature/settings/GallerySettings.kt"

    // ── 一、分流只有一处 ─────────────────────────────────────────────

    @Test
    fun `入口只有一颗分流点，pixiv 落作品页而其余落旧看图器`() {
        val host = code(postActivity)
        assertEquals("openGalleryPost 里只许有一处分流判断", 1, host.countWith("if (site == GallerySite.PIXIV)"))
        assertEquals("pixiv 那一支只许走 PixivWorkActivity.intent", 1, host.countWith("PixivWorkActivity.intent(this, postId)"))
        // 别处再开一颗 PixivWorkActivity = 第二个分流点，将来一定有一处漏掉。
        val strays = RepoSources.allMainLines().filter { (rel, _, _) ->
            rel != "com/venera/compose/PixivWorkActivity.kt"
        }.count { (_, _, line) -> line.contains("PixivWorkActivity::class.java") }
        assertEquals("PixivWorkActivity::class.java 只许出现在它自己的 intent() 里", 0, strays)
    }

    @Test
    fun `作品页那颗 Activity 认不出站点就直接炸，不退回某一站取图`() {
        val act = code(activity)
        assertEquals("站点键要现查", 1, act.countWith("requireNotNull(GallerySite.fromRouteKey(siteKey))"))
        assertTrue("不是 pixiv 就要炸，不能悄悄当 pixiv 取图", act.any { it.startsWith("require(site == GallerySite.PIXIV)") })
    }

    // ── 二、两屏互不吃对方 ───────────────────────────────────────────

    @Test
    fun `旧看图器一个字都不认识新作品页`() {
        // 隔离的机械形状：旧屏里搜不到新屏的任何符号。反过来（新屏吃旧屏的界面）也不许。
        assertEquals("GalleryPostScreen 不许引用作品页", 0, code(oldScreen).countWith("PixivWork"))
        assertEquals("作品页不许反过来画旧看图器那一屏", 0, code(screen).countWith("GalleryPostScreen("))
    }

    @Test
    fun `作品页只吃 pixiv 一站，三站站点名一次都不出现`() {
        val body = code(screen)
        assertTrue("作品页要按 pixiv 站点取邻居", body.countWith("GallerySite.PIXIV") >= 1)
        listOf("YANDERE", "GELBOORU", "SAFEBOORU").forEach { site ->
            assertEquals("作品页里出现了 $site —— 两屏没隔离干净", 0, body.countWith("GallerySite.$site"))
        }
    }

    // ── 三、两枚档位：契约、实现、适配器、读点、写点各就各位 ──────────
    //
    // 从前这里是三枚，第三枚叫「多图浏览方向」。2026-10-18 第四批按用户那句
    // 「还是直接删了横着一页一页那个模式切换吧，只留竖着连续翻页模式」整档撤掉；
    // 它不留半套那一格由下面「横档模式整档撤干净」钉，不在这里靠"少写一枚"来表达。

    @Test
    fun `两枚档位每枚都是契约一枚声明、实现一枚、适配器一枚、屏上一读、设置一写`() {
        listOf(
            "pageSeam" to Triple(
                "pixivWorkSeam", "workPrefs.pageSeam.collectAsState()", "setPixivWorkSeam(PixivPageSeam.valueOf(it))",
            ),
            "detailsInFlow" to Triple(
                "pixivWorkDetailsInFlow", "workPrefs.detailsInFlow.collectAsState()",
                "setPixivWorkDetailsInFlow(it)",
            ),
        ).forEach { (member, row) ->
            val (storage, read, setter) = row
            assertEquals("$member 在契约里只声明一次", 1, code(contract).countWith("val $member:"))
            assertEquals("$member 在适配器上只接一次", 1, code(adapter).countWith("override val $member:"))
            assertEquals("$storage 在实现类上只存一处", 1, code(prefsImpl).countWith("val $storage: StateFlow"))
            assertEquals("$member 在作品页只读一处", 1, code(screen).countWith(read))
            assertEquals("$storage 在设置页只写一处", 1, code(settings).countWith(setter))
        }
    }

    /**
     * ⚠️ 这一条钉的是**挂在哪一颗接口上**，不是"有没有这三枚"。
     *
     * 起因（2026-10-18）：这几枚一开始挂在 `GalleryPreferences` 上 —— 那颗是**跨端共享**读面，
     * 桌面那颗 `DesktopGalleryPreferences` 逐枚实现它，挂进去就是让桌面替每一档各写一个没人读的
     * getter，还要把桌面那笔"每一枚都有下落"的账一起改宽。摘回隔壁 `PixivWorkPreferences` 后，
     * 上面那条加法判式对"挂错接口"这件事是**全绿**的（它只数名字），所以这一条单独成一颗。
     */
    @Test
    fun `两枚档位住在 PixivWorkPreferences 而不是跨端共享的那颗读面`() {
        val body = code(contract)
        assertEquals("契约要单独一颗接口", 1, body.countWith("interface PixivWorkPreferences"))
        assertEquals("容器上要有一颗可空的 pixivWork 槽（桌面不接）", 1, body.countWith("val pixivWork: PixivWorkPreferences? = null"))
        // 共享读面上这三枚的名字一枚都不许出现（挂回去 = 桌面被迫实现三枚死档）。
        listOf("pixivWorkSeam", "pixivWorkDetailsInFlow").forEach {
            assertEquals("$it 不许出现在跨端共享读面那颗文件里", 0, body.countWith("val $it:"))
        }
        // 对照组：上面那三条不是"文件读空了才恒 0"。
        assertTrue("对照组失效：没读到接口本体", body.any { it.startsWith("interface GalleryPreferences {") })
    }

    @Test
    fun `两枚键名与默认档钉死（默认=留一条边+详情接在末页后）`() {
        val body = code(prefsImpl)
        listOf(
            "\"pref_pixiv_work_seam\"",
            "\"pref_pixiv_work_details_in_flow\"",
        ).forEach { assertTrue("键名漂了：$it", body.any { line -> line.contains(it) }) }
        assertTrue("接缝默认档不再是一条边", body.any { it.contains("KEY_PIXIV_WORK_SEAM, PixivPageSeam.HAIRLINE") })
        assertTrue("详情接续默认档不再是开", body.any { it.contains("KEY_PIXIV_WORK_DETAILS_IN_FLOW, true") })
    }

    /**
     * 「只留竖着连续翻页」的机械形状：那一档的**每一层**都不许留副本，含注释与 KDoc 里的名字。
     *
     * 判式扫的是整棵 `app/src/main` 的**每一行**（不过滤注释）—— 这一族的旧账不是"代码里还剩一处调用"，
     * 而是"注释里还写着它有另一档"：那种句子会被后来的人当事实引用（本仓为这条栽过六次）。
     * 屏上那一档现在只剩一颗 `PixivWorkFlow`（定义 + 唯一一处调用 = 2），所以顺带钉住"没有第二支"。
     */
    @Test
    fun `横档模式整档撤干净，注释里也不许留名字`() {
        val all = RepoSources.allMainLines()
        listOf(
            "PixivAxis", "pixivWorkAxis", "pref_pixiv_work_axis", "setPixivWorkAxis", "多图浏览方向",
            "horizontalSlotCount", "advancesToNextWork", "fillsViewport", "PixivNextWorkSlot", "boundaryFiredFor",
        ).forEach { needle ->
            val hits = all.filter { it.third.contains(needle) }.map { "${it.first}:${it.second}" }
            assertEquals("「$needle」已经随横档一起没，残留：$hits", 0, hits.size)
        }
        // 只剩一档：一颗定义 + 一处调用，多一处就是又长出第二支
        assertEquals("翻页流只有一颗、只被调一次", 2, code(screen).countWith("PixivWorkFlow("))
        // 对照组：上面那串零残留不是因为整棵树没读到
        assertTrue("对照组失效：没读到作品页", code(screen).any { it.startsWith("fun PixivWorkScreen(") })
        assertTrue("对照组失效：档名那一行读到了", all.any { it.third.contains("PixivPageSeam") })
    }

    /**
     * 画师那一行的两枚把手（**关注** / **屏蔽**）必须真接线 —— 用户 2026-10-18 在稿上点名的两件。
     *
     * 存在性判式在这里不够：摆一枚「关注」而 onClick 是空的，屏上看着对、按下去不动，
     * 而"有没有那一枚"这种尺子全绿。所以钉的是**onClick 里出现的是哪颗写口**，
     * 外加屏蔽存的**规则类型必须是 AUTHOR**：照抄画师卡那条 TAG 会在 pixiv 存一条永远挡不住的规则
     * （pixiv 的 tags 是作品标签，画师名不在里面），而 Toast 会照样说"已屏蔽"。
     */
    @Test
    fun `画师那一行的关注与屏蔽各接到自己那枚真写口`() {
        // 装配在共用那颗（`PixivWorkInfoPanel.kt`）：两个宿主各抄一份写口就是等着分叉的两处。
        val body = hosts()
        assertEquals("关注判定只从共享那颗走一次", 1, body.countWith("GalleryArtistFollows.isFollowing(follows, GallerySite.PIXIV, author)"))
        assertEquals("关注写口只在这一处调", 1, body.countWith("ports.follows.toggle(GallerySite.PIXIV, author)"))
        assertEquals("屏蔽存的是 AUTHOR 规则，且用既有的 >= 0 判成功", 1, body.countWith("guard.addRule(\"AUTHOR\", author) >= 0"))
        assertEquals("守卫只从 ports 取一次", 1, body.countWith("val guard = ports.contentGuard"))
        assertEquals("关注那一枚只有一份措辞（两处渲染共用同一个 composable）", 1, body.countWith("if (artist.isFollowed) \"已关注\" else \"关注\""))
        assertEquals("屏蔽那一项只有一枚", 1, body.countWith("Text(\"屏蔽该画师\""))
        // 对照组：上面那几条不是"文件读空了才恒 1"。
        assertTrue("对照组失效：没读到作品页本体", code(screen).any { it.startsWith("fun PixivWorkScreen(") })
        assertTrue("对照组失效：没读到共用装配那颗", code(infoPanel).any { it.startsWith("internal fun pixivArtistActions(") })
    }

    // ── 四、接缝与详情接续：钉值本身，不钉"有没有写 padding" ─────────

    @Test
    fun `页间距只有两档且取自现成 token，没有第三处各写各的`() {
        val body = code(screen)
        assertEquals(
            "两档只能从这一句出来（写死 0 也能骗过存在性判式，所以钉整串）",
            1,
            body.countWith("if (seam == PixivPageSeam.NONE) tokens.spacing.none else tokens.spacing.hairline"),
        )
        assertEquals("这一档只用在页与页之间那一处", 1, body.countWith("Spacer(Modifier.height(gap))"))
    }

    @Test
    fun `详情段进不进这条流只看那一枚开关，且全程一条滚动容器`() {
        val body = code(screen)
        assertEquals("详情段只由那一枚开关决定进不进流", 1, body.countWith("if (detail != null && detailsInFlow)"))
        // 一个滚动容器：这条流是一棵 LazyColumn，页与详情都是它的 item。
        assertEquals("作品页自己不许再起第二棵滚动墙", 1, body.countWith("LazyColumn("))
        assertTrue("详情段是 item 而不是第二个滚动者", body.any { it.contains("item(key = \"detail-") })
    }

    // ── 五、多页对账：静默交半份就是假话 ─────────────────────────────

    @Test
    fun `多页要逐页取齐并对账，取不齐整条失败而不是退回第一页`() {
        val cli = code(client)
        assertEquals("单页不多打那一跳", 1, cli.countWith("if (head.pageCount <= 1) return@runCatching head"))
        assertEquals("多页取齐之后才交出去", 1, cli.countWith("head.copy(pages = pages)"))
        assertTrue(
            "对账要钉在认出来的枚数上，不是钉在「有没有地址」上",
            code(feedParse).any { it.contains("if (urls.size != expected)") },
        )
    }

    // ── 六、屏上不许有假数（稿子里那些编出来的值一枚都不许进代码） ────

    @Test
    fun `稿子里那些编出来的数字与名字一枚都不许进代码`() {
        val body = src(screen) + src(activity) + src(infoPanel)
        listOf("851", "1.2k", "38k", "32 收藏", "Kakkoi", "Big Red", "jaymonoto", "コイカツ", "東方Project").forEach { fake ->
            assertEquals("作品页里出现了稿子用的假数「$fake」", -1, body.indexOf(fake))
        }
    }

    @Test
    fun `计数行只从纯判据那一颗取值，不各写各的`() {
        assertEquals("三格计数只有一处来源", 1, code(screen).countWith("PixivWorkPage.facts("))
        // 2026-10-18 第四批改口径：评论那一格的**数与出口合到动作行同一枚**，详情段不再抄一份。
        // 那半的判式在「评论的数与出口只在动作行念一遍」那颗；这里只钉"详情段不再自己念"，
        // 两把尺各钉各的一格（同一件事不许有两把尺子）。
        assertEquals("详情段不再自己念评论数", 0, code(screen).countWith("val comments = detail.commentCount"))
        assertEquals("详情段不再自己摆评论那一行", 0, code(screen).countWith("条 · 在浏览器里看"))
        // 对照组：上面两枚零不是"作品页那颗文件读空了"
        assertTrue("对照组失效：没读到计数行", code(screen).any { it.contains("PixivWorkPage.facts(") })
    }

    // ── 七、动图不装作能放 ───────────────────────────────────────────

    @Test
    fun `动图明说一句并给一条真出口，不画成一张静帧冒充能播`() {
        val body = code(screen)
        assertTrue("ugoira 没被分流出去", body.any { it.contains("if (detail?.ugoira == true)") })
        assertTrue("那一屏没有明说的出口", body.any { it.contains("UgoiraNotice { onOpenViewer(detail.post.id) }") })
        // 2026-10-19 用户报「动图这一作整屏上半是白的」：那一支从前只摆一句字、静帧不画。
        // 现在第一帧要真摆上去（同一颗 `PixivPageImage`，不为静帧另写一支渲染件），
        // 而"只画得出第一帧"这句明说照旧在图下面 —— 两件事一起钉。
        assertTrue(
            "动图那一支没把第一帧摆上去",
            body.any { it.contains("val still = pages.firstOrNull()") },
        )
        assertEquals(
            "静帧复用同一颗渲染件（不另写第二支）",
            2,
            body.count { it == "PixivPageImage(" },
        )
        assertEquals(
            "静帧也接同一条点击出口",
            2,
            body.countWith("onClick = { rect -> openPages("),
        )
        assertEquals("旧看图器收到动图那一支", 1, code(activity).countWith("Intent(this, GalleryPostActivity::class.java)"))
    }

    // ── 八、无障碍：每一页都自带"第 N 页 / 共 M 页" ──────────────────

    @Test
    fun `页码两处说法都在，接缝那条边不承担唯一说法`() {
        val body = code(screen)
        assertTrue("每页的语义没了", body.any { it.contains("contentDescription = description") })
        // 2026-10-19 用户：「去掉中间的页数指示器」—— 顶栏那枚「1 / N」胶囊整格撤掉，
        // 屏上不再念页位。这一格的语义**不再缺**：每页自己的 `pageSemantic` 仍逐页念
        // 「第 N 页 / 共 M 页」，读屏用户照样拿得到页位（撤的只是视觉那一枚）。
        assertEquals(
            "顶栏那枚页码胶囊已撤干净",
            0,
            body.countWith("第 \${pageIndex + 1} 页，共 \${pages.size} 页"),
        )
        assertEquals("页码语义只从 pageSemantic 一颗出来", 1, body.countWith("private fun pageSemantic("))
    }

    // ── 九、第二轮改口径（用户 2026-10-18 真机五条里的第一批）──────────

    /**
     * ① 屏底不再常驻一排动作。
     *
     * 立案读数：顶栏那枚「更多信息」与 dock 里的「详情」**打开的是同一层**（都是 `sheetOpen = true`），
     * 用户读到的是一枚重复入口；而五枚常驻图标把图压掉一屏高。
     * 撤 dock、四枚动作搬进弹层那一层，但**失败横幅留在屏上** —— 取数失败时弹层是空的，
     * 把错误也搬进去等于让用户对着一张白屏找"为什么没图"。
     */
    @Test
    fun `屏底不再常驻一排动作，四枚搬进弹层而失败横幅留在屏上`() {
        // 四枚动作的**挂载**住在共用那颗（两个宿主共用一份动作行），屏体与 chrome 住在作品页。
        val body = hosts()
        assertEquals("dock 那一整排撤干净（定义与调用一起没）", 0, body.countWith("DockAction("))
        assertEquals("PixivWorkDock 那颗不再存在", 0, body.countWith("PixivWorkDock("))
        assertEquals("「更多信息」入口只许有一枚（从前顶栏与 dock 各一枚，进的是同一层）", 1, body.countWith("Icons.Outlined.MoreVert"))
        listOf("actions.onFavorite()", "actions.onDownload()", "actions.onShare()", "actions.onOpenComments()").forEach {
            // 钉的是"这一枚在动作行里挂了一次"。宿主各自还有一行把下载接到自己的进度上
            // （`params.onDownload()` / `saveWork(...)`），那不是第二次挂载，所以needle带上接收者。
            assertEquals("$it 只该在弹层那一层被挂一次", 1, body.countWith(it))
        }
        assertEquals("失败横幅仍在屏上", 1, code(screen).countWith("if (failure != null)"))
        // 对照组：读空了上面全部恒真。
        assertTrue("对照组失效：没读到作品页本体", body.any { it.startsWith("fun PixivWorkScreen(") })
    }

    /**
     * ② 一页只有一支渲染件，"铺满视口"那枚形参整格掉出去。
     *
     * 立案读数（真机，2026-10-18 第三批）：横向下"图片只会 1/4" —— 那一档的页高由 pager 给而
     * 渲染件不用，两支靠 `fillsViewport` 分开。第四批把横向那一档整档撤了 ⇒ 这枚形参**没有第二种
     * 取值**，留着就是"漏传不报错"那一族（本仓为这条立过案）：整格撤，判式改钉它为零。
     */
    @Test
    fun `一页只有一支渲染件，铺满视口那枚形参整格掉出去`() {
        val body = code(screen)
        assertEquals("渲染件只有一颗", 1, body.countWith("private fun PixivPageImage("))
        assertEquals("铺满视口那一档不再存在", 0, body.countWith("fillsViewport"))
        // 页那一格按"站报的真比例 + 通栏宽"摆，比例只从 domain 那一颗问
        assertEquals("骨架格的比例只有一处来源", 1, body.countWith("PixivWorkPage.placeholderAspect("))
    }

    /**
     * ③ 这一屏摆**中档**，并且图回来之前有骨架。
     *
     * 立案读数：多页腿从前只取 `urls.original` ⇒ 开屏就拉四张原图，而且没有宽高比占位（"没骨架"）。
     * 骨架复用现成的 `VeneraShimmer`（大图页一档没落位时用的就是它），比例只从 domain 那一颗问 ——
     * 屏上自己除一次宽高就是第二把尺子。
     */
    @Test
    fun `作品页摆中档并复用现成骨架，原图档不在这一屏出现`() {
        val body = code(screen)
        assertEquals("骨架用现成那颗，不造新的", 1, body.countWith("VeneraShimmer("))
        assertEquals("占位比例只从 domain 那一颗问", 1, body.countWith("PixivWorkPage.placeholderAspect("))
        // 原图档只住在大图播放器那一层的 HD 档（第二批）；这一屏出现它就是又一处开屏拉原图。
        assertEquals("这一屏不许直接取原图档", 0, body.countWith("originalUrl"))
        // 缩放层整颗搬去播放器（用户已批"双击只在大图播放器里"）。留着还有一处真缺陷：
        // 连读那一档的 maxHeight 是 Infinity ⇒ 上限算出来是 Infinity，捏合直接失灵。
        assertEquals("这一屏不再挂缩放层", 0, body.countWith("rememberZoomableState("))
        assertEquals("也不再挂双击", 0, body.countWith("DoubleClickToZoomListener"))
    }

    /**
     * ⑥ 画师头像按 `authorId` 现取。
     *
     * 立案读数（真机）：头像那一格"只显示占位符" —— 成因是本屏压根没调那条腿
     * （`AuthorAvatar` 只画首字母），而站方详情腿给了 `userId`，现成的 `pixivAvatarUrl(userId)`
     * 就是为这一格准备的（`/ajax/user/{id}` → `imageBig`/`image`，地址按 2026-10-01 那条改判落盘）。
     */
    @Test
    fun `画师头像按用户号现取，首字母座那颗整颗撤掉`() {
        // 取地址那一颗也住在共用件里（两个宿主都要摆脸），所以这里分成两把尺而不是数一枚总数：
        // **调站方那条腿**只有一处，而**画脸**在两处 —— 数错对象会把"两处各取一次"读成合规。
        assertEquals("取地址只调那一颗", 1, code(infoPanel).countWith("ports.artists.pixivAvatarUrl(userId)"))
        assertEquals("取地址那颗的定义只有一枚", 1, code(infoPanel).countWith("internal fun pixivAvatarUrl("))
        assertEquals("首字母座那颗撤掉（现成件里已带兜底，屏上不另写一份）", 0, hosts().countWith("AuthorAvatar("))
    }

    // ── 十、第三批改口径（用户 2026-10-18 真机两张图）───────────────────

    /**
     * ① 屏底摘要条整颗撤掉。
     *
     * 立案读数（真机两张图）：连读那一档滚到详情段时，摘要条与它正上方那行画师信息是
     * **同一份读数的两遍**；而横向档按用户那句「横着一页一页翻应该和竖着连续翻页模式结构一样」
     * 也要同一套 chrome —— 两支一起撤，不留半套。
     * 撤完之后顶栏那枚「更多信息」仍在，失败横幅留在屏上（取数失败时屏上必须念得出为什么没图）。
     */
    @Test
    fun `屏底摘要条整颗撤掉，一档共用同一套 chrome`() {
        val body = code(screen)
        assertEquals("摘要条那颗连定义带调用一起没", 0, body.countWith("PixivWorkPeek"))
        assertEquals("它专用的那层不透明底也跟着撤", 0, body.countWith("CHROME_ALPHA"))
        // 撤的是摘要条那一处；详情段里那行画师信息是这一段唯一的脸，必须还在
        assertEquals("详情段仍画一次头像", 1, body.countWith("GalleryArtistAvatar("))
    }

    /**
     * ④ 顶栏那两枚按钮走全站同一颗玻璃座（批次 H 定的「顶栏图标按钮唯一形态」）。
     *
     * 从前这里自己写了一颗实心圆（`surfaceContainerHigh` 一块底），与全站那把尺不是一枚件。
     * 换 `VeneraTopBarPill` 要**三件一起接**，少任何一件的症状都是"按钮底板整个没了"
     * （那三条判据写在那颗件的 KDoc 上）：采样源、采样层挂载、把采样层递下去的那枚 CompositionLocal。
     */
    @Test
    fun `顶栏按钮换成全站那颗玻璃座，采样源三件一起接`() {
        val body = code(screen)
        assertEquals("自己那颗实心圆座撤掉", 0, body.countWith("ChromePill"))
        assertEquals("顶栏两枚按钮各一颗", 2, body.countWith("VeneraTopBarPill("))
        assertEquals("采样源只记一份", 1, body.countWith("rememberTopBarBackdrop("))
        assertEquals("被采样的那一层只有一处", 1, body.countWith(".blurBackdropSource("))
        assertEquals("采样层递下去只在一处", 1, body.countWith("LocalTopBarBackdrop provides"))
    }

    /**
     * ③ 顶栏那一格：图**穿到状态栏之上**，压在那一格上的改成全站主页面同一颗模糊背板。
     *
     * 两处改口径（用户 2026-10-18 第四批，把第三批那版推翻）：
     * - 第三批做的是"内容整块往下让位 + 空出来的那一格拿当前页大图的模糊版垫底"，用户看了要改成
     *   「上滑的时候图片还是能过状态栏，不过要有我们现在所有主页面都有的顶栏模糊效果」⇒ 让位那两笔
     *   整格撤（判式改钉 0），顶栏那一行只留一颗背板；
     * - 背板不再自绘一份模糊，改用全站那颗 `VeneraTopBarBackdrop`（渐变高斯模糊 + 轻补底），它从
     *   `ComicDetailScreen` 里那颗 private 件提上来共用 —— 屏上"顶栏磨砂"从此只有一把尺。
     *   （那颗件从前还画一条下沿 hairline，2026-10-19 用户「全部顶栏那种线去掉」，两处一起撤了。）
     * 高度那一格仍然只从"状态栏 + 顶栏塌下来那一档"这一句出来，不写死数。
     */
    @Test
    fun `图穿到状态栏之上，顶栏那一格改由全站同一颗模糊背板压底`() {
        val body = code(screen)
        assertEquals(
            "顶栏那一格的高度只从这一句出来",
            1,
            body.countWith("val topBandHeight = statusBarTop + tokens.spacing.topBarCollapsedHeight"),
        )
        assertEquals("内容不再往下让位（第三批那一版已被第四批推翻）", 0, body.countWith(".padding(top = topBandHeight)"))
        assertEquals("当前页大图模糊垫底那一版已被推翻，本屏不再用它", 0, body.countWith("CoverHeroBackdrop("))
        assertEquals("顶栏背板只走全站那颗件、只有一处", 1, body.countWith("VeneraTopBarBackdrop("))
        // 那颗件全仓只有一颗定义，且ComicDetailScreen 里那份 private 抄本不再存在
        assertEquals(
            "全站顶栏模糊背板只有一颗定义",
            1,
            RepoSources.allMainLines().count { it.third.contains("fun VeneraTopBarBackdrop(") },
        )
        assertEquals(
            "旧的 private 抄本没留下",
            0,
            RepoSources.allMainLines().count { it.third.contains("private fun DetailTopBarBackdrop(") },
        )
    }

    /**
     * ② 只剩竖连：页码由那条流的第一个可见项喂，末尾那一格只推外层 pager。
     *
     * 两把尺各钉一件事：
     * - **页码要真跟手** —— 立案读数：`pageIndex` 从前只由连读那条流喂，横向档压根不走那条流，
     *   顶栏永远念「1 / N」（屏上一句假数，编译器不报）。现在写它的位置只许有一处；
     * - **末尾那一格只在认得出下一位时才摆** —— 判据在调用方那一句（`works.getOrNull(slot + 1)`），
     *   屏上那一格是**提醒 + 真出口**：按下去推的就是那一枚外层 pager，没有第二种换作品写法。
     */
    @Test
    fun `页码只有一处写，末尾那一格只推外层 pager`() {
        val body = code(screen)
        // 2026-10-19 批 233-2 之后写页码的那一处搬到宿主，**喂数的那一处**留在槽里：
        // 一颗共享的 LazyListState 会被同屏另一槽的滚动改写（横翻回来页码念的是隔壁那一槽）。
        assertEquals("页码跟手只有一处写", 1, body.countWith("if (slotId == currentId) pageIndex = index"))
        assertEquals("喂页码的那把尺只问第一个可见项", 1, body.countWith("snapshotFlow { listState.firstVisibleItemIndex }"))
        // 旧那串把"夹到最后一页"写在这次赋值里，搬家后它落在槽内那一处：这一枚钉的是**夹还活着**，
        // 不钉它在哪一行（钉行号会把下一次同名搬家算成违例）。
        assertEquals("尾项号仍要夹到最后一页", 1, body.countWith("it.coerceAtMost(pages.size - 1)"))
        assertEquals("下一位只从墙里的顺序认", 1, body.countWith("nextWork = works.getOrNull(slot + 1)"))
        assertEquals("末尾那一格只有一颗渲染件（定义 + 一处调用）", 2, body.countWith("PixivNextWorkRow("))
        assertEquals("认不出下一位就不摆那一格", 1, body.countWith("if (nextWork != null) {"))
        assertEquals("换作品只推那一枚外层 pager", 1, body.countWith("pagerState.animateScrollToPage(slot + 1)"))
    }

    // ── 十一、第四批：动作行念数、标签铺开、评论只在动作行念一遍 ───────

    /**
     * ④ 标签段按用户给的官方那张稿重排（节头一枚图标 + 把手在右 + 整片就地铺开）。
     *
     * 撤掉的是"四枚一列 × 两列"那把硬切：长短不一时右列被短标签拉出洞，而藏在尾上的那枚把手
     * 读起来像第四枚标签。**枚数上限不变**（还是 domain 那一颗的 8），改的只是排法与措辞。
     */
    @Test
    fun `标签段铺开成一行流，节头带图标而把手改念「查看全部」`() {
        val body = code(screen)
        // 批 233-3 起评论正文也要铺开（文字段 + 小表情一枚一行），所以这里是**两处**：标签那一处 + 评论那一处。
        // 评论那一处的形状由后面那条 `评论默认只摆最新一条…` 单独钉，不在这一条里替它说话。
        assertEquals("铺开两处 FlowRow（标签 + 评论正文的分段）", 2, body.countWith("FlowRow("))
        assertEquals("节头那枚图标", 1, body.countWith("Icons.Outlined.LocalOffer"))
        assertEquals("把手只念「查看全部」", 1, body.countWith("查看全部"))
        // needle 带引号：钉的是标签把手那句旧措辞「更多」，而 2026-10-19 规格照给评论节头
        // 补了一枚真把手「更多评论」—— 裸串会把那一句也算成违例（尺子咬到邻居，不是邻居错了）。
        assertEquals("旧那句把手措辞没留下", 0, body.countWith("\"更多\""))
        assertEquals("旧那把硬切没留下", 0, body.countWith("chunked(4)"))
        // 上限仍然只问 domain 那一颗（两把尺一头：屏上不自己截断）
        assertEquals("铺开的枚数只从 domain 那一颗问", 1, body.countWith("PixivWorkPage.tagsShown("))
        assertEquals("藏着的枚数也只从那一颗问", 1, body.countWith("PixivWorkPage.tagsHidden("))
        assertEquals("把手只在真藏着枚数时摆（0 就是假开关）", 1, body.countWith("!tagsExpanded && hidden > 0"))
    }

    /**
     * ④＋⑥ 评论这一件事在屏上只念一遍：动作行那枚同时带**数**与**出口**。
     *
     * 立案读数：详情段里那行「评论 N 条 · 在浏览器里看」与动作行那枚图标念的是同一个数、
     * 给的是同一条出口（用户 2026-10-18 第四批「优化下面的按钮和 tag 展示 ui，按照图三」）。
     * 撤的是重复那一半，不是出口本身 —— 匿名腿取不到评论内容，所以"点了没反应"的把手一枚都不许有。
     */
    @Test
    fun `评论的数与出口只在动作行念一遍，详情段不再重复`() {
        assertEquals("详情段不再自己念评论数", 0, code(screen).countWith("val comments = detail.commentCount"))
        assertEquals("详情段不再自己摆评论行", 0, code(screen).countWith("条 · 在浏览器里看"))
        val panel = code(infoPanel)
        assertEquals("动作行那枚评论带的是真数", 1, panel.countWith("count = actions.commentCount,"))
        assertEquals("动作行那枚收藏带的是真数", 1, panel.countWith("count = actions.favoriteCount,"))
        assertEquals("评论那一枚的措辞只有一份", 1, panel.countWith("在浏览器里看评论"))
        assertEquals("数只从动作行那一颗渲染件出来", 1, panel.countWith("if (count != null) {"))
        // 对照组：那几枚零不是"文件读空了才恒 0"
        assertTrue("对照组失效：没读到共用装配那颗", panel.any { it.startsWith("internal fun pixivWorkActions(") })
        assertTrue("对照组失效：没读到动作行那颗", panel.any { it.startsWith("private fun WorkAction(") })
    }
    // ─────────────────────────────────────────────────────────────────────
    // 批 231-E：墙 ↔ 作品页的 hero 转场（2026-10-19）
    // ─────────────────────────────────────────────────────────────────────

    /** 墙那两排卡的宿主（排行榜 + 推荐共用同一颗 `PixivWorkCard`）。 */
    private val pixivHome = "com/venera/compose/gallery/ui/GalleryPixivHome.kt"

    /**
     * **两端必须用同一把键**（`viewerKey`，不是 `uid`）。
     *
     * 这条是这一批最容易静默写错的一处：墙回写落点若用 `uid`、作品页离场查 `viewerKey`，
     * 两边各自都"写对了"，只是永远查不到 —— 屏上的表现是"返回时整页抬回去"，不报错、不崩溃，
     * 谁也不会来报。`GalleryPostScreen` 那颗文件头注里记着同一条。
     */
    @Test
    fun `墙回写的落点与作品页查询的键是同一把 viewerKey`() {
        val home = code(pixivHome)
        // 墙：写落点用 viewerKey（写错成 uid 就查不到）。
        // （2026-10-19 批 B §3 地基：登记改吃双矩形 `(键, full, visible)` —— 定位那把要没裁过的。）
        assertEquals(
            "墙回写落点必须用 viewerKey",
            1,
            home.countWith("GalleryFlyIn.noteCardBounds(work.post.viewerKey, full, bounds)"),
        )
        assertEquals(
            "墙的回写用 uid 是那一族静默错的形状",
            0,
            home.countWith("GalleryFlyIn.noteCardBounds(work.post.uid"),
        )
        // 卡片离场时要把这一条的落点摘掉（不摘 = 隔一批还留着上一个人的框）。
        assertEquals(
            "卡片销毁要摘自己的落点",
            1,
            home.countWith("GalleryFlyIn.forgetCardBounds(work.post.viewerKey)"),
        )
        // 只在真的变了才写：布局回调每帧都来，无条件赋值 = 每帧重组这一格。
        // （2026-10-19 批 A：登记从整卡搬到封面那一块，局部量随之改名 coverRect。）
        assertTrue(
            "回写必须带'变了才写'的闸门（否则滚动每帧重组）",
            home.any { it.contains("if (bounds != coverRect)") },
        )
    }

    /**
     * 作品页那一侧：落点查询**必须存在**（今天这一半是断的 —— 只回写、从不取用）。
     *
     * ⚠️ 这条只钉"查了没有"，不钉"动画长什么样"：离场那套动画是 `PixivWorkScreen` 自己的事，
     * 判据去描述它会变成描述实现。
     */
    @Test
    fun `作品页离场要按同一把键取落点`() {
        val body = code(screen)
        assertTrue(
            "作品页离场必须按 viewerKey 查墙上那一格",
            body.any { it.contains("GalleryFlyIn.cardBoundsOf(") },
        )
        assertEquals(
            "不许用 uid 去查（与墙那侧的写入口径必须一致）",
            0,
            body.countWith("GalleryFlyIn.cardBoundsOf(post.uid"),
        )
        // 2026-10-19 批 A 真机验收补（读数 17:25:38，`pixiv:150828286`）：历史那一格**落点就在表里
        // 登记着，返回照样 target=null 整页退** —— 旧版第一跳只吃邻居队列，而历史的 tapTile
        // 只写飞行交接件、从不写 GalleryViewerQueue（队列认不出只等于"没有邻居"，
        // 不等于"这件作品没有落点"）。身份链必须退回本件作品的详情 post：它的 viewerKey
        // 由 `site.routeKey:id` 长出（pageOrdinal 只住页轴，列表腿与详情腿都是 null），
        // 与墙登记那侧逐字同串 —— 是同一把键的另一条来路，不是第二把键。
        // 钉整串而不是钉"有没有 detail?.post"：存在性判式拦得住删掉，拦不住"退到别的什么上"。
        //
        // 批 B②（2026-10-19）之后有两个消费者：**终点**查作品键、**起点**查逐页键。
        // 身份链仍只许一处：起点吃 `back` 手里那颗 `post`（`post?.let { exitStartOrNull(it) }`），
        // 不另起一条 —— 两句话逐字相同就是第二处真相，明天改链的人只改一处，
        // 起点就悄悄退化回旧行为（这一笔就是那天被接线尺当场红出来的）。
        assertEquals(
            "离场取身份只许这一句（队列 → 本件详情 post → null）",
            1,
            body.countWith("val post = works.getOrNull(workIndex) ?: detail?.post"),
        )
        assertEquals(
            "起点复用 back 手里那颗 post，不另起一条身份链（批 B②）",
            1,
            body.countWith("post?.let { exitStartOrNull(it) }"),
        )
        assertEquals(
            "起点那一颗只换页位再查（屏上不自己拼 \":pN\"）",
            1,
            body.countWith("val pagePost = post.copy(pageOrdinal = pageIndex)"),
        )
    }

    /**
     * 批 B②（2026-10-19）：离场起点从**整屏**换成**当前那一页图块**。
     *
     * 立案（§9.3b）：多 P 作翻到第 3 页按返回，旧代码截 `fullFrameBox`（整屏）⇒ 飞行体带着
     * 整摞页 + 动作条 + 标签缩回一张小卡，而落点是一张作品卡 —— 两者不是同一张画，
     * 末段只能淡掉（`exitBodyAlpha`）才读得下去。换成当前页之后飞行体带的是用户正在看的那一页。
     *
     * 三笔各钉一件事：
     * 1. 截的那一块**不许**再是整屏（`fullFrameBox` 从 captureRegion 的实参里退场）；
     * 2. 起点来自 `exitStartOrNull()`（那一页图块的矩形），拿不到 ⇒ 整页退，不演；
     * 3. 页位来自**每槽自报**的 `pageIndex`（不是宿主自己猜的那一颗）。
     */
    @Test
    fun `离场起点是当前那一页图块不是整屏`() {
        val body = code(screen)
        assertEquals(
            "captureRegion 吃的那一块是起点那一页（批 B②），整屏退场",
            0,
            body.countWith("GalleryFlyIn.captureRegion(host, fullFrameBox)"),
        )
        assertEquals(
            "截之前先问那一页在哪（问不到 ⇒ 整页退，不演飞完了）",
            1,
            body.countWith("GalleryFlyIn.captureRegion(view, start)"),
        )
        // 起点与终点两把键都是 `cardBoundsOf` 那一张表：页登记 `uid:pN`、墙登记 `uid`。
        assertEquals(
            "起点查逐页键（页轴同一颗算式，屏上不自己拼）",
            1,
            body.countWith("GalleryFlyIn.cardBoundsOf(pagePost.viewerKey)"),
        )
        // 页位每槽自报（`onPageIndexChanged` → 宿主 `pageIndex`），不另起一颗 state。
        assertEquals(
            "页位只从那一颗问（每槽自己报上来的那个）",
            1,
            body.countWith("var pageIndex by remember(currentId)"),
        )
    }

    // ── 十三、批 B①④⑤ 活画面飞行体（2026-10-19）─────────────────────────────
    //
    // ① 飞行体里换活画面（与墙卡同 URL 的封面），④⑤ 于是把"淡出补丁"从飞行体上摘掉。
    // 三笔各钉一件事，外加一笔负向扫描：
    // 1. **件在那儿**：`GalleryLiveFlyBody` 这一族进场时被实例化一次；
    // 2. **URL 与比例从哪儿来**：只从 `detail.post` / `works` 那颗 `GalleryPost` 上取
    //    （`previewUrl` + `cardRatio`），屏上不另拼地址、不猜比例；
    // 3. **两颗严格互斥**：活画面接管时截图族那颗不许画（同一 from/to 画两遍 = "同一张画两份"，
    //    而那是本仓反复在治的那一族）；
    // 4. **离场那一半的补丁不许被顺手删**：离场仍是截图族（②③ 的成果：带一页图块缩回小卡），
    //    页块与卡封面不是同一张画 ⇒ `exitBodyAlpha` 那一笔必须还在。删了它，落地那一帧
    //    就是"一张小号页面压在封面之上"（用户读到"贴错了再撕下来"）。

    @Test
    fun `进场活画面与截图族二选一`() {
        val body = code(screen)
        assertEquals(
            "活画面那一族进场时被实例化一次（批 B①）",
            1,
            body.countWith("GalleryLiveFlyBody("),
        )
        // 两颗互斥靠同一颗布尔：截图族那颗带 `!liveTakesOver`，活画面那颗带 `liveTakesOver`。
        assertEquals(
            "截图族那颗在活画面接管时不许画（互斥）",
            1,
            body.countWith("entryBitmap != null && entryFrom != null && !liveTakesOver"),
        )
        assertEquals(
            "活画面那颗只在这一族接管时才画",
            1,
            body.countWith("entryFrom != null && liveTakesOver"),
        )
        // ⚠️ 2026-10-20 真机那一格泛白的病根：接管判据**必须**是"位图到手"，不许只是"URL 非空"。
        // 按 URL 判会让截图族提前让开（`!liveTakesOver`），而活画面那边图还没到手也什么都不画
        // ⇒ 两条路都不画、屏上只剩半透明的页面压在墙上（录屏 `g007` 逐帧证）。
        assertEquals(
            "接管判据必须是位图到手（不是 URL 非空）",
            1,
            body.countWith("val liveTakesOver = liveBitmap != null && liveRatio != null"),
        )
        assertEquals(
            "位图在组合期取一次（判据与绘制吃同一份）",
            1,
            body.countWith("val liveBitmap = rememberLiveFlyBitmap(liveUrl)"),
        )
        // 负向扫描：不许再有"按 URL 判接管"那种写法（演回来就是同一格泛白）。
        assertEquals(
            "不许按 URL 判接管（那是泛白的病根）",
            0,
            body.countWith("liveUrl.isNotBlank() && liveRatio != null"),
        )
        // URL 与比例的唯一出处：`detail.post`（详情腿）或 `works` 里同一号码（列表腿 / 种子那一格）。
        assertEquals(
            "封面地址只从 GalleryPost 上取（屏上不另拼）",
            1,
            body.countWith("detail?.post ?: works.firstOrNull { it.id == currentId }"),
        )
        assertEquals(
            "比例只取 cardRatio 且 > 0（0 = 站方没给，不拿 1:1 顶一个）",
            1,
            body.countWith("livePost?.cardRatio?.takeIf { it > 0f }"),
        )
        // 负向扫描：活画面接管时**不许**再喂淡出补丁（没有"两份"就不需要淡）。
        // 截图族那颗仍喂（深链那一族靠它），所以全文件里 `handoffBodyAlpha(` 只该出现一次。
        assertEquals(
            "淡出补丁只服务截图族那一颗（批 B⑤ 进场那一半）",
            1,
            body.countWith("alpha = { handoffBodyAlpha(entryHandoff.value) }"),
        )
        // 离场那一半**必须留着**：离场还是截图族，页块 ≠ 卡封面（②③ 那一族）。
        assertEquals(
            "离场末段淡出不许被顺手删（离场仍是截图族，见②③）",
            1,
            body.countWith("alpha = { exitBodyAlpha(exitFly.value) }"),
        )
    }

    // ── 十六、玻璃底跟着飞淡掉（2026-10-20，主人「返回手势的模糊动画有点怪」）────
    //
    // 从前是**一帧跳清**：手指刚动，背后那面墙先"啪"地变清，**然后**图才缩 ——
    // 两件事分开读，就是那句"怪"。现在半径跟着 `exitFly` 收到 0，
    // 于是"图缩回去"与"背后变清"是同一件事的两面。

    @Test
    fun `模糊跟着离场进度淡掉不许一帧跳清`() {
        val raw = src(screen).replace("\r\n", "\n")
        // 一帧跳清那种写法必须退场：`clearChromeForExit` 里不许再出现 `clearFlags(`
        // （那是 10-20 之前的形状：flag 与半径同一帧归零）。
        val from = raw.indexOf("fun clearChromeForExit() {")
        val to = raw.indexOf("LaunchedEffect(blurFading, postId)")
        assertTrue("找不到那一颗，量具失效", from > 0 && to > from)
        val branch = raw.substring(from, to)
        assertEquals(
            "淡掉那一颗不许再一帧摘 flag（那是一帧跳清）",
            0,
            branch.lines().count { it.contains("clearFlags(") && !it.trimStart().startsWith("//") },
        )
        val body = code(screen)
        // 半径跟着进度线性收到 0，起点取窗口当前那一位（不另写常量）。
        assertTrue(
            "半径要按离场进度收到 0",
            body.any { it.contains("blurFromPx * (1f - progress.coerceIn(0f, 1f))") },
        )
        assertTrue("起点半径取窗口当前那一位", body.any { it.contains("val from = win.attributes.blurBehindRadius") })
        assertTrue("淡到 0 那一刻才摘 flag", body.any { it.contains("模糊已收到 0 ⇒ 摘 flag") })
        // 两位都要写（只改一处就是"探针说 0、屏上还糊"那一族）。
        assertTrue("半径要写回 attributes", body.any { it.contains("params.blurBehindRadius = px") })
        // 取消那一笔要**还原**（淡到一半被取消时不能停在中间值）。
        assertTrue("取消要还原半径", body.any { it.contains("模糊还原到") })
    }

    // ── 十五、批 C1 跟手返回（2026-10-20）──────────────────────────────────
    //
    // 从 `BackHandler`（按一下才飞）换成 `PredictiveBackHandler`（手指拖着图缩回卡）。
    // 决策层与算式**全是现成的**（`GalleryExitGesture` 四道门闩 + `GalleryExitExpansion`），
    // 这一刀只做接线。四笔各钉一件事：
    // 1. 手势那条挂上了、而且**不许挂 `leaving`** 当 enabled；
    // 2. 决策层的事件流形状与样板同形（Started/Progress/Cancel/Commit 四笔齐）；
    // 3. 跟手用**高刚度弹簧**追赶，不许 `snapTo`（稀疏进度点会整页跳位）；
    // 4. 取消那一笔必须**先归位再清帧**（反了就是画面空一帧），且三样都要清干净
    //    （渲染条件不查 `leaving`，漏一样飞行体就永远挂在屏上）。

    @Test
    fun `跟手返回接到现值判据层上`() {
        val body = code(screen)
        // 手势那条挂上了；旧那颗裸 BackHandler 退场（同一屏挂两个会抢同一次返回手势）。
        assertEquals("系统返回走 PredictiveBackHandler（批 C1）", 1, body.countWith("PredictiveBackHandler {"))
        assertEquals("裸 BackHandler 退场（两颗会抢同一次手势）", 0, body.countWith("BackHandler(enabled = !leaving)"))
        // ⚠️ enabled 不许挂 `leaving`：`Prepare` 那一笔就把它翻真，挂上去等于手势一开始就关掉自己。
        assertEquals("enabled 不许挂 leaving（会把自己这条流关掉）", 0, body.countWith("PredictiveBackHandler(enabled = !leaving)"))
        // 事件流四笔齐（与 `GalleryPostScreen` 同形，少一笔就是某种结局没人接）。
        assertEquals("起手交 Started", 1, body.countWith("if (first) GalleryExitGestureEvent.Started(event.progress)"))
        assertEquals("拖动交 Progress", 1, body.countWith("else GalleryExitGestureEvent.Progress(event.progress)"))
        assertEquals("取消交 Cancel（仅在真动过画面时）", 1, body.countWith("if (receivedProgress) onExitGestureEvent(GalleryExitGestureEvent.Cancel)"))
        assertEquals("松手交 Commit", 1, body.countWith("else GalleryExitGestureEvent.BackWithoutProgress"))
        // 跟手用高刚度弹簧，不用 snapTo（稀疏进度点 snap 会整页跳位）。
        assertEquals("跟手用高刚度弹簧追赶", 1, body.countWith("exitFly.animateTo(command.target, exitFollowSpec)"))
        assertEquals("跟手不许 snapTo 到目标", 0, body.countWith("exitFly.snapTo(command.target)"))
        // 决策层与算式都吃现成那颗，屏上不另写判断。
        assertEquals("决策层吃现成那颗（屏上不另判）", 1, body.countWith("nextExitGesture(exitPhase, exitLastProgress, event)"))
        assertEquals("四条指令都要接（Prepare/Restore/FlySelfDriven/Finish）",
            4, listOf("GalleryExitGestureCommand.Prepare", "GalleryExitGestureCommand.Restore",
                "GalleryExitGestureCommand.FlySelfDriven", "GalleryExitGestureCommand.Finish").count { body.countWith(it) == 1 })
    }

    @Test
    fun `取消那一笔要归位要清帧要挂回玻璃底`() {
        // 取两支的原文（Restore 到 FlySelfDriven 之间）。
        val body = src(screen).replace("\r\n", "\n")
        val from = body.indexOf("GalleryExitGestureCommand.Restore ->")
        val to = body.indexOf("GalleryExitGestureCommand.FlySelfDriven ->")
        assertTrue("找不到 Restore 那一支，量具失效", from > 0 && to > from)
        val branch = body.substring(from, to)
        // ① "先归位"由**决策层**保证：`Cancel` 那一档交回的是 `SetVisualTarget(0f)` 再 `Restore`
        //    （指令顺序执行，见 `GalleryExitGesture.kt` 的 Cancel 支）—— 所以 UI 侧不再自己 snapTo，
        //    两处都做就是"归位两次"，而且 snap 会把这一帧钉死、看不见回弹。
        val gesture = src("com/venera/compose/gallery/domain/GalleryExitGesture.kt").replace("\r\n", "\n")
        assertTrue(
            "决策层的 Cancel 必须先归位再 Restore",
            gesture.contains("listOf(GalleryExitGestureCommand.SetVisualTarget(0f), GalleryExitGestureCommand.Restore)"),
        )
        assertTrue("UI 侧不许再自己 snapTo 归位（归位只住决策层）", !branch.contains("snapTo(0f)"))
        // ② 三样都要清：渲染条件不查 `leaving`（②③那轮删掉的），漏一样飞行体就永远挂在屏上。
        assertTrue("要清像素", branch.contains("exitFrame = null"))
        assertTrue("要清起点", branch.contains("exitFrom = null"))
        assertTrue("要清落点", branch.contains("exitTo = null"))
        assertTrue("要撤 leaving（页面要重新显示）", branch.contains("leaving = false"))
        // ③ 玻璃底**必须挂回去**：不挂就是"拖到一半松手回弹"之后这一屏的模糊底永久没了
        //    （样板 `restoreChromeAfterCancel` 那句注释记着同一个坑）。
        assertTrue("要挂回 FLAG_BLUR_BEHIND", branch.contains("addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)"))
        assertTrue(
            "半径要还原到离场前记下的起点（淡到一半取消时不能停在中间值）",
            branch.contains("setBackgroundBlurRadius(blurFromPx.toInt())"),
        )
    }

    @Test
    fun `两条路共用同一颗备料与算式`() {
        val body = code(screen)
        // 备料只住一颗：跟手那条（Prepare 指令里）与三键那条（flySelfDrivenExit 里）都调它。
        // 两处各写一遍就是两处真相（"点顶栏与按手势落点不一样"，只有真机能看见）。
        assertEquals("备料只住一颗函数", 1, body.countWith("fun prepareExit("))
        assertEquals("两条路都调它（跟手 + 三键）", 2, body.countWith("prepareExit {"))
        // 自驱那条也只住一颗：三键 / 顶栏 / 决策层 `FlySelfDriven` 三处都走它。
        assertEquals("自驱飞只住一颗函数", 1, body.countWith("fun flySelfDrivenExit()"))
        // 摘模糊抽成具名函数（两条路共用），且窗口那两件都做齐。
        assertEquals("摘模糊只住一颗函数", 1, body.countWith("fun clearChromeForExit()"))
        assertTrue("要摘 flag", body.any { it.contains("FLAG_BLUR_BEHIND") })
        assertTrue(
            "半径要跟离场进度收到 0（只摘 flag 在某些 ROM 上还留着）",
            body.any { it.contains("win.setBackgroundBlurRadius(px)") } &&
                body.any { it.contains("blurFromPx * (1f - progress.coerceIn(0f, 1f))") },
        )
        // 顶栏那颗 / 三键那条仍走自驱（没有手指可跟），不许被手势那条吞掉。
        assertEquals("back 仍走自驱那条", 1, body.countWith("val back: () -> Unit = { flySelfDrivenExit() }"))
    }

    // ── 十四、批 B⑦ 无锚退路换侧滑（2026-10-19）─────────────────────────────
    //
    // ⑦ 的原话：「可见比不足 25% ⇒ 当场判无锚、走全宽侧滑（顶层平移、底层 -25% 视差 +
    // alpha 0.9），**不再**走"整页从下沿抬上来"」。判式那一颗（`hasAnchor`）与投影
    // （`sideSlideTopOffset` / `sideSlideBackOffset`）在 domain 层，由
    // `GalleryAnchorGateTest` 钉；这里只钉**接线**：三条"不飞了"的路径是不是都改成侧滑了。

    @Test
    fun `无锚三条退路都改成侧滑`() {
        val body = code(screen)
        // 三条路径各调一次那颗辅助函数（没有逐页键 / 量不到落点 / 落点边缘回来而超时已放弃）。
        // 定义句是 `fun startSideSlide() {`（以 `fun ` 开头），调用句是光秃秃的 `startSideSlide()`
        // —— 两者都要数，且必须是"1 处定义 + 3 处调用"这一个组合。
        // 少一处调用 = 有一条不飞了的路径还在"直接显示"；多一处 = 又有人就地起了一趟侧滑。
        assertEquals(
            "三条不飞了的路径都起动侧滑（批 B⑦）",
            4,
            body.countWith("startSideSlide()"),
        )
        assertEquals(
            "其中只有一处是定义",
            1,
            body.countWith("fun startSideSlide()"),
        )
        // 口径只此一份：三处各写一遍动画就是三处真相（改弹簧档要改三个地方）。
        // 定义那一笔与上面那笔合起来看：1 处定义 + 3 处调用 = 口径只有一份、三条路径都接上。
        assertEquals(
            "侧滑起动只住在一颗函数里（口径不重复）",
            1,
            body.countWith("sideSlide.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))"),
        )
        // 侧滑与 hero 互斥：起动那一颗带闸门，UI 层的平移也带闸门。
        assertEquals(
            "起动带 hero 闸门（正在飞就不侧滑）",
            1,
            body.countWith("if (entryFlying) return"),
        )
        assertEquals(
            "顶层平移只在这一趟走（走 hero 时不许动）",
            1,
            body.countWith("if (sideSliding) {"),
        )
        // 平移必须来自判据层那一颗，不许在 UI 层就地写 `1f - progress`。
        assertEquals(
            "顶层平移吃判据层 sideSlideTopOffset",
            1,
            body.countWith("translationX = size.width * sideSlideTopOffset(sideSlide.value)"),
        )
        assertEquals(
            "屏上不许就地写侧滑投影（判据只住一份）",
            0,
            body.countWith("translationX = size.width * (1f - "),
        )
        // 退路不许再演成"飞完了"：三条路径的日志都要念得出自己走了侧滑。
        assertTrue(
            "日志要念得出走侧滑（不许静默交回）",
            body.any { it.contains("⇒ 走侧滑退路") },
        )
    }

    @Test
    fun `活画面件自带边界清单`() {
        // 尺子只钉"界写在文件里"：下次谁再在 drawWithCache 里找 density，能看见这条。
        val live = src("com/venera/compose/gallery/ui/GalleryLiveFlyBody.kt")
        assertTrue(
            "活画面件要写明组合期 / 绘制期那条界",
            live.contains("组合期取位图，绘制期只做几何"),
        )
        assertTrue(
            "活画面件要写明不重组那一档（drawWithCache 按位图做键）",
            live.contains("不重组"),
        )
        // 几何只住一份：活画面件自己也走判据层那一颗，不另写一遍插值。
        assertEquals(
            "几何走判据层 flightFrame（不另写一遍插值）",
            1,
            live.split("flightFrame(").size - 1,
        )
    }

    // ── 十二、2026-10-19 那一轮"效果有点差"的四件 ─────────────────────────────
    //
    // 读数出处是用户那三张真机截图：进图要往下拉才正常大小（A）、按钮里的数字换行裁切（B）、
    // 评论栏念着一行 404（C）、hero 转场压根没接上（D）。每件各一把尺，尺子只钉"下次再坏能不能看见"。

    /**
     * 从「sig」那一行切到它自己的收尾。
     *
     * ⚠️ 收尾只认**顶格**那一枚 `}`（原始行、不 trim）：一颗函数内部到处都是缩进的闭合括号
     * （`DisposableEffect { … }`、`if (count != null) { … }`），拿 trim 后的 "}" 当终点
     * 会切在半路，判据于是只看得到函数开头几行 —— 那种尺子会**绿着漏掉**整颗件。
     */
    private fun bodyOf(rel: String, sig: String): String {
        val raw = src(rel).lines()
        val from = raw.indexOfFirst { it.trim().contains(sig) }
        assertTrue("找不到分支 $sig（$rel），量具失效", from >= 0)
        val rest = raw.drop(from + 1).indexOfFirst { it == "}" }
        assertTrue("$sig 那一颗没有顶格收尾，量具失效", rest >= 0)
        return raw.subList(from, from + 1 + rest)
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
            .joinToString("\n")
    }

    private fun countIn(body: String, needle: String): Int = body.lines().count { it.contains(needle) }

    private val appClient = "com/venera/compose/gallery/data/PixivAppClient.kt"

    /**
     * A：多页那一作从第 2 页起被压成小方块（用户「进入图片后得要往下拉一点才能显示正常大小」）。
     *
     * 成因是两件事叠在一起：登录腿的逐页对象没有宽高（那是匿名腿 /pages 才给的），
     * 而缺比例时 AsyncImage 仍挂 fillMaxSize() —— 无界高度里 Compose 退回位图像素尺寸。
     * 所以尺子钉两头：缺比例那一支必须让高度随内容走，且比例必须由图自己报回来补上。
     */
    @Test
    fun `缺比例的那一页不在无界高度里挂 fillMaxSize`() {
        val body = bodyOf(screen, "private fun PixivPageImage(")
        assertTrue("缺比例那一支必须 wrapContentHeight（让高度随内容走）", body.contains("Modifier.wrapContentHeight()"))
        assertTrue("补的那一档比例只能来自图自己报的 intrinsicSize", body.contains("state.painter.intrinsicSize"))
        assertTrue("比例 = 宽 / 高，不拿 1:1 顶", body.contains("loadedAspect = size.width / size.height"))
        assertEquals(
            "高度口径只许问一把尺（boxAspect = 站方报的 ?: 图报的）",
            1,
            countIn(body, "if (boxAspect != null) Modifier.fillMaxSize() else Modifier.fillMaxWidth()"),
        )
        assertEquals("AsyncImage 不许无条件铺满（那就是那一格小方块）", 0, countIn(body, "modifier = Modifier.fillMaxSize(),"))
    }

    /**
     * B：动作行那枚带计数的按钮把 41774 换行成两截再裁掉（用户「按钮还是没改反而 ui 有问题」）。
     *
     * 从前每一枚套在 VeneraIconButton 里，content 被钉在一整格固定方框内；规格照要的是
     * "图标 + 数"平摊在一整条外壳里。所以尺子钉：外壳是一整条实色圆角，而每一枚自己按内容取宽、
     * 但仍要吃全站同一档触达高度（宽度随内容 ≠ 可以缩成一枚点不着的小把手）。
     */
    @Test
    fun `动作行是一整条外壳而每一枚自己按内容取宽`() {
        val row = bodyOf(infoPanel, "internal fun PixivWorkActionRow(")
        assertEquals("外壳只有一条实色圆角条", 1, countIn(row, ".background(tokens.color.surfaceContainerHigh)"))
        assertEquals("外壳圆角走卡片那一档", 1, countIn(row, ".clip(RoundedCornerShape(tokens.shape.card))"))
        val pill = bodyOf(infoPanel, "private fun WorkAction(")
        // needle 带括号：裸名会咬到下面那行 `VeneraIconButtonTouchSize`（常量名把这颗件名包在里面），
        // 而这一条钉的是"不再**调用**那颗固定方格件"。
        assertEquals("那一枚不再借固定方格", 0, countIn(pill, "VeneraIconButton("))
        assertEquals("触达高度仍吃全站那一档", 1, countIn(pill, ".heightIn(min = VeneraIconButtonTouchSize)"))
        assertEquals("计数只许一行（换行就是被裁的那一格）", 1, countIn(pill, "maxLines = 1"))
        assertEquals("每一枚自己挂点击", 1, countIn(pill, ".clickable(onClick = onClick)"))
    }

    /**
     * C：评论那一腿从前打的是 /v1/illust/comments —— 站方早挪到 v3（2026-10-19 现量：
     * v1 回 404 路由不存在，v3 回 400 缺凭据，同一颗 client 无票据对照）。
     *
     * ⚠️ 这条尺只扫代码行：KDoc 里那句"从前写的是 v1"是改判记录，必须留着
     * （本仓的规矩是撤一条负结论时把漂进注释的副本点名，而不是把注释一起洗掉）。
     */
    @Test
    fun `评论腿只认量过的 v3 且代码里只有一处评论端点`() {
        val lines = code(appClient)
        assertEquals(
            "端点常量必须是 v3/illust/comments",
            1,
            lines.countWith("ILLUST_COMMENTS = \"https://app-api.pixiv.net/v3/illust/comments\""),
        )
        assertEquals("代码里只许出现一处评论路径（第二处就是第二把尺）", 1, lines.countWith("illust/comments"))
        assertEquals("v1 那一档只许活在注释里", 0, lines.countWith("/v1/illust/comments"))
        assertEquals("没在 v3 上量过的参数不发", 0, lines.countWith("include_total_comments"))
    }

    /**
     * D：进场飞行体（墙那一格 → 这一屏第 0 页那一格）。用户读数「hero 动画也没接上」——
     * 离场那半上一批已接，进场这半压根没读交接件。
     *
     * 接线类判据钉顺序不钉存在（"排完立刻 dispose"那种假动作，存在性判式全绿）：
     * 读 payload/origin → 量到落点 → 起飞 → 落点那一格真画出像素 → 才清交接件。
     */
    @Test
    fun `进场飞行体按等落点、起飞、等像素、清交接件这个顺序走`() {
        val body = code(screen).joinToString("\n")
        val iPayload = body.indexOf("val cardFrame = GalleryFlyIn.payload")
        val iOrigin = body.indexOf("val flyOrigin = GalleryFlyIn.origin")
        val iWait = body.indexOf("snapshotFlow { GalleryFlyIn.cardBoundsOf(entryKey) }")
        val iAnim = body.indexOf("entryFly.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))")
        val iDrawn = body.indexOf("snapshotFlow { pageZeroDrawn }")
        val iConsume = body.indexOf("GalleryFlyIn.consume()", iDrawn)
        assertTrue("起点那一帧要从交接件读", iPayload in 0 until iWait)
        assertTrue("起点矩形要从交接件读", iOrigin in 0 until iWait)
        assertTrue("必须先量到落点再起飞（量不到就老实不飞）", iWait in 0 until iAnim)
        assertTrue("飞完还要等落点那一格真画出像素，撤早了就是闪", iAnim in 0 until iDrawn)
        assertTrue("清交接件排在最后", iDrawn in 0 until iConsume)
        // 落点键必须由 domain 那一颗长：屏上不自己拼 ":p0"（那是第二处真相）。
        // 批 B⑥（不等落点）之后来源换了"详情或种子"（种子与真第 0 页的键逐字相等，见
        // `PixivWorkPage.seedDetailOf`），但算式仍只有那一颗 —— 改的是喂给它的料。
        assertEquals(
            "落点键只从 domain 那一颗长（详情或种子二选一，屏上不另拼 \":p0\"）",
            1,
            code(screen).countWith("val source = detail ?: works.firstOrNull { it.id == currentId }?.let { PixivWorkPage.seedDetailOf(it) }"),
        )
        // 圆角那一档是 @Composable getter：**绘制期**直接读它 = 读 compositionLocal，拿不到主题值
        // （本仓为这一格栽过，见 `GalleryFlyIn` 头注）。批 B① 的活画面件参数收的是 Float
        // （`drawWithCache` 里换算不了 Dp），所以那一颗的圆角在**组合期**就 `.toPx()` 完递进去 ——
        // 这正是允许的那一种。尺子于是钉"只准那一处、且必须是递给活画面件"：
        // 多一处就是又有人在绘制期读主题，少一处就是活画面件在拿 0dp 飞。
        assertEquals(
            "组合期换算圆角只准那一处（递给活画面件，批 B①）",
            1,
            code(screen).countWith("cardCornerRadiusPx = with(density) { veneraCoverCornerRadius().toPx() }"),
        )
        // **只清自己那一笔**：等落点那几百毫秒里用户可能已经点进某一页，那一跳会重新 capture 一份起点
        // （见 openPages 那头）。那时这一屏若无条件 consume，就是把播放器的起点清走 —— 表现为"进大图不飞"。
        // 2026-10-19 多出一处：落点在预算边缘回来、而超时那一条已经放弃时，这一趟不起飞，
        // 手里那一帧照样要交还（否则这一屏一直攥着位图）。
        assertEquals("这一屏共五处 consume（点图那一处 + 进场那四处）", 5, code(screen).countWith("GalleryFlyIn.consume()"))
        assertEquals(
            "四处全部带身份闸门（手里那帧还是当初那一帧才清）",
            4,
            code(screen).countWith("if (GalleryFlyIn.payload === frame) GalleryFlyIn.consume()"),
        )
    }

    /**
     * E：图与"图以下那一块"是**两条钟**（2026-10-19 用户：「图片飞入完成才突然几帧内加载出
     * 下面的界面不好」）。真机帧证：`u5a/0025` → `u5a/0026` 间隔 20ms，前一帧屏上只有飞行体，
     * 后一帧动作条 / 作者行 / 标签 / 评论整块出现。
     *
     * 根因不是淡得快，是起得晚：内容区原来只有交棒那一条钟，而交棒从**落位**才开始。
     * 所以这里钉的是"两条钟各自挂在各自的那一格上"，合回去这一条就会红。
     */
    @Test
    fun `图与图以下那一块走两条钟，内容区不等飞完`() {
        val body = code(screen).joinToString("\n")
        // ① 两条 alpha 都要**下到项上**：挂在外层容器上就只有一条钟，内容区必然被图卡在后面。
        assertEquals(
            "图那一块读交棒那条钟（pageEntryAlpha）",
            1,
            code(screen).countWith("graphicsLayer { alpha = pageEntryAlpha() }"),
        )
        assertEquals(
            "内容区读它自己那条钟（detailEntryAlpha）：ugoira 提示 / 详情段 / 下一作品 三处",
            3,
            code(screen).countWith("graphicsLayer { alpha = detailEntryAlpha() }"),
        )
        // ② pager 那一层**不许**再挂 alpha —— 挂在上面就是一条钟，内容区必然被图卡在后面。
        //    （顶栏那两层照旧挂 pageAlpha，它们不是图也不是内容区，跟着 chrome 那条走。）
        val lines = code(screen)
        val iBlur = lines.indexOfFirst { it == ".blurBackdropSource(topBarBackdrop)," }
        assertTrue("pager 那一层的取样源要挂在内容层上", iBlur > 0)
        assertEquals(
            "pager 上一层必须是 fillMaxSize，中间不许再插 alpha 层（插上去就只剩一条钟）",
            ".fillMaxSize()",
            lines[iBlur - 1],
        )
        // ③ 内容区那条钟在**飞行进行中**起手：挂在 entryFly 上，排在 entryFly.animateTo 之前。
        val iLead = body.indexOf("snapshotFlow { entryFly.value }.first { it >= HANDOFF_DETAIL_LEAD_FLY }")
        val iFly = body.indexOf("entryFly.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow))")
        val iDrawn = body.indexOf("snapshotFlow { pageZeroDrawn }")
        assertTrue("内容区那条钟由飞行进度触发（不是写死的毫秒）", iLead >= 0)
        assertTrue("内容区要在飞行**进行中**就起手，排在 entryFly 之前", iLead in 0 until iFly)
        // ④ 内容区那条钟**不**排在等像素之后 —— 排后面就是串行，"图先落地、内容后长"。
        assertTrue("内容区不许被等落点像素那一关拦住（拦住就是又变回串行）", iLead in 0 until iDrawn)
        // ⑤ 内容区那条钟必须有自己的 Animeatable，且默认值取 1（不飞的那些退路天然是对的）。
        assertEquals("内容区那条钟初值取 1 ⇒ 五条「不飞了」的退路不用挨个抬回来", 1, code(screen).countWith("val entryDetail = remember(postId) { Animatable(1f) }"))
        // ⑥ 两条算式各自来自 domain 一颗，不许在屏上二次实现。
        assertEquals(1, code(screen).countWith("import com.venera.compose.gallery.domain.handoffPageAlpha"))
        assertEquals(1, code(screen).countWith("import com.venera.compose.gallery.domain.handoffDetailAlpha"))
    }

    /** 2026-10-19 那张规格照：两节节头各一枚箭头 + 关注那一枚是实心主色药丸。 */
    @Test
    fun `节头把手与关注药丸按 2026-10-19 那张规格照`() {
        val lines = code(screen)
        assertEquals("标签节头念「作品标签」", 1, lines.countWith("\"作品标签\""))
        assertEquals("两节节头各一枚箭头（点得动的才画箭头）", 2, lines.countWith("Icons.Outlined.KeyboardArrowRight"))
        assertEquals("评论节头念「更多评论」", 1, lines.countWith("\"更多评论\""))
        assertEquals(
            "动作行那枚评论入口只在站方真给评论时才摆（作者关了评论 = 点了没反应那枚假把手）",
            1,
            code(infoPanel).countWith("if (actions.showComments) {"),
        )
        // 判据的**出处**也钉一下：这一枚吃的是站方那两个硬字段（评论开关 + 评论数），不是本地猜的。
        assertEquals(
            "入口的判据只从站方字段长",
            1,
            code(infoPanel).countWith("showComments = detail != null && !detail.commentsOff && detail.commentCount != null"),
        )
        assertEquals(
            "关注那一枚：没关注是实心主色",
            1,
            lines.countWith("if (artist.isFollowed) tokens.color.surfaceContainerHigh else tokens.color.primary"),
        )
        assertEquals("没关注时带一枚「+」", 1, lines.countWith("Icons.Outlined.Add"))
    }

    // ── 批 233-2：横向档那一槽的滚动状态由槽自己持有 ─────────────────────

    /**
     * 2026-10-19 批 233-2（用户「图二作品点进去后会被顶的很上，图三才是正常大小」）。
     *
     * 立案：那一颗 `LazyListState` 从前由**宿主**建一次、发进 `HorizontalPager` 的**每一个槽**。
     * 两个宿主同时组合时（当前页 + 邻页预组合），后组合那一槽的滚动会把前一颗的锚改掉 ——
     * 表现正是"这一条被顶到很上面"。修好的形状是：槽自持一颗，页位**报上来**给宿主。
     *
     * 四把尺各钉一件事：自持一颗 / 旧的共享那颗没留下 / 报上来的那一句只认自己那一槽 /
     * "夹到最后一页"那一步还活着（在别的用例里，这里钉**顺序**：槽内报 → 宿主过滤位号 → 宿主写页码）。
     */
    @Test
    fun `每一槽自持一颗滚动状态，页码按位号过滤后才写`() {
        val body = code(screen)
        assertEquals("自持那一颗只在槽里建", 1, body.countWith("val listState = rememberLazyListState()"))
        // 旧那颗共享的（宿主建的 `flowState`）连**注释里**都不许留：留着的就是下一次搬家的手抄本。
        // 计数走 `countIn`（吃原文、不过滤注释行）—— 删除类的尺子按本仓口径扫全文含 KDoc。
        assertEquals("旧的共享那颗没留下（全文含注释）", 0, countIn(src(screen), "flowState"))
        assertEquals("槽把页位报上来，签名只有一处", 1, body.countWith("onPageIndexChanged: (Int) -> Unit = {}"))
        assertEquals("报上来那一处只有一处调用", 1, body.countWith("onPageIndexChanged = { index ->"))
        assertEquals(
            "宿主按位号过滤（隔壁槽的滚动不许写这一屏的读数）",
            1,
            body.countWith("if (slotId == currentId) pageIndex = index"),
        )
        // 顺序判据分两段，各钉各的坐标：
        // ① 槽内**先量后报**（同一颗文件里的先后，量在报之前）；
        // ② 宿主那一处**把写包在回报那一句里面**（下一行就是写）—— 不钉这个就会长成"宿主自己读槽的 state"，
        //    那正是这一批要撤掉的形状。两段的 index 都取自同一份过滤后的列表，不混文件行号。
        val measure = body.indexOfFirst { it.contains("snapshotFlow { listState.firstVisibleItemIndex }") }
        val report = body.indexOfFirst { it.contains("onPageIndexChanged(if (pages.isEmpty())") }
        assertTrue("槽内必须先量再报", measure in 0 until report)
        val callSite = body.indexOfFirst { it.contains("onPageIndexChanged = { index ->") }
        assertTrue("宿主写页码必须包在回报那一句里面（下一行）", body[callSite + 1].contains("if (slotId == currentId) pageIndex = index"))
    }

    // ── 批 233-3：评论只摆最新一条，其余就地展开；表情与贴纸照画 ──────────

    /**
     * 2026-10-19 批 233-3（用户「评论太多了，应该只显示一条最新评论，其他评论放到查看更多评论」）。
     *
     * 钉的是**封顶那一枚的出处**与**把手只在真有余条时才摆**：
     * 上限写死在 `COMMENTS_SHOWN` 一处（屏上不自己数第二遍），展开态由宿主持有、两个宿主读同一份
     * —— 滚动流里点了「更多评论」而抽屉那一版还收着，就是同一件事两把尺。
     */
    @Test
    fun `评论默认只摆最新一条，其余就地展开而两个宿主读同一份`() {
        val body = code(screen)
        assertEquals("封顶那一枚只有一处声明", 1, body.countWith("private const val COMMENTS_SHOWN = 1"))
        assertEquals("未展开只摆封顶那几条", 1, body.countWith("state.comments.take(COMMENTS_SHOWN)"))
        assertEquals(
            "展开与收起只换摆几条，不另要一页",
            1,
            body.countWith("if (expanded) state.comments else state.comments.take(COMMENTS_SHOWN)"),
        )
        assertEquals(
            "余条数只从已取到的那一页减封顶",
            1,
            body.countWith("val moreCount = (loaded?.comments?.size ?: 0) - COMMENTS_SHOWN"),
        )
        assertEquals("把手只在真有余条时摆（0 就是点了没反应）", 1, body.countWith("if (moreCount > 0) {"))
        assertEquals("那句措辞只有一处", 1, body.countWith("if (expanded) \"收起\" else \"更多评论\""))
        // 两个宿主读同一份：展开态由宿主持有，往下传两处（流那一版 + 抽屉那一版），把手两处各一枚。
        assertEquals("宿主那份展开态传给两个宿主", 3, body.countWith("commentsExpanded = commentsExpanded"))
        assertEquals(
            "切换都写回同一份（两枚把手不是两把开关）",
            2,
            body.countWith("onToggleCommentsExpanded = { commentsExpanded = !commentsExpanded }"),
        )
    }

    /**
     * 评论正文里的**小表情**与**贴纸**都画得出来（用户「也要正常显示评论图和表情」）。
     *
     * 三条各钉一枚：
     * 1. 切分只有一处出处（domain 那颗函数），屏上不自己拼正则；
     * 2. **表里没有的那一串原样念**（`(${'$'}segment.name)`），不摆破图 —— 那是站方哪天新增一枚的唯一读法；
     * 3. 贴纸只吃站方那一格 `stampUrl`（评论不能附图，屏上"评论里的图"就是这一格，别拿别人的脸或赞数凑）。
     */
    @Test
    fun `评论正文的表情与贴纸各只有一处出处`() {
        val body = code(screen)
        assertEquals("切分只调 domain 那一颗", 1, body.countWith("splitPixivComment(comment.text)"))
        assertEquals("号只从那张表问", 1, body.countWith("pixivEmojiId(segment.name)"))
        assertEquals("未命中回字面短码", 1, body.countWith("\"(${'$'}{segment.name})\""))
        assertEquals("画成图的那枚表情把短码报给读屏", 1, body.countWith("contentDescription = \"表情 ${'$'}{segment.name}\""))
        assertEquals("贴纸只吃站方那一格", 1, body.countWith("if (comment.stampUrl.isNotBlank())"))
        assertEquals("贴纸那句读数只念一遍", 1, body.countWith("contentDescription = \"贴纸\""))
        assertEquals("正文那一颗：定义 + 一处调用", 2, body.countWith("PixivCommentBody("))
    }

    /**
     * 2026-10-19 批 233-2 的**另一半**（用户：「图还是会被顶上去……往下拉一点就正常，从画廊进来就不正常」）。
     *
     * 真机读数（这一屏现场取的，不是推断）：
     * - 站方详情腿回的是 `"width":2400,"height":4000` 且 `page_count:1` ⇒ 1080 宽下这一格该有 **1800** 高；
     * - 设备缓存里真正被画的那张 medium 是 **324x540**（比例 0.600，与站方逐页宽高**同档**，没裁）；
     * - `uiautomator` 量到那一格只有 **[0,0][1080,979]**，像素层复量左右**没有黑边**（Fit 满宽）
     *   ⇒ 不是比例算错，是**上面 821px 在屏外**：这一格被滚走了。
     *
     * 成因是本仓量过那一族：**懒列表"第 0 项之前/之后插入"的锚定漂移**
     * （首页顶栏重叠那次同一根因，记忆「首页顶栏重叠的真根因：锚定漂移」）。
     * 进场那一刻 `detail` 还没到 ⇒ 页那一摞压根不存在，锚停在详情那几格上；页长出来那一刻整摞
     * 插在锚**之上**，锚不动 ⇒ 图被顶出屏外。用户"往下拉一点"就是手动把锚拽回第 0 项。
     *
     * 三把尺各钉一件事：归零**只有一处**、它排在自持那颗 state **之后**、
     * 且**不许**挂在 `LaunchedEffect(Unit)` 上（每次组合都归零会把"横翻回来还在原来那一页"那条既有裁决打掉）。
     *
     * 批 B⑥（不等落点）之后名单换了，尺子跟着换：首帧那一格可能是**种子**（列表腿那条长出来的
     * 糊图，只有一枚），归零只认**真页**。否则详情到手那一刻 `pagesAppeared` 已翻真、锚不再归零，
     * 而那一刻醒着的是整摞真页（多 P 作一次多出好几摞）⇒ 正是这一条要防的那一跳。
     */
    @Test
    fun `页第一次长出来时这一槽把锚拽回顶部`() {
        val body = code(screen)
        assertEquals("归零只有一处", 1, body.countWith("listState.scrollToItem(0)"))
        assertEquals(
            "只在「这一作**真**页第一次铺出来」那一刻归零（种子那一格不算）",
            1,
            body.countWith("if (pages.isNotEmpty() && !pagesAppeared && detail != null) {"),
        )
        assertEquals(
            "键是作品号与页数（不是 Unit ⇒ 不会每次组合都归零）",
            1,
            body.countWith("LaunchedEffect(workId, pages.size) {"),
        )
        val declared = body.indexOfFirst { it.contains("val listState = rememberLazyListState()") }
        val reset = body.indexOfFirst { it.contains("listState.scrollToItem(0)") }
        assertTrue("量到 state 之后才归零（顺序反了就是打在不存在的那颗上）", declared in 0 until reset)
        assertEquals(
            "作品号从详情或种子那一颗问（批 B⑥：种子首帧就有，屏上不另拼一份身份）",
            1,
            body.countWith("val workId = (detail ?: seedDetail)?.post?.id ?: 0L"),
        )
    }

    /**
     * 落点那张表必须是**快照状态**（2026-10-19 · 批 233-5）。
     *
     * 立案与现量都在 `desktop/src/test/kotlin/com/venera/desktop/gallery/ui/DesktopSnapshotFlowObservationTest.kt`：
     * 没有第二帧时，普通 `HashMap` 的写入叫不醒 `snapshotFlow`（实测只报订阅那一刻的 null），
     * 而 `mutableStateMapOf` 的写入当场作数（实测 `[null, 5]`）。
     * 进场那一趟等的正是这一格 —— 押在"别人恰好画了一帧"上，屏上就读成「hero 没接上」。
     *
     * 这一条不钉那句注释、只钉那颗底：注释会被人改措辞，底换回去才是真退化。
     */
    @Test
    fun `落点那张表住进快照状态而不是普通Map`() {
        val fly = code("com/venera/compose/gallery/ui/GalleryFlyIn.kt")
        assertEquals(
            "落点住进 mutableStateMapOf（写入即叫醒等待者，量具在 desktop 那一颗）",
            1,
            fly.countWith("cardBounds = mutableStateMapOf<String, Rect>()"),
        )
        assertEquals("不许退回普通 HashMap（那等于把转场押在别人的帧上）", 0, fly.countWith("cardBounds = HashMap<String, Rect>()"))
        // 读它的那一句仍然是 `first { it != null }`：换了底而等式没接上，等于白改。
        assertEquals("进场那一趟仍按落点等到才起飞", 1, code(screen).countWith("snapshotFlow { GalleryFlyIn.cardBoundsOf(entryKey) }"))
    }
}
