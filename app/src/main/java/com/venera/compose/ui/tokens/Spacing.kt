package com.venera.compose.ui.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 间距 / 尺寸 Token。
 *
 * 规则（A3.1，本轮按「严格」档确认）：除本文件外，任何 UI 代码不得出现 `xx.dp`。
 *
 * 命名遵循 4dp 基准栅格（[space1] = 4dp），刻意与密度无关地固定，
 * 不随 MD3/MIUIX 切换 —— MIUIX 与 MD3 的**间距**规范差异远小于颜色/形状，
 * 强行分叉只会让页面代码出现无意义的 if。
 */
@Immutable
data class VeneraSpacingTokens(
    val none: Dp = 0.dp,
    val space1: Dp = 2.dp,
    val space2: Dp = 4.dp,
    val space3: Dp = 6.dp,
    val space4: Dp = 8.dp,
    val space5: Dp = 10.dp,
    val space6: Dp = 12.dp,
    val space7: Dp = 14.dp,
    val space8: Dp = 16.dp,
    val space9: Dp = 20.dp,
    val space10: Dp = 24.dp,
    val space11: Dp = 32.dp,

    /** 页面左右安全边距。 */
    val screenHorizontal: Dp = 12.dp,
    /** 列表项内部左右边距。 */
    val rowHorizontal: Dp = 14.dp,
    /** 列表项内部纵向边距。 */
    val rowVertical: Dp = 12.dp,
    /** 图标徽章尺寸。 */
    val badgeSize: Dp = 28.dp,
    /** 徽章内图标尺寸。 */
    val badgeIconSize: Dp = 18.dp,
    /** 列表行尾「›」指示符的点击/占位宽度。 */
    val chevronWidth: Dp = 12.dp,
    // ────────────────────────────────────────────────────────────────────
    // Bottom Navigation Insets 契约（全局唯一真源）
    //
    // 责任划分：
    //   1. 系统     -> navigationBars inset（由 WindowInsets 提供，谁都不许硬编码）
    //   2. BottomBar -> 自身高度 + 自身与系统 inset 的间距（barBottomGap）
    //   3. Screen   -> 只消费 [bottomBarClearance]，不得再自行叠加 Bar 高度
    //
    // 页面内容底部留白 = bottomBarClearance + navigationBars inset（由 Scaffold/NavHost 提供）。
    // 任何页面都**禁止**再额外加 Spacer(bottomBarClearance) 或写 88dp/96dp 这类魔法数。
    // ────────────────────────────────────────────────────────────────────

    /** 悬浮底栏外壳高度（与 VeneraFloatingNavBar / LiquidGlass 的 64dp 保持一致）。 */
    val bottomBarHeight: Dp = 64.dp,
    /** 底栏与系统导航栏之间的视觉间距。 */
    val bottomBarBottomGap: Dp = 12.dp,
    /**
     * 页面内容需要为底部导航预留的净留白（= 底栏高度 + 底栏底部间距）。
     *
     * 注意：**不含**系统 navigationBars inset —— 那部分由 Scaffold / NavHost 统一提供，
     * 页面若再加一次就会重复（这正是本轮修复的双重留白问题）。
     */
    val bottomBarClearance: Dp = bottomBarHeight + bottomBarBottomGap,

    // ── 首页专用语义尺寸（跨组件保持一致的设计决策，非一次性约束）──
    /** 今日推荐卡片宽度。 */
    val heroCardWidth: Dp = 264.dp,
    /** 今日推荐卡片高度。 */
    val heroCardHeight: Dp = 152.dp,
    /** 今日推荐封面宽度。 */
    val heroCoverWidth: Dp = 96.dp,
    /** 今日推荐封面高度。 */
    val heroCoverHeight: Dp = 136.dp,
    /** 历史卡片宽度。 */
    val historyCardWidth: Dp = 124.dp,
    /** 历史卡片封面高度。 */
    val historyCoverHeight: Dp = 170.dp,
    /**
     * 推荐轮播（中央 Hero）整块高度。
     *
     * 手机宽下 Hero 实宽约 143dp，配 170dp 高就是 0.84 的「矮胖」比例；
     * 封面本身是 0.72 竖幅，所以这块要明显高于历史卡才不显肥。
     */
    val recommendHeroHeight: Dp = 220.dp,
    /** 推荐轮播侧卡宽度下限（MD3 目标：侧卡 = 主卡 1/3，这里只做夹取）。 */
    val recommendSideMinWidth: Dp = 64.dp,
    /** 推荐轮播侧卡宽度上限。压到 88dp 是为了让主卡吃到 ~143dp，别退化成两张大卡。 */
    val recommendSideMaxWidth: Dp = 88.dp,
    /** 源状态行首字母徽章尺寸。 */
    val sourceAvatarSize: Dp = 34.dp,
    /** 状态圆点尺寸。 */
    val statusDotSize: Dp = 8.dp,
    /** 统计条高度（图片收藏 Top 条形图）。 */
    val barHeight: Dp = 8.dp,
    /** 统计条标签列宽。 */
    val barLabelWidth: Dp = 96.dp,
    /** 统计条数值列宽。 */
    val barValueWidth: Dp = 30.dp,
    /** 统计数字之间的分隔线高度。 */
    val statDividerHeight: Dp = 26.dp,

    // ── 组件层尺寸（跨页面复用，非页面级）──
    /** 漫画封面宽高比（3:4 竖版）。 */
    val coverAspectRatio: Float = 0.72f,
    /**
     * 平板档插图卡的**宽高比下限** = 1 / 1.1，等价于"卡高不超过列宽的 1.1 倍"。
     *
     * 瀑布流的卡高由图片真实比例决定，平板三列时一张竖页能撑到列宽的 1.4 倍，
     * 一屏只放得下一行半（用户真机口径「还要更小、两行能看全」）。比例下限是唯一不需要
     * 实测列宽的封顶写法：高 = 宽 / 比例，把比例抬到 1/1.1 就把高压在 1.1 倍列宽内，
     * 超出部分由 ContentScale.Crop 裁掉 —— 点开灯箱看的仍是全图。手机档不封顶。
     */
    val favoriteImageMinRatio: Float = 0.91f,
    /**
     * 卡片预览/骨架的参考宽度（不是列数依据）。
     *
     * 列数不在这里定：由 `components.comicListColumnCount` 按网格实测可用宽推导，
     * 口径照抄 master（brief 每 220dp 一列、detailed 每 360dp 一列）。
     */
    val comicCardMinWidth: Dp = 130.dp,
    /** Chip 内文字左右内边距。 */
    val chipHorizontalPadding: Dp = 12.dp,
    /** Chip 内文字纵向内边距。 */
    val chipVerticalPadding: Dp = 6.dp,
    /** Chip 在行内的间距。 */
    val chipSpacing: Dp = 8.dp,
    /** Chip 前置/后置图标尺寸。 */
    val chipIconSize: Dp = 16.dp,
    /** 1dp 发丝线：MD3 组件描边/分隔线口径（分段控制器容器描边、单元分隔线）。 */
    val hairline: Dp = 1.dp,
    /** 0.5dp 极细线：分段控制器容器描边（用户拍板「0.5dp 实线」）。 */
    val hairlineThin: Dp = 0.5.dp,
    /** 图源/文件夹筛选胶囊的统一高度（用户拍板 32dp，比 Chip 更矮更紧凑）。 */
    val filterChipHeight: Dp = 32.dp,
    /**
     * MD3 Segmented Button 规范高度（48dp，组件默认档）。
     * 用户手机真机反馈原拍板的 40dp「紧凑」档同样太矮细弱，升回 MD3 默认。
     */
    val segmentedHeight: Dp = 48.dp,
    /**
     * 宽屏档（isWideScreen，>600dp）的分段控制器高度：用户拍板「平板上 40dp 太矮太细弱」。
     * 手机档不读这个值，compact 零回归线不破。
     */
    val segmentedHeightWide: Dp = 56.dp,
    /**
     * MD3 Segmented Button 单元间隙，同时是分隔线在两格之间的居中量。
     *
     * 取现成的 space5 而不是 MD3 规范里的 4dp：这个值原本是 4dp，但组件把它当像素用了
     * （见 VeneraSegmentedButton 的 px/dp 对账），真机上实际渲染出的"药丸中的药丸"环宽
     * 是 4×密度 ≈ 10.5dp。单位修正后要让观感停在用户已拍板的那一档，就写 10dp。
     */
    val segmentedGap: Dp = space5,
    /**
     * 折叠态顶栏（小标题行）高度：与 Miuix `TopAppBarDefaults.CollapsedHeight`
     * 同值的本地镜像（该常量在库内是 internal，读不到只能照抄）。
     */
    val topBarCollapsedHeight: Dp = 52.dp,
    /** SourceBadge 内文字左右内边距。 */
    val badgeHorizontalPadding: Dp = 6.dp,
    /** SourceBadge 内文字纵向内边距。 */
    val badgeVerticalPadding: Dp = 2.dp,
    /** SourceBadge 在封面上的内缩距离。 */
    val badgeInset: Dp = 6.dp,
    /** 卡片内容内部间距。 */
    val cardContentPadding: Dp = 8.dp,
    /** 卡片封面与文字之间的间距。 */
    val cardCoverGap: Dp = 6.dp,
    /**
     * 骨架屏流光一个周期的时长（毫秒）。
     * 3000 = master 用的 `shimmer_animation` 包的 duration 默认值
     * （包内另有 Interval(0, 0.6) 让行程只占前 60%，后 40% 停一拍）。
     */
    val shimmerPeriodMillis: Int = 3000,

    // ── 通用控件尺寸（跨页面复用，非 Search 专属）──
    /** 单行输入框 / 搜索框高度。 */
    val searchFieldHeight: Dp = 48.dp,
    /** 单行输入框内文字左右内边距。 */
    val fieldHorizontalPadding: Dp = 14.dp,
    /** 图标按钮的触达尺寸。 */
    val iconButtonSize: Dp = 40.dp,
    /** 结果区顶部/区块之间的标准间距。 */
    val sectionGap: Dp = 14.dp,
    /** 网格中卡片之间的间距。 */
    val gridGap: Dp = 12.dp,
    /** 列表模式左侧封面宽度。 */
    val listCoverWidth: Dp = 92.dp,

    // ── 详情页头部与操作区尺寸（对齐 hero/history 既有页面级尺寸先例）──
    /** 详情页封面宽度。 */
    val detailCoverWidth: Dp = 110.dp,
    /** 详情页右侧信息列高度（与封面等高对齐）。 */
    val detailCoverHeight: Dp = 152.dp,
    /** 详情页主操作胶囊按钮高度。 */
    val detailPrimaryButtonHeight: Dp = 56.dp,
    /** 详情页悬浮顶栏（返回 / 标题 / 分享所在行）高度。 */
    val detailTopBarHeight: Dp = 48.dp,
    /**
     * 详情页首个内容项需要为悬浮顶栏让出的净高度（= [detailTopBarHeight] + 呼吸间距 24dp）。
     *
     * 独立成 Token 的原因：真机反馈封面行紧贴返回/分享钮（原值 48+8 太挤），
     * 而这个数只能表达成「顶栏高 + 间距」，写死在页面里就成了没人看得懂的 56dp。
     * 不含系统 statusBars inset —— 那部分由 WindowInsets 提供。
     */
    val detailTopBarClearance: Dp = detailTopBarHeight + 24.dp,
    /**
     * 详情页「标签与分类」分组名列宽。
     *
     * 是**最小**列宽而非定宽：定宽会把 `Chinese Team:` 这类长键从单词中间截断换行，
     * 列宽随内容增长才能保住「组名成词」。译成中文后（作者/汉化组/分类/标签）
     * 多数落在 56dp 内，各行仍对齐。
     */
    val detailCategoryLabelWidth: Dp = 56.dp,
    /** 详情页章节胶囊最小宽度。 */
    val detailChapterChipMinWidth: Dp = 96.dp,
    /** 详情页相关推荐封面宽度。 */
    val detailRecommendWidth: Dp = 90.dp,
    /** 详情页相关推荐封面高度。 */
    val detailRecommendCoverHeight: Dp = 120.dp,

    // ── 下拉刷新（网络收藏页落地，语义通用）──
    /**
     * 下拉刷新指示行的**满展开行高**。
     *
     * 行高由下拉进度 1:1 驱动，所以这个数就是「跟手下移的最大距离」；
     * 放手后刷新中保持满高，刷新完成收回 → 卡片自然上移露出新内容。
     */
    val pullRefreshRowHeight: Dp = 48.dp,
    // ── 加载指示器尺寸（波浪环；M3 Expressive 的振幅/波长按大尺寸调，小于此会糊）──
    /** 整页 / 区块居中的加载环。 */
    val loaderPage: Dp = 48.dp,
    /** 行内加载环：下拉刷新那一行、分区头、触底 footer 都用它。低于 24dp 波浪就看不出来了。 */
    val loaderInline: Dp = 28.dp,
)

/**
 * 形状 Token。
 *
 * MD3 与 MIUIX 的圆角哲学不同：MD3 偏「中等圆角 + 明确分层」，
 * MIUIX 偏「大圆角 + 连续性」。因此本 Token 随主题切换。
 */
@Immutable
data class VeneraShapeTokens(
    val extraSmall: Dp,
    val small: Dp,
    val medium: Dp,
    val large: Dp,
    val extraLarge: Dp,

    /** 设置分组卡片圆角。 */
    val card: Dp,
    /** 图标徽章圆角（徽章是圆角方形，不是圆形）。 */
    val badge: Dp,
)

/** 动效时长 Token（A3.1 要求动画时长也走 Token）。 */
@Immutable
data class VeneraMotionTokens(
    val short: Int = 120,
    val medium: Int = 220,
    val long: Int = 320,
    /**
     * 网络图片「占位层 → 真图」的淡入时长。
     *
     * 独立于 [short]/[medium]：那两个服务界面转场，这个服务**内容替换**。
     * 内容替换要慢到足以让人意识到「图换掉了」，又不能慢到让滚动显得黏；
     * 200ms 是真机观感定下来的值，和转场阶梯分开调才不会互相牵制。
     */
    val imageFadeInMillis: Int = 200,
)

/** 静态间距实例：两套主题共用。 */
val VeneraSpacing = VeneraSpacingTokens()

/** MD3 形状：跟随 Material3 默认圆角阶梯。 */
val Md3Shapes = VeneraShapeTokens(
    extraSmall = 4.dp,
    small = 8.dp,
    medium = 12.dp,
    large = 16.dp,
    extraLarge = 28.dp,
    card = 16.dp,
    badge = 8.dp,
)

/** MIUIX 形状：更大的圆角、更强的连续性，与 miuix-compose 观感一致。 */
val MiuixShapes = VeneraShapeTokens(
    extraSmall = 6.dp,
    small = 10.dp,
    medium = 14.dp,
    large = 18.dp,
    extraLarge = 30.dp,
    card = 18.dp,
    badge = 10.dp,
)
