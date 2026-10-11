# 批 87 · 阅读那一屏「翻页那一档」（2026-10-11）

**一句话**：整页翻那一档是真的 `HorizontalPager`（一屏一张、日漫那一档从右往左），模式那一排接上了对岸同一枚键
（`pref_reading_mode`），页号从"当前真摆着的那一页"念，而**档位与算式在桌面自持一份**（B 路）——
四处口径由尺子从对岸源码文本现取再比。

排期出处：`docs/rounds/remaining-plan-and-reuse-2026-10-10.md` §4 的 批 87（B 档：`Pager` + 音量键）。
本批**拆成两半**，上半（这一份）= 整页翻那一档 + 模式那枚偏好 + 页号 + 历史两档；
下半 = 键位与音量键，它等的是一枚 `D_按键` 读数，见 §1.3。

---

## 1. 题面与三条现量

### 1.1 这一屏今天有什么、缺什么

批 81 起桌面有阅读那一屏，但只有**纵向连续流**一面墙；批 82 把历史那一笔接上、批 82.5 把"落在第几页"接上。
现量三条缺口（都在代码里，不是猜的）：

- 屏上**没有模式那一排**：`DesktopComicReader.kt` 里 `HorizontalPager` 零处、`rememberPagerState` 零处。
- 桌面**从不读也不写** `pref_reading_mode`：那颗键只在对岸 `data/prefs/VeneraPreferences.kt:659`。
- 页号那一句只有「共 N 页」，当前第几页只进了历史那一笔，屏上念不到。

### 1.2 页翻那一面墙的形状（`foundation-desktop-1.12.1.jar` 现量）

`HorizontalPager(state, modifier, contentPadding, pageSize, beyondViewportPageCount, pageSpacing,
verticalAlignment, flingBehavior, reverseLayout, userScrollEnabled, key, nestedScroll, snappingPosition,
overscroll, content)`；`rememberPagerState(initialPage, initialPageOffsetFraction, pageCount)`；
`PagerState.currentPage / settledPage / scrollToPage / animateScrollToPage`。
⇒ 方向用 `reverseLayout` 而不是自己算页序，落点用 `scrollToPage`，读数取 `settledPage`。

### 1.3 音量键那一路：三条硬读数 + 一格没量

下半（键位/音量键）为什么本批不做，三条现量摆在这里：

1. `androidx.compose.ui.input.key` 的 `toComposeEvent` 用 `awtEvent.getKeyCode()` 造 `Key`；
2. AWT 那 189 枚 `VK_` 常量里**音量 / 静音 / 媒体键 0 命中**（逐颗数过）；
3. `Key.VolumeUp` / `Key.VolumeDown` 在桌面是**合成哨兵**（`Key(-1000000019, 0, loc=2)`），
   拿它与真按键的读数比较恒假 ⇒ 唯一可能的通路是 `androidx.compose.ui.awt.getAwtEventOrNull` 再读
   `rawKeyCode`（Windows 上 VK 175 / 176）。

⚠️ **没量过的那一格**：真按一次音量键，Windows 会不会把 VK 175 送进这一层（本机无 OS 级输入，手册 §8 那条硬边界）。
所以 `pref_volume_key_turn` 那枚键**本批不接** —— 接了就是一枚没人按得动的假开关。
下半的第一件事是补一枚 `D_按键` 读数（把 `rawKeyCode` 原样念出来），有了它才谈得上音量键。

---

## 2. 三条定案

1. **只做三档**（条漫·连续 / 日漫·右至左 / 美漫·左至右）。`HORIZONTAL_CONTINUOUS` 与 `DOUBLE_PAGE` 的档位与
   算式整颗留着（读档要认得全），但**不上屏**：摆一枚按不动的档位就是本仓最忌的假开关。
   缺口登记在 §7。
2. **种子只作本次初值，不写盘**（批 83.1 同一条口径）。三种情形各念一句实话（`desktopReaderModeSeedNote` 三态）：
   顶过、顶的是没做那档、没顶而盘上那档没做 —— 折档而不说就是静默换档。
3. **两档各读各的那一面墙**：纵向读 `listState.firstVisibleItemIndex`，整页翻读 `pagerState.settledPage`；
   落起点共用那一颗有界重试件，两枚包装各滚各的。取 `settledPage` 而不是 `currentPage`：
   后者在拖动的半路上就已经换值，那一格落进历史就是"人还没翻过去，进度已经记上了"。

### 2.4 落地之后回读代码抓到自己造的一条缺陷（已修；**帧上没有**，判据有）

⚠️ 这一条是**读代码读出来的**，帧上没有（换档要点一下屏上那枚药丸，而鼠标自验是禁的）—— 所以它按
"结构性推断 + 补尺子"记，不许写成"量到过"。

形状：那一屏的 `LaunchedEffect` 的键里**有 `mode`**，而它干的两件事是"先落起点，再开始盯"。
于是用户点一下档位 ⇒ 这颗 effect 整个重跑 ⇒
① 把 `reading.startPage`（今天恒 0，因为详情那一行写死 0）**重新落到新摆出来那面墙上** —— 人在第 40 页，
切个档跳回第 1 页；② 盯梢那一笔跟着把新那面墙的起始页 **写进 `comic_history`** —— 那一列两端共用，
手机侧读它续读，于是"在桌面上点了一下档位"就把手机上"下次从第 40 页读"挪回了第 1 页。

②比①严重（它写的是共享那张表），而两条同一根因：**换档不该重落起点**。
补法（与判据一起走 TDD）：这一屏自己记一枚"已经摆过的那一页"（`remember(reading.episodeId)`，
由盯梢那一笔更新，**不去读那面已经离开组合的墙** —— 不在组合里的 state 是空操作那一族），
落点取 `desktopReaderCarriedPage(种子折过的那一页, 已经摆过的那一页)`：没摆过才用种子，摆过就用摆过那一页。
"先落起点后开始盯"那条次序不动（它正是换档那一趟不写 0 页的保证：落定之后盯梢第一笔就是落点）。

**已按 TDD 补上**（`desktopReaderCarriedPage` + 三处接线锚点；台架 P22/P23 各咬一条）。
⚠️ 这一格**仍然没有帧**：换档要真点一下那枚药丸，而鼠标自验是禁的 —— 有的是纯函数两档、
接线锚点四条，与"喂进缺陷形状就红"的两笔扰动。

---

## 3. 甲路撞墙与乙路改道

### 3.1 A 路（转交对岸那两颗纯件）—— 量过、没走、等点头

现量：`reader/ReaderInteractionPolicy.kt` 零行 import；`reader/ComicPageSource.kt` 只 import `java.io.File`；
两颗都**不指**目录里那三颗 Android 面（逐颗核过）。所以把它们 srcDir 进桌面编译面**技术上成立**，
而"接同族功能前先 grep 域里有没有现成算式"（批 8 那一课）指的正是这条路。

挡住它的是受保域里的一行：`app/src/test/java/com/venera/compose/desktop/DesktopNoAndroidImportTest.kt:82`
那条 UI 禁串把**整个** `reader/` 目录算作 Android UI 面 ⇒ 桌面 import 那颗纯判据件会在**对岸那把尺子**上红。
`app/` 是受保域，本批不动 ⇒ 登记为**等点头**：点头后删掉桌面那三颗自持件、把 `reader/` 请进编译面。
（那一颗文件里的判据 ⑦ 已经写明"红在自己家才有信息量"，两处注释互为对证。）

### 3.2 B 路（桌面自持 + 四处回核）

新件 `gallery/data/DesktopReaderMode.kt`（五档、键与档名逐字照对岸 + 那颗归一别名件）。四处回核
（`DesktopReaderPagedSeedTest` ⑦，全部**从对岸源码文本现取再比**，不是抄在这里的串）：

| 回核 | 比的是什么 | 对岸出处 |
|---|---|---|
| 档位表 | 枚名 → 键 + 档名（取前两枚字段） | `reader/ComicPageSource.kt:66-70` |
| 归一别名表 | 串 → 落到哪一枚（`else ->` 那一条不算） | 同文件 `fromKey` |
| 步长那两枚数 | 「双页跨 2、其余跨 1」 | `reader/ReaderInteractionPolicy.kt` |
| 组首与钳位 | `takeIf { it <= lastIndex }` / `takeIf { it >= 0 }` / `page - page % step` 三段逐字同串 | 同文件 |

再加两把口径尺子：那枚键名与那句默认值从 `data/prefs/VeneraPreferences.kt` 现取再比（①②）。

### 3.3 B 路的代价（要说清，别写成"抄一份了事"）

回核只覆盖**量过的这四处**。对岸新增第五处口径（例如给档位再加一枚屏上要念的字段、
或改 `pairStart` 的参数名让"逐字同串"比不着）时这把尺子看不见 —— 那正是 §3.1 那条 A 路的价值所在。

⚠️ 一条已经踩到的：`READING_MODE_KEY` 第一版按**常量名**猜成 `pref_default_reading_mode`，
而尺子（①）现取对岸真值 `pref_reading_mode` 当场报红 —— **尺子是对的，猜是错的**。
这一格再次印证 §0 那条：口径要现取，别从名字反推。

---

## 4. 落地清单

- 新建 `gallery/data/DesktopReaderMode.kt`（五档表 + `fromKey`）。
- 新建 `gallery/ui/DesktopReaderPaging.kt`（三档 choices、`desktopReaderIsPaged/Reverses/PageStep/
  NextPageIndex/PreviousPageIndex/PairStart/StepTarget/PageLabel/ModeOf/ModeSeedNote` + 两档共用的
  `desktopApplyStartPage` 与两枚包装）。
- 新建 `gallery/data/DesktopComicReaderPreferences.kt`（`pref_reading_mode` 读写、同数据根一颗、写时念
  `D_阅读档 写 键=… 档=…`）。
- 改 `gallery/ui/DesktopComicReader.kt`：模式那一排 + 种子实话 + 「共 N 页」「第 X / N 页」两枚节点 +
  `HorizontalPager` 那一支 + `stepPaged`（翻不动念 `D_翻页 到边了 …`）。
- 改 `DesktopComicPane.kt` / `VeneraDesktopApp.kt` / `VeneraDesktop.kt` / `ForensicFlags.kt` /
  `desktop/build.gradle.kts`：`--read-mode=` 三层落点（`-PreadMode` → `readMode(args)` → 形参）。
- 新尺子三颗 **51 条**：`DesktopReaderPagingTest` 23（纯函数 + 台架式 `runBlocking` + 离屏 `runComposeUiTest` 造真
  `rememberPagerState`）、`DesktopReaderPagedSeedTest` 22（接线、四处回核、B 路边界、README 同轨）、
  `DesktopReaderModeSeedTest` 6（那颗判读件的行为面 —— 从前只有"三层各有一处"那类存在性锚点）。
- 旧尺子改账三颗（`DesktopComicReaderStartPageTest` 锚点搬到那颗共用件、`DesktopComicReaderSemanticsTest`
  添"当前页号也得单独念一格"、`DesktopComicReaderProgressWriteTest` 的形参表）。
- README 中英两份各改一处能力句 + 各添一条缺口句（两份仍对等，各 468 行）。

**现量**：判据 **1097 → 1148 条 / 138 → 141 颗类 / 0 失败**、`desktop/src/main` **105 → 108 颗 / 21,497 → 22,005 行**、
测试文件 145 → 148 颗、共享面 **86 颗 / 11,378 行 / 点名排除 14 颗 / 含 android import 0 颗**（与批 86 逐字相同 ⇒
本批没往共享面添一格 —— 那正是 §3.1 那条 A 路没走的直接后果）、`app/` **0 行改动**（受保域一字未动）。

---

## 5. 台架（`_qa/b87_perturb.js`，23 笔）

驱动：`sh _qa/perturb_driver.sh b87 20` 先跑 20 笔，§2.4 那条缺陷与 README 那条同轨尺子补完后
`sh _qa/perturb_driver.sh b87 23 21` 接跑 3 笔（驱动支持从中间接跑且不抹前面的账）。
**22 笔要求红 → 全红，共 35 条断言红；P2 要求不红 → 确实一笔没红。** 无一笔是"编译面就红"的废笔。

| 笔 | 喂进去的病 | 咬住的尺子 |
|---|---|---|
| P1 | 双页那一档步长写死成一 | 2 条：`步长那两枚数回核对岸` + `落在组内第二页时往回退的是整组` |
| P2 | **等价改写**：`delta` 的正负判式倒过来写（语义不变） | **0 条**（这一笔的正确输出就是一笔都没红） |
| P3 | 到头那一档不再交回空 | 2 条：`组首与钳位那三处算式逐字回核对岸` + `翻不动时交回空而不是原地那一页` |
| P4 | 组首对齐撤掉 | 3 条：回核那三处 + `双页那一档一步跨两页…` + `落在组内第二页…` |
| P5 | 档位表里一枚**键**漂一格 | `档位表逐字回核对岸` |
| P6 | 档位表里一枚**档名**漂一格 | 2 条：`档位表逐字回核对岸` + `种子顶的是没做的那档时折回默认档而那句实话跟着说` |
| P7 | 归一别名表少一枚别名 | `归一别名表逐字回核对岸` |
| P8 | 盘上那枚键名换成桌面自造的一串 | `模式那枚键名逐字回核对岸` |
| P9 | 盘上那句默认值漂一格 | `模式那枚默认值逐字回核对岸` |
| P10 | 取证参数认不出时折回默认档 | 2 条：`错字与空值都不折回默认档` + `别名表那一串不在取证参数这一路上` |
| P11 | 盯的那一路读错那一面墙（`settledPage`→`currentPage`） | `两档各读自己那一枚页号来源` |
| P12 | 等不到时把请求值当读数 | 2 条：`重试有界而等不到不假装落定` + `落起点那颗件到顶也不把请求值当读数` |
| P13 | 尝试上限写死成数字 | 3 条：`重试有界…` + `UnusedInternalTest` ①③（那颗常量当场变成零调用） |
| P14 | 页序写死成正排 | 3 条：`方向跟着档位而不是写死` + `UnusedInternalTest` ①③ |
| P15 | 当前页号只算不摆 | `有页时每一页各摆一格而页数的读数上屏`（**这一条是本批补的离屏断言**，见下） |
| P16 | 那一排改吃整张档位表（没做的两档长出按钮） | `那一排念的是表里的档名而不是另写一遍` |
| P17 | A 路偷偷走进编译面（`reader` 目录 srcDir 回来） | `桌面不引对岸阅读那一族的件` |
| P18 | 种子顶过档那句实话不上屏 | 3 条：`种子顶过档必须念出来…` + `UnusedInternalTest` ①③ |
| P19 | 那一排点了不写盘 | `模式那一排点了就写而屏上立刻换档` |
| P20 | 构建脚本那一跳的参数名漂一格 | `取证参数那一跳三层都在` |
| P21 | 对外文档只改中文那份 | `两份README的翻页那一条同轨` |
| P22 | 换档重落起点（§2.4 那条缺陷的形状） | `摆过之后换档落在摆过那一页而不是甩回第一页` |
| P23 | 那一页不再由盯梢那一笔记下 | `换档不重落起点而那一页由盯梢那一笔现取` |

三笔台架自己要记的：
- **P15 是"补了尺子才有牙"的一笔**：第一版屏上那句是「共 N 页 · 第 X / N 页」合成一枚 `Text`，
  而旧的离屏判据只认「共 3 页」那一句精确文本 —— 拆开两枚节点后旧尺子当场红（第一次全量红 4 条里就有它）。
  本批因此给它补了「第 1 / 3 页」单独一格，P15 才咬得住"只算不摆"。
- **P23 第一版是废笔**：`to` 串写成 `val pageIndex: Flow<Int> = shownFlow`，而它是 `from` 串的**前缀**，
  `check` 那一步按"终点必须 0 处"直接挡下（批 86 记过同一形状）。改成"只念不记"那一档才咬到行为。
- **`UnusedInternalTest` 连咬三笔**（P13/P14/P18）：撤掉一处调用，那颗 internal 件立刻变成零调用 ——
  批 9 那把尺子在本批第三次替人记账，不用另写判据。

耗时：第一段 21:36:33 起、22:05:59 收尾（20 笔 + 全量）= **29 分 26 秒**，每笔 ≈1 分 28 秒；
第二段 22:11 起、22:15:59 收尾（3 笔 + 全量）≈ 4 分 50 秒。**台架合计 ≈34 分钟**（每笔跑的都是全量）。
逐笔还原后的收尾全量：`EXIT=0` / `suites=141 tests_sum=1148 testcase=1148 @Test=1148 files=148 failures=0`。

---

## 6. 图证

数据目录隔离在 `LOCALAPPDATA=D:\venera-compose\_qa\b87_local`，每一趟都 `--no-daemon` 起。
取证种子是这一批新造的 `--read-mode=`（`-PreadMode`），配现成的 `--read=` / `--read-page=`。

⚠️ **先记一笔量具自己的事故**：第一版这一节写过一帧"顶的是「对开·双页」"的图，而**那一趟根本没起过**
（`_qa/b87_frameB.log` 不存在就是反证）—— 那张图来自读数通道的一次污染输出（手册 §0 那条"返回编造内容"的已知坑，
这次污染的是**图像**而不是文本 —— 连"我自己看了一眼图"那道自审都骗得过去）。**屏上每句行为断言都要答得出哪次量的**，
答不出就把那一条撤下来：那一态（种子顶的是没做的档）当时从文档里撤成"帧上待补"，**补拍成下面的帧 B 才算数**。

### 已量到的四帧

| 帧 | 参数 | stdout 关键读数 | 屏上 |
|---|---|---|---|
| A | `-Pread=1461316/1461318 -PreadMode=HORIZONTAL_RTL -PreadPage=3`（pid 19300） | `--read-mode=HORIZONTAL_RTL 认成 档=日漫·右至左`、`D_阅读 起点 请求=3 量到=3 页表=89 试=1`、`D_历史 写 源=jm 号=1461316 章=0 页=3/89` | 「日漫·右至左」药丸红、`‹ 上一页 › 下一页 ›` 两枚、一屏一张、「共 89 页」「第 4 / 89 页」、红字「这一档是取证参数顶上的（盘上那档还是「条漫·连续」，没有改动）」 |
| A2 | 同上而 `-PreadMode=HORIZONTAL_LTR -PreadPage=6`（pid 17868） | `认成 档=美漫·左至右`、`请求=6 量到=6 页表=89 试=1`、`D_历史 写 … 页=6/89` | 「美漫·左至右」红、那一页**画出来了**（白底 + 中间那枚「漫神漫画」水印），「第 7 / 89 页」 |
| B | `-Pread=1461316/1461318 -PreadMode=DOUBLE_PAGE`（顶一枚**没做**的档，pid 4000） | `认成 档=对开·双页`、`请求=0 量到=0 页表=89 试=0`、`D_历史 写 … 页=0/89` | 红字「取证参数顶的是「对开·双页」，那一档本批还没做 ⇒ 这一屏照「条漫·连续」摆（盘上没改动）」、那一排**只有三枚**、走纵向流（**没有**那两枚翻页钮） |
| C | 无种子；先把 `_qa/b87_local/venera/prefs/venera_preferences.json` 手写成 `{"pref_reading_mode":"DOUBLE_PAGE"}`（pid 35988） | `D_阅读 起点 请求=0 量到=0 页表=89 试=0` | 红字「盘上那一档是「对开·双页」，本批还没做 ⇒ 这一屏照「条漫·连续」摆（盘上没改动）」、「条漫·连续」红、纵向流（**没有**那两枚翻页钮） |

四帧各钉一件事：
- **A/A2**：整页翻那一档在真窗口里是活的，且**两档各有对证**（药丸红的是被顶的那一枚，不是恒红第一枚）；
  `试=1` 是这一档与纵向那一档的实测差别（批 82.5 那趟同一枚页号要 `试=8`）——
  ⚠️ 这一格**只有两枚页号的读数**（3 与 6），不许写成"pager 那面墙总是落得更快"。
- **A2 补了 A 那一格说不清的**：帧 A 那一页整张白，没量死是"源站那一页本来就白"还是"pager 没画图"；
  帧 A2 递第 7 页时画出了带水印的那一张 ⇒ 渲染通路是通的，帧 A 那一格按"那一页本身白"记（**推断，未量死**）。
- **B/C 是那句实话的另外两态**（第三态"顶过而且做得了"就是 A/A2 那一句），而 B 与 C 两帧的**屏上摆法跟着折**：
  走纵向流、那两枚翻页钮不出现 —— 不是只念一句而行为照旧。
- **C 两条**：跑完那颗手写的 `venera_preferences.json` 仍是**那 35 字节、逐字未变**
  ⇒ 折回默认档**没有写回盘**，而那一枚键名是**真被读到的那一枚**（手写文件 → 屏上念出「对开·双页」）。
- **种子不写盘**有两条盘上读数：帧 A 之后与帧 B 之后各数一次 `_qa/b87_local/venera/prefs/`，**都是 0 颗文件**
  （帧 C 用的是手写那一颗，不算这一格的账）。

每帧 `relatedTransientWindows=[]`（没有 `title:"Error"` 那一格），每趟用完按 PID `taskkill`，
收尾 `count=0`（Venera 窗 0 枚），真人那份 `%LOCALAPPDATA%\venera` 未碰。

### 6.5 交付物里那一屏（本批重打了包，拖了四批的账一起结）

`./gradlew :desktop:createDistributable --rerun-tasks`（45 秒）→ 拷进 `_qa/desktop-exe-2026-10-11-b87/`（216 MB）。
两把包尺子现跑：`check_cfg.js` → `classpath=100 missing=0 orphan_jars=0`；
`check_pkg_jar_sha.js` → 包内 `desktop-eb1ee4ce….jar` **4,705,630 B** 与现产物 `desktop/build/libs/desktop.jar`
sha256 逐字节同一颗。
包内真跑（直接起 `venera-desktop-probe.exe`，程序参数形态 `--proxy=… --read=… --read-mode=HORIZONTAL_RTL --read-page=3`，
`LOCALAPPDATA` 指到 `_qa/b87_pkg_local`）：那一帧摆着「日漫·右至左」红、`‹ 上一页 › 下一页 ›`、「第 4 / 89 页」
与那句种子实话 ⇒ **翻页那一档在交付物里是活的**（不只是 `gradle :desktop:app` 那一层）。
`D_数据目录 D:\venera-compose\_qa\b87_pkg_local\venera` 当场念过，跑完 `prefs/` 里 **0 颗文件**
（种子不写盘那一条在交付物里也成立，第三次读数）。

⚠️ 一条量具知识：包内那一层的**中文 stdout 全成乱码**（`D_取证参数 --read-mode=HORIZONTAL_RTL 认成 档=` 后面那截没了），
比 `:desktop:app` 那一层的 U+FFFD 更彻底 ⇒ **包内取证只能靠 ASCII 那半加帧**，
凡是"只有中文念得出来"的断言在包里都取不到证（这一格从前没量过，本批补上）。

---

## 7. 下一件与欠的格

**下一件**：批 87 下半（键位 + 音量键 + `D_按键` 读数）。排期表里 批 88/89 排在它后面。

欠的格（本批**没量到**的，不许当成验过了）：
1. **屏上那三枚药丸没点过**：`setReadingMode` 那一路只有单测、接线尺子与台架 P19，帧上没有 —— 鼠标自验禁，
   而这一族刻意没有取证种子（种子不写盘）。正解是下半把键位接上后走键盘，或补一枚"自动点那一排第几枚"的种子。
   同一格还连带压着 §2.4 那条：**换档那一路没有帧**（修法已落地、判据有，行为只有离线读数）。
2. **`reverseLayout` 的真方向没量**：两帧各摆一张，看不出"从右往左"。要量死得连拍两帧并拖动，
   而拖动要 OS 级输入（禁）。这一格只有 `desktopReaderReverses` 那颗纯函数 + 接线尺子。
3. **`试=1` 那一格只有一枚页号**（见 §6）。
4. **没做的两档**：`DOUBLE_PAGE` 要一次跨两页（算式已在，屏上没有那一格），`HORIZONTAL_CONTINUOUS` 要横向连续流
   （`HorizontalPager` 的 `pageSize` 那一族改法），两档都排在下一批之后。
5. **交付包本批重打了**：`_qa/desktop-exe-2026-10-11-b87/`（216 MB，两把包尺子各 0，见 §6.5），
   一次结掉批 83 / 83.1 / 85 / 86 四批没重打的账 —— 那一包里今天**第一次**带着落盘、图库浏览历史、源管理与翻页那一档。
   旧包 `_qa/desktop-exe-2026-10-10-b829b/` 按可逆路径处理要等点头（本批只登记，没动它）。
   ⚠️ 包内那一层的参数形态是**程序参数** `--read-mode=…`，`-P` 只到 gradle 层。
6. **量具事故一条已就地改判**（§6 开头那一笔）：图证那一路从前只防"没图 = 没跑起来"，
   这次要防的是"**多出一张没跑过的图**"。补的口径：每一帧的 stdout 落盘成 `_qa/b87_frame*.log`，
   文档里那一行必须能指回一颗存在的 log 文件 —— 图与 log 对不上号时以 log 为准。
