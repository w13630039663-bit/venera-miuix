package com.venera.compose.data.update

import android.content.Context
import android.util.Log
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.data.api.ComicPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 本包的发布通道（检查更新与"去下载"都指向它）。
 *
 * 坐标取自本仓库 `master` 分支 `lib/pages/settings/about.dart` 的 `kProjectSlug`，
 * 两边同一仓库。**上游 venera-app/venera 的版本号与本包不通用** ——
 * 拿上游判断更新会把用户导去下载一个装不上、也不是这个界面的安装包。
 */
object ProjectChannel {
    const val SLUG = "w13630039663-bit/venera-miuix"
    const val REPO_URL = "https://github.com/$SLUG"
    const val RELEASES_URL = "$REPO_URL/releases"
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$SLUG/releases/latest"
}

/** 检查结果。`Failed` 与 `UpToDate` 必须分开：一次断网不能播报成"已是最新版本"。 */
sealed interface UpdateCheck {
    /**
     * [release] 带着更新日志与候选安装包 —— 应用内下载要的就是这两样，
     * 而它们与 [remoteVersion] 来自**同一次**请求（见 [AppUpdateChecker.check]）。
     */
    data class Available(val remoteVersion: String, val release: LatestRelease) : UpdateCheck
    data object UpToDate : UpdateCheck
    data object Failed : UpdateCheck
}

object AppUpdateChecker {

    private const val TAG = "AppUpdate"

    /** 启动检查的最小间隔，照抄 master 的 `24 * 60 * 60 * 1000`。 */
    const val STARTUP_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    /** 本包版本号只从安装包取这一份：设置页、漫画源脚本、更新比较都用它。 */
    fun localVersion(context: Context): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    suspend fun check(context: Context): UpdateCheck {
        val local = localVersion(context) ?: return UpdateCheck.Failed
        val release = fetchLatestRelease(context) ?: return UpdateCheck.Failed
        return if (isNewer(release.version, local)) {
            UpdateCheck.Available(release.version, release)
        } else {
            UpdateCheck.UpToDate
        }
    }

    /** 开关开着、且距上次占坑超过 24 小时，才在启动时打这一次请求。 */
    fun shouldCheckOnStartup(prefs: ComicPreferences): Boolean =
        prefs.checkUpdateOnStart.value &&
            System.currentTimeMillis() - prefs.lastUpdateCheckAt >= STARTUP_CHECK_INTERVAL_MS

    /**
     * 「以后再说」的判据：压住的**只有被跳过的那一版**，出了更高的一版照旧提醒。
     *
     * 写成相等判断而不是"有值就不弹"，是因为后者等于让一次误点永久关掉更新通道 ——
     * 用户下次知道自己落后，只能自己想起来去设置页翻。
     */
    fun shouldAnnounce(remote: String, ignored: String?): Boolean =
        ignored.isNullOrBlank() || isNewer(remote, ignored)

    private suspend fun fetchLatestRelease(context: Context): LatestRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(ProjectChannel.LATEST_RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .build()
        try {
            VeneraNetworkClient.getInstance(context.applicationContext)
                .okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "发布通道返回 HTTP ${response.code}")
                        return@use null
                    }
                    val body = response.body?.string().orEmpty()
                    val release = parseLatestRelease(body)
                    if (release == null) {
                        // 只报长度不报正文：body 里带着日志全文，日志文件不该收下一整篇发布说明。
                        Log.w(TAG, "发布通道响应解不出可用版本（${body.length} 字符，HTTP 200）")
                    }
                    release
                }
        } catch (e: Exception) {
            Log.w(TAG, "拉取发布通道失败", e)
            null
        }
    }

    /**
     * `v1.6.6-miuix` → `1.6.6`。展示用；比较不依赖它。
     *
     * 2026-10-20：实现已下放到 `ReleaseFeed.kt` 的顶层 `displayVersionOf(…)` —— 那一侧是
     * 纯数据文件、要进两端共享的编译面，不能反向依赖本 object（本 object 绑 Context /
     * 通知渠道）。这里保留一行委托，**行为逐字相同**，调用点一个都不用改。
     */
    fun displayVersion(tag: String): String = displayVersionOf(tag)

    fun isNewer(remote: String, local: String): Boolean {
        val a = versionSegments(remote)
        val b = versionSegments(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * 口径同 master 的 `_compareVersion`：tag 是本仓库作者手打的，常带 `v` 前缀、
     * `-miuix` 预发布后缀，段数也可能不齐（`1.7` 与 `1.7.0`）。
     * 一次格式随意不该把「检查更新」整个打断。
     */
    private fun versionSegments(version: String): List<Int> = version
        .removePrefix("v").removePrefix("V")
        .substringBefore('-').substringBefore('+')
        .split('.')
        .map { LEADING_DIGITS.find(it)?.value?.toIntOrNull() ?: 0 }

    private val LEADING_DIGITS = Regex("^\\d+")
}
