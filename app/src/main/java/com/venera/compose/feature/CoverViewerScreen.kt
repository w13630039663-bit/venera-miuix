package com.venera.compose.feature

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.venera.compose.components.venera.VeneraTextButton
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.venera.compose.components.venera.VeneraIconButton

/**
 * 封面全屏查看器 (对齐官方 comic_details_page/cover_viewer.dart):
 * 全屏展示封面, 点击切换顶栏显隐, 顶栏含返回与保存到相册。
 */
@Composable
fun CoverViewerScreen(
    coverUrl: String,
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokens = VeneraTokens
    var appBarVisible by remember { mutableStateOf(true) }
    // 取图状态必须上屏：这一屏原先是裸 AsyncImage，取不到就是一片纯黑且什么都不说，
    // 用户只能靠"黑得不对劲"猜它失败了。
    var loading by remember(coverUrl) { mutableStateOf(true) }
    var failed by remember(coverUrl) { mutableStateOf(false) }
    var retryTick by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { appBarVisible = !appBarVisible },
        contentAlignment = Alignment.Center,
    ) {
        key(retryTick) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverUrl)
                    // 重试要绕开内存里那份失败结果，否则点一下只是把同一个错误再放一遍。
                    .memoryCacheKey("cover-viewer:$coverUrl#$retryTick")
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onLoading = { loading = true; failed = false },
                onSuccess = { loading = false; failed = false },
                onError = { loading = false; failed = true },
            )
        }

        if (loading && !failed) {
            CircularWavyProgressIndicator(
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.24f),
            )
        }

        if (failed) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
            ) {
                Text(
                    text = "这张封面没取到",
                    fontSize = tokens.type.body,
                    color = Color.White,
                )
                Text(
                    text = "网络或源站没给回图片，可以重试一次",
                    fontSize = tokens.type.caption,
                    color = Color.White.copy(alpha = 0.72f),
                )
                VeneraTextButton(text = "再来一次", onClick = { retryTick++ })
            }
        }

        if (appBarVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VeneraIconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                VeneraIconButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val ok = saveCoverToGallery(context, coverUrl, title)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                if (ok) "已保存到相册 Pictures/Venera" else "保存失败",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }) {
                    Icon(Icons.Outlined.SaveAlt, contentDescription = "保存", tint = Color.White)
                }
            }
        }
    }
}

private suspend fun saveCoverToGallery(context: android.content.Context, url: String, title: String): Boolean {
    return try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request)
        val image = (result as? SuccessResult)?.image ?: return false
        val bmp = (image as? coil3.BitmapImage)?.bitmap
            ?: (image as? android.graphics.drawable.BitmapDrawable)?.bitmap
            ?: return false
        val dir = File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
            "Venera"
        )
        if (!dir.exists()) dir.mkdirs()
        val safeTitle = title.replace(Regex("""[\\/:*?<>|]"""), "_").take(60)
        val file = File(dir, safeTitle + "_" + System.currentTimeMillis() + ".jpg")
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 95, out) }
        true
    } catch (e: Exception) {
        false
    }
}