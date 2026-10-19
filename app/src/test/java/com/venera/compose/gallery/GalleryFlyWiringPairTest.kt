package com.venera.compose.gallery

import com.venera.compose.testsupport.RepoSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 飞行接线的两把源码尺（批 A，2026-10-19；计划见
 * `docs/rounds/gallery-pixiv-hero-vs-pixez-2026-10-19.md` 批 A 判据 ③④）。
 *
 * ## 为什么要源码尺而不是运行时判据
 *
 * 半接线的表现是**静默的**：有落点登记却没有去程截图的宿主，点进去走的是
 * "整页从下沿抬上来"那条退路 —— 屏上看不出"缺了什么"，单测也没有运行时
 * （本仓无 Robolectric，见 `GalleryExitFlyBodyTest` 头注）。所以只在源码层面钉。
 *
 * ## 两把尺
 *
 * 1. **接线成对尺**：有落点登记（`noteCardBounds`）的宿主必须有去程截图
 *    （`GalleryFlyIn.capture`），且登记与注销（`forgetCardBounds`）在文件内成对 ——
 *    Lazy 回收后旧矩形滞留，返回就会飞向一块已经不在屏上的卡。
 * 2. **键尺**：落点表的登记、注销、查询三侧只许念 `viewerKey`。
 *    从前主墙念 `uid`、查询侧念 `viewerKey`，靠"列表腿两字段碰巧相等"成立 ——
 *    那是一条脆接线，谁先改一边都不会有编译器或运行时报错。
 */
class GalleryFlyWiringPairTest {

    private val uiDir = "com/venera/compose/gallery/ui"

    /** 注释行不参与扫描：注释里提及 API 不算接线，写错也不算违例。 */
    private fun isCode(line: String): Boolean {
        val t = line.trim()
        return !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*")
    }

    /**
     * 接线成对尺：每个登记落点的宿主，都必须既有去程截图、又有同文件的注销。
     *
     * 宿主清单以扫描结果为准（新加一面墙 = 自动入尺），但至少要有这五面：
     * 主墙（GalleryScreen）、首页预览行（GalleryHomeSections）、历史（GalleryHistoryScreen）、
     * pixiv 首页（GalleryPixivHome）、作品页内（PixivWorkScreen）——
     * 少一面多半是有人把登记删了，先查清是不是刻意的再改这颗测试。
     */
    @Test
    fun `有落点登记的宿主必须有去程截图且登记注销成对`() {
        val hosts = RepoSources.kotlinFiles(uiDir)
            .map { it to it.readText().replace("\r\n", "\n") }
            .filter { (_, text) ->
                text.lines().any { isCode(it) && it.contains("GalleryFlyIn.noteCardBounds(") }
            }
        assertTrue("一个登记宿主都没扫到，量具失效", hosts.isNotEmpty())
        assertTrue(
            "宿主应至少五面（主墙/首页预览行/历史/pixiv 首页/作品页内），现读：" +
                hosts.joinToString { it.first.name },
            hosts.size >= 5,
        )
        for ((file, text) in hosts) {
            val lines = text.lines()
            assertTrue(
                "${file.name} 有落点登记（noteCardBounds）却没有 GalleryFlyIn.capture( —— " +
                    "有落点无截图 = 半接线，去程必走整页抬升退路",
                lines.any { isCode(it) && it.contains("GalleryFlyIn.capture(") },
            )
            val note = lines.count { isCode(it) && it.contains("GalleryFlyIn.noteCardBounds(") }
            val forget = lines.count { isCode(it) && it.contains("GalleryFlyIn.forgetCardBounds(") }
            assertEquals(
                "${file.name} 登记与注销不成对（note=$note forget=$forget）—— " +
                    "Lazy 回收后旧矩形滞留，返回会飞向一块已经不在屏上的卡",
                note,
                forget,
            )
        }
    }

    /**
     * 键尺：落点表的登记、注销、查询三侧只许念 `viewerKey`。
     *
     * 负向扫描列全违例（不许只报第一处），注释行除外。
     * `GalleryFlyIn.kt` 自身的形参声明不带 `GalleryFlyIn.` 前缀，天然不在扫描面里。
     *
     * 唯一放行的别名是 `entryKey`（作品页进场落点键的局部名）：放行的前提是它的
     * **每一处定义**都由 `viewerKey` 派生（下面第二段断言）—— 派生断了这一尺当场红，
     * 而不是把一个不相干的变量悄悄放进来。
     */
    @Test
    fun `落点表登记注销查询三侧只许念 viewerKey`() {
        // 别名派生只在**落点表的使用面**（gallery/ui）里查：别的域（漫画那侧的
        // ComicDetailViewModel）也有自己的 entryKey 局部量，与落点表无关，不在这一尺的辖区。
        val entryKeyDefs = RepoSources.kotlinFiles(uiDir).flatMap { f ->
            f.readText().replace("\r\n", "\n").lines().mapIndexed { i, l -> Triple(f.name, i + 1, l) }
        }.filter { (_, _, line) -> isCode(line) && line.contains("val entryKey") }
        assertTrue(
            "entryKey 这个别名没有定义（量具失效或它被改名了 —— 两把断言要一起改）",
            entryKeyDefs.isNotEmpty(),
        )
        val badDefs = entryKeyDefs.filter { (_, _, line) -> !line.contains("viewerKey") }
        assertTrue(
            "entryKey 必须由 viewerKey 派生（它是落点表键的别名，不是第二种键），违例：\n" +
                badDefs.joinToString("\n") { (rel, no, l) -> "$rel:$no: ${l.trim()}" },
            badDefs.isEmpty(),
        )

        fun compliant(t: String) = t.contains("viewerKey") || t.contains("entryKey")
        val offenders = RepoSources.allMainLines().filter { (_, _, line) ->
            val t = line.trim()
            isCode(line) && (
                t.contains("GalleryFlyIn.noteCardBounds(") ||
                    t.contains("GalleryFlyIn.forgetCardBounds(") ||
                    t.contains("GalleryFlyIn.cardBoundsOf(")
                ) && !compliant(t)
        }
        assertTrue(
            "落点表的键必须统一 viewerKey（uid 那条是靠'列表腿两字段碰巧相等'成立的脆接线，" +
                "登记与查询各念各的键的表现是静默不飞），违例：\n" +
                offenders.joinToString("\n") { (rel, no, l) -> "$rel:$no: ${l.trim()}" },
            offenders.isEmpty(),
        )
    }
}
