package com.venera.desktop.gallery.data

import com.venera.desktop.gallery.ui.DesktopSourceTree
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * 第一百零三批 · 页那一笔落盘走的是**原子形**。
 *
 * 题面是现量到的**同一颗件里两形并存**：清单那一笔从前就走「同目录临时档 + 带覆盖语义的改名」，
 * 而页那一笔是裸 `writeBytes` ⇒ 进程被杀在半途会留下一颗**非 0 字节的半截页**，
 * 下一趟把它当已有页跳过、清单最终仍落 `complete:true` ——
 * 症状与批 102 治的那一族逐字相同，只是成因从「站方回的不是图像」换成「写没写完」。
 *
 * ⚠️ 本批只把**落盘那一步**改成原子形，**不动** `present` 与 `desktopComicChapterIsComplete`
 * （「跳过已有页之前先读一次文件头」那一格要改判的是全仓唯一那把判齐尺子，另批办）。
 * ⚠️ Windows 那一课（本仓在共享 store 那一族栽过一次）：`File.renameTo` 在目标已存在时**只返回 `false` 而不抛** ⇒
 * 这里必须用带覆盖语义的那一颗改名，判式 ② 专门跑「同一目标连续两笔」。
 */
internal class DesktopComicAtomicWriteTest {

    /** 带着 JPEG 头的 12 字节桩（批 102 那道闸的下界），第四枚留给人换内容。 */
    private fun page(marker: Int): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), marker.toByte(),
        0, 1, 2, 3, 4, 5, 6, 7,
    )

    private fun tempRoot(): File = java.nio.file.Files.createTempDirectory("b103").toFile()

    private val dataFile = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicChapterDownload.kt")

    /** ① 那颗件把字节交到一个**已存在**的目标上：内容换过来，而临时档一颗都不留。 */
    @Test
    fun 交进已存在的目标而临时档不留盘() {
        val dir = File(tempRoot(), "a").apply { mkdirs() }
        val target = File(dir, "0001.jpg")
        target.writeBytes(page(0x11))
        desktopComicAtomicWriteTo(target, page(0x22))
        assertTrue("目标那一颗的内容必须是新交进去的那一串：", page(0x22).contentEquals(target.readBytes()))
        assertFalse("临时档不留盘（finally 里删）：", File(dir, "0001.jpg.tmp").exists())
        assertEquals("目录里只许有目标那一颗：", 1, dir.list()!!.size)
    }

    /**
     * ② 同一目标**连续两笔**，第二笔仍生效 —— 这一格钉的就是 Windows 那一课：
     * 有人把改名换成 `File.renameTo` 时第二笔只返回 `false` 而不抛，屏上看着一切正常而盘上仍是旧的那一颗。
     */
    @Test
    fun 同一目标连续两笔而第二笔仍生效() {
        val dir = File(tempRoot(), "b").apply { mkdirs() }
        val target = File(dir, "0001.jpg")
        desktopComicAtomicWriteTo(target, page(0xAA))
        desktopComicAtomicWriteTo(target, page(0xBB))
        assertTrue("第二笔不许静默失败（`renameTo` 在 Windows 不覆盖）：", page(0xBB).contentEquals(target.readBytes()))
        assertEquals("两笔都不留临时档：", 1, dir.list()!!.size)
    }

    /**
     * ③ 临时档**不算一页**：盘上只有 `0001.jpg.tmp` 而清单说齐 ⇒ 那一话仍算不齐，
     * 而那一趟续跑也不把 `.tmp` 当已有页跳过（`written=1` 而不是 0）。
     * ⚠️ 这一格今天就是绿的（那两判吃的是精确页名），它是**回归保险**：改成按前缀数页的那一类写法就会红。
     */
    @Test
    fun 临时档不算已有页也不判齐() {
        val dir = File(tempRoot(), "c").apply { mkdirs() }
        File(dir, "0001.jpg.tmp").writeBytes(page(0x33))
        File(dir, "chapter.json").writeText(
            """{"chapterId":"1461318","title":"t","order":1,"expectedPages":1,"complete":true}""",
        )
        assertFalse("只有临时档而真页缺席 ⇒ 不许说齐：", desktopComicChapterIsComplete(dir, 1))
        val run = runBlocking {
            desktopComicDownloadChapter(dir, "1461316", "1461318", 1, "t", 1,
                shouldCancel = { false }, progress = { _, _ -> }) { page(0x44) }
        }
        assertEquals("续跑那一趟不许把临时档当成已有页而少写一页：", 1, (run as DesktopComicChapterRun.Completed).written)
    }

    /** ④ 页那一笔**走的是那颗件**（接线计数：声明一处 + 清单一处 + 页一处；直写目标那一形只剩临时档内部那一处）。 */
    @Test
    fun 页那一笔走的是那颗共用原子件() {
        assertEquals("那颗件在 main 里必须恰有三处（声明 + 清单 + 页）：", 3, DesktopSourceTree.codeOccurrences(dataFile, "desktopComicAtomicWriteTo("))
        assertEquals(
            "直写字节那一形整棵树里只许出现在那颗件的临时档那一笔（页与清单都不许再直写）：",
            1,
            DesktopSourceTree.codeOccurrences(dataFile, ".writeBytes(bytes)"),
        )
    }

    /**
     * ⑤ 改名那两枚参数与那颗兜底都在，而 `File.renameTo` 一颗都不许出现。
     * ⚠️ 负向钉的是 `.renameTo(` 而不是「用了 `Files.move`」—— 后者对「先 renameTo 再补一刀 move」那种写法是绿的。
     */
    @Test
    fun 改名带覆盖语义而不许用那颗不抛的旧件() {
        val code = DesktopSourceTree.codeText(dataFile)
        assertTrue("改名必须是带覆盖语义的那一颗：", code.contains("StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE"))
        // ⚠️ 这一枚钉的是**那一跳本身**而不是那颗符号名：符号名在 `import` 行里也出现，
        //    只 `contains("AtomicMoveNotSupportedException")` 的话把 catch 整块撤掉它照样绿（台架 M4 实测就是这么漏的）。
        assertEquals("不支持原子改名那一档要有兜底那一跳：", 1, DesktopSourceTree.codeOccurrences(dataFile, "catch (unsupported: AtomicMoveNotSupportedException)"))
        assertEquals("不许换成 `File.renameTo`（Windows 上它只返回 false 而不抛）：", 0, code.split(".renameTo(").size - 1)
        assertEquals("临时档只在 finally 里删一处：", 1, DesktopSourceTree.codeOccurrences(dataFile, "temp.delete()"))
    }

    /** ⑥ 中途取页失败那一趟：目录里只剩真页，一颗临时档都不留。 */
    @Test
    fun 失败那一趟不留临时档只留真页() {
        val dir = File(tempRoot(), "d").apply { mkdirs() }
        runCatching {
            runBlocking {
                desktopComicDownloadChapter(dir, "1461316", "1461319", 1, "t", 3,
                    shouldCancel = { false }, progress = { _, _ -> }) { index ->
                    if (index == 1) error("站方那一页回不动") else page(index + 1)
                }
            }
        }.onSuccess { fail("第二页取不到本该抛出而它成了：$it") }
        assertEquals("第一页留着：", 1, dir.listFiles { f -> f.name.endsWith(".jpg") }!!.size)
        assertEquals("目录里只许有那一页与清单，不许有临时档：", 2, dir.list()!!.size)
    }
}
