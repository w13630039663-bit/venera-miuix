package com.venera.compose.components.venera

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.venera.compose.components.CHROME_BAND_TOP_BAR
import com.venera.compose.components.ChromeCaptureSuppress
import com.venera.compose.components.drawVeneraAmbient
import com.venera.compose.components.reportChromeBand
import com.venera.compose.feature.LocalVeneraDarkTheme
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.basic.TopAppBarState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 顶栏这一侧的采样层，供 [VeneraTopBarPill] 直接取用。
 *
 * 为什么要走 CompositionLocal 而不是逐屏传参：`navigationIcon` / `actions` / `bottomContent`
 * 都是 [VeneraTopAppBar] **内部求值**的 composable lambda，而它自己已经收了 `backdrop` 参数——
 * 在这里 provide 一次，13 个调用点一行都不用改。
 * 用 `compositionLocalOf` 不用 static：这个值会随能力检测与 `enableBlur` 在 null↔非 null 之间切，
 * static 会让切换不重组（正是 `VeneraTokens.kt` 那条"切档留旧值"的病的形状）。
 */
val LocalTopBarBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Venera 统一顶栏（对齐 pixez-miuix 规范）：
 * - 顶置时大标题靠左底衬全通透；
 * - 下滑时大标题自动折叠居中，并淡入 HyperOS 渐变式高斯模糊（从状态栏向内容区平滑衰减，无硬切边缘）；
 * - 优雅降级：不支持 RuntimeShader 时平滑降级为 surface 半透明。
 *
 * @param title 折叠后居中的小标题。
 * @param largeTitle 顶置时靠左的大标题；默认与 [title] 相同。
 * @param subtitle 大标题下方副标题（可选）。
 * @param scrollBehavior 滚动行为控制器，需挂载到列表的 nestedScroll 上。
 * @param backdrop 采样源，由 [rememberTopBarBackdrop] 提供并挂载到列表上。
 * @param navigationIcon 左侧导航图标。
 * @param actions 右侧操作图标集合。
 * @param bottomContent 顶栏下方常驻内容（如分类 Tab 等）。
 * @param onDoubleTap 双击顶栏空白处（2026-09-30 新增，加性可选）。
 *   画廊用它"双击回到顶部"。**默认 null ⇒ 其余 12 个调用点零改动、零行为差异**。
 *
 *   ⚠️ 手势与顶栏内的动作钮是**并存**的：`detectTapGestures` 挂在顶栏这层 Box 上，
 *   而 `actions` 里那些 `VeneraTopBarPill` 各有自己的 clickable，子组件先消费点击事件，
 *   所以"点胶囊仍然生效、双击空白处才回顶"两条预期同时成立。
 *   这一条**尚未真机验证**（本轮设备未连），真机上要先确认这两件事都成立。
 * @param scrollProgressOverride 玻璃进度覆盖源（2026-10-02，设置 hero 页专用，加性可选）。
 *   hero 页调用方**不挂** scrollBehavior 的 nestedScroll 连接（原因见
 *   [SettingsHome]），折叠机制整个不参与，玻璃/分割线/小标题的进度由调用方从
 *   列表 scrollState 直接推导传入。null（默认）= 其余页面走 contentOffset 原路。
 */
@Composable
fun VeneraTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: String = title,
    subtitle: String = "",
    scrollBehavior: ScrollBehavior? = null,
    backdrop: Backdrop? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    onDoubleTap: (() -> Unit)? = null,
    scrollProgressOverride: State<Float>? = null,
) {
    val tokens = VeneraTokens
    val density = LocalDensity.current
    val surfaceColor = MiuixTheme.colorScheme.surface

    // 动态淡入进度：仅根据 contentOffset（下滑 48dp 线性淡入），对齐 pixez-miuix 规范。
    // 不使用 collapsedFraction——它在大标题折叠动画中会快速跳至 1.0，导致模糊背板过早 100% 不透明（白色覆盖）。
    val scrollProgress = remember(scrollBehavior) {
        derivedStateOf {
            val state = scrollBehavior?.state ?: return@derivedStateOf 0f
            val thresholdPx = with(density) { 48.dp.toPx() }
            if (thresholdPx > 0f) (-state.contentOffset / thresholdPx).coerceIn(0f, 1f) else 0f
        }
    }
    val activeProgress = scrollProgressOverride ?: scrollProgress

    // 折叠态那一行的**顶部 inset**（hero 路径下有两处要用：自绘小标题的居中基准、以及把整条顶栏
    // 钉成"恒折叠态"的高度）。算一次、两处共用，不然两处各算一遍迟早会漂。
    //
    // ⚠️ 2026-10-16 三修（真机「居中标题栏还是没有居中」）：这里**必须**读 miuix 自己用的那一份 ——
    // miuix 的 `TopAppBarLayout` 挂的是 `WindowInsets.systemBars.only(WindowInsetsSides.Top)`
    // （`TopAppBar.kt:806`，挂在这颗 Layout 自己身上），而不是 `WindowInsets.statusBars`。
    // 两者在多数机型数值恰好相等（本机实测也相等：胶囊行 188px = inset 49.2dp + 26dp），
    // 所以从前读 `statusBars`"看起来对"其实只是碰巧；一旦不等，自绘小标题就会与 miuix 的
    // 按钮行整整错开那一份差值。跟着 miuix 读同一份，才与设备无关。
    val collapsedTopInset = WindowInsets.systemBars.only(WindowInsetsSides.Top)
        .asPaddingValues().calculateTopPadding()
    // ── 覆盖进度路径：整条顶栏必须是**恒折叠态**（2026-10-16，真机「上滑标题不居中」）──
    //
    // 这一路的折叠机制整个退场（调用方不挂 nestedScroll，见 [scrollProgressOverride]），于是
    // miuix 的 `barHeight` 会**一直停在展开态** —— miuix `TopAppBar.kt:862-879`：
    //   `barHeight = lerp(collapsedHeight, collapsedHeight + expansion, 1f - collapseFraction)`，
    //   而 `collapseFraction` 由 `scrolledOffset` 推 ⇒ 连接不挂 ⇒ 恒 0 ⇒ 恒 = collapsedHeight + expansion。
    // `expansion` 那一份就是大标题那一行（`largeTitle` 的 Box 自带 `padding(top = CollapsedHeight)`，
    // 见 `TopAppBar.kt:747`），所以顶栏比"折叠到位"的样子**高出一整行**。
    //
    // 而标题那一侧呢：miuix 原生小标题的 alpha 挂 `collapsedFraction`（`TopAppBar.kt:672-682`
    // 的 `smallTitleVisible = collapsedFraction * 3f >= 1f`）⇒ 同样恒 0 ⇒ 原生小标题**永不出现**，
    // 屏上那个「画廊」只能是本文件自绘的那一份。它按 `CollapsedHeight` 那条带居中 ⇒ 落在这条
    // "多出一整行"的背板的上三分之一处。两件事叠起来读就是「标题贴在顶上、下面留一截空白」。
    //
    // 所以这一路把高度**钉死**成 顶部 inset + 折叠高度：背板、分割线、标题、actions 全部落回
    // 折叠态该有的位置。只在 `largeTitle` 为空时钉 —— 万一将来有人拿覆盖进度配一个非空大标题，
    // 宁可退回现状，也不能把它静默裁掉。
    //
    // ⚠️⚠️ 2026-10-16 三修 —— 钉法**只许裁、不许挤压**，这一条是把前两轮白改的根因（真机
    // 原话：「居中标题栏还是没有居中」）：
    //
    // 这一支的 `layout{}` 里**不能**把 miuix 的 `maxHeight` 压到它自然高以下。压了之后
    // 它整棵子树会被**居中上顶** `(自然高 − 允许高) / 2`，与真机逐像素读数完全对上：
    //   · miuix 本体自然高 = inset(49.2dp) + `layoutHeight`(≈94.4dp) ≈ 143.6dp
    //     （`layoutHeight = barHeight + LargeTitleBottomPadding`，`barHeight = 52dp + expansion`，
    //      而 `expansion` 正是那个**空大标题**占的一整行 ≈ 38dp —— 见 `TopAppBar.kt:864-890`）
    //   · 这里钉的允许高 = 49.2 + 52 ≈ 101.2dp ⇒ 上顶 (143.6 − 101.2) / 2 ≈ 21.2dp ≈ 53px
    // 真机读数（同一台机、同一份包，逐张截图量右上两枚胶囊里图标笔画的中心 y）：
    //   · 普通页（没有这颗盒子）8 张截图**全部** 188px —— 即 miuix 的正位 = inset + 26dp
    //   · 头图页有这颗盒子时：旧包 134px、新包 132px —— 两次几乎一样，因为第二版我只换了
    //     **摆放方式**（`place(0, 0)`）而没动这条 `maxHeight` ⇒ 挤压照旧 ⇒ 白改一轮
    //   · 188 − 134 = 54px ≈ 21.2dp × 2.5 ✓ 闭环
    // 而自绘小标题一直按 `inset + 26dp` 落（它在普通页那条基准上，实测也正是 188px）——
    // 所以"标题不居中"其实是**按钮被顶上去**，不是标题掉下来。
    //
    // 修法：测量时把高度**完全放开**（`Constraints.Infinity`），让它按自然高报出来；多出来的
    // 那一整行空大标题由外层 `clipToBounds` 裁掉 —— "钉成折叠态高度"照样成立，而 miuix 内部
    // 那套 `insets padding + verticalCenter` 一点不动，`actionIcons` 便落回 `inset + 26dp`，
    // 与自绘小标题**同一条线**。
    val pinCollapsedHeight = scrollProgressOverride != null && largeTitle.isEmpty()
    // 钉高度那一路用的像素高度 = `顶部 inset + 折叠高度`。组合期取好：`layout{}` 是测量期，
    // 那里读不到 `collapsedTopInset` 这个组合期局部量。
    val pinnedBarHeightPx = with(density) {
        (collapsedTopInset + TopAppBarDefaults.CollapsedHeight).roundToPx()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // ── 起点截帧时让位（2026-10-04，与壳层底栏同一枚开关，成因写在那一处）──
            // 顶栏是玻璃覆盖层，滚到上面那几行的卡会被它压住；PixelCopy 读的是已合成表面，
            // 于是那一条玻璃会被截进飞行体。只走 alpha：顶栏的折叠状态与滚动锚点都不参与。
            .graphicsLayer {
                alpha = if (ChromeCaptureSuppress.suppressed) 0f else 1f
            }
            // 它自己占的那一块由它自己报 —— 这里不存"顶栏有多高"的第二份真相。
            .reportChromeBand(CHROME_BAND_TOP_BAR)
            // 双击回顶部（只在调用方给了 onDoubleTap 时挂）。
            //
            // ⚠️ 用 `pointerInput` 而不是 `combinedClickable`：后者会给整条顶栏加上按压涟漪与
            // 可点击语义（顶栏不是按钮），也会把点击从子组件手里抢过来。`detectTapGestures`
            // 只认双击那一下、单点不消费，所以 `actions` 里的胶囊照常先拿到自己的点击。
            .then(
                if (onDoubleTap != null) {
                    Modifier.pointerInput(onDoubleTap) {
                        detectTapGestures(onDoubleTap = { onDoubleTap() })
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        // ── 真实渐变高斯模糊背板（对齐 pixez-miuix 规范）──
        if (backdrop != null && isRuntimeShaderSupported()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = activeProgress.value
                    }
                    .progressiveTextureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        // 从状态栏开始 100% 强度模糊，底部平滑衰减至 0，curve = 2.2f 杜绝硬切边缘
                        gradient = ProgressiveBlur.Top.copy(curve = 2.2f),
                        blurRadius = 10f,
                        colors = BlurDefaults.blurColors(
                            blendColors = listOf(
                                // 低强度补底：只负责提升文字对比，不再把玻璃推向白色。
                                // 泛白的主因曾是采样层缺氛围光（见 rememberTopBarBackdrop），
                                // 修掉后这里只需要很轻的一层。
                                BlendColorEntry(color = surfaceColor.copy(alpha = GlassTintAlpha)),
                            ),
                        ),
                    ),
            )
        } else {
            // 降级兜底：不支持 RuntimeShader 时显示半透明底色
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = activeProgress.value
                    }
                    .background(surfaceColor.copy(alpha = 0.85f)),
            )
        }

        // ── 底部分割线：alpha = 0.08 * fraction（折叠越深越明显，顶置时完全透明不可见）──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .graphicsLayer { alpha = 0.08f * activeProgress.value }
                .background(tokens.color.textPrimary)
                .align(Alignment.BottomStart),
        )

        // ── 折叠小标题（scrollProgressOverride 路径专用）──
        //
        // miuix 自带的小标题挂在 collapsedFraction 上，而覆盖进度路径下折叠机制整个
        // 不参与（collapsedFraction 恒 0，见 `TopAppBar.kt:672-682`），它永远不会出现 ——
        // 所以这里自绘一份，alpha 与玻璃背板共用同一条 activeProgress：玻璃起、标题现，玻璃没、标题隐。
        //
        // ⚠️ 2026-10-16 三修（真机「居中标题栏还是没有居中」）：居中基准改用**与 miuix 同一个
        // inset 修饰符**，不再自己读一个 Dp 再算。
        //
        // miuix 的标题行与按钮行都在 `windowInsetsPadding(WindowInsets.systemBars.only(Top))`
        // **之内**：`TopAppBar.kt:806` 那个 padding 挂在这颗 Layout 自己身上，`:881` 的
        // `verticalCenter = collapsedHeight / 2` 从 inset 之后起算，`:906`(title) 与 `:920`
        // (actionIcons) 同式落位 ⇒ 那一行的绝对中心 = `inset + CollapsedHeight / 2`。
        // 这里挂同一个 padding、再 `height(CollapsedHeight)`：盒子内容区 = `[inset, inset + 52dp]`，
        // 居中 ⇒ 中心同为 `inset + 26dp` —— **同一个修饰符消费同一份 inset，逐像素同线**。
        //
        // 从前写的是 `.height(statusBarTop + CollapsedHeight).padding(top = statusBarTop)`
        // （自己读 `WindowInsets.statusBars`）。它在多数机型数值恰好等于 miuix 那一份，所以
        // "看起来是对的"；但两者本来就不是同一个来源，一旦不等就会整整错一份差值（而且尺子
        // 也量不出来 —— 本机两者相等，实测自绘标题与 miuix 按钮行同在 188px）。把"同线"
        // 建立在巧合上不行，一并收口到 miuix 那一个表达式。
        if (scrollProgressOverride != null && title.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                    .height(TopAppBarDefaults.CollapsedHeight)
                    .graphicsLayer { alpha = activeProgress.value },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    // ⚠️ 2026-10-16 改口径（真机「滑后『画廊』…字体明显小很多」）：
                    // 原来写的是 `tokens.type.itemTitle`（MD3 16 / MIUIX 17sp），而 miuix 原生小标题
                    // 用的是 `MiuixTheme.textStyles.title3.fontSize`（`TopAppBar.kt:732`）+ Medium。
                    // 真机实测：探索页（走原生那一份）字高 38px、画廊这枚只有 30px，差 20%。
                    // 这里直接读**同一个主题槽位**，不再自己挑一格 —— 于是
                    // ① 与原生逐像素同号；② 我们的 `miuixTextStylesOf` 把 title3 映射到
                    // `statNumber`(20sp)，仍然是表里的现成档，没有库外数字；③ 换风格档一起走。
                    fontSize = MiuixTheme.textStyles.title3.fontSize,
                    fontWeight = tokens.type.weightMedium,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        // ── 顶栏本体：miuix TopAppBar，底色透明，由背板负责视觉背景 ──
        // 顶栏里的图标按钮（VeneraTopBarPill）要磨砂就得拿到**同一份**采样层，
        // 否则它会采到别层内容、颜色与背后那条对不上（收藏页那颗当初就是这么对齐的）。
        CompositionLocalProvider(LocalTopBarBackdrop provides backdrop) {
            // ⚠️ `pinCollapsedHeight` 那一路的高度钉死（成因见函数上半部分的长注与"三修"那一段）：
            //
            // `clipToBounds` 是**必须**的 —— miuix 的 `TopAppBarLayout` 内部是
            // `layout(constraints.maxWidth, layoutHeight)`（`TopAppBar.kt:892`），它**不看
            // `maxHeight`**，报出来的高度会超出这里给的约束；不裁的话那一整行大标题的空白区
            // 仍旧画出去，钉高度就白钉了（而且背板 `matchParentSize` 也会跟着回到旧高度）。
            // 这一路下大标题恒为空串（见 `pinCollapsedHeight` 的判据），所以裁不掉任何内容。
            if (pinCollapsedHeight) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // ⚠️⚠️ 2026-10-16 三修（真机「居中标题栏还是没有居中」）——**这一行就是
                        // 前两轮的根因**：测量 miuix 本体时**不许**把高度压到它自然高以下。
                        //
                        // 压了之后它整棵子树会被**居中上顶** `(自然高 − 允许高) / 2`：
                        //   · miuix 本体自然高 = inset(49.2dp) + `layoutHeight`(≈94.4dp) ≈ 143.6dp
                        //     （`layoutHeight = barHeight + LargeTitleBottomPadding`，
                        //      `barHeight = 52dp + expansion`，而 `expansion` 正是那个**空大标题**
                        //      占的一整行 ≈ 38dp —— `TopAppBar.kt:864-890`）
                        //   · 钉的允许高 = 49.2 + 52 ≈ 101.2dp ⇒ 上顶 (143.6 − 101.2)/2 ≈ 21.2dp ≈ 53px
                        // 真机读数（同机同包、逐张截图量右上两枚胶囊里图标笔画的中心 y）：
                        //   · 普通页（没有这颗盒子）**8 张截图全部 188px** = miuix 的正位
                        //   · 头图页有这颗盒子时：旧包 134px、新包 132px
                        //   · 188 − 134 = 54px ≈ 21.2dp × 2.5 ✓ 闭环
                        // 而自绘小标题一直落在 188px（与普通页同一基准）—— 所以"标题不居中"
                        // 其实是**按钮被顶上去**。第二版只把摆放从 SizeNode 换成 `place(0, 0)`、
                        // 没动这条 `maxHeight` ⇒ 挤压照旧 ⇒ 白改一轮（这正是它被白赔的原因）。
                        //
                        // 修法：把高度**完全放开**（`Constraints.Infinity`），让它按自然高报出来；
                        // 多出来的那一整行空大标题交给外层 `clipToBounds` 裁 —— "钉成折叠态高度"
                        // 照样成立，而 miuix 内部那套 `insets padding + verticalCenter` 一点不动，
                        // 于是它的 `actionIcons` 落回 `inset + 26dp`，与自绘小标题**同一条线**。
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(
                                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                            )
                            val width = if (constraints.maxWidth == Constraints.Infinity) {
                                placeable.width
                            } else {
                                constraints.maxWidth
                            }
                            layout(width, pinnedBarHeightPx) { placeable.place(0, 0) }
                        }
                        .clipToBounds(),
                ) {
                    VeneraTopAppBarBody(
                        title = title,
                        largeTitle = largeTitle,
                        subtitle = subtitle,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = navigationIcon,
                        actions = actions,
                        bottomContent = bottomContent,
                    )
                }
            } else {
                VeneraTopAppBarBody(
                    title = title,
                    largeTitle = largeTitle,
                    subtitle = subtitle,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    bottomContent = bottomContent,
                )
            }
        }
    }
}

/**
 * miuix [TopAppBar] 本体的那一份装配（底色透明，视觉背景交给背板）。
 *
 * 单独抽出来只为一件事：上面那条"钉高度"的分支要和原样分支**共用同一份参数表**，
 * 否则将来加一个参数只改一边，两路就悄悄分叉了（这正是本仓反复踩过的"两处各写一遍"形状）。
 */
@Composable
private fun VeneraTopAppBarBody(
    title: String,
    largeTitle: String,
    subtitle: String,
    scrollBehavior: ScrollBehavior?,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    bottomContent: @Composable () -> Unit,
) {
    val tokens = VeneraTokens
    TopAppBar(
        title = title,
        largeTitle = largeTitle,
        subtitle = subtitle,
        color = Color.Transparent,
        titleColor = tokens.color.textPrimary,
        largeTitleColor = tokens.color.textPrimary,
        subtitleColor = tokens.color.textSecondary,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        bottomContent = bottomContent,
    )
}

/**
 * 带有底色保护与平台着色器能力检测的 Backdrop 采样源。
 *
 * 1. 运行时熔断：若硬件/平台不支持 RuntimeShader (Android < API 33) 或主动禁用模糊，返回 null；
 * 2. 氛围光注入：采样层必须复刻 [com.venera.compose.components.drawVeneraAmbient] 的
 *    页面背景（surface 底色 + primary/secondary 取色光斑 + 浅色压暗）。
 *    毛玻璃模糊的是**采样层**而非屏幕像素——若采样层只铺纯 surface，玻璃会比
 *    实际页面背景偏白，与 MD3 动态取色脱节（「模糊玻璃变白」的用户报告根因）。
 */
@Composable
fun rememberTopBarBackdrop(enableBlur: Boolean = true): LayerBackdrop? {
    if (!enableBlur || !isRuntimeShaderSupported()) return null
    val isDark = LocalVeneraDarkTheme.current
    val primary = MiuixTheme.colorScheme.primary
    val secondary = MiuixTheme.colorScheme.tertiaryContainer.let {
        if (it != Color.Unspecified) it else Color(0xFF6750A4)
    }
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawVeneraAmbient(
            primary = primary,
            secondary = secondary,
            surface = surfaceColor,
            isDark = isDark,
        )
        drawContent()
    }
}

/**
 * 方便内容列表快捷挂载 Backdrop 采样源。
 */
fun Modifier.blurBackdropSource(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) this.layerBackdrop(backdrop) else this

/**
 * 创建与 VeneraTopAppBar 配套的滚动行为。
 */
@Composable
fun rememberVeneraTopAppBarBehavior(
    state: TopAppBarState = rememberTopAppBarState(),
    canScroll: () -> Boolean = { true },
): ScrollBehavior = MiuixScrollBehavior(state = state, canScroll = canScroll)
