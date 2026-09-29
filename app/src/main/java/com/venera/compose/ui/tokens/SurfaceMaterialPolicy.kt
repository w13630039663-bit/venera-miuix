package com.venera.compose.ui.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import com.venera.compose.data.prefs.SurfaceMaterial

/**
 * 内容层材质（实色 / 液态玻璃）—— 与 `AppearanceStyle`（色板/形状/字号）、`NavigationBarStyle`
 * （只管底栏）**正交的第三轴**，放在判据旁边而不是 `feature/VeneraTheme.kt`：
 * `ui/tokens` 从不 import `feature`（唯一反向依赖会让预览主题没法独立 provide）。
 *
 * 用 `compositionLocalOf` 而不是 `staticCompositionLocalOf`：static 的提供者换值时**不会**让读取方
 * 重组，切材质会留下一屏旧观感（同一条理由见 `VeneraTokens.kt:178` 那段注释）。
 *
 * 也不塞进 `VeneraTokenSet`：那个对象由 `remember(appearance)` 缓存（`VeneraTheme.kt:79`），
 * 加字段就得同步改 remember 的 key，漏改正是"切了档屏幕没跟着变"那一类 bug 的形状。
 * 材质只决定"用什么表面"，不决定间距/形状/字号阶梯。
 */
val LocalSurfaceMaterial = compositionLocalOf { SurfaceMaterial.SOLID }

/**
 * 玻璃开不开的**唯一**读取口。页面与封装件里不要再自己比枚举 —— 材质轴上"关了就什么都不做"
 * 这一条只有一个出处，才谈得上"关闭档零回归"。
 */
@Composable
@ReadOnlyComposable
fun veneraGlassEnabled(): Boolean = SurfaceMaterialPolicy.glassEnabled(LocalSurfaceMaterial.current)

/**
 * 玻璃按元素体量分成的三档。
 *
 * 分档不是为了好看，是为了**层数预算**：每上一个真模糊就是一张离屏纹理加一次高斯卷积，
 * 而一屏里的角标与小图标按钮能到二十个。所以体量决定档位，档位决定用哪条库调用。
 */
enum class VeneraGlassTier {
    /** 卡片 / 设置分组 / 面板：真模糊 + 描边高光。 */
    CONTAINER,

    /** 按钮 / 胶囊 / 分段器：真模糊 + 细描边。 */
    CONTROL,

    /** 图标按钮 / 角标 / 进度底衬：**不模糊**，只留一层染色。 */
    INLINE,
}

/** 上玻璃的元素种类。判据只认角色，不认调用方是哪个页面 —— 否则同一类元素会在各页长成两样。 */
enum class VeneraGlassRole {
    CARD,
    SETTINGS_GROUP,
    PANEL,
    BUTTON,
    CHIP,
    SEGMENTED,
    ICON_BUTTON,
    BADGE,
    PROGRESS,
}

/**
 * miuix-blur 的描边高光预设在本项目侧的名字。
 *
 * [libName] 是库里那个**属性名本身**（`top.yukonga.miuix.kmp.blur.highlight.Highlight` 的伴生属性，
 * 2026-09-29 从 0.9.4-rc01 的 sources jar 逐字读过：是扁平名 `GlassStrokeMiddleLight`，
 * 不存在 `Highlight.GlassStrokeMiddle.Light` 那种点链）。把它当数据存着而不是当场拼字符串，
 * 是为了让"库升级改名"这种事在单测里红，而不是在真机上表现为"玻璃描边静默消失"。
 */
enum class VeneraGlassHighlight(val libName: String) {
    MIDDLE_LIGHT("GlassStrokeMiddleLight"),
    MIDDLE_DARK("GlassStrokeMiddleDark"),
    SMALL_LIGHT("GlassStrokeSmallLight"),
    SMALL_DARK("GlassStrokeSmallDark"),
}

/**
 * 「界面材质」这一轴的判据层（零 Android 依赖，可单测）。
 *
 * 为什么单独一层：本项目单测没有 Robolectric，材质判据一旦写进 composable 或写进
 * `Modifier` 扩展里就没人钉得住；而这一轴最要命的两条恰恰是"默认必须是实色"与
 * "点缀档绝不真模糊"—— 都是必须能断言的东西。
 */
object SurfaceMaterialPolicy {

    /**
     * 玻璃开不开。**只看材质轴，不看 [com.venera.compose.data.prefs.AppearanceStyle]，
     * 也不看 [com.venera.compose.data.prefs.NavigationBarStyle]** —— 三轴正交是拍板过的，
     * 这里若偷偷联动，就会出现"我没开玻璃它怎么透了"或反之。
     */
    fun glassEnabled(material: SurfaceMaterial): Boolean = material == SurfaceMaterial.LIQUID_GLASS

    fun tierOf(role: VeneraGlassRole): VeneraGlassTier = when (role) {
        VeneraGlassRole.CARD, VeneraGlassRole.SETTINGS_GROUP, VeneraGlassRole.PANEL -> VeneraGlassTier.CONTAINER
        VeneraGlassRole.BUTTON, VeneraGlassRole.CHIP, VeneraGlassRole.SEGMENTED -> VeneraGlassTier.CONTROL
        VeneraGlassRole.ICON_BUTTON, VeneraGlassRole.BADGE, VeneraGlassRole.PROGRESS -> VeneraGlassTier.INLINE
    }

    /**
     * 档位 → 描边预设。深浅色各取自家那一条：Dark 系的描边本身是近白色低透明度
     * （库里 `BloomStroke` 的默认色），拿到浅色主题下会读成脏边，反之在深色下看不见。
     *
     * 点缀档返回 null = **不描边**：那么小的面积上描边只会糊掉图标，而这一档本来就不做模糊。
     */
    fun highlightFor(isDark: Boolean, tier: VeneraGlassTier): VeneraGlassHighlight? = when (tier) {
        VeneraGlassTier.CONTAINER -> if (isDark) VeneraGlassHighlight.MIDDLE_DARK else VeneraGlassHighlight.MIDDLE_LIGHT
        VeneraGlassTier.CONTROL -> if (isDark) VeneraGlassHighlight.SMALL_DARK else VeneraGlassHighlight.SMALL_LIGHT
        VeneraGlassTier.INLINE -> null
    }
}
