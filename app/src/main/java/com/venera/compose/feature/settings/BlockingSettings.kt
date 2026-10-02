package com.venera.compose.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraDialog
import com.venera.compose.components.venera.VeneraTextField
import com.venera.compose.components.venera.VeneraTextButton
import com.venera.compose.data.api.BusinessPorts
import com.venera.compose.security.guard.GuardRulePattern
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun BlockingSettings(onBack: () -> Unit, onRules: (String) -> Unit) {
    val context = LocalContext.current
    val guard = remember(context) { BusinessPorts.of(context).contentGuard }
    val ruleBook = remember(context) { BusinessPorts.of(context).guardRuleBook }
    val prefs = remember(context) { BusinessPorts.of(context).comicPrefs }
    val secureScreen by prefs.secureScreen.collectAsState()
    val mode by guard.nsfwMaskMode.collectAsState()
    val blockAi by guard.blockAiComics.collectAsState()
    val rules by ruleBook.rules.collectAsState()
    SettingsPage("屏蔽", onBack, largeTitle = "屏蔽与过滤", heroSubtitle = "规则类型 · AI 标签") {
        SettingsGroup("隐私") {
            // FLAG_SECURE 由 MainActivity 订阅同一个偏好应用到窗口，拨一下当前界面即刻生效。
            SettingsToggle(
                "屏幕防窥", secureScreen, prefs::setSecureScreen,
                summary = "禁止截图、录屏和最近任务里的缩略图，重启后仍然有效。系统的长截屏也会失效。",
            )
        }
        // 原「成人内容处理」与本组这条是**同一个偏好**（nsfwMaskMode）在两页各摆了一份，
        // 措辞还不一样（不处理/模糊封面/隐藏条目 vs 不过滤/封面打码/彻底隐藏）。
        // 现在只留守卫页这一份措辞，二级页整体搬进来。
        SettingsGroup("内容守卫") {
            SettingsSelect(
                "R18 敏感内容分级遮罩", mode,
                listOf("OFF" to "不过滤", "BLUR" to "封面打码", "HIDE" to "彻底隐藏"),
                { guard.setNsfwMaskMode(it) },
                summary = "被判定为敏感内容的条目怎么显示。" +
                    "打码之后点击不会恢复清晰；彻底隐藏只在部分列表页完全生效。",
            )
            SettingsToggle(
                "屏蔽 AI 生成漫画", blockAi, guard::setBlockAiComics,
                summary = "整个条目不再出现，与上面的遮罩各管各的。" +
                    "只认明确的 AI 标记：标签 ai-generated / ai_generated，" +
                    "或者标题里的 [AI Generated]、[AI Art]、【AI】。单独的 ai 字样不算，以免误伤。",
            )
            // 逐源预设其实已经生效（assets/source_content_warning.json，33 源），
            // 缺的是逐源用户覆盖与选择 UI —— 移入底部「尚未实现」折叠区，不占主区。
        }
        // KEYWORD 原先只有守卫二级页能进；页面合并后必须在这里补上入口，
        // 否则已保存的关键词规则就没地方查看和删除了。
        listOf("KEYWORD" to "关键词", "TAG" to "标签", "AUTHOR" to "画师", "COMIC_ID" to "作品").forEach { (type, title) ->
            SettingsGroup(title) {
                SettingsAction(title, "已保存 "+ rules.count { it.type == type } +" 条规则", onClick = { onRules(type) })
            }
        }
    }
}

/** 原版三个屏蔽列表和关键词列表共用结构，复用真实数据库，不另存 UI 状态。 */
@Composable
internal fun BlockingRulesSettings(type: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { BusinessPorts.of(context).guardRuleBook }
    val rules by manager.rules.collectAsState()
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var regexMode by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Long?>(null) }
    val title = when (type) { "TAG" -> "屏蔽标签"; "AUTHOR" -> "屏蔽画师"; "COMIC_ID" -> "屏蔽作品"; else -> "关键词屏蔽" }
    SettingsPage(title, onBack, largeTitle = title) {
        SettingsGroup {
            SettingsAction("匹配说明", when(type) {
                "TAG" -> "标签按包含匹配，不是精确匹配。"
                "AUTHOR" -> "作者按包含匹配，只有提供作者信息的源生效。"
                "COMIC_ID" -> "填写作品编号，按编号精确匹配，不区分源。"
                else -> "标题按包含匹配，不区分大小写。"
            })
            Column(Modifier.padding(16.dp)) {
                // 原守卫页就有正则开关，而这里恒按字面添加，两页能力不一致；
                // addRule 本身已支持 isRegex，这里只是把入口补上。
                SettingsToggle(
                    "按正则匹配", regexMode, { regexMode = it; error = null },
                    summary = "关掉按普通文字匹配，打开按正则匹配。",
                )
                VeneraTextField(
                    value = input,
                    onValueChange = { input = it; error = null },
                    label = "添加屏蔽项",
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                )
                if (error != null) Text(error!!)
                VeneraTextButton(
                    text = if (busy) "正在保存" else "添加",
                    enabled = !busy && input.isNotBlank(),
                    onClick = {
                        val pattern = input.trim()
                        if (regexMode && !GuardRulePattern.compiles(pattern)) {
                            // 正则在写库前校验：坏模式会静默永不命中，比报错更难查。
                            // 判据本身在 GuardRulePattern —— 恢复备份那条路收的是同一道关。
                            error = "正则写法有误"
                        } else if (rules.any { it.type == type && it.pattern.equals(pattern, true) }) {
                            error = "这条已经加过了"
                        } else {
                            busy = true
                            scope.launch {
                                try {
                                    if (manager.addRule(type, pattern, regexMode) >= 0) input = "" else error = "保存失败，再试一次"
                                } finally { busy = false }
                            }
                        }
                    },
                )
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
    if (id != null) {
        VeneraDialog(
            show = true,
            onDismissRequest = { deleting = null },
            title = "删除屏蔽项？",
            content = { Text("删掉后这条规则就不再生效了。") },
            confirmText = "删除",
            onConfirm = {
                deleting = null
                scope.launch {
                    if (!manager.deleteRule(id)) {
                        Toast.makeText(context, "删除失败，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            dismissText = "取消",
            onDismiss = { deleting = null },
        )
    }
}
