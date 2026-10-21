package com.venera.compose.components.venera

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.LocalVeneraDarkTheme
import com.venera.compose.ui.tokens.LocalSurfaceMaterial
import com.venera.compose.ui.tokens.SurfaceMaterialPolicy
import com.venera.compose.ui.tokens.VeneraGlassHighlight
import com.venera.compose.ui.tokens.VeneraGlassRole
import com.venera.compose.ui.tokens.VeneraGlassTier
import com.venera.compose.ui.tokens.VeneraTokens
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
 * - 染色强度 0.16：现在只有下面 [GlassTintAlpha] 那一处定义（顶栏、药丸、详情页背板都读它）。
 *   它只负责提升文字对比，不再把玻璃推向白色 —— 泛白的真凶曾是采样层缺氛围光，已修，这里不补偿。
 * - 模糊半径：直接用库默认 `BlurDefaults.BlurRadius`（20f），不另给数字。
 *
 * 深浅色下 [tintAlpha] 不分档：顶栏那条 0.16 是深浅共用的现成读数，没有依据就不拆两份。
 *
 * ⚠️ 这三个 alpha 值 2026-09-30 之后**只解释了"透多少"，没解释"抬多亮"** ——
 * 抬亮量由**叠的是哪个色**决定，见下面 [containerColorFor]。
 */
private const val ContainerAlphaLight = 0.22f
private const val ContainerAlphaDark = 0.28f

/**
 * 面板档（[VeneraGlassRole.PANEL]）在**浅色**主题下的容器不透明度 —— 只有浅色需要它。
 *
 * ## 归因订正：它**不是**"消灰块"的开关（2026-10-16 二次真机复核）
 *
 * 这一档最初是照着「玻璃把面板压暗了 ⇒ 抬容器 alpha」写下的，**那个归因是错的**。
 * 真凶是 [veneraSurfaceShadowElevation] 那一条：玻璃档卡底透明，miuix `Surface` 挂在
 * 调用方 modifier **之内**的平台投影（不透明黑剪影）就从卡底透出来了。读数与判据见那里。
 *
 * 反证就在这一轮里：把 0.22 抬到 0.86 之后，用户截图上的灰块**一点没退**。
 * 因为投影画在容器色**之后**（层序 ① 模糊 → ② 容器色 → ③ 投影 → ④ 子元素），
 * 调 ② 的量动不了 ③ 之后的结果 —— 逐通道比值恒为 0.785 的那一族数，就是这个道理。
 *
 * ## 那它为什么还要留着 —— 换成站得住的那条理由
 *
 * 玻璃的"分层量" = alpha × (容器色 − 采样底)。浅色下 `surfaceContainerHigh`(#F6E4E2) 与
 * 氛围层顶部(#EFDCD5)几乎是同一个亮度（抬亮只有 **+7**），于是 0.22 这一档在浅色下
 * 算出来的分层只有 **+1.5** —— 面板与页面背景肉眼分不开，用户会读到"卡片不见了"。
 * 浅色下要让面板"是自己一块面"，只能让容器色自己说话。
 *
 * ⚠️ 深色档**不动**（仍 [ContainerAlphaDark]）：深色下采样底与容器色都近黑，
 * 同一个 alpha 的语义是成立的（真机验证过），没有对应读数就不拆两份。
 *
 * ⚠️ 本档**不是**玻璃在浅色下的"亮度档"。想让它更"透"就调它是可以的，但先读上面这条：
 * 调低不会让面板变灰（灰是 [veneraSurfaceShadowElevation] 那一层的事），只会让面板变淡。
 */
private const val PanelAlphaLight = 0.86f

/**
 * 玻璃补底那一层的染色强度：顶栏、药丸、详情页背板与这颗件共用一个数。
 *
 * 从前是四处各抄一遍 `0.16f`（其中三处还各写了一句"与某某同步"）—— 改一档要翻四颗文件，
 * 而现在这一档是"透多少"与"抬多亮"两条曲线的交点，动它的理由只会是整屏玻璃一起动。
 */
internal const val GlassTintAlpha = 0.16f

/**
 * 某一档玻璃**叠哪个色**（容器色与染色色共用一个来源）。
 *
 * ## 为什么 CONTAINER 档不能继续用 `surface`（批次 N · N1）
 *
 * 玻璃的提亮量 = 容器 alpha × (容器色 − 被采样的底)。这一套深色方案里 `surface` 与页面底
 * 几乎是同一个近黑（真机实测页面底 rgb(20,19,24)），于是"模糊一个近黑的底、再叠 0.28 的近黑"
 * = **零提亮**。2026-09-30 量过：玻璃档下节背板比页面底只亮 **+2**、条目卡边缘只亮 **+7**，
 * 而参考图那张卡比自己的页面底亮 **+21/+21/+36** —— 用户那句
 * 「开了玻璃就是这样，感觉就是不如图一那种」的成因就在这里，不在模糊本身。
 *
 * 反证也在这屏上：顶栏与搜索框实测亮 +27 / +23，因为它们走的是
 * `tokens.color.surfaceContainerHigh`（`gallery/ui/GalleryHomeChrome.kt` 那两层 Surface）。
 * 同一个 alpha 在 chrome 有效、在内容区失效 ⇒ 该换的是**槽位**，不是 alpha。
 *
 * 所以 CONTAINER 档改取 [VeneraGlassRole] 所在容器已有的抬亮语义槽 `surfaceContainerHigh`：
 * 它是 MD3 色调表面档、本仓已登记在 token 层（`ui/tokens/Color.kt` 那段说明），**不新造颜色**。
 *
 * ## 为什么 CONTROL / INLINE 两档不动
 *
 * 芯片、按钮、分段器一屏能到二十个，它们要的是"与所在面板区分开"，不是"比页面亮一档"。
 * 把容器那一抬加到它们身上，顶栏那一排会糊成一片亮块。点缀档连容器底都不铺，更不受影响。
 *
 * ## 玻璃档仍然只是"好一些"，不是"到位"（如实记，别当已解决）
 *
 * 换槽位后玻璃档的抬亮 ≈ 0.28 × (47−20) ≈ **+7.5**（原来约 +5）—— 因为 alpha 才是那一档的上限，
 * 而 0.28 是真机验证过的读数，本轮**刻意不动它**。要往参考图那个量级走只有两条路：
 * 抬容器 alpha（会牺牲"透"，与上面那条读数冲突），或让采样底本身变亮（要动录制层，
 * 而那是保护域 `Navigation.kt:547` 的 `surfaceBase`）。**这一条留给真机读数之后再拍板**，
 * 不在实现里顺手改。实色档没有这个问题：它叠的是不透明的 `surfaceContainerHigh`，抬亮直接 +27。
 */
@Composable
private fun containerColorFor(tier: VeneraGlassTier): Color = when (tier) {
    VeneraGlassTier.CONTAINER -> VeneraTokens.color.surfaceContainerHigh
    VeneraGlassTier.CONTROL, VeneraGlassTier.INLINE -> MiuixTheme.colorScheme.surface
}

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
    val tier = SurfaceMaterialPolicy.tierOf(role)
    // 叠哪个色由档位决定（CONTAINER 取抬亮过的 surfaceContainerHigh，其余照旧 surface）——
    // 理由与真机读数见 [containerColorFor]。
    val base = containerColorFor(tier)
    val colors = BlurDefaults.blurColors(
        blendColors = listOf(BlendColorEntry(color = base.copy(alpha = GlassTintAlpha))),
    )
    val highlight = SurfaceMaterialPolicy.highlightFor(isDark, tier)?.let(::miuixHighlight)
    // 容器不透明度按档取。面板档在浅色下单独走 [PanelAlphaLight]（反向变暗那一条的处置，
    // 读数与判据写在那个常量上）；其余两档、以及所有档的深色一律照旧。
    val containerAlpha = when {
        isDark -> ContainerAlphaDark
        role == VeneraGlassRole.PANEL -> PanelAlphaLight
        else -> ContainerAlphaLight
    }
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
            ).background(base.copy(alpha = containerAlpha))
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

/**
 * 一块表面该用多少投影 —— **唯一出处**。与 [veneraGlassSurface] / [veneraGlassCardColors]
 * 同一条口径：玻璃档归零、实色档照旧，调用方不许自己比枚举。
 *
 * ## 为什么玻璃档必须把投影关掉（2026-10-16，用户报「浅色模式下还是有这种色块」）
 *
 * miuix `Surface` 把投影挂在 `Modifier.graphicsLayer(shadowElevation, shape, clip = false)` 上
 * （`basic/Surface.kt:128-146`），而那一格位于**调用方 modifier 之内**：
 *
 * ```
 * .veneraGlassSurface(...)            ← 调用方给的，在**外**
 *   .graphicsLayer(shadowElevation)   ← 库自己加的，在**内**
 *     .clip(shape).background(color)
 * ```
 *
 * Compose 的绘制是**外层先画**，所以真实层序是
 * **① 模糊 → ② 容器色(alpha) → ③ 平台投影 → ④ 子元素**。
 * 平铺不透明的 Surface 时 ③ 被 ② 盖住，谁也不会发现它在那儿；玻璃档 ② 是透明的
 * （"底色置透明"正是"不挡采样"的前提），**③ 那层黑剪影就从卡底里透出来了**。
 *
 * ## 读数（4 张真机截图的连通域分割，不是目测）
 *
 * 两块挂了 `shadowElevation` 的玻璃面，颜色**都**等于 `surfaceContainerHigh × 0.785`：
 * 入口药丸 `#CDBBB9`(L=0.520)、搜索卡 `#C1B3B2`(L=0.468)/`#C9B0AD`/`#BCB0AE`；
 * 而旁边不挂投影的同类表面（Info 通知 `#F7E5DF`）是满值。×0.785 逐通道吻合到 ±1，
 * ⇒ 那层黑的不透明度 ≈ **0.215**，而且**与背后是什么无关**：
 * s2 的页面底 `#A5A1A0`(L=0.36) 与 s3 的 `#EFE4E1`(L=0.79) 亮度差一倍，卡色只动 ±9。
 *
 * ## 决定性证据：投影夹在"卡底"和"子元素"之间
 *
 * 同一张卡里，胶囊是**亮**的、胶囊周围的卡底是**灰**的。按上面的层序，③ 在 ④ 之下
 * ⇒ 不透明的胶囊照常发亮、透明的卡底把黑透出来。这一条把"投影"钉死，也顺带排掉了
 * "容器 alpha 太低"那个假设：那是第 ② 层的量，动不了 ③ 之后的结果 ——
 * 上一轮把 0.22 抬到 0.86 却没消掉灰块，正是这条。
 *
 * ## n=6 vs n=2 的受控对照
 *
 * 全仓 8 处玻璃挂载点里**只有这 2 处**传了非零 `shadowElevation`
 * （`GalleryHomeChrome` 入口条 / `GallerySearchArea` 搜索卡）——
 * 而用户 4 张截图里报灰的**恰好就是这两个元素**；另外 6 处（`VeneraCard` / `VeneraChip` /
 * `VeneraFilterPill` / `VeneraControls` / `SettingsComponents` / `GallerySheetBlock`）
 * 没挂投影，截图里全部正常。miuix `Card` 自身不含 `graphicsLayer`（已逐字核过），卡片不踩这一脚。
 *
 * ## 为什么不直接在调用点写 0.dp
 *
 * 实色档**要**这个投影：它是实色面上唯一的纵深线索。所以拆成"按档取"，
 * 玻璃档的纵深交给模糊与描边高光、实色档交给投影，各归各的。
 */
@Composable
fun veneraSurfaceShadowElevation(solidElevation: Dp): Dp =
    if (veneraGlassEnabled()) 0.dp else solidElevation

/**
 * 配套：玻璃开着时容器本体必须透明，否则 Card 的不透明底会把刚采的样全盖掉。
 *
 * ⚠️ **实色档也必须抬亮**（批次 N · N1）。这一支原先直接回 `CardDefaults.defaultColors()`，
 * 而 miuix 那张卡在深色方案下只比页面底亮 +12 —— 于是"关掉玻璃就看不出分组"，
 * 而用户明令禁止只在单一档位成立的效果（那是一种假开关）。
 * 现在两档叠的是**同一个语义槽** `surfaceContainerHigh`：实色档不透明地铺它，
 * 玻璃档按容器 alpha 铺它。差别只在"透不透"，不在"亮不亮"。
 */
@Composable
fun veneraGlassCardColors(): CardColors =
    if (veneraGlassEnabled()) {
        CardDefaults.defaultColors().copy(color = Color.Transparent)
    } else {
        CardDefaults.defaultColors().copy(color = VeneraTokens.color.surfaceContainerHigh)
    }
