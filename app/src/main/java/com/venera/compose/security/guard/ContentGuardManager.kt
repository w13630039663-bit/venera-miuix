package com.venera.compose.security.guard

import android.content.ContentValues
import android.content.Context
import com.venera.compose.data.db.VeneraDatabase
import com.venera.compose.feature.ComicItem
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ExplorePagePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.LinkedHashMap

data class GuardRule(
    val id: Long,
    val type: String, // "KEYWORD", "TAG", "AUTHOR", "COMIC_ID"
    val pattern: String,
    val isRegex: Boolean,
    val isEnabled: Boolean
)

/**
 * 全局内容屏蔽与分级安全守卫 (S7)
 *
 * 判定链（对齐原版 venera-miuix ContentGuard，命中即停）：
 *  1. 用户屏蔽规则（DB 规则：关键词/标签/作者/ID，最高优先）
 *  2. 预设表源级判定（assets/source_content_warning.json：jm/picacg/nhentai 等
 *     整站成人源直接命中，MangaDex/copy_manga 等 safe 源靠显式标记兜底）
 *  3. 显式成人标记预编译正则兜底（R-18 / 18禁 / 無修正 / エロ / 里番 / hentai …），
 *     扫描标题 / 作者 / 描述 / 标签（剥离 female: male: 等命名空间前缀后参与匹配）
 *  4. 默认 safe
 *
 * 遮蔽模式（nsfwMaskMode）只决定「命中后怎么处理」，不参与命中判定：
 *  - OFF  : 不过滤不打码
 *  - BLUR : 条目**必须保留**，由 [coverMaskStateFor] 返回 BLURRED 交卡片打码
 *           （列表剔除绝不能在此模式发生，否则整站成人源在探索页直接变空列表）
 *  - HIDE : [filterComicModels] / [filterExploreParts] 才执行物理剔除
 */
class ContentGuardManager private constructor(private val context: Context) {

    private val dbHelper = VeneraDatabase.getInstance(context)
    private val prefs = context.getSharedPreferences("venera_guard_prefs", Context.MODE_PRIVATE)

    private val _rules = MutableStateFlow<List<GuardRule>>(emptyList())
    val rules: StateFlow<List<GuardRule>> = _rules.asStateFlow()

    private val _nsfwMaskMode = MutableStateFlow(prefs.getString("nsfw_mode", "OFF") ?: "OFF")
    val nsfwMaskMode: StateFlow<String> = _nsfwMaskMode.asStateFlow()

    // ── 源级预设表（对齐原版 source_content_warning.json）──
    /** sourceKey -> "safe" | "mixed" | "nsfw"；未收录的源不在 map 中。 */
    private val sourcePresets = HashMap<String, String>()

    // ── 显式成人标记（预编译：判定在列表滚动时每帧都会跑，绝不能在调用点 new Regex）──
    private val explicitPatterns = listOf(
        Regex("r[\\s\\-_]?18", RegexOption.IGNORE_CASE),   // R18 / R-18 / R 18
        Regex("18\\s*[-_]?\\s*禁"),                          // 18禁 / 18 禁
        Regex("18\\s*\\+"),                                  // 18+
        Regex("成人向|成人漫画|成人漫畫|成人誌|成人志"),
        Regex("無修正|无修正|無碼|无码"),
        Regex("エロ"),
        Regex("里番|裏番"),
        Regex("hentai", RegexOption.IGNORE_CASE),
        Regex("\\badult\\b", RegexOption.IGNORE_CASE),
        Regex("\\bnsfw\\b", RegexOption.IGNORE_CASE),
        Regex("\\bporn", RegexOption.IGNORE_CASE),
    )

    // ── 判定 LRU 缓存：key = sourceKey@id，value = HIT / MISS ──
    private val verdictCache = LinkedHashMap<String, String>()

    init {
        loadRules()
        loadSourcePresets()
    }

    fun setNsfwMaskMode(mode: String) {
        prefs.edit().putString("nsfw_mode", mode).apply()
        _nsfwMaskMode.value = mode
        invalidate()
    }

    fun loadRules() {
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery("SELECT * FROM content_guard_rules ORDER BY id DESC", null)
            val list = mutableListOf<GuardRule>()
            cursor.use {
                val idIdx = it.getColumnIndex("id")
                val typeIdx = it.getColumnIndex("rule_type")
                val patIdx = it.getColumnIndex("pattern")
                val regIdx = it.getColumnIndex("is_regex")
                val enIdx = it.getColumnIndex("is_enabled")
                while (it.moveToNext()) {
                    list.add(
                        GuardRule(
                            id = it.getLong(idIdx),
                            type = it.getString(typeIdx),
                            pattern = it.getString(patIdx),
                            isRegex = it.getInt(regIdx) == 1,
                            isEnabled = it.getInt(enIdx) == 1
                        )
                    )
                }
            }
            _rules.value = list
            invalidate()
        } catch (_: Exception) {}
    }

    /**
     * 载入内置源级分级预设表（assets/source_content_warning.json，随 APK 打包）。
     * 加载失败不致命：退化为「全部 safe」，判定链仍有用户规则 + 显式标记兜底。
     */
    private fun loadSourcePresets() {
        try {
            val text = context.assets.open("source_content_warning.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val sources = root.optJSONObject("sources") ?: return
            sources.keys().forEach { key ->
                val entry = sources.optJSONObject(key) ?: return@forEach
                sourcePresets[key] = entry.optString("level", "safe")
            }
        } catch (_: Exception) {
            // 预设表缺失/损坏：清空已载入内容，全部按 safe 处理（宁松勿严）。
            sourcePresets.clear()
        }
    }

    /**
     * 源显示名 -> sourceKey 别名表。
     *
     * 为什么需要：历史记录等本地持久化数据里存的是**源显示名**（如 "拷贝漫画"），
     * 而预设表按 sourceKey（如 "copy_manga"）索引。别名由壳层在源加载后注入。
     */
    private val sourceNameAliases = HashMap<String, String>()

    /** 注册「源显示名 -> sourceKey」别名（源列表加载完成后由壳层调用）。 */
    fun registerSourceNameAliases(aliases: Map<String, String>) {
        synchronized(sourceNameAliases) {
            sourceNameAliases.clear()
            sourceNameAliases.putAll(aliases)
        }
        invalidate()
    }

    /**
     * 把传入的 sourceKeyOrName 归一成预设表的 sourceKey：
     *  - 本身就是预设 key -> 原样返回；
     *  - 是已注册的显示名 -> 解析成 key；
     *  - 其他 -> 原样返回（未收录源按 safe）。
     */
    private fun resolveSourceKey(sourceKeyOrName: String): String {
        if (sourceKeyOrName.isBlank()) return sourceKeyOrName
        if (sourcePresets.containsKey(sourceKeyOrName)) return sourceKeyOrName
        return synchronized(sourceNameAliases) { sourceNameAliases[sourceKeyOrName] } ?: sourceKeyOrName
    }

    /** 源级预设分级（"nsfw"/"mixed"/"safe"）；未收录源返回 null（按 safe）。 */
    private fun sourcePresetLevel(sourceKey: String): String? =
        if (sourceKey.isBlank()) null else sourcePresets[sourceKey]

    /** 设置/规则/预设变化后必须调用：否则用户改完看不到效果。 */
    private fun invalidate() {
        synchronized(verdictCache) { verdictCache.clear() }
    }

    suspend fun addRule(type: String, pattern: String, isRegex: Boolean = false): Long = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("rule_type", type)
                put("pattern", pattern.trim())
                put("is_regex", if (isRegex) 1 else 0)
                put("is_enabled", 1)
                put("created_at", System.currentTimeMillis())
            }
            val id = db.insert("content_guard_rules", null, cv)
            loadRules()
            id
        } catch (_: Exception) {
            -1L
        }
    }

    suspend fun deleteRule(id: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = dbHelper.writableDatabase
            val count = db.delete("content_guard_rules", "id = ?", arrayOf(id.toString()))
            loadRules()
            count > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 检查单本漫画是否命中「用户屏蔽规则」。
     * 注意：源级预设 / 显式标记判定不在本方法内 —— 它们只参与遮蔽判定链
     * （[coverMaskStateFor]），不应影响用户显式黑名单的语义。
     */
    fun isComicBlocked(title: String, author: String = "", tags: List<String> = emptyList(), comicId: String = "", description: String = ""): Boolean {
        val activeRules = _rules.value.filter { it.isEnabled }
        if (activeRules.isEmpty()) return false

        // 标签拆分：e-hentai 的 "female:big breasts" 命中 "big breasts" 也算。
        val normalizedTags = tags.flatMap { tag ->
            val plain = if (tag.contains(':')) tag.substringAfter(':').trim() else tag
            if (plain.isBlank() || plain == tag) listOf(tag) else listOf(tag, plain)
        }.filter { it.isNotBlank() }

        for (rule in activeRules) {
            when (rule.type) {
                "KEYWORD" -> {
                    if (match(rule, title)) return true
                    if (author.isNotBlank() && match(rule, author)) return true
                    if (description.isNotBlank() && match(rule, description)) return true
                    if (normalizedTags.any { match(rule, it) }) return true
                }
                "AUTHOR" -> {
                    if (author.isNotBlank() && match(rule, author)) return true
                }
                "TAG" -> {
                    if (normalizedTags.any { match(rule, it) }) return true
                }
                "COMIC_ID" -> {
                    if (comicId.isNotBlank() && rule.pattern.equals(comicId, ignoreCase = true)) return true
                }
            }
        }
        return false
    }

    /**
     * 过滤漫画列表（列表物理剔除）。
     *
     * 语义 = 「用户显式黑名单剔除」+「HIDE 强度剔除」：
     *  - 用户规则命中：任何遮蔽模式下都剔除（显式黑名单，等同原版 blockedWords）。
     *  - 源级/显式标记命中：仅 HIDE 模式剔除；**BLUR 模式严禁走到这里**，
     *    页面侧已按 nsfwMaskMode == "HIDE" 分流，BLUR 时条目必须保留交卡片打码。
     */
    fun filterComicModels(comics: List<Comic>): List<Comic> {
        val activeRules = _rules.value.filter { it.isEnabled }
        val hideMode = _nsfwMaskMode.value == "HIDE"
        if (activeRules.isEmpty() && !hideMode) return comics
        return comics.filterNot { comic ->
            val userBlocked = isComicBlocked(
                title = comic.title,
                author = comic.subTitle,
                tags = comic.tags,
                comicId = comic.id,
                description = comic.description,
            )
            val nsfwHide = hideMode &&
                maskStateInternal(comic.sourceKey, comic.title, comic.subTitle, comic.tags, comic.id, comic.description)
            userBlocked || nsfwHide
        }
    }

    /**
     * 过滤探索页分区（逐分区过滤，空分区整块剔除，避免留下"孤儿标题"）。
     * 仅在 HIDE 模式或存在用户黑名单时执行实际过滤。
     */
    fun filterExploreParts(parts: List<ExplorePagePart>): List<ExplorePagePart> {
        val activeRules = _rules.value.filter { it.isEnabled }
        val hideMode = _nsfwMaskMode.value == "HIDE"
        if (activeRules.isEmpty() && !hideMode) return parts
        return parts.mapNotNull { part ->
            val filtered = filterComicModels(part.comics)
            if (filtered.isEmpty()) null else part.copy(comics = filtered)
        }
    }

    /**
     * R18 分级遮罩判定（页面卡片消费的口径）：
     *
     * - "OFF"  → 全部 VISIBLE
     * - "BLUR" → 命中判定链（用户规则 / 源级预设 / 显式标记）→ BLURRED
     * - "HIDE" → 命中判定链 → HIDDEN（列表剔除已在数据层完成，此处兜底）
     */
    fun coverMaskStateFor(title: String, author: String = "", tags: List<String> = emptyList(), comicId: String = "", description: String = ""): String {
        val mode = _nsfwMaskMode.value
        if (mode == "OFF") return "VISIBLE"
        // 无 sourceKey 的重载路径：不走 LRU（调用方未提供源身份，逐次判定即可）。
        return if (maskHit(title, author, tags, comicId, description, null)) {
            if (mode == "HIDE") "HIDDEN" else "BLURRED"
        } else "VISIBLE"
    }

    /**
     * 带 sourceKey 的判定入口（本地收藏等非 [Comic] 模型使用）。
     * 与 [coverMaskStateFor] 同一套判定链（用户规则 > 源级预设 > 显式标记），走 LRU 缓存。
     */
    fun coverMaskStateFor(
        sourceKey: String,
        title: String,
        author: String = "",
        tags: List<String> = emptyList(),
        comicId: String = "",
        description: String = "",
    ): String {
        val mode = _nsfwMaskMode.value
        if (mode == "OFF") return "VISIBLE"
        val key = sourceKey + "@" + comicId
        val cached = synchronized(verdictCache) { verdictCache.remove(key) }
        if (cached != null) {
            synchronized(verdictCache) { verdictCache[key] = cached }
            return if (cached == "HIT") (if (mode == "HIDE") "HIDDEN" else "BLURRED") else "VISIBLE"
        }
        val hit = maskHit(title, author, tags, comicId, description, sourceKey)
        synchronized(verdictCache) {
            verdictCache[key] = if (hit) "HIT" else "MISS"
            while (verdictCache.size > CACHE_LIMIT) verdictCache.remove(verdictCache.keys.first())
        }
        return if (hit) (if (mode == "HIDE") "HIDDEN" else "BLURRED") else "VISIBLE"
    }

    /** 带 [Comic] 的判定入口（走 LRU 缓存，列表场景优先使用）。 */
    fun coverMaskStateFor(comic: Comic): String {
        val mode = _nsfwMaskMode.value
        if (mode == "OFF") return "VISIBLE"
        val key = comic.sourceKey + "@" + comic.id
        val cached = synchronized(verdictCache) { verdictCache.remove(key) }
        if (cached != null) {
            synchronized(verdictCache) { verdictCache[key] = cached }
            return if (cached == "HIT") (if (mode == "HIDE") "HIDDEN" else "BLURRED") else "VISIBLE"
        }
        val hit = maskHit(comic.title, comic.subTitle, comic.tags, comic.id, comic.description, comic.sourceKey)
        synchronized(verdictCache) {
            verdictCache[key] = if (hit) "HIT" else "MISS"
            while (verdictCache.size > CACHE_LIMIT) verdictCache.remove(verdictCache.keys.first())
        }
        return if (hit) (if (mode == "HIDE") "HIDDEN" else "BLURRED") else "VISIBLE"
    }

    /** 判定链核心（命中即停）：用户规则 > 源级预设 > 显式标记正则。 */
    private fun maskHit(
        title: String,
        author: String,
        tags: List<String>,
        comicId: String,
        description: String,
        sourceKey: String?,
    ): Boolean {
        if (isComicBlocked(title, author, tags, comicId, description)) return true
        if (sourceKey != null) {
            // 显示名别名归一后再查预设表（历史记录等场景传的是源显示名）。
            val level = sourcePresets[resolveSourceKey(sourceKey)]
            if (level == "nsfw" || level == "mixed") return true
        }
        return hasExplicitMark(title, author, tags, description)
    }

    private fun maskStateInternal(sourceKey: String, title: String, author: String, tags: List<String>, comicId: String, description: String): Boolean =
        maskHit(title, author, tags, comicId, description, sourceKey)

    /** 显式成人标记：预编译正则扫描 标题/作者/描述/标签（含命名空间拆分）。 */
    private fun hasExplicitMark(title: String, author: String, tags: List<String>, description: String): Boolean {
        val fields = buildList {
            add(title)
            if (author.isNotBlank()) add(author)
            if (description.isNotBlank()) add(description)
            tags.forEach { tag ->
                add(tag)
                if (tag.contains(':')) add(tag.substringAfter(':'))
            }
        }
        return fields.any { field -> field.isNotBlank() && explicitPatterns.any { p -> p.containsMatchIn(field) } }
    }

    private fun match(rule: GuardRule, target: String): Boolean {
        return try {
            if (rule.isRegex) {
                Regex(rule.pattern, RegexOption.IGNORE_CASE).containsMatchIn(target)
            } else {
                target.contains(rule.pattern, ignoreCase = true)
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        /** 判定 LRU 上限（对齐原版 2000 条）。 */
        const val CACHE_LIMIT = 2000

        @Volatile
        private var INSTANCE: ContentGuardManager? = null

        fun getInstance(context: Context): ContentGuardManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ContentGuardManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
