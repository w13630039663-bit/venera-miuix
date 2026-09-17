package com.venera.compose.source.explore

/**
 * 应用层的「通用标签」映射层 —— 纯辅助，**不参与源的分类体系**。
 *
 * 硬性约束（对应用户的核心要求）：
 *  1. 绝不能覆盖 / 修改 / 删除源自己的原生 Tag —— 通用标签只是一组**额外的**快捷入口。
 *  2. 通用标签不写回任何源；点击时只作为关键词搜索转发。
 *  3. 某源一个通用标签都映射不上时，整块 UI 隐藏，不显示空分组。
 *
 * 数据结构：
 *   Source -> native_categories / native_tags / native_sort_modes / capabilities
 *   App    -> optional_unified_tags          （就是本文件）
 */
enum class UnifiedTag(
    val label: String,
    /** 该通用标签在多数源里的常见写法；仅用于提示，不做强制归一。 */
    val aliases: List<String>,
) {
    LONG("长篇", listOf("长篇", "long", "Long")),
    SHORT("短篇", listOf("短篇", "short", "Short")),
    COLOR("彩色", listOf("全彩", "彩色", "full color", "fullcolor", "Full Color")),
    MONO("黑白", listOf("黑白", "monochrome", "Monochrome")),
    DOUJIN("同人", listOf("同人", "doujinshi", "Doujinshi")),
    ;

    companion object {
        /**
         * 判断某个源的原生条目是否「可能」命中该通用标签。
         *
         * 刻意保持**极轻量**：只在应用层做别名包含匹配，不建立权威映射表，
         * 也不因为匹配不上就隐藏源的原生 Tag。命中只是让用户多一个快捷入口。
         */
        fun matches(tag: UnifiedTag, nativeLabel: String): Boolean {
            val normalized = nativeLabel.trim().lowercase()
            if (normalized.isEmpty()) return false
            return tag.aliases.any { alias ->
                val a = alias.lowercase()
                normalized == a || normalized.contains(a)
            }
        }
    }
}

/**
 * 通用标签在一份源数据上的可用性。
 *
 * @param available 该源原生标签里能匹配上的通用标签（可能为空 -> UI 隐藏整块）
 * @param isFallbackOnly true 表示该源没有任何原生分类体系，通用标签只是搜索降级入口
 */
data class UnifiedTagAvailability(
    val available: List<UnifiedTag>,
    val isFallbackOnly: Boolean = false,
) {
    val isEmpty: Boolean get() = available.isEmpty()
}

/**
 * 从源的原生分类条目里推导出「通用标签」的可用集合。
 *
 * 注意：这里的输入**只用于判断可用性**，源的 [SourceExploration.nativeSections] 不会被改动。
 */
fun unifiedTagsFor(exploration: SourceExploration): UnifiedTagAvailability {
    val nativeLabels = exploration.nativeSections
        .flatMap { section -> section.items.map { it.label } }

    val available = UnifiedTag.entries.filter { tag ->
        nativeLabels.any { UnifiedTag.matches(tag, it) }
    }

    return UnifiedTagAvailability(
        available = available,
        // 源完全没有分类体系时，通用标签仍可作为跨源搜索的兜底入口。
        isFallbackOnly = exploration.nativeSections.isEmpty(),
    )
}
