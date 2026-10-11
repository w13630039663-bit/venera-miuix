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

/**
 * 图库首页那一栏「正在关注的画师」。
 *
 * ⚠️ 稿上这一栏写的是「热门画师」，本轮**改名**「正在关注的画师」：Pixiv 这一轮不接，而仓库里
 * 从来没有"热门画师榜"这个数据源 —— 硬造就是一屏假读数。判据一律转交共享那颗
 * （[desktopFollowedArtistsRowReadout] 走 `GalleryFollowedArtists.rowOf`，横卡按 `ROW_SIZE` 截断），
 * 桌面这边**不另立一套**"什么叫同一位"、"什么叫新"。
 *
 * 行的形状照设计稿的 `.row2`（`:174-179`）：座 44×44 圆角 6、行内衬 6、座与文字间距 10、
 * 名字 13.5px/600、元信息 11.5px 走 `--t3`。稿上那一行行尾的 `.go` 箭头**不画**：
 * 桌面没有画廊的画师介绍页，摆一枚指过去的箭头就是假开关。
 *
 * ## 这一栏与「画师」域那一屏的差别**只有两处**
 *
 * 一是截断：这一栏按横卡的 8 位截，摆不下的用 `overflow` 念出来；那一屏是整页，摆全。
 * 二是头像：那一屏给看得见的几位现取，**这一栏不取** —— 横卡的八位全在视口里，
 * "按视口闸门取"在这一栏等于"进来就发 16 笔"，与闸门的精神相反。
 * 圆座三档、同人合并、排位、张数四件事两处同源（同一颗判据件、同一颗行 composable），
 * 所以"首页第一位与画师页第一位不是同一个人"这种漂在结构上长不出来。
 *
 * 空名单的文案是「还没有关注画师 · 关注是本地动作」，**不写"0 位热门"**那一类话：
 * 前者是用户还没做，后者是把没数据说成有数据。
 */
@Composable
internal fun DesktopFollowedArtistsPane(ports: GalleryPorts) {
    val follows by ports.follows.follows.collectAsState()
    val favorites by ports.favorites.favorites.collectAsState()
    val readout = remember(follows, favorites) { desktopFollowedArtistsRowReadout(follows, favorites) }
    val avatars = ports.avatars
    // 只读本机已有的地址：这一栏按 ROW_SIZE 截断，八位全在视口里，"现取"就等于进来就发 16 笔。
    // 键轨那枚常量与整页现取那一路共用，所以那里取到的那几位在这里直接命中。
    val faces = desktopArtistFaces(readout) { site, name -> avatars.peek(site, name, DESKTOP_AVATAR_SOURCE_KEY) }
    // 离线词典那一问：整枚替换那一路（对岸 `GalleryHomeSections.kt:502` 同一档）。
    // ⚠️ 取不到就当没有译名（名字退回站方那串），而不是把那一行留空；名单空着时**一发都不发**。
    val names = remember(readout) { readout.artists.map { it.name } }
    var translations by remember(names) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(names) {
        translations = if (names.isEmpty()) emptyMap() else runCatching { ports.lexicon.translations(names) }.getOrDefault(emptyMap())
    }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("正在关注的画师")
        if (readout.gap == GalleryFollowedArtistsGap.NO_FOLLOWS) {
            Text("还没有关注画师 · 关注是本地动作")
        } else {
            readout.artists.forEach { artist ->
                DesktopFollowedArtistRow(artist, desktopArtistFace(faces[artist.name], artist.previewUrl), translations)
            }
            // 摆不下的那几位要念出来，否则这一行读起来像"就这么些人"。
            if (readout.overflow > 0) Text("另有 ${readout.overflow} 位没摆进这一栏 · 去「画师」那一屏看全")
        }
        Text(
            DesktopArtistFaceCaliberCaption,
            color = MetaText,
            fontSize = 11.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 一位画师一行：圆座 + **可点的名字** + 元信息（来源站与名下张数）。
 *
 * 点那枚名字 = 去搜索这一位（批 2 ⑤ 的动线，首页那一栏与「画师」整页共用同一颗行件 ⇒ 两处一起有）。
 * 带的是 `artist.sites` 而不是某一枚站：跨站同名并成一条的那一位本来就属于两站，
 * 交两站是**都打**（真做了的事），替用户挑一站才是猜 —— 换算规则在 [desktopSearchSeedFor]。
 * ⚠️ 只点名字而**不点整行**：整行是这位的身份条，而下一步「取关」要落在同一行的另一头，
 * 整行可点会让那枚按钮的触达面被行手势吃掉。
 *
 * 元信息全是**真读数**：`sites` 的徽标次序恒为枚举自然序（跨站同名并成一条的结果，两站都关注过就摆两枚），
 * `count` 是这几站里带着这个名字的收藏张数，为 0 时那一行字整段不说
 * （口径在 [FollowedArtistRef] 的注释：0 就别说那一行字）。
 *
 * [face] 是**已经判过档**的那一张地址（`null` = 真头像与名下收藏图两档都没有）。字母座是**底层**，
 * 图加载成功才盖上去 —— 所以"图坏了"与"这位没头像"落到屏上是同一个形状，
 * 这一条由 [DesktopArtistFaceCaliberCaption] 那句在页尾明说，不静默。
 */
@Composable
internal fun DesktopFollowedArtistRow(
    artist: FollowedArtistRef,
    face: String?,
    /** 离线词典那一问的结果（整枚替换那一路，见 `desktopArtistDisplayName`）。**必填**：漏传的形状是"首页有中文而整页没有"，不给默认值。 */
    translations: Map<String, String>,
    /**
     * 行自己的修饰符。整页那一屏要把它压进 `Row` 里、右边再挂一枚「取关」，所以留这一格 ——
     * ⚠️ 它是 `Modifier` 而不是回调：本仓禁的是**带默认值的回调参数**（漏传不报错 = 动作没人接），
     * 而 `Modifier` 给默认值是 Compose 的通行形状，漏传的表现是"宽没分配对"，看得见也测得出。
     */
    modifier: Modifier = Modifier,
) {
    val handoff = LocalDesktopSearchHandoff.current
    Row(
        modifier = modifier.fillMaxWidth().padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SeatBackground)
                .border(1.dp, DesktopTheme.SurfaceRaised, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(artist.name.take(1).uppercase(), fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (face != null) {
                // 字母座是**底层**，图在上面：加载中与加载失败都还没有东西盖上来 ⇒ 屏上仍是字母。
                // 这里刻意不接 `onLoading` / `onError` 去换形状 —— 44dp 的座摆不下第二种读数，
                // 所以那两种落点统一由页尾那句 [DesktopArtistFaceCaliberCaption] 明说，不静默。
                AsyncImage(
                    model = face,
                    contentDescription = "${artist.name} 的卡面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                desktopArtistDisplayName(artist.name, translations),
                color = DesktopTheme.AccentFuji,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { handoff(artist.sites, artist.name) },
            )
            val meta = buildString {
                // 顿号而不是空格：跨站同名并成一条之后这里可能有两枚站名，
                // 用空格隔开会被读成"yande.re Gelbooru"是一个站名（图证里就是这么读的）。
                append(artist.sites.joinToString("、") { it.displayName })
                if (artist.count > 0) append(" · 名下 ${artist.count} 张")
            }
            Text(meta, color = MetaText, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * 圆座三档那句读数说明，**只给这一栏**（整页那一屏用的是 `DesktopArtistsPageCaliberCaption`）。
 *
 * ⚠️ 这一句从前两处共用，而第十批 ② 起整页那一屏**会**去取 —— 两处共用一句，改一处就有一句变假。
 * 分开之后每一句都只说自己那一屏：**「没取到 / 没有 / 没接」是三句话**，而"这一栏不主动取"
 * 是第四句，只在真的只读档的那一栏才许念。
 */
internal const val DesktopArtistFaceCaliberCaption: String =
    "卡面三档：本机存过的真头像 > 名下最近一张收藏 > 首字母座。这一屏不主动去站方取头像，" +
        "所以只关注没收藏的那几位落字母座；图坏了也落字母座。"

// 座底色与元信息色都取稿的深色档阶梯（`--layer2:#2C2C2C` / `--t3:#8B8B8B`，`:10-11`），浅色档映射归 S5。
private val SeatBackground = DesktopTheme.CardBackground
// 2026-10-04：`MetaText` 原先这里是 `Color(0xFF8B8B8B)` 字面量 —— 那是 [DesktopTheme.TextTertiary]
// 的同值重写，而扫描器的前缀表里没有登记 `0xFF8B8B8B`，所以它从落成那天起就没被抓到。
// 这是 2026-10-04 静态体检扫全量字面量时才发现的（旧表只认四档 surface，文字档 never watched）。
private val MetaText = DesktopTheme.TextTertiary
