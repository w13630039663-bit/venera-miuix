package com.venera.compose.data.tags

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.venera.compose.data.prefs.TagTranslationMode
import com.venera.compose.data.prefs.VeneraPreferences

/**
 * 标签**译文显示**器：把源生标签换成「译文 (原文)」双显。
 *
 * 只改显示文本：点击药丸时回传的、以及构造查询时发送的始终是源生原文
 * （`TagSearchPolicy.requestKeyword` 只读 `SearchTag.raw`），
 * 所以译文怎么显示都不会影响搜索结果 —— 这是「统一视觉，不统一 Source 能力」的落地口径。
 *
 * 原文常驻括号里是刻意的：字典覆盖不到的中文源标签、以及 1.2% 的多命名空间歧义键
 * 都会退回原文，若同时隐藏原文，用户就无从判断这个药丸到底对应源里的哪个词。
 */
@Composable
fun rememberTagDisplayLabel(): (String) -> String {
    val context = LocalContext.current
    val manager = remember(context) { TagTranslationManager.getInstance(context) }
    val prefs = remember(context) { VeneraPreferences.getInstance(context) }
    val mode by prefs.tagTranslationMode.collectAsStateWithLifecycle()
    val loadedLanguages by manager.loadedLanguages.collectAsStateWithLifecycle()

    val language = resolveTagLanguage(mode, context)
    LaunchedEffect(language) { language?.let(manager::ensureLanguage) }

    val ready = language != null && language in loadedLanguages
    return remember(ready, language, manager) {
        { raw ->
            val target = language
            if (!ready || target == null) raw
            else manager.displayLabel(raw, target)
                ?.takeIf { it.isNotBlank() && !it.equals(raw, ignoreCase = true) }
                ?.let { "$it ($raw)" }
                ?: raw
        }
    }
}

/**
 * SYSTEM 模式下，系统语言同时决定「译不译」和「译成简还是繁」：
 * 非中文一律不译（显原文），zh-Hant / 台港澳 → 繁体，其余中文 → 简体。
 */
private fun resolveTagLanguage(mode: TagTranslationMode, context: Context): String? = when (mode) {
    TagTranslationMode.OFF -> null
    TagTranslationMode.SIMPLIFIED -> TagTranslationManager.LANGUAGE_SIMPLIFIED
    TagTranslationMode.TRADITIONAL -> TagTranslationManager.LANGUAGE_TRADITIONAL
    TagTranslationMode.SYSTEM -> {
        val locale = context.resources.configuration.locales[0]
        when {
            !locale.language.equals("zh", ignoreCase = true) -> null
            locale.script.equals("Hant", ignoreCase = true) ||
                locale.country.uppercase() in TRADITIONAL_REGIONS ->
                TagTranslationManager.LANGUAGE_TRADITIONAL
            else -> TagTranslationManager.LANGUAGE_SIMPLIFIED
        }
    }
}

private val TRADITIONAL_REGIONS = setOf("TW", "HK", "MO")
