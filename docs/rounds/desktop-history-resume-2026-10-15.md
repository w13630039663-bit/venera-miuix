# 第一百一十四批方案 · 历史那一行按下：库里那两个数一直在，是没人把它们递给阅读器

日期：2026-10-15 ｜ 前置：批 94（详情屏「继续阅读」）、批 113（rounds `desktop-shelf-reroll-2026-10-15.md`）
本篇要动的域：`desktop/src/main`、`desktop/src/test`、`_qa`（`app/` 一颗不碰）

## 0. 题面（三处现读，一处**待查**）

桌面「历史」那一屏按下之后**永远从第一页起**，而库里那两个数本来就在：

- `desktop/src/main/…/DesktopComicHistory.kt:18-24` —— 一条 `HistoryRecord` 存的字段里有
  `chapterIndex`、`pageIndex`、`totalPages` 三格（写侧 `:52-54` 落 `lastChapterIndex` / `lastPageIndex`）；
- `desktop/src/main/…/DesktopComicHistoryScreen.kt:137` —— 那一行的 `clickable { onOpen(…) }` 递出去的是
  `DesktopComicTarget(rec.comicId, rec.title, rec.coverUrl)` **三格**，章与页**一个都没带**；
- `desktop/src/main/…/DesktopComicReader.kt:84` 载荷 `startPage` 的注释自己写着：
  「『从库里那一行续读』今天还没接（历史那一屏按下去目前从第一页起），所以这里唯一的非零来源是种子」。

⇒ **不缺列、不缺数据，缺的是一次接线**：这一格不动跨端数据面（与批 97 那次「清单两端语义不同轨」不是一件事），
也不需拍板；对岸的成品参照就是批 94 接上的那颗「继续阅读」主胶囊（同一个动线，从详情那一行进）。

**已定案（2026-10-15 复核，量具换了一把）**：子代理报来一条「Dart 那侧没有 `chapter.json` 这一层」，
我这边的复核读数 `git grep -c 'chapter.json' master -- lib/` 命中 **0 颗文件** —— 一度按老纪律当成"量具坏了"挂着，
现在三条读数把它钉死了：
- `git show master:lib/network/download.dart | wc -l` = **879 行**（文件在），
  同一把 `git grep -l 'class ' master -- lib` 也能列出几十颗文件 ⇒ `git grep` 在 `master` 上不是坏的那把；
- 那颗文件的落盘处只有 `:307-308`（`cover<ext>`）与 `:580-581`（`<index><ext>` 页图），
  整库里 `Directory(`/`File(` 那 14 处**没有一处**写 `chapter.json` ⇒ **Dart 那侧确实没有这一层**；
- 与本仓批 97 那条记录不冲突：共读 `chapter.json` 的"对岸"**不是 master 的 Dart**，而是本分支的 Kotlin Android 模块 ——
  现读三处：`app/src/main/java/com/venera/compose/download/ChapterCompleteness.kt`、`DownloadManager.kt`、`LocalComicManager.kt`。
⇒ 本批那一格（往清单里补 comicId）若要动，动的是 **`app/` 那三份 Kotlin 文件 = 受保域** ⇒ 必须先拍板（与批 112 记的那条一致）；
而**续读这一格根本不碰清单**，所以本批不受这一格牵连。
⚠️ 教训一条：**「对岸」在本仓有两个所指**（master 的 Dart `lib/` 与本分支的 Kotlin `app/`），
跨端事实要先问清是哪一岸，否则一条真读数会被读成矛盾。

## 1. 顺带量到的一条跨端事实（本批不动手，记进对岸账）

**先说清是哪一岸**：与桌面共读同一份 `chapter.json` 的是**本分支的 Kotlin Android 模块**
（`app/src/main/java/com/venera/compose/download/DownloadManager.kt:284` 读那颗文件；`ChapterCompleteness.kt:16-37` 是它的纯函数判据），
而 **master 的 Flutter 那侧没有这一层**（`lib/network/download.dart` 全文 14 处 `File(`/`Directory(` 里只有 `cover<ext>`（`:307-308`）
与 `<index><ext>` 页图（`:580-581`），没有任何一处写 `chapter.json`）。两岸都要记，混着说就会造出假矛盾（见 §0 末那条教训）。

「停手」与「取消」在两岸都是**两档**，逐行读数：
- Flutter（master）：`lib/network/download.dart:143-147` `pause()` 只置 `_isRunning = false`（不删不改名 ⇒ **留半截**）；
  `:119` `cancel()` 走 `Directory(path!).delete(recursive: true)`（⇒ **整本删掉**）。
- Kotlin（本分支 `app/`）：`DownloadManager.kt:188` `pause(taskId)` / `:198` `pauseAll()` 是一档，
  `:220` `cancel(taskId)` 与 `:229` `delete(taskId, deleteFiles = true)` 是另一档，后者在 `:234` 走
  `File(task.directoryPath).deleteRecursively()` ⇒ **同一张两档的表，只是键名换了写法**。

桌面那一侧的对应物：停手 = 协作式取消（`DesktopComicChapterDownload.kt` 落 `complete=false` 而页一颗不删）+
队列表落 `STOPPED`；"整棵删掉"在桌面是**另一枚把手**（批 98 的 `DesktopComicChapterDelete`，两步确认）。
⇒ 三岸语义**方向一致**：停手留半截、删除清干净。本批不改它，这一格就此结案。

## 2. 判式预算（下一轮落地时先写这些）

| # | 档 | 钉的是哪一格 |
|---|---|---|
| ① | 行为 | 纯件：一条记录 + 库里那两个数 ⇒ 算出的载荷里 `episodeIndex` / `startPage` 就是那两个数 |
| ② | 行为 | 库里那一页越界（`pageIndex >= totalPages` 或为负）⇒ 夹到合法边界而**不是**照原样递（宁可错慢不可静默交错） |
| ③ | 行为 | 那一行不可打开（源不可用那一档）⇒ 不产载荷（与 `desktopComicHistoryRowCanOpen` 同一把闸） |
| ④ | 接线 | 那一屏的 `clickable` 递出去的那一句里**带着章与页**（钉整串表达式，不钉标识符） |
| ⑤ | 接线 | 载荷那一跳住在装配根与那一屏之间，**不在**展示件里读库（展示件仍只吃纯数据） |
| ⑥ | 形状 | 全仓 `startPage =` 的非零来源只有两处（种子 + 这一格），别长出第三条 |

## 3. 台架预算

P1 递 `startPage = 0`（把库里那一页丢了）⇒ ①④；P2 越界不夹（原样递）⇒ ②；
P3 不可开那一档也产载荷 ⇒ ③；P4 把读库搬进展示件 ⇒ ⑤；P5 `episodeIndex` 丢了（只递页）⇒ ①。

## 4. 代价档

纯件约 10 行 + 那一屏一行 + 载荷装配一处；判式 6 枚 + 台架 5 笔。
不动 `chapter.json`、不碰 `app/`、不动队列那一线（批 112/113 的成果原样留着）。
