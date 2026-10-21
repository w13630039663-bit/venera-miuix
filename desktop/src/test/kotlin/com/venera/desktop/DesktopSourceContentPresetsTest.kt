package com.venera.desktop

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「源级预设表真的读到了内容」—— 安全评审 C 的**接线那一半**。
 *
 * ## 为什么必须有这条尺
 *
 * 本喵这一轮刚犯过一次同类错：把 `DesktopSourceContentPresets` 文件写好了，
 * 但**没接进判据**、也**没进 resources include 清单**，于是
 * `grep DesktopSourceContentPresets desktop/src/main` 只有定义自己一处 —— 等于没写。
 *
 * 安全评审给的同款提醒：`ProvideDesktopUxPlatformServices` 就是"实现写好、零调用"的实例。
 * 所以「文件存在」不等于「判据生效」。这条尺**真去解析那颗 JSON**，断言它读出了东西。
 *
 * ## 这条尺能抓到什么失效
 *
 * - resources include 清单漏了 `source_content_warning.json` ⇒ `getResourceAsStream` 给 null
 *   ⇒ `levels` 空表 ⇒ 下面第一条就红；
 * - JSON 形状变了（比如平铺改成嵌套）⇒ 解析出 0 条 ⇒ 也红；
 * - `isGuarded` 的 `mixed` 分支被删掉 ⇒ 第三条红。
 */
class DesktopSourceContentPresetsTest {

    @Test
    fun `预设表必须能真读到并解析出条目`() {
        val levels = DesktopSourceContentPresets.levels
        assertTrue(
            "预设表解析出 ${levels.size} 条 —— 空表意味着「这一道判据没生效」，" +
                "而它的成因是：${DesktopSourceContentPresets.lastError}。\n" +
                "最可能是 `desktop/build.gradle.kts` 的 resources include 清单漏了 " +
                "`source_content_warning.json`（那次它真的漏了）。",
            levels.size >= 10,
        )
        assertNull(
            "解析成功时 lastError 必须是 null，实际：${DesktopSourceContentPresets.lastError}",
            DesktopSourceContentPresets.lastError,
        )
    }

    @Test
    fun `nsfw 源必须命中 —— 这是历史卡打码的唯一依据`() {
        val levels = DesktopSourceContentPresets.levels
        // 这几个是表里明确标 nsfw 的，随便挑两个核；不钉具体名单（名单会变）
        val nsfwKeys = levels.filterValues { it == "nsfw" }.keys
        assertTrue(
            "表里一条 `nsfw` 都没有 —— 那 `isGuarded` 永远不会因源级预设命中。实际等级分布：" +
                levels.entries.groupingBy { it.value }.eachCount(),
            nsfwKeys.isNotEmpty(),
        )
        nsfwKeys.take(3).forEach { key ->
            assertTrue("源 `$key` 标了 nsfw，isGuarded 必须返回 true", DesktopSourceContentPresets.isGuarded(key))
        }
    }

    @Test
    fun `mixed 也必须命中 与安卓那句双竖线逐字一致`() {
        val mixedKeys = DesktopSourceContentPresets.levels.filterValues { it == "mixed" }.keys
        assertTrue(
            "表里没有 `mixed` 档 —— 无法验证这条判据。实际等级：" +
                DesktopSourceContentPresets.levels.values.toSet(),
            mixedKeys.isNotEmpty(),
        )
        mixedKeys.forEach { key ->
            assertTrue(
                "源 `$key` 标的是 `mixed`，必须也算命中。\n" +
                    "安卓 `ContentGuardManager.maskHit:441-443` 写的是 " +
                    "`if (level == \"nsfw\" || level == \"mixed\") return true` —— " +
                    "少了 `mixed` 那一半，e-hentai 这类站就漏判了。",
                DesktopSourceContentPresets.isGuarded(key),
            )
        }
    }

    @Test
    fun `safe 与未知源不许命中 —— 误遮是另一种伤害`() {
        val safeKeys = DesktopSourceContentPresets.levels.filterValues { it == "safe" }.keys
        safeKeys.take(3).forEach { key ->
            assertTrue(
                "源 `$key` 标的是 safe，不该因源级预设被判成人向。" +
                    "表自己的 `_comment` 写着「宁松勿严：多糊一张封面的代价远小于错糊普通作品」",
                !DesktopSourceContentPresets.isGuarded(key),
            )
        }
        assertTrue(
            "表里根本没有的源，不该命中（缺省 safe）",
            !DesktopSourceContentPresets.isGuarded("__不存在的源__"),
        )
    }

    @Test
    fun `判定链里真的用到预设表 而不只是定义了它`() {
        // 本喵这一轮踩过的坑：文件写好、零引用。所以这条必须查**调用点**。
        val f = java.io.File(
            DesktopSourceContentPresets::class.java
                .protectionDomain.codeSource.location.toURI()
        )
        assertNotNull(f)
        val ux = java.io.File(
            DesktopSourceTree.repoRoot,
            "desktop/src/main/kotlin/com/venera/desktop/DesktopUxPlatformServices.kt",
        )
        assertTrue("读不到 DesktopUxPlatformServices.kt —— 判据失效（不是通过）", ux.isFile)
        val code = ux.readText().lines().filterNot { ln ->
            val s = ln.trim()
            s.startsWith("//") || s.startsWith("*") || s.startsWith("/*")
        }.joinToString("\n")
        assertTrue(
            "`DesktopUxPlatformServices` 的代码里没有引用 `DesktopSourceContentPresets` —— " +
                "那预设表就是死件，历史卡仍然恒不遮（安全评审 C 的原缺口）。",
            "DesktopSourceContentPresets.isGuarded(" in code,
        )
        assertEquals(
            "`looksAdult` 应当同时吃 sourceKey 与 tags 两样（只吃 tags 就回到原缺口）",
            true,
            Regex("""fun looksAdult\(sourceKey: String, tags: List<String>\)""").containsMatchIn(code),
        )
    }
}
