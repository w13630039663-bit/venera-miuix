# 第一百一十一批 · 「本机已下载」那一行给一枚「补齐」把手（2026-10-14）

## 0. 题面：那一屏只说不办

批 110 之后，`缺 N 页` 那一档会**多起来**：批 102 那道闸之前落盘的过盾页从此不算一页、被取消的半截话本来就算缺。
包内现量（批 110 rounds §13 那一帧）：

```
_manga_pandajunheleisajun-hiyaku · 第 1 话「第01话」 · 28/30 页 · 缺 2 页 · 2.5MB
D_本机下载 本=1 话=1 齐=0 缺=1 跳=0 字节=2658270
```

屏上把"缺 2 页"讲得清清楚楚，而**没有任何一个把手能治它**：用户要补只能回探索 → 点开那本 → 长按那一话
→ 重新排一遍。那一行右边今天那一枚是「删掉第 N 话的本机文件」（批 98），按下去是**越按越少**。
⇒ 这一批办的是"说了缺就得给一条补的路"。

## 1. 定档：一枚把手 + 那一屏也要驱动泵

两格缺一不可，所以同批办（分开就是"排上了没人跑"那种半截活）：

1. **行上第二枚把手**：只在 `!chapter.complete` 那一档摆，题面「补齐这一话」。
   按下去 = `DesktopComicDownloadQueue.enqueue(…)` 一笔，**盘上一颗文件都不动**（与批 105「撤掉这一笔」同一判据：
   入队不删、删除不排队，两枚把手各只改一件事）。
2. **`desktopComicEngineNeeded` 的 `DOWNLOADS` 那一支从恒 `false` 改成吃 `hasQueueWork`**。
   不然第 1 枚把手排上去的那一笔会一直 `PENDING` 到用户自己走去「下载队列」那一行 ——
   批 106 立那条规矩的理由就是"这一屏自己造的活不该等换屏"，今天这一屏也开始造活了。

### 行模型够不够（现量）

`DesktopComicDownloadedChapter` 已带 `sourceKey / comicId / chapterId / order / title / expectedPages / writtenPages / complete`
⇒ `enqueue` 那九枚形参里只有两枚没有现成值：

- `comicTitle`：盘上**根本没有本子的题名**（`DesktopComicDownloadedGroup.label` 就是 `comicId`，目录名也是净化过的号）。
  递 `chapter.comicId`。⚠️ 这一格**不上屏**：队列那一行念的是 `第 N 话「章名」 · 档位 · 页 · 本子 <comicId>`
  （`DesktopComicQueuePane.kt:176-177` 现量），`comicTitle` 与 `comicCover` 都不进那一句 ⇒ 不会把号当题名念给用户看。
- `comicCover`：递空串（同上，不渲染）。

`placeholder` 递 `false`：这一笔的号与话号都是从盘上真目录读回来的，不是占位。

## 2. 判式（预算 8 枚，新建 `DesktopComicShelfRepairTest`）

| # | 档 | 钉的是哪一格 |
|---|---|---|
| ① | 行为 | 不齐那一行交回"该摆补齐把手"，齐了那一行交回"不摆"（两档各喂一次） |
| ② | 行为 | 把手那句题面念得出补的是哪一话（带话序与章名，且**不许**念成"重下整话"） |
| ③ | 行为 | 从行递出去的那一笔 `enqueue` 九枚形参逐格对得上（`sourceKey`/`comicId`/`chapterId`/`order`/`title` 五枚必须原样；`placeholder=false`） |
| ④ | 行为 | 按一次只多一笔（同一行连按两次仍是表里一条 —— 复用批 101「重复入队不去」那颗口径） |
| ⑤ | 行为 | 补齐那一跳**不删盘上任何一颗文件**（按之前后逐颗比大小） |
| ⑥ | 形状 | `desktopComicEngineNeeded(DOWNLOADS, true/false)` 两档各一：有待跑才要引擎，表空就不该起（与 `QUEUE` 那一支同轨） |
| ⑦ | 接线 | 那一屏的 `when` 里 `DOWNLOADS -> hasQueueWork`（钉**表达式**而不只标识符 —— 手册里那条"钉整串"的纪律） |
| ⑧ | 形状 | 展示那一件仍"只吃纯数据与动作"：`DesktopComicDownloadsContent` 里零处 `DesktopComicDownloadQueue.` 直调（入队住在容器那一层，与删除同一分家规矩） |

⚠️ ⑥⑦ 是同一条判断的两把尺子（行为 + 字面），批 106 那一批就是这么钉 `QUEUE` 那一支的；只留一把就会被"改一支漏一支"。

## 3. 台架（预算 7 笔，逐笔要求红）

- P1 把手在齐了那一档也摆 ⇒ 咬 ①
- P2 题面写成"重下整话"（把语义换成假的） ⇒ 咬 ②
- P3 `enqueue` 把 `comicId` 递进 `chapterId` 那一格（两枚形参并成一枚的老坑） ⇒ 咬 ③
- P4 入队前不查 `existing`（改成恒加一条） ⇒ 咬 ④
- P5 补齐那一跳顺手 `deleteRecursively` ⇒ 咬 ⑤
- P6 `DOWNLOADS -> false`（回到批 111 之前） ⇒ 咬 ⑥⑦
- P7 把入队那一步搬进展示那一件（`content` 里直调队列） ⇒ 咬 ⑧⑦

## 4. 代价档与不动的东西

**中**：一颗纯件（把手该不该摆 + 题面 + 那九枚形参的映射）+ 那一屏一枚 `Button` + `when` 那一支 + 8 枚判式 + 7 笔台架 + 交付包。
不动：清单形状、页命名、`enqueue` 那颗件自己（只多一个调用点）、`app/`（受保域）、真人 `%LOCALAPPDATA%\venera`。

## 5. 落地账（一）：判式与两处改判

判式 **12 枚**全绿（§2 预算的是 8 枚，多的 ⑨⑩⑪⑫ 四枚全归「取证种子 `--repair=`」那半截 ——
屏上那两枚把手不许真点鼠标，所以必须有一枚种子能替用户按下去，而它要从装配根一路递到那一屏，
于是多出「命中/落空各念一句」「与把手同一条入队路」「递到那一屏」「三档各归各」四枚），
那颗类现数 12 条。实现落在
新建的 `gallery/data/DesktopComicShelfRepair.kt`（现数 77 行：`DesktopComicRepairArgs` + 五颗纯件 + 那一跳 + 两句种子话）
与 `DesktopComicDownloadsPane.kt`（容器多一个 `onRepair`、展示那一件多一枚 `Button`）、`ForensicFlags.kt` / `VeneraDesktop.kt`（那颗种子）。
`desktop/src/main` 122 → **123 颗 / 25,992 → 26,143 行**；`test` 源码 33,789 → **34,034 行 / 176 → 177 颗文件**（`wc -l` 口径，与批 110 那一跑同法）。

⚠️ **两处旧判式被这一批合法改宽，各有各的理由**（不写清就会被下一批当成"漂移"回滚）：

1. `DesktopComicQueueRowPumpTest` ②「其余七档不许被那枚实参顺手改宽」→ **六档**：
   `DOWNLOADS` 从前在恒假那一组，现在与 `QUEUE` 同组吃 `hasQueueWork`。它的枚数期望（`off` 四屏 → 三屏）
   与 `queueWorkScreens` 名单一起改，并在名单上留一句"这一档的判式搬到 `DesktopComicShelfRepairTest` ⑥⑦"——
   **搬走不等于丢掉**（那一屏仍被钉，只是钉它的是新那把）。
2. 两把"数入队把手枚数"的对照组（`DesktopComicQueueScreenTest` 顶栏牌子那颗 + `DesktopStaleClaimsTest`
   「章节下载那句不再说缺」）从 `1` 抬到 **`2`**：入队那一路本来只有详情长按那一枚，今天多了「补齐」。
   这两枚钉的是"没人入队 ⇒ 屏上那句是假话"与"队列那一屏自己不开第三条路"，**抬数不放宽**：
   第三条路一出现就会红（2 → 3）。

另两处小改（都属于"屏上说法跟着实现走"）：展示那一件的形参表（`DesktopComicDownloadsScreenTest` ④ 那把
`code.contains(整串签名)` 跟着补 `onRepair`），以及那一屏的空态提示 —— 原文「每一话右边那颗把手把它从本机删掉」
在两枚把手之后就成了假话，改成同时说清两枚各办什么。

## 6. 落地账（二）：台架逐笔归账

`sh _qa/perturb_driver.sh b111 9` —— **9 笔全咬**（`_qa/b111_perturb.summary.txt` 2026-10-15 02:52 那一遍，
用的是 §10 修好的量具 + §10 换过形的夹具）。收尾全量 `suites=169 tests_sum=1465 testcase=1465 @Test=1465 files=177 failures=2`
（那两枚红都在读 `app/` 的对照尺子上，属并行的另一位未提交的改动，本批一颗 `app/` 文件都没写过；`git status --porcelain app/` 现数 76 条）。

| 笔 | 改坏的是 | 咬住的判式 | 红成哪一句 |
| --- | --- | --- | --- |
| P1 | `desktopComicRepairShouldShow` 恒真（齐了也摆把手） | ① 不齐那一档才摆补齐把手 | 「已下全那一档不许摆」 |
| P2 | 把手题面改成「重下整话 · 共 30 页」 | ② 把手那句念的是补缺页而不是重下整话 | `expected:<[补齐这一话 · 还缺 2] 页> but was:<[重下整话 · 共 30] 页>` |
| P3 | 入队时把本子号递进话号那一格 | ③ 行里那五格原样进队列而两枚号不许并、⑤ 补齐那一跳不删盘上任何一颗文件（2 条） | 「话号（清单里那一格，不是本子号）」+「排的就是这一话」 |
| P4 | 排进去的那一笔标成占位 | ③ 行里那五格原样进队列而两枚号不许并 | 「号都是从盘上真目录读回来的，不是占位」 |
| P5 | 入队之前先调删除那一件 | ⑤ 补齐那一跳不删盘上任何一颗文件 | 「按完那一格盘上还剩几颗（0 颗＝整颗目录被删了）： expected:\<29\> but was:\<0\>」 |
| P6 | `DOWNLOADS` 那一支回到恒假 | ⑥ 本机已下载那一屏也驱动泵、⑦ 那一支的字面必须是hasQueueWork（2 条） | 「表里有活时那一屏要引擎」+「DOWNLOADS 那一支要钉整串（只查标识符会放过 \`DOWNLOADS -> false\`）」 |
| P7 | 把入队那一步搬进展示那一件 | ⑧ 展示那一件不直调队列 | 「展示那一件里不许直调队列： expected:\<0\> but was:\<1\>」 |
| P8 | 种子落空那一句丢掉「不排队」 | ⑨ 种子命中与落空两档各念一句 | 「落空那一句要点名不排队：…本机没有这一话」 |
| P9 | 装配那一跳没把种子递下去 | ⑪ 种子从装配根一路递到那一屏 | 「pane 那一层要把 config 里那枚递下去： expected:\<1\> but was:\<0\>」（同笔另红在批 97 那把 `DesktopComicDownloadsScreenTest :: 那一档接到那一屏`） |

P5 那一笔另有一段自己的账，写在 §10（它连绿两遍，抓出的是**量具**与**夹具**两层洞，不是这把尺子不咬）。

## 7. 盘上那一格「号」装的是净化后的形 —— 结构差是真的，而我给它写的那句行为结论被自己的读数推翻了

定档时假定「行模型里的 `comicId` 就是可以再排一次的那枚号」。回读代码后确认**两形确实不同轨**，
四条都是代码里的原话：

- 读侧：`DesktopComicDownloadedList.kt:99` —— `val comicId = comicDir.name`，行模型那枚号**取自盘上目录名**；
- 写侧：`DesktopComicDownloadQueue.kt:93-98` —— 目录名由 `desktopComicSafePathKey(comicId)` 造，
  而同一颗 task 自己的 `comicId` 格存的是**原号**（`:324` 直接递形参），两份并不同轨；
- 净化规则：`DesktopComicChapterDownload.kt:51` —— 把 `/ \ : * ? " < > |` 替成 `_`，不去首尾空白；
- 清单救不了：`DesktopComicChapterDownload.kt:24` —— `chapter.json` 字段表是 `chapterId` / `title` /
  `order` / `expectedPages` / `complete`，**没有 comicId 那一格**，所以盘上没有第二处能读出原号。

**⚠️ 本批先按推断写过一句「slug 号源的补齐递的是错号 ⇒ 那一趟必然失败」，§9 的包内读数把它推翻了**
（写下到推翻不到四十分钟，留档是为了记住这一族的成因）：goda 那一支取章节**根本不看 `comicId`** ——
`app/src/main/assets/sources/goda.js:466-471` 的 `loadEp(comicId, epId)` 把 `epId.split("@")` 的两截
直接拼成 `/chapter/getinfo?m=…&c=…`。所以用盘上那一形排回去，goda 照样把缺的两页补回来了（§9 的 B 趟）。

于是"两形不同轨到底伤在哪"要改成**逐源量**而不是按号形分档：

| 那一格被谁吃 | 现状 | 证据 |
| --- | --- | --- |
| 取章节 `loadEp` 用 `comicId` 拼 URL 的那些源 | 会取错 —— **今天没有读数**，34 份随包脚本一份一问才知道有几份 | 反例：goda 不吃这格（`goda.js:466-471`），所以 B 趟能补 |
| `--repair=` 的种子命中判据（比的是行模型那格＝盘上形） | 拿站点形打种子会落到「本机没有这一话」 | §9 的 A 趟（念得出话，不静默） |
| 详情/收藏/历史那些**不走盘**的路径 | 与本批无关（它们手里的号本来就是站点形） | 行模型只在「本机已下载」这一棵树上 |

## 8. 批 112 题面（本批只立案，不动手）

按 §7 改判后的范围重立，三条都可量：

1. **逐源把 `loadEp` / `loadComicInfo` 有没有吃 `comicId` 问一遍**（34 份随包脚本，一份一问，落一张表）；
   只有那些吃的源需要回收原号，不吃的源摆把手就是对的 —— 别拿 goda 一支的实测外推全表。
2. 需要回收的那几档，盘上唯一还留着原号的地方是队列表：`download_tasks.json` 每行带 `comicId`（原号）与
   `directoryPath`（内含净化后的两层）。回收路是**反查** `desktopComicSafePathKey(候选原号) == 目录名`；
   查不到就**明说查不到**（把手换成一句「这台机器上没有这号的原始记录」），不排错号。
   对岸下载的那几本天然没有队列记录 ⇒ 落这一档，这是数据面天花板不是缺陷。
3. 种子那一跳跟着改：命中判据两边都走同一次净化，别再要求打者知道盘上形（§9 的 A 趟就是这一格的症状）。

**第 1 条的起手读数**（2026-10-15 现数 `app/src/main/assets/sources/*.js`，逐行取证而不是按缩进切函数体 ——
第一版那把按 `\n    },` 切体的探针给出过 "0/34" 这种显然假的空结果）：34 份随包脚本**全部**提到 `comicId`，
其中 **20 份**把它拼进 URL（`baozi ccc comic_walker copy_manga copy_manga_multi_accounts ehentai gelbooru happy
hot_manga ikmmh jm komiic lanraragi manhuagui manhuaren mxs nhentai picacg wnacg zaimanhua`），
**goda 不在其列** —— 与 §9 的 B 趟补得回来对上。但"拼进 URL"不等于"会取错"：还要问第二层，
**这一枚号本身含不含 `/ \ : * ? " < > |` 那族字符**（数值号源净化前后同一串 ⇒ 无碍）。
现量到必受影响的两档：`ehentai`（`Network.get(\`${comicId}?hc=1\`)` —— 那枚号本身就是整条 URL）、
`ikmmh`（`Network.get(comicId, …)` 同理）。剩下十八档要一份一份问它列表里交回的号是什么形，才是批 112 的正题。

## 9. 包内现量（两趟，都在试用包自己的 `trial-data` 里，不碰真人 `%LOCALAPPDATA%\venera`）

跑的是**双击那一路**（`cmd //c` 起包内 `try-shelf-repair.bat`，不是命令行手打参数）；夹具先把
`0001.jpg` / `0002.jpg` 换成两枚 2,9xx 字节的过盾页等价文本（名字对、个头够，只有头部说实话），
再清空队列表，然后按两形各起一趟。读数取自 `_qa/b111_pkg_run.log`（2026-10-15 03:03–03:05）。

**A 趟 —— 种子打站点形 `--repair=%%2Fmanga%%2Fpandajunheleisajun-hiyaku/19777@1253812`（起窗第 5 秒就有读数）**

```
D_本机下载 本=1 话=1 齐=0 缺=1 跳=0 字节=2655958
D_补齐种子 号=/manga/pandajunheleisajun-hiyaku 话=19777@1253812 本机没有这一话 ⇒ 不排队（盘上有什么去看「本机已下载」那一屏）
```

那一行命中判据吃的是行模型那格（盘上形），站点形对不上 ⇒ **一句念得出话的落空**，而不是静默，
也不是把错号排进队。

**B 趟 —— 种子打盘上形 `--repair=_manga_pandajunheleisajun-hiyaku/19777@1253812`（起窗第 0 秒命中）**

```
D_补齐种子 号=_manga_pandajunheleisajun-hiyaku 话=19777@1253812 缺=2 留=28 排队=是
D_队列 笔=1 跑=0 待=1 停=0 败=0 完=0 留档=无
D_下载进度 号=_manga_pandajunheleisajun-hiyaku 话=0 页=28/30
D_下载进度 号=_manga_pandajunheleisajun-hiyaku 话=0 页=29/30
D_下载进度 号=_manga_pandajunheleisajun-hiyaku 话=0 页=30/30
D_下载 号=_manga_pandajunheleisajun-hiyaku 话=0 页=2/30 全=是
D_队列 笔=1 跑=0 待=0 停=0 败=0 完=1 留档=无
```

盘上回读：`0001.jpg` = 94,506 字节、`0002.jpg` = 51,128 字节，头部 `R I F F " q 001 \0 W E B P`
—— 两枚都从"过盾页等价文本"变回真图，而 `败=0 完=1` 是**队列自己数的**而不是屏上一句愿话。
**这一趟同时证了两件事**：把手真能把缺的页补上（批 111 的题面），而 goda 这一支不吃 `comicId`（§7 的改判）。

⚠️ 两件事要说在前面：① B 趟补齐时那句 `D_下载 … 全=是` 里"号"念的是**盘上形**，读日志的人别把它当站点形去搜；
② 跑完包里的 `download_tasks.json` 留着那一笔 `完=1`，再点把手会按去重那条把已排完的那一笔重排（对岸同一形）。

**残留与收窗**：`_qa/b111_pkg_run.sh` 的 `taskkill` 只按 `venera-desktop-probe` 这一枚镜像名关（绝不动 `java.exe`，
并行那位在建库）；中途量到一次 `关掉之后进程数：2`（A/B 两趟重叠），最后一趟收尾后现数 **0**。
同批还量到自己那份跑法脚本第 73 行引号不平衡（`unexpected EOF while looking for matching '"'`），
它把 B 段"再读一次"那一步打断了 —— 上面那串终局读数是从 `run-b.log` 直接补读的，不是脚本念的。

## 10. 量具自审：P5 连绿两遍，抓到两层洞

P5 那笔写的是「补齐那一跳顺手清盘」（入队之前先调 `desktopComicDeleteDownloadedChapter`）。它连绿两遍，
而每一遍的"绿"都不是同一件事：

**第一层 —— 台架判 RED 那一行只看得到断言失败，看不到抛异常。**
`_qa/*_perturb.js` 的 report 步骤写成 `m[0].match(/<failure message="…)"/)`，而 JUnit 的 XML 把
断言失败写成 `<failure>`、把抛出的异常写成 `<error>`。于是 **NPE / 缺货 / 越界这一族失败在台账里恒等于"没咬住"**。
现数：`_qa/*_perturb.js` 共 **58** 颗，其中读 XML 的 **50** 颗全部带这个洞（`b69`–`b74` 那 6 颗是
`[^"]{0,120}` 那一形，同样只认 failure）。已统一改成 `<(?:failure|error) message="…"`，并补一颗闸门
`_qa/check_rig_error_tag.js`：自审时塞进一颗只认 failure 的假台架 ⇒ `BAD …/有 1 颗…/EXIT=1`，撤掉 ⇒
`ok …读了 XML 的共 50 颗 / 扫了 58 颗 / EXIT=0`。**这条改判会回头影响旧台账的措辞**：以前记的"某笔没咬住 ⇒
改尺子"里，可能有一批其实是笔把代码改崩了而台账看不见。

**第二层 —— 本批夹具把行模型那格递成了站点形。**
`chapter()` 原先写 `comicId = "/manga/panda"`，而生产里那一格是**读目录名读回来的**盘上形
（`DesktopComicDownloadedList.kt:99` ⇒ `_manga_panda`）。差这一形，删除那一件的"认得出这一目录"
（`DesktopComicChapterDelete.kt:76-80`）就恒假 ⇒ 它返回 `Rejected` 而**一颗文件都没删** —— 笔自己不成立，
判式当然绿。补齐清单（删除路要吃 `chapter.json`）并把那格换成盘上形之后，P5 第一遍咬住、红成
`java.lang.NullPointerException`（目录整颗没了 ⇒ `listFiles()` 回 null 被 `!!` 接住）。红是红了，但话说不出来，
所以判式再改一次：先 `listFiles() ?: emptyArray()`，再补一句
`assertEquals("按完那一格盘上还剩几颗（0 颗＝整颗目录被删了）：", before.size, after.size)`，逐字节那把留在后面。

四遍账（同一支笔）：旧量具 + 旧夹具 = 绿；新量具 + 旧夹具 = 绿（笔本身没删任何东西）；
新量具 + 盘上形夹具 = 咬，红成 NPE；再加"念得出话"那一改 = 咬，红成一句人话。
判式数不变，仍是 **12 颗**。

⚠️ 顺带一条与本批实现无关的事实：**批 98 那枚删除把手对 slug 号源也走同一道"认得出"的闸** ——
屏上递的是行模型那格（盘上形），所以生产里认得出、删得掉；只有把号写成站点形才 `Rejected`。
这条不是缺陷，但它和 §7 是同一个根（两形不同轨）的两张脸，批 112 一并收。
