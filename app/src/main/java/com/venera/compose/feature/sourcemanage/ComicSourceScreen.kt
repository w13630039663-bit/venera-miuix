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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
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
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.venera.compose.components.VeneraEmptyView
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraDialog
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.components.venera.VeneraSwitch
import com.venera.compose.components.venera.VeneraTextButton
import com.venera.compose.components.venera.VeneraTextField
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.VeneraIconButton
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens

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

    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topBarBehavior.nestedScrollConnection)
                .blurBackdropSource(topBarBackdrop),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = statusBarTop + 104.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (sourceRows.isEmpty()) {
                item(key = "src-empty") {
                    VeneraEmptyView(
                        title = "还没有漫画源",
                        message = "点击上方「一键安装 / 更新全部官方源」或「本地导入」添加你的第一个源",
                    )
                }
            }

            // 画廊的两张配置卡（Gelbooru 账号 / SauceNAO Key）2026-09-29 搬去
            // 「设置 → 画廊」了。从前它们寄在这一页，理由是"整仓只有这一页是各站点的配置"——
            // 那页的主题其实是 **JS 源脚本**，而这两站是原生客户端，压根不在这份源清单里；
            // 现在画廊有了自己的分区，寄居的理由就不成立了。

            // ==================== 1. 顶部操作概览卡片 ====================
            item {
                VeneraCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(tokens.spacing.space7)) {
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
                                    fontSize = tokens.type.itemTitle,
                                    fontWeight = tokens.type.weightBold,
                                    color = tokens.color.textPrimary
                                )
                                Text(
                                    text = "当前默认: $activeKey",
                                    fontSize = tokens.type.overline,
                                    color = tokens.color.textTertiary
                                )
                            }
                            VeneraIconButton(onClick = { viewModel.refreshPings() }) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "网络测速",
                                    tint = tokens.color.primary
                                )
                            }
                        }

                        // 网络受限提示：熔断中的域名说明当前网络直连不可达，需要代理
                        if (unreachableHosts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(tokens.shape.small),
                                color = StatusColors.Degraded.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "${unreachableHosts.size} 个站点连不上，已经跳过这些源",
                                        fontSize = tokens.type.overline,
                                        color = StatusColors.Degraded,
                                        fontWeight = tokens.type.weightSemibold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "要用这些源的话，在设置 → 网络 里配代理",
                                        fontSize = tokens.type.badge,
                                        color = tokens.color.textTertiary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = tokens.color.divider)
                        Spacer(modifier = Modifier.height(10.dp))

                        // 快捷操作按钮组
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(tokens.shape.small),
                                color = tokens.color.primaryContainer.copy(alpha = 0.35f),
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
                                        tint = tokens.color.primary,
                                        modifier = Modifier.size(tokens.spacing.chipIconSize)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "官方清单",
                                        fontSize = tokens.type.overline,
                                        fontWeight = tokens.type.weightMedium,
                                        color = tokens.color.primary
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(tokens.shape.small),
                                color = tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
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
                                        tint = tokens.color.textSecondary,
                                        modifier = Modifier.size(tokens.spacing.chipIconSize)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "链接安装",
                                        fontSize = tokens.type.overline,
                                        fontWeight = tokens.type.weightMedium,
                                        color = tokens.color.textSecondary
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(tokens.shape.small),
                                color = tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
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
                                        tint = tokens.color.textSecondary,
                                        modifier = Modifier.size(tokens.spacing.chipIconSize)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "本地导入",
                                        fontSize = tokens.type.overline,
                                        fontWeight = tokens.type.weightMedium,
                                        color = tokens.color.textSecondary
                                    )
                                }
                            }

                            // 检查更新（对齐官方 _CheckUpdatesButton）
                            Surface(
                                shape = RoundedCornerShape(tokens.shape.small),
                                color = tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha),
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
                                            modifier = Modifier.size(tokens.spacing.chipIconSize),
                                            strokeWidth = 2.dp,
                                            color = tokens.color.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Update,
                                            contentDescription = null,
                                            tint = tokens.color.textSecondary,
                                            modifier = Modifier.size(tokens.spacing.chipIconSize)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "检查更新",
                                        fontSize = tokens.type.overline,
                                        fontWeight = tokens.type.weightMedium,
                                        color = tokens.color.textSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 一键同步全部 33 个官方源（已装且版本一致会自动跳过）
                        val total = syncProgress?.second ?: repoItems.size
                        Surface(
                            shape = RoundedCornerShape(tokens.shape.small),
                            color = if (syncProgress != null) tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha)
                            else tokens.color.primary,
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
                                        modifier = Modifier.size(tokens.spacing.chipIconSize),
                                        strokeWidth = 2.dp,
                                        color = tokens.color.primary
                                    )
                                    Spacer(modifier = Modifier.width(tokens.spacing.space4))
                                    Text(
                                        text = "正在同步 ${syncProgress.first}/$total ...",
                                        fontSize = tokens.type.caption,
                                        color = tokens.color.textSecondary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudDownload,
                                        contentDescription = null,
                                        tint = StatusColors.OnBadgeSurface,
                                        modifier = Modifier.size(tokens.spacing.chipIconSize)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "一键安装 / 更新全部官方源",
                                        fontSize = tokens.type.caption,
                                        fontWeight = tokens.type.weightBold,
                                        color = StatusColors.OnBadgeSurface
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

                VeneraCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = tokens.spacing.space2)) {
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
                                        tint = StatusColors.Degraded,
                                        modifier = Modifier.size(tokens.spacing.chipIconSize)
                                    )
                                    Spacer(modifier = Modifier.width(tokens.spacing.space1))
                                }
                                Text(
                                    text = row.name,
                                    fontSize = tokens.type.itemTitle,
                                    fontWeight = tokens.type.weightBold,
                                    color = if (row.enabled) tokens.color.textPrimary
                                    else tokens.color.textDisabled
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // 版本胶囊
                                Surface(
                                    shape = RoundedCornerShape(tokens.shape.small),
                                    color = StatusColors.BadgeSurface
                                ) {
                                    Text(
                                        text = row.version,
                                        fontSize = tokens.type.caption,
                                        color = StatusColors.OnBadgeSurface,
                                        modifier = Modifier.padding(horizontal = tokens.spacing.chipHorizontalPadding, vertical = tokens.spacing.badgeVerticalPadding)
                                    )
                                }
                                // 有新版本（对应官方 availableUpdates 命中的 "New Version" 胶囊）
                                row.latestVersion?.let { newVersion ->
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(tokens.shape.small),
                                        color = tokens.color.primaryContainer.copy(alpha = 0.45f)
                                    ) {
                                        Text(
                                            text = "New v$newVersion",
                                            fontSize = tokens.type.overline,
                                            fontWeight = tokens.type.weightBold,
                                            color = tokens.color.primary,
                                            modifier = Modifier.padding(horizontal = tokens.spacing.badgeHorizontalPadding, vertical = tokens.spacing.badgeVerticalPadding)
                                        )
                                    }
                                }
                                // 连通性指示
                                if (latency != null) {
                                    Spacer(modifier = Modifier.width(tokens.spacing.space2))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // 测速延迟色阶圆点 + 文字：Healthy/Degraded/Failing/Unknown 全走语义色板
                                        Box(
                                            modifier = Modifier
                                                .size(tokens.spacing.statusDotSize)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        latency < 0 -> StatusColors.Failing
                                                        latency <= 800 -> StatusColors.Healthy
                                                        latency <= 3000 -> StatusColors.Degraded
                                                        else -> StatusColors.Failing
                                                    }
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(tokens.spacing.space1))
                                        Text(
                                            text = if (latency >= 0) "${latency}ms" else "不可达",
                                            fontSize = tokens.type.overline,
                                            color = when {
                                                latency < 0 -> StatusColors.Failing
                                                latency <= 800 -> StatusColors.Healthy
                                                latency <= 3000 -> StatusColors.Degraded
                                                else -> StatusColors.Failing
                                            }
                                        )
                                    }
                                }
                            }

                            // 右侧操作区
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // 可用性测试（真实发一次请求）
                                VeneraIconButton(
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

                                // 启用 / 禁用开关：尺寸交给转发件的两家后端各自决定。
                                // 这里原本钉着 44×28dp（M3 开关的压缩版），而 miuix 的轨道是 49×28dp，
                                // 于是 Miuix 档下整颗被压扁 —— 观感尺寸用现成口径，不自己造数。
                                VeneraSwitch(
                                    checked = row.enabled,
                                    onCheckedChange = { checked ->
                                        row.fileName?.let { viewModel.setEnabled(it, checked) }
                                    },
                                    modifier = Modifier.padding(horizontal = 2.dp)
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
                                    shape = RoundedCornerShape(tokens.shape.extraSmall),
                                    color = tokens.color.primaryContainer.copy(alpha = 0.3f)
                                ) {
                                    Text(
                                        text = "活跃",
                                        fontSize = tokens.type.badge,
                                        color = tokens.color.primary,
                                        fontWeight = tokens.type.weightBold,
                                        modifier = Modifier.padding(horizontal = tokens.spacing.badgeHorizontalPadding, vertical = tokens.spacing.badgeVerticalPadding)
                                    )
                                }
                            } else {
                                Text(
                                    text = "设为活跃",
                                    fontSize = tokens.type.overline,
                                    color = tokens.color.textTertiary,
                                    modifier = Modifier
                                        .clickable { viewModel.setActiveSource(row.key) }
                                        .padding(vertical = 4.dp, horizontal = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // 置顶
                            VeneraIconButton(
                                onClick = { row.fileName?.let { viewModel.setPinned(it, !row.pinned) } },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = if (row.pinned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "置顶",
                                    tint = if (row.pinned) StatusColors.Degraded else tokens.color.textDisabled,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // 上移 / 下移
                            VeneraIconButton(
                                onClick = { row.fileName?.let { viewModel.moveSource(true, it) } },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "上移",
                                    tint = tokens.color.textDisabled,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            VeneraIconButton(
                                onClick = { row.fileName?.let { viewModel.moveSource(false, it) } },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "下移",
                                    tint = tokens.color.textDisabled,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // 重载配置
                            VeneraIconButton(
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
                                VeneraIconButton(
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
                                VeneraIconButton(
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
                                            tint = if (row.hasUpdate) StatusColors.Degraded else tokens.color.textDisabled,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // 卸载 / 删除漫画源（支持全部内置源与 JS 扩展源）
                            VeneraIconButton(
                                onClick = { deleteConfirmRow = row },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "删除源",
                                    tint = StatusColors.Failing,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // 分割线
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = tokens.color.divider)

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
                                                    fontSize = tokens.type.body,
                                                    fontWeight = tokens.type.weightMedium,
                                                    color = tokens.color.textPrimary
                                                )
                                                Spacer(modifier = Modifier.height(tokens.spacing.space1))
                                                Text(
                                                    text = item.value.ifBlank { item.defaultValue.ifBlank { "未设置" } },
                                                    fontSize = tokens.type.caption,
                                                    color = tokens.color.textTertiary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            VeneraIconButton(
                                                onClick = {
                                                    editingInputSetting = Triple(row.key, item, item.value)
                                                },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Edit,
                                                    contentDescription = "编辑",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = tokens.color.textDisabled
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
                                                fontSize = tokens.type.body,
                                                fontWeight = tokens.type.weightMedium,
                                                color = tokens.color.textPrimary
                                            )

                                            Surface(
                                                shape = RoundedCornerShape(tokens.shape.small),
                                                color = StatusColors.BadgeSurface,
                                                modifier = Modifier.clickable {
                                                    editingSelectSetting = Pair(row.key, item)
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = tokens.spacing.chipHorizontalPadding, vertical = tokens.spacing.chipVerticalPadding),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = displayText,
                                                        fontSize = tokens.type.sectionTitle,
                                                        color = StatusColors.OnBadgeSurface,
                                                        fontWeight = tokens.type.weightMedium
                                                    )
                                                    Spacer(modifier = Modifier.width(tokens.spacing.badgeHorizontalPadding))
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowDropDown,
                                                        contentDescription = null,
                                                        tint = StatusColors.OnBadgeSurface,
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
                                            VeneraSwitch(
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
                                                fontSize = tokens.type.body,
                                                fontWeight = tokens.type.weightMedium,
                                                color = tokens.color.textPrimary
                                            )
                                            Button(
                                                onClick = { viewModel.executeCallback(row.key, item.key) },
                                                colors = ButtonDefaults.buttonColors(color = tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha))
                                            ) {
                                                Text(
                                                    text = item.buttonText,
                                                    fontSize = tokens.type.caption,
                                                    color = tokens.color.textPrimary
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
                                        fontSize = tokens.type.body,
                                        fontWeight = tokens.type.weightMedium,
                                        color = tokens.color.primary
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "登录",
                                        tint = tokens.color.textDisabled
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
                                        Text(text = title, fontSize = tokens.type.body, fontWeight = tokens.type.weightMedium, color = tokens.color.textPrimary)
                                        Text(text = data, fontSize = tokens.type.sectionTitle, color = tokens.color.textTertiary)
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
                                        Text(text = "重新登录", fontSize = tokens.type.body, fontWeight = tokens.type.weightMedium, color = tokens.color.textPrimary)
                                        Text(text = "点击此处如果登录已过期", fontSize = tokens.type.overline, color = tokens.color.textTertiary)
                                    }
                                    VeneraIconButton(
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
                                    Text(text = "注销", fontSize = tokens.type.body, fontWeight = tokens.type.weightMedium, color = StatusColors.Failing)
                                    VeneraIconButton(
                                        onClick = { logoutConfirmKey = row.key },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                            contentDescription = "注销",
                                            tint = StatusColors.Failing,
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

        com.venera.compose.components.venera.VeneraTopAppBar(
            title = "漫画源",
            largeTitle = "漫画源",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            navigationIcon = {
                VeneraTopBarPill(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary,
                    )
                }
            }
        )
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

    // 3. 登录弹窗（对齐官方 _LoginPage：只呈现该源真正声明的登录方式，
    //    避免给「仅支持网页登录」的源展示无意义的账密表单）
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

                    // ---- 账号密码登录（官方 account.login(account, pwd)）----
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

                    // ---- 内嵌 WebView 网页登录（官方 account.loginWithWebview）----
                    accountInfo?.loginWebsite?.takeIf { it.isNotBlank() }?.let { webUrl ->
                        if (accountInfo.supportsPasswordLogin) Spacer(modifier = Modifier.height(8.dp))
                        // 登记在册的外观缺口：miuix 的 ButtonColors 没有描边位，"描边按钮"换后端会连描边一起丢，
                        // 所以这两处（本文件的 OutlinedButton 与下面带图标的 TextButton）保持 M3 直连。
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
                        VeneraTextButton(
                            text = "手动填写 Cookie",
                            onClick = {
                                cookieLoginTargetKey = sourceKey
                                loginTargetSourceKey = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
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
            VeneraCard(modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space8)) {
                Column(modifier = Modifier.padding(tokens.spacing.space9)) {
                    Text(text = "填写 Cookie 登录", fontSize = tokens.type.itemTitle, fontWeight = tokens.type.weightBold, color = tokens.color.textPrimary)
                    Spacer(modifier = Modifier.height(tokens.spacing.space2))
                    Text(
                        text = "请从浏览器开发者工具中复制对应的 Cookie 值",
                        fontSize = tokens.type.overline,
                        color = tokens.color.textTertiary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        itemsIndexed(fields) { index, name ->
                            VeneraTextField(
                                value = values.getOrElse(index) { "" },
                                onValueChange = { if (index < values.size) values[index] = it },
                                label = name,
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

    // 5. 删除源确认 Dialog
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

    // 8. 官方清单弹窗 (33源)
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

                    // Repo URL：可编辑 + Refresh（对齐官方仓库清单顶部卡片，
                    // 上游这里就是一个输入框而不是只读说明文字）
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
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VeneraTextButton(text = "恢复默认", onClick = { repoUrlInput = viewModel.defaultRepoUrl })
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

    // 10. 检查更新结果弹窗（对齐官方 showUpdateDialog：列出 name: version 并可一键全更）
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
