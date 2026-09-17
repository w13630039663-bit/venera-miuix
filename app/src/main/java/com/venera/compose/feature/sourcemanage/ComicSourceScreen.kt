package com.venera.compose.feature.sourcemanage

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.venera.compose.source.ComicSource
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.js.JsComicSource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 漫画源管理与专属配置界面
 * 1:1 对齐原版 Venera 界面设计：
 * 1. 源专属动态配置 (Input / Select / Switch / Callback)
 * 2. 账号管理体系 (登录 / 重新登录 / 注销 / 网页登录 / 凭证保存)
 * 3. 官方源清单 (33源) 一键下载与本地/网络链接安装
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicSourceScreen(
    onNavigateBack: () -> Unit,
    viewModel: ComicSourceViewModel = viewModel()
) {
    val context = LocalContext.current
    val sourceRows by viewModel.sourceRows.collectAsStateWithLifecycle()
    val activeKey by viewModel.activeSourceKey.collectAsStateWithLifecycle()
    val latencyMap by viewModel.latencyMap.collectAsStateWithLifecycle()
    val repoItems by viewModel.repoItems.collectAsStateWithLifecycle()
    val isLoadingRepo by viewModel.isLoadingRepo.collectAsStateWithLifecycle()
    val installingFileNames by viewModel.installingFileNames.collectAsStateWithLifecycle()
    val installAllProgress by viewModel.installAllProgress.collectAsStateWithLifecycle()
    val testingKeys by viewModel.testingKeys.collectAsStateWithLifecycle()
    val unreachableHosts by viewModel.unreachableHosts.collectAsStateWithLifecycle()
    val configBundles by viewModel.configBundles.collectAsStateWithLifecycle()
    val checkingUpdates by viewModel.checkingUpdates.collectAsStateWithLifecycle()
    val updateCandidates by viewModel.updateCandidates.collectAsStateWithLifecycle()
    val batchUpdateProgress by viewModel.batchUpdateProgress.collectAsStateWithLifecycle()
    val repoUrlValue by viewModel.repoUrl.collectAsStateWithLifecycle()

    // 已安装源数量（仅统计启用项，这才是真正参与聚合搜索的数量）
    val enabledCount = sourceRows.count { it.enabled }

    // collectAsStateWithLifecycle() 返回的是委托属性，Kotlin 不允许对其做智能转换，
    // 因此这里先取到局部变量再在分支中使用。
    val syncProgress = installAllProgress

    var showRepoDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var repoUrlInput by remember { mutableStateOf("") }
    var deleteConfirmRow by remember { mutableStateOf<SourceRow?>(null) }
    var logoutConfirmKey by remember { mutableStateOf<String?>(null) }

    // 编辑 Input 设置弹窗状态
    var editingInputSetting by remember { mutableStateOf<Triple<String, SourceSettingItem.Input, String>?>(null) }
    // 编辑 Select 设置弹窗状态
    var editingSelectSetting by remember { mutableStateOf<Pair<String, SourceSettingItem.Select>?>(null) }
    // 登录弹窗状态 (sourceKey)
    var loginTargetSourceKey by remember { mutableStateOf<String?>(null) }

    // 内嵌 WebView 网页登录目标 (sourceKey to account.loginWithWebview.url)
    var webLoginTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    // 手填 Cookie 登录目标 (sourceKey)
    var cookieLoginTargetKey by remember { mutableStateOf<String?>(null) }
    // 源脚本编辑目标 (fileName to displayName)
    var editTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val scope = rememberCoroutineScope()

    // 监听 Toast 消息
    LaunchedEffect(Unit) {
        viewModel.messageEvent.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // 本地文件选择器
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!content.isNullOrBlank()) {
                    viewModel.installFromScriptContent(content, uri.lastPathSegment ?: "本地规则")
                }
            } catch (e: Exception) {
                Toast.makeText(context, "读取本地文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "漫画源",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==================== 1. 顶部操作概览卡片 ====================
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MiuixTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Extension,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "已启用 $enabledCount / ${sourceRows.size} 个漫画源",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "当前默认: $activeKey",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            IconButton(onClick = { viewModel.refreshPings() }) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "网络测速",
                                    tint = MiuixTheme.colorScheme.primary
                                )
                            }
                        }

                        // 网络受限提示：熔断中的域名说明当前网络直连不可达，需要代理
                        if (unreachableHosts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFFA000).copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "⚠️ ${unreachableHosts.size} 个站点当前网络不可达，其源已自动熔断跳过",
                                        fontSize = 11.sp,
                                        color = Color(0xFFFFA000),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "如需使用这些源，请在设置中配置 HTTP/SOCKS 代理",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = Color.DarkGray.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // 快捷操作按钮组
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        // 每次打开都用当前值回填，避免显示上一次编辑到一半的内容
                                        repoUrlInput = repoUrlValue
                                        showRepoDialog = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudDownload,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "官方清单",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.DarkGray.copy(alpha = 0.25f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showUrlDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Link,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "链接安装",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.DarkGray.copy(alpha = 0.25f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { filePicker.launch("*/*") }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "本地导入",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // 检查更新（对齐官方 _CheckUpdatesButton）
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.DarkGray.copy(alpha = 0.25f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = !checkingUpdates) { viewModel.checkUpdates() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (checkingUpdates) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MiuixTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Update,
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "检查更新",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 一键同步全部 33 个官方源（已装且版本一致会自动跳过）
                        val total = syncProgress?.second ?: repoItems.size
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (syncProgress != null) Color.DarkGray.copy(alpha = 0.35f)
                            else MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = syncProgress == null) {
                                    viewModel.installAll()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 9.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (syncProgress != null) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "正在同步 ${syncProgress.first}/$total ...",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudDownload,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "一键安装 / 更新全部官方源",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==================== 2. 漫画源专属配置卡片列表 (对齐截图) ====================
            items(sourceRows, key = { it.key }) { row ->
                val bundle = configBundles[row.key]
                val isActive = row.key == activeKey
                val isTesting = testingKeys.contains(row.key)
                val latency = latencyMap[row.key]

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        // 1. 源头部：标题、版本胶囊与操作图标
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (row.pinned) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = "已置顶",
                                        tint = Color(0xFFFFC107),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = row.name,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (row.enabled) MiuixTheme.colorScheme.onSurface
                                    else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // 版本胶囊
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF2C2C2E)
                                ) {
                                    Text(
                                        text = row.version,
                                        fontSize = 12.sp,
                                        color = Color(0xFFD1D1D6),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                // 有新版本（对应官方 availableUpdates 命中的 "New Version" 胶囊）
                                row.latestVersion?.let { newVersion ->
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    ) {
                                        Text(
                                            text = "New v$newVersion",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                // 连通性指示
                                if (latency != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (latency >= 0) "${latency}ms" else "不可达",
                                        fontSize = 11.sp,
                                        color = if (latency in 0..3000) Color(0xFF4CAF50) else Color(0xFFE53935)
                                    )
                                }
                            }

                            // 右侧操作区
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // 可用性测试（真实发一次请求）
                                IconButton(
                                    onClick = { viewModel.testSource(row) },
                                    enabled = !isTesting,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    if (isTesting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MiuixTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Speed,
                                            contentDescription = "连通性测试",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // 启用 / 禁用开关
                                Switch(
                                    checked = row.enabled,
                                    onCheckedChange = { checked ->
                                        row.fileName?.let { viewModel.setEnabled(it, checked) }
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .height(28.dp)
                                        .width(44.dp)
                                )
                            }
                        }

                        // 第二行：活跃源 / 排序 / 刷新 / 卸载
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isActive) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                ) {
                                    Text(
                                        text = "活跃",
                                        fontSize = 10.sp,
                                        color = MiuixTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "设为活跃",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    modifier = Modifier
                                        .clickable { viewModel.setActiveSource(row.key) }
                                        .padding(vertical = 4.dp, horizontal = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // 置顶
                            IconButton(
                                onClick = { row.fileName?.let { viewModel.setPinned(it, !row.pinned) } },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = if (row.pinned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "置顶",
                                    tint = if (row.pinned) Color(0xFFFFC107) else Color.Gray,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // 上移 / 下移
                            IconButton(
                                onClick = { row.fileName?.let { viewModel.moveSource(true, it) } },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "上移",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { row.fileName?.let { viewModel.moveSource(false, it) } },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "下移",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // 重载配置
                            IconButton(
                                onClick = { viewModel.reloadSource(row.key) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "刷新配置",
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // 编辑 .js 原文（仅限 JS 扩展源）
                            if (row.isJs && row.fileName != null) {
                                IconButton(
                                    onClick = { editTarget = row.fileName to row.name },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.EditNote,
                                        contentDescription = "编辑源脚本",
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // 按 source.url 单独更新该源（仅限 JS 扩展源）
                            if (row.isJs && row.fileName != null) {
                                val updating = installingFileNames.contains(row.fileName)
                                IconButton(
                                    onClick = { viewModel.updateSource(row.fileName) },
                                    enabled = !updating,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    if (updating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(15.dp),
                                            strokeWidth = 2.dp,
                                            color = MiuixTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Update,
                                            contentDescription = "更新该源",
                                            tint = if (row.hasUpdate) Color(0xFFFFA000) else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // 卸载 / 删除漫画源（支持全部内置源与 JS 扩展源）
                            IconButton(
                                onClick = { deleteConfirmRow = row },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "删除源",
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // 分割线
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = Color.DarkGray.copy(alpha = 0.25f))

                        // ==================== 2. 源专属设置项 (settings) ====================
                        val settings = bundle?.settings.orEmpty()
                        if (settings.isNotEmpty()) {
                            settings.forEach { item ->
                                when (item) {
                                    is SourceSettingItem.Input -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    editingInputSetting = Triple(row.key, item, item.value)
                                                }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.title,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = item.value.ifBlank { item.defaultValue.ifBlank { "未设置" } },
                                                    fontSize = 12.sp,
                                                    color = Color.Gray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    editingInputSetting = Triple(row.key, item, item.value)
                                                },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Edit,
                                                    contentDescription = "编辑",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = Color.Gray
                                                )
                                            }
                                        }
                                    }

                                    is SourceSettingItem.Select -> {
                                        val displayOption = item.options.find { it.value == item.value }
                                        val displayText = displayOption?.text ?: item.value.ifBlank { item.defaultValue }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = item.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF2C2C2E),
                                                modifier = Modifier.clickable {
                                                    editingSelectSetting = Pair(row.key, item)
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = displayText,
                                                        fontSize = 13.sp,
                                                        color = Color(0xFFF2F2F7),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = null,
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    is SourceSettingItem.Switch -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = item.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Switch(
                                                checked = item.value,
                                                onCheckedChange = { checked ->
                                                    viewModel.updateSetting(row.key, item.key, checked)
                                                }
                                            )
                                        }
                                    }

                                    is SourceSettingItem.Callback -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = item.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Button(
                                                onClick = { viewModel.executeCallback(row.key, item.key) },
                                                colors = ButtonDefaults.buttonColors(color = Color(0xFF2C2C2E))
                                            ) {
                                                Text(
                                                    text = item.buttonText,
                                                    fontSize = 12.sp,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ==================== 3. 源账号管理区域 (account) ====================
                        val accountInfo = bundle?.accountInfo
                        if (accountInfo?.hasAccount == true) {
                            if (!accountInfo.isLogged) {
                                // 未登录状态：展示登录入口
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { loginTargetSourceKey = row.key }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "登录",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "登录",
                                        tint = Color.Gray
                                    )
                                }
                            } else {
                                // 已登录状态：展示账户信息、重新登录与注销
                                accountInfo.infoItems.forEach { (title, data) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text(text = data, fontSize = 13.sp, color = Color.Gray)
                                    }
                                }

                                // 重新登录
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.relogin(row.key) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(text = "重新登录", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text(text = "点击此处如果登录已过期", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    IconButton(
                                        onClick = { viewModel.relogin(row.key) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Refresh,
                                            contentDescription = "重新登录",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // 注销
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { logoutConfirmKey = row.key }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "注销", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE53935))
                                    IconButton(
                                        onClick = { logoutConfirmKey = row.key },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                            contentDescription = "注销",
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== 对话框组件区域 ====================

    // 1. Input 设置项编辑弹窗（含官方 inputValidator 正则校验）
    editingInputSetting?.let { (sourceKey, item, currentVal) ->
        var tempValue by remember { mutableStateOf(currentVal) }
        var errorText by remember { mutableStateOf<String?>(null) }

        // 官方 `inputValidator: RegExp(validator)` —— 编译失败时按「无校验」处理，
        // 不因为源写了个坏正则就让用户无法保存设置。
        val validator: Regex? = remember(item.key) {
            item.validator?.takeIf { it.isNotBlank() }
                ?.let { runCatching { Regex(it) }.getOrNull() }
        }

        Dialog(onDismissRequest = { editingInputSetting = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MiuixTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = item.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = tempValue,
                        onValueChange = {
                            tempValue = it
                            errorText = null
                        },
                        singleLine = true,
                        isError = errorText != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    errorText?.let { err ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = err, fontSize = 11.sp, color = Color(0xFFE53935))
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editingInputSetting = null }) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            // 官方用的是 `inputValidator.hasMatch(text)`（**子串匹配**，
                            // 不是全匹配）—— 不匹配则显示 errorText 且**不关闭**弹窗。
                            if (validator != null && !validator.containsMatchIn(tempValue)) {
                                errorText = "Invalid input"
                                return@Button
                            }
                            viewModel.updateSetting(sourceKey, item.key, tempValue.trim())
                            editingInputSetting = null
                        }) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }

    // 2. Select 设置项选择单选弹窗
    editingSelectSetting?.let { (sourceKey, item) ->
        Dialog(onDismissRequest = { editingSelectSetting = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MiuixTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = item.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(14.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(item.options) { option ->
                            val isSelected = option.value == item.value
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateSetting(sourceKey, item.key, option.value)
                                        editingSelectSetting = null
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = option.text,
                                    fontSize = 14.sp,
                                    color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MiuixTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 3. 登录弹窗（对齐官方 _LoginPage：只呈现该源真正声明的登录方式，
    //    避免给「仅支持网页登录」的源展示无意义的账密表单）
    loginTargetSourceKey?.let { sourceKey ->
        val bundle = configBundles[sourceKey]
        val accountInfo = bundle?.accountInfo
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var isLoggingIn by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { if (!isLoggingIn) loginTargetSourceKey = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MiuixTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "登录 ${bundle?.sourceName ?: ""}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (accountInfo?.loginUnavailable == true) {
                        Text(
                            text = "该源未在脚本中声明登录方式",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }

                    // ---- 账号密码登录（官方 account.login(account, pwd)）----
                    if (accountInfo?.supportsPasswordLogin == true) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("用户名 / 账号") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                if (username.isBlank() || password.isBlank()) {
                                    Toast.makeText(context, "用户名与密码不能为空", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isLoggingIn = true
                                viewModel.login(sourceKey, username.trim(), password.trim()) { success, _ ->
                                    isLoggingIn = false
                                    if (success) {
                                        loginTargetSourceKey = null
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoggingIn
                        ) {
                            if (isLoggingIn) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                            } else {
                                Text("继续登录")
                            }
                        }
                    }

                    // ---- 内嵌 WebView 网页登录（官方 account.loginWithWebview）----
                    accountInfo?.loginWebsite?.takeIf { it.isNotBlank() }?.let { webUrl ->
                        if (accountInfo.supportsPasswordLogin) Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                // 交给内嵌 WebView：只有它才能同时拿到 cookie 与 localStorage
                                webLoginTarget = sourceKey to webUrl
                                loginTargetSourceKey = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("通过网页登录")
                        }
                    }

                    // ---- 手填 Cookie 登录（官方 account.loginWithCookies）----
                    if (accountInfo?.supportsCookieLogin == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                cookieLoginTargetKey = sourceKey
                                loginTargetSourceKey = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("手动填写 Cookie")
                        }
                    }

                    // ---- 注册外链（官方 account.registerWebsite）----
                    accountInfo?.registerWebsite?.takeIf { it.isNotBlank() }?.let { regUrl ->
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(regUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "无法打开注册页面", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("创建账号 / 注册")
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. Cookie 直填登录弹窗（官方 account.loginWithCookies.fields / .validate）
    cookieLoginTargetKey?.let { sourceKey ->
        val bundle = configBundles[sourceKey]
        val fields = bundle?.accountInfo?.cookieFields.orEmpty()
        val values = remember(sourceKey) {
            mutableStateListOf<String>().apply { repeat(fields.size) { add("") } }
        }
        var submitting by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { if (!submitting) cookieLoginTargetKey = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MiuixTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "填写 Cookie 登录", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "请从浏览器开发者工具中复制对应的 Cookie 值",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        itemsIndexed(fields) { index, name ->
                            OutlinedTextField(
                                value = values.getOrElse(index) { "" },
                                onValueChange = { if (index < values.size) values[index] = it },
                                label = { Text(name) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { cookieLoginTargetKey = null }, enabled = !submitting) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                submitting = true
                                scope.launch {
                                    val res = viewModel.loginWithCookies(sourceKey, values.toList())
                                    submitting = false
                                    Toast.makeText(
                                        context,
                                        if (res.isSuccess) "登录成功"
                                        else (res.exceptionOrNull()?.message ?: "登录失败"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (res.isSuccess) cookieLoginTargetKey = null
                                }
                            },
                            enabled = !submitting
                        ) {
                            if (submitting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                            } else {
                                Text("登录")
                            }
                        }
                    }
                }
            }
        }
    }

    // 5. 内嵌 WebView 网页登录（官方 account.loginWithWebview）
    //    只有内嵌 WebView 才能在登录成功后回抓 cookie 与 localStorage。
    webLoginTarget?.let { (sourceKey, loginUrl) ->
        WebLoginScreen(
            loginUrl = loginUrl,
            sourceName = configBundles[sourceKey]?.sourceName ?: sourceKey,
            onCheckLogin = { url, title -> viewModel.checkWebLogin(sourceKey, url, title) },
            onComplete = { cookieHeader, localStorageJson, url ->
                viewModel.completeWebLogin(sourceKey, cookieHeader, localStorageJson, url)
            },
            onFinish = { webLoginTarget = null }
        )
    }

    // 4. 注销确认 Dialog
    logoutConfirmKey?.let { sourceKey ->
        AlertDialog(
            onDismissRequest = { logoutConfirmKey = null },
            title = { Text("确认注销") },
            text = { Text("确定要退出该漫画源的账号登录状态吗？已保存的凭证将被清除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.logout(sourceKey)
                    logoutConfirmKey = null
                }) {
                    Text("注销", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { logoutConfirmKey = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 5. 删除源确认 Dialog
    deleteConfirmRow?.let { row ->
        AlertDialog(
            onDismissRequest = { deleteConfirmRow = null },
            title = { Text("确认删除") },
            text = { Text("确认删除「${row.name}」漫画源吗？删除后相关本地缓存与配置将被清理。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSource(row.key, row.fileName)
                    deleteConfirmRow = null
                }) {
                    Text("删除", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmRow = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 8. 官方清单弹窗 (33源)
    if (showRepoDialog) {
        Dialog(onDismissRequest = { showRepoDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MiuixTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "官方源仓库清单 (${repoItems.size}条)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showRepoDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Repo URL：可编辑 + Refresh（对齐官方仓库清单顶部卡片，
                    // 上游这里就是一个输入框而不是只读说明文字）
                    OutlinedTextField(
                        value = repoUrlInput,
                        onValueChange = { repoUrlInput = it },
                        label = { Text("Repo URL") },
                        placeholder = { Text("https://.../index.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "地址应指向仓库的 index.json；留空则回退官方默认地址",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { repoUrlInput = viewModel.defaultRepoUrl }) {
                            Text("恢复默认")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.setRepoUrl(repoUrlInput) },
                            enabled = !isLoadingRepo
                        ) {
                            if (isLoadingRepo) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Refresh")
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    // 一键同步入口
                    Button(
                        onClick = { viewModel.installAll() },
                        enabled = syncProgress == null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (syncProgress != null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("同步中 ${syncProgress.first}/${syncProgress.second}")
                        } else {
                            Text("一键安装 / 更新全部")
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    if (isLoadingRepo) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(repoItems, key = { it.fileName }) { item ->
                                // 安装状态必须按 fileName 判定：copy_manga.js 与
                                // copy_manga_multi_accounts.js 共用同一个 key
                                val installedRow = sourceRows.find { it.fileName == item.fileName }
                                val isInstalling = installingFileNames.contains(item.fileName)
                                val hasUpdate = installedRow != null &&
                                        ComicSourceManager.compareVersion(item.version, installedRow.version) > 0

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.DarkGray.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = item.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "v${item.version}",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                            if (!item.description.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = item.description,
                                                    fontSize = 11.sp,
                                                    color = Color.Gray,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))
                                        when {
                                            isInstalling -> CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                            hasUpdate -> Button(
                                                onClick = { viewModel.installFromRepo(item) },
                                                colors = ButtonDefaults.buttonColors(color = Color(0xFFFFA000))
                                            ) {
                                                Text("更新", fontSize = 11.sp, color = Color.White)
                                            }
                                            installedRow != null -> Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "已安装 v${installedRow.version}",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF4CAF50),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                            else -> Button(
                                                onClick = { viewModel.installFromRepo(item) },
                                                colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary)
                                            ) {
                                                Text("安装", fontSize = 11.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 9. 链接安装 Dialog
    if (showUrlDialog) {
        Dialog(onDismissRequest = { showUrlDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MiuixTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "通过 JS 规则网络链接安装", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = { Text("https://.../source.js") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showUrlDialog = false }) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (urlInput.isNotBlank()) {
                                viewModel.installFromUrl(urlInput.trim())
                                showUrlDialog = false
                                urlInput = ""
                            }
                        }) {
                            Text("开始安装")
                        }
                    }
                }
            }
        }
    }

    // 10. 检查更新结果弹窗（对齐官方 showUpdateDialog：列出 name: version 并可一键全更）
    updateCandidates?.takeIf { it.isNotEmpty() }?.let { candidates ->
        val progress = batchUpdateProgress
        AlertDialog(
            onDismissRequest = { if (progress == null) viewModel.dismissUpdateCandidates() },
            title = { Text("发现 ${candidates.size} 个可更新源") },
            text = {
                Column {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(candidates) { (name, newVersion) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "→ v$newVersion",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    progress?.let { (done, total) ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "正在更新 $done/$total ...",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.updateAllAvailable() },
                    enabled = progress == null
                ) {
                    Text("全部更新")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissUpdateCandidates() },
                    enabled = progress == null
                ) {
                    Text("稍后")
                }
            }
        )
    }

    // 11. 源脚本编辑页（对齐官方 _EditFilePage）
    editTarget?.let { (fileName, sourceName) ->
        // 官方源脚本多为 10~60KB，同步读取足够，也少一层 loading 态
        val initialText = remember(fileName) { viewModel.readSourceText(fileName) }
        if (initialText == null) {
            LaunchedEffect(fileName) {
                Toast.makeText(context, "无法读取源文件: $fileName", Toast.LENGTH_SHORT).show()
                editTarget = null
            }
        } else {
            SourceEditScreen(
                fileName = fileName,
                sourceName = sourceName,
                initialText = initialText,
                onSave = { content, cb -> viewModel.saveSourceText(fileName, content, cb) },
                onClose = { editTarget = null }
            )
        }
    }
}
