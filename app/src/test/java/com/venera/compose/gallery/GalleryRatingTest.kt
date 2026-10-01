package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GalleryRatingTone
import com.venera.compose.gallery.domain.ratingLabel
import com.venera.compose.gallery.domain.ratingToneOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 分级呈现的判据（2026-10-01，信息卡重排那一批）。
 *
 * 两条要钉住的东西，都不是"风格"：
 *
 * 1. **两站的字形都要认**。yande.re 是单字母（`s`/`q`/`e`），Gelbooru 是单词
 *    （`general`/`sensitive`/`questionable`/`explicit`）。只认一套的后果是另一站
 *    每一条都落到"未知" —— 那不是数据缺失，是我们没认出来。
 * 2. **认不出就是认不出**。空值、脏值一律 [GalleryRatingTone.Unknown]，
 *    **不许**因为"看起来像成人站"就归到 [GalleryRatingTone.Caution]。
 *    这条与全仓"没有的字段一律不编"是同一根红线，所以它必须有一条用例挡着。
 */
class GalleryRatingTest {

    @Test
    fun `yande 的单字母三档各归一次`() {
        assertEquals(GalleryRatingTone.Safe, ratingToneOf("s"))
        assertEquals(GalleryRatingTone.Caution, ratingToneOf("q"))
        assertEquals(GalleryRatingTone.Explicit, ratingToneOf("e"))
    }

    @Test
    fun `gelbooru 的四个单词全部认得`() {
        // general 与 safe 同档、sensitive 与 questionable 同色档，但**都认得**。
        assertEquals(GalleryRatingTone.Safe, ratingToneOf("general"))
        assertEquals(GalleryRatingTone.Caution, ratingToneOf("sensitive"))
        assertEquals(GalleryRatingTone.Caution, ratingToneOf("questionable"))
        assertEquals(GalleryRatingTone.Explicit, ratingToneOf("explicit"))
    }

    @Test
    fun `大小写与前后空白不影响判定`() {
        // 站方两站的输出是稳定的，但这一层不该假设它稳定。
        assertEquals(GalleryRatingTone.Safe, ratingToneOf("Safe"))
        assertEquals(GalleryRatingTone.Explicit, ratingToneOf("  EXPLICIT  "))
        assertEquals(GalleryRatingTone.Caution, ratingToneOf("Questionable"))
    }

    @Test
    fun `认不出的值一律未知不许替站方下判断`() {
        // 这四条里没有一条是 Caution —— 把"没认出来"画成"站方说是敏感"就是编数据。
        assertEquals(GalleryRatingTone.Unknown, ratingToneOf(""))
        assertEquals(GalleryRatingTone.Unknown, ratingToneOf("   "))
        assertEquals(GalleryRatingTone.Unknown, ratingToneOf("r18"))
        assertEquals(GalleryRatingTone.Unknown, ratingToneOf("unknown"))
    }

    @Test
    fun `标签保留英文原文两站的字形都要能对账`() {
        // 用户拿它回站方页面对账，只留中文会对不上。
        assertEquals("安全 (s)", ratingLabel("s"))
        assertEquals("一般 (general)", ratingLabel("general"))
        assertEquals("敏感 (sensitive)", ratingLabel("sensitive"))
        assertEquals("存疑 (questionable)", ratingLabel("q"))
        assertEquals("成人 (explicit)", ratingLabel("e"))
    }

    @Test
    fun `空值显示成空而不是显示成未知两个字后面的空白`() {
        // 「未知 (空)」才是完整读数；「未知 ()」读起来像模板漏填，纯空白同理。
        assertEquals("未知 (空)", ratingLabel(""))
        assertEquals("未知 (空)", ratingLabel("   "))
        assertEquals("未知 (r18)", ratingLabel("r18"))
    }
}
