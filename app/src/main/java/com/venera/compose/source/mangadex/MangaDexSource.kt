package com.venera.compose.source.mangadex

import android.content.Context
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.source.ComicSource
import com.venera.compose.source.model.ChapterPages
import com.venera.compose.source.model.Comic
import com.venera.compose.source.model.ComicChapter
import com.venera.compose.source.model.ComicDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * MangaDex 官方开源漫画源
 * 特点：全球最大开源漫画站、免登录全量开放 REST API、支持多语言检索与 At-Home CDN 图片
 */
class MangaDexSource(private val context: Context) : ComicSource {

    override val key: String = "manga_dex"
    override val name: String = "MangaDex"
    override val version: String = "1.2.0"
    override val iconUrl: String = "https://mangadex.org/favicon.ico"

    private val networkClient by lazy { VeneraNetworkClient.getInstance(context) }
    private val baseUrl = "https://api.mangadex.org"

    override suspend fun ping(): Long = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val res = networkClient.get("$baseUrl/ping")
            if (res.contains("pong", ignoreCase = true)) {
                System.currentTimeMillis() - start
            } else {
                -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }

    override suspend fun search(keyword: String, page: Int, options: List<String?>?): Result<List<Comic>> = withContext(Dispatchers.IO) {
        runCatching {
            val offset = (page - 1) * 20
            val encodedTitle = URLEncoder.encode(keyword, "UTF-8")
            val url = "$baseUrl/manga?title=$encodedTitle&limit=20&offset=$offset&includes[]=cover_art&contentRating[]=safe&contentRating[]=suggestive&order[relevance]=desc"
            val responseText = networkClient.get(url)
            val json = JSONObject(responseText)
            val dataArray = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<Comic>()

            for (i in 0 until dataArray.length()) {
                val item = dataArray.getJSONObject(i)
                val id = item.getString("id")
                val attributes = item.optJSONObject("attributes") ?: JSONObject()
                val titleObj = attributes.optJSONObject("title") ?: JSONObject()
                val title = pickTitle(titleObj, attributes.optJSONArray("altTitles"))
                val descriptionObj = attributes.optJSONObject("description") ?: JSONObject()
                val desc = descriptionObj.optString("zh")
                    .ifEmpty { descriptionObj.optString("zh-hk") }
                    .ifEmpty { descriptionObj.optString("en") }
                    .ifEmpty { descriptionObj.optString("ja") }

                val coverFileName = findCoverFileName(item.optJSONArray("relationships"))
                val coverUrl = if (coverFileName.isNotEmpty()) {
                    "https://uploads.mangadex.org/covers/$id/$coverFileName.256.jpg"
                } else {
                    ""
                }

                val tags = mutableListOf<String>()
                val tagsArr = attributes.optJSONArray("tags") ?: JSONArray()
                for (t in 0 until tagsArr.length()) {
                    val tagItem = tagsArr.getJSONObject(t)
                    val tagName = tagItem.optJSONObject("attributes")?.optJSONObject("name")?.optString("en")
                    if (!tagName.isNullOrEmpty()) tags.add(tagName)
                }

                list.add(
                    Comic(
                        id = id,
                        title = title,
                        subTitle = tags.take(2).joinToString(" · "),
                        cover = coverUrl,
                        sourceKey = key,
                        tags = tags,
                        description = desc,
                        updateTime = attributes.optString("updatedAt").take(10)
                    )
                )
            }
            list
        }
    }

    override suspend fun getComicDetails(comicId: String): Result<ComicDetails> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. 获取漫画元数据
            val mangaUrl = "$baseUrl/manga/$comicId?includes[]=cover_art&includes[]=author"
            val mangaRes = networkClient.get(mangaUrl)
            val mangaJson = JSONObject(mangaRes)
            val mangaData = mangaJson.getJSONObject("data")
            val attributes = mangaData.optJSONObject("attributes") ?: JSONObject()
            val titleObj = attributes.optJSONObject("title") ?: JSONObject()
            val title = pickTitle(titleObj, attributes.optJSONArray("altTitles"))
            val descObj = attributes.optJSONObject("description") ?: JSONObject()
            val desc = descObj.optString("zh")
                .ifEmpty { descObj.optString("zh-hk") }
                .ifEmpty { descObj.optString("en") }
                .ifEmpty { descObj.optString("ja") }

            val coverFileName = findCoverFileName(mangaData.optJSONArray("relationships"))
            val coverUrl = if (coverFileName.isNotEmpty()) {
                "https://uploads.mangadex.org/covers/$comicId/$coverFileName.512.jpg"
            } else {
                ""
            }

            val author = findAuthorName(mangaData.optJSONArray("relationships"))
            val status = when (attributes.optString("status")) {
                "completed" -> "已完结"
                "ongoing" -> "连载中"
                "cancelled" -> "已取消"
                "hiatus" -> "休刊中"
                else -> "连载中"
            }

            val tags = mutableListOf<String>()
            val tagsArr = attributes.optJSONArray("tags") ?: JSONArray()
            for (t in 0 until tagsArr.length()) {
                val tagItem = tagsArr.getJSONObject(t)
                val tagName = tagItem.optJSONObject("attributes")?.optJSONObject("name")?.optString("en")
                if (!tagName.isNullOrEmpty()) tags.add(tagName)
            }

            // 2. 获取章节 Feed
            val feedUrl = "$baseUrl/manga/$comicId/feed?limit=100&order[chapter]=asc&contentRating[]=safe&contentRating[]=suggestive"
            val feedRes = networkClient.get(feedUrl)
            val feedJson = JSONObject(feedRes)
            val feedArr = feedJson.optJSONArray("data") ?: JSONArray()
            val chapters = mutableListOf<ComicChapter>()

            for (i in 0 until feedArr.length()) {
                val chItem = feedArr.getJSONObject(i)
                val chId = chItem.getString("id")
                val chAttr = chItem.optJSONObject("attributes") ?: JSONObject()
                val chNum = chAttr.optString("chapter").ifEmpty { "${i + 1}" }
                val chTitle = chAttr.optString("title")
                val displayTitle = if (chTitle.isNotEmpty()) "第 $chNum 话 · $chTitle" else "第 $chNum 话"
                val updatedAt = chAttr.optString("updatedAt").take(10)

                chapters.add(
                    ComicChapter(
                        id = chId,
                        title = displayTitle,
                        order = i + 1,
                        updatedAt = updatedAt
                    )
                )
            }

            ComicDetails(
                comic = Comic(
                    id = comicId,
                    title = title,
                    subTitle = author,
                    cover = coverUrl,
                    sourceKey = key,
                    tags = tags,
                    description = desc,
                    updateTime = attributes.optString("updatedAt").take(10)
                ),
                author = author,
                status = status,
                rating = 0f,
                chapters = chapters,
                thumbnails = emptyList()
            )
        }
    }

    override suspend fun getChapterPages(comicId: String, chapterId: String): Result<ChapterPages> = withContext(Dispatchers.IO) {
        runCatching {
            val atHomeUrl = "$baseUrl/at-home/server/$chapterId"
            val resText = networkClient.get(atHomeUrl)
            val json = JSONObject(resText)
            val atHomeBaseUrl = json.getString("baseUrl")
            val chapterObj = json.getJSONObject("chapter")
            val hash = chapterObj.getString("hash")
            val dataArr = chapterObj.getJSONArray("data")
            val pageUrls = mutableListOf<String>()

            for (i in 0 until dataArr.length()) {
                val fileName = dataArr.getString(i)
                pageUrls.add("$atHomeBaseUrl/data/$hash/$fileName")
            }

            ChapterPages(
                comicId = comicId,
                chapterId = chapterId,
                chapterTitle = "",
                pages = pageUrls
            )
        }
    }

    override suspend fun getExploreComics(page: Int): Result<List<Comic>> = withContext(Dispatchers.IO) {
        runCatching {
            val offset = (page - 1) * 20
            val url = "$baseUrl/manga?limit=20&offset=$offset&includes[]=cover_art&order[followedCount]=desc&contentRating[]=safe&contentRating[]=suggestive"
            val responseText = networkClient.get(url)
            val json = JSONObject(responseText)
            val dataArray = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<Comic>()

            for (i in 0 until dataArray.length()) {
                val item = dataArray.getJSONObject(i)
                val id = item.getString("id")
                val attributes = item.optJSONObject("attributes") ?: JSONObject()
                val titleObj = attributes.optJSONObject("title") ?: JSONObject()
                val title = pickTitle(titleObj, attributes.optJSONArray("altTitles"))
                val coverFileName = findCoverFileName(item.optJSONArray("relationships"))
                val coverUrl = if (coverFileName.isNotEmpty()) {
                    "https://uploads.mangadex.org/covers/$id/$coverFileName.256.jpg"
                } else {
                    ""
                }

                list.add(
                    Comic(
                        id = id,
                        title = title,
                        subTitle = "🔥 热门收录",
                        cover = coverUrl,
                        sourceKey = key,
                        description = attributes.optJSONObject("description")?.optString("en") ?: "",
                        updateTime = attributes.optString("updatedAt").take(10)
                    )
                )
            }
            list
        }
    }

    private fun pickTitle(titleObj: JSONObject, altTitles: JSONArray?): String {
        var title = titleObj.optString("zh")
        if (title.isEmpty()) title = titleObj.optString("zh-hk")
        if (title.isEmpty()) title = titleObj.optString("zh-tw")
        if (title.isEmpty()) title = titleObj.optString("en")
        if (title.isEmpty()) title = titleObj.optString("ja")
        if (title.isEmpty() && titleObj.keys().hasNext()) {
            title = titleObj.optString(titleObj.keys().next())
        }

        if (title.isEmpty() && altTitles != null) {
            for (i in 0 until altTitles.length()) {
                val alt = altTitles.optJSONObject(i) ?: continue
                val t = alt.optString("zh").ifEmpty { alt.optString("zh-hk") }.ifEmpty { alt.optString("en") }
                if (t.isNotEmpty()) return t
            }
        }
        return title.ifEmpty { "未知标题" }
    }

    private fun findCoverFileName(relationships: JSONArray?): String {
        if (relationships == null) return ""
        for (i in 0 until relationships.length()) {
            val rel = relationships.getJSONObject(i)
            if (rel.optString("type") == "cover_art") {
                val attr = rel.optJSONObject("attributes")
                val fileName = attr?.optString("fileName")
                if (!fileName.isNullOrEmpty()) return fileName
            }
        }
        return ""
    }

    private fun findAuthorName(relationships: JSONArray?): String {
        if (relationships == null) return "未知作者"
        for (i in 0 until relationships.length()) {
            val rel = relationships.getJSONObject(i)
            if (rel.optString("type") == "author" || rel.optString("type") == "artist") {
                val attr = rel.optJSONObject("attributes")
                val name = attr?.optString("name")
                if (!name.isNullOrEmpty()) return name
            }
        }
        return "未知作者"
    }
}
