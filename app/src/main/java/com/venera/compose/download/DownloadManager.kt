package com.venera.compose.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.venera.compose.data.network.ImageHeaderPolicy
import com.venera.compose.data.network.ImagePipelinePolicy
import com.venera.compose.data.prefs.VeneraPreferences
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

    /**
     * 当前存储根：每次现读，用户在设置里改目录后无需重启进程即生效。
     * 建根与 .nomedia 注入也在这里，保证无论落在哪块存储上都不污染相册。
     */
    private val downloadsRootDir: File
        get() = ComicStorageRoot.resolve(context).also { ComicStorageRoot.ensureRoot(it) }

    // 任务清单恒定留在应用私有目录，不跟随漫画数据搬家：
    // 否则换一次存储根就让进行中的下载队列整体失联，已完成任务也会被判定成未下载。
    private val tasksConfigFile: File by lazy {
        ComicStorageRoot.tasksFile(context).apply { parentFile?.mkdirs() }
    }

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    // 并发数走偏好（默认 2，1–16）。semaphore 是 lazy 的，因此首次下载时取当时值；
    // 改完偏好在下一轮下载队列启动时生效，不强行打断进行中的任务。
    private var maxConcurrency = VeneraPreferences.getInstance(context).downloadThreads.value
    private val semaphore by lazy { Semaphore(maxConcurrency) }
    private val activeJobs = mutableMapOf<String, Job>()

    init {
        createNotificationChannel()
        loadTasksFromDisk()
        // 启动后台队列监控调度循环
        scope.launch {
            processQueueLoop()
        }
    }

    // ================================= 系统前台下载通知 =================================

    /** 低打扰下载进度渠道（Channel ID: venera_download）。 */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "离线下载",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "漫画离线下载进度与完成状态"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    /** Android 13+ 通知运行时权限（未授予时静默跳过，不打扰用户）。 */
    private fun canPostNotification(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** 每个任务一条进度通知（taskId 尾部哈希作 notificationId，互不覆盖）。 */
    private fun notifyTaskProgress(task: DownloadTask) {
        if (!canPostNotification()) return
        try {
            val notificationId = task.taskId.hashCode()
            val text = "${task.downloadedPages}/${task.totalPages} 页" +
                (if (task.speedText.isNotBlank()) " · ${task.speedText}" else "")
            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("正在下载：${task.comicTitle} - ${task.chapterTitle}")
                .setContentText(text)
                .setOngoing(true)
                .setSilent(true)
            if (task.totalPages > 0) {
                builder.setProgress(task.totalPages, task.downloadedPages, false)
            }
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: Exception) {
            Log.w(tag, "notifyTaskProgress failed", e)
        }
    }

    /** 任务完成/失败：更新对应通知并转为可清除。 */
    private fun notifyTaskFinished(task: DownloadTask) {
        if (!canPostNotification()) return
        try {
            val notificationId = task.taskId.hashCode()
            val (title, icon) = if (task.status == DownloadStatus.COMPLETED) {
                "下载完成：${task.comicTitle} - ${task.chapterTitle}" to android.R.drawable.stat_sys_download_done
            } else {
                "下载失败：${task.errorMsg ?: "未知错误"}" to android.R.drawable.stat_notify_error
            }
            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText("${task.comicTitle} - ${task.chapterTitle}")
                .setOngoing(false)
                .setAutoCancel(true)
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: Exception) {
            Log.w(tag, "notifyTaskFinished failed", e)
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

    /**
     * 存储根搬家后把任务里记的绝对路径前缀换到新根，否则已完成章节会被
     * [isChapterDownloaded] 判成未下载、点一下就直接重下一遍。
     */
    fun relocateTasks(oldRoot: File, newRoot: File): Int {
        val oldPrefix = oldRoot.absolutePath.trimEnd('/')
        var changed = 0
        val updated = _tasks.value.map { task ->
            if (task.directoryPath.startsWith(oldPrefix + "/")) {
                changed++
                task.copy(directoryPath = newRoot.absolutePath + task.directoryPath.removePrefix(oldPrefix))
            } else task
        }
        if (changed > 0) {
            _tasks.value = updated
            saveTasksToDisk()
        }
        return changed
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

            // 断点续下判定：文件已存在且**内容确实是图片**才跳过。
            // ⚠️ 历史缺陷两连：①旧版本把混淆未还原的原始字节落盘；②显式 Accept-Encoding
            // 导致 OkHttp 关闭透明解压，落盘的是 Brotli/GZIP 压缩包 —— 两类坏图都 >1KB，
            // 会被大小检查短路跳过，重试永远无法治愈。
            // 修复：跳过前读取文件头做魔数校验，非图片一律重新下载覆盖；
            // JM 混淆图恒定不跳过（落盘前已去混淆，重下即治愈历史坏图）。
            // 判定必须在 realUrl 解析之后（useOnImageLoad 源的 item 是 imageKey，非 URL）。
            val isScrambledImage = ImagePipelinePolicy.getScrambleNum(realUrl) > 1
            val existingValid = targetFile.exists() && targetFile.length() > 1024 &&
                runCatching { isValidImageBytes(targetFile.readBytes()) }.getOrDefault(false)
            if (!isScrambledImage && existingValid) {
                downloaded++
                updateTaskProgress(taskId, downloaded = downloaded, total = totalCount)
                continue
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
            _tasks.value.find { it.taskId == taskId }?.let { notifyTaskFinished(it) }
        }
    }

    private fun downloadSingleImage(url: String, extraHeaders: Map<String, String>, targetFile: File): Long {
        // 关键修复：绝不能向 OkHttp 传入显式 Accept-Encoding（禁漫源 JS 的 getImgHeaders
        // 携带 "gzip, deflate, br, zstd"），否则 OkHttp 会关闭自动透明解压，
        // body.byteStream() 拿到的是原始 Brotli/GZIP 压缩包，BitmapFactory 无法解码，
        // 落盘全变「读取失败」的坏图。剥离后 OkHttp 自动加 Accept-Encoding: gzip
        // 并透明解压，拿到的是真实图片字节。
        // 过滤放在**合并之后**：ImageHeaderPolicy（JS 源 publish 的防盗链头）与
        // extraHeaders（resolveImageLoadingConfig 返回的头）两条路都可能携带
        // Accept-Encoding，只过滤单路会漏。
        val mergedHeaders = (ImageHeaderPolicy.headersFor(url) + extraHeaders)
            .filterKeys { !it.equals("Accept-Encoding", ignoreCase = true) }
        val reqBuilder = Request.Builder()
            .url(url)
            // 图片取流不进域名熔断口径（理由见 ImageFetchTag）：下载失败该由下载任务自己重试，
            // 而不是顺手把整站拉黑 60s，连带元数据与在线封面一起失败。
            .tag(com.venera.compose.data.network.ImageFetchTag::class.java, com.venera.compose.data.network.ImageFetchTag())
        mergedHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

        val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        val response = networkClient.okHttpClient.newCall(reqBuilder.build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code} on image $url")
        }

        val body = response.body ?: throw IOException("Empty response body")
        // 禁漫（JMComic）等源的图片是分块混淆打乱的：在线阅读由 ImagePipelinePolicy 在
        // 解码前动态还原，但下载必须**落盘前还原**，否则本地书架读到的永远是乱序二进制，
        // 点进去无法解码或整页空白。这里读全量字节 → 按 scrambleNum 还原 → 再写盘。
        val rawBytes = body.byteStream().use { it.readBytes() }
        response.close()
        if (rawBytes.isEmpty()) throw IOException("Empty image body: $url")

        // 校验真实图片魔数（JPEG/PNG/GIF/WebP）：防范代理 403 HTML 页、
        // 压缩残留或任何非图片数据被当图落盘 —— 校验失败即抛错走重试。
        if (!isValidImageBytes(rawBytes)) {
            throw IOException("Invalid image content received (length=${rawBytes.size})")
        }

        val scrambleNum = ImagePipelinePolicy.getScrambleNum(url)
        val finalBytes = if (scrambleNum > 1) {
            ImagePipelinePolicy.descrambleJmImage(rawBytes, scrambleNum)
        } else {
            rawBytes
        }

        FileOutputStream(tmpFile).use { output -> output.write(finalBytes) }

        // 原子安全落地：rename 失败（跨分区/占用等）时降级 copy，绝不留下 .tmp 孤儿
        if (targetFile.exists()) targetFile.delete()
        if (!tmpFile.renameTo(targetFile)) {
            tmpFile.copyTo(targetFile, overwrite = true)
            tmpFile.delete()
        }
        return finalBytes.size.toLong()
    }

    /** 校验是否为合法的图片数据头（JPEG / PNG / WebP / GIF）。 */
    private fun isValidImageBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return true
        // PNG: 89 50 4E 47
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return true
        // GIF: 47 49 46 38
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte()) return true
        // WebP: RIFF....WEBP
        if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()) return true
        return false
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
        val updated = _tasks.value.map {
            if (it.taskId == taskId) it.copy(downloadedPages = downloaded, totalPages = total, updateTime = System.currentTimeMillis())
            else it
        }
        _tasks.value = updated
        // 下载中任务同步系统通知（进度条 + 速度）
        updated.find { it.taskId == taskId }?.let { task ->
            if (task.status == DownloadStatus.DOWNLOADING) notifyTaskProgress(task)
        }
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
        list.find { it.taskId == taskId }?.let { notifyTaskFinished(it) }
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
        /** 系统通知渠道 ID（低打扰下载进度）。 */
        private const val NOTIFICATION_CHANNEL_ID = "venera_download"

        @Volatile
        private var INSTANCE: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
