package com.venera.desktop.gallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 离屏语义树判据（接进桌面模块的第一条，2026-10-09）。
 *
 * ## 为什么要有这一条
 *
 * qoder 的 [DesktopGalleryFavoritesClearTest] ①~⑩ 全是纯函数 + 源码扫描，它们证明得了
 * "两步的形状对"，证明不了"点下去屏上那句真的变了"。这一条用 `runComposeUiTest` 把界面
 * **离屏渲成语义树**、`performClick()` 之后断言命中的是**屏上那句文案** —— 毫秒级、不起窗、
 * 不碰鼠标/前台，正好补上 856 条判据全缺的像素层盲区。
 *
 * ## 范围边界（诚实标注，不越界也不重测）
 *
 * - **测的是真实的** [desktopFavoritesClearNext] / [desktopFavoritesClearLabel] 两个纯函数
 *   （直接来自产品代码 `DesktopGalleryFavoritesClear.kt`）+ 真实的 Compose 重组与语义树机制。
 * - 清空按钮在 `DesktopGalleryFavoritesPane.kt` 里的 UI 接线形状（哪一行调哪颗、两档各一处）
 *   由 qoder 的源码扫描判据 ④⑨ 钉着，这里不重测 —— 只验"点一下屏上那句变没变"这一层。
 * - **盖不住的**（与取证种子同一盲区，照旧）：真实滚轮/手势的落点、焦点归属、剪贴板内容、
 *   系统文件框。这些要么要真事件（用户已放行鼠标键盘），要么仍要用户。
 */
@OptIn(ExperimentalTestApi::class)
class FavoritesClearSemanticsTest {

    /**
     * 第一按只武装（文案 IDLE→ARMED），第二按才真清（触发 onFire）。
     * 这一条抓住的是"两次点击之间屏上那句文案确实变了" —— 纯函数判据做不到。
     */
    @Test
    fun 点一下屏上那句从_idle_变_armed_再点才真清() = runComposeUiTest {
        var fired = false
        setContent { FavoritesClearButtonUnderTest(count = 3, onFire = { fired = true }) }

        onNodeWithText("清空全部 3 条").assertExists("未武装那句该在屏上")
        onNodeWithText("清空全部 3 条").performClick()

        onNodeWithText("确认清空这 3 条", substring = true).assertExists("第一按该改口成确认句")
        assertFalse("第一按只武装，不许真清", fired)

        onNodeWithText("确认清空这 3 条", substring = true).performClick()
        assertTrue("第二次按才真清，该触发 onFire", fired)
    }

    /**
     * 反向对照：只点一次，屏上进入武装态、但那一笔清档动作绝不发生。
     * 与 qoder 的纯函数判据 [DesktopGalleryFavoritesClearTest.条数变了就退回未武装] 互为表里——
     * 那条验函数返回值，这条验"返回值真的反映到了屏上"。
     */
    @Test
    fun 只点一次屏上武装但不真清() = runComposeUiTest {
        var fired = false
        setContent { FavoritesClearButtonUnderTest(count = 5, onFire = { fired = true }) }

        onNodeWithText("清空全部 5 条").performClick()
        onNodeWithText("确认清空这 5 条", substring = true).assertExists()
        assertFalse("只点一次，那一笔清档动作不许发生", fired)
    }
}

/**
 * 最小可离屏渲染的被测件：复用产品两个真实纯函数，用 `mutableStateOf` 管 `armedAt`
 * （与 `DesktopGalleryFavoritesPane.kt` 第 85 行同形）。被测的是"真实状态 → 真实重组 → 屏上真变"这条链，
 * 不是重新实现一遍逻辑。
 */
@Composable
private fun FavoritesClearButtonUnderTest(count: Int, onFire: () -> Unit) {
    var armedAt by remember { mutableStateOf<Int?>(null) }
    val stage = if (armedAt == null) DesktopFavoritesClearStage.IDLE else DesktopFavoritesClearStage.ARMED
    Row {
        BasicText(
            desktopFavoritesClearLabel(stage, count),
            modifier = Modifier.clickable {
                when (desktopFavoritesClearNext(stage, armedAt ?: 0, count)) {
                    DesktopFavoritesClearStage.FIRE -> onFire()
                    DesktopFavoritesClearStage.ARMED -> armedAt = count
                    DesktopFavoritesClearStage.IDLE -> Unit
                }
            },
        )
    }
}
