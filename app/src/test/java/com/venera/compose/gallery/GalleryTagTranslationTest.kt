package com.venera.compose.gallery

import com.venera.compose.gallery.ui.tagDisplayLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 画廊标签的**显示层**汉化。
 *
 * 这一层唯一的硬不变量：**译文只改显示**。点药丸发出去的、复制的、屏蔽落库的都仍是原串
 * （调用方一直把 `tag` 而不是这个返回值交给动作回调）—— 译文会随词典更新而变，
 * 而屏蔽词表与检索词必须跟着站方的原词走。这条与漫画侧
 * `com.venera.compose.data.tags.TagDisplay` 是同一份口径。
 */
class GalleryTagTranslationTest {

    @Test
    fun `有译名时双显，原文常驻括号里`() {
        assertEquals(
            "过膝袜 (thighhighs)",
            tagDisplayLabel("thighhighs", mapOf("thighhighs" to "过膝袜")),
        )
    }

    @Test
    fun `查不到译名就原样显示，不编一个`() {
        // 词典对 yande.re 只有约 73% 的名称覆盖（Danbooru 已删/改名而本站仍活着的词它没有），
        // 覆盖不到的那一屏就是原文 —— 隐藏原文会让人没法判断这枚药丸对应站方哪个词。
        assertEquals("see_through", tagDisplayLabel("see_through", emptyMap()))
    }

    @Test
    fun `词典把人名照抄罗马字时不再重复一遍`() {
        // ffdkj 里 46,804 条是 cn_name == name（画师名本来没有公认中文名）。
        // 不排除这一支，画师那一行会长成 "huke (huke)"。
        assertEquals("huke", tagDisplayLabel("huke", mapOf("huke" to "huke")))
    }

    @Test
    fun `查词按小写比对，但括号里仍原样保留站方给的大小写`() {
        // 括号里那一串是用户会拿去搜、也会拿去落屏蔽表的**原串**，
        // 把它顺手改成小写就等于改了站方的词形 —— 搜不到时没人知道是我们动过。
        assertEquals("单人女性 (1Girl)", tagDisplayLabel("1Girl", mapOf("1girl" to "单人女性")))
    }

    @Test
    fun `空串译文算没有译文`() {
        assertEquals("1girl", tagDisplayLabel("1girl", mapOf("1girl" to " ")))
    }
}
