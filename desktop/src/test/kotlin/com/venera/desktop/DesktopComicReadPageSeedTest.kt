package com.venera.desktop

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 82.5 · `--read-page=` 那颗种子（纯判据）。
 *
 * ## 为什么这一枚是**补量具**而不是新功能
 *
 * 批 82 收尾时「滚到哪一页就记到哪一页」那一格只能写"没验"：离屏语义树里的 `LazyListState`
 * 量不到滚动（`scrollToItem(3)` 与 `rememberLazyListState(initialFirstVisibleItemIndex = 3)`
 * 之后 `firstVisibleItemIndex` 都还是 0 —— 那一档的读数在 rounds 那份文件里）。
 * 这一枚把"从第几页起"变成命令行进来的种子：真窗口里那一屏一开局就滚到那一页，
 * 于是"页号从 `LazyListState` 流进写的那一笔"这件事有了一行 stdout 与库里那一行，不再靠推断。
 *
 * 判读走**独立一枚**而不是 `--read` 的第四截：章名里允许有斜杠（同 [ForensicFlags.read] 那条），
 * 多切一截就会把章名劈掉一截，而劈剩的那半在屏上看着像"章名就这么短"。
 */
class DesktopComicReadPageSeedTest {

    @Test
    fun 页号三档各自读数() {
        assertNull("没给：不念、不报、不种", ForensicFlags.readPage(emptyList()))
        assertEquals(3, ForensicFlags.readPage(listOf("--read-page=3")))
        // 只给名字不带值是打错了，不是"从第 0 页起"—— 那一档必须与"没给"分得开
        assertNull(ForensicFlags.readPage(listOf("--read-page")))
        assertNull(ForensicFlags.readPage(listOf("--read-page=")))
    }

    @Test
    fun 不是个数与负数都不替人猜一个页号() {
        assertNull(ForensicFlags.readPage(listOf("--read-page=abc")))
        assertNull(ForensicFlags.readPage(listOf("--read-page=-2")))
        // 对照组：同一条判读确实认得出合法的数（读空会让上面两条恒真）
        assertEquals("第 1 页的索引就是 0，别把合法的 0 读成没给", 0, ForensicFlags.readPage(listOf("--read-page=0")))
    }

    @Test
    fun 登记在表里而不是被念成拼错() {
        assertTrue("--read-page 没登记进 KNOWN（会被 unknown 念成拼错）", ForensicFlags.KNOWN.contains("--read-page"))
        assertTrue("认得出的参数被念成不认识", ForensicFlags.unknown(listOf("--read-page=3")).isEmpty())
    }

    @Test
    fun 阅读载荷带上页号而只给页号时不造一屏() {
        val reading = ForensicFlags.read(listOf("--read=9/g"), startPage = 4)!!
        assertEquals(4, reading.startPage)
        assertEquals(
            "没种页号就是从第一页起（0 是一个真页号，不是\"未知\"）",
            0,
            ForensicFlags.read(listOf("--read=9/g"), startPage = null)!!.startPage,
        )
        assertNull("只给页号而没有 --read：不凭空造出阅读那一屏", ForensicFlags.read(listOf(), startPage = 4))
    }

    @Test
    fun 页号落空那一句只在真的落空时念() {
        val seed = ForensicFlags.read(listOf("--read=9/g"), startPage = 4)
        assertNull("两枚都给了、也合上了：没什么可念", ForensicFlags.readPageNote(4, seed))
        assertNull(ForensicFlags.readPageNote(null, null))
        // 给了页号而那一屏根本没进（没有 --read）：静默就是假开关的口径，必须出声
        assertTrue(
            "落空那一句要点名缺的是哪一枚",
            ForensicFlags.readPageNote(4, null)!!.contains("--read"),
        )
        assertNull(
            "页号本身读不出时那句由 readPage 自己念过，这里不重复",
            ForensicFlags.readPageNote(null, ForensicFlags.read(listOf("--read=9/g"), startPage = null)),
        )
    }

    @Test
    fun 种子到进程那一跳也在() {
        // 同一颗纪律第九次：判读层与消费点接好了也不算通 —— 构建脚本漏了这一跳时
        // `-PreadPage=3` 就是"不报错也不做事"，而"页号没生效"会被读成"那一屏根本没滚"。
        val gradle = java.io.File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        assertTrue("`app` 任务没读 gradleProperty(\"readPage\")", gradle.contains("""gradleProperty("readPage")"""))
        assertTrue("`--read-page` 没被拼进 args（值传不下去）", gradle.contains("\"--read-page=\$it\""))
        assertTrue("对照组失效：这份构建脚本里没有 :desktop:app 那颗粒任务", gradle.contains("""tasks.register<JavaExec>("app")"""))
    }

    @Test
    fun 集成方两枚种子按次序合起来() {
        val main = DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"))
        val pageAt = main.indexOfFirst { it.contains("ForensicFlags.readPage(") }
        val readAt = main.indexOfFirst { it.contains("ForensicFlags.read(") }
        assertTrue("集成方没读 --read-page", pageAt >= 0)
        assertTrue("集成方没把页号交给阅读那颗种子", readAt >= 0)
        assertTrue("次序反了：先判页号再判那一屏（读空时上面两条都成立而次序看不见）", pageAt < readAt)
        assertTrue(
            "页号没真交给那颗判读（这里钉的是把值传下去那一步，不是两个标识符各自在场）",
            main.any { it.contains("ForensicFlags.read(args.asList(), readPageSeed)") },
        )
        assertTrue("落空那一句被省了", main.any { it.contains("ForensicFlags.readPageNote(") })
    }
}
