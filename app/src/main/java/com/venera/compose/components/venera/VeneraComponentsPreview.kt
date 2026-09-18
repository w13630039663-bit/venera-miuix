package com.venera.compose.components.venera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.venera.compose.data.prefs.AppearanceStyle
import com.venera.compose.ui.tokens.VeneraPreviewTheme
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 组件层统一 Preview 矩阵。
 *
 * 覆盖：MIUIX Light / MIUIX Dark / MD3 Light / MD3 Dark。
 * 每个组合展示关键状态：normal / selected / disabled / loading。
 *
 * 关于 error 状态：VeneraCover 的错误态需要真实网络失败才能触发，
 * 静态 Preview 无法构造，故不展示（已在组件内通过 onError 处理）。
 */

@Composable
private fun ChipRow() {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space6),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing),
    ) {
        // normal
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            VeneraChip(text = "添加条件", leadingIcon = Icons.Outlined.Add)
            VeneraChip(text = "已选条件", trailingIcon = Icons.Outlined.Close, selected = true)
            VeneraTagChip(text = "同人")
        }
        // selected
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            VeneraChip(text = "已选中", selected = true)
            VeneraTagChip(text = "选中标签", selected = true)
        }
        // disabled
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            VeneraChip(text = "不可用", enabled = false)
            VeneraTagChip(text = "禁用标签", enabled = false)
        }
        // with leading icon
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            VeneraChip(text = "搜索", leadingIcon = Icons.Outlined.Search, selected = true)
        }
        // 纯展示（onClick = null）：无按压反馈，用于卡片上的只读标签
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            VeneraChip(text = "只读", onClick = null)
            VeneraTagChip(text = "只读标签", onClick = null)
        }
    }
}

/*
 * ── 交互状态覆盖说明 ──
 *
 * VeneraChip 的 4 个交互状态中，只有 2 个能在静态 Preview 中呈现：
 *
 *  | 状态     | Preview 可呈现 | 呈现方式 |
 *  |----------|---------------|---------|
 *  | selected | ✅ 可          | 传 selected = true |
 *  | disabled | ✅ 可          | 传 enabled = false |
 *  | pressed  | ❌ 不可        | 需要真实指针按下，Preview 无输入源 |
 *  | focused  | ❌ 不可        | 需要键盘/D-pad 焦点事件 |
 *
 * pressed / focused 由 MutableInteractionSource 驱动（collectIsPressedAsState /
 * collectIsFocusedAsState），属运行时交互，只能在设备上验证。
 * 其视觉效果为：scale 1.0 → 0.96、alpha 1.0 → 0.88、focused 时描边转 primary。
 */

@Composable
private fun CardAndCoverRow() {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space6),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
    ) {
        // 正常卡片：封面 + SourceBadge 覆盖层
        Row(Modifier.width(tokens.spacing.comicCardMinWidth)) {
            VeneraCard {
                VeneraCover(url = "", contentDescription = "空 url 显示占位") {
                    VeneraSourceBadge(name = "Picacg")
                }
                Spacer(Modifier.height(tokens.spacing.cardCoverGap))
                Text(
                    text = "卡片标题（两行省略示例）",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textPrimary,
                )
            }
        }
        // 加载态卡片：骨架
        Row(Modifier.width(tokens.spacing.comicCardMinWidth)) {
            VeneraCard {
                VeneraCoverShimmer()
                Spacer(Modifier.height(tokens.spacing.cardCoverGap))
                VeneraShimmer(Modifier.fillMaxWidth().height(tokens.spacing.barHeight))
            }
        }
    }
}

@Composable
private fun ComponentMatrix() {
    Column(Modifier.fillMaxWidth()) {
        ChipRow()
        CardAndCoverRow()
    }
}

@Preview(name = "Components MIUIX Light", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMiuixLight() {
    VeneraPreviewTheme(AppearanceStyle.MIUIX, dark = false) { ComponentMatrix() }
}

@Preview(name = "Components MIUIX Dark", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMiuixDark() {
    VeneraPreviewTheme(AppearanceStyle.MIUIX, dark = true) { ComponentMatrix() }
}

@Preview(name = "Components MD3 Light", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMd3Light() {
    VeneraPreviewTheme(AppearanceStyle.MD3, dark = false) { ComponentMatrix() }
}

@Preview(name = "Components MD3 Dark", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMd3Dark() {
    VeneraPreviewTheme(AppearanceStyle.MD3, dark = true) { ComponentMatrix() }
}
