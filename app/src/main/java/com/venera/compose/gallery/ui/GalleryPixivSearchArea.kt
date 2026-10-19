package com.venera.compose.gallery.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import coil3.ImageLoader
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.venera.compose.components.venera.VeneraChip
import com.venera.compose.components.venera.VeneraChipVariant
import com.venera.compose.components.venera.VeneraPressFeedback
import com.venera.compose.components.venera.veneraAvatarRing
import com.venera.compose.components.venera.veneraGlassSurface
import com.venera.compose.components.venera.veneraSurfaceShadowElevation
import com.venera.compose.gallery.domain.PixivBookmark
import com.venera.compose.gallery.domain.PixivRatio
import com.venera.compose.gallery.domain.PixivSearchFilter
import com.venera.compose.gallery.domain.PixivSearchKind
import com.venera.compose.gallery.domain.PixivSearchOrder
import com.venera.compose.gallery.domain.PixivTagMode
import com.venera.compose.gallery.domain.PixivWorkType
import com.venera.compose.gallery.data.PixivUser
import com.venera.compose.ui.tokens.VeneraGlassRole
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.ui.tokens.veneraGlassEnabled
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * pixiv 那一副搜索卡（2026-10-17 方案 C：**卡内展开的筛选面板**）。
 *
 * ## 它为什么是第二张卡，而不是给原来那张加分支
 *
 * 两张卡吃的是**两套语法**：booru 那张的核心是"标签胶囊进框"（`GalleryTagFilter` + `tags=a+b`），
 * pixiv 这张的核心是"一个词 + 一排站内参数"（词不被拆成胶囊，条件也不进历史）。
 * 硬并成一张会逼出十来个 `if (domain == PIXIV)`，而那些分支里任何一处走错都不报错，
 * 只会在屏上留下"条件写着 A、结果是 B"。所以这一颗自成一体，只共用 ViewModel 的状态
 * （`results / page / mode` 那一套 —— 那面墙、页尾、滚动落点都只认它们）。
 *
 * ## 形态（两态 + 一块可长出来的面板）
 *
 * | 态 | 画什么 |
 * |---|---|
 * | 展开（[GallerySearchMode.INPUT]） | 输入行 → 排序行（含「筛选」入口）→ 历史 / 面板 |
 * | 收成一条（[GallerySearchMode.RESULTS]） | 一行：词 + 生效条件 + 张数读数（点它回展开） |
 *
 * 面板**长在卡里**（方案 C 的要点）：不遮罩、不跳转、不多一层。
 *
 * ⚠️ **这一版不抢键盘**（与稿上那条"点筛选先收键盘"不同，这里要说明为什么改了）：
 * 稿上按四段算的展开高度是 429dp，而键盘在时可用高度只有 423dp（873 − 150 − 300）——
 * 差 6dp，所以那条规矩本来是"没得商量"。落地后四段用的是 token 里那档 chip（比稿上的矮一档）、
 * 行距也按 `space4` 收过，实测约 250dp，加上排序行与输入行，整张卡约 356dp，键盘在也放得下。
 * 既然放得下，就不该去抢用户的键盘 —— 他很可能正想改那个词。
 *
 * ## 面板里那四段各自为什么成立（2026-10-17 深夜改判）
 *
 * 第一版只摆了比例与时间，理由是"收藏与作品在**匿名腿**上被站方静默忽略"。用户当场改判：
 * 「人气 / 收藏 / 作品这三段应该是有的，你看看 pixez 是怎么做的」。查证之后他是对的 ——
 * pixez 走的是**带票据的** `app-api /v1/search/illust`，那上面 `sort=popular_desc` 与
 * `bookmark_num_min` 都是真的；动图那一档它是**客户端筛** `illust.type == 'ugoira'`。
 * 所以现在按登录态二选一取数（见 `GallerySearchViewModel.runPixivSearch`），四段全部保留：
 *
 * - 收藏：登录腿发 `bookmark_num_min`；未登录点它**不改条件**、只说一句话
 *   （匿名跳的 `blt/bgt` 现量逐条一致 = 被忽略，摆一枚亮着"已选中"的开关就是假开关）；
 * - 作品：两条腿都只能**本地筛**（站方没有这枚参数，pixez 也是客户端筛）；
 * - 比例：匿名腿发 `ratio`（现量 total 620880 → 64924），登录腿没有这一枚 ⇒ 也走本地筛；
 * - 时间：匿名腿 `scd/ecd`、登录腿 `start_date/end_date`，单边有效。
 *
 * 证据与读数全表在 [PixivSearchFilter] 的类注释里，这里只留结论。
 *
 * ## 标签模式为什么在框里而不在面板里
 *
 * 「部分 / 完全」回答的是"**这一句**怎么解释"，与"这一批要哪一些"不是一件事。
 * 它住进输入框左侧那枚小令牌之后，面板只剩"筛"，收起面板也不会丢掉这一项 ——
 * 而且它是**最常改**的那一枚，藏进面板等于每次都多两次点击。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GalleryPixivSearchArea(
    svm: GallerySearchViewModel,
    rvm: GalleryReverseViewModel,
    allowNsfw: Boolean,
    resultCount: Int?,
) {
    val tokens = VeneraTokens
    val focusRequester = remember { FocusRequester() }
    val reverseFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val expanded = svm.mode == GallerySearchMode.INPUT
    val cardShape = RoundedCornerShape(tokens.shape.extraLarge)

    // 标签模式那一枚的下拉开在**卡这一层**：它住在输入行里，而输入行在收条时整行不画 ——
    // 记在行里的话，展开/收起一次就归零（同 `GallerySearchArea` 里排行菜单那处踩过的坑）。
    var tagModeMenuOpen by remember { mutableStateOf(false) }
    // 两枚日期格各自要弹一次日历。null = 没开。
    var dateField by remember { mutableStateOf<PixivDateField?>(null) }

    // 展开 = 一步到键盘；收成一条 = 放掉焦点（理由与 booru 那张逐字相同）。
    // 反搜开着时整条让位：焦点该归反搜框。
    LaunchedEffect(expanded, rvm.open) {
        if (rvm.open) return@LaunchedEffect
        if (expanded) {
            delay(PIXIV_FOCUS_DELAY_MS)
            runCatching { focusRequester.requestFocus() }
        } else {
            focusManager.clearFocus()
        }
    }
    LaunchedEffect(rvm.open) {
        if (!rvm.open) return@LaunchedEffect
        delay(PIXIV_FOCUS_DELAY_MS)
        runCatching { reverseFocusRequester.requestFocus() }
    }

    dateField?.let { field ->
        PixivDateDialog(
            field = field,
            initial = when (field) {
                PixivDateField.FROM -> svm.pixivFilter.from
                PixivDateField.TO -> svm.pixivFilter.to
            },
            onDismiss = { dateField = null },
            onPick = { picked ->
                dateField = null
                svm.setPixivFilter(
                    when (field) {
                        PixivDateField.FROM -> svm.pixivFilter.copy(from = picked)
                        PixivDateField.TO -> svm.pixivFilter.copy(to = picked)
                    },
                )
            },
            onClear = {
                dateField = null
                svm.setPixivFilter(
                    when (field) {
                        PixivDateField.FROM -> svm.pixivFilter.copy(from = null)
                        PixivDateField.TO -> svm.pixivFilter.copy(to = null)
                    },
                )
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = tokens.spacing.screenHorizontal,
                end = tokens.spacing.screenHorizontal,
                top = tokens.spacing.space2,
                bottom = tokens.spacing.space3,
            ),
    ) {
        Surface(
            shape = cardShape,
            color = if (veneraGlassEnabled()) {
                androidx.compose.ui.graphics.Color.Transparent
            } else {
                tokens.color.surfaceContainerHigh
            },
            shadowElevation = veneraSurfaceShadowElevation(tokens.elevation.attached),
            modifier = Modifier
                .veneraGlassSurface(VeneraGlassRole.PANEL, tokens.shape.extraLarge)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = if (expanded) {
                            tokens.spacing.dockedSearchBarHeight
                        } else {
                            tokens.spacing.listRowMinHeight
                        },
                    ),
                verticalArrangement = Arrangement.Center,
            ) {
                // ── 输入行（与 booru 那张同一具身体：定高胶囊 + 竖分隔 + 尾随两枚）──
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
                                .clip(RoundedCornerShape(percent = 50))
                                .background(tokens.color.surfaceVariant)
                                // 定高而不是 `heightIn`：这一行里有一根 `fillMaxHeight()` 的竖分隔，
                                // 只给下限时它会吃满父级剩余高度（booru 那张为这事写过一整段注释）。
                                .height(tokens.spacing.dockedSearchBarHeight)
                                .padding(horizontal = tokens.spacing.space6),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                        ) {
                            Crossfade(
                                targetState = rvm.open,
                                animationSpec = tween(tokens.motion.medium),
                                label = "pixivLeadIcon",
                            ) { reverse ->
                                Icon(
                                    imageVector = if (reverse) Icons.Outlined.ImageSearch else Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = if (reverse) tokens.color.primary else tokens.color.onSurfaceVariant,
                                    modifier = Modifier.size(tokens.spacing.chipIconSize),
                                )
                            }

                            // 标签模式令牌：**只在标签态出现**（反搜态这一框装的是链接）。
                            if (!rvm.open) {
                                PixivTagModeToken(
                                    mode = svm.pixivFilter.tagMode,
                                    menuOpen = tagModeMenuOpen,
                                    onMenuOpen = { tagModeMenuOpen = it },
                                    onPick = { mode ->
                                        tagModeMenuOpen = false
                                        svm.setPixivFilter(svm.pixivFilter.copy(tagMode = mode))
                                    },
                                )
                            }

                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (rvm.open) {
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
                                            capitalization = KeyboardCapitalization.None,
                                            imeAction = ImeAction.Search,
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onSearch = { rvm.submit(allowNsfw) },
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(reverseFocusRequester),
                                    )
                                } else {
                                    if (svm.pixivTerm.isEmpty()) {
                                        Text(
                                            // 占位不写站名：域就在首页那一排上摆着（与 booru 那张同一条）。
                                            text = "标签 / 画师 / 作品",
                                            fontSize = tokens.type.body,
                                            color = tokens.color.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    BasicTextField(
                                        value = svm.pixivTerm,
                                        onValueChange = { svm.setPixivTerm(it) },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontSize = tokens.type.body,
                                            color = tokens.color.textPrimary,
                                        ),
                                        cursorBrush = SolidColor(tokens.color.primary),
                                        keyboardOptions = KeyboardOptions(
                                            // 标签是小写串，自动首字母大写会凭空造出搜不到的词。
                                            capitalization = KeyboardCapitalization.None,
                                            imeAction = ImeAction.Search,
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onSearch = { svm.submitPixivTerm() },
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester)
                                            .onFocusChanged {
                                                if (it.isFocused) svm.setPixivPanelOpen(false)
                                            },
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(tokens.spacing.hairline)
                                    .background(tokens.color.outlineVariant),
                            )

                            Crossfade(
                                targetState = rvm.open,
                                animationSpec = tween(tokens.motion.medium),
                                label = "pixivTrailing",
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
                                        if (svm.pixivTerm.isNotEmpty()) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = "清空输入",
                                                tint = tokens.color.onSurfaceVariant,
                                                modifier = Modifier
                                                    .size(tokens.spacing.chipIconSize)
                                                    .clickable { svm.setPixivTerm("") },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (rvm.open) {
                    GalleryReverseSearchArea(rvm = rvm, allowNsfw = allowNsfw)
                } else if (expanded) {
                    // ── 页签行：作品 / 画师（2026-10-18）──
                    // pixez 的搜索结果页就是一颗两页签的 TabBar（`result_page.dart`），
                    // 用户问「搜索作者（画师）的分栏怎么没做」问的就是这一排。
                    PixivKindRow(
                        kind = svm.pixivKind,
                        onPick = { svm.setPixivKind(it) },
                    )

                    // 条件被拦住时那句话（没登录却挑了人气 / 收藏）：就摆在页签行下面。
                    // 等到按了搜索才在页尾说，用户早就以为条件生效了。
                    svm.notice?.let { message ->
                        Box(
                            modifier = Modifier.padding(
                                start = tokens.spacing.space8,
                                end = tokens.spacing.space8,
                                bottom = tokens.spacing.space4,
                            ),
                        ) {
                            SearchNoticeLine(message)
                        }
                    }

                    if (svm.pixivKind == PixivSearchKind.ARTIST) {
                        // 画师那一栏**没有排序也没有筛选**（那五段全都只作用于作品）：
                        // 摆一排点了不生效的开关比不摆更糟。这一格留给历史 / 提示。
                        PixivHistoryBlock(svm = svm)
                    } else {
                    // ── 排序行：常驻。它是"这批图按什么排的"的唯一落点，所以不进面板。 ──
                    PixivSortRow(
                        order = svm.pixivFilter.order,
                        filterCount = svm.pixivFilter.activeCount,
                        panelOpen = svm.pixivPanelOpen,
                        onPickOrder = { order ->
                            // 「人气」点不点得动**只由 ViewModel 判**（要票据，也要会员）：
                            // 拒的时候它连条件都不改，UI 不另抄一份判据 —— 两处各判一次必然漂。
                            svm.setPixivFilter(svm.pixivFilter.copy(order = order))
                        },
                        onTogglePanel = {
                            // 只开合，**不动键盘**：四段面板约 250dp，键盘之上放得下，
                            // 抢走键盘反而会把"正想改的那个词"挡住（见文件头那段读数与改判理由）。
                            svm.setPixivPanelOpen(!svm.pixivPanelOpen)
                        },
                    )

                    if (svm.pixivPanelOpen) {
                        PixivFilterPanel(
                            filter = svm.pixivFilter,
                            onPickBookmark = { mark ->
                                // 收藏**不再要求登录**（2026-10-18 改判）：非会员走词尾 `users入り`
                                // （那一档匿名实测真生效 —— 同词「猫」不带它 60 条里只有 2 条有收藏数标签，
                                // 带上之后 58 条里 47 条是 `100users入り`），会员走 `bookmark_num_min`。
                                // 从前它只发后者，而后者在站方是付费档 ⇒ 非会员点了被静默忽略 = "没生效"。
                                svm.setPixivFilter(svm.pixivFilter.copy(bookmarks = mark))
                            },
                            onPickExcludeAi = { on ->
                                svm.setPixivFilter(svm.pixivFilter.copy(excludeAi = on))
                            },
                            onPickWorkType = { svm.setPixivFilter(svm.pixivFilter.copy(workType = it)) },
                            onPickRatio = { svm.setPixivFilter(svm.pixivFilter.copy(ratio = it)) },
                            onPickFrom = { dateField = PixivDateField.FROM },
                            onPickTo = { dateField = PixivDateField.TO },
                            onPickPreset = { days ->
                                val today = LocalDate.now()
                                svm.setPixivFilter(
                                    if (days == null) {
                                        svm.pixivFilter.copy(from = null, to = null)
                                    } else {
                                        svm.pixivFilter.copy(
                                            from = today.minusDays((days - 1).toLong()),
                                            to = today,
                                        )
                                    },
                                )
                            },
                            onReset = { svm.resetPixivFilter() },
                        )
                    } else {
                        // 面板收起时那一块是**历史 / 空态提示**：pixiv 这一跳没有标签补全
                        // （站方的补全端点没接），所以这一格只放"最近搜过什么"。
                        PixivHistoryBlock(svm = svm)
                    }
                    }
                } else {
                    PixivResultStrip(
                        svm = svm,
                        resultCount = resultCount,
                        onOpenInput = { svm.backToInput() },
                    )
                }
            }
        }
    }
}

/** 日期弹层正在改哪一格。 */
private enum class PixivDateField { FROM, TO }

/**
 * 输入框左侧那枚「部分 / 完全」。
 *
 * 两档互斥、而且是最常改的一枚 —— 所以它不做成面板里的一行，而是**框里的一枚令牌**：
 * 点它弹两行菜单（不是就地翻转）。就地翻转少一次点击，但"部分"这两个字本身读不出
 * "它还能是别的"，一枚没有箭头的字在框里长得像标签而不是控件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PixivTagModeToken(
    mode: PixivTagMode,
    menuOpen: Boolean,
    onMenuOpen: (Boolean) -> Unit,
    onPick: (PixivTagMode) -> Unit,
) {
    val tokens = VeneraTokens
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(tokens.color.surfaceContainerHigh)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onMenuOpen(true) }
                .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
        ) {
            Text(
                text = mode.label,
                fontSize = tokens.type.overline,
                color = tokens.color.onSurfaceVariant,
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = "标签匹配方式",
                tint = tokens.color.onSurfaceVariant,
                modifier = Modifier.size(tokens.spacing.chipIconSize),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpen(false) }) {
            PixivTagMode.entries.forEach { candidate ->
                DropdownMenuItem(
                    text = {
                        Text(
                            // 菜单里把两档的区别说全：框里那两个字放不下"标签"两个字，
                            // 而"部分 / 完全"单看读不出是"标签的哪一部分"。
                            text = "标签${candidate.label}",
                            fontSize = tokens.type.body,
                            color = if (candidate == mode) {
                                tokens.color.primary
                            } else {
                                tokens.color.textPrimary
                            },
                        )
                    },
                    onClick = { onPick(candidate) },
                )
            }
        }
    }
}

/**
 * 排序行 + 「筛选」入口，一行。
 *
 * 排序**三档全摆**（人气 / 旧序 / 最新）。人气那一档只有带票据的 app-api 排得动，
 * 没登录时点它不改条件、只把话说上屏 —— 由调用点判，理由与读数见 [PixivSearchOrder]。
 * 「筛选」钮上那枚数字是**面板里真的动过的段数**：排序与标签模式都在屏上看得见，
 * 不该混进这个数里（混进去之后「2」到底是哪两件事，用户得逐段点开找）。
 */
@Composable
private fun PixivSortRow(
    order: PixivSearchOrder,
    filterCount: Int,
    panelOpen: Boolean,
    onPickOrder: (PixivSearchOrder) -> Unit,
    onTogglePanel: () -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = tokens.spacing.space8,
                end = tokens.spacing.space8,
                bottom = tokens.spacing.space4,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        Text(
            text = "排序",
            fontSize = tokens.type.caption,
            color = tokens.color.textTertiary,
            maxLines = 1,
        )
        // 三档按"旧 → 新"排：稿上也是这个方向，用户读的是时间轴；人气落在最左，
        // 与它"只有登录才有"的身份分开 —— 它不是一个时刻。
        PixivSearchOrder.entries.reversed().forEach { candidate ->
            VeneraChip(
                text = candidate.label,
                selected = candidate == order,
                variant = VeneraChipVariant.Filter,
                onClick = { onPickOrder(candidate) },
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    if (panelOpen || filterCount > 0) {
                        tokens.color.primaryContainer
                    } else {
                        tokens.color.surfaceVariant
                    },
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onTogglePanel() }
                .padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = if (panelOpen || filterCount > 0) {
                    tokens.color.onPrimaryContainer
                } else {
                    tokens.color.onSurfaceVariant
                },
                modifier = Modifier.size(tokens.spacing.chipIconSize),
            )
            Text(
                text = if (filterCount > 0) "筛选 $filterCount" else "筛选",
                fontSize = tokens.type.caption,
                color = if (panelOpen || filterCount > 0) {
                    tokens.color.onPrimaryContainer
                } else {
                    tokens.color.onSurfaceVariant
                },
                maxLines = 1,
            )
            Icon(
                imageVector = if (panelOpen) {
                    Icons.Outlined.KeyboardArrowUp
                } else {
                    Icons.Outlined.KeyboardArrowDown
                },
                contentDescription = if (panelOpen) "收起筛选" else "展开筛选",
                tint = if (panelOpen || filterCount > 0) {
                    tokens.color.onPrimaryContainer
                } else {
                    tokens.color.onSurfaceVariant
                },
                modifier = Modifier.size(tokens.spacing.chipIconSize),
            )
        }
    }
}

/**
 * 卡内面板：**收藏** + **作品** + **比例** + **发布时间** + 重置。
 *
 * 两列排法：左边一枚 [VeneraTokens.spacing.iconButtonSize] 宽的标签列，右边是控件。
 * 竖排（标签在上、控件在下）看着更松，但每一段都要多花一行 —— 而这一屏的高度是按 10dp 算的。
 *
 * ## 四段各自落在哪条腿上（2026-10-17 深夜补齐）
 *
 * | 段 | 真发得出去的条件 |
 * |---|---|
 * | 收藏 | 会员 → 登录腿 `bookmark_num_min`；其余 → **词尾 `users入り`**（匿名实测真生效） |
 * | 排除 AI | 匿名腿 `ai_type=1` / 登录腿 `search_ai_type=1`（两条腿都真生效） |
 * | 作品 | **两条腿都只能本地筛**（`illustType==2` / `type=="ugoira"`） |
 * | 比例 | 匿名腿发 `ratio`，登录腿没有这一枚 ⇒ 登录时也走本地筛 |
 * | 时间 | 匿名腿 `scd/ecd`，登录腿 `start_date/end_date`（单边有效） |
 *
 * 收藏那一段**不再要求登录**（2026-10-18 改判）：非会员走词尾标签那一档、会员走真参数，两种发法都真筛得动。
 * 只剩「人气」还挂会员门，而那句拒词由 `setPixivFilter` 一处说 —— UI 不各自判一次，两处必漂一处。
 *
 * 「重置全部」只重置**面板里**那五段：排序与标签模式都在屏上摆着，把它们一起重置，
 * 用户会以为自己刚选的那一档排序也坏了（而它明明还在）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PixivFilterPanel(
    filter: PixivSearchFilter,
    onPickBookmark: (PixivBookmark) -> Unit,
    onPickExcludeAi: (Boolean) -> Unit,
    onPickWorkType: (PixivWorkType) -> Unit,
    onPickRatio: (PixivRatio) -> Unit,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onPickPreset: (Int?) -> Unit,
    onReset: () -> Unit,
) {
    val tokens = VeneraTokens
    // 两枚日期格**各读各的**（不读归一之后的那一对）：只填了起始时，结束那一格必须还是空的 ——
    // 把起始抄过去，屏上就写着"这是一个区间"，而请求里发的是单边。
    val bounds = filter.bounds()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = tokens.spacing.space8,
                end = tokens.spacing.space8,
                bottom = tokens.spacing.space6,
            ),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        // 面板与上面那两行之间一根线：面板是"另一块"，不加线时它读起来像排序行的续行。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(tokens.spacing.hairline)
                .background(tokens.color.outlineVariant),
        )

        // 收藏门槛：七档在 411dp 宽的机器上会折成两行（约 299dp 的控件位放不下七枚）。
        PixivPanelRow(label = "收藏") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            ) {
                PixivBookmark.entries.forEach { candidate ->
                    VeneraChip(
                        text = candidate.label,
                        selected = candidate == filter.bookmarks,
                        variant = VeneraChipVariant.Filter,
                        onClick = { onPickBookmark(candidate) },
                    )
                }
            }
        }

        // 站方的硬过滤档：www 那一条是 `ai_type=1`、app-api 是 `search_ai_type=1`。
        // 2026-10-18 现量（词=`猫`）：`ai_type=0` 回 21 条 `aiType=2`，`ai_type=1` 回 **0 条** ——
        // 这一枚**匿名也发得出去**，所以不挂登录门。
        PixivPanelRow(label = "AI") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            ) {
                VeneraChip(
                    text = "全部",
                    selected = !filter.excludeAi,
                    variant = VeneraChipVariant.Filter,
                    onClick = { onPickExcludeAi(false) },
                )
                VeneraChip(
                    text = "排除 AI",
                    selected = filter.excludeAi,
                    variant = VeneraChipVariant.Filter,
                    onClick = { onPickExcludeAi(true) },
                )
            }
        }

        PixivPanelRow(label = "作品") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            ) {
                PixivWorkType.entries.forEach { candidate ->
                    VeneraChip(
                        text = candidate.label,
                        selected = candidate == filter.workType,
                        variant = VeneraChipVariant.Filter,
                        onClick = { onPickWorkType(candidate) },
                    )
                }
            }
        }

        PixivPanelRow(label = "比例") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            ) {
                PixivRatio.entries.forEach { candidate ->
                    VeneraChip(
                        text = candidate.label,
                        selected = candidate == filter.ratio,
                        variant = VeneraChipVariant.Filter,
                        onClick = { onPickRatio(candidate) },
                    )
                }
            }
        }

        PixivPanelRow(label = "时间") {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.space3)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                ) {
                    PixivDateBox(
                        date = bounds?.first,
                        placeholder = "起始",
                        onClick = onPickFrom,
                    )
                    Text(
                        text = "至",
                        fontSize = tokens.type.caption,
                        color = tokens.color.textTertiary,
                        maxLines = 1,
                    )
                    PixivDateBox(
                        date = bounds?.second,
                        placeholder = "结束",
                        onClick = onPickTo,
                    )
                }
                // 四个快捷：覆盖九成用法，精确区间交给上面那两格。
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                ) {
                    PixivSearchFilter.DATE_PRESETS.forEach { days ->
                        VeneraChip(
                            text = pixivPresetLabel(days),
                            selected = pixivPresetMatches(days, bounds),
                            variant = VeneraChipVariant.Filter,
                            onClick = { onPickPreset(days) },
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = "重置全部",
                fontSize = tokens.type.caption,
                color = if (filter.isDefault) {
                    tokens.color.textTertiary
                } else {
                    tokens.color.primary
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(tokens.shape.small))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        enabled = !filter.isDefault,
                    ) { onReset() }
                    .padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space3),
            )
        }
    }
}

/** 面板里的一行：左边定宽标签，右边控件。 */
@Composable
private fun PixivPanelRow(label: String, content: @Composable () -> Unit) {
    val tokens = VeneraTokens
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = tokens.type.caption,
            color = tokens.color.textTertiary,
            maxLines = 1,
            modifier = Modifier
                .width(tokens.spacing.iconButtonSize)
                // 标签与第一行控件同一条基线：不加这一截，两档字号会差半个字高。
                .padding(top = tokens.spacing.space2),
        )
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

/** 一枚日期格。空着的时候写占位（"起始 / 结束"），不写今天 —— 写今天会被读成"已经筛过了"。 */
@Composable
private fun PixivDateBox(date: LocalDate?, placeholder: String, onClick: () -> Unit) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.shape.small))
            .background(tokens.color.surfaceVariant)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        Icon(
            imageVector = Icons.Outlined.DateRange,
            contentDescription = null,
            tint = tokens.color.onSurfaceVariant,
            modifier = Modifier.size(tokens.spacing.chipIconSize),
        )
        Text(
            text = date?.toString() ?: placeholder,
            fontSize = tokens.type.caption,
            color = if (date == null) tokens.color.textTertiary else tokens.color.textPrimary,
            maxLines = 1,
        )
    }
}

/** 快捷档的名字。`null` = 不限。 */
private fun pixivPresetLabel(days: Int?): String = when (days) {
    null -> "不限"
    7 -> "近 7 天"
    30 -> "近 30 天"
    else -> "近 1 年"
}

/**
 * 快捷档是不是"当前就是它"。
 *
 * 判据是**日期真的对得上**，不是"上一次点了哪个快捷"：用户点完「近 30 天」再手动改一枚日期，
 * 那一枚就该灭掉 —— 否则屏上同时说着"近 30 天"和一个别的区间。
 */
private fun pixivPresetMatches(days: Int?, bounds: Pair<LocalDate?, LocalDate?>?): Boolean {
    if (days == null) return bounds == null
    if (bounds == null) return false
    val today = LocalDate.now()
    val expectedFrom = today.minusDays((days - 1).toLong())
    return bounds.first == expectedFrom && bounds.second == today
}

/**
 * 面板收起时那一块：最近搜过什么。
 *
 * ⚠️ **没有补全**：pixiv 的标签补全端点没接（booru 那侧走的是 `searchTags`，pixiv 不吃）。
 * 所以这一格空着的时候不画"没有以它开头的标签"那种话 —— 我们压根没问过站方，
 * 说了就是把"我们没问"念成"站里没有"。
 */
@Composable
private fun PixivHistoryBlock(svm: GallerySearchViewModel) {
    val tokens = VeneraTokens
    val history = svm.visibleHistory
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = tokens.spacing.space8,
                end = tokens.spacing.space8,
                bottom = tokens.spacing.space4,
            ),
    ) {
        if (history.isEmpty()) {
            Text(
                // 空历史时给一句"这里能做什么"，而不是留一块白 —— 面板收起后这一格是卡的底边。
                text = "打一个词就搜。筛选里能按收藏、作品、比例与发布时间收窄。",
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
            )
            return@Column
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                    ) { svm.clearHistory() }
                    .padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space4),
            )
        }
        Spacer(modifier = Modifier.height(tokens.spacing.space1))
        // 复用 booru 那侧的历史列表（双列紧凑网格 + 两步删除）。它吃的是 entry 的 `query`，
        // pixiv 那几条的 `query` 就是那个词。
        //
        // ⚠️ 站标**不画**（用户 2026-10-17：「p 站搜索历史不需要显示站点」）：这一格里的每一条
        // 都属于 pixiv，摆一枚 "P" 是在回答一个没人问过的问题，还把那个词挤窄一截。
        HistoryList(
            history = history,
            onPick = { svm.applyHistory(it) },
            onRemove = { svm.removeHistory(it) },
            showSourceMark = false,
        )
    }
}

/**
 * 搜索卡里那一排**页签**：作品 / 画师（2026-10-18）。
 *
 * 出处是 pixez 搜索结果页那颗 `TabBar`（`result_page.dart` 的 `tabs: [illust, painter]`）。
 * 做成两枚 chip 而不是 miuix 的分段控件：这一排只有两项、又坐在卡里，
 * 分段控件那层外框会把卡面切成两条（同 `GallerySearchArea` 里来源那一排的取舍，
 * 那边用它是因为三档 + 它是"换数据源"；这里换的是"看什么"，两枚 chip 更轻）。
 *
 * 它**常驻**在展开态（不随面板开合消失）：换页签是"我要看另一种东西"，
 * 比排序更外层 —— 藏进面板的话，切一次要两步。
 */
@Composable
private fun PixivKindRow(kind: PixivSearchKind, onPick: (PixivSearchKind) -> Unit) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = tokens.spacing.space8,
                end = tokens.spacing.space8,
                bottom = tokens.spacing.space4,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        PixivSearchKind.entries.forEach { candidate ->
            VeneraChip(
                text = candidate.label,
                selected = candidate == kind,
                variant = VeneraChipVariant.Filter,
                onClick = { onPick(candidate) },
            )
        }
    }
}

/**
 * 画师那一栏的结果名单（`/v1/search/user`）。
 *
 * ## 为什么是名单而不是瀑布流
 *
 * 这一栏摆的是**人**：没有封面、没有长宽比，瀑布流那套"按图高排"在这里没有意义。
 * 一列 60dp 的行读起来是"一页通讯录"，与首页那排画师头像同一种语言（圆头像 + 名字）。
 *
 * ## 点一位画师 = 打开站方页面
 *
 * 本仓没有 pixiv 画师主页那一屏（`GalleryArtistProfileScreen` 走的是 booru 的查询语言），
 * 所以走 [openPixivUserPage]：Custom Tab 打开 `pixiv.net/users/{id}`。
 * 那是**唯一诚实的动作** —— 按下去真能到那个人的全部作品，而不是停下来说"还没做"。
 *
 * @param loading 这一栏正在取（第一页或续页）。
 * @param error 取失败那句原话；非空时页脚给"重试"。
 * @param exhausted 站方说没有下一页了（页脚写总数，不再自动续）。
 */
@Composable
internal fun GalleryPixivArtistResults(
    users: List<PixivUser>,
    imageLoader: ImageLoader,
    loading: Boolean,
    error: String?,
    exhausted: Boolean,
    onOpen: (PixivUser) -> Unit,
    onLoadMore: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
    ) {
        items(users, key = { it.id }) { user ->
            PixivArtistRow(user = user, imageLoader = imageLoader, onOpen = onOpen)
        }
        item(key = "pixiv-artist-footer") {
            // 续页与作品那面墙同一个语义：滚到页脚就取下一页。但这里**只在这一栏活着时**成立
            // —— 页脚项一进组合就触发，而"进组合"本身就等于"用户滚到了底"。
            if (users.isNotEmpty() && !exhausted && error == null && !loading) {
                LaunchedEffect(users.size) { onLoadMore() }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = tokens.spacing.space5),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    error != null -> Text(
                        text = error,
                        fontSize = tokens.type.caption,
                        color = tokens.color.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    loading -> Text(
                        text = "正在取下一页",
                        fontSize = tokens.type.caption,
                        color = tokens.color.textTertiary,
                    )
                    exhausted && users.isNotEmpty() -> Text(
                        // 站方不报总数（`next_url` 只回答"还有没有"），所以只报**已摆出几位**。
                        text = "共 ${users.size} 位",
                        fontSize = tokens.type.overline,
                        color = tokens.color.textTertiary,
                    )
                }
            }
        }
    }
}

/** 名单里的一行：圆头像 + 名字 + `@账号`。 */
@Composable
private fun PixivArtistRow(
    user: PixivUser,
    imageLoader: ImageLoader,
    onOpen: (PixivUser) -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.shape.large))
            .background(tokens.color.surfaceContainerHigh)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = VeneraPressFeedback.indication,
            ) { onOpen(user) }
            .padding(horizontal = tokens.spacing.space6, vertical = tokens.spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
    ) {
        Box(
            modifier = Modifier
                .size(tokens.spacing.listRowMinHeight)
                .veneraAvatarRing(CircleShape)
                .background(tokens.color.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (user.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = user.name,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // 站方没给头像 ⇒ 首字母座（同 `PixivUser.avatarUrl` 那条注：不拿别人的头像顶）。
                Text(
                    text = user.name.take(1),
                    fontSize = tokens.type.itemTitle,
                    color = tokens.color.textSecondary,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                fontSize = tokens.type.body,
                lineHeight = TEXT_LINE_HEIGHT.em,
                color = tokens.color.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.account.isNotBlank()) {
                Text(
                    text = "@${user.account}",
                    fontSize = tokens.type.caption,
                    lineHeight = TEXT_LINE_HEIGHT.em,
                    color = tokens.color.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = tokens.spacing.space1),
                )
            }
        }
    }
}

/**
 * 收成一条时那一行：词 + 生效条件 + 张数。
 *
 * 这一行的存在理由与 booru 那张的胶囊行同一条 —— 往下看图时它是**唯一还留在屏上**的
 * "我在搜什么"。只是 pixiv 这一侧的词不是胶囊，所以词单独画一枚，条件跟在后面。
 */
@Composable
private fun PixivResultStrip(
    svm: GallerySearchViewModel,
    resultCount: Int?,
    onOpenInput: () -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = tokens.spacing.space8,
                end = tokens.spacing.space8,
                top = tokens.spacing.space2,
                bottom = tokens.spacing.space2,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VeneraChip(
                text = svm.pixivTerm.ifBlank { "pixiv" },
                selected = true,
                variant = VeneraChipVariant.Filter,
                onClick = onOpenInput,
            )
            // 画师那一栏没有条件可报（那五段只作用于作品），只把"这一栏是什么"说出来 ——
            // 否则收成一条之后屏上只剩一个词，用户分不出下面那些人名是作品还是画师。
            if (svm.pixivKind == PixivSearchKind.ARTIST) {
                VeneraChip(
                    text = PixivSearchKind.ARTIST.label,
                    variant = VeneraChipVariant.Tonal,
                    onClick = onOpenInput,
                )
            } else {
                // 条件摘要去掉排序那一枚：收成一条时"按什么排的"已经由结果本身说明了，
                // 而这一行每多一枚都在跟词抢宽度。
                svm.pixivFilter.summary().drop(1).forEach { line ->
                    VeneraChip(
                        text = line,
                        variant = VeneraChipVariant.Tonal,
                        onClick = onOpenInput,
                    )
                }
            }
        }
        if (resultCount != null) {
            Spacer(modifier = Modifier.width(tokens.spacing.space6))
            Text(
                // 单位跟着页签走：画师那一栏摆的是人，报"张"就是把人数说成张数。
                text = if (svm.pixivKind == PixivSearchKind.ARTIST) "$resultCount 位" else "$resultCount 张",
                fontSize = tokens.type.caption,
                color = tokens.color.textSecondary,
                maxLines = 1,
            )
        }
    }
}

/**
 * 一枚日期格弹出来的日历。
 *
 * 用的是 M3 的 [DatePicker]（miuix 没有日历面，见 `GallerySearchArea` 头注里那份"仍保留的
 * M3 直连"清单）。`initialSelectedDateMillis` 按 **UTC 零点**换：这一格只表达"哪一天"，
 * 换算里带上时区就会在跨零点时差一天。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PixivDateDialog(
    field: PixivDateField,
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
    onClear: () -> Unit,
) {
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: today).toEpochDay() * PIXIV_MILLIS_PER_DAY,
        yearRange = PIXIV_EARLIEST_DATE.year..today.year,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (field == PixivDateField.FROM) "从哪一天" else "到哪一天") },
        text = {
            DatePicker(state = state)
        },
        confirmButton = {
            TextButton(onClick = {
                val picked = state.selectedDateMillis
                    ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    ?: return@TextButton
                onPick(picked)
            }) { Text("就用这一天") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("不限") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

/** pixiv 站本身 2007 年才开站：日历从这里起，比"1900 年"少滚一百年。 */
private val PIXIV_EARLIEST_DATE: LocalDate = LocalDate.of(2007, 9, 1)

private const val PIXIV_MILLIS_PER_DAY = 86_400_000L

/** 与 booru 那张同一个值（`GallerySearchArea.FOCUS_REQUEST_DELAY_MS`）：等节点 attach 完成。 */
private const val PIXIV_FOCUS_DELAY_MS = 60L
