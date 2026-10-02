package com.venera.compose.security.guard

import android.content.Context
import com.venera.compose.data.db.DatabasePorts
import com.venera.compose.data.db.GuardRuleStore
import com.venera.compose.data.db.optTextValue
import com.venera.compose.data.db.parseJsonObject
import com.venera.compose.data.platform.android.AndroidKeyValueStore
import com.venera.compose.feature.ComicItem
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ExplorePagePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

data class GuardRule(
    val id: Long,
    val type: String, // "KEYWORD", "TAG", "AUTHOR", "COMIC_ID"
    val pattern: String,
    val isRegex: Boolean,
    val isEnabled: Boolean
)

/**
 * AI 标签词。归一（繁转简 + 小写）后**精确等值**。
 *
 * 出处：`ai` / `ai-generated` 逐字同 master 卡片 AI 角标的判据
 * （`lib/components/comic.dart:485-487`）；`ai生成` / `ai绘图` 取自本仓
 * [com.venera.compose.data.tags.TagNormalizer] 已归类好的中文形态（`:84`）。
 * 刻意不加"含 ai 字样就算"——那会误伤角色名与英文单词，master 也没这么做。
 * （反过来，裸 `ai` 这一枚在两站画廊上**也**是角色名：yande.re `tags=ai` 那 19 条是《Artery Gear》的 AI。
 * 漫画侧不动它 —— EH 那边 `AI生成` 那一系要用；画廊侧把它剔出去了，理由与读数见
 * [com.venera.compose.gallery.domain.GalleryAi]。）
 *
 * `internal` 而不是 private：画廊那把 AI 判据（[com.venera.compose.gallery.domain.GalleryAi]）
 * **以这张表为准再只收窄不放宽**，只把开关分家（用户 2026-09-29 拍板"画廊和漫画分开"= 分开的是开关）。
 * 词表复制第二份迟早会漂，漂的那一半通常是被抄的那份。
 */
internal val AiTagKeys = setOf("ai", "ai-generated", "ai生成", "ai绘图")

/**
 * AI 标签判据的纯函数部分（不含繁转简，那一步要 Context 与语言表，测不到）。
 *
 * 刻意**不剥** `female:` / `tag:` 这类命名空间前缀 —— master 的角标判据就是整串小写等值
 * （`comic.dart:485-487`）。剥了会把 `female:ai`（角色名 AI）也算成 AI 生成，
 * 属于误伤；宁可漏也不错杀整本正常漫画。
 */
internal fun isAiTagValue(raw: String): Boolean =
    raw.trim().lowercase() in AiTagKeys

/**
 * 标题里的 AI 标记（预编译：判定在列表滚动时每帧都跑，绝不能在调用点 new Regex，
 * 与本文件 [explicitPatterns] 同一约定）。
 *
 * 为什么需要它：e-hentai 把标记写在标题里（`[AI Generated]` / `[AI Art]`），
 * tags 里**没有** ai —— 纯标签判据会把整批漏掉（2026-09-22 实测截图）。
 *
 * 刻意只用「词组」与「括号独立词」两类，**不匹配裸 ai** —— 那会连 `openai`、
 * `AI少女`（角色名）、`waiting` 一起误杀。
 */
private val AiTitlePatterns = listOf(
    Regex("ai[\\s\\-_]?generated", RegexOption.IGNORE_CASE),   // AI Generated / AI-Generated / AIGenerated
    Regex("ai[\\s\\-_]?(生成|绘图|繪圖|作画|作畫)", RegexOption.IGNORE_CASE),   // 简繁两套都要写进模式，见下方说明
    Regex("ai[\\s\\-_]?art\\b", RegexOption.IGNORE_CASE),      // [AI Art] / AI-Art
    Regex("[\\[【(（]\\s*ai\\s*[\\]】)）]", RegexOption.IGNORE_CASE),            // [AI] 【AI】 (AI)：独立成词才算
)

/**
 * 标题判据**不**过繁转简表：那是逐字符查表，跑在每帧的列表判定上不划算，
 * 所以把简体/繁体两种写法直接写进模式里。
 */
internal fun isAiTitleMarked(title: String): Boolean =
    title.isNotBlank() && AiTitlePatterns.any { it.containsMatchIn(title) }


/**
 * 全局内容屏蔽与分级安全守卫
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
 *
 * `content_guard_rules` 的 SQL 在 [GuardRuleStore]（那颗不碰 Android 类型，所以 `:desktop:test`
 * 能拿真库跑规则读写）；这里剩下的才是 Context 的活：assets 里的源级预设表、偏好、内存缓存与判定链。
 */
class ContentGuardManager private constructor(private val context: Context) {

    private val ruleStore = com.venera.compose.StartupTrace.timed("Guard: DatabasePorts.of(core)") {
        GuardRuleStore(DatabasePorts.of(context).core)
    }
    private val prefs = com.venera.compose.StartupTrace.timed("Guard: KeyValueStore(venera_guard_prefs)") {
        AndroidKeyValueStore(context, "venera_guard_prefs")
    }

    private val _rules = MutableStateFlow<List<GuardRule>>(emptyList())
    val rules: StateFlow<List<GuardRule>> = _rules.asStateFlow()

    private val _nsfwMaskMode = MutableStateFlow(prefs.getString("nsfw_mode", "OFF") ?: "OFF")
    val nsfwMaskMode: StateFlow<String> = _nsfwMaskMode.asStateFlow()

    // ── 源级预设表（对齐原版 source_content_warning.json）──
    /** sourceKey -> "safe" | "mixed" | "nsfw"；未收录的源不在 map 中。 */
    private val sourcePresets = HashMap<String, String>()

    /**
     * 用户规则的正则按 pattern 记忆。[match] 在列表滚动里是「每项 × 每规则 × 每字段」
     * 的调用密度，现场 new Regex 与本文件既有的「绝不能在调用点 new Regex」约定相悖。
     */
    private val userRegexes = java.util.concurrent.ConcurrentHashMap<String, Regex>()

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
        com.venera.compose.StartupTrace.timed("Guard: loadRules() [SELECT content_guard_rules]") { loadRules() }
        com.venera.compose.StartupTrace.timed("Guard: loadSourcePresets() [assets+JSON]") { loadSourcePresets() }
    }

    fun setNsfwMaskMode(mode: String) {
        prefs.put("nsfw_mode", mode)
        _nsfwMaskMode.value = mode
        invalidate()
    }

    private val _blockAiComics = MutableStateFlow(prefs.getBoolean("block_ai", false))

    /** 「屏蔽 AI 生成漫画」开关。与 R18 的三档模式**互不相关**：命中 AI 一律物理剔除。 */
    val blockAiComics: kotlinx.coroutines.flow.StateFlow<Boolean> = _blockAiComics.asStateFlow()

    fun setBlockAiComics(enabled: Boolean) {
        prefs.put("block_ai", enabled)
        _blockAiComics.value = enabled
        invalidate()
    }

    /**
     * AI 标记词。归一（繁转简 + 小写）后**精确等值**，不扫标题也不扫描述。
     *
     * 出处：`ai` / `ai-generated` 逐字同 master 卡片 AI 角标的判据
     * （`lib/components/comic.dart:485-487`）；`ai生成` / `ai绘图` 取自本仓
     * [com.venera.compose.data.tags.TagNormalizer] 已归类好的中文形态（`:84`）。
     * 这里刻意不加"含 ai 字样就算"——那会误伤角色名与英文单词，master 也没这么做。
     */

    /** 源是否把这条标成了 AI 生成：标签（繁转简后等值）或标题（预编译词组）任一命中。 */
    private fun isAiMarked(title: String, tags: List<String>): Boolean =
        isAiTitleMarked(title) || tags.any { isAiTagValue(variantConverter.traditionalToSimplified(it)) }

    private val variantConverter by lazy {
        com.venera.compose.data.tags.ChineseVariantConverter.getInstance(context)
    }

    fun loadRules() {
        try {
            _rules.value = ruleStore.loadAll().map {
                GuardRule(
                    id = it.id,
                    type = it.ruleType,
                    pattern = it.pattern,
                    isRegex = it.isRegex,
                    isEnabled = it.isEnabled
                )
            }
            invalidate()
        } catch (e: Exception) {
            // 与改造前一致：规则读不出来就保持上一次已知的那批（首次是空表）。
            // 这里不升级成抛 —— 判定链宁可少几条规则也不该把整个探索页打死，
            // 而"少规则"的方向是**漏屏蔽**，所以在真机回归里要点这一条（见 4b 报告）。
            // 但不许静默：logcat 必须留痕，否则"库坏了"与"就是没规则"在屏上分不开。
            android.util.Log.w("ContentGuard", "loadRules 读取失败，沿用已知规则集", e)
        }
    }

    /**
     * 载入内置源级分级预设表（assets/source_content_warning.json，随 APK 打包）。
     * 加载失败不致命：退化为「全部 safe」，判定链仍有用户规则 + 显式标记兜底。
     */
    private fun loadSourcePresets() {
        try {
            val text = context.assets.open("source_content_warning.json").bufferedReader().use { it.readText() }
            val root = parseJsonObject(text)
            val sources = root["sources"]?.takeIf { it.isJsonObject }?.asJsonObject ?: return
            for (key in sources.keySet()) {
                val entry = sources[key]?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                sourcePresets[key] = entry.optTextValue("level", "safe")
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
            val id = ruleStore.add(type, pattern.trim(), isRegex, System.currentTimeMillis())
            loadRules()
            id
        } catch (_: Exception) {
            // 写不进去回报 -1，调用方（长按卡片"屏蔽本作"）据此如实提示失败；判据与改造前相同。
            -1L
        }
    }

    suspend fun deleteRule(id: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val deleted = ruleStore.delete(id)
            loadRules()
            deleted
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 画廊条目的黑名单判定，命中就**把那条规则交回去** —— 页面要说得出"是哪条规则挡的"，
     * 不能只让屏上凭空少几张（2026-09-25 真机就是这样：规则 `ai` 把 Danbooru 整天清空，
     * 而页尾仍写着「Danbooru 20」，那是一条假读数）。
     *
     * 不走 [isComicBlocked] 的原因见 [GalleryBlockMatch]：图站 tag 是下划线标识符，
     * 子串匹配会让 `ai` 命中 `long_hair`。漫画侧那一份判据一字未动。
     */
    fun findGalleryBlockedRule(author: String, tags: List<String>): GuardRule? =
        _rules.value.filter { it.isEnabled }.firstOrNull { GalleryBlockMatch.blocked(it, author, tags) }

    /**
     * 检查单本漫画是否命中「用户屏蔽规则」。
     * 注意：源级预设 / 显式标记判定不在本方法内 —— 它们只参与遮蔽判定链
     * （[coverMaskStateFor]），不应影响用户显式黑名单的语义。
     */
    fun isComicBlocked(title: String, author: String = "", tags: List<String> = emptyList(), comicId: String = "", description: String = ""): Boolean {
        com.venera.compose.StartupTrace.once("Guard: first isComicBlocked")
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
        com.venera.compose.StartupTrace.once("Guard: first filterComicModels")
        val activeRules = _rules.value.filter { it.isEnabled }
        val hideMode = _nsfwMaskMode.value == "HIDE"
        val blockAi = _blockAiComics.value
        if (activeRules.isEmpty() && !hideMode && !blockAi) return comics
        val dropped = ArrayList<Comic>(4)
        val kept = comics.filter { comic ->
            val userBlocked = isComicBlocked(
                title = comic.title,
                author = comic.subTitle,
                tags = comic.tags,
                comicId = comic.id,
                description = comic.description,
            )
            val nsfwHide = hideMode &&
                maskStateInternal(comic.sourceKey, comic.title, comic.subTitle, comic.tags, comic.id, comic.description)
            val aiHide = blockAi && isAiMarked(comic.title, comic.tags)
            if (userBlocked || nsfwHide || aiHide) dropped.add(comic)
            !(userBlocked || nsfwHide || aiHide)
        }
        // 逐源剔除量：这是"屏蔽到底有没有生效"的唯一客观读数。只记源键与条数，不落标题到 logcat。
        if (dropped.isNotEmpty()) {
            val bySource = dropped.groupingBy { it.sourceKey.ifBlank { "?" } }.eachCount()
            android.util.Log.i(
                "VeneraGuard",
                "剔除 ${dropped.size}/${comics.size} 条 mode=${_nsfwMaskMode.value} ai=$blockAi rules=${activeRules.size} $bySource"
            )
        }
        return kept
    }

    /**
     * 过滤探索页分区（逐分区过滤，空分区整块剔除，避免留下"孤儿标题"）。
     * 仅在 HIDE 模式或存在用户黑名单时执行实际过滤。
     */
    fun filterExploreParts(parts: List<ExplorePagePart>): List<ExplorePagePart> {
        val activeRules = _rules.value.filter { it.isEnabled }
        val hideMode = _nsfwMaskMode.value == "HIDE"
        if (activeRules.isEmpty() && !hideMode && !_blockAiComics.value) return parts
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
        // AI 屏蔽与 R18 的三档模式无关，且必须排在 "OFF" 早退**之前** ——
        // 否则用户把分级遮罩关掉时，AI 屏蔽会被一起关掉（一个开关管两件事 = 假开关）。
        if (_blockAiComics.value && isAiMarked(title, tags)) return "HIDDEN"
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
        if (_blockAiComics.value && isAiMarked(title, tags)) return "HIDDEN"
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
        com.venera.compose.StartupTrace.once("Guard: first coverMaskStateFor(Comic)")
        if (_blockAiComics.value && isAiMarked(comic.title, comic.tags)) return "HIDDEN"
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
                userRegexes.getOrPut(rule.pattern) {
                    Regex(rule.pattern, RegexOption.IGNORE_CASE)
                }.containsMatchIn(target)
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
                INSTANCE ?: com.venera.compose.StartupTrace.timed("Guard: ContentGuardManager() full ctor") {
                    ContentGuardManager(context.applicationContext)
                }.also { INSTANCE = it }
            }
        }
    }
}
