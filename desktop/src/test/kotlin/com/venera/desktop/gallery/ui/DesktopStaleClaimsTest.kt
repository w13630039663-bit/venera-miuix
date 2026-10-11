package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryRanking
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十三批 批 7 · **过期说明**的机械尺子。
 *
 * ## 这一批评的是"话"，而话会跑得比代码快
 *
 * 本仓记过一整轮「实现了但零调用点」，而它的**镜像**更阴：界面或注释里写着"桌面还没有 X"，
 * 而 X 早已落地（手册 §22 那条撤掉的句子活了四批）。规矩是 §22.3 那句的**反向应用**：
 * **接上一样能力之后，要回查每一屏那句说明还成不成立。**
 * 这五句就是这么量出来的（子代理盘面 + 自己逐条 grep 复核，见 `docs/rounds/…plan…` §12）。
 *
 * ## ⚠️ 这一颗判据**故意扫全文**，不跳注释
 *
 * 本仓所有扫描尺子都走 `DesktopSourceTree.codeText`（跳注释行），因为"不许出现 X"那类判式
 * 会把口径注释里的说明当成违例。但**过期说明九成住在注释里** —— 那把尺子在这里恰好是瞎的：
 * 五条禁串用 `codeText` 扫，全部恒绿。所以这里直接读 `File.readText()`，
 * 而对照组 ⑤ 就是钉"这台机器真看得见注释行"。
 *
 * ## 每条禁串都配一条"为什么它已经不成立"的正面证据
 *
 * 光禁一句措辞，等于把后来的代码钉死在今天的形状上：哪天那枚能力被撤了，那句说明就该**回来**，
 * 而禁串只会红在措辞上。所以每条都配一颗对照 —— **撤能力会先红在对照上**，
 * 读的人立刻知道"这句禁串该跟着撤"，而不是被要求改注释。
 */
class DesktopStaleClaimsTest {

    private val uiDir = File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/ui")

    /** 整个 `gallery/ui/` 的**全文**（含注释）：文件名 → 内容。 */
    private fun fullText(): List<Pair<String, String>> =
        uiDir.listFiles()?.filter { it.extension == "kt" }?.map { it.name to it.readText() }?.sortedBy { it.first }
            ?: error("读不到桌面界面目录：${uiDir.path}")

    /** 某句禁串还活在哪些文件的哪一行（一次列全，不短路）。 */
    private fun hits(banned: String): List<String> =
        fullText().flatMap { (name, text) ->
            text.lines().filter { it.contains(banned) }.map { "$name: ${it.trim().take(90)}" }
        }

    /** ① 屏上那句音量键说明不再说"只能点按钮"——← / → 今天也能翻页。 */
    @Test
    fun 音量键那句不再把翻页说成只有按钮() {
        val row = DesktopGallerySettingsSpec.notOnScreen.first { it.pref == "galleryVolumeKeyTurn" }
        assertEquals("这一屏还写着「只能点那两枚按钮」", 0, hits("桌面上翻页请点浮层里那两枚按钮").size)
        assertTrue(
            "撤掉旧那句之后得说清今天怎么翻（只删不说 = 用户读到一句没头没尾的话）：" + row.why,
            row.why.contains("←") && row.why.contains("→"),
        )
        // 对照：这句被禁**是因为键真接上了**，不是因为"不想提按钮"。哪天键撤了，对照会先红。
        val keys = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopKeyNavigation.kt"))
        assertTrue("对照组失效：← / → 没被认成翻页步", keys.contains("Key.DirectionLeft") && keys.contains("Key.DirectionRight"))
        val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
        assertTrue("对照组失效：那三枚键没接到浮层上", app.contains(".onKeyEvent {"))
    }

    /** ② 「关注在桌面没有入口」那句已经活了太久而入口就在浮层里。 */
    @Test
    fun 关注入口那句已下架() {
        assertEquals("还有文件写着「关注这个动作在桌面还没有入口」", 0, hits("关注这个动作在桌面还没有入口").size)
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        assertTrue("对照组失效：浮层里那枚关注字没了（那上面那句就该回来）", panel.contains("desktopFollowLabel("))
    }

    /** ③ 「与发现那一屏同一档缺口」——那一屏的续页第十批 ③ 就上线了，参照物不存在。 */
    @Test
    fun 最新那一屏不再拿发现屏当缺席的挡箭牌() {
        assertEquals("还有文件写着「与发现那一屏同一档缺口」", 0, hits("与发现那一屏同一档缺口").size)
        val forYou = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopForYouPane.kt"))
        assertTrue("对照组失效：发现那一屏的续页持有件没读到（那句若被撤了就另说，但别拿它当参照）", forYou.contains("DesktopGalleryBatch"))
        // 撤掉错的参照物之后，那一屏**仍然要**明说自己的边界（不许只删不说）。
        // 批 23 接上续页，旧那句「只给第一轮的第一页」退役，换上今天仍然成立的那一句。
        val latest = File(uiDir, "DesktopLatestPane.kt").readText()
        assertTrue("最新那一屏不再明说自己那一笔的边界（给一整页）", latest.contains("一整页"))
    }

    /**
     * ④ 搜索那一屏关于"历史"的两句都不成立：三枚判据在编译面、落盘件早就接好。
     *
     * ⚠️ 这与首页那一行「历史」（浏览过哪张卡）**不是一件事** —— 那一句仍然成立，
     * 判据 ⑥ 反向钉着它不许被顺手撤掉。
     */
    @Test
    fun 搜索屏那两句关于落盘件的假话下架() {
        assertEquals("还有文件写着「得先给桌面一份落盘件」", 0, hits("先给桌面一份落盘件").size)
        assertEquals("还有文件点名那份并不存在的 `GallerySearchHistory`", 0, hits("那份 `GallerySearchHistory`").size)
        // 那颗名字到底存不存在：全仓 grep 一次。注释点名一颗不存在的件，比没注释更坏（手册 §18 那条坑）。
        val named = DesktopSourceTree.androidMainSources()
            .flatMap { file -> DesktopSourceTree.codeLinesOf(file).filter { it.contains("class GallerySearchHistory") } }
        assertEquals("对照组失效：`GallerySearchHistory` 原来是真东西（那句就该留着）", 0, named.size)
        val ports = DesktopSourceTree.codeText(
            java.io.File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/data/DesktopGalleryPorts.kt"),
        )
        assertTrue("对照组失效：桌面那颗具名键值件没接线", ports.contains("stores = DesktopStoreFactory("))
        val face = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/domain/GallerySearch.kt"))
        assertTrue(
            "对照组失效：那三枚判据不在共享面上（在桌面编译面外）",
            listOf("fun encodeGallerySearchHistory(", "fun decodeGallerySearchHistory(", "fun pushGallerySearchHistory(")
                .all { face.contains(it) },
        )
    }

    /**
     * ⑤ 这台机器真看得见注释行（不证的话，上面那些禁串全是恒绿的空判式）。
     *
     * ⚠️ 这一条钉过两句**具体的散文**，两次都红了而机器其实好得很：第一次是批 14 之后那句被改写，
     * 第二次是第十八批并行会话重写画师那一屏的注释。那是 §0 坑 ㉖ 那一族的第三种宿主 ——
     * 前两次钉的是代码形状，这次钉死的是**别人家注释里的一句话**。
     * ⇒ 现在钉**机制**：从被扫的那一片语料里现取一行注释，拿它自己回找。
     * 谁把 `fullText` 换成 `codeText`，这条会红；而它不依赖任何一句具体的话长什么样。
     */
    @Test
    fun 扫描器看得见注释() {
        val needle = fullText()
            .flatMap { (_, text) -> text.lines() }
            .map { it.trim() }
            .firstOrNull { it.startsWith("//") && it.length > 30 }
            ?.take(40)
            ?: error("语料里一行像样的注释都没有 ⇒ 被扫的那一片目录是空的，这条判据没有对象")
        assertTrue(
            "对照组失效：全文扫描抓不到现取的那行注释（$needle…）⇒ 上面那些禁串是恒真的空判式",
            hits(needle).isNotEmpty(),
        )
    }

    /**
     * ⑥ 反向钉：**如果**又长出一行未实现的，它必须逐颗点名差的是哪一层，措辞不许是"敬请期待"那一类。
     *
     * 这一枚从前钉的是「历史」那一行「未实现」**仍然成立**（批 85 之后剩一枚）——
     * 第八十六批把那一行也点亮了，于是这一族今天没有对象。留着的理由与手册里那条**定点豁免**同一条：
     * 桌面首页允许长成"有一行还没做"的形状（与 Android 设置页"灰行连标题一起撤"相反），
     * 所以那一族的判式要留在这里，而不是等哪天真长出一行时重新现攒一句。
     * ⚠️ 枚数的账由判据 ⑦ 与 `DesktopGalleryHistorySeedTest` ② 各钉一层：这一枚只管"长出来时长得对不对"。
     */
    @Test
    fun 还成立的缺席点名逐行不许少() {
        val absent = desktopGalleryHomeRows().filter { it.content == null }
        absent.forEach { row ->
            assertTrue("「${row.title}」那一行没说要差哪一层", row.reason?.isNotBlank() == true)
            listOf("暂不支持", "敬请期待", "开发中", "即将上线").forEach { word ->
                assertTrue("「${row.title}」那句是措辞不是事实：${row.reason}", row.reason?.contains(word) == false)
            }
        }
        // 三枚对照：先后点亮的那三行都不该又变回未实现（各自的账在对应那颗判据里）。
        listOf("排行榜", "下载", "历史").forEach { title ->
            assertTrue("「$title」那一行又变回未实现了", absent.none { it.title == title })
        }
    }

    /**
     * ⑦ 「四行未实现」这一数也会过期 —— 「最新」第十批 ⑤ 点亮后剩三行，「排行榜」批 28 点亮后剩二行，
     * 「下载」批 85 点亮后剩一行。
     *
     * 数的错比措辞的错更容易漏：没人会去 grep 一个数字。所以这里不比对字符串，
     * 而是**现数**并把数钉进文案（`rows` 的枚数变了，这句会红在对照上，逼着一起改）。
     */
    @Test
    fun 未实现的行数说明跟着账走() {
        assertEquals("还有文件写着「四行未实现」", 0, hits("四行未实现").size)
        assertEquals("还有文件写着「二行未实现」（批 85 之后剩一行）", 0, hits("二行未实现").size)
        assertEquals("还有文件写着「一行未实现」（批 86 之后剩零行）", 0, hits("一行未实现").size)
        val rows = desktopGalleryHomeRows()
        val absent = rows.count { it.content == null }
        assertEquals("今天六行全都有内容件（历史那一行批 86 转正），对不上就先改账再改判据", 0, absent)
        val doc = File(uiDir, "DesktopGalleryHomeSections.kt").readText()
        // 把现数折成中文数词再比：哪天哪一行点亮了，这里会要求文案跟着改数，
        // 而不是让人去 grep 一个没人会怀疑的数字。
        assertTrue(
            "那一颗文件不再说未实现的有几行（只删不说 = 下一批又要重新量）",
            doc.contains(数词[absent] + "行未实现"),
        )
    }

    /**
     * ⑧ 「这一屏今天不存查询历史」那句到批 9 就不成立了（三枚判据 + 落盘件 + 屏上那一排都接上了）。
     *
     * ⚠️ 这与首页那一行「历史」（浏览过哪张卡）**仍然不是一件事** —— 那一句由判据 ⑥ 反向钉着，别撤。
     */
    @Test
    fun 搜索屏那句不存历史的话下架() {
        assertEquals("还有文件写着「这一屏今天不存查询历史」", 0, hits("这一屏今天**不**存查询历史").size)
        val pane = File(uiDir, "DesktopGallerySearchPane.kt").readText()
        assertTrue("对照组失效：屏上那一排没了（那句若成立就该回来）", pane.contains("DesktopHistoryRow("))
        val holder = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopSearchHistory.kt"))
        assertTrue("对照组失效：读盘那颗没了", holder.contains("fun desktopHistoryRead("))
    }

    /**
     * ⑨ 「补全不做成打字时自动弹，因为桌面没有那套防抖状态机」那句是假话（批 11 量出来的）。
     *
     * 候选那一排**本来就跟着输入框一起换**：那颗 effect 的 key 里有 `query`，每敲一键重跑一笔、
     * 上一笔由 effect 自己作废 —— 对岸靠 `suggestJob?.cancel()`，两端同一形状，谁都没有防抖延迟。
     * 那句把"今天就是这么跑的"写成"桌面做不到"，属于 §22.3 那族最阴的：**能力在，话把它说没了**。
     */
    @Test
    fun 补全那句做不到的话下架() {
        assertEquals("还有文件写着那句防抖状态机", 0, hits("是因为桌面没有那套防抖状态机").size)
        assertEquals("还有文件写着「避免每敲一键就出一笔请求」", 0, hits("避免每敲一键就出一笔请求").size)
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGallerySearchPane.kt"))
        // 对照：这句被禁**是因为补全真按每一次输入重跑**，撤掉那颗 effect 会先红在这里。
        assertTrue(
            "对照组失效：补全那颗 effect 不再随输入重跑（那句若成立就该回来）",
            pane.contains("LaunchedEffect(ports, sourceIndex, query)") && pane.contains("term.length < 2"),
        )
        val holder = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopSearchSuggestion.kt"))
        assertTrue("对照组失效：候选不再走那颗共享复检件", holder.contains("refineGalleryTagSuggestions("))
    }

    /**
     * ⑩ 「还没有本地历史存储，读不到浏览记录」那句是假话（批 14 量出来的）。
     *
     * 存储**在**：`data/db` 整个目录进桌面编译面，而桌面自己就有一颗 `HistoryDaoTest` 在读写那张表。
     * 真的那一半是"**没有会往里写一笔的界面**" —— 批 14 当时那样量，因为那一屏还不存在。
     * ⚠️ 那半句到第八十一批也过期了（桌面今天有阅读那一屏，只是它只读不写），所以改判成
     * "差的是那笔没写的账"，并由下面那条 ⑬ 钉住"不许再拿某一屏存不存在当差的那一层"。
     * 两句在屏上读起来像同一句（"这功能没做"），可它们差的是**完全不同的下一批**：
     * 前者要接存储，后者要先有一屏会写它的界面。
     */
    @Test
    fun 历史那行不再说没有存储() {
        assertEquals("还有文件写着「还没有本地历史存储」", 0, hits("还没有本地历史存储").size)
        // ⚠️ 第八十六批把这一枚的后半撤了：那一行已经点亮（`reason` 恒 null），
        // "那一行要点名差的是哪一层"这件事从此没有对象 —— 它搬去两处：
        // 「转正了没」由 `DesktopGalleryHistorySeedTest` ① 钉，「旧句子不许活回来」由那一颗的 ③ 钉，
        // 「再长出一行未实现的得点名差哪一层」由这一颗文件的 ⑥ 与 `DesktopGalleryHomeSectionsTest` ④ 管。
        // 这里留下的是**只有这一枚知道**的那两半：那句被禁是因为存储今天在桌面可达 —— 撤了对照会先红。
        val build = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        assertTrue(
            "对照组失效：`data/db` 不在桌面编译面上了（那存储就真算没有，那句该回来）",
            build.contains("srcDir(\"../app/src/main/java/com/venera/compose/data/db\")"),
        )
        assertTrue(
            "对照组失效：桌面那颗 `HistoryDao` 的用例没了",
            File(DesktopSourceTree.repoRoot, "desktop/src/test/kotlin/com/venera/desktop/db/HistoryDaoTest.kt").isFile,
        )
    }

    /**
     * ⑪ 「这两颗 pane 此刻还没有消费点，接线归集成」是 S2-B 那批留下的话，今天不成立。
     *
     * 日榜那一屏今天真在出图（批 13 那帧屏上就是封面墙），而"没有消费点"这句还住在类注释里 ——
     * 下一批读到这句会以为那一屏还没接线，于是去"接线"，实际会把已经在跑的东西再接一遍。
     */
    @Test
    fun 日榜那句没有消费点的话下架() {
        assertEquals("还有文件写着「此刻还没有消费点」", 0, hits("此刻还没有消费点").size)
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopDailyPane.kt"))
        // 对照：这句被禁**是因为那一屏真把墙画出来了**（撤掉墙或撤掉接线会先红在这里）。
        assertTrue("对照组失效：日榜那一屏不再画封面墙（那句若成立就该回来）", pane.contains("DesktopGalleryPostWall("))
        val wired = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryHomeSections.kt"))
        // ⚠️ 这一枚只钉到**被调的那颗件**（括号前）：扰动 V3 把接线换成等价写法 `DesktopDailyPane(ports = ports)`
        // 时它误红过一次 —— 那是 §0 坑 ㉖ 那一族，退一层之后"撤掉接线"仍会红（那颗件名整个消失），
        // 而"换一种传参写法"不再误伤。
        assertTrue("对照组失效：日榜那一屏没被接进主区", wired.contains("DesktopDailyPane("))
    }

    /**
     * ⑫ 「分段控件三档：只有「推荐」今天真的有东西」到第十批 ⑤ 就不成立了（「最新」点亮了）。
     *
     * 这一条不靠禁串赢：对照是**真跑** `desktopGallerySegments()` —— 数出来哪几档带着原因，
     * 屏上就是哪几档点不动。那句注释写的是"两档"，今天只剩一档。
     */
    @Test
    fun 段头那句只有一档有东西的话下架() {
        assertEquals("还有文件写着「只有「推荐」今天真的有东西」", 0, hits("只有「推荐」今天真的有东西").size)
        val segments = desktopGallerySegments().associate { it.label to it.reason }
        assertEquals(
            "今天带原因的档位变了（那句注释与这段读数要一起改）：$segments",
            emptyList<String>(),
            segments.filterValues { it != null }.keys.toList(),
        )
        // 「最新」与「排行」两档都必须有内容件（reason 为 null 才算点亮）。
        assertEquals("「最新」那一档又变回未实现了（那注释该回来）", null, segments["最新"])
        assertEquals("「排行」那一档又变回未实现了（批 28 那一屏的账要回滚就先改这里）", null, segments["排行"])
    }

    /**
     * ⑬ 收藏那一屏的兜底话还在说「桌面今天没有设置界面可改它」—— 第七批就有设置域、
     * 批 6 起分级档与屏蔽规则两道写口都真在那一屏上（`addRule` / `deleteRule` / `setNsfwMaskMode`）。
     *
     * ⚠️ 这一句是**直接上屏**的（那面墙被挡空时的 `ifBlank` 兜底），不是注释里的话。
     * 而它劝用户的话正好相反："改不了" ⇒ 用户就不去改了，其实那一屏改得动。
     */
    @Test
    fun 收藏那一屏不再说改不了() {
        assertEquals("还有文件写着「桌面今天没有设置界面可改它」", 0, hits("桌面今天没有设置界面可改它").size)
        val settings = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGallerySettingsPane.kt"))
        // 对照：这句被禁**是因为那一屏真有三道写口** —— 撤掉任一道会先红在这里（那句若重新成立就该回来）。
        // 只钉被调的那颗件名（§0 坑 ㉖）：参数怎么传、包在哪个 if 里都不许成为误红的理由。
        listOf("setNsfwMaskMode(", "addRule(", "deleteRule(").forEach { seam ->
            assertTrue("对照组失效：设置那一屏没调 $seam（那一句说不定又成立了）", settings.contains(seam))
        }
        val pane = File(uiDir, "DesktopGalleryFavoritesPane.kt").readText()
        // 不许只删不说：那句兜底话仍然要在，而它得把"去哪儿改"指出来。
        assertTrue("兜底那句不见了", pane.contains("分级与屏蔽规则把这一批全挡下了"))
        assertTrue("删掉旧那句之后没指出去哪儿改", pane.contains("分级与屏蔽规则把这一批全挡下了（") && pane.contains("设置"))
    }

    /**
     * ⑭ 两句注释把一颗**已经不存在的件**当依据（`DesktopGalleryDomain.notWiredReason`）。
     *
     * 第九批最后一枚坦白页转真时，那套域级缺席说明随缺席一起消失了，可两处注释还指着它的名字说
     * "rail 上那两枚域按键已经在用"。注释点名一颗不存在的件比没注释更坏（手册 §18 那条坑 ⑥ 的镜像），
     * 而这里还多一层：**它拿"别人已经在用"给自己的形状当理由，那个别人已经不在了。**
     */
    @Test
    fun 注释不许拿一颗不存在的件当依据() {
        assertEquals("还有文件引用 `notWiredReason`", 0, hits("notWiredReason").size)
        // 对照：今天七枚域全部 absence 为 null ⇒ "rail 上还有两枚坦白页在用同一套办法"必然是假话。
        // 这条是真跑枚举，不是扫字面 —— 哪天又添一枚坦白页，这里会红着提醒那句话可以回来了。
        assertEquals(
            "rail 上又出现进不去的域了（那套缺席说明的引用方式要重新谈）：" +
                DesktopDomain.entries.filter { !it.isWired }.map { it.title },
            0,
            DesktopDomain.entries.count { !it.isWired },
        )
        assertTrue(
            "对照组失效：缺席机制那颗类型没了（那今天连'域级坦白页'这个概念都不存在，注释更不该引用它）",
            DesktopDomain.entries.first().absence == null,
        )
    }

    /** ⑮ 画师那一域的注释还说"头像不主动现取（只读本机档）"—— 第十批 ② 就改成按视口现取了。 */
    @Test
    fun 画师那一域不再说不现取头像() {
        assertEquals("还有文件写着「头像不主动现取」", 0, hits("头像不主动现取").size)
        val page = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopArtistsPage.kt"))
        // 对照：这句被禁**是因为那一屏真在按视口问头像**（撤掉现取会先红在这里）。
        assertTrue("对照组失效：画师那一屏不再算视口可见集", page.contains("desktopVisibleProbeTargets("))
    }

    /** ⑯ 发现那一域的注释还说它缺"续页"—— 那一屏的「下一页」第十批 ③ 与批 8 就接上了。 */
    @Test
    fun 发现那一域不再说缺续页() {
        assertEquals("还有文件把'续页'列为发现域缺的那一层", 0, hits("续页（那套状态在").size)
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopForYouPane.kt"))
        // 对照：这句被禁**是因为那一屏真有续页那一档相位**（撤掉续页会先红在这里）。件名级锚点，理由同 ⑬。
        // 批 23 把那枚相位收成了整个目录共享的一颗（从前是发现、单人页各抄一遍），所以锚点跟着换名。
        assertTrue("对照组失效：发现那一屏的续页相位没了", pane.contains("DesktopGalleryMorePhase"))
        // 另一半仍然成立，不许见"缺"就撤：沿用上次那一批确实还缺（那颗件吃 Context，在排除清单里）。
        val build = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        assertTrue("对照组失效：`GalleryForYouCache` 又进桌面编译面了（那句缺席该撤）", build.contains("\"GalleryForYouCache.kt\","))
    }

    /**
     * ⑰ 设置台账那行从前断言"桌面今天一律让它动"—— 这一句写下来时**从来没有量过**。
     *
     * 批 16 先把它从屏上撤下来（两种行为都不说），批 17 用四环链量准了：
     * 桌面走 coil3 默认 ImageLoader（共享那颗 `GalleryImageLoader` 在排除清单里）→
     * `javap` 读 `coil-core-jvm`：非 Android 那侧默认注册的就是 `SkiaImageDecoder$Factory` →
     * 它调 `org.jetbrains.skia.Image.makeFromEncoded(byte[])` 而**不是** `Codec` →
     * `DesktopAnimatedDecodeProbeTest` 本机真跑：自造两帧 GIF（`Codec帧数=2`）经那颗入口交出
     * 一张 4×4 静态图、像素 == 第 0 帧。⇒ 屏上今天说的是**停在第一帧**（量过的实话）。
     * ⚠️ 这一条不许退回"一律让它动"，也不许在没重量之前写成别的（比如"动着放"或"画不出来"）。
     */
    @Test
    fun 动图那一档不再断言它会动() {
        assertEquals("还有文件写着「桌面今天一律让它动」", 0, hits("桌面今天一律让它动").size)
        // 反方向那句也不许写：批 17 量的是"解得出来、只解首帧"，写成"画不出来"是另一句假话。
        assertEquals("还有文件写着「整张画不出来」", 0, hits("整张画不出来").size)
        val row = DesktopGallerySettingsSpec.editable.first { it.pref == "galleryAnimated" }
        assertTrue("撤掉那句之后这一行成了空句：${row.caption}", row.caption.isNotBlank())
        // 屏上那句现在说的是量出来的那一档行为。这里只钉"第一帧"这一枚**承载事实的词**
        // （同 ① 钉 ← 与 → 一个口径），而问句那半已经不含它 —— 扰动 P4 就是拿"去掉答案、只留问题"
        // 验过这一枚会红的。
        assertTrue("那一行不再点名今天的行为是哪一帧：${row.caption}", row.caption.contains("第一帧"))
        // 对照（证据件）：量出这句话的那颗探针还在，而且**代码行里**量的就是桌面用的那颗入口。
        // ⚠️ 必须走 codeText：扰动 P3 把探针换成别的解码入口之后，只有类注释里还留着那个名字，
        //    用 readText 扫就被注释满足了 ⇒ 对照恒真（坑 ㉑ 的那把尺子这次咬的是我自己的对照）。
        val probePath = "desktop/src/test/kotlin/com/venera/desktop/gallery/data/DesktopAnimatedDecodeProbeTest.kt"
        val probeFile = File(DesktopSourceTree.repoRoot, probePath)
        assertTrue("对照组失效：量首帧那颗探针文件没了 ⇒ 屏上那句又成了没量过的断言", probeFile.isFile)
        val probeCode = DesktopSourceTree.codeText(probeFile)
        assertTrue(
            "对照组失效：探针不再走 `Image.makeFromEncoded`（那屏上那句的依据换了，得重量）",
            probeCode.contains("Image.makeFromEncoded"),
        )
        assertTrue("对照组失效：探针不再数帧数（夹具是不是两帧就没人管了）", probeCode.contains("frameCount"))
        // 对照（读侧）：登记仍在 —— 撤掉它就不是"这一档能改"而是"这一档不存在"，那是另一件事。
        // ⚠️ 这一段从前是**反向钉**（"设置那一屏不许出现写口"），批 52 把写口接上了 ⇒ 按那条对照自己
        //   留的话（"接上那天屏上那句要按新行为重量一遍"）翻成正向钉：写口必须真在，
        //   而那句"不能在这儿改"整树不许再出现 —— 它就是本文件管的那一族假话。
        val settings = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGallerySettingsPane.kt"))
        assertTrue("这一屏没调动图写口 ⇒ 那一行是按下去什么都不发生的假开关", settings.contains("settings.setGalleryAnimated("))
        assertEquals("还有文件写着「不能在这儿改」（这一枚现在改得动了）", 0, hits("不能在这儿改").size)
        val prefs = DesktopSourceTree.codeText(
            java.io.File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/data/DesktopGalleryPreferences.kt"),
        )
        assertTrue("对照组失效：`galleryAnimated` 那枚读侧登记没了", prefs.contains("KEY_ANIMATED"))
    }

    /**
     * ⑱ 画师单人页不再说「这一屏今天不接续页」（第二十一批 批 21）。
     *
     * 那一句是批 19 写下的**当时的边界**，而它住在两处：段头那句屏上的话，与类注释里同一句说法。
     * 这里扫全文（含注释）而不是只扫代码行 —— 坑 ㉑ 那条：过期说明九成住在注释里。
     *
     * ⚠️ 对照钉的是"续页真接上了"这三件事，而不是"我不想看见那五个字"：
     * 撤掉续页（回到只取一轮第一页）会先红在对照上，读的人立刻知道那句该回来，
     * 而不是被要求改注释（§22.3 的反向应用，与 ①②⑬… 同一套配法）。
     */
    @Test
    fun 单人页不再说缺续页() {
        assertEquals("还有文件把'不接续页'当边界念着", 0, hits("不接续页").size)
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopArtistProfilePane.kt")) + "\n" +
            // 批 22 把"一页回来后怎么落地"从屏里抽到了判据面（为了 ⑰–⑳ 能真跑），
            // 所以这一条对照读的是**那两片合起来**的文本 —— 它要钉的事一字未改。
            DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopArtistProfilePolicy.kt"))
        assertTrue("对照组失效：那一屏没有那枚「下一页」的文案件", pane.contains("desktopGalleryMoreLabel("))
        assertTrue("对照组失效：那一屏不吃到底判据（按钮可见性没人管）", pane.contains("atEnd"))
        assertTrue(
            "对照组失效：那一屏不再走共享那颗持有件（续页的三本账没人记了）",
            pane.contains("desktopGalleryBatchApply("),
        )
    }

    /**
     * ⑲ 发现那一屏不再说「每次进来都重新抽一轮」—— 批 5 起换域换回来**还是同一批**。
     *
     * 这一条最坏的地方不是句子假，而是**同模块里有一条绿判据正钉着相反的事实**：
     * `DesktopDomainStateMemoryTest` ④ 的用例名就叫「种子换回来还是同一批」，
     * 而 `DesktopForYouPaneTest` 那条只判这句常量含「沿用上次」—— 两条各绿各的，屏上是一句假话。
     */
    @Test
    fun 每次进来都重抽那句下架() {
        assertEquals("还有文件写着「每次进来都重新抽」", 0, hits("每次进来都重新抽").size)
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopForYouPane.kt"))
        // 对照一：这句被禁**是因为换回来真的还是同一批**（种子与取数态都在 saveable 上、且有跳过守卫）。
        // 撤回普通 `remember` 会先红在这里，而不是红在措辞上 —— 那句说明就该回来。
        assertTrue(
            "对照组失效：那一屏的种子不在 `rememberSaveable` 上（那这句禁串该撤）",
            Regex("var seed by rememberSaveable").containsMatchIn(pane),
        )
        assertTrue(
            "对照组失效：那一屏没有那道跳过守卫（还原了状态还是每次进屏重发一笔）",
            pane.contains("if (state is DesktopForYouState.Ready) return@LaunchedEffect"),
        )
        // 对照二：**仍然成立的那一半**——缺的是跨进程那一份快照，而它在桌面编译面外。
        val build = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        assertTrue("对照组失效：`GalleryForYouCache` 又进桌面编译面了（那半句也该撤）", build.contains("\"GalleryForYouCache.kt\","))
        // 撤掉假话不许只删不说：新的那一句必须点名"哪一种进来才会重抽"。
        assertTrue(
            "页尾那句没说要留到什么时候（只删不说）：${DESKTOP_FOR_YOU_SNAPSHOT_LINE}",
            DESKTOP_FOR_YOU_SNAPSHOT_LINE.contains("再回来还是") && DESKTOP_FOR_YOU_SNAPSHOT_LINE.contains("关掉"),
        )
    }

    /**
     * ⑳ 排行榜那一行的两句缺席理由**都**假：
     * ① "没有跨站取榜的口" —— 口在共享面上（`GalleryRankings` 那六档与各站分数串，桌面单人页从批 19
     *    就在吃它；`GalleryDailyFeed` 更是桌面「推荐」那一档正在用的跨站并发件）；
     * ② "yande.re 也只固定给 40 条" —— 那枚天花板钉在 `popular_recent`（`page`/`limit` 被忽略），
     *    而排行榜要走的 `searchPosts(order:score)` 认 `limit`（天花板 320）也认 `page`。
     *
     * ⚠️ 这一条最像批 24 那条：`DesktopGalleryHomeSectionsTest` ④ 只断 reason 含「40 条」，
     * 于是它把那个**失真数字**钉成了要求（坑 ㉖ 第十二次）。
     */
    @Test
    fun 排行榜那行不再说缺口() {
        assertEquals("还有文件写着「没有跨站取榜的口」", 0, hits("没有跨站取榜的口").size)
        assertEquals("还有文件写着「只固定给 40 条」", 0, hits("只固定给 40 条").size)
        // 批 28 之后那一行已经点亮：它自己"有没有内容件、缺席说明撤净没"由 `DesktopRankingTest` ⑨ 钉，
        // 这里只留着"那两句假话不许回来"与下面三条对照（同一件事只许一把尺子钉）。
        // 对照一：禁它**是因为口真的在**（桌面哪天不吃 `GalleryRankings` 了，那句缺席理由就该回来）
        val policy = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopArtistProfilePolicy.kt"))
        assertTrue("对照组失效：桌面不再吃 `GalleryRankings`", policy.contains("GalleryRankings."))
        // 对照二：六档真在共享面上（真跑枚举，不扫字）
        assertEquals("对照组失效：排行榜今天不是六档", 6, GalleryRanking.entries.size)
        // 对照三：40 钉在 popular_* 那一路，而排行榜走的那条契约上就带分页与 limit
        val api = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/data/GalleryBusinessApi.kt"))
        assertTrue(
            "对照组失效：`searchPosts` 不再带分页形参（那一句「40 条」反而该回来）",
            api.contains("suspend fun searchPosts(tags: String, page: Int, limit: Int)"),
        )
    }

    /**
     * ㉑ 反搜那一路不再说"整颗没接"（批 35 摘掉了那两枚抛桩）。
     *
     * 这一条是**批 34/35 自己写的两句**变旧的：设置那一屏的键区注释与端口注释都写过
     * "桌面这一族今天整颗没接"，当时是实话，接上那一刻就成了假话
     * （[[project-implemented-but-unwired]] 那条反方向的账，这里是它的第三次）。
     */
    @Test
    fun 反搜那一路不再说整颗没接() {
        assertEquals("还有界面件写着反搜「整颗没接」", 0, hits("整颗没接").size)
        // 对照一：真跑那颗判据件 —— 它有得出发车那一档，说明这一族今天真在判而不是桩。
        val gate = desktopReverseGate(
            GalleryPost(site = GallerySite.YANDERE, id = 1L, fileUrl = "https://img/1.jpg"),
            masked = false,
            hasKey = true,
        )
        assertTrue("对照组失效：这一族今天判不出发车那一档（那句缺席说明反而该回来）", gate is DesktopReverseGate.Send)
        // 对照二：那一枚入口真接在浮层上（撤了接线会先红在这里，而不是红在措辞上）。
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        assertTrue("对照组失效：浮层不再调那一笔", panel.contains("desktopReverseRun("))
    }

    /**
     * ㉒ 「桌面没有阅读器」那句是假话了（第八十一批落地了阅读那一屏）。
     *
     * 它是批 14 那次改判补上去的：那时那半句是实话，而**一句点名"差哪一层"的话也会随落地过期**。
     * 所以这一条把这类句式整体禁掉：差的那一层要说是"那笔没写的账"，不是"某一屏存不存在"。
     */
    @Test
    fun 阅读那一屏已经有了而那句还没改过来() {
        assertEquals("还有文件写着「桌面没有阅读器」", 0, hits("桌面没有阅读器").size)
        // 对照：这句被禁**是因为阅读那一屏真在桌面上挂着** —— 撤掉挂载会先红在这里。
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicPane.kt"))
        assertTrue(
            "对照组失效：阅读那一屏不再挂分了（那一句就又算实话，该把它放回来）",
            pane.contains("DesktopComicReaderPane("),
        )
        // 对照二：那一屏不是空壳 —— 它真去问页表（撤掉那一问等于又回到"没有那一屏"）
        val reader = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicReader.kt"))
        assertTrue("对照组失效：阅读那一屏没问页表了", reader.contains("engine.pages("))
    }

    private val 数词 = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八")
}
