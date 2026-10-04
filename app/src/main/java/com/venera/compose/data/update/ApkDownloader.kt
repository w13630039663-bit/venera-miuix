package com.venera.compose.data.update

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.download.DownloadNotificationChannel
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request

/** 一次应用内更新下载的进度。`Failed` 的话必须带**实际读数**，见 [UpdateDownload.Failed]。 */
sealed interface UpdateDownload {
    data object Idle : UpdateDownload

    /** [viaMirror] 为真表示此刻走的是加速镜像，界面上要念出来 —— 用户有权知道字节从谁家来的。 */
    data class Running(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
        val viaMirror: Boolean,
    ) : UpdateDownload

    /** 字节下完了，正在算散列。14 MB 量级一百来毫秒，但这一段不该被跳过去写成"完成"。 */
    data object Verifying : UpdateDownload

    /**
     * 落盘完成。[verified] 为假表示发布通道这次没给 `digest`，那份文件**没有被校验过** ——
     * UI 必须把这句话说出来，不许用同一句"下载完成"盖掉两种情况。
     */
    data class Ready(val file: File, val verified: Boolean, val size: Long) : UpdateDownload

    /**
     * [reason] 要么带"哪个候选 + 什么码/什么异常"，要么带"散列期望前缀 vs 实得前缀"。
     * 一句"下载失败"没法让人判断该重试、该换网、还是该去浏览器 —— 所以这里不收那种写法。
     */
    data class Failed(val reason: String) : UpdateDownload
}

/**
 * 把发布通道上的 APK 下到本地并**证明它是我们发出去的那一份**。
 *
 * ## 形状
 *
 * 只有这一颗碰网络与磁盘：候选顺序（[downloadCandidates]）与选哪一份（[pickApkAsset]）都是纯函数，
 * 装不装得上是 [ApkInstallLauncher]。这条链上每一环的失败读数不同，分开写才报得出"断在哪一环"。
 *
 * ## 校验为什么必须在流里算
 *
 * 边写边喂 [MessageDigest] 与"下完再读一遍算"结果相同，但后者要把 14 MB 再走一遍磁盘，
 * 而且给"先拿未校验的文件去干什么"留了一个窗口。这里没有那个窗口：
 * **散列不等就删掉，且永远不改名为正式名**。
 *
 * ## 半截文件为什么一律不留
 *
 * `.part` 只存在于下载过程中；任何异常、校验不符、用户取消，都先删 `.part` 再交回状态。
 * 留着的形状是"下一次接着写进去"，而断点续传本批刻意不做（方案文档 §1.3）——
 * 那半截字节既没人读也没人清，只会在用户第二次点更新时把整条链拖慢。
 */
class ApkDownloader private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = VeneraNetworkClient.getInstance(appContext).okHttpClient

    private val _state = MutableStateFlow<UpdateDownload>(UpdateDownload.Idle)
    val state: StateFlow<UpdateDownload> = _state.asStateFlow()

    private var job: Job? = null

    /** 上一次通知落地的时刻，用来把 notify 节流到一秒一次（进度 state 不受它影响）。 */
    private var lastNotifyAt = 0L

    fun start(asset: ReleaseAsset) {
        cancel()
        clearDownloadedFiles()
        _state.value = UpdateDownload.Running(0L, asset.size, 0L, viaMirror = false)
        DownloadNotificationChannel.ensure(appContext)
        job = scope.launch { run(asset) }
    }

    /** 用户点取消：协程停下来，`.part` 当场删掉，通知撤掉。状态回 Idle 而不是 Failed。 */
    fun cancel() {
        job?.cancel()
        job = null
        deletePartFiles()
        notifier(appContext).cancel(NOTIFICATION_ID)
        if (_state.value !is UpdateDownload.Idle) _state.value = UpdateDownload.Idle
    }

    /** 装完或用户不装了：把下载目录清掉。进行中的那一笔不碰。 */
    fun clearDownloadedFiles() {
        if (job?.isActive == true) return
        ApkInstallLauncher.apkDir(appContext).listFiles()?.forEach { file ->
            if (file.isFile) runCatching { file.delete() }
        }
    }

    private suspend fun run(asset: ReleaseAsset) {
        val candidates = downloadCandidates(asset.url, asset.sha256)
        val reasons = mutableListOf<String>()
        for ((index, url) in candidates.withIndex()) {
            val viaMirror = index > 0
            val target = File(ApkInstallLauncher.apkDir(appContext), asset.name)
            val part = File(target.path + PART_SUFFIX)
            deleteIfExists(part)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                val written = fetchToFile(url, part, digest, asset.size, viaMirror)
                val actual = digest.hexLower()
                _state.value = UpdateDownload.Verifying
                if (asset.sha256 != null && actual != asset.sha256) {
                    // 校验不过：删掉、记下两边前缀、换下一个候选。绝不改名为正式名。
                    deleteIfExists(part)
                    reasons += "${hostOf(url)} 校验不符（期望 ${asset.sha256.take(12)}… 实得 ${actual.take(12)}…）"
                    continue
                }
                if (!part.renameTo(target)) {
                    deleteIfExists(part)
                    reasons += "落盘改名失败：${target.absolutePath}"
                    continue
                }
                val verified = asset.sha256 != null
                _state.value = UpdateDownload.Ready(target, verified, written)
                notifyReady(target, asset)
                if (!verified) Log.w(TAG, "发布通道未给 digest，本次下载未经校验：${asset.name}")
                return
            } catch (e: kotlinx.coroutines.CancellationException) {
                deleteIfExists(part)
                throw e
            } catch (e: Exception) {
                deleteIfExists(part)
                reasons += "${hostOf(url)} ${e.javaClass.simpleName}: ${e.message ?: "无详情"}"
                Log.w(TAG, "更新包下载候选失败 ${hostOf(url)}", e)
            }
        }
        val failure = if (reasons.isEmpty()) "没有可尝试的下载地址" else reasons.joinToString("；")
        _state.value = UpdateDownload.Failed(failure)
        notifyFailed(failure)
    }

    /**
     * 拉一条候选并流式落盘。
     *
     * 总长优先吃响应头的 `Content-Length`，拿不到才退到发布通道给的 `asset.size`
     * （镜像改写过响应头是常态，两个来源里**响应头更新**）。两个都是 0 时进度条走不出来，
     * 那一档 UI 退成不确定态的转环而不是假装 0%。
     */
    private fun fetchToFile(
        url: String,
        part: File,
        digest: MessageDigest,
        fallbackTotal: Long,
        viaMirror: Boolean,
    ): Long {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("响应没有正文")
            val total = resolveTotal(response.headers["Content-Length"], fallbackTotal)
            part.parentFile?.mkdirs()
            body.byteStream().use { input ->
                part.outputStream().buffered(8 * 1024).use { output ->
                    return pump(input, output, digest, total, viaMirror)
                }
            }
        }
    }

    private fun pump(
        input: InputStream,
        output: java.io.OutputStream,
        digest: MessageDigest,
        total: Long,
        viaMirror: Boolean,
    ): Long {
        var lastSampleAt = System.currentTimeMillis()
        var lastSampleBytes = 0L
        return pumpWithDigest(input, output, digest) { done ->
            val now = System.currentTimeMillis()
            if (now - lastSampleAt >= STATE_SAMPLE_INTERVAL_MS) {
                val elapsedMs = (now - lastSampleAt).coerceAtLeast(1L)
                val speed = (done - lastSampleBytes) * 1000L / elapsedMs
                lastSampleAt = now
                lastSampleBytes = done
                _state.value = UpdateDownload.Running(done, total, speed, viaMirror)
                maybeNotifyProgress(done, total, viaMirror)
            }
        }
    }

    private fun maybeNotifyProgress(done: Long, total: Long, viaMirror: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < NOTIFY_INTERVAL_MS) return
        lastNotifyAt = now
        postProgress(done, total, viaMirror)
    }

    private fun postProgress(done: Long, total: Long, viaMirror: Boolean) {
        if (!DownloadNotificationChannel.canPost(appContext)) return
        val builder = NotificationCompat.Builder(appContext, DownloadNotificationChannel.ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载更新")
            .setContentText(if (viaMirror) "官方直链不通，正走加速镜像" else "来自发布通道")
            .setOngoing(true)
            .setSilent(true)
        val percent = progressPercent(done, total)
        if (percent >= 0) {
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        runCatching { notifier(appContext).notify(NOTIFICATION_ID, builder.build()) }
            .onFailure { Log.w(TAG, "更新进度通知失败", it) }
    }

    private fun notifyReady(file: File, asset: ReleaseAsset) {
        if (!DownloadNotificationChannel.canPost(appContext)) return
        val text = if (asset.sha256 != null) "轻触以安装（已通过校验）" else "轻触以安装（未校验：这次没拿到官方哈希）"
        val notification = NotificationCompat.Builder(appContext, DownloadNotificationChannel.ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("更新下载完成 ${asset.name}")
            .setContentText(text)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(ApkInstallLauncher.pendingInstallIntent(appContext, file))
            .build()
        runCatching { notifier(appContext).notify(NOTIFICATION_ID, notification) }
            .onFailure { Log.w(TAG, "更新完成通知失败", it) }
    }

    private fun notifyFailed(reason: String) {
        if (!DownloadNotificationChannel.canPost(appContext)) return
        val notification = NotificationCompat.Builder(appContext, DownloadNotificationChannel.ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("更新下载失败")
            .setContentText(reason.take(MAX_NOTIFY_TEXT))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        runCatching { notifier(appContext).notify(NOTIFICATION_ID, notification) }
            .onFailure { Log.w(TAG, "更新失败通知失败", it) }
    }

    private fun deletePartFiles() {
        ApkInstallLauncher.apkDir(appContext).listFiles()?.forEach { file ->
            if (file.name.endsWith(PART_SUFFIX)) deleteIfExists(file)
        }
    }

    private fun deleteIfExists(file: File) {
        runCatching { if (file.exists()) file.delete() }.onFailure { Log.w(TAG, "临时文件没删掉：${file.path}") }
    }

    companion object {
        private const val TAG = "AppUpdate"
        private const val PART_SUFFIX = ".part"
        private const val NOTIFICATION_ID = 0x5A02
        private const val MAX_NOTIFY_TEXT = 120

        /** 界面刷新的采样间隔：再快就是每帧一次 string 换算，纯浪费。 */
        private const val STATE_SAMPLE_INTERVAL_MS = 200L

        /** 通知落地的最小间隔，系统那边 notify 打太密会被合并甚至限流。 */
        private const val NOTIFY_INTERVAL_MS = 1_000L

        @Volatile
        private var INSTANCE: ApkDownloader? = null

        fun getInstance(context: Context): ApkDownloader =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApkDownloader(context.applicationContext).also { INSTANCE = it }
            }
    }
}

private fun notifier(context: Context): NotificationManagerCompat = NotificationManagerCompat.from(context)

/**
 * 边读边写边喂散列，返回总字节数。[onBytes] 在**每个完整块之后**回调一次（累计值，单调不减）。
 *
 * 抽出来只为一件事：`digest.update(buffer, 0, read)` 里那个 `read` 是"这次实际读到多少"，
 * 写成 `digest.update(buffer)` 就在一半的块上把**上一块的尾巴重复算进去**，
 * 而散列错只会表现为"校验失败"，看起来像镜像在使坏、不像自己代码写错。
 * 这种形状必须有用例钉住，不能靠读代码确认（用例见 `ApkDownloadStreamTest`）。
 */
internal fun pumpWithDigest(
    input: InputStream,
    output: java.io.OutputStream,
    digest: MessageDigest,
    onBytes: (Long) -> Unit,
): Long {
    val buffer = ByteArray(DOWNLOAD_CHUNK_BYTES)
    var done = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
        output.write(buffer, 0, read)
        done += read
        onBytes(done)
    }
    output.flush()
    return done
}

/**
 * 进度百分比（0..100）。总长未知时交回 -1，调用侧据此走**不确定态**那条分支 ——
 * 拿 0 当"未知"会得到一枚停在 0% 的假进度条。
 */
internal fun progressPercent(done: Long, total: Long): Int {
    if (total <= 0L) return -1
    return ((done * 100) / total).coerceIn(0L, 100L).toInt()
}

/**
 * 这一笔下载该按多长算进度：优先响应头的 `Content-Length`，缺失/非正数才退到发布通道给的 `asset.size`。
 *
 * 顺序是刻意的：镜像改写过响应头是常态（本包实测三种前缀都能给 200，头部形状各家自己定），
 * 而"实际这一条流有多长"只有响应头知道。两边都拿不到就是 0 → 不确定态。
 */
internal fun resolveTotal(headerContentLength: String?, fallbackBytes: Long): Long {
    val fromHeader = headerContentLength?.toLongOrNull()
    return if (fromHeader != null && fromHeader > 0L) fromHeader else fallbackBytes.coerceAtLeast(0L)
}

internal fun MessageDigest.hexLower(): String =
    digest().joinToString("") { "%02x".format(it) }

/**
 * 报读数时念域名而不是念整条 URL：URL 里带着 release 名与版本号，念完一行都放不下。
 *
 * 镜像那条的形状是 `https://gh-proxy.com/https://github.com/…`，
 * 先 `substringAfter("://")` 再 `substringBefore('/')` 得到的正是 `gh-proxy.com` ——
 * 也就是"该把失败算在谁头上"那个答案。
 */
internal fun hostOf(url: String): String =
    url.substringAfter("://").substringBefore('/')

/** 单次读块的大小。32 KB 是 `okio`/`BufferedInputStream` 常用的那一档，不另造数字。 */
private const val DOWNLOAD_CHUNK_BYTES = 32 * 1024
