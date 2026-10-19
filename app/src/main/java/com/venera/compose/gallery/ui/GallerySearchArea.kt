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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
// ── 本文件仍保留的 M3 直连（登记在册）──
// · DatePicker + rememberDatePickerState：miuix 只有 NumberPicker，日历面没有对应物，
//   所以"看哪一期"整枚弹窗（含它内部的两颗按钮）保持 M3 —— 换后端会静默丢掉日历。
// · DropdownMenu / DropdownMenuItem：miuix 只有弹层模型不同的 ListPopup。
// · 按压反馈这里**不再写死 ripple()**：MiuixTheme 自己 provide 了 LocalIndication = MiuixIndication，
//   显式传 ripple() 等于在 Miuix 主题上强按一层 M3 水波，所以交回环境。
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.venera.compose.gallery.data.GalleryPorts
import top.yukonga.miuix.kmp.basic.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.SegmentedBackdrop
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraChipVariant
import com.venera.compose.components.venera.VeneraSegmentedButton
import com.venera.compose.components.venera.veneraGlassSurface
import com.venera.compose.components.venera.veneraSurfaceShadowElevation
import com.venera.compose.feature.LocalVeneraDarkTheme
import com.venera.compose.gallery.data.GalleryTagSuggestion
import com.venera.compose.gallery.domain.GalleryHomeDomain
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GalleryRankings
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.gallery.domain.GalleryTagFilter
import com.venera.compose.gallery.domain.galleryNotices
import com.venera.compose.gallery.domain.gallerySourceMarkText
import com.venera.compose.ui.tokens.GalleryTagCategoryColors
import com.venera.compose.ui.tokens.VeneraGlassRole
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.ui.tokens.veneraGlassEnabled
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
 * **只有一个触发点：系统返回**（[GalleryScreen] 那层 BackHandler；键盘起着时返回键被输入法吃掉，
 * 落到它手上的是键盘没起的那一种）。
 * 从前还有第二个"键盘一收就自动收条"，2026-10-15 按用户点名撤掉了 —— 理由写在那处注释里，
 * 一句话版：卡片会在用户还没挑完补全的时候自己跑掉，而缺席原因恰好只住在展开区。
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
    /** 「以图搜图」那一层。开着时**同一张卡原地形变**成反搜的输入形态（2026-09-28 改，见下）。 */
    rvm: GalleryReverseViewModel,
    /** 与那面墙同一把的分级判据，直接透传给 SauceNAO 的 `hide` 参数。 */
    allowNsfw: Boolean,
    /**
     * 结果态那枚读数槽要报的张数。**与页尾 `GallerySearchEnd` 同一把尺子**：
     * 两处都由调用方喂 `searchWall.cards.size`（"真正摆出来的那些"，不是站方声称的总数）——
     * 同一个数在两处报出两个值，就是假读数。
     *
     * 传 null = 这一轮还没到货（读数槽改画波浪环）。刻意**不给默认值**：
     * 默认 null 会让下一个调用点忘传时静默变成"永远在取数"，而那是屏上看得见的一句假话。
     */
    resultCount: Int?,
) {
    // ── 内容域分流（2026-10-17）──
    // pixiv 吃的是**另一套语法**：一个词 + 站内参数，连"标签胶囊进框"这个概念都没有
    // （它不把词拆成多枚，也不吃 `tags=`）。所以整张卡换掉，而不是在这张卡里长分支 ——
    // 那些分支里任何一处走错都不报错，只会在屏上留下"条件写着 A、结果是 B"。
    if (svm.domain == GalleryHomeDomain.PIXIV) {
        GalleryPixivSearchArea(
            svm = svm,
            rvm = rvm,
            allowNsfw = allowNsfw,
            resultCount = resultCount,
        )
        return
    }
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

    /*
     * 排行菜单与选期弹层的开关**记在这里，不记在芯片里**。
     *
     * 记在芯片里会出这条 bug（2026-09-29 第四轮）：展开态点「排行：默认」→ 芯片从 `FlowRow`
     * 那一支换到 `Row` 那一支（收条时就是这一换），组合位置一变就是**新实例**，`remember` 归零
     * → 菜单闪一下就没了。
     * 提到两种形态之上之后，收条照收（现在只有"系统返回"这一条路会收），
     * 菜单只是改挂到收条那一颗芯片上，不会自己关掉。
     */
    var rankingMenuOpen by remember { mutableStateOf(false) }
    var periodPickerOpen by remember { mutableStateOf(false) }

    // 历史在这一态要用（下面空框时的「最近搜索」列表），而恢复点只有一处：
    // 2026-09-30 第二轮它从 `GalleryScreen` 搬回这里 —— 首页那节撤下之后，
    // **这里重新成为唯一消费它的地方**（`restoreHistoryIfNeeded` 自带
    // `if (history.isNotEmpty()) return`，所以重复调用是幂等的）。
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

    // 键盘收起**不再**自动把这张卡收成一条（用户 2026-10-15 点名）。
    //
    // 从前这里挂着一道 effect：`imeVisible` 下降沿 → `collapseToResults()`。它的出发点是
    // "手一收键盘就是想看图"，真机上的代价是**卡片自己会跑**：
    //  1. 补全列表点一枚 = 加一枚胶囊 = 立刻重查，而输入法在多数机型上会随这次重绘落一次
    //     —— 用户看到的是"我还没挑完，整块就收走了"；
    //  2. 弹层（排行菜单 / 选期日历）抢焦点导致的下落被当成"用户要收"，2026-09-29 为此
    //     专门抽过一颗 `GallerySearchCollapse` 判据挡三种误伤 —— 挡到最后一共剩两种不挡；
    //  3. 这一档缺席原因（`GalleryNotices`）就住在展开区里，收条那一刻它整个不画，
    //     于是"这一轮哪条腿没跑起来"在用户最关心的时刻恰好看不见。
    // 收条于是只剩一个触发点：**系统返回**（`GalleryScreen` 那层 BackHandler）。
    // 展开的入口不变：点卡上任意位置 / 顶栏那枚 🔍。
    //
    // 键盘在不在仍然参与**高度**判据（`listMaxHeight` 上面那几行），撤掉的只是"收条"这一件事。

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
    LaunchedEffect(svm.term, svm.source, expanded) {
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
            .padding(
                start = tokens.spacing.screenHorizontal,
                end = tokens.spacing.screenHorizontal,
                top = tokens.spacing.space2,
                bottom = tokens.spacing.space3,
            ),
        // 两态都从**左侧**起排。收起态已改满宽（见下面那段），"挂哪边"这件事随之消失；
        // 而从左侧起排同时是展开态、以及它身下那条常驻入口条的阅读起点 —— 三处同一条。
        horizontalAlignment = Alignment.Start,
    ) {
        Surface(
            shape = cardShape,
            // ── 结果态那张卡：**满宽** + 走材质轴（2026-10-16 第三轮，用户拍板「设成全宽吧」）──
            // 它是**浮层**（锚在入口条位置、不推内容），所以首屏那一排正好压在第一排图上；
            // 用户这一轮给的解法不是"再省一点遮挡"，而是**把这一条直接铺满**：
            //  - 满宽之后它**与身下那条常驻入口条逐像素同宽**（两处同吃 `screenHorizontal` 内缩），
            //    于是"展开时是同一块面在长高"这条契约在宽度上也成立了 —— 从前 66% 那一版
            //    长开时会**横向跳一下**（这是收窄方案一直带着的隐性代价）；
            //  - 满宽腾出来的右侧那一大块不再是空白，正好拿来摆读数槽（见下面那个分支）。
            // 走过的弯路如实记着：第一轮收成顶栏吸附层那一档（34%）→ 真机把胶囊切了半截；
            // 第二轮取补数（66%）+ 挂右 → 不切了，但仍然横向跳、右侧仍是空白。
            // **"省遮挡"这条路到此为止**：遮挡由下面 `searchResultsInset` 那条稳定空白带负责，
            // 卡的宽度不再兼任"少挡一点"这件事（两个目标压在一个数上，两边都做不到位）。
            //  底色：**走材质轴**（2026-10-16 修，用户点名「补回材质的问题」）——
            //       实色档不透明地铺 `surfaceContainerHigh`；玻璃档底色置透明、由
            //       `veneraGlassSurface(PANEL)` 采氛围层做真模糊（不透明底会把刚采的样全盖掉）。
            //       ⚠️ 从前这里是 `surfaceContainerHigh.copy(alpha = tagContainerAlpha)` 的**伪透**，
            //       两处都错：① 它绕过 `SurfaceMaterial` 轴 —— 用户把材质切到"实色"，这一张照旧
            //       半透明，正是本仓明令禁止的假开关（同 `veneraGlassCardColors` 头注那一条）；
            //       ② 0.90 落在最不划算的凹谷 —— 只透 10%，读不出玻璃，却足够把卡色染成底下
            //       那张图的颜色（真机采样卡内三点 #352A28 / #3A3230 / #403635 各不相同，
            //       同卡内底色居然不是一个值）。两态**同一个材质**：展开态下面压着键盘与补全，
            //       玻璃采的也是氛围层（不采页面内容），文字对比由容器 alpha 兜着，不会更难读。
            color = if (veneraGlassEnabled()) Color.Transparent else tokens.color.surfaceContainerHigh,
            // ⚠️ 玻璃档归零，**不能**直接写 `tokens.elevation.attached`（2026-10-16 修）：
            // miuix Surface 把投影挂在调用方 modifier **之内**的 graphicsLayer 上，绘制层序是
            // ① 模糊 → ② 容器色 → **③ 平台投影** → ④ 子元素。卡底透明时 ③ 那层黑剪影会从卡底
            // 透出来 —— 真机实测 `#C1B3B2` = `surfaceContainerHigh × 0.785`，且与背后亮度无关。
            // 铁证：同一张卡里胶囊是亮的、胶囊周围的卡底是灰的（③ 在 ④ 之下）。
            // 读数与判据见 `VeneraGlass.veneraSurfaceShadowElevation`。
            shadowElevation = veneraSurfaceShadowElevation(tokens.elevation.attached),
            modifier = Modifier
                // 玻璃挂在这一格：与 VeneraCard / 设置页分组卡**同一处入口**
                // （`veneraGlassSurface` 是"一块圆角表面要不要玻璃"的唯一出处，
                //  免得同类元素在各页长成两样）。实色档它是纯 no-op。
                .veneraGlassSurface(VeneraGlassRole.PANEL, tokens.shape.extraLarge)
                // **两态都满宽**：收起态从前那条 `widthIn(max = 屏宽 × 66%)` 已撤
                // （用户 2026-10-16「设成全宽吧」；代价与理由写在上面那段）。
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
                                RankingChip(
                                    svm = svm,
                                    menuOpen = rankingMenuOpen,
                                    onMenuOpen = { rankingMenuOpen = it },
                                    pickerOpen = periodPickerOpen,
                                    onPickerOpen = { periodPickerOpen = it },
                                )
                            }
                        } else {
                            // 满宽之后这一行分成**两段**：左边是可横滑的条件（条件再多也只占一格），
                            // 右边是**读数槽**。
                            //
                            // 读数槽刻意**不进横滑**：它报的是"这一轮回了多少张 / 有没有腿没跑起来"，
                            // 滑走就等于没有 —— 而这正是用户往下看图时唯一还看得见这条卡的时刻。
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    svm.filters.forEach { filter ->
                                        FilterChipItem(
                                            filter = filter,
                                            onToggle = { toggleExclude(filter) },
                                            onRemove = { removeFilter(filter) },
                                        )
                                    }
                                    // 排行档跟着胶囊走到底：收成一条那一态它也在，
                                    // 否则"这一墙是按什么排的"在下拉看图时就没了落点。
                                    RankingChip(
                                        svm = svm,
                                        menuOpen = rankingMenuOpen,
                                        onMenuOpen = { rankingMenuOpen = it },
                                        pickerOpen = periodPickerOpen,
                                        onPickerOpen = { periodPickerOpen = it },
                                    )
                                }
                                Spacer(modifier = Modifier.width(tokens.spacing.space6))
                                SearchResultReadout(
                                    svm = svm,
                                    resultCount = resultCount,
                                    onOpenInput = { svm.backToInput() },
                                )
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
                // 反搜态没有"收成一条"的形态（它的身体本来就只有两行），
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
                            modifier = Modifier
                                .fillMaxWidth()
                                // 2026-10-15 照参考图：输入行自己是一颗**填充**胶囊（不是描边 —— 描边那版被否）。
                                // 底取 `surfaceVariant`，比卡底 `surfaceContainerHigh` 低一档（参考图实测
                                // 卡底/控件底 1.055:1 与 1.115:1 两级），于是"这一行才是能敲东西的框"
                                // 由底色表达，屏上一根线都不画。
                                //
                                // ⚠️ 必须是**定高**而不是 `heightIn(min = )`：这一行里有一根
                                // `fillMaxHeight()` 的竖分隔，而 Row 的高度取"子件最大测量高" ——
                                // 只给下限时那根分隔会把父级传下来的剩余全高一口吃满。真机读数：
                                // 整行被撑成 ~520dp，而 `percent = 50` 的圆角半径取短边 ⇒ 屏上是一枚
                                // **整屏高的拱门**，分段器与历史全被顶出屏幕。定高取 MD3 docked search
                                // bar 那一档，与首页那条常驻入口条同值同形状（不立新数字）。
                                .clip(RoundedCornerShape(percent = 50))
                                .background(tokens.color.surfaceVariant)
                                .height(tokens.spacing.dockedSearchBarHeight)
                                .padding(horizontal = tokens.spacing.space6),
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
                                            // 占位不写站名：来源就在下面那排分段器上摆着，这里再写一遍"当前哪一站"
                                            // 就得跟着档改字（「全部」档压根没有单一站名可写）。
                                            // 说清这一框能敲什么更有用。
                                            text = "标签 / 画师 / 作品",
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

                            // 参考图那一根竖分隔：把"框里能敲的字"与"框尾那两枚动作"切开。
                            // 从前它们只靠 12dp 的间距并列，读起来像三个都能敲的东西 ——
                            // 而其中两枚按下去是换态/清空，语义完全不同一档。
                            // 它写 `fillMaxHeight()` 是安全的：所在那一颗 Row 上面已经**定高**，
                            // 那条"整屏拱门"的事故正是这两者配错才有的（见那颗 Row 的注释）。
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(tokens.spacing.hairline)
                                    .background(tokens.color.outlineVariant),
                            )
                            // ── 尾随钮：按形态给不同的"现在能退掉什么"，原地过渡 ──
                            //
                            // 批次 K 把「以图搜图」也搬进这一格：它原先贴在站点选择器旁边，
                            // 于是"输入框自己变成另一副样子"的那具身体里，按钮长在框外面 ——
                            // 点的位置与形变发生的位置对不上，读起来就像换了个页面。
                            // 标签态这一格里同时放两枚：反搜入口 + "现在能退掉什么"（清空 / 删最后一枚）。
                            Crossfade(
                                targetState = rvm.open,
                                animationSpec = tween(tokens.motion.medium),
                                label = "trailing",
                            ) { reverse ->
                                if (reverse) {
                                    ReverseTrailingActions(rvm)
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ImageSearch,
                                            contentDescription = "以图搜图",
                                            tint = tokens.color.textSecondary,
                                            modifier = Modifier
                                                .size(tokens.spacing.chipIconSize)
                                                .clip(RoundedCornerShape(tokens.shape.small))
                                                .clickable { rvm.openLayer() },
                                        )
                                        if (svm.term.isNotEmpty() || svm.filters.isNotEmpty()) {
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
                    }
                }

                // ── 展开出来的那一半（只有输入端在）──
                // 用 AnimatedVisibility 让它**长开**而不是跳变。
                //
                // ⚠️ 2026-09-30 第二轮：**网格不再跟着它避让了** —— 这张卡改成了从入口条位置
                // 弹出的浮层（不再推内容），所以"高度由外面逐帧量走"那条通道
                // （`onSizeChanged` 回给页面算 padding）已经整条撤掉。这里保留长开动画只是因为
                // 卡片自己长高读起来更顺，与网格无关了。
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
                        // 站点（来源）切换。批次 K 之后这一行**只有它**：
                        // 反搜入口搬进了输入框的尾随格（形变的那具身体里，按钮该长在框上）。
                        //
                        // MD3 对 2~5 个互斥选项的标准件是 Segmented Button，
                        // 不是一排可横滑的 filter chip。「全部」这一档不是第三个站，
                        // 它是**一次打到两站**的来源选择，所以列表来自 [GallerySearchSource.options]
                        // 而不是 `GallerySite.entries`（默认档在首，两站按站表序跟在后面）。
                        VeneraSegmentedButton(
                            options = GallerySearchSource.options.map { it.label },
                            selectedIndex = GallerySearchSource.options.indexOf(svm.source).coerceAtLeast(0),
                            onSelect = { index ->
                                GallerySearchSource.options.getOrNull(index)?.let { svm.setSource(it) }
                            },
                            // 这一排住在**实色卡**上而不是玻璃顶栏：半透明底会被卡底吃掉（真机现量
                            // 未选档与卡底只差 1.021:1），而首页那一排摘掉之后这里是唯一的换源入口。
                            backdrop = SegmentedBackdrop.Opaque,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        svm.notice?.let {
                            Spacer(modifier = Modifier.height(tokens.spacing.space3))
                            SearchNoticeLine(it)
                        }
                        // ── 站点筛选栏**下方**那条提示（2026-10-12 改造）──
                        //
                        // 这一轮有条腿没跑起来 → 卡上就说出来。只写在页尾是不够的：
                        // 展开着卡的人看的是这一块，而"少了一个站的货"必须在他改条件之前就看得见。
                        //
                        // 从前这里是**一行灰字**（`legFailureNotice()` 拼的那串），三类处境同一张脸。
                        // 现在按档分块，未配账号那一档带一枚「去配置账号」——那句文案本来就写着
                        // "到「设置 → 画廊」里去填"，而此前屏上没有任何能按的东西。
                        //
                        // ⚠️ 重试走 `svm.runSearch(1)`（第 1 页可打断在途那一笔），
                        // 不是 `retryLoadMore()`：这里失败的是**第一轮**取数，
                        // 屏上还没有任何结果，续页那条路的前提不成立。
                        GalleryNotices(
                            notices = remember(svm.legFailures) { galleryNotices(svm.legFailures) },
                            compact = true,
                            onRetry = { svm.runSearch(1) },
                            modifier = Modifier.padding(top = tokens.spacing.space3),
                        )
                        // 换过画师名也要在这儿说：卡上那排胶囊是用户打的**原词**，
                        // 而屏上那面墙已经是另一个人的画了 —— 不在这儿交代，读起来就是"搜错了"。
                        svm.artistAliasNotice?.let {
                            Spacer(modifier = Modifier.height(tokens.spacing.space3))
                            SearchNoticeLine(it)
                        }

                        // 胶囊进框之后，"点胶囊改成排除"是个不直观的交互，得在这儿说一句。
                        // 只在真有胶囊时出现 —— 空框时这两个动作都还不存在，说了也只是噪音。
                        //
                        // ⚠️ 从前这句带着"（前面加 -）"，2026-10-15 撤：那是把**查询语法**教给用户，
                        // 而这一屏的交互本来就是即时生效的胶囊（点一下即换档、点 × 即删），
                        // 语法那一路是内部写法，屏上不欠用户一课（口径见批 2 的"常驻行不教操作语法"）。
                        if (svm.filters.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(tokens.spacing.space3))
                            SearchNoticeLine(
                                "点标签改成排除，点标签上的 × 删掉" +
                                    (GallerySearch.budgetNotice(svm.site)?.let { "。$it" } ?: ""),
                            )
                        }

                        // 打字看补全；框里没字、而且屏上还**没有**一轮搜索结果时看推荐。
                        //
                        // 第三档（没字 + 已经有结果或空态）**刻意不摆列表**：用户此刻看的是结果那一片，
                        // 而摊着的列表会把它挤到键盘底下 —— 真机实测那一档是
                        // 卡片 ~266dp + 键盘 ~340dp + 顶栏地板 ~128dp，873dp 的屏只剩 ~124dp，
                        // 连"这一串标签没有可摆的图"那句都读不全，只剩一个图标露头。
                        // 想接着加标签直接打字，补全照样出来。
                        //
                        // ⚠️ 2026-09-30：这一档里的「最近搜索」**整块搬去了首页第 5 节**
                        // （用户拍板"上首页、并从搜索卡里撤下"）。理由有两条，都不是省事：
                        //  1. 同一份历史在卡片里与首页各画一遍，用户在任一处删掉之后另一处会显得没同步；
                        //  2. 它本来是"浏览时顺手接着搜"的入口，而卡片只在用户**已经决定要搜**之后才开着 ——
                        //     真正的浏览场景（首页、没点任何东西）此前看不到自己的历史。
                        // 留下的 `RecommendationRows` 是另一件事：那是**我们替他算出来的**，
                        // 只在"他正打算搜"这一刻才有意义，所以它留在卡里。
                        val hasResultRound = svm.page > 0 || svm.isSearching
                        if (svm.term.isNotBlank() || !hasResultRound) {
                            Spacer(modifier = Modifier.height(tokens.spacing.space4))
                            if (svm.term.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = listMaxHeight)
                                        // 限高不是行距的整数倍 ⇒ 最下面那枚会被硬切一半，渐隐收口
                                        // （成因与为什么用 DstIn 见 `fadeBottomEdge`）。
                                        .fadeBottomEdge(tokens.spacing.space6),
                                ) {
                                    SuggestionList(
                                        suggestions = svm.suggestions,
                                        loading = awaitingSuggest || svm.isSuggesting,
                                        error = svm.suggestError,
                                        term = svm.term.trim(),
                                        missCopy = svm.suggestionMissCopy(svm.term.trim()),
                                        onPick = { addTag(it) },
                                    )
                                }
                            } else {
                                // 「最近搜索」只摆**当前域**那几条（2026-10-17 用户点名「两个站源的
                                // 搜索历史要分离」）。判据在 `visibleHistory` 里，盘上的形状没动。
                                if (svm.visibleHistory.isNotEmpty()) {
                                    HistoryHeader(onClearAll = { svm.clearHistory() })
                                    Spacer(modifier = Modifier.height(tokens.spacing.space1))
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = listMaxHeight)
                                        // 限高不是行距的整数倍 ⇒ 最下面那枚会被硬切一半，渐隐收口
                                        // （成因与为什么用 DstIn 见 `fadeBottomEdge`）。
                                        .fadeBottomEdge(tokens.spacing.space6),
                                ) {
                                    HistoryList(
                                        history = svm.visibleHistory,
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
internal fun ReverseTrailingActions(rvm: GalleryReverseViewModel) {
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
        // **显式写 null**：这一排住在"每一行都在抢卡片高度"的那一屏里（见本文件头注），
        // 命中盒一大整排就跟着长高 —— 用户 2026-10-16 点名的正是这个连带效应
        // （「连同搜索tag一起变大了，你分开」）。所以这里退回组件的旧形状：× 的 clickable
        // 仍在图标自己身上。要改大得连着这一行一起改，那一改是有声的。
        removeTouchSize = null,
    )
}

/**
 * 排行档那枚胶囊 + 六行下拉（2026-09-29）。
 *
 * ## 为什么挂在胶囊行**末尾**而不是另起一行
 *
 * 这一屏每一行都在抢卡片的高度（玻璃顶栏那三条硬约束与 `GallerySearchArea` 头注都记着这条口径），
 * 而这一行本来就在：展开态是 `FlowRow`（放不下就换行），收成一条态是单行横滑。
 * 于是卡片的两个形态里"这批图按什么排的"都常驻可见，**零新增高度**。
 *
 * ⚠️ 下滑之后压在顶栏那一行的**吸附胶囊层**（`GallerySearchCollapsedChips`）刻意不跟着加 ——
 * 那一行宽度封在屏宽 34% 之内、要和居中的标题抢位，多塞一颗就把用户自己的条件挤掉了。
 * 想看当前档，回到顶部（卡片长回来）或点开胶囊行末尾这枚。
 *
 * ## 文字为什么只念一小截
 *
 * 胶囊上写「排行：周」（`shortLabel`），完整档名与**实际日期区间**在菜单里 ——
 * 胶囊行是横滑的，每一颗都在跟用户自己攒的条件抢宽度。
 *
 * ## 选中态
 *
 * 非默认档才亮（`selected`）：默认档就是"没开排行"，让它一直亮着等于把常态画成异常态。
 *
 * ## 行**照站画**：摆不出来的档不列出
 *
 * 判据是 [GalleryRankings.supportsForApp] 那一把（2026-10-13 起移动端用它，不是原生那把）：
 * 站方自己给不给是一问（`GalleryRankings.supports`，桌面读这一把），**我们做不做得出**是另一问 ——
 * Gelbooru 的四档时间窗由 id 边界合成（`GelbooruWindowBoundary`），所以纯 Gelbooru 来源列六档。
 * 混腿来源（一条原生 + 一条合成）那四档照旧不列：半面墙按周、半面墙按历年高分，
 * 屏上读不出对错，而胶囊上那个「周」字是在骗人。
 * 更早那版"留着行但置灰 + 写'本站没有'"是 2026-09-29 第五轮被用户点名撤掉的，判据没变。
 *
 * ## 末行「选具体哪一期…」
 *
 * 月/年档可以翻历史期。yande.re 原生认 `date:A..B`，任意一期都是**一笔请求**（§十二）；
 * Gelbooru 要**两枚**边界合成（`id:>=A id:<B`，2026-10-16 带凭据在真站上量过算术），
 * 于是这一腿的等待按 [GelbooruWindowBoundary.CLOSED_WINDOW_LEG_TIMEOUT_MS] 放宽 ——
 * 第一次点某一历史期要等十来秒，同一期在同一颗 ViewModel 里问第二遍是缓存直接的。
 * 入口放在同一个下拉的末尾而不是另起一行：这一屏每一行都在抢卡片高度，同一条口径。
 * 摆不摆这一行问的是每一条腿（[GalleryRankings.supportsPeriodPickerForApp]）——
 * 给不出窗口的来源，连这一行也不出现。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RankingChip(
    svm: GallerySearchViewModel,
    menuOpen: Boolean,
    onMenuOpen: (Boolean) -> Unit,
    pickerOpen: Boolean,
    onPickerOpen: (Boolean) -> Unit,
) {
    val tokens = VeneraTokens
    // 日切按 UTC，与站方 created_at 同一时区（判据在 GalleryRankings，这里只取读数）。
    val todayUtc = LocalDate.now(ZoneOffset.UTC)
    Box {
        VeneraChip(
            text = buildString {
                append("排行：${svm.ranking.shortLabel}")
                // 历史期把期次直接标在胶囊上：「排行：月 2024-03」——
                // 不点开下拉也读得出看的是哪一期，不然"这一墙为什么全是三年前的图"没法解释。
                GalleryRankings.periodShortLabel(svm.ranking, svm.periodAnchor)?.let { append(" $it") }
            },
            selected = svm.ranking != GalleryRanking.NEWEST,
            variant = VeneraChipVariant.Filter,
            onClick = { onMenuOpen(true) },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpen(false) }) {
            // 这一档摆不摆这一行，问的是**每一条腿**（[GalleryRankings.supportsForApp]）：
            // 「全部」档里 Gelbooru 给不了时间窗，那一档就只剩「默认」与「全部排行」两行。
            // 照第一条腿列六行会出现"胶囊写着周、Gelbooru 那腿交回历年高分"—— 假开关。
            // 本站没有的档**不列出**（2026-09-29 定的口径：原先"留着行但置灰 + 写'本站没有'"，
            // 实际观感是六行里四行点不动，比少两行更烦；变的只是不参与判据的行不再画出来）。
            // 2026-10-13：这一把换成**移动端那把** —— Gelbooru 的纯本站来源现在多出天/周/月/年四行，
            // 那四档站方不给、由 id 边界合成（`GelbooruWindowBoundary`），混腿来源照旧不列。
            GalleryRanking.entries
                .filter { GalleryRankings.supportsForApp(svm.source.sites, it) }
                .forEach { ranking ->
                    val range = GalleryRankings.windowLabel(ranking, todayUtc)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = ranking.label + if (range != null) "　$range" else "",
                                fontSize = tokens.type.body,
                                // 当前档给主色：菜单里几行长得一样，不点亮就读不出"现在是哪一档"。
                                color = if (ranking == svm.ranking) tokens.color.primary else Color.Unspecified,
                            )
                        },
                        trailingIcon = {
                            if (ranking == svm.ranking) Icon(imageVector = Icons.Outlined.Check, contentDescription = null)
                        },
                        onClick = {
                            onMenuOpen(false)
                            // 选中即重查（这套 UI 没有"提交"那一步）。
                            svm.setRanking(ranking)
                        },
                    )
                }
            // 「选哪一期」同样按**每一条腿**判，但用的是移动端那把（[GalleryRankings.supportsPeriodPickerForApp]）：
            // Gelbooru 的历史期由 id 边界合成（起、止两枚），站方自己不给 `date:` —— 拿原生那把判，
            // 这一行在纯 Gelbooru 来源上永远不出现，而那四档合成档明明已经能翻到具体哪一期。
            // 混腿来源（一条原生 + 一条合成）照旧不摆：半面墙按这一期、半面墙按别的，
            // 而胶囊上写着期次 —— 同一个假开关的入口版。
            if (GalleryRankings.supportsPeriodPickerForApp(svm.source.sites)) {
                DropdownMenuItem(
                    text = { Text(text = "选具体哪一期…　月 / 年", fontSize = tokens.type.body) },
                    onClick = {
                        onMenuOpen(false)
                        onPickerOpen(true)
                    },
                )
            }
        }
        if (pickerOpen) RankingPeriodDialog(svm = svm, todayUtc = todayUtc, onDismiss = { onPickerOpen(false) })
    }
}

/**
 * 「看哪一期」的弹层（2026-09-29 第二轮，同日晚些按第四轮反馈改形状）。
 *
 * 粒度只有 月 / 年 两枚：用户拍板"天/周回看是上一天/上一周那种一步的事，
 * 为它开一层弹层不值"。
 *
 * 两枚各配一种控件，**不共用日历**：
 * - **按月** = 日历，点任意一天取它所在的那一月（与 moebooru 的日期选择器同一口径）；
 * - **按年** = 年份列表，点一个年份取那一整年。
 *
 * 为什么按年不共用日历：Material3 1.5.0-alpha22 的年份选择器是 `private`（读源码确认，
 * `showModeToggle` 只切"日历 ↔ 数字输入"），拿不到 —— 于是共用日历那版的长相就是
 * "选了按年，下面还是一屏天数"，用户 2026-09-29 原话。自己列一排年份比逼日历换个形状便宜。
 *
 * 年份范围钉在 **2007..今年**：那是 yande.re 存档的实测起点（`date:..2006-06-01` 已经回 0 条），
 * 给一个翻得出空页的年份就是假入口。往后的年份不给选（窗口终点恒被今天钉住，
 * 真点出去只会得到 `date:2026-12-01..2026-09-29` 这种起在止之后的串）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RankingPeriodDialog(
    svm: GallerySearchViewModel,
    todayUtc: LocalDate,
    onDismiss: () -> Unit,
) {
    val tokens = VeneraTokens
    var target by remember {
        mutableStateOf(
            if (GalleryRankings.supportsHistory(svm.ranking)) svm.ranking else GalleryRanking.MONTH,
        )
    }
    val byYear = target == GalleryRanking.YEAR
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = (svm.periodAnchor ?: todayUtc).toEpochDay() * MILLIS_PER_DAY,
        yearRange = GalleryRankings.EARLIEST_PERIOD.year..todayUtc.year,
    )
    // 年份那一档另存一份选中值：与日历的 `selectedDateMillis` 分开，
    // 否则"在日历上点过的某一天"会冒充"选过的那一年"。
    var pickedYear by remember { mutableStateOf(svm.periodAnchor?.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("看哪一期") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = if (byYear) "点一个年份，就取那一整年。" else "日历里点任意一天，就取它所在的这一月。",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textSecondary,
                )
                Spacer(modifier = Modifier.height(tokens.spacing.space3))
                Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3)) {
                    VeneraChip(
                        text = "按月",
                        selected = target == GalleryRanking.MONTH,
                        variant = VeneraChipVariant.Filter,
                        onClick = { target = GalleryRanking.MONTH },
                    )
                    VeneraChip(
                        text = "按年",
                        selected = target == GalleryRanking.YEAR,
                        variant = VeneraChipVariant.Filter,
                        onClick = { target = GalleryRanking.YEAR },
                    )
                }
                Spacer(modifier = Modifier.height(tokens.spacing.space3))
                if (byYear) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                    ) {
                        GalleryRankings.yearChoices(todayUtc).forEach { year ->
                            VeneraChip(
                                text = year.toString(),
                                selected = year == pickedYear,
                                variant = VeneraChipVariant.Filter,
                                onClick = { pickedYear = year },
                            )
                        }
                    }
                } else {
                    DatePicker(state = pickerState)
                }
                if (svm.periodAnchor != null) {
                    // 回到本期单独一枚：那是一句"我选错了，撤销"，不该藏在再点一次同一档里。
                    TextButton(onClick = {
                        onDismiss()
                        svm.pickPeriod(target, null)
                    }) { Text("回到本期") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val picked = if (byYear) {
                    // 一年都没挑 = 打开看了一眼，这不是"改期"，一笔请求都不该发（与日历那档同一条口径）。
                    pickedYear?.let { LocalDate.of(it, 1, 1) }
                } else {
                    pickerState.selectedDateMillis
                        ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                }
                onDismiss()
                if (picked == null) return@TextButton
                svm.pickPeriod(target, picked)
            }) { Text("就看这一期") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private const val MILLIS_PER_DAY = 86_400_000L

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
    //
    // pixiv 那一域没有"胶囊"这回事（它的查询是一个词 + 一排站内参数），所以那一侧
    // 摆的是**词 + 条件摘要**：吸附进顶栏时，"我在搜什么"这条读数不能因为换了域就没了。
    if (svm.domain == GalleryHomeDomain.PIXIV) {
        if (svm.pixivTerm.isBlank()) return
        Box(
            modifier = modifier
                .padding(start = tokens.spacing.topBarTitlePadding)
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
                VeneraChip(
                    text = svm.pixivTerm,
                    selected = true,
                    variant = VeneraChipVariant.Filter,
                    onClick = onExpand,
                )
                svm.pixivFilter.summary().drop(1).forEach { line ->
                    VeneraChip(
                        text = line,
                        variant = VeneraChipVariant.Tonal,
                        onClick = onExpand,
                    )
                }
            }
        }
        return
    }
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
 * 结果态那一行尾巴上的**读数槽**：这一轮回了多少张、有没有腿没跑起来。
 *
 * ## 为什么它必须常驻在这一条上
 *
 * 结果态这张卡是"带着条件往下看图"时**唯一还留在屏上的那句话**。它的两个兄弟读数都住在别处：
 *  - 页尾 `GallerySearchEnd` 的「已摆出 N 张」要滚到墙底才看得见；
 *  - 展开区里的 `GalleryNotices`（哪条腿没跑起来的原因）在收成一条之后整块不画。
 *
 * 于是"我这一轮到底出货了没 / 是不是少了一个站"恰好**在最需要它的那一刻是看不见的**。
 * 这一格就是给这两件事的落点（用户 2026-10-16「可以在搜索框里加入其他信息」+ 满宽腾出的那一块）。
 *
 * ## 三态共用同一格
 *
 * | 处境 | 画什么 |
 * |---|---|
 * | 第一轮在途（`isSearching && page == 0`） | 波浪环（本文件既有件），**不报数** |
 * | 到货 | `N 张`（`type.caption` + `textSecondary`） |
 * | 有腿缺席（`svm.legFailures`） | 前置一枚警示字形，点它回展开态看原因 |
 *
 * ⚠️ 张数**不在这里算**：由调用方喂（与页尾同一把尺子，见 [GallerySearchArea] 的 `resultCount`）。
 * 两处各算一遍一定会分叉，而屏上同时摆着两个不同的数，两个都不可信了。
 *
 * ⚠️ `resultCount == null` 且不在途时**什么都不画**：那一档是"已开搜索但还没发过请求"，
 * 报一个 `0 张` 就是把"还没搜"说成"没有结果"。
 */
@Composable
private fun SearchResultReadout(
    svm: GallerySearchViewModel,
    resultCount: Int?,
    onOpenInput: () -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 缺席预警摆最前：它是这一格里唯一"值得用户动一下"的信息（其余两态都只是读数）。
        // 点它回展开态 —— 原因是哪一站、为什么，整段住在那里，不在这儿再打一遍（那句话 40 来个字，
        // 压在收条上只会读成省略号）。
        // 取色用 `onTertiaryContainer`：与 `VeneraNotice` 的 Warning 档同一个语义槽，不新造警告色。
        if (svm.legFailures.isNotEmpty()) {
            Icon(
                imageVector = Icons.Outlined.ReportProblem,
                contentDescription = "这一轮有来源没答上，点开看原因",
                tint = tokens.color.onTertiaryContainer,
                modifier = Modifier
                    .size(tokens.spacing.chipIconSize)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onOpenInput() },
            )
        }
        if (svm.isSearching && svm.page == 0) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(tokens.spacing.loaderInline),
                color = tokens.color.primary,
                trackColor = tokens.color.surfaceVariant,
            )
        } else if (resultCount != null) {
            Text(
                text = "$resultCount 张",
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
                maxLines = 1,
            )
        }
    }
}

/**
 * 底部渐隐：给"被限高切断的内容"一个收口。
 *
 * 为什么需要：补全列表与最近搜索都住在 `heightIn(max = listMaxHeight)` 里，而那个上限是
 * `listRowMinHeight × 行数` —— 它**不是行距的整数倍**（一行实际占 `listRowMinHeight + space2`），
 * 所以最下面那一枚总是被**硬切一半**（2026-10-16 用户第三张截图上就是那一枚，用户那句「ui有问题」）。
 * 硬切读起来像"这里坏了"，渐隐读起来是"下面还有"。
 *
 * 用 `DstIn` 而不是画一块面板色的矩形：面板在玻璃档是半透明的，拿容器色去盖会盖出一道
 * 比卡底更实的带子；`DstIn` 只改 alpha，与底下是什么色无关，两档材质下都对。
 *
 * ⚠️ **`DstIn` 必须配一层离屏层**（`CompositingStrategy.Offscreen`），否则它"只改 alpha"改的
 * 不是**这一段自己**的 alpha —— 详见下面那句 `graphicsLayer` 上的注释与真机读数。
 * 这两句是一体的，拆开就出 bug（2026-10-16 已经出过一次）。
 */
private fun Modifier.fadeBottomEdge(fadeHeight: Dp): Modifier =
    // ⚠️ 离屏层是**前置条件**，不是优化（2026-10-16 修，用户原话「但是这下下面有黑边了」）。
    //
    // `drawWithContent { drawContent(); drawRect(..., DstIn) }` 单独用时，`DstIn` 的 dst 不是
    // "这一段自己刚画的内容"，而是**当前画布上已经画过的一切** —— 它会连卡底之下的氛围层与
    // 图墙一起乘以渐变 alpha 抹掉，抹到 alpha=0 就透到窗口背景（纯黑）。于是"渐隐"变成"黑边"。
    //
    // 真机读数（2026-10-16 12:51，浅色玻璃）：卡内 `#F5DDDD`(L=0.763) → 渐变起点 `#E9D4D3`
    // → 单调压暗 → **`#0F0B08`(L=0.004，纯黑)** → 下一像素回到 `#F4E8E8`。
    // 横向 x=60..813（正是卡内、避开圆角）整条 `#454042` —— 那是卡自己的粉被 DstIn 乘剩的残值。
    //
    // 开了离屏层之后 dst 只有这一段的内容，"渐隐到透明"才是它字面上的意思。
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = fadeHeight.toPx()
            if (fade <= 0f) return@drawWithContent
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - fade,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
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
    /** 「没有以它开头的标签」那行整句由 ViewModel 拼（要按**真正问过的那几条腿**说，见 [GallerySearchViewModel.suggestionMissCopy]）。 */
    missCopy: String,
    onPick: (GalleryTagSuggestion) -> Unit,
) {
    val context = LocalContext.current
    // 补全行的中文译名：**整表一次查**，不给每行各查一次（八行就是八笔主键查，纯属白给）。
    // 查不到的那一行原样显示站方给的词 —— 宁可不译，不猜一个看着像的。
    var translations by remember(suggestions) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(suggestions) {
        translations = if (suggestions.isEmpty()) emptyMap() else GalleryPorts.of(context).lexicon.translations(suggestions.map { it.name })
    }
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
                    text = error ?: missCopy,
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
                label = translations[suggestion.name.lowercase()]
                    ?.let { "$it (${suggestion.name})" } ?: suggestion.name,
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
    label: String,
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
            .clickable(interactionSource = interactionSource) { onPick(suggestion) }
            .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space2),
    ) {
        Text(
            // 译文只改显示：点这一行交出去的仍是 [GalleryTagSuggestion.name]（见 onPick）。
            text = label,
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

// 「最近搜索」的渲染件整块搬回本文件（2026-09-30 第二轮）：用户拍板
// "最近搜索放回点击搜索框后下面框框着"，所以它重新住进这张卡的空框态。
// 形状与搬到首页之前**逐字相同**（双列紧凑 chip、字母小徽标、`×` 删单条）——
// 那三条都是真机反馈定的（见 `_trash/gallery-history-row-2026-09-30/README.md`）。

/**
 * 「最近搜索」那一行标题 + 「清空」。
 *
 * 标题**不放进限高的列表里**：放进去它会占掉 4 行里的 1 行，于是可见的历史只剩 3 条。
 * 单独一行也让它读起来像"这一栏在说什么"，而不是一条可以点的记录。
 */
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
                ) { onClearAll() }
                .padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space4),
        )
    }
}

/**
 * 最近搜索列表。每行两件事：**点一行 = 把那一轮再跑一遍**，
 * **点右边的 × = 删这一条**（`clearHistory` 曾是个只有实现没有入口的死方法）。
 *
 * **双列紧凑网格**，不是整行的 LazyColumn：
 *  - 同一个词跨站并成一条之后，历史短了，整行布局十行就顶到键盘底下、密度反而更差；
 *  - 每条自持半宽，长查询串自己省略 —— 这一块的用途是"认出我搜过哪个"，不是读全文。
 * 外层那个 `heightIn(max =)` 的限高照旧：行数超了在这块里自己滚，卡片不再跟着长高。
 *
 * @param showSourceMark 前置那枚字母站标画不画。booru 那侧要（同一条历史可能跨过两站，
 *   字母是它唯一还有信息量的差别）；pixiv 那一格不要 —— 那里每一条都属于同一站，
 *   摆一枚 "P" 只把那个词挤窄一截（用户 2026-10-17：「p 站搜索历史不需要显示站点」）。
 */
@Composable
internal fun HistoryList(
    history: List<GallerySearchEntry>,
    onPick: (GallerySearchEntry) -> Unit,
    onRemove: (GallerySearchEntry) -> Unit,
    showSourceMark: Boolean = true,
) {
    val tokens = VeneraTokens
    // ── 删除改成两步（2026-10-16 用户点名「太小很难按到而且还容易删除」）──
    // 记**下标**而不是记那一枚 entry：`GallerySearchEntry` 不是 data class，
    // 拿它当相等判据会退化成"引用相同"，而这一列表每次组合都是新实例。
    var armedIndex by remember { mutableStateOf(-1) }
    LaunchedEffect(armedIndex) {
        if (armedIndex < 0) return@LaunchedEffect
        delay(REMOVE_ARM_MS)
        armedIndex = -1
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        history.chunked(2).forEachIndexed { lineIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
            ) {
                row.forEachIndexed { slot, entry ->
                    val index = lineIndex * 2 + slot
                    val armed = index == armedIndex
                    VeneraChip(
                        text = entry.query,
                        // 字母小徽标（Y / G / Y·G）：这一条在哪些站搜过，是历史里唯一还有信息量的
                        // 差别；摆站名整行会把关键词挤没。
                        leadingText = if (showSourceMark) gallerySourceMarkText(entry.sites) else null,
                        // 第一下 × → 这颗自己变档（`selected` = primaryContainer 实底）并把把手换成 ✓，
                        // 第二下 ✓ 才真删。文案与哪一条都还在原处 —— 只撤掉"一触即删"这一件事。
                        // ✓ 是这一屏既有的"这就是当前那一个"记号（排行下拉里就在用），不立新符号。
                        trailingIcon = if (armed) Icons.Outlined.Check else Icons.Outlined.Close,
                        selected = armed,
                        onClick = {
                            armedIndex = -1
                            onPick(entry)
                        },
                        // × 单独一个动作：整枚 chip 是"按这一条再搜一次"，删掉不能也落在它上面。
                        onRemoveClick = {
                            if (armed) {
                                armedIndex = -1
                                onRemove(entry)
                            } else {
                                armedIndex = index
                            }
                        },
                        // 这一档是"按钮"而不是"一排里的元素"：命中盒 40dp（现成的 `iconButtonSize`），
                        // 图标仍画 16dp ⇒ 视觉一寸没变，命中区从 256dp² 变 1600dp²。
                        // 与上面条件胶囊那一档刻意不同值 —— 理由写在 `FilterChipItem` 那处。
                        removeTouchSize = tokens.spacing.iconButtonSize,
                        // Neutral 填的就是卡底那颗 `surfaceContainerHigh` ⇒ 住在卡里 1.000:1，
                        // 画出来只剩文字悬在卡上。Tonal 是同一形态低一档的底（无描边、无圆点）。
                        variant = VeneraChipVariant.Tonal,
                        modifier = Modifier.weight(1f),
                    )
                }
                // 落单的那条（总数为奇数时最后一行只有一条）占住右半格，别让上一条被拉成满宽。
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(tokens.spacing.space2))
        }
    }
}

/**
 * 历史那枚 × 的"待确认"停留多久（毫秒）。
 *
 * 与补全防抖那 250ms 不是一件事，别合并：那一档是"等用户停手"，这一档是"给用户读完那枚 ✓ 的时间"。
 * 4 秒取的是"够看清、又不至于让人以为卡住了"，超时自动回原样，不留下一个说不清状态的紫块。
 */
private const val REMOVE_ARM_MS = 4_000L

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
     * 当前排行档的读数（含日期区间）；默认档传 null，那一档就是站方原序，没什么可念的。
     *
     * 页尾必须带上它：这行报的是"这面墙是怎么来的"，只报条件不报排法，
     * 用户切完档在页尾读不出区别，就会怀疑那一下没生效。
     */
    ranking: String? = null,
    /**
     * 这一站把条件砍过（预算装不下全部胶囊）时的读数；没砍就 null。
     *
     * 必须摆在这儿而不是只摆在展开区：收成一条之后展开区整个不画了，
     * 而"我明明选了 3 枚、怎么像只搜了 2 枚"这件事恰恰是收条之后才看得出来的。
     */
    trimmedNote: String? = null,
    /**
     * 「这一轮有哪些腿没跑起来」的读数；全都跑起来了传 null。
     *
     * 「全部」档少了一条腿，屏上就少了一个站的货，而这行页尾是用户唯一读得出"少了"的地方 ——
     * 不点名，那一站今天没出货就成了"这站本来就没几张"。
     */
    legNotice: String? = null,
    /**
     * 这一轮把用户打的画师名**换成了站方记的正名**时的那句话；没换传 null。
     *
     * 页尾必须念：上面那行写着「已摆出 N 张」，而条件那一串是用户的原词 ——
     * 少了这一句，"搜 setmen 出来一批 tokenbox 的画"就成了一次没人解释的换词。
     */
    aliasNotice: String? = null,
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
                ranking?.let { append(" · 排行 $it") }
                if (page > 1) append(" · 第 $page 页")
                if (videos > 0) append(" · 含 $videos 个视频")
                if (blockedCount > 0) append("；另有 ${blockedByRulesFragment(blockedCount, blockedRules)}")
                if (hiddenByRating > 0) append("；${ratingHiddenFragment(hiddenByRating)}")
                if (noImage != null) append("；$noImage")
                // 失败档要说空串：状态串由 feedTailStatus 收口，失败原因交给下面那行重试。
                append(feedTailStatus(loadingMore, exhausted, loadMoreError != null))
            },
            fontSize = tokens.type.overline,
            color = tokens.color.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        // 续页失败：说清是哪一笔失败，并给一枚**真能发出请求**的重试。
        // 此前没有这一块 —— 失败之后页面进入"永远不再取下一页"，而上面那行还写着"上滑继续取"。
        // 「条件被砍」与「某条腿没跑起来」与它同式：都是"屏上少了东西，得有个说法"的读数行。
        listOfNotNull(trimmedNote, legNotice, aliasNotice).forEach { note ->
            Spacer(modifier = Modifier.height(tokens.spacing.space3))
            Text(
                text = note,
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        GalleryLoadMoreRetryLine(loadMoreError, onRetryLoadMore)
    }
}
