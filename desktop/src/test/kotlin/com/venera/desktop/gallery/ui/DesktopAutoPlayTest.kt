package com.venera.desktop.gallery.ui

import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.platform.FakePaths
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第四十六批 批 46 · 「大图页自动连播间隔」那一枚从"点名缺席"改成**真开关**。
 *
 * 那颗偏好在桌面今天住在 `DesktopGallerySettingsSpec.notOnScreen` 里，屏上那句写的是
 * 「桌面浮层已经能翻页，还差『到点自己翻』这一步」—— 而 spec 自己的技术账就写着 **接得住**。
 * 判据本体（什么时候该翻、什么时候不许抢操作）住在共享的 `GalleryAutoPlay.shouldAdvance`
 * （`gallery/domain/GalleryViewerPolicies.kt`，桌面编译面内），桌面**转交而不重述**：
 * 连播不许在弹层开着时翻、不许替人决定视频何时起播、不许在最后一张空转 —— 那三条都是对岸量出来的。
 *
 * ⑤ 颗各钉一层，缺一层就是假开关：判据转交（①）、写口真跑（②）、那本账改了（③）、
 * 屏上真调写口（④）、档位不超出对岸区间（⑤）。
 */
class DesktopAutoPlayTest {

    private fun tmp(tag: String) = File(System.getProperty("java.io.tmpdir"), "r1f-ap-$tag-${System.nanoTime()}")

    /** ① 连播判据**转交**共享那颗，桌面不重述（两份判据会漂，漂出来的形状是"正在看的那张被翻走"）。 */
    @Test
    fun 连播判据转交共享那颗而桌面不重述() {
        // 落点在**集成方**而不是浮层：浮层按规矩只吃形参，而这里 `step` 与偏好都已经在手
        // （批 45 记过：往浮层加一枚形参要铺三处调用点，铺不出新事实）
        val overlay = DesktopSourceTree.desktopSourceOf("gallery/ui/VeneraDesktopApp.kt")
        val code = DesktopSourceTree.codeText(overlay)
        assertEquals(
            "浮层里 `GalleryAutoPlay.shouldAdvance(` 必须恰好一处（两处 = 两条各自计时）",
            1,
            DesktopSourceTree.codeOccurrences(overlay, "GalleryAutoPlay.shouldAdvance("),
        )
        // 实参表里四枚读数必须在（写法无关：命名实参与位置实参都算过）
        val call = code.substringAfter("GalleryAutoPlay.shouldAdvance(").substringBefore(")")
        listOf(
            "autoPlaySec" to "间隔没递进去 = 开关拨了而连播按默认档跑",
            "selection.index" to "当前序号没递进去 = 判据无法知道这是不是最后一张",
            "selection.size" to "张数没递进去 = 会在最后一张上空转",
            "isVideo" to "视频那一档没递进去 = 连播会替人决定何时起播、何时出声",
        ).forEach { (token, why) ->
            assertTrue("$why（那一处实参表里没有「$token」：$call）", call.contains(token))
        }
        // 桌面不许自己再写一遍"什么时候该翻"
        // 桌面不许自己再写一遍「什么时候该翻」那一道闸。
        // ⚠️ 判式加宽过一次，因为扰动真骗过了窄的那版：把共享调用塞进 `if (false && …)` 的死支、
        // 另写一道 `autoPlaySec <= 0`，**只数调用次数与实参 token 的尺子全绿**（坑 ㉗ 的近亲：锚点替死支作保）。
        val restatedFiles = DesktopSourceTree.desktopMainSources().filter {
            val code = DesktopSourceTree.codeText(it)
            code.contains("seconds <= 0") || code.contains("autoPlaySec <= 0") || code.contains("if (false")
        }
        assertTrue(
            "桌面重述（或塞进死支）了连播那一道闸：" + restatedFiles.joinToString(" · ") { it.name },
            restatedFiles.isEmpty(),
        )
        // 到点之后真要做的那两件事必须在，而**顺序**是「先等再翻」
        val body = code.substringAfter("GalleryAutoPlay.shouldAdvance(")
        assertTrue("等那一档秒数那一笔不在（不 delay 就是一瞬翻完整批）", body.contains("delay(autoPlaySec * 1000L)"))
        assertTrue("翻那一步不在（只等不 step = 这一枚还是假开关）", body.contains("step(1)"))
        assertTrue(
            "顺序反了：先 step 再 delay 是「翻走之后才开始计时」那一形",
            body.indexOf("delay(autoPlaySec * 1000L)") < body.indexOf("step(1)"),
        )
    }

    /** ② 写口真跑：夹在对岸那条区间里、落盘、流里读得回（至少喂三种值）。 */
    @Test
    fun 写口夹在零到三十而落盘读回同一档() {
        val dir = tmp("write")
        val p = DesktopGalleryPreferences(FakePaths(dir))
        listOf(10 to 10, 999 to 30, -5 to 0, 0 to 0).forEach { (given, want) ->
            p.setGalleryAutoPlaySec(given)
            assertEquals("写进 $given 之后流里读到的不是 $want", want, p.galleryAutoPlaySec.value)
        }
        // 最后写一档非零再换一颗新实例：钉的是"真落盘"。
        // ⚠️ 这里不许用 0 做这一步：默认档就是 0，写不进盘也读得回 0，那条断言会恒真。
        p.setGalleryAutoPlaySec(20)
        assertEquals("盘上没留住那一档", 20, DesktopGalleryPreferences(FakePaths(dir)).galleryAutoPlaySec.value)
    }

    /** ③ 那本账改了：这一枚不再住在"点名缺席"那一区，而进了"屏上真能改"那一区。 */
    @Test
    fun 那一枚从点名缺席搬进屏上能改() {
        val spec = DesktopGallerySettingsSpec
        assertFalse(
            "还挂在缺席那一区：屏上会念一句「还差到点自己翻」而这一档其实能改",
            spec.notOnScreen.any { it.pref == "galleryAutoPlaySec" },
        )
        assertTrue(
            "屏上能改那一区里没有这一枚",
            spec.editable.any { it.pref == "galleryAutoPlaySec" },
        )
        assertEquals("点名缺席那一区应收成两枚（批 46 起四枚，批 52 动图、批 61 backdrop 各搬走一枚）", 2, spec.notOnScreen.size)
    }

    /** ④ 设置屏真调那一枚写口（写了 setter 而界面不调 = 第二枚假开关）。 */
    @Test
    fun 设置屏真调那一枚写口() {
        val pane = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopGallerySettingsPane.kt")
        val code = DesktopSourceTree.codeText(pane)
        assertTrue(
            "设置屏没调 `setGalleryAutoPlaySec(` ⇒ 那一档在屏上改不了",
            code.contains("setGalleryAutoPlaySec("),
        )
        assertEquals(
            "读那一档的 `collectAsState` 必须恰好一处（两处 = 两行各读各的）",
            1,
            DesktopSourceTree.codeOccurrences(pane, "galleryAutoPlaySec.collectAsState()"),
        )
    }

    /** ⑤ 档位全在对岸那条区间内，而 0 那一档就是"关"。 */
    @Test
    fun 档位不超出对岸那条区间而零档是关() {
        val tiers = DesktopGallerySettingsSpec.autoPlayTiers
        assertTrue("档位表是空的：那一行会摆出一排没有内容的按钮", tiers.isNotEmpty())
        assertTrue("有档位越过了对岸滑条的上界 30：$tiers", tiers.all { it in 0..30 })
        assertTrue("缺了 0 那一档 = 关不掉连播", tiers.contains(0))
        assertEquals("档位不许重复（两枚同名按钮会摆在一排里）", tiers.size, tiers.distinct().size)
    }
}
