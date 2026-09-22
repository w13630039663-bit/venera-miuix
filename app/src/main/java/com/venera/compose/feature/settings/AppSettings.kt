package com.venera.compose.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.download.ComicStorageRoot
import com.venera.compose.download.DownloadManager
import com.venera.compose.sync.BackupTransfers
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
    val cacheMaxMb by prefs.httpCacheMaxMb.collectAsState()
    var cacheBytes by remember { mutableLongStateOf(-1L) }
    val network = remember(context) { VeneraNetworkClient.getInstance(context) }
    val scope = rememberCoroutineScope()
    val defaultRoot = remember(context) { ComicStorageRoot.defaultDir(context).absolutePath }

    // Cache.size() 读磁盘，不能在组合期直接调。
    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) { network.httpCacheSizeBytes() }
    }

    // ── 本地漫画存储目录自选 ────────────────────────────────────────
    val configuredPath by prefs.comicStoragePath.collectAsState()
    var resumeTick by remember { mutableIntStateOf(0) }
    // resolve() 只是读内存里的偏好 + 拼路径，可以在组合期取；探针要写磁盘，只能进 state。
    var storagePath by remember { mutableStateOf(ComicStorageRoot.resolve(context).absolutePath) }
    var storageWritable by remember { mutableStateOf(true) }
    var pendingTarget by remember { mutableStateOf<String?>(null) }
    var pendingCounts by remember { mutableStateOf(0 to 0) }
    // 探针没过的目录：等用户去系统页授权，回来自动接着走，不必重挑一遍目录
    var pendingGrantPath by remember { mutableStateOf<String?>(null) }
    var showGrantPrompt by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun askMigration(target: String, counts: Pair<Int, Int>) {
        pendingCounts = counts
        pendingTarget = target
    }

    LaunchedEffect(configuredPath, resumeTick) {
        val probed = withContext(Dispatchers.IO) {
            val dir = ComicStorageRoot.resolve(context)
            dir.absolutePath to ComicStorageRoot.probeWritable(dir)
        }
        storagePath = probed.first
        storageWritable = probed.second
    }

    // 从系统「所有文件访问」页返回时重测
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeTick) {
        val path = pendingGrantPath ?: return@LaunchedEffect
        pendingGrantPath = null
        val ok = withContext(Dispatchers.IO) { ComicStorageRoot.probeWritable(File(path)) }
        if (!ok) {
            Toast.makeText(context, "$path 仍写入不了，权限可能还没生效", Toast.LENGTH_LONG).show()
        } else {
            commitOrAskTarget(context, prefs, path) { t, c -> askMigration(t, c) }
        }
    }

    val pickDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val outcome = withContext(Dispatchers.IO) { evaluatePickedDir(context, uri) }
            when (outcome) {
                is DirChoice.Reject ->
                    Toast.makeText(context, outcome.reason, Toast.LENGTH_LONG).show()

                is DirChoice.NeedsPermission -> {
                    showGrantPrompt = outcome.path
                }

                is DirChoice.Accept ->
                    commitOrAskTarget(context, prefs, outcome.path) { t, c -> askMigration(t, c) }
            }
            busy = false
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        // octet-stream 而非 zip：保存面板不会替 .venera 强行改扩展名
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val res = withContext(Dispatchers.IO) { BackupTransfers.exportBackupTo(context, uri) }
            busy = false
            res.onSuccess { Toast.makeText(context, "已导出到 $it", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val res = withContext(Dispatchers.IO) { BackupTransfers.importBackupFrom(context, uri) }
            busy = false
            res.onSuccess {
                Toast.makeText(
                    context,
                    "已恢复：历史 ${it.historyCount} 条，收藏 ${it.favoriteCount} 部，" +
                        "统计 ${it.statsCount} 条，规则 ${it.guardRulesCount} 条",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    SettingsPage(title = "应用", onBack = onBack, largeTitle = "应用与数据") {
        SettingsGroup(title = "数据") {
            SettingsAction(
                title = "本地漫画存储路径",
                summary = when {
                    !storageWritable -> "$storagePath（当前不可写：需要「所有文件访问」权限）"
                    configuredPath.isBlank() -> "$storagePath（应用私有目录，随应用卸载一起清除）"
                    else -> "$storagePath（自定义目录）"
                },
                enabled = !busy,
                onClick = { pickDirLauncher.launch(null) }
            )
            if (!storageWritable) {
                SettingsAction(
                    "开启「所有文件访问」权限",
                    "授予后回到本页会自动重测；未授予时下载会直接失败，不会偷偷写到别处",
                    onClick = { showGrantPrompt = storagePath }
                )
            }
            if (configuredPath.isNotBlank()) {
                SettingsAction(
                    "改回应用私有目录",
                    "恢复默认位置：$defaultRoot",
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            commitOrAskTarget(context, prefs, defaultRoot) { t, c -> askMigration(t, c) }
                            busy = false
                        }
                    }
                )
            }
            // 自选目录后用户仍可能要把路径贴进文件管理器，原来的复制能力保留
            SettingsAction("复制当前存储路径", storagePath, onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("本地漫画存储路径", storagePath))
                Toast.makeText(context, "路径已复制", Toast.LENGTH_SHORT).show()
            })
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
            // 备份包只含四张表（history/favorite/stats/guard_rules），文案不许超过这个覆盖面。
            SettingsAction(
                "导出数据",
                "自选保存位置与文件名，导出阅读历史、收藏、阅读统计、屏蔽规则；" +
                    "不含偏好设置、Cookie 与已装漫画源",
                enabled = !busy,
                onClick = { exportLauncher.launch(BackupTransfers.suggestedFileName()) }
            )
            SettingsAction(
                "导入数据",
                "自选 .venera 备份文件，覆盖恢复上述四类数据（不影响偏好与漫画源）",
                enabled = !busy,
                onClick = { importLauncher.launch(arrayOf("*/*")) }
            )
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

    // 探针失败 → 讲清代价再给唯一出口（系统设置页），不做静默回落
    showGrantPrompt?.let { path ->
        AlertDialog(
            onDismissRequest = { showGrantPrompt = null },
            title = { Text("需要「所有文件访问」权限") },
            text = {
                Text(
                    "Android 11 起的分区存储不允许应用直接用文件接口写公共目录，" +
                        "实测往「$path」写入失败。\n\n" +
                        "开启后本应用才能把漫画库放进你自己选的目录（含外置卡）。" +
                        "该开关只影响文件读写，可在系统设置里随时撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingGrantPath = path
                    showGrantPrompt = null
                    openAllFilesSettings(context)
                }) { Text("打开系统设置") }
            },
            dismissButton = {
                TextButton(onClick = { showGrantPrompt = null }) { Text("取消") }
            },
        )
    }

    // 旧根非空才问；两个选项的差别在文案里写实，不美化
    pendingTarget?.let { target ->
        val (comics, chapters) = pendingCounts
        AlertDialog(
            onDismissRequest = { pendingTarget = null },
            title = { Text("切换存储目录") },
            text = {
                Text(
                    "新目录：$target\n\n" +
                        "当前目录里有 $comics 部漫画 / $chapters 章。\n" +
                        "· 移动到所选目录：把这些内容搬过去，旧目录清空。\n" +
                        "· 仅对新下载生效：旧目录内容不再出现在本地书架，已下载判定也会转" +
                        "「未下载」，再点会重新拉取。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingTarget = null
                    busy = true
                    scope.launch {
                        applyStoragePath(context, prefs, target, migrate = true)
                        busy = false
                    }
                }) { Text("移动到所选目录") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingTarget = null
                    busy = true
                    scope.launch {
                        applyStoragePath(context, prefs, target, migrate = false)
                        busy = false
                    }
                }) { Text("仅对新下载生效") }
            },
        )
    }
}

private sealed interface DirChoice {
    data class Accept(val path: String) : DirChoice
    data class NeedsPermission(val path: String) : DirChoice
    data class Reject(val reason: String) : DirChoice
}

/** 三道关：换算真实路径 → 目录准入守卫 → 实写探针。任一不过就把理由交回用户。 */
private fun evaluatePickedDir(context: Context, uri: Uri): DirChoice {
    val path = ComicStorageRoot.resolveTreePath(context, uri)
        ?: return DirChoice.Reject(
            "系统选择器给的是虚拟目录，换算不出真实路径。请在选择器里进入「内部存储」或 SD 卡，" +
                "选一个普通文件夹（例如 Download/漫画）"
        )
    ComicStorageRoot.rejectReason(path)?.let { return DirChoice.Reject(it) }
    if (!ComicStorageRoot.probeWritable(File(path))) return DirChoice.NeedsPermission(path)
    return DirChoice.Accept(path)
}

/**
 * 已是当前目录 → 直接播报；旧根为空 → 无风险直接切；非空 → 交给 UI 弹迁移确认。
 *
 * `counts` 要递归数目录，必须挂 IO 线程，点击链路一律走协程。
 */
private suspend fun commitOrAskTarget(
    context: Context,
    prefs: VeneraPreferences,
    target: String,
    onAskMigration: (String, Pair<Int, Int>) -> Unit,
) {
    val oldDir = ComicStorageRoot.resolve(context)
    if (File(target).absolutePath == oldDir.absolutePath) {
        Toast.makeText(context, "所选目录已是当前存储目录", Toast.LENGTH_SHORT).show()
        return
    }
    val counts = withContext(Dispatchers.IO) { ComicStorageRoot.counts(oldDir) }
    if (counts.first == 0 && counts.second == 0) {
        applyStoragePath(context, prefs, target, migrate = false)
    } else {
        onAskMigration(target, counts)
    }
}

/**
 * 真正落刀。顺序是刻意的：迁移在前、改偏好在后，
 * 这样迁移抛错时根目录压根没换过，不会留下「一半在新、一半在旧」的状态。
 */
private suspend fun applyStoragePath(
    context: Context,
    prefs: VeneraPreferences,
    target: String,
    migrate: Boolean,
) {
    val oldDir = ComicStorageRoot.resolve(context)
    val newDir = File(target)
    val isDefault = newDir.absolutePath == ComicStorageRoot.defaultDir(context).absolutePath
    val result = withContext(Dispatchers.IO) {
        runCatching {
            if (!ComicStorageRoot.ensureRoot(newDir)) error("目标目录不可用：$target")
            if (migrate) ComicStorageRoot.migrate(oldDir, newDir).getOrThrow() else 0
        }.map { movedDirs ->
            prefs.setComicStoragePath(if (isDefault) "" else newDir.absolutePath)
            // 任务清单里记的是绝对路径，搬家后必须换前缀，否则已完成章节会被判成未下载
            if (migrate) DownloadManager.getInstance(context).relocateTasks(oldDir, newDir)
            movedDirs
        }
    }
    result.onSuccess { dirs ->
        val tail = if (migrate) "，已迁移 $dirs 个源目录" else "，已有内容留在原目录"
        Toast.makeText(context, "存储目录已切换$tail", Toast.LENGTH_LONG).show()
    }.onFailure {
        Toast.makeText(context, "切换失败：${it.message}（存储目录未改动）", Toast.LENGTH_LONG).show()
    }
}

/** 定制 ROM 上带 `package:` 的那个 intent 不一定接得住，接不住就退到总列表页。 */
private fun openAllFilesSettings(context: Context) {
    val byPackage = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )
    val listOnly = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    val opened = listOf(byPackage, listOnly).any { intent ->
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }
    if (!opened) {
        Toast.makeText(
            context,
            "打不开系统设置页，请在「设置 → 应用 → 特殊应用权限 → 所有文件访问」里手动开启本应用",
            Toast.LENGTH_LONG,
        ).show()
    }
}

private fun formatMib(bytes: Long): String =
    if (bytes < 1024) "$bytes B"
    else if (bytes < 1024 * 1024) "%.1f KiB".format(bytes / 1024.0)
    else "%.1f MiB".format(bytes / (1024.0 * 1024.0))
