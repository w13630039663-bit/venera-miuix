package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.venera.desktop.gallery.data.DesktopBrowseView
import com.venera.desktop.gallery.data.DesktopGalleryBrowseHistory
import com.venera.desktop.gallery.data.desktopBrowseHeaderReading
import com.venera.desktop.gallery.data.desktopBrowseRowLine
import io.github.composefluent.component.Text

/** 那一格现在摆的是什么：三档各念各的（灰块与"坏图"同形是量过的缺陷）。 */
private enum class BrowseThumbPhase { Loading, Loaded, Failed }

/**
 * 批 86 · 本机点开过的那些图（首页「历史」那一行的那一屏）。
 *
 * ## 形参只有那颗件，没有路径也没有 provider
 *
 * 读侧的形参表就是它的权限边界：拿到 `PathProvider` 等于拿到一条能顺手建目录的手
 * （与批 85 那颗 `saveDir: File` 同一族口径，判据 ④⑰ 钉着）。
 *
 * ## 这一屏摆图，而「下载」那一屏不摆 —— 两件不同的事
 *
 * 那一屏的证据是文件名、字节数与时刻，三样都从盘上取；摆一张图就得把字节再解一遍，
 * 而缓存里那张与盘上那张**是两份东西**，混在一屏里就问不出"屏上这张是盘上哪一颗"。
 * 这一屏的证据本来就在站方那一边：这一行说的是"这台机器上有人点开过这张"，
 * 那张预览档正是让人一眼认得那是哪张的唯一线索。地址取不到就当场念出**哪一档**取不到。
 *
 * ## 没有"点开回原卡"那一枚
 *
 * 那一格只存了站、号、预览档与时刻四样（理由在 `DesktopGalleryBrowseHistory` 的类注释里）。
 * 要从这四样重建整张帖子，就得把原图地址与宽高也存进档、开始欠过期账 —— 那是另一件事。
 * 现在摆一枚按钮下去只能带回半屏，正是本仓最忌的那枚假开关，所以**不摆**。
 *
 * ## 坏档那一句不在这里消费
 *
 * 对岸那两枚坏档读数走的是「根上消费一次 + 存进状态」（`DesktopCorruptNoticeTest` 钉着），
 * 因为它们那两枚是**读走即清**的：挂在屏里就会随那一屏离开组合而消失。
 * 这一颗不清 —— 它说的是"盘上那份坏了"，那是一件一直成立的事，所以直接 `collectAsState` 读。
 */
@Composable
internal fun DesktopGalleryBrowseHistoryPane(history: DesktopGalleryBrowseHistory) {
    val rows by history.rows.collectAsState()
    val broken by history.notice.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = DesktopGalleryMetrics.imageWallHorizontalPadding,
                vertical = DesktopGalleryMetrics.detailBodyGap,
            ),
        verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap),
    ) {
        Text(
            desktopBrowseHeaderReading(rows),
            color = DesktopTheme.TextSecondary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        broken?.let {
            // 颜色与字号口径照画师那一屏那句（同一件事在两屏不该长两个样）。
            Text(it, color = DesktopTheme.AccentBeni, fontSize = 12.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap)) {
            items(rows, key = { "${it.site.routeKey}-${it.postId}" }) { row ->
                BrowseHistoryRow(row)
            }
        }
    }
}

/** 一格：预览档那一座（尺寸口径照「正在关注的画师」那一栏的 44 座）+ 站名、号与那一刻。 */
@Composable
private fun BrowseHistoryRow(row: DesktopBrowseView) {
    val shape = RoundedCornerShape(6.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap)) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(shape)
                .border(1.dp, DesktopTheme.SurfaceRaised, shape)
                .background(DesktopTheme.ImagePlaceholder),
            contentAlignment = Alignment.Center,
        ) {
            if (row.thumbUrl.isBlank()) {
                Text("站方没给缩略档", color = DesktopTheme.OnImagePlaceholder, fontSize = 9.sp, maxLines = 2)
            } else {
                var phase by remember(row.thumbUrl) { mutableStateOf(BrowseThumbPhase.Loading) }
                AsyncImage(
                    model = row.thumbUrl,
                    contentDescription = "${row.site.displayName} 的作品",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onSuccess = { phase = BrowseThumbPhase.Loaded },
                    onError = { phase = BrowseThumbPhase.Failed },
                )
                when (phase) {
                    BrowseThumbPhase.Loaded -> Unit
                    // 正在取与取不到念两句：只留一块灰，用户分不出这两件事。
                    BrowseThumbPhase.Loading -> Text("正在取图", color = DesktopTheme.OnImagePlaceholder, fontSize = 9.sp, maxLines = 2)
                    BrowseThumbPhase.Failed -> Text("图没加载出来", color = DesktopTheme.OnImagePlaceholder, fontSize = 9.sp, maxLines = 2)
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                desktopBrowseRowLine(row),
                color = DesktopTheme.TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
