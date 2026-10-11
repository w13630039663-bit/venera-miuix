package com.venera.desktop.gallery.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.venera.desktop.gallery.data.DesktopCookieOutcome
import com.venera.desktop.gallery.data.DesktopSourceRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 批 83 · 源管理那一屏的**离屏语义树**判据（不起窗、不动光标）。
 *
 * 渲的是 [DesktopComicSourcesPane]（吃现成的 [DesktopSourcesState]），而不是 `rememberDesktopSources`
 * 那颗持有件 —— 持有件要真数据目录与真清单，而这一层要验的恰恰是"屏上念不念得对、按下去落不落"。
 * 折算法本身（停用 → 落回另一颗）由 `DesktopSourceCatalogTest` 那 12 条纯判据钉，
 * 这里补的是它盖不到的两格：**那两句"没生效"必须上屏**、**按一次恰好落一次**。
 *
 * ⚠️ 三行各喂**不同**的题名：`onNodeWithText` 认的是"恰好一枚"，同名会一次匹到多枚而红得看不出是哪一行。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopComicSourcesSemanticsTest {

    private fun state(
        rows: List<DesktopSourceRow>,
        activeKey: String,
        pick: (String) -> String? = { null },
        toggle: (String) -> String? = { null },
        saveNote: String? = null,
        cookieFields: Map<String, List<String>> = emptyMap(),
    ) = DesktopSourcesState(
        rows = rows,
        activeKey = activeKey,
        disabledCount = rows.count { !it.enabled },
        total = rows.size,
        pick = pick,
        toggle = toggle,
        saveNote = saveNote,
        cookieFields = cookieFields,
    )

    /**
     * 这一族离屏用例**不跑登录**：那一趟要起 GraalJS 与真网络（`DesktopCookieLogin.run`），
     * 而这里要验的只是"格子摆不摆得出来、按下去交的是哪几枚值"。
     */
    private val fakeLogin: suspend (String, List<String>) -> DesktopCookieOutcome =
        { _, _ -> DesktopCookieOutcome(false, "这一条用例不跑登录", emptyList(), emptyMap()) }

    private val three = listOf(
        DesktopSourceRow("jm", "禁漫天堂", enabled = true),
        DesktopSourceRow("goda", "绅士漫画", enabled = false),
        DesktopSourceRow("nhentai", "nhentai 站", enabled = true),
    )

    @Test
    fun 三行各摆一格而在用与已停用各标各的() {
        runComposeUiTest {
            setContent { DesktopComicSourcesPane(state(three, activeKey = "jm"), fakeLogin) }
            listOf("禁漫天堂", "绅士漫画", "nhentai 站").forEach {
                onNodeWithText(it, substring = true).assertExists("三行该各摆一格，缺的是$it")
            }
            // 「在用」只许有一枚（多一枚就是两行同时被点名）
            assertEquals("在用的标记数不对", 1, onAllNodesWithText("在用").fetchSemanticsNodes().size)
            assertEquals("停用的标记只许落在关着的那一行", 1, onAllNodesWithText("已停用").fetchSemanticsNodes().size)
            // 取到名字的行念名字，而 key 也得上屏（改名口径要看得见）
            onNodeWithText("（goda）", substring = true).assertExists("停用着的那一行也要念得出自己的 key")
        }
    }

    /**
     * 那一屏得念得出数，而**内部读数不许上屏**。
     *
     * ⚠️ 这一条是图证抓回来的：第一版把 stdout 那句 `D_源 停用=0 清单=34 口径=桌面本地…` 原样摆上界面，
     * 判据全绿而屏上是一行黑话（`D_` 前缀 + 表名）。⇒ 屏上走人话，`D_` 只进 stdout，
     * 并且这里钉一条负向的：任何以 `D_` 开头的字面量都不许出现在这一屏。
     */
    @Test
    fun 数念得出而内部读数不许上屏() {
        runComposeUiTest {
            setContent { DesktopComicSourcesPane(state(three, activeKey = "jm"), fakeLogin) }
            onNodeWithText("清单共 3 颗，这一台机器上停用了 1 颗").assertExists("停用数与清单数得念得出")
            // （`assertDoesNotExist()` 不收消息 —— 那两句失败时靠上面那行断言的名字定位）
            onNodeWithText("D_", substring = true).assertDoesNotExist()
            onNodeWithText("comic_source", substring = true).assertDoesNotExist()
        }
        runComposeUiTest {
            setContent {
                DesktopComicSourcesPane(
                    state(three.map { it.copy(enabled = true) }, activeKey = "jm"),
                    fakeLogin,
                )
            }
            onNodeWithText("一颗都没停用", substring = true).assertExists("零停用要说成一句人话，而不是念「停用了 0 颗」")
        }
    }

    @Test
    fun 按一次停用恰好落一次在那一颗头上() {
        val hits = mutableListOf<String>()
        runComposeUiTest {
            setContent {
                DesktopComicSourcesPane(
                    state(three, activeKey = "jm", toggle = { hits += it; null }),
                    fakeLogin,
                )
            }
            // 启用着的两行才摆「停用」那枚（关着的那行摆的是「启用」）
            assertEquals("该摆两枚「停用」", 2, onAllNodesWithText("停用").fetchSemanticsNodes().size)
            onAllNodesWithText("停用")[0].performClick()
            assertEquals("按一次落一次，且落在被按的那一颗", listOf("jm"), hits)
            onAllNodesWithText("停用")[1].performClick()
            assertEquals("第二按落在第二颗头上而不是又回到第一颗", listOf("jm", "nhentai"), hits)
        }
    }

    @Test
    fun 最后一颗关不掉时那句上屏而不是默默吞掉() {
        runComposeUiTest {
            setContent {
                DesktopComicSourcesPane(
                    state(three, activeKey = "jm", toggle = { "这是最后一颗启用着的源，再关就没有可装的源了" }),
                    fakeLogin,
                )
            }
            onAllNodesWithText("停用")[0].performClick()
            onNodeWithText("最后一颗启用着的源", substring = true).assertExists("没生效又什么都不说 = 用户读到的是按了没反应")
        }
    }

    @Test
    fun 停用状态没落盘时那句上屏() {
        runComposeUiTest {
            setContent {
                DesktopComicSourcesPane(
                    state(three, activeKey = "jm", saveNote = "停用状态没落盘：IllegalStateException"),
                    fakeLogin,
                )
            }
            onNodeWithText("停用状态没落盘", substring = true).assertExists("写盘失败只进 stdout 的话，屏上看着是关好了的")
        }
    }

    @Test
    fun 选停用着的那一颗时那句上屏而不许把它换成当前源() {
        val picked = mutableListOf<String>()
        runComposeUiTest {
            setContent {
                DesktopComicSourcesPane(
                    state(
                        three,
                        activeKey = "jm",
                        pick = { key -> if (key == "goda") "「绅士漫画」是停用着的，要先把它的停用点开" else { picked += key; null } },
                    ),
                    fakeLogin,
                )
            }
            onNodeWithText("绅士漫画", substring = true).performClick()
            onNodeWithText("是停用着的", substring = true).assertExists("点不动的那一句必须看得见")
            assertEquals("停用着的那颗不许被选为当前源", emptyList<String>(), picked)
            onNodeWithText("nhentai 站", substring = true).performClick()
            assertEquals("启用着的那一颗按一次恰好落一次", listOf("nhentai"), picked)
        }
    }

    /**
     * 接线那一格（离屏渲不出持有件，只能扫源码钉）：域 body 必须把**折完之后**的那颗 key 交给引擎。
     *
     * ⚠️ 只钉"出现过 `sources.activeKey`"不够 —— 那一句在别处出现也算数；钉的是引擎那一次的实参形状。
     */
    @Test
    fun 引擎吃的是折过停用之后那颗key而不是启动时那颗() {
        val pane = DesktopSourceTree.desktopUiSource("DesktopComicPane.kt")
        val code = DesktopSourceTree.codeText(pane)
        assertEquals("当前源没被折进装载那颗参数", 1, code.split("config.copy(sourceKey = sources.activeKey)").size - 1)
        assertEquals("引擎没吃折完的那颗", 1, code.split("rememberComicEngine(activeConfig").size - 1)
        assertEquals(
            "源管理那一屏没挂上分岔（列表就在屏上而没人渲染它）",
            1,
            // 只钉"分岔里调的是这一颗件"，不钉整串实参：那一屏往后添参数时这条不该红
            //（坑 ㉖ 的正解 —— 红在字面不在行为上的判据，改了也不算测到）。
            code.split("SOURCES -> DesktopComicSourcesPane(").size - 1,
        )
        // 持有件那一格：「选它」那一下得真的改写 picked。只钉 pick 回调存在不够 ——
        // 回调里写 `picked = config.sourceKey`（等于没写）编译与屏上都看不出差别。
        // ⚠️ 那一行住在**展示件那颗文件**里（`rememberDesktopSources` 与屏在同一颗），不在域 body：
        //    第一版在这里扫 `DesktopComicPane.kt`，于是这一格**恒红**（clean 树里 `picked` 一个都没有），
        //    还顺带把台架 14 笔的每一笔都染红 —— 那是量具坏掉，不是尺子咬住（见 rounds §9）。
        val screen = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopComicSourcesScreen.kt"))
        assertEquals("「选它」那一下没真的改当前源", 1, screen.split("picked = key").size - 1)
    }

    /**
     * 批 82.8 那一格的名字来源：`rows` 的题名在名字表没命中时**退回键**（`DesktopSourceCatalog.rows` 就这么写的），
     * 而历史那一屏拿它做"键或显示名任一命中"的判断 ⇒ `nameOf` 必须把那种退回**折回空串**，
     * 否则"没名字"会被读成"名字恰好等于键"，那一格看着像命中而其实没有名字表撑着。
     */
    @Test
    fun 名字缺位时那一格交回空串而不是键() {
        val s = state(three + DesktopSourceRow("noname", "noname", enabled = true), activeKey = "noname")
        assertEquals("题名等于键就是「没名字」那一档", "", s.nameOf("noname"))
        assertEquals("有名字的那一颗照回显示名", "禁漫天堂", s.nameOf("jm"))
    }
}
