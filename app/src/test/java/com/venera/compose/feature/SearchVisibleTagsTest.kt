package com.venera.compose.feature

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * L1 折叠口径：只合字面同义，语义同义一律保留（详见 venera-tag-multilang-plan.md §4.1）。
 * 折叠键只影响"显示几枚药丸"，存活下来的 raw 才是发给源的词 —— 所以这里断言的是 raw 文本本身。
 */
class SearchVisibleTagsTest {

    @Test fun collapsesCaseAndWhitespaceVariants() = assertEquals(
        listOf("Full Color"),
        searchVisibleTags(listOf("Full Color", "full  color", "FULL COLOR"), emptyList()),
    )

    @Test fun keepsNamespaceQualifiedSurvivor() = assertEquals(
        listOf("language:chinese"),
        searchVisibleTags(listOf("chinese", "language:chinese"), emptyList()),
    )

    /** 反向顺序也必须留带命名空间的那个，否则结果会随源返回顺序变化。 */
    @Test fun survivorIsOrderIndependent() = assertEquals(
        listOf("female:lolicon"),
        searchVisibleTags(listOf("female:lolicon", "lolicon"), emptyList()),
    )

    @Test fun doesNotMergeSemanticSiblings() = assertEquals(
        listOf("chinese", "translated"),
        searchVisibleTags(listOf("chinese", "translated"), emptyList()),
    )

    /** 真机回归：`東方: 靈夢` 这类"前缀: 值"形态，不同前缀不得互相吞并。 */
    @Test fun differentNamespacesNeverMerge() = assertEquals(
        listOf("female:dog", "parody:dog"),
        searchVisibleTags(listOf("female:dog", "parody:dog"), emptyList()),
    )

    @Test fun bareYieldsToEveryQualifiedVariant() = assertEquals(
        listOf("東方: 愛麗絲", "角色: 愛麗絲"),
        searchVisibleTags(listOf("愛麗絲", "東方: 愛麗絲", "角色: 愛麗絲"), emptyList()),
    )

    @Test fun excludesByConceptKey() = assertEquals(
        listOf("schoolgirl"),
        searchVisibleTags(
            tags = listOf("Female:Loli", "schoolgirl"),
            exclude = listOf("female:  loli"),
        ),
    )

    @Test fun appliesTranslatorToSurvivors() = assertEquals(
        listOf("女性 (female:lolicon)"),
        searchVisibleTags(listOf("lolicon", "female:lolicon"), emptyList()) {
            mapOf("female:lolicon" to "女性 (female:lolicon)")[it] ?: it
        },
    )
}
