package com.venera.compose.components.venera

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
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
