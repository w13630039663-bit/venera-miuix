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
     * [DesktopDomain.isInTopGroup] / [DesktopDomain.precedesSpacer] 判出来的，
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
     * ⚠️ 缺席域的**数目**钉在 3：2026-10-04 骨架那批是五枚（发现 / 搜索 / 收藏 / 画师 / 设置），
     * 第二批把搜索与收藏两域接成真页面 ⇒ 剩 3 枚。这个数是"还有哪几域没做"的清单，
     * 改它就等于悄悄多接或少接了一域 —— 而那正是本文件其余断言要覆盖的事。
     */
    @Test
    fun `缺席域的短句在预算内 且点名了已接线的件`() {
        val absent = DesktopDomain.entries.mapNotNull { domain -> domain.absence?.let { domain to it } }
        assertEquals(
            "七枚里应当剩 3 枚是缺席域（发现 / 画师 / 设置）—— 搜索与收藏已在第二批转正",
            3,
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
     * ⑥ 四枚真能进的域：`absence` 必须是 `null`。
     *
     * 看上去像在测一件显而易见的事，其实防的是**回滚**：有人为了排查临时给某一域挂回一句
     * 缺席说明的那一刻，这一批搬进来的取数就又变回"写了从不读"。
     *
     * ⚠️ 名单从 2 枚长到 4 枚（第二批接进收藏与搜索）—— 这条断言的**形状**没变，
     * 变的是它守的那份清单，而清单本身就是"桌面今天真有几屏能看"的规格。
     */
    @Test
    fun `图库 漫画 收藏 搜索四域真能进`() {
        val wired = listOf(
            DesktopDomain.GALLERY,
            DesktopDomain.COMIC,
            DesktopDomain.FAVORITES,
            DesktopDomain.SEARCH,
        )
        wired.forEach { domain ->
            assertTrue(
                "「${domain.title}」应当是真能进的（absence = null）",
                domain.isWired,
            )
        }
        assertEquals(
            "真能进的域应当恰好是这四枚 —— 剩下三枚仍带缺席说明",
            4,
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
            "onSelect 必须把新的域真的写进 state，否则 rail 永远停在初始那一格",
            appCode.contains("onSelect = { domain = it }"),
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
        assertTrue(
            "onDispose 里必须是 session?.close()",
            comic.contains("onDispose { session?.close() }"),
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
}
