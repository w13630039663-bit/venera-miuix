package com.venera.desktop.gallery.ui

import com.venera.compose.data.platform.HttpEngine
import com.venera.compose.gallery.data.GelbooruCredentials
import com.venera.compose.gallery.data.SauceNaoCredentials
import okhttp3.OkHttpClient
import okhttp3.Protocol

/**
 * S4 的假站：**用一枚拦截器把请求掐在本地**，不打真网络。
 *
 * ## 为什么不用 `MockWebServer` / 真起一个 HTTP server
 *
 * 三个站点的域名是 `GallerySite` 里的**枚举常量**（`apiHost`），URL 在 client 内部拼死
 * —— 没有注入点。所以"让请求去别处"只有两条路：改 `GallerySite`（那是生产面，S4 不许动），
 * 或者在 `HttpEngine` 那一层掐住。后者正是本仓为桌面侧留的那枚口，
 * 也就是说**这枚假站走的是与生产同一条出网路径**（同样的 URL 拼装、同样的解信封、同样的
 * 401 → 人话翻���），差别只在最后一跳不真的出去。
 *
 * 另有一条更硬的理由：本仓的纪律是"真相来自被测代码本身，配置与文案是被核对的一方"。
 * 另起一个 HTTP server 就要改 host 解析（hosts 文件 / DNS 劫持），那是机器级副作用；
 * 而拦截器是**进程内、可撤销、跨平台**的。
 */
class FakeHttpEngine : HttpEngine {

    /** 末一跳之前的响应表：URL 精确匹配 → (code, body)。 */
    private val routes = LinkedHashMap<String, Route>()

    /** 实际发出去的请求，按顺序记着 —— 判 pid 0 基这类口径只能从这里读。 */
    val requests = mutableListOf<String>()

    private data class Route(val code: Int, val body: String)

    fun on(url: String, code: Int = 200, body: String = ""): FakeHttpEngine =
        apply { routes[url] = Route(code, body) }

    /** 只答状态码不给 body —— **401 那一档的实测形态就是 body 为空**，不许拿一段 JSON 冒充。 */
    fun onStatus(url: String, code: Int): FakeHttpEngine = on(url, code, "")

    /**
     * 清空路由与请求簿记，供下一条用例复用**同一颗**实例。
     *
     * ## 为什么必须复用同一颗，而不是每条用例 new 一个
     *
     * 三颗 client 都是 `getInstance(engine, …)` 的**进程内单例**，第一次装配时就把
     * `engine` 与（Gelbooru 那颗还有）凭据**存进 INSTANCE** 了。所以每条用例各 new 一颗
     * `FakeHttpEngine`，从第二条起请求就打到第一条那颗引擎上 —— 那颗的路由表早已被清空，
     * 症状是"拦截器报没有为这枚 URL 登记假响应"，而真因是单例缓存，与被测逻辑无关。
     * 这不是测试的将就：**单例缓存是生产行为**（`GalleryClientsAndroid` 那批适配器也靠它只建一次），
     * 所以测试要顺着它，而不是每条用例绕过它。
     */
    fun reset() = apply {
        routes.clear()
        requests.clear()
    }

    override val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .addInterceptor { chain ->
            val url = chain.request().url.toString()
            requests += url
            // ⚠️ 这里不能写 `firstOrNull { url.startsWith(it.key) }` ——
            // `it.key` 在嵌套 lambda 里指的不是外层那枚 entry（判式写成 `_` 也一样编不过：
            // `firstOrNull` 的谓词是 `(Map.Entry) -> Boolean`，单参没有解构可用）。
            // 具名收进来才断得清是哪一枚路由。
            val matched = routes.entries.firstOrNull { entry -> url.startsWith(entry.key) }
            val route = matched?.value ?: return@addInterceptor error(
                "没有为这枚 URL 登记假响应：$url\n" +
                    "已登记：${routes.keys.joinToString("\n        ")}",
            )
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(route.code)
                .message(if (route.code == 200) "OK" else "ERR")
                .body(route.body.toResponseBody())
                .build()
        }
        .build()

    /**
     * 造一枚 body。
     *
     * ⚠️ okhttp 5.x 把 `ResponseBody.Companion.create` 挪成了**扩展函数**（`content` 那个参数在前），
     * 静态重载已弃用 —— 照旧写法会红也会留一条告警，所以这里显式用扩展形状。
     * 401 那一档 body 必须是**空串**（实测站方就是空 body），空串照样造得出一枚合法 body。
     */
    private fun String.toResponseBody(): okhttp3.ResponseBody =
        okhttp3.ResponseBody.create(null, this)

    /** 带了**真凭据**的那一份（`withCredentials` 会把两枚参数拼上去）。 */
    object GoodCredentials : GelbooruCredentials {
        override val userId: String = "1234"
        override val apiKey: String = "deadbeefdeadbeefdeadbeef"
    }

    /** 匿名：两枚都空串，client 照发（不提前拦），由站方回 401。 */
    object AnonymousCredentials : GelbooruCredentials {
        override val userId: String = ""
        override val apiKey: String = ""
    }

    /** 错凭据：client 看不出是哪一半错（实测站方三种错法都回同一个 401），这里也照样发。 */
    object WrongCredentials : GelbooruCredentials {
        override val userId: String = "9999"
        override val apiKey: String = "000000000000000000000000"
    }

    object NoSauceNaoKey : SauceNaoCredentials {
        override val apiKey: String = ""
    }

    companion object {
        /**
         * **全测试类共用这一颗**（理由见 [reset]：三颗 client 是 `getInstance` 单例，
         * 第一次装配就锁定了 engine 与凭据）。
         *
         * `synchronized` 保住 JVM 用例并行的安全 —— 路由表是普通 LinkedHashMap，
         * 而 JUnit 的 runner 不保证测试之间不重叠。
         */
        val shared: FakeHttpEngine = FakeHttpEngine()

        /** 取一颗干净的共用引擎：清簿记 + 登记本条用例的路由。 */
        @Synchronized
        fun fresh(build: FakeHttpEngine.() -> Unit): FakeHttpEngine =
            shared.reset().apply(build)
    }
}
