package com.venera.compose.gallery

import com.venera.compose.gallery.data.PixivUserParse
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * `user_previews` 那一族答复的解析判据（2026-10-17 第八轮）。
 *
 * 这一族有两个调用点（`/v1/user/following` 与 `/v1/user/recommended`），答复形状相同，
 * 所以判据只写一遍、两条腿共用。样本抄的是 pixez-miuix `UserPreview.kt` 那份模型对得上的真实键名
 * （`user_previews[]` / `user.profile_image_urls.medium` / `is_muted` / `next_url`）。
 *
 * 口径与仓里其余解析件一致：
 * 1. 整份读不懂 = 抛；
 * 2. 有 N 条却一条认不出 = 也抛（站方改键名的表现就是整栏消失，不能说成"你没关注人"）。
 */
class PixivUserParseTest {

    private val sample = """
        {
          "user_previews": [
            {
              "user": {
                "id": 1234567,
                "name": "テスト絵師",
                "account": "test_eshi",
                "profile_image_urls": { "medium": "https://i.pximg.net/user-profile/img/1.jpg" }
              },
              "illusts": [ { "id": 111 } ],
              "novels": [],
              "is_muted": false
            },
            {
              "user": {
                "id": 7654321,
                "name": "另一位",
                "account": "another",
                "profile_image_urls": { "medium": "" }
              },
              "illusts": [],
              "novels": [],
              "is_muted": true
            }
          ],
          "next_url": "https://app-api.pixiv.net/v1/user/recommended?filter=for_android&offset=30"
        }
    """.trimIndent()

    @Test
    fun `一页人里该有的都有，头像与静音标志照站方给`() {
        val page = PixivUserParse.userPreviews(sample, "测试")
        assertEquals(2, page.users.size)
        val first = page.users[0]
        assertEquals(1234567L, first.id)
        assertEquals("テスト絵師", first.name)
        assertEquals("test_eshi", first.account)
        assertEquals("https://i.pximg.net/user-profile/img/1.jpg", first.avatarUrl)
        assertEquals(false, first.muted)
        // 头像空串就留空串（界面按首字母圆座降级，**不拿别人的头像顶**）。
        assertEquals("", page.users[1].avatarUrl)
        assertTrue(page.users[1].muted)
        assertEquals(
            "https://app-api.pixiv.net/v1/user/recommended?filter=for_android&offset=30",
            page.nextUrl,
        )
    }

    @Test
    fun `没有 next_url 就是到底了，不是下一页未知`() {
        val page = PixivUserParse.userPreviews("""{"user_previews":[]}""", "测试")
        assertTrue(page.users.isEmpty())
        assertNull(page.nextUrl)
    }

    @Test
    fun `只有 account 没有 name 的人也算认得出，不整条丢`() {
        val body = """
            {"user_previews":[{"user":{"id":9,"account":"only_handle",
             "profile_image_urls":{"medium":"https://x/1.jpg"}}}]}
        """.trimIndent()
        val page = PixivUserParse.userPreviews(body, "测试")
        assertEquals(1, page.users.size)
        assertEquals("only_handle", page.users[0].name)
    }

    @Test
    fun `有 N 条却一条都认不出就炸，不能说成没关注人`() {
        val body = """{"user_previews":[{"user":{"uid":1}},{"user":{"uid":2}}]}"""
        try {
            PixivUserParse.userPreviews(body, "测试")
            fail("应当抛")
        } catch (e: IOException) {
            assertTrue("要说得出是字段名变了：${e.message}", e.message.orEmpty().contains("改了字段名"))
        }
    }

    @Test
    fun `不是 JSON 就炸，并把开头截进消息里`() {
        try {
            PixivUserParse.userPreviews("<html>风控</html>", "测试")
            fail("应当抛")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("不是 JSON"))
        }
    }

    @Test
    fun `app-api 的 error 对象要念出状态码与站方原因`() {
        val body = """
            {"error":{"message":"","reason":"","status_code":191,
             "user_message":"このリクエストは無効です"}}
        """.trimIndent()
        try {
            PixivUserParse.userPreviews(body, "pixiv 推荐用户")
            fail("应当抛")
        } catch (e: IOException) {
            val message = e.message.orEmpty()
            assertTrue("要带端点名与状态码：$message", message.contains("pixiv 推荐用户") && message.contains("191"))
            assertTrue("要带站方原话：$message", message.contains("無効"))
        }
    }
}
