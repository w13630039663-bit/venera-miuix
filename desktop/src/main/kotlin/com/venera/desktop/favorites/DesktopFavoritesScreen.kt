package com.venera.desktop.favorites

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.contentMaxWidthFor
import com.venera.compose.components.venera.VeneraButton
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.data.db.FavoriteRecord
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 桌面收藏屏 —— 对应安卓 `FavoritesScreen`（1337 行）。
 *
 * ## 与安卓的差别（写明）
 *
 * 安卓那 1337 行有文件夹分组、排序、多选、批量移出、更新角标、下拉刷新。
 * 这一批做：网格铺开 + 点开详情 + 移出收藏。
 * 没做：文件夹、排序、多选、批量操作、更新检测（`hasUpdate` 只显示角标，不做检测）。
 *
 * ## 空态为什么要分两句
 *
 * `favorites` 为空有两种成因：「还没读回来」与「库读回来了但确实没有收藏」。
 * 合成一句就等于把"库还没开"说成"你没收过藏" —— 本仓反复记过的那类假完成。
 */
@Composable
internal fun DesktopFavoritesScreen(
    mode: WideScreenLayoutMode,
    favorites: List<FavoriteRecord>,
    /** `false` = 还没读回来。与"读了但确实没有"要分开说。 */
    loaded: Boolean,
    /** 读失败成因。非空照原话上屏。 */
    note: String?,
    maskedOf: (FavoriteRecord) -> Boolean,
    onOpen: (FavoriteRecord) -> Unit,
    onRemove: (FavoriteRecord) -> Unit,
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
                    text = "收藏",
                    fontSize = tokens.type.screenTitle,
                    color = tokens.color.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${favorites.size} 本",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textTertiary,
                )
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
                favorites.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 132.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = tokens.spacing.space6),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                ) {
                    items(favorites) { fav ->
                        FavoriteCell(
                            fav = fav,
                            masked = maskedOf(fav),
                            onOpen = { onOpen(fav) },
                            onRemove = { onRemove(fav) },
                        )
                    }
                }

                // 「还没读回来」与「读了确实没有」是两件事，屏上分开说。
                !loaded -> CenterNote("正在读本地收藏…")

                else -> CenterNote(
                    "还没有收藏。在探索页点开一本，详情页可以收藏。",
                )
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
private fun FavoriteCell(
    fav: FavoriteRecord,
    masked: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val tokens = VeneraTokens
    VeneraCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.72f)) {
                VeneraCover(
                    url = fav.coverUrl,
                    contentDescription = fav.title,
                    mask = if (masked) VeneraCoverMask.Masked else VeneraCoverMask.Visible,
                )
            }
            Spacer(Modifier.height(tokens.spacing.space1))
            Text(
                text = fav.title,
                fontSize = tokens.type.caption,
                color = tokens.color.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = tokens.spacing.space2),
            )
            Text(
                text = fav.sourceName,
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = tokens.spacing.space2),
            )
            Box(modifier = Modifier.padding(tokens.spacing.space2)) {
                VeneraButton(onClick = onRemove) {
                    Text("移出", fontSize = tokens.type.caption)
                }
            }
        }
    }
}
