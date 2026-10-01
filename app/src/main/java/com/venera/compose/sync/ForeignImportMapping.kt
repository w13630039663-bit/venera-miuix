package com.venera.compose.sync

/**
 * 外部归档（官方 Venera / PicaComic）里「来源」怎么读 —— **纯 JVM 判据，可单测**。
 *
 * 这一层单独抽出来，理由与 [FavoriteBackupRows] 相同：真正会**静默出错**的地方就是它。
 * 把 type 读成错的来源不会抛异常，只会导入一批「打开就报错／根本不显示」的条目。
 *
 * ## 两边的 `type` 列含义完全不同
 *
 * - **PicaComic**：内置源用**小整数枚举**（`FavoriteType` / `HistoryType` 类），
 *   只有自定义源才用 `sourceKey.hashCode`（Dart 哈希）。
 * - **官方 Venera**：一律 `ComicType(key.hashCode)`，即 Dart 哈希，没有小整数。
 *
 * ## 而且两边的枚举值彼此对不上（上游就是这么写的，不是笔误）
 *
 * | type | PicaComic 收藏 | PicaComic 历史 | 官方 Venera |
 * |------|---------------|---------------|------------|
 * | 0 | picacg | picacg | — |
 * | 1 | ehentai | ehentai | — |
 * | 2 | jm | jm | — |
 * | 3 | hitomi | hitomi | — |
 * | 4 | htmanga→wnacg | htmanga→wnacg | — |
 * | 5 | （无） | **nhentai** | — |
 * | 6 | **nhentai** | （无） | — |
 *
 * 口径直接取自官方 Venera 的 `utils/data.dart: importPicaData()` —— 它就是这个生态里
 * 「读 PicaComic 归档」的权威实现，不要另起炉灶。
 *
 * 认不出的数值（自定义源的 Dart 哈希）返回 `null`，由调用方**跳过并计数**；
 * 上游对这种情况的兜底是把数字原样塞进自己的库，那是 Dart 哈希世界的做法，
 * 在本仓会落成一堆来源不明的条目，不能照搬。
 */
internal object ForeignImportMapping {

    /**
     * 随包内置的漫画源 key，与 `app/src/main/assets/sources/index.json` 一致
     * （单测 [ForeignImportMappingTest.builtInKeysMatchSourceIndex] 守着这条一致性）。
     *
     * 用途只有一个：当归档里**没带** `comic_source/` 目录时，靠这批 key 也能把
     * 官方归档的 Dart 哈希反查回来。之所以能从归档里拿，是因为官方 Venera 的
     * 源都落在 `<数据目录>/comic_source/<key>.js`，导出时会一并打进归档。
     */
    val BUILT_IN_SOURCE_KEYS: List<String> = listOf(
        "Komiic", "ManHuaGui", "baozi", "ccc", "comic_walker", "comick", "copy_manga",
        "ehentai", "gelbooru", "goda", "happy", "hcomic", "hitomi", "hot_manga", "ikmmh",
        "jcomic", "jm", "kavita", "komga", "lanraragi", "manga_dex", "manhuaren",
        "manwaba", "mh1234", "mh18", "mxs", "mycomic", "nhentai", "picacg",
        "shonen_jump_plus", "wnacg", "ykmh", "zaimanhua",
    )

    /**
     * 源 key 归一化：`htmanga` 已改名为 `wnacg`（上游注释「换名字了, 绅士漫画」），
     * `importPicaData` 在 `folder_sync.key` 与 `image_favorites` 两处都做了这个转换。
     * 本仓必须同样处理，否则绑定好的网络收藏夹会指向一个不存在的源。
     */
    fun normalizeSourceKey(key: String): String =
        if (key.equals("htmanga", ignoreCase = true)) "wnacg" else key

    /** PicaComic 收藏表的 `type` 小整数 → sourceKey；认不出返回 null。 */
    fun picaFavoriteSourceKey(type: Int): String? = when (type) {
        0 -> "picacg"
        1 -> "ehentai"
        2 -> "jm"
        3 -> "hitomi"
        4 -> "wnacg"
        6 -> "nhentai"
        else -> null
    }

    /** PicaComic 历史表的 `type` 小整数 → sourceKey；认不出返回 null。注意 5 与 6 的差异。 */
    fun picaHistorySourceKey(type: Int): String? = when (type) {
        0 -> "picacg"
        1 -> "ehentai"
        2 -> "jm"
        3 -> "hitomi"
        4 -> "wnacg"
        5 -> "nhentai"
        else -> null
    }

    /**
     * Dart 哈希 → sourceKey 的反查表。
     *
     * [candidateKeys] 应当包含「归档 `comic_source/` 目录里的源文件名」+「本仓内置源」，
     * 前者覆盖用户自装的第三方源（官方 Venera 把源存成 `<key>.js`，文件名即 key）。
     * 同一哈希撞上多个 key 时保留先到者 —— 碰撞概率极低，且不做提示也比乱猜好。
     */
    fun dartHashSourceIndex(candidateKeys: Collection<String>): Map<Int, String> {
        val index = HashMap<Int, String>(candidateKeys.size * 2)
        for (key in candidateKeys) {
            if (key.isBlank()) continue
            index.putIfAbsent(DartStringHash.hash(key), key)
        }
        return index
    }

    /**
     * 从一份 Venera 源文件的**内容**里读出它的 key。
     *
     * ⚠️ 不能拿文件名当 key，本仓源目录里就有两个反例：`komiic.js` 的 key 是 `Komiic`
     * （只是大小写不同），`copy_manga_multi_accounts.js` 的 key 是 `copy_manga`
     * （跟文件名毫无关系）。认错 key 的代价是该源下的收藏全部变成"来源未知"。
     *
     * 判据是源文件顶层的 `key = "..."` 声明（`ComicSourceParser` 也是靠这一行给
     * `ComicSource.key` 赋值）。两处细节：
     * - `(?m)` + `^[ \t]*` 只认行首（可带缩进）的声明，避开函数体里的
     *   `let key = '...'` —— picacg.js 的签名函数里就有一个同名的局部变量；
     * - 取第一个匹配，类字段声明都在文件最前面。
     */
    fun sourceKeyFromSourceFile(text: String): String? =
        SOURCE_KEY_DECLARATION.find(text)?.groupValues?.get(1)

    private val SOURCE_KEY_DECLARATION =
        Regex("""(?m)^[ \t]*key[ \t]*=[ \t]*["']([^"']+)["']""")

    /**
     * 1 基 → 0 基的章节／页码换算。
     *
     * **两边语义不同，这是必须转换的地方**：官方 Venera 的 `History.ep` / `.page`
     * 注释写明是 *1-based*，PicaComic 同族（`0` 还被用作「没有阅读位置记录」的哨兵）；
     * 而本仓 `comic_history.last_chapter_index` / `.last_page_index` 是 **0 基**
     * （`ComicDetailScreen` 里 `lastChapterIndex == actualIdx` 直接和 0 基的
     * `forEachIndexed` 下标比较，显示页码时再 `+ 1`）。
     *
     * 上游 `importPicaData` 不需要转换是因为它两边都是 1 基；我们不做就会**整体偏移一章一页**，
     * 而且不会报错，只表现为「继续阅读跳到了错误的位置」。
     */
    fun toZeroBasedIndex(oneBased: Int): Int = (oneBased - 1).coerceAtLeast(0)
}
