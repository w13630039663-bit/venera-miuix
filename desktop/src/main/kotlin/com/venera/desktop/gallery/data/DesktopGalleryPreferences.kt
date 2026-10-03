package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.gallery.data.GalleryPreferences
import com.venera.compose.gallery.domain.GalleryAnimatedMode
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryPreloadMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GalleryViewerBackdrop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 画廊那 12 枚显示/播放档在桌面侧的读数（getter-only，与 [GalleryPreferences] 同宽）。
 *
 * ## 形状照 Android 侧，不是照"桌面想要"
 *
 * `VeneraPreferences` 是在**构造时读一次**、之后每枚走 `setGalleryXxx` 更新那条流。桌面今天
 * 没有写侧（W5 那颗设置页没搬过来），所以这里也是"构造时读一次 + 之后不变"：
 * 改成每次访问现读，屏上的档就会在没有人改它的时候自己变 —— 那是 Android 侧不存在的行为。
 *
 * ## 键名与默认档两头分开记账，这件事由用例钉
 *
 * Android 侧那 12 枚键名是 `VeneraPreferences` 里的 **private** 常量（不在共享面上），桌面这边
 * 只能重述一遍字符串。重述就是会漂的那种重复，所以 `DesktopGalleryPreferencesTest` 拿文本
 * 回核：这里写的每一串键名与每一枚默认档，都要能在 `VeneraPreferences.kt` 里找到同一句。
 *
 * ## 存储文件与 Android 落在不同文件上，这是刻意的
 *
 * 文件名仍引 [PreferenceKeys.PREFS_NAME]（与 `DesktopDatabasePorts` 里那三枚偏好同一个 store），
 * 但 Android 那侧是 `venera_preferences.xml`、桌面是 `prefs/venera_preferences.json` ——
 * **名字一致不等于数据共享**（跨端自动迁移不在本轮范围，已拍板）。
 *
 * 两枚在桌面档不参与：[GalleryPreferences.galleryHideTopBar] / [galleryHideBottomBar]
 * 直接给 `false` 常量（桌面没有那两条栏，P5 已拍桌面走侧栏）；给常量而不是给一条能写却没人读的键，
 * 是为了不留"看起来能配其实恒假"的开关。
 */
class DesktopGalleryPreferences(paths: PathProvider) : GalleryPreferences {

    private val store = JsonKeyValueStore(PreferenceKeys.PREFS_NAME, paths)

    override val galleryColumnMode: StateFlow<GalleryColumnMode> =
        readEnum(KEY_COLUMNS, GalleryColumnMode.AUTO).asStateFlow()

    override val galleryPreviewQuality: StateFlow<GalleryPreviewQuality> =
        readEnum(KEY_PREVIEW, GalleryPreviewQuality.PREVIEW).asStateFlow()

    override val galleryKeepScreenOn: StateFlow<Boolean> =
        MutableStateFlow(store.getBoolean(KEY_KEEP_SCREEN_ON, true)).asStateFlow()

    override val galleryVolumeKeyTurn: StateFlow<Boolean> =
        MutableStateFlow(store.getBoolean(KEY_VOLUME_KEY, false)).asStateFlow()

    override val galleryAutoPlaySec: StateFlow<Int> =
        MutableStateFlow(store.getInt(KEY_AUTOPLAY_SEC, 0)).asStateFlow()

    override val galleryPreload: StateFlow<GalleryPreloadMode> =
        readEnum(KEY_PRELOAD, GalleryPreloadMode.NEXT).asStateFlow()

    /**
     * 桌面取 **ALWAYS** 档：`WIFI_ONLY` 的判据来自 `GalleryConnectivity`，而那颗在桌面排除清单里
     * （它吃 `ConnectivityManager`）。把 unmetered 硬编码成"永远不动"等于桌面永远静帧，
     * 硬编码成"永远动"又是往判据里塞一个假读数 —— 所以这里改的是**档位**而不是那条布尔：
     * 桌面不做流量计费判断，等同 ALWAYS。桌面今天没有这一档的设置入口（写侧属 W5），
     * 所以这一枚在桌面档是常量语义，不是"能配"。
     */
    override val galleryAnimated: StateFlow<GalleryAnimatedMode> =
        readEnum(KEY_ANIMATED, GalleryAnimatedMode.ALWAYS).asStateFlow()

    override val galleryBackdrop: StateFlow<GalleryViewerBackdrop> =
        readEnum(KEY_BACKDROP, GalleryViewerBackdrop.GLASS).asStateFlow()

    override val galleryBlockAi: StateFlow<Boolean> =
        MutableStateFlow(store.getBoolean(KEY_BLOCK_AI, false)).asStateFlow()

    override val galleryAiBadge: StateFlow<Boolean> =
        MutableStateFlow(store.getBoolean(KEY_AI_BADGE, true)).asStateFlow()

    override val galleryHideTopBar: StateFlow<Boolean> = ALWAYS_FALSE

    override val galleryHideBottomBar: StateFlow<Boolean> = ALWAYS_FALSE

    /** 解码口径逐字照 `VeneraPreferences.readEnum`：存枚举名，认不出来就退默认档。 */
    private inline fun <reified T : Enum<T>> readEnum(key: String, fallback: T) =
        MutableStateFlow(enumValues<T>().firstOrNull { it.name == store.getString(key, null) } ?: fallback)

    companion object {
        private val ALWAYS_FALSE: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

        // 键名与默认档抄自 app/src/main/java/com/venera/compose/data/prefs/VeneraPreferences.kt:668-682
        // 与 :173-320；改这里之前先改那里，否则 DesktopGalleryPreferencesTest 会红。
        const val KEY_COLUMNS = "pref_gallery_columns"
        const val KEY_PREVIEW = "pref_gallery_preview"
        const val KEY_KEEP_SCREEN_ON = "pref_gallery_keep_screen_on"
        const val KEY_VOLUME_KEY = "pref_gallery_volume_key"
        const val KEY_AUTOPLAY_SEC = "pref_gallery_autoplay_sec"
        const val KEY_PRELOAD = "pref_gallery_preload"
        const val KEY_ANIMATED = "pref_gallery_animated"
        const val KEY_BACKDROP = "pref_gallery_backdrop"
        const val KEY_BLOCK_AI = "pref_gallery_block_ai"
        const val KEY_AI_BADGE = "pref_gallery_ai_badge"
        // 那两枚 hide_top_bar / hide_bottom_bar 的键名**不在这里重述**：桌面这两枚给的是常量
        // （见类注释里 P5 那条），既然一个字节都不读，把键名抄过来就是一颗会漂的死键。
    }
}
