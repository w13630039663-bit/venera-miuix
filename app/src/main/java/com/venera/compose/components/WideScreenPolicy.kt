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

/** 宽屏档判定阈值：master 的 `changePoint`。 */
private val WideScreenWidthThreshold = 600.dp

/**
 * 宽屏档判定（平板 / 横向宽窗）：阈值唯一收口在本文件，
 * 组件内需要按档位换几何（如分段控制器加高）时只许调这个，不得各自抄 600。
 */
fun isWideScreen(screenWidth: Dp): Boolean = screenWidth > WideScreenWidthThreshold

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
