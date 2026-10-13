package com.venera.compose.reader

import com.venera.compose.data.platform.PreferenceKeys

/**
 * 巡航在「本话已经到底」那一刻该做什么。
 *
 * ── 为什么要有这一层 ──
 *
 * 改动前两个分支各写一遍"到底就停"：翻页档是 `if (!turnToNextPage()) isAutoScrolling = false`，
 * 条漫档更糟 —— 它连"到底"都不判，只每帧 `scrollBy`，到末尾后 `scrollBy` 恒返回 0，
 * 于是**巡航在跑、屏上不动**（一种既不停也不前进的静默假象）。
 *
 * 把"到底之后干什么"收成这一枚枚举，两个分支读同一个答案，也就不会再一次分叉。
 */
internal enum class ReaderAutoStep {
    /** 还没到底，或者正在换话 —— 什么都不用做，接着推。 */
    Continue,

    /** 本话到底了，而且后面还有话：切过去继续。 */
    NextChapter,

    /** 本话到底、后面没有了（或用户把续章关了）：巡航到此为止。 */
    Stop,
}

/**
 * 本话到底那一刻的判据。
 *
 * @param hasNextChapter 后面还有没有话，**且**用户允许续章（`pref_reader_auto_next_chapter`）。
 *   把"用户关掉了续章"折进这一个参数而不是另开一个分支：对巡航来说那两件事的结果完全一样
 *   （就是不走），合成一个参数才不会出现"关了续章但还切了一话"这种只有一边改对的漏法。
 * @param chapterLoading 正在取下一话的页表。
 *   **它优先于 [hasNextChapter]**：换话是要联网的，取页表期间巡航若照常判"到底"，
 *   会连着触发好几次 `switchToChapter` —— 而 `switchToChapter` 自己有 `isChapterLoading`
 *   守卫，那几次全被吞掉，屏上表现为"卡在末页好几拍才动"。
 */
internal fun autoStepAtChapterEnd(hasNextChapter: Boolean, chapterLoading: Boolean): ReaderAutoStep = when {
    chapterLoading -> ReaderAutoStep.Continue
    hasNextChapter -> ReaderAutoStep.NextChapter
    else -> ReaderAutoStep.Stop
}

/**
 * 这一档阅读模式支不支持自动巡航。
 *
 * 抽出来只为一件事：**面板上那枚按钮的可用态**与**巡航协程自己的分支**从前各写一份三连判断
 * （面板 `:966-968`，协程 `:537-573`）。两处一旦漂，就会出现"按钮亮着、按下去协程立刻退出"
 * 那类假开关。现在两处读同一个函数，加档时编译器会在 `when` 上逼人表态。
 *
 * 横向连续流与双页档**不参与**（改动前就不参与，本次不扩）：
 * 前者没有稳定的"一屏一页"节奏，后者的翻页步长是 2，与"每 N 秒翻一页"对不齐。
 */
internal fun autoCruiseSupported(mode: ReaderReadingMode): Boolean = when (mode) {
    ReaderReadingMode.VERTICAL_CONTINUOUS -> true
    ReaderReadingMode.HORIZONTAL_LTR -> true
    ReaderReadingMode.HORIZONTAL_RTL -> true
    ReaderReadingMode.HORIZONTAL_CONTINUOUS -> false
    ReaderReadingMode.DOUBLE_PAGE -> false
}

/**
 * 自动翻页间隔的步进 —— 阅读器面板里那两枚 −/+ 用（2026-10-13 用户点名"加上时间控制 1-10s"）。
 *
 * 夹取写在**这一颗**而不是写在点击回调里：回调里各夹一遍迟早与写入钳位那份分叉，
 * 而分叉出来的屏上表现是"按到顶了还能再按"（假开关那一族）。
 * 区间本身只有一处定义：[PreferenceKeys.AUTO_PAGE_INTERVAL_SEC_RANGE]，
 * 设置页那条滑块与 prefs 的写入钳位读的都是它。
 */
internal fun nextAutoPageIntervalSec(current: Float, deltaSec: Int): Float =
    (current + deltaSec).coerceIn(PreferenceKeys.AUTO_PAGE_INTERVAL_SEC_RANGE)
