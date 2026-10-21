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
