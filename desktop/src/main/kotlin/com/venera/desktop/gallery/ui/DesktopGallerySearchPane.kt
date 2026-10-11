package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryLegGuard
import com.venera.compose.gallery.domain.GalleryLegOutcome
import com.venera.compose.gallery.domain.GallerySearch
import com.venera.compose.gallery.domain.GallerySearchEntry
import com.venera.compose.gallery.domain.GallerySearchMerge
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.gallery.domain.GalleryTagFilter
import com.venera.compose.gallery.domain.GalleryDailyFeed
import com.venera.compose.gallery.domain.GalleryForYouMerge
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 画廊搜索域：标签串 → 三站（或单站）并发搜 → [GallerySearchMerge.interleave] 合成一面墙。
 *
 * ## 取数链**一步都不新造**，逐颗点名
 *
 * | 这一屏做的事 | 用的是哪一颗 |
 * |---|---|
 * | 发车前算哪些腿能发 | `GallerySearchSource.availableLegs` / `missingLegs` |
 * | 每条腿一笔超时 + 原因不吞 | `GalleryLegGuard.guard`（与日榜同一份） |
 * | 合成一面墙（保序 + 跨站去重） | `GallerySearchMerge.interleave` |
 * | 哪些站到底了 | `GallerySearchMerge.exhaustedSites` + `GallerySearch.isExhausted` |
 * | 续页时每条腿各自要第几页 | `nextPagesForAppend` |
 * | 每站能写几枚标签 | `GallerySearch.tagBudget` / `fitsTagBudget` |
 * | 历史读一次 / 落一条 / 删一条 / 整清 | `decodeGallerySearchHistory` / `pushGallerySearchHistory` / 那颗键值件（桌面这一层在 `DesktopSearchHistory.kt`，**一颗判据都没重述**） |
 *
 * ⚠️ 这张表是本屏的**全部**理由。搜索的坑几乎全在"三条腿的异步结果怎么拼"上 ——
 * 而那些坑 Android 侧已经踩过一遍并把判据提到了 `gallery/domain`（整颗目录在桌面编译面内）。
 * 桌面再写一份 `async { … }.await()` 的手抄拼装，等于把"两腿都空要不要炸""缺席腿算不算到底"
 * 这两条判据复制一份，而它们一旦分叉，用户看到的是"同一串标签在两端搜出不同数量的图"。
 *
 * ## 三条不能省的读数
 *
 * 1. **缺席的腿要逐站点名 + 原因**（`GalleryLegGuard.outcomeOf` 的 `reason`），
 *    混成"部分站点无结果"就是本仓最忌的静默降级。
 * 2. **没配 Gelbooru 账号时它**根本不算缺席，而是"发不出去"** ——
 *    `GallerySearchSource.missingLegs` 那句 `NEEDS_ACCOUNT_REASON` 直接上屏。
 *    这两件事在屏上必须长得不一样：一个是站方没给数，一个是我们压根没发车。
 * 3. **标签预算在发车前拦住**（`fitsTagBudget`）。Safebooru 匿名只吃 2 枚，超了站方直接 422 ——
 *    让它 422 再把那句话翻译成"搜索失败"就是把站方的读数念错了。
 *
 * ## 桌面这一屏**没有**的东西，逐条说清为什么（不留假开关）
 *
 * - **标签补全（`searchTags`）**（第十四批 批 11 接上复检与读数）：候选排在输入框**下方那一行**，
 *   打字时它自己换（≥2 个字符才发车，每敲一键重跑一笔、上一笔被 effect 作废 —— 与对岸 `suggestJob?.cancel()` 同一形状），
 *   点一枚候选 = 把词接进输入串。**没有**的是弹层式下拉：桌面那一排是内联的，不盖任何东西。
 *   候选拿回来一律走共享面那颗 `refineGalleryTagSuggestions` 复检（站方会**静默**忽略前缀参数），
 *   而"这一站没答上 / 站里真没有这个词 / 根本没发车"在屏上是三句不同的话。
 * - **翻页**（第十三批 批 8 接上了）：墙下面那枚「下一页」把**这一轮发过的腿**各往前取一页，
 *   追加而不是替换，跨页去重吃上一笔攒下的键。三件事仍然**没有**，逐条说清：
 *   滚到页尾**不自动续页**（桌面没有那一套滚动位置状态机，要点那枚钮）；
 *   没有"跳到第 N 页"（站方给的是游标式分页，跳页要重取中间那些页）；
 *   到底的那几站不再问，所以「下一页」会自己消失而不是变成按不动的灰钮。
 * - **历史**（第十三批 批 9 接上了）：进这一屏读一次，**第 1 页取成功**那一笔写回一条，
 *   屏上「最近搜索」那一排三个动作各有真落点（点那一串字 = 按它再跑一遍、删掉这一条、整清）。
 *   一颗判据都没新写：encode / decode / push 三枚在共享面 `gallery/domain/GallerySearch.kt`，
 *   落盘走 `ports.stores.open(PreferenceKeys.PREFS_GALLERY_SEARCH)`，与手机那份是同一个文件。
 *   这一排**没有**的，逐条说清：不按站各记一条历史（同键并站是共享面的判据）、
 *   没有"跳回那一页"（历史只记串与腿，页码不在那一枚条目里）。
 *   ⚠️ 这与首页 pane 那一行「历史」（浏览过哪张卡）**不是一件事**，那一句缺席说明仍然成立。
 *   ⚠️ 「发现」那一屏**不往这里写** —— 它每轮换的标签串是机器生成的，灌进去会把用户自己敲的那几行挤没
 *   （对岸同一条禁令，判据在 `DesktopSearchHistoryTest` ⑧）。
 */
@Composable
internal fun DesktopGallerySearchPane(
    ports: GalleryPorts,
    seed: DesktopSearchSeed,
    /**
     * 取证种子 `--reverse-file=` 给的那一条本机路径（`null` = 本轮不自动发车）。
     *
     * **必填、不给默认值**：漏传的形状是"参数打进去了而那一格永远不出现"，它不会报错 ——
     * 与 `seed` 那一枚同一条理由（链条：集成方读参数 → 应用根 → 域 body → 这一屏 → 入口那一行）。
     */
    seedLocalReverse: String?,
) {
    val pad = DesktopGalleryMetrics.pageHorizontalPadding
    val metrics = DesktopGalleryMetrics

    // 来源轴：默认「全部」（用户 2026-09-30 在 Android 侧拍板过，桌面沿用同一口径）。
    val sources = remember { GallerySearchSource.options }
    var sourceIndex by rememberSaveable { mutableStateOf(sourceIndexOf(sources, seed.source)) }
    val source = sources[sourceIndex]

    // 查询初值与**后面每一次交接**都读同一颗种子（批 2 ⑤ 之前这里只有 `--query=` 一个读者）。
    // ⚠️ "没给"（null）与"给了空串"在**这一屏上落同一个初值** —— 都是空查询那一档，
    // 别假装空串能拍出不一样的屏；两档分开是为了 stdout 念的是哪句
    // （"不预填" vs "按空查询起"），判读的理由见 ForensicFlags.query 的注释。
    var query by rememberSaveable { mutableStateOf(seed.query) }
    var round by rememberSaveable { mutableStateOf(0) }
    // 批 5：`state` 的 inputs 与下面那枚取数 effect 的 key 同一组（ports / sourceIndex / round / query），
    // 于是"Ready"就等价于"为这一组输入取到手了"，那道跳过守卫才不会把该重取的一笔挡掉。
    var state: SearchState by rememberSaveable(ports, sourceIndex, round, query) {
        mutableStateOf<SearchState>(SearchState.Idle)
    }
    var suggestions by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    // 那一排候选旁边该补的那一句（批 11）：**没事可说是 null**，而不是"没有候选"。
    // 它是 transient（跟随当前输入），所以走 `remember` 而不是 saveable —— 换域换回来时词已经变了，
    // 把上一词的读数留着正是"屏上那句话不是真话"那一族。
    var suggestionNotice by remember { mutableStateOf<String?>(null) }
    // 候选那一排的中文译名（对岸 `GallerySearchArea.kt:1209` 同一档）：括号并列那一形，
    // 而**点候选接进查询那一步仍交原名** —— 把「风景 (landscape)」接进输入框就是替用户打了一串他没打过的标签。
    var suggestionTranslations by remember(suggestions) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(suggestions) {
        val names = suggestions
        suggestionTranslations = if (names.isEmpty()) emptyMap() else runCatching { ports.lexicon.translations(names) }.getOrDefault(emptyMap())
    }
    // 交接那颗 effect 的"这一颗种子我已经落过"记号（批 5）。没有它的话，换回来时 effect 重跑，
    // 用户在这屏手打的串会被弹回种子那一串 —— 从前那是"整屏重头"顺带遮住的，现在状态留下了，
    // 这一笔就得单独说清：**同一颗种子只落一次**，而 `token` 变（又点了一枚标签）才再落。
    var seedApplied by rememberSaveable { mutableStateOf(-1) }
    // 「下一页」在飞的那一笔（批 8）。它**不进** `state`：那一枚的档位是"这一屏整体上是什么状态"，
    // 而续页期间第一页的内容还在屏上、只是下面多了一行"正在取下一页" —— 换成 Loading 会把
    // 整面墙换成一根进度条，用户读到的成了"搜索又重跑了一遍"。
    var appending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 「用本机的一张图找」那一格的状态（批 77 下半）。两颗界面件共用这一颗 holder：
    // 入口跟着输入行，结果那一片长在它下面，而它们是**同一颗状态的两次读**。
    // 不换域留存 —— 那颗 holder 不是 Serializable，走 saveable 会在换域那一刻才抛（本屏那颗历史同形）。
    val localReverse = remember { DesktopLocalReverseState() }
    // 查询历史（批 9）。这一枚**故意不用 `rememberSaveable`**：那颗条目不是 Serializable，
    // 而 saveable 的默认 saver 是在"要存的那一刻"才试着序列化 —— 屏上用起来一切正常，
    // 换域那一刻才抛，正是本仓记过的"漏传不报错 / 到运行期才炸"那一族。
    // 盘上那份才是唯一真相，所以这里只 `remember` 一份内存表 + 进屏读一次。
    val historyStore = remember(ports) { desktopHistoryStore(ports) }
    var history by remember { mutableStateOf<List<GallerySearchEntry>>(emptyList()) }

    // 进这一屏读一次。与对岸有一处**实差**要说清：那边把表留在 ViewModel 里、靠 isNotEmpty 只读一次，
    // 这边换域回来会重读一遍盘（那颗条目不可 saveable，而盘上那份本来就是唯一真相）——
    // 代价是一次文件读，换来的是"另一台设备写的历史我也看得见"，不是漏接线。
    LaunchedEffect(historyStore) {
        history = withContext(Dispatchers.IO) { desktopHistoryRead(historyStore) }
    }

    // 交接落地（批 2 ⑤：点标签 / 点画师名）。两笔写**在同一笔 effect 里**，因为它们不许被拆开：
    // 下面那颗取数 effect 同时键着 `sourceIndex` 与 `query`，先写一个就会拿旧来源在新标签上发一笔
    // （Android 那边 `acceptHandoff` 同一句警告：切站 + 装条件 + 开搜是**一步**）。
    // key 是**整颗种子**而不是 `seed.query`：同一枚标签被点第二次时串没变、只有 `token` 变了，
    // 而那一笔必须重新落地 —— 否则"用户改了输入框，再去点同一枚标签"屏上不动。
    LaunchedEffect(seed) {
        if (seed.token == seedApplied) return@LaunchedEffect
        seedApplied = seed.token
        query = seed.query
        sourceIndex = sourceIndexOf(sources, seed.source)
    }

    // Gelbooru 配没配：既是"发不发车"的判据，也是 UI 形状的判据（没配时那一格要念出原因）。
    //
    // ⚠️ 这里**不许**写 `?: return`：接线没给桌面凭据件时那是一条静默的空屏 ——
    // 用户看到的是"这一域什么都没有"，而真相是"接线缺一件"。缺件要说出来。
    val credentials = ports.credentials as? DesktopGalleryCredentials
    if (credentials == null) {
        Text(
            "这一屏需要桌面凭据件（判断 Gelbooru 配没配），当前接线没给：${ports.credentials::class.java.simpleName}",
            color = DesktopTheme.AccentBeni,
            fontSize = 13.sp,
            modifier = Modifier.padding(pad),
        )
        return
    }
    val identityState by credentials.gelbooru.identity.collectAsState()
    val hasGelbooru = identityState != null

    val dispatched = remember(source, hasGelbooru) { source.availableLegs(hasGelbooru) }
    val missing = remember(source, hasGelbooru) { source.missingLegs(hasGelbooru) }
    val budgetNotice = remember(dispatched) {
        dispatched.firstNotNullOfOrNull { GallerySearch.budgetNotice(it) }
    }

    // 标签预算**在发车前**判：站方 422 是站方的读数，翻译成"搜索失败"就是把它念错了。
    val filters = remember(query) { GallerySearch.parseQuery(query) }
    val overBudget = remember(dispatched, filters.size) {
        dispatched.firstOrNull { !GallerySearch.fitsTagBudget(it, filters.size) }
    }

    // 一条腿的取数。三站并发 + 每站一笔超时，判据全在 GalleryLegGuard 里。
    LaunchedEffect(ports, sourceIndex, round, query) {
        // 批 5：换回这一屏时 effect 一定重跑，而这一组的结果已经在这颗 state 里了 ⇒ 别再发一笔。
        if (state is SearchState.Ready) return@LaunchedEffect
        val tags = GallerySearch.queryOf(GallerySearch.parseQuery(query))
        if (tags.isBlank()) {
            state = SearchState.Idle
            return@LaunchedEffect
        }
        if (dispatched.isEmpty()) {
            state = SearchState.Idle
            return@LaunchedEffect
        }
        if (overBudget != null) {
            state = SearchState.Blocked(
                GallerySearch.budgetNotice(overBudget) ?: "标签数超出这一站的预算",
            )
            return@LaunchedEffect
        }
        state = SearchState.Loading
        // ⚠️ `runCatching` 包的是**整段**（含 interleave）：`GallerySearchMerge.interleave` 自己在
        // 「三站都给货却滤完 0 条」时会 `getOrThrow`，那属于判据出错而不是某一站缺席 ——
        // 它必须冒到 [SearchState.Failed] 让这一屏变成错误页，而不是被腿级 catch 咽成空墙。
        // 而每一腿内部的失败由 `GalleryLegGuard.guard` 各自接走（它交回 `answered=false` + 原因），
        // 所以腿级失败永远不会走到这里 —— 屏上会看到的是"那一站没答上"而不是"搜索失败"。
        val landing = fetchSearchPage(
            ports = ports,
            base = DesktopGalleryBatch(seed = round.toLong(), queries = dispatched.associateWith { tags }),
        )
        state = landing.fold(
            onSuccess = { SearchState.Ready(it.batch, it.legs, it.fetchedAtMillis) },
            onFailure = { SearchState.Failed("${it::class.java.simpleName}: ${it.message?.take(200)}") },
        )
        // 只有**这一笔**（第 1 页那一次发车）落历史：续页那一笔落第二条就会让同一轮在那一排里占两格，
        // 而那排的作用是"认出我搜过哪个"。落的是 dispatched（真发出去的那几条腿）而不是来源档那几站 ——
        // 未配账号的那一站从没被搜过，记进去就是把我们自己的闸说成用户搜过。
        if (landing.isSuccess) {
            val entry = desktopSearchHistoryEntry(dispatched, query)
            history = withContext(Dispatchers.IO) { desktopHistoryPush(historyStore, history, entry) }
        }
    }

    // 标签补全：≥2 个字符才发车（1 个字符的词站方回的东西没有参考价值，白问一笔）。
    // ⚠️ 每敲一键都重跑这一颗 effect，而上一笔由 effect 自己作废 —— 对岸是 `suggestJob?.cancel()`，
    //   两端同一形状：**没有**防抖延迟，别把它写成"桌面做不到"（批 11 之前那句就是这么说的，是假话）。
    // ⚠️ 每一腿交回的是 `Result` 而不是列表。上一版写的是 `searchTags(term).getOrDefault(emptyList())`，
    //   吞掉失败之后"这一站没答上"与"这一站真没有这个词"在屏上就成了同一句沉默（批 11 修的就是这一格）。
    LaunchedEffect(ports, sourceIndex, query) {
        val term = query.trim()
        if (term.length < 2 || dispatched.isEmpty()) {
            suggestions = emptyList()
            suggestionNotice = null
        } else {
            val legs = withContext(Dispatchers.IO) {
                dispatched.map { site ->
                    async { site to boardOf(ports, site).searchTags(term) }
                }.map { it.await() }
            }
            // 复检走共享面那颗件（前缀按原始输入复检一次、空名丢掉、未废弃在前按张数降序）——
            // 站方静默忽略前缀参数时回的是 200 + 一个像样的数组，不自检就会把不相干的标签摆成"你要搜的"。
            val outcome = desktopMergeSuggestionLegs(term, legs, SUGGESTION_ROWS)
            suggestions = outcome.candidates.map { it.name }
            suggestionNotice = desktopSuggestionReading(term, outcome)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        // ① 段头：标题 + 来源轴（`GallerySearchSource.options`：全部 + 三站，顺序是 domain 定的）。
        Column(Modifier.padding(horizontal = pad)) {
            Row(
                Modifier.fillMaxWidth().height(metrics.pageHeaderHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.pageHeaderGap),
            ) {
                Text(
                    "搜索",
                    color = DesktopTheme.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                DesktopSearchSourceAxis(
                    sources = sources,
                    selectedIndex = sourceIndex,
                    // 没配账号的那一格**照样可点**，点了由下面那条缺席行念出原因 ——
                    // 画成灰格会让人以为"不可用"是这一站坏了，而不是"你没配"。
                    onSelect = { sourceIndex = sources.indexOfFirst { s -> s.label == it.label } },
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    // ⚠️ 第二个位置参数是 `enabled`、trailing lambda 形状 ——
                    // 与 `DesktopGelbooruCredentialPanel` 里那两处同形，别照 material 那套写。
                )
                AccentButton(
                    onClick = { round += 1 },
                    disabled = query.isBlank() || dispatched.isEmpty() || overBudget != null,
                ) { Text("搜索") }
            }

            // ②′ 「用本机的一张图找」那一格（批 77 下半）。位置照对岸：入口跟在搜索卡的输入行之后，
            // 而结果那一片长在它下面（对岸是把墙那一片换成结果，桌面这里两处同轴、同一颗状态）。
            DesktopGalleryLocalReverseEntryRow(
                state = localReverse,
                ports = ports,
                scope = scope,
                seedPath = seedLocalReverse,
            )
            DesktopGalleryLocalReverseResults(
                state = localReverse,
                ports = ports,
                scope = scope,
            )

            // ② 最近搜索那一排（批 9）。空表就整块不摆 —— 摆一枚"还没有历史"的占位是把缺席
            // 画成一个控件，而这一屏本来就没有那种形状的占位（与首页那三行「未实现」不是一件事）。
            if (history.isNotEmpty()) {
                DesktopHistoryRow(
                    history = history,
                    onPick = { entry ->
                        // 三步一起写：条件、档位、以及**另起一轮**。少了第三步时连点同一条历史
                        // 前两样都没变 ⇒ 取数那颗跳过守卫整笔挡掉，屏上就是"点了没反应"（对岸记过的死局）。
                        val landing = desktopHistoryLanding(entry)
                        query = landing.query
                        sourceIndex = sourceIndexOf(sources, landing.source)
                        round += 1
                    },
                    onRemove = { entry ->
                        scope.launch {
                            history = withContext(Dispatchers.IO) { desktopHistoryRemoveOne(historyStore, history, entry) }
                        }
                    },
                    onClearAll = {
                        scope.launch {
                            history = withContext(Dispatchers.IO) { desktopHistoryClearAll(historyStore) }
                        }
                    },
                )
            }

            // ③ 三档缺席，三档长得不一样（这是本屏最容易做坏的地方）。
            SearchNotices(
                state = state,
                source = source,
                dispatched = dispatched,
                missing = missing,
                budgetNotice = budgetNotice,
                filters = filters,
            )

            // ④ 标签补全：候选行 + 那一问的读数。点一枚候选 = 接进输入串，这是一条**真动作**（会改 query ⇒ 触发重搜）。
            // ⚠️ 读数与候选**各画各的**：上一版只在候选非空时画那一排，于是"补全失败"与"站里没有这个词"
            //   两种情况下屏上都什么都不出现 —— 而屏上沉默正是用户只能靠猜的那一档。
            if (query.isNotBlank()) {
                if (suggestions.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        suggestions.forEach { name ->
                            Text(
                                desktopTagDisplayLabel(name, suggestionTranslations),
                                modifier = Modifier
                                    .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(999.dp))
                                    // 点候选 = 把它当一枚**普通**标签接在后面（不是替换输入框的内容）。
                                    // ⚠️ 走 `GalleryTagFilter.of` 而不是直接 `+ name`：这一串是站方语法，
                                    // 候选名里带 `-` 的（少数站拿它当分隔）必须还原成排除项，
                                    // 直接拼会在下一次 parse 时被切成两枚 —— 屏幕上就变成"搜了个没打过的标签"。
                                    .clickable {
                                        GalleryTagFilter.of(name)?.let { tag ->
                                            query = (filters + tag).joinToString(" ") { it.token }
                                        }
                                    }
                                    .padding(horizontal = metrics.chipHorizontalPadding, vertical = 3.dp),
                                color = DesktopTheme.TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
                suggestionNotice?.let { message ->
                    Text(message, color = DesktopTheme.TextSecondary, fontSize = 12.5.sp)
                }
            }
        }

        // ⑤ 内容本体。状态栏与它**平级**才贴得住窗底。
        Box(Modifier.weight(1f).padding(start = pad, end = pad, bottom = 10.dp)) {
            when (val current = state) {
                SearchState.Idle -> SearchIdle(source, dispatched.isEmpty(), missing.isNotEmpty())
                SearchState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("正在向 ${dispatched.size} 个站发车", color = DesktopTheme.TextSecondary, fontSize = 13.sp)
                    ProgressBar()
                }

                is SearchState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "这一轮没有取到：${current.reason}",
                        color = DesktopTheme.AccentBeni,
                        fontSize = 13.sp,
                    )
                    Text(
                        "重试",
                        color = DesktopTheme.AccentFuji,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { round += 1 },
                    )
                }

                is SearchState.Ready -> Column(Modifier.fillMaxWidth()) {
                    val batch = current.batch
                    // 与日榜墙、收藏墙同一把分级判据。搜索这一路以前**没过这一关**：
                    // 同一条命中在手机上被挡、在桌面上直接摆出来，而搜索恰恰是最容易撞到
                    // 未分级内容的入口。`guardRules` 只当 remember 的键，理由同日榜那处。
                    // ⚠️ 这七枚取值与那面墙**排在段头两句之前**：段头那句要数墙（批 13），
                    //    而它排在最前面是观感顺序 —— 两者不冲突，声明顺序跟着数据走。
                    val maskMode by ports.contentGuard.nsfwMaskMode.collectAsState()
                    val guardRules by ports.contentGuard.rules.collectAsState()
                    val blockAi by ports.prefs.galleryBlockAi.collectAsState()
                    // 第五批：列数 / 清晰度 / AI 角标这三枚也在这面墙上生效（与日榜、收藏同一套档）。
                    val columnMode by ports.prefs.galleryColumnMode.collectAsState()
                    val previewQuality by ports.prefs.galleryPreviewQuality.collectAsState()
                    val aiBadge by ports.prefs.galleryAiBadge.collectAsState()
                    // 点卡的接收者住在应用根（选中态只有一颗），这里只取句柄并**显式传给墙**。
                    val onPick = LocalDesktopWallPick.current
                    val wall = remember(batch.posts, maskMode, guardRules, blockAi) {
                        DesktopGalleryWallPolicy.of(batch.posts, maskMode, blockAi, ports.contentGuard)
                    }
                    // 「进屏 N 张」与「各站 x 张」**同一份数**：都数这面墙真摆出来的那些
                    // （从前两句吃的是 `batch.posts` = 取回的整批，被挡掉的那些也计在里面）。
                    val countLine = desktopWallReadingLine(
                        "进屏 ${wall.cards.size} 张",
                        desktopPerSiteReading(desktopWallPosts(wall).groupingBy { it.site }.eachCount(), unit = "张"),
                        // 这批是**累计**的（续页只追加不替换），所以那句"进屏 N 张"到第 N 页仍然成立；
                        // 页码另起一段，因为它是"各腿最深那一页"，不是张数。
                        if (batch.page > 1) "已给到第 ${batch.page} 页" else null,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "「${query.trim()}」",
                            color = DesktopTheme.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        // 零张时那一格**整段不画**：从前它是「 · 进屏 0 张 ·」——末尾悬着一枚分隔符，
                        // 而"各站"那一段为空是常事（全被挡在屏前、或站方答上但 0 条）。
                        if (countLine.isNotEmpty()) {
                            Text(
                                countLine,
                                color = DesktopTheme.TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (batch.posts.isEmpty()) {
                        Text(
                            "站方答上了这一轮，但没有可摆上屏的内容。",
                            color = DesktopTheme.TextSecondary,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        if (wall.cards.isEmpty()) {
                            // 取到了、也支持展示，但全被分级与屏蔽挡下 —— 单独一句，不并入上面那句
                            // 「没有可摆上屏的内容」：那一句的主语是"站方没给能摆的档"，
                            // 而这里的内容是能摆的、是我们不给看。
                            Text(
                                "站方答上了 ${batch.posts.size} 张，本可以摆上屏的都被挡下了：" +
                                    desktopWallGuardReading(wall)
                                        .ifBlank { "分级与屏蔽规则把这一批全挡下了" },
                                color = DesktopTheme.TextSecondary,
                                fontSize = 12.5.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        } else {
                            DesktopWallGuardLine(wall, Modifier.padding(top = 8.dp))
                            DesktopGalleryPostWall(
                                wall,
                                // 搜索那一屏的段头/输入轴在 pane 里，不进滚动容器（它要常驻）。
                                lead = {},
                                columnMode = columnMode,
                                quality = previewQuality,
                                aiBadge = aiBadge,
                                onPick = onPick,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    // 页尾那一档只有三种话可说：正在取下一页 / 还能再取（那枚钮）/ 发过的站全到底了。
                    // 「到底」那句判的是**这一轮发过的腿**全到底，不是"三站都到底"（没配凭据的那一站
                    // 压根没发车，它不参与结论 —— 见 [DesktopGalleryBatch.atEnd]）。
                    when {
                        appending -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "正在取下一页…",
                                color = DesktopTheme.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            ProgressBar()
                        }

                        !batch.atEnd -> AccentButton(
                            onClick = {
                                val base = batch
                                appending = true
                                scope.launch {
                                    fetchSearchPage(ports, base).fold(
                                        onSuccess = { landed ->
                                            // 落地闸：中途改过查询或又按了一次「搜索」，这一笔属于上一批。
                                            // 那一笔的标签串与图对不上，落下来就是"卡片写着新串而图来自旧串"，
                                            // 所以丢掉 —— 但**丢掉要出声**（只在 stdout，屏上此时已是新那批）。
                                            val now = (state as? SearchState.Ready)?.batch?.seed
                                            if (now == landed.batch.seed) {
                                                state = SearchState.Ready(landed.batch, landed.legs, landed.fetchedAtMillis)
                                            } else {
                                                println(
                                                    "APPEND=丢掉一笔续页：这一笔取于第 ${base.page} 页之后（种子 ${landed.batch.seed}），" +
                                                        "而屏上已经是种子 $now 的那一批 ⇒ 内容不落地",
                                                )
                                            }
                                            appending = false
                                        },
                                        onFailure = { failure ->
                                            appending = false
                                            println(
                                                "APPEND=续页没取成：${failure::class.java.simpleName} " +
                                                    "${failure.message?.take(200)}（屏上留着已经取到的那 ${base.posts.size} 张）",
                                            )
                                        },
                                    )
                                }
                            },
                            disabled = appending,
                        ) { Text("下一页", fontSize = 13.sp) }

                        // 没有一站到底 ⇒ **不念**（上一版会念出「已经取到底：（这一轮发过的站都到底了）」那种空壳）。
                        else -> desktopExhaustedReading(batch.sitesDone.map { it.displayName })?.let { line ->
                            Text(
                                line,
                                color = DesktopTheme.TextTertiary,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    // 单腿没答上时"到底"那句是**下一笔再问它**的意思，所以那一档不并进来念：
                    // 上面 [SearchNotices] 第 ③ 档逐站点名了原因，这里不重复。
                }

                is SearchState.Blocked -> Text(
                    current.reason,
                    color = DesktopTheme.AccentBeni,
                    fontSize = 13.sp,
                )
            }
        }

        if (state is SearchState.Ready) {
            val current = state as SearchState.Ready
            val dropped = listOfNotNull(
                // 两枚计数是**逐笔累加**的（续页那一笔排掉几条要并进来，只留最后一笔会把上一笔的账抹掉）。
                current.batch.droppedUnusable.takeIf { it > 0 }?.let { "不可摆 $it 条" },
                current.batch.droppedDuplicates.takeIf { it > 0 }?.let { "重复 $it 条" },
            ).joinToString(" · ")
            DesktopGalleryStatusBar(
                siteTotal = dispatched.size,
                // ⚠️ 这里传的是**最近一笔真正答上的腿数**减出来的缺席数，不是累计：
                // "答上了但回空表"不是缺席（`GalleryLegOutcome.answered == true`），
                // 两者混起来就是"这一站明明没出错却被算成缺席"，所以只数 `answered == false`。
                missingCount = current.legs.count { !it.answered },
                fetchedAtMillis = current.fetchedAtMillis,
                budgetMs = SearchBudgetMs,
                droppedLabel = dropped,
            )
        }
    }
}

/** 搜索屏的每站预算：**引日榜那一枚**，不另写一个 12_000。 */
private val SearchBudgetMs = GalleryDailyFeed.PER_SITE_TIMEOUT_MS

/**
 * 一笔落地后的三样读数（第一轮与续页同一颗）。
 *
 * `fetchedAtMillis` 在**取完数那一刻**取，不是渲染时现取 —— 否则状态栏那句回答的是
 * "我什么时候看的屏"，而不是"墙上这批是哪一轮拿的"。
 */
internal class DesktopSearchLanding(
    val batch: DesktopGalleryBatch,
    val legs: List<GalleryLegOutcome>,
    val fetchedAtMillis: Long,
)

/**
 * 取**一页**搜索。第一轮与「下一页」走的是同一颗 —— 两条路各写一份取数，
 * 就是"第一页能翻而续页的判据漂"那种形状的成因（发现那一屏同一族，见 `fetchForYouPage`）。
 *
 * 三条转交，一条都不在桌面重述：
 * - 各腿要第几页 → [desktopGalleryBatchNextPages]（里面就是共享面那颗 `nextPagesForAppend`，
 *   缺席的那一站从第 1 页补，不跟着别腿跳页）；
 * - 哪些腿不再问 → [desktopGalleryBatchLegs]（到底的站排除在外）；
 * - 混排、跨页去重、到底判据 → [GallerySearchMerge]，落地成新一颗状态 → [desktopGalleryBatchApply]。
 *
 * ⚠️ `interleave` 包在 `runCatching` 之内是**判据**而不是习惯：它自己在"给货却滤完 0 条"时抛，
 * 那属于判据出错，必须冒到错误页，不能被腿级失败咽成空墙。
 *
 * 失败名单那颗账走 [desktopFailureLedgerAfter]（批 33：这一屏从前自己写的是
 * `base.failures + 这一轮的原因` —— 并集方向反了，于是抖过一次而后来答上的那一站
 * 永久挂在页尾那句「没答上」里）。改成 `internal` 是为了让判据能真跑它（与批 31/32 同一族）。
 */
internal suspend fun fetchSearchPage(
    ports: GalleryPorts,
    base: DesktopGalleryBatch,
): Result<DesktopSearchLanding> = withContext(Dispatchers.IO) {
    runCatching {
        val legsToRun = desktopGalleryBatchLegs(base, base.queries)
        val pages = desktopGalleryBatchNextPages(base, legsToRun.keys.toList())
        val outcomes = coroutineScope {
            legsToRun.map { (site, tags) ->
                val board = boardOf(ports, site)
                site to async {
                    GalleryLegGuard.guard(site) {
                        board.searchPosts(tags, page = pages.getValue(site), limit = board.pageSize)
                    }
                }
            }.map { (site, deferred) -> site to deferred.await() }
        }.toMap()
        val legs = outcomes.values.toList()
        // 合成前先把"没答上的腿"剔掉：它们不参与混排，也不该被算进"到底"
        // （`GallerySearchMerge` 的类注释第 ③ 条：缺席腿当成到底，一次抖动就永久踢掉一站）。
        val answered = outcomes.filterValues { it.answered }.mapValues { it.value.posts }
        val returned = answered.mapValues { (_, posts) -> posts.size }
        val merged = GallerySearchMerge.interleave(answered, base.seenKeys).getOrThrow()
        val landed = desktopGalleryBatchApply(
            current = base,
            round = base.seed,
            pages = pages,
            returnedBySite = returned,
            reasons = desktopFailureLedgerAfter(
                previous = base.failures,
                asked = outcomes.keys,
                fresh = outcomes.mapNotNull { (site, outcome) -> outcome.reason?.let { site to it } }.toMap(),
            ),
            pageSizeOf = { site -> boardOf(ports, site).pageSize },
            // 搜索这一路**不排收藏**（那一档只在「猜你喜欢」上有意义），所以那枚计数恒 0 ——
            // 交一个 0 而不是不给：少一个入参的形状与"这里确实不排"是两件事。
            paged = GalleryForYouMerge.Paged(
                posts = merged.posts,
                perSite = merged.perSite,
                excludedFavourite = 0,
                droppedUnusable = merged.droppedUnusable,
                droppedDuplicates = merged.droppedDuplicates,
            ),
        )
        DesktopSearchLanding(landed, legs, System.currentTimeMillis())
    }
}

/** 来源轴（复用段头那颗分段控件的形状，但数据面是 `GallerySearchSource`，不是 pane 的六项）。 */
@Composable
private fun DesktopSearchSourceAxis(
    sources: List<GallerySearchSource>,
    selectedIndex: Int,
    onSelect: (GallerySearchSource) -> Unit,
) {
    val metrics = DesktopGalleryMetrics
    Box(
        Modifier
            .background(DesktopTheme.SidebarBackground, RoundedCornerShape(metrics.controlRadius))
            .padding(metrics.segmentedInnerPadding),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.segmentedItemGap)) {
            sources.forEachIndexed { index, source ->
                val selected = index == selectedIndex
                Text(
                    source.label,
                    modifier = Modifier
                        .background(
                            color = if (selected) DesktopTheme.SurfaceRaised else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(metrics.segmentedItemRadius),
                        )
                        .clickable { onSelect(source) }
                        .padding(
                            horizontal = metrics.segmentedItemHorizontalPadding,
                            vertical = metrics.segmentedItemVerticalPadding,
                        ),
                    color = if (selected) DesktopTheme.TextPrimary else DesktopTheme.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 三档缺席，三档**长得不一样**。
 *
 * 混淆这三者是搜索屏最容易做坏的地方：
 * - **发不出去**（没配 Gelbooru 账号）：我们自己没发车 ⇒ 摆 `NEEDS_ACCOUNT_REASON` 与配置入口。
 * - **没答上**（超时 / 401 / 解析失败）：站方或链路的问题 ⇒ 逐站点名 + `reason`。
 * - **预算挡住**：我们自己拦下的 ⇒ 说清是几枚标签的预算，不是"搜索失败"。
 *
 * 三条在屏上挤成一句"部分站点无结果"是本仓最忌的那种静默降级。
 */
@Composable
private fun SearchNotices(
    state: SearchState,
    source: GallerySearchSource,
    dispatched: List<GallerySite>,
    missing: Map<GallerySite, String>,
    budgetNotice: String?,
    filters: List<GalleryTagFilter>,
) {
    // ① 发不出去的那些腿
    if (missing.isNotEmpty()) {
        Text(
            missing.entries.joinToString(" · ") { "${it.key.displayName}：${it.value}" },
            color = DesktopTheme.AccentBeni,
            fontSize = 12.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
    // ② 预算那道闸：即便还没点搜索也要说（用户在输入框里就能看到自己装不下）。
    if (filters.isNotEmpty() && budgetNotice != null) {
        val wouldBlock = dispatched.firstOrNull { !GallerySearch.fitsTagBudget(it, filters.size) }
        if (wouldBlock != null) {
            Text(
                "$budgetNotice（当前 ${filters.size} 枚）",
                color = DesktopTheme.TextTertiary,
                fontSize = 11.5.sp,
            )
        }
    }
    // ③ 本轮没答上的腿
    if (state is SearchState.Ready) {
        val unanswered = state.legs.filter { !it.answered }
        if (unanswered.isNotEmpty()) {
            Text(
                "这一轮没答上的站：" + unanswered.joinToString(" · ") { leg ->
                    "${leg.site.displayName}：${leg.reason ?: "没给原因"}"
                },
                color = DesktopTheme.AccentBeni,
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 没搜过的那一屏。**不居中**（理由与收藏屏那条一致）。 */
@Composable
private fun SearchIdle(source: GallerySearchSource, noLegs: Boolean, hasMissing: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "还没有搜过",
            color = DesktopTheme.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            when {
                noLegs && hasMissing -> "这一档一条腿都发不出去，先把上面那一条处理掉。"
                noLegs -> "这一档没有可发的站。"
                else -> "在上面的框里输入标签（空格分隔，减号开头是排除），按「搜索」向 ${source.label} 发车。"
            },
            color = DesktopTheme.TextSecondary,
            fontSize = 12.5.sp,
        )
    }
}

/**
 * 「最近搜索」那一排。三件事各自是**一个动作**，不许合并：
 * 点那一串字 = 按这一条再跑一遍；点「删掉」= 只去掉这一条；点「清空」= 整清。
 * 对岸把这三件事拆开的原话记在 `GallerySearchArea.kt` 的列表注释里（整枚 chip 是"再跑一遍"，
 * 删掉不能也落在它上面）—— 合并成一枚的后果是"想删一条得先搜一遍"。
 *
 * 站名跟在词后面用顿号分开（不是空格）：图证记过一次"空格分隔的站名被读成一个站名"。
 * 横着滚而不折行，是因为这一排的用途是"认出我搜过哪个"，不是读全文；
 * 摆几条**不设上限**（封顶在共享面那颗 `HISTORY_MAX`，桌面不自砍一份）。
 */
@Composable
private fun DesktopHistoryRow(
    history: List<GallerySearchEntry>,
    onPick: (GallerySearchEntry) -> Unit,
    onRemove: (GallerySearchEntry) -> Unit,
    onClearAll: () -> Unit,
) {
    val metrics = DesktopGalleryMetrics
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("最近搜索", color = DesktopTheme.TextTertiary, fontSize = 12.sp)
            Text(
                "清空",
                color = DesktopTheme.AccentFuji,
                fontSize = 12.sp,
                modifier = Modifier.clickable { onClearAll() },
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            history.forEach { entry ->
                Row(
                    Modifier
                        .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(999.dp))
                        .padding(start = metrics.chipHorizontalPadding, end = 6.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        entry.query,
                        modifier = Modifier.clickable { onPick(entry) },
                        color = DesktopTheme.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                    Text(
                        entry.sites.joinToString("、") { it.displayName },
                        color = DesktopTheme.TextTertiary,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                    Text(
                        "删掉",
                        modifier = Modifier.clickable { onRemove(entry) },
                        color = DesktopTheme.TextTertiary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

/**
 * 站 → board。`when` 穷尽：加站时编译器顶出来，而不是给一条 else 悄悄返回 null。
 *
 * 第九批起「发现」那一屏也用这一颗（它按站发的是同一族请求），所以从 `private` 提上来 ——
 * 两处各写一份 `when (site)` 就是给"某一处漏了一站"留位置。
 */
internal fun boardOf(ports: GalleryPorts, site: GallerySite) = when (site) {
    GallerySite.YANDERE -> ports.boards.yandere
    GallerySite.GELBOORU -> ports.boards.gelbooru
    GallerySite.SAFEBOORU -> ports.boards.safebooru
}

/**
 * 来源档 → 分段控件的索引（那颗控件吃索引，而交接载荷交的是**哪一档**）。
 *
 * 换算只留一处，而找不到就**当场说清**而不是钳回 0：`options` 恒由「全部 + 每一站的单站档」
 * 拼成（`GallerySearchSource.kt:92-93`），种子那两颗出处也在同一张表里取，
 * 所以真到了 -1 只能是有人在表外造了一枚来源 —— 那该炸，而不是悄悄落到「全部」再让用户以为
 * "我点的是那一站"。
 */
private fun sourceIndexOf(sources: List<GallerySearchSource>, source: GallerySearchSource): Int =
    sources.indexOfFirst { it == source }.also {
        require(it >= 0) { "来源轴里没有「$source」这一档 —— 交接载荷是在表外造的来源" }
    }

/** 补全候选最多摆几枚。取 8 是"一行放得下"的那一档，不是从一个更大的数里砍下来。 */
private const val SUGGESTION_ROWS = 8

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Failed(val reason: String) : SearchState

    /** 标签数超出某站预算 —— **我们自己拦下的**，不是站方 422。 */
    data class Blocked(val reason: String) : SearchState

    data class Ready(
        /** 累计的那批卡 + 各腿页码 + 到底的站 + 逐站点名的原因（第十三批 批 8 起这一屏也用它）。 */
        val batch: DesktopGalleryBatch,
        /** **最近一笔**各腿的落点：状态栏那句"几站没答上"读的是它，累计的账在 [batch] 里。 */
        val legs: List<GalleryLegOutcome>,
        val fetchedAtMillis: Long,
    ) : SearchState
}
