package com.venera.desktop.platform

import com.venera.compose.data.platform.quoteIdentifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收藏夹表名转义判据（一个收藏夹一张表、表名来自用户输入）。
 * 用例名与样本串照简报；注入那条对断言做了修正，理由见本任务报告：
 * 分号是合法表名字符，转义后必然留在标识符本体里——它只是不再充当语句分隔符。
 */
class QuoteIdentifierTest {
    @Test fun `反引号必须被翻倍而不是被当结束符`() {
        assertEquals("`fav``s`", quoteIdentifier("fav`s"))
    }

    @Test fun `正常中文名与空格照收`() { assertEquals("`我的 收藏`", quoteIdentifier("我的 收藏")) }

    @Test fun `空串与换行直接拒`() {
        assertTrue(runCatching { quoteIdentifier("") }.isFailure)
        assertTrue(runCatching { quoteIdentifier("a\nb") }.isFailure)
    }

    @Test fun `注入串只能变成表名本体`() {
        val evil = "x`; DROP TABLE comic_favorite; --"
        val quoted = quoteIdentifier(evil)
        assertTrue(quoted.startsWith("`") && quoted.endsWith("`"))
        // 剥掉外包反引号后，本体就是原名（内部反引号翻倍）；分号被留在名字里、
        // 但因整个串被反引号包住而不充当语句分隔符——真库上的行为断言见 JdbcSqliteDatabaseTest。
        // 简报原文此处写的是 assertFalse(内层含 ";")，与它自己的注释相矛盾（分号必须保留在表名里），
        // 已按"注入串只能变成表名本体"的语义修正，见任务报告偏离 1。
        assertEquals(evil.replace("`", "``"), quoted.drop(1).dropLast(1))
    }
}
