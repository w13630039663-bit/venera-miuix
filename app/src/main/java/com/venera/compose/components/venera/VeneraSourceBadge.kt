package com.venera.compose.components.venera

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

/**
 * Venera 漫画源标识徽章（语义层）。
 *
 * ── 为什么它**不是** VeneraChip ──
 * 用户与规格都明确：Source 是 **Source Identity**，不是普通 Tag。二者必须明显区分：
 *
 * | | VeneraTagChip | VeneraSourceBadge |
 * |---|---|---|
 * | 语义 | 内容分类（题材/属性） | 内容**来源**（哪个站） |
 * | 底色 | 表面叠层（跟主题走） | **固定深色**（保证压在任何封面上都可读） |
 * | 形状 | 胶囊（extraLarge） | 小圆角（extraSmall） |
 * | 位置 | 卡片正文区 | **封面左上角**覆盖 |
 * | 字号 | caption | badge（更小） |
 *
 * 因此它不复用 VeneraChip，而是独立实现——这不是重复，是刻意的语义分离。
 *
 * 底色使用固定深色而非主题色：封面颜色不可控（可能是任何图），
 * 只有固定的高对比底板才能保证 source 名永远可读。
 *
 * @param name 源名称（如 "Picacg" / "nhentai"）。过长时单行省略。
 * @param onClick 可空；提供时点击可切换/筛选到该源。
 */
@Composable
fun BoxScope.VeneraSourceBadge(
    name: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    if (name.isBlank()) return
    val tokens = VeneraTokens
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Surface(
        shape = RoundedCornerShape(tokens.shape.extraSmall),
        // 固定深色底板：压在任意封面图上都可读（见上方说明）
        color = StatusColors.BadgeSurface,
        modifier = modifier
            .align(Alignment.TopStart)
            .padding(tokens.spacing.badgeInset)
            .then(clickModifier),
    ) {
        Text(
            text = name,
            fontSize = tokens.type.badge,
            fontWeight = tokens.type.weightSemibold,
            color = StatusColors.OnBadgeSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                horizontal = tokens.spacing.badgeHorizontalPadding,
                vertical = tokens.spacing.badgeVerticalPadding,
            ),
        )
    }
}
