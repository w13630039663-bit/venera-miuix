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

}
