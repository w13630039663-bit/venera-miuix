package com.venera.compose.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.venera.compose.data.network.PreferredIpProbe
import com.venera.compose.data.network.PreferredIpRules
import com.venera.compose.data.network.PreferredIpRuntime
import com.venera.compose.data.prefs.VeneraPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text

/**
 * 网络设置页里的「Cloudflare 优选 IP」那一组。
 *
 * ## 为什么开关不放进弹窗
 *
 * 开关回答的是"今天这条路走不走"，它得在页面上**一眼可见**（含它当前为什么实际不参与，
 * 比如挂着代理）；两份列表与探活读数是编辑动作，归弹窗。这与代理那一档把类型塞进弹窗不同：
 * 代理只有一个"生效/不生效"，而这一档开了之后还有一整轮可观测的探活结果。
 *
 * ## 三条与判据层对齐的口径
 *
 * - **不内置任何 IP**：默认是空表 + 关闭，用户不粘就一律走正常解析（写死的 IP 一旦失效，
 *   该域名会比不绑更糟地不可用）。
 * - **不静默**：粘了 15 项只认出 12 台，屏上就说"识别出 12 台（共 15 项）· 上限 12"。
 * - **挂了代理就直说**：代理在的时候目标域名由代理解析，这一层根本不参与 ——
 *   不说这一句，用户"开了优选 IP 又挂着代理"只会以为功能坏了。
 */
@Composable
internal fun PreferredIpSettingsGroup(prefs: VeneraPreferences) {
    val enabled by prefs.cfPreferredIpEnabled.collectAsState()
    val ips by prefs.cfPreferredIps.collectAsState()
    val hosts by prefs.cfPreferredHosts.collectAsState()
    val proxyType by prefs.proxyType.collectAsState()
    var showEditor by rememberSaveable { mutableStateOf(false) }
    val proxyInUse = proxyType == "HTTP" || proxyType == "SOCKS"

    SettingsToggle(
        title = "Cloudflare 优选 IP",
        checked = enabled,
        onCheckedChange = { next ->
            savePreferredIp(prefs, enabled = next, ips, hosts, clearReadings = false)
        },
        summary = summaryOf(enabled, ips, hosts, proxyInUse),
    )
    SettingsAction(
        title = "候选节点与探活",
        summary = "手动粘贴候选地址并逐个探活；一键测速自动选点请用「线路测速」页",
        onClick = { showEditor = true },
    )

    if (showEditor) {
        PreferredIpEditorDialog(
            prefs = prefs,
            enabled = enabled,
            initialIps = ips,
            initialHosts = hosts,
            proxyInUse = proxyInUse,
            onDismiss = { showEditor = false },
        )
    }
}

/** 页面上那一行读数：把"开了但没用"的三种情形分别说出来，不留一句笼统的"已开启"。 */
private fun summaryOf(enabled: Boolean, ipsRaw: String, hostsRaw: String, proxyInUse: Boolean): String {
    if (!enabled) return "未开启 · 全部走正常解析"
    val recognized = PreferredIpRules.parseIps(ipsRaw).size
    val tokens = PreferredIpRules.countIpTokens(ipsRaw)
    val entryCount = PreferredIpRules.parseHosts(hostsRaw).size
    return when {
        proxyInUse -> "已开启，但当前走代理 · 这一层不参与"
        recognized == 0 -> "已开启，但还没有可用的候选地址"
        entryCount == 0 -> "已开启，但没有域名用它"
        else -> "已绑 $recognized 台候选（共 $tokens 项）· $entryCount 个域名"
    }
}

@Composable
private fun PreferredIpEditorDialog(
    prefs: VeneraPreferences,
    enabled: Boolean,
    initialIps: String,
    initialHosts: String,
    proxyInUse: Boolean,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var ips by rememberSaveable { mutableStateOf(initialIps) }
    var hosts by rememberSaveable { mutableStateOf(initialHosts) }
    var probing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<Map<String, List<PreferredIpProbe.Row>>>(emptyMap()) }

    val recognized = PreferredIpRules.parseIps(ips)
    val entries = PreferredIpRules.parseHosts(hosts)

    // ⚠️ 与代理那一档同样**留在 M3 AlertDialog**（2026-09-29 的裁决）：这枚弹窗里嵌的是
    // 两个输入框 + 一颗按钮，换成 VeneraDialog（miuix 后端 = WindowDialog）之后内部若再有
    // Dialog 套 Dialog 的链路，预测式返回与焦点归属在真机上没人验过。
    AlertDialog(
        onDismissRequest = { if (!probing) onDismiss() },
        title = { Text("Cloudflare 优选 IP") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("候选节点地址")
                OutlinedTextField(
                    value = ips,
                    onValueChange = { ips = it; failure = null; rows = emptyMap() },
                    placeholder = { Text("一行一个，也可以用逗号或空格分开") },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !probing,
                    modifier = Modifier.fillMaxWidth(),
                    // 候选不该让用户自己满世界找 IP 来粘：自动选点已整合进「线路测速」页
                    //（抽样 → 探活 → 自动写回），这里只留手动粘贴这条高级路。
                    supportingText = {
                        Text(
                            "一般不用手填：到「线路测速」页点「一键测速并自动选点」，测完会自动填好这里。" +
                                "也可手动粘贴 IP（IPv4 / IPv6 字面量）；域名不填这里，填下面的「适用域名」。"
                        )
                    },
                )
                // 认不出来的那些必须当场说出来：默默丢掉等于让用户以为"我粘了 15 台但只通 3 台"。
                Text(
                    "识别出 ${recognized.size} 台（共 ${PreferredIpRules.countIpTokens(ips)} 项）· " +
                        "上限 ${PreferredIpRules.MAX_IPS_PER_HOST} 台",
                )

                Text("适用域名")
                OutlinedTextField(
                    value = hosts,
                    onValueChange = { hosts = it; failure = null; rows = emptyMap() },
                    placeholder = { Text("只有这些域名会绑优选 IP，其余一律走正常解析") },
                    minLines = 2,
                    maxLines = 5,
                    enabled = !probing,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("共 ${entries.size} 个域名")

                if (!enabled) {
                    // 探活与开关是两件事：这里只测"这几台通不通"，不替用户把路开上。
                    Text("总开关还没开：这一轮结果只做参考，保存不会自动开启它。")
                }
                if (proxyInUse) {
                    // 这一句是**真话而不是免责**：代理在的时候 OkHttp 只把 Dns 用在代理主机上，
                    // 目标域名由代理解析 —— 探活也因此不跑（量到的不是这台机器的距离）。
                    Text("当前设置了代理，优选 IP 这一层不会参与；要用它请先把代理切回「跟随系统默认」。")
                }
                if (failure != null) Text(failure.orEmpty())

                if (probing) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularWavyProgressIndicator()
                        Text("正在探活…")
                    }
                } else if (rows.isNotEmpty()) {
                    rows.forEach { (entry, list) ->
                        Text(entry)
                        list.forEach { row ->
                            Text("  ${row.ip}  ${row.detail}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !probing,
                onClick = {
                    failure = null
                    scope.launch {
                        // 探活之前先落盘：探的是屏上这一份表，不先保存就会出现"探的是旧表、
                        // 屏上摆的是新表"这种两处真相。开关状态照当前的来，不替用户偷偷开。
                        savePreferredIp(prefs, enabled, ips, hosts, clearReadings = true)
                        if (recognized.isEmpty() || entries.isEmpty()) {
                            failure = if (recognized.isEmpty()) "先填至少一个候选地址" else "先填至少一个适用域名"
                        } else if (proxyInUse) {
                            failure = "当前走代理，探活量不到这台机器的真实距离，已按「不参与」处理"
                        } else {
                            probing = true
                            rows = withContext(Dispatchers.IO) {
                                runCatching { PreferredIpProbe.probeAll() }
                                    .getOrElse { cause ->
                                        failure = "探活没跑成：${cause.message ?: "未知错误"}"
                                        emptyMap()
                                    }
                            }
                            probing = false
                        }
                    }
                },
            ) { Text("保存并探活") }
        },
        dismissButton = {
            TextButton(enabled = !probing, onClick = onDismiss) { Text("取消") }
        },
    )
}

/**
 * 写偏好并让**正在跑的**那层立刻看到新配置。
 *
 * 不需要重建客户端：`PreferredIpDns` 每次都从 [PreferredIpRuntime] 取快照，而客户端上挂的这层
 * 本来就一直在（关闭时判据对它交回 `Dns.SYSTEM`）。这一点刻意与代理那一档不同 ——
 * 代理必须重建客户端，是因为 `Proxy` 在建客户端那一刻就定死了。
 *
 * [clearReadings] 用在候选表变过的时候：旧表探出来的"哪台通"对新表没有任何意义。
 */
internal fun savePreferredIp(
    prefs: VeneraPreferences,
    enabled: Boolean,
    ips: String,
    hosts: String,
    clearReadings: Boolean,
) {
    prefs.setCfPreferredIp(enabled, ips, hosts)
    val proxyType = prefs.proxyType.value
    PreferredIpRuntime.configure(
        enabled = enabled,
        ipsRaw = ips,
        hostsRaw = hosts,
        proxyInUse = proxyType == "HTTP" || proxyType == "SOCKS",
    )
    if (clearReadings) PreferredIpRuntime.clearReadings()
}
