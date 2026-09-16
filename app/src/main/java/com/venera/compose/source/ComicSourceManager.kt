package com.venera.compose.source

import android.content.Context
import com.venera.compose.engine.VeneraJsEngine
import com.venera.compose.source.baozi.BaoziMangaSource
import com.venera.compose.source.copymanga.CopyMangaSource
import com.venera.compose.source.js.ComicSourceParser
import com.venera.compose.source.js.JsComicSource
import com.venera.compose.source.mangadex.MangaDexSource
import com.venera.compose.source.model.ChapterPages
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ComicDetails
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 漫画源核心管理器（单例）
 * 负责各漫画源的注册、活跃源切换、全网并发聚合搜索与网络延迟 (Ping) 周期探测
 */
class ComicSourceManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val registeredSources = linkedMapOf<String, ComicSource>()

    private val _sourcesFlow = MutableStateFlow<List<ComicSource>>(emptyList())
    val sourcesFlow: StateFlow<List<ComicSource>> = _sourcesFlow.asStateFlow()

    private val _activeSourceKey = MutableStateFlow("manga_dex")
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

    init {
        // 注册内置主流漫画源
        val mangaDex = MangaDexSource(context)
        val copyManga = CopyMangaSource(context)
        val baozi = BaoziMangaSource(context)

        registeredSources[mangaDex.key] = mangaDex
        registeredSources[copyManga.key] = copyManga
        registeredSources[baozi.key] = baozi

        _sourcesFlow.value = registeredSources.values.toList()

        // 异步加载已安装的 .js 规则源
        scope.launch {
            loadInstalledJsSources()
            refreshPings()
        }
    }

    fun loadInstalledJsSources() {
        val dir = File(context.filesDir, "comic_source").apply { mkdirs() }
        var files = dir.listFiles { f -> f.extension == "js" } ?: emptyArray()
        if (files.isEmpty()) {
            listOf("copy_manga.js", "baozi.js", "manga_dex.js").forEach { name ->
                try {
                    context.assets.open("sources/$name").bufferedReader().use { reader ->
                        File(dir, name).writeText(reader.readText())
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ComicSourceManager", "Failed to unpack asset source: $name", e)
                }
            }
            files = dir.listFiles { f -> f.extension == "js" } ?: emptyArray()
        }
        for (file in files) {
            try {
                val source = parser.parseFile(file)
                registeredSources[source.key] = source
                android.util.Log.i("ComicSourceManager", "Loaded JS ComicSource: ${source.name} (${source.key}) v${source.version}")
            } catch (e: Exception) {
                android.util.Log.e("ComicSourceManager", "Failed to parse js source: ${file.name}", e)
            }
        }
        _sourcesFlow.value = registeredSources.values.toList()
    }

    fun installJsSource(jsContent: String): Result<ComicSource> {
        return try {
            val source = parser.parse(jsContent)
            val dir = File(context.filesDir, "comic_source").apply { mkdirs() }
            val file = File(dir, "${source.key}.js")
            file.writeText(jsContent)
            registeredSources[source.key] = source
            _sourcesFlow.value = registeredSources.values.toList()
            Result.success(source)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteJsSource(key: String): Boolean {
        val file = File(context.filesDir, "comic_source/$key.js")
        if (file.exists()) file.delete()
        val dataFile = File(context.filesDir, "comic_source/$key.data")
        if (dataFile.exists()) dataFile.delete()
        val fallback = when (key) {
            "manga_dex" -> MangaDexSource(context)
            "copy_manga" -> CopyMangaSource(context)
            "baozi" -> BaoziMangaSource(context)
            else -> null
        }
        if (fallback != null) {
            registeredSources[key] = fallback
        } else {
            registeredSources.remove(key)
        }
        _sourcesFlow.value = registeredSources.values.toList()
        return true
    }

    fun registerSource(source: ComicSource) {
        registeredSources[source.key] = source
        _sourcesFlow.value = registeredSources.values.toList()
    }

    fun setActiveSource(key: String) {
        _activeSourceKey.value = key
    }

    fun getSource(key: String): ComicSource? {
        return registeredSources[key]
    }

    /**
     * 刷新所有漫画源的连通性与 Ping 延迟
     */
    fun refreshPings() {
        scope.launch {
            val resultMap = mutableMapOf<String, Long>()
            val deferreds = registeredSources.values.map { source ->
                async {
                    val latency = source.ping()
                    source.key to latency
                }
            }
            deferreds.awaitAll().forEach { (k, v) ->
                resultMap[k] = v
            }
            _latencyMapFlow.value = resultMap
        }
    }

    /**
     * 单源搜索结果载体（供全网流式聚合展示）
     */
    data class SourceSearchResult(
        val sourceKey: String,
        val sourceName: String,
        val comics: List<Comic> = emptyList(),
        val error: String? = null,
        val isLoading: Boolean = false
    )

    /**
     * S4 核心突破：全网聚合搜索流式语义反转
     * 使用 channelFlow 逐源并发下发，任意源完成检索立即 emit，彻底解除慢源阻塞
     */
    fun searchAggregatedStream(keyword: String, page: Int = 1): Flow<SourceSearchResult> = channelFlow {
        val targets = registeredSources.values.filter { it.key != "all" }
        for (source in targets) {
            launch {
                try {
                    val res = source.search(keyword, page)
                    if (res.isSuccess) {
                        send(
                            SourceSearchResult(
                                sourceKey = source.key,
                                sourceName = source.name,
                                comics = res.getOrDefault(emptyList()),
                                error = null,
                                isLoading = false
                            )
                        )
                    } else {
                        send(
                            SourceSearchResult(
                                sourceKey = source.key,
                                sourceName = source.name,
                                comics = emptyList(),
                                error = res.exceptionOrNull()?.message ?: "检索无结果",
                                isLoading = false
                            )
                        )
                    }
                } catch (e: Exception) {
                    send(
                        SourceSearchResult(
                            sourceKey = source.key,
                            sourceName = source.name,
                            comics = emptyList(),
                            error = e.message ?: "检索异常",
                            isLoading = false
                        )
                    )
                }
            }
        }
    }

    /**
     * 单源搜索（或同步聚合等待模式）
     */
    suspend fun search(sourceKey: String, keyword: String, page: Int = 1): Result<List<Comic>> = withContext(Dispatchers.IO) {
        if (sourceKey == "all") {
            val allDeferreds = registeredSources.values.filter { it.key != "all" }.map { source ->
                async {
                    source.search(keyword, page).getOrDefault(emptyList())
                }
            }
            val results = allDeferreds.awaitAll().flatten()
            Result.success(results)
        } else {
            val source = registeredSources[sourceKey] ?: registeredSources.values.first()
            source.search(keyword, page)
        }
    }

    /**
     * 获取漫画详情
     */
    suspend fun getComicDetails(sourceKey: String, comicId: String): Result<ComicDetails> = withContext(Dispatchers.IO) {
        val source = registeredSources[sourceKey] ?: registeredSources.values.first()
        source.getComicDetails(comicId)
    }

    /**
     * 获取章节图片集
     */
    suspend fun getChapterPages(sourceKey: String, comicId: String, chapterId: String): Result<ChapterPages> = withContext(Dispatchers.IO) {
        val source = registeredSources[sourceKey] ?: registeredSources.values.first()
        source.getChapterPages(comicId, chapterId)
    }

    /**
     * 获取探索流
     */
    suspend fun getExploreComics(sourceKey: String, page: Int = 1): Result<List<Comic>> = withContext(Dispatchers.IO) {
        val source = registeredSources[sourceKey] ?: registeredSources.values.first()
        source.getExploreComics(page)
    }

    companion object {
        @Volatile
        private var INSTANCE: ComicSourceManager? = null

        fun getInstance(context: Context): ComicSourceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ComicSourceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
