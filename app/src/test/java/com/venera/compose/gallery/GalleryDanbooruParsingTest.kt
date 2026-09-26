package com.venera.compose.gallery

import com.venera.compose.gallery.data.DanbooruDto
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 按**实测响应**锁住 Danbooru 的字段解析与归一翻译。
 *
 * 素材取自 2026-09-25 的探针（一条真实 post，字段名与取值原样保留）。
 * 这一站与 yande.re 的差别每一条都是实测的、都不是照另一端点推断的：
 * 中间档叫 `large_file_url`、尺寸叫 `image_width/height`、档位宽高**只藏在 `media_asset.variants[]` 里**、
 * 没有 `author` 字段（画师在 `tag_string_artist`）、**有** `fav_count`、分级多出 `g` 一档。
 */
class GalleryDanbooruParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(raw: String = sample): List<GalleryPost> =
        json.decodeFromString<List<DanbooruDto>>(raw).map { it.toPost() }

    private val sample = """
    [{
      "id": 12253020,
      "created_at": "2026-09-24T12:56:01.520-04:00",
      "score": 0,
      "up_score": 0,
      "down_score": 0,
      "source": "Scanned by me from a doujinshi",
      "md5": "abad471f3f74fe9e9905e75505d9496d",
      "rating": "s",
      "image_width": 5685,
      "image_height": 8000,
      "tag_string": "1girl absurdres highres hood mahou_shoujo_madoka_magica oumi_neneha pink_hair scan solo",
      "tag_string_general": "1girl absurdres hood pink_hair solo",
      "tag_string_artist": "oumi_neneha",
      "tag_string_character": "",
      "tag_string_copyright": "mahou_shoujo_madoka_magica",
      "tag_string_meta": "highres",
      "fav_count": 0,
      "file_ext": "jpg",
      "file_size": 10894877,
      "has_large": true,
      "is_banned": false,
      "pixiv_id": null,
      "media_asset": {
        "id": 50200150,
        "md5": "abad471f3f74fe9e9905e75505d9496d",
        "file_ext": "jpg",
        "status": "active",
        "pixel_hash": "c45664c1c724d6f2880f2b3d0e1fbb54",
        "variants": [
          { "type": "180x180", "url": "https://cdn.donmai.us/180x180/ab/ad/abad471f3f74fe9e9905e75505d9496d.jpg", "width": 128, "height": 180, "file_ext": "jpg" },
          { "type": "360x360", "url": "https://cdn.donmai.us/360x360/ab/ad/abad471f3f74fe9e9905e75505d9496d.jpg", "width": 256, "height": 360, "file_ext": "jpg" },
          { "type": "720x720", "url": "https://cdn.donmai.us/720x720/ab/ad/abad471f3f74fe9e9905e75505d9496d.webp", "width": 512, "height": 720, "file_ext": "webp" },
          { "type": "sample", "url": "https://cdn.donmai.us/sample/ab/ad/sample-abad471f3f74fe9e9905e75505d9496d.jpg", "width": 850, "height": 1196, "file_ext": "jpg" },
          { "type": "original", "url": "https://cdn.donmai.us/original/ab/ad/abad471f3f74fe9e9905e75505d9496d.jpg", "width": 5685, "height": 8000, "file_ext": "jpg" }
        ]
      },
      "file_url": "https://cdn.donmai.us/original/ab/ad/abad471f3f74fe9e9905e75505d9496d.jpg",
      "large_file_url": "https://cdn.donmai.us/sample/ab/ad/sample-abad471f3f74fe9e9905e75505d9496d.jpg",
      "preview_file_url": "https://cdn.donmai.us/180x180/ab/ad/abad471f3f74fe9e9905e75505d9496d.jpg"
    }]
    """.trimIndent()

    @Test
    fun `三档地址归一到与另一站同一套名字`() {
        val post = decode().first()
        assertEquals("https://cdn.donmai.us/180x180/ab/ad/abad471f3f74fe9e9905e75505d9496d.jpg", post.previewUrl)
        // 二级要的是 sample 档（850×1196），不是原图 —— 顶层 large_file_url 与它是同一个地址，
        // 但**宽高只在 variants 里**，所以尺寸必须从变体取，不能拿 image_width 顶替。
        assertEquals("https://cdn.donmai.us/sample/ab/ad/sample-abad471f3f74fe9e9905e75505d9496d.jpg", post.largeUrl)
        assertEquals(850, post.largeWidth)
        assertEquals(1196, post.largeHeight)
        assertEquals("https://cdn.donmai.us/original/ab/ad/abad471f3f74fe9e9905e75505d9496d.jpg", post.fileUrl)
    }

    @Test
    fun `画师从 tag_string_artist 翻译`() {
        val post = decode().first()
        // 实测这一站**没有** author 字段。留空会让二级信息卡的"作者"永远是"—"，
        // 而数据其实是给全的 —— 那是"实现了但没接上"那类缺口。
        assertEquals("oumi_neneha", post.author)
    }

    @Test
    fun `标签按站方那五串分桶，空桶不摆`() {
        val post = decode().first()
        // 实测这一站五串全给；本条 fixture 的 `tag_string_character` 是空的（真数据里也常有这种），
        // 空桶要整个不出现 —— 摆一个标题写着"角色"的空区比不摆更糟。
        assertEquals(listOf("通用", "画师", "作品", "元数据"), post.tagGroups.map { it.label })
        assertEquals(listOf("1girl", "absurdres", "hood", "pink_hair", "solo"), post.tagGroups.first().tags)
        assertEquals(listOf("oumi_neneha"), post.tagGroups[1].tags)
        // 分桶只给人看：黑名单判定仍吃平铺那串（换成按桶判会让跨桶的 tag 漏判）。
        assertEquals(9, post.tagList.size)
    }

    /**
     * 「关于这张图」里每一枚 chip 的原串都必须能在 `tagList` 里找到。
     *
     * 这条不是整理癖，是**长按菜单里那个「屏蔽」的成立条件**：屏蔽落库成 `TAG` 规则，
     * 而画廊那面墙的判据（`GalleryBlockMatch`）只拿 `post.tagList` 去比。桶里的 tag 要不在
     * tagList 里，那句"已屏蔽「x」"就成了假成功 —— toast 报了、图还在。
     *
     * 2026-09-25 实测 60 条 post、五桶共 2280 个 tag 串：**没有一个**不在 `tag_string` 里
     * （`tag_string` 是这五串的合集，还额外带着没归到任何桶的 `scan` 这类）。
     * 以前这份 fixture 把 `tag_string` 少写了 `highres` 与 `mahou_shoujo_madoka_magica` 两支，
     * 那是按"tag_string 只是 general"的想当然写的，现已按实测改回来。
     */
    @Test
    fun `五桶里每一枚标签都在 tagList 里 屏蔽规则才拦得住`() {
        val post = decode().first()
        post.tagGroups.forEach { group ->
            group.tags.forEach { tag ->
                assertTrue("${group.label} 的 $tag 不在 tagList 里", tag in post.tagList)
            }
        }
    }

    @Test
    fun `fav_count 是有值的整数而不是 null`() {
        val post = decode().first()
        assertEquals(0, post.favCount)
        // 与 yande.re 的对照写在这里：那边实测**没有**这个字段，归一后是 null。
        // 两者语义不同 —— 0 是"真没人收藏"，null 是"站方没给"，加权时不能混。
        assertEquals(null, GalleryPost(site = GallerySite.YANDERE, id = 1).favCount)
    }

    @Test
    fun `瀑布流比例与百万像素用 sample 档`() {
        val post = decode().first()
        assertEquals(850f / 1196f, post.cardRatio, 0.001f)
    }

    @Test
    fun `单页地址与站点身份`() {
        val post = decode().first()
        assertEquals("https://danbooru.donmai.us/posts/12253020", post.pageUrl)
        assertEquals(GallerySite.DANBOORU, post.site)
        // 两站同 id 必须是两个身份（列表 key / 缓存 key 全靠它）。
        assertEquals("danbooru:12253020", post.uid)
    }

    @Test
    fun `general 档不算成人`() {
        val g = decode(sample.replace("\"rating\": \"s\"", "\"rating\": \"g\"")).first()
        assertEquals(false, g.isAdultMarked)
        val e = decode(sample.replace("\"rating\": \"s\"", "\"rating\": \"e\"")).first()
        assertEquals(true, e.isAdultMarked)
    }

    @Test
    fun `没有 sample 变体时退回顶层 large 与原图尺寸`() {
        // 实测小图 has_large=false、variants 里就没有 sample 那一档。
        // 此时 large_file_url 就是可用的中间档，尺寸只能退回 image_width/height。
        val noSample = sample.replace(
            """{ "type": "sample", "url": "https://cdn.donmai.us/sample/ab/ad/sample-abad471f3f74fe9e9905e75505d9496d.jpg", "width": 850, "height": 1196, "file_ext": "jpg" },""",
            "",
        )
        val post = decode(noSample).first()
        assertEquals("https://cdn.donmai.us/sample/ab/ad/sample-abad471f3f74fe9e9905e75505d9496d.jpg", post.largeUrl)
        assertEquals(5685, post.largeWidth)
        assertEquals(8000, post.largeHeight)
    }

    // region 视频条目（实测 2026-09-25 日榜里的 #12252180，字段原样保留）

    private val videoSample = """
    [{
      "id": 12252180,
      "score": 317,
      "source": "https://x.com/Onokiwi/status/2103122284328227157",
      "md5": "1d7bb89f55796612f3e5ff0596c8bba3",
      "rating": "e",
      "image_width": 1920,
      "image_height": 1080,
      "tag_string": "animated 1girl blue_hair",
      "tag_string_artist": "nyudachl vanilla_flavor_(parfum-fraise)",
      "fav_count": 12,
      "file_ext": "mp4",
      "file_size": 16911522,
      "has_large": false,
      "is_banned": false,
      "media_asset": {
        "id": 50198471,
        "md5": "1d7bb89f55796612f3e5ff0596c8bba3",
        "file_ext": "mp4",
        "file_size": 16911522,
        "image_width": 1920,
        "image_height": 1080,
        "duration": 42.771156,
        "status": "active",
        "variants": [
          { "type": "180x180", "url": "https://cdn.donmai.us/180x180/1d/7b/1d7bb89f55796612f3e5ff0596c8bba3.jpg", "width": 180, "height": 101, "file_ext": "jpg" },
          { "type": "720x720", "url": "https://cdn.donmai.us/720x720/1d/7b/1d7bb89f55796612f3e5ff0596c8bba3.webp", "width": 720, "height": 405, "file_ext": "webp" },
          { "type": "original", "url": "https://cdn.donmai.us/original/1d/7b/1d7bb89f55796612f3e5ff0596c8bba3.mp4", "width": 1920, "height": 1080, "file_ext": "mp4" }
        ]
      },
      "file_url": "https://cdn.donmai.us/original/1d/7b/1d7bb89f55796612f3e5ff0596c8bba3.mp4",
      "large_file_url": "https://cdn.donmai.us/original/1d/7b/1d7bb89f55796612f3e5ff0596c8bba3.mp4",
      "preview_file_url": "https://cdn.donmai.us/180x180/1d/7b/1d7bb89f55796612f3e5ff0596c8bba3.jpg"
    }]
    """.trimIndent()

    @Test
    fun `视频条目的中间档必须是静帧而不是 mp4`() {
        val post = decode(videoSample).first()

        assertTrue(post.isVideo)
        // 实测视频**没有** sample 档，而顶层 large_file_url 就是原片 mp4；
        // 若照图片那条路走，二级会把 .mp4 交给 Coil 解码 = 一张永远加载失败的图。
        assertEquals("https://cdn.donmai.us/720x720/1d/7b/1d7bb89f55796612f3e5ff0596c8bba3.webp", post.largeUrl)
        assertEquals(720, post.largeWidth)
        assertEquals(405, post.largeHeight)
        // 播放地址另走一条字段，只有三级/播放器才碰它。
        assertEquals("https://cdn.donmai.us/original/1d/7b/1d7bb89f55796612f3e5ff0596c8bba3.mp4", post.videoUrl)
    }

    @Test
    fun `时长只从 media_asset 取 图片条目一律 null`() {
        val video = decode(videoSample).first()
        assertEquals(42.771156, video.durationSeconds!!, 0.0001)
        assertEquals("42″", video.durationLabel)

        // 实测顶层没有 duration 键，站方对图片也不给时长 → 不能编一个 0 出来。
        val still = decode().first()
        assertEquals(null, still.durationSeconds)
        assertEquals("", still.durationLabel)
    }

    @Test
    fun `视频没有静帧变体时退回 preview 而不是 mp4`() {
        val noStill = videoSample.replace(
            """{ "type": "720x720", "url": "https://cdn.donmai.us/720x720/1d/7b/1d7bb89f55796612f3e5ff0596c8bba3.webp", "width": 720, "height": 405, "file_ext": "webp" },""",
            "",
        )
        val post = decode(noStill).first()

        assertEquals("https://cdn.donmai.us/180x180/1d/7b/1d7bb89f55796612f3e5ff0596c8bba3.jpg", post.largeUrl)
    }

    @Test
    fun `瀑布流比例对视频取静帧尺寸`() {
        val post = decode(videoSample).first()
        assertEquals(720f / 405f, post.cardRatio, 0.001f)
    }

    // endregion
}
