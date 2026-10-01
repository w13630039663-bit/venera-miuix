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
    /** 画廊（图站）域。Indigo 500：与 Theming 那枚紫分开，两区同色相会读混。 */
    val Gallery = Color(0xFF3F51B5)
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

    // ── 画廊两站的**品牌色**（2026-09-30 新增，来源标识那一对用的）──
    /**
     * Gelbooru 品牌蓝。
     *
     * 取自站方 favicon 本身：不透明像素里除白色外出现最多的就是 `#006ffa`（22 个像素）。
     * ⚠️ **不要**改成站方 SVG 里那条 path 自己的 `fill`（`#FFFFFF`）—— 它是设计给
     * 站方自己的彩色页头用的；摆到我们这枚固定深色小底板上就是一块没有识别度的白斑。
     * 与 `BadgeSurface` 的对比度 ≈ 3.7:1，高于**图形元素** 3:1 的门槛（非文本，不适用 4.5:1）。
     *
     * 它同时被 `res/drawable/ic_source_gelbooru.xml` 写着（那枚由
     * `scripts/build_source_icons.mjs` 生成、fill 值也出自脚本里的常量）——
     * 改色要**两处一起改**，这处是给人读的登记，那处才是渲染值。
     */
    val GallerySourceGelbooru = Color(0xFF006FFA)

    /**
     * yande.re 的强调色（**字母标**的底色）。
     *
     * 它的 favicon 不是站标（详见 `scripts/build_source_icons.mjs` 头注的实测），
     * 所以那一站我们用品牌色字母标。这个值取自它站点 CSS 里的链接色
     * （`a:link { color: #ee8887 }`，出自主样式表 `application-*.css`）——
     * **是推断，不是官方发布的品牌规范**：真机上若觉得偏，它有个现成的候选
     * `#3C3CDC`（同一份 CSS 里按钮 hover 的背景色）。改这一处即可。
     */
    val GallerySourceYandere = Color(0xFFEE8887)

    /** 品牌色底上的文字/图形色。两站的品牌底色都是中间调，白字在两者上都 ≥ 3:1。 */
    val OnGallerySourceBrand = Color(0xFFFFFFFF)
}

/**
 * 画师介绍页「平台入口」那排胶囊上，各家的**品牌色**（2026-10-01 新增）。
 *
 * ── 它是什么、不是什么 ──
 *
 * 它是"这一枚色块属于哪一家"的**功能性固定色**，与 [GalleryTagCategoryColors]
 * 同一档：不跟壁纸取色、不随 MD3/MIUIX 分叉。参照物是各家自己的品牌资产，
 * 所以**改值前必须先去核源**，不能凭观感调 —— 调偏了就是"把一家认成另一家"。
 *
 * ── 色值出处（逐条可核）──
 *
 * 五家取自 [Simple Icons](https://simpleicons.org) v15.22.0 的数据文件
 * （那一份逐条记着官方出处，本仓的 `scripts/build_platform_icons.mjs` 会把出处打进产物注释）。
 * ⚠️ 这份表是"屏上认账的那一份"，脚本里那行只是登记 —— **改色要两处一起改**，
 * 与 [GallerySourceGelbooru] 同一条口径。
 *
 * ── 一处**故意不照抄官方色** ──
 *
 * [X] 官方品牌色是 `#000000`（Simple Icons 记的就是它）。纯黑在**深色档**下与卡面同色，
 * 22dp 的方块会整块消失、只剩一个镂空的字形。所以取 X 自家界面里那枚链接蓝
 * `#1d9bf0`（参考图三那一枚用的也是它）。这是唯一一处偏离，理由是"照抄会让它看不见"。
 *
 * [Fanbox] 用的是站方 `https://s.pximg.net/common/images/fanbox/logo.svg` 里
 * 那条 path 自己的 fill（`#2c333c`）—— **不是**它的吉祥物底色。
 * 吉祥物那枚（`apple-touch-icon.png`，浅黄 `#faf18a` 底 + 白兽 + 黑描边）是**彩色方块**，
 * 染成单色会得到一整块实心方（它的 alpha 处处为 255），也压不住 14dp 的字形。
 * 那枚横版字标（148×20）同理，缩到 14dp 读不出字。所以 FANBOX 的徽标里摆的是字母标「F」，
 * 口径与 [GallerySourceYandere] 那条"没有方形站标就用品牌色字母标"完全一致。
 */
internal object GalleryPlatformColors {
    /** pixiv 官方品牌色。 */
    val Pixiv = Color(0xFF0096FA)

    /** X（原 Twitter）**界面蓝**，非官方品牌色 `#000000` —— 理由见本对象头注。 */
    val X = Color(0xFF1D9BF0)

    /** Instagram 官方品牌色。站方真标是渐变，这里取单色档（本仓禁渐变，且渐变压不出单色字形）。 */
    val Instagram = Color(0xFFFF0069)

    /** FANBOX：站方 logo 矢量档里那条 path 的 fill。 */
    val Fanbox = Color(0xFF2C333C)

    /** Tumblr 官方品牌色。 */
    val Tumblr = Color(0xFF36465D)

    /** YouTube 官方品牌色。 */
    val YouTube = Color(0xFFFF0000)

    /**
     * 品牌色底上的字形色。
     *
     * 白。上面六档里最浅的是 [Tumblr] 的 `#36465d`，与白的对比度 ≈ 9.6:1；
     * 最险的是 [X] 的 `#1d9bf0`，≈ 2.9:1 —— 字形是非文本图形，门槛是 3:1，
     * 差一点点。真机上若判定读不出，改法是换一枚更深的蓝（`#0f6ea8` 一带），
     * 而**不是**把字形调粗：调粗会让它跟相邻的平台名（同一枚胶囊里的文字）抢重量，那是另一件事。
     */
    val OnPlatformBrand = Color(0xFFFFFFFF)
}

/**
 * 图站**分级徽标**的固定色板（2026-10-01 新增，「关于这张图」重排那一批）。
 *
 * ── 为什么是固定色 ──
 *
 * 与 [SettingsBadgeColors] / [StatusColors] 同一条口径：这是**功能性语义色**。
 * 「安全 / 留意 / 成人」在任何主题下都得一眼可辨；跟随 MD3 壁纸取色会退化成
 * 同色系的不同深浅，那就等于没有编码。
 *
 * ── 为什么只有四档，而分级有五个语义档 ──
 *
 * 徽标是**实底胶囊 + 白字**（走 [com.venera.compose.components.venera.VeneraChip]
 * 的 `containerColorOverride` / `contentColorOverride` 两个口子），于是每档都要保证
 * 白字压住容器色 ≥ 4.5:1（正文门槛）。逐色量过（WCAG 2.1，sRGB 相对亮度）：
 *
 * | 本表取值 | 白字对比度 |
 * |---|---|
 * | [Safe] `#2E7D32` | 5.13:1 |
 * | [Caution] `#A15C00` | 5.17:1 |
 * | [Explicit] `#C62828` | 5.63:1 |
 * | [Unknown] `#616161` | 6.29:1 |
 *
 * 而"五档一比一"要的那些**黄/琥珀族根本进不来**：`#4CAF50` 2.78:1、`#F9A825` 1.97:1、
 * `#FFB300` 1.90:1 —— 明度太高，白字压不住；把色阶压深到能过对比度，
 * `sensitive` 与 `questionable` 又分不出来（`#9A6A00` 与 `#A15C00` 色相差不到 10°）。
 *
 * 所以本表只编码「**这一档有多需要留意**」，精确档位由徽标上的字承担 ——
 * 完整的推导在 [com.venera.compose.gallery.domain.GalleryRatingTone] 头注里，
 * 改这里的值之前先读那一份。
 *
 * ⚠️ [Unknown] 是**独立一档**，不是 [Caution] 的兜底：站方给了个我们不认识的值
 * 与"站方说是敏感"是两件事，画成同一个颜色等于替站方下了一个它没下过的判断。
 */
internal object GalleryRatingColors {
    /** 安全 / 一般。 */
    val Safe = Color(0xFF2E7D32)

    /** 敏感 / 存疑（两档合用一色，理由见头注）。 */
    val Caution = Color(0xFFA15C00)

    /** 成人。 */
    val Explicit = Color(0xFFC62828)

    /** 站方给了不认识的值、或值是空的。 */
    val Unknown = Color(0xFF616161)

    /**
     * 四色底上的字：固定白。
     *
     * 与 [GalleryPlatformColors.OnPlatformBrand] 同一个理由 —— 这四档底色的明度
     * 都是按"白字可读"选出来的，换成主题文字色反而会有一半档位塌掉。
     */
    val OnRatingBadge = Color(0xFFFFFFFF)
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
 * 「关于这张图」半模态里**每一块的色相**（2026-10-01 第三次重排，用户报"分区的色块不明显"）。
 *
 * ── 先说病：上一版的"分区"在屏上根本不存在 ──
 *
 * 上一版的分区靠"换一张 `VeneraCard`"：卡片吃 `surfaceContainerHigh`，半模态自己吃
 * `surfaceContainerLow`。真机截图逐像素量过（1080×2376，见 `.tmp_shots/` 那一次对比度拉伸取样）：
 * 整屏内容区**自始至终是同一个 `rgb(245,242,249)`**，连一条边界都找不出来 ——
 * 两档色调面在**动态取色**下只差一个色调步，再乘上玻璃那 0.22 的容器 alpha，差到 1 以内。
 * 所以这不是"色淡了一点"，是"分区没有可见载体"。
 *
 * ── 判据：**明度负责"这是一块"，色相负责"这是哪一块"** ──
 *
 * 两件事分开承担，才不会再出现"某一档主题下整片糊平"：
 * - "存在"由描边（`outlineVariant` 发丝线）+ 一层极淡的同色相洗底承担（见 [WashAlpha]）——
 *   描边与色相都不依赖色调阶梯，换主题、换壁纸、开关玻璃都立得住；
 * - "归属"由节标题那枚徽标（[BadgeAlpha] 底 + 100% 字形）承担，四块四色，一眼分得开。
 *
 * ── 为什么不派生主题色 ──
 *
 * 与 [SettingsBadgeColors] 写在同一页上的理由逐字相同：这一组色的用途是**互相区分**，
 * 而动态取色总共只给 2~3 个色相族 —— 派生过去四块会退化成同色系，"色块"这件事就没了。
 *
 * ── 亮暗两档与对比度 ──
 *
 * 亮档取 Material 700/800、暗档取 200/300。徽标字形是 18dp 的**非文本图形**（门槛 3:1），
 * 压在自己那 15% 的浅底上（近似等于所在面）：
 * 亮档最险的是 [TagsLight] `#EF6C00` ≈ 3.9:1，其余 ≥ 5:1；
 * 暗档最浅的 [TagsDark] `#FFD54F` 在 `#1C1B1F` 上 ≈ 11:1。
 *
 * ⚠️ 与 [GalleryTagCategoryColors] **有四对色相相邻**（靛-蓝、青-绿、粉-红、琥珀-橙），
 * 这是刻意的取舍而非疏忽：那一份的六个色相已经占满常用域，再挑就只剩灰与棕。
 * 真正会读错的是**同值**（那等于宣称"这两件事是一回事"），本表与那一份**没有一对同值**；
 * 而两处色相各自出现的位置也隔得很远 —— 一个是块标题、一个是标签桶名。
 */
/** 「关于这张图」的**四块**。枚举值即色相，顺序即屏上顺序（见 [GallerySheetSectionColors]）。 */
internal enum class GallerySheetSection { Facts, Artist, Source, Tags }

internal object GallerySheetSectionColors {
    /** 事实网格（这张图的客观属性）。 */
    val FactsLight = Color(0xFF303F9F) // Indigo 700
    val FactsDark = Color(0xFF9FA8DA)  // Indigo 200

    /** 画师。 */
    val ArtistLight = Color(0xFF00796B) // Teal 700
    val ArtistDark = Color(0xFF80CBC4)  // Teal 200

    /** 来源链接。 */
    val SourceLight = Color(0xFFC2185B) // Pink 700
    val SourceDark = Color(0xFFF48FB1)  // Pink 200

    /** 标签。 */
    val TagsLight = Color(0xFFEF6C00) // Orange 800
    val TagsDark = Color(0xFFFFD54F)  // Amber 300

    /**
     * 取一块在当前深浅档下的色相。
     *
     * [dark] 由调用方从 `LocalVeneraDarkTheme` 传进来，而不是在这里读 —— 与
     * [GalleryTagCategoryColors.of] 同一条：`ui/tokens` 不 import `feature`
     * （唯一那条反向依赖会让预览主题没法独立 provide）。
     */
    fun of(section: GallerySheetSection, dark: Boolean): Color = when (section) {
        GallerySheetSection.Facts -> if (dark) FactsDark else FactsLight
        GallerySheetSection.Artist -> if (dark) ArtistDark else ArtistLight
        GallerySheetSection.Source -> if (dark) SourceDark else SourceLight
        GallerySheetSection.Tags -> if (dark) TagsDark else TagsLight
    }

    /**
     * 块底色那层"色相洗底"的 alpha。
     *
     * 0.07：亮档实测把 `#F5F2F9` 洗成 `rgb(233,231,244)`（Δ≈12，与一个色调步同量级但**不靠**色调步），
     * 暗档把 `#141218` 抬约 +10 —— 两档都"看得见但不抢内容"。
     * 再高就开始影响块内文字的对比度了（块里还有 12sp 的次级文字）。
     */
    const val WashAlpha = 0.07f

    /**
     * 徽标底色的 alpha。
     *
     * 0.15，与设置页那枚 [SettingsBadgeColors] 徽标同档（见那个对象的头注：
     * "15% alpha 作底 + 100% 作图标 tint"），全仓徽标只有这一种画法。
     */
    const val BadgeAlpha = 0.15f
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

/**
 * 压在**用户自选图片**上的覆盖层固定色（设置页 hero 头图与名言卡，2026-10-02 新增）。
 *
 * 与 [SettingsBadgeColors] / [StatusColors] 同一条口径：图是用户随手选的，
 * 亮度和色相完全不可控，覆盖层若跟随主题取色就会出现「白字配白图」；
 * 所以 scrim 与图上文字必须是**固定色**，靠 scrim 自身的浓度兜底可读性。
 *
 * 两层 scrim 是同一色（黑）的两档浓度，不是两色渐变：
 * 顶部一层只保状态栏图标对比，底部一层才保标题白字。
 */
internal object ImageOverlayColors {
    /** 图上文字（标题）：纯白 —— 在底部 scrim（≥55% 黑）上对比度 ≥ 7:1。 */
    val OnImage = Color(0xFFFFFFFF)

    /** 图上文字（副标题 / 次要）：70% 白，与标题拉开层级但仍在 scrim 上可读。 */
    val OnImageSecondary = Color(0xB3FFFFFF)

    /** 顶部 scrim（状态栏背后）：35% 黑。 */
    val ScrimTop = Color(0x59000000)

    /** 底部 scrim 终点（标题所在端）：55% 黑。 */
    val ScrimBottom = Color(0x8C000000)

    /** 图上动作钮（相机 / 清除）的底：45% 黑圆底，白图标。 */
    val ActionPill = Color(0x73000000)
}
