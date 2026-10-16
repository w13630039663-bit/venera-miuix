# 批 106 · 让「下载队列」那一行自己驱动泵（方案，2026-10-13）

上一格是批 105（看队列的那一屏，rounds `desktop-comic-queue-screen-2026-10-13.md` §7c/§7d）。
本批做那一格里**登记而未做**的那一条：站在「下载队列」那一行时**泵不跑**。

## 0. 缺陷那一形（不是推测，是包内读数）

批 105 第三趟包内实测（`_qa/desktop-exe-2026-10-13-b105/`，预置三笔的演示表）：

```
D_队列 笔=3 跑=0 待=1 停=1 败=0 完=1 留档=无
D_队列屏 行=3 停=0 撤=0
```

盘上有一笔 `PENDING`，屏上三行都摆出来了，而 **`跑=0`** —— 那一笔永远不会开始。
成因：`DesktopComicPane.kt:374` 那颗 `desktopComicEngineNeeded` 把 `QUEUE` 归到 false 那一支
⇒ `DesktopComicPane.kt:278` 的 `rememberComicEngine(…, needed = false)` 交回 `session = null`
⇒ `DesktopComicQueueRunner.kt` 里 `if (engine != null) desktopComicQueuePumpLoop(...)` 那一支走不到。

屏上那句「回到漫画域就接着跑」因此是半句假话：**今天它是"到探索或详情那一屏才接着跑"**，
而"看队列的那一屏不驱动队列"本身就半件功能。

## 1. 定档：`needed` 多收一枚"表里有没有活要干"

```kotlin
internal fun desktopComicEngineNeeded(screen: DesktopComicScreen, hasQueueWork: Boolean): Boolean = when (screen) {
    DesktopComicScreen.FAVORITES, DesktopComicScreen.HISTORY, DesktopComicScreen.SOURCES, DesktopComicScreen.DOWNLOADS -> false
    // 这一行只在**真有一笔待跑**时才要引擎：空表还去起一颗 GraalJS 就是批 82.7 反着来。
    DesktopComicScreen.QUEUE -> hasQueueWork
    DesktopComicScreen.EXPLORE, DesktopComicScreen.DETAIL, DesktopComicScreen.READER -> true
}
```

**必填、不给默认值**（同一颗纪律第三次：漏传的形状是"参数在而永远恒真/恒假"，编译不报错）。
调用点 `DesktopComicPane.kt:278` 递 `queueTasks.any { it.status == DesktopComicQueueStatus.PENDING }` —— 那枚表**已经**在这一层收过（批 101/105），不多收一次。

⚠️ **代价要说清**：`rememberComicEngine` 那一跳是"起引擎 + 装载整源 + 30 张封面"（`ComicLoadResult` 把三样捆在一颗件里交回来）。
所以停在队列那一行且有活要干时，会付一次与进「探索」同量级的装载。两档备选：

- **A（本批做）**：接受这一笔代价。好处是零新地基，泵复用域那一层唯一那颗句柄（两颗 `EngineSession` = 把源脚本文档再 parse 一遍，批 82 的注释明令不许）。
- B（登记不做）：把"只要句柄不要封面"拆成第二颗装载件。要动 `rememberComicEngine` 与那颗装载闭包，牵面比本批整批都大，而收益只是省 30 张图的出网 —— 等真被量成瓶颈再说。

## 2. 接缝现量（2026-10-13，`grep` 可复核）

| 要动的地方 | 现量 |
|---|---|
| 那颗纯判断 | `DesktopComicPane.kt:374`（`QUEUE -> false` 那一支是本批要改的那一行） |
| 唯一调用点 | `DesktopComicPane.kt:278` `rememberComicEngine(activeConfig, needed = desktopComicEngineNeeded(screen))` |
| 按旧签名点名的尺子 | `DesktopComicRowDispatchTest.kt:58-62`（**5 处**调用）+ `DesktopComicDownloadsScreenTest.kt:117`（**1 处**）⇒ 共 6 处要跟着补第二枚实参 |
| 还有一把只吃"含不含那颗件"的尺子 | `DesktopComicRowDispatchTest.kt:72` `code.contains("desktopComicEngineNeeded(")` ⇒ 改签名不影响它 |
| 泵那颗入口 | `DesktopComicQueueRunner.kt:39-49`（`if (engine != null)` 那一支今天就是本批的行为钉子） |
| 屏上那两句要跟着改准 | `DesktopComicQueuePane.kt` 表头「1 笔排队 · 一次只跑一笔（回到漫画域就接着跑）」与 `desktopComicQueueLampText` 的「N 话排队中」/「排着队 · 回到漫画域就接着跑」（后者住在 `DesktopComicDetail.kt`，批 101 判式 ⑪ 钉着那两枚字面锚点） |

## 3. 判式草案（每条答得出"改哪一行会红"）

1. **行为**（本批的主钉子）：`desktopComicEngineNeeded(QUEUE, hasQueueWork = true)` 为真而为 false 那一档为假 —— 两档各一枚断言（只喂一种值的那枚会被"恒真"放过，这条纪律本仓踩过）。
2. **其余七档不许被顺手改宽**：`EXPLORE/DETAIL/READER` 恒真、`FAVORITES/HISTORY/SOURCES/DOWNLOADS` 恒假，且**与 `hasQueueWork` 取值无关**（两种实参各过一遍 —— 这一枚就是防"顺手写成 `-> hasQueueWork || true`"）。
3. **接线**：`DesktopComicPane.kt` 里 `desktopComicEngineNeeded(screen, ` 恰一处，而旧那一形 `desktopComicEngineNeeded(screen))` **零处**（搬家类判式两钉）。
4. **待跑那一格从表上来**：装载点吃的是 `queueTasks.any { … PENDING … }` 而不是 `queueTasks.isNotEmpty()` —— 数 `PENDING` 那一枚 token 恰一处（"有已完成"与"有待跑"是两件事，混了就变成"跑完了还白起一颗引擎"）。
5. **泵真的会跑**（行为层，不只看判式）：喂一份带 `PENDING` 的表 + 一颗 `session = null` ⇒ 只读表不跑圈（批 105 判式 ⑭ 已钉）；**新增**：`hasQueueWork` 为真时那一屏的表头不许念「本机队列空」而念得出在跑/在排（语义树喂着渲）。
6. **屏上那句跟着改准**：「回到漫画域就接着跑」这一串在 `gallery/ui/` 整目录里的归属要重数 —— 正解是**只留 `DesktopComicDetail.kt` 那一处**（那一屏本来就有引擎，那句在那里是真话），而队列那一屏改成「这一屏会把它跑到页边界为止」之类说得准的说法。⚠️ 批 101 判式 ⑪ 钉的是详情那一屏那两枚锚点，**不许顺手把它一起改红**：改之前先数谁在吃那一句。
7. **顶栏牌子那一句不许被替换**（`StatusLamp(statusText)` 仍恰一处 —— 批 105 判式 ⑫ 已钉，本批只复跑）。

## 4. 台架（预算 6-8 笔）

- 把 `QUEUE -> hasQueueWork` 改回 `-> false`（主钉子必红）；
- 改成 `-> true`（恒起引擎那一形 —— 只有"空表那一档为假"那枚判式咬得住，所以这一笔专门验它）；
- 把 `any { … PENDING }` 换成 `isNotEmpty()`（判式 4 咬）；
- 调用点退回旧那一形（判式 3 两枚各咬一次）；
- 把 `FAVORITES…DOWNLOADS` 那一支改成 `-> hasQueueWork`（判式 2 咬 —— 别的屏被顺手改宽）；
- 屏上那句改回「回到漫画域就接着跑」（判式 6 咬）。
- ⚠️ 三条笔形纪律照旧：终点串仍可编译、起点终点不互为子串、终点不许空串；**新加的两条**（批 105 学到）：终点串不许与同一颗文件里已有字面量同串（回滚 `replace` 只改第一处），也不许用 `if (false` 那种死支形状（`DesktopAutoPlayTest` 会代咬）。

## 5. 取证种子

**不引新的**：`--start-domain=COMIC --start-section=comic-queue` 已经能把窗落到那一行（批 105 §7c 那三趟就是这一路），而"跑起来了没有"由 `D_队列 跑=…` 念。
包内复验：演示表里那一笔 `PENDING` 这次应当真的被泵接走（`跑=1`，屏上那一行多一枚「停这一笔」）—— 这一格是**图证 + 读数两条**，缺一条就不算收口。

## 6. 代价档

**中**：一颗纯判断加一枚必填实参 + 6 处尺子搬家 + 屏上一句措辞改准 + 包内复验一趟。
不动作用域宿主、不动那颗 runner 的圈形、不引第二颗 `EngineSession`。

## 7. 落地账（2026-10-13 实施时现量，与 §1 的定档有一处**改判**）

**① `hasQueueWork` 不止数 `PENDING`，还数 `RUNNING`（§1 那一段照原文写会打转，本条就地改判）。**
实施前顺着接线推出来的形状：手上那一笔在内存里正是 `RUNNING`（写盘那一跳才降级成 `PENDING`，见 `DesktopComicDownloadQueue` 类注释④），
所以只认 `PENDING` 的话 —— 跑第一笔的同一刻 `queueTasks.any { PENDING }` 翻假 ⇒ `needed=false` ⇒
`rememberComicEngine` 里 `session = null` ⇒ `DisposableEffect(session)` 的 `onDispose` 掐掉 `queueScope` 并关掉 context ⇒
泵那一圈的 `finally` 走 `abandonRunning` 把那一笔交回 `PENDING` ⇒ `needed` 又翻真 ⇒ 重新起引擎、重新问页表。
**起一颗引擎、掐一圈泵来回打转**，每圈都重新问一遍页表。落成的表达式：

```kotlin
val queueHasWork = queueTasks.any { it.status == DesktopComicQueueStatus.PENDING || it.status == DesktopComicQueueStatus.RUNNING }
```

⚠️ 这条是**推演**不是读数（还没在屏上量到过转圈），但它改的是"要不要那么起"那一格的正误，所以按最保守那一档写。
判式 ④ 钉的是那一整串表达式，不钉标识符。

**② 装载点的两枚 `collectAsState()` 必须搬到 `engine` 那一行之前**（`queueTasks` 从前住在 `:280`，而新实参在 `:278` 就要用）。
搬完 `queuePrimed` 与 `queueTasks` 相邻，次序变了而读数没变 —— 那两格都只由那张表说话。

**③ 新增尺子 `DesktopComicQueueRowPumpTest`（6 颗 `@Test`）**：① 两档各一枚断言、② 其余七档两种实参各过一遍、
③ 调用点新形恰一处而旧形零处加签名必填一枚、④ 那一整串表达式恰一处而 `queueTasks.isNotEmpty()` 零处、
⑤ `if (engine != null) desktopComicQueuePumpLoop(engine, downloadsRoot)` 恰一处、
⑥ 队列那一颗文件里「回到漫画域」零处而新那句「站在这一屏就接着跑」在。

**④ 三处既有尺子跟着改判**（都是本批故意推翻的旧账，不是回归）：
`DesktopComicRowDispatchTest` 那一颗 @Test 的 5 处调用补 `hasQueueWork = false`（两档由新尺子钉，不双轨）、
`DesktopComicDownloadsScreenTest` ⑤ 那一处同理、
`DesktopComicQueueScreenTest` ② 的第三枚断言从「`QUEUE -> false` 恰一处」**改成「`QUEUE -> false` 零处」**——
批 105 那句「这一屏不要引擎」正是本批推翻的那半句假话。

**⑤ 台架八笔**：`_qa/b106_perturb.js`（由批 105 那份生成，`diff` 只落在头部与 PETS 两段、marker 两行都在，台架纪律第 9 条）。
笔形与预期：P1 恒 false、P2 恒 true、P3 表非空、P4 调用点用错格（递 `queuePrimed`）、P5 四屏被接上实参、
P6 泵那一支摘掉句柄闸、P7 屏上那句改回旧那半句 —— 七笔都该咬；P8 恒等改形（只加一对括号）**必须一笔不红**。
台账（`_qa/b106_perturb.summary.txt`，2026-10-13 现跑）：**7 笔咬住 + 1 笔正确静** —— P1 恒 false（咬 ① 真那一档 + 批 105 ② 那枚负钉）、P2 恒 true（咬 ① 假那一档）、P3 表非空（咬 ④）、P4 调用点递错格（咬 ③）、P5 四屏被接上实参（咬 ②）、P6 泵那一支摘闸（咬 ⑤ + 批 105 ⑮，另外把 ⑦ 那枚语义树判式打成 `UncaughtExceptionsBeforeTest` —— 没有句柄就 `engine!!`，未捕获的空指正确实在那一圈里抛了，这一枚归因是**连带而不是噪声**：它证明判式 ⑤ 背后那条真值链在跑）、P7 那句改回旧那半句（咬 ⑥）；P8 恒等改形（只加一对括号）**一笔不红**（尺子没钉死写法）。还原后全量 `suites=164 tests_sum=1424 failures=0`。

**⑥ 登记一条不修的措辞账**：详情那一屏的「回到漫画域就接着跑」由批 101 判式 ⑯ 钉着，本批一个字没动；
严格说它仍是宽了一格 —— 站在漫画域里那**四屏本地行**（收藏 / 历史 / 源管理 / 本机已下载）时句柄是 null 而泵不跑。
把它改准要同时动批 101 的锚点与那一屏的档名表，与本批"一行驱动泵"不是同一件事，留作后续。

**⑦ 包内复验（`_qa/desktop-exe-2026-10-13-b106/`，2026-10-13 12:05 那一趟，`try-queue-screen.bat` 同一条命令行）**：**两条都有**。
读数（stdout，GBK 解出后落在 `_qa/b106_dlines.txt`）：

```
D_队列 笔=3 跑=0 待=1 停=1 败=0 完=1 留档=无      ← 起手：那一笔还在待跑
D_队列屏 行=0 停=0 撤=0 / D_队列屏 行=3 停=0 撤=0  ← 批 105 那颗 primed 的第五档照旧先念"正在读"
D_下载 号=2130882 话=0 页=0/0 全=否 因=loadEp 失败: 无效的章节ID   ← 站在这一行，泵真的去问站方了
D_队列 笔=3 跑=0 待=0 停=1 败=1 完=1 留档=无      ← 待 1 → 0 而 败 0 → 1：那一笔被这一行接走
```

图证那一帧（标题 `#14632`，`occlusionSafe=true`）：三行都摆着，第一行 `第 1 话「第1話」 · 失败了 · 0/0 页 · 本子 2130882 · 因=IllegalStateException: loadEp 失败: 无效的章节ID`，
表头改口成「这一页上 3 笔都排完了」（待=0 那一档，与判式 ④ 同一形状），每行右边一枚「撤掉这一笔」而**没有**「停这一笔」（那一刻没在跑），
顶栏牌子同一刻念「三站 · 本机 3 笔已排完」。关窗后 `tasklist` 残留 **0** 颗。

⚠️ 那一笔落"失败"而不是往下写页，成因是**演示表那三笔的号与话号是早先按 jm 的形状手填的**（`goda_2130882_1` 里那个 `chapterId="1"` 站方不认），
不是本批的缺陷 —— 本批要的那一格（站在这一行就把活接走）恰恰被这一趟量到了。

**再补一趟现量（同一颗包，`--start-section=comic-explore` 那一趟，120s 后关窗，残留 0）**：

```
D_卡标命中 站=goda 墙=48 贴上=0 盘上=0 样例=/manga/pandajunheleisajun-hiyaku,/manga/ruoxianruochi-merryweathereypeach,…
```

⇒ **goda 的漫画号是 slug 而不是数字号**，种子里那枚 `2130882` 是 jm 那一族的形状，站方根本不认。
要把演示做成"真下得动页"，种子得把 `(号, 话号)` **一起**换掉，而话号还得从详情那一趟的读数里取 —— 登记为**下一格**：
它是种子数据的事，不动本批的接线，也不影响本批那两条读数（`待=1 → 待=0 败=1` 与图证那一帧的行尾原话）。

## 8. 下一格的题面（三趟现量，含本批自己走错的那两趟）

想拿一枚真话号去换演示种子，就得开一趟详情屏。**前两趟我把参数用错了**，那两格不是代码缺陷，写在这里是为了别让下一批再走一遍：

| 那一趟 | 命令行（包内 exe） | 读数 | 判读 |
|---|---|---|---|
| 一 | `goda --start-domain=COMIC --detail=/manga/pandajunheleisajun-hiyaku` | `D_详情` 零行 | **`--detail` 是开关不是号**（`ON = 1/true/on`，值不是那一档 ⇒ "值为关"）；号另有其参数 |
| 二 | `goda --start-domain=COMIC --start-section=comic-explore --detail=1` | `D_落点 … 详情=关(启动) 种子=detail` | 参数被正确读成"开详情那一层"，但**没有号** ⇒ 载荷设不上。`种子=detail` 与 `详情=关` 这两格本来就允许不同刻，不是打脸 |
| 三 | `goda --start-domain=COMIC --comic=/manga/pandajunheleisajun-hiyaku --detail` | `D_落点 … 详情=开(启动) 种子=detail+comic` ⇒ **到达路是通的**；紧跟着 `D_详情 源=goda 号=C: 态=没成 因=…loadInfo 失败: Remote host terminated the handshake` | ⚠️ **这一格才是缺陷**：号被读成了 `C:` |

**下一格要做的事（按第三趟那一格写）**：`--comic=` 的值形状是 `<漫画号>[/<题名>]`（`ForensicFlags.kt:126-133` 的 KDoc 明写），
而 goda / nhentai 那一族的**漫画号自己就带斜杠**（`D_卡标命中 站=goda 墙=48 … 样例=/manga/pandajunheleisajun-hiyaku,…`）⇒
按 `/` 一切就把号切成两截（本机这一趟还叠了一层 git-bash 的 POSIX 路径转换，所以念出来是 `C:`；**换成 cmd 那一侧不会转，但号仍然会在第一个斜杠处断**）。
同一族的 `--download=<漫画号>/<话号>` 与 `--read=<号>/<话号>` 吃的是同一种拼法 ⇒ **slug 号站点上这三颗种子结构性传不进去**，
而它们正是"不动鼠标就能量到详情/落盘/阅读"的那三条路（批 96 的 KDoc 明写没有它就量不到真落盘）。

**成因已经读到行级**（不用再来一趟）：`ForensicFlags.kt:568` 那一行是 `val slash = raw.indexOf('/')` —— 切的是**第一个**斜杠。
所以 `--comic=/manga/xxx` 在 cmd 那一侧（没有路径转换）会得到 `号=""` 而题名=`manga/xxx`；本机这一趟叠了一层 git-bash 的
POSIX 路径转换（`/manga/…` → `C:/Program Files/Git/manga/…`）才被念成 `号=C:`。**两形同一根**：号里不许有斜杠，而 slug 号全是斜杠。

三条候选定档（下一批拍板，别顺手就改）：
① 分隔符从 `/` 换成号里不会出现的字符（改动最小，但要同步那三颗参数的 KDoc 与既有取证脚本、以及钉它们的判式）；
② 从**最后一个**斜杠切（`substringBeforeLast('/')`）—— 可题名那一截本身也可能带斜杠，切成两截的语义就糊了；
③ 值收 URL 编码（`%2F`），解析处解一次 —— 好处是"号里有斜杠"与"号与题名之间那道斜杠"从此分得开，代价是启动脚本里那串要写编码后的形状。
⚠️ 无论选哪一档，判式都要钉"**递进去的号与屏上念出的号逐字相同**"这一条（本批这一格是 `号=C:` 被念出来才看见的 —— 读数把谎当场拆了，这就是那一行 `D_详情` 存在的理由）。
