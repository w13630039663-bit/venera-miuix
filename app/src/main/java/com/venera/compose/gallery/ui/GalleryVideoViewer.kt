package com.venera.compose.gallery.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.CircularWavyProgressIndicator
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
import android.widget.Toast
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import com.venera.compose.data.network.ImageHeaderPolicy
import com.venera.compose.data.network.UserAgentPolicy
import com.venera.compose.gallery.data.GalleryPost
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
    /** 铺满整屏（由调用方持有，因为它要连带改掉外层容器给本页的尺寸）。 */
    fullscreen: Boolean,
    onToggleFullscreen: (Boolean) -> Unit,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val request = remember(post.uid) { galleryLargeRequest(context, post) }
    // 底图取哪一档由 [GalleryPost.videoPosterUrl] 定，两站**不一样**（都是实测）：
    // - yande.re：翻译时把 largeUrl 换成了 `jpeg_url` 静帧，直接用它；
    // - Gelbooru：站方对视频的 `sample_url` 给**空串**，中档被兜底成**原片 mp4** ——
    //   那种地址交给 Coil 会先白下十几二十 MB 再解码失败，所以它退到 `preview_url`。
    // 仍然**不假设底图拿得到**（缩略图也可能 404）：拿不到时退回封面比例占位，由播放器顶上。
    var started by rememberSaveable(post.uid) { mutableStateOf(false) }
    val ratio = post.cardRatio.takeIf { it > 0f } ?: tokens.spacing.coverAspectRatio

    Box(
        (if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(ratio))
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
            GalleryPlayer(
                url = post.videoUrl,
                headers = videoRequestHeaders(post.videoUrl),
                fullscreen = fullscreen,
                onToggleFullscreen = onToggleFullscreen,
                onError = { reason ->
                    // 播不了要说一句话，并且**把播放钮还回去**：留着一块黑框什么也不说，
                    // 用户只能认为页面坏了（本仓口径：失败必须响亮，不许静默交错）。
                    Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
                    started = false
                },
            )
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
                    // 读数**没有就不摆**：Gelbooru 的 JSON 里根本没有 file_size（实测），
                    // 一律翻译成了 0 —— 旧写法于是每条视频都写着"播放 · 0 MB"，
                    // 那是最容易骗人的假读数（看着像"这文件是空的"）。
                    text = buildString {
                        append("播放")
                        if (post.fileSize > 0) append(" · ${"%.1f".format(post.fileSize / 1024.0 / 1024.0)} MB")
                        post.durationSeconds?.takeIf { it > 0.0 }?.let { append(" · ${it.toInt()} 秒") }
                    },
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
 * 播放请求要带的头 —— **直接吃防盗链那张表**（[ImageHeaderPolicy]），不在这一层另立口径。
 *
 * 真机实测（2026-09-27，拿测试夹具里那条真实视频地址打的，两档都验过）：
 * ```
 * 不带 Referer → 302 https://gelbooru.com/hotlink.php?hash=/images/…   Content-Type: text/html
 * 带 Referer   → 206 video/mp4，前 12 字节 `…ftypisom`，Range 正常（能 seek）
 * ```
 * media3 会**跟着那个 302 走**，然后把 hotlink 页的 HTML 当媒体容器解析 ——
 * 报出来正是 `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED`（不是 403、不是网络问题）。
 * 图片侧早就修过同一个坑（表里 `gelbooru.com` 那条注释记着 302→hotlink.php 与
 * "这不是地域封禁"那段弯路），而**视频这一层当时只补了 UA、没补 Referer**，
 * 于是就成了这里注释原本预言的那句"缩略图看得到、点开播不了"。
 *
 * ⚠️ 表里**没有** UA 的站（yande.re 就是）要补上全局默认串：只交表里那几点，
 * 等于把现在能正常播的那一路改成"不带 UA"，那是修一个坏一个。
 * gzip 不是这一条的成因（实测同一地址带 `Accept-Encoding: gzip` 照样回裸字节 + 206）。
 */
private fun videoRequestHeaders(url: String): Map<String, String> =
    LinkedHashMap<String, String>(ImageHeaderPolicy.headersFor(url)).apply {
        putIfAbsent("User-Agent", UserAgentPolicy.DEFAULT_USER_AGENT)
    }

@OptIn(UnstableApi::class)
@Composable
private fun GalleryPlayer(
    url: String,
    headers: Map<String, String>,
    fullscreen: Boolean,
    onToggleFullscreen: (Boolean) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tokens = VeneraTokens
    // 两枚读数各自独立：`buffering` 是"这一笔还在取"，`firstFrame` 是"屏上已经开始动"。
    // 快门色是透明的，所以首帧之前屏上看到的**就是底图那张静帧** —— 没有这两枚读数时，
    // "正在缓冲"和"点了没反应"长得一模一样（用户 2026-09-27 点名的第一条）。
    var firstFrame by remember(url) { mutableStateOf(false) }
    var buffering by remember(url) { mutableStateOf(true) }
    val player = remember(url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    // 整张头表交出去（含 Referer），而不是只挂一个 UA —— 见 [videoRequestHeaders]。
                    // 方法名是 `setDefaultRequestProperties`（media3 1.11.1 上没有
                    // `setRequestProperties`，那是旧名字 —— 按猜的写会直接编译不过）。
                    DefaultHttpDataSource.Factory()
                        .setDefaultRequestProperties(headers)
                        .setAllowCrossProtocolRedirects(true)
                )
            )
            // 起播要攒多少，media3 的默认值是 **2.5 秒"媒体时长"**（bufferForPlaybackMs），
            // 而这一站的视频码率不低（实测一条 26.3 秒 / 2.4 MB ≈ 92 KB/s 的媒体流），
            // 2.5 秒就是两百多 KB；池子里那些 16~26 MB 的原片要先攒好几 MB 才肯动。
            // 本机 curl 实测吞吐 256 KB / 3.06s ≈ 84 KB/s —— 默认值就是"点下去要等好几秒"
            // 的直接成因。500ms 敢这么给，是因为实测这些 mp4 **是 faststart**
            // （`moov` 在第 36 字节、`mdat` 在 48413，先读 48 KB 就够起播），
            // 代价是慢网下更容易中途 rebuffer —— 那有"重新缓冲中"的环在说。
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 10_000,
                        /* maxBufferMs = */ 30_000,
                        /* bufferForPlaybackMs = */ 500,
                        /* bufferForPlaybackAfterRebufferMs = */ 1_500,
                    )
                    .build()
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
        // 播放失败必须报出来。media3 用的是自己的 HTTP 栈（不过 VeneraNetworkClient，
        // 也拿不到防盗链那张表），所以"缩略图看得到、点开播不了"最容易在这一层发作 ——
        // 没有这个监听器时它的表现就是一块黑框，什么线索都不给。
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                Log.d(
                    PLAYER_LOG_TAG,
                    "state=${stateName(state)} playing=${player.isPlaying} " +
                        "suppressed=${player.playbackSuppressionReason} buffered=${player.bufferedPosition}",
                )
                // 只认 STATE_BUFFERING：ENDED 不是"还在取"，把环留在那儿就是假读数。
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY && !firstFrame) {
                    // READY 但还没出声画 —— 十有八九是 `playWhenReady` 被压住了
                    // （音频焦点、或恢复策略）。补这一下之前，用户看到的就是
                    // "加载完了还停在预览，得再点一下才动"。
                    // 只在**首帧之前**补：翻回来/seek 后重新进 READY 不该替用户按下播放。
                    player.play()
                }
            }

            override fun onRenderedFirstFrame() {
                Log.d(PLAYER_LOG_TAG, "first frame rendered")
                firstFrame = true
                buffering = false
            }

            override fun onPlayerError(error: PlaybackException) {
                onError("这条播不出来：${error.errorCodeName} ${error.message.orEmpty()}".trim())
            }
        }
        lifecycle.addObserver(observer)
        player.addListener(listener)
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    controllerAutoShow = false
                    // 快门色透明：不然起播前会闪一块黑盖住底图。
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    // media3 的控制器**本来就带一枚全屏钮**（`exo_player_control_view.xml`
                    // 里的 fullscreenButton / minimalFullscreenButton），但它只在
                    // "设过监听器"时才显形（PlayerControlView 按 listener != null 判可见）。
                    // 我们从来没设过 —— 这就是真机上"没有全屏钮"的原因，不是它没有这个钮。
                    setFullscreenButtonClickListener { wantFullscreen -> onToggleFullscreen(wantFullscreen) }
                }
            },
            update = {
                it.player = player
                // 同一枚钮按当前状态换图标（进入 / 退出两个样子）。
                it.setFullscreenButtonState(fullscreen)
                // **必须同时换 resize mode**：默认是 FIT，而 FIT 下"容器变大"根本不会让
                // 画面变大 —— 画面尺寸由受限的那一边定，而那一边本来就是屏宽
                // （实测一条 639×470 的视频在竖屏上已经铺满宽度）。
                // 只扩框不换 mode = 一枚点了没反应的钮，那是本仓零容忍的假开关。
                // 代价要说清：ZOOM 是**裁切铺满**，竖屏看横屏视频时只看得见中间那一条。
                it.resizeMode = if (fullscreen) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        // **首帧之前必须有读数**。快门是透明的，所以那段时间屏上看到的仍然是底图那张静帧，
        // 与"点了没反应"长得一模一样（用户 2026-09-27 点名的第一条）。
        // 波浪环是全站口径；落点在这块画面的正中（起播前那儿本来就是空的，不挡内容）。
        if (buffering || !firstFrame) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(tokens.spacing.loaderInline),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                    )
                    Text(
                        text = if (!firstFrame) "缓冲中" else "重新缓冲中",
                        fontSize = tokens.type.caption,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = tokens.spacing.space2),
                    )
                }
            }
        }
    }
}

/** 日志里把 media3 那三个状态整数翻成人话（读数要能直接看懂，不然等于没记）。 */
private fun stateName(state: Int): String = when (state) {
    Player.STATE_IDLE -> "IDLE"
    Player.STATE_BUFFERING -> "BUFFERING"
    Player.STATE_READY -> "READY"
    Player.STATE_ENDED -> "ENDED"
    else -> "UNKNOWN($state)"
}

private const val PLAYER_LOG_TAG = "GalleryVideo"
