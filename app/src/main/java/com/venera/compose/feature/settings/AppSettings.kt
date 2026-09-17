package com.venera.compose.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.venera.compose.data.prefs.VeneraPreferences
import java.io.File

/** Follows app.dart: data, user, troubleshooting, then existing offline entry points. */
@Suppress("UNUSED_PARAMETER")
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

    SettingsPage(title = "应用", onBack = onBack) {
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
            UnsupportedSetting("设置新的存储路径", "当前本地漫画管理器使用固定内部目录，尚未实现目录迁移。")
            UnsupportedSetting("缓存大小与清理", "尚无统一缓存统计与清理管理器，不能将网络缓存等同于全部缓存。")
            UnsupportedSetting("缓存上限", "网络缓存目前固定为 100 MB，尚无可持久化并生效的上限设置。")
        }
        SettingsGroup {
            SettingsAction("导出应用数据", "前往同步与备份页面导出本地备份", onClick = onSync)
            SettingsAction("导入应用数据", "前往同步与备份页面恢复本地备份", onClick = onSync)
            SettingsAction("数据同步", "配置 WebDAV 同步与备份", onClick = onSync)
        }
        SettingsGroup(title = "用户") {
            UnsupportedSetting("语言", "尚未实现应用语言持久化与界面语言切换。")
            UnsupportedSetting("需要身份验证", "尚未接入应用解锁与生物识别验证流程。")
        }
        SettingsGroup(title = "故障排查") {
            SettingsAction("打开日志", "查看运行日志与诊断信息", onClick = onLogs)
        }
        SettingsGroup(title = "离线与本地漫画") {
            SettingsAction("下载管理", "管理下载队列与离线章节", onClick = onDownloads)
            SettingsAction("本地漫画", "浏览、导入和管理本地漫画", onClick = onLocalComics)
        }
    }
}
