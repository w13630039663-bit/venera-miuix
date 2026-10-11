package com.venera.desktop.gallery.data

import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.db.GuardRuleStore
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.security.guard.GalleryBlockMatch
import com.venera.compose.security.guard.GuardRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 桌面侧的画廊内容守卫口 —— 共享面那四枚成员，直连已在桌面编译面上的 [GuardRuleStore]，
 * 再加**两道桌面自持的写口**（[setNsfwMaskMode] / [deleteRule]）。
 *
 * ## 那两道写口为什么在这颗类上、而不在共享契约上
 *
 * 第十二批 批 6 之前这一排期写着"要给 `GalleryContentGuard` 加 setter，得先点头"。重量过一遍：
 * 分级档落在键值件里（桌面这颗已经在读那一枚），删除落在 `GuardRuleStore.delete`（`data/db`，
 * 桌面编译面上）—— 两道都不必动契约，所以走第七批那条现成形状：界面吃**这颗具体件**当形参，
 * 转换点在集成方（`DesktopGalleryPorts.contentGuard`）。判据 `DesktopGuardWriteTest` ⑧ 反向钉着
 * "共享面一枚成员都没多"。
 *
 * ## 为什么这一颗不碰冻结的 `ContentGuardManager`
 *
 * 命中判定今天已经不在那颗类里了：`findGalleryBlockedRule` 的实体是
 * "启用中的规则里，第一颗被 [GalleryBlockMatch.blocked] 判中的那条"
 * （`ContentGuardManager.kt:266-267`），而 [GalleryBlockMatch] 与 [GuardRule] 都是零 import 的
 * 纯声明、已在桌面编译面上。所以桌面这边交回的是**同一个判据**，不是另写一套 ——
 * 这条是硬要求：图站 tag 是下划线标识符，子串匹配会让 `ai` 命中 `long_hair`，
 * 口径分叉的代价是"墙上少了几张图而没人知道为什么"。
 *
 * ## 读库时机
 *
 * [DatabasePorts] 那边的纪律是"接线只装 lambda、不碰 SQLite"，所以这里不放在构造里，
 * 而是第一次有人真问规则时才读（`lazy`）。读不出来**保持上一次已知的那批**（首次是空表）并出声：
 * 与 `ContentGuardManager.loadRules` 同一条口径 —— 少规则的方向是漏屏蔽，宁可漏也不把整屏打死，
 * 但不许静默。
 *
 * [nsfwMaskMode] 的键名、默认档与**那份键值件**三样都照对岸（`ContentGuardManager.kt:82-89`：
 * store 名 `venera_guard_prefs`、键 `nsfw_mode`、默认 `OFF`）。
 * 这一枚从前读在 `PREFS_NAME`（`venera_preferences`）上 —— 键名对、文件不对，
 * 于是桌面永远读到默认档；只有读面的时候这件事量不出来，补上写口才成为错（判据 ⑥′ 钉住）。
 * 这一档只决定"命中后怎么处理"，不参与命中判定。
 */
class DesktopGalleryContentGuard(private val handle: DesktopGalleryHandle) : GalleryContentGuard {

    private val ruleStore: GuardRuleStore by lazy { GuardRuleStore(DatabasePorts.of(handle).core) }

    /** 键值件也 `lazy`：构造里不碰盘（与 [ruleStore] 同一条纪律），第一次有人问这档才建。 */
    private val store by lazy { JsonKeyValueStore(PreferenceKeys.PREFS_GUARD, handle.paths) }

    private val ruleState by lazy { MutableStateFlow(loadOrNull(previous = emptyList())) }

    override val rules: StateFlow<List<GuardRule>> get() = ruleState.asStateFlow()

    private val maskState by lazy {
        MutableStateFlow(
            store.getString(KEY_NSFW_MODE, DEFAULT_MASK_MODE) ?: DEFAULT_MASK_MODE,
        )
    }

    override val nsfwMaskMode: StateFlow<String> get() = maskState.asStateFlow()

    override fun blockedGalleryRule(author: String, tags: List<String>): GuardRule? =
        rules.value.filter { it.isEnabled }.firstOrNull { GalleryBlockMatch.blocked(it, author, tags) }

    /** 写进的是**站点原值**；判成功用 `>= 0`，写不进去回报 -1 —— 与 Android 侧同一条口径。 */
    override suspend fun addRule(type: String, pattern: String, isRegex: Boolean): Long =
        withContext(Dispatchers.IO) {
            runCatching {
                val id = ruleStore.add(type, pattern.trim(), isRegex, System.currentTimeMillis())
                ruleState.value = loadOrNull(previous = ruleState.value)
                id
            }.getOrDefault(NO_RULE_ID)
        }

    /**
     * 删一条规则。名字与 Android 那道写口同一枚（`guardRuleBook.deleteRule`），
     * 返回值也照它：**库里本来有没有这一条**。
     *
     * 所以 `false` 有两种意思 —— 删砸了，或者那一条本来就不在表里了。两种都不许报成"已删掉"：
     * 前者用户以为规则没了而墙上还在挡，后者那一条早没了、这一屏此刻显示的是别的东西。
     * 这里不替调用方分辨这两种（要分辨得让 [GuardRuleStore.delete] 改回报三态，那是共享面的改动），
     * 界面上那一句因此说的是"没有删掉，这一条可能本来就不在表里"。
     */
    suspend fun deleteRule(id: Long): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val deleted = ruleStore.delete(id)
                ruleState.value = loadOrNull(previous = ruleState.value)
                deleted
            }.getOrDefault(false)
        }

    /**
     * 写分级档：先落盘、再把那条流推到新值（与 `ContentGuardManager.setNsfwMaskMode` 同一形状，
     * 那里之后还有一次 `invalidate()` 清判定缓存 —— 桌面这边没有缓存，流的本身就是缓存）。
     *
     * 落盘失败时**流不动**，于是屏上继续显示旧的那一档，而不是显示一档其实没存上的值；
     * 返回值只表达"写进去没有"这一件事，界面上那句"保存失败"由它来说。
     *
     * ⚠️ 这里**不重列**三档合法值：那张表在桌面只有一份（`DesktopGuardPolicy.desktopMaskModeLabels`），
     * 界面写之前过 `desktopRequireMaskMode`。在这颗类里再列一遍 `OFF/BLUR/HIDE`，
     * 就是同端两处口径 —— 哪天加第四档，漏改这一处会静默收下写错的值。
     */
    fun setNsfwMaskMode(mode: String): Boolean = runCatching {
        store.put(KEY_NSFW_MODE, mode)
        maskState.value = mode
    }.isSuccess

    private fun loadOrNull(previous: List<GuardRule>): List<GuardRule> = runCatching {
        ruleStore.loadAll().map {
            GuardRule(
                id = it.id,
                type = it.ruleType,
                pattern = it.pattern,
                isRegex = it.isRegex,
                isEnabled = it.isEnabled,
            )
        }
    }.getOrElse { failure ->
        DesktopGalleryFailureLog.warn(
            TAG,
            "屏蔽规则读不出来，沿用已知的那批（本次 ${failure.message}）；" +
                "这一档失败的方向是**漏屏蔽**，不是把整屏打死",
        )
        previous
    }

    private companion object {
        const val TAG = "DesktopGalleryContentGuard"
        const val KEY_NSFW_MODE = "nsfw_mode"
        const val DEFAULT_MASK_MODE = "OFF"
        const val NO_RULE_ID = -1L
    }
}
