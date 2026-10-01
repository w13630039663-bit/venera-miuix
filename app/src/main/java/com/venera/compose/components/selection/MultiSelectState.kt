package com.venera.compose.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 收藏页**四处**（本地收藏 / 图片收藏的插图 / 网络收藏 / 画廊收藏）共用的多选状态机。
 *
 * ## 为什么要抽出这一份
 *
 * 抽之前四处各写一套，行为已经分叉了：本地收藏长按直接进多选、图片收藏长按弹菜单；
 * 选中标记一处是右上角勾、一处是右下角勾；一处有"全选"、一处连入口都没有。
 * 同一件事在同一个页面里有两三种做法，用户学到的"长按会发生什么"就永远不成立。
 *
 * ## 参数 T 是什么
 *
 * 由各面板自己定，只要**在同一面墙内唯一**：
 * 本地收藏是 `(id, type)`、插图收藏是 `Long`、网络收藏是 `(sourceKey, id)`、
 * 画廊是 `uid`（带站键）。状态机本身不认识业务，只做集合运算。
 *
 * ## 长按 = 进多选 / 已在多选态时 = 区间选
 *
 * 这是官方 `local_favorites_page.dart:700-760` 的原始语义，两处差别很大：
 * - **不在多选态**：进入多选，并选中被长按的那一项；
 * - **已在多选态**：从**上一次点的位置**到这次点的位置，把区间内每一项**逐个反选**
 *   （含两端，跳过锚点自己）—— 所以同一段区间连按两次会还原。
 *
 * 区间选这一步被抽到 [MultiSelectRules.rangeToggle] 里做纯函数，逻辑有单测兜。
 */
@Stable
class MultiSelectState<T> {

    /** 是否处于多选态。进入后卡片上才摆选择框、才拦点击。 */
    var active by mutableStateOf(false)
        private set

    /** 已选集合。空集与 [active] 的关系由本类自己维持（空 ⇒ 退出），调用方不用管。 */
    var selected by mutableStateOf<Set<T>>(emptySet())
        private set

    /**
     * 区间选的锚点：最近一次被点击 / 长按的那一项。
     *
     * 刻意**不做成 State**：它只服务下一次长按的计算，变了不需要重组。
     */
    private var anchor: T? = null

    val count: Int get() = selected.size

    fun contains(item: T): Boolean = item in selected

    /** 长按一项：进入多选并选中它。 */
    fun enter(item: T) {
        active = true
        selected = selected + item
        anchor = item
    }

    /**
     * 点击一项（多选态内）：切换它的选中状态。
     *
     * 选中数归零时**自动退出多选** —— 官方 `_checkExitSelectMode()` 同一条：
     * 一个都没选却还挂着"已选择 0 项"的工具条，是个没有出口的死状态。
     */
    fun toggle(item: T) {
        selected = if (item in selected) selected - item else selected + item
        anchor = item
        if (selected.isEmpty()) active = false
    }

    /**
     * 长按一项（多选态内）：区间反选。
     *
     * @param order 当前墙上**可见**条目的顺序。必须是可见的那一批 ——
     *   拿被屏蔽规则挡下的条目参与区间，会出现"长按一下，屏幕上跳出一堆没见过的选中"。
     */
    fun toggleRange(item: T, order: List<T>) {
        selected = MultiSelectRules.rangeToggle(selected, anchor, item, order)
        anchor = item
        if (selected.isEmpty()) active = false
    }

    /**
     * 全选当前列表。
     *
     * ⚠️ **不动锚点**：官方 `selectAll`（`local_favorites_page.dart:204-212`）只重写选中集合，
     * `lastSelectedIndex` 原样保留 ⇒ 全选之后长按仍从"上一次真正点过的那一项"起算。
     * 这里若擅自把锚点挪到表尾，全选后的第一次长按就会拉出一段莫名其妙的区间。
     */
    fun selectAll(all: List<T>) {
        selected = all.toSet()
    }

    /**
     * 反选（官方菜单里的 "Invert Selection"，`local_favorites_page.dart:214-233`）。
     *
     * 同样**不动锚点**。官方这一个入口漏调了 `_checkExitSelectMode()`，
     * 于是"反选成空"会留下一条"已选择 0 项"的工具条；我们统一收敛为退出，
     * 与本类其余入口保持同一条不变量。
     */
    fun invert(all: List<T>) {
        selected = all.filterNot { it in selected }.toSet()
        if (selected.isEmpty()) active = false
    }

    /**
     * 列表变了之后收敛选中集合：把**已经不在列表里**的项剔掉。
     *
     * 为什么必须做：选中项可能已被删掉（另一条入口删的、导入覆盖的、外站同步掉的），
     * 列表刷新后若还留着它们，工具条会报一个"已选择 3 项"而屏上只看得见 2 个勾 ——
     * 那第 3 项点了删除也删不掉任何东西（它本来就不在数据里了）。
     *
     * 收敛后一个不剩就自动退出多选：空列表配一条"已选择 0 项"的工具条是个没有出口的死状态。
     */
    fun retainAll(all: List<T>) {
        val allowed = all.toSet()
        val kept = selected.filter { it in allowed }.toSet()
        if (kept.size != selected.size) {
            selected = kept
        }
        if (selected.isEmpty()) active = false
    }

    /**
     * 退出多选并清空选中。
     *
     * 对应官方菜单里的 "Deselect"（`_cancel()`，`local_favorites_page.dart:932-937`）——
     * 官方那一个动作就是"清空 + 退出"，没有"只清空、不退出"的入口，
     * 所以这里也不提供 [clearSelection] 那种半吊子状态。
     */
    fun exit() {
        active = false
        selected = emptySet()
        anchor = null
    }
}

@Composable
fun <T> rememberMultiSelectState(): MultiSelectState<T> = remember { MultiSelectState() }

