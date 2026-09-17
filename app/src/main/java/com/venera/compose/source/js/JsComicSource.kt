package com.venera.compose.source.js

import android.util.Log
import com.google.gson.reflect.TypeToken
import com.venera.compose.engine.VeneraJsEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.venera.compose.feature.sourcemanage.SelectOption
import com.venera.compose.feature.sourcemanage.SourceAccountInfo
import com.venera.compose.feature.sourcemanage.SourceSettingItem
import com.venera.compose.source.ComicSource
import com.venera.compose.source.model.ChapterPages
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ComicChapter
import com.venera.compose.source.model.ResolvedImageConfig
import com.venera.compose.source.model.ThumbnailPage
import com.venera.compose.source.FavComicNext
import com.venera.compose.source.FavComicPage
import com.venera.compose.source.FavFolders
import com.venera.compose.source.FavoriteData
import com.venera.compose.source.model.ComicDetails
import com.venera.compose.source.model.CategoryButtonData
import com.venera.compose.source.model.CategoryComicsOption
import com.venera.compose.source.model.CategoryComicsResult
import com.venera.compose.source.model.CategoryData
import com.venera.compose.source.model.CategoryItem
import com.venera.compose.source.model.CategoryPart
import com.venera.compose.source.model.ExplorePageData
import com.venera.compose.source.model.ExplorePagePart
import com.venera.compose.source.model.PageJumpTarget

class JsComicSource(
    private val engine: VeneraJsEngine,
    override val key: String,
    override val name: String,
    override val version: String,
    override val iconUrl: String? = null,
    /** JS 顶层声明的更新地址（官方 `this['temp'].url`），供「按 URL 更新」使用 */
    override val url: String = ""
) : ComicSource {

    private companion object {
        const val TAG = "VeneraFav"

        /** 各源约定的「全部收藏」文件夹 id（ehentai.js / copy_manga_multi_accounts.js 均如此） */
        const val ALL_FOLDER_ID = "-1"
    }

    private val gson = engine.gson

    override val favoriteData: FavoriteData? by lazy { loadFavoriteData() }

    /**
     * 解析源 JS 的 `favorites` 对象，生成 [FavoriteData]。
     * 源未声明 `favorites` 时返回 null（该源不支持网络收藏）。
     * 对齐官方 `parser.dart:_loadFavoriteData`。
     */
    private fun loadFavoriteData(): FavoriteData? {
        val metaJson = runCatching {
            engine.evaluate(
                """
                JSON.stringify({
                    exists: !!ComicSource.sources['$key'].favorites,
                    multiFolder: ComicSource.sources['$key'].favorites?.multiFolder === true,
                    singleFolderForSingleComic: ComicSource.sources['$key'].favorites?.singleFolderForSingleComic === true,
                    isOldToNewSort: ComicSource.sources['$key'].favorites?.isOldToNewSort === true,
                    hasLoadComics: typeof ComicSource.sources['$key'].favorites?.loadComics === 'function',
                    hasLoadNext: typeof ComicSource.sources['$key'].favorites?.loadNext === 'function',
                    hasAddFolder: typeof ComicSource.sources['$key'].favorites?.addFolder === 'function',
                    hasDeleteFolder: typeof ComicSource.sources['$key'].favorites?.deleteFolder === 'function'
                })
                """.trimIndent()
            )
        }.getOrNull() ?: return null

        val meta = runCatching {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(metaJson, object : TypeToken<Map<String, Any?>>() {}.type) as? Map<String, Any?>
        }.getOrNull() ?: return null

        if (meta["exists"] != true) return null

        val hasLoadComics = meta["hasLoadComics"] == true
        val hasLoadNext = meta["hasLoadNext"] == true
        val multiFolder = meta["multiFolder"] == true
        val singleFolderForSingleComic = meta["singleFolderForSingleComic"] == true
        val isOldToNewSort = meta["isOldToNewSort"] == true
        val hasAddFolder = meta["hasAddFolder"] == true
        val hasDeleteFolder = meta["hasDeleteFolder"] == true

        // 官方对 `loadComics` / `loadNext` 分别做 _checkExists，两者可单独存在
        // （多数源只声明 loadComics；ehentai 只声明 loadNext，首屏即由它返回）。
        //
        // ⚠️ 脚本必须带 `return`：`VeneraJsEngine.evaluateAsync` 会把它包进
        // `(function() { <script> })()`，缺 `return` 时整体返回 undefined，
        // 经 `JSON.stringify({success:true, data:undefined})` 后 `data` 键会被整个丢掉，
        // 于是 Kotlin 侧拿到 null 并静默降级成空列表（曾表现为「网络收藏一直为空且不报错」）。
        val loadComic: (suspend (Int, String?) -> Result<FavComicPage>)? = if (hasLoadComics) {
            { page, folder ->
                runFavCall(
                    "return ComicSource.sources['$key'].favorites.loadComics(${gson.toJson(page)}, ${gson.toJson(folder)})"
                ) { data ->
                    FavComicPage(parseFavComics(data["comics"]), (data["maxPage"] as? Number)?.toInt() ?: 1)
                }
            }
        } else null

        val loadNext: (suspend (String?, String?) -> Result<FavComicNext>)? = if (hasLoadNext) {
            { next, folder ->
                runFavCall(
                    "return ComicSource.sources['$key'].favorites.loadNext(${gson.toJson(next)}, ${gson.toJson(folder)})"
                ) { data ->
                    FavComicNext(parseFavComics(data["comics"]), data["next"]?.toString())
                }
            }
        } else null

        val loadFolders: (suspend (String?) -> Result<FavFolders>)? = if (multiFolder) {
            { comicId ->
                runFavCall(
                    "return ComicSource.sources['$key'].favorites.loadFolders(${gson.toJson(comicId)})"
                ) { data ->
                    val raw = (data["folders"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                        (k?.toString() ?: return@mapNotNull null) to (v?.toString() ?: "")
                    }?.toMap() ?: emptyMap()

                    // ⚠️ 顺序修正：源里的 folders 是 JS Map（插入序），但 JSON.stringify 会把
                    // 「整数形式」的键（0/1/2…）排到最前，字符串键排在其后 —— 于是
                    // ehentai / copy_manga 用 "-1" 表示的那个「全部/All」项会从首位被挤到末尾。
                    // 各源约定 "-1" = 全部，这里把它提回首位，恢复官方 Map 的原始顺序。
                    val folders = LinkedHashMap<String, String>(raw.size).apply {
                        raw[ALL_FOLDER_ID]?.let { put(ALL_FOLDER_ID, it) }
                        raw.forEach { (k, v) -> if (k != ALL_FOLDER_ID) put(k, v) }
                    }

                    val favorited = (data["favorited"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                    FavFolders(folders, favorited)
                }
            }
        } else null

        val addFolder: (suspend (String) -> Result<Unit>)? = if (multiFolder && hasAddFolder) {
            { name ->
                runFavCall(
                    "return ComicSource.sources['$key'].favorites.addFolder(${gson.toJson(name)})"
                ) { _ -> }
            }
        } else null

        val deleteFolder: (suspend (String) -> Result<Unit>)? = if (multiFolder && hasDeleteFolder) {
            { folderId ->
                runFavCall(
                    "return ComicSource.sources['$key'].favorites.deleteFolder(${gson.toJson(folderId)})"
                ) { _ -> }
            }
        } else null

        val addOrDelFavorite: suspend (String, String, Boolean, String?) -> Result<Unit> = { comicId, folderId, isAdding, favoriteId ->
            runFavCall(
                "return ComicSource.sources['$key'].favorites.addOrDelFavorite(" +
                    "${gson.toJson(comicId)}, ${gson.toJson(folderId)}, ${gson.toJson(isAdding)}, ${gson.toJson(favoriteId)})"
            ) { _ -> }
        }

        return FavoriteData(
            key = key,
            title = name,
            multiFolder = multiFolder,
            singleFolderForSingleComic = singleFolderForSingleComic,
            isOldToNewSort = isOldToNewSort,
            loadComic = loadComic,
            loadNext = loadNext,
            loadFolders = loadFolders,
            addFolder = addFolder,
            deleteFolder = deleteFolder,
            addOrDelFavorite = addOrDelFavorite,
        )
    }

    /**
     * 网络收藏调用的统一封装：对齐官方 `retryZone`。
     * - 未登录直接返回失败（Not login）
     * - 调用抛 `Login expired` 时自动 reLogin 后重试一次
     */
    private suspend fun <T> runFavCall(
        script: String,
        transform: (Map<*, *>) -> T,
    ): Result<T> {
        // ⚠️ getAccountInfo() 内部是**同步** engine.evaluate()。若在主线程调用，
        // VeneraJsEngine.evaluate() 会 evalExecutor.submit{}.get(30s)，而主线程被占死时
        // evaluateBlocking post 到 mainHandler 的 Runnable 永远排不上，必定卡满 30 秒超时。
        // 收藏调用都由 viewModelScope（主线程）发起，故这里必须先切到 IO。
        if (!withContext(Dispatchers.IO) { getAccountInfo().isLogged }) {
            return Result.failure(Exception("Not login"))
        }
        suspend fun attempt(): Result<T> = runCatching {
            val env = evaluateEnvelope(script)
            if (env["success"] != true) {
                throw Exception(env["error"]?.toString() ?: "favorite call failed")
            }
            if (env["data"] == null) {
                // 这是「静默为空」的特征：脚本缺 `return` 时 evaluateAsync 会得到
                // undefined，JSON.stringify 直接把 data 键丢掉。留一条明确日志，
                // 免得又出现「无报错但列表恒空」这种要翻半天的情况。
                Log.w(TAG, "fav call returned success but data==null (脚本可能漏了 return): $script")
            }
            val data = (env["data"] as? Map<*, *>) ?: emptyMap<Any?, Any?>()
            runCatching {
                transform(data)
            }.onFailure { Log.w(TAG, "fav call transform failed: $script -> ${it.message}") }
                .getOrThrow()
        }.onFailure {
            Log.w(TAG, "fav call failed: $script -> ${it.message}")
        }
        val first = attempt()
        val err = first.exceptionOrNull()?.message ?: ""
        return if (first.isFailure && err.contains("Login expired")) {
            // relogin() 会读写源数据文件（JsSourceDataStore 的 {key}.data），
            // 与 ComicSourceViewModel 的既有做法一致，放到 IO 线程执行。
            val re = withContext(Dispatchers.IO) { relogin() }
            if (re.getOrDefault(false)) attempt() else first
        } else {
            first
        }
    }

    /** 将源返回的 comic 列表（任意 JSON 形态）解析为 [Comic]，字段对齐 [search] 的解析逻辑 */
    private fun parseFavComics(raw: Any?): List<Comic> {
        val list: List<*> = when (raw) {
            is List<*> -> raw
            is Map<*, *> -> (raw["comics"] as? List<*>) ?: (raw["list"] as? List<*>) ?: emptyList<Any?>()
            else -> emptyList<Any?>()
        }
        return list.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val id = (map["id"] ?: map["path_word"] ?: map["comicId"])?.toString() ?: return@mapNotNull null
            val title = (map["title"] ?: map["name"])?.toString() ?: ""
            val subTitle = (map["subTitle"] ?: map["subtitle"] ?: map["author"])?.toString() ?: ""
            val cover = (map["cover"] ?: map["coverUrl"])?.toString() ?: ""
            val desc = (map["description"] ?: map["desc"])?.toString() ?: ""
            val tags = when (val t = map["tags"] ?: map["theme"]) {
                is List<*> -> t.mapNotNull {
                    when (it) {
                        is Map<*, *> -> it["name"]?.toString()
                        else -> it?.toString()
                    }
                }
                else -> emptyList()
            }
            val updateTime = (map["updateTime"] ?: map["datetime_updated"])?.toString() ?: ""
            Comic(
                id = id,
                title = title,
                subTitle = subTitle,
                cover = cover,
                sourceKey = key,
                tags = tags,
                description = desc,
                updateTime = updateTime,
                rating = SourcePayloadParser.rating(map),
                likesCount = SourcePayloadParser.likesCount(map)
            )
        }
    }

    /**
     * `search.loadNext` 型源的分页游标缓存。
     *
     * 官方把「当前页的 next」保存在搜索页状态里：第 1 页传 `null`，之后传上一页
     * 返回的 `res.next`。我们的 [ComicSource.search] 只有 `(keyword, page)` 两个参数，
     * 因此用 `keyword + 页码` 作为键在源实例内缓存游标 —— 语义与官方一致，
     * 未命中时退化为 `null`（等价于"从第一页开始"），不会产生错误结果。
     */
    private val nextTokenCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun nextCacheKey(keyword: String, options: List<String?>?, page: Int) = gson.toJson(listOf(keyword, options, page))

    override suspend fun ping(): Long {
        val start = System.currentTimeMillis()
        return try {
            val res = search("test", 1)
            if (res.isSuccess) System.currentTimeMillis() - start else -1L
        } catch (e: Exception) {
            -1L
        }
    }

    override suspend fun getSearchOptions(): List<com.venera.compose.source.model.SearchOptionGroup> {
        val raw = engine.evaluateAsync("""
            var s = ComicSource.sources[${gson.toJson(key)}];
            return s && s.search ? (s.search.optionList || []) : [];
        """.trimIndent())
        return SourcePayloadParser.searchOptions(SourcePayloadParser.data(raw))
    }

    override suspend fun formatSearchTag(namespace: String, tag: String): String {
        val raw = engine.evaluateAsync("""
            var s = ComicSource.sources[${gson.toJson(key)}];
            if (s && s.search && typeof s.search.onTagSuggestionSelected === 'function') {
                return await s.search.onTagSuggestionSelected(${gson.toJson(namespace)}, ${gson.toJson(tag)});
            }
            return ${gson.toJson(if (namespace.isBlank()) tag else "$namespace:$tag")};
        """.trimIndent())
        return SourcePayloadParser.data(raw)?.toString() ?: super.formatSearchTag(namespace, tag)
    }

    override suspend fun search(keyword: String, page: Int, options: List<String?>?): Result<List<Comic>> {
        return try {
            val pageNum = if (page < 1) 1 else page
            // 官方 search.loadNext(keyword, options, next) 的 next 由**搜索页状态**维护：
            // 第 1 页传 null，之后传上一页返回的 res.next。这里用同语义的缓存等价实现。
            val nextToken = if (pageNum == 1) null else nextTokenCache[nextCacheKey(keyword, options, pageNum - 1)]
            // S8 批次B: 搜索页传入的筛选值（null 时回退源默认，保持原行为）
            val optsJson = gson.toJson(options ?: emptyList<String>())
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.search) return { comics: [] };
                    // S8: 使用搜索页传入的筛选值；未传时对齐官方 useDefaultOptions() 用默认值
                    var opts = $optsJson;
                    if (opts.length === 0) opts = _veneraOptionValues(s.search.optionList);
                    var res = null;
                    if (s.search.load) {
                        res = await s.search.load(${gson.toJson(keyword)}, opts, $pageNum);
                    } else if (s.search.loadNext) {
                        res = await s.search.loadNext(${gson.toJson(keyword)}, opts, ${gson.toJson(nextToken)});
                    }
                    return res || { comics: [] };
                })()
            """.trimIndent()

            val rawJson = engine.evaluateAsync(script)
            val envelope = gson.fromJson<Map<String, Any?>>(rawJson, object : TypeToken<Map<String, Any?>>() {}.type)
            if (envelope["success"] != true) {
                return Result.failure(Exception(envelope["error"]?.toString() ?: "search failed"))
            }
            val rawData = envelope["data"]
            // 记录下一页游标：loadNext 型源返回 res.next，load 型源返回 res.maxPage
            if (rawData is Map<*, *>) {
                val next = rawData["next"]?.toString()
                if (!next.isNullOrBlank() && next != "null") {
                    nextTokenCache[nextCacheKey(keyword, options, pageNum)] = next
                }
            }
            val comicsList: List<*> = when (rawData) {
                is List<*> -> rawData
                is Map<*, *> -> (rawData["comics"] as? List<*>)
                    ?: (rawData["list"] as? List<*>)
                    ?: (rawData["results"] as? List<*>)
                    ?: (rawData["data"] as? List<*>)
                    ?: emptyList<Any?>()
                else -> emptyList<Any?>()
            }

            val comics = comicsList.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val id = (map["id"] ?: map["path_word"] ?: map["comicId"])?.toString() ?: return@mapNotNull null
                val title = (map["title"] ?: map["name"])?.toString() ?: ""
                val subTitle = (map["subTitle"] ?: map["subtitle"] ?: map["author"])?.toString() ?: ""
                val cover = (map["cover"] ?: map["coverUrl"])?.toString() ?: ""
                val desc = (map["description"] ?: map["desc"])?.toString() ?: ""
                val tags = when (val t = map["tags"] ?: map["theme"]) {
                    is List<*> -> t.mapNotNull {
                        when (it) {
                            is Map<*, *> -> it["name"]?.toString()
                            else -> it?.toString()
                        }
                    }
                    else -> emptyList()
                }
                val updateTime = (map["updateTime"] ?: map["datetime_updated"])?.toString() ?: ""

                Comic(
                    id = id,
                    title = title,
                    subTitle = subTitle,
                    cover = cover,
                    sourceKey = key,
                    tags = tags,
                    description = desc,
                    updateTime = updateTime,
                    rating = SourcePayloadParser.rating(map),
                    likesCount = SourcePayloadParser.likesCount(map)
                )
            }
            Result.success(comics)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getComicDetails(comicId: String): Result<ComicDetails> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.comic || !s.comic.loadInfo) throw new Error("loadInfo not implemented");
                    var res = await s.comic.loadInfo(${gson.toJson(comicId)});
                    return res;
                })()
            """.trimIndent()

            val rawJson = engine.evaluateAsync(script)
            val envelope = gson.fromJson<Map<String, Any?>>(rawJson, object : TypeToken<Map<String, Any?>>() {}.type)
            if (envelope["success"] != true) {
                Log.e("VeneraDebug", "loadInfo($key) JS failed: ${envelope["error"]}")
                return Result.failure(Exception(envelope["error"]?.toString() ?: "loadInfo failed"))
            }
            val res = envelope["data"] as? Map<*, *> ?: return Result.failure(Exception("invalid comic details"))

            val title = res["title"]?.toString() ?: ""
            val subTitle = res["subTitle"]?.toString() ?: (res["subtitle"]?.toString() ?: "")
            val cover = res["cover"]?.toString() ?: ""
            val desc = res["description"]?.toString() ?: ""
            val author = res["author"]?.toString() ?: (res["uploader"]?.toString() ?: "")

            val rawTags = res["tags"]
            val tagMap = mutableMapOf<String, List<String>>()
            val tags = when (rawTags) {
                is List<*> -> {
                    val list = rawTags.mapNotNull { it?.toString() }
                    tagMap["标签"] = list
                    list
                }
                is Map<*, *> -> {
                    for ((k, v) in rawTags) {
                        val groupName = k?.toString() ?: "标签"
                        val groupList = when (v) {
                            is List<*> -> v.mapNotNull { it?.toString() }
                            else -> listOfNotNull(v?.toString())
                        }
                        tagMap[groupName] = groupList
                    }
                    tagMap.values.flatten()
                }
                else -> emptyList()
            }

            val chapters = mutableListOf<ComicChapter>()
            val chapterGroups = mutableListOf<com.venera.compose.source.model.ChapterGroup>()
            val rawChapters = res["chapters"]
            if (rawChapters is Map<*, *>) {
                var globalOrder = 0
                for ((groupKey, groupVal) in rawChapters) {
                    if (groupVal is Map<*, *>) {
                        val groupChapters = mutableListOf<ComicChapter>()
                        var orderInGroup = 0
                        for ((subKey, subVal) in groupVal) {
                            val ch = ComicChapter(
                                id = subKey.toString(),
                                title = subVal.toString(),
                                order = globalOrder++,
                                group = groupKey.toString()
                            )
                            chapters.add(ch)
                            groupChapters.add(ch)
                        }
                        chapterGroups.add(com.venera.compose.source.model.ChapterGroup(name = groupKey.toString(), chapters = groupChapters))
                    } else {
                        val ch = ComicChapter(id = groupKey.toString(), title = groupVal.toString(), order = globalOrder++)
                        chapters.add(ch)
                    }
                }
            } else if (rawChapters is List<*>) {
                rawChapters.forEachIndexed { index, item ->
                    if (item is Map<*, *>) {
                        val chId = item["id"]?.toString() ?: index.toString()
                        val chTitle = item["title"]?.toString() ?: "第 $index 话"
                        chapters.add(ComicChapter(id = chId, title = chTitle, order = index))
                    }
                }
            }

            if (chapterGroups.isEmpty() && chapters.isNotEmpty()) {
                chapterGroups.add(com.venera.compose.source.model.ChapterGroup(name = "默认", chapters = chapters))
            }

            // 关联推荐
            val recommend = (res["recommend"] as? List<*>)?.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val id = map["id"]?.toString() ?: return@mapNotNull null
                val recTitle = map["title"]?.toString() ?: ""
                val recCover = map["cover"]?.toString() ?: ""
                Comic(id = id, title = recTitle, cover = recCover, sourceKey = key, rating = SourcePayloadParser.rating(map), likesCount = SourcePayloadParser.likesCount(map))
            }.orEmpty()

            // 缩略图
            val thumbnails = (res["thumbnails"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()

            // 预览评论
            val comments = (res["comments"] as? List<*>)?.mapNotNull { item ->
                (item as? Map<*, *>)?.let(SourcePayloadParser::comment)
            }.orEmpty()

            val comic = Comic(
                id = comicId,
                title = title,
                subTitle = subTitle,
                cover = cover,
                sourceKey = key,
                tags = tags,
                description = desc,
                updateTime = res["updateTime"]?.toString() ?: "",
                isFavorite = res["isFavorite"] == true,
                rating = SourcePayloadParser.rating(res),
                likesCount = SourcePayloadParser.likesCount(res)
            )

            val details = ComicDetails(
                comic = comic,
                author = author,
                status = res["status"]?.toString().orEmpty(),
                rating = (res["stars"] as? Number)?.toFloat() ?: 0f,
                chapters = chapters,
                chapterGroups = chapterGroups,
                thumbnails = thumbnails,
                recommend = recommend,
                tagMap = tagMap,
                uploader = res["uploader"]?.toString() ?: author,
                uploadTime = res["uploadTime"]?.toString() ?: "",
                updateTime = res["updateTime"]?.toString() ?: "",
                url = res["url"]?.toString() ?: "",
                stars = (res["stars"] as? Number)?.toFloat() ?: 0f,
                maxPage = (res["maxPage"] as? Number)?.toInt() ?: 1,
                likesCount = (res["likesCount"] as? Number)?.toInt() ?: 0,
                isLiked = res["isLiked"] == true,
                isFavorite = res["isFavorite"] == true,
                commentCount = (res["commentCount"] as? Number)?.toInt() ?: comments.size,
                comments = comments,
                subId = res["subId"]?.toString(),
                sourceKey = key
            )
            Result.success(details)
        } catch (e: CancellationException) {
            // 协程取消必须原样抛出（Kotlin 协程规范）：否则「用户按返回离开页面」
            // 会被记成一次加载失败，还可能继续走失败分支弹一个已无人关心的提示。
            throw e
        } catch (e: Exception) {
            Log.e("VeneraDebug", "getComicDetails($key) exception", e)
            Result.failure(e)
        }
    }

    override suspend fun getChapterPages(comicId: String, chapterId: String): Result<ChapterPages> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.comic || !s.comic.loadEp) throw new Error("loadEp not implemented");
                    var res = await s.comic.loadEp(${gson.toJson(comicId)}, ${gson.toJson(chapterId)});
                    // 官方语义（parser.dart:_parseImageLoadingConfigFunc）：源声明了
                    // comic.onImageLoad 时，loadEp 的每一项都是「图片键」，展示前必须
                    // 经 onImageLoad 解析成真实地址 —— EH 返回页码，其余源多用于改写/加签。
                    res.useOnImageLoad = !!(s.comic && s.comic.onImageLoad);
                    return res;
                })()
            """.trimIndent()

            val rawJson = engine.evaluateAsync(script)
            val envelope = gson.fromJson<Map<String, Any?>>(rawJson, object : TypeToken<Map<String, Any?>>() {}.type)
            if (envelope["success"] != true) {
                Log.e("VeneraDebug", "loadEp($key, cid=$comicId, eid=$chapterId) JS failed: ${envelope["error"]}")
                return Result.failure(Exception(envelope["error"]?.toString() ?: "loadEp failed"))
            }
            val rawData = envelope["data"]
            val rawImages: List<*> = when (rawData) {
                is List<*> -> rawData
                is Map<*, *> -> (rawData["images"] as? List<*>)
                    ?: (rawData["pages"] as? List<*>)
                    ?: (rawData["list"] as? List<*>)
                    ?: (rawData["data"] as? List<*>)
                    ?: emptyList<Any?>()
                else -> emptyList<Any?>()
            }
            val pages = rawImages.mapNotNull {
                val s = it?.toString()?.trim() ?: return@mapNotNull null
                if (s.isBlank()) return@mapNotNull null
                if (s.startsWith("//")) "https:$s" else s
            }
            val resMap = rawData as? Map<*, *>
            val useOnImageLoad = resMap?.get("useOnImageLoad") == true

            val headers = (resMap?.get("headers") as? Map<*, *>)?.mapNotNull { (k, v) ->
                if (k != null && v != null) k.toString() to v.toString() else null
            }?.toMap() ?: emptyMap()

            Result.success(
                ChapterPages(
                    comicId = comicId,
                    chapterId = chapterId,
                    pages = pages,
                    headers = headers,
                    useOnImageLoad = useOnImageLoad
                )
            )
        } catch (e: CancellationException) {
            // 见 getComicDetails：取消是控制流，不是错误
            throw e
        } catch (e: Exception) {
            Log.e("VeneraDebug", "getChapterPages($key, cid=$comicId, eid=$chapterId) exception", e)
            Result.failure(e)
        }
    }

    /**
     * 解析单页图片的真实加载配置 —— 对齐官方 `_parseImageLoadingConfigFunc` +
     * `network/images.dart:_loadComicImage`：
     * 调源 JS `comic.onImageLoad(imageKey, comicId, epId, nl)` 拿 `{url, headers, nl, modifyImage}`；
     * 当源仅返回 headers / modifyImage 时（如 jm、komiic、manhuagui、manhuaren、komga、lanraragi），
     * 真实 url 回退到原 `imageKey`；
     * 下载失败时由调用方带上 [nl] 重新解析（等价官方执行 onLoadFailed 闭包），上限 5 次。
     */
    suspend fun resolveImageLoadingConfig(
        comicId: String,
        epId: String,
        imageKey: String,
        nl: String?
    ): Result<ResolvedImageConfig> {
        return try {
            val nlLiteral = nl?.let { gson.toJson(it) } ?: "undefined"
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.comic || !s.comic.onImageLoad) throw new Error("onImageLoad not implemented");
                    return await s.comic.onImageLoad(${gson.toJson(imageKey)}, ${gson.toJson(comicId)}, ${gson.toJson(epId)}, $nlLiteral);
                })()
            """.trimIndent()

            val rawJson = engine.evaluateAsync(script)
            val envelope = gson.fromJson<Map<String, Any?>>(rawJson, object : TypeToken<Map<String, Any?>>() {}.type)
            if (envelope["success"] != true) {
                return Result.failure(Exception(envelope["error"]?.toString() ?: "onImageLoad failed"))
            }
            val data = envelope["data"] as? Map<*, *>
                ?: return Result.failure(Exception("onImageLoad 返回了无效配置"))
            val rawUrl = data["url"]?.toString().orEmpty().trim()
            val url = if (rawUrl.isNotBlank()) {
                if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
            } else {
                if (imageKey.startsWith("//")) "https:$imageKey" else imageKey
            }
            val headers = (data["headers"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                if (k != null && v != null) k.toString() to v.toString() else null
            }?.toMap() ?: emptyMap()
            val modifyImage = data["modifyImage"]?.toString()
            if (!modifyImage.isNullOrBlank()) {
                val numMatch = Regex("""const\s+num\s*=\s*(\d+)""").find(modifyImage)
                val num = numMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (num > 1) {
                    com.venera.compose.data.network.ImagePipelinePolicy.registerScramble(url, num)
                }
            }
            Result.success(
                ResolvedImageConfig(
                    url = url,
                    headers = headers,
                    nl = data["nl"]?.toString(),
                    modifyImage = modifyImage
                )
            )
        } catch (e: CancellationException) {
            // 见 getComicDetails：取消是控制流，不是错误
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 拉取一页官方预览缩略图 —— 对齐官方 `parser.dart:_parseThumbnailLoader`：
     * 调源 JS `comic.loadThumbnails(id, next)`，返回 `{thumbnails, urls, next}`；
     * `next == null` 表示没有更多页。源未实现该函数时如实失败
     * （官方 `_checkExists("comic.loadThumbnails")` 为 false 时返回 null 加载器）。
     *
     * EH 的 `loadThumbnails` 抓的是 gallery 页面里的官方预览小图，一次请求拿整页
     * 缩略图，比逐页请求大图快得多，也不吃站方的图片配额。
     *
     * @param next 上一页返回的 token；首页传 null（JS 侧收到字面量 null）。
     */
    suspend fun loadThumbnails(comicId: String, next: String?): Result<ThumbnailPage> {
        return try {
            val nextLiteral = next?.let { gson.toJson(it) } ?: "null"
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.comic || !s.comic.loadThumbnails) throw new Error("loadThumbnails not implemented");
                    return await s.comic.loadThumbnails(${gson.toJson(comicId)}, $nextLiteral);
                })()
            """.trimIndent()

            val rawJson = engine.evaluateAsync(script)
            val envelope = gson.fromJson<Map<String, Any?>>(rawJson, object : TypeToken<Map<String, Any?>>() {}.type)
            if (envelope["success"] != true) {
                Log.e("VeneraDebug", "loadThumbnails($key) JS failed: ${envelope["error"]}")
                return Result.failure(Exception(envelope["error"]?.toString() ?: "loadThumbnails failed"))
            }
            val res = envelope["data"] as? Map<*, *>
                ?: return Result.failure(Exception("loadThumbnails 返回了无效数据"))
            val thumbs = (res["thumbnails"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            // JS 的 null 经 JSON 过来就是 null → 表示末页
            val nextToken = res["next"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            Result.success(ThumbnailPage(thumbnails = thumbs, next = nextToken))
        } catch (e: CancellationException) {
            // 见 getComicDetails：取消是控制流，不是错误
            throw e
        } catch (e: Exception) {
            Log.e("VeneraDebug", "loadThumbnails($key, cid=$comicId, next=$next) exception", e)
            Result.failure(e)
        }
    }

    override suspend fun getExplorePages(): Result<List<ExplorePageData>> {
        return try {
            val script = """
                return (function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.explore || !Array.isArray(s.explore)) return [];
                    return s.explore.map(function(e, idx) {
                        return {
                            title: e.title || s.name,
                            type: e.type || "multiPageComicList",
                            pageIndex: idx
                        };
                    });
                })()
            """.trimIndent()
            val rawJson = engine.evaluateAsync(script)
            val envelope = gson.fromJson<Map<String, Any?>>(rawJson, object : TypeToken<Map<String, Any?>>() {}.type)
            if (envelope["success"] != true) {
                return Result.success(emptyList())
            }
            val list = envelope["data"] as? List<*> ?: return Result.success(emptyList())
            val pages = list.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val title = m["title"]?.toString() ?: name
                val type = m["type"]?.toString() ?: "multiPageComicList"
                val idx = (m["pageIndex"] as? Number)?.toInt() ?: 0
                ExplorePageData(
                    title = title,
                    type = type,
                    sourceKey = key,
                    sourceName = name,
                    pageIndex = idx
                )
            }
            Result.success(pages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadExplorePage(pageIndex: Int, page: Int): Result<List<ExplorePagePart>> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.explore || !s.explore[$pageIndex]) throw new Error("Explore page not found");
                    var exp = s.explore[$pageIndex];
                    var t = exp.type;
                    if (t === "singlePageWithMultiPart") {
                        var res = await exp.load();
                        return { type: t, title: exp.title, data: res };
                    } else if (t === "multiPageComicList") {
                        var res = exp.load ? await exp.load($page) : (exp.loadNext ? await exp.loadNext($page == 1 ? null : String($page)) : { comics: [] });
                        return { type: t, title: exp.title, data: res };
                    } else if (t === "mixed") {
                        var res = await exp.loadMixed($page);
                        return { type: t, title: exp.title, data: res };
                    } else {
                        var res = exp.load ? await exp.load($page) : [];
                        return { type: t, title: exp.title, data: res };
                    }
                })()
            """.trimIndent()
            val rawJson = engine.evaluateAsync(script)
            val envelope = gson.fromJson<Map<String, Any?>>(rawJson, object : TypeToken<Map<String, Any?>>() {}.type)
            if (envelope["success"] != true) {
                return Result.failure(Exception(envelope["error"]?.toString() ?: "loadExplorePage failed"))
            }
            val payload = envelope["data"] as? Map<*, *> ?: return Result.success(emptyList())
            val type = payload["type"]?.toString() ?: ""
            val rawData = payload["data"]

            fun parseComicMap(m: Map<*, *>): Comic? {
                val comicObj = (m["comic"] as? Map<*, *>) ?: m
                val id = comicObj["id"]?.toString() ?: comicObj["path_word"]?.toString() ?: return null
                val title = comicObj["title"]?.toString() ?: comicObj["name"]?.toString() ?: ""
                val subTitle = comicObj["subTitle"]?.toString() ?: comicObj["author"]?.toString() ?: ""
                val cover = comicObj["cover"]?.toString() ?: ""
                val tags = (comicObj["tags"] as? List<*>)?.mapNotNull { it?.toString() }
                    ?: (comicObj["theme"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get("name")?.toString() }
                    ?: emptyList()
                return Comic(
                    id = id,
                    title = title,
                    subTitle = subTitle,
                    cover = cover,
                    sourceKey = key,
                    tags = tags,
                    rating = SourcePayloadParser.rating(comicObj),
                    likesCount = SourcePayloadParser.likesCount(comicObj)
                )
            }

            fun parseComicList(list: List<*>): List<Comic> {
                return list.mapNotNull { item ->
                    (item as? Map<*, *>)?.let { parseComicMap(it) }
                }
            }

            val parts = mutableListOf<ExplorePagePart>()
            if (type == "singlePageWithMultiPart") {
                if (rawData is Map<*, *>) {
                    for ((k, v) in rawData) {
                        val sectionTitle = k?.toString() ?: ""
                        val comics = (v as? List<*>)?.let { parseComicList(it) } ?: emptyList()
                        if (comics.isNotEmpty()) {
                            parts.add(ExplorePagePart(title = sectionTitle, comics = comics))
                        }
                    }
                } else if (rawData is List<*>) {
                    for (item in rawData) {
                        val m = item as? Map<*, *> ?: continue
                        val partTitle = m["title"]?.toString() ?: ""
                        val comics = (m["comics"] as? List<*>)?.let { parseComicList(it) } ?: emptyList()
                        if (comics.isNotEmpty()) {
                            parts.add(ExplorePagePart(title = partTitle, comics = comics))
                        }
                    }
                }
            } else {
                val comics = when (rawData) {
                    is Map<*, *> -> (rawData["comics"] as? List<*>)?.let { parseComicList(it) } ?: emptyList()
                    is List<*> -> parseComicList(rawData)
                    else -> emptyList()
                }
                val title = payload["title"]?.toString() ?: name
                if (comics.isNotEmpty()) {
                    parts.add(ExplorePagePart(title = title, comics = comics))
                }
            }
            Result.success(parts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getExploreComics(page: Int): Result<List<Comic>> {
        val res = loadExplorePage(0, page)
        return res.map { parts -> parts.flatMap { it.comics } }
    }

    override suspend fun getCategoryData(): Result<CategoryData?> {
        return try {
            val script = """
                return (function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.category) return null;
                    var cat = s.category;
                    var parts = (cat.parts || []).map(function(p) {
                        var categories = p.categories || [];
                        var params = p.categoryParams || [];
                        var itemType = p.itemType || "category";
                        var items = [];
                        for (var i = 0; i < categories.length; i++) {
                            var raw = categories[i];
                            var label = typeof raw === 'object' ? (raw.label || '') : String(raw);
                            var param = (params[i] !== undefined && params[i] !== null) ? String(params[i]) : null;
                            var target = null;
                            if (typeof raw === 'object' && raw.target) {
                                target = raw.target;
                            } else {
                                target = {
                                    page: itemType,
                                    attributes: {
                                        category: label,
                                        param: param,
                                        keyword: label
                                    }
                                };
                            }
                            items.push({ label: label, target: target });
                        }
                        return {
                            name: p.name || "",
                            type: p.type || "fixed",
                            items: items
                        };
                    });
                    var buttons = (cat.buttons || []).map(function(b) {
                        return { label: b.label || "", action: b.action || "" };
                    });
                    return {
                        title: cat.title || s.name,
                        key: cat.title || s.name,
                        enableRankingPage: !!cat.enableRankingPage,
                        parts: parts,
                        buttons: buttons,
                        sourceKey: s.key
                    };
                })()
            """.trimIndent()
            val rawJson = engine.evaluateAsync(script)
            val envelope = gson.fromJson<Map<String, Any?>>(rawJson, object : TypeToken<Map<String, Any?>>() {}.type)
            if (envelope["success"] != true || envelope["data"] == null) {
                return Result.success(null)
            }
            val data = envelope["data"] as? Map<*, *> ?: return Result.success(null)
            val title = data["title"]?.toString() ?: name
            val keyStr = data["key"]?.toString() ?: key
            val enableRanking = data["enableRankingPage"] == true
            val rawParts = (data["parts"] as? List<*>) ?: emptyList<Any>()
            val parts = rawParts.mapNotNull { p ->
                val pm = p as? Map<*, *> ?: return@mapNotNull null
                val partName = pm["name"]?.toString() ?: ""
                val partType = pm["type"]?.toString() ?: "fixed"
                val rawItems = (pm["items"] as? List<*>) ?: emptyList<Any>()
                val items = rawItems.mapNotNull { item ->
                    val im = item as? Map<*, *> ?: return@mapNotNull null
                    val label = im["label"]?.toString() ?: return@mapNotNull null
                    val tm = im["target"] as? Map<*, *>
                    val targetPage = tm?.get("page")?.toString() ?: "category"
                    @Suppress("UNCHECKED_CAST")
                    val attrs = tm?.get("attributes") as? Map<String, Any?>
                    CategoryItem(
                        label = label,
                        target = PageJumpTarget(
                            sourceKey = key,
                            page = targetPage,
                            attributes = attrs
                        )
                    )
                }
                CategoryPart(name = partName, type = partType, items = items)
            }
            val rawButtons = (data["buttons"] as? List<*>) ?: emptyList<Any>()
            val buttons = rawButtons.mapNotNull { b ->
                val bm = b as? Map<*, *> ?: return@mapNotNull null
                CategoryButtonData(
                    label = bm["label"]?.toString() ?: "",
                    action = bm["action"]?.toString() ?: ""
                )
            }
            Result.success(
                CategoryData(
                    title = title,
                    key = keyStr,
                    enableRankingPage = enableRanking,
                    parts = parts,
                    buttons = buttons,
                    sourceKey = key
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCategoryComicsOptions(category: String, param: String?): Result<List<CategoryComicsOption>> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.categoryComics) return [];
                    if (s.categoryComics.optionLoader) {
                        var res = await s.categoryComics.optionLoader(${gson.toJson(category)}, ${gson.toJson(param)});
                        return res || [];
                    }
                    return s.categoryComics.optionList || [];
                })()
            """.trimIndent()
            val rawJson = engine.evaluateAsync(script)
            val envelope = gson.fromJson<Map<String, Any?>>(rawJson, object : TypeToken<Map<String, Any?>>() {}.type)
            if (envelope["success"] != true) return Result.success(emptyList())
            val list = envelope["data"] as? List<*> ?: return Result.success(emptyList())
            val options = list.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val label = m["label"]?.toString() ?: ""
                val rawOptions = (m["options"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val optionsMap = linkedMapOf<String, String>()
                for (opt in rawOptions) {
                    if (opt.contains("-")) {
                        val split = opt.split("-", limit = 2)
                        optionsMap[split[0]] = split[1]
                    } else {
                        optionsMap[opt] = opt
                    }
                }
                val notShowWhen = (m["notShowWhen"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val showWhen = (m["showWhen"] as? List<*>)?.mapNotNull { it?.toString() }
                CategoryComicsOption(
                    label = label,
                    options = optionsMap,
                    notShowWhen = notShowWhen,
                    showWhen = showWhen
                )
            }
            Result.success(options)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadCategoryComics(
        category: String,
        param: String?,
        options: List<String>,
        page: Int
    ): Result<CategoryComicsResult> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.categoryComics || !s.categoryComics.load) throw new Error("categoryComics not implemented");
                    var res = await s.categoryComics.load(
                        ${gson.toJson(category)},
                        ${gson.toJson(param)},
                        ${gson.toJson(options)},
                        $page
                    );
                    return res;
                })()
            """.trimIndent()
            val rawJson = engine.evaluateAsync(script)
            val envelope = gson.fromJson<Map<String, Any?>>(rawJson, object : TypeToken<Map<String, Any?>>() {}.type)
            if (envelope["success"] != true) {
                return Result.failure(Exception(envelope["error"]?.toString() ?: "loadCategoryComics failed"))
            }
            val res = envelope["data"]
            val comicListRaw = when (res) {
                is Map<*, *> -> res["comics"] as? List<*>
                is List<*> -> res
                else -> null
            } ?: emptyList<Any>()

            val maxPage = (res as? Map<*, *>)?.get("maxPage")?.let { (it as? Number)?.toInt() }
            val next = (res as? Map<*, *>)?.get("next")?.toString()

            val comics = comicListRaw.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val id = m["id"]?.toString() ?: m["path_word"]?.toString() ?: return@mapNotNull null
                val title = m["title"]?.toString() ?: m["name"]?.toString() ?: ""
                val subTitle = m["subTitle"]?.toString() ?: m["author"]?.toString() ?: ""
                val cover = m["cover"]?.toString() ?: ""
                val tags = (m["tags"] as? List<*>)?.mapNotNull { it?.toString() }
                    ?: (m["theme"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get("name")?.toString() }
                    ?: emptyList()
                Comic(
                    id = id,
                    title = title,
                    subTitle = subTitle,
                    cover = cover,
                    sourceKey = key,
                    tags = tags,
                    rating = SourcePayloadParser.rating(m),
                    likesCount = SourcePayloadParser.likesCount(m)
                )
            }
            Result.success(CategoryComicsResult(comics = comics, maxPage = maxPage, next = next))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadCategoryRanking(option: String, page: Int): Result<List<Comic>> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.categoryComics || !s.categoryComics.ranking) return [];
                    var r = s.categoryComics.ranking;
                    if (r.load) {
                        return await r.load(${gson.toJson(option)}, $page);
                    } else if (r.loadWithNext) {
                        return await r.loadWithNext(${gson.toJson(option)}, $page == 1 ? null : String($page));
                    }
                    return [];
                })()
            """.trimIndent()
            val rawJson = engine.evaluateAsync(script)
            val envelope = gson.fromJson<Map<String, Any?>>(rawJson, object : TypeToken<Map<String, Any?>>() {}.type)
            if (envelope["success"] != true) return Result.success(emptyList())
            val res = envelope["data"]
            val rawList = when (res) {
                is Map<*, *> -> res["comics"] as? List<*>
                is List<*> -> res
                else -> null
            } ?: emptyList<Any>()
            val comics = rawList.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                val id = m["id"]?.toString() ?: m["path_word"]?.toString() ?: return@mapNotNull null
                val title = m["title"]?.toString() ?: m["name"]?.toString() ?: ""
                val subTitle = m["subTitle"]?.toString() ?: m["author"]?.toString() ?: ""
                val cover = m["cover"]?.toString() ?: ""
                Comic(id = id, title = title, subTitle = subTitle, cover = cover, sourceKey = key, rating = SourcePayloadParser.rating(m), likesCount = SourcePayloadParser.likesCount(m))
            }
            Result.success(comics)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCommentCapabilities(): com.venera.compose.source.model.CommentCapabilities {
        val script = """
            return (async function() {
                var s = ComicSource.sources[${gson.toJson(key)}];
                return {
                    canLoad: !!s && (typeof s.comic?.loadComments === 'function' || typeof s.comments?.load === 'function' || typeof s.comments === 'function'),
                    canSend: !!s && (typeof s.comic?.sendComment === 'function' || typeof s.comments?.send === 'function')
                };
            })()
        """.trimIndent()
        return try {
            val data = SourcePayloadParser.data(engine.evaluateAsync(script)) as? Map<*, *>
            com.venera.compose.source.model.CommentCapabilities(data?.get("canLoad") == true, data?.get("canSend") == true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("JsComicSource", "Unable to read comment capabilities for $key", e)
            com.venera.compose.source.model.CommentCapabilities()
        }
    }

    override suspend fun loadComments(comicId: String, subId: String?, page: Int, replyId: String?): Result<List<com.venera.compose.source.model.Comment>> =
        loadCommentsPage(comicId, subId, page, replyId).map { it.comments }

    override suspend fun loadCommentsPage(comicId: String, subId: String?, page: Int, replyId: String?): Result<com.venera.compose.source.model.CommentPage> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources[${gson.toJson(key)}];
                    var args = [${gson.toJson(comicId)}, ${gson.toJson(subId)}, $page, ${gson.toJson(replyId)}];
                    if (typeof s?.comic?.loadComments === 'function') return await s.comic.loadComments(...args);
                    if (typeof s?.comments?.load === 'function') return await s.comments.load(...args);
                    if (typeof s?.comments === 'function') return await s.comments(...args);
                    throw new Error("当前源暂不支持加载评论");
                })()
            """.trimIndent()
            val data = SourcePayloadParser.data(engine.evaluateAsync(script))
            val map = data as? Map<*, *>
            val rawList = when (data) {
                is List<*> -> data
                is Map<*, *> -> (data["comments"] ?: data["list"]) as? List<*> ?: error("评论响应缺少列表")
                else -> error("评论响应格式错误")
            }
            val comments = rawList.mapNotNull { (it as? Map<*, *>)?.let(SourcePayloadParser::comment) }
            val maxPage = map?.get("maxPage")?.toString()?.toDoubleOrNull()?.toInt()?.takeIf { it >= 0 }
            Result.success(com.venera.compose.source.model.CommentPage(comments, maxPage))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendComment(comicId: String, subId: String?, content: String, replyId: String?): Result<Boolean> = commentAction(
        """
            var args = [${gson.toJson(comicId)}, ${gson.toJson(subId)}, ${gson.toJson(content)}, ${gson.toJson(replyId)}];
            if (typeof s?.comic?.sendComment === 'function') return await s.comic.sendComment(...args);
            if (typeof s?.comments?.send === 'function') return await s.comments.send(...args);
            throw new Error("当前源暂不支持发送评论");
        """.trimIndent()
    )

    override suspend fun likeComment(commentId: String, comicId: String, subId: String?, isLike: Boolean): Result<Boolean> = commentAction(
        """
            if (typeof s?.comic?.likeComment === 'function') return await s.comic.likeComment(${gson.toJson(comicId)}, ${gson.toJson(subId)}, ${gson.toJson(commentId)}, $isLike);
            if (typeof s?.comments?.like === 'function') return await s.comments.like(${gson.toJson(commentId)}, ${gson.toJson(comicId)}, ${gson.toJson(subId)}, $isLike);
            throw new Error("当前源暂不支持评论点赞");
        """.trimIndent()
    )

    override suspend fun voteComment(commentId: String, isUpvote: Boolean, comicId: String, subId: String?, isCancel: Boolean): Result<Boolean> = commentAction(
        """
            if (typeof s?.comic?.voteComment === 'function') return await s.comic.voteComment(${gson.toJson(comicId)}, ${gson.toJson(subId)}, ${gson.toJson(commentId)}, $isUpvote, $isCancel);
            if (typeof s?.comments?.vote === 'function') return await s.comments.vote(${gson.toJson(commentId)}, ${if (isUpvote) 1 else -1}, ${gson.toJson(comicId)}, ${gson.toJson(subId)}, $isCancel);
            throw new Error("当前源暂不支持评论投票");
        """.trimIndent()
    )

    /** JS actions may return void or a numeric score; errors must never become success. */
    private suspend fun commentAction(body: String): Result<Boolean> = try {
        val script = """
            return (async function() {
                var s = ComicSource.sources[${gson.toJson(key)}];
                var result = await (async function() { $body })();
                return result !== false;
            })()
        """.trimIndent()
        Result.success(SourcePayloadParser.data(engine.evaluateAsync(script)) == true)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun starRating(comicId: String, rating: Float): Result<Boolean> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (s && s.comic && s.comic.star) {
                        await s.comic.star(${gson.toJson(comicId)}, $rating);
                    }
                    return true;
                })()
            """.trimIndent()
            engine.evaluateAsync(script)
            Result.success(true)
        } catch (e: Exception) {
            Result.success(true)
        }
    }

    override suspend fun likeComic(comicId: String): Result<Boolean> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (s && s.comic && s.comic.like) {
                        await s.comic.like(${gson.toJson(comicId)});
                    }
                    return true;
                })()
            """.trimIndent()
            engine.evaluateAsync(script)
            Result.success(true)
        } catch (e: Exception) {
            Result.success(true)
        }
    }

    override fun getSettings(): List<SourceSettingItem> {
        return try {
            val script = """
                (function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.settings) return '[]';
                    var res = [];
                    for (var k in s.settings) {
                        var item = s.settings[k];
                        if (!item) continue;
                        res.push({
                            key: k,
                            title: item.title || k,
                            type: item.type || 'input',
                            default: item.default !== undefined ? item.default : null,
                            options: item.options || [],
                            validator: item.validator || null,
                            buttonText: item.buttonText || '点击执行'
                        });
                    }
                    return JSON.stringify(res);
                })()
            """.trimIndent()
            val rawJson = engine.evaluate(script)
            if (rawJson.isNullOrBlank() || rawJson == "null") return emptyList()
            val listType = object : TypeToken<List<Map<String, Any?>>>() {}.type
            val rawList: List<Map<String, Any?>> = gson.fromJson(rawJson, listType) ?: emptyList()
            val dataStore = engine.dataStore

            rawList.mapNotNull { item ->
                val itemKey = item["key"]?.toString() ?: return@mapNotNull null
                val title = item["title"]?.toString() ?: itemKey
                val type = item["type"]?.toString() ?: "input"
                val defaultVal = item["default"]
                val savedVal = dataStore.loadSetting(key, itemKey) ?: defaultVal

                when (type) {
                    "select" -> {
                        val rawOptions = item["options"] as? List<*> ?: emptyList<Any?>()
                        val options = rawOptions.mapNotNull { opt ->
                            val optMap = opt as? Map<*, *> ?: return@mapNotNull null
                            val v = optMap["value"]?.toString() ?: return@mapNotNull null
                            val text = optMap["text"]?.toString() ?: v
                            SelectOption(value = v, text = text)
                        }
                        SourceSettingItem.Select(
                            key = itemKey,
                            title = title,
                            value = savedVal?.toString() ?: (defaultVal?.toString() ?: ""),
                            defaultValue = defaultVal?.toString() ?: "",
                            options = options
                        )
                    }
                    "switch" -> {
                        val isChecked = when (savedVal) {
                            is Boolean -> savedVal
                            is Number -> savedVal.toInt() != 0
                            is String -> savedVal.equals("true", ignoreCase = true)
                            else -> defaultVal as? Boolean ?: false
                        }
                        SourceSettingItem.Switch(
                            key = itemKey,
                            title = title,
                            value = isChecked,
                            defaultValue = defaultVal as? Boolean ?: false
                        )
                    }
                    "callback" -> {
                        val btnText = item["buttonText"]?.toString() ?: "点击执行"
                        SourceSettingItem.Callback(
                            key = itemKey,
                            title = title,
                            buttonText = btnText
                        )
                    }
                    else -> { // input
                        SourceSettingItem.Input(
                            key = itemKey,
                            title = title,
                            value = savedVal?.toString() ?: (defaultVal?.toString() ?: ""),
                            defaultValue = defaultVal?.toString() ?: "",
                            validator = item["validator"]?.toString()
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("JsComicSource", "Failed to getSettings for $key", e)
            emptyList()
        }
    }

    override fun saveSetting(key: String, value: Any) {
        engine.dataStore.saveSetting(this.key, key, value)
    }

    override suspend fun executeSettingCallback(key: String): Result<Unit> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['${this.key}'];
                    if (s && s.settings && s.settings['$key'] && typeof s.settings['$key'].callback === 'function') {
                        await s.settings['$key'].callback();
                    }
                    return true;
                })()
            """.trimIndent()
            engine.evaluateAsync(script)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getAccountInfo(): SourceAccountInfo {
        return try {
            val script = """
                (function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.account) return JSON.stringify({ hasAccount: false });
                    var items = [];
                    if (s.account.infoItems && Array.isArray(s.account.infoItems)) {
                        for (var i = 0; i < s.account.infoItems.length; i++) {
                            var item = s.account.infoItems[i];
                            var val = "";
                            if (typeof item.data === 'function') {
                                try { val = item.data() || ""; } catch(e) {}
                            } else if (item.data) {
                                val = item.data.toString();
                            }
                            items.push({ title: item.title || "", data: val });
                        }
                    }
                    var web = s.account.loginWithWebview || null;
                    var ck = s.account.loginWithCookies || null;
                    return JSON.stringify({
                        hasAccount: true,
                        supportsPasswordLogin: typeof s.account.login === 'function',
                        loginWebsite: (web && web.url) ? web.url : null,
                        registerWebsite: s.account.registerWebsite || null,
                        cookieFields: (ck && Array.isArray(ck.fields)) ? ck.fields : [],
                        infoItems: items
                    });
                })()
            """.trimIndent()
            val rawJson = engine.evaluate(script)
            if (rawJson.isNullOrBlank() || rawJson == "null") return SourceAccountInfo(hasAccount = false)
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val map: Map<String, Any?> = gson.fromJson(rawJson, type) ?: emptyMap()

            val hasAccount = map["hasAccount"] == true
            if (!hasAccount) return SourceAccountInfo(hasAccount = false)

            val isLogged = engine.dataStore.isLogged(key)
            val accountData = engine.dataStore.getAccount(key)
            val username = when (accountData) {
                is List<*> -> accountData.firstOrNull()?.toString()
                is Map<*, *> -> accountData["username"]?.toString() ?: accountData["name"]?.toString()
                else -> null
            }

            val rawInfo = map["infoItems"] as? List<*> ?: emptyList<Any?>()
            val infoItems = rawInfo.mapNotNull { item ->
                val itemMap = item as? Map<*, *> ?: return@mapNotNull null
                val t = itemMap["title"]?.toString() ?: return@mapNotNull null
                val d = itemMap["data"]?.toString() ?: ""
                t to d
            }

            val rawCookieFields = map["cookieFields"] as? List<*> ?: emptyList<Any?>()
            val cookieFields = rawCookieFields.mapNotNull { e ->
                e?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            }

            SourceAccountInfo(
                hasAccount = true,
                isLogged = isLogged,
                username = username,
                infoItems = infoItems,
                supportsPasswordLogin = map["supportsPasswordLogin"] == true,
                // 源可能用 getter 动态返回，故统一过滤掉 JS 侧的 "null" 字面量
                loginWebsite = map["loginWebsite"]?.toString()
                    ?.takeIf { it.isNotBlank() && it != "null" },
                registerWebsite = map["registerWebsite"]?.toString()
                    ?.takeIf { it.isNotBlank() && it != "null" },
                cookieFields = cookieFields
            )
        } catch (e: Exception) {
            SourceAccountInfo(hasAccount = false)
        }
    }

    override suspend fun login(username: String, password: String): Result<Boolean> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.account || !s.account.login) throw new Error("该源不支持密码登录");
                    var res = await s.account.login(${gson.toJson(username)}, ${gson.toJson(password)});
                    if (res && res.error) {
                        throw new Error(res.errorMessage || "登录失败");
                    }
                    return true;
                })()
            """.trimIndent()
            val rawJson = engine.evaluateAsync(script)
            val envelope = gson.fromJson<Map<String, Any?>>(rawJson, object : TypeToken<Map<String, Any?>>() {}.type)
            if (envelope["success"] != true) {
                return Result.failure(Exception(envelope["error"]?.toString() ?: "登录失败"))
            }
            engine.dataStore.saveAccount(key, listOf(username, password))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun relogin(): Result<Boolean> {
        val accountData = engine.dataStore.getAccount(key)
        if (accountData is List<*> && accountData.size >= 2) {
            val user = accountData[0]?.toString() ?: ""
            val pwd = accountData[1]?.toString() ?: ""
            if (user.isNotBlank() && pwd.isNotBlank()) {
                return login(user, pwd)
            }
        }
        return Result.failure(Exception("未发现已保存的账号密码凭证，请点击【登录】"))
    }

    override suspend fun logout(): Result<Boolean> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (s && s.account && typeof s.account.logout === 'function') {
                        await s.account.logout();
                    }
                    return true;
                })()
            """.trimIndent()
            engine.evaluateAsync(script)
            engine.dataStore.logout(key)
            Result.success(true)
        } catch (e: Exception) {
            engine.dataStore.logout(key)
            Result.success(true)
        }
    }

    /* ------------------------------------------------------------------ *
     * 网页登录（官方 account.loginWithWebview）
     * ------------------------------------------------------------------ */

    /**
     * 内嵌 WebView 当前 url/title 是否表示已登录成功。
     *
     * 官方在 WebView 的**每次导航或标题变化**时都会调用它，因此这里必须轻量、
     * 无副作用，且失败时返回 false 而不是抛异常 —— 否则每次页面跳转都会刷日志。
     */
    override suspend fun checkLoginStatus(url: String, title: String): Boolean {
        return try {
            val script = """
                (function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.account || !s.account.loginWithWebview) return false;
                    if (typeof s.account.loginWithWebview.checkStatus !== 'function') return false;
                    return s.account.loginWithWebview.checkStatus(
                        ${gson.toJson(url)}, ${gson.toJson(title)}) === true;
                })()
            """.trimIndent()
            engine.evaluate(script)?.trim()?.trim('"') == "true"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 网页登录成功后的源侧回调。
     *
     * **调用时机必须在 cookies 与 `_localStorage` 落库之后** ——
     * 源就是在这里二次加工它们的：
     * - ehentai：把 forums.e-hentai.org 的 cookie 改域后注入 exhentai.org
     * - ccc.js：从 `_localStorage["accessToken"]` 解析 JWT 并落库 token/expireTime
     */
    override suspend fun onWebLoginSuccess(): Result<Unit> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (s && s.account && s.account.loginWithWebview
                        && typeof s.account.loginWithWebview.onLoginSuccess === 'function') {
                        await s.account.loginWithWebview.onLoginSuccess();
                    }
                    return true;
                })()
            """.trimIndent()
            val env = evaluateEnvelope(script)
            if (env["success"] == true) Result.success(Unit)
            else Result.failure(Exception(env["error"]?.toString() ?: "onLoginSuccess 执行失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /* ------------------------------------------------------------------ *
     * Cookie 直填登录（官方 account.loginWithCookies）
     * ------------------------------------------------------------------ */

    override fun getCookieFields(): List<String> = getAccountInfo().cookieFields

    override suspend fun validateCookies(cookies: List<String>): Boolean {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.account || !s.account.loginWithCookies) return false;
                    if (typeof s.account.loginWithCookies.validate !== 'function') return false;
                    return (await s.account.loginWithCookies.validate(${gson.toJson(cookies)})) === true;
                })()
            """.trimIndent()
            val env = evaluateEnvelope(script)
            val data = env["data"]
            env["success"] == true && (data == true || data?.toString() == "true")
        } catch (e: Exception) {
            false
        }
    }

    /** 执行 `return (async function(){...})()` 形式的脚本并解析出 `{success,data,error}` 信封 */
    private suspend fun evaluateEnvelope(script: String): Map<String, Any?> {
        val raw = engine.evaluateAsync(script)
        return try {
            gson.fromJson(raw, object : TypeToken<Map<String, Any?>>() {}.type) ?: emptyMap()
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "无法解析 JS 执行结果")
        }
    }
}
