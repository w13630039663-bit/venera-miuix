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
 * ⚠️ 那句"否则 `DesktopGalleryPreferencesTest` 会红"原本是一张**开不出来的支票** —— 这颗测试类
 * 此前从来没有存在过（2026-10-04 第四批才补上）。注释指向一颗不存在的守卫，比没注释更坏：
 * 读的人会以为漂了会被抓到。
 *
 * 本批起 `galleryBlockAi` 是桌面**第一个真被消费的档**（墙上那一关，见手册 §18），其余 11 枚
 * 今天仍零消费 —— 所以"桌面设置页"这件事的瓶颈不在控件面，而在**没有消费点**（§18.1）。
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

    // ---- 下面这八枚是桌面**今天真有消费点**的档，所以它们有写侧 ----
    //
    // 写侧两步走（与 Android 侧 `VeneraPreferences.setGalleryXxx` 逐字同一形状）：
    // 先落盘、再把那条流推到新值。少第二步的后果是"存下来了，但屏上要重启才变" ——
    // 用户按下去看不见反应，那就是假开关。用例 ⑧⑨ 一条钉流、一条钉盘。
    //
    // ⚠️ 别给其余两枚补 setter：它们今天没人读（那两枚属于桌面还没有的大图页键位与常亮；
    // 另两枚 hide 给常量）。批 61 给 `backdrop` 补了 setter —— 那一枚是**先有消费点**（大图那一屏那层底）才动的。
    // 多出来的写口不会让任何现有测试变红，它只会在哪天被接进界面时变成"能改但没人读"。
    // 这条由 `DesktopGalleryPreferencesTest` ⑩ 反射钉着：**只有这八枚**能有 `setGallery*`。

    private val columnModeState = readEnum(KEY_COLUMNS, GalleryColumnMode.AUTO)
    private val previewQualityState = readEnum(KEY_PREVIEW, GalleryPreviewQuality.PREVIEW)
    private val preloadState = readEnum(KEY_PRELOAD, GalleryPreloadMode.NEXT)
    private val blockAiState = MutableStateFlow(store.getBoolean(KEY_BLOCK_AI, false))
    private val aiBadgeState = MutableStateFlow(store.getBoolean(KEY_AI_BADGE, true))

    override val galleryColumnMode: StateFlow<GalleryColumnMode> = columnModeState.asStateFlow()

    override val galleryPreviewQuality: StateFlow<GalleryPreviewQuality> = previewQualityState.asStateFlow()

    override val galleryKeepScreenOn: StateFlow<Boolean> =
        MutableStateFlow(store.getBoolean(KEY_KEEP_SCREEN_ON, true)).asStateFlow()

    override val galleryVolumeKeyTurn: StateFlow<Boolean> =
        MutableStateFlow(store.getBoolean(KEY_VOLUME_KEY, false)).asStateFlow()

    /**
     * 「到点自己翻下一张」那一档（批 46）。与那两枚开关一样留了**可写的 backing**：
     * 屏上那排档位按钮按下去要立刻看见反应（写盘 + 推流两步），只写盘不推流就是假开关。
     */
    private val autoPlaySecState = MutableStateFlow(store.getInt(KEY_AUTOPLAY_SEC, 0))

    override val galleryAutoPlaySec: StateFlow<Int> = autoPlaySecState.asStateFlow()

    override val galleryPreload: StateFlow<GalleryPreloadMode> = preloadState.asStateFlow()

    /**
     * 桌面取 **ALWAYS** 档：`WIFI_ONLY` 的判据来自 `GalleryConnectivity`，而那颗在桌面排除清单里
     * （它吃 `ConnectivityManager`）。把 unmetered 硬编码成"永远不动"等于桌面永远静帧，
     * 硬编码成"永远动"又是往判据里塞一个假读数 —— 所以这里改的是**档位**而不是那条布尔：
     * 桌面不做流量计费判断，那一档在桌面上等同 ALWAYS（屏上那一行明说了这件事）。
     * 批 51 给了消费点（大图那一屏那颗播放器），批 52 补上写口与屏上那一行。
     */
    private val animatedState = readEnum(KEY_ANIMATED, GalleryAnimatedMode.ALWAYS)

    override val galleryAnimated: StateFlow<GalleryAnimatedMode> = animatedState.asStateFlow()

    /**
     * 大图那一层的那枚底（批 61 起**改得动**：从前它挂在屏上点名缺席那一区）。
     *
     * backing 与别的档同一形状：写盘 + 推流两步都要，少一步就是"重启就忘"或"改了不动"。
     */
    private val backdropState = readEnum(KEY_BACKDROP, GalleryViewerBackdrop.GLASS)

    override val galleryBackdrop: StateFlow<GalleryViewerBackdrop> = backdropState.asStateFlow()

    override val galleryBlockAi: StateFlow<Boolean> = blockAiState.asStateFlow()

    override val galleryAiBadge: StateFlow<Boolean> = aiBadgeState.asStateFlow()

    /** 列数档。写盘 + 推流两步，理由见上面那段。 */
    fun setGalleryColumnMode(mode: GalleryColumnMode) {
        store.put(KEY_COLUMNS, mode.name)
        columnModeState.value = mode
    }

    fun setGalleryPreviewQuality(quality: GalleryPreviewQuality) {
        store.put(KEY_PREVIEW, quality.name)
        previewQualityState.value = quality
    }

    fun setGalleryBlockAi(on: Boolean) {
        store.put(KEY_BLOCK_AI, on)
        blockAiState.value = on
    }

    fun setGalleryAiBadge(on: Boolean) {
        store.put(KEY_AI_BADGE, on)
        aiBadgeState.value = on
    }

    /**
     * 连播间隔。夹在 0..30 **逐字照对岸那颗 setter**（`VeneraPreferences.setGalleryAutoPlaySec`：
     * 「滑条那一头已经给了区间，这里再兜一道，防别处写进一个负数把连播变成每 0 秒翻一张」）。
     * 桌面这头摆的是档位按钮，那道兜底也不许省：写口的另一半是对岸与被人手工改过的盘上档。
     */
    fun setGalleryAutoPlaySec(seconds: Int) {
        val clamped = seconds.coerceIn(0, 30)
        store.put(KEY_AUTOPLAY_SEC, clamped)
        autoPlaySecState.value = clamped
    }

    /** 预取档。写盘 + 推流两步，理由同 [setGalleryColumnMode]：只推流不落盘是"重启就忘"，只落盘不推流是"改了不动"。 */
    fun setGalleryPreload(mode: GalleryPreloadMode) {
        store.put(KEY_PRELOAD, mode.name)
        preloadState.value = mode
    }

    /**
     * 动图档（批 52）。同一形状：写盘 + 推流。
     *
     * 读侧那一路是 `GalleryMotion.animates(mode, unmetered = true)` —— 桌面量不出"哪种网不算流量"，
     * 所以 `WIFI_ONLY` 走的是"照播"那一支，而屏上那一行明说了这件事（`DesktopGallerySettingsSpecTest` ⑧ 钉着）。
     */
    fun setGalleryAnimated(mode: GalleryAnimatedMode) {
        store.put(KEY_ANIMATED, mode.name)
        animatedState.value = mode
    }

    /** 大图那一层的底（批 61）。写盘 + 推流两步，理由同 [setGalleryColumnMode]。 */
    fun setGalleryBackdrop(backdrop: GalleryViewerBackdrop) {
        store.put(KEY_BACKDROP, backdrop.name)
        backdropState.value = backdrop
    }

    override val galleryHideTopBar: StateFlow<Boolean> = ALWAYS_FALSE

    override val galleryHideBottomBar: StateFlow<Boolean> = ALWAYS_FALSE

    /**
     * 「画成水彩」那枚 Beta 档：桌面**没有那个入口**（它长在 Android 大图页的「关于这张图」面板里，
     * 桌面今天没有那一行），所以给常量而不是给一条能写却没人读的键 —— 与上面那两条 hide 档同一形。
     * 哪天桌面接了那一行，就把它换成 `store.getBoolean(那一枚键, false)` 并按 `DesktopGalleryPreferencesTest` ①② 补回核。
     */
    override val galleryWatercolorBeta: StateFlow<Boolean> = ALWAYS_FALSE

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
