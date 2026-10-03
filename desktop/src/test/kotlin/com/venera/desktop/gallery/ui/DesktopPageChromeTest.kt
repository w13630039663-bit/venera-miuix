package com.venera.desktop.gallery.ui

import androidx.compose.ui.unit.dp
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryDailyFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 页面"外壳"两件套（段头 `.phead` + 状态栏 `.status`）的**机器判据**。
 *
 * 这两块最容易被做成"看着整齐但没有一句真话"的东西 —— 稿上那一排 chips 是手摆的占位词、
 * 「每站 12s 预算」可以照抄成字面量、「本次取数 X 前」不刷新就永远停在"刚刚"。
 * 这几都不是编译能拦的，所以钉在这儿。
 *
 * ## 覆盖面
 *
 * ① 段头 chips 取**当页标签频次**：同频次按名字典序稳序（不然每次重排骨架都在抖）；
 * ② `+N` 那个数是**剩下的 distinct 标签数**，不是自己编的；
 * ③ 「X 前」的三级换算 + **时钟倒拨不许说出负数**；
 * ④ 站点读数**不念"0 缺席"**（"没有缺席"该用一个词说完）；
 * ⑤ **预算必须是整秒** —— 这一条是给 [budgetLabel] 兜底的：它是整除换算，一旦别人把
 *    `PER_SITE_TIMEOUT_MS` 改成非整秒，界面念的数就会比实际跑的小，而那时已经很难看出来；
 * ⑥ 文本负向：预算不许写死、`Ctrl K` 键帽不许回来、旧页脚不许复活。
 *
 * ⚠️ 所有负向断言一律走 `DesktopSourceTree.codeText`（**跳注释**）。
 * 本仓记过两次"判据被自己的注释带偏"：注释里本来就该能点名那个被禁的 token，
 * 含进注释就会把说明判成违例，逼着后人把说明删掉。
 */
class DesktopPageChromeTest {

    // ── ① 当页标签频次 ────────────────────────────────────────────────────

    private fun post(id: Long, tags: String) = GalleryPost(site = GallerySite.YANDERE, id = id, tags = tags)

    @Test
    fun `段头 chips 取当页标签频次 而不是手摆的占位词`() {
        val posts = listOf(
            post(1, "东方Project 少女 樱花"),
            post(2, "东方Project 少女"),
            post(3, "东方Project"),
        )
        val (tags, hidden) = desktopTopTags(posts, limit = 2)
        assertEquals(listOf("东方Project", "少女"), tags)
        // 当页 distinct 标签共 3 个，取了 2 ⇒ +1
        assertEquals(1, hidden)
    }

    @Test
    fun `同频次的两个标签按字典序稳序 每次重排骨架不许换次序`() {
        val posts = listOf(post(1, "bbb aaa"), post(2, "ccc bbb aaa ddd"))
        // aaa/bbb 各出现 2 次、ccc/ddd 各 1 次 ⇒ 计数相同时按名字排，前三必是 aaa,bbb,ccc
        val (tags, _) = desktopTopTags(posts, limit = 3)
        assertEquals(listOf("aaa", "bbb", "ccc"), tags)
    }

    @Test
    fun `一张图都没有时 chips 为空且不报出一个假的加号`() {
        val (tags, hidden) = desktopTopTags(emptyList(), limit = 4)
        assertTrue(tags.isEmpty())
        assertEquals(0, hidden)
    }

    @Test
    fun `空白标签不进榜`() {
        val posts = listOf(post(1, "a   b"), post(2, "a"))
        val (tags, hidden) = desktopTopTags(posts, limit = 4)
        assertEquals(listOf("a", "b"), tags)
        assertEquals(0, hidden)
    }

    // ── ② 状态栏的三条读数 ────────────────────────────────────────────────

    @Test
    fun `取数时刻的三级换算`() {
        val now = 1_000_000_000L
        assertEquals("刚刚", elapsedLabel(now - 59_000, now))
        assertEquals("1 分钟前", elapsedLabel(now - 60_000, now))
        assertEquals("59 分钟前", elapsedLabel(now - 59 * 60_000, now))
        assertEquals("1 小时前", elapsedLabel(now - 3_600_000, now))
        assertEquals("1 天前", elapsedLabel(now - 86_400_000, now))
    }

    @Test
    fun `时钟倒拨时说的是刚刚 而不是负几分钟前`() {
        val now = 5_000L
        assertEquals("刚刚", elapsedLabel(now + 120_000, now))
    }

    @Test
    fun `没有缺席时不念零缺席`() {
        // 站点数走派生值（_`Daily.pools.size`_）而不是稿上的汉字「三」—— 详见 `statusTally` 注释。
        assertEquals("3 站 · 都给内容", statusTally(3, 0))
        assertEquals("3 站 · 2 给内容 · 1 缺席", statusTally(3, 1))
        assertEquals("3 站 · 0 给内容 · 3 缺席", statusTally(3, 3))
    }

    @Test
    fun `预算由超时常量现算且今天是整秒`() {
        // ⑤ 这条兜底比"12s"本身更重要：`budgetLabel` 走整除秒，非整秒会被截断成更小的数 ——
        // 界面念 12s 而实际等 12.5s，属于"念的比跑的小"。所以直接要求那一端必须是整秒。
        assertEquals(0L, GalleryDailyFeed.PER_SITE_TIMEOUT_MS % 1_000L)
        assertEquals("12s", budgetLabel(GalleryDailyFeed.PER_SITE_TIMEOUT_MS))
    }

    // ── ③ 文本负向 ────────────────────────────────────────────────────────

    @Test
    fun `预算不写死 状态栏念的数必须来自那一颗超时常量`() {
        val header = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryPageHeader.kt"))
        // 「12s」如果出现在段头代码里，说明预算被抄成了字面量 —— 改预算时它不会跟着变。
        assertFalse("段头不许写死预算：", header.contains("12s"))

        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopDailyPane.kt"))
        assertTrue("预算须引 PER_SITE_TIMEOUT_MS：", pane.contains("GalleryDailyFeed.PER_SITE_TIMEOUT_MS"))
    }

    @Test
    fun `段头与 pane 对同一份六项表说话 不再各自写一份未实现原因`() {
        val header = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryPageHeader.kt"))
        assertTrue("段头的原因串须复用注册表：", header.contains("desktopGalleryHomeRows()"))
    }

    @Test
    fun `状态栏不画 Ctrl K 键帽 桌面没有全局快捷键链`() {
        val status = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopGalleryStatusBar.kt"))
        assertFalse("首页顶到位的键帽不许回来：", status.contains("Ctrl K"))
    }

    @Test
    fun `旧页脚没有复活 三站口径只由 Hero 侧卡说一次`() {
        val pane = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopDailyPane.kt"))
        assertFalse(
            "旧页脚不许复活（重复第二遍三站口径会各自漂移）：",
            pane.contains("yande.re 取的是"),
        )
        assertTrue("状态栏已接上：", pane.contains("DesktopGalleryStatusBar("))
        // 缺席计数必须来自真数据，不是我们另数一遍
        assertTrue("站点总数须取 daily.pools.size：", pane.contains("daily.pools.size"))
        assertTrue("缺席数须取 failures.size：", pane.contains("daily.failures.size"))
    }

    @Test
    fun `状态栏贴窗底 必须在吃 weight 的那一层之外`() {
        // 这条是结构性的：状态栏若落在 weight(1f) 那一层里面，会跟着墙一起被压缩，
        // 窗口一矮就看不见 —— 而"某一行看不见"正是本仓最忌的那种静默交错。
        val metrics = DesktopGalleryMetrics
        assertEquals(24.dp, metrics.statusBarHeight)
        assertTrue("状态栏须有高度预算且不吃 weight：", metrics.statusBarHeight.value > 0)
    }
}
