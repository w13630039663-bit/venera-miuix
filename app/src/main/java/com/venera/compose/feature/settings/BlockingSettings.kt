package com.venera.compose.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.venera.compose.security.guard.ContentGuardManager
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun BlockingSettings(onBack: () -> Unit, onRules: (String) -> Unit, onGuard: () -> Unit) {
    val context = LocalContext.current
    val guard = remember(context) { ContentGuardManager.getInstance(context) }
    val mode by guard.nsfwMaskMode.collectAsState()
    val rules by guard.rules.collectAsState()
    SettingsPage("屏蔽与过滤", onBack) {
        SettingsGroup("隐私") {
            SettingsToggle("不允许成人内容", mode != "OFF", { guard.setNsfwMaskMode(if (it) "BLUR" else "OFF") },
                summary = "当前：" + when (mode) { "BLUR" -> "模糊封面"; "HIDE" -> "隐藏条目"; else -> "未启用" } +
                    "。当前仅依据用户规则，不具备原版源级成人分级判定。")
            UnsupportedSetting("屏幕防窥", "尚无持久化安全窗口开关，无法保证重启后禁止截图与任务缩略图")
            UnsupportedSetting("源分级", "尚无逐源分级预设、判定来源与用户覆盖存储")
        }
        listOf("TAG" to "标签", "AUTHOR" to "画师", "COMIC_ID" to "作品").forEach { (type, title) ->
            SettingsGroup(title) {
                SettingsAction(title, "已保存 "+ rules.count { it.type == type } +" 条规则", onClick = { onRules(type) })
                UnsupportedSetting("启用$title 屏蔽", "规则管理器没有分类总开关；已添加规则持续生效，可进入列表删除撤销")
            }
        }
        SettingsGroup("现有内容守卫") {
            SettingsAction("完整内容守卫", "保留现有分级遮罩模式与规则管理入口", onClick = onGuard)
        }
    }
}

/** 原版三个屏蔽列表和关键词列表共用结构，复用真实数据库，不另存 UI 状态。 */
@Composable
internal fun BlockingRulesSettings(type: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { ContentGuardManager.getInstance(context) }
    val rules by manager.rules.collectAsState()
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Long?>(null) }
    val title = when (type) { "TAG" -> "屏蔽标签"; "AUTHOR" -> "屏蔽画师"; "COMIC_ID" -> "屏蔽作品"; else -> "关键词屏蔽" }
    SettingsPage(title, onBack) {
        SettingsGroup {
            SettingsAction("匹配说明", when(type) {
                "TAG" -> "当前按标签包含匹配，与原版标签精确匹配不同。"
                "AUTHOR" -> "当前按作者包含匹配，仅对返回作者数据的源生效。"
                "COMIC_ID" -> "填写作品编号。当前按编号精确匹配，不区分源；尚不支持原版的源键与编号组合。"
                else -> "当前按标题关键词包含匹配，不区分大小写。"
            })
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(input, { input = it; error = null }, label = { Text("添加屏蔽项") },
                    isError = error != null, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
                if (error != null) Text(error!!)
                TextButton(enabled = !busy && input.isNotBlank(), onClick = {
                    val pattern = input.trim()
                    if (rules.any { it.type == type && it.pattern.equals(pattern, true) }) {
                        error = "该屏蔽项已经存在"
                    } else {
                        busy = true
                        scope.launch {
                            try {
                                if (manager.addRule(type, pattern) >= 0) input = "" else error = "保存失败，请重试"
                            } finally { busy = false }
                        }
                    }
                }) { Text(if (busy) "正在保存" else "添加") }
            }
        }
        SettingsGroup("已保存的屏蔽项") {
            val selected = rules.filter { it.type == type }
            if (selected.isEmpty()) SettingsAction("暂无屏蔽项")
            selected.forEach { rule ->
                key(rule.id) { SettingsAction(rule.pattern, "点击删除" + if (rule.isRegex) " · 正则规则" else "") { deleting = rule.id } }
            }
        }
    }
    val id = deleting
    if (id != null) AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除屏蔽项？") },
        text = { Text("删除后该规则将不再拦截匹配内容。") },
        confirmButton = { TextButton(onClick = {
            deleting = null
            scope.launch { if (!manager.deleteRule(id)) Toast.makeText(context, "删除失败，请重试", Toast.LENGTH_SHORT).show() }
        }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
}
