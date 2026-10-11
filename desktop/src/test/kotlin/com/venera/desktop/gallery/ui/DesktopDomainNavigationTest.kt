package com.venera.desktop.gallery.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **多域骨架**的形状判据 —— 2026-10-04 这一批把桌面从"一个画面"改成"一个应用"时落的防线。
 *
 * ## 这一批到底治了什么
 *
 * 改之前 `VeneraDesktop()` 根里有约 300 行状态（`cards` / `covers` / `session` / `open` /
 * `pages` / `showFavorites` / `favTree` …），三个 `LaunchedEffect` 忙着往里写，最后一行只渲染
 * `DesktopGalleryHome(ports)`。逐个数下来，那批变量**每一条的全部引用都是自己赋值给自己** ——
 * 一个消费点都没有。而它们为真：源脚本要装载、探索页要拉、**三十张封面要串行出网**。
 *
 * rail 那头同样是"看起来做了"：`DesktopGalleryRail` 收的 `selected` 由调用方写死成
 * `GALLERY`，于是点了另外两枚只会往主区底部吐一句缺席说明，当前域从来不变。
 * 用户看得见七个格子、看得见 hover 反馈，点下去才发现哪里也没去 —— 那是假开关。
 *
 * ## 判据分成两条通道
 *
 * - **能真跑的一律真跑**：[DesktopDomain] 是 internal 枚举、[desktopDomainRows] 是纯函数，
 *   测试与它们同包，所以七枚次序、缺席成对、caption 字长都能直接取值而不是扫源码 ——
 *   改一行注释就能绕过正则，但绕不过一次取值。
 * - **只有"不该存在"的东西才扫文本**：这类东西出场方式太多（直接调用、lambda、再包一层 `fun`），
 *   唯有"这个文件里不许出现这串"守得住。
 */
class DesktopDomainNavigationTest {

    // ─────────────── 真跑：七枚域本身 ───────────────

    /**
     * ① rail 上恰好七枚，**次序**照稿 `:354-364`。
     *
     * 次序不是排版偏好：稿上「图画组｜漫画」之间那道 8px 空隙与「设置」贴底是靠
     * [DesktopDomain.precedesSpacer] 判出来的（批 26 撤了另一枚同族的判据：它零调用点），
     * 而这两枚 getter 写的是序数比较 —— 有人往中间插一枚，设置就掉到半空中。
     */
    @Test
    fun `rail 恰好七枚 次序照稿`() {
        assertEquals(
            "rail 七枚的次序应与稿 :354-364 逐枚对齐",
            listOf("GALLERY", "DISCOVER", "SEARCH", "FAVORITES", "ARTISTS", "COMIC", "SETTINGS"),
            DesktopDomain.entries.map { it.name },
        )
    }

    /**
     * ② 每一枚都要落脚 —— 有内容位就**不许**再挂缺席说明，反之亦然。
     *
     * 这一条的原型是 `DesktopGalleryHomeSectionsTest` 里那条 XOR：`DesktopGalleryHomeRow` 传的是
     * `content` / `reason` 两枚独立字段，于是"既能渲染又有理由"（渲染出一个从没念出口的缺席说法）
     * 和"两者都空"（本仓最忌的"看起来正常但实际少了一半"）这两种形状都可能被写出来。
     */
    @Test
    fun `有内容位与缺席说明必须同真同假`() {
        val rows = DesktopDomain.entries.flatMap { desktopDomainRows(it) }
        rows.forEach { row ->
            if (row.content == null) {
                assertTrue(
                    "「${row.title}」没有内容位，必须带一句为什么缺席",
                    row.reason?.isNotBlank() == true,
                )
            } else {
                assertTrue(
                    "「${row.title}」已经有内容件了，不该再挂缺席理由（那会让界面同时宣称两件事）",
                    row.reason == null,
                )
            }
        }
    }

    /**
     * ③ 缺席域的 `reason` 必须在 pane caption 的字数预算内，且 `wired` 不许空。
     *
     * 字数那条是**量出来的而不是审美**：pane 224 扣掉 [DesktopGalleryMetrics.captionStart]
     * 后一行约 28 个汉字，`DesktopUnimplementedRow` 给的是 `maxLines = 2` 加省略号。
     * 写长了就被吃掉 —— 那等于"缺席"没被说出来，正是这批最忌的静默交错。
     *
     * `wired` 非空锁的是**缺席的种类**：这张清单把"接好了但没画"和"这台机器上没有"
     * 分成两句话。空清单等于退回那句什么都说了、等于什么都没说的"暂不支持"。
     *
     * ⚠️ 缺席域的**数目**钉在 0：2026-10-04 骨架那批是五枚（发现 / 搜索 / 收藏 / 画师 / 设置），
     * 第二批把搜索与收藏两域接成真页面（剩 3 枚），第七批转正**设置域**（§21）、
     * 第八批转正**画师域**（§22）、第九批转正**发现域**（§23）—— 七枚域到此全部是真页面。
     * 这个数是"还有哪几域没做"的清单，改它就等于悄悄多接或少接了一域。
     *
     * ⚠️ 今天它是 0，所以下面那个 `forEach` **一条都不会跑到** —— 那几条措辞断言此刻等于没验证过。
     * 真钉住"不许挂回一句缺席说明"的是这一行 `assertEquals(0, absent.size)` 本身；
     * 循环留着唯一的用途是：将来真出现缺席域时它立刻开始工作。别把它当成"这条已经覆盖了"。
     */
    @Test
    fun `缺席域的短句在预算内 且点名了已接线的件`() {
        val absent = DesktopDomain.entries.mapNotNull { domain -> domain.absence?.let { domain to it } }
        assertEquals(
            "七枚里应当**没有**缺席域了（设置第七批、画师第八批、发现第九批陆续转正）—— " +
                "挂回一句缺席说明之前，先答这一屏为什么又看不见了",
            0,
            absent.size,
        )

        absent.forEach { (domain, absence) ->
            val reason = absence.reason
            assertTrue(
                "「${domain.title}」的缺席短句超出 caption 两行预算（${reason.length} 字 > 30）：$reason",
                reason.length <= 30,
            )
            assertTrue(
                "「${domain.title}」的 wired 清单是空的 —— 那就把「接好了但没画」和「本机没有」混成同一句了",
                absence.wired.isNotEmpty(),
            )
            absence.wired.forEach { item ->
                assertTrue(
                    "「${domain.title}」的 wired 条目必须点名到具体成员，不许写「底层已就绪」这类话：$item",
                    item.length >= 8,
                )
            }
        }
    }

    /**
     * ④ 缺席理由**不许**用那些不承诺任何东西的措辞。
     *
     * "敬请期待" / "暂不支持" / "开发中" 这三种写法在本仓是同一类问题：它们描述的是
     * 时间表而不是缺席的东西。用户读完不知道能不能绕、差的是哪一层，于是下一次还会在同一个位置点一遍。
     */
    @Test
    fun `缺席理由不许用不承诺任何东西的措辞`() {
        val forbidden = listOf("敬请期待", "暂不支持", "开发中", "即将上线", "TODO")
        DesktopDomain.entries.forEach { domain ->
            val reason = domain.absence?.reason ?: return@forEach
            forbidden.forEach { word ->
                assertTrue(
                    "「${domain.title}」的缺席理由里出现了「$word」—— 那说的是时间表而不是缺席的东西，" +
                        "用户读完不知道差的是哪一层：$reason",
                    reason.contains(word).not(),
                )
            }
        }
    }

    /**
     * ⑤ 每个域都必须查得到自己的页条，包括只有一行的缺席域。
     *
     * 缺席域那一行的意义在 [desktopDomainRows] 的注释里：pane **不许空着**。
     * 空 pane 会让"这个域到底有没有东西"变成一次猜测 —— 而猜测是支持工单的形状。
     */
    @Test
    fun `七个域各自都有页条 缺席域至少一行`() {
        DesktopDomain.entries.forEach { domain ->
            val rows = desktopDomainRows(domain)
            assertTrue("「${domain.title}」的页条一行都没有 —— pane 会空着，那是让用户去猜", rows.isNotEmpty())
            assertEquals(
                "「${domain.title}」的页条 key 不许重复（重复的 key 会让选中态同时命中两行）",
                rows.size,
                rows.map { it.key }.toSet().size,
            )
        }
    }

    /**
     * ⑥ 七枚真能进的域：`absence` 必须是 `null`，且每一域真有一行带内容件。
     *
     * 看上去像在测一件显而易见的事，其实防的是**回滚**：有人为了排查临时给某一域挂回一句
     * 缺席说明的那一刻，这一批搬进来的取数就又变回"写了从不读"。
     *
     * ⚠️ 名单从 2 枚长到 4 枚（第二批接进收藏与搜索）、5 枚（第七批接进设置）、
     * 6 枚（第八批接进画师）、7 枚（第九批接进发现）—— 这条断言的**形状**没变，
     * 变的是它守的那份清单，而清单本身就是"桌面今天真有几屏能看"的规格。
     * 长到头之后这条与"0 枚缺席域"是同一件事的两面：那一枚管不许挂回去，
     * 这一枚管"每一域真有一行能看的东西"（`absence = null` 而 rows 全空是另一种静默缺席）。
     */
    @Test
    fun `七域全部真能进`() {
        DesktopDomain.entries.forEach { domain ->
            assertTrue(
                "「${domain.title}」应当是真能进的（absence = null）",
                domain.isWired,
            )
            assertTrue(
                "「${domain.title}」挂着 absence = null 却没有一行带内容件 —— 那是比坦白页更坏的形状",
                desktopDomainRows(domain).any { it.content != null },
            )
        }
        assertEquals(
            "真能进的域应当恰好是这七枚 —— 桌面今天没有坦白页了",
            7,
            DesktopDomain.entries.count { it.isWired },
        )
    }

    // ─────────────── 扫文本：那些不该存在的东西 ───────────────

    private val appCode: String =
        DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))

    private val shellCode: String =
        DesktopSourceTree.codeText(
            File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/VeneraDesktop.kt"),
        )

    /**
     * ⑦ rail 的 `selected` 必须是**状态驱动的**，不许再写死常量。
     *
     * 这一条就是那次"假开关"的机械防线。写死的形状编译通过、跑得起来、七个格子都画得出来，
     * 只有"点了没有去任何地方"这一件事是错的 —— 而那一件恰恰是用户唯一会体验到的事。
     */
    @Test
    fun `rail 的 selected 是状态而不是写死的常量`() {
        assertTrue(
            "rail 必须由当前域驱动：selected = domain（域每变一次它就跟着变）",
            appCode.contains("selected = domain"),
        )
        assertTrue(
            "onSelect 必须把新的域真的写进 state，否则 rail 永远停在初始那一格" +
                "（⚠️ 判式只钉行为不钉写法：那颗 lambda 里要有 domain = it。" +
                "从前钉的是整串「onSelect = { domain = it }」，第四十八批在同一处补一枚落点戳就把它判红了 —— 那是写法锚点，不是行为变了）",
            appCode.contains("onSelect = {") && appCode.contains("domain = it"),
        )
        listOf(
            "DesktopGalleryDomain.GALLERY",
            "selected = DesktopDomain.GALLERY",
        ).forEach { dead ->
            assertTrue(
                "「$dead」是写死的当前域 —— 点了另外六枚当前域不会变，那是把 rail 降级成一排提示按钮",
                appCode.contains(dead).not(),
            )
        }
    }

    /**
     * ⑧ pane 宽在应用根里只引一处，七个域共用。
     *
     * "切到缺席域那一栏突然变窄"是很不起眼但很确定的缺陷信号：它等于承认每个域有自己的一套布局，
     * 正是本仓判过负的"第二套布局系统"（`docs/rounds/large-screen-adaptation-stage2-plan-2026-10-02.md:20`）。
     */
    @Test
    fun `pane 宽只有一处 切域时不许跳变`() {
        val hits = appCode.split("paneWidth").size - 1
        assertEquals(
            "pane 宽度应在应用根里只引用一次（七个域共用）—— 一处一次矛盾就是七套布局的开始，命中 $hits 次",
            1,
            hits,
        )
    }

    /**
     * ⑨ 启动不许再空跑漫画链路，但**不许因此把取证开关一起打死**。
     *
     * 这条是本批最值钱的一条读数：改之前 `main()` 一开口就装载源脚本、拉探索页、
     * 串行拉 30 张封面图，喂一串从不参与布局的变量。用户能感觉到的只有"启动有点慢"，
     * 看不到那三十次出网是白跑的。
     *
     * ⚠️ 但这儿不能粗暴地断言"整个文件里不许出现 `explore(`" —— `--autofav` 取证要能自己
     * 取它需要的那一张。守的是**位置**：旧的根组合没了，那些 API 就只能出现在取证函数内部。
     */
    @Test
    fun `启动不许再空跑漫画链路 取证那条除外`() {
        assertTrue(
            "旧的 VeneraDesktop() 根组合应已删掉 —— 它那三个 LaunchedEffect 就是启动空跑的来源",
            shellCode.contains("private fun VeneraDesktop(").not(),
        )
        assertTrue(
            "应用根应是 VeneraDesktopApp（集成方只做装配，不再自己维护那一批取数状态）",
            shellCode.contains("VeneraDesktopApp("),
        )
        listOf("cards", "covers", "favTree", "showFavorites", "pageStatus").forEach { dead ->
            assertTrue(
                "VeneraDesktop.kt 里还留着「$dead」—— 那是旧那批从不进入布局的状态，应随根组合一起删",
                Regex("\\b$dead\\b").find(shellCode) == null,
            )
        }
        assertTrue(
            "--autofav 的取证链不能跟着删 —— 它有自己的退出码判据（写链完好 = 0）",
            shellCode.contains("runAutofavEvidence("),
        )
    }

    /**
     * ⑩ `EngineSession` 必须在退出这一屏时被关掉。
     *
     * GraalJS 的 context 不是 GC 友好的东西：装载挂在 `LaunchedEffect` 上意味着每切一次进漫画域
     * 再回来就新建一颗，没有 dispose 的话看几眼就能把 JVM 撑死。
     * `DisposableEffect` 的 key 必须是 session 本身而不是 `Unit` —— 写 `Unit` 的话它在这一屏
     * 存活期间只 dispose 一次，"重取"一次就泄漏一颗。
     */
    @Test
    fun `漫画引擎必须随页面退场关闭`() {
        val comic = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicPane.kt"))
        assertTrue(
            "必须有 DisposableEffect(session) 把 GraalJS context 关掉 —— key 写 Unit 会在重取时泄漏一颗",
            comic.contains("DisposableEffect(session)"),
        )
        // ⚠️ 批 80 改判：原先这一条钉的是 `onDispose { session?.close() }` **整串**。
        // 那一形是错的——闭包读的是变量当下的值，`null → 引擎` 那次切换关掉的是刚建好的那一颗，
        // 而这条判据把它当成了规格（手册 §0 那一族："措辞锚点会把当时的诊断冻成规格"）。
        // 现在钉的是**意图**（退场要关掉取出来的那一颗），形状由 `DesktopComicDetailWiringTest` ⑦
        // 与那条负向一起钉；撤掉 close 仍然会红在这里。
        assertTrue(
            "onDispose 里必须关掉**这一刻取出来**的那一颗（写 `session?.close()` 关的是变量当下的值）",
            comic.contains("onDispose { closing?.close() }"),
        )
    }

    /**
     * ⑪ `main()` 里**不许**直接造 `EngineSession` / `DesktopJsHost`。
     *
     * ## 这一条补的是上一批判据的**缺口**（2026-10-04 第二批补上）
     *
     * ⑨ 那条只证明了"旧的根组合已删、那批状态变量不在入口文件"，它**没有**直接锁死
     * 未来有人在 `main()` 里重新写一句 `EngineSession(...)` 来图方便。
     *
     * 为什么那一句很贵（本仓为此付过一次账）：`EngineSession` 一构造就去
     * `EngineAssets` 装载源脚本（本机实测 `sources/jm.js` 38626 字节），
     * 而 `main()` 是**每一帧之前**都跑的那一处。装载发生在用户还没点任何东西的时候，
     * 症状只是"启动有点慢"，看不出是白跑的 —— 这正是上一批量到 300 行从不消费的状态才发现它的原因。
     *
     * ## 为什么用扫文本而不是"这段代码跑起来没有 IO"
     *
     * 判据的对象是**位置**（这一串不许出现在 `main()` 的函数体里），而位置这件事只有扫文本能判；
     * 真跑一轮 `main()` 要开一扇窗、要真网络，那拿不到设备时就不是判据而是许愿。
     *
     * ## 扫的是「`main()` 的函数体」而不是「整个文件」—— 这是本条第一版的错，已修
     *
     * 第一版扫了整个 `VeneraDesktop.kt`，当场把 `runAutofavEvidence` 里的两行判红（VeneraDesktop.kt:121-122）。
     * 那两行是**合法**的：那是 `--autofav` 取证链，用户显式带开关、要的就是当场装载引擎，
     * 而且它自带 `finally { engine.close() }` 与退出码判据（写链完好 = 0）。
     * 一条把合法构造点扫红的判据比没有判据更坏：下一个人只会去把判据删掉或者把取证链改坏。
     *
     * 所以正确的切法是**按函数体切**：`main` 的签名到它的收尾大括号之间不许出现这两个构造，
     * 取证函数那一段不在扫描范围内。本条同时锁死"取证链不许被顺手搬进 `main`"。
     */
    @Test
    fun `main 入口不许直接造漫画引擎`() {
        val mainBody = desktopMainBody()
        listOf("EngineSession(", "DesktopJsHost(").forEach { ctor ->
            assertTrue(
                "main() 的函数体里出现了「$ctor」—— 引擎只允许从 DesktopComicPane " +
                    "或 runAutofavEvidence 进入（前者按需装载、后者要 --autofav 开关且有退出码判据）；" +
                    "在入口就造它＝用户还没点任何东西就先装载源脚本",
                mainBody.contains(ctor).not(),
            )
        }
        // 取证链那一处必须**还在** —— 它是这条判据承认的唯一入口构造点。
        // 只断言"main 里没有"的话，有人把整条取证链删掉判据照样是绿的，而 --autofav 就静默失灵。
        assertTrue(
            "runAutofavEvidence 里那一处构造必须还在（--autofav 是它唯一的引擎入口，删了开关就静默失灵）",
            shellCode.contains("runAutofavEvidence(") &&
                shellCode.substringAfter("private fun runAutofavEvidence(")
                    .substringBefore("\nprivate fun ", "\nfun ")
                    .let { it.contains("EngineSession(") && it.contains("DesktopJsHost(") },
        )
        // 反面：漫画域那两颗真身必须在位，且装载点在那颗 pane 里。
        val comic = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicPane.kt"))
        assertTrue(
            "漫画域那一屏才是 EngineSession 的真身 —— 它被删了就变成「漫画域没有引擎」",
            comic.contains("EngineSession(") || comic.contains("DesktopJsHost("),
        )
    }

    /**
     * 取出 `VeneraDesktop.kt` 里 `fun main(...)` 的函数体（不含签名那几行的参数默认值区）。
     *
     * 为什么要写这颗而不是 `substringAfter("fun main(")`：那样会把**后面所有**顶层函数
     * （`selfShot` 等）一起扫进来，而判据的对象是 `main` 自己的函数体。
     * 做法是从 `fun main` 起按大括号配平数到收尾 —— 入口文件里没有字符串里带裸大括号的情况
     * （`"${...}"` 里的花括号是模板表达式，成对出现，配平不受影响），所以这个简化是安全的。
     */
    private fun desktopMainBody(): String {
        val start = shellCode.indexOf("\nfun main(")
        assertTrue("VeneraDesktop.kt 里找不到 fun main( —— 入口被改名或删掉时这条判据必须红", start >= 0)
        val open = shellCode.indexOf('{', start)
        var depth = 0
        var i = open
        while (i < shellCode.length) {
            when (shellCode[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return shellCode.substring(open + 1, i)
                }
            }
            i++
        }
        throw AssertionError("main() 的函数体没有配平收尾大括号 —— 文件被截断了？")
    }

    /**
     * ⑫ 三面墙**共用一颗**卡位画法，不许各画一张。
     *
     * ## 这条防的是"接第二个域时顺手再画一张卡"
     *
     * 2026-10-04 第二批把收藏域与搜索域接进来时，卡位画法原先是 `DesktopDailyPane.kt` 里的
     * 一颗 `private` 件 —— 不提成共用件，那两域就只有两个选择：复制一份（口径分叉），
     * 或者把 `private` 改成 `internal` 之后在两处直接调它。
     *
     * 选了后者并提成 [DesktopGalleryPostTile]：日榜墙 / 收藏墙 / 搜索墙吃的是**同一颗**。
     * 判据钉两件事：
     * 1. 共用件存在且是 `internal`（三处能调到）；
     * 2. **日榜墙那份文件里不再有 `private` 的同名定义** —— 只查"共用件在不在"是不够的，
     *    留着旧定义时编译器不报错（不同文件、不同可见性），屏上却已经是两套。
     */
    @Test
    fun `三面墙共用一颗卡位画法`() {
        val tile = DesktopSourceTree.desktopUiSource("DesktopGalleryPostTile.kt")
        assertTrue(
            "全桌面唯一的卡位画法应当是 DesktopGalleryPostTile.kt —— 缺了这颗，三面墙就会各画一张",
            tile.isFile,
        )
        assertTrue(
            "卡位画法必须是 internal（不是 private）—— private 的话收藏墙与搜索墙都调不到它，" +
                "而那正是本批把 private 提上来的原因",
            DesktopSourceTree.codeText(tile).contains("internal fun DesktopGalleryPostTile("),
        )
        assertTrue(
            "卡位画法应当也含那面墙（列数推导与首卡跨列都在里面）—— 少这一颗就会有三处各摆一次 LazyVerticalGrid",
            DesktopSourceTree.codeText(tile).contains("internal fun DesktopGalleryPostWall("),
        )
        listOf("DesktopDailyPane.kt", "DesktopGalleryFavoritesPane.kt", "DesktopGallerySearchPane.kt")
            .forEach { name ->
                val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(name))
                assertTrue(
                    "$name 里还留着 private 的 DesktopGalleryPostTile 定义 —— " +
                        "同一张卡两处定义时编译器不报错，屏上却是两套画法",
                    code.contains("private fun DesktopGalleryPostTile(").not(),
                )
                assertTrue(
                    "$name 里还有自己那一份 LazyVerticalGrid —— 列数推导只该在共用墙里（桌面侧唯一出处）",
                    code.contains("LazyVerticalGrid(").not(),
                )
            }
    }

    /**
     * ⑬ 缺席转真之后，那张 `wired` 清单**不许**被留下。
     *
     * 坦白页念那张清单是为了回答"差的是哪一层"。一旦这一域真能进，它要回答的问题就没有答案了 ——
     * 而清单还在的话，界面上会同时说"这一域有内容"和"这一域缺三层界面"，
     * 用户只会读到后半句，然后以为那面墙是残缺的。
     */
    @Test
    fun `真能进的域不许再挂 wired 清单`() {
        DesktopDomain.entries.filter { it.isWired }.forEach { domain ->
            assertTrue(
                "「${domain.title}」absence 已经是 null，不该再有任何 wired 清单",
                (domain.absence?.wired ?: emptyList()).isEmpty(),
            )
        }
    }

    /**
     * ⑭ `--start-domain=` 必须**真被消费**（2026-10-04 第三批）。
     *
     * ## 为什么这一颗要单独钉
     *
     * 判读层自己有用例（`ForensicFlagsTest`），所以"没接上去"这件事**不会**让那一套变红：
     * 参数打得进、窗口开得了、截图写得出，唯一断的是"起始域交给应用根"那一步 ——
     * 于是每张图都是默认那一域的，而**文件名写着目标域**。本仓为"实现了但零调用点"付过一整批的账，
     * 而这一颗的账比平常更贵，因为它产出的是伪证而不只是死代码。
     *
     * ## 为什么还是扫文本而不是真跑
     *
     * 与 ⑪ 同一条理由：真跑一轮 `main()` 要开窗、要真网络、要 Win32 截图，
     * 拿不到那些的时候它不是判据而是许愿。这里断言的是**位置与接线**，正适合扫代码行
     * （[DesktopSourceTree.codeText] 已把注释行剥掉，所以说明文字里提到这些串不会造成假命中）。
     */
    @Test
    fun `起始域参数必须真被交给应用根`() {
        val mainBody = desktopMainBody()
        assertTrue(
            "main() 里必须调 ForensicFlags.startDomain —— 判读层写了不接就是零消费",
            mainBody.contains("ForensicFlags.startDomain("),
        )
        assertTrue(
            "认不出那一档必须中止启动（不退到默认域）—— 退出码 1 是它唯一的可见后果",
            mainBody.contains("exitProcess(1)"),
        )
        assertTrue(
            "解析出来的起始域要真传进应用根（命名参数 startDomain = startDomain）",
            mainBody.contains("startDomain = startDomain"),
        )
        assertTrue(
            "应用根若还写死 mutableStateOf(DesktopDomain.GALLERY)，那颗参数就只是装饰",
            appCode.contains("mutableStateOf(DesktopDomain.GALLERY)").not(),
        )
        assertTrue(
            "当前域的初值必须是那颗参数",
            appCode.contains("mutableStateOf(startDomain)"),
        )
    }

    /**
     * ⑯ 起始查询串必须**真被交给搜索那一屏**（第五批补的那枚取证参数）。
     *
     * 这一条钉的是本仓最阴的一种缺口：**判读层写了、参数解析对了、stdout 也念了，
     * 但那一屏的初值还是写死的空串** —— 于是 `-Pquery=ai` 用起来像生效了（它不报错），
     * 拍出来的却是空态那一屏。搜索域是七枚域里唯一有输入框的，也是唯一"不给输入就拍不到东西"的。
     */
    @Test
    fun `起始查询串必须真被交给搜索那一屏`() {
        val mainBody = desktopMainBody()
        assertTrue(
            "main() 没调 ForensicFlags.query —— 判读层写了不接就是零消费",
            mainBody.contains("ForensicFlags.query("),
        )
        assertTrue("解析出来的查询串没传进应用根", mainBody.contains("startQuery = startQuery"))
        // ⚠️ 第十二批 批2 之后这一条的**形状**变了：应用根不再把裸串交给搜索屏，而是把它装进
        // 那颗种子（同一个初值还要被"点标签 / 点画师名"改写，两颗状态各写一份就会争谁覆盖谁）。
        // 不变量一字未放宽 —— 仍然是"`--query=` 那一串要走到搜索屏的输入框"，只是它现在住在种子里。
        assertTrue("应用根没把查询串装进那颗种子", appCode.contains("DesktopSearchSeed(startQuery"))
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGallerySearchPane.kt"))
        assertTrue("搜索那一屏的查询初值不是那颗种子", pane.contains("mutableStateOf(seed.query"))
        assertTrue(
            "pane 里还留着写死的空串作查询初值 —— 那正好是「参数只是装饰」的形状",
            pane.contains("mutableStateOf(\"\")").not(),
        )
        // 对照组：应用根真读到了内容（读空会让上面那两条 contains 恒真）。
        // 注：应用根调的是**域路那颗** `DesktopGallerySearchBody`，pane 在 `DesktopGalleryDomainPanes.kt` 里
        // —— 这条对照组就是被这一点教出来的：拿 `DesktopGallerySearchPane(` 作样会误报"读空了"。
        assertTrue("对照组失效：应用根里连搜索域的域路调用都没找到", appCode.contains("DesktopGallerySearchBody("))
    }

    /**
     * ⑰ 起始**行**参数必须真被用来预选 pane 里那一行（第十批 ⑤ 补的取证参数）。
     *
     * `--start-domain=` 只到域，到不了域内那一行 —— 于是图库域里新点亮的「最新」今天拍不到。
     * 这一条钉的是与 ⑯ 同一族的缺口：**判读层写了、main 也念了，而 pane 的选中行还是 `rows.first()`**，
     * 那种形状下 `-PstartSection=latest` 用起来像生效了（它不报错），拍出来的却是日榜。
     */
    @Test
    fun `起始行参数必须真被用来预选 pane 里那一行`() {
        val mainBody = desktopMainBody()
        assertTrue("main() 没调 ForensicFlags.section —— 判读层写了不接就是零消费", mainBody.contains("ForensicFlags.section("))
        assertTrue("main() 没把「对不上任何行」那一句念出来", mainBody.contains("ForensicFlags.sectionNote("))
        assertTrue("main() 没拿行表去核这一枚（键与题名都要认）", mainBody.contains("desktopDomainRows("))
        assertTrue("解析出来的起始行没传进应用根", mainBody.contains("startSection = startSection"))
        assertTrue("应用根没收这颗必填参数", appCode.contains("startSection: String?"))
        assertTrue("应用根没把它用来播种 pane 的选中行", appCode.contains("put(startDomain, startSection)"))
        // gradle 那一环同样钉：`-PstartSection=…` 没映射进 `args` 时那枚参数被**静默忽略**，
        // 而这正是本族最忌的形状 —— 命令行看起来是个开关，实际什么都没做（不报错、截图照出）。
        val gradle = DesktopSourceTree.codeText(
            java.io.File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts"),
        )
        assertTrue("`app` 任务没读 gradleProperty(\"startSection\")", gradle.contains("""gradleProperty("startSection")"""))
        assertTrue("读到了却没拼成 --start-section=…", gradle.contains("""--start-section=$"""))
        // 对照组：应用根真读到了内容（读空会让上面那两条 contains 恒真）。
        assertTrue("对照组失效：应用根里连 pane 选中行那本账都没读到", appCode.contains("mutableStateMapOf<DesktopDomain, String>()"))
    }

    /**
     * ⑮ 抢前台那一步**只许出现在取证那一路**（2026-10-04 第三批，用户拍板走"抬自己这扇窗"那条）。
     *
     * `selfShot` 现在会把这扇窗临时抬成 topmost。这件事在取证里是正解，但一旦漏进正式路径，
     * 用户每次启动都会被抢前台 —— 而"被抢前台"正是 §8 那条禁令要挡住的那种感觉。
     * 所以钉四件，每件对着一种以后会被顺手改坏的形状：
     *
     * 1. `frame.toFront()` 在入口文件里**恰好一次**，且在 `raiseOwnWindowForShot` 内（写在别处
     *    就等于多一处抢前台的入口）；`requestFocus` 一颗都**不许有** —— 抬窗要的只是 z-order，
     *    把键盘焦点从用户正在打的字上抢走与"截一张图"不成比例；
     * 2. `main()` 的函数体里**一个 `alwaysOnTop` 都不许有**：那颗窗的创建路径必须干净；
     * 3. 抬起来必须有落回，而且**两层都落**：AWT 的 `isAlwaysOnTop = false` 与 Win32 的
     *    `setTopmost(hwnd, false)` —— 只解一半的话这扇窗会一直压着用户的屏幕；
     * 4. 落回要包在 `finally` 里，而不是"成功那条路上记得写" —— 那三档"截不到"的 `return`
     *    是 `runCatching` 里的**非局部 return**，会绕过跟在后面的 `.also { }` 一类收尾。
     *    这一条就是当初选 try/finally 而不是链式收尾的原因，判据把它钉住。
     */
    @Test
    fun `抢前台只发生在取证那一路`() {
        val hits = Regex(Regex.escape("frame.toFront()")).findAll(shellCode).count()
        assertEquals(
            "「frame.toFront()」应在 VeneraDesktop.kt 里恰好出现一次（取证那一颗函数内）—— 命中 $hits 次",
            1,
            hits,
        )
        assertTrue(
            "frame.toFront() 必须待在 raiseOwnWindowForShot 里，搬进别处就是多一处抢前台的入口",
            desktopFunctionBody("raiseOwnWindowForShot").contains("frame.toFront()"),
        )
        assertEquals(
            "抬窗不许抢输入焦点：requestFocus 一颗都不该有 —— 取证要的只是 z-order，" +
                "把键盘焦点从用户正在打的字上抢走与「截一张图」完全不成比例",
            0,
            Regex(Regex.escape("requestFocus")).findAll(shellCode).count(),
        )
        assertTrue(
            "main() 的函数体里不许出现 alwaysOnTop —— 正式启动那一路不能被抢前台",
            desktopMainBody().contains("alwaysOnTop").not(),
        )
        val shot = desktopFunctionBody("selfShot")
        assertTrue("selfShot 必须把 topmost 落回 false", shot.contains("isAlwaysOnTop = false"))
        assertTrue(
            "AWT 那层落回之外，Win32 那颗位也要落回（只解一半这扇窗就一直压着用户的屏幕）",
            shot.contains("setTopmost(hwnd, false)"),
        )
        assertTrue("落回要包在 finally 里（非局部 return 会绕过链式收尾）", shot.contains("finally {"))
    }

    /**
     * ⑱ 单人页那颗种子必须**真被交给应用根**，而且构建脚本那一跳也要接上（第二十批）。
     *
     * 这一条钉的是同一族三个断点，每断一个都产出一张**看不出问题**的图：
     * 1. 判读层写了、`main()` 没调 ⇒ 至少会被 `unknown` 报成拼错的参数，还会出声；
     * 2. `main()` 调了、没传进应用根 ⇒ 屏上永远是画师列表；
     * 3. 传进了应用根、`args(...)` 里漏了这一枚 ⇒ 用户在命令行打 `-Partist=…`
     *    **既不报错也不生效**（第十一批的 `-Pdetail` 就是这么漏的，手册 §27.5 那条"没有像素证据"）。
     * 第 2、3 档不会红在任何一层，只会把"这一屏验过了"变成假话 —— 而批 19 的图证缺口正是它。
     *
     * 另外钉一条**这一枚特有的**：种子认出来时起始域要跟着落到「画师」那一域。
     * 只把目的地种进槽而域留在别处，那一屏根本不在组合里，屏上什么也不会出现 ——
     * 那是"参数给了不报错而屏上是另一屏"最省事的一种做法。
     */
    @Test
    fun `单人页种子必须真被交给应用根并接进app任务`() {
        val mainBody = desktopMainBody()
        assertTrue("main() 没调 ForensicFlags.artist —— 判读层写了不接就是零消费", mainBody.contains("ForensicFlags.artist("))
        assertTrue("main() 没把「没落到那一屏」那一句念出来", mainBody.contains("ForensicFlags.artistNote("))
        assertTrue(
            "种子认出来时起始域没跟着落到「画师」那一域 —— 只种槽不换域，屏上什么也不会出现",
            mainBody.contains("DesktopDomain.ARTISTS"),
        )
        assertTrue("解析出来的目的地没传进应用根", mainBody.contains("startArtist = "))
        assertTrue(
            "应用根没收这颗必填参数（带默认值的新参数漏传不报错，正是这一族最忌的形状）",
            appCode.contains("startArtist: DesktopArtistTarget?"),
        )
        assertTrue("应用根没拿它作单人页槽的初值", appCode.contains("mutableStateOf(startArtist)"))
        // 对照组：应用根真读到了内容（读空会让上面那两条 contains 恒真）。
        assertTrue(
            "对照组失效：应用根里连单人页那颗粒调用都没读到",
            appCode.contains("DesktopArtistProfilePane("),
        )

        val gradle = DesktopSourceTree.codeText(File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts"))
        assertTrue("`app` 任务没读 gradleProperty(\"artist\")", gradle.contains("""gradleProperty("artist")"""))
        assertTrue("`--artist` 没被拼进 args（值传不下去，判读层再对也接不到）", gradle.contains("\"--artist=\$it\""))
        assertTrue("对照组失效：这份构建脚本里没有 :desktop:app 那颗粒任务", gradle.contains("""tasks.register<JavaExec>("app")"""))
    }

    /**
     * ⑲ 排行榜那颗种子必须**一路接到那一屏的两枚初值**，而构建脚本那一跳也要接上（第三十批）。
     *
     * 与 ⑱ 同族、断点更多一层：那一枚只种"进哪一屏"，这一枚要种"那一屏里停在哪儿"，
     * 所以中间多一次域内分发（应用根 → 域分发 → 图库那一行的 body → 那一屏）。少一环都不报错：
     * 1. 判读层写了、`main()` 没调 ⇒ 会被 `unknown` 报成拼错的参数，还会出声；
     * 2. `main()` 调了、没换域换行 ⇒ 那一屏根本不在组合里，**屏上什么也不会出现**；
     * 3. 传进了应用根、没递到那一屏 ⇒ 档永远停在默认，期次那一排永远不出现（＝批 29 的缺口原样留着）；
     * 4. `args(...)` 里漏这一枚 ⇒ 用户打 `-Pranking=…` **既不报错也不生效**。
     * 第 2、3、4 档不会红在任何一层，只会把"那一层拍到了"变成假话。
     */
    @Test
    fun 排行榜种子必须真被交给那一屏并接进app任务() {
        val mainBody = desktopMainBody()
        assertTrue("main() 没调 ForensicFlags.ranking —— 判读层写了不接就是零消费", mainBody.contains("ForensicFlags.ranking("))
        assertTrue("main() 没把「没落到那一屏」那一句念出来", mainBody.contains("ForensicFlags.rankingNote("))
        // 域与行的接管走那颗纯函数（真跑判据在 `ForensicFlagsTest` ⑳）。这里只钉接线那一跳：
        // 曾经直接钉 `DesktopDomain.GALLERY` 出现过 —— 读数里那句"起始域跟着落到「图库」"也含这串字，
        // 于是接管被撤掉它照样绿（§0 坑 ㉛ 的同族：锚点会替不相干的那一处作证）。
        assertTrue("main() 没问落点那颗纯函数（域与行不再跟着种子走）", mainBody.contains("ForensicFlags.rankingLanding("))
        // ⚠️ 批 80 改判：落点现在多走一跳（`comicLanding` 排在 `rankingLanding` 之后，`--comic` 会接管起始域）。
        // 这两枚锚点原先钉的是 `landing.domain` / `landing.section`，那一串字如今只是中间结果的名字 ——
        // 继续钉它就是替一个不相干的名字作证（同一颗文件上面那段注释讲的正是这个忌）。
        // ⚠️ 批 81 又改判一次（坑 ㉖ 第八次，红在上一次刚退让过的那枚锚点上）：接管链再加了一环
        // （`--read` 排在 `--comic` 之后），`comicLanding` 不再是末端。锚点继续钉某一枚**中间结果的名字**
        // 就是替一个随时会换掉的名字作证 —— 退到"末端那一枚接管结果"这一层：换了名字仍绿，
        // 而撤掉整条接管链（退回直接吃 flagDomain）必红。
        val domainTail = Regex("""val startDomain = (\w+)\.domain""").find(mainBody)?.groupValues?.get(1)
        assertTrue("起始域不是从接管链的末端取的（那一形没了 = 接管被撤）", domainTail != null)
        assertTrue(
            "起始域退回直接吃用户的 flag：种子那一枚永远不会接上（`--ranking`/`--comic`/`--read` 全断）",
            domainTail != "flagDomain",
        )
        val sectionTail = Regex("""val startSection = (\w+)\.section""").find(mainBody)?.groupValues?.get(1)
        assertTrue("起始行不是从接管链的末端取的", sectionTail != null)
        assertTrue("起始行退回直接吃 flagSection", sectionTail != "flagSection")
        assertTrue("解析出来的种子没传进应用根", mainBody.contains("startRanking = "))
        assertTrue(
            "应用根没收这颗必填参数（带默认值的新参数漏传不报错，正是这一族最忌的形状）",
            appCode.contains("startRanking: DesktopRankingSeed?"),
        )
        assertTrue("应用根没把它递给域内分发", appCode.contains("startRanking = startRanking"))

        val sections = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryHomeSections.kt"))
        assertTrue("那一行的分发没收这颗种子", sections.contains("startRanking: DesktopRankingSeed?"))
        assertTrue("种子没递到排行榜那一屏", sections.contains("DesktopRankingPane(ports, startRanking)"))
        // 对照组：两份文本真读到了内容（读空会让上面那几条 contains 恒真）。
        assertTrue(
            "对照组失效：应用根里连域分发那颗函数都没读到",
            appCode.contains("private fun DesktopDomainBody("),
        )
        assertTrue("对照组失效：那份分发表里连「最新」那一屏都没读到", sections.contains("DesktopLatestPane(ports"))

        val gradle = DesktopSourceTree.codeText(File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts"))
        assertTrue("`app` 任务没读 gradleProperty(\"ranking\")", gradle.contains("""gradleProperty("ranking")"""))
        assertTrue("`--ranking` 没被拼进 args（值传不下去，判读层再对也接不到）", gradle.contains("\"--ranking=\$it\""))
    }

    /**
     * 取 `VeneraDesktop.kt` 里某颗顶层函数的**函数体**（大括号配平，与 [desktopMainBody] 同一条简化前提）。
     * 找不到就抛：取证那两颗函数被改名或删掉时，这条判据必须红而不是静默少扫一颗。
     */
    private fun desktopFunctionBody(name: String): String {
        val start = shellCode.indexOf("fun $name(")
        assertTrue("VeneraDesktop.kt 里找不到 fun $name( —— 取证那一路被改名或删掉了", start >= 0)
        val open = shellCode.indexOf('{', start)
        var depth = 0
        var i = open
        while (i < shellCode.length) {
            when (shellCode[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return shellCode.substring(open + 1, i)
                }
            }
            i++
        }
        throw AssertionError("fun $name 的函数体没有配平收尾大括号 —— 文件被截断了？")
    }
}
