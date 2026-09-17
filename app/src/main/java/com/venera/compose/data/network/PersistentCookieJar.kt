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
        migrateSubdomainCookiesToRegistrableDomain()
    }

    /**
     * 一次性迁移：把「登录在子域、业务在父域」时代存下的 cookie 复制一份到注册域。
     *
     * 历史行为是只按**登录页的 host** 落库，于是 e-hentai 这类源的 cookie 全躺在
     * `forums.e-hentai.org` 桶里，`e-hentai.org` 的请求永远匿名。新代码已在写入时
     * 同时落到注册域，但**老用户已经存下的数据不会自动变**——若不迁移就得重新登录。
     *
     * 特性：只做「复制」，从不删除或覆盖同名 cookie；由 prefs 标记保证只跑一次。
     */
    private fun migrateSubdomainCookiesToRegistrableDomain() {
        if (prefs.getBoolean(KEY_DOMAIN_MIGRATION_DONE, false)) return
        val now = System.currentTimeMillis()
        var changed = false

        for ((host, list) in memoryStore.toMap()) {
            val reg = registrableDomain(host) ?: continue
            val snapshot = synchronized(list) { list.toList() }
            if (snapshot.isEmpty()) continue
            val target = memoryStore.getOrPut(reg) { mutableListOf() }
            synchronized(target) {
                val names = target.mapTo(mutableListOf()) { it.name }
                for (sc in snapshot) {
                    if (sc.expiresAt <= now) continue
                    if (sc.name in names) continue
                    target.add(sc.copy(domain = reg, hostOnly = false))
                    names.add(sc.name)
                    changed = true
                }
            }
        }
        if (changed) {
            for (host in memoryStore.keys.toList()) persistHost(host)
        }
        prefs.edit { putBoolean(KEY_DOMAIN_MIGRATION_DONE, true) }
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

    /**
     * 落库响应/JS 下发的 Cookie。
     *
     * ## 两条与官方 `CookieJarSql` 对齐的语义（都曾是缺陷源）
     *
     * 1. **按 cookie 自身的 domain 分桶**，而不是一律按 `url.host`。
     *    官方 `cookie.db` 的主键是 `(name, domain, path)`，domain 列就是归属；
     *    查表时用 `_getAcceptedDomains(host)`（host + 各级 `.suffix`）再取。
     *    以前的实现一律塞进 `url.host` 桶，于是「domain 与请求 host 不一致」的 cookie
     *    （典型：源把 `.e-hentai.org` 的 cookie 通过 `setCookies("https://exhentai.org", …)`
     *    下发）会被放进错误的桶里 —— 既发不出去，又参与下面的同名去重。
     *
     * 2. **同名去重必须带 domain 维度**。官方靠主键里的 domain 让
     *    `ipb_member_id@.e-hentai.org` 与 `ipb_member_id@.exhentai.org` 共存；
     *    只按 name 去重会让后写的顶掉先写的（ehentai 的 cookie 直填正是
     *    同一批里 8 条两两同名、只有 domain 不同），随后 `matches(url)` 判 false，
     *    请求就变成了匿名 —— 表现为「收藏列表变成 All (0)」。
     */
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        val touchedHosts = LinkedHashSet<String>()

        for (c in cookies) {
            val host = c.domain.ifEmpty { url.host }
            val currentList = memoryStore.getOrPut(host) { mutableListOf() }
            synchronized(currentList) {
                // 同名**同域**才算同一条；不同域的同名 Cookie 必须共存
                currentList.removeAll { it.name == c.name && it.domain == c.domain }
                if (c.expiresAt > now) {
                    currentList.add(SerializableCookie.fromCookie(c))
                }
            }
            touchedHosts.add(host)
        }
        for (host in touchedHosts) persistHost(host)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val validCookies = mutableListOf<Cookie>()
        val seenNames = HashSet<String>()

        // ★ 除自身 host 外还要查父域桶。很多源是「登录在子域、业务在父域」：
        //   e-hentai 的登录发生在 forums.e-hentai.org，而 baseUrl 取自设置项
        //   `domain`（默认 e-hentai.org），只查自身桶会让 favorites.php 变成匿名请求。
        //   更具体的 host 排在前面，同名 cookie 优先采用更具体的那一份。
        for (h in cookieHostChain(url.host)) {
            val list = memoryStore[h] ?: continue
            var dirty = false
            synchronized(list) {
                val it = list.iterator()
                while (it.hasNext()) {
                    val sc = it.next()
                    if (sc.expiresAt <= now) {
                        it.remove()
                        dirty = true
                    } else {
                        val cookie = sc.toCookie()
                        // 先判匹配再去重：不匹配的 cookie 不应占用名字位
                        if (cookie.matches(url) && seenNames.add(cookie.name)) {
                            validCookies.add(cookie)
                        }
                    }
                }
            }
            if (dirty) persistHost(h)
        }
        return validCookies
    }

    /**
     * 清空指定 URL 关联的全部 Cookie（对应 JS 侧 `Network.deleteCookies(url)`）。
     *
     * 同时匹配父域与子域：部分源（如包子漫画）注销时传入的是裸域名 `bzmgcn.com`，
     * 而 Cookie 实际是按请求主机 `cn.bzmgcn.com` 落库的，必须双向匹配才不会漏删。
     */
    fun clearForUrl(url: HttpUrl) {
        val host = url.host
        val targets = memoryStore.keys.filter { stored ->
            stored == host || stored.endsWith(".$host") || host.endsWith(".$stored")
        }
        for (h in targets) {
            memoryStore.remove(h)
            prefs.edit { remove(h) }
        }
    }

    fun clear() {
        memoryStore.clear()
        prefs.edit { clear() }
    }

    private companion object {
        /** 子域 → 注册域 cookie 迁移标记（只跑一次） */
        const val KEY_DOMAIN_MIGRATION_DONE = "cookie_domain_migration_v1"
    }
}