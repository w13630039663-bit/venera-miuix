package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.YandeReDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 按**实测响应**锁住 yande.re 的字段解析与归一翻译。
 *
 * 素材取自 2026-09-24 的探针（一条真实 post，字段名与取值原样保留，值有截短）：
 * 站方一共回三十多个字段，其中 `sample_url` / `jpeg_url` / `file_url` 三档地址
 * **没有** `large_file_url`、也**没有** `fav_count` —— 这个形状是 Danbooru 系的常见误解来源，
 * 写错就是拿不到图 / 把"不知道"当成 0。
 */
class GalleryPostParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** 站方原始 JSON → 归一条目（解析层与翻译层一起锁）。 */
    private fun decode(raw: String = sample): List<GalleryPost> =
        json.decodeFromString<List<YandeReDto>>(raw).map { it.toPost() }

    private val sample = """
    [{
      "id": 347607,
      "tags": "aria_blue entergram ricol seifuku sweater tagme",
      "created_at": 1790254479,
      "updated_at": 1790254500,
      "creator_id": 1234,
      "approver_id": null,
      "author": "moonian",
      "change": 900001,
      "source": "https://ariablue.jp/",
      "score": 1674,
      "md5": "8be5b7ac6c9f6cc1f3b4798b8d25bb2c",
      "file_size": 32128363,
      "file_ext": "webp",
      "file_url": "https://files.yande.re/image/8b/e5/8be5b7ac6c9f6cc1f3b4798b8d25bb2c/yande.re%20347607.webp",
      "is_shown_in_index": true,
      "preview_url": "https://assets.yande.re/data/preview/8b/e5/8be5b7ac6c9f6cc1f3b4798b8d25bb2c.jpg",
      "preview_width": 150,
      "preview_height": 94,
      "actual_preview_width": 300,
      "actual_preview_height": 207,
      "sample_url": "https://files.yande.re/sample/8b/e5/8be5b7ac6c9f6cc1f3b4798b8d25bb2c/yande.re%20347607%20sample.jpg",
      "sample_width": 1047,
      "sample_height": 1500,
      "sample_file_size": 348833,
      "jpeg_url": "https://files.yande.re/jpeg/8b/e5/8be5b7ac6c9f6cc1f3b4798b8d25bb2c/yande.re%20347607%20jpeg.jpg",
      "jpeg_width": 3500,
      "jpeg_height": 2410,
      "jpeg_file_size": 1431858,
      "rating": "e",
      "is_rating_locked": false,
      "has_children": true,
      "parent_id": null,
      "status": "active",
      "is_pending": false,
      "width": 9151,
      "height": 6300,
      "is_held": false,
      "frames_pending_string": "",
      "frames_pending": [],
      "frames_string": "",
      "frames": [],
      "is_note_locked": false,
      "last_noted_at": 0,
      "last_commented_at": 0
    }]
    """.trimIndent()

    @Test
    fun `未知字段一律忽略而不是抛异常`() {
        val list = decode()
        assertEquals(1, list.size)
        val post = list[0]
        assertEquals(347607L, post.id)
        assertEquals("e", post.rating)
        assertEquals(1674, post.score)
        // tags 是**一个空格分隔串**、没有 `general:` 这类命名空间前缀（实测）。
        assertEquals(listOf("aria_blue", "entergram", "ricol", "seifuku", "sweater", "tagme"), post.tagList)
        // 实测站方 JSON 里没有 fav_count → 归一后必须是 null，不是 0。
        assertEquals(null, post.favCount)
    }

    @Test
    fun `本站没有分类字段，标签只出一桶`() {
        val post = decode().first()
        // 实测 44 个键里不含 `tag_string_*`（Danbooru 才有那五串），
        // 所以这里**不硬造**"通用/角色"那种假分组 —— 分不出来就是分不出来。
        assertEquals(listOf("标签"), post.tagGroups.map { it.label })
        assertEquals(post.tagList, post.tagGroups.first().tags)
    }

    @Test
    fun `三档地址各按实测域名取用`() {
        val post = decode().first()
        assertEquals(true, post.previewUrl.startsWith("https://assets.yande.re/"))
        assertEquals(true, post.largeUrl.startsWith("https://files.yande.re/jpeg/"))
        assertEquals(true, post.fileUrl.startsWith("https://files.yande.re/image/"))
    }

    @Test
    fun `瀑布流比例取 jpeg 档而不是被裁过的 preview`() {
        val post = decode().first()
        // preview 300×207 与 jpeg 3500×2410 都是 1.452；width/height 是原图 9151×6300。
        // 取 jpeg 档：它在 preview 缺失时仍有值，且与原图同比例。
        assertEquals(3500f / 2410f, post.cardRatio, 0.001f)
    }

    @Test
    fun `拿不到任何尺寸时返回 0 由卡片退回占位比例`() {
        val bare = GalleryPost(site = GallerySite.YANDERE, id = 7)
        assertEquals(0f, bare.cardRatio, 0f)
    }

    @Test
    fun `本站单页地址与站方 source 字段不混用`() {
        val post = decode().first()
        // pageUrl 是我们能分享/打开的本站地址；source 是画师自己的出处，两者不能互换。
        assertEquals("https://yande.re/post/show/347607", post.pageUrl)
        assertEquals("https://ariablue.jp/", post.source)
    }

    @Test
    fun `空列表与缺字段都解得开`() {
        assertEquals(0, decode("[]").size)
        val onlyId = decode("""[{"id":1}]""").first()
        assertEquals(1L, onlyId.id)
        assertEquals("", onlyId.previewUrl)
        assertEquals(emptyList<String>(), onlyId.tagList)
        // 缺 rating 时按"成人内容"处理更保守，但判据在 GalleryGuard 里按 isAdultMarked 走，
        // 这里只锁住解析层不炸。
        assertEquals("", onlyId.rating)
    }
}
