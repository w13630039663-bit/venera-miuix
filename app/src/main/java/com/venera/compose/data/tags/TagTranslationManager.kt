package com.venera.compose.data.tags

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 标签多语言与命名空间翻译管理服务 (S4 核心基础设施)
 *
 * 数据源：
 * - assets/tags.json (1.04MB 完整 EhTagTranslation 词典, 简体)
 * - assets/tags_tw.json (同键集合的繁体版)
 * - 支持 rows (命名空间：female, male, mixed, language, parody, character, artist, etc.)
 * - 异步内存常驻哈希索引，提供 O(1) 毫秒级翻译与前缀搜索补全
 *
 * 繁体按需加载：只有当用户的标签显示语言解析为繁体时才读 tags_tw.json，
 * 简体保持「构造即加载」（搜索联想依赖它，不能等）。
 */
class TagTranslationManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 命名空间字典: "female" -> "女性", "parody" -> "原作"
    private val namespaceMap = mutableMapOf<String, String>()

    /** 单一语言的字典槽。三个视图服务不同用途，互不共用。 */
    private class LangDict {
        /** 带命名空间的精确字典: "parody:touhou project" -> "东方Project" */
        val scoped = HashMap<String, String>()

        /** 全局**唯一译法**字典: "yuri" -> "百合"（同键在不同命名空间下译法不一致的键被排除） */
        val unique = HashMap<String, String>()

        /** 先到先赢的全量字典，仅供搜索联想遍历；与今日行为一致，不承诺无歧义。 */
        val flat = HashMap<String, String>()
    }

    private val dicts = ConcurrentHashMap<String, LangDict>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    private val _loadedLanguages = MutableStateFlow<Set<String>>(emptySet())
    val loadedLanguages: StateFlow<Set<String>> = _loadedLanguages.asStateFlow()

    init {
        loadLanguage(LANGUAGE_SIMPLIFIED)
    }

    /** 幂等：目标语言字典未就绪时异步加载，已就绪则什么都不做。 */
    fun ensureLanguage(language: String) {
        if (!dicts.containsKey(language)) loadLanguage(language)
    }

    private fun loadLanguage(language: String) {
        if (!inFlight.add(language)) return
        scope.launch {
            try {
                val jsonString = context.assets.open(assetOf(language))
                    .bufferedReader().use { it.readText() }
                parse(jsonString)?.let {
                    dicts[language] = it
                    _loadedLanguages.value = _loadedLanguages.value + language
                    android.util.Log.i(
                        "TagTranslationManager",
                        "Loaded ${it.flat.size} tags (${it.unique.size} unambiguous) for $language.",
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("TagTranslationManager", "Failed to load ${assetOf(language)}: ${e.message}")
            } finally {
                inFlight.remove(language)
            }
        }
    }

    private fun assetOf(language: String): String =
        if (language == LANGUAGE_TRADITIONAL) "tags_tw.json" else "tags.json"

    private fun parse(jsonString: String): LangDict? {
        val dict = LangDict()
        val root = JSONObject(jsonString)

        // 1. 读取 rows 命名空间
        val rowsObj = root.optJSONObject("rows")
        if (rowsObj != null) {
            val keys = rowsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                namespaceMap[key.lowercase()] = rowsObj.optString(key)
            }
        }

        // 2. 遍历各分类命名空间。同一英文键在不同命名空间下可能译法不同（实测 403 条），
        //    所以「全局字典」必须只收无歧义键 —— 多数源给的标签是**不带命名空间**的扁平串，
        //    此时唯一安全的做法是要么译对、要么不译，绝不能猜。
        val ambiguous = HashSet<String>()
        val topKeys = root.keys()
        while (topKeys.hasNext()) {
            val namespace = topKeys.next()
            if (namespace == "rows") continue
            val categoryObj = root.optJSONObject(namespace) ?: continue

            val itemKeys = categoryObj.keys()
            while (itemKeys.hasNext()) {
                val rawKey = itemKeys.next()
                val rawVal = categoryObj.optString(rawKey)
                if (rawVal.isBlank()) continue
                val lowerKey = rawKey.lowercase()
                dict.scoped["${namespace.lowercase()}:$lowerKey"] = rawVal

                val first = dict.flat[lowerKey]
                when {
                    first == null -> dict.flat[lowerKey] = rawVal
                    first != rawVal -> ambiguous.add(lowerKey)
                }
            }
        }
        dict.flat.forEach { (key, value) ->
            if (key !in ambiguous) dict.unique[key] = value
        }
        return dict
    }

    /**
     * 展示用译文。只在以下两种情形给出译文，其余一律返回 null 让调用方保留原文：
     * 1. 标签自带命名空间（如 `female:yuri`）—— 可精确定位，无歧义；
     * 2. 该英文键在全字典里译法唯一。
     *
     * 宁可不译，不可译错：译文只用于显示，请求侧仍走 [com.venera.compose.feature.SearchTag.raw]。
     */
    fun displayLabel(tag: String, language: String): String? {
        val dict = dicts[language] ?: return null
        val text = tag.trim()
        if (text.isEmpty()) return null
        val colon = text.indexOf(':')
        if (colon > 0) {
            val namespace = text.substring(0, colon).trim().lowercase()
            val body = text.substring(colon + 1).trim().lowercase()
            if (body.isEmpty()) return null
            return dict.scoped["$namespace:$body"] ?: dict.unique[body]
        }
        return dict.unique[text.lowercase()]
    }

    /**
     * 翻译单标签。如果带命名空间（如 "parody:touhou project"），优先按命名空间检索
     */
    fun translate(tag: String, namespace: String? = null): String {
        val lowerTag = tag.lowercase().trim()
        val dict = dicts[LANGUAGE_SIMPLIFIED] ?: return tag
        if (namespace != null) {
            val scoped = dict.scoped["${namespace.lowercase()}:$lowerTag"]
            if (!scoped.isNullOrBlank()) return scoped
        }
        return dict.flat[lowerTag] ?: tag
    }

    /**
     * 获取命名空间中文展示名 (如 "female" -> "女性")
     */
    fun getNamespaceName(namespace: String): String {
        return namespaceMap[namespace.lowercase()] ?: namespace
    }

    /**
     * 搜索联想建议：根据输入的关键字前缀，在英日原文或中文译名中匹配建议标签
     */
    fun suggestTags(query: String, limit: Int = 10): List<Pair<String, String>> {
        if (query.isBlank()) return emptyList()
        val lowerQuery = query.lowercase().trim()
        val results = mutableListOf<Pair<String, String>>()

        for ((raw, translation) in (dicts[LANGUAGE_SIMPLIFIED]?.flat ?: return emptyList())) {
            if (raw.contains(lowerQuery) || translation.contains(lowerQuery)) {
                results.add(raw to translation)
                if (results.size >= limit) break
            }
        }
        return results
    }

    companion object {
        const val LANGUAGE_SIMPLIFIED = "zh_CN"
        const val LANGUAGE_TRADITIONAL = "zh_TW"

        @Volatile
        private var INSTANCE: TagTranslationManager? = null

        fun getInstance(context: Context): TagTranslationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TagTranslationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
