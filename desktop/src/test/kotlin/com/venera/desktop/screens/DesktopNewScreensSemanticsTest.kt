package com.venera.desktop.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.data.db.FavoriteRecord
import com.venera.desktop.detail.DesktopComicDetailScreen
import com.venera.desktop.explore.DesktopExploreScreen
import com.venera.desktop.favorites.DesktopFavoritesScreen
import com.venera.desktop.gallery.data.DesktopComicPage
import com.venera.desktop.reader.DesktopReaderScreen
import com.venera.desktop.search.DesktopSearchScreen
import com.venera.desktop.sourcemanage.DesktopSourceManageScreen
import com.venera.desktop.sourcemanage.DesktopSourceRowUi
import com.venera.engineprobe.ComicInfo
import com.venera.engineprobe.EngineSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-21 那批六个新屏的**离屏语义树**判据。
 *
 * ## 为什么必须有这一层（进度清单 §下一步 1 那一笔账）
 *
 * 那批加了 6 颗屏，而 `:desktop:test` 的条数**一条没涨**（1076 → 1076）——
 * 也就是那 6 屏没有任何回归保护。纯函数判据只能验算式，源码扫描判据只能验接线形状，
 * 两层都盖不住「返回值有没有反映到屏上」。本仓在批 81 那一课记得很清楚：
 * 屏上写着「第 1 话」而那一行是死的，什么都对，缺的只有那一下回调。
 *
 * ## 点击走哪一条路（这台架量出来的，不是推的）
 *
 * 三条量证（2026-10-21，`D_探针4`）：
 *  - `VeneraButton` / `VeneraCard` 在**使能态**下 `performClick` 落得到回调；
 *  - 在**禁用态**（`enabled = false`）下 `performClick` **落不到**（tick 不动）——
 *    所以"按下去不该写库"这类判据只有 `performClick` 能钉；
 *  - `performSemanticsAction(OnClick)` 在禁用态**照样落得到**（tick 动了）——
 *    它绕过了使能闸门，因此本文件**一律不用**它：用它去钉"禁用时不写库"会钉出一条假绿。
 *
 * ## 视口那一格要当心
 *
 * 同一天量到的：Compact（单栏）时详情那两行章节只有**第一行**进了组合树
 * （`第 2 话` 读不到），Expanded（宽屏两栏）两行都在。要看第二行的判据必须用 Expanded。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopNewScreensSemanticsTest {

    private val wide = WideScreenLayoutMode.Expanded
    private val narrow = WideScreenLayoutMode.Compact

    private fun info(episodes: List<EngineSession.Episode> = listOf(
        EngineSession.Episode("第 1 话", "e1"),
        EngineSession.Episode("第 2 话", "e2"),
        EngineSession.Episode("占位那话", "e3", placeholder = true),
    )) = ComicInfo(title = "某本漫画", description = "一句话简介", episodes = episodes, cover = "https://d.jpg")

    private fun cards(n: Int) = (1..n).map { EngineSession.ComicCard("第${it}本", "c$it", "https://e.invalid/$it.jpg") }

    private fun fav(id: String, title: String) = FavoriteRecord(
        comicId = id, title = title, author = "", coverUrl = "", sourceName = "jm",
    )

    /** 屏都铺在一只 fillMaxSize 的 Box 里：离屏那只有固定尺寸，不给约束会量不到滚动容器里的东西。 */
    @Composable
    private fun Stage(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) { content() }
    }

    // ── ① 详情屏 ────────────────────────────────────────────────────────────

    /**
     * 那枚收藏钮的**四档各念一句**（含 `favoriteBusy`），按一次恰好落一次。
     *
     * ⚠️ 写法上的硬规矩（全量回归抓到过一次跨类污染，读数见 `DesktopAccentFillTest`
     * 那颗莫名报 `UncaughtExceptionsBeforeTest` 的形态）：**一颗测试体里只能有一层
     * `runComposeUiTest`**。写成 `= runComposeUiTest { … runComposeUiTest { … } }`
     * 是两只 `runTest` 套在一起，里层退出时留下的未捕获异常会打到**下一颗不相干的测试**上
     * —— 谁被撞全凭执行顺序运气，所以这种写法在本仓等同于"随机弄红别人"。
     * 要多档就平铺成几层各自独立的（下面的 `for` 就是这个形状）。
     */
    @Test
    fun 详情屏收藏钮四档各念一句而按一次恰好落一次() {
        var taps = 0
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopComicDetailScreen(
                        mode = wide, title = "某本漫画", info = info(), note = null, masked = false,
                        favorited = false, favoriteBusy = false,
                        onToggleFavorite = { taps++ }, onBack = {}, onOpenEpisode = {},
                    )
                }
            }
            onNodeWithText("收藏").assertExists()
            onNodeWithText("收藏").performClick()
            assertEquals("按一次该恰好落一次（落两次就是连点会写两遍那一族）", 1, taps)
        }

        // 另外三档各起一层（平铺，不嵌套）：各念各的那句，不许塌成一句。
        for ((favorited, busy, expect) in listOf(
            Triple(true, false, "已收藏 · 点取消"),
            Triple(null, false, "正在读收藏…"),
            Triple(false, true, "正在写收藏…"),
        )) {
            runComposeUiTest {
                setContent {
                    Stage {
                        DesktopComicDetailScreen(
                            mode = wide, title = "某本漫画", info = info(), note = null, masked = false,
                            favorited = favorited, favoriteBusy = busy,
                            onToggleFavorite = {}, onBack = {}, onOpenEpisode = {},
                        )
                    }
                }
                onNodeWithText(expect).assertExists()
            }
        }
    }

    /**
     * **还没读回来时按下去不写**：那一档 `enabled = false`，`performClick` 落不到回调
     * （台架量出来的），所以这里能真钉住「冷启动头几按不会把'还没读回来'当成'没收过'再写一条」。
     */
    @Test
    fun 详情屏收藏没读回来时按下去不写库() = runComposeUiTest {
        var taps = 0
        setContent {
            Stage {
                DesktopComicDetailScreen(
                    mode = wide, title = "某本漫画", info = info(), note = null, masked = false,
                    favorited = null, favoriteBusy = false,
                    onToggleFavorite = { taps++ }, onBack = {}, onOpenEpisode = {},
                )
            }
        }
        onNodeWithText("正在读收藏…").performClick()
        assertEquals("那一档必须按不动：写下去就是往共享表里塞一条用户没想要的收藏", 0, taps)
        // 自检那一下：树真的渲出来了（否则上一个 assertEquals 是"什么都没发生"换来的绿）。
        onNodeWithText("← 返回").assertExists()
    }

    /** 没接写侧（`onToggleFavorite == null`）时**那枚钮不摆** —— 评审 R6 那一族的形状。 */
    @Test
    fun 详情屏没有写口时那枚收藏钮根本不摆() = runComposeUiTest {
        setContent {
            Stage {
                DesktopComicDetailScreen(
                    mode = wide, title = "某本漫画", info = info(), note = null, masked = false,
                    favorited = false, onToggleFavorite = null, onBack = {}, onOpenEpisode = {},
                )
            }
        }
        // 四句档名一句都不许出现：摆了就是"按下去什么都不发生"的假开关。
        listOf("收藏", "已收藏 · 点取消", "正在读收藏…", "正在写收藏…").forEach {
            assertFalse("没有写口却摆了那枚钮：$it", runCatching { onNodeWithText(it).assertExists() }.isSuccess)
        }
    }

    /** 章节表：条数念真值、点一行恰好进一次、占位话那句要上屏而**那一行仍可点**。 */
    @Test
    fun 详情屏章节行按下去恰好进阅读器一次() = runComposeUiTest {
        var opened: EngineSession.Episode? = null
        var count = 0
        setContent {
            Stage {
                DesktopComicDetailScreen(
                    mode = wide, title = "某本漫画", info = info(), note = null, masked = false,
                    favorited = false, onToggleFavorite = {}, onBack = {},
                    onOpenEpisode = { count++; opened = it },
                )
            }
        }
        // 条数按真值念：批 81 那一课（屏上有文字而那一行是死的）在这里的另一形是"数字对不上"。
        onNodeWithText("章节（3）").assertExists()
        onNodeWithText("第 1 话").performClick()
        assertEquals(1, count)
        assertEquals("第 1 话", opened?.name)
        // 占位话：标出来，但**不**变灰 —— 点进去由阅读器如实说取不到页。
        onNodeWithText("占位那话").assertExists()
        onNodeWithText("占位话").assertExists()
        onNodeWithText("占位那话").performClick()
        assertEquals("占位话那一行不许按不动（静默变灰会被读成'坏了'）", 2, count)
    }

    /** 空章节表 + 详情没读回来 + 失败成因：三句各是各的，都不留白。 */
    @Test
    fun 详情屏那三种没内容各念一句而不留白() {
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopComicDetailScreen(
                        mode = wide, title = "某本漫画", info = info(emptyList()), note = null, masked = false,
                        favorited = false, onToggleFavorite = {}, onBack = {}, onOpenEpisode = {},
                    )
                }
            }
            onNodeWithText("章节（0）").assertExists()
            onNodeWithText("这一本没取到章节。可能是源站限流，或这本尚未更新。").assertExists()
        }

        // 第二层另起一颗（不许嵌在上面那层里面，理由见上面那颗的注释）。
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopComicDetailScreen(
                        mode = wide, title = "某本漫画", info = null,
                        note = "读不到这一本：IllegalStateException 装载后取不到 source key: jm",
                        masked = false, favorited = null, onToggleFavorite = {}, onBack = {}, onOpenEpisode = {},
                    )
                }
            }
            // 成因原话上屏（不许折成"加载失败"那种没信息量的套话）。
            onNodeWithText("读不到这一本：IllegalStateException 装载后取不到 source key: jm").assertExists()
            // `info == null` 时章节表那一整段不渲：没有它就没有"章节（0）"那句。
            assertFalse("详情没读回来时不许念'章节（0）'（那是把'还没读'说成'这本没章节'）",
                runCatching { onNodeWithText("章节（0）").assertExists() }.isSuccess)
        }
    }

    /** 返回那一下：入口接上之后每一屏都得有出路（源管理那一屏从前就是单行道）。 */
    @Test
    fun 详情屏返回按一次恰好一次() = runComposeUiTest {
        var backs = 0
        setContent {
            Stage {
                DesktopComicDetailScreen(
                    mode = wide, title = "某本漫画", info = info(), note = null, masked = false,
                    favorited = false, onToggleFavorite = {}, onBack = { backs++ }, onOpenEpisode = {},
                )
            }
        }
        onNodeWithText("← 返回").performClick()
        assertEquals(1, backs)
    }

    // ── ② 阅读器屏 ──────────────────────────────────────────────────────────

    private fun pages(n: Int) = (0 until n).map {
        DesktopComicPage("jm", "c1", "e1", "k$it", it, engine = null)
    }

    /**
     * **页格有高度**（进度清单 §1「页图显示」那格的关键一判）。
     *
     * 那一屏此前四次截图页区全黑，排掉的三个因里第 2 因写的是「裸 `AsyncImage` 高度塌成 0」，
     * 修法是给每项 `aspectRatio(0.7f)` 兜底 —— 但**改完没重跑验证**（清单里那条 ⚠️）。
     * 这一判就是补那次验证：离屏量到的是 **1024×1463**（h/w = 1.43，正是 1/0.7）。
     * 量到 0 高就说明兜底没了，那 74 个 item 又会全黑。
     *
     * ⚠️ 这一判证的是**布局层有高度**，不证"字节取回来了"。字节那一层要真源真网络，
     * 离屏够不着（与 `DesktopReaderKeyTest` 那句"别把离屏按得动写成用户那儿能按"同一口径）。
     */
    @Test
    fun 阅读器每页那一格有实测高度而页码按人看的从1数() = runComposeUiTest {
        setContent {
            Stage {
                DesktopReaderScreen(
                    pages = pages(3), episodeLabel = "第 1 话", note = null, masked = false,
                    currentPage = 0, onPageChange = {}, onBack = {},
                )
            }
        }
        val b = onAllNodes(hasContentDescription("第 1 页"), useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val w = (b.right - b.left).value
        val h = (b.bottom - b.top).value
        println("D_页格 w=${w.toInt()} h=${h.toInt()}")
        assertTrue("页格高度塌成 $h：那颗 aspectRatio 兜底没了（清单 §1 因 2 复发）", h > 100f)
        assertEquals("兜底比例是 0.7（w/h）：量出来 $w×$h", 0.7f, (w / h), 0.05f)
        // 页码从 1 数；空表时那句整条不念（"第 1 / 0 页"是自相矛盾的读数）。
        onNodeWithText("第 1 / 3 页").assertExists()
    }

    @Test
    fun 阅读器空页表那三种成因各念一句() {
        for ((note, expect) in listOf(
            null to "这一话没取到页。可能是源站限流，或这一话需要登录。",
            "源站这一话回了 0 页。" to "源站这一话回了 0 页。",
            "取页失败：IllegalStateException 取页失败 页=3" to "取页失败：IllegalStateException 取页失败 页=3",
        )) {
            runComposeUiTest {
                setContent {
                    Stage {
                        DesktopReaderScreen(
                            pages = emptyList(), episodeLabel = "第 1 话", note = note, masked = false,
                            currentPage = 0, onPageChange = {}, onBack = {},
                        )
                    }
                }
                onNodeWithText(expect).assertExists()
                assertFalse("空表时不许念页码：第 x / 0 页", runCatching { onNodeWithText("第 1 / 0 页").assertExists() }.isSuccess)
            }
        }
    }

    /**
     * 滚动落进 `onPageChange`（写历史那一跳的唯一上游）。
     *
     * ⚠️ 第一版这里写的是"订阅落地就会报一次当前值"，跑出来 `seen=[]` 当场红 ——
     * 那条假设是错的，而且错得有价值：`snapshotFlow { firstVisibleItemIndex }` 后面跟着
     * `if (idx != currentPage) onPageChange(idx)`，**初值相同就不会发**（那是有意的，
     * 不然每次进阅读器都白写一次历史）。所以只有真滚一格才量得到这一条链。
     * 滚动用的是这台架的 `performScrollToIndex`（量具从 class 里数出来的，不是猜的）。
     */
    @Test
    fun 阅读器滚一格就把当前页交回外面() = runComposeUiTest {
        val seen = mutableListOf<Int>()
        setContent {
            Stage {
                DesktopReaderScreen(
                    pages = pages(4), episodeLabel = "第 1 话", note = null, masked = false,
                    currentPage = 0, onPageChange = { seen += it }, onBack = {},
                )
            }
        }
        onNodeWithText("第 1 / 4 页").assertExists()
        assertTrue("这台架没有滚动量具这一说 ⇒ 判据失效（不是通过）",
            runCatching { onNode(hasScrollToIndexAction()).assertExists() }.isSuccess)
        seen.clear()
        onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        waitForIdle()
        assertTrue("滚到第 3 格而 onPageChange 一次都没收到（那条链断了 → 写历史永远停在第 0 页）：$seen",
            seen.contains(2))
    }

    // ── ③ 探索屏 ────────────────────────────────────────────────────────────

    /** 分区头按真值念条数、点卡恰好进详情一次。 */
    @Test
    fun 探索屏分区头念真条数而点卡进详情一次() = runComposeUiTest {
        var opened: String? = null
        var count = 0
        setContent {
            Stage {
                DesktopExploreScreen(
                    mode = wide, sourceKey = "jm",
                    sections = listOf(EngineSession.Section("连载更新", cards(3))),
                    note = null, maskedOf = { false },
                    onOpen = { count++; opened = it.title }, onReload = {}, onOpenSourceManage = {},
                )
            }
        }
        onNodeWithText("连载更新").assertExists()
        onNodeWithText("3 本").assertExists()
        onNodeWithText("第1本").performClick()
        assertEquals(1, count)
        assertEquals("第1本", opened)
    }

    /**
     * **源管理入口真的接上了**（进度清单 §2 那一格「没有入口 ⇒ 这屏打不开」）。
     *
     * 这一判钉的是那枚按钮存在且那一下回调落得到；路由真切过去由
     * [DesktopSourceManageEntryTest] 那把源码尺钉（离屏够不到壳里那颗 `when`）。
     */
    @Test
    fun 探索屏那枚源管理入口按一次恰好落一次() = runComposeUiTest {
        var taps = 0
        setContent {
            Stage {
                DesktopExploreScreen(
                    mode = wide, sourceKey = "jm", sections = listOf(EngineSession.Section("A", cards(1))),
                    note = null, maskedOf = { false }, onOpen = {}, onReload = {},
                    onOpenSourceManage = { taps++ },
                )
            }
        }
        onNodeWithText("源管理").performClick()
        assertEquals("入口是假的（摆了而不落）：那屏仍然打不开", 1, taps)
    }

    /** 取失败那句原话上屏，而**入口还在**（取不到探索页时最该进得去源管理）。 */
    @Test
    fun 探索屏取失败时入口仍然按得到() = runComposeUiTest {
        var taps = 0
        setContent {
            Stage {
                DesktopExploreScreen(
                    mode = wide, sourceKey = "jm", sections = emptyList(),
                    note = "取探索页失败：IllegalStateException 装载后取不到 source key: jm",
                    maskedOf = { false }, onOpen = {}, onReload = {},
                    onOpenSourceManage = { taps++ },
                )
            }
        }
        onNodeWithText("取探索页失败：IllegalStateException 装载后取不到 source key: jm").assertExists()
        onNodeWithText("源管理").performClick()
        assertEquals("坏了的时候反而进不去改 —— 那正是最该能进的时候", 1, taps)
    }

    /** 「还没取回来」与「这一源没回内容」分开说。 */
    @Test
    fun 探索屏空sections的两档各念一句() {
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopExploreScreen(
                        mode = wide, sourceKey = "jm", sections = emptyList(), note = null,
                        maskedOf = { false }, onOpen = {}, onReload = {}, onOpenSourceManage = {},
                    )
                }
            }
            onNodeWithText("正在从「jm」取探索页…").assertExists()
        }
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopExploreScreen(
                        mode = wide, sourceKey = "jm", sections = emptyList(), note = "这一源没回探索内容。",
                        maskedOf = { false }, onOpen = {}, onReload = {}, onOpenSourceManage = {},
                    )
                }
            }
            onNodeWithText("这一源没回探索内容。").assertExists()
            assertFalse("有成因时不许再念'正在取…'", runCatching { onNodeWithText("正在从「jm」取探索页…").assertExists() }.isSuccess)
        }
    }

    // ── ④ 搜索屏 ────────────────────────────────────────────────────────────

    @Test
    fun 搜索屏结果按真条数铺开而点卡进详情一次() = runComposeUiTest {
        var count = 0
        setContent {
            Stage {
                DesktopSearchScreen(
                    mode = wide, sourceKey = "jm", query = "猫", onQueryChange = {}, onSearch = {},
                    results = cards(2), searching = false, note = null, maskedOf = { false },
                    onOpen = { count++ },
                )
            }
        }
        onNodeWithText("只在「jm」里搜").assertExists()
        onNodeWithText("第1本").performClick()
        assertEquals(1, count)
    }

    /** 那四档各念一句：搜中 / 空输入 / 搜完没结果 / 失败成因。 */
    @Test
    fun 搜索屏那四档各念一句而空输入与搜中时按不动() {
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopSearchScreen(
                        mode = wide, sourceKey = "jm", query = "猫", onQueryChange = {}, onSearch = {},
                        results = emptyList(), searching = true, note = null, maskedOf = { false }, onOpen = {},
                    )
                }
            }
            onNodeWithText("正在搜索…").assertExists()
            onNodeWithText("搜索中…").performClick()
            assertFalse("搜中不许能再按", runCatching { onNodeWithText("「猫」在「jm」里没搜到。").assertExists() }.isSuccess)
        }
        for ((query, results, expect) in listOf(
            Triple("", emptyList<EngineSession.ComicCard>(), "输入书名或作者，回车开始搜。"),
            Triple("猫", emptyList<EngineSession.ComicCard>(), "「猫」在「jm」里没搜到。"),
        )) {
            runComposeUiTest {
                setContent {
                    Stage {
                        DesktopSearchScreen(
                            mode = wide, sourceKey = "jm", query = query, onQueryChange = {}, onSearch = {},
                            results = results, searching = false, note = null, maskedOf = { false }, onOpen = {},
                        )
                    }
                }
                onNodeWithText(expect).assertExists()
            }
        }
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopSearchScreen(
                        mode = wide, sourceKey = "jm", query = "猫", onQueryChange = {}, onSearch = {},
                        results = emptyList(), searching = false,
                        note = "搜索失败：IllegalStateException 装载后取不到 source key: jm",
                        maskedOf = { false }, onOpen = {},
                    )
                }
            }
            onNodeWithText("搜索失败：IllegalStateException 装载后取不到 source key: jm").assertExists()
        }
    }

    /**
     * 空输入时那枚「搜索」按不动（否则一次空搜索会盖掉结果区，把屏念成"没搜到"）。
     *
     * ⚠️ 第一版这里写的是 `onNodeWithText("搜索")`，当场红在
     * `Failed to inject mouse input … found '2' nodes`：**"搜索" 这串在同屏有两枚节点**
     * （标题那枚 `Text` 与按钮那枚 `Button`）。所以必须用「带点击动作」那一枚。
     * 这不是判据写错，是这台架的第二条量程，记在这儿免得下一个人再撞。
     */
    @Test
    fun 搜索屏空输入时那枚搜索钮按不动() = runComposeUiTest {
        var taps = 0
        setContent {
            Stage {
                DesktopSearchScreen(
                    mode = wide, sourceKey = "jm", query = "   ", onQueryChange = {}, onSearch = { taps++ },
                    results = emptyList(), searching = false, note = null, maskedOf = { false }, onOpen = {},
                )
            }
        }
        val that = onNode(hasTextExactly("搜索") and hasClickAction())
        that.assertExists()
        that.performClick()
        assertEquals("空输入按下去不该发一次搜索", 0, taps)
    }

    /** 输入了才使能（同一枚钮两档），按一次恰好发一次。 */
    @Test
    fun 搜索屏有输入时那枚搜索钮按一次恰好发一次() = runComposeUiTest {
        var taps = 0
        setContent {
            Stage {
                DesktopSearchScreen(
                    mode = wide, sourceKey = "jm", query = "猫", onQueryChange = {}, onSearch = { taps++ },
                    results = emptyList(), searching = false, note = null, maskedOf = { false }, onOpen = {},
                )
            }
        }
        onNode(hasTextExactly("搜索") and hasClickAction()).performClick()
        assertEquals(1, taps)
    }

    // ── ⑤ 收藏屏 ────────────────────────────────────────────────────────────

    /**
     * 那两句**必须分开**：「正在读本地收藏…」与「还没有收藏…」。
     *
     * 清单 §3 记的正是这一格：`FavoriteDao` 的 `init { refresh() }` 发起即返回而初值是空表，
     * 直接读 `.value` 就会把"还没读回来"说成"没有收藏"。而**库里 0 条收藏时两档读起来一模一样** ⇒
     * 那一判用参数把两档都渲出来量文案，不靠跑真库（那条等待逻辑本身由
     * `DesktopFavoriteWriteSideTest.收藏读侧那条等待还在` 那把尺子钉在源码上）。
     */
    @Test
    fun 收藏屏还没读回来与确实没有各念一句() {
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopFavoritesScreen(
                        mode = wide, favorites = emptyList(), loaded = false, note = null,
                        maskedOf = { false }, onOpen = {}, onRemove = {},
                    )
                }
            }
            onNodeWithText("正在读本地收藏…").assertExists()
            assertFalse("不许把'还没读回来'说成'没收过'",
                runCatching { onNodeWithText("还没有收藏。在探索页点开一本，详情页可以收藏。").assertExists() }.isSuccess)
        }
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopFavoritesScreen(
                        mode = wide, favorites = emptyList(), loaded = true, note = null,
                        maskedOf = { false }, onOpen = {}, onRemove = {},
                    )
                }
            }
            onNodeWithText("还没有收藏。在探索页点开一本，详情页可以收藏。").assertExists()
        }
    }

    @Test
    fun 收藏屏计数念真值而移出与点开各按一次恰好一次() = runComposeUiTest {
        var opens = 0
        var removes = 0
        setContent {
            Stage {
                DesktopFavoritesScreen(
                    mode = wide,
                    favorites = listOf(fav("c1", "收藏的一本"), fav("c2", "收藏的二本")),
                    loaded = true, note = null, maskedOf = { false },
                    onOpen = { opens++ }, onRemove = { removes++ },
                )
            }
        }
        onNodeWithText("2 本").assertExists()
        // 两格各有自己那枚「移出」⇒ 必须按下标取（`onNodeWithText` 遇到同名多枚会直接失败）。
        onAllNodes(hasText("移出"))[0].performClick()
        assertEquals(1, removes)
        onNodeWithText("收藏的一本").performClick()
        assertEquals(1, opens)
    }

    @Test
    fun 收藏屏读失败那句原话上屏() = runComposeUiTest {
        setContent {
            Stage {
                DesktopFavoritesScreen(
                    mode = wide, favorites = emptyList(), loaded = true,
                    note = "读不到本地收藏：IllegalStateException 库没建",
                    maskedOf = { false }, onOpen = {}, onRemove = {},
                )
            }
        }
        onNodeWithText("读不到本地收藏：IllegalStateException 库没建").assertExists()
    }

    // ── ⑥ 源管理屏 ──────────────────────────────────────────────────────────

    /**
     * 那三格写口全按得到（启停 / 遮罩档 / 屏蔽 AI）+ 有出路。
     *
     * 「有出路」这一判是补 2026-10-21 量到的那一格：源管理屏此前**根本没有 `onBack`**，
     * 顶栏只有「源管理」四个字 —— 入口接上之后就成了一条单行道，进去出不来。
     */
    @Test
    fun 源管理屏三格写口各按一次而返回按得到() = runComposeUiTest {
        val toggled = mutableListOf<Pair<String, Boolean>>()
        val modes = mutableListOf<String>()
        val ai = mutableListOf<Boolean>()
        var backs = 0
        setContent {
            Stage {
                DesktopSourceManageScreen(
                    mode = wide,
                    rows = listOf(DesktopSourceRowUi("jm", "jm 显示名", true), DesktopSourceRowUi("copy_manga", "拷贝漫画", false)),
                    maskMode = "OFF", blockAi = false, note = null,
                    onToggleSource = { k, on -> toggled += k to on },
                    onMaskModeChange = { modes += it },
                    onBlockAiChange = { ai += it },
                    onBack = { backs++ },
                )
            }
        }
        onNodeWithText("← 返回").performClick()
        assertEquals("这屏没有出路就是单行道", 1, backs)

        onNodeWithText("停用").performClick()
        assertEquals(listOf("jm" to false), toggled)
        onNodeWithText("启用").performClick()
        assertEquals("启停按下去要吃的是那一行的 key", listOf("jm" to false, "copy_manga" to true), toggled)

        // 遮罩档：**只有换档才发一次**。点当前档不发 —— 那会把"点了没反应"做成静默，
        // 而那正是屏上拿括号标出当前档的理由（注释写着：颜色在不同主题下不可靠）。
        onNodeWithText("模糊").performClick()
        assertEquals(listOf("BLUR"), modes)
        onNodeWithText("· 不遮 ·").performClick()
        assertEquals("当前档按下去不该再发一次写", listOf("BLUR"), modes)

        onNodeWithText("打开").performClick()
        assertEquals(listOf(true), ai)
    }

    /** 已经屏蔽 AI 那一档：那句"已开"要上屏而按钮改口成「关掉」，按下去吃 `false`。 */
    @Test
    fun 源管理屏已开AI屏蔽时那句改口而按下去交回false() = runComposeUiTest {
        val ai = mutableListOf<Boolean>()
        val modes = mutableListOf<String>()
        setContent {
            Stage {
                DesktopSourceManageScreen(
                    mode = wide, rows = listOf(DesktopSourceRowUi("jm", "jm 显示名", true)),
                    maskMode = "HIDE", blockAi = true, note = null,
                    onToggleSource = { _, _ -> }, onMaskModeChange = { modes += it },
                    onBlockAiChange = { ai += it }, onBack = {},
                )
            }
        }
        onNodeWithText("已开：AI 标记的图会被挡住").assertExists()
        onNodeWithText("关掉").performClick()
        assertEquals(listOf(false), ai)
        // 当前档是 HIDE ⇒ 那一句带括号，而「不遮」不带。按非当前档要发一次。
        onNodeWithText("· 隐藏 ·").assertExists()
        onNodeWithText("不遮").performClick()
        assertEquals(listOf("OFF"), modes)
    }

    /** 说清「34 颗随包、不能删也不能装」，而不是画一个按不动的删除钮。 */
    @Test
    fun 源管理屏念清源不能增删而计数按真值念() = runComposeUiTest {
        setContent {
            Stage {
                DesktopSourceManageScreen(
                    mode = wide,
                    rows = listOf(DesktopSourceRowUi("jm", "jm 显示名", true), DesktopSourceRowUi("x", "x 名", false)),
                    maskMode = "BLUR", blockAi = true, note = null,
                    onToggleSource = { _, _ -> }, onMaskModeChange = {}, onBlockAiChange = {}, onBack = {},
                )
            }
        }
        onNodeWithText("源（1/2 启用）").assertExists()
        onNodeWithText(
            "这 34 个源随包分发（`assets/sources/*.js`），不是用户安装的，" +
                "所以这里只能启停、不能删除或新增。",
        ).assertExists()
        // 遮罩档那句安全交代必须在（H4 那一格的屏上证词）。
        onNodeWithText("默认 OFF（与安卓同一默认值）。OFF 时命中项不打码 —— " +
            "只要这个档是 OFF，下面那两类内容就会直接铺出来。").assertExists()
        // 当前档标出来，非当前档不标。
        onNodeWithText("· 模糊 ·").assertExists()
        onNodeWithText("关掉").assertExists() // blockAi=true 时那句是"已开"+按钮"关掉"
        onNodeWithText("已开：AI 标记的图会被挡住").assertExists()
    }

    @Test
    fun 源管理屏读不到清单时那句成因上屏() = runComposeUiTest {
        setContent {
            Stage {
                DesktopSourceManageScreen(
                    mode = wide, rows = emptyList(), maskMode = "OFF", blockAi = false,
                    note = "读不到源清单：IllegalStateException 资产目录没找到",
                    onToggleSource = { _, _ -> }, onMaskModeChange = {}, onBlockAiChange = {}, onBack = {},
                )
            }
        }
        onNodeWithText("读不到源清单：IllegalStateException 资产目录没找到").assertExists()
    }

    // ── ⑦ 打码那一档：四屏都吃外面递进来的判据，不自己决定 ──────────────────

    /**
     * 卡片/封面在 `masked` 两档下**屏上文字不变**（打码是 Modifier 层，不造第二套文案）。
     *
     * 为什么钉这个而不是钉"糊没糊"：观感量不到（离屏没有像素输出这一说），
     * 而这一族真正会漂的是"遮罩在这里另算一套判据"。四屏的签名都是 `masked` / `maskedOf`
     * 由外面递进来，那一条由 [DesktopMaskJudgementSingleSourceTest] 那把源码尺钉。
     */
    @Test
    fun 四屏在打码档下文案不变而都渲得出来() {
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopExploreScreen(
                        mode = wide, sourceKey = "jm", sections = listOf(EngineSession.Section("A", cards(1))),
                        note = null, maskedOf = { true }, onOpen = {}, onReload = {}, onOpenSourceManage = {},
                    )
                }
            }
            onNodeWithText("第1本").assertExists()
        }
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopFavoritesScreen(
                        mode = wide, favorites = listOf(fav("c1", "收藏的一本")), loaded = true, note = null,
                        maskedOf = { true }, onOpen = {}, onRemove = {},
                    )
                }
            }
            onNodeWithText("收藏的一本").assertExists()
        }
        runComposeUiTest {
            setContent {
                Stage {
                    DesktopReaderScreen(
                        pages = pages(2), episodeLabel = "第 1 话", note = null, masked = true,
                        currentPage = 0, onPageChange = {}, onBack = {},
                    )
                }
            }
            onNodeWithText("第 1 / 2 页").assertExists()
        }
    }

    /**
     * 单栏（Compact）那一路的量程记录 —— **顺带说明为什么点击判据只在 Expanded 上钉**。
     *
     * 第一版这里在单栏下按 `第 1 话`，跑出 `expected:<1> but was:<0>`。读出来的成因不是"那行是死的"：
     * 单栏是 `Column{ 顶栏 / DetailHead / EpisodeList }`，而 `DetailHead` 里那颗封面自持
     * `aspectRatio` ⇒ 在 1024 宽下要了 1463 高，把章节表整条推到视口下方。
     * 那一行**在组合树里**（`第 1 话` 读得到），只是 `performClick` 落的是它的实际框 ⇒ 落空。
     * 宽屏两栏时 `DetailHead` 只有 280dp 宽，行就在视口内，点击判据因此钉在 Expanded 那一颗里。
     *
     * ⚠️ 这一判钉的是**这台架的量程**，不是"用户那儿点不动"：真人窗口里那一栏自己会滚。
     *    （与 `DesktopReaderKeyTest` 那句"别把离屏按得动写成用户那儿能按"同一条口径。）
     */
    @Test
    fun 单栏时章节行在树里而落在视口外() = runComposeUiTest {
        setContent {
            Stage {
                DesktopComicDetailScreen(
                    mode = narrow, title = "某本漫画", info = info(), note = null, masked = false,
                    favorited = false, onToggleFavorite = {}, onBack = {}, onOpenEpisode = {},
                )
            }
        }
        onNodeWithText("章节（3）").assertExists()
        val row = onNodeWithText("第 1 话")
        row.assertExists()
        val top = row.getUnclippedBoundsInRoot().top.value
        println("D_单栏 第1话 top=$top（离屏那只有 1024x768 那一档）")
        assertTrue(
            "这一句变了就意味着单栏那一路的排版变了（封面不再占满一栏），本文件的点击判据要跟着重量：",
            top > 700f,
        )
        // 台架量到的第二格：单栏时视口里只有第一行，第二行还没进组合树。
        assertFalse(
            "单栏第二行进了树 ⇒ 上面那条视口判据的量程变了",
            runCatching { onNodeWithText("第 2 话").assertExists() }.isSuccess,
        )
    }
}
