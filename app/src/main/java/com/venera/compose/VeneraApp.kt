package com.venera.compose

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.venera.compose.data.network.VeneraNetworkClient
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 应用入口容器（S0-2；S0-6：Coil 改用 lambda Call.Factory，不再持有刚构建时的快照）。
 *
 * 存在的意义有三：
 * 1. 给 Coil3 提供一个 **Application 级单例 ImageLoader**，并让它走我们自己的
 *    OkHttpClient（PersistentCookieJar + 防盗链 ImageHeaderInterceptor），
 *    否则图片请求走 Coil 默认客户端，拷贝漫画 / 包子这类站的内页必 403。
 * 2. 后续（S1/S1.5）在这里初始化脚本引擎与网络中间件（CF 过盾回写）。
 * 3. 让 Manifest 有地方挂 Application（原先没有 android:name，等于没有全局初始化点）。
 */
class VeneraApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // 预热统一网络引擎（内含持久化 CookieJar）
        VeneraNetworkClient.getInstance(this)
        // 内容守卫：注册「源显示名 -> sourceKey」别名，供历史记录等以显示名存储的
        // 本地数据走源级预设判定（历史表 source_name 列存显示名）。
        val guard = com.venera.compose.security.guard.ContentGuardManager.getInstance(this)
        val sourceManager = com.venera.compose.source.ComicSourceManager.getInstance(this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            sourceManager.sourcesFlow.collect { sources ->
                guard.registerSourceNameAliases(sources.associate { it.name to it.key })
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val networkClient = VeneraNetworkClient.getInstance(this)
        return ImageLoader.Builder(context)
            .components {
                // S1.5: 优先接入 Venera 自定义 Fetcher 管道（动态 Header + 字节流处理）
                add(com.venera.compose.data.network.VeneraImageFetcher.Factory(this@VeneraApp, networkClient.okHttpClient))
                add(OkHttpNetworkFetcherFactory(callFactory = networkClient.okHttpClient))
            }
            .build()
    }
}