package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GallerySite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十二批 批 2 · ④：关注 / 取关那枚入口，与名单档坏了那一句。
 *
 * ## 这一条为什么是"接线"而不是"新功能"
 *
 * 写口两端都实现好了：契约在 `GalleryBusinessApi.kt:49`（`toggle(site, name)`），
 * 桌面那份在 `DesktopGalleryArtistFollows.kt:21`，共享 store 里那条落盘链还是第十批 ②′ 修过的
 * （Windows 上 `renameTo` 不覆盖那条）。**零调用点的是界面** —— 于是屏上只能自陈
 * 「关注这个动作在桌面还没有入口」，而那句话活了四批。
 *
 * ## 取关为什么不能直接 `toggle`
 *
 * `toggle` 是**翻转**：对一位没关注的点下去是"关注上"。整页那一行的站点来自并条后的
 * `artist.sites`，理论上可以有两位里只有一位在名单上（并条的判据是名字，不是名单）。
 * 那一格若真出现，"取关"就会把另一位**加进**名单 —— 与本意完全相反且不报错。
 * 所以写之前先按名单筛一遍：只对**确已在名单上**的那几站翻。
 */
class DesktopArtistFollowActionTest {

    /** ① 写口真的被界面调到了（两处各一处：浮层关注、整页取关）。 */
    @Test
    fun 关注与取关各有真写口() {
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        val page = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopArtistsPage.kt"))
        assertTrue("浮层没调 `ports.follows.toggle`：画师那一栏点了没反应", panel.contains("ports.follows.toggle("))
        assertTrue("整页没调 `ports.follows.toggle`：那一屏取不了关", page.contains("ports.follows.toggle("))
        // 对照组：共享面确实有这颗写口（读空了的话上面两条 contains 会恒假而看不出是判式坏了）。
        val api = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/data/GalleryBusinessApi.kt"))
        assertTrue("对照组失效：共享面上找不到 `toggle` 的声明", api.contains("toggle("))
    }

    /** ② 亮/灭读的是**名单那份流**，不是界面自己的一颗 bool。 */
    @Test
    fun 关注状态只有名单一个来源() {
        val panel = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryDetailPanel.kt"))
        assertTrue(
            "浮层没collect关注名单 —— 那就必然在界面上自己记一份亮灭（点了立刻对、重开就错）",
            panel.contains("ports.follows.follows.collectAsState()"),
        )
        assertFalse(
            "界面上多了一颗自己的关注状态（第二份状态与名单会各说各话）",
            Regex("var following\\b").containsMatchIn(panel),
        )
    }

    /** ③ 那两枚字是一句纯函数，且两端同词（Android 用的也是「关注 / 已关注」）。 */
    @Test
    fun 两枚状态各有一句() {
        assertEquals("没关注时说'关注'", "关注", desktopFollowLabel(false))
        assertEquals("已关注时说'已关注'", "已关注", desktopFollowLabel(true))
        val android = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/ui/GalleryArtistRows.kt"))
        assertTrue(
            "Android 那侧的措辞不是「已关注」—— 两端各写一份词，用户换设备就读成两件事",
            android.contains("已关注"),
        )
    }

    /** ④ 取关只摆在整页那一屏，首页那一栏**没有**动作（横卡那一栏只有"点名字去搜"）。 */
    @Test
    fun 首页那一栏不掺取关() {
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopFollowedArtistsPane.kt"))
        assertFalse("首页那一栏出现了写口（横卡那一格本该只读）", pane.contains("toggle("))
        // 对照组：那颗行件确实被这一栏用上了（读空了会让上面那条恒真）。
        assertTrue("对照组失效：那一栏根本没摆共用行件", pane.contains("DesktopFollowedArtistRow("))
    }

    /**
     * ⑤ 名单档读不出来那一句要**念在屏上**，且**逐字用 store 那一句**。
     *
     * `consumeNotice()` 装的不是"写成功回执"（那是盘点里我写错的一条）—— store 只在
     * `readFromDisk()` 解不开档时才写这句（含备份文件名），而这正是"用户攒的名单没被悄悄覆盖"
     * 唯一的读数出口。不念就等于：屏上摆着一份空名单，而真相是"那份档坏了"。
     *
     * ⚠️ 批 3′ 改判过一次：这一条原本断言"这一屏自己 `consumeNotice()`"，而那颗槽**读走即清**，
     * 于是换域再回来那句就自己消失了。现在消费的主人是应用根，这一屏只吃一颗必填参数
     * （为什么见 `DesktopCorruptNoticeTest` 的类注释与判据 ①②）。
     */
    @Test
    fun 名单档坏了那一句要上屏() {
        val page = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopArtistsPage.kt"))
        assertTrue("画师那一屏没接那份读数", page.contains("followsNotice: String?"))
        assertFalse(
            "画师那一屏又自己去消费了 —— 读走即清的那颗槽挂在会离开组合的屏里，那句话会自己消失",
            page.contains("consumeNotice("),
        )
        assertTrue(
            "取用了却没摆上屏（只进日志 = 用户永远不知道自己那份名单是坏了而不是空的）",
            page.contains("followsNotice") && Regex("Text\\(it").containsMatchIn(page),
        )
        // 对照组：store 那句确实只在坏档那一条路上写。
        val store = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/data/GalleryArtistFollowsStore.kt"))
        assertTrue("对照组失效：store 里没有那句'读不出来'", store.contains("读不出来"))
        assertFalse(
            "桌面自己重述了一句（两处各写一份，store 那句改了就没人跟上）",
            page.contains("关注名单档读不出来"),
        )
    }

    /** ⑥ 那句"还没有入口"必须随入口一起下架（§22.3 那条回查）。 */
    @Test
    fun 旧那句没有入口不许残留() {
        listOf("DesktopArtistsPage.kt", "DesktopGalleryDetailPanel.kt").forEach { name ->
            val offenders = DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopUiSource(name))
                .filter { it.contains("还没有入口") || it.contains("没有入口") }
                .map { it.trim() }
            assertTrue("「$name」还写着关注没有入口：" + offenders.joinToString(" ｜ "), offenders.isEmpty())
        }
    }

    /** ⑦ 取关那一步**只翻已在名单上的那些站**（否则会把另一位加进名单）。 */
    @Test
    fun 取关只翻名单里已有的那几站() {
        val targets = desktopUnfollowTargets(
            rowSites = listOf(GallerySite.YANDERE, GallerySite.GELBOORU),
            followed = setOf(GallerySite.YANDERE),
        )
        assertEquals(
            "并条行上的站不代表都在名单里；对没在名单里的那一站 `toggle` 是**加进去**，与「取关」完全相反",
            listOf(GallerySite.YANDERE),
            targets,
        )
        assertEquals("两站都在名单里就两站都翻", 2, desktopUnfollowTargets(rowSites = listOf(GallerySite.YANDERE, GallerySite.GELBOORU), followed = setOf(GallerySite.YANDERE, GallerySite.GELBOORU)).size)
        assertTrue("都不在名单里就一笔都不发", desktopUnfollowTargets(rowSites = listOf(GallerySite.SAFEBOORU), followed = emptySet()).isEmpty())
    }

    /**
     * ⑧ 那句「点名字就去搜索」要落在**有名字可点**的那一屏。
     *
     * 图证读出来的：名单为空那一档，页脚照样念着"点名字去搜这一位" —— 而屏上一个名字都没有。
     * 与本仓那条「空态要说空态该说的话」同族（第八批的三档空态、第九批的四档空态）。
     */
    @Test
    fun 空名单那一屏不念点名字() {
        val page = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopArtistsPage.kt"))
        val foot = page.indexOf("item(key = \"foot\")")
        // 锚点只取**动线本身那三个字**（批 19 把页脚那句改成"点名字去搜索那一屏搜这一位，「看他的图」进单人页"，
        // 旧锚 `点名字就去搜索` 红在一次"就"字上 —— 那是 §0 坑 ㉖ 的形状：钉措辞而不是钉行为）。
        val line = page.indexOf("点名字")
        val gate = page.indexOf("if (readout.gap != GalleryFollowedArtistsGap.NO_FOLLOWS)", foot)
        val caption = page.indexOf("DesktopArtistsPageCaliberCaption", foot)
        assertTrue("判式先失效：页脚那一段没找到（找不到就等于恒真）", foot >= 0 && line > foot && caption > foot)
        assertTrue(
            "页脚那句没被自己的闸拦住（空屏上念着一件此刻做不到的事）",
            gate in 0..<line,
        )
        // 对照组：闸只圈那一句。那颗口径说明讲的是圆座三档，空态也要念 —— 不许被顺手一起藏掉。
        assertTrue("页脚那句口径说明被挪进了闸里（或整段被删了）：caption=$caption gate=$gate", caption < gate)
    }
}
