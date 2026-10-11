package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十二批 批 3′ · 两枚坏档读数的**主人**（批 3 收尾回查时抓出来的）。
 *
 * ## 为什么会单开一批
 *
 * 批 2 把「名单档坏了」那一句接上了屏（`DesktopArtistFollowActionTest` ⑤），而它当时的形状是
 * **页面自己消费**：`DesktopArtistsPage.kt` 里一句 `remember { ports.follows.consumeNotice() }`。
 * 批 3 量 ⑨（换域丢状态）时逐屏数"重来的是什么"，那一格数出来的是另一族缺陷：
 * `consumeNotice()` 是**读走即清**（`GalleryArtistFollowsStore.kt:65`），所以用户切走再切回来，
 * 那句"那份档坏了、已另存为 …"就**自己消失了** —— 屏上仍然摆着同一份空名单，而解释它的那句话没了。
 * 这不是"丢了状态"，是**同一句缺席理由只在第一次进组合时存在**，比丢状态更难被发现。
 *
 * 顺带数出第二颗：`DesktopGalleryFavorites.consumeNotice()` 在桌面 UI **零调用点** ——
 * 收藏档坏了这件事在桌面上从来没出口（store 那句在 `GalleryFavoritesStore.kt:257-261`）。
 *
 * ## 主人只能有一个，而且不能是那一段组合
 *
 * 消费点提到应用根：**一次组合、一次消费、存进状态**，两屏各吃一颗必填参数。
 * 挂在页面上（`remember {}`）就是刚才那个形状 —— 页面离开组合时那份读数跟着离开，
 * 而应用根整场只组合一次（它是 `Window` 的内容），所以它记得住。
 *
 * 两枚字仍然**逐字来自 store**（判据 ⑤）：桌面重述一句，store 那句改了就没有人跟上，
 * 而那一句里带着备份文件名与绝对路径 —— 那是用户唯一能找到自己那份档的线索。
 */
class DesktopCorruptNoticeTest {

    private val app = DesktopSourceTree.desktopUiSource("VeneraDesktopApp.kt")

    /** 那颗读数有没有被**摆上屏**：`Text(那颗` / `?.let {` / `!= null` 三种形状都算（判的是"接了没画"，不是画的形式）。 */
    private fun rendered(code: String, name: String): Boolean =
        Regex("$name\\s*(\\?\\.let|!=\\s*null)").containsMatchIn(code) || Regex("Text\\(\\s*$name").containsMatchIn(code)

    /** ① 两枚读数各被消费**恰好一次**，而且消费点在应用根（不在任何一屏里）。 */
    @Test
    fun 两枚坏档读数只在根上消费() {
        // 扫 `gallery/ui/` 而不是整棵 main：桌面那两颗 wrapper 里各有一句
        // `override fun consumeNotice(): String? = store.consumeNotice()`，那是**转发**不是消费点。
        val sites = DesktopSourceTree.desktopUiSources()
            .flatMap { file -> DesktopSourceTree.codeLinesOf(file).map { file.name to it } }
            .filter { it.second.contains("consumeNotice()") }
        assertTrue(
            "消费点不在应用根（每一屏各自消费一次 = 谁先读到谁把另一屏那句吃掉）：" +
                sites.joinToString(" | ") { "${it.first}:${it.second}" },
            sites.all { it.first == "VeneraDesktopApp.kt" },
        )
        val consumed = sites.map { it.second }.filter { it.contains("ports.follows.consumeNotice()") }
        val favorites = sites.map { it.second }.filter { it.contains("ports.favorites.consumeNotice()") }
        assertTrue("名单那枚没在根上消费（或消费了两回）：$consumed", consumed.size == 1)
        assertTrue("收藏那枚没在根上消费（桌面 UI 今天对它是零调用点）：$favorites", favorites.size == 1)
    }

    /**
     * ② 消费出来的那句要**存进状态**，而不是组合期现取。
     *
     * 组合期现取（`val x = ports.follows.consumeNotice()` 不带 `remember`）更糟：每次重组都调一次，
     * 第一次之后拿到的恒是 `null` —— 那句话只会闪一帧。
     */
    @Test
    fun 消费一次之后存进状态() {
        val holders = DesktopSourceTree.codeLinesOf(app)
            .filter { it.contains("consumeNotice()") }
        assertEquals("根上消费那两枚读数的行不对：$holders", 2, holders.size)
        assertTrue(
            "根上那两枚消费结果没存成状态（组合期现取 = 第二次重组起恒是 null，那句话只会闪一帧）：" + holders,
            holders.all { it.contains("remember") },
        )
        // 反向：两屏都不许再自己消费。
        listOf("DesktopArtistsPage.kt", "DesktopGalleryFavoritesPane.kt").forEach { name ->
            val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(name))
            assertFalse("$name 里还在自己 consumeNotice() —— 那一屏离开组合时读数会跟着消失", code.contains("consumeNotice("))
        }
    }

    /** ③ 两屏各把吃进来的那句**摆上屏**（只接参数不摆 = 假接线）。 */
    @Test
    fun 两屏各把那句摆上屏() {
        val page = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopArtistsPage.kt"))
        assertTrue("画师那一屏没接那颗参数", page.contains("followsNotice: String?"))
        assertTrue("画师那一屏接了参数却没摆上屏", rendered(page, "followsNotice"))

        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryFavoritesPane.kt"))
        assertTrue("收藏那一屏没接那颗参数", pane.contains("favoritesNotice: String?"))
        assertTrue("收藏那一屏接了参数却没摆上屏", rendered(pane, "favoritesNotice"))

        // 中间那一跳也要真传（域路 `when` 到各屏，漏传是编译错误，但传错成常量不是）。
        val bodies = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDomainPanes.kt"))
        assertTrue("域路没把名单那句递给画师那一屏", bodies.contains("followsNotice = followsNotice"))
        assertTrue("域路没把收藏那句递给收藏那一屏", bodies.contains("favoritesNotice = favoritesNotice"))
    }

    /** ④ 两枚参数必填、不给默认值（"漏传不报错"那一族的老规矩）。 */
    @Test
    fun 那颗参数不带默认值() {
        val signatures = DesktopSourceTree.desktopMainSources()
            .flatMap { DesktopSourceTree.codeLinesOf(it) }
            .filter { it.contains("followsNotice: String?") || it.contains("favoritesNotice: String?") }
        assertTrue("判式读空了（一条签名都没扫到）", signatures.isNotEmpty())
        signatures.forEach { line ->
            assertFalse("坏档读数参数给了默认值（漏传就静默变 null，那正是本批要修的形状）：$line", line.contains("= null"))
        }
    }

    /**
     * ⑤ 两枚字**逐字来自 store**，桌面不许重述。
     *
     * store 那两句里带着备份文件名与绝对路径（`*.corrupt-<时间戳>` / `gallery_favorites.json` 的全路径）——
     * 那是用户唯一能找回原档的线索，桌面自编一句就把线索编掉了。
     */
    @Test
    fun 桌面不许重述那两枚字() {
        val banned = listOf("关注名单档读不出来", "画廊收藏档读不出来", "已另存为")
        val offenders = DesktopSourceTree.desktopUiSources()
            .flatMap { file -> DesktopSourceTree.codeLinesOf(file).map { file.name to it } }
            .filter { (_, line) -> banned.any { line.contains(it) } }
        assertTrue(
            "桌面重述了 store 那句（两处各写一份，store 改了没人跟上）：" + offenders.joinToString(" | ") { "${it.first}:${it.second}" },
            offenders.isEmpty(),
        )
        // 对照组：那两句确实各自住在 store 里（读空了会让上面那条恒真）。
        val follows = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/data/GalleryArtistFollowsStore.kt"))
        val favorites = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/data/GalleryFavoritesStore.kt"))
        assertTrue("对照组失效：名单 store 里没有那句'读不出来'", follows.contains("读不出来"))
        assertTrue("对照组失效：收藏 store 里没有那句'读不出来'", favorites.contains("读不出来"))
    }
}
