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
    /** ② 亮/灭读的是**名单那份流**，不是界面自己的一颗 bool。 */
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
}
