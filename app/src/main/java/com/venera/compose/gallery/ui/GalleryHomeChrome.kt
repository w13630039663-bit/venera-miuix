package com.venera.compose.gallery.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.venera.compose.components.venera.PILL_TINT_BOTTOM
import com.venera.compose.components.venera.PILL_TINT_TOP
import com.venera.compose.components.venera.veneraFaceSheen
import com.venera.compose.components.venera.veneraGlassSurface
import com.venera.compose.components.venera.veneraSurfaceShadowElevation
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.ui.tokens.VeneraGlassRole
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
 * ⚠️ 第三条（阴影）2026-10-16 起**要经 `veneraSurfaceShadowElevation` 过一道**：玻璃档下它归零。
 * 原因是那一档卡底透明，平台投影会从卡底透出来（读数与判据写在 `VeneraGlass` 那个函数上）。
 * "三条逐字同源"这条契约不变 —— 两处仍然读**同一个表达式的同一条来源**，
 * 只是那条来源现在是"按档取"，于是两态继续逐像素同步（该有投影时两处一起有、该没有时一起没有）。
 *
 * ── 为什么高度是常量而不是逐帧量 ──
 *
 * 网格的顶部避让要拿这个高度去算，而"逐帧量顶栏高度"那条路在本仓已经失败过一次
 * （`FREEZE-STATEMENT.md` 第七轮：`heightOffsetLimit` 是普通 `var`、composition 期读不到真值，
 * 于是首帧算出的避让要等到用户滚一下才生效）。本条的**内容是定高件**（就一条 48dp 的条），
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
    /**
     * 搜索是不是开着（调用方传 `svm.active`）。开着时这一条**让位**给从它身上长开的那张卡 ——
     * 两边挂同一个 key 的共享元素，展开/收起读起来是"这一块面在长高 / 缩回"，
     * 口径与取舍见 `GallerySearchContainer.kt`。
     *
     * 刻意**不给默认值**：默认 `false` 会让下一个调用点忘传时静默退化成"永远显示"
     * （= 这一轮之前的样子，也就是两块东西各动各的），而那种退化在屏上看得见、
     * 在代码里看不见。同一个理由见 `GallerySearchArea.resultCount`。
     */
    searchOpen: Boolean,
    /**
     * 首页那一排选中的内容域，以及点其中一格。
     *
     * 2026-10-17 第三轮（用户另一张稿「看下站源切换能不能做成这样」）：那一排从前是自己占一行的
     * 一节（`section-domain`），现在并进这一行摆在搜索条左边 —— 左右各 11dp 的空是这一行自己给的，
     * 所以**这两枚参数必填、不给默认值**：给默认值的话下一个调用点会静默把切换器摘掉，
     * 而"整页只剩 Pixiv 那一池、切不回去"正是这一族最贵的形状（同日刚修的那条空态吞页同一族）。
     */
    domain: GalleryHomeDomain,
    onPickDomain: (GalleryHomeDomain) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenReverse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val view = LocalView.current
    // 恒占位：这一格是**墙的第一行**，高度参与墙的布局；而"让位给卡"是把内容撤下来做的 ——
    // 撤下来的那一行若不兜住，墙会硬跳一行（正是 2026-10-15 要修的那一条，只是从前靠
    // "常驻不撤"绕开，现在靠这块固定高度的外框兜住）。
    //
    // 高度取 `galleryHomeChromeHeight()` 而不是另写一个数：它就是这个组件的自然高度，
    // 那条"常量与实际摆件逐项对应"的尺子（`GalleryHomeChromeTest`）同时管着这里。
    Box(modifier = modifier.height(galleryHomeChromeHeight())) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            // ── 内容域那一排（2026-10-17 第三轮，用户稿「看下站源切换能不能做成这样」）──
            //
            // 从前它自己占一行（`section-domain`），右边整片空着；稿上要求它与搜索条**同一行**、
            // 坐在搜索条左边那一格。这一行的总高度仍是 `galleryHomeChromeHeight()` 那三项之和，
            // 一个字没改 —— 常量与摆件逐项对应那条硬约束（见文件头注与 `GalleryHomeChromeTest`）不受影响。
            //
            // 摆法：外框吃**与条同一条带**（top=space2 起、`segmentedHeight` 那一档 ——
            // 2026-10-17 用户点名「搜索框缩成和切换源一样大小」之后两边同高），
            // 里面按 Center 摆那颗分段器（它自己也是 48dp，正中坐下就是齐的）。不写"往下挪 4dp"这种数：
            // 那条带就是条自己占的那一段，两者共用同一组 token 就是唯一的对齐方式。
            Box(
                modifier = Modifier
                    .padding(top = tokens.spacing.space2, bottom = tokens.spacing.space3)
                    .height(tokens.spacing.segmentedHeight),
                contentAlignment = Alignment.Center,
            ) {
                GalleryHomeDomainChips(selected = domain, onPick = onPickDomain)
            }
            AnimatedVisibility(
                modifier = Modifier.weight(1f),
                visible = !searchOpen,
                // ⚠️ **位移恒 0**：只借时长、不要视觉动效。
                //
                // 这一层是"展开"那一下的**源**（outgoing），而共享元素按
                // `renderInOverlayDuringTransition = false` 就地渲染 ⇒ 父级的 alpha 会乘到飞行体上。
                // 它若按常规 `fadeOut`，飞行体会在 150ms 内被父级淡到 0，而 bounds 动画要跑 300ms
                // ⇒ 后半程什么都看不见，读起来又回到用户抱怨的"淡出淡入"。
                // 内容的交叉淡变交给 `sharedBounds` 自己的 enter/exit（默认 fadeIn/fadeOut）。
                // 完整推演见 `GallerySearchContainer.kt` 头注第 3 条。
                enter = slideInVertically(animationSpec = SEARCH_CONTAINER_SHOW_SLIDE_SPEC) { 0 },
                exit = slideOutVertically(animationSpec = SEARCH_CONTAINER_HIDE_SLIDE_SPEC) { 0 },
            ) {
                // ── 外框与"那一块面"必须分两层（2026-10-17 第四轮，用户第二次点「搜索栏大小还是没有对齐」）──
                //
                // 真凶不是那个高度数字，是**内缩挂在哪一层**：原先三个 `padding` 直接串在
                // `Surface` 的修饰符链上，而 miuix `Surface` 的底色铺的是**它自己的节点**
                // （`basic/Surface.kt:57-69`：`Box(modifier = modifier.…clip(shape).background(color))`
                // —— `padding` 在链上更外，于是「内缩 + 48dp」整块都被涂上了色）。
                //
                // 读数（用户截图 1080×572，密度 2.625）：切换器的药丸 391~519 = 129px ≈ **48dp**；
                // 搜索条底色 382~533 = 152px ≈ **58dp** = `space2 + 48 + space3` 那条带。
                // ⇒ 上一轮把 56 改成 48 确实生效了（卡片内缩仍是 4+6），只是被这 10dp 的内缩盖过去了。
                // 所以改法只有一个：**内缩留在外框，`Surface` 自己只拿 48dp**，
                // 左边那颗分段器的外框也是同一条带（`padding(top=space2, bottom=space3).height(48)`），
                // 两边从此逐像素同高。
                Box(
                    modifier = Modifier
                        // 共享元素挂在**外框**（不是 Surface）：卡那一端包在调用点的 Box 里，
                        // 两端外框同构（同样满屏宽、同样那三个内缩）⇒ 飞行时相对关系一致。
                        // 挂进内缩层会让两端一个是"面"、一个是"面 + 边距"，飞行途中横跳一下。
                        // ⚠️ 外框高度仍是 `space2 + 48 + space3`，与 `galleryHomeChromeHeight()` 逐项对应，
                        // 所以共享元素的 bounds 与改动前逐像素相同 —— 这一刀不动转场。
                        .gallerySearchContainerElement(this@AnimatedVisibility)
                        .fillMaxWidth()
                        // 左右内缩与搜索卡同档：展开时两边不会横跳。
                        .padding(
                            start = tokens.spacing.screenHorizontal,
                            end = tokens.spacing.screenHorizontal,
                            // ⚠️ 下面这两个内缩与 [galleryHomeChromeHeight] 里的加法必须逐项对应。
                            top = tokens.spacing.space2,
                            bottom = tokens.spacing.space3,
                        ),
                ) {
                    Surface(
                        shape = RoundedCornerShape(tokens.shape.extraLarge),
                        // ⚠️ 两档都置透明 —— 这是方案 B 那一刀能生效的**前提**，不是随手改的：
                        //
                        // 面的底色改由 Surface **内部**那枚 [veneraFaceSheen] 独家提供
                        // （见下面 Row 上那一处）。若这里还铺 `surfaceContainerHigh`，
                        // 它就压在渐变**之上**（miuix `Surface` 的 `background(color)` 在
                        // `basic/Surface.kt:146`，位于调用方 modifier **之内**，而外层先画）
                        // ⇒ 渐变被整个盖死，屏上仍是那张平涂的面。
                        //
                        // 为什么渐变不挂在这个**外框**上（挂这儿最省事）：
                        //  ① 同样会被上面的底色盖死；
                        //  ② 外框带 `padding`，它**没有圆角** —— 渐变顶端会画成方角，
                        //     而圆角只由 Surface 的 `clip(shape)` 提供。
                        // 落在内部则两件事都成立：clip 由 Surface 给、底色由渐变给。
                        color = Color.Transparent,
                        // 玻璃档归零：卡底透明时，miuix Surface 那层平台投影会从卡底透出来
                        // （用户 2026-10-16「浅色模式下还是有这种色块」的真凶，读数与判据见
                        //  `VeneraGlass.veneraSurfaceShadowElevation`）。
                        //
                        // 方案 B 把实色档从 `attached`(3dp) 抬到 `floating`(6dp)：
                        // 那一档本来就写着「悬浮元素」，而这一条正是整行里唯一的"浮"的那一头
                        // （站源分段器**没有**投影）。抬一档就是"一浮一沉"里"沉"的另一半。
                        // ⚠️ 与搜索卡那一端（仍是 `attached`）不再同档：那是"同一块面在长高"的契约，
                        // 而这里的投影读作"这块面浮在墙面上"。共享元素按 bounds 插值、不插值投影，
                        // 起飞那一帧的投影差 3dp 用户读不出来（真要读得出来，得改的是两端的**高度**）。
                        shadowElevation = veneraSurfaceShadowElevation(tokens.elevation.floating),
                        modifier = Modifier
                            // 玻璃层与 Surface 的底色**必须同一个节点**：它跟着 Surface 一起被
                            // 内缩框住，才不会又铺成一整条 58dp（玻璃档下这一层就是那块可见的底）。
                            .veneraGlassSurface(VeneraGlassRole.PANEL, tokens.shape.extraLarge)
                            .fillMaxWidth()
                            // 定高（常量口径的那一条，理由见类头注）。不用 heightIn(min=)：
                            // 那会让这一格随字体缩放长高，而避让公式吃的是常量 —— 两者会悄悄错开。
                            //
                            // 2026-10-17 用户点名「搜索框缩成和切换源一样大小」：这一格从
                            // `dockedSearchBarHeight`（56dp）换成左边那颗分段器的 `segmentedHeight`（48dp），
                            // 两件从此等高。展开成搜索卡那一端仍是 56dp —— 共享元素按 bounds 插值，
                            // 8dp 的差在起飞那一刻是"长高一点"，不是横跳。
                            .height(tokens.spacing.segmentedHeight)
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
                                // 方案 B 顺带修的一处居中：此前只有 `fillMaxWidth`，而 Surface 是定高 48dp
                                // —— Row 自己的高度是**内容高**，整条居中靠父级 Box 的默认对齐兜着。
                                // 现在 Row 自己就是 48dp，图标 / 占位字 / 圆钮三者的中线由这一层保证
                                // （用户 2026-10-17「标题和按钮也没居中」里属于这一条的那一半）。
                                //
                                // ⚠️ 吃得到定高是因为 miuix `Surface` 那个 Box 传了
                                // `propagateMinConstraints = true`（`basic/Surface.kt:67`），
                                // 而 `.height(48)` 挂在外层 —— 于是这里拿到的约束 min=max=48。
                                .fillMaxSize()
                                // ── 方案 B 的「浮」：这块面的**唯一**底色出处──
                                //
                                // 顶更白、底落到卡面色：从槽位插值，不写死 CSS 里那对
                                // `#FFFFFF/#FDF6F6`（动态取色与深色档下写死值会整片脱节）。
                                // 玻璃档下这条整体 no-op（[veneraFaceSheen] 开头就 return），
                                // 那一档的底色由 `veneraGlassSurface(PANEL, …)` 的真模糊负责。
                                .veneraFaceSheen(
                                    topColor = lerp(tokens.color.surfaceContainerHigh, tokens.color.surface, 0.55f),
                                    bottomColor = tokens.color.surfaceContainerHigh,
                                )
                                // 左侧内缩比右侧大（CSS `padding:0 12px 0 22px`）——
                                // 左边紧挨着 🔍 图标、右边紧挨着图搜圆钮，两侧呼吸该对称才对称。
                                // ⚠️ 这一刀只动**横向**：纵向那三个内缩在上游外框上，
                                // 与 [galleryHomeChromeHeight] 那条常量的耦合一字未动。
                                .padding(start = tokens.spacing.space9, end = tokens.spacing.space6),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                // 方案 B：占位与 🔍 一起降一档到 `textTertiary`。
                                // 原先占位用 `onSurfaceVariant`，浅色下实测 #534343 ——
                                // 比正文还艳，读起来像"里面已经有一行字了"，而不像一个待输入的框。
                                tint = tokens.color.textTertiary,
                                modifier = Modifier.size(tokens.spacing.chipIconSize),
                            )
                            Text(
                                // 占位文案沿用搜索卡那一句（**不另造**）：两处说的是同一个框能敲什么，
                                // 抄成两句迟早分叉，而用户会以为是两个不同的入口。
                                text = SEARCH_ENTRY_HINT,
                                fontSize = tokens.type.body,
                                color = tokens.color.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            // ── 方案 B：图搜收进一枚粉圆钮（整条唯一的彩色焦点）──
                            //
                            // 从前它是一枚裸灰图标（`textSecondary`），与左边那颗 🔍 同一档 ——
                            // 于是"点条本体展开搜索"与"以图搜图"两个动作在屏上**没有主次**。
                            // 收进粉圆钮之后：面是 `primaryContainer`、图标是 `onPrimaryContainer`
                            // （深色档下靠这套配对保证对比度，不靠手调 alpha），
                            // 而这一行里**只有它带彩色** ⇒ 一眼扫到"这里还能干另一件事"。
                            Box(
                                // 触达位仍是 40dp 那一档，不因为"变成一枚钮"就缩小 ——
                                // 误触代价是走错一层（走错 = 展开的是搜索而不是反搜）。
                                modifier = Modifier
                                    .size(tokens.spacing.iconButtonSize)
                                    .clip(CircleShape)
                                    // 同一套材质语言（渐变 + 顶部高光），插值比例与站源那枚粉块一致 ——
                                    // 两处都是"这一行的粉色"，屏上该读成同一件事。
                                    .veneraFaceSheen(
                                        topColor = lerp(
                                            tokens.color.primaryContainer,
                                            tokens.color.primary,
                                            PILL_TINT_TOP,
                                        ),
                                        bottomColor = lerp(
                                            tokens.color.primaryContainer,
                                            tokens.color.primary,
                                            PILL_TINT_BOTTOM,
                                        ),
                                        highlightAlpha = 0.65f,
                                    )
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
                                    tint = tokens.color.onPrimaryContainer,
                                    modifier = Modifier.size(tokens.spacing.chipIconSize),
                                )
                            }
                        }
                    }
                }
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
        tokens.spacing.segmentedHeight +
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
