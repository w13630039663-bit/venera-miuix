package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider

/**
 * 「哪几颗源被停用」落在盘上的那一格（批 83）。
 *
 * ## 为什么这一份是桌面本地而不是共享表（现量过两份存储，不是凭印象）
 *
 * Android 的 `comic_source` 表首次运行**播种五条写死的默认源**（`ComicSourceDao.kt:41-47`：
 * copymanga / picacg / mangadex / ehentai / nhentai），而桌面列表的真值是**随包那 34 颗 js**
 * （`EngineAssets.listSourceKeys`）。两者不是一件事：那张表是"手机上装过哪些源"的元数据
 * （图标、排序、config_json），这一份是"这台机器上哪几颗不参与选源"。并成一颗等于把 A 站的
 * 凭据发给 B 站 ⇒ **刻意不同轨**，并且把这句话写进读数里，别让它看起来像漏了同步。
 *
 * 存储件复用既有的 [JsonKeyValueStore]（`prefs/venera_desktop_sources.json`）：它的三条行为判据
 * （文件不存在=空表、半截 JSON 抛而不当"没配置"、写走临时文件再整体替换）正是这一格要的，
 * 自己另开一条写文件的路只会少掉那三条（`File.renameTo` 那一课就是这条路）。
 *
 * ⚠️ `public` 是**被包名决定的**，不是随便挑的：集成方 `com.venera.desktop.VeneraDesktop` 要把
 * 它交给 `DesktopComicEngineConfig`（那颗也是 public），而 Kotlin 不许 public 函数暴露 internal 参数类型。
 * 这一层其余的器件（`DesktopSourceCatalog` / `DesktopSourceSelection`）仍是 `internal`。
 */
class DesktopSourceState(paths: PathProvider) {

    private val store = JsonKeyValueStore(PREFS_NAME, paths)

    /** 停用的那些源脚本 key；全新数据 = 空表（一颗都没停用），不抛。 */
    fun disabled(): Set<String> = store.getStringSet(KEY_DISABLED)

    /**
     * 整份交回而不是增量记：空集也要真写回一份空数组。
     *
     * ⚠️ 这里不许写成 `if (keys.isEmpty()) store.remove(KEY) else store.put(...)` —— 删键与写空数组
     * 今天读回来都是空表，看着等价，但"用户主动全开"与"这份文件从来没写过"是两件事；
     * 而 `remove` 走的是另一条落盘分支，将来谁改默认档就会只改一处。
     */
    fun setDisabled(keys: Set<String>) {
        store.put(KEY_DISABLED, keys.sorted().toSet())
    }

    companion object {
        /** 文件名自证是桌面本地那一份（不是共享表）。 */
        const val PREFS_NAME = "venera_desktop_sources"
        private const val KEY_DISABLED = "disabled_sources"

        /** stdout 那一行：停用几颗、清单几颗、以及**这一份只管桌面**那句口径。 */
        fun reading(disabled: Set<String>, total: Int): String =
            "D_源 停用=${disabled.size} 清单=$total 口径=桌面本地（与共享表 comic_source 刻意不同轨）"
    }
}
