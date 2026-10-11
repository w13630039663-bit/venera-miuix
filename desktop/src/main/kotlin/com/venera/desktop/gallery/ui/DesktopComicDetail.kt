package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.engineprobe.EngineSession
import io.github.composefluent.component.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 详情那一屏的落点载荷（挂在应用根，与画廊的 `selected`、画师的 `artistTarget` 同一族）。
 *
 * [title] 是**卡上那个题名**：`loadInfo` 还没回来时屏上得有个题名，而那时站方题名我们还不晓得。
 * 拿漫画号充数是另一种谎（用户看到的是"这本叫 123"）。
 *
 * [cover] 是**入口那一枚封面地址**（首页卡上的那串 / 历史行里已有的那串 / 种子那趟没有 ⇒ 空串）。
 * 它存在的理由只有一格：写进 `comic_history` 的那一笔要它（对岸的口径是"详情那张优先、入口这枚兜底"，
 * 见 `desktopSessionCover`），而两端共用那张表。
 */
data class DesktopComicTarget(val comicId: String, val title: String, val cover: String)

/** 详情那一屏的三态。`Failed` 吃的是**已经收成一行**的原话（catch 现场负责一行化）。 */
internal sealed interface ComicDetailState {
    data object Loading : ComicDetailState
    data class Failed(val reason: String) : ComicDetailState
    data class Ready(
        val title: String,
        val description: String,
        val episodes: List<EngineSession.Episode>,
        /** `loadInfo` 答复里那一栏 `cover`（缺栏 = 空串）。历史那一笔的封面优先吃它。 */
        val cover: String,
    ) : ComicDetailState
}

/**
 * stdout 那一行：这一屏开得成不成、几章、简介有没有、封面取没取到。
 *
 * 为什么要有一行（而不是只看屏）：详情这一屏的三态里，`Loading` 与 `Failed` 在像素上都是
 * "左上角一行小字"，隔着一张分不出；而"话=0"这一格正是"整本翻不出页"那条老缺陷的形状。
 *
 * `封面=` 那一格只念有无、不念那串地址：它是这一笔历史封面的**第一个来源**，而"站方没给"与
 * "桌面没读"塌成同一格时，落库那行为空就查不出是哪一站（地址本身还可能带签名参数，不进 stdout）。
 */
internal fun desktopComicDetailReading(comicId: String, sourceKey: String, state: ComicDetailState): String {
    val head = "D_详情 源=$sourceKey 号=$comicId"
    return when (state) {
        ComicDetailState.Loading -> "$head 态=等回话"
        is ComicDetailState.Failed -> "$head 态=没成 因=${desktopComicDetailOneLine(state.reason)}"
        is ComicDetailState.Ready -> {
            val desc = state.description.trim()
            // 话号那一串是**给取证抄的**：`--read=<号>/<话号>` 要一枚真话号，而这一屏从前不念它
            // ⇒ 阅读那一屏只能靠屏上点一下才到得了，而鼠标是禁的（手册 §8）。批 30 的 `--ranking=` 同一套办法。
            "$head 态=上屏 话=${state.episodes.size} 简介=${if (desc.isEmpty()) "无" else "${desc.length}字"}" +
                " 封面=${if (state.cover.isBlank()) "无" else "有"} 话号=${desktopComicEpisodeIds(state.episodes)}"
        }
    }
}

/**
 * 前 5 枚话号，占位章念 `-`。
 *
 * 上限 5 是**有意的**：整本刷进 stdout 既没人抄，也会把那一行拉到几百字。
 * `-` 而不是空串：空串在两枚相邻的话号之间看不出来，而"这一章是占位章"正是取页时要换算法的那一格。
 */
internal fun desktopComicEpisodeIds(episodes: List<EngineSession.Episode>): String =
    episodes.take(5).joinToString(",") { if (it.placeholder || it.id.isBlank()) "-" else it.id }

/** 一行化：站方的原话常带 stack 尾巴，那会把读数拆成两行，抓读数的脚本就漏掉后半句。 */
private fun desktopComicDetailOneLine(reason: String): String =
    reason.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').trim().take(240)

/**
 * 漫画详情那一屏（批 80）：题名 + 简介 + 章节表 + 那枚收藏。
 *
 * ## 为什么引擎句柄是从外面递进来的
 *
 * `EngineSession` 是探索那一屏在 IO 线程里构造的 GraalJS context，它归那一屏的
 * `DisposableEffect` 关（`DesktopDomainNavigationTest` ⑩/⑪ 钉着：应用根不许自己造一颗）。
 * 详情这一屏要的只是"用同一颗问一次 `loadInfo`"，所以句柄由持有者传下来。
 *
 * ## 为什么这一屏不是覆盖层
 *
 * 画廊那张卡点开是覆盖层（半屏还能看见墙），而漫画详情要点开某一话——那是**换一屏**，
 * 与画师单人页同形。覆盖层那条路要处理"压在模糊之上"的一整族问题，这里没有收益。
 */
@Composable
internal fun DesktopComicDetailPane(
    session: EngineSession?,
    target: DesktopComicTarget,
    sourceKey: String,
    favorites: () -> LocalFavoritesManager,
    onBack: () -> Unit,
    onOpenEpisode: (DesktopComicReading) -> Unit,
) {
    var state by remember(target.comicId) { mutableStateOf<ComicDetailState>(ComicDetailState.Loading) }
    var favoriteOutcome by remember(target.comicId) { mutableStateOf<DesktopComicFavoriteOutcome?>(null) }
    val scope = rememberCoroutineScope()
    // key 带 session：装载没完成时这一格先摆"等回话"，句柄到手那一刻 effect 重跑并真去问。
    LaunchedEffect(session, target.comicId) {
        val engine = session
        if (engine == null) {
            state = ComicDetailState.Loading
            return@LaunchedEffect
        }
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                engine.details(target.comicId).let {
                    ComicDetailState.Ready(it.title, it.description, it.episodes, it.cover)
                }
            }
        }
        state = outcome.fold(
            { it },
            { ComicDetailState.Failed("${it::class.java.simpleName}: ${it.message?.take(240)}") },
        )
        println(desktopComicDetailReading(target.comicId, sourceKey, state))
    }
    DesktopComicDetailContent(
        state = state,
        target = target,
        favoriteOutcome = favoriteOutcome,
        onBack = onBack,
        onFavorite = {
            // 与卡角那一枚吃同一颗动作与同一份三态措辞（两处各写一句就会念成两个名字）。
            val card = EngineSession.ComicCard(currentTitle(state, target), target.comicId, "")
            scope.launch { favoriteOutcome = desktopAddComicFavorite(favorites, sourceKey, card) }
        },
        onOpenEpisode = onOpenEpisode,
    )
}

/**
 * 纯展示那一件：吃 [ComicDetailState] 这块纯数据，**不发请求、不读盘、不要引擎句柄**。
 *
 * 拆开的理由是这一件能被喂着离屏渲成语义树来验（`DesktopComicDetailSemanticsTest`）——
 * 装载那一件要真 GraalJS 与真源，测试里渲不出来；而"屏上那句话到底变没变"这一层
 * 从前只有起窗拍帧一条路（一批 3~5 分钟），现在是一条断言几秒。
 */
@Composable
internal fun DesktopComicDetailContent(
    state: ComicDetailState,
    target: DesktopComicTarget,
    favoriteOutcome: DesktopComicFavoriteOutcome?,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    /**
     * 某一话点开（进阅读那一屏）。必填、不给默认值：漏传的形状是一行**摆着能点的样子而按不动**，
     * 而批 81 之前这里正是"屏上写着「第1话」而按下去什么都不发生"。
     * 交回的是纯数据 [DesktopComicReading] 而不是引擎的那个 `Episode` —— 展示层一吃引擎侧的形状，
     * 语义树那一层就渲不出来了（`DesktopComicDetailWiringTest` ③ 钉着这一条）。
     */
    onOpenEpisode: (DesktopComicReading) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(4.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("← 返回列表", color = DesktopTheme.TextSecondary, fontSize = 11.sp)
            }
            Text(
                " ${currentTitle(state, target)}",
                color = DesktopTheme.TextPrimary,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(
                Modifier
                    .padding(start = 8.dp)
                    .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(4.dp))
                    .clickable(onClick = onFavorite)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("收藏", color = DesktopTheme.TextSecondary, fontSize = 11.sp)
            }
        }
        favoriteOutcome?.let { outcome ->
            Text(
                desktopComicFavoriteScreenReading(outcome),
                color = if (outcome is DesktopComicFavoriteOutcome.Failed) {
                    DesktopTheme.AccentBeni
                } else {
                    DesktopTheme.TextSecondary
                },
                fontSize = 12.sp,
            )
        }
        when (val current = state) {
            ComicDetailState.Loading ->
                Text("正在问源站这本的目录…", color = DesktopTheme.TextTertiary, fontSize = 12.sp)

            is ComicDetailState.Failed ->
                Text("没问到目录：${current.reason}", color = DesktopTheme.AccentBeni, fontSize = 12.sp)

            is ComicDetailState.Ready -> {
                // 简介：源不给就是一行都不摆（摆"暂无简介"是替源站撒一句它没说过的话）。
                current.description.trim().takeIf { it.isNotEmpty() }?.let { description ->
                    Text(
                        description,
                        color = DesktopTheme.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "共 ${current.episodes.size} 话",
                    color = DesktopTheme.TextTertiary,
                    fontSize = 12.sp,
                )
                // LazyColumn 而不套 verticalScroll：`LazyVerticalGrid × verticalScroll` 那一条整屏崩的
                // 不变式对这一族同样成立，这里靠 weight 拿到有界高度。
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(current.episodes.size) { index ->
                        val episode = current.episodes[index]
                        // 载荷在这一行就拼齐（起点与封面都是**这一处**的决定，见下面那两句注释）。
                        val reading = desktopComicReadingOf(target, episode, index, startPage = 0, detailCover = current.cover)
                        // 整行点得动（批 81）：从前这一行是死的 —— 屏上写着「第1话」而按下去什么都不发生。
                        // `clickable` 排在 `padding` 之前，那一圈内衬因此也算进命中区（与「重取」那枚同一口径）。
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(4.dp))
                                // 起点写在这里而不是由默认值补：从详情那一行进就是从头读（"从库里那一页续读"还没接）。
                                .clickable { onOpenEpisode(reading) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(
                                desktopReaderEpisodeLabel(episode.name, episode.placeholder, episode.id),
                                color = DesktopTheme.TextPrimary,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 站方题名没回来之前先用卡上那个；回来了以站方为准（空答复不算"题名是空串"）。 */
private fun currentTitle(state: ComicDetailState, target: DesktopComicTarget): String =
    (state as? ComicDetailState.Ready)?.title?.takeIf { it.isNotBlank() } ?: target.title
