package com.venera.desktop.gallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.toPost
import com.venera.compose.gallery.domain.GalleryMerge
import io.github.composefluent.component.Text
import kotlinx.coroutines.launch

/**
 * 画廊收藏域的那一面墙。
 *
 * ## 取数链一步都不新造
 *
 * `ports.favorites.favorites` 是**真 StateFlow**（`GalleryFavoritesStore` 在构造时同步读完本机
 * `gallery_favorites.json`），本屏整条链就是读它 → [GalleryFavorite.toPost] → 墙。
 * 桌面这一侧此前**消费数 0**（2026-10-04 骨架那批量的），而 Android 侧那份数据一直在落盘。
 *
 * ## 三条口径，每条都对着一种"假读数"
 *
 * 1. **站方键认不出来的条目要单独说出来，不许静默丢**。`GalleryFavorite.site` 认不出 routeKey
 *    时给 `null`（`GallerySite.fromRouteKey` 的口径），而 [GalleryFavorite.toPost] 的签名要一个
 *    非空的站 —— 于是这批条目**没法画**。直接 `mapNotNull { it.site?.let { s -> it.toPost(s) } }`
 *    的形状是"收藏 12 张"而屏上只有 9 张，用户会去数。本屏把那几条的**条数与键**一起念出来。
 * 2. **不可摆上屏的（[GalleryMerge.isDisplayable] 为假）也要计数**。日榜那头有
 *    `droppedUnusable` 这条读数，本屏照同一把尺；否则"收藏里有 3 张是站方没给图的记录"
 *    会被读成"这 3 张加载失败"—— 两件事的成因不同。
 * 3. **空收藏与"有收藏但一张都摆不出来"必须念成两句话**。前者是"你还没收藏过"，
 *    后者是"有 8 条记录但全都摆不上屏"，后者需要用户去设置/数据目录里排查。
 *
 * ## 这一屏的写口：只有「清空」，而且是同一屏上两步
 *
 * `GalleryFavorites` 的 `toggle` / `removeAll` / `clear` 三枚写动作都在，桌面那份逐枚转交了；
 * 这一屏此前只接 `toggle`，于是"收藏多了清不掉"。批 77 补上「清空」，走
 * [desktopClearGalleryFavorites]：第一按只**武装**（那句改成念条数的确认），第二按才真清，
 * 清完**读回**存档还剩几条（还剩就说明没生效，那句不许念成"已清空"）。
 *
 * ⚠️ 为什么不是弹层：桌面今天没有 `ContentDialogHost`（全仓只有 `SpikeWindow` 那枚探针用过 `FluentDialog`），
 * 为这一格去应用根新铺一层宿主是把两件事捆成一件。原来那段"桌面没有弹层宿主"的理由早在 2026-10-04
 * 就被更正过（fluent 侧 `ContentDialogHostState.show` 在、S0 实测渲染成形；真成立的那条限制是
 * **弹层只能实底**）—— 今天仍然不铺宿主，是因为这一格用不着，不是因为做不到。
 *
 * 摆一枚"点了就永久删掉用户收藏、且不可撤销"的按钮而没有确认，仍然是本仓最不该有的形状；
 * 而"再按一次"那一步之所以能算确认层，是因为**两档各念一句且都点名条数**（判据 ①），
 * 且条数一变就重新武装（判据 ②）。
 */
@Composable
internal fun DesktopGalleryFavoritesPane(
    ports: GalleryPorts,
    /**
     * 那份档**读不出来**时 store 留下的那一句（`null` = 档是好的），逐字念、桌面不重述。
     *
     * 必填、不给默认值，而且不在这一屏里取 —— `consumeNotice()` 读走即清，主人是应用根（批 3′）。
     * 这一枚在批 3′ 之前是**桌面零调用点**：收藏档坏了而这一屏只说"还没有收藏过"，
     * 就是把"我们读不出来"念成"你没有"。
     */
    favoritesNotice: String?,
) {
    val favorites by ports.favorites.favorites.collectAsState()

    // 「清空」那两步（批 77）：`armedAt` = 武装那一刻屏上念着的条数（`null` = 没武装）。
    // 存**条数**而不是存一颗 bool：条数一变就说明"要删的那批"换了，判据 ② 钉的就是这一格。
    val scope = rememberCoroutineScope()
    var armedAt by remember { mutableStateOf<Int?>(null) }
    var clearReading by remember { mutableStateOf<DesktopClearOutcome?>(null) }

    // `site` 为 null 的那批（站方键认不出来）与不可摆上屏的计数都从**这一屏的收藏**现算，
    // 不另存一份 state —— 另存就会与收藏流不同步，而不同步的读数比没有读数更坏。
    val projection = remember(favorites) { projectFavorites(favorites) }

    // 与日榜墙同一把分级判据（共享的 `GalleryGuard.maskStateFor`）。收藏这一屏以前是
    // 「存档 → toPost → isDisplayable → 墙」，**分级那一关根本没过** ——
    // 于是同一条收藏在手机上被挡、在桌面上照常摆出来。`guardRules` 只当 remember 的键，理由同日榜那处。
    val maskMode by ports.contentGuard.nsfwMaskMode.collectAsState()
    val guardRules by ports.contentGuard.rules.collectAsState()
    val blockAi by ports.prefs.galleryBlockAi.collectAsState()
    // 列数 / 清晰度 / AI 角标这三枚与日榜那处同一批接上（第五批）。收藏墙与日榜墙**必须是同一套档**，
    // 否则用户会遇到"同一张卡在两屏上大小不一样"。
    val columnMode by ports.prefs.galleryColumnMode.collectAsState()
    val previewQuality by ports.prefs.galleryPreviewQuality.collectAsState()
    val aiBadge by ports.prefs.galleryAiBadge.collectAsState()
    // 点卡的接收者住在应用根（选中态只有一颗），这里只取句柄。
    val onPick = LocalDesktopWallPick.current
    val wall = remember(projection.posts, maskMode, guardRules, blockAi) {
        DesktopGalleryWallPolicy.of(projection.posts, maskMode, blockAi, ports.contentGuard)
    }

    val pad = DesktopGalleryMetrics.pageHorizontalPadding
    Column(Modifier.fillMaxWidth()) {
        // ⚠️ 这一屏**不挂** `verticalScroll`：下面那一面墙自己就是滚动容器（`LazyVerticalGrid`），
        // 套进纵向滚动会拿到无限高并当场抛（整屏崩，不是滚不动）。
        // 这条此前一直是**潜伏**的：这台机器上没有画廊收藏，所以走的全是空态那几档。
        // 判据与真崩溃读数在 `DesktopWallScrollNestingTest`。
        Column(
            Modifier
                .weight(1f)
                .padding(start = pad, end = pad, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DesktopFavoritesHead(projection, desktopWallPosts(wall))
            // 「清空」那一枚（批 77）：只在真有条目时摆；第一按只武装、第二按才走那颗件，且能反悔。
            // 摆在三档空态**之前**、段头**之后**：这一排的主语是"存档里有 N 条"，与后面摆不摆得出来无关。
            val count = favorites.size
            if (desktopFavoritesClearVisible(count)) {
                val stage = if (armedAt == null) DesktopFavoritesClearStage.IDLE else DesktopFavoritesClearStage.ARMED
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        desktopFavoritesClearLabel(stage, count),
                        color = if (stage == DesktopFavoritesClearStage.ARMED) {
                            DesktopTheme.AccentBeni
                        } else {
                            DesktopTheme.AccentFuji
                        },
                        fontSize = 12.sp,
                        modifier = Modifier.clickable {
                            when (desktopFavoritesClearNext(stage, armedAt ?: 0, count)) {
                                DesktopFavoritesClearStage.FIRE -> {
                                    armedAt = null
                                    scope.launch {
                                        clearReading = desktopClearGalleryFavorites(ports.favorites, count)
                                    }
                                }
                                // 条数变了就重新对准**当下**这个数：删的那批必须是屏上刚念着的那批。
                                DesktopFavoritesClearStage.ARMED -> armedAt = count
                                // 那颗纯函数不会交回 IDLE（一按要么武装要么真清），这一支只为穷尽而存在。
                                DesktopFavoritesClearStage.IDLE -> Unit
                            }
                        },
                    )
                    if (stage == DesktopFavoritesClearStage.ARMED) {
                        Text(
                            "先不清了",
                            color = DesktopTheme.TextTertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { armedAt = null },
                        )
                    }
                    clearReading?.let { outcome ->
                        Text(
                            desktopFavoritesClearReading(outcome),
                            color = if (outcome is DesktopClearOutcome.Failed) {
                                DesktopTheme.AccentBeni
                            } else {
                                DesktopTheme.TextSecondary
                            },
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            // 逐字念 store 那一句（里面带着原档的绝对路径），桌面不重述 —— 重述的话 store 改了没人跟上。
            // 摆在三档空态**之前**：档坏了与一条收藏都没有是两件事，先说坏了我才知道后面那句能不能信。
            if (favoritesNotice != null) {
                Text(
                    favoritesNotice,
                    color = DesktopTheme.AccentBeni,
                    fontSize = 12.5.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when {
                // 分支次序是判据的次序：先"一条收藏都没有"，再"有收藏但一张都摆不出来"。
                // 反过来写会让后者落进空态那句话，用户读完以为是自己没收藏。
                favorites.isEmpty() -> DesktopFavoritesEmpty(
                    "还没有收藏过",
                    "这一屏读的是本机 gallery_favorites.json。收藏一条之后它就会出现在这儿。",
                )

                projection.posts.isEmpty() -> DesktopFavoritesEmpty(
                    "收藏里有 ${favorites.size} 条，但一张都摆不上屏",
                    unrenderableSentence(projection),
                )

                // 第四档：摆得上屏，但**全被分级这一关挡下**。它不能并进上面那档 ——
                // 那一句的主语是"站方给的档不支持展示"，而这里的内容是支持的、是我们不给看。
                // 把"我们不给看"念成"站方没有"，用户会去翻存档然后以为自己丢了收藏。
                // 走到这一档时 `projection.posts` 必非空（上面那档已经接走了"一张都摆不出来"），
                // 所以那句"全被挡下"不会把"本来就没有"说成"被我们挡了"。
                // 「本可以」那两个字是判据：少了它，"可摆上屏"与"被挡下"在同一句里互相打脸。
                wall.cards.isEmpty() -> DesktopFavoritesEmpty(
                    "收藏里有 ${favorites.size} 条，本可以摆上屏的 ${projection.posts.size} 张全被挡下了",
                    desktopWallGuardReading(wall)
                        // ⚠️ 这句兜底话**第三次**改口径，两次都是被"设置那一屏"推着改的：
                        // 第三批写它的时候桌面没有那一屏，"去设置里看档位"是一枚指向不存在的假指针；
                        // 第七批有了设置域、批 6 起分级档与屏蔽规则在那一屏真能改 ⇒ 今天它又该把用户指过去。
                        // 判据 `DesktopStaleClaimsTest` ⑬ 钉着这一句不许退回"改不了"，并配三道写口的对照。
                        .ifBlank { "分级与屏蔽规则把这一批全挡下了（当前档位读自本机存档，「设置」那一屏能改它）。" },
                )

                else -> {
                    DesktopWallGuardLine(wall)
                    DesktopGalleryPostWall(
                        wall = wall,
                        // 这一屏没有前置那几排（节头在 pane 外面），显式交空 —— `lead` 是必填的。
                        lead = {},
                        columnMode = columnMode,
                        quality = previewQuality,
                        aiBadge = aiBadge,
                        // 点卡的接收者住在应用根（选中态只有一颗），这里只取句柄并**显式传给墙**。
                        onPick = onPick,
                        // `weight(1f)` 而不是只给 `fillMaxWidth()`：墙要吃**剩下那一截的有界高度**，
                        // 撤掉外层滚动之后没有别的容器给它限高了。
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }

        // 状态栏与日榜墙**同一颗**。三句读数被显式换掉，三句都不能照抄日榜：
        // - 「本次取数」→ [LOCAL_READ_LABEL]「本机存档读于」：收藏读的是本机 JSON，一次网都不发；
        // - 「每站 N 预算 · 超时那一站这一轮缺席」→ 整条不念（[STATUS_NO_BUDGET_MS]）：
        //   收藏屏没有"站"这个动作，说超时是凭空指控；
        // - 「N 站 · 都给内容」→ 整条不念（[STATUS_NO_SITE_TALLY]）：那句的主语是"站出勤"，
        //   而存档是**已经躺在本机**的，没有任何一站出过勤；空收藏时它会念「0 站 · 都给内容」。
        //
        // 站分布并没有因此少讲 —— 它挪到了段头那句「本机存档 N 条 · 摆上屏 M 张 · 某某站 k 张」，
        // 那一句的主语是"存档"，在本屏是真读数。
        // ⚠️ `missingCount` 不再传：首句整条不念时它是**没人读**的参数，
        // 留一个 0 在那儿会让下一个人以为"0 缺席"是本屏的一处判断。
        DesktopGalleryStatusBar(
            siteTotal = STATUS_NO_SITE_TALLY,
            missingCount = 0,
            fetchedAtMillis = projection.readAtMillis,
            budgetMs = STATUS_NO_BUDGET_MS,
            droppedLabel = projection.droppedLabel,
            elapsedLabelPrefix = LOCAL_READ_LABEL,
        )
    }
}

/** 段头：标题 + 条数读数。**不套** [DesktopGalleryPageHeader] —— 那一颗的日期与"换一批"是日榜专属。 */
@Composable
private fun DesktopFavoritesHead(projection: FavoritesProjection, onScreen: List<GalleryPost>) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "画廊收藏",
            color = DesktopTheme.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        // 段头那一句：本机存档（**条**，存档里有多少条记录）/ 摆上屏（**张**，真在这面墙上的）/
        // 各站分布（与"摆上屏"同一份数）。
        // ⚠️ 后两截从前吃的是 `projection.posts`（过关之前），于是「摆上屏」那句冒领了还没上屏的张数 ——
        //   墙过完分级与屏蔽之后剩几张，屏上从来没人念（批 13 改的就是这一格）。
        desktopWallReadingLine(
            "本机存档 ${projection.total} 条",
            "摆上屏 ${onScreen.size} 张",
            desktopPerSiteReading(onScreen.groupingBy { it.site }.eachCount(), unit = "张"),
        ).takeIf { it.isNotEmpty() }?.let { line ->
            Text(
                line,
                color = DesktopTheme.TextSecondary,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 空态。**刻意不居中** —— 与 [DesktopAbsentDomainBody] 同一条理由：
 * 居中会把"这一屏没东西"变成版面设计的一部分，而这里要说的两句话（没收藏过 / 有收藏摆不出来）
 * 都需要用户去对照自己的存档，不是让他欣赏一个空画面。
 */
@Composable
private fun DesktopFavoritesEmpty(title: String, detail: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = DesktopTheme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = DesktopTheme.TextSecondary, fontSize = 12.5.sp)
    }
}

/**
 * "一张都摆不出来"那一档把三条成因都念出来，不让用户猜是哪一条。
 *
 * 条数一律取 [FavoritesProjection.unrecognizedCount] 而不是 `unrecognizedSites.size`：
 * 后者是去重后的**键数**，屏上要念的是丢了几条**记录**。3 条同坏键的记录只念"1 条"会让用户
 * 以为只丢了一条，回头去数存档时对不上 —— 那比少念一条成因更坏。
 */
private fun unrenderableSentence(projection: FavoritesProjection): String {
    val parts = listOfNotNull(
        projection.unrecognizedCount.takeIf { it > 0 }
            ?.let { "站方键认不出来 $it 条（${projection.unrecognizedSites.joinToString("、")}）" },
        projection.droppedUnusable.takeIf { it > 0 }?.let { "没有可摆的缩略图 $it 条" },
    )
    return parts.joinToString("，") + "。这些记录还在存档里，界面上画不出来。"
}

/**
 * 收藏 → 墙的投影。**纯函数**，所以"三种空态各归各"这件事能上单测而不是靠人眼点。
 *
 * @param unrecognizedSites 站方 routeKey 认不出来的**键**（已去重）。
 *   点名到键而不是只给条数：用户能拿这个键去 `gallery_favorites.json` 里找到那几行并自己改；
 *   只给"3 条"他无处下手。去重是因为同一串坏键可能重复几行，重复念会把别的成因挤出屏幕。
 * @param unrecognizedCount 认不出来的**条数**（不去重）。
 *   这一枚与 [unrecognizedSites] 分开是**必须的**，不是冗余：坏键去重之后
 *   `unrecognizedSites.size` 是"几个键"，而屏上那句念的是"几条记录摆不出来" ——
 *   存档里有 3 条同坏键的记录时，去重后的 size 会让屏上念"认不出 1 条"，
 *   而用户实际丢掉的是 3 条。那是本仓最忌的假读数（少报，用户以为只丢了一条）。
 *   这条判据（`DesktopGalleryFavoritesPaneTest` ②）就是钉这一枚的。
 * @param readAtMillis 读这一屏的那一刻。收藏不出网，所以这枚时刻戳的语义是"**存档**读了几点"，
 *   不是"取数完成几点" —— 状态栏那句话在本屏改念「存档」，见 [DesktopGalleryFavoritesPane]。
 */
internal data class FavoritesProjection(
    val posts: List<GalleryPost>,
    val total: Int,
    val unrecognizedSites: List<String>,
    val unrecognizedCount: Int,
    val droppedUnusable: Int,
    val readAtMillis: Long,
) {
    val droppedLabel: String
        get() = listOfNotNull(
            droppedUnusable.takeIf { it > 0 }?.let { "不可摆 $it 条" },
            // 计数那一项按**条数**念（[unrecognizedCount]），不是按去重后的键数 ——
            // 用户丢的是记录，屏上就该念记录；键是另一件事，由 [unrecognizedSites] 点名。
            unrecognizedCount.takeIf { it > 0 }?.let { "站方键认不出 $it 条" },
        ).joinToString(" · ")
}

internal fun projectFavorites(
    favorites: List<GalleryFavorite>,
    nowMillis: Long = System.currentTimeMillis(),
): FavoritesProjection {
    val posts = ArrayList<GalleryPost>(favorites.size)
    val unrecognized = ArrayList<String>()
    var dropped = 0
    favorites.forEach { favorite ->
        val site = favorite.site
        if (site == null) {
            // 键认不出来 ⇒ 这一条**画不了**（toPost 要一个非空站）。计进去并点名，
            // 不拿"当成某一站"兜 —— 那会把 A 站的收藏摆到 B 站的坐标系里，是串图。
            unrecognized += favorite.siteKey
            return@forEach
        }
        val post = favorite.toPost(site)
        if (GalleryMerge.isDisplayable(post)) {
            posts += post
        } else {
            dropped++
        }
    }
    return FavoritesProjection(
        posts = posts,
        total = favorites.size,
        // 同一个键认不出来可能是**多条**（存档里那条键写错了）。列出去重，
        // 让那句话念的是"哪几个键有问题"而不是同一串重复 N 遍；
        // 而条数另存一枚（[unrecognizedCount]），屏上计数那一项按条数念。
        unrecognizedSites = unrecognized.distinct().sorted(),
        unrecognizedCount = unrecognized.size,
        droppedUnusable = dropped,
        readAtMillis = nowMillis,
    )
}

/**
 * 「没有每站预算」的哨兵值。传它给 [DesktopGalleryStatusBar] 的 `budgetMs` ⇒ 末句整条不念。
 *
 * 抽成常量而不是让调用点写 `-1`：这个数在桌面侧**只有一个含义**（"这一屏不涉及站"），
 * 而 `budgetMs` 是 `Long`，写裸 `-1` 会被下一个人读成"预算是负一"。
 */
internal const val STATUS_NO_BUDGET_MS = -1L
