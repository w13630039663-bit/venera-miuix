package com.venera.compose.ui.tokens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.defaultTextStyles

/**
 * 把 [VeneraTypographyTokens] 这份表喂给 miuix **自己**用的 `TextStyles`。
 *
 * ## 为什么这一步在今天几乎看不见效果，却仍然要做
 *
 * `VeneraTheme` 原先只把 colors 交给 `MiuixTheme`，字阶一律让库自带默认
 * （`miuix-ui 0.9.4-rc01` 的 `theme/TextStyles.kt`：main 17 / body1 16 / body2 14 /
 * footnote1 13 / footnote2 11 / subtitle 14+Bold / title1 32 / title2 24 / title3 20 /
 * title4 18 / headline1 17 / headline2 16 / button 17 / paragraph 17 + 1.2em）。
 * 而 [MiuixTypography] 是按 `docs/rounds/font-scale-pixez-alignment-2026-09.md` 第 4 节
 * **逐格对齐到那一套官方值**的，所以今天两边相等 —— 但那是**巧合式相等**：
 * 两份真相各写一处，改一处另一处不动，屏上就会出现"同屏两套字号"。
 * 接上之后，miuix 组件的字与页面里 `tokens.type.*` 的字是**同一份表**，改一处两边一起改。
 *
 * ## 唯一没有对应格的那一档
 *
 * `title4` 库给 18sp，而我们这张表里没有 18 这一格（MIUIX 官方尺寸全集是
 * {11,13,14,16,17,18,20,24,32}，我们把相邻两档用作 statNumber=20 与 sectionTitle=14）。
 * 这里**原样沿用库默认**：既不为了"对齐"造一个没有出处的数，也不把 18 压成 16 或 20 ——
 * 那是观感改动，不是统一。
 *
 * ## 字族（2026-11-09）
 *
 * 每格显式带上 `t.fontFamily`（= [androidx.compose.ui.text.font.FontFamily.Default]）。
 * 库默认表本就不设字族（落到平台 sans-serif），所以这是**等值显式化**：
 * 把"Miuix 与 Material3 共用同一系统字体来源"从巧合变成约定，
 * 也让下面 `defaultTextStyles().title4` 那一格有了对照基准 —— 它同样不设字族，两边同源。
 * 行高除 `paragraph` 外不批量补：库只有这一档带行高，给单行部件补行高会顶开固定高度容器，
 * 属于布局改动（本批红线）。
 */
fun miuixTextStylesOf(t: VeneraTypographyTokens): TextStyles = TextStyles(
    // 书写形态：`TextStyle(fontSize = …)` 首参紧邻 —— `TopBarTitleScaleTest` 的子串锚点吃这一形态，
    // 字族作为尾参换行补上（换行只拆行不拆"TextStyle(fontSize"这一段）。
    main = TextStyle(
        fontSize = t.itemTitle,
        fontFamily = t.fontFamily,
    ),
    // lineHeight 这一条库里是 1.2f.em（`TextStyles.kt` 的 Paragraph），是唯一带行高的角色；
    // 我们的表只管字号不管行高，所以这里照抄库值而不是自造。
    paragraph = TextStyle(
        fontSize = t.itemTitle,
        lineHeight = 1.2f.em,
        fontFamily = t.fontFamily,
    ),
    body1 = TextStyle(
        fontSize = t.chevron,
        fontFamily = t.fontFamily,
    ),
    body2 = TextStyle(
        fontSize = t.body,
        fontFamily = t.fontFamily,
    ),
    button = TextStyle(
        fontSize = t.itemTitle,
        fontFamily = t.fontFamily,
    ),
    footnote1 = TextStyle(
        fontSize = t.caption,
        fontFamily = t.fontFamily,
    ),
    footnote2 = TextStyle(
        fontSize = t.overline,
        fontFamily = t.fontFamily,
    ),
    headline1 = TextStyle(
        fontSize = t.itemTitle,
        fontFamily = t.fontFamily,
    ),
    headline2 = TextStyle(
        fontSize = t.chevron,
        fontFamily = t.fontFamily,
    ),
    // 层级靠字重不靠字号：库里 subtitle 就是 14 + Bold，与我们 body(14)/caption(13)
    // 只差 1sp 却分两档是同一件事。
    subtitle = TextStyle(
        fontSize = t.sectionTitle,
        fontWeight = t.weightBold,
        fontFamily = t.fontFamily,
    ),
    title1 = TextStyle(
        fontSize = t.display,
        fontFamily = t.fontFamily,
    ),
    title2 = TextStyle(
        fontSize = t.screenTitle,
        fontFamily = t.fontFamily,
    ),
    title3 = TextStyle(
        fontSize = t.statNumber,
        fontFamily = t.fontFamily,
    ),
    // 库里 title4 = 18sp，而我们这张表没有 18 这一格（MIUIX 官方尺寸全集是
    // {11,13,14,16,17,18,20,24,32}，相邻两档我们用作 sectionTitle=14 与 statNumber=20）。
    // 原样沿用库默认那一档：既不造一个没有出处的数，也不把 18 压成 16 或 20 —— 那是观感改动。
    // 走 defaultTextStyles() 而不是 MiuixTheme.textStyles：后者的取值是 @Composable 的，
    // 会把这颗纯映射函数拖成组合函数（编译期就拦住了）。
    title4 = defaultTextStyles().title4,
)
