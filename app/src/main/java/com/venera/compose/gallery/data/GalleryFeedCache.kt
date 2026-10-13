package com.venera.compose.gallery.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 画廊日榜那一屏的**离线快照**（冷启动第一帧就摆它，不转圈等网络）。
 *
 * @param savedEpochDay 这份快照写于哪一天（`LocalDate.toEpochDay`）。跨天判据要读它 ——
 *   日榜的内容按天换，"永远是缓存"就是"永远看不到今天的热门"，那是另一种坏。
 * @param posts 直接复用 [GalleryFavorite] 那一套可序列化形状与它两条既有转换
 *   （[GalleryPost.toFavorite] / [GalleryFavorite.toPost]）。**不另写第三套 schema**：
 *   这一屏要的字段与收藏要的字段是同一批，重复一份只会两份漂。
 */
@Serializable
data class GalleryFeedSnapshot(
    @SerialName("saved_epoch_day") val savedEpochDay: Long = 0L,
    @SerialName("date") val date: String = "",
    @SerialName("notice") val notice: String? = null,
    /**
     * 「这一轮哪几站没给内容、各是什么原因」，**按站一份**。
     *
     * 为什么不能只留 [notice] 那个拼好的串：提示块要按"未配账号 / 出错 / 本来没货"三档分开上屏，
     * 还要给未配账号那一档挂一枚「去配置账号」—— 而从一个 `站：原因 · 站：原因` 的
     * 显示串里反解出结构，只能靠关键词猜。**猜错不报错，只在屏上换一个颜色、少一个按钮**，
     * 正是最难查的那一类。所以结构在这一层就留住，与 `GalleryForYouSnapshot.failures` 同一形状。
     *
     * [notice] 保留不删：空态那句整段说明仍在用它，且旧档里只有它（读回来是空表，
     * 屏上少一个提示块、不会错）—— 这条兼容路径**只退化成少一块**，不会退化成假读数。
     */
    @SerialName("failures") val failures: List<GalleryForYouSiteRow> = emptyList(),
    @SerialName("posts") val posts: List<GalleryFavorite> = emptyList(),
)

/**
 * 日榜快照的落盘。文件档，与 [GalleryFavoritesStore] 同一套写法（临时文件改名 +
 * `ignoreUnknownKeys`），因为这两件事两边都踩过：
 * 写到一半被杀不能把已有内容截断成半个 JSON，而新版本写过的字段被旧版本读到不该当场炸。
 */
object GalleryFeedCache {

    private const val FILE_NAME = "gallery_feed_cache.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = false
    }

    /**
     * **同步**读。
     *
     * 为什么不走 IO 线程：调用点在 ViewModel 的 `init`，而"这一轮还要不要联网"就取决于
     * 这一次读的结果 —— 异步读会抢不过那次判断，冷启动照样发一笔请求，缓存就白存了。
     * 量级：日榜一屏 40~60 条、每条只存展示字段，整档几十 KB，读+解析几毫秒。
     */
    fun read(context: Context): GalleryFeedSnapshot? {
        val file = file(context)
        if (!file.exists()) return null
        // 读不出来就当作没有缓存。这里"静默重来"是对的方向：最坏就是回到加缓存之前那次
        // 联网加载，用户什么也没丢（这一档存的不是他产生的数据，是站方的一屏）。
        return runCatching { json.decodeFromString<GalleryFeedSnapshot>(file.readText()) }
            .getOrNull()
            ?.takeIf { it.posts.isNotEmpty() }
    }

    suspend fun write(context: Context, snapshot: GalleryFeedSnapshot) = withContext(Dispatchers.IO) {
        runCatching {
            val file = file(context)
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(snapshot))
            if (!tmp.renameTo(file)) {
                tmp.delete()
                throw java.io.IOException("日榜快照档改名失败：${file.absolutePath}")
            }
        }
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)
}
