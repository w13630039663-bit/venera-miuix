package com.venera.compose.components.venera

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 覆盖进度路径（`scrollProgressOverride != null`）下顶栏必须是**恒折叠态**
 * （2026-10-16，真机「上滑标题不居中」）。
 *
 * 这一路折叠机制整个退场（调用方不挂 `nestedScroll`，见 `VeneraTopAppBar` 的参数注），带来两个
 * **互相叠加**的后果，都由 miuix 自己的源码可直接推出：
 *
 * ① **顶栏高度停在展开态**。`TopAppBar.kt:862-879`：
 *    `barHeight = lerp(collapsedHeight, collapsedHeight + expansion, 1f - collapseFraction)`，
 *    `collapseFraction` 由 `scrolledOffset` 推 ⇒ 连接不挂 ⇒ 恒 0 ⇒ 恒 = `collapsedHeight + expansion`。
 *    `expansion` 那一份就是大标题那一行（`largeTitle` 的 Box 自带 `padding(top = CollapsedHeight)`，
 *    `TopAppBar.kt:747-748`）⇒ 顶栏比折叠到位的形态**高出一整行**，背板跟着一起虚高。
 *
 * ② **miuix 原生小标题永不出现**。`TopAppBar.kt:672-682` 的
 *    `smallTitleVisible = collapsedFraction * 3f >= 1f` ⇒ 同样恒 0 ⇒ `smallTitleAlpha` 恒 0
 *    （`:724-727` 把它挂在 title 的 `graphicsLayer` 上）。所以屏上那个「画廊」只能是本文件自绘的
 *    那一份 —— 它必须与 miuix 的按钮行**同一条线**。
 *
 * ⚠️ 2026-10-16 三修（真机原话「居中标题栏还是没有居中」，前两轮白改）——**根因是钉法，不是基准**：
 *
 * 钉高度那一支如果把 miuix 的测量高度**压到它自然高以下**，它整棵子树会被**居中上顶**
 * `(自然高 − 允许高) / 2`：miuix 本体自然高 ≈ 143.6dp（inset 49.2 + `barHeight` 52 + 那个
 * **空大标题**占的一整行 ≈ 38 + 4），钉的允许高 ≈ 101.2dp ⇒ 上顶约 21.2dp。
 *
 * 真机逐像素读数（同一台机、同机同包，量右上两枚胶囊里图标笔画的中心 y）：
 *   · **普通页（没有这颗盒子）8 张截图全部 188px** —— 这就是 miuix 的正位 = `inset + 26dp`
 *   · 头图页有这颗盒子时：旧包（`Modifier.height`）134px、新包（`layout{} + place(0,0)`）132px
 *   · `188 − 134 = 54px ≈ 21.2dp × 2.5` ✓ 闭环 ——**两版几乎一样，因为第二版只换了摆放方式、
 *     没动那条 `maxHeight`**，挤压照旧（这正是它被白赔的原因）
 *   · 而自绘小标题一直落在 188px（与普通页同一基准）⇒「标题不居中」其实是**按钮被顶上去**。
 */
class VeneraTopAppBarHeroCollapseTest {

    private val rel = "com/venera/compose/components/venera/VeneraTopAppBar.kt"

    private fun src(): String {
        val f = File(RepoSources.appMain, rel)
        assertTrue("找不到 $rel，量具失效", f.isFile)
        return f.readText().replace("\r\n", "\n")
    }

    /** 注释行不参与匹配（扫描类的口径与 TokenBudgetFrozenTest 同一把）。 */
    private fun codeLines(): List<String> = src().lines().map { it.trim() }
        .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    @Test fun `覆盖进度路径把顶栏钉成折叠态高度`() {
        val body = src()
        assertTrue(
            "缺 pinCollapsedHeight 这个判据 ⇒ 顶栏仍会停在展开态（高出一整行大标题）",
            body.contains("val pinCollapsedHeight = scrollProgressOverride != null && largeTitle.isEmpty()"),
        )
        assertTrue(
            "钉高度那一支没把高度设成 顶部 inset + CollapsedHeight",
            body.contains("val pinnedBarHeightPx = with(density) {") &&
                body.contains("(collapsedTopInset + TopAppBarDefaults.CollapsedHeight).roundToPx()"),
        )
        val lines = codeLines()
        val pinAt = lines.indexOfFirst { it.contains("if (pinCollapsedHeight)") }
        assertTrue("找不到 pinCollapsedHeight 那一支", pinAt >= 0)
        val branch = lines.drop(pinAt).take(26)
        // ⚠️ 这一支**不许**用 `Modifier.height(...)`：它会把 miuix 按被钉的高度硬框住，
        // 而 miuix 的自然高比那个数大一整行空大标题 ⇒ 就是上面那条"居中上顶"的同一个病。
        // 必须自己 `layout{}` + `place(0, 0)`，并把测量高度放开。
        assertTrue(
            "钉高度那一支又用回了 SizeNode（`Modifier.height`）⇒ miui 本体被框住、整棵子树居中上顶",
            branch.none { it.startsWith(".height(") || it.contains("Modifier.height(") },
        )
        assertTrue(
            "钉高度那一支没有自己 place(0, 0)（不过 SizeNode 的唯一手法）",
            branch.any { it.contains("place(0, 0)") },
        )
        // ⚠️⚠️ 2026-10-16 三修的核心判据（真机「居中标题栏还是没有居中」，前两轮都是白改）：
        // 测量 miuix 本体时**不许**把高度压到它自然高以下 —— 压了就 `(自然高 − 允许高)/2`
        // 居中上顶。真机读数：普通页 8 张截图胶囊图标中心恒 188px（miuix 正位），头图页被压时
        // 134 / 132px，差 54px ≈ 21.2dp × 2.5 ✓。高度必须放开成 `Constraints.Infinity`。
        assertTrue(
            "钉高度那一支把 miuix 的高度压到了自然高以下（`maxHeight = pinnedBarHeightPx`）⇒ " +
                "它内部整棵子树会被居中上顶约 21.2dp，胶囊与自绘小标题错开（真机实测 54px）",
            branch.none { it.contains("maxHeight = pinnedBarHeightPx") },
        )
        assertTrue(
            "钉高度那一支没把测量约束放开（应为 `maxHeight = Constraints.Infinity`）",
            branch.any { it.contains("maxHeight = Constraints.Infinity") },
        )
        // ⚠️ `clipToBounds` 是刚需：miuix 的 `TopAppBarLayout` 内部是
        // `layout(constraints.maxWidth, layoutHeight)`（`TopAppBar.kt:892`），**不看 maxHeight**，
        // 报出的高度会超出约束；不裁的话那一整行空白仍会画出去，钉高度等于白钉。
        assertTrue(
            "钉高度那一支里没有 clipToBounds ⇒ miuix 报出的超高部分照样画出去",
            branch.any { it.contains("clipToBounds()") },
        )
        // 判据必须带 `largeTitle.isEmpty()` 那一半：万一将来有人拿覆盖进度配一个非空大标题，
        // 宁可退回现状，也不能把它静默裁掉。
        assertTrue("判据丢了 largeTitle 为空那一半", body.contains("largeTitle.isEmpty()"))
    }

    /**
     * 自绘小标题的字号必须是 **miuix 原生小标题那一档**（2026-10-16，真机「滑后『画廊』…
     * 字体明显小很多」）。
     *
     * miuix 给折叠小标题用的是 `MiuixTheme.textStyles.title3.fontSize` + Medium
     * （`TopAppBar.kt:732-733`）。我们原来自己挑了一格 `tokens.type.itemTitle`（MD3 16sp），
     * 真机实测字高 30px vs 探索页原生那枚 38px —— 差 20%，用户一眼就看出来了。
     * 读同一个主题槽位之后，两边**同号**；而且我们的 `miuixTextStylesOf` 把 title3 映射到
     * `statNumber`(20sp)，仍是表里的现成档，没有引入库外数字。
     */
    @Test fun `自绘小标题字号跟 miuix 原生那一档`() {
        val body = src()
        val at = body.indexOf("if (scrollProgressOverride != null && title.isNotEmpty())")
        assertTrue("找不到自绘小标题那一段", at >= 0)
        val seg = body.substring(at, minOf(body.length, at + 2000))
        assertTrue(
            "自绘小标题又用了自己挑的那一格（itemTitle）⇒ 比原生小 20%（真机字高 30 vs 38px）",
            !seg.contains("fontSize = tokens.type.itemTitle"),
        )
        assertTrue(
            "自绘小标题没跟 miuix 原生的 title3 同档",
            seg.contains("fontSize = MiuixTheme.textStyles.title3.fontSize"),
        )
    }

    /**
     * 自绘小标题的居中基准必须**与 miuix 用同一个 inset 修饰符**（2026-10-16 三修）。
     *
     * miuix 的标题行与按钮行都落在 `windowInsetsPadding(WindowInsets.systemBars.only(Top))`
     * **之内**（`TopAppBar.kt:806` 那个 padding 挂在这颗 Layout 自己身上，`:881` 的
     * `verticalCenter = collapsedHeight / 2` 从 inset 之后起算，`:906`/`:920` 同式落位）。
     * 所以自绘那一份也挂同一个 padding + `height(CollapsedHeight)`：盒子 = `[inset, inset+52dp]`，
     * 居中 ⇒ 中心 = `inset + 26dp`，与 miuix 的按钮行**逐像素同线**。
     *
     * 从前是自己读 `WindowInsets.statusBars` 再算 Dp（`.height(statusBarTop + CollapsedHeight)
     * .padding(top = statusBarTop)`）。本机上两份 inset 恰好相等（实测两条线同在 188px），所以
     * 看不出问题；但两者不是同一个来源，不等时就整整错一份差值 —— 判据要钉住"用同一个表达式"。
     */
    @Test fun `自绘小标题的居中基准与 miuix 同源`() {
        val body = src()
        val at = body.indexOf("if (scrollProgressOverride != null && title.isNotEmpty())")
        assertTrue("找不到自绘小标题那一段", at >= 0)
        val seg = body.substring(at, minOf(body.length, at + 900))
        assertTrue(
            "自绘小标题没挂 miuix 同款 inset 修饰符 ⇒ 与按钮行不保证同线",
            seg.contains(".windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))"),
        )
        assertTrue(
            "自绘小标题没把自己按 `CollapsedHeight` 框住 ⇒ 居中基准不是 `inset + CollapsedHeight/2`",
            seg.contains(".height(TopAppBarDefaults.CollapsedHeight)"),
        )
        assertTrue(
            "自绘小标题又自己读 statusBars 再算 Dp ⇒ 与 miuix 那一份不再同源（本机相等只是巧合）",
            !seg.contains("padding(top = statusBarTop)"),
        )
        assertTrue("自绘小标题丢了 ActiveProgress 驱动的 alpha", seg.contains("alpha = activeProgress.value"))
    }

    @Test fun `钉高度那一支与普通支共用同一份参数表`() {
        // 两处各写一遍 `TopAppBar(...)` 就是本仓反复踩过的"两处会分叉"形状 ——
        // 抽成 VeneraTopAppBarBody 之后，只许有一处定义、两处调用。
        val lines = codeLines()
        val defined = lines.count { it.contains("private fun VeneraTopAppBarBody(") }
        val called = lines.count { it.trim() == "VeneraTopAppBarBody(" }
        assertTrue("VeneraTopAppBarBody 必须正好定义一处（实际 $defined）", defined == 1)
        assertTrue("VeneraTopAppBarBody 必须被两个分支各调一次（实际 $called）", called == 2)
        val direct = lines.count { it.trim() == "TopAppBar(" }
        assertTrue("不许再有直接调 miuix TopAppBar 的第三处（实际 $direct）", direct == 1)
    }
}
