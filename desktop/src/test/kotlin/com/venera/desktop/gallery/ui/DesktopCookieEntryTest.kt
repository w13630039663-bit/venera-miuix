package com.venera.desktop.gallery.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.venera.desktop.ForensicFlags
import com.venera.desktop.gallery.data.DesktopCookieLogin
import com.venera.desktop.gallery.data.DesktopCookieOutcome
import com.venera.desktop.gallery.data.DesktopSourceNames
import com.venera.desktop.gallery.data.DesktopSourceRow
import com.venera.engineprobe.DesktopCookieJar
import com.venera.engineprobe.DesktopJsHost
import java.io.ByteArrayOutputStream
import java.io.File
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第八十八批·下半 剩下的那几格：手填 cookie 的**入口**、取证种子、以及"不许在桌面重述脚本口径"。
 *
 * ## 为什么这一批的入口只有三颗源摆得出格子
 *
 * 名单是脚本自己的 `account.loginWithCookies.fields`（随包 34 颗里只有 ehentai / lanraragi / mycomic 有那一形）。
 * 桌面**不认得也不许认得**那些名字：屏上摆几格、按什么次序交给源，全从脚本现取。
 * 写死一份名单的话，站方改字段名时脚本变了而屏上还摆老几格，用户每次都被判"cookie 无效"。
 * 所以这里既钉"解析出来的形状对不对"，也钉**桌面 main 里一个字段名都不许出现**。
 *
 * ## 取证种子那一格为什么"永不落盘"
 *
 * `--cookie=` 是给量具用的（手填那最后一步要人真粘一次，而鼠标自验是禁的）。
 * 盘上那份是用户在这台机器上的登录态，参数把它写进去等于量具动了被量的东西（批 83.1 同一条口径）。
 * 种子因此走罐里**单独那一层**：随请求发得出，而 `persist` 永远看不见它 —— 这一条是行为量出来的，
 * 不是看注释看出来的（本批 P 类扰动跑的就是那一行有没有漏）。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopCookieEntryTest {

    private val root: File = File(System.getProperty("java.io.tmpdir"), "venera-entry-${System.nanoTime()}")
    private val store = File(root, "cookies.json")
    private val exh = "https://exhentai.org/".toHttpUrl()

    private fun cookie(name: String, value: String, domain: String = "exhentai.org"): Cookie =
        Cookie.Builder().name(name).value(value).domain(domain).build()

    private fun rows() = listOf(
        DesktopSourceRow("jm", "禁漫天堂", enabled = true),
        DesktopSourceRow("ehentai", "E-Hentai", enabled = true),
    )

    private fun state(
        fields: Map<String, List<String>>,
    ) = DesktopSourcesState(
        rows = rows(),
        activeKey = "jm",
        disabledCount = 0,
        total = 2,
        pick = { null },
        toggle = { null },
        saveNote = null,
        cookieFields = fields,
    )

    /** ① 名单从脚本文本现取：三档形状（有那一形 / 没那一形 / `fields` 在窗口之外）。 */
    @Test
    fun 名单从脚本现取而不许把别的fields当成员() {
        val withForm = """
            account = {
                loginWithCookies: {
                    fields: [
                        "ipb_member_id",
                        "ipb_pass_hash",
                    ],
                    validate: async (values) => true,
                },
            }
        """.trimIndent()
        assertEquals("两枚的名单读成一枚或读丢顺序", listOf("ipb_member_id", "ipb_pass_hash"),
            DesktopSourceNames.cookieFieldsOf(withForm))
        assertEquals(
            "没有那一形的脚本不许得到一个假名单（屏上就会摆一枚按不动的框）",
            emptyList<String>(),
            DesktopSourceNames.cookieFieldsOf("ComicSource.sources = {}; let fields = [\"x\"]"),
        )
        // 窗口那一格：400 个字符之外的那份 fields 属于**别的**块（选项列表那一族），拿来当 cookie 名单就是读错人。
        val far = "loginWithCookies: {\n validate: (v) => v,\n" + "z".repeat(500) + "\n fields: [\"deep\"]\n}"
        assertEquals("窗口不生效：把 400 字符之外的 fields 当成了 cookie 名单", emptyList<String>(),
            DesktopSourceNames.cookieFieldsOf(far))
    }

    /** ② 随包三颗真源各自读得出名单，而读出来的每一枚名字**确实写在脚本里**（不自证）。 */
    @Test
    fun 随包那三颗源的名单与脚本对得上() {
        val assetDir = File(DesktopSourceTree.repoRoot, "app/src/main/assets")
        for (key in listOf("ehentai", "lanraragi", "mycomic")) {
            val script = File(assetDir, "sources/$key.js").readText()
            val fields = DesktopSourceNames.cookieFieldsOf(script)
            assertTrue("$key 有 loginWithCookies 那一形而名单读空了", fields.isNotEmpty())
            fields.forEach { name ->
                assertTrue("$key 的名单里那枚「$name」不在脚本里（解析造出了名字）", script.contains("\"$name\""))
            }
        }
        // 对照：没有那一形的源必须读成空表 —— 否则"只摆给得出的源"那条判据没有反面
        val none = File(assetDir, "sources/jm.js")
        if (none.isFile && !none.readText().contains("loginWithCookies")) {
            assertEquals("jm 没有那一形而解析给了它名单", emptyList<String>(),
                DesktopSourceNames.cookieFieldsOf(none.readText()))
        }
    }

    /** ③ 桌面 main 与 `:engine-probe` 里**一个脚本字段名都不许出现**（口径不许在桌面重述）。 */
    @Test
    fun 桌面不许重述脚本那份字段名单() {
        val names = listOf("ipb_member_id", "ipb_pass_hash", "igneous", "cf_clearance")
        val scope = DesktopSourceTree.desktopMainSources() + DesktopSourceTree.engineProbeSourceDir()
        val hits = names.flatMap { name ->
            scope.filter { DesktopSourceTree.codeText(it).contains(name) }.map { "${it.name} <- $name" }
        }
        // ⚠️ 名单里刻意不收 `apiKey`：lanraragi 那一形要的正是这枚，而画廊侧 Gelbooru 凭据面板
        // **自己也有**一个叫 apiKey 的东西（两本账不是一颗）。那条源要收的是"改了脚本没人管"，
        // 把同名标识符算成违例只会让人去改个无辜的名字（坑 ① 的近亲：尺子咬到自己）。
        assertEquals("桌面重述了字段名单（脚本改了没人回头改这里）：$hits", 0, hits.size)
    }

    /** ④ 只摆给得出的源：名单空的那一行连那一枚按钮都不该出现。 */
    @Test
    fun 名单空的那一行不摆凭据格() {
        val calls = mutableListOf<Pair<String, List<String>>>()
        runComposeUiTest {
            setContent {
                DesktopComicSourcesPane(
                    state(mapOf("ehentai" to listOf("ipb_a", "ipb_b"))),
                    login = { key, values ->
                        calls += key to values
                        DesktopCookieOutcome(true, "", listOf("ipb_a", "ipb_b"), mapOf("ipb_a" to 1, "ipb_b" to 1))
                    },
                )
            }
            assertEquals("只有一颗源有名单而那一枚按钮摆了两处", 1,
                onAllNodesWithText("填凭据").fetchSemanticsNodes().size)
            // 那句实话必须上屏，而且点名是哪一形 —— 钉的是那颗件产出的句子（改一个字不该红）
            assertTrue("那句实话不点名是哪一形：" + desktopWebLoginAbsentLine,
                desktopWebLoginAbsentLine.contains("内嵌") && desktopWebLoginAbsentLine.contains("手工粘贴"))
            onNodeWithText(desktopWebLoginAbsentLine).assertExists("源管理那一屏没念出桌面缺的是哪一形")
        }
        assertTrue("这一条用例不该被调登录", calls.isEmpty())
    }

    /** ⑤ 按一次恰好交一次，且**按名单次序**交出那几枚值（顺序是源认的口径）。 */
    @Test
    fun 摊开格子后按登录恰好交上去那几枚() {
        val calls = mutableListOf<Pair<String, List<String>>>()
        runComposeUiTest {
            setContent {
                DesktopComicSourcesPane(
                    state(mapOf("ehentai" to listOf("ipb_b", "ipb_a"))),
                    login = { key, values ->
                        calls += key to values
                        DesktopCookieOutcome(true, "", listOf("ipb_b", "ipb_a"), mapOf("ipb_b" to 1, "ipb_a" to 1))
                    },
                )
            }
            onNodeWithText("填凭据").performClick()
            // 名单给两枚 ⇒ 屏上该有恰好两个可输入的框（一枚都不许多，多了就是重述了口径）
            onNodeWithText("ipb_b").assertExists("名单里那一枚的名字没上屏")
            val editors = onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
            assertEquals("输入框枚数与名单枚数不配套", 2, editors)
            onAllNodes(hasSetTextAction())[0].performTextInput("VAL0")
            onAllNodes(hasSetTextAction())[1].performTextInput("VAL1")
            onNodeWithText("登录").performClick()
            assertEquals("按一次只该交一次", 1, calls.size)
            assertEquals("交出去的是被点开那颗源", "ehentai", calls[0].first)
            assertEquals(
                "值没按名单次序摊平（源按 fields[i] 取名，次序错了就是拿 B 当 A）",
                listOf("VAL0", "VAL1"),
                calls[0].second,
            )
        }
    }

    /** ⑥ 实参表那颗纯函数：缺位当空串，而名单外的多余键不许混进去。 */
    @Test
    fun 实参表按名单取而不收多余键() {
        assertEquals(
            "次序或缺位那一格不对",
            listOf("1", "", "3"),
            DesktopCookieLogin.payload(listOf("a", "b", "c"), mapOf("a" to "1", "c" to "3", "zzz" to "9")),
        )
    }

    /** ⑦ 回核那三档：源认了而盘上一枚没落，绝不许被念成"登录成功"（降级不静默）。 */
    @Test
    fun 回核三档各说各的而不许把空落点念成成功() {
        val ok = DesktopCookieOutcome(true, "源认了这批凭据", listOf("a"), mapOf("a" to 2))
        assertTrue("过了且落了盘：" + DesktopCookieLogin.screenNote(ok),
            DesktopCookieLogin.screenNote(ok).contains("已登录"))
        val hollow = DesktopCookieOutcome(true, "源认了这批凭据", listOf("a"), emptyMap())
        val note = DesktopCookieLogin.screenNote(hollow)
        assertFalse("盘上一枚都没落而屏上念了「已登录」（那就是假开关）", note.contains("已登录"))
        assertTrue("盘上一枚都没落却没说清重启带不上：" + note, note.contains("重启就带不上"))
        val failed = DesktopCookieOutcome(false, "该源的 validate 不是函数", emptyList(), emptyMap())
        assertTrue("没过时那句得把源的说法带出来：" + DesktopCookieLogin.screenNote(failed),
            DesktopCookieLogin.screenNote(failed).contains("validate 不是函数"))
        // 屏上那句不许带内部读数形状
        listOf(ok, hollow, failed).forEach {
            assertFalse("D_ 那种内部读数不许上屏", DesktopCookieLogin.screenNote(it).contains("D_"))
        }
    }

    /** ⑧ stdout 那一行从状态现取：每枚名字落了几颗都念得出（取证只认这一行）。 */
    @Test
    fun 读数那行念得出每枚落了几颗() {
        val reading = DesktopCookieLogin.reading(
            "ehentai",
            DesktopCookieOutcome(true, "", listOf("ipb_a", "ipb_b"), mapOf("ipb_a" to 2)),
        )
        assertTrue(reading.contains("D_手填登录 源=ehentai"))
        assertTrue("名单里第二枚没落盘却没念出来：$reading", reading.contains("ipb_b=0"))
        assertTrue("总数没念出来：$reading", reading.contains("计=2"))
    }

    /** ⑨ 罐的种子层：随请求发得出，而**永不进盘**（取证参数不许改用户的登录态）。 */
    @Test
    fun 种子并进本次装载而不写盘上那份() {
        val jar = DesktopCookieJar(store)
        jar.seedFromRequest(exh, listOf(cookie("ipb_pass_hash", "SEEDED")))
        jar.saveFromResponse(exh, listOf(cookie("real_from_server", "REAL")))
        val onDisk = store.readText(Charsets.UTF_8)
        assertTrue("站方回的那枚没落盘：" + onDisk, onDisk.contains("REAL"))
        assertFalse("取证种子被写进盘上那份（量具动了被量的东西）：" + onDisk, onDisk.contains("SEEDED"))
        assertFalse("种子的名字也漏进盘上了：" + onDisk, onDisk.contains("ipb_pass_hash"))
        val loaded = jar.loadForRequest(exh)
        assertEquals("种子与盘上那枚应当各算一枚", 2, loaded.size)
    }

    /** ⑩ 同名键种子赢：本次声明的那一枚压过盘上旧的（否则种进去等于没种）。 */
    @Test
    fun 同一枚键种子压过盘上那枚() {
        val jar = DesktopCookieJar(store)
        jar.saveFromResponse(exh, listOf(cookie("ipb_pass_hash", "FROM_SERVER")))
        jar.seedFromRequest(exh, listOf(cookie("ipb_pass_hash", "SEEDED")))
        val hit = jar.loadForRequest(exh).filter { it.name == "ipb_pass_hash" }
        assertEquals("同名键攒成两枚（请求头里两套真相）", 1, hit.size)
        assertEquals("种的那枚没赢", "SEEDED", hit.first().value)
    }

    /**
     * ⑮ 种子那一枚必须**真进罐**才算吃下（裸 host 与带前导点的那种写法都要吃得下）。
     *
     * ⚠️ 这一条是起窗那一趟抓回来的缺陷补的尺子：`exhentai.org` 在 OkHttp 那里解不成 URL（要带 scheme），
     * 而从前"解析成功"就被报成"吃下了" —— 屏上照念 `种子=2/2` 而请求一个字都没带。
     * 离屏那几条（⑨⑩）都从 `seedFromRequest(url, …)` 那一步起，正好绕过了"串 → url"那一环，
     * 所以这一条钉的是**串那一层**，不是再量一遍罐。
     */
    @Test
    fun 种子那枚必须真进罐才算吃下() {
        val jar = DesktopCookieJar(store)
        assertTrue("裸 host 那一枚没进罐（汇总那句就成了假数）", jar.seed("exhentai.org:ipb_member_id=42"))
        val served = jar.loadForRequest(exh)
        assertEquals("进了罐却带不出去", 1, served.size)
        assertEquals("值不是那一枚", "42", served.first().value)
        assertTrue("带前导点那种写法（脚本里就是这么写的）吃不下", jar.seed(".forums.e-hentai.org:igneous=9"))
        val forums = "https://forums.e-hentai.org/index.php".toHttpUrl()
        assertEquals("点号剥掉之后仍带不到那颗 host 上", 1, jar.loadForRequest(forums).size)
        assertFalse("形状错的（没有冒号那一段）不许算吃下", jar.seed("bad-shape-without-colon"))
        assertFalse("名为空的那一枚不许算吃下", jar.seed("exhentai.org:=v"))
    }

    /** ⑪ `--cookie=` 的判读三档：没给不打、读不出出声、给了报数。 */
    @Test
    fun 取证参数那一枚读得出几串而读不出那两档出声() {
        assertEquals("没给这枚参数时不该并任何东西", emptyList<String>(), ForensicFlags.cookies(emptyList()))
        val kept = ForensicFlags.cookies(
            listOf("--cookie=exhentai.org:ipb_a=1", "--cookie=exhentai.org:ipb_b=2"),
        )
        assertEquals("同一枚参数给两遍是这一族的常态（一站要粘几枚字段）", 2, kept.size)
        val captured = ByteArrayOutputStream()
        val saved = System.out
        val dropped = try {
            System.setOut(java.io.PrintStream(captured))
            ForensicFlags.cookies(listOf("--cookie=", "--cookie", "--cookie=exhentai.org:ipb_c=3"))
        } finally {
            System.setOut(saved)
        }
        assertEquals("两枚读不出的一枚也没并进去", 1, dropped.size)
        val text = captured.toString(Charsets.UTF_8)
        // ⚠️ 判的是"读不出那一档自己那句话"，不是"有没有念过 D_取证参数"：
        //    台架 P15 撤掉那一句时后者的对照（收到=N 可用=N）照样绿 —— 对照组把判据喂饱了（坑 ㉙ 的近亲）。
        assertTrue("空值与不带值没各自点名：" + text, text.contains("--cookie 那一串是空的"))
        assertTrue("报数那一句不见了：" + text, text.contains("收到=3 可用=1"))
    }

    /** ⑫ 那枚参数登记进已知名单（漏登记 = 起窗时被念成拼错，而参数其实是对的）。 */
    @Test
    fun 那枚参数在已知名单里() {
        assertEquals("没登记进 KNOWN：" + ForensicFlags.unknown(listOf("--cookie=exhentai.org:a=1")),
            emptyList<String>(), ForensicFlags.unknown(listOf("--cookie=exhentai.org:a=1")))
        // 对照：这条不是恒真的装饰 —— 没登记的参数照样得被报出来
        assertEquals("未知参数报不出来（对照组失效）", listOf("--bogus"), ForensicFlags.unknown(listOf("--bogus")))
    }

    /** ⑬ 接线：种子从宿主一路走到罐，且**宿主那颗文件没有把盘上那份建出来**。 */
    @Test
    fun 宿主吃到种子而那颗只并内存不建盘上那份() {
        val dataDir = File(root, "host-data").also { it.mkdirs() }
        val captured = ByteArrayOutputStream()
        val saved = System.out
        try {
            System.setOut(java.io.PrintStream(captured))
            DesktopJsHost(
                File(DesktopSourceTree.repoRoot, "app/src/main/assets"),
                dataDir,
                null,
                listOf("exhentai.org:seed_one=1", "bad-shape-without-colon"),
            ).close()
        } finally {
            System.setOut(saved)
        }
        val text = captured.toString(Charsets.UTF_8)
        assertTrue("种子那一行没念出吃进几枚：" + text, text.contains("种子=1/2"))
        assertTrue("形状不对那枚没被点名摘掉：" + text, text.contains("摘掉整枚"))
        assertFalse("起一趟就把盘上那份建了出来（没写就不该有那颗文件）", DesktopCookieJar.fileIn(dataDir).exists())
    }

    /** ⑭ 接线（扫源码钉）：宿主的罐、配置那枚必填参数、集成方那一步，三处各一处。 */
    @Test
    fun 种子那条线三处各接一次() {
        val host = DesktopSourceTree.codeText(DesktopSourceTree.engineProbeSource("DesktopJsHost.kt"))
        assertEquals("宿主没把种子并进罐", 1, host.split("applySeedCookies(seedCookies)").size - 1)
        // 只许加不许减：漫画域里有几处起宿主，种子就得进几处（现量两处：平时刻装载那一颗 + 登录那一趟）。
        val given = DesktopSourceTree.codeOccurrences(
            DesktopSourceTree.desktopUiSource("DesktopComicPane.kt"),
            "config.seedCookies",
        )
        assertTrue("种子没递满漫画域那几处起宿主的点（现量=$given，至少该有 2）", given >= 2)
        val appFile = File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/VeneraDesktop.kt")
        val app = DesktopSourceTree.codeText(appFile)
        assertEquals("集成方没把那枚参数递给配置（漏传的形状是参数递上而屏上照旧）", 1,
            app.split("seedCookies = seedCookies").size - 1)
        assertTrue("参数没登记在取证名单里", app.contains("ForensicFlags.cookies(args.asList())"))
        // 构建脚本那一跳（同一颗纪律第十一次）：判读层与消费点都接好了而 `-Pcookie=` 没拼进进程参数，
        // 屏上就永远拍不到那一格 —— 而"拍不到"会被读成"手填那一路坏了"（批 83.1 那一课的同族）。
        val build = java.io.File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts")
        val buildLines = DesktopSourceTree.codeLinesOf(build)
        assertEquals("构建脚本里那一枚 -P 属性没接进来（或在代码行里长成了两处）", 1,
            buildLines.count { it.contains("gradleProperty(\"cookie\")") })
        assertEquals("那一跳没拼成规范形状的那一枚参数", 1,
            buildLines.count { line -> line.contains("--cookie=") && line.contains("map") })
        assertEquals("分隔符那一层摊不成多枚（一站要粘几枚字段）", 1,
            buildLines.count { it.contains("?.split(\"|\")") })
    }
}
