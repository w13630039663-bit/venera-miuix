package com.venera.compose.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.data.prefs.VeneraPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text

private val proxyOptions = listOf("NONE" to "跟随系统默认", "HTTP" to "HTTP", "SOCKS" to "SOCKS5")

@Composable
fun NetworkSettings(prefs: VeneraPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val proxyType by prefs.proxyType.collectAsState()
    val proxyHost by prefs.proxyHost.collectAsState()
    val proxyPort by prefs.proxyPort.collectAsState()
    var showProxy by rememberSaveable { mutableStateOf(false) }
    val proxySummary = if (proxyType == "HTTP" || proxyType == "SOCKS") {
        "${proxyOptions.first { it.first == proxyType }.second} · ${proxyHost.ifBlank { "127.0.0.1" }}:$proxyPort"
    } else {
        "跟随系统默认"
    }

    val threads by prefs.downloadThreads.collectAsState()

    SettingsPage(title = "网络", onBack = onBack, largeTitle = "网络与代理", heroSubtitle = "代理 · 缓存 · 超时") {
        SettingsGroup {
            SettingsAction("代理", proxySummary, onClick = { showProxy = true })
            SettingsSlider("下载并发", threads.toFloat(), 1f..16f,
                { prefs.setDownloadThreads(it.toInt()) }, steps = 14, suffix = " 线程",
                summary = "同时下载几章。改完对新排进队列的任务生效，不影响正在下的。")
            // 审计后删掉「DNS 覆盖」灰行：DoH 客户端已 import 但未接线，且在国内网络下
            // 强制 DoH 可能整体不可达 —— 属高风险网络变更，不是"差一个开关"。
            // 僵尸键 enableDoH（默认 true 且无人读）已在 settings-audit-2026-09.md §3 记录。
        }
        SettingsGroup("Cloudflare 优选 IP") {
            PreferredIpSettingsGroup(prefs)
        }
        SettingsGroup("网络诊断") {
            SettingsAction("重置失败记录", "清除暂时连不上的站点记录，这些站点会马上重新尝试") {
                com.venera.compose.data.network.HostCircuitBreaker.resetAll()
                Toast.makeText(context, "已清除失败记录", Toast.LENGTH_SHORT).show()
            }
        }
    }
    if (showProxy) {
        ProxySettingsDialog(prefs = prefs, onDismiss = { showProxy = false })
    }
}

@Composable
private fun ProxySettingsDialog(prefs: VeneraPreferences, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by rememberSaveable { mutableStateOf(prefs.proxyType.value) }
    var host by rememberSaveable { mutableStateOf(prefs.proxyHost.value.ifBlank { "127.0.0.1" }) }
    var port by rememberSaveable { mutableStateOf(prefs.proxyPort.value.toString()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val manual = type == "HTTP" || type == "SOCKS"
    val validPort = port.toIntOrNull()?.takeIf { it in 1..65535 }
    val validHost = host.isNotBlank() && host.none { it.isWhitespace() || it == '/' || it == '@' }
    val valid = type == "NONE" || (manual && validHost && validPort != null)

    // ⚠️ 这一枚**刻意留在 M3 AlertDialog**，不是漏迁（2026-09-29 拍板）：
    // 它是"表单弹窗"，正文里嵌着 SettingsSelect —— 而 SettingsSelect 现在已经走
    // VeneraDialog（miuix 后端 = WindowDialog，自己起一个 Dialog 窗口）。外层若也换成
    // VeneraDialog，就变成 Dialog 套 Dialog，预测式返回与焦点归属在真机上没测过；
    // 拿"UI 统一"去赌一条没人验过的窗口链，正是本仓禁止的假统一。留作下一批带真机验证的独立项。
    // 连带：**它内部的两颗按钮与两个输入框也一起留在 M3**（`VeneraTextField` 已存在但这里不用），
    // 让这一个弹窗内部自洽 —— 一半 miuix 一半 M3 比整枚 M3 更难解释。
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("代理") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!saving) {
                    SettingsSelect("代理类型", type, proxyOptions, onSelected = { type = it; error = null })
                } else {
                    Text("正在保存代理配置…")
                }
                if (manual) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it; error = null },
                        label = { Text("主机地址") },
                        singleLine = true,
                        enabled = !saving,
                        isError = !validHost,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it; error = null },
                        label = { Text("端口") },
                        singleLine = true,
                        enabled = !saving,
                        isError = validPort == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !saving,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                prefs.setProxy(type, host.trim(), validPort ?: prefs.proxyPort.value)
                                VeneraNetworkClient.getInstance(context).rebuildClient()
                            }
                            Toast.makeText(context, "代理已保存，之后的请求生效", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = "代理配置生效失败：${failure.message ?: "未知错误"}"
                        } finally {
                            saving = false
                        }
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("取消") }
        }
    )
}
