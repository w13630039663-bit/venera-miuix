package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 两端 UI 的**双向**隔离尺（S2-B §2.5 用例 ③）。
 *
 * ## 为什么这是第二把尺子，而不是把 S1 那把改宽一点
 *
 * S1 落的 `DesktopNoAndroidImportTest`（住在 `:app` 的测试面）管的是"**共享面 + 桌面源不许点名 Android SDK**"，
 * 它被刻意放在 `:app` 侧是因为它要钉的失败形态之一是 `:desktop:compileKotlin` 炸了 —— 那次跑之后
 * `:desktop:test` 根本不会执行。两把尺子的分工：
 *
 * | | 管什么 | 在哪一侧跑 |
 * |---|---|---|
 * | `DesktopNoAndroidImportTest`（S1） | 桌面 / 共享源里出现 `android.*`（含不带 import 的内联 FQN） | `:app:testDebugUnitTest` |
 * | 本文件 | **跨端 UI 互引**：桌面不许连上 Android 的 UI，Android 也不许连上桌面的 UI | `:desktop:test` |
 *
 * 其中 `app/src/main → com.venera.desktop` 这一向 S1 那颗**没有**（它只扫桌面与共享源）。
 * 桌面上 UI 一旦 import 进 Android，`:desktop:compileKotlin` 不一定立刻红（那半边是 KGP 的 JVM 面），
 * 但"物理与逻辑分离"这条硬约束就已经破了 —— 破了而没人说，正是本批最忌的那类。
 *
 * ⚠️ 两端 UI 的那张表（`feature` / `gallery.ui` / `reader` / `components`）与 S1 那颗**故意同串**，
 * 也与 `BusinessApiBoundaryTest` 的 UI 口径同串：三份各管一头（那边管"UI 不许直连实现"，
 * 这边管"桌面不许连上 UI"），不许并成一把。
 */
class DesktopUiIsolationTest {

    @Test
    fun `桌面 main 源零 Android UI 引用 含不带 import 的内联全名`() {
        // 判式与 S1 那颗同串；这里扫**任何代码行**而不是只扫 import —— S1 的坑 2 就是"没有 import、
        // 却写了一句内联全限定名"，只按 import 扫会给出假空结果。
        assertNoMatch(DesktopSourceTree.desktopMainSources(), DESKTOP_FORBIDDEN, "桌面 → Android UI")
    }

    @Test
    fun `Android main 源零桌面引用`() {
        assertNoMatch(DesktopSourceTree.androidMainSources(), ANDROID_FORBIDDEN, "Android UI → 桌面")
    }

    @Test
    fun `桌面 UI 壳只经 GalleryPorts 形参拿数据 不在 UI 里取静态口`() {
        // 桌面那半边 UI 里出现 `GalleryPorts.of(...)` 就意味着它在找一个静态句柄：
        // A 线的接线件（DesktopGalleryPorts）在另一个 worktree 里，这种写法今天编不过、
        // 明天被接上之后会静默绑到一个"谁都能改"的全局 —— pane 只吃形参才是两颗计划能并行的前提。
        // 只扫代码行：这两颗文件的口径注释里**本来就该**出现那个被禁的写法（"不在 UI 里调 …"），
        // 含进注释就是把说明当成违例 —— 首跑正是这么红的两次，读数在案。
        val offenders = desktopUiSources().flatMap { codeLineHits(it, "GalleryPorts.of(") }
        assertTrue("桌面 UI 里出现了 GalleryPorts.of(...)：$offenders", offenders.isEmpty())
    }

    private fun desktopUiSources(): List<java.io.File> =
        DesktopSourceTree.desktopMainSources().filter { it.parentFile?.name == "ui" && it.parentFile?.parentFile?.name == "gallery" }

    /** 某个串在这颗文件的**代码行**（非注释行）里的命中，形如 `文件名:行号`。 */
    private fun codeLineHits(file: java.io.File, token: String): List<String> =
        file.readText().lines().mapIndexedNotNull { index, line ->
            if (!line.isCommentLine() && line.contains(token)) "${file.name}:${index + 1}" else null
        }

    private fun assertNoMatch(files: List<java.io.File>, pattern: Regex, direction: String) {
        val findings = files.flatMap { file ->
            val relative = file.absolutePath.removePrefix(DesktopSourceTree.repoRoot.absolutePath).replace('\\', '/')
            file.readText().lines().mapIndexedNotNull { index, line ->
                if (!line.isCommentLine() && pattern.containsMatchIn(line)) {
                    "$relative:${index + 1}: ${line.trim().take(120)}"
                } else {
                    null
                }
            }
        }
        assertTrue(
            "$direction 破了：命中 ${findings.size} 处（判式 $pattern）\n" + findings.joinToString("\n").take(2000),
            findings.isEmpty(),
        )
    }

    /**
     * 注释行判式认三种起法：双斜杠、块注释正文那个星号，以及整行写完的块注释。
     * 后两种不是想象：S1 在 `DesktopNoAndroidImportTest` 里就量到过一处"整行写完的文档注释"
     * 被当成越界；这里同一颗坑不重踩（那两个字符连写用索引比较，免得在本文件的 KDoc 里造出真 token）。
     */
    private fun String.isCommentLine(): Boolean {
        val t = trimStart()
        return t.startsWith("//") || t.startsWith("*") || (t.length > 1 && t[0] == '/' && t[1] == '*')
    }

    private companion object {
        val DESKTOP_FORBIDDEN = Regex("""com\.venera\.compose\.(feature|gallery\.ui|reader|components|MainActivity)\b""")
        val ANDROID_FORBIDDEN = Regex("""\bcom\.venera\.desktop\b""")
    }
}
