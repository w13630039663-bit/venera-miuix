package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第三十九批 批 39 · 词典译名接上另两格（「正在关注的画师」那一栏 + 搜索那排候选）。
 *
 * ## 为什么是**两颗**件而不是复用批 38 那一颗
 *
 * 对岸那两格自己就不是同一形：
 * - 关注栏 `GalleryHomeSections.kt:317` —— `translations[artist.name] ?: artist.name`，**整枚替换**，
 *   且查的键是原名（不 lower）；
 * - 候选那一排 `GallerySearchArea.kt:1247` 与浮层那排标签 —— `…?.let { "$it (原名)" } ?: 原名`，**括号并列**、键走小写。
 *
 * 复用一颗就会把「风景 (landscape)」那种括号摆进 44dp 的头像栏（名字那一行本来就 `maxLines = 1`，
 * 括一号就截断），或者反过来把标签那格的原名挤掉 —— 用户要复制的、要认的都是站方那串。用例 ② 逐字回核那两行。
 *
 * ## 每一格都只改显示
 *
 * 与批 38 同一条纪律：关注栏那枚点击是"去搜这一位"（交 `artist.name` 原文，`handoff`），
 * 候选那枚是"去搜这一枚标签"（交 `name` 原文）。把显示串递进动线，得到的是一条搜不出东西的查询。
 */
class DesktopLexiconDisplayNameTest {

    /** ① 关注栏那颗件四档：整枚替换 / 没译名 / 空白算没有 / 键按原名那串查（对岸不 lower）。 */
    @Test
    fun 画师那一格是整枚替换的三档() {
        assertEquals("有译名就整枚替换（对岸 `translations[artist.name] ?: artist.name`）", "风景", desktopArtistDisplayName("landscape", mapOf("landscape" to "风景")))
        assertEquals("没译名交回原名", "anmi", desktopArtistDisplayName("anmi", emptyMap()))
        assertEquals("空白译名算没有", "anmi", desktopArtistDisplayName("anmi", mapOf("anmi" to "  ")))
        assertEquals(
            "对岸那行查的是原名那串（不 lower）：大写键才命中，而不是退回小写档",
            "某位",
            desktopArtistDisplayName("Anmi", mapOf("Anmi" to "某位", "anmi" to "小写档")),
        )
    }

    /** ② 两形各自逐字回核对岸 —— 合成一颗的那一刻，屏幕上必有一格形状是错的。 */
    @Test
    fun 两形的底本各回核一处() {
        val artists = DesktopSourceTree.androidSource("gallery/ui/GalleryHomeSections.kt").readText()
        assertTrue("对照组失效：对岸关注栏那行不再是整枚替换", artists.contains("translations[artist.name] ?: artist.name"))
        val suggestions = DesktopSourceTree.androidSource("gallery/ui/GallerySearchArea.kt").readText()
        assertTrue("对照组失效：对岸候选那行不再是括号并列", suggestions.contains("""?.let { "${'$'}it (${'$'}{suggestion.name})" }"""))
        assertFalse(
            "关注栏那颗件被改成了括号形（对岸那格是整枚替换，44dp 的名字行会截断）",
            desktopArtistDisplayName("cat", mapOf("cat" to "猫")).contains("("),
        )
    }

    /** ③ 关注栏接线：那一问只发一次、走效果、显示吃那颗件，而**动线仍交原名**。 */
    /** ④ 候选那一排接线：显示吃批 38 那颗括号形件，而**接进查询的那一步仍交候选原文**。 */
}
