package com.venera.compose.components.venera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CircleShape
import com.venera.compose.data.prefs.AppearanceStyle
import com.venera.compose.ui.tokens.VeneraGlassRole
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.compose.ui.tokens.veneraGlassEnabled
import androidx.compose.material3.AlertDialog as Md3AlertDialog
import androidx.compose.material3.IconButton as Md3IconButton
import androidx.compose.material3.Slider as Md3Slider
import androidx.compose.material3.Switch as Md3Switch
import androidx.compose.material3.TextButton as Md3TextButton
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.window.WindowDialog

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
) {
    if (useMiuixWidgets) {
        MiuixSwitch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
    } else {
        Md3Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
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
) {
    if (useMiuixWidgets) {
        MiuixSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
    } else {
        Md3Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}

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
        MiuixIconButton(onClick = onClick, modifier = modifier.then(glassModifier), enabled = enabled) { content() }
    } else {
        Md3IconButton(onClick = onClick, modifier = modifier.then(glassModifier), enabled = enabled) { content() }
    }
}

/** 文字按钮。miuix 的 `TextButton` 以 `text: String` 起头、没有 slot，所以这里也不给 slot。 */
@Composable
fun VeneraTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (useMiuixWidgets) {
        MiuixTextButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled)
    } else {
        Md3TextButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text) }
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
 */
@Composable
fun VeneraDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
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
                        MiuixTextButton(text = it, onClick = onDismiss ?: onDismissRequest)
                    }
                    confirmText?.let {
                        MiuixTextButton(text = it, onClick = onConfirm ?: onDismissRequest)
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
                    VeneraTextButton(text = it, onClick = onConfirm ?: onDismissRequest)
                }
            },
            dismissButton = dismissText?.let {
                { VeneraTextButton(text = it, onClick = onDismiss ?: onDismissRequest) }
            },
        )
    }
}
