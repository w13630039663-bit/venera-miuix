package com.venera.compose.components.venera

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import com.venera.compose.feature.LocalVeneraDarkTheme
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.ui.tokens.veneraGlassEnabled

/**
 * **实色档**的「面材质」：垂直微渐变（顶亮底暗）+ 顶部 1px 高光。玻璃档**整条 no-op**。
 *
 * ── 为什么需要它（2026-10-17 方案 B，用户拍板「先按方案b」）──
 *
 * 首页 chrome 那一行（站源分段器 + 搜索条）此前是两块**均匀平涂 + 零高光 + 零投影**的面，
 * 压在粉色照片墙上读作两张贴纸 —— 没有厚度，怎么调色都还是"素"的。
 * 评审里 A/B 两档里 B 是「立层级」：搜索浮成主角、站源沉成底。
 * 这一颗件就是那三件套（渐变 / 高光 / 投影交给调用方的 `shadowElevation`）里前两件的**唯一出处**。
 *
 * ## 渐变端点一律从 token 推，不写死 hex
 *
 * CSS 复刻里写的是 `#FFFFFF → #FDF6F6`、`#FFD2D8 → #FFB6C2`。那两个值**只对那一套粉色板成立** ——
 * 动态取色（MD3 壁纸色）与深色档下会整片脱节。所以两端都吃调用方给的**语义槽**，
 * 由 [lerp] 在槽位之间插值（判据出自评审第四节点出的坑①）。
 *
 * ## 为什么玻璃档整条 no-op（坑②，踩过就是"模糊没了"）
 *
 * `Modifier.blur` / miuix `textureEffect` 只采得到**它自己那层节点背后**的像素。
 * 玻璃档下这块面背后是氛围层，在它**外面**再挂一个不透明 `background(brush)` 就等于
 * 把刚采到的模糊盖死 —— 玻璃会当场退化成一块平涂。
 * 玻璃档的高光与分层由 [veneraGlass] 里的 `Highlight` 描边与容器 alpha 负责，
 * 这里**不再叠第二套**，否则两套高光会互相打架。
 *
 * ## 顶部高光为什么用 `drawWithContent` 而不是 `border`
 *
 * CSS 是 `inset 0 1px 0` —— **只有顶边**。`Modifier.border` 四边齐描，
 * 而这个圆角很大（`extraLarge`），左右与底边那三笔会被读成"描了个边"而不是"顶上有光"。
 * 画在 `drawWithContent` 里还有个附带好处：它落在调用方的 `clip` **之内**，
 * 于是线走到圆角处被裁掉 ⇒ 视觉上是一条中间亮、两头自然隐去的高光，与 CSS 同形。
 *
 * ## 深色档为什么要减半高光
 *
 * 那条线是 `Color.White`。浅色下它是"光"，深色下同样的 alpha 会是一条脏白的硬边
 * （深色玻璃的描边高光在库里走 `GlassStrokeMiddleDark`，本身就是近白低透明，见
 * [com.venera.compose.ui.tokens.SurfaceMaterialPolicy.highlightFor]）。
 * 没有对应读数，所以深色按比例压低而不是照抄浅色的 alpha。
 *
 * ⚠️ `tokens.spacing` 是 `@Composable` 取值器，绘制期读不到 —— [hairlinePx] 必须组合期取。
 */
@Composable
fun Modifier.veneraFaceSheen(
    /** 渐变顶端（更亮那一头）。语义槽，不是写死色。 */
    topColor: Color,
    /** 渐变底端（更暗那一头）。 */
    bottomColor: Color,
    /** 顶部高光的不透明度（浅色档）。深色档按 [DARK_HIGHLIGHT_RATIO] 压低。 */
    highlightAlpha: Float = 0.9f,
): Modifier {
    // 玻璃档：分层与高光归 [veneraGlass]，这里什么都不叠（坑②）。
    if (veneraGlassEnabled()) return this
    val isDark = LocalVeneraDarkTheme.current
    val hairlinePx = with(LocalDensity.current) { VeneraTokens.spacing.hairline.toPx() }
    val highlight = Color.White.copy(
        alpha = if (isDark) highlightAlpha * DARK_HIGHLIGHT_RATIO else highlightAlpha,
    )
    return this
        .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
        .drawWithContent {
            drawContent()
            // 线宽取 1dp、**居中**在顶端内侧（y = 半线宽）：画在 y=0 会被 clip 掉一半，
            // 读起来比设计稿淡，而"高光本来就该淡"这件事用户看不出来、"高光不见了"看得出来。
            drawLine(
                color = highlight,
                start = Offset(0f, hairlinePx / 2f),
                end = Offset(size.width, hairlinePx / 2f),
                strokeWidth = hairlinePx,
            )
        }
}

/** 深色档的高光比例。压到三分之一以下：那条线是纯白，深色下是唯一一处会"发亮"的边。 */
private const val DARK_HIGHLIGHT_RATIO = 0.3f

/**
 * 「粉色件」的渐变两端插值比例 —— [Modifier.veneraFaceSheen] 在粉色面上的默认口径。
 *
 * 用在哪三处（**都在首页 chrome 那一行**）：站源分段器的选中胶囊、搜索条尾那枚图搜圆钮。
 * 两处本就该读成同一件事（"这一行里唯一带粉色的东西"），所以走同一对数而不是各写一份。
 *
 * ## 为什么是插值而不是换一枚更深的槽位
 *
 * 评审的原话是「选中态现在太淡」。换槽位（比如从 `primaryContainer` 换到 `primary`）会牵动
 * 全仓所有读 `primaryContainer` 的地方 —— 选中态是这个语义，不该由首页 chrome 单方面改深。
 * 插值把"深多少"留在这颗件自己身上：改它只改这一行。
 *
 * ## 为什么要两个数而不是一个
 *
 * 改成渐变之后，"深"这件事本身得由**底端**承担，顶端留一点亮是为了让面有厚度 ——
 * 纯深会读成一枚扁掉的色块（这正是 CSS 里两枚件都用 `#FFDx → #FFBx` 而不是单色的原因）。
 *
 * ⚠️ 这两枚是**观感参数**、不是间距/字阶，刻意不进 `ui/tokens/`：
 * 进 token 就成了"全站粉色件的渐变"，而顶栏药丸与详情页背板并不需要它。
 */
internal const val PILL_TINT_TOP = 0.08f
internal const val PILL_TINT_BOTTOM = 0.26f