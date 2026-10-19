package com.venera.compose.components.venera

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.venera.compose.ui.tokens.VeneraGlassRole
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * Venera 通用卡片容器（基础层）。
 *
 * 职责边界（严格遵守分层）：
 *  - 只提供 surface / shape / interaction / theme adaptation。
 *  - **不含任何业务语义**：不出现 comic、comicId、SearchMode、HomeMode 等页面概念。
 *  - 页面若需要"漫画卡"，请组合 VeneraCard + VeneraCover + 文本，而不是给 VeneraCard 加业务参数。
 *
 * 主题适配：
 *  - 底层使用 miuix 的 Card：MIUIX 模式下取 Miuix 原生表面；MD3 模式下 VeneraTheme
 *    已把 Material 色板桥接进 Miuix，因此表面同样正确。
 *  - 圆角取 shape.card（MD3 16dp / MIUIX 18dp），通过 miuix Card 的 cornerRadius 参数传入。
 *
 * 手势：onClick / onLongClick 走 miuix Card 官方可点击重载（内部 squircleSurface +
 * combinedClickable，press 反馈由 miuix 统一处理）。**不要**把 combinedClickable
 * 挂到外层 modifier 上再叠一层——squircle 裁剪层级会吞掉长按手势（真机实测）。
 *
 * @param onClick 可空；提供时整卡可点击。
 * @param onLongClick 可空；提供时整卡响应长按（如多选/移除确认）。
 * @param pressFeedback 按下时的几何反馈。**默认 [PressFeedbackType.None]** —— miuix `Card`
 *   自己的默认档，所以既有的三十几处调用点外观与手感逐字不变（漫画侧那几个页面是冻结区，
 *   不许被顺带改）。
 *
 *   显式传 [VeneraPressFeedback.card] 才会上"下沉"：画廊那几面**图片墙**要的就是它
 *   （口径与理由全在 [VeneraPressFeedback] 的类头注，这里不重复一遍）。
 *
 *   ⚠️ 它**不吃** `onClick == null`：挂上之后 miuix 会把 pressable 无条件接起来，
 *   于是"按得动但什么都不发生"——那是本仓最忌的假开关。不可点的那一档请传 None。
 */
@Composable
fun VeneraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    pressFeedback: PressFeedbackType = PressFeedbackType.None,
    /**
     * 内容**通栏**（封面直接贴到卡边，圆角由 miuix `Card` 自己裁）。
     *
     * 「封面就是这张卡」的那种形状要的就是它。排行榜卡用的是它：
     * 用户 2026-10-17 报「卡面太小了」，实测封面两侧各缩了 8dp `cardContentPadding`，
     * 而设计稿上的封面是**通栏**的（`d2_rank.png`：红图一直顶到卡片边缘，四个圆角就是封面的四个角）。
     *
     * 刻意做成**布尔语义**而不是一个 `contentPadding: Dp` 参数 —— 后者要表达"不要内衬"
     * 就得在调用点写 `0.dp`，而裸 `N.dp` 字面量是本仓封顶的存量（`TokenBudgetFrozenTest`）：
     * 新增尺寸一律要过 token 一遍。布尔不需要任何新数字。
     *
     * 代价要自己负：内衬归零后**文字区得自己补**水平内衬，否则标题会顶到卡边。
     */
    edgeToEdge: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = VeneraTokens
    val cornerRadius = tokens.shape.card
    // 要不要玻璃、容器色透不透明，都由 veneraGlassSurface / veneraGlassCardColors 那一处决定
    // （设置页分组卡走同一对），这里不留第二份判断。
    Card(
        modifier = modifier.veneraGlassSurface(VeneraGlassRole.CARD, cornerRadius),
        cornerRadius = cornerRadius,
        colors = veneraGlassCardColors(),
        pressFeedbackType = pressFeedback,
        onClick = onClick,
        onLongPress = onLongClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // `edgeToEdge` 时**不挂**任何 padding，而不是挂一个 0.dp ——
                // 理由见那个参数的头注（裸 dp 是封顶存量）。
                .then(
                    if (edgeToEdge) {
                        Modifier
                    } else {
                        Modifier.padding(tokens.spacing.cardContentPadding)
                    },
                ),
            content = content,
        )
    }
}
