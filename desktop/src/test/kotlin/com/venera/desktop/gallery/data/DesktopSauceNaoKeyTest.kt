package com.venera.desktop.gallery.data

import com.venera.desktop.gallery.ui.DesktopSourceTree
import com.venera.desktop.gallery.ui.desktopSauceNaoKeyReading
import com.venera.desktop.platform.FakePaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 桌面第三十四批 批 34 · SauceNao 键那一枚输入口（反搜那一族的第一格）。
 *
 * ## 为什么先做键，以及为什么先修一颗撞名
 *
 * 批 34 动手时桌面这一族是两枚直接抛的桩、`ports.reverse` 零消费点，而"入口 + 结果那一屏"要吃的正是这枚键：
 * **没有输入口就先接反搜，等于摆一枚永远按不动的开关**。批 35 已经把那一格接上（`DesktopReverseSearchTest`），
 * 所以本批这颗夹具现在钉的是"屏上写的与请求带的是同一份"。
 *
 * 动手前先量到一颗更坏的：`SauceNaoCredentials` 与 `GelbooruCredentials` **成员同名**（两枚都叫 `apiKey`），
 * 而桌面这一颗同时实现这两颗接口、只交出一个 `apiKey`（= Gelbooru 那份）。
 * 批 34 那天它没炸只是因为反搜那两枚方法是抛桩；批 35 接上之后，递错一枚的后果是**发给 saucenao.com 的 `api_key=` 成了 Gelbooru 那枚** ——
 * 把 A 站的凭据发给 B 站。对岸不撞是因为两颗 account 各是各的对象。所以本批第一件是拆开这两枚读数。
 *
 * ## 三条口径照对岸，一条都不重定
 *
 * 键名与存储名逐字回核 `SauceNaoAccount.kt`（② 那一条）；**只落盘不校验**（对岸的理由在它自己的类注释里：
 * SauceNAO 没有"保证非空的最小请求"那种端点）；空串 = 清掉（对岸 `clear() = save("")` 同一条），
 * 所以桌面这边只暴露一颗 `setSauceNaoKey` 而不是成对的两枚 —— 批 26 撤的就是"成对而死"那两枚。
 */
class DesktopSauceNaoKeyTest {

    private val androidAccount by lazy {
        DesktopSourceTree.androidSource("gallery/data/SauceNaoAccount.kt").readText()
    }

    private fun freshRoot(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "d34-sauce-${System.nanoTime()}")
        check(dir.mkdirs()) { "建不出临时数据目录：${dir.path}" }
        return dir
    }

    private fun credentialsAt(dir: File) = DesktopGalleryCredentials(DesktopGalleryHandle.of(FakePaths(dir)))

    /** ① 两枚 key 各归各的存储（这条是本批的牙：撞名那天它红），并真跑落盘与清掉。 */
    @Test
    fun `① 两枚key各归各的存储而填了立刻看得见`() {
        val dir = freshRoot()
        val credentials = credentialsAt(dir)
        assertFalse("对照组失效：新建那颗今天不该已填", credentials.sauceNaoHasKey.value)

        credentials.setSauceNaoKey("  d34-sauce-key  ")
        assertTrue("填了而那句「已填」没翻真（流没跟着写盘走）", credentials.sauceNaoHasKey.value)
        assertEquals(
            "落盘要去首尾空白（留半截空格等于一枚看着填了实际用不了的键）",
            "d34-sauce-key",
            credentials.sauceNao.apiKey,
        )
        assertEquals(
            "两枚 key 撞成一份了 —— 发给 saucenao.com 的会是 Gelbooru 那枚",
            "",
            credentials.apiKey,
        )
        assertTrue(
            "换一颗对象读不到 ⇒ 那枚键只活在内存里，重启就没了",
            credentialsAt(dir).sauceNaoHasKey.value,
        )

        credentials.setSauceNaoKey("")
        assertFalse("清掉之后那句「已填」还在", credentials.sauceNaoHasKey.value)
        assertFalse("清掉没落盘（下一颗对象仍读得到旧键）", credentialsAt(dir).sauceNaoHasKey.value)
    }

    /** ② 键名与存储名逐字回核对岸，而存储名走的是那枚共享常量而不是各写一遍字面。 */
    @Test
    fun `② 键名与存储名与对岸同串`() {
        val desktop = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryCredentials.kt").readText()
        val androidKey = Regex("""KEY_API_KEY = "([^"]+)"""").find(androidAccount)?.groupValues?.get(1)
        val desktopKey = Regex("""KEY_API_KEY = "([^"]+)"""").find(desktop)?.groupValues?.get(1)
        assertTrue("对照组失效：对岸那句常量没读到，这条回核没有底本", androidKey != null)
        assertTrue("对照组失效：桌面那句常量没读到", desktopKey != null)
        assertEquals("键名与对岸不是同一串（两端会各写一份文件各读一份）", androidKey, desktopKey)
        assertEquals(
            "桌面没引那枚共享存储名常量（引它才算同一份 prefs）",
            1,
            desktop.split("PreferenceKeys.PREFS_SAUCENAO_ACCOUNT").size - 1,
        )
        assertTrue("对岸没引那枚共享常量 ⇒ 这条回核的前提变了", androidAccount.contains("PreferenceKeys.PREFS_SAUCENAO_ACCOUNT"))
    }

    /** ③ 屏上那两句各说各的档。那颗函数**不收键本体** —— 拿不到就上不了屏，比禁串硬。 */
    @Test
    fun `③ 那两句读数按档各说一句`() {
        val filled = desktopSauceNaoKeyReading(hasKey = true)
        val empty = desktopSauceNaoKeyReading(hasKey = false)
        assertTrue("已填那句没点名不回显：" + filled, filled.contains("已保存") && filled.contains("不回显"))
        assertTrue("没填那句要说清差的是哪件事：" + empty, empty.contains("还没填") && empty.contains("找同一张图"))
        assertTrue("两句不许同形", filled != empty)
    }

    /** ④ 四跳接线：端口那颗具体件、集成方递进去、应用根必填、那一屏真调写口。 */
}
