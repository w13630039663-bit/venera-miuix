package com.venera.engineprobe

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * 源脚本凭据那罐 cookie：**内存与盘上同一颗件**（批 88·下半 第一格）。
 *
 * ## 为什么这一格排在入口之前
 *
 * 桥里 `loginWithCookies` 走的是 `Network.setCookies`（= 这一颗），而"已登录"那一格记的
 * `JsSourceDataStore.account` **早就落盘**。罐留在内存里时，两半不同轨的形状就是本仓最忌的假开关：
 * 屏上念「已登录」而重启后请求带不上凭据。所以先盘后入口。
 *
 * ## 三条口径的出处（不是这一格定的）
 *
 * - **桶按请求 host 分** —— 照对岸 `PersistentCookieJar` 落 SharedPreferences 时"每 host 一桶"的形状；
 * - **同名键新的赢**（`name + domain + path` 三者全同才算同一枚）—— 照对岸那颗里 `removeAll{}` 再 `add`；
 * - **落点由调用点递进来** —— 这颗件不自己选目录，递进来的目录随数据根走，所以隔离数据根把这罐一起隔离
 *   （批 85/82.9b 同一条纪律：读数不许自造证据）。
 *
 * ## 坏档为什么留档再重开
 *
 * 盘上那份解不开时静默当空罐用 = 用户以为自己还登录着而凭据没了，且"我明明登录过"再也查不回去。
 * 所以挪一份 `.bad-<毫秒>` 并念一行点名（画廊历史那颗同一处理，判据 ⑥）。
 */
class DesktopCookieJar(private val store: File) : CookieJar {

    private val gson = Gson()
    private val bucketType = object : TypeToken<Map<String, List<Map<String, Any?>>>>() {}.type
    private val buckets = ConcurrentHashMap<String, MutableList<Cookie>>()

    /**
     * 本次装载专用的那一层（取证种子 `--cookie=`）：随请求发得出，而**永不落盘**
     * —— [persist] 只读 [buckets]。取证参数把值写进用户那份额是"量具动了被量的东西"
     * （批 83.1 同一条口径）。同名键这一层赢，因为它是这一趟声明的真相。
     */
    private val seeds = ConcurrentHashMap<String, MutableList<Cookie>>()

    init {
        load()
    }

    companion object {
        /**
         * 罐在数据根里的那一颗文件（**唯一出处**：宿主与界面回核都从这一颗拿路径）。
         *
         * 为什么做成函数而不是两处各拼一次 `"cookies.json"`：文件名漂一格的结果是
         * 「屏上回核读的那份不是宿主写的那份」，而那一形在屏上看着完全正常。
         */
        fun fileIn(dataDir: File): File = File(dataDir, "cookies.json")
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        buckets.compute(url.host) { _, existing ->
            val kept = existing ?: mutableListOf()
            cookies.forEach { c ->
                kept.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
                kept.add(c)
            }
            kept
        }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val seeded = seeds[url.host]?.filter { it.matches(url) }.orEmpty()
        val kept = buckets[url.host]?.filter { it.matches(url) }.orEmpty().filter { d ->
            seeded.none { it.name == d.name && it.domain == d.domain && it.path == d.path }
        }
        return seeded + kept
    }

    /** 递进本次装载的那一枚（只活在这一趟）。 */
    fun seedFromRequest(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        seeds.compute(url.host) { _, existing ->
            val kept = existing ?: mutableListOf()
            cookies.forEach { c ->
                kept.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
                kept.add(c)
            }
            kept
        }
    }

    /**
     * 解析并并进一枚取证种子（`--cookie=<host>:<名>=<值>`）。交回的那一句**只在真进了罐时为真**。
     *
     * ⚠️ 这一格是起窗那一趟抓回来的：从前"解析成功"就被当成"吃下了"，而裸 host（`exhentai.org`）
     * 在 OkHttp 那里根本解不成 URL（`toHttpUrlOrNull` 要带 scheme），于是两枚种子一枚都没进罐，
     * 而汇总那一句照念 `种子=2/2`（把 parsed 报成 applied）。桥里 `handleCookie` 早就带了这个兜
     * （`"https://$urlStr"`），种子这一路没抄 —— 同一件事在两处各有写法就是这一族的成因。
     *
     * 前导点照桥里那条口径剥掉（`.exhentai.org` 与 `exhentai.org` 对 OkHttp 的 `domain()` 不是一回事，
     * 后者才建得起来，而语义等价）。
     */
    fun seed(raw: String): Boolean {
        val hostPart = raw.split(":", limit = 2)
        val nameValue = hostPart.getOrNull(1)?.split("=", limit = 2)
        if (hostPart.size != 2 || nameValue?.size != 2) {
            println("D_凭据种子 摘掉整枚 原样=$raw 因=不是 <host>:<名>=<值>")
            return false
        }
        var host = hostPart[0].trim().lowercase()
        while (host.startsWith(".")) host = host.substring(1)
        val url = host.toHttpUrlOrNull() ?: "https://$host".toHttpUrlOrNull()
        if (url == null) {
            println("D_凭据种子 摘掉整枚 原样=$raw 因=host 当不成 URL")
            return false
        }
        val name = nameValue[0].trim()
        // 名为空那一枚 OkHttp 的 Builder 不吃错（它只挡非法字符），而"名是空的"那枚进了罐
        // 就是请求头里多一段 `=v` —— 桥里 buildCookie 同一条判据（`if (name.isEmpty()) return null`）。
        if (name.isEmpty()) {
            println("D_凭据种子 摘掉整枚 原样=$raw 因=名是空的")
            return false
        }
        val cookie = runCatching {
            Cookie.Builder()
                .name(name)
                .value(nameValue[1])
                .domain(url.host)
                .apply { if (url.isHttps) secure() }
                .build()
        }.getOrElse {
            println("D_凭据种子 摘掉整枚 原样=$raw 因=cookie 不成")
            return false
        }
        seedFromRequest(url, listOf(cookie))
        return true
    }

    /**
     * 回核用的读数：按 cookie 名数**盘上那一份**里有几枚（不含本次装载种进去的）。
     *
     * 为什么要这一问：源脚本的 `validate` 回 `true` 只说明站方认了这批凭据，
     * 而"重启还带不带得上"是另一件事 —— 那一格只有现读盘上那份才算量过。
     */
    fun tally(names: List<String>): Map<String, Int> =
        names.associateWith { name -> buckets.values.sumOf { list -> list.count { it.name == name } } }

    /** 登出：清掉与该请求匹配的那几枚，**内存与盘上一起清**（只清内存就是两条轨）。 */
    fun clearForUrl(url: HttpUrl) {
        buckets.compute(url.host) { _, existing ->
            existing?.filterNotTo(mutableListOf()) { it.matches(url) }
        }
        persist()
    }

    private fun persist() {
        val dir = store.parentFile ?: return
        if (!dir.isDirectory && !dir.mkdirs()) {
            println("D_凭据罐 写=建目录失败 落点=${dir.path}")
            return
        }
        val tmp = File(dir, "${store.name}.tmp")
        val done = runCatching {
            tmp.writeText(gson.toJson(snapshot()), Charsets.UTF_8)
            Files.move(tmp.toPath(), store.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        if (done.isFailure) {
            // 写不进盘就等于"重启会丢"，这一句不许静默：内存照旧可用，但读数必须念出来。
            tmp.delete()
            println("D_凭据罐 写=失败 落点=${store.path} 因=${done.exceptionOrNull()?.javaClass?.simpleName}")
        }
    }

    private fun snapshot(): Map<String, List<Map<String, Any?>>> =
        buckets.mapValues { (_, cookies) ->
            cookies.toList().map {
                mapOf(
                    "name" to it.name, "value" to it.value, "domain" to it.domain,
                    "path" to it.path, "expires" to it.expiresAt,
                    "secure" to it.secure, "httpOnly" to it.httpOnly,
                )
            }
        }

    private fun load() {
        if (!store.isFile) return
        val text = runCatching { store.readText(Charsets.UTF_8) }.getOrNull()
        val parsed = text?.let { raw ->
            runCatching { gson.fromJson<Map<String, List<Map<String, Any?>>>>(raw, bucketType) }.getOrNull()
        }
        if (parsed == null) {
            println("D_凭据罐 读=坏档 落点=${store.path}")
            quarantine()
            return
        }
        for ((host, records) in parsed) {
            val cookies = records.orEmpty().mapNotNull(::buildCookie)
            if (cookies.isNotEmpty()) buckets[host] = cookies.toMutableList()
        }
    }

    /** 把解不开的那份挪走并点名；挪不动也念一句，现档随后会被下一次写覆盖。 */
    private fun quarantine() {
        val parent = store.parentFile ?: return
        val kept = File(parent, "${store.name}.bad-${System.currentTimeMillis()}")
        val moved = runCatching {
            Files.move(store.toPath(), kept.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.isSuccess
        println("D_凭据罐 留档=${if (moved) kept.name else "失败"} 落点=${store.path}")
    }

    /** 逐条容错：坏的那一枚摘掉，整桶不废（与桥里 buildCookie 同一条判据）。 */
    private fun buildCookie(m: Map<String, Any?>): Cookie? = runCatching {
        val name = m["name"] as? String ?: return null
        val value = m["value"] as? String ?: return null
        val domain = (m["domain"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
        Cookie.Builder().apply {
            name(name)
            value(value)
            domain(domain)
            (m["path"] as? String)?.takeIf { it.isNotEmpty() }?.let { path(it) }
            (m["expires"] as? Number)?.toLong()?.let { expiresAt(it) }
            if (m["secure"] == true) secure()
            if (m["httpOnly"] == true) httpOnly()
        }.build()
    }.onFailure { println("D_凭据罐 摘掉坏枚 name=${m["name"]} domain=${m["domain"]}") }.getOrNull()
}
