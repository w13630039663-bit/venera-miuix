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
import com.venera.compose.source.ComicSource
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
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val activeKey by viewModel.activeSourceKey.collectAsStateWithLifecycle()
    val latencyMap by viewModel.latencyMap.collectAsStateWithLifecycle()
    val repoItems by viewModel.repoItems.collectAsStateWithLifecycle()
    val isLoadingRepo by viewModel.isLoadingRepo.collectAsStateWithLifecycle()
    val installingKeys by viewModel.installingKeys.collectAsStateWithLifecycle()
    val configBundles by viewModel.configBundles.collectAsStateWithLifecycle()

    var showRepoDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var deleteConfirmKey by remember { mutableStateOf<String?>(null) }
    var logoutConfirmKey by remember { mutableStateOf<String?>(null) }

    // 编辑 Input 设置弹窗状态
    var editingInputSetting by remember { mutableStateOf<Triple<String, SourceSettingItem.Input, String>?>(null) }
    // 编辑 Select 设置弹窗状态
    var editingSelectSetting by remember { mutableStateOf<Pair<String, SourceSettingItem.Select>?>(null) }
    // 登录弹窗状态 (sourceKey)
    var loginTargetSourceKey by remember { mutableStateOf<String?>(null) }

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
                                    text = "已启用 ${sources.size} 个漫画源",
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
                                    .clickable { showRepoDialog = true }
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
                        }
                    }
                }
            }

            // ==================== 2. 漫画源专属配置卡片列表 (对齐截图) ====================
            items(sources, key = { it.key }) { source ->
                val bundle = configBundles[source.key]
                val isActive = source.key == activeKey
                val ping = latencyMap[source.key]
                val isJsSource = source is JsComicSource

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
                                Text(
                                    text = source.name,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // 版本胶囊
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF2C2C2E)
                                ) {
                                    Text(
                                        text = source.version,
                                        fontSize = 12.sp,
                                        color = Color(0xFFD1D1D6),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // 右侧三个操作图标
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // 设为活跃快捷指示
                                if (isActive) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        modifier = Modifier.padding(end = 4.dp)
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
                                            .clickable { viewModel.setActiveSource(source.key) }
                                            .padding(end = 4.dp)
                                    )
                                }

                                // 刷新/重载
                                IconButton(
                                    onClick = { viewModel.reloadSource(source.key) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = "刷新配置",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // 删除（仅限 JS 扩展源）
                                if (isJsSource) {
                                    IconButton(
                                        onClick = { deleteConfirmKey = source.key },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "删除源",
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
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
                                                    editingInputSetting = Triple(source.key, item, item.value)
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
                                                    editingInputSetting = Triple(source.key, item, item.value)
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
                                                    editingSelectSetting = Pair(source.key, item)
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
                                                    viewModel.updateSetting(source.key, item.key, checked)
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
                                                onClick = { viewModel.executeCallback(source.key, item.key) },
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
                                        .clickable { loginTargetSourceKey = source.key }
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
                                        .clickable { viewModel.relogin(source.key) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(text = "重新登录", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text(text = "点击此处如果登录已过期", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    IconButton(
                                        onClick = { viewModel.relogin(source.key) },
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
                                        .clickable { logoutConfirmKey = source.key }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "注销", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE53935))
                                    IconButton(
                                        onClick = { logoutConfirmKey = source.key },
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

    // 1. Input 设置项编辑弹窗
    editingInputSetting?.let { (sourceKey, item, currentVal) ->
        var tempValue by remember { mutableStateOf(currentVal) }
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
                        onValueChange = { tempValue = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editingInputSetting = null }) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
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

    // 3. 登录弹窗 (账号密码 / 网页登录 / 注册外链)
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

                    // 网页登录 (loginWebsite)
                    accountInfo?.loginWebsite?.takeIf { it.isNotBlank() }?.let { webUrl ->
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "无法打开网页: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("通过网页登录 (WebView)")
                        }
                    }

                    // 注册账号 (registerWebsite)
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
    deleteConfirmKey?.let { key ->
        AlertDialog(
            onDismissRequest = { deleteConfirmKey = null },
            title = { Text("确认删除") },
            text = { Text("确认彻底删除该漫画源扩展吗？删除后相关本地缓存将被清理。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSource(key)
                    deleteConfirmKey = null
                }) {
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

    // 6. 官方清单弹窗 (33源)
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

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "数据源: venera-configs@main",
                        fontSize = 11.sp,
                        color = Color.Gray
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
                                        if (isInstalling) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        } else if (installed != null) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "已安装",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF4CAF50),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        } else {
                                            Button(
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

    // 7. 链接安装 Dialog
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
}
