package com.venera.compose.download

import android.content.Context
import android.util.Log
import com.venera.compose.data.network.ImageHeaderPolicy
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.model.ComicChapter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 生产级下载管理器 (S6)
 *
 * 核心特性：
 * 1. 应用层并发调度池 (默认 2 并发，支持可调)
 * 2. 逐图下载 + 失败重试 3 次 + 断点续下 (已存在完整文件自动跳过)
 * 3. 目录规范与防媒体库脏化 (.nomedia)
 * 4. 任务清单落盘持久化与启动自动恢复
 * 5. 实时平滑速度计算与进度流式响应
 * 6. 支持离线阅读判断与本地文件秒开
 */
class DownloadManager private constructor(private val context: Context) {

    private val tag = "DownloadManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val networkClient = VeneraNetworkClient.getInstance(context)
    private val sourceManager = ComicSourceManager.getInstance(context)

    private val downloadsRootDir: File by lazy {
        File(context.filesDir, "downloads").apply {
            if (!exists()) mkdirs()
            // 自动注入 .nomedia 防止污染相册
            File(this, ".nomedia").let { if (!it.exists()) it.createNewFile() }
        }
    }

    private val tasksConfigFile: File by lazy {
        File(downloadsRootDir, "download_tasks.json")
    }

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private var maxConcurrency = 2
    private val semaphore by lazy { Semaphore(maxConcurrency) }
    private val activeJobs = mutableMapOf<String, Job>()

    init {
        loadTasksFromDisk()
        // 启动后台队列监控调度循环
        scope.launch {
            processQueueLoop()
        }
    }

    /**
     * 批量加入下载任务
     */
    fun enqueue(
        sourceKey: String,
        comicId: String,
        comicTitle: String,
        comicCover: String,
        chapters: List<ComicChapter>
    ) {
        val current = _tasks.value.toMutableList()
        var addedCount = 0

        for ((idx, ch) in chapters.withIndex()) {
            val taskId = "${sourceKey}_${comicId}_${ch.id}"
            val existing = current.find { it.taskId == taskId }
            if (existing != null) {
                if (existing.status == DownloadStatus.FAILED || existing.status == DownloadStatus.PAUSED) {
                    val updated = existing.copy(status = DownloadStatus.PENDING, errorMsg = null)
                    current[current.indexOf(existing)] = updated
                    addedCount++
                }
                continue
            }

            val safeTitle = ch.title.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
            val chDir = File(getComicDir(sourceKey, comicId), "${ch.order.toString().padStart(4, '0')}_$safeTitle")

            val newTask = DownloadTask(
                taskId = taskId,
                sourceKey = sourceKey,
                comicId = comicId,
                comicTitle = comicTitle,
                comicCover = comicCover,
                chapterId = ch.id,
                chapterTitle = ch.title,
                chapterOrder = ch.order,
                status = DownloadStatus.PENDING,
                downloadedPages = 0,
                totalPages = 0,
                directoryPath = chDir.absolutePath,
                createTime = System.currentTimeMillis() + idx
            )
            current.add(newTask)
            addedCount++

            // 保存漫画基本元数据，方便离线书架扫描
            saveComicMetadata(sourceKey, comicId, comicTitle, comicCover)
        }

        if (addedCount > 0) {
            _tasks.value = current
            saveTasksToDisk()
        }
    }

    fun pause(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        updateTaskStatus(taskId, DownloadStatus.PAUSED)
    }

    fun resume(taskId: String) {
        updateTaskStatus(taskId, DownloadStatus.PENDING)
    }

    fun pauseAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        val updated = _tasks.value.map {
            if (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING) {
                it.copy(status = DownloadStatus.PAUSED, speedText = "")
            } else it
        }
        _tasks.value = updated
        saveTasksToDisk()
    }

    fun resumeAll() {
        val updated = _tasks.value.map {
            if (it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED) {
                it.copy(status = DownloadStatus.PENDING, errorMsg = null)
            } else it
        }
        _tasks.value = updated
        saveTasksToDisk()
    }

    fun cancel(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        val current = _tasks.value.toMutableList()
        current.removeAll { it.taskId == taskId }
        _tasks.value = current
        saveTasksToDisk()
    }

    fun delete(taskId: String, deleteFiles: Boolean = true) {
        val task = _tasks.value.find { it.taskId == taskId }
        cancel(taskId)
        if (deleteFiles && task != null && task.directoryPath.isNotBlank()) {
            try {
                File(task.directoryPath).deleteRecursively()
            } catch (e: Exception) {
                Log.w(tag, "Failed to delete files for task $taskId", e)
            }
        }
    }

    fun clearCompleted() {
        val current = _tasks.value.toMutableList()
        current.removeAll { it.status == DownloadStatus.COMPLETED }
        _tasks.value = current
        saveTasksToDisk()
    }

    /**
     * 判断某个章节是否已经下载完毕
     */
    fun isChapterDownloaded(sourceKey: String, comicId: String, chapterId: String): Boolean {
        val task = _tasks.value.find { it.sourceKey == sourceKey && it.comicId == comicId && it.chapterId == chapterId }
        if (task?.status == DownloadStatus.COMPLETED) return true

        // 检查磁盘目录是否存在且包含有效图片
        val comicDir = getComicDir(sourceKey, comicId)
        if (!comicDir.exists()) return false
        val chapterDirs = comicDir.listFiles { f -> f.isDirectory } ?: return false
        for (dir in chapterDirs) {
            val infoFile = File(dir, "chapter.json")
            if (infoFile.exists()) {
                try {
                    val json = JSONObject(infoFile.readText())
                    if (json.optString("chapterId") == chapterId) {
                        val images = dir.listFiles { f -> f.isFile && (f.extension == "jpg" || f.extension == "png" || f.extension == "webp") }
                        return !images.isNullOrEmpty()
                    }
                } catch (_: Exception) {}
            }
        }
        return false
    }

    /**
     * 获取已下载章节的图片本地文件列表（按自然序号排序）
     */
    fun getDownloadedChapterFiles(sourceKey: String, comicId: String, chapterId: String): List<File>? {
        val task = _tasks.value.find { it.sourceKey == sourceKey && it.comicId == comicId && it.chapterId == chapterId }
        val targetDir = if (task != null && task.directoryPath.isNotBlank()) {
            File(task.directoryPath)
        } else {
            val comicDir = getComicDir(sourceKey, comicId)
            comicDir.listFiles { f -> f.isDirectory }?.find { dir ->
                val info = File(dir, "chapter.json")
                info.exists() && runCatching { JSONObject(info.readText()).optString("chapterId") == chapterId }.getOrDefault(false)
            }
        }

        if (targetDir != null && targetDir.exists()) {
            val files = targetDir.listFiles { f ->
                f.isFile && (f.extension == "jpg" || f.extension == "png" || f.extension == "webp") && f.length() > 0
            }?.sortedBy { it.name }
            if (!files.isNullOrEmpty()) {
                return files
            }
        }
        return null
    }

    fun getComicDir(sourceKey: String, comicId: String): File {
        val safeKey = sourceKey.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val safeId = comicId.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        return File(File(downloadsRootDir, safeKey), safeId).apply {
            if (!exists()) mkdirs()
        }
    }

    // ================================= 内部队列与下载执行 =================================

    private suspend fun processQueueLoop() {
        while (true) {
            val pendingTask = _tasks.value.firstOrNull { it.status == DownloadStatus.PENDING }
            if (pendingTask != null && activeJobs.size < maxConcurrency) {
                val taskId = pendingTask.taskId
                updateTaskStatus(taskId, DownloadStatus.DOWNLOADING)
                val job = scope.launch {
                    try {
                        runDownloadTask(taskId)
                    } catch (e: CancellationException) {
                        Log.d(tag, "Task $taskId canceled")
                    } catch (e: Exception) {
                        Log.e(tag, "Task $taskId unhandled error", e)
                        updateTaskError(taskId, e.message ?: "下载发生未知异常")
                    } finally {
                        activeJobs.remove(taskId)
                    }
                }
                activeJobs[taskId] = job
            }
            delay(500)
        }
    }

    private suspend fun runDownloadTask(taskId: String) {
        val task = _tasks.value.find { it.taskId == taskId } ?: return
        val chapterDir = File(task.directoryPath).apply { if (!exists()) mkdirs() }

        // 写入章节元数据
        File(chapterDir, "chapter.json").writeText(
            JSONObject().apply {
                put("chapterId", task.chapterId)
                put("title", task.chapterTitle)
                put("order", task.chapterOrder)
            }.toString()
        )

        val source = sourceManager.getSource(task.sourceKey)
        if (source == null) {
            updateTaskError(taskId, "找不到对应漫画源: ${task.sourceKey}")
            return
        }

        // 1. 获取章节全部图片 URL
        val pagesResult = source.getChapterPages(task.comicId, task.chapterId)
        if (pagesResult.isFailure || pagesResult.getOrNull() == null || pagesResult.getOrNull()!!.pages.isEmpty()) {
            val err = pagesResult.exceptionOrNull()?.message ?: "获取章节图片列表为空或失败"
            updateTaskError(taskId, err)
            return
        }

        val chapterPages = pagesResult.getOrNull()!!
        val pageUrlsOrKeys = chapterPages.pages
        val totalCount = pageUrlsOrKeys.size
        updateTaskProgress(taskId, downloaded = 0, total = totalCount)

        // 2. 逐图下载与重试
        var downloaded = 0
        var lastBytes = 0L
        var lastTime = System.currentTimeMillis()

        for ((idx, item) in pageUrlsOrKeys.withIndex()) {
            if (!currentCoroutineContext().isActive) break

            val pageFileName = "${(idx + 1).toString().padStart(4, '0')}.jpg"
            val targetFile = File(chapterDir, pageFileName)

            // 如果文件已存在且大小正常，视为已下载
            if (targetFile.exists() && targetFile.length() > 1024) {
                downloaded++
                updateTaskProgress(taskId, downloaded = downloaded, total = totalCount)
                continue
            }

            // 解析真实 URL（如果源声明了 useOnImageLoad）
            var pageHeaders = chapterPages.headers
            val realUrl = if (chapterPages.useOnImageLoad && source is com.venera.compose.source.js.JsComicSource) {
                val resolved = source.resolveImageLoadingConfig(
                    comicId = task.comicId,
                    epId = task.chapterId,
                    imageKey = item,
                    nl = null
                )
                val config = resolved.getOrNull()
                if (config != null) {
                    if (config.headers.isNotEmpty()) {
                        pageHeaders = pageHeaders + config.headers
                    }
                    config.url
                } else {
                    item
                }
            } else {
                item
            }

            var downloadSuccess = false
            var attempt = 0
            while (attempt < 3 && !downloadSuccess && currentCoroutineContext().isActive) {
                attempt++
                try {
                    val bytesWritten = downloadSingleImage(realUrl, pageHeaders, targetFile)
                    if (bytesWritten > 0) {
                        downloadSuccess = true
                        downloaded++
                        // 速度计算
                        val now = System.currentTimeMillis()
                        val diffTime = now - lastTime
                        lastBytes += bytesWritten
                        if (diffTime >= 800) {
                            val speed = (lastBytes * 1000f / diffTime)
                            val speedText = formatSpeed(speed)
                            updateTaskSpeed(taskId, speedText)
                            lastBytes = 0
                            lastTime = now
                        }
                        updateTaskProgress(taskId, downloaded = downloaded, total = totalCount)
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Image $idx attempt $attempt failed for ${task.chapterTitle}: ${e.message}")
                    if (attempt >= 3) {
                        updateTaskError(taskId, "第 ${idx + 1} 页下载重试3次仍失败: ${e.message}")
                        return
                    }
                    delay(600)
                }
            }
        }

        if (downloaded >= totalCount) {
            updateTaskStatus(taskId, DownloadStatus.COMPLETED)
        }
    }

    private fun downloadSingleImage(url: String, extraHeaders: Map<String, String>, targetFile: File): Long {
        val headers = ImageHeaderPolicy.headersFor(url) + extraHeaders
        val reqBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }

        val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        val response = networkClient.okHttpClient.newCall(reqBuilder.build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code} on image $url")
        }

        val body = response.body ?: throw IOException("Empty response body")
        var bytesCopied = 0L
        body.byteStream().use { input ->
            FileOutputStream(tmpFile).use { output ->
                val buffer = ByteArray(8192)
                var bytes = input.read(buffer)
                while (bytes >= 0) {
                    output.write(buffer, 0, bytes)
                    bytesCopied += bytes
                    bytes = input.read(buffer)
                }
            }
        }
        if (tmpFile.exists() && bytesCopied > 0) {
            tmpFile.renameTo(targetFile)
        }
        return bytesCopied
    }

    private fun formatSpeed(bytesPerSec: Float): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024 * 1024))
            bytesPerSec >= 1024 -> String.format("%.0f KB/s", bytesPerSec / 1024)
            else -> String.format("%.0f B/s", bytesPerSec)
        }
    }

    private fun saveComicMetadata(sourceKey: String, comicId: String, title: String, cover: String) {
        try {
            val comicDir = getComicDir(sourceKey, comicId)
            val info = File(comicDir, "comic_info.json")
            info.writeText(
                JSONObject().apply {
                    put("id", comicId)
                    put("title", title)
                    put("cover", cover)
                    put("sourceKey", sourceKey)
                    put("updateTime", System.currentTimeMillis())
                }.toString()
            )
        } catch (_: Exception) {}
    }

    private fun updateTaskStatus(taskId: String, status: DownloadStatus) {
        val list = _tasks.value.map {
            if (it.taskId == taskId) it.copy(status = status, speedText = if (status != DownloadStatus.DOWNLOADING) "" else it.speedText, updateTime = System.currentTimeMillis())
            else it
        }
        _tasks.value = list
        saveTasksToDisk()
    }

    private fun updateTaskProgress(taskId: String, downloaded: Int, total: Int) {
        val list = _tasks.value.map {
            if (it.taskId == taskId) it.copy(downloadedPages = downloaded, totalPages = total, updateTime = System.currentTimeMillis())
            else it
        }
        _tasks.value = list
    }

    private fun updateTaskSpeed(taskId: String, speedText: String) {
        val list = _tasks.value.map {
            if (it.taskId == taskId) it.copy(speedText = speedText)
            else it
        }
        _tasks.value = list
    }

    private fun updateTaskError(taskId: String, errorMsg: String) {
        val list = _tasks.value.map {
            if (it.taskId == taskId) it.copy(status = DownloadStatus.FAILED, errorMsg = errorMsg, speedText = "", updateTime = System.currentTimeMillis())
            else it
        }
        _tasks.value = list
        saveTasksToDisk()
    }

    // ================================= 任务持久化 =================================

    private fun saveTasksToDisk() {
        scope.launch {
            try {
                val array = JSONArray()
                for (task in _tasks.value) {
                    val obj = JSONObject().apply {
                        put("taskId", task.taskId)
                        put("sourceKey", task.sourceKey)
                        put("comicId", task.comicId)
                        put("comicTitle", task.comicTitle)
                        put("comicCover", task.comicCover)
                        put("chapterId", task.chapterId)
                        put("chapterTitle", task.chapterTitle)
                        put("chapterOrder", task.chapterOrder)
                        // 若处于正在下载中，落盘记为 PAUSED 避免下次启动直接冲
                        put("status", if (task.status == DownloadStatus.DOWNLOADING) DownloadStatus.PAUSED.name else task.status.name)
                        put("downloadedPages", task.downloadedPages)
                        put("totalPages", task.totalPages)
                        put("errorMsg", task.errorMsg ?: "")
                        put("directoryPath", task.directoryPath)
                        put("createTime", task.createTime)
                        put("updateTime", task.updateTime)
                    }
                    array.put(obj)
                }
                tasksConfigFile.writeText(array.toString())
            } catch (e: Exception) {
                Log.w(tag, "Failed to save tasks to disk", e)
            }
        }
    }

    private fun loadTasksFromDisk() {
        if (!tasksConfigFile.exists()) return
        try {
            val text = tasksConfigFile.readText()
            val array = JSONArray(text)
            val list = mutableListOf<DownloadTask>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawStatus = obj.optString("status", DownloadStatus.PENDING.name)
                val status = try { DownloadStatus.valueOf(rawStatus) } catch (_: Exception) { DownloadStatus.PENDING }
                list.add(
                    DownloadTask(
                        taskId = obj.getString("taskId"),
                        sourceKey = obj.optString("sourceKey", ""),
                        comicId = obj.optString("comicId", ""),
                        comicTitle = obj.optString("comicTitle", "未知漫画"),
                        comicCover = obj.optString("comicCover", ""),
                        chapterId = obj.optString("chapterId", ""),
                        chapterTitle = obj.optString("chapterTitle", "未知章节"),
                        chapterOrder = obj.optInt("chapterOrder", 0),
                        status = if (status == DownloadStatus.DOWNLOADING) DownloadStatus.PAUSED else status,
                        downloadedPages = obj.optInt("downloadedPages", 0),
                        totalPages = obj.optInt("totalPages", 0),
                        errorMsg = obj.optString("errorMsg").ifBlank { null },
                        directoryPath = obj.optString("directoryPath", ""),
                        createTime = obj.optLong("createTime", System.currentTimeMillis()),
                        updateTime = obj.optLong("updateTime", System.currentTimeMillis())
                    )
                )
            }
            _tasks.value = list
        } catch (e: Exception) {
            Log.e(tag, "Failed to load tasks from disk", e)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
