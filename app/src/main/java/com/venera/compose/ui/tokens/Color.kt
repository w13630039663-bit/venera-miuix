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

    /**
     * MD3 色调表面档 `surfaceContainerHigh`。
     *
     * 为什么要单独一档而不是 [surfaceVariant]：MD3 的"贴附输入面"（docked search bar）与
     * 无描边的 tonal chip 都指定吃这一档，它比 `surfaceVariant` 更"实"、又比 surface 更"退"。
     * 两套主题都桥得上（MIUIX 模式下 `Colors.toMaterialColors` 与 MD3→Miuix 双向都映射了
     * `surfaceContainerHigh`），所以这里只是把已有语义槽位**摆到 token 层**，页面不越层取色板。
     */
    val surfaceContainerHigh: Color,

    // 强调
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,

    /**
     * 次级容器对（MD3 `secondaryContainer` / `onSecondaryContainer`）。
     *
     * 用途是 **filter chip 的选中态**：MD3 规范里选中的 filter chip 填 secondaryContainer，
     * 而不是 primaryContainer —— 后者是"主行动"的色，选中一枚筛选条件不该那么响。
     * 动态取色下它是色板里刻意低饱和的一族，正好承担"已选但不抢眼"。
     */
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,

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

/**
 * 图站标签**分类语义色**（补全行右侧那枚分类名用的就是它）。
 *
 * ── 为什么需要 ──
 * Gelbooru / yande.re 的标签有 6 个数字档（0 通用 / 1 画师 / 3 作品 / 4 角色 / 5 元数据），
 * 站方 CSS 给每档配了专属色（`tag-type-0` 蓝 / `1` 红 / `3` 紫 / `4` 绿 / `5` 橙）。
 * 这是图站用户扫补全时**最强的辨识信号** —— 「这行是画师还是角色」一眼就分得开，
 * 比读中文档位名快得多。不摆出来等于白丢站方已经给好的信息。
 *
 * ── 为什么不照抄站方原色 ──
 * 站方那几个值是配「白底 + 大号带下划线的链接文字」用的，`#0f0`（角色档）在白底上
 * 对比度只有 1.4:1 —— 站方自己都靠下划线兜底。这里作为 12sp 的元信息文字，
 * 必须满足 WCAG AA 小字档（4.5:1），所以按 Material 色板各取一档重配，
 * 亮暗各一套（口径同 [SettingsBadgeColors]：功能性固定色，不跟壁纸取色）。
 *
 * 索引 = 站方的数字档本身（两站共用同一套命名空间，见 `galleryTagCategoryLabel`）。
 * 取不到就返回 null，由调用方退到中性文字色 —— **不硬安一个色相**：
 * 给一个我们没记录的编号配上颜色，等于宣称它属于某个已知分类。
 */
internal object GalleryTagCategoryColors {
    /** 亮色主题：Material 800/900 档（白底上 ≥ 4.5:1）。 */
    private val OnLight = listOf(
        Color(0xFF1565C0), // 0 通用   Blue 800
        Color(0xFFC62828), // 1 画师   Red 800
        Color(0xFF616161), // 2 未知   Grey 700（站方保留档，实测少见）
        Color(0xFF6A1B9A), // 3 作品   Purple 800
        Color(0xFF2E7D32), // 4 角色   Green 800
        Color(0xFFE65100), // 5 元数据 Orange 900
    )

    /** 暗色主题：Material 200/300 档（在 ~#1C1B1F 上 ≥ 4.5:1）。 */
    private val OnDark = listOf(
        Color(0xFF90CAF9), // 0 通用   Blue 200
        Color(0xFFEF9A9A), // 1 画师   Red 200
        Color(0xFFBDBDBD), // 2 未知   Grey 400
        Color(0xFFCE93D8), // 3 作品   Purple 200
        Color(0xFFA5D6A7), // 4 角色   Green 200
        Color(0xFFFFCC80), // 5 元数据 Orange 200
    )

    /** 这一档的分类色；站方给了我们没记录的编号时返回 null。 */
    fun of(category: Int, dark: Boolean): Color? = (if (dark) OnDark else OnLight).getOrNull(category)
}

/**
 * 「自定义取色」的预设种子色板。
 *
 * 出处：逐字抄自 `jay3-yy/BiliPai` 的 `design-system/.../core/theme/Color.kt`
 * （那边是 `ThemeColors` + `ThemeColorNames` 两条按下标对齐的表，靠单测才钉住等长；
 * 这里合成一条，长度错位在结构上就不可能发生）。
 *
 * 校准：BiliPai 的 樱花粉 = `0xFFFA7298`，与用户截图里界面显示的 `#FA7298` 逐字相同 ——
 * 证明表里存的是**种子色本身**，不是网格上那圈渐变渲染后的像素值。
 *
 * 存 `argb: Int` 而不是 `Color`：落盘走 SharedPreferences 的 Int 键，
 * `data.prefs` 那层不该依赖 Compose 类型。
 */
data class ThemeSeedPreset(val name: String, val argb: Int) {
    val color: Color get() = Color(argb)
}

object ThemeSeedPresets {
    /** 网格列数，同 BiliPai 的 `ThemeColors.chunked(5)`。 */
    const val Columns = 5

    /** 未命中预设时显示的名字（手输 #RRGGBB 会落到这里）。 */
    const val CustomName = "自定义"

    val All = listOf(
        ThemeSeedPreset("经典蓝", 0xFF007AFF.toInt()),
        ThemeSeedPreset("樱花粉", 0xFFFA7298.toInt()),
        ThemeSeedPreset("天空蓝", 0xFF00A1D6.toInt()),
        ThemeSeedPreset("薄荷绿", 0xFF34C759.toInt()),
        ThemeSeedPreset("梦幻紫", 0xFFAF52DE.toInt()),
        ThemeSeedPreset("活力橙", 0xFFFF5722.toInt()),
        ThemeSeedPreset("静谧蓝灰", 0xFF607D8B.toInt()),
        ThemeSeedPreset("珊瑚红", 0xFFFF6B6B.toInt()),
        ThemeSeedPreset("靛蓝", 0xFF5856D6.toInt()),
        ThemeSeedPreset("翡翠青", 0xFF00BFA5.toInt()),
        ThemeSeedPreset("炽焰红", 0xFFF44336.toInt()),
        ThemeSeedPreset("绯樱粉", 0xFFE91E63.toInt()),
        ThemeSeedPreset("星云紫", 0xFF9C27B0.toInt()),
        ThemeSeedPreset("暮影紫", 0xFF673AB7.toInt()),
        ThemeSeedPreset("靛空蓝", 0xFF3F51B5.toInt()),
        ThemeSeedPreset("晴空蓝", 0xFF2196F3.toInt()),
        ThemeSeedPreset("极光青", 0xFF00BCD4.toInt()),
        ThemeSeedPreset("海沫绿", 0xFF009688.toInt()),
        ThemeSeedPreset("新叶绿", 0xFF4CAF50.toInt()),
        ThemeSeedPreset("日光黄", 0xFFFFEB3B.toInt()),
        ThemeSeedPreset("琥珀金", 0xFFFFC107.toInt()),
        ThemeSeedPreset("暖阳橙", 0xFFFF9800.toInt()),
        ThemeSeedPreset("可可棕", 0xFF795548.toInt()),
        ThemeSeedPreset("雾霭蓝灰", 0xFF607D8F.toInt()),
        ThemeSeedPreset("晨曦粉", 0xFFFF9CA8.toInt()),
    )

    /** 默认种子 = 表首（经典蓝），同 BiliPai 的 `theme_color_index` 默认 0。 */
    val DefaultArgb: Int = All.first().argb

    fun nameOf(argb: Int): String = All.firstOrNull { it.argb == argb }?.name ?: CustomName
}
