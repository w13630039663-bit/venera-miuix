---
name: miuix-topbar-state-not-observable
description: miuix TopAppBarState.heightOffsetLimit 是普通 var 不是快照状态（负值语义），composition 期读它不会随写入重组
metadata:
  type: project
---

`top.yukonga.miuix.kmp.basic.TopAppBarState`：`heightOffset` / `contentOffset` 走 `mutableFloatStateOf`（可观察），**但 `heightOffsetLimit` 是普通 `var`**（源码 `TopAppBar.kt:306`），而且存的是**负值**（`updateHeightOffsetLimit` 写 `-大标题高`）。顶栏自己在布局期直接读它，所以画得对；页面在 composition 期读它，写入不会触发重组，要等下一次无关重组才拿到新值。

**Why:** 想让内容避让「大标题折叠顶栏」的展开高度时，很自然会去读这个字段 —— 它会给出首帧 0、之后不更新的表现。

**How to apply:** 真要用它，就在 `LaunchedEffect` 里跟几帧（`withFrameNanos`）抄进自己的 `mutableStateOf`；别指望直接读就收敛。另注意 `contentPadding` 被算成 NaN 时 LazyList 不报错，而是**当作没有留白**。

⚠️ 但**先确认重叠真是留白不足**：首页那次「顶栏重叠」查到最后跟这个字段无关，真因见 [[lazy-list-anchor-drift-on-head-insertion]]。相关：[[reference-gradle-cache-sources-jars]]、[[project-card-size-drivers]]。
