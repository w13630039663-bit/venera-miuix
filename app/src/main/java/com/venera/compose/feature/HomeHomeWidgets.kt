package com.venera.compose.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.ComicSharedTransition
import com.venera.compose.components.coverSharedElement
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraShimmer
import com.venera.compose.data.db.HistoryRecord
import com.venera.compose.source.model.Comic
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

/*
 * 首页的**展示零件**（2026-10-20 从 `HomeScreen.kt` 抽出来，两端共用）。
 *
 * ## 为什么单独一颗文件
 *
 * `HomeScreen.kt` 有两半：上半是 `AndroidHomeScreen`（绑 `viewModel()` / `Lifecycle` /
 * `ComicSourceManager`，只能留在 `:app`），下半是这十几个子件 —— 它们**只吃形参**。
 *
 * 抽出来的直接收益：桌面端能用**同一颗**零件摆首页（统计格 / 推荐轮播 / 历史卡 /
 * 骨架），而不是拿画廊图墙冒充。这正是「两端编译同一份源码」要的形状。
 *
 * ## 平台调用去了哪
 *
 * 这一批原本有 4 处平台耦合，2026-10-20 全部收进 `LocalUxPlatformServices`：
 *  - 内容守卫单例 ×2 → `ux.coverMaskStateOf(…)`
 *  - 手势排除区 → `ux.setGestureExclusionRects(…)`
 * 收口之后本文件零 `import android.`、零 `LocalContext`。
 */


/* ------------------------------------------------------------------ *
 * 局部组件（仅 HomeScreen 使用）
 * ------------------------------------------------------------------ */

/** 统计单元格：数字为主、标签为辅。 */
@Composable
internal fun StatCell(value: Int, label: String, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            fontSize = tokens.type.statNumber,
            fontWeight = tokens.type.weightBold,
            color = tokens.color.textPrimary,
        )
        Spacer(Modifier.height(tokens.spacing.space1))
        Text(
            text = label,
            fontSize = tokens.type.caption,
            color = tokens.color.textSecondary,
        )
    }
}

/** 极弱分隔线：仅用于统计区，不引入新的 Card 风格。 */
@Composable
internal fun StatDivider() {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .width(tokens.spacing.space1)
            .height(tokens.spacing.statDividerHeight)
            .background(tokens.color.divider.copy(alpha = tokens.current.dividerAlpha))
    )
}

/** 胶囊按钮：prominent = 主操作（管理本地），否则为次要信息胶囊。 */
@Composable
internal fun PillButton(text: String, prominent: Boolean, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    Surface(
        shape = RoundedCornerShape(if (prominent) tokens.shape.small else tokens.shape.large),
        color = if (prominent) tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha)
        else tokens.color.primaryContainer.copy(alpha = tokens.current.badgeAlpha),
        modifier = modifier,
    ) {
        Text(
            text = text,
            fontSize = tokens.type.caption,
            color = if (prominent) tokens.color.textPrimary else tokens.color.primary,
            fontWeight = if (prominent) tokens.type.weightMedium else tokens.type.weightRegular,
            modifier = Modifier.padding(
                horizontal = if (prominent) tokens.spacing.space6 else tokens.spacing.space5,
                vertical = tokens.spacing.space3,
            ),
        )
    }
}

/** 历史卡片：封面 + 标题两行 ellipsis + 进度（次要文字色）。 */
@Composable
internal fun HistoryCardHome(
    record: HistoryRecord,
    ux: com.venera.compose.components.UxPlatformServices,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier
            .width(tokens.spacing.historyCardWidth)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(tokens.spacing.historyCoverHeight)
                .clip(RoundedCornerShape(tokens.shape.medium))
                .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha)),
        ) {
            // 内容守卫：历史记录 sourceName 是显示名，由守卫别名解析后走源级预设判定。
            // 2026-10-20：判定链本身没动，只是"怎么拿到 ContentGuardManager"收进了
            // `LocalUxPlatformServices`（那颗是 Android 静态单例 + Context，桌面侧没有）。
            val maskState = ux.coverMaskStateOf(
                sourceKey = record.sourceName,
                title = record.title,
                author = record.author,
                comicId = record.comicId,
            )
            com.venera.compose.components.venera.VeneraCover(
                url = record.coverUrl,
                contentDescription = record.title,
                shimmerWhileLoading = false,
                mask = if (maskState == "VISIBLE") com.venera.compose.components.venera.VeneraCoverMask.Visible
                       else com.venera.compose.components.venera.VeneraCoverMask.Masked,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(tokens.spacing.space3))
        Text(
            text = record.title,
            fontSize = tokens.type.caption,
            fontWeight = tokens.type.weightMedium,
            color = tokens.color.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // 阅读进度：保留原有数据（lastChapterTitle + lastPageIndex），仅降级为次要文字
        Text(
            text = record.lastChapterTitle + " · P." + (record.lastPageIndex + 1),
            fontSize = tokens.type.caption,
            color = tokens.color.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 「可能你感兴趣」的内容分级切换（正常 / R18）。
 *
 * 为什么不用 [VeneraSegmentedButton]：那一件是**整条通栏**的分段条（每颗 weight(1f)、高 48dp），
 * 放进分区标题行会把这一行撑成一整块控件，标题与「换一批」都被挤没。这里要的是贴在标题右侧的
 * 一枚紧凑药丸对，所以按同一套设计口径（选中 = 实心 primary + onPrimary 字；未选 = surfaceVariant
 * 半透明、胶囊圆角）单独收一枚，高度跟着标题行走。
 *
 * 两档绑定的源在 HomeViewModel 的 NORMAL_/R18_RECOMMEND_SOURCES 里，这一件只负责切档。
 */
@Composable
internal fun RecommendModeSelector(
    selected: HomeRecommendMode,
    onSelect: (HomeRecommendMode) -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha))
            .padding(tokens.spacing.space1),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeRecommendMode.entries.forEach { mode ->
            val active = mode == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (active) tokens.color.primary else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space1),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.label,
                    fontSize = tokens.type.caption,
                    fontWeight = if (active) tokens.type.weightSemibold else tokens.type.weightMedium,
                    color = if (active) tokens.color.onPrimary else tokens.color.textSecondary,
                )
            }
        }
    }
}

/**
 * 推荐轮播：material3 的 **MD3 标准「中央 Hero」轮播**（`HorizontalCenteredHeroCarousel`）——
 * 一张主卡在正中，左右各挂一张缩窄的侧卡；主卡从两侧平滑滑入中心，两侧同时反向缩放，
 * 全部由组件自带的 keyline 插值完成（不引第三方库、不做真 3D 变换）。
 *
 * 尺寸口径照 material3 官方 sample：`maxItemWidth` 不指定 → 主卡吃掉「视口减去两张侧卡与两枚间距」。
 * 侧卡目标宽不由我们定，库的算法是「主卡目标除以 3，再夹进 min/max」（本仓夹在 64~88dp）。
 * 库里那句原文是 "Visually balanced layouts should aim to use small items that are 1/3 the size
 * of large items" —— 主卡与侧卡 3:1 就是 MD3 中央 Hero 的平衡档，不是排版失手，也不是宽度分配失衡。
 * 现量（MuMu 1440x2560 @544dpi，`_qa/ui-audit-2026-10-14/after/b5-00-home.png` 第 1300 行逐像素扫描）：
 * 主卡 691px 合 203dp、侧卡 217px 合 64dp、间距 8dp —— 侧卡正好落在 min 那一端。
 * 夹在视口三分之一以内是硬约束：侧卡再宽，求解器就排成「两张大卡」而不是一个 Hero。
 *
 * 交互沿用 MD3 约定：点焦点卡进详情，点侧卡先把焦点滚过去。
 * 标题只给焦点卡（单独摆在轮播下方）—— 卡片宽度边滑边变，文字放在卡里会跟着重排抖动。
 * 「换一批」后随机落一个焦点位，让刷新在视觉上也真的是新一批；首次进入仍从第 0 张看起。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecommendCarousel(
    ux: com.venera.compose.components.UxPlatformServices,
    comics: List<Comic>,
    batch: Int,
    onOpen: (Comic) -> Unit,
) {
    val tokens = VeneraTokens
    val state = rememberCarouselState { comics.size }
    val animScope = rememberCoroutineScope()
    val initialBatch = remember { batch }
    LaunchedEffect(batch) {
        if (batch == initialBatch || comics.size < 2) return@LaunchedEffect
        state.animateScrollToItem(Random.nextInt(comics.size))
    }
    HorizontalCenteredHeroCarousel(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .height(tokens.spacing.recommendHeroHeight)
            // 排除区挂在 padding **之前** —— 量到的是含两侧 24dp 让位的整行，才盖得住屏幕边缘热区
            .systemGestureExclusionBand()
            // 两侧各让开 24dp：一是 MD3 官方 sample 就是这么摆 Hero 的，二是让横拖的落点
            // 离开系统「边缘返回」热区（边缘热区内的触摸应用收不到，只能靠让位 + 排除区两招）。
            .padding(horizontal = tokens.spacing.space10),
        itemSpacing = tokens.spacing.space4,
        minSmallItemWidth = tokens.spacing.recommendSideMinWidth,
        maxSmallItemWidth = tokens.spacing.recommendSideMaxWidth,
    ) { index ->
        val comic = comics[index]
        val focused = state.currentItem == index
        RecommendCarouselItem(
            comic = comic,
            ux = ux,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clickable {
                    if (focused) onOpen(comic) else animScope.launch { state.animateScrollToItem(index) }
                }
                .maskClip(RoundedCornerShape(tokens.shape.extraLarge)),
        )
    }
    val focal = comics.getOrNull(state.currentItem)
    if (focal != null) {
        // 标题与作者都居中：Hero 在正中，左对齐的文字会让整块看起来往左倒。
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(tokens.spacing.space3))
            Text(
                text = focal.title,
                fontSize = tokens.type.caption,
                fontWeight = tokens.type.weightMedium,
                color = tokens.color.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (focal.subTitle.isNotBlank()) {
                Text(
                    text = focal.subTitle,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 宽屏档的推荐区形态：按全站漫画网格同一列宽口径排**单行**封面卡。
 *
 * 只做一行 —— 首页首屏厚度是定过的（历史记录那一区正是从两行改成单行才不压首屏），
 * 推荐区没理由更厚，所以宽屏取 columns 张铺满一行就收。
 * 封面比例复用应用内现成的 historyCardWidth : historyCoverHeight（124 : 170），不新造数；
 * 卡片本体仍走 [RecommendCarouselItem]，共享元素飞行与封面遮罩判定因此和轮播完全一致。
 */
@Composable
internal fun RecommendGridRow(
    ux: com.venera.compose.components.UxPlatformServices,
    comics: List<Comic>,
    columns: Int,
    onOpen: (Comic) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.gridGap),
    ) {
        comics.forEach { comic ->
            Column(modifier = Modifier.weight(1f)) {
                RecommendCarouselItem(
                    comic = comic,
                    ux = ux,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(tokens.spacing.historyCardWidth / tokens.spacing.historyCoverHeight)
                        .clickable { onOpen(comic) }
                        // maskClip 是轮播 CarouselItemScope 的成员扩展，网格这里用不上；
                        // 卡片不滚动变形，普通 clip 足够。
                        .clip(RoundedCornerShape(tokens.shape.extraLarge)),
                )
                Spacer(Modifier.height(tokens.spacing.cardCoverGap))
                Text(
                    text = comic.title,
                    fontSize = tokens.type.caption,
                    fontWeight = tokens.type.weightMedium,
                    color = tokens.color.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (comic.subTitle.isNotBlank()) {
                    Text(
                        text = comic.subTitle,
                        fontSize = tokens.type.caption,
                        color = tokens.color.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        // 末批不足一行时用等宽空位补齐，卡片不会被 weight 拉宽
        repeat(columns - comics.size) { Spacer(Modifier.weight(1f)) }
    }
}

/**
 * 轮播单张卡：宽高都由轮播的 keyline 给（焦点卡宽、侧卡窄），内容只做封面 + 遮罩圆角。
 * 封面参与「→ 详情页」共享元素飞行；打码命中一律不飞（飞行层会绕开页面裁剪）。
 */
@Composable
internal fun RecommendCarouselItem(
    comic: Comic,
    ux: com.venera.compose.components.UxPlatformServices,
    modifier: Modifier,
) {
    val tokens = VeneraTokens
    // 直接走守卫 LRU 判定链（源级预设 + 用户规则）；取单例那一步收进了平台服务。
    val maskState = ux.coverMaskStateOf(
        sourceKey = comic.sourceKey,
        title = comic.title,
        author = comic.subTitle,
        tags = comic.tags,
        comicId = comic.id,
        description = comic.description,
    )
    Box(
        modifier = modifier.background(
            tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha)
        ),
    ) {
        com.venera.compose.components.venera.VeneraCover(
            url = comic.cover,
            contentDescription = comic.title,
            shimmerWhileLoading = false,
            mask = if (maskState == "VISIBLE") com.venera.compose.components.venera.VeneraCoverMask.Visible
                   else com.venera.compose.components.venera.VeneraCoverMask.Masked,
            // 槽位宽高都由轮播给，封面不能再自持比例（否则固定高度里会横向留缝）
            preserveAspectRatio = false,
            modifier = Modifier
                .fillMaxSize()
                .coverSharedElement(
                    key = ComicSharedTransition.coverKey(comic.sourceKey, comic.id),
                    allowFly = maskState == "VISIBLE",
                ),
        )
    }
}

/**
 * 刷新中的占位：与轮播同高同圆角的灰块 + 呼吸脉冲。
 * 复用全站那套 [VeneraShimmer]（它本来就只做 alpha 呼吸，不引 shader），不新造动画。
 */
@Composable
internal fun RecommendSkeleton(columns: Int) {
    val tokens = VeneraTokens
    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4)) {
        repeat(columns) {
            VeneraShimmer(
                modifier = Modifier
                    .width(tokens.spacing.historyCardWidth)
                    .height(tokens.spacing.recommendHeroHeight),
                shape = RoundedCornerShape(tokens.shape.extraLarge),
            )
        }
    }
}

/**
 * 把这一条横滑区域登记成**系统手势排除区**。
 *
 * 全面屏手势下，屏幕左右边缘的横拖被系统判成「返回」，轮播贴到边缘就永远滑不动 ——
 * 真机症状是「想抽卡却退出了页面」。排除区只是给系统的提示（OEM 可忽略），
 * 且每条边最多 200dp 高，所以只挂在这一行，不是整页。
 */
@Composable
internal fun Modifier.systemGestureExclusionBand(): Modifier {
    // 2026-10-20：`LocalView.systemGestureExclusionRects` 是 Android 专有面，收进了
    // `LocalUxPlatformServices`（桌面实现是空操作）。量化到 16px 那条判据原样保留 ——
    // 它挡的是「列表滚动时每像素都改排除区 = 给渲染线程挂一条持续重算」。
    val ux = com.venera.compose.components.LocalUxPlatformServices.current
    var band by remember { mutableStateOf<FloatArray?>(null) }
    DisposableEffect(ux) {
        onDispose { ux.setGestureExclusionRects(emptyList()) }
    }
    LaunchedEffect(band) {
        val rect = band ?: return@LaunchedEffect
        ux.setGestureExclusionRects(listOf(rect))
    }
    return this.onGloballyPositioned { coords ->
        val position = coords.positionInRoot()
        val size = coords.size
        val top = (position.y / 16f).roundToInt() * 16
        val next = floatArrayOf(
            position.x.roundToInt().toFloat(),
            top.toFloat(),
            (position.x + size.width).roundToInt().toFloat(),
            (top + size.height).toFloat(),
        )
        if (band == null || !next.contentEquals(band)) band = next
    }
}

/** 源状态行：健康度圆点 + 延迟文案，点击重测。 */
@Composable
internal fun SourceStatusRowHome(
    name: String,
    latency: Long?,
    onRetest: () -> Unit,
) {
    val tokens = VeneraTokens
    val health = healthOf(latency)
    val healthColor = health.color()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRetest)
            .padding(vertical = tokens.spacing.space5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(tokens.spacing.sourceAvatarSize)
                .clip(CircleShape)
                .background(tokens.color.primary.copy(alpha = tokens.current.badgeAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1),
                fontSize = tokens.type.body,
                fontWeight = tokens.type.weightBold,
                color = tokens.color.primary,
            )
        }
        Spacer(Modifier.width(tokens.spacing.space6))
        Text(
            text = name,
            fontSize = tokens.type.body,
            fontWeight = tokens.type.weightMedium,
            color = tokens.color.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(tokens.spacing.space4))
        Box(
            modifier = Modifier
                .size(tokens.spacing.statusDotSize)
                .clip(CircleShape)
                .background(healthColor),
        )
        Spacer(Modifier.width(tokens.spacing.space3))
        Text(
            text = health.label(latency),
            fontSize = tokens.type.caption,
            color = healthColor,
        )
        Spacer(Modifier.width(tokens.spacing.space6))
    }
}

/** 统计条：标签 + 比例条 + 数值。 */
@Composable
internal fun StatBarRow(label: String, count: Int, maxCount: Int) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = tokens.type.caption,
            color = tokens.color.textPrimary,
            modifier = Modifier.width(tokens.spacing.barLabelWidth),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(tokens.spacing.barHeight)
                .clip(RoundedCornerShape(tokens.shape.extraSmall))
                .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.selectedSurfaceAlpha)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((count.toFloat() / maxCount).coerceIn(0.06f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(tokens.shape.extraSmall))
                    .background(tokens.color.primary),
            )
        }
        Spacer(Modifier.width(tokens.spacing.space5))
        Text(
            text = count.toString(),
            fontSize = tokens.type.overline,
            color = tokens.color.textSecondary,
            modifier = Modifier.width(tokens.spacing.barValueWidth),
        )
    }
}

internal enum class SourceHealth { Connected, Degraded, Failing, Unknown }

/** 由延迟值推导健康度：null=未测速，<0=失败，<400ms=健康，其余=偏高。 */
internal fun healthOf(latency: Long?): SourceHealth = when {
    latency == null -> SourceHealth.Unknown
    latency < 0 -> SourceHealth.Failing
    latency < 400 -> SourceHealth.Connected
    else -> SourceHealth.Degraded
}

@Composable
internal fun SourceHealth.color(): Color = when (this) {
    SourceHealth.Connected -> StatusColors.Healthy
    SourceHealth.Degraded -> StatusColors.Degraded
    SourceHealth.Failing -> StatusColors.Failing
    SourceHealth.Unknown -> StatusColors.Unknown
}

@Composable
internal fun SourceHealth.label(latency: Long?): String = when (this) {
    SourceHealth.Connected, SourceHealth.Degraded -> "$latency ms"
    SourceHealth.Failing -> "连接失败"
    SourceHealth.Unknown -> "未测速"
}

/**
 * 「可能你感兴趣」的内容分级。两档各自绑定一组源（见 [HomeViewModel] 里的源集常量）：
 *
 * - [NORMAL] 全年龄向：MangaDex / 爱看漫 / 拷贝漫画
 * - [R18] 成人向：禁漫天堂 / nhentai
 *
 * 存的是 [key]（大小写不敏感读回），所以改枚举名不会让老用户的档位掉回默认。
 */
enum class HomeRecommendMode(val key: String, val label: String) {
    NORMAL("NORMAL", "正常"),
    R18("R18", "R18");

    companion object {
        fun fromKey(key: String?): HomeRecommendMode =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: NORMAL
    }
}

/**
 * 首页段标（对齐设计稿 track 规范）：
 * 彩色垂直小条 + 中文标题 + 极小大写英文 + 弹性占位 + 右侧扩展区域。
 */
@Composable
internal fun HomeTrackHeader(
    title: String,
    enTag: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val tokens = VeneraTokens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .height(13.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )
        Spacer(Modifier.width(tokens.spacing.space3))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = tokens.type.weightBold,
            color = tokens.color.textPrimary,
        )
        if (enTag.isNotBlank()) {
            Spacer(Modifier.width(tokens.spacing.space2))
            Text(
                text = enTag,
                fontSize = 8.5.sp,
                fontWeight = tokens.type.weightBold,
                letterSpacing = 0.16.sp,
                color = tokens.color.textTertiary.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 本地漫画的三本书脊图标件（纯绘制，零静态资源依赖）。 */
@Composable
internal fun HomeBookSpines(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(34.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .width(7.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFE58FA6))
        )
        Box(
            modifier = Modifier
                .width(7.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF9F8CD6))
        )
        Box(
            modifier = Modifier
                .width(7.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF78B7AE))
        )
    }
}
