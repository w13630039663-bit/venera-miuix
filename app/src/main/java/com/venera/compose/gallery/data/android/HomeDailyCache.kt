package com.venera.compose.gallery.data.android

import android.content.Context
import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.toFavorite
import com.venera.compose.gallery.data.toPost
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 首页「画廊每日热门」那一条横排的**离线快照**。
 *
 * ## 为什么需要它（2026-10-19 用户原话：「主页的画廊功能加载太慢了，开启软件首页后要等
 * 10 多秒才显示画廊的部件」）
 *
 * 那条横排原先**只等网络**：`loadGalleryDailyAndArtists()` 先 `loadDaily(seed)`，
 * 而那一腿要三站并发各自落地（单站超时 12s），全部回来之后才把 8 张图铺上去。
 * 冷启动时的读数就是「首页其余部分都画好了，画廊那一条空着十来秒」。
 *
 * 首页要的其实不是「今天的榜单」，而是「一屏好看的图」。所以口径与日榜那一屏不同：
 * **先铺上次的，后台再刷**。图是同一批站方的热门，隔一天看不出差别；
 * 而"空着十秒"是每一眼都看得见的。
 *
 * ## 为什么不用 [com.venera.compose.gallery.data.GalleryFeedCache]
 *
 * 那一份是日榜整屏的快照（40~60 条 + 逐站失败原因 + 日期），跨天判据也绑在
 * 「今天必须看到今天的热门」上。首页只要 8 条、且**跨天也该照铺**（见上），
 * 两套口径混用会让其中一边的判据变成假的。共用的是可序列化形状
 * （[GalleryFavorite]）与它的两条转换，不另写第三套 schema。
 *
 * ⚠️ 这一颗住在 `gallery/data/android/`：`Context.filesDir` 是平台句柄，桌面编译面
 * 靠 `desktop/build.gradle.kts` 里那条 `android/` 子目录的 exclude 自动挡掉，不必再往
 * 那份逐颗清单里加一行。
 * （这里不写那串带星号的 glob 原文：它含 `斜杠星号`，而 Kotlin 的块注释是**可嵌套**的 ——
 *  写进来就等于在这颗文件里开了一个永不闭合的注释，整档编译失败。已踩过一次。）
 */
@Serializable
private data class HomeDailySnapshot(
    @SerialName("saved_at") val savedAt: Long = 0L,
    @SerialName("posts") val posts: List<GalleryFavorite> = emptyList(),
)

object HomeDailyCache {

    private const val FILE_NAME = "home_gallery_daily_cache.json"

    /**
     * 快照的"新鲜"窗口：这段时间内重进首页**不再发那一笔网络**。
     *
     * 30 分钟：首页那一条是「热门」而不是「实时」，半小时内换一批的概率远低于
     * 用户来回切页签的概率；而 `ON_START`/`ON_RESUME` 两个生命周期回调都会来要一次
     * （`HomeScreen` 里那两个 `LaunchedEffect` / `DisposableEffect`），不设窗口就是
     * 每切一次页签打三轮站方接口。
     */
    const val FRESH_WINDOW_MS = 30 * 60 * 1000L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = false
    }

    /** 读回来的那 8 张 + 写盘时刻（调用方用它判 [FRESH_WINDOW_MS]）。 */
    data class Snapshot(val posts: List<GalleryPost>, val savedAt: Long)

    /**
     * **同步**读。
     *
     * 与 [com.venera.compose.gallery.data.GalleryFeedCache.read] 同一条理由：调用点在
     * ViewModel 的 `init`，异步读会抢不过首帧组合，"秒出"就白做了。量级：8 条、几十个字段，
     * 一个几 KB 的 JSON，读+解析几毫秒。
     */
    fun read(context: Context): Snapshot? {
        val file = file(context)
        if (!file.exists()) return null
        // 读不出来当作没有缓存：最坏退回"等网络"，用户什么也没丢。
        val decoded = runCatching {
            json.decodeFromString<HomeDailySnapshot>(file.readText())
        }.getOrNull() ?: return null
        val posts = decoded.posts.mapNotNull { favorite ->
            favorite.site?.let { favorite.toPost(it) }
        }
        if (posts.isEmpty()) return null
        return Snapshot(posts = posts, savedAt = decoded.savedAt)
    }

    suspend fun write(context: Context, posts: List<GalleryPost>) = withContext(Dispatchers.IO) {
        if (posts.isEmpty()) return@withContext
        runCatching {
            val file = file(context)
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(
                json.encodeToString(
                    HomeDailySnapshot(
                        savedAt = System.currentTimeMillis(),
                        posts = posts.map { it.toFavorite() },
                    )
                )
            )
            // 先写临时档再改名：写到一半被杀不能把已有内容截断成半个 JSON。
            if (!tmp.renameTo(file)) {
                tmp.delete()
                throw java.io.IOException("首页画廊快照档改名失败：${file.absolutePath}")
            }
        }
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)
}
