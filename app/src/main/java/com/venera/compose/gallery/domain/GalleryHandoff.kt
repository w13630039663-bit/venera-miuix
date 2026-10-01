package com.venera.compose.gallery.domain

/**
 * 大图页入场"交棒"那一段的透明度算式 —— **飞行体落位之后**才走的那一档。
 *
 * ## 它解决的问题（2026-10-01 第四次报"打开图片还是闪一下"，录屏逐帧量出来的）
 *
 * 老写法把**页面的淡入**挂在去程那条弹簧的百分比上（`fly ≥ 0.75` 起淡），同时让飞行体
 * 在同一个窗口里淡出。它假设"这一段里飞行体已经压在落点那一框上"—— 而弹簧的**百分比不是路程**：
 * `fly=0.75` 时飞行体还在**路程的 75%**，离落点约 130px、面积小 13%。
 *
 * 于是同一张图同时有两份可见：页面那份在终点、飞行体那份在半路。两层 alpha 都是 0.5 时，
 * 屏上就是**两个错位的半透明副本**（重影）。录屏读数：回看"与最终稳定帧的差"在
 * `fly=0.875`（交叉中点）最大 —— 与这条算式逐位吻合。
 *
 * 更糟的是总不透明度：飞行体画在**页面之上**，两层叠起来的覆盖是
 * `a_body + (1 - a_body) · a_page`。取 `a_page = f`、`a_body = 1 - f` 时它等于 `1 - f + f²`，
 * 在 `f = 0.5` 处掉到 **0.75** —— 图会先**暗一下**再回来（黑色幕布从下面透上来）。
 * 这两条合起来，用户看到的就是"**图片本身闪一下**"。
 *
 * ## 现在的口径
 *
 * 交棒**不与飞行重叠**：飞行全程不淡任何东西（飞行体就是不透明的起点像素），
 * 落位之后才用 [HANDOFF_PAGE_FRACTION] 把这一档切成互不重叠的两半 ——
 *
 * - 前半程（`progress ∈ [0, 0.5]`）：页面 alpha `0 → 1`，飞行体**恒为 1**。
 *   图那一块被不透明的飞行体盖着，看不出页面在下面淡入；只有 chrome 与四周在渐显。
 * - 后半程（`progress ∈ [0.5, 1]`）：页面**恒为 1**，飞行体 `1 → 0`。
 *   此刻页面已经不透明，淡的只是"糊 → 清晰"，总不透明度恒为 1。
 *
 * [handoffCoverage] 把"两层叠起来的不透明度"写成算式，[GalleryHandoffTest] 用扫描把它钉在 1 上 ——
 * 这一条就是本轮真正要防回归的东西：**任何"两层同时半透明"的写法都会让它掉下 1**。
 */

/** 交棒里"页面淡到位"所占的比例，剩下那一半留给飞行体淡出。两者**不能重叠**。 */
const val HANDOFF_PAGE_FRACTION = 0.5f

/** 交棒第 [progress] 成（0..1）时，**页面**（含 chrome）的透明度。 */
fun handoffPageAlpha(progress: Float): Float =
    (progress / HANDOFF_PAGE_FRACTION).coerceIn(0f, 1f)

/** 交棒第 [progress] 成时，**飞行体**的透明度。它画在页面之上。 */
fun handoffBodyAlpha(progress: Float): Float =
    ((1f - progress) / (1f - HANDOFF_PAGE_FRACTION)).coerceIn(0f, 1f)

/**
 * 两层叠起来之后的**总不透明度**（飞行体画在页面之上）。
 *
 * 它必须恒为 1：低于 1 就意味着后面那层压暗的幕布能从图里透上来 —— 那就是"图暗一下"。
 */
fun handoffCoverage(progress: Float): Float {
    val body = handoffBodyAlpha(progress)
    return body + (1f - body) * handoffPageAlpha(progress)
}
