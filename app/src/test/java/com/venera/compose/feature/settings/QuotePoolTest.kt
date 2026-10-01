package com.venera.compose.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 名言池判据：池子非空、无重复、抽样确定且不越界。 */
class QuotePoolTest {

    @Test
    fun poolIsNotEmptyAndHasNoDuplicateText() {
        assertTrue(QuotePool.quotes.size >= 10)
        assertEquals(QuotePool.quotes.size, QuotePool.quotes.map { it.text }.toSet().size)
    }

    @Test
    fun everyQuoteHasNonBlankText() {
        QuotePool.quotes.forEach { q ->
            assertTrue("空句子进了池子：$q", q.text.isNotBlank())
        }
    }

    @Test
    fun pickIsDeterministicForTheSameSeed() {
        // 确定性抽样是给单测复现用的契约：同一种子必须同一句。
        val a = QuotePool.pick(42L)
        val b = QuotePool.pick(42L)
        assertEquals(a, b)
    }

    @Test
    fun differentSeedsCoverMoreThanOneQuote() {
        // 不是要求两个种子必得不同句（那在概率上不成立），
        // 而是 64 个种子必须踩到不止一句 —— 防止有人把 pick 写成恒返回第一句。
        val picked = (0L until 64L).map { QuotePool.pick(it).text }.toSet()
        assertTrue("64 个种子只踩到 ${picked.size} 句，抽样可疑", picked.size > 1)
    }

    @Test
    fun randomIndexIsWithinBounds() {
        repeat(64) {
            val i = QuotePool.randomIndex()
            assertTrue(i in QuotePool.quotes.indices)
        }
        // 大量抽样应踩到不止一个下标：防「随机」被写成恒等。
        val picked = (0 until 64).map { QuotePool.randomIndex() }.toSet()
        assertTrue("64 连抽只踩到 ${picked.size} 个下标，randomIndex 可疑", picked.size > 1)
    }
}
