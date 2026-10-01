---
name: project-implemented-but-unwired
description: 本仓有一类反复出现的缺口：能力函数写好了但零调用点。审缺口时先 grep 符号名，只命中自己声明行的就是没接线——比读文档快且不会漏
metadata:
  type: project
---

**Venera Compose 端口最容易被漏掉的一类缺口不是"没实现"，而是"实现了但没有任何调用点"。**

2026-09-22 一次全仓审计里，四个名字全仓**只命中它们自己的声明行**：
阅读器「收藏当前页」（导致收藏图集页与首页插图卡永远空着）、详情页评分提交、
收藏夹自定义排序的保存（于是 `CUSTOM` 排序按一个用户永远改不了的字段排）、
以及追更页（顺带追更调度器也没有调用点，整条链三段全断：不调度 → 不跑 → 无入口）。

同一轮还查出三个"有实现无入口/无出口"的：
搜索结果与探索卡片**完全没有长按菜单**（组件形参 `onLongClick` 早就有，只有网络收藏在用）；
章节列表 `take(80)` 之后**没有"显示全部"出口**，超 80 章的本子后面的章节静默不可达（master 有 `showAll`）；
打码/屏蔽**没有单条解除出口**（master 的 `_maskMenuEntries` 整组没移植）。

**Why:** 这类缺口在方案文档里查不到（文档只记"做了什么设计"），
在 git log 里也看不出来（函数是随某个大提交一起进来的，看起来"已完成"）。
只有从"用户能不能碰到它"这个方向反推才会发现。而且它们的修复成本极低 —— 纯接线活。

**How to apply:**
- 被问"还有什么能优化/还缺什么"时，先跑一遍符号反查：`grep -rn "<函数名>" --include=*.kt` 只命中声明行的，就是断线。
  同理 `grep -c "onLongClick" <某几个屏幕>`、`grep -rn "requestedOrientation"`（本仓阅读器无方向锁）。
- 报告时把"接线活"和"设计活"分开列 —— 用户对前者的耐心明显更高，一批能清好几条。
- 字体缩放是另一类系统性零适配：`grep -rc fontScale app/src/main` **整源码集 0 命中**，
  而顶栏大标题折叠高度硬编码 `104.dp` 有 18 处（见 [[project-card-size-drivers]]）。
- 相关：[[project-settings-placeholder-policy]]（另一种"看得见但没实现"的处置判据）、
  [[project-source-data-ceilings]]（有些缺口是数据面到顶，不是没接线，别混为一谈）。

## 2026-09-28 又攒了一批（归"第 2 轮"处置）

画廊侧：`GalleryFavoritesStore.notice`（StateFlow 从没被 collect，收藏档损坏那句"已另存为…"用户看不见）、
`contains(uid)`（大图页改用 `favorites.any{}` 线性找）、`GalleryFeedSource.guarded()`（被
`guardedWithBudget()` 取代后的残留）、`GalleryReverseViewModel.openTargets()`。
漫画侧：`ComicSourceManager.deleteJsSource` / `resetRepoUrl`（**源删不掉、仓库地址改不回来**）、
`FavoritesViewModel.invertSelection`、`LocalFavoritesManager.onRead` / `editTags`、
`ReaderSettings.autoCropBorders` + 整个 `BitmapSliceHelper`（假开关）、
`ComicSourceManager.searchAggregatedStream`（聚合流整条零调用）。

**判据同前**：`grep -rn "<符号名>" --include=*.kt` 只命中声明行 = 断线。
每项只有一个决定要做：**接上** 或 **删干净**，别让它继续以"看起来已完成"的形态躺在仓里。
排序与理由见 [[project-breadboard-gap-rounds]]。
