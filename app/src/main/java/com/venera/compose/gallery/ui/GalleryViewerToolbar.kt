package com.venera.compose.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 满屏播放器底部那一整条 **Dock**（用户 2026-09-25：去掉顶栏，
 * 把 HD / 下载 / 信息 / 分享 全收进一个悬浮栏）。
 * 之前"pill + 右边独立 FAB"那两截的排法作废 —— 现在只有一截，图才是页面上唯一的主角。
 *
 * **摆出来的每一个钮都必须有真事可做**（用户红线：零容忍假按钮）：
 * - `♥` —— 收藏 / 取消收藏（[com.venera.compose.gallery.data.GalleryFavoritesStore]）。
 *   实心 + [VeneraTokens] 的 `actionFavorite` 色 = 已收藏。**不是**"点了弹个提示"：
 *   它与「收藏 → 图片收藏 → 画廊收藏」那一档读的是同一份存档，当场就能对上。
 * - `HD` —— 在 large 档与 `file_url` 原图档之间换档（逐张记忆，见调用点）。
 *   **视频条目不摆这一钮**：实测站方对视频没有更小的转码档，
 *   `large_file_url` 就是原片，"切高清"对它是假开关。
 * - `下载` —— 原样存下来（[com.venera.compose.gallery.data.GallerySaver]），
 *   进行中把图标换成波浪环并吃掉点击，避免出现"点了没反应"的那一下。
 * - `信息` —— 拉起 [GalleryInfoSheet]（顶栏没了，"在站点打开"也搬进那里）。
 * - `分享` —— 系统分享面板，带本站单页地址。
 *
 * 心形**排在最前**：它是这一条里唯一的"状态"钮（其余四个都是"做一件事"），
 * 单独一格也和内容动作区分开。
 */
@Composable
fun GalleryViewerToolbar(
    post: GalleryPost,
    preferHd: Boolean,
    isFavorite: Boolean,
    saving: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleHd: () -> Unit,
    onDownload: () -> Unit,
    onInfo: () -> Unit,
    onShare: () -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(
                start = tokens.spacing.screenHorizontal,
                end = tokens.spacing.screenHorizontal,
                bottom = tokens.spacing.space5,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(tokens.shape.extraLarge))
                .background(tokens.color.surface)
                .padding(horizontal = tokens.spacing.space2, vertical = tokens.spacing.badgeVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
        ) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏这张图",
                    // 与 HD 钮同一条口径：图标本身没有开关形态，不染色就分不清现在是开还是关。
                    // 未收藏时走 textPrimary（与其余四个钮一致），不摆一个灰掉的"禁用"外观 ——
                    // 它是可点的，灰掉会读成"这里现在不能点"。
                    tint = if (isFavorite) tokens.color.actionFavorite else tokens.color.textPrimary,
                )
            }
            if (!post.isVideo) {
                IconButton(onClick = onToggleHd) {
                    Icon(
                        imageVector = Icons.Outlined.HighQuality,
                        contentDescription = if (preferHd) "切回清晰档" else "看原图档",
                        // 用主色当"已开启"的唯一信号：图标本身没有开关形态，
                        // 不染色就分不清这钮现在是开还是关（那就是个假开关）。
                        tint = if (preferHd) tokens.color.primary else tokens.color.textPrimary,
                    )
                }
            }
            IconButton(onClick = onDownload, enabled = !saving) {
                if (saving) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(tokens.spacing.loaderInline),
                            color = tokens.color.primary,
                            trackColor = tokens.color.surfaceVariant,
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = "原样存进相册",
                        tint = tokens.color.textPrimary,
                    )
                }
            }
            IconButton(onClick = onInfo) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "关于这张图",
                    tint = tokens.color.textPrimary,
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "分享这张图",
                    tint = tokens.color.textPrimary,
                )
            }
        }
    }
}
