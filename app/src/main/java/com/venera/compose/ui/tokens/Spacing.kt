package com.venera.compose.ui.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
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
    /**
     * 设置首页 hero 头图高度（**状态栏以下的内容高**；实际 Box 高度 = 状态栏 + 本值）。
     *
     * 208dp：比两张分组卡高一点，够摆「标题 + 副标题 + 右下动作钮」三层不挤；
     * 再高就开始跟内容抢屏（参考图那种半屏 hero 放在设置页会让首组设置项不可见）。
     */
    val settingsHeroHeight: Dp = 208.dp,
    /**
     * 设置子页 hero 头图高度（同上口径）。比首页矮一档（148dp）：
     * 子页的标题只有一行、没有应用卡叠底，再高就纯浪费纵向空间。
     */
    val settingsHeroHeightSubpage: Dp = 148.dp,
    /**
     * hero 与其后第一块内容（首页=应用身份卡 / 子页=第一组卡）的**重叠量**。
     *
     * 这是「图与卡片之间没有过渡」的修复主体（2026-10-02 实测反馈）：参考图的观感
     * 不是靠渐变糊出来的，而是**卡片直接叠在图上** —— 图延伸到卡片背后，内容与头图
     * 在视觉上连续。hero 的布局占位高度会比视觉高度矮这一段（见 SettingsHeroImage
     * 里的 layout 实现），后续内容自然上移压图。
     */
    val settingsHeroOverlap: Dp = 48.dp,
    /** 名言卡左侧小图边长（与画廊画师头像同一档：够看清一张脸，又不压过句子）。 */
    val quoteAvatarSize: Dp = 56.dp,
    /** 列表项内部左右边距。 */
    val rowHorizontal: Dp = 14.dp,
    /** 列表项内部纵向边距。 */
    val rowVertical: Dp = 12.dp,
    /** 图标徽章尺寸。 */
    val badgeSize: Dp = 28.dp,
    /** 徽章内图标尺寸。 */
    val badgeIconSize: Dp = 18.dp,
    /**
     * 画廊卡片那一行上的**站方来源标识**边长（`VeneraGallerySourceMark`）。
     *
     * 18dp（批次 M · M7，用户 2026-09-30 拍板"图标太大了"）：与 [badgeIconSize] 同一档。
     * 原先的 24dp 是按"要比标题行醒目一点"定的，真机对照参考图之后发现反了 ——
     * 那一行的主体是标题，站标只是署名，24dp 会让每枚卡都先看见两个色块。
     * 两站（真图标 / 品牌色字母标）共用这一档，别给单站调大小：并排时两枚必须一样大。
     */
    val sourceMarkSize: Dp = 18.dp,
    /**
     * 「正在关注的画师」那一行**圆形头像**的直径。
     *
     * 56dp（2026-10-01 用户改判，从 44dp 回摆）。用户原话：「画师头像太小了」。
     *
     * 44dp 那一档是批次 M · M7 降下来的，当时的理由是"56dp 读起来像三个大圆饼、
     * 把下面每日热门那一行压成了配角"。**那条理由今天不成立了**：批次 N · N2 给每位画师
     * 发了**自己的一张 [com.venera.compose.components.venera.VeneraCard]**，
     * 圆头像从"浮在节背板上的一颗圆饼"变成"卡片里的一张脸" —— 卡面给了它边界，
     * 同样的直径不再往外散。所以本轮回摆到参考图那一档。
     *
     * 几何上限是 72dp（[artistAvatarSlotWidth] 88dp − 卡内衬 [cardContentPadding] 两边各 8dp）。
     * 但贴到 72dp 时圆边与卡内衬齐平，卡会读成"一张头像配一行字"而不是"一个人的卡"。
     * 56dp 两头各留 8dp，是这个尺寸下"脸最大、卡还在"的那一档。
     *
     * ⚠️ 这个值**只影响脸的直径**，不影响一格多宽 —— 一屏摆四位由 [artistAvatarSlotWidth]
     * 独扛，改这里不会把第四位挤出屏外。
     */
    val artistAvatarSize: Dp = 56.dp,
    /**
     * 画师那一格（= 每位一张卡）的**外宽**。
     *
     * 88dp（批次 N · N2，从 72dp 回摆）。这一格的宽度不是"头像 + 左右呼吸"，
     * 而是**一张 [com.venera.compose.components.venera.VeneraCard] 的宽度** —— 画师行从
     * "节背板里裸摆图标"改成"每位一张卡"之后，卡自己两头各吃掉
     * [cardContentPadding]（8dp），所以内容宽 = 本值 − 16dp。
     *
     * 72dp 那个数是批次 M 量的，当时这一格里没有卡、内容宽就是 72dp 整；
     * 套上卡之后只剩 56dp，而下面那一行「[站标] N 张收藏」按 11sp 实算要 ≈60dp
     * （一枚 18dp 站标 + 4dp 间隙 + "1 张收藏" ≈38dp）—— **连一枚站标都装不下**，
     * 两枚站标（跨站同名合并那位，批次 M · M5）更会省略号到读不出张数。
     * 88dp 是这一格降下来之前的原值，本轮回摆到它：内容 72dp，一枚站标 + 张数读得完整，
     * 两枚站标那位仍会挤掉张数的一两个字（真机若判定不可接受，改法是两枚站标时不摆张数，
     * 而不是再放大这一格 —— 88dp 已经是一屏四位的上限）。
     */
    val artistAvatarSlotWidth: Dp = 88.dp,
    /**
     * 画师卡里那一行「[站标] N 张收藏」上**站标**的边长。
     *
     * 14dp（2026-10-01 用户改判，比 [sourceMarkSize] 低一档）。用户原话：「站点图片太大了」。
     *
     * 降它的盘算是这一行的实际宽度对不上主次：18dp 站标 + 2dp 间隙 + "N 张收藏"（11sp 实算 ≈42dp）
     * ≈ 62dp，而卡的内容宽只有 72dp —— 也就是说**卡里最宽的东西是这一行，不是上面那颗头像**，
     * 再加站标是一枚**饱和色块**（白底蓝 G / 粉脸位图），视觉重量直接压过头顶。
     * 于是整张卡的读序变成"先看见站标、再看见人"，与"这是你关注的**人**"正好相反。
     *
     * ⚠️ **只有画师卡读这一档**。卡片署名行（[GalleryCardCaption]）上那枚仍是 [sourceMarkSize] 18dp：
     * 那里站标是**署名**，和标题同级、本来就该显眼，降下来会让两站的图挤在一排卡里认不出处。
     * 两处尺寸不同不是漏统一，是两个角色。而**同一处里两站之间仍必须一样大**（硬口径，不许给单站调）。
     *
     * 顺带收益：yande.re 那枚是从站方 16px favicon 放大的位图，14dp（本机 ≈42 物理像素）
     * 比 18dp（≈54）少放大约 1/4，糊的程度反而轻。
     */
    val artistSourceMarkSize: Dp = 14.dp,
    /**
     * 画师介绍页头部那块 **hero** 的高度（2026-10-01 用户拍板「对标图三」）。
     *
     * 196dp 是照着参考图量的：那一块的实宽 = 屏宽 − 两边各 16dp，比例约 1.83:1，
     * 393dp 屏上落 196dp。它不是"一张大图"，而是**这一页唯一的主视觉** ——
     * 现状那一版把头像与名字挤在一行 60dp 的条目里，整页没有重心（用户原话「感觉不太行」）。
     *
     * ⚠️ 高度定死而不跟着底图比例走：底图是"该画师热门作品的第一张"，
     * 它随时可能缺档、也可能换人换图，跟着比例变会让整页布局在取数前后跳一次。
     */
    val artistHeroHeight: Dp = 196.dp,
    /**
     * 画师介绍页 hero 底图的**模糊核半径**。
     *
     * 10dp（2026-10-01 用户报「hero 底图感觉有点模糊过头了」）。这是**模糊核的参数，
     * 不是屏上看到的模糊量** —— 见 [artistHeroBackdropScale]：`CoverHeroBackdrop` 的
     * modifier 链是 `.scale(s).blur(r)`、缩放挂在外层，所以屏上实际模糊 ≈ `r × s`，
     * 这一档的观感要按 `10 × 1.15 ≈ 11.5dp` 读。
     *
     * 为什么不是直接沿用 [space11]（漫画详情页那一档 32dp）：那一档的职责是"把头部染成
     * 这张封面的颜色" —— 详情页里底图**压在封面卡下面**，糊到什么也认不出正是它要的。
     * 而介绍页里底图**就是这一页唯一的主视觉**，同一条 32dp 叠上 1.35 倍放大之后
     * 屏上约 43dp（本机 ≈119 物理像素），已经和图像自身的细节同一个量级。
     *
     * ⚠️ 与 [artistHeroBackdropScale] **是一对**，调一个必须调另一个：模糊降下来后
     * 放大也该跟着降（多出来的那圈纯粹是在放大一张已经不够清楚的图）。
     */
    val artistHeroBackdropBlur: Dp = 10.dp,
    /**
     * 画师介绍页 hero 底图的**放大量**。
     *
     * 1.15。这一项存在的唯一理由是给模糊留出外扩余量：`Modifier.blur` 的默认边缘处理
     * 会按原矩形把模糊结果裁掉，不放大就会在四边露出一圈被裁淡的边。下界是
     * `1 + 2 × blur / min(宽, 高)` —— 这块是 361×196dp 的横条，**按矮边算**：
     * `1 + 2 × 10 / 196 ≈ 1.102`，取 1.15 留一点余量。
     *
     * 为什么不像详情页那样保留 1.35：放大是**直接**损失清晰度的一步（用更少的源像素
     * 铺更多的目标像素），1.35 在 361dp 宽的底图上要凭空放大 487dp 再裁回来 ——
     * 详情页可以这么干是因为它不在乎内容，而这一页在乎。
     */
    val artistHeroBackdropScale: Float = 1.15f,
    /**
     * 平台入口胶囊上那枚**品牌色小方块**的边长。
     *
     * 22dp：要让 14dp 的字形（[platformGlyphSize]）四边各留 4dp 呼吸，
     * 又不能大到跟胶囊里的平台名抢 —— 它是"哪一家"的色锚，不是主视觉。
     */
    val platformBadgeSize: Dp = 22.dp,
    /**
     * 品牌色小方块里那枚**字形**的边长。
     *
     * 14dp：与画师卡上的站标（[artistSourceMarkSize]）同档。两个数一样不是巧合 ——
     * 它们在同一页上同时出现（hero 里的外链排、网格卡里的署名行），差一档就会读成"两种徽标"。
     */
    val platformGlyphSize: Dp = 14.dp,
    /** 列表行尾「›」指示符的点击/占位宽度。 */
    val chevronWidth: Dp = 12.dp,
    // ────────────────────────────────────────────────────────────────────
    // Bottom Navigation Insets 契约（全局唯一真源）
    //
    // 责任划分：
    //   1. 系统      -> navigationBars inset（由 WindowInsets 提供，谁都不许硬编码）
    //   2. BottomBar -> 自身高度 + 自身与系统 inset 的间距（barBottomGap）
    //   3. Screen   -> 只消费 [LocalBottomBarClearance]，不得再自行叠加 Bar 高度
    //
    // 页面内容底部留白 = LocalBottomBarClearance.current + navigationBars inset（由 Scaffold/NavHost 提供）。
    // 任何页面都**禁止**再额外加 Spacer(这笔留白) 或写 88dp/96dp 这类魔法数。
    //
    // 真源是 [LocalBottomBarClearance]，**不是**下面这个常量：常量只作为窄窗默认档值与单测基线存在，
    // 页面一律不许直读它。档位判断（该留多少）收口在唯一的 provider 处，由 `wideScreenLayoutMode()`
    // 驱动 —— 按档位的 `if` 散进各页面是要禁止的形态。
    // ────────────────────────────────────────────────────────────────────

    /** 悬浮底栏外壳高度（与 VeneraFloatingNavBar / LiquidGlass 的 64dp 保持一致）。 */
    val bottomBarHeight: Dp = 64.dp,
    /** 底栏与系统导航栏之间的视觉间距。 */
    val bottomBarBottomGap: Dp = 12.dp,
    /**
     * 底栏在位时页面内容需要预留的净留白（= 底栏高度 + 底栏底部间距）。
     *
     * ⚠️ **这个常量不是真源。** 它是窄窗（底栏可见那一档）的默认值兼单测基线；
     * 页面要避让时读 [LocalBottomBarClearance]，直读本常量会让侧栏档的 FAB / 回到顶部钮浮空。
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
     * 分段控制器**每颗药丸之间**的间隙。
     *
     * 取现成的 space5 而不是 MD3 规范里的 4dp：这个值原本是 4dp，但组件把它当像素用了
     * （见 VeneraSegmentedButton 的 px/dp 对账），真机上实际渲染出的环宽是 4×密度 ≈ 10.5dp。
     * 单位修正后要让观感停在用户已拍板的那一档，就写 10dp。
     *
     * 2026-09-28 组件换成"每颗自持药丸、没有外框"之后，这一档不再兼任
     * "分隔线在两格之间居中"的职责（分隔线随容器一起取消了）。
     */
    val segmentedGap: Dp = space5,
    /**
     * 折叠态顶栏（小标题行）高度：与 Miuix `TopAppBarDefaults.CollapsedHeight`
     * 同值的本地镜像（该常量在库内是 internal，读不到只能照抄）。
     */
    val topBarCollapsedHeight: Dp = 52.dp,
    /**
     * 大标题 / 小标题的左内边距：Miuix `TopAppBarDefaults.TitlePadding` 的本地镜像。
     *
     * 结果态把搜索条件胶囊吸附到顶栏左上角时，横向起点要与大标题**同一个 x**，
     * 否则那行胶囊读起来是"飘在标题左前方"而不是"接替了标题的位置"。
     */
    val topBarTitlePadding: Dp = 26.dp,
    /**
     * 吸附态那行条件胶囊的最大宽度 = 屏宽 × 本比例。
     *
     * 上限的由来：顶栏的小标题是**居中**的（Miuix 的 TopAppBar 把 title 摆在
     * `(屏宽 − 标题宽) / 2` 处），左侧净空只有 (屏宽 − 标题宽) / 2。
     * 标题实宽随系统字体缩放变，算不准，所以取一个任何屏宽下都留得住居中标题的比例：
     * 最窄的 393dp 屏也给标题与胶囊之间留下约 8dp。胶囊再多也不越过这条线 ——
     * 超出部分在行内横滑，而不是把标题挤走。
     */
    val collapsedChipWidthFraction: Float = 0.34f,
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

    // ── 「关于这张图」半模态的三个尺寸（2026-10-01 两轮重排，用户先报"信息卡再做下美化"、
    //    再报"还是有点乱"。第一轮留下的 `sheetBottomClearance` / `sheetFieldLabelWidth`
    //    已随第二轮一起删掉：前者的那套"字段名在左、值在右"定义列表整块没了，后者被
    //    下面那列分类名取代 —— 留着一枚没人读的 token 只会让下一个人以为版面还是老样子）──
    /**
     * 半模态里**块与块之间**的间距。
     *
     * 12dp（= [space6]）。判据只有一条：**组间必须大于组内**。
     * 这一页的"组内"是卡片内衬 [cardContentPadding]（8dp）与网格/胶囊的行距 [space4]（8dp），
     * 而此前组间写的是 `space3`（**6dp**）—— 组间比组内还紧，读起来是"某一张卡内部的第二段"，
     * 分组感整个失效（邻近原则）。12 > 8 才是成立的读数。
     *
     * ⚠️ 别拿它替 [sectionGap]（14dp）：那是**页面级**区块之间的距离，
     * 半模态是一张浮在别的东西上面的面板，节奏要更紧一档。
     * 也**不是**给底部留白用的：底部动作条现在是一条真实存在的兄弟节点，
     * 它自己就把内容与屏底隔开了，不需要再垫一层（更不能用 [bottomBarClearance] ——
     * 那 76dp 是给"被一级悬浮底栏压住"的页面留的，半模态盖在底栏上面，谁也不挡）。
     */
    val sheetGroupGap: Dp = space6,
    /**
     * 半模态标签墙上**每桶左侧那一列分类名**的宽度。
     *
     * 64dp，一列**定宽**。为什么定宽：后面跟的是一整片**会折行的胶囊**，不定宽时几桶之间的
     * 胶囊左缘就会参差（「角色」两字比「元数据」三字窄 12dp），读起来像没对齐的表格。
     *
     * 为什么是 64：它要装下「元数据」这种三字标签**外加一枚 14dp 的档位图标**
     * （12sp 三字 ≈36dp，加图标与 4dp 间隙 ≈54dp，MIUIX 的 13sp 档下 ≈57dp），
     * 再留一点系统字体缩放余量。给到 72dp 会让右边那片胶囊少摆得下小半枚 ——
     * 那一列只是索引，不该比内容还宽。
     */
    val sheetTagLabelWidth: Dp = 64.dp,
    /**
     * 半模态**底部动作条**里每一项的高度。
     *
     * 48dp = MD3 列表项触达位下限，与 [VeneraIconButton][com.venera.compose.components.venera.VeneraIconButton]
     * 内部钉死的那个数同档。这一条压在屏底、且是这一页唯一的动作出口，触达位不能比图标按钮更小。
     */
    val sheetActionHeight: Dp = 48.dp,
    /**
     * 半模态**滚动区**的高度上限 = **屏高 − 本值**。
     *
     * 为什么要有上限、而且必须按**屏幕**算：内容区的高度不能再由"面板当前多高"决定。
     * 那是一个自我引用的环 —— 面板高度由内容量出，内容的可用高度又由面板给，
     * 用户在面板上拖一下就会让两边互相推着走（表现是面板盖满屏之后下拉不动、原地高频抖动）。
     * 上限一旦是"屏高减一个常量"，内容高度就与面板当前姿态无关，环断开。
     * 实测读数与成因记在 `gallery/domain/GallerySheetScroll.kt` 的头注。
     *
     * 为什么是 140dp：留给**同一张 sheet 里不参与滚动的那几样** ——
     * M3 拖动把手那一档（约 48dp）、底部动作条 [sheetActionHeight] 及其上下内衬（约 64dp）、
     * 再加一点呼吸。宁可保守（面板最多比屏矮一截），也不能切掉动作条 ——
     * 那一刀切下去用户看到的是"下载原图"整条没了。
     */
    val sheetBodyHeightReserve: Dp = 140.dp,
    /**
     * 大图页连播那条进度线的高度。
     *
     * 1.5dp：比发丝线（1dp）厚一档，因为它**压在图像上** —— 图有浅色区域时 1dp 会整条消失，
     * 而这条线是"还有多久翻页"的唯一说法（见 `GalleryAutoPlayProgressLine`），
     * 看不见就等于这个功能不存在。也不取 2dp：那是分隔线（[divider]）的量级，
     * 摆在整屏宽上会读成"底栏多了一道边"。
     */
    val autoPlayProgressThickness: Dp = 1.5.dp,
    /**
     * 骨架屏流光一个周期的时长（毫秒）。
     * 3000 = master 用的 `shimmer_animation` 包的 duration 默认值
     * （包内另有 Interval(0, 0.6) 让行程只占前 60%，后 40% 停一拍）。
     */
    val shimmerPeriodMillis: Int = 3000,

    // ── 通用控件尺寸（跨页面复用，非 Search 专属）──
    /** 单行输入框 / 搜索框高度。 */
    val searchFieldHeight: Dp = 48.dp,
    /**
     * MD3 **docked search bar** 的高度。
     *
     * 与上面那枚 48dp 不是一回事：48dp 是"表单里的单行输入框"，这一档是搜索条本体
     * （胶囊 + 内部图标 + 右侧动作位）。数值取 MD3 规范原值 56dp，
     * 圆角用它的一半（走 [VeneraShapeTokens.extraLarge]，MD3 档实测就是 28dp）。
     */
    val dockedSearchBarHeight: Dp = 56.dp,
    /** 单行输入框内文字左右内边距。 */
    val fieldHorizontalPadding: Dp = 14.dp,
    /**
     * MD3 **单行列表项**的最小高度（补全行、历史行）。
     *
     * 48dp 是 MD3 列表项触达位的规范值。此前的补全行纵向只有 4dp 内边距，
     * 整行约 24dp —— 远低于触达位下限，手指按不准，还容易连带误触相邻那一行。
     * 定成**最小**高度而不是定高：标签名过长换行时行该能长高，而不是把文字裁掉。
     */
    val listRowMinHeight: Dp = 48.dp,
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

/**
 * 页面内容底部要为导航让出的净留白 —— 这条契约的**唯一真源**。
 *
 * 为什么必须是 Local 而不是常量：侧栏档（`Medium` / `Expanded`）整条底栏不渲染，
 * 留白应归 0，而 24 个消费点若各自按档位写 `if`，同一个数就有 24 个来源，
 * 「有底栏 ⟺ 有留白」这条不变量只能靠人记。收口成一个注入点后，档位判断只发生一次，
 * 底栏可见性与这个值由同一个 `WideScreenLayoutMode` 驱动 —— 由构造保证，不靠纪律。
 *
 * 用 `compositionLocalOf` 不用 static：值会随窗口形态变化（自由窗口拉宽跨断点），
 * static 会让整棵子树在跨档时不做最小重组。
 *
 * 默认值取窄窗（底栏在位）那一档，让未显式提供 provider 的子树（如独立 Activity 的页面）
 * 仍拿到正确留白，而不是静默塌成 0。
 */
val LocalBottomBarClearance = compositionLocalOf { VeneraSpacing.bottomBarClearance }

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
