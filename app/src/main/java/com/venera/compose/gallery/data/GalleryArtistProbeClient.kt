package com.venera.compose.gallery.data

import android.content.Context
import com.venera.compose.data.network.NoInteractiveBypassTag
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.gallery.domain.GalleryAvatarEndpointKind
import com.venera.compose.gallery.domain.GalleryAvatarProbeEndpoint
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 按探针派生的端点取**第三方平台上的头像**（批次 L · L9）。
 *
 * 这一路存在的理由：Gelbooru 侧没有任何匿名可读的画师外链端点，唯一供体 Danbooru 又卡盾，
 * 而**帖子自带的出处**（`source`）实测覆盖 18/20 —— 站方逐条记在帖子上的地址，不必再问一次"这位是谁"。
 * 探针（`GalleryArtistAvatarProbe`）把出处换成端点，这里只管发出去、读回来。
 *
 * 三条不是顺手就定的口径：
 *
 * - **[GalleryAvatarProbeEndpoint.origin] 照字段带上**。fanbox 那台 api 对「不带 Origin」「只带 Referer」
 *   一律回 400 `general_error`（2026-09-30 14:05 逐档实测，`Origin: https://<子域>.fanbox.cc` 才 200）。
 * - **解析器由 [GalleryAvatarEndpointKind] 选，不嗅 host**。嗅错的表现是"读不懂 → 抛错"，
 *   看着像站方坏了，其实是我们走错了门。
 * - **[NoInteractiveBypassTag]**：与日榜、Danbooru 那两路同口径，画廊不弹过盾窗口。
 *   失败就交回 `failure`，画师行退回首字母座，屏上不出现"这位没有头像"那种我们没资格说的话。
 *
 * 一台量不准的留档：`baraag.net` 的 TLS 握手从**本机出口**（含代理）直接失败，`pawoo.net` 通。
 * 两家是同一套 Mastodon API，所以这里不按实例分叉；baraag 通不通要在真机上再看一次。
 */
class GalleryArtistProbeClient private constructor(context: Context) {

    private val appContext = context.applicationContext

    /** 端点对应的那张头像地址。null = 站方答上了但没有可用头像（调用方换首字母座）。 */
    suspend fun avatarUrl(endpoint: GalleryAvatarProbeEndpoint): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val body = execute(endpoint)
            when (endpoint.kind) {
                GalleryAvatarEndpointKind.FANBOX -> GalleryArtistEndpointParse.fanboxAvatarUrl(body)
                GalleryAvatarEndpointKind.MASTODON -> GalleryArtistEndpointParse.mastodonAvatarUrl(body)
            }
        }
    }

    private fun execute(endpoint: GalleryAvatarProbeEndpoint): String {
        val builder = Request.Builder()
            .url(endpoint.endpoint)
            .header("Accept", "application/json")
            .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
        endpoint.origin?.let { builder.header("Origin", it) }
        val client = VeneraNetworkClient.getInstance(appContext).okHttpClient
        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("${endpoint.kind} 返回 ${response.code}")
            return body
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: GalleryArtistProbeClient? = null

        fun getInstance(context: Context): GalleryArtistProbeClient = INSTANCE ?: synchronized(this) {
            INSTANCE ?: GalleryArtistProbeClient(context.applicationContext).also { INSTANCE = it }
        }
    }
}
