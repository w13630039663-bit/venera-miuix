package com.venera.compose.source

import android.content.Context
import android.util.LruCache
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.venera.compose.data.network.HostCircuitBreaker
import com.venera.compose.engine.explainSourceFailure
import com.venera.compose.engine.VeneraJsEngine
import com.venera.compose.source.baozi.BaoziMangaSource
import com.venera.compose.source.copymanga.CopyMangaSource
import com.venera.compose.source.js.ComicSourceParser
import com.venera.compose.source.js.JsComicSource
import com.venera.compose.source.mangadex.MangaDexSource
import com.venera.compose.source.model.ChapterPages
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ComicDetails
import com.venera.compose.source.model.ResolvedImageConfig
import com.venera.compose.source.model.ThumbnailPage
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.data.network.registrableDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 已安装漫画源的元数据（持久化单位是 **fileName**，不是 key）。
 *
 * ⚠️ 为什么必须用 fileName 而不是 key 作为身份标识：
 * 官方 index.json 共 33 条记录但只有 32 个唯一 key —— `copy_manga.js`(拷贝漫画) 与
 * `copy_manga_multi_accounts.js`(拷贝漫画M) **声明了同一个 key = "copy_manga"**。
 * 若以 key 作为落盘文件名，安装多账号版会把标准版直接覆盖掉；
 * 而两者在同一个 JS 上下文里也必然互相覆盖 `ComicSource.sources[key]`，故它们在运行期互斥。
 */
data class InstalledSourceMeta(
    val fileName: String,
    val key: String,
    val name: String,
    val version: String,
    val enabled: Boolean = true,
    val pinned: Boolean = false
)

/**
 * 漫画源核心管理器（单例）
 *
 * 职责：源注册、启用/禁用与排序、远程仓库同步、全网并发聚合搜索、连通性探测。
 *
 * 聚合搜索的设计要点（直接决定"能不能搜到东西"）：
 * 1. **有界并发**：33 源不会一次性全部打满，用 [SEARCH_CONCURRENCY] 信号量限流。
 * 2. **单源超时**：任一源超过 [SOURCE_TIMEOUT_MS] 立即以错误结果下发，绝不让慢源拖住整轮。
 * 3. **只搜启用的源**：用户在源管理里关掉的源不参与检索。
 * 4. **不再开机自动 Ping**：旧实现会在启动时对每个源各跑一次完整搜索，
 *    等于开机就往网络里打 33 个阻塞请求，直接拖垮用户随后的第一次真实搜索。
 */
class ComicSourceManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    /**
     * 「图片键 → 真实加载配置」的解析结果缓存。
     *
     * 官方 `network/images.dart` 命中磁盘缓存时**连 `onImageLoad` 都不调用**；
     * 我们无法预知磁盘缓存是否命中（图片 URL 由源临时签发），因此退一步缓存
     * **解析结果**：同一页在同一会话内被再次展示（翻回上一页、预加载与当前页
     * 撞车、双页模式重复渲染）时直接复用，不再跨 WebView 重跑一次源 JS。
     */
    private val imageConfigCache = LruCache<String, ResolvedImageConfig>(512)

    /** 解析中的请求（key → Deferred），用于合并同一张图的并发解析。 */
    private val imageConfigInflight = ConcurrentHashMap<String, Deferred<Result<ResolvedImageConfig>>>()

    private val sourceDir = File(context.filesDir, "comic_source").apply { mkdirs() }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 运行期注册表：JS 声明的 key -> 源实例 */
    private val registeredSources = linkedMapOf<String, ComicSource>()

    /** 原生兜底实现：当 JS 源检索失败时回退 */
    private val builtinSources = linkedMapOf<String, ComicSource>()

    private val _sourcesFlow = MutableStateFlow<List<ComicSource>>(emptyList())
    val sourcesFlow: StateFlow<List<ComicSource>> = _sourcesFlow.asStateFlow()

    private val _installedMeta = MutableStateFlow<List<InstalledSourceMeta>>(emptyList())
    val installedMeta: StateFlow<List<InstalledSourceMeta>> = _installedMeta.asStateFlow()

    private val _activeSourceKey = MutableStateFlow("copy_manga")
    val activeSourceKey: StateFlow<String> = _activeSourceKey.asStateFlow()

    private val _latencyMapFlow = MutableStateFlow<Map<String, Long>>(emptyMap())
    val latencyMapFlow: StateFlow<Map<String, Long>> = _latencyMapFlow.asStateFlow()

    val jsEngine: VeneraJsEngine by lazy {
        VeneraJsEngine(context).apply {
            init()
            loadStandardLib()
        }
    }

    val parser: ComicSourceParser by lazy { ComicSourceParser(jsEngine) }

    fun getDeletedBuiltinKeys(): Set<String> {
        return prefs.getStringSet(KEY_DELETED_BUILTINS, emptySet()) ?: emptySet()
    }

    private fun markBuiltinDeleted(key: String) {
        val set = getDeletedBuiltinKeys().toMutableSet()
        set.add(key)
        prefs.edit { putStringSet(KEY_DELETED_BUILTINS, set) }
    }

    private fun unmarkBuiltinDeleted(key: String) {
        val set = getDeletedBuiltinKeys().toMutableSet()
        if (set.remove(key)) {
            prefs.edit { putStringSet(KEY_DELETED_BUILTINS, set) }
        }
    }

    fun getSourceOrFallback(sourceKey: String): ComicSource? {
        val reg = registeredSources[sourceKey]
        if (reg != null) return reg
        if (sourceKey in getDeletedBuiltinKeys()) return null
        return builtinSources[sourceKey]
    }

    init {
        val mangaDex = MangaDexSource(context)
        val copyManga = CopyMangaSource(context)
        val baozi = BaoziMangaSource(context)

        builtinSources[mangaDex.key] = mangaDex
        builtinSources[copyManga.key] = copyManga
        builtinSources[baozi.key] = baozi

        val deletedBuiltins = getDeletedBuiltinKeys()
        for ((k, v) in builtinSources) {
            if (k !in deletedBuiltins) {
                registeredSources[k] = v
            }
        }

        _installedMeta.value = readMeta()
        _sourcesFlow.value = registeredSources.values.toList()

        scope.launch {
            loadInstalledJsSources()
        }
    }

    /* ------------------------------------------------------------------ *
     * 元数据持久化
     * ------------------------------------------------------------------ */

    private fun readMeta(): List<InstalledSourceMeta> {
        val raw = prefs.getString(KEY_INSTALLED, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<InstalledSourceMeta>>() {}.type
            gson.fromJson<List<InstalledSourceMeta>>(raw, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeMeta(list: List<InstalledSourceMeta>) {
        prefs.edit { putString(KEY_INSTALLED, gson.toJson(list)) }
        _installedMeta.value = list
    }

    private fun metaOf(fileName: String): InstalledSourceMeta? =
        _installedMeta.value.firstOrNull { it.fileName == fileName }

    /** 已禁用源的 key 集合；不在该集合内的 key（含原生源）视为启用 */
    private fun disabledKeys(): Set<String> =
        _installedMeta.value.filter { !it.enabled }.map { it.key }.toSet()

    fun isSourceEnabled(key: String): Boolean = key !in disabledKeys()

    /* ------------------------------------------------------------------ *
     * 加载 / 安装 / 卸载
     * ------------------------------------------------------------------ */

    /** 加载全部已安装的 JS 规则源（按元数据顺序） */
    fun loadInstalledJsSources() {
        val meta = readMeta()
        val valid = mutableListOf<InstalledSourceMeta>()
        var changed = false

        // 1. 清掉文件已被删除的元数据
        for (m in meta) {
            if (File(sourceDir, m.fileName).exists()) valid.add(m) else changed = true
        }

        // 2. 首次启动：把 assets 内置源写入本地（仅包子/拷贝/MangaDex 作为开箱可用）。
        //    用一次性标记 [KEY_BOOTSTRAPPED] 保证「用户主动卸载全部源」后重启不会被重新塞回——
        //    空列表既可能是首次安装，也可能是用户的选择，仅凭 isEmpty() 无法区分。
        if (!prefs.getBoolean(KEY_BOOTSTRAPPED, false)) {
            if (valid.isEmpty()) {
                val bootstrapped = bootstrapBundledSources()
                if (bootstrapped.isNotEmpty()) {
                    valid.addAll(bootstrapped)
                    changed = true
                }
            }
            // 无论本次是否真的写入，都记为已 bootstrap（老版本升级时 valid 非空也走这里）
            prefs.edit { putBoolean(KEY_BOOTSTRAPPED, true) }
        }

        // 3. 顺序解析并注册
        for (m in valid) {
            try {
                val source = parser.parseFile(File(sourceDir, m.fileName))
                registeredSources[source.key] = source
                // 元数据里的展示名优先（部分源的 JS name 不可靠，例如 hitomi 声明为 galleriesindex）
                val displayName = m.name.ifBlank { source.name }
                if (m.key != source.key || m.version != source.version || m.name != displayName) {
                    val idx = valid.indexOf(m)
                    valid[idx] = m.copy(
                        key = source.key,
                        version = source.version,
                        name = displayName
                    )
                    changed = true
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to parse js source: ${m.fileName}", e)
            }
        }

        // 4. 同步元数据 ↔ 运行期注册表
        syncRegistry(valid)

        if (changed) writeMeta(valid) else _installedMeta.value = valid
        _sourcesFlow.value = registeredSources.values.toList()
    }

    /** 首次启动时从 assets 解包少量开箱即用源 */
    private fun bootstrapBundledSources(): List<InstalledSourceMeta> {
        val result = mutableListOf<InstalledSourceMeta>()
        val defaultFiles = listOf("baozi.js", "copy_manga.js", "manga_dex.js")
        val repoIndex = readBundledRepoIndex().associateBy { it.fileName }
        val deleted = getDeletedBuiltinKeys()
        for (name in defaultFiles) {
            try {
                val content = context.assets.open("sources/$name").bufferedReader().use { it.readText() }
                val source = parser.parse(content)
                if (source.key in deleted) continue
                File(sourceDir, name).writeText(content)
                val entry = repoIndex[name]
                result.add(
                    InstalledSourceMeta(
                        fileName = name,
                        key = source.key,
                        name = entry?.name ?: source.name,
                        version = source.version
                    )
                )
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to bootstrap bundled source: $name", e)
            }
        }
        return result
    }

    /** 同步运行期注册表：注册已启用源，移除已不存在的 JS 源与已删除的内置源 */
    private fun syncRegistry(meta: List<InstalledSourceMeta>) {
        val jsKeys = meta.map { it.key }.toSet()
        val deleted = getDeletedBuiltinKeys()
        val iterator = registeredSources.iterator()
        while (iterator.hasNext()) {
            val (key, source) = iterator.next()
            if (key in deleted) {
                iterator.remove()
            } else if (source is JsComicSource && key !in jsKeys) {
                iterator.remove()
            }
        }
    }

    /**
     * 安装/更新一个 JS 源。
     *
     * @param jsContent 脚本内容
     * @param fileName  仓库中的文件标识（**身份标识**，见 [InstalledSourceMeta]）
     * @param displayName 仓库展示名（优先于 JS 内声明的 name）
     */
    fun installJsSource(
        jsContent: String,
        fileName: String? = null,
        displayName: String? = null
    ): Result<ComicSource> {
        return try {
            val source = parser.parse(jsContent)
            // 重新安装源时取消删除标记
            unmarkBuiltinDeleted(source.key)

            val resolvedFileName = fileName?.takeIf { it.isNotBlank() } ?: "${source.key}.js"

            // 运行期互斥：同一 key 只能存在一个源（copy_manga 两个变体即属此类）
            val meta = readMeta().toMutableList()
            val conflicted = meta.filter { it.key == source.key && it.fileName != resolvedFileName }
            for (c in conflicted) {
                meta.remove(c)
                File(sourceDir, c.fileName).delete()
                android.util.Log.i(TAG, "Removed mutually exclusive source: ${c.fileName}")
            }

            File(sourceDir, resolvedFileName).writeText(jsContent)

            val entry = InstalledSourceMeta(
                fileName = resolvedFileName,
                key = source.key,
                name = displayName?.takeIf { it.isNotBlank() } ?: source.name,
                version = source.version,
                enabled = metaOf(resolvedFileName)?.enabled ?: true,
                pinned = metaOf(resolvedFileName)?.pinned ?: false
            )
            val existingIdx = meta.indexOfFirst { it.fileName == resolvedFileName }
            if (existingIdx >= 0) meta[existingIdx] = entry else meta.add(entry)

            writeMeta(sortMeta(meta))

            registeredSources[source.key] = source
            _sourcesFlow.value = registeredSources.values.toList()
            Result.success(source)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 按「置顶优先 + 用户自定义顺序」整理 */
    private fun sortMeta(list: List<InstalledSourceMeta>): List<InstalledSourceMeta> =
        list.sortedByDescending { it.pinned }

    /**
     * 彻底删除/卸载漫画源（无论是 JS 扩展还是原生内置源如包子/拷贝/MangaDex）。
     */
    fun deleteSource(key: String, fileName: String? = null): Boolean {
        val meta = readMeta().toMutableList()
        val targetFileName = fileName ?: meta.firstOrNull { it.key == key }?.fileName

        if (targetFileName != null) {
            meta.removeAll { it.fileName == targetFileName || it.key == key }
            writeMeta(meta)
            File(sourceDir, targetFileName).delete()
            File(sourceDir, "$targetFileName.data").delete()
            _availableUpdates.value = _availableUpdates.value - targetFileName
        } else {
            meta.removeAll { it.key == key }
            writeMeta(meta)
        }

        File(sourceDir, "$key.js").delete()
        File(sourceDir, "$key.data").delete()
        jsEngine.dataStore.evict(key)

        // 记录为用户主动删除，防止 builtinSources 回退或重启复活
        markBuiltinDeleted(key)

        // 彻底从运行期注册表中移除
        registeredSources.remove(key)

        if (_activeSourceKey.value == key) {
            _activeSourceKey.value = registeredSources.keys.firstOrNull() ?: ""
        }

        _sourcesFlow.value = registeredSources.values.toList()
        return true
    }

    /** 卸载单个 JS 源（兼容原有调用） */
    fun uninstallSource(fileName: String): Boolean {
        val meta = readMeta()
        val target = meta.firstOrNull { it.fileName == fileName }
        val key = target?.key ?: fileName.removeSuffix(".js")
        return deleteSource(key, fileName)
    }

    /** 兼容旧调用：按 key 卸载/删除 */
    fun deleteJsSource(key: String): Boolean {
        return deleteSource(key)
    }

    fun setSourceEnabled(fileName: String, enabled: Boolean) {
        val meta = readMeta().toMutableList()
        val idx = meta.indexOfFirst { it.fileName == fileName }
        if (idx < 0) return
        meta[idx] = meta[idx].copy(enabled = enabled)
        writeMeta(meta)
    }

    fun setSourcePinned(fileName: String, pinned: Boolean) {
        val meta = readMeta().toMutableList()
        val idx = meta.indexOfFirst { it.fileName == fileName }
        if (idx < 0) return
        meta[idx] = meta[idx].copy(pinned = pinned)
        writeMeta(sortMeta(meta))
    }

    /** 拖拽排序 */
    fun moveSource(fromIndex: Int, toIndex: Int) {
        val meta = readMeta().toMutableList()
        if (fromIndex !in meta.indices || toIndex !in meta.indices || fromIndex == toIndex) return
        val item = meta.removeAt(fromIndex)
        meta.add(toIndex, item)
        writeMeta(meta)
    }

    fun registerSource(source: ComicSource) {
        registeredSources[source.key] = source
        _sourcesFlow.value = registeredSources.values.toList()
    }

    fun setActiveSource(key: String) {
        _activeSourceKey.value = key
    }

    fun getSource(key: String): ComicSource? = registeredSources[key]

    fun getSourceByFileName(fileName: String): ComicSource? {
        val key = _installedMeta.value.firstOrNull { it.fileName == fileName }?.key ?: return null
        return registeredSources[key]
    }

    /** 当前参与检索的源（已剔除禁用项） */
    fun searchTargets(): List<ComicSource> {
        val disabled = disabledKeys()
        return registeredSources.values.filter { it.key != "all" && it.key !in disabled }
    }

    /* ------------------------------------------------------------------ *
     * 仓库索引
     * ------------------------------------------------------------------ */

    private val repoIndexCache = MutableStateFlow<List<RepoIndexEntry>>(emptyList())
    val repoIndex: StateFlow<List<RepoIndexEntry>> = repoIndexCache.asStateFlow()

    private val _repoLoading = MutableStateFlow(false)
    val repoLoading: StateFlow<Boolean> = _repoLoading.asStateFlow()

    /**
     * 仓库索引地址。
     *
     * 官方把它存在 `appdata.settings['comicSourceListUrl']`，并在仓库清单里
     * 提供**可编辑输入框 + Refresh**；本实现同样允许用户改，并持久化到 SharedPreferences。
     */
    private val _repoUrl = MutableStateFlow(
        prefs.getString(KEY_REPO_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_REPO_URL
    )
    val repoUrl: StateFlow<String> = _repoUrl.asStateFlow()

    fun setRepoUrl(url: String) {
        val normalized = url.trim().ifBlank { DEFAULT_REPO_URL }
        _repoUrl.value = normalized
        prefs.edit { putString(KEY_REPO_URL, normalized) }
    }

    /** 恢复官方默认仓库地址 */
    fun resetRepoUrl() = setRepoUrl(DEFAULT_REPO_URL)

    /** 内置仓库索引（离线兜底，与官方 index.json 逐字一致） */
    fun readBundledRepoIndex(): List<RepoIndexEntry> {
        return try {
            val raw = context.assets.open("sources/index.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<RepoIndexEntry>>() {}.type
            gson.fromJson<List<RepoIndexEntry>>(raw, type) ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to read bundled repo index", e)
            emptyList()
        }
    }

    /** 拉取远程仓库索引，失败时回退内置索引 */
    suspend fun fetchRepoIndex(repoUrl: String = DEFAULT_REPO_URL): List<RepoIndexEntry> =
        withContext(Dispatchers.IO) {
            _repoLoading.value = true
            val result = try {
                val req = Request.Builder().url(repoUrl).build()
                val resp = VeneraNetworkClient.getInstance(context).okHttpClient.newCall(req).execute()
                val body = resp.body?.string()
                if (resp.isSuccessful && !body.isNullOrBlank()) {
                    val type = object : TypeToken<List<RepoIndexEntry>>() {}.type
                    gson.fromJson<List<RepoIndexEntry>>(body, type) ?: emptyList()
                } else {
                    readBundledRepoIndex()
                }
            } catch (e: Exception) {
                readBundledRepoIndex()
            }
            repoIndexCache.value = result
            _repoLoading.value = false
            result
        }

    /** 下载单个源的脚本：优先 CDN，失败回退内置 assets */
    private fun fetchSourceScript(
        entry: RepoIndexEntry,
        repoUrl: String = DEFAULT_REPO_URL
    ): String? {
        val fileUrl = resolveSourceUrl(entry, repoUrl)
        try {
            val req = Request.Builder().url(fileUrl).build()
            val resp = VeneraNetworkClient.getInstance(context).okHttpClient.newCall(req).execute()
            val body = resp.body?.string()
            if (resp.isSuccessful && !body.isNullOrBlank()) return body
        } catch (e: Exception) {
            android.util.Log.w(TAG, "CDN download failed for ${entry.fileName}, falling back to assets", e)
        }
        return try {
            context.assets.open("sources/${entry.fileName}").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    /** 安装指定仓库条目 */
    fun installFromRepo(
        entry: RepoIndexEntry,
        repoUrl: String = DEFAULT_REPO_URL,
        onDone: (Result<ComicSource>) -> Unit = {}
    ) {
        scope.launch {
            val content = fetchSourceScript(entry, repoUrl)
            val result = if (content.isNullOrBlank()) {
                Result.failure(Exception("无法获取 ${entry.fileName} 的脚本内容"))
            } else {
                installJsSource(content, entry.fileName, entry.name)
            }
            withContext(Dispatchers.Main) { onDone(result) }
        }
    }

    /** 一键安装全部（已装且版本一致的跳过） */
    fun installAll(
        entries: List<RepoIndexEntry>,
        repoUrl: String = DEFAULT_REPO_URL,
        onProgress: (done: Int, total: Int, current: String) -> Unit = { _, _, _ -> },
        onDone: (success: Int, failed: Int, skipped: Int) -> Unit = { _, _, _ -> }
    ) {
        scope.launch {
            val installed = readMeta().associateBy { it.fileName }
            var success = 0
            var failed = 0
            var skipped = 0
            entries.forEachIndexed { index, entry ->
                val existing = installed[entry.fileName]
                if (existing != null && compareVersion(existing.version, entry.version) >= 0) {
                    skipped++
                } else {
                    val content = fetchSourceScript(entry, repoUrl)
                    if (content.isNullOrBlank()) {
                        failed++
                    } else {
                        if (installJsSource(content, entry.fileName, entry.name).isSuccess) success++ else failed++
                    }
                }
                withContext(Dispatchers.Main) { onProgress(index + 1, entries.size, entry.name) }
            }
            loadInstalledJsSources()
            withContext(Dispatchers.Main) { onDone(success, failed, skipped) }
        }
    }

    /**
     * 解析某个仓库条目对应的脚本下载地址（对齐官方 `_ComicSourceList` 的 Add 逻辑）。
     *
     * 1. 条目自带合法 `url` → 直接使用（官方 index.json 目前 33 条都未用到该字段，
     *    但上游代码保留了这条分支，第三方仓库会用到）
     * 2. 否则视为「与 index.json 同目录」，把 fileName 拼到仓库地址所在目录之后
     */
    fun resolveSourceUrl(entry: RepoIndexEntry, repoUrl: String = DEFAULT_REPO_URL): String {
        val explicit = entry.url?.trim()
        if (!explicit.isNullOrBlank() && isHttpUrl(explicit)) return explicit
        return concatRepoFileName(repoUrl, entry.fileName)
    }

    /**
     * 把 fileName 拼到仓库索引地址所在目录之后。
     *
     * 官方在此处有一处容错：若去掉协议后的地址**不含 `/`**（即仓库地址就是裸域名，
     * 例如 `https://example.com`），则退化为 `$repoUrl/$fileName`，
     * 而不是错误地做 `substringBeforeLast('/')` 得到 `https:/fileName`。
     */
    private fun concatRepoFileName(repoUrl: String, fileName: String): String {
        val withoutScheme = repoUrl.replaceFirst("https://", "").replaceFirst("http://", "")
        return if (withoutScheme.contains("/")) {
            repoUrl.substring(0, repoUrl.lastIndexOf('/') + 1) + fileName
        } else {
            "$repoUrl/$fileName"
        }
    }

    private fun isHttpUrl(s: String): Boolean =
        s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)

    /* ------------------------------------------------------------------ *
     * 单源「编辑 / 更新 / 删除」三件套（对齐官方 comic_source_page.dart）
     * ------------------------------------------------------------------ */

    /**
     * 可更新源：fileName -> 远端版本号（对应官方 `ComicSourceManager.availableUpdates`）。
     *
     * ⚠️ 与官方的**有意差异**：官方以 sourceKey 作键，本实现以 fileName 作键。
     * 官方 index.json 有 33 条记录却只有 32 个唯一 key ——
     * `copy_manga.js` 与 `copy_manga_multi_accounts.js` 声明同一个 key，
     * 用 key 作键会让两个文件永远匹配到同一份远端版本，
     * 于是其中一个会被误判为「有新版本」并反复提示。
     */
    private val _availableUpdates = MutableStateFlow<Map<String, String>>(emptyMap())
    val availableUpdates: StateFlow<Map<String, String>> = _availableUpdates.asStateFlow()

    fun clearAvailableUpdate(fileName: String) {
        _availableUpdates.value = _availableUpdates.value - fileName
    }

    /** 已安装源的 .js 绝对路径 */
    fun sourceFilePath(fileName: String): String = File(sourceDir, fileName).absolutePath

    /** 读取源的 .js 原文（编辑用）；文件不存在返回 null */
    fun readSourceText(fileName: String): String? = try {
        val f = File(sourceDir, fileName)
        if (f.exists()) f.readText() else null
    } catch (e: Exception) {
        android.util.Log.e(TAG, "Failed to read source text: $fileName", e)
        null
    }

    /**
     * 保存编辑后的 .js 原文并重新注册该源。
     *
     * 与官方 `_EditFilePage.dispose()`（写回 → reload）等价，但**多一道校验**：
     * 先解析、解析通过才落盘。否则用户一个笔误就能让源在下次启动时直接消失。
     */
    fun saveSourceText(fileName: String, content: String): Result<ComicSource> {
        return try {
            parser.parse(content) // 语法/元数据校验，失败则抛异常且不落盘
            File(sourceDir, fileName).writeText(content)
            val result = installJsSource(content, fileName, metaOf(fileName)?.name)
            if (result.isSuccess) {
                _sourcesFlow.value = registeredSources.values.toList()
                clearAvailableUpdate(fileName)
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 单源更新：按源自己声明的 `url` 重新下载脚本（对齐官方 `update(source)`）。
     *
     * 三个关键点：
     * 1. 请求头带 `cache-time: no` —— 官方用同一招绕开 CDN 缓存；不带的话
     *    jsDelivr 这类 CDN 会一直回旧版本，表现为「点了更新却毫无变化」。
     * 2. 源未声明 `url` 时回退到仓库索引里的同 fileName 条目。
     * 3. 解析成功后才覆盖本地文件。官方是「先 remove 再写」，中途失败会让源凭空消失；
     *    这里更保守，失败时原文件与已注册实例都保持不变。
     */
    suspend fun updateSource(
        fileName: String,
        repoUrl: String = DEFAULT_REPO_URL
    ): Result<ComicSource> = withContext(Dispatchers.IO) {
        val meta = _installedMeta.value.firstOrNull { it.fileName == fileName }
            ?: return@withContext Result.failure(Exception("源不存在: $fileName"))

        val declared = registeredSources[meta.key]?.url?.trim().orEmpty()
        val url = if (declared.isNotBlank() && isHttpUrl(declared)) {
            declared
        } else {
            repoIndexCache.value.firstOrNull { it.fileName == fileName }
                ?.let { resolveSourceUrl(it, repoUrl) }
                ?: concatRepoFileName(repoUrl, fileName)
        }

        try {
            val req = Request.Builder()
                .url(url)
                .header("cache-time", "no")
                .build()
            val resp = VeneraNetworkClient.getInstance(context).okHttpClient.newCall(req).execute()
            val body = resp.body?.string()
            if (!resp.isSuccessful || body.isNullOrBlank()) {
                return@withContext Result.failure(Exception("下载失败：HTTP ${resp.code}"))
            }
            val result = installJsSource(body, fileName, meta.name)
            if (result.isSuccess) {
                clearAvailableUpdate(fileName)
                _sourcesFlow.value = registeredSources.values.toList()
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 检查更新（对齐官方 `checkComicSourceUpdate`）。
     *
     * @return 可更新的源数量；`-1` 表示网络失败（与官方同语义，UI 据此提示"网络错误"）
     */
    suspend fun checkUpdates(repoUrl: String = DEFAULT_REPO_URL): Int = withContext(Dispatchers.IO) {
        val entries = try {
            val req = Request.Builder().url(repoUrl).build()
            val resp = VeneraNetworkClient.getInstance(context).okHttpClient.newCall(req).execute()
            val body = resp.body?.string()
            if (!resp.isSuccessful || body.isNullOrBlank()) {
                return@withContext -1
            }
            val type = object : TypeToken<List<RepoIndexEntry>>() {}.type
            gson.fromJson<List<RepoIndexEntry>>(body, type) ?: return@withContext -1
        } catch (e: Exception) {
            return@withContext -1
        }
        if (entries.isEmpty()) return@withContext -1

        repoIndexCache.value = entries
        val byFileName = entries.associateBy { it.fileName }
        val updates = mutableMapOf<String, String>()
        for (m in _installedMeta.value) {
            val remote = byFileName[m.fileName] ?: continue
            val current = registeredSources[m.key]?.version ?: m.version
            if (compareVersion(remote.version, current) > 0) {
                updates[m.fileName] = remote.version
            }
        }
        _availableUpdates.value = updates
        updates.size
    }

    /* ------------------------------------------------------------------ *
     * 检索
     * ------------------------------------------------------------------ */

    data class SourceSearchResult(
        val sourceKey: String,
        val sourceName: String,
        val comics: List<Comic> = emptyList(),
        val error: String? = null,
        val isLoading: Boolean = false
    )

    /**
     * 全网聚合搜索：逐源并发下发，任意源完成立即 emit。
     * 单个源受 [SOURCE_TIMEOUT_MS] 约束，慢源不会拖住整轮。
     */
    fun searchAggregatedStream(keyword: String, page: Int = 1): Flow<SourceSearchResult> = channelFlow {
        val targets = searchTargets()
        val semaphore = Semaphore(SEARCH_CONCURRENCY)

        for (source in targets) {
            launch {
                semaphore.withPermit {
                    val event = try {
                        val res = withTimeout(SOURCE_TIMEOUT_MS) {
                            runCatching { source.search(keyword, page) }
                                .getOrElse { Result.failure(it) }
                        }
                        var finalRes = res
                        // JS 源失败或为空时，回退到原生实现
                        if ((finalRes.isFailure || finalRes.getOrDefault(emptyList()).isEmpty())) {
                            val fallback = builtinSources[source.key]
                            if (fallback != null && fallback !== source) {
                                val nativeRes = runCatching { fallback.search(keyword, page) }.getOrNull()
                                if (nativeRes != null && nativeRes.isSuccess && nativeRes.getOrDefault(emptyList()).isNotEmpty()) {
                                    finalRes = nativeRes
                                }
                            }
                        }
                        if (finalRes.isSuccess) {
                            SourceSearchResult(source.key, source.name, finalRes.getOrDefault(emptyList()))
                        } else {
                            SourceSearchResult(
                                source.key, source.name, emptyList(),
                                finalRes.exceptionOrNull()?.message ?: "检索无结果"
                            )
                        }
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        SourceSearchResult(source.key, source.name, emptyList(), "检索超时（${SOURCE_TIMEOUT_MS / 1000}s）")
                    } catch (e: Exception) {
                        SourceSearchResult(source.key, source.name, emptyList(), e.message ?: "检索异常")
                    }
                    send(event)
                }
            }
        }
    }

    suspend fun search(sourceKey: String, keyword: String, page: Int = 1, options: List<String>? = null): Result<List<Comic>> =
        withContext(Dispatchers.IO) {
            if (sourceKey == "all") {
                val results = searchTargets().map { source ->
                    async {
                        source.search(keyword, page, options).getOrDefault(emptyList())
                    }
                }.awaitAll().flatten()
                Result.success(results)
            } else {
                val source = getSourceOrFallback(sourceKey)
                ?: return@withContext Result.failure(Exception("未找到漫画源: $sourceKey"))
                var res = source.search(keyword, page, options)
                if (res.isFailure || res.getOrDefault(emptyList()).isEmpty()) {
                    val fallback = getSourceOrFallback(sourceKey)
                    if (fallback != null && fallback !== source) {
                        val nativeRes = runCatching { fallback.search(keyword, page) }.getOrNull()
                        if (nativeRes != null && nativeRes.isSuccess && nativeRes.getOrDefault(emptyList()).isNotEmpty()) {
                            res = nativeRes
                        }
                    }
                }
                res
            }
        }

    suspend fun getComicDetails(sourceKey: String, comicId: String): Result<ComicDetails> =
        withContext(Dispatchers.IO) {
            val source = getSourceOrFallback(sourceKey) ?: return@withContext Result.failure(
                Exception("未找到漫画源: $sourceKey")
            )
            var res = source.getComicDetails(comicId)
            if (res.isFailure) {
                val fallback = getSourceOrFallback(sourceKey)
                if (fallback != null && fallback !== source) {
                    val nativeRes = runCatching { fallback.getComicDetails(comicId) }.getOrNull()
                    if (nativeRes != null && nativeRes.isSuccess) res = nativeRes
                }
            }
            res
        }

    suspend fun getChapterPages(sourceKey: String, comicId: String, chapterId: String): Result<ChapterPages> =
        withContext(Dispatchers.IO) {
            val source = getSourceOrFallback(sourceKey) ?: return@withContext Result.failure(
                Exception("未找到漫画源: $sourceKey")
            )
            var res = source.getChapterPages(comicId, chapterId)
            if (res.isFailure) {
                val fallback = getSourceOrFallback(sourceKey)
                if (fallback != null && fallback !== source) {
                    val nativeRes = runCatching { fallback.getChapterPages(comicId, chapterId) }.getOrNull()
                    if (nativeRes != null && nativeRes.isSuccess) res = nativeRes
                }
            }
            res
        }

    /**
     * 解析「动态页」的真实图片地址（仅 JS 源支持）。
     *
     * 对齐官方 `ComicSource.getImageLoadingConfig`：当源的 `ChapterPages.useOnImageLoad`
     * 为 true 时，阅读器须在展示每个图片键前调本方法换取真实 `{url, headers, nl}`。
     * [nl] 为 EH 换源参数 —— 下载失败时回传上次返回的 nl 再解析一次（官方 onLoadFailed 语义）。
     *
     * **缓存与去重**（对齐官方 `network/images.dart` 的 `_loadingImages` 表）：
     * 解析一次要跨 WebView 调源 JS 并由源发起网络请求，同一张图被阅读器与前瞻
     * 预加载同时请求时若各解析一次，既是数倍延迟也可能触发站方限流。
     * 因此按 `sourceKey|cid|eid|imageKey|nl` 缓存解析结果并合并并发请求；
     * 失败结果**不缓存**，以便换源重试能真正重新解析。
     *
     * @param forceRefresh 绕过缓存强制重新解析（图片 URL 过期 / 换源重试时用）
     */
    suspend fun resolveImageLoadingConfig(
        sourceKey: String,
        comicId: String,
        epId: String,
        imageKey: String,
        nl: String? = null,
        forceRefresh: Boolean = false
    ): Result<ResolvedImageConfig> = withContext(Dispatchers.IO) {
        val cacheKey = "$sourceKey|$comicId|$epId|$imageKey|${nl.orEmpty()}"
        if (!forceRefresh) {
            imageConfigCache.get(cacheKey)?.let { return@withContext Result.success(it) }
        }

        // 并发去重：同一 key 已在解析中就直接等它的结果，不重复起 JS 调用
        val deferred = synchronized(imageConfigInflight) {
            val running = imageConfigInflight[cacheKey]
            if (running != null && !forceRefresh) {
                running
            } else {
                scope.async { doResolveImageLoadingConfig(sourceKey, comicId, epId, imageKey, nl) }
                    .also { imageConfigInflight[cacheKey] = it }
            }
        }

        val res = deferred.await()
        synchronized(imageConfigInflight) { imageConfigInflight.remove(cacheKey, deferred) }
        res.onSuccess { imageConfigCache.put(cacheKey, it) }   // 失败不缓存，允许重试
        res
    }

    private suspend fun doResolveImageLoadingConfig(
        sourceKey: String,
        comicId: String,
        epId: String,
        imageKey: String,
        nl: String?
    ): Result<ResolvedImageConfig> {
        val source = getSourceOrFallback(sourceKey)
            ?: return Result.failure(Exception("未找到漫画源: $sourceKey"))
        if (source !is JsComicSource) {
            return Result.failure(Exception("该源不支持动态图片解析"))
        }
        return runCatching { source.resolveImageLoadingConfig(comicId, epId, imageKey, nl) }
            .getOrElse { Result.failure(it) }
    }

    /**
     * 拉取源的一页官方预览缩略图（对齐官方 `parser.dart:_parseThumbnailLoader`）。
     *
     * 仅 JS 源且源声明了 `comic.loadThumbnails` 时可用；EH 由此获取 gallery 页面里
     * 的官方预览小图（分页），`next` 为 null 表示末页。
     */
    suspend fun loadThumbnails(
        sourceKey: String,
        comicId: String,
        next: String? = null
    ): Result<ThumbnailPage> = withContext(Dispatchers.IO) {
        val source = getSourceOrFallback(sourceKey)
            ?: return@withContext Result.failure(Exception("未找到漫画源: $sourceKey"))
        if (source is JsComicSource) {
            source.loadThumbnails(comicId, next)
        } else {
            Result.failure(Exception("该源不支持预览图接口"))
        }
    }

    suspend fun getExploreComics(sourceKey: String, page: Int = 1): Result<List<Comic>> =
        withContext(Dispatchers.IO) {
            val source = getSourceOrFallback(sourceKey)
            ?: return@withContext Result.failure(Exception("未找到漫画源: $sourceKey"))
            source.getExploreComics(page)
        }

    /**
     * 获取所有已启用源的探索页面定义
     */
    suspend fun getAllExplorePages(): List<com.venera.compose.source.model.ExplorePageData> = withContext(Dispatchers.IO) {
        val pages = mutableListOf<com.venera.compose.source.model.ExplorePageData>()
        val sources = registeredSources.values.filter { isSourceEnabled(it.key) }
        for (source in sources) {
            val res = source.getExplorePages()
            pages.addAll(res.getOrDefault(emptyList()))
        }
        pages
    }

    suspend fun loadExplorePage(
        sourceKey: String,
        pageIndex: Int,
        page: Int = 1
    ): Result<List<com.venera.compose.source.model.ExplorePagePart>> = withContext(Dispatchers.IO) {
        val source = getSourceOrFallback(sourceKey)
            ?: return@withContext Result.failure(Exception("未找到漫画源: $sourceKey"))
        source.loadExplorePage(pageIndex, page)
    }

    /**
     * 获取所有已启用源的分类页面元数据
     */
    suspend fun getAllCategories(): List<com.venera.compose.source.model.CategoryData> = withContext(Dispatchers.IO) {
        val categories = mutableListOf<com.venera.compose.source.model.CategoryData>()
        val sources = registeredSources.values.filter { isSourceEnabled(it.key) }
        for (source in sources) {
            val res = source.getCategoryData()
            val data = res.getOrNull()
            if (data != null && (data.parts.isNotEmpty() || data.enableRankingPage)) {
                categories.add(data)
            }
        }
        categories
    }

    suspend fun getCategoryData(sourceKey: String): Result<com.venera.compose.source.model.CategoryData?> = withContext(Dispatchers.IO) {
        val source = getSourceOrFallback(sourceKey)
            ?: return@withContext Result.failure(Exception("未找到漫画源: $sourceKey"))
        source.getCategoryData()
    }

    suspend fun getCategoryComicsOptions(
        sourceKey: String,
        category: String,
        param: String?
    ): Result<List<com.venera.compose.source.model.CategoryComicsOption>> = withContext(Dispatchers.IO) {
        val source = getSourceOrFallback(sourceKey)
            ?: return@withContext Result.failure(Exception("未找到漫画源: $sourceKey"))
        source.getCategoryComicsOptions(category, param)
    }

    suspend fun loadCategoryComics(
        sourceKey: String,
        category: String,
        param: String?,
        options: List<String>,
        page: Int = 1
    ): Result<com.venera.compose.source.model.CategoryComicsResult> = withContext(Dispatchers.IO) {
        val source = getSourceOrFallback(sourceKey)
            ?: return@withContext Result.failure(Exception("未找到漫画源: $sourceKey"))
        source.loadCategoryComics(category, param, options, page)
    }

    suspend fun loadCategoryRanking(
        sourceKey: String,
        option: String,
        page: Int = 1
    ): Result<List<Comic>> = withContext(Dispatchers.IO) {
        val source = getSourceOrFallback(sourceKey)
            ?: return@withContext Result.failure(Exception("未找到漫画源: $sourceKey"))
        source.loadCategoryRanking(option, page)
    }

    /* ------------------------------------------------------------------ *
     * 连通性 / 可用性
     * ------------------------------------------------------------------ */

    /**
     * 对单个源做一次真实可用性测试。
     * 返回 (延迟毫秒, 错误信息)；成功时延迟 >= 0，失败为 -1。
     */
    suspend fun testSource(key: String): Pair<Long, String?> = withContext(Dispatchers.IO) {
        val source = registeredSources[key] ?: return@withContext -1L to "源未安装"
        val start = System.currentTimeMillis()
        try {
            val res = withTimeout(SOURCE_TIMEOUT_MS) { source.search("测试", 1) }
            if (res.isSuccess) {
                (System.currentTimeMillis() - start) to null
            } else {
                -1L to explainSourceFailure(res.exceptionOrNull()?.message ?: "无响应")
            }
        } catch (e: Exception) {
            -1L to explainSourceFailure(e.message ?: "测试异常")
        }
    }

    /** 手动刷新全部启用源的延迟（不再由启动流程自动触发） */
    fun refreshPings() {
        scope.launch {
            val resultMap = mutableMapOf<String, Long>()
            val targets = searchTargets()
            val semaphore = Semaphore(SEARCH_CONCURRENCY)
            val deferreds = targets.map { source ->
                async {
                    semaphore.withPermit {
                        val latency = try {
                            withTimeout(SOURCE_TIMEOUT_MS) { source.ping() }
                        } catch (e: Exception) {
                            -1L
                        }
                        resultMap[source.key] = latency
                    }
                }
            }
            deferreds.awaitAll()
            _latencyMapFlow.value = resultMap
        }
    }

    /** 处于熔断（被判定为网络不可达）的域名，供 UI 提示用户配置代理 */
    fun unreachableHosts(): Set<String> = HostCircuitBreaker.openHosts()

    fun resetNetworkBreaker() = HostCircuitBreaker.resetAll()

    /* ------------------------------------------------------------------ *
     * 网页登录落库（官方 account.loginWithWebview）
     * ------------------------------------------------------------------ */

    /**
     * 把内嵌 WebView 抓到的 cookie 写入全局 CookieJar，供源的 OkHttp 请求复用。
     *
     * Android 的 `CookieManager.getCookie(url)` 只返回 `name=value; name2=value2`
     * 形式的请求头字符串，**拿不到 domain/path/expires 等属性**，因此这里按 url 的
     * host 归属、path=`/`、有效期给一年 —— 与官方把 WebView cookie 整体导入共享
     * CookieJar 的最终效果一致。
     *
     * @return 实际写入的 cookie 条数
     */
    fun saveCookiesFromWebLogin(url: String, cookieHeader: String?): Int {
        if (cookieHeader.isNullOrBlank()) return 0
        val httpUrl = url.toHttpUrlOrNull() ?: return 0
        val farFuture = System.currentTimeMillis() + 365L * 24 * 3600 * 1000
        val cookieJar = VeneraNetworkClient.getInstance(context).cookieJar

        // 解析出「name=value」对（domain/path 由下面的 loop 按目标主机补）
        val pairs = cookieHeader.split(";").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val cName = part.substring(0, idx).trim()
            val cValue = part.substring(idx + 1).trim()
            if (cName.isEmpty()) null else cName to cValue
        }
        if (pairs.isEmpty()) return 0

        // ★ 关键：不能只按登录页的 host 落库。很多源「登录在子域、业务在父域」，
        //   最典型的是 e-hentai —— 网页登录在 forums.e-hentai.org，而源的 baseUrl
        //   取自设置项 `domain`（默认 e-hentai.org）；只写子域会让
        //   https://e-hentai.org/favorites.php 变成匿名请求，表现为"收藏夹取不到"。
        //   因此同一份 cookie 同时写入「自身 host」与「注册域」两个归属。
        //   （官方 Flutter 版是把 WebView cookie 连同真实 domain 整体导入，
        //     效果等价于同一注册域内互通。）
        var saved = 0
        val targets = linkedSetOf(httpUrl.host)
        registrableDomain(httpUrl.host)?.let { targets.add(it) }

        for (host in targets) {
            val targetUrl = "https://$host".toHttpUrlOrNull() ?: continue
            val cookies = pairs.mapNotNull { (cName, cValue) ->
                try {
                    Cookie.Builder()
                        .name(cName)
                        .value(cValue)
                        .domain(host)
                        .path("/")
                        .expiresAt(farFuture)
                        .build()
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Skip invalid cookie: $cName", e)
                    null
                }
            }
            if (cookies.isEmpty()) continue
            cookieJar.saveFromResponse(targetUrl, cookies)
            saved += cookies.size
        }
        return saved
    }

    /**
     * 写入网页登录抓到的 localStorage 快照。
     *
     * 源侧通过 `this.loadData("_localStorage")` 读取（如 ccc.js 取 accessToken 解析 JWT）。
     */
    fun saveWebLoginLocalStorage(key: String, localStorageJson: String?) {
        if (localStorageJson.isNullOrBlank()) return
        try {
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val map: Map<String, Any?> = gson.fromJson(localStorageJson, type) ?: return
            if (map.isEmpty()) return
            jsEngine.dataStore.saveData(key, KEY_LOCAL_STORAGE, map)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save localStorage for $key", e)
        }
    }

    /**
     * 标记源为「已登录」。
     *
     * 官方以字符串 `"ok"` 表示**非账密方式**（网页登录 / cookie 直填）登录成功，
     * 账密登录则以 `["user","pass"]` 数组落库 —— 两者都满足 `isLogged`。
     */
    fun markLoggedIn(key: String) {
        jsEngine.dataStore.saveAccount(key, "ok")
    }

    /** 读取源声明的「需要手填的 cookie 字段」列表 */
    fun cookieFieldsOf(key: String): List<String> =
        registeredSources[key]?.getCookieFields() ?: emptyList()

    /**
     * 清除网页登录遗留的 localStorage 快照。
     *
     * 该快照里存的是 `accessToken` / `refreshToken` 这类**凭证material**，
     * 注销时必须一并清掉，否则退出登录后仍能凭残留 token 访问。
     */
    fun clearLocalStorage(key: String) {
        jsEngine.dataStore.deleteData(key, KEY_LOCAL_STORAGE)
    }

    companion object {
        private const val TAG = "ComicSourceManager"
        private const val PREFS_NAME = "venera_sources"
        private const val KEY_INSTALLED = "installed_sources_v1"

        /** 一次性标记：是否已执行过「内置源开箱写入」。用户清空全部源后不再回填 */
        private const val KEY_BOOTSTRAPPED = "bundled_bootstrapped_v1"

        /** 用户主动删除的内置/原生源集合，防止回退与重启复活 */
        private const val KEY_DELETED_BUILTINS = "deleted_builtin_sources_v1"

        /** 网页登录抓取的 localStorage 快照在源数据里的键名（与官方一致） */
        const val KEY_LOCAL_STORAGE = "_localStorage"

        /** 仓库索引地址的持久化键（用户可改） */
        private const val KEY_REPO_URL = "comic_source_repo_url_v1"

        /** 官方源仓库索引 */
        const val DEFAULT_REPO_URL = "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/index.json"

        /** 聚合搜索并发上限 */
        private const val SEARCH_CONCURRENCY = 8

        /** 单源检索硬超时 */
        private const val SOURCE_TIMEOUT_MS = 20_000L

        /** 版本号比较：a > b 返回正数 */
        fun compareVersion(a: String, b: String): Int {
            val pa = a.split('.').mapNotNull { it.trim().toIntOrNull() }
            val pb = b.split('.').mapNotNull { it.trim().toIntOrNull() }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val x = pa.getOrElse(i) { 0 }
                val y = pb.getOrElse(i) { 0 }
                if (x != y) return x - y
            }
            return 0
        }

        @Volatile
        private var INSTANCE: ComicSourceManager? = null

        fun getInstance(context: Context): ComicSourceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ComicSourceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

/**
 * 远程仓库索引条目（字段与官方 index.json 完全一致）。
 *
 * [url] 对应官方 index.json 中可选的 `url` 字段：允许某个源的脚本托管在
 * 仓库目录之外。为空时按「与 index.json 同目录」拼接 fileName（官方同等逻辑）。
 */
data class RepoIndexEntry(
    val name: String,
    val fileName: String,
    val key: String,
    val version: String,
    val description: String? = null,
    val url: String? = null
)
