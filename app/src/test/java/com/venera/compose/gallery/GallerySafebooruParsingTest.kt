package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.SafebooruPostDto
import com.venera.compose.gallery.data.SafebooruTagDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 按**实测响应**锁住 Safebooru 的字段解析与归一翻译。
 *
 * 下面每一段 JSON 都是 2026-10-01 直连 `safebooru.donmai.us` 拿到的**原文**
 * （只裁掉了与解析无关的相邻键），不是照 Danbooru 文档编的样本 ——
 * 这一站的字段表与 Gelbooru 名字像但形状不同，编的样本会一起骗人。
 *
 * 四条**错了不会报错**的地方各有专门用例：
 * 1. 标签串叫 `tag_string`，站方**根本没有** `tags` 键 —— 按 `tags` 声明会得到空串，
 *    HTTP 200、正文合法，卡片却一枚标签都没有，屏蔽规则与标签翻译同时静默失效；
 * 2. 降档尺寸只在 `media_asset.variants[]` 里，顶层没有 `sample_width` 类字段 ——
 *    写死一对常量会让整站所有卡按同一个比例摆；
 * 3. 视频条目的 `large_file_url` 就是原片 mp4（与 `file_url` 同址）—— 直接当图用会
 *    先下整片再解码失败；
 * 4. 档名里的数字是**外接框**（`180x180` 档实测是 135×180），拿档名算比例必错。
 *
 * 另有一条实测前提记在这里，因为它决定了 [GalleryPost.isAdultMarked] 那条红线的代价：
 * **Safebooru 全站只有 `g` 一档**（`rating:s`/`rating:q`/`rating:e` 各回 0 条，
 * 而 `rating:g` 有结果 —— 它是纯全年龄镜像）。所以 `g` 一旦漏进安全档，
 * 表现不是"少数几张被误打码"，而是**整站每一张都被打码**。
 */
class GallerySafebooruParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 实测原文：一条有 sample 降档的竖图（3000×4000，sample 850×1133，preview 135×180）。
     * `fav_count` 170 是站方真数 —— 另两站没有这一字段。
     */
    private val imageEntry = """
        {"id":12290033,"rating":"g","score":190,"fav_count":170,
         "source":"https://x.com/vlizzyvlizz117/status/2105348196683943972",
         "file_ext":"jpg","image_width":3000,"image_height":4000,
         "preview_file_url":"https://cdn.donmai.us/180x180/9c/fe/9cfea6cbed0860e4f08a46b81edaebcf.jpg",
         "large_file_url":"https://cdn.donmai.us/sample/9c/fe/sample-9cfea6cbed0860e4f08a46b81edaebcf.jpg",
         "file_url":"https://cdn.donmai.us/original/9c/fe/9cfea6cbed0860e4f08a46b81edaebcf.jpg",
         "file_size":2713926,"md5":"9cfea6cbed0860e4f08a46b81edaebcf","has_large":true,
         "tag_string_artist":"vlizz",
         "tag_string":"1boy 2koma 3girls absurdres angry blonde_hair blurry harry_potter hermione_granger highres ron_weasley slytherin vlizz wizarding_world",
         "media_asset":{"file_ext":"jpg","duration":null,"image_width":3000,"image_height":4000,
           "variants":[
             {"type":"180x180","url":"https://cdn.donmai.us/180x180/9c/fe/9cfea6cbed0860e4f08a46b81edaebcf.jpg","width":135,"height":180,"file_ext":"jpg"},
             {"type":"360x360","url":"https://cdn.donmai.us/360x360/9c/fe/9cfea6cbed0860e4f08a46b81edaebcf.jpg","width":270,"height":360,"file_ext":"jpg"},
             {"type":"720x720","url":"https://cdn.donmai.us/720x720/9c/fe/9cfea6cbed0860e4f08a46b81edaebcf.webp","width":540,"height":720,"file_ext":"webp"},
             {"type":"sample","url":"https://cdn.donmai.us/sample/9c/fe/sample-9cfea6cbed0860e4f08a46b81edaebcf.jpg","width":850,"height":1133,"file_ext":"jpg"},
             {"type":"original","url":"https://cdn.donmai.us/original/9c/fe/9cfea6cbed0860e4f08a46b81edaebcf.jpg","width":3000,"height":4000,"file_ext":"jpg"}]}}
    """.trimIndent()

    /**
     * 实测原文：一条**视频**（mp4，9.06 秒）。坑 3 与坑 4 同时在：
     * `large_file_url` 就是原片，而静帧是 `720x720` 那一档的 **webp**。
     */
    private val videoEntry = """
        {"id":12294302,"rating":"g","score":1,"fav_count":1,
         "source":"https://x.com/Y75Zei/status/2093321681695842431",
         "file_ext":"mp4","image_width":720,"image_height":720,
         "preview_file_url":"https://cdn.donmai.us/180x180/64/0c/640cab5dee0a1c835efaab0fa1594f56.jpg",
         "large_file_url":"https://cdn.donmai.us/original/64/0c/640cab5dee0a1c835efaab0fa1594f56.mp4",
         "file_url":"https://cdn.donmai.us/original/64/0c/640cab5dee0a1c835efaab0fa1594f56.mp4",
         "file_size":361492,"md5":"640cab5dee0a1c835efaab0fa1594f56","has_large":false,
         "tag_string_artist":"y75zei",
         "tag_string":"2girls animated blue_hair inu_sakuya_(nejikirio) remilia_scarlet sound touhou video y75zei",
         "media_asset":{"file_ext":"mp4","duration":9.055782,"image_width":720,"image_height":720,
           "variants":[
             {"type":"180x180","url":"https://cdn.donmai.us/180x180/64/0c/640cab5dee0a1c835efaab0fa1594f56.jpg","width":180,"height":180,"file_ext":"jpg"},
             {"type":"360x360","url":"https://cdn.donmai.us/360x360/64/0c/640cab5dee0a1c835efaab0fa1594f56.jpg","width":360,"height":360,"file_ext":"jpg"},
             {"type":"720x720","url":"https://cdn.donmai.us/720x720/64/0c/640cab5dee0a1c835efaab0fa1594f56.webp","width":720,"height":720,"file_ext":"webp"},
             {"type":"original","url":"https://cdn.donmai.us/original/64/0c/640cab5dee0a1c835efaab0fa1594f56.mp4","width":720,"height":720,"file_ext":"mp4"}]}}
    """.trimIndent()

    /**
     * 实测原文：一条 `has_large=false` 的图（779×779）—— 站方没有 sample 档，
     * `large_file_url` 与 `file_url` **同址**，`variants` 里也没有 `sample` 那一档。
     */
    private val noSampleEntry = """
        {"id":12294341,"rating":"g","score":0,"fav_count":0,
         "source":"https://i.pximg.net/img-original/img/2016-05-17/04/55/39/56924670_p0.jpg",
         "file_ext":"jpg","image_width":779,"image_height":779,
         "preview_file_url":"https://cdn.donmai.us/180x180/a6/e5/a6e5f2386fb4a38df587c0ddeaf3dc5a.jpg",
         "large_file_url":"https://cdn.donmai.us/original/a6/e5/a6e5f2386fb4a38df587c0ddeaf3dc5a.jpg",
         "file_url":"https://cdn.donmai.us/original/a6/e5/a6e5f2386fb4a38df587c0ddeaf3dc5a.jpg",
         "file_size":465667,"md5":"a6e5f2386fb4a38df587c0ddeaf3dc5a","has_large":false,
         "tag_string_artist":"qitoli",
         "tag_string":"black_shirt blue_archive cape devil_survivor_(series) from_behind male_focus qitoli red_pants shirt",
         "media_asset":{"file_ext":"jpg","duration":null,"image_width":779,"image_height":779,
           "variants":[
             {"type":"180x180","url":"https://cdn.donmai.us/180x180/a6/e5/a6e5f2386fb4a38df587c0ddeaf3dc5a.jpg","width":180,"height":180,"file_ext":"jpg"},
             {"type":"360x360","url":"https://cdn.donmai.us/360x360/a6/e5/a6e5f2386fb4a38df587c0ddeaf3dc5a.jpg","width":360,"height":360,"file_ext":"jpg"},
             {"type":"720x720","url":"https://cdn.donmai.us/720x720/a6/e5/a6e5f2386fb4a38df587c0ddeaf3dc5a.webp","width":720,"height":720,"file_ext":"webp"},
             {"type":"original","url":"https://cdn.donmai.us/original/a6/e5/a6e5f2386fb4a38df587c0ddeaf3dc5a.jpg","width":779,"height":779,"file_ext":"jpg"}]}}
    """.trimIndent()

    /**
     * 实测原文：同池子里**另一条**有 sample 降档的图（2978×3461 → sample 850×988）。
     * 摆两条是为了反证"写死一对常量"：同一站同一档名，实际尺寸并不相同。
     */
    private val secondImageEntry = """
        {"id":12291394,"rating":"g","fav_count":76,
         "file_ext":"jpg","image_width":2978,"image_height":3461,
         "preview_file_url":"https://cdn.donmai.us/180x180/e2/80/e280e12e53ab6cb130e325b1df122ca4.jpg",
         "large_file_url":"https://cdn.donmai.us/sample/e2/80/sample-e280e12e53ab6cb130e325b1df122ca4.jpg",
         "file_url":"https://cdn.donmai.us/original/e2/80/e280e12e53ab6cb130e325b1df122ca4.jpg",
         "file_size":1003148,"md5":"e280e12e53ab6cb130e325b1df122ca4","has_large":true,
         "tag_string_artist":"dixretsa",
         "tag_string":"1girl absurdres black_coat black_shirt blonde_hair blush coat collared_shirt",
         "media_asset":{"file_ext":"jpg","duration":null,
           "variants":[
             {"type":"180x180","url":"https://cdn.donmai.us/180x180/e2/80/e280e12e53ab6cb130e325b1df122ca4.jpg","width":155,"height":180,"file_ext":"jpg"},
             {"type":"360x360","url":"https://cdn.donmai.us/360x360/e2/80/e280e12e53ab6cb130e325b1df122ca4.jpg","width":310,"height":360,"file_ext":"jpg"},
             {"type":"720x720","url":"https://cdn.donmai.us/720x720/e2/80/e280e12e53ab6cb130e325b1df122ca4.webp","width":620,"height":720,"file_ext":"webp"},
             {"type":"sample","url":"https://cdn.donmai.us/sample/e2/80/sample-e280e12e53ab6cb130e325b1df122ca4.jpg","width":850,"height":988,"file_ext":"jpg"},
             {"type":"original","url":"https://cdn.donmai.us/original/e2/80/e280e12e53ab6cb130e325b1df122ca4.jpg","width":2978,"height":3461,"file_ext":"jpg"}]}}
    """.trimIndent()

    /** 实测原文：`/tags.json?search[name_matches]=hatsune*`，张数叫 `post_count`、分类叫 `category`。 */
    private val tagEntries = """
        [{"id":2728183,"name":"hatsune_miku_(miku_with_you_2026)","post_count":7,"category":4,
          "created_at":"2026-09-12T01:26:18.411-04:00","is_deprecated":false},
         {"id":2721713,"name":"hatsune_miku_(p4:_dancing_all_night)","post_count":1,"category":0,
          "created_at":"2026-09-03T17:45:19.234-04:00","is_deprecated":true}]
    """.trimIndent()

    private fun postsOf(raw: String): List<GalleryPost> =
        listOf(json.decodeFromString<SafebooruPostDto>(raw).toPost())

    @Test
    fun `标签串取自 tag_string，站方没有 tags 这个键`() {
        // 这一条是整站最贵的坑：按 `tags` 声明不会抛异常，只会让每一张卡的标签数是 0。
        val post = postsOf(imageEntry).single()
        assertTrue(post.tagList.isNotEmpty())
        assertTrue(post.tagList.contains("harry_potter"))
        // 画师正名来自 tag_string_artist（另两站没有这一串）。
        assertEquals("vlizz", post.author)
    }

    @Test
    fun `g 档不被打码`() {
        // 实测 Safebooru 全站只有 g 一档（rating:s/q/e 各 0 条）：这一条挂了整站就全瞎。
        val post = postsOf(imageEntry).single()
        assertEquals("g", post.rating)
        assertTrue(!post.isAdultMarked)
        // 同名字形（yande.re 的 s）与单词字形（Gelbooru 的 general）继续算安全；q/e 不算。
        assertTrue(!GalleryPost(site = GallerySite.SAFEBOORU, id = 1, rating = "s").isAdultMarked)
        assertTrue(GalleryPost(site = GallerySite.SAFEBOORU, id = 1, rating = "q").isAdultMarked)
        assertTrue(GalleryPost(site = GallerySite.SAFEBOORU, id = 1, rating = "e").isAdultMarked)
    }

    @Test
    fun `中档尺寸取自 variants，不是写死的那一对`() {
        // 曾经写死 850×1133（来自最早探的那一条）—— 同一池子里实测还见过
        // 850×988 / 850×1416 / 850×1074，写死等于整站所有卡按同一个假比例摆。
        val post = postsOf(imageEntry).single()
        assertEquals(850, post.largeWidth)
        assertEquals(1133, post.largeHeight)
        assertEquals(850.0 / 1133.0, post.cardRatio.toDouble(), 1e-4)
    }

    @Test
    fun `两条同站同档名的条目算出两个不同的比例`() {
        val first = postsOf(imageEntry).single()          // sample 850×1133
        val second = postsOf(secondImageEntry).single()   // sample 850×988
        assertEquals(850, second.largeWidth)
        assertEquals(988, second.largeHeight)
        assertNotEquals(first.cardRatio, second.cardRatio)
        assertEquals(850.0 / 988.0, second.cardRatio.toDouble(), 1e-4)
        // 缩略档也跟着各走各的（135×180 与 155×180）。
        assertEquals(155, second.previewWidth)
    }

    @Test
    fun `档名是外接框，缩略图实际尺寸跟着 variants 走`() {
        // `180x180` 这一档对 3000×4000 的竖图实测是 **135×180**，不是 180×180。
        val post = postsOf(imageEntry).single()
        assertEquals(135, post.previewWidth)
        assertEquals(180, post.previewHeight)
        assertEquals(
            "https://cdn.donmai.us/180x180/9c/fe/9cfea6cbed0860e4f08a46b81edaebcf.jpg",
            post.previewUrl,
        )
    }

    @Test
    fun `开门档取 360 那一档而不是 180`() {
        // 与另两站的 preview 级同口径（yande 300×212 / Gelbooru 350px 级）：
        // 180 铺满一屏会糊到认不出画的是什么（2026-10-01 用户报 hero 糊的同一类成因）。
        val post = postsOf(imageEntry).single()
        assertNotEquals(post.previewUrl, post.fastUrl)
        assertTrue(post.fastUrl.contains("/360x360/"))
    }

    @Test
    fun `has_large 为假时中档兜底到原图，尺寸跟着用原图那一对`() {
        val post = postsOf(noSampleEntry).single()
        assertEquals(post.fileUrl, post.largeUrl)
        assertTrue(post.largeUrl.contains("/original/"))
        assertEquals(779, post.largeWidth)
        assertEquals(779, post.largeHeight)
    }

    @Test
    fun `视频条目的中档是站方静帧，绝不能是原片`() {
        // 实测 large_file_url 对视频就是原片 mp4（与 file_url 同址）。
        // 交给 Coil = 先下整片再解码失败，屏上什么都没有（真机表现"视频黑屏"）。
        val post = postsOf(videoEntry).single()
        assertTrue(post.isVideo)
        assertNotEquals(post.fileUrl, post.largeUrl)
        assertTrue(post.largeUrl.endsWith(".webp"))
        // 静帧与原片不同址，所以底图判据直接吃它（与 yande.re 那一路 jpeg_url 静帧同形）。
        assertEquals(post.largeUrl, post.videoPosterUrl)
        assertEquals(post.largeUrl, post.backdropUrl)
    }

    @Test
    fun `时长取自 media_asset_duration，图片条目保持 null`() {
        // 这一站**有**时长（另两站的 JSON 里没有 → 那边一律 null）。
        assertEquals(9.055782, postsOf(videoEntry).single().durationSeconds!!, 1e-6)
        // 图片条目站方给的是 JSON null，不是 0 —— "0 秒"是被编出来的事实。
        assertNull(postsOf(imageEntry).single().durationSeconds)
        assertNull(postsOf(noSampleEntry).single().durationSeconds)
    }

    @Test
    fun `fav_count 是站方真数而不是 null`() {
        // GalleryPost 的注释原先写"两站都没有这一字段"，这一站有（实测 170）。
        val post = postsOf(imageEntry).single()
        assertEquals(170, post.favCount)
        assertEquals(0, postsOf(noSampleEntry).single().favCount)
    }

    @Test
    fun `单页地址与归一标识按实测形态拼`() {
        val post = postsOf(imageEntry).single()
        // 实测 https://safebooru.donmai.us/posts/12290033 → 200（与主站同形，不是 /post/show/）。
        assertEquals("https://safebooru.donmai.us/posts/12290033", post.pageUrl)
        assertEquals("safebooru:12290033", post.uid)
        assertEquals(GallerySite.SAFEBOORU, post.site)
        assertEquals("jpg", post.fileExt)
        assertEquals(2713926L, post.fileSize)
    }

    @Test
    fun `缺 media_asset 时不崩，比例退回可得的那一对`() {
        // 站方在个别条目上不嵌 media_asset（匿名读偶发缺块）。没有 variants 就只有
        // 顶层的 image_width/height —— 它降档后比例不变，所以中档尺寸用它兜底是真值。
        val bare = """
            {"id":9,"rating":"g","fav_count":0,"file_ext":"jpg",
             "image_width":1200,"image_height":800,
             "preview_file_url":"https://cdn.donmai.us/180x180/aa/bb/x.jpg",
             "large_file_url":"https://cdn.donmai.us/sample/aa/bb/sample-x.jpg",
             "file_url":"https://cdn.donmai.us/original/aa/bb/x.jpg","md5":"x","tag_string":"a b"}
        """.trimIndent()
        val post = json.decodeFromString<SafebooruPostDto>(bare).toPost()
        // 中档地址仍然指向 sample（站方给的就是它），只是没有单独的降档尺寸。
        assertEquals("https://cdn.donmai.us/sample/aa/bb/sample-x.jpg", post.largeUrl)
        assertEquals(0, post.largeWidth)
        // cardRatio 于是退回 previewWidth（缺 → 0）再到 width/height，比例仍是 1.5。
        assertEquals(1.5, post.cardRatio.toDouble(), 1e-4)
        assertEquals(2, post.tagList.size)
    }

    @Test
    fun `tag 端点的字段名按实测的 post_count_category，弃用标记站方给了就用`() {
        val tags = json.decodeFromString<List<SafebooruTagDto>>(tagEntries)
        val suggestions = tags.map { it.toSuggestion() }
        assertEquals(GallerySite.SAFEBOORU, suggestions.first().site)
        assertEquals("hatsune_miku_(miku_with_you_2026)", suggestions.first().name)
        // 张数叫 post_count（Gelbooru 叫 count）、分类叫 category（Gelbooru 叫 type）。
        assertEquals(7, suggestions.first().count)
        assertEquals(4, suggestions.first().category)
        assertEquals("角色", suggestions.first().categoryLabel)
        // Gelbooru 那边没有 is_deprecated 只能一律 false，这一站站方直接给 —— 给真值。
        assertTrue(suggestions.first().deprecated.not())
        assertTrue(suggestions[1].deprecated)
    }
}
