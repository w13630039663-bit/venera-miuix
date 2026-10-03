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
 * 桌面侧的画廊内容守卫口 —— 四枚成员，直连已在桌面编译面上的 [GuardRuleStore]。
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
 * [nsfwMaskMode] 的键名与默认档照 `ContentGuardManager.kt:89`（`nsfw_mode`，默认 `OFF`）：
 * 这一档只决定"命中后怎么处理"，不参与命中判定。
 */
class DesktopGalleryContentGuard(private val handle: DesktopGalleryHandle) : GalleryContentGuard {

    private val ruleStore: GuardRuleStore by lazy { GuardRuleStore(DatabasePorts.of(handle).core) }

    private val ruleState by lazy { MutableStateFlow(loadOrNull(previous = emptyList())) }

    override val rules: StateFlow<List<GuardRule>> get() = ruleState.asStateFlow()

    private val maskState by lazy {
        MutableStateFlow(
            JsonKeyValueStore(PreferenceKeys.PREFS_NAME, handle.paths)
                .getString(KEY_NSFW_MODE, DEFAULT_MASK_MODE) ?: DEFAULT_MASK_MODE,
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
