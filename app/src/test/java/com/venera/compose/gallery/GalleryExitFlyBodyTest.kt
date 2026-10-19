package com.venera.compose.gallery

import com.venera.compose.gallery.domain.FlyRect
import com.venera.compose.gallery.domain.exitExpansion
import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 飞行体共享件的那把尺子（2026-10-19 批 A）。
 *
 * ## 它补的是哪句空话
 *
 * `GalleryExitFlyBody.kt` 的头注从前写着"两边同形由 `GalleryExitFlyBodyTest` 钉着 —— 一处漂了
 * 当场红"，而这颗测试**全仓不存在**（计划文档 §2.1 现量过）。也就是说共享件抽了一半、
 * 钉它不漂的那把尺子是一句空话。本文件就是那句话的兑现。
 *
 * ## 守四条
 *
 * 1. **渲染只许住在这两颗件里**：gallery/ui 目录（含子目录）下 `drawImage(` 恰好 2 处
 *    （进场 1 + 离场 1），且各带一个 `addRoundRect(`。多一处 = 又有人就地抄了一遍飞行体，
 *    而那种抄本的漂移只有真机能看见（同一部作品从两面墙点进去飞得不一样）。
 * 2. **几何只有一个真相**：两颗件都必须吃判据层那同一颗 [exitExpansion]，不许在自己身上写
 *    `(to.left - from.left)` 这类就地插值。它方向无关（进场件调它时方向相反），
 *    这正是它能被两趟共用的原因。
 * 3. **形参形状不许回退**：调用点实参里不许出现 `.value`。形参收 `() -> Float` 的全部理由见
 *    `GalleryExitFlyBody` 的 `@param progress`：动画值在 `drawBehind` 里每帧现取，只重绘这一块；
 *    当 `Float` 实参递就是把动画值读回组合期，每帧重组这一层。
 * 4. **`Rect` → `FlyRect` 只此一份**：两份转换就是两份真相。
 *
 * 没有 Robolectric / compose-ui-test，所以 1、2、3、4 都是源码形状尺；第 5 条是纯函数层，
 * 钉"同一进度同一对矩形下，两趟共用的那颗判据给出的 dst/src"本身自洽（可共用的前提）。
 */
class GalleryExitFlyBodyTest {

    private val uiDir = "com/venera/compose/gallery/ui"
    private val entryFile = "GalleryEntryFlyBody.kt"
    private val exitFile = "GalleryExitFlyBody.kt"
    /** 批 B① 的活画面件：与墙卡同 URL，几何仍走判据层那一颗。 */
    private val liveFile = "GalleryLiveFlyBody.kt"

    /** 只保留**代码行**（注释里写着同样的符号名，不滤就等于判据读我们自己的解释）。 */
    private fun codeLines(rel: String): List<String> {
        val f = File(RepoSources.appMain, rel)
        assertTrue("找不到 $rel，量具失效", f.isFile)
        return f.readText().replace("\r\n", "\n").lines().filter { line ->
            val t = line.trimStart()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }
    }

    private fun codeLinesOfDir(dir: String): List<Triple<String, Int, String>> =
        RepoSources.linesOf(dir).filter { (_, _, line) ->
            val t = line.trimStart()
            !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"))
        }

    /**
     * 飞行体渲染只许住在这三颗件里：`drawImage(` 恰好三处，各带一个 `addRoundRect(`。
     *
     * 批 B①（2026-10-19）从两颗变三颗：多出来的是**活画面件** `GalleryLiveFlyBody` ——
     * 它与墙卡同 URL，飞的仍是同一颗判据层（[flightFrame]）算出来的几何，所以它是合法的第三处。
     * 这把尺防的仍然是"有人就地抄一遍飞行体"：多出第四处就是抄本，
     * 而抄本的漂移只有真机能看见（同一部作品从两面墙点进去飞得不一样）。
     */
    @Test
    fun `飞行体渲染只许住在这三颗件里`() {
        val hits = codeLinesOfDir(uiDir).filter { (_, _, line) -> line.contains("drawImage(") }
        assertEquals(
            "gallery/ui 下 drawImage( 只许三处（进场共享件 + 离场共享件 + 活画面件）—— " +
                "多出第四处就是又有人就地抄了一遍飞行体：\n" +
                hits.joinToString("\n") { "${it.first}:${it.second} ${it.third.trim()}" },
            3,
            hits.size,
        )
        assertEquals(
            "三处必须各自落在那三颗共享件里",
            setOf(entryFile, exitFile, liveFile),
            hits.map { it.first.substringAfterLast('/') }.toSet(),
        )
        for (name in listOf(entryFile, exitFile, liveFile)) {
            val rects = codeLines("$uiDir/$name").count { it.contains("addRoundRect(") }
            assertEquals("$name 只许有一个 addRoundRect(（一个圆角矩形一次画完）", 1, rects)
        }
    }

    /**
     * 活画面件**不许自己写几何**：那一颗的 `drawImage(` 附近必须出现判据层那一颗。
     *
     * 这是三处 `drawImage(` 能共存的唯一理由：三颗件画的是不同的内容（截图 / 截图 / 活画面），
     * 但几何只住一份。就地写 `(to.left - from.left)` 这类插值，就是第二份真相的开头。
     */
    @Test
    fun `活画面件吃判据层不自己写几何`() {
        val body = codeLines("$uiDir/$liveFile")
        assertTrue(
            "活画面件要走判据层 flightFrame（同两颗截图件）",
            body.any { it.contains("flightFrame(") },
        )
        assertTrue(
            "活画面件不许就地插值（几何只住一份）",
            body.none { it.contains("(to.left - from.left)") || it.contains("(from.left + (to.left - from.left)") },
        )
        // 形参形状：动画值走 lambda 不传值（同两颗截图件，理由见 `GalleryExitFlyBody` 头注）。
        assertTrue(
            "progress 必须是 lambda（动画值每帧现取，只重绘这一层）",
            body.any { it.contains("progress: () -> Float") },
        )
    }

    /** 去程全程不淡那条口径：进场件的 alpha 是形参，不带随进度衰减的默认值。 */
    @Test
    fun `进场件不把淡出写进默认值`() {
        val body = codeLines("$uiDir/$entryFile")
        assertTrue(
            "进场件不许出现 alpha = bodyAlpha * (1f - t) 那条随进度淡出的支（2026-10-01 重影 + 0.75 暗一下）",
            body.none { it.contains("alpha = bodyAlpha * (1f - t)") },
        )
        assertTrue("进场件的 alpha 形参默认必须是整程不淡", File(RepoSources.appMain, "$uiDir/$entryFile").readText().contains("alpha: () -> Float = { 1f }"))
    }

    /** 几何只有一个真相：两颗件都吃 [exitExpansion]，不就地写矩形插值。 */
    @Test
    fun `两颗件共用同一颗几何判据`() {
        for (name in listOf(entryFile, exitFile)) {
            val body = codeLines("$uiDir/$name")
            val calls = body.count { it.contains("exitExpansion(") }
            assertEquals("$name 只许调一次 exitExpansion", 1, calls)
            val inline = body.filter { it.contains("(to.left - from.left)") || it.contains("(to.top - from.top)") }
            assertTrue("$name 不许自己写矩形插值（几何只许住判据层）：$inline", inline.isEmpty())
        }
    }

    /**
     * 同一进度、同一对矩形下，两趟共用的那颗判据给出同一份 dst/src。
     *
     * 这不是在证"正确"（正确由 `GalleryExitExpansionTest` 逐位钉着），是在证**可共用**：
     * 进场件与离场件传的是同一对矩形、方向相反，若判据对方向敏感，两颗件就不能共享它。
     */
    @Test
    fun `同一进度下进出两趟同源`() {
        val from = FlyRect(left = 32f, top = 1103f, width = 496f, height = 276f)
        val to = FlyRect(left = 0f, top = 200f, width = 1080f, height = 1386f)
        for (p in listOf(0f, 0.13f, 0.5f, 0.87f, 1f)) {
            val forward = exitExpansion(p, from, to, 1080, 1386)
            val backward = exitExpansion(p, to, from, 1080, 1386)
            assertEquals("进度 $p 的 dst.left 应当是逐边插值", from.left + (to.left - from.left) * p, forward.dst.left, 0f)
            // 方向对调只是把同一对矩形的两端交换：源裁切按 cover 语义重算，宽度仍是同一档。
            assertEquals("进度 $p 的源宽两趟共用同一档", forward.source.width, backward.source.width)
        }
    }

    /**
     * 形参形状不许回退：**调用点**（宿主屏，不是件自身）的实参里不许出现**裸** `.value`。
     *
     * `.value` 出现在实参里 = 动画值在组合期被读了一次 ⇒ 每帧重组那一层。形参收
     * `() -> Float` 的全部理由见 `GalleryExitFlyBody` 的 `@param progress`。
     *
     * ⚠️ 必须先剥掉 `{ … }` 再找 `.value`：`progress = { fly.value }` 正是**我们要的形状**
     * （lambda 里读 = 绘制阶段读），不剥就会把正确写法一起罚掉。裸的那种是
     * `progress = fly.value` —— 剥完还在，那才罚。
     */
    @Test
    fun `调用点实参不许出现动画值的即时读取`() {
        val files = RepoSources.kotlinFiles(uiDir).filter { it.isFile }
        for (name in listOf(entryFile, exitFile)) {
            val callSite = name.removeSuffix(".kt")
            // 调用点都在宿主屏里（PixivWorkScreen / GalleryPostScreen），不在件自身的文件里
            val callLines = files.filter { it.name != name }.flatMap { f ->
                f.readText().replace("\r\n", "\n").lines().mapIndexed { i, line -> Triple(f, i, line) }
            }.filter { (_, _, line) ->
                line.contains("$callSite(") && !line.contains("fun $callSite(")
            }
            assertTrue(
                "$callSite( 一个调用点都没有 —— 这颗件没人用就等于白抽",
                callLines.isNotEmpty(),
            )
            for ((f, idx, _) in callLines) {
                val lines = f.readText().replace("\r\n", "\n").lines()
                val args = lines.subList(idx, minOf(idx + 18, lines.size)).joinToString("\n")
                // 剥掉所有 lambda 体（含跨行的）再找裸 .value；不嵌套的 lambda 一把就够
                val stripped = Regex("\\{[^{}]*}").replace(args, " ")
                assertTrue(
                    "${f.name} 里 $callSite( 的实参出现了裸 .value —— 形参收 lambda 的理由" +
                        "（绘制阶段每帧现取、不把整层拖进重组）被绕过去了：\n$stripped",
                    !stripped.contains(".value"),
                )
            }
        }
    }

    /** `Rect` → `FlyRect` 全仓只此一份：两份转换就是两份真相。 */
    @Test
    fun `Rect 翻 FlyRect 只此一份`() {
        val defs = codeLinesOfDir(uiDir).filter { (_, _, line) ->
            line.contains("fun Rect.toFlyRect(") ||
                line.contains("fun androidx.compose.ui.geometry.Rect.toFlyRect(")
        }
        assertEquals(
            "Rect → FlyRect 只许有一份定义（进出两颗件共用）：\n" +
                defs.joinToString("\n") { "${it.first}:${it.second} ${it.third.trim()}" },
            1,
            defs.size,
        )
        assertTrue("那一份必须住在离场件里（internal，进场件同包直接用）", defs[0].first.endsWith(exitFile))
    }
}
