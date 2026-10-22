package com.venera.compose.gallery.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.venera.compose.data.platform.android.AndroidKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 画廊下载任务状态。
 */
enum class GalleryDownloadStatus {
    RUNNING,
    COMPLETED,
    FAILED,
}

/**
 * 画廊下载任务项。
 */
data class GalleryDownloadTask(
    val id: String,
    val post: GalleryPost,
    val title: String,
    val subtitle: String,
    val site: GallerySite,
    val thumbnailUrl: String,
    val localPath: String? = null,
    val status: GalleryDownloadStatus = GalleryDownloadStatus.RUNNING,
    val fileSize: Long = 0L,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val userId: Long? = null,
    val artistName: String? = null,
)

/**
 * 画廊统一下载任务管理器（2026-10-22）。
 *
 * 统一管理来自 yande.re、Gelbooru、Safebooru 与 Pixiv 的所有单图与多图原图下载任务。
 * 持久化记录到 SharedPreferences，支持重试、打开文件、删除与长按批量删除。
 */
class GalleryDownloadManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = AndroidKeyValueStore(context, "venera_gallery_downloads")
    private val gson = Gson()

    private val _tasks = MutableStateFlow<List<GalleryDownloadTask>>(emptyList())
    val tasks: StateFlow<List<GalleryDownloadTask>> = _tasks.asStateFlow()

    init {
        loadTasks()
    }

    private fun loadTasks() {
        val raw = prefs.getString(KEY_TASKS, null) ?: return
        try {
            val type = object : TypeToken<List<GalleryDownloadTask>>() {}.type
            val list = gson.fromJson<List<GalleryDownloadTask>>(raw, type) ?: emptyList()
            _tasks.value = list
        } catch (_: Exception) {
            _tasks.value = emptyList()
        }
    }

    private fun saveTasks() {
        try {
            val json = gson.toJson(_tasks.value)
            prefs.put(KEY_TASKS, json)
        } catch (_: Exception) {}
    }

    /**
     * 提交下载任务。
     */
    fun enqueue(
        post: GalleryPost,
        title: String? = null,
        userId: Long? = null,
        artistName: String? = null,
        onDone: ((Result<GallerySaver.Saved>) -> Unit)? = null,
    ): GalleryDownloadTask {
        val taskId = "${post.site.routeKey}-${post.id}-${System.currentTimeMillis()}"
        val effectiveTitle = title?.takeIf { it.isNotBlank() }
            ?: (if (post.site == GallerySite.PIXIV) "作品 #${post.id}" else "${post.site.displayName} #${post.id}")
        val effectiveSubtitle = when {
            !artistName.isNullOrBlank() -> "$artistName · #${post.id}"
            post.author.isNotBlank() -> "${post.author} · #${post.id}"
            else -> "#${post.id}"
        }
        val task = GalleryDownloadTask(
            id = taskId,
            post = post,
            title = effectiveTitle,
            subtitle = effectiveSubtitle,
            site = post.site,
            thumbnailUrl = post.previewUrl.ifBlank { post.largeUrl },
            status = GalleryDownloadStatus.RUNNING,
            userId = userId,
            artistName = artistName,
        )

        val updated = listOf(task) + _tasks.value
        _tasks.value = updated
        saveTasks()

        scope.launch {
            val result = GallerySaver.save(
                context = context,
                post = post,
                title = title,
                userId = userId,
                artistName = artistName,
            )
            updateTaskResult(taskId, result)
            withContext(Dispatchers.Main) {
                onDone?.invoke(result)
            }
        }

        return task
    }

    /**
     * 重试下载失败的任务。
     */
    fun retry(taskId: String) {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        val updated = _tasks.value.map {
            if (it.id == taskId) it.copy(status = GalleryDownloadStatus.RUNNING, errorMessage = null) else it
        }
        _tasks.value = updated
        saveTasks()

        scope.launch {
            val result = GallerySaver.save(
                context = context,
                post = task.post,
                title = task.title,
                userId = task.userId,
                artistName = task.artistName,
            )
            updateTaskResult(taskId, result)
        }
    }

    private fun updateTaskResult(taskId: String, result: Result<GallerySaver.Saved>) {
        val updated = _tasks.value.map { t ->
            if (t.id == taskId) {
                result.fold(
                    onSuccess = { saved ->
                        t.copy(
                            status = GalleryDownloadStatus.COMPLETED,
                            localPath = saved.path,
                            fileSize = saved.bytesSaved,
                            errorMessage = null,
                        )
                    },
                    onFailure = { err ->
                        t.copy(
                            status = GalleryDownloadStatus.FAILED,
                            errorMessage = err.message ?: "下载失败",
                        )
                    },
                )
            } else {
                t
            }
        }
        _tasks.value = updated
        saveTasks()
    }

    fun delete(taskId: String) {
        _tasks.value = _tasks.value.filterNot { it.id == taskId }
        saveTasks()
    }

    fun deleteBatch(taskIds: Set<String>) {
        _tasks.value = _tasks.value.filterNot { it.id in taskIds }
        saveTasks()
    }

    fun clearFinished() {
        _tasks.value = _tasks.value.filter { it.status == GalleryDownloadStatus.RUNNING }
        saveTasks()
    }

    companion object {
        private const val KEY_TASKS = "gallery_download_tasks"

        @Volatile
        private var INSTANCE: GalleryDownloadManager? = null

        fun getInstance(context: Context): GalleryDownloadManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: GalleryDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}