package com.venera.compose.gallery.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

/**
 * 墙上长按一张卡弹出的动作表（2026-10-04，用户第 2 条点名的五项）。
 *
 * 参照的是 pixez-miuix 那一版（`IllustCard.kt` 长按 → 底部动作表）。**读数要如实说**：
 * pixez 表里的动作是「下载 / 复制信息 / 复制图片 / 复制链接 / 分享链接 / 以图搜图 / 屏蔽作品」，
 * 里面**没有**「查看详情」「查看画师」「收藏」这三项 —— 它把详情当单击、画师走详情页头像、
 * 收藏放详情页的心形。所以这五项是我们自己的动作集，照搬过来的只有
 * "长按走底部动作表、不走逐枚锚的下拉"这一个形态。
 *
 * 三点选型：
 * - **m3 `ModalBottomSheet`，不用 miuix 的弹层**：与同族那张「关于这张图」
 *   （[GalleryInfoSheet]）同一个容器，那一张已经把两条既有约束趟过了 ——
 *   miuix 的 Overlay 系要页内有 miuix Scaffold 才可用，而独立 Dialog 窗口采不到玻璃、
 *   弹层只能实底。既然实底是必然，就不必为形态再换一套宿主。
 * - **一张表而不是逐枚锚的下拉**：卡是瀑布流里回收复用的槽位，锚点会随滚动漂。
 * - **这颗件不实现任何业务**：收藏走 `GalleryFavorites.toggle`、分享走 [shareGalleryPost]
 *   （与大图页同一份实现）、复制与「查看画师」都是既有链路的入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryCardActionSheet(
    post: GalleryPost,
    onDismiss: () -> Unit,
    /** 进大图页。本表只负责收起，导航归调用方。 */
    onOpenDetail: () -> Unit,
    /** 进画师介绍页，参数是站方那串作者名。 */
    onOpenArtist: (String) -> Unit,
) {
    val context = LocalContext.current
    val tokens = VeneraTokens
    val scope = rememberCoroutineScope()
    // 端口每次组一遍不便宜，记在 context 上（与 GalleryArtistRows 拿 follows 那颗同一写法）。
    val store = remember(context) { GalleryPorts.of(context).favorites }
    val favorites by store.favorites.collectAsState()
    // `contains` 在具体 store 上、不在 GalleryFavorites 接口上，所以这里按收集到的那份算 ——
    // 走 collectAsState 而不是调一下就算，是为了让标签（收藏/取消收藏）随收藏变化。
    val isFavorite = favorites.any { it.uid == post.uid }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // skipPartiallyExpanded：这张表统共五行，半展开会把它停在屏幕中间、底下留一大片空。
        // Reader 那三张表都带这一参；GalleryInfoSheet 那张长内容表刻意不带（它要能滚到底）。
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = tokens.spacing.screenHorizontal,
                    vertical = tokens.spacing.space4,
                ),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.space1),
        ) {
            // 表头先说清这是**哪一张**：瀑布流里长按，弹出后若只有动作没有对象，
            // 用户得靠记忆确认自己按的是刚才那一张。
            Text(
                text = "${post.site.displayName} #${post.id}",
                fontSize = tokens.type.caption,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(bottom = tokens.spacing.space2),
            )
            ActionRow(Icons.Outlined.Visibility, "查看详情") {
                onDismiss()
                onOpenDetail()
            }
            ActionRow(Icons.Outlined.FavoriteBorder, if (isFavorite) "取消收藏" else "收藏") {
                scope.launch {
                    // toggle 返回"现在是不是收藏态"。它内部已经落盘，这里补一句反馈 ——
                    // 墙上没有常驻的收藏读数，不出一句话用户没法确认这一下生效了（假开关零容忍）。
                    val now = store.toggle(post)
                    Toast.makeText(
                        context,
                        if (now) "已收藏该${if (post.isVideo) "视频" else "图片"}" else "已取消收藏",
                        Toast.LENGTH_SHORT,
                    ).show()
                    onDismiss()
                }
            }
            ActionRow(Icons.Outlined.ContentCopy, "复制图片地址") {
                copyImageUrl(context, post.fileUrl)
                onDismiss()
            }
            ActionRow(Icons.Outlined.Share, "分享图片") {
                scope.launch {
                    // 与大图页那枚分享走同一份实现：取原档、原样落字节、失败出声。
                    shareGalleryPost(context, post)
                    onDismiss()
                }
            }
            // 作者名来自站方那一个字段（yande.re 的 `author` / Gelbooru 的 `owner` /
            // Safebooru 的 `tag_string_artist`），三站语义不完全同宽（见 GalleryPost.author 头注）。
            // 空就**不摆这一行**：摆一枚点进去是空介绍页的入口，就是假开关。
            val artist = post.author.trim()
            if (artist.isNotEmpty()) {
                ActionRow(Icons.Outlined.Person, "查看画师「$artist」") {
                    onDismiss()
                    onOpenArtist(artist)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .padding(vertical = tokens.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tokens.color.textPrimary,
            modifier = Modifier.size(tokens.spacing.chipIconSize),
        )
        Text(
            text = label,
            fontSize = tokens.type.itemTitle,
            color = tokens.color.textPrimary,
        )
    }
}

/**
 * 复制图片直链。地址为空时**必须说话**：那种卡是站方没给 `file_url` 的那一类，
 * 静默复制一个空串读起来就是"复制了但剪贴板里没有"。
 */
private fun copyImageUrl(context: Context, value: String) {
    if (value.isBlank()) {
        Toast.makeText(context, "这张没有可复制的图片地址", Toast.LENGTH_SHORT).show()
        return
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("图片地址", value))
    Toast.makeText(context, "已复制图片地址", Toast.LENGTH_SHORT).show()
}
