package com.venera.desktop.gallery.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 钉住「取数路真的走了守卫」—— 安全评审 B 的**接线那一半**。
 *
 * ## 为什么「判据对了」不等于「修好了」
 *
 * [DesktopWallMaskTest] 证明 `DesktopGalleryWallPolicy.of` 本身是对的（四个档位分支都打到了）。
 * 但如果取数路**不调它**，墙上照样一张图都不遮 —— 这正是修复前的状态：
 * policy 零调用点，`desktopWallCardOf` 的 `masked` 吃默认 `false`。
 *
 * 架构评审给过同类提醒：`ProvideDesktopUxPlatformServices` 就是"实现写好了、没人用"的实例。
 * 所以"接口改完"这一步必须单独有尺。
 *
 * ## 这条尺钉三件事
 *
 * ① `runFirstPage` 里出现 `DesktopGalleryWallPolicy.of(`；
 * ② 走 policy 之后，`masked` 是**从 `DesktopWallCard` 搬过来的**（`masked = it.masked`），
 *    不是又写死一个 `false`；
 * ③ 缺省 `guard == null` 那条分支**必须打印读数**，不许静默（静默会让后来的人
 *    以为这一屏判过了）。
 */
class DesktopWallPolicyWiredTest {

    private val file: File
        get() {
            val f = File(
                DesktopSourceTree.repoRoot,
                "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopWallFirstPage.kt",
            )
            assertTrue("读不到 DesktopWallFirstPage.kt —— 判据失效（不是通过）", f.isFile)
            return f
        }

    private fun code(): List<String> = file.readText().lines().filterNot { ln ->
        val s = ln.trim()
        s.startsWith("//") || s.startsWith("*") || s.startsWith("/*")
    }

    @Test
    fun `取数路必须真的调 policy 否则墙上等于没判`() {
        val lines = code()
        assertTrue(
            "`DesktopWallFirstPage` 里没有 `DesktopGalleryWallPolicy.of(` —— " +
                "那墙上就回到「masked 吃默认 false」的老路：无论档位如何，一张图都不遮。\n" +
                "这正是安全评审 B 的原始缺口。",
            lines.any { "DesktopGalleryWallPolicy.of(" in it },
        )
    }

    @Test
    fun `masked 必须从 policy 搬过来 不许在取数层另写一个`() {
        val text = code().joinToString("\n")
        assertTrue(
            "看不到 `masked = it.masked` —— masked 必须从 `DesktopWallCard` **搬**过来，" +
                "与 `DesktopWallCardModel.kt` 类注释那句「这里只搬」一致。\n" +
                "如果在取数层另算一遍，两处判据一定会漂。",
            "masked = it.masked" in text,
        )
        // 禁止在走 policy 之后还出现硬编码 false
        val afterPolicy = text.substringAfter("DesktopGalleryWallPolicy.of(")
        assertTrue(
            "走 policy 之后又出现了 `masked = false` —— 那把判据结果丢掉了",
            "masked = false" !in afterPolicy,
        )
    }

    @Test
    fun `没给 guard 时必须打印读数 不许静默放行`() {
        val text = code().joinToString("\n")
        assertTrue(
            "`guard == null` 的分支里没有 println —— 静默放行会让日志看起来像「判过了」。\n" +
                "缺省不判是允许的（有些调用点只要取数），但**必须留下证据**。",
            "D_守卫 未判" in text,
        )
        assertTrue(
            "走 policy 那条路没有读数 —— 判了多少、遮了多少、藏了多少，出问题时得能查",
            "D_守卫 判了" in text,
        )
    }

    @Test
    fun `读数里的按规则挡不许用减法算 那是假读数`() {
        // 实测一次起窗：175 张一张没遮、一张没藏，读数却报「按规则挡=175」——
        // 因为原式子 `cards.size - 遮 - 藏` 把「既没遮也没藏」全归成了「按规则挡」。
        // 假读数比没有读数更坏：查问题的人会以为真有 175 张被规则挡掉。
        // 真值只有 `blockedRules`（规则集合，不是张数）。
        val text = code().joinToString("\n")
        val hasFake = Regex("""按规则挡\s*=\s*\$\{""").containsMatchIn(text)
        assertTrue(
            "读数里又出现了算式形式的「按规则挡」—— 那个数只能从 " +
                "`blockedRules` 读，不许用减法算（算出来的把未遮未藏的全算进去了）。",
            !hasFake,
        )
        assertTrue(
            "读数里没有念 `blockedRules` —— 命中规则数出问题时没法查",
            "blockedRules" in text,
        )
    }
}
