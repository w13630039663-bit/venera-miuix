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
        assertEquals("点名缺席那一区应收成三枚（批 46 起四枚，批 52 动图、批 61 backdrop 各搬走一枚，批 108 给「画成水彩」补点名的一枚）", 3, spec.notOnScreen.size)
    }

    /** ④ 设置屏真调那一枚写口（写了 setter 而界面不调 = 第二枚假开关）。 */
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
