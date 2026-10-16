package com.venera.compose.components.venera

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 页面标题的**两档字号必须与 miuix 顶栏那两档同源**（2026-10-16，真机两轮反馈）。
 *
 * 用户的原话：
 *  - 「滑后『画廊』还是不在同一水平线，**且字体明显小很多**」（折叠态）；
 *  - 「**他收起前**画廊的字都要小很多」（展开态，即头图上的白字大标题）。
 *
 * 两处「小很多」是同一个病：**我们自己挑了一格字号**，而不是去读 miuix 给这两格用的槽位。
 * 对照 miuix 自己的 `basic/TopAppBar.kt`：
 *  - 折叠小标题 → `fontSize = MiuixTheme.textStyles.title3.fontSize` + `FontWeight.Medium`（`:732-733`）
 *  - 展开大标题 → `fontSize = MiuixTheme.textStyles.title1.fontSize` + Medium 之外为 Normal（`:763-764`）
 *
 * 真机读数（同一台机、同一张截图里量）：折叠态我们 30px vs 原生 38px（≈ itemTitle 16sp vs title3 20sp）；
 * 展开态头图白字用的是 `screenTitle`（MD3 22sp）而原生大标题是 `title1`（MD3 28sp）。
 *
 * 这一条**不是**"造一个新数字"：我们的主题（`ui/tokens/MiuixTextStyles.kt`）本来就把
 * `title1 → display`、`title3 → statNumber` 映射到自己那张表上，所以读主题槽位 = 读表里现成的档，
 * 而页面里再也不会出现"这一页的标题是哪一格"这种各写一遍的判断。
 */
class TopBarTitleScaleTest {

    private val appMain = RepoSources.appMain

    private fun src(rel: String): String {
        val f = File(appMain, rel)
        assertTrue("找不到 $rel，量具失效", f.isFile)
        return f.readText().replace("\r\n", "\n")
    }

    /** 注释行不参与匹配（扫描类的口径与 TokenBudgetFrozenTest 同一把）。 */
    private fun codeLines(rel: String): List<String> = src(rel).lines().map { it.trim() }
        .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    /** 展开态：头图上的白字大标题 = `title1`（= 顶栏 `largeTitle` 那一档）。 */
    @Test fun `头图大标题跟 miuix 的 title1 同档`() {
        val rel = "com/venera/compose/feature/settings/SettingsHero.kt"
        val body = src(rel)
        assertTrue(
            "头图大标题没读 miuix 的 title1 槽位 ⇒ 比顶栏大标题小一截（真机「收起前字小很多」）",
            body.contains("fontSize = MiuixTheme.textStyles.title1.fontSize"),
        )
        // 负向：原来那一格（screenTitle = MD3 22sp / MIUIX 24sp）不许再出现在这颗件里。
        assertTrue(
            "头图大标题又用回了 screenTitle（那一档比大标题小 20% 上下）",
            codeLines(rel).none { it.contains("tokens.type.screenTitle") },
        )
    }

    /** 折叠态：自绘小标题 = `title3`（= 顶栏折叠小标题那一档）。 */
    @Test fun `折叠小标题跟 miuix 的 title3 同档`() {
        val rel = "com/venera/compose/components/venera/VeneraTopAppBar.kt"
        assertTrue(
            "折叠小标题没读 miuix 的 title3 槽位 ⇒ 比原生小 20%（真机字高 30 vs 38px）",
            src(rel).contains("fontSize = MiuixTheme.textStyles.title3.fontSize"),
        )
    }

    /**
     * 为什么"读主题槽位"不算库外数字：我们自己那张表把这两格映射到了现成的档上。
     *
     * 把这条钉住，是为了让下一个人看见 `title1/title3` 时不用去翻 miuix —— 也防止有人
     * 顺手把 `title1` 改成别的档（那会同时改掉**全库**所有用 title1 的地方，是大动作）。
     */
    @Test fun `主题里这两格的映射没被改掉`() {
        val rel = "com/venera/compose/ui/tokens/MiuixTextStyles.kt"
        val body = src(rel)
        assertTrue("title1 不再映射到 display", body.contains("title1 = TextStyle(fontSize = t.display)"))
        assertTrue("title3 不再映射到 statNumber", body.contains("title3 = TextStyle(fontSize = t.statNumber)"))
    }

    /**
     * 展开态那一格**除了字号**，边距与字重也要与顶栏大标题同源（2026-10-16 第二轮）。
     *
     * 用户的原话：「画廊的未收起大字和其他页面的字体和颜色对不上」。真机读数（两张截图，
     * 同机 1080×2376，量各自标题的墨迹外接框）：
     *  - 字左 x：图上 43 vs 探索页 73 —— 差 30px ≈ 12dp，正是 `rowHorizontal`(14dp)
     *    与 miuix `TopAppBarDefaults.TitlePadding`(26dp) 之差；
     *  - 字顶 y：图上 304 vs 探索页 267 —— 差 37px，主因是标题顶边自己挑了 `space11 * 2`
     *    (64dp) 而那一格是 `CollapsedHeight`(52dp)；
     *  - 笔画墨覆盖率：图上 3704/10540 = 37.0% vs 探索页 3776/11932 = 31.6%（同两字、
     *    各自外接框内）—— 差的就是 `weightSemibold` 与原生 `FontWeight.Normal` 那一档。
     *
     * 修法与字号那条同一个思路：**不自己挑，读顶栏用的那一份**。所以这里钉的是
     * `TopAppBarDefaults.TitlePadding` / `CollapsedHeight` / `FontWeight.Normal` 三个符号，
     * 而不是"26dp / 52dp / 400"这种抄来的数 —— 抄数会在 miuix 升版时静默错开。
     *
     * ⚠️ 判式一律走 [codeLines]（跳注释行）而不是全文 `contains`：这颗件的长注里
     * 就**明写着** `TopAppBarDefaults.TitlePadding` 与 `FontWeight.Normal` 这几个串，
     * 全文尺子会被自己的注释喂饱（恒绿）。
     *
     * 唯一刻意不同的一格是**颜色**：图上仍用 `ImageOverlayColors.OnImage` 纯白。
     * 深色字压用户自选图（亮度不可控）会直接读不出，这一条用户 2026-10-16 明确保留。
     */
    @Test fun `头图大标题的边距与字重跟 miuix 大标题同源`() {
        val rel = "com/venera/compose/feature/settings/SettingsHero.kt"
        val lines = codeLines(rel)
        assertTrue(
            "图上标题的左边距不是顶栏那一份 TitlePadding ⇒ 切页时标题横跳 12dp",
            lines.any { it.contains("start = TopAppBarDefaults.TitlePadding") },
        )
        assertTrue(
            "图上标题的顶边不是顶栏折叠高度那一格 ⇒ 与其余页的大标题错开一行",
            lines.any { it.contains("top = topBarInset + TopAppBarDefaults.CollapsedHeight") },
        )
        assertTrue(
            "图上标题的字重又回到 SemiBold ⇒ 同一档标题两种笔画粗细（真机「字体对不上」）",
            lines.any { it.contains("fontWeight = FontWeight.Normal") },
        )
        // 顶部 inset 也必须与 miuix 同一份来源：两者数值在多数机型恰好相等，
        // 拿 statusBars 算出来的对齐是碰巧，不是同源（同一个坑在 VeneraTopAppBar 量过）。
        assertTrue(
            "顶部 inset 又用回 WindowInsets.statusBars（miuix 顶栏吃的是 systemBars.only(Top)）",
            lines.none { it.contains("WindowInsets.statusBars") },
        )
        // 负向：三样"自己挑的量"不许回来。
        assertTrue(
            "weightSemibold 回来了（原生大标题是 Normal）",
            lines.none { it.contains("tokens.type.weightSemibold") },
        )
        assertTrue(
            "rowHorizontal 又用来定标题边距（那一档是 14dp，比顶栏窄 12dp）",
            lines.none { it.contains("tokens.spacing.rowHorizontal") },
        )
    }

    /**
     * 顶部 inset 的来源单独钉一枚：上面那条只查了"不许出现 statusBars"，
     * 这一条查的是"必须出现 systemBars.only(Top)" —— 少了它，把整行删掉也能过。
     */
    @Test fun `图上标题吃的顶部 inset 与 miuix 顶栏同一来源`() {
        val lines = codeLines("com/venera/compose/feature/settings/SettingsHero.kt")
        assertTrue(
            "顶部 inset 没读 systemBars.only(Top) ⇒ 与 miuix 顶栏不是同一份，错开一份差值",
            lines.any { it.contains("WindowInsets.systemBars.only(WindowInsetsSides.Top)") },
        )
    }

    /**
     * 颜色那一格是**刻意不同**的，所以也要有尺子：防止下一个人"顺手对齐"成 textPrimary，
     * 把图上标题变成深色 —— 那会在亮图上直接读不出。
     */
    @Test fun `图上标题的颜色仍是固定纯白不跟主题`() {
        val lines = codeLines("com/venera/compose/feature/settings/SettingsHero.kt")
        assertTrue(
            "图上标题不再用 ImageOverlayColors.OnImage（深色字压亮度不可控的用户图会看不见）",
            lines.any { it.contains("color = ImageOverlayColors.OnImage,") },
        )
        assertTrue(
            "图上标题又跟主题取了 textPrimary",
            lines.none { it.contains("tokens.color.textPrimary") },
        )
    }
}
