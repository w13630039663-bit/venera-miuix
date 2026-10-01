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
 * 标签多语言与命名空间翻译管理服务 
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
     * 取若干命名空间的「英文键 → 中文译名」词条，**按传入顺序**拼接，返回的键已去掉 `"ns:"` 前缀。
     *
     * 为什么是函数而不是直接把 map 交出去：`LangDict.scoped` 是 HashMap（无序），而题材归一化
     * 需要一个**确定的先后顺序** —— 同一英文键在不同命名空间下译法可能不同（实测 403 条歧义），
     * 谁在前决定了最终规范名。所以顺序由调用方给，这里只负责过滤 + 去前缀。
     *
     * 字典是异步加载的（见 [init]）：未就绪时返回空表，调用方据此退化。
     */
    fun topicEntries(
        namespaces: List<String>,
        language: String = LANGUAGE_SIMPLIFIED,
    ): List<Pair<String, String>> {
        val scoped = dicts[language]?.scoped ?: return emptyList()
        val out = ArrayList<Pair<String, String>>()
        for (namespace in namespaces) {
            val prefix = namespace.lowercase() + ":"
            for ((key, value) in scoped) {
                if (key.startsWith(prefix)) out.add(key.substring(prefix.length) to value)
            }
        }
        return out
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
     *
     * 两级：先查字典 rows（EhTag 的 12 个命名空间），未命中再查源原生键兜底表，
     * 都没有才原样返回 —— 宁可不译也不臆造一个错的组名。
     */
    fun getNamespaceName(namespace: String): String {
        val lower = namespace.lowercase()
        return namespaceMap[lower] ?: sourceNamespaceLabel(lower) ?: namespace
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

/**
 * 源原生命名空间键的中文兜底表（键一律小写；`tags.json` 的 rows 优先，未命中才查这里）。
 *
 * 为什么需要：字典 rows 只覆盖 EhTag 那 12 个命名空间，而多数中文源写的是自己造的键
 * （哔咔的 `Chinese Team`、禁漫的 `Work`/`View`…），于是详情页在中文界面里裸显
 * `Author:` / `Categories:`，读起来像没加载完。
 *
 * 表项**全部来自 `app/src/main/assets/sources` 下各 .js 源里实际写出的键**，不是推测：
 * jm.js（Author/Tag/Work/Actor/View）、picacg.js（Author/Chinese Team/Categories/Tags）、
 * manga_dex.js（Status/Authors/Artists/Tags）、lanraragi.js（Tags/Pages/Extension）、
 * nhentai.js（Categories/Tags）、shonen_jump_plus.js（Update）。
 * goda/mh18/hcomic 直接写中文键，走原样返回即可。
 *
 * 刻意**不**走 `JsComicSource.translate()`：那份字典服务的是探索页分类名
 * （见 venera-tag-multilang-plan.md §1.3「与标签无关，别混用」），且从 UI 取源实例要碰
 * Source / ViewModel 两个保护域。这里只是组名显示，点击检索仍携带源生原词，请求不变。
 */
private val SOURCE_NAMESPACE_LABELS = mapOf(
    "author" to "作者",
    "authors" to "作者",
    "artists" to "艺术家",
    "chinese team" to "汉化组",
    "tag" to "标签",
    "tags" to "标签",
    "category" to "分类",
    "categories" to "分类",
    "work" to "原作",
    "actor" to "出演",
    "status" to "状态",
    "update" to "更新",
    "view" to "浏览",
    "pages" to "页数",
    "extension" to "扩展",
)

/** 源原生命名空间键 → 中文组名；不在表内返回 null。 */
internal fun sourceNamespaceLabel(namespace: String): String? =
    SOURCE_NAMESPACE_LABELS[namespace.lowercase().trim()]

