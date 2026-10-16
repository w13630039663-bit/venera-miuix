package com.venera.desktop.gallery.ui

import com.venera.compose.data.db.HistoryRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 82 · 阅读历史那一屏的**读侧**判据（纯函数 + 源码扫描）。
 *
 * ## 两句抄对岸的话，一处公式抄对岸的算式
 *
 * 空态那两句与进度那一句都出自 `app/src/main/java/com/venera/compose/feature/HistoryScreen.kt`
 * 和 `HistoryViewModel.kt:95-105` 的 `progressDescription()`。那颗扩展函数住在 `feature/`，
 * **不在桌面编译面上**（`desktop/build.gradle.kts` 只 shared `data/platform` 与 `data/db` 那两片），
 * 所以这里只能把算式与措辞各抄一遍，再用判据钉住"抄的那一份没漂"。
 *
 * ## 这一屏最容易被做成假的两件事
 *
 * - 读库失败画成空态：屏上「还没有阅读记录」是句实话吗？不是 —— 它是"我们没读到"。两档必须念两种话。
 * - 把别的源那一行也做成能点的样子：漫画域这一趟只装载了一个源（`config.sourceKey`），
 *   点了另一源的那本只会拿错的源去问同一个号 —— 那是拿 A 站的凭据问 B 站那一族的老账。
 */
class DesktopComicHistoryScreenTest {

    private fun record(
        chapterIndex: Int = 1,
        pageIndex: Int = 7,
        total: Int = 89,
        sourceName: String = "jm",
        comicId: String = "1461316",
    ) = HistoryRecord(
        comicId = comicId,
        title = "某本漫画",
        author = "",
        coverUrl = "",
        sourceName = sourceName,
        lastChapterTitle = "第 2 话",
        lastChapterIndex = chapterIndex,
        lastPageIndex = pageIndex,
        totalPages = total,
        updatedAt = 1_700_000_000_000L,
    )

    @Test
    fun 进度那一句与对岸那颗扩展函数同式() {
        // 对岸：`第 ${lastChapterIndex + 1} 话` 与 `第 ${lastPageIndex + 1} 页`，中间 ` - `，
        // 两截各带一枚 `>= 1` 的门（都关着时整句是空串）。四种组合各喂一次。
        assertEquals("第 2 话 - 第 8 页", desktopComicHistoryProgressText(record()))
        assertEquals("只有话号那一截时不留分隔符", "第 6 话", desktopComicHistoryProgressText(record(chapterIndex = 5, pageIndex = -1)))
        assertEquals("只有页号那一截", "第 1 页", desktopComicHistoryProgressText(record(chapterIndex = -1, pageIndex = 0)))
        assertEquals("两截都关着就是空串（调用方不摆那一格）", "", desktopComicHistoryProgressText(record(chapterIndex = -1, pageIndex = -1)))
    }

    @Test
    fun 对岸那颗算式改了这里要跟着红() {
        // 抄过来的东西一定会漂，所以把**原件**钉在判据里：对岸的算式一改，这条先红，而不是屏上悄悄不一样。
        val formula = DesktopSourceTree.androidSource("feature/HistoryViewModel.kt").readText()
        listOf("lastChapterIndex + 1", "lastPageIndex + 1", "\"第 \$ep 话\"", "\"第 \$page 页\"", "\" - \"", "ep >= 1", "page >= 1")
            .forEach { token ->
                assertTrue("对岸那颗里不再出现「$token」—— 桌面这一句要跟着改判，不是悄悄留着：$token", formula.contains(token))
            }
        val emptyScreen = DesktopSourceTree.androidSource("feature/HistoryScreen.kt").readText()
        // 锚在 `VeneraEmptyView(` 那一整块上，不锚在"文件里第一个 title ="：
        // 那一屏别处也有 `title = "…"`，拿第一处会把顶栏题名当成空态那句（假绿）。
        val emptyView = Regex("VeneraEmptyView\\(\\s*title = \"([^\"]+)\"\\s*,\\s*message = \"([^\"]+)\"").find(emptyScreen)
            ?: error("对岸的空态不再是 `VeneraEmptyView(title = …, message = …)` 那一块")
        assertEquals("空态标题抄对岸那一句", emptyView.groupValues[1], DESKTOP_HISTORY_EMPTY_TITLE)
        assertEquals("空态副句抄对岸那一句", emptyView.groupValues[2], DESKTOP_HISTORY_EMPTY_MESSAGE)
    }

    @Test
    fun 读数那一行分得开空态与读不成() {
        // 批 82.8 起这一格多一枚 `名=`：那一行点不点得动由"键或显示名"两档决定，
        // 读数必须念得出当时拿哪两种东西在比（名字表没命中时那一格是空的，那也是一种形状）。
        assertEquals("D_历史 屏 源=jm 名=禁漫天堂 行=3", desktopComicHistoryScreenReading("jm", "禁漫天堂", 3, null))
        assertEquals("D_历史 屏 源=jm 名= 行=0", desktopComicHistoryScreenReading("jm", "", 0, null))
        val failed = desktopComicHistoryScreenReading("jm", "禁漫天堂", 0, "IllegalStateException: 端口没装")
        assertTrue("读不成必须念成因：$failed", failed.contains("因=IllegalStateException"))
        assertTrue("行数是几就念几（读不成时不许把 0 说成「没有记录」）：$failed", failed.contains("行=0"))
        assertEquals("读数不许带换行", 1, failed.lines().size)
    }

    @Test
    fun 首页漫画域那行从今天起点得动() {
        val rows = desktopComicRows()
        // 批 83 添了第四行「源管理」⇒ 这一条从"三行"改成"四行"，并逐行点名（只数颗数的话，
        // 谁被换掉都看不出来 —— 那是本仓那一族"计数判据放过内容漂移"的形状）。
        // 批 96/97 之后是五行：添的那一行是「本机已下载」，它**追加在末尾**而不是插进中间。
        // ⚠️ 本条从前用 `rows[2]` / `rows[3]` 按**索引**点名，而"插中间会把这两枚指到别的话上去"这件事
        // 正是这一族自己刚踩过的形状（批 94 那条「行号会漂」同形）⇒ 改成按 key 找：以后谁插在哪都点得准。
        // 批 96/97 之后是五行，第一百零五批之后是六行：添的那两行都**追加在末尾**而不是插进中间。
        assertEquals("漫画域从今天起六行（探索 / 收藏夹 / 阅读历史 / 源管理 / 本机已下载 / 下载队列）", 6, rows.size)
        val history = rows.first { it.key == "comic-history" }
        assertEquals("阅读历史", history.title)
        assertEquals("转正的那一行必须带内容件而不是理由", DesktopGallerySectionContent.ComicHistory, history.content)
        assertEquals("已转正的那一行不许再留「未实现」那句", null, history.reason)
        val sources = rows.first { it.key == "comic-sources" }
        assertEquals("源管理", sources.title)
        assertEquals(DesktopGallerySectionContent.ComicSources, sources.content)
        assertEquals("源管理那一屏是真屏，不许挂「未实现」的理由", null, sources.reason)
        // 批 97 那一行：写侧在批 96 已经落地，这一行是它缺的**读侧**，所以它同样不许挂「未实现」。
        val downloads = rows.first { it.key == "comic-downloads" }
        assertEquals("本机已下载", downloads.title)
        assertEquals(DesktopGallerySectionContent.ComicDownloads, downloads.content)
        assertEquals("那一屏是真屏（数盘上那一棵树），不许留「未实现」的理由", null, downloads.reason)
    }

    @Test
    fun 两行历史各读各的那一份() {
        // 从前这一枚钉的是「图库那一行的理由不再念那一笔没写」—— 第八十六批那一行点亮了，那句跟着撤，
        // 「转正了没」由 `DesktopGalleryHistorySeedTest` ①②③ 钉（同一件事只许一把尺子）。
        // 这里留下的是**只有这一枚管得着**的那一条：两行历史读的是两份不同的数据，不许谁并谁。
        val galleryHistory = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryBrowseHistory.kt"),
        )
        val comicHistory = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicHistory.kt"),
        )
        assertTrue("漫画那一颗不再写 comic_history 那张表了（并档的那一步就会把两件事记成一件）",
            comicHistory.contains("HistoryRecord"))
        assertTrue("图库那一颗不落自己那份 JSON 档了", galleryHistory.contains("gallery_browse_history.json"))
        assertFalse("图库那一颗开始写漫画那张表了（两张表不是同一件事）", galleryHistory.contains("comic_history"))
    }

    @Test
    fun 异源那一行不做成点得动的样子() {
        // 判式只看代码行。这里钉的是"那一下挂在一个由 guard 决定的分支上"，不是"文件里有某个符号"——
        // 后者会被一颗定义了却没被用的 guard 喂饱（接线类判据的老坑）。
        val body = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicHistoryScreen.kt"))
        // 数的是 `Modifier.clickable` 而不是裸的 ".clickable"：后者会被 import 那一行喂饱
        // （`import androidx.compose.foundation.clickable` 里就含 ".clickable"，量具自己咬自己一口）。
        val clickableLines = body.lines().filter { it.contains("Modifier.clickable") }
        assertEquals("那一屏只有卡位可点，而点得动的只许是同趟那一种", 1, clickableLines.size)
        val line = clickableLines[0]
        assertTrue("那一下必须挂在 canOpen 那一句上，不是无条件挂：$line", line.contains("canOpen"))
        assertTrue("guard 的名字必须真从那颗纯件来：$line", line.contains("onOpen("))
        assertEquals(
            "行开不开得动只许问这一颗件",
            1,
            // 钉 `rec,` 那一枚**调用点前缀**而不是整张实参表：从前钉的是 `(rec, sourceKey)`，
            // 加一枚入参就归零（坑 ㉖ 那一族）；而裸的 `(rec,` 不会把定义那一行也数进来。
            body.lines().count { it.contains("desktopComicHistoryRowCanOpen(rec,") },
        )
    }

    // ─────────────────── 批 82.8：那一行"是不是当前源"的口径与对岸同轨 ───────────────────

    @Test
    fun 手机写的显示名在桌面这一侧算当前源() {
        // 对岸往 `comic_history.source_name` 写的是**显示名**（`VeneraReaderScreen.kt:445` 递的是
        // `session.sourceName`），而桌面从前只认键 ⇒ 手机记的那一行摆着而按不动。
        assertTrue(
            "库里写着「拷贝漫画」而当前源是 copy_manga ⇒ 这一行该点得动",
            desktopComicHistoryRowCanOpen(record(sourceName = "拷贝漫画"), "copy_manga", "拷贝漫画"),
        )
    }

    @Test
    fun 键那一档照旧算当前源() {
        // 桌面自己写的是键（`DesktopComicHistory.kt:51`）—— 这一档不许被改坏。
        assertTrue(desktopComicHistoryRowCanOpen(record(sourceName = "jm"), "jm", "禁漫天堂"))
    }

    @Test
    fun 两档都吃大小写不敏感() {
        // 对岸那条口径是 `equals(…, ignoreCase = true)`，而随包 34 颗里就有两枚脚本键与文件名只差大小写
        //（`komiic → Komiic`、`manhuagui → ManHuaGui`）。
        assertTrue("键那一档", desktopComicHistoryRowCanOpen(record(sourceName = "JM"), "jm", "禁漫天堂"))
        assertTrue("显示名那一档", desktopComicHistoryRowCanOpen(record(sourceName = "Komiic"), "komiic", "Komiic"))
    }

    @Test
    fun 别颗源的显示名不许算成当前源() {
        assertTrue(
            "拿别的源的显示名冒充当前源，点下去就是拿错的源问同一个号",
            !desktopComicHistoryRowCanOpen(record(sourceName = "禁漫天堂"), "copy_manga", "拷贝漫画"),
        )
    }

    @Test
    fun 显示名缺位时不许把空串当通行证() {
        // 名字表没命中那一格是**退化输入**：这时只许按键认。写成 `record.sourceName.equals(sourceName, true)`
        // 而不挡空串，库里那行源名恰好也是空的话就被放行了。
        assertTrue("库里空源名对上空显示名", !desktopComicHistoryRowCanOpen(record(sourceName = ""), "jm", ""))
        assertTrue("库里空源名对不上键", !desktopComicHistoryRowCanOpen(record(sourceName = ""), "jm", "禁漫天堂"))
    }

    @Test
    fun 对岸那条键或显示名的口径改了这里要跟着红() {
        // 同轨不是嘴上说的：把对岸那两枚 `equals(…, ignoreCase = true)` 钉在判据里，它一改这条先红。
        val resolve = DesktopSourceTree.androidSource("feature/ComicDetailViewModel.kt").readText()
        listOf(
            "it.key.equals(sourceNameOrKey, ignoreCase = true)",
            "it.name.equals(sourceNameOrKey, ignoreCase = true)",
        ).forEach { token ->
            assertTrue("对岸 `resolveSourceKey` 不再含「$token」—— 桌面这一颗要跟着改判，不许悄悄留着：$token", resolve.contains(token))
        }
    }

    @Test
    fun 那句说明不再说启动而点名去哪儿换() {
        // 两笔假话：批 83 之后换源不必重启（源管理那一屏运行期就换），而 82.8 之后显示名的行本来就点得动。
        assertTrue("屏上不许再念「启动」：$DESKTOP_HISTORY_OTHER_SOURCE_NOTE", !DESKTOP_HISTORY_OTHER_SOURCE_NOTE.contains("启动"))
        assertTrue("要指出去哪儿换：$DESKTOP_HISTORY_OTHER_SOURCE_NOTE", DESKTOP_HISTORY_OTHER_SOURCE_NOTE.contains("源管理"))
    }

    @Test
    fun 那一屏真拿到了当前源的显示名() {
        // 接线而不是"文件里有这个名字"：域 body 递进历史那一屏的必须是源管理那一张表折出来的名字。
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicPane.kt"))
        assertEquals("当前源的显示名没递进历史那一屏", 1, pane.split("DesktopComicHistoryPane(activeConfig, sources.").size - 1)
    }
}
