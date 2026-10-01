package com.venera.compose.components.selection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 多选状态机的契约。
 *
 * 这里守的是四条"一写错就变成死状态或幽灵勾选"的规则：
 * 1. 选中集为空 ⇒ 必须自动退出多选（否则留下一条"已选择 0 项"的工具条，没有出口）；
 * 2. 锚点必须随每次交互移动，区间选才有连续的起点；
 * 3. 列表刷新后 `retainAll` 必须把已经不存在的选中项剔掉；
 * 4. `clearSelection` 只清选中、不退出多选（官方菜单里 "Deselect" 与退出是两个动作）。
 */
class MultiSelectStateTest {

    private val order = listOf("a", "b", "c", "d")

    @Test
    fun enterActivatesAndSelectsThePressedItem() {
        val s = MultiSelectState<String>()
        s.enter("a")
        assertTrue(s.active)
        assertEquals(setOf("a"), s.selected)
        assertEquals(1, s.count)
        assertTrue(s.contains("a"))
    }

    @Test
    fun toggleAddsThenRemoves() {
        val s = MultiSelectState<String>()
        s.enter("a")
        s.toggle("b")
        assertEquals(setOf("a", "b"), s.selected)
        s.toggle("a")
        assertEquals(setOf("b"), s.selected)
        assertTrue("还剩一项，不该退出多选", s.active)
    }

    @Test
    fun deselectingTheLastItemExitsMultiSelect() {
        val s = MultiSelectState<String>()
        s.enter("a")
        s.toggle("a")
        assertEquals(emptySet<String>(), s.selected)
        assertFalse("已选择 0 项不该还挂着工具条", s.active)
    }

    @Test
    fun rangeTogglesFromTheLastInteractedAnchor() {
        val s = MultiSelectState<String>()
        s.enter("a")            // 锚点 = a
        s.toggleRange("c", order) // a→c：b、c 被反选
        assertEquals(setOf("a", "b", "c"), s.selected)

        // 锚点已随上一次交互移到 c(2)：再长按 a(0) 走区间 0..2，跳过 c 自己
        // ⇒ a 与 b 因为本来就在选中里，这次被取消。
        s.toggleRange("a", order)
        assertEquals(setOf("c"), s.selected)
    }

    @Test
    fun rangeThatEmptiesTheSelectionAlsoExitsMultiSelect() {
        val s = MultiSelectState<String>()
        s.enter("b")            // {b}，锚点 b
        s.toggle("a")           // {a,b}，锚点 a
        s.toggle("a")           // {b}，锚点仍是 a
        assertEquals(setOf("b"), s.selected)

        // 锚点 a(0) → 目标 b(1)：区间只有 b 一个非锚点项，它是选中态 ⇒ 被取消 ⇒ 空集。
        s.toggleRange("b", order)
        assertEquals(emptySet<String>(), s.selected)
        assertFalse("区间反选把最后一项也取消掉时，必须自动退出多选", s.active)
    }

    @Test
    fun selectAllDoesNotMoveTheAnchor() {
        val s = MultiSelectState<String>()
        s.enter("a")            // 锚点 a
        s.toggle("b")           // 锚点 b
        s.selectAll(order)
        assertEquals(order.toSet(), s.selected)

        // 锚点仍停在 b(1)：长按 d(3) 走区间 1..3，跳过 b ⇒ c 与 d 被取消。
        // （官方 selectAll 不碰 lastSelectedIndex，这里必须一致 —— 否则全选后第一次长按
        //   会从表尾拉出一段莫名其妙的区间。）
        s.toggleRange("d", order)
        assertEquals(setOf("a", "b"), s.selected)
    }

    @Test
    fun invertFlipsTheWholeListAndExitsWhenNothingIsLeft() {
        val s = MultiSelectState<String>()
        s.enter("a")
        s.toggle("b")
        s.invert(order)                  // {a,b} → {c,d}
        assertEquals(setOf("c", "d"), s.selected)
        assertTrue(s.active)

        s.invert(order)                  // 再反选回来
        assertEquals(setOf("a", "b"), s.selected)

        s.selectAll(order)
        s.invert(order)                  // 全选再反选 = 空集
        assertEquals(emptySet<String>(), s.selected)
        assertFalse("反选成空必须自动退出，不留'已选择 0 项'的死状态", s.active)
    }

    @Test
    fun retainAllDropsItemsThatLeftTheList() {
        val s = MultiSelectState<String>()
        s.enter("a")
        s.toggle("c")
        assertEquals(setOf("a", "c"), s.selected)

        // 换了个收藏夹：只剩 a、b
        s.retainAll(listOf("a", "b"))
        assertEquals(setOf("a"), s.selected)
        assertTrue(s.active)
    }

    @Test
    fun retainAllExitsWhenNothingSelectedSurvives() {
        val s = MultiSelectState<String>()
        s.enter("c")
        s.retainAll(listOf("a", "b"))
        assertEquals(emptySet<String>(), s.selected)
        assertFalse("选中项全被剔掉后必须自动退出多选", s.active)
    }

    @Test
    fun retainAllOnUntouchedListKeepsEverything() {
        val s = MultiSelectState<String>()
        s.enter("a")
        s.toggle("b")
        s.retainAll(order)
        assertEquals(setOf("a", "b"), s.selected)
        assertTrue(s.active)
    }

    @Test
    fun exitResetsEverythingIncludingTheAnchor() {
        val s = MultiSelectState<String>()
        s.enter("a")
        s.toggle("b")
        s.exit()
        assertFalse(s.active)
        assertEquals(emptySet<String>(), s.selected)
        assertEquals(0, s.count)

        // 退出后锚点必须清掉：否则下次进多选长按会拿一个陈旧的起点去拉区间。
        s.enter("d")
        assertEquals(setOf("d"), s.selected)
        s.toggleRange("a", order)
        // 锚点 d(3) → 目标 a(0)：b、c、a 被反选，d 保持
        assertEquals(setOf("d", "a", "b", "c"), s.selected)
    }
}
