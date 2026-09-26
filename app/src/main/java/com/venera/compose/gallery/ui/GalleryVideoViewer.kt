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
import com.venera.compose.gallery.data.DanbooruClient
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
    // 视频条目的 largeUrl 在翻译时就换成了静帧（见 DanbooruClient.toPost），
    // 所以这张底图永远拿得到，且和三级用的是同一个 cache key。
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
 * 两站的 UA **不能共用**：
 * - Danbooru 挂在 Cloudflare 上，实测**浏览器型 UA 一律 403 + `cf-mitigated`**，只认非浏览器串；
 * - yande.re 那侧图片/接口一直走的是全局默认 UA（移动端 Chrome 串）且实测正常。
 *
 * media3 用自己的 HTTP 栈，既不过 `VeneraNetworkClient` 也拿不到 `ImageHeaderPolicy` 的内置表，
 * 所以这一层必须自己带 —— 不带就会出现"缩略图看得到、点开播不了"那种最难查的错。
 */
private fun userAgentFor(post: GalleryPost): String = when (post.site) {
    GallerySite.DANBOORU -> DanbooruClient.API_USER_AGENT
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
