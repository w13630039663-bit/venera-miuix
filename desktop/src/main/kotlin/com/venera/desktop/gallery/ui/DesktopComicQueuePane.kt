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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.venera.desktop.gallery.data.DesktopComicDownloadQueue
import com.venera.desktop.gallery.data.DesktopComicQueueStatus
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 第一百零五批 · 漫画域「下载队列」那一屏（一列任务 + 两枚把手）。
 *
 * ## 这一屏补的是哪一格
 *
 * 批 101 之后队列**能跑而看不见**：全局只有顶栏那枚牌子的一句摘要（`三站 · 2 话排队中`），
 * 它说得出"整体在哪"而说不出"每一笔在哪"；而「停这一笔」的把手只挂在详情那一屏 ——
 * 用户按完第三话退回列表时，手上那一笔就没有把手了。对岸有的**「撤掉这一笔」**（把行摘出去而不删页）
 * 桌面此前一处都没有。这一屏给的就是那一列 + 那两枚。
 *
 * ## 两枚把手各只改一件事（这是本文件存在的主要理由）
 *
 * - 「停这一笔」= 扳那颗**协作式**闸：行留下、页留下、状态由泵在页边界上落 `STOPPED`。
 *   它只挂在**正在跑**那一行上（`desktopComicQueueCanStop`）—— 排队那一笔还没开始，扳闸没有对象。
 * - 「撤掉这一笔」= 把那一行从表里摘出去：盘上那一棵目录一颗页都不动（回值就是"还剩几页"，现量）。
 *   每一行都摆得出它：摘表与档位无关，而且是**可逆**的（再说一次同一话就是重新排上，批 101 判式 ② 钉着那一档）。
 *
 * ⚠️ 撤掉的落点是 `scope.launch { withContext(Dispatchers.IO) { … } }` 那一跳，而**撤之前先扳闸**：
 * 桌面掐不掉在飞那一笔（批 100 那条边界），能做的只有让它写到页边界为止。少了那一下，形状就成了
 * "屏上已经念了撤掉了，而它一路写到最后一页才停"。`drop` 里刻意不清那颗闸，理由写在那一颗的注释上。
 *
 * ## 这一屏不挂「删除」也不挂「重下」
 *
 * 删除归「本机已下载」那一屏（批 98：回核→认→现量→动手→回读盘，删的凭据也在那里），
 * 重排归章节表上的长按。这一屏只说**过程**。所以本文件里 `deleteRecursively(` 一处都没有
 * （而全 main 仍恰一处，住在批 98 那颗件里）—— 判式钉的就是这条界线。
 *
 * ## 分家：装载那件碰表与盘，展示那件只吃纯数据
 *
 * 与批 97 那一屏同一条规矩：[DesktopComicQueueContent] 里不出现 `EngineSession`，
 * 所以它能被喂着离屏渲出来验三态与两枚把手各落到哪一颗回调。
 * 表本身由 `DesktopComicBody` 那一层 collect 一次再递下来（全桌面 collect 那一颗 StateFlow 的仍是两屏，没添第三处）。
 */
@Composable
internal fun DesktopComicQueuePane(downloadsRoot: File, tasks: List<DesktopComicQueueTask>, primed: Boolean) {
    var lastResult by remember(downloadsRoot) { mutableStateOf<String?>(null) }
    // 两枚把手各按了几次：那是这一屏自己的读数，而"按了"与"表里少了那一行"是两件事，得分开数。
    var stopPasses by remember(downloadsRoot) { mutableStateOf(0) }
    var removePasses by remember(downloadsRoot) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    // ⚠️ 键里放的是 `tasks.size` 而不是 `tasks`：泵每一页都刷一次表，拿整份表当键会变成"每写一页念一行"。
    LaunchedEffect(tasks.size, stopPasses, removePasses) {
        println(desktopComicQueueScreenReading(tasks, stopPasses, removePasses))
    }
    DesktopComicQueueContent(
        tasks = tasks,
        primed = primed,
        lastResult = lastResult,
        onStop = { task ->
            DesktopComicDownloadQueue.requestStop(task.taskId)
            stopPasses += 1
            lastResult = desktopComicQueueStoppedLine(task)
        },
        onRemove = { task ->
            // 先扳闸再摘行（见类注释）：行一摘，表里再没人管那一趟，只剩那颗闸能让它停在页边界上。
            DesktopComicDownloadQueue.requestStop(task.taskId)
            scope.launch {
                val pagesLeft = withContext(Dispatchers.IO) { DesktopComicDownloadQueue.drop(downloadsRoot, task.taskId) }
                removePasses += 1
                lastResult = desktopComicQueueRemovedLine(task, pagesLeft)
            }
        },
    )
}

/** 「停这一笔」那颗把手的文案（与「撤掉这一笔」必须两两不同 —— 一枚「取消」按钮会塌成第二种事实）。 */
internal const val DESKTOP_COMIC_QUEUE_STOP_LABEL = "停这一笔"

/** 「撤掉这一笔」那颗把手的文案。 */
internal const val DESKTOP_COMIC_QUEUE_REMOVE_LABEL = "撤掉这一笔"

/**
 * 展示那一件：**只吃纯数据与两枚回调**（一次 IO 都不做），所以它能被喂着离屏渲出来验
 * —— 与批 97 / 详情那两屏同一条分家规矩，理由是同一句。
 */
@Composable
internal fun DesktopComicQueueContent(tasks: List<DesktopComicQueueTask>, primed: Boolean, lastResult: String?, onStop: (DesktopComicQueueTask) -> Unit, onRemove: (DesktopComicQueueTask) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = DesktopGalleryMetrics.imageWallHorizontalPadding, vertical = DesktopGalleryMetrics.detailBodyGap),
        verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap),
    ) {
        Text(desktopComicQueueScreenHeader(tasks, primed), color = DesktopTheme.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        // 上一次的果：撤掉了 / 扳了停手 各一句，不许塌成一句（那是两种事实：行没了 与 行留着而写到页边界）。
        Text(
            lastResult ?: "停这一笔 = 让手上那一笔写到页边界为止；撤掉这一笔 = 把它从表里摘出去，盘上那些页不动",
            color = DesktopTheme.TextTertiary,
            fontSize = 12.sp,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap)) {
            items(tasks, key = { it.taskId }) { task ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        desktopComicQueueRowLine(task),
                        modifier = Modifier.weight(1f),
                        color = DesktopTheme.TextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (desktopComicQueueCanStop(task)) {
                        Button(onClick = { onStop(task) }) { Text(DESKTOP_COMIC_QUEUE_STOP_LABEL, fontSize = 12.sp) }
                    }
                    Button(onClick = { onRemove(task) }) { Text(DESKTOP_COMIC_QUEUE_REMOVE_LABEL, fontSize = 12.sp) }
                }
            }
        }
    }
}

/**
 * 表头那一句（上屏的话）。五档各一句，两两不许相同。
 *
 * ⚠️ 第一档是**「还没读到」**而不是"没有"：那一屏起手比读表那一跳快（包内实测到第一帧念「本机队列空」
 * 而盘上有三笔），把"还没读到"说成"读到了没有"正是批 97 那一屏立「正在数」那一档的同一族假话。
 * ⚠️ "没有排队而有已完成"那一档**不许**念成"队列空"：用户按了三次长按然后回这一屏，
 * 听到"空"就会以为那三下白按了。
 */
internal fun desktopComicQueueScreenHeader(tasks: List<DesktopComicQueueTask>, primed: Boolean): String {
    if (tasks.isEmpty() && !primed) return "正在读那张表…"
    val running = tasks.firstOrNull { it.status == DesktopComicQueueStatus.RUNNING }
    if (running != null) {
        val waiting = tasks.count { it.status == DesktopComicQueueStatus.PENDING }
        return "正在下 第 ${running.chapterOrder + 1} 话「${running.chapterTitle}」 · " +
            "${running.downloadedPages}/${running.totalPages} 页 · 另有 $waiting 笔排队"
    }
    val waiting = tasks.count { it.status == DesktopComicQueueStatus.PENDING }
    if (waiting > 0) return "$waiting 笔排队 · 一次只跑一笔 · 站在这一屏就接着跑"
    if (tasks.isEmpty()) return "本机队列空 —— 章节表上长按「下载」那一笔会排到这里来"
    return "这一页上 ${tasks.size} 笔都排完了（盘上那些页归「本机已下载」那一屏说）"
}

/**
 * 行里那一句：第几话、题名（用「」包起来 —— 源站的章节题名常长成「第2話」，与前缀挨着念就成了
 * `第 2 话 第2話`，批 97/98 两帧图证都拍到这一眼）、档位、页码、是哪一本。
 *
 * 失败那一档把站方原话带上（`error` 格）：只说「失败了」等于让用户再按一次去撞同一堵墙。
 */
internal fun desktopComicQueueRowLine(task: DesktopComicQueueTask): String {
    val base = "第 ${task.chapterOrder + 1} 话「${task.chapterTitle}」 · ${desktopComicQueueStatusText(task.status)} · " +
        "${task.downloadedPages}/${task.totalPages} 页 · 本子 ${task.comicId}"
    return if (task.status == DesktopComicQueueStatus.FAILED && task.error.isNotEmpty()) "$base · 因=${task.error}" else base
}

/** 档位那两个字（与顶栏牌子那句、与详情那一屏的行话同一套档名，各屏只是对象不同）。 */
internal fun desktopComicQueueStatusText(status: DesktopComicQueueStatus): String = when (status) {
    DesktopComicQueueStatus.PENDING -> "排队中"
    DesktopComicQueueStatus.RUNNING -> "正在下"
    DesktopComicQueueStatus.DONE -> "已排完"
    DesktopComicQueueStatus.FAILED -> "失败了"
    DesktopComicQueueStatus.STOPPED -> "已停手"
}

/**
 * 「停这一笔」只挂在**正在跑**那一行。
 *
 * 排队那一笔还没开始（扳了闸也只会在它开跑的第一页上立刻停手，那一格由「撤掉」说更清楚）；
 * 已排完 / 已停手 / 失败了 三档都没有在飞的东西可停。
 */
internal fun desktopComicQueueCanStop(task: DesktopComicQueueTask): Boolean =
    task.status == DesktopComicQueueStatus.RUNNING

/** 扳了停手那一句：**不许**说「已停 / 已暂停」—— 桌面是协作式，页边界才停，而那一句是立刻停的口气。 */
internal fun desktopComicQueueStoppedLine(task: DesktopComicQueueTask): String =
    "扳了停手：第 ${task.chapterOrder + 1} 话「${task.chapterTitle}」写到页边界为止，那一行的档位会自己变（这一笔留在表里）"

/** 撤掉了那一句：点名盘上还剩几页（现量），并指路到「本机已下载」那一屏 —— 撤掉不等于删干净。 */
internal fun desktopComicQueueRemovedLine(task: DesktopComicQueueTask, pagesLeft: Int): String =
    "撤掉了：第 ${task.chapterOrder + 1} 话「${task.chapterTitle}」已从表里摘出去，盘上那 $pagesLeft 页还留着（要删干净去「本机已下载」那一屏）"

/** 这一屏自己的取证读数（只进 stdout，不进界面）：行数与两枚把手各按了几次全从状态现取。 */
internal fun desktopComicQueueScreenReading(tasks: List<DesktopComicQueueTask>, stops: Int, removes: Int): String =
    "D_队列屏 行=${tasks.size} 停=$stops 撤=$removes"
