package com.venera.compose.source

import android.content.Context
import com.venera.compose.source.baozi.BaoziMangaSource
import com.venera.compose.source.copymanga.CopyMangaSource
import com.venera.compose.source.mangadex.MangaDexSource
import com.venera.compose.source.model.ChapterPages
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ComicDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 漫画源核心管理器（单例）
 * 负责各漫画源的注册、活跃源切换、全网并发聚合搜索与网络延迟 (Ping) 周期探测
 */
class ComicSourceManager private constructor(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val registeredSources = linkedMapOf<String, ComicSource>()

    private val _sourcesFlow = MutableStateFlow<List<ComicSource>>(emptyList())
    val sourcesFlow: StateFlow<List<ComicSource>> = _sourcesFlow.asStateFlow()

    private val _activeSourceKey = MutableStateFlow("manga_dex")
    val activeSourceKey: StateFlow<String> = _activeSourceKey.asStateFlow()

    private val _latencyMapFlow = MutableStateFlow<Map<String, Long>>(emptyMap())
    val latencyMapFlow: StateFlow<Map<String, Long>> = _latencyMapFlow.asStateFlow()

    init {
        // 注册内置主流漫画源
        val mangaDex = MangaDexSource(context)
        val copyManga = CopyMangaSource(context)
        val baozi = BaoziMangaSource(context)

        registeredSources[mangaDex.key] = mangaDex
        registeredSources[copyManga.key] = copyManga
        registeredSources[baozi.key] = baozi

        _sourcesFlow.value = registeredSources.values.toList()

        // 启动后台延迟探测
        refreshPings()
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
     * 单源或全网聚合搜索
     */
    suspend fun search(sourceKey: String, keyword: String, page: Int = 1): Result<List<Comic>> = withContext(Dispatchers.IO) {
        if (sourceKey == "all") {
            // 全网并发搜索并聚合
            val allDeferreds = registeredSources.values.map { source ->
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
