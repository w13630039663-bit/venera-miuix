package com.venera.compose.data.tags

/**
 * 题材归一化：把各源写法不一的**同一个题材**（`lolicon` / `蘿莉` / `萝莉` / `loli`）
 * 合到同一个规范名上。纯 JVM 类 —— 字典与繁简转换都由构造参数注入，因此可单测。
 *
 * 五级管线，逐条对齐 `master` 分支 `lib/pages/stats_page.dart:1122-1211` 的 `_TagNormalizer`：
 *
 * 1. **namespace 排除**（31 项）：作者 / 状态 / 语言 / 社团这类不是题材，直接丢；
 * 2. **合并字典**：EhTagTranslation 六类题材库按 [TOPIC_NAMESPACES] 的**固定顺序**合成
 *    `英文键 → 中文规范名`；
 * 3. **别名表**：字典没收录的同义写法（`loli → lolicon`）；
 * 4. **繁转简**：字典未命中时按字级表兜底（见 [ChineseVariantConverter]）；
 * 5. **格式词排除**（24 项）：同人 / 短篇 / 画集 / AI生成 这类元信息值不进偏好统计。
 *
 * 归一化**只在聚合时做**，入库仍存原始 `namespace:tag` —— 这样将来字典更新可以直接重算，
 * 不必清库（原版同样的取舍）。
 *
 * @param dictionary 见 [buildDictionary]，键已按 [normKey] 规范化
 * @param toSimplified 繁体→简体；不注入则退化为恒等（简繁桶会并列）
 * @param hasTraditional 是否含繁体字；不注入则一律走「原样兜底」
 */
class TagNormalizer(
    dictionary: Map<String, String>,
    private val toSimplified: (String) -> String = { it },
    private val hasTraditional: (String) -> Boolean = { false },
) {

    private val dict = dictionary

    /**
     * 返回规范名；`null` = 该标签不参与题材统计。
     *
     * ⚠️ **没有 namespace 的裸标签一律返回 null**（`idx <= 0` 即弃）。这是原版口径而不是
     * 缺陷：裸串分不清是题材还是分类词（jm 只给「分类词」、picacg 给 `Tags`），
     * 收进来会把「已完结」这种词当题材。别当 bug 修。
     */
    fun normalize(plainTag: String): String? {
        val idx = plainTag.indexOf(':')
        if (idx <= 0) return null
        val namespace = plainTag.substring(0, idx).trim().lowercase()
        val tag = plainTag.substring(idx + 1).trim()
        if (tag.isEmpty() || namespace in EXCLUDED_NAMESPACES) return null

        val stripped = normKey(tag)
        val key = ALIASES[stripped] ?: stripped
        // 先查去尾 s 的 key，再查不去 s 的原样 key —— 有些词本来就合法以 s 结尾。
        var result = dict[key] ?: dict[tag.lowercase()]
        if (result == null) {
            result = if (hasTraditional(tag)) toSimplified(tag) else tag
        }
        // 字典值可能本身是繁体（tags_tw），比对前先归一，否则排除表形同虚设。
        if (toSimplified(result).lowercase() in EXCLUDED_TAG_VALUES) return null
        return result
    }

    companion object {
        /** 参与聚合的六类题材库，**顺序即先到先得的优先级**（与原版 addAll 顺序一致）。 */
        val TOPIC_NAMESPACES: List<String> =
            listOf("female", "male", "mixed", "other", "parody", "character")

        /**
         * 作者类 namespace。
         *
         * 对「题材统计」而言要排除（画师不是题材），但「画师统计」恰恰要靠它 ——
         * 有些源不把作者写进 `author` 字段，只给 `Author:xxx` / `作者:xxx` 这类标签。
         * 提成单一来源，排除表与 [resolveAuthor] 共用，避免两处各写一份而飘。
         */
        val AUTHOR_NAMESPACES: Set<String> = setOf(
            "作者", "author", "authors", "artists", "artist", "画师", "插画",
        )

        /** 与「题材」无关的 namespace。清单抄自原版，而原版注释称其来自 20 个真实源 JS 的 tags 键名普查。 */
        val EXCLUDED_NAMESPACES: Set<String> = AUTHOR_NAMESPACES + setOf(
            // 状态 / 元信息类
            "状态", "status", "连载中", "更新", "update", "date", "时间", "热度",
            "view", "work", "misc",
            // 语言 / 来源分类（值为 同人/短篇/AI生成 等格式词，不是题材）
            "语言", "language", "categories", "category", "分类", "分類",
            // 社团 / 上传者
            "group", "社团", "uploader", "上传", "上传者", "cosplayer", "reclass",
        )

        /**
         * 解析一本漫画的画师：优先 `author` 字段，为空时回落到作者类标签的 value。
         *
         * 真机反馈「画师一栏统计不到」的根因：部分源（含禁漫）不把作者写进 author 字段，
         * 只给 `Author:xxx` / `作者:xxx` 标签；只读 author 就会整本为空。
         * namespace 比对一律先 lowercase —— 各源大小写不统一（`Author:` / `artists:`）。
         */
        fun resolveAuthor(author: String, tags: List<String>): String? {
            author.trim().takeIf { it.isNotEmpty() }?.let { return it }
            tags.forEach { tag ->
                val namespace = tag.substringBefore(':', missingDelimiterValue = "").trim().lowercase()
                if (namespace.isNotEmpty() && namespace in AUTHOR_NAMESPACES) {
                    tag.substringAfter(':', missingDelimiterValue = "").trim()
                        .takeIf { it.isNotEmpty() }
                        ?.let { return it }
                }
            }
            return null
        }

        /** 字典未覆盖的同义写法（规范化后的英文 key → 规范英文 key）。 */
        val ALIASES: Map<String, String> = mapOf(
            "loli" to "lolicon",
            "shota" to "shotacon",
        )

        /** 格式 / 元信息类标签值：不是题材。比对前先 lower + 繁转简。 */
        val EXCLUDED_TAG_VALUES: Set<String> = setOf(
            "同人", "同人志", "短篇", "短篇集", "单行本", "画集", "画册", "cg", "cg集",
            "ai生成", "ai绘图", "ai-generated", "漫画", "杂志", "其他", "unknown",
            "系列", "连载中", "已完结", "完结", "全彩", "彩色", "无修", "无修正",
            // 语言系：禁漫等源把语言塞在兜底 namespace（Tag:）下，namespace 级排除拦不住，
            // 只能在值级拦 —— 否则「中文」会当成一个题材进偏好榜和首页推荐。
            "中文", "汉语", "国语", "繁体中文", "简体中文", "日语", "日文", "日本語",
            "英语", "英文", "english", "chinese", "japanese", "korean",
            "韩语", "韩文", "한국어", "translated",
            // 汉化归属：是来源信息，不是题材
            "汉化", "中文汉化", "个人汉化", "官方汉化", "禁漫汉化", "禁漫汉化组",
        )

        /**
         * 字典键规范化：小写 + 去尾部 `s`。
         *
         * `reclass` 例外 —— 去掉 s 会变成 `recla`，且它本身就是被排除的 namespace，
         * 这条特例是原版 `TagsTranslation` 既有语义的一部分，照搬。
         */
        internal fun normKey(tag: String): String {
            var t = tag.trim().lowercase()
            if (!t.endsWith("reclass") && t.endsWith("s") && t.length > 2) {
                t = t.substring(0, t.length - 1)
            }
            return t
        }

        /**
         * 把「英文键 → 中文规范名」的词条列表折成查找表。
         *
         * 同一个英文键在两个命名空间下译法不同时**保留先来的那个**，所以 [entries] 的
         * 顺序必须是按 [TOPIC_NAMESPACES] 给的（female 优先于 character）。
         */
        fun buildDictionary(entries: List<Pair<String, String>>): Map<String, String> {
            val dict = HashMap<String, String>(entries.size * 2)
            for ((key, value) in entries) {
                dict.putIfAbsent(normKey(key), value)
            }
            return dict
        }
    }
}
