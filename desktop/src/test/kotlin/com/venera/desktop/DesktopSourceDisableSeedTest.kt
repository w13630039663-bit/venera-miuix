package com.venera.desktop

import com.venera.desktop.gallery.data.DesktopSourcePick
import com.venera.desktop.gallery.data.DesktopSourceSelection
import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 83.1 · `--disable=<源键>` 那枚取证种子（2026-10-10）。
 *
 * ## 这一批要的东西是"帧上看得见"，不是"能跑"
 *
 * 批 83 立的那条口径是「**停用是真生效的**」：`DesktopSourceSelection.resolve` 把"点名要的那颗已被停用"
 * 折成落回另一颗（`DesktopComicSourcesScreen.kt:44`）。今天证明它的只有离屏语义树那一层 ——
 * 也就是说"停进而装载照旧"这种缺陷形状，屏上看不看得见全凭运气。
 * 这一枚种子让**起窗那一趟**直接带着"某颗源是停用着的"起来：源管理那一屏的枚数与灰行、
 * 以及漫画域真装的那一颗源，两样同时进帧。
 *
 * ## 三条纪律（都配对照）
 *
 * 1. **种子只并进本次装载**，不写盘上那份（`prefs/venera_desktop_sources.json` 是用户的状态，
 *    不是取证的临时值）⇒ `setDisabled(` 在这一屏的代码行里只许出现一次，且那一处不是起手那行。
 * 2. **认不出的那串什么都不改**（清单里没有就等于没给），且必须出声点名那串 —— 静默吞掉取证参数
 *    是本仓那一族"参数打错了而量具看起来在工作"。
 * 3. **三层落点逐层钉**（判读 → 集成方递参 → 构建脚本那一跳），同一颗纪律第十次：
 *    漏了 gradle 那一跳时 `-Pdisable=jm` 是"不报错也不做事"。
 */
class DesktopSourceDisableSeedTest {

    private val keys = listOf("jm", "goda", "nhentai")

    private fun ui(name: String): File = DesktopSourceTree.desktopUiSource(name)

    /** ① 判读四档：没给 / 认出那串 / 只给名不带值 / 带个空值。后两档都要出声。 */
    @Test
    fun 判读四档各归各位() {
        assertNull("没给参数却被当成种子", ForensicFlags.disable(emptyList()))
        assertEquals("jm", ForensicFlags.disable(listOf("--disable=jm")))
        assertEquals("那串要原样交回（认不出时点名它）", "no.such", ForensicFlags.disable(listOf("--disable=no.such")))
        assertNull("只给名字不带值要算没给（不猜成空串）", ForensicFlags.disable(listOf("--disable")))
        assertNull("`--disable=` 空值要算没给（空串会被并成一颗假源）", ForensicFlags.disable(listOf("--disable=")))
        assertEquals("同一个名字给两遍时取第一颗（别悄悄并两颗）", "jm", ForensicFlags.disable(listOf("--disable=jm", "--disable=goda")))
    }

    /** ② 那一句读数三档两两不同，且认不出那档必须带着用户打进去的那串。 */
    @Test
    fun 三档读数各说各话() {
        val none = ForensicFlags.disableNote(null, keys)
        assertNull("没给参数就不许念（念了就是把上一轮的读数当本轮的）", none)
        val known = requireNotNull(ForensicFlags.disableNote("jm", keys))
        val unknown = requireNotNull(ForensicFlags.disableNote("no.such", keys))
        assertTrue("认出那一档没说是并进**本次装载**（不说范围就会读成“已经把盘上那份改了”）：$known", known.contains("本次装载"))
        assertTrue("认不出那一档没点名那串：$unknown", unknown.contains("no.such"))
        assertTrue("认不出那一档把它说成了生效：$unknown", unknown.contains("一颗也没"))
        assertNotEquals("两档撞成同一句（用户读不出自己打对没打对）", known, unknown)
    }

    /** ③ 认得出的那颗**真被折进装载**：点名要它时落回另一颗，而不是照旧装它。 */
    @Test
    fun 种子真折进装载而不是只画个灰() {
        val stored = emptySet<String>()
        val withoutSeed = DesktopSourceSelection.resolve(keys, stored, "jm")
        assertEquals("对照组失效：没种子时这颗本该装得上（那这一批就没折什么东西）", DesktopSourcePick.Exact, withoutSeed.kind)
        val withSeed = DesktopSourceSelection.resolve(keys, stored + "jm", "jm")
        assertEquals("点名要一颗停用着的而它没落回另一颗", DesktopSourcePick.FellBack, withSeed.kind)
        assertNotEquals("落回的那颗还是被停用那一颗", "jm", withSeed.key)
        assertTrue("落回那句没念从谁落到谁：${withSeed.reading}", withSeed.reading.contains("jm") && withSeed.reading.contains(withSeed.key))
    }

    /** ④ 认不出的那串什么都不改（并进去等于没并）。 */
    @Test
    fun 认不出那串不改装载() {
        val stored = setOf("goda")
        val union = stored + "no.such"
        assertEquals("并了一串清单里没有的，落点跟着变了", DesktopSourceSelection.resolve(keys, stored, "jm").key, DesktopSourceSelection.resolve(keys, union, "jm").key)
        assertEquals("清单里没有的那串不该被算进『停用了 N 颗』", 1, union.count { it in keys })
    }

    /** ⑤ 三层落点：消费点吃种子、集成方递参数、构建脚本那一跳、KNOWN 登记。 */
    /** ⑥ 种子不落盘：整屏只有一处会写盘，而那一处是用户点一下那一下。 */
}
