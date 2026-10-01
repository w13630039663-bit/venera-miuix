---
name: lazy-list-anchor-drift-on-head-insertion
description: 首页「顶栏重叠」真根因：条件项冷启动插到 LazyColumn 头部导致滚动锚点漂到 idx=1，第 0 项被画进 contentPadding 留白
metadata:
  type: project
---

首页「阅读统计」压在顶栏大标题上、**滑一下回顶部就好** —— 真因不是顶栏避让高度不够，而是 LazyColumn 的滚动锚定漂移：

冷启动时 `ui.todayPages` 还是 0，`if (ui.todayPages > 0 || ui.weekPages > 0)` 那个**第 0 项不存在**；Room 数据到达后它插到列表头部，LazyList 为保持「当前首屏那一项不变」把视口锚到新 index 1，于是真正的第 0 项被画进 `contentPadding` 那块留白里（LazyList 不在 contentPadding 处裁剪）→ 观感就是内容顶到状态栏底下。

**诊断指纹**（屏幕上打一行临时 overlay 即可读到）：`idx=1 off=0 pad=149 ho=0 co=0` —— 留白值正确、顶栏全展开，只有首屏 index 不是 0。

**How to apply:** 根治办法是**别让项在头部生灭** —— 把 `if (数据条件) { item { … } }` 改成 `item(key = "…") { if (数据条件) { … } }`：项恒定存在，判空只决定内容，头部没有「插入项」这个动作，锚点就不会动。事后补救（`rememberLazyListState` + `scrollToItem(0)`）会**差一帧**，真机观感是「先贴在顶栏上、再自己跳下来」的一闪，别当终解。也别再回去调 `statusBarTop + 104.dp`（真机确认其他四个主 Tab 用同一地板没问题）。同类「返回/重建后状态被打回」的坑见 [[project-nav-entry-recomposition]]，多轮误诊的定位法见 [[project-multi-ai-regression-triage]]。
