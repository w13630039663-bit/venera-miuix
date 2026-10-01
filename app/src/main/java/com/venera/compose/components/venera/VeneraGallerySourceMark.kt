package com.venera.compose.components.venera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.R
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 画廊条目的**来源标识**（那枚站方图标）。
 *
 * ── 为什么不复用 [VeneraSourceBadge] ──
 *
 * 那枚是「源名 + 固定深色小圆角底板」的文字徽章，被漫画侧 6 个页面共用；这里是**图形标识**，
 * 承载的信息量不同（一眼认出哪一站，不靠读字）。两者形状与用途都不一样，合并只会互相牵制。
 *
 * ── 两枚都是站方图形，但清晰度不对等（这不是漏做）──
 *
 * 2026-09-30 实测，两档都由 `scripts/build_source_icons.mjs` 从站方原档转出：
 * - **Gelbooru**：站上有一枚 `layout/gelbooru-logo.svg`（360×360 单路径）⇒ **矢量**，
 *   任意密度都锐利。原档填充色是 #FFFFFF（为站方彩色页头设计的），这里改成品牌蓝 #006FFA。
 * - **yande.re**：站方**没有方形站标** —— 唯一可用的方图是 favicon 那枚 **16×16**，
 *   而它其实是一张裁自插画的粉脸（256 像素全不透明，主色是皮肤与头发色调）。
 *   摆到 24dp（本机 ≈72 物理像素）必然发糊。
 *   ⇒ 用户知情后仍选它：这张脸就是 yande.re 在浏览器标签页上的标识，**与站方一致优先于好看**。
 *
 * 两枚**同尺寸、同圆角**，所以并排时视觉重量一致；差别只在"一个是矢量、一个是 16px 位图"，
 * 而这是数据面的事实，不是设计选择。
 *
 * ⚠️ 它和 [VeneraSourceBadge] 一样**刻意不参与「界面材质 = 液态玻璃」这一轴**：
 * 它背后是颜色不可控的封面，底板的存在理由就是"压在任何图上都可读"。
 *
 * @param site 哪一站。`when` 不留 `else`：加新站时必须显式表态（同 `sourceMarkLetter` 的口径）。
 * @param size 徽标边长。两站共用同一档，别给单站调大小。
 */
@Composable
fun VeneraGallerySourceMark(
    site: GallerySite,
    modifier: Modifier = Modifier,
    size: Dp = VeneraTokens.spacing.sourceMarkSize,
) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(tokens.shape.extraSmall)
    when (site) {
        GallerySite.GELBOORU -> Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                // 白底：站标那枚 path 的形状是"实心 G"，直接压在封面上会因为透明度消失，
                // 铺一层固定白底既能托住图形，也与它原本的设计底色（站方彩色页头）同族。
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_source_gelbooru),
                contentDescription = site.displayName,
                tint = Color.Unspecified,
                modifier = Modifier.fillMaxSize(),
            )
        }

        GallerySite.YANDERE -> Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                // 那枚 16px 位图是全不透明的，正常情况下盖满这一层；留着它是因为
                // 位图在极端缩放比下会露出边缘，而品牌色底板本来就是这一站的链接色。
                .background(StatusColors.GallerySourceYandere),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_source_yandere),
                contentDescription = site.displayName,
                // 不许染色：那是站方的图，染成我们的主色就不是它了。
                tint = Color.Unspecified,
                modifier = Modifier.fillMaxSize(),
            )
        }

        GallerySite.SAFEBOORU -> Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                // 与 Gelbooru 同一条理由：safebooru.donmai.us 的 favicon 是 Danbooru
                // 系的纸箱标，带透明像素（由 build_source_icons.mjs 尊重 AND 掩码转出），
                // 站方这枚图形是为浅色浏览器标签栏设计的 —— 白底托住它，压在封面上不散。
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_source_safebooru),
                contentDescription = site.displayName,
                tint = Color.Unspecified,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
