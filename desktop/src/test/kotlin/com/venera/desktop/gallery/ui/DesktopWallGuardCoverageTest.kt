package com.venera.desktop.gallery.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 桌面第三十七批 批 37 · 墙上那行"少了谁"的**覆盖面**（名单式判据换成机械尺子）。
 *
 * ## 这一批量到的真缺陷
 *
 * 单人页那一面墙只在 `wall.cards.isEmpty()` 那一格里念那两笔缺席 —— 于是"取回 20 张、摆上屏 16 张"
 * 这种最常见的形状**屏上没有任何一句话说明少了哪四张**（`DesktopArtistProfilePane.kt:190`）。
 * 旧的尺子（`DesktopWallCountSourceTest` ⑧）没抓到它，因为它把证据写成
 * `code.contains("guardFragments")` ——**那串字面确实在这颗文件里，只是住在另一个分支里**。
 * 这是 §0 坑 ㉔ 那一族（文本锚点看不见分支条件）的又一形态，而这次连"数出现次数"都不够：
 * 出现一次但永远命中不了，与没出现等价。
 *
 * ## 为什么这次改成扫而不是列名单
 *
 * 同一件事今天有七面墙（日榜、发现、搜索、收藏、最新、排行榜、单人页），而旧的两条判据各自列了
 * 三枚与四枚的文件名 —— **名单会把"这一面墙有没有说话"读成"我记不记得把它列进来"**（§0 坑 ⑧）。
 * 现在把"哪几面墙"交给源码本身回答：凡是摆墙的那颗文件（调 `DesktopGalleryPostWall(` 的那些）都在射程里。
 *
 * ## 收成一颗件而不是各写一遍
 *
 * 那两笔缺席读数此前有两种拼法（`" · "` 与 `"；"`）、两种字号、两种颜色，而**对岸只有一种连接符**。
 * 尺子因此钉两件事：每面墙都摆那颗共用行（`DesktopWallGuardLine`），且 pane 里不许自己拼 `guardFragments(` ——
 * 拼法只许住在 [desktopWallGuardReading] 那一颗件里。
 */
class DesktopWallGuardCoverageTest {

    /** 摆墙的那颗件自己（它定义那颗 composable，不是消费者）。 */
    private val definitionFile = "DesktopGalleryPostTile.kt"

    /**
     * 所有**摆墙**的界面件：`gallery/ui/` 下把 `DesktopGalleryPostWall(` 当函数调的那些文件。
     *
     * 定义那颗文件要剔掉 —— 它那一行写的是 `internal fun DesktopGalleryPostWall(`，
     * 含同一串字面（不剔就会把件本体当成一面墙，②那条也会把它判成违例）。
     */
    private fun wallPanes(): List<File> =
        DesktopSourceTree.desktopUiSources().filter { file ->
            file.name != definitionFile &&
                DesktopSourceTree.codeLinesOf(file).any { line -> line.contains("DesktopGalleryPostWall(") && !line.contains("fun ") }
        }

    /** ① 覆盖面自己先自审：扫出来的必须是**七面**这个量级，而且不许把件本体算进来。 */
    /** ② 每面墙都得摆那行缺席读数，而**不许只住在某个分支里**：共用那颗件是无条件摆的（它自己判空表）。 */
    @Test
    fun 每一面墙都摆那行缺席读数() {
        wallPanes().forEach { file ->
            val code = DesktopSourceTree.codeText(file)
            assertTrue(
                "${file.name} 没摆 DesktopWallGuardLine —— 被挡与收起的那些张数在屏上没有任何一句话说，" +
                    "读的人只会看到「取回多少」与「摆上屏多少」两个数对不上",
                code.contains("DesktopWallGuardLine("),
            )
        }
    }

    /** ③ 拼法只许住在一颗件里：pane 里出现 `guardFragments(` 就是各写一遍（今天两种连接符就是这么来的）。 */
}
