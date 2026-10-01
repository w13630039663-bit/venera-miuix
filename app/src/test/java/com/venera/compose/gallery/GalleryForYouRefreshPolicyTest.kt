package com.venera.compose.gallery

import com.venera.compose.gallery.domain.GalleryForYouRefreshPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「猜你喜欢那一屏要不要沿用上次取到的」的判据（批次 O）。
 *
 * ## 这一屏为什么会"每次进来都重新加载"
 *
 * 切 Tab 走 `popUpTo(startDestination){saveState=true}`，画廊那条条目是**被 pop 掉的** ——
 * `NavBackStackEntry` 的 KDoc 明写 pop 时 "ViewModels will be cleared"，而 `saveState`
 * 只救 `rememberSaveable`，救不了 `viewModel()`。于是切回来四个 VM 全是新实例。
 *
 * 日榜那一屏 2026-09-29 就为这件事存过快照并配了同日门
 * （[com.venera.compose.gallery.domain.GalleryFeedRefreshPolicy]），
 * 但**猜你喜欢这一屏从来没接上同一套** —— 它连缓存都没有，所以每次进来必发一笔请求。
 * 本轮补的就是那一处遗漏，判据与日榜同一条。
 *
 * ## 为什么判据要抽成纯对象
 *
 * VM 是 `AndroidViewModel`，本项目没有 Robolectric，测不到。而这里两条判据错了都**静默**：
 * - `canHydrate` 放宽到"有内容就行"→ 种子丢了也照铺，下一次「换一批」拿的是与屏上无关的新池子；
 * - `canHydrate` 收紧过头 / 空快照算数 → **一屏空的 + 门关着**，冷启动之后永远不再取，
 *   那是比"每次都刷新"更坏的一种假死。
 * 所以落在这里、配单测。
 */
class GalleryForYouRefreshPolicyTest {

    private val today = 20_726L

    // ── canHydrate：内容与推荐种子必须成对在场 ──

    @Test
    fun `有内容也有种子才算铺上了缓存`() {
        assertTrue(GalleryForYouRefreshPolicy.canHydrate(postCount = 40, seed = 1_700_000_000L))
    }

    @Test
    fun `空快照不许算铺上 —— 那会让门永远关着`() {
        // 这一条是"假死"的闸门：posts 为空却被认成已铺好，shouldAutoLoad 就恒 false，
        // 用户看到的是一屏空白而且怎么切都不再取。
        assertFalse("零内容不能算已铺好", GalleryForYouRefreshPolicy.canHydrate(postCount = 0, seed = 1L))
    }

    @Test
    fun `种子缺席时不许沿用`() {
        // 猜你喜欢的内容是按种子从两站原始池里重排出来的。种子丢了还沿用那一屏，
        // 下一次「换一批」换的就是一个与屏上那份无关的池子。
        assertFalse(GalleryForYouRefreshPolicy.canHydrate(postCount = 40, seed = null))
    }

    @Test
    fun `零与负的种子都不算有效值`() {
        // 种子是 System.currentTimeMillis()，0 只可能是"没存上"，不是"1970 年那一轮"。
        assertFalse(GalleryForYouRefreshPolicy.canHydrate(postCount = 40, seed = 0L))
        assertFalse(GalleryForYouRefreshPolicy.canHydrate(postCount = 40, seed = -1L))
    }

    // ── shouldAutoLoad：同日不取、跨天补拉、没铺上就取 ──

    @Test
    fun `铺上了缓存而且就是今天存的 不再联网`() {
        assertFalse(
            GalleryForYouRefreshPolicy.shouldAutoLoad(
                hydrated = true, cachedOnEpochDay = today, todayEpochDay = today,
            ),
        )
    }

    @Test
    fun `跨了天必须补拉`() {
        assertTrue(
            GalleryForYouRefreshPolicy.shouldAutoLoad(
                hydrated = true, cachedOnEpochDay = today - 1, todayEpochDay = today,
            ),
        )
    }

    @Test
    fun `没铺上缓存时一律要取 哪怕日期对得上`() {
        // 用户点了「换一批」会把两个标记一起清掉（与日榜 refresh() 同一对），
        // 这时候如果还看日期，就会把「换一批」变成假按钮。
        assertTrue(
            GalleryForYouRefreshPolicy.shouldAutoLoad(
                hydrated = false, cachedOnEpochDay = today, todayEpochDay = today,
            ),
        )
    }

    @Test
    fun `铺上了但日期缺席也要取`() {
        assertTrue(
            GalleryForYouRefreshPolicy.shouldAutoLoad(
                hydrated = true, cachedOnEpochDay = null, todayEpochDay = today,
            ),
        )
    }
}
