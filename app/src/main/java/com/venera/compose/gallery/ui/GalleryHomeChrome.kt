package com.venera.compose.gallery.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text

/**
 * 画廊首页的**常驻 chrome**：一条搜索入口条（来源切换只在展开的搜索卡里，见下面第二节）。
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
 *
 * ── 2026-10-15 改口径：这一行**常驻不撤**，来源切换搬进卡里 ──
 *
 * 两件事一起改的（用户原话「做个容器变化的动画，现在是没有过渡动画的」+「把首页默认显示的
 * 源站去掉，只在搜索框展开时显示」）：
 *
 *  1. 从前搜索开着时 [GalleryScreen] 把这一整行从墙的 header 槽里**摘掉**，于是内容瞬间上跳
 *     约一行的距离，而卡片是另一层浮起来长开的 —— 两处不同步，读起来就是"换了一块东西"而不是
 *     "同一块面在变高"。现在这一行恒传给墙：卡片（锚在同一条基准线 `topBarFloor` 上、
 *     `scaleIn` 从顶部起）从它身上原地盖过来，收起时又缩回它身上，**全程内容不动**。
 *     搜索开着时它被卡片与 scrim 压在下面；结果态（卡片收回顶栏那排胶囊）它又露出来，
 *     正好是"回到输入框"的一枚额外出口。
 *  2. 首页那一排 `VeneraSegmentedButton` 摘掉了 —— 卡里那一排本来就存在、吃同一份
 *     `GallerySearchSource.options`，两处并摆才是"同一屏两种来源切换器"的成因（见冻结声明
 *     2026-09-28 那一节）。摘掉之后换源只剩"点开搜索再换"这一条路，首页**不留**当前源的提示
 *     （用户原话「只在搜索框展开时显示」）。
 *
 * 顶栏下因此永远不会同时出现两套来源切换器：只有一套，长在卡里。
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
 * 于是首帧算出的避让要等到用户滚一下才生效）。本条的**内容是定高件**（就一条 56dp 的条），
 * 高度在写的时候就已知，用常量既准确又首帧就对。
 *
 * 与之配套的硬约束：**这条 chrome 的实际高度必须等于 [galleryHomeChromeHeight]**。
 * 两处读的是同一批 token（见下面 [GalleryHomeChrome] 里的 padding 与间距），
 * 改任何一处间距都要同时改两处 —— 这是本节唯一允许的耦合，刻意不做成自动测量：
 * 自动测量会把"常量"这个前提悄悄换掉，而依赖它的避让公式看不出来。
 * 这条耦合现在有尺子了（`GalleryHomeChromeTest`：常量只认那三项，组件里不许再出现第二种
 * 行间距件），从前只有这段注释。
 *
 * ── 那枚反搜入口为什么长在条尾 ──
 *
 * 它与搜索卡里那一枚是**同一个动作**（`rvm.openLayer()`），位置也一致（都在输入行的尾随格）。
 * 提出来常驻之后，"贴链接 / 挑本机图"这一步不再需要先展开搜索卡 —— 那两件都是搜索的前置动作。
 *
 * @param onOpenSearch 点条本体 = 展开搜索卡（调用方走 `svm.openSearch()`）。
 *   这一行常驻之后，"开着搜索"与"没开"两种状态下它都在屏上：开着时被卡片盖住，
 *   点到的永远是卡片那一侧（scrim 与卡都在它之上），不会出现"按条没反应"那一格。
 * @param onOpenReverse 点条尾 = 展开搜索卡**并**切进反搜态。顺序由调用方定，
 *   必须是"先开搜索、再开反搜"：`GallerySearchArea` 那条抢焦点的 effect 在 `rvm.open` 为真时
 *   刻意让位给反搜框，先开反搜会让标签框与反搜框抢一次焦点（观感是焦点闪一下）。
 *
 * 本组件**不再接来源档**：换源只在展开的搜索卡里那一排（`svm.setSource` 的唯一入口），
 * 从前首页这一排带过两条语义（无留存条件时只换档不发请求、有留存条件时顺手在新档重搜一次），
 * 摘掉之后这两条都只剩卡内那一个触发点 —— 卡外那条"看不见的重搜"随之消失，
 * 也就不必再往 `GallerySearchViewModel`（历史保护域）加 `reSearch = false` 那个入口了。
 */
@Composable
internal fun GalleryHomeChrome(
    onOpenSearch: () -> Unit,
    onOpenReverse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val view = LocalView.current
    Surface(
        shape = RoundedCornerShape(tokens.shape.extraLarge),
        color = tokens.color.surfaceContainerHigh,
        shadowElevation = tokens.elevation.attached,
        modifier = modifier
            .fillMaxWidth()
            // 左右内缩与搜索卡同档：展开时两边不会横跳。
            .padding(
                start = tokens.spacing.screenHorizontal,
                end = tokens.spacing.screenHorizontal,
                // ⚠️ 下面这两个内缩与 [galleryHomeChromeHeight] 里的加法必须逐项对应。
                top = tokens.spacing.space2,
                bottom = tokens.spacing.space3,
            )
            // 定高（常量口径的那一条，理由见类头注）。不用 heightIn(min=)：
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
}

/**
 * 这条 chrome 的高度：**「上滑收起顶/底栏」的收栏阈值**（[GalleryChromeHidePolicy] 用）。
 *
 * 2026-09-30 第三版起 chrome 是墙里的第一行内容，它的高度**不再参与网格避让**
 * （避让只剩顶栏地板）—— 这把常量的消费点只剩收栏阈值一处。
 *
 * 逐项对应 [GalleryHomeChrome] 里那两个内缩与那一条定高条 —— 改一处就要改两处，
 * 刻意不做自动测量（理由见 [GalleryHomeChrome] 头注）。这条耦合从前只有注释、没有尺子，
 * 现在由 `GalleryHomeChromeTest` 钉着（只许这三项，补第二行必须同时补常量）。
 *
 * ⚠️ `@Composable` 摘不掉：`tokens.spacing` 本身是 composable 取值器（主题级注入），
 * 去掉注解会红在 Functions which invoke @Composable functions must be marked with the … 那一句。
 */
@Composable
internal fun galleryHomeChromeHeight(): Dp {
    val tokens = VeneraTokens
    return tokens.spacing.space2 +
        tokens.spacing.dockedSearchBarHeight +
        tokens.spacing.space3
}

/**
 * 首页那条搜索入口上的占位文案。
 *
 * 提到文件级而不是就地写串：搜索卡里有一份一模一样的（`GallerySearchArea` 的输入端占位），
 * 两处是**同一句话的两种摆法**。留着常量是为了让"改文案时两处一起改"这件事有一个可搜的落点。
 *
 * 刻意**不写站名**：这一行常驻不撤，写进去就得跟着档改字（「全部」档压根没有单一站名可写），
 * 而站名本来就在展开后的卡片里那一排分段器上摆着 —— 条上重复一遍只会多一处要同步的地方。
 */
internal const val SEARCH_ENTRY_HINT = "标签 / 画师 / 作品"
