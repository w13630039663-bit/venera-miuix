# 批 82.8 · 历史那一行的「源对不对」口径与对岸同轨（2026-10-10 前置量级）

这一份是**动工前的量级记录**，代码一行没改。目的：把"手机写的行桌面按不动"这一格从推测变成读数。

## 1. 两侧今天各自往 `comic_history.source_name` 里写什么

| 侧 | 写入点 | 写进去的是 |
|---|---|---|
| Android | `app/src/main/java/com/venera/compose/reader/VeneraReaderScreen.kt:445-458` | `session.sourceName.ifBlank { "拷贝漫画" }` —— **显示名**，且空值兜底是一枚硬编码的源名 |
| 桌面 | `desktop/src/main/kotlin/com/venera/desktop/gallery/data/DesktopComicHistory.kt:51` | `p.sourceKey.ifBlank { DESKTOP_HISTORY_SOURCE_FALLBACK }` —— **键**（如 `copymanga`） |

同一张表、同一列，两端不同轨。现成的旁证：`desktop/src/test/kotlin/com/venera/desktop/db/HistoryDaoTest.kt:108-109` 那两条夹具本身写的就是「拷贝漫画」「哔咔漫画」。

## 2. 读侧的不对称在哪一侧

| 侧 | 判"这一行是不是当前源"的口径 | 结果 |
|---|---|---|
| Android | `ComicDetailViewModel.kt:961-965` 的 `resolveSourceKey`：`it.key.equals(x, ignoreCase = true) \|\| it.name.equals(x, ignoreCase = true)` —— 键与显示名**都认** | 桌面写的那行（键）在手机侧认得 |
| 桌面 | `DesktopComicHistoryScreen.kt:72-73`：`record.sourceName == sourceKey` —— 只认键、区分大小写 | 手机写的那行（显示名）在桌面这一侧判"不是当前源"，那一行**摆着但按不动**（`DesktopComicHistoryScreen.kt:116` 的 `canOpen` 决定挂不挂 `clickable`，屏上那句「换源启动才打得开」住在 `:53`） |

⇒ 缺口只在桌面读侧这一边，不是"两端都要改"。

### 1.1 「键」在这个仓库里其实有三把（2026-10-10 12:46 现量，34 颗全量）

| 口径 | 出处 | 值 |
|---|---|---|
| **文件名去扩展** | `engine-probe/…/EngineAssets.kt:62-69`（`nameWithoutExtension`）—— 批 83 那张清单与桌面停用名单吃的就是这把 | `copy_manga` / `copy_manga_multi_accounts` / `komiic` / `manhuagui` |
| **脚本里的 `key` 字段** | 对岸 `ComicSourceParser` 读的、也是 `comic_history` 与收藏那侧解析出来的 | 与文件名**3 颗不同**：`copy_manga_multi_accounts → copy_manga`（与另一颗**撞同一枚键**）、`komiic → Komiic`、`manhuagui → ManHuaGui`（后两枚只差大小写） |
| **`comic_source` 种子里那五条** | `app/…/data/db/ComicSourceDao.kt` 的硬编码播种 | `copymanga`、`mangadex`、`picacg`、`ehentai`、`nhentai` —— 其中 `copymanga` 与 `mangadex` **随包清单里根本没有**（随包是 `copy_manga`、`manga_dex`） |

对 82.8 的影响：判定走 **ignoreCase**，那两枚只差大小写的自然吃掉；真正需要写清的是"桌面这枚键是文件名"这句话 —— 它决定了 82.8 之后仍然存在的最后一格：手机把 `copy_manga_multi_accounts` 那颗记成 `copy_manga`，两端在这一颗上会撞车（`拷贝漫画` 与 `拷贝漫画M` 两个显示名指向同一枚键）。这一条**登记，不在本批改**：改它要动对岸的键口径，那是受保域。

## 3. 修法（定案，未实施）

`desktopComicHistoryRowCanOpen` 从「键 == 键」改成与对岸同一条口径：**键或该键的显示名，两者任一 ignoreCase 命中即算当前源**。

- 显示名从哪来：批 83 已经在域层建好 `key → 显示名` 那张表（`DesktopSourceNames.displayNameOfKey`，免起引擎，34/34 命中），历史那一屏只需把这张表递进去。
- 判定形状：先把行里那串**解析回键**（遍历键与名字，ignoreCase），解析出的键等于当前源才算得动。
- ⚠️ 一处**刻意不与对岸同轨**：对岸解析不出时退到 `sources.activeSourceKey`（它的语境是"我要给这本收藏"，退到当前源是对的）；这一屏的语境是"筛出属于当前源的行"，退到当前源会把别源的行一起点亮 ⇒ 桌面解析不出就是"按不动"，且那句 `DESKTOP_HISTORY_OTHER_SOURCE_NOTE` 照旧念得出。这条差别必须写进判据的题面，不许后来者"顺手统一"。

## 4. 待办（下一批的验收口径）

1. 纯函数尺子：键命中 / 显示名命中 / 大小写不同仍命中 / 另一颗源的显示名不许命中 / 解析不出时那一档（含"名字表为空"的退化输入）。
2. 接线尺子：那张名字表**真递进了** `DesktopComicHistoryPane`，且 `canOpen` 那一处不是仍吃旧的单键比较（数出现次数，不是"有没有一处"）。
3. 离屏语义树：手机那形状的夹具（`sourceName = "拷贝漫画"`）渲染出的行**真挂了 clickable**（`performClick` 后 `onOpen` 收到载荷）。
4. 扰动台架 + 起窗拍帧（`-PstartDomain=COMIC -PstartSection=comic-history`，隔离数据根 `_qa/b828_local`，用完关窗数残留）。
5. 夹具要落进真库：桌面自己写不出显示名那一档 ⇒ 用只读打开真文件核到盘上那一条（口径见 `project-desktop-forensic-capture` 批 41 那条），或者临时以夹具直接喂展示件。
6. **改判预告（动工当天一定会红这一条）**：`DesktopComicHistoryScreenTest.kt:126-129`「异源那一行不做成点得动的样子」把调用点钉成了整串实参 `desktopComicHistoryRowCanOpen(rec, sourceKey)` —— 加第三枚入参（那颗源的显示名）会让这一计数归零而红。正解不是放宽计数，而是按手册 坑 ㉖ 那句改钉**前缀** `desktopComicHistoryRowCanOpen(`（仍数"恰好一处"），并在该行就地写明"当年钉的是那一版实参表，而那张表正是缺陷形状"。
7. 屏上那句「换源启动才打得开」（`DesktopComicHistoryScreen.kt:53`）落地后**只对真异源的行成立** ⇒ 判据要钉住"这一句只挂在解析不出、或解析出的键不等于当前源的那一支"（同一颗语义树尺子里加：拿显示名那枚夹具按出来的行**不许**带这句话）。不钉这一条，文案就会跑在行为前面（那条"文案说没有 X 而 X 已落地"的反方向）。

## 5. 落地形状（2026-10-10 13:0x 动工）

| 件 | 改了什么 |
|---|---|
| `DesktopComicHistoryScreen.kt` | `desktopComicHistoryRowCanOpen(record, sourceKey, sourceName)` 三枚入参：先挡 `record.sourceName` 为空（**两个空串不许互相命中**），再 `equals(sourceKey, ignoreCase) \|\| equals(sourceName, ignoreCase)`；`DesktopComicHistoryContent` / `DesktopComicHistoryPane` 各多一枚**必填** `sourceName`（不给默认值 —— 漏传的形状是一枚永远按不动的行）；读数多一格 `名=`；那句说明从「换源启动才打得开」改成「这一行是别的源记的，去源管理换过来才打得开」（旧那句在批 83 之后是假话：换源不必重启）；文件头那一节整段重写 |
| `DesktopComicSourcesScreen.kt` | `DesktopSourcesState.nameOf(key)`：题名在名字表没命中时会**退回键**（`DesktopSourceCatalog.rows` 就这么写的），而这一格要的是"有没有名字" ⇒ `takeIf { it != key } ?: ""` 把退回折回空串，缺位就交回空串而不是"看起来像命中的键" |
| `DesktopComicPane.kt` | 历史那一屏的调用点递 `sources.nameOf(sources.activeKey)` —— 名字取自**当前那颗折完的**（`activeKey`），不是启动时那颗 |

## 6. 判据（先 RED 后 GREEN，逐笔有账）

- **RED**（`_qa/b828_red.log`，13:0x）：编译期就红，两枚形状都在 ——
  `No parameter with name 'sourceName' found.` ×7（语义树那 7 处调用点）、
  `Too many arguments for 'fun desktopComicHistoryRowCanOpen(record: HistoryRecord, sourceKey: String): Boolean'`（纯函数那几条）。
  ⇒ 这一笔红的是"该有的形状还没有"，不是笔误。
- **GREEN**（`_qa/b828_green.log`）：`DesktopComicHistoryScreenTest` 14 条 / `DesktopComicHistoryScreenSemanticsTest` 7 条，0 失败。
- **清白基线**（`_qa/b828_baseline.log`，跑台架**之前**）：作用域三颗 = 3 suites / 29 条 / **0 失败**（14 + 7 + 8）——
  这一格是批 83 那一课的直接应用：**先证明干净树里没有恒红的尺子，再逐笔读台架的红单**，否则"每笔都红同一条"会被读成"每笔都咬住"。
- 新增 8 条纯函数 + 2 条语义树 + 1 条 `nameOf`（`DesktopComicSourcesSemanticsTest` 7→8）：见判据目录那三行。
- **改判一条**：`DesktopComicHistoryScreenTest` 里谓词计数锚点从整张实参表 `desktopComicHistoryRowCanOpen(rec, sourceKey)` 改成调用点前缀 `desktopComicHistoryRowCanOpen(rec,` —— 旧锚点会把"加一枚入参"判成"接线断了"（§0 坑 ㉖ / ㊞ 那一族；裸前缀 `(rec,` 而不是 `(rec`，免得把定义那一行也数进来）。

## 7. 台架（`_qa/b828_perturb.js`，8 笔）

`node _qa/b828_perturb.js check` = **8 笔各命中一处，可跑**。八笔的形状：
P1 显示名那一档整个不要（回到只认键）、P2 键那一档改成大小写敏感、P3 空源名那道守卫不要（两个空串互相命中）、
P4 把"有名字"当成"同一条源"、P5 那句说明退回念「启动」、P6 读数少念 `名=` 那一格、P7 域 body 不递名字（恒交空串）、
P8 `nameOf` 缺位时交回键。**跑之前先做了清白基线**（§6 那一笔 0 失败），所以下面每一笔的红单都只可能是它自己那一格 —— 批 83 那把恒红尺子的顺序。

| 笔 | 改坏的是什么 | 红单（条数）与咬住它的尺子 |
|---|---|---|
| P1 | 只认键那一档 | 2 条：语义树「手机写的那一行按得动而不念那句说明」（`expected:<1> but was:<0>`）+ 纯函数同名那条 |
| P2 | 键那一档大小写敏感 | 1 条：「两档都吃大小写不敏感」（红在**键那一档**那句） |
| P3 | 空源名那道守卫不要 | 1 条：「显示名缺位时不许把空串当通行证」（库里空源名对上空显示名被放行） |
| P4 | 有名字就算同一条源 | 3 条：「别颗源的显示名不许算成当前源」+ 语义树那两条对照组（异源那一行、名字缺位那一行都被放行） |
| P5 | 说明退回念「启动」 | 1 条：「那句说明不再说启动而点名去哪儿换」（报错里就是旧那句原文） |
| P6 | 读数少念 `名=` | 1 条：「读数那一行分得开空态与读不成」（`expected:<D_历史 屏 源=jm [名=禁漫天堂 ]行=3>`） |
| P7 | 域 body 不递名字 | 1 条：「那一屏真拿到了当前源的显示名」（接线计数 1→0） |
| P8 | `nameOf` 缺位交回键 | 1 条：「名字缺位时那一格交回空串而不是键」（`expected:<[]> but was:<[noname]>`） |

收尾那一跑（还原后全量，必须 0 红）：**`EXIT=0` / `suites=130 tests_sum=1016 testcase=1016 @Test=1016 files=137 failures=0`**，
对账尺 `node _qa/check_ruler_catalog.js` = **0 处漂移**（表行 130 = XML 类 130、表合计 1016 = XML 合计 1016）。

## 7.5 起窗两帧（隔离数据根 `_qa/b828_local`，用完关窗）

**第一帧（库里 0 行）** —— 读数：

```
D_资源 来源=包内 /sources/ 清单=34 颗
D_落点 域=漫画(启动) 行=阅读历史 浮层=关(换域或换页自动收) 查看器=关 详情=关(启动) 阅读=关(启动) 种子=无
D_源 用=jm
D_源 停用=0 清单=34 口径=桌面本地（与共享表 comic_source 刻意不同轨）
D_历史 屏 源=jm 名=禁漫天堂 行=0
```

⇒ **`名=禁漫天堂` 是这一批最要紧的一格读数**：它是 `DesktopSourcesState.nameOf(activeKey)` 在真进程里现取出来的
（离屏语义树喂的是手搭的 `rows`，证不到"名字真从脚本头部读出来了"这一跳）。屏上：pane 四行（探索 / 收藏夹 / 阅读历史 / 源管理）、
「阅读历史」高亮，内容区是空态那两句「还没有阅读记录 / 去随便翻两页吧」。

**第二帧（播了三行夹具之后）** —— `node _qa/seed_history_probe_rows.js` 写进 `venera_core.db` 的 `comic_history`，
写罢自己回读盘上三行（`probe_key_wrote|jm`、`probe_name_wrote|禁漫天堂`、`probe_other_source|goda`），复跑读数 `D_历史 屏 源=jm 名=禁漫天堂 行=3`。帧上三行老实念着（逐字读过）：

| 屏上那一行 | 源名那一格念的是 | 挂没挂那句说明 |
|---|---|---|
| 夹具丙·别源（第 1 话 - 第 4 页） | `goda · 这一行是别的源记的，去源管理换过来才打得开` | **挂了**（对照组） |
| 夹具乙·手机写法（第 3 话 - 第 12 页） | `禁漫天堂` | **没挂** ⇒ 这一行现在算当前源，本批的正题 |
| 夹具甲·桌面写法（第 2 话 - 第 8 页） | `jm` | 没挂（改前也一样） |

⇒ **同一帧里自带对照组**：丙挂而乙不挂，差别只在"那一列写的是键还是显示名"——这正是"改前乙会挂上那句说明"的反面证据，
不必再补一帧改前图（改前那一档由台架 P1 咬住：只认键的形状当场让语义树那条按不动）。
`relatedTransientWindows` 空 ⇒ 无 Error 框；两次关窗后带标题的 Venera 窗 **0** 扇（`_qa/close_venera_windows.ps1` 念 `residue=0`）。
⚠️ 帧由窗口捕获通道读到（WGC，`occlusionSafe=true`），**没有落成 PNG** —— 这台机器上 `-Pshot` 与 `CopyFromScreen` 两条落盘路都断（手册 §0 与批 32 / 62 那两笔账），所以图证以"逐字读过屏上那三行"的方式入账。


## 8. 数字与欠账

- 数字（2026-10-10 13:22 现量）：`app/` `git status --porcelain app/` = **0 行**；`desktop/src/main` **100 颗 / 20,399 行**（批 83 收尾时 20,367 ⇒ 本批 +32）；全量 **1016 / 130 / 0 失败**、目录 **0 处漂移**（两笔都在 §7 末尾）；起窗两趟、关窗残留 **0**、真库未碰（全程 `LOCALAPPDATA=_qa/b828_local`）；README 两份各一处同步（能力句里那句"历史那一屏认得手机侧记下的那一本"）。
- 交付包：本批与批 81/82/82.5/82.6/82.7/83 **合并一次重打**（`createDistributable --rerun-tasks` + `_qa/check_pkg_jar_sha.js`），跑完把包名回填到这里。
- 欠：**真库里那一行**（手机写进去的显示名）今天仍然只有离屏语义树按过 —— 桌面自己写不出显示名那一档（它写的是键），
  而真机那一跳要手机侧跑一次阅读器；已列进手册 §17 那笔用户回执（读哪一行、屏上该看到什么）。
- 欠：`copy_manga_multi_accounts` 与 `copy_manga` 撞同一枚**脚本**键那一格（§1.1）—— 改它要动对岸口径，受保域，登记不动。

