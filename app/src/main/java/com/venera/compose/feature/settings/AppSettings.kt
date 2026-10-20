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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.venera.compose.components.venera.VeneraDialog
import com.venera.compose.download.ComicStorageRoot
import com.venera.compose.sync.BackupTransfers
import com.venera.compose.sync.SourceAccountMode
import com.venera.compose.data.api.NetworkPreferences
import com.venera.compose.data.api.BusinessPorts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Follows app.dart: data, user, troubleshooting, then existing offline entry points. */
@Composable
fun AppSettings(
    prefs: NetworkPreferences,
    onBack: () -> Unit,
    onSync: () -> Unit,
    onLogs: () -> Unit,
    onDownloads: () -> Unit,
    onLocalComics: () -> Unit,
    onFollowUpdates: () -> Unit
) {
    val context = LocalContext.current
    val cacheMaxMb by prefs.httpCacheMaxMb.collectAsState()
    var cacheBytes by remember { mutableLongStateOf(-1L) }
    val network = remember(context) { BusinessPorts.of(context).network }
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

    // 导出时「漫画源账号」打包到哪一档。默认不导出 —— 包里含账密与 token，
    // 而归档是一份用户会随手分享出去的文件，不能让一次顺手点"导出"把它写进去。
    var sourceAccountMode by remember { mutableStateOf(SourceAccountMode.NONE) }

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
            Toast.makeText(context, "$path 还是写不进去，权限可能没生效", Toast.LENGTH_LONG).show()
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
            val res = withContext(Dispatchers.IO) {
                BackupTransfers.exportBackupTo(context, uri, sourceAccountMode)
            }
            busy = false
            res.onSuccess { Toast.makeText(context, "已导出到 $it", Toast.LENGTH_LONG).show() }
                .onFailure { e ->
                    android.util.Log.w("VeneraDebug", "export backup failed", e)
                    Toast.makeText(context, "导出没成功 🥲 换个位置再试一次", Toast.LENGTH_LONG).show()
                }
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
                Toast.makeText(context, BackupTransfers.describeResult(it), Toast.LENGTH_LONG).show()
            }.onFailure { e ->
                android.util.Log.w("VeneraDebug", "import backup failed", e)
                Toast.makeText(context, "这个备份没能导入 🥲 确认选的是 Venera 导出的文件", Toast.LENGTH_LONG).show()
            }
        }
    }

    SettingsPage(title = "应用", onBack = onBack, largeTitle = "应用与数据", heroSubtitle = "存储 · 备份 · 下载") {
        SettingsGroup(title = "数据") {
            SettingsAction(
                title = "本地漫画存储路径",
                summary = when {
                    !storageWritable -> "$storagePath（当前写不进去，缺所有文件访问权限）"
                    configuredPath.isBlank() -> "$storagePath（应用私有目录，卸载应用时会一起删掉）"
                    else -> "$storagePath（自己选的目录）"
                },
                enabled = !busy,
                onClick = { pickDirLauncher.launch(null) }
            )
            if (!storageWritable) {
                SettingsAction(
                    "开启「所有文件访问」权限",
                    "授权后返回本页会自动重新检测。没有权限时无法下载。",
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
                    network.rebuildHttpClient()
                },
                steps = 62, suffix = " MB",
                summary = "只统计网页和接口缓存，不含已下载的图片。",
            )
            SettingsAction(
                "网络缓存占用与清理",
                if (cacheBytes < 0) "正在统计…" else "当前 ${formatMib(cacheBytes)} · 点击清空（不影响已下载和收藏）",
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
            // 覆盖面以 BackupManager 类注释那张表为准（十个成员），文案不许超过它。
            SettingsSelect(
                title = "导出内容 · 漫画源账号",
                value = sourceAccountMode.name,
                options = listOf(
                    SourceAccountMode.NONE.name to "不导出（默认）",
                    SourceAccountMode.ACCOUNT_ONLY.name to "只导出登录状态",
                    SourceAccountMode.ALL.name to "登录状态 + 源内设置",
                ),
                onSelected = { name ->
                    sourceAccountMode = SourceAccountMode.entries.firstOrNull { it.name == name }
                        ?: SourceAccountMode.NONE
                },
                summary = "登录信息会明文写进备份。",
            )
            SettingsAction(
                "导出数据",
                "包含收藏、阅读历史、阅读统计、屏蔽规则和插图收藏地址" +
                    if (sourceAccountMode == SourceAccountMode.NONE) {
                        "。不含设置、Cookie 和图片文件"
                    } else {
                        "，以及上面所选的漫画源登录信息。不含设置、Cookie 和图片文件"
                    },
                enabled = !busy,
                onClick = { exportLauncher.launch(BackupTransfers.suggestedFileName()) }
            )
            SettingsAction(
                "导入数据",
                "支持 .venera 和 .picadata。官方 Venera 或 PicaComic 的备份只能导入收藏和阅读历史。",
                enabled = !busy,
                onClick = { importLauncher.launch(arrayOf("*/*")) }
            )
            SettingsAction("数据同步", "用 WebDAV 同步和备份", onClick = onSync)
        }
        SettingsGroup(title = "故障排查") {
            SettingsAction("打开日志", "查看运行日志", onClick = onLogs)
        }
        SettingsGroup(title = "离线与本地漫画") {
            SettingsAction("下载管理", "查看下载队列和离线章节", onClick = onDownloads)
            SettingsAction("本地漫画", "浏览和管理本地漫画", onClick = onLocalComics)
            SettingsAction("追更更新", "每天检查收藏夹里的新章节", onClick = onFollowUpdates)
        }
    }

    // 探针失败 → 讲清代价再给唯一出口（系统设置页），不做静默回落
    showGrantPrompt?.let { path ->
        VeneraDialog(
            show = true,
            onDismissRequest = { showGrantPrompt = null },
            title = "需要「所有文件访问」权限",
            content = {
                Text(
                    "无法写入「$path」。\n\n" +
                        "开启「所有文件访问」后，才能把漫画放在自己选的目录。随时可以在系统设置里关闭。"
                )
            },
            confirmText = "打开系统设置",
            onConfirm = {
                pendingGrantPath = path
                showGrantPrompt = null
                openAllFilesSettings(context)
            },
            dismissText = "取消",
            onDismiss = { showGrantPrompt = null },
        )
    }

    // 旧根非空才问；两个选项的差别在文案里写实，不美化
    pendingTarget?.let { target ->
        val (comics, chapters) = pendingCounts
        VeneraDialog(
            show = true,
            onDismissRequest = { pendingTarget = null },
            title = "切换存储目录",
            content = {
                Text(
                    "新目录：$target\n\n" +
                        "当前目录有 $comics 部漫画、$chapters 章。\n" +
                        "· 移动：搬到新目录，并清空旧目录。\n" +
                        "· 只对新下载生效：旧内容不再显示，已下载的会重新计算。"
                )
            },
            confirmText = "移动到所选目录",
            onConfirm = {
                pendingTarget = null
                busy = true
                scope.launch {
                    applyStoragePath(context, prefs, target, migrate = true)
                    busy = false
                }
            },
            dismissText = "仅对新下载生效",
            onDismiss = {
                pendingTarget = null
                busy = true
                scope.launch {
                    applyStoragePath(context, prefs, target, migrate = false)
                    busy = false
                }
            },
        )
    }
}

/**
 * 选目录的三档结果。
 *
 * `internal` 而不是 `private`：画廊设置的「下载目录」那一行走的是**同一套**三道关
 * （换算真实路径 → 准入守卫 → 实写探针）。留两份实现必然漂，而漂的那一半通常是守卫那份。
 */
internal sealed interface DirChoice {
    data class Accept(val path: String) : DirChoice
    data class NeedsPermission(val path: String) : DirChoice
    data class Reject(val reason: String) : DirChoice
}

/** 三道关：换算真实路径 → 目录准入守卫 → 实写探针。任一不过就把理由交回用户。 */
internal fun evaluatePickedDir(context: Context, uri: Uri): DirChoice {
    val path = ComicStorageRoot.resolveTreePath(context, uri)
        ?: return DirChoice.Reject(
            "这个位置无法作为存储目录。请进入「内部存储」或 SD 卡，选择一个普通文件夹。"
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
    prefs: NetworkPreferences,
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
    prefs: NetworkPreferences,
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
            if (migrate) BusinessPorts.of(context).downloads.relocateTasks(oldDir, newDir)
            movedDirs
        }
    }
    result.onSuccess { dirs ->
        val tail = if (migrate) "，已迁移 $dirs 个源目录" else "，已有内容留在原目录"
        Toast.makeText(context, "存储目录已切换$tail", Toast.LENGTH_LONG).show()
    }.onFailure { e ->
        android.util.Log.w("VeneraDebug", "switch storage dir failed", e)
        Toast.makeText(context, "没切过去，存储目录没有改动 🥲 检查下新位置能不能写", Toast.LENGTH_LONG).show()
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
            "打不开系统设置页。请在「设置 → 应用 → 特殊应用权限 → 所有文件访问」里手动打开",
            Toast.LENGTH_LONG,
        ).show()
    }
}

/** 字节数 → 人类可读。同包内共享（`UpdateCheckUi` 的更新进度那一行也用这把），别再开第四份。 */
internal fun formatMib(bytes: Long): String =
    if (bytes < 1024) "$bytes B"
    else if (bytes < 1024 * 1024) "%.1f KiB".format(bytes / 1024.0)
    else "%.1f MiB".format(bytes / (1024.0 * 1024.0))
