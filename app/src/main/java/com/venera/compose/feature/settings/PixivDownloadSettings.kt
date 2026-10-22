package com.venera.compose.feature.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.venera.compose.data.prefs.VeneraPreferences
import com.venera.compose.feature.SettingsSubScreen
import com.venera.compose.feature.openSettingsSubScreen
import com.venera.compose.download.ComicStorageRoot
import com.venera.compose.gallery.domain.PixivSaveFormatHelper
import com.venera.compose.ui.tokens.VeneraTokens
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * pixiv 专属下载设置页面（2026-10-22）。
 *
 * 包含：保存路径、保存格式（带 token chips 弹窗）、收藏作品自动保存开关、以及网络设置快捷入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PixivDownloadSettings(
    prefs: VeneraPreferences,
    onBack: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokens = VeneraTokens

    val customDownloadPath by prefs.pixivDownloadPath.collectAsState()
    val saveFormat by prefs.pixivSaveFormat.collectAsState()
    val autoSaveOnFavorite by prefs.pixivAutoSaveOnFavorite.collectAsState()

    val defaultPath = remember(context) {
        File(ComicStorageRoot.resolve(context), "Pixiv").absolutePath
    }
    val downloadPath = customDownloadPath.ifBlank { defaultPath }

    val pickDirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val outcome = withContext(Dispatchers.IO) { evaluatePickedDir(context, uri) }) {
                is DirChoice.Reject -> Toast.makeText(context, outcome.reason, Toast.LENGTH_LONG).show()
                is DirChoice.NeedsPermission -> Toast.makeText(
                    context,
                    "无法写入 ${outcome.path}。请先授权该目录。",
                    Toast.LENGTH_LONG,
                ).show()

                is DirChoice.Accept -> {
                    prefs.setPixivDownloadPath(outcome.path)
                    Toast.makeText(context, "下载路径已设为：${outcome.path}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var formatDialogVisible by remember { mutableStateOf(false) }

    SettingsPage(
        title = "pixiv 下载设置",
        onBack = onBack,
        largeTitle = "下载与存储",
        heroSubtitle = "路径 · 命名 · 存储策略",
    ) {
        SettingsGroup("存储路径与格式") {
            SettingsAction(
                title = "保存路径",
                summary = if (customDownloadPath.isBlank()) "$downloadPath（默认）" else "$downloadPath（自定义）",
                onClick = { pickDirLauncher.launch(null) },
            )
            if (customDownloadPath.isNotBlank()) {
                SettingsAction("恢复默认保存路径", "当前自定义：$customDownloadPath") {
                    prefs.setPixivDownloadPath("")
                    Toast.makeText(context, "已恢复为默认保存路径", Toast.LENGTH_SHORT).show()
                }
            }
            SettingsAction(
                title = "保存格式",
                summary = saveFormat,
                onClick = { formatDialogVisible = true },
            )
            SettingsAction(
                title = "下载任务管理",
                summary = "查看全部、运行中、已完成与失败的图片任务",
                onClick = { context.openSettingsSubScreen(SettingsSubScreen.GALLERY_DOWNLOADS) },
            )
        }

        SettingsGroup("偏好策略") {
            SettingsToggle(
                title = "收藏作品后不自动保存",
                checked = autoSaveOnFavorite,
                onCheckedChange = { prefs.setPixivAutoSaveOnFavorite(it) },
                summary = "开启后点击收藏不会自动触发后台原图下载。",
            )
        }

        SettingsGroup("网络选项") {
            SettingsAction(
                title = "网络设置",
                summary = "配置 OAuth、API 服务的 ECH/SNI 绕过策略以及自定义图片源 Host",
                onClick = onOpenNetworkSettings,
            )
        }
    }

    if (formatDialogVisible) {
        SaveFormatDialog(
            currentFormat = saveFormat,
            onDismiss = { formatDialogVisible = false },
            onConfirm = { newFormat ->
                prefs.setPixivSaveFormat(newFormat)
                formatDialogVisible = false
            },
        )
    }
}

/**
 * 保存格式自定义对话框（图 2 原版还原）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SaveFormatDialog(
    currentFormat: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val tokens = VeneraTokens
    var text by remember { mutableStateOf(currentFormat) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(tokens.shape.extraLarge))
                .background(tokens.color.surface)
                .padding(tokens.spacing.space6),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "保存格式",
                    fontSize = tokens.type.screenTitle,
                    fontWeight = tokens.type.weightSemibold,
                    color = tokens.color.textPrimary,
                )

                Spacer(Modifier.height(tokens.spacing.space5))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("保存格式") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(Modifier.height(tokens.spacing.space4))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                ) {
                    PixivSaveFormatHelper.AVAILABLE_TOKENS.forEach { (label, token) ->
                        FormatTokenChip(
                            label = label,
                            onClick = { text += token },
                        )
                    }
                }

                Spacer(Modifier.height(tokens.spacing.space6))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(tokens.spacing.space11),
                    ) {
                        Text(
                            text = "取消",
                            fontSize = tokens.type.itemTitle,
                            color = tokens.color.textSecondary,
                        )
                    }

                    Button(
                        onClick = { onConfirm(text.ifBlank { PixivSaveFormatHelper.DEFAULT_FORMAT }) },
                        modifier = Modifier
                            .weight(1f)
                            .height(tokens.spacing.space11),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.color.primary,
                            contentColor = tokens.color.onPrimary,
                        ),
                        shape = RoundedCornerShape(tokens.shape.large),
                    ) {
                        Text(
                            text = "确定",
                            fontSize = tokens.type.itemTitle,
                            fontWeight = tokens.type.weightSemibold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatTokenChip(label: String, onClick: () -> Unit) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.spacing.space11))
            .background(tokens.color.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = tokens.type.caption,
            color = tokens.color.textPrimary,
        )
    }
}
