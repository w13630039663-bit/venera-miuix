package com.venera.compose.source.copymanga

import android.content.Context
import android.util.Base64
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * 拷贝漫画 (CopyManga) 漫画源
 * 特点：国内主流海量中文化漫画平台，通过逆向 App HMAC-SHA256 签名与时间戳完成鉴权访问
 */
class CopyMangaSource(private val context: Context) : ComicSource {

    override val key: String = "copy_manga"
    override val name: String = "拷贝漫画"
    override val version: String = "1.4.2"
    override val iconUrl: String = "https://www.copymanga.tv/favicon.ico"

    private val networkClient by lazy { VeneraNetworkClient.getInstance(context) }
    private val apiUrl = "https://api.copy2000.online"

    private val secretBase64 = "M2FmMDg1OTAzMTEwMzJlZmUwNjYwNTUwYTA1NjNhNTM="
    private val deviceInfo = "${Random.nextInt(1000000, 9999999)}V-${Random.nextInt(1000, 9999)}"
    private val pseudoId by lazy {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        (1..16).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private fun buildHeaders(): Map<String, String> {
        val keyBytes = Base64.decode(secretBase64, Base64.DEFAULT)
        val ts = (System.currentTimeMillis() / 1000).toString()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        val sigBytes = mac.doFinal(ts.toByteArray(Charsets.UTF_8))
        val sig = sigBytes.joinToString("") { "%02x".format(it) }

        val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
        val dt = sdf.format(Date())

        return mapOf(
            "User-Agent" to "COPY/3.0.6",
            "source" to "copyApp",
            "deviceinfo" to deviceInfo,
            "dt" to dt,
            "platform" to "3",
            "referer" to "com.copymanga.app-3.0.6",
            "version" to "3.0.6",
            "device" to "AB1C.123456.001",
            "pseudoid" to pseudoId,
            "Accept" to "application/json",
            "region" to "0",
            "authorization" to "Token",
            "umstring" to "b4c89ca4104ea9a97750314d791520ac",
            "x-auth-timestamp" to ts,
            "x-auth-signature" to sig
        )
    }

    override suspend fun ping(): Long = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val url = "$apiUrl/api/v3/search/comic?limit=1&offset=0&q=test"
            val res = networkClient.get(url, buildHeaders())
            if (res.contains("\"code\":200") || res.contains("\"results\":")) {
                System.currentTimeMillis() - start
            } else {
                -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }

    override suspend fun search(keyword: String, page: Int): Result<List<Comic>> = withContext(Dispatchers.IO) {
        runCatching {
            val offset = (page - 1) * 20
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "$apiUrl/api/v3/search/comic?limit=20&offset=$offset&q=$encoded"
            val responseText = networkClient.get(url, buildHeaders())
            val json = JSONObject(responseText)
            val resultsObj = json.optJSONObject("results") ?: JSONObject()
            val listArr = resultsObj.optJSONArray("list") ?: JSONArray()
            val list = mutableListOf<Comic>()

            for (i in 0 until listArr.length()) {
                val item = listArr.getJSONObject(i)
                val pathWord = item.optString("path_word")
                val title = item.optString("name")
                val cover = item.optString("cover")
                val updateTime = item.optString("datetime_updated").take(10)

                val authors = mutableListOf<String>()
                val authorArr = item.optJSONArray("author") ?: JSONArray()
                for (a in 0 until authorArr.length()) {
                    val aObj = authorArr.getJSONObject(a)
                    authors.add(aObj.optString("name"))
                }

                val tags = mutableListOf<String>()
                val themeArr = item.optJSONArray("theme") ?: JSONArray()
                for (t in 0 until themeArr.length()) {
                    val tObj = themeArr.getJSONObject(t)
                    tags.add(tObj.optString("name"))
                }

                list.add(
                    Comic(
                        id = pathWord,
                        title = title,
                        subTitle = authors.joinToString(", ").ifEmpty { "拷贝精选" },
                        cover = cover,
                        sourceKey = key,
                        tags = tags,
                        description = item.optString("brief"),
                        updateTime = updateTime
                    )
                )
            }
            list
        }
    }

    override suspend fun getComicDetails(comicId: String): Result<ComicDetails> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. 请求漫画详情基础信息
            val infoUrl = "$apiUrl/api/v3/comic2/$comicId?in_mainland=true&platform=3"
            var title = comicId
            var cover = ""
            var author = "未知作者"
            var desc = ""
            var status = "连载中"
            val tags = mutableListOf<String>()

            try {
                val infoRes = networkClient.get(infoUrl, buildHeaders())
                val infoJson = JSONObject(infoRes)
                val cObj = infoJson.optJSONObject("results")?.optJSONObject("comic")
                if (cObj != null) {
                    title = cObj.optString("name", comicId)
                    cover = cObj.optString("cover", "")
                    desc = cObj.optString("brief", "")
                    val authorArr = cObj.optJSONArray("author")
                    if (authorArr != null && authorArr.length() > 0) {
                        author = authorArr.getJSONObject(0).optString("name", author)
                    }
                    val themeArr = cObj.optJSONArray("theme")
                    if (themeArr != null) {
                        for (j in 0 until themeArr.length()) {
                            val tName = themeArr.getJSONObject(j).optString("name")
                            if (tName.isNotEmpty()) tags.add(tName)
                        }
                    }
                    val statusInt = cObj.optInt("status", 1)
                    status = if (statusInt == 0) "已完结" else "连载中"
                }
            } catch (e: Exception) {
                // 容错降级
            }

            // 2. 拉取章节列表
            val chaptersUrl = "$apiUrl/api/v3/comic/$comicId/group/default/chapters?limit=100&offset=0"
            val chaptersRes = networkClient.get(chaptersUrl, buildHeaders())
            val chJson = JSONObject(chaptersRes)
            val resultsObj = chJson.optJSONObject("results") ?: JSONObject()
            val listArr = resultsObj.optJSONArray("list") ?: JSONArray()
            val chapters = mutableListOf<ComicChapter>()

            for (i in 0 until listArr.length()) {
                val chItem = listArr.getJSONObject(i)
                val uuid = chItem.optString("uuid")
                val chName = chItem.optString("name")
                chapters.add(
                    ComicChapter(
                        id = uuid,
                        title = chName.ifEmpty { "第 ${i + 1} 话" },
                        order = i + 1,
                        updatedAt = chItem.optString("datetime_created").take(10)
                    )
                )
            }

            ComicDetails(
                comic = Comic(
                    id = comicId,
                    title = title,
                    subTitle = author,
                    cover = cover,
                    sourceKey = key,
                    tags = tags,
                    description = desc
                ),
                author = author,
                status = status,
                rating = 0f,
                chapters = chapters
            )
        }
    }

    override suspend fun getChapterPages(comicId: String, chapterId: String): Result<ChapterPages> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$apiUrl/api/v3/comic/$comicId/chapter2/$chapterId"
            val resText = networkClient.get(url, buildHeaders())
            val json = JSONObject(resText)
            val chapterObj = json.optJSONObject("results")?.optJSONObject("chapter") ?: JSONObject()
            val contentsArr = chapterObj.optJSONArray("contents") ?: JSONArray()
            val pages = mutableListOf<String>()

            for (i in 0 until contentsArr.length()) {
                val item = contentsArr.getJSONObject(i)
                val imgUrl = item.optString("url")
                if (imgUrl.isNotEmpty()) pages.add(imgUrl)
            }

            ChapterPages(
                comicId = comicId,
                chapterId = chapterId,
                pages = pages,
                headers = mapOf("Referer" to "com.copymanga.app-3.0.6")
            )
        }
    }

    override suspend fun getExploreComics(page: Int): Result<List<Comic>> = withContext(Dispatchers.IO) {
        runCatching {
            val offset = (page - 1) * 20
            val url = "$apiUrl/api/v3/ranks?limit=20&offset=$offset&_update=true&type=1&date_type=day"
            val resText = networkClient.get(url, buildHeaders())
            val json = JSONObject(resText)
            val listArr = json.optJSONObject("results")?.optJSONArray("list") ?: JSONArray()
            val list = mutableListOf<Comic>()

            for (i in 0 until listArr.length()) {
                val item = listArr.getJSONObject(i).optJSONObject("comic") ?: continue
                list.add(
                    Comic(
                        id = item.optString("path_word"),
                        title = item.optString("name"),
                        subTitle = "🔥 今日榜单",
                        cover = item.optString("cover"),
                        sourceKey = key,
                        updateTime = item.optString("datetime_updated").take(10)
                    )
                )
            }
            list
        }
    }
}
