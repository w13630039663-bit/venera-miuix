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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 对应原版 SettingsSection：标题在卡片外，行靠留白区分。 */
@Composable
internal fun SettingsGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f),
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), content = content)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun RowScope.SettingLabel(title: String, summary: String?, enabled: Boolean = true) {
    Column(Modifier.weight(1f)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .45f))
        if (!summary.isNullOrBlank()) Text(summary, fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = .55f), modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
internal fun SettingsAction(title: String, summary: String? = null, enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        SettingLabel(title, summary, enabled)
        if (enabled && onClick != null) Text("›", fontSize = 22.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .4f))
    }
}

/** 未实现的设置不分配虚构默认值，直接说明能力缺口。 */
@Composable
internal fun UnsupportedSetting(title: String, reason: String) =
    SettingsAction(title, "暂不支持：$reason", enabled = false)

@Composable
internal fun SettingsToggle(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    summary: String? = null, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) }
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        SettingLabel(title, summary, enabled)
        Switch(checked = checked, onCheckedChange = if (enabled) onCheckedChange else null, enabled = enabled)
    }
}

@Composable
internal fun SettingsSelect(title: String, value: String, options: List<Pair<String, String>>,
    onSelected: (String) -> Unit, summary: String? = null) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == value }?.second ?: "未识别的已保存值：$value"
    SettingsAction(title, listOfNotNull(label, summary).joinToString("\n")) { open = true }
    if (open) AlertDialog(
        onDismissRequest = { open = false }, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (key, text) ->
                    SettingsAction(text, if (value == key) "已选择" else null) {
                        onSelected(key)
                        open = false
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { open = false }) { Text("取消") } }
    )
}

@Composable
internal fun SettingsSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit, steps: Int = 0, suffix: String = "") {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("$title · "+ (if (value % 1f == 0f) value.toInt().toString() else value.toString()) + suffix, fontSize = 15.sp)
        Slider(value = value.coerceIn(range), onValueChange = onValueChange, valueRange = range, steps = steps)
    }
}

@Composable
internal fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = MiuixTheme.colorScheme.onSurface) }
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 80.dp), content = content)
    }
}

@Composable
internal fun SectionIconBadge(color: Color, content: @Composable () -> Unit) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = .15f)),
        contentAlignment = Alignment.Center, content = { content() })
}
