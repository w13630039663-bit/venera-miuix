package com.venera.compose.components.venera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton as Md3FilledTonalButton
import androidx.compose.material3.OutlinedButton as Md3OutlinedButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import androidx.compose.material3.MaterialTheme as Md3MaterialTheme
import androidx.compose.material3.OutlinedTextField as Md3OutlinedTextField
import com.venera.compose.data.platform.AppearanceStyle
import com.venera.compose.ui.tokens.VeneraGlassRole
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.ui.tokens.veneraGlassEnabled
import androidx.compose.material3.AlertDialog as Md3AlertDialog
import androidx.compose.material3.IconButton as Md3IconButton
import androidx.compose.material3.Slider as Md3Slider
import androidx.compose.material3.Switch as Md3Switch
import androidx.compose.material3.SliderDefaults as Md3SliderDefaults
import androidx.compose.material3.SwitchDefaults as Md3SwitchDefaults
import top.yukonga.miuix.kmp.basic.SliderDefaults as MiuixSliderDefaults
import top.yukonga.miuix.kmp.basic.SwitchDefaults as MiuixSwitchDefaults
import androidx.compose.material3.TextButton as Md3TextButton
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button as Md3Button
import androidx.compose.material3.ButtonDefaults as Md3ButtonDefaults
import androidx.compose.material3.Checkbox as Md3Checkbox
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox

/**
 * 控件族的**后端选择处**。
 *
 * 参数只取两家的**交集**（`checked/onCheckedChange/modifier/enabled` 这一层），不发明第三方参数：
 * 一发明参数就会出现"某个后端接了、另一个后端偷偷忽略"的假开关。所以像 miuix `Slider` 独有的
 * `showKeyPoints/keyPoints/magnetThreshold`、M3 `Switch` 独有的 `thumbContent` 都**不进签名**。
 *
 * 后端跟着 [AppearanceStyle] 走，**不跟材质轴**：材质只管表面透不透，管不着这是谁的开关。
 * ⚠️ 这一条的代价要说明白：`AppearanceStyle` 的默认值是 MIUIX（`VeneraPreferences.kt:298`），
 * 所以 Miuix 档下这些控件从此**真的**是 Miuix 的形态 —— 这正是"UI 完全符合 Miuix 规范"那条诉求，
 * 但它不等于"零变化"。逐像素零回归的保证只覆盖：材质轴关着时、以及 MD3 档。
 */
private val useMiuixWidgets: Boolean
    @Composable @ReadOnlyComposable get() = VeneraTokens.appearance == AppearanceStyle.MIUIX

/**
 * 开关。两家的 `checked/onCheckedChange/modifier/enabled` 四件同形，
 * 且 `onCheckedChange` **两边都可空**（miuix `Switch.kt:66` 是 `((Boolean) -> Unit)?`，
 * material3 1.5.0-alpha22 同样可空）—— 调用点就是这么用的（设置页把"禁用"表达成回传 null），
 * 这里收成非空就会逼调用点写 `?: {}` 那种吞掉空值的假代码。
 */
@Composable
fun VeneraSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedThumbColor: Color? = null,
) {
    if (useMiuixWidgets) {
        MiuixSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = checkedThumbColor?.let {
                MiuixSwitchDefaults.switchColors().copy(checkedThumbColor = it)
            } ?: MiuixSwitchDefaults.switchColors(),
        )
    } else {
        Md3Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = checkedThumbColor?.let {
                Md3SwitchDefaults.colors(checkedThumbColor = it)
            } ?: Md3SwitchDefaults.colors(),
        )
    }
}

/**
 * 滑条。`steps` 两家都有（miuix `Slider.kt` 里就有 `steps: Int = 0`，并 `require(steps >= 0)`），
 * 所以这一件是能真双后端的 —— 之前有一版判断说"miuix 没有 steps"，读了签名才知道是错的。
 */
@Composable
fun VeneraSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    thumbColor: Color? = null,
    activeTrackColor: Color? = null,
    inactiveTrackColor: Color? = null,
) {
    // 三家同义的位子在两家里名字不同：M3 的 active/inactiveTrackColor 就是 miuix 的
    // foreground/background（miuix `SliderColors` 只有前景/背景轨道两色）。
    // 传 null 一律落回**各自后端的默认值**，转发件不自带色值 —— 否则另一档会被连带改脸。
    if (useMiuixWidgets) {
        // miuix 的 SliderColors 字段是 private val，读不到只能逐位 copy（copy 的形参是公开的）。
        val base = MiuixSliderDefaults.sliderColors()
        val withThumb = if (thumbColor != null) base.copy(thumbColor = thumbColor) else base
        val withActive =
            if (activeTrackColor != null) withThumb.copy(foregroundColor = activeTrackColor) else withThumb
        val miuixColors =
            if (inactiveTrackColor != null) withActive.copy(backgroundColor = inactiveTrackColor) else withActive
        MiuixSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = miuixColors,
        )
    } else {
        // M3 的工厂自己认 `Color.Unspecified` = 用主题默认，所以 null 直接翻成它即可。
        Md3Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = Md3SliderDefaults.colors(
                thumbColor = thumbColor ?: Color.Unspecified,
                activeTrackColor = activeTrackColor ?: Color.Unspecified,
                inactiveTrackColor = inactiveTrackColor ?: Color.Unspecified,
            ),
        )
    }
}

/**
 * 图标按钮的触达位边长 —— **全仓只有这一处写这个数**。
 *
 * 48dp 不是新造的数：它就是 M3 `IconButton` 的默认触达尺寸，也是 Android 无障碍的触达位下限。
 * miuix 的 `IconButtonDefaults.MinWidth/MinHeight` 是 40dp，直接吃默认会让全仓每一颗图标按钮
 * 的触摸区悄悄缩掉 8dp（源管理列表那排最先被看出来）。
 *
 * 公开它还有一个用途：要给一排钮**留空位**（不是留假钮）时，占位必须与真实触达位同尺寸，
 * 否则那排钮会因为少一枚而整体挪位 —— 画廊大图页那条 dock 的「关于这张图」就靠这条不动。
 */
val VeneraIconButtonTouchSize = 48.dp

/**
 * 图标按钮。两家的 content 类型不同（M3 是 `RowScope`，miuix 是普通 lambda），
 * 对外统一成**普通 lambda**：M3 那一侧传 `{ content() }`，receiver 不用即可，
 * 不为此额外塞一个 `Row` 进布局（塞了就会改测量结果，那是观感回归的源头）。
 *
 * 材质轴开着时给一层 [VeneraGlassRole.ICON_BUTTON]（点缀档：**只染色不模糊** ——
 * 一屏二十个图标按钮，逐个建离屏就是层数炸弹）。
 */
@Composable
fun VeneraIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val glassModifier = if (veneraGlassEnabled()) {
        Modifier.veneraGlass(role = VeneraGlassRole.ICON_BUTTON, shape = CircleShape)
    } else {
        Modifier
    }
    if (useMiuixWidgets) {
        MiuixIconButton(
            onClick = onClick,
            modifier = modifier.then(glassModifier),
            enabled = enabled,
            minWidth = VeneraIconButtonTouchSize,
            minHeight = VeneraIconButtonTouchSize,
        ) { content() }
    } else {
        Md3IconButton(onClick = onClick, modifier = modifier.then(glassModifier), enabled = enabled) { content() }
    }
}

/**
 * 破坏性操作用**错误色**，其余用各家自己的常规色。单独抽成纯函数只为一件事：
 * M3 的 `TextButton` 默认无底、字色走 `contentColor`，miuix 的默认是**次要色胶囊 +
 * onSecondaryVariant 文字** —— 两边的"常规色"根本不是同一个值，所以颜色必须由各后端
 * 自己的默认色当 `normal` 传进来，只替换破坏性那一支；写死一个色值就会让 MD3 档跟着变脸。
 * 错误色两家共用 M3 的 `colorScheme.error`（miuix 的 `ColorScheme` 里没有 error 位）。
 */
fun veneraTextColor(normal: Color, destructiveColor: Color, destructive: Boolean): Color =
    if (destructive) destructiveColor else normal

/** 文字按钮。miuix 的 `TextButton` 以 `text: String` 起头、没有 slot，所以这里也不给 slot。 */
@Composable
fun VeneraTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val error = Md3MaterialTheme.colorScheme.error
    if (useMiuixWidgets) {
        val base = MiuixButtonDefaults.textButtonColors()
        MiuixTextButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = base.copy(textColor = veneraTextColor(base.textColor, error, destructive)),
        )
    } else {
        // 不用 `textButtonColors().copy(...)`：M3 1.5.0-alpha22 的 ButtonColors 不是 data class，
        // 没有 copy。常规色直接写 M3 TextButton 的默认值（primary），两家各走各的默认。
        Md3TextButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = Md3ButtonDefaults.textButtonColors(
                contentColor = veneraTextColor(Md3MaterialTheme.colorScheme.primary, error, destructive),
            ),
        ) { Text(text) }
    }
}

/**
 * 按钮的**语义变体**（控件体系统一 · 阶段 1）。选变体先看语义，再看外观：
 *
 * - [Primary]     主要操作：确认、登录、开始下载。一屏一个，别把两个主行动并排放。
 * - [Secondary]   普通辅助操作：与主行动同框但不是主角（ tonal 容器档）。
 * - [Outline]     有明确边界的次级入口：跳转、外链、"网页登录"这类**入口**而非终点。
 * - [Ghost]       取消、返回、关闭等低强调操作：无底无框，纯内容。
 * - [Destructive] 删除、清空等破坏性操作：错误色实底，任何页面不得用别的色表达"危险"。
 */
enum class VeneraButtonVariant { Primary, Secondary, Outline, Ghost, Destructive }

/**
 * 按钮尺寸档（高/内边距的数值出处见 [VeneraSpacingTokens.buttonHeightCompact] 一组 Token）。
 * 视觉高度与命中区分开管理：[Compact] 只是视觉矮，触达位由各后端的 min 尺寸另保。
 */
enum class VeneraButtonSize { Compact, Medium, Large }

/** 一个尺寸档落到布局上的两个数（最小高 + 内容水平内边距）。 */
data class VeneraButtonMetrics(val minHeight: Dp, val horizontalPadding: Dp)

/**
 * 尺寸档 → 布局量的纯映射。数值全部由调用方传入（组件从 Token 取），
 * 本函数只保证"档位 ↔ 数值"这条路是可断言的，不在组件里散 if。
 */
fun veneraButtonMetrics(
    size: VeneraButtonSize,
    compactHeight: Dp,
    mediumHeight: Dp,
    largeHeight: Dp,
    horizontalPadding: Dp,
): VeneraButtonMetrics = when (size) {
    VeneraButtonSize.Compact -> VeneraButtonMetrics(compactHeight, horizontalPadding)
    VeneraButtonSize.Medium -> VeneraButtonMetrics(mediumHeight, horizontalPadding)
    VeneraButtonSize.Large -> VeneraButtonMetrics(largeHeight, horizontalPadding)
}

/**
 * 变体 → 颜色规格的**纯映射**（MIUIX 后端消费；MD3 走各原生变体，两者语义必须一致，
 * 由 [VeneraButtonSpecTest] 把这条一致性钉住）。
 *
 * 为什么 Outline/Ghost 的禁用底是透明而不是灰：这两个变体的底本来就是"没有底"，
 * 禁用时往底上加灰会让"幽灵按钮"变成一块灰饼 —— 禁用只降内容色。
 *
 * 为什么返回 [Color.Transparent] 而不是 `null`：透明底是真实状态（要画按下反馈的面），
 * 与"无边框"（[borderColor] 为 null）不是一个意思。
 */
data class VeneraButtonSpec(
    val container: Color,
    val content: Color,
    /** 描边色；null = 本变体不描边。 */
    val borderColor: Color?,
    val disabledContainer: Color,
    val disabledContent: Color,
)

fun veneraButtonSpec(
    variant: VeneraButtonVariant,
    primary: Color,
    onPrimary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    error: Color,
    onError: Color,
    outline: Color,
    disabledContainer: Color,
    disabledContent: Color,
): VeneraButtonSpec = when (variant) {
    VeneraButtonVariant.Primary ->
        VeneraButtonSpec(primary, onPrimary, null, disabledContainer, disabledContent)
    VeneraButtonVariant.Secondary ->
        VeneraButtonSpec(secondaryContainer, onSecondaryContainer, null, disabledContainer, disabledContent)
    VeneraButtonVariant.Outline ->
        VeneraButtonSpec(Color.Transparent, primary, outline, Color.Transparent, disabledContent)
    VeneraButtonVariant.Ghost ->
        VeneraButtonSpec(Color.Transparent, primary, null, Color.Transparent, disabledContent)
    VeneraButtonVariant.Destructive ->
        VeneraButtonSpec(error, onError, null, disabledContainer, disabledContent)
}

/**
 * 填充按钮（控件体系统一 · 阶段 1 扩展）。两家的 `Button` content **都是 `RowScope` 槽位**，
 * 所以签名是真交集 —— 建这一件的理由：弹窗里的"提交/登录"按钮要在文字前面挂进度条，
 * 而 `VeneraTextButton` 走的是 `text: String`，表达不了"按钮里有子件"。
 *
 * 变体在两家的映射（语义一致、实现各异 —— 这是"共用语义和 Token、底层实现不同"那条原则）：
 *
 * | Variant      | MD3                  | MIUIX                                       |
 * |--------------|----------------------|---------------------------------------------|
 * | Primary      | `Button` 默认         | `MiuixButton` 默认（submit 实底）             |
 * | Secondary    | `FilledTonalButton`  | `MiuixButton` + secondaryContainer 实底      |
 * | Outline      | `OutlinedButton`     | `MiuixButton` 透明底 + `Modifier.border`     |
 * | Ghost        | `TextButton`         | `MiuixButton` 透明底、无框                   |
 * | Destructive  | `Button` + error 色位 | `MiuixButton` + error 实底                   |
 *
 * Outline 在 MIUIX 是**真实边框适配**（`ButtonColors` 没有描边位，边框由 Modifier 画，
 * 圆角与按钮本体同档），不是把它静默显示成普通填充按钮 —— 旧头注里"miuix 没有对应物"
 * 的结论对**库原生**成立，对本转发件不成立：我们自己把描边补上了。
 *
 * @param loading 异步进行中。真 isLoading 时整颗按钮**不可点**（`enabled && !loading`），
 *   并在最前面挂一枚与内容同色的细进度环 —— 调用点原来手写"if (busy) 环 else 文字"的
 *   那套可以删了，但**状态来源不变**：loading 仍由调用方的异步状态驱动，本组件不自持状态。
 */
@Composable
fun VeneraButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: VeneraButtonVariant = VeneraButtonVariant.Primary,
    size: VeneraButtonSize = VeneraButtonSize.Medium,
    loading: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = VeneraTokens
    val metrics = veneraButtonMetrics(
        size = size,
        compactHeight = tokens.spacing.buttonHeightCompact,
        mediumHeight = tokens.spacing.buttonHeightMedium,
        largeHeight = tokens.spacing.buttonHeightLarge,
        horizontalPadding = tokens.spacing.buttonHorizontalPadding,
    )
    val clickEnabled = enabled && !loading

    // loading 环要跟内容同色，否则在 Ghost/Outline 上会悬出一枚谁也不认的亮环。
    val loadingColor: Color
    val decorated: @Composable RowScope.() -> Unit
    if (useMiuixWidgets) {
        val mc = MiuixTheme.colorScheme
        val spec = veneraButtonSpec(
            variant = variant,
            primary = mc.primary,
            onPrimary = mc.onPrimary,
            secondaryContainer = mc.secondaryContainer,
            onSecondaryContainer = mc.onSecondaryContainer,
            error = Md3MaterialTheme.colorScheme.error,
            onError = Md3MaterialTheme.colorScheme.onError,
            outline = tokens.color.outline,
            disabledContainer = mc.disabledPrimaryButton,
            disabledContent = mc.disabledOnPrimaryButton,
        )
        loadingColor = if (clickEnabled) spec.content else spec.disabledContent
        val shape = RoundedCornerShape(MiuixButtonDefaults.CornerRadius)
        val base = MiuixButtonDefaults.buttonColors()
        val colors = base.copy(
            color = spec.container,
            disabledColor = spec.disabledContainer,
            contentColor = spec.content,
            disabledContentColor = spec.disabledContent,
        )
        val borderModifier = spec.borderColor?.let {
            Modifier.border(tokens.spacing.hairline, it, shape)
        } ?: Modifier
        decorated = {
            if (loading) {
                VeneraLoadingDot(color = loadingColor)
                Spacer(Modifier.width(tokens.spacing.space3))
            }
            content()
        }
        MiuixButton(
            onClick = onClick,
            modifier = modifier.then(borderModifier),
            enabled = clickEnabled,
            minHeight = metrics.minHeight,
            colors = colors,
            insideMargin = PaddingValues(
                horizontal = metrics.horizontalPadding,
                vertical = tokens.spacing.space3,
            ),
            content = decorated,
        )
    } else {
        val cs = Md3MaterialTheme.colorScheme
        loadingColor = cs.primary
        decorated = {
            if (loading) {
                VeneraLoadingDot(color = loadingColor)
                Spacer(Modifier.width(tokens.spacing.space3))
            }
            content()
        }
        // MD3 走各原生变体（禁用/高度/水波纹都是库自己的完整行为），不套 MIUIX 那份 spec。
        val heightModifier = modifier.heightIn(min = metrics.minHeight)
        when (variant) {
            VeneraButtonVariant.Primary ->
                Md3Button(onClick = onClick, modifier = heightModifier, enabled = clickEnabled, content = decorated)
            VeneraButtonVariant.Secondary ->
                Md3FilledTonalButton(onClick = onClick, modifier = heightModifier, enabled = clickEnabled, content = decorated)
            VeneraButtonVariant.Outline ->
                Md3OutlinedButton(onClick = onClick, modifier = heightModifier, enabled = clickEnabled, content = decorated)
            VeneraButtonVariant.Ghost ->
                Md3TextButton(onClick = onClick, modifier = heightModifier, enabled = clickEnabled, content = decorated)
            VeneraButtonVariant.Destructive ->
                Md3Button(
                    onClick = onClick,
                    modifier = heightModifier,
                    enabled = clickEnabled,
                    colors = Md3ButtonDefaults.buttonColors(
                        containerColor = cs.error,
                        contentColor = cs.onError,
                    ),
                    content = decorated,
                )
        }
    }
}

/** 按钮内那枚加载环的尺寸（16dp = [VeneraSpacingTokens.chipIconSize]，与图标同档）。 */
@Composable
private fun VeneraLoadingDot(color: Color) {
    CircularProgressIndicator(
        modifier = Modifier.size(VeneraTokens.spacing.chipIconSize),
        strokeWidth = VeneraTokens.spacing.space1,
        color = color,
    )
}

/** M3 的复选框是 `Boolean`，miuix 的是 `ToggleableState`（三态里的 Indeterminate 本仓不用）。 */
fun veneraToggleState(checked: Boolean): ToggleableState =
    if (checked) ToggleableState.On else ToggleableState.Off

/**
 * `onCheckedChange = null` 在两家都表示"只展示、不可交互"（M3 靠可空回调、miuix 靠
 * `onClick: (() -> Unit)?`）。这里必须把 null 原样传下去 —— 换成 `{}` 会让禁用态的
 * 复选框照样翻转状态，那是行为回归，不是观感差异。
 */
fun veneraCheckClick(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?): (() -> Unit)? =
    onCheckedChange?.let { handler -> { handler(!checked) } }

/** 复选框。签名与 [VeneraSwitch] 同形（`checked/onCheckedChange/modifier/enabled`）。 */
@Composable
fun VeneraCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (useMiuixWidgets) {
        MiuixCheckbox(
            state = veneraToggleState(checked),
            onClick = veneraCheckClick(checked, onCheckedChange),
            modifier = modifier,
            enabled = enabled,
        )
    } else {
        Md3Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
    }
}

/**
 * `isError` → 描边色。单独抽成纯函数只为一件事：这个参数**只有 M3 后端原生支持**，
 * miuix 侧必须有人显式映射，否则它会变成一个"编译得过、传得进去、屏幕上没有"的假开关。
 * 错误态一律赢（调用点会把"禁用灰"当 default 传进来，见 `VeneraTextFieldMappingTest` 第三条）。
 */
fun veneraFieldBorderColor(defaultBorder: Color, errorBorder: Color, isError: Boolean): Color =
    if (isError) errorBorder else defaultBorder

/**
 * miuix 的输入框**只有 label 一个文本槽**（`TextField.kt:301-302`：`label: String` +
 * `useLabelAsPlaceholder`），M3 却是 label 与 placeholder 两个槽。规则：
 * 有标签就用标签（Miuix 的标签本来就占着空态那格，提示文字是重复表达）；
 * 没标签时把占位符顶上标签位并声明它当占位符用。两者皆空就是真没有。
 * 抽成纯函数是为了让"两个都给"这种冲突有人当场判，而不是到了屏幕上悄悄丢一个。
 */
data class VeneraFieldLabelSlot(val text: String, val asPlaceholder: Boolean)

fun veneraFieldLabelSlot(label: String, placeholder: String): VeneraFieldLabelSlot = when {
    label.isNotBlank() -> VeneraFieldLabelSlot(label, asPlaceholder = false)
    placeholder.isNotBlank() -> VeneraFieldLabelSlot(placeholder, asPlaceholder = true)
    else -> VeneraFieldLabelSlot("", asPlaceholder = false)
}

/**
 * 输入框。签名取两家交集：`value/onValueChange/modifier/enabled/label/placeholder/singleLine/
 * visualTransformation/isError/leadingIcon/trailingIcon`（miuix `TextField.kt:294` 那一个重载就是
 * `String` 版，图标是 `@Composable (() -> Unit)?`，`visualTransformation` 两家同形 ——
 * 密码与 API Key 三类输入全靠它，丢了就是明文上屏）。
 *
 * 两处刻意不对等，都写成可断言的纯函数而不是"传进去算了"：
 * ① `isError`：miuix 没这个参数，映射成**描边换错误色**（见 [veneraFieldBorderColor]）；
 * ② `placeholder`：miuix 没有独立槽位，映射见 [veneraFieldLabelSlot]。
 * 错误**文字**本来就在外层由调用点自己画（设置页三处都是 `Text(error)`），转发件不重复表达。
 *
 * `label` 用空串表示"没有标签"：miuix 的 label 是 `String` 而非可空槽位，
 * 两家都以空白串为无标签，调用点不必各写一套 null 判断。
 *
 * `textStyle` 可空是刻意的：两家的默认字面各自来自自己的主题（miuix `textStyles.main`、
 * M3 `typography.bodyLarge`），转发件**不替调用点决定默认值**，只在它真传了的时候覆盖，
 * 否则一覆盖就会把另一档的排版偷偷改掉。
 */
@Composable
fun VeneraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "",
    placeholder: String = "",
    singleLine: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle? = null,
    isError: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    if (useMiuixWidgets) {
        val slot = veneraFieldLabelSlot(label, placeholder)
        val baseColors = TextFieldDefaults.textFieldColors()
        MiuixTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            label = slot.text,
            useLabelAsPlaceholder = slot.asPlaceholder,
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            textStyle = textStyle ?: MiuixTheme.textStyles.main,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            colors = baseColors.copy(
                borderColor = veneraFieldBorderColor(
                    defaultBorder = baseColors.borderColor,
                    errorBorder = Md3MaterialTheme.colorScheme.error,
                    isError = isError,
                ),
            ),
        )
    } else {
        Md3OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            singleLine = singleLine,
            isError = isError,
            visualTransformation = visualTransformation,
            textStyle = textStyle ?: Md3MaterialTheme.typography.bodyLarge,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            label = if (label.isBlank()) null else {
                { Text(label) }
            },
            placeholder = if (placeholder.isBlank()) null else {
                {
                    Text(
                        text = placeholder,
                        color = Md3MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

/**
 * 对话框。主体只有一个槽位（[content]），因为两家在这里的差别仅是**名字**：
 * M3 叫 `text: ColumnScope.() -> Unit`，miuix 叫 `content: () -> Unit`。按钮两家都是"放进去的"，
 * 只是 M3 有专用 `confirmButton/dismissButton` 槽、miuix 要自己排在 content 里 ——
 * 所以按钮走 [confirmText]/[dismissText] 这四件参数，两家各自摆位，**调用点不碰**。
 *
 * `show` 而不是 `if (show) { … }`：miuix 的窗口件自己管进出场，把它包在 if 里会丢掉退场动画；
 * M3 侧反过来必须条件组合（`AlertDialog` 没有 show 参数），这一差异**封在这里面**。
 *
 * ⚠️ **这一件刻意不参与材质轴（液态玻璃）**，别来"补统一"：`WindowDialog` 的内容跑在独立的
 * `androidx.compose.ui.window.Dialog` 窗口里，而玻璃采样的是**宿主窗口**的 RenderNode ——
 * 跨窗口取不到，贴上去只会是一块空白底板。弹窗在玻璃档保持实底是物理限制，不是漏做。
 *
 * @param onConfirm 省略时回落 [onDismissRequest]（M3 的确认框常有"点确定=关闭"的形态）。
 * @param confirmDestructive 确认按钮走错误色（"注销/删除"这类破坏性动作），两家都认，
 *   映射与 [VeneraTextButton] 共用同一条 [veneraTextColor]。
 * @param buttonsEnabled 两颗按钮的可用性闸门。源管理的"批量更新"弹窗在跑进度时要同时锁住
 *   两颗（调用点本来就是同一个条件），所以只给一个参数，不给 `confirmEnabled/dismissEnabled` 两位
 *   —— 两位参数会诱导出"只锁一颗"的调用点，而那是这两家都没保证能对齐的形态。
 */
@Composable
fun VeneraDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmDestructive: Boolean = false,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    buttonsEnabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    if (useMiuixWidgets) {
        WindowDialog(show = show, modifier = modifier, title = title, onDismissRequest = onDismissRequest) {
            Column(Modifier.fillMaxWidth()) {
                content?.invoke()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(VeneraTokens.spacing.space3, Alignment.End),
                ) {
                    dismissText?.let {
                        MiuixTextButton(
                            text = it,
                            onClick = onDismiss ?: onDismissRequest,
                            enabled = buttonsEnabled,
                        )
                    }
                    confirmText?.let {
                        val base = MiuixButtonDefaults.textButtonColors()
                        MiuixTextButton(
                            text = it,
                            onClick = onConfirm ?: onDismissRequest,
                            enabled = buttonsEnabled,
                            colors = base.copy(
                                textColor = veneraTextColor(
                                    base.textColor,
                                    Md3MaterialTheme.colorScheme.error,
                                    confirmDestructive,
                                ),
                            ),
                        )
                    }
                }
            }
        }
    } else if (show) {
        Md3AlertDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            title = { Text(title) },
            text = content?.let { { it() } },
            confirmButton = {
                confirmText?.let {
                    VeneraTextButton(
                        text = it,
                        onClick = onConfirm ?: onDismissRequest,
                        enabled = buttonsEnabled,
                        destructive = confirmDestructive,
                    )
                }
            },
            dismissButton = dismissText?.let {
                {
                    VeneraTextButton(
                        text = it,
                        onClick = onDismiss ?: onDismissRequest,
                        enabled = buttonsEnabled,
                    )
                }
            },
        )
    }
}
