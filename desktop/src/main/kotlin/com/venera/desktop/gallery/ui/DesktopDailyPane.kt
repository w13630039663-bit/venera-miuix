package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * 3. **卡片不可点，卡位上不摆像素**。两件事同一句理由：桌面既没有画廊单页与大图页，也没有图库的取图件
 *    （`GalleryImageLoader` 在 `desktop/build.gradle.kts` 的排除清单里，成因写着 coil3 + data.prefs + data.network）。
 *    也不在 UI 壳里另起一条 OkHttp 取图路 —— 那会在桌面装出"看着像取图层、实际少了 UA 与限流"的假路，
 *    S1 已经把这种半做过一次判断（`docs/rounds/windows-gallery-home-s1-2026-10-03.md` §「三件故意没做」第 1 条）。于是卡位摆的是**这一条路真拿得到的东西**（站方给的元数据），
 *    而"图片不显示"这件事在节头说出来一次。
 *
 * ⚠️ 出图不在本批的验收面上（S2-B 计划 §3）：这两颗 pane 此刻还没有消费点，`VeneraDesktop.kt` 的接线归集成。
 */
@Composable
internal fun DesktopDailyPane(ports: GalleryPorts) {
    var attempt by remember(ports) { mutableStateOf(0) }
    // 种子只在"这一屏立起来"时取一次：同一个种子必得同一个序列（GalleryMerge 的口径），
    // 否则组合一重建整屏换序，读起来像"页面自己抖了一下"。Android 那侧种子由 ViewModel 持有，
    // 桌面这轮还没有 VM，所以记在 remember 里；「换一批」用 attempt 递进，是一笔真动作。
    val seed = remember(ports) { System.currentTimeMillis() }
    var state: DailyState by remember(ports, seed, attempt) { mutableStateOf(DailyState.Loading) }

    LaunchedEffect(ports, seed, attempt) {
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
    LaunchedEffect(ports, seed, attempt, identityReady) {
        if (identityReady) attempt += 1
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
    val perSite = merged.perSite.entries.joinToString("、") { "${it.key.displayName} ${it.value} 张" }
    val dropped = listOfNotNull(
        merged.droppedUnusable.takeIf { it > 0 }?.let { "不可摆 $it 条" },
        merged.droppedDuplicates.takeIf { it > 0 }?.let { "重复 $it 条" },
        merged.videos.takeIf { it > 0 }?.let { "其中视频 $it 条" },
    ).joinToString(" · ")

    // 段头 chips 取**当页**标签频次（[desktopTopTags]），随机数那样飘的东西不要。
    // `remember(posts)` 而不是每次重组都算：几十张卡的标签表扫一遍不贵，但没必要跟着动画重组重算。
    val (tags, hiddenTagCount) = remember(merged.posts) { desktopTopTags(merged.posts) }
    val segments = remember { desktopGallerySegments() }
    var segmentIndex by remember { mutableStateOf(0) }
    var segNotice by remember { mutableStateOf<String?>(null) }
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

        // ② 内容本体：hero → 缺席 → 节头 → 墙。整块吃剩余高度，状态栏才贴得住窗底。
        Column(
            Modifier
                .weight(1f)
                // ⚠️ 写 `start/end/bottom` 而不是 `horizontal + bottom` —— Compose 的 `padding`
                // 没有那个具名组合（`horizontal` 只与 `vertical` 配对），写上去编译不过。
                .padding(start = pad, end = pad, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DesktopDailyHero(
                daily = daily,
                onShuffle = onShuffle,
            )

            // ③ 缺席必须先说 —— 它决定"这一屏为什么只有两站"，所以它**留在墙上方**而不是页脚。
            if (daily.failures.isNotEmpty()) {
                Text(
                    "这一轮没给内容的站：" +
                        daily.failures.entries.joinToString(" · ") { "${it.key.displayName}：${it.value}" },
                    color = DesktopTheme.AccentBeni,
                    fontSize = 12.sp,
                )
                // S4：缺席原因指向的那件事要能在这里修。
                // 条件刻意只判"这一站缺席"而不判缺席原因的字面：**原因句会改**（站方改文案、
                // 换措辞），拿字面去挂钩会让面板在某次措辞调整后静默消失。
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

            // ④ 节头（稿 `.sechead` `:140-141`）：节名 + 进屏读数。
            //    ⚠️ 稿这里右边还有一枚「查看更多」（`.sechead .more`）—— **不画**：桌面今天的墙
            //    就是这一轮取到的全部，"查看更多"按下去没有下一页可翻，是一枚假开关。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "精选推荐",
                    color = DesktopTheme.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    " · 进屏 ${merged.posts.size} 张 · $perSite",
                    color = DesktopTheme.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ② 墙本体。列数推导与首卡跨 2 列都在 [DesktopGalleryPostWall] 里 ——
            // 收藏墙与搜索墙吃的是同一颗，三处各摆一次 `LazyVerticalGrid` 就会长出三份列数口径。
            DesktopGalleryPostWall(
                posts = merged.posts,
                wideFirst = true,
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
