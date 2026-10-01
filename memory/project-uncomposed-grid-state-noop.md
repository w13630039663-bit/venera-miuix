---
name: project-uncomposed-grid-state-noop
description: 撤掉 HorizontalPager 之后，另一面墙不在组合时 scrollToItem 是空操作（effect 静默失效）；新增带默认值的必填语义参数会让调用点漏传同时骗过编译与单测
metadata:
  type: project
---

批次 K（2026-09-30）把画廊首页从"同一目的地内的两页 Pager"合成一屏之后，留下两条只有读代码
才会发现的静默失效，都已修，但**同类形状还会再出现**：

1. **`scrollToItem` 打在没有组合的 grid state 上是空操作。** pager 时代两页同时被组合
   （`beyondViewportPageCount = 1`），所以"新一轮结果落地 → 滚回顶部"那个 effect 一直真跑得动；
   合一屏之后 `when(wallFeed)` 只组合当前那一面，用户在日榜那面点推荐节头的「换一批」时，
   `forYouGridState.scrollToItem(0)` 什么都不做 —— 等他切过来正好落在"新一轮结果的深处"，
   也就是那段注释声称要防的事，**而代码一行都没少**。修法：把 `wallFeed` 加进 `LaunchedEffect` 的键，
   让"切到这面墙"本身成为那次补滚的触发点。
   同一条判据适用于任何"给不在屏上的 LazyList/LazyStaggeredGrid state 下命令"的代码。
2. **新参数给了默认值 = 调用点漏传时编译绿、单测全绿。** `GalleryForYouPage(sections = emptyList())`
   默认值让"猜你喜欢那面墙整片没有四节"这件事静默通过 464 条单测（本项目没有 Robolectric，
   ViewModel 与组合层摸不到）。
   **规则：给"漏了就错但不会报错"的参数不要用默认值**；非要用时，接线改完要**逐个调用点回读**，
   并把这类缺口列进真机清单（那次就是清单第 ⑧ 条）。

**Why:** 这两条都不是逻辑错，而是"检查面看不见"——`git diff` 干净、编译干净、单测干净，
只有真机或逐行读接线才露出来。用户明确要求过：降级与失效路径不许静默交错（宁可错慢）。

**How to apply:** 动画廊那三面墙（搜索 / 日榜 / 推荐，各自一把 gridState）的滚动、刷新、
续页判据时，先确认"这一面墙此刻在不在组合里"；给共用 composable 加参数时先问"漏传会怎样"。
相关既有教训：[[lazy-list-anchor-drift-on-head-insertion]]（节点头必须恒定存在）、
[[project-nav-entry-recomposition]]（条目重建组合会把 LaunchedEffect 再跑一次）。
