package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.KeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.data.platform.PreferenceKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 阅读那一屏的偏好（第八十七批）。
 *
 * ## 只有一枚键：模式
 *
 * 读的是对岸**同一枚键名** —— 键名与默认值都不是这一颗定的，`DesktopReaderPagedSeedTest` ①②
 * 从 `data/prefs/VeneraPreferences.kt` 的源码文本里**现取**再比（对岸改了写法，红的是判据）。
 * ⚠️ 同轨的是**键**，不是那份存储：Android 侧落在应用目录的 `venera_preferences.xml`，
 * 桌面侧由同包的 `JsonKeyValueStore` 落在**桌面数据根**下的 `prefs/venera_preferences.json`
 * （手册 §16 那条数据根）。两边各读各的根，所以手机上选的那一档桌面看不见 —— 别把这一颗
 * 写成"跨端同步"，那是一条没有读数撑着的话。
 *
 * `pref_volume_key_turn`（音量键翻页）那一枚**本批不接**：音量键在桌面上走的是另一条通路，
 * 还没量过它到不到得了这一层（见 `docs/rounds/desktop-reader-paging-2026-10-11.md` §1.3），
 * 先接上就是一枚没人按得动的假开关。那一枚与键位同批。
 *
 * ## 为什么这里读盘上原值而不在写入时校验档位表
 *
 * `DesktopReaderMode.fromKey` 自己就负责归一（认不出的串落回纵向那一档），那张别名表逐字照对岸、
 * 由回核尺子钉着；桌面这一颗不再列一遍"合法值清单"—— 清单进了就是第三张表。
 * 写口那边不同：写进去的串要**与屏上那一排同源**（`mode.key`），所以它收枚举而不是收串。
 *
 * ## `public` 是被包名决定的，不是随便挑的
 *
 * 它随 `DesktopComicEngineConfig`（集成方在另一个包，那颗必须是 public）一起交过来，
 * 而 Kotlin 不许 public 的形参表里出现 internal 的类型 —— 所以这一颗只能跟着抬成 public。
 * 批 86 那颗历史件刻意留在 internal（它走的是应用根的形参，那一层本来就在同一个包），
 * 两处的差别是**编译面**而不是口味。构造仍是 internal：外部只能通过 `getInstance` 拿，
 * 免得长出第二颗缓存同一份 JSON 的件。
 */
class DesktopComicReaderPreferences internal constructor(private val store: KeyValueStore) {

    private val _readingMode = MutableStateFlow(DesktopReaderMode.fromKey(store.getString(READING_MODE_KEY, READING_MODE_FALLBACK)))

    /** 盘上那一档。取证种子顶过之后屏上用哪一档，由 `desktopReaderModeOf` 那一颗决定（它排得比这里靠前）。 */
    val readingMode: StateFlow<DesktopReaderMode> = _readingMode.asStateFlow()

    /** 选档：写盘 + 立刻出声，屏上那一排是即时生效的（没有"保存"那一步）。 */
    fun setReadingMode(mode: DesktopReaderMode) {
        store.put(READING_MODE_KEY, mode.key)
        _readingMode.value = mode
        println("D_阅读档 写 键=${mode.key} 档=${mode.label}")
    }

    companion object {

        @Volatile
        private var instance: DesktopComicReaderPreferences? = null

        @Volatile
        private var instanceRoot: String? = null

        /** 同一个数据根只建一颗：两颗各自缓存同一份 JSON 就会漂出"两个当前档"。 */
        fun getInstance(paths: PathProvider): DesktopComicReaderPreferences {
            val root = paths.dataRoot.absolutePath
            instance?.takeIf { instanceRoot == root }?.let { return it }
            synchronized(this) {
                instance?.takeIf { instanceRoot == root }?.let { return it }
                val created = DesktopComicReaderPreferences(JsonKeyValueStore(PreferenceKeys.PREFS_NAME, paths))
                instance = created
                instanceRoot = root
                return created
            }
        }
    }
}

/** 对岸那枚键（`VeneraPreferences.KEY_DEFAULT_READING_MODE` = `pref_reading_mode`），由判据回读对岸源码核对。 */
private const val READING_MODE_KEY = "pref_reading_mode"

/**
 * 对岸那句默认值（`VeneraPreferences.kt:72` 的 `getString(KEY_DEFAULT_READING_MODE, "HORIZONTAL_LTR")`
 * —— 手册行 164 那批按用户点名「播放器设置成默认美漫，左至右」改判过，桌面跟着同轨）。
 *
 * ⚠️ 这一枚**从那张表取**而不是在这里写死串：档位的键与名字只有 `DesktopReaderMode` 一个出处
 * （同批尺子的 ③ 会咬写死的档位串），而"桌面默认与对岸同一档"由 ② 现取对岸那串、两边各归一再比。
 */
private val READING_MODE_FALLBACK = DesktopReaderMode.HORIZONTAL_LTR.key
