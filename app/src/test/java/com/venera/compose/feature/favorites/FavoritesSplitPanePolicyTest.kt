package com.venera.compose.feature.favorites

import androidx.compose.ui.unit.dp
import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收藏页双栏档位判据（2026-10-12，用户点名的 B 方案）。
 *
 * 这张用例要钉的是**零回归线**：`< 600dp` 必须走单栏，且 600 这条边界归**双栏**
 * （与 MD3 WindowSizeClass 的 media 档一致，不是本仓 WideScreenPolicy 的 `>` 口径）。
 */
class FavoritesSplitPanePolicyTest {

    /**
     * 手机档全区间必须是单栏。
     *
     * 这是方案 §4「零回归线」的可执行形态 —— 一旦有人把阈值调低，
     * 手机端会冒出双栏，而真机回归未必抓得到（要特定宽度才复现）。
     */
    @Test
    fun `手机档必须是单栏`() {
        for (w in 320..599) {
            assertFalse("${w}dp 不该进双栏", favoritesUsesSplitPane(w.dp))
        }
    }

    /**
     * 600dp 这条边界归**双栏**。
     *
     * 与 `WideScreenPolicyTest.breakpointsAreExclusiveOnBothSides` 的 600 归属**故意相反**：
     * 那边照 Flutter master 的 `>`（600 算手机），这边照 MD3 库的 `>=`（600 算 medium）。
     * 两处对齐的第一方不同，所以两把尺不同 —— 这条用例就是防止有人"顺手统一"。
     */
    @Test
    fun `600dp 归双栏与导航侧的口径故意不同`() {
        assertFalse("599dp 单栏", favoritesUsesSplitPane(599.dp))
        assertTrue("600dp 进双栏（MD3 口径 `>=`）", favoritesUsesSplitPane(600.dp))
    }

    /** 宽窗全区间必须进双栏，不许中间断档。 */
    @Test
    fun `宽窗必须连续进双栏`() {
        for (w in 600..2000 step 7) {
            assertTrue("${w}dp 应进双栏", favoritesUsesSplitPane(w.dp))
        }
    }

    /**
     * 第三栏（收藏夹导航）只在 expanded（≥840dp）出。
     *
     * 方案 §3.3 的裁决：medium 档收藏夹入口留在列表头部，不提前出第三栏。
     */
    @Test
    fun `第三栏只在 expanded 出`() {
        assertFalse("839dp 仍是两栏", favoritesUsesThreePanes(839.dp))
        assertTrue("840dp 才出第三栏", favoritesUsesThreePanes(840.dp))
    }

    /** 双栏 ⟸ 三栏：出了三栏必然已经进了双栏，两者不许互相矛盾。 */
    @Test
    fun `三栏必然蕴含双栏`() {
        for (w in 320..2000 step 11) {
            if (favoritesUsesThreePanes(w.dp)) {
                assertTrue("${w}dp 出了三栏却没进双栏 —— 判据自相矛盾", favoritesUsesSplitPane(w.dp))
            }
        }
    }

    // ── 右栏载荷的键（2026-10-13：真机崩溃栈逼出来的一格）──

    /**
     * 号里带斜杠与竖线也要原样回来。
     *
     * 分隔符换成 `|` 或 `/` 就会在这一条上崩：goda 那一档的本子号是带斜杠的 slug 形状，
     * 一本会被切成两段，或者解出别人的号。
     */
    @Test
    fun `号里带斜杠与竖线仍原样回来`() {
        listOf("350303", "/manga/abc123/", "a|b/c", "带 空格 的号").forEach { id ->
            val parsed = parseFavoritesDetailKey(favoritesDetailKey("jm", id))
            assertEquals("源名", "jm", parsed?.first)
            assertEquals("本子号", id, parsed?.second)
        }
    }

    /** 同源的相邻两本不许撞键（撞了就是一点开的是 A、右栏出的是 B）。 */
    @Test
    fun `相邻两本不撞键`() {
        assertTrue(
            "两本撞上了同一枚键",
            favoritesDetailKey("jm", "350303") != favoritesDetailKey("jm", "350304"),
        )
        assertTrue(
            "同号不同源撞上了",
            favoritesDetailKey("jm", "1") != favoritesDetailKey("goda", "1"),
        )
    }

    /**
     * 形状不对一律 null —— 调用方按「右栏没有选中项」处理。
     *
     * 半截的键不能猜：拿"源名为空、号是 jm"这种解出来的东西去开详情，
     * 屏上就是开了一本对不上号的本子。
     */
    @Test
    fun `畸形键不猜本子`() {
        listOf(
            "",
            "no-separator-here",
            "jm",
            DETAIL_KEY_SEPARATOR,
            "jm$DETAIL_KEY_SEPARATOR",
            "${DETAIL_KEY_SEPARATOR}350303",
        ).forEach { raw ->
            assertNull("这一串不该解出一本：$raw", parseFavoritesDetailKey(raw))
        }
    }

    /**
     * 宿主那颗**不许再把域对象塞进导航器**（真机崩形的机械尺子）。
     *
     * 崩形是：`rememberListDetailPaneScaffoldNavigator<ComicItem>()` +
     * `navigateTo(Detail, comic)` —— 导航器由 `rememberSaveable` 存，Android 侧
     * 只认 Bundle 装得下的类型，于是「点开一本再切页」在 dispose 的 `performSave` 里抛
     * `IllegalArgumentException: item at index 1 can't be saved: ComicItem(...)`
     * （MuMu 上四笔 dropbox 同栈：`ListSaver.kt:41` ← `ThreePaneScaffoldNavigator.kt:503`）。
     * 编译器管不到"类型参数换了个谁"，只能扫源码。
     */
    @Test
    fun `宿主只把键交给可保存状态`() {
        val dir = "com/venera/compose/feature/favorites"
        val host = RepoSources.kotlinFiles(dir).firstOrNull { it.name == "FavoritesSplitPaneHost.kt" }
            ?: error("找不到 FavoritesSplitPaneHost.kt，尝试过的目录：${RepoSources.appMain.absolutePath}/$dir")
        val code = host.readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
        assertTrue(
            "可保存的那一格只能是 String 键（放对象就会在切页时崩）：$code",
            code.any { it.contains("rememberSaveable { mutableStateOf<String?>(null) }") },
        )
        assertTrue(
            "写进那一格的值只能来自 favoritesDetailKey(...)",
            code.any { it.contains("favoritesDetailKey(comic.sourceName, comic.id)") },
        )
        assertTrue(
            "不许把 ComicItem 本体放进可保存状态",
            code.none { Regex("""rememberSaveable[^)\n]*ComicItem""").containsMatchIn(it) },
        )
    }

    /**
     * 两栏几何只许问 [favoritesPaneWeights]，不许在宿主里再抄一份比例。
     *
     * 抄一份出来就是第二把尺子：改的人只会改到一处，另一处继续按旧比例占宽 ——
     * 而它的屏上表现正是这一批刚修掉的那格「右栏空着也占宽」。
     */
    @Test
    fun `宿主里不许再抄一份栏宽比例`() {
        val dir = "com/venera/compose/feature/favorites"
        val code = RepoSources.kotlinFiles(dir)
            .first { it.name == "FavoritesSplitPaneHost.kt" }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
        assertTrue(
            "weight(...) 里出现字面量数字 = 宿主自己定了比例",
            code.none { Regex("""weight\(\s*[0-9]""").containsMatchIn(it) },
        )
    }

    // ── 两栏重量：右栏没内容就不许占宽（2026-10-13 用户读数）──

    @Test
    fun `右栏没内容时列表吃满全宽`() {
        val (list, detail) = favoritesPaneWeights(detailOpen = false)
        assertEquals("关闭态详情那一份必须是 0", 0f, detail, 0f)
        assertEquals(1f, list / (list + detail), 1e-6f)
    }

    @Test
    fun `右栏打开时两栏仍是 1 比 2`() {
        val (list, detail) = favoritesPaneWeights(detailOpen = true)
        assertEquals(2f, detail / list, 1e-6f)
    }

    /** 打开右栏只许把列表**挤窄**，不许反过来比关闭态更宽（那等于这一档永远开不了右栏）。 */
    @Test
    fun `打开右栏只会让列表变窄`() {
        val (openList, openDetail) = favoritesPaneWeights(detailOpen = true)
        val (closedList, closedDetail) = favoritesPaneWeights(detailOpen = false)
        val openShare = openList / (openList + openDetail)
        val closedShare = closedList / (closedList + closedDetail)
        assertTrue("$openShare < $closedShare", openShare < closedShare)
    }
}
