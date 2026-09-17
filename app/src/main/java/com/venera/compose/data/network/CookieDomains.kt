package com.venera.compose.data.network

/**
 * Cookie 域名归属工具。
 *
 * ## 为什么需要它
 *
 * [PersistentCookieJar] 按 `url.host` **精确分桶**存取。而很多源存在「登录在子域、
 * 业务在父域」的结构，最典型的是 e-hentai：
 *
 * - 网页登录发生在 `forums.e-hentai.org`
 * - 但源的 `baseUrl` 取自设置项 `domain`，**默认是 `e-hentai.org`**
 * - 于是 `https://e-hentai.org/favorites.php` 永远拿不到登录 cookie，
 *   匿名请求返回未登录页面 → 解析出 0 个收藏夹（网页端会话完全正常）
 *
 * Android 的 `CookieManager.getCookie(url)` 只回 `name=value` 请求头字符串，
 * **拿不到 domain 属性**，所以「登录成功后把 cookie 导入共享 CookieJar」这一步
 * 只能靠 host 推断归属。官方 Flutter 版是把 WebView 的 cookie 连同真实 domain
 * 整体导入，等价效果就是：**同一个注册域下的子域之间应当互通**。
 *
 * 这里提供两个纯函数：
 * - [registrableDomain]：`forums.e-hentai.org` → `e-hentai.org`（拿不到就返回 null）
 * - [cookieHostChain]：`forums.e-hentai.org` → `[forums.e-hentai.org, e-hentai.org]`
 */
private val MULTI_PART_SUFFIXES = setOf(
    "com.cn", "net.cn", "org.cn", "gov.cn", "edu.cn", "ac.cn",
    "co.uk", "org.uk", "me.uk", "ac.uk", "gov.uk",
    "co.jp", "ne.jp", "or.jp", "ac.jp",
    "com.tw", "org.tw", "net.tw", "edu.tw",
    "com.hk", "org.hk", "net.hk", "edu.hk",
    "co.kr", "or.kr", "ne.kr",
    "com.au", "net.au", "org.au", "edu.au",
    "com.br", "com.mx", "com.ar", "com.tr", "com.sg", "com.my",
    "com.ph", "com.vn", "com.pk", "com.ua", "com.pl", "com.es", "com.it",
    "co.nz", "co.za", "co.in", "co.id", "or.id", "web.id", "my.id",
)

/**
 * 多租户共享后缀：这些后缀下的「注册域」其实不属于同一个站点
 * （`a.github.io` 与 `b.github.io` 毫无关系），因此**不做父域提升**。
 */
private val SHARED_HOST_SUFFIXES = setOf(
    "github.io", "pages.dev", "vercel.app", "netlify.app", "herokuapp.com",
    "blogspot.com", "wordpress.com", "firebaseapp.com", "web.app", "workers.dev",
    "onrender.com", "glitch.me", "surge.sh", "azurewebsites.net", "cloudfront.net",
    "s3.amazonaws.com", "r2.dev", "fly.dev", "repl.co",
)

/**
 * 取 host 的「注册域」（可跨子域共享 cookie 的那一级）。
 *
 * - `forums.e-hentai.org` → `e-hentai.org`
 * - `www.example.co.uk`   → `example.co.uk`（识别多段后缀）
 * - `e-hentai.org`        → `null`（本身已是注册域，无需提升）
 * - `a.github.io`         → `null`（多租户共享后缀，刻意不提升）
 * - `127.0.0.1` / 裸 IP    → `null`
 */
fun registrableDomain(host: String?): String? {
    val h = host?.trim()?.lowercase()?.removePrefix(".")?.takeIf { it.isNotEmpty() } ?: return null
    // 裸 IP / localhost 不参与域名归并
    if (h.contains(':') || h.all { it.isDigit() || it == '.' }) return null
    val labels = h.split('.').filter { it.isNotEmpty() }
    if (labels.size < 3) return null

    val lastTwo = labels.takeLast(2).joinToString(".")
    if (lastTwo in SHARED_HOST_SUFFIXES) return null

    // 形如 xxx.co.uk / xxx.com.cn：注册域要吃三段
    val candidate = if (lastTwo in MULTI_PART_SUFFIXES) {
        if (labels.size < 4) return null
        labels.takeLast(3).joinToString(".")
    } else {
        lastTwo
    }
    return candidate.takeIf { it != h }
}

/**
 * 读取 cookie 时需要依次查的桶：自身 host + 逐级父域（到注册域为止）。
 *
 * `forums.e-hentai.org` → `[forums.e-hentai.org, e-hentai.org]`
 * 顺序保持「更具体在前」，便于调用方保持既有优先级。
 */
fun cookieHostChain(host: String?): List<String> {
    val h = host?.trim()?.lowercase()?.removePrefix(".")?.takeIf { it.isNotEmpty() } ?: return emptyList()
    val chain = linkedSetOf(h)
    registrableDomain(h)?.let { chain.add(it) }
    return chain.toList()
}
