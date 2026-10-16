package com.venera.desktop.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.venera.desktop.gallery.data.DesktopComicDownloadedChapter
import com.venera.desktop.gallery.data.DesktopComicDownloadedGroup
import com.venera.desktop.gallery.data.desktopComicChaptersByComic
import com.venera.desktop.gallery.data.desktopComicDownloadedChapters
import com.venera.desktop.gallery.data.desktopComicDownloadedHeaderReading
import com.venera.desktop.gallery.data.desktopComicDownloadedSkipped
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.data.desktopComicSafePathKey
import com.venera.desktop.gallery.data.DesktopComicShelfRawIdIndex
import com.venera.desktop.gallery.data.desktopComicShelfRerollKey
import com.venera.desktop.gallery.data.desktopComicRepairChapter
import com.venera.desktop.gallery.data.desktopComicRepairLabel
import com.venera.desktop.gallery.data.desktopComicRepairSeedHitLine
import com.venera.desktop.gallery.data.desktopComicRepairSeedMissLine
import com.venera.desktop.gallery.data.desktopComicRepairShouldShow
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 批 97 · 本机存过的那些**整话**（漫画域首页「本机已下载」那一行的那一屏）。
 *
 * ## 这一屏只有一件事：数盘上已有的
 *
 * 与画廊那一屏（`DesktopDownloadsPane`）同一条分工：写的那一路住在 `DesktopComicChapterDownload`（长按才走），
 * 这一路只把 `数据根/downloads/<站名键>/<号>/<话目录>` 里数得出的东西念出来。所以形参是一颗 `File` 而不是
 * `PathProvider`、更不是引擎句柄 —— 递进来一条能顺手建目录或问站方的手，这一屏就再也测不动了
 * （离屏语义树要能喂着一渲，判据 ① 钉"这一颗件里不出现 `EngineSession`"）。
 *
 * ## 为什么不摆封面也不摆页图
 *
 * 这一屏的证据是**号、话名、盘上页数、字节数** —— 四样都从盘上取。摆一张图就得把字节再解一遍，
 * 而"这一话存下来了"不需要解码证明；更要紧的是走 Coil 就会混进第二种来源（缓存里那张与盘上那张是两份东西），
 * 那一档在画廊下载屏同样的理由被拒过一次。
 *
 * ## 「还没东西」在这一屏上有三种事实，不许塌成一句
 *
 * 目录还没建出来 / 目录在而里面没有整话 / 里面的清单读不出（手工放的书、导进来的裸目录、半截的 JSON）。
 * 把第三种说成"没有"，用户就会以为长按那一下白按了 —— 正是这一屏要治的那条缺口，绝不能在新屏里再造一次。
 */
@Composable
internal fun DesktopComicDownloadsPane(downloadsRoot: File, deleteSeed: Pair<String, String>?, repairSeed: Pair<String, String>?) {
    var state by remember(downloadsRoot) { mutableStateOf<DesktopComicDownloadListing?>(null) }
    // 删完要重数：那一格不是"刷新按钮"，是"盘上少了一棵"这件事的凭据 ⇒ 只由真删那一跳推进。
    var pass by remember(downloadsRoot) { mutableStateOf(0) }
    var armedKey by remember(downloadsRoot) { mutableStateOf<String?>(null) }
    var lastResult by remember(downloadsRoot) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // 何时再读一次盘：队列里**任何一笔落到终局**（补完了 / 失败了 / 停手了）就说明盘上变了。
    // 这一枚订阅只贡献"何时再读"，屏上那些行仍只从下面那一跳的读盘结果来（判式 ⑦ 钉 `collectAsState` 恰一处）。
    val rerollKey = desktopComicShelfRerollKey(DesktopComicDownloadQueue.tasks.collectAsState().value)
    LaunchedEffect(downloadsRoot, pass, rerollKey) {
        // 读盘只在这一跳里发生（组合期一次都不许碰盘 —— 判据钉这一颗文件里 `withContext(Dispatchers.IO)` 恰一处）。
        state = withContext(Dispatchers.IO) {
            val read = DesktopComicDownloadListing(
                groups = desktopComicChaptersByComic(desktopComicDownloadedChapters(downloadsRoot)),
                skipped = desktopComicDownloadedSkipped(downloadsRoot),
                rootExists = downloadsRoot.isDirectory,
            )
            println(desktopComicDownloadedReading(read))
            read
        }
    }
    var seedHandled by remember(downloadsRoot) { mutableStateOf(false) }
    LaunchedEffect(state, deleteSeed) {
        val seed = deleteSeed ?: return@LaunchedEffect
        val listing = state ?: return@LaunchedEffect
        if (seedHandled) return@LaunchedEffect
        seedHandled = true
        val target = listing.groups.flatMap { it.chapters }
            .firstOrNull { desktopComicSafePathKey(seed.first) == it.comicId && it.chapterId == seed.second }
        if (target == null) {
            // 落空也要出声（三档落空都念，静默会把「我话号打错了」读成「删除坏了」）。
            println(desktopComicDeleteSeedMissLine(seed.first, seed.second))
            lastResult = "种子要删的那一话本机没有（号=" + seed.first + " 话=" + seed.second + "）"
            return@LaunchedEffect
        }
        println(desktopComicDeleteSeedHitLine(seed.first, seed.second))
        lastResult = desktopComicDeleteScreenLine(target, desktopComicDeleteChapter(downloadsRoot, target))
        pass += 1
    }
    // 把手与 `--repair=` 种子走**同一条路**（这一颗文件里 `desktopComicRepairChapter(` 恰一处 ⇒ 屏上没有第二条入队路）。
    // 只入队，不碰盘（判式 ⑤ 钉着）。入队那一刻**不推重数**：那时盘上还是老样子，重数出来的还是同一句「缺 N 页」
    // （批 111 在这里留的是 `pass += 1`，包内读数把它判成假话 —— 改口由上面那枚终局键负责）。
    // 队列表是单例，回收那张表只能在**要用的那一刻**现读（组合期读它会把屏上「已下全」绑到队列状态上）。
    val shelfRawIdIndex = { DesktopComicShelfRawIdIndex.of(DesktopComicDownloadQueue.tasks.value) }
    val fireRepair: (DesktopComicDownloadedChapter) -> Unit = { chapter ->
        lastResult = desktopComicRepairChapter(
            downloadsRoot, chapter,
            shelfRawIdIndex(),
        )
    }
    var repairSeedHandled by remember(downloadsRoot) { mutableStateOf(false) }
    LaunchedEffect(state, repairSeed) {
        val seed = repairSeed ?: return@LaunchedEffect
        val listing = state ?: return@LaunchedEffect
        if (repairSeedHandled) return@LaunchedEffect
        repairSeedHandled = true
        val target = listing.groups.flatMap { it.chapters }
            .firstOrNull { desktopComicSafePathKey(seed.first) == it.comicId && it.chapterId == seed.second }
        if (target == null) {
            // 落空也要出声（与删除那一枚同一判据：静默会把「我话号打错了」读成「补齐坏了」）。
            println(desktopComicRepairSeedMissLine(seed.first, seed.second))
            lastResult = "种子要补的那一话本机没有（号=" + seed.first + " 话=" + seed.second + "）"
            return@LaunchedEffect
        }
        println(desktopComicRepairSeedHitLine(seed.first, seed.second, target, shelfRawIdIndex()))
        fireRepair(target)
    }
    val current = state
    if (current == null) {
        // 还没读出来时念"正在数"，不许先念"一条都没有" —— 那是把"还没读到"说成"读到了空"。
        Text("正在数本机存过哪些整话", color = DesktopTheme.TextTertiary, fontSize = 12.sp)
    } else {
        DesktopComicDownloadsContent(
            current,
            armedKey = armedKey,
            lastResult = lastResult,
            onRepair = fireRepair,
            onDelete = { chapter ->
                val rowKey = desktopComicDeleteRowKey(chapter)
                if (desktopComicDeleteShouldFire(armedKey, rowKey)) {
                    armedKey = null
                    scope.launch {
                        lastResult = desktopComicDeleteScreenLine(chapter, desktopComicDeleteChapter(downloadsRoot, chapter))
                        pass += 1
                    }
                } else {
                    armedKey = desktopComicDeleteNextKey(armedKey, rowKey)
                }
            },
        )
    }
}

/**
 * 展示那一件：**只吃纯数据与一个动作**（一次 IO 都不做），所以它能被喂着离屏渲出来验
 * —— 与详情那一屏（`DesktopComicDetailContent`）同一条分家规矩，理由是同一句。
 */
@Composable
internal fun DesktopComicDownloadsContent(state: DesktopComicDownloadListing, armedKey: String?, lastResult: String?, onRepair: (DesktopComicDownloadedChapter) -> Unit, onDelete: (DesktopComicDownloadedChapter) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = DesktopGalleryMetrics.imageWallHorizontalPadding, vertical = DesktopGalleryMetrics.detailBodyGap),
        verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap),
    ) {
        Text(desktopComicDownloadedScreenHeader(state), color = DesktopTheme.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        // 上一次的果：四档各一句，而"没删成"那三档绝不与"删掉了"同形（判据 ⑫ 钉着）。
        Text(lastResult ?: "缺页的那一行右边多一枚把手：只把缺的那几页补上，盘上已有的不动；另一枚把那一话从本机删掉（删了要重下一遍）", color = DesktopTheme.TextTertiary, fontSize = 12.sp)
        val groups = state.groups
        LazyColumn(verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap)) {
            groups.forEach { group ->
                items(group.chapters, key = { desktopComicDeleteRowKey(it) }) { chapter ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            desktopComicDownloadedRowLine(chapter),
                            modifier = Modifier.weight(1f),
                            color = DesktopTheme.TextPrimary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (desktopComicRepairShouldShow(chapter)) {
                            Button(onClick = { onRepair(chapter) }) {
                                Text(desktopComicRepairLabel(chapter), fontSize = 12.sp)
                            }
                        }
                        Button(onClick = { onDelete(chapter) }) {
                            Text(
                                if (desktopComicDeleteRowKey(chapter) == armedKey)
                                    desktopComicDeleteArmedLabel(chapter.order + 1)
                                else desktopComicDeleteIdleLabel(chapter.order + 1),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 读盘那一趟交回的三样（`rootExists` 必须从状态现取 —— 只看条数就把"目录还没建"与"建好而空"塌成一句）。 */
internal data class DesktopComicDownloadListing(
    val groups: List<DesktopComicDownloadedGroup>,
    val skipped: Int,
    val rootExists: Boolean,
)

/** 表头那一句（上屏的话）。三态各一句，与读数那一行是**两句不同的话**：读数给取证，这句给用户。 */
internal fun desktopComicDownloadedScreenHeader(listing: DesktopComicDownloadListing): String {
    val groups = listing.groups
    val chapters = groups.flatMap { it.chapters }
    return when {
        chapters.isEmpty() && listing.skipped == 0 && !listing.rootExists -> "本机还没存过整话（那一目录还没建出来）"
        chapters.isEmpty() && listing.skipped == 0 -> "存话的目录建好了，里面还一条都没有"
        chapters.isEmpty() -> "那一目录里有 ${listing.skipped} 条清单读不出（手工放的书或下到一半的话），今天摆不出"
        else -> "本机存过 ${groups.size} 本 · ${chapters.size} 话 · 齐了 ${chapters.count { it.complete }} 话" +
            (if (listing.skipped > 0) " · 另有 ${listing.skipped} 条读不出" else "")
    }
}

/**
 * 行里那一句：**号**、哪一话、盘上真有几页、齐没齐、多大。一屏可能摆着好几本，号不带上就分不出哪一话属于哪一本。
 *
 * ⚠️ 题名用「」包起来：源站的章节题名本身就常长成「第2話」，与前缀那截挨着念就成了
 * `第 2 话 第2話`（批 97/98 两帧图证都拍到这一眼）。
 */
internal fun desktopComicDownloadedRowLine(chapter: DesktopComicDownloadedChapter): String =
    if (chapter.complete) {
        "${chapter.comicId} · 第 ${chapter.order + 1} 话「${chapter.title}」 · ${chapter.writtenPages}/${chapter.expectedPages} 页 · 已下全 · ${desktopByteLabel(chapter.bytes)}"
    } else {
        "${chapter.comicId} · 第 ${chapter.order + 1} 话「${chapter.title}」 · ${chapter.writtenPages}/${chapter.expectedPages} 页 · 缺 ${chapter.expectedPages - chapter.writtenPages} 页 · ${desktopByteLabel(chapter.bytes)}"
    }

/** 那一屏的取证读数：六格全从状态现取（与表头那句同一次读盘，不另走一趟）。 */
internal fun desktopComicDownloadedReading(listing: DesktopComicDownloadListing): String =
    desktopComicDownloadedHeaderReading(listing.groups, listing.skipped)
