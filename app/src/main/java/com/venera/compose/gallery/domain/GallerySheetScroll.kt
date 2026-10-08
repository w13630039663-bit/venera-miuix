package com.venera.compose.gallery.domain

/**
 * 半模态内容区**滚动位移的去向**：内容区吃不完的那一份，哪些扣在自己这儿、哪些放给面板。
 *
 * ## 当前职责
 *
 * 这里保留 M3 嵌套滚动链的残余位移判据：只限制内容区已经到边界后，仍会继续传给面板的部分。
 * 它不是高度稳定方案。鬼畜复现的最新设备日志显示，原来的 `contentAtBottom` 条件
 * 没有扣住实际向下面板交接的那份位移；稳定面板锚点由 `GalleryInfoSheet` 的固定总高度负责。
 *
 * ## 为什么两种位移必须分开处理
 *
 * - **手指还在屏上的拖拽**：面板必须拿到。用户就是靠"在内容上往下拖"把面板拖下去关掉的，
 *   这一份被扣下就等于把下滑关闭封死 —— 那正是"下拉不下来"里最糟的一种。
 * - **手指离屏之后的惯性**：面板**一步都不该拿到**。它是上面那个环的唯一入口 ——
 *   一次甩动就能把面板推离锚点，而面板离锚点之后又会改变内容的可用高度。
 *   扣掉它，用户的滑动体验一点不损（内容自己已经滚到位了），
 *   面板则回到"只有手指按着才动"这个干净的规则上。
 *
 * ## ⚠️ 返回值是"**我吃掉了多少**"，不是"我放了多少"
 *
 * `NestedScrollConnection` 的约定：`onPostScroll` / `onPostFling` 的返回值是**本层消费掉的量**，
 * 剩下的才会继续交给祖先（这里就是那张 sheet）。所以"不给面板"要返回**全部**，
 * "全给面板"要返回 0 —— 与直觉相反，写反的后果是这次的修复完全失效而屏上看不出异常
 * （拖动那一侧则会变成"拖不动面板"）。判据抽在这里、并且带单测，就是为了让写反这件事红一次。
 */
object GallerySheetScroll {

    /**
     * 拖动（`NestedScrollSource.Drag` / `UserInput`）吃不完的那一份纵向位移 —— **要不要扣住**。
     *
     * ## 2026-10-05 诊断判据：在旧的近全屏高度下尝试限制已到底时的残余拖动
     *
     * 旧版恒返回 0（全放行），理由是「手指按着往下拖 = 用户在用这个手势关面板，一份都不能扣」。
     * 那条理由**只在面板没顶格时成立**。用户 2026-10-05 报的帧级上下跳有三种条件同时成立：
     *
     * 「面板**已 Expanded** + 标签全展导致**内容滚到底** + **在内容区（非把手）下拉**」。
     *
     * 旧判据假设内容区已经吃不下任何位移，这一份顺着嵌套滚动交给面板。而 m3 自己的
     * `ConsumeSwipeWithinBottomSheetBoundsNestedScrollConnection.onPostScroll`
     * （`SheetDefaults.kt:655-656`）对 UserInput **无条件** `dispatchRawDelta(available)` ——
     * 面板于是被**逐帧**推动。面板一动就触发 m3 的 `draggableAnchors` 重算挡
     * （`BottomSheet.kt:293`），而那里的 `Expanded` 锚点是 `fullHeight − sheetSize.height`
     * （`:306`）—— **依赖内容量出的高**。锚点变 ⇒ `dragTarget` 被反复置/清 ⇒ 面板帧间来回。
     * 「间隔不到几帧」就是从这来的。
     *
     * ## 这条判据的限制
     *
     * 设备日志已证明连接器处在祖先位，但这个按**实时滚动值**切换的条件会漏过底部到顶部的连续手势：
     * 内容回到 `value == 0` 后 `contentAtBottom` 已为 false，正向剩余量于是继续交给 M3。
     * 该判据仅作旧实验记录；当前面板稳定性不再依赖它。
     *
     * @param availableY 本层吃不完、下一步本该交给面板的纵向位移（**向下为正**）。
     * @param panelExpanded 面板当前是否已是 `Expanded` 档。传 `false` 一律放行。
     * @param contentAtBottom 内容区是否已滚到最底（`scrollState.value >= scrollState.maxValue`）。
     *   传 `false` 一律放行。
     * @return **本层消费掉的量**（嵌套滚动约定，与「放了多少」相反）。
     */
    fun consumedFromDrag(availableY: Float, panelExpanded: Boolean, contentAtBottom: Boolean): Float =
        if (panelExpanded && contentAtBottom) availableY else 0f

    /**
     * 惯性吃不完的那一份 —— **全扣**，两个方向都扣。
     *
     * 用在两处：`onPostScroll` 里 `source` 是抛的那一份位移（含它自己的回弹），
     * 以及 `onPostFling` 里那笔**残余速度**（它是这个环里"跑得最久"的一份，只挡位移会漏掉一半）。
     * 上滑到底后的那次甩动（负向）是抖动最常见的起点，回弹那一下（正向）同样会把面板推离锚点，
     * 所以这里不看正负、也不看大小：一份都不给。
     */
    fun consumedFromFling(availableY: Float): Float = availableY
}
