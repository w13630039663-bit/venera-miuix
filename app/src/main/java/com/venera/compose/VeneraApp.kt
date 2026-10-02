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
        StartupTrace.anchor()
        StartupTrace.mark("App.onCreate enter")
        // data/db 的平台接线：只装一个「怎么从 Context 造出口」的 lambda，不碰 SQLite，
        // 建库时机仍是第一次取用 DAO / 收藏管理器（与改造前一致）。
        com.venera.compose.data.platform.android.AndroidDatabasePorts.install()
        // 网络引擎：主线程只留 UA 策略初始化（1~4ms），CookieJar 与 OkHttpClient 的
        // 47~66ms 装配交给下面这条预热协程；不预热的话这笔钱会改在首图那刻由主线程付。
        StartupTrace.timed("App: VeneraNetworkClient.getInstance") { VeneraNetworkClient.getInstance(this) }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            StartupTrace.timed("App: warm okHttpClient [bg]") {
                VeneraNetworkClient.getInstance(this@VeneraApp).okHttpClient
            }
        }
        // 内容守卫：注册「源显示名 -> sourceKey」别名，供历史记录等以显示名存储的
        // 本地数据走源级预设判定（历史表 source_name 列存显示名）。
        val guard = StartupTrace.timed("App: ContentGuardManager.getInstance") {
            com.venera.compose.security.guard.ContentGuardManager.getInstance(this)
        }
        val sourceManager = StartupTrace.timed("App: ComicSourceManager.getInstance") {
            com.venera.compose.source.ComicSourceManager.getInstance(this)
        }
        StartupTrace.mark("App: before collect launches")
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            sourceManager.sourcesFlow.collect { sources ->
                guard.registerSourceNameAliases(sources.associate { it.name to it.key })
            }
        }
        // 追更检查的排程跟着「追更收藏夹」这一个真相走：选定即排上每日检查，取消即撤掉。
        // StateFlow 会立刻重放当前值，所以冷启动时已开启的追更也会补排一次。
        // 不放进 VeneraPreferences 的 setter —— 偏好层不该顺手启动后台任务。
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            StartupTrace.mark("App: followUpdates collect starts")
            com.venera.compose.data.prefs.VeneraPreferences.getInstance(this@VeneraApp)
                .followUpdatesFolder.collect { folder ->
                    if (folder != null) {
                        StartupTrace.timed("App: FollowUpdatesScheduler.enable") {
                            com.venera.compose.data.db.FollowUpdatesScheduler.enable(this@VeneraApp)
                        }
                    } else {
                        com.venera.compose.data.db.FollowUpdatesScheduler.disable(this@VeneraApp)
                    }
                }
        }
        StartupTrace.mark("App.onCreate exit")
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val started = android.os.SystemClock.elapsedRealtime()
        val networkClient = VeneraNetworkClient.getInstance(this)
        val loader = ImageLoader.Builder(context)
            .logger(com.venera.compose.data.network.VeneraImageLogger)
            .components {
                // 只有需要改写字节的两条路（JM 去混淆 / EH 雪碧图）走自定义 fetcher；
                // 其余图片走 Coil 自带的网络 fetcher —— 它会流式写进磁盘缓存并以文件源解码。
                add(com.venera.compose.data.network.VeneraImageFetcher.Factory(this@VeneraApp, networkClient.okHttpClient))
                add(OkHttpNetworkFetcherFactory(callFactory = com.venera.compose.data.network.ImageFetchCallFactory(networkClient.okHttpClient)))
            }
            .build()
        StartupTrace.recordElapsed("App: Coil newImageLoader", started)
        return loader
    }
}