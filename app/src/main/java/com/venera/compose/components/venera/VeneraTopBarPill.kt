package com.venera.compose.components.venera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.textureBlur
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 顶栏按钮能不能磨砂。
 *
 * 两个"不能"各挡一条，任一不满足都必须走**可见的**回落底，而不是什么都不画：
 * ① 没有采样层（这一屏没接 `rememberTopBarBackdrop()`，或它在 Dialog 窗口里）；
 * ② 平台不支持 RuntimeShader。
 * 少了任何一条判据，症状都是"顶栏按钮的底板整个没了"——那不是磨砂淡一点，是掉档。
 */
fun veneraPillFrosted(backdropPresent: Boolean, shaderSupported: Boolean): Boolean =
    backdropPresent && shaderSupported

/**
 * 顶栏图标按钮的**唯一形态**：磨砂圆座。
 *
 * 出处是收藏页右上角那颗布局切换钮（原来只在那一页私有实现过一份），
 * 配方逐字沿用，不自造数字：圆座 40dp（`tokens.spacing.iconButtonSize`）、
 * `textureBlur` 半径 10f、补底 `surface @ 0.16f`（与 `VeneraTopAppBar` 栏级背板同档，
 * 两处玻璃不同档就会看出"按钮浮在另一层上"）、描边 `hairline` + `outlineVariant`。
 *
 * **视觉 40dp、触摸区 48dp**：外层 48dp 只负责点击与触达，磨砂与描边都在内层 40dp 上。
 * 这一条是刻意的不对齐——`VeneraIconButton` 仍是"整颗 48dp"，因为它服务的是内容区密度，
 * 那里一屏二十颗，不能个个带外壳。
 *
 * 按压反馈自己做（scale 1→0.96、alpha 1→0.88，与 `VeneraChip` 同口径），
 * 并把 `indication` 关掉：默认高亮画在 48dp 外层上，会溢到 40dp 圆座外面形成一圈光晕。
 *
 * @param content 图标内容（玻璃只挂外壳，不挂图标 —— 与 `VeneraIconButton` 同一条规则）。
 */
@Composable
fun VeneraTopBarPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = VeneraTokens
    val backdrop = LocalTopBarBackdrop.current
    val frosted = veneraPillFrosted(backdrop != null, isRuntimeShaderSupported())
    val circle = RoundedCornerShape(percent = 50)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                val p = if (pressed) 1f else 0f
                scaleX = lerp(1f, 0.96f, p)
                scaleY = lerp(1f, 0.96f, p)
                alpha = lerp(1f, 0.88f, p)
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(tokens.spacing.iconButtonSize)
                .clip(circle)
                .then(
                    if (frosted) {
                        Modifier.textureBlur(
                            backdrop = backdrop!!,
                            shape = circle,
                            blurRadius = 10f,
                            colors = BlurColors(
                                blendColors = listOf(
                                    BlendColorEntry(color = tokens.color.surface.copy(alpha = 0.16f)),
                                ),
                            ),
                        )
                    } else {
                        Modifier.background(
                            tokens.color.surface.copy(alpha = tokens.current.selectedSurfaceAlpha),
                        )
                    },
                )
                .border(tokens.spacing.hairline, tokens.color.outlineVariant, circle),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}
