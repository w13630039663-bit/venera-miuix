package com.venera.compose

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.venera.compose.feature.VeneraTheme
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.ui.GalleryPlayerSurface
import com.venera.compose.gallery.ui.GalleryVideoHandoff
import com.venera.compose.gallery.ui.rememberGalleryVideoPlayer
import com.venera.compose.gallery.ui.videoRequestHeaders

/**
 * 画廊视频的**全屏播放页**（2026-10-19，用户「重做一遍」那一批）。
 *
 * ## 为什么必须是另一颗 Activity（而不是在同一页里把框放大）
 *
 * 用户原话：「横屏视频点击全屏放大后并不是全屏只是个框，我要的是安卓全屏的样式，
 * 而且你这个做的要旋转几次还要重新加载，重做一遍」。旧实现是在大图页里换尺寸 +
 * 给宿主 Activity 改 `requestedOrientation`，三件事一起坏：
 *
 * 1. **重新加载**：本应用全局没有 `android:configChanges`（见 `AndroidManifest.xml`），
 *    改朝向 = 宿主 Activity 重建 ⇒ ExoPlayer 释放、重新 `prepare()`、图重下。
 *    而"旋转几次"是另一件事：`SENSOR_LANDSCAPE` 在手持姿势接近竖直时会来回翻转。
 * 2. **只是铺满内容区**：大图页还有顶栏、底栏、系统栏，`fillMaxSize()` 铺的是**内容区**，
 *    在用户眼里仍然是"一个框"。
 * 3. **看着糊**：旧版全屏时换成 `RESIZE_MODE_ZOOM`（裁切铺满），横屏视频在竖屏里只看得见
 *    中间一条 —— 那正是"模糊"的来源之一（放大裁切，不是原比例）。
 *
 * 现在这一颗 Activity：
 * - **不碰朝向**：清单不声明 `screenOrientation`，跟着宿主（竖屏）走。这是 2026-10-23
 *   第三次改这里才定下来的 —— 前两版（运行期 `requestedOrientation`、清单静态 `landscape`）
 *   都撞同一面墙，见下面「2026-10-23 真机定因」；
 * - **自己藏系统栏**（immersive sticky，边滑边现）；
 * - **黑底 + 原比例 `FIT`**（不裁切）；
 * - 退出时把播放位置写回 [GalleryVideoHandoff]，就地那页接着播。
 *
 * ## 为什么不继承 `VeneraSubActivityBase`
 *
 * 基类那套是给"浮在模糊背景上"的页面准备的（边到边 + blur-behind + 不透明氛围底）。
 * 全屏播放页要的是**纯黑**：背后那层糊既看不见（黑底盖死），还会白烧 GPU；
 * 而 `enableEdgeToEdge` 默认那层状态栏 scrim 会在黑底上留一条灰边。
 *
 * ## 2026-10-23 真机定因：三件"看着像那么回事、其实是反效果"的事
 *
 * 1. **`android:configChanges` 在 ColorOS 上拦不住 relaunch**。真机日志：
 *    `ActivityRecordExtImpl: hookShouldRelaunchLocked, state: true,
 *    reason: adjustedChanges={CONFIG_ORIENTATION, CONFIG_SCREEN_SIZE}`
 *    —— 清单里明明 `handles=0x4dfb`（含 ORIENTATION|SCREEN_SIZE），系统仍判"要重启"。
 *    所以"吃 configChanges ⇒ 转屏不重建"这条前提**在本机不成立**，别再把宝押在它上面。
 * 2. **播放器实例跨 Activity 交棒是净负分**。launch 时 ColorOS 强制 relaunch，旧实例
 *    `onDispose` 判 `isTakenOver` 时新实例已把槽位 `remove` 掉 ⇒ 判据为假 ⇒ 旧实例
 *    release 掉新实例正在用的播放器 ⇒ 全屏页报错 `finish()` ⇒ "点全屏闪一下就回大图页"。
 *    现在只交棒位置，每页各建各的播放器（详见 `rememberGalleryVideoPlayer` 头注）。
 * 3. **「竖屏任务里启动横屏 Activity」= 无限重启**。前两版分别用运行期
 *    `requestedOrientation` 与清单静态 `landscape`/`portrait`，两条都让 ColorOS 判
 *    relaunch，而 relaunch 出来的新实例再被 relaunch。真机日志里
 *    `START …GalleryVideoActivity → removeAppToken(f=finishing) → START …` 循环十几次，
 *    用户看到的就是"背景闪一下、按钮变一下、又回来"。
 *    ⇒ **这一页不声明朝向、不碰 `requestedOrientation`**：它继承宿主，全屏观感靠
 *    藏系统栏 + `RESIZE_MODE_FIT` 铺满实现。想横着看就横握手机，本页吃 `configChanges`，
 *    几何变更不重建（这一条在本机对**同一朝向内的尺寸变化**是成立的）。
 *
 * 结论：**别改朝向，也别共享播放器实例**。朝向跟着宿主，实例各页自有。
 */
@UnstableApi
class GalleryVideoActivity : ComponentActivity() {

    private lateinit var post: GalleryPost
    private var startPositionMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 站点/条目从 Intent 里复原；`recreate()` 走不到这里（configChanges 已接管转屏），
        // 但进程被杀后重建仍走 `savedInstanceState` 那条原路 —— 所以不读 Bundle，
        // 一律以 Intent 为准：Intent 在这两种情况下都还在。
        post = requireNotNull(parsePost(intent)) { "全屏播放页缺少条目参数" }
        startPositionMs = intent.getLongExtra(EXTRA_POSITION_MS, 0L)

        // ── 朝向：什么都不做（2026-10-23 真机定因）────────────────────────────────
        //
        // 从前这里写 `requestedOrientation = LANDSCAPE / PORTRAIT`。本机（ColorOS）上那是
        // **一次运行期改朝向** ⇒ 一次 Activity relaunch：真机 log 里
        // `hookShouldRelaunchLocked ... adjustedChanges={CONFIG_ORIENTATION, CONFIG_SCREEN_SIZE}`
        // 紧跟着旧实例 surface destroy，播放器那一跳正好卡在中间 —— 用户说的
        // 「旋转几次还要重新加载」，有一半就是进全屏这一下自己转出来的。
        //
        // 第二版改成"清单静态声明 + 按宽高拆两颗 Activity"，撞的是同一面墙的更狠版本：
        // 竖屏任务里起横屏 Activity ⇒ ColorOS 无限 relaunch（`START → removeAppToken(f) →
        // START → …` 循环十几次），用户看到"点全屏闪一下就回来"。
        //
        // 现在**这一页不碰朝向**：跟着宿主走，全屏观感靠藏系统栏 + FIT 铺满。
        // 想横着看就横握手机 —— 本页吃 `configChanges`，同一朝向内的尺寸变化不重建。
        enableEdgeToEdge()
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // 屏幕防窥要覆盖到这一页：这里露的是整屏视频，比缩略图更该挡。
        applySecureScreenPreference()
        hideSystemBars()
        setContent {
            VeneraTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    FullscreenPlayer(
                        post = post,
                        startPositionMs = startPositionMs,
                        onExit = { finish() },
                    )
                }
            }
        }
    }

    /**
     * 沉浸式：藏状态栏与导航栏，用户从边缘滑出时**临时现一下**再自动收回去
     * （`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`）。这是"安卓全屏样式"本身，
     * 也是用户那句"只是个框"要的另一半 —— 系统栏不去掉，画面永远被切一刀。
     */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * 焦点每回来一次就重申一遍沉浸式（2026-10-23 加固）：从边缘滑出系统栏、对话框关闭、
     * 分屏切回来……这些路径都会把系统栏**自己请回来**，画面又变回“内容区一个框”。
     * 经典坑：`onCreate` 里只藏一次是拎不住它的，官方口径就是在
     * `onWindowFocusChanged(hasFocus = true)` 里重调一遍。
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        // 退出/切后台前把位置交回就地那页。放在 `onPause` 而不是 `onDestroy`：
        // `onDestroy` 在进程被杀时不一定跑到，而 `onPause` 一定会。
        currentPosition()?.let { GalleryVideoHandoff.put(post.videoUrl, it) }
        super.onPause()
    }

    /** 当前播放位置由 Compose 那侧回填（Activity 够不着 Composable 里那只播放器）。 */
    private var positionProbe: (() -> Long)? = null

    private fun currentPosition(): Long? = positionProbe?.invoke()

    @Composable
    private fun FullscreenPlayer(
        post: GalleryPost,
        startPositionMs: Long,
        onExit: () -> Unit,
    ) {
        val context = LocalContext.current
        val headers = remember(post.videoUrl) { videoRequestHeaders(post.videoUrl) }
        val player: ExoPlayer =
            rememberGalleryVideoPlayer(
                url = post.videoUrl,
                headers = headers,
                startPositionMs = startPositionMs,
            )
        DisposableEffect(player) {
            positionProbe = { player.currentPosition }
            onDispose { positionProbe = null }
        }
        // 系统返回（含预测式返回）直接 finish；退出前的位置由 `onPause` 交回。
        // 这里不挂 `BackHandler`：手势返回的跟手动画是系统的，自己拦一道反而把它吃掉。
        GalleryPlayerSurface(
            player = player,
            post = post,
            fullscreen = true,
            // 全屏页里那枚钮是"退出全屏"：点了就 finish，落回就地播放。
            onFullscreenClick = onExit,
            onError = { reason ->
                android.widget.Toast.makeText(context, reason, android.widget.Toast.LENGTH_LONG)
                    .show()
                onExit()
            },
        )
    }

    /**
     * 这一条是不是横屏视频。
     *
     * 判据顺序与就地那页同一份口径：先看站方报的真宽高，再看 `cardRatio`，
     * 最后退回"当横屏处理"（视频绝大多数是横的，且横屏是最不坏的错法）。
     *
     * ⚠️ 它现在**只用于诊断与日志**，不再决定 Activity 朝向：2026-10-23 真机读数证明
     * 按它分流到横/竖两颗 Activity 会撞上 ColorOS「竖屏任务里起横屏 Activity 强制
     * relaunch」那面墙，结果是无限重启（详见本文件头注与 `AndroidManifest.xml`）。
     */
    companion object {
        fun isLandscapeVideo(post: GalleryPost): Boolean {
            val w = post.largeWidth.takeIf { it > 0 } ?: post.width
            val h = post.largeHeight.takeIf { it > 0 } ?: post.height
            if (w > 0 && h > 0) return w >= h
            val ratio = post.cardRatio
            return ratio <= 0f || ratio >= 1f
        }

        const val EXTRA_SITE = "video_site_key"
        const val EXTRA_ID = "video_post_id"
        const val EXTRA_TAGS = "video_tags"
        const val EXTRA_RATING = "video_rating"
        const val EXTRA_AUTHOR = "video_author"
        const val EXTRA_SOURCE = "video_source"
        const val EXTRA_PREVIEW_URL = "video_preview_url"
        const val EXTRA_FAST_URL = "video_fast_url"
        const val EXTRA_LARGE_URL = "video_large_url"
        const val EXTRA_FILE_URL = "video_file_url"
        const val EXTRA_FILE_SIZE = "video_file_size"
        const val EXTRA_DURATION = "video_duration"
        const val EXTRA_WIDTH = "video_width"
        const val EXTRA_HEIGHT = "video_height"
        const val EXTRA_POSITION_MS = "video_position_ms"

        /**
         * 把 [GalleryPost] 摊成 Intent 参数。
         *
         * 为什么不塞 `Serializable`：`GalleryPost` 是共享面（桌面也编）的纯 data class，
         * 让它 `: Serializable` 会把"可序列化"这件 Android 宿主的事写进模型里 ——
         * 而模型那层不许碰平台件（那条尺子在 `DesktopSharedFaceLedgerTest` 里）。
         * 逐字段摊开只有十几行，且**加了新字段会在这里显形**，不会静默丢。
         *
         * ⚠️ 不分流朝向（2026-10-23）：从前按 `isLandscapeVideo` 分流到横/竖两颗 Activity，
         * 撞上 ColorOS「竖屏任务里起横屏 Activity 强制 relaunch」那面墙，无限重启。
         * 现在只此一颗，朝向不碰，跟着宿主走。
         */
        fun intent(context: Context, post: GalleryPost, positionMs: Long): Intent =
            Intent(context, GalleryVideoActivity::class.java).apply {
                putExtra(EXTRA_SITE, post.site.routeKey)
                putExtra(EXTRA_ID, post.id)
                putExtra(EXTRA_TAGS, post.tags)
                putExtra(EXTRA_RATING, post.rating)
                putExtra(EXTRA_AUTHOR, post.author)
                putExtra(EXTRA_SOURCE, post.source)
                putExtra(EXTRA_PREVIEW_URL, post.previewUrl)
                putExtra(EXTRA_FAST_URL, post.fastUrl)
                putExtra(EXTRA_LARGE_URL, post.largeUrl)
                putExtra(EXTRA_FILE_URL, post.fileUrl)
                putExtra(EXTRA_FILE_SIZE, post.fileSize)
                putExtra(EXTRA_DURATION, post.durationSeconds ?: -1.0)
                putExtra(EXTRA_WIDTH, post.width)
                putExtra(EXTRA_HEIGHT, post.height)
                putExtra(EXTRA_POSITION_MS, positionMs)
            }

        private fun parsePost(intent: Intent): GalleryPost? {
            val siteKey = intent.getStringExtra(EXTRA_SITE) ?: return null
            val site = GallerySite.fromRouteKey(siteKey) ?: return null
            val duration = intent.getDoubleExtra(EXTRA_DURATION, -1.0)
            return GalleryPost(
                site = site,
                id = intent.getLongExtra(EXTRA_ID, 0L),
                tags = intent.getStringExtra(EXTRA_TAGS).orEmpty(),
                rating = intent.getStringExtra(EXTRA_RATING).orEmpty(),
                author = intent.getStringExtra(EXTRA_AUTHOR).orEmpty(),
                source = intent.getStringExtra(EXTRA_SOURCE).orEmpty(),
                previewUrl = intent.getStringExtra(EXTRA_PREVIEW_URL).orEmpty(),
                fastUrl = intent.getStringExtra(EXTRA_FAST_URL).orEmpty(),
                largeUrl = intent.getStringExtra(EXTRA_LARGE_URL).orEmpty(),
                fileUrl = intent.getStringExtra(EXTRA_FILE_URL).orEmpty(),
                fileSize = intent.getLongExtra(EXTRA_FILE_SIZE, 0L),
                durationSeconds = duration.takeIf { it > 0.0 },
                width = intent.getIntExtra(EXTRA_WIDTH, 0),
                height = intent.getIntExtra(EXTRA_HEIGHT, 0),
            )
        }
    }
}

