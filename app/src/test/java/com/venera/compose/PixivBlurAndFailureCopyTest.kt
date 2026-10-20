package com.venera.compose

import com.venera.compose.testsupport.RepoSources
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **codex 那一轮的收尾尺**（2026-10-20）。
 *
 * 那一轮做了三件事，本文件各钉一把，免得下次有人把它们"顺手改回去"：
 *
 * 1. **pixiv 作品页彻底不挂窗口模糊** —— 用基类现成开关 `blurBehindEnabled = false`，
 *    而不是在屏里逐处 `clearFlags` / `setBackgroundBlurRadius`。两处各写一套就是两处真相，
 *    而且屏里那套只能"挂上再摘"（跳变），开关那套是从头到尾没有。
 * 2. **失败原话不摆到屏上** —— 站方偶尔回整页 HTML，把 `exception.message` 直接当 caption
 *    既读不懂、也会撑坏卡片高度。技术异常只进日志，屏上给一句人话。
 * 3. **caption 可以带一枚图标** —— `GalleryRowCaption` 的 `icon` 形参；不给图标时与旧版逐像素一致。
 */
class PixivBlurAndFailureCopyTest {

    private fun src(rel: String): String {
        val f = File(RepoSources.appMain, rel)
        assertTrue("找不到 $rel，量具失效", f.isFile)
        return f.readText().replace("\r\n", "\n")
    }

    private fun code(rel: String): List<String> = src(rel).lines().map { it.trim() }
        .filter { !it.startsWith("//") && !it.startsWith("*") && !it.startsWith("/*") }

    @Test
    fun `pixiv 作品页用基类开关关掉窗口模糊`() {
        val activity = code("com/venera/compose/PixivWorkActivity.kt")
        // 走基类现成开关：一行声明，与其它页同一套机制。
        assertEquals(
            "要 override 基类那颗开关",
            1,
            activity.count { it.contains("override val blurBehindEnabled: Boolean = false") },
        )
        // 两条旧机制必须退场：它们只能"挂上再摘"，而摘的那一刻就是用户读到的跳变。
        assertEquals(
            "推迟型那颗必须退场（不挂了就没有'何时挂'的问题）",
            0,
            activity.count { it.contains("override val deferredBlurBehind") },
        )
        assertEquals(
            "半径那一颗必须退场",
            0,
            activity.count { it.contains("override val blurBehindDp") },
        )
        // 屏里那套"挂上再摘"的件一并退场（两套口径共存时，读代码的人无法判断哪套在生效）。
        val screen = code("com/venera/compose/gallery/ui/PixivWorkScreen.kt")
        assertEquals("屏里不许再有 onEnterStart 形参", 0, screen.count { it.contains("onEnterStart: () -> Unit") })
        assertEquals("屏里不许再有挂模糊那颗", 0, screen.count { it.contains("fun clearChromeForExit()") })
        assertEquals("屏里不许再直接动窗口模糊半径", 0, screen.count { it.contains("setBackgroundBlurRadius(") })
    }

    @Test
    fun `站方失败原话不摆到屏上`() {
        val screen = code("com/venera/compose/gallery/ui/GalleryScreen.kt")
        // exceptionOrNull()?.message 直接当 caption = 把整页 HTML 摆到卡片上。
        assertEquals(
            "不许把异常 message 直接当 caption",
            0,
            screen.count { it.contains("pixivRankingCaption = result.exceptionOrNull()?.message") },
        )
        // 改为：异常进日志 + 屏上一句人话。
        assertTrue(
            "异常要进日志（不许静默吞掉）",
            screen.any { it.contains("""Log.w("VeneraDebug", "pixiv ranking failed""") },
        )
        assertEquals(
            "两条腿（登录 / 匿名）各给一句人话",
            2,
            screen.count { it.contains("排行榜暂时没加载出来，稍后再试。") },
        )
    }

    @Test
    fun `caption 带图标时不给图标与旧版一致`() {
        val sections = code("com/venera/compose/gallery/ui/GalleryHomeSections.kt")
        // 形参默认 null ⇒ 旧调用点一个都不用改，观感逐像素不变。
        assertEquals(
            "icon 形参必须有 null 默认值",
            1,
            sections.count { it.contains("private fun GalleryRowCaption(text: String, icon: ImageVector? = null)") },
        )
        // 画的时候要判空（不判就是每次都在排一枚空 Icon，行高会跳）。
        assertTrue("摆图标前要判空", sections.any { it.contains("if (icon != null)") })
    }
}
