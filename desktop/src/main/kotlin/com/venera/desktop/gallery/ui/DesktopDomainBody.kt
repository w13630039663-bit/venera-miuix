package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.component.Text

/**
 * 缺席域的主区 —— 点 rail 上那五枚（发现 / 搜索 / 收藏 / 画师 / 设置）任何一枚，落到的就是这一屏。
 *
 * ## 这一屏必须说完的三件事
 *
 * 1. 这一域叫什么（标题 + [UNIMPLEMENTED_LABEL] 那颗药丸，与 pane 里那行用同一套标签语言）。
 * 2. 到底缺的是哪一层（[DesktopAbsence.reason]）。
 * 3. **已经接好的有哪几件**（[DesktopAbsence.wired]）。
 *
 * 第 ③ 条是这一屏存在的理由。只说 ① ② 的坦白页跟"敬请期待"没有区别 —— 它把
 * "GalleryFavorites.favorites 这颗 StateFlow 正在本机流动、只是没人读它"和
 * "这台机器上压根没有收藏这件事"念成了同一句话。而后者是假的。
 *
 * ## 视觉上刻意**不居中**
 *
 * 左上角起排、该多宽多宽，不做那种"空状态插画 + 一句话"的处理。居中的空态会把
 * "这一屏没东西"变成版面设计的一部分，而它要说的恰恰是相反的一件事：
 * **东西是有的，界面没有**。
 */
@Composable
internal fun DesktopAbsentDomainBody(domain: DesktopDomain) {
    val absence = requireNotNull(domain.absence) {
        "「${domain.title}」既有内容又说缺席 —— ${DesktopDomain::class.simpleName} 里的两处必须同真同假"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                domain.title,
                color = DesktopTheme.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                kindLabel(absence.kind),
                color = DesktopTheme.TextSecondary,
                fontSize = 10.5.sp,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(999.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }

        Text(
            absence.reason,
            color = DesktopTheme.TextSecondary,
            fontSize = 13.sp,
        )

        if (absence.wired.isNotEmpty()) {
            Text(
                "已经接好、只是没有界面去看的那几件：",
                color = DesktopTheme.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            absence.wired.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.size(16.dp).padding(top = 5.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(3.dp)
                                .background(DesktopTheme.AccentFuji, RoundedCornerShape(999.dp)),
                        )
                    }
                    Text(
                        item,
                        modifier = Modifier.weight(1f),
                        color = DesktopTheme.TextTertiary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * 缺席成因的那句话。
 *
 * 两档必须分开念：[DesktopAbsenceKind.NO_UI] 是"接好了没画"，[DesktopAbsenceKind.NO_CHAIN]
 * 是"本机真没有"。混起来说"暂不支持"会用同一句话盖住两种完全不同的下一步 ——
 * 前者只差一屏 UI，后者得先有那条链。
 */
private fun kindLabel(kind: DesktopAbsenceKind): String = when (kind) {
    DesktopAbsenceKind.NO_UI -> "取数已通 · 缺界面"
    DesktopAbsenceKind.NO_CHAIN -> "本机没有这条链"
}
