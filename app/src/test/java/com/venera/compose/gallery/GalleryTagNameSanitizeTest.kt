package com.venera.compose.gallery

import com.venera.compose.gallery.domain.sanitizeGalleryTagName
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 交接标签名清洗的判据（2026-10-23，用户「去掉多余前缀与非法符号」）。
 *
 * 为什么值得单测而不是只靠源码尺：这颗函数是纯函数，而"洗什么、不洗什么"两边都有代价 ——
 * 洗少了（前导 `-` 变排除项、引号捅穿 URL），洗多了（pixiv 合法的空格与斜杠被抹，
 * "Fate/Grand Order" 这种词直接搜不到）。每一行用例钉一边，改动必须同时看两边。
 */
class GalleryTagNameSanitizeTest {

    @Test
    fun `正常标签原样通过`() {
        assertEquals("touhou", sanitizeGalleryTagName("touhou"))
        assertEquals("wowoguni", sanitizeGalleryTagName("  wowoguni  "))
        assertEquals("女の子", sanitizeGalleryTagName("女の子"))
        // 半角下划线是 booru 的空格写法，一个字符都不动。
        assertEquals("kaga_(kancolle)", sanitizeGalleryTagName("kaga_(kancolle)"))
    }

    @Test
    fun `pixiv 合法的内部空格与斜杠保留`() {
        // 这条是"不洗什么"那一侧的判据：pixiv 标签名里空格和斜杠都合法，
        // 洗了就是把能搜到的词改成搜不到。
        assertEquals("Fate/Grand Order", sanitizeGalleryTagName("Fate/Grand Order"))
        assertEquals("VOCALOID", sanitizeGalleryTagName("VOCALOID"))
    }

    @Test
    fun `前导符号成串去掉`() {
        // `-` 会被 GalleryTagFilter.token 拼成排除项；`@` 是画师 handle；`#` 是话题写法。
        assertEquals("wowoguni", sanitizeGalleryTagName("-wowoguni"))
        assertEquals("wowoguni", sanitizeGalleryTagName("@wowoguni"))
        assertEquals("wowoguni", sanitizeGalleryTagName("#wowoguni"))
        assertEquals("wowoguni", sanitizeGalleryTagName("--@@##  wowoguni"))
        // 前导空白与符号混着来也一次收干净。
        assertEquals("foo", sanitizeGalleryTagName("  - @ foo"))
    }

    @Test
    fun `非法符号换空白并压缩`() {
        // 引号 / 尖括号 / 反斜杠 / 竖线是查询语法与文件名里的非法符号。
        assertEquals("foo bar", sanitizeGalleryTagName("foo\"bar"))
        assertEquals("foo bar", sanitizeGalleryTagName("foo<bar"))
        assertEquals("foo bar", sanitizeGalleryTagName("foo|bar"))
        assertEquals("foo bar", sanitizeGalleryTagName("foo\\bar"))
        // 被符号撑出来的连续空白压成一个，不长出双空格。
        assertEquals("a b", sanitizeGalleryTagName("a\"  \"b"))
    }

    @Test
    fun `不可见字符按空白处理`() {
        // NBSP / 零宽空格 / BOM 屏上不显示，进 URL 必炸。
        assertEquals("foo bar", sanitizeGalleryTagName("foo\u00A0bar"))
        assertEquals("foo bar", sanitizeGalleryTagName("foo\u200Bbar"))
        assertEquals("foo bar", sanitizeGalleryTagName("foo\uFEFFbar"))
        // 控制字符（含 \u0000 一族脏数据）同样换空白。
        assertEquals("foo bar", sanitizeGalleryTagName("foo\u0000bar"))
        assertEquals("foo bar", sanitizeGalleryTagName("foo\tbar"))
        // 全角空格也是空白：压缩之后不留双空格。
        assertEquals("a b", sanitizeGalleryTagName("a\u3000\u3000b"))
    }

    @Test
    fun `洗成空串的返回空`() {
        // 整枚都是符号 = 没有可搜的词，调用方据此不写交接槽。
        assertEquals("", sanitizeGalleryTagName(""))
        assertEquals("", sanitizeGalleryTagName("   "))
        assertEquals("", sanitizeGalleryTagName("@"))
        assertEquals("", sanitizeGalleryTagName("---"))
        assertEquals("", sanitizeGalleryTagName("\"<>|"))
    }
}
