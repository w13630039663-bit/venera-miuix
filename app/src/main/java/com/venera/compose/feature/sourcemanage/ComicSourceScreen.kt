/**
 * 漫画源管理与专属配置界面
 * 对齐 _design/source-manage-redesign.html 重设计规范：
 * 1. 列表态：只回答三件事 —— 哪些开着、哪些能用、哪些该更新。
 * 2. 状态条：已启用 / 待更新 / 连不上 + 重新测速。
 * 3. 搜索与分类 Chips：全部 / 官方 / 扩展 / 停用。
 * 4. 手风琴展开第二层：源设置 / 账号 / 维护。
 * 5. 独立添加源弹层 (ModalBottomSheet)：官方清单 / 链接安装 / 本地导入 / 一键同步。
 * 6. 独立排序模式：上下箭头微调与星标置顶。
 */
package com.venera.compose.feature.sourcemanage

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.venera.compose.components.LocalWindowWidthDp
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.contentMaxWidthFor
import com.venera.compose.components.venera.*
import com.venera.compose.components.wideScreenLayoutMode
import com.venera.compose.source.ComicSource
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.js.JsComicSource
import com.venera.compose.ui.tokens.LocalBottomBarClearance
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class SourceFilter(val label: String) {
    ALL("全部"),
    OFFICIAL("官方"),
    EXTENSION("扩展"),
    DISABLED("停用")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicSourceScreen(
    onNavigateBack: () -> Unit,
    viewModel: ComicSourceViewModel = viewModel()
) {
    val context = LocalContext.current
    val tokens = VeneraTokens
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

    val enabledCount = sourceRows.count { it.enabled }
    val updateCount = sourceRows.count { it.hasUpdate }
    val unreachableCount = sourceRows.count {
        unreachableHosts.contains(it.key) || (it.latency != null && it.latency < 0)
    }

    val officialFileNames = remember(repoItems) { repoItems.map { it.fileName }.toSet() }
    val officialCount = sourceRows.count { !it.isJs || (it.fileName != null && officialFileNames.contains(it.fileName)) }
    val extensionCount = sourceRows.count { it.isJs && (it.fileName == null || !officialFileNames.contains(it.fileName)) }
    val disabledCount = sourceRows.count { !it.enabled }

    val syncProgress = installAllProgress

    // 交互与搜索/过滤状态
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(SourceFilter.ALL) }
    var inSortMode by remember { mutableStateOf(false) }
    var expandedKey by remember { mutableStateOf<String?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }

    // 弹窗状态
    var showRepoDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var repoUrlInput by remember { mutableStateOf("") }
    var deleteConfirmRow by remember { mutableStateOf<SourceRow?>(null) }
    var logoutConfirmKey by remember { mutableStateOf<String?>(null) }

    var editingInputSetting by remember { mutableStateOf<Triple<String, SourceSettingItem.Input, String>?>(null) }
    var editingSelectSetting by remember { mutableStateOf<Pair<String, SourceSettingItem.Select>?>(null) }
    var loginTargetSourceKey by remember { mutableStateOf<String?>(null) }
    var webLoginTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var cookieLoginTargetKey by remember { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.messageEvent.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

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
                android.util.Log.w("VeneraDebug", "read local source file failed", e)
                Toast.makeText(context, "这个文件读不出来 🥲 换一个 .js 源文件试试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // 过滤列表
    val filteredRows = remember(sourceRows, searchQuery, selectedFilter, officialFileNames) {
        sourceRows.filter { row ->
            val matchesSearch = searchQuery.isBlank() ||
                row.name.contains(searchQuery, ignoreCase = true) ||
                row.key.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                SourceFilter.ALL -> true
                SourceFilter.OFFICIAL -> (!row.isJs || (row.fileName != null && officialFileNames.contains(row.fileName)))
                SourceFilter.EXTENSION -> (row.isJs && (row.fileName == null || !officialFileNames.contains(row.fileName)))
                SourceFilter.DISABLED -> !row.enabled
            }
            matchesSearch && matchesFilter
        }
    }
    val pinnedRows = remember(filteredRows) { filteredRows.filter { it.pinned } }
    val otherRows = remember(filteredRows) { filteredRows.filter { !it.pinned } }

    Box(modifier = Modifier.fillMaxSize()) {
        val contentMaxWidth = contentMaxWidthFor(
            wideScreenLayoutMode(LocalWindowWidthDp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(contentMaxWidth?.let { Modifier.widthIn(max = it) } ?: Modifier)
                .align(Alignment.TopCenter)
                .nestedScroll(topBarBehavior.nestedScrollConnection)
                .blurBackdropSource(topBarBackdrop),
            contentPadding = PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = statusBarTop + VeneraSpacing.topBarFloor,
                bottom = LocalBottomBarClearance.current + VeneraSpacing.space9
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (inSortMode) {
                // ==================== 排序模式 ====================
                item(key = "sort-hint") {
                    Text(
                        text = "按住上下箭头调整排序，点星标把常用的源置顶。列表顺序决定聚合搜索里各源的先后。",
                        fontSize = 12.sp,
                        color = tokens.color.textSecondary,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                itemsIndexed(sourceRows, key = { _, r -> "sort-${r.key}" }) { index, row ->
                    VeneraCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 上移与下移
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VeneraIconButton(
                                    onClick = {
                                        if (index > 0 && row.fileName != null) {
                                            viewModel.moveSource(true, row.fileName)
                                        }
                                    },
                                    enabled = index > 0 && row.fileName != null,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "上移",
                                        tint = if (index > 0 && row.fileName != null) tokens.color.textPrimary else tokens.color.textDisabled,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                VeneraIconButton(
                                    onClick = {
                                        if (index < sourceRows.size - 1 && row.fileName != null) {
                                            viewModel.moveSource(false, row.fileName)
                                        }
                                    },
                                    enabled = index < sourceRows.size - 1 && row.fileName != null,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "下移",
                                        tint = if (index < sourceRows.size - 1 && row.fileName != null) tokens.color.textPrimary else tokens.color.textDisabled,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = row.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (row.enabled) tokens.color.textPrimary else tokens.color.textDisabled
                                    )
                                    if (row.key == activeKey) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = StatusColors.BadgeSurface
                                        ) {
                                            Text(
                                                text = "默认",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = StatusColors.OnBadgeSurface,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (row.enabled) "已启用 · v${row.version}" else "已停用 · v${row.version}",
                                    fontSize = 11.sp,
                                    color = tokens.color.textTertiary
                                )
                            }

                            VeneraIconButton(
                                onClick = {
                                    if (row.fileName != null) {
                                        viewModel.setPinned(row.fileName, !row.pinned)
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (row.pinned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (row.pinned) "取消置顶" else "置顶",
                                    tint = if (row.pinned) StatusColors.Degraded else tokens.color.textTertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // ==================== 常规列表态 ====================
                if (sourceRows.isEmpty()) {
                    item(key = "src-empty") {
                        VeneraEmptyView(
                            title = "还没有漫画源",
                            message = "点击右上角「+」或一键安装官方源添加你的第一个源",
                        )
                    }
                }

                // 1. 顶部状态盘
                item(key = "src-stats") {
                    VeneraCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = enabledCount.toString(),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.color.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "已启用",
                                    fontSize = 11.sp,
                                    color = tokens.color.textSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(24.dp)
                                    .background(tokens.color.outline.copy(alpha = 0.15f))
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = updateCount.toString(),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (updateCount > 0) StatusColors.Degraded else tokens.color.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "待更新",
                                    fontSize = 11.sp,
                                    color = tokens.color.textSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(24.dp)
                                    .background(tokens.color.outline.copy(alpha = 0.15f))
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = unreachableCount.toString(),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (unreachableCount > 0) StatusColors.Failing else tokens.color.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "连不上",
                                    fontSize = 11.sp,
                                    color = tokens.color.textSecondary
                                )
                            }

                            VeneraIconButton(
                                onClick = { viewModel.refreshPings() },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "重新测速",
                                    tint = tokens.color.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // 2. 搜索框
                item(key = "src-search") {
                    VeneraTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "搜索源名称或标识",
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = tokens.color.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = if (searchQuery.isNotBlank()) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "清除",
                                    tint = tokens.color.textSecondary,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { searchQuery = "" }
                                )
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 3. 分类过滤 Chips
                item(key = "src-chips") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SourceFilter.values().forEach { filter ->
                            val isSelected = selectedFilter == filter
                            val count = when (filter) {
                                SourceFilter.ALL -> sourceRows.size
                                SourceFilter.OFFICIAL -> officialCount
                                SourceFilter.EXTENSION -> extensionCount
                                SourceFilter.DISABLED -> disabledCount
                            }

                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (isSelected) tokens.color.primaryContainer else tokens.color.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.clickable { selectedFilter = filter }
                            ) {
                                Text(
                                    text = "${filter.label} $count",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) tokens.color.primary else tokens.color.textSecondary,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // 4. 置顶源分段
                if (pinnedRows.isNotEmpty()) {
                    item(key = "sec-pinned") {
                        Text(
                            text = "置顶",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.color.textSecondary,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                    items(pinnedRows, key = { "pin-${it.key}" }) { row ->
                        RenderSourceItem(
                            row = row,
                            activeKey = activeKey,
                            isExpanded = expandedKey == row.key,
                            onToggleExpand = { expandedKey = if (expandedKey == row.key) null else row.key },
                            latency = latencyMap[row.key],
                            isUnreachable = unreachableHosts.contains(row.key) || (latencyMap[row.key] != null && latencyMap[row.key]!! < 0),
                            configBundle = configBundles[row.key],
                            onSetEnabled = { isEnabled -> if (row.fileName != null) viewModel.setEnabled(row.fileName, isEnabled) },
                            onSetActive = { viewModel.setActiveSource(row.key) },
                            onSetPinned = { isPinned -> if (row.fileName != null) viewModel.setPinned(row.fileName, isPinned) },
                            onCheckUpdates = { viewModel.checkUpdates() },
                            onUpdateSource = { if (row.fileName != null) viewModel.updateSource(row.fileName) },
                            onTestSource = { viewModel.testSource(row) },
                            onEditScript = { if (row.fileName != null) editTarget = Pair(row.fileName, row.name) },
                            onDelete = { deleteConfirmRow = row },
                            onOpenInputSetting = { item, currentVal -> editingInputSetting = Triple(row.key, item, currentVal) },
                            onOpenSelectSetting = { item -> editingSelectSetting = Pair(row.key, item) },
                            onUpdateSetting = { k, v -> viewModel.updateSetting(row.key, k, v) },
                            onExecuteCallback = { k -> viewModel.executeCallback(row.key, k) },
                            onLogin = { loginTargetSourceKey = row.key },
                            onWebLogin = { url -> webLoginTarget = Pair(row.key, url) },
                            onCookieLogin = { cookieLoginTargetKey = row.key },
                            onLogout = { logoutConfirmKey = row.key }
                        )
                    }
                }

                // 5. 全部源分段
                item(key = "sec-all") {
                    Text(
                        text = if (pinnedRows.isNotEmpty()) "全部源" else "漫画源",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.color.textSecondary,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                val listToRender = if (pinnedRows.isNotEmpty()) otherRows else filteredRows
                items(listToRender, key = { it.key }) { row ->
                    RenderSourceItem(
                        row = row,
                        activeKey = activeKey,
                        isExpanded = expandedKey == row.key,
                        onToggleExpand = { expandedKey = if (expandedKey == row.key) null else row.key },
                        latency = latencyMap[row.key],
                        isUnreachable = unreachableHosts.contains(row.key) || (latencyMap[row.key] != null && latencyMap[row.key]!! < 0),
                        configBundle = configBundles[row.key],
                        onSetEnabled = { isEnabled -> if (row.fileName != null) viewModel.setEnabled(row.fileName, isEnabled) },
                        onSetActive = { viewModel.setActiveSource(row.key) },
                        onSetPinned = { isPinned -> if (row.fileName != null) viewModel.setPinned(row.fileName, isPinned) },
                        onCheckUpdates = { viewModel.checkUpdates() },
                        onUpdateSource = { if (row.fileName != null) viewModel.updateSource(row.fileName) },
                        onTestSource = { viewModel.testSource(row) },
                        onEditScript = { if (row.fileName != null) editTarget = Pair(row.fileName, row.name) },
                        onDelete = { deleteConfirmRow = row },
                        onOpenInputSetting = { item, currentVal -> editingInputSetting = Triple(row.key, item, currentVal) },
                        onOpenSelectSetting = { item -> editingSelectSetting = Pair(row.key, item) },
                        onUpdateSetting = { k, v -> viewModel.updateSetting(row.key, k, v) },
                        onExecuteCallback = { k -> viewModel.executeCallback(row.key, k) },
                        onLogin = { loginTargetSourceKey = row.key },
                        onWebLogin = { url -> webLoginTarget = Pair(row.key, url) },
                        onCookieLogin = { cookieLoginTargetKey = row.key },
                        onLogout = { logoutConfirmKey = row.key }
                    )
                }
            }
        }

        // 顶栏组件
        VeneraTopAppBar(
            title = if (inSortMode) "调整顺序" else "漫画源",
            largeTitle = if (inSortMode) "调整顺序" else "漫画源",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            navigationIcon = {
                VeneraTopBarPill(onClick = {
                    if (inSortMode) inSortMode = false else onNavigateBack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary,
                    )
                }
            },
            actions = {
                if (inSortMode) {
                    TextButton(onClick = { inSortMode = false }) {
                        Text(
                            text = "完成",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = tokens.color.primary
                        )
                    }
                } else {
                    VeneraTopBarPill(onClick = { inSortMode = true }) {
                        Icon(
                            imageVector = Icons.Outlined.SwapVert,
                            contentDescription = "调整顺序",
                            tint = tokens.color.textSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    VeneraTopBarPill(onClick = { showAddSheet = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "添加源",
                            tint = tokens.color.textSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        )
    }

    // ==================== 添加源面板 (ModalBottomSheet) ====================
    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "添加漫画源",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = tokens.color.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "源脚本是一个 .js 文件，装进来就会出现在列表里",
                    fontSize = 12.sp,
                    color = tokens.color.textSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // 选项 1: 官方清单
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showAddSheet = false
                            showRepoDialog = true
                            viewModel.loadRepo()
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(tokens.color.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = null,
                            tint = tokens.color.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "官方清单", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tokens.color.textPrimary)
                        Text(text = "${repoItems.size.takeIf { it > 0 } ?: 33} 个源，按需安装或整体更新", fontSize = 11.sp, color = tokens.color.textSecondary)
                    }
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.color.textTertiary)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 选项 2: 从链接安装
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showAddSheet = false
                            showUrlDialog = true
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(tokens.color.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            tint = tokens.color.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "从链接安装", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tokens.color.textPrimary)
                        Text(text = "粘贴以 .js 结尾的地址", fontSize = 11.sp, color = tokens.color.textSecondary)
                    }
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.color.textTertiary)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 选项 3: 本地导入
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showAddSheet = false
                            filePicker.launch("*/*")
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(tokens.color.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = tokens.color.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "本地导入", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tokens.color.textPrimary)
                        Text(text = "从手机里挑一个 .js 文件", fontSize = 11.sp, color = tokens.color.textSecondary)
                    }
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.color.textTertiary)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 一键安装全部
                Button(
                    onClick = {
                        viewModel.installAll()
                        showAddSheet = false
                    },
                    enabled = syncProgress == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    if (syncProgress != null) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("同步中 ${syncProgress.first}/${syncProgress.second}")
                    } else {
                        Icon(imageVector = Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("一键安装 / 更新全部官方源", fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = "已装且版本一致的源会自动跳过",
                    fontSize = 11.sp,
                    color = tokens.color.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp)
                )
            }
        }
    }

    // ==================== 原生对话框体系（100% 完整保留） ====================

    // 1. Input 设置项编辑弹窗
    editingInputSetting?.let { (sourceKey, item, currentVal) ->
        var tempValue by remember { mutableStateOf(currentVal) }
        var errorText by remember { mutableStateOf<String?>(null) }

        val validator: Regex? = remember(item.key) {
            item.validator?.takeIf { it.isNotBlank() }
                ?.let { runCatching { Regex(it) }.getOrNull() }
        }

        Dialog(onDismissRequest = { editingInputSetting = null }) {
            VeneraCard(modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space8)) {
                Column(modifier = Modifier.padding(tokens.spacing.space9)) {
                    Text(text = item.title, fontSize = tokens.type.itemTitle, fontWeight = tokens.type.weightBold, color = tokens.color.textPrimary)
                    Spacer(modifier = Modifier.height(tokens.spacing.space7))
                    VeneraTextField(
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
                        Text(text = err, fontSize = tokens.type.overline, color = StatusColors.Failing)
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        VeneraTextButton(text = "取消", onClick = { editingInputSetting = null })
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
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
            VeneraCard(modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space8)) {
                Column(modifier = Modifier.padding(tokens.spacing.space9)) {
                    Text(text = item.title, fontSize = tokens.type.itemTitle, fontWeight = tokens.type.weightBold, color = tokens.color.textPrimary)
                    Spacer(modifier = Modifier.height(tokens.spacing.space7))
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

    // 3. 登录弹窗
    loginTargetSourceKey?.let { sourceKey ->
        val bundle = configBundles[sourceKey]
        val accountInfo = bundle?.accountInfo
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var isLoggingIn by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { if (!isLoggingIn) loginTargetSourceKey = null }) {
            VeneraCard(modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space8)) {
                Column(modifier = Modifier.padding(tokens.spacing.space9)) {
                    Text(
                        text = "登录 ${bundle?.sourceName ?: ""}",
                        fontSize = tokens.type.itemTitle,
                        fontWeight = tokens.type.weightBold,
                        color = tokens.color.textPrimary
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space6))

                    if (accountInfo?.loginUnavailable == true) {
                        Text(
                            text = "该源未在脚本中声明登录方式",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }

                    if (accountInfo?.supportsPasswordLogin == true) {
                        VeneraTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = "用户名 / 账号",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        VeneraTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "密码",
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

                    accountInfo?.loginWebsite?.takeIf { it.isNotBlank() }?.let { webUrl ->
                        if (accountInfo.supportsPasswordLogin) Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
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

                    accountInfo?.registerWebsite?.takeIf { it.isNotBlank() }?.let { regUrl ->
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(regUrl)))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("去注册账号", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        VeneraTextButton(text = "取消", onClick = { loginTargetSourceKey = null }, enabled = !isLoggingIn)
                    }
                }
            }
        }
    }

    // 4. 手填 Cookie 登录弹窗
    cookieLoginTargetKey?.let { sourceKey ->
        val bundle = configBundles[sourceKey]
        val fields = bundle?.accountInfo?.cookieFields.orEmpty()
        val values = remember(sourceKey) { mutableStateListOf(*Array(fields.size) { "" }) }
        var submitting by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { if (!submitting) cookieLoginTargetKey = null }) {
            VeneraCard(modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space8)) {
                Column(modifier = Modifier.padding(tokens.spacing.space9)) {
                    Text(
                        text = "Cookie 登录 - ${bundle?.sourceName ?: ""}",
                        fontSize = tokens.type.itemTitle,
                        fontWeight = tokens.type.weightBold,
                        color = tokens.color.textPrimary
                    )
                    Spacer(modifier = Modifier.height(tokens.spacing.space6))

                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        itemsIndexed(fields) { idx, fieldName ->
                            VeneraTextField(
                                value = values.getOrElse(idx) { "" },
                                onValueChange = { if (idx < values.size) values[idx] = it },
                                label = fieldName,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        VeneraTextButton(text = "取消", onClick = { cookieLoginTargetKey = null }, enabled = !submitting)
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
                                        else "登录没成功 🥲 检查一下填的字段是否正确",
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

    // 5. 内嵌 WebView 网页登录
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

    // 6. 注销确认 Dialog
    logoutConfirmKey?.let { sourceKey ->
        VeneraDialog(
            show = true,
            onDismissRequest = { logoutConfirmKey = null },
            title = "确认注销",
            content = { Text("确定要退出该漫画源的账号登录状态吗？已保存的凭证将被清除。") },
            confirmText = "注销",
            confirmDestructive = true,
            onConfirm = {
                viewModel.logout(sourceKey)
                logoutConfirmKey = null
            },
            dismissText = "取消",
            onDismiss = { logoutConfirmKey = null },
        )
    }

    // 7. 删除源确认 Dialog
    deleteConfirmRow?.let { row ->
        VeneraDialog(
            show = true,
            onDismissRequest = { deleteConfirmRow = null },
            title = "确认删除",
            content = { Text("确认删除「${row.name}」漫画源吗？删除后相关本地缓存与配置将被清理。") },
            confirmText = "删除",
            confirmDestructive = true,
            onConfirm = {
                viewModel.deleteSource(row.key, row.fileName)
                deleteConfirmRow = null
            },
            dismissText = "取消",
            onDismiss = { deleteConfirmRow = null },
        )
    }

    // 8. 官方源仓库清单 Dialog
    if (showRepoDialog) {
        Dialog(onDismissRequest = { showRepoDialog = false }) {
            VeneraCard(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
                Column(modifier = Modifier.padding(tokens.spacing.space6)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "官方源仓库清单 (${repoItems.size}条)",
                            fontSize = tokens.type.itemTitle,
                            fontWeight = tokens.type.weightBold,
                            color = tokens.color.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        VeneraIconButton(onClick = { showRepoDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    VeneraTextField(
                        value = repoUrlInput,
                        onValueChange = { repoUrlInput = it },
                        label = "Repo URL",
                        placeholder = "https://.../index.json",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "地址应指向仓库的 index.json；留空则回退官方默认地址",
                        fontSize = tokens.type.badge,
                        color = tokens.color.textTertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.setRepoUrl(repoUrlInput.trim())
                                viewModel.loadRepo(repoUrlInput.trim().ifEmpty { null })
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("应用并拉取", fontSize = tokens.type.caption)
                        }
                        VeneraTextButton(
                            text = "恢复默认",
                            onClick = {
                                repoUrlInput = ""
                                viewModel.setRepoUrl("")
                                viewModel.loadRepo(null)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

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
                                val installedRow = sourceRows.find { it.fileName == item.fileName }
                                val isInstalling = installingFileNames.contains(item.fileName)
                                val hasUpdate = installedRow != null &&
                                        ComicSourceManager.compareVersion(item.version, installedRow.version) > 0

                                Surface(
                                    shape = RoundedCornerShape(tokens.shape.medium),
                                    color = tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha)
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
                                                    fontSize = tokens.type.body,
                                                    fontWeight = tokens.type.weightBold,
                                                    color = tokens.color.textPrimary
                                                )
                                                Spacer(modifier = Modifier.width(tokens.spacing.badgeHorizontalPadding))
                                                Text(
                                                    text = "v${item.version}",
                                                    fontSize = tokens.type.overline,
                                                    color = tokens.color.textTertiary
                                                )
                                            }
                                            if (!item.description.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = item.description,
                                                    fontSize = tokens.type.overline,
                                                    color = tokens.color.textTertiary,
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
                                                colors = ButtonDefaults.buttonColors(color = StatusColors.Degraded)
                                            ) {
                                                Text("更新", fontSize = tokens.type.overline, color = StatusColors.OnBadgeSurface)
                                            }
                                            installedRow != null -> Surface(
                                                shape = RoundedCornerShape(tokens.shape.extraSmall),
                                                color = StatusColors.Healthy.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "已安装 v${installedRow.version}",
                                                    fontSize = tokens.type.overline,
                                                    color = StatusColors.Healthy,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                            else -> Button(
                                                onClick = { viewModel.installFromRepo(item) },
                                                colors = ButtonDefaults.buttonColors(color = tokens.color.primary)
                                            ) {
                                                Text("安装", fontSize = tokens.type.overline, color = tokens.color.onPrimary)
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
            VeneraCard(modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space8)) {
                Column(modifier = Modifier.padding(tokens.spacing.space9)) {
                    Text(text = "通过 JS 规则网络链接安装", fontSize = tokens.type.itemTitle, fontWeight = tokens.type.weightBold, color = tokens.color.textPrimary)
                    Spacer(modifier = Modifier.height(tokens.spacing.space7))
                    VeneraTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = "https://.../source.js",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        VeneraTextButton(text = "取消", onClick = { showUrlDialog = false })
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

    // 10. 检查更新结果弹窗
    updateCandidates?.takeIf { it.isNotEmpty() }?.let { candidates ->
        val progress = batchUpdateProgress
        VeneraDialog(
            show = true,
            onDismissRequest = { if (progress == null) viewModel.dismissUpdateCandidates() },
            title = "发现 ${candidates.size} 个可更新源",
            content = {
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
            confirmText = "全部更新",
            onConfirm = { viewModel.updateAllAvailable() },
            dismissText = "稍后",
            onDismiss = { viewModel.dismissUpdateCandidates() },
            buttonsEnabled = progress == null,
        )
    }

    // 11. 源脚本编辑页
    editTarget?.let { (fileName, sourceName) ->
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

/**
 * 列表项渲染（折叠/手风琴展开两态）
 */
@Composable
private fun RenderSourceItem(
    row: SourceRow,
    activeKey: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    latency: Long?,
    isUnreachable: Boolean,
    configBundle: SourceConfigBundle?,
    onSetEnabled: (Boolean) -> Unit,
    onSetActive: () -> Unit,
    onSetPinned: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onUpdateSource: () -> Unit,
    onTestSource: () -> Unit,
    onEditScript: () -> Unit,
    onDelete: () -> Unit,
    onOpenInputSetting: (SourceSettingItem.Input, String) -> Unit,
    onOpenSelectSetting: (SourceSettingItem.Select) -> Unit,
    onUpdateSetting: (String, Any) -> Unit,
    onExecuteCallback: (String) -> Unit,
    onLogin: () -> Unit,
    onWebLogin: (String) -> Unit,
    onCookieLogin: () -> Unit,
    onLogout: () -> Unit
) {
    val tokens = VeneraTokens

    val railColor = when {
        !row.enabled -> tokens.color.outline.copy(alpha = 0.25f)
        isUnreachable -> StatusColors.Failing
        row.hasUpdate -> StatusColors.Degraded
        else -> tokens.color.primary
    }

    VeneraCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            // 头部 Row (可点击展开折叠)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 3px 彩色状态条
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(railColor)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = row.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (row.enabled) tokens.color.textPrimary else tokens.color.textDisabled,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (row.key == activeKey) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = StatusColors.BadgeSurface
                            ) {
                                Text(
                                    text = "默认",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusColors.OnBadgeSurface,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "v${row.version}",
                            fontSize = 11.sp,
                            color = tokens.color.textTertiary
                        )

                        if (row.latestVersion != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "新版 ${row.latestVersion}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusColors.Degraded
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        when {
                            row.testing -> {
                                Text(text = "测速中...", fontSize = 11.sp, color = tokens.color.textTertiary)
                            }
                            !row.enabled -> {
                                Text(text = "已停用", fontSize = 11.sp, color = tokens.color.textDisabled)
                            }
                            isUnreachable -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(StatusColors.Failing))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "连不上", fontSize = 11.sp, color = StatusColors.Failing)
                                }
                            }
                            latency != null -> {
                                val dotColor = when {
                                    latency <= 800 -> StatusColors.Healthy
                                    latency <= 3000 -> StatusColors.Degraded
                                    else -> StatusColors.Failing
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(dotColor))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "${latency}ms", fontSize = 11.sp, color = dotColor)
                                }
                            }
                            else -> {
                                Text(text = if (row.isJs) "扩展源" else "原生", fontSize = 11.sp, color = tokens.color.textTertiary)
                            }
                        }
                    }
                }

                // 开关（阻止点击行展开）
                VeneraSwitch(
                    checked = row.enabled,
                    onCheckedChange = { onSetEnabled(it) }
                )

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ChevronRight,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = tokens.color.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // 手风琴展开第二层
            if (isExpanded) {
                HorizontalDivider(
                    color = tokens.color.outline.copy(alpha = 0.12f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                // 1. 源设置
                val settings = configBundle?.settings.orEmpty()
                if (settings.isNotEmpty()) {
                    Text(
                        text = "源设置",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.color.textSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )

                    settings.forEach { setting ->
                        when (setting) {
                            is SourceSettingItem.Switch -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = setting.title,
                                        fontSize = 13.sp,
                                        color = tokens.color.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    VeneraSwitch(
                                        checked = setting.value,
                                        onCheckedChange = { onUpdateSetting(setting.key, !setting.value) }
                                    )
                                }
                            }
                            is SourceSettingItem.Input -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpenInputSetting(setting, setting.value) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = setting.title,
                                        fontSize = 13.sp,
                                        color = tokens.color.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = setting.value.ifBlank { "点击设置" },
                                        fontSize = 12.sp,
                                        color = tokens.color.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = tokens.color.textTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            is SourceSettingItem.Select -> {
                                val currentText = setting.options.find { it.value == setting.value }?.text ?: setting.value
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpenSelectSetting(setting) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = setting.title,
                                        fontSize = 13.sp,
                                        color = tokens.color.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = currentText,
                                        fontSize = 12.sp,
                                        color = tokens.color.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = tokens.color.textTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            is SourceSettingItem.Callback -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = setting.title,
                                        fontSize = 13.sp,
                                        color = tokens.color.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(onClick = { onExecuteCallback(setting.key) }) {
                                        Text(setting.buttonText, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. 账号体系
                val accountInfo = configBundle?.accountInfo
                if (accountInfo?.hasAccount == true) {
                    Text(
                        text = "账号",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.color.textSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )

                    if (accountInfo.isLogged) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "已登录", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tokens.color.textPrimary)
                                Text(
                                    text = "${accountInfo.username ?: "已登录"} · 凭证有效",
                                    fontSize = 11.sp,
                                    color = StatusColors.Healthy
                                )
                            }
                            Text(
                                text = "重新登录",
                                fontSize = 12.sp,
                                color = tokens.color.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        if (accountInfo.supportsWebLogin) {
                                            onWebLogin(accountInfo.loginWebsite ?: "")
                                        } else {
                                            onLogin()
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onLogout() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "退出登录",
                                fontSize = 13.sp,
                                color = StatusColors.Failing
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "未登录", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tokens.color.textPrimary)
                                Text(text = "登录后可访问收藏与源专属资源", fontSize = 11.sp, color = tokens.color.textSecondary)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (accountInfo.supportsWebLogin) {
                                    OutlinedButton(onClick = { onWebLogin(accountInfo.loginWebsite ?: "") }) {
                                        Text("网页登录", fontSize = 11.sp)
                                    }
                                } else if (accountInfo.supportsPasswordLogin) {
                                    Button(onClick = onLogin) {
                                        Text("登录", fontSize = 11.sp)
                                    }
                                } else if (accountInfo.supportsCookieLogin) {
                                    OutlinedButton(onClick = onCookieLogin) {
                                        Text("填Cookie", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. 维护操作
                Text(
                    text = "维护",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tokens.color.textSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )

                // 设为默认源
                if (row.key != activeKey) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSetActive() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "设为默认源", fontSize = 13.sp, color = tokens.color.textPrimary)
                            Text(text = "聚合搜索和详情首选", fontSize = 11.sp, color = tokens.color.textSecondary)
                        }
                        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.color.textTertiary)
                    }
                }

                // 置顶此源 / 取消置顶
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSetPinned(!row.pinned) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (row.pinned) "取消置顶" else "置顶此源",
                            fontSize = 13.sp,
                            color = tokens.color.textPrimary
                        )
                        Text(
                            text = if (row.pinned) "当前已置顶在最上方" else "将常用源排在最前面",
                            fontSize = 11.sp,
                            color = tokens.color.textSecondary
                        )
                    }
                    Icon(
                        imageVector = if (row.pinned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                        tint = if (row.pinned) StatusColors.Degraded else tokens.color.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 检查更新 / 立即更新
                if (row.hasUpdate) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "更新到 ${row.latestVersion}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StatusColors.Degraded)
                            Text(text = "当前版本 v${row.version}", fontSize = 11.sp, color = tokens.color.textSecondary)
                        }
                        Button(onClick = onUpdateSource) {
                            Text("立即更新", fontSize = 11.sp)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onCheckUpdates() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "检查更新", fontSize = 13.sp, color = tokens.color.textPrimary)
                            Text(text = "当前已是最新版本 v${row.version}", fontSize = 11.sp, color = tokens.color.textSecondary)
                        }
                        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.color.textTertiary)
                    }
                }

                // 测试可用性
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTestSource() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "测试网络连通性", fontSize = 13.sp, color = tokens.color.textPrimary)
                        Text(text = "实测向源发起一次连接测试", fontSize = 11.sp, color = tokens.color.textSecondary)
                    }
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.color.textTertiary)
                }

                // 编辑源脚本
                if (row.isJs && row.fileName != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onEditScript() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "编辑源脚本", fontSize = 13.sp, color = tokens.color.textPrimary)
                            Text(text = "查看或在线修改 JS 规则", fontSize = 11.sp, color = tokens.color.textSecondary)
                        }
                        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.color.textTertiary)
                    }
                }

                // 删除此源
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDelete() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "删除此源", fontSize = 13.sp, color = StatusColors.Failing)
                        Text(text = "收藏与阅读记录会保留", fontSize = 11.sp, color = tokens.color.textSecondary)
                    }
                }
            }
        }
    }
}
