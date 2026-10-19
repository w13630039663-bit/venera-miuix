package com.venera.compose.gallery

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.PixivFeedParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pixiv 两条匿名腿的形状判据（批次 pixiv · T3）。
 *
 * 夹具**不是编的**：两份都从 2026-10-16 深夜本机直连拿回的现量答复里裁下来
 * （榜单 `ranking.php?mode=daily&format=json` 的 `contents[0]` = 真号 150822243，
 * 标签 `ajax/search/artworks/1girl` 的 `data[0]` = 真号 150926978），只删掉与判据无关的键。
 * 留全形状的理由在 [PixivFeedParse] 的类注释里：**同一种意思在两腿里类型相反**，
 * 押任何一种 `@Serializable` DTO 都会让另一腿当场失败或静默解成默认值。
 */
class PixivFeedParseTest {

    /** 榜单：`illust_id` 是 number、`illust_page_count` 是 string、**没有分级格**、`prev`/`next` 是 boolean|number 混形。 */
    private val rankingBody = """
        {"mode":"daily","content":"all","page":1,"prev":false,"next":2,"date":"20261015","rank_total":500,
         "contents":[
           {"illust_id":150822243,"title":"落ち穂拾い","user_name":"某画师","user_id":36025620,
            "url":"https://i.pximg.net/c/480x960/img-master/img/2026-10-14/00/01/10/150822243_p0_master1200.jpg",
            "illust_type":"0","illust_page_count":"8","width":1446,"height":2048,
            "tags":["名画","3000users入り"],"rating_count":2402,"is_masked":false,
            "illust_content_type":{"sexual":0,"lo":false,"grotesque":false}},
           {"illust_id":7,"title":"被遮过的那一枚","user_name":"b","user_id":8,
            "url":"https://i.pximg.net/c/480x960/img-master/img/7_p0_master1200.jpg",
            "illust_type":"0","illust_page_count":"1","width":10,"height":20,"tags":[],"is_masked":true},
           {"title":"整枚记录都没有作品号（站方夹进来的推广位）"}
         ]}
    """.trimIndent()

    /** 标签那一腿：`id`/`userId` 是 string、`pageCount`/`xRestrict`/`width` 是 number、`bookmarkData` 给成 null。 */
    private fun tagBody(rows: String) = """
        {"error":false,"message":"","body":{"illustManga":{"total":60,"lastPage":1000,"data":[$rows]}}}
    """.trimIndent()

    private val tagRowGeneral =
        """{"id":"150926978","title":"T","illustType":0,"xRestrict":0,"restrict":0,"sl":2,"bookmarkData":null,
            "url":"https://i.pximg.net/c/250x250_80_a2/img-master/img/2026-10-16/22/38/44/150926978_p0_square1200.jpg",
            "tags":["1girl","eyes","女の子"],"userId":"129892029","userName":"Serika",
            "width":702,"height":1066,"pageCount":5,"aiType":2,"isMasked":false}"""

    @Test
    fun `榜单：作品号是数字也读得回来，没有作品号的那一枚不算一张图`() {
        val posts = PixivFeedParse.ranking(rankingBody)
        assertEquals(2, posts.size)
        assertEquals(150822243L, posts[0].post.id)
        assertEquals(GallerySite.PIXIV, posts[0].post.site)
        assertEquals("pixiv:150822243", posts[0].post.uid)
    }

    @Test
    fun `榜单：多页留住并且就用第 0 页那一张（用户 2026-10-16 定档）`() {
        val post = PixivFeedParse.ranking(rankingBody)[0].post
        // 站方给的 url 尾巴本来就是 `_p0`，所以这一档零额外请求（不必打 /ajax/illust/{id}/pages）。
        assertTrue("图址应当是第 0 页：${post.previewUrl}", post.previewUrl.contains("_p0_master1200.jpg"))
        assertEquals(1446, post.width)
        assertEquals(2048, post.height)
        assertEquals("jpg", post.fileExt)
    }

    @Test
    fun `榜单没有分级格：is_masked 走遮档，其余记成安全档（依据=匿名拿不到 R-18 那两档）`() {
        val posts = PixivFeedParse.ranking(rankingBody)
        assertEquals("general", posts[0].post.rating)
        assertFalse("匿名榜单里正常那一枚不该被打码", posts[0].post.isAdultMarked)
        assertEquals("masked", posts[1].post.rating)
        assertTrue("站方自己已经遮过的那一枚要落进成人档", posts[1].post.isAdultMarked)
    }

    @Test
    fun `榜单：标签拼成空格分隔的一串，画师与出处各归各位`() {
        val post = PixivFeedParse.ranking(rankingBody)[0].post
        assertEquals("名画 3000users入り", post.tags)
        assertEquals(listOf("名画", "3000users入り"), post.tagList)
        assertEquals("某画师", post.author)
        assertEquals("https://www.pixiv.net/users/36025620", post.source)
        assertEquals("https://www.pixiv.net/artworks/150822243", post.pageUrl)
    }

    @Test
    fun `榜单：rating_count 那一格先不喂给任何字段（语义未定性）`() {
        val post = PixivFeedParse.ranking(rankingBody)[0].post
        assertEquals(0, post.score)
        assertEquals(null, post.favCount)
    }

    @Test
    fun `标签那一腿：id 是字符串也读得回来，xRestrict 四种取值各落各档`() {
        val r15 = """{"id":"3","xRestrict":2,"url":"https://i.pximg.net/x/3_p0_square1200.jpg","tags":[],"userId":"4","userName":"d","width":1,"height":1}"""
        val r18 = """{"id":"2","xRestrict":1,"url":"https://i.pximg.net/x/2_p0_square1200.jpg","tags":[],"userId":"5","userName":"e","width":1,"height":1}"""
        val unseen = """{"id":"6","xRestrict":9,"url":"https://i.pximg.net/x/6_p0_square1200.jpg","tags":[],"userId":"7","userName":"f","width":1,"height":1}"""
        val posts = PixivFeedParse.tagWorks(tagBody("$tagRowGeneral,$r18,$r15,$unseen"))
        assertEquals(listOf("general", "r18", "r15", "masked"), posts.map { it.post.rating })
        assertEquals(listOf(false, true, true, true), posts.map { it.post.isAdultMarked })
        assertEquals("150926978", posts[0].post.previewUrl.substringAfterLast('/').substringBefore("_p0"))
    }

    @Test
    fun `标签那一腿：pageCount 是数字也认，多页同样取第 0 页`() {
        val post = PixivFeedParse.tagWorks(tagBody(tagRowGeneral)).single().post
        assertTrue("图址应当是第 0 页：${post.previewUrl}", post.previewUrl.contains("_p0_square1200.jpg"))
        assertEquals(702, post.width)
        assertEquals("Serika", post.author)
        assertEquals("1girl eyes 女の子", post.tags)
    }

    /**
     * ⑦ 卡片上那枚「8P」的出处（用户 2026-10-18：不用点进作品也能看出有几张图）。
     *
     * 立案读数（2026-10-16 现量，同一份夹具）：榜腿带 `illust_page_count` 但是**字符串** `"8"`，
     * 搜索腿带 `pageCount` 是**数字** 5 —— 两腿都给，只是从前解析时丢了这一格。
     */
    @Test
    fun `榜单：illust_page_count 是字符串也落到行模型，那是卡上那枚角标的出处`() {
        val posts = PixivFeedParse.ranking(rankingBody)
        assertEquals(8, posts[0].post.pageCount)
        assertEquals(1, posts[1].post.pageCount)
    }

    @Test
    fun `标签那一腿：pageCount 是数字也落到行模型，缺那一格交 null 不当 1`() {
        assertEquals(5, PixivFeedParse.tagWorks(tagBody(tagRowGeneral)).single().post.pageCount)
        // 站方这一条没给这一格 ⇒ null。读成 1 就是替站方编一句"这一作只有一页"，
        // 而卡上那枚角标的判据正是「>1 才摆」—— 编出来的 1 不会摆，编出来的 8 会。
        val noKey = """{"id":"9","url":"https://i.pximg.net/x/9_p0_square1200.jpg","tags":[]}"""
        assertEquals(null, PixivFeedParse.tagWorks(tagBody(noKey)).single().post.pageCount)
    }

    @Test
    fun `标签那一腿：data 为空是合法空表，不是一句错`() {
        // 现量：一个不存在的词 ⇒ `data` 是 `[]`、`total` 是 0（站方答上了"没有"，不是没答）。
        assertEquals(emptyList<com.venera.compose.gallery.data.PixivWork>(), PixivFeedParse.tagWorks(tagBody("")))
    }

    @Test
    fun `error=true 要抛出来，不能切成一张图都没有`() {
        // 现量：拿一个已经改过的旧用户编号要头像，站方直接回 error=true —— 那一形状在这儿同样不许变哑。
        val body = """{"error":true,"message":"ページが見つかりません","body":{"illustManga":{"data":[]}}}"""
        val e = runCatching { PixivFeedParse.tagWorks(body) }.exceptionOrNull()
        assertTrue("error=true 必须抛而不是交空表：$e", e is IllegalStateException || e is java.io.IOException)
        assertTrue("抛出来的话里要带上站方的 message：${e?.message}", e?.message?.contains("ページが見つかりません") == true)
    }

    @Test
    fun `回来的不是 JSON 对象时炸出来（维护页或被换盾的形状）`() {
        val e = runCatching { PixivFeedParse.ranking("<!DOCTYPE html><html>Just a moment...</html>") }.exceptionOrNull()
        assertTrue("过盾页/维护页不能读成榜单空：$e", e is java.io.IOException)
        assertTrue("消息要能认出是哪一条腿：${e?.message}", e?.message?.contains("pixiv 榜单") == true)
    }

    @Test
    fun `有 N 条却一条都认不出＝站方改了键名，不能读成这一页没有作品`() {
        // 键名被改的表现就是整墙消失；"整墙消失"与"这一页真的没有"必须分得开（降级路径不许静默交错）。
        val renamed = """
            {"mode":"daily","contents":[{"illustId":1,"imageUrl":"https://i.pximg.net/a_p0.jpg"},
                                        {"illustId":2,"imageUrl":"https://i.pximg.net/b_p0.jpg"}]}
        """.trimIndent()
        val e = runCatching { PixivFeedParse.ranking(renamed) }.exceptionOrNull()
        assertTrue("改了键名要抛而不是交空表：$e", e is java.io.IOException)
        assertTrue("消息要说清给过几条：${e?.message}", e?.message?.contains("2 条") == true)
    }

    @Test
    fun `一条记录里没有图址就不算一张图，但别拖坏同页其余那些`() {
        val noImage = """{"id":"8","xRestrict":0,"tags":[],"userId":"9","userName":"g","width":1,"height":1}"""
        val posts = PixivFeedParse.tagWorks(tagBody("$tagRowGeneral,$noImage"))
        assertEquals(1, posts.size)
        assertEquals(150926978L, posts.single().post.id)
    }

    @Test
    fun `榜单那三枚分页标记不是作品数据，读不读都不影响条数`() {
        // `prev:false` 与 `next:2` 是 boolean|number 混形 —— 押成任一种都会当场失败，所以逐字段走。
        val posts = PixivFeedParse.ranking(rankingBody)
        assertEquals(2, posts.size)
    }

    /** 排行榜那一节要摆的两行字（`01 · 作品标题` 与画师名）都在这里，不靠 UI 现编。 */
    @Test
    fun `榜单给站方原题与真序号，按标签那一腿只有题没有序`() {
        val ranked = PixivFeedParse.ranking(rankingBody.replace("\"is_masked\":false", "\"is_masked\":false,\"rank\":1"))
        assertEquals("落ち穂拾い", ranked[0].title)
        assertEquals(1, ranked[0].rank)
        // 夹具里第二枚没写 rank ⇒ 那一格必须是 null，不能是 0（0 会被念成"第 0 名"）。
        assertEquals(null, ranked[1].rank)

        val byTag = PixivFeedParse.tagWorks(tagBody(tagRowGeneral)).single()
        assertEquals("T", byTag.title)
        assertEquals("这一腿站方不给排名，序号必须是 null 而不是按位次数", null, byTag.rank)
    }

    /** 标题是站方原题：与 booru 那侧"从标签算出来的标题"**不同源**，所以不塞进共用的行模型。 */
    @Test
    fun `没有标题格时交空串而不是拿标签当标题`() {
        val noTitle = """{"id":"9","xRestrict":0,"url":"https://i.pximg.net/x/9_p0_square1200.jpg","userId":"1","userName":"h","width":1,"height":1}"""
        val work = PixivFeedParse.tagWorks(tagBody(noTitle)).single()
        assertEquals("", work.title)
        assertEquals("h", work.post.author)
    }
}
