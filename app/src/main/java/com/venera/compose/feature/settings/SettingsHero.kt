package com.venera.compose.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import com.venera.compose.feature.LocalVeneraDarkTheme
import com.venera.compose.ui.tokens.ImageOverlayColors
import com.venera.compose.ui.tokens.VeneraTokens
import java.io.File
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import android.app.Activity

/**
 * 设置 hero 页的系统状态栏图标翻转（2026-10-02 第五轮「处理下状态栏」）。
 *
 * 头图在场且还没滚过头图时，状态栏区域 = 图 + 顶部 scrim，此时用**白图标**
 * （与图上白标题同一套对比逻辑，浅色主题下全局那处 `VeneraTheme` 会设成深图标，
 * 压在亮图上就看不清了）。滚过 [SettingsHome] 给出的阈值后，毛玻璃背板接管
 * 状态栏区域，翻回主题默认色（浅色玻璃配深图标）。做法对齐
 * `GalleryPostScreen` 那处：进出各设一次，离页还原，主题变化时全局那处自己会跑。
 *
 * 只动状态栏，不动导航栏 —— 导航栏始终归全局主题管。
 */
@Composable
internal fun SettingsHeroSystemBarIcons(heroPresent: Boolean, scrolledPastHero: Boolean) {
    if (!heroPresent) return
    val view = LocalView.current
    val isDark = LocalVeneraDarkTheme.current
    DisposableEffect(view, isDark, scrolledPastHero) {
        val controller = (view.context as? Activity)?.window
            ?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null) {
            controller.isAppearanceLightStatusBars = scrolledPastHero && !isDark
        }
        onDispose {
            controller?.isAppearanceLightStatusBars = !isDark
        }
    }
}

/**
 * 设置页 hero 头图（首页 + 全部子页共用一份实现，2026-10-02）。
 *
 * ── 调用契约 ──
 *
 * 只在 [file] 非 null（用户设置过图且文件还在）时调用；调用方是
 * [SettingsPage] 与设置首页 —— 没选图的页面走原来的「大标题顶栏」分支，
 * 整条 hero 链路不参与渲染（零降级风险）。
 *
 * ── 过渡模型（2026-10-02 四轮定稿：模糊延伸到内容背后）──
 *
 * 用户要的是「选完图自动模糊过渡」：不是在图底下贴一条带子（v3 的做法在真机上
 * 模糊层与清晰层取景错位，成了一条脏带），而是**同一张图自己往下延续成模糊**：
 *
 * 1. **模糊延伸层**（最底）：同图、同宽、**TopCenter 取景**——比清晰层**高出
 *    [extend] 向下延伸**，顶部对齐所以两层取景逐像素同源（v3 错位的根因就是
 *    两层高度不同 + 居中取景，Crop 各取各的段）。blur 后作为 hero 下半与卡片
 *    背后的底色延续出去，**故意不裁剪**、画出 hero 框外。
 * 2. **清晰层**：正常画满 hero，下缘 [fadeHeight] 内用 DstIn 淡出 —— 淡出的部分
 *    露出底下的模糊延伸层，「清晰 → 模糊」在图内连续完成。
 * 3. **溶接渐变**：模糊延伸段的下缘 96dp 渐入页面背景色，整条过渡以
 *    「清晰 → 模糊 → 背景」收尾，卡片就叠在这条带上。
 *
 * z 序为什么成立：Compose 同层兄弟按布局顺序绘制，内容列在 hero 之后 —— 溢出的
 * 模糊层自然垫在卡片背后（参考图的图透到卡后正是这个）。
 *
 * 其余决策承前：标题在返回胶囊下方（statusBar+64dp，两层不重叠）；覆盖层色取
 * [ImageOverlayColors] 固定色板（图亮度不可控），溶接渐变终点色除外 —— 那一段要的
 * 就是「融入页面背景」，跟主题走。
 *
 * @param file 已解析好的本地文件（调用方先过 [SettingsHeroImageStore.resolve]）。
 * @param title 压在图上的标题（首页「设置」/ 子页各自的页名）。
 * @param subtitle 副标题；null 不摆。
 * @param totalHeight hero 视觉全高（**含状态栏段**，调用方算好传入）。
 * @param overlapBottom 布局占位比视觉高度矮多少（其后内容上移叠图）。
 */
@Composable
internal fun SettingsHeroImage(
    file: File,
    title: String,
    subtitle: String?,
    totalHeight: Dp,
    overlapBottom: Dp,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pageBackground = MiuixTheme.colorScheme.background
    // tokens 的 getter 是 @Composable：所有要带进绘制期的量都在组合期取成局部值。
    val fadeHeight = tokens.spacing.space11 * 3 // 清晰层下缘淡出带 96dp
    val extend = tokens.spacing.space11 * 5 // 模糊层向下延伸 160dp（垫到卡片背后）
    val blurRadius = tokens.spacing.space8 // 16dp
    val mergeHeight = tokens.spacing.space11 * 3 // 溶接渐变带 96dp（offset lambda 里只能用局部量）
    // 布局期用的像素量（@Composable getter 进不了 measure lambda，组合期先取好）。
    val density = LocalDensity.current
    val fullHeightPx = with(density) { totalHeight.roundToPx() }
    val extendPx = with(density) { extend.roundToPx() }
    val cutPx = with(density) { overlapBottom.roundToPx() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "设置页头图" }
            // 「占位矮一截、画面画全高」：报告高度 = 全高 − overlap，place 从 y=0 画全图，
            // 后续内容上移压到图的下段上。
            //
            // ⚠️⚠️ 不能用 `.height(totalHeight)` + 内层 `.layout { layout(w, h - cut) }` 的
            // 组合实现同一效果（2026-10-01 三轮真机排查踩死的坑）：`Modifier.height` 的
            // SizeNode 会把测出来矮了 cut 的内层内容**垂直居中**在自己强制报告的全高框里，
            // 内容整体下移 cut/2 —— 顶部露出一条 (cut/2) 高的背景带，正是用户反复报告的
            // 「hero 盖不满状态栏、中间隔着一条」（208−48)/2 = 80dp 框在 393dp 屏上约合
            // 63px，与逐帧截图测得的间隔完全一致）。所以高度约束必须在这里自己放开、
            // 自己 place(0,0)，不经过任何 SizeNode。
            .layout { measurable, constraints ->
                // 内层放开高度约束到「全高 + 模糊延伸」：模糊层要测出超出报告区的高度
                // 才有「画出框外垫到卡片背后」的延伸观感；清晰层显式 height(totalHeight)。
                val placeable = measurable.measure(
                    constraints.copy(minHeight = 0, maxHeight = fullHeightPx + extendPx)
                )
                layout(placeable.width, (fullHeightPx - cutPx).coerceAtLeast(0)) {
                    placeable.place(0, 0)
                }
            },
        // ⚠️ 故意不 clipToBounds：模糊延伸层要画出框外垫到卡片背后（本组件的核心观感）。
    ) {
        // 模糊延伸层：比 hero 高出 extend，与清晰层同宽同取景（TopCenter）——
        // 顶部对齐保证「清晰图淡出的那一段」和「模糊层显示的那一段」是同一幅画的同一段。
        Box(
            Modifier
                .fillMaxWidth()
                .height(totalHeight + extend)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // 只在下缘向背景溶接（DstIn 抹掉底部 alpha）；上段让清晰层去露。
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            0.55f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadius),
            )
        }
        // 清晰层：下缘 fadeHeight 内 DstIn 淡出，露出底下的模糊延伸层。
        // ⚠️ 显式 height(totalHeight)：外层约束已放开到全高+延伸（模糊层要用），
        // fillMaxSize 会跟着撑到 826，把清晰图整体拉高。
        Box(
            Modifier
                .fillMaxWidth()
                .height(totalHeight)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fadeStart = ((size.height - fadeHeight.toPx()) / size.height)
                        .coerceIn(0f, 1f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            fadeStart to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 溶接渐变：模糊延伸段的下缘 → 页面背景色。摆到 hero 框外（延伸区底部），
        // offset 是纯绘制偏移、不参与布局，正好用来画到框外。
        Box(
            Modifier
                .fillMaxWidth()
                .height(mergeHeight)
                .offset { IntOffset(0, (totalHeight + extend - mergeHeight).roundToPx()) }
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, pageBackground))
                ),
        )
        // 顶 scrim：从状态栏顶盖到标题块下缘，保返回胶囊与标题白字的对比。
        Box(
            Modifier
                .fillMaxWidth()
                .height(statusBarTop + tokens.spacing.space11 * 4)
                .background(
                    Brush.verticalGradient(listOf(ImageOverlayColors.ScrimTop, Color.Transparent))
                ),
        )
        // 标题 + 副标题：返回胶囊下方（胶囊浮在 hero 顶部，标题从 64dp 起不与它重叠）。
        Column(
            Modifier
                .padding(
                    start = tokens.spacing.rowHorizontal,
                    end = tokens.spacing.rowHorizontal,
                    top = statusBarTop + tokens.spacing.space11 * 2,
                ),
        ) {
            Text(
                text = title,
                fontSize = tokens.type.screenTitle,
                fontWeight = tokens.type.weightSemibold,
                color = ImageOverlayColors.OnImage,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = tokens.type.caption,
                    color = ImageOverlayColors.OnImageSecondary,
                    modifier = Modifier.padding(top = tokens.spacing.space2),
                )
            }
        }
    }
}
