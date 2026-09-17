package com.venera.compose.feature.sourcemanage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 源脚本编辑页（对齐官方 `_EditFilePage`）。
 *
 * 官方流程：进入全屏编辑页 → 改 `.js` 原文 → **dispose 时写回文件** → `reload()`。
 * 本实现把「写回」显式化为一个保存动作，并且**先解析校验再落盘** ——
 * 官方那种「退出即写」一旦有语法错误，会让源在下次启动时被静默丢弃。
 */
@Composable
fun SourceEditScreen(
    fileName: String,
    sourceName: String,
    initialText: String,
    /** 交由调用方落库；回调 (是否成功, 失败原因) */
    onSave: (content: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onClose: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var saving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val dirty = text != initialText

    fun tryClose() {
        if (saving) return
        if (dirty) confirmDiscard = true else onClose()
    }

    BackHandler { tryClose() }

    Dialog(
        onDismissRequest = { tryClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ---------------- 顶部栏 ----------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { tryClose() }, enabled = !saving) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "编辑 $sourceName",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = fileName + if (dirty) " · 未保存" else "",
                            fontSize = 11.sp,
                            color = if (dirty) Color(0xFFFFA000) else Color.Gray
                        )
                    }
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Button(
                        onClick = {
                            saving = true
                            errorText = null
                            onSave(text) { ok, err ->
                                saving = false
                                if (ok) onClose() else errorText = err
                            }
                        },
                        enabled = !saving && dirty
                    ) {
                        Text("保存")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(Color.DarkGray.copy(alpha = 0.35f))
                )

                // ---------------- 语法错误提示 ----------------
                errorText?.let { err ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE53935).copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "解析失败，未保存：$err",
                            fontSize = 12.sp,
                            color = Color(0xFFE53935)
                        )
                    }
                }

                // ---------------- 编辑器本体 ----------------
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        errorText = null
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface
                    ),
                    placeholder = { Text("JavaScript 源脚本", fontSize = 12.sp) }
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃修改？") },
            text = { Text("当前修改尚未保存，返回将丢失这些改动。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) {
                    Text("放弃", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text("继续编辑")
                }
            }
        )
    }
}
