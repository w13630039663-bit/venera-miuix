package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.component.Text

/**
 * rail 那一列 —— **一级导航**，形状照稿 `.rail{flex:0 0 48px;…}`（`:59`）
 * 与 `.rbtn{…}`（`:60`）：宽 48、按钮视觉 36×36（触达 40×40）、选中态藤紫 + 3×20 圆角指示条
 * （`.rbtn.sel` 在 `:63-64`）。
 *
 * ## 七枚全可是 —— selected 不再是写死的常量（2026-10-04 改）
 *
 * 上一版 `DesktopGalleryRail` 收一个**由调用方写死**的 `DesktopGalleryDomain.GALLERY`，
 * 于是点了另外两枚只会往主区底部吐一句缺席说明，**当前域从来不变**。
 * 那形状等于把 rail 从"导航"降级成"一排提示按钮"：用户看得见七个格子、看得见 hover，
 * 却在路由器那里看到地址的那一刻才发现自己哪里也没去。
 *
 * 现在 [selected] 是真状态，缺席域点了就**真的切过去**，目的地是一屏把理由念全的坦白页
 * （[DesktopDomain.absence]）。缺席的立足点从"临时浮出来的一句话"变成"一个能回来的地方"。
 *
 * ## 排版照稿的三段（`:354-364`）
 *
 * ```
 * 图库 发现 搜索 收藏 画师     ← 一组，组内 4dp
 *   ── 8px ──                  ← `:359` 的 `<span style="height:8px">`
 * 漫画
 *   ── 弹性留白 ──             ← `:361` 的 `.spacer`
 * 设置                         ← 贴底（`:362-363` 那两枚装饰在本批不做）
 * ```
 *
 * ⚠️ **rail 底色与 pane 不同档**（2026-10-04 改）：rail 走 [DesktopTheme.RailBackground]
 * `#1C1C1C`（稿 `:59` 的 `.rail{background:#1C1C1C}`），pane 走 [DesktopTheme.SidebarBackground]
 * `#272727`（稿 `:10` 的 `--layer`）—— **这枚明度差就是两级导航的层级差本身**。
 * 本条原先写的是"两者同档、层级差只由宽度与指示条说清"，与代码不符
 * （那时两处确实同色，深色下两级糊成一片）。
 */
@Composable
internal fun DesktopGalleryRail(
    selected: DesktopDomain,
    onSelect: (DesktopDomain) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxHeight().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.railButtonSpacing),
    ) {
        DesktopDomain.entries.forEach { domain ->
            RailButton(
                domain = domain,
                selected = domain == selected,
                onClick = { onSelect(domain) },
            )
            when {
                // 稿 `:359`：前五枚与「漫画」之间那道 8px —— 视觉上把"浏览"与"读图"分成两段。
                // （在 Column 的 4dp 之上再叠 8dp，合计 12dp，够看出来是分组而不是普通行距。）
                domain == DesktopDomain.ARTISTS ->
                    Spacer(Modifier.height(DesktopGalleryMetrics.railGroupGap))

                // 稿 `:361`：`.spacer` 吃掉剩余高度，把「设置」推到 rail 底部。
                // 少了这一枚，设置会紧跟在漫画下面，而稿上它明明是**贴底**的那一枚。
                domain.precedesSpacer -> Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RailButton(
    domain: DesktopDomain,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val glyph = if (selected) DesktopTheme.AccentFuji else DesktopTheme.TextSecondary
    val text = if (selected) DesktopTheme.TextPrimary else DesktopTheme.TextSecondary

    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                // 40×40 的触达，视觉上是 36×36 的圆角块 —— 触达与观感分开（同 pane 行的做法）
                .width(36.dp)
                .height(36.dp)
                .background(
                    color = when {
                        selected -> DesktopTheme.SelectionGradientStart
                        hovered -> DesktopTheme.SurfaceRaised
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(6.dp),
                )
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                domain.glyph,
                color = glyph,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        if (selected) {
            // 稿 `.rbtn.sel::before` 那一枚：3dp 宽、圆角 3、藤紫。
            // 与 pane 行同一套形状 —— 两级导航的"我在哪"用同一种语言说。
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(20.dp)
                    .background(DesktopTheme.AccentFuji, RoundedCornerShape(3.dp)),
            )
        }
    }
}
