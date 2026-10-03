package com.venera.desktop.gallery.ui

import androidx.compose.ui.graphics.Color
import io.github.composefluent.darkColors

/**
 * 桌面首页的**深色主题与色板唯一出处** —— surface 阶梯与文字色在这里定死，其余文件一律不写死色值。
 *
 * ## 为什么强制深色（2026-10-04）
 *
 * `:desktop` 之前由 `FluentTheme { … }` 自己跟系统，于是**浅色档**下画出来的界面与设计稿
 * `docs/designs/windows-gallery-home-touhou-2026-10-03.html` 那套深色阶梯毫无关系 ——
 * 而 `DesktopUnimplementedRow` / `DesktopGalleryHomeSections` 里那几枚色值（`#8B8B8B` 灰、
 * `#CBB6FF` 藤紫、`#3A2A55→#2C2C2C` 渐变）**全部是稿的深色档**。浅底上那几枚就是"看起来发灰的脏色"。
 * 所以本轮把深浅**钉死在深色**：[darkColors] 交给 Fluent，其余 surface 走稿的四档阶梯。
 *
 * ⚠️ 代价要写明：桌面端**今天没有主题切换**。稿上的两档浅色映射、以及
 * `DesktopGalleryPreferences` 里那 12 枚读成员（`galleryAnimated` 等）在桌面给的都是常量，
 * 主题轴还没接 —— 这是"深色固定"能成立的前提。将来要接主题轴时，
 * 本文件要变成两套色板而不是删掉深色这套。
 *
 * ## 阶梯的语义（不是随手排的几档）
 *
 * | 档 | 值 | 用在哪 | 为什么在这一档 |
 * |---|---|---|---|
 * | 窗底 | `#202020` | `windowBackground` | 稿 `:10`，整扇窗的最底层 |
 * | rail | `#1C1C1C` | `rail` | 稿 `:59` 的 `.rail{background:#1C1C1C}`，**比窗底与 pane 都更暗** |
 * | 侧栏 | `#272727` | pane | 稿 `:11` 的 `--layer`，比窗底高一档 |
 * | 卡片 | `#2C2C2C` | Hero 侧卡、作品卡 | 稿 `:12` 的 `--card`，比侧栏再高一档 |
 * | 浮起 | `#353535` | 选中行 hover、输入框、**作品卡描边** | 稿 `--card-hov` `:13` / `--stroke` `:11`（同一颗值） |
 *
 * ## 为什么 rail 单独一档（2026-10-04 改判）
 *
 * 这一档原先**不存在**，当时 rail 与 pane 共用 `SidebarBackground`，而
 * `DesktopGalleryHome` 的注释却写着「底色刻意比 pane 更深一档」—— 注释与代码互相矛盾，
 * 结果两级导航在深色下糊成同一片，只靠一条 1px 线撑着（深色里几乎看不见）。
 *
 * 现在照稿执行：`.rail{background:#1C1C1C}`（稿 `:59`）确实比 `.pane{background:var(--layer)}`
 * 更暗，而**这枚明度差就是两级导航的层级差本身** —— rail 说"我在哪个域"、pane 说"我在这个域的哪一页"。
 * 删掉它两级会塌成一档，所以它不是"随手加的中间灰"，是承重值。
 * 由 `DesktopCardOverlayTest` 逐字钉死 + 一条「rail 与 pane 必须不同档」的断言守住。
 *
 * ⚠️ **五档之间不许再出现第六档"差不多"的色**：本仓记过的那类事故就是"随手加一个中间灰"，
 * 屏幕上一眼看不出差别、久了只觉得脏。五档是上限。
 */
internal object DesktopTheme {

    // ── surface 阶梯（逐字抄稿的 CSS 变量，:10-13）────────────────────────────

    /** 窗底。稿 `--host` = `#202020`。 */
    val WindowBackground: Color = Color(0xFF202020)

    /**
     * rail 专用底色。稿 `.rail{background:#1C1C1C}`（`docs/designs/windows-gallery-home-touhou-2026-10-03.html:59`）。
     *
     * ⚠️ 它是**五档阶梯里的第二档**，不是"随手加的中间灰"：比窗底 `#202020` 与 pane 的
     * `#272727` 都更暗，而**这枚明度差就是两级导航的层级差本身**（rail = 我在哪个域，
     * pane = 我在这个域的哪一页）。2026-10-04 之前 rail 与 pane 共用 [SidebarBackground]，
     * 两级在深色下糊成一片 —— 由 `DesktopCardOverlayTest` 的「rail 与 pane 必须不同档」守住。
     */
    val RailBackground: Color = Color(0xFF1C1C1C)

    /** 侧栏（pane）。稿 `--layer` = `#272727`。 */
    val SidebarBackground: Color = Color(0xFF272727)

    /** 卡片。稿 `--card` = `#2C2C2C`。 */
    val CardBackground: Color = Color(0xFF2C2C2C)

    /** 交互态浮起。稿 `--card-hov` = `#353535`。只在 hover / 聚焦时出现。 */
    val SurfaceRaised: Color = Color(0xFF353535)

    // ── 文字（稿 `:14-17` 的 --t1..--t3，本次按用户口径收成两档）───────────────

    /** 主文字。`#FFFFFF`。 */
    val TextPrimary: Color = Color(0xFFFFFFFF)

    /** 次要文字。`#A0A0A0`（用户口径；稿那枚 `--t3` 是 `#8B8B8B`，更暗一档，本轮统一提上来）。 */
    val TextSecondary: Color = Color(0xFFA0A0A0)

    /** 最弱一档：未实现行、caption。稿 `--t3` = `#8B8B8B`。 */
    val TextTertiary: Color = Color(0xFF8B8B8B)

    // ── 强调色（稿 `:18-19`）─────────────────────────────────────────────

    /** 藤紫：选中态文字、指示条、focus。稿 `--fuji-lite` = `#CBB6FF`。 */
    val AccentFuji: Color = Color(0xFFCBB6FF)

    /** 博丽朱：神社档装饰与警示。稿 `--beni` = `#D6463C`。 */
    val AccentBeni: Color = Color(0xFFD6463C)

    /**
     * 选中行的横向渐变（稿 `.nav.sel`：`linear-gradient(90deg,#3A2A55,#2C2C2C 78%)`）。
     *
     * 起点的 `#3A2A55` 是藤紫压进卡片档的混色，**不是**新色 —— 它比 [AccentFuji] 暗、比
     * [CardBackground] 亮一档，所以它在两档之间而不是自成一档阶梯。
     */
    val SelectionGradientStart: Color = Color(0xFF3A2A55)
    val SelectionGradientEnd: Color = Color(0xFF2C2C2C)

    /**
     * 作品卡底部遮罩的终点：用户口径 `#80000000`（半透明黑）。
     *
     * 为什么是半透明而不是实色：图本身明暗差别极大（白底作品贴实色会糊掉画），
     * 半透明黑让文字在任何底图上都够对比 —— 这一条由
     * `DesktopCardOverlayTest` 按"终点带 alpha"钉住，改成不透明即红。
     */
    val CardOverlayEnd: Color = Color(0x80000000)

    /**
     * 压在图上的**小胶囊底**（作品卡的站点标、hero 的「重排」钮）：`#B0202020`。
     *
     * 它是窗底 `#202020` 的**半透明变体**而不是新的一档 surface —— 与 [CardOverlayEnd] 同理由，
     * 实色会糊掉图面。收进主题是因为它原先在两个文件里各写一遍 `Color(0xB0202020)` 字面量，
     * 而旧版 `DesktopSurfaceLiterals` 的前缀表只认 `0xFF` ⇒ 这两处从来没被「色板唯一出处」扫到。
     */
    val OverlayScrim: Color = Color(0xB0202020)

    /**
     * 「未实现」行的**图标**档：`#5E5E5E`，稿 `.nav.off svg`（`:212`）。
     *
     * 比同行的文字档 [TextTertiary]（`#8B8B8B`）再暗一档 —— 图标是次要信号，
     * 不该和标题抢注意力。收进主题的理由同上（原先在 `DesktopUnimplementedRow.kt` 里写字面量）。
     */
    val OffGlyph: Color = Color(0xFF5E5E5E)

    /** 图位底色：图还没到 / 取不到时那块不是纯黑也不是纯白，而是卡片档压深一点。 */
    val ImagePlaceholder: Color = Color(0xFF1F1F1F)

    /** 图位里的字（"没给缩略档"这类），压在 [ImagePlaceholder] 上。 */
    val OnImagePlaceholder: Color = Color(0xFF8B8B8B)

    /**
     * 给 `FluentTheme` 用的深色配色：accent 走藤紫。
     *
     * 为什么 accent 取藤紫而不是博丽朱：博丽朱是神社档的装饰色，选中态那种"当前位置"的
     * 语义对不上；稿上 `.nav.sel` 的指示条本来就是 `--fuji-lite`。
     */
    fun colors() = darkColors(accent = AccentFuji)
}
