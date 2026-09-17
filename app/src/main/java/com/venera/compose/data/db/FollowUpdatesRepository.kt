package com.venera.compose.data.db

import android.content.Context
import com.venera.compose.source.ComicSourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * 单个漫画的检查结果（对齐原版 `ComicUpdateResult`）。
 */
data class ComicUpdateResult(val updated: Boolean, val errorMessage: String? = null)

/**
 * 整批检查的进度（对齐原版 `UpdateProgress`）。
 */
data class UpdateProgress(
    val total: Int,
    val current: Int,
    val errors: Int,
    val updated: Int,
    val currentTitle: String? = null,
)

/**
 * 追更检查（S5-5），对齐原版 `foundation/follow_updates.dart`。
 *
 * 关键规则（与原版一致，改动会误报或漏报）：
 * - **节流**：距上次检查不足 [CHECK_THROTTLE_MS] 的条目直接跳过（除非 [ignoreCheckTime]）
 * - **并发 5**：原版用 5 个消费者从 Channel 取任务
 * - **每完成 5 个延迟** [THROTTLE_STEP_MS]（最多 10 秒），避免打爆源站
 * - **重试 3 次**，每次失败间隔 2 秒
 * - 判定「有新更新」的依据是**服务端给出的更新时间字符串变化**，不是章节数
 */
class FollowUpdatesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val favorites = LocalFavoritesManager.getInstance(appContext)
    private val sourceManager = ComicSourceManager.getInstance(appContext)

    /** 单个漫画：拉详情 → 比更新时间 → 写回。 */
    suspend fun updateComic(
        comic: FavoriteItemWithUpdateInfo,
        folder: String,
    ): ComicUpdateResult {
        var retries = 3
        while (true) {
            try {
                val details = withContext(Dispatchers.IO) {
                    sourceManager.getComicDetails(comic.item.sourceKey, comic.item.id).getOrThrow()
                }

                // 先把最新的标题/封面/标签写回收藏（原版 updateInfo）
                favorites.updateInfo(
                    folder,
                    comic.item.copy(
                        name = details.comic.title,
                        author = details.author.ifBlank { comic.item.author },
                        coverPath = details.comic.cover.ifBlank { comic.item.coverPath },
                        tags = details.comic.tags.ifEmpty { comic.item.tags },
                    ),
                )

                val newTime = details.updateTime
                return if (!newTime.isNullOrBlank() && newTime != comic.updateTime) {
                    favorites.updateUpdateTime(folder, comic.item.id, comic.item.sourceKey, newTime)
                    ComicUpdateResult(updated = true)
                } else {
                    // 没有变化也要刷新检查时间，否则下次仍会被判为「未检查」
                    favorites.updateCheckTime(folder, comic.item.id, comic.item.sourceKey)
                    ComicUpdateResult(updated = false)
                }
            } catch (e: Exception) {
                delay(RETRY_DELAY_MS)
                retries--
                if (retries == 0) return ComicUpdateResult(false, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * 检查整个收藏夹。
     *
     * @param ignoreCheckTime true 时忽略节流，强制全量检查
     * @param onProgress 进度回调（会在 IO 线程之外被调用，调用方自行切线程）
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun updateFolder(
        folder: String,
        ignoreCheckTime: Boolean = false,
        onProgress: (UpdateProgress) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val all = favorites.getComicsWithUpdatesInfo(folder)

        val toCheck = if (ignoreCheckTime) {
            all
        } else {
            val now = System.currentTimeMillis()
            all.filter { comic ->
                val last = comic.lastCheckTime
                last == null || now - last >= CHECK_THROTTLE_MS
            }
        }

        val total = toCheck.size
        val done = AtomicInteger(0)
        val errors = AtomicInteger(0)
        val updated = AtomicInteger(0)

        onProgress(UpdateProgress(total, 0, 0, 0))
        if (total == 0) return@withContext

        val channel = Channel<FavoriteItemWithUpdateInfo>(Channel.UNLIMITED)

        supervisorScope {
            // 生产者：配合节流（每 5 个歇一下，对齐原版）
            launch {
                var c = 0
                for (comic in toCheck) {
                    channel.send(comic)
                    c++
                    if (c % THROTTLE_BATCH == 0) delay(THROTTLE_STEP_MS)
                }
                channel.close()
            }

            // 5 个消费者
            repeat(CONCURRENCY) {
                launch {
                    for (comic in channel) {
                        val result = updateComic(comic, folder)
                        if (result.updated) updated.incrementAndGet()
                        if (result.errorMessage != null) errors.incrementAndGet()
                        val cur = done.incrementAndGet()
                        onProgress(
                            UpdateProgress(
                                total = total,
                                current = cur,
                                errors = errors.get(),
                                updated = updated.get(),
                                currentTitle = comic.item.name,
                            )
                        )
                    }
                }
            }
        }
    }

    /** 有新更新的条目（用于更新列表页）。 */
    suspend fun getUpdates(folder: String): List<FavoriteItemWithUpdateInfo> = favorites.getUpdates(folder)

    suspend fun countUpdates(folder: String): Int = favorites.countUpdates(folder)

    suspend fun markAsRead(id: String, sourceKey: String) = favorites.markAsRead(id, sourceKey)

    companion object {
        /** 距上次检查不足 1 天则跳过（原版 `inDays < 1`）。 */
        const val CHECK_THROTTLE_MS = 24 * 60 * 60 * 1000L
        const val CONCURRENCY = 5
        const val THROTTLE_BATCH = 5
        const val THROTTLE_STEP_MS = 2_000L
        const val RETRY_DELAY_MS = 2_000L

        @Volatile
        private var INSTANCE: FollowUpdatesRepository? = null

        fun getInstance(context: Context): FollowUpdatesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FollowUpdatesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
