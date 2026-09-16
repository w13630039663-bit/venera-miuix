package com.venera.compose.data.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Venera 核心统一网络引擎
 * 基于 OkHttp 4，统一配置 Cookie 持久化、User-Agent 伪装、请求超时与连接池
 */
class VeneraNetworkClient private constructor(context: Context) {

    val cookieJar = PersistentCookieJar(context.applicationContext)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 Venera/1.0"
                )
                .header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
            chain.proceed(requestBuilder.build())
        }
        .build()

    suspend fun get(url: String, headers: Map<String, String>? = null): String = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder().url(url)
        headers?.forEach { (k, v) -> reqBuilder.header(k, v) }
        val response = okHttpClient.newCall(reqBuilder.build()).execute()
        response.body?.string() ?: ""
    }

    suspend fun post(url: String, jsonBody: String, headers: Map<String, String>? = null): String = withContext(Dispatchers.IO) {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val reqBody = jsonBody.toRequestBody(mediaType)
        val reqBuilder = Request.Builder().url(url).post(reqBody)
        headers?.forEach { (k, v) -> reqBuilder.header(k, v) }
        val response = okHttpClient.newCall(reqBuilder.build()).execute()
        response.body?.string() ?: ""
    }

    suspend fun downloadBytes(url: String, referer: String? = null): ByteArray = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder().url(url)
        if (referer != null) {
            reqBuilder.header("Referer", referer)
        }
        val response = okHttpClient.newCall(reqBuilder.build()).execute()
        response.body?.bytes() ?: ByteArray(0)
    }

    companion object {
        @Volatile
        private var INSTANCE: VeneraNetworkClient? = null

        fun getInstance(context: Context): VeneraNetworkClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VeneraNetworkClient(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}