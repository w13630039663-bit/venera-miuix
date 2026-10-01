---
name: venera-miuix-master-branch-is-users-flutter-fork
description: 用户说"我原项目做的 X"= 本仓库 master 分支（Flutter venera-miuix，origin 同一个 URL）里他自己加的功能；去 lib/ 下用 git grep/git show 找，不要 clone 外部仓库
metadata:
  type: reference
---

本仓库 `origin` 就是 `https://github.com/w13630039663-bit/venera-miuix.git`，分支两枚：
`master` = **用户自己写的 Flutter 分支版 Venera（venera-miuix，Miuix 界面重构 + 一批上游没有的自研功能）**，`compose-migration` = 当前 Kotlin/Compose 重写线。所以用户给一个 venera-miuix 的 GitHub URL 时，**代码已经在本地 `.git` 里，不需要 clone**。

**读法（都是只读命令）**：`git grep -n -i "<关键词>" master -- lib`、`git show master:lib/pages/stats_page.dart`。想细读就把 blob 抽到 `.reference/venera-miuix/` 下再 Read。

**master 里有、上游 flutter-master 里没有的东西**（即"用户自研"，移植时是权威参考）：阅读统计页 `lib/pages/stats_page.dart`（含 `_TagNormalizer` 题材归一化、标签云、按月时间轴、环形占比图，以及 `read_stats(date,cid,type,pages,tags)` 表与 `HistoryManager` 的一批统计查询）、多 tag 搜索 `lib/utils/multi_tag_search.dart`（`tagQueryOf` / `normalizeTagValue` 含繁转简）、`lib/utils/opencc.dart`、光学径向转场 shader、MD3 悬浮底栏。

**How to apply:** 判断"原版有没有这个功能"要分清两个基线 —— 问「上游 Venera 有没有」看 `.reference/flutter-master/`；问「**我原项目**做过没有」看 `master` 分支。两者都会漂移，给结论时带 `master:<path>:<line>` 或 file:line。移植时**不要照抄 master 的缺陷**：本轮就发现 master 的标签云 `_dig` 只带中文显示名（原生标签源注定搜不到），这类点要在方案文档里单列"移植时要改的原版缺陷"。

相关：[[venera-ui-refactor-authoritative-docs]]、[[project-opencc-table-already-shipped]]
