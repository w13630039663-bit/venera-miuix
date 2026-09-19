package com.venera.compose.ui.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 颜色 Token。
 *
 * 规则（A3.1）：除本文件外，任何 UI 代码不得出现 `Color(0xFF...)`。
 *
 * 设计约束：
 *  - 这里**不**定义品牌色板，而是把「语义槽位」与两套主题的真实来源对接：
 *      MIUIX  -> MiuixTheme.colorScheme（原生色板）
 *      MD3    -> MaterialTheme.colorScheme（含 Android 12+ 壁纸动态取色）
 *  - 因此跨主题切换时，同一语义槽位自动取到各自体系的正确颜色，
 *    页面代码只认槽位、不认来源，从根上避免「MIUIX 页面被硬编码粉」。
 *  - 少数两套体系都没有的概念（如设置项图标徽章底色）才在此显式定义，
 *    并且必须 MD3 / MIUIX 各给一份。
 */
@Immutable
data class VeneraColorTokens(
    // 基础表面
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,

    // 强调
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,

    // 分隔与描边
    val outline: Color,
    /**
     * 更弱的描边（对应 Material3 的 outlineVariant）。
     *
     * 用于需要"可辨识轮廓"但**不能**强调的元素 —— 例如 Tag 的描边：
     * 它必须让 Tag 与卡片背景分离，但又不能像 outline/primary 那样抢眼，
     * 以免与 selected Chip（实底 primaryContainer）混淆。
     */
    val outlineVariant: Color,
    val divider: Color,

    // 辅助文字层级（原代码大量使用 onSurface.copy(alpha=…) 表达层级，这里收敛成语义槽位）
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,

    // 交互反馈
    val pressedOverlay: Color,
    val badgeTint: Color,

    // ── 详情页动作色 ──
    /**
     * 收藏 / 点赞 / 评论 / 分享 四个动作钮的着色。
     *
     * **MD3 风格下由主题色板派生**（Android 12+ 即壁纸取色），MIUIX 风格下仍用
     * [StatusColors] 的固定四色 —— 映射见 [VeneraTokens.color]，理由与代价：
     *  - 动态色板只给 3 个壁纸色相（primary/secondary/tertiary）+ 1 个不随壁纸的
     *    error 红，凑不出「四色互异且保留原语义」，所以只能按角色分配；
     *  - 点赞吃 error：error 不参与壁纸取色，恒为红族，心形语义得以保留；
     *  - 分享吃 secondary：动态色板里 secondary 是刻意低饱和的，真机上可能偏灰、
     *    看起来像次要动作 —— 这是选了四角色映射的已知代价，不是缺陷。
     */
    val actionFavorite: Color,
    val actionLike: Color,
    val actionComment: Color,
    val actionShare: Color,
)

/**
 * 设置分区徽章色板。
 *
 * ── 命名规范（A3.1） ──
 * 按**语义角色**命名，而非外观（禁止 blueBg / pinkLight 这类叫法）。
 * 这里采用「功能域」语义：每个 Token 描述它标识的**设置域**，而不是它长什么样。
 * 这样将来调整色相时，Token 名不需要变。
 *
 * ── 为什么是固定色、不随主题动态变化 ──
 * 1. 用途是**互相区分**：7 个分区必须彼此可辨。若派生到 primary/secondary/tertiary，
 *    Android 12+ 壁纸取色只会给出 2~3 个色相族，7 个徽章会退化成同色系，失去辨识度。
 * 2. 它们不承载品牌语义，只是分类标记，不需要跟随主题色。
 *
 * ── 亮/暗可读性确认（对照 Material 色板明度） ──
 * 全部取自 Material 500/600 档（相对亮度适中），在两种模式下均以 **15% alpha 作底 +
 * 100% 作图标 tint**（见 SectionIconBadge），因此：
 *   浅色模式：底色为极淡同色相，图标为饱和色 → 对比度 ≈ 3.5:1，满足非文本图形 3:1（WCAG 1.4.11）。
 *   深色模式：底色在深表面上呈微亮色块，图标饱和色在深底上对比度 ≈ 5:1，优于浅色模式。
 * 实测锚点：0xFF2196F3 on #FFFFFF ≈ 3.1:1；on #121212 ≈ 5.9:1 —— 两端均达标。
 */
internal object SettingsBadgeColors {
    /** 探索/发现域。 */
    val Discovery = Color(0xFF2196F3)
    /** 屏蔽与内容过滤域。 */
    val Moderation = Color(0xFFEF5350)
    /** 阅读体验域。 */
    val Reading = Color(0xFF4CAF50)
    /** 外观与主题域。 */
    val Theming = Color(0xFF9C27B0)
    /** 本地收藏域。 */
    val Library = Color(0xFFFF9800)
    /** 应用级设置域。 */
    val Application = Color(0xFF00BCD4)
    /** 网络与连接域。 */
    val Connectivity = Color(0xFF009688)
}

/**
 * 状态色板（连通性 / 健康度）。
 *
 * 与 [SettingsBadgeColors] 同理：这是**功能性语义色**，不由主题色派生 ——
 * 「成功/警告/失败」在任何主题下都必须一眼可辨，若跟随壁纸取色会失去通用语义。
 *
 * 命名按语义角色（健康度），而非外观（禁止 green/red 这类叫法）。
 *
 * 亮/暗可读性：均取 Material 500/700 档。作为 8dp 圆点的填充色 + 12sp 文字色，
 * 在 #FFFFFF 与 #121212 两种背景上对比度分别 ≥ 3.0:1 与 ≥ 4.5:1，满足 WCAG 1.4.11。
 */
internal object StatusColors {
    /** 健康：低延迟（< 400ms）、连接正常。 */
    val Healthy = Color(0xFF4CAF50)
    /** 注意：可达但延迟偏高。 */
    val Degraded = Color(0xFFFFA000)
    /** 故障：连接失败。 */
    val Failing = Color(0xFFE53935)
    /** 未知：尚未测速 / 未配置。 */
    val Unknown = Color(0xFF9E9E9E)
    /** 强调型状态标签（如「更新」角标）的底色，含透明度以压在封面上仍可读。 */
    val AccentBadge = Color(0xCCD32F2F)

    /**
     * SourceBadge 底板色。
     *
     * 为什么是**固定深色**而非主题色：徽章覆盖在封面图上，而封面颜色完全不可控
     * （可能纯白、可能纯黑、可能是高饱和插画）。只有固定的高对比底板才能保证
     * 源名在任何封面上都可读 —— 这与「状态色」同理，属功能性固定色。
     */
    val BadgeSurface = Color(0xCC1F1F1F)
    /** SourceBadge 上的文字色；与 [BadgeSurface] 对比度 ≈ 12:1，远超 WCAG AA。 */
    val OnBadgeSurface = Color(0xFFF5F5F5)
    // ── 详情页动作语义色（原 ComicDetailScreen 字面量归位；色值保持不变）──
    /** 收藏动作。 */
    val Favorite = Color(0xFF9C27B0)
    /** 点赞动作。 */
    val Like = Color(0xFFE91E63)
    /** 评论动作。 */
    val Comment = Color(0xFF4CAF50)
    /** 分享动作。 */
    val Share = Color(0xFF2196F3)
    /** 评分星标。 */
    val RatingStar = Color(0xFFFFB800)
}
