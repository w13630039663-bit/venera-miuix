package com.venera.desktop.gallery.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * 桌面首页两级导航的尺寸口径 —— rail 48、pane 224、图片墙横向内边距 24、每列预算 200 在**桌面的唯一出处**。
 *
 * ## 口径账（哪几颗有对岸、哪颗没有）
 *
 * - **rail 48 没有可漂的对岸**：Android 端压根没有 rail 这一层（那边是底栏 + 侧栏，没有 48dp 的域切换条）。
 *   数是设计稿给的：`docs/designs/windows-gallery-home-touhou-2026-10-03.html:59` 的 `.rail{flex:0 0 48px}`。
 *   桌面自持，不需要机器看管。rail 那一层由 `DesktopGalleryHome` **自绘**（2026-10-04 拆掉了
 *   集成方原先套的 compose-fluent `NavigationView`，那层自带 180dp 侧栏，判负记录见
 *   `docs/rounds/large-screen-adaptation-stage2-plan-2026-10-02.md:20`），本文件管它的**数**。
 * - **pane 224 真会漂**：对岸 = `app/src/main/java/com/venera/compose/components/WideScreenPolicy.kt:60`
 *   那颗 private `ExpandedSideBarWidth = 224.dp`（master `_kSideBarWidth`）。两端各持一份是**受机器看管的重复**，
 *   不是自由重复 —— 由 `DesktopWidthCaliberDriftTest` 逐字核对。
 * - **每列 200 真会漂**：对岸 = 同一颗文件 `:130` 表达式里的除数（沿 master 的 `maxCrossAxisExtent: 200`）。
 * - **横向内边距 24** = `VeneraSpacing.screenHorizontal`(12dp) × 2，对岸是同一颗文件 `:121` 的
 *   `imageWallHorizontalPadding()`。这三处都由同一颗用例钉。
 *
 * ⚠️ 这些数**不许**改成从 Android 侧 import：`components/` 不在 `:desktop` 的 srcDir 里，而
 * `desktop/build.gradle.kts` 本轮冻结（srcDir 与排除清单由 `DesktopSharedFaceLedgerTest` 双向对账）。
 * `WideScreenPolicy.kt` 本身一行不改 —— 原计划要往它加 `railWidth()`，那是动 Android UI 基建，已撤。
 */
internal object DesktopGalleryMetrics {

    /** 域切换条宽（图库 / 漫画 / 设置）。设计稿原值，桌面自持。 */
    val railWidth: Dp = 48.dp

    /** 域内页条宽。对岸见类注释里的 `WideScreenPolicy.kt:60`。 */
    val paneWidth: Dp = 224.dp

    /**
     * 图片墙的左右内边距**合计**。对岸 = `WideScreenPolicy.imageWallHorizontalPadding()`
     * （= `VeneraSpacing.screenHorizontal` 的 12dp × 2，`:121`）。
     */
    val imageWallHorizontalPadding: Dp = 24.dp

    /**
     * 每列预算（dp）。对岸 = `WideScreenPolicy.kt:130` 表达式里的除数 200，
     * 而那颗数沿的是 master 详情页预览格的 `maxCrossAxisExtent: 200`。
     */
    const val IMAGE_WALL_COLUMN_BUDGET_DP: Float = 200f

    /**
     * 列数 = `ceil((内容区宽 − 横向内边距) / 每列预算)`，下限 3。
     *
     * ⚠️ 吃的是**内容区实测宽**（= 窗口宽 − rail − pane），而 Android 那颗 `imageWallColumnCount`
     * 吃**窗口宽**再自己减掉 sideBar。两条路的输入口径不同，因为桌面没有 `MediaQuery`，
     * 宽度只能自己量；**读数在每一档上都与设计稿标注一致**：
     *
     * | 窗口 | 内容区 | 推导 | 列数 |
     * |---|---|---|---|
     * | **1440**（当前默认，稿 `.frame`） | `1440−48−224 = 1168` | `ceil((1168−24)/200) = 6` | **6** |
     * | 1280 | 1008 | `ceil((1008−24)/200) = 5` | 5 |
     * | 1080（旧默认） | 808 | `ceil((808−24)/200) = 4` | 4 |
     *
     * 稿 `:338` 与 `:657` 两处标注的「内容净宽 1168 → 6 列」与 1440 档逐字相符。
     * ⚠️ 稿上 `.wall{grid-template-columns:repeat(5,...)}`（`:184`）那个 5 是**展示稿的硬编码摆位**，
     * 不是推导产物 —— **推导不许抄展示稿**，所以本函数在 1440 档给 6 是正解而不是偏差。
     * 这条由 `DesktopWidthCaliberDriftTest` 钉死。
     *
     * 下限 3 是照 Android 原式（`:130` 的 `coerceAtLeast(3)`）抄的，不是桌面另定的一档；
     * 向上取整的方向也一致（保证算出来的格宽不超预算，而不是刚好超）。
     */
    fun imageWallColumnCount(contentWidthDp: Float): Int =
        ceil((contentWidthDp - imageWallHorizontalPadding.value).coerceAtLeast(0f) / IMAGE_WALL_COLUMN_BUDGET_DP)
            .toInt()
            .coerceAtLeast(3)

    /** pane 行高。设计稿 `.nav{height:40px}`（`:73`），与批次 H 定的「视觉 40 + 触达 48」同一档。 */
    val navRowHeight: Dp = 40.dp

    /** pane 行的左右内缩与设计稿 `.nav{padding:0 12px}` 同值（`:73`）。 */
    val navRowHorizontalPadding: Dp = 12.dp

    /** pane 行里图标与文字的间距。设计稿 `.nav{gap:12px}`（`:73`）。 */
    val navRowGap: Dp = 12.dp

    /** 图标位宽。设计稿 `.nav svg{width:16px}`（`:74`）；桌面这轮没有图标集，位子里摆一个汉字。 */
    val navGlyphSlotWidth: Dp = 16.dp

    /** 图片墙的行列间距。设计稿 `.wall{gap:14px}`（`:184`）。 */
    val wallGridGap: Dp = 14.dp

    /**
     * Hero 区高。设计稿 `.hero{height:330px}`（`:147`）的**基础值**。
     *
     * ⚠️ 稿 `:268` 另有一条 `body[data-deco="shrine"] .hero{height:360px}`，那是**装饰档覆盖值**
     * （`:292` 的 `<body data-deco="shrine">` 只是设计稿的预览开关）。本轮不做装饰层，
     * 所以取基础值 330 —— 提前依赖一个没开的档，就等于让尺寸说谎。
     * 将来真做神社档装饰时，只需要改这一处。
     */
    val heroHeight: Dp = 330.dp

    /**
     * Hero 右侧两小卡的**固定**列宽。设计稿 `.top{grid-template-columns:1fr 320px}`（`:146`）——
     * 右列写死 320，左列才吃剩下的 `1fr`。
     *
     * ⚠️ 这里必须是固定宽而不是第二个 `weight`：两个等权会把 `1fr : 320` 的非对称压成 1:1，
     * 而那正是稿 `:657` 标注的「非对称来自 hero 占 1fr + 右侧两小卡固定 320」的反面。
     */
    val heroSideColumnWidth: Dp = 320.dp

    /** Hero 左右两列的间距。设计稿 `.top{gap:16px}`（`:146`）。 */
    val heroGap: Dp = 16.dp

    /**
     * caption 的起始缩进 = 行内缩 + 图标位宽 + 图标间距，让说明文字与标题**左边缘对齐**。
     * 写成推导而不是再抄一个魔数：三个数任何一颗跟着设计稿动，缩进自己跟上。
     */
    val captionStart: Dp = navRowHorizontalPadding + navGlyphSlotWidth + navRowGap
}
