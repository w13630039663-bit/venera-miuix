package com.venera.compose.components.venera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import com.venera.compose.components.VeneraAmbientBackground
import com.venera.compose.data.prefs.AppearanceStyle
import com.venera.compose.data.prefs.SurfaceMaterial
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraPreviewTheme
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 组件层统一 Preview 矩阵。
 *
 * 覆盖：**三轴里的两轴** × 明暗 = 8 张 —— {MIUIX, MD3} × {SOLID, LIQUID_GLASS} × {Light, Dark}。
 * 每个组合展示关键状态：normal / selected / disabled / loading。
 *
 * 关于 error 状态：VeneraCover 的错误态需要真实网络失败才能触发，
 * 静态 Preview 无法构造，故不展示（已在组件内通过 onError 处理）。
 */

@Composable
private fun ChipRow() {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space6),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing),
    ) {
        // normal
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            VeneraChip(text = "添加条件", leadingIcon = Icons.Outlined.Add)
            VeneraChip(text = "已选条件", trailingIcon = Icons.Outlined.Close, selected = true)
            VeneraTagChip(text = "同人")
        }
        // selected
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            VeneraChip(text = "已选中", selected = true)
            VeneraTagChip(text = "选中标签", selected = true)
        }
        // disabled
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            VeneraChip(text = "不可用", enabled = false)
            VeneraTagChip(text = "禁用标签", enabled = false)
        }
        // with leading icon
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            VeneraChip(text = "搜索", leadingIcon = Icons.Outlined.Search, selected = true)
        }
        // 纯展示（onClick = null）：无按压反馈，用于卡片上的只读标签
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            VeneraChip(text = "只读", onClick = null)
            VeneraTagChip(text = "只读标签", onClick = null)
        }
    }
}

/*
 * ── 交互状态覆盖说明 ──
 *
 * VeneraChip 的 4 个交互状态中，只有 2 个能在静态 Preview 中呈现：
 *
 *  | 状态     | Preview 可呈现 | 呈现方式 |
 *  |----------|---------------|---------|
 *  | selected | ✅ 可          | 传 selected = true |
 *  | disabled | ✅ 可          | 传 enabled = false |
 *  | pressed  | ❌ 不可        | 需要真实指针按下，Preview 无输入源 |
 *  | focused  | ❌ 不可        | 需要键盘/D-pad 焦点事件 |
 *
 * pressed / focused 由 MutableInteractionSource 驱动（collectIsPressedAsState /
 * collectIsFocusedAsState），属运行时交互，只能在设备上验证。
 * 其视觉效果为：scale 1.0 → 0.96、alpha 1.0 → 0.88、focused 时描边转 primary。
 */

@Composable
private fun CardAndCoverRow() {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space6),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
    ) {
        // 正常卡片：封面 + SourceBadge 覆盖层
        Row(Modifier.width(tokens.spacing.comicCardMinWidth)) {
            VeneraCard {
                VeneraCover(url = "", contentDescription = "空 url 显示占位") {
                    VeneraSourceBadge(name = "Picacg")
                }
                Spacer(Modifier.height(tokens.spacing.cardCoverGap))
                Text(
                    text = "卡片标题（两行省略示例）",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textPrimary,
                )
            }
        }
        // 加载态卡片：骨架
        Row(Modifier.width(tokens.spacing.comicCardMinWidth)) {
            VeneraCard {
                VeneraCoverShimmer()
                Spacer(Modifier.height(tokens.spacing.cardCoverGap))
                VeneraShimmer(Modifier.fillMaxWidth().height(tokens.spacing.barHeight))
            }
        }
    }
}

@Composable
private fun ComponentMatrix() {
    // 必须套 VeneraAmbientBackground：它就是全站玻璃唯一采样源的**安装点**。
    // 不套它，LIQUID_GLASS 档的预览永远只会走"没有录制层 ⇒ no-op"那条分支，
    // 于是"两档看起来一样"会被误读成"玻璃没生效"——其实只是没人装采样层。
    VeneraAmbientBackground {
        Column(Modifier.fillMaxWidth()) {
            ChipRow()
            CardAndCoverRow()
        }
    }
}

/*
 * ── 这组「材质」预览能证明什么、不能证明什么（写在这里，别拿它当玻璃观感的验收）──
 *
 * ✅ 能证明：SOLID 那四张与改动前逐像素同形（转发件与 veneraGlass 在关闭档返回入参 Modifier
 *    本身，不建 Highlight、不 remember 任何东西）；LIQUID_GLASS 那四张**不崩、不改度量**
 *    （卡片尺寸、行高、胶囊高度两档一致 ⇒ 玻璃没有偷偷占布局）。
 * ❌ 不能证明：模糊/染色/描边高光到底透不透。`drawBackdrop` 内部先过 `isRuntimeShaderSupported()`，
 *    而预览渲染器（layoutlib）有没有 RuntimeShader 我**没有实测过** —— 不支持时它整段静默跳过，
 *    预览里看到的就是"实色"。所以"玻璃透得出氛围光、卡片不泛白、标题读得清"这三条只能在真机判。
 * ⚠️ 深色那两张还有一处已知失真：`VeneraPreviewTheme` 没有 provide `LocalVeneraDarkTheme`
 *    （补它要么引反向依赖要么改 8 个读取点，含 FROZEN 文件），于是深色预览里的氛围层
 *    按**浅色**光斑强度画 —— 别在预览里判深色玻璃的明暗。
 */

@Preview(name = "Components MIUIX Light", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMiuixLight() {
    VeneraPreviewTheme(AppearanceStyle.MIUIX, dark = false) { ComponentMatrix() }
}

@Preview(name = "Components MIUIX Dark", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMiuixDark() {
    VeneraPreviewTheme(AppearanceStyle.MIUIX, dark = true) { ComponentMatrix() }
}

@Preview(name = "Components MD3 Light", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMd3Light() {
    VeneraPreviewTheme(AppearanceStyle.MD3, dark = false) { ComponentMatrix() }
}

@Preview(name = "Components MD3 Dark", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMd3Dark() {
    VeneraPreviewTheme(AppearanceStyle.MD3, dark = true) { ComponentMatrix() }
}

@Preview(name = "Components MIUIX Light · 液态玻璃", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMiuixLightGlass() {
    VeneraPreviewTheme(AppearanceStyle.MIUIX, dark = false, material = SurfaceMaterial.LIQUID_GLASS) {
        ComponentMatrix()
    }
}

@Preview(name = "Components MIUIX Dark · 液态玻璃", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMiuixDarkGlass() {
    VeneraPreviewTheme(AppearanceStyle.MIUIX, dark = true, material = SurfaceMaterial.LIQUID_GLASS) {
        ComponentMatrix()
    }
}

@Preview(name = "Components MD3 Light · 液态玻璃", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMd3LightGlass() {
    VeneraPreviewTheme(AppearanceStyle.MD3, dark = false, material = SurfaceMaterial.LIQUID_GLASS) {
        ComponentMatrix()
    }
}

@Preview(name = "Components MD3 Dark · 液态玻璃", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMd3DarkGlass() {
    VeneraPreviewTheme(AppearanceStyle.MD3, dark = true, material = SurfaceMaterial.LIQUID_GLASS) {
        ComponentMatrix()
    }
}

/**
 * 顶栏圆座矩阵：[provideSource] 决定 `LocalTopBarBackdrop` 给不给，等价于真机上
 * 「这一屏接没接 `rememberTopBarBackdrop()`」——追更页那类自绘 chrome 就是不给的那档。
 *
 * 每颗都挂到真实配方上：普通（textPrimary 图标）/ 低频（textSecondary）/ 高亮（primary）/
 * 禁用 / 带角标。角标由**调用点**用 Box 叠，不在圆座内部 —— 与首页、本地书架两处一致。
 */
@Composable
private fun TopBarPillMatrix(provideSource: Boolean) {
    val tokens = VeneraTokens
    val source = if (provideSource) rememberTopBarBackdrop() else null
    VeneraAmbientBackground(modifier = Modifier.blurBackdropSource(source)) {
        CompositionLocalProvider(LocalTopBarBackdrop provides source) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space6),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VeneraTopBarPill(onClick = {}) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary,
                        modifier = Modifier.size(tokens.spacing.chipIconSize),
                    )
                }
                VeneraTopBarPill(onClick = {}) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "低频动作",
                        tint = tokens.color.textSecondary,
                        modifier = Modifier.size(tokens.spacing.chipIconSize),
                    )
                }
                VeneraTopBarPill(onClick = {}, enabled = false) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "禁用态",
                        tint = tokens.color.textDisabled,
                        modifier = Modifier.size(tokens.spacing.chipIconSize),
                    )
                }
                // 高亮态 + 角标：与"下载中心/源更新"那两处同构
                Box {
                    VeneraTopBarPill(onClick = {}) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "高亮态",
                            tint = tokens.color.primary,
                            modifier = Modifier.size(tokens.spacing.chipIconSize),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(tokens.spacing.space1)
                            .clip(CircleShape)
                            .background(StatusColors.Degraded),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "12",
                            fontSize = tokens.type.badge,
                            color = StatusColors.OnBadgeSurface,
                            modifier = Modifier.padding(horizontal = tokens.spacing.badgeHorizontalPadding),
                        )
                    }
                }
            }
        }
    }
}

/*
 * ── 这组「顶栏圆座」预览能证明什么、不能证明什么（别拿它当磨砂观感的验收）──
 *
 * ✅ 能证明：40dp 圆座 + 48dp 触达盒的几何、hairline 描边在明暗两档都看得见、角标贴位、
 *    以及**回落态一定有底板**（provideSource=false 那两张是半透明 surface + 描边，不是裸图标）。
 * ❌ 不能证明磨砂本身：`textureBlur` 内部先过 `isRuntimeShaderSupported()`，而预览渲染器
 *    （layoutlib）有没有 RuntimeShader **没有实测过**；不支持时 true/false 两张会长得一模一样，
 *    那**不代表**真机上也一样。
 * ❌ 不能证明按压缩放：pressed 需要真实指针事件，静态预览里 `collectIsPressedAsState` 恒 false。
 * 唯一的凭据是真机逐屏走一遍（结论记在 FREEZE-STATEMENT.md 批次 H）。
 */

@Preview(name = "顶栏圆座 MIUIX Light · 有采样层", showBackground = true, widthDp = 400)
@Composable
private fun TopBarPillMiuixLightWithSource() {
    VeneraPreviewTheme(AppearanceStyle.MIUIX, dark = false) { TopBarPillMatrix(provideSource = true) }
}

@Preview(name = "顶栏圆座 MIUIX Light · 无采样层(回落)", showBackground = true, widthDp = 400)
@Composable
private fun TopBarPillMiuixLightFallback() {
    VeneraPreviewTheme(AppearanceStyle.MIUIX, dark = false) { TopBarPillMatrix(provideSource = false) }
}

@Preview(name = "顶栏圆座 MIUIX Dark · 有采样层", showBackground = true, widthDp = 400)
@Composable
private fun TopBarPillMiuixDarkWithSource() {
    VeneraPreviewTheme(AppearanceStyle.MIUIX, dark = true) { TopBarPillMatrix(provideSource = true) }
}

@Preview(name = "顶栏圆座 MD3 Dark · 无采样层(回落)", showBackground = true, widthDp = 400)
@Composable
private fun TopBarPillMd3DarkFallback() {
    VeneraPreviewTheme(AppearanceStyle.MD3, dark = true) { TopBarPillMatrix(provideSource = false) }
}
