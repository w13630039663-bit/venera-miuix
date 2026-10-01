package com.venera.compose.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.venera.compose.components.venera.veneraGlassSurface
import com.venera.compose.feature.LocalVeneraDarkTheme
import com.venera.compose.ui.tokens.GallerySheetSection
import com.venera.compose.ui.tokens.GallerySheetSectionColors
import com.venera.compose.ui.tokens.VeneraGlassRole
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 「关于这张图」半模态里**一块**的容器与它的节标题（2026-10-01 第三次重排）。
 *
 * ## 为什么不再用 `VeneraCard`
 *
 * 上一版每一块都是一张 `VeneraCard`（吃 `surfaceContainerHigh`），半模态自己是
 * `surfaceContainerLow`。用户报"分区的色块不明显"，真机截图逐像素量下来是**零差**：
 * 动态取色下这两档只差一个色调步，再乘上玻璃那 0.22 的容器 alpha 就没了
 * （读数与成因见 [GallerySheetSectionColors] 的头注）。
 * 也就是说问题不在"卡片画得淡"，而在**把分区的可见性押在了色调阶梯上** ——
 * 那条阶梯随壁纸、随深浅档、随材质轴三处都会变，押不起。
 *
 * 所以这一版把两件事分开，各自用不会塌的载体：
 * - **"这是一块"**：[tint] 一层色相洗底 + 一圈 `outlineVariant` 发丝描边。
 *   描边不随主题明暗变，洗底差的是**色相**而不是明度 —— 两者在四档主题里都立得住。
 * - **"这是哪一块"**：节标题前那枚 28dp 的色相徽标（[SheetSectionBadge]）。
 *
 * 材质轴照样接：玻璃档开着时块面仍走 [veneraGlassSurface]（关闭档那一句是纯 no-op），
 * 所以"开关玻璃"这件事的观感差异在这一页没有被吃掉。
 */
@Composable
internal fun SheetBlock(
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = VeneraTokens
    val cornerRadius = tokens.shape.card
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .veneraGlassSurface(VeneraGlassRole.CARD, cornerRadius)
            .background(tint.copy(alpha = GallerySheetSectionColors.WashAlpha))
            .border(tokens.spacing.hairline, tokens.color.outlineVariant, shape)
            .padding(tokens.spacing.cardContentPadding),
        // 块内**只有两种孩子**：节标题 + 一块内容（各自的内部间距由内容自己管）。
        // 所以这里的行距就是"标题与内容之间"那一档，不掺别的东西。
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space5),
        content = content,
    )
}

/** 四块的色相，按当前深浅档取一份 —— 判据在 [GallerySheetSectionColors.of]，这里只接线。 */
@Composable
internal fun sheetSectionTint(section: GallerySheetSection): Color =
    GallerySheetSectionColors.of(section, LocalVeneraDarkTheme.current)

/**
 * 一块的**节标题**：色相徽标 + 名字（+ 右端一枚只作提示的动作字）。
 *
 * [onAction] 非空时**整行可点**（「展开全部」/「收起」），右端那句文字本身不带点击 ——
 * 12sp 的四个字只有 48dp 宽，单给它挂点击就是让人拿手指去戳一排小字。
 * 这条口径与上一版一致，没有跟着这次重排改。
 */
@Composable
internal fun SheetSectionHeader(
    icon: ImageVector,
    title: String,
    tint: Color,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.shape.small))
            .then(if (onAction == null) Modifier else Modifier.clickable(onClick = onAction)),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetSectionBadge(icon = icon, tint = tint)
        Text(
            text = title,
            fontSize = tokens.type.itemTitle,
            fontWeight = tokens.type.weightSemibold,
            color = tokens.color.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                text = action,
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
                maxLines = 1,
            )
            Text(
                text = "›",
                fontSize = tokens.type.chevron,
                color = tokens.color.textTertiary,
            )
        }
    }
}

/**
 * 节标题前那枚**色相徽标**：28dp 圆角方块，15% 本色底 + 100% 本色字形。
 *
 * 这不是新画法：与设置页那枚 `SectionIconBadge` **逐参数相同**
 * （`badgeSize` / `shape.badge` / `badgeAlpha` / `badgeIconSize` 四个现成 token，一个都没新造），
 * 因为"一屏里的徽标只有一种画法"这件事比"这两页各调各的手感"重要。
 * 两份实现（不共用函数）是**分层**的代价：那一枚在 `feature/settings` 里，
 * 让 `gallery/ui` 去 import 它会把画廊挂到设置域上；换来的只是十几行同形代码。
 *
 * 色相承担"这是哪一块"，字形承担"这块是什么" —— 色盲档下色不成立，字还在。
 */
@Composable
internal fun SheetSectionBadge(icon: ImageVector, tint: Color) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .size(tokens.spacing.badgeSize)
            .clip(RoundedCornerShape(tokens.shape.badge))
            .background(tint.copy(alpha = GallerySheetSectionColors.BadgeAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(tokens.spacing.badgeIconSize),
        )
    }
}
