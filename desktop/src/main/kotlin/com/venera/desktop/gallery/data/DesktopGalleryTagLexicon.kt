package com.venera.desktop.gallery.data

import com.venera.compose.gallery.data.GalleryTagCategories
import com.venera.compose.gallery.data.GalleryTagLexicon
import com.venera.compose.gallery.domain.GalleryTitleTag
import com.venera.desktop.platform.DesktopLogger

/**
 * 桌面侧的标签词典口 —— **一半能真跑、一半是有意降级**，两半都要说得出名字。
 *
 * ## 真跑的那一枚
 *
 * [fetchTagCategories] 走 [GalleryTagCategories]（S1 后它只吃 `HttpEngine`），
 * 与 Android 侧同一颗类、同一条缓存、同一个"读不出来交 null 而不是空表"的判据。
 *
 * ## 降级的三枚，以及为什么"空"在这里是诚实的
 *
 * [artistNames] / [translations] / [titleTags] 的实体在 `GalleryTagDictionary`，而那颗上不了
 * 桌面编译面：词典库是 **assets 复制出来的真实路径**（`Context.assets`），且走
 * `data.platform.android.openReadOnlySqlDatabase`。桌面包里今天没有那份 `gallery_tags_*.sqlite`。
 *
 * 这三枚的返回形状刻意与 **Android 侧"词典打不开"那一条路逐字相同**，而不是另造一套：
 * - [titleTags] 交 **null** —— 那颗类的原口径就是"null = 库没打开，空表 = 这批名字里确实没有"
 *   （`GalleryTagDictionary.kt:135-137`），所以 null 是这里唯一不说谎的答案；
 * - [artistNames] / [translations] 交空集 —— 界面上表现成"标签不分桶、不显示译文"，
 *   与 Android 侧库坏了那一次一模一样。
 *
 * ⚠️ 这里没有"失败"可交（契约这三枚不带 `Result`），所以**必须出声**：首次命中打一条
 * [DesktopGalleryFailureLog]，说明是"词典未随包分发"而不是"这批标签就是没有画师名"。
 * 不出声的那一半就是本仓最忌的静默交错 —— 空集与"查过了没有"在屏上长得一样。
 */
class DesktopGalleryTagLexicon(private val handle: DesktopGalleryHandle) : GalleryTagLexicon {

    override suspend fun artistNames(names: List<String>): Set<String> {
        announceDictionaryAbsent()
        return emptySet()
    }

    override suspend fun translations(names: List<String>): Map<String, String> {
        announceDictionaryAbsent()
        return emptyMap()
    }

    /** null 而不是空表：调用方按"库没打开"处理，不显示标题档。 */
    override suspend fun titleTags(names: List<String>): Map<String, GalleryTitleTag>? {
        announceDictionaryAbsent()
        return null
    }

    override suspend fun fetchTagCategories(pageUrl: String): Map<String, Int>? =
        GalleryTagCategories.getInstance(handle.engine, DesktopLogger).fetch(pageUrl)

    private fun announceDictionaryAbsent() {
        DesktopGalleryFailureLog.warn(TAG, DICT_ABSENT)
    }

    private companion object {
        const val TAG = "DesktopGalleryTagLexicon"
        const val DICT_ABSENT =
            "画廊标签词典未随桌面包分发，标签不分桶与译文这一档今天没有；" +
                "它表现成\"这批标签查不到\"，但真实成因是词典缺席（Android 侧走 assets 里那份 sqlite）"
    }
}
