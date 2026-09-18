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
     * 网格中漫画卡片的**最小宽度**（dp）。
     *
     * 供 GridCells.Adaptive 使用：列数由实际可用宽度推导，不写死"2 列"。
     * 360dp 屏 → 2 列；412dp → 2 列；600dp+ 平板 → 3 列；折叠屏 → 更多。
     * 130dp 的取值保证 360dp 屏两列 + 间距后仍有余量。
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
    /** Shimmer 动画一个周期的周期时长（毫秒）。 */
    val shimmerPeriodMillis: Int = 1200,

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
    /** 详情页「标签与分类」分组名列宽。 */
    val detailCategoryLabelWidth: Dp = 56.dp,
    /** 详情页章节胶囊最小宽度。 */
    val detailChapterChipMinWidth: Dp = 96.dp,
    /** 详情页相关推荐封面宽度。 */
    val detailRecommendWidth: Dp = 90.dp,
    /** 详情页相关推荐封面高度。 */
    val detailRecommendCoverHeight: Dp = 120.dp,
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
