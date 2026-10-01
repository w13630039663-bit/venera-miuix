---
name: project-segmented-pills-style
description: VeneraSegmentedButton 从 MD3"药丸中的药丸"换成"每颗自持药丸"（2026-09-28 用户点名照 pixez-miuix 动态页改）：为什么容器描边在玻璃顶栏上不成立、保留了哪些口径、动冻结文件怎么记
metadata:
  type: project
---

2026-09-28 用户拿两张真机截图报"画廊两页切换与收藏页图片收藏那排按钮看着像坏的"，
点名改成 [137458/pixez-miuix](https://github.com/137458/pixez-miuix) 动态页「全部/公开/私密」那种效果。

**根因不是几何错位**（我先猜错了）：旧形态是整条大药丸描一根 0.5dp `outlineVariant` hairline +
里面一颗实心滑动块。那根描边贴在**实时模糊的亮画作**上时几乎看不见，于是整条读起来像一个断掉的框。
每颗自己带底之后不再依赖描边，背后是什么内容都读得清。

**Why:** 玻璃顶栏上的元素可见性不能按"不透明背景上的规范样式"推 —— 判据是"贴在最亮的内容上还读不读得出边界"。
这与 [[project-glass-chrome-inline-area-rules]] 是同一条规律的两面：那边是**别涂不透明底**（会出横贯硬边），
这边是**别只靠细描边**（会被内容吃掉）。

## 换了什么、保留了什么

- 换：去掉外层容器 Surface/border 与竖分隔线，去掉滑动的果冻块（它依附容器才成立）；
  改成每颗自己 `background`，选中=实心 `primary`+`onPrimary`，未选中=`surfaceVariant`
  叠现成的 `selectedSurfaceAlpha`（0.5，VeneraChip 禁用态同档），加 0.96 弹性缩放（阻尼仍 0.7 那族）。
- **保留（历轮真机反馈定的，一条没动）**：`segmentedHeight` 48dp / 宽屏 56dp、字号宽屏升 `itemTitle`、
  `segmentedGap`=10dp、**整条宽度仍由调用方按"单段占屏宽 25%"推导**（用户"太宽太散"那条反馈）。
- API（`options/selectedIndex/onSelect`）不变，四个调用点零改动。

**How to apply:**
- 用户说"改成 X 那个效果"时，先分清**形态问题**与**可见性问题**：这次真正要修的是可见性，
  但用户给的处方（照参照物）恰好也解决了它 —— 照处方做，同时在记录里写清真实根因。
- 共用组件被 N 处用到时，用 AskUserQuestion 明确问"只改点名的两处 / 一次改到位"，
  用户 2026-09-28 选了**一次改到位**（理由：不要同一屏出现两种分段器）。
- 改到**冻结页的外观**（`FavoritesScreen` 2026-09-18 冻结）时：只改它那句已过期的注释，
  并往 `FREEZE-STATEMENT.md` 追加一条豁免记录（写清"只改注释、逻辑与几何一字未动"）——
  这是本仓对"动了冻结文件"的固定处置，见 [[reference-venera-workflow-docs]]。
- 参照物源码读法：GitHub 的 tree API（`api.github.com/repos/<r>/git/trees/HEAD?recursive=1`）
  配 raw.githubusercontent 逐个文件读；本机**没有 gh CLI**，`gh` 命令直接 command not found。

相关：[[project-favorites-secondary-row-in-chrome]]、[[project-glass-chrome-inline-area-rules]]、
[[feedback-mirror-official-values]]、[[project-breadboard-gap-rounds]]
