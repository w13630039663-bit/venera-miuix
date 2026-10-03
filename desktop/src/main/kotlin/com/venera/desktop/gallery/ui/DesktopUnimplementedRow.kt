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

/**
 * 一项「没接」的首页内容 —— 设计稿 `.nav.off` + `.lock` 那一套形状
 * （`docs/designs/windows-gallery-home-touhou-2026-10-03.html:79-88`）。
 *
 * ## 这是用户对既有口径的一次**定点豁免**（2026-10-03 拍板），照做不要自己发挥
 *
 * 仓里原来的口径是「未实现的灰行连标题一起撤」（`docs/rounds/settings-audit-2026-09.md:288`，2026-09-30 拍板，
 * 用在 Android 设置页）。桌面首页 pane 这四行是**显式批准的例外**
 * （`docs/rounds/windows-gallery-home-s2b-shell-2026-10-03.md` §2.2），例外只在这三条前提同时成立时才成立：
 *
 * 1. 沿用稿上的灰与药丸：文字 `#8B8B8B`（稿的 `--t3`）、图标 `#5E5E5E`（稿 `.nav.off svg`），
 *    行尾一枚 **10.5px 药丸写「未实现」**（稿 `.nav .lock` 的形状，底 `#33251F`、字 `#E0A08F`；
 *    稿上那颗药丸原本写的「桌面不可用」按本轮口径换成「未实现」）。
 * 2. 完整原因写在**同一行下方的 caption**（12px、`maxLines = 2`），句子里**点名缺席的那颗件**，
 *    不写「敬请期待」那一类话。
 * 3. 整行**不可点**：`selected = false`、没有 `onClick`。本文件连 `clickable` 都不 import ——
 *    所以「no-op 分支」在这颗件里不是"被避免"，是**结构上写不出来**。
 *    （稿上的计数徽标 `1.2k` / `286` / `2` / `3,412` 一律不带：没有 store 就没有数。）
 *
 * ⚠️ **caption 的字数是有预算的**：pane 224 扣掉 `captionStart` 缩进之后一行约 28 个汉字，
 * 两行念不完就被省略号吃掉 —— 那等于"缺席"没被说出来，正是本批最忌的静默交错。
 * 所以 reason 的句长由 `DesktopGalleryHomeSectionsTest` 钉在 30 字以内。
 *
 * ⚠️ 上面那几个色值是稿的**深色档**。`:desktop` 今天由集成方的 `FluentTheme` 决定深浅，
 * 浅色档下 `#8B8B8B` 在白底上约 3.5:1 仍可辨（不会重演"硬用白字、浅色主题等于隐形"那次），
 * 但深浅两档的正式映射属设计稿回改（S5）的范围，本轮不自己发挥。
 */
@Composable
internal fun DesktopUnimplementedRow(title: String, glyph: String, reason: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DesktopGalleryMetrics.navRowHeight)
                .padding(horizontal = DesktopGalleryMetrics.navRowHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.navRowGap),
        ) {
            Box(Modifier.width(DesktopGalleryMetrics.navGlyphSlotWidth), contentAlignment = Alignment.Center) {
                Text(
                    glyph,
                    color = OffGlyph,
                    fontSize = NavFontSize,
                    maxLines = 1,
                )
            }
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = OffText,
                fontSize = NavFontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                UNIMPLEMENTED_LABEL,
                color = LockText,
                fontSize = LockFontSize,
                modifier = Modifier
                    .background(LockBackground, RoundedCornerShape(999.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Text(
            reason,
            modifier = Modifier.padding(start = DesktopGalleryMetrics.captionStart, end = 4.dp),
            color = OffText,
            fontSize = CaptionFontSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 行尾药丸上的那三个字。抽成常量是为了让用例能钉「四行都带同一句」。 */
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
