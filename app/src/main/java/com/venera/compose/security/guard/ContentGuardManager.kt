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
 * 核心特性：
 * 1. 屏蔽词 (标题/描述匹配)
 * 2. 屏蔽标签 (Tags 匹配)
 * 3. 屏蔽画师/作者 (Author 匹配)
 * 4. 支持正则表达式与普通包含匹配
 * 5. 分级遮罩模式: OFF (不过滤), BLUR (封面模糊/打码), HIDE (完全从列表中隐藏)
 */
class ContentGuardManager private constructor(private val context: Context) {

    private val dbHelper = VeneraDatabase.getInstance(context)
    private val prefs = context.getSharedPreferences("venera_guard_prefs", Context.MODE_PRIVATE)

    private val _rules = MutableStateFlow<List<GuardRule>>(emptyList())
    val rules: StateFlow<List<GuardRule>> = _rules.asStateFlow()

    private val _nsfwMaskMode = MutableStateFlow(prefs.getString("nsfw_mode", "OFF") ?: "OFF")
    val nsfwMaskMode: StateFlow<String> = _nsfwMaskMode.asStateFlow()

    init {
        loadRules()
    }

    fun setNsfwMaskMode(mode: String) {
        prefs.edit().putString("nsfw_mode", mode).apply()
        _nsfwMaskMode.value = mode
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
        } catch (_: Exception) {}
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
     * 检查单本漫画是否命中屏蔽规则
     */
    fun isComicBlocked(title: String, author: String = "", tags: List<String> = emptyList(), comicId: String = ""): Boolean {
        val activeRules = _rules.value.filter { it.isEnabled }
        if (activeRules.isEmpty()) return false

        for (rule in activeRules) {
            when (rule.type) {
                "KEYWORD" -> {
                    if (match(rule, title)) return true
                }
                "AUTHOR" -> {
                    if (author.isNotBlank() && match(rule, author)) return true
                }
                "TAG" -> {
                    if (tags.any { tag -> match(rule, tag) }) return true
                }
                "COMIC_ID" -> {
                    if (comicId.isNotBlank() && rule.pattern.equals(comicId, ignoreCase = true)) return true
                }
            }
        }
        return false
    }

    /**
     * 过滤漫画列表
     */
    fun filterComics(comics: List<ComicItem>): List<ComicItem> {
        val activeRules = _rules.value.filter { it.isEnabled }
        if (activeRules.isEmpty()) return comics

        return comics.filterNot { comic ->
            isComicBlocked(
                title = comic.title,
                author = comic.author,
                tags = comic.tags,
                comicId = comic.id
            )
        }
    }

    /**
     * 过滤源生 Comic 模型列表（探索页 / 分类漫画流 / 搜索结果共用口径）
     *
     * Comic 模型的作者在 subTitle（"作者：xxx" 前缀交由 UI 拼接，模型层是纯文本），
     * tags 是源原生标签串。与 [filterComics] 保持同一套规则命中逻辑。
     */
    fun filterComicModels(comics: List<Comic>): List<Comic> {
        val activeRules = _rules.value.filter { it.isEnabled }
        if (activeRules.isEmpty()) return comics
        return comics.filterNot { comic ->
            isComicBlocked(
                title = comic.title,
                author = comic.subTitle,
                tags = comic.tags,
                comicId = comic.id
            )
        }
    }

    /**
     * 过滤探索页分区（逐分区过滤，空分区整块剔除，避免留下"孤儿标题"）
     */
    fun filterExploreParts(parts: List<ExplorePagePart>): List<ExplorePagePart> {
        val activeRules = _rules.value.filter { it.isEnabled }
        if (activeRules.isEmpty()) return parts
        return parts.mapNotNull { part ->
            val filtered = filterComicModels(part.comics)
            if (filtered.isEmpty()) null else part.copy(comics = filtered)
        }
    }

    /**
     * R18 分级遮罩判定：该漫画在当前遮罩模式下应如何展现。
     *
     * - "OFF"  → 不过滤（SFW 内容永远返回 VISIBLE）
     * - "BLUR" → 命中规则的封面打码（封面模糊 + 锁角标）
     * - "HIDE" → 命中规则的内容彻底隐藏（列表过滤已实现，此处同 BLUR 兜底）
     *
     * 返回 VISIBLE / BLURRED / HIDDEN。
     */
    fun coverMaskStateFor(title: String, author: String = "", tags: List<String> = emptyList(), comicId: String = ""): String {
        val mode = _nsfwMaskMode.value
        if (mode == "OFF") return "VISIBLE"
        if (!isComicBlocked(title, author, tags, comicId)) return "VISIBLE"
        return if (mode == "HIDE") "HIDDEN" else "BLURRED"
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
        @Volatile
        private var INSTANCE: ContentGuardManager? = null

        fun getInstance(context: Context): ContentGuardManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ContentGuardManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
