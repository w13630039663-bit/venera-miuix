package com.venera.desktop.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.domain.GalleryFollowedArtistsGap
import com.venera.desktop.gallery.data.DesktopGalleryFailureLog
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal const val DesktopArtistsPageCaliberCaption: String =
    "卡面三档：本机存过的真头像 > 名下最近一张收藏 > 首字母座。这一屏只给当前看得见的几位去站方取一次头像，" +
        "滚过去不再加取，取到的存进本机。够得着的路有 pixiv、fanbox 与 Mastodon 三条，三条都没答上才落字母座。"
