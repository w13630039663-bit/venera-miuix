package com.venera.compose.data.platform

/**
 * 共享偏好文件名与三颗收藏键名的**唯一事实源**。
 *
 * 为什么要这颗文件：Android 侧 `data/prefs/VeneraPreferences` 与桌面侧
 * `desktop/.../DesktopDatabasePorts` 都要读写这四颗名字。此前桌面侧是逐字抄的私有字面量，
 * "两端一致"只有注释级证据 —— 任何一端改名，另一端的用例照样绿，症状是桌面写的键
 * Android 永远读不到。现在两端引用同一批常量，一致性由编译期保证（谁改名两端一起变）。
 *
 * 本文件零 `android.*`：`data/platform` 目录整体在桌面 srcDir 编译面里，别往这儿加任何平台类型。
 *
 * 口径边界：共享的是**名字**，不是数据。两端今天落在不同的文件上 —— Android 是
 * SharedPreferences `venera_preferences.xml`，桌面是 `prefs/venera_preferences.json`，
 * 互不读取（并库不在此处，本文件只保证不再出现"键名漂移"这一类洞）。
 */
object PreferenceKeys {

    /** prefs 存储名。Android 侧成 `.xml`，桌面侧由 [JsonKeyValueStore] 成 `<name>.json`。 */
    const val PREFS_NAME = "venera_preferences"

    /** 新收藏插入位置（Android 侧缺省 `"start"`）。 */
    const val KEY_NEW_FAVORITE_ADD_TO = "pref_new_favorite_add_to"

    /** 读完移动到夹子哪一端（`"start"` / `"end"` / 未设 = 不动）。 */
    const val KEY_MOVE_FAVORITE_AFTER_READ = "pref_move_favorite_after_read"

    /** 追更指向的收藏夹名（未设 = 未开启追更）。 */
    const val KEY_FOLLOW_UPDATES_FOLDER = "pref_follow_updates_folder"
}
