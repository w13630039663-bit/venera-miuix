package com.venera.compose.gallery.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.SpringSpec
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import com.venera.compose.components.LocalCoverTransitionScopes
import top.yukonga.miuix.kmp.anim.folmeSpring

/**
 * 常驻入口条 ↔ 展开的搜索卡：**同一块面的容器变化**（2026-10-16 第七轮）。
 *
 * ── 它替换掉了什么 ──
 *
 * 从前这一下是「淡出淡入 + 等比缩放」：卡的 `AnimatedVisibility` 挂
 * `fadeIn + scaleIn(initialScale = 0.94f, transformOrigin = 顶边中点)`。那是**近似物** ——
 * `scaleIn` 是等比缩放，而这一下要的是「56dp 的条 → 约 200dp 的卡」这条**竖向独立插值**，
 * `0.94` 对 `56 / 200 = 0.28` 毫无表达力，所以读起来只能是"淡入 + 轻微放大"。
 * 用户 2026-10-16 原话：「我想你另外看看画廊的主页搜索框真的做不了容器变化动画吗，
 * 现在还是淡出淡入的」。
 *
 * ── ⚠️ 一条要钉正的**错结论**（同一轮早先记下的）──
 *
 * 那一轮曾把可行的 API 认成 `sharedBoundsWithCallerManagedVisibility`。**它不是公开 API**：
 * `animation-android-1.12.0-sources.jar` 的 `SharedTransitionScope.kt:1252` 现读，
 * 签名是 `internal fun Modifier.sharedBoundsWithCallerManagedVisibility(...)`。
 * 「编译产物里存在」不等于「本模块能调」—— 这是本轮唯一一处靠读源码救回来的判断。
 *
 * 公开的只有两个：[SharedTransitionScope.sharedBounds]（要一个 `AnimatedVisibilityScope`）
 * 与 `sharedElementWithCallerManagedVisibility`（是 `sharedElement` 语义：只有变成可见的
 * 那一端被渲染、且要求两端"视觉内容一致"，与这里"两端内容完全不同"的场景不匹配）。
 *
 * 所以形状是：**两端各包一层 `AnimatedVisibility`**（入口条那一层是本轮新加的），
 * 两端挂**同一个 key** 的 `sharedBounds`。同 key 跨布局树分支匹配是共享元素的正常用法 ——
 * 入口条在墙的 header 里、卡在页面顶层 Box 里，本来就不在一棵子树上。
 *
 * ── 三个定死了的取舍 ──
 *
 * 1. **`resizeMode = RemeasureToBounds`**（不是默认的 `scaleToBounds`）：这一下宽度几乎不变
 *    （两端同吃 `screenHorizontal` 内缩 ⇒ 逐像素同宽，见 `GallerySearchArea` 里那条注释），
 *    动的只有高度。`scaleToBounds` 会把输入框里那行字与分段器整体缩放，`56 → 200` 这个量级下必糊。
 * 2. **`renderInOverlayDuringTransition = false`**：这一档默认 `true`，而 `true` 会把内容搬进
 *    `SharedTransitionLayout` 的 overlay 去画 —— 那一层在 **NavHost 之外**，而玻璃档的模糊采的是
 *    挂在 NavHost 上的那块 `layerBackdrop`（`Navigation.kt` 的 `contentLayerBackdrop`）。
 *    搬进 overlay 就等于采不到底 ⇒ 玻璃档的卡会褪成一块死色。
 *    代价写在第 3 条里，两条是**成对**的，改一条必须回头看另一条。
 * 3. **入口条那一端的进出场用"位移恒 0"的 slide，不用 fade**：
 *    `renderInOverlayDuringTransition = false` 时共享元素**就地渲染**，父级的 alpha 会乘到飞行体上。
 *    入口条在"展开"这一下是**源**（outgoing），它若按常规 `fadeOut`，飞行体会在 150ms 内被父级
 *    淡到 0，而 bounds 动画要跑 300ms ⇒ 后半程什么都看不见 —— 读起来又回到用户抱怨的那四个字。
 *    `slideInVertically { 0 }` / `slideOutVertically { 0 }` 只借它的**时长**（与 bounds 转场同源），
 *    不产生任何视觉动效；内容的交叉淡变交给 `sharedBounds` 自己的 enter/exit（默认 fadeIn/fadeOut）。
 *
 * ── 只做了一对 ──
 *
 * 「结果态滚到 1/3 收进顶栏那行胶囊」是**另一对**（卡 ↔ 顶栏胶囊），本轮不碰：那一路卡是
 * outgoing 而入口条恒不可见（`svm.active` 仍为真）⇒ 没有配对目标，仍走卡自己的 fade 兜底。
 * 要真做，得给 `GallerySearchCollapsedChips` 也挂同一个 key 的 sharedBounds。
 */

/**
 * 这一下容器变化的响应时间（秒）—— **miuix 顶栏居中标题那一颗 spring**。
 *
 * 与 `.reference/miuix-ui-094/…/basic/TopAppBar.kt:688` 那两颗逐字同源（显示 0.3、隐藏 0.15）：
 * 居中标题淡入与搜索卡收起在**同一帧**触发，曲线不同源的话读起来是"两件事各动各的"。
 *
 * 长高取 0.3：用户点条是要打字，卡"长开"这一下有内容要做，慢一档才读得出是同一块面在变。
 * 缩回取 0.15：收是"我知道了、让开"，比长开快一档才像同一个人做的动作。
 */
internal const val SEARCH_CONTAINER_RESPONSE_SHOW = 0.3f
internal const val SEARCH_CONTAINER_RESPONSE_HIDE = 0.15f

/**
 * 容器变化那两颗曲线（`Float` 给 fade、`IntOffset` 给 slide —— 参数值逐字相同，是同一条弹簧）。
 *
 * 放在这里而不是各处写 `folmeSpring(damping = 1.0f, response = 0.3f)`：**时长必须与
 * [GallerySearchTransition.Bounds] 一致**，否则内容会在 bounds 动画跑完之前被
 * `AnimatedVisibility` 移除，尾部被硬切。两边各写一遍数字就一定会分叉。
 */
internal val SEARCH_CONTAINER_SHOW_SPEC: SpringSpec<Float> =
    folmeSpring(damping = 1.0f, response = SEARCH_CONTAINER_RESPONSE_SHOW)
internal val SEARCH_CONTAINER_HIDE_SPEC: SpringSpec<Float> =
    folmeSpring(damping = 1.0f, response = SEARCH_CONTAINER_RESPONSE_HIDE)
internal val SEARCH_CONTAINER_SHOW_SLIDE_SPEC: SpringSpec<IntOffset> =
    folmeSpring(damping = 1.0f, response = SEARCH_CONTAINER_RESPONSE_SHOW)
internal val SEARCH_CONTAINER_HIDE_SLIDE_SPEC: SpringSpec<IntOffset> =
    folmeSpring(damping = 1.0f, response = SEARCH_CONTAINER_RESPONSE_HIDE)

/**
 * 搜索容器这一对共享元素的 key 与曲线。
 *
 * 单独收一个 object（而不是让两端各写一遍字面量）：key 不一致时共享元素**不报错**，
 * 只退化成"各自淡入淡出" —— 本仓在封面飞行上吃过这一口，所以口径必须只有一个来源。
 */
internal object GallerySearchTransition {

    /**
     * 两端**逐字相同**才能匹配。改它等于把这一对共享元素拆散。
     */
    const val Key = "gallery-search-container"

    /**
     * 容器 bounds 的插值曲线。**按方向选档**（长高 0.3 / 缩回 0.15），与
     * [SEARCH_CONTAINER_RESPONSE_SHOW] 那一组同源。
     *
     * `BoundsTransform` 在 1.12 的签名是 `(initialBounds, targetBounds) -> FiniteAnimationSpec<Rect>`
     * —— 位置与尺寸共用一条 spec，所以只能靠"往哪边变"来选档。
     *
     * `folmeSpring<Rect>` 与 `folmeSpring<Float>` 是同一个函数（`MiuixEasing.kt` 里
     * `fun <T> folmeSpring(...): SpringSpec<T>`），按类型参数各取一份实例。
     */
    val Bounds: BoundsTransform = BoundsTransform { initial, target ->
        val growing = target.height >= initial.height
        folmeSpring<Rect>(
            damping = 1.0f,
            response = if (growing) SEARCH_CONTAINER_RESPONSE_SHOW else SEARCH_CONTAINER_RESPONSE_HIDE,
        )
    }
}

/**
 * 给"搜索容器"的两端（常驻入口条 / 展开的搜索卡）挂上**同一个**共享元素。
 *
 * 读不到作用域的页面（`LocalCoverTransitionScopes` 为 null —— 比如这一屏没被
 * `CoverTransitionHost` 包住）返回**原样**：那时两态各自按自己的 `AnimatedVisibility`
 * 进出场，页面照常工作。转场是增强，不该成为渲染前提
 * （与 `Modifier.coverSharedElement` 同一条纪律）。
 *
 * ⚠️ 作用域只有 `GalleryRoute` 那个导航条目里有（`Navigation.kt` 的 `CoverTransitionHost`），
 * 所以"两端都在画廊首页这一屏内"这条前提成立 —— 它俩本来就在同一屏（一个在墙的 header、
 * 一个在页面顶层 Box），不跨导航条目。
 *
 * @param animatedVisibilityScope 声明这一端的那层 `AnimatedVisibility` 的作用域；
 *   共享元素靠它判断这一端是"进入"还是"退出"（也就是动画的起点与终点）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.gallerySearchContainerElement(
    animatedVisibilityScope: AnimatedVisibilityScope,
): Modifier {
    val scopes = LocalCoverTransitionScopes.current ?: return this
    return with(scopes.shared) {
        this@gallerySearchContainerElement.sharedBounds(
            sharedContentState = rememberSharedContentState(key = GallerySearchTransition.Key),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = GallerySearchTransition.Bounds,
            // 内容要跟着动画 bounds **重新排**（输入框里那行字、分段器那一排），不能整体缩放。
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            // 不进 overlay —— 那一层在 NavHost 之外，玻璃档的模糊背板挂在 NavHost 上（见类头注第 2 条）。
            renderInOverlayDuringTransition = false,
        )
    }
}
