package com.venera.compose.data.tags

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * EhTagTranslation 词条的**图标 markdown 清洗**（2026-10-02）。
 *
 * 官方数据库的一部分词条，译名本身带 `![图标](url)` 前缀 —— 那是给官方 wiki 渲染
 * tag 图标用的，我们只取后半段文字。不剥的话，详情页药丸与长按复制里就是一整串
 * `https://raw.githubusercontent.com/...`（用户报的「EH/NH 英文站出现超长 tag」）。
 */
class TagTranslationSanitizeTest {

    @Test
    fun `剥掉图标前缀只留译名`() {
        assertEquals(
            "东方Project",
            stripTranslationIconMarkdown(
                "![阴阳玉图标](https://raw.githubusercontent.com/wiki/EhTagTranslation/Database/database-icon/touhou%20project.webp)东方Project",
            ),
        )
    }

    @Test
    fun `alt 为空的图标也剥得掉`() {
        // 蔚蓝档案这类词条官方给的是 `![](favicon 地址)译名`，alt 段什么都没有。
        assertEquals(
            "蔚蓝档案",
            stripTranslationIconMarkdown("![](https://bluearchive.nex.com/favicon.ico)蔚蓝档案"),
        )
    }

    @Test
    fun `一篇词条里多枚图标全剥`() {
        assertEquals(
            "甲乙",
            stripTranslationIconMarkdown("![a](https://x/a.webp)![b](https://x/b.webp)甲乙"),
        )
    }

    @Test
    fun `普通词条原样通过`() {
        assertEquals("百合", stripTranslationIconMarkdown("百合"))
        assertEquals("cunnilingus", stripTranslationIconMarkdown("cunnilingus"))
    }

    @Test
    fun `剥完只剩空串按没有译文处理`() {
        // 图标-only 的词条剥完是空串：parse 会跳过它（宁可不译，不摆一串 URL）。
        assertEquals("", stripTranslationIconMarkdown("![图标](https://x/y.webp)"))
        assertEquals("", stripTranslationIconMarkdown("   "))
    }

    @Test
    fun `普通 markdown 链接不是图标、不动`() {
        // 只剥图片语法 `![..](..)`，普通超链接 `[..](..)` 不是这一类，保持原样 ——
        // 清洗范围跟着「实测出现的脏数据」走，不扩大化。
        assertEquals(
            "[维基](https://example.com)词条",
            stripTranslationIconMarkdown("[维基](https://example.com)词条"),
        )
    }
}
