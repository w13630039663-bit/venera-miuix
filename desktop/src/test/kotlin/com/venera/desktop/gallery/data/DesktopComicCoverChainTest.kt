package com.venera.desktop.gallery.data

import com.venera.desktop.gallery.ui.DesktopComicTarget
import com.venera.desktop.gallery.ui.DesktopSourceTree
import com.venera.engineprobe.EngineSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 82.6 · 历史那一笔的**封面**从哪来。
 *
 * ## 量出来的缺口
 *
 * `comic_history` 是两端共用的一张表。对岸那一处写口落的是 `coverUrl = session.coverUrl`，
 * 而桌面这一处落的是**空串**（批 82 当时的说明写着「阅读那一屏没有封面地址」—— 量过是假的：
 * 首页那一行手上有 `card.cover`，历史那一行手上有库里已有的那串，而 `loadInfo` 的答复里
 * 还有一栏 `cover` 桌面从前根本没读）。
 *
 * 后果不是"少一张图"这么轻：`saveHistory` 那条「空封面沿用旧值」只救**已经有封面**的行，
 * 桌面第一次写的那一行从此永久是空白框（手机侧历史页读 `cover_url`），且不会自愈。
 *
 * ## 为什么这里不"设计"优先级
 *
 * 对岸的口径写在 `ComicDetailViewModel` 那一处：**详情解析出来的那张优先，入口带的兜底**，
 * 而它旁边就有一段成文的理由（照抄入口值 = 给历史页留永久空白框）。桌面照同一条，不另定。
 */
class DesktopComicCoverChainTest {

    private fun progress(cover: String) = DesktopReadingProgress(
        comicId = "1461316",
        title = "某本漫画",
        sourceKey = "jm",
        chapterTitle = "第 2 话",
        chapterIndex = 1,
        pageIndex = 7,
        totalPages = 89,
        coverUrl = cover,
    )

    @Test
    fun 历史那一笔吃载荷里那枚封面() {
        // 两种值各喂一次：只喂一种时"吃载荷"与"写死一个常数"在这把尺子下一模一样。
        assertEquals(
            "载荷给了地址就落库 —— 对岸那一段写的就是 `coverUrl = session.coverUrl`",
            "https://cdn.example/1461316.jpg",
            desktopHistoryRecord(progress("https://cdn.example/1461316.jpg"), nowMillis = 1_700_000_000_000L).coverUrl,
        )
        assertEquals(
            "载荷没地址时才是空串（那一档由那颗件的「空封面沿用旧值」兜住已有的行）",
            "",
            desktopHistoryRecord(progress(""), nowMillis = 1_700_000_000_000L).coverUrl,
        )
    }

    @Test
    fun 会话封面按对岸那条优先级选() {
        assertEquals(
            "详情回了封面就用详情那张，入口那枚只在它缺货时才用",
            "https://detail.jpg",
            desktopSessionCover("https://detail.jpg", "https://card.jpg"),
        )
        assertEquals(
            "详情那栏是**空白**（不是 null）就退入口 —— 对岸用的是 ifBlank，照它",
            "https://card.jpg",
            desktopSessionCover("   ", "https://card.jpg"),
        )
        assertEquals(
            "两档都没才落空串，而且**原样带**那枚空白串（对岸不 trim；写口那颗件按 blank 兜底，桌面不自己加一刀）",
            "  ",
            desktopSessionCover("", "  "),
        )
    }

    @Test
    fun 阅读载荷把算好的封面带下去() {
        val target = DesktopComicTarget("1461316", "卡上题名", "https://card.jpg")
        val episode = EngineSession.Episode("第 2 话", "g")
        assertEquals(
            "详情那一张优先",
            "https://detail.jpg",
            com.venera.desktop.gallery.ui.desktopComicReadingOf(
                target, episode, 1, startPage = 0, detailCover = "https://detail.jpg"
            ).coverUrl,
        )
        assertEquals(
            "详情没给 ⇒ 落卡上那一枚（而不是空串）",
            "https://card.jpg",
            com.venera.desktop.gallery.ui.desktopComicReadingOf(
                target, episode, 1, startPage = 0, detailCover = ""
            ).coverUrl,
        )
    }

    @Test
    fun 对岸那两处口径仍在() {
        val reader = DesktopSourceTree.androidSource("reader/VeneraReaderScreen.kt").readText()
        assertEquals(
            "对岸写口不再吃会话封面了 ⇒ 桌面这一条要跟着改判，而不是自己定一套",
            1,
            Regex("""coverUrl\s*=\s*session\.coverUrl""").findAll(reader).count(),
        )
        val detail = DesktopSourceTree.androidSource("feature/ComicDetailViewModel.kt").readText()
        assertTrue(
            "对岸那条「详情优先、入口兜底」的式子改形状了 ⇒ `desktopSessionCover` 要回核",
            Regex("""details\?\.comic\?\.cover\?\.ifBlank \{ comic\.coverUrl \} \?: comic\.coverUrl""")
                .containsMatchIn(detail),
        )
    }

    @Test
    fun 种子那一趟的载荷不猜封面() {
        val seeded = com.venera.desktop.ForensicFlags.read(listOf("--read=1461316/g"), startPage = null)
        assertEquals(
            "种子没经过详情那一行，手里就是没有地址 ⇒ 空串是实话（填一枚猜出来的会把没量过的东西写进共用的表）",
            "",
            seeded?.coverUrl,
        )
        // 对照：同一颗件给过非空值时必须带得下去 —— 少了这一半，上面那条会因"恒空"而假绿。
        val target = DesktopComicTarget("1461316", "题名", "https://card.jpg")
        assertEquals(
            "入口那枚在非空那一档仍要流到载荷里",
            "https://card.jpg",
            com.venera.desktop.gallery.ui.desktopComicReadingOf(
                target, EngineSession.Episode("第 1 话", "g"), 0, startPage = 0, detailCover = ""
            ).coverUrl,
        )
    }

    @Test
    fun 那句没有封面地址的旧说明已下架() {
        val text = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicHistory.kt").readText()
        // 扫全文不跳注释（与 DesktopStaleClaimsTest 同一口径）：那句假话正住在注释里。
        assertEquals("还有文件写着「阅读那一屏没有封面地址」", 0, text.lines().count { it.contains("没有封面地址") })
        assertEquals(
            "对照失效：写口不再吃载荷里那枚封面了（那一句该跟着回来）",
            1,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicHistory.kt"),
                "coverUrl = p.coverUrl",
            ),
        )
    }
}
