package com.venera.compose.gallery.domain

/**
 * 一枚**能当标题用的**标签：译名 + 站方档位。
 *
 * 档位必须跟着译名一起走：`GalleryCardTitle` 要靠它分「作品」与「角色」的先后，
 * 以及回退时「画师」与「通用」的先后。如果只传一张 `名字 → 译名` 的表，那一层就没法知道
 * 哪枚是哪一档 —— 于是"作品优先"这条判据只能退化成"按标签顺序取第一个"，
 * 遇到「角色排在作品前面」的图就会摆成「雷电将军（原神）· 原神」。
 */
data class GalleryTitleTag(
    /** 显示用的名字：词典有中文就是中文，没有就回原词（人名/罗马字照抄那类）。 */
    val label: String,
    /**
     * 站方档位：通用 `GalleryTagCategory.GENERAL`(0) / 画师 `ARTIST`(1) /
     * 作品 `COPYRIGHT`(3) / 角色 `CHARACTER`(4)。**元数据(5) 不进这张表**（理由见 `GalleryCardTitle` 判据 4）。
     */
    val category: Int,
)

/**
 * 画廊卡片那一行标题的判据。
 *
 * ## 为什么标题只能这样来（不许编）
 *
 * 两站的 **post JSON 都没有标题字段**（yande.re 44 个键里没有、Gelbooru 只有一串平铺 `tags`，
 * 实测记录见 `gallery-tag-category-translation-and-nav-2026-09.md` §0.3）。
 * 所以"这张画叫什么"这件事，站方只在一个地方给了：**标签**。
 * 而离线词典（`assets/gallery_tags_*.sqlite`，ffdkj 那份 MIT 表）**带档位**，
 * 其中 3 是作品、4 是角色 —— 那正是参考图里「原神 · 雷电将军」那一行的来路。
 * 实测：`genshin_impact → 3 原神`、`raiden_shogun → 4 雷电将军（原神）`、
 * `touhou → 3 东方Project`、`blue_archive → 3 蔚蓝档案`。
 *
 * ## 判据
 *
 * 1. **作品优先、角色次之**：`作品 · 角色` 是最像"这一张画的是什么"的读法
 *    （参考图那行也是这个序）。只有其中一类时就摆那一类。
 * 2. **同类里取标签顺序的第一枚**（两站的 tags 都是字母序 ⇒ 结果确定、不随哈希飘）。
 * 3. **角色译名尾部的同作品括号要去掉**：词典给 `raiden_shogun` 的译名是 `雷电将军（原神）`，
 *    而作品那枚已经写了「原神」——不去掉就会读成「原神 · 雷电将军（原神）」。
 *    只在括号内容与作品名**相同**时才去；不认识的括号保留
 *    （`初音未来（VOCALOID）` 里的 `VOCALOID` 是有效区分信息，删了反而少一层）。
 * 4. **作品与角色都没有 → 回退到画师名，再回退到通用标签的译名**（批次 M · M6）。
 *    这一条**推翻**了原先的"一个都没有就 null，不拿通用标签或画师名顶包"。
 *    用户 2026-09-30：「部分图片没有标题就不要完全空着，显示角色和其他 tag 了」。
 *    原判据担心的是"通用标签会摆出「掀起上衣」这种不成句的东西"——那个担心现在被
 *    "整行空着更糟"盖过去：屏上一整片没有标题的卡读起来像坏了，而不像"这张没标题"。
 *    但**次序不许换**（作品/角色 > 画师 > 通用），且**元数据档（5）永不进回退**：
 *    `highres` / `translated` 是这张图的技术属性，摆到标题行上就成了"这张画叫高分辨率"。
 * 5. **四档都没有 → `null`**，卡片就不摆标题行（横滑行里摆一行空文字会把封面高度拉开参差）。
 *
 * 纯函数、无 Android 依赖 —— 本项目没有 Robolectric，判据不放在这一层就永远没人钉。
 */
object GalleryCardTitle {

    /** 作品那一档。与 `GalleryTagCategory.COPYRIGHT` / `galleryTagCategoryLabel(3)` 同一个编号。 */
    private const val COPYRIGHT = 3

    /** 角色那一档。 */
    private const val CHARACTER = 4

    /** 画师那一档（回退第一档）。 */
    private const val ARTIST = 1

    /** 通用那一档（回退第二档，垫底）。 */
    private const val GENERAL = 0

    /**
     * @param tagList 条目自带的标签（站方给的顺序）。
     * @param labels 这次查询的产物：`标签名(lowercase) → 译名 + 档位`。
     *   含通用/画师/作品/角色四档（由 `GalleryTagDictionary.titleTags` 保证）；
     *   这一层**不重复判档位**，免得两处口径分叉 —— 档位不在这四档里的条目会被忽略
     *   （元数据那一档就是这么被挡在外面的）。
     * @return 拼好的标题；四档都没有时 `null`。
     */
    fun titleOf(tagList: List<String>, labels: Map<String, GalleryTitleTag>): String? {
        if (tagList.isEmpty() || labels.isEmpty()) return null
        val copyright = firstOf(tagList, labels, COPYRIGHT)
        val character = firstOf(tagList, labels, CHARACTER)
        if (copyright != null && character != null) {
            // 角色的括号尾巴里若与作品同名，去掉它（判据 3）。
            return "$copyright · ${stripSameTitleSuffix(character, copyright)}"
        }
        (copyright ?: character)?.let { return it }
        // 回退两档（判据 4）。次序是拍板定的，不许对调：画师名是"这张谁画的"，
        // 通用标签只是"这张画里有什么"，前者更像标题。
        firstOf(tagList, labels, ARTIST)?.let { return it }
        return firstOf(tagList, labels, GENERAL)
    }

    /** 按 [tagList] 的顺序取第一枚**档位等于 [category]** 的译名。 */
    private fun firstOf(tagList: List<String>, labels: Map<String, GalleryTitleTag>, category: Int): String? =
        tagList.firstOrNull { labels[it.lowercase()]?.category == category }
            ?.let { labels.getValue(it.lowercase()).label }

    /**
     * 去掉 `雷电将军（原神）` 尾部那个与作品名重复的括号。
     *
     * 中文全角与英文半角括号都认（词典两种都有）。**只在内容与 [title] 完全相同（忽略大小写与空白）时**
     * 才去掉；`初音未来（VOCALOID）` 这种不匹配的原样保留 —— 那是有效信息。
     */
    private fun stripSameTitleSuffix(label: String, title: String): String {
        val trimmed = label.trimEnd()
        if (!trimmed.endsWith("）") && !trimmed.endsWith(")")) return label
        val open = trimmed.lastIndexOfAny(charArrayOf('（', '('))
        if (open <= 0) return label
        val inner = trimmed.substring(open + 1, trimmed.length - 1).trim()
        if (!inner.equals(title.trim(), ignoreCase = true)) return label
        // 去掉括号段后再 trim：`雷电将军 （原神）` 这种中间带空格的写法也不会留一个尾空格。
        return trimmed.substring(0, open).trimEnd().takeIf { it.isNotEmpty() } ?: label
    }
}
