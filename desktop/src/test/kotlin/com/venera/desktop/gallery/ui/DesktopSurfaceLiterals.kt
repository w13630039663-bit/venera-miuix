package com.venera.desktop.gallery.ui

import java.io.File

/**
 * "色板只有一处出处"的扫描器 —— [DesktopCardOverlayTest] 第①条用。
 *
 * ## 这条为什么需要机器看管
 *
 * 本仓记过的那类事故是"随手加一个中间灰"：四档 surface 阶梯本来对齐了，
 * 某次为了"让某一处看着更协调"加了一颗 `Color(0xFF2A2A2A)`，于是那处比两侧都亮半档。
 * 单看那处代码没问题、编译过、单测全绿，只有并排看才发现"这一块脏"。
 * 所以规矩是：**色值字面量只允许出现在 [DesktopTheme] 里**，别处要颜色就引它。
 *
 * ## 为什么扫的是**代码行**而不是全文
 *
 * 口径注释里**必须**能点名对岸那个数（`--layer` = `#272727`、遮罩的 `#80000000`），
 * 含进注释就会把判据读成"到处都写死了色值"，逼着后人把说明删掉 —— 那是把文档当违例。
 * 与 `DesktopSourceTree.codeLines` 同一条纪律，形状也复用同一份。
 */
internal object DesktopSurfaceLiterals {

    /**
     * 深色 surface 阶梯那几档 + 文字两档的字面量前缀。
     *
     * ⚠️ 2026-10-04 补了三处**本来就漏网**的前缀 —— 旧表只认 `0xFF`，于是：
     * - `0xB0202020`（`#B0` 半透明窗底，作品卡站点胶囊与 hero「重排」钮各写一遍）从来没被扫到；
     * - `0xFF5E5E5E`（稿 `.nav.off svg` 的图标档，比文字再暗一档）同样漏网；
     * - `0xFF1C1C1C`（rail 专用档）本轮新增。
     *
     * 补它们的**前提**是那三处字面量先收进 [DesktopTheme]（`OverlayScrim` / `OffGlyph` / `RailBackground`），
     * 否则一补前缀就会把既有代码打成红 —— 那是"判据自己先崩"，不是"抓到漂移"。
     */
    private val SURFACE_PREFIXES = listOf(
        // 四档基础阶梯
        "0xFF202020", "0xFF272727", "0xFF2C2C2C", "0xFF353535",
        "0xFF1F1F1F",
        // rail 专用档（稿 `.rail{background:#1C1C1C}`，比 pane 更暗 —— 明度差即层级差）
        "0xFF1C1C1C",
        // 半透明窗底：胶囊底。`#B0` 前缀是旧盲区
        "0xB0202020",
        // 未实现行的图标档（稿 `.nav.off svg`）
        "0xFF5E5E5E",
    )

    /**
     * 色板那颗文件之外，所有写死 surface 色值的「文件:行」。
     *
     * 只认**行首或逗号后紧跟的色值字面量**（`Color(0xFF…)` / `= 0xFF…`），不扫注释 ——
     * 理由见类注释。
     */
    fun outsideTheme(): List<String> {
        val theme = File(DesktopSourceTree.repoRoot, THEME_REL).absolutePath
        return DesktopSourceTree.desktopMainSources()
            .filter { it.absolutePath != theme }
            .flatMap { file ->
                DesktopSourceTree.codeLinesOf(file)
                    .mapIndexedNotNull { index, line ->
                        SURFACE_PREFIXES.firstOrNull { line.contains("Color($it") || line.contains("= $it") }
                            ?.let { hit -> "${file.relativeTo(DesktopSourceTree.repoRoot).path}:${index + 1}  $hit" }
                    }
            }
    }

    private const val THEME_REL =
        "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopTheme.kt"
}
