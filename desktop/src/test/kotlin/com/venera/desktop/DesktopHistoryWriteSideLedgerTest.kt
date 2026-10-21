package com.venera.desktop

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 钉住「桌面上那张表，到底有没有人写」—— 架构评审 R1（本轮最重要的发现）。
 *
 * ## 这条缺口为什么最要紧
 *
 * 评审逐行核过：`comic_history` 与 `reading_stats` 在桌面侧**都没有写入者**
 * （`.saveHistory(` 零命中、`insertSession(`/`recordSession(` 零调用、
 * `desktopWriteReadingHistory(` 只有 import 没有调用）。
 *
 * 于是首页那两块（阅读统计、历史记录）**读口做得再对，真机上也是恒空屏**。
 * 而空屏长得像"你还没读过书"，不像"这个功能没接完" —— 最容易被当成正常。
 *
 * ## 为什么现在**不是**让它红
 *
 * 桌面此刻只剩 12 个 `@Composable`，**没有阅读器 UI**（平行线那条按用户
 * 2026-10-20 裁决删掉了）。没有翻页这一动作，"翻一页就记一行"就没有落点 ——
 * 所以这条**不是漏接，是结构性缺失**。硬造一个 `saveHistory` 调用点等于
 * 编一条不存在的行为，比缺着更坏。
 *
 * 所以这条测试做两件事：
 *   ① 如实记下"现在没人写"这个事实，**并在有人写的时候自动变红**要求更新本注释；
 *   ② 钉住写口本身是好的（`desktopWriteReadingHistory` 有权取消的语义），
 *      以及**将来阅读器 UI 一回来就必须接上**。
 *
 * 换句话说：这条尺现在守的是"账不许糊"，不是"功能已完成"。
 */
class DesktopHistoryWriteSideLedgerTest {

    private fun mainSources(): List<File> =
        File(DesktopSourceTree.repoRoot, "desktop/src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    /** 去掉注释行后的代码文本（只看真代码，不看 KDoc 里的举例）。 */
    private fun codeOf(files: List<File>): String = files.joinToString("\n") { f ->
        f.readText().lines()
            .filterNot { ln ->
                val s = ln.trim()
                s.startsWith("//") || s.startsWith("*") || s.startsWith("/*")
            }
            .joinToString("\n")
    }

    @Test
    fun `写侧现在没人调 这是结构性的 要有账`() {
        val code = codeOf(mainSources())

        val writes = Regex("""\bdesktopWriteReadingHistory\s*\(""").findAll(code)
            .count { m ->
                // 排除它自己的声明（`internal suspend fun desktopWriteReadingHistory(`）
                val before = code.substring(maxOf(0, m.range.first - 40), m.range.first)
                "fun " !in before
            }
        val saves = Regex("""\.saveHistory\s*\(""").findAll(code).count()
        val sessions = Regex("""\b(insertSession|recordSession)\s*\(""").findAll(code)
            .count { m ->
                val before = code.substring(maxOf(0, m.range.first - 40), m.range.first)
                "fun " !in before
            }

        // 有阅读器 UI 吗？（翻页那个动作的落点）
        //
        // ⚠️ 别用 `fun \w*Reader\w*\(` 这种宽正则：`ReaderProbe.kt` 里那个
        // `private fun ReaderProbe()` 是**探针**，不是阅读器，会把判据喂饱
        // （第一次写就踩了这个）。判据要落在"真能翻页的屏"上。
        val files = mainSources()
        val hasReaderUi = files
            .filter { it.name.startsWith("DesktopComicReader") }
            .any { "@Composable" in it.readText() } ||
            Regex("""@Composable[\s\S]{0,300}?fun\s+Desktop\w*Reader\w*\s*\(""")
                .containsMatchIn(files.joinToString("\n") { it.readText() })

        if (hasReaderUi) {
            // 阅读器 UI 回来了 ⇒ 写侧必须接上，否则首页两块恒空。
            assertTrue(
                "桌面已经有阅读器 UI 了，但**没有任何写入调用**：" +
                    "desktopWriteReadingHistory=$writes / saveHistory=$saves / session=$sessions。\n" +
                    "不接的话，首页的「阅读统计」与「历史记录」永远是空的，" +
                    "而空屏看起来像「你还没读过书」，不像「这个功能没接完」（架构评审 R1）。",
                writes + saves + sessions > 0,
            )
        } else {
            // 没有阅读器 UI ⇒ 写侧无处落地。**这不是通过，是记账**。
            assertTrue(
                "桌面既没有阅读器 UI、却又有写入调用 —— 那说明写口接到了不知什么地方，" +
                    "请复核本注释是否需要改成「已接线」。" +
                    "（writes=$writes saves=$saves sessions=$sessions）",
                writes + saves + sessions == 0,
            )
        }
    }

    @Test
    fun `写口本身要守住取消语义`() {
        val f = File(
            DesktopSourceTree.repoRoot,
            "desktop/src/main/kotlin/com/venera/desktop/gallery/data/DesktopComicHistory.kt",
        )
        assertTrue("读不到 DesktopComicHistory.kt —— 判据失效（不是通过）", f.isFile)
        val text = f.readText()
        assertTrue(
            "`desktopWriteReadingHistory` 必须显式重新抛 `CancellationException`。" +
                "否则取消会被 catch(Throwable) 吞成一条「写入失败」读数 —— " +
                "那是把「没干完」记成「干失败了」，用户会看到一条根本不存在的错误。",
            "catch (canceled: CancellationException)" in text &&
                text.contains("throw canceled"),
        )
        assertTrue(
            "写口必须 `withContext(NonCancellable + Dispatchers.IO)`：写到一半被取消，" +
                "会在库里留半行",
            "NonCancellable" in text,
        )
    }
}
