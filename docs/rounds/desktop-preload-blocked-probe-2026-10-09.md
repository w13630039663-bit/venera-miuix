# 批 60 · 「挡 / 无址」那两句到底能不能被量到（2026-10-09）

零产品代码改动。这一批是把批 57 留下的"只有判据没有读数"那两句拿去**造场景**，
造的过程里量到三条事实 —— 其中两条直接改掉了下一件的走法。

## 1. 用户黑名单产不出「挡」：它走的是 HIDDEN，压根不进 `cards`

造法第一版：往 `content_guard_rules` 插一条 `TAG / animated_gif`（应用自己那颗写口的同一形状），
起窗 `-Pdetail=1`。屏上：

> 站方答上了 57 张，本可以摆上屏的都挡下了：57 张命中屏蔽规则 animated_gif
> `[animated_gif] ·进册 0 张`

而 stdout 上 `D_预取` **一行都没有**。回代码对上因：`app/…/gallery/domain/GalleryGuard.kt:29`
里 `blockedByUser` 判在 `maskMode` **之前**，命中即 `HIDDEN`；`DesktopGalleryWallPolicy.kt:80` 只在
`BLURRED` 那一支才 `cards += DesktopWallCard(post, masked = true)`。
⇒ **黑名单命中的卡根本不进列表**，预取那颗 `filterNot { it.masked }` 永远数不到它们 ——
读数里的「挡=N」只可能来自**分级 BLUR** 那一档。这一条是量出来的，不是推的（判据 ⑤ 的 `blocked` 仍然真跑，只是它的生产者只有一条路）。

顺带一条取证层的：墙 0 张时 `-Pdetail=1` 的浮层开不出来，而 `D_落点 … 浮层=关 … 种子=detail` 两行把
"种子没吃到"与"没卡可开"分得清清楚楚 —— 这正是批 48 给落点加读数的直接回报。

## 2. 分级 BLUR 那一档在本机匿名面拿不到料

第二版造法：`venera_guard_prefs.json` 的 `nsfw_mode` 改 `BLUR`（跑前备份、跑完还原），
两跑各 12 页（`animated_gif` / `1girl`，yandere + safebooru 都给内容）：

```
D_预取 档=NEXT 排=1 取=SAMPLE      × 12
```

`挡=` 一次都没出现 ⇒ 这两站在匿名请求里**没有一张带成人标记**（`isAdultMarked` 恒假），
而第三站 Gelbooru 对匿名请求一律 401（屏上那行红字本来就写着）。
⇒ 「挡=N」这一侧仍量不到，但**原因是量出来的**：不是没试，是料不到手。要量它得先有 Gelbooru 凭据（那是用户手上的东西，本仓不代填）。

## 3. 「无址=N」的两条候选路径，本批量完：一条已死、一条要站方配合

它要的是"这一档没有地址"。两件事查完：

- **`ORIGINAL` 档不空**：`GalleryPost.kt:14` 那张实测表逐站写着原档取 `file_url`（yande.re 原图、Gelbooru `file_url`、Safebooru `file_url`），
  而 `desktopDetailTierUrl(_, ORIGINAL) = post.fileUrl` ⇒ 三站列表 DTO 都填得到，**换档换不出"无址"**。
  ⇒ 原计划的 `--tier=` 种子对这两句**没有用处**，这一条从批 61 的清单里撤掉（先加种子再找料是本末倒置，这里连料都不存在）。
- **只剩"视频没封面"一条**：`wallUrl` 对 `isVideo` 走 `videoPosterUrl`，那枚为空才算无址 —— 而那要站方真给一条没封面的视频卡，
  本机匿名面（Gelbooru 401、Safebooru 全站 `g`）造不出来。

⇒ 「挡=N」与「无址=N」两句的定位就此定下：**由判据真跑（批 57 的 ⑤⑥⑧），不由屏上取证**。
手册 §17 与本轮第 1、2 段合起来就是这一条结论的凭据；将来谁把它们写成"已量到"，来这份文件对。

## 4. 工具两条（都栽过一次）

- `_qa/procs.ps1` 抓 PID 后直接塞进一条 `taskkill`：两行 PID 会让**两扇窗都活下来**，本批就是这样留了 2 枚残留，复数才发现。
  现在有 `_qa/close_venera_windows.ps1`：逐条关 + 2 秒后复数 + 念 `residue=N`（跑过，`residue=0`）。
- `.ps1` 必须 **ASCII-only**：第一版带中文注释那份被 PS 5.1 当 GBK 读，`ParserError` 当场炸 —— 这条坑记过，本批又踩第二次，
  所以那句说明现在写进了那颗脚本自己的头部。

## 5. 数字

产品代码 0 处改动；判据仍 **743 / 96 类 / 0 失败**（`_qa/check_ruler_catalog.js`：0 处漂移）；
`content_guard_rules` 跑完剩 0 行（逐条删过并复看）、两份 prefs 已还原、窗口残留 0 枚。
