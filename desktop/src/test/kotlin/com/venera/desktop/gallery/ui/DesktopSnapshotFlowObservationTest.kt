package com.venera.desktop.gallery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 批 233-5 的量具：**`snapshotFlow` 等落点时，叫醒等待者的是"快照状态被写了"，不是"屏上又画了一帧"**
 * （进程内，零窗口，`autoAdvance = false` ⇒ 全程没有第二帧）。
 *
 * ## 这一条为什么值得单独立一颗
 *
 * 作品页那条进场飞行体等的是"落点量出来了没有"：
 *
 * ```
 * snapshotFlow { GalleryFlyIn.cardBoundsOf(entryKey) }.first { it != null }
 * ```
 *
 * 而落点是卡片的布局回调写进 `GalleryFlyIn.cardBounds` 的，那颗原本是一只**普通 `HashMap`**
 * （它的注释写着"刻意不是 Compose 状态"，理由是"做成 state 只会白拖一次重组"）。
 *
 * ## 现量结论（2026-10-19，两次跑同一份代码）
 *
 * - 普通那只：写完不派帧 ⇒ **一次读数也不发**（实测只有订阅那一刻的 `null`）。
 *   等待者只能等"别的什么东西碰巧画了一帧"—— 图落位、骨架、弹簧，都与转场无关。
 * - 快照那只：同一时刻**当场读到**（实测 `[null, 5]`）⇒ 写入自己就作数。
 *
 * ⇒ 这一批把 `cardBounds` 改成 `mutableStateMapOf`（见那颗文件里改判的那段）。
 * 那条"白拖一次重组"的理由在这一族里不成立：**没有人在组合期读它**（读它的三处都是
 * `snapshotFlow` 与点击那一刻的命令式取数），所以做成快照状态只多一件事 —— 让写入叫得醒等待者。
 *
 * ⚠️ 另一半仍然没关：真机上"有帧"与"没帧"在屏上读不出差别（两种都表现为"偶尔不飞"），
 * 所以这一族最后由带 `FlyProbe` 读数的诊断包收尾（手册 §11 待上机那一格）。
 *
 * ## 量具自己那条（写下来免得下一次再踩）
 *
 * 第一版用 `BroadcastFrameClock` + 单线程 `runBlocking` 量，"派一帧之后普通那只读到没有"这一档
 * 的读数随调度漂（同一份代码一次 `[null]`、一次 `[1]`）⇒ **那一档不钉**，只钉不需要帧的那一档。
 */
@OptIn(ExperimentalTestApi::class)
class DesktopSnapshotFlowObservationTest {

    /** 没有第二帧时：普通那只不醒，快照那只当场醒。这一条就是换底的全部理由。 */
    @Test
    fun 落点住进快照状态后写入即叫醒等待者而普通Map等不到帧不醒() = runComposeUiTest {
        val plain = HashMap<String, Int?>()
        val state = mutableStateMapOf<String, Int?>()
        val plainSaw = mutableListOf<Int?>()
        val stateSaw = mutableListOf<Int?>()
        setContent {
            LaunchedEffectCollect(plain, plainSaw)
            LaunchedEffectCollect(state, stateSaw)
        }
        mainClock.autoAdvance = false
        waitForIdle()
        // 订阅落地的基线：两边都只报过"还没人写过"那一个 null。
        assertEquals("普通那只订阅即读到 null", listOf<Int?>(null), plainSaw)
        assertEquals("快照那只订阅即读到 null", listOf<Int?>(null), stateSaw)
        runOnUiThread {
            plain["k"] = 5
            state["k"] = 5
        }
        waitForIdle()
        assertEquals(
            "普通那只：不派帧就没有新读数（等待者只能赌别人画一帧）",
            listOf<Int?>(null),
            plainSaw,
        )
        assertEquals(
            "快照那只：写入即叫醒，不需要谁再画一帧 ⇒ 落点该住在这里",
            listOf<Int?>(null, 5),
            stateSaw,
        )
    }

    /** 订阅一只 Map 的 `snapshotFlow` 并逐次收下读数（两种底共用这一颗）。 */
    @Composable
    private fun LaunchedEffectCollect(map: Map<String, Int?>, sink: MutableList<Int?>) {
        LaunchedEffect(map) { snapshotFlow { map["k"] }.collect { sink += it } }
    }
}
