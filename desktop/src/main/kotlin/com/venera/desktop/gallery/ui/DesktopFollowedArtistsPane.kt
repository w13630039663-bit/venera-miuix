package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.domain.FollowedArtistRef
import com.venera.compose.gallery.domain.GalleryFollowedArtists
import com.venera.compose.gallery.domain.GalleryFollowedArtistsGap
import io.github.composefluent.component.Text

/**
 * 「正在关注的画师」这一 pane。
 *
 * ⚠️ 稿上这一栏写的是「热门画师」，本轮**改名**「正在关注的画师」：Pixiv 这一轮不接，而仓库里
 * 从来没有"热门画师榜"这个数据源 —— 硬造就是一屏假读数。判据用已共享的那颗纯函数
 * [GalleryFollowedArtists.rowOf]（跨站同名并成一条、排位取最近一次关注、行里摆不下的用 `overflow` 念出来），
 * 桌面这边**不另立一套**"什么叫同一位"、"什么叫新"。
 *
 * 行的形状照设计稿的 `.row2`（`:141-146`）：座 44×44 圆角 6、行内衬 6、座与文字间距 10、
 * 名字 13.5px/600、元信息 11.5px 走 `--t3`。稿上那一行行尾的 `.go` 箭头**不画**：
 * 桌面没有画廊的画师介绍页，摆一枚指过去的箭头就是假开关。
 *
 * ## 卡面次序这条口径现在**摆不出图**，但判据不许改
 *
 * 三档是「真头像 > 名下最近一张收藏 > 首字母座」（判据在 [GalleryFollowedArtists.faceOf]，
 * 用户 2026-10-01 改的次序）。前两档都是**图片**，而桌面这轮没有图库取图件（成因见
 * [DesktopDailyPane] 类注释的第 3 条），所以这里一律落到第三档，并把这件事在节尾说出来一次。
 * `faceOf` 与 `seedFaces` 两判据因此**不在这里调用** —— 在这儿算出一个没人能消费的地址只是死代码；
 * 接上取图层的那一批（S3）要把这两颗判据与圆座档位一起接回。
 *
 * 空名单的文案是「还没有关注画师 · 关注是本地动作」，**不写"0 位热门"**那一类话：
 * 前者是用户还没做，后者是把没数据说成有数据。
 */
@Composable
internal fun DesktopFollowedArtistsPane(ports: GalleryPorts) {
    val follows by ports.follows.follows.collectAsState()
    val favorites by ports.favorites.favorites.collectAsState()
    val readout = remember(follows, favorites) { GalleryFollowedArtists.rowOf(follows, favorites) }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("正在关注的画师")
        if (readout.gap == GalleryFollowedArtistsGap.NO_FOLLOWS) {
            Text("还没有关注画师 · 关注是本地动作")
        } else {
            readout.artists.forEach { artist -> DesktopFollowedArtistRow(artist) }
            // 摆不下的那几位要念出来，否则这一行读起来像"就这么些人"。
            if (readout.overflow > 0) Text("另有 ${readout.overflow} 位没摆进这一栏")
        }
        Text(
            "头像与名下收藏图本轮不显示：桌面还没有图库的取图件，卡面一律首字母座",
            color = MetaText,
            fontSize = 11.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 一位画师一行：首字母座 + 名字 + 元信息（来源站与名下张数）。
 *
 * 元信息全是**真读数**：`sites` 的徽标次序恒为枚举自然序（跨站同名并成一条的结果，两站都关注过就摆两枚），
 * `count` 是这几站里带着这个名字的收藏张数，为 0 时那一行字整段不说
 * （口径在 [FollowedArtistRef] 的注释：0 就别说那一行字）。
 */
@Composable
private fun DesktopFollowedArtistRow(artist: FollowedArtistRef) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(44.dp).background(SeatBackground, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(artist.name.take(1).uppercase(), fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(artist.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = buildString {
                append(artist.sites.joinToString(" ") { it.displayName })
                if (artist.count > 0) append(" · 名下 ${artist.count} 张")
            }
            Text(meta, color = MetaText, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// 座底色与元信息色都取稿的深色档阶梯（`--layer2:#2C2C2C` / `--t3:#8B8B8B`，`:10-11`），浅色档映射归 S5。
private val SeatBackground = Color(0xFF2C2C2C)
private val MetaText = Color(0xFF8B8B8B)
