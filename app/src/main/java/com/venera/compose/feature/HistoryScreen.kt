/**
 * S5-4 历史页（对齐原版 `pages/history_page.dart`）。
 *
 * 原版是**网格** + 多选删除 + 清空（全部 / 仅未收藏），并没有「今天/昨天/更早」
 * 的时间分组（那是分阶段任务书里的设想）。这里按官方实现：网格 + 多选 + 清空。
 *
 * Batch 2 规范化重构（Stage 0~2 信息架构调整后作为主 Tab 运行）：
 *  - 空态统一 VeneraEmptyView（消除硬编码 emoji 与字面值字号）。
 *  - 卡片容器 VeneraCard，字号/间距/圆角全部 VeneraTokens。
 *  - 顶部二级工具条：普通态 = 布局切换 + 清空菜单入口；多选态 = Close /
 *    已选 N 项 / 全选 / 删除（动画平滑切换，颜色走 Token）。
 *  - 手势：selectionBack（多选退出）+ PredictiveBackOverlay（清空确认弹窗）保持联动；
 *    系统返回在多选态先退多选、非多选态由壳层处理 Tab 返回语义。
 */
package com.venera.compose.feature

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.venera.compose.components.ComicCardLayout
import com.venera.compose.components.ComicSharedTransition
import com.venera.compose.components.coverSharedElement
import com.venera.compose.components.ComicLayoutToggleButton
import com.venera.compose.components.VeneraEmptyView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.components.comicListColumnCount
import com.venera.compose.components.rememberContentWidth
import com.venera.compose.components.rememberComicListDisplayMode
import com.venera.compose.components.rememberPredictiveBackState
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AndroidHistoryScreen(
    onSelect: (ComicItem) -> Unit,
    /** 二级页返回。多选态下这个位置的动作是「退出多选」，与系统预测返回同语义。 */
    onBack: () -> Unit,
) {
    val tokens = VeneraTokens
    val vm: HistoryViewModel = viewModel()
    val records by vm.history.collectAsState()
    val displayMode = rememberComicListDisplayMode()
    var showClearMenu by remember { mutableStateOf(false) }
    val selectionBack = rememberPredictiveBackState(
        enabled = vm.multiSelectMode && !showClearMenu,
    ) { vm.exitMultiSelect() }
    // 大标题折叠 + 真实毛玻璃顶栏（页内自治）
    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 列数按网格实测可用宽推（宽屏加列），左右内边距要从可用宽里扣掉
    val (gridWidth, gridWidthModifier) =
        rememberContentWidth(tokens.spacing.rowHorizontal * 2)

    Box(modifier = Modifier.fillMaxSize()) {
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusBarTop + 104.dp),
                contentAlignment = Alignment.Center,
            ) {
                VeneraEmptyView(
                    title = "还没有阅读记录",
                    message = "去随便翻两页吧",
                    icon = Icons.Outlined.History,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(comicListColumnCount(displayMode.value, gridWidth)),
                contentPadding = PaddingValues(
                    start = tokens.spacing.rowHorizontal,
                    end = tokens.spacing.rowHorizontal,
                    top = statusBarTop + 104.dp + if (vm.multiSelectMode) 44.dp else 0.dp,
                    bottom = VeneraSpacing.bottomBarClearance,
                ),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
                // 挂载折叠与录制行为：下滑时大标题收起、真实高斯模糊背板淡入
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(topBarBehavior.nestedScrollConnection)
                    .blurBackdropSource(topBarBackdrop),
            ) {
                items(records, key = { "${it.comicId}-${it.sourceName}" }) { record ->
                    HistoryCard(
                        record = record,
                        detailed = displayMode.value == "detailed",
                        selected = (record.comicId to record.sourceName) in vm.selected,
                        multiSelectMode = vm.multiSelectMode,
                        onClick = {
                            if (vm.multiSelectMode) {
                                vm.toggleSelect(record)
                            } else {
                                onSelect(record.toComicItem())
                            }
                        },
                        onLongClick = { vm.enterMultiSelect(record) },
                    )
                }
            }
        }

        // ── 统一顶栏：置于前景层，毛玻璃对底层 LazyVerticalGrid 进行物理级模糊 ──
        VeneraTopAppBar(
            title = "历史",
            largeTitle = "历史",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            navigationIcon = {
                IconButton(
                    onClick = { if (vm.multiSelectMode) vm.exitMultiSelect() else onBack() }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (vm.multiSelectMode) "退出多选" else "返回",
                        tint = tokens.color.textPrimary,
                    )
                }
            },
            actions = {
                if (vm.multiSelectMode) {
                    TopBarAction(text = "全选", onClick = { vm.selectAll(records) })
                    TopBarAction(
                        text = "删除",
                        tint = com.venera.compose.ui.tokens.StatusColors.Failing,
                        onClick = { vm.deleteSelected() },
                    )
                } else {
                    IconButton(onClick = { showClearMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "清空选项",
                            tint = tokens.color.textSecondary,
                        )
                    }
                    ComicLayoutToggleButton(displayMode.value) { displayMode.value = it }
                }
            },
            bottomContent = {
                // 多选提示条放在 bottomContent 中，跟随 TopAppBar 一同悬浮折叠
                if (vm.multiSelectMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = tokens.spacing.rowHorizontal, vertical = tokens.spacing.space2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "退出多选",
                            tint = tokens.color.textPrimary,
                            modifier = Modifier
                                .size(tokens.spacing.iconButtonSize)
                                .clickable { vm.exitMultiSelect() }
                                .padding(tokens.spacing.space2),
                        )
                        Spacer(modifier = Modifier.width(tokens.spacing.space2))
                        Text(
                            text = "已选择 ${vm.selected.size} 项",
                            fontSize = tokens.type.body,
                            fontWeight = tokens.type.weightMedium,
                            color = tokens.color.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    if (showClearMenu) {
        ClearHistoryMenu(
            onDismiss = { showClearMenu = false },
            onClearUnfavorited = { vm.clearUnfavorited() },
            onClearAll = { vm.clearAll() },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(
    record: HistoryRecord,
    detailed: Boolean,
    selected: Boolean,
    multiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val tokens = VeneraTokens
    VeneraCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            ComicCardLayout(
                detailed = detailed,
                modifier = Modifier.padding(tokens.spacing.space3),
                cover = {
                    // 内容守卫：BLUR 命中打码（源级预设 + 用户规则）；HIDE 已在数据层兜底。
                    val guard = com.venera.compose.security.guard.ContentGuardManager.getInstance(LocalContext.current)
                    val maskState = guard.coverMaskStateFor(
                        sourceKey = record.sourceName,
                        title = record.title,
                        author = record.author,
                        comicId = record.comicId,
                    )
                    com.venera.compose.components.venera.VeneraCover(
                        url = record.coverUrl,
                        contentDescription = record.title,
                        shimmerWhileLoading = false,
                        mask = if (maskState == "VISIBLE") VeneraCoverMask.Visible else VeneraCoverMask.Masked,
                        modifier = Modifier
                            // 与详情页封面配对的共享元素：key 用 record.sourceName —— 交给
                            // 详情的 HistoryRecord.toComicItem() 填的正是同一个串，两端必然一致。
                            // 打码命中不飞（飞行内容渲染进 overlay，等于绕开页面裁剪）。
                            .coverSharedElement(
                                key = ComicSharedTransition.coverKey(record.sourceName, record.comicId),
                                allowFly = maskState == "VISIBLE",
                            )
                            .fillMaxWidth()
                            .height(if (detailed) tokens.spacing.historyCoverHeight else tokens.spacing.historyCoverHeight + tokens.spacing.space9),
                    )
                },
                content = {
                    Column(
                        modifier = Modifier.padding(
                            top = if (detailed) tokens.spacing.none else tokens.spacing.space1,
                            end = if (detailed && multiSelectMode) tokens.spacing.badgeSize else tokens.spacing.none,
                        )
                    ) {
                        Text(
                            text = record.title,
                            fontSize = if (detailed) tokens.type.body else tokens.type.caption,
                            fontWeight = tokens.type.weightMedium,
                            color = tokens.color.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(tokens.spacing.space2))
                        Text(
                            text = record.progressDescription(),
                            fontSize = tokens.type.overline,
                            color = tokens.color.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = record.sourceName,
                            fontSize = tokens.type.badge,
                            color = tokens.color.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
            if (multiSelectMode) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) tokens.color.primary else if (detailed) tokens.color.textPrimary else Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(tokens.spacing.space2)
                        .size(tokens.spacing.badgeSize),
                )
            }
        }
    }
}

/** 二级工具条文字按钮（全选 / 删除）：Token 化字号与内距，无涟漪。 */
@Composable
private fun ToolbarAction(text: String, onClick: () -> Unit) {
    val tokens = VeneraTokens
    Text(
        text = text,
        fontSize = tokens.type.caption,
        color = tokens.color.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.shape.small))
            .clickable(onClick = onClick)
            .padding(
                horizontal = tokens.spacing.space3,
                vertical = tokens.spacing.space2,
            ),
    )
}

@Composable
private fun ClearHistoryMenu(
    onDismiss: () -> Unit,
    onClearUnfavorited: () -> Unit,
    onClearAll: () -> Unit,
) {
    val tokens = VeneraTokens
    com.venera.compose.components.PredictiveBackOverlay(onDismiss = onDismiss) {
        Card(modifier = Modifier.padding(horizontal = tokens.spacing.space9)) {
            Column(modifier = Modifier.padding(tokens.spacing.space8)) {
                Text(
                    text = "清空历史记录",
                    fontSize = tokens.type.itemTitle,
                    fontWeight = tokens.type.weightSemibold,
                )
                Spacer(modifier = Modifier.height(tokens.spacing.space5))
                Text(
                    text = "「清空未收藏」会保留仍在你收藏夹里的漫画的阅读进度。",
                    fontSize = tokens.type.caption,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(modifier = Modifier.height(tokens.spacing.space7))
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space5)) {
                    Button(
                        onClick = { onClearUnfavorited(); onDismiss() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha)
                        ),
                    ) { Text("清空未收藏") }
                    Button(
                        onClick = { onClearAll(); onDismiss() },
                        modifier = Modifier.weight(1f),
                    ) { Text("全部清空") }
                }
                Spacer(modifier = Modifier.height(tokens.spacing.space5))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        color = tokens.color.surfaceVariant.copy(alpha = VeneraTokens.current.placeholderAlpha)
                    ),
                ) { Text("取消") }
            }
        }
    }
}

/** 顶栏内的文字操作按钮（多选态「全选 / 删除」用）。 */
@Composable
private fun TopBarAction(
    text: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    val tokens = VeneraTokens
    Text(
        text = text,
        fontSize = tokens.type.body,
        fontWeight = tokens.type.weightMedium,
        color = tint ?: tokens.color.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.shape.small))
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space2),
    )
}
