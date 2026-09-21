package com.venera.compose.components.venera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 封面裁切状态（通用语义，非页面专用）。
 *
 * - [Visible]  : 正常显示
 * - [Masked]   : 命中内容守卫，需打码（模糊 + 暗色磨砂遮罩 + R18 角标）
 */
enum class VeneraCoverMask { Visible, Masked }

/**
 * Venera 统一漫画封面（基础层）。
 *
 * 职责：
 *  - 统一 aspect ratio / ContentScale.Crop / 圆角 / 占位 / 加载 / 错误。
 *  - 保证「加载中 → 加载完成」不发生布局跳动（占位与成品几何一致）。
 *
 * 通用能力（保持通用，不含页面逻辑）：
 *  - [mask] : NSFW 打码，不判断业务规则（由调用方决定何时 Masked）。命中时：
 *      1) Modifier.blur 模糊（部分老系统/关闭硬件加速的设备上可能静默失效）；
 *      2) **半透明深色磨砂遮罩**（不依赖 blur，任何设备都可见）；
 *      3) **右上角 R18 高对比角标**（对齐旧版「打码必须一眼可辨」的要求）。
 *  - [content] : 覆盖层插槽，用于放置 SourceBadge 等（BoxScope）。
 *
 * 刻意**不**包含：
 *  - 点击（由外层 VeneraCard 或调用方处理）
 *  - 业务判断（如标题命中检测）——那是 SecurityGuard 的职责
 *
 * @param url 封面地址；空串时直接显示占位，不发起请求。
 * @param contentDescription 无障碍描述（通常是作品标题）。
 * @param preserveAspectRatio 封面是否自持 [VeneraTokens] 的封面比例。
 *   默认 true（列表/详情那套「宽度定死、高度自算」的用法）。轮播这类**宽高都由外部槽位给定**
 *   的场景要传 false，否则 aspectRatio 会在固定高度里把图横向收窄，卡片两侧留缝。
 */
@Composable
fun VeneraCover(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    mask: VeneraCoverMask = VeneraCoverMask.Visible,
    shimmerWhileLoading: Boolean = true,
    preserveAspectRatio: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(tokens.shape.medium)
    val isMasked = mask == VeneraCoverMask.Masked

    // 是否有过成功加载：用于区分「首次加载」与「加载失败」。
    var hadError by remember(url) { mutableStateOf(false) }
    var loaded by remember(url) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .then(
                if (preserveAspectRatio) {
                    Modifier.fillMaxWidth().aspectRatio(tokens.spacing.coverAspectRatio)
                } else {
                    // 外部已给死宽高（轮播槽位）：再叠 aspectRatio 会横向收窄留缝
                    Modifier.fillMaxSize()
                }
            )
            .clip(shape)
            // 占位底色：与 VeneraCoverShimmer 观感一致，避免闪白
            .background(tokens.color.surfaceVariant.copy(alpha = tokens.current.placeholderAlpha)),
    ) {
        val showShimmer = shimmerWhileLoading && url.isNotBlank() && !loaded && !hadError
        if (showShimmer) {
            VeneraCoverShimmer(Modifier.fillMaxSize())
        }
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                onError = { hadError = true },
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isMasked) Modifier.blur(tokens.spacing.space10) else Modifier),
            )
        }
        if (isMasked) {
            // 暗色磨砂遮罩：不依赖 blur 生效，保证任何设备上打码视觉 100% 可见。
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = tokens.current.maskScrimAlpha)),
            )
            // 右上角 R18 角标：高对比红底，对齐旧版「R18 · 已打码」语义。
            Text(
                text = "R18",
                fontSize = tokens.type.badge,
                fontWeight = tokens.type.weightBold,
                color = StatusColors.OnBadgeSurface,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(tokens.spacing.badgeInset)
                    .background(StatusColors.AccentBadge, RoundedCornerShape(tokens.shape.extraSmall))
                    .padding(horizontal = tokens.spacing.badgeHorizontalPadding, vertical = tokens.spacing.badgeVerticalPadding),
            )
        }
        // 覆盖层插槽：SourceBadge 等
        content()
    }
}
