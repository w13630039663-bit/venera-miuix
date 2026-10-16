package com.venera.desktop.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 99 · 卡上那一枚「已下载 / 离线不全」的两档算式。
 *
 * ⚠️ 这一族在一条线上一共存着**三把"齐不齐"的口径**（对岸 `DownloadManager.stateOfChapterDir` 吃
 * `expectedPages` + 清单 `complete` + `listFiles` 计数、对岸 `LocalComicManager` 只看有没有图、
 * 桌面批 96 立的第三口径 = 清单 `complete:true` 且逐页查盘非空）。这一屏**只许吃第三口径已经算好的那一格**，
 * 所以 ④ 那一条把两种值各喂一遍：只吃 `complete`，别的一概不看。
 */
class DesktopComicOfflineBadgeTest {

    private fun chapter(
        sourceKey: String,
        comicId: String,
        order: Int,
        complete: Boolean,
        written: Int = 3,
        expected: Int = 3,
    ) = DesktopComicDownloadedChapter(
        sourceKey = sourceKey,
        comicId = comicId,
        chapterId = "c$comicId-$order",
        order = order,
        title = "第${order + 1}話",
        expectedPages = expected,
        writtenPages = written,
        bytes = 1024L,
        complete = complete,
    )

    /** ① 每一话都齐 ⇒ 「已下载」那一档，且话数数得着。 */
    @Test
    fun 每话都齐那一本算已下载() {
        val badges = desktopComicOfflineBadges(
            listOf(chapter("jm", "1461316", 0, true), chapter("jm", "1461316", 1, true))
        )
        assertEquals(1, badges.size)
        assertEquals(
            "两话都齐该念已下载 2 话：" + badges,
            DesktopComicOfflineBadge.Downloaded(chapters = 2),
            badges[desktopComicOfflineKey("jm", "1461316")],
        )
    }

    /** ② 有话不齐 ⇒ 「离线不全」那一档，且「齐了几 / 共几」两枚数都念得出。 */
    @Test
    fun 有话不齐那一本算离线不全() {
        val badges = desktopComicOfflineBadges(
            listOf(
                chapter("jm", "9", 0, true),
                chapter("jm", "9", 1, false, written = 1, expected = 89),
                chapter("jm", "9", 2, false, written = 0, expected = 20),
            )
        )
        assertEquals(
            "一齐两缺该念 1/3：" + badges,
            DesktopComicOfflineBadge.Partial(complete = 1, total = 3),
            badges[desktopComicOfflineKey("jm", "9")],
        )
    }

    /** ③ 一本都没有 ⇒ 空表。给"零话"编一枚标记就是屏上多一句假话。 */
    @Test
    fun 没有整话时交回空表() {
        assertTrue("空树不该有标记：" + desktopComicOfflineBadges(emptyList()), desktopComicOfflineBadges(emptyList()).isEmpty())
    }

    /**
     * ④ 齐不齐**只吃 `complete` 那一格**（两枚值各喂一种，这是"不许在旁边再写第二种口径"的正证）。
     *
     * `complete=true` 而页对不上 ⇒ 仍算齐；`complete=false` 而页对得上 ⇒ 仍算不齐。
     * 谁要是把这一判改成自己数页，这一条就红。
     */
    @Test
    fun 只吃清单那一格而不自己数页() {
        val trustManifest = desktopComicOfflineBadges(listOf(chapter("jm", "7", 0, complete = true, written = 0, expected = 89)))
        assertEquals(
            "清单说齐而页对不上时仍按齐（口径只有批 96 那一把）：" + trustManifest,
            DesktopComicOfflineBadge.Downloaded(chapters = 1),
            trustManifest[desktopComicOfflineKey("jm", "7")],
        )
        val distrustManifest = desktopComicOfflineBadges(listOf(chapter("jm", "8", 0, complete = false, written = 89, expected = 89)))
        assertEquals(
            "清单说不齐而页对得上时仍按不齐：" + distrustManifest,
            DesktopComicOfflineBadge.Partial(complete = 0, total = 1),
            distrustManifest[desktopComicOfflineKey("jm", "8")],
        )
    }

    /** ⑤ 键是「站 + 号」：同号异站不许串（对岸那棵树本来就按站分目录，串了就给 B 站的本子贴 A 站的标记）。 */
    @Test
    fun 同号异站不串() {
        val badges = desktopComicOfflineBadges(listOf(chapter("jm", "1461316", 0, complete = true)))
        assertTrue("jm 那一本该有标记：" + badges, badges.containsKey(desktopComicOfflineKey("jm", "1461316")))
        assertFalse("goda 同号不该跟着有：" + badges, badges.containsKey(desktopComicOfflineKey("goda", "1461316")))
    }

    /** ⑥ 两档标签各念各的，都不带取证那串、都不带内部黑话。 */
    @Test
    fun 两档标签各说一句() {
        val full = desktopComicOfflineBadgeLabel(DesktopComicOfflineBadge.Downloaded(chapters = 2))
        val partial = desktopComicOfflineBadgeLabel(DesktopComicOfflineBadge.Partial(complete = 1, total = 3))
        assertEquals("已下载 2 话", full)
        assertEquals("离线不全 1/3 话", partial)
        listOf(full, partial).forEach {
            assertFalse("屏上句不许带读数：" + it, it.contains("D_"))
            assertFalse("屏上句不许带反引号那种实现笔记：" + it, it.contains("`"))
        }
    }

    /** ⑦ 命中数：只数**同一站**对上的号（墙上是 goda 的号而盘上是 jm 的，不算贴上）。 */
    @Test
    fun 命中数只数同站对上的那些() {
        val badges = desktopComicOfflineBadges(
            listOf(chapter("jm", "1", 0, true), chapter("goda", "2", 0, true))
        )
        assertEquals("jm 墙上只有号 1 对得上：" + badges, 1, desktopComicOfflineHits(badges, "jm", listOf("1", "2", "3")))
        assertEquals("goda 墙上只有号 2 对得上：", 1, desktopComicOfflineHits(badges, "goda", listOf("1", "2", "3")))
        assertEquals("墙上一枚都对不上时念零：" , 0, desktopComicOfflineHits(badges, "nhentai", listOf("1", "2")))
    }

    /** ⑧ 命中那句整串钉死：五格各归各的，而 `样例=` 只念前 5 枚（喂 7 枚）。 */
    @Test
    fun 命中那句五格齐全而样例只念五枚() {
        val badges = desktopComicOfflineBadges(listOf(chapter("jm", "a1", 0, true)))
        assertEquals(
            "一句要答'墙上几张贴了几枚'：" + desktopComicOfflineHitReading("jm", badges, listOf("a1", "b2", "c3")),
            "D_卡标命中 站=jm 墙=3 贴上=1 盘上=1 样例=a1,b2,c3",
            desktopComicOfflineHitReading("jm", badges, listOf("a1", "b2", "c3")),
        )
        val seven = listOf("1", "2", "3", "4", "5", "6", "7")
        val line = desktopComicOfflineHitReading("jm", emptyMap(), seven)
        assertTrue("样例该只念前五枚：" + line, line.endsWith("样例=1,2,3,4,5"))
        assertFalse("样例不许把整墙贴出来：" + line, line.contains("6,7"))
        assertEquals("空墙那一档也念得出而不抛：", "D_卡标命中 站=jm 墙=0 贴上=0 盘上=0 样例=",
            desktopComicOfflineHitReading("jm", emptyMap(), emptyList()))
    }

    /** ⑨ 读数那句：三格各归各的，空表那一档也念得出（"没念"与"念了零"是两件事）。 */
    @Test
    fun 读数那句三格齐全() {
        val badges = desktopComicOfflineBadges(
            listOf(
                chapter("jm", "1", 0, true),
                chapter("jm", "2", 0, false, written = 1, expected = 5),
                chapter("jm", "2", 1, true),
            )
        )
        assertEquals(
            "两本一本全齐一本不全：" + desktopComicOfflineReading("jm", badges),
            "D_卡标 站=jm 卡=2 全齐=1 不全=1",
            desktopComicOfflineReading("jm", badges),
        )
        assertEquals(
            "空表也要念得出零：" + desktopComicOfflineReading("goda", emptyMap()),
            "D_卡标 站=goda 卡=0 全齐=0 不全=0",
            desktopComicOfflineReading("goda", emptyMap()),
        )
    }
}
