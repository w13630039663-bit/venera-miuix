---
name: project-loading-indicator-direction
description: 本项目加载态的设计方向 —— 不定进度一律用 M3 Expressive 的 CircularWavyProgressIndicator（不是 Miuix 的，Miuix 没有这个组件），并说明它来自哪个依赖、哪些地方仍留着旧圆环
metadata:
  type: project
---

2026-09-19 起，用户在详情页、阅读器、网络收藏页、搜索页连续点名要求把加载指示器换成 **`CircularWavyProgressIndicator`**（Material 3 Expressive 的「波浪形」圆环）。后续再加加载态默认按这个走。

**关键事实（别再重新查一遍）**：

- 这个组件**不在 Miuix 里**。`top.yukonga.miuix.kmp` 0.9.4-rc01 是当前最新 tag，main 分支也只有 `LinearProgressIndicator` / `CircularProgressIndicator` / `InfiniteProgressIndicator`。
- 它来自 **androidx material3**（`androidx.compose.material3.WavyProgressIndicatorKt`）。项目把 material3 钉在 `1.5.0-alpha22`（M3 Expressive 线，`gradle/libs.versions.toml:14`），所以**零新增依赖**就能用。宿主函数多数已带 `@OptIn(ExperimentalMaterial3Api::class)`；实测新加的地方不补 opt-in 也能编译。
- 它是手册 §4.2「不用 Material3 完整 UI 组件当业务组件」的一处**有意例外**，由用户点名批准。
- 描边/振幅/波长默认值按 **48dp** 尺寸给（aar 里是内联 `dp.toPx()`，未导出常量 —— 此点为按 API 形态推断，未实测）。**小于约 24dp 就别用**，会糊成一团。
- 尺寸已进 Token：`VeneraSpacing.loaderPage = 48.dp`（整页 / 区块居中）、`loaderInline = 28.dp`（行内、下拉刷新行、触底 footer）。

**已换完**：详情页（章节加载、预览卡头部那枚已从 16dp 提到 28dp）、阅读器 6 处（收敛成私有 `ReaderWavyIndicator`）、网络收藏页 4 处（含 `AccordionLoader` 整页加载与下拉刷新行）、搜索页（`ResultSkeleton` 两张 shimmer 空卡整体换成 `SearchLoadingIndicator`，5 个调用点）。

**仍留着旧 M3 圆环、用户还没点名的位置**：详情页评论状态行（16dp）与收藏面板按钮内联（18dp，嵌在 30dp 徽章槽里，换要放大）、`reader/ChapterCommentsSheet.kt`、漫画源管理页 `ComicSourceScreen` 9 处、`FavoriteImagesScreen`、`LocalComicScreen`、`StatsScreen`、`SyncBackupScreen`、`SourceEditScreen`、`WebLoginScreen`。另有 4 处 `LinearProgressIndicator` 是定进度条（下载 / 导入进度），不是转圈，未纳入这一类。

**2026-09-20 更新**：用户点名把探索页与分类二级页的刷新指示器也换掉了 —— `UnifiedExploreScreen` 的 `CenteredLoader` + 触底 footer、`SourceSectionScreen` 的 `CenteredLoader` 共 3 处已改为 `CircularWavyProgressIndicator`（用 `loaderInline` 28dp；footer 原本用 `badgeSize`，小于 24dp 不适用）。这两个冻结页的豁免由该指令给出。

**2026-09-21 的例外**：首页「可能你感兴趣」刷新中的占位，用户点名要的是**灰骨架 + 呼吸脉冲**（复用既有 `VeneraShimmer`，与轮播同高同圆角），不是波浪环 —— 那里原先挂的 `CircularWavyProgressIndicator` 已删。**判据**：整块内容在重拉时用骨架；只有一行/一个 footer 的小指示器才用波浪环。

相关：[[venera-ui-refactor-authoritative-docs]]、[[feedback-mirror-official-values]]
