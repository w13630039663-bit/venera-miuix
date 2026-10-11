package com.venera.desktop.gallery.data

import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第七十九批 · **取图那一跳接上防盗链头**（B 路：不碰 `app/`，桌面自持那一枚常量）。
 *
 * ## 这一格是谁量出来的
 *
 * 批 43 那三帧：pixiv 那一档把头像地址取到并落了档，而屏上仍是字母座 —— 断点在**桌面取图片字节那一层**。
 * 对岸那张表（`app/…/data/network/ImageHeaderPolicy.kt:73`）早就写着
 * `"pximg.net" to mapOf("Referer" to "https://www.pixiv.net/")`，注释里还留着成对读数
 * （无 `Referer` → 403 的 146 字节错误页 / 带 → 200 的 5,149 字节真图）。
 * 桌面吃不到那颗件：`data/network` 整目录不在桌面编译面里（`desktop/build.gradle.kts` 的 srcDir 清单），
 * 而 A 路（把对岸那两处的常量改成**转交**自纯 JVM 那颗）要点头 —— 没点到，所以本批走 B 路。
 *
 * ## B 路的代价与它的护栏
 *
 * B 路等于**桌面自己抄一份**那条规则，而两份表会漂 —— 漂出来的形状是"某一站永远 403"。
 * 所以这里把"抄"限定到最小：只抄 `pximg.net` 那一枚值，且
 * ③ 逐字回读对岸那一行核对（对岸改了值而桌面没跟上 ⇒ 红），
 * ⑤ 机械扫其余那些 host 一颗都不许出现在桌面代码行里（不许顺手把整张表搬过来），
 * ② 图片客户端从引擎那台**派生**（新造一台就是"另起一条路而那条路没接线"那一族，也是批 47 那条
 *   「预取必须吃同一份单例」的同族），
 * ④ 装上那一处恰好一次且**出声**（不装单例，屏上那些 `AsyncImage` 各吃默认客户端 ⇒ 头还是盖不上）。
 */
class DesktopImagePipelineTest {

    /** 只记录「交下去的那一枚请求」的假链路：拦截器的行为就是它交给下游的东西。 */
    private class RecordingChain(private val request: Request) : Interceptor.Chain {
        var sent: Request? = null
        override fun request(): Request = request
        override fun proceed(request: Request): Response {
            sent = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("ok")
                .build()
        }

        override fun connection(): Connection? = null
        override fun call(): Call = throw UnsupportedOperationException("这一条判据不碰 call")
        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private fun refererFor(url: String): String? {
        val chain = RecordingChain(Request.Builder().url(url).build())
        DesktopImageHeaderInterceptor().intercept(chain)
        return chain.sent?.header("Referer")
    }

    /** ① 那一枚头**真的**盖到 pixiv 图床的两种子域名上，而对照组一枚都不许吃。 */
    @Test
    fun 防盗链头真盖到图床请求而对照组不加() {
        val sub = refererFor("https://i.pximg.net/user-profile/img/a_170.jpg")
        assertNotNull("pixiv 图床那一笔没盖上 Referer ⇒ 屏上必然是字母座", sub)
        assertEquals("Referer 盖错了值", DESKTOP_PIXIV_REFERER, sub)
        assertEquals("另一枚子域也要吃到（表里那条按后缀匹配）", DESKTOP_PIXIV_REFERER, refererFor("https://s.pximg.net/x.png"))
        assertEquals("裸域本身也要吃到", DESKTOP_PIXIV_REFERER, refererFor("https://pximg.net/x.png"))
        // 对照组三枚：画廊本站、donmai 的 CDN、以及一枚**名字像而不同**的域（后缀匹配写成 contains 就会中）
        assertNull("对照组被污染：safebooru 那一笔不该吃 pixiv 的 Referer", refererFor("https://safebooru.donmai.us/1.jpg"))
        assertNull("yande.re 那一笔不该吃", refererFor("https://yande.re/image/x.jpg"))
        assertNull("`notpximg.net` 不是 pximg.net（后缀匹配不许用 contains / endsWith(不带点)）", refererFor("https://cdn.notpximg.net/x.jpg"))
    }

    /** ② 图片客户端是从引擎那台**派生**的：代理与超时全继承，只多那一枚拦截器。 */
    @Test
    fun 图片客户端派生自引擎那台而只多一枚拦截器() {
        val base = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 7890)))
            .connectTimeout(11, TimeUnit.SECONDS)
            .readTimeout(23, TimeUnit.SECONDS)
            .build()
        val images = DesktopImagePipeline.clientForImages(base)
        assertEquals("代理没继承 ⇒ 取证那枚 `--proxy=` 对图片这一路是空的", base.proxy, images.proxy)
        assertEquals("连接超时没继承", base.connectTimeoutMillis, images.connectTimeoutMillis)
        assertEquals("读超时没继承", base.readTimeoutMillis, images.readTimeoutMillis)
        assertEquals(
            "派生出来的客户端应当只比 base 多**一枚**拦截器",
            base.interceptors.size + 1,
            images.interceptors.size,
        )
        assertTrue(
            "多出来的那一枚不是防盗链那颗：" + images.interceptors.last(),
            images.interceptors.last() is DesktopImageHeaderInterceptor,
        )
    }

    /**
     * ③ 抄来的那一枚值与对岸**逐字同值**，而且桌面全树只写这一处。
     *
     * 这条是 B 路的代价换来的义务：对岸改了值而桌面没跟上，屏上的形状仍然是"字母座"，
     * 而这次连"我们抄了一份旧的"都读不出来。锚按对岸那一整行（手册 §0 坑 ㉔：只数标识符看不见死值）。
     */
    @Test
    fun 抄来的那一枚值与对岸逐字同值而只写一处() {
        val counterpart = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("data/network/ImageHeaderPolicy.kt"))
        val line = counterpart.lineSequence().firstOrNull { it.contains("\"$DESKTOP_PIXIV_IMAGE_HOST\" to") }
        assertNotNull("对岸那张表里已经没有 `" + DESKTOP_PIXIV_IMAGE_HOST + "` 那一行了（它改了而这里没跟上）：$line", line)
        assertTrue(
            "对岸那一行里的 Referer 值与桌面抄的那枚不同（对岸=$line 桌面=$DESKTOP_PIXIV_REFERER）",
            line!!.contains("\"Referer\" to \"$DESKTOP_PIXIV_REFERER\""),
        )
        val hosts = DesktopSourceTree.desktopMainSources().filter {
            DesktopSourceTree.codeText(it).contains("https://www.pixiv.net/")
        }
        assertEquals(
            "那枚值又有了第二份出处（现在是 ${hosts.joinToString(" · ") { it.name }}）：",
            listOf("DesktopImagePipeline.kt"),
            hosts.map { it.name },
        )
    }

    /** ④ 接线：应用根装**恰好一次**，装的那一刻出声，而那颗件里三样都在。 */
    @Test
    fun 装载那一处真接上且出声() {
        val appFile = DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt")
        assertEquals(
            "应用根里 `DesktopImagePipeline.install(` 必须恰好一处（装好几处 = 后一处静默盖掉前一处）",
            1,
            DesktopSourceTree.codeOccurrences(appFile, "DesktopImagePipeline.install("),
        )
        val pipelineFile = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopImagePipeline.kt")
        val pipeline = DesktopSourceTree.codeText(pipelineFile)
        listOf(
            "SingletonImageLoader.setSafe" to "不装单例，屏上那些 AsyncImage 各自吃默认客户端 ⇒ 头还是盖不上",
            "OkHttpNetworkFetcherFactory(" to "取流要交 Coil 自带的 okhttp 供体（另起 fetcher 就是另起一条路）",
            "clientForImages(" to "客户端必须是派生那一颗，不许在这里新造一台",
        ).forEach { (token, why) ->
            assertTrue("$why（那颗件里没有「$token」）", pipeline.contains(token))
        }
        assertEquals(
            "装上那一刻必须出声一行 `D_取图`（现在是 ${DesktopSourceTree.codeOccurrences(pipelineFile, "D_取图")} 处）",
            1,
            DesktopSourceTree.codeOccurrences(pipelineFile, "D_取图"),
        )
        // 对照组：那颗件真在被扫（读空会让上面四条恒真）
        assertTrue("对照组失效：那颗件里没有 `object DesktopImagePipeline`", pipeline.contains("object DesktopImagePipeline"))
    }

    /**
     * ⑤ 负向：**只许抄那一枚**，不许顺手把整张表搬过来。
     *
     * 扫的是代码行（口径账的注释里本来就该点名那些站）；B 路多出来的风险就是"抄着抄着成了一张表"，
     * 而两张表漂出来的形状是"某一站永远取不到图"。
     */
    @Test
    fun 桌面不许把整张表搬过来() {
        val offenders = DesktopSourceTree.desktopMainSources().filter { file ->
            val code = DesktopSourceTree.codeText(file)
            OTHER_HOSTS.any { code.contains("\"$it\" to") || code.contains("$it\" to mapOf") }
        }
        assertTrue(
            "桌面又去重述对岸那张表的别行了（那些 host 一条都不该在桌面出现）：" +
                offenders.joinToString(" · ") { it.name },
            offenders.isEmpty(),
        )
        // 对照组：这些字面确实住在对岸那颗文件里（不是我把名单拼错了才"扫不到"）
        val counterpart = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("data/network/ImageHeaderPolicy.kt"))
        assertTrue("对照组失效：对岸那张表里没有 gelbooru 那一行", counterpart.contains("\"gelbooru.com\" to"))
    }

    private companion object {
        /** 对岸表里其余那些 host：桌面一枚都不许写。 */
        val OTHER_HOSTS = listOf(
            "gelbooru.com", "donmai.us", "hitomi.la", "nhentai.net", "picacg.com", "e-hentai.org", "ehgt.org",
        )
    }
}
