package com.venera.compose.components

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-15 问题清单 G1 批的尺子：**整轮失败要留结构** + **底栏让位的接线只许加长**。
 *
 * 两条都是"抄一份不会报错、漏改一处也不报错"的形态，编译器与运行时都管不到：
 *
 * ① 日榜那面墙的失败分支从前在**首页与二级页各写一遍**同一句 `feedError = "…"`，
 *    而两处都不写那份「站 → 原因」的结构 —— 结构只在成功那一轮的 `accept()` 里落地。
 *    于是缓存铺屏时断网 = 屏上零说法（P0-1）。收口成 `GalleryViewModel.reject` 之后，
 *    "每处只许改一件失败形状"必须钉住，否则下一批又会把文案抄回两个来源。
 * ② `LocalBottomBarClearance` 这条契约已有 22 个文件 29 道消费者，但**漫画详情**这一页
 *    只垫了 8dp（P1-4：平板右栏底栏带 y978~1051 与「预览 (12 页)」行 y1028~1055 坐标重叠）。
 *    接线类尺子按仓内纪律**数道数**，不钉"某处存在"（存在会被同屏另一处合法调用喂饱）。
 */
class UiAuditG1Test {

    private fun codeText(dir: String, name: String): String =
        RepoSources.kotlinFiles(dir)
            .first { it.name == name }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }
            .joinToString("\n")

    private val galleryUi = "com/venera/compose/gallery/ui"

    /** ① 整轮失败的那句文案只有一个来源。 */
    @Test
    fun `整轮失败的收口只有 ViewModel 那一处`() {
        val vm = codeText(galleryUi, "GalleryViewModel.kt")
        assertEquals(
            "`reject` 里也只许写一句失败文案（多处等于多个来源）",
            1,
            Regex("""feedError = "这次没读到内容""").findAll(vm).count(),
        )
        // 对照：两处调用点都不再自己抄那句。
        listOf("GalleryScreen.kt" to "gallery feed failed", "GalleryDailyScreen.kt" to "gallery daily feed failed")
            .forEach { (file, _) ->
                val text = codeText(galleryUi, file)
                assertEquals("$file 又自己写了一遍失败文案", 0, Regex("""feedError = "这次""").findAll(text).count())
                assertEquals("$file 必须走 reject 那颗收口", 1, Regex("""vm\.reject\(""").findAll(text).count())
            }
    }

    /** ② 失败轮那份「站 → 原因」的结构必须落地，两面墙同一口径。 */
    @Test
    fun `失败轮也收下按站原因`() {
        val daily = codeText(galleryUi, "GalleryViewModel.kt")
        assertTrue(
            "reject 不再反解 GalleryEmptyRound 的话，断网时那一排提示拿不到本轮事实",
            daily.contains("(e as? GalleryEmptyRound)?.reasons?.let { failures = it }"),
        )
        // 猜你喜欢那头：成功与失败两支各写一次（只有一支 = 整轮失败时屏上无声）。
        val forYou = codeText(galleryUi, "GalleryForYouViewModel.kt")
        assertEquals(
            "`failures = outcome.reasons` 应当出现在成功与失败两支",
            2,
            Regex("""failures = outcome\.reasons""").findAll(forYou).count(),
        )
    }

    /** ③ 底栏让位的接线：道数只许加长，且漫画详情恒在其内。 */
    @Test
    fun `底栏让位接线只许加长`() {
        val baseline = 29
        val hits = RepoSources.allMainLines().filter { it.third.contains("LocalBottomBarClearance.current") }
        val paths = hits.map { it.first }.distinct()
        assertTrue(
            "消费点从 $baseline 道掉到 ${hits.size} 道：契约的口径是「底栏在位 ⟺ 有让位」，" +
                "把某一处换回手写 magic number 就是在恢复「滑到底那一行读不全」",
            hits.size >= baseline,
        )
        assertTrue(
            "漫画详情这一页又不垫让位了（P1-4：底栏带与「预览 (12 页)」行坐标重叠）",
            paths.any { it.endsWith("feature/ComicDetailScreen.kt") },
        )
        // 对照：那一页的让位是加在 contentPadding 的 bottom 上，而不是新造一个数。
        val detail = codeText("com/venera/compose/feature", "ComicDetailScreen.kt")
        assertEquals(
            1,
            Regex("""bottom = LocalBottomBarClearance\.current \+ tokens\.spacing\.space8""").findAll(detail).count(),
        )
    }
}
