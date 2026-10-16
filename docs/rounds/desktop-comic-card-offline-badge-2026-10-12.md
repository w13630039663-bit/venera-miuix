# 批 99 · 卡上那一枚「已下载 / 离线不全」（2026-10-12）

档② 的第一格。题面来自批 97 收口时登记、批 98 收尾时确认欠着的那一格（排期 §4「批 99（建议第一件）」），
外加同一帧图证里量到的那一眼观感（`第 2 话 第2話` 两截挨着念，登记为观感而非缺陷，随本批一起定）。

## 0. 改动表

| 层 | 文件 | 动什么 |
|---|---|---|
| 数据 | `gallery/data/DesktopComicCardOffline.kt`（**新建**） | 两档算式 `Downloaded(chapters)` / `Partial(complete, total)`、那枚**「站 + 号」**的键、屏上标签两句、取证那句 `D_卡标 站= 卡= 全齐= 不全=`。**只吃 `chapter.complete` 那一格**，一页都不自己数。**下半再补两格**：`desktopComicOfflineHits` 与 `D_卡标命中 站= 墙= 贴上= 盘上= 样例=` |
| 展示 | `gallery/ui/DesktopComicOfflineMark.kt`（**新建**） | `rememberDesktopComicOfflineBadges(dataDir, sourceKey)`（唯一一处 `withContext(Dispatchers.IO)` + 读数打印）与 `ComicOfflineMark`（压在封面右下角，**不可点**） |
| 展示 | `gallery/ui/DesktopComicPane.kt` | `ComicExplorePane` 取那张表 → `ComicSection` 多两枚必填形参（`sourceKey` / `offline`）→ `ComicCard` 多一枚 `offline: DesktopComicOfflineBadge?` → 卡上摆那一枚。**那笔高度账一字未动**。下半再添一颗 `LaunchedEffect(offline, state)`：把**墙上那排卡的号**递给命中那句 |
| 文案 | `gallery/ui/DesktopComicDownloadsPane.kt` | 行里那句的题名从裸串换成 `「…」`（`第 2 话 第2話` → `第 2 话「第2話」`） |
| 对外 | `README.md` / `README.en.md` 第 253 行那一格 | 「还没接的是…那两档行内标记」当场改判（中英两份一起），并新立一把机械尺子防回流 |

**`app/` 零改动**（受保域）。

## 1. 为什么这一格的价值全在"不写第四种口径"

那条线上一共存着三把"齐不齐"：

| 谁 | 吃什么 | 出处 |
|---|---|---|
| 对岸下载层 | `expectedPages` + 清单 `complete` + `listFiles{}` 的**颗数** | `app/…/download/DownloadManager.kt:307-318` |
| 对岸本地书架 | 只看有没有图（`images.isNotEmpty()`） | `app/…/download/LocalComicManager.kt:71`、`:139` |
| 桌面（批 96 立、批 97 沿用） | 清单 `complete:true` **且**逐页查盘且非 0 字节 | `gallery/data/DesktopComicChapterDownload.kt` 那颗判 |

卡上这一枚吃的是**第三把已经算好的那一格**（`chapter.complete`，由批 97 的读侧交回来），所以：
- 卡上说的"离线不全"与「本机已下载」那一屏说的"缺 N 页"**必然同源**；
- 台架 N10（那颗件自己数页）一落下去就红两把（判据 ④ 的负向锚点 + 判据 ② 的期望值）—— 这一族的锁在这。

## 2. 判式（每条都点名"改哪一行会红"）

数据层 `DesktopComicOfflineBadgeTest.kt`（7 颗）：
1. 每一话都齐 ⇒ `Downloaded(chapters=2)`（话数数得着，不是恒一句"已下载"）。
2. 一齐两缺 ⇒ `Partial(complete=1, total=3)`（两枚各是各的，不许塌成一格）。
3. 一本都没有 ⇒ **空表**（给"零话"编一枚标记就是屏上多一句假话）。
4. **只吃清单那一格**：`complete=true` 而 `writtenPages=0/expectedPages=89` ⇒ 仍算齐；`complete=false` 而页对得上 ⇒ 仍算不齐（两枚值各喂一种，这是"没写第四种口径"的正证）。
5. 键是「站 + 号」：`jm/1461316` 有标记而 `goda/1461316` 不许跟着有。
6. 标签两句 `assertEquals` **逐字**（不写"两两不同"那种被数值顶开的判式 —— 批 98 那一课），且都不带 `D_`、不带反引号。
7. 读数那句整串钉死两档：`D_卡标 站=jm 卡=2 全齐=1 不全=1` / 空表 `卡=0 全齐=0 不全=0`。

接线层 `DesktopComicCardOfflineTest.kt`（8 颗）：
8. 那一枚在卡上摆得出来：`internal fun ComicOfflineMark(` 恰 1 处 + `ComicOfflineMark(` 在 `DesktopComicPane.kt` 恰 1 处（数出现次数，不数"存在"）。
9. 那一枚**不吃点击**：那颗文件里 `clickable` 0 处；对照钉「收藏」那枚仍不可点（防"整屏都没 clickable"这种误伤）。
10. 读盘那一跳住在另一颗文件：装载那颗里 `withContext(Dispatchers.IO)` 恰 1 处、`println(desktopComicOfflineReading(` 恰 1 处，而那一屏里 `desktopComicDownloadedChapters(` **0 处**（⚠️ 从前写的是"那一屏的 IO 跳数仍为 1"，一跑就红在**既有两跳**上 —— 引擎装载 + 收藏夹那一路，见 §6）。
11. **只复用批 97 那颗读侧**：`desktopComicDownloadedChapters(` 恰 1 处，且那颗件与数据层那件的代码行里 `chapter.json` / `listFiles` / `expectedPages ==` / `writtenPages ==` / `isFile` / `walkTopDown` 各 0 处。
12. 新形参**必填不给默认值**（`offline:` 两枚都不许带 `=`）。
13. 那笔高度账没被动：`val cardHeight = cardWidth * (4f / 3f) + 22.dp` 逐字仍在（标记只能压角，加一行就会算错行高 —— `LazyVerticalGrid` 那一族的近亲）。
14. 键只有一处出处：`desktopComicOfflineKey(` 声明恰 1 处、那一屏调用恰 1 处，且那一屏不许出现手工拼串那形。
15. 行里那两句不再挨着念：`desktopComicDownloadedRowLine` 交回的串**不含** `第 2 话 第2話` 而**含** `第 2 话「第2話」`（这条是图证量出来的，不是推断）。
16. 对外文档：`DesktopReadmeStaleClaimsTest` **⑥**（中英两份那句"还没接"不许回流 + 那一条必须点名"卡上"与"已下载"；对照走 `codeText` 钉那一枚真在卡上调到）。

## 2b. 下半：那句「贴上几枚」是图证逼出来的

上半收口时包内那一帧（`b99frame`，带代理 `127.0.0.1:7897`）量到的是：**墙上真出了 30 张卡**（`漫画探索 · 源 jm · 代理 …` / `3 个分区 / 72 条 · 出图 30 张`，`异常行=0`），而本机那棵树里只有 `1461316` 一本 —— **它不在这 30 张里**，所以那一枚标记一张都没贴。⇒ 像素那条路量不到"贴出来"这一格，而当时也没有任何读数答得出"贴了几张"。

这就是本仓那条老规矩的对象（**屏上每句行为断言都要答得出"哪次量的"**）：与其把"卡上摆得出来"写成一句推断，不如给它一枚读数。落下的三格：

- `desktopComicOfflineHits(badges, sourceKey, cardIds)` —— 只数**同一站**对上的号（判据 ⑦ 用 `jm`/`goda`/`nhentai` 三种源键各喂一遍）。
- `desktopComicOfflineHitReading(…) = D_卡标命中 站= 墙= 贴上= 盘上= 样例=` —— 整串钉死（判据 ⑧），且 `样例=` **只念前 5 枚**（喂 7 枚，末段必须恰为 `样例=1,2,3,4,5` 且不含 `6,7`）：读数不是日志。空墙那一档也念得出 `墙=0 贴上=0 样例=`，不抛。
- 消费点 = `ComicExplorePane` 里那颗 `LaunchedEffect(offline, state)`（判据 ⑨）。⚠️ ⑨ 第一版钉的是 `flatMap { it.comics }` 那**半串**，而装载那一段里合法地另有 `all = shown.flatMap { it.comics }`（`:437`）—— 半串会被同屏另一处合法调用喂饱（§0.1 那一族）。这一处是**落笔前按既有那一族把锚点换成整串表达式**（`ready.sections.flatMap { it.comics }.map { it.id }`），不是台架跑出绿之后才补：N19（把实参换成 `emptyList()` ⇒ 贴没贴上永远念零）一跑就咬在 ⑨ 上。
  ⚠️ 那条"半串会全绿"**本批没反证过**：弱化那一版从没跟 N19 一起跑过 ⇒ 在这里它是**推理而不是读数**（本仓对这类账的口径：标成推理，别写成量过的）。

⚠️ **本节那句"像素那条路量不到'贴出来'这一格"当场改判**：它只对"押一枚号"成立 —— 收尾那一趟改成**一次押 10 枚候选号**就量到了（`贴上=10` 并有帧），见 §4b。

## 3. 台架

`_qa/b99_perturb.js` + `sh _qa/perturb_driver.sh b99 16`（逐笔归账把 `BUILD_FAILED` 与 `RED` 分开数）。

**终账：19 笔 = 18 笔咬住 + 1 笔正确静（N16）**，`BAD`（该红不红）/`WRONG`（该不红却红）/`BUILD_FAILED` 各 **0 条**；每一笔收尾的"还原后的全量"都是 `EXIT=0`、`suites=158 tests_sum=1355 testcase=1355 @Test=1355 files=165 failures=0`（上半那 16 笔跑完时是 1352，下半添了 3 颗判据之后是 1355 —— 两遍都是同一组数，逐笔没漂移）。
台账日志：`_qa/b99_ledger.out`（上半 16 笔）+ `_qa/b99_ledgerB.out`（下半 N17..N19 各带一笔还原后全量）。

| 笔 | 改坏什么 | 红几把 / 红在谁 |
|---|---|---|
| N1 | 「每一话都齐」松成「有话齐就算」 | 2 把：②（一齐两缺被说成已下载）+ ⑦（读数那三格跟着变） |
| N2 | 键从「站+号」退化成只认号 | 1 把：⑤ 同号异站不串 |
| N3 | 齐的话数虚报（`list.count{…}` → `list.size`） | 3 把：②④⑦（三档各看一枚数） |
| N4 | 「离线不全 1/3 话」换成笼统一句 | 1 把：⑥ 标签逐字 |
| N5 | 读数漏掉「不全」那一格 | 1 把：⑦ |
| N6 | 读数「不全」恒念零 | 1 把：⑦ |
| N7 | 那一枚改成点得动 | 1 把：⑨ 不吃点击 |
| N8 | 卡上那一枚不摆 | **5 把**：⑧ 计数 + ⑬ 高度账那枚 `Alignment.BottomEnd` + 对外文档 ⑥（对照锚点跟着塌）+ 另两把既有尺子 |
| N9 | 读盘那一跳挤进那一屏 | **3 把**：⑩ 分家 + `DesktopUnusedInternalTest` ①③（装载那颗成了零调用 internal） |
| N10 | 那颗件自己数页（第二口径回流） | 2 把：④（两枚值各喂一种正是为这一笔准备的）+ ⑪ 负向锚点 |
| N11 | 新形参给默认值 | 1 把：⑫ |
| N12 | 加一行而动那笔高度账 | 1 把：⑬ |
| N13 | 手工拼键 | 1 把：⑭（`键只有一处出处`） |
| N14 | 题名的「」撤掉 | 1 把：⑮ |
| N15 | 对外文档假话回流 | 1 把：README 尺子 ⑥ |
| N16 | **等价改写要求不红**（交回前先拷一份 `HashMap(loaded)`） | 0 把 ⇒ 正确输出 |
| N17 | 命中数只看号不看站（goda 的卡被 jm 的树贴上） | 2 把：⑦（`jm` 那一档念 0）+ ⑧（`贴上=` 跟着塌） |
| N18 | `样例=` 把整墙念出来 | 1 把：⑧（`样例=1,2,3,4,5,6,7` 被"末段恰为五枚"与"不含 `6,7`"两句拦住） |
| N19 | 命中那句恒交回空墙（贴没贴上永远念零） | 1 把：⑨ 整串表达式（另两半——调用计数与"不进界面"——**都不咬**，见 §2b 末段） |

最值钱的两条读数：**N8 一笔红五把**（"卡上那一枚不摆"同时塌掉计数、高度、对外文档三族锚点 —— 说明这一格被钉得不止一处）与 **N9 一笔红三把**（把读盘挤进那一屏，被"分家"那把抓到之后，**另一把既有尺子 `DesktopUnusedInternalTest` 又抓到那颗装载件成了零调用 internal** —— 本仓"零调用点"那族尺子第一次在**扰动笔**身上应验，不是缺陷而是它证明那把尺子活着）。


## 4. 包内

量具 `_qa/b99_pkg_card.sh`（四趟）+ `_qa/b99_pkg_shotloop.sh` 配 `_qa/b99_make_stub.js`（图证那一趟）。
判式仍是那两条：**读数与盘上两栏对照**（盘上那一栏在**进程停了之后**才数，批 98 那一课）+ **收尾同名进程现数 = 0**。

| 趟 | 盘上（进程停之后） | `D_卡标` | `D_卡标命中` | 异常行 | 残留 |
|---|---|---|---|---|---|
| `b99_frame`（上半收口，带代理 `127.0.0.1:7897`，真树 `1461316`） | 话目录=1 页=89 清单=1 字节=24,905,629 | `站=jm 卡=1 全齐=1 不全=0` | ——（那趟还没有这一句） | 0 | 0 |
| `b99b_zero`（隔离目录空着） | 全 0 | `卡=0 全齐=0 不全=0` | `墙=72 贴上=0 盘上=0` | 0 | 0 |
| `b99b_plant`（真树 `1461316`） | 话目录=1 页=89 字节=24,905,629 | `卡=1 全齐=1 不全=0` | `墙=72 贴上=0 盘上=1` | 0 | 0 |
| `b99b_hit`（`plant2 1446451`，号从上一趟 `样例=` 现取） | 同上 | `卡=1 全齐=1 不全=0` | **`墙=72 贴上=1 盘上=1`** | 0 | 0 |
| `b99b_shot`（同一棵桩再跑一趟） | 同上 | `卡=1 全齐=1 不全=0` | `墙=72 贴上=0 样例=1462585,1452804,…`（与上一趟那串全不相干） | 0 | ——（那一趟的 `.out` 被会话截断，只有 `.utf8.log` 的尾部；残留改由**现查** `Get-Process` = 0） |
| `b99_shot1`（图证：一次押 10 枚候选号） | 901 颗文件 / 1.1 MB（桩） | `卡=10 全齐=10 不全=0` | **`墙=72 贴上=10 盘上=10`**，第 12s 念到 | 0 | 0（捕获后 `Stop-Process`，现数 = 0） |

**图证那一帧**（`get_window_state`，窗口 `#7240`，`bounds 1440×900` → 像素 `1226×768`）：漫画域「探索」那面墙照常出图（六列、封面与题名各就各位），
`relatedTransientWindows: []`、`compositedRelatedTransientCount: 0` ⇒ **没有 `title:"Error"` 的孤儿窗、没崩**，而 `贴上=10` 那一句是同一进程同一时刻念的 ——
这一格要防的那件事（新接的 composable 只在屏上才崩）到此有读数 + 有帧。
⚠️ **那一枚药丸上的字在这一帧里读不出来**：窗口 1440 宽被量具降到 1226（0.851 倍），而标签是 10.sp ⇒ 约 11 px 高。
所以"压在封面右下角"这一句的证据是**判式 ⑬ 钉的 `Alignment.BottomEnd`** + 这一帧"版面没被搞坏"，**不是**这帧里能认出那两个字。

### 4b. 「墙每次启动重洗」这句当场改判

上半与 §2b 从前记的是"每次启动重洗 ⇒ 押不中"。`b99_shot1` 把它否了：一次押 10 枚号，**10 枚全贴上**；
而第 5 趟与第 6 趟念出的 `样例=` 是**同一串**（第 4 趟那串则不同）。⇒ 真相是"同一参数下墙上那排号会变，但不是每趟都重洗"，
**成因未定论**（两档候选：分区装载顺序的竞态 / 列表缓存过期后重抽 —— 本仓首页那条线本来就有 2 分钟节流那一档）。
留下的可复用结论只有一条：**要押中就别押一枚、押一垛**（`_qa/b99_make_stub.js` 一字节裸页 ⇒ 押十枚号 1.1 MB，不是 250 MB）。

## 5. 数字

- 判据：**1355 条 / 158 颗类 / 0 失败**（三方口径 `suites=158 tests_sum=1355 testcase=1355 @Test=1355 files=165 failures=0`，2026-10-13 现跑）；
  对账 `node _qa/check_ruler_catalog.js` → **0 处漂移**（本批添 2 行、`DesktopReadmeStaleClaimsTest` 那行 5→6）。
- 源码：**main 119 颗 / 24,726 行**、**test 165 颗 / 31,345 行**（`find … -exec cat {} + | wc -l` 现量）。
- 本批新建 4 颗：main `DesktopComicCardOffline.kt` 73 行 + `DesktopComicOfflineMark.kt` 66 行；
  test `DesktopComicOfflineBadgeTest.kt` 166 行 + `DesktopComicCardOfflineTest.kt` 132 行。
- 台架：**19 笔 = 18 笔咬住 + 1 笔正确静（N16）**，`BAD`/`WRONG`/`BUILD_FAILED` 各 0 条。
- 交付包：`_qa/desktop-exe-2026-10-12-b99b/` **216 MB**、两颗 jar 与 `…-b99/` 逐字节同一颗、
  cfg `classpath=100 missing=0 orphan_jars=0`（`check_cfg.js` + `check_pkg_jar_sha.js` 两把一起）。
- `app/`：**本批 0 行改动**。⚠️ 现查同一棵 worktree：`git status --porcelain app/` = **66 条目**（46 M + 20 ??），
  其中 `app/src` 下 **47 颗的 mtime 落在 2026-10-13 01:52–04:11** —— 而本批 desktop 侧最后一笔写盘是 **10-12 21:21**。
  ⇒ 那 47 颗不是本批动的（本批只写 `desktop/` `docs/` `README*.md` `_qa/` 四处），但**此刻有第二双手在改受保域**，
  全仓未提交条目也从上一版记的 64 涨到 **140**（`app/` 66 + `desktop/` 51 + `docs/` 17）。这一格要报给用户，别由本会话擅自收口。

## 6. 台架与量具自己坏的那几笔

- **判式 ⑩ 第一版红在既有事实上**（写尺子的人错，不是产品错）：那条从前钉的是"那一屏的 `withContext(Dispatchers.IO)` 仍恰一处"，而 `DesktopComicPane.kt` **今天本来就有两处**（引擎装载 `:429` + 收藏夹那一路 `:666`，现量）。第一跑就红 ⇒ 改成钉"那一屏里 `desktopComicDownloadedChapters(` 0 处"（"没挤进来"讲的是那颗读侧，不是跳数）。
  ⚠️ **同一句错话还留在 `DesktopComicOfflineMark.kt` 自己的 KDoc 里**（"那一屏今天只有一处 `withContext(Dispatchers.IO)`（引擎装载）"）—— 台架跑着的时候不许动被测文件，所以它排在台架收尾之后与 N7/N16 的复跑同一趟改（改完必须复跑一次全量再钉数）。这是 §0.1 那一族"那句'还没有 X'会在同批内变假"的**反向形**：写下的**计数断言**在写下那一刻就已经是错的。
- **N3/N7/N9/N16 第一版全被自己那道"起点与终点互为子串"的闸挡下**（`check` 报 4 笔 BAD，一笔都没跑）：那四笔都是"往一行里加东西"的形状（插一行 import、`+ 1`、`badges = loaded` → `badges = loaded.toMap()`）⇒ 终点串包含起点串。正解是**换扰动形状而不放宽闸**（批 92 坑 ㉚ 那一课的第四次）：N3 换成 `list.count { it.complete }` → `list.size`、N7 换成"整行换掉"（`background` 那两行换成 `clickable`）、N9 换成**全限定名**所以不需要插 import、N16 换成 `HashMap(loaded)`（不是 `loaded` 的超串）。
- **N8 的终点串从前写成空串**：`revert` 那一路要拿终点当"命中 1 处"的起点，而空串在任意文本里处处都在（这颗 `occurrences` 直接返回 `-1` 而不是 1）⇒ 换成一行注释（`// 扰动 N8：这一枚今天不摆`），判式 ⑧ 数的仍是 `ComicOfflineMark(` 的调用次数，不受影响。
