package com.venera.compose.data.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 题材归一化管线的行为契约。字典与繁简转换都注入假实现，因此这些用例**不依赖 asset**，
 * 跑的是纯 JVM —— 也正是选「归一化只在聚合时做」这条设计的理由。
 */
class TagNormalizerTest {

    private val dict = TagNormalizer.buildDictionary(
        listOf(
            "lolicon" to "萝莉",
            "school girls" to "学生",
            "big breasts" to "巨乳",
            "shotacon" to "正太",
            "glasses" to "眼镜",
        )
    )

    private val simplifiedTable = mapOf("蘿莉" to "萝莉", "同人誌" to "同人志")

    /** 只在注入的假简繁表里出现的字，用来证明「繁转简」这一步真的被走到了。 */
    private fun fakeSimplify(text: String): String = simplifiedTable[text] ?: text
    private fun fakeHasTraditional(text: String): Boolean = simplifiedTable.containsKey(text)

    private fun normalizer(
        dictionary: Map<String, String> = dict,
        withTraditional: Boolean = true,
    ) = TagNormalizer(
        dictionary = dictionary,
        toSimplified = if (withTraditional) { tag -> fakeSimplify(tag) } else { tag -> tag },
        hasTraditional = if (withTraditional) { tag -> fakeHasTraditional(tag) } else { _ -> false },
    )

    @Test
    fun tagsWithoutNamespaceAreDropped() {
        // 裸串分不清是题材还是分类词（jm 只给分类词），原版口径是宁缺毋滥。
        assertNull(normalizer().normalize("校服"))
        assertNull(normalizer().normalize(":lolicon"))
        assertEquals("萝莉", normalizer().normalize("female:lolicon"))
    }

    @Test
    fun nonTopicNamespacesNeverCountAsTopics() {
        val n = normalizer()
        assertNull(n.normalize("author:nakamura"))
        assertNull(n.normalize("状态:连载中"))
        assertNull(n.normalize("Categories: Doujinshi"))
        assertNull(n.normalize("group:circle"))
        // 大小写与前后空白都不影响 namespace 判定
        assertNull(n.normalize("  AUTHOR : nakamura "))
    }

    @Test
    fun dictionaryMergeKeepsFirstCategoryOnCollision() {
        // female 在 TOPIC_NAMESPACES 里排在 character 之前，所以 female 的译法胜出。
        val merged = TagNormalizer.buildDictionary(
            listOf("yuri" to "百合", "yuri" to "尤里")
        )
        assertEquals("百合", merged["yuri"])
    }

    @Test
    fun trailingSIsStrippedExceptReclass() {
        assertEquals("school girl", TagNormalizer.normKey("School Girls"))
        assertEquals("reclass", TagNormalizer.normKey("reclass"))
        // 字典侧与查询侧同用一套 normKey，去尾 s 才能咬合
        assertEquals("眼镜", normalizer().normalize("female:glasses"))
    }

    @Test
    fun aliasesFoldIntoCanonicalDictionaryKey() {
        assertEquals("萝莉", normalizer().normalize("female:loli"))
        assertEquals("正太", normalizer().normalize("male:shota"))
    }

    @Test
    fun multiWordTagsSurviveIntact() {
        // 旧实现按空格切，"big breasts" 会劈成 "big" + "breasts" 两个假题材。
        assertEquals("巨乳", normalizer().normalize("female:big breasts"))
    }

    @Test
    fun traditionalValuesFallBackToVariantConversion() {
        assertEquals("萝莉", normalizer().normalize("female:蘿莉"))
        // 关掉繁简注入 → 不成桶为「萝莉」，只得以繁体原样成桶（简繁并列就是不做 3b 的后果）
        assertEquals("蘿莉", normalizer(withTraditional = false).normalize("female:蘿莉"))
    }

    @Test
    fun formatValuesAreExcludedAfterSimplification() {
        val n = normalizer(dictionary = emptyMap())
        assertNull(n.normalize("other:同人"))
        // 繁体写法先转简再比对，否则排除表形同虚设
        assertNull(n.normalize("other:同人誌"))
        assertNull(n.normalize("category:AI绘图"))
        assertEquals("萝莉", n.normalize("female:蘿莉"))
    }

    @Test
    fun openccTableParsesOnlyCharLevelPairs() {
        val (s2t, t2s) = ChineseVariantConverter.parseLines(
            sequenceOf(
                "# comment line",
                "",
                "萝蘿",          // 正常的字级条目
                "现代漢",        // 3 个码点：词级条目，必须跳过
                "髮发發",        // 同上
                "龙龍",
            )
        )
        assertEquals(2, s2t.size)
        assertEquals(mapOf('萝'.code to '蘿'.code, '龙'.code to '龍'.code), s2t)
        assertEquals(mapOf('蘿'.code to '萝'.code, '龍'.code to '龙'.code), t2s)
    }

    @Test
    fun conversionWalksCodePointsNotChars() {
        val (s2t, t2s) = ChineseVariantConverter.parseLines(sequenceOf("萝蘿"))
        // 表外的字符（含代理对 emoji）原样保留，不被劈半
        assertEquals("萝莉🚬", convertCodePoints("蘿莉🚬", t2s))
        assertEquals("蘿莉", convertCodePoints("蘿莉", s2t))
        assertTrue(containsMappedCodePoint("蘿", t2s))
        assertFalse(containsMappedCodePoint("萝", t2s))
        // 表为空时恒等返回（asset 读失败的安全退化）
        assertEquals("蘿莉", convertCodePoints("蘿莉", emptyMap()))
    }
}
