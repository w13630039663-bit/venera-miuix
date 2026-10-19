package com.venera.compose.components.venera

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 横向可滚动容器右缘那道**渐隐**（基础层，不含任何业务语义）。
 *
 * ── 它修的是什么 ──
 *
 * 2026-10-14 手机档截图量到三处同一件事：源药丸行、收藏页的文件夹行、详情页的分组 tabs
 * 排到"刚好满 / 略微超出"那一档时，**最后一枚被切成一半**（收藏页那回 `nhentai` 被裁成
 * `● nl`）。横向容器在这一点上最难读 —— 屏上没有任何东西说"这一行还能往右滑"，
 * 用户只会以为自己那一枚不存在。
 *
 * 解法两半，缺一半都不算修好：
 * 1. [hScrollEndFade]：右缘一道从透明到 [container] 的渐隐 —— "还有东西在屏幕外"这件事
 *    第一次有了视觉证据；
 * 2. [hScrollEndContentPadding]：尾部那截内容内边距 —— 滑到**底**的时候，末枚要能整个
 *    退出渐隐区。只加渐隐不加这一截，滑到底末枚仍然压在渐隐底下，等于把"被裁"从
 *    中途挪到了终点。
 *
 * ⚠️ [container] **必填、没有默认值**：渐隐吃的是"这一行底下是什么颜色"。
 * 页面底色与卡片底色不是一回事（详情页的「相关推荐」那一排就坐在 `VeneraCard` 面上），
 * 给一个默认色就等于让新调用点静默长成一道错色的硬边。
 *
 * ⚠️ 挂在 `horizontalScroll` / `LazyRow` **之后**（链上更靠外的一侧）：
 * `drawWithContent` 画的是它右边那颗节点的产物，所以渐隐固定在视口上、跟着内容一起滚的是字。
 *
 * @param container 这一行底下的那块颜色（页面底 / 卡面，由调用方按实际坐的地方递进来）。
 * @param width 渐隐带宽。默认 [VeneraTokens.spacing.space8]（16dp）—— 与屏上左右内边距同档，
 *   再宽就会把末枚完整露出来的那一档也糊掉。
 */
@Composable
fun Modifier.hScrollEndFade(
    container: Color,
    width: Dp = VeneraTokens.spacing.space8,
): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx().coerceAtMost(size.width)
    if (w <= 0f) return@drawWithContent
    // 渐隐的两端点写在**节点坐标**里（画刷不跟着 `topLeft` 走）：
    // 省掉 startX/endX 会得到一对重合端点，那在 Compose 里是退化的线性渐变 —— 读起来就是一块实色。
    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            1f to container,
            startX = size.width - w,
            endX = size.width,
        ),
        topLeft = Offset(size.width - w, 0f),
        size = Size(w, size.height),
    )
}

/** 与 [hScrollEndFade] 配对的那截尾部内容内边距（理由见那颗的头注第 2 条）。 */
@Composable
fun hScrollEndContentPadding(width: Dp = VeneraTokens.spacing.space8): PaddingValues =
    PaddingValues(end = width)

/**
 * [hScrollEndContentPadding] 的 **`Row + horizontalScroll` 版**（2026-10-17 第三十轮）。
 *
 * `Row` 没有 `contentPadding` 参数（那是懒容器独有的）⇒ "滑到底时末枚整个退出渐隐区"
 * 这件事在裸横滑行上只能靠**末尾一枚等宽占位件**做到。画廊首页「每日热门」那一排
 * （`GalleryPosterRow`）与它的骨架（`GalleryRowSkeleton`）正是这种形状：一张白卡里
 * 一条裸 `Row`，卡片定宽、全量摆不贵（见那边"为什么用 Row 而不是 LazyRow"的注）。
 *
 * 宽度默认与渐隐带宽同档 [VeneraTokens.spacing.space8]：两处从**同一个默认值**走，
 * 改带宽时不会只改一半（"渐隐与尾部内衬成对"那条纪律的 Row 形态）。
 *
 * ⚠️ 它是**内容**不是 Modifier —— 要摆在横滑行的**最后一格**，与 [hScrollEndFade]
 * （挂在容器 modifier 上）各司其职：一个让末枚退得出渐隐区，一个让"还能往右滑"看得见。
 */
@Composable
fun HScrollEndSpacer(width: Dp = VeneraTokens.spacing.space8) {
    Spacer(modifier = Modifier.width(width))
}

/**
 * 横向可滚动容器**左缘**那道渐隐 —— [hScrollEndFade] 的左半边（2026-10-17 第二十一轮）。
 *
 * ## 为什么左端要单独一颗，而不是把 [hScrollEndFade] 改成两端一起画
 *
 * 右端那道**恒亮**是对的：只要还有内容在屏幕外，「还能往右滑」就该有视觉证据。
 * 左端不是 —— 滑到起点时左侧根本没有内容被裁，这时候画一道渐隐，等于把第一位头像的
 * 左缘（以及卡面内衬）白白糊掉。所以左端必须**跟着滚动位置**：离开起点才淡入、
 * 回到起点就淡出。
 *
 * ## 为什么 `visible` 收的是 `State<Float>` 而不是 `Float`
 *
 * [drawWithContent] 的 lambda 在**绘制阶段**执行。在绘制期读 `state.value`，Compose 会把
 * 这次绘制登记成该 state 的订阅者 —— 于是透明度每帧变时**只重绘、不重组**。
 * 换成 `Float` 参数就是每帧把新 Modifier 递给 `LazyRow`（连带重组它的宿主），
 * 而 fling 那一秒里要跑 60 次。同一条纪律见记忆里「`tokens.*` 是 @Composable getter
 * ⇒ 绘制期读不到，组合期取局部量」那条的反面用法：**该在绘制期读的，就别在组合期读**。
 *
 * ## 左端不需要配对的内容内边距
 *
 * 右端那截 [hScrollEndContentPadding] 是为了「滑到底时末枚能整个退出渐隐区」。
 * 左端没有这件事：起点时 `visible = 0`，渐隐本身就不存在。
 *
 * @param container 这一行底下的那块颜色（与右端那颗同一个 `container`，否则两端色不一样）。
 * @param visible 0..1 的可见度，由调用方按滚动位置驱动（`animateFloatAsState` 的 `State` 直接传进来）。
 * @param width 渐隐带宽，默认与右端同档 [VeneraTokens.spacing.space8]。
 */
@Composable
fun Modifier.hScrollStartFade(
    container: Color,
    visible: State<Float>,
    width: Dp = VeneraTokens.spacing.space8,
): Modifier = drawWithContent {
    drawContent()
    val alpha = visible.value.coerceIn(0f, 1f)
    if (alpha <= 0f) return@drawWithContent
    val w = width.toPx().coerceAtMost(size.width)
    if (w <= 0f) return@drawWithContent
    drawRect(
        // 两端点同样写在**节点坐标**里（与右端那颗对称）：`startX` 已经在节点左缘，
        // 仍显式给出，省掉它就会退化成一对重合端点 = 一块实色。
        brush = Brush.horizontalGradient(
            0f to container.copy(alpha = container.alpha * alpha),
            1f to Color.Transparent,
            startX = 0f,
            endX = w,
        ),
        topLeft = Offset.Zero,
        size = Size(w, size.height),
    )
}
