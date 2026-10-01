package com.venera.compose.data.network

import java.util.concurrent.ConcurrentHashMap

/**
 * Cloudflare 优选 IP 的**进程内配置与探活结果**。
 *
 * 判据全在 [PreferredIpRules]（纯函数、可脱离 gradle 单跑），这一层只做三件事：
 * 存配置、存读数、给 [PreferredIpDns] 与 [PreferredIpFallbackInterceptor] 提供同一份快照。
 *
 * ## 为什么默认是"关闭 + 空表"
 *
 * [configure] 之前它就是 `enabled = false`、两条表都空 —— 此时 [snapshot] 让判据对任何域名
 * 都交 [PreferredIpPlan.SystemDns]，而 `SystemDns` 就是 OkHttp 原本的 `Dns.SYSTEM`。
 * 也就是说：**没配过的用户走的代码路径与今天逐字一致**（验收第 2 条）。
 *
 * ## 为什么探活结果不落盘
 *
 * CF 会调整边缘节点、防火墙会持续封。把上一次"哪台通"当成下一次的事实，正是那份方案里
 * "写死的 IP 失效后比不绑更糟"的成因。所以只有**用户给的配置**进偏好，
 * **读数**只活在进程里，每次冷启动重新探（[refreshInBackground] 在首次用到该域名时后台发起，
 * 不挡当前这一笔）。
 */
internal object PreferredIpRuntime {

    /** 一份配置快照。[ips] 与 [entries] 都已经在 [PreferredIpRules] 里规范化并筛过。 */
    data class Config(
        val enabled: Boolean = false,
        val ips: List<String> = emptyList(),
        val entries: List<String> = emptyList(),
        /**
         * 用户是否挂了 HTTP/SOCKS 代理。
         *
         * 挂代理时目标域名由**代理**那侧解析，`Dns` 只会被问代理主机 ——
         * 优选 IP 在这条路上根本不参与，量出来的延迟也不是用户这台机器的。
         * 判据由接线侧（唯一读过 `proxyType` 的地方）写进来，探活那侧因此不需要 Context。
         */
        val proxyInUse: Boolean = false,
    )

    @Volatile
    private var config = Config()

    /** 条目 → 候选 IP → 读数。**只在内存里**，理由见类头。 */
    private val states = ConcurrentHashMap<String, ConcurrentHashMap<String, PreferredIpState>>()

    /** 条目 → 观察窗截止时间（连失败过一次之后整条暂停用优选，改走系统解析）。 */
    private val demoted = ConcurrentHashMap<String, Long>()

    /** 上一轮完整探活完成的时刻（0 = 从没探过）。 */
    @Volatile
    private var fullProbeAt = 0L

    /** 由 `VeneraNetworkClient.buildClient()` 与设置页保存时调用。 */
    fun configure(enabled: Boolean, ipsRaw: String, hostsRaw: String, proxyInUse: Boolean) {
        val next = Config(
            enabled = enabled,
            ips = PreferredIpRules.parseIps(ipsRaw),
            entries = PreferredIpRules.parseHosts(hostsRaw),
            proxyInUse = proxyInUse,
        )
        config = next
        // 表改过之后，旧条目名下的读数不再有任何读者；留着它，下一次 plan 会拿上一张表的结论。
        states.keys.filterNot { it in next.entries }.forEach(states::remove)
        demoted.keys.filterNot { it in next.entries }.forEach(demoted::remove)
    }

    fun currentConfig(): Config = config

    /** 判据看的那份快照。时间戳不进快照，由调用方按 [PreferredIpRules.plan] 的参数给。 */
    fun snapshot(): PreferredIpSnapshot = PreferredIpSnapshot(
        enabled = config.enabled,
        entries = config.entries,
        statesByEntry = states.mapValues { (_, perIp) -> perIp.toMap() },
        demotedByEntry = demoted.toMap(),
    )

    /** 设置页那一屏要摆的原始读数（每个条目下每台候选的状态）。 */
    fun readings(): Map<String, Map<String, PreferredIpState>> =
        states.mapValues { (_, perIp) -> perIp.toMap() }

    /** 这一轮还需不需要探：开启了、两张表都非空、且从没探过或已过 freshness。 */
    fun needsProbe(nowMs: Long): Boolean {
        val current = config
        if (!current.enabled || current.ips.isEmpty() || current.entries.isEmpty()) return false
        return PreferredIpRules.probeStale(fullProbeAt, nowMs)
    }

    /** 一轮完整探活结束的时刻（freshness 判据用它）。 */
    fun finishProbeRound() {
        fullProbeAt = System.currentTimeMillis()
    }

    /**
     * 探活结果的写回。
     *
     * [passed] 与 [detail] 分两个参数传，而不是"detail 为空就算通过"：通过时屏上也要摆一句
     * 读数（"HTTP 200 · 412 ms"），拿展示串判空会让所有候选都算失败 ——
     * 整层静默变成"开了等于没开"，这是本仓最忌的那种错。
     */
    fun recordProbe(entry: String, ip: String, latencyMs: Long, passed: Boolean, detail: String, nowMs: Long) {
        val perIp = states.computeIfAbsent(entry) { ConcurrentHashMap() }
        perIp[ip] = PreferredIpState(
            latencyMs = latencyMs,
            passed = passed,
            probedAt = nowMs,
            detail = detail,
        )
        // 这一条目重新有可用候选时，观察窗当场作废（否则"刚探通"还要再等 60 秒才生效）。
        if (PreferredIpRules.healthyIps(perIp, nowMs).isNotEmpty()) demoted.remove(entry)
    }

    /** 连失败过一次：该条目的优选档暂时停用，之后那一笔改走系统解析。 */
    fun demote(entry: String, untilMs: Long) {
        demoted[entry] = untilMs
    }

    /** 手动清掉所有读数（用户在设置页改了 IP 列表之后调用，避免拿旧结论摆新表）。 */
    fun clearReadings() {
        states.clear()
        demoted.clear()
        fullProbeAt = 0L
    }

    /**
     * 这一条目的探活目标。默认表里实测过的用实测路径，其余用根路径。
     *
     * ⚠️ 条目可能写的是父域（`donmai.us` 管 `safebooru.donmai.us`）：默认表按**条目串**查，
     * 查不到就走根路径 —— 宁可探得保守，也不要拿一条实测 403 的路径（图片站根路径那种）当判据。
     */
    fun targetFor(entry: String): PreferredIpTarget =
        PreferredIpRules.DEFAULT_TARGETS.firstOrNull { it.host == entry.lowercase() }
            ?: PreferredIpTarget(entry, PreferredIpRules.FALLBACK_PATH)
}
