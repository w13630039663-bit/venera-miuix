# 批 107 · 四颗取证种子的号段吃不下 slug 号（方案，2026-10-13）

上一批是批 106（让「下载队列」那一行自己驱动泵，rounds `desktop-comic-queue-row-drives-pump-2026-10-13.md`）。
本批的题面是那一批 §8 顺手量出来的，**所有行号与读数都是 2026-10-13 现量的**（`awk NR` / 包内 stdout 可复核）。

## 0. 缺陷那一形（读数，不是推测）

包内那一趟（`_qa/desktop-exe-2026-10-13-b106/`，命令行 `goda --start-domain=COMIC --comic=/manga/pandajunheleisajun-hiyaku --detail`）：

```
D_落点 域=漫画(启动) 行=探索 … 详情=开(启动) … 种子=detail+comic
D_详情 源=goda 号=C: 态=没成 因=IllegalStateException: loadInfo 失败: Remote host terminated the handshake
```

`详情=开(启动)` 说明**到达路是通的**（这一度被我误判成"slug 站上详情屏没有不动鼠标的到达路"，那是参数用错：`--detail` 是开关，号另有 `--comic` —— 已在批 106 §8 就地改判）。
真的那一格是 **`号=C:`**：递进去的号与屏上念出的号**不是同一串**。

`C:` 这一形是 git-bash 的 POSIX 路径转换叠了一层（`/manga/…` → `C:/Program Files/Git/manga/…`），**但换成 cmd 那一侧也不会对**：
`ForensicFlags.kt:568` 那一行是 `val slash = raw.indexOf('/')`，切的是**第一个**斜杠，而 goda 的号自己就以 `/manga/` 开头 ⇒ 号段恒为空。

## 1. 射程：六处 `indexOf('/')` 里只有四颗中招（逐枚问"第一段装的是哪一样东西"）

| 参数 | 值形状（现量行号） | 第一段装的是 | 中招？ |
|---|---|---|---|
| `--comic` | `<漫画号>[/<题名>]`（`:568`） | **漫画号** | ✅ 号是 slug 就断 |
| `--download` | `<漫画号>/<话号>`（`:590`） | **漫画号** | ✅ 且 `slash <= 0` 那一档直接把整颗参数判死 |
| `--delete` | `<漫画号>/<话号>`（`:659`） | **漫画号** | ✅ 同上 |
| `--read` | `<漫画号>[/<话号>[/<章名>]]`（`:765` 起，用的是 `raw.split('/')`） | **漫画号** | ✅ `parts[0]` 恒为空串 |
| `--artist` | `<站点>/<标签>`（`:533`） | 站点 routeKey | ❌ 不是号，别顺手改 |
| `--ranking` | `<档>/<周期>`（`:954`） | 枚举名 | ❌ 同上 |

⚠️ 这一列的判据是"**第一段装的是不是漫画号**"，不是"有没有用斜杠切"。同一族里 `--artist` / `--ranking` 的第一段是枚举或站键，
给它们也加解码就是把 A 站的形状套到 B 站头上（本仓那一族栽过：同名字段≠同语义）。

## 2. 定档：**先按裸斜杠切，再把每一段百分号解码**

```kotlin
/** 号段里要放斜杠就写 `%2F`；切分只看**裸**斜杠，所以数字号那一族一字不动（向后兼容）。 */
private fun desktopForensicSegment(raw: String): String =
    raw.replace("%2F", "/").replace("%2f", "/").replace("%25", "%")
```

- `--comic=%2Fmanga%2Fpandajunheleisajun-hiyaku` ⇒ 没有裸斜杠 ⇒ 号段整串解成 `/manga/pandajunheleisajun-hiyaku` ✅，题名按既有规矩"用号充"；
- `--download=%2Fmanga%2Fxxx/1686882001` ⇒ 裸斜杠切在号与话之间 ✅；
- `--read` 的 `<号>/<话>/<章名>` 三档：号是第一段（编码后不含裸斜杠）⇒ 切分次序不变，而**章名那一截仍允许带斜杠**（`parts.drop(2).joinToString("/")` 那一行不许动）。
- ⚠️ `%25` 必须**最后**再解（否则 `%252F` 会被解两次，把"字面的 `%2F`"读成斜杠 —— 同一族"解码次序就是语义"那一形）。

**为什么不选另两档**：① 换分隔符（如 `:`）要同时动四颗参数的 KDoc、屏上那几句"要写成 …"的提示、既有取证脚本与钉它们的判式，牵面比这一批大；
② `substringBeforeLast('/')` 看着省，但 `--read` 的第三段（章名）本来就允许带斜杠，从后切会把章名劈成两截 —— **它解不了"号与话之间那道斜杠"与"号自己那道斜杠"同形这一件事**。

## 3. 接缝现量（下一批开工照这张表动）

| 要动的地方 | 现量（2026-10-13） |
|---|---|
| `--comic` 的切分 | `ForensicFlags.kt:568-576`（`indexOf('/')` + 号段空那一档的 stdout） |
| `--download` | `:590-597`（`slash <= 0 \|\| slash == raw.length - 1` 那一档就是 slug 号的死因） |
| `--delete` | `:659-666`（同形） |
| `--read` | `:765` 起（`val parts = raw.split('/')`，`parts[0]` 号、`getOrNull(1)` 话、`drop(2).joinToString("/")` 章名） |
| 既有判式（会跟着搬家） | **现数：10 颗测试文件吃这四颗种子**（`DesktopComicSeedLandingTest` / `DesktopComicDownloadSeedTest` / `DesktopComicDeleteSeedTest` / `DesktopComicReadSeedTest` / `DesktopComicReadPageSeedTest` / `DesktopReaderPagedSeedTest` / `DesktopReaderModeSeedTest` / `DesktopComicReaderWiringTest` / `DesktopComicDetailWiringTest` / `DesktopComicCoverChainTest`）⇒ 开工第一件事是把它们的实参形状逐枚过一遍（改解码不该让任何一枚跟着改期望值，但"号里带 `%`"那一族最容易踩到拼接） |
| 屏上/stdout 那几句提示 | 每一颗的"要写成 `<漫画号>/<话号>`，例如 1461316/1461318" —— 改完要跟着念出 `%2F` 那一档，否则参数对了而提示还在教错的 |

## 4. 判式草案（每条答得出"改哪一行会红"）

1. **行为**（主钉子）：`comic(listOf("--comic=%2Fmanga%2Fabc"))` 回值的 `comicId` **逐字等于** `/manga/abc`；同一颗判式对 `--comic=12345` 断言 `12345`（两形各一枚，只喂一种会被"恒解/恒不解"放过）；
2. `download` / `delete`：`%2Fmanga%2Fabc/9` ⇒ 号=`/manga/abc`、话=`9`；而 `1461316/1461318` 一字不变（向后兼容那一枚）；
3. `read`：`%2Fmanga%2Fabc/1/第 3 话/特别篇` ⇒ 号解出斜杠、话=`1`、**章名仍是 `第 3 话/特别篇`**（`drop(2).joinToString("/")` 那一行不许被顺手改掉）；
4. **解码次序**：`%252F` 解出来是字面 `%2F` 而不是斜杠（钉这一枚是因为它只能靠样例过 —— 单靠"两两不同"的判式会被自己另一格喂饱）；
5. **负向**：`--artist` 与 `--ranking` 那颗文件里 `desktopForensicSegment(` 出现次数为 **0**（别把 A 站形状发给 B 站）；
6. 提示那几句要跟着改准：四颗的"要写成 …"里 `%2F` 那一档念得出，而 `--artist` / `--ranking` 的提示一字不变（同一批里两形各一枚，防"顺手全局替换"）；
7. `D_取证参数` 那几行仍念得出**解完之后**的号（不是原始串）—— 本批这一格就是靠 `号=C:` 与递进去的串不同才被抓到的，判式要把它钉成"递进去的号与念出的号逐字相同"。

## 5. 台架（预算 6-8 笔）

- 把 `%2F` 那一档整块摘掉（主钉子必红）；
- 解码次序反过来（`%25` 先解）—— 只有判式 ④ 咬得住；
- 把 `--read` 的 `drop(2).joinToString("/")` 换成 `getOrNull(2)`（章名带斜杠那一格必红）；
- 给 `--artist` 也接上那颗助手（判式 ⑤ 咬 —— 越界那一形）；
- 四颗里漏改一颗（逐颗各一笔：`--download` 漏、`--delete` 漏、`--read` 漏、`--comic` 漏）—— 这一族就是"同一件事改了一半"的钉子；
- 屏上/参数提示没跟着改（判式 ⑥ 咬）。
- ⚠️ 笔形六条纪律照旧（终点可编译、起终不互为子串、删除型用注释戳、终点不与同文件已有字面量同串、不用 `if (false` 死支、要求不红那笔自带身份戳）。

## 6. 取证种子与包内复验

**不引新参数**：这一批改的就是种子参数本身。复验走批 106 那条路：
`--comic=%2Fmanga%2F<真 slug> --detail` 那一趟要念出 `D_详情 源=goda 号=/manga/…`（**与递进去的逐字相同**）而 `态=上屏 话=N 话号=…`；
拿到真话号之后，`--download=%2Fmanga%2F<号>/<话号>` 那一趟才量得到"slug 站上整话真落盘"这一格（今天这一格在交付物里是**空白**，
而批 96 的 KDoc 明写没有这颗种子就量不到真落盘 —— 也就是说：这一批补的不是便利，是**一条从没被量过的路**）。
演示表种子（`_qa/b105_seed/download_tasks.json`）跟着换成这一组真号，包内那一趟就该从「排队中」走到「正在下」并一页一页涨。

## 7. 代价档

**中低**：一颗私有助手 + 四颗判读各接一跳 + 提示那几句改准 + 判式 7 条 + 台架 6-8 笔 + 包内两趟复验。
不动引擎、不动落盘那一路、不引新参数，也不碰 `--artist` / `--ranking` 那两颗（§1 那一列写清了为什么）。

## 8. 落地账（2026-10-13 实施时现量；**两处与 §2/§4 的定档不同，都按证据改判**）

**① 只解「号那一段」，不是"每一段"（§2 那句"再逐段百分号解码"就地改判）。**
实施时定形的理由是：会含斜杠的只有漫画号（题名是显示文字、话号是站方 id、章名那一截本来就**允许**带裸斜杠并被
`parts.drop(2).joinToString("/")` 原样拼回）。多解一道就是把"字面的 `%2F`"读成分隔符 —— 而判式 ⑤ 要数 `desktopForensicSegment(` **恰五处**
（四跳加定义），"每段都解"会把它推到七八处，那颗计数尺子当场作废。落成的形状：

```kotlin
private fun desktopForensicSegment(raw: String): String =
    raw.replace("%2F", "/").replace("%2f", "/").replace("%25", "%")
```

**② 判式 ⑤ 第一遍咬到了自己**（台架纪律第 11 条的近亲，本仓第六次）：那颗助手长在 `fun artist(` 与 `fun comic(` **之间**，
于是"artist 那一格里 `desktopForensicSegment(` 零处"这一枚断言把助手的定义行算了进去 ⇒ 红。
修法不是放宽断言而是**换切段右界**（`substringBefore("private fun desktopForensicSegment")`）—— 判式的期望值一字没动。

**③ 已跑完的**：RED 6/6 全红在断言（不是编译面）⇒ 实施 ⇒ 全量 **1430 条 / 165 类 / 172 颗文件 / 0 失败**（`BUILD SUCCESSFUL`），
`three_way_counts.js` 三方对得上，`app/` 受保域 0 行改动。新尺子 `DesktopForensicSeedSlugIdsTest` 6 条已登记进目录与手册 §16 行 161。

**④ 还没跑的（下一段接着做，别当已验）**：
- **台架**：✅ 已跑（`_qa/b107_perturb.js`，**6 笔全咬**，逐笔只跑 `DesktopForensicSeedSlugIdsTest` 那一颗类约 25s/笔 —— 这一批不走 `perturb_driver.sh` 是因为全量 95s × 6 会超出一次前台命令的时间窗，而六笔的咬合面全在那一颗类里，这条取舍写在台架文件的头注里）：
  P1 摘掉解码 → 红 4 颗（①②③）；P2 `%25` 先解 → 红 1 颗（④，正是为这一笔立的那枚）；P3 四颗里漏改 `--read` → 红 2 颗（③ 与 ⑤ 的计数）；
  P4 越界给 `--artist` 也接上 → 红 1 颗（⑤）；P5 章名只取第三段 → 红 1 颗（③）；P6 提示撤掉 `%2F` 那一档 → 红 1 颗（⑥）。
  逐笔还原后 `check` 念 `ok 共 6 笔`、marker 归 0；随后 `:desktop:cleanTest :desktop:test` 强制复跑全量 **`BUILD SUCCESSFUL`**（1m37s），
  `three_way_counts.js` 三方对得上、`check_ruler_catalog.js` **0 处漂移**、`app/` 受保域 0 行改动。
- **交付包**：✅ 已打 —— `_qa/desktop-exe-2026-10-13-b107/`（`createDistributable --offline --rerun-tasks` = **11 actionable tasks: 11 executed**；`check_pkg_jar_sha.js` 念「两颗都对得上 ⇒ 交付物与当前源码同步」；四枚启动器由 `_qa/b106_make_launchers.js` 生成，ASCII-only + CRLF 当场断言）。
- **包内复验第一趟**：✅ **通过**（同一颗包，`goda --start-domain=COMIC --comic=%2Fmanga%2Fpandajunheleisajun-hiyaku --detail`，150s 后 `taskkill`，残留 0）：

```
D_落点 域=漫画(启动) 行=探索 … 详情=开(启动) … 种子=detail+comic
D_详情 源=goda 号=/manga/pandajunheleisajun-hiyaku 态=上屏 话=15 简介=29字 封面=有 话号=19777@1253812,19777@1390922,19777@1390921,19777@1804660,19777@1804661
```

  ⇒ **屏上念出的号与递进去的那一串逐字相同**（批 106 那一趟的 `号=C:` 已被这一格替掉），而 `态=上屏 话=15` 顺带交出五枚真话号（形状是 `<本子号>@<章节号>`，也带 `@` 而不带 `/`）。
- **还没跑的**：
- **包内复验第二趟**：拿上面那枚真话号跑 `--download=%2Fmanga%2Fpandajunheleisajun-hiyaku/19777@1253812` —— 那一格（slug 站上整话真落盘）今天**仍是空白**，而批 96 的 KDoc 明写没有这颗种子就量不到真落盘；
  ⚠️ 记账口径：这一趟已在 2026-10-13 04:5x 起跑（日志 `_qa/b107_dl.log`，跑完即 `taskkill` 并数盘上页数），**若本节没有补上读数，就是本轮在记账前中断** —— 以那份日志为准重读一遍再落账，别把"跑过"当"验过"。
  **第一趟的读数（已到手，两格都是真话）**：

```
D_取证参数 --download 起窗后下载整话（号=/manga/pandajunheleisajun-hiyaku 话=19777@1253812 走的是长按那一条路）
D_取证参数 --download=/manga/pandajunheleisajun-hiyaku/19777@1253812 没有配套的 --comic ? 本轮不开详情那一屏，那一话没种进去（不下载）
盘上页数=0 清单=0  关窗残留=0
```

  ⇒ ① **解码那颗在 `--download` 上也已经念对了号**（号段整串 `/manga/…` 而话号 `19777@1253812` 分得清清楚楚）—— 本批的主钉子在这一格就算过了；
  ② 但那一趟**没落盘**，成因不是缺陷而是**那颗种子要求配套 `--comic=`**（它要先开详情那一屏才种得进那一话），而这一句拒绝是**当场念出来的**（不是静默少做事）；
  ③ 正解命令行：`goda --start-domain=COMIC --comic=%2Fmanga%2F<slug> --download=%2Fmanga%2F<slug>/<话号>` —— 这一趟在 05:05 另起（日志 `_qa/b107_dl2.log`），**读数未落入本节即代表它还没被读过**，下一段先读那份日志再落账。

**⑤ 第二趟的读数（05:09 落账，`--comic` 与 `--download` 配套那一趟，200s 后 `taskkill`，残留 0）**：

```
D_下载种子 号=/manga/pandajunheleisajun-hiyaku 话=19777@1253812 落在=第 1 话 目录=15 话
D_队列 笔=0 跑=0 待=0 …      ← 起手：表还空
D_队列 笔=1 跑=0 待=1 停=0 败=0 完=0 留档=无   ← 那一笔排上了，而跑=0
盘上页数=0 清单=0
```

⇒ **本批那颗解码在真交付物上端到端过了**：slug 号与带 `@` 的话号被分开、被认下来、还在 15 话里**落对了第 1 话**（`--download` 那颗从前在 slug 站上根本进不来）。
⚠️ **同时量出一条独立的真缺陷（不属本批射程，另立一批）**：那一圈泵的形状是 `val task = nextPending(root) ?: break` ——
**表在某一瞬为空时那一圈就退出**，之后新排进来的一笔不会被接走（这一趟就是它：起手 `笔=0` ⇒ 泵 break ⇒ 种子随后排上 ⇒ `待=1` 而 `跑=0` 到关窗）。
批 106 包内那一趟没撞上它，是因为那份演示表在起窗前就有活（泵一起来就有的跑）。
下一批的题面：泵要在"这一圈没活"之后仍能被新入队唤醒（`awaitPending` 那一类的信号，或让 `enqueue` 自己踢一圈），
且不许把它改成"每 200ms 轮询一次表"那种假活（多一处开销而屏上说不出新事实）。
- 演示种子（`_qa/b105_seed/download_tasks.json`）还带着 jm 形状的数字号，要换成这一组真 `(号, 话号)`。
