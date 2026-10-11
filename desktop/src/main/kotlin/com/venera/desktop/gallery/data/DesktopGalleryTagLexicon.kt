package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.GalleryTagCategories
import com.venera.compose.gallery.data.GalleryTagLexicon
import com.venera.compose.gallery.domain.GalleryTitleTag
import com.venera.desktop.platform.DesktopLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 桌面侧的标签词典口（批 40 起**四枚全真**）。
 *
 * ## 真跑的四枚与各自的来源
 *
 * - [fetchTagCategories] 走 [GalleryTagCategories]（S1 后它只吃 `HttpEngine`），与 Android 侧同一颗类、
 *   同一条缓存、同一个"读不出来交 null 而不是空表"的判据；
 * - [artistNames] / [translations] / [titleTags] 走 [DesktopGalleryTagDictionary]：
 *   包内那份 `gallery_tags_<行数>.sqlite` 复制成真实路径后**只读**打开，
 *   读法与档位判据仍在共享的 `data/db/TagDictionaryStore` 里。
 *
 * ## 契约不带 `Result` 的那两枚：降级必须出声，而且念得出是哪一态
 *
 * 批 40 到批 77 之间，这两枚是 `… ?: emptySet()` / `… ?: emptyMap()` —— 契约要一个非空的东西，
 * 于是"我们的故障"被念成"这批名字里没有"，而**日志里一个字都没有**。批 78 把 elvis 那一形拆成三态分支：
 * 命中交命中，`Miss` 那一支**先念一句**（`DesktopGalleryFailureLog` 那颗首次出声、后续计数，
 * 免得墙上一刷几千行把这句埋成噪声），再交契约要求的降级值。
 *
 * ⚠️ 出声住在契约口这三枚里，不住在 [artistNamesRead] 那一枚（面板问的是后者）：
 * 面板那一格把态递上了屏上那句兜底（`DesktopGalleryDetailPolicy.kt`），所以同一次降级在屏上已有名字，
 * 再念一遍就是把同一件事说两遍。
 *
 * ## [artistNamesRead] 为什么单独存在
 *
 * 契约那枚只能交空集，屏上那句"离线词典里也没有这一串标签"因此在词典没读出来时是假话。
 * 这一枚交回**整个三态**，面板据此念"这个包里也没带离线词典"/"离线词典这次没读出来"。
 * 它是桌面侧自持的一枚，不进 `app/` 那份契约（受保域，一个字都不动）。
 */
class DesktopGalleryTagLexicon(private val handle: DesktopGalleryHandle) : GalleryTagLexicon {

    private val dictionary = DesktopGalleryTagDictionary(handle.paths)

    /** 画师档那一问的**完整三态**：面板用它把兜底那句念成能支持的那一句。 */
    internal suspend fun artistNamesRead(names: List<String>): DesktopDictRead<Set<String>> =
        withContext(Dispatchers.IO) { dictionary.artistNames(names) }

    /** 契约的画师档口：没读到交空集（契约不带 null），但先念一句是哪一态。 */
    override suspend fun artistNames(names: List<String>): Set<String> = when (val read = artistNamesRead(names)) {
        is DesktopDictRead.Hit -> read.value
        is DesktopDictRead.Miss -> {
            DesktopGalleryFailureLog.warn(
                TAG,
                desktopDictionaryDegradedReading(DESKTOP_DICT_MEMBER_ARTISTS, read.why),
            )
            emptySet()
        }
    }

    /** 契约的译名口：没读到交空映象（契约不带 null），但先念一句是哪一态；命中的键恒小写。 */
    override suspend fun translations(names: List<String>): Map<String, String> =
        withContext(Dispatchers.IO) { dictionary.translations(names) }.let { read ->
            when (read) {
                is DesktopDictRead.Hit -> read.value
                is DesktopDictRead.Miss -> {
                    DesktopGalleryFailureLog.warn(
                        TAG,
                        desktopDictionaryDegradedReading(DESKTOP_DICT_MEMBER_TRANSLATIONS, read.why),
                    )
                    emptyMap()
                }
            }
        }

    /** 契约的标题档口：`null` = 没读到（包里没有或读不出，那一句已出声），空表 = 这批名字里确实没有能当标题的标签。 */
    override suspend fun titleTags(names: List<String>): Map<String, GalleryTitleTag>? =
        withContext(Dispatchers.IO) { dictionary.titleTags(names) }.let { read ->
            when (read) {
                is DesktopDictRead.Hit -> read.value
                is DesktopDictRead.Miss -> {
                    DesktopGalleryFailureLog.warn(
                        TAG,
                        desktopDictionaryDegradedReading(DESKTOP_DICT_MEMBER_TITLES, read.why),
                    )
                    null
                }
            }
        }

    override suspend fun fetchTagCategories(pageUrl: String): Map<String, Int>? =
        GalleryTagCategories.getInstance(handle.engine, DesktopLogger).fetch(pageUrl)

    private companion object {
        const val TAG = "DesktopGalleryTagLexicon"
    }
}
