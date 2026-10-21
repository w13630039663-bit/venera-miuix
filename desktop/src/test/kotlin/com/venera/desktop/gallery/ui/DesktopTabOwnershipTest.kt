package com.venera.desktop.gallery.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 钉住「五个 tab 不许互相冒充」—— 2026-10-20 用户当场抓到的那一颗越界。
 *
 * ## 越界长什么样
 *
 * 桌面壳曾经把**画廊那面图墙**摆到 `HOME` 位置，`else` 分支也兜回它：
 *
 * ```kotlin
 * when (currentTab) {
 *     VeneraNavTab.HOME -> DesktopHomePane(layoutMode, cards)      // ← 画的是图片墙
 *     VeneraNavTab.GALLERY -> DesktopGalleryWallPane(...)          // ← 画的是图片墙
 *     VeneraNavTab.FAVORITES -> DesktopFavoritesPane(...)          // ← 还是图片墙
 *     else -> DesktopHomePane(layoutMode, cards)                   // ← 还是图片墙
 * }
 * ```
 *
 * 用户原话：「你这个首页怎么和安卓端的完全不一样？」—— 安卓首页是**漫画推荐页**
 * （`feature/HomeScreen.kt` 1124 行：阅读统计 / 可能你感兴趣 / 历史），
 * 图片墙属于 `GALLERY`（`gallery/ui/GalleryScreen.kt` 3453 行）。
 *
 * ## 这条钉的是什么
 *
 * 不是"首页必须长成漫画推荐页"（那要等业务门面接上），而是：
 * **除了 GALLERY 那一格，谁都不许渲染那面图墙**。没接的屏要走如实报的
 * `DesktopNotWiredPane`，不许拿别的屏顶上 —— 假装比空白更糟。
 *
 * ⚠️ 判据只扫**代码行**（非注释行）：这颗壳的 KDoc 里本来就要写那些被禁的形状
 * （"上一版曾把 `DesktopHomePane` 摆到 HOME"），含进注释就是把说明当违例。
 */
class DesktopTabOwnershipTest {

    private fun shellFile(): File {
        val candidates = listOf(
            File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/desktop/VeneraDesktopLargeScreen.kt"),
        )
        val found = candidates.firstOrNull { it.isFile }
        assertTrue(
            "读不到桌面壳文件，判据失效（不是通过）：${candidates.map { it.path }}",
            found != null,
        )
        return found!!
    }

    /**
     * 代码行（非注释）里的 `VeneraNavTab.X -> …` 分发分支。
     *
     * ⚠️ 只认**分发那一段**：`DesktopNotWiredPane` 里也有一句
     * `VeneraNavTab.GALLERY -> "画廊"`（把 tab 翻成屏名给人看），那是文案映射不是分发。
     * 第一版没区分，把那一句当成分支，判出"GALLERY 没挂图片墙"的假红。
     *
     * 区分办法：分发分支右侧一定是一个**调用**（`XxxPane(`），文案映射右侧是字符串字面量。
     */
    private fun dispatchBranches(): Map<String, String> {
        val out = mutableMapOf<String, String>()
        shellFile().readText().lines().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) return@forEach
            val m = Regex("""VeneraNavTab\.(\w+)\s*->\s*(.+)$""").find(line) ?: return@forEach
            val rhs = m.groupValues[2].trim()
            // 文案映射（右侧是字符串字面量）不算分发。
            if (rhs.startsWith("\"")) return@forEach
            out[m.groupValues[1]] = rhs
        }
        return out
    }

    @Test
    fun `五个 tab 各走各的分支 不许 else 兜回某一屏冒充`() {
        val branches = dispatchBranches()
        assertTrue("一个 VeneraNavTab 分支都没读到，判据失效（不是通过）：${shellFile().name}", branches.isNotEmpty())

        // HOME / FAVORITES / SEARCH / EXPLORE 都必须显式点名：
        // 落在 else 里就等于"谁都没给它写屏"，那正是上一版的形状。
        val mustBeExplicit = listOf("HOME", "FAVORITES", "SEARCH", "EXPLORE")
        val missing = mustBeExplicit.filter { it !in branches }
        assertTrue(
            "这些 tab 没有显式分支（掉进 else 就会被别的屏冒充）：$missing\n" +
                "实际分支：$branches",
            missing.isEmpty(),
        )
    }

    @Test
    fun `图片墙只许挂在 GALLERY 那一格`() {
        val branches = dispatchBranches()
        val wallOwners = branches.filterValues { it.contains("DesktopWallScaffold") || it.contains("GalleryWallPane") }
        val wrong = wallOwners.keys.filter { it != "GALLERY" }
        assertTrue(
            "图片墙被挂到了 GALLERY 以外的 tab 上：$wrong（$wallOwners）—— 那会让用户以为那就是他的首页",
            wrong.isEmpty(),
        )
        assertTrue(
            "GALLERY 那一格没挂图片墙：${branches["GALLERY"]}",
            wallOwners.containsKey("GALLERY"),
        )
    }

    @Test
    fun `没接的屏要走如实报的 DesktopNotWiredPane`() {
        val branches = dispatchBranches()
        val notWired = branches.filterValues { it.contains("DesktopNotWiredPane") }
        val wrong = notWired.keys - setOf("HOME", "FAVORITES", "SEARCH", "EXPLORE")
        assertTrue("DesktopNotWiredPane 只该给还没接的那四屏用，多出来的：$wrong", wrong.isEmpty())
    }
}
