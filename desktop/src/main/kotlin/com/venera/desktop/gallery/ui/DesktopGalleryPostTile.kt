package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
 * 圆角 8 来自稿的 `--r-card:8px`（`:21`）。刻意不挂 `clickable` —— 桌面没有画廊详情页，
 * 画成能点的样子就是一枚假开关。
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
 * ## 三档图位状态，**不许留一档"什么都没有"**
 *
 * 1. `previewUrl` 空串 → 「站方没给缩略档」+ 站名（这是**站方没给**，不是我们没取到）；
 * 2. 加载中 → 站名 + 一句"正在取图"（所以灰块至少知道自己是什么）；
 * 3. 加载失败 → 「图没加载出来」+ 站名 + 域名的头几段（便于用户报障时定位是哪张）。
 *
 * ⚠️ 第 3 档**不许**静默重试到成功：那是"悄悄变成正常"的另一种形状。
 */
@Composable
internal fun DesktopGalleryPostTile(post: GalleryPost, wide: Boolean = false) {
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
            // ⚠️ coil3 的三枚回调是**具名参数**（`onLoading` / `onSuccess` / `onError`），
            // 不是 `onSuccessListener` / `onErrorListener` 那一族 —— 那是 coil2 的 modifier 扩展，
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

/**
 * 一面墙：列数走 [DesktopGalleryMetrics.imageWallColumnCount]，首卡跨 2 列。
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
 */
@Composable
internal fun DesktopGalleryPostWall(
    posts: List<GalleryPost>,
    modifier: Modifier = Modifier,
    wideFirst: Boolean = false,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = DesktopGalleryMetrics.imageWallColumnCount(maxWidth.value)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.wallGridGap),
            verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.wallGridGap),
        ) {
            // ⚠️ 用 `itemsIndexed` 而不是 `items`：首卡要跨 2 列，而"是不是第一张"只有 index 知道。
            //
            // `coerceAtMost(2)` 而不是直接写 2：下限公式是 3（`DesktopGalleryMetrics.kt` 的
            // `coerceAtLeast(3)`），所以 2 恒 ≤ `maxLineSpan`；显式写下这个上限是为了
            // 哪天下限被调到 2 时不会静默跨满整行。**不写 `maxLineSpan`** —— 那是"跨满一行"，
            // 与稿的"跨 2 列"不是一回事。
            itemsIndexed(
                items = posts,
                key = { _, post -> "${post.site.routeKey}-${post.id}" },
                span = { index, _ ->
                    if (wideFirst && index == 0) GridItemSpan(maxLineSpan.coerceAtMost(2)) else GridItemSpan(1)
                },
            ) { index, post ->
                DesktopGalleryPostTile(post, wide = wideFirst && index == 0)
            }
        }
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
