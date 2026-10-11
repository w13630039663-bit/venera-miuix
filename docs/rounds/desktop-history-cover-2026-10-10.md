# 批 82.6 · 历史那一笔不再丢掉封面（2026-10-10）

接着批 82 那张账往下做的又一件：**两端共用 `comic_history` 那一列里，桌面写的那一笔把封面丢了**。
`app/` 一字未动、不 commit、不 push。

## 1. 三条定案

1. **优先级不许桌面定**：会话封面 = 详情解析出来的那张优先、入口带的那枚兜底，两档都空才是空串。
   这条口径的原文在 `app/…/feature/ComicDetailViewModel.kt:862-863`
   （`details?.comic?.cover?.ifBlank { comic.coverUrl } ?: comic.coverUrl`），旁边就有一段成文的理由：
   照抄入口值等于给历史页留一个永久空白框。判据 `DesktopComicCoverChainTest` ④ 用正则**从 Android 源码现取那两句**，
   对岸改形状时桌面这条会红，而不是被留在原地当规格。
2. **那一枚地址在拼载荷那一刻就算好**，不留到写口再算：`desktopComicReadingOf(…, detailCover)` 里过一次
   `desktopSessionCover`，`DesktopReadingProgress.coverUrl` 只是原样带。写口那颗件要的是"这一笔落库的内容"，
   把"选哪一张"留在里面就等于让共用那张表上的一格由写口自己发明。
3. **种子那一趟留空串是实话**：`--read=` 不经过详情那一行、也没有入口卡，填一枚猜出来的地址会把没量过的东西写进共用的表。
   那一趟只跑在隔离数据目录里（`_qa/b826_local`），碰不到用户的真库。

## 2. 这一格为什么会漂着（三层）

- **写侧**：`desktopHistoryRecord` 从前写死 `coverUrl = ""`，注释的理由是"阅读那一屏没有封面地址"。
- **那句理由是假的**：三个地方手上都有 —— 首页那一行的 `card.cover`、历史那一行库里已有的 `rec.coverUrl`、
  以及 `loadInfo` 答复里的 `cover` 那一栏（桌面从前**根本没读**它，`ComicInfo` 只有题名/简介/章表三样）。
- **为什么没有一把尺子抓到**：`comic_history` 没有版本号也没有迁移，两端各写一种形状不会红；
  而批 82 的判据把当时那句错诊断**钉成了要求**（`assertEquals("", record.coverUrl)`，
  离屏真跑那颗也写 `assertEquals("", rec.coverUrl)`）⇒ 锚点替错话作证，手册 §0 坑 ㉖ 的**第十三次**。

后果不是"少一张图"：`saveHistory` 那条「空封面沿用旧值」只救**本来就有封面**的行，
桌面第一次写的那一行从此 `cover_url` 恒空，而手机侧 `HistoryScreen.kt:277` 与 `HomeScreen.kt:618` 都按那一格画图
⇒ 真机上是永久空白框（url 为空连请求都不发，Coil 那边一条日志都不会有）。
**老行会自愈**：下一笔带地址的写是 `INSERT OR REPLACE`，整行换掉，那一格跟着填上。

## 3. 判据：新增 8 条 + 改判 3 处

新增 `DesktopComicCoverChainTest`（8 条）：① 写口吃载荷那枚（**两种值各喂一次**，只喂一种时"吃载荷"与"写死常数"在同一把尺子下一样）
/ ② `desktopSessionCover` 三档 + 空白那档退入口（对岸用 `ifBlank`）+ 两档都没时**原样带**那枚空白串（对岸不 trim，桌面不加一刀）
/ ③ 载荷在拼那一刻算好 / ④ 对岸两处口径的回核（`coverUrl = session.coverUrl` 与那条优先级式子）
/ ⑤ 阅读器构造进度件时 `coverUrl = reading.coverUrl` 恰好一次 / ⑥ 三处落点各自交代来源（`card.cover` / `rec.coverUrl` / 种子空串）
/ ⑦ 种子那趟的载荷不猜封面（**配非空对照**，否则这一条会因"恒空"而假绿）/ ⑧ 那句旧说明下架（扫全文不跳注释）+ 对照是写口真在吃载荷。

改判三处（都是"锚点替错话作证"）：
- `DesktopComicHistoryTest` ①：`assertEquals("", record.coverUrl)` → 吃载荷那枚，KDoc 写明本批改判与序号；
- `DesktopComicReaderProgressWriteTest` ②（离屏真跑那颗）：`rec.coverUrl` 从"留空"改成断言**载荷里那枚 URL 一路流到 `HistoryRecord`** —— 这一颗是整条封面链唯一的离屏真跑证据；
- `DesktopComicDetailWiringTest` ①：锚点从整串实参表退到 `onOpenComic(DesktopComicTarget(`（坑 ㉖ **第十四次**：批 82.5 那两条 `desktopComicReadingOf(…, startPage = 0)` 的锚点也钉到了闭括号，本批添一枚实参就红在两处 ⇒ 一律退到"再改也不会误红"那一层，只钉调用点不带闭括号）。

跟改（必填字段带来的构造点，共 6 颗测试文件）：`DesktopComicHistoryWriterTest`、`DesktopComicDetailSemanticsTest`（11 处）、
`DesktopComicReaderTest`（6 处，并把「阅读载荷从详情载荷与章节推出」那条扩成喂两种封面）、`DesktopComicReaderSemanticsTest`（2 处）、
`DesktopComicSeedLandingTest`、`DesktopComicLoadInfoTest`（+2 条：`cover` 三档、键名回核对岸 + **负向钉桌面不许多认候选键**）、
`DesktopComicDetailReadingTest`（+1 条：`封面=有/无` 且**不许念出那串地址本身**）。

## 4. 现量读数（2026-10-10，全部当场重取）

- 三方口径：`desktop` **973 判据 / 126 颗类 / 0 失败**（`tests=` 之和 = `<testcase>` = 源码 `@Test` = 973，`three_way_counts.js` 念 `files=133`）；
  判据目录 `check_ruler_catalog.js`：**0 处漂移**。
- `app` 全套 **886 / 0 失败**（Android 侧行为零变更）；`git diff --stat -- app/` 空 ⇒ **`app/` 一字未动**。
- 规模：`desktop/src/main` 97 颗 / **19,869 行**（批 82.5 是 19,819 ⇒ +50 行、0 新文件）；`engine-probe/src/main` 8 颗 / 1,401 行。
- **起窗那一趟（`-Pkey=jm -Pcomic=1461316`，数据根隔离到 `_qa/b826_local`）**：
  `D_数据目录 D:\venera-compose\_qa\b826_local\venera（写入回读已验）` ⇒ 没碰真库；
  `D_落点 域=漫画(启动) … 详情=开(启动) 阅读=关(启动) 种子=comic`；
  **`D_详情 源=jm 号=1461316 态=上屏 话=15 简介=81字 封面=有 话号=1461316,1461318,1461319,1461321,1461322`**
  ⇒ 那 `封面=有` 是**这一栏第一次被量到**（jm 的 `loadInfo` 真回 `cover`，桌面真读到了）。
- 帧：窗口捕获一张（详情那一屏，题名「衣冠禽獸」那本、15 话列表照常摆 ⇒ 展示层没改坏），
  `relatedTransientWindows` 空 ⇒ 无 Error 框。帧只在内联会话里，**PNG 没落盘**（与批 81/82 同一格账）。
- 真实数据根未被写：`%LOCALAPPDATA%\venera\db\venera_core.db` 起窗前后都是 `2026/10/9 6:46:46`。
- 关窗残留：`Venera*` 窗口 0 扇，java 进程回到起手那两枚（3952 / 14332）。

## 5. 扰动台架（13 笔，全部咬住自己那一条）

`sh _qa/perturb_driver.sh b826 13`，逐笔报告在 `_qa/b826_p<N>.report.txt`，收尾全量 `EXIT=0`。

| 笔 | 改坏的那一格 | 红了谁 |
|---|---|---|
| C1 | 写口退回"恒空串"（批 82 那个形状） | ①、⑧ 对照、`DesktopComicHistoryTest` ①、离屏真跑 ② —— **两层同时红**，正是本批要的 |
| C2 | 封面优先级调了个 | ②、③、语义树那条点击回归（整对象判等抓到）、`DesktopComicReaderTest` 载荷那条 |
| C3 | 用 `isNotEmpty` 代替 `ifBlank` | 只有 ② 的空白档 —— 那一档本来就只在那儿分得开 |
| C4 | 拼载荷时直传详情那枚（丢掉兜底） | ⑦ 对照、③、载荷那条（两种值各喂一次才分得出） |
| C5 | 构造进度件时不吃载荷封面 | ⑤、离屏真跑 ② |
| C6 | 首页那一行填错列（题名当封面） | ⑥ |
| C7 | 历史那一行丢掉库里已有的那串 | ⑥ |
| C8 | 种子那一趟拿漫画号充封面 | ⑥ |
| C9 | 解析不读 `cover` 那一栏 | `DesktopComicLoadInfoTest` 键名那条 + 三档那条 |
| C10 | 自己多认一个 `coverUrl` 候选键 | 键名回核那条（负向那一半：`data["coverUrl"]` 零次） |
| C11 | 那一格恒念「有」 | `封面只念有无` + 两条整句判等 |
| C12 | 把整串地址念进 stdout | 同上三条（负向那半抓到） |
| C13 | 种子的阅读载荷填猜出来的地址 | ⑦ |

## 6. 台架自己被抓的一处

`report` 那颗用 `/<testsuite[^>]*name="([^"]+)"/` 取归属类名 —— `[^>]*` 会先吃进 **`hostname="DESKTOP-…"`**
（`name="` 是它的子串），于是 13 笔的红账全部打成同一个机器名，"哪一颗类红的"那一格整列丢失。
本批靠**用例名**回认（名字够独特），已把正则改成 `<testsuite\b[^>]*?\sname="`。
这是手册那条「读结果 XML 的归属要逐块切开」的又一变体：**归属字段本身也要过一次自审**，
尤其当它只是打印出来给人看的——它不参与判红判绿，所以坏了也没人管。

## 7. 还欠的那格（写清楚，不当覆盖）

- **真人那一趟没量**：非空封面要落进库里那一行，得从详情那一屏**按一行**进阅读屏；OS 级点击仍禁
  （手册 §8）。本批量到的是「详情真回了那串」+「那串一路流到 `HistoryRecord`」（离屏真跑），
  中间那一按不在账上。
- **只量了 jm 这一站**：`封面=有` 是 jm 的读数。别的站是否也回 `cover` 那一栏没量 ⇒ 不写成"各站都有"；
  真不回时入口那枚（`card.cover`）就是那条兜底路，行为与对岸一致。
- **详情那一屏仍不画封面**：对岸那屏头部有图，桌面这一屏只有题名/简介/章表。本批只把那一栏**读出来给历史用**，
  没顺手加像素（那是另一件事，要另开一批并配观感判据）。
- **「从库里那一页续读」还没接**（批 82.5 挂的那格）：历史那一行现在倒是把 `rec.coverUrl` 带进了载荷，
  但页号仍从第一页起。
