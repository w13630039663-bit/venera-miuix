package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GalleryRankings
import com.venera.compose.gallery.domain.GallerySearchMerge
import com.venera.compose.gallery.domain.GalleryTagFilter
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十九批 批 19 · 画师单人页的尺子。
 *
 * 两条主线：① **口径不许多造一条**（查询串、18 那张预算都得是对岸的）；
 * ② **这一屏是单腿的**，所以任何"三站"式的整块措辞都不许出现在它身上。
 */
class DesktopArtistProfileTest {

    private val uiDir = File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/ui")
    private val today = LocalDate.of(2026, 10, 7)
    private val target = DesktopArtistTarget(GallerySite.YANDERE, "kiki")

    private fun code(name: String): String = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(name))

    /** ① 查询串整颗转交对岸，桌面一手都不写。 */
    @Test
    fun 查询串不许桌面自拼() {
        val policy = code("DesktopArtistProfilePolicy.kt")
        assertTrue("算式没转交对岸那颗：" + policy.contains("GalleryRankings.searchQuery("), policy.contains("GalleryRankings.searchQuery("))
        // 站方后缀是对岸那一处钉着的，桌面这边出现它的字面量就是"手抄了一份"。
        listOf("order:score", "sort:score:desc", "date:").forEach { suffix ->
            assertEquals("桌面这边自己写了 $suffix", 0, policy.split(suffix).size - 1)
        }
    }

    /** ② 真跑：与对岸算式同参调用必须逐字相等，而排序后缀真的带上了。 */
    @Test
    fun 查询串与对岸算式逐字相等() {
        GallerySite.entries.forEach { site ->
            val t = DesktopArtistTarget(site, "kiki")
            val mine = desktopArtistProfileQuery(t, today)
            val theirs = GalleryRankings.searchQuery(
                site = site,
                filters = listOf(GalleryTagFilter("kiki")),
                ranking = GalleryRanking.ALL,
                anchor = null,
                todayUtc = today,
            )
            assertEquals("${site.displayName} 的查询串与对岸算式不一样", theirs, mine)
            assertTrue("查询串里丢了标签名：$mine", mine.contains("kiki"))
        }
        val all = GallerySite.entries.map { desktopArtistProfileQuery(DesktopArtistTarget(it, "kiki"), today) }
        assertTrue("三站里没有一站带 order:score：$all", all.any { it.contains("order:score") })
        assertTrue("三站里没有一站带 sort:score:desc：$all", all.any { it.contains("sort:score:desc") })
    }

    /** ③ 一次取几张不是桌面造的数字。 */
    @Test
    fun 取数预算逐字回核对岸() {
        val screen = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/ui/GalleryArtistProfileScreen.kt"))
        val line = screen.lines().firstOrNull { it.trim().startsWith("private const val SLOT_FETCH_SIZE") }
        assertTrue("对岸那颗文件里读不到 SLOT_FETCH_SIZE 那一行了（值若改了要跟着改账）", line != null)
        val theirs = requireNotNull(line?.substringAfter('=')?.trim()?.toIntOrNull()) { "那一行读不出数字：$line" }
        assertEquals("桌面的预算与对岸不等", theirs, DESKTOP_ARTIST_SLOT_SIZE)
    }

    /** ④ 单腿形状：这一屏不许念"三站"，也不许多收一枚站点。 */
    @Test
    fun 单人页是单腿的所以不许念三站() {
        // ⚠️ 扫**代码行**：类注释里"这一屏不念三站那套词"这句话本身就该出现（它是口径），
        // 而违例的形状是**代码或屏上文案**里真把一站说成三站。
        listOf("DesktopArtistProfilePolicy.kt", "DesktopArtistProfilePane.kt").forEach { name ->
            val text = code(name)
            assertEquals("$name 的代码或屏上文案里出现了「三站」", 0, text.split("三站").size - 1)
        }
        assertEquals(
            "目的地多带了别的东西（它只能是一站 + 一个名字）",
            2,
            // 钉**构造器参数数**而不是字段数：`::class.members` 要 kotlin-reflect（桌面测试的 classpath 上没有，
            // 第一版在那里直接抛 KotlinReflectionNotSupportedError —— 判据自己炸掉不是判据红），
            // 而 `declaredFields` 会连编译器自己生成的那枚一起数进来（实测数出 3）。
            DesktopArtistTarget::class.java.constructors.single().parameterCount,
        )
    }

    /** ⑤ 三句话各一句：发了没答上 / 答上了零张 / 整条链出事，不许塌成一句沉默。 */
    @Test
    fun 三句话分开念() {
        val failure = desktopArtistFailureReading(target, "timeout")
        val empty = desktopArtistEmptyReading(target)
        assertNotEquals("「没答上」与「真没有」是两句话，不能同串", failure, empty)
        assertTrue("没答上那句没点名是哪一站：$failure", failure.contains("yande.re"))
        assertTrue("没答上那句没点名是哪一位：$failure", failure.contains("kiki"))
        assertTrue("零张那句没点名站：$empty", empty.contains("yande.re"))
        // 第三句（整条链出事）住在屏上那一格，由 ⑥ 之外的那条判据盯着；这里钉它不同前两句的形状。
        val pane = code("DesktopArtistProfilePane.kt")
        assertTrue("屏上没有「取数出了事」那第三句话：" + pane.contains("与「这一站没答上」是两句话"),
            pane.contains("与「这一站没答上」是两句话"))
    }

    /** ⑥ 名单那一行的箭头真有目的地了，屏上"没有单人页"那句随上架一起下架。 */
    @Test
    fun 入口接上了而那句缺席说明下架() {
        val page = code("DesktopArtistsPage.kt")
        // 锚点钉到**造目的地那颗件**与**那道动口**本身（不是 `DesktopArtistTarget(` 那串调用形状）：
        // 站点次序那条规矩今天住在 policy 里，屏这边写 `DesktopArtistTarget(it, name)` 那种手抄形状反倒是判据要防的。
        assertTrue("画师名单那一行没走那颗造目的地的件", page.contains("desktopArtistProfileTarget("))
        assertTrue("那一枚入口没接动口", page.contains("onOpenArtist("))
        val wired = code("VeneraDesktopApp.kt")
        assertTrue("单人页没被接进主区", wired.contains("DesktopArtistProfilePane("))
        val pane = code("DesktopArtistProfilePane.kt")
        assertTrue("屏上那句「点名字就去搜索…」要有名字可点才念，而它现在还得说清单人页在", pane.contains("DesktopArtistTarget"))
        listOf("DesktopArtistsPage.kt", "DesktopGalleryDomainPanes.kt", "DesktopDomains.kt").forEach { name ->
            assertEquals("$name 还写着「没有画师单人页」", 0, File(uiDir, name).readText().split("没有画师单人页").size - 1)
        }
    }

    /** ⑦ 别名那一层今天不在这条路上：零张时不猜、不换名重搜。 */
    @Test
    fun 零张时不自动换别名重搜() {
        // 同样只看代码行：类注释里点名"别名这一层今天不在这条路上"是要的（§12 一号原则要求缺席点名）。
        listOf("DesktopArtistProfilePolicy.kt", "DesktopArtistProfilePane.kt").forEach { name ->
            val text = code(name)
            listOf("别名", "alias", "Alias").forEach { word ->
                assertEquals("$name 的取数那一路掺进了 $word（批次 J 今天不在这屏做）", 0, text.split(word).size - 1)
            }
        }
        // 对照钉的是**分界**而不是"有没有件"：`GalleryArtistAlias` 那颗件早就在 main 里（用在 yande.re 的
        // 画师记录 / 头像 URL 那一路），而批次 J 提的"搜索给 0 行时换正名重搜"那一路确实没接 ——
        // 所以桌面这一屏不吃别名是**刻意的边界**，不是缺件，也不是"那件事做完了"。
        // 这条对照钉的是"件在而这条路没接"：哪天接了它，这里会红着要求把禁串一起重谈，而不是静默变成假边界。
        val named = DesktopSourceTree.androidMainSources()
            .flatMap { file -> DesktopSourceTree.codeLinesOf(file).filter { it.contains("GalleryArtistAlias") } }
        assertTrue("对照失效：对岸那颗 `GalleryArtistAlias` 没了（那这一条禁串就是恒真装饰）", named.isNotEmpty())
    }

    /**
     * ⑧ 墙是现成那颗，而且不许被包进纵向滚动（整屏崩那两条不变式之一）。
     *
     * ⚠️ 这一条从前还钉过"屏上不许出现「下一页」" —— 批 21 把续页接上之后那句**成了假话**，
     * 按"它要防的事"改判：它防的从来不是那三个字，而是**摆一枚按下去什么都不发生的按钮**。
     * 那一件事今天由 ⑫ 钉（到底要消失、不许留灰钮），所以这里只留墙面与不变式那两条。
     * 这是 §0 坑 ㉖ 的**第九次**，而且红在上一批刚写的判据自己身上 —— 同族的旧账。
     */
    @Test
    fun 墙面吃现成件而不破滚动不变式() {
        val pane = code("DesktopArtistProfilePane.kt")
        assertTrue("没有复用墙上那颗件", pane.contains("DesktopGalleryPostWall("))
        assertTrue("没有过守卫那一层", pane.contains("DesktopGalleryWallPolicy.of("))
    }

    /** ⑩ 目的地那条"取第一站"的规矩要真跑数得出来（形状判据抓不到取错站这种错）。 */
    @Test
    fun 多站的行取第一站而零站的行不摆入口() {
        val two = GallerySite.entries.take(2)
        val target = requireNotNull(desktopArtistProfileTarget("kiki", two)) { "两站的行取不到目的地" }
        assertEquals("取的不是那一行的第一站", two.first(), target.site)
        assertEquals("名字丢了", "kiki", target.name)
        assertEquals("零站的那种行该交回 null（屏上整枚不摆）", null, desktopArtistProfileTarget("kiki", emptyList()))
    }

    /**
     * ⑨ 段头那一句：点名站与名字，而**过期的那句边界不许回来**。
     *
     * 批 19 到这里钉的是"段头必须念『这一屏今天不接续页』"，那是当时的边界。
     * 续页接上之后同一件事换了主语：段头**不许再承诺一件已经做到的事**，
     * 而"这一屏说了什么"仍然要上屏（不是只住在文档里）。
     */
    @Test
    fun 段头点名站与名字而那句不接续页下架() {
        val line = desktopArtistHeadline(target)
        assertTrue("那句没点名站与名字：$line", line.contains("yande.re") && line.contains("kiki"))
        assertTrue("那句还在念批 19 的旧边界：$line", !line.contains("不接续页"))
        assertTrue("屏上没有摆那句段头", code("DesktopArtistProfilePane.kt").contains("desktopArtistHeadline("))
    }

    /**
     * ⑪ 续页**吃共享那颗持有件**，桌面这边不另造一份页码账。
     *
     * 这一条是 §0 坑 ㉓ 那一族的正面版：共享面 `nextPagesForAppend` 从第一天起就写着
     * "缺席的腿从第 1 页补"，而发现屏从前手抄了一份 `page + 1`，后果是**某一页的图永久漏掉**
     * （不崩、不红字，屏上只写"这一站没答上"）。这一屏今天只有一条腿，抄一份看起来更无害 ——
     * 而"只有一条腿所以可以手抄"正是那一批开始时的想法。
     */
    @Test
    fun 续页吃共享持有件而不另造页码账() {
        // 批 22 把"一页回来后怎么落地"从屏里抽到了判据面（为了 ⑰–⑳ 能真跑），
        // 所以这一条扫的是**那两片合起来**的文本 —— 判据不变：这一屏的续页账必须走共享那三颗。
        val pane = code("DesktopArtistProfilePane.kt") + "\n" + code("DesktopArtistProfilePolicy.kt")
        listOf(
            "desktopGalleryBatchLegs(" to "该问哪几条腿没转交共享那颗件",
            "desktopGalleryBatchNextPages(" to "下一笔要第几页没转交共享那颗件",
            "desktopGalleryBatchApply(" to "一页落地没走那颗 reducer",
            "DesktopGalleryBatch(" to "这一屏根本没有那颗持有件（页码、去重键、到底三本账没地方放）",
        ).forEach { (token, why) ->
            assertTrue("$why：屏上读不到 $token", pane.contains(token))
        }
        // 手抄页码账的两种形状都钉住（`base.page` 是那枚"最深一页"的读数口，拿它算下一笔就是第二份账）。
        listOf("page + 1", "base.page", "batch.page").forEach { handCopied ->
            assertEquals("屏上另造了一份页码账（$handCopied）", 0, pane.split(handCopied).size - 1)
        }
        // 对照：那三颗共享件真的存在（读空会让上面四条 contains 恒真）。
        val holder = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryBatch.kt"))
        assertTrue("对照失效：那颗持有件自己不再转交共享算式了", holder.contains("nextPagesForAppend("))
    }

    /**
     * ⑫ 那枚「下一页」要**真接上取数**，而到底时**消失**而不是变成按不动的灰钮。
     *
     * 批 19 的 ⑧ 防的就是这一格，只是那时没有东西可防。今天钉三样：
     * 文案走那颗件（不许在屏上写死一串）、可见性吃 `atEnd`（判据在共享那颗持有件上，桌面不重述）、
     * 不许留一枚 `enabled = false` 的按钮（那是"假开关"最省事的写法，而它比没有按钮更坏）。
     */
    @Test
    fun 那枚下一页真接上取数而到底时消失() {
        val pane = code("DesktopArtistProfilePane.kt")
        assertTrue("「下一页」的文案没走那颗共享件（屏上写死一串字，页尾那句就会与它漂）", pane.contains("desktopGalleryMoreLabel("))
        assertTrue("那枚按钮的可见性没吃到底判据", pane.contains("atEnd"))
        assertEquals(
            "到底时留了一枚 disabled 的按钮 —— 屏上按不动的按钮比没有按钮更坏",
            0,
            pane.split("enabled = false").size - 1,
        )
        // 对照：那枚文案真的写着「下一页」（读空会让上面第一条 contains 恒真）。
        assertTrue("对照失效：那颗共享件里的按钮文案不是「下一页」", desktopGalleryMoreLabel(18, unit = "张").contains("下一页"))
    }

    /**
     * ⑬ 页尾那三档各说一句，而"没取成"那一句必须**替已经摆出来的那些张担保**。
     *
     * 第三句不是修辞：续页失败时第一页还在屏上，如果只说"下一页没取成"，
     * 用户读到的就是"这一屏坏了"，然后他会整屏重进 —— 而重进会把已经看过的那一批换掉。
     */
    @Test
    fun 页尾三档各一句而失败那句替旧张数担保() {
        assertEquals("Idle 那一档不该有话说（页尾就是那枚按钮）", null, desktopGalleryMoreReading(DesktopGalleryMorePhase.Idle, taken = 18, unit = "张"))
        val loading = requireNotNull(desktopGalleryMoreReading(DesktopGalleryMorePhase.Loading, taken = 18, unit = "张"))
        val failed = requireNotNull(desktopGalleryMoreReading(DesktopGalleryMorePhase.Failed("timeout"), taken = 18, unit = "张"))
        val end = desktopArtistEndReading(target, taken = 36)
        assertTrue("正在取那一句没说清在取什么：$loading", loading.contains("下一页"))
        assertTrue("失败那句没点名原因：$failed", failed.contains("timeout"))
        assertTrue("失败那句没替已经摆出来的那些张担保：$failed", failed.contains("一张不少"))
        assertTrue("到底那句没点名是哪一站：$end", end.contains("yande.re"))
        assertTrue("到底那句没报今天一共摆了几张：$end", end.contains("36"))
        assertNotEquals("三句塌成一句了", loading, failed)
        assertNotEquals("到底与失败塌成一句了", end, failed)
    }

    /**
     * ⑭ 「一页 18 张」这枚预算在**两处**必须是同一枚常量：发出去要几页的 limit，
     * 与"回来的张数算不算到底"那把尺。两处写成两个数的那一种漂法是想得出的：
     * 要 18 张而按 20 判到底 ⇒ 每一页都被判成到底，那枚按钮在第一页之后就消失。
     *
     * 这里不扫字面形状（那是坑 ㉖），而是**真跑**共享那颗到底判据：
     * 满一页不算到底、少一张就算到底 —— 用的正是这一屏发出去的那枚 limit。
     */
    @Test
    fun 到底那把尺吃的就是发出去的那枚预算() {
        val site = GallerySite.YANDERE
        val full = GallerySearchMerge.exhaustedSites(mapOf(site to DESKTOP_ARTIST_SLOT_SIZE)) { DESKTOP_ARTIST_SLOT_SIZE }
        val short = GallerySearchMerge.exhaustedSites(mapOf(site to DESKTOP_ARTIST_SLOT_SIZE - 1)) { DESKTOP_ARTIST_SLOT_SIZE }
        assertTrue("满一页就被判成到底（那枚按钮第一页之后就会消失）：$full", full.isEmpty())
        assertEquals("少一张却没被判成到底（会一直要同一页）：$short", setOf(site), short.toSet())
        // 对照：这一屏**只有一处**发车点（第一轮与第 N 轮共用一颗函数），所以 limit 只写一次。
        val pane = code("DesktopArtistProfilePane.kt")
        assertEquals(
            "取数那一路被写成了两份（两条路各写一份取数就是「第一页能翻而续页判据漂」的成因）",
            1,
            pane.split("searchPosts(").size - 1,
        )
    }

    /** ⑮ 新页尾那几句仍然是**单腿**的措辞：不许借多站那套词。 */
    @Test
    fun 新页尾那几句不许借多站措辞() {
        val readings = listOf(
            requireNotNull(desktopGalleryMoreReading(DesktopGalleryMorePhase.Loading, taken = 18, unit = "张")),
            requireNotNull(desktopGalleryMoreReading(DesktopGalleryMorePhase.Failed("x"), taken = 18, unit = "张")),
            desktopArtistEndReading(target, taken = 18),
            desktopGalleryMoreLabel(18, unit = "张"),
        )
        readings.forEach { line ->
            listOf("各站", "每站", "这一轮发出去的标签", "三站").forEach { multiLeg ->
                assertTrue("那句借了多站的措辞（$multiLeg）：$line", !line.contains(multiLeg))
            }
        }
    }

    /**
     * ⑯ 那一屏的**状态形状**跟着换：`Ready` 交回的是那颗持有件，而不是裸张数表。
     *
     * 为什么这一条要单独钉：续页最省事的错法是"再取一页然后 `posts + 新的`"，
     * 那样去重键（`seenKeys`）与逐腿页码都不会更新 ⇒ 站方第 2 页与第 1 页重叠时
     * **同一张图会摆两遍**，而屏上那句"取回 N 张"数的是两遍。
     */
    @Test
    fun 状态交回的是那颗持有件() {
        val pane = code("DesktopArtistProfilePane.kt")
        val policy = code("DesktopArtistProfilePolicy.kt")
        assertTrue("Ready 那档交回的还是裸张数表（去重键与页码没人记）：$pane", pane.contains("Ready(val batch: DesktopGalleryBatch"))
        // ⚠️ 钉**整串表达式**而不是标识符 `seenKeys`：扰动把这一行改成 `seenKeys = emptySet()` 时，
        // 只扫标识符的判式照样绿（§0 坑 ㉖ 的**第十次**，而且又红在本批刚写的那条上）。
        // 这条今天只是"接线还在"的哨兵 —— 那件事本身由 ⑰⑱⑲ 真跑盖住（批 22 把落地那颗抽成
        // 不发网络的纯函数之后才跑得动）。
        assertTrue("追加那一步没带上这一批已有的去重键（同一张图会摆两遍）", policy.contains("seenKeys = base.seenKeys"))
    }

    /**
     * ⑰ 真跑：两页重叠一张 ⇒ 追加之后**一张不摆两遍**。
     *
     * 这一条是批 21 那条文本判据（`seenKeys = base.seenKeys`）的**升级**：扰动第一次跑出绿之后
     * 才看清，"去重键要带上"这件事不该只靠扫一行字 —— 而它又不该要一颗整只的 fake `GalleryPorts`，
     * 所以把"一页回来后怎么落地"抽成一颗不发网络的 [desktopArtistBatchAfter]，判据直接真跑它。
     */
    @Test
    fun 两页重叠那一张不摆两遍() {
        val site = GallerySite.YANDERE
        val query = "anmi"
        val first = (1L..18L).map { postOf(site, it) }
        // 第二页与第一页**重叠一张**（站方翻页不保证边界稳定，这是常态不是意外）。
        val second = (18L..35L).map { postOf(site, it) }
        val base = DesktopGalleryBatch(seed = 0L, queries = mapOf(site to query))
        val after1 = desktopArtistBatchAfter(base, DesktopArtistTarget(site, "anmi"), page = 1, posts = first, answered = true, reason = null)
        assertEquals("第一页就丢了张数", 18, after1.posts.size)
        val after2 = desktopArtistBatchAfter(after1, DesktopArtistTarget(site, "anmi"), page = 2, posts = second, answered = true, reason = null)
        assertEquals("重叠那一张被摆了两遍（去重键没吃上一批已有的）", 35, after2.posts.size)
        assertEquals(
            "屏上有重复条目",
            after2.posts.size,
            after2.posts.map { it.uid }.distinct().size,
        )
        assertEquals("页码账没记到第 2 页", mapOf(site to 2), after2.pageBySite)
        assertTrue("那一页到底了却没被记上（下一笔还会去要它）", after2.sitesDone.isEmpty())
    }

    /** ⑱ 真跑：那一笔**没答上**时，页码不前进、原因要点名、不许被算成到底。 */
    @Test
    fun 没答上那一笔不推进页码也不算到底() {
        val site = GallerySite.YANDERE
        val target = DesktopArtistTarget(site, "anmi")
        val base = DesktopGalleryBatch(seed = 0L, queries = mapOf(site to "anmi"))
        val ok = desktopArtistBatchAfter(base, target, 1, (1L..18L).map { postOf(site, it) }, answered = true, reason = null)
        val flaky = desktopArtistBatchAfter(ok, target, 2, emptyList(), answered = false, reason = "timeout")
        assertEquals("没答上的一页把张数改了", ok.posts, flaky.posts)
        assertEquals("没答上的那一页被记进页码账了（那一页的图就永久漏掉）", mapOf(site to 1), flaky.pageBySite)
        assertTrue("没答上却没留下原因：" + flaky.failures, flaky.failures[site] == "timeout")
        assertTrue("缺席被算成到底了（一次抖动就永久不再问这一站）", !flaky.atEnd)
        assertEquals("到底判据没通过时，下一笔还要得到那一页的号", 2, desktopArtistNextPage(flaky, target, "anmi"))
    }

    /** ⑲ 真跑：回来的张数**不足一页**才算到底，而到底之后不再要页（那枚按钮该消失）。 */
    @Test
    fun 不足一页才算到底而到底之后不再要页() {
        val site = GallerySite.YANDERE
        val target = DesktopArtistTarget(site, "anmi")
        val base = DesktopGalleryBatch(seed = 0L, queries = mapOf(site to "anmi"))
        val full = desktopArtistBatchAfter(base, target, 1, (1L..18L).map { postOf(site, it) }, answered = true, reason = null)
        assertTrue("满一页被判成到底（那枚按钮第一页之后就没了）：" + full.sitesDone, !full.atEnd)
        assertEquals(1, desktopArtistNextPage(full, target, "anmi").let { if (it == null) -1 else 1 })
        val tail = desktopArtistBatchAfter(full, target, 2, (19L..23L).map { postOf(site, it) }, answered = true, reason = null)
        assertTrue("少一张却没被判成到底（会一直要同一页）：" + tail.sitesDone, tail.atEnd)
        assertEquals("到底了还在要下一笔", null, desktopArtistNextPage(tail, target, "anmi"))
    }

    /** ⑳ 真跑：第一笔要的是第 1 页，而**没标签 / 没发过车**那两种形状不在这儿（屏上另有两句）。 */
    @Test
    fun 第一笔要的就是第一页() {
        val site = GallerySite.YANDERE
        val target = DesktopArtistTarget(site, "anmi")
        val fresh = DesktopGalleryBatch(seed = 0L, queries = mapOf(site to "anmi"))
        assertEquals(1, desktopArtistNextPage(fresh, target, "anmi"))
        // 对照：`queries` 空的那种批次（这一屏压根没发车）不该被说成"到底"。
        assertTrue("空 queries 被判成到底（那是空屏不是看完）", !DesktopGalleryBatch(seed = 0L).atEnd)
    }

    private fun postOf(site: GallerySite, id: Long) = GalleryPost(
        site = site,
        id = id,
        tags = "anmi",
        previewUrl = "https://img/$site-$id.jpg",
        fileExt = "jpg",
    )
}
