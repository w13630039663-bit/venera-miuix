package com.venera.compose.gallery.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pixiv 评论里那一串小表情的判据（2026-10-19 · 批 233-3，用户「也要正常显示评论图和表情」）。
 *
 * 钉四件事：
 * 1. **认得的才换图**：站方哪天新增一枚，屏上读到的是它的**字面短码**，不是一块破图；
 * 2. **形状只认小写短码**：`(笑)`、`(1)`、`(Name)` 都不许被当表情（误伤一条真评论比漏一枚表情贵）；
 * 3. **地址由号长出来**，不在调用点拼第二遍；
 * 4. **号段就是那 38 枚**：多一枚少一枚都是表被人动过（三家独立实现互证 + CDN 现量，出处在那颗文件的注释里）。
 */
class PixivCommentEmojiTest {

    /** 纯文字：整段原样交回，一段不多一段不少。 */
    @Test
    fun `纯文字只切出一段`() {
        assertEquals(
            listOf(PixivCommentSegment.Text("画师这期的构图真好")),
            splitPixivComment("画师这期的构图真好"),
        )
    }

    /** 文字与表情交替：顺序必须保住，否则屏上念的是另一句话。 */
    @Test
    fun `文字与表情按原顺序交替`() {
        assertEquals(
            listOf(
                PixivCommentSegment.Text("太强了 "),
                PixivCommentSegment.Emoji("star"),
                PixivCommentSegment.Text(" 和 "),
                PixivCommentSegment.Emoji("happy3"),
                PixivCommentSegment.Text("！"),
            ),
            splitPixivComment("太强了 (star) 和 (happy3)！"),
        )
    }

    /** 相邻两枚之间没有文字：中间不许长出空的文字段（空段在 FlowRow 里是个看不见的占位）。 */
    @Test
    fun `相邻两枚表情之间不产生空文字段`() {
        assertEquals(
            listOf(PixivCommentSegment.Emoji("heart"), PixivCommentSegment.Emoji("teardrop")),
            splitPixivComment("(heart)(teardrop)"),
        )
    }

    /** 表里没有的那一串照样切得出 Emoji，但号是 null —— 调用点据此原样念出短码。 */
    @Test
    fun `未收录的短码认得出形状而认不出号`() {
        assertEquals(listOf(PixivCommentSegment.Emoji("newemoji")), splitPixivComment("(newemoji)"))
        assertNull(pixivEmojiId("newemoji"))
        assertEquals(101, pixivEmojiId("normal"))
    }

    /** 形状不合的一串根本不该进 Emoji：全角括号、大写开头、数字开头、纯数字。 */
    @Test
    fun `形状不合的一律当文字`() {
        for (raw in listOf("(笑)", "(1)", "(Name)", "(_star)", "(star")) {
            assertEquals("$raw 必须整串当文字", listOf(PixivCommentSegment.Text(raw)), splitPixivComment(raw))
        }
    }

    /** 空正文（纯贴纸那一档）交空表，不许交出一段空文字。 */
    @Test
    fun `空正文交空表`() {
        assertTrue(splitPixivComment("").isEmpty())
    }

    /** 地址只有一种写法：号段 + `.png`。 */
    @Test
    fun `地址由号长出`() {
        assertEquals("https://s.pximg.net/common/images/emoji/101.png", pixivEmojiUrl(101))
        assertEquals("https://s.pximg.net/common/images/emoji/503.png", pixivEmojiUrl(503))
    }

    /**
     * 号段一共 38 枚、且**号不重复**。
     *
     * 这一枚钉的是"这张表没被人随手加减过"：加一枚没现量过的短码，屏上就是一块破图；
     * 删一枚则是把站方在用的表情退回成字面短码。两种都读在真机上，量具要先叫。
     */
    @Test
    fun `号段就是那三十八枚且号互不相同`() {
        val ids = listOf(
            pixivEmojiId("normal"), pixivEmojiId("surprise"), pixivEmojiId("serious"), pixivEmojiId("heaven"),
            pixivEmojiId("happy"), pixivEmojiId("excited"), pixivEmojiId("sing"), pixivEmojiId("cry"),
            pixivEmojiId("normal2"), pixivEmojiId("shame2"), pixivEmojiId("love2"), pixivEmojiId("interesting2"),
            pixivEmojiId("blush2"), pixivEmojiId("fire2"), pixivEmojiId("angry2"), pixivEmojiId("shine2"),
            pixivEmojiId("panic2"),
            pixivEmojiId("normal3"), pixivEmojiId("satisfaction3"), pixivEmojiId("surprise3"),
            pixivEmojiId("smile3"), pixivEmojiId("shock3"), pixivEmojiId("gaze3"), pixivEmojiId("wink3"),
            pixivEmojiId("happy3"), pixivEmojiId("excited3"), pixivEmojiId("love3"),
            pixivEmojiId("normal4"), pixivEmojiId("surprise4"), pixivEmojiId("serious4"), pixivEmojiId("love4"),
            pixivEmojiId("shine4"), pixivEmojiId("sweat4"), pixivEmojiId("shame4"), pixivEmojiId("sleep4"),
            pixivEmojiId("heart"), pixivEmojiId("teardrop"), pixivEmojiId("star"),
        )
        assertEquals("点名 38 枚", 38, ids.size)
        assertTrue("每一枚都要有号", ids.all { it != null })
        assertEquals("号不许重复", 38, ids.toSet().size)
    }
}
