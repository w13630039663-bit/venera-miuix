# 批 80 · 漫画卡点开进详情那一屏（章节表 + 简介 + 那枚收藏）

日期：2026-10-10　线：桌面端（`desktop/` + `engine-probe/`）　**`app/` 零改动**

## 0. 这一批补的是哪一格

§2 表里"读也没有写也没有"那一族里最贵的一格：漫画域在桌面上**只有列表**。
封面卡不带任何点击（只有卡角那枚「收藏」点得动），而取数层那颗 `EngineSession.details`
（`engine-probe/…/EngineSession.kt:227`）**整条早就在**、`loadInfo` 也在 S0 探针里真跑过 ——
就是没有一个调用点。批 80 把这一跳接上：点封面 → 详情那一屏（题名 + 简介 + 章节表 + 收藏）。

阅读那一屏（`pages` / `pageImage`）不在本批，是批 81；历史写口是批 82。

## 1. 一处真缺口是这一批自己暴露的

`--comic=` 那颗取证种子的**判读层与消费点都是全的**（`ForensicFlags.comic()` 在 2026-10-09
由并行那条线接好，`VeneraDesktop.kt` 也递进了应用根），**但构建脚本那一跳漏了**：
`desktop/build.gradle.kts` 的 `listOf(...)` 里根本没有 `gradleProperty("comic")`。
⇒ `-Pcomic=…` 是"不报错也不做事"，而"那一屏拍不到图"会被读成"那一屏起不来"。
这是同一颗纪律**第七次**踩到（`detail` / `artist` / `ranking` / `reverse` / `reverseFile` / `viewer` 各漏过一次），
所以判据 ④ 直接把那一枚拼串整串钉住（`"--comic=$it"`），配 `:desktop:app` 那颗粒任务当对照组。

## 2. 落点

| 件 | 改了什么 |
| --- | --- |
| `engine-probe/…/ComicLoadInfo.kt`（新） | `parseComicLoadInfo(comicId, env)` 纯函数 + `ComicInfo(title, description, episodes)`。`details` 从 `Pair<String, List<Episode>>` 改成它 |
| `engine-probe/…/EngineSession.kt` | `details` 只剩"发一次 JS"，解析挪进上面那颗纯函数 |
| `desktop/…/gallery/ui/DesktopComicDetail.kt`（新） | `DesktopComicTarget` + `ComicDetailState` 三态 + `desktopComicDetailReading` 读数 + **装载件 `DesktopComicDetailPane` 与展示件 `DesktopComicDetailContent` 分两颗** |
| `desktop/…/gallery/ui/DesktopComicPane.kt` | 封面整张可点（`onClick` 必填无默认）、域 body 收详情落点、开着一格就整屏换过去 |
| `desktop/…/gallery/ui/VeneraDesktopApp.kt` | `startComic` 必填参数、`comicTarget` + `comicStamp` 住根上、换域换页自动收（**首帧不收**，否则种子被自己收掉）、`D_落点` 那一行多 `详情=开/关(戳)` |
| `desktop/build.gradle.kts` | 补上 §1 那一跳 |

`app/` 一个字没动：详情这一屏的数据来自 `engine-probe`（桌面与探针共用的取数层），
`source/model/ComicDetails` 那颗在桌面编译面**外**（`desktop/build.gradle.kts:21-118` 从没加过 `source/model`），
所以既没扩共享面也没解任何受保域。

## 3. 为什么把那一屏拆成「装载」与「展示」两颗

这一批正好赶上验证口径改口（手册 §8.1，2026-10-10）：**能不碰前台就不碰**，首选离屏语义树。
而离屏渲的前提是那块 composable **不吃引擎句柄、不吃 DB、不吃网络**。

- 装载那一件（`DesktopComicDetailPane`）：唯一会 `engine.details(...)` 的地方，测试渲不出（要真 GraalJS + 真源）。
- 展示那一件（`DesktopComicDetailContent`）：只吃 `ComicDetailState` 这块纯数据 ⇒ 能被喂着渲成语义树。

判据 ③ 的负向那一条钉的就是这个：**展示层那一段里 `EngineSession` 零出现**。
它一旦去吃句柄，这一层就整体退回"只有像素"，而那一退是静默的（测试仍全绿，只是不再测屏）。

## 4. 尺子（本批 +33 条 / 5 颗，分三层）

| 层 | 颗 | 条 | 钉什么 |
| --- | --- | --- | --- |
| 纯函数 | `DesktopComicLoadInfoTest.kt` | 7 | `loadInfo` 答复 → 标题/简介/章节表：Map 时 key=话号 value=话名（**写过一次反的**）、分组表摊平、List 缺项各自兜底、没有章节表补占位章且话号沿用漫画号、缺 `description` 与 `null` 都算空串、`success=false` 与没有 `data` 都要抛且那句带原话 |
| 纯函数 | `DesktopComicDetailReadingTest.kt` | 6 | stdout 那一行三态各一句、章数数得出来（占位章算一章）、简介数得出字数、空白算"无"而不报"零字"、**那一行里不许有换行** |
| 纯函数 | `DesktopComicSeedLandingTest.kt` | 5 | `--comic=` 那颗种子的落点判读：没给一字不改 / 给了且没被抢就把起始域接到漫画并把行交回默认 / 被 `--artist` 或已解出的 `--ranking` 接管时**不许偷偷改**用户落点 / 三档落空各念一句而接管那一句念得出是谁赢的 / 域没跟过去时那句不许与接管那句同形 |
| 离屏语义树 | `DesktopComicDetailSemanticsTest.kt` | 7 | 题名走站方那枚、空白简介不摆占位句、故障那一格不许同时摆章节表、等回话时不许提前摆章数、两枚钮各按一次落到回调、三态那句回话**上屏**而不只进日志、章节两行各自在屏上 |
| 源码扫描 | `DesktopComicDetailWiringTest.kt` | 8 | §2 那六处接线 + §1 那一跳 + `KNOWN` 登记 + 展示层不吃引擎 + ⑦ 退场关掉的是取进局部的那一颗（负向钉 `onDispose { session?.close() }` 零处）+ ⑧ `startComic` 只在落点真跟到漫画域时才有 |

**7 条语义树判据跑完 13 秒**，零窗口、零前台、不动用户光标；同一格走旧口径要起窗 3~5 分钟。

## 5. 扰动（11 笔）

`_qa/b80_perturb.js`：P1 封面点进的是收藏不是详情 / P2 卡位回调给了默认值 / P3 那一问挪到组合线程上 /
P4 展示层去吃引擎句柄 / P5 落点那行不念详情 / P6 落点那行不改钥匙 / P7 那一枚没拼进 args /
P8 那枚参数没登记 / P9 空简介摆占位句 / P10 没有章节表时不补占位章 / P11 故障塌成"共 0 话"。

其中 **P9 / P11 只有离屏语义树那把尺子咬得到**（源码扫描看不见"屏上摆的是哪句"），
P3 / P4 是顺序与负向那两型。逐笔读数见 §6。

⚠️ 台架第一次跑就红在**自己身上**：report 那一段写成 `readBody(f = path.join(...))`，
给 `const` 循环变量赋值直接抛 TypeError ⇒ P1 没记账就被判 `REPORT_EMPTY` 而中断。
修完重跑。**台架自己也要有对照组**这一条不是新课，但这一批又踩了一遍。

## 6. 读数（收尾现数，2026-10-10 02:0x~02:4x 实跑）

| 量 | 数 | 从哪取的 |
| --- | --- | --- |
| 判据 | **878 条 / 112 类 / 0 失败** | `sh gradlew :desktop:test --offline --rerun-tasks`（`--rerun-tasks` 是必须的：没改测试源码时它会 `UP-TO-DATE`，"BUILD SUCCESSFUL in 1s" 就是**没跑**的凭证，旧 XML 不算读数） |
| 三方口径 | `suites=112 tests_sum=878 testcase=878 @Test=878 failures=0` | `node _qa/three_way_counts.js`（台架收尾那一笔顺带跑的） |
| 判据目录 | **0 处漂移**（表行 112 / 表合计 878） | `node _qa/check_ruler_catalog.js` |
| 扰动 | **11 笔全红在正主上，共 12 条红**（P2 一笔红两条：批 27 那把"回调不许有默认值"的机械尺子 + 本批 ①） | `_qa/b80_perturb.summary.txt`；还原后全量 `EXIT=0` |
| 语义树那一层 | 7 条 **13 秒**、零窗口 | 本批第一次用这一层（`DesktopComicDetailSemanticsTest.kt`） |
| 源码 | `desktop/src/main` **93 颗 / 18,717 行**；`app/` 零改动 | `find` + `wc -l` |
| 包 | `_qa/desktop-exe-2026-10-10-b80/`，包内 desktop jar **4,409,030 B**（`unzip -l` 数到 `DesktopComicDetailKt` / `DesktopComicDetailPane`；`ComicLoadInfoKt` 在 `engine-probe-*.jar` 里） | ⚠️ 这一份是**换 app 内一颗 jar**，不是重新 `jpackage`（见 §6.3） |
| 窗口 | 起窗两次、每次 `taskkill` 后**残留 0** | 按标题数窗，不按 `CommandLine`（那会把自家 shell 圈进去） |

**逐笔扰动落的哪条尺子**（一笔一格的账，别只写"11 笔全红"）：
P1 → ①；P2 → ① + 批 27 那把默认值机械尺子；P3 → ③（顺序那一支）；P4 → ③（负向那一支：展示层吃句柄）；
P5 → ⑤；P6 → ⑤（钥匙那一支）；P7 → ④；P8 → ⑥；P9 → 语义树「空白简介不摆占位句」；
P10 → 纯函数「没有章节表补占位章」；P11 → 语义树「故障那一格不摆章节表」。
**P9 与 P11 只有离屏语义树那把咬得到** —— 这一格源码扫描结构性看不见（"屏上摆的是哪句"不在文本里）。

### 6.1 一条**假尺子**是本批自己写的，被自己的扰动抓到

判据 ⑥ 第一版把 `KNOWN` 那张表写成跨行匹配（`KNOWN = listOf\(` 一路吃到第一个 `COMIC` 再吃到 `)`）——
它跨过表尾撞上了同一颗文件 464 行 `comic()` 自己用的那两枚 `COMIC`，
于是 **P8（把登记摘掉）第一次跑念的是"一笔都没红"**。改成只截表内那一段之后 P8 单独复跑才红。
⇒ 这是"扰动跑出绿 ≠ 判据成立"那一族的**又一次**，而这次的特殊之处是：**红的是我为了写判据而写的正则**，
不是产品代码。措辞已回写进判据注释与目录那一行。

### 6.2 两处真缺陷（都是这一批踩出来的）

1. **退场那一笔关掉了刚建好的那颗引擎**：`onDispose { session?.close() }` 读的是变量当下的值，
   `null → 引擎` 那次切换 dispose 的正是新起来的那一颗 ⇒ `PolyglotException: Context execution was cancelled`，
   屏上详情永远念「态=没成」。正解先取局部（`val closing = session`），判据 ⑦ 正反两钉。
   ⚠️ 连带改判一条**旧判据**：`DesktopDomainNavigationTest` ⑨ 当年钉的就是那个错形状（坑 ㉖ 第八次）。
2. **落点读数会说谎**：`--comic` 与 `--artist`/`--ranking` 同给而赢的不是漫画域时，种子照样递下去 ⇒
   `D_落点 … 详情=开` 而域还停在图库。补 `comicLanding` / `comicNote` 两枚纯函数 +
   `startComic = comicSeed.takeIf { … }`（判据 ⑧），落点那一行的 `详情=` 后面多一枚"是谁改走的"。

### 6.3 那条"交付包在漫画域静默退出"——**本批末撤账**（复现不出来）

- 09 日到 10 日凌晨我记过一条账：交付包 exe 在 `--start-domain=COMIC` 下"1 秒静默退出"，
  而 `:desktop:run` 活着 ⇒ 当时把嫌疑记在"打包那一层"，并在换上新 jar 后看到包也活了，就写成了
  "与详情崩同一枚成因"。**那个归因不成立，今天量掉了：**
- **控制复跑（2026-10-10 02:5x，同一台机器、同一串参数 `--start-domain=COMIC --proxy=127.0.0.1:7890`、数据根各自隔离）**：

  | 跑 | 那颗包 | 日志里的 `打包=` | 结果 |
  | --- | --- | --- | --- |
  | 甲 | `_qa/desktop-exe-2026-10-09-b79/`（**旧 jar**，带 `onDispose { session?.close() }` 那一形） | `打包=true` | **活着**（65 秒仍在，念出 `D_资源 来源=包内 /sources/jm.js 字节=38626`），`_qa/b79_exe_repro.log` |
  | 乙 | `_qa/desktop-exe-2026-10-10-b80/`（新 jar） | `打包=true` | 活着并念出 `D_详情 … 态=上屏`，`_qa/b80_exe_comicdomain.log` |

- 再回看当初那三份"退出"的日志（`_qa/b79_oldroot_comic.log`、`b79_oldroot_run.log`、`b79_control_run.log`）：
  **三颗的 `D_环境` 都念 `打包=false`** ⇒ 它们是开发跑（`:desktop:run` / `:desktop:app`），不是包；
  而且它们不是"进程没了"，是**停在 `D_落点` 那一行不再出声**（漫画探索在解码封面之前本来就不打印），
  那几跑后来是我自己 `Stop-Process` 掉的。
  ⚠️ 顺带一条量具坑：这三份是 UTF-8 而两份包日志是 GBK ⇒ 用 UTF-8 模式串 `grep` GBK 文件会**假空**
  （我先就是这么漏看了 `D_环境`，差点把"包不打印这行"写成新事实）。
- ⇒ **撤账**：这条按用户口径"复现不出来的问题直接撤销，不继续追查"处理 —— 从"已定位到打包层"退回**未复现**，
  不留任何结论在文档里。§17 第 4 项与包内说明里那两句同步改判。
- **留下的一条真规矩**（这条值）：断言"换了 X 之后活了"之前，要先量"**两跑只差 X**"。
  我那一跑同时换了**两颗变量**（jar 与 `打包=` 那一层：失败样本全是 `打包=false`，成功样本全是 `true`），
  所以"换 jar 救活"从头到尾没被量过 —— 这是"同一枚参数至少跑两遍"那条的**新形态**：
  **控制复跑要控的是"差几枚变量"，不是"跑两次"**。
- **那条 hygiene 账（与本节归因无关）当日也结掉了**：真跑 `:desktop:createDistributable --rerun-tasks`（03:06，11 项任务全 executed，不是 `UP-TO-DATE`）
  重打出 `_qa/desktop-exe-2026-10-10-b80r/`，并新立第三把尺子 `_qa/check_pkg_jar_sha.js`（包内那颗 jar 的 SHA == `desktop/build/libs/desktop.jar`）。三把尺子的读数：
  `check_cfg` = classpath 100 / missing 0 / orphan 0；`sync_pkg` 对照 = `copied=0 moved_to_trash=0`（复制忠实）；
  `check_pkg_jar_sha` = 对 b80r **0**、对旧 b79 那包**退出码 1**（字节数也不同）⇒ 尺子咬得住。
  ⚠️ **重打这一趟当场抓到一条实物证据**：上一版换装包带的是**上一笔命名的 skiko**
  （`skiko-awt-runtime-windows-x64-0.150.1-cb2d757a….jar`，而现产物那颗叫 `…-737e62c6….jar`），
  cfg 点名的就是它自己那一份 ⇒ `check_cfg` 一路 0/0 放行。这正是"换装看不见的那一格"长什么样。
  另两条现量：compose 那颗 jar 的打包是**可复现**的（01:39 手工换进去的那一颗与 03:06 重打的那一颗 sha256 同为 `dc8c2014…`）
  ⇒ 两包只差那一颗 skiko；起窗验 b80r：`D_环境 … 打包=true`、`--comic=1461316` 念出 `D_详情 源=jm 号=1461316 态=上屏 话=15 简介=81字`，关窗残留 0。

### 6.4 打包层实跑读数（`_qa/b80_exe_comicdomain.log`，GBK 那一份，`iconv -f GBK` 后逐字照录）

```
D_落点 域=漫画(启动) 行=探索 浮层=关(换域或换页自动收) 查看器=关 详情=开(启动)   种子=viewer关+comic
D_详情 源=jm 号=1480597 态=上屏 话=1 简介=无
D_落点 …详情=关(退回列表)… / D_落点 …详情=开(点封面)…
D_详情 源=jm 号=1461316 态=上屏 话=15 简介=81字
D_详情 源=jm 号=1175106 态=上屏 话=68 简介=39字
```

⚠️ **后四行不是本 agent 按的**（禁鼠标这条没改；那一扇窗开着的时候外部有人操作过它）。
照录、标明出处不明，只作"打包层这一路真走通了、而且换域回来种子没把落点钉死"的旁证，
**不写成"本批自验过"** —— 与批 77 下半那格同一种"有外部读数而无可复核"。

## 7. 图证

`_qa/b80_frame_comic_detail.png`（WGC 窗口捕获那一帧 1226×768，标题带 `#1940`，用完 `taskkill` 残留 0）：
屏上读得到「← 返回列表」+ 站方原题名 `[Cirenk] Fauzy: Nana For One! (My Hero Academia) [ai翻译] [Chinese]`
+ 一枚「收藏」+「共 1 话」+ 一行「第1话」，**简介那一栏整栏不摆**（这本站方没给简介 ⇒ 与判据「空白算没有这一栏」同形）。

两条观感如实记：
① 章数只有 1 话时那一屏**几乎全空**（标题与「共 N 话」顶在上面，下面一大片黑）—— 能用，不好看；
   要不要给空档补一层骨架，留给批 81 一起看（那一批会让这一屏有真章节表）。
② 帧右缘那一列有几枚不相干的残字（抓取边界之外的东西），不影响这一屏的判读，但**别拿它当"内容被裁了"的证据**。
