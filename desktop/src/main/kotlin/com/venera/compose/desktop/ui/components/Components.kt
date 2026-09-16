package com.venera.compose.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.URI

// 图片全局内存缓存
private val imageMemoryCache = mutableMapOf<String, ImageBitmap>()

@Composable
fun AsyncNetworkImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var bitmap by remember(url) { mutableStateOf(imageMemoryCache[url]) }

    LaunchedEffect(url) {
        if (bitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    val bytes = URI(url).toURL().openStream().use { it.readBytes() }
                    val bmp = bytes.decodeToImageBitmap()
                    imageMemoryCache[url] = bmp
                    bitmap = bmp
                } catch (_: Exception) {
                    // 降级占位
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier.background(Color(0xFF262626)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "海报", fontSize = 11.sp, color = Color.DarkGray)
        }
    }
}

@Composable
fun MiuixSectionHeader(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "›",
            fontSize = 20.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant
        )
    }
}

@Composable
fun CapsuleTag(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MiuixTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun StarRating(
    rating: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "★",
            color = Color(0xFFFFB800),
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = rating,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
