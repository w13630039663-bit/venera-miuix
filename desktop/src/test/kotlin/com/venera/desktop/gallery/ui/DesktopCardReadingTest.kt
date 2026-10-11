package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十二批 ①②：两处"屏上那句话不是真话"的读数。
 *
 * ## ① 卡底那行「分数 0」
 *
 * `GalleryPost.score` 是**非空 `Int`、缺省 0**（`GalleryPost.kt:46`），而 Gelbooru 站方
 * **真会给 `"score":0`**（实测原文在 `GelbooruClient.kt:349`）—— 于是"这一张零分"与
 * "这一站没给这个数"在屏上是同一句话。本仓对 `0` 的口径早就定了：
 * 隔壁那颗 `favCount` 是 `Int?`（`:53`）、Android 那张表用 `fileSize > 0` 挡（`GalleryInfoSheet.kt:437`）、
 * 分辨率取不到就**整格不摆**（`:429`）。所以这一格不是审美问题，是假读数。
 *
 * ⚠️ 同一条判据顺手覆盖「标签 0 枚」：`tags` 缺省是空串，`tagList` 空既可能是"站方真没给标签"
 * 也可能是"这一条没被解出来"，两件事同样不许用一句「0 枚」替它们说话。
 *
 * ## ② 画师头像"没取到"被记成"查过了、没有"
 *
 * 第十批 ② 我自己定过一条原则：**只有定论才落档**。而 `desktopArtistProbedFace`
 * 把失败与定论压成同一个 `null`（`DesktopArtistsPolicy.kt:164-179`：失败只 warn 后 return null），
 * 那颗 `null` 又直接进 `probed`（`DesktopArtistsPage.kt:82`）⇒ 一次网络抖动就让那位画师
 * **永久**是字母座，而且这一屏再也不问。这一条抓的就是那个退化。
 */
class DesktopCardReadingTest {

    private fun post(score: Int = 0, tags: String = "") = GalleryPost(
        site = GallerySite.GELBOORU,
        id = 11L,
        tags = tags,
        score = score,
        previewUrl = "https://p/11.jpg",
    )

    /** ① 分数与标签两半**各自可缺**，两半都缺就整行不摆。 */
    @Test
    fun 零分不再被念成分数() {
        assertEquals("有分有标签是原样两半", "分数 16 · 标签 3 枚", desktopCardMetricLine(post(score = 16, tags = "a b c")))
        assertEquals(
            "零分不摆分数那半 —— 而它既可能是真零分也可能是站方没给，两种都不能被念成'零分'",
            "标签 2 枚",
            desktopCardMetricLine(post(score = 0, tags = "a b")),
        )
        assertEquals("没有标签就不摆标签那半", "分数 7", desktopCardMetricLine(post(score = 7, tags = "")))
        assertEquals(
            "两半都没有就**整行不摆**（摆一行空的是噪声，摆「分数 0 · 标签 0 枚」是假读数）",
            null,
            desktopCardMetricLine(post(score = 0, tags = "")),
        )
        // 对照组：负数也不摆（站方没给时不该出现，但钳子要比"只判 !=0"严）。
        assertEquals(null, desktopCardMetricLine(post(score = -1, tags = "")))
    }

    /** ①′ hero 那两行共用同一颗判据件，不许各写一份算式。 */
    @Test
    fun 三处读数共用同一颗件() {
        val sites = listOf("DesktopGalleryPostTile.kt", "DesktopDailyHero.kt")
        val offenders = sites.flatMap { name ->
            DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopUiSource(name))
                .filter { it.contains("分数 ") }
                .map { "$name：$it" }
        }
        assertTrue(
            "还有地方在**自己拼**分数那半（口径就会长成三份，而漏改的那份继续念「分数 0」）：" +
                offenders.joinToString(" ｜ "),
            offenders.isEmpty(),
        )
        sites.forEach { name ->
            val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(name))
            assertTrue("「$name」没转交那颗共用的读数件", code.contains("desktopCardMetricLine("))
        }
    }

    /** ② 三态里只有两态算定论；失败那一态不许进落档表。 */
    @Test
    fun 只有定论才落档() {
        val probed = mutableMapOf<String, String?>()
        val tried = mutableSetOf<String>()

        desktopFaceProbeRecord(probed, tried, "a", DesktopFaceProbe.Found("https://av/a.png"))
        assertEquals("取到了要落档（下次进屏不再问）", "https://av/a.png", probed["a"])

        desktopFaceProbeRecord(probed, tried, "b", DesktopFaceProbe.DefinitiveNone)
        assertTrue("站方确实没有头像 ⇒ 这是一句**定论**，要落档（落 null）", probed.containsKey("b"))
        assertEquals(null, probed["b"])

        desktopFaceProbeRecord(probed, tried, "c", DesktopFaceProbe.Failed("java.net.SocketTimeoutException"))
        assertFalse(
            "没取到**不是**定论：落进 probed 就等于宣布「这位画师没有头像」，而真相是我们没问到",
            probed.containsKey("c"),
        )

        // 三态都进 tried：这一屏内不重发（少了这张表，来回滚动就是无限请求）。
        assertEquals("三态都要记'这一屏试过了'（防无限重发）", setOf("a", "b", "c"), tried)
    }

    /** ②′ 闸门吃的是"定论 ∪ 试过"两把键，而不是只看落档表。 */
    @Test
    fun 待发清单要同时避开两把键() {
        val targets = desktopVisibleProbeTargets(
            visibleKeys = listOf("artist:a", "artist:b", "artist:c", "artist:d"),
            resolved = setOf("b", "c"),
            hasFace = setOf("a"),
        )
        assertEquals("a 本机已有、b/c 这一屏试过了 ⇒ 只剩 d", listOf("d"), targets)
    }

    /** ②″ 失败那一句要说得出**是哪一档**（"没取到 / 没有 / 没接"是三句话）。 */
    @Test
    fun 三态各有一句人话() {
        assertEquals("取到与没有都不该在屏上留字", "", desktopFaceProbeNotice(DesktopFaceProbe.Found("x")))
        assertEquals("站方没有这一档，要说'没有'", "没有", desktopFaceProbeNotice(DesktopFaceProbe.DefinitiveNone))
        assertTrue(
            "没取到要说'没取到'并带上成因的头一段（便于报障），不许与'没有'混成一句",
            desktopFaceProbeNotice(DesktopFaceProbe.Failed("SocketTimeoutException: timeout")).let {
                it.contains("没取到") && it.contains("SocketTimeoutException")
            },
        )
    }
}
