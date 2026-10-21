package com.venera.desktop.gallery.data

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第一百一十二批 · 「补齐」排出去之前，把盘上那一形换回站点原号。
 *
 * 题面与现量在 rounds `desktop-shelf-raw-id-2026-10-15.md`：行模型那格是目录名（净化形），
 * 队列表那格是原号，清单里没有 comicId ⇒ 盘上读不回原号。真缺陷面现量是 3/34 档源。
 *
 * ⚠️ 表由调用点**显式递进来**而不是让件自己去读队列单例 —— 单例跨用例残留会让"能不能还原"变成随机事。
 */
class DesktopComicShelfRawIdTest {

    private fun task(sourceKey: String, comicId: String, order: Int = 0, updated: Long = 1L) = DesktopComicQueueTask(
        taskId = "t_$sourceKey" + comicId + "_" + order, sourceKey = sourceKey, comicId = comicId,
        comicTitle = "某一本", comicCover = "", chapterId = "19777@1253812", chapterTitle = "第01话",
        chapterOrder = order, directoryPath = "$sourceKey/$comicId/0000_第01话",
        status = DesktopComicQueueStatus.DONE, downloadedPages = 30, totalPages = 30, error = "",
        placeholder = false, createTime = 1L, updateTime = updated,
    )

    private fun chapter(comicId: String, sourceKey: String = "goda") = DesktopComicDownloadedChapter(
        sourceKey = sourceKey, comicId = comicId, chapterId = "19777@1253812", order = 0, title = "第01话",
        expectedPages = 30, writtenPages = 28, bytes = 2_658_270L, complete = false,
    )

    /** ① 表里有那一笔 ⇒ 递出去的是原号，而目录仍是盘上那一颗（两句必须一起成立）。 */
    @Test
    fun 回收到的号进队而目录不长出第二棵() {
        val index = DesktopComicShelfRawIdIndex.of(listOf(task("goda", "/manga/panda")))
        val args = desktopComicShelfRepairArgsOf(chapter("_manga_panda"), index)
        assertEquals("递出去的是站点那一形：", "/manga/panda", args.comicId)
        assertEquals(
            "而 enqueue 造的目录还是盘上那一颗（净化回来的正是它）：",
            "goda/_manga_panda/0000_第01话",
            desktopComicQueueRelativeDir(args.sourceKey, args.comicId, args.chapterOrder, args.chapterTitle),
        )
    }

    /** ② 表里查不到 ⇒ 沿用行里那一格，不抛也不空（31/34 档源本来就吃这一档）。 */
    @Test
    fun 查不到就沿用行里那格而不抛() {
        val args = desktopComicShelfRepairArgsOf(chapter("1461316"), DesktopComicShelfRawIdIndex.of(emptyList()))
        assertEquals("号原样：", "1461316", args.comicId)
        assertFalse("查不到就不是还原：", desktopComicShelfRepairComicId(chapter("1461316"), DesktopComicShelfRawIdIndex.of(emptyList())).second)
    }

    /** ③ 同一枚盘上形留下两笔 ⇒ 优先认"看得出是原号"那一笔，其次才按时间（批 111 的把手留下的正是目录形）。 */
    @Test
    fun 多笔时优先认得出原号那一笔() {
        val diskFirst = DesktopComicShelfRawIdIndex.of(
            listOf(task("goda", "_manga_panda", updated = 9L), task("goda", "/manga/panda", updated = 5L)),
        )
        assertEquals("残留那笔更新也不认（原号那一笔优先）：", "/manga/panda", diskFirst.rawIdOf("goda", "_manga_panda"))
        val numeric = DesktopComicShelfRawIdIndex.of(
            listOf(task("goda", "x:y", updated = 5L), task("goda", "x/y", updated = 9L)),
        )
        assertEquals(
            "两笔都看得出原号却又净成同一形（`x:y` 与 `x/y`）⇒ 这层歧义只能认最近那一笔：",
            "x/y", numeric.rawIdOf("goda", "x_y"),
        )
    }

    /** ④ 键里带站名：别站的同一枚净化名不许冒充（认下来会把 A 本的页写进 B 本的目录）。 */
    @Test
    fun 两站同枚净化名不许互认() {
        val index = DesktopComicShelfRawIdIndex.of(listOf(task("jm", "/manga/panda")))
        assertNull("goda 那一行不能吃 jm 那笔：", index.rawIdOf("goda", "_manga_panda"))
        assertEquals("同站那一行才认：", "/manga/panda", index.rawIdOf("jm", "_manga_panda"))
    }

    /** ⑤ 还原与沿用两种果各有各的话（含糊成一句就等于不说）。 */
    @Test
    fun 那一句念得出用的是哪一形() {
        assertEquals(
            "还原那一档：", " · 号按本机记录还原成 /manga/panda",
            desktopComicShelfRepairIdNote("/manga/panda", resolved = true),
        )
        assertEquals(
            "沿用那一档：", " · 本机没有这号的原始记录，排的是目录那一形 _manga_panda",
            desktopComicShelfRepairIdNote("_manga_panda", resolved = false),
        )
    }

    /** ⑥ 净化只许在回收那颗件里出现三处（建键 + 从原号算目录名 + 判"像不像原号"），读侧一次都不许。 */
    @Test
    fun 尺子只在这两颗件里磨() {
        val rawIdFile = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicShelfRawId.kt")
        assertEquals("回收那颗件里恰三处（第四处就说明有人在别处另算一次净化）：", 3, DesktopSourceTree.codeOccurrences(rawIdFile, "desktopComicSafePathKey("))
        val listFile = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicDownloadedList.kt")
        assertEquals("读侧不认得队列表，也不净化（钉了这一条它就再也搬不进去）：", 0, DesktopSourceTree.codeOccurrences(listFile, "desktopComicSafePathKey("))
    }

    /** ⑦ 两枚种子的命中判据两边同走一次净化（钉整串表达式，并禁掉旧那一形 —— 只数新形会放过"改了一半"）。 */
    /** ⑧ 回收住在入队那一跳，而不在读侧那一屏（读侧吃了队列状态，屏上「已下全」就会随表变动而改口）。 */
    @Test
    fun 回收住在入队那一跳() {
        val repair = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicShelfRepair.kt")
        assertEquals("那一跳吃index（恰一处）：", 1, DesktopSourceTree.codeOccurrences(repair, "desktopComicShelfRepairArgsOf(chapter, index)"))
        assertEquals("那一句带上是哪一形（屏上那句与种子那句各一处）：", 2, DesktopSourceTree.codeOccurrences(repair, "desktopComicShelfRepairIdNote("))
        val listFile = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicDownloadedList.kt")
        assertEquals("读侧零处引用回收那颗件：", 0, DesktopSourceTree.codeOccurrences(listFile, "DesktopComicShelfRawIdIndex"))
    }

    /** ⑩ 表里那一行本来就是盘上形（批 111 的把手留下的就是它）⇒ 那句不许谎称「还原过」。 */
    @Test
    fun 残留那笔不算还原() {
        val index = DesktopComicShelfRawIdIndex.of(listOf(task("goda", "_manga_panda", updated = 9L)))
        val (used, resolved) = desktopComicShelfRepairComicId(chapter("_manga_panda"), index)
        assertEquals("号还是那一格：", "_manga_panda", used)
        assertFalse("认下来就等于谎称从本机记录还原：", resolved)
    }

    /** ⑨ 行模型没被偷偷加格（批 105 那本账：多一枚要吃 6 处穷尽 when + 7 处构造）。 */
    @Test
    fun 行模型仍是那九格() {
        val fields = DesktopComicDownloadedChapter::class.java.declaredFields
            .filterNot { it.isSynthetic }
            .filterNot { it.name.startsWith("$") }
        assertEquals(
            "行模型那九格（多一枚就要重开批 105 那本账）：" + fields.map { it.name }.sorted(),
            9, fields.size,
        )
    }
}
