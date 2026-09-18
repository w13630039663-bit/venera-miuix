package com.venera.compose.components.venera

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * @param onClick 可空；提供时整卡可点击，未提供时纯展示。
 */
@Composable
fun VeneraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = VeneraTokens
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier),
        cornerRadius = tokens.shape.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.cardContentPadding),
            content = content,
        )
    }
}
