package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.domain.GalleryDailyFeed
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
 * 2. **列数走 [DesktopGalleryMetrics.imageWallColumnCount]**，默认 1080 窗口档的读数是 **4 列**；
 *    设计稿上画的 5 列**不抄**（那条由 `DesktopWidthCaliberDriftTest` 与 `DesktopGalleryHomeSectionsTest` 钉住）。
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

        is DailyState.Ready -> DailyWall(current.daily)
    }
}

@Composable
private fun DailyWall(daily: GalleryDailyFeed.Daily) {
    val merged = daily.merged
    val perSite = merged.perSite.entries.joinToString("、") { "${it.key.displayName} ${it.value} 张" }
    val dropped = listOfNotNull(
        merged.droppedUnusable.takeIf { it > 0 }?.let { "不可摆 $it 条" },
        merged.droppedDuplicates.takeIf { it > 0 }?.let { "重复 $it 条" },
        merged.videos.takeIf { it > 0 }?.let { "其中视频 $it 条" },
    ).joinToString(" · ")

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("每日热门 · 进屏 ${merged.posts.size} 张 · 各站：$perSite")
        // 三站的「热门」不是同一件事（实测口径在 GalleryDailyFeed 的类注释里）：只有 yande.re 是日榜，
        // 另两站没有按天的视图。所以日期只描述它自己那一路，不许拿来描述整屏。
        Text("yande.re 取的是 ${daily.date} 的日榜；Gelbooru 与 Safebooru 没有日榜端点，取的是高分池")
        if (dropped.isNotBlank()) Text("已滤掉：$dropped")
        // 缺席的站点名：名单逐字来自 failures.keys，不在这外面再数一遍。
        if (daily.failures.isNotEmpty()) {
            Text(
                "这一轮没给内容的站：" + daily.failures.entries.joinToString(" · ") { "${it.key.displayName}：${it.value}" },
            )
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = DesktopGalleryMetrics.imageWallColumnCount(maxWidth.value)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.wallGridGap),
                verticalArrangement = Arrangement.spacedBy(DesktopGalleryMetrics.wallGridGap),
            ) {
                items(merged.posts, key = { "${it.site.routeKey}-${it.id}" }) { post ->
                    DesktopGalleryPostTile(post)
                }
            }
        }
    }
}

/**
 * 一张卡：图位 + 元数据。
 *
 * 几何都取现成口径，不造新数：图位比例 3:4 来自设计稿的 `.art-card .im{aspect-ratio:3/4}`（`:154`），
 * 圆角 8 来自稿的 `--r-card:8px`（`:21`）。刻意不挂 `clickable` —— 桌面没有画廊详情页，
 * 画成能点的样子就是一枚假开关。
 */
@Composable
private fun DesktopGalleryPostTile(post: GalleryPost) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 占位格：本轮用站名代替图片
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .background(Color(0xFF333333), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                post.site.displayName,
                color = Color(0xFF8B8B8B),
                fontSize = 13.sp,
                maxLines = 1
            )
        }
        Text(post.author.ifBlank { "站方没给画师" }, maxLines = 1)
        Text("分数 ${post.score} · 标签 ${post.tagList.size} 枚", maxLines = 1)
    }
}

private sealed interface DailyState {
    data object Loading : DailyState
    data class Failed(val reason: String) : DailyState
    data class Ready(val daily: GalleryDailyFeed.Daily) : DailyState
}

// 两个色值同样取稿的深色档阶梯（`--card-hov:#333333` 与 `--t3:#8B8B8B`），浅色档映射归 S5。
private val TilePlaceholder = Color(0xFF333333)
private val PlaceholderText = Color(0xFF8B8B8B)
