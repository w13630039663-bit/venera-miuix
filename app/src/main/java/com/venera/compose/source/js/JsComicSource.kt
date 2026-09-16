package com.venera.compose.source.js

import com.google.gson.reflect.TypeToken
import com.venera.compose.engine.VeneraJsEngine
import com.venera.compose.source.ComicSource
import com.venera.compose.source.model.ChapterPages
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ComicChapter
import com.venera.compose.source.model.ComicDetails

class JsComicSource(
    private val engine: VeneraJsEngine,
    override val key: String,
    override val name: String,
    override val version: String,
    override val iconUrl: String? = null
) : ComicSource {

    private val gson = engine.gson

    override suspend fun ping(): Long {
        val start = System.currentTimeMillis()
        return try {
            val res = search("test", 1)
            if (res.isSuccess) System.currentTimeMillis() - start else -1L
        } catch (e: Exception) {
            -1L
        }
    }

    override suspend fun search(keyword: String, page: Int): Result<List<Comic>> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.search) return { comics: [] };
                    var res = null;
                    if (s.search.load) {
                        res = await s.search.load(${gson.toJson(keyword)}, null, $page);
                    } else if (s.search.loadNext) {
                        res = await s.search.loadNext(${gson.toJson(keyword)}, null, null);
                    }
                    return res || { comics: [] };
                })()
            """.trimIndent()

            val rawJson = engine.evaluateAsync(script)
            val envelope = gson.fromJson<Map<String, Any?>>(rawJson, object : TypeToken<Map<String, Any?>>() {}.type)
            if (envelope["success"] != true) {
                return Result.failure(Exception(envelope["error"]?.toString() ?: "search failed"))
            }
            val data = envelope["data"] as? Map<*, *> ?: return Result.success(emptyList())
            val comicsList = data["comics"] as? List<*> ?: return Result.success(emptyList())

            val comics = comicsList.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val id = map["id"]?.toString() ?: return@mapNotNull null
                val title = map["title"]?.toString() ?: ""
                val subTitle = map["subTitle"]?.toString() ?: ""
                val cover = map["cover"]?.toString() ?: ""
                val desc = map["description"]?.toString() ?: ""
                val tags = (map["tags"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val updateTime = map["updateTime"]?.toString() ?: ""

                Comic(
                    id = id,
                    title = title,
                    subTitle = subTitle,
                    cover = cover,
                    sourceKey = key,
                    tags = tags,
                    description = desc,
                    updateTime = updateTime
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
                return Result.failure(Exception(envelope["error"]?.toString() ?: "loadInfo failed"))
            }
            val res = envelope["data"] as? Map<*, *> ?: return Result.failure(Exception("invalid comic details"))

            val title = res["title"]?.toString() ?: ""
            val subTitle = res["subTitle"]?.toString() ?: (res["subtitle"]?.toString() ?: "")
            val cover = res["cover"]?.toString() ?: ""
            val desc = res["description"]?.toString() ?: ""
            val author = res["author"]?.toString() ?: (res["uploader"]?.toString() ?: "")

            val rawTags = res["tags"]
            val tags = when (rawTags) {
                is List<*> -> rawTags.mapNotNull { it?.toString() }
                is Map<*, *> -> rawTags.flatMap { (k, v) ->
                    if (v is List<*>) v.mapNotNull { it?.toString() } else listOfNotNull(v?.toString())
                }
                else -> emptyList()
            }

            val chapters = mutableListOf<ComicChapter>()
            val rawChapters = res["chapters"]
            if (rawChapters is Map<*, *>) {
                var order = 0
                for ((chKey, chVal) in rawChapters) {
                    if (chVal is Map<*, *>) {
                        for ((subKey, subVal) in chVal) {
                            chapters.add(ComicChapter(id = subKey.toString(), title = subVal.toString(), order = order++))
                        }
                    } else {
                        chapters.add(ComicChapter(id = chKey.toString(), title = chVal.toString(), order = order++))
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

            val comic = Comic(
                id = comicId,
                title = title,
                subTitle = subTitle,
                cover = cover,
                sourceKey = key,
                tags = tags,
                description = desc
            )

            val details = ComicDetails(
                comic = comic,
                author = author,
                status = res["status"]?.toString() ?: "连载中",
                rating = (res["stars"] as? Number)?.toFloat() ?: 0f,
                chapters = chapters,
                sourceKey = key
            )
            Result.success(details)
        } catch (e: Exception) {
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
                    return res;
                })()
            """.trimIndent()

            val rawJson = engine.evaluateAsync(script)
            val envelope = gson.fromJson<Map<String, Any?>>(rawJson, object : TypeToken<Map<String, Any?>>() {}.type)
            if (envelope["success"] != true) {
                return Result.failure(Exception(envelope["error"]?.toString() ?: "loadEp failed"))
            }
            val res = envelope["data"] as? Map<*, *> ?: return Result.failure(Exception("invalid chapter pages"))
            val rawImages = res["images"] as? List<*> ?: emptyList<Any?>()
            val pages = rawImages.mapNotNull { it?.toString() }

            val headers = (res["headers"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                if (k != null && v != null) k.toString() to v.toString() else null
            }?.toMap() ?: emptyMap()

            Result.success(
                ChapterPages(
                    comicId = comicId,
                    chapterId = chapterId,
                    pages = pages,
                    headers = headers
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getExploreComics(page: Int): Result<List<Comic>> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.explore || !s.explore.length) return [];
                    var p = s.explore[0];
                    var res = await (p.load ? p.load($page) : []);
                    return res || [];
                })()
            """.trimIndent()

            val rawJson = engine.evaluateAsync(script)
            val envelope = gson.fromJson<Map<String, Any?>>(rawJson, object : TypeToken<Map<String, Any?>>() {}.type)
            if (envelope["success"] != true) {
                return Result.failure(Exception(envelope["error"]?.toString() ?: "explore failed"))
            }
            val list = envelope["data"] as? List<*> ?: return Result.success(emptyList())
            val comics = list.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val id = map["id"]?.toString() ?: return@mapNotNull null
                val title = map["title"]?.toString() ?: ""
                val cover = map["cover"]?.toString() ?: ""
                Comic(
                    id = id,
                    title = title,
                    cover = cover,
                    sourceKey = key
                )
            }
            Result.success(comics)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
