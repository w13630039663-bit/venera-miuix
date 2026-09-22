package com.venera.compose.ui.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 字号 / 字重 Token。
 *
 * 规则（A3.1）：除本文件外，任何 UI 代码不得出现 `xx.sp` 或 `FontWeight.Wxxx`。
 *
 * 命名采用「语义角色」而不是「数值」：页面写 `VeneraType.Body` 而不是 `16.sp`，
 * 这样 MD3/MIUIX 两套字号阶梯可以各自演化而页面不动。
 *
 * 注意：这些是**尺寸**层，与 miuix 的 TextStyles（主题级 TextStyle）互补 ——
 * 页面用 miuix 的 Text/Material3 的 Text 渲染时，从本 Token 取 size/weight。
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
)

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
)
