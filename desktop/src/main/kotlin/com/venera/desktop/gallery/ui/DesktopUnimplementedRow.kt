package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.component.Text

internal const val UNIMPLEMENTED_LABEL = "未实现"

private val OffText = DesktopTheme.TextTertiary
private val OffGlyph = DesktopTheme.OffGlyph

/**
 * 药丸底色。
 *
 * ⚠️ 稿上是 `#33251F`（暖棕，`:88`），而那一档是配**神社暖调**的装饰色。
 * 本轮窗体整体转冷调深阶梯（`#202020`/`#272727`/`#2C2C2C`）之后，那颗暖棕放在冷灰里
 * 读起来是"一块脏斑"而不是"一枚标记"。所以药丸改成 [DesktopTheme.SurfaceRaised] 冷灰底
 * + [DesktopTheme.TextSecondary] 字 —— 形状（999 圆角、10.5px、右侧对齐）一字未动。
 * 暖棕留给神社档装饰，不用来标"缺席"。
 */
private val LockBackground = DesktopTheme.SurfaceRaised
private val LockText = DesktopTheme.TextSecondary
private val NavFontSize = 14.sp
private val LockFontSize = 10.5.sp
private val CaptionFontSize = 12.sp
