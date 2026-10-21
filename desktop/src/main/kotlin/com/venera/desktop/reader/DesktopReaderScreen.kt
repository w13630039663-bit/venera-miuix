package com.venera.desktop.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import com.venera.compose.components.venera.VeneraButton
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopComicPage

/**
 * 桌面漫画阅读器 —— 对应安卓 `VeneraReaderScreen`（2529 行），本轮做**纵向连读**这一档。
 *
 * ## 为什么阅读器是这批的第一优先
 *
 * 不只是因为它大。更硬的理由：**没有"翻页"这个动作，首页的统计与历史永远是空的。**
 * `desktopWriteReadingHistory` 写件早就写好了，但一直没有调用点 ——
 * 因为桌面没有阅读器，没有"读到第几页"这个事实可写。
 *
 * 所以这个文件同时是**写侧的上游**：它一上屏，首页那三格数字与历史卡就开始自己长。
 *
 * ## 与安卓的差别（写明，不假装一样）
 *
 * 安卓那 2529 行里有单页/双页/横向/纵向四种翻页、手势、边缘点击区、亮度/色温浮层、
 * 音量键翻页、全屏沉浸。大屏鼠标键盘的场景下：
 * - 本轮做**纵向连读**（一条 LazyColumn 往下滚）+ **页码跳转**；
 * - 键盘 ← → 翻页、Esc 退出；
 * - 不做双击缩放、不做边缘点击区（那是手指的交互，鼠标上会误触）。
 *
 * 双页/横向那两档的**算式**在 `DesktopReaderPaging.kt` 里是完整的，本轮没接到 UI 上 ——
 * 这条明确记着，不算"已经支持"。
 *
 * ## 打码
 *
 * 每页一张图，`masked` 由外面算好传进来（判据在 `UxPlatformServices`，
 * 与墙上同一条链）。**阅读器不能自己决定打不打码** —— 两处判据一定会漂。
 */
@Composable
internal fun DesktopReaderScreen(
    /**
     * 这一话的页。
     *
     * 类型是 [DesktopComicPage] 而不是 [String]：`DesktopComicPageFetcher` 只认这个 model
     * （见那个类的 KDoc）。拿字符串当 model 会被 Coil 送给网络 fetcher，
     * 而页键不是 http(s) ⇒ 一片黑。
     */
    pages: List<DesktopComicPage>,
    /** 章节名（顶栏那个）。 */
    episodeLabel: String,
    /** 取页失败 / 读取中的成因。非空照原话上屏。 */
    note: String?,
    /** 这一本要不要打码。外面算好，这里只搬。 */
    masked: Boolean,
    /** 当前页（0-based）。由外面持有，好在切屏后保持。 */
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPage.coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    val lastIndex = pages.lastIndex

    // 滚动 → 当前页。用 `snapshotFlow` 而不是每次 onScroll 回调：
    // 前者只在**值真的变**时触发，后者会在滚动过程中打几十次，写历史会被打爆。
    LaunchedEffect(listState, pages.size) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { idx -> if (idx != currentPage) onPageChange(idx) }
    }

    // 键盘：← → 翻页，Esc 返回。大屏上这是主交互（鼠标滚轮走 LazyColumn 自己那套）。
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionRight, Key.DirectionDown, Key.PageDown -> {
                        val t = (currentPage + 1).coerceAtMost(maxOf(lastIndex, 0))
                        if (t != currentPage) onPageChange(t)
                        true
                    }
                    Key.DirectionLeft, Key.DirectionUp, Key.PageUp -> {
                        val t = (currentPage - 1).coerceAtLeast(0)
                        if (t != currentPage) onPageChange(t)
                        true
                    }
                    Key.Escape -> { onBack(); true }
                    else -> false
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 顶栏 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A))
                    .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
            ) {
                VeneraButton(onClick = onBack) { Text("← 返回") }
                Text(
                    text = episodeLabel,
                    fontSize = tokens.type.body,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    // 页号一律按「人看的」从 1 数：空表时不念（"第 1 / 0 页"是自相矛盾的读数）。
                    text = if (pages.isEmpty()) "" else "第 ${currentPage + 1} / ${pages.size} 页",
                    fontSize = tokens.type.caption,
                    color = Color(0xFFBBBBBB),
                )
            }

            // ── 成因 / 空态 ──
            if (note != null) {
                Text(
                    text = note,
                    fontSize = tokens.type.caption,
                    color = Color(0xFFFFB4A9),
                    modifier = Modifier.padding(
                        horizontal = tokens.spacing.space4,
                        vertical = tokens.spacing.space2,
                    ),
                )
            }

            if (pages.isEmpty()) {
                // 空页表 ≠ 阅读器坏了。要说清是哪一种。
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (note == null) "这一话没取到页。可能是源站限流，或这一话需要登录。" else "",
                        fontSize = tokens.type.body,
                        color = Color(0xFFCCCCCC),
                    )
                }
                return@Column
            }

            // ── 正文：纵向连读 ──
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
            ) {
                items(pages.size) { i ->
                    // ⚠️ 这里**不能**用 `VeneraCover`：它是**封面**的形状
                    // （内部 `ContentScale.Crop` + `aspectRatio(coverAspectRatio)`），
                    // 用在整页漫画上会把 2:3 的页裁成封面框那一条 —— 屏上一片黑。
                    //
                    // ⚠️ 也不能只写一个裸 `AsyncImage`：**高度会塌成 0**。
                    // `FillWidth` 只约束宽度，高度等图片首帧；而 0 高的 item 在 LazyColumn
                    // 里不会被预取 ⇒ 永远等不到那一帧，74 个 item 全黑（端到端实测）。
                    //
                    // 所以给每一项一个**高度兜底**：按常见漫画页比例（h/w≈1.43，即 0.7）
                    // 先占位，图到了之后 `ContentScale.Fit` 在框内等比显示 —— 不裁、不留白边。
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.7f)
                            // 打码与墙/封面同一条语义：模糊。放在 Box 上，盖住整页。
                            .then(if (masked) Modifier.blur(24.dp) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = pages[i],
                            contentDescription = "第 ${i + 1} 页",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                item {
                    // 到底了要说一句：连读时用户不知道自己是不是滑完了。
                    Spacer(Modifier.height(tokens.spacing.space6))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "— 本话完 —",
                            fontSize = tokens.type.caption,
                            color = Color(0xFF888888),
                            modifier = Modifier.padding(bottom = tokens.spacing.space6),
                        )
                    }
                }
            }
        }
    }
}
