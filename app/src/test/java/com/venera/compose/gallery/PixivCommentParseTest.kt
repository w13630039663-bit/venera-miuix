package com.venera.compose.gallery

import com.venera.compose.gallery.data.PixivAppParse
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * pixiv **app-api 评论腿**的解析判据（2026-10-19 · 批 231-F）。
 *
 * ## 这颗单测钉四件事
 *
 * 1. **参数名**：`illust_id` 不是 `id` —— 那是真机事故的根因（已登录时写成 `id`，
 *   站方回 400 且 `user_message` 是空串，于是整个作品页每个作品都报错）。
 *   判据在 `PixivAppApiParamsTest`。
 * 2. **`user: {}`（删号）**：那一条评论**照样交**，只是脸与名字都空 ——
 *   丢掉它就等于少一条真评论。
 * 3. **`parent_comment: {}` = 没有父评论**：站方用空对象表达 null，不是缺键。
 * 4. **`stamp`（贴纸）**：那是屏上"评论里的图"唯一的来源（批 233-3）——
 *   正文空而有贴纸的那一条**不许**被"没有正文"那把尺吃掉，而缺位/形状不对都读成空串。
 *
 * 样本形状照 pixivpy `aapi.py:illust_comments` 与 `models.py:Comment` 抄。
 */
class PixivCommentParseTest {

    private val sample = """
        {
          "comments": [
            {
              "id": 1001,
              "comment": "\u3044\u3064\u3082\u898b\u3066\u307e\u3059",
              "date": "2026-10-01T06:12:00+09:00",
              "user": {
                "id": 123,
                "name": "\u30bb\u30c8\u30de\u30f3",
                "account": "tokenbox",
                "profile_image_urls": { "medium": "https://i.pximg.net/user-profile/x_50.png" }
              },
              "parent_comment": {}
            },
            {
              "id": 1002,
              "comment": "\u7d76\u5bfe\u306b\u5de5\u53e3\u3067\u3059",
              "date": "2026-10-02T07:20:00+09:00",
              "user": {},
              "parent_comment": { "id": 1001, "comment": "\u3044\u3064\u3082\u898b\u3066\u307e\u3059", "date": "2026-10-01T06:12:00+09:00", "user": {} }
            }
          ],
          "next_url": "https://app-api.pixiv.net/v1/illust/comments?illust_id=148343100&offset=30",
          "total_comments": 42
        }
    """.trimIndent()

    @Test
    fun `评论 - 三格都认得出且翻页游标原样带回`() {
        val page = PixivAppParse.illustComments(sample)
        assertEquals(2, page.comments.size)
        assertEquals(42, page.totalCount)
        assertTrue("next_url 必须原样带回", page.nextUrl?.contains("offset=30") == true)

        val first = page.comments[0]
        assertEquals(1001L, first.id)
        assertEquals("\u30bb\u30c8\u30de\u30f3", first.userName)
        assertEquals("tokenbox", first.userAccount)
        assertEquals("https://i.pximg.net/user-profile/x_50.png", first.userAvatarUrl)
        assertEquals("2026-10-01T06:12:00+09:00", first.date)
        // `parent_comment: {}` ⇒ 顶级评论（站方用空对象表达 null）。
        assertNull("空 parent 就是没有父评论", first.replyToId)
    }

    /**
     * `user: {}` = **删号**：脸与名字都空，但这条评论要摆。
     *
     * 屏上由 `PixivCommentRow` 退"已注销用户"与首字母座 —— 数据层只负责**不丢**。
     */
    @Test
    fun `评论 - user 为空对象时评论本身照样交`() {
        val page = PixivAppParse.illustComments(sample)
        val second = page.comments[1]
        assertEquals(1002L, second.id)
        assertEquals("", second.userName)
        assertEquals("", second.userAccount)
        assertEquals("", second.userAvatarUrl)
        // 有父评论：那一条的 id 要认得出来。
        assertEquals(1001L, second.replyToId)
    }

    @Test
    fun `评论 - 整份读不懂时抛`() {
        try {
            PixivAppParse.illustComments("""{"error":{"message":"invalid_request"}}""")
            fail("error 答复必须抛")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("error"))
        }
    }

    @Test
    fun `评论 - 没有 comments 数组时抛`() {
        try {
            PixivAppParse.illustComments("""{"next_url":null}""")
            fail("缺 comments 必须抛")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().contains("comments"))
        }
    }

    /** 有 N 条却一条都认不出 = 站方改字段名，不许说成"没有评论"。 */
    @Test
    fun `评论 - 有 N 条却认不出时抛`() {
        try {
            PixivAppParse.illustComments("""{"comments":[{"foo":"bar"},{"baz":1}]}""")
            fail("认不出一条必须抛")
        } catch (expected: IOException) {
            // 判据只要求"抛了，而且说的是改字段名这一族"—— 不钉整句文案，
            // 否则改一次措辞就得回来改测试（那是把判据退化成正则）。
            assertTrue(
                "抛错的话要说清是'认不出'而不是'没有'：${expected.message}",
                expected.message.orEmpty().let { it.contains("认不出") || it.contains("认得出") },
            )
        }
    }

    /** 单条认不出（没有正文）⇒ 跳过它，不拖垮同批另外那些。 */
    @Test
    fun `评论 - 单条缺正文时跳过那一条`() {
        val body = """
            {"comments":[
              {"id":1,"comment":"\u6709\u6b63\u6587"},
              {"id":2,"comment":""}
            ],"total_comments":2}
        """.trimIndent()
        val page = PixivAppParse.illustComments(body)
        assertEquals(1, page.comments.size)
        assertNotNull(page.comments[0])
    }

    /**
     * **贴纸**（stamp）那一格：站方 `stamp` 是个对象，地址在 `stamp.stamp_url`
     * （2026-10-19 · 批 233-3，用户「也要正常显示评论图」）。
     *
     * 三条各钉一枚：
     * 1. 有 `stamp` ⇒ 号与地址都取出；
     * 2. **没有** `stamp` ⇒ 空串而不是 null 也不是抛（多数评论就是没贴纸）；
     * 3. `stamp` 形状不对（不是对象）⇒ 当没贴纸，**不拖垮整条**（正文还在，丢掉整条就是少一条真评论）。
     */
    @Test
    fun `评论 - 贴纸那一格认得出而缺位时交空串`() {
        val withStamp = PixivAppParse.illustComments(
            """
                {"comments":[
                  {"id":1,"comment":"","stamp":{"stamp_id":219,"stamp_url":
                    "https://s.pximg.net/common/images/stamp/gs/219.jpg"}}
                ]}
            """.trimIndent(),
        )
        assertEquals("纯贴纸的评论不许被当成空条目丢掉", 1, withStamp.comments.size)
        val stamp = withStamp.comments[0]
        assertEquals("", stamp.text)
        assertEquals("https://s.pximg.net/common/images/stamp/gs/219.jpg", stamp.stampUrl)

        val noStamp = PixivAppParse.illustComments("""{"comments":[{"id":2,"comment":"\u53ea\u6709\u5b57"}]}""")
        assertEquals("没有贴纸那一格 = 空串", "", noStamp.comments[0].stampUrl)

        val badShape = PixivAppParse.illustComments(
            """{"comments":[{"id":3,"comment":"\u5b57\u9762\u4e0a\u8fd8\u5728","stamp":[]}]}""",
        )
        assertEquals("stamp 形状不对时那条要留下", 1, badShape.comments.size)
        assertEquals("", badShape.comments[0].stampUrl)
    }

    /** 上面那颗 `sample` 里两条都没有贴纸 ⇒ 都读成空串（缺键与空数组不是两种"有"）。 */
    @Test
    fun `评论 - 默认样本里没有贴纸时不留脏值`() {
        val page = PixivAppParse.illustComments(sample)
        assertTrue(page.comments.all { it.stampUrl.isEmpty() })
    }
}
