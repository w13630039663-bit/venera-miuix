package com.venera.desktop.gallery.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.gallery.domain.GalleryViewerBackdrop

/**
 * 大图那一层今天该画哪个色，以及**底下那面墙要不要糊**（第六十一批接色，第六十三批接糊）。
 *
 * 三档不透明的直接吃枚举自己带的 `argb` —— 那是全仓现成的口径（对岸改数桌面跟着改），
 * 在这颗件里再写一遍 `0xFF121212` 就是两条轨（判据 ② 钉着这一条）。
 *
 * ## `GLASS` 这一档今天画的是"糊 + 压暗"，两半各住一处
 *
 * - **糊的是墙**：`desktopWallBlurRadius` 交出的那枚半径由集成方挂在墙那一棵 `Column` 上。
 *   对岸那一档是**窗口级** `FLAG_BLUR_BEHIND`，而那条路在桌面判负（S0-6：材质根本没画出来，
 *   三档逐字节相同全是死灰）⇒ 桌面走**应用内**那一层，它画不画得出来是批 62 那枚探针量过的
 *   （两枚同样的棋盘，吃 `Modifier.blur` 那一枚格边消失）。
 * - **压暗住在这一侧**：`argb` 为 null 时交回的是黑 @ [`DesktopBackdropScrimAlpha`]，
 *   不是主题坐底色 —— 那一层必须**半透明**，不然底下糊没糊都被它盖掉。
 *
 * ⚠️ 两枚数都是**重述**（对岸那两颗常量一颗 `private`、一颗在 `protected companion` 里，
 * 桌面编译面读不到），所以由判据 ⑨ 逐字回核对岸那两句，改数只能两边一起改。
 */
internal const val DesktopBackdropScrimAlpha = 0.45f

/** 半径口径照对岸 `VeneraSubActivityBase.BlurBehindDp`（那一句又沿用应用内既有的「内容重度模糊」18dp）。 */
internal const val DesktopWallBlurDp = 18

internal fun desktopBackdropColor(backdrop: GalleryViewerBackdrop): Color =
    backdrop.argb?.let { Color(it) } ?: Color.Black.copy(alpha = DesktopBackdropScrimAlpha)

/**
 * 墙上那一棵今天挂不挂模糊（批 63）。
 *
 * 只有 `GLASS` **且大图那一屏开着**才给半径：另三档画的是不透明那一层，糊了也看不见 ⇒ 白烧一层；
 * 而浮层开着、大图没开时也不糊 —— 那一档在对岸属于大图页的幕布，不属于浮层。
 */
internal fun desktopWallBlurRadius(backdrop: GalleryViewerBackdrop, viewerOpen: Boolean): Dp =
    if (backdrop == GalleryViewerBackdrop.GLASS && viewerOpen) DesktopWallBlurDp.dp else 0.dp
