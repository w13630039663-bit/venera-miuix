package com.venera.compose.feature.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.venera.compose.components.VeneraEmptySize
import com.venera.compose.components.VeneraEmptyTone
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.venera.VeneraButton
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraChipVariant
import com.venera.compose.data.network.PreferredIpLine
import com.venera.compose.data.network.PreferredIpLineProbe
import com.venera.compose.data.network.PreferredIpLineStatus
import com.venera.compose.data.network.PreferredIpProbe
import com.venera.compose.data.network.PreferredIpRuntime
import com.venera.compose.data.network.PreferredIpRules
import com.venera.compose.data.network.PreferredIpSpeedTestSource
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GelbooruAccount
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.ui.tokens.SettingsBadgeColors
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text

/**
 * 线路测速页（设置 → 网络 → 线路测速）。
 *
 * 这一页只做一件事：**让用户看清「这台机器到各个 Cloudflare 站点的边缘节点，哪条线最快」**，
 * 并让他在「看一眼」和「真的用上」之间二选一 —— 所以它和设置页的探活是两条路：
 *
 * - **只读**：[PreferredIpProbe.probeIps] 只量不写，不会因为他瞄一眼就把已落盘的优选 IP 结论覆盖掉；
 * - **写回只在「使用最优线路」那一下发生**，且复用 [savePreferredIp]（与「候选节点与探活」同一套落盘口径）。
 *
 * 三句必须说清的话（用户原话要求的）：
 * 1. **测的是哪个网站**：顶部「测速目标网站」逐条列出（源名 + host + 探活路径），
 *    且**目标跟着用户实际拥有的源走** —— 装了哔咔才测哔咔、装了禁漫才测禁漫、
 *    Gelbooru 配了账号才测它（与画廊 `availableLegs` 同一判据），没装/没配的压根不出现；
 * 2. **不一定可靠**：单次请求延迟 ≠ 实际浏览体验，边缘节点也会随时间变（见底部说明组）；
 * 3. **推荐开代理**：优选 IP 只优化直连路径，想要稳地访问这些站点，开代理通常更好（同见底部说明组）。
 */
@Composable
internal fun PreferredIpSpeedTestScreen(prefs: VeneraPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedIps by prefs.cfPreferredIps.collectAsState()
    val savedHosts by prefs.cfPreferredHosts.collectAsState()

    var probing by remember { mutableStateOf(false) }
    var lines by remember { mutableStateOf<List<PreferredIpLine>>(emptyList()) }
    var inUseIps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }

    // 测速目标跟着用户实际拥有的源走：已安装且启用的漫画源（key/名字都来自 installedMeta，
    // 官方源 JS 自己声明的）→ 图库三站（Gelbooru 没配账号不算，同画廊 availableLegs 判据）
    // → 用户在「适用域名」手填的额外条目。全部推导逻辑在判据层 speedTestSources（可单测）。
    val sourceManager = remember { ComicSourceManager.getInstance(context) }
    val installedMeta by sourceManager.installedMeta.collectAsState()
    val gelbooruIdentity by GelbooruAccount.getInstance(context).identity.collectAsState()

    val targets = remember(installedMeta, gelbooruIdentity, savedHosts) {
        PreferredIpRules.speedTestSources(
            installedComicSourceKeys = installedMeta.filter { it.enabled }.map { it.key },
            comicSourceNames = installedMeta.associate { it.key to it.name },
            enabledGalleryRouteKeys = buildList {
                add(GallerySite.YANDERE.routeKey)
                add(GallerySite.SAFEBOORU.routeKey)
                if (gelbooruIdentity != null) add(GallerySite.GELBOORU.routeKey)
            },
            userHosts = PreferredIpRules.parseHosts(savedHosts),
        )
    }

    fun startProbe() {
        probing = true
        error = null
        // 候选 IP 同样是「有就用用户的，没有就抽样 Cloudflare 官方网段」—— 与自动选点同一池。
        val ips = if (savedIps.isNotBlank()) {
            PreferredIpRules.parseIps(savedIps)
        } else {
            PreferredIpRules.sampleCloudflareIps()
        }
        // 探活域名就是上面这份动态目标的 host（各源路径判据由 targetFor 按已知端点表给出）。
        val hosts = targets.map { it.target.host }
            .ifEmpty { PreferredIpRules.DEFAULT_TARGETS.map { it.host } }
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { PreferredIpProbe.probeIps(ips, hosts) }
                val input = result.mapValues { (_, rs) ->
                    rs.map { PreferredIpLineProbe(ip = it.ip, passed = it.passed, latencyMs = it.latencyMs, detail = it.detail) }
                }
                val summarized = PreferredIpRules.summarizeLineStates(input)
                // 「正在使用」= 当前 runtime 里还健康的候选（优选 IP 真正在用的那批）。
                val snap = PreferredIpRuntime.snapshot()
                val healthy = snap.statesByEntry.flatMap { (_, perIp) ->
                    PreferredIpRules.healthyIps(perIp, System.currentTimeMillis())
                }.toSet()
                lines = summarized
                inUseIps = healthy
            } catch (e: Exception) {
                error = "测速没跑成：${e.message ?: "未知错误"}"
            } finally {
                probing = false
            }
        }
    }

    fun applyBest(ip: String) {
        // 复用设置页的落盘口径：写回偏好 + 立刻让 runtime 看到，清掉旧读数。
        // 只写这一台最优节点（「使用最优线路」= 用这一条），其余由用户后续再探。
        // ⚠️ 域名要把这页动态推导出来的源域名**一并写进「适用域名」**：不然测通了哔咔/禁漫，
        // 运行期 plan() 一查适用表没有它们，照样走系统解析 —— 测了等于白测。手填条目保留。
        val mergedHosts = (targets.map { it.target.host } + PreferredIpRules.parseHosts(savedHosts))
            .distinct()
            .joinToString("\n")
        savePreferredIp(prefs, enabled = true, ips = ip, hosts = mergedHosts, clearReadings = true)
        inUseIps = setOf(ip)
        Toast.makeText(context, "已应用最优线路：$ip", Toast.LENGTH_SHORT).show()
    }

    SettingsPage(title = "线路测速", largeTitle = "线路测速", onBack = onBack) {
        val tokens = VeneraTokens

        // ── 测的是哪个网站：逐条列出来（跟着已安装源走） ──
        SettingsGroup("测速目标网站") {
            targets.forEach { t -> SpeedTestTargetRow(t) }
            Text(
                "列表跟随你安装的漫画源与画廊站点：装了才测，没装不出现。" +
                    "测速通过真实 HTTPS 请求，测量你这台设备到这些站点 Cloudflare 边缘节点的直连延迟" +
                    "（不经过应用内代理，量的是直连这一条路）。",
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(
                    horizontal = tokens.spacing.rowHorizontal,
                    vertical = tokens.spacing.space3,
                ),
            )
        }

        // ── 开始测速 ──
        VeneraButton(
            onClick = { startProbe() },
            enabled = !probing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = tokens.spacing.space4),
        ) {
            Text(if (probing) "测速中…" else "开始测速")
        }

        if (probing) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = tokens.spacing.space6),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularWavyProgressIndicator(Modifier.size(tokens.spacing.loaderInline))
                Text(
                    "正在测速…",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textSecondary,
                    modifier = Modifier.padding(start = tokens.spacing.space4),
                )
            }
        } else if (error != null) {
            Text(
                error.orEmpty(),
                fontSize = tokens.type.caption,
                color = StatusColors.Failing,
                modifier = Modifier.padding(
                    horizontal = tokens.spacing.rowHorizontal,
                    vertical = tokens.spacing.space4,
                ),
            )
        } else if (lines.isEmpty()) {
            VeneraEmptyView(
                message = "还没有测速结果，点上方「开始测速」。",
                tone = VeneraEmptyTone.NotYet,
                size = VeneraEmptySize.Compact,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            lines.forEachIndexed { idx, line ->
                // 「最优」= 第一条**全域名通过**的线（partial 只能服务部分域名，不算最优）。
                // summarizeLineStates 已把全过的排在前，所以 idx==0 且全过即是最快那条。
                val isBest = idx == 0 && line.passedEntries == line.totalEntries && line.totalEntries > 0
                SpeedTestLineCard(line = line, isBest = isBest, isInUse = line.ip in inUseIps)
            }

            // 「使用最优线路」：把最快且**全域名通过**的那台写进优选 IP 配置。
            // 没有全过的线时（所有节点都只能服务部分域名）整组不显示 —— 不能把一个半残的节点当最优。
            val best = lines.firstOrNull { it.passedEntries == it.totalEntries && it.totalEntries > 0 }
            if (best != null) {
                VeneraButton(
                    onClick = { applyBest(best.ip) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = tokens.spacing.space4),
                ) {
                    Text("使用最优线路（${best.ip}）")
                }
            }
        }

        // ── 说明：不一定可靠 + 推荐开代理 ──
        SettingsGroup("说明") {
            Text(
                "⚠️ 测速结果不一定可靠：只测量单次请求的延迟，不反映实际浏览或图片加载体验，" +
                    "且 Cloudflare 边缘节点会随时间变化。",
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
                modifier = Modifier.padding(
                    horizontal = tokens.spacing.rowHorizontal,
                    vertical = tokens.spacing.space3,
                ),
            )
            Text(
                "想要流畅访问这些站点，推荐开启代理：优选 IP 只优化直连路径的延迟，" +
                    "而代理通常能更稳地访问（开代理后这一层不会参与，见「候选节点与探活」）。",
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
                modifier = Modifier.padding(
                    horizontal = tokens.spacing.rowHorizontal,
                    vertical = tokens.spacing.space3,
                ),
            )
        }
    }
}

/** 测速目标里的一行：源名做主标题，host + 探活路径做副行。 */
@Composable
private fun SpeedTestTargetRow(source: PreferredIpSpeedTestSource) {
    val tokens = VeneraTokens
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.rowHorizontal, vertical = tokens.spacing.space3),
    ) {
        Text(
            source.label,
            fontSize = tokens.type.itemTitle,
            fontWeight = tokens.type.weightMedium,
            color = tokens.color.textPrimary,
        )
        // 自定义条目的 label 就是 host 本身，别再重复一遍。
        val subtitle =
            if (source.target.host.equals(source.label, ignoreCase = true)) {
                "路径 ${source.target.path}"
            } else {
                "${source.target.host} · 路径 ${source.target.path}"
            }
        Text(
            subtitle,
            fontSize = tokens.type.caption,
            color = tokens.color.textTertiary,
            modifier = Modifier.padding(top = tokens.spacing.space1),
        )
    }
}

/** 一台候选节点的一张卡片：状态点 + IP + 延迟/通过数 + 最优/正在使用徽标 + 逐域名明细。 */
@Composable
private fun SpeedTestLineCard(line: PreferredIpLine, isBest: Boolean, isInUse: Boolean) {
    val tokens = VeneraTokens
    val statusColor = when (line.status) {
        PreferredIpLineStatus.Healthy -> StatusColors.Healthy
        PreferredIpLineStatus.Degraded -> StatusColors.Degraded
        PreferredIpLineStatus.Failing -> StatusColors.Failing
    }
    VeneraCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.space2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(tokens.spacing.statusDotSize)
                    .background(statusColor, CircleShape),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = tokens.spacing.space4),
            ) {
                Text(
                    line.ip,
                    fontSize = tokens.type.itemTitle,
                    fontWeight = tokens.type.weightMedium,
                    color = tokens.color.textPrimary,
                )
                val sub = if (line.status == PreferredIpLineStatus.Failing) {
                    line.failureReason ?: "未通过"
                } else {
                    "最慢 ${line.slowestLatencyMs} ms · ${line.passedEntries}/${line.totalEntries} 域名通过"
                }
                Text(
                    sub,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(top = tokens.spacing.space1),
                )
            }
            if (isBest) {
                VeneraChip("最优", selected = true, variant = VeneraChipVariant.Selected)
            }
            if (isInUse) {
                VeneraChip(
                    "正在使用",
                    variant = VeneraChipVariant.Tag,
                    containerColorOverride = SettingsBadgeColors.Connectivity.copy(alpha = tokens.current.badgeAlpha),
                    contentColorOverride = SettingsBadgeColors.Connectivity,
                )
            }
        }
        Column(Modifier.padding(top = tokens.spacing.space4)) {
            line.entries.forEach { e ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = tokens.spacing.space1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        e.entry,
                        fontSize = tokens.type.caption,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    val color = if (e.passed) tokens.color.textSecondary else StatusColors.Failing
                    Text(
                        if (e.passed) "${e.latencyMs} ms" else e.detail,
                        fontSize = tokens.type.caption,
                        color = color,
                    )
                }
            }
        }
    }
}
