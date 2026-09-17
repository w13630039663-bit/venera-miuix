package com.venera.compose.feature

import android.content.Intent
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.venera.compose.sync.BackupManager
import com.venera.compose.sync.WebDavConfig
import com.venera.compose.sync.WebDavFileItem
import com.venera.compose.sync.WebDavSyncManager
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 生产级云同步与备份恢复配置页面 (S7)
 */
@Composable
fun SyncBackupScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncManager = remember { WebDavSyncManager.getInstance(context) }
    val backupManager = remember { BackupManager.getInstance(context) }

    var config by remember { mutableStateOf(syncManager.getConfig()) }
    var serverUrl by remember { mutableStateOf(config.serverUrl) }
    var username by remember { mutableStateOf(config.username) }
    var password by remember { mutableStateOf(config.password) }
    var remotePath by remember { mutableStateOf(config.remotePath) }

    var isOperating by remember { mutableStateOf(false) }
    var remoteBackups by remember { mutableStateOf<List<WebDavFileItem>>(emptyList()) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    // 本地备份文件导入器
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    isOperating = true
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val tempFile = File(context.cacheDir, "import_backup.venera")
                        tempFile.outputStream().use { output ->
                            inputStream.copyTo(output)
                        }
                        inputStream.close()

                        val res = backupManager.importBackup(tempFile)
                        tempFile.delete()
                        if (res.isSuccess) {
                            val sum = res.getOrNull()!!
                            Toast.makeText(
                                context,
                                "恢复完成！历史 ${sum.historyCount} 条，收藏 ${sum.favoriteCount} 部，统计 ${sum.statsCount} 条",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(context, "导入失败: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "读取备份异常: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isOperating = false
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "云同步与备份恢复",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. WebDAV 云同步配置
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
                                Icon(Icons.Outlined.CloudSync, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = "WebDAV 云端同步", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(text = "支持坚果云、Nextcloud、Alist 等标准 WebDAV 网盘", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("服务器地址 (URL)") },
                            placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("账号") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("密码 / 应用令牌") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = remotePath,
                            onValueChange = { remotePath = it },
                            label = { Text("远程备份目录") },
                            placeholder = { Text("/Venera/") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 保存与测试
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val newCfg = WebDavConfig(
                                        serverUrl = serverUrl.trim(),
                                        username = username.trim(),
                                        password = password.trim(),
                                        remotePath = remotePath.trim()
                                    )
                                    syncManager.saveConfig(newCfg)
                                    Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("保存配置")
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        isOperating = true
                                        val testCfg = WebDavConfig(
                                            serverUrl = serverUrl.trim(),
                                            username = username.trim(),
                                            password = password.trim(),
                                            remotePath = remotePath.trim()
                                        )
                                        val res = com.venera.compose.sync.WebDavClient(testCfg).testConnection()
                                        isOperating = false
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "WebDAV 连通成功！", Toast.LENGTH_SHORT).show()
                                            // 顺便拉取云端备份列表
                                            val listRes = syncManager.getRemoteBackups()
                                            if (listRes.isSuccess) {
                                                remoteBackups = listRes.getOrNull() ?: emptyList()
                                            }
                                        } else {
                                            Toast.makeText(context, "连接失败: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("测试连接", color = MiuixTheme.colorScheme.onSurface)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 上传与还原
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isOperating = true
                                        val res = syncManager.uploadToWebDav()
                                        isOperating = false
                                        if (res.isSuccess) {
                                            Toast.makeText(context, res.getOrNull(), Toast.LENGTH_SHORT).show()
                                            val listRes = syncManager.getRemoteBackups()
                                            if (listRes.isSuccess) {
                                                remoteBackups = listRes.getOrNull() ?: emptyList()
                                            }
                                        } else {
                                            Toast.makeText(context, "同步失败: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("立即上传云端", color = Color.White)
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        isOperating = true
                                        val res = syncManager.restoreFromWebDav()
                                        isOperating = false
                                        if (res.isSuccess) {
                                            val sum = res.getOrNull()!!
                                            Toast.makeText(context, "云端恢复成功！历史 ${sum.historyCount} 条，收藏 ${sum.favoriteCount} 部", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "恢复失败: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(color = Color(0xFF388E3C)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("恢复最新备份", color = Color.White)
                            }
                        }
                    }
                }
            }

            // 2. 本地归档导出与导入
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFF9800)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.FolderZip, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = "本地归档文件 (.venera)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(text = "无需网络，打包导出或选取本地文件导入", fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isOperating = true
                                        val res = backupManager.exportBackup()
                                        isOperating = false
                                        if (res.isSuccess) {
                                            val file = res.getOrNull()!!
                                            Toast.makeText(context, "导出成功: ${file.name}", Toast.LENGTH_SHORT).show()
                                            try {
                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                val share = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/zip"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(share, "分享/保存备份文件"))
                                            } catch (_: Exception) {}
                                        } else {
                                            Toast.makeText(context, "导出失败: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("导出本地备份")
                            }

                            Button(
                                onClick = {
                                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                                },
                                colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("选取文件导入", color = MiuixTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // 3. 云端历史备份记录 (若已获取)
            if (remoteBackups.isNotEmpty()) {
                item {
                    Text(
                        text = "云端可用备份版本 (${remoteBackups.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }

                items(remoteBackups.sortedByDescending { it.lastModified }) { backup ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = backup.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "时间: ${if (backup.lastModified > 0) dateFormat.format(Date(backup.lastModified)) else "未知"} · 大小: ${backup.size / 1024} KB",
                                    fontSize = 10.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        isOperating = true
                                        val res = syncManager.restoreFromWebDav(backup.name)
                                        isOperating = false
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "成功从 ${backup.name} 恢复数据！", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "还原失败: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            ) {
                                Text("恢复此版本", fontSize = 11.sp, color = MiuixTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        if (isOperating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Card(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = MiuixTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("正在处理云端/数据操作，请稍候...", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
