package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GalleryCardTitle
import com.venera.compose.gallery.domain.GalleryTitleTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 锁住卡片标题那一行。
 *
 * 为什么值得单独一个测试类：这一行是屏上**唯一**替用户说"这张画是什么"的文字，
 * 而它的来路（离线词典的档位 + 译名）有四种会**静默出错**的形状：
 * 拼错顺序、重复作品名、拿不该用的档位顶包、以及把"没有"画成有。
 * 本项目没有 Robolectric，判据不放纯函数层就一行都测不到。
 */
class GalleryCardTitleTest {

    private fun tag(label: String, category: Int) = GalleryTitleTag(label, category)

    /** 通用 0 / 画师 1 / 作品 3 / 角色 4 / 元数据 5 —— 与 `GalleryTagCategory` 同一套编号。 */
    private val general = 0
    private val artist = 1
    private val copyright = 3
    private val character = 4
    private val metadata = 5

    @Test
    fun `作品与角色同时命中时按作品 角色 拼`() {
        val labels = mapOf(
            "genshin_impact" to tag("原神", copyright),
            "raiden_shogun" to tag("雷电将军", character),
        )
        assertEquals(
            "原神 · 雷电将军",
            GalleryCardTitle.titleOf(listOf("genshin_impact", "raiden_shogun", "1girl"), labels),
        )
    }

    @Test
    fun `角色译名尾部与作品同名的括号被去掉`() {
        // 词典给的真实值是 `雷电将军（原神）`——不去掉就会读成「原神 · 雷电将军（原神）」。
        val labels = mapOf(
            "genshin_impact" to tag("原神", copyright),
            "raiden_shogun" to tag("雷电将军（原神）", character),
        )
        assertEquals(
            "原神 · 雷电将军",
            GalleryCardTitle.titleOf(listOf("genshin_impact", "raiden_shogun"), labels),
        )
    }

    @Test
    fun `括号里与作品同名时去掉 英文半角括号也认`() {
        // 作品与括号内容相同（忽略大小写）⇒ 去掉。词典两种括号都有，半角那条也要认。
        val labels = mapOf(
            "vocaloid" to tag("VOCALOID", copyright),
            "hatsune_miku" to tag("初音未来(VOCALOID)", character),
        )
        assertEquals(
            "VOCALOID · 初音未来",
            GalleryCardTitle.titleOf(listOf("vocaloid", "hatsune_miku"), labels),
        )
    }

    @Test
    fun `括号里是别的东西时原样保留`() {
        // 括号内容是 VOCALOID，而选中的作品是东方Project ⇒ 不匹配 ⇒ 保留（那是有效信息）。
        val labels = mapOf(
            "touhou" to tag("东方Project", copyright),
            "hatsune_miku" to tag("初音未来（VOCALOID）", character),
        )
        assertEquals(
            "东方Project · 初音未来（VOCALOID）",
            GalleryCardTitle.titleOf(listOf("hatsune_miku", "touhou"), labels),
        )
    }

    @Test
    fun `同档多枚时按标签顺序取第一枚 不按词典顺序`() {
        // 两枚角色档都在表里：结果必须由 tagList 的顺序定（站方的字母序），
        // 否则同一张图会跟着 HashMap 的遍历顺序换标题。
        val labels = mapOf(
            "kirisame_marisa" to tag("雾雨魔理沙", character),
            "hakurei_reimu" to tag("博丽灵梦", character),
        )
        assertEquals(
            "雾雨魔理沙",
            GalleryCardTitle.titleOf(listOf("kirisame_marisa", "hakurei_reimu"), labels),
        )
        assertEquals(
            "博丽灵梦",
            GalleryCardTitle.titleOf(listOf("hakurei_reimu", "kirisame_marisa"), labels),
        )
    }

    @Test
    fun `只有角色时直接摆角色`() {
        val labels = mapOf("raiden_shogun" to tag("雷电将军（原神）", character))
        // 没有作品可参照 ⇒ 括号原样留着（去掉它就丢了作品信息）。
        assertEquals(
            "雷电将军（原神）",
            GalleryCardTitle.titleOf(listOf("1girl", "raiden_shogun"), labels),
        )
    }

    @Test
    fun `只有作品时直接摆作品`() {
        val labels = mapOf("blue_archive" to tag("蔚蓝档案", copyright))
        assertEquals("蔚蓝档案", GalleryCardTitle.titleOf(listOf("blue_archive", "solo"), labels))
    }

    /**
     * 回退链的后两档（批次 M · M6，用户 2026-09-30：「部分图片没有标题就不要完全空着，
     * 显示角色和其他 tag 了」）。
     *
     * 这两条**推翻**了原判据 4（"一个都没有 → null，不拿通用标签或画师名顶包"）。
     * 原判据的理由是"通用标签会摆出「掀起上衣」这种不成句的东西"——那条理由现在被
     * "整行空着更糟"盖过去了：屏上一整片没有标题的卡，读起来像坏了，而不像"这张没标题"。
     * 但**次序不许换**：作品/角色永远优先，画师名次之，通用标签垫底。
     */
    @Test
    fun `没有作品与角色时回退到画师名`() {
        val labels = mapOf(
            "setmen" to tag("setmen", artist),
            "landscape" to tag("风景", general),
        )
        // 画师档常常没有中文译名（库里 cn 是 NULL），此时**摆原词**是对的：
        // 那是专有名词，音译一个反而没人认得出。
        assertEquals("setmen", GalleryCardTitle.titleOf(listOf("1girl", "landscape", "setmen"), labels))
    }

    @Test
    fun `画师名优先于通用标签`() {
        val labels = mapOf(
            "shirt_lift" to tag("掀起上衣", general),
            "tokenbox" to tag("tokenbox", artist),
        )
        assertEquals(
            "tokenbox",
            GalleryCardTitle.titleOf(listOf("shirt_lift", "solo", "tokenbox"), labels),
        )
    }

    @Test
    fun `连画师都没有时摆通用标签的译名`() {
        val labels = mapOf("shirt_lift" to tag("掀起上衣", general))
        assertEquals("掀起上衣", GalleryCardTitle.titleOf(listOf("1girl", "shirt_lift"), labels))
    }

    @Test
    fun `同档多枚时回退也按标签顺序取第一枚`() {
        val labels = mapOf(
            "shirt_lift" to tag("掀起上衣", general),
            "hair_ribbon" to tag("发带", general),
        )
        assertEquals("发带", GalleryCardTitle.titleOf(listOf("hair_ribbon", "shirt_lift"), labels))
        assertEquals("掀起上衣", GalleryCardTitle.titleOf(listOf("shirt_lift", "hair_ribbon"), labels))
    }

    @Test
    fun `元数据档不许顶包`() {
        // 5 = metadata（`highres`、`translated` 那一类）：那是"这张图的技术属性"，
        // 摆到标题行上就成了"这张画叫高分辨率"。这一档连回退都不进。
        val labels = mapOf(
            "highres" to tag("高分辨率", metadata),
            "translated" to tag("已翻译", metadata),
        )
        assertNull(GalleryCardTitle.titleOf(listOf("highres", "translated", "1girl"), labels))
    }

    @Test
    fun `四档都没有时回 null 整行不画`() {
        assertNull(GalleryCardTitle.titleOf(listOf("1girl", "solo"), emptyMap()))
    }

    @Test
    fun `标签与词典都为空时回 null`() {
        assertNull(GalleryCardTitle.titleOf(emptyList(), emptyMap()))
        assertNull(GalleryCardTitle.titleOf(listOf("1girl"), emptyMap()))
    }

    @Test
    fun `大小写与顺序都不影响命中`() {
        val labels = mapOf("genshin_impact" to tag("原神", copyright))
        // 站方的 tags 是字母序小写，但词典查询做的是 lowercase 归一，这里锁住那一层契约。
        assertEquals("原神", GalleryCardTitle.titleOf(listOf("Genshin_Impact"), labels))
    }
}
