package com.venera.compose.gallery

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.KonachanPostDto
import com.venera.compose.gallery.data.KonachanTagDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 按**实测响应**锁住 Konachan 的字段解析与归一翻译。
 *
 * （裁掉了与解析无关的相邻键；站方那几个**把全部标签拼进文件名**的超长 URL 缩成了同形短名，
 * 键与其余取值逐字照抄 —— 断言只吃键名与数值，不吃 URL 尾巴），不是照另一站文档编的样本 ——
 * 这一站与 yande.re 同内核（Moebooru），字段形状**几乎逐键相同**，而上一代图库源
 * Safebooru（Danbooru 系）是另一套字形，两系互换必炸（见下"三条静默坑"）。
 *
 * 1. 标签串就叫 `tags`（Moebooru 字形）。上一代 Safebooru 叫 `tag_string`、那边**没有** `tags` 键 ——
 *    把两系键名互换，HTTP 200、正文合法，卡片却一枚标签都没有，屏蔽规则与标签翻译同时静默失效；
 * 2. 降档尺寸有**现成的顶层** `sample_width/height`（实测 1500×903 / 1500×1061 各条不同）——
 *    写死一对常量会让整站所有卡按同一个比例摆（同 yande.re 那条纪律，本站同样适用）；
 * 3. `jpeg_url` **不是降档**：实测 `jpeg_width/height` 恒等于 `width/height`（2000×1204 → 2000×1204），
 *    它是原分辨率 JPEG 重编码 —— 拿它当中档 = 每次开门先下一个原图大小的文件（yande.re 同款坑）。
 *
 * 另有一条实测前提记在这里，因为它决定了 [GalleryPost.isAdultMarked] 那条红线的代价：
 * **`.net` 镜像实测只出 `s` 一档**（500 条样本全 `s`，`rating:e` 回 0 条）。`s` 本来就在安全档
 * 白名单里（yande.re 同字形共用）；本站**没有** Danbooru 系的 `g`，别把那站的字形表照抄进来。
 * 而 `fav_count` / `media_asset.duration` 是 Danbooru 系（旧 Safebooru）独有的格子，
 * 本站实测 44 键里**都没有** → 归一模型那两格在这里恒 null，各有一条用例钉住"编不出事实"。
 */
class GalleryKonachanParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 实测原文（`order:score` 榜首）：高分 1718 的横图，2000×1204 → sample 1500×903。
     * `jpeg_*` 恒等原尺寸（2000×1204）—— 中档绝不能吃它（坑 3）。
     */
    private val hotImageEntry = """
        {"id":220877,"tags":"book brown_eyes cao_xiong headphones leaves long_hair makise_kurisu pantyhose purple_hair reflection shorts steins;gate third-party_edit tie",
         "rating":"s","score":1718,
         "source":"http://www.pixiv.net/member_illust.php?mode=medium&illust_id=22889135",
         "md5":"09d247edf506efacca7ead758bf307b2","file_size":3111826,"file_ext":"png",
         "file_url":"https://konachan.net/image/09/d2/09d247edf506efacca7ead758bf307b2/big.png",
         "preview_url":"https://konachan.net/data/preview/09/d2/09d247edf506efacca7ead758bf307b2.jpg",
         "actual_preview_width":300,"actual_preview_height":181,
         "sample_url":"https://konachan.net/sample/09/d2/09d247edf506efacca7ead758bf307b2/sample.jpg",
         "sample_width":1500,"sample_height":903,
         "jpeg_url":"https://konachan.net/jpeg/09/d2/09d247edf506efacca7ead758bf307b2/full.jpg",
         "jpeg_width":2000,"jpeg_height":1204,
         "width":2000,"height":1204,
         "author":"Dummy"}
    """.trimIndent()

    /**
     * 实测原文（最新页）：2481×1754 → sample 1500×1061。
     * 与上面那条**同站同档名、不同实际比例**（903 vs 1061）—— 反证"写死一对常量"（坑 2）。
     * 这条作者名是上传者账号（`BattlequeenYume`），**不是**画师标签（Moebooru 语义，见 [GalleryPost.author]）。
     */
    private val newestEntry = """
        {"id":410585,"tags":"ardenlolo blue_eyes blush bow breasts brown_hair choker cleavage clouds dress elbow_gloves flowers gloves goddess_of_victory:_nikke headband headdress leaves long_hair marian_(nikke) petals rose sky watermark wedding wedding_attire",
         "created_at":1794315293,"updated_at":1794315293,
         "rating":"s","score":6,
         "source":"",
         "md5":"96360757d728d64a9d6e96de829342cf","file_size":10039012,"file_ext":"png",
         "file_url":"https://konachan.net/image/96/36/96360757d728d64a9d6e96de829342cf/big.png",
         "preview_url":"https://konachan.net/data/preview/96/36/96360757d728d64a9d6e96de829342cf.jpg",
         "preview_width":150,"preview_height":106,
         "actual_preview_width":300,"actual_preview_height":212,
         "sample_url":"https://konachan.net/sample/96/36/96360757d728d64a9d6e96de829342cf/sample.jpg",
         "sample_width":1500,"sample_height":1061,
         "jpeg_url":"https://konachan.net/jpeg/96/36/96360757d728d64a9d6e96de829342cf/full.jpg",
         "jpeg_width":2481,"jpeg_height":1754,
         "width":2481,"height":1754,"status":"active",
         "author":"BattlequeenYume"}
    """.trimIndent()

    /**
     * 合成原文（**不是**站方样例）：万一站方哪天 `sample_url` 给空 —— 中档兜底到
     * `jpeg_url`（同一张画的另一个真容器），尺寸跟着走 `jpeg_*`。实测 500/500 条 sample 非空，
     * 这一支按理走不到；留着它与"绝不留编出来的地址"同一条纪律。
     */
    private val noSampleEntry = """
        {"id":9,"tags":"a b","rating":"s","score":0,
         "md5":"aabb","file_ext":"jpg","file_size":100,
         "file_url":"https://konachan.net/image/aa/bb/aabb.jpg",
         "preview_url":"https://konachan.net/data/preview/aa/bb/aabb.jpg",
         "preview_width":150,"preview_height":100,
         "actual_preview_width":300,"actual_preview_height":200,
         "sample_url":"","sample_width":0,"sample_height":0,
         "jpeg_url":"https://konachan.net/jpeg/aa/bb/aabb.jpg",
         "jpeg_width":1200,"jpeg_height":800,
         "width":1200,"height":800,"author":""}
    """.trimIndent()

    /**
     * 实测原文（`tag.json?name=maid*&order=count`）：张数叫 **`count`**、分类叫 **`type`**
     * （Moebooru 字形，同 yande.re；不是 Danbooru 系的 `post_count`/`category`），
     * 而且**没有** `is_deprecated` 这个键。
     */
    private val tagEntries = """
        [{"id":60,"name":"maid","count":8434,"type":0,"ambiguous":false},
         {"id":137474,"name":"maid_dolce","count":13,"type":3,"ambiguous":false}]
    """.trimIndent()

    private fun postsOf(raw: String): List<GalleryPost> =
        listOf(json.decodeFromString<KonachanPostDto>(raw).toPost())

    @Test
    fun `标签串取自 tags，不是上一代的 tag_string`() {
        // 两系键名互换不会抛异常，只会让每一张卡的标签数是 0（静默坑 1）。
        val post = postsOf(hotImageEntry).single()
        assertTrue(post.tagList.isNotEmpty())
        assertTrue(post.tagList.contains("makise_kurisu"))
    }

    @Test
    fun `s 档安全而 q_e 打码，未知值同样打码`() {
        // .net 镜像实测只出 s（安全档白名单里本来就有，同 yande.re 字形共用）。
        val post = postsOf(hotImageEntry).single()
        assertEquals("s", post.rating)
        assertTrue(!post.isAdultMarked)
        assertTrue(GalleryPost(site = GallerySite.KONACHAN, id = 1, rating = "q").isAdultMarked)
        assertTrue(GalleryPost(site = GallerySite.KONACHAN, id = 1, rating = "e").isAdultMarked)
        // 空串与未知值一律落"成人"：方向是宁可错打码，不是放行（isAdultMarked 的白名单判式）。
        assertTrue(GalleryPost(site = GallerySite.KONACHAN, id = 1, rating = "").isAdultMarked)
    }

    @Test
    fun `中档尺寸取自顶层 sample 那一对，不是写死的常量`() {
        // 同池子里实测 sample 是 1500×903 与 1500×1061 两条不同比例（静默坑 2），
        // 写死任何一对都会让整站卡按同一个假比例摆。
        val post = postsOf(hotImageEntry).single()
        assertEquals(1500, post.largeWidth)
        assertEquals(903, post.largeHeight)
        assertEquals(1500.0 / 903.0, post.cardRatio.toDouble(), 1e-4)
    }

    @Test
    fun `两条同站条目的 sample 算出两个不同的比例`() {
        val first = postsOf(hotImageEntry).single()   // sample 1500×903
        val second = postsOf(newestEntry).single()    // sample 1500×1061
        assertNotEquals(first.cardRatio, second.cardRatio)
        assertEquals(1500.0 / 1061.0, second.cardRatio.toDouble(), 1e-4)
    }

    @Test
    fun `中档绝不能吃 jpeg——它是原分辨率重编码不是降档`() {
        // 实测 jpeg_width/height 恒等于 width/height（2000×1204；yande.re 同款坑，静默坑 3）：
        // 拿它当中档 = 每次打开先下一个原图大小的文件。
        val post = postsOf(hotImageEntry).single()
        assertTrue(post.largeUrl.contains("/sample/"))
        assertNotEquals(post.fileUrl, post.largeUrl)
        assertEquals(2000, post.width)
        assertEquals(1204, post.height)
        // 而原档那一格仍归 file_url，比例与原尺寸一致。
        assertTrue(post.fileUrl.contains("/image/"))
    }

    @Test
    fun `开门档与网格卡同址——preview_url，缓存白拿`() {
        // Moebooru 没有"更中间的一档"，300 级 preview 与另两站同口径（同 yande.re 那一路模型）。
        val post = postsOf(hotImageEntry).single()
        assertEquals(post.previewUrl, post.fastUrl)
        // 摆放前比例吃 actual_preview（300×181），不是被裁过的 preview_width（150×90）。
        assertEquals(300, post.previewWidth)
        assertEquals(181, post.previewHeight)
    }

    @Test
    fun `sample 为空的合成兜底：中档退到 jpeg 容器，尺寸跟着走`() {
        val post = json.decodeFromString<KonachanPostDto>(noSampleEntry).toPost()
        assertTrue(post.largeUrl.contains("/jpeg/"))
        assertEquals(1200, post.largeWidth)
        assertEquals(800, post.largeHeight)
        assertEquals(1.5, post.cardRatio.toDouble(), 1e-4)
        assertEquals(2, post.tagList.size)
    }

    @Test
    fun `fav_count 与 duration 本站没有——交 null 而不是编一个数`() {
        // "0"与"0 秒"是被编出来的事实 —— 归一模型那两格在这里恒 null。
        val post = postsOf(hotImageEntry).single()
        assertNull(post.favCount)
        assertNull(post.durationSeconds)
        val second = postsOf(newestEntry).single()
        assertNull(second.favCount)
        assertNull(second.durationSeconds)
    }

    @Test
    fun `author 是上传者账号名，不能当画师捷径`() {
        // Moebooru 语义：`author` 同 yande.re（上传者）。画师识别走标签词典/页面分类，
        // GallerySaver.resolveBooruArtist 与 GalleryCardActionSheet 因此**不**给本站开捷径。
        val post = postsOf(hotImageEntry).single()
        assertEquals("Dummy", post.author)
    }

    @Test
    fun `单页地址与归一标识按实测形态拼`() {
        val post = postsOf(hotImageEntry).single()
        // 实测 https://konachan.net/post/show/220877 → 200（Moebooru 写法，/posts/{id} 那形是 Danbooru 系）。
        assertEquals("https://konachan.net/post/show/220877", post.pageUrl)
        assertEquals("konachan:220877", post.uid)
        assertEquals(GallerySite.KONACHAN, post.site)
        assertEquals("png", post.fileExt)
        assertEquals(3111826L, post.fileSize)
    }

    @Test
    fun `tag 端点的字段名按实测的 count_type，弃用位一律 false`() {
        val tags = json.decodeFromString<List<KonachanTagDto>>(tagEntries)
        val suggestions = tags.map { it.toSuggestion() }
        assertEquals(GallerySite.KONACHAN, suggestions.first().site)
        assertEquals("maid", suggestions.first().name)
        // 张数叫 count（不是 Danbooru 系的 post_count）、分类叫 type（不是 category）。
        assertEquals(8434, suggestions.first().count)
        assertEquals(0, suggestions.first().category)
        assertEquals("通用", suggestions.first().categoryLabel)
        assertEquals(3, suggestions[1].category)
        // 本站没有 is_deprecated 这个键 → 一律 false（与 yande.re 那颗 DTO 同一条纪律：
        // 别拿 ambiguous——那是"标签有歧义"，与"已废弃"是两件事——去顶替）。
        assertTrue(suggestions.first().deprecated.not())
        assertTrue(suggestions[1].deprecated.not())
    }
}
