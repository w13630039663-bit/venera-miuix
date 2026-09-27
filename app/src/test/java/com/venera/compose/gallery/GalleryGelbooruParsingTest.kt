package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GelbooruPostEnvelopeDto
import com.venera.compose.gallery.data.GelbooruTagEnvelopeDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 按**实测响应**锁住 Gelbooru 的字段解析与归一翻译。
 *
 * 下面每一段 JSON 都是 2026-09-26 探针拿到的**原文**（只裁掉了与解析无关的相邻条目），
 * 不是照文档编的样本。这么做的理由很直接：Gelbooru 的官方 wiki `howto:api`
 * **只讲请求参数、完全不讲响应字段**，所以字段名除了实测没有第二个可靠来源。
 *
 * 三条**最容易写错、且错了不会报错**的地方各有专门用例：
 * 1. 响应是**信封**（条目在 `post`/`tag` 键里），不是裸数组；
 * 2. `sample_url` 会是**空串**（小图与视频），中档必须兜底到 `file_url`；
 * 3. 分级是**单词**（`general`/`sensitive`/…），不是 yande.re 那种单字母。
 */
class GalleryGelbooruParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 实测原文：一条普通静态图（`sample_url` 正常给）。
     * 注意 `@attributes` 里那个 `count` 是**命中总数**（1430 万级）。
     */
    private val imageEntry = """
        {"@attributes":{"limit":2,"offset":0,"count":14304678},"post":[
        {"id":14970719,"created_at":"Sat Sep 26 03:51:09 -0500 2026","score":0,
         "width":1334,"height":1002,"md5":"da5619f7afe408e0589fa25d43603bb1",
         "directory":"da/56","image":"da5619f7afe408e0589fa25d43603bb1.png",
         "rating":"general","source":"https://i.bandori.party/u/c/art/1280Lisa-Imai-Happy-9eKI4H.png",
         "change":1790412670,"owner":"beamblue367","creator_id":1120925,"parent_id":0,
         "sample":1,"preview_height":262,"preview_width":349,
         "tags":"2girls bang_dream! brown_hair game_asset grabbing_another&#039;s_arm imai_lisa",
         "title":"","has_notes":"false","has_comments":"false",
         "file_url":"https://img4.gelbooru.com/images/da/56/da5619f7afe408e0589fa25d43603bb1.png",
         "preview_url":"https://img4.gelbooru.com/thumbnails/da/56/thumbnail_da5619f7afe408e0589fa25d43603bb1.jpg",
         "sample_url":"https://img4.gelbooru.com/samples/da/56/sample_da5619f7afe408e0589fa25d43603bb1.jpg",
         "sample_height":638,"sample_width":849,"status":"active","post_locked":0,"has_children":"false"}]}
    """.trimIndent()

    /**
     * 实测原文：一条**小图**（684×597），站方给 `sample:0` 且 **`sample_url` 是空串**。
     * 这就是必须兜底的那一档 —— 照抄 `sample_url` 会让大图页中层整个空掉。
     */
    private val smallImageEntry = """
        {"@attributes":{"limit":3,"offset":0,"count":14304678},"post":[
        {"id":197798,"score":31144,"width":682,"height":597,"md5":"3c5dd70262f440630adfc1f9bb7d5da1",
         "image":"6d15efdf372585ce6cead5f2a3df3465381686f8.jpg","rating":"general",
         "source":"http://hccweb1.bai.ne.jp/morikawadanpei-g/graf/lejeno.jpg","change":1733124668,
         "owner":"anonymous","sample":0,"preview_height":306,"preview_width":350,
         "tags":"3d bad_id blue_eyes dinosaur",
         "file_url":"https://img4.gelbooru.com/images/3c/5d/3c5dd70262f440630adfc1f9bb7d5da1.jpg",
         "preview_url":"https://img4.gelbooru.com/thumbnails/3c/5d/thumbnail_3c5dd70262f440630adfc1f9bb7d5da1.jpg",
         "sample_url":"","sample_height":0,"sample_width":0,"status":"active"}]}
    """.trimIndent()

    /**
     * 实测原文：一条**视频**。两个坑同时在：`sample_url` 空串，
     * 而且 `image` 是 `.webm` 而 `file_url` 是 `.mp4`（站方转过码）。
     */
    private val videoEntry = """
        {"@attributes":{"limit":3,"offset":0,"count":14304678},"post":[
        {"id":8742200,"score":10042,"width":639,"height":470,"md5":"0c7461e61e6611587e5105ca36b788e1",
         "image":"0c7461e61e6611587e5105ca36b788e1.webm","rating":"explicit",
         "source":"https://gelbooru.com/index.php?page=post&amp;s=view&amp;id=7457880","change":1782541002,
         "owner":"borleech","sample":0,"preview_height":257,"preview_width":350,
         "tags":"1boy 1girl animated video",
         "file_url":"https://img4.gelbooru.com/images/0c/74/0c7461e61e6611587e5105ca36b788e1.mp4",
         "preview_url":"https://img4.gelbooru.com/thumbnails/0c/74/thumbnail_0c7461e61e6611587e5105ca36b788e1.jpg",
         "sample_url":"","sample_height":0,"sample_width":0,"status":"active"}]}
    """.trimIndent()

    private val tagEntry = """
        {"@attributes":{"limit":3,"offset":0,"count":444},"tag":[
        {"id":126,"name":"touhou","count":1024947,"type":3,"ambiguous":0},
        {"id":1874267,"name":"third_eye_(touhou)","count":39742,"type":0,"ambiguous":0}]}
    """.trimIndent()

    /** 解信封 → 翻译 → **滤掉翻不出来的**（与生产代码 `requestPosts` 的口径一致）。 */
    private fun postsOf(raw: String): List<com.venera.compose.gallery.data.GalleryPost> =
        json.decodeFromString<GelbooruPostEnvelopeDto>(raw).posts().mapNotNull { it.toPost() }

    @Test
    fun `信封里的条目能解出来并翻成归一形状`() {
        val posts = postsOf(imageEntry)
        assertEquals(1, posts.size)
        val post = posts.single()
        assertEquals(GallerySite.GELBOORU, post.site)
        assertEquals(14970719L, post.id)
        assertEquals("general", post.rating)
        assertEquals(1334, post.width)
        assertEquals(1002, post.height)
        assertEquals("beamblue367", post.author)
        assertEquals("da5619f7afe408e0589fa25d43603bb1", post.md5)
        assertEquals("png", post.fileExt)
        // 站点地址前缀按实测的形态拼（`index.php?page=post&s=view&id=`）。
        assertEquals(
            "https://gelbooru.com/index.php?page=post&s=view&id=14970719",
            post.pageUrl,
        )
        assertEquals("gelbooru:14970719", post.uid)
    }

    @Test
    fun `中档取 sample_url，尺寸也跟着用 sample 那一对`() {
        val post = postsOf(imageEntry).single()
        assertTrue(post.largeUrl.contains("/samples/"))
        assertEquals(849, post.largeWidth)
        assertEquals(638, post.largeHeight)
        // 开门档与网格同址（缓存直接命中）。
        assertEquals(post.previewUrl, post.fastUrl)
    }

    @Test
    fun `sample_url 为空串时中档兜底到原图，尺寸换成原图那一对`() {
        // 这是整条链上最容易踩空的地方：站方给小图/视频的 sample_url 是 **""** 而不是缺键。
        val post = postsOf(smallImageEntry).single()
        assertEquals(post.fileUrl, post.largeUrl)
        assertTrue(post.largeUrl.contains("/images/"))
        // 尺寸必须跟着换，否则瀑布流会拿 sample 的 0×0 去算比例。
        assertEquals(682, post.largeWidth)
        assertEquals(597, post.largeHeight)
        // 即便 sample 没了，缩略图仍然在（实测恒非空）。
        assertTrue(post.previewUrl.isNotBlank())
    }

    @Test
    fun `视频的扩展名取自 file_url 而不是 image`() {
        // 实测：image 是 .webm、file_url 是 .mp4（站方转码）。拿 image 判会把可播的 mp4 当 webm。
        val post = postsOf(videoEntry).single()
        assertEquals("mp4", post.fileExt)
        assertTrue(post.isVideo)
        // 中档同样是空串兜底到原片 —— 所以**中档不能当视频底图**，见下面两条。
        assertEquals(post.fileUrl, post.largeUrl)
    }

    @Test
    fun `视频底图档绝不能是原片`() {
        // 旧测试在第 140 行写着"视频底图由 UI 那边处理"，而 UI 并没有处理：
        // `GalleryVideoViewer` 直接把 `largeUrl` 喂给 Coil —— 对 Gelbooru 视频那就是
        // 拿一条 mp4 当图解码：先白下几十 MB，再解不出来，那一屏什么都没有。
        val post = postsOf(videoEntry).single()
        assertNotEquals(post.fileUrl, post.videoPosterUrl)
        assertEquals(post.previewUrl, post.videoPosterUrl)
    }

    @Test
    fun `站方给了静帧时视频底图要用静帧而不是缩略图`() {
        // yande.re 那一路 large 档本来就是原分辨率的 jpg 静帧（与 fileUrl 不同址），
        // 它比 350px 缩略图清楚，所以判据是"large 与原片同址才退档"，不是"视频一律用缩略图"。
        val still = GalleryPost(
            site = GallerySite.YANDERE,
            id = 1,
            fileExt = "webm",
            previewUrl = "https://assets.yande.re/data/preview/8b/e5/x.jpg",
            largeUrl = "https://files.yande.re/jpeg/8b/e5/yande.re%201%20jpeg.jpg",
            fileUrl = "https://files.yande.re/data/8b/e5/x.webm",
        )
        assertEquals(still.largeUrl, still.videoPosterUrl)
    }

    @Test
    fun `标签里的 HTML 实体要解开`() {
        // 实测站方把单引号转义成 &#039;。不解开的话 tagList 里的串与用户屏蔽词对不上，
        // 屏蔽规则会**静默漏挡**那一类标签。
        val post = postsOf(imageEntry).single()
        assertTrue(post.tagList.contains("grabbing_another's_arm"))
        assertTrue(post.tagList.none { it.contains("&#039;") })
    }

    @Test
    fun `source 里的 HTML 实体也要解开`() {
        val post = postsOf(videoEntry).single()
        assertTrue(post.source.contains("&s=view&id="))
        assertTrue(!post.source.contains("&amp;"))
    }

    @Test
    fun `没有 file_url 的条目整条丢掉，不留空壳卡片`() {
        // 站方偶尔会给出一行没有可用图片地址的条目。摆上去就是一张尺寸正确、内容全空的灰卡。
        val broken = """
            {"@attributes":{"limit":1,"offset":0,"count":1},"post":[
            {"id":5,"width":100,"height":100,"rating":"general","tags":"x",
             "preview_url":"https://img4.gelbooru.com/thumbnails/aa/bb/thumbnail_x.jpg",
             "file_url":"","sample_url":""}]}
        """.trimIndent()
        // postsOf 已经做过 toPost 并滤掉了 null（见它的实现）。
        val posts = postsOf(broken)
        assertTrue(posts.isEmpty())
    }

    @Test
    fun `favCount 与 fileSize 一律为空，不编数`() {
        // 两站都不给这两个字段。编 0 会让 UI 显示"这张零收藏 / 0 MB"——那是编出来的事实。
        val post = postsOf(imageEntry).single()
        assertNull(post.favCount)
        assertEquals(0L, post.fileSize)
        assertNull(post.durationSeconds)
    }

    @Test
    fun `tag 端点能解出来，字段名按实测的 count_type`() {
        val tags = json.decodeFromString<GelbooruTagEnvelopeDto>(tagEntry).tags()
        assertEquals(2, tags.size)
        val touhou = tags.first()
        assertEquals("touhou", touhou.name)
        assertEquals(1024947, touhou.count)
        assertEquals(3, touhou.type)

        val suggestions = tags.map { it.toSuggestion() }
        assertEquals(GallerySite.GELBOORU, suggestions.first().site)
        assertEquals(1024947, suggestions.first().count)
        // 站方没有 `is_deprecated` → 一律 false，不拿 ambiguous 去顶替。
        assertTrue(suggestions.none { it.deprecated })
        assertEquals("作品", suggestions.first().categoryLabel)
        assertEquals("通用", suggestions[1].categoryLabel)
    }

    @Test
    fun `条目退化成单个对象时也能解出来`() {
        // 本轮实测**没有**发作（连 &id= 单条取也是数组），但这是 Gelbooru 系 API 的老坑，
        // 兼容的代价只有一处判断，不兼容的代价是"某个冷门查询整页崩"。
        val single = """
            {"@attributes":{"limit":1,"offset":0,"count":1},
             "post":{"id":7,"rating":"general","tags":"x",
             "file_url":"https://img4.gelbooru.com/images/aa/bb/aa.jpg",
             "preview_url":"https://img4.gelbooru.com/thumbnails/aa/bb/thumbnail_aa.jpg",
             "sample_url":""}}
        """.trimIndent()
        assertEquals(1, postsOf(single).size)
    }

    @Test
    fun `tag 键缺席时回空表而不是抛异常`() {
        val empty = """{"@attributes":{"limit":1,"offset":0,"count":0}}"""
        assertTrue(json.decodeFromString<GelbooruPostEnvelopeDto>(empty).posts().isEmpty())
        assertTrue(json.decodeFromString<GelbooruTagEnvelopeDto>(empty).tags().isEmpty())
    }

    @Test
    fun `命中总数能读出来，页尾可以用真数`() {
        // 与 Danbooru 不同（那边没有总数端点），这一站给了 count，所以读数不必靠数已取回的张数。
        val env = json.decodeFromString<GelbooruPostEnvelopeDto>(imageEntry)
        assertEquals(14304678L, env.attributes.count)
    }
}
