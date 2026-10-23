package com.venera.compose.gallery.ui

import android.widget.Toast
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import com.venera.compose.R
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
 *
 * ## 全屏改由独立 Activity 承担（2026-10-19，用户「重做一遍」）
 *
 * 旧版在这里把框换成 `fillMaxSize()` 再改宿主 Activity 的 `requestedOrientation`，
 * 三件事一起坏：
 * 1. 改朝向 ⇒ 宿主 Activity 没有 `configChanges` ⇒ **整页重建**：ExoPlayer 释放、
 *    重新 `prepare()`、图重下 —— 就是用户说的"要旋转几次还要重新加载"；
 * 2. 只把框放大、画面仍是 `RESIZE_MODE_ZOOM`（裁切）⇒ 看着"变大了但不清"；
 * 3. 大图页那一层还有顶栏 / 底栏 / 系统栏，`fillMaxSize()` 只是铺满了**内容区**，
 *    在用户眼里仍然是"一个框"。
 *
 * 现在这里**只管就地这一块**，全屏交给 `GalleryVideoActivity`：它自己锁朝向、自己吃
 * `configChanges`（旋转不重建）、自己藏系统栏，本页一个像素都不动。
 * 播放位置通过 [GalleryVideoHandoff] 交接，回来接着播。
 */
@OptIn(UnstableApi::class)
@Composable
fun GalleryVideoViewer(
    post: GalleryPost,
    masked: Boolean,
    imageLoader: ImageLoader,
    /** 点播放器那枚全屏钮。参数是**当前播放位置**，全屏页要从那里接着播。 */
    onOpenFullscreen: (Long) -> Unit,
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
        Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.Black),
    ) {
        coil3.compose.AsyncImage(
            model = request,
            contentDescription = "${post.site.displayName} #${post.id}",
            imageLoader = imageLoader,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                // 打码只在**还没开播**时糊底图：开播之后那层糊会盖在画面上（真机读数），
                // 而播放器自己已经在画了，遮不遮由 `masked` 与起播状态共同决定。
                .then(if (masked && !started) Modifier.blur(tokens.spacing.space10) else Modifier),
        )
        if (started) {
            GalleryInlinePlayer(
                post = post,
                onOpenFullscreen = onOpenFullscreen,
                onStop = { started = false },
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
                        if (post.fileSize > 0) {
                            append(" · ${"%.1f".format(post.fileSize / 1024.0 / 1024.0)} MB")
                        }
                        post.durationSeconds?.takeIf { it > 0.0 }?.let { append(" · ${it.toInt()} 秒") }
                    },
                    fontSize = tokens.type.caption,
                    color = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = tokens.spacing.space5),
                )
                if (masked) {
                    Text(
                        text = "已按分级打码 · 点击即可开始播放",
                        fontSize = tokens.type.caption,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.Center).padding(top = 80.dp),
                    )
                }
            }
        }
    }
}

/**
 * 就地播放器（自带建/释与"回位"）。
 *
 * 回位那一截是 2026-10-19 新增的：从全屏页返回时，位置经 [GalleryVideoHandoff] 写回来，
 * 这里在 `ON_RESUME` 取一次并 `seekTo`。取完即删，所以"下次再点进来从头播"照旧。
 */
@OptIn(UnstableApi::class)
@Composable
private fun GalleryInlinePlayer(
    post: GalleryPost,
    onOpenFullscreen: (Long) -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val headers = remember(post.videoUrl) { videoRequestHeaders(post.videoUrl) }
    val player = rememberGalleryVideoPlayer(url = post.videoUrl, headers = headers)

    DisposableEffect(player) {
        // 从全屏页回来：接着播到的地方。取完即删（一次性交棒）。
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                GalleryVideoHandoff.take(post.videoUrl)?.let { position ->
                    if (position > 0L) player.seekTo(position)
                    player.play()
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    GalleryPlayerSurface(
        player = player,
        post = post,
        fullscreen = false,
        // 点全屏那一跳只把**当前位置**交出去（经 [GalleryVideoHandoff]），播放器实例不交：
        // 实例交棒在 ColorOS 上会被 relaunch 打断（见 `rememberGalleryVideoPlayer` 头注），
        // 结果是旧实例 release 掉新实例正在用的播放器，全屏页随即报错退回。
        onFullscreenClick = {
            onOpenFullscreen(player.currentPosition.coerceAtLeast(0L))
        },
        onError = { reason ->
            // 播不了要说一句话，并且**把播放钮还回去**：留着一块黑框什么也不说，
            // 用户只能认为页面坏了（本仓口径：失败必须响亮，不许静默交错）。
            Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
            onStop()
        },
    )
}

/**
 * 播放画面本身（控制器 + 缓冲读数 + 首帧口径）。就地与全屏**共用这一颗** ——
 * 两处各写一遍的话，"全屏能播、就地播不了"这类只在一条路上复现的裂口迟早会出现。
 *
 * @param fullscreen 只影响全屏钮的图标朝向。
 *   ⚠️ 两种形态都用 `RESIZE_MODE_FIT`：用户 2026-10-19 明确说「不应该有裁剪」，
 *   而 `ZOOM` 就是裁切铺满 —— 横屏视频在竖屏里只看得见中间一条，那正是"看着糊"的来源之一。
 */
@OptIn(UnstableApi::class)
@Composable
internal fun GalleryPlayerSurface(
    player: ExoPlayer,
    post: GalleryPost,
    fullscreen: Boolean,
    onFullscreenClick: () -> Unit,
    onError: (String) -> Unit,
) {
    val tokens = VeneraTokens
    // 两枚读数各自独立：`buffering` 是"这一笔还在取"，`firstFrame` 是"屏上已经开始动"。
    // 快门色是透明的，所以首帧之前屏上看到的**就是底图那张静帧** —— 没有这两枚读数时，
    // "正在缓冲"和"点了没反应"长得一模一样（用户 2026-09-27 点名的第一条）。
    var firstFrame by remember(player) { mutableStateOf(false) }
    var buffering by remember(player) { mutableStateOf(true) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
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
                firstFrame = true
                buffering = false
            }

            override fun onPlayerError(error: PlaybackException) {
                onError("这条播不出来：${error.errorCodeName} ${error.message.orEmpty()}".trim())
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // 黑底（2026-10-23）：起播后那层糊底图仍在下层（它只是被 PlayerView 盖住，没被移除），
    // 播放器画面在 letterbox 或合成空隙间露出的任何一像素都会透出那张被放大的低清图 ——
    // 真机上读起来就是“播着还是糊”。黑底把下层彻底扣住。
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                // ⚠️ 从布局里充气而不是 `PlayerView(ctx)`：`surface_type` 只在构造函数里读一次，
                // 没有运行时 setter。必须是 texture_view —— 否则 SurfaceView 落在**窗口下面**，
                // 会被本页窗口的 `FLAG_BLUR_BEHIND`（32dp）整块糊掉，而底图与文字画在窗口里所以清楚，
                // 读起来正是「预览清楚、一开播就糊」。定因读数与完整推理写在
                // `res/layout/venera_gallery_player.xml` 的头注里。
                (android.view.LayoutInflater.from(ctx)
                    .inflate(R.layout.venera_gallery_player, null) as PlayerView).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    controllerAutoShow = false
                    // 快门色透明：不然起播前会闪一块黑盖住底图。
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    // media3 的控制器**本来就带一枚全屏钮**（`exo_player_control_view.xml`
                    // 里的 fullscreenButton / minimalFullscreenButton），但它只在
                    // "设过监听器"时才显形（PlayerControlView 按 listener != null 判可见）。
                    setFullscreenButtonClickListener { onFullscreenClick() }
                }
            },
            update = {
                it.player = player
                // 同一枚钮按当前状态换图标（进入 / 退出两个样子）。
                it.setFullscreenButtonState(fullscreen)
                it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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
