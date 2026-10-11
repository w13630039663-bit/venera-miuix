package com.venera.desktop.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.desktop.gallery.data.DesktopSavedImage
import com.venera.desktop.gallery.data.DesktopSavedListing
import com.venera.desktop.gallery.data.desktopListSavedImages
import io.github.composefluent.component.Text
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 批 85 · 本机存过的那些图（首页「下载」那一行的那一屏）。
 *
 * ## 这一屏只有一件事：**数盘上已有的**
 *
 * 它不建目录、不落新东西、也不问站方 —— 写的那一路住在 `DesktopImageSaver`（按一枚按钮才走），
 * 这一路只是把 `数据根/downloads/图库` 里数得出的东西念出来。所以形参是集成方算好的 `File`
 * 而不是 `PathProvider`：把 provider 递进界面件，就等于递进一条能顺手建目录的手
 * （判据 ⑮ 钉这一条）。同理，全文件只许出现**一次**读盘（判据 ⑯）。
 *
 * ## 为什么不摆缩略图
 *
 * 这一屏的证据是**文件名、字节数与时刻** —— 那三样都从盘上取，摆一张图就得把字节再解一遍，
 * 而"这一张存下来了"这件事不需要解码证明（存的那一刻已经回读过大小）。
 * 更重要的是：摆图要走 Coil，而缓存里那张与盘上那张**是两份东西**，
 * 一屏里混两种来源就会问不出"屏上这张到底是盘上哪一颗"。
 *
 * ## 四态各一句
 *
 * 「还没存过」在这一屏上有四种不同事实：目录还没建、目录在而空着、读不出来、有东西。
 * 塌成一句"空空如也"就会把"我们没读到"说成"盘上没有"（那一族在本仓返工过多次）。
 */
@Composable
internal fun DesktopDownloadsPane(saveDir: File) {
    var listing by remember(saveDir) { mutableStateOf<DesktopSavedListing?>(null) }
    LaunchedEffect(saveDir) {
        listing = withContext(Dispatchers.IO) { desktopListSavedImages(saveDir) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = DesktopGalleryMetrics.imageWallHorizontalPadding, vertical = DesktopGalleryMetrics.detailBodyGap),
        verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap),
    ) {
        val current = listing
        if (current == null) {
            // 还没读出来时念"正在数"，不许先念"一张都没有" —— 那是把"还没读到"说成"读到了空"。
            Text("正在数这一目录里有什么", color = DesktopTheme.TextTertiary, fontSize = 12.sp)
        } else {
            Text(desktopSavedHeaderReading(current), color = DesktopTheme.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val items = (current as? DesktopSavedListing.Listed)?.items.orEmpty()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap)) {
            items(items, key = { it.name }) { item ->
                Text(
                    desktopSavedItemLine(item),
                    modifier = Modifier.fillMaxWidth(),
                    color = DesktopTheme.TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 表头那一句：四态各一句，两两不许相同（判据 ⑬）。 */
internal fun desktopSavedHeaderReading(listing: DesktopSavedListing): String = when (listing) {
    is DesktopSavedListing.Missing -> "本机还一张都没存过（目录还没建出来）"
    is DesktopSavedListing.Empty -> "存图的目录建好了，里面还空着：${listing.dir}"
    is DesktopSavedListing.Unreadable -> "这一目录此刻读不出来：${listing.dir}"
    is DesktopSavedListing.Listed ->
        "本机存过 ${listing.items.size} 张 · 合计 ${desktopByteLabel(listing.items.sumOf { it.bytes })} · 存在 ${listing.dir}"
}

/** 行里那一句：名字、真实字节数、最后改动那一刻。时刻按本机时区格式化（这一句只上屏，不进读数）。 */
internal fun desktopSavedItemLine(item: DesktopSavedImage): String =
    "${item.name} · ${item.bytes} 字节 · ${SAVED_TIME.format(Instant.ofEpochMilli(item.modifiedAtMillis).atZone(ZoneId.systemDefault()))}"

private val SAVED_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
