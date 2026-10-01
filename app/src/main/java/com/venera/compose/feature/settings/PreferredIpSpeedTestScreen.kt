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
 * 这一页是优选 IP 的**主操作台**：看清「这台机器到各个 Cloudflare 站点的边缘节点，哪条线最快」，
 * 并一键把最优候选用上 —— 两颗按钮对应两种意图：
 *
 * - **一键测速并自动选点（主路）**：从 Cloudflare 官方网段抽样 → 探活 → 展示 → 自动写回最优候选
 *   （判据在 [PreferredIpRules.bestIpsFromLines]：**行级口径** —— 任一网站答上话就入选、按最快
 *   通过延迟排序、上限 `MAX_IPS_PER_HOST`，没答上的网站被忽略；写回落盘口径与
 *   [savePreferredIp] 同一套）；
 * - **测当前候选（只看）**：[PreferredIpProbe.probeIps] 只量不写，复核已保存的候选今天还行不行。
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
    val proxyType by prefs.proxyType.collectAsState()
    val proxyInUse = proxyType == "HTTP" || proxyType == "SOCKS"

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

    fun applyBest(ipsText: String, message: String) {
        // 复用设置页的落盘口径：写回偏好 + 立刻让 runtime 看到，清掉旧读数。
        // ⚠️ 域名要把这页动态推导出来的源域名**一并写进「适用域名」**：不然测通了哔咔/禁漫，
        // 运行期 plan() 一查适用表没有它们，照样走系统解析 —— 测了等于白测。手填条目保留。
        val mergedHosts = (targets.map { it.target.host } + PreferredIpRules.parseHosts(savedHosts))
            .distinct()
            .joinToString("\n")
        savePreferredIp(prefs, enabled = true, ips = ipsText, hosts = mergedHosts, clearReadings = true)
        inUseIps = PreferredIpRules.parseIps(ipsText).toSet()
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun startProbe(autoApply: Boolean) {
        probing = true
        error = null
        // 候选 IP 两条路：只看的模式用已保存的候选（没有也抽样）；自动选点永远抽一批新的 ——
        // 与「候选节点与探活」原自动选点同一口径：抽样表是刚量出来的结论，不该被旧表污染。
        val ips = if (!autoApply && savedIps.isNotBlank()) {
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
                if (autoApply) {
                    // 选点判据在判据层（bestIpsFromLines，可单测）：行级口径 —— 任一网站答上话
                    // 就入选、按最快通过延迟升序、上限 MAX_IPS_PER_HOST（没答上的网站被忽略，
                    // 运行期 runtime 探活会自动回退系统解析）。
                    val best = PreferredIpRules.bestIpsFromLines(summarized)
                    if (best.isEmpty()) {
                        error = "抽样 ${ips.size} 台没有一台对任何目标站点答上话，可再按一次重抽一批"
                    } else {
                        applyBest(
                            ipsText = best.joinToString("\n"),
                            message = "已自动选点 ${best.size} 台并应用",
                        )
                    }
                }
            } catch (e: Exception) {
                error = "测速没跑成：${e.message ?: "未知错误"}"
            } finally {
                probing = false
            }
        }
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

        // ── 一键测速并自动选点（主路）：抽样 → 探活 → 展示 → 自动写回最优候选 ──
        VeneraButton(
            onClick = { startProbe(autoApply = true) },
            enabled = !probing && !proxyInUse,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = tokens.spacing.space4),
        ) {
            Text(if (probing) "测速中…" else "一键测速并自动选点")
        }
        // 只看模式：量「当前已保存的候选」今天还行不行，不动任何配置。
        // 用自动选点落盘后，这颗就是复核按钮 —— 两颗的关系在说明组里也讲了一遍。
        VeneraButton(
            onClick = { startProbe(autoApply = false) },
            enabled = !probing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("测当前候选（只看，不改配置）")
        }
        if (proxyInUse) {
            // 与「候选节点与探活」同一句真话：代理在的时候目标域名由代理解析，
            // 优选 IP 这一层根本不参与 —— 自动选点写回的表没人用，等于白测。
            Text(
                "当前设置了代理，优选 IP 这一层不会参与，自动选点已停用；要用它请先把代理切回「跟随系统默认」。",
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(
                    horizontal = tokens.spacing.rowHorizontal,
                    vertical = tokens.spacing.space3,
                ),
            )
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
        } else {
            // 错误提示**不挡结果**：自动选点没挑出可用节点时，用户更需要看到每台节点
            // 对每个网站的具体表现（哪些绿哪些红），一句报错糊脸上什么都看不见是倒错的优先级。
            if (error != null) {
                Text(
                    error.orEmpty(),
                    fontSize = tokens.type.caption,
                    color = StatusColors.Failing,
                    modifier = Modifier.padding(
                        horizontal = tokens.spacing.rowHorizontal,
                        vertical = tokens.spacing.space4,
                    ),
                )
            }
            if (lines.isEmpty()) {
                if (error == null) {
                    VeneraEmptyView(
                        message = "还没有测速结果，点上方「一键测速并自动选点」。",
                        tone = VeneraEmptyTone.NotYet,
                        size = VeneraEmptySize.Compact,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                lines.forEachIndexed { idx, line ->
                    // 「最优」= 排第一且**至少有一个网站答上话**的线（与自动选点同一行级口径：
                    // 没答上的网站被忽略，不要求全过 —— 全过才算好会让被阻断站点在场时永远没有最优）。
                    // summarizeLineStates 已把可用的排在前，所以 idx==0 且有通过即是最快那条。
                    val isBest = idx == 0 && line.passedEntries > 0
                    SpeedTestLineCard(line = line, isBest = isBest, isInUse = line.ip in inUseIps)
                }

                // 「使用最优线路」：把排头那台（至少一个网站通过）写进优选 IP 配置。
                // 与自动选点同行级口径；它没答上的网站运行期由 runtime 探活自然回退系统解析。
                val best = lines.firstOrNull { it.passedEntries > 0 }
                if (best != null) {
                    VeneraButton(
                        onClick = { applyBest(best.ip, "已应用最优线路：${best.ip}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = tokens.spacing.space4),
                    ) {
                        Text("使用最优线路（${best.ip}）")
                    }
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
                // 失败原因（尤其 RESET 那句「被按域名阻断…建议开代理」很长）不能放进无权重的
                // 右槽：它会先量满整行，把左边的域名条目挤成一字一行的竖排（真机 2026-10-01 实锤）。
                // 所以分两种形态：通过 = 「域名 ←→ 延迟」一行；失败 = 域名一行、原因整行铺开。
                if (e.passed) {
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
                        Text(
                            "${e.latencyMs} ms",
                            fontSize = tokens.type.caption,
                            color = tokens.color.textSecondary,
                        )
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = tokens.spacing.space1),
                    ) {
                        Text(
                            e.entry,
                            fontSize = tokens.type.caption,
                            color = tokens.color.textTertiary,
                        )
                        Text(
                            e.detail,
                            fontSize = tokens.type.caption,
                            color = StatusColors.Failing,
                            modifier = Modifier.padding(top = tokens.spacing.space1),
                        )
                    }
                }
            }
        }
    }
}
