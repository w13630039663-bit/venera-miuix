package com.venera.compose.data.platform

/**
 * 共享偏好**存储名**与三颗收藏键名的**唯一事实源**。
 *
 * 为什么要这颗文件：Android 侧 `data/prefs/VeneraPreferences` 与桌面侧
 * `desktop/.../DesktopDatabasePorts` 都要读写这四颗名字。此前桌面侧是逐字抄的私有字面量，
 * "两端一致"只有注释级证据 —— 任何一端改名，另一端的用例照样绿，症状是桌面写的键
 * Android 永远读不到。现在两端引用同一批常量，一致性由编译期保证（谁改名两端一起变）。
 *
 * 2026-10-03 起这张表**扩张到全部 13 个存储文件**：审计实测原先有 10 处把存储名写成内联字面量
 * （`AndroidKeyValueStore(context, "venera_cookies")` 那一类），另有三处各自私有 `const PREFS_NAME`。
 * 内联字面量的后果不是难看，是**改错一个字就静默丢一整个模块的存量偏好**（新名字落到一个空文件上，
 * 老文件还在原地没人读），而且没有任何一端会报错。现在名字集中在这里，
 * 由 `data/platform/PreferenceStorageNamesTest` 逐字钉住既有值（改名即红）。
 *
 * 例外一条：`venera_guard_prefs` 的写入点在**冻结文件** `security/guard/ContentGuardManager` 里，
 * 本轮不牵它进来（`FREEZE-STATEMENT.md:18` 只允许修实际 Bug，而这里今天没有可指认的故障），
 * 只把它的值登记在下面并由化石用例盯着 —— 谁改了那两行，用例会红。
 *
 * 本文件零 `android.*`：`data/platform` 目录整体在桌面 srcDir 编译面里，别往这儿加任何平台类型。
 *
 * 口径边界：共享的是**名字**，不是数据。两端今天落在不同的文件上 —— Android 是
 * SharedPreferences `venera_preferences.xml`，桌面是 `prefs/venera_preferences.json`，
 * 互不读取（并库不在此处，本文件只保证不再出现"键名漂移"这一类洞）。
 */
object PreferenceKeys {

    /** prefs 存储名。Android 侧成 `.xml`，桌面侧由同包的 JsonKeyValueStore 成 `<name>.json`。 */
    const val PREFS_NAME = "venera_preferences"

    /** 新收藏插入位置（Android 侧缺省 `"start"`）。 */
    const val KEY_NEW_FAVORITE_ADD_TO = "pref_new_favorite_add_to"

    /** 读完移动到夹子哪一端（`"start"` / `"end"` / 未设 = 不动）。 */
    const val KEY_MOVE_FAVORITE_AFTER_READ = "pref_move_favorite_after_read"

    /** 追更指向的收藏夹名（未设 = 未开启追更）。 */
    const val KEY_FOLLOW_UPDATES_FOLDER = "pref_follow_updates_folder"

    // ── 存储文件名（一个名字 = 一个 SharedPreferences 文件）──

    /** 已安装漫画源的注册表 meta（`source/ComicSourceManager`）。 */
    const val PREFS_SOURCES = "venera_sources"

    /** 内容屏蔽与分级守卫规则（写入点在冻结文件 `security/guard/ContentGuardManager`）。 */
    const val PREFS_GUARD = "venera_guard_prefs"

    /** host → 过盾 UA 的绑定表（`data/network/UserAgentPolicy`）。 */
    const val PREFS_UA_POLICY = "venera_ua_policy"

    /** 持久化 Cookie 罐（`data/network/PersistentCookieJar`）。 */
    const val PREFS_COOKIES = "venera_cookies"

    /** 漫画评分/收藏数缓存（`data/prefs/ComicMetricsCache`）。 */
    const val PREFS_COMIC_METRICS = "comic_source_metrics"

    /** 列表布局档（`data/prefs/ComicListPreferences`，与阅读器设置分开是刻意的）。 */
    const val PREFS_COMIC_LIST_PRESENTATION = "comic_list_presentation"

    /** 首页推荐"退出前那批数据"的落盘（`feature/HomeViewModel`，冷启动首屏铺它）。 */
    const val PREFS_HOME_RECOMMEND_CACHE = "home_recommend_cache"

    /** 画廊搜索历史（`gallery/ui/GallerySearchViewModel`）。 */
    const val PREFS_GALLERY_SEARCH = "venera_gallery_search"

    /** WebDAV 配置（`sync/WebDavSyncManager`）。 */
    const val PREFS_WEBDAV = "venera_webdav_prefs"

    /** 拷贝漫画源自己的设置（`source/copymanga/CopyMangaSource`）。 */
    const val PREFS_COPY_MANGA = "venera_source_copy_manga"

    /** Gelbooru API 凭据（`gallery/data/GelbooruAccount`）。 */
    const val PREFS_GELBOORU_ACCOUNT = "venera_gelbooru_account"

    /** SauceNAO API key（`gallery/data/SauceNaoAccount`）。 */
    const val PREFS_SAUCENAO_ACCOUNT = "venera_saucenao_account"
}
