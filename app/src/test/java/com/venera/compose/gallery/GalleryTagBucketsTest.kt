package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GalleryTagCategory
import com.venera.compose.gallery.domain.buildGalleryTagBuckets
import com.venera.compose.gallery.domain.parseGalleryTagCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画廊标签分桶。
 *
 * 两个入口各自都被真片段钉住：`parseGalleryTagCategories` 吃的是**站方 HTML 原文抄录**
 * （2026-09-28 实测，见 `gallery-tag-category-translation-and-nav-2026-09.md` §0.4/§0.7），
 * `buildGalleryTagBuckets` 锁的是"分不出来时宁可少摆不可摆错"这条降级口径。
 */
class GalleryTagBucketsTest {

    /** yande.re `/post/view/1269641` 的标签侧原文（两枚真标签，档位分别是画师与通用）。 */
    private val yandereHtml = """
        <ul id="tag-sidebar">
        <li class="tag-type-artist"><a href="/artist/show?name=gijang">?</a> <a href="/post?tags=gijang">gijang</a> <span class="post-count">136</span> </li>
        <li class="tag-type-general"><a href="/wiki/show?title=no_bra">?</a> <a href="/post?tags=no_bra">no bra</a> <span class="post-count">208185</span> </li>
        <li class="tag-type-general"><a href="/wiki/show?title=cameltoe">?</a> <a href="/post?tags=cameltoe">cameltoe</a> <span class="post-count">58316</span> </li>
        </ul>
    """.trimIndent()

    /** Gelbooru `s=view&id=14970719` 的形状：中间多一层 span、地址是 `&amp;` 串、名字带 `!` 且被百分号编码。 */
    private val gelbooruHtml = """
        <ul class="tag-list" id="tag-list">
        <li class="tag-type-character"><span class="sm-hidden"><a href="index.php?page=wiki&amp;s=list&amp;search=imai_lisa">?</a> </span><a href="index.php?page=post&amp;s=list&amp;tags=imai_lisa">imai lisa</a> <span style='color: #a0a0a0;'>1492</span></li>
        <li class="tag-type-copyright"><span class="sm-hidden"><a href="index.php?page=wiki&amp;s=list&amp;search=bang_dream%21">?</a> </span><a href="index.php?page=post&amp;s=list&amp;tags=bang_dream%21">bang dream!</a> <span style='color: #a0a0a0;'>55666</span></li>
        <li class="tag-type-general"><span class="sm-hidden"><a href="index.php?page=wiki&amp;s=list&amp;search=2girls">?</a> </span><a href="index.php?page=post&amp;s=list&amp;tags=2girls">2girls</a></li>
        <li class="tag-type-metadata"><a href="index.php?page=post&amp;s=list&amp;tags=tagme">tag me</a></li>
        </ul>
    """.trimIndent()

    @Test
    fun `yande re 片段读出档位，键取下划线原形而不是锚文本`() {
        val parsed = parseGalleryTagCategories(yandereHtml)
        assertEquals(mapOf("gijang" to 1, "no_bra" to 0, "cameltoe" to 0), parsed)
        // 锚文本 "no bra" 如果被当成键，就永远对不上 API 那串 tags（"no_bra"）—— 这一枚会静默不分桶。
        assertNull(parsed["no bra"])
    }

    @Test
    fun `gelbooru 片段先还原实体再取 tags 参数`() {
        val parsed = parseGalleryTagCategories(gelbooruHtml)
        assertEquals(
            mapOf("imai_lisa" to 4, "bang_dream!" to 3, "2girls" to 0, "tagme" to 5),
            parsed,
        )
    }

    @Test
    fun `某条 li 自己没有检索链接时不许借用下一条的名字`() {
        // 站方对未知/新建标签渲染的形状可能少一条链接。跨条匹配会把下一枚的名字算到这个档位上，
        // 那是"分错桶" —— 比"这一枚没分桶"坏得多，而且屏上完全看不出来。
        val html = """
            <li class="tag-type-character"><a href="/wiki/show?title=orphan">?</a> 没有检索链接</li>
            <li class="tag-type-artist"><a href="/post?tags=gijang">gijang</a></li>
        """.trimIndent()
        val parsed = parseGalleryTagCategories(html)
        assertEquals(mapOf("gijang" to 1), parsed)
        assertTrue(parsed.values.none { it == 4 })
    }

    @Test
    fun `空串与没有标签列表的页面`() {
        assertTrue(parseGalleryTagCategories("").isEmpty())
        assertTrue(parseGalleryTagCategories("<html><body>已删除</body></html>").isEmpty())
    }

    @Test
    fun `认不出的类名不映射成任何档`() {
        // 站方还有 tag-type-specifier 这类形状；把它当"通用"就是冒充一个我们没读过的判定。
        val html = """<li class="tag-type-specifier"><a href="/post?tags=1girl">1girl</a></li>"""
        assertTrue(parseGalleryTagCategories(html).isEmpty())
    }

    @Test
    fun `加号不当作空格解码`() {
        // java.net.URLDecoder 会把 + 解成空格，而 c+union 这种标签真存在：
        // 解错的表现是"这一枚没分桶"，最难被发现。
        val html = """<li class="tag-type-general"><a href="index.php?page=post&s=list&tags=c+union">c+union</a></li>"""
        val parsed = parseGalleryTagCategories(html)
        assertEquals(mapOf("c+union" to 0), parsed)
    }

    @Test
    fun `站方判定齐全时按档位分桶，未判定的落标签桶而不是通用`() {
        val tags = listOf("gijang", "imai_lisa", "bang_dream!", "no_bra", "never_classified")
        val categories = mapOf(
            "gijang" to GalleryTagCategory.ARTIST,
            "imai_lisa" to GalleryTagCategory.CHARACTER,
            "bang_dream!" to GalleryTagCategory.COPYRIGHT,
            "no_bra" to GalleryTagCategory.GENERAL,
        )
        val groups = buildGalleryTagBuckets(tags, categories)
        assertEquals(listOf("画师", "角色", "作品", "通用", "标签"), groups.map { it.label })
        assertEquals(listOf("never_classified"), groups.last().tags)
        assertEquals(1, groups.sumOf { if (it.label == "通用") it.tags.size else 0 })
    }

    @Test
    fun `桶序固定画师在前标签在最后，空桶不产出`() {
        val groups = buildGalleryTagBuckets(
            listOf("no_bra", "gijang"),
            mapOf("gijang" to 1, "no_bra" to 0),
        )
        assertEquals(listOf("画师", "通用"), groups.map { it.label })
        // 元数据/角色/作品 全空 → 一个都不摆。
        assertEquals(2, groups.size)
    }

    @Test
    fun `站方判定取不到时只兜画师一栏`() {
        val tags = listOf("gijang", "no_bra", "seifuku")
        val groups = buildGalleryTagBuckets(tags, categories = null, fallbackArtistNames = setOf("gijang"))
        assertEquals(listOf("画师", "标签"), groups.map { it.label })
        assertEquals(listOf("no_bra", "seifuku"), groups[1].tags)
        // 兜底不许摆出"通用/角色/作品"任何一桶名：词典对 yande.re 只有 73% 名称覆盖。
        assertTrue(groups.none { it.label in setOf("通用", "角色", "作品", "元数据") })
    }

    @Test
    fun `空标签串不产出任何桶`() {
        assertTrue(buildGalleryTagBuckets(emptyList(), mapOf("gijang" to 1)).isEmpty())
        assertTrue(buildGalleryTagBuckets(emptyList(), null).isEmpty())
    }

    @Test
    fun `站方给了空表也算取不到`() {
        // emptyMap() 与 null 同义：没有一条判定就不要假装分过桶。
        val groups = buildGalleryTagBuckets(listOf("gijang"), emptyMap(), setOf("gijang"))
        assertEquals(listOf("画师"), groups.map { it.label })
    }

    @Test
    fun `大小写与档位表同源`() {
        val groups = buildGalleryTagBuckets(listOf("A"), mapOf("a" to 5))
        assertEquals(listOf("元数据"), groups.map { it.label })
    }
}
