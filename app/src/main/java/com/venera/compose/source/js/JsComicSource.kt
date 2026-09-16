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
                Comic(id = id, title = recTitle, cover = recCover, sourceKey = key)
            }.orEmpty()

            // 缩略图
            val thumbnails = (res["thumbnails"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()

            // 预览评论
            val comments = (res["comments"] as? List<*>)?.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val userName = map["userName"]?.toString() ?: "读者"
                val content = map["content"]?.toString() ?: return@mapNotNull null
                val avatar = map["avatar"]?.toString()
                val time = map["time"]?.toString()
                val score = (map["score"] as? Number)?.toInt() ?: 0
                val vote = (map["voteStatus"] as? Number)?.toInt() ?: 0
                val chId = map["id"]?.toString() ?: ""
                val isLiked = map["isLiked"] == true
                com.venera.compose.source.model.Comment(
                    id = chId,
                    userName = userName,
                    avatar = avatar,
                    content = content,
                    time = time,
                    score = score,
                    voteStatus = vote,
                    isLiked = isLiked
                )
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
                isFavorite = res["isFavorite"] == true
            )

            val details = ComicDetails(
                comic = comic,
                author = author,
                status = res["status"]?.toString() ?: "连载中",
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

    override suspend fun loadComments(comicId: String, subId: String?, page: Int): Result<List<com.venera.compose.source.model.Comment>> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.comments) return { comments: [] };
                    var res = null;
                    if (s.comments.load) {
                        res = await s.comments.load(${gson.toJson(comicId)}, ${gson.toJson(subId)}, $page);
                    } else if (typeof s.comments === 'function') {
                        res = await s.comments(${gson.toJson(comicId)}, ${gson.toJson(subId)}, $page);
                    }
                    return res || { comments: [] };
                })()
            """.trimIndent()
            val rawJson = engine.evaluateAsync(script)
            val envelope = gson.fromJson<Map<String, Any?>>(rawJson, object : TypeToken<Map<String, Any?>>() {}.type)
            if (envelope["success"] != true) return Result.success(emptyList())
            val data = envelope["data"]
            val rawList = when (data) {
                is List<*> -> data
                is Map<*, *> -> (data["comments"] as? List<*>) ?: (data["list"] as? List<*>) ?: emptyList<Any?>()
                else -> emptyList<Any?>()
            }
            val comments = rawList.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                com.venera.compose.source.model.Comment(
                    id = map["id"]?.toString() ?: "",
                    userName = map["userName"]?.toString() ?: "读者",
                    avatar = map["avatar"]?.toString(),
                    content = map["content"]?.toString() ?: return@mapNotNull null,
                    time = map["time"]?.toString(),
                    score = (map["score"] as? Number)?.toInt() ?: 0,
                    voteStatus = (map["voteStatus"] as? Number)?.toInt() ?: 0,
                    isLiked = map["isLiked"] == true
                )
            }
            Result.success(comments)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendComment(comicId: String, subId: String?, content: String): Result<Boolean> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.comments || !s.comments.send) throw new Error("comments.send not supported");
                    var res = await s.comments.send(${gson.toJson(comicId)}, ${gson.toJson(subId)}, ${gson.toJson(content)});
                    return res !== false;
                })()
            """.trimIndent()
            val rawJson = engine.evaluateAsync(script)
            val envelope = gson.fromJson<Map<String, Any?>>(rawJson, object : TypeToken<Map<String, Any?>>() {}.type)
            if (envelope["success"] == true) Result.success(true) else Result.failure(Exception(envelope["error"]?.toString()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun likeComment(commentId: String, comicId: String): Result<Boolean> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (s && s.comments && s.comments.like) {
                        await s.comments.like(${gson.toJson(commentId)}, ${gson.toJson(comicId)});
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

    override suspend fun voteComment(commentId: String, isUpvote: Boolean, comicId: String): Result<Boolean> {
        return try {
            val script = """
                return (async function() {
                    var s = ComicSource.sources['$key'];
                    if (s && s.comments && s.comments.vote) {
                        await s.comments.vote(${gson.toJson(commentId)}, ${if (isUpvote) 1 else -1}, ${gson.toJson(comicId)});
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
}
