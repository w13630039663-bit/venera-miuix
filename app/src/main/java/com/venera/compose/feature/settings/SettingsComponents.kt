package com.venera.compose.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
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
import androidx.compose.ui.unit.dp
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

/**
 * 「尚未实现」折叠区。
 *
 * 设置页不再拿一排灰色不可点行当 TODO 看板 —— 既占视觉，又让用户误以为可以改。
 * 未实现项统一收进这个默认折叠的区块；保留/删除判据见 settings-audit-2026-09.md。
 */
@Composable
internal fun SettingsFutureGroup(content: @Composable ColumnScope.() -> Unit) {
    val tokens = VeneraTokens
    var open by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(
                        horizontal = tokens.spacing.rowHorizontal,
                        vertical = tokens.spacing.space5,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "尚未实现",
                        fontSize = tokens.type.itemTitle,
                        fontWeight = tokens.type.weightMedium,
                        color = tokens.color.textSecondary,
                    )
                    Text(
                        text = if (open) "点击收起" else "点击展开查看缺口清单",
                        fontSize = tokens.type.caption,
                        color = tokens.color.textTertiary,
                    )
                }
                Text(
                    text = if (open) "▲" else "▼",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                )
            }
            if (open) {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = tokens.spacing.space2),
                    content = content,
                )
            }
        }
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
    summary: String = "",
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
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
            )
        }
    }
}

@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    /** 展开态大标题。全站顶栏都是"短标题折叠 + 长标题展开"两级（见 DownloadScreen /
     *  LocalComicScreen / SettingsHome），缺了它大标题折叠就没有视觉变化。 */
    largeTitle: String = title,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = VeneraTokens
    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(topBarBehavior.nestedScrollConnection)
                .blurBackdropSource(topBarBackdrop)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = tokens.spacing.screenHorizontal,
                    end = tokens.spacing.screenHorizontal,
                    // 顶栏浮层占位（状态栏 + 折叠顶栏），内容自状态栏下方开始，不被遮挡。
                    top = statusBarTop + 104.dp,
                    bottom = tokens.spacing.bottomBarClearance,
                ),
            content = content,
        )

        // 顶栏浮层：与设置首页 / 其余页面同契约（大标题折叠 + 毛玻璃）。
        // 此前这里是纯 Column 自带标题行，没有状态栏内边距处理，
        // 导致二级页顶栏被系统状态栏压住。
        VeneraTopAppBar(
            title = title,
            largeTitle = largeTitle,
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary,
                    )
                }
            },
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
