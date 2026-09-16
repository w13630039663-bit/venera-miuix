package com.venera.compose.engine

import android.content.Context
import android.util.Base64
import com.venera.compose.data.network.VeneraNetworkClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class JsHttpHandler(private val context: Context) {

    fun handle(req: Map<String, Any?>): Map<String, Any?> {
        val url = req["url"] as? String ?: return mapOf("error" to "No url provided")
        val method = (req["http_method"] as? String ?: "GET").uppercase()
        val headersMap = (req["headers"] as? Map<*, *>)?.mapNotNull { (k, v) ->
            if (k != null && v != null) k.toString() to v.toString() else null
        }?.toMap() ?: emptyMap()
        val bytesMode = req["bytes"] == true
        val dataObj = req["data"]

        val builder = Request.Builder().url(url)

        var hasUa = false
        for ((k, v) in headersMap) {
            if (k.equals("user-agent", ignoreCase = true)) hasUa = true
            builder.header(k, v)
        }
        if (!hasUa) {
            builder.header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
            )
        }

        val body: RequestBody? = when {
            method == "GET" || method == "HEAD" -> null
            dataObj is Map<*, *> && dataObj["__bytes_base64__"] is String -> {
                val b = Base64.decode(dataObj["__bytes_base64__"] as String, Base64.DEFAULT)
                b.toRequestBody("application/octet-stream".toMediaTypeOrNull())
            }
            dataObj is String -> {
                dataObj.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            }
            else -> {
                if (method in listOf("POST", "PUT", "PATCH")) {
                    ByteArray(0).toRequestBody()
                } else null
            }
        }
        builder.method(method, body)

        return try {
            val client = VeneraNetworkClient.getInstance(context).okHttpClient
            val response = client.newCall(builder.build()).execute()
            val resHeaders = mutableMapOf<String, String>()
            for (name in response.headers.names()) {
                resHeaders[name] = response.headers.values(name).joinToString(",")
            }
            val resBody: Any = if (bytesMode) {
                val bytes = response.body?.bytes() ?: ByteArray(0)
                mapOf("__bytes_base64__" to Base64.encodeToString(bytes, Base64.NO_WRAP))
            } else {
                response.body?.string() ?: ""
            }
            mapOf(
                "status" to response.code,
                "headers" to resHeaders,
                "body" to resBody,
                "error" to null
            )
        } catch (e: Exception) {
            mapOf(
                "status" to 0,
                "headers" to emptyMap<String, String>(),
                "body" to "",
                "error" to (e.message ?: e.javaClass.simpleName)
            )
        }
    }
}
