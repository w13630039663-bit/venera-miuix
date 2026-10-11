package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面「发现」那一屏的形状判据（第九批）。
 *
 * ## 为什么这一批是扫源码而不是真跑
 *
 * 取数那一条链（抽样 → 三腿并发 → 混摆 → 守卫 → 墙）今天没有可注入的假端口 ——
 * 它吃的是 `GalleryPorts` 那一整套真件，而**造一套假的**要把 `boards` 三颗都包一层，
 * 那是另一批的活。所以这里只钉"接线形状"：哪些判据被转交了、哪两句缺席必须在屏上、
 * 哪三枚偏好必须传下去。取数本身的正确性由计划那一层（`DesktopForYouPlanTest` 七条）与
 * 共享判据自己的用例覆盖。
 *
 * ⚠️ 扫源码这一支的三条固有代价（手册 §4）在这里都成立，所以每条负向断言都配了对照组，
 * 且一律只看**代码行**（`codeText` 跳注释）。
 */
class DesktopForYouPaneTest {

    private val code: String =
        DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopForYouPane.kt"))

    /** ① 墙上那一关必须过第四批那层守卫，且分级与屏蔽两枚都真传进去。 */
    @Test
    fun 那一屏的墙要过内容守卫而不是自己摆卡() {
        assertTrue("这一屏没走 `DesktopGalleryWallPolicy.of` —— 那就是第三面没过守卫的墙", code.contains("DesktopGalleryWallPolicy.of("))
        assertTrue("守卫那颗件没把分级档传进去", code.contains("maskMode"))
        assertTrue("守卫那颗件没把屏蔽 AI 传进去", code.contains("blockAi"))
        assertTrue("守卫那颗件没拿到 contentGuard（规则与模式那两份）", code.contains("ports.contentGuard"))
        // 对照组：真跑过守卫的产物才走这一颗墙。
        assertTrue("对照组失效：没读到那一颗共用的墙", code.contains("DesktopGalleryPostWall("))
    }

    /** ② 三枚偏好一律**显式传**下去。漏一枚的形状是"某一屏上那枚档静默不生效"。 */
    @Test
    fun 三枚偏好必须显式传给那一面墙() {
        val at = code.indexOf("DesktopGalleryPostWall(")
        assertTrue("找不到那一面墙的调用点", at >= 0)
        // 取调用点往后那一段就够：不数参数位（`modifier = Modifier.fillMaxWidth().weight(1f)`
        // 里那个括号会把"数逗号"这种判法做成假红 —— 第一版就是这么红的）。
        val call = code.substring(at).take(320)
        listOf("wall = wall", "columnMode", "quality", "aiBadge").forEach { arg ->
            assertTrue("那一面墙没收到「$arg」", call.contains(arg))
        }
        // 对照组：墙必须拿到**有界**高度（外层不滚之后，是这一枚 modifier 在给高度）。
        assertTrue("墙没被给有界高度（`weight(1f)` 那一档）：$call", call.contains("weight(1f)"))
    }

    /**
     * ③ 仍然没做的那件事必须在屏上点名，而且**只用用户能感知的话**说。
     *
     * ⚠️ 这一条从前钉的是"两件事"（续页 + 沿用快照）。第十批 ③ 把续页接上了，
     * 于是那句"只给第一轮的第一页"从**真话变成了假话** —— 而它当时还留着，
     * 因为这条判据只判 `code.contains("第一页")`，而"正在取第一页…"那句里也有这三个字。
     * 现在改成钉**那颗句子本身**（`DESKTOP_FOR_YOU_SNAPSHOT_LINE`），并加一条负断言：
     * 旧那句"只给第一轮的第一页"不许再出现在屏上。
     */
    @Test
    fun 沿用快照那句要说清哪一种进来才重抽() {
        // ⚠️ 批 24 改判：这一条从前钉的是"那句含『沿用上次』"，而那句本身写着"每次进来都重新抽一轮"——
        // 批 5 把这一屏包进 `SaveableStateProvider` 之后它就是**假话**，而这条判据只判含不含那四个字，
        // 于是它替假话作了保（同模块 `DesktopDomainStateMemoryTest` ④ 钉的正是相反的事实）。
        // 现在钉的是**要点名哪一种进来才重抽**：换回来不重抽、关掉程序才重抽。
        assertTrue(
            "那句没把「换回来还是这一批」说出来：" + DESKTOP_FOR_YOU_SNAPSHOT_LINE,
            DESKTOP_FOR_YOU_SNAPSHOT_LINE.contains("再回来还是"),
        )
        assertTrue(
            "那句没点名今天真缺的那一档（关掉程序再开没有快照可沿用）：" + DESKTOP_FOR_YOU_SNAPSHOT_LINE,
            DESKTOP_FOR_YOU_SNAPSHOT_LINE.contains("关掉"),
        )
        assertEquals(
            "那句还在说「每次进来都重新抽」—— 批 5 之后那是假话",
            0,
            DESKTOP_FOR_YOU_SNAPSHOT_LINE.split("每次进来都重新抽").size - 1,
        )
        assertTrue("那句没摆上屏", code.contains("DESKTOP_FOR_YOU_SNAPSHOT_LINE"))
        assertTrue(
            "续页接上了还念「只给第一轮的第一页」—— 那就是把做掉的件事说成没做（手册 §22.3 反方向那条）",
            code.contains("只给第一轮的第一页").not(),
        )
        assertTrue(
            "那句没点名什么时候才会重新抽（旧那一版把它钉在『每…都重抽』那句话的形状上）",
            DESKTOP_FOR_YOU_SNAPSHOT_LINE.contains("重新抽"),
        )
        // ⚠️ 后面这一组是本批我自己写出来、又被这条抓掉的：屏上那句一度写着
        // "ViewModel"、"吃 Context"、"编译面的排除清单" —— 那是**实现笔记进界面**，
        // 用户读到的是一堆内部名词，而"差的是哪一层"该写在代码注释与文档里。
        val literals = Regex("\"(?:[^\"\\\\]|\\\\.)*\"").findAll(code).map { it.value }.toList()
        assertTrue("对照组失效：一颗字符串字面量都没抽到（那下面几条恒真）", literals.size > 8)
        listOf("ViewModel", "Context", "编译面", "排除清单", "GalleryForYouCache").forEach { internal ->
            literals.forEach { line ->
                assertTrue("屏上那句带了实现笔记「$internal」：$line", line.contains(internal).not())
            }
        }
    }

    /** ④ 缺席要逐站点名，且 Gelbooru 那一档是**发车前**拦 + 转交共享那句原因。 */
    @Test
    fun 缺席的站逐一点名而凭据那档转交共享原因() {
        assertTrue("没引用计划那颗交回的逐站原因", code.contains("reasonFor"))
        assertTrue("没引用没发出去的站清单", code.contains("silentSites"))
        assertTrue(
            "Gelbooru 没配账号时那句必须转交 `GallerySearchSource.NEEDS_ACCOUNT_REASON`，桌面不许另写一份",
            code.contains("GallerySearchSource.NEEDS_ACCOUNT_REASON"),
        )
        assertTrue("发车前没拦 Gelbooru（匿名一律 401，发出去只是让用户等一轮网络）", code.contains("GallerySite.GELBOORU"))
        // 对照组：腿级原因没被吞掉。
        assertTrue("对照组失效：每腿没走那颗共用的预算件", code.contains("GalleryLegGuard.guard("))
    }

    /** ⑤ 「换一批」不许是假按钮：它必须真换那颗种子，而种子只在这一屏生成一次。 */
    @Test
    fun 换一批真换种子而种子不在渲染期现取() {
        assertTrue("「换一批」没在换种子", code.contains("seed = System.currentTimeMillis()"))
        // 批 5 之后那颗种子住在 `rememberSaveable` 里（换回来还是同一批卡），这一条两种都算过：
        // 它钉的是"不在渲染期现取"，不是用哪一枚 remember 家族。
        assertTrue(
            "种子不在 remember/rememberSaveable 里（每次重组都换 = 墙自己换序）",
            Regex("remember(Saveable)?\\s*\\{\\s*mutableStateOf\\(System.currentTimeMillis\\(\\)\\)").containsMatchIn(code),
        )
        assertTrue("发出去的标签串没念到屏上", code.contains("这一轮发出去的标签"))
        // 对照组：混摆那颗真被调，且拿的是这一轮那颗种子。
        assertTrue("对照组失效：没走共享那颗混摆", code.contains("GalleryForYouMerge.page("))
        assertTrue("对照组失效：混摆没拿到本轮种子", code.contains("seed = seed"))
    }

    /** ⑥ 取数失败与一站没答上是两句话 —— 不许把前者糊成后者。 */
    @Test
    fun 取数出事与一站没答上是两档() {
        assertTrue("没有「取数出了事」那一档", code.contains("DesktopForYouState.Failed"))
        assertTrue("失败那句没说明它与单站缺席不同", code.contains("两句话"))
        assertTrue("没答上的站没有单独的位置", code.contains("failures"))
        // 续页那一笔失败是**第三种**：已经摆出来的那些不受影响，那句必须单独说。
        // ⚠️ 批 23 改判：那两句从各屏抄写收成一颗（`desktopGalleryMoreReading`），所以这里钉的是
        // "这一屏吃了那颗件"，而那两句话本身**真跑**那颗件 —— 只扫字面的话，改了件里的句子既测不到
        // 屏上少了什么，也测不到件里漂了什么（手册 §18 那条坑）。
        assertTrue("这一屏没吃页尾那三档那颗共享件", code.contains("desktopGalleryMoreReading("))
        val moreFailed = requireNotNull(
            desktopGalleryMoreReading(DesktopGalleryMorePhase.Failed("timeout"), taken = 18, unit = "张"),
        )
        assertTrue("那颗件里没有「下一页没取成」那一档：$moreFailed", moreFailed.contains("下一页没取成"))
        assertTrue("那一档没说明旧卡还在：$moreFailed", moreFailed.contains("已经摆出来的这些一张不少"))
        // 对照组：合成失败（判据炸了）走的是 getOrThrow，不会被咽成空墙。
        assertTrue("对照组失效：混摆那颗的失败被咽了", code.contains(".getOrThrow()"))
    }

    /** ⑦ 「下一页」必须真发一笔，而且**到底了就不摆**（假开关防线）。 */
    @Test
    fun 下一页真发车而到底时不摆那枚按钮() {
        // 批 23 改判同一族：文案那颗件共享，这一屏只留调用点，"下一页"那三个字真跑出来核对。
        assertTrue("屏上没有「下一页」那颗（调用点没了）", code.contains("desktopGalleryMoreLabel("))
        assertTrue("对照失效：那颗件已经不说「下一页」了", desktopGalleryMoreLabel(18, unit = "张").contains("下一页"))
        assertTrue("「下一页」没被 atEnd 挡住（到底了还摆一枚按下去没反应的按钮就是假开关）",
            code.contains("!batch.atEnd"))
        // 两条路共用同一颗取数件：各写一份就是"第一页能翻而续页的判据漂"。
        // 数的是**调用点**（那颗件自己的声明行不算 —— 第一版把声明也数进去，红在一次 3 上）。
        // 第三十二批 批 32 改判同一族：排除声明从前写的是"这一行不含 `private suspend fun`"，
        // 于是**可见性关键字被钉成了要求** —— 把 `private` 改成 `internal`（为了真跑那条链）就红在这里，
        // 而这件事与"两条路共用一颗"毫无关系。现在按"是不是声明那颗"排除。
        val calls = code.lines().count { it.contains("fetchForYouPage(") && !it.contains("fun fetchForYouPage(") }
        assertEquals("第一轮与续页没共用同一颗取数件", 2, calls)
        // 续页要往前推页码 —— 但**怎么推**这一格在第十三批 批 8 改判过一次：
        // 从前这里断言的是字面 `base.page + 1`，而那一枚正是缺陷本体（对所有腿发同一页，
        // 上一笔超时的那一站会被跳走它从没拿到的第 1 页）。现在页码只由那颗转交共享面的算式算一次。
        assertTrue("续页没算各腿的页码（页码算式只有一处：desktopGalleryBatchNextPages）", code.contains("desktopGalleryBatchNextPages("))
        assertFalse(
            "又拿全局那一枚 `page` 去推所有腿的页码了 —— 那正是「上一笔超时的一站永久漏一页」的成因",
            code.contains("base.page + 1"),
        )
        // 到底那句只能在 atEnd 那一档里念：没到底就说"已经到底"是拿一句假话结束一整屏。
        val gate = code.indexOf("if (batch.atEnd)")
        val sentence = code.indexOf("都到底了")
        assertTrue("找不到那句「到底了」", gate >= 0 && sentence >= 0)
        assertTrue("那句「都到底了」不在 atEnd 那道闸里面（它会无条件念）", sentence - gate in 0..200)
        // 对照组：抽取没读空。
        assertTrue("对照组失效：这一屏没读 batch 的页码", code.contains("batch?.page"))
    }

    /** ⑧ 「缺席不算到底」的**桌面这一半**：张数表只能从答上了的腿里来。 */
    @Test
    fun 失败的那一站不许被记成回了零条() {
        assertTrue(" pools 没只收答上了的腿（失败被当成空页 = 那一站被判到底）",
            code.contains("filter { it.second.answered }"))
        val returnedLine = code.lines().firstOrNull { it.contains("val returned") }.orEmpty()
        assertTrue("找不到那张张数表（它一旦被改名，下面那条断言就恒真了）", returnedLine.isNotEmpty())
        assertTrue(
            "张数表从全部腿算的（失败的那一站会被记成 0 条 → exhaustedSites 把它判成到底）：$returnedLine",
            returnedLine.contains("pools"),
        )
        // 对照组：reducer 真被调，且拿的是这一轮的种子（作废闸在那一头）。
        assertTrue("对照组失效：没调落地那颗", code.contains("desktopGalleryBatchApply("))
        assertTrue("对照组失效：落地没拿本轮种子", code.contains("round = base.seed"))
    }
}
