package com.venera.desktop.gallery.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.data.db.FavoriteItem
import com.venera.compose.data.db.LocalFavoritesManager
import com.venera.engineprobe.DesktopJsHost
import com.venera.engineprobe.EngineSession
import io.github.composefluent.component.Text
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 漫画域 —— 2026-10-04 从 `VeneraDesktop.kt` 回收的那一批链路在这里成了真页面。
 *
 * ## 回收前它们是什么
 *
 * 原先 `VeneraDesktop()` 里躺着一组状态：`cards` / `covers` / `session` / `open` / `pages` /
 * `pageStatus` / `showFavorites` / `favTree` —— 三个 `LaunchedEffect` 忙着往里写，
 * 而这批东西**一个消费点都没有**：那颗组合的最后一行只有 `DesktopGalleryHome(ports)`。
 *
 * 最坏的一处不是"写了没用"，是**它在真花钱**：`main()` 一开口就去装载源脚本、拉探索页、
 * 然后串行拉 30 张封面图。那是三十来次真实出网，为的是喂一串从不参与布局的变量。
 * 用户看到的是"启动有点慢"，看不到那一串读数是被丢掉的。
 *
 * 现在它们有了消费点，代价是它们**不再在开机时跑** —— 装载挂在 [ComicExplorePane] 的
 * `LaunchedEffect` 上，也就是"切到漫画域才装载"。同一份 IO，换了一个诚实的时机。
 *
 * ## 这一域的两页为什么各自
 *
 * - **探索**：`EngineSession` 的探索页（分区 + 封面墙）+ 点开看前 8 页。
 * - **收藏夹**：[LocalFavoritesManager]，落在本机 SQLite 的那份。
 *
 * ⚠️ 后者与 rail 上「收藏」那一枚**不是同一件事** —— 那边是画廊收藏
 * （[com.venera.compose.gallery.data.GalleryFavorites]，落 `gallery_favorites.json`），
 * 这边是漫画收藏（落 `LocalFavoriteDatabase`）。两边条数不相等是**正常的**，
 * 把两个域的收藏数念成同一个数就是假读数。
 */
/**
 * 漫画域的装配参数。
 *
 * ⚠️ `public` 是**被包名决定的**，不是随便挑的：集成方（`com.venera.desktop.VeneraDesktop`）
 * 在另一个包里，要把这份参数交给 [VeneraDesktopApp] 就必须够得着它。
 * 这一层里其余的器件都是 `internal`（`sibling` 之间互相调用），只有这颗与 [VeneraDesktopApp] 是例外。
 */
data class DesktopComicEngineConfig(
    val assetDir: File,
    val dataDir: File,
    val proxy: String?,
    /** 起始源脚本的 key（就是 `sources/<key>.js` 那颗文件名）。 */
    val sourceKey: String,
    /**
     * 收藏管理器的**懒装载**。做成 lambda 而不是直接一颗 manager，是因为
     * `LocalFavoritesManager.getInstance` 会建两棵库 —— 在 `main()` 里装配等于开机即建，
     * 哪怕用户今天一整天都不点开漫画域。第一次真正要看收藏时才取。
     */
    val favorites: () -> LocalFavoritesManager,
)

internal object DesktopComicSectionKey {
    const val EXPLORE = "comic-explore"
    const val FAVORITES = "comic-favorites"
}

/** 漫画域的两页，次序就是 pane 里自上而下的次序。 */
internal fun desktopComicRows(): List<DesktopGalleryHomeRow> = listOf(
    DesktopGalleryHomeRow(
        key = DesktopComicSectionKey.EXPLORE,
        title = "探索",
        glyph = "探",
        content = DesktopGallerySectionContent.ComicExplore,
        reason = null,
    ),
    DesktopGalleryHomeRow(
        key = DesktopComicSectionKey.FAVORITES,
        title = "收藏夹",
        glyph = "夹",
        content = DesktopGallerySectionContent.ComicFavorites,
        reason = null,
    ),
)

@Composable
internal fun DesktopComicBody(row: DesktopGalleryHomeRow, config: DesktopComicEngineConfig) {
    val rendered: Unit = when (row.content) {
        DesktopGallerySectionContent.ComicExplore -> ComicExplorePane(config)
        DesktopGallerySectionContent.ComicFavorites -> ComicFavoritesPane(config)
        // 与 `DesktopGalleryHomeBody` 里那处 error 对称：两条域路不许串。
        DesktopGallerySectionContent.Daily,
        DesktopGallerySectionContent.FollowedArtists,
        -> error("「${row.title}」是图库域的页，不该由漫画域渲染 —— 分岔见 VeneraDesktopApp.DesktopDomainBody")
        null -> error("漫画域今天没有未实现的行，「${row.title}」却带了空的 content 位")
    }
}

// ─────────────────────────── 探索 ───────────────────────────

private sealed interface ComicExploreState {
    data object Loading : ComicExploreState
    data class Failed(val reason: String) : ComicExploreState
    data class Ready(
        val sourceName: String,
        val sections: List<EngineSession.Section>,
        val covers: Map<String, ImageBitmap>,
        val readCount: Int,
    ) : ComicExploreState
}

/**
 * 一次装载带回的两样东西。
 *
 * ⚠️ [engine] 必须在结果里一路带回来、而不是让组合里的 state 自己再建一颗：
 * `EngineSession` 是在 IO 线程里构造的 GraalJS context，它是这一次装载**唯一**的那颗句柄，
 * 丢了它再建一颗等于把已装载的源脚本文档重新 parse 一遍。
 */
private data class ComicLoadResult(
    val engine: EngineSession,
    val sourceName: String,
    val sections: List<EngineSession.Section>,
    val covers: Map<String, ImageBitmap>,
)

/** 一次就取 12 个分区 × 每区 12 条；再往下堆对一个"看一眼"的画面没有意义。 */
private const val COMIC_EXPLORE_SECTION_LIMIT = 12
private const val COMIC_EXPLORE_ITEM_LIMIT = 12

/** 封面并发上限。原版是串行拉 30 张，一张慢就把整屏拖住。 */
private const val COMIC_COVER_LIMIT = 30

@Composable
private fun ComicExplorePane(config: DesktopComicEngineConfig) {
    var attempt by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<ComicExploreState>(ComicExploreState.Loading) }
    // EngineSession 持有 GraalJS 的 context，必须随这一屏的退场关掉 —— 不关就是句柄泄漏，
    // 而每切一次走漫画域再回来会再建一颗，来看几次就能把 JVM 撑死。
    //
    // key 用 session 而不是 Unit：换一颗新 session 时旧 effect 的 `onDispose` 会先跑，
    // 把上一颗 context 关掉。写 `Unit` 的话它在这一屏存活期间只 dispose 一次，
    // 于是"重取"一次就泄漏一颗。
    var session by remember { mutableStateOf<EngineSession?>(null) }
    DisposableEffect(session) {
        onDispose { session?.close() }
    }
    LaunchedEffect(config.sourceKey, attempt) {
        state = ComicExploreState.Loading
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val host = DesktopJsHost(config.assetDir, config.dataDir, config.proxy)
                val engine = EngineSession(host, config.assetDir)
                val name = engine.load(config.sourceKey)
                val page = engine.explore(1)
                val shown = page.sections.map { it.copy(comics = it.comics.take(COMIC_EXPLORE_ITEM_LIMIT)) }
                    .take(COMIC_EXPLORE_SECTION_LIMIT)
                val all = shown.flatMap { it.comics }
                if (all.isEmpty()) {
                    // 空手而归必须说清是哪种空：没有 explore / 信封失败 / 源回了 0 条
                    error(page.error ?: "源返回 0 条（分区 ${page.sections.size} 个）")
                }
                val covers = LinkedHashMap<String, ImageBitmap>()
                all.take(COMIC_COVER_LIMIT).forEach { card ->
                    if (card.cover.isBlank()) return@forEach
                    runCatching {
                        org.jetbrains.skia.Image.makeFromEncoded(engine.coverBytes(card.cover))
                            .toComposeImageBitmap()
                    }.getOrNull()?.let { covers[card.id] = it }
                }
                ComicLoadResult(engine, name, shown, covers)
            }
        }
        state = outcome.fold(
            // 顺序要紧：先交 session（换 session 会触发旧 effect 的 onDispose，把上一颗 context 关掉），
            // 再置 state。反过来会得到一小段"state 已经是新的、而 session 还是旧的"的窗口，
            // 那时点开的详情会打在已经关掉的那颗 host 上。
            onSuccess = { result ->
                session = result.engine
                ComicExploreState.Ready(
                    sourceName = result.sourceName,
                    sections = result.sections,
                    covers = result.covers,
                    readCount = result.covers.size,
                )
            },
            onFailure = { ComicExploreState.Failed("${it::class.java.simpleName}: ${it.message?.take(240)}") },
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("漫画探索", color = DesktopTheme.TextPrimary, fontSize = 18.sp)
            Text(
                " · 源 ${config.sourceKey}${config.proxy?.let { " · 代理 $it" } ?: " · 直连"}",
                color = DesktopTheme.TextSecondary,
                fontSize = 12.sp,
            )
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .background(DesktopTheme.SurfaceRaised, RoundedCornerShape(4.dp))
                    .clickable { attempt += 1 }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("重取", color = DesktopTheme.TextSecondary, fontSize = 11.sp)
            }
        }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            when (val current = state) {
                ComicExploreState.Loading ->
                    Text("装载源 ${config.sourceKey}…", color = DesktopTheme.TextSecondary, fontSize = 12.sp)

                is ComicExploreState.Failed ->
                    Text(current.reason, color = DesktopTheme.AccentBeni, fontSize = 12.sp)

                is ComicExploreState.Ready -> {
                    Text(
                        "${current.sourceName} · ${current.sections.size} 个分区 / " +
                            "${current.sections.sumOf { it.comics.size }} 条 · 出图 ${current.readCount} 张",
                        color = DesktopTheme.TextSecondary,
                        fontSize = 12.sp,
                    )
                    current.sections.forEach { section ->
                        ComicSection(section, current.covers)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComicSection(section: EngineSession.Section, covers: Map<String, ImageBitmap>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            section.title,
            color = DesktopTheme.TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        // 固定 6 列而不是 Adaptive：宽度 kost, wall 那是 deli 立面 стекла，
        // 这里是一排同样尺寸的封面，列数跟窗宽走会让"这张封面多大"变成变量。
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(estimatedGridHeight(section.comics.size)),
        ) {
            items(section.comics) { card -> ComicCard(card, covers[card.id]) }
        }
    }
}

/** 每行列数固定 6，封面按 3:4，加行间距 —— 高度必须算出来，否则 Grid 在 Column 里会争无限高度。 */
private fun estimatedGridHeight(count: Int): androidx.compose.ui.unit.Dp {
    val rows = (count + 5) / 6
    val cardWidth = 132.dp
    val cardHeight = cardWidth * (4f / 3f) + 22.dp
    // ⚠️ Dp 只能写在乘号的**左边**（`Int.times(Dp)` 没有这个重载，`Dp.times(Int)` 才有）
    return cardHeight * rows + 8.dp * (rows - 1).coerceAtLeast(0)
}

@Composable
private fun ComicCard(card: EngineSession.ComicCard, cover: ImageBitmap?) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(Modifier.width(132.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .background(
                    if (hovered) DesktopTheme.SurfaceRaised else DesktopTheme.CardBackground,
                    RoundedCornerShape(6.dp),
                )
                .hoverable(interaction),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // 封面没取到也要"`没出图`是看得见的" —— 空白块会被读成"这张还没加载完"
                Text("无封面", color = DesktopTheme.TextTertiary, fontSize = 11.sp)
            }
        }
        Text(
            card.title,
            color = DesktopTheme.TextSecondary,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ─────────────────────────── 收藏夹 ───────────────────────────

@Composable
private fun ComicFavoritesPane(config: DesktopComicEngineConfig) {
    var tree by remember { mutableStateOf<List<Pair<String, List<FavoriteItem>>>>(emptyList()) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val manager = config.favorites()
                manager.currentFolders().map { it to manager.getFolderComics(it) }
            }
        }
        outcome.fold(
            onSuccess = { tree = it; note = null },
            onFailure = {
                note = "收藏列表读取失败：${it::class.java.simpleName}: ${it.message?.take(200)}"
                tree = emptyList()
            },
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("漫画收藏夹", color = DesktopTheme.TextPrimary, fontSize = 18.sp)
        Text(
            "这一份落在本机 SQLite（LocalFavoriteDatabase），与 rail「收藏」那一枚的画廊收藏不是同一份数据。",
            color = DesktopTheme.TextTertiary,
            fontSize = 11.sp,
        )

        val currentNote = note
        if (currentNote != null) {
            Text(currentNote, color = DesktopTheme.AccentBeni, fontSize = 12.sp)
        } else if (tree.isEmpty()) {
            Text("还没有收藏任何一本。", color = DesktopTheme.TextSecondary, fontSize = 12.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tree.forEach { (folder, items) ->
                    item {
                        Column {
                            Text("$folder · ${items.size} 本", color = DesktopTheme.TextPrimary, fontSize = 14.sp)
                            items.forEach { comic ->
                                Text(
                                    "　${comic.name}　（源 ${comic.sourceKey}）",
                                    color = DesktopTheme.TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
