package com.venera.compose.feature

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.data.network.VeneraNetworkClient
import com.venera.compose.data.prefs.ThemeMode
import com.venera.compose.data.prefs.VeneraPreferences
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 生产级设置中心 (S7)
 *
 * 核心特性：
 * 1. 漫画源扩展管理直达
 * 2. 网络与代理 (HTTP/SOCKS5 + 熔断重置)
 * 3. 内容屏蔽与分级守卫直达
 * 4. 阅读器完整排版与交互偏好
 * 5. 外观与暗黑模式切换
 * 6. 本地书架与离线下载中心直达
 * 7. WebDAV 云端同步与备份直达
 * 8. 阅读足迹与统计直达
 * 9. 单页插图收藏直达
 * 10. 运行日志与诊断直达
 */
@Composable
fun AndroidSettingsScreen(
    onNavigateToSourceManage: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToLocalComics: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onNavigateToFavoriteImages: () -> Unit = {},
    onNavigateToGuard: () -> Unit = {},
    onNavigateToSync: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { VeneraPreferences.getInstance(context) }
    val proxyType by prefs.proxyType.collectAsState()
    val proxyHost by prefs.proxyHost.collectAsState()
    val proxyPort by prefs.proxyPort.collectAsState()

    var hostInput by remember(proxyHost) { mutableStateOf(proxyHost.ifBlank { "127.0.0.1" }) }
    var portInput by remember(proxyPort) { mutableStateOf(proxyPort.toString()) }

    var showReaderSettingsDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLocalManageDialog by remember { mutableStateOf(false) }

    data class SettingEntry(
        val title: String,
        val subtitle: String,
        val icon: ImageVector,
        val badgeColor: Color,
        val onClick: () -> Unit
    )

    val settingEntries = listOf(
        SettingEntry(
            title = "离线与本地漫画",
            subtitle = "管理下载队列、已完成离线章节与本地书架",
            icon = Icons.Filled.CloudDownload,
            badgeColor = Color(0xFF009688),
            onClick = { showLocalManageDialog = true }
        ),
        SettingEntry(
            title = "云同步与备份恢复",
            subtitle = "WebDAV 双向云同步、本地 .venera 备份打包与恢复",
            icon = Icons.Filled.CloudSync,
            badgeColor = Color(0xFFFF9800),
            onClick = onNavigateToSync
        ),
        SettingEntry(
            title = "阅读足迹与统计",
            subtitle = "阅读时长、累计页数、14 天趋势图表与偏好题材",
            icon = Icons.Filled.BarChart,
            badgeColor = Color(0xFFE91E63),
            onClick = onNavigateToStats
        ),
        SettingEntry(
            title = "单页与插图收藏",
            subtitle = "浏览在阅读器中收藏的精彩画面与插图",
            icon = Icons.Filled.Collections,
            badgeColor = Color(0xFF673AB7),
            onClick = onNavigateToFavoriteImages
        ),
        SettingEntry(
            title = "内容屏蔽与分级守卫",
            subtitle = "配置关键词/标签/作者黑名单与 R18 分级遮罩",
            icon = Icons.Filled.FilterAlt,
            badgeColor = Color(0xFFE53935),
            onClick = onNavigateToGuard
        ),
        SettingEntry(
            title = "阅读器体验配置",
            subtitle = "默认排版、页间距、常亮、音量键翻页与切边",
            icon = Icons.Filled.Book,
            badgeColor = Color(0xFF4CAF50),
            onClick = { showReaderSettingsDialog = true }
        ),
        SettingEntry(
            title = "外观与暗黑模式",
            subtitle = "跟随系统、亮色模式、暗黑模式切换",
            icon = Icons.Filled.ColorLens,
            badgeColor = Color(0xFF9C27B0),
            onClick = { showThemeDialog = true }
        ),
        SettingEntry(
            title = "运行日志与排错诊断",
            subtitle = "实时捕获网络诊断、崩溃与规则源报错日志",
            icon = Icons.Filled.Terminal,
            badgeColor = Color(0xFF607D8B),
            onClick = onNavigateToLogs
        )
    )

    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部 About 区块
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MiuixTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "V", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "Venera Compose", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "版本 2.0.0 · 原生全功能对齐版", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)) {
                            Text(text = "已就绪 S6/S7", fontSize = 11.sp, color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                            Text(text = "纯原生架构", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }

        // 重点入口：漫画源扩展管理
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToSourceManage() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF673AB7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Extension,
                            contentDescription = "漫画源扩展管理",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "漫画源扩展管理", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "管理 33 条官方/本地规则源、测速与更新",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Text(text = "›", fontSize = 20.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
        }

        // 网络与代理配置
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF009688)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = "网络与代理",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "网络与代理", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "为需要外网的源配置 HTTP / SOCKS5 代理",
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 代理类型切换
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("NONE" to "关闭", "HTTP" to "HTTP", "SOCKS" to "SOCKS5").forEach { (value, label) ->
                            val selected = proxyType == value
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        prefs.setProxy(value, hostInput.trim(), portInput.toIntOrNull() ?: 7890)
                                        VeneraNetworkClient.getInstance(context).rebuildClient()
                                        Toast.makeText(context, "代理已设为 $label", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) Color.White else MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    if (proxyType != "NONE") {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = hostInput,
                            onValueChange = { hostInput = it },
                            label = { Text("代理主机") },
                            placeholder = { Text("127.0.0.1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it.filter { c -> c.isDigit() }.take(5) },
                            label = { Text("端口") },
                            placeholder = { Text("7890") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val port = portInput.toIntOrNull()
                                    if (hostInput.isBlank() || port == null || port !in 1..65535) {
                                        Toast.makeText(context, "请填写有效的代理主机与端口", Toast.LENGTH_SHORT).show()
                                    } else {
                                        prefs.setProxy(proxyType, hostInput.trim(), port)
                                        VeneraNetworkClient.getInstance(context).rebuildClient()
                                        Toast.makeText(context, "代理配置已生效", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("保存并生效")
                            }
                            TextButton(
                                onClick = {
                                    com.venera.compose.data.network.HostCircuitBreaker.resetAll()
                                    Toast.makeText(context, "已清除网络熔断记录", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("重置熔断", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // 功能分类设置入口列表
        items(settingEntries) { entry ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { entry.onClick() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(entry.badgeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = entry.icon, contentDescription = entry.title, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = entry.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = entry.subtitle, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Text(text = "›", fontSize = 20.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
        }
    }

    // 本地与下载管理子弹窗
    if (showLocalManageDialog) {
        AlertDialog(
            onDismissRequest = { showLocalManageDialog = false },
            title = { Text("本地与下载管理", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showLocalManageDialog = false
                                onNavigateToDownloads()
                            }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = MiuixTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("离线下载中心", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("查看正在下载中的任务、下载速度与已完成队列", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showLocalManageDialog = false
                                onNavigateToLocalComics()
                            }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Folder, contentDescription = null, tint = Color(0xFFFF9800))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("本地离线书架", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("离线秒开阅读已下载漫画，支持导入/导出 CBZ", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLocalManageDialog = false }) { Text("关闭") }
            }
        )
    }

    // 阅读器体验配置弹窗
    if (showReaderSettingsDialog) {
        val readingMode by prefs.defaultReadingMode.collectAsState()
        val keepScreenOn by prefs.keepScreenOn.collectAsState()
        val volumeKeyTurn by prefs.volumeKeyTurn.collectAsState()
        val autoCrop by prefs.autoCropBorders.collectAsState()
        val clickToTurn by prefs.clickToTurn.collectAsState()
        val nightFilter by prefs.nightFilter.collectAsState()

        AlertDialog(
            onDismissRequest = { showReaderSettingsDialog = false },
            title = { Text("阅读器体验设置", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("默认排版模式:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("VERTICAL" to "连续纵向", "HORIZONTAL_LTR" to "从左到右", "HORIZONTAL_RTL" to "从右到左 (日漫)").forEach { (mode, label) ->
                            val selected = readingMode == mode
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { prefs.setDefaultReadingMode(mode) }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        color = if (selected) Color.White else MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("保持屏幕常亮", fontSize = 13.sp)
                        Switch(checked = keepScreenOn, onCheckedChange = { prefs.setKeepScreenOn(it) })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("音量键翻页", fontSize = 13.sp)
                        Switch(checked = volumeKeyTurn, onCheckedChange = { prefs.setVolumeKeyTurn(it) })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("点击屏幕翻页", fontSize = 13.sp)
                        Switch(checked = clickToTurn, onCheckedChange = { prefs.setClickToTurn(it) })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("夜间柔光滤镜", fontSize = 13.sp)
                        Switch(checked = nightFilter, onCheckedChange = { prefs.setNightFilter(it) })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReaderSettingsDialog = false }) { Text("完成") }
            }
        )
    }

    // 外观与主题弹窗
    if (showThemeDialog) {
        val currentTheme by prefs.themeMode.collectAsState()

        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("外观与主题模式", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ThemeMode.SYSTEM to "跟随系统",
                        ThemeMode.LIGHT to "浅色明亮",
                        ThemeMode.DARK to "深色暗黑"
                    ).forEach { (mode, label) ->
                        val selected = currentTheme == mode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                        ) {
                            Box(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else MiuixTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("取消") }
            }
        )
    }
}
