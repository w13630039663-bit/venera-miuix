package com.venera.compose.feature

import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryLegGuard
import com.venera.compose.gallery.domain.GalleryNoticeKind
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.gallery.domain.galleryNotices
import com.venera.compose.gallery.domain.noRecommendTagsReason
import com.venera.compose.gallery.ui.noticeTitle
import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-14 UI 审查第二批的尺子：画廊内容区的提示**只许一行**，
 * 而那一行背后一个字都不许被删掉。
 *
 * 立案依据是手机档截图（`_qa/ui-audit-2026-10-14/`）：首页那面墙上两块提示各占
 * 3~4 行正文，把「正在关注的画师 / 每日热门」整块推出首屏。
 *
 * 两类尺子分工：①②③④ 扫源码（"新写一处不受控的调用点"、"把手挂在没有正文的块上"
 * 这类形态编译器和运行时都看不见）；⑤⑥ 直接测 `noticeTitle` 的行为（超长会静默
 * 变成省略号，扫源码看不出两站并起时那一行实际有多长）。
 */
class UiAuditBatch2Test {

    private fun codeLines(packagePath: String, fileName: String): List<String> =
        RepoSources.kotlinFiles(packagePath)
            .first { it.name == fileName }
            .readLines()
            .map { it.trim() }
            .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    private val notice = codeLines("com/venera/compose/components/venera", "VeneraNotice.kt")
    private val noticeHost = codeLines("com/venera/compose/gallery/ui", "GalleryNotices.kt")

    /** 实参表跨行，判式必须吃整颗调用而不是只有锚点那一行。 */
    private fun callArgs(lines: List<String>, anchor: String): List<String> {
        val start = lines.indexOfFirst { it.contains(anchor) }
        assertTrue("找不到调用点：$anchor", start >= 0)
        val args = mutableListOf<String>()
        for (i in start until minOf(start + 12, lines.size)) {
            if (i > start && lines[i].startsWith(")")) break
            args += lines[i]
        }
        return args
    }

    /** ① 三面墙（首页 / 按收藏推荐 / 搜索）的调用点全部递进 `compact = true`。 */
    @Test
    fun `画廊三面墙的提示都走一行档`() {
        val walls = listOf(
            "com/venera/compose/gallery/ui" to "GalleryScreen.kt",
            "com/venera/compose/gallery/ui" to "GalleryForYouScreen.kt",
            "com/venera/compose/gallery/ui" to "GallerySearchArea.kt",
        )
        walls.forEach { (pkg, name) ->
            val lines = codeLines(pkg, name)
            val starts = lines.indices.filter { lines[it].startsWith("GalleryNotices(") }
            assertTrue("$name 上没有提示调用点（改动挪走了就把这条一起改掉）", starts.isNotEmpty())
            starts.forEach { start ->
                val block = callArgs(lines, lines[start])
                assertTrue(
                    "$name 的提示没走一行档：${block.joinToString(" | ")}",
                    block.any { it.contains("compact = true") },
                )
            }
        }
    }

    /** ② 宿主那层的 `compact` 是必填形参：第四个调用点必须当场决定一次。 */
    @Test
    fun `一行档是必选项而不是默认值`() {
        val signature = callArgs(noticeHost, "internal fun GalleryNotices(")
        assertTrue(
            "compact 又带回默认值了 —— 漏传不报错，新调用点会静默长成多行块",
            signature.any { it == "compact: Boolean," },
        )
        assertTrue(
            "宿主没把 compact 递给组件",
            noticeHost.any { it == "compact = compact," },
        )
    }

    /** ③ 正文只收起不删：那一档的渲染条件必须是"非一行档或已展开"。 */
    @Test
    fun `一行档收起的是正文而不是删掉正文`() {
        val gate = notice.first { it.startsWith("val showBody") }
        assertEquals(
            "正文的显隐条件被改了 —— 这一条红了先问『原因还在屏上吗』",
            "val showBody = hasTitle && message.isNotBlank() && (!compact || expanded)",
            gate,
        )
        assertTrue(
            "正文那一格又回到无条件的 `if (hasTitle)`：多行文档式提示会直接铺回内容区",
            notice.none { it == "if (hasTitle) {" },
        )
        assertTrue("正文仍然要真地画出来", notice.any { it == "text = message," })
    }

    /** ④ 展开把手只在真有隐藏正文时出现，且那一行的可点性与它是同一个条件。 */
    @Test
    fun `没有正文的块上不摆展开把手`() {
        val collapsible = notice.first { it.startsWith("val collapsible") }
        assertTrue(
            "把手的条件松了：$collapsible",
            collapsible.contains("compact && hasTitle && message.isNotBlank()"),
        )
        assertTrue("把手只在可展开时画", notice.any { it == "if (collapsible) {" })
        assertTrue(
            "整行的可点性与把手不同轨：按得动却没东西可展，或看得见箭头却按不动",
            notice.any { it.startsWith("modifier = if (collapsible)") },
        )
        assertTrue(
            "读屏念不到这一行能按（onClickLabel 丢了，展开这条路就不可发现）",
            notice.any { it.contains("clickable(onClickLabel =") },
        )
    }

    /** ⑤ 标题在 Compact 档压成单行：两行标题 + 把手 = 这一行仍然吃掉整块高度。 */
    @Test
    fun `一行档的标题只占一行`() {
        val maxLines = notice.first { it.startsWith("maxLines = if (hasTitle)") }
        assertTrue("标题行数没按档位分：$maxLines", maxLines.contains("if (compact) 1 else 2"))
        assertTrue("超长要省略而不是换行", maxLines.contains("else 3"))
    }

    /** ⑥ 行为尺子：四个档、一站与两站并起，标题都是**一行且短到读得完**。 */
    @Test
    fun `每个档的标题在手机上都是一行短句`() {
        val gelbooru = GallerySite.GELBOORU
        val yande = GallerySite.YANDERE
        val cases = mapOf(
            GalleryNoticeKind.MissingAccount to mapOf(
                gelbooru to GallerySearchSource.NEEDS_ACCOUNT_REASON,
            ),
            GalleryNoticeKind.LegFailed to mapOf(gelbooru to "Read timed out"),
            GalleryNoticeKind.NoContent to mapOf(yande to GalleryLegGuard.NO_CONTENT),
            GalleryNoticeKind.NoRecommendTags to mapOf(gelbooru to noRecommendTagsReason(gelbooru)),
        )
        cases.forEach { (kind, reasons) ->
            val title = noticeTitle(galleryNotices(reasons).single())
            assertEquals("档位对不上：$kind → $title", kind, galleryNotices(reasons).single().kind)
            assertFalse("标题里不许有换行（一行档会把后面的行吃掉）：$title", title.contains("\n"))
            assertTrue("标题超长，首屏那一行只会读成省略号：「$title」(${title.length})", title.length <= 32)
        }

        // 两站同类并起：站名是逐站并列的，最容易在这儿超长度上限。
        val twoSite = mapOf(
            gelbooru to GalleryLegGuard.NO_CONTENT,
            yande to GalleryLegGuard.NO_CONTENT,
        )
        val joined = noticeTitle(galleryNotices(twoSite).single())
        assertFalse("两站并起也不许换行：$joined", joined.contains("\n"))
        assertTrue("两站并起的标题超长：「$joined」(${joined.length})", joined.length <= 32)
    }
}
