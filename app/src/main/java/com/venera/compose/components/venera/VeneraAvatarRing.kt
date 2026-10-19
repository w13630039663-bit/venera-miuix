package com.venera.compose.components.venera

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.Composable
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 头像那圈**发丝描边**（2026-10-17 第二十三轮，用户「给页面所有的头像都加一层淡淡的描边」）。
 *
 * ## 为什么需要它
 *
 * 裸圆头像压在白卡上时，圆与卡面之间只有**色差**、没有边界线。真机滑动时色差会被抗锯齿吃掉，
 * 读起来是"糊了一圈"而不是"一个圆"；截图里那排画师头像就是这个问题。
 * 一圈 1dp 的 `outlineVariant`（色板里那一档本身就叫"更弱的描边"）把轮廓钉回来，
 * 而它在浅底上只多出一档灰，不会读成"描边按钮"。
 *
 * ## 顺序（踩过的坑）
 *
 * `border` 与 `background` 同语义：它画的是「**它之后**所有 modifier 决定的尺寸」。
 * 所以调用点必须把决定尺寸的那颗（如 `.size(64.dp)`）放在前面，**不能**放在后面 ——
 * 放在后面就变成"按后面那层的尺寸描边"，视觉上是细一圈或者干脆没画出来。
 *
 * `clip` 放在最外层：圆头像的图是直角矩形，不裁就是方的。铺色排在 `clip` 之后，
 * 否则那张占位底色会带直角从圆边上漏出来。
 *
 * @param shape 圆头像传 [androidx.compose.foundation.shape.CircleShape]；
 *   卡里那枚小头像是圆角方形（照抄画师那一排的站方原样，不强行改成圆的）。
 */
@Composable
fun Modifier.veneraAvatarRing(shape: Shape): Modifier {
    val tokens = VeneraTokens
    return this
        .clip(shape)
        .border(tokens.spacing.hairline, tokens.color.outlineVariant, shape)
}