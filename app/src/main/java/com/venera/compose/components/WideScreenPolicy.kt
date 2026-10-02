// 宽屏（平板 / 横向宽窗）档所有"通栏悬浮部件"的唯一宽度口径。
//
// 为什么只能有一处：master 的 `_buildMd3BottomBar` 用 `min(_kGlassBarMaxWidth = 540, 宽 - 24*2)`
// 收口底部导航，而我们的阅读器顶栏 / 控制岛 / 抽屉此前各自 `fillMaxWidth()`，
// 1280dp 上被拉成一整条。两处各算一次阈值就会漂移（`Navigation.kt` 里
// "两条底栏路径几何零差异"的契约就是这么被打破过一次）。
//
// 540 与 24 都是 master 原值，不是新造数：
//   master `lib/components/navigation_bar.dart:148` — barW = min(_kGlassBarMaxWidth, width - _kGlassBarHorizontalPadding*2)
//   master `lib/foundation/consts.dart:1`           — changePoint = 600（小于它算手机）
package com.venera.compose.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraSpacing

/** 宽屏档判定阈值：master 的 `changePoint`（`consts.dart:2`）。 */
private val WideScreenWidthThreshold = 600.dp

/** master 的 `changePoint2`（`consts.dart:7`）。全仓唯一用法是 `nav:254` 的 `width > changePoint2`。 */
private val WideScreenExpandedThreshold = 1300.dp

/**
 * 大屏档位：master `targetFormContext` 返回的 0 / 2 / 3 三态（跳过 1，那是 0→2 的动画中间态）。
 *
 * ⚠️ **名字是 MD3 的，阈值不是 MD3 的。**
 * MD3 官方断点是 compact <600 / medium 600–839 / **expanded ≥840**；
 * 本枚举取 **600 / 1300**，抄的是 master 的 `changePoint` 与 `changePoint2`。
 * 本项目要对标的第一方是 Flutter master，不是 MD3 —— 看到 `Expanded` 不要按 840 判。
 */
enum class WideScreenLayoutMode { Compact, Medium, Expanded }

/**
 * 断点判定 —— **吃屏幕宽**（不是网格/容器实测宽）。
 *
 * 两类宽度回答的是不同问题，别混：「该换布局了吗」是设备形态的粗粒度判断，屏幕宽正是这个语义，
 * 且照 master（`context.width` = `MediaQuery.size.width`）才可能与官方一致；
 * 「该排几列」才用实测宽，那一条已经收口在 `ComicPresentationPolicy`，不要退回去。
 *
 * 比较符取 `>`：**恰好 600dp 归 `Compact`（仍算手机档）**。
 * 这一点三方一致，别改成 `>=`：① master `nav:251` 原文 `if (width > changePoint) target = 2`，
 * 600dp 上官方给的是底部胶囊而非侧栏；② 本仓既有单测 `wideScreenChromeMaxWidth(600.dp)`
 * 断言返回 `null`（手机档无上限），改判会连带把 chrome 收口口径翻掉；
 * ③ master 在 600 处三种写法并存（`>` 3 处 / `<` 9 处 / `>=` 1 处），
 * 且 `consts.dart:1` 的注释与 `nav:251` 的代码互相矛盾 —— 那是官方的边界缺陷，不照抄。
 *
 * ⚠️ master 那 9 处 `<` 判的是 AppBar 用 shadow 还是 blur，与导航形态无关，
 * 不构成「600 必须是手机」的独立证据；真正的依据是 `nav:251` 本身。
 */
fun wideScreenLayoutMode(screenWidth: Dp): WideScreenLayoutMode = when {
    screenWidth > WideScreenExpandedThreshold -> WideScreenLayoutMode.Expanded
    screenWidth > WideScreenWidthThreshold -> WideScreenLayoutMode.Medium
    else -> WideScreenLayoutMode.Compact
}

/** master `_kFoldedSideBarWidth`（`navigation_bar.dart:132`）。 */
private val FoldedSideBarWidth = 72.dp

/** master `_kSideBarWidth`（`navigation_bar.dart:134`）。 */
private val ExpandedSideBarWidth = 224.dp

/**
 * 侧栏宽度。抄的是 master 插值公式的**稳态值**，不是插值本身：
 * `nav:310-312` 在 `value = 2/3` 时内容内缩算出来恰好等于 72 / 224，
 * 即「内容内缩 == 侧栏宽」是这两条路线的自洽性保证。
 */
fun sideBarWidthFor(mode: WideScreenLayoutMode): Dp = when (mode) {
    WideScreenLayoutMode.Compact -> 0.dp
    WideScreenLayoutMode.Medium -> FoldedSideBarWidth
    WideScreenLayoutMode.Expanded -> ExpandedSideBarWidth
}

/**
 * 页面内容为底部导航预留的净留白。
 *
 * **批次 2 起三档分值**：Compact 76 / Medium 0 / Expanded 0。
 *
 * 为什么侧栏档归 0：master 在 `value >= 2` 时把整条底栏连同 overlay 层一起消失
 * （`nav:933` `shouldShowAppBar = controller.value < 2` ⇒ false，`:972-974` 提前 return），
 * 底栏不存在 ⇒ 没有需要避让的对象。留 76dp 会在侧栏档留下一条永远填不满的空空白。
 *
 * ⚠️ 归 0 的**前提是底栏真的不渲染**，两者必须同一颗提交落地。
 * 批次 1 曾把三档都设 76 作中间态（那时底栏仍无条件渲染，提前归 0 会让平板档
 * 最后一行内容被悬浮底栏压住 —— 只在侧栏档出现、手机档全好、回归测试抓不到）。
 * 批次 2 的 [VeneraSideBar] 接管了侧栏档的导航，`Navigation.kt` 的底栏门同步加上了
 * 档位判断 ⇒ 前提已成立。**若将来回退侧栏落地，必须同时把这两档改回 76。**
 */
fun bottomBarClearanceFor(mode: WideScreenLayoutMode): Dp = when (mode) {
    WideScreenLayoutMode.Compact -> VeneraSpacing.bottomBarClearance
    WideScreenLayoutMode.Medium,
    WideScreenLayoutMode.Expanded -> 0.dp
}

/**
 * 图片墙列数 —— 画廊墙 / 每日推荐 / 画廊收藏 / 图片收藏四处共用这一把。
 *
 * 每列预算 200dp 不是新造数：本仓详情页预览格早就照 master 钉了
 * `SliverGridDelegateWithMaxCrossAxisExtent(maxCrossAxisExtent: 200)`
 * （见 `ComicPresentationPolicy.comicPreviewColumnCount`），这里沿同一把尺，
 * 连"向上取整"的方向也一致（保证算出来的格宽不超预算，而不是刚好超）。
 *
 * ⚠️ **手机档恒等于 2，不参与预算计算** —— 这是用户 2026-10-02 的硬约束
 * （「现在调的都是大屏版，别动手机版」）的实现，别拿 `ceil` 的裸结果替它：
 * 425dp 以上的手机（大屏机/折叠屏合起）裸算会得 3 列，等于悄悄改了手机版。
 * 侧栏档下限取 3 = 今天 `if (wide) 3 else 2` 的原值 ⇒ **列数只许比今天多，
 * 任何宽度都不会因为这次改动变得比现在更大**（画廊卡是 staggered 网格，
 * 格子高度跟着宽度走，减列就等于放大卡片）。
 *
 * 宽度传**窗口宽**（`screenWidthDp`），不传物理屏宽：分屏与自由窗口下两者不等；
 * 再减掉本档侧栏占掉的 [sideBarWidthFor] 与网格自己的左右内边距 ——
 * 侧栏是这一轮新出现的扣项，忘了减就会在宽窗上多排一列、卡片被挤窄。
 */
fun imageWallColumnCount(
    windowWidth: Dp,
    mode: WideScreenLayoutMode,
    horizontalContentPadding: Dp,
): Int {
    if (mode == WideScreenLayoutMode.Compact) return 2
    val available = (windowWidth - sideBarWidthFor(mode) - horizontalContentPadding).value.coerceAtLeast(0f)
    return kotlin.math.ceil(available / 200f).toInt().coerceAtLeast(3)
}

/**
 * 宽屏档判定（平板 / 横向宽窗）：阈值唯一收口在本文件，
 * 组件内需要按档位换几何（如分段控制器加高）时只许调这个，不得各自抄 600。
 *
 * 保留签名、只改函数体：现有消费点全部只调列数/尺寸，没有一处结构分支，
 * 它们要的是一个「是不是宽屏」的布尔，不需要档位 —— 一处都不用改。
 * 出现第一个需要区分 `Medium` / `Expanded` 的消费点时再一次性替换掉它们。
 */
@Deprecated(
    "改用 wideScreenLayoutMode() 拿档位；本函数只是「!= Compact」的别名，" +
        "保留是为了让只关心是否宽屏的消费点零改动。",
    ReplaceWith("wideScreenLayoutMode(screenWidth) != WideScreenLayoutMode.Compact"),
    level = DeprecationLevel.WARNING,
)
fun isWideScreen(screenWidth: Dp): Boolean =
    wideScreenLayoutMode(screenWidth) != WideScreenLayoutMode.Compact

/** master `_kGlassBarMaxWidth`。 */
private val WideScreenMaxWidth = 540.dp

/** master `_kGlassBarHorizontalPadding` × 2。 */
private val WideScreenSideBudget = 48.dp

/**
 * 宽屏档的收口宽度；**手机档返回 `null`**，表示"各部件沿用现有几何、一字不改"。
 *
 * 返回 null 而不是返回一个等于满宽的数，是为了让调用点必须显式处理手机分支 ——
 * 避免出现"手机档也被某个上限悄悄改掉"这种回不去的观感回归。
 */
fun wideScreenChromeMaxWidth(screenWidth: Dp): Dp? =
    if (screenWidth > WideScreenWidthThreshold) {
        minOf(WideScreenMaxWidth, screenWidth - WideScreenSideBudget)
    } else {
        null
    }

/** 抽屉内容限宽：master `scaffold.dart:662,727` 给章节目录与阅读设置抽屉的定宽。 */
val WideScreenDrawerWidth = 400.dp
