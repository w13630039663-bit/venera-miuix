package com.venera.compose.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text

/**
 * 设置页通用组件。
 *
 * 本轮改造目标（A2 / A3.1）：
 *  - 清零所有硬编码：颜色走 VeneraTokens.color，字号走 VeneraTokens.type，
 *    间距走 VeneraTokens.spacing，圆角走 VeneraTokens.shape。
 *  - 组件来源统一为 miuix-compose（Card / Text）+ Material3 补齐
 *    （IconButton / Switch / Slider / AlertDialog —— miuix 侧对应组件签名不同，
 *     为避免同页混用两套语义，此处保持「容器用 miuix、控件用 M3」的既有边界，不新增混用）。
 */

/** 对应原版 SettingsSection：标题在卡片外，行靠留白区分。 */
@Composable
internal fun SettingsGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val tokens = VeneraTokens
    if (title != null) {
        Text(
            text = title,
            fontSize = tokens.type.sectionTitle,
            fontWeight = tokens.type.weightSemibold,
            color = tokens.color.textSecondary,
            modifier = Modifier.padding(
                start = tokens.spacing.rowHorizontal,
                top = tokens.spacing.rowHorizontal,
                bottom = tokens.spacing.space3,
            ),
        )
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space2),
            content = content,
        )
    }
    Spacer(Modifier.height(tokens.spacing.space4))
}

@Composable
private fun RowScope.SettingLabel(title: String, summary: String?, enabled: Boolean = true) {
    val tokens = VeneraTokens
    Column(Modifier.weight(1f)) {
        Text(
            text = title,
            fontSize = tokens.type.itemTitle,
            fontWeight = tokens.type.weightMedium,
            color = if (enabled) tokens.color.textPrimary else tokens.color.textDisabled,
        )
        if (!summary.isNullOrBlank()) {
            Text(
                text = summary,
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(top = tokens.spacing.space1),
            )
        }
    }
}

@Composable
internal fun SettingsAction(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val tokens = VeneraTokens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
            .padding(
                horizontal = tokens.spacing.rowHorizontal,
                vertical = tokens.spacing.rowVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingLabel(title, summary, enabled)
        if (enabled && onClick != null) {
            Text(
                text = "›",
                fontSize = tokens.type.chevron,
                color = tokens.color.textTertiary,
            )
        }
    }
}

/** 未实现的设置不分配虚构默认值，直接说明能力缺口。 */
@Composable
internal fun UnsupportedSetting(title: String, reason: String) =
    SettingsAction(title, "暂不支持：$reason", enabled = false)

@Composable
internal fun SettingsToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val tokens = VeneraTokens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(
                horizontal = tokens.spacing.rowHorizontal,
                vertical = tokens.spacing.space5,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingLabel(title, summary, enabled)
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
        )
    }
}

@Composable
internal fun SettingsSelect(
    title: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    summary: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == value }?.second ?: "未识别的已保存值：$value"
    SettingsAction(title, listOfNotNull(label, summary).joinToString("\n")) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { (key, text) ->
                        SettingsAction(text, if (value == key) "已选择" else null) {
                            onSelected(key)
                            open = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("取消") } },
        )
    }
}

@Composable
internal fun SettingsSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    steps: Int = 0,
    suffix: String = "",
) {
    val tokens = VeneraTokens
    Column(
        Modifier.padding(
            horizontal = tokens.spacing.rowHorizontal,
            vertical = tokens.spacing.space5,
        )
    ) {
        Text(
            text = "$title · " + (if (value % 1f == 0f) value.toInt().toString() else value.toString()) + suffix,
            fontSize = tokens.type.itemTitle,
        )
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
internal fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val tokens = VeneraTokens
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = tokens.spacing.space2,
                    vertical = tokens.spacing.space2,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = tokens.color.textPrimary,
                )
            }
            Text(
                text = title,
                fontSize = tokens.type.screenTitle,
                fontWeight = tokens.type.weightBold,
                color = tokens.color.textPrimary,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = tokens.spacing.screenHorizontal,
                    end = tokens.spacing.screenHorizontal,
                    bottom = tokens.spacing.bottomBarClearance,
                ),
            content = content,
        )
    }
}

@Composable
internal fun SectionIconBadge(color: Color, content: @Composable () -> Unit) {
    val tokens = VeneraTokens
    Box(
        Modifier
            .size(tokens.spacing.badgeSize)
            .clip(RoundedCornerShape(tokens.shape.badge))
            .background(color.copy(alpha = tokens.current.badgeAlpha)),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
