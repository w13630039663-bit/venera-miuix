package com.venera.desktop.gallery.ui

import com.venera.compose.data.platform.KeyValueStore
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.gallery.domain.decodeGallerySearchHistory
import com.venera.compose.gallery.domain.encodeGallerySearchHistory
import com.venera.compose.gallery.domain.pushGallerySearchHistory

/**
 * 搜索那一屏的**查询历史**（第十三批 批 9）。
 * *
 * ## 这一颗件里一颗新判据都没有
 *
 * 三枚判据早就在共享面 `gallery/domain/GallerySearch.kt`：
 * - `decodeGallerySearchHistory` —— v2 行格式 + **旧档（一行一站）不改写也能读**，读完按 canonical 键现场并一次；
 * - `encodeGallerySearchHistory` —— 写回时才升版本，封顶 `HISTORY_MAX`；
 * - `pushGallerySearchHistory` —— 同键**并所有**旧条目的站、新的排最前。
 *
 * 桌面从前只欠"什么时候写、在哪一栏读"那一段接线，所以这里的全部职责就是把那三枚转交过去，
 * 外加两枚**这一屏特有**的判断（下面那两条 `//` 单独标出来）。重述一份行格式的后果是
 * "手机上搜过的历史在桌面上看不见，而两端各自看起来都正常工作" —— 那是本仓最忌的静默分叉。
 *
 * ## 两枚这一屏特有的判断
 *
 * 1. **没发车就不记**（[desktopSearchHistoryEntry]）。`GallerySearchEntry` 对空腿是 `require` 抛，
 *    而桌面确实存在"一站都没发出去"的那一轮（没配 Gelbooru 账号而来源档只有它）——
 *    那一轮屏上是空态，历史里不该冒出一条"点开来没有腿"的记录。
 * 2. **记的是这一轮真发过的腿**，不是来源档里的那几站：未配账号的那一站没搜过，
 *    把它记进站名就是把**我们自己的闸**说成**用户搜过**（对岸同一条判断在 `saveHistory` 那一行）。
 *
 * ## 清空为什么是 remove 而不是写空表
 *
 * 对岸 `clearHistory` 走的是 `prefs.remove("history")`。写成编码后的空表在盘上是
 * 「v2」那一枚版本哨兵 —— 两种读法今天都返回空表，但**明天不一样**：
 * 哪天读侧改成"没有这个键就带一份默认词进来"，写了哨兵的那一份会被当成"用户清过"，
 * 而删掉键的那一份才是"从来没存过"。跟着对岸走，不自己定语义。
 */

/** 盘上那一枚键名。**与对岸同一枚**（判据 ① 拿 Android 源码逐字回核，不靠这里抄一遍）。 */
internal const val DESKTOP_SEARCH_HISTORY_KEY: String = "history"

/**
 * 那份键值件。桌面这颗 `DesktopStoreFactory` 与对岸 `GalleryPorts.of(app).stores` 是同一条路，
 * 所以手机与桌面读写的**是同一个文件**（在本机同一份 dataRoot 下）。
 */
internal fun desktopHistoryStore(ports: GalleryPorts): KeyValueStore =
    ports.stores.open(PreferenceKeys.PREFS_GALLERY_SEARCH)

/** 读一次：没有这个键就是空表（不是 null —— 调用方拿到 null 迟早会写出一份到处漂的兜底）。 */
internal fun desktopHistoryRead(store: KeyValueStore): List<GallerySearchEntry> =
    decodeGallerySearchHistory(store.getString(DESKTOP_SEARCH_HISTORY_KEY, null))

internal fun desktopHistoryWrite(store: KeyValueStore, entries: List<GallerySearchEntry>) {
    store.put(DESKTOP_SEARCH_HISTORY_KEY, encodeGallerySearchHistory(entries))
}

/**
 * 落一条并交回新表。[entry] 为 null 表示"这一轮不该记"（见文件头那条判断 1）——
 * 那种时候**一个字节都不写**，而不是写一条空站列表的记录进去。
 */
internal fun desktopHistoryPush(
    store: KeyValueStore,
    current: List<GallerySearchEntry>,
    entry: GallerySearchEntry?,
): List<GallerySearchEntry> {
    if (entry == null) return current
    val next = pushGallerySearchHistory(current, entry)
    desktopHistoryWrite(store, next)
    return next
}

/** 删一条（那一排每条右侧那枚动作）。对岸 `removeHistory` 同形：交回滤掉之后的表并写盘。 */
internal fun desktopHistoryRemoveOne(
    store: KeyValueStore,
    current: List<GallerySearchEntry>,
    entry: GallerySearchEntry,
): List<GallerySearchEntry> {
    val next = current.filterNot { it == entry }
    desktopHistoryWrite(store, next)
    return next
}

/** 整清：删掉那个键，交回空表（语义的来由见文件头那一节）。 */
internal fun desktopHistoryClearAll(store: KeyValueStore): List<GallerySearchEntry> {
    store.remove(DESKTOP_SEARCH_HISTORY_KEY)
    return emptyList()
}

/**
 * 这一轮该记的那一条：**真发过车的腿** + 用户打的那串（裁两端）。
 *
 * 返回 null 的两档都不是"没数据"而是"这一轮没发生过"：一站都没发出去、或者串是空的。
 * ⚠️ 不许在这里改成"记来源档那几站" —— 见文件头那条判断 2。
 */
internal fun desktopSearchHistoryEntry(
    dispatched: List<GallerySite>,
    query: String,
): GallerySearchEntry? {
    if (dispatched.isEmpty() || query.isBlank()) return null
    return GallerySearchEntry(dispatched.toSet(), query.trim())
}

/**
 * 点一条历史该切到哪一档：多站 ⇒ 「全部」，单站 ⇒ 那一站。
 *
 * 一条在两站都搜过的历史点开只回一站，等于把他上次看到的结果少摆一半 ——
 * 而对岸把这条判据写在 `applyHistory` 里（判据 ④ 逐字回核那行）。
 */
internal fun desktopHistorySourceOf(entry: GallerySearchEntry): GallerySearchSource =
    if (entry.sites.size > 1) GallerySearchSource.ALL else GallerySearchSource.single(entry.site)

/**
 * 点一条历史要装的**两样**条件。屏上那第三步（另起一轮）不在这里，它是界面自己的动作 ——
 * 但对岸记过它的死局：只装条件不另起一轮，连点同一条会被取数那颗跳过守卫整笔挡掉，
 * 于是"点了没反应"。所以判据 ⑤ 钉的是界面那一格三行都在。
 *
 * 装回的是 [GallerySearchEntry.filters] 重拼的串而不是原串：与对岸同一个来源，
 * 而且 `-` 开头那种排除项在下一次解析时不会被切成两枚（搜索屏那枚候选行走的同一条路）。
 */
internal class DesktopHistoryLanding(val query: String, val source: GallerySearchSource)

internal fun desktopHistoryLanding(entry: GallerySearchEntry): DesktopHistoryLanding =
    DesktopHistoryLanding(entry.filters.joinToString(" ") { it.token }, desktopHistorySourceOf(entry))
