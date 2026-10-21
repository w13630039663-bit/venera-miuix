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
    /** ② 「关注在桌面没有入口」那句已经活了太久而入口就在浮层里。 */
    /** ③ 「与发现那一屏同一档缺口」——那一屏的续页第十批 ③ 就上线了，参照物不存在。 */
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
    /**
     * ⑨ 「补全不做成打字时自动弹，因为桌面没有那套防抖状态机」那句是假话（批 11 量出来的）。
     *
     * 候选那一排**本来就跟着输入框一起换**：那颗 effect 的 key 里有 `query`，每敲一键重跑一笔、
     * 上一笔由 effect 自己作废 —— 对岸靠 `suggestJob?.cancel()`，两端同一形状，谁都没有防抖延迟。
     * 那句把"今天就是这么跑的"写成"桌面做不到"，属于 §22.3 那族最阴的：**能力在，话把它说没了**。
     */
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
    /** ⑯ 发现那一域的注释还说它缺"续页"—— 那一屏的「下一页」第十批 ③ 与批 8 就接上了。 */
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
    /**
     * ⑲ 发现那一屏不再说「每次进来都重新抽一轮」—— 批 5 起换域换回来**还是同一批**。
     *
     * 这一条最坏的地方不是句子假，而是**同模块里有一条绿判据正钉着相反的事实**：
     * `DesktopDomainStateMemoryTest` ④ 的用例名就叫「种子换回来还是同一批」，
     * 而 `DesktopForYouPaneTest` 那条只判这句常量含「沿用上次」—— 两条各绿各的，屏上是一句假话。
     */
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
    /**
     * ㉒ 「桌面没有阅读器」那句是假话了（第八十一批落地了阅读那一屏）。
     *
     * 它是批 14 那次改判补上去的：那时那半句是实话，而**一句点名"差哪一层"的话也会随落地过期**。
     * 所以这一条把这类句式整体禁掉：差的那一层要说是"那笔没写的账"，不是"某一屏存不存在"。
     */
    /**
     * ㉓ 「仍然缺的是漫画章节下载」那半句是假话了（第九十六批把整话落盘接上了）。
     *
     * 它住在 `DesktopGalleryHomeSections.kt` 的**注释**里（那条"下载"缺席说明的历史账），
     * 正是这颗尺子故意扫全文而不跳注释的那一族 —— 用 `codeText` 扫它恒绿。
     * 排期 `remaining-plan-and-reuse-2026-10-10.md` 批 96 那一行点名要**同批**改掉它：
     * 同一族那五句假话平均活四批，而这一句已经活到第六批才落地。
     */
    /** 某句禁串活在 `desktop/src/main` **整棵树**的哪一行（含注释；比 [hits] 宽一档）。 */
    private fun hitsMain(banned: String): List<String> =
        DesktopSourceTree.desktopMainSources().flatMap { file ->
            file.readText().lines().filter { it.contains(banned) }.map { "${file.name}: ${it.trim().take(90)}" }
        }

    /**
     * ㉔ 「已下载的整话在屏上数不着」那半句也过期了（第九十七批接上了「本机已下载」那一屏）。
     *
     * 同一族**第二次**栽在同一批里：㉓ 那条是批 96 接手时把别人留下的假话和自己的工作一起改的，
     * 而这一句是批 96 自己新写的账，批 97 把读侧接上之后它**在同批内**就变假了 ——
     * 改 README 的时候把界面里那句漏了，而新建那颗读侧件的 own 注释里又写了一遍。
     * 所以这一条扫**整个 main**（不只 `gallery/ui/`）：那句说的是"桌面到处都没有"，本来就不该只钉一个目录。
     * 对照钉的是那一屏真在树上、且真接在漫画域的分支上。
     */
    /**
     * ㉕ 「那一屏删不掉已下的话」也过期了（第九十八批接上了删除）。
     *
     * 同一族**第三次**栽在同一批里：㉓ 是批 96 接手时改别人的账，㉔ 是批 96 自己写的账被批 97 顶假，
     * 而这一句是批 97 自己新写的账（那颗读侧件的 KDoc 与 README 各一遍），批 98 接上删除后它**在同批内**又假了。
     * 所以禁串取的是那一屏**连写的形状**，而扫的是整个 main（含注释）。
     */
    private val 数词 = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八")
}
