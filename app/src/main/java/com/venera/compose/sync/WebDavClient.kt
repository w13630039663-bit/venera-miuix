package com.venera.compose.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 原生轻量级 WebDAV 客户端 (S7)
 *
 * 核心特性：
 * 1. 标准 HTTP Basic Auth 鉴权
 * 2. PROPFIND 远程目录探测与备份文件列取
 * 3. MKCOL 自动补建云端归档目录
 * 4. 流式 PUT 上传与 GET 下载
 * 5. 过期备份云端 DELETE 维护
 */
class WebDavClient(private val config: WebDavConfig) {

    private val tag = "WebDavClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val credentials: String by lazy {
        Credentials.basic(config.username, config.password)
    }

    private fun getNormalizedBaseUrl(): String {
        var base = config.serverUrl.trim()
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "https://$base"
        }
        return base.removeSuffix("/")
    }

    private fun getRemoteDirUrl(): String {
        val base = getNormalizedBaseUrl()
        val path = config.remotePath.trim().let {
            if (!it.startsWith("/")) "/$it" else it
        }.let {
            if (!it.endsWith("/")) "$it/" else it
        }
        return "$base$path"
    }

    /**
     * 测试 WebDAV 连通性与凭据正确性
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = getNormalizedBaseUrl()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", credentials)
                .method("PROPFIND", "".toRequestBody("application/xml".toMediaType()))
                .header("Depth", "0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code in 200..299 || response.code == 207) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "testConnection failed", e)
            Result.failure(e)
        }
    }

    /**
     * 确保远端备份目录存在（若不存在则自动 MKCOL）
     */
    suspend fun ensureRemoteDirectory(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dirUrl = getRemoteDirUrl()
            val checkReq = Request.Builder()
                .url(dirUrl)
                .header("Authorization", credentials)
                .method("PROPFIND", "".toRequestBody("application/xml".toMediaType()))
                .header("Depth", "0")
                .build()

            val exists = client.newCall(checkReq).execute().use { it.isSuccessful || it.code == 207 }
            if (exists) return@withContext Result.success(Unit)

            // 创建远程目录
            val mkcolReq = Request.Builder()
                .url(dirUrl)
                .header("Authorization", credentials)
                .method("MKCOL", null)
                .build()

            client.newCall(mkcolReq).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 405 /* 已存在 */) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("创建远程目录失败: HTTP ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 列出远程目录下的备份文件列表
     */
    suspend fun listBackups(): Result<List<WebDavFileItem>> = withContext(Dispatchers.IO) {
        try {
            ensureRemoteDirectory()
            val dirUrl = getRemoteDirUrl()
            val request = Request.Builder()
                .url(dirUrl)
                .header("Authorization", credentials)
                .method("PROPFIND", "".toRequestBody("application/xml".toMediaType()))
                .header("Depth", "1")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 207) {
                    return@withContext Result.failure(Exception("PROPFIND 失败 HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                val items = parsePropFindResponse(body, dirUrl)
                Result.success(items.filter { !it.isDirectory && (it.name.endsWith(".venera") || it.name.endsWith(".zip")) })
            }
        } catch (e: Exception) {
            Log.w(tag, "listBackups failed", e)
            Result.failure(e)
        }
    }

    /**
     * 上传备份文件
     */
    suspend fun uploadFile(fileName: String, file: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ensureRemoteDirectory()
            val targetUrl = "${getRemoteDirUrl()}$fileName"
            val request = Request.Builder()
                .url(targetUrl)
                .header("Authorization", credentials)
                .put(file.asRequestBody("application/zip".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 201 || response.code == 204) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("上传失败 HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "uploadFile failed", e)
            Result.failure(e)
        }
    }

    /**
     * 下载指定的备份文件
     */
    suspend fun downloadFile(fileName: String, destinationFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val targetUrl = "${getRemoteDirUrl()}$fileName"
            val request = Request.Builder()
                .url(targetUrl)
                .header("Authorization", credentials)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("下载备份失败 HTTP ${response.code}"))
                }
                val body = response.body ?: return@withContext Result.failure(Exception("空响应体"))
                if (destinationFile.exists()) destinationFile.delete()
                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Result.success(destinationFile)
            }
        } catch (e: Exception) {
            Log.e(tag, "downloadFile failed", e)
            Result.failure(e)
        }
    }

    /**
     * 删除指定的远程备份文件
     */
    suspend fun deleteFile(fileName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val targetUrl = "${getRemoteDirUrl()}$fileName"
            val request = Request.Builder()
                .url(targetUrl)
                .header("Authorization", credentials)
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 204 || response.code == 404) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("删除远程文件失败 HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePropFindResponse(xml: String, currentDirUrl: String): List<WebDavFileItem> {
        val items = mutableListOf<WebDavFileItem>()
        if (xml.isBlank()) return items

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var inResponse = false
            var currentHref = ""
            var currentLength: Long = 0
            var currentModTime: Long = 0
            var isDir = false

            val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (name.lowercase()) {
                            "response" -> {
                                inResponse = true
                                currentHref = ""
                                currentLength = 0
                                currentModTime = 0
                                isDir = false
                            }
                            "href" -> if (inResponse) currentHref = parser.nextText().trim()
                            "getcontentlength" -> if (inResponse) currentLength = parser.nextText().trim().toLongOrNull() ?: 0L
                            "getlastmodified" -> if (inResponse) {
                                val text = parser.nextText().trim()
                                currentModTime = runCatching { dateFormat.parse(text)?.time ?: 0L }.getOrDefault(0L)
                            }
                            "collection" -> if (inResponse) isDir = true
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name.equals("response", ignoreCase = true) && inResponse) {
                            inResponse = false
                            val cleanHref = currentHref.removeSuffix("/")
                            val fileName = cleanHref.substringAfterLast('/')
                            // 排除当前目录自身的响应项
                            if (fileName.isNotBlank() && cleanHref != currentDirUrl.removeSuffix("/")) {
                                items.add(
                                    WebDavFileItem(
                                        name = fileName,
                                        path = cleanHref,
                                        size = currentLength,
                                        lastModified = currentModTime,
                                        isDirectory = isDir
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(tag, "XML parsing error", e)
        }
        return items
    }
}
