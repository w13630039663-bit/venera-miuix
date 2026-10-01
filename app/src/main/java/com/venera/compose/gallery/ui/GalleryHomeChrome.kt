package com.venera.compose.gallery.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.components.isWideScreen
import com.venera.compose.components.venera.VeneraSegmentedButton
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

/**
 * 画廊首页的**常驻 chrome**：搜索入口条 + 来源分段器。
 *
 * ── 2026-09-30 第三版：它是**页面内容的第一行**，随内容滚走 ──
 *
 * 用户口径（原话）：「顶栏我需要下滑折叠，我只需要搜索栏保持在这里就行」。
 * 两版浮层方案被真机否掉之后，最终形态是：由 [GalleryScreen] 把它作为
 * 猜你喜欢墙的第一行（`GalleryForYouPage` 的 chrome 槽）传进来 ——
 * 首屏时紧贴顶栏下方（那个位置由网格避让地板保证），往下滚与内容一起离屏。
 * 本组件只管画，不管钉在哪。
 *
 * ── 为什么要有它（2026-09-30 用户拍板）──
 *
 * 此前这一格只在搜索开着时住着 `GallerySearchArea`，也就是说**不点顶栏那枚 🔍，屏上就看不出
 * 这一屏能搜**。用户拿新版效果图对照后的口径是「搜索 + 来源切换要常驻在首屏最显眼处」。
 * 于是同一格改成两态互斥：搜索关着 → 本条（由 [GalleryScreen] 在 `svm.active` 时挂进墙里）；
 * 搜索开着 → 那张搜索浮层卡（**卡本体一个字没改**）。
 * 两者永不同屏，所以顶栏下不会出现两套来源切换器。
 *
 * ── 为什么不照搬对照图的发光 / 霓虹 ──
 *
 * 它那套（紫蓝渐变、发光描边）与本仓的 MIUIX + M3 深色毛玻璃是两套语言。这里只借它的
 * **信息架构与位置**：色板取 `tokens.color.surfaceContainerHigh`、圆角取 `shape.extraLarge`、
 * 阴影取 `elevation.attached` —— 三条与搜索卡**逐字同源**，所以展开成那张卡时是同一块面在长高，
 * 不是换了一副皮。一个新数字都不立。
 *
 * ── 为什么高度是常量而不是逐帧量 ──
 *
 * 网格的顶部避让要拿这个高度去算，而"逐帧量顶栏高度"那条路在本仓已经失败过一次
 * （`FREEZE-STATEMENT.md` 第七轮：`heightOffsetLimit` 是普通 `var`、composition 期读不到真值，
 * 于是首帧算出的避让要等到用户滚一下才生效）。本条的**内容是定高件**（一条 56dp 的条 +
 * 一行分段器），高度在写的时候就已知，用常量既准确又首帧就对。
 *
 * 与之配套的硬约束：**这条 chrome 的实际高度必须等于 [galleryHomeChromeHeight]**。
 * 两处读的是同一批 token（见下面 [GalleryHomeChrome] 里的 padding 与间距），
 * 改任何一处间距都要同时改两处 —— 这是本节唯一允许的耦合，刻意不做成自动测量：
 * 自动测量会把"常量"这个前提悄悄换掉，而依赖它的避让公式看不出来。
 *
 * ── 那枚反搜入口为什么长在条尾 ──
 *
 * 它与搜索卡里那一枚是**同一个动作**（`rvm.openLayer()`），位置也一致（都在输入行的尾随格）。
 * 提出来常驻之后，"贴链接 / 挑本机图"这一步不再需要先展开搜索卡 —— 那两件都是搜索的前置动作。
 *
 * @param source 当前来源档。分段器的选中态由它反查，所以与搜索卡里那一排**必然同一个值**。
 * @param onOpenSearch 点条本体 = 展开搜索卡（调用方走 `svm.openSearch()`）。
 * @param onOpenReverse 点条尾 = 展开搜索卡**并**切进反搜态。顺序由调用方定，
 *   必须是"先开搜索、再开反搜"：`GallerySearchArea` 那条抢焦点的 effect 在 `rvm.open` 为真时
 *   刻意让位给反搜框，先开反搜会让标签框与反搜框抢一次焦点（观感是焦点闪一下）。
 * @param onPickSource 换来源档。语义与搜索卡里那排**同一个入口**（`svm.setSource`），
 *   也就是 `switchSource(reSearch = true)` 的既有语义。它在首页这一态下有两种分支，都要说清：
 *   - **手上没有留存条件时**（刚启动、或上轮条件已清空）：只把档切过去、模式落回输入端，
 *     **一笔请求都不发**，屏上除那颗药丸高亮之外没有任何变化 —— 这正是"配置下一次搜索"该有的样子。
 *   - **手上有上一轮留存的条件时**（搜过 → 关掉搜索，chips 与结果按既有拍板**不清**）：
 *     它会顺手按那排条件在新档重搜一次。这一笔在首页是**看不见的**（结果要等用户点开搜索才上屏），
 *     而结果集与胶囊的档位也因此换到了新站 —— 与"在搜索卡里换站"是同一套语义，只是触发点在卡外。
 *     ⚠️ 这是本轮新增的可达路径（此前换档只能发生在卡内）。留着不改的理由：要让它"只换档不开搜"
 *     就得往 `GallerySearchViewModel`（历史保护域）加一个 `reSearch = false` 的公开入口，
 *     而本轮的口径是零保护域改动；这条已记入交付说明，等真机看观感再定。
 */
@Composable
internal fun GalleryHomeChrome(
    source: GallerySearchSource,
    onOpenSearch: () -> Unit,
    onOpenReverse: () -> Unit,
    onPickSource: (GallerySearchSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val view = LocalView.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            // 左右内缩与搜索卡同档：展开时两边不会横跳。
            .padding(
                start = tokens.spacing.screenHorizontal,
                end = tokens.spacing.screenHorizontal,
                // ⚠️ 下面这四个间距与 [galleryHomeChromeHeight] 里的加法必须逐项对应。
                top = tokens.spacing.space2,
                bottom = tokens.spacing.space3,
            ),
    ) {
        Surface(
            shape = RoundedCornerShape(tokens.shape.extraLarge),
            color = tokens.color.surfaceContainerHigh,
            shadowElevation = tokens.elevation.attached,
            modifier = Modifier
                .fillMaxWidth()
                // 定高（常量口径的一半，理由见类头注）。不用 heightIn(min=)：
                // 那会让这一格随字体缩放长高，而避让公式吃的是常量 —— 两者会悄悄错开。
                .height(tokens.spacing.dockedSearchBarHeight)
                // 不给涟漪：与搜索卡同一条口径（那张卡整块也不给），
                // 按下反馈交给震动，免得"点这里"的暗示比搜索卡本身还强。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onOpenSearch()
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = tokens.color.onSurfaceVariant,
                    modifier = Modifier.size(tokens.spacing.chipIconSize),
                )
                Text(
                    // 占位文案沿用搜索卡那一句（**不另造**）：两处说的是同一个框能敲什么，
                    // 抄成两句迟早分叉，而用户会以为是两个不同的入口。
                    text = SEARCH_ENTRY_HINT,
                    fontSize = tokens.type.body,
                    color = tokens.color.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    // 触达位取图标按钮那一档（40dp），不是图标自身的 16dp ——
                    // 后者按不准，而这一枚还紧挨着"点整条展开搜索"的落点，误触代价是走错一层。
                    modifier = Modifier
                        .size(tokens.spacing.iconButtonSize)
                        .clip(RoundedCornerShape(tokens.shape.small))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onOpenReverse()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ImageSearch,
                        contentDescription = "以图搜图",
                        tint = tokens.color.textSecondary,
                        modifier = Modifier.size(tokens.spacing.chipIconSize),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(tokens.spacing.space3))

        // 来源切换器：**同一个组件、同一份选项表**（`GallerySearchSource.options`），
        // 与搜索卡里那一排逐字同源 —— 各写一份就会出现"首页点亮第一格、卡片里是单站档"那种错位。
        VeneraSegmentedButton(
            options = GallerySearchSource.options.map { it.label },
            selectedIndex = GallerySearchSource.options.indexOf(source).coerceAtLeast(0),
            onSelect = { index ->
                GallerySearchSource.options.getOrNull(index)?.let(onPickSource)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 这条 chrome 的高度：**「上滑收起顶/底栏」的收栏阈值**（[GalleryChromeHidePolicy] 用）。
 *
 * 2026-09-30 第三版起 chrome 是墙里的第一行内容，它的高度**不再参与网格避让**
 * （避让只剩顶栏地板）—— 这把常量的消费点只剩收栏阈值一处。
 *
 * 逐项对应 [GalleryHomeChrome] 里的 padding 与那两个 Spacer —— 改一处就要改两处，
 * 刻意不做自动测量（理由见 [GalleryHomeChrome] 头注）。
 *
 * 分段器那一档必须跟着组件自己的 `isWideScreen` 分支走：它渲染出来是 48 还是 56 由库侧决定，
 * 这里读同一个判据与同一对 token，才不会在平板上算少 8dp。
 */
@Composable
internal fun galleryHomeChromeHeight(): Dp {
    val tokens = VeneraTokens
    val wide = isWideScreen(LocalConfiguration.current.screenWidthDp.dp)
    val segmented = if (wide) tokens.spacing.segmentedHeightWide else tokens.spacing.segmentedHeight
    return tokens.spacing.space2 +
        tokens.spacing.dockedSearchBarHeight +
        tokens.spacing.space3 +
        segmented +
        tokens.spacing.space3
}

/**
 * 首页那条搜索入口上的占位文案。
 *
 * 提到文件级而不是就地写串：搜索卡里有一份一模一样的（`GallerySearchArea` 的输入端占位），
 * 两处是**同一句话的两种摆法**。留着常量是为了让"改文案时两处一起改"这件事有一个可搜的落点。
 *
 * 刻意**不写站名**：来源就在下面那排分段器上摆着，写进去就得跟着档改字
 * （「全部」档压根没有单一站名可写）。
 */
internal const val SEARCH_ENTRY_HINT = "标签 / 画师 / 作品"
