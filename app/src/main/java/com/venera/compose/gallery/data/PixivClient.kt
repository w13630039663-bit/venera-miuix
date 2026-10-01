package com.venera.compose.gallery.data

import android.content.Context
import com.venera.compose.data.network.NoInteractiveBypassTag
import com.venera.compose.data.network.VeneraNetworkClient
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * pixiv 的**匿名头像**取数（批次 L）。
 *
 * 只用一个端点：`/ajax/user/{id}`。2026-09-30 实测（走本机代理出口）：
 *
 * - **匿名可读**，且 `User-Agent`、`Referer` 两档对照下来**都不需要**（三档都 200、同一份 687 字节）；
 * - 答复里 `body.image` / `body.imageBig` 就是头像两档尺寸（同一位实测 `…_50.png` / `…_170.png`），
 *   挑哪一档由 [GalleryArtistEndpointParse] 与 `GalleryArtistUrls.avatarUrl` 判；
 * - 但**头像图片本身**（`i.pximg.net`）不带 `Referer: https://www.pixiv.net/` 是 403（实测 403 / 200 各一次），
 *   那是取流层的事，见 `ImageHeaderPolicy`（批次 L · L4）。
 *
 * 两条红线：
 *
 * 1. **地址会过期，所以一律不落库**。实测拿一个已经改过的旧用户编号（1360347）来要，
 *    站方直接回 `error=true`。所以这里每次面板打开现取，存下来的只有"名字 + 站别"（关注名单）。
 * 2. **只做未登录读取，不带任何凭据**。这个端点在登录后会多回一堆私人字段，
 *    我们只需要一张公开头像，也就不该让任何会话材料进这条请求。
 */
class PixivClient private constructor(context: Context) {

    private val appContext = context.applicationContext

    /** 取那位 pixiv 用户的头像地址。null = 站方答上了但没有可用地址（调用方换首字母占位）。 */
    suspend fun avatarUrl(userId: Long): Result<String?> = withContext(Dispatchers.IO) {
        runCatching { GalleryArtistEndpointParse.pixivAvatarUrl(execute("$BASE/ajax/user/$userId")) }
    }

    /**
     * 从**作品号**反查这张图的作者（批次 L · L10）。
     *
     * 存在的理由：出处常写成 `pixiv.net/artworks/N`，那串是作品号 —— 拿它当用户号去要头像必然
     * `error=true`（实测），而这一跳把它换成真正的用户号，入口和头像才有着落。
     * `failure` = 作品被删/被锁（实测 15 条里 4 条 404）或读不懂；调用方什么都不摆，不说"这位没有 pixiv"。
     */
    suspend fun artworkAuthor(illustId: Long): Result<PixivArtworkAuthor> = withContext(Dispatchers.IO) {
        runCatching { GalleryArtistEndpointParse.pixivArtworkAuthor(execute("$BASE/ajax/illust/$illustId")) }
    }

    private fun execute(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            // 与日榜同一条口径：画廊这一路**不弹过盾窗口**（挂在那儿等交互会把整屏挂死）。
            .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
            .build()
        val client = VeneraNetworkClient.getInstance(appContext).okHttpClient
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("pixiv 返回 ${response.code}")
            return body
        }
    }

    companion object {
        private const val BASE = "https://www.pixiv.net"

        @Volatile
        private var INSTANCE: PixivClient? = null

        fun getInstance(context: Context): PixivClient = INSTANCE ?: synchronized(this) {
            INSTANCE ?: PixivClient(context.applicationContext).also { INSTANCE = it }
        }
    }
}
