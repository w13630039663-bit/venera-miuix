package com.venera.desktop.gallery.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面第十三批 批 8 · 搜索那一屏的**续页**。
 *
 * ## 这一批补的是"只取第 1 页"那笔欠账
 *
 * 搜索那一屏从第六批起就是页码写死的，页尾挂着一句「（翻页还没接）」。
 * 缺的不是网络那一层（`searchPosts(query, page, limit)` 一直能翻页），缺的是**这一屏的状态**：
 * 各腿第几页、哪些站到底、去重键累计到哪儿 —— 而那几样在第十批 ③ 已经立成一颗
 * [DesktopGalleryBatch]（发现那一屏在用）。所以这一批不是"再造一套分页"，是**把第二屏接到同一颗上**：
 * 两处各定一遍"缺席的腿算不算到底"，早晚会漂成"同一串标签在两端翻出不同数量"。
 *
 * ## 顺手修掉一处缺陷（长在发现那一屏上）
 *
 * 那颗 holder 从前只存一枚全局页码，而发现屏对**所有腿**发同一个 `page + 1` ——
 * 上一笔超时的那一站从没拿到过第 1 页，下一笔却被跳到第 2 页 ⇒ 那一页的图**永久漏掉**，
 * 而屏上只写着"那一站没答上"。共享面早写了这条判据（`nextPagesForAppend`：缺席的腿从第 1 页补），
 * 桌面没吃它。现在页码是一份逐腿的账，算式转交那颗共享件（判据在 [DesktopGalleryBatchTest] ⑧⑨）。
 *
 * ## 这一屏仍然没有的（逐条点名，不留假开关）
 *
 * 滚到页尾**不**自动续页（要点那枚钮）；没有"跳到第 N 页"；查询历史还不存（那是批 9）。
 */
class DesktopSearchPagingTest {

    private val pane = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopGallerySearchPane.kt",
    )

    /** 代码行（跳注释）—— 判"接线在不在"用这一把。 */
    private fun code(): String = DesktopSourceTree.codeText(pane)

    /** 全文（含注释）—— 判"那句说明还成不成立"用这一把（批 7 那条方法：过期说明住在注释里）。 */
    private fun full(): String = pane.readText()

    /** ① 「下一页」真摆出来了，而它外面有三道闸。 */
    /** ② 那句"翻页还没接"下架；而这一屏的**边界**要换成新的那句说实话。 */
    /** ③ 页码只有一处算式：转交那颗共享件，屏上不自写。 */
    @Test
    fun 页码算式只有一处() {
        val code = code()
        assertTrue("续页没转交那颗页码算式", code.contains("desktopGalleryBatchNextPages("))
        assertFalse("屏上又写死了一枚 `page = 1`（那正是这一批要撤的东西）", code.contains("page = 1"))
        assertFalse("屏上自己推页码（那是「所有腿同一页」的旧形状）", code.contains("page + 1"))
        assertTrue("取数没吃算出来的那一页", code.contains("page = pages.getValue(site)"))
        // 对照：那颗共享件真在桌面编译面上（读空会让上面几条恒真）。
        val face = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("gallery/domain/GallerySearchSource.kt"))
        assertTrue("对照组失效：共享面读不到那颗算式", face.contains("fun nextPagesForAppend("))
    }

    /** ④ 第一轮与续页**共用同一颗取数件**：各写一份就是"第一页能翻而续页判据漂"的成因。 */
    /** ⑤ 跨页去重的键要从上一笔带过来 —— 漏了就是"翻一页，三张里两张是刚才那两张"。 */
    /** ⑥ 续页落地有一道闸，而被丢掉的那一笔要出声（不许静默"点了没反应"）。 */
    /** ⑦ 反向钉：这一批**不该**动的东西 —— 三档缺席各自那一句话还在。 */
}
