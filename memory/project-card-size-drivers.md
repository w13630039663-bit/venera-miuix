---
name: project-card-size-drivers
description: 用户说"卡片太大"指的是整张列表卡，而整卡高度由信息列与标签区驱动、不由封面决定；顶栏 bottomContent 是浮层且各页用 104.dp 魔数手调留白 —— 这两点各烧掉过三轮往返
metadata:
  type: project
---

**先确认改哪个元素。** 2026-09-20 收藏页"卡片太大"的反馈，我两次理解成"详情页封面太大"并去改 `detailCoverWidth`（110→140→122→92 来回），用户真正要的是**收藏页那张列表卡**变小。用户说的"卡片"= 列表里的卡，"缩略图/封面"才指图。尺寸类反馈动手前先复述一句"改的是 X 页的整卡还是详情页的封面"。

**整卡高度不由封面决定。** 只缩封面缩略图，卡片可以完全不变。三个独立驱动项：

1. 信息列的最小高度 —— `ComicTileLayout.ComicTileDetailed` 里曾写死 `heightIn(min = 180.dp)`，封面缩到 128 也白缩。
2. 标签区 —— 同一组件里是 `tags.take(10)` + `FlowRow(maxLines = 3)`，每个 chip ≈32dp，三行 ≈116dp。所以**标签多的源（picacg）卡高 200dp+，标签少的源（禁漫实测只给分类词、description 为空）矮一截** —— 同一页两种高度就是这么来的。
3. 描述行数（有标签 2 行 / 无标签 3 行）与 padding。

**顶栏浮层与 104.dp 魔数。** 各页内容顶部留白普遍写成 `statusBarTop + 104.dp`（全仓约 9 处），而 `VeneraTopAppBar(bottomContent = …)` 是**浮层、不参与列表排版** —— 有筛选项药丸的页面就会被压在首行内容上（2026-09-20 分类二级页真机截图即此）。别的页面靠手调常量糊：Favorites `+48.dp`、Download `+92.dp`、History 多选时 `+44.dp`。正解是量出浮层实际高度（`onSizeChanged` 挂在 padding **之前**才含自身留白）计入，分类页已这么改；同类遮挡在其它带 bottomContent 的页面仍可能出现。

**104.dp 会被系统字体缩放压破。** 2026-09-21 真机截图：首页启动时「阅读统计」整行压在折叠大标题「首页」上。根因不在浮层，而在 miuix 大标题是 `title1 = 32.sp`、且顶栏**内部自己带 systemBars 上边距**（见 [[reference-gradle-cache-sources-jars]]）—— 字体放大后展开高度就超过 `statusBarTop + 104.dp`。首页已改成 `maxOf(statusBarTop + 104.dp, 实测顶栏展开高度)`：取大而不是替换，保证不会比其他主 Tab 更靠上。同类遮挡若在别的页面复现，先问用户「设置→显示→字体大小」是否放大过。

**观感口径一律照抄现成的**（见 [[feedback-mirror-official-values]]）：单列行卡统一走 `components.ComicRowCard`（从搜索页逐行搬出），封面宽度用 `listCoverWidth`(92dp) + `coverAspectRatio`(0.72)，标签用 `searchVisibleTags` 取 5 个 / `FlowRow maxLines=2` / `VeneraTagChip`。

相关：[[project-shared-element-transition]]、[[project-nav-entry-recomposition]]
