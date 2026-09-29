package com.venera.compose.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraIconButton
import com.venera.compose.components.venera.VeneraSlider
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.ui.tokens.VeneraTokens
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text

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
 * - `分享` —— 系统分享面板，交出去的是**原档字节**（图或视频，与「下载」同档），
 *   外加一条单页地址。原档要先落一次盘，所以它与「下载」同一形态：
 *   进行中把图标换成波浪环并吃掉点击 —— 视频 16~26 MB 那几秒里，
 *   一个没有任何反馈的钮就是"点了没反应"。
 * - `▶ / ⏸` —— 自动连播的开关（**点**），与这一屏的速度（**长按**展开一条滑条）。
 *   开启时染主色（与 HD 同一约定）。这一颗改的是**本次浏览**的速度，
 *   退出大图页就回到「设置 → 画廊 → 大图页 → 自动连播间隔」那一条的默认值 ——
 *   用户 2026-09-29 拍板：底栏这颗不写回偏好，免得随手一调就把默认值污染了。
 *   判据在 `GalleryAutoPlay`：到底停、信息面板开着不走、放大态不走、**视频页不走**
 *   （所以停在视频上时这颗不会自己起播，这是刻意的，不是漏了）。
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
    sharing: Boolean,
    autoPlaySec: Int,
    onToggleAutoPlay: () -> Unit,
    onAutoPlaySecChange: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleHd: () -> Unit,
    onDownload: () -> Unit,
    onInfo: () -> Unit,
    onShare: () -> Unit,
) {
    val tokens = VeneraTokens
    // 速度面板的开关只属于这条 dock：它不跨页、不进存档，关掉面板不影响连播本身。
    var speedOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(
                start = tokens.spacing.screenHorizontal,
                end = tokens.spacing.screenHorizontal,
                bottom = tokens.spacing.space5,
            ),
        horizontalAlignment = Alignment.Start,
    ) {
        // ── 长按播放钮展开的那一条：只在需要时占位，平时这条 dock 还是原来那一截 ──
        AnimatedVisibility(
            visible = speedOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .padding(bottom = tokens.spacing.space2)
                    .clip(RoundedCornerShape(tokens.shape.extraLarge))
                    .background(tokens.color.surface)
                    .padding(horizontal = tokens.spacing.space4, vertical = tokens.spacing.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("连播", fontSize = tokens.type.caption, color = tokens.color.textSecondary)
                VeneraSlider(
                    value = autoPlaySec.toFloat().coerceIn(AUTOPLAY_MIN_SEC.toFloat(), AUTOPLAY_MAX_SEC.toFloat()),
                    onValueChange = { onAutoPlaySecChange(it.roundToInt().coerceIn(AUTOPLAY_MIN_SEC, AUTOPLAY_MAX_SEC)) },
                    valueRange = AUTOPLAY_MIN_SEC.toFloat()..AUTOPLAY_MAX_SEC.toFloat(),
                    steps = AUTOPLAY_MAX_SEC - AUTOPLAY_MIN_SEC - 1,
                    modifier = Modifier
                        .width(160.dp)
                        .padding(horizontal = tokens.spacing.space3),
                )
                Text("${autoPlaySec} 秒", fontSize = tokens.type.caption, color = tokens.color.textPrimary)
            }
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(tokens.shape.extraLarge))
                .background(tokens.color.surface)
                .padding(horizontal = tokens.spacing.space2, vertical = tokens.spacing.badgeVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
        ) {
            VeneraIconButton(onClick = onToggleFavorite) {
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
                VeneraIconButton(onClick = onToggleHd) {
                    Icon(
                        imageVector = Icons.Outlined.HighQuality,
                        contentDescription = if (preferHd) "切回清晰档" else "看原图档",
                        // 用主色当"已开启"的唯一信号：图标本身没有开关形态，
                        // 不染色就分不清这钮现在是开还是关（那就是个假开关）。
                        tint = if (preferHd) tokens.color.primary else tokens.color.textPrimary,
                    )
                }
            }
            VeneraIconButton(onClick = onDownload, enabled = !saving) {
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
            VeneraIconButton(onClick = onInfo) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "关于这张图",
                    tint = tokens.color.textPrimary,
                )
            }
            VeneraIconButton(onClick = onShare, enabled = !sharing) {
                if (sharing) {
                    // 与「下载」同一形态：原档要先下再落盘，那几秒必须有个在做事的读数。
                    Box(contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(tokens.spacing.loaderInline),
                            color = tokens.color.primary,
                            trackColor = tokens.color.surfaceVariant,
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "分享这张图",
                        tint = tokens.color.textPrimary,
                    )
                }
            }
            // ── 自动连播：点 = 开/关，长按 = 展开上面那条速度 ──
            // 这颗不用 IconButton：那个不吃 onLongClick。同尺寸的 Box + combinedClickable
            // 是仓库里已有的做法（`components/ComicTileLayout.kt:66` 那颗长按钮同一形）。
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = onToggleAutoPlay,
                        onLongClick = { speedOpen = !speedOpen },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (autoPlaySec > 0) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (autoPlaySec > 0) "停止连播（长按可调速度）" else "开始连播（长按可调速度）",
                    // 与 HD 那颗同一条约定：图标自己没有开关形态，不染色就读不出现在是开还是关。
                    tint = if (autoPlaySec > 0) tokens.color.primary else tokens.color.textPrimary,
                )
            }
        }
    }
}

/**
 * 连播速度的上下界（秒）。地板 1 秒：再快就不是"看图"而是放幻灯片；
 * 上限 15 秒比设置页那条（0~30，0=关）窄 —— 底栏这颗是"看着看着顺手调快调慢"，
 * 不是拿它当"每半分钟翻一张"的定时器，那种需求归设置页。
 */
private const val AUTOPLAY_MIN_SEC = 1
private const val AUTOPLAY_MAX_SEC = 15
