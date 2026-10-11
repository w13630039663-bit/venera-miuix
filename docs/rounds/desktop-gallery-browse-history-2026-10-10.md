# 第八十六批 · 归档那一格改判（零新依赖）+ 图库浏览历史点亮首页「历史」那一行

**日期**：2026-10-10（同日第八十六批）
**前置**：批 85（图片落盘 + 首页「下载」那一行）、批 82（漫画阅读历史写口）、批 40（两颗共享 store 的原子写）
**排期出处**：`remaining-plan-and-reuse-2026-10-10.md` §4 的 批 86 那一行，本批把它拆成两半并调了序，理由见 §1.2。

## 1. 这一批要办的事，与两半为什么这么切

### 1.1 归档 / CBZ 那一格：**不买库**，用 JDK 自带的 `java.util.zip`

排期表 A 档第 2 行原本写的是「`net.lingala.zip4j:zip4j:2.11.6` **或** `org.apache.commons:commons-compress:1.28.0`，二选一」，
并且留了一句「jlink 侧要现量它需不需要额外 `modules(...)`」。**现量之后这一格改判**，三条读数：

1. **本仓对岸两处已经在用 JDK 那一套**，而且用途正好就是这一格要办的两件事：
   - `app/src/main/java/com/venera/compose/download/LocalComicManager.kt:13-15` —— `ZipEntry` / `ZipFile` / `ZipOutputStream`，
     CBZ 导出（`:198` 那颗 `ZipOutputStream`）与导入解包（`:232` 的 `ZipFile(archiveFile)` + `:250` `zip.getInputStream(entry)`）；
   - `app/src/main/java/com/venera/compose/sync/BackupManager.kt:29-31` —— 同三颗，`.venera` 备份的打包（`:149`）与解包（`:176`）。
   ⇒ 「解包与流式随机读整颗件」这一层根本不用造，也不用买：`java.util.zip` 就是它，且**两端同一份 JVM 标准件**。
2. **jlink 那个风险在这条路上不存在**（现量，不是推断）：`java --describe-module java.base` 里有一行 `exports java.util.zip`；
   而**交付包那颗镜像本体**（`_qa/desktop-exe-2026-10-10-b829b/venera-desktop-probe/runtime/lib/modules`，54,013,133 B）
   用 `jimage list` 数出 **43 条 `java/util/zip/*.class`**，`ZipFile` / `ZipEntry` / `ZipInputStream` / `ZipOutputStream` 四颗逐一点名在内 ——
   不需要像 `java.sql`（批 82.9 那课）那样手写进 `modules(...)`。包体现在就带着这颗能力，引第三方库才是净增。
   ⚠️ 这一笔第一遍量出来是 **0**，因为我给 `jimage list` 写的匹配串是 `java.base/java/util/zip/` ——
   那颗命令输出的路径**不带模块前缀**（前缀是单独一行 `Module: java.base`）。修正后的读数是上面那一条。
   §0 坑 ㉑ 那一族又多一例：**判式写错会给出一个像证据的假数**。
3. **多出来的那些格式我们结构性不做**：`README.md:244` 那行「EPUB / 7Z / ZIP 归档直读」写的是**结构性不做**
   （本地漫画一律以目录形态进阅读器，CBZ 导入是解包）。commons-compress 唯一比 JDK 多的就是 7z 那一档 ——
   为一格明写不做的格式引一颗库加一批传递依赖，是拿包体去买一件不发生的事。

⇒ 这一格的交付物是**一把负向尺子**（桌面构建脚本里不许出现这两颗坐标）+ 本节这段读数，而不是新代码。
桌面今天也确实没有归档读写点：唯一的 zip 消费点会是备份打包，而那一半不在本批（§1.2）。

### 1.2 备份导出打包那半格：**本批不动**，因为它是用户明确不点头的那一件

`desktop-port-scope-verdicts-2026-10-10.md:10` 写着「本轮范围 = 只成文（W0 + W1）；**W2~W6 用户明确不点头**」，
那张表 §5 的第 6 行就是「W6 | 备份导入导出（#6，桌面自持 zip，不摘 exclude） | **未点头**」。
「动手前不用问，直接开工」撤的是**计划里我自己挂的"等你说做不做"那类门**，撤不动用户亲口说过的那一个不。
所以本批把 批 86 的第二半从排期里摘出来，等那一件重新点头再做（不是我去问，是它本来就挂在"未点头"那一列）。

顺带把同一张表里已经过期的一格记下来：**W5（画廊图片落盘）今天已经随批 85 落地**，
那张 §5 表与 §4 的「下载 / 另存为 / 批量下载 | 改判：#7 做最小落盘（**未开工**，W5）」两处都还是旧话，本批回写。

### 1.3 第二半换成：图库浏览历史（原排期 批 89 的第一档）提前点亮

首页那六行今天只剩**一行**带「未实现」药丸 —— 「历史」。它的三条判据（原文抄自 S2-B 计划 §2.2，
逐字在 `DesktopGalleryHomeSections.kt:183-206` 那段注释里）是：
「存在 `gallery/data/GalleryHistoryStore.kt`（或 `data/db` 里一张 gallery 历史表）**且**注册表有 `HISTORY`**且**有内容件」。
读数：`HISTORY` 在注册表里（早就在）、缺的是那颗存储与那一屏 —— 与批 85 那行的形状一模一样。

`data/db` 里那张 `comic_history` 不是它（批 14 就为这句量过一条假话：那是**漫画阅读进度**，
桌面从批 82 起真的在写它，首页「阅读历史」那一行读的就是它，与 gallery 这一行不是同一件事）。
gallery 这一侧的浏览历史**从没存过** —— 屏上那句 reason 今天仍然成立，本批把它撤下来是因为它**变成了不成立**。

为什么新建的这颗不在 `gallery/data/` 而在 `desktop/`：原判据点名的两种存储形态都是**对岸那一侧**的候选，
而这一格没有可对岸的件 —— Android 侧根本没有 gallery 浏览历史（`README.md` 桌面那一节是这么写的，
`app/` 是受保域，为桌面的一屏去给对岸加一颗 store 不是本批的授权面）。
批 85 是同一条处理：对岸那颗 `GallerySaver` 因为 MediaStore 整颗在排除清单里 ⇒ 桌面自持一颗，
判据跟着改写并把理由留在原处（不许只写结论）。

## 2. 落点（六处）

| 层 | 文件 | 这一处干什么 |
|---|---|---|
| 存储 | `desktop/…/gallery/data/DesktopGalleryBrowseHistory.kt`（新） | 磁盘 JSON 档 + 纯函数行集（去重、排序）+ 三态读数 |
| 消费·读 | `desktop/…/gallery/ui/DesktopGalleryHistoryPane.kt`（新） | 首页那一行点进去的那一屏：表头一句 + 逐格缩略图与站名/号/时间 |
| 消费·写 | `desktop/…/gallery/ui/DesktopGalleryDetailPanel.kt` | 浮层起来那一刻记一行（`LaunchedEffect(post.uid)`） |
| 注册表 | `desktop/…/gallery/ui/DesktopGalleryHomeSections.kt` | sealed 加 `GalleryHistory`、那一行 `content` 转正、`reason` 撤、pane 派发加一支 |
| 穷尽面 | `DesktopGalleryDomainPanes.kt`（四处 `when`）+ `DesktopGallerySettingsPane.kt`（一处） | 补新分支，`sealed` 换了 `when` 就得跟着换 |
| 集成 | `VeneraDesktop.kt` → `VeneraDesktopApp.kt` → `DesktopDomainBody` | 建一颗 store，沿 `saveDir`（批 85）同一条路递进浮层与那一屏 |

## 3. 设计里三条不显然的定案

### 3.1 键必须是「站 + 号」两枚合起来，不是号

三站各自有自己的编号空间，`#12349956` 在 safebooru 与 gelbooru 是两张不同的图。
这条不是假想风险：本仓记过一枚同族的债 —— 桌面 peek 的复合键**两端不同轨**（一头传空串），
档位在而永远命中不了，单测全绿（`project-implemented-but-unwired` 那条的近亲）。
所以判据里有一条**专门拿同号异站喂它**（§4 的 ②），写侧与读侧都要过。

### 3.2 存的是「看过」这一件事，不存整张 post

一行只落四样：站键、号、预览档地址、看过时刻。**不**存原图地址、宽高、标签、分级。
理由两条：
- 存了就得负责它过期（站方地址会变），而这一屏要的只是"一眼认得那是哪张"；
- 想从历史重建整张 `GalleryPost` 是另一件事（要它就得存下全部字段），那一层今天不做，
  屏上也就不摆任何"点开回原卡"的动作 —— 摆一枚只能带回一屏缩略图的按钮，就是本仓最忌的假开关。

磁盘字段名跟对岸那两颗 store 同口径（蛇形 + `@SerialName`，见 `GalleryArtistFollowsStore.kt:31-35` 的
`site` / `name` / `added_at`），这一条由判据扫对岸那颗文件现取，不写死在这里。

### 3.3 不设条数上限

对岸那两颗同类件（`GalleryFavoritesStore` / `GalleryArtistFollowsStore`）**都没有上限**（现量：三颗文件里
`take(` / `MAX_` / `limit` 零处），所以这里也不造一个新数字 —— 按「观感尺寸用现成口径，不造新数字」那条。
代价写清楚：§6 帧 A 那一行**实测 147 字节**（站方预览地址占 79，去掉地址 68），一万条形如 1.5 MB 的 JSON，
读它一次是同步的（与那两颗同一形状：构造时同步读一次，为的是第一帧就是对的）。
这一格挂在手册 §17 的待问清单里，不由本批拍。（此处从前估的「≈90 字节」是写代码前的推测，帧 A 落地后被现量推翻，按现量改。）

## 4. 现量与 RED→GREEN

### 4.1 三笔现量（写代码之前）

| 量什么 | 读数 | 这一格因此定了什么 |
|---|---|---|
| 归档那颗件到底要不要买 | `LocalComicManager.kt:13-15` 与 `BackupManager.kt:29-31` 两处 import 的就是 `java.util.zip` 那三颗；`java --describe-module java.base` 输出里有 `exports java.util.zip`；交付包 `runtime/lib/modules`（54,013,133 B）里 `jimage list` 数出 **43 条 `java/util/zip/*.class`** | 零新依赖、零 `modules(...)` 补票、包体 0 增量。⚠️ 第一遍 `jimage` 数出来是 **0** —— 我把匹配串写成带模块前缀的 `java.base/java/util/zip/`，而那颗命令输出的路径不带前缀（§1.1 第 2 条记着这次自审） |
| 首页那一行差的是哪一层 | 六行里只有 `section-history` 带 `reason`；`data/db` 里那张 `comic_history` 是漫画阅读进度（批 82 起桌面真在写）；`gallery/data/` 与 `data/db` 里都没有 gallery 浏览历史的存储 | 三条判据缺两条（存储 + 那一屏），且"存储"这一条的两种点名片段形态都在对岸 ⇒ 补第三种：桌面自持（§1.3） |
| 同类件有没有条数上限 | `GalleryFavoritesStore` / `GalleryArtistFollowsStore` / `GalleryArtistAvatarStore` 三颗文件里 `take(` / `MAX_` / `limit` **各 0 处** | 不造新数字 ⇒ 不设 cap（§3.3），代价与待问那一格写在 §7 |

### 4.2 两枚 RED

第一枚 RED 是**编译级**的（新 API 还不存在），逐字：

```
e: DesktopGalleryBrowseHistoryTest.kt:63:9 Unresolved reference 'DesktopBrowseView'.
e: DesktopGalleryBrowseHistoryTest.kt:68:20 Unresolved reference 'desktopBrowseRowsViewed'.
e: DesktopGalleryBrowseHistoryTest.kt:119:21 Unresolved reference 'DesktopGalleryBrowseHistory'.
```

第二枚 RED 是**那颗件公开了 internal 类型**：`'public' property exposes its 'internal' type argument 'DesktopBrowseView'`（两行，`rows` 与 `recordView`）。
本仓记过那一课（public 暴露 internal 返回类型＝编译错误，整颗退 internal 就够，测试源集够得着），
所以改的是那颗件的可见性而不是把行类型抬成 public —— 抬上去等于给桌面主源多一颗公开面。
顺带把判据 ⑮ 的锚点从 `startsWith("class …")` 改成 `startsWith("internal class …")`：**可见性一改，扫源码的尺子跟着搬家**（坑 ㊞ 的近亲）。

### 4.3 第一次全量：1097 条里红 10 条，其中 7 条是旧尺子在报真话

| 红的用例 | 是谁的错 | 怎么收的 |
|---|---|---|
| `DesktopGalleryHomeSectionsTest` ③④ 与 byKey 那一条 | 旧账：内容项数写 5、未实现项数写 1、byKey 拿「历史」当 null 内容的样例 | 项数改 6/0 并逐行点名；④ 退成"有未实现的行才逐颗点名"，并当场写明它**今天没有对象**、重新有对象的时刻由 `DesktopGalleryHistorySeedTest` ② 报；byKey 那一条不再拿 null 样例，改钉"六行都取得到内容件" |
| `DesktopStaleClaimsTest` ⑥⑦ 与「历史那行不再说没有存储」 | 旧账：枚数 1、屏上那句缺席理由还在 | ⑥ 改成"再长出一行未实现的要逐颗点名 + 三枚对照（排行榜／下载／历史都不许回未实现）"；⑦ 加禁串「一行未实现」并把现数折成中文数词比（今天要求文件里写「零行未实现」）；那第三枚只留它**独有**的两半（`data/db` 在编译面 + `HistoryDaoTest` 在），点名搬去哪两颗尺子了 |
| `DesktopComicHistoryScreenTest` 图库域那一行的理由 | 旧账：那一枚钉的是"那一行仍未点亮"，它自己写着「点亮时这条判据要跟着撤」 | 按它自己那句撤，并改成只这一枚管得着的那条：**两行历史各读各的那一份**（`comic_history` 那张表 vs 那份 JSON 档，不许并一颗） |
| 本批自己的 3 条（一千条不截断／读数签名／`LaunchedEffect` 枚数） | **全是我的**：① 样例喂了 `id=0`，而 `id=0` 正是判据 ⑪ 要摘掉的坏行 ⇒ 数出 999；② 签名钉的是整行相等，而实现末尾多一个 ` =`；③ 数 `LaunchedEffect(post.uid) {` 时漏数了批 35 那枚 `--reverse` 种子 | 样例改成号从 1 起并写明为什么；签名钉法换成整串前缀（仍钉死形参表）；枚数改 4 并在消息里点名那四处 |

⇒ 全绿那一笔：**1097 条 / 138 颗类 / 0 失败 / 0 错误**（`node _qa/three_way_counts.js` 三方对得上，145 份 XML）。
`git diff --stat -- app/` **空**（对岸一字未动）；共享面 86 颗 / 11,378 行、14 颗点名排除一字未动（本批没摘也没加排除项）。

## 5. 扰动台架

夹具 `_qa/b86_perturb.js`（15 笔）+ `sh _qa/perturb_driver.sh b86 15`，逐字汇总在 `_qa/b86_perturb.summary.txt`。
`check` 现跑读数：**「15 笔各命中一处，可跑」**（`_qa/b86_check.txt`）—— 每一笔的锚点都只咬一处，先排掉"改坏了两处"和"那一句根本不在文件里"两种假台架。

| 笔 | 改坏什么 | 要求 | 咬到的尺子 |
|---|---|---|---|
| P1 | 复合键塌成一枚（拿号当唯一键） | 红 | 1 条：`同一个号在两个站是两行` |
| **P2** | **等价改写**：站比较换成 `routeKey` 比较（语义不变） | **不许红** | **0 条 —— 一笔没红，这是正确输出** |
| P3 | 落盘存原图地址（欠一笔过期账） | 红 | 2 条：`磁盘那一行只有四个键`、`写盘之后新开一颗读回同一份` |
| P4 | 磁盘键名换驼峰（与对岸两颗 store 断轨） | 红 | 3 条：四个键那条 + `坏行当场摘掉而不是念半截` + `时刻那个键名与对岸同形` |
| P5 | 坏档不留档直接删 | 红 | 1 条：`读不开的那份先留档再开空档` |
| P6 | 缺档当坏档念（把"没有"说成"读不到"） | 红 | 1 条：`没有那份文件时不报错` |
| P7 | 改名不带覆盖选项（Windows 上第二笔起静默丢） | 红 | 3 条：`第二次写不丢`、`新开一颗读回同一份`、`原子写用的是覆盖那条路` |
| P8 | 定序写成正序 | 红 | 5 条：一千条不截断 + 只留一行刷新时刻 + 读回顺序 + 同刻定序 + 两行各认自己的站 |
| P9 | 空的那一档念成"读不开" | 红 | 1 条：`表头分得清没看过与看过几张` |
| P10 | 表头丢了最近那一格的时刻 | 红 | 1 条：同上一条 |
| P11 | 行里念 `routeKey` 而不是站名 | 红 | 1 条：`每一行念站名与号` |
| P12 | stdout 那句把地址念出去（站方地址常带 `api_key`） | 红 | 1 条：`读数里不许出现地址` |
| P13 | 守卫反向（挡住的那一档才记） | 红 | 1 条：`挡住的那一档不记进历史` |
| P14 | 那一屏自己拼一句表头（读数没了消费点） | 红 | 1 条：`读数都有消费点` |
| P15 | 首页那一行改回未实现（假开关回魂） | 红 | 8 条：`DesktopGalleryHistorySeedTest` 3 条 + `DesktopGalleryHomeSectionsTest` 2 条 + `DesktopStaleClaimsTest` 2 条 + `接线点总数` 那条 |

**14 笔要求红 → 全红，共 30 条断言红；P2 要求不红 → 确实一笔没红。** 15 笔逐笔还原后的全量：`EXIT=0` / `suites=138 tests_sum=1097 testcase=1097 @Test=1097 files=145 failures=0`。
耗时：`check` 21:25:56 起，P15 还原 21:48:42，收尾全量 21:49:58 ⇒ **15 笔 + 收尾全量共 24 分 02 秒，每笔 ≈ 1 分 36 秒**（每笔跑的是全量 `:desktop:test`，不是单类）。

三笔台架本身要记的：
- **P2 是本批唯一一笔"要求不红"**，钉的是"等价改写不许误伤"。上一批的教训是尺子只钉了措辞没钉行为 ⇒ 这一笔专门把行为不变的写法拿过来喂一遍，11 颗尺子全绿才算数。
- **P13 第一版锚点被否**：原来想写的 `to` 串是 `if (!masked)` 的整句改写，但那串本就是文件里另一处的子串，`check` 会数出两处 —— 改成只翻守卫那两个字（`!masked` → `masked`），一处命中。这是"锚点会被同屏另一处合法写法喂饱"的老坑，`check` 这一步就是拦它的。
- **P15 一笔咬三颗尺子文件**，正是设计意图：那一行一旦回退，界面上的那句旧话、首页注册表、以及"未实现的行数说明"三本账会同时不对，谁也不许单独活着。

## 6. 图证（两帧，跨进程）

数据目录隔离在 `LOCALAPPDATA=D:\venera-compose\_qa\b86_local`，两趟都 `--no-daemon` 起（挂 daemon 时 env 递不进被 fork 的那台 JVM，会写脏真人那份 `%LOCALAPPDATA%\venera`）。
**本批没有新造取证参数** —— 写的那一路走 `--detail=1`（第十一批那枚"起窗后自动点开第一张卡"），读的那一路走 `--start-section=section-history`（第十批 ⑤ 那枚"哪一行先选中"），两枚都是现成的。

### 帧 A · 点开一张卡就记一行（pid 47556）

`./gradlew --no-daemon :desktop:app -Pproxy=127.0.0.1:7890 -Pdetail=1`，stdout 逐字（节选）：

```
D_数据目录 D:\venera-compose\_qa\b86_local\venera（写入回读已验）
D_落点 域=图库(启动) 行=每日热门 浮层=关(换域或换页自动收) 查看器=关 详情=关(启动) 阅读=关(启动) 种子=detail
D_看过 #1269141 站=yande.re 共 1 行
```

那一帧上：右侧浮层摆着 yande.re #1269141（与 stdout 那枚号逐字同一颗），左侧那六行里「历 历史」**已经不带「未实现」药丸**（与批 85 之前那两帧的差别就在这一格）。
盘上那颗：`_qa/b86_local/venera/gallery_browse_history.json`，**149 字节**，内容逐字：

```
[{"site":"yandere","id":1269141,"thumb":"https://assets.yande.re/data/preview/69/71/6971422690395a29bc1e6b7d0a3f0031.jpg","viewed_at":1791638066725}]
```

四个键、存的是 `preview` 那一档地址（不是 `data/file/` 那一档）—— §3.2 那条口径在真机上留了读数。

### 帧 B · 换一趟进程去读它（pid 46020）

`./gradlew --no-daemon :desktop:app -Pproxy=127.0.0.1:7890 -PstartSection=section-history`，stdout 只有落点那两行、**没有 `D_看过`**（grep 计数 0）⇒ 那一屏真的只读不写。
那一帧上：表头「本机看过 1 张 · 最近 2026-10-10 21:14」，下面一格摆着**那张兔玩偶的缩略图**（网络现取，代理通）+「yande.re · #1269141 · 2026-10-10 21:14」。

两帧合起来是这一批的闭环：**写的那一笔在 A 进程，看得见它的是 B 进程** —— "落盘"不是同一颗 JVM 里的一句自述。
收尾：两帧之后 Venera 窗 **0 枚**（`powershell -File _qa/b64_windows.ps1` 读数 `count=0`），真人那份 `%LOCALAPPDATA%\venera` 未碰。

## 7. 下一件与欠的格

### 7.1 下一件

**批 87 · 阅读器翻页那一档**（`Pager` + 音量键，B 档，不引依赖）。排期表 `remaining-plan-and-reuse-2026-10-10.md` §4 现第一件。

### 7.2 本批只做了"改判"的那一格 —— 别把它读成已落地

归档 / CBZ 直读：**本批落地的是"不买库"这个结论**（§1.1 三条现量 + `DesktopGalleryHistorySeedTest` ⑫ 那把负向尺子钉住构建脚本与版本目录里不许长出 zip4j / commons-compress）。
**zip 读侧那一圈代码一行都没写** —— 桌面今天打不开 `.zip`/`.cbz`。下一件如果做它，落点是 `java.util.zip`（`ZipFile`/`ZipInputStream`）+ 漫画详情那一屏的章节来源，包体 0 增量、零 `modules(...)` 补票（那格已量掉）。

### 7.3 备份导出打包：退回等点头，不算本批的账

裁决表 `desktop-port-scope-verdicts-2026-10-10.md` §5 那行记着 **W6 用户明确不点头**。
「动手前不用问」撤掉的是我自己给自己设的开工门，撤不掉用户亲口说的"不"。所以这一格从排期表 批 86 那一行摘出来，回到「挂着等点头」那一张表，不在本批面上。

### 7.4 本批 knowingly 留下的三格

| 格子 | 现状 | 为什么现在不补 |
|---|---|---|
| 历史那一屏的行点不开 | 每一行只念「站名 · #号 · 时刻」+ 缩略图，**没有** `onClick`（`DesktopGalleryBrowseHistoryPane.kt:100` 那颗 `BrowseHistoryRow` 形参表里只有 `row`） | §3.2：档里只有四样，重建不出整张 `GalleryPost`；摆一枚只能带回一屏缩略图的按钮就是假开关。要补就得先把存储面扩到"够重建"，那是另一批 |
| 不设条数上限 | 一万条形如 1.5 MB（§3.3 现量单行 147 字节），构造时同步读一次 | 对岸三颗同类件都没有上限，不造新数字；这一格挂在手册 §17 待问清单 |
| 交付包没为这一批重打 | 现用包仍是 `_qa/desktop-exe-2026-10-10-b829b/`（批 83／83.1／85／86 四批未重打） | 本批新落的那一路只在**被递进种子时**才改变屏上那一格：写侧由 `--detail`（或 `--reverse`/`--copy`/`--save` 任一枚）带出浮层，读侧由 `--start-section=section-history` 直接落在那一屏。开发跑用 `-Pdetail=1`（`desktop/build.gradle.kts:347` 翻成程序参数），包内给 `venera-desktop-probe.exe` 直接递同一串程序参数（批 78 那趟就是这么递的）。⇒ 正解是"下一批重打包时顺手各起一趟，核包内 `D_看过` 与 `数据根/gallery_browse_history.json` 那一颗"，不是现在重打一遍 |

⚠️ 上一批（批 85）在 §6 写的"顺手 `-Psave=1` 起一趟"说的是开发跑那一侧；包内那一侧参数形态是程序参数 `--save=1`。两批合起来，下一趟重打包要各递 `--save=1`、`--detail=1`、`--start-section=section-history` 三趟。

