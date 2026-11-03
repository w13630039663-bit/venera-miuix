package com.venera.desktop.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.contentMaxWidthFor
import com.venera.compose.components.venera.VeneraButton
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopComicQueueTask
import com.venera.desktop.gallery.ui.DESKTOP_COMIC_QUEUE_REMOVE_LABEL
import com.venera.desktop.gallery.ui.DESKTOP_COMIC_QUEUE_STOP_LABEL
import com.venera.desktop.gallery.ui.desktopComicQueueCanStop
import com.venera.desktop.gallery.ui.desktopComicQueueRowLine
import com.venera.desktop.gallery.ui.desktopComicQueueScreenHeader

/**
 * 桌面下载队列屏 —— 对应安卓 `DownloadScreen`（对岸那一屏在通知栏 + 下载管理页里）。
 *
 * ## 这一批补的是"有表没有门"
 *
 * 那张表和那一圈泵都已经有判据盯着（`DesktopComicDownloadQueueTest` / `DesktopComicQueueWakeTest`
 * / `DesktopComicQueueRowPumpTest`），而**全仓没有一屏 UI 能把那几行摆出来**：
 * `DesktopComicQueuePane.kt` 那 95 行只剩纯函数，`DesktopComicDownloadsPane.kt` 属于已被拆掉的
 * Fluent 平行线。于是"排了几笔 / 跑到第几页 / 哪一笔失败了 / 怎么停手"这四件事
 * 用户今天看不见也改不了 —— 逻辑接完了而门没开。
 *
 * ## 形状照源管理屏，组件只用同一套
 *
 * `VeneraButton` / `VeneraCard` / `VeneraTokens` + material3 `Text`（与其余五屏同一套），
 * **不引 Fluent 第二套**。文案与档位口径逐字复用 `gallery/ui/DesktopComicQueuePane.kt`
 * 里那几颗纯函数（它们各自有判据钉着），这里只负责把它们摆上屏，一个字都不另写。
 *
 * ## 三件事刻意没做（不悄悄省掉）
 *
 * - **不摆封面**：那一行要说的是档位与页码，封面在详情那一屏已经摆过；而这一屏自己判遮罩
 *   会撞 `DesktopMaskJudgementSingleSourceTest` ③（判据只有壳那一处）。
 * - **没有"全部开始 / 全部取消"**：对岸那两个动作在桌面上会同时对多笔扳闸，
 *   而泵恒串行 1 ⇒"全部开始"只是重排一遍表，说了像没说的是那枚假开关。
 * - **不在这删页**：撤掉只把那一行摘出表，盘上那些页归「本机已下载」那一屏说（口径抄自
 *   [com.venera.desktop.gallery.ui.desktopComicQueueRemovedLine] 那句）。
 */
@Composable
internal fun DesktopComicQueueScreen(
    mode: WideScreenLayoutMode,
    /** 那张表（壳里 collect 下来递的，屏自己不碰单例）。 */
    tasks: List<DesktopComicQueueTask>,
    /** 表**读回来过没有**：`false` 时念「正在读那张表」而不是「本机队列空」。 */
    primed: Boolean,
    /** 扳闸/撤掉之后的那一句（由壳现算，屏上照原话摆）。 */
    note: String?,
    /** 「停这一笔」：只挂在正在跑那一行（判据 [desktopComicQueueCanStop]）。 */
    onStop: (DesktopComicQueueTask) -> Unit,
    /** 「撤掉这一笔」：只把那一行摘出表，**盘上一颗页都不动**。 */
    onRemove: (DesktopComicQueueTask) -> Unit,
    /** 回底栏那一格。与源管理/详情同一条口径：二级页必须有出路。 */
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val maxWidth = contentMaxWidthFor(mode)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
                .fillMaxSize()
                .padding(horizontal = tokens.spacing.screenHorizontal),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
            ) {
                VeneraButton(onClick = onBack) { Text("← 返回") }
                Text(
                    text = "下载队列",
                    fontSize = tokens.type.screenTitle,
                    color = tokens.color.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }

            // 顶栏那一行念的是整张表的形状（空 / 排队 / 正在跑 / 都排完），四档各一句。
            Text(
                text = desktopComicQueueScreenHeader(tasks, primed),
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
            )

            if (note != null) {
                Text(
                    text = note,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(top = tokens.spacing.space2),
                )
            }

            Spacer(Modifier.height(tokens.spacing.space3))

            // 表还没读回来 / 读回来而确实是空的：两种都不摆行，也不摆"没有行"的假把手。
            if (tasks.isEmpty()) return@Column

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = tokens.spacing.space6),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
            ) {
                items(tasks, key = { it.taskId }) { task ->
                    QueueRow(task = task, onStop = { onStop(task) }, onRemove = { onRemove(task) })
                }
            }
        }
    }
}

/** 一笔一行：左边那一句念档位与页码，右边最多两枚把手。 */
@Composable
private fun QueueRow(
    task: DesktopComicQueueTask,
    onStop: () -> Unit,
    onRemove: () -> Unit,
) {
    val tokens = VeneraTokens
    VeneraCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = tokens.spacing.space3,
                vertical = tokens.spacing.space2,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
        ) {
            // 整行那句（第几话、「」、档位、页码、是哪一本、失败时带上站方原话）。
            Text(
                text = desktopComicQueueRowLine(task),
                fontSize = tokens.type.caption,
                color = tokens.color.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 「停这一笔」只挂在 RUNNING 那一行 —— 排队那一笔还没开始，
            // 终局那三档没有在飞的东西可停（判据一处不重写，直接问那颗纯函数）。
            if (desktopComicQueueCanStop(task)) {
                VeneraButton(onClick = onStop) {
                    Text(DESKTOP_COMIC_QUEUE_STOP_LABEL, fontSize = tokens.type.caption)
                }
            }
            // 「撤掉这一笔」挂在**每一档**上：跑完与失败的那些留在表里只是历史，
            // 而"清掉那张历史"没有别的门 —— 不许摆而按不动，也不许整行没有出路。
            VeneraButton(onClick = onRemove) {
                Text(DESKTOP_COMIC_QUEUE_REMOVE_LABEL, fontSize = tokens.type.caption)
            }
        }
    }
}
