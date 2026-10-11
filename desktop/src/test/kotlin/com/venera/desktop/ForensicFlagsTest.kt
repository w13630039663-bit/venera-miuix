package com.venera.desktop

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.desktop.gallery.ui.DesktopDomain
import com.venera.desktop.gallery.ui.DesktopGalleryHomeSectionKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 取证参数的判读（Task 6 第 4 件）。
 *
 * 这几条钉的是**形状**，不是打印：`-Pautofav=0` 必须判成关（原来是开，因为只看给没给）、
 * 两参同给必须有一句实话（原来是 favcheck 先 exitProcess、autofav 被静默吃掉）、
 * 不认识的 `--xxx` 必须被列出来（原来是无声忽略）。
 * 读数本身在 `:desktop:app` 的真实运行里另取（见 task-6 报告）。
 */
class ForensicFlagsTest {

    @Test
    fun 值为关就判成关而不是开() {
        for (off in listOf("0", "false", "off", "OFF ", "False")) {
            assertEquals("--autofav=$off 该判成关", false, ForensicFlags.state(listOf("--autofav=$off"), "--autofav"))
        }
    }

    @Test
    fun 没给参数是null只给名字是开() {
        assertNull(ForensicFlags.state(listOf("jm", "--proxy=127.0.0.1:7890"), "--autofav"))
        assertEquals(true, ForensicFlags.state(listOf("--autofav"), "--autofav"))
        for (on in listOf("1", "true", "on", "ON")) {
            assertEquals(true, ForensicFlags.state(listOf("--favcheck=$on"), "--favcheck"))
        }
    }

    @Test
    fun 读不懂的值按关处理不猜成开() {
        assertEquals(false, ForensicFlags.state(listOf("--autofav=maybe"), "--autofav"))
        assertEquals(false, ForensicFlags.state(listOf("--autofav="), "--autofav"))
    }

    @Test
    fun 两参同给必须说一句() {
        val both = listOf("--autofav=1", "--favcheck=1")
        val note = ForensicFlags.bothGivenNote(
            ForensicFlags.state(both, "--autofav"),
            ForensicFlags.state(both, "--favcheck"),
        )
        assertNotNull("同给必须有读数", note)
        assertTrue("要说清 autofav 不执行：$note", note!!.contains("只跑 favcheck") && note.contains("autofav"))
        // favcheck 值为关时不提前退出，这句得跟着变，不能照抄"只跑 favcheck"
        val favOff = ForensicFlags.bothGivenNote(true, ForensicFlags.state(listOf("--favcheck=0"), "--favcheck"))
        assertTrue("favcheck 关时要改口：$favOff", favOff!!.contains("值为关"))
        assertNull(ForensicFlags.bothGivenNote(true, null))
        assertNull(ForensicFlags.bothGivenNote(null, null))
    }

    @Test
    fun 拼错的参数一律被列出来() {
        assertEquals(
            listOf("--autofv=1", "--shots=a.png"),
            ForensicFlags.unknown(listOf("jm", "--autofv=1", "--shots=a.png", "--proxy=127.0.0.1:7890")),
        )
        // 认识的（含带值的）不许混进来
        assertEquals(
            emptyList<String>(),
            ForensicFlags.unknown(listOf("jm", "--autofav=0", "--favcheck=1", "--shot=a.png", "--proxy=null")),
        )
    }

    /**
     * `--start-domain=` 认**两种写法**（枚举名大小写不敏感 + rail 上的中文题名），
     * 并且**七枚域各自都过一遍**而不是点两枚：将来加第八枚域时只往 enum 里塞一行而忘了它
     * 能不能被认回来，症状是"那一域的取证静默不可用"——本批存在的目的就是把"要看哪一域"
     * 变成读数，所以这条不能只抽两枚。
     */
    @Test
    fun 起始域对每枚域都认枚举名与中文题名() {
        DesktopDomain.entries.forEach { domain ->
            listOf(domain.name, domain.name.lowercase(), domain.title).forEach { raw ->
                val readout = ForensicFlags.startDomain(listOf("--start-domain=$raw"))
                assertTrue(
                    "「--start-domain=$raw」应认成「${domain.title}」，实际是 ${readout::class.simpleName}",
                    readout is StartDomainReadout.Resolved,
                )
                val hit = readout as StartDomainReadout.Resolved
                assertEquals("「--start-domain=$raw」认错了域", domain, hit.domain)
                assertEquals("读数里要留用户原样打进来的那串", raw, hit.raw)
            }
        }
    }

    /**
     * **没给**与**给了但认不出**必须是两种读数，而后者绝不塌回默认域。
     *
     * 这条钉的是本批唯一一次口径分岔：[ForensicFlags.state] 对读不懂的值是"按关处理"，
     * 因为那颗读不懂顶多半次取证白跑；这一颗若也"按默认处理"，产出的就是**一张顶着目标域
     * 文件名的图库域截图** —— 文件名替它作了伪证，读图的人不会知道参数没生效。
     * 「宁可错慢不可静默交错」在这一颗上的形状就是：宁可不启动，也不交回一张错的图。
     */
    @Test
    fun 没给起始域与认不出是两种读数且不塌回默认域() {
        assertTrue(
            "没给参数应判成 NotGiven（不打读数，与本轮无关）",
            ForensicFlags.startDomain(listOf("jm", "--shot=a.png")) is StartDomainReadout.NotGiven,
        )
        listOf("--start-domain=没有这一域", "--start-domain=图库域", "--start-domain=1").forEach { arg ->
            val readout = ForensicFlags.startDomain(listOf(arg))
            assertTrue(
                "「$arg」应判成认不出 —— 它不是任何一枚域的枚举名也不是题名",
                readout is StartDomainReadout.Unresolved,
            )
            assertEquals(
                "认不出时要把原值带回去（读数里要说清用户打的是什么）",
                arg.removePrefix("--start-domain="),
                (readout as StartDomainReadout.Unresolved).raw,
            )
        }
    }

    /** 只给名字不带值 = 认不出。这一颗单独放着，是因为 `state()` 那边同样的形状是"按开处理" */
    @Test
    fun 起始域只给名字不带值是认不出() {
        listOf("--start-domain", "--start-domain=").forEach { arg ->
            val readout = ForensicFlags.startDomain(listOf(arg))
            assertTrue("「$arg」没带值，不能当成默认域：$readout", readout is StartDomainReadout.Unresolved)
            assertEquals("", (readout as StartDomainReadout.Unresolved).raw)
        }
    }

    /**
     * 新参数必须登记进 KNOWN。
     *
     * 带**对照组**：只断言"登记的不在 unknown 里"是半条判据 —— KNOWN 整颗清空它照样绿。
     * 所以同一条用例里再钉一枚名字差一个字母的**必须**被报出来。
     */
    @Test
    fun 起始域参数已登记而拼错那枚仍要报出来() {
        assertEquals(emptyList<String>(), ForensicFlags.unknown(listOf("jm", "--start-domain=SETTINGS")))
        assertEquals(
            listOf("--start-domainn=SETTINGS"),
            ForensicFlags.unknown(listOf("--start-domainn=SETTINGS")),
        )
    }

    /**
     * `--query=` 的三档：没给 / 给了带值 / 给了不带值。
     *
     * 第五批补这一枚是因为一条取证缺口：搜索域那一屏要输入才有内容，而键盘与鼠标自验是禁的
     * （手册 §8）—— 于是"AI 角标在真数据上长什么样"这类账**结构性地拍不到**。
     * 这一枚把查询串变成命令行上的读数，与 `--start-domain=` 同一族。
     *
     * ⚠️ "没给"（`null`）与"给了空串"（`""`）**必须是两个值**：前者是"这一轮不预填"，
     * 后者是"用户明确要一个空查询的那一屏"（空态、建议词那一档）。塌成一个就说不清是哪件事。
     */
    @Test
    fun 查询串三档判读() {
        assertNull(ForensicFlags.query(listOf("jm", "--proxy=127.0.0.1:7890")))
        assertEquals("ai-generated", ForensicFlags.query(listOf("--query=ai-generated")))
        // 带空格的查询要原样留着（站方标签里有 `flat chest` 这种两词标签，trim 掉中间就换了一个标签）。
        assertEquals("flat chest", ForensicFlags.query(listOf("--query=flat chest")))
        assertEquals("", ForensicFlags.query(listOf("--query")))
        assertEquals("", ForensicFlags.query(listOf("--query=")))
    }

    /** 这一枚也要登记进 KNOWN，同上一条的形状：带对照组，防"清空 KNOWN 也绿"。 */
    @Test
    fun 查询串参数已登记而拼错那枚仍要报出来() {
        assertEquals(emptyList<String>(), ForensicFlags.unknown(listOf("--start-domain=SEARCH", "--query=ai")))
        assertEquals(listOf("--qurey=ai"), ForensicFlags.unknown(listOf("--qurey=ai")))
    }

    /**
     * 查询串只在**搜索域**有对象。给在别的域上要如实说一句 ——
     * 不中止（域名由 `--start-domain=` 自己说，这一枚不会替它作伪证），但绝不静默。
     */
    @Test
    fun 查询串落在别的域要有一句实话() {
        assertNull("没给查询串时一句都不该说", ForensicFlags.queryNote(null, DesktopDomain.SEARCH))
        assertNull("落在搜索域，正合适，不用说", ForensicFlags.queryNote("ai", DesktopDomain.SEARCH))
        assertNull("给了空串但落在搜索域：那是用户要的空态那一屏，不用说", ForensicFlags.queryNote("", DesktopDomain.SEARCH))
        val note = ForensicFlags.queryNote("ai", DesktopDomain.GALLERY)
        assertNotNull("落在图库域却不预填任何东西，必须出声", note)
        assertTrue("那句实话得点名两个东西：查询串与落在哪一域", note!!.contains("ai") && note.contains("图库"))
    }

    /**
     * `--start-section=` 的三档：没给 / 给了带值 / 给了不带值。
     *
     * 这一枚是给「pane 里那一行」用的（`--start-domain=` 只到域，到不了域内那一行）。
     * 裸给不带值**不当成"没给"**：那是一条读得出形状的输入，屏上会落在第一行，
     * 而"落在第一行"与"没指定"是两句话（同 `--query` 那一档的判法）。
     */
    @Test
    fun 起始行三档判读() {
        assertNull(ForensicFlags.section(listOf("jm", "--proxy=127.0.0.1:7890")))
        assertEquals("latest", ForensicFlags.section(listOf("--start-section=latest")))
        assertEquals("最新", ForensicFlags.section(listOf("--start-section=最新")))
        assertEquals("", ForensicFlags.section(listOf("--start-section")))
        assertEquals("", ForensicFlags.section(listOf("--start-section=")))
    }

    /** 登记进 `KNOWN`（漏登记会被 [ForensicFlags.unknown] 报成拼错），且没命中行要说一句实话。 */
    @Test
    fun 起始行参数已登记而没命中那一行要有一句实话() {
        assertTrue(
            "`--start-section` 没登记进 KNOWN —— 那它每次都会被报成拼错的参数",
            ForensicFlags.KNOWN.contains("--start-section"),
        )
        assertEquals(
            "只给这一枚时一句未识别都不该有",
            emptyList<String>(),
            ForensicFlags.unknown(listOf("--start-domain=GALLERY", "--start-section=latest")),
        )
        assertNull("没给起始行时一句都不该说", ForensicFlags.sectionNote(null, matched = true, domain = DesktopDomain.GALLERY))
        assertNull(
            "没给起始行、也没命中（本来就没有行可命中），同样不用说",
            ForensicFlags.sectionNote(null, matched = false, domain = DesktopDomain.GALLERY),
        )
        assertNull(
            "命中了那一行，正合适，不用说",
            ForensicFlags.sectionNote("latest", matched = true, domain = DesktopDomain.GALLERY),
        )
        val note = ForensicFlags.sectionNote("nonsense", matched = false, domain = DesktopDomain.GALLERY)
        assertNotNull("给了一枚对不上任何行的起始行却不出声，那就是「窗口开在第一行」被读成「参数生效了」", note)
        assertTrue(
            "那句实话得点名三个东西：给的值、落在哪一域、以及「没预选任何东西」",
            note!!.contains("nonsense") && note.contains("图库") && note.contains("没预选"),
        )
    }

    /**
     * ⑮ `--artist=` 的判读：把「进那一位的单人页」变成命令行上的读数（第二十批）。
     *
     * 这一枚补的是批 19 留下的**取证缺口**：单人页是"点一下才出来的那一层"，而鼠标自验是禁的
     *（手册 §8）—— 没有它，那一屏的全部判据都只到"编译过了"为止，而"拍不到图"会被读成"那一屏起不来"。
     *
     * 值写成 `<站点>/<标签>` 两截，因为那一屏**单腿**（一次一站），而同一个标签在两站间不是同一批图：
     * 只给名字就得替用户猜一站，猜错的那张图会顶着对的名字。斜杠按**第一个**分 ——
     * 标签自己可能含斜杠（站方没禁那个字符），切错就把一枚标签劈成两枚。
     *
     * 四档必须分开：没给 / 认得出 / 读不出（形状错）/ 读得出但落点与 `--start-domain=` 不一致。
     * 后两档各修各的：前者改输入，后者什么都不用改（那是一次覆盖）。
     */
    @Test
    fun 单人页种子判读分四档() {
        assertTrue(
            "没给这一枚却判成别的档 —— 「这一轮不进去」与「进错了屏」就分不开",
            ForensicFlags.artist(listOf("jm", "--query=ai")) is ArtistReadout.NotGiven,
        )

        val hit = ForensicFlags.artist(listOf("--artist=yandere/kiki"))
        assertTrue("合法形状判不出 Resolved：$hit", hit is ArtistReadout.Resolved)
        hit as ArtistReadout.Resolved
        assertEquals("站点段认成了别的一站", GallerySite.YANDERE, hit.target.site)
        assertEquals("标签段被切错了", "kiki", hit.target.name)
        assertEquals("读数里要能对上原样打进来的那串", "yandere/kiki", hit.raw)

        // 站点的另外两种写法也认（与 `--start-domain=` 同一口径：枚举名 / 键 / 站方自己的写法）。
        listOf("YANDERE/kiki", "yande.re/kiki").forEach { arg ->
            val r = ForensicFlags.artist(listOf("--artist=$arg"))
            assertTrue("`$arg` 该认成 yande.re，实际是 $r", r is ArtistReadout.Resolved)
            assertEquals(GallerySite.YANDERE, (r as ArtistReadout.Resolved).target.site)
        }
        // 对照：三站各自的键都要认得 —— 只把 yandere 写进表也能让上面那几条绿。
        GallerySite.entries.forEach { site ->
            val r = ForensicFlags.artist(listOf("--artist=${site.routeKey}/kiki"))
            assertTrue("${site.routeKey} 认不出：$r", r is ArtistReadout.Resolved && r.target.site == site)
        }
        // 标签里的空格原样留着（`flat chest` 这种两词标签真实存在，trim 掉中间就换了一枚标签）。
        val spaced = ForensicFlags.artist(listOf("--artist=yandere/abc def"))
        assertEquals("abc def", (spaced as ArtistReadout.Resolved).target.name)

        listOf("--artist", "--artist=", "--artist=kiki", "--artist=nope/kiki", "--artist=yandere/", "--artist=yandere/  ")
            .forEach { arg ->
                val r = ForensicFlags.artist(listOf(arg))
                assertTrue("`$arg` 是一枚读不出的输入，不该塌回「没给」：$r", r is ArtistReadout.Unresolved)
                assertTrue(
                    "`$arg` 没说清为什么读不出来（三种形状的修法不同）",
                    (r as ArtistReadout.Unresolved).why.isNotBlank(),
                )
            }
    }

    /**
     * ⑯ 这一枚同样要登记进 `KNOWN`，而**给了却没落到那一屏**时必须出声。
     *
     * 两档出声是两件不同的事，措辞得分得开：
     * - 读不出（形状错）⇒ 本轮不进单人页，屏上还是起始那一域；
     * - 读得出、但 `--start-domain=` 指的是别处 ⇒ 域名以 `--artist` 为准（单人页在「画师」那一域的第二层里）。
     * 后者不是错误而是一次**覆盖**，而覆盖不吭声就是"参数互相打架时悄悄赢了一边"那一族的形状。
     */
    @Test
    fun 单人页种子已登记而没落到那一屏要有一句实话() {
        assertTrue(
            "`--artist` 没登记进 KNOWN —— 那它每次都会被报成拼错的参数",
            ForensicFlags.KNOWN.contains("--artist"),
        )
        assertEquals(
            "只给这两枚时一句未识别都不该有",
            emptyList<String>(),
            ForensicFlags.unknown(listOf("--start-domain=ARTISTS", "--artist=yandere/kiki")),
        )
        assertEquals(listOf("--artst=kiki"), ForensicFlags.unknown(listOf("--artst=kiki")))

        assertNull("没给这一枚时一句都不该说", ForensicFlags.artistNote(ForensicFlags.artist(emptyList()), flagDomain = null))
        val resolved = ForensicFlags.artist(listOf("--artist=yandere/kiki"))
        assertNull("种子认出来了、也没给域名：正合适", ForensicFlags.artistNote(resolved, flagDomain = null))
        assertNull(
            "种子与 --start-domain=ARTISTS 同给，说的是同一屏",
            ForensicFlags.artistNote(resolved, flagDomain = DesktopDomain.ARTISTS),
        )
        val overridden = ForensicFlags.artistNote(resolved, flagDomain = DesktopDomain.SETTINGS)
        assertNotNull("种子把用户点名的那一域顶掉了却不出声，那就是「悄悄赢了一边」", overridden)
        assertTrue(
            "那句实话得点名三样：给的值、被顶掉的那一域、以及谁赢 —— 实际：$overridden",
            overridden!!.contains("yandere/kiki") && overridden.contains("设置") && overridden.contains("以 --artist 为准"),
        )

        val unreadable = ForensicFlags.artistNote(ForensicFlags.artist(listOf("--artist=kiki")), flagDomain = null)
        assertNotNull("读不出却不出声，那张图就顶着单人页的文件名", unreadable)
        assertTrue(
            "那句实话要点名三样：给的值、「本轮没进单人页」、可选的站点写法 —— 实际：$unreadable",
            unreadable!!.contains("kiki") && unreadable.contains("没进单人页") && unreadable.contains("yandere"),
        )
    }

    /**
     * ⑰ `--ranking=` 的判读：把「停在榜的哪一档、哪一期」变成命令行上的读数（第三十批）。
     *
     * 这一枚补的是批 29 留下的**取证缺口**：期次那一排三枚按钮只在档切到月/年时才出现，
     * 而鼠标自验是禁的（手册 §8）—— 六条判据全绿而那三枚拍不到，"那一层验过了"就是假话。
     *
     * 值写成 `<档>[/<期>]`。档段三种写法都认（枚举名 / 下拉里的整行题 / 胶囊上那一小截），
     * 期段可省 = 本期。期段不是多余的：「这是回看的历史期」那一句与那枚期次标记都只在锚点非空时出形状，
     * 省掉期段就只能拍到"回看层开着而停在本期"那一格。
     *
     * ⚠️ 判读吃 `todayUtc` 而且**必填**：地板那一档与"那一期还没到"那一档都是相对本期判的，
     * 而带默认值的新参数漏传不报错（手册 §0 那一族）—— 给了默认值就会静默按机器时钟判，
     * 用例也就换不出两种时钟。
     */
    @Test
    fun 排行榜种子判读分四档() {
        val today = LocalDate.of(2026, 10, 7)
        assertTrue(
            "没给这一枚却判成别的档 —— 「这一轮不动档位」与「停错了档」就分不开",
            ForensicFlags.ranking(listOf("jm", "--query=ai"), today) is RankingReadout.NotGiven,
        )

        listOf("MONTH", "month", "按月排行", "月").forEach { arg ->
            val r = ForensicFlags.ranking(listOf("--ranking=$arg"), today)
            assertTrue("`$arg` 该认成月档，实际是 $r", r is RankingReadout.Resolved)
            r as RankingReadout.Resolved
            assertEquals("`$arg` 认成了别的一档", GalleryRanking.MONTH, r.seed.ranking)
            assertNull("只给档名 = 本期，不该有锚点", r.seed.anchor)
            assertEquals("读数里要能对上原样打进来的那串", arg, r.raw)
        }

        // 对照：六档全都要认得 —— 只把月档写进表也能让上面那几条绿。
        GalleryRanking.entries.forEach { ranking ->
            val r = ForensicFlags.ranking(listOf("--ranking=${ranking.name}"), today)
            assertTrue("${ranking.name} 认不出：$r", r is RankingReadout.Resolved && r.seed.ranking == ranking)
        }

        val month = ForensicFlags.ranking(listOf("--ranking=MONTH/2024-03"), today)
        assertEquals("月档的期段该落到那个月的第一天", LocalDate.of(2024, 3, 1), (month as RankingReadout.Resolved).seed.anchor)
        val year = ForensicFlags.ranking(listOf("--ranking=YEAR/2019"), today)
        assertEquals("年档的期段该落到那一年的第一天", LocalDate.of(2019, 1, 1), (year as RankingReadout.Resolved).seed.anchor)
        val singleDigitMonth = ForensicFlags.ranking(listOf("--ranking=月/2024-3"), today)
        assertEquals("月段允许一位数", LocalDate.of(2024, 3, 1), (singleDigitMonth as RankingReadout.Resolved).seed.anchor)

        listOf(
            "--ranking",                 // 不带值 = 给了又没给全，与"没给这一枚"是两种读数
            "--ranking=",
            "--ranking=nope",
            "--ranking=MONTH/20-03",
            "--ranking=MONTH/2024-13",
            "--ranking=MONTH/2024-3-4",
            "--ranking=YEAR/2024-03",
            "--ranking=DAY/2024-03",
            "--ranking=ALL/2019",
            "--ranking=MONTH/2006-05",
            "--ranking=MONTH/2099-01",
            "--ranking=MONTH/2024-03/1",
        ).forEach { arg ->
            val r = ForensicFlags.ranking(listOf(arg), today)
            assertTrue("`$arg` 是一枚读不出的输入，不该塌回「没给」：$r", r is RankingReadout.Unresolved)
            assertTrue(
                "`$arg` 没说清为什么读不出来（每一档的修法不一样）",
                (r as RankingReadout.Unresolved).why.isNotBlank(),
            )
        }
        // 「默认（最新）」这一档的整行题带全角括号，原样要认（那是下拉里那一行的字面）。
        val newest = ForensicFlags.ranking(listOf("--ranking=${GalleryRanking.NEWEST.label}"), today)
        assertTrue("整行题认不出：$newest", newest is RankingReadout.Resolved && newest.seed.ranking == GalleryRanking.NEWEST)
    }

    /**
     * ⑱ 这一枚同样要登记进 `KNOWN`，而**给了却没落到那一屏**时必须出声。
     *
     * 四档出声各是各的事，措辞得分得开：
     * - 读不出（形状错）⇒ 本轮不进那一档，屏上仍是排行榜那一屏的默认档；
     * - 域被顶 ⇒ 起始域以 `--ranking` 为准（那一屏在「图库」域里）；
     * - 行被顶 ⇒ 起始行同理（`--start-section=` 指的是别行时那一屏根本不在组合里）；
     * - 与 `--artist` 同给 ⇒ 赢的是画师那枚，这一枚**没种进去**，得点名是哪一枚落空。
     * 后三档都不是错误而是一次覆盖或一次落空，不吭声就是"参数互相打架时悄悄赢了一边"那一族。
     */
    @Test
    fun 排行榜种子已登记而没落到那一屏要有一句实话() {
        val today = LocalDate.of(2026, 10, 7)
        assertTrue(
            "`--ranking` 没登记进 KNOWN —— 那它每次都会被报成拼错的参数",
            ForensicFlags.KNOWN.contains("--ranking"),
        )
        assertEquals(
            "只给这两枚时一句未识别都不该有",
            emptyList<String>(),
            ForensicFlags.unknown(listOf("--start-domain=GALLERY", "--ranking=MONTH/2024-03")),
        )
        assertEquals(listOf("--rankin=MONTH"), ForensicFlags.unknown(listOf("--rankin=MONTH")))

        assertNull(
            "没给这一枚时一句都不该说",
            ForensicFlags.rankingNote(ForensicFlags.ranking(emptyList(), today), null, null, artistWins = false),
        )
        val resolved = ForensicFlags.ranking(listOf("--ranking=MONTH/2024-03"), today)
        assertNull(
            "种子认出来了、域与行都没点名、画师种子也没抢：正合适",
            ForensicFlags.rankingNote(resolved, flagDomain = null, flagSection = null, artistWins = false),
        )
        assertNull(
            "用户点的域就是图库、行就是排行榜：说的是同一屏，不必重复",
            ForensicFlags.rankingNote(
                resolved,
                flagDomain = DesktopDomain.GALLERY,
                flagSection = DesktopGalleryHomeSectionKey.RANKING,
                artistWins = false,
            ),
        )

        val dom = ForensicFlags.rankingNote(resolved, flagDomain = DesktopDomain.SETTINGS, flagSection = null, artistWins = false)
        assertNotNull("种子把用户点名的那一域顶掉了却不出声，那就是「悄悄赢了一边」", dom)
        assertTrue(
            "那句实话得点名三样：给的值、被顶掉的那一域、谁赢 —— 实际：$dom",
            dom!!.contains("MONTH/2024-03") && dom.contains("设置") && dom.contains("以 --ranking 为准"),
        )

        val sec = ForensicFlags.rankingNote(
            resolved,
            flagDomain = null,
            flagSection = DesktopGalleryHomeSectionKey.LATEST,
            artistWins = false,
        )
        assertNotNull("行被顶掉而不出声，那张图会顶着排行榜的文件名", sec)
        assertTrue(
            "那句实话要点名差的是**行**（给的键与要去的那一行都在）—— 实际：$sec",
            sec!!.contains(DesktopGalleryHomeSectionKey.LATEST) &&
                sec.contains("行") &&
                sec.contains("以 --ranking 为准"),
        )

        val stolen = ForensicFlags.rankingNote(resolved, flagDomain = null, flagSection = null, artistWins = true)
        assertNotNull("--artist 与 --ranking 同给而赢的是前者，这一枚必须说自己没生效", stolen)
        assertTrue(
            "那句实话要同时点名两枚参数并说清谁落空 —— 实际：$stolen",
            stolen!!.contains("--artist") && stolen.contains("--ranking") && stolen.contains("没种进去"),
        )

        val unreadable = ForensicFlags.rankingNote(
            ForensicFlags.ranking(listOf("--ranking=nope"), today),
            flagDomain = null,
            flagSection = null,
            artistWins = false,
        )
        assertNotNull("读不出却不出声，那张图就顶着那一档的文件名", unreadable)
        assertTrue(
            "那句实话要点名三样：给的值、「没进排行榜那一屏」、可选的档名 —— 实际：$unreadable",
            unreadable!!.contains("nope") && unreadable.contains("没进排行榜") && unreadable.contains("MONTH"),
        )
    }

    /**
     * ⑳ 落点（域与行）跟着种子走，而两枚种子同给时**一字不改**用户的落点。
     *
     * 这一格以前只住在 `main()` 里（那颗要建窗口、要数据目录，测试碰不到），于是只剩文本锚点 ——
     * 而"接管"的判式里就写着 `DesktopDomain.GALLERY`、正面读数那句也写着同一串，
     * 撤掉接管不会红（手册 §0 坑 ㉛ 的同族：锚点替不相干的那一处作证）。
     * 抽成 [ForensicFlags.rankingLanding] 之后这一条是**真跑**。
     */
    @Test
    fun 种子认得出时域与行一起跟着走() {
        val today = LocalDate.of(2026, 10, 7)
        val resolved = ForensicFlags.ranking(listOf("--ranking=MONTH/2024-03"), today)
        val hit = ForensicFlags.rankingLanding(
            resolved,
            DesktopDomain.SETTINGS,
            DesktopGalleryHomeSectionKey.LATEST,
            artistWins = false,
        )
        assertEquals(
            "种子认得出而域没跟着落到「图库」—— 那一屏不在组合里，屏上什么也不会出现",
            DesktopDomain.GALLERY,
            hit.domain,
        )
        assertEquals("域跟了而行没跟 —— 档与期都没处种", DesktopGalleryHomeSectionKey.RANKING, hit.section)

        val untouched = ForensicFlags.rankingLanding(
            ForensicFlags.ranking(emptyList(), today),
            DesktopDomain.SEARCH,
            DesktopGalleryHomeSectionKey.LATEST,
            artistWins = false,
        )
        assertEquals("没给这一枚却改了用户的域", DesktopDomain.SEARCH, untouched.domain)
        assertEquals("没给这一枚却改了用户的行", DesktopGalleryHomeSectionKey.LATEST, untouched.section)

        val wrongShape = ForensicFlags.rankingLanding(
            ForensicFlags.ranking(listOf("--ranking=nope"), today),
            DesktopDomain.SETTINGS,
            null,
            artistWins = false,
        )
        assertEquals(
            "读不出那一枚把用户的域改掉了 —— 它只是没进去，不是替用户改主意",
            DesktopDomain.SETTINGS,
            wrongShape.domain,
        )

        // 两枚种子同给：赢的是 `--artist`，这一枚不改落点（改了就跟 rankingNote 那句"没种进去"打脸）。
        val stolen = ForensicFlags.rankingLanding(resolved, DesktopDomain.ARTISTS, null, artistWins = true)
        assertEquals("--artist 赢时域被排行榜种子抢回来了", DesktopDomain.ARTISTS, stolen.domain)
        assertNull("--artist 赢时行也被改掉了", stolen.section)
    }

    /**
     * 反搜那颗种子（第三十六批 批 36）：**登记、接管、实话**三样一起钉。
     *
     * 为什么要有这一枚：结果那一格是"按一下才出来的那一层"，而鼠标自验是禁的（手册 §8）——
     * 批 35 的图证只能拍到那枚按钮本身，拍不到它按下去之后的任何东西。
     * 与批 20（`--artist=`）、批 30（`--ranking=`）同一族，形状也照那两枚：
     * ⚠️ "接管浮层"这一格**必须做成跑得动的纯函数**（批 30 定的那条）：留在 `main()` 里就只剩文本锚点，
     * 而文本锚点看不见死分支（把 `reverse == true` 改成恒 false，屏上浮层不弹，判据仍然绿）。
     */
    @Test
    fun 反搜种子已登记而它把浮层一起摆出来() {
        assertTrue("--reverse 没登记进 KNOWN（拼错与不认得是两条读数，不能混）", ForensicFlags.KNOWN.contains("--reverse"))
        assertEquals("--reverse 被报成了不认识的参数", emptyList<String>(), ForensicFlags.unknown(listOf("--reverse")))
        assertEquals("--reverse=1 被报成了不认识的参数", emptyList<String>(), ForensicFlags.unknown(listOf("--reverse=1")))
        // 接管那一格：四格各一个答案（第三枚 `copy` 是批 54 加的那一档，这里逐格显式给 null，
        // 因为"哪枚种子会替用户开窗"这件事漏掉一档就是静默 —— 那颗函数不给默认值）。
        assertTrue(ForensicFlags.detailPanel(detail = null, reverse = true, copy = null, save = null))
        assertTrue(ForensicFlags.detailPanel(detail = false, reverse = true, copy = null, save = null))
        assertTrue(ForensicFlags.detailPanel(detail = true, reverse = null, copy = null, save = null))
        assertFalse(ForensicFlags.detailPanel(detail = null, reverse = null, copy = null, save = null))
        assertFalse("值为关却被接管了", ForensicFlags.detailPanel(detail = null, reverse = false, copy = null, save = null))
    }

    /** 那句实话只在**它替用户多开了一扇东西**时念（不吭声就是"参数打架时悄悄赢了一边"那一族）。 */
    @Test
    fun 反搜种子接管浮层时有一句实话() {
        val note = ForensicFlags.reverseNote(detail = null, reverse = true)
        assertTrue("没给 --detail 而 --reverse 把浮层摆出来了，这一句必须念：" + note, note != null && note.contains("--reverse"))
        assertTrue("那句要说清多开的是什么：" + note, note!!.contains("浮层"))
        assertNull("--detail 本来就开着，没有多开任何东西 ⇒ 不该吭声", ForensicFlags.reverseNote(detail = true, reverse = true))
        assertNull("--reverse 没给 ⇒ 与本轮无关", ForensicFlags.reverseNote(detail = true, reverse = null))
        assertNull("值为关那一档由 state 念，这里不重复一句", ForensicFlags.reverseNote(detail = null, reverse = false))
    }

    /**
     * 「复制图片」那颗种子（第五十四批）：**登记、接管、实话**三样一起钉，形状照批 36 那两枚。
     *
     * ⚠️ 接管那一格是**同一颗函数**（`detailPanel`）而不是第二颗：「哪枚种子会替用户开窗」是同一件事，
     * 分两处判就要在参数同给时漂出两种答案，而那时 `D_落点` 念的是其中一处。
     */
    @Test
    fun 复制种子已登记而它把浮层一起摆出来() {
        assertTrue("--copy 没登记进 KNOWN（拼错与不认得是两条读数，不能混）", ForensicFlags.KNOWN.contains("--copy"))
        assertEquals("--copy 被报成了不认识的参数", emptyList<String>(), ForensicFlags.unknown(listOf("--copy")))
        assertEquals("--copy=1 被报成了不认识的参数", emptyList<String>(), ForensicFlags.unknown(listOf("--copy=1")))
        assertTrue(ForensicFlags.detailPanel(detail = null, reverse = null, copy = true, save = null))
        assertTrue(ForensicFlags.detailPanel(detail = false, reverse = null, copy = true, save = null))
        assertFalse("值为关却被接管了", ForensicFlags.detailPanel(detail = null, reverse = null, copy = false, save = null))
        assertFalse("三枚都没给时浮层不该自己开", ForensicFlags.detailPanel(detail = null, reverse = null, copy = null, save = null))
    }

    /** 那句实话只在**它替用户多开了一扇东西**时念（批 85 把 `--copy` 与 `--save` 并成一颗：同一扇多开的窗）。 */
    @Test
    fun 复制种子接管浮层时有一句实话() {
        val note = ForensicFlags.overlaySeedNote(detail = null, copy = true, save = null)
        assertTrue("没给 --detail 而 --copy 把浮层摆出来了，这一句必须念：" + note, note != null && note.contains("--copy"))
        assertTrue("那句要说清多开的是什么：" + note, note!!.contains("浮层"))
        assertNull("--detail 本来就开着，没有多开任何东西 ⇒ 不该吭声", ForensicFlags.overlaySeedNote(detail = true, copy = true, save = null))
        assertNull("--copy 没给 ⇒ 与本轮无关", ForensicFlags.overlaySeedNote(detail = null, copy = null, save = null))
        assertNull("值为关那一档由 state 念，这里不重复一句", ForensicFlags.overlaySeedNote(detail = null, copy = false, save = null))
        // 批 85 那一枚同档：只给 --save 时那句必须点名「存到本机」，而不许把 --copy 一起算进来。
        val saved = requireNotNull(ForensicFlags.overlaySeedNote(detail = null, copy = null, save = true))
        assertTrue("没点名 --save：" + saved, saved.contains("--save") && saved.contains("存到本机"))
        assertFalse("只给了 --save 却把 --copy 念进来了：" + saved, saved.contains("--copy"))
    }

    /**
     * 崩一枚那颗种子（批 53）当时**漏了这一条**：判读、消费点、构建脚本三跳都接了，唯独没登记进 KNOWN，
     * 于是带 `--crash` 起窗时 stdout 同时念出「参数已判读」与「未识别的参数」—— 两句互相打脸。
     *
     * 这一条不是补出来的漂亮判据，它钉的是这一族反复踩的那个坑：**新增一枚参数要连带登记那张表**。
     */
    @Test
    fun 崩一枚那颗种子登记在案() {
        assertTrue("--crash 没登记进 KNOWN", ForensicFlags.KNOWN.contains("--crash"))
        assertEquals("--crash=thread 被报成了不认识的参数", emptyList<String>(), ForensicFlags.unknown(listOf("--crash=thread")))
        // 对照组：那张表仍然真在管不认识的那一族（清空 KNOWN 会让上面两条恒真）。
        assertEquals("对照组失效：不认识的参数也没被报出来了", listOf("--nonsense=1"), ForensicFlags.unknown(listOf("--nonsense=1")))
    }
}
