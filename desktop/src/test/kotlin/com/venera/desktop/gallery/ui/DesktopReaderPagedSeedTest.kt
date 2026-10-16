package com.venera.desktop.gallery.ui

import com.venera.desktop.gallery.data.DesktopReaderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 第八十七批：阅读那一屏「翻页那一档」的**接线与口径**判据。
 *
 * 纯算式那一面在 [DesktopReaderPagingTest]（全部真跑）；这一颗管的是六件只有源码面量得到的事：
 * ① 模式那枚键**与对岸同轨**（键名与默认值都是从对岸源码文本现取的，不是抄在这里的字符串）；
 * ② 横向档真的用 `HorizontalPager` 而不是"看起来像翻页的滚动"；
 * ③ 两档各读各的那一枚状态（拿 listState 去滚 pager 那面墙是本仓记过的空操作那一族）；
 * ④ 今天没做的两档**不上屏**（摆一枚按不动的档位就是本仓最忌的假开关）；
 * ⑤ 模式那枚偏好的读写只走那一颗件；
 * ⑥ 取证参数那一跳三层都在（构建脚本 → 判读层 → 消费点）。
 *
 * 另有 ⑦ 那一组是 **B 路的接缝**：桌面不引对岸 `reader/` 那一族（A 路等点头），而档位表、
 * 归一别名表、跨页那两枚数、组首与钳位那三段算式**四处**都从对岸源码文本现取再比。
 * ⚠️ 这把尺子只覆盖"量过的这四处"：对岸新增第五处口径时它看不见，这正是 B 路记在
 * `gallery/data/DesktopReaderMode.kt` 与 `desktop/build.gradle.kts` 注释里的那笔代价。
 */
class DesktopReaderPagedSeedTest {

    private val readerFile = DesktopSourceTree.desktopUiSource("DesktopComicReader.kt")
    private val pagingFile = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopReaderPaging.kt")
    private val prefsFile = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicReaderPreferences.kt")
    private val appFile = DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt")
    private val integrationFile = DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt")
    private val flagsFile = DesktopSourceTree.desktopSourceOf("ForensicFlags.kt")

    // ── ① 那枚键与对岸同轨 ─────────────────────────────────────────────────

    @Test
    fun 模式那枚键名逐字回核对岸() {
        val against = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("data/prefs/VeneraPreferences.kt"))
        val key = Regex("""KEY_DEFAULT_READING_MODE = "([^"]+)"""").find(against)?.groupValues?.get(1)
        checkNotNull(key) { "对岸那枚键名的声明找不到了（对岸改了写法 ⇒ 桌面这一条要跟着重判，不许放宽）" }
        assertEquals("桌面这边写的键名与对岸不同串", 1, DesktopSourceTree.codeOccurrences(prefsFile, "\"$key\""))
    }

    /**
     * ② 默认档与对岸同一档 —— **两边各归一再比**，而不是比字面串。
     *
     * 从前这一条比的是"对岸那串原样出现在桌面那颗文件里"。行 164 那批把对岸默认从别名 `"VERTICAL"`
     * 换成表名 `"HORIZONTAL_LTR"` 之后，那种比法与 ③（档位的键与名字只有那张表一个出处，不许在表外写死串）
     * **互相咬死**：照 ② 就得写死串，照 ③ 就找不到那串。所以比的是**归一之后的那一档** ——
     * 那才是"两端起手同一档"这件行为本身，而 ③ 一个字不放宽。
     */
    @Test
    fun 模式那枚默认值逐字回核对岸() {
        val against = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("data/prefs/VeneraPreferences.kt"))
        val fallback = Regex("""getString\(KEY_DEFAULT_READING_MODE, "([^"]*)"\)""").find(against)?.groupValues?.get(1)
        checkNotNull(fallback) { "对岸那句默认值找不到了（写法变了就重判这一条）" }
        val code = DesktopSourceTree.codeText(prefsFile)
        val declared = Regex("""READING_MODE_FALLBACK = DesktopReaderMode\.([A-Z_]+)\.key""").find(code)?.groupValues?.get(1)
        checkNotNull(declared) { "桌面那颗默认值不再是「从那张表取」的形状（改了写法就重判这一条，不许放宽）" }
        assertEquals(
            "桌面这边写的默认档与对岸不是同一档（对岸那串=$fallback）",
            DesktopReaderMode.fromKey(fallback),
            DesktopReaderMode.valueOf(declared),
        )
        assertEquals(
            "默认那枚必须真的喂给读侧（声明了而没用上就是假轨道）",
            1,
            DesktopSourceTree.codeOccurrences(prefsFile, "store.getString(READING_MODE_KEY, READING_MODE_FALLBACK)"),
        )
    }

    @Test
    fun 桌面不许把档位写成字符串字面量() {
        // 档位的键与名字只有那张表一个出处：别处写死串就会与那张表漂（而屏上念的、盘上存的都取自它）。
        val literals = listOf("\"VERTICAL_CONTINUOUS\"", "\"HORIZONTAL_RTL\"", "\"HORIZONTAL_LTR\"", "\"DOUBLE_PAGE\"", "\"HORIZONTAL_CONTINUOUS\"")
        val tableFile = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopReaderMode.kt")
        val offenders = DesktopSourceTree.desktopMainSources().filter { file ->
            file != tableFile && literals.any { literal -> DesktopSourceTree.codeOccurrences(file, literal) > 0 }
        }
        assertEquals("档位名只许走那张表：${offenders.map { it.name }}", emptyList<File>(), offenders)
        // 对照组：那张表里这五串各在场（否则上面那条是恒真的空判式）
        literals.forEach { literal ->
            assertTrue("档位表里少了 $literal", DesktopSourceTree.codeOccurrences(tableFile, literal) >= 1)
        }
    }

    // ── ② 横向档用的是真的页翻件 ────────────────────────────────────────────

    @Test
    fun 横向那一档用的是Pager而不是滚动凑的() {
        assertEquals("页翻那面墙只摆一处", 1, DesktopSourceTree.codeOccurrences(readerFile, "HorizontalPager("))
        assertEquals("那一面墙的状态只造一次", 1, DesktopSourceTree.codeOccurrences(readerFile, "rememberPagerState("))
    }

    @Test
    fun 方向跟着档位而不是写死() {
        // 日漫那一档 reverseLayout=true，左至右 false —— 由那颗纯件算，屏上不许自己判 == RTL
        assertEquals(1, DesktopSourceTree.codeOccurrences(readerFile, "reverseLayout = desktopReaderReverses(mode)"))
        assertFalse("屏上不许重述 RTL 那一判", DesktopSourceTree.codeText(readerFile).contains("== DesktopReaderMode.HORIZONTAL_RTL"))
    }

    // ── ③ 两档各读各的那一枚状态 ───────────────────────────────────────────

    @Test
    fun 两档各读自己那一枚页号来源() {
        val text = DesktopSourceTree.codeText(readerFile)
        assertEquals("纵向那一路 1 处", 1, text.split("snapshotFlow { listState.firstVisibleItemIndex }").size - 1)
        assertEquals("横向那一路 1 处", 1, text.split("snapshotFlow { pagerState.settledPage }").size - 1)
    }

    @Test
    fun 换档不重落起点而那一页由盯梢那一笔现取() {
        // rounds §2.4：那颗 effect 的键里有 `mode`，所以换档会整个重跑 —— 重跑时若再落一次起点，
        // 人就被甩回第 1 页，而盯梢那一笔会跟着把第 1 页写进**两端共用**的 `comic_history`。
        val text = DesktopSourceTree.codeText(readerFile)
        assertEquals("落点要走接手那一颗件（只一处）", 1, text.split("desktopReaderCarriedPage(seedPage, shownPage)").size - 1)
        assertEquals("那一页由盯梢那一笔现取（不去读那面已经离开组合的墙）", 1,
            text.split(".onEach { shownPage = it }").size - 1)
        assertEquals("那一枚按话重置（换一本不许带着上一本的页号）", 1, text.split("mutableStateOf<Int?>(null)").size - 1)
        // 对照：种子那一跳还在（接手是叠在它之后，不是把它换掉）
        assertEquals("折起点那一颗没了", 1, text.split("desktopReaderStartPageIndex(reading.startPage, pageCount)").size - 1)
        val effectLine = DesktopSourceTree.codeLinesOf(readerFile)
            .firstOrNull { it.startsWith("LaunchedEffect(listState, pagerState, reading, state, mode)") }
        checkNotNull(effectLine) { "那颗 effect 的签名搬家了（这一条要跟着重判，不许放宽）" }
        assertFalse("起点那颗 effect 的键里不许有那一页（落点与盯梢会排成死循环）", effectLine.contains("shownPage"))
    }

    @Test
    fun 两档各滚自己那一面墙而次序还是先落后盯() {
        val lines = DesktopSourceTree.codeLinesOf(readerFile)
        val verticalApply = lines.indexOfFirst { it.contains("desktopApplyReaderStartPage(listState,") }
        val pagedApply = lines.indexOfFirst { it.contains("desktopApplyReaderStartPagePager(pagerState,") }
        val watch = lines.indexOfFirst { it.contains("desktopWatchReaderProgress(") }
        assertTrue("纵向那档没走落起点那颗件", verticalApply >= 0)
        assertTrue("横向那档没走它自己那一枚", pagedApply >= 0)
        assertTrue("盯梢排在了落起点之前（第一笔写的是没发生过的进度）", watch > verticalApply && watch > pagedApply)
    }

    @Test
    fun 落定那一步取的页号在屏上也念得出来() {
        // 页号那句必须由展示件念（藏在纯件里就没人负责上屏），而那句算式只在判据件里一处
        assertTrue(DesktopSourceTree.codeOccurrences(readerFile, "desktopReaderPageLabel(") >= 1)
        assertEquals("那句算式只许一处定义", 1, DesktopSourceTree.codeOccurrences(pagingFile, "internal fun desktopReaderPageLabel"))
    }

    // ── ④ 今天没做的两档不上屏 ─────────────────────────────────────────────

    @Test
    fun 没做的两档不许长出名字来() {
        val banned = listOf("对开·双页", "横向·连续")
        val hits = DesktopSourceTree.desktopUiSources().flatMap { file ->
            banned.filter { b -> DesktopSourceTree.codeOccurrences(file, b) > 0 }
        }
        assertEquals("摆上去就是按不动的那一枚：$hits", emptyList<String>(), hits)
        // 对照组：那两串在对岸的档位表里（否则这条是恒真的空判式）
        assertTrue(
            "对照组失效：对岸档位表读不到那两串",
            DesktopSourceTree.codeText(DesktopSourceTree.androidSource("reader/ComicPageSource.kt")).contains("对开·双页"),
        )
    }

    // ── ⑤ 模式那枚偏好：读、写、只吃形参 ───────────────────────────────────

    @Test
    fun 模式那一排点了就写而屏上立刻换档() {
        assertEquals("写口只在切换那一行被调", 1, DesktopSourceTree.codeOccurrences(readerFile, "setReadingMode("))
        assertEquals("屏上的档从偏好件现读", 1, DesktopSourceTree.codeOccurrences(readerFile, "readerPrefs.readingMode.collectAsState()"))
    }

    @Test
    fun 偏好件只有一颗而界面只吃形参() {
        assertEquals(
            "那颗件只声明一处，而构造是 internal（抬成 public 是被 config 的包名决定的，见那颗件的注释）",
            1,
            DesktopSourceTree.codeOccurrences(prefsFile, "class DesktopComicReaderPreferences internal constructor"),
        )
        // UI 不许自己 getInstance（那是"界面伸手取静态口"那一族，DesktopUiIsolationTest 也钉同一条）
        assertFalse("阅读那一屏不许自己取单例", DesktopSourceTree.codeText(readerFile).contains("DesktopComicReaderPreferences.getInstance"))
        assertEquals("集成方那一处建实例", 1, DesktopSourceTree.codeOccurrences(integrationFile, "DesktopComicReaderPreferences.getInstance"))
    }

    @Test
    fun 种子那一路只作初值而不写盘() {
        // 取证种子顶的是**本次装载**的那一档（批 83.1 同一条口径）；写盘会把用户的档挪走。
        assertTrue(
            "应用根那一路要把种子接进形参（声明 + 往下递 + 域 body 的参数，至少两处）",
            DesktopSourceTree.codeOccurrences(appFile, "startReaderMode") >= 2,
        )
        assertTrue(
            "种子没被折成初值那一颗件（留在 main() 里就没尺子 —— 批 30 那一课）",
            DesktopSourceTree.codeOccurrences(pagingFile, "internal fun desktopReaderModeOf") == 1,
        )
        // 应用根那条路上不许有写口
        assertFalse("应用根把种子写进盘了", DesktopSourceTree.codeText(appFile).contains("setReadingMode("))
    }

    @Test
    fun 种子顶过档必须念出来而不静默接管() {
        assertEquals("那句实话只在判据件里一处", 1, DesktopSourceTree.codeOccurrences(pagingFile, "internal fun desktopReaderModeSeedNote"))
        assertTrue("展示件没把那句念上屏", DesktopSourceTree.codeOccurrences(readerFile, "desktopReaderModeSeedNote(") >= 1)
    }

    // ── ⑥ 三层落点：构建脚本 → 判读层 → 消费点 ─────────────────────────────

    @Test
    fun 取证参数那一跳三层都在() {
        val build = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        assertEquals("构建脚本那一跳", 1, build.lines().count { it.contains("providers.gradleProperty(\"readMode\")") })
        assertTrue("那一跳递的是程序参数而不是系统属性", build.contains("--read-mode="))
        assertEquals("判读层那一颗", 1, DesktopSourceTree.codeOccurrences(flagsFile, "fun readMode("))
        assertTrue("集成方读了判读层那一颗", integrationFile.readText().contains("ForensicFlags.readMode("))
    }

    // ── ⑦ B 路的接缝：桌面不引那一族，而三处口径逐字回核对岸 ────────────────

    /**
     * 桌面自持一份同轨件的**边界**尺子：`reader/` 那一族一颗都不许进桌面编译面。
     *
     * 为什么这条必须存在：A 路（把 `reader/` srcDir 进来）被受保域里那行 UI 禁串挡住，
     * 而"没人 import"这件事没有尺子的话，下一个人顺手补一行就把它拉回甲路 —— 那时红的是
     * `app/` 那颗而不是这一颗，读起来像对岸坏了。判据红在自己家才有信息量。
     */
    @Test
    fun 桌面不引对岸阅读那一族的件() {
        val offenders = DesktopSourceTree.desktopMainSources()
            .filter { DesktopSourceTree.codeOccurrences(it, "import com.venera.compose.reader") > 0 }
        assertEquals("阅读那一族进桌面编译面了（那是等点头的 A 路）：${offenders.map { it.name }}", emptyList<File>(), offenders)
        val build = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        assertFalse(
            "构建脚本把 reader 目录 srcDir 进来了（A 路没点头）",
            build.contains("srcDir(\"../app/src/main/java/com/venera/compose/reader\")"),
        )
        // 对照组：那一族在对岸原位（不在原位时上面两条会一起变成恒真）
        listOf("ReaderInteractionPolicy.kt", "ComicPageSource.kt").forEach { name ->
            assertTrue("对岸那颗件不在原位：$name", File(DesktopSourceTree.repoRoot, "app/src/main/java/com/venera/compose/reader/$name").isFile)
        }
    }

    /**
     * 档位表（枚名 → 键 + 屏上念的名字）逐字回核对岸。
     *
     * 两侧的写法都是 `NAME("key", "label"…`，对岸多带 `icon` 与 `description` 两枚，
     * 所以取**前两枚**比：对岸加了第三枚桌面不知道，但桌面漏了一档、改了一串键或档名都会红在这里。
     */
    @Test
    fun 档位表逐字回核对岸() {
        val against = entryTable(DesktopSourceTree.codeText(DesktopSourceTree.androidSource("reader/ComicPageSource.kt")))
        val mine = entryTable(DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopReaderMode.kt")))
        assertTrue("对照组失效：对岸那张表里只读到 ${against.size} 档（写法变了要重判这一条）", against.size >= 5)
        assertEquals("桌面那张表与对岸不是同一张（枚名、键或档名漂了）", against, mine)
    }

    /** 归一别名表（串 → 落到哪一档）逐字回核对岸：读档时两边必须把同一个串认成同一档。 */
    @Test
    fun 归一别名表逐字回核对岸() {
        val against = aliasTable(DesktopSourceTree.codeText(DesktopSourceTree.androidSource("reader/ComicPageSource.kt")))
        val mine = aliasTable(DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopReaderMode.kt")))
        assertTrue("对照组失效：对岸那颗归一件里只读到 ${against.size} 枚别名（写法变了要重判这一条）", against.size >= 10)
        assertEquals("两表的别名口径漂了（同一个串在两端认成不同档）", against, mine)
    }

    /** 跨几页那两枚数从对岸现取再比：这一处正是"没人会同时改"的那种数（双页一组两页）。 */
    @Test
    fun 步长那两枚数回核对岸() {
        val against = stepPair(DesktopSourceTree.codeText(DesktopSourceTree.androidSource("reader/ReaderInteractionPolicy.kt")))
        val mine = stepPair(DesktopSourceTree.codeText(pagingFile))
        checkNotNull(against) { "对岸那句步长找不到了（写法变了要重判这一条，不许放宽）" }
        assertEquals("跨页那两枚数与对岸不同：对岸 ${against} 桌面 $mine", against, mine)
        assertEquals("步长该是「双页 2、其余 1」这个形状（现量自对岸），实际 $mine", Pair("2", "1"), mine)
    }

    /** 组首对齐与两处钳位那三段算式逐字同串（参数名两端刻意取同名，才比得出串来）。 */
    @Test
    fun 组首与钳位那三处算式逐字回核对岸() {
        val against = DesktopSourceTree.androidSource("reader/ReaderInteractionPolicy.kt")
        listOf("takeIf { it <= lastIndex }", "takeIf { it >= 0 }", "page - page % step").forEach { token ->
            assertEquals("对岸那份里这句不是 1 处：$token", 1, DesktopSourceTree.codeOccurrences(against, token))
            assertEquals("桌面这份里这句不是 1 处：$token", 1, DesktopSourceTree.codeOccurrences(pagingFile, token))
        }
    }

    /** 屏上那一排念的是表里的档名（不写死串）；这一条把"名字取自表"钉在接线层。 */
    @Test
    fun 那一排念的是表里的档名而不是另写一遍() {
        assertEquals("那一排的 Text 只许取自表里的 label", 1, DesktopSourceTree.codeOccurrences(readerFile, "choice.label"))
        // 对照：整屏里没有档位名串（与 ④ 那颗共用一张表，这里只钉"念的是表"）
        assertTrue("屏上没摆那一排（choice 都没出现）", DesktopSourceTree.codeOccurrences(readerFile, "DESKTOP_READER_MODE_CHOICES.forEach") == 1)
    }

    // ── ⑧ 对外文档那一条同轨（改一处必须同步另一处，本仓口径）───────────────

    @Test
    fun 两份README的翻页那一条同轨() {
        val zh = File(DesktopSourceTree.repoRoot, "README.md").readText()
        val en = File(DesktopSourceTree.repoRoot, "README.en.md").readText()
        assertEquals("中文那份还写着只有纵向那一档", 0, Regex("阅读那一屏的纵向连续流").findAll(zh).count())
        assertEquals("英文那份还写着只有纵向那一档", 0, Regex("the vertical reading screen").findAll(en).count())
        assertTrue("中文那份没念整页翻那一档", zh.contains("整页翻那一档"))
        assertTrue("英文那份没念整页翻那一档", en.contains("a real paging tier"))
        // 对照：两份都还留着"键位没接到这一屏"那一条 —— 只改能力句而不改缺口句就是半份文档
        assertTrue("中文那份的键位缺口没了", zh.contains("阅读那一屏的键位"))
        assertTrue("英文那份的键位缺口没了", en.contains("Keys on the reading screen"))
    }

    /** 对岸那张表：枚名 → 键 + 档名（只取前两枚字段）。 */
    private fun entryTable(text: String): Map<String, Pair<String, String>> =
        Regex("([A-Z][A-Z_]+)\\(\"([^\"]+)\", \"([^\"]+)\"").findAll(text)
            .associate { it.groupValues[1] to (it.groupValues[2] to it.groupValues[3]) }

    /** 那颗归一件的别名表：串 → 落到的枚名（`else ->` 之后那一条不算，它是兜底不是别名）。 */
    private fun aliasTable(text: String): Map<String, String> {
        val body = text.substringAfter("fun fromKey").substringAfter("when").substringAfter("{").substringBefore("else ->")
        val table = mutableMapOf<String, String>()
        body.lines().forEach { line ->
            val arrow = line.indexOf("->")
            if (arrow >= 0) {
                val target = line.substring(arrow + 2).trim()
                Regex("\"([^\"]+)\"").findAll(line.substring(0, arrow)).forEach { table[it.groupValues[1]] = target }
            }
        }
        return table
    }

    /** 那句步长里的两枚数（跨页步长、其余档的步长）。 */
    private fun stepPair(text: String): Pair<String, String>? =
        Regex("== \\w+\\.DOUBLE_PAGE\\) (\\d+) else (\\d+)").find(text)
            ?.let { it.groupValues[1] to it.groupValues[2] }
}
