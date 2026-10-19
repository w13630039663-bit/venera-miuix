package com.venera.compose.feature.pet

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.venera.compose.gallery.data.allowAnimatedImage
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 首页顶栏的桌宠（方案 B「完整贴纸」：等比缩、不裁一刀，保住尾巴这个识别特征）。
 *
 * 摆位：落在 `VeneraTopAppBar` 的 `navigationIcon` 槽位。该槽位在 miuix 里锚定在
 * `CollapsedHeight / 2`，**与折叠进度无关** —— 大标题在它下方折叠，它自己一步都不动，
 * 所以"位置恒定"不需要写任何跟随动画。横向起点由 miuix 的 `NavigationIconPadding`（16dp）
 * 给出，正好避开 Android 的侧滑返回热区。
 *
 * 纵向受那条 52dp 行的 `clipToBounds()` 管：navigationIcon 的高度**不参与 barHeight 计算**，
 * 高于 52dp 会被上下对称裁掉。贴纸 39.5dp 高，留了一半余量。
 *
 * **刻意不可点击**：轻点台词 / 连点换姿 / 长按摸头那几档都还没有实现，
 * 摆一枚按下去什么都不发生的"陪伴物"就是假开关（本仓对这一族有判据）。
 * 要做交互，得连着台词池一起做。
 */
@Composable
fun PetMascot(modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    val context = LocalContext.current
    val animate = remember(context) { systemAnimationsEnabled(context) }
    val model = remember(animate) {
        ImageRequest.Builder(context)
            .data(PetImageLoader.ASSET_URI)
            // 这一句是本件唯一让它"会动"的开关：漏了不报错，只会静默停在首帧。
            .allowAnimatedImage(animate)
            .build()
    }
    AsyncImage(
        model = model,
        imageLoader = PetImageLoader.get(context),
        contentDescription = "小肥鱼",
        contentScale = ContentScale.Fit,
        modifier = modifier.size(tokens.spacing.petStickerWidth, tokens.spacing.petStickerHeight),
    )
}

/**
 * 系统「移除动画」那一档（过渡动画缩放 = 0）时她停在首帧。
 *
 * 读的是 `TRANSITION_ANIMATION_SCALE` 而不是 `ANIMATION_SCALE`：后者在公开 API 里**根本不存在**
 * （2026-10-16 拿 android-37 的 android.jar `javap` 量过：`Settings.Global` 上公开的只有
 * `WINDOW_ANIMATION_SCALE` 与 `TRANSITION_ANIMATION_SCALE` 两枚，`ANIMATION_SCALE` 是 @hide）。
 * 开发者选项里那个"动画时长缩放"和无障碍里的"移除动画"写的正是这两枚，取过渡那一枚
 * —— 贴纸这种跨帧变化更接近一次过渡，而不是窗口开合。
 *
 * 读数失败一律当"动画开着"：这是一条锦上添花的让步，不该因为读不到就把整只桌宠弄没。
 */
private fun systemAnimationsEnabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.TRANSITION_ANIMATION_SCALE,
        1f,
    ) > 0f
}.getOrDefault(true)
