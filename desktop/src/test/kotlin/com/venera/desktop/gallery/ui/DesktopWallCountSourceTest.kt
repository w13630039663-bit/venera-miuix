package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十五批 批 13 · 段头那两句**必须同一个主语**。
 *
 * ## 症状
 *
 * 「进屏 N 张 · yande.re x 张」这两截从前吃的是两份数：
 * - 日榜：`进屏` 数**过了分级那道关的墙**（`wall.cards.size`），而「各站」数**过关之前**的 merge
 *   （`merged.perSite`）⇒ 有内容被挡时两句加不起来（屏上会写"进屏 8 张 · yande.re 12 张、Gelbooru 6 张"）；
 * - 搜索：两截都数过关**之前**（`batch.posts`）⇒ 屏上那句"进屏 171 张"数的不是屏上那一面墙；
 * - 收藏：「摆上屏 M 张」数的是 `projection.posts`（过关之前）——**那句话本身就不成立**。
 *
 * ## 为什么"遮挡档"算摆出来
 *
 * `BLURRED` 那一档在屏上占一格（带「分级遮挡」角标），所以它计入"上屏"；
 * 而 `HIDDEN`（命中屏蔽规则或成人档=隐藏）不落屏，它由下面那行「N 张按…收起」单独说。
 * 两句话各有各的数，谁也不冒领谁 —— 这一批只是把"上屏"那一句改成真的数上屏的那些。
 *
 * ## 没被顺手统一的那一处
 *
 * 最新那一屏数的是**取回的条目**（「yande.re 12 条」），它整屏没有"进屏 N 张"那一句，
 * 而它下面另有一行「滤掉 N 张不能上屏的」—— 那是两个主语，本来就该是两个数（⑦ 钉住不许跟着这一批改）。
 */
class DesktopWallCountSourceTest {

    private fun post(id: Long, site: GallerySite = GallerySite.YANDERE) = GalleryPost(
        site = site,
        id = id,
        rating = "s",
        tags = "",
        author = "",
    )

    private fun card(id: Long, site: GallerySite = GallerySite.YANDERE, masked: Boolean = false) =
        DesktopWallCard(post(id, site), masked)

    /** ① 墙上真摆出来的那些 = 数这一句的唯一来源（遮挡档占一格 ⇒ 算；被挡的不在卡里 ⇒ 不算）。 */
    @Test
    fun 数的是墙上真摆出来的那些张() {
        val wall = DesktopWall(
            cards = listOf(card(1), card(2, GallerySite.GELBOORU), card(3, masked = true)),
            blockedCount = 5,
            blockedRules = listOf("AI 生成"),
            hiddenByRating = 5,
        )
        assertEquals(3, desktopWallPosts(wall).size)
    }

    /** ② 空墙 ⇒ 空表。于是「各站」那串是空串，由拼串那颗件丢掉（屏上只剩「进屏 0 张」）。 */
    @Test
    fun 一面空墙数得出零张而不是空壳() {
        assertEquals(emptyList<GalleryPost>(), desktopWallPosts(DesktopWall()))
        assertEquals(
            " · 进屏 0 张",
            desktopWallReadingLine(
                "进屏 0 张",
                desktopPerSiteReading(emptyMap<GallerySite, Int>(), unit = "张"),
            ),
        )
    }

    /** ③ 各站那串按墙分组：被挡的那 5 张不许混进来。 */
    @Test
    fun 各站那串里不含有关被挡掉的那些() {
        val wall = DesktopWall(
            cards = listOf(card(1), card(2), card(3, GallerySite.GELBOORU)),
            blockedCount = 5,
        )
        val posts = desktopWallPosts(wall)
        assertEquals(
            "yande.re 2 张、Gelbooru 1 张",
            desktopPerSiteReading(posts.groupingBy { it.site }.eachCount(), unit = "张"),
        )
    }

    /** ④ 日榜那一句不再吃过关之前的 `merged.perSite`（它只许数墙）。 */
    /** ⑤ 搜索那两句同理：`batch.posts` 是取回的，不是上屏的。 */
    /** ⑥ 收藏那句「摆上屏 M 张」现在才真的数上屏的（而「本机存档 N 条」仍数存档 —— 两句话两个主语）。 */
    /** ⑦ 最新那一屏不许跟着这一批改：它数的是**取回的条目**（条），而它整屏没有"进屏 N 张"那一句。 */
    // ⚠️ 旧 ⑧「被挡那些仍有另一句话说」**已被 `DesktopWallGuardCoverageTest` ①②③ 取代**（批 37）：
    // 它把证据写成 `contains("guardFragments")`，而单人页那一串**只住在 cards 为空那一格里** ——
    // 非空时屏上不说少了谁，这条照样绿（§0 坑 ㉔：文本锚点看不见分支条件）。它列的四枚文件名也是名单而非尺子。

    private fun count(text: String, token: String): Int = text.split(token).size - 1
}
