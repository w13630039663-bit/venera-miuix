package com.venera.desktop.gallery.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.venera.compose.data.db.HistoryRecord
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 批 82 · 阅读历史那一屏的**离屏语义树**判据。
 *
 * 渲的是 [DesktopComicHistoryContent]（吃现成的行表），不是 pane —— pane 要真开库。
 * 这一层的三条最要紧：**空态与读不成念两种话**、**异源那一行按不动**、**同源那一行按一次恰好落一次**。
 *
 * ⚠️ 三行各喂**不同**的进度数字：`onNodeWithText(...)` 认的是"恰好一枚"，三行同式的话
 * 那一句会一次匹配到三枚，红得看不出是哪一行的账（这一族在批 80 就踩过一次）。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopComicHistoryScreenSemanticsTest {

    private fun record(id: String, title: String, source: String = "jm", chapter: Int = 1, page: Int = 7) =
        HistoryRecord(
            comicId = id,
            title = title,
            author = "",
            coverUrl = "",
            sourceName = source,
            lastChapterTitle = "第 ${chapter + 1} 话",
            lastChapterIndex = chapter,
            lastPageIndex = page,
            totalPages = 89,
            updatedAt = 1_700_000_000_000L,
        )

    @Test
    fun 三行各摆一格而题名进度与源名都在() {
        runComposeUiTest {
            setContent {
                DesktopComicHistoryContent(
                    sourceKey = "jm",
                    sourceName = "禁漫天堂",
                    records = listOf(
                        record("1", "第一本", chapter = 1, page = 7),
                        record("2", "第二本", source = "goda", chapter = 2, page = 3),
                        record("3", "第三本", chapter = 0, page = 0),
                    ),
                    note = null,
                    onOpen = {},
                )
            }
            listOf("第一本", "第二本", "第三本").forEach {
                onNodeWithText(it).assertExists("三行该各摆一格，缺的是$it")
            }
            // 进度那一句各按对岸那颗算式（第 N 话 - 第 M 页，都是从 1 数起的人话）
            listOf("第 2 话 - 第 8 页", "第 3 话 - 第 4 页", "第 1 话 - 第 1 页").forEach {
                onNodeWithText(it).assertExists("进度那一格念错的是$it")
            }
            // 异源那一行的源名与那句说明是同一枚 Text（"goda · 这一行是别的源记的…"），所以按子串认
            onNodeWithText("goda", substring = true).assertExists("异源那一行也要念出自己是谁的源")
            onNodeWithText(DESKTOP_HISTORY_EMPTY_TITLE).assertDoesNotExist()
        }
    }

    @Test
    fun 零行时空态那两句上屏() {
        runComposeUiTest {
            setContent {
                DesktopComicHistoryContent(sourceKey = "jm", sourceName = "禁漫天堂", records = emptyList(), note = null, onOpen = {})
            }
            onNodeWithText(DESKTOP_HISTORY_EMPTY_TITLE).assertExists()
            onNodeWithText(DESKTOP_HISTORY_EMPTY_MESSAGE).assertExists()
        }
    }

    @Test
    fun 读不成那一档念失败而不是念空态() {
        // 这一条是这一屏的假开关 catcher：把 note 折进"没有记录"那一支，屏上就是一句实话形状的美话，
        // 而库里其实有行、只是今天没读到（第 57 批「一笔都不发」那一族的老账）。
        runComposeUiTest {
            setContent {
                DesktopComicHistoryContent(
                    sourceKey = "jm",
                    sourceName = "禁漫天堂",
                    records = emptyList(),
                    note = "IllegalStateException: 端口没装",
                    onOpen = {},
                )
            }
            onNodeWithText("IllegalStateException: 端口没装", substring = true).assertExists("成因要上屏")
            onNodeWithText(DESKTOP_HISTORY_EMPTY_TITLE).assertDoesNotExist()
        }
    }

    @Test
    fun 同源那一行按一次交回卡位() {
        val opened = mutableListOf<DesktopComicTarget>()
        runComposeUiTest {
            setContent {
                DesktopComicHistoryContent(
                    sourceKey = "jm",
                    sourceName = "禁漫天堂",
                    records = listOf(record("77", "这一本"), record("88", "那一本", source = "goda")),
                    note = null,
                    onOpen = { opened += it },
                )
            }
            onNodeWithText("这一本").performClick()
            assertEquals("按一行恰好落一次", 1, opened.size)
            assertEquals("77", opened[0].comicId)
            assertEquals("题名用库里那一行记着的（不是现取，取不到才是空）", "这一本", opened[0].title)
        }
    }

    @Test
    fun 异源那一行按不动而屏上说清为什么() {
        val opened = mutableListOf<DesktopComicTarget>()
        runComposeUiTest {
            setContent {
                DesktopComicHistoryContent(
                    sourceKey = "jm",
                    sourceName = "禁漫天堂",
                    records = listOf(record("88", "别源的", source = "goda")),
                    note = null,
                    onOpen = { opened += it },
                )
            }
            onNodeWithText("别源的").performClick()
            assertEquals("漫画域这一趟只装了 jm，拿错的源去问同一个号只会问出别的东西", 0, opened.size)
            onNodeWithText(DESKTOP_HISTORY_OTHER_SOURCE_NOTE, substring = true).assertExists()
        }
    }

    /**
     * 批 82.8 的正题：手机那一侧写进库的是**显示名**，而这一屏从前只认键 ⇒ 这一行摆着按不动。
     * 同轨之后它必须点得动，而且**不许**再被挂上那句"别的源记的"。
     */
    @Test
    fun 手机写的那一行按得动而不念那句说明() {
        val opened = mutableListOf<DesktopComicTarget>()
        runComposeUiTest {
            setContent {
                DesktopComicHistoryContent(
                    sourceKey = "copy_manga",
                    sourceName = "拷贝漫画",
                    records = listOf(record("99", "手机记的那本", source = "拷贝漫画")),
                    note = null,
                    onOpen = { opened += it },
                )
            }
            onNodeWithText("手机记的那本").performClick()
            assertEquals("库里写着显示名而当前源是它的键 ⇒ 这一行该点得动", 1, opened.size)
            assertEquals("99", opened[0].comicId)
            onNodeWithText(DESKTOP_HISTORY_OTHER_SOURCE_NOTE, substring = true).assertDoesNotExist()
        }
    }

    /** 名字表没命中那一格（清单读不出来时）：只许按键认，那一行照旧按不动并说清为什么。 */
    @Test
    fun 显示名缺位时那一行仍然按不动() {
        val opened = mutableListOf<DesktopComicTarget>()
        runComposeUiTest {
            setContent {
                DesktopComicHistoryContent(
                    sourceKey = "copy_manga",
                    sourceName = "",
                    records = listOf(record("99", "只有显示名", source = "拷贝漫画")),
                    note = null,
                    onOpen = { opened += it },
                )
            }
            onNodeWithText("只有显示名").performClick()
            assertEquals("显示名缺位时不许把这一行放行", 0, opened.size)
            onNodeWithText(DESKTOP_HISTORY_OTHER_SOURCE_NOTE, substring = true).assertExists()
        }
    }
}
