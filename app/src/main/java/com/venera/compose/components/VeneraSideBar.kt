package com.venera.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 大屏侧栏 —— 抄 master `navigation_bar.dart` 的 `form 2` / `form 3` 两态。
 *
 * ## 为什么需要它
 *
 * 官方在宽窗把整条底栏连同 overlay 层一起消失（`nav:933` `shouldShowAppBar = controller.value < 2`），
 * 内容改从左侧让宽（`nav:310-312`）。**顶栏也不渲染**（`nav:957` 受同一个布尔门控），
 * 所以侧栏档是「无顶栏 · 无底栏 · 内容左内缩」，不是「顶栏换成侧栏」。
 *
 * ## 宽度抄哪
 *
 * 抄 master 插值公式的**稳态值**（`nav:132` `_kFoldedSideBarWidth = 72`、
 * `nav:134` `_kSideBarWidth = 224`）。不抄插值本身 —— master 是
 * `AnimatedBuilder` + `controller.animateTo(target)` 的连续值（`nav:296,299`），
 * 我们是三态离散（方案 §5 第 3 条：**先跳变、后动画**）。
 * `animateDpAsState` 造假 0→72 会与真机不符，而且动画会掩盖问题，跳变一眼能看出要不要修。
 *
 * ## 为什么不画选中态底衬
 *
 * 方案 §5 第 1 条：选中态复用既有语义色（`primary`），不新造 token。
 * 这里的呈现就是「图标换 Filled + 染 `primary`」，与 [VeneraFloatingNavBar] 的
 * 药丸指示器同一套语义，只是几何不同（侧栏是方块药丸，底栏是胶囊）。
 */
@Composable
fun VeneraSideBar(
    currentTab: VeneraNavTab?,
    onTabSelected: (VeneraNavTab) -> Unit,
    mode: WideScreenLayoutMode,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tokens = VeneraTokens

    // Compact 档留这道闸是为了让本组件可被无条件调用：万一误入，渲染成纯内容
    // 而不是侧栏压住页面。调用方本不该在Compact 挂它。
    if (mode == WideScreenLayoutMode.Compact) {
        Box(modifier = modifier.fillMaxSize()) { content() }
        return
    }

    val expanded = mode == WideScreenLayoutMode.Expanded

    Row(modifier = modifier.fillMaxSize()) {
        // ── 侧栏本体 ──
        Column(
            modifier = Modifier
                .width(sideBarWidthFor(mode))
                .fillMaxHeight()
                .background(tokens.color.surfaceContainerHigh)
                .systemBarsPadding()
                .padding(vertical = tokens.spacing.space8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4, Alignment.CenterVertically),
        ) {
            VeneraNavTab.entries.forEach { tab ->
                val selected = tab == currentTab
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        // 不用 indication：与 VeneraTopBarPill 同款处置 —— 默认高亮会溢到
                        // 圆座外形成光晕。这里点按反馈自己做（alpha 变化）。
                        .clickable { onTabSelected(tab) }
                        .background(
                            if (selected) {
                                tokens.color.primary.copy(
                                    alpha = tokens.current.selectedSurfaceAlpha,
                                )
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            }
                        )
                        .padding(
                            horizontal = tokens.spacing.space3,
                            vertical = tokens.spacing.space3,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
                ) {
                    Icon(
                        imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
                        contentDescription = tab.title,
                        tint = if (selected) tokens.color.primary else tokens.color.textSecondary,
                        modifier = Modifier.size(tokens.spacing.space11),
                    )
                    if (expanded) {
                        Text(
                            text = tab.title,
                            fontSize = tokens.type.caption,
                            color = if (selected) tokens.color.primary else tokens.color.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        // ── 内容层：左内缩量恰等于侧栏宽 ──
        //
        // master `nav:310-312` 的内缩公式在稳态下算出来正好等于侧栏宽
        // （form 2 ⇒ 72×1+152×0 = 72；form 3 ⇒ 72×1+152×1 = 224），
        // 「内容内缩 == 侧栏宽」是这两条路线的自洽性保证，别自己另编一个内缩量。
        // 这里用「Row 的第二个子节点自然占剩余宽」等价实现 —— 内缩量 = 侧栏宽。
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}
