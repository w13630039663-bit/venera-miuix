package com.venera.compose.components.venera

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.venera.compose.feature.LocalVeneraDarkTheme
import com.venera.compose.ui.tokens.LocalSurfaceMaterial
import com.venera.compose.ui.tokens.SurfaceMaterialPolicy
import com.venera.compose.ui.tokens.VeneraGlassHighlight
import com.venera.compose.ui.tokens.VeneraGlassRole
import com.venera.compose.ui.tokens.VeneraGlassTier
import com.venera.compose.ui.tokens.veneraGlassEnabled
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blendColors
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.textureEffect
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全站玻璃的**唯一采样源**（由 [com.venera.compose.components.VeneraAmbientBackground] 安装）。
 *
 * 为什么只有一份：每多一个 `rememberLayerBackdrop` 就多一张离屏纹理，而玻璃真正的代价在录制那一步
 * 不在读取那一步。氛围光是一张静态 Canvas —— 滚动时它不重绘，所以这一份**不会逐帧重捕**，
 * 这才是"整站贴玻璃还不忘减帧"成立的前提。
 *
 * 默认 null ⇒ 没有任何录制层时，玻璃修饰符自动 no-op，而不是去采一个不存在的东西。
 */
val LocalGlassBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/**
 * 三个观感参数，**全部抄本仓已真机验证过的读数**，不自造：
 * - 容器透明度 0.22 / 0.28：`components/backdrop/VeneraLiquidGlassNavBar.kt:119`
 *   （同处 :117-118 的注释说明官方 catalog 默认 0.4 太不透，玻璃要"透"必须压低这一层）。
 * - 染色强度 0.16：`components/venera/VeneraTopAppBar.kt:100`，那行自己的注释写着
 *   "只负责提升文字对比，不再把玻璃推向白色"——泛白的真凶曾是采样层缺氛围光，已修，这里不补偿。
 * - 模糊半径：直接用库默认 `BlurDefaults.BlurRadius`（20f），不另给数字。
 *
 * 深浅色下 [tintAlpha] 不分档：顶栏那条 0.16 是深浅共用的现成读数，没有依据就不拆两份。
 */
private const val ContainerAlphaLight = 0.22f
private const val ContainerAlphaDark = 0.28f
private const val TintAlpha = 0.16f

/** 枚举 → 库里的 `Highlight` 对象。库里是**扁平属性名**（不是 `Highlight.GlassStrokeMiddle.Light` 那种点链）。 */
private fun miuixHighlight(preset: VeneraGlassHighlight): Highlight = when (preset) {
    VeneraGlassHighlight.MIDDLE_LIGHT -> Highlight.GlassStrokeMiddleLight
    VeneraGlassHighlight.MIDDLE_DARK -> Highlight.GlassStrokeMiddleDark
    VeneraGlassHighlight.SMALL_LIGHT -> Highlight.GlassStrokeSmallLight
    VeneraGlassHighlight.SMALL_DARK -> Highlight.GlassStrokeSmallDark
}

/**
 * 给一个元素上 Miuix 液态玻璃。
 *
 * 三条硬约定，写在类型签名上而不是靠调用方自觉：
 * 1. **关闭档必须纯 no-op**：材质轴关着、或没有录制层时，返回**入参 Modifier 本身**，
 *    不建 BlurColors、不 remember 任何东西 —— 这一条是"切材质后两套观感逐像素不变"的实现前提。
 * 2. 档位由 [VeneraGlassRole] 经判据层算出，调用方不许自己点档（否则同类元素会在各页长成两样）。
 * 3. 本文件**只准 import miuix 一家的 blur**：`com.kyant:backdrop` 与 miuix-blur 有五个同名符号
 *    （Backdrop / LayerBackdrop / rememberLayerBackdrop / drawBackdrop / Highlight），混 import 会在
 *    编译期就指错库。Kyant 那一家的出处**限定在 `components/backdrop/` 目录内**
 *    （2026-09-29 实数：`VeneraLiquidGlassNavBar.kt` 与 `InteractiveHighlight.kt` 两个文件），
 *    目录外任何文件出现 `import com.kyant` 就是违规。**唯一在册例外**：保护域 `feature/Navigation.kt`
 *    自 `4270487`（官方 Liquid Glass 底栏接入那一轮）就 import 它，那是底栏录制层的接线处、
 *    属未获豁免不得动的文件 —— 别把它当违规去"清理"，也别拿它当先例往别的文件加 Kyant。
 *    自查命令要**按 import 行匹配**，
 *    别用 `grep com\.kyant` —— 那样会把 `androidx.compose.material3.Text` 之类的包名也算成命中
 *    （我自己就是这么误报过一次，白查一轮）。
 *
 * ⚠️ 这一层拿到的是**模糊 + 染色 + 描边高光**，**不含折射透镜**：`lens` 不在 miuix-blur 里
 * （2026-09-29 逐字核过 sources jar），只有底栏那圈用 Kyant 实现了它。别对外说内容区玻璃会"折"。
 */
@Composable
fun Modifier.veneraGlass(
    role: VeneraGlassRole,
    shape: Shape,
    backdrop: LayerBackdrop? = LocalGlassBackdrop.current,
    enabled: Boolean = SurfaceMaterialPolicy.glassEnabled(LocalSurfaceMaterial.current),
): Modifier {
    if (!enabled || backdrop == null) return this
    val isDark = LocalVeneraDarkTheme.current
    val surface = MiuixTheme.colorScheme.surface
    val tier = SurfaceMaterialPolicy.tierOf(role)
    val colors = BlurDefaults.blurColors(
        blendColors = listOf(BlendColorEntry(color = surface.copy(alpha = TintAlpha))),
    )
    val highlight = SurfaceMaterialPolicy.highlightFor(isDark, tier)?.let(::miuixHighlight)
    return when (tier) {
        VeneraGlassTier.CONTAINER, VeneraGlassTier.CONTROL ->
            // 先采后铺：链上靠前的先画（在底），所以模糊层在下、半透明容器色在中、内容在上 ——
            // 与底栏那套「外壳 = 采样 + 容器色」的层序是同一个形状。
            textureEffect(
                backdrop = backdrop,
                shape = shape,
                blurRadius = BlurDefaults.BlurRadius,
                colors = colors,
                highlight = highlight,
            ).background(surface.copy(alpha = if (isDark) ContainerAlphaDark else ContainerAlphaLight))
        // 点缀档：一屏能到二十个，绝不建模糊；只染一层色，连容器底都不铺。
        VeneraGlassTier.INLINE -> drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blendColors(colors) },
        )
    }
}

/**
 * 「一块圆角表面要不要玻璃」的**唯一**出处 —— VeneraCard 与设置页分组卡都走这里。
 *
 * 为什么要抽：这两处各自写一遍"玻璃时透明、实色时默认色"，就会有第三处照抄时漏掉一半，
 * 表现为"这张卡透了那张卡不透"。形状固定用 RoundedCornerShape(cornerRadius)：
 * miuix 的玻璃入参要的是 `Shape`，而它内部自己按 cornerRadius 做 squircle 裁剪，
 * 两者的圆角差在真机上能不能看出来还没定 —— 但绝不该各处不一样。
 */
@Composable
fun Modifier.veneraGlassSurface(role: VeneraGlassRole, cornerRadius: Dp): Modifier =
    if (veneraGlassEnabled()) {
        veneraGlass(role = role, shape = RoundedCornerShape(cornerRadius))
    } else {
        this
    }

/** 配套：玻璃开着时容器本体必须透明，否则 Card 的不透明底会把刚采的样全盖掉。 */
@Composable
fun veneraGlassCardColors(): CardColors =
    if (veneraGlassEnabled()) {
        CardDefaults.defaultColors().copy(color = Color.Transparent)
    } else {
        CardDefaults.defaultColors()
    }
