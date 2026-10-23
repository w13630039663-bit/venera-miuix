package com.venera.compose.gallery.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.venera.compose.GalleryVideoActivity
import com.venera.compose.data.network.ImageHeaderPolicy
import com.venera.compose.data.network.UserAgentPolicy
import com.venera.compose.gallery.data.GalleryPost
import java.util.concurrent.ConcurrentHashMap

/**
 * 画廊视频播放的**唯一**播放器工厂 + 请求头口径 + 位置交棒。
 *
 * ## 为什么抽出来
 *
 * 2026-10-19 那一批把「全屏」从"同一个 Activity 里把框放大"改成**另一个 Activity**
 * （见 `GalleryVideoActivity`）。于是播放器要在两个地方建：就地那一小块，和全屏那一整屏。
 * 建法（防盗链头表、起播缓冲档、跨协议重定向、释放时机）有十几条实测结论垫着，
 * 抄成两份必然漂 —— 而漂了之后的症状是"就地能播、全屏播不了"这种只在一条路上复现的裂口。
 * 所以两处共用本文件：**口径只写一次**。
 */

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
 *
 * ⚠️ 表里**没有** UA 的站（yande.re 就是）要补上全局默认串：只交表里那几点，
 * 等于把现在能正常播的那一路改成"不带 UA"，那是修一个坏一个。
 */
internal fun videoRequestHeaders(url: String): Map<String, String> =
    LinkedHashMap<String, String>(ImageHeaderPolicy.headersFor(url)).apply {
        putIfAbsent("User-Agent", UserAgentPolicy.DEFAULT_USER_AGENT)
    }

/**
 * 建一只画廊视频用的 [ExoPlayer]（**不**负责 release，交调用方的 `DisposableEffect`）。
 *
 * @param startPositionMs 起播位置。全屏那一跳要接着就地播到的地方，所以必须能传。
 */
@UnstableApi
internal fun buildGalleryVideoPlayer(
    context: Context,
    url: String,
    headers: Map<String, String>,
    startPositionMs: Long = 0L,
): ExoPlayer = ExoPlayer.Builder(context)
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
        // `seekTo` 必须在 `prepare()` **之前**：之后调会让播放器先按 0 位置读一段
        // 再跳走，观感是"从第一帧开始闪一下"。
        if (startPositionMs > 0L) seekTo(startPositionMs)
        prepare()
    }

/**
 * 组合一棵"跟着 Lifecycle 走"的播放器：`ON_STOP` 暂停、离开组合时 release。
 *
 * @param startPositionMs 只在**首次**建播放器时生效（`remember(url)` 的 key 里不含它）：
 *   把它算进 key 会让"交棒位置"每变一次就重建播放器。
 *
 * ⚠️ 2026-10-23 拆掉了「播放器**实例**跨 Activity 交棒」那一整套（takeover / giveBack /
 * releaseIfTaken）。真机日志证明它是**净负分**：
 * - 就地页点全屏时把实例存进接管槽，然后 ColorOS 因运行期 `requestedOrientation` 强制 relaunch
 *   （`hookShouldRelaunchLocked ... adjustedChanges={CONFIG_ORIENTATION, CONFIG_SCREEN_SIZE}`，
 *   连 `android:configChanges` 都拦不住）；
 * - relaunch 的旧实例 `onDispose` 里判 `isTakenOver(url, player)` —— 而新实例取走实例时已经
 *   `remove` 了槽位，判据返回 false ⇒ **旧实例把新实例正在用的那只播放器 release 掉**；
 * - 全屏页随即收到播放器错误 → `onError` → `finish()` ⇒ 用户看到"点全屏闪一下就回大图页"。
 *
 * 现在只交棒**位置**（毫秒）：每页各建各的播放器，谁都不 release 别人的实例。朝向写死在清单上
 * （见 `AndroidManifest.xml` 与 `GalleryVideoActivity`），全屏页一出生就是对的朝向，
 * 不存在"改朝向 ⇒ relaunch ⇒ 实例易主"这条链，也不需要实例交棒。
 */
@UnstableApi
@Composable
internal fun rememberGalleryVideoPlayer(
    url: String,
    headers: Map<String, String>,
    startPositionMs: Long = 0L,
): ExoPlayer {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(url) { buildGalleryVideoPlayer(context, url, headers, startPositionMs) }
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
    return player
}

/**
 * 「就地播放器 ↔ 全屏播放页」之间那一枚**位置交棒**。
 *
 * ## 为什么需要它（用户 2026-10-19：「横屏视频点击全屏放大后并不是全屏只是个框，
 * 我要的是安卓全屏的样式，而且你这个做的要旋转几次还要重新加载，重做一遍」）
 *
 * 旧做法是**在同一个 Activity 里**把播放器那块 Box 从"按比例定框"换成 `fillMaxSize()`，
 * 再给宿主 Activity 改 `requestedOrientation`。三件事一起坏：
 * 1. 改朝向 ⇒ 宿主 Activity 没有 `configChanges` ⇒ **整页重建**：ExoPlayer 释放、
 *    重新 `prepare()`、图重下 —— 就是用户说的"要重新加载"，而且旋转过程中还会闪两下；
 * 2. 只把框放大、画面仍是 `RESIZE_MODE_ZOOM`（裁切）⇒ 看着"变大了但不清"；
 * 3. 大图页那一层还有顶栏 / 底栏 / 系统栏，`fillMaxSize()` 只是铺满了**内容区**，
 *    在用户眼里仍然是"一个框"。
 *
 * 现在的分工：全屏是**另一个 Activity**（`GalleryVideoActivity`），它自己锁朝向、自己吃
 * `configChanges`（旋转不重建）、自己藏系统栏。就地那一页**一动不动** —— 于是"旋转"完全
 * 发生在上面那一层，底下那页的播放器、图片、滚动位置一个都没重来。
 *
 * 交棒只走这一个对象：进全屏时记下当前播放位置，退出时全屏页把新位置写回来，
 * 就地播放器在 `ON_RESUME` 时取一次（取完即删，避免下次进来莫名 seek）。
 */
internal object GalleryVideoHandoff {

    /** url → 播放位置（毫秒）。用并发表：写的一方在播放器回调线程上。 */
    private val positions = ConcurrentHashMap<String, Long>()

    fun put(url: String, positionMs: Long) {
        if (url.isBlank()) return
        positions[url] = positionMs.coerceAtLeast(0L)
    }

    /** 取一次就删：这是一枚"一次性交棒"，不是一份要长期同步的状态。 */
    fun take(url: String): Long? {
        if (url.isBlank()) return null
        return positions.remove(url)
    }
}

/**
 * 打开全屏播放页（2026-10-19）。
 *
 * `makeCustomAnimation(0, 0)` 把系统那套**打开**转场压掉：全屏页自己锁朝向、自己铺黑底，
 * 两套动画同时跑会在中间那两帧看到"就地的框被拉大"。关闭那一下**不压** ——
 * 返回要留系统那套跨 activity 预测式动画。
 */
fun Context.openGalleryVideoFullscreen(post: GalleryPost, positionMs: Long) {
    // 入场给 MIUIX 风格平移转场，退场交回系统预测式返回动画
    startActivity(
        GalleryVideoActivity.intent(this, post, positionMs),
        androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
            this,
            com.venera.compose.R.anim.miuix_activity_open_enter,
            com.venera.compose.R.anim.miuix_activity_open_exit
        ).toBundle(),
    )
}
