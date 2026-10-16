package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.desktop.gallery.data.DesktopComicOfflineBadge
import com.venera.desktop.gallery.data.desktopComicDownloadedChapters
import com.venera.desktop.gallery.data.desktopComicOfflineBadgeLabel
import com.venera.desktop.gallery.data.desktopComicOfflineBadges
import com.venera.desktop.gallery.data.desktopComicOfflineReading
import com.venera.desktop.gallery.data.desktopComicDownloadsRoot
import io.github.composefluent.component.Text
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 批 99 · 探索那一屏的卡上标记：一次数完「本机存过哪几本、齐没齐」。
 *
 * ⚠️ 这一跳住在**另一颗文件**而不是那一屏里：那一屏今天已有两处读盘（引擎装载与收藏夹那一路），
 * 把"数本机那一棵树"再挤进去就会把"装载与展示分家"那几把尺子顶成两句假话之一（批 98 删除那一路同一颗形状）。
 *
 * 刷新点是**这一屏重新进组合的那一刻**（从详情退回探索、或换源）：`ComicExplorePane` 在 `when (screen)` 的
 * 另一支时整颗离开组合，所以刚下完一话退回来能看见标记跟着变 —— 而**同一屏之内不会自己变**，别夸成"实时"。
 */
@Composable
internal fun rememberDesktopComicOfflineBadges(
    dataDir: File,
    sourceKey: String,
): Map<String, DesktopComicOfflineBadge> {
    var badges by remember { mutableStateOf<Map<String, DesktopComicOfflineBadge>>(emptyMap()) }
    LaunchedEffect(dataDir, sourceKey) {
        val root = desktopComicDownloadsRoot(dataDir)
        val loaded = withContext(Dispatchers.IO) { desktopComicOfflineBadges(desktopComicDownloadedChapters(root)) }
        println(desktopComicOfflineReading(sourceKey, loaded))
        badges = loaded
    }
    return badges
}

/**
 * 压在封面右下角那一枚（左下角那枚是「收藏」，批 76）。
 *
 * ⚠️ 它**不可点**：整张封面是"点卡进详情"的命中区，这一枚一旦吃事件就会在那一片上挖掉一块点不动的地方。
 * 也不另起一行 —— 行高由 `estimatedGridHeight` 那笔算式定（封面 3:4 + 22.dp），加一行就会算错。
 */
@Composable
internal fun ComicOfflineMark(badge: DesktopComicOfflineBadge, modifier: Modifier) {
    Box(
        modifier
            .background(DesktopTheme.OverlayScrim, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(desktopComicOfflineBadgeLabel(badge), color = DesktopTheme.TextPrimary, fontSize = 10.sp)
    }
}
