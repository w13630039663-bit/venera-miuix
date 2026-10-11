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

/**
 * rail「画师」那一域的目的地（第八批转正，第十批 ② 接上头像现取）。
 *
 * ## 这一屏与首页那一栏**同源**，只差两件事
 *
 * 名单、同人合并、排位、张数、圆座三档五条判据都走同一颗判据件
 * （[desktopArtistsReadout] / [desktopArtistFace] / [desktopArtistFaces]，下面转交的是共享那颗
 * `GalleryFollowedArtists`），行也走同一颗 [DesktopFollowedArtistRow]。差的两件事：
 *
 * - **截不截断**：这一屏是整页，按 [DESKTOP_ARTISTS_PAGE_SIZE] 摆全，所以 `overflow` 恒 0；
 *   首页那一栏按横卡的 8 位截，摆不下的念出来并把人指到这里。
 * - **取不取头像**：这一屏给**当前看得见的**几位去站方取一次（闸门见下），首页那一栏只读本机已有的。
 *   两处吃的键轨是同一枚 [DESKTOP_AVATAR_SOURCE_KEY]，所以这里取到的那几位，
 *   下次进首页那一栏就直接命中，不必再问。
 *
 * ## 视口闸门：为什么整屏 `forEach` 那一版不行
 *
 * 名单可以有几十位，而每位要**两笔**请求（链接表 → 探针）。进屏就把整张名单问一遍，
 * 付的是几十笔、换回来的是"这一屏第一次打开要转很久"。所以这一屏改成 [LazyColumn]
 * （`Column + verticalScroll + forEach` 那种形状拿不到"哪些位在视口里"），
 * 可见集从 `layoutInfo.visibleItemsInfo` 现算、随滚动自己更新，
 * 而**查过的（含"查过、没有"）记在 [probed] 里不重发** —— 少了那张表，来回滚动就是无限发请求。
 *
 * ## 这一屏**仍然**缺的那一层，要点名
 *
 * - **单人页批 19 才上架**：稿上那一行的 `.go` 那一层今天有了目的地（「看他的图」那枚），
 *   而它是**一条腿**的 —— 取那一行站点次序里的第一站，段头逐字点名是哪一站。
 *   （批 2 ⑤ 那之后"要看某位的图"先有了另一条动线：**点名字直接去搜这一位**，不再是要用户自己打字。
 *   两条都在，页脚那句把两者都说清 —— 判据 ⑥ 盯着那句里不许再写"那一层没有"。）
 * - **这一屏不摆关注那一步**：入口在详情浮层画师那一栏（批 2 ④ 上架）。空名单那一格说的是"本机还没有名单"，不是"暂不支持"。
 * - **三条来路都试**（批 42 起）：链接表里的 pixiv 主页 → fanbox → Mastodon，三条都没有才落字母座。
 *   这三条都由下面那两句读数念出来（本仓规矩：**点名差的是哪一层**，见手册 §12 一号原则）。
 */
@Composable
internal fun DesktopArtistsPage(
    ports: GalleryPorts,
    /**
     * 名单档**读不出来**那一句（store 只在解不开档时写它，含另存出的备份文件名），逐字念、桌面不重述。
     *
     * 必填、不给默认值，而且**不在这一屏里取**：`consumeNotice()` 读走即清，
     * 屏里自己消费就会随这一屏离开组合而消失（切走再切回来，屏上仍是那份空名单，而解释它的那句话没了）。
     * 主人是应用根，理由与调用处那三行注释同（批 3′）。
     */
    followsNotice: String?,
    /**
     * 那一行「看他的图」按下去要走到的目的地。**必填、不给默认值**：
     * 漏传的形状是一枚画出来而按不动的入口 —— 比不摆更糟，而这一屏今天确实有目的地可去（批 19）。
     */
    onOpenArtist: (DesktopArtistTarget) -> Unit,
) {
    val follows by ports.follows.follows.collectAsState()
    val favorites by ports.favorites.favorites.collectAsState()
    val scope = rememberCoroutineScope()
    val readout = remember(follows, favorites) { desktopArtistsReadout(follows, favorites) }
    val avatars = ports.avatars
    // 整页那一屏与首页那一栏共用同一颗行件 ⇒ 词典那一问两处都得发，否则同一位在两屏念成两个名字。
    val names = remember(readout) { readout.artists.map { it.name } }
    var artistTranslations by remember(names) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(names) {
        artistTranslations = if (names.isEmpty()) emptyMap() else runCatching { ports.lexicon.translations(names) }.getOrDefault(emptyMap())
    }
    // 本机档先播种：档里答上过的那几位第一帧就是真头像，不等异步（同 Android 2026-10-03 那条读数）。
    val seeded = remember(readout) {
        desktopArtistFaces(readout) { site, name -> avatars.peek(site, name, DESKTOP_AVATAR_SOURCE_KEY) }
    }
    // 本轮**问过且有定论**的（值可以是 null = 站方确实没有），判"问没问过"看的是键而不是值。
    val probed = remember(readout) { mutableStateMapOf<String, String?>() }
    // 本轮**试过**的（含没取到的）。两张表都要，少一张各出一族毛病：
    // 只记定论 ⇒ "没问到"那几位每帧重发（无限请求）；把失败也落进定论表 ⇒ 一次抖动 = 永久字母座。
    // 两张都随 `readout` 重建 ⇒ 换一次列表就允许再问。
    val tried = remember(readout) { mutableSetOf<String>() }
    val listState = rememberLazyListState()

    LaunchedEffect(readout) {
        // snapshotFlow 是**合流**的：取数那几秒里滚过去的中间位置会被丢掉，只交最新那一帧的可见集。
        // 于是"取到一半用户滚走了"这一笔照样跑完并落盘，而不是被取消掉、下次再白跑一遍。
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.key } }
            .distinctUntilChanged()
            .collect { keys ->
                // 一位一位**串行**：一次两笔、并发起来就是同时压站方十几笔（与 Android 那一栏同口径）。
                desktopVisibleProbeTargets(keys, resolved = probed.keys + tried, hasFace = seeded.keys)
                    .forEach { name ->
                        val artist = readout.artists.firstOrNull { it.name == name } ?: return@forEach
                        val probe = desktopArtistProbedFace(ports, artist)
                        desktopFaceProbeRecord(probed, tried, name, probe)
                        // 那句**只进日志、不上屏**（那颗件的类注释写的就是这一条，批 26 之前没人执行它）：
                        // 一屏几十位画师，一位网络抖动就在屏上留一行红字，读的人会以为整域出了事。
                        // 日志件自己带去重计数，所以逐位递也不会刷屏。
                        desktopFaceProbeNotice(probe).takeIf { it.isNotBlank() }?.let { line ->
                            DesktopGalleryFailureLog.warn("ArtistFace", "${name}：$line")
                        }
                    }
            }
    }

    val faces: Map<String, String?> = readout.artists.associate {
        it.name to (probed[it.name] ?: seeded[it.name])
    }

    val pad = DesktopGalleryMetrics.pageHorizontalPadding
    Column(Modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(start = pad, end = pad, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("关注画师", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    // 两个数都是现算的真读数：`follows.size` 是名单里**几条**（一站一条），
                    // `distinctTotal` 是并成**几位**（跨站同名并一条）。只念后一个会把"两站都关注过"这件事抹平。
                    Text(
                        "本机关注 ${follows.size} 条 · 跨站同名并成 ${readout.distinctTotal} 位",
                        color = DesktopTheme.TextTertiary,
                        fontSize = 12.5.sp,
                    )
                    // 逐字念 store 那一句（含备份文件名），桌面不重述 —— 重述的话 store 改了没人跟上。
                    followsNotice?.let {
                        Text(it, color = DesktopTheme.AccentBeni, fontSize = 12.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            if (readout.gap == GalleryFollowedArtistsGap.NO_FOLLOWS) {
                item(key = "empty") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("还没有关注画师", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "打开任意一张图，在画师那一栏点「关注」就进这一份名单。" +
                                "这一屏只念本机已有的名单，不替你猜该关注谁。",
                            color = DesktopTheme.TextSecondary,
                            fontSize = 12.5.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                items(items = readout.artists, key = { "${DESKTOP_ARTIST_ROW_KEY_PREFIX}${it.name}" }) { artist ->
                    // 取关只翻**已在名单上的那几站**：`toggle` 是翻转，对不在名单的那一站点下去是
                    // 把他**加进**名单 —— 与这一枚字的意思正好相反，而且不报错（判据 ⑦ 钉这一格）。
                    val followedSites = follows.filter { it.name == artist.name }.map { it.site }.toSet()
                    val targets = desktopUnfollowTargets(artist.sites, followedSites)
                    // 单人页是**一条腿**的：目的地取那一行站点次序里的第一站，而段头会把是哪一站逐字念出来。
                    val profile = desktopArtistProfileTarget(artist.name, artist.sites)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DesktopFollowedArtistRow(
                            artist,
                            desktopArtistFace(faces[artist.name], artist.previewUrl),
                            artistTranslations,
                            Modifier.weight(1f),
                        )
                        // 零站的那种行**整枚不摆**：摆一枚按下去只会说"没有目的地"的入口比不摆更糟（同一枚取关的纪律）。
                        if (profile != null) {
                            Button(onClick = { onOpenArtist(profile) }) {
                                Text("看他的图", fontSize = 13.sp)
                            }
                        }
                        // 没得翻就**整枚不摆**：摆一枚按下去只会新增关注的「取关」比不摆更糟。
                        if (targets.isNotEmpty()) {
                            Button(onClick = {
                                scope.launch { targets.forEach { site -> ports.follows.toggle(site, artist.name) } }
                            }) {
                                Text("取关", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            item(key = "foot") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        DesktopArtistsPageCaliberCaption,
                        color = DesktopTheme.TextTertiary,
                        fontSize = 11.5.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 那句"点名字去搜"要有名字可点才念 —— 名单为空那一屏上它说的是此刻做不到的事
                    // （判据 ⑧）。上面那颗口径说明讲的是圆座三档，空态也要念，所以没被一起圈进来。
                    if (readout.gap != GalleryFollowedArtistsGap.NO_FOLLOWS) {
                        Text(
                            "点名字去搜索那一屏搜这一位，「看他的图」进这一位的单人页（一次一站）。圆座与元信息不可点。",
                            color = DesktopTheme.TextTertiary,
                            fontSize = 11.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 这一屏圆座那三档的读数说明。
 *
 * 与首页那一栏共用的 [DesktopArtistFaceCaliberCaption] **分开**：那一句写的是"不主动去站方取"，
 * 对只读档的那一栏还是真话，对这一屏已经不是了 —— 两处共用一句，改一处就有一句变假。
 *
 * 三件事必须点名：闸门（只给看得见的几位取）、落盘（取到就存，下次不必再问）、
 * 以及**够得着的有三路**（pixiv / fanbox / Mastodon）。少列一件就是把"没接"说成"没有"。
 */
internal const val DesktopArtistsPageCaliberCaption: String =
    "卡面三档：本机存过的真头像 > 名下最近一张收藏 > 首字母座。这一屏只给当前看得见的几位去站方取一次头像，" +
        "滚过去不再加取，取到的存进本机。够得着的路有 pixiv、fanbox 与 Mastodon 三条，三条都没答上才落字母座。"
