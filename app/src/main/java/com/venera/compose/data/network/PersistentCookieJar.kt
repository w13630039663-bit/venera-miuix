package com.venera.compose.data.network

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 线程安全且持久化的 CookieJar
 * 自动持久化保存漫画源登录态与 Cloudflare Clearance Token
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs: SharedPreferences = context.getSharedPreferences("venera_cookies", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val memoryStore = ConcurrentHashMap<String, MutableList<SerializableCookie>>()

    init {
        loadFromPrefs()
    }

    private data class SerializableCookie(
        val name: String,
        val value: String,
        val expiresAt: Long,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean
    ) {
        fun toCookie(): Cookie {
            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .expiresAt(expiresAt)
                .path(path)
            if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
            if (secure) builder.secure()
            if (httpOnly) builder.httpOnly()
            return builder.build()
        }

        companion object {
            fun fromCookie(c: Cookie): SerializableCookie {
                return SerializableCookie(
                    name = c.name,
                    value = c.value,
                    expiresAt = c.expiresAt,
                    domain = c.domain,
                    path = c.path,
                    secure = c.secure,
                    httpOnly = c.httpOnly,
                    hostOnly = c.hostOnly
                )
            }
        }
    }

    private fun loadFromPrefs() {
        val all = prefs.all
        for ((host, jsonStr) in all) {
            if (jsonStr is String) {
                try {
                    val type = object : TypeToken<List<SerializableCookie>>() {}.type
                    val list: List<SerializableCookie> = gson.fromJson(jsonStr, type)
                    memoryStore[host] = list.toMutableList()
                } catch (_: Exception) {}
            }
        }
    }

    private fun persistHost(host: String) {
        val list = memoryStore[host] ?: return
        val json = gson.toJson(list)
        prefs.edit { putString(host, json) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val currentList = memoryStore.getOrPut(host) { mutableListOf() }
        val now = System.currentTimeMillis()

        synchronized(currentList) {
            for (c in cookies) {
                // 移除过期或同名的旧 Cookie
                currentList.removeAll { it.name == c.name }
                if (c.expiresAt > now) {
                    currentList.add(SerializableCookie.fromCookie(c))
                }
            }
        }
        persistHost(host)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val list = memoryStore[host] ?: return emptyList()
        val now = System.currentTimeMillis()
        val validCookies = mutableListOf<Cookie>()

        synchronized(list) {
            val it = list.iterator()
            while (it.hasNext()) {
                val sc = it.next()
                if (sc.expiresAt <= now) {
                    it.remove()
                } else {
                    val cookie = sc.toCookie()
                    if (cookie.matches(url)) {
                        validCookies.add(cookie)
                    }
                }
            }
        }
        return validCookies
    }

    fun clear() {
        memoryStore.clear()
        prefs.edit { clear() }
    }
}