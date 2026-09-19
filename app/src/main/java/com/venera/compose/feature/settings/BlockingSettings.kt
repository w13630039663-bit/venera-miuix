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
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.security.guard.ContentGuardManager
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun BlockingSettings(onBack: () -> Unit, onRules: (String) -> Unit, onGuard: () -> Unit) {
    val context = LocalContext.current
    val guard = remember(context) { ContentGuardManager.getInstance(context) }
    val prefs = remember(context) { VeneraPreferences.getInstance(context) }
    val secureScreen by prefs.secureScreen.collectAsState()
    val mode by guard.nsfwMaskMode.collectAsState()
    val rules by guard.rules.collectAsState()
    SettingsPage("屏蔽与过滤", onBack) {
        SettingsGroup("隐私") {
            // 原来是二态 toggle：当前为 HIDE 时关一下再开会被静默改写成 BLUR，且 HIDE 只能绕到
            // 守卫页才选得到。改成三态直选，模式集合与 ContentGuardManager 判定链一致。
            SettingsSelect(
                "成人内容处理", mode,
                listOf("OFF" to "不处理", "BLUR" to "模糊封面", "HIDE" to "隐藏条目"),
                { guard.setNsfwMaskMode(it) },
                summary = "命中规则后如何处理。原版的「点击后揭示模糊」尚未实现；" +
                    "隐藏条目仅在支持逐源判定的列表页完全生效。",
            )
            // FLAG_SECURE 由 MainActivity 订阅同一个偏好应用到窗口，拨一下当前界面即刻生效。
            SettingsToggle(
                "屏幕防窥", secureScreen, prefs::setSecureScreen,
                summary = "禁止截图、录屏与任务列表缩略图；重启后保持。开启后系统自带的长截屏也会失效。",
            )
            // 逐源预设其实已经生效（assets/source_content_warning.json，33 源），
            // 原占位文案说"尚无预设"与实现不符；真正缺的是逐源用户覆盖与选择 UI。
            UnsupportedSetting("源分级", "逐源预设已生效（33 源 safe/mixed/nsfw），缺的是逐源用户覆盖存储与选择界面")
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
    var regexMode by rememberSaveable { mutableStateOf(false) }
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
                // 守卫页早就有正则开关（ContentGuardScreen），而这里恒按字面添加，
                // 两页能力不一致；addRule 本身已支持 isRegex，这里只是把入口补上。
                SettingsToggle(
                    "按正则匹配", regexMode, { regexMode = it; error = null },
                    summary = "关闭＝按字面包含匹配；开启＝按正则匹配（与完整内容守卫页一致）",
                )
                OutlinedTextField(input, { input = it; error = null }, label = { Text("添加屏蔽项") },
                    isError = error != null, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
                if (error != null) Text(error!!)
                TextButton(enabled = !busy && input.isNotBlank(), onClick = {
                    val pattern = input.trim()
                    if (regexMode && runCatching { Regex(pattern) }.isFailure) {
                        // 正则在写库前校验：坏模式会静默永不命中，比报错更难查。
                        error = "正则表达式无法编译"
                    } else if (rules.any { it.type == type && it.pattern.equals(pattern, true) }) {
                        error = "该屏蔽项已经存在"
                    } else {
                        busy = true
                        scope.launch {
                            try {
                                if (manager.addRule(type, pattern, regexMode) >= 0) input = "" else error = "保存失败，请重试"
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
