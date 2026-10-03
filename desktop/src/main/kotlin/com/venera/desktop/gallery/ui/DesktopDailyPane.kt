package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import coil3.compose.AsyncImage
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.GelbooruCredentialState
import com.venera.compose.gallery.domain.GalleryDailyFeed
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

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
            onSuccess = { DailyState.Ready(it) },
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

        is DailyState.Ready -> DailyWall(current.daily, ports, onShuffle = { attempt += 1 })
    }
}

@Composable
private fun DailyWall(
    daily: GalleryDailyFeed.Daily,
    ports: GalleryPorts,
    onShuffle: () -> Unit,
) {
    val merged = daily.merged
    val perSite = merged.perSite.entries.joinToString("、") { "${it.key.displayName} ${it.value} 张" }
    val dropped = listOfNotNull(
        merged.droppedUnusable.takeIf { it > 0 }?.let { "不可摆 $it 条" },
        merged.droppedDuplicates.takeIf { it > 0 }?.let { "重复 $it 条" },
        merged.videos.takeIf { it > 0 }?.let { "其中视频 $it 条" },
    ).joinToString(" · ")

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ① 精简 Header：只留「标题 + 计数」这一行。
        // ⚠️ 上一轮把三段说明（各站条数 / 三站口径差别 / 已滤掉几条）全堆在正文顶上，
        // 结果是"进屏 40 张"这种读数占掉了首屏最好的位置，而它们是**页脚该说的话**。
        // 现在：一条 Header 报数 + 一条状态条报缺席与已滤掉的，其余移到墙下面。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "每日热门",
                color = DesktopTheme.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                " · 进屏 ${merged.posts.size} 张 · $perSite",
                color = DesktopTheme.TextSecondary,
                fontSize = 12.sp,
            )
        }

        // ② Hero 区：左侧大位 + 右侧两张小卡（稿 `.top` 的非对称，`:364`）。
        //    `onShuffle` 是形参而不是就地改 attempt —— 「重排」背后是真动作
        //    （`loadDaily(seed)` 换种子本地重排），而 attempt 属于这一层的组合状态；
        //    让子件直接改它就是"孙件伸手去改孙孙件的状态"，换一颗调用点就编译不过。
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

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val columns = DesktopGalleryMetrics.imageWallColumnCount(maxWidth.value)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.wallGridGap),
                verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.wallGridGap),
            ) {
                // ⚠️ 用 `itemsIndexed` 而不是 `items`：首卡要跨 2 列（稿 `.art-card.wide{grid-column:span 2}` `:186`），
                // 而"是不是第一张"只有 index 知道。
                //
                // `coerceAtMost(2)` 而不是直接写 2：下限公式是 3（`DesktopGalleryMetrics.kt` 的
                // `coerceAtLeast(3)`），所以 2 恒 ≤ `maxLineSpan`；显式写下这个上限是为了
                // 哪天下限被调到 2 时不会静默跨满整行。**不写 `maxLineSpan`** —— 那是"跨满一行"，
                // 与稿的"跨 2 列"不是一回事。
                //
                // `key` 沿用原串不变 ⇒ 换列数（4↔5↔6）时 LazyGrid 的滚动锚点按 key 保持，
                // 不会发生"内容跳到另一张图"那种漂移。
                itemsIndexed(
                    items = merged.posts,
                    key = { _, post -> "${post.site.routeKey}-${post.id}" },
                    span = { index, _ ->
                        if (index == 0) GridItemSpan(maxLineSpan.coerceAtMost(2)) else GridItemSpan(1)
                    },
                ) { index, post ->
                    DesktopGalleryPostTile(post, wide = index == 0)
                }
            }
        }

        // ④ 页脚：接住从 Header 移下来的三段读数（稿 `.status`，`:470`）。
        // 放这里而不是顶部，因为它们是**解释**而不是**内容** —— 首屏该给图。
        // ⚠️ 三站口径那条必须在页脚（不是删掉）：只有 yande.re 是日榜，另两站取的是高分池，
        // 不说清就会长成"这三站都是今天的热门"这种假读数。
        Text(
            "yande.re 取的是 ${daily.date} 的日榜；Gelbooru 与 Safebooru 没有日榜端点，取的是全站高分池" +
                if (dropped.isNotBlank()) "　已滤掉：$dropped" else "",
            color = DesktopTheme.TextTertiary,
            fontSize = 11.sp,
            maxLines = 2,
        )
    }
}

/**
 * 一张卡：**图位 + 压在图底的常驻遮罩**。
 *
 * 几何取现成口径，不造新数：常态图位比例 3:4 来自设计稿 `.art-card .im{aspect-ratio:3/4}`（`:187`），
 * [wide] 那档 16:10 来自 `.art-card.wide .im{aspect-ratio:16/10}`（`:188`），
 * 圆角 8 来自稿的 `--r-card:8px`（`:21`）。刻意不挂 `clickable` —— 桌面没有画廊详情页，
 * 画成能点的样子就是一枚假开关。
 *
 * ## 描边不是阴影
 *
 * 稿 `.art-card{border:1px solid var(--stroke)}`（`:185`）—— 全稿卡片一律用描边不用阴影，
 * 而 `--stroke` = `#353535` 正是已有的 [DesktopTheme.SurfaceRaised]（`--card-hov` 同一颗值），
 * 所以这里复用它、**不新增第六档色**。描边画在 layout 边界内，不影响任何宽度口径。
 *
 * ## 遮罩为什么是**常驻**而不是 hover 才有
 *
 * 照稿 `.art-card .ov`（`:190`）走：文字压在图上，若只在 hover 时才出遮罩，键盘用户与触屏用户
 * 永远读不到那两行 —— 而且一屏几十张卡，鼠标扫过时反复明灭本身就是闪烁。所以遮罩常驻。
 * 渐变从透明到 [DesktopTheme.CardOverlayEnd]（`#80000000` 半透明黑），白字压在上面。
 *
 * ## 三档图位状态，**不许留一档"什么都没有"**
 *
 * 上一轮的坏形：只处理了"站方没给缩略档"（空串），于是 Coil 在**加载中**与**加载失败**两档
 * 什么都不画 —— 纯灰块与"这张图坏了"**长得一模一样**，用户在窄栏里一屏看到两个灰块
 * 只能猜。现在三档各有各的说法：
 * 1. `previewUrl` 空串 → 「站方没给缩略档」+ 站名（这是**站方没给**，不是我们没取到）；
 * 2. 加载中 → 站名 + 一句"正在取图"（所以灰块至少知道自己是什么）；
 * 3. 加载失败 → 「图没加载出来」+ 站名 + 域名的头 6 位（便于用户报障时定位是哪张）。
 *
 * ⚠️ 第 3 档**不许**静默重试到成功：那是"悄悄变成正常"的另一种形状。
 */
@Composable
private fun DesktopGalleryPostTile(post: GalleryPost, wide: Boolean = false) {
    // 网格档按用途取 [GalleryPost.previewUrl]（`GalleryPost.kt:66`「网格缩略档」，三站同口径）。
    // ⚠️ 不拿 `largeUrl` / `fileUrl` 顶：那些是原图（yande.re 单条实测可达 6.6 MB，
    // 见 `GalleryPost.kt:22`），一屏几十张就是几百 MB 的下载与解码，而墙上只画 3:4 的小格。
    val preview = post.previewUrl
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(if (wide) 16f / 10f else 3f / 4f)
            .clip(RoundedCornerShape(8.dp))
            .background(DesktopTheme.ImagePlaceholder)
            .border(1.dp, DesktopTheme.SurfaceRaised, RoundedCornerShape(8.dp)),
    ) {
        if (preview.isBlank()) {
            TileFallback("站方没给缩略档", post.site.displayName)
        } else {
            var phase by remember(preview) { mutableStateOf<TilePhase>(TilePhase.Loading) }
            // ⚠️ coil3 的三枚回调是**具名参数**（`onLoading` / `onSuccess` / `onError`，
            // javap 读 coil-compose-core-jvm-3.6.2 的 AsyncImageKt 实测），不是
            // `onSuccessListener` / `onErrorListener` 那一族 —— 那是 coil2 的 modifier 扩展，
            // 写上去会红。也不是 `placeholder` / `error` 那两枚 Painter 形参：
            // 我们要的是**知道自己处于哪一档**，不是画一张占位图。
            AsyncImage(
                model = preview,
                contentDescription = "${post.site.displayName} 的作品",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { phase = TilePhase.Loaded },
                onError = { phase = TilePhase.Failed },
            )
            when (phase) {
                // 加载中也摆字：纯灰块与"坏图"同形是上一轮实测到的缺陷。
                TilePhase.Loading -> TileFallback("正在取图", post.site.displayName)
                // ⚠️ 只点名**哪一档**出了事，不回显整条 URL：那张图能显示在屏幕上，
                // 链接也就等于摆出去了。这里只留 host 的头几段作定位线索。
                TilePhase.Failed -> TileFallback("图没加载出来", "${post.site.displayName} · ${hostHint(preview)}")
                TilePhase.Loaded -> Unit
            }
        }

        // 常驻遮罩 + 白字，压在图底（稿 `.art-card .ov` + `.ttl` / `.cap`）。
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        // 上半段完全透明 ⇒ 不挡画面主体；下半段才压深。
                        0.0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1.0f to DesktopTheme.CardOverlayEnd,
                    ),
                ),
        )
        // 标题层（稿 `.art-card .ttl` `:193`）：左 12 / 上 12 / **右 60** / 13px / 600 / 白字。
        // 那个 `end = 60` 正是稿给右上角站点胶囊留的位，所以下面那颗胶囊一字不用动。
        //
        // ⚠️ **文案取首个标签，不是作品名**：[GalleryPost] 没有 `title` 字段（三站 API 都不给），
        // 稿上摆的是作品名且旁边标了「虚构占位」。按本仓"不许把不知道说成知道"的纪律，
        // 这里摆**这一条路真拿得到的东西**：booru 系站点上标签就是作品的身份，三站同口径。
        // 标签为空的那一档明说"站方没给"，不编造。作者名已在底部第一行，这里不重复。
        Text(
            post.tagList.firstOrNull() ?: "站方没给标签",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp, end = 60.dp),
            color = DesktopTheme.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                post.author.ifBlank { "站方没给画师" },
                color = DesktopTheme.TextPrimary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "分数 ${post.score} · 标签 ${post.tagList.size} 枚",
                color = DesktopTheme.TextPrimary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 站点胶囊（稿 `.src`）：站别是身份的一部分，三站各 id 独立编号，同号必是两张不同的图。
        Text(
            post.site.displayName,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .background(DesktopTheme.OverlayScrim, RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
            color = DesktopTheme.TextPrimary,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

/** 图位三态。刻意做成 sealed 而不是布尔 —— 下一轮若要加"图过大降采样"时是第四档，不是插在中间。 */
private enum class TilePhase { Loading, Loaded, Failed }

/** 图位上那两行字：上句说**出了什么事**，下句说**是哪一站**。压在深底上所以用次要档。 */
@Composable
private fun TileFallback(reason: String, site: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(reason, color = DesktopTheme.OnImagePlaceholder, fontSize = 12.sp, maxLines = 1)
        Text(site, color = DesktopTheme.OnImagePlaceholder, fontSize = 11.sp, maxLines = 1)
    }
}

/** 只取 host 的头两段作定位线索，不回显整条 URL（理由见 [TilePhase.Failed] 那一段）。 */
private fun hostHint(url: String): String =
    url.removePrefix("https://").removePrefix("http://").substringBefore('/').take(18)

private sealed interface DailyState {
    data object Loading : DailyState
    data class Failed(val reason: String) : DailyState
    data class Ready(val daily: GalleryDailyFeed.Daily) : DailyState
}
