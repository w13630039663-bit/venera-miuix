package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 82.7 第二步 · 漫画域那一屏**由谁渲染**（纯函数层）。
 *
 * ## 这一颗为什么必须存在（不是补测试，是补一条拍到的缺陷）
 *
 * 缺陷读数（同一批第一步补的量具拍出来的，手册行 124）：
 * `D_落点 域=漫画(启动) 行=阅读历史 … 详情=开(启动)` 而 `D_详情` **一个字都没念**，帧上屏的是历史那一屏
 * —— 详情与阅读两屏从前只渲染在 `ComicExplorePane` 里面，于是**从「阅读历史」那一行按一行**：
 * 载荷设上了、屏不换、也不报错。批 82 那屏"点得动"只量到回调那一层。
 *
 * ## 为什么把判断抽成跑得动的纯函数而不是留在组合里
 *
 * 组合那一层要真引擎才能起（`rememberComicEngine` 会建 GraalJS 与打网络），离屏渲不出来；
 * 而"载荷在时不许看行"这一格恰恰是**逻辑**，逻辑就该用逻辑的量具钉。
 * 同一族先例：批 30 的"接管落点"、批 36 的"浮层接管"都是先抽成纯函数才量得到。
 */
class DesktopComicRowDispatchTest {

    private val explore = DesktopGallerySectionContent.ComicExplore
    private val favorites = DesktopGallerySectionContent.ComicFavorites
    private val history = DesktopGallerySectionContent.ComicHistory

    @Test
    fun 开着详情时不许再看是哪一行() {
        // 这一条就是缺陷本身：以前"行=阅读历史"会把详情吃掉（那一屏只在探索那一支里组合）。
        assertEquals(DesktopComicScreen.DETAIL, desktopComicScreen(history, hasComic = true, hasRead = false))
        assertEquals(DesktopComicScreen.DETAIL, desktopComicScreen(explore, hasComic = true, hasRead = false))
        assertEquals(DesktopComicScreen.DETAIL, desktopComicScreen(favorites, hasComic = true, hasRead = false))
    }

    @Test
    fun 阅读比详情更高一层而两枚各管一档() {
        assertEquals(DesktopComicScreen.READER, desktopComicScreen(history, hasComic = true, hasRead = true))
        assertEquals(
            "只给阅读载荷（`--read=` 那颗种子那一趟）也直接落阅读屏",
            DesktopComicScreen.READER,
            desktopComicScreen(explore, hasComic = false, hasRead = true),
        )
        assertEquals(
            "两枚都没给时才按行分岔：三行各回自己那一屏",
            DesktopComicScreen.HISTORY,
            desktopComicScreen(history, hasComic = false, hasRead = false),
        )
        assertEquals(DesktopComicScreen.EXPLORE, desktopComicScreen(explore, hasComic = false, hasRead = false))
        assertEquals(DesktopComicScreen.FAVORITES, desktopComicScreen(favorites, hasComic = false, hasRead = false))
    }

    @Test
    fun 只有真要问源站的那几屏才起引擎() {
        // 「收藏」「阅读历史」两屏读的是本地库，白起一颗 GraalJS + 整源装载是代价不是准备。
        // 第一百零六批：`hasQueueWork` 这一路只喂 false —— 「那一行有没有活」的两档由
        // `DesktopComicQueueRowPumpTest` ①/② 钉，这里不双轨钉同一件事。
        assertFalse(desktopComicEngineNeeded(DesktopComicScreen.HISTORY, hasQueueWork = false))
        assertFalse(desktopComicEngineNeeded(DesktopComicScreen.FAVORITES, hasQueueWork = false))
        assertTrue("探索那一屏的墙要图要卡", desktopComicEngineNeeded(DesktopComicScreen.EXPLORE, hasQueueWork = false))
        assertTrue("详情要 `loadInfo`", desktopComicEngineNeeded(DesktopComicScreen.DETAIL, hasQueueWork = false))
        assertTrue("阅读要取页", desktopComicEngineNeeded(DesktopComicScreen.READER, hasQueueWork = false))
    }

    @Test
    fun 两屏的渲染点从探索那一支提到域层() {
        val pane = DesktopSourceTree.desktopUiSource("DesktopComicPane.kt")
        val code = DesktopSourceTree.codeText(pane)
        assertEquals("详情那一屏全树只许组合一次", 1, code.split("DesktopComicDetailPane(").size - 1)
        assertEquals("阅读那一屏全树只许组合一次", 1, code.split("DesktopComicReaderPane(").size - 1)
        assertTrue("域层必须走那颗纯判断（不写死 if/else 形状，判式只钉它真被调过）", code.contains("desktopComicScreen("))
        assertTrue("引擎要不要起也必须走那颗纯判断", code.contains("desktopComicEngineNeeded("))
        // 反向那一半：探索那一支不再管载荷 —— 留着就是"两条路都能渲染同一屏"，
        // 而缺陷的形状正是"只有那一条路"。
        // ⚠️ 判式取**整张参数表**（从函数头到第一行函数体），不取 `ComicExplorePane(…comicTarget` 那种
        // 一带而过的串：那种写法只咬得住"载荷排在参数表前部"，把它添在 `onOpenComic` **之后**
        // 就绕过去了（`[^)]*` 吃不过 `(DesktopComicTarget)` 里那个右括号 —— 扰动台架 C7 就是拿来咬这一格的）。
        val declStart = code.indexOf("private fun ComicExplorePane(")
        val declEnd = code.indexOf("val state = engine.state")
        assertTrue("探索那一屏的函数头不见了（改了名？那这一格得跟着改）", declStart in 0 until declEnd)
        assertFalse(
            "探索那一支的参数表里不许再出现详情载荷（渲染点已提到域层）",
            code.substring(declStart, declEnd).contains("comicTarget"),
        )
    }

    @Test
    fun 退场那颗不许跟着分支走() {
        val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicPane.kt"))
        assertEquals("引擎句柄的 dispose 全树恰好一处", 1, code.split("onDispose { queueScope.cancel(); closing?.close() }").size - 1)
        assertTrue(
            "dispose 必须挂在引擎那颗持有件里，而不是探索那一屏的 UI 段（否则换行就漏一颗）",
            code.indexOf("private fun rememberComicEngine(") in 0 until code.indexOf("onDispose { queueScope.cancel(); closing?.close() }"),
        )
    }
}
