package com.venera.compose.gallery.data

import com.venera.compose.data.platform.HttpEngine
import com.venera.compose.data.platform.Logger
import com.venera.compose.data.platform.LruMap
import com.venera.compose.data.platform.NoInteractiveBypassTag
import com.venera.compose.gallery.domain.parseGalleryTagCategories
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 取一张帖子的**站方分类**（标签 → 档位数字）。
 *
 * 为什么要有这么一层：两站的 post JSON 都不给分类（实测，见
 * `gallery-tag-category-translation-and-nav-2026-09.md` §0.3），而它们的**帖子页 HTML**
 * 把每一枚标签标了 `tag-type-artist|character|copyright|general|metadata` —— 那是站方对
 * "这张画"自己的判定。逐枚去问 tag 端点要 10~30 笔请求（每站预算 12s，早算过），
 * 而**整张贴只多一笔**。
 *
 * 三条刻意的取舍：
 * - **只在「关于这张图」打开时才发**：多数浏览行为根本不看标签那一屏；
 * - **不弹过盾窗口**（[NoInteractiveBypassTag]）：这条链路失败的表现是"退回不分桶"，
 *   而过盾那个 await 没有超时，挂住就是永久转圈 —— 为一个分组读数把人卡在盾前不值；
 *   两站的帖子页实测**匿名 200 可取**（Gelbooru 锁的是 DAPI，不是帖子页）。
 * - **失败交 null，不交空表**：调用方要能分清"站方没分类"与"这一笔没成"，
 *   后者才走离线词典的画师兜底（见 [com.venera.compose.gallery.domain.buildGalleryTagBuckets]）。
 */
class GalleryTagCategories internal constructor(
    private val engine: HttpEngine,
    private val logger: Logger,
) {

    /** 同一张图反复开合面板只发一笔。存的是"这一张贴的判定"，几十条字符串，64 张足够。 */
    private val cache = LruMap<String, Map<String, Int>>(CACHE_SIZE)

    /**
     * @param pageUrl 那张帖的本站单页地址（[GalleryPost.pageUrl]，站方自己的地址，不自己拼）。
     * @return null = **没取到**（网络失败 / 非 200 / 认不出任何类名）；非 null 可能是空表。
     */
    suspend fun fetch(pageUrl: String): Map<String, Int>? = withContext(Dispatchers.IO) {
        cache.get(pageUrl)?.let { return@withContext it }
        runCatching {
            val request = Request.Builder()
                .url(pageUrl)
                .header("Accept", "text/html,application/xhtml+xml")
                .tag(NoInteractiveBypassTag::class.java, NoInteractiveBypassTag())
                .build()
            engine.okHttpClient
                .newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IOException("标签分类页返回 ${response.code}")
                    val parsed = parseGalleryTagCategories(body)
                    // 200 但一枚类名都没认出来 = 站方把那一块改版了。
                    // 交 null 而不是空表：让调用方知道"这次没读出来"，去走兜底，
                    // 而不是摆一个"站方说这张画没有任何分类"的假读数。
                    if (parsed.isEmpty()) throw IOException("分类页没有可识别的 tag-type 类名")
                    parsed
                }
        }.onSuccess { cache.put(pageUrl, it) }
            .onFailure {
                logger.info(TAG, "取 $pageUrl 的标签分类失败，退到离线词典兜底：${it.message}")
            }
            .getOrNull()
    }

    companion object {
        private const val TAG = "GalleryTagCategories"

        /** 按**条数**计（不是字节）：[LruMap] 的容量单位由这里决定，这里一条=一张帖。 */
        private const val CACHE_SIZE = 64

        @Volatile
        private var INSTANCE: GalleryTagCategories? = null

        fun getInstance(engine: HttpEngine, logger: Logger): GalleryTagCategories =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: GalleryTagCategories(engine, logger).also { INSTANCE = it }
            }
    }
}
