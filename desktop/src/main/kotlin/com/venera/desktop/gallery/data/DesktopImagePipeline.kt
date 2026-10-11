package com.venera.desktop.gallery.data

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.venera.compose.data.platform.HttpEngine
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * 桌面取图片字节那一跳的**防盗链头**（第七十九批，B 路：不碰 `app/`）。
 *
 * ## 这一格断在哪
 *
 * 批 43 三帧读数：pixiv 那一档把头像地址取到并落了档，而屏上仍是字母座。断点不在链里，在**取字节**那一层 ——
 * pixiv 图床无 `Referer` 恒 403（对岸注释里留着成对读数：无 = 146 字节错误页 / 带 = 200 的 5,149 字节真图），
 * 而桌面那一路走的是 Coil 的**默认单例客户端**：`DesktopHttpEngine` 那台只管画廊那十颗接口客户端，
 * `AsyncImage` 背后那台谁都没配过。
 *
 * ## 为什么是"抄一枚"而不是"接那张表"
 *
 * 那张表住在 `app/…/data/network/ImageHeaderPolicy.kt`，而 `data/network` 整目录不在桌面编译面里
 * （禁引集合那本账见 `DesktopSharedFaceLedgerTest`）。A 路是把对岸那两处的常量改成转交自纯 JVM 那颗，
 * 那要点头；没点到，所以这里走 B 路：**桌面自持同一枚值**。
 *
 * ⚠️ B 路的代价是"两份会漂"，所以把抄的范围钉到最小，并由判据看着：
 * 只抄 `pximg.net` 那一枚（⑤ 机械扫其余 host 一颗都不许多写）、值逐字回读对岸那一行核对（③）、
 * 客户端从引擎那台**派生**而不是新造（②）、装的那一刻出声（④）。
 * 对岸改了那枚值而这里没跟上时，红的是判据 ③ 而不是屏上那片字母座。
 */
internal const val DESKTOP_PIXIV_IMAGE_HOST: String = "pximg.net"

/** 与对岸 `"pximg.net" to mapOf("Referer" to …)` 那一行**逐字同值**（判据 ③ 回读核对）。 */
internal const val DESKTOP_PIXIV_REFERER: String = "https://www.pixiv.net/"

/** 按 host 后缀给那一枚头；其余 host 原样交下去（对照组由判据 ① 钉着）。 */
internal class DesktopImageHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host.lowercase()
        // 后缀匹配必须带那颗点：`cdn.notpximg.net` 不是 `pximg.net`（判据 ① 的第三枚对照）
        val pixiv = host == DESKTOP_PIXIV_IMAGE_HOST || host.endsWith(".$DESKTOP_PIXIV_IMAGE_HOST")
        if (!pixiv) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("Referer", DESKTOP_PIXIV_REFERER).build())
    }
}

internal object DesktopImagePipeline {

    /** 图片那一台 = 引擎那台 + 恰好一枚拦截器（代理与超时全继承：`--proxy=` 对图片这一路也得管用）。 */
    internal fun clientForImages(base: OkHttpClient): OkHttpClient =
        base.newBuilder().addInterceptor(DesktopImageHeaderInterceptor()).build()

    /**
     * 装上 Coil 的单例供体：**应用根一次**，且必须早于任何取图。
     *
     * 不装单例的话，屏上那些 `AsyncImage` 各吃 Coil 的默认客户端，头仍然盖不上；
     * 而预取那一族（批 47）吃的就是这同一份单例 —— 另起一枚 ImageLoader 就是"两条轨"那一族。
     */
    fun install(engine: HttpEngine) {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { clientForImages(engine.okHttpClient) }))
                    // 阅读那一屏的一页（批 81）：字节由引擎取（含解混淆），所以走自定义 fetcher。
                    // ⚠️ 这两行**成对**：只挂 fetcher 不挂 Keyer 时 Coil 的 `key()` 交回 null，
                    // 语义是"这一格不进内存缓存" —— 屏上完全看不出异常，而每次滚回来都重新解混淆一遍。
                    add(DesktopComicPageKeyer)
                    add(DesktopComicPageFetcher.Factory())
                }
                .build()
        }
        println("D_取图 装=防盗链一枚 站=$DESKTOP_PIXIV_IMAGE_HOST 客户端=引擎那台派生 页取数=挂了两枚(键+fetcher)")
    }
}
