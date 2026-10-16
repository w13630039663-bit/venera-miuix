package com.venera.compose.components.venera

import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.SinkFeedback

/**
 * 「图片面」按压反馈的**唯一出处**（2026-10-16，用户要求「给图廊所有图片都加上按压反馈」）。
 *
 * ## 为什么要有这一颗
 *
 * 在此之前，画廊里能点开的图片**一半没有任何反馈**：`GalleryPostCard` 走的是 miuix
 * `Card(onClick = …)`，而 miuix 那颗 `Card` 的 `pressFeedbackType` **默认就是
 * [PressFeedbackType.None]**（`.reference/miuix-ui-094/…/basic/Card.kt:92` 现读），
 * 按下去屏上纹丝不动 —— 用户读成「这一张点不点得动」。反倒是浏览历史那一格显式挂了
 * `ripple()`，全仓唯一有反馈的图片面。同一面图墙两种手感，比都没有更糟。
 *
 * 这一颗把口径收成一处：**所有图片面按下去都下沉**。
 *
 * ## 为什么是 Sink（下沉），不是 Tilt、也不是涟漪
 *
 *  - **Tilt**（[top.yukonga.miuix.kmp.utils.TiltFeedback]）按触点位置绕 X/Y 轴转 8°。
 *    它是给**单枚立面**（设置项、封面卡那种独占一屏的元素）准备的：瀑布流里一屏十几张，
 *    每张各按触点朝不同方向歪，相邻两格立刻错开，读起来是"页面在抖"而不是"我按住了这一张"。
 *  - **涟漪**（material3 的 `ripple()`）是画在**文字面 / 列表行**上的语言。压在照片缩略图上时，
 *    那一圈半透明色会盖住用户正在看的画面本身；而这面墙上"图"就是全部内容。
 *  - **Sink** 只做几何：整块等比缩到 0.94，不改色、不加层、不遮住任何像素。图片面要的正是这个 ——
 *    反馈要说的是"我收到了"，不是"看我表演"。
 *
 * ## 两条路，同一个 miuix 原语
 *
 * miuix 把同一件事开成两个口子，本仓两条都要用（用哪条由**那个可点面是不是 miuix `Card`** 决定）：
 *
 *  - [card] —— `Card(pressFeedbackType = …)` / 经 [VeneraCard] 透传的那条；
 *  - [indication] —— `combinedClickable` / `clickable` / `pressable` 那条（非 miuix `Card` 的可点面）。
 *
 * 两条**不是两套参数**：miuix 内部 `PressFeedbackType.Sink -> SinkFeedback()`
 * （`…/basic/Card.kt:105-111` 现读），取的就是 [indication] 这一颗的默认参数
 * （`sinkAmount = 0.94f`、`spring(0.8f, 600f)`）。所以两条路上屏的位移、曲线、时长逐字一致，
 * 改这里一处就是全画廊一起改。这条等价关系由 `VeneraPressFeedbackTest` 钉住
 * （白盒读 miuix 源码那两行，而不是"文件里出现过这两个符号"）。
 *
 * ## 与 Chip 那条口径的关系（不是同一档，也不该是）
 *
 * [com.venera.compose.ui.tokens.VeneraTokenSet.chipPressedScale]（0.96 / alpha 0.88）是
 * **胶囊**那一档：小元素缩得太狠会读成"抖了一下"，所以它退一步只缩 4%，用 alpha 补足力度。
 * 图片卡有半屏宽，同样是 0.96 只挪几个 dp，按下去几乎看不出；0.94 在这个尺度上才与胶囊
 * "按下去有反应"的强度相当。两档各管各的尺寸，沿用 miuix 给卡面调好的那个值，不另造数字。
 */
object VeneraPressFeedback {

    /**
     * miuix `Card(pressFeedbackType = …)` 那条路 —— 也经 [VeneraCard] 的 `pressFeedback` 透传。
     *
     * ⚠️ 挂上它之后，miuix 的 `pressable` 是**无条件**接上的（`Card.kt:113` 现读：
     * `if (pressFeedback != null) interactionSource else null`），与"有没有传 onClick"无关。
     * 所以"这一块此刻不可点"的场景（例如以图搜图那一行没找到我方条目时）必须**显式退回**
     * [PressFeedbackType.None]，否则会出现"按得动、什么都不发生"的假开关。
     */
    val card: PressFeedbackType = PressFeedbackType.Sink

    /**
     * `combinedClickable` / `clickable` / `pressable` 那条路（非 miuix `Card` 的可点面）。
     *
     * 与 [card] 是同一个原语的两种递法（理由见类头注）。
     */
    val indication: SinkFeedback = SinkFeedback()
}
