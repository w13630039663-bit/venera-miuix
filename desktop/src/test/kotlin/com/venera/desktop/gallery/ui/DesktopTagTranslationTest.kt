package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第三十八批 批 38 · 浮层那一排标签接上**离线词典的中文译名**（只改显示）。
 *
 * ## 这一批先把一条挂着的前提量掉
 *
 * 排期上挂了几轮的"结构性豁免（词典那三枚生产者结构性无桌面调用点）"**前提是反的**：
 * `GalleryTagLexicon` 四枚成员里桌面只吃了一枚（`fetchTagCategories`，在浮层的分类那一问），
 * 而另三枚在**桌面已有的同一屏上**有对应用途 —— 对岸 `translations` 吃在三处（首页关注栏 `GalleryHomeSections.kt:502`、
 * 搜索补全 `GallerySearchArea.kt:1209`、大图页标签 `GalleryPostScreen.kt:363`），后两处的桌面对应物
 * （标签补全那一排、浮层那一排标签）今天都在屏上。所以那是**缺口**而不是豁免（手册 §0 那一族：
 * 文案说"还没有 X"而 X 已落地，与反方向说"这是结构性豁免"而它其实接得上，是同一枚硬币的两面）。
 * 本批接的是**浮层那一排标签**那一格；其余两格（关注栏、补全那一排）另批做，不顺手混进来。
 *
 * ## 口径只有一条：只改显示
 *
 * 对岸那句写着得逐字照办（`GalleryInfoSheet.kt:184`：「**只改显示**：菜单里那三项与搜索交的都是原名」）——
 * 屏蔽规则与搜索那两路交的是站方那串原文，把译名递进去就是"往规则库里写一条永远命中不了的规则"。
 * 所以 ③ 那头的判据钉的是**动作那一串不许换成 label**，而不是钉标签画了什么。
 */
class DesktopTagTranslationTest {

    /** ① 那颗纯函数三档各一：有译名 / 没译名 / 译名是空串或等于原名。 */
    @Test
    fun 标签读数三档各说一句() {
        assertEquals(
            "有译名时念「译名 (原名)」——对岸那格就是这个形状",
            "风景 (landscape)",
            desktopTagDisplayLabel("landscape", mapOf("landscape" to "风景")),
        )
        assertEquals("没译名时原样交回", "1girl", desktopTagDisplayLabel("1girl", mapOf("landscape" to "风景")))
        assertEquals("译名是空串等于没有", "cat", desktopTagDisplayLabel("cat", mapOf("cat" to "   ")))
        assertEquals(
            "译名与原名同一串时不重复摆（对岸那条 `it != tag`）",
            "monochrome",
            desktopTagDisplayLabel("monochrome", mapOf("monochrome" to "monochrome")),
        )
    }

    /** ② 键的大小写归一逐字照对岸（站方标签常带大写，而词典按小写存）。 */
    @Test
    fun 查词典用的是原名的小写形() {
        assertEquals(
            "对岸查的是 `tag.lowercase()` 那一档：大写的原名也要能命中",
            "角色 (Charactor)",
            desktopTagDisplayLabel("Charactor", mapOf("charactor" to "角色")),
        )
    }

    /** ③ 接线：那一排标签真吃那颗件，而**动作交的还是原名**（只改显示那条口径的牙）。 */
    /** ④ 那一问**只在换那张卡时问一次**，且落在组合之外（摆放期写状态 = 每帧重组那一族）。 */
    /**
     * ⑤ 页脚那句必须说全**四件**事（不归类 / 那一形是谁给的 / 点是搜索 / 按住是屏蔽）。
     *
     * 这一条是本批接上译名之后**当场补**的：那句从前写着"按该站自己的返回原样展示"，
     * 而接上词典之后它半句都不剩 —— 屏上一排「风景 (landscape)」而页脚说"原样"，就是批 24/25 那一族
     * （判据全绿而屏上说话是假的）。所以这一条钉的不是措辞美感，而是**那句话还成不成立**。
     * ⚠️ 这条判据在 40 批里**翻过两次**：批 39 撤那半句时它是 `assertFalse`（当时词典件恒空，
     * 承诺就成了做不到的那一句）；批 40 把词典带上桌面、量到 79,415 行里 71,423 行带中文之后回到 `assertTrue`。
     * 翻的理由必须是**读数**而不是审美，这一条才有价值（手册 §0 坑 ㉖：措辞锚点会把一句话冻成规格）。
     */
}
