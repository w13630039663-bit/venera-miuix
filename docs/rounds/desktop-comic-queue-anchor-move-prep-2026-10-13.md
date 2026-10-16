# 批 104 · 把"搬家时会断"的那三把尺子先改成搬不烂的（2026-10-13）

这是批 101（下载队列）的第一格，**纯测试侧、`desktop/src/main` 一字未动**。
题面来自方案文档 `desktop-comic-download-queue-2026-10-13.md` §10 那次复量：
把那颗整话下载的 runner 抽成共用件时，会断的不是"那一颗判式"而是**四把尺子、三颗测试文件**，
其中两把拿同一串字面量当**对外文档那句话的证据**。先把它们改成"调用点搬到哪都成立"的形状，
批 101 开工时就不必在同批里做证据重定向 —— 那一格是本仓最容易出现"说明还在而证据没了"的地方。

## 0. 改动表

| 件 | 改了什么 |
|---|---|
| `gallery/ui/DesktopComicDetailWiringTest.kt` | ⑪ 里**新增一枚反向引用判式并单独成一颗 `@Test`**（⑪b）：跨全 main 扫 `desktopComicDownloadChapter(\s*dir\s*,\s*([A-Za-z0-9_.]+)\s*,\s*\1\s*,` —— 第二枚与第三枚实参是**同一个表达式**就红。不依赖任何变量名，抽件后仍成立 |
| `gallery/ui/DesktopStaleClaimsTest.kt` | `章节下载那句不再说缺` 那两枚对照组从"**详情那颗文件**含有这一串"改成**跨全 main 数次数恰一处**（`desktopComicDownloadChapter(dir,` 与 `desktopComicChapterDir(downloadsRoot(),`） |
| `DesktopReadmeStaleClaimsTest.kt` | 同一格同源的那枚，同样改成跨全 main 数次数恰一处 |
| `desktop/src/main` | **0 行**（本批不动产品代码，所以交付包不需要重打，理由见 §4） |

`app/` 0 行改动（受保域）。

## 1. 为什么"钉文件名 + 钉字面量"这两把会在抽件时断

⑪ 那两枚锚点钉的是 `DesktopComicDetail.kt` 里那一整串实参（`desktopComicDownloadChapter(dir, target.comicId, episode.id, index,`）。
它的**题面**至今成立且很硬（两枚 id 递错哪一枚都不编译失败、纯函数单测也不红，只有钉整串才看得见 —— 批 97 那条真缺陷就是这么抓到的）。
但抽件之后：调用点搬到共用件、形参改名（`comicId` / `chapterId`），那一串字面必然不存在 ⇒
**尺子红，而产品没错**。届时改判的人面对的是"这枚锚点到底该钉什么"，
而最容易的处置是**把这一枚删掉**（因为新写法的形状不显然），那一族的缺陷就从此没人管 ——
这是"判据先于产品变脏"的形状：产品没错而尺子红了，红得太像"尺子过时"，于是被顺手撤掉。

所以本批不是"放松锚点"，是**加一枚与位置无关的等价钉法**并让它先绿：
反向引用那枚抓的正是"两枚实参是同一个表达式"这一形，而这一形就是那条真缺陷的**根**（两枚 id 装错层）。

## 2. 判式（新增 1 颗，`DesktopComicDetailWiringTest` 13 → 14）

- **⑪b `调用里两枚实参不许是同一个表达式`** —— 逐颗 main 源文件用 `Regex` 扫 `codeText`，命中即点名那颗文件名。
  ⚠️ **这一枚必须单独成一颗 `@Test`**：先前把它寄在 ⑪ 里面，台架 Q2 跑出来红的是 ⑪ **开头那条字面锚点**，
  这一枚**压根没跑到**（同一条 `@Test` 里的断言是短路的：第一枚一红，后面的就只是装饰）。
  ⇒ 归因只能按"哪一颗 `@Test` 红"来分，不能按"哪一枚断言写了什么"来分。这是本批最值钱的一条自我打脸，
  已写进台账纪律（见 §5）。
- 两枚跨 main 次数判据：`== 1` 而不是"含不含"—— 零处 = 那句"还缺"又成实话（该把说明放回来并连判据一起撤）；
  两处 = **第二条落盘路**（批 96 那条纪律：队列与长按必须走同一颗 runner）。
  ⚠️ 数次数用的是 `codeOccurrences`（跳注释），而那颗**声明**写的是 `desktopComicDownloadChapter(` 换行再接 `dir: File` ⇒
  不含 `(dir,` 那一串，不会被数进调用里（这一格是现量核过的，不是假设）。

## 3. 台架

`_qa/b104_perturb.js` + `sh _qa/perturb_driver.sh b104 4`，终账（`_qa/b104_ledgerC.out`）：

| 笔 | 改坏了什么 | 红几把 | 归账 |
|---|---|---|---|
| Q1 | 两枚 id 都递本子号（`target.comicId, target.comicId`） | 2 | ⑪（字面锚点）+ ⑪b（反向引用）—— 同一种缺陷被两枚钉住，正是想要的冗余 |
| Q2 | **两枚 id 都递话号**（`episode.id, episode.id`） | 2 | ⑪ + ⑪b —— **⑪b 独立成立的那一笔**：这一形旧那枚字面锚点根本不认，是它把住的 |
| Q3 | 第一枚改成命名实参 `dir = dir`（token 那串不再出现） | 3 | `DesktopStaleClaimsTest` + `DesktopReadmeStaleClaimsTest` + ⑪ —— 两把跨 main 次数判据都咬住了"调用点看不见"这一档 |
| Q4 | 等价改写要求不红（页数那枚实参加 `.toInt()`） | 0 | **正确静**（`BAD 这一笔…要求不红，而它确实一笔没红 —— 这是正确输出`）⇒ 锚点没把 incidental 写法一起冻住 |

⚠️ 同一颗台架跑过**三遍**，前两遍都不能记账：
- 第一遍（`_qa/b104_ledger.out`）：⑪b 还寄在 ⑪ 里面 ⇒ Q2 的红归因不到它头上（见 §2 那条自我打脸）。
- 第二遍（`_qa/b104_ledgerB.out`）：**四笔全 `BUILD_FAILED`** —— 拆 `@Test` 时我多留了一颗 `}`，
  编译面就红。按台账纪律那**一笔都不算咬住**（是量具自己坏了），而它的直接后果是
  `report` 里 `readdirSync(RESULTS)` 撞 `ENOENT` 抛栈、驱动整遍中断 —— ⚠️ 记一条新的：
  **编译面全红的台架会把量具自己炸在 `report` 那一跳，看到 `node:fs` 而不是 RED 时先怀疑笔而不是尺子**。
- 第三遍才是上面这份终账；`BUILD_FAILED` 0 笔、`REVERT_FAILED` 0 笔，还原后全量 `EXIT=0`。

## 4. 交付包：本批**不需要**重打，而这件事有读数而不是推理

测试源集不进 classpath ⇒ 产品字节不变。但台架逐笔"改坏再逐字还原"会把 `DesktopComicDetail.kt` 的
**mtime 推到打包之后**（现量：main 里比 07:40 更新的有 1 颗），于是"`check_pkg_jar_sha.js` 念对得上"这一句
看上去像漏检。决定性读数是让 Gradle 自己回答：`sh gradlew :desktop:jar --offline` 念
**:desktop:compileKotlin UP-TO-DATE** —— Gradle 的 up-to-date 判定吃的是**输入内容哈希而不是 mtime**，
它说 UP-TO-DATE 就等于"从打包那一刻到现在，main 的字节一颗没变"。
⇒ 记一条量具形状：**"mtime 变了"与"内容变了"是两格，能拿构建系统的内容判定来分岔时别用时间戳推断**。

包读数复跑：`_qa/desktop-exe-2026-10-13-b103/` 两颗 jar 仍「对得上」。

## 5. 数字与后续

- 全量：`suites=161 tests_sum=1382 testcase=1382 @Test=1382 files=168 failures=0`（台架收尾那一跑与全量各一次，逐字同）。
- 三方口径：「三方对得上」；判据目录闸：见 §5b（本批 1381 → 1382，`DesktopComicDetailWiringTest` 13 → 14，类数不变 161）。
- `desktop/src/main` **119 颗 / 24,970 行 —— 与批 103 收尾那一跑逐字同（本批一字未动）**；`desktop/src/test` **168 颗 / 32,058 行**（上一版 168 颗 / 32,031 行 ⇒ 颗数不变、多 27 行，全在三颗测试文件里）。
- 台账纪律新增两条（写进用户那份 `feedback-perturbation-ledger-hygiene`）：
  ⑪ **同一条 `@Test` 里的断言是短路的** ⇒ 新增的"另一枚形状"必须**单独成一颗判式**，否则台架归因会把它的功劳记到前面那枚头上；
  ⑫ **编译面全红的那一遍，`report` 会自己抛栈**（结果目录不存在）⇒ 看到 `node:fs` 中断先查笔的形状（多余的 `}`、悬空的实参）。
- **批 101 剩余的格**：`DesktopComicDownloadSeedTest` ⑤ 那枚 `occurrencesAcrossMain("runChapterDownload") == 3`
  本来就跨 main，抽件时**枚数会变**（那颗 lambda 的声明搬家），届时要重数一次而不是放松成"至少一处"；
  ⑪ 那两枚字面锚点在抽件之后可以**直接删**（⑪b 已经接管同一件事，且更硬）。这两条都写在方案 §10/§11。

## 5b. 现量数字（收尾那一跑之后补）

- `node _qa/three_way_counts.js` → `suites=161 tests_sum=1382 testcase=1382 @Test=1382 files=168 failures=0`、「三方对得上」。
- `node _qa/check_ruler_catalog.js` → 见下面这一格（本批把 `DesktopComicDetailWiringTest` 从 13 改成 14、表合计改成 1382，手册 §10 同步）。
- **受保域 `app/` 0 行改动**（现量）：`find app/src -name '*.kt' -newermt '2026-10-13 05:00'` = **0 颗**；`git status --porcelain app/` = **66 条**（与批 102/103 同数）。
- 全仓未提交 **149 条**（`desktop/` 54 + `docs/` 23 + `app/` 66 + 其余）；`docs/rounds/*.md` **155 颗**（本批添第 155 颗）。
- 交付包：**不重打**，决定性读数见 §4（`:desktop:compileKotlin UP-TO-DATE` + `check_pkg_jar_sha.js` 两颗仍「对得上」）。
