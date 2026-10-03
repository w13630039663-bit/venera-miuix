package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
 * 三个域：图库 / 漫画 / 设置（设计稿 `.rail`，`:320-331`）。
 *
 * ## 为什么今天只有图库是真入口，而另两枚**仍然是可点的**
 *
 * 漫画侧在桌面确实有能跑的东西（`:engine-probe` 那条 `EngineSession` 链实测能取数），
 * 但它被关在 S0 探针壳里、不是界面。设置侧**一颗都没有**。
 * 按本仓"不许有假开关"的纪律，正确的做法不是把两枚画成灰的（那是"未实现"那一档的形状，
 * 会让用户以为点进去能看到说明），而是：**点它就把"这一档没接"说清楚**。
 * 所以 [notWiredReason] 那句话是这个组件存在的一半理由 ——
 * 它让"没接"有一处可以落脚，而不是静默消失。
 */
internal enum class DesktopGalleryDomain(val title: String, val glyph: String, val notWiredReason: String) {
    GALLERY("图库", "图", "") {
        override fun toString() = title
    },
    COMIC("漫画", "漫", "漫画域在桌面还没有入口页：取数链能跑（S0 那条最小闭环实测过），但没做成界面。"),
    SETTINGS("设置", "设", "设置域在桌面还没有入口页：偏好与内容守卫都已接线到本机数据目录，界面没做。"),
    ;

    val isWired: Boolean get() = notWiredReason.isEmpty()
}

/**
 * rail 那一列。
 *
 * 形状照稿 `.rail` + `.rbtn`（`:320`、`:57`）：宽 48、图标位 16、选中态藤紫 + 3×20 圆角指示条。
 * 与 pane 那一列的差别只有**底色**（rail 用 [DesktopTheme.SidebarBackground]，pane 同档 ——
 * 两者的层级差由宽度与指示条说清，明度差留给 pane↔主区那一对）。
 */
@Composable
internal fun DesktopGalleryRail(
    selected: DesktopGalleryDomain,
    onSelect: (DesktopGalleryDomain) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxHeight().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DesktopGalleryDomain.entries.forEach { domain ->
            RailButton(
                domain = domain,
                selected = domain == selected,
                onClick = { onSelect(domain) },
            )
        }
    }
}

@Composable
private fun RailButton(
    domain: DesktopGalleryDomain,
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
                // 40×40 的触达，视觉上是 32×32 的圆角块 —— 触达与观感分开（同 pane 行的做法）
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
            Text(domain.glyph, color = glyph, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
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
