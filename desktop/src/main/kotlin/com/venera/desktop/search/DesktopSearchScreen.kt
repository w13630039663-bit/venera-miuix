package com.venera.desktop.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.contentMaxWidthFor
import com.venera.compose.components.venera.VeneraButton
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.engineprobe.EngineSession

/**
 * 桌面搜索屏 —— 对应安卓 `SearchScreen`（1830 行）。
 *
 * ## 与安卓的差别（写明）
 *
 * 安卓那 1830 行有搜索历史、联想补全、按源筛选、标签搜索、多源并发、分页加载。
 * 这一批做：一个输入框 + 单源搜索 + 网格结果 + 点开详情。
 * 没做：历史、联想、多源并发、分页。
 *
 * ## 为什么只有一个源
 *
 * 多源并发要 `ComicSourceManager` 那一套（在 `auto_exclude` 里），
 * 而这一批的原则是"不为了凑功能去伪造能力"。所以先把单源那条打通，
 * 屏上**明确写出当前搜的是哪个源** —— 让用户知道这不是全站搜索。
 */
@Composable
internal fun DesktopSearchScreen(
    mode: WideScreenLayoutMode,
    sourceKey: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    results: List<EngineSession.ComicCard>,
    /** 搜索中标志 —— 与"搜完了没结果"要分开说。 */
    searching: Boolean,
    note: String?,
    maskedOf: (EngineSession.ComicCard) -> Boolean,
    onOpen: (EngineSession.ComicCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val maxWidth = contentMaxWidthFor(mode)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
                .fillMaxSize()
                .padding(horizontal = tokens.spacing.screenHorizontal),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
            ) {
                Text(
                    text = "搜索",
                    fontSize = tokens.type.screenTitle,
                    color = tokens.color.textPrimary,
                )
                Text(
                    // 说清"只搜这一个源"，不让用户以为这是全站。
                    text = "只在「$sourceKey」里搜",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = tokens.spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("书名 / 作者") },
                        // 回车即搜：大屏上这是主交互，不该强迫用户去点按钮。
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    )
                }
                VeneraButton(onClick = onSearch, enabled = query.isNotBlank() && !searching) {
                    Text(if (searching) "搜索中…" else "搜索")
                }
            }

            if (note != null) {
                Text(
                    text = note,
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(bottom = tokens.spacing.space2),
                )
            }

            when {
                results.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 132.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = tokens.spacing.space6),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                ) {
                    items(results) { card ->
                        ResultCell(card = card, masked = maskedOf(card), onOpen = { onOpen(card) })
                    }
                }

                searching -> CenterNote("正在搜索…")

                query.isBlank() -> CenterNote("输入书名或作者，回车开始搜。")

                // 搜完了、有 query、但没结果 —— 要说"没搜到"，而不是回到"请输入"。
                else -> CenterNote("「$query」在「$sourceKey」里没搜到。")
            }
        }
    }
}

@Composable
private fun CenterNote(text: String) {
    val tokens = VeneraTokens
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, fontSize = tokens.type.body, color = tokens.color.textTertiary)
    }
}

@Composable
private fun ResultCell(
    card: EngineSession.ComicCard,
    masked: Boolean,
    onOpen: () -> Unit,
) {
    val tokens = VeneraTokens
    VeneraCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.72f)) {
                VeneraCover(
                    url = card.cover,
                    contentDescription = card.title,
                    mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                )
            }
            Spacer(Modifier.height(tokens.spacing.space1))
            Text(
                text = card.title,
                fontSize = tokens.type.caption,
                color = tokens.color.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = tokens.spacing.space2)
                    .padding(bottom = tokens.spacing.space2),
            )
        }
    }
}
