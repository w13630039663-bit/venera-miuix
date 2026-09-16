package com.venera.compose.feature.sourcemanage

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.venera.compose.source.ComicSource
import com.venera.compose.source.js.JsComicSource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicSourceScreen(
    onNavigateBack: () -> Unit,
    viewModel: ComicSourceViewModel = viewModel()
) {
    val context = LocalContext.current
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val activeKey by viewModel.activeSourceKey.collectAsStateWithLifecycle()
    val latencyMap by viewModel.latencyMap.collectAsStateWithLifecycle()
    val repoItems by viewModel.repoItems.collectAsStateWithLifecycle()
    val isLoadingRepo by viewModel.isLoadingRepo.collectAsStateWithLifecycle()
    val installingKeys by viewModel.installingKeys.collectAsStateWithLifecycle()

    var showRepoDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var deleteConfirmKey by remember { mutableStateOf<String?>(null) }

    // 监听提示消息
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
                title = "漫画源扩展管理",
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
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. 顶部操作与概览卡片
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MiuixTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Extension,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "共已加载 ${sources.size} 个漫画源",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "当前活跃: $activeKey",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant
                                )
                            }
                            IconButton(onClick = { viewModel.refreshPings() }) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "测速",
                                    tint = MiuixTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // 快捷操作按钮组
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showRepoDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudDownload,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "官方清单",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showUrlDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Link,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "链接安装",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { filePicker.launch("*/*") }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "本地导入",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. 已安装漫画源列表标题
            item {
                Text(
                    text = "已安装源列表",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // 3. 漫画源卡片
            items(sources, key = { it.key }) { source ->
                val isActive = source.key == activeKey
                val ping = latencyMap[source.key]
                val isJsSource = source is JsComicSource

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = source.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isJsSource) Color(0xFF9C27B0).copy(alpha = 0.15f) else Color(0xFF2196F3).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (isJsSource) "JS 扩展" else "内置",
                                        fontSize = 10.sp,
                                        color = if (isJsSource) Color(0xFF9C27B0) else Color(0xFF2196F3),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "key: ${source.key} · v${source.version}",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val pingText = if (ping == null) "探测中" else if (ping < 0) "超时" else "${ping}ms"
                                val pingColor = if (ping != null && ping > 0) Color(0xFF4CAF50) else Color.Gray
                                Text(
                                    text = pingText,
                                    fontSize = 11.sp,
                                    color = pingColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // 操作按钮：设为活跃源
                        if (isActive) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            ) {
                                Text(
                                    text = "活跃中",
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.setActiveSource(source.key) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("设为活跃", fontSize = 11.sp)
                            }
                        }

                        // 删除按钮（仅限 JS 扩展源）
                        if (isJsSource) {
                            IconButton(onClick = { deleteConfirmKey = source.key }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "删除",
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 官方清单 Dialog
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
                            text = "官方源仓库清单 (33条)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showRepoDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "数据源: venera-configs@main",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant
                    )
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
                            items(repoItems) { item ->
                                val installed = sources.find { it.key == item.key }
                                val isInstalling = installingKeys.contains(item.key)

                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${item.fileName} · v${item.version}",
                                                fontSize = 11.sp,
                                                color = MiuixTheme.colorScheme.onBackgroundVariant
                                            )
                                        }

                                        if (isInstalling) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        } else if (installed != null) {
                                            val hasUpdate = item.version > installed.version
                                            if (hasUpdate) {
                                                Button(
                                                    onClick = { viewModel.installFromRepo(item) },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text("更新", fontSize = 11.sp)
                                                }
                                            } else {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                ) {
                                                    Text(
                                                        text = "已安装",
                                                        fontSize = 11.sp,
                                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            Button(
                                                onClick = { viewModel.installFromRepo(item) },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Text("安装", fontSize = 11.sp)
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

    // 链接安装 Dialog
    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("从链接安装漫画源") },
            text = {
                Column {
                    Text("输入 .js 规则文件的 HTTP/HTTPS 链接：", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = { Text("https://.../source.js") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (urlInput.isNotBlank()) {
                            viewModel.installFromUrl(urlInput.trim())
                            urlInput = ""
                            showUrlDialog = false
                        }
                    }
                ) {
                    Text("安装")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 删除确认 Dialog
    if (deleteConfirmKey != null) {
        val keyToDelete = deleteConfirmKey!!
        AlertDialog(
            onDismissRequest = { deleteConfirmKey = null },
            title = { Text("确认删除漫画源？") },
            text = { Text("将删除扩展规则文件及本地关联缓存数据 ($keyToDelete)") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSource(keyToDelete)
                        deleteConfirmKey = null
                    }
                ) {
                    Text("删除", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmKey = null }) {
                    Text("取消")
                }
            }
        )
    }
}
