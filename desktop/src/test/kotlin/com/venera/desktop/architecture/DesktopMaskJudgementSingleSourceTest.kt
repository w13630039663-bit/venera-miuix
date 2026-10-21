package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「六颗新屏的打码判据只有壳里那一处」—— 阅读器那颗文件注释写的「两处判据一定会漂」。
 *
 * ## 漂了会是什么形状
 *
 * 这一族的成因在清单 §1/§2 与 `DesktopUxServicesProvidedTest` 都记过：桌面此前吃的是
 * `NoOpUxPlatformServices`（`coverMaskStateOf` 恒 `VISIBLE`），于是**共享零件里的封面全不打码** ——
 * 而它不让任何测试变红，因为那个返回值是合法值。修法是壳里 provide 真守卫 + 壳替每一屏问那一处。
 *
 * 现在的新风险是反方向的：**六屏各算一套**。屏自己算有两种坏法——
 *  ① 判据与守卫件那份档不一致（墙上遮着、详情不遮）；
 *  ② 有人在自己屏里读 `guard.nsfwMaskMode` 直接比字符串（那才是真的两份口径）。
 * 清单 A 条担心的正是这一族（`jm` 是探索默认源，`OFF` 档下成人封面直接铺出来）。
 *
 * ## 判据（数自现状，不是设想的红线）
 *
 *  - 六屏的文件里**零处**判据：只吃形参 `masked` / `maskedOf`，不 import 守卫件、不比档名；
 *  - 问 `coverMaskStateOf` 的地方只有壳（计数一并钉住）；
 *  - 装配点读的是守卫件那一条流，不是硬编码档名。
 */
class DesktopMaskJudgementSingleSourceTest {

    private val screens = listOf(
        "detail/DesktopComicDetailScreen.kt",
        "explore/DesktopExploreScreen.kt",
        "search/DesktopSearchScreen.kt",
        "favorites/DesktopFavoritesScreen.kt",
        "reader/DesktopReaderScreen.kt",
        "sourcemanage/DesktopSourceManageScreen.kt",
    )

    private fun text(rel: String) = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf(rel))

    /** ① 六屏里不许出现「自己判该不该遮」的那几种写法。 */
    @Test
    fun 六屏只吃递进来的masked而不自判() {
        // 守卫件那三个名字 + 那个判据函数：屏里出现任何一个就意味着它自己算了一遍。
        val banned = listOf(
            "coverMaskStateOf",
            "DesktopGalleryContentGuard",
            "nsfwMaskMode",
            "setNsfwMaskMode",
            "GalleryContentGuard",
        )
        val offenders = screens.flatMap { rel ->
            banned.filter { it in text(rel) }.map { "$rel → $it" }
        }
        assertTrue(
            "这些屏自己算了一遍打码判据（两处判据一定会漂：漂成的样子是墙上遮着而详情不遮）：$offenders",
            offenders.isEmpty(),
        )
    }

    /** ② 形参形状按屏的粒度分两种：逐卡判的吃函数、整本判的吃单值。 */
    @Test
    fun 形参形状按屏的粒度分两种而不是一屏一套() {
        val perCard = listOf(
            "explore/DesktopExploreScreen.kt",
            "search/DesktopSearchScreen.kt",
            "favorites/DesktopFavoritesScreen.kt",
        )
        perCard.forEach { rel ->
            assertTrue("$rel 不再逐卡判（那三屏一屏几十本，每本的源与标签都不同）：", "maskedOf: (" in text(rel))
        }
        listOf("detail/DesktopComicDetailScreen.kt", "reader/DesktopReaderScreen.kt").forEach { rel ->
            assertTrue("$rel 变成逐卡判据了（那一本的档是按整本算的）：", "masked: Boolean," in text(rel))
            assertTrue("$rel 不该同时有两种形参：", "maskedOf: (" !in text(rel))
        }
    }

    /** ③ 判据的问法只有一处出处：壳（`DesktopUxPlatformServices.kt` 那 1 处是**定义**那颗）。 */
    @Test
    fun 判据只在壳里问() {
        val counts = DesktopSourceTree.desktopMainSources()
            .associate { it.name to DesktopSourceTree.codeOccurrences(it, "coverMaskStateOf") }
            .filterValues { it > 0 }
        assertEquals(
            "`coverMaskStateOf` 的分布变了。现状按**剥注释后的代码行**数：壳 5 处 = 详情 / 阅读 / 收藏 / 探索 / 搜索 " +
                "那五路各问一次（**墙**那一路不在这里 —— 它把 `guard` 与 `maskMode` 递进取数层判）" +
                " + `DesktopUxPlatformServices.kt` 1 处（那颗是判据的**定义**，不算另算一遍）。" +
                "新增一处要先答：这一屏的判据为什么不复用壳里那一条。",
            mapOf("VeneraDesktopLargeScreen.kt" to 5, "DesktopUxPlatformServices.kt" to 1),
            counts,
        )
    }

    /** ④ 那条链的源头是真守卫件（不是常量、不是默认档）。 */
    @Test
    fun 判据那条链的源头是真守卫() {
        val app = DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"))
        val maskLine = app.firstOrNull { it.startsWith("maskModeOf =") }
        assertTrue("装配点没递 `maskModeOf`（屏上判据会恒吃默认档）：$app", maskLine != null)
        assertTrue("递进去的不是守卫件那一条流。实际：$maskLine", "guard." in maskLine!!)

        val shell = text("VeneraDesktopLargeScreen.kt")
        assertTrue("`LocalUxPlatformServices provides` 那一句没了（R3 复发）：", "LocalUxPlatformServices provides" in shell)
        assertTrue("provide 的居然是 NoOp（与不 provide 等价）：", "DesktopUxPlatformServices(" in shell)
    }

    /**
     * ⑤ 源管理屏那三档的字面量与壳里那句失败读数同一套。
     *
     * 写口与读口分在两处时，漂的样子是「屏上能选 HIDE 而守卫件认不出来」——
     * 那比不遮更糟（用户以为开了最严的一档）。
     */
    @Test
    fun 三档字面量在屏上与壳里的读数同一套() {
        val manage = text("sourcemanage/DesktopSourceManageScreen.kt")
        listOf("OFF", "BLUR", "HIDE").forEach {
            assertTrue("源管理屏少了那一档 $it：", "MaskChoice(\"$it\"" in manage)
        }
        assertTrue(
            "壳里那句写口失败读数不再点名这三档（屏上选了而写不进去时，用户读不出为什么）：",
            "档位只接受 OFF/BLUR/HIDE" in text("VeneraDesktopLargeScreen.kt"),
        )
    }

    /** ⑥ 阅读器那一档的打码是 Modifier 层（blur），不另造一套文案；页格那颗兜底也还在。 */
    @Test
    fun 阅读器的打码不造第二套文案而页格兜底还在() {
        val reader = text("reader/DesktopReaderScreen.kt")
        assertTrue("那一档不再是 blur（改成藏起来会与墙上/封面的语义分家）：", "Modifier.blur(24.dp)" in reader)
        assertEquals(
            "页格那颗高度兜底没了或被写成两处（清单 §1 因 2：0 高的 item 不被预取 ⇒ 永远等不到首帧）：",
            1, reader.lines().count { "aspectRatio(0.7f)" in it },
        )
        // 页图吃的是对象 model，不是字符串（清单 §1 因 3）。
        assertTrue("页不再是 `pages[i]` 那个对象（退回字符串会被 Coil 送网络 fetcher ⇒ 一片黑）：", "model = pages[i]" in reader)
    }
}
