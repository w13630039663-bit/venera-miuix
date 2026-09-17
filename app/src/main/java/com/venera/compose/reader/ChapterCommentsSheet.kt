package com.venera.compose.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import android.widget.Toast
import com.venera.compose.source.ComicSourceManager
import com.venera.compose.source.model.Comment
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch

/**
 * 章节评论 Sheet 内容 (S8, 对齐官方 reader/chapter_comments.dart):
 * 拉取源声明的 chapterComments 列表 + 发表本章评论 (sendChapterComment)。
 * 源未声明章评能力时如实提示, 不显示假数据。
 */
@Composable
fun ChapterCommentsSheetContent(
    sourceKey: String,
    comicId: String,
    chapterId: String,
    chapterTitle: String
) {
    val context = LocalContext.current
    val sourceManager = remember { ComicSourceManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var loadedOnce by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        error = null
        val source = sourceManager.getSource(sourceKey)
        if (source == null) {
            error = "找不到漫画源"
            loading = false
            return
        }
        val res = source.loadChapterComments(comicId, chapterId, page = 1)
        if (res.isSuccess) {
            comments = res.getOrDefault(emptyList())
            error = null
        } else {
            error = res.exceptionOrNull()?.message ?: "加载评论失败"
        }
        loading = false
        loadedOnce = true
    }

    LaunchedEffect(sourceKey, comicId, chapterId) {
        reload()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp)
    ) {
        Text(
            text = "本章评论 · " + chapterTitle,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(14.dp))

        // 发表输入行
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("说点什么...", fontSize = 13.sp, color = Color.Gray) },
                modifier = Modifier.weight(1f),
                maxLines = 2,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MiuixTheme.colorScheme.primary,
                    unfocusedBorderColor = Color(0xFF3A3A3A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (input.isBlank()) return@Button
                    sending = true
                    scope.launch {
                        val source = sourceManager.getSource(sourceKey)
                        val res = source?.sendChapterComment(comicId, chapterId, input.trim())
                        sending = false
                        if (res?.isSuccess == true) {
                            Toast.makeText(context, "发表成功", Toast.LENGTH_SHORT).show()
                            input = ""
                            scope.launch { }
                            // 重新拉取评论
                            comments = emptyList()
                            loading = true
                            val r2 = source.loadChapterComments(comicId, chapterId, page = 1)
                            if (r2.isSuccess) comments = r2.getOrDefault(emptyList())
                            loading = false
                        } else {
                            Toast.makeText(
                                context,
                                "发表失败: " + (res?.exceptionOrNull()?.message ?: "当前源不支持"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                enabled = input.isNotBlank() && !sending,
                colors = ButtonDefaults.buttonColors(containerColor = MiuixTheme.colorScheme.primary)
            ) {
                Text(if (sending) "发送中" else "发表", fontSize = 13.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when {
            loading -> Box(modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MiuixTheme.colorScheme.primary, strokeWidth = 2.dp)
            }
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                Text(text = error ?: "", fontSize = 13.sp, color = Color(0xFFEF5350))
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = { scope.launch { reload() } }) {
                    Text("重试", color = Color.White)
                }
            }
            comments.isEmpty() && loadedOnce -> Text(
                text = "本章还没有评论 · 来抢首评吧",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 20.dp)
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(comments, key = { it.id.ifBlank { it.userName + it.content.hashCode() } }) { comment ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = comment.userName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (comment.time != null) {
                                    Text(text = comment.time ?: "", fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = comment.content,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                lineHeight = 17.sp
                            )
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Spacer(modifier = Modifier.weight(1f))
                                if ((comment.replyCount ?: 0) > 0) {
                                    Text(text = comment.replyCount.toString() + " 回复", fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}