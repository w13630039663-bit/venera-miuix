package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十一批 P0：详情浮层（覆盖式 340）。
 *
 * ## 这一批还的债是「卡点不动」，而它带出来的第一条规矩是**填不出的格子整格不摆**
 *
 * 稿上那六格里有三格结构性没有（标题 / 相关标签 / 作品数）加一格今天没有（上传时间），
 * 四个动作里只有一个今天有现成写侧。用户 2026-10-04 拍的是**改稿**：
 * 撤掉，而不是留一行"这一站没给" —— 留一格常年的"没给"与本仓"宁可少摆不可摆错"那条对立。
 * ⇒ 于是"撤掉了"这件事必须由判据钉住（③）：撤掉的东西一旦被谁顺手加回来，
 * 屏上就会出现一格永远空的表，而那是这一批最初要避免的形状。
 *
 * ## 信息表提成纯函数（④）不是洁癖
 *
 * 「读数没有就不摆那一行」与「有值也必须走一位小数」两条在 Android 那颗
 * `GalleryInfoSheet.GalleryFactGrid` 里是**用真缺陷换来的**（Gelbooru 的 JSON 没有 `file_size`，
 * 摆出来每张都写「0 MB」；整数除法又把 yande.re 不到 1.5 MB 的真文件也压成「0 MB」）。
 * 桌面这边只有把它做成能真跑的函数，才能拿 Gelbooru 与 yande.re 两种输入各跑一遍。
 */
class DesktopGalleryDetailTest {

    private fun post(
        site: GallerySite,
        width: Int = 0,
        height: Int = 0,
        ext: String = "",
        size: Long = 0L,
    ) = GalleryPost(
        site = site,
        id = 42L,
        tags = "landscape night",
        width = width,
        height = height,
        fileExt = ext,
        fileSize = size,
        previewUrl = "https://img/42.jpg",
    )

    private fun List<Pair<String, String>>.value(key: String): String? = find { it.first == key }?.second

    private fun assertNoRow(rows: List<Pair<String, String>>, key: String, why: String) {
        assertTrue("$why（但「$key」那一行还是被摆出来了）", rows.none { it.first == key })
    }

    /** ④ 信息表：读数没有就**整行不摆**，有值走一位小数，键名与 Android 那颗同源。 */
    @Test
    fun 信息表只摆真读数() {
        val gelbooru = desktopDetailInfoRows(
            post(GallerySite.GELBOORU, width = 1920, height = 1080, ext = "jpg"),
            favorite = false,
        )
        assertEquals("来源键名与 Android 那侧同一颗词", "Gelbooru", gelbooru.value("来源"))
        assertEquals("分辨率写法同源（中间没有空格）", "1920×1080", gelbooru.value("分辨率"))
        assertEquals("格式取站方那个扩展名并大写", "JPG", gelbooru.value("格式"))
        assertNoRow(gelbooru, "文件大小", "Gelbooru 的 post JSON 根本没有 file_size，解析处一律填 0")
        assertEquals("未收藏要说未收藏", "未收藏", gelbooru.value("本地状态"))

        val yandere = desktopDetailInfoRows(
            post(GallerySite.YANDERE, width = 1280, height = 960, ext = "png", size = 1_400_000L),
            favorite = true,
        )
        assertEquals(
            "整数除法会把不到 1.5 MB 的真文件压成「0 MB」—— 必须走一位小数",
            "1.3 MB",
            yandere.value("文件大小"),
        )
        assertEquals("已收藏", yandere.value("本地状态"))

        val bare = desktopDetailInfoRows(post(GallerySite.SAFEBOORU), favorite = false)
        assertEquals("站方什么都没给 ⇒ 只剩来源与本地状态两行（不许摆 0×0 与空格式）", 2, bare.size)
    }

    /** ③ 撤掉的那几样不许回来：整颗面板件扫字面量，一次列出全部违例。 */
    @Test
    fun 填不出的那几样不许被摆回来() {
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        listOf("上传时间", "相关标签", "作品数", "暂不支持", "这一站没给", "分享").forEach { banned ->
            assertTrue(
                "面板上出现了「$banned」—— 那一样是拍板**撤掉**的（留一格常年的没给与本仓那条对立）",
                panel.contains(banned).not(),
            )
        }
        // 对照组：信息表那五档真在（读空了会让上面六条恒真）。
        val policy = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPolicy.kt"))
        listOf("来源", "分辨率", "格式", "文件大小", "本地状态").forEach { key ->
            assertTrue("对照组失效：信息表里没有「$key」这一档", policy.contains("\"$key\""))
        }
    }

    /** ⑧ 面板与策略两颗件里**一个 `.dp` 字面量都不许有**：尺寸一律走 `DesktopGalleryMetrics`。 */
    @Test
    fun 面板尺寸一律走现成口径不许造新数() {
        val offenders = listOf("DesktopGalleryDetailPanel.kt", "DesktopGalleryDetailPolicy.kt").flatMap { name ->
            DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopUiSource(name))
                .filter { line -> Regex("[0-9]+(\\.[0-9]+)?\\.dp").containsMatchIn(line) }
                .map { "$name：$it" }
        }
        assertTrue(
            "出现了写死的 dp（稿上那批值已进口径表：340 / 46 / 200 / 30 / 16 / 12 / 76）：" +
                offenders.joinToString(" ｜ "),
            offenders.isEmpty(),
        )
        // 对照组：那批值确实进了口径表。
        val metrics = DesktopSourceTree.codeText(
            File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGalleryMetrics.kt"),
        )
        listOf("detailWidth", "detailScrimWidth", "detailHeroHeight", "detailCloseSize", "detailInfoKeyWidth")
            .forEach { token -> assertTrue("口径表里少了「$token」", metrics.contains(token)) }
    }

    /** ①② 卡位可点：`onClick` 必填、五处调用点都真传，且点击面是**整张卡**。 */
    @Test
    fun 卡位可点而点击面是整张卡() {
        val tile = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryPostTile.kt"))
        assertTrue("tile 那颗件没收到 onClick", tile.contains("onClick: (GalleryPost) -> Unit"))
        assertTrue(
            "onClick 带了默认值 —— 漏传不报错，而漏传的形状正是「某一屏的卡点不动」（与第五批那枚墙参数同一条教训）",
            Regex("onClick: \\(GalleryPost\\) -> Unit\\s*=").containsMatchIn(tile).not(),
        )
        assertTrue("点击修饰没挂在整张卡上（挂在小件上就是「看得见的一圈字能点、图不能点」）", tile.contains("clickable"))
        // 墙往下传的是**整批与序号**而不是裸 post：`masked` 那一档必须跟着进面板，
        // 否则"墙上挡住的那张图，点开面板就看到了"就是第四批那层守卫的一个绕出口。
        // ⚠️ P0 时这一枚是 `(DesktopWallCard) -> Unit`，P1 加翻页后改成整批（判据 ⑤ of P1 同一条）。
        assertTrue("那颗共用的墙没收到 onPick（或收到了裸 post，挡住那一档就丢了）",
            tile.contains("onPick: (DesktopWallSelection) -> Unit"))

        listOf(
            "DesktopDailyPane.kt",
            "DesktopGalleryFavoritesPane.kt",
            "DesktopGallerySearchPane.kt",
            "DesktopForYouPane.kt",
            "DesktopLatestPane.kt",
        ).forEach { name ->
            val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(name))
            assertTrue("「$name」那一屏没传 onPick（这一屏的卡就点不动）", code.contains("onPick ="))
        }
    }

    /**
     * ⑥ 浮层不吃 `weight`：它是覆盖层，进了权重分配就是把列数压掉一档的那种旧账。
     *
     * ⚠️ 这一条**改过一次判法**（不是放宽）：原句扫的是骨架那颗件里有没有
     * `DesktopGalleryMetrics.detailWidth`，而落地后面板自己就知道自己多宽 ——
     * 那句会红在一个"其实更对"的形状上。改成两侧各钉一处：
     * **宽**由面板自己从口径表取（谁画谁负责），**渐隐**由骨架挂（它要压在别人的内容上）。
     * 少了任何一枚，屏上就会出现"浮层凭空窄/宽一档"或"面板边上一条脏带"。
     */
    @Test
    fun 浮层是覆盖层而不参与宽度分配() {
        val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
        assertTrue("面板没叠在右上角", app.contains("Alignment.TopEnd"))
        assertTrue("左缘那道渐隐不在骨架这一层（画进面板自己框内就只是边上一条脏带）", app.contains("DesktopGalleryMetrics.detailScrimWidth"))
        assertTrue("关掉浮层那条路不在", app.contains("selected = null"))
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        assertTrue("面板宽没走口径表", panel.contains("width(DesktopGalleryMetrics.detailWidth)"))
        assertTrue("面板里出现了 weight( —— 覆盖层不该参与分配", panel.contains("weight(").not())
        // 对照组：主区那一格仍然吃权重（换成 Box 之后不许把它弄丢，否则整屏塌成一排）。
        assertTrue("主区没吃 weight(1f)：", app.contains(".weight(1f)"))
    }

    /**
     * ⑦ 三个动作各有真落点，且没有一处是空 lambda。
     *
     * ⚠️ 2026-10-09 批 77 下半 改判一处锚点：`browse(` 那一句原本住在面板这颗文件里，
     * 而本批把「交给系统浏览器」那一步搬进 `DesktopOpenInBrowser.kt`（新那一格「打开来源」要用同一颗，
     * 抄一份就是两份会漂的口径）。判据的**意思**没变 —— "那一枚按钮真能开到东西" ——
     * 所以锚点跟着搬家：面板这侧钉的是"它在调用那颗件"，真调 AWT 的那一行钉在它的新家里。
     * 少钉任何一侧都会漏掉两种坏法（面板不再调用 / 那颗件被掏空）。
     */
    @Test
    fun 三个动作都真接上了() {
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        listOf(
            "openInBrowser(" to "在浏览器打开原图",
            "systemClipboard" to "复制链接",
            "toggle(" to "收藏开关",
        ).forEach { (token, what) ->
            assertTrue("「$what」没真接上（源码里找不到 $token）", panel.contains(token))
        }
        assertEquals(
            "面板自己又去调 AWT 了（那一步的唯一出处在 DesktopOpenInBrowser.kt）",
            0,
            DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"), "Desktop.getDesktop()"),
        )
        val browser = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopOpenInBrowser.kt"))
        assertTrue("那颗件里真调 AWT 的那一行没了（面板调用还在，按下去却是空的）：$browser", browser.contains("browse("))
        listOf("onClick = { }", "onClick = {}", "Unit }").forEach { empty ->
            assertTrue("面板上出现了空 lambda「$empty」—— 那就是一枚假开关", panel.contains(empty).not())
        }
    }

    /** ⑤ 选中态只有一处声明（散成几处就会互相看不见）。 */
    @Test
    fun 选中态只住在应用根一颗() {
        val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt"))
        assertEquals("选中态的声明处必须恰好一处", 1, Regex("var selected\\b").findAll(app).count())
        assertTrue("面板没吃到选中态（声明了却没人用）", app.contains("DesktopGalleryDetailPanel("))
        // 对照组：面板那颗件真存在且收这一批。
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        assertTrue("对照组失效：面板那颗件没收到这一批", panel.contains("selection: DesktopWallSelection"))
    }
}
