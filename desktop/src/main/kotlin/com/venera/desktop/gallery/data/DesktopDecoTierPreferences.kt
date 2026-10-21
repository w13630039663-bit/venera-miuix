package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.data.platform.PreferenceKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 装饰档的**桌面自持**读写（批 116）。
 *
 * ## 为什么这一枚不塞进 `DesktopGalleryPreferences`
 *
 * 那一颗的键名逐枚要与对岸 `VeneraPreferences.kt` 同串（`DesktopGalleryPreferencesTest` ① 钉着），
 * 而装饰档是**桌面独有的概念** —— 手机侧没有 rail 那一列、也没有首页 hero 的装饰层，
 * 对岸从来没有这一枚。把它写成 `pref_gallery_*` 等于伪造一枚"对岸也有"的键，
 * 那比多一个类严重得多。所以这里用 `pref_desktop_` 前缀明说是桌面自持的，
 * 与 [DesktopComicReaderPreferences] 同一条形状（那一枚读的是对岸同名的键，这一枚没有对岸）。
 *
 * ## 存储文件仍是同一份
 *
 * `JsonKeyValueStore(PreferenceKeys.PREFS_NAME, paths)` —— 与那 12 枚偏好同一个
 * `prefs/venera_preferences.json`。多一枚键不得多一份文件，否则"重启还在"这件事会有两本账。
 *
 * ## 写侧两步：落盘 + 推流
 *
 * 与那八枚 setter 逐字同一形状。少第二步的后果是"存下来了，但屏上要重启才变"，
 * 少第一步的后果是"这一屏改得动、下一启动又回到默认档" —— 两种都是本仓零容忍的假开关。
 */
class DesktopDecoTierPreferences(paths: PathProvider) {

    private val store = JsonKeyValueStore(PreferenceKeys.PREFS_NAME, paths)

    private val tierState = MutableStateFlow(desktopDecoTierOf(store.getString(KEY_TIER, null)))

    /** 盘上那一档。取证种子顶过之后屏上用哪一档，由 `desktopDecoTierForScreen` 决定（它排得比这里靠前）。 */
    val decoTier: StateFlow<DesktopDecoTier> = tierState.asStateFlow()

    /** 选档：写盘 + 立刻出声，屏上那一排是即时生效的（没有"保存"那一步）。 */
    fun setDecoTier(tier: DesktopDecoTier) {
        store.put(KEY_TIER, tier.key)
        tierState.value = tier
        println("D_装饰档 写 键=${tier.key} 档=${tier.label}")
    }

    companion object {
        /** 桌面自持的键名（前缀明写 `pref_desktop_`，见类注释那条"为什么不在对岸那 12 枚里"）。 */
        const val KEY_TIER: String = "pref_desktop_deco_tier"
    }
}
