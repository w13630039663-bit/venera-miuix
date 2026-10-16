# 批 97 · 桌面「本机已下载」那一屏（读侧数盘上那一棵树）— 2026-10-12

题面出自排期 `remaining-plan-and-reuse-2026-10-10.md:111`（批 96 落完之后**新添**的那一格，代价档 小-中）。
批 96 把整话写进盘上并在交付物里量到 89 颗 / 24,905,629 B / 清单 `complete:true`，
而桌面**没有任何一处读那棵树** ⇒ 用户按了长按、盘上多了 24 MB 而看不见。本批补的就是那一条读侧与那一屏。

## 0. 改动表（现量，逐颗点名）

| 文件 | 新建/改 | 这一颗做什么 |
|---|---|---|
| `gallery/data/DesktopComicDownloadedList.kt` | **新建** | 读侧：数 `<根>/<站>/<号>/<话目录>` 那一层，逐页查盘得页数与字节，四档认不出都交回 `null` 而不抛 |
| `gallery/ui/DesktopComicDownloadsPane.kt` | **新建** | 装载那一件（一次 IO 读盘 + 一句取证读数）与展示那一件（只吃纯数据，能喂着离屏渲） |
| `gallery/ui/DesktopComicPane.kt` | 改 | 第五行「本机已下载」+ `DesktopComicScreen.DOWNLOADS` + 分岔 + 引擎闸那一支 `false` + 挂载点 |
| `gallery/ui/DesktopGalleryHomeSections.kt` | 改 | `ComicDownloads` 那一格（sealed 成员、可选中分支、错误表分支）+ 把批 96 那句旧账改成今天的 |
| `gallery/ui/DesktopGalleryDomainPanes.kt` / `DesktopGallerySettingsPane.kt` | 改 | 两处 `when` 跟着穷尽（编译器点的，不是我猜的） |
| `README.md` / `README.en.md` | 改 | 那一条「漫画章节下载」的后半句：已下的那些今天数得着了，还缺的是**删不掉** |
| `gallery/data/DesktopComicChapterDownload.kt` | 改（**下半**） | 清单那颗件的形参 `comicId` → **`chapterId`**，`desktopComicDownloadChapter` 添一枚 `chapterId` 形参（号与话号两枚并存：前者只进报错那一句） |
| `gallery/ui/DesktopComicDetail.kt` | 改（**下半**） | 那颗 runner 把 `episode.id` 递进清单那一格（判据 ⑪ 钉这一整串实参，含一条负向） |
| `gallery/data/DesktopComicDownloadedList.kt` | 改（**下半**） | 号改从**目录名**取；回核从"号与目录名相等 + 前缀"换成**整名相等** `dir.name == desktopComicChapterDirName(order, title)` |
| `DesktopComicDownloadedListTest.kt` | **新建** | 十一颗（读侧的行为与两条分家规矩） |
| `DesktopComicDownloadsScreenTest.kt` | **新建** | 八颗（那一屏的三态、接线计数、读数与上屏两句） |
| `DesktopComicHistoryScreenTest.kt` | 改判 | 首页那行从 4 颗改 5 颗，并把按 `rows[2]/rows[3]` 的索引访问换成按 key 取（见 §2.4） |
| `DesktopStaleClaimsTest.kt` | 改 | 添 ㉔（整棵 main 不许再写"任何一屏都数不着"） |
| `DesktopReadmeStaleClaimsTest.kt` | 改 | 添 ⑤（中英两份同一句 + 必须点名还缺的那一层） |

`app/` 一字未动（现数 0 行）；不引新 jar；没有一笔台架打向对岸。

## 1. 对岸锚点（逐字回核，含它自己那两处不一致）

- **两颗读侧两个口径**（这就是排期那一行提的"开工第一问"）：
  - `app/…/download/DownloadManager.kt` 的 `stateOfChapterDir` 吃 `expectedPages` + 清单 `complete` + `listFiles` **计数**；
  - `app/…/download/LocalComicManager.kt` 只看有没有图（`images.isNotEmpty()`）。
  ⇒ 桌面**沿批 96 已立的第三口径并做硬**：清单说 `complete:true` **且**逐页查盘（复用 `desktopComicChapterIsComplete`，两处共用而不是各写一份），另数一份"盘上非空的那几页"给屏上说缺页。
  ⇒ 三档差异登记在此，不在桌面两处各认一种（判据：台架 P4 撤掉那颗复用会同时红三颗）。
- **三档口径逐条登记**（排期那一行要求的正是这件事，不许两处各认一种）：

  | 输入形状 | 对岸 `chapterOfflineState`（`ChapterCompleteness.kt:31-45`） | 桌面本批读侧 |
  |---|---|---|
  | 目录里没有清单 | `null` ⇒ 不算这一章（`stateOfChapterDir:308` 读不出即 `null`） | 「跳」那一格计数，不摆 |
  | 有清单、`complete:false`、`expectedPages` 在 | `PARTIAL`（`pageCount < expected`） | 摆出来，念「缺 N 页」 |
  | 有清单、**两个标记都没有**（对岸**开下载之前**就写了清单的那批老数据） | **`COMPLETE`** —— 注释钉着"有图即算完"，理由是那批现存离线书会一夜之间变"未下载" | 「跳」那一格（`expectedPages` 那一枚字段是读侧的硬要求） |
  | 盘上颗数比清单的 `expectedPages` 少而 `complete:true` | `COMPLETE`（只看颗数 ≥ 期望） | **不报齐**，念「缺 N 页」—— 桌面逐页按页名查，`0007.jpg` 缺位而 `0008.jpg` 在，对岸数得成"够数"而桌面不算 |

  ⇒ 最后两行是**桌面刻意更严**（屏上那一句是用户据以删文件的话，宁可念少不许念多），第三行是**桌面刻意不同**（这一屏只数批 96 那一套自己写下的形状，不去冒充"本地离线书架"的全量）。
  两句都写进屏上那句「N 条清单读不出（手工放的书或下到一半的话）」的括号里，不是只有代码注释。
- **`LocalComicScreen.kt:315-321`「删除」那一格的形状**：`deleteLocalComic(comic)` 的 `Boolean` **被丢掉**，
  紧接着无条件 `Toast「已删除本地漫画」`；而 `LocalComicManager.kt:298-309` 是 `try { root.deleteRecursively() } catch → false`。
  ⇒ 对岸那一形今天会"没删掉也说删掉了"。桌面**不照抄这一处**（本批没做删除，做的时候结果必须上屏，见 §6 那一格）。
- **`LocalComicManager` 行走的是它自己那套扫描**（`PageImageFiles.kt:9` 点名两处扫描共用），
  而桌面的目录树是批 96 逐字回核 `DownloadManager` 那一套 ⇒ 桌面这一屏**只认自己写下的那一棵树**，不去冒充"本地离线书架"的全量。

## 2. 落下来的形状与它的判式

### 2.1 「齐不齐」吃盘上，清单只是其中一半

`readChapter` 逐页 `0 until expected` 查 `NNNN.jpg`：`isFile && length() > 0` 才算一页 ⇒
`writtenPages`、`bytes` 两栏都是**从盘上数出来的**；`complete` 走批 96 那颗 `desktopComicChapterIsComplete(dir, expected)`。
清单说 `complete:true` 而盘上少页 ⇒ 那一行念「缺 N 页」。屏上那一句是用户据以删文件的话，**宁可念少不许念多**。

### 2.2 认不出的一律不抛，而**都要计数**

没有清单 / 清单读不出 / 缺字段 / 清单里的号与目录名对不上 / 目录名前缀与 `order` 对不上 ⇒ 交回 `null`，
由 `desktopComicDownloadedSkipped` 数成「跳」那一格。
理由：这一屏要在用户机器上数他攒了三年的东西，里面什么都可能有（手工放的书、导进来的裸目录、半截的 JSON）；
抛出去就是一屏空白，而**静默跳过**就是"下了而不见了" —— 那正是这一屏要治的病，绝不能在新屏里再造一次。

### 2.3 三态空句 + 一句读数，两本账各说各话

上屏那句有**四种**事实（目录没建 / 建好而空 / 有 N 条读不出 / 有东西），两两不许相同（判据 screen ②）；
取证那行 `D_本机下载 本= 话= 齐= 缺= 跳= 字节=` 六格全从状态现取，与上屏那句是**两句不同的话**，
且 `D_` 那类东西不进界面（screen ⑦ 钉负向）。

### 2.4 一处计划外：既有尺子按**行号**点首页那五行

新行只能**追加在末尾**（`DesktopComicPane.kt:193-194` 那两行注释写的就是这件事）：
`DesktopComicHistoryScreenTest` 的 ① 从前按 `rows[2]` / `rows[3]` 取 key 与题名，插进中间会把那两枚指到别的话上去。
本批把那两枚索引访问换成按 key 取（`rows.first { it.key == … }`）并把行数钉成 5 ——
这与批 94 的「行号会漂」是同一族：**按位置点的尺子，加一行就红**，而红的不是被测件。

## 3. 扰动台架（两份，合起来覆盖上下半）

- **`_qa/b97_perturb.js`（上半那份，20 笔）跑到 P9 主动停**：P1–P9 **逐笔咬住**，台账里 `BAD`（一笔都没红）/ `WRONG`（要求不红却红）/ `BUILD_FAILED` 各 **0 条**。
  停的原因不是台架坏了，而是**下半要改的那两行正是 P1/P2 打的锚点**（号与目录回核、目录名前缀回核）——
  先改完再一次跑齐，比同一颗件跑两遍省一半台架时间。
  ⚠️ 停在"P10 已 apply 而未跑"那一笔：由 `node _qa/b97_perturb.js revert 10` 逐字还原，并现数两处锚点（`Text(desktopComicDownloadedReading(state)` 残留 **0**、表头那句 **1**）才继续动源码。
  已咬的九笔各打在自己那颗判据上，例如 P5 红在「清单读不出时那颗抛了：`IllegalStateException`」、P7 红在「那一格念的是号而不是编出来的题名：`a` expected `<1>` but was `<a>`」。
- **`_qa/b97b_perturb.js`（下半合跑，19 笔）**：N1 写侧那格改从目录名取（**打 §6 那条缺陷**）、N2 调用点把本子号当话号递进去、N3 撤掉整名回核、N4 读侧的号改回从清单取、N5–N18 = 上半没跑到的 11 笔 + 三笔回流（README 中英 + main 注释），N19 等价改写（`count{}` → `filter{}.size`）**要求不红**。
  起手 `check` 现数：**19 笔各命中一处，可跑**。**下半那四笔已逐笔咬住**（红单原文，逐字取自 `_qa/b97b_perturb.summary.txt`）：
  N1 写侧那格改从目录名取 ⇒ 红在 `DesktopComicChapterDownloadTest` ⑥；
  N2 调用点把本子号当话号递进去 ⇒ 红在 `DesktopComicDetailWiringTest` ⑪（这条正是"两枚 id 都在手里、递错不炸编译"那一格）；
  N3 撤掉整名回核 ⇒ **一次红两颗**（`目录名前缀对不上order那一档点名` + `对岸写的整话认得出而题名对不上要点名`）；
  N4 读侧的号改回从清单取 ⇒ 红在「号从目录名取，清单那一格不许顶替」。
  台账跑到 N5 时本文在写，其余 14 笔（含 N19 等价改写要求不红）与"还原后的全量"由 `_qa/b97b_final.txt` 收尾那一次读数钉进本节与 §5。

### 3.1 台架终账：**19 笔全部咬住**，其中两笔是量具自己坏了、改完复跑

- 第一遍跑完 19 笔：`N1..N13` 与 `N16..N18` 逐笔红在自己的判据上，`N19` 等价改写**要求不红而确实一笔没红**（台账原文：`BAD 这一笔（N19 …）要求不红，而它确实一笔没红 —— 这是正确输出`）。
- 🔴 **`N14` 与 `N15` 第一遍是 `BUILD_FAILED`（编译面就红）** —— 这两笔不是产品缺陷而是**量具自己坏了**：
  - `N14` 的终点串写成 `java.io.File(config.dataDir)`，而 `java.io.File` **没有 `File(File)` 构造子**；
  - `N15` 的起点串只圈了那一行的**块首三行**，撤完剩下 `glyph`/`content`/`reason` 三行悬空。
  改法：`N14` 换 `java.io.File(config.dataDir, "downloads-x")`（能编译而真的换了另一颗根）、`N15` 圈**整块七行**。
  复跑（`sh _qa/perturb_driver.sh b97b 15 14`，从中间接跑不清空前面账）两笔都咬住：
  `N14` ⇒ `DesktopComicDownloadsScreenTest :: 那一档接到那一屏 | downloads 根只许一处递给读侧 expected:<1> but was:<0>`；
  `N15` ⇒ `DesktopComicHistoryScreenTest :: 首页漫画域那行从今天起点得动 | 漫画域从今天起五行…`。
  复跑后的"还原后的全量"仍是 `EXIT=0`、`1310 / 154 / 0 失败`。
- ⚠️ 这一族的形状值得记：**台架笔红在编译面 ≠ 判据成立**，它只说明那枚笔没打到行为层；而它**看起来像红**（驱动会照常记一行），所以逐笔归账时必须去分 `BUILD_FAILED` 与 `RED` 两类（本仓此前记过"编译面就红的废笔"，批 94 的 P7 是第一次，这是第二次）。
- 收尾现数：`_qa/b97b_current.txt` 归 **0**、锁目录已释放、`venera-desktop-probe` 同名进程 **0**、`app/` **0 行**。



## 4. 交付包与包内六趟（`_qa/b97_pkg_screen.sh`）

包 `_qa/desktop-exe-2026-10-12-b97/`（216 MB / cfg `classpath=100 missing=0 orphan_jars=0` / 包内 `desktop-942c135d…jar` 与 `engine-probe…jar` **两颗都与现产物逐字节同一颗**）。
六趟都走 `--start-domain=COMIC --start-section=comic-downloads`（**通用那一路种子，本批没加新参数**），都念到 `D_取证参数 --start-section 要去到那一行（值=comic-downloads）`，
每趟 `异常行=0 枚`、`收尾后同名进程数=0`、数据目录是 `_qa/b97/` 那份隔离副本（真人 `%LOCALAPPDATA%\venera` 一字未碰）。

| 趟 | 盘上真相（量具自己数） | 那一屏念的读数 | 这一趟证到的是 |
|---|---|---|---|
| t1 `fresh` | 话目录=0 页=0 字节=0 空=0 | `本=0 话=0 齐=0 缺=0 跳=0 字节=0` | 空的那一档不许静默，六格照样报得出 |
| t2 `bare`（三层 + 1 颗页，**不放清单**） | 话目录=1 页=1 字节=8 空=0 | `本=0 话=0 齐=0 缺=0 跳=1 字节=0` | 认不出的那些**计数点名**而不是消失（"下了而不见了"的反面） |
| t3 `copy`（整棵 b96 那 89 页） | 话目录=1 页=89 字节=24,905,629 空=0 | `本=1 话=1 齐=1 缺=0 跳=0 字节=24905629` | **字节那一栏与盘上逐字同轨**（读数不是再拼一次名字） |
| t4 `trim 20`（删末尾 20 页，清单仍写 `complete:true`） | 话目录=1 页=69 字节=18,170,063 空=0 | `本=1 话=1 齐=0 缺=1 跳=0 字节=18170063` | 清单说齐而盘上少页 ⇒ **不许报齐**（屏上那一行念「缺 20 页」；读数那两栏按**话**数，别把 缺=1 读成缺 1 页） |
| t5 `blank 3`（末尾 3 颗截成 0 字节，颗数不变） | 话目录=1 页=89 字节=23,108,496 空=3 | `本=1 话=1 齐=0 缺=1 跳=0 字节=23108496` | **零字节那一颗不算一页**，而字节只数真在的（颗数对不上字节是正确行为） |
| t6 `andshape`（清单那格换成**话号** 1461318，号仍住在目录名） | 话目录=1 页=89 字节=24,905,629 空=0 | `本=1 话=1 齐=1 缺=0 跳=0 字节=24905629` | §6 那条缺陷修好之后的**正证**：对岸形状的清单照样摆得出来 —— 号从目录名取，那一格不许顶替 |
| t7 `copy` + `HOLD=200`（给窗口捕获留窗） | 话目录=1 页=89 字节=24,905,629 空=0 | 同 t3 | 那一屏的**像素那一帧**（见下面那段）；`收尾后同名进程数=0`，现数同名进程 **0** |

**窗口捕获那一帧取到了**（第七趟 `b97t7` 用 `HOLD=200` 把窗按住，走 `get_window_state`，`occlusionSafe=true`、`isForeground=false` 不抢前台、`relatedTransientWindows=[]` 没有 Error 弹层；窗口标题 `Venera Desktop R1-F 最小闭环 #26616`，判帧归属按标题里的 `#<pid>` 即可）：

- 左边那一列五行齐：探索 / 收藏夹 / 阅读历史 / 源管理 / **本机已下载**（选中那一行带「本」字圆座与紫色药丸），**新行没有把既有四行的位置挤走**；
- 表头那句上屏的是 **`本机存过 1 本 · 1 话 · 齐了 1 话`**（用户话，不带 `D_`，与取证那行是两句）；
- 行里那一句是 **`1461316 · 第 2 话 第2話 · 89/89 页 · 已下全 · 23.7MB`** ⇒ 号打头（一屏摆几本也分得清）、`第 2 话` 是 1 基人话而目录那格是 0 基的 `0001`、页数与字节都来自盘上。
- ⚠️ 观感那一格登记：`第 2 话 第2話` 两截挨着念有点重复（前一截是我们算的序、后一截是站方题名），**不是缺陷**，要不要去掉序由下一批连同"删除"那一格一起定。


## 5. 数字（全部现量，台架还原后那一笔）

- 全量：`EXIT=0`、**`suites=154 tests_sum=1310 testcase=1310 @Test=1310 files=161 failures=0`** —— 三方口径 `node _qa/three_way_counts.js` 报**三方对得上**。
- 量具目录：`node _qa/check_ruler_catalog.js` 报 **`表行=154 表合计=1310；XML 类=154 XML 合计=1310` ⇒ 0 处漂移**（本批新登记两行：`DesktopComicDownloadedListTest.kt` 11、`DesktopComicDownloadsScreenTest.kt` 8；跟着添用例四行：`DesktopStaleClaimsTest` 23→24、`DesktopReadmeStaleClaimsTest` 4→5、`DesktopComicChapterDownloadTest` 10→11、`DesktopComicDetailWiringTest` 10→11）。
- 判据 1287 → **1310**（+23）：上半 21 颗（读侧 11 + 那一屏 8 + ㉔ + ⑤）+ 下半 2 颗（⑥b 与 ⑪）。
  ⚠️ 其中 `DesktopComicDownloadedListTest` ⑤ 与 `DesktopComicHistoryScreenTest` ① 是**改判不是添颗**（前者换成"对岸形状的清单必须摆得出来 + 题名对不上才点名"，后者首页行数 4⇒5 并把 `rows[2]/rows[3]` 索引访问换成按 key 取）。
- `desktop/src/main` **115 颗 / 24,353 行**、`desktop/src/test` **161 颗 / 30,687 行**（台架释放后复量，与还原后的全量同一份树）。
- `app/` **0 行改动**（`git status --porcelain app/ | wc -l` = 0）。
- 交付包 `_qa/desktop-exe-2026-10-12-b97/`：216 MB、cfg `classpath=100 missing=0 orphan_jars=0`、包内 `desktop-942c135d…jar` 与 `engine-probe…jar` **两颗与现产物逐字节同一颗**（`check_pkg_jar_sha.js` 逐颗核）。
- 台架两份合计 **39 笔**（`b97` 的 P1–P9 + `b97b` 的 19 笔，其中 2 笔改完复跑），`BAD`（该红不红）**0 条**、`WRONG`（该不红却红）**0 条**。


## 6. 计划外那一格：清单里 `chapterId` 两端**语义**不同轨（真缺陷，量出来的）

这条不是取证缺口，是用户今天就能踩的缺陷，而它是**只读量对岸那一族时撞上的**（本批台架与判据都没打它 —— 记下来免得被读成"验过了"）。

### 现量两条

- 对岸写清单：`app/…/download/DownloadManager.kt:502-509` 的 `writeChapterMeta` ⇒ `put("chapterId", task.chapterId)`，
  那一格装的是**这一话的号**。
- 桌面写清单：`desktop/…/gallery/data/DesktopComicChapterDownload.kt:95` ⇒ `append("\"chapterId\":\"").append(comicId)`，
  同一个字段名装的是**本子的号**。
- 盘上实证（批 96 包内那一趟留在 `_qa/b96_local` 的副本）：
  `{"chapterId":"1461316","title":"第2話","order":1,"expectedPages":89,"complete":true}`
  而这一趟种的是 `--comic=1461316 --download=1461316/1461318` —— **1461316 是号、1461318 才是话号**，
  那格写的是前者。

### 后果是双向都断

- 对岸读桌面下的：`DownloadManager.kt:309` `stateOfChapterDir` 拿清单里那格与要查的话号比 ⇒ 恒不相等 ⇒ 报「没下过」。
  这一条的后果**对岸自己写在注释里**（`:329-330`「已完成章节会被 `isChapterDownloaded` 判成未下载、点一下就直接重下一遍」），
  消费点现数两处：`ComicDetailScreen.kt:1650` 批量下载用 `filterNot { isChapterDownloaded(...) }`（认不出就默认选中 ⇒ 真重下）、
  `:1724` 那一行的三档标记；另一处 `AndroidBusinessPorts.kt:537` 是它对外那枚口。
  再加 `getDownloadedChapterFiles`（`:283` 同样按 `chapterId` 找目录）交回 `null` ⇒ 离线页读不到。
- 桌面读对岸下的：本批新写的读侧拿那格当"号"与**目录名**比（`readChapter` 里 `comicId != comicDir.name`）⇒
  对岸写的清单里那是话号 ⇒ 不相等 ⇒ 归进「读不出」那一档而**只计数不摆**。

也就是说：**批 96 那句"命名逐字回核对岸"只核到了路径三段与页名，没核清单字段的语义** —— 同一份 `chapter.json` 在两端读作两种东西。
而本批的读侧本来就是为了治"下了而看不见"，却在新边界上把它又造了一次（这正是本仓那一族的形状：**新屏里再造一次老缺口**）。

### 修法（已定，动代码要等台架收尾）

1. **写侧**：`desktopComicChapterManifestText` 那一格的实参换成**话号**（逐字对齐岸 `:505`）。
2. **读侧**：本子的号**从目录名取**（`comicDir.name`）—— 那本来就是目录名的定义，而且对 `b96` / `b96b` 两版已经写在用户盘上的清单**向后兼容**（那格旧值不再参与判定）。
3. 读侧原来那枚"号与清单对不上"的回核换成**题名回核**：对岸的目录名是 `padStart(order)_safeTitle`（`DownloadManager.kt:156`），
   清单里带 `title` ⇒ 目录名后缀必须等于 `desktopComicSafeChapterTitle(title)`，那一档照样抓得住"导进来的裸目录 / 手工放的书 / 半截的树"。
4. 跟着要动的账：判据 ⑤ 与 ⑩、台架 P1、批 96 rounds 与手册行 146 里那句"命名逐字回核"要加改判半句、README 那一条不动（它没承诺互认）。
5. 新增的可测形状：**对岸形状的清单**（`chapterId` = 话号）必须在桌面这一屏摆得出来 —— 这一条只有把两端的字段语义钉在同一颗尺子上才抓得住回流。

⚠️ 本批**没有**改这一格的原因不是"不重要"，而是台架 `_qa/b97_perturb.js` 正在逐笔改坏再还原同一批源文件；
测试源码一动就会把正在跑的台账染脏（那一族记过：并发改动会被算到前一笔头上）。修法与判据随下一笔落地。

## 7. 欠账与下一件

- **删不掉**：那一屏今天只数不删。对岸那一形（`LocalComicScreen.kt:315-321` + `LocalComicManager.kt:298-309`）带删除，
  但它**把 `Boolean` 丢掉并无条件说"已删除"** —— 桌面做这一格时结果必须上屏，且路径必须落在 `downloads` 根内（防越界），
  并且只删这一屏自己写下的那一目录。代价档 小-中（一颗写侧件 + 一句确认 + 一排判据 + 一趟包内"删完再数"）。
- 没有**下载队列**那一屏（进度条、暂停/继续、系统通知），也没有~~**逐页进度与取消**~~。
  **⚠️ 2026-10-13 批 100 逐格改判**：逐页进度与取消**已接**（进度每页一格且起手念的是盘上已有的数、取消是协作式且那一档落 `complete=false` 而一页不删）；**还缺的只有队列那一屏**（多话排队 + 重启续排，方案在 `desktop-comic-download-queue-2026-10-13.md`）与系统通知（桌面结构性不搬）。
- ~~对岸那两档「已下载 / 离线不全」的**行内标记**（卡上那一枚）没接 —— 现在只有独立那一屏数得出，墙上与详情那列表还没带上。~~
  **⚠️ 2026-10-13 批 99 已接**：卡上那枚药丸两档各一句（全下齐 / 有缺），读侧**复用批 97 那一颗**而没有造第四种"齐不齐"口径。详 rounds `desktop-comic-card-offline-badge-2026-10-12.md`。
- `order` 会漂那一格**继承自对岸**（批 94 同题，要改得两端一起拍）。
