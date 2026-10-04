package com.venera.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * 截"起点那一帧"的那几帧里，**压在内容之上的自有 chrome 让不让位**。
 *
 * ## 为什么需要它（2026-10-04 真机图证）
 *
 * `GalleryFlyIn.capture` 走 `PixelCopy`，而 PixelCopy 读的是**窗口已经合成好的表面** ——
 * 它不知道"这一块的像素属于卡片还是属于压在上面的底栏"。所以一张下沿被底栏压住的卡，
 * 截出来的飞行体里就横着一条底栏（用户图证里能清楚认出「首页」「收藏」两枚与那层玻璃），
 * 落点却是纯图，交棒那一刻那条带消失 —— 这就是读到的"录制/闪"。
 *
 * 唯一的解法是**截之前让 chrome 不在场**。这里刻意不用"摘掉组合"或"改 padding"的写法：
 * 那两种都会让内容重排、卡片跟着挪，而起点矩形是**点击那一刻**量的，挪了就对不上屏上那一块 ——
 * 与 10-01 那条「起点与终点要同构」是同一族错。**只有 alpha 是纯绘制层的，几何零变化。**
 *
 * ## 为什么住在 components 层而不是 gallery 层
 *
 * 读它的是**壳层底栏**（`feature/Navigation.kt` 那个覆盖层 Box）与**全站共用的顶栏**
 * （`components/venera/VeneraTopAppBar.kt`），写它的是画廊的飞行链路。
 * 状态若住在 `gallery/ui` 下，`components` 那一头要 import 上层就破 `LayeringEdgeTest`；
 * 住在 `components` 则两个方向都是合法的。跨层形状照 `gallery/ui/GalleryChromeAutoHide.kt`
 * 的先例：**必须是快照字段**，普通 `var` 的写入不会让壳层那一支重组。
 *
 * ## 为什么带一张"谁压在哪"的表
 *
 * 无条件收会让**每一次**点卡都闪一下 chrome，而实际上只有"这张卡确实被压住"时才需要收。
 * 各块 chrome 自己把窗口矩形登记进来（[reportChromeBand]），截之前只判一次相交 ——
 * 判据不重复写：底栏多高、顶栏落到哪，**由它们自己报**，这里不存任何常量。
 */
object ChromeCaptureSuppress {

    /** chrome 读它决定自己的 alpha；只有 `GalleryFlyIn.capture` 那几帧为 true。 */
    var suppressed by mutableStateOf(false)
        internal set

    /**
     * 各块自有 chrome 当前占着的**窗口**矩形。
     *
     * 刻意**不是**快照状态：它每帧都被 `onGloballyPositioned` 回写（滚动时顶栏还在折叠），
     * 做成 state 会让读它的每一支每帧重组 —— 那是本仓已经量过一次的自激环。
     * 只有点击那一刻读一次，普通集合就够（写在主线程，读也在主线程）。
     */
    private val bands = HashMap<String, Rect>()

    /**
     * 登记一块 chrome 当前占的窗口矩形。**零面积的回报只忽略、不覆盖**。
     *
     * 这不是防御性的空判：本仓已经量过一次 —— 组合里一个项要被撤掉之前，
     * 最后一次布局回调给的就是 `0×0`（`GalleryFlyIn.noteCardBounds` 那条同一成因）。
     * 照单覆盖的话，一条正在消失的栏会把还在用的那份矩形抹成零高，
     * 而零高的栏与任何跨到那一行的卡都"相交"（四笔严格比较拦不住它）——
     * 于是每次点卡都白闪一下 chrome。
     */
    fun reportBand(key: String, bounds: Rect) {
        if (bounds.width <= 0f || bounds.height <= 0f) return
        bands[key] = bounds
    }

    fun forgetBand(key: String) {
        bands.remove(key)
    }

    /** [rect] 有没有被某一块自有 chrome 压住 —— 决定这一次截帧要不要让 chrome 让位。 */
    fun obstructs(rect: Rect): Boolean = bands.values.any { it.overlapsWith(rect) }

    /**
     * 两个矩形有没有**真的叠在一起**。
     *
     * 自己写而不叫 `Rect.intersects`：那个名字在本仓锁的 compose 版本上没解析成功（编译期实测），
     * 而这条判据就四笔比较，写出来比查版本便宜。
     * **贴边不算压住**（四笔都是严格小于）：卡的下沿正好落在栏的上沿上时，那一块像素还是卡的。
     */
    private fun Rect.overlapsWith(other: Rect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom
}

/** 底栏那一层（键固定，全壳只有一条）。 */
const val CHROME_BAND_BOTTOM_BAR = "bottomBar"

/** 顶栏（`VeneraTopAppBar` 全站一处登记，同屏只有一条在组合里）。 */
const val CHROME_BAND_TOP_BAR = "topBar"

/**
 * 把自己这块的窗口矩形登记进 [ChromeCaptureSuppress]，卸载时撤掉。
 *
 * 挂在 chrome 的**根节点**上：登记的必须是"屏上真正盖住内容的那一块"，
 * 所以包含它的内边距与阴影之外的实际绘制范围 —— 挂在里面某颗钮上会漏登记整条栏。
 *
 * ⚠️ 这里**不写任何快照状态**：`onGloballyPositioned` 在滚动时每帧都来一次，
 * 存进 state 就是本仓已经量过一次的那条自激环（每帧新 `Rect` 实例 × 引用比较）。
 * 转场期两屏共用同一个 key 时，先卸的那一支会把后一支的登记一起撤掉 ——
 * 症状只是"这一次不多收一下 chrome"，方向是安全的。
 */
@Composable
fun Modifier.reportChromeBand(key: String): Modifier {
    DisposableEffect(key) {
        onDispose { ChromeCaptureSuppress.forgetBand(key) }
    }
    return then(
        Modifier.onGloballyPositioned { coords ->
            if (coords.isAttached) ChromeCaptureSuppress.reportBand(key, coords.boundsInWindow())
        },
    )
}
