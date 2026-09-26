package com.venera.compose.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import com.venera.compose.data.network.UserAgentPolicy
import com.venera.compose.gallery.data.GelbooruClient
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 画廊的视频条目播放器（二级页就地播）。
 *
 * 为什么要"点一下才建播放器"：实测站方**没有更小的视频转码档** —— `media_asset.variants`
 * 只给静帧（180/360 jpg、720 webp）加原片 mp4，样本 16.1 MB / 42.8 秒、26.1 MB / 26.3 秒。
 * ExoPlayer 一 `prepare()` 就开始缓冲，所以自动播等于替用户随机预下载几十 MB ——
 * 一天池子里视频只占 5%（实测），为那 5% 让另外 95% 的进入也付一次建/释开销同样不值。
 *
 * 三级（原图 + 缩放）那层对视频没有意义，所以本页**不进三级**：点画面是切播放/暂停，
 * 交给 [PlayerView] 自己的控制器。
 */
@OptIn(UnstableApi::class)
@Composable
fun GalleryVideoViewer(
    post: GalleryPost,
    masked: Boolean,
    imageLoader: ImageLoader,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val request = remember(post.uid) { galleryLargeRequest(context, post) }
    // 视频条目的底图取哪一档，两站**不一样**（都是实测）：
    // - yande.re：翻译时把 largeUrl 换成了 `jpeg_url` 静帧，所以拿得到一张图；
    // - Gelbooru：站方对视频的 `sample_url` 给**空串**，于是 `sampleUrl.ifBlank { fileUrl }`
    //   兜底成**原片 mp4** —— 这张"底图"Coil 解不了，会走占位。
    // 所以下面不能假设"底图永远拿得到"：拿不到时退回封面比例占位，由播放器顶上。
    var started by rememberSaveable(post.uid) { mutableStateOf(false) }
    val ratio = post.cardRatio.takeIf { it > 0f } ?: tokens.spacing.coverAspectRatio

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .background(Color.Black)
            // 分级打码在这里同样生效：视频页不能成为绕过守卫的后门。
            .then(if (masked) Modifier.blur(tokens.spacing.space10) else Modifier),
    ) {
        coil3.compose.AsyncImage(
            model = request,
            contentDescription = "${post.site.displayName} #${post.id}",
            imageLoader = imageLoader,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (started) {
            GalleryPlayer(url = post.videoUrl, userAgent = userAgentFor(post))
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { started = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayCircleOutline,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(tokens.spacing.loaderPage),
                )
                Text(
                    text = "播放 · ${post.fileSize / 1024 / 1024} MB" +
                        post.durationSeconds?.let { " · ${it.toInt()} 秒" }.orEmpty(),
                    fontSize = tokens.type.caption,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = tokens.spacing.space5),
                )
            }
        }
    }
}

/**
 * 两站播放时各带什么 UA。
 *
 * - Gelbooru：用它自己的非浏览器串（与 JSON 接口那边同串）。
 *   ⚠️ 这一条**不是**从 Danbooru 那份"浏览器串必 403"的实测搬过来的 ——
 *   那条强判据是 Danbooru 特有的，Gelbooru 侧本轮**没有实测到**同样形态。
 *   这里保持一致只是省事：同一站的接口与 CDN 用同一个串，将来要调也只调一处。
 * - yande.re：图片/接口一直走全局默认 UA（移动端 Chrome 串）且实测正常。
 *
 * media3 用自己的 HTTP 栈，既不过 `VeneraNetworkClient` 也拿不到 `ImageHeaderPolicy` 的内置表，
 * 所以这一层必须自己带 —— 不带就会出现"缩略图看得到、点开播不了"那种最难查的错。
 */
private fun userAgentFor(post: GalleryPost): String = when (post.site) {
    GallerySite.GELBOORU -> GelbooruClient.API_USER_AGENT
    GallerySite.YANDERE -> UserAgentPolicy.DEFAULT_USER_AGENT
}

@OptIn(UnstableApi::class)
@Composable
private fun GalleryPlayer(url: String, userAgent: String) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultHttpDataSource.Factory()
                        .setUserAgent(userAgent)
                        .setAllowCrossProtocolRedirects(true)
                )
            )
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                playWhenReady = true
                prepare()
            }
    }
    DisposableEffect(player) {
        val observer = LifecycleEventObserver { _, event ->
            // 退到后台要停：Media3 不会自己替我们放掉网络与解码器。
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                controllerAutoShow = false
                // 快门色透明：不然起播前会闪一块黑盖住底图。
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize(),
    )
}
