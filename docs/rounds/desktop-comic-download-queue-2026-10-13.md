# 批 101 · 桌面下载队列（方案，2026-10-13）

上一批是批 100（逐页进度 + 协作式取消，rounds `desktop-comic-download-progress-cancel-2026-10-13.md`）。
这一批的题面来自排期表里那一行「下载队列（多话排队 + 重启续排）」。

⚠️ 本文档里的对岸读数全部是 2026-10-13 现量的（路径:行号可 `awk NR` 复核），不是从批 96/97 的旧记录抄的。

## 0. 这一批要解决的那一件事

今天桌面**一次只能下一话**：详情那一屏的状态槽只容一笔（`DesktopComicDetail.kt:443` 的 `download: DesktopComicChapterDownload?`），
而 `:331` 有一枚硬闸 `if (download != null) return@LaunchedEffect` —— 按第二话会被静默吞掉。
并且那颗 runner 挂在 `rememberCoroutineScope()`（`:235`）上 ⇒ **离开那一屏就死**，跑到一半的那话既不完成也不留痕。

所以这一批要的是三条：
1. 挑多话 → **排队**（入队即回执，屏上看得见每一笔走到哪）；
2. 队列住在**应用级作用域**里（换屏、关详情都不断）；
3. **重启续排**（下次启动把上次没跑完的接着排，而不是当它没发生）。

## 1. 对岸那一层的实测范围（738 行怎么切）

`app/src/main/java/com/venera/compose/download/DownloadManager.kt` 一颗文件包了队列 + 执行 + 落盘 + 通知 + 判据。逐条处置：

| 职责 | 对岸证据 | 绑 Android？ | 桌面处置 |
|---|---|---|---|
| 入队 / 去重 / 已完成与失败重排 | `DownloadManager.kt:133-184` | 否 | **照形** |
| FIFO 调度循环（取表里第一枚 PENDING） | `:352` `firstOrNull { it.status == PENDING }` | 否 | **照形**（表序就是执行序） |
| 并发上限走偏好（**默认 2、1–16**） | `:59-62` + `data/prefs/VeneraPreferences.kt:154,663` | 是（SharedPreferences） | **弃 + 改判**：桌面**恒串行 1**，不引入新偏好键。理由见 §2③ |
| 逐页取数 + 重试 + 速度 | `:409-483,594-600` | 否 | 桌面已有那颗（批 96/100 的 `desktopComicDownloadChapter`）——**复用而不再写第二颗 runner** |
| 任务清单落盘 / 读盘（`org.json`） | `:659-725`（`saveTasksToDisk` 从 `:659` 起，15 枚 `put` 在 `:665-680`） | 是（`org.json` 由 Android 提供） | **另写**：手写 JSON，同批 96 那颗 `chapter.json` 的口径与原子写那一族 |
| 重启恢复（`DOWNLOADING` 一律降级） | `:67-72`、`:674`（**写盘时就降级**）、`:711` | 半 | **照形**（见 §2④） |
| 进度通知 / 完成通知 | `:87-128` + `DownloadNotificationChannel.kt` 整颗 | **是** | **弃** |
| `POST_NOTIFICATIONS` 运行时权限 | `DownloadNotificationChannel.kt:47-50` | 是 | **弃** |
| 前台服务 / wake lock | **全仓 0 命中**（`grep` manifest 无 `<service>`） | — | **本来就没有**，不许在文档里写"对岸有前台服务" |
| 存储根选择 + SAF `treeUri` 换算 | `ComicStorageRoot.kt:20,65-141` | **是** | **弃**：桌面固定 `数据根/downloads`（`DesktopComicChapterDownload.kt:49`） |
| 换根时改写任务路径 `relocateTasks` | `:332-346` | 是（依赖上条） | **弃** |
| 完成态判据 | `:253-318` + `ChapterCompleteness.kt:19` | 否 | **不新建**：桌面沿用批 96 那颗最硬的 `desktopComicChapterIsComplete`（见 §2②） |
| 图片魔数校验 + 坏图重下 | `:444-451,580-592` | 否 | **本批不做**，登记为批 102 候选（见 §6） |
| 仅 wifi / 手动>自动优先级 / 自动追更 | **0 命中**（`WIFI_ONLY` 只属于画廊动图，`data/prefs/VeneraPreferences.kt:318` + `gallery/domain/GalleryMotion.kt:10`）；下载的用户入口**只有一处** = `ComicDetailScreen.kt:1744`（`AndroidBusinessPorts.kt:534` 那一处是端口自己的转交，不是第二个入口） | — | **无此形**，不许自己造 |

队列数据形状（对岸 `saveTasksToDisk` 那 15 枚 `put`，`:665-680`）：`taskId` `sourceKey` `comicId` `comicTitle` `comicCover` `chapterId` `chapterTitle` `chapterOrder` `status` `downloadedPages` `totalPages` `errorMsg` `directoryPath` `createTime` `updateTime`。
`taskId` 的拼法是 `源_子_话`（`:144`）。内存态是 `MutableStateFlow<List<DownloadTask>>`（`:56-57`），每次变更**全量刷盘**（`:624` `:653` `:684`）。

## 2. 四条拍板（每条都写清为什么是这个档）

**① 队列文件叫 `download_tasks.json` 而**不**沿用对岸的字段语义。**
名字逐字沿用（本仓「命名逐字回核」那把尺子管的是名字），但字段逐枚问"装的是哪一层东西"—— 这正是批 97 那一课（同一份 `chapter.json`，对岸 `chapterId` 装**话号**而桌面曾装**本子号** ⇒ 双向认不出）：
- `directoryPath`：对岸装的是**设备绝对路径**（SAF 换算过的），桌面换成绝对路径就是"同名字段不同语义"，而两端数据根在不同设备上根本不可比 ⇒ **桌面这一格改写「相对数据根的目录名」**（与批 97 读侧「号从目录名取」同轨），并在判据里钉死它不许长成绝对路径。
- `downloadedPages`：只允许是**盘上回读出来的数**（`present` 那份），不许是"这一趟写了几页"的内存计数 —— 后者在半截续跑时是 0 而真相是 62。
- 两把尺子盯着这件事：字段清单判据（逐枚点名 + 两两不同）+ 一条负向（`directoryPath` 里不许出现盘符或 `\`）。

**② 判齐只有那一颗，队列不造第四种口径。**
全仓现有三档"齐不齐"（对岸 `ChapterCompleteness`、桌面 `desktopComicChapterIsComplete`、画廊那一格），桌面这一侧只用最硬那一把（清单说齐 **且** 每一页都在盘上且非零）。
队列条目的 `status` 里 `DONE` 那一档**必须是回读盘之后的结论**而不是"runner 交回了 `Completed`"—— 半截取消正是 `Completed` 都不成立而 `Cancelled` 的那一族（批 100 §1）。

**③ 桌面恒串行 1，且不引入并发偏好键。**
三条理由：(a) 引擎那颗 GraalJS `Context` 单线程（批 81 实测：同一把锁下 `maxLive` 必须为 1），并发只会把两笔排进同一条锁队；
(b) 对岸那个 1–16 是给人盯下载速度的，桌面今天没有那一屏的偏好区；(c) 少一枚键就少一处「实现了但零调用点」/「假开关」。
这条由判据钉成"那一颗常量恰 1 处且没有第二枚并发数"，**不**写成"以后可以加"。

**④ 重启续排：`RUNNING` 那一档在写盘时就降级成 `PENDING`，读盘时不猜。**
对岸是在 `saveTasksToDisk` 里把 `DOWNLOADING` 写成 `PAUSED`（`:674`），读侧再原样认。
桌面这一侧不留"看起来正在跑而其实进程已经死了"那一档 ⇒ **写盘那一跳就把进行中降级成待跑**，读盘时不需要"这条是上次的僵尸还是这次的活"这种推断（宁可错慢不可静默交错那一族的正解形态）。
取消语义（批 100 那条协作式边界）在队列里要**重新定义而不是照抄**：对岸 `Job.cancel()` 硬掐（`:187` 与 `:219` 两处），桌面的闸掐不掉在飞的那一笔 ⇒ 队列那枚「停这一笔」念的是「停手：不再取下一页」，屏上措辞沿用批 100 那两句（「正在停手…」而**不是**「已停止」）。

## 3. 桌面的动线（谁住在哪）

- **宿主**：应用根那一颗 composable 里 `remember(dataRoot) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }` + `DisposableEffect onDispose { cancel() }`。
  现状读数：桌面 `src/main` 下**没有任何长驻作用域**（`rememberCoroutineScope()` 现量 **17 处使用**，而 `grep 'CoroutineScope('` 去掉这一族之后 **0 命中**；`VeneraDesktopApp.kt:693` 那枚 `zoomScope` 也是同一族）⇒ 这颗是本批新建的唯一一枚，判据要数它恰一处。
  ⚠️ 用 `remember` 而不是 `rememberSaveable` 的理由与 `VeneraDesktopApp.kt:428-432` 那段注释同（应用根那层没有 registry，写后者只会让人以为有跨进程恢复）。
- **队列件**：`DesktopComicDownloadQueue`（数据层，纯 `StateFlow` + 那颗任务表的读写）与**调度器**分开两颗 —— 装载与展示分家是本仓的既有规矩。
- **入口**：详情那一屏长按的第二枚动作改成「加入队列」（现有 `onDownloadEpisode` 那一枚的去向变了而**不动长按机制**）；那一屏状态槽从「一笔」换成「这一话在队列里的那一枚或 null」。
- **看队列的那一屏**：新建一屏（排在 `DesktopComicDownloadsPane`「本机已下载」旁边，不是同一颗 —— 那一颗看的是**结果**，这一颗看的是**过程**）。

## 4. 判式草案（每条都要答得出"改哪一行会红"）

数据层（新建类）：
1. 入队两笔 ⇒ 表序就是执行序，且 `taskId` 三格拼法逐字；
2. 重复入队不去而**已完成那档重排**（对岸 `:145-153` 那一形的对照）；
3. 队列跑完一笔后**回读盘**才说 `DONE`（喂"runner 说完成而清单没写完"这一档 ⇒ 不许 `DONE`）；
4. 写盘那跳 `RUNNING` 降级成 `PENDING`（读回来不含"正在跑"那一档）；
5. `directoryPath` 那一格是相对目录名（负向：不含盘符、不含 `\`）；
6. `downloadedPages` 从盘上来（续跑样例：盘上 2 页而这一趟写 1 页 ⇒ 交回 3）；
7. 字段 15 枚逐枚点名 + 两两不同（批 98 那颗 ⑫ 改判的形状，不许只钉"互不相同"）；
8. 坏档留档并点名（与批 88 那颗 cookie 罐同形）；
9. 串行恰 1（并发常量一处、无第二枚、无新偏好键）。

接线层：
10. 应用根那颗作用域恰一处 + `onDispose` 关掉的是取进局部的那一颗（批 80 ⑦ 那一课）；
11. 长按第二枚动作的去向从「就地跑」换成「入队」（数出现次数，不数存在 —— 批 99 那一课）；
12. 详情那一屏不再需要那枚「只容一笔」硬闸 ⇒ `:331` 那一行的**替代形状**要钉整串表达式（不许写成"没有硬闸"）。

语义树：
13. 队列那一屏三态各一句（空 / 有排队 / 有失败）；
14. 那一枚「停这一笔」只在跑着时摆（沿用批 100 那颗）。

假话回流：
15. 中英两份 README 那句「一次只能下一话」不许回流 + 现有那三处「本机已下载」相关旧账同步改判。

## 5. 取证种子（这一批没有它量不到）

- `--enqueue=<漫画号>/<话号>,<话号>,…`：起窗即入队多话（走**与长按同一条代码路**，否则量到的是种子那条路而不是用户那一条 —— 批 96 那一课）。
- `--queue-stop-after=<N>`：跑完第 N 笔后扳那一枚「停这一笔」，量「在飞的那一笔掐不掉」这一格。
- `--queue-peek`：只把队列表念成一行读数（`D_队列 笔=… 跑=… 停=…`）而不真下 —— 给"重启续排"那一趟当对照组。
- ⚠️ 包内 stdout 是 GBK，读数先 `iconv`；盘上的文件数**在进程停之后**才数（批 96/99 那两课）。

## 6. 未量到 / 下一批

- **未量到**：对岸的**速度那一格**（`speedText`）算在哪一行、桌面要不要这一格；队列那一屏要不要显示"剩余时间"。
- **未量到**：`ChapterCompleteness.kt:19` 那一档到底是按话还是按页（批 99 记过"读数里齐/缺按话数而屏上按页数"，这颗文件今天没逐行读）。
- **批 102 候选**：图片魔数校验 + 坏图重下（对岸 `:444-451,580-592` 有而桌面只判 `length > 0`）。它会把"齐"的定义改动 ⇒ **必须单独一批**，不能塞进队列（会撞上 §2② 那把唯一尺子）。

## 7. 代价档

按批 96–100 的实测节奏：数据层那颗 + 9 判式 ≈ 一批里最大的一块；接线 + 那一屏 + 语义树 再一块；台架 15–20 笔；包内 4–5 趟。
**总计 = 「大」档（与批 97 + 98 两批之和相当）**，比批 99/100 各高一档。理由就一句：这一批第一次动**应用根那一层的作用域宿主**，而那一层此前只有 `rememberCoroutineScope`。
⚠️ 2026-10-13 复核：这一档**不降**，而且"能不能拆成两批"这一问已被 §8 那笔试切量出答案 —— **拆不开**。

## 8. 2026-10-13 试切「上半 = 只做数据件」**失败**的账（下一批开工前先读这一节）

今天先按"上半 = 队列数据件 + 判据，下半 = 宿主与那一屏"落了一版纯数据件（11 颗判据全绿），结果**被仓里既有那把 `DesktopUnusedInternalTest` 拦住**：

```
① 桌面 main 里不许有产品代码零调用的 internal 件
   …这些 internal 声明在产品代码里一处都没被提到：desktopComicQueueNext / Enqueue / MarkDone /
     TasksJson / LoadTasks / PagesOnDisk / LampText / CanStart（仅测试引用）
③ 仅测试引用那一档也必须没有  expected:<0> but was:<8>
```

⇒ **这条切法不成立**：那把尺子（本仓的规矩，不是缺陷）要求每一颗 internal 都有**产品代码里的调用点**，
所以"数据件先落、接线下一批"这一族在桌面走不通 —— 队列**必须与它的执行器同批落地**。
两份文件已收回（`git status` 里不留半成品），全量复跑回到 `1369 / 159 / 0`。

### 真正的前置问题是「引擎归谁」，而它今天有读数了

执行器要取页就得吃一枚 `EngineSession`。现量：桌面**三处**造引擎，全都是"用完即关"的形状 ——
`VeneraDesktop.kt:120`、`gallery/ui/DesktopComicPane.kt:442`（在 `withContext(Dispatchers.IO)` 里现造现用）、
`gallery/data/DesktopCookieLogin.kt:60`。而详情那一屏的 runner 吃的是**外面递进来的** `session`（`DesktopComicDetail.kt:271` `val engine = session`）。

三档候选，各自的代价与风险（下一批开工时先量掉两档）：

| 档 | 形状 | 代价 | 风险 |
|---|---|---|---|
| A | 应用根自持**第二枚**长驻 `EngineSession` 给队列用 | 一次 GraalJS 装载 + 常驻内存 | 两枚引擎同时活着 = 批 81 那把锁要重判（锁键含引擎身份，两枚不互斥）；退出要关干净（批 80 ⑦ 那一课） |
| B | 每一笔任务**现造现关**一枚引擎（照 `DesktopComicPane.kt:442` 那一形） | 每笔一次装载（秒级） | 不引新长驻件，最像现有形状；但一笔一关会把"续跑到第 N 页"的会话状态丢掉（页表要重问一次） |
| C | 队列只在**漫画域那一层**活着（离开域就停手，任务留在盘上） | 复用域那枚 session，改动最小 | 「离开那一屏还在跑」这一格**做不到** ⇒ 屏上必须说清"回这一域才继续"，否则就是假开关 |

本鱼当前推荐 **C 起步 + B 兜住"域外也要能续"** 的那半句 —— 但这一条要**先量再定**，不许凭表定档。

三档候选，各自的代价与风险 —— **本批收尾前已把这一问量掉，定档见下**：

| 档 | 形状 | 代价 | 风险 |
|---|---|---|---|
| A | 应用根自持**第二枚**长驻 `EngineSession` 给队列用 | 一次 GraalJS 装载 + 常驻内存 | 两枚引擎同时活着 = 批 81 那把锁要重判（锁键含引擎身份，两枚不互斥）；退出要关干净（批 80 ⑦ 那一课） |
| B | 每一笔任务**现造现关**一枚引擎（照 `DesktopComicPane.kt:442` 那一形） | 每笔一次装载（秒级） | 不引新长驻件；但一笔一关会把"页表"重问一次（那一问是站方往返，白烧流量） |
| C | 队列的**执行**只在**漫画域那一层**活着（离开域就停手，任务留在盘上） | 复用域那枚 session，改动最小 | 「离开那一屏还在跑」这一格**做不到** ⇒ 屏上必须说清"回这一域才继续"，否则就是假开关 |

### 定档（2026-10-13，现量之后）：**C**

量的那一格是「漫画域到底有没有一枚活得比详情屏久的引擎」—— **有**：
`DesktopComicPane.kt:413` `var session by remember { mutableStateOf<EngineSession?>(null) }`，
`:414` 起 `DisposableEffect(session)` 在退域时关掉的是**取进局部的那一颗**（批 80 ⑦ 那一课的落地形状），
`:468` 装载成功才 `session = result.engine`，而 `:261-280` 把它一路递给详情那一屏。
⇒ **域那一层已经是引擎的宿主**，队列的执行器挂在它上面是**复用**而不是新引一枚长驻件（A 档整档因此不必要），
也不必每笔重新问一次页表（B 档那格白烧流量）。

C 档的两条**必须一起做**，否则就退化成假开关：
1. **状态与执行分居**：任务表挂在**应用根**那一层（顶栏那枚牌子在域外也要念得出真话），
   执行器挂在**漫画域**那一层（吃 `:413` 那枚 session）。同一颗 holder 两处读，不是一个 holder 两处写。
2. **屏上念得出这一格**：离开域 ⇒ 协作式停手（批 100 那颗闸，在飞那一笔掐不掉），
   那一行念的是「回这一域就接着跑」而**不是**「已暂停」—— 与批 100 那句「正在停手…」不许写成「已停止」同一理。

### 顶栏那枚牌子（同批必改，且现在有了判据形状）

`gallery/ui/DesktopGalleryTopBar.kt:84` 摆的是**硬编码**的 `StatusLamp("三站 · 本地无任务队列")`，
而它自己的注释 `:177` 写着「而"本地无任务队列"这句是**真的**：桌面取数是"拉一次画一次"，没有队列这个东西」。
现查：**没有任何判据钉着这串字面量**（`grep StatusLamp|本地无任务` 在 `desktop/src/test` 下 0 命中）⇒
队列一落地，这句就静默变假，而仓里那把扫假话的尺子抓不到它（它扫的是 `gallery/ui/` 的**措辞**表，不是这枚牌子）。
⇒ 这一批必须**同时**：把牌子改成由任务表算出来的一句（三档：空 / `N 话排队中` / `正在下 第 k 话 · i/M 页`），
并补一颗判据钉"它念的是**算出来的**而不是写死的"（负向：`StatusLamp("三站 · 本地无任务队列")` 那一整串在 main 里 0 处）。
（批 100 那一帧图证里这枚牌子就在右上角，念的正是「三站 · 本地无任务队列」。）

⚠️ 换措辞前有一件已核过的事：仓里那把名字最像的尺子 `DesktopPageChromeTest` ①（「旧页脚没有复活 三站口径只由 Hero 侧卡说一次」）**扫的是 `DesktopDailyPane.kt` 那一颗文件**（现量：`codeText(desktopUiSource("DesktopDailyPane.kt"))`），
所以它**不会**因为顶栏这枚牌子改词而红 —— 别把它当成"已经有人钉着这串字面量"。
（这一句本鱼先前写的是"那颗尺子会动"，那是**没核就写**，2026-10-13 复核实为 0 处相关，已就地改判。）

## 10. 2026-10-13 复量：抽那颗 runner 牵动的不是「那一颗」而是**四把尺子、三颗文件**

本档 §8 原先只登记了「`DesktopComicDetailWiringTest` ⑪ 逐字钉着那一处调用点」——**数量错了**（只数了一颗文件）。
现量（`grep -rn "desktopComicDownloadChapter\|runChapterDownload" desktop/src` 逐条回读判式原文）：

| 尺子 | 钉的是什么 | 抽件之后要怎么改判 |
|---|---|---|
| `DesktopComicDetailWiringTest.调用点递的是话号而不是本子号` | **整串实参** `desktopComicDownloadChapter(dir, target.comicId, episode.id, index,` 在详情那一颗文件里恰一处，**负向**钉旧形 `…target.comicId, target.comicId,` 零处 | 实参表搬到共用件后那两个名字（`target.comicId` / `episode.id`）在调用点变成 `comicId` / `chapterId` 之类 ⇒ 这一颗要拆成**两枚锚点**：调用点那一串（递的顺序仍是 号 在前、话号在后）+ 共用件那一串（形参名与清单那一格的对应）。它的**题面**（同名两枚参数递错不编译不红纯函数，只有钉整串才看得见）不变，钉法要跟着搬家 |
| `DesktopStaleClaimsTest.章节下载那句不再说缺` | 拿 `detail.contains("desktopComicDownloadChapter(dir,")` 当**对照组**，证的是「README/手册那句『整话落盘接上了』不是假话」，另有一条钉 `desktopComicChapterDir(downloadsRoot(),` | 证据要改指**共用件里那颗 runner 有真实消费点**（"消费点而不只是声明"这一层含义必须保住，否则那条对照就退化成 `DesktopUnusedInternalTest` 已经管的事）；最稳的写法是**两侧都钉**：共用件里有落盘那一跳 + 详情/队列两处各有一次调用 |
| `DesktopReadmeStaleClaimsTest`（同一格那把） | 与上一把同源，只是它扫的是 **README 的中英两条**，并把同一字面当正面证据 | 与上一把**一起改**，并把"改一处必须同步另一处"那条机械尺子的对象跟着指过去 |
| `DesktopComicDownloadSeedTest` ⑤ | `occurrencesAcrossMain("runChapterDownload")` **恰 3**（声明 + 长按 + 种子两处按），另有三条"长按/种子各吃那颗 runner""runner 不在挂手上"的断言 | 抽件之后**枚数会变**（那颗 lambda 的声明搬到共用件就是 2 或 3，取决于留不留薄壳）。这一颗本来就是"数出现次数"那一族 —— 改判时**别把数改成"至少一处"**，那正好是它当初要防的那种退化 |

**结论没变而代价更硬了**：这四把里有**两把把同一串字面量当作对外文档那句话的证据**（README 与屏上文案各一把），
所以抽件不是"改一处调用点"，而是**一次跨三颗测试文件的证据重定向**，而且每把都要求"消费点"而不是"声明在"。
⇒ 这一批仍然只能整批落（§8 那条切法约束不变），且开工第一件事应当是**先给这四把各写一枚"搬家之后仍然成立"的新钉法**，
再动那颗 runner —— 顺序反过来就会有一段"文档证据悬空"的时间，而那正是本仓那一族「说明还在而证据没了」。

## 11. 2026-10-13 再复量三处接缝（行级）：其中一格的代价可以整格划掉

① **任务表这一格其实不必动应用根** —— 桌面早已有"进程级单例"那一形：`object DesktopGalleryFailureLog`
（`gallery/data/DesktopGalleryFailureLog.kt:14-32`，里面是 `ConcurrentHashMap` 加一枚给用例用的 `reset()`）。
照那一形立一颗 `object DesktopComicQueue` + 一枚 `StateFlow`，**顶栏那枚牌子直接 collect 它**就行，
不需要新的组合层宿主、也不用碰 `VeneraDesktop.kt` 与 `gallery/ui/VeneraDesktopApp.kt` 那两处集成点。
⚠️ 同形的前案要一起记住：`GalleryFavoritesStore.notice` 那颗 StateFlow 从前**从没被 collect**（"实现了而零调用点"那一族）。
⇒ 立这枚状态时必须同批立一条**"有人在 collect"**的判据（批 99 那枚 `D_卡标命中 站= 墙= 贴上= 盘上=` 就是这一课的正解：
屏上断言要配一枚读数，别留成推理）。

② **作用域那一格仍然是桌面第一次** —— 现量：`desktop/src/main` 里 `CoroutineScope(` 长驻宿主 **0 处**，
而 `rememberCoroutineScope()` **17 处**（`DesktopComicDetail.kt:235`、`DesktopComicDownloadsPane.kt:63`、`DesktopComicPane.kt:498`、
`DesktopComicReader.kt:265`、`VeneraDesktopApp.kt:693` 等，逐枚可 `grep -rn "CoroutineScope("` 复现）。
⇒ 执行器那颗 scope 只能挂在**已经活得比详情屏久的那一层**：`DesktopComicPane.kt:413` 那枚 `var session by remember`
与 `:414` 的 `DisposableEffect(session)` 同一把钥匙上，多挂一颗 `CoroutineScope(SupervisorJob() + Dispatchers.IO)`，
`onDispose` 里先 `cancel()` 那颗 scope **再** `close()` 那颗 session。
⚠️ 这一格判据要**钉顺序**而不是钉存在（本仓那一课）：反过来就是"任务还在跑而 context 已被关"，
而批 80 真跑撞到的那句 `PolyglotException: Context execution was cancelled.` 正是它的近亲
（当时的成因记在 `DesktopComicDetailWiringTest` ⑦ 那条：`onDispose` 读的是变量当下的值）。

③ **详情那一屏的状态槽仍然只容一笔** —— `DesktopComicDetail.kt:443` 那一格 + `:331` 那枚
`if (download != null) return@LaunchedEffect` 硬闸（本批复量仍在）。⇒ 多话排队要么改这一格，要么让长按只**交一枚意图给队列**
而不在详情里自己跑；别假设"多按几次自己就排上了"，那道早退会把第二笔之后全吃掉而屏上一句实话都不念。

**代价档因此更新**（不是降档，是把一格不确定性划掉）：原记「这一批第一次动**应用根那一层**」那一格**不成立** ⇒
省下集成方与跨域宿主两处改动；剩下的"大"落在三处 —— §10 那四把尺子的证据搬家、上面②那颗长驻 scope 第一次立、队列那一行/那一屏的 UI 新建。开工次序建议：先 §10 那四枚锚点（纯测试侧，可先绿），再 ①②（数据 + 作用域），
最后 UI 与包内读数。

## 9. 一句话给下一批

题面已经量干净了：**切法**（数据件与执行器同批，`DesktopUnusedInternalTest` 不许"仅测试引用"）、
**引擎归谁**（定档 C：执行器挂 `DesktopComicPane.kt:413` 那枚域级 session，状态挂应用根，两处一读一写）、
**同批必改的两格**（顶栏那枚写死的牌子 + `DesktopPageChromeTest` ① 跟着改判）。
剩下的都是实现，不再是"不知道要做什么"。

## 12. 落地账（2026-10-13，批 101 收口）

### 落了哪几颗件

| 件 | 位置 | 干什么 |
|---|---|---|
| 队列表 | `gallery/data/DesktopComicDownloadQueue.kt`（**新建**，16 枚字段那份 `download_tasks.json`） | 任务表（`object` + `StateFlow`，前案 `DesktopGalleryFailureLog`）：入队去重与"已完成那档重排"、写盘那一跳降级、坏档留档并点名、牌子那一句、`D_队列` 那一行 |
| 执行器 | `gallery/ui/DesktopComicQueueRunner.kt`（**新建**） | **唯一那一圈泵**：读表 → 一次只取第一枚待跑 → 问页表 → 逐页落盘 → **回读盘**才定档 → `finally` 把「正在跑」交回「待跑」 |
| 作用域宿主 | `DesktopComicPane.kt:413` 那一层 | 桌面**第一枚**长驻作用域 `CoroutineScope(SupervisorJob() + Dispatchers.IO)`，key 与 `session` 同一枚；`onDispose { queueScope.cancel(); closing?.close() }`（**先掐作用域再关 context**） |
| 详情那一屏 | `DesktopComicDetail.kt` | 长按与 `--download=` 都改成只把「排进队列」这一枚意图交出去（`enqueueChapterDownload`）；那一格状态槽从"自己那一趟"换成**从队列表里挑出这一本的那一笔**；`if (download != null) return@LaunchedEffect` 那枚"只容一笔"的硬闸换成「这一话已经在队里」 |
| 顶栏牌子 | `DesktopGalleryTopBar.kt` + `VeneraDesktopApp.kt` | 写死的 `StatusLamp("三站 · 本地无任务队列")` 改成必填参数 `statusText`，由任务表算出来（四档），注释里那句"而这句是真的"就地改判 |
| 屏上新档 | `desktopComicChapterStatusText` + `DesktopComicChapterDownload.queued` | 第六档「排着队 · 回到漫画域就接着跑」—— C 档那条边界写在屏上而不是写在文档里 |

**数字（现量）**：判据 **1400 条 / 162 类 / 169 文件 / 0 失败**（上一批 1382 / 161 / 168），`main` **121 颗 / 25,619 行**（批 104 是 119 / 24,970 ⇒ 本批 +2 颗 / +649 行），`test` 源码 **32,532 行**（+501），`app/` **0 行改动**（`git status --porcelain app/` 仍是 66 颗，与本批开工前同一数）。

### 四条拍板落到码上之后的两处**改判**（不是照方案做的，要记账）

1. **§4⑨「并发常量恰一处」改成"不立常量"**。一枚喂给 `take(1)` 的常量是**假开关**（改成 2 也不会真并发，因为那一圈本来就是一枚 `while`）。串行改由**循环形状**钉：`nextPending` 恰一处 + `queueScope.launch {` 恰一处 + 零处 `async`/`coroutineScope`。
2. **§5 那三枚种子只做了一枚都没有**。`--download=` 与 `--download-cancel-after=` 今天吃的就是队列那一条路（长按与种子同一条纪律没变），而"多话排队"那一格改由 **判据 ①②⑰（表序=执行序、串行取第一枚、重启续排）+ 单元层真跑**钉；包内那一趟要用多笔排队的话，**预置那份 `download_tasks.json`** 比新引一枚 `--enqueue=` 更省（新引一枚要动六层接线 + 四把判据，而它换不来"表序与串行"之外的东西）。⚠️ 因此**本批没有新的取证种子**，`ForensicFlags` 一字未动。

### 未落的两格（登记）

- **看队列的那一屏**（§3 最后那一颗、§4⑬⑭）：三档各一句 + 那一枚「停这一笔」在**那一屏**上的形状。今天"停这一笔"只在详情那一屏有把手，而全局视图由顶栏那枚牌子给（一句而不是一列）。落它不需要动队列表 ⇒ 可以单独一批，且不会撞上 §8 那条"`DesktopUnusedInternalTest` 不许仅测试引用"的切法约束（那一屏自己就是消费点）。
- **扰动台架（本批唯一的低于常规口径处）**。补跑清单，每一笔都要"改坏→全量→逐字还原"：
  `R1` 撤掉写盘那一跳的降级（`downgradeRunning` 原样交回 `status.name`）→ 咬 ④；
  `R2` `directoryPath` 改存绝对路径 → 咬 ⑤；
  `R3` `settle` 里把 `desktopComicChapterIsComplete` 换成恒真（runner 说完成就算完成）→ 咬 ③；
  `R4` `settle` 里把 `downloadedPages` 写成 `run.written`（内存计数）→ 咬 ⑥；
  `R5` 牌子改回硬编码那一串 → 咬 ⑪ 两枚；
  `R6` 泵里加第二枚 `queueScope.launch { }` → 咬 ⑨；
  `R7` `onDispose` 顺序反写 → 咬 ⑭ + `DesktopComicDetailWiringTest` ⑦ + `DesktopComicRowDispatchTest` + `DesktopDomainNavigationTest`（四处字面锚点同一串）；
  `R8` 撤掉 `finally` 里那一枚 `abandonRunning` → 咬 ⑭；
  `R9` 详情早退闸改回 `if (download != null)` → 咬 ⑮ + `DesktopComicDownloadSeedTest`；
  `R10` 摘掉一枚 `collectAsState`（应用根改读 `.value`）→ 咬 ⑩；
  `R11` 少写一枚字段（`errorMsg` 那一行 `put` 撤掉）→ 咬 ⑦（并连带 ④⑰ 读不回）；
  `R12` 长按绕过挂手直连 `enqueue(` → 咬 ⑫ + `DesktopStaleClaimsTest` 那枚对照组。
  ⚠️ 台架纪律照旧：笔的终点串必须**保持可编译**、`BUILD_FAILED` 不算咬住、跑着时不许动被测源文件、每笔跑前删旧结果 XML 并核 mtime。


## 12b. 台架 15 笔的逐笔终账（2026-10-13 本批收尾补跑）

台架 `_qa/b101_perturb.js` 15 笔，驱动 `sh _qa/perturb_driver.sh b101 15`（逐笔 apply → 全量 → report → revert，目录锁与 `set -o pipefail` 都在驱动里）。
**归账：12 笔咬住 + 2 笔探针正确静（各换出一把新尺子）+ 1 笔 BUILD_FAILED（量具形状坏了，不算咬住）**；还原后全量 `1401 / 162 类 / 169 文件 / 0 失败`，`three_way_counts.js` 三方对得上、`check_ruler_catalog.js` 0 处漂移。

| 笔 | 改坏的是哪一行 | 谁咬住的 | 记账 |
|---|---|---|---|
| R1 | 写盘那一跳的降级（`downgradeRunning`） | ④「写盘那一跳降级掉正在跑那一档」 | 第一版笔形用了 `if (false)`，被 `DesktopAutoPlayTest:55` 那把**全 main 死支扫描**代咬（那条红是真的而归因脏）⇒ 换成「DONE 才降级」重跑，只剩本批 ④ + ⑰ 一枚真实连带 |
| R2 | `desktopComicQueueDir` 不再拼回根上 | ⑤ + ③（建不出目录那一句） | 相对名当绝对用会同时打断"拼回根"与"回读盘"两把 |
| R3 | `settle` 里把判齐换成恒真 | ③「跑完那一档要回读盘才说完成」 | 第四种"齐不齐"口径的正解钉子 |
| R4 | 进度那一跳不落盘（探针） | **第一遍一笔都没红** ⇒ 补 ⑥′「每写一页都全量刷盘」后重跑咬住 | 缺口：内存改了而盘上留旧数，崩在半路再起来就少念几页 —— 改的是尺子不是笔 |
| R5 | 牌子改回硬编码那句 | ⑪ | 旧串 0 处那枚直接红 |
| R6 | 泵里起第二枚 `launch` | ⑨ | "两枚就是两个执行器"那枚计数钉住了 |
| R7 | `onDispose` 顺序反写 | ⑭ + `DesktopComicDetailWiringTest` ⑦ + `DesktopComicRowDispatchTest` + `DesktopDomainNavigationTest` | 一笔红四把 = 那一串被四批的尺子共同钉着，正是它该有的形状 |
| R8 | 撤掉 `abandonRunning` | ⑭（+ `DesktopUnusedInternalTest` ① 那把顺带） | 退域不交回待跑 ⇒ 回域永不重排 |
| R9 | 早退闸改回旧形 | ⑮ + `DesktopComicDownloadSeedTest` | "第二笔被静默吞掉"两把一起挡 |
| R10 | 应用根不再 `collectAsState` | ⑩ | 那枚"恰那两屏"的名单判据咬得住 |
| R11 | 少写一枚字段 `errorMsg` | ⑦ + ④ + ⑰ | 少一枚 → 自家 parse 认不出形状 → 读回来 0 笔，三把一起红 |
| R12 | 长按绕过挂手直连 `enqueue(` | ⑫ + SeedTest ⑤ + `DesktopStaleClaimsTest` + `DesktopReadmeStaleClaimsTest` | 一笔红四把：计数与两处"文档证据"同咬 |
| R13 | 把手不扳闸 | ⑬ + `DesktopReadmeStaleClaimsTest` | 「摆着而按不动」那一族有读数也有尺子 |
| R14 | 种子那枚页上限没消费点 | 第一遍 **BUILD_FAILED**（`if (false)` 那一形）⇒ 改成"整行摘掉消费点"重跑，⑬ 直咬 | BUILD_FAILED 不算咬住（量具坏了），这条纪律本批又用上一次 |
| R15 | 牌子少一档（1 笔排队改念「N 笔待跑」）（探针） | **第一遍一笔都没红** ⇒ ⑪ 补两枚"念得出「排队」二字"的正锚点后重跑咬住 | 缺口：⑪ 原先只钉"含一枚数"，换措辞就放过 —— 记进台架纪律：**"两两不同 + 含数字"钉不住一档的语义，要各档钉自己的关键词** |

### 三条量具结论（可复用）

1. **`if (false` 在本仓是"别人家的探针"**：`DesktopAutoPlayTest:55` 扫全 main 的 `if (false` / `seconds <= 0` 一类死支与重述，所以扰动笔**不许用恒假条件**当终点形状 —— 代咬会污染归因（本批 R1 与 R14 各踩一次）。
2. **计数型判据看不见死分支**：`armPageCap(` 那枚"恰一处"是文本计数，把调用塞进永不执行的那一支它照绿。所以"消费点"这一族要么摘整行（本批 R14 的重跑形状），要么配一枚行为判据。
3. **探针笔值得专门留**：15 笔里 2 笔探针全静 ⇒ 直接换来两把新尺子（⑥′ 与 ⑪ 的正锚点）。这正是"该红不红改的是尺子"那一条的可操作形式。
