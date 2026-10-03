package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.KeyValueStore
import com.venera.compose.gallery.data.GalleryStoreFactory

/**
 * 桌面侧的具名键值存储。
 *
 * 形状照 `AndroidGalleryStoreFactory`：名字由调用点原样递进来，这里**不改名也不加前缀** ——
 * 存储名就是文件名，改名等于换数据文件（那一档今天只有画廊搜索缓存在用，
 * 见 `PreferenceKeys.PREFS_GALLERY_SEARCH`）。
 *
 * 与 Android 的差别只在实现载体：那边 `AndroidKeyValueStore`（SharedPreferences），
 * 这边 [JsonKeyValueStore]（`data/platform`，本仓共享面）。今天落不同文件，不假装共享数据。
 */
class DesktopStoreFactory(private val handle: DesktopGalleryHandle) : GalleryStoreFactory {
    override fun open(name: String): KeyValueStore = JsonKeyValueStore(name, handle.paths)
}
