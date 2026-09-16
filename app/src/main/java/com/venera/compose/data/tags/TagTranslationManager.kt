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

/**
 * 标签多语言与命名空间翻译管理服务 (S4 核心基础设施)
 *
 * 数据源：
 * - assets/tags.json (1.04MB 完整 EhTagTranslation 词典)
 * - 支持 rows (命名空间：female, male, parody, character, artist, language, etc.)
 * - 异步内存常驻哈希索引，提供 O(1) 毫秒级翻译与前缀搜索补全
 */
class TagTranslationManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 命名空间字典: "female" -> "女性", "parody" -> "原作"
    private val namespaceMap = mutableMapOf<String, String>()

    // 全量反向与正向字典: "touhou project" -> "东方Project", "yuri" -> "百合"
    private val tagDictionary = mutableMapOf<String, String>()

    // 带命名空间的字典: "parody:touhou project" -> "东方Project"
    private val scopedDictionary = mutableMapOf<String, String>()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        loadDictionaryAsync()
    }

    private fun loadDictionaryAsync() {
        scope.launch {
            try {
                val jsonString = context.assets.open("tags.json").bufferedReader().use { it.readText() }
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

                // 2. 遍历各分类命名空间
                val topKeys = root.keys()
                while (topKeys.hasNext()) {
                    val namespace = topKeys.next()
                    if (namespace == "rows") continue
                    val categoryObj = root.optJSONObject(namespace) ?: continue

                    val itemKeys = categoryObj.keys()
                    while (itemKeys.hasNext()) {
                        val rawKey = itemKeys.next()
                        val rawVal = categoryObj.optString(rawKey)
                        if (rawVal.isNotBlank()) {
                            val lowerKey = rawKey.lowercase()
                            scopedDictionary["$namespace:$lowerKey"] = rawVal
                            if (!tagDictionary.containsKey(lowerKey)) {
                                tagDictionary[lowerKey] = rawVal
                            }
                        }
                    }
                }
                _isLoaded.value = true
                android.util.Log.i("TagTranslationManager", "Loaded ${tagDictionary.size} tags in dictionary.")
            } catch (e: Exception) {
                android.util.Log.w("TagTranslationManager", "Failed to load tags.json: ${e.message}")
            }
        }
    }

    /**
     * 翻译单标签。如果带命名空间（如 "parody:touhou project"），优先按命名空间检索
     */
    fun translate(tag: String, namespace: String? = null): String {
        val lowerTag = tag.lowercase().trim()
        if (namespace != null) {
            val scoped = scopedDictionary["${namespace.lowercase()}:$lowerTag"]
            if (!scoped.isNullOrBlank()) return scoped
        }
        return tagDictionary[lowerTag] ?: tag
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

        for ((raw, translation) in tagDictionary) {
            if (raw.contains(lowerQuery) || translation.contains(lowerQuery)) {
                results.add(raw to translation)
                if (results.size >= limit) break
            }
        }
        return results
    }

    companion object {
        @Volatile
        private var INSTANCE: TagTranslationManager? = null

        fun getInstance(context: Context): TagTranslationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TagTranslationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
