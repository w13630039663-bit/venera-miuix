package com.venera.compose.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraChipVariant
import com.venera.compose.components.venera.VeneraSegmentedButton
import com.venera.compose.feature.LocalVeneraDarkTheme
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.domain.GalleryRecommendation
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.GalleryTagFilter
import com.venera.compose.ui.tokens.GalleryTagCategoryColors
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import java.util.Locale

/**
 * 画廊搜索的**内联展开区** —— 挂在 `VeneraTopAppBar.bottomContent` 里，就地撑在顶栏下面，
 * 并把下面的图片网格推下去。两个高度、同一张卡：
 *
 * | `svm.mode` | 形态 | 内容 |
 * |---|---|---|
 * | [GallerySearchMode.INPUT]   | 展开（列表最多 2 / 4 行，按键盘在不在，再多在列表内滚动） | 已选条件 + 搜索框 + 站点选择 + 补全 / 历史列表 |
 * | [GallerySearchMode.RESULTS] | 收成**一行胶囊**（最低 48dp） | 只有已选条件那一条横滑行（含框内 ×、点本体改排除） |
 *
 * ── 结果态为什么不摆输入框（2026-09-26 第八轮）──
 *
 * 此前两态都摆输入框，于是"收成一条"实际是 chips 行 + 输入行两条 ≈ 96dp 常驻在标题下面，
 * 用户下滑看图时那一段一直占着（真机截图反馈）。现在收成一条时输入行整行不画：
 * 屏高让出来一半，而"我在搜什么"仍然一眼可见（就是那行胶囊）。
 * 要接着加标签/改条件的出口有两个：点这一行任意位置、或顶栏那枚 🔍 —— 都回到展开态。
 *
 * 顺带解掉一个老毛病：能聚焦的输入框哪怕收着也在跟键盘打架
 * （键盘弹起 → 高度变 → 层切换 → 键盘再收），收成一条那一态现在压根没有键盘这件事。
 *
 * ── 为什么是一整张圆角悬浮卡，而不是"一块满宽底色矩形"（2026-09-26 第七轮改判）──
 *
 * 这块区此前吃过两个正好相反的亏：**有底**会在它与顶栏之间切出一条硬边（第五轮撤掉了底）；
 * **没底**之后滚动时它又直接压在锐利图片上 —— 据真机截图，描边款的站点 chip 在亮图上
 * 已经读不出来。两个都不是调色能解的，因为当时它是一块**满宽矩形**、顶边与顶栏齐平：
 * 于是有底就是硬切，没底就压图，没有第三个选项。
 *
 * 现在换成：`extraLarge` 圆角 + `surfaceContainerHigh` + 与搜索条同档阴影的一张卡，
 * 左右各内缩 [VeneraTokens.spacing.screenHorizontal]、上下留一小段缝，**不画任何满宽底色**。
 * 圆角 + 内缩 + 投影让"这是一块浮起来的控件"一眼可辨，那条硬边自然不存在；
 * 卡是不透明的，滚动时图片从它下面穿过，文字永远落在自家面上。
 * 展开与收起是同一张卡在长高 / 收窄，形态连续。
 *
 * ── 怎么收起 ──
 * 两个触发点，都不必让用户去找按钮：
 *  1. **键盘收起**（`WindowInsets.isImeVisible` 由可见转不可见）—— 手一收就收条，接着看图；
 *  2. **系统返回**（[GalleryScreen] 那层 BackHandler；键盘起着时返回键被输入法吃掉，落到它手上的是键盘没起的那一种）。
 * 收成一条之后**点卡上任意位置**即重新展开并弹键盘，所以不需要再放一枚只干这件事的箭头。
 *
 * ── 首页置顶时顶栏是**完全透明**的 ──
 * `VeneraTopAppBar` 的模糊背板 alpha 挂在滚动进度上（下滑 48dp 才淡入），且从状态栏向下
 * 衰减到底部为 0 —— 也就是说搜索区所在的那一段，任何滚动位置下都没有背板。这就是它必须自带容器的原因。
 *
 * 用户 2026-09-25 定的两条交互原样保留：**选一枚补全词 = 加一枚胶囊 = 立刻重查**（无提交钮）、
 * **胶囊进框**（框内 × 删枚、点胶囊本体改成排除、输入框空时退格删末枚）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GallerySearchArea(
    svm: GallerySearchViewModel,
    /** 「根据你的收藏」抽出来的标签集，按站一份。成因三种，见 [RecommendationRows]。 */
    recommendations: Map<GallerySite, GalleryRecommendation>,
    /** 点一枚推荐 = 把那一站的这串条件装好并开搜。 */
    onPickRecommendation: (GallerySite, List<String>) -> Unit,
    /** 区域真实高度回给页面：网格顶部避让按它算，展开 / 收起时网格才被平滑推下去。 */
    onSizeChanged: (IntSize) -> Unit,
    /** 「以图搜图」那一层。开着时**同一张卡原地形变**成反搜的输入形态（2026-09-28 改，见下）。 */
    rvm: GalleryReverseViewModel,
    /** 与那面墙同一把的分级判据，直接透传给 SauceNAO 的 `hide` 参数。 */
    allowNsfw: Boolean,
) {
    val tokens = VeneraTokens
    val focusRequester = remember { FocusRequester() }
    val reverseFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val expanded = svm.mode == GallerySearchMode.INPUT
    // 键盘在不在，直接决定列表能给多高 —— 也就决定下面的网格还剩多少地方（见 LIST_MAX_ROWS_*）。
    val imeVisible = WindowInsets.isImeVisible
    // 列表高度上限：**限高不截断** —— 行数超了就在列表内滚动，卡片不会因此继续长高。
    // 取 token 的整数倍而不是写死一个 192dp：行高改了这个上限跟着改。
    val listMaxHeight = tokens.spacing.listRowMinHeight *
        if (imeVisible) LIST_MAX_ROWS_TYPING else LIST_MAX_ROWS_IDLE
    val cardShape = RoundedCornerShape(tokens.shape.extraLarge)

    LaunchedEffect(Unit) { svm.restoreHistoryIfNeeded() }

    // 展开 = 一步到键盘（MD3 SearchView 的标准行为）。
    //
    // **等一小段再要焦点**：焦点修饰符要等节点 attach 完成才可用，同帧 `requestFocus()`
    // 会撞上 "FocusRequester is not initialized"；顺带也让键盘落在卡片长开之后。
    //
    // 收成一条时**把焦点一并放掉**：焦点若留在一条已经收起来的框上，再点卡不会触发
    // onFocusChanged，就展不开了 —— 那正是"点了没反应"。
    //
    // ⚠️ 反搜开着时**这条整个让位**：焦点该归反搜框（见下面 LaunchedEffect(rvm.open)）。
    // 两条 effect 同帧抢焦点的话，谁后跑谁赢 —— 焦点就会在两个框之间闪。
    LaunchedEffect(expanded, rvm.open) {
        if (rvm.open) return@LaunchedEffect
        if (expanded) {
            delay(FOCUS_REQUEST_DELAY_MS)
            runCatching { focusRequester.requestFocus() }
        } else {
            focusManager.clearFocus()
        }
    }

    // 键盘收起 → 收成一条。
    // 只在**确实见过键盘起来**之后才收（`imeWasVisible` 记的就是那次上升沿）：
    // 展开那一帧键盘还没弹起来，不设这道闸会当场把刚展开的区收掉。
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            imeWasVisible = true
        } else if (imeWasVisible) {
            imeWasVisible = false
            // 反搜开着时**不收**：那一具身体没有"收成一条"的形态（它本来就只有两行），
            // 而用户从系统相册挑图回来必然伴随一次键盘下落 —— 在那一刻把反搜条收掉是错的。
            if (!rvm.open) svm.collapseToResults()
        }
    }

    // 区被移出组合（关搜索）时把在途补全掐掉：补全协程挂在 ViewModel 上，
    // 不会随本区销毁自动取消，落地后会往一个已经关掉的区里写候选。
    DisposableEffect(Unit) { onDispose { svm.clearSuggestions() } }

    // ── 形变切换时的焦点交接 ──
    //
    // 两种形态的输入框是**同一个位置的两套绑定**，切过去时必须：
    // - 反搜 → 标签：焦点还给标签框（弹键盘，用户接着打标签）；
    // - 标签 → 反搜：焦点交给反搜框（弹键盘，直接能贴链接）。
    // 不做这一步的话，点搜图钮之后键盘会收起、要点一下框才能输入 ——
    // "形变"就变成了"换了个样子但没法打字"。
    //
    // 同样**等一小段**再要焦点：AnimatedVisibility 的内容 attach 完成前
    // requestFocus 会撞 "FocusRequester is not initialized"，与上面那条同因。
    LaunchedEffect(rvm.open) {
        if (!rvm.open) return@LaunchedEffect   // 切回标签态由上面 LaunchedEffect(expanded) 接手
        delay(FOCUS_REQUEST_DELAY_MS)
        runCatching { reverseFocusRequester.requestFocus() }
    }

    // ── 补全：防抖 250ms，且只在展开时跑 ──
    // 收成一条时输入框还在（就在卡里），不门控的话"看着结果"那一段也会偷发请求。
    var awaitingSuggest by remember { mutableStateOf(false) }
    LaunchedEffect(svm.term, svm.site, expanded) {
        if (!expanded) {
            awaitingSuggest = false
            return@LaunchedEffect
        }
        val term = svm.term.trim()
        if (term.isEmpty()) {
            awaitingSuggest = false
            svm.showSuggestions(emptyList())
            svm.suggestError = null
            return@LaunchedEffect
        }
        // 词一变就把旧候选清掉再进加载态：留着旧的那一列会先闪一屏**上一个前缀**的结果，
        // 而它在视觉上完全像"这次的候选"，用户会照着点下去。
        awaitingSuggest = true
        svm.showSuggestions(emptyList())
        svm.suggestError = null
        delay(SUGGEST_DEBOUNCE_MS)
        svm.loadSuggestions(term)
        awaitingSuggest = false
    }

    fun addTag(suggestion: GalleryTagSuggestion) {
        if (svm.addTag(suggestion)) svm.term = ""
    }

    /** 点胶囊本体＝把它在"包含 / 排除"之间翻一面（站方语法就是前面加 `-`）。 */
    fun toggleExclude(filter: GalleryTagFilter) {
        svm.notice = null
        svm.setFilters(
            svm.filters.map {
                if (it.name == filter.name) it.copy(excluded = !it.excluded) else it
            },
        )
    }

    /** 胶囊尾那枚 ×＝把这一枚撤掉（撤到零枚时 [GallerySearchViewModel.applyFilters] 会把整段退回输入态）。 */
    fun removeFilter(filter: GalleryTagFilter) {
        svm.notice = null
        svm.setFilters(svm.filters.filterNot { it.name == filter.name })
    }

    // 搜索触发**不在这里**：唯一触发点是 ViewModel 的 `applyFilters`。
    // 这里曾经还有一个 `LaunchedEffect(filters)`，它与命令式调用撞成双发，
    // 还把「点历史不直接开搜」那条拍板架空了（注释说不开搜、代码一定开搜）。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onSizeChanged(it) }
            .padding(
                start = tokens.spacing.screenHorizontal,
                end = tokens.spacing.screenHorizontal,
                top = tokens.spacing.space2,
                bottom = tokens.spacing.space3,
            ),
    ) {
        Surface(
            shape = cardShape,
            color = tokens.color.surfaceContainerHigh,
            shadowElevation = tokens.elevation.attached,
            modifier = Modifier
                .fillMaxWidth()
                // 整张卡都是"回到输入"的入口：收成一条时点它任何位置即展开并弹键盘。
                // 放在卡上而不是另加一枚箭头 —— 展开态下这条路径同样成立（已聚焦时
                // requestFocus 是空操作），一份代码两种形态都用得上。
                //
                // 不给涟漪：卡是一整块面，整块泛起涟漪会把"点这里"的暗示做强，
                // 而卡里的胶囊、历史行各有自己的按压反馈。卡内子项优先拿到点击，
                // 所以这层不会把胶囊的 × 和排除切换吃掉。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    // 展开时点卡＝把焦点要回来（已聚焦时 requestFocus 是空操作，两条路径共用一份代码）。
                    // 收成一条时输入框**不在组合里**，`focusRequester` 还没 attach，
                    // 直接 requestFocus 会撞 "FocusRequester is not initialized" ——
                    // 那一态该做的是"回到展开"。
                    if (expanded) focusRequester.requestFocus() else svm.backToInput()
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 展开态的最小高度取 MD3 docked search bar 的 56dp；
                    // 收成一条时卡里只剩一行胶囊，最低按 MD3 单行列表项的 48dp 收口
                    // （低于它，胶囊后头那枚 × 的触达位就不够按了）。
                    .heightIn(
                        min = if (expanded) {
                            tokens.spacing.dockedSearchBarHeight
                        } else {
                            tokens.spacing.listRowMinHeight
                        },
                    ),
                verticalArrangement = Arrangement.Center,
            ) {
                // ── 已选条件（标签态专属；反搜态没有标签胶囊，整行收起）──
                // 两态都摆，但形态不同：
                //  - 展开态用 FlowRow（放得下就让它换行，顺势占满整卡宽）；
                //  - 收成一条时用**单行横滑**，条件再多也不许把卡撑高 —— 那一态的全部意义
                //    就是"用最少的屏高把'我在搜什么'说出来"。
                //
                // 2026-09-28 形变改造：套 AnimatedVisibility 让它在切去反搜时**收拢**而不是消失 ——
                // 卡高跟着平滑变化，下面那块（反搜区）同时在长开，这是"形变"的下半支。
                AnimatedVisibility(
                    visible = !rvm.open && svm.filters.isNotEmpty(),
                    enter = expandVertically(animationSpec = tween(tokens.motion.medium)) +
                        fadeIn(animationSpec = tween(tokens.motion.medium)),
                    exit = shrinkVertically(animationSpec = tween(tokens.motion.medium)) +
                        fadeOut(animationSpec = tween(tokens.motion.medium)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                // 四条分别写：`padding(horizontal, vertical)` 那档装不下
                                // "上下不同"的组合（展开态下方由输入行收口，收成一条时得自己补）。
                                start = tokens.spacing.space8,
                                end = tokens.spacing.space8,
                                top = tokens.spacing.space2,
                                bottom = if (expanded) tokens.spacing.none else tokens.spacing.space2,
                            ),
                    ) {
                        if (expanded) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                            ) {
                                svm.filters.forEach { filter ->
                                    FilterChipItem(
                                        filter = filter,
                                        onToggle = { toggleExclude(filter) },
                                        onRemove = { removeFilter(filter) },
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                            ) {
                                svm.filters.forEach { filter ->
                                    FilterChipItem(
                                        filter = filter,
                                        onToggle = { toggleExclude(filter) },
                                        onRemove = { removeFilter(filter) },
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 输入行：**两种形态共用同一条槽，原地形变**（2026-09-28 改）──
                //
                // 这是整个形变的锚点。标签态与反搜态都有"图标 + 文本槽 + 尾随钮"这一行，
                // 结构相同、身份不同 —— 让它**永远在组**（只要 expanded），内部按 rvm.open
                // 换绑定与图标，Crossfade 只作用在会变的那几件小件上。
                // 视觉上就是"这一行自己变成了另一副样子"，而不是"一行消失、另一行出现"。
                //
                // 仍旧只在 expanded 时画（收成一条那一态没有键盘这件事，理由见上面那段历史注释）；
                // 反搜态没有"收成一条"的形态（它的身体本来就只有两行，见 imeWasVisible 那条注释），
                // 所以 `rvm.open` 时这一行同样在组 —— `expanded || rvm.open`。
                //
                // 为什么不摆两个 TextField 互相 Crossfade：那会在过渡中丢焦点/丢键盘，
                // 光标位置也 crossfade 不了。一个槽、按形态换内容绑定，焦点连续性由
                // openLayer/closeLayer 之后的 LaunchedEffect 管。
                if (expanded || rvm.open) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = tokens.spacing.space8,
                                vertical = tokens.spacing.space2,
                            ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                        ) {
                            // ── 引导图标：Search ↔ ImageSearch，原地小过渡 ──
                            Crossfade(
                                targetState = rvm.open,
                                animationSpec = tween(tokens.motion.medium),
                                label = "leadIcon",
                            ) { reverse ->
                                Icon(
                                    imageVector = if (reverse) Icons.Outlined.ImageSearch else Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = if (reverse) tokens.color.primary else tokens.color.onSurfaceVariant,
                                    modifier = Modifier.size(tokens.spacing.chipIconSize),
                                )
                            }

                            // ── 文本槽：同一个位置，按形态换内容绑定 ──
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (rvm.open) {
                                    // 反搜态：贴链接 / 本机挑图
                                    val picked = rvm.picked
                                    val fieldText = when {
                                        rvm.url.isNotEmpty() -> rvm.url
                                        picked != null -> picked.name
                                        else -> ""
                                    }
                                    if (fieldText.isEmpty()) {
                                        Text(
                                            text = "贴一个图片链接 · 或从本机挑一张",
                                            fontSize = tokens.type.body,
                                            color = tokens.color.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    BasicTextField(
                                        value = rvm.url,
                                        onValueChange = { rvm.onUrlChange(it) },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = tokens.type.body,
                                            color = tokens.color.textPrimary,
                                        ),
                                        cursorBrush = SolidColor(tokens.color.primary),
                                        keyboardOptions = KeyboardOptions(
                                            // 图片链接里大小写与转义都是内容，
                                            // 自动首字母大写会凭空造出抓不到的 URL。
                                            capitalization = KeyboardCapitalization.None,
                                            imeAction = ImeAction.Search,
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onSearch = { rvm.submit(allowNsfw) }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(reverseFocusRequester),
                                    )
                                } else {
                                    // 标签态（原样保留，含退格删胶囊）
                                    if (svm.term.isEmpty()) {
                                        Text(
                                            text = "在 ${svm.site.displayName} 搜标签",
                                            fontSize = tokens.type.body,
                                            color = tokens.color.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    BasicTextField(
                                        value = svm.term,
                                        onValueChange = {
                                            svm.ensureInputMode()
                                            svm.term = it
                                        },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = tokens.type.body,
                                            color = tokens.color.textPrimary,
                                        ),
                                        cursorBrush = SolidColor(tokens.color.primary),
                                        keyboardOptions = KeyboardOptions(
                                            // 图站标签是小写下划线串，自动首字母大写会凭空造出搜不到的词。
                                            capitalization = KeyboardCapitalization.None,
                                            imeAction = ImeAction.Search,
                                        ),
                                        keyboardActions = KeyboardActions(onSearch = {
                                            // 回车：首行候选**还没进框**就选中它（"回车选首行"那条路径保留）；
                                            // 否则把框里那串字当标签提交。
                                            //
                                            // 首行已经在框里时必须走后者 —— 直接 addTag 会撞上去重判断
                                            // 静默返回，那就是真机上"点历史装回条件后按键盘搜索键没反应"
                                            // 的成因（那时框里和首行都是刚装回来的那枚标签）。
                                            val first = svm.suggestions.firstOrNull()
                                            if (first != null && svm.filters.none { it.name == first.name }) {
                                                addTag(first)
                                            } else {
                                                svm.submitTerm()
                                            }
                                        }),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester)
                                            .onFocusChanged { if (it.isFocused) svm.ensureInputMode() }
                                            // 退格删最后一枚胶囊：只在框里没字时接管这次按键。
                                            // 这是"顺手"那一档，主入口仍是胶囊上的 × —— 软键盘的退格事件
                                            // 不是每个输入法都送进 Compose，不能把删除只押在它身上。
                                            .onPreviewKeyEvent { event ->
                                                if (event.key == Key.Backspace &&
                                                    event.type == KeyEventType.KeyDown &&
                                                    svm.term.isEmpty() &&
                                                    svm.filters.isNotEmpty()
                                                ) {
                                                    svm.removeLastFilter()
                                                    true
                                                } else {
                                                    false
                                                }
                                            },
                                    )
                                }
                            }

                            // ── 尾随钮：按形态给不同的"现在能退掉什么"，原地过渡 ──
                            Crossfade(
                                targetState = rvm.open,
                                animationSpec = tween(tokens.motion.medium),
                                label = "trailing",
                            ) { reverse ->
                                if (reverse) {
                                    ReverseTrailingActions(rvm)
                                } else if (svm.term.isNotEmpty() || svm.filters.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = if (svm.term.isNotEmpty()) "清空输入" else "删掉最后一枚标签",
                                        tint = tokens.color.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(tokens.spacing.chipIconSize)
                                            .clickable {
                                                if (svm.term.isNotEmpty()) {
                                                    svm.term = ""
                                                } else {
                                                    svm.removeLastFilter()
                                                }
                                            },
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 展开出来的那一半（只有输入端在）──
                // 高度由内容决定，外面的 `onSizeChanged` 每帧拿到真实高度，
                // 网格避让跟着它走 —— 所以这里用 AnimatedVisibility 让它**长开**而不是跳变，
                // 网格才是被"推下去"的。
                //
                // 2026-09-28 形变改造：反搜开着时这一整块收拢（站点选择 / 补全 / 历史都是
                // 标签态专属），与下面那块反搜区的长开**同时进行** —— 卡高一收一放，
                // 观感是"搜索框自己变成了另一副样子"。
                AnimatedVisibility(
                    visible = !rvm.open && expanded,
                    enter = expandVertically(
                        animationSpec = tween(durationMillis = tokens.motion.medium),
                    ) + fadeIn(animationSpec = tween(tokens.motion.medium)),
                    exit = shrinkVertically(
                        animationSpec = tween(durationMillis = tokens.motion.medium),
                    ) + fadeOut(animationSpec = tween(tokens.motion.medium)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = tokens.spacing.space8,
                                end = tokens.spacing.space8,
                                bottom = tokens.spacing.space6,
                            ),
                    ) {
                        // 站点切换 + 以图搜图入口，同一行。
                        //
                        // 站点那半：MD3 对 2~5 个互斥选项的标准件是 Segmented Button，
                        // 不是一排可横滑的 filter chip（两个站用横滑容器是空转的）。
                        //
                        // 反搜那半为什么**贴在这里**而不是另起一行：它和"选哪一站"是同一层
                        // 决定 —— 都在回答"这一轮按什么条件去取图"。另起一行就要多占 48dp，
                        // 而这一屏每一行都在抢卡片的高度（那面墙才是这一屏的主角）。
                        // 站点选择器因此从满宽让成 weight(1f)：两枚选项 + 一枚图标钮，
                        // 1080px 宽的机器上各段仍容得下 "Gelbooru" 全名。
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                        ) {
                            VeneraSegmentedButton(
                                options = GallerySite.entries.map { it.displayName },
                                selectedIndex = GallerySite.entries.indexOf(svm.site).coerceAtLeast(0),
                                onSelect = { index ->
                                    GallerySite.entries.getOrNull(index)?.let { svm.setSite(it) }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Outlined.ImageSearch,
                                contentDescription = "以图搜图",
                                tint = if (rvm.open) tokens.color.primary else tokens.color.textSecondary,
                                modifier = Modifier
                                    .size(tokens.spacing.chipIconSize)
                                    .clip(RoundedCornerShape(tokens.shape.small))
                                    .clickable { rvm.openLayer() },
                            )
                        }

                        svm.notice?.let {
                            Spacer(modifier = Modifier.height(tokens.spacing.space3))
                            SearchNoticeLine(it)
                        }

                        // 胶囊进框之后，"点胶囊改成排除"是个不直观的交互，得在这儿说一句。
                        // 只在真有胶囊时出现 —— 空框时这两个动作都还不存在，说了也只是噪音。
                        if (svm.filters.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(tokens.spacing.space3))
                            SearchNoticeLine(
                                "点标签改成排除（前面加 -），点标签上的 × 删掉" +
                                    (GallerySearch.budgetNotice(svm.site)?.let { "。$it" } ?: ""),
                            )
                        }

                        // 打字看补全；框里没字、而且屏上还**没有**一轮搜索结果时看历史。
                        //
                        // 第三档（没字 + 已经有结果或空态）**刻意不摆列表**：用户此刻看的是结果那一片，
                        // 而摊着的历史会把它挤到键盘底下 —— 真机实测那一档是
                        // 卡片 ~266dp + 键盘 ~340dp + 顶栏地板 ~128dp，873dp 的屏只剩 ~124dp，
                        // 连"这一串标签没有可摆的图"那句都读不全，只剩一个图标露头。
                        // 想接着加标签直接打字，补全照样出来。
                        val hasResultRound = svm.page > 0 || svm.isSearching
                        if (svm.term.isNotBlank() || !hasResultRound) {
                            Spacer(modifier = Modifier.height(tokens.spacing.space4))
                            if (svm.term.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = listMaxHeight),
                                ) {
                                    SuggestionList(
                                        suggestions = svm.suggestions,
                                        loading = awaitingSuggest || svm.isSuggesting,
                                        error = svm.suggestError,
                                        term = svm.term.trim(),
                                        site = svm.site,
                                        onPick = { addTag(it) },
                                    )
                                }
                            } else {
                                // 「根据你的收藏」排在历史**上面**：它是"我们替你算出来的"，
                                // 历史是"你自己搜过的"，前者要用户先知道有这条路。
                                RecommendationRows(
                                    recommendations = recommendations,
                                    onPick = onPickRecommendation,
                                )
                                if (svm.history.isNotEmpty()) {
                                    HistoryHeader(onClearAll = { svm.clearHistory() })
                                    Spacer(modifier = Modifier.height(tokens.spacing.space1))
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = listMaxHeight),
                                ) {
                                    HistoryList(
                                        history = svm.history,
                                        onPick = { svm.applyHistory(it) },
                                        onRemove = { svm.removeHistory(it) },
                                    )
                                }
                            }
                        }
                    }
                }

                // ═══ 反搜态专属区：隐私提示 + 动作 chips + 预览 ═══
                //
                // 与上面那块标签区是**同一时长的对偶动画**：这块长开时那块收拢，
                // 卡高是两个方向的动画叠加 —— 加上输入行的原地形变（图标/占位/尾随钮
                // 都在原地过渡），整体观感才是"搜索框自己变成了另一副样子"，
                // 而不是"A 淡出、B 淡入"。
                //
                // 内容本体在 [GalleryReverseSearchArea]（它自己的头部注释讲了三条取舍）；
                // 这里**刻意不再套 cardModifier 那层壳** —— 它已经住在这张卡里了。
                AnimatedVisibility(
                    visible = rvm.open,
                    enter = expandVertically(animationSpec = tween(tokens.motion.medium)) +
                        fadeIn(animationSpec = tween(tokens.motion.medium)),
                    exit = shrinkVertically(animationSpec = tween(tokens.motion.medium)) +
                        fadeOut(animationSpec = tween(tokens.motion.medium)),
                ) {
                    GalleryReverseSearchArea(rvm = rvm, allowNsfw = allowNsfw)
                }
            }
        }
    }
}

/**
 * 反搜输入行尾随的那一枚钮：按"现在有什么可退"给不同动作，三档互斥。
 *
 * 从反搜卡自己的那具身体里**原样搬过来**（挑中的图 → 撤图；链接 → 清空；
 * 都没有 → 粘贴）—— 两具身体必须给同一个位置的同一枚钮同样的语义，
 * 否则形变前后"这一格会干什么"就变了，那是形变做出来最隐蔽的一种坏。
 */
@Composable
private fun ReverseTrailingActions(rvm: GalleryReverseViewModel) {
    val tokens = VeneraTokens
    val clipboard = LocalClipboardManager.current
    val picked = rvm.picked
    when {
        picked != null -> Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "撤掉这张图",
            tint = tokens.color.onSurfaceVariant,
            modifier = Modifier
                .size(tokens.spacing.chipIconSize)
                .clickable { rvm.clearInput() },
        )
        rvm.url.isNotEmpty() -> Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "清空链接",
            tint = tokens.color.onSurfaceVariant,
            modifier = Modifier
                .size(tokens.spacing.chipIconSize)
                .clickable { rvm.clearInput() },
        )
        else -> Icon(
            imageVector = Icons.Outlined.ContentPaste,
            contentDescription = "粘贴链接",
            tint = tokens.color.primary,
            modifier = Modifier
                .size(tokens.spacing.chipIconSize)
                .clickable {
                    val text = clipboard.getText()?.text?.trim().orEmpty()
                    if (text.isEmpty()) {
                        rvm.notice = "剪贴板里是空的"
                    } else {
                        rvm.onUrlChange(text)
                    }
                },
        )
    }
}

/** 补全防抖。搜索**不在这里防抖** —— 触发点已收进 ViewModel（`applyFilters`），
 *  连点两枚标签由那一处的"页 1 打断在途那一笔"负责合并，不需要再叠一层延时。 */
private const val SUGGEST_DEBOUNCE_MS = 250L

/** 展开到「要输入焦点」之间的等待（理由见 [GallerySearchArea] 里那处 requestFocus）。 */
private const val FOCUS_REQUEST_DELAY_MS = 120L

/**
 * 列表在屏上最多铺几行（键盘收起时）。
 *
 * 4 行（=192dp）是个折中：再多卡片就把可见的图片挤没了，而这一形态相对全屏层
 * 最大的好处恰恰是"能一边打字一边看见结果在动"。
 */
private const val LIST_MAX_ROWS_IDLE = 4

/**
 * 键盘起着时的行数上限。
 *
 * 收到 2 行（而不是继续给 4 行）是**真机实测逼出来的**：4 行那一版卡片约 330dp，
 * 叠上键盘（约 340dp）与顶栏地板（约 128dp）之后，873dp 高的屏只剩约 70dp 给网格 ——
 * 内联形态唯一的好处当场落空，用户看到的是卡片下面一片空白。
 * 打了字本来就在看候选，2 行足够"挑一个"；键盘一收高度就还回来了。
 */
private const val LIST_MAX_ROWS_TYPING = 2

/**
 * 一枚已选条件胶囊。
 *
 * 用 **Filter** 变体而不是 Tag：MD3 给"已选的筛选条件"的就是 filter chip，
 * 它选中时填 `secondaryContainer`（已选但不抢眼），未选中只有一层 outline 描边。
 * Tag 变体走的是 `primaryContainer` —— 框里每加一枚就多一颗主色胶囊，两三枚之后
 * 整块搜索区一片主色，视觉过响（真机截图已实锤）。
 * 顺带把"包含 / 排除"的区分从"两种实底"变成"实底 vs 描边"，更好认。
 *
 * 文字直接用 `filter.token`（排除项就带着 `-`）：那是站方语法本身，复制去网页版能搜到同一个东西。
 *
 * 抽成一个组件而不是在两处各写一遍，是因为展开态（FlowRow 可换行）与收成一条（单行横滑）
 * 已经是两套容器了 —— 胶囊本体必须**一模一样**，否则两种形态下点同一枚胶囊的手感会分叉。
 */
@Composable
private fun FilterChipItem(
    filter: GalleryTagFilter,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    VeneraChip(
        text = filter.token,
        selected = !filter.excluded,
        variant = VeneraChipVariant.Filter,
        trailingIcon = Icons.Outlined.Close,
        onClick = onToggle,
        onRemoveClick = onRemove,
    )
}

/**
 * 吸附态：结果态下拉看图时，条件胶囊"接替大标题的位置"停在顶栏左上角。
 *
 * ── 为什么要单独画一层（2026-09-26 第九轮）──
 * 结果态那张卡本来就只占一行胶囊，但它有两个副作用：一是**常驻**在标题下面那一段
 * （用户下滑看图时那一段一直占着），二是它是一块圆角面板，视觉上永远"浮"在顶栏之外。
 * 用户的口径是：下拉时胶囊并入顶栏、回到顶部时整卡再长回来。
 *
 * ── 位置怎么定的（不是"差不多对齐"，是同一条基准线）──
 * Miuix 的 TopAppBar 把 navigationIcon / actions / 小标题**都钉在
 * `TopAppBarDefaults.CollapsedHeight / 2` 这一行的垂直中心**（大标题折叠时也是收到这一行），
 * 所以这里对齐的正是那条线 —— 纵向由 [VeneraSpacing.topBarCollapsedHeight] 定高 + 内容居中，
 * 横向起点与标题同为 [VeneraSpacing.topBarTitlePadding]。
 * 宽度封在 [VeneraSpacing.collapsedChipWidthFraction] 之内，给居中标题让位；超出部分横滑。
 *
 * ── 为什么这里不可编辑 ──
 * 顶栏是**摘要位**，不是编辑位：这里刻意不画 ×，点胶囊任意位置＝回到展开态去改。两条理由 ——
 * 顶栏里的 × 触达只有 16dp（far below 48dp 下限，按不准，还容易连带误触整枚胶囊），
 * 而"点胶囊本体＝改成排除"这层语义在卡片里已经成立，同一枚胶囊换个位置就换一套动作必然误触。
 * 要删一枚 / 改排除，点开搜索一步就到。
 */
@Composable
internal fun GallerySearchCollapsedChips(
    svm: GallerySearchViewModel,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    // 结果态不可能条件为空（清空到零枚时 ViewModel 会把整段退回输入态），
    // 但空列表画一个零宽 Box 没有任何意义，直接不画。
    if (svm.filters.isEmpty()) return
    Box(
        modifier = modifier
            .padding(start = tokens.spacing.topBarTitlePadding)
            // 定高 = 折叠态顶栏那一行的高度，内容在其中垂直居中 → 与顶栏既有元素同一条中心线。
            .height(tokens.spacing.topBarCollapsedHeight)
            .widthIn(
                max = LocalConfiguration.current.screenWidthDp.dp *
                    tokens.spacing.collapsedChipWidthFraction,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            svm.filters.forEach { filter ->
                VeneraChip(
                    // 排除项直接把 `-` 摆在文字上：那是站方语法本身，与卡片里那枚完全一致。
                    text = filter.token,
                    selected = !filter.excluded,
                    variant = VeneraChipVariant.Filter,
                    onClick = onExpand,
                )
            }
        }
    }
}

/**
 * 补全列表。
 *
 * 三件此前没有、而这正是"扫一眼挑一个"必需的事：
 *  - 行高走 MD3 单行列表项（48dp 最小）而不是 4dp 内边距挤出的 ~24dp；
 *  - 有**加载态**：打字后防抖期 + 在途期都摆波浪环，不再是一段"什么都没发生"的空窗；
 *  - 分类名**上色**（站方那套数字档的颜色语义），计数加千分位，首项高亮标出"回车选它"。
 */
@Composable
private fun SuggestionList(
    suggestions: List<GalleryTagSuggestion>,
    loading: Boolean,
    error: String?,
    term: String,
    site: GallerySite,
    onPick: (GalleryTagSuggestion) -> Unit,
) {
    val tokens = VeneraTokens
    if (suggestions.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = tokens.spacing.space4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (loading) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(tokens.spacing.loaderInline),
                    color = tokens.color.primary,
                    trackColor = tokens.color.surfaceVariant,
                )
                Spacer(modifier = Modifier.height(tokens.spacing.space4))
                Text(
                    text = "正在找以「$term」开头的标签",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                )
            } else {
                // 取不到时说清是"没有这个词"还是"请求失败"，不说笼统的"搜索失败"。
                Text(
                    text = error ?: "${site.displayName} 没有以「$term」开头的标签",
                    fontSize = tokens.type.body,
                    color = tokens.color.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(suggestions, key = { _, item -> item.name }) { index, suggestion ->
            SuggestionRow(
                suggestion = suggestion,
                // 首项高亮 = 标出"回车会选这一行"，此前那条回车路径没有任何视觉锚点，
                // 框里敲着 `loli`、列表第一行也是 `loli`，用户不知道按回车会发生什么。
                highlighted = index == 0,
                onPick = onPick,
            )
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: GalleryTagSuggestion,
    highlighted: Boolean,
    onPick: (GalleryTagSuggestion) -> Unit,
) {
    val tokens = VeneraTokens
    // 分类色随主题取（亮暗各一套，都是各自背景上过 WCAG AA 的档位）。
    val categoryColor = GalleryTagCategoryColors.of(suggestion.category, LocalVeneraDarkTheme.current)
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(tokens.shape.small)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = tokens.spacing.listRowMinHeight)
            .clip(shape)
            .background(if (highlighted) tokens.color.surfaceVariant else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = ripple()) { onPick(suggestion) }
            .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space2),
    ) {
        Text(
            text = suggestion.name,
            fontSize = tokens.type.body,
            color = tokens.color.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(tokens.spacing.space4))
        // 分类名上色：图站用户扫补全时，"这行是画师还是角色"靠颜色一眼分得开，
        // 比读中文档位名快得多。颜色取自站方那套数字档（见 GalleryTagCategoryColors）。
        Text(
            text = suggestion.categoryLabel,
            fontSize = tokens.type.caption,
            color = categoryColor ?: tokens.color.textTertiary,
            maxLines = 1,
        )
        if (suggestion.deprecated) {
            Text(
                text = " · 已废弃",
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
                maxLines = 1,
            )
        }
        // 站方给的是**该标签**的张数，不是本次查询命中数（两站都没有总数端点）。
        Text(
            text = " · ${formatTagCount(suggestion.count)}",
            fontSize = tokens.type.caption,
            color = tokens.color.textTertiary,
            maxLines = 1,
        )
    }
}

/**
 * 「最近搜索」那一行标题 + 「清空」。
 *
 * 标题**不放进限高的列表里**：放进去它会占掉 4 行里的 1 行，于是可见的历史只剩 3 条。
 * 单独一行也让它读起来像"这一栏在说什么"，而不是一条可以点的记录。
 */
/**
 * 「根据你的收藏」那一栏。
 *
 * ## 一枚条目 = **整串条件**，不是一枚标签一行
 *
 * 这几枚标签的价值全在"它们在同一张收藏里**一起出现过**"那条共现约束上
 * （判据见 `GalleryRecommendations.pickTags`）。拆成单枚标签摆，用户点下去就是
 * "搜一个高频词" —— 那正是这套算法要避开的 `1girl solo long_hair` 式废组合。
 * 所以行上写的那串，就是点下去发出去的搜索串：读起来什么样，点下去就什么样。
 *
 * ## 两种"没有推荐"分开说（不许静默交错）
 *
 * - 一站都没收藏 → 引导去收藏；
 * - 有收藏但标签全被屏蔽规则挡完（或标签本身是空的）→ 点名是哪一站、为什么。
 *   这一种**不能**退化成上面那句"快去收藏" —— 那是把我们的缺陷说成用户没干活。
 */
@Composable
private fun RecommendationRows(
    recommendations: Map<GallerySite, GalleryRecommendation>,
    onPick: (GallerySite, List<String>) -> Unit,
) {
    val tokens = VeneraTokens
    val picks = recommendations.entries.mapNotNull { entry ->
        (entry.value as? GalleryRecommendation.Tags)?.tags?.let { entry.key to it }
    }
    if (picks.isEmpty()) {
        val blockedOut = recommendations.entries.filter { it.value is GalleryRecommendation.NoUsableTags }
        Text(
            text = if (blockedOut.isEmpty()) {
                "还没有画廊收藏。收藏几张图，这里会长出「按你的口味」的入口。"
            } else {
                blockedOut.joinToString("、") {
                    "${it.key.displayName}：你收藏的那些标签都被屏蔽规则挡完了（或标签本身是空的），算不出推荐"
                }
            },
            fontSize = tokens.type.caption,
            color = tokens.color.textTertiary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = tokens.spacing.space4),
        )
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "根据你的收藏",
            fontSize = tokens.type.caption,
            color = tokens.color.textTertiary,
            modifier = Modifier.padding(bottom = tokens.spacing.space1),
        )
        picks.forEach { (site, tags) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = tokens.spacing.listRowMinHeight)
                    .clip(RoundedCornerShape(tokens.shape.small))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                    ) { onPick(site, tags) }
                    .padding(start = tokens.spacing.space4, end = tokens.spacing.space1),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = tokens.color.textTertiary,
                    modifier = Modifier.size(tokens.spacing.chipIconSize),
                )
                Spacer(modifier = Modifier.width(tokens.spacing.space6))
                Text(
                    // 走 queryOf 而不是自己 join：这一串**就是**要发出去的搜索条件，
                    // 拼法必须与真正发请求那处同一个来源，否则会出现"看着一样、搜出来不一样"。
                    text = GallerySearch.queryOf(tags.map { GalleryTagFilter(it) }),
                    fontSize = tokens.type.body,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = site.displayName,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(start = tokens.spacing.space6),
                )
            }
        }
    }
}

@Composable
private fun HistoryHeader(onClearAll: () -> Unit) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "最近搜索",
            fontSize = tokens.type.caption,
            color = tokens.color.textTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "清空",
            fontSize = tokens.type.caption,
            color = tokens.color.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(tokens.shape.small))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                ) { onClearAll() }
                .padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space4),
        )
    }
}

/**
 * 最近搜索列表。每行两件事：**点一行 = 把那一轮的条件装回来**（不直接开搜，用户 2026-09-25 拍板），
 * **点右边的 × = 删这一条**（`clearHistory` 曾是个只有实现没有入口的死方法）。
 */
@Composable
private fun HistoryList(
    history: List<GallerySearchEntry>,
    onPick: (GallerySearchEntry) -> Unit,
    onRemove: (GallerySearchEntry) -> Unit,
) {
    val tokens = VeneraTokens
    if (history.isEmpty()) {
        Text(
            text = "输入标签名（英文或下划线串），从补全里点选。两站标签词表不通，所以一次只搜一个站。",
            fontSize = tokens.type.caption,
            color = tokens.color.textTertiary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = tokens.spacing.space4),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(history, key = { "${it.site.routeKey}=${it.query}" }) { entry ->
            HistoryRow(
                entry = entry,
                onPick = { onPick(entry) },
                onRemove = { onRemove(entry) },
            )
        }
    }
}

@Composable
private fun HistoryRow(
    entry: GallerySearchEntry,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    val tokens = VeneraTokens
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(tokens.shape.small)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = tokens.spacing.listRowMinHeight)
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = ripple()) { onPick() }
            .padding(start = tokens.spacing.space4, end = tokens.spacing.space1),
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            tint = tokens.color.textTertiary,
            modifier = Modifier.size(tokens.spacing.chipIconSize),
        )
        Spacer(modifier = Modifier.width(tokens.spacing.space6))
        Text(
            text = historyQueryLabel(entry.query, tokens.color.textTertiary),
            fontSize = tokens.type.body,
            color = tokens.color.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(tokens.spacing.space4))
        Text(
            text = entry.site.displayName,
            fontSize = tokens.type.caption,
            color = tokens.color.textTertiary,
            maxLines = 1,
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "删掉这条历史",
                tint = tokens.color.textTertiary,
                modifier = Modifier.size(tokens.spacing.chipIconSize),
            )
        }
    }
}

/**
 * 历史行的查询串：**排除项压暗**。
 *
 * 直接把 `loli -rating:explicit` 原样摆出来，读起来像一行日志。压暗 `-` 开头的那几段之后，
 * 一眼能分出"我要找的"和"我排掉的"，也不用发明一套新语法（那串仍是站方原样，复制去网页能搜）。
 */
private fun historyQueryLabel(query: String, tertiary: Color): AnnotatedString =
    buildAnnotatedString {
        query.split(' ').forEachIndexed { index, part ->
            if (index > 0) append(" ")
            if (part.startsWith("-")) {
                withStyle(SpanStyle(color = tertiary)) { append(part) }
            } else {
                append(part)
            }
        }
    }

/** 标签计数加千分位：`211823` → `211,823`。图站计数动辄六位，不加分隔号读不出量级。 */
private fun formatTagCount(count: Int): String = String.format(Locale.US, "%,d", count)

@Composable
internal fun SearchNoticeLine(text: String) {
    val tokens = VeneraTokens
    Text(
        text = text,
        fontSize = tokens.type.caption,
        color = tokens.color.textSecondary,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 结果墙页尾那一行读数。样式照日榜的 `GalleryFeedEnd`（overline + textTertiary + 居中）。
 *
 * 刻意**不报"共 N 条"**：两站都没有总数端点（实测 404），报总数就是编。
 * "站方给了行但没给图"那一支也要报出来（[noImage]）—— 那正是"搜得出条数却屏上没图"的真实成因，
 * 不点名就会被当成网络问题去查。
 */
@Composable
fun GallerySearchEnd(
    cards: Int,
    query: String,
    page: Int,
    exhausted: Boolean,
    loadingMore: Boolean,
    blockedCount: Int,
    blockedRules: List<String>,
    hiddenByRating: Int,
    videos: Int,
    noImage: String?,
    /**
     * 这一站把条件砍过（预算装不下全部胶囊）时的读数；没砍就 null。
     *
     * 必须摆在这儿而不是只摆在展开区：收成一条之后展开区整个不画了，
     * 而"我明明选了 3 枚、怎么像只搜了 2 枚"这件事恰恰是收条之后才看得出来的。
     */
    trimmedNote: String? = null,
    /** 续页那一笔的失败原因；非空时页尾如实说 + 给一枚「重试」。 */
    loadMoreError: String? = null,
    onRetryLoadMore: () -> Unit = {},
) {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = buildString {
                append("「$query」已摆出 $cards 张")
                if (page > 1) append(" · 第 $page 页")
                if (videos > 0) append(" · 含 $videos 个视频")
                if (blockedCount > 0) append("；另有 $blockedCount 张命中屏蔽规则 ${blockedRules.joinToString("、")}")
                if (hiddenByRating > 0) append("；$hiddenByRating 张按「成人内容处理」收起")
                if (noImage != null) append("；$noImage")
                append(
                    when {
                        loadingMore -> "；正在取下一页"
                        exhausted -> "；已经到底"
                        // 失败时**不能**再说"上滑继续取" —— 那是一句假读数：此刻怎么滑都不会再发请求。
                        loadMoreError != null -> ""
                        else -> "；上滑继续取"
                    },
                )
            },
            fontSize = tokens.type.overline,
            color = tokens.color.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // 续页失败：说清是哪一笔失败，并给一枚**真能发出请求**的重试。
        // 此前没有这一块 —— 失败之后页面进入"永远不再取下一页"，而上面那行还写着"上滑继续取"。
        if (trimmedNote != null) {
            Spacer(modifier = Modifier.height(tokens.spacing.space3))
            Text(
                text = trimmedNote,
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (loadMoreError != null) {
            Spacer(modifier = Modifier.height(tokens.spacing.space3))
            Text(
                text = "下一页没取到：$loadMoreError",
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(tokens.spacing.space2))
            VeneraChip(
                text = "重试",
                variant = VeneraChipVariant.Assist,
                onClick = onRetryLoadMore,
            )
        }
    }
}
