package com.venera.compose.components.selection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 区间反选的判据 —— 对着官方 `local_favorites_page.dart:737-757` 逐条钉死。
 *
 * 这段逻辑不在界面上留痕，只能靠单测守：一旦写反（例如不去跳过锚点、或把反选写成全选），
 * 表现是"长按一下，屏幕上跳出一堆没见过的勾"，而这类现象在真机上很难复现定位。
 */
class MultiSelectRulesTest {

    // a b c d e
    private val order = listOf("a", "b", "c", "d", "e")

    @Test
    fun rangeCoversBothEndsButNeverTouchesTheAnchor() {
        // 锚点 a(0) → 目标 c(2)：b、c 被反选；锚点 a 自己**保持原状**（它已在选中里，不会被动）。
        assertEquals(
            setOf("a", "b", "c"),
            MultiSelectRules.rangeToggle(setOf("a"), "a", "c", order),
        )
    }

    @Test
    fun anchorIsKeptAsIsEvenWhenItWasNotSelected() {
        // 锚点没被选中时也不会因为"落在区间里"而被勾上 —— 跳过锚点是**无条件**的。
        assertEquals(
            setOf("c", "d", "e"),
            MultiSelectRules.rangeToggle(emptySet(), "b", "e", order),
        )
    }

    @Test
    fun directionOnlyChangesWhichEndIsTheAnchor() {
        // 向下长按（锚点 a → 目标 d）：区间 0..3 除锚点外全部反选 ⇒ 多出 b、c、d。
        assertEquals(
            setOf("a", "b", "c", "d"),
            MultiSelectRules.rangeToggle(setOf("a"), "a", "d", order),
        )
        // 向上长按（锚点 d → 目标 a）：区间同样 0..3，但这次被跳过的是 d 而不是 a
        // ⇒ a 被取消、b 与 c 被勾上。两向都成立的性质只有一条：**锚点不动**。
        assertEquals(
            setOf("b", "c"),
            MultiSelectRules.rangeToggle(setOf("a"), "d", "a", order),
        )
    }

    @Test
    fun alreadySelectedItemsInsideTheRangeGetDeselected() {
        // 是"反选"，不是"全选"：区间内已勾上的会被取消。
        assertEquals(
            setOf("a", "c", "e"),
            MultiSelectRules.rangeToggle(setOf("a", "b", "d"), "a", "e", order),
        )
    }

    @Test
    fun itemsOutsideTheRangeAreUntouched() {
        // 区间两端之外的选中项（这里的 a 与 e）不该被波及。
        assertEquals(
            setOf("a", "e", "c"),
            MultiSelectRules.rangeToggle(setOf("a", "e"), "b", "c", order),
        )
    }

    @Test
    fun anchorEqualToTargetChangesNothing() {
        // 锚点==目标时区间只剩它自己（被跳过）⇒ 什么都不变。
        // 这条同时说明官方**没有**"再长按一次同一位置即撤销"的性质，UI 文案里不要承诺它。
        assertEquals(
            setOf("a", "c"),
            MultiSelectRules.rangeToggle(setOf("a", "c"), "c", "c", order),
        )
    }

    @Test
    fun missingAnchorDegradesToSingleToggle() {
        // 换了收藏夹 / 改了搜索词后，锚点可能已不在当前列表里。
        // 官方这种情况会拿陈旧下标去 comics[i] 越界崩溃；我们退化成单点开关。
        assertEquals(
            setOf("a", "c"),
            MultiSelectRules.rangeToggle(setOf("a"), "zzz", "c", order),
        )
        assertEquals(
            setOf("a"),
            MultiSelectRules.rangeToggle(setOf("a", "c"), "zzz", "c", order),
        )
    }

    @Test
    fun nullAnchorDegradesToSingleToggle() {
        assertEquals(
            setOf("d"),
            MultiSelectRules.rangeToggle(emptySet(), null, "d", order),
        )
    }

    @Test
    fun missingTargetDegradesToSingleToggle() {
        // 目标本身被屏蔽规则挡掉时一样不能去猜落点。
        assertEquals(
            setOf("a", "zzz"),
            MultiSelectRules.rangeToggle(setOf("a"), "a", "zzz", order),
        )
    }

    @Test
    fun rangeStopsAtTheListEdges() {
        // 锚点 b(1) → 目标 e(4)：a(0) 在区间外，必须原样不动。
        val result = MultiSelectRules.rangeToggle(setOf("b"), "b", "e", order)
        assertEquals(setOf("b", "c", "d", "e"), result)
        assertTrue("区间外/锚点都不该被凭空勾上", "a" !in result)
    }

    @Test
    fun sameRangeLongPressedTwiceIsIdempotentNotAnUndo() {
        // 官方语义下不存在"再长按一次还原"：长按会把锚点挪到本次目标。
        val once = MultiSelectRules.rangeToggle(setOf("a"), "a", "c", order) // {a,b,c}
        val twice = MultiSelectRules.rangeToggle(once, "c", "c", order)
        assertEquals(once, twice)
    }
}
