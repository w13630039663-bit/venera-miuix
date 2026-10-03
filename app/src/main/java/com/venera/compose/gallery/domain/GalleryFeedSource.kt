package com.venera.compose.gallery.domain

import android.content.Context
import com.venera.compose.gallery.data.GalleryPorts
import java.util.Calendar

/**
 * [GalleryDailyFeed] 的 **Android 门面**。
 *
 * 取数编排、三站并发、时间预算与 `Daily` 的形状全在 [GalleryDailyFeed] —— 那颗吃
 * `GalleryBoards` 契约、一句平台类型都没有，所以它是桌面编译面的一部分。这颗只负责
 * 把 `Context` 换成契约，唯一存在理由是 `gallery/ui` 那两处
 * `GalleryFeedSource.getInstance(context)`（`GalleryDailyScreen.kt:81`、`GalleryScreen.kt:221`）
 * 与 `BusinessApiBaseline` 上那两条归属记录**一字不动**（本轮硬约束：Android UI 零改动）。
 *
 * ⚠️ 别把编排逻辑再写回这颗：写回来就等于把它重新钉死在 Android 面，桌面侧只能另抄一份
 * 并发与合并 —— 而"两份迟早分叉"正是 [GalleryLegGuard] 那次收口查出的真缺陷的同族。
 *
 * 下面两枚 companion 成员是**转发**，不是第二份定义：`PER_SITE_TIMEOUT_MS` 与 `yesterdayString`
 * 的读点里，`GalleryFeedBudgetTest` / `GalleryFeedDateTest` / `GalleryLegGuard` 与
 * `GalleryForYouViewModel` 的注释都点名着 `GalleryFeedSource`，门面必须继续答得出这两个名字。
 * 数只有一处（[GalleryDailyFeed]），所以两侧不可能算出不同的 12s。
 */
class GalleryFeedSource private constructor(context: Context) {

    private val appContext = context.applicationContext

    /**
     * `lazy` 是**时机**判据，不是省成本：改造前 boards/clients 是在第一次 `loadDaily` 里
     * 才 `getInstance` 的，构造期什么都不建。改成构造期急取就把端口图建到了
     * `remember { GalleryFeedSource.getInstance(context) }` 那一刻。
     */
    private val feed by lazy { GalleryDailyFeed(GalleryPorts.of(appContext).boards) }

    suspend fun loadDaily(seed: Long): Result<GalleryDailyFeed.Daily> = feed.loadDaily(seed)

    companion object {
        /** 转发到 [GalleryDailyFeed]。`const` 不能转发，所以这里是一枚属性 —— 值仍是同一个数。 */
        val PER_SITE_TIMEOUT_MS: Long get() = GalleryDailyFeed.PER_SITE_TIMEOUT_MS

        internal fun yesterdayString(now: Calendar = Calendar.getInstance()): String =
            GalleryDailyFeed.yesterdayString(now)

        @Volatile
        private var INSTANCE: GalleryFeedSource? = null

        fun getInstance(context: Context): GalleryFeedSource = INSTANCE ?: synchronized(this) {
            INSTANCE ?: GalleryFeedSource(context.applicationContext).also { INSTANCE = it }
        }
    }
}
