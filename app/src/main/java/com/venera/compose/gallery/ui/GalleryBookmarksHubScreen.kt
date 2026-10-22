package com.venera.compose.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.ExpandMore
import com.venera.compose.components.venera.blurBackdropSource
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraTopAppBar
import com.venera.compose.components.venera.VeneraTopBarPill
import com.venera.compose.components.venera.rememberTopBarBackdrop
import com.venera.compose.components.venera.rememberVeneraTopAppBarBehavior
import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GalleryImageLoader
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.data.toPost
import com.venera.compose.ui.tokens.LocalBottomBarClearance
import com.venera.compose.ui.tokens.VeneraSpacing
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch

/**
 * 画廊「收藏」主页（图 2 原版重构，2026-10-22）。
 *
 * - 顶部 Pixiv 书签横幅卡片，点击一键查看 Pixiv 云端书签；
 * - 本地收藏统计与排序/筛选（支持站点筛选、安全分级筛选、最新/最早排序）；
 * - 下方 3 列极简纯图瀑布流展示，右上角保留精致红心快速操作。
 */
@Composable
internal fun GalleryBookmarksHubScreen(
    onOpenPost: (GalleryPost) -> Unit,
    onOpenPixivBookmarks: () -> Unit,
    onBack: () -> Unit,
) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val topBarBehavior = rememberVeneraTopAppBarBehavior()
    val topBarBackdrop = rememberTopBarBackdrop()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = statusBarTop + VeneraSpacing.topBarFloor

    val store = remember { GalleryPorts.of(context).favorites }
    val allFavorites by store.favorites.collectAsState()
    val imageLoader: ImageLoader = remember { GalleryImageLoader.get(context) }

    var selectedSite by remember { mutableStateOf<GallerySite?>(null) }
    var selectedRating by remember { mutableStateOf<String?>(null) } // null=全部, s, q, e
    var newestFirst by remember { mutableStateOf(true) }
    var filterMenuExpanded by remember { mutableStateOf(false) }

    val filteredList = remember(allFavorites, selectedSite, selectedRating, newestFirst) {
        var list = allFavorites.asSequence().filter { it.site != GallerySite.PIXIV }
        if (selectedSite != null) {
            list = list.filter { it.site == selectedSite }
        }
        if (selectedRating != null) {
            list = list.filter { it.rating.equals(selectedRating, ignoreCase = true) }
        }
        val sorted = if (newestFirst) {
            list.sortedByDescending { it.savedAt }
        } else {
            list.sortedBy { it.savedAt }
        }
        sorted.toList()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .blurBackdropSource(topBarBackdrop)
                .nestedScroll(topBarBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = tokens.spacing.space3,
                end = tokens.spacing.space3,
                top = topPadding,
                bottom = LocalBottomBarClearance.current + tokens.spacing.space6,
            ),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            verticalItemSpacing = tokens.spacing.space2,
        ) {
            // ── 顶部 Pixiv 书签大卡片 ──
            item(span = StaggeredGridItemSpan.FullLine) {
                PixivBookmarkEntryBanner(
                    onClick = onOpenPixivBookmarks,
                )
            }

            // ── 计数与筛选/排序菜单行 ──
            item(span = StaggeredGridItemSpan.FullLine) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = tokens.spacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "共 ${filteredList.size} 张",
                        fontSize = tokens.type.body,
                        color = tokens.color.textSecondary,
                    )

                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.85f))
                                .border(tokens.spacing.hairline, tokens.color.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(percent = 50))
                                .clickable { filterMenuExpanded = true }
                                .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space2),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FilterList,
                                contentDescription = null,
                                tint = tokens.color.primary,
                                modifier = Modifier.size(tokens.spacing.space4),
                            )
                            val filterSummary = when {
                                selectedSite != null && selectedRating != null -> "${selectedSite?.displayName} · $selectedRating"
                                selectedSite != null -> selectedSite?.displayName.orEmpty()
                                selectedRating != null -> when (selectedRating) {
                                    "s" -> "安全"
                                    "q" -> "敏感"
                                    "e" -> "成人"
                                    else -> selectedRating.orEmpty()
                                }
                                else -> "排序 / 筛选"
                            }
                            Text(
                                text = filterSummary,
                                fontSize = tokens.type.body,
                                fontWeight = tokens.type.weightMedium,
                                color = tokens.color.textPrimary,
                            )
                            Icon(
                                imageVector = Icons.Outlined.ExpandMore,
                                contentDescription = null,
                                tint = tokens.color.textSecondary,
                                modifier = Modifier.size(tokens.spacing.space4),
                            )
                        }

                        DropdownMenu(
                            expanded = filterMenuExpanded,
                            onDismissRequest = { filterMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (newestFirst) "✓ 最新收藏在前" else "最新收藏在前") },
                                onClick = {
                                    newestFirst = true
                                    filterMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (!newestFirst) "✓ 最早收藏在前" else "最早收藏在前") },
                                onClick = {
                                    newestFirst = false
                                    filterMenuExpanded = false
                                },
                            )
                            // 站点筛选（完全排除 PIXIV）
                            DropdownMenuItem(
                                text = { Text(if (selectedSite == null) "✓ 全部图库站点" else "全部图库站点") },
                                onClick = {
                                    selectedSite = null
                                    filterMenuExpanded = false
                                },
                            )
                            GallerySite.entries.filter { it != GallerySite.PIXIV }.forEach { site ->
                                DropdownMenuItem(
                                    text = { Text(if (selectedSite == site) "✓ ${site.displayName}" else site.displayName) },
                                    onClick = {
                                        selectedSite = site
                                        filterMenuExpanded = false
                                    },
                                )
                            }
                            // 分级筛选
                            DropdownMenuItem(
                                text = { Text(if (selectedRating == null) "✓ 全部分级" else "全部分级") },
                                onClick = {
                                    selectedRating = null
                                    filterMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (selectedRating == "s") "✓ 普通 (Safe)" else "普通 (Safe)") },
                                onClick = {
                                    selectedRating = "s"
                                    filterMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (selectedRating == "q") "✓ 敏感 (Questionable)" else "敏感 (Questionable)") },
                                onClick = {
                                    selectedRating = "q"
                                    filterMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (selectedRating == "e") "✓ 成人 (Explicit)" else "成人 (Explicit)") },
                                onClick = {
                                    selectedRating = "e"
                                    filterMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            // ── 3 列纯图极简瀑布流卡片 ──
            items(filteredList, key = { it.uid }) { favorite ->
                MinimalFavoriteCard(
                    favorite = favorite,
                    imageLoader = imageLoader,
                    onOpen = { onOpenPost(favorite.toPost(favorite.site ?: GallerySite.YANDERE)) },
                )
            }
        }

        VeneraTopAppBar(
            title = "收藏",
            largeTitle = "收藏",
            scrollBehavior = topBarBehavior,
            backdrop = topBarBackdrop,
            navigationIcon = {
                VeneraTopBarPill(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary,
                    )
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * 顶部 Pixiv 书签横幅卡片（还原图 2 样式）。
 */
@Composable
private fun PixivBookmarkEntryBanner(onClick: () -> Unit) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.shape.card))
            .background(tokens.color.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.space5, vertical = tokens.spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        Icon(
            imageVector = Icons.Outlined.Bookmark,
            contentDescription = null,
            tint = Color(0xFF9E4747),
            modifier = Modifier.size(tokens.spacing.space8),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Pixiv 书签",
                fontSize = tokens.type.body,
                fontWeight = tokens.type.weightSemibold,
                color = tokens.color.textPrimary,
            )
            Spacer(Modifier.height(tokens.spacing.space1))
            Text(
                text = "查看 Pixiv 账号的云端书签",
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = "进入",
            tint = tokens.color.textTertiary,
        )
    }
}

/**
 * 极简纯图片卡片（无心标纯图展示）。
 */
@Composable
private fun MinimalFavoriteCard(
    favorite: GalleryFavorite,
    imageLoader: ImageLoader,
    onOpen: () -> Unit,
) {
    val tokens = VeneraTokens
    val ratio = if (favorite.width > 0 && favorite.height > 0) {
        (favorite.width.toFloat() / favorite.height.toFloat()).coerceIn(0.5f, 2.0f)
    } else 1.0f
    val displayUrl = favorite.previewUrl.ifBlank { favorite.largeUrl }.ifBlank { favorite.fileUrl }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(tokens.shape.small))
            .clickable(onClick = onOpen),
    ) {
        VeneraCover(
            url = displayUrl,
            contentDescription = null,
            preserveAspectRatio = false,
            imageLoader = imageLoader,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
