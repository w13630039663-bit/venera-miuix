package com.venera.compose.components.venera

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.venera.compose.ui.tokens.VeneraGlassRole
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Card

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
 */
@Composable
fun VeneraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
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
        onClick = onClick,
        onLongPress = onLongClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.cardContentPadding),
            content = content,
        )
    }
}
