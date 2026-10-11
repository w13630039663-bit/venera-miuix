package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GelbooruCredentialState
import com.venera.compose.gallery.domain.GalleryDailyFeed
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「每日热门」这一 pane：三站的每日热门 → 合并去重 → 图片墙。
 * （三站的"热门"不是同一件事：只有 yande.re 是按天的日榜，另两站取的是高分池 —— 口径在那份类注释里，
 * 界面上的日期因此只描述 yande.re 那一路。）
 *
 * 取数只走 S1 落进共享面的那颗编排件 `GalleryDailyFeed(ports.boards).loadDaily(seed)`；
 * `ports` 由**形参**交进来，这颗文件里没有 `GalleryPorts.of(...)`（桌面的接线件 `DesktopGalleryPorts`
 * 在 A 线的 worktree 里，形状不在本批的编译面上 —— 这一条正是两颗计划能并行的前提）。
 *
 * ## 三条口径
 *
 * 1. **`failures` 非空必须在界面上说出来**：缺席站的名单**逐字来自 `failures.keys`**，
 *    不自己数、不自己判哪几站算缺。三站混搭静默退化成一两站刷屏是本仓最忌的那类假读数。
 *    各站实际进屏的条数（`merged.perSite`）与被滤掉的条数一并念出来 —— 少了也要说。
 * 2. **列数走 [DesktopGalleryMetrics.imageWallColumnCount]**：默认 1440 窗口档（稿 `.frame` 尺寸）
 *    的读数是 **6 列**（`1440−48−224=1168` → `ceil((1168−24)/200)=6`，与稿 `:338`/`:657` 标注的
 *    「内容净宽 1168 → 6 列」逐字相符）。稿上 `.wall` 那个 `repeat(5)` 是**展示稿的硬编码摆位**，
 *    **推导不许抄展示稿** —— 由 `DesktopWidthCaliberDriftTest` 与 `DesktopWallColumnTest` 钉住。
 * 3. **卡位摆的是这一条路真拿得到的东西**（站方给的元数据 + 那一档缩略图），而**卡片可点**
 *    （第十一批 P0 起：点一张卡 = 右侧盖上详情浮层，见 [DesktopGalleryDetailPanel]）。
 *    ⚠️ 这一条**从前写的是反话**（"卡片不可点，卡位上不摆像素"），那是两批之前的旧账：
 *    第四批起卡位就一直用 coil3 的 `AsyncImage` 真取图（三态说法在 [DesktopGalleryPostTile] 的类注释里），
 *    而"没有画廊详情页"那半句在 P0 之后也不成立了。**留着的真话只有一句**：
 *    共享那颗 `GalleryImageLoader` 仍在 `desktop/build.gradle.kts` 的排除清单里（成因写着
 *    coil3 + data.prefs + data.network），桌面也不在 UI 壳里另起一条 OkHttp 取图路 ——
 *    那会装出"看着像取图层、实际少了 UA 与限流"的假路，S1 对这种半做过一次判断
 *    （`docs/rounds/windows-gallery-home-s1-2026-10-03.md` §「三件故意没做」第 1 条）。
 *
 * ⚠️ S2-B 那批在这里写过一句"这两颗 pane 还没有消费点、接线归集成" —— **今天不成立**：
 * 这一屏真在画封面墙（`DesktopGalleryPostWall`），第十五批 批 13 那帧窗口捕获读到的就是它。
 * 那一格留着只当历史读数，**别拿它当待办**（禁串由 `DesktopStaleClaimsTest` ⑪ 钉着，配一条"墙还在"的对照）。
 */
@Composable
internal fun DesktopDailyPane(ports: GalleryPorts) {
    // 批 5：这一屏的三枚账都换成了 `rememberSaveable` —— 单独换没有用，
    // 是应用根那颗 `SaveableStateHolder` 在 subtree 离开组合前把它们存下、用同一个键回来时还原
    // （理由与"为什么不是根上那张表"都写在 `VeneraDesktopApp` 里那段注释与 `DesktopDomainStateMemoryTest` 的类注释）。
    var attempt by rememberSaveable(ports) { mutableStateOf(0) }
    // 种子只在"这一屏立起来"时取一次：同一个种子必得同一个序列（`GalleryMerge` 的口径），
    // 否则组合一重建整屏换序，读起来像"页面自己抖了一下"。Android 那侧种子由 ViewModel 持有，
    // 桌面这轮没有 VM，所以从前记在 `remember` 里 —— 而那颗随组合一起没了，换回来就是另一批卡。
    val seed = rememberSaveable(ports) { System.currentTimeMillis() }
    var state: DailyState by rememberSaveable(ports, seed, attempt) { mutableStateOf(DailyState.Loading) }

    LaunchedEffect(ports, seed, attempt) {
        // 组合重新进来时 effect **一定**重跑，所以还原了状态还要这一道守卫：
        // 没有它，用户看到的是"卡还在、却又转了一圈圈"。`state` 的 inputs 与本枚 effect 的 key
        // 是同一组 ⇒ "Ready" 就等价于"为这一组输入已经取到手"，这一笔才可以跳。
        if (state is DailyState.Ready) return@LaunchedEffect
        // 引擎与网络都是阻塞式，一笔都不许留在 UI 线程上（与 VeneraDesktop.kt:310 同一纪律）。
        val outcome = withContext(Dispatchers.IO) {
            runCatching { GalleryDailyFeed(ports.boards).loadDaily(seed).getOrThrow() }
        }
        state = outcome.fold(
            // 时刻戳在这里取 —— `getOrThrow()` 返回的那一点就是"这一轮到手"的时刻。
            onSuccess = { DailyState.Ready(it, System.currentTimeMillis()) },
            onFailure = { DailyState.Failed("${it::class.java.simpleName}: ${it.message?.take(240)}") },
        )
    }

    // S4：配好凭据之后自动重跑一轮。
    //
    // ⚠️ 读的是 `gelbooruIdentity` 这枚**流**，而不是某个本地 state —— 判据件是单一事实源
    // （存好/注销时它自己推流）。这里每次**进屏**取一次初值就够了：用户在这屏上配好凭据
    // 时 `identity` 从 null 变成有值，这一笔的 key 变了就重跑；反过来注销也重跑，
    // 于是"注销后那一站又缺席"同样会被说出来，而不是停在一屏旧读数上。
    //
    // 刻意**不**读 `apiKey` / `userId` 两枚串：那两样是明文，逐值重组会让这屏在每次击键时重跑取数。
    val identityReady = (ports.credentials as? DesktopGalleryCredentials)
        ?.gelbooru?.identity?.value != null
    // 批 5 顺手把这一笔的**触发条件**对准了：从前它写的是 `if (identityReady) attempt += 1`，
    // 那是"每次进这一屏都重跑一轮"（换回来必然重新发车），而本意是"凭据**在这屏上**配好了才重跑"。
    // 现在比的是"进屏时看到的那一档"与"现在这一档"，同档就不重跑；换回来时两者相同 ⇒ 不跳轮。
    var identitySeen by rememberSaveable { mutableStateOf(identityReady) }
    LaunchedEffect(ports, seed, attempt, identityReady) {
        if (identityReady == identitySeen) return@LaunchedEffect
        identitySeen = identityReady
        attempt += 1
    }

    when (val current = state) {
        DailyState.Loading -> Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("正在取三站的每日热门")
            ProgressBar()
        }

        is DailyState.Failed -> Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("这一轮每日热门没有取到：${current.reason}")
            Text("重试", modifier = Modifier.clickable { attempt += 1 })
        }

        is DailyState.Ready -> DailyWall(
            daily = current.daily,
            ports = ports,
            fetchedAtMillis = current.fetchedAtMillis,
            onShuffle = { attempt += 1 },
        )
    }
}

@Composable
private fun DailyWall(
    daily: GalleryDailyFeed.Daily,
    ports: GalleryPorts,
    fetchedAtMillis: Long,
    onShuffle: () -> Unit,
) {
    val merged = daily.merged
    // ⚠️ 各站那一串**不在这里算**：它必须与段头那句「进屏 N 张」同一份数（都数墙），
    // 而墙在下面才建起来 —— 从前这里吃 `merged.perSite`（过关之前的 merge），
    // 于是有内容被挡时屏上写成"进屏 8 张 · yande.re 12 张"，两句加不起来（批 13）。
    val dropped = listOfNotNull(
        merged.droppedUnusable.takeIf { it > 0 }?.let { "不可摆 $it 条" },
        merged.droppedDuplicates.takeIf { it > 0 }?.let { "重复 $it 条" },
        merged.videos.takeIf { it > 0 }?.let { "其中视频 $it 条" },
    ).joinToString(" · ")

    // 段头 chips 取**当页**标签频次（[desktopTopTags]），随机数那样飘的东西不要。
    // `remember(posts)` 而不是每次重组都算：几十张卡的标签表扫一遍不贵，但没必要跟着动画重组重算。
    val (tags, hiddenTagCount) = remember(merged.posts) { desktopTopTags(merged.posts) }
    val segments = remember { desktopGallerySegments() }
    var segmentIndex by rememberSaveable { mutableStateOf(0) }
    var segNotice by rememberSaveable { mutableStateOf<String?>(null) }
    val pad = DesktopGalleryMetrics.pageHorizontalPadding

    Column(Modifier.fillMaxSize()) {
        // ① 段头（稿 `.phead`，`:387-395`）：日期 / 并行 / 预算 + 分段控件 + 当页标签 + 换一批。
        DesktopGalleryPageHeader(
            date = daily.date,
            budgetLabel = budgetLabel(GalleryDailyFeed.PER_SITE_TIMEOUT_MS),
            tags = tags,
            hiddenTagCount = hiddenTagCount,
            selectedIndex = segmentIndex,
            onSegment = { segment ->
                if (segment.reason == null) {
                    // 接不上的那一档**不换选中项** —— 换过去内容区没有任何东西可画，
                    // 就成了"选中了但下面还是上一屏"的假态。这一条由调用方把关而不是段头自己决定：
                    // 段头只负责把"我为什么接不上"交出来。
                    segmentIndex = segments.indexOfFirst { it.label == segment.label }
                    segNotice = null
                } else {
                    segNotice = segment.reason
                }
            },
            onShuffle = onShuffle,
            modifier = Modifier.padding(horizontal = pad),
        )

        // 点「最新 / 排行」时的话就落在这里 —— 它是段头的回答，所以紧贴段头下方，
        // 不混进墙上方那条缺席行（那条说的是**取数**，这条说的是**视图没接**）。
        segNotice?.let { reason ->
            Text(
                reason,
                modifier = Modifier.padding(horizontal = pad, vertical = 6.dp),
                color = DesktopTheme.AccentBeni,
                fontSize = 12.sp,
            )
        }

        // ② 内容本体：hero → 缺席 → 节头 → 被挡那一行 → 墙，**全在一颗滚动容器里**
        //（用户 2026-10-04 那条：整页该能下滑，不该只有下面那截在滚）。
        // ⚠️ 解法**不是**"外层挂 `verticalScroll`" —— 那会让墙吃无限高、整屏崩（手册 §23.3）；
        //   也**不是**"给墙算一个定高" —— 照片墙的行数随窗宽变，那条高是一条会漂的账。
        //   正解是把前面那几排做成**跨整行的 grid item**，于是全页只剩一个滚动容器。
        // 整块吃剩余高度，状态栏才贴得住窗底。
        Column(
            Modifier
                .weight(1f)
                // ⚠️ 写 `start/end/bottom` 而不是 `horizontal + bottom` —— Compose 的 `padding`
                // 没有那个具名组合（`horizontal` 只与 `vertical` 配对），写上去编译不过。
                .padding(start = pad, end = pad, bottom = 10.dp),
        ) {
            // 分级这一关在**画之前**过，而且**整屏只算一次**：hero 与墙吃同一份 [DesktopWall]。
            // 各算一遍的话，"hero 取墙上第一张"这句话就会分叉 —— 而 Android 侧那把判据
            // （`buildGalleryWall`）也是在使用处算一次、两处共用。
            // 判据是共享那颗 `GalleryGuard.maskStateFor`，与 Android 同一把
            // （详见 [DesktopGalleryWallPolicy] 的类注释）。
            // `guardRules` 在这里没有别的用处，**只当 remember 的键**：屏蔽规则是 store 里的表，
            // 不进键的话"长按标签屏蔽此标签"之后这面墙不会重算 —— Android 侧同一处也是这么挂的。
            val maskMode by ports.contentGuard.nsfwMaskMode.collectAsState()
            val guardRules by ports.contentGuard.rules.collectAsState()
            val blockAi by ports.prefs.galleryBlockAi.collectAsState()
            // 这三枚是"墙上那一格"的档（列数 / 摆哪一档图 / 摆不摆 AI 角标）。第五批之前它们
            // 在桌面**零消费** —— 设置改了屏上不动，那与假开关同形。判据本身都不在这里重写：
            // 列数转交 `GallerySettingsModel.gridColumns`，地址转交 `.wallUrl`，AI 判定转交 `GalleryAi`。
            val columnMode by ports.prefs.galleryColumnMode.collectAsState()
            val previewQuality by ports.prefs.galleryPreviewQuality.collectAsState()
            val aiBadge by ports.prefs.galleryAiBadge.collectAsState()
            // 点卡的接收者住在应用根（选中态只有一颗），这里只取句柄并**显式传给墙**。
            val onPick = LocalDesktopWallPick.current
            val wall = remember(merged.posts, maskMode, guardRules, blockAi) {
                DesktopGalleryWallPolicy.of(merged.posts, maskMode, blockAi, ports.contentGuard)
            }

            DesktopGalleryPostWall(
                wall = wall,
                columnMode = columnMode,
                quality = previewQuality,
                aiBadge = aiBadge,
                onPick = onPick,
                wideFirst = true,
                // 前置那四排做成跨整行的 item ⇒ 它们跟着墙一起滚，全页只有这一个滚动容器。
                lead = {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        DesktopDailyHero(
                            daily = daily,
                            wall = wall,
                            onShuffle = onShuffle,
                        )
                    }

                    // ③ 缺席必须先说 —— 它决定"这一屏为什么只有两站"，所以它**留在墙上方**而不是页脚。
                    if (daily.failures.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "这一轮没给内容的站：" +
                                        daily.failures.entries.joinToString(" · ") { "${it.key.displayName}：${it.value}" },
                                    color = DesktopTheme.AccentBeni,
                                    fontSize = 12.sp,
                                )
                                // S4：缺席原因指向的那件事要能在这里修。
                                // 条件刻意只判"这一站缺席"而不判缺席原因的字面：**原因句会改**
                                // （站方改文案、换措辞），拿字面去挂钩会让面板在某次措辞调整后静默消失。
                                // 理由原文改由面板自己念（`GelbooruCredentialState.ANONYMOUS_HINT`）。
                                if (daily.failures.containsKey(GallerySite.GELBOORU)) {
                                    val credentials = ports.credentials as? DesktopGalleryCredentials
                                    if (credentials != null) {
                                        GelbooruCredentialLauncher(credentials)
                                    } else {
                                        Text(
                                            "Gelbooru 缺凭据（${GelbooruCredentialState.ANONYMOUS_HINT}），" +
                                                "但当前接线没有桌面凭据件。",
                                            color = DesktopTheme.TextSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ④ 节头（稿 `.sechead` `:140-141`）：节名 + 进屏读数。
                    //    「进屏 N 张」念的是**过了分级这一关**的张数 —— 拿取数张数冒充进屏张数，
                    //    会在有内容被挡时少报，而用户回头数存档就对不上。
                    //    ⚠️ 稿这里右边还有一枚「查看更多」（`.sechead .more`）—— **不画**：桌面今天的墙
                    //    就是这一轮取到的全部，"查看更多"按下去没有下一页可翻，是一枚假开关。
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "精选推荐",
                                color = DesktopTheme.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            // 零张时那一格**整段不画**：从前它写成「 · 进屏 0 张 ·」——各站那一段为空时
                            // 末尾会悬着一枚分隔符（这一档真会走到：整轮全被挡在屏前）。
                            desktopWallReadingLine(
                                "进屏 ${wall.cards.size} 张",
                                desktopPerSiteReading(
                                    desktopWallPosts(wall).groupingBy { it.site }.eachCount(),
                                    unit = "张",
                                ),
                            ).takeIf { it.isNotEmpty() }?.let { line ->
                                Text(
                                    line,
                                    color = DesktopTheme.TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // 被挡的那两笔（分级收起 / 命中屏蔽规则）单独一行说，不与"进屏 N 张"合并：
                    // 合进去会变成"进屏 8 张（2 张被挡）"这种一句话两个主语的读法。
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        DesktopWallGuardLine(wall)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        // ↑ 第二层是内容本体那一节（hero / 缺席 / 节头 / 墙），它吃 `weight(1f)`；
        //   下面那条状态栏必须与它**平级**才贴得住窗底 —— 塞进它里面会和墙一起被压缩。

        // ⑤ 状态栏（稿 `.status`，`:243-245`）：全宽贴在内容区底部。
        //
        // ⚠️ 这里原先是一条"页脚"（把三站口径与已滤掉条数排在墙下面）。本批把它换成状态栏：
        // - 「三站口径」那句 Hero 右侧第一张侧卡**已经在说**（内容一致），页脚再说一遍就是重复；
        // - 「已滤掉 X 条」是真读数，但它属于"这一批货的账"，该躺在状态栏那条读数带里；
        // - 「yande.re 取的是 x 的日榜」同样由侧卡承担。
        // 同一件事在两处讲，改动时迟早只改一处 —— 那时屏上就同时摆着两个互相否定的读数。
        DesktopGalleryStatusBar(
            siteTotal = daily.pools.size,
            missingCount = daily.failures.size,
            fetchedAtMillis = fetchedAtMillis,
            budgetMs = GalleryDailyFeed.PER_SITE_TIMEOUT_MS,
            droppedLabel = dropped,
        )
    }
}


private sealed interface DailyState {
    data object Loading : DailyState
    data class Failed(val reason: String) : DailyState

    /**
     * @param fetchedAtMillis **取数完成那一刻**的时刻戳，给状态栏那句「本次取数 X 前」。
     *   不是在渲染时现取的时刻 —— 那句话要回答的是"墙上这批图是哪一轮拿的"，
     *   而"渲染的这一刻"会随着每次重组往后挪，填进去就成了自指。
     */
    data class Ready(val daily: GalleryDailyFeed.Daily, val fetchedAtMillis: Long) : DailyState
}
