package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySearchSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十二批 批 2 · ⑤：标签 / 画师 → 搜索那条动线。
 *
 * ## 这一批补的是"缺动线被说成用户的责任"
 *
 * 画师那一屏页脚写着「要看某位的图，去「搜索」那一屏输入他的名字」，而详情浮层里那整片标签墙
 * 与那枚名字全是裸文本 —— 把一条 Android 侧早就有的主路径（`GalleryInfoSheet` 点标签 →
 * `GallerySearchHandoff.postTag` → `GalleryScreen` consume → 即查重查）在桌面上说成"请你自己打字"。
 * 手册 §22.3 那条规矩在这里的反向应用：**接上能力之后要回查每一屏那句说明**。
 *
 * ## 「站点不能猜」是这条链唯一容易被写错的地方
 *
 * Android 那边交接载荷带的是 `(站, 标签)`，注释原文是「两站词表不通，站点不能猜」
 * （`GallerySearchHandoff.kt:34-37`）。桌面这一侧多一档可用来源（「全部」是桌面搜索屏自己的
 * 来源轴第一档），所以规则是：**只有一位所属站就切到那一站，跨好几位（画师整页那种并条）就打全部**
 * —— "都打"是真做了的事，把它写成某一站才是猜。
 *
 * ## 画师那一栏的三档，与批 1 那条同源
 *
 * 画师名不是 `post.author`。实测那句在 `GalleryInfoSheet.kt:444-447`：yande.re 的 `author`
 * 与 Gelbooru 的 `owner` 都是**上传者的登录名**（真帖 1269641 的 `author=Arsy`，画师是标签 `gijang`），
 * 只有 Safebooru 的 `tag_string_artist` 才是画师档。而桌面浮层从前把 `post.author` 摆在稿的
 * `.artist .nm`（画师署名）那一格上 —— 那是一句**用位置说出来的假读数**。
 * 真画师走站方标好的档位（`fetchTagCategories` → `galleryArtistNames`，词典兜底那一路桌面恒空），
 * 于是"没取到 / 没有 / 没接"三档又出现一次：那一页没答上、答上了而这张没标画师档、取到了几位。
 */
class DesktopSearchHandoffTest {

    private fun post(
        site: GallerySite = GallerySite.GELBOORU,
        tags: String = "touhou wowoguni",
        author: String = "Arsy",
    ) = GalleryPost(
        site = site,
        id = 11L,
        tags = tags,
        author = author,
        previewUrl = "https://p/11.jpg",
    )

    /**
     * ① 交接载荷：串**原样**、站点**按所属**、同一枚再点一次要能再落地。
     *
     * `token` 存在的理由不是装饰：种子是数据类，同一枚标签第二次交接 `query` 与 `source` 都没变
     * ⇒ 消费方那颗 `LaunchedEffect(seed)` 不会重启 ⇒ 用户改了输入框之后再点同一枚标签，屏上不动。
     * 那正是"看起来能点而其实只在第一次管用"。
     */
    @Test
    fun 交接带着所属站而不猜() {
        val seed = desktopSearchSeedFor(listOf(GallerySite.GELBOORU), "touhou", token = 3)
        assertEquals("交接串就是那一枚标签，不改写不加前缀", "touhou", seed.query)
        assertEquals(
            "一位所属站 ⇒ 来源轴切到那一站（留在「全部」会把另外两站的同名标签一起捞进来，" +
                "而用户看到的墙不是他点的那一站的）",
            listOf(GallerySite.GELBOORU),
            seed.source.sites,
        )
        assertEquals(3, seed.token)

        val again = desktopSearchSeedFor(listOf(GallerySite.GELBOORU), "touhou", token = 4)
        assertFalse(
            "同一枚标签第二次交接必须是个**不等**的载荷（否则消费方不会重新落地）",
            seed == again,
        )

        assertEquals(
            "两站并条的那一位（画师整页）打全部 —— 都打是真做了，写成某一站才是猜",
            GallerySearchSource.ALL.sites,
            desktopSearchSeedFor(GallerySite.entries.toList(), "gijang", token = 1).source.sites,
        )
        assertEquals(
            "排除项的减号是站方语法，不在这里改写（改了下一次 parse 就变成另一枚标签）",
            "-foo",
            desktopSearchSeedFor(listOf(GallerySite.YANDERE), "-foo", token = 1).query,
        )
    }

    /**
     * ② 画师那一栏三档：`null`（那一页没答上）与"答上了而这张没标画师档"**不许塌回同一档**。
     *
     * 这正是批 1 那条 `DesktopFaceProbe` 的同族：把"没问到"记成"没有"，屏上就替站方宣布了一句话。
     */
    @Test
    fun 画师那一栏三档分开() {
        val asked = desktopDetailArtists(post().tagList, categories = mapOf("wowoguni" to 1, "touhou" to 0), fallbackArtistNames = emptySet(), asked = true)
        assertTrue(
            "站方答上了档位，画师就该是站方标好的那几个名字（这里只有 wowoguni 是 1 档）",
            asked is DesktopDetailArtists.Names && asked.names == listOf("wowoguni"),
        )

        val unlabeled = desktopDetailArtists(post(tags = "touhou").tagList, categories = mapOf("touhou" to 0), fallbackArtistNames = emptySet(), asked = true)
        assertTrue(
            "答上了而这一张确实没标画师档 ⇒ 这是**一句定论**，与'没问到'不同",
            unlabeled is DesktopDetailArtists.Unlabeled,
        )

        val notAnswered = desktopDetailArtists(post().tagList, categories = null, fallbackArtistNames = emptySet(), asked = true)
        assertTrue(
            "那一页没答上（或还没取完）⇒ 不许说成'这张没有画师'",
            notAnswered is DesktopDetailArtists.NotAnswered,
        )
        // 对照：站方答上了但整张一个档位都没分类（空表）也不算"没问到"。
        assertTrue(
            "空档位表是'答上了、没分类'，与 null（没答上）两档",
            desktopDetailArtists(post().tagList, categories = emptyMap(), fallbackArtistNames = emptySet(), asked = true) is DesktopDetailArtists.Unlabeled,
        )
    }

    /**
     * ②′ 三档各说一句人话，而取到的那一档不在屏上留一句废话。
     *
     * "没取到"那一句要**点是哪一站**：2026-10-05 的 stdout 上那一行写的是
     * `取 https://safebooru.donmai.us/posts/12314547 的标签分类失败：分类页没有可识别的 tag-type 类名`
     * —— 页面 200 而认不出类名，不是网络。那一站到底"没接"还是"偶尔没取到"还差第二张样本，
     * 所以这一句先把站名摆出来，让人自己去分，而不是替用户下一个结论。
     */
    @Test
    fun 画师那一栏三档各有一句() {
        val yande = GallerySite.YANDERE
        assertEquals("取到名字就不必再写一句'画师如下'（名字本身就是内容）", "", desktopArtistLineNotice(DesktopDetailArtists.Names(listOf("gijang"), DesktopArtistSource.SITE), yande, dictionary = null))
        assertEquals("站方答上了而这张没标画师档，要说'没标'", "这一张站方没标画师", desktopArtistLineNotice(DesktopDetailArtists.Unlabeled, yande, dictionary = null))
        val notice = desktopArtistLineNotice(DesktopDetailArtists.NotAnswered, GallerySite.SAFEBOORU, dictionary = null)
        assertTrue(
            "没取到那一句里不许出现'没有画师'（那是把'我们没问到'说成站方的结论）",
            notice.contains("没取到") && !notice.contains("没有画师"),
        )
        assertTrue(
            "那一句要点是哪一站 —— 三站的帖子页 markup 不是一套，'没取到'在某一站可能是永远",
            notice.contains(GallerySite.SAFEBOORU.displayName),
        )
    }

    /**
     * ③ 「上传者」那一格与 Android **同一个词**，而且走信息表而不是摆在署名位。
     *
     * 词不是自选的：判据直接从 `GalleryInfoSheet.kt` 那一行把标签抽出来比。两端各写一份词，
     * 用户换设备就读到两张不同的表 —— 而"这张图到底谁画的"恰好是最不该两端不同的那一格。
     */
    @Test
    fun 上传者那一格逐字回核Android() {
        val androidLine = DesktopSourceTree.codeLinesOf(DesktopSourceTree.androidSource("gallery/ui/GalleryInfoSheet.kt"))
            .first { it.contains("post.author") && it.contains("FactCell") }
        val label = Regex("\"([^\"]+)\"").findAll(androidLine)
            .map { it.groupValues[1] }
            .toList()
            .last()
        val rows = desktopDetailInfoRows(post(author = "Arsy"), favorite = false)
        assertTrue(
            "`post.author` 在桌面没走信息表 —— 它又被摆回署名位了（那是上传者，不是画师）：$rows",
            rows.any { it.first == label && it.second == "Arsy" },
        )
        assertEquals("Android 那一格的词是「上传者」，桌面不许换成第二颗词", "上传者", label)
    }

    /** ④ 画师那一份取数**只走共享那颗词典件**，桌面不自拼页面解析。 */
    @Test
    fun 画师档位取数不自拼() {
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        assertTrue(
            "面板没把标签档位这件事转交共享词典件（自己解析那一页 HTML 就是第二份判据）",
            panel.contains("fetchTagCategories("),
        )
        listOf("Regex(", "Jsoup", "HttpEngine").forEach { banned ->
            assertFalse("面板里出现了自己取数/解析的形状「$banned」", panel.contains(banned))
        }
        // 对照组：确实**接了**词典件而不是只写了一句注释。
        assertTrue("面板没接 ports.lexicon", panel.contains("ports.lexicon"))
    }

    /** ⑤ 标签墙每一枚都可点，而可点的地方要有真的接收者。 */
    @Test
    fun 标签墙上每一枚都可点() {
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        val wallStart = panel.indexOf("post.tagList.forEach")
        assertTrue("面板里没有标签墙那一段（判式先失效，后面全是恒真）", wallStart >= 0)
        val wall = panel.substring(wallStart, minOf(wallStart + 900, panel.length))
        // ⚠️ 2026-10-07 批 10 改判：这一条原本只认 `.clickable` 那一枚字面形状，于是"同一枚 chip 上
        // 另加一枚长按动作"（`combinedClickable`，点击仍是去搜它）把它判红了 —— 它要防的是
        // "一整片标签只能看"，而那件事并没有发生。**判意图不判字面**：两种形状都算可点，
        // 而"点击那一枚仍接交接"由下面那条单独钉（批 8 那条"扫整串表达式"的规矩反向适用）。
        assertTrue(
            "标签墙那段里没有可点接线 —— 一整片标签还是只能看",
            wall.contains(".clickable") || wall.contains(".combinedClickable("),
        )
        assertTrue(
            "那一枚点击里没调用交接（摆了动作却没接线 = 假开关）",
            wall.contains("onClick = { onSearch(listOf(post.site), tag) }"),
        )
        assertFalse(
            "浮层改回去读那颗局部槽了 —— 它是**覆盖层**，挂在 provider 外面，" +
                "读到的是默认值，用户一按就当场炸（2026-10-05 真炸过，读数只在 stdout）",
            panel.contains("LocalDesktopSearchHandoff.current"),
        )
        assertFalse("交接被写成空 lambda（按下去什么都不发生而不报错）", panel.contains("-> Unit }"))
    }

    /** ⑥ 四跳接线：声明一处、根上 provide、页脚那句旧说明不许残留。 */
    @Test
    fun 交接槽只有一颗而旧那句说明下架() {
        val declarations = DesktopSourceTree.desktopMainSources()
            .flatMap { DesktopSourceTree.codeLinesOf(it) }
            .filter { it.contains("LocalDesktopSearchHandoff") && it.contains("compositionLocalOf") }
        assertEquals("交接槽的声明处应当恰好一处，多一处就是第二份状态：" + declarations, 1, declarations.size)

        val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
        assertTrue("应用根没 provide 那颗交接槽", app.contains("LocalDesktopSearchHandoff provides"))
        assertTrue(
            "覆盖层那一格没从根上把交接递进去（provider 够不到它，见判据 ⑤ 那一次当场炸）",
            app.contains("onSearch = goToSearch"),
        )
        assertTrue(
            "根上换域时没把种子写进状态（provide 只写了个空操作的话，切过去仍是空屏）",
            app.contains("searchSeed"),
        )

        val search = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGallerySearchPane.kt"))
        assertTrue("搜索屏没读那颗种子（交接落在没人消费的槽位上）", search.contains("seed.token") || search.contains("LaunchedEffect(seed)"))

        val artists = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopArtistsPage.kt"))
        assertFalse(
            "画师那一屏还写着「去「搜索」那一屏输入他的名字」—— 动线接上了，这句就把缺动线说成用户的责任",
            artists.contains("输入他的名字"),
        )
    }

    /** ⑦ 那颗种子的初值必须仍然是 `--query=`（取证那条链不许被交接顶掉）。 */
    @Test
    fun 取证初值与交接共用一颗状态() {
        val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
        assertTrue(
            "搜索屏的种子初值没吃 `startQuery` —— 第六批那条 `-Pquery=` 链会静默断在最后一跳",
            app.contains("searchSeed") && app.contains("startQuery"),
        )
        val nav = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGallerySearchPane.kt"))
        assertFalse("搜索屏还留着旧的 startQuery 形参（两颗状态各写一份 = 谁覆盖谁没人说清）", nav.contains("startQuery:"))
    }
}
