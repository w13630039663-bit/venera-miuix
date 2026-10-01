package com.venera.compose.components.selection

/**
 * 收藏页多选的**判据**（纯函数，零 Compose 依赖 —— 没有界面、没有设备也能跑）。
 *
 * 单独成文件是为了让它能进 `_probe/l0/run-judgment-tests.sh` 的独立跑测清单：
 * [MultiSelectState] 带着 `mutableStateOf` / `@Composable`，脱离 gradle 编不了，
 * 而这里这条区间反选的算法是整块功能里**最值得钉死**的一处。
 */
object MultiSelectRules {

    /**
     * 区间反选：把 `order` 里 anchor 与 target 之间（**含两端，跳过 anchor 自己**）
     * 的每一项逐个 toggle，返回新的选中集合。
     *
     * 逐条对齐官方 `local_favorites_page.dart:737-757`：
     * - 两端按下标排序后再遍历（向上长按与向下长按结果一致）；
     * - `continue` 跳过的是**锚点自己**（官方比的是 `lastSelectedIndex`，即锚点下标），
     *   所以锚点这一项在区间内**永远保持原状**；
     * - 区间内其余项一律**反选**而不是"全选"：被区间裹住的已选项会被取消，
     *   只有**区间两端之外**的项不受影响。
     *
     * ⚠️ 官方**没有**"再长按一次同一位置即撤销"的性质 —— 长按结束会把锚点挪到本次目标，
     * 下一次同一位置的区间只剩它自己（被跳过），因此什么都不变。别把这条当成可用的撤销。
     *
     * 锚点或目标不在当前 `order` 里（换了收藏夹、改了搜索词、屏蔽规则把锚点收走了）时
     * **退化为单点 toggle**。官方此处会拿一个陈旧的 int 下标直接去 `comics[i]`，
     * 越界即抛 RangeError —— 我们刻意不复刻这个崩溃路径。
     */
    fun <T> rangeToggle(selected: Set<T>, anchor: T?, target: T, order: List<T>): Set<T> {
        val anchorIndex = anchor?.let { order.indexOf(it) } ?: -1
        val targetIndex = order.indexOf(target)
        if (anchorIndex < 0 || targetIndex < 0) {
            return if (target in selected) selected - target else selected + target
        }
        val from = minOf(anchorIndex, targetIndex)
        val to = maxOf(anchorIndex, targetIndex)
        var result = selected
        for (i in from..to) {
            if (i == anchorIndex) continue
            val item = order[i]
            result = if (item in result) result - item else result + item
        }
        return result
    }
}
