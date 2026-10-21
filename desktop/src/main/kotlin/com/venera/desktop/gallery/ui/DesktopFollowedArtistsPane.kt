package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.domain.FollowedArtistRef
import com.venera.compose.gallery.domain.GalleryFollowedArtistsGap
import io.github.composefluent.component.Text

internal const val DesktopArtistFaceCaliberCaption: String =
    "卡面三档：本机存过的真头像 > 名下最近一张收藏 > 首字母座。这一屏不主动去站方取头像，" +
        "所以只关注没收藏的那几位落字母座；图坏了也落字母座。"

// 座底色与元信息色都取稿的深色档阶梯（`--layer2:#2C2C2C` / `--t3:#8B8B8B`，`:10-11`），浅色档映射归 S5。
private val SeatBackground = DesktopTheme.CardBackground
// 2026-10-04：`MetaText` 原先这里是 `Color(0xFF8B8B8B)` 字面量 —— 那是 [DesktopTheme.TextTertiary]
// 的同值重写，而扫描器的前缀表里没有登记 `0xFF8B8B8B`，所以它从落成那天起就没被抓到。
// 这是 2026-10-04 静态体检扫全量字面量时才发现的（旧表只认四档 surface，文字档 never watched）。
private val MetaText = DesktopTheme.TextTertiary

