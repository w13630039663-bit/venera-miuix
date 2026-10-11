package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 51 · 动图那一档的四道算式（设计 §3）。
 *
 * 它们必须是纯函数：播放器只负责"取字节、解码、推进帧号"，而**该不该动 / 隔多久 / 失败念哪句 /
 * 读数长什么样**这四问一旦散进播放器，就成了"只有真跑一遍才看得见"的东西 —— 本仓在预取与连播上
 * 各吃过一次（§76 / §75）。
 */
class DesktopGalleryViewerMotionTest {

    // ① 该不该逐帧：三档各过一遍。"关掉这一档"与"站方只给一帧"是两种不同的静帧，
    //    而偷懒的实现（只看 frameCount）会把前者做成"设置里那枚开关是假的"。
    @Test
    fun `逐帧要同时满足该动与多帧`() {
        assertTrue(desktopShouldAnimate(frameCount = 12, animateGifs = true))
        assertFalse("关了还在动 = 那枚开关是假开关", desktopShouldAnimate(frameCount = 12, animateGifs = false))
        assertFalse("单帧却起时钟 = 白烧一帧解码", desktopShouldAnimate(frameCount = 1, animateGifs = true))
    }

    // ② 帧间隔下界：站方给 0 或负数时不许把 CPU 打满。⚠️ 1ms 不是观感口径，是防自旋下界
    //    （本仓与 Coil 桌面侧都没有既有下限可抄 ⇒ 这里不造第二个数）。
    //    合法值必须原样过：夹到 1 之外再夹一道就会把 40ms 的慢帧抽成快帧。
    @Test
    fun `帧间隔只夹下界且合法值原样`() {
        assertEquals(1, desktopFrameDelayMs(0))
        assertEquals(1, desktopFrameDelayMs(-200))
        assertEquals(40, desktopFrameDelayMs(40))
        assertEquals(1, desktopFrameDelayMs(1))
    }

    // ③ 三种失败各一句，两两不许相同：「没取到 / 没有 / 没接」是三句话这一条在本仓立了几十批，
    //    而这一族最容易塌成一句"动图失败"——塌了就等于把"缓存键不中"读成"站方不给动图"。
    @Test
    fun `三种退化各念一句`() {
        val lines = DesktopAnimatedCause.entries.map { desktopAnimatedFallback(it) }
        assertEquals("三档要各一句", 3, lines.distinct().size)
        assertTrue("每句都得能上屏（不许空串）：" + lines, lines.all { it.isNotBlank() })
        assertNotEquals(lines[0], lines[1])
    }

    // ④ 读数把六枚都念出来：帧数、循环档、缓存命中与否、吃的哪一档、尺寸、每帧真实字节。
    //    少了"缓存=空/中"就量不到 §2 第 3 条那一格（openSnapshot 键不中是恒空，不是命中）；
    //    少了"每帧="就没人知道 1000×1000 那种动图该不该收上限（Android 侧炸过一次堆）。
    @Test
    fun `读数六枚都在`() {
        val line = desktopAnimatedReadout(
            frames = 24, repetition = 3, cacheHit = false, tier = DesktopDetailTier.SAMPLE,
            width = 1000, height = 1000, bytesPerPixel = 4,
        )
        assertTrue("少了帧数：$line", line.contains("24"))
        assertTrue("少了循环档：$line", line.contains("3"))
        assertTrue("少了缓存那一枚：$line", line.contains("缓存=空"))
        assertTrue("少了档位：$line", line.contains("SAMPLE"))
        assertTrue("少了尺寸：$line", line.contains("1000x1000"))
        // 每帧字节 = 宽高×位深（位深由调用方从真物件取，不是这里假设的 4）：
        // 1000×1000×4 ≈ 3.8MiB，所以念得出 MB 这一档。
        assertTrue("每帧字节没念出 MB 档：$line", Regex("每帧=[0-9.]+MB").containsMatchIn(line))
        // 小图要念成 KB 而不是 0.0MB —— 那一档一旦塌成 0，读数就再也量不到"堆该不该收"。
        val tiny = desktopAnimatedReadout(
            frames = 2, repetition = 0, cacheHit = true, tier = DesktopDetailTier.PREVIEW,
            width = 100, height = 100, bytesPerPixel = 4,
        )
        assertTrue("小图那档没念成 KB：$tiny", Regex("每帧=[0-9.]+KB").containsMatchIn(tiny))
    }

    // ⑤ 无限循环在 Skia 那边是 -1。直接把它念出去就是假读数：屏上明明在无限循环，
    //    读数却写"循环=-1"，读的人会判成"解错了"。⇒ 喂两种值，各念各的。
    @Test
    fun `无限循环不许念成负一`() {
        val forever = desktopAnimatedReadout(2, -1, true, DesktopDetailTier.PREVIEW, 8, 8, 4)
        assertTrue("无限要念成无限：$forever", forever.contains("无限"))
        assertFalse("不许把 -1 当循环次数念出去：$forever", forever.contains("-1"))
        val thrice = desktopAnimatedReadout(2, 3, true, DesktopDetailTier.PREVIEW, 8, 8, 4)
        assertTrue("有限档要念得出那个数：$thrice", thrice.contains("3"))
    }

    // ⑥ 判"该不该动"只许有一处调用共享那颗 `GalleryMotion.animates`，且全树不许重述那个 when。
    //    ⚠️ 判式**不能**按枚举成员名扫：`DesktopGalleryPreferences.kt:95` 那句
    //    `readEnum(KEY_ANIMATED, GalleryAnimatedMode.ALWAYS)` 是**合法默认档**而不是重述 ——
    //    第一版就红在那一行上（§0 坑 ①：禁串把合法代码扫红）。要禁的是"分支形状"本身。
    @Test
    fun `共享那颗判据只被调一次也没被重述`() {
        val sources = DesktopSourceTree.desktopMainSources()
        val calls = sources.sumOf { DesktopSourceTree.codeOccurrences(it, "GalleryMotion.animates(") }
        assertEquals("该动与否只许一处现算（多了就是有人重述了）", 1, calls)
        val restated = sources.filter {
            Regex("GalleryAnimatedMode\\.[A-Z_]+\\s*->").containsMatchIn(DesktopSourceTree.codeText(it))
        }
        assertTrue("有人在自己文件里重写那一族分支：" + restated.map { it.name }, restated.isEmpty())
    }

    // ⑦ 层已拍板"只在大图那一屏动"（2026-10-09）⇒ 墙上那张卡与浮层那颗件里不许出现解码器。
    //    这条是那一拍的唯一机械凭据：没有它，"只在大图"就只是一句说过的话。
    @Test
    fun `解码器不许出现在墙上与浮层`() {
        for (name in listOf("DesktopGalleryPostTile.kt", "DesktopGalleryDetailPanel.kt")) {
            val hits = DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource(name), "Codec")
            assertEquals("$name 里出现了 $hits 处 Codec", 0, hits)
        }
    }

    // ⑧ 退化必须**回落到 AsyncImage**，而不是把那一格撤空：静默白屏是这一族最坏的失败形状。
    @Test
    fun `大图那一屏留着回落那条路`() {
        val viewer = DesktopSourceTree.desktopUiSource("DesktopGalleryViewer.kt")
        assertEquals("AsyncImage 这一路要留着当兜底", 1, DesktopSourceTree.codeOccurrences(viewer, "AsyncImage("))
        assertTrue("要真吃播放器那颗件", DesktopSourceTree.codeOccurrences(viewer, "DesktopAnimatedImage(") == 1)
    }

    // ⑨ "像不像动图"只看地址，且**不许被 query 上的过期参数骗进去**：
    //    站方那一路地址常带 `?…` 尾巴，只看 endsWith 就会把静帧当动图起一趟解码。
    @Test
    fun `地址那一道门认扩展名而不认尾巴`() {
        assertTrue(desktopLooksAnimated("https://x/a/b.gif"))
        assertTrue(desktopLooksAnimated("https://x/a/b.GIF?token=1"))
        assertFalse("带 query 的静帧：不该进解码那趟", desktopLooksAnimated("https://x/a/b.jpg?token=gif"))
        assertFalse(desktopLooksAnimated("https://x/a/b.jpg"))
        assertFalse(desktopLooksAnimated(""))
    }

    // ⑩ 播放器一次只持**一帧**位图。这条是"不收帧数上限"那决定的唯一凭据（真机读数：
    //    `每帧=3.8MB`，1000×1000×70 帧那张）—— 帧数不进堆，只有尺寸进，所以 70 帧与 700 帧一样贵。
    //    哪天有人"顺手"预解全片或每帧新建一颗，这里红，而不是等屏上炸堆。
    //    ⚠️ 三把各管一种偷懒：数 alloc 抓"新加一处"，钉顺序抓"把那一处搬进循环"
    //    （同一处、每帧跑一遍，存在性判式对它全绿 —— §0 那条"钉顺序不钉存在"的旧账），
    //    数 `<ImageBitmap>` 抓"存成一整片"（`List` / `Array` / `ArrayList` 三种写法都撞这一枚字面）。
    @Test
    fun `播放器一次只持一帧位图`() {
        val player = DesktopSourceTree.desktopUiSource("DesktopAnimatedImage.kt")
        assertEquals("位图只许有一处 alloc（复用同一颗）", 1, DesktopSourceTree.codeOccurrences(player, "allocPixels"))
        assertEquals(
            "交上屏的位图每帧只新建一颗",
            // 带括号才数得是"调用"：`import …toComposeImageBitmap` 那一行也是代码行 ——
            // 第一版没带括号，这把尺子红在自己的 import 上（§0 坑 ① 那一族）。
            1, DesktopSourceTree.codeOccurrences(player, "toComposeImageBitmap()"),
        )
        assertEquals(
            "帧类型只许出现在那一枚单帧状态上（多一处就是有人把帧存起来了）",
            // 锚点写 `<ImageBitmap` 而不是 `<ImageBitmap>`：真形状是 `mutableStateOf<ImageBitmap?>`，
            // 带闭括号那把尺子数出 0 颗，红在拼写上而不是红在代码上。
            1, DesktopSourceTree.codeOccurrences(player, "<ImageBitmap"),
        )
        val lines = DesktopSourceTree.codeLinesOf(player)
        val alloc = lines.indexOfFirst { it.contains("allocPixels") }
        val loop = lines.indexOfFirst { it.contains("while (true)") }
        assertTrue("找不到那趟逐帧循环 ⇒ 这一颗判式要看的东西变了", loop >= 0)
        assertTrue("allocPixels 在循环里/之后 ⇒ 每帧新建一颗位图，`每帧=` 那行读数不够用", alloc in 0 until loop)
    }
}
