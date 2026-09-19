package com.venera.compose.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.data.prefs.VeneraPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Follows app.dart: data, user, troubleshooting, then existing offline entry points. */
@Composable
fun AppSettings(
    prefs: VeneraPreferences,
    onBack: () -> Unit,
    onSync: () -> Unit,
    onLogs: () -> Unit,
    onDownloads: () -> Unit,
    onLocalComics: () -> Unit
) {
    val context = LocalContext.current
    // LocalComicManager and DownloadManager both use this fixed internal directory.
    val storagePath = remember(context) { File(context.filesDir, "downloads").absolutePath }
    val cacheMaxMb by prefs.httpCacheMaxMb.collectAsState()
    var cacheBytes by remember { mutableLongStateOf(-1L) }
    val network = remember(context) { VeneraNetworkClient.getInstance(context) }
    val scope = rememberCoroutineScope()

    // Cache.size() 读磁盘，不能在组合期直接调。
    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) { network.httpCacheSizeBytes() }
    }

    SettingsPage(title = "应用", onBack = onBack, largeTitle = "应用与数据") {
        SettingsGroup(title = "数据") {
            SettingsAction(
                title = "本地漫画存储路径",
                summary = "$storagePath（点击复制）",
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("本地漫画存储路径", storagePath))
                    Toast.makeText(context, "路径已复制", Toast.LENGTH_SHORT).show()
                }
            )
            SettingsSlider(
                "网络缓存上限", cacheMaxMb.toFloat(), 16f..1024f,
                {
                    prefs.setHttpCacheMaxMb(it.toInt())
                    // 上限是建 client 时写进 OkHttp Cache 的，改完必须重建才生效。
                    network.rebuildClient()
                },
                steps = 62, suffix = " MB",
                summary = "仅 HTTP 响应缓存（漫画源接口与网页），不含图片磁盘缓存与下载目录。",
            )
            SettingsAction(
                "网络缓存占用与清理",
                if (cacheBytes < 0) "正在统计…" else "当前 ${formatMib(cacheBytes)} · 点击清空（不影响下载与收藏）",
                onClick = {
                    if (cacheBytes >= 0) {
                        cacheBytes = -1L
                        Toast.makeText(context, "正在清空网络缓存", Toast.LENGTH_SHORT).show()
                    }
                    // evictAll() 会删磁盘条目，放 IO 线程跑完再回主线程刷新显示。
                    scope.launch {
                        withContext(Dispatchers.IO) { network.clearHttpCache() }
                        cacheBytes = withContext(Dispatchers.IO) { network.httpCacheSizeBytes() }
                        Toast.makeText(context, "网络缓存已清空", Toast.LENGTH_SHORT).show()
                    }
                },
            )
            // 审计发现原行名"导出应用数据"是过度承诺：BackupManager 只打包
            // history / favorite / stats / guard_rules，不含偏好、cookie 与已装源。
            SettingsAction("导出阅读与收藏数据", "前往同步与备份页面导出本地备份（阅读历史、收藏、统计、屏蔽规则；不含偏好设置、Cookie 与已装漫画源）", onClick = onSync)
            SettingsAction("导入阅读与收藏数据", "前往同步与备份页面恢复本地备份（覆盖上述四类数据，不影响偏好与源）", onClick = onSync)
            SettingsAction("数据同步", "配置 WebDAV 同步与备份", onClick = onSync)
        }
        SettingsGroup(title = "故障排查") {
            SettingsAction("打开日志", "查看运行日志与诊断信息", onClick = onLogs)
        }
        SettingsGroup(title = "离线与本地漫画") {
            SettingsAction("下载管理", "管理下载队列与离线章节", onClick = onDownloads)
            SettingsAction("本地漫画", "浏览、导入和管理本地漫画", onClick = onLocalComics)
        }
        SettingsFutureGroup {
            UnsupportedSetting("需要身份验证", "尚未接入应用解锁与生物识别验证流程；" +
                "需要先定「生物识别不可用时的兜底解锁」，不做半套。")
        }
    }
}

private fun formatMib(bytes: Long): String =
    if (bytes < 1024) "$bytes B"
    else if (bytes < 1024 * 1024) "%.1f KiB".format(bytes / 1024.0)
    else "%.1f MiB".format(bytes / (1024.0 * 1024.0))
