package com.venera.desktop.gallery.ui

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 视觉重构（2026-10-04）的**机器判据** —— 那些"改错了不会红、但用户看得见"的规矩。
 *
 * ## 为什么不靠肉眼核
 *
 * 本仓记过多次"看起来对"的假绿：色值从 `#80000000` 变成 `#00000000`（遮罩等于没有）、
 * 药丸底色从冷灰换回暖棕（在一片冷调里读成脏斑），这些**编译通过、单测全绿、只有人眼发现**。
 * 而这条文件里的判据全部是**纯文本/纯数值**，不需要起窗口、不需要截图。
 *
 * ## 覆盖面
 *
 * ① surface 阶梯**五档**一字未改，且**没有第六档**（随手加一个"差不多"的中间灰是最常见的漂；
 *   第五档 rail `#1C1C1C` 是设计稿给的、承重的层级差，2026-10-04 用户拍板准入）；
 * ② 文字三档 + 强调两档都落在稿/口径给的值上；
 * ③ 遮罩终点**必须带 alpha** —— 改成不透明即红；
 * ④ 未实现药丸**不许回到暖棕**（那一档是神社装饰色，冷调里读成脏斑）；
 * ⑤ 本仓**没有第二份色板**：桌面源里除 [DesktopTheme] 外不许再写死色值字面量；
 * ⑥ hero 的**非对称**（右列固定 320 而非第二个 weight）与高度出处；
 * ⑦ 作品墙首卡**跨 2 列**、宽卡 16:10、卡片有**标题层**与**描边**。
 */
class DesktopCardOverlayTest {

    /**
     * surface 阶梯**五档**逐字钉死，且桌面没有第二份色板。
     *
     * ⑤ 那一档（2026-10-04 改判，用户拍板）：rail 专用档 `#1C1C1C`。
     * 它**不是**"随手加的中间灰"—— 稿 `.rail{background:#1C1C1C}` 就是这么画的，
     * 而它比 pane 的 `#272727` 更暗这件事**本身就是两级导航的层级差**
     * （rail = 我在哪个域，pane = 我在这个域的哪一页）。
     * 2026-10-04 之前 rail 与 pane 共用 `SidebarBackground`，注释却声称"rail 更深一档"，
     * 结果深色下两级糊成一片 —— 那条注释与代码互相矛盾的现场就是本条的由来。
     * 所以下面多一条「rail 与 pane 必须不同档」的断言：删掉第五档，两级会塌成一档。
     */
    @Test
    fun `surface 阶梯五档逐字未改且没有第六档`() {
        // 逐档钉死：任何一个被"顺手调一下"都会红
        assertEquals("窗底必须是 #202020", 0xFF202020.toInt(), DesktopTheme.WindowBackground.toArgb())
        assertEquals("侧栏必须是 #272727", 0xFF272727.toInt(), DesktopTheme.SidebarBackground.toArgb())
        assertEquals("卡片必须是 #2C2C2C", 0xFF2C2C2C.toInt(), DesktopTheme.CardBackground.toArgb())
        assertEquals("浮起档必须是 #353535", 0xFF353535.toInt(), DesktopTheme.SurfaceRaised.toArgb())
        assertEquals("rail 必须是 #1C1C1C", 0xFF1C1C1C.toInt(), DesktopTheme.RailBackground.toArgb())

        assertTrue(
            "rail 与 pane 必须不同档 —— 同档就等于两级导航没有层级差" +
                "（DesktopGalleryHome 的注释曾声称更深一档，实际两处是同一个色）",
            DesktopTheme.RailBackground.toArgb() != DesktopTheme.SidebarBackground.toArgb(),
        )

        // 防新增：有人再加一颗 `Color(0xFF2A2A2A)` 时会红。
        val others = DesktopSurfaceLiterals.outsideTheme()
        assertEquals(
            "除 DesktopTheme.kt 之外还有别处写死 surface 色值：$others\n" +
                "色板只有 DesktopTheme 一处出处，别的地方要颜色就引它。",
            emptyList<String>(),
            others,
        )
    }

    /**
     * Hero 必须保持**非对称**：左 `1fr` + 右固定 320，而不是两个 `weight` 对称化。
     *
     * 稿 `.top{grid-template-columns:1fr 320px}`（`:146`），`:657` 标注「非对称来自 hero 占 1fr +
     * 右侧两小卡固定 320」。两个等权会把它压成 1:1 —— 那不是"另一种风格"，是**把设计稿的核心手法丢了**。
     *
     * 同时钉住高度：写死的 `180.dp` 是缩水版（稿 `.hero{height:330px}` `:147`），
     * 高度必须由 [DesktopGalleryMetrics] 单一出处给，改档时只动那一处。
     */
    @Test
    fun `hero 右列是固定宽且高度由 Metrics 出处给`() {
        val hero = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopDailyHero.kt"))
        assertTrue(
            "hero 右列应引 heroSideColumnWidth（稿 1fr 320px），不是第二个 weight(1f)",
            hero.contains("DesktopGalleryMetrics.heroSideColumnWidth"),
        )
        assertTrue(
            "hero 高度应由 DesktopGalleryMetrics.heroHeight 给，不是就地写死 180.dp",
            hero.contains("DesktopGalleryMetrics.heroHeight") && !hero.contains("180.dp"),
        )
    }

    /**
     * 作品墙的三件形状 —— **只有肉眼能发现**的那三样，逐条钉成文本判据：
     * 首卡跨 2 列、宽卡 16:10、卡片有标题层与描边。
     *
     * ⚠️ 用 [DesktopSourceTree.codeText]（跳注释）而不是 `readText`：否则这些串在注释里
     * 被提到就会让断言恒真 —— 那是本仓记过的"看起来有判据、其实没有"。
     */
    @Test
    fun `墙的首卡跨两列且卡片有标题层与描边`() {
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopDailyPane.kt"))
        assertTrue(
            "首卡应跨 2 列（稿 .art-card.wide{grid-column:span 2}）",
            pane.contains("GridItemSpan(maxLineSpan.coerceAtMost(2))"),
        )
        assertTrue("宽卡应是 16:10（稿 .art-card.wide .im）", pane.contains("16f / 10f"))
        assertTrue(
            "卡片应有标题层（稿 .art-card .ttl 在左上）",
            pane.contains("Alignment.TopStart"),
        )
        assertTrue(
            "卡片应有 1px 描边（稿 .art-card{border:1px solid var(--stroke)}，全稿不用阴影）",
            pane.contains(".border(1.dp") && pane.contains("DesktopTheme.SurfaceRaised"),
        )
    }

    @Test
    fun `文字与强调色落在口径给的值上`() {
        assertEquals(0xFFFFFFFF.toInt(), DesktopTheme.TextPrimary.toArgb())
        assertEquals(0xFFA0A0A0.toInt(), DesktopTheme.TextSecondary.toArgb())
        assertEquals(0xFF8B8B8B.toInt(), DesktopTheme.TextTertiary.toArgb())
        assertEquals("藤紫 #CBB6FF", 0xFFCBB6FF.toInt(), DesktopTheme.AccentFuji.toArgb())
        assertEquals("博丽朱 #D6463C", 0xFFD6463C.toInt(), DesktopTheme.AccentBeni.toArgb())
    }

    /**
     * 遮罩终点必须**半透明**。
     *
     * 为什么不许改成不透明黑：作品图明暗差别极大（白底作品贴实色会把画面糊掉），
     * `#80` 那一档 alpha 是"压得住字、看得见图"的平衡点。改成 `#FF000000` 时
     * 文字对比更好看、截图也"更清楚"，但下半个图就没了 —— 那是拿观感换真内容。
     */
    @Test
    fun `遮罩终点必须半透明不许改成不透明`() {
        val alpha = DesktopTheme.CardOverlayEnd.alpha
        assertTrue(
            "遮罩终点必须带 alpha（当前 ${DesktopTheme.CardOverlayEnd}），" +
                "不透明黑会把作品图下半个糊掉 —— 那是拿观感换真内容",
            alpha in 0.3f..0.95f,
        )
        assertEquals("终点必须是 #80000000", 0x80000000.toInt(), DesktopTheme.CardOverlayEnd.toArgb())
    }

    /**
     * 药丸底色不许回到暖棕 `#33251F`。
     *
     * 那一档是设计稿神社档的装饰色，配暖调用的。窗体转冷调深阶梯之后，
     * 暖棕药丸在一片 `#2C2C2C` 里读成"一块脏斑"而不是"一枚标记"——
     * 而"缺席"这个信号必须一眼可辨。
     */
    @Test
    fun `未实现药丸不许用暖棕底`() {
        val file = DesktopSourceTree.desktopUiSource("DesktopUnimplementedRow.kt").readText()
        assertTrue(
            "药丸底色回到暖棕 #33251F 了 —— 冷调深色阶梯里那是脏斑，不是标记",
            !file.contains("0xFF33251F"),
        )
        assertTrue(
            "药丸底色应改引 DesktopTheme.SurfaceRaised（形状不变、色值换档）",
            file.contains("DesktopTheme.SurfaceRaised"),
        )
    }

    /**
     * 凭据面板**不许**再直接挂在正文里（2026-10-04 用户口径：输入框与「保存并校验」不占核心视觉区）。
     *
     * 判据是对源码的负向断言：pane 那一件里出现 `DesktopGelbooruCredentialPanel(` 就是没走入口。
     */
    @Test
    fun `凭据面板不许直接挂在正文里`() {
        val pane = DesktopSourceTree.desktopUiSource("DesktopDailyPane.kt").readText()
        assertTrue(
            "DesktopDailyPane 直接画了凭据面板 —— 它必须收进 GelbooruCredentialLauncher 那一行",
            !pane.contains("DesktopGelbooruCredentialPanel("),
        )
        assertTrue(
            "正文里应当是入口那一行",
            pane.contains("GelbooruCredentialLauncher("),
        )
    }

    /**
     * Hero 区必须存在，且取的是**墙上那批**的第一张。
     *
     * 负向判据：Hero 若另按分数挑一张，就等于在用户已经看到的 40 张之外多出一个选择，
     * 点了却没有下一页可翻。所以不许出现"挑最高分"那类判式。
     */
    @Test
    fun `Hero 取墙上第一张而不是另挑最高分`() {
        val hero = DesktopSourceTree.desktopUiSource("DesktopDailyHero.kt").readText()
        assertTrue("Hero 必须取 merged.posts 的第一张", hero.contains("daily.merged.posts.firstOrNull()"))
        listOf("maxByOrNull", "maxBy {", "sortedByDescending", "sortedBy { it.score }").forEach { bad ->
            assertTrue("Hero 里出现了「$bad」—— 那是另挑一张，与墙上不是同一个集合", !hero.contains(bad))
        }
    }

    /**
     * Hero 大位必须走**底图那一档**（`GalleryPost.backdropUrl`），不许拿网格缩略档顶。
     *
     * 2026-10-04 用户发来截图报「hero 的大图糊成一片」—— 当时写的是
     * `previewUrl.ifBlank { largeUrl }`，即**缩略档优先**：300~360px 的图铺到 ≈900×330 的
     * 大位上等于放大 3 倍，再叠一层底部遮罩就只剩色块，认不出画的是什么。
     *
     * 这不是"参数该调一下"，是**选错了档**。本仓 `GalleryPost.backdropUrl` 就是为
     * 「当背景底图」开的那一档（非视频取中档 `largeUrl`，视频条目整条转给 `videoPosterUrl`
     * 那道"中档被兜底成原片 mp4 就退缩略图"的闸门），Android 侧画师介绍页的 hero
     * 用的就是它 —— 所以这里断言的是**引用**，不是把那条判据再抄一遍。
     *
     * ⚠️ 2026-10-01 已经报过一次同类事故（用户原话「模糊过头」），记录就在
     * `GalleryPost.backdropUrl` 的 KDoc 里。桌面侧这次是同一个错。
     *
     * 用 [DesktopSourceTree.codeText]（跳注释）：本条 KDoc 里点名了 `previewUrl`，
     * 含进正文会让负向断言恒假。
     */
    @Test
    fun `Hero 大位走底图那一档而不是网格缩略档`() {
        val hero = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopDailyHero.kt"))
        assertTrue(
            "Hero 大位应引 GalleryPost.backdropUrl —— 中档优先，视频条目另有闸门",
            hero.contains(".backdropUrl"),
        )
        assertTrue(
            "Hero 大位里就地写了 previewUrl —— 那是网格缩略档，铺满大位会糊成色块；" +
                "取哪一档的判据归 GalleryPost.backdropUrl，UI 层不许再写一份",
            !hero.contains("previewUrl"),
        )
    }
}
