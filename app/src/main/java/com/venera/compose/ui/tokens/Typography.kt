package com.venera.compose.ui.tokens

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 字号 / 字重 / 字族 / 行高 / 字距 Token。
 *
 * 规则（A3.1）：除本文件外，任何 UI 代码不得出现 `xx.sp` 或 `FontWeight.Wxxx`。
 *
 * 命名采用「语义角色」而不是「数值」：页面写 `VeneraType.Body` 而不是 `16.sp`，
 * 这样 MD3/MIUIX 两套字号阶梯可以各自演化而页面不动。
 *
 * 注意：这些是**尺寸**层，与 miuix 的 TextStyles（主题级 TextStyle）互补 ——
 * 页面用 miuix 的 Text/Material3 的 Text 渲染时，从本 Token 取 size/weight。
 *
 * ── 2026-11-09 补的三个维度，以及为什么它们不改观感 ──
 *
 * [fontFamily] 恒为 [FontFamily.Default]：Android 侧这就是「跟随系统字体」的唯一正确写法 ——
 * Default 解析到平台 sans-serif，用户在系统设置里换字体、开非线性字号缩放都自动生效。
 * 仓内不内置 MiSans/Roboto 任何固定字体（res 里零枚 .ttf，已核）。
 *
 * [lineHeightMd]/[lineHeightBody]/[lineHeightCaption]/[lineHeightDisplay] 只作**迁移落点**：
 * 全仓裸 `lineHeight = 18.sp` 这类存量在换 token 时必须逐档等值落地（MD3 阶梯照 MD3 官方
 * 默认行高定档；MIUIX 阶梯按其自身 display×1.25 / 其余×1.43 的既有比例定档 —— 这两个比例
 * 正是存量 13→18、14→20、12→17 反推出来的，不是新造数）。迁移前后渲染像素不变。
 * 不做的事：不给 MiuixTheme 的样式槽批量补行高（库只有 paragraph 一档带行高，
 * 给单行部件补行高会顶开固定高度容器，那是布局改动）。
 *
 * [letterSpacingWide] 是现量 `overline 0.16.sp` 的收口，其余档取 0 = 库现状。
 */
@Immutable
data class VeneraTypographyTokens(
    /** 页面大标题（如「设置」）。 */
    val screenTitle: TextUnit,
    /** 分组内条目标题。 */
    val itemTitle: TextUnit,
    /** 正文。 */
    val body: TextUnit,
    /** 次要说明文字。 */
    val caption: TextUnit,
    /** 极小标注（版本号、脚注）。 */
    val overline: TextUnit,
    /** 分区小标题。 */
    val sectionTitle: TextUnit,
    /** 行尾导航指示符「›」。 */
    val chevron: TextUnit,
    /** 大号数字/标题强调。 */
    val display: TextUnit,

    /** 统计数字（首页「今日页数」等大号数值）。 */
    val statNumber: TextUnit,
    /** 角标 / 微型标签（「更新」角标、进度数字）。 */
    val badge: TextUnit,

    val weightRegular: FontWeight,
    val weightMedium: FontWeight,
    val weightSemibold: FontWeight,
    val weightBold: FontWeight,

    // ── 2026-11-09：字族 / 行高 / 字距 ──
    /** 唯一合法字族来源：Android 系统字体（见文件头注）。 */
    val fontFamily: FontFamily,
    /** itemTitle 档行高（MD3 28 / MIUIX 24）。 */
    val lineHeightMd: TextUnit,
    /** body 档行高（MD3 20 / MIUIX 20）。 */
    val lineHeightBody: TextUnit,
    /** caption 档行高（MD3 16 / MIUIX 18）。 */
    val lineHeightCaption: TextUnit,
    /** display 档行高（MD3 40 / MIUIX 40）。 */
    val lineHeightDisplay: TextUnit,
    /** 常规字距（两阶均 0 = 现状）。 */
    val letterSpacingNormal: TextUnit,
    /** 小字号加宽字距（现量 overline 0.16.sp 的收口）。 */
    val letterSpacingWide: TextUnit,
)

/**
 * 极小号（11sp 档）现量字距。
 *
 * 单拎成常量而不进 [VeneraTypographyTokens] 的 letterSpacing 家族：现量只有这一处
 * （HomeHomeWidgets 的时段问候），它是「装饰性小字母间距」而不是某档字号的配套字距，
 * 塞进表里会误导下一个读者以为 overline 全场景都加宽。
 */
val VeneraOverlineLetterSpacing = 0.16.sp

/** MD3 字号阶梯：相对紧凑，层级靠字重拉开。 */
val Md3Typography = VeneraTypographyTokens(
    screenTitle = 22.sp,
    itemTitle = 16.sp,
    body = 14.sp,
    caption = 12.sp,
    overline = 11.sp,
    sectionTitle = 13.sp,
    chevron = 18.sp,
    display = 28.sp,
    statNumber = 20.sp,
    badge = 10.sp,
    weightRegular = FontWeight.Normal,
    weightMedium = FontWeight.Medium,
    weightSemibold = FontWeight.SemiBold,
    weightBold = FontWeight.Bold,
    fontFamily = FontFamily.Default,
    // MD3 官方默认行高（Material3 Typography 的 titleMedium/bodyMedium/bodySmall/displayLarge）。
    lineHeightMd = 28.sp,
    lineHeightBody = 20.sp,
    lineHeightCaption = 16.sp,
    lineHeightDisplay = 40.sp,
    letterSpacingNormal = 0.sp,
    letterSpacingWide = 0.16.sp,
)

/**
 * MIUIX 字号阶梯：每个值都取 miuix `TextStyles` 的官方原值，不造库外数字。
 * 官方尺寸全集 {11, 13, 14, 16, 17, 18, 20, 24, 32}；对账见 font-scale-pixez-alignment-2026-09.md 第 4 节。
 *
 * body 14 与 caption 13 只差 1sp 是库本身的档位限制（body2 / footnote1），不是笔误：
 * 这一层靠 weightMedium/weightBold 拉层级，与官方 subtitle（14 + Bold）同构。
 */
val MiuixTypography = VeneraTypographyTokens(
    screenTitle = 24.sp,
    itemTitle = 17.sp,
    body = 14.sp,
    caption = 13.sp,
    overline = 11.sp,
    sectionTitle = 14.sp,
    chevron = 16.sp,
    display = 32.sp,
    statNumber = 20.sp,
    badge = 11.sp,
    weightRegular = FontWeight.Normal,
    weightMedium = FontWeight.Medium,
    weightSemibold = FontWeight.SemiBold,
    weightBold = FontWeight.Bold,
    fontFamily = FontFamily.Default,
    // 行高比例是现量反推：13→18、14→20（×1.43），display 档 ×1.25（32→40）。
    // 与 MD3 同屏的 14→20/12→17 只差 1sp 是两套阶梯各自的既有事实，统一时不动渲染。
    lineHeightMd = 24.sp,
    lineHeightBody = 20.sp,
    lineHeightCaption = 18.sp,
    lineHeightDisplay = 40.sp,
    letterSpacingNormal = 0.sp,
    letterSpacingWide = 0.16.sp,
)

/**
 * 把字号阶梯桥成 Material3 的 `Typography`（**逐槽等值**，见各槽注释的出处）。
 *
 * 为什么需要这座桥：`VeneraTheme` 原先不把 typography 交给 `MaterialTheme`，
 * MD3 件（Button/TextField/Dialog/…）读到的 `MaterialTheme.typography` 一直是**库默认表** ——
 * 它与我们的阶梯是另一处「两份真相」。接上之后槽值逐格取自阶梯（MD3 阶梯的 screenTitle=22
 * 就是 MD3 官方 titleLarge=22，等值），字族统一 [FontFamily.Default]。
 *
 * 阶梯里没有的 15/16/21/25/57 四档**沿用 MD3 官方值**并注明：库默认表今天就在屏上生效
 * （OnboardingScreen、DownloadsLocalScreen 直接读 `MaterialTheme.typography.*`），
 * 把它们压进阶梯档位是改观感，不是统一。
 */
fun veneraMaterial3TypographyOf(t: VeneraTypographyTokens): Typography = Typography(
    displayLarge = TextStyle(
        fontSize = 57.sp, lineHeight = 64.sp, fontFamily = t.fontFamily,
        fontWeight = t.weightRegular, letterSpacing = t.letterSpacingNormal,
    ),
    displayMedium = TextStyle(
        fontSize = t.display, lineHeight = t.lineHeightDisplay, fontFamily = t.fontFamily,
        fontWeight = t.weightRegular, letterSpacing = t.letterSpacingNormal,
    ),
    displaySmall = TextStyle(
        fontSize = t.screenTitle, lineHeight = 28.sp, fontFamily = t.fontFamily,
        fontWeight = t.weightRegular, letterSpacing = t.letterSpacingNormal,
    ),
    headlineLarge = TextStyle(
        fontSize = 32.sp, lineHeight = 40.sp, fontFamily = t.fontFamily,
        fontWeight = t.weightRegular, letterSpacing = t.letterSpacingNormal,
    ),
    headlineMedium = TextStyle(
        fontSize = t.statNumber, lineHeight = t.lineHeightDisplay, fontFamily = t.fontFamily,
        fontWeight = t.weightRegular, letterSpacing = t.letterSpacingNormal,
    ),
    headlineSmall = TextStyle(
        fontSize = t.screenTitle, lineHeight = t.lineHeightMd, fontFamily = t.fontFamily,
        fontWeight = t.weightRegular, letterSpacing = t.letterSpacingNormal,
    ),
    titleLarge = TextStyle(
        fontSize = t.screenTitle, lineHeight = t.lineHeightMd, fontFamily = t.fontFamily,
        fontWeight = t.weightRegular, letterSpacing = t.letterSpacingNormal,
    ),
    titleMedium = TextStyle(
        fontSize = t.itemTitle, lineHeight = t.lineHeightMd, fontFamily = t.fontFamily,
        fontWeight = t.weightMedium, letterSpacing = t.letterSpacingNormal,
    ),
    titleSmall = TextStyle(
        fontSize = t.sectionTitle, lineHeight = t.lineHeightBody, fontFamily = t.fontFamily,
        fontWeight = t.weightMedium, letterSpacing = t.letterSpacingNormal,
    ),
    bodyLarge = TextStyle(
        fontSize = t.itemTitle, lineHeight = t.lineHeightMd, fontFamily = t.fontFamily,
        fontWeight = t.weightRegular, letterSpacing = t.letterSpacingNormal,
    ),
    bodyMedium = TextStyle(
        fontSize = t.body, lineHeight = t.lineHeightBody, fontFamily = t.fontFamily,
        fontWeight = t.weightRegular, letterSpacing = t.letterSpacingNormal,
    ),
    bodySmall = TextStyle(
        fontSize = t.caption, lineHeight = t.lineHeightCaption, fontFamily = t.fontFamily,
        fontWeight = t.weightRegular, letterSpacing = t.letterSpacingNormal,
    ),
    labelLarge = TextStyle(
        fontSize = t.itemTitle, lineHeight = t.lineHeightBody, fontFamily = t.fontFamily,
        fontWeight = t.weightMedium, letterSpacing = t.letterSpacingNormal,
    ),
    labelMedium = TextStyle(
        fontSize = t.overline, lineHeight = t.lineHeightCaption, fontFamily = t.fontFamily,
        fontWeight = t.weightMedium, letterSpacing = t.letterSpacingNormal,
    ),
    labelSmall = TextStyle(
        fontSize = t.badge, lineHeight = 16.sp, fontFamily = t.fontFamily,
        fontWeight = t.weightMedium, letterSpacing = t.letterSpacingNormal,
    ),
)
