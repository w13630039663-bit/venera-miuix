package com.venera.desktop.gallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.preferredExternalUrl
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Frame
import java.io.File

/**
 * 桌面第七十七批 批 77 · 下半 · 「用本机的一张图找」那一格的**界面那一段**。
 *
 * ## 分成两颗而不是一颗：入口与结果各住在屏上不同的位置
 *
 * 入口跟着输入行（对岸 `GallerySearchArea` 也是把它挂在搜索卡的输入行尾随格），
 * 而结果那一片长得比一行高得多。两颗共用一颗 [DesktopLocalReverseState]
 * （`mutableStateOf` 的属性，不是一份内存表 + 一份 state），所以"按完按钮之后结果那一格跟着动"
 * 是同一颗状态的两次读，不存在"哪一边先刷新"的账。
 *
 * ## 屏上每一句都来自批 35 那颗件
 *
 * 等结果那句、失败那句、命中那三句、封顶那句、站方那句「没有相似图」与「回了而摆不出」
 * 全部转交 `DesktopReverseSearchPolicy`。判据 ⑩ 把这件事做成机械尺子（那两句字面量在全树只许出现一次）。
 * 这一颗文件里**新写的句子只有三句**：入口的能力缺席那句、挑中的那一句、取消挑图那一句
 * —— 那三句是本机这一路才有的事，浮层那一路没有对应物，所以不是第二份口径。
 *
 * ## 结果那一片吃的高度是现成那一档
 *
 * `DesktopGalleryMetrics.detailHeroHeight`（浮层里给图留的那一档 200dp）而不是新造一个数：
 * 这一格与浮层那一格是**同一族读数**，占的视觉预算也该同档，而屏上两处高度一样时
 * 用户才不会读成"这是两种东西"。超出的部分自己滚（那一排是一条普通 Column 的 `verticalScroll`，
 * **不是**墙 —— 墙套进纵向滚动会当场崩，那条不变式在 `DesktopWallScrollNestingTest`）。
 *
 * ## ⚠️ 挑图那一跳今天拍不到（不许写成验过了）
 *
 * 系统文件框要**鼠标点**才走得完，而 agent 侧不许动鼠标（手册 §8）⇒ 本批的图证只能拍到
 * 入口那一行与结果那一片（种子从 `--reverse-file=` 直接给路径，跳过文件框）。
 * ⚠️ 2026-10-09 09:02 改判一处：那一跳**有读数了**，但不是本批自验的 —— 交付包起窗那一跑里
 * 有人按了那枚按钮并在文件框里挑了一张 2 MB 的 png（屏上「挑中的是 4a0f8a81-….png（2 MB）…」
 * 与 stdout 第二行同刻）。本鱼没驱动那一下，也没人能复核是谁点的 ⇒ 记成"有外部读数"，不记成"验过了"。
 * 判据 `DesktopReverseFileSeedTest` ③ 钉的是种子那条路不许自己去开框（开了会挂死在模态框上），
 * 而不是钉它"验过"。
 */

/**
 * 那一格的状态。
 *
 * 换域换回来会回到空态（这颗 holder 不是 `Serializable`，走 `rememberSaveable` 会在换域那一刻才抛 ——
 * 与本屏那颗查询历史同一族形状，所以这里只 `remember` 一份内存表）。
 * 真正需要留下的读数在 stdout 那两行里，不依赖这一格。
 */
internal class DesktopLocalReverseState {
    /** 挑中的那一句（闸口认下来才有；挡住时留 null 而把闸口那句交给落点那一档）。 */
    var pickedReading by mutableStateOf<String?>(null)

    var waiting by mutableStateOf(false)
    var outcome by mutableStateOf<DesktopReverseOutcome?>(null)

    /** 「打开来源」那句（成功时不摆，与浮层同一形状）。 */
    var notice by mutableStateOf<String?>(null)

    /**
     * 闸口那一颗：读数那一句要回答"这一笔到底发了没有、发的是哪一张"，
     * 所以它必须与发车用的**是同一颗**，不是事后凭 state 猜的。
     */
    var gate by mutableStateOf<DesktopLocalReverseGate?>(null)

    /**
     * 那一笔真抛过的异常。失败那第二行读数（型 + 栈顶帧）只能从**它**念，
     * 不能从被压成字符串的落点里猜 —— 猜出来的那一行等于什么都没念（判据 ⑬ 钉的是这一枚的用法）。
     */
    var pendingError by mutableStateOf<Throwable?>(null)
}

/**
 * 入口那一行：一枚按钮 + 挑中的那一句 + 种子那一条路。
 *
 * @param seedPath 取证种子（`--reverse-file=`）给的那一条路径。`null` = 本轮不自动发车（默认行为）。
 *   **必填、不给默认值**：漏传的形状是"参数打进去了而那一格永远不出现"，它不会报错 ——
 *   与批 36 那颗种子同一条理由（那一枚当年做成 compositionLocal 之后，判据全绿而屏上根本没动）。
 */
@Composable
internal fun DesktopGalleryLocalReverseEntryRow(
    state: DesktopLocalReverseState,
    ports: GalleryPorts,
    scope: CoroutineScope,
    seedPath: String?,
) {
    val hasKey by ports.credentials.sauceNaoHasKey.collectAsState()
    val maskMode by ports.contentGuard.nsfwMaskMode.collectAsState()
    // 文件框要一扇父窗口才贴得住（没有父窗口时它自己起一扇空的，会被主窗压在最底下）。
    // 父窗口从 AWT 那一份"本机可见的 Frame"里现取，而不是从组合里取一枚上下文对象：
    // 这一路今天没有鼠标走得到（手册 §8），拿不到也没关系 —— `FileDialog(null, …)` 合法，
    // 那一档只少了一个父窗口，不会崩，而这里也**不假装量到过**（真 shape 要等人手点的那一次）。
    val imageOwner = remember { Frame.getFrames().firstOrNull { it.isVisible } }

    val fireLocalReverse: (String?) -> Unit = { picked ->
        // 正在等的时候**不响应第二次**，而不是并两笔：与浮层那一格同一条口径
        // （一笔反搜要几秒，后到的那笔盖掉先到的那批是错的读数）。
        if (!state.waiting) {
            val gate = desktopLocalReverseGate(picked, hasKey)
            state.gate = gate
            state.pendingError = null
            state.notice = null
            state.outcome = null
            state.pickedReading = (gate as? DesktopLocalReverseGate.Proceed)
                ?.let { desktopLocalReversePickedReading(it.fileName, it.sizeBytes) }
            state.waiting = gate is DesktopLocalReverseGate.Proceed
            if (gate is DesktopLocalReverseGate.Blocked) {
                // 挡住 ⇒ 落点就是「没发车」那一档，那句原话由结果那一格摆出来。
                // 用户取消文件框走的也是这里（那一枚闸口自己念"先挑一张"，这里不替他编一句别的）。
                val notSent = DesktopReverseOutcome.NotSent(gate.why)
                state.outcome = notSent
                println(desktopLocalReverseReadout(picked, gate, notSent))
            }
            if (gate is DesktopLocalReverseGate.Proceed) {
                scope.launch {
                    val outcome = try {
                        desktopLocalReverseRun(
                            reverse = ports.reverse,
                            gate = gate,
                            allowNsfw = desktopAllowNsfw(maskMode),
                            readFile = { path -> File(path).readBytes() },
                            onThrowable = { failure -> state.pendingError = failure },
                        )
                    } catch (cancellation: CancellationException) {
                        // 取消**不落成一档**：换域、离开那一屏都会取消它，那一笔得原样交回协程层。
                        state.waiting = false
                        throw cancellation
                    }
                    println(desktopLocalReverseReadout(picked, gate, outcome))
                    if (outcome is DesktopReverseOutcome.Failed) {
                        println(desktopLocalReverseRefusalReading(outcome.reason, state.pendingError))
                    }
                    state.outcome = outcome
                    state.waiting = false
                }
            }
        }
    }

    // 种子那一条路：**跳过文件框**直接给路径，走的仍是按钮那同一枚动作（自己再写一遍发车就是第二份口径）。
    if (seedPath != null) {
        LaunchedEffect(seedPath) { fireLocalReverse(seedPath) }
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (desktopCanPickLocalImage()) {
            AccentButton(onClick = { fireLocalReverse(desktopPickLocalImage(imageOwner)) }, disabled = state.waiting) {
                Text(DESKTOP_LOCAL_REVERSE_ENTRY_LABEL, fontSize = 13.sp)
            }
        } else {
            // 与浮层那两枚同一条形状：不支持时**不摆那枚按钮**，而是把这句实话摆出来。
            Text("这台机器没给出挑本机图的路，那一枚不摆", color = DesktopTheme.TextTertiary, fontSize = 11.5.sp)
        }
        state.pickedReading?.let { line ->
            Text(line, color = DesktopTheme.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * 结果那一格：四档各念各的，与浮层那一片**同一批句子**。
 *
 * 什么都没有时整块不摆（不摆"还没有结果"那种占位：那一枚会把"还没按"与"按了而没回"画成同一个样子）。
 */
@Composable
internal fun DesktopGalleryLocalReverseResults(
    state: DesktopLocalReverseState,
    ports: GalleryPorts,
    scope: CoroutineScope,
) {
    val canBrowse = desktopCanBrowseExternally()
    if (state.waiting || state.outcome != null) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = DesktopGalleryMetrics.detailHeroHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap),
        ) {
            // 挡住的那几档在这里**没有**落点那一格（闸口没放行时上一行根本不会摆，见那颗入口行的注释）：
            // 屏上此刻有的是「挑中的是…」或什么都没有的一句 —— 而那正是那句"先挑一张"该出现的时刻。
            if (state.waiting) {
                Text(desktopReverseWaitingReading(), color = DesktopTheme.TextTertiary, fontSize = 12.sp)
            }
            when (val outcome = state.outcome) {
                is DesktopReverseOutcome.NotSent ->
                    Text(outcome.why, color = DesktopTheme.AccentBeni, fontSize = 12.sp)

                is DesktopReverseOutcome.Failed ->
                    Text(desktopReverseFailedReading(outcome.reason), color = DesktopTheme.AccentBeni, fontSize = 12.sp)

                is DesktopReverseOutcome.NoSimilar ->
                    Text(
                        desktopReverseNoSimilarReading(outcome.message, outcome.returned),
                        color = DesktopTheme.TextSecondary,
                        fontSize = 12.sp,
                    )

                is DesktopReverseOutcome.Answered -> {
                    Text(
                        "找同一张图的结果",
                        color = DesktopTheme.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    outcome.hits.forEach { hit ->
                        Column(verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.tightGap)) {
                            Text(desktopReverseHitReading(hit), color = DesktopTheme.TextPrimary, fontSize = 12.5.sp)
                            hit.title?.let { title ->
                                Text(
                                    title,
                                    color = DesktopTheme.TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(desktopReverseArtistReading(hit), color = DesktopTheme.TextSecondary, fontSize = 12.sp)
                            desktopReverseSiteRefReading(hit)?.let { ref ->
                                Text(ref, color = DesktopTheme.TextTertiary, fontSize = 11.5.sp)
                            }
                            // 「打开来源」走 `preferredExternalUrl` 而不是第一条：站方给的 `source`
                            // 常是一张裸图直链，口径与浮层那一片同一颗（那一颗件在共享面）。
                            val source = preferredExternalUrl(hit.extUrls)
                            if (source != null && canBrowse) {
                                Text(
                                    "打开来源",
                                    color = DesktopTheme.AccentFuji,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable {
                                        scope.launch { state.notice = openInBrowser(source) }
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        desktopReverseCapReading(cap = outcome.cap, taken = outcome.hits.size),
                        color = DesktopTheme.TextTertiary,
                        fontSize = 11.5.sp,
                    )
                }

                null -> Unit
            }
            state.notice?.let { line ->
                Text(line, color = DesktopTheme.TextSecondary, fontSize = 12.sp)
            }
        }
    }
}
