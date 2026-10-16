# 批 105 · 桌面「看队列的那一屏」（方案，2026-10-13）

上一批是批 101（下载队列本体，rounds `desktop-comic-download-queue-2026-10-13.md` §12 落地账）。
这一批补的正是那一节末尾登记的**两格之一**：另一格（补跑批 101 的 15 笔扰动台架）此刻正在跑。

本文档里的对岸读数全部是 2026-10-13 现量的（`awk NR` 可复核），不是从批 101 §1 那张表抄的。

## 0. 这一批要解决的那一件事

批 101 之后，队列**能跑**而**看不见**：

- 全局只有顶栏那枚牌子的一句摘要（`三站 · N 话排队中` / `正在下 第 k 话 · i/M 页`），它说得出"整体在哪"而说不出"每一笔在哪"；
- 「停这一笔」的把手只在**详情那一屏**（`DesktopComicDetail.kt` 的 `stopActiveTask`），而用户按完第三话就退回列表时，手上那一笔已经没有把手了；
- 对岸有的**「撤掉这一笔」**（不删已写的页）桌面今天**一处都没有**。

所以这一批要三样：一列任务（每一笔一行，念得出档位与页码）、一枚「停手」、一枚「撤掉」。⚠️ **不要**再加"删文件"——那一格批 98 已经在「本机已下载」那一屏做过了，见 §2③。

## 1. 对岸那五颗动作逐条处置（`download/DownloadManager.kt` 现量）

| 动作 | 对岸证据 | 它对表做什么 | 它对盘上页做什么 | 桌面处置 |
|---|---|---|---|---|
| `pause(taskId)` | `:185-189` | `activeJobs[taskId]?.cancel()` 硬掐 + 状态落 `PAUSED`（行**留在表里**） | 不动 | **改形照搬**：桌面没有硬掐（协作式，批 100 那条边界），措辞只能念「正在停手…」而**不许**写「已暂停」—— 那听上去像立刻停了，也暗示有一枚"继续"把手 |
| `resume(taskId)` | `:191-193` | 状态回 `PENDING` | 不动 | **不立新把手**：桌面「再排一次」的合法形状就是再按一次同一话（`enqueue` 里"已完成/已停手那一档重排回待跑"已被批 101 判式 ② 钉死）。同一件事摆两枚把手就是两处口径 |
| `pauseAll()` | `:195-205` | 掐全部 + 把 `DOWNLOADING`/`PENDING` 一起落 `PAUSED`，顺带清 `speedText` | 不动 | **弃**：桌面恒串行 1（批 101 §2③），"全部"里最多一枚在跑、其余本来就还没动 ⇒ 这一枚按钮量不出任何与"逐个停"不同的事实 |
| `resumeAll()` | `:207-216` | 把 `PAUSED` **和 `FAILED`** 一起重排回 `PENDING` | 不动 | **弃**：它一句里干两件事（重试被停的 + 重试失败的）。要重试失败的，桌面已有的"再按一次那一话"更明确，而这里少一处恒真的批量把手就多一处可核对的真话 |
| `cancel(taskId)` | `:218-225` | 掐 + **`removeAll { it.taskId == taskId }`**（行从表里摘掉） | 不动 | **照形** = 这一批的第二枚把手「撤掉这一笔」，屏上那句要念得出"盘上那几页还留着"（否则用户会以为撤掉=删干净） |
| `delete(taskId, deleteFiles)` | `:227-240` | `cancel` + `File(task.directoryPath).deleteRecursively()` | **删整棵目录** | **不搬**：桌面已经有那颗删除件（批 98：回核→认→现量→动手→**回读盘**，失败就抛）。这一屏再挂一只手就是第二条删文件的路，而对岸这一条把失败 `Log.w` 吞掉（`:238`）正是批 98 反着做的那一形 |

⚠️ 顺带一格读数形状：对岸 `pauseAll` 里那枚 `speedText = ""`（`:202`）说明它**每笔带速度**，而桌面没有速度那一格（批 101 §6 登记的"未量到"仍未量到）。这一批不做速度：分母与页数已经够那一行说实话，多一格就是多一处要维护的估算。

## 2. 拍板（每条写清为什么是这个档）

**① 「停手」与「撤掉」是两枚把手，各自只改一件事。**
停手 = 扳那颗协作式闸（行留下、页留下、状态落 `STOPPED`）；撤掉 = 从表里摘掉那一行（页留下、盘上目录一字不动）。
判据要钉得住这两者的**差别**：撤掉之后 `tasks.size` 少一枚而那棵目录的页数不变；停手之后行数不变而档位变。写成一枚"取消"按钮就塌成了第二种事实。

**② 撤掉不许顺手删页。**
这是 §1 那颗 `delete` 的处置落进这一屏的形状。判式：`gallery/ui/DesktopComicQueuePane.kt` 里 `deleteRecursively(` **零处**，而全 main 仍恰一处（住在批 98 那颗件里）。

**③ 这一屏不挂"重下"与"删除"。**
删除归「本机已下载」那一屏（那里说的是**结果**，删的凭据也在那里）；重排归长按。这一屏只说**过程**。

**④ 行的摆位：追加在末尾而不插进既有行中间。**
现成前案与理由都写在码上（`DesktopComicPane.kt:205-219` 那段注释：既有尺子按 `rows[2]/rows[3]` 点 key 与题名，插中间会把那两枚指到别处）。新行 `key = QUEUE`、`title = "下载队列"`、`glyph = "队"`。

**⑤ 装载与展示分家照批 97 那一屏的形状。**
`DesktopComicQueuePane(downloadsRoot, …)` 装载件负责读表 + 扳闸；`DesktopComicQueueQueueContent(...)` 展示件只吃纯数据 ⇒ 语义树那层能喂着渲（三态各一句、两枚把手各按一次落到回调）。展示件里**不许出现** `EngineSession`（`DesktopComicDetailWiringTest` ③ 同一条规矩，离屏渲不出的就是那一族）。

## 3. 接缝现量（下一批开工时照这张表动，别再量第二遍）

| 要动的地方 | 现量（2026-10-13） |
|---|---|
| 域内行模型 | `DesktopComicPane.kt:205-219`（`DOWNLOADS` 那一行的写法与"追加在末尾"的注释） |
| 行内容枚举闸 | `DesktopComicPane.kt:243-250`（`content != …` 那一串 + `error("「${row.title}」不是漫画域的页")`）⇒ 新档必须同时加进这一串，否则起窗就炸那句 |
| 屏枚举 | `DesktopComicPane.kt:331` `enum class DesktopComicScreen { READER, DETAIL, EXPLORE, FAVORITES, HISTORY, SOURCES, DOWNLOADS }` |
| 载荷优先判断 | `DesktopComicPane.kt:337-341` `desktopComicScreen(content, hasComic, hasRead)` |
| 挂载 | `DesktopComicPane.kt:309` 那一支 `DesktopComicScreen.DOWNLOADS -> DesktopComicDownloadsPane(desktopComicDownloadsRoot(config.dataDir), deleteSeed = config.deleteSeed)` |
| 键与内容档 | `DesktopComicSectionKey` / `DesktopGallerySectionContent`（两颗都要加一枚，`when` 穷尽那一族才编得过） |
| 现成的两半分家 | `DesktopComicDownloadsPane.kt:57`（装载件）与 `:124`（`…Content` 展示件），含「读盘只在 `withContext(Dispatchers.IO)` 那一跳」的判据写法 |
| 表本身 | `DesktopComicDownloadQueue.kt`（`tasks: StateFlow`、`requestStop`、`enqueue`、`stopRequested`）⇒ **需要新增的只有一颗「摘掉那一行」**（`fun drop(root: File, taskId: String)`），其余全在 |
| 牌子与读数 | `desktopComicQueueLampText` / `desktopComicQueueReading`（`D_队列 笔=… 跑=… 待=… 停=… 败=… 完=… 留档=…`）已经写好了那一屏要念的档名 |

## 4. 判式草案（每条都要答得出"改哪一行会红"）

1. 新行**追加在末尾**：`rows.last().key == QUEUE`，且 `rows[2]`/`rows[3]` 的 key 与题名一字不变（对照组：插中间那笔必红）；
2. 行内容枚举闸认这一档：`content == DesktopGallerySectionContent.ComicQueue` 在那一串里恰一处（漏了就是起窗炸 `error`，而那是屏上量不到的形状）；
3. 装载件收 `downloadsRoot: File` 而**不收引擎句柄**；展示件形参表里零处 `EngineSession`（照 `DesktopComicDetailWiringTest` ③ 的写法）；
4. 三态各一句而两两不同：空表 / 只有排队 / 有正在跑（含页码 `i/M`），且**不许**把"没有排队而有已完成"念成空表（批 101 ⑪ 已经钉了四档，这一颗钉那一屏那三档）；
5. 「停手」按一次落到 `requestStop` 而**不落** `drop`：数出现次数（`requestStop(` 这一屏恰一处、`stopRequested(` 仍零处 —— 那是泵那颗闸的专属计数）；
6. 「撤掉」按一次落到那颗新件：`drop(` 全 main 恰一处调用 + 摘掉之后表少一枚而那棵目录的 `desktopComicQueuePagesOnDisk` **一字不变**（这一颗是 §2①②的正面钉子）；
7. 撤掉那一行时**不许**留下"正在跑"那一档的僵尸：撤的是正在跑那一笔 ⇒ 先 `requestStop` 还是直接摘？拍板是**照对岸 `cancel` 那一形**（掐 + 摘），而桌面掐不掉在飞那一笔 ⇒ 判式钉「摘掉之后那一笔的目录仍然只由那颗判齐件说话」，并登记一条：被撤的那一笔如果还在写，页会留到页边界为止（屏上那句必须念得出"这一笔已撤，已写的页留着"）；
8. `D_队列` 那一行仍由泵念，而这一屏**自己再念一行** `D_队列屏 行=N 停=k 撤=m`（屏上每句行为断言都要答得出"哪次量的"，别留成推理 —— 批 99 那枚 `D_卡标命中` 的正解形状）；
9. 顶栏那枚牌子不许被这一屏替换掉（负向：`StatusLamp(statusText)` 仍恰一处），两句各有各的对象；
10. 中英两份 README 那句「还缺下载队列那一屏」就地改判（两份**同一批改**，`DesktopReadmeStaleClaimsTest` ② 那把枚数尺子会盯着）+ 本仓那把扫假话的尺子扫 `gallery/ui/` 整目录，新文件自动在射程内（别在判式里写文件名单）。

## 5. 取证种子这一格要不要新引一枚

**不引。** 现量理由：这一屏的状态**全部来自那张表**，而那张表就在 `数据根/downloads/download_tasks.json` —— 起窗前把那份喂成任意形状（预置 3 笔：1 正在跑 / 1 排队 / 1 失败）就能量到"每一笔那一行 + 两枚把手各按一次"，
比新引一枚 `--queue-*` 少六层接线与四把判据（批 101 §12 同一笔账）。⚠️ 唯一的例外：**「撤掉」那一下要点真把手**，而语义树里 `performClick` 量得到（进程内、零窗口、不动用户光标），不需要种子也不需要像素。

## 5b. 两条现量补充（照抄批 97 那一屏的形状时要错的地方）

① **「撤掉」是一步而「删除」是两步。** 批 97/98 那一屏的删除把手走 `armedKey` 那一对标签（`desktopComicDeleteIdleLabel(2)` → `desktopComicDeleteArmedLabel(2)`，`DesktopComicDownloadsScreenTest.kt:211-225` 真按两次），因为**它动用户盘上的东西**。
这一屏的「撤掉」只摘表里那一行而**页一颗都不动** ⇒ 不需要确认那一步；给它加一枚"再点一次确认"就是把不可逆的形状搬到可逆的动作上，多一处要解释的话而少一处真风险。判式要钉得住这条区别：那一屏里 `armedKey` 那一路零处出现（不照抄），而 `lastResult` 那一路**要**照抄（撤掉了 / 撤没成 各一句，不许塌成一句）。

② **语义树那一层的量法已现成**：展示件收纯数据 ⇒ `runComposeUiTest` 里 `setContent { DesktopComicQueueContent(纯数据, …) }` + `onNodeWithText(...)` 断言三态，`performClick()` 断言两枚把手各落到**哪一颗回调**（`DesktopComicDownloadsScreenTest.kt:54-81/158-161` 同一族，零窗口、不动用户光标）。装载件那一半要真表 ⇒ 由 §4⑥ 那颗"摘掉之后表少一枚而盘上页数不变"在数据层钉，不在屏上钉。

## 6. 代价档
**中**。比批 99/100 略高（多一次行枚举与域路闸的改动），比批 101 低一整档（不动作用域宿主、不动那颗 runner、不改任何既有锚点的对象）。

⚠️ §3 那张接缝表**当场被推翻两处**（照它动会编不过）：加一枚 `DesktopGallerySectionContent` 要吃的不是"键与内容档两颗"，而是**六处穷尽 `when` 的错误名单**（图库 `DesktopGalleryHomeBody` + 收藏 + 搜索 + 画师 + 发现 + 设置各一处）+ 行模型那一枚 `content` 位 ⇒ 以逗号结尾那一串现量共 **7 处**；另有 `DesktopGalleryHomeNavRow` 那一处摆位档（编译器一次只报一颗，第一遍就报在 `DesktopGalleryHomeSections.kt:372`）。

**交付包这一批补打了** —— 落地前现量（2026-10-13，`node _qa/check_pkg_jar_sha.js _qa/desktop-exe-2026-10-13-b103/venera-desktop-probe`）：
包里那颗 `desktop.jar` 与 `desktop/build/libs/desktop.jar`（**5,010,819 B**，`sha256=d7f818f07d67b482…`）**不是同一颗而字节数也不同**，而 `engine-probe` 那颗逐字节相同（它这批没动）⇒ 差的就是批 101 那一次 `main` 改动（批 104 是纯测试侧、`main` 一字未动，所以"落后两批"那句是错的口径，此处按现量改判）。

## 7. 落地账（2026-10-13 本批收尾现量）

| 动了什么 | 哪一颗件 | 记的是什么 |
|---|---|---|
| 新建那一屏（装载 + 展示两半分家） | `gallery/ui/DesktopComicQueuePane.kt`（**新文件**，205 行）+ `gallery/ui/DesktopComicQueueRunner.kt`（读表那一跳搬家，见 §7c） | `DesktopComicQueuePane`（扳闸 / 摘行 / 那一屏自己的读数）与 `DesktopComicQueueContent`（只吃纯数据与两枚回调）分成两件；四档表头、行里那一句、两枚把手的文案都是**独立纯函数**，语义树喂着渲 |
| 数据层只多一颗件 | `gallery/data/DesktopComicDownloadQueue.kt` 的 `drop(root, taskId): Int` | 摘行 + 清 `caps` 而**不清 `stops`**；回值是摘那一刻盘上还剩几页（现量） |
| 那一行进漫画域首页 | `DesktopComicPane.kt` 五处（`DesktopComicSectionKey.QUEUE` / 行**追加在末尾** / 放行 `if` 串 / `DesktopComicScreen.QUEUE` + `desktopComicScreen` 映射 / 挂载那一支 + `desktopComicEngineNeeded` 归 false） | 与批 97 那一行同一族：`desktopComicRows()` 五行 → **六行** |
| 新档过六处穷尽闸 | `DesktopGalleryHomeSections.kt`（枚举 + `…HomeNavRow` + `…HomeBody`）/ `DesktopGalleryDomainPanes.kt` 四处 / `DesktopGallerySettingsPane.kt` 一处 | 少一处编不过；逗号结尾那一串现量 **7 处**（判式 ② 数着它） |
| 新建那颗类 | `desktop/src/test/…/DesktopComicQueueScreenTest.kt`（**17 颗**） | 8 颗行为 + 9 颗接线/计数（明细见判据目录那一行） |
| 既有三把尺子跟着搬家改数 | `DesktopComicHistoryScreenTest` ① 五行→六行；`DesktopComicDownloadsScreenTest` ⑤ 域档 7→8；`DesktopComicDownloadQueueTest` ⑬ 消费点 1→**3** | 「恰一处」那类计数判式在合法多一枚把手时必须改数，否则新屏一接就红 |

**数（2026-10-13 落地那一跑现量）**：判据 **1418 条 / 163 类 / 170 颗文件 / 0 失败**（`three_way_counts.js` = 三方对得上）；`main` **122 颗 / 25,897 行**（批 101 是 121 / 25,619 ⇒ +1 颗 / +278 行），`test` **32,975 行**（+443）；`app/` **一字未动**（`git status --porcelain app/` 与开工前同一数 66）。

**三处改判（写清是文档错了而不是代码变了）**：
1. §3 那张接缝表漏数：加一枚内容档要吃的是**六处穷尽 `when` + 行模型的 `content` 位**（共 7 处逗号串），不是"两颗枚举"；编译器第一遍就报在 `DesktopGalleryHomeSections.kt:372`。
2. §4⑤ 那句「`requestStop(` 这一屏恰一处」不成立也不该成立：那一屏合法有两枚（停手那一下 + 撤掉那一下先扳闸）。⇒ 改判成**两枚各钉一处、全 main 共 3 处**，并且把顺序钉子**圈进 `onRemove` 那颗 lambda**（`substringAfter("onRemove = { task ->")`）—— 全文件 `indexOf` 会被同屏另一处合法调用喂饱，那种判式对"撤掉整个漏扳闸"是绿的（台架 P2 就是专打这一形，改完才咬住）。
3. §5 那句"这一屏不需要取证种子"**改判而不落地**：预置那份 `download_tasks.json` 确实够喂形状，而"要点到那一屏"由**现成的** `--start-domain=COMIC --start-section=comic-queue` 满足（`paneKeys` 那一族，认不出还会念 `sectionNote`）⇒ 本批**没有新引一枚种子**，包内读数走这一路。

**本批另抓到一枚不属于它的缺陷（登记而未修）**：全量回归第一遍红在 `DesktopAccentFillTest`/`DesktopComicDetailSemanticsTest` 那两把别人家的尺子上，读数 `UncaughtExceptionsBeforeTest` 的栈全在 `app/…/LocalFavoritesManager.kt` —— 后台刷新先取表名再逐枚数，中间被 `rename`/`deleteFolder` 拆掉那张表就抛，而异常落在 `Dispatchers.IO` 的未捕获处理里，**由下一个 compose 用例中招**（谁中全凭顺序运气，三遍分别中在三个不同类上）。测试侧已排成确定性（拆表之前先 `close()` 等完在飞刷新），**真修要动 `app/` 那颗件，属受保域，未点头**；代价写在该文件新加的那段注释里（「后台刷新会不会跟着改名走」这一格今天没有判据覆盖）。

**台架**：`_qa/b105_perturb.js` 11 笔（8 笔起手 + 3 笔给后立的判式补），逐笔归账见 §7b。**交付包**：`_qa/desktop-exe-2026-10-13-b105/`（两颗 jar 与现产物逐字节同一颗、cfg `classpath=100 missing=0 orphan_jars=0`；包内那一趟见 §7c）。

## 7b. 台架逐笔终账（`_qa/b105_ledger.out` + `_qa/b105_ledger2.out` + `_qa/b105_p6.out`）

| 笔 | 打的是什么 | 结果 | 咬住它的尺子 |
|---|---|---|---|
| P1 | 撤掉那一枚顺手把盘上那一棵删掉 | **咬住** | ③ 那一屏 `deleteRecursively(` 要 0 处而全 main 恰一处；③ 页数出处那一枚；⑥′ 顺序钉子（`drop` 不见了 ⇒ `dropAt=-1`） |
| P2 | 撤掉那一枚**漏扳闸**（只摘行） | **咬住** | ⑥′ 顺序钉子（`gate=-1 drop=80`）；⑦ `requestStop(` 那一屏 2→1；批 101 ⑬ 全 main 3→2 |
| P3 | 「停这一笔」挂到所有行 | **咬住** | ⑥ 纯函数三档；⑧ 语义树 `assertDoesNotExist` |
| P4 | 「都排完」那一档塌成「队列空」 | **咬住** | ④ 四档两两不同 + 逐档点名 |
| P5 | 等价改写（行里那截页数换成拼接） | **正确静**（要求不红） | —— 尺子没钉写法，这一笔是它的对照 |
| P6 | 那一屏不再自己念读数 | 第一遍 **BUILD_FAILED**（终点串用了没 import 的 `desktopComicQueueLampText` ⇒ **量具坏，不算咬**）；换成「只念行数的假读数」复跑 → **咬住** | ⑪ 那一行由这一屏自己念；`DesktopUnusedInternalTest` ①/③（那颗纯函数退成「仅测试引用」） |
| P7 | 撤掉那一句里的页数换成恒 0（现量退成推断） | **咬住** | ③ 新加的那枚**出处钉子**（本笔就是为它立的探针） |
| P8 | 域路闸漏这一档 | **咬住** | ② 那一串 1→0 |
| P9 | 「还没读到那张表」那一档塌成「本机队列空」（§7d 那格假话的正面笔） | **咬住** | ⑯ 那一档要念得出在读 |
| P10 | 那颗闸反了向（读回来过才念「正在读」） | **咬住** | ⑯ 两句不许相同 + ④ 空表那一句（**两把各咬一次**，正向与反向都堵住） |
| P11 | 摘掉 starter 里读表那一跳（§7c 那枚缺陷的形状本身） | **咬住** | ⑭ 行为（读回来 0 笔）+ ⑮ 字面（`prime(downloadsRoot)` 那一处没了） |

**归账（三遍合起来 11 笔）**：**10 笔咬住 + 1 笔要求不红（P5 正确静）+ 1 次 `BUILD_FAILED`**（P6 第一遍，量具坏：终点串用了没 import 的件 ⇒ 改笔形复跑才咬）。P9-P11 是 §7c/§7d 那三颗**后立的**判式的补笔（它们的第一遍红是包内实测那一趟给的，不是台架给的），跑在修完之后：`sh _qa/perturb_driver.sh b105 11 9`。还原后全量两遍各一次：`1415 / 163 / 0`（P8 收尾那一跑，早于 §7c 那两颗判式）与 **`1418 / 163 / 0`**（P11 收尾那一跑，本批终账）。

## 7c. 包内那一趟（读数在前、图证在后）

预置 `trial-data\venera\downloads\download_tasks.json` 三笔（1 待跑 / 1 已排完 / 1 已停手），起包用 `try-queue-screen.bat`（= `--start-domain=COMIC --start-section=comic-queue`，不动鼠标）。**第一趟念出的是一枚真缺陷**：

- stdout：`D_落点 域=漫画(启动) 行=下载队列 …` 而 `D_队列屏 行=0 停=0 撤=0`，且**整趟没有一行 `D_队列 笔=…`**（泵从没跑到 `prime`）。
- 图证：那一行确实选中了、那一屏确实渲出来了（表头念「本机队列空 —— 章节表上长按「下载」那一笔会排到这里来」，顶栏牌子同一刻念「三站 · 本机队列空」）—— 而盘上那份表里有三笔。
- 成因：`desktopComicStartQueuePump` 旧第一行 `val engine = session ?: return`，而这一屏在 `desktopComicEngineNeeded` 里被归到「不要引擎」那一档 ⇒ 域那一层的 `session` 是 `null` ⇒ **读表那一跳永远轮不到**。详情那一屏看不出这件事（那里必然有引擎），所以 15 颗判式全绿而屏上是假话 —— 又一枚「单测全绿而屏上才露馅」的形状。
- 修法：读表 + 念 `D_队列` 搬到引擎判式**之前**，`if (engine != null)` 才跑那一圈（不新引第二处 `launch`，恒串行那颗判式照旧）。新增 ⑭（行为：`session = null` 也要把表读回来，吃 `Dispatchers.Unconfined` 就地跑完，不靠 sleep）与 ⑮（字面：`session ?: return` 不许回来 + 读表要在引擎判式之前）。
- **第二趟复验（修完 + 重打包）念对了**：`D_队列 笔=3 跑=0 待=1 停=1 败=0 完=1 留档=无` 与 `D_队列屏 行=3 停=0 撤=0`（起手那一行仍是 `行=0`，紧接着才 `行=3` ⇒ 又立案修了第三格，见 §7d）。
- 图证那一帧（标题 `#248`）三行都摆出来了：`第 1 话「第1話」 · 排队中 · 0/0 页 · 本子 2130882` / `第 2 话「第2話」 · 已排完 · 3/3 页` / `第 3 话「第3話」 · 已停手 · 1/3 页`；每行右边一枚「撤掉这一笔」而**三行都没有**「停这一笔」（没有一笔在跑 ⇒ 与判式 ⑥/⑧ 同一形状，屏上复现）；表头念「1 笔排队 · 一次只跑一笔（回到漫画域就接着跑）」，顶栏牌子同一刻念「三站 · 1 话排队中」—— 同一张表两处各说一句，互不替换。

## 7d. 第二趟量到的第二格：第一帧那句「本机队列空」

修完读表时机之后 `D_队列屏 行=0` 仍出现**一次**（然后才是 `行=3`）：那一屏的组合比 `prime` 那一跳快，而表在内存里的初值就是空列表 ⇒ 第一帧把「还没读到」念成了「读到了没有」。这与批 97 那一屏立「正在数本机存过哪些整话」同一族，所以同一处补同一形：

- 表那颗件多一格 `primed: StateFlow<Boolean>`，由 `prime` 在**赋完 `_tasks` 之后**才置真（反着置会留一帧「说读到了而数是空的」）。
- 那一屏的表头多一档：`tasks.isEmpty() && !primed` ⇒ 「正在读那张表…」；域那一层多收一次 `primed`（收表那两处不变，仍只有 `DesktopComicPane.kt` 与 `VeneraDesktopApp.kt`）。
- 判式 ⑯ 钉这一档：与「本机队列空」两句不许相同、语义树喂 `primed = false` 渲得出这一句而渲不出那一句、装载件必须把那一格递下去。

**登记在册而未做的一格（下一件的正题）**：站在这一行时 `desktopComicEngineNeeded` 归 false ⇒ 域那一层的 `session` 是 `null` ⇒ **泵不跑**（第二趟读数 `跑=0 待=1` 就是它）。屏上那句「回到漫画域就接着跑」因此要说得更准：今天它是「到探索或详情那一屏才接着跑」。两档修法：① 只改措辞（小）；② 让这一行在有活要干时才要引擎（`needed = 表里有待跑的笔`，中 —— 牵动 `desktopComicEngineNeeded` 那族按签名点名的尺子）。本批选 ①，**下一件按 ② 做** —— 「看队列的那一屏不驱动队列」本身就半件功能。

**第三趟（最终交付包 `_qa/desktop-exe-2026-10-13-b105/`，含 §7d 那一格）**：读数与第二趟同形 —— `D_队列 笔=3 跑=0 待=1 停=1 败=0 完=1 留档=无` → `D_队列屏 行=0 停=0 撤=0` → `D_队列屏 行=3 停=0 撤=0`（那第一行是**读数**，不是屏上那句：屏上此刻念的是「正在读那张表…」，判式 ⑯ 钉着）。图证那一帧（标题 `#32304`）三行齐摆、每行一枚「撤掉这一笔」而零枚「停这一笔」、表头「1 笔排队 · 一次只跑一笔（回到漫画域就接着跑）」、顶栏「三站 · 1 话排队中」。包内三道门：`check_pkg_jar_sha.js` 两颗 jar 逐字节同一颗、`check_cfg.js` 念 `classpath=100 missing=0 orphan_jars=0`、`check_ruler_catalog.js` 念「对得上：0 处漂移」。窗口关掉之后现数残留进程 **0** 颗。⚠️ 那一趟**没有**跑到任何一笔下载（`跑=0`），所以包内那份演示表今天仍是预置的那三笔 —— 这一格与上面登记的"这一行不驱动泵"是同一件事。
