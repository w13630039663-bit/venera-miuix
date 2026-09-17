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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
    private val prefs by lazy { context.getSharedPreferences("venera_source_copy_manga", Context.MODE_PRIVATE) }
    private val apiUrl: String
        get() {
            val host = prefs.getString("base_url", "api.copy2000.online")?.takeIf { it.isNotBlank() } ?: "api.copy2000.online"
            return if (host.startsWith("http")) host else "https://$host"
        }

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

        // 官方 copy_manga.js：authorization = `Token${token ? " " + token : ""}`
        // 未登录时就是裸 "Token"，登录后必须带上 token，否则账号态请求全部按游客处理。
        val token = prefs.getString("token", null)?.takeIf { it.isNotBlank() }
        val authorization = if (token != null) "Token $token" else "Token"

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
            "authorization" to authorization,
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
                        // S8: 移除硬编码假副标题「🔥 今日榜单」，如实用更新时间展示
                        subTitle = item.optString("datetime_updated").take(10),
                        cover = item.optString("cover"),
                        sourceKey = key,
                        updateTime = item.optString("datetime_updated").take(10)
                    )
                )
            }
            list
        }
    }

    override fun getSettings(): List<com.venera.compose.feature.sourcemanage.SourceSettingItem> {
        val currentApi = prefs.getString("base_url", "api.copy2000.online") ?: "api.copy2000.online"
        val currentRegion = prefs.getString("region", "0") ?: "0"
        val currentQuality = prefs.getString("image_quality", "1500") ?: "1500"

        return listOf(
            com.venera.compose.feature.sourcemanage.SourceSettingItem.Input(
                key = "base_url",
                title = "API地址",
                value = currentApi,
                defaultValue = "api.copy2000.online"
            ),
            com.venera.compose.feature.sourcemanage.SourceSettingItem.Select(
                key = "region",
                title = "CDN线路",
                value = currentRegion,
                defaultValue = "0",
                options = listOf(
                    com.venera.compose.feature.sourcemanage.SelectOption("1", "大陆线路"),
                    com.venera.compose.feature.sourcemanage.SelectOption("0", "海外线路")
                )
            ),
            com.venera.compose.feature.sourcemanage.SourceSettingItem.Select(
                key = "image_quality",
                title = "图片质量",
                value = currentQuality,
                defaultValue = "1500",
                options = listOf(
                    com.venera.compose.feature.sourcemanage.SelectOption("800", "低 (800)"),
                    com.venera.compose.feature.sourcemanage.SelectOption("1200", "中 (1200)"),
                    com.venera.compose.feature.sourcemanage.SelectOption("1500", "高 (1500)")
                )
            )
        )
    }

    override fun saveSetting(key: String, value: Any) {
        prefs.edit().putString(key, value.toString()).apply()
    }

    override fun getAccountInfo(): com.venera.compose.feature.sourcemanage.SourceAccountInfo {
        val username = prefs.getString("account_username", null)
        val token = prefs.getString("token", null)
        val isLogged = !token.isNullOrBlank()

        return com.venera.compose.feature.sourcemanage.SourceAccountInfo(
            hasAccount = true,
            isLogged = isLogged,
            username = username,
            infoItems = if (isLogged) listOf("用户名" to (username ?: "拷贝用户")) else emptyList(),
            supportsPasswordLogin = true
        )
    }

    /**
     * 账密登录，严格对齐官方 `copy_manga.js` 的 `account.login`：
     *
     * ```
     * POST {apiUrl}/api/v3/login
     * Content-Type: application/x-www-form-urlencoded;charset=utf-8
     * body: username={账号}&password={base64(密码-salt)}&salt={salt}&authorization=Token+
     * ```
     *
     * 其中 `salt` 为 1000..9999 的随机数，`password` 用**标准 base64（无换行）**，
     * 服务端返回 `results.token` 后作为后续请求的 `authorization: Token <token>`。
     *
     * ⚠️ 历史遗留：这里曾把登录写成「存一个写死的 `dummy_token` 然后返回成功」——
     * 纯假登录，账号根本没通过验证。已改为真实请求。
     */
    override suspend fun login(username: String, password: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val salt = Random.nextInt(1000, 9999)
            val encodedPwd = Base64.encodeToString(
                "$password-$salt".toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            // 注意 `authorization=Token+`：`+` 在 form 编码里就是空格，与原版一致
            val formBody = "username=$username&password=$encodedPwd\n&salt=$salt&authorization=Token+"

            val request = okhttp3.Request.Builder()
                .url("$apiUrl/api/v3/login")
                .post(formBody.toRequestBody("application/x-www-form-urlencoded;charset=utf-8".toMediaType()))
                .apply { buildHeaders().forEach { (k, v) -> header(k, v) } }
                .build()

            val responseText = withContext(Dispatchers.IO) {
                networkClient.okHttpClient.newCall(request).execute().use { resp ->
                    resp.body?.string() ?: ""
                }
            }
            val json = JSONObject(responseText)
            val code = json.optInt("code", -1)
            if (code != 200) {
                val msg = json.optString("message").ifBlank { json.optString("msg") }
                throw Exception(msg.ifBlank { "登录失败（服务端 code=$code）" })
            }
            val token = json.optJSONObject("results")?.optString("token").orEmpty()
            if (token.isBlank()) throw Exception("登录响应里没有 token")

            prefs.edit()
                .putString("token", token)
                .putString("account_username", username)
                .apply()
            true
        }
    }

    override suspend fun logout(): Result<Boolean> = withContext(Dispatchers.IO) {
        prefs.edit().remove("token").remove("account_username").apply()
        Result.success(true)
    }
}
