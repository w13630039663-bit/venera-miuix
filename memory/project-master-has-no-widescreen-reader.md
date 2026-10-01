---
name: project-master-has-no-widescreen-reader
description: 负结果：master(Flutter) 阅读器与首页几乎没有宽屏分支，"照 master"在大屏适配上没有可抄形态；含唯一几条真实存在的宽度规则与骨架屏流光出处
metadata:
  type: project
---

**事实（2026-09-22 花了一整个子代理 + 1M token 逐字核出来的负结果，别再重查）**：

- **master 的阅读器 chrome 完全没有宽屏分支**：`lib/pages/reader/` 下**零引用** `changePoint`(600) / `changePoint2`(1300)；顶栏与底栏是贴边通栏（`scaffold.dart:152-168`，`left:0, right:0`）；页面图是纯黑底上的 `BoxFit.contain`，**不限宽**（横屏看竖页两侧留黑是 master 本来的行为）。
- 阅读器里**唯一**一条按宽度分叉的规则：`scaffold.dart:528-550` 的 `small = (maxWidth - buttons.length * 50) < 120` —— 宽分支 = 左侧 `E#:P#` 页码芯片 + `Spacer()` 把**纯图标**按钮推到右端；窄分支 = 按钮间插 `Spacer()` 均分、丢掉芯片。master 底栏按钮**没有文字标签**（图标 + Tooltip）。
- 阅读器另有几条定值（与宽度无关，但可照）：顶栏定高 `kTopBarHeight = 56`、底栏定高 `kBottomBarHeight = 105`（`scaffold.dart:15,17`）；章节目录与阅读设置都是**侧边抽屉定宽 400**（`scaffold.dart:662,727`）。
- **"每屏几张图"是按方向而非按宽度**（`reader.dart:499-516`，portrait/landscape 两个设置项，默认各 1），属用户设置，不是自适应。
- **master 首页唯一的宽屏规则**：`home_page.dart:65` —— `context.width > changePoint ? widget.paddingHorizontal(8) : 原样`，宽屏只是多 8dp 边距，内容仍通栏。master **没有** Hero 轮播这个部件。
- **骨架屏**：master 用 `shimmer_animation` 包（`lib/components/loading.dart:141`，`color: dark ? white : black` + 包默认值）。该包真实默认（查了 pub.dev + GitHub `maddyb99/shimmer_animation`）：`duration 3s`、`colorOpacity 0.3`、`direction fromLTRB`；色带 `width = 0.2`、色标 `[transparent, c@0.05, c@0.3, c@0.05, transparent]`、渐变 rect 横跨 `-0.5w..1.5w`；行程是 `Tween(0→1)` 套 `Interval(0, 0.6, Curves.decelerate)`（前 60% 走完、后 40% 停一拍）。**关键：它是"会移动的色带"，底色是静止的** —— 只做 alpha 呼吸是另一种东西，在浅底色上几乎看不出来。

**Why:** 大屏这一轮用户的硬要求是"观感尺寸用现成口径"（见 [[feedback-mirror-official-values]]），但阅读器/首页这两处 master **根本没有现成口径** —— 不知道这一点就会要么误以为"照 master 就行"、要么再花一轮子代理重查，要么把别的部件的数硬搬过来（后者已被否过一次）。

**How to apply:** 再遇到"大屏这块照 master 怎么改"，先说清"master 对这个部件没有宽屏形态"，再给几条**有出处**的路线让用户拍（复用已定稿口径 / 搬 master 那条唯一的宽度判据 / 用户自己给数），不要默默搬数。Compose 侧的宽屏收口口径已抽在 `components/WideScreenPolicy.kt`（>600dp → `min(540, 宽-48)`，手机档返回 `null`），列数口径在 `ComicPresentationPolicy.kt`。另注：`min(540, 宽-48)` 里"宽-48"在 >600dp 档**永远赢不了** 540（600−48=552>540），即 master 的宽窗底栏实际就是定宽 540。

相关：[[venera-miuix-master-branch-is-users-flutter-fork]]、[[project-home-recommend]]、[[project-loading-indicator-direction]]
