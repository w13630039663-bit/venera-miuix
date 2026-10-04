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
 */
fun miuixTextStylesOf(t: VeneraTypographyTokens): TextStyles = TextStyles(
    main = TextStyle(fontSize = t.itemTitle),
    // lineHeight 这一条库里是 1.2f.em（`TextStyles.kt` 的 Paragraph），是唯一带行高的角色；
    // 我们的表只管字号不管行高，所以这里照抄库值而不是自造。
    paragraph = TextStyle(fontSize = t.itemTitle, lineHeight = 1.2f.em),
    body1 = TextStyle(fontSize = t.chevron),
    body2 = TextStyle(fontSize = t.body),
    button = TextStyle(fontSize = t.itemTitle),
    footnote1 = TextStyle(fontSize = t.caption),
    footnote2 = TextStyle(fontSize = t.overline),
    headline1 = TextStyle(fontSize = t.itemTitle),
    headline2 = TextStyle(fontSize = t.chevron),
    // 层级靠字重不靠字号：库里 subtitle 就是 14 + Bold，与我们 body(14)/caption(13)
    // 只差 1sp 却分两档是同一件事。
    subtitle = TextStyle(fontSize = t.sectionTitle, fontWeight = t.weightBold),
    title1 = TextStyle(fontSize = t.display),
    title2 = TextStyle(fontSize = t.screenTitle),
    title3 = TextStyle(fontSize = t.statNumber),
    // 库里 title4 = 18sp，而我们这张表没有 18 这一格（MIUIX 官方尺寸全集是
    // {11,13,14,16,17,18,20,24,32}，相邻两档我们用作 sectionTitle=14 与 statNumber=20）。
    // 原样沿用库默认那一档：既不造一个没有出处的数，也不把 18 压成 16 或 20 —— 那是观感改动。
    // 走 defaultTextStyles() 而不是 MiuixTheme.textStyles：后者的取值是 @Composable 的，
    // 会把这颗纯映射函数拖成组合函数（编译期就拦住了）。
    title4 = defaultTextStyles().title4,
)
