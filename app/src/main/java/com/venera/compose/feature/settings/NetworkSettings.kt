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

private val proxyOptions = listOf("NONE" to "系统默认（不指定应用代理）", "HTTP" to "HTTP", "SOCKS" to "SOCKS5")

@Composable
fun NetworkSettings(prefs: VeneraPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val proxyType by prefs.proxyType.collectAsState()
    val proxyHost by prefs.proxyHost.collectAsState()
    val proxyPort by prefs.proxyPort.collectAsState()
    val doh by prefs.enableDoH.collectAsState()
    var showProxy by rememberSaveable { mutableStateOf(false) }
    val proxySummary = if (proxyType == "HTTP" || proxyType == "SOCKS") {
        "${proxyOptions.first { it.first == proxyType }.second} · ${proxyHost.ifBlank { "127.0.0.1" }}:$proxyPort"
    } else {
        "系统默认（不指定应用代理）"
    }

    SettingsPage(title = "网络", onBack = onBack) {
        SettingsGroup {
            SettingsAction("代理", proxySummary, onClick = { showProxy = true })
            UnsupportedSetting("DNS 覆盖", "尚无域名与地址覆盖表、启用开关和服务器名称指示配置；加密域名解析已保存为" + if (doh) "开启，但尚未接入网络。" else "关闭，且尚未接入网络。")
            UnsupportedSetting("下载线程", "下载管理器使用固定并发配置，尚未接入原版 1–16 线程的持久化设置。")
        }
        SettingsGroup("现有网络诊断") {
            SettingsAction("重置网络熔断", "清除失败主机的临时熔断记录") {
                com.venera.compose.data.network.HostCircuitBreaker.resetAll()
                Toast.makeText(context, "已清除网络熔断记录", Toast.LENGTH_SHORT).show()
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
                UnsupportedSetting("强制直连", "当前客户端的 NONE 使用系统默认代理选择器，尚不支持忽略系统代理。")
                if (manual) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it; error = null },
                        label = { Text("主机（不含协议或端口）") },
                        singleLine = true,
                        enabled = !saving,
                        isError = !validHost,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it; error = null },
                        label = { Text("端口（1–65535）") },
                        singleLine = true,
                        enabled = !saving,
                        isError = validPort == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    UnsupportedSetting("用户名与密码", "当前代理配置未实现凭据持久化与代理认证。")
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
                            Toast.makeText(context, "代理已保存，对后续请求生效", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = "应用代理配置失败：${failure.message ?: "未知错误"}"
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
