---
name: project-favorites-secondary-row-in-chrome
description: 收藏页「漫画/画廊」二级分段器必须挂顶栏 bottomContent（2026-09-27 用户拍板 A 案）；附 PJZ110 实测顶栏几何与两种失败模式的数字
metadata:
  type: project
---

2026-09-27 用户拍板：收藏页「图片收藏」那一屏的二级分段器（漫画收藏 / 画廊收藏）**归进顶栏 `bottomContent`**，不留在内容层、也不做成随墙滚走的 header。

**Why:** 一条**常驻且要跟着折叠顶栏走**的控件行，只要钉在 chrome 外面，就必然二选一出问题，两种都真机实测过：
① 让位写常量 → 顶栏折叠后玻璃底边与该行之间多一条空带（用户第一次报"上滑就这样"）；
② 让位改成 `onSizeChanged` 逐帧量顶栏真实高度 → 空带确实归零，但玻璃在底边留一条**横贯全屏的硬边**，该行孤零零坐在硬边之外的裸背景上，且墙的视口跟着顶栏变（`Column` + `weight(1f)`），折叠 98px 行程里内容额外多走 256px，还要每滚一帧整屏（含 pager 三面墙）重组一次。
挂进 `bottomContent` 后它是顶栏的一部分：跟着走、被同一块玻璃罩住，两种毛病同时不可能发生，且**不需要任何测量**。

**How to apply:**
- 顶栏让位一律回到常量地板 `statusBarTop + 104.dp`（+ 每多一条常驻分段器行再加一个 `segmentedRowHeight`），并且**放进网格的 `contentPadding`**，不要放进包内容的外层 `padding` —— 这条是 `FREEZE-STATEMENT.md` 第七轮已经收过口的结论（那一轮把"为测顶栏高度引入的整套东西"全删了，只留注释）。再动这块前先读第七轮，别把它 retired 的模式重新引进来。
- 门控用 `mode`（横滑**停稳**才落）而不是 `pagerState.currentPage`（过半就翻），外面套与页面级分段器同款 `AnimatedVisibility(fade + expand/shrinkVertically)`，否则切页时那一行会当场跳掉。

**PJZ110 实测几何**（1080×2376 / 420dpi → 1dp=2.625px，可直接复算不用重测）：状态栏 0–120px；`miuix TopAppBar` 展开态本体 120–524px（= barHeight 235 + `LargeTitleBottomPadding` 11 + `bottomContent` 158），折叠态收到 120–268px（`CollapsedHeight` 52dp=137px）；一条 48dp 分段器行含 `space3`(6dp) 上下留白 = 158px。`space3` 只有 6dp，别按 12dp 推。

**miuix 折叠是真·高度变化**（`miuix-ui-android-0.9.4-rc01-sources.jar` 的 `TopAppBar.kt`）：`Layout` 的 measure 块里 `barHeight = lerp(collapsedHeight, collapsedHeight+expansion, 1-collapseFraction)`，`expansion` = 大标题实测高，所以顶栏**测量高度**随滚动连续缩小 —— 这条推翻了"折叠只是平移、高度不变"的猜测。

**读法**（无 sources 时）：`unzip -p <...>-sources.jar commonMain/top/yukonga/miuix/kmp/basic/TopAppBar.kt`；注意 `node -e "..."` 里**中文字面量会被 Git Bash 打烂**（匹配恒 null），要匹配文本就把脚本落成 `.js` 文件再跑。`uiautomator dump //sdcard/x.xml` 的双斜杠是躲 MSYS 路径转换的。

相关：[[project-glass-chrome-inline-area-rules]]、[[miuix-topbar-state-not-observable]]、[[project-card-size-drivers]]、[[reference-venera-workflow-docs]]
