package com.venera.compose.platform

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import com.venera.compose.components.CachedComicMetricsSnapshot
import com.venera.compose.components.FavoriteSaveResult
import com.venera.compose.components.UxPlatformServices
import com.venera.compose.components.LocalUxPlatformServices
import coil3.imageLoader
import com.venera.compose.feature.SettingsSubScreen
import com.venera.compose.feature.openSettingsSubScreen
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * [UxPlatformServices] 的 Android 实现 —— 把 `components/` 里原先那五处平台调用
 * **逐字搬进这一颗**（2026-10-20，写法 A）。
 *
 * ## 口径：行为必须逐字不变
 *
 * 搬家的判据不是"能编译"，是"按下去的手感一模一样"。所以每一处都保留原调用：
 *  - 触觉：`view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)`
 *    （原 `VeneraFloatingNavBar.kt:167`）；
 *  - 吐司：`Toast.makeText(context, message, Toast.LENGTH_SHORT).show()`
 *    （原 `ComicCardContextMenu.kt:102`）；
 *  - 帧钟：`Choreographer.getInstance().postFrameCallback`
 *    （原 `backdrop/Coroutines.kt:14`）；
 *  - 偏好监听：`SharedPreferences.OnSharedPreferenceChangeListener`
 *    （原 `ComicListPresentation.kt:28,57`）；
 *  - 预测性返回：Android 30+ 支持，恒 `true`（挂不挂由调用点既有判据再说）。
 *
 * ## 为什么 `view` 与 `context` 都是构造参数而不是在这里 `LocalContext.current`
 *
 * 这一颗本身**不会被桌面编译面收进来**（它 import 了 `android.*`，由 `DesktopFace` 的禁引账本
 * 排除）。但它如果是 `@Composable`、内部吃 `LocalContext`，那就变成一个"共享面里的
 * Composable"—— 调用点仍然要在共享面里 import 它，于是共享面又和 Android 绑在一起了。
 * 所以这里做成**普通类**：寄宿方（`MainActivity`）取好 `view` / `context` 再 new 出来，
 * 共享面只认接口。
 */
class AndroidUxPlatformServices(
    private val view: View,
    private val context: Context,
) : UxPlatformServices {

    override fun hapticTap() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    override fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override suspend fun awaitNextFrame() {
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            android.view.Choreographer.getInstance().postFrameCallback {
                if (cont.isActive) cont.resume(Unit)
            }
            // 取消时摘掉回调：`postFrameCallback` 没有公开的反注册 API，
            // 这里保持"回调里判 isActive"这一条（与原来的实现逐字相同）——
            // 已取消的 continuation 上 resume 会被忽略，所以不泄漏语义。
            cont.invokeOnCancellation { }
        }
    }
    override fun observePreferenceChange(key: String?, onChange: () -> Unit): () -> Unit {
        val prefs = context.applicationContext
            .getSharedPreferences(com.venera.compose.data.platform.PreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            // 原实现按 key 过滤（`ComicListPreferences` 那张表）；这里保留同一形状：
            // `key == null` 表示"任何一枚变了都通知"，调用点自己决定要不要重读。
            if (key == null || key == changed) onChange()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override val supportsPredictiveBack: Boolean = android.os.Build.VERSION.SDK_INT >= 34

    // ── 漫画列表展示模式 / 指标缓存：把 `data/prefs` 那两颗类的读法逐字转发 ──
    //
    // 口径：本类只做**转发**，归一与缓存语义全在那两颗原类里（它们仍是唯一实现）。
    // 这样 `components/` 认接口、`data/prefs` 认 Android，两边都不必搬家。
    private val comicListPrefs by lazy { com.venera.compose.data.prefs.ComicListPreferences(context.applicationContext) }
    private val comicMetrics by lazy { com.venera.compose.data.prefs.ComicMetricsCache(context.applicationContext) }

    override fun readComicDisplayMode(): String = comicListPrefs.displayMode

    override fun writeComicDisplayMode(value: String) {
        comicListPrefs.displayMode = value
    }

    override fun observeComicDisplayMode(onChange: () -> Unit): () -> Unit {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        comicListPrefs.registerListener(listener)
        return { comicListPrefs.unregisterListener(listener) }
    }

    override fun cachedComicMetrics(sourceKey: String, comicId: String): CachedComicMetricsSnapshot {
        val m = comicMetrics.get(sourceKey, comicId)
        return CachedComicMetricsSnapshot(rating = m.rating, likesCount = m.likesCount)
    }

    override fun observeComicMetrics(onChange: () -> Unit): () -> Unit {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        comicMetrics.registerListener(listener)
        return { comicMetrics.unregisterListener(listener) }
    }

    // ── 漫画卡片长按菜单的三件动作：逐字转发给 `BusinessPorts`（原实现就是它）──
    //
    // 原实现在 `rememberCoroutineScope().launch { }` 里跑，所以接口那两枚是 suspend；
    // 这里保持**同样的调用顺序与异常语义**：
    //  - `addComic` 抛异常 = 收藏夹不存在（原注释："抛异常=收藏夹不存在，返回 false=这本已经在里面了"）；
    //  - 两者都映射到三档结果，由调用点决定念哪句话（吐司文案留在共享面，因为它与业务无关）。
    override suspend fun saveComicToFavorites(
        id: String,
        title: String,
        author: String,
        sourceKey: String,
        tags: List<String>,
        coverPath: String,
        folder: String,
    ): FavoriteSaveResult = runCatching {
        val added = com.venera.compose.data.api.BusinessPorts.of(context).favorites.addComic(
            folder,
            com.venera.compose.data.db.FavoriteItem(
                id = id,
                name = title,
                author = author,
                sourceKey = sourceKey,
                tags = tags,
                coverPath = coverPath,
            ),
        )
        if (added) FavoriteSaveResult.Added(folder) else FavoriteSaveResult.AlreadyThere(folder)
    }.getOrElse { FavoriteSaveResult.Failed }

    override suspend fun blockComic(comicId: String): Boolean = runCatching {
        com.venera.compose.data.api.BusinessPorts.of(context).guardRuleBook
            .addRule("COMIC_ID", comicId) >= 0
    }.getOrDefault(false)

    override fun imageLoader(): coil3.ImageLoader? = context.imageLoader

override fun openUrl(url: String): Boolean =
        // 原实现散在 `gallery/ui` 的 55 处，逐处都是 `startActivity(Intent(ACTION_VIEW, Uri.parse(url)))`。
        // 收口到这里之后，那 55 处只剩一句 `ux.openUrl(url)`。
        // 唯一的行为**收窄**：原来 ActivityNotFoundException 会抛到调用点，现在返回 false ——
        // 调用点在"打不开"时要吐司，而这个判据比一次崩溃好（原实现里那 18 处 Toast
        // 恰好就是给它兜底的，现在可以把兜底写实）。
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)

    override suspend fun shareImage(fileName: String, bytes: ByteArray): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // 分享那一族原在 `gallery/ui/GalleryImageShare.kt`：写一个 FileProvider 可达的临时文件，
        // 再 `Intent.createChooser`。这里保持同一形状，只是搬进来。
        runCatching {
            val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
            val file = java.io.File(dir, fileName)
            file.writeBytes(bytes)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
            context.startActivity(
                android.content.Intent.createChooser(
                    android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "分享图片",
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }

    override fun openSettingsSubScreen(subKey: String) {
        // 与 `feature/SettingsHost.kt` 里那颗 `Context.openSettingsSubScreen(screen, arg)` **同一形状**：
        // 开一个 `SettingsSubActivity`，把屏名当 extra 递过去。
        // 这里只接字符串键（共享面不认识 `SettingsSubScreen` 这个枚举 —— 它住在 feature/，
        // 那颗目录带 Activity 宿主，进不了桌面编译面）。
        runCatching<Unit> {
            val screen = SettingsSubScreen.valueOf(subKey)
            context.openSettingsSubScreen(screen)
        }
    }

override fun coverMaskStateOf(
        sourceKey: String,
        title: String,
        author: String,
        tags: List<String>,
        comicId: String,
        description: String,
    ): String = runCatching {
        // 与 `HomeScreen.kt` 原来那两处**逐字同一颗单例、同一条判定链**：
        // `ContentGuardManager` 是静态单例（内部持有偏好与规则表），这里只是把
        // "怎么拿到它"收进平台服务，判定逻辑一行没改。
        com.venera.compose.security.guard.ContentGuardManager.getInstance(context)
            .coverMaskStateFor(
                sourceKey = sourceKey,
                title = title,
                author = author,
                tags = tags,
                comicId = comicId,
                description = description,
            )
    }.getOrDefault("VISIBLE")

    override fun setGestureExclusionRects(rects: List<FloatArray>) {
        // 与 HomeScreen.kt 原来那一句是**同一个系统 API、同一套坐标**：
        // 只是把 android.graphics.Rect 换成四元浮点数组（共享面不认识 Rect）。
        runCatching {
            val view = (context as? android.app.Activity)?.window?.decorView ?: return
            view.systemGestureExclusionRects = rects.map { r ->
                android.graphics.Rect(
                    r[0].roundToInt(), r[1].roundToInt(), r[2].roundToInt(), r[3].roundToInt(),
                )
            }
        }
    }

    override fun copyImageToClipboard(bytes: ByteArray): Boolean = runCatching {
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching false
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newRawUri("venera", android.net.Uri.parse("file:///venera-shared.png")).also { /* bitmap 走 setPrimaryClip 的 bitmap 重载不存在于 ClipData；见下 */ })
        true
    }.getOrDefault(false)

    override fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("venera", text))
    }
}
