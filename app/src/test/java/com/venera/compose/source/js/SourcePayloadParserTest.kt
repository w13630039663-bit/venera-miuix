package com.venera.compose.source.js

import org.junit.Assert.*
import org.junit.Test

class SourcePayloadParserTest {
    @Test fun searchOptionsReadEnvelopeAndPreserveSourceOrder() {
        val json = """{"success":true,"data":[{"label":"排序","options":["10-最新","2-热门","2-重复"],"default":"2"}]}"""
        val groups = SourcePayloadParser.searchOptions(SourcePayloadParser.data(json))
        assertEquals(1, groups.size)
        assertEquals(listOf("10", "2"), groups.single().options.keys.toList())
        assertEquals("2", groups.single().defaultKey)
        assertEquals("热门", groups.single().options["2"])
    }

    @Test fun ehMultiSelectAndDropdownUseProtocolValues() {
        val json = """{"success":true,"data":[{"type":"multi-select","options":["0-分类一","1-分类二"],"default":["0","1"]},{"type":"dropdown","options":["a-选项"]}]}"""
        val groups = SourcePayloadParser.searchOptions(SourcePayloadParser.data(json))
        assertEquals("[\"0\",\"1\"]", groups[0].defaultKey)
        assertNull(groups[1].defaultKey)
    }

    @Test(expected = IllegalStateException::class)
    fun scriptFailureIsNotAnEmptyFilterList() {
        SourcePayloadParser.data("""{"success":false,"error":"登录已过期"}""")
    }

    @Test fun absentMetricsRemainAbsentAndRealZeroIsPreserved() {
        assertNull(SourcePayloadParser.rating(emptyMap<String, Any>()))
        assertNull(SourcePayloadParser.likesCount(emptyMap<String, Any>()))
        assertEquals(0, SourcePayloadParser.likesCount(mapOf("likesCount" to 0)))
        assertEquals(4.5f, SourcePayloadParser.rating(mapOf("stars" to 4.5)))
        assertNull(SourcePayloadParser.rating(mapOf("stars" to "NaN")))
    }

    @Test fun numericCommentIdRemainsValidForReplyRequests() {
        val payload = SourcePayloadParser.data("""{"success":true,"data":{"id":123,"content":"评论"}}""") as Map<*, *>
        assertEquals("123", SourcePayloadParser.comment(payload)!!.id)
    }

    @Test fun replyCapabilityDistinguishesMissingAndZeroReplies() {
        assertNull(SourcePayloadParser.comment(mapOf("content" to "评论"))!!.replyCount)
        assertEquals(0, SourcePayloadParser.comment(mapOf("content" to "评论", "replyCount" to 0))!!.replyCount)
    }
}
