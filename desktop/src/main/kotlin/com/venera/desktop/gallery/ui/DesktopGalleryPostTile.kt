package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import io.github.composefluent.component.Text

/**
 * 桌面**全站唯一**的画廊卡位画法 —— 日榜墙、收藏墙、搜索墙三处共用它。
 *
 * ## 为什么不各画一张（这是本仓判过负的"第二套布局系统"在最小尺度上的重演）
 *
 * 2026-10-04 之前它是 `DesktopDailyPane.kt` 里的 `private` 件。收藏域与搜索域接进来时，
 * 三处各画一张的代价不是"多几百行"，而是**同一条口径会长成三份**：
 * 比例（3:4 / wide 16:10）、圆角、描边那一档色、遮罩的渐变停靠点、
 * 以及最要紧的**图位三态说法**。任何一处漏改，用户就会看到"同一张图在日榜里是 3:4、
 * 在收藏里是方角、坏图时其中一屏什么都不说"。所以它提到这里，三处一起吃。
 *
 * 迁移时**只改了可见性**（`private` → `internal`）与所在文件，逻辑与色值一字未动 ——
 * 提件不是重写，改动越小越不会顺手引入新观感。
 *
 * ## 一张卡：图位 + 压在图底的常驻遮罩
 *
 * 几何取现成口径，不造新数：常态图位比例 3:4 来自设计稿 `.art-card .im{aspect-ratio:3/4}`（`:187`），
 * [wideFirst] 那档 16:10 来自 `.art-card.wide .im{aspect-ratio:16/10}`（`:188`），
 * 圆角 8 来自稿的 `--r-card:8px`（`:21`）。
 *
 * ⚠️ 这里**曾经**写着「刻意不挂 `clickable` —— 桌面没有画廊详情页，画成能点的样子就是一枚假开关」，
 * 那是对的旧裁决（当时详情面板还没有，挂上去确实是假开关）。第十一批 P0 把详情浮层接上了，
 * [onClick] 于是成了必填参数（**不给默认值**：漏传不报错的形状正是"某一屏的卡点不动"，
 * 与第五批那枚墙参数同一条教训），旧那句随之下架。点击面是**整张卡**而不是卡底那两行字 ——
 * 挂在小件上就成了"看得见的一圈字能点、图不能点"。
 *
 * ## 描边不是阴影
 *
 * 稿 `.art-card{border:1px solid var(--stroke)}`（`:185`）—— 全稿卡片一律用描边不用阴影，
 * 而 `--stroke` = `#353535` 正是 [DesktopTheme.SurfaceRaised]。
 * ⚠️ 曾经这里还写着「（`--card-hov` 同一颗值）」—— 是错的，`--card-hov` 是 `#333333`，
 * 比 `--stroke` 的 `#353535` **暗一档**（两处都在稿 `:10`）。代码取 `#353535` 是对的，
 * 错的只是那句注释。所以这里复用它、**不新增第六档色**。描边画在 layout 边界内，不影响宽度口径。
 *
 * ## 遮罩为什么是**常驻**而不是 hover 才有
 *
 * 照稿 `.art-card .ov`（`:189`）走：文字压在图上，若只在 hover 时才出遮罩，键盘用户与触屏用户
 * 永远读不到那两行 —— 而且一屏几十张卡，鼠标扫过时反复明灭本身就是闪烁。所以遮罩常驻。
 * 渐变从透明到 [DesktopTheme.CardOverlayEnd]（`#80000000` 半透明黑），白字压在上面。
 *
 * ## 四档图位状态，**不许留一档"什么都没有"**
 *
 * 0. `masked = true`（分级遮挡档）→ 「分级遮挡」+ 站名，**而且根本不发起取图**：
 *    一张注定要盖住的图，没必要先花一次流量与一次解码再盖住它。
 *    这一档排在"站方没给缩略档"之前判 —— 反过来会让被遮挡的条目谎报"站方没给"，
 *    那是把"我们不给看"说成"站方没有"，两个方向都算错报；
 * 1. 地址是空串 → 「站方没给缩略档」+ 站名（这是**站方没给**，不是我们没取到）；
 * 2. 加载中 → 站名 + 一句"正在取图"（所以灰块至少知道自己是什么）；
 * 3. 加载失败 → 「图没加载出来」+ 站名 + 域名的头几段（便于用户报障时定位是哪张）。
 *
 * ⚠️ 第 3 档**不许**静默重试到成功：那是"悄悄变成正常"的另一种形状。
 *
 * ⚠️ 第 1 档那句文案里的"缩略档"是**旧说法**。第五批起这一格取哪一档由用户的
 * 「预览清晰度」定（preview / sample 两档，判据在共享那颗 `GallerySettingsModel.wallUrl`），
 * 所以严格讲这一档是"站方没给这一档的图"。留原句是因为它短、且两档都空时才是这一档；
 * 要改文案就连着 `TilePhase.Failed` 那句一起改，别只改一半。
 */
@Composable
internal fun DesktopGalleryPostTile(
    post: GalleryPost,
    masked: Boolean,
    quality: GalleryPreviewQuality,
    aiBadge: Boolean,
    onClick: (GalleryPost) -> Unit,
    wide: Boolean = false,
) {
    // 取哪一档**不在这里判**：交给 `tileImageRequest`（它再转给共享那颗 `GallerySettingsModel.wallUrl`）。
    // ⚠️ 曾经这里写死 `post.previewUrl` —— 那是"墙上摆哪一档"这一档偏好恒不生效的形状，
    // 而且它把两条判据（视频只吃静帧、LARGE 取不到退 preview）抄在了桌面这一侧。
    // `null` 只有一种来处：这一张被分级挡住了，**连发起取图都不该有**。
    val url = tileImageRequest(post, masked, quality)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(if (wide) 16f / 10f else 3f / 4f)
            .clip(RoundedCornerShape(8.dp))
            .background(DesktopTheme.ImagePlaceholder)
            .border(1.dp, DesktopTheme.SurfaceRaised, RoundedCornerShape(8.dp))
            // 点击面 = **整张卡**（几何与描边都在它之内，所以挂这一层不改任何尺寸口径）。
            // 挡住的那一档同样能点：点开的面板里那一格照旧是「分级遮挡」，
            // `masked` 一路跟着卡进面板，这里不给守卫开绕出口。
            .clickable { onClick(post) },
    ) {
        if (url == null) {
            // 这一档**不进下面那条 else**：进了就会起 `AsyncImage`，
            // 而这一张的内容正是"这台机器的这个人不该看到"。
            TileFallback("分级遮挡", post.site.displayName)
        } else if (url.isBlank()) {
            TileFallback("站方没给缩略档", post.site.displayName)
        } else {
            var phase by remember(url) { mutableStateOf<TilePhase>(TilePhase.Loading) }
            // ⚠️ coil3 的三枚回调是**具名参数**（`onLoading` / `onSuccess` / `onError`），
            // 不是 `onSuccessListener` / `onErrorListener` 那一族 —— 那是 coil2 的 modifier 扩展，
            // 写上去会红。也不是 `placeholder` / `error` 那两枚 Painter 形参：
            // 我们要的是**知道自己处于哪一档**，不是画一张占位图。
            AsyncImage(
                model = url,
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
                TilePhase.Failed -> TileFallback("图没加载出来", "${post.site.displayName} · ${hostHint(url)}")
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
            // 分数与标签两半**各自可缺**（判据在 [desktopCardMetricLine]）：
            // `score` 是非空 Int、缺省 0，而 Gelbooru 站方真会给 `"score":0` ⇒
            // 「分数 0」把"零分"与"这一站没给这个数"念成了同一句话。两半都没有就整行不摆。
            desktopCardMetricLine(post)?.let { metrics ->
                Text(
                    metrics,
                    color = DesktopTheme.TextPrimary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
        // 「AI」角标：文案与 Android 那颗 `GalleryAiPill` 同串（`AI_BADGE_TEXT`，由用例逐字回核），
        // 判据同一条（站方没打标一律不摆，不靠画风推断）。
        //
        // 位置与 Android 不同（那颗在左上）：桌面这一张的左上被标题占了、右上是站点胶囊、
        // 左下是作者与分数那两行，右下角是这一张上唯一还空着的一角。**刻意不可点** ——
        // 点下去要的是"只看这一站/只看 AI"，桌面还没有筛选态的出口，做了就是假按钮。
        if (aiBadgeFor(post, aiBadge)) {
            Text(
                AI_BADGE_TEXT,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(DesktopTheme.OverlayScrim, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                color = DesktopTheme.TextPrimary,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

/**
 * 一面墙：列数走 [desktopWallColumnCount]（AUTO 才吃宽度，TWO/THREE 是用户覆盖），首卡跨 2 列。
 *
 * 三枚档（列数 / 清晰度 / AI 角标）在这里**一次收下、逐张传给卡位**，不在卡位里各自去读偏好：
 * 那样一面墙上会有几十次 `collectAsState`，而"同一面墙上第 1 张与第 20 张不同档"这种形状
 * 恰恰是最难看出来的错。
 *
 * ## 为什么是**一颗**而不是三处各摆一次 `LazyVerticalGrid`
 *
 * 与 [DesktopGalleryPostTile] 同一条理由，只是这次差的是**列数推导**：
 * 那一颗是"卡片长什么样"，这一颗是"一面墙摆几列"。推导在
 * [DesktopGalleryMetrics.imageWallColumnCount] 里就一处（1440 档读数 6 列，与稿 `:338`/`:657`
 * 的「内容净宽 1168 → 6 列」逐字相符）。三处各写一遍 `BoxWithConstraints { … }`，
 * 将来改下限（现在是 3）就会漏掉一处，而漏掉的那处症状是"同一面墙在两个域里列数不同"。
 *
 * ## [wideFirst] 为什么是**参数**而不是一律跨 2 列
 *
 * 只有日榜墙有那个"今日推荐首卡大图位"的稿（`.art-card.wide`，`:186`/`:188`）；
 * 收藏墙与搜索墙是均匀网格。做成参数而不是两件，是为了让"要不要首卡大图位"这个决定
 * 留在**调用点**（看得见），而不是藏在墙自己的默认参数里（看不见）。
 *
 * ## 键用 `"${site}-${id}"` 而不是 `id`
 *
 * 三站各自独立编号，同一个 id 在三站是三张不同的图 —— 用裸 id 当键会让三站的图互相顶掉。
 *
 * ## 收的是 [DesktopWall] 而不是 `List<GalleryPost>`
 *
 * 墙自己**不判分级**：判据在 [DesktopGalleryWallPolicy] 那一层算完再交进来（纯函数，能被单测取值）。
 * 让墙自己判，等于三面墙各长一把过滤 —— 而"同一张图在日榜被挡、在收藏里摆出来"正是这一层要禁的事。
 */
@Composable
internal fun DesktopGalleryPostWall(
    wall: DesktopWall,
    columnMode: GalleryColumnMode,
    quality: GalleryPreviewQuality,
    aiBadge: Boolean,
    /**
     * 点了一张卡。交出的是**整批 + 序号**（[DesktopWallSelection]）而不是裸 post ——
     * 两件事各占一半理由：`masked` 那一档必须跟着进浮层（否则"墙上挡住的那张，点开就看到"
     * 就是第四批那层守卫的绕出口），而"第 3 / 40 张"里那个 40 只有墙自己知道
     *（守卫挡掉的不算、续页追加的要算）。
     *
     * 同样**必填、不给默认值**：五处调用点漏一处，形状就是"某一屏的卡点不动"。
     */
    onPick: (DesktopWallSelection) -> Unit,
    /**
     * 墙**前面**那几排（节头、hero、缺席读数…），跟着同一颗滚动容器一起滚。
     *
     * 必填、不给默认值：带默认值时"该有节头的那一屏漏传"不报错。
     * 为什么把这颗口子开在墙上而不是让调用方自己套一层滚动容器：
     * `LazyVerticalGrid` 自己就是滚动容器，外面再挂 `verticalScroll` 会**整屏崩**（手册 §23.3），
     * 而"给墙算一个定高"对照片墙是一条会漂的账（行数随窗宽变）。
     * 于是"整页下滑"只剩这一条路 —— 前置内容做成**跨整行的 grid item**，全页一个滚动容器。
     */
    lead: LazyGridScope.() -> Unit,
    modifier: Modifier = Modifier,
    wideFirst: Boolean = false,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 三枚新参数一律**必填**：带默认值时漏传不报错，而"漏传"的形状正好是
        // "同一份设置在某一屏上静默不生效" —— 那与第四批修掉的"墙不过守卫"是同一族缺陷。
        val columns = desktopWallColumnCount(maxWidth.value, columnMode)
        // 取证种子（`--detail`）：起窗之后自己点开这一屏的第一张卡。
        // ⚠️ 放在 `LaunchedEffect(wall.cards)` 里而不是组合期直接调：组合期写状态就是
        //   "每帧新实例 × 引用比较"那一族（手册 §摆放期写矩形），而第一帧 `cards` 还是空的。
        //   默认那颗种子是 `false`（判据 `DesktopGalleryDetailP1Test` ⑨），所以不带参数时这条链不通。
        if (LocalDesktopWallSeedFirst.current) {
            LaunchedEffect(wall.cards) {
                if (wall.cards.isNotEmpty()) onPick(DesktopWallSelection(wall.cards, 0))
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.wallGridGap),
            verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.wallGridGap),
        ) {
            // 前置那几排先入列：`maxLineSpan` 是"跨满一行"，正是它们要的（与首卡跨 2 列不是一回事）。
            lead()
            // ⚠️ 用 `itemsIndexed` 而不是 `items`：首卡要跨 2 列，而"是不是第一张"只有 index 知道。
            //
            // `coerceAtMost(2)` 而不是直接写 2：下限公式是 3（`DesktopGalleryMetrics.kt` 的
            // `coerceAtLeast(3)`），所以 2 恒 ≤ `maxLineSpan`；显式写下这个上限是为了
            // 哪天下限被调到 2 时不会静默跨满整行。**不写 `maxLineSpan`** —— 那是"跨满一行"，
            // 与稿的"跨 2 列"不是一回事。
            itemsIndexed(
                items = wall.cards,
                key = { _, card -> "${card.post.site.routeKey}-${card.post.id}" },
                span = { index, _ ->
                    if (wideFirst && index == 0) GridItemSpan(maxLineSpan.coerceAtMost(2)) else GridItemSpan(1)
                },
            ) { index, card ->
                DesktopGalleryPostTile(
                    card.post,
                    masked = card.masked,
                    quality = quality,
                    aiBadge = aiBadge,
                    // 交出**整批与序号**：`masked` 要跟着进浮层，而页码那个"共几张"只有墙知道。
                    onClick = { onPick(DesktopWallSelection(wall.cards, index)) },
                    wide = wideFirst && index == 0,
                )
            }
        }
    }
}

/**
 * 墙上那两笔"被挡了多少"的读数，一行。空表就**一行都不画** ——
 * 没有东西被挡的时候摆一句"没有内容被屏蔽"是把不存在的事说一遍，
 * 而一屏几十张卡底下多一行常显文字，读的人只会以为哪里出了事。
 *
 * ⚠️ 这一行是**每一面墙都要摆的那一颗件**（批 37）：拼法住在 [desktopWallGuardReading] 里，
 * 各 pane 只许调这一颗，不许自己 `guardFragments().joinToString(…)` ——
 * 从前四处各写一遍，其中单人页把那一串写进了"整墙空了"那一格，非空时屏上就没有任何一句话说少了谁。
 */
@Composable
internal fun DesktopWallGuardLine(wall: DesktopWall, modifier: Modifier = Modifier) {
    val reading = desktopWallGuardReading(wall)
    if (reading.isEmpty()) return
    Text(
        reading,
        color = DesktopTheme.TextSecondary,
        fontSize = 12.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(bottom = 6.dp),
    )
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
