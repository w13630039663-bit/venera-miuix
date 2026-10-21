# 桌面端 1-5 项 · 进度清单

生成时间：2026-10-21
分支：`compose-migration`　HEAD：`f9d2efec`（**本轮全部改动尚未提交**）
 governing doc：`docs/rounds/desktop-reuse-android-ui-2026-10-18.md`（写法 A：复用安卓大屏 UI）

> 本清单只写**量出来的**东西。每条要么带文件:行号，要么带实测读数，
> 要么明写「没做 / 没验」。不用"基本完成""大致可用"这种话。

---

## 0. 基线（刚跑的，不是印象）

| 项 | 数 | 说明 |
|---|---|---|
| `:desktop:test` | **1130 / 0 失败** | 171 个 XML（本轮开头是 1076 / 164，补了 54 条判据） |
| `:app:testDebugUnitTest` | **1795 / 0 失败** | 212 个 XML（本轮一条没动，重跑过两次确认没被弄坏） |
| `:desktop:compileKotlin` | SUCCESSFUL | |
| 桌面 `src/main` 里 `@Composable` 数 | **35** | 本轮开始时是 12；上一版记 30 已过期，重新量过 |

⚠️ ~~**测试条数一条没涨**（1076 → 1076）。本轮新增 6 个屏，
**一个针对它们的测试都没写**。这是本清单里最难看的一条，见 §4-A1。~~
⇒ **本轮收尾已推翻这一条**：补了 54 条（1076 → 1130，全量 0 失败），逐类账在 §6 项 1。
那句"最难看"本喵留着不删 —— 它记的是写下它时的事实，划掉它而不是抹掉它。

---

## 1. 阅读器 + 详情屏

| 子项 | 状态 | 证据 |
|---|---|---|
| 详情屏文件 | ✅ 已写 | `desktop/.../detail/DesktopComicDetailScreen.kt`（291 行，4 个 `@Composable`；原记 249 行，本轮加收藏钮后重新量） |
| 阅读器文件 | ✅ 已写 | `desktop/.../reader/DesktopReaderScreen.kt`（233 行） |
| 接进壳 | ✅ | `VeneraDesktopLargeScreen.kt` 的 `when (route)` 分支 |
| 常驻引擎 | ✅ | `VeneraDesktop.kt` `uiEngine`（与探针那颗分开；探针那颗 `finally { close() }`，不能给 UI 用） |
| **详情取真数据** | ✅ **实测通过** | 截图 `_qa/e2e-2-detail.png`：《斯文壞貓貓》真封面 + 真简介 + **20 话**列表 |
| **阅读器取页表** | ✅ **实测通过** | 截图 `_qa/e2e-3-reader.png` 顶栏念「第 1 / 73 页」—— 73 页是真取回来的 |
| **写阅读历史** | ✅ **实测通过** | 库里新增一行：`1454539 / 斯文壞貓貓 / 第2話 / last_page_index=0 / total=73`（跑之前是 3 行手工数据，跑之后 4 行） |
| **页图显示** | ❌ **没验过** | 三次截图（`e2e-3`/`e2e-5`/`e2e-6`/`e2e-7`）页区**全是黑的** |

### 页图为什么还黑（四个因，已排掉三个）

| # | 因 | 处置 |
|---|---|---|
| 1 | 用 `VeneraCover` —— 它内部 `ContentScale.Crop` + 封面比例，把 2:3 的页裁成一条 | ✅ 已改 `AsyncImage` + `Fit` |
| 2 | 裸 `AsyncImage` 在 `LazyColumn` 里**高度塌成 0**（0 高 item 不被预取 ⇒ 永远等不到首帧） | ✅ 已加 `aspectRatio(0.7f)` 兜底容器 |
| 3 | model 传的是**字符串** `"comic\|src\|comic\|ep\|key"`，而 `DesktopComicPageFetcher.Factory : Fetcher.Factory<DesktopComicPage>` 只认**对象**；字符串被 Coil 送给网络 fetcher ⇒ 直接失败 | ✅ 已改成构造 `DesktopComicPage(...)` |
| 4 | **引擎并发**：`DesktopJsHost` 与 `EngineSession` 都零同步（查过：无 `synchronized`/`Mutex`/`withLock`）。接上界面后同一颗引擎有 4 个调用点，实测 `IllegalStateException 装载后取不到 source key: jm`（`EngineSession.kt:52`）—— 两趟 `load()` 交错抢走 `window['temp_source']` | ✅ **已重跑验证（2026-10-21 补）**：`DesktopEngineCallConcurrencyTest` 用真引擎真并发量到 ①过锁 24 趟峰值并发恰好 1、抢走 0、抛 0；②同负载裸调 24 趟**抛 18**，抛的逐字就是这一条。见 §6 |

因 4 的修法：新增 `DesktopEngineCall.kt`（`desktopEngineCall`），
锁键公式从 `DesktopComicPage.lockKey` 抽成 `desktopEngineLockKey`（一处，不重写），
探索 / 详情 / 取页 / 搜索四个调用点全部过它。

**⇒ 第 1 项状态：详情可用、阅读器能开能记历史、因 4 已补验；"看得见页"这件事仍未证实（只证到布局层有高度，见 §6）。**

---

## 2. 源管理界面

| 子项 | 状态 | 证据 |
|---|---|---|
| 屏文件 | ✅ | `desktop/.../sourcemanage/DesktopSourceManageScreen.kt`（271 行，4 个 `@Composable`；原记 256 行，本轮加"← 返回"后重新量） |
| 接进壳 | ✅ | 走 `DesktopRoute.SourceManage`（入口本轮已补，见下） |
| 源清单读 | ✅ 已接 | `EngineAssets.listSourceKeys` + `DesktopSourceNames.displayNameOfKey` + `DesktopSourceCatalog.rows` |
| 启停写 | ✅ 已接 | `DesktopSourceState.setDisabled`（盘上 `venera_desktop_sources.json`） |
| **遮罩档写口** | ✅ 已接 | `guard.setNsfwMaskMode(mode)` —— **闭合安全评审 H4**（此前 `setNsfwMaskMode` 零调用点 ⇒ 用户没入口改档 ⇒ 遮罩永远停在默认 `OFF`） |
| 屏蔽 AI 写口 | ✅ 已接 | `gallerySettings.setGalleryBlockAi(on)`（不是 `galleryBlockAi.value =` —— 那是 `asStateFlow()`，只读） |
| **实测** | ❌ **没验** | 见 §5「验证受限」 |

### 两个没解决的

- ~~**没有入口**~~ ✅ **本轮已补**（2026-10-21）：探索屏顶栏加了「源管理」那枚钮
  （`DesktopExploreScreen` 新增 `onOpenSourceManage`，**故意不给默认值** —— 给 `{}` 就是假开关，R6 那一族），
  壳里 `onOpenSourceManage = { route = DesktopRoute.SourceManage }`。
  放在顶栏**任何成功分支之外**，所以取失败时那一枚仍按得到（有尺钉着）。
  顺手补了这屏**从前没有的出路**：`onBack` 参数 + 标题旁「← 返回」——
  从前它是单行道，进得去出不来。
  ⚠️ 入口只有探索这一处；安卓那边源管理住在「设置」里，将来补了设置屏**应挪过去而不是留两处**
  （`DesktopSourceManageEntryTest` 钉的就是"恰好一处"，将来多一处会红得有意义）。
- ⚠️ 补入口时撞上**一颗既有缺口**（不是本轮弄坏的，`git diff` 查过）：侧栏那一路原本只
  `onTabSelected = { currentTab = it }`，而壳里分派是**二级页优先**（`DesktopRoute.Tab ->` 排在最末一支）
  ⇒ 停在详情 / 阅读器 / 源管理时点侧栏任何一格，**屏上仍是那一本漫画，而高亮已经跳过去了**，
  读起来像"侧栏坏了"。本轮已修（切 tab 同时 `route = DesktopRoute.Tab`），
  并配了尺 + **做过扰动实验**（把源码改回旧写法 ⇒ 该红的两条红、前提那条不红；改回修法 ⇒ 绿）。见 §6。
- 只做了「清单 + 启停 + 两档守卫设置」。安卓 `ComicSourceScreen`(1629) +
  `SourceEditScreen`(199) + `SourceSectionScreen`(604) + `PreferredIpSpeedTestScreen`(462) +
  `WebLoginScreen`(315) 里的**源增删 / config 表单 / 导入导出 / 测速 / 网页登录**都没做。
  源增删在桌面**不成立**（34 颗 js 随包，不是用户装的）⇒ 屏上已明写这句；
  但 config 表单 / 测速 / WebLogin 是 [值] 没做，不是 [术]。

---

## 3. 收藏 / 搜索 / 探索 三格

| 格 | 屏文件 | 接线 | 实测 |
|---|---|---|---|
| **探索** | ✅ `explore/DesktopExploreScreen.kt`(208；原记 194，本轮加源管理入口) | ✅ | ✅ **通过**：`_qa/b1-explore3.png` 真出 30 本（jm「連載更新」分区），点卡进详情也通 |
| **搜索** | ✅ `search/DesktopSearchScreen.kt`(190；原记 191) | ✅ `EngineSession.search()` | ❌ 没验（本轮补了无头尺，见 §6；真源真网络那一档仍没验） |
| **收藏** | ✅ `favorites/DesktopFavoritesScreen.kt`(178；原记 179) | ✅ `FavoriteDao` | ❌ 没验（本轮补了写侧：详情屏收藏钮，见 §6） |

三格都已**不再是 `DesktopNotWiredPane`**。

### 收藏这一格有个已知的等待坑（已按口径处理，但没实测）

`FavoriteDao` 与 `HistoryDao` 同一个形状：`init { refresh() }` 里是
`scope.launch { _favoritesFlow.value = … }`，发起即返回，而初值是 `emptyList()`。
直接读 `.value` ⇒ 屏上把"还没读回来"说成"没有收藏"。
所以 `readFavorites` 用 `first { it.isNotEmpty() }` + 2 秒超时回退 `.value`
（照搬 `DesktopHomeHistorySource` 那一套，没重写）。

⚠️ **这条只被"照抄一个已验证的做法"支撑，没被实测支撑** —— 而库里现在
`comic_favorite` 是 0 条，就算跑对了也看不出区别。要真验得先造一条收藏，
而**详情页还没有收藏按钮**（§1 的"没做"里列着）。

⇒ **本轮（2026-10-21）收藏按钮补上了**（§6 有账），那句"要真验得先造一条收藏"的前置条件没了。
但**这一条的 ⚠️ 本身还没解除**：那个 `first { it.isNotEmpty() }` + 超时的读侧口径
仍然只有"照抄"支撑、没有实测支撑 —— 无头尺能钉"参数形状与不写库"（`DesktopFavoriteWriteSideTest`），
钉不了"库里真有一条时念得对不对"。⚠️ 保留。

---

## 4. 画廊子屏

**❌ 完全没动。**

需要做的（安卓侧行数实测）：`GalleryScreen`(3453)、`GalleryPostScreen`(2120)、
`PixivWorkScreen`(2507)、`PixivArtistScreen`(1239)、`PixivAccountScreen`(2070)、
`GalleryForYouScreen`(658)、`GalleryDailyScreen`(234)、`GalleryHistoryScreen`(770)、
`GalleryBookmarksHubScreen`(206)、`GalleryArtistProfileScreen`(705)、
`FavoriteImagesScreen`(565)、`FollowUpdatesScreen`(301)。

⚠️ 其中 **pixiv 那三屏（2507+1239+2070）在 `desktop/build.gradle.kts` 的 `exclude` 里，
连编译都进不来** ⇒ 那部分是 [术]（要先做平台件替换），不是"没来得及"。
其余（Post / Daily / ForYou / History / Bookmarks / ArtistProfile / FavoriteImages）是 [值]。

桌面侧 `Desktop*Pane.kt` 有 **14 个是 0 个 `@Composable` 的逻辑壳**
（`DesktopForYouPane` / `DesktopDailyPane` / `DesktopLatestPane` / `DesktopRankingPane` /
`DesktopArtistProfilePane` / `DesktopGalleryFavoritesPane` / `DesktopGallerySearchPane` …），
算式齐、UI 缺 —— 第 4 项主要就是在这些壳上补 UI。

---

## 5. 本地漫画

**❌ 完全没动。**

卡在写侧：桌面下载树**从不写 `comic_info.json`**，而读侧（`DesktopComicDownloadedList`）
以它为存在判据 ⇒ 现在做本地漫画屏只会永远念"没有本"。
必须先补 `comic_info.json` 的写侧（安全评审 R7 / G1 那一族）。

⚠️ `LocalComicManager.kt` / `ComicStorageRoot.kt` 在 `auto_exclude` 里、用 `Context`/SAF
⇒ 不能移植，要复用 `DesktopComicDownloadedList`。
另外**绝不照抄** `LocalComicManager.deleteLocalComic`（它丢掉 `deleteRecursively()` 的返回值直接 `return true`）。

---

## 本轮顺手修掉的（都是量出来的真 bug，不是重构）

| # | 问题 | 处置 |
|---|---|---|
| B1 | 壳文件被脚本 `splitlines()`+`join` 往返弄出**满屏空行**（1613 行 CRLF / 775 行真码，每行夹一空行）。Kotlin 不在乎空行 ⇒ 编译全绿 ⇒ 藏到今天 | ✅ 归一（每段连续空行减一个），**逐行校验非空行序列一字未变** |
| B2 | 写历史的 `LaunchedEffect(route, readerPage)` **键里没有 `readerPages`**：进阅读器那刻页表还空，effect 在 `isEmpty()` 处退出，页表 0→73 时**不重跑** ⇒ 历史永远空白 | ✅ 键加 `readerPages.size`；随后实测到新增一行 |
| B3 | 三处 `LaunchedEffect` 被插到状态声明**之前**（`route` / `currentTab` / `layoutMode` 三回，同一个根因） | ✅ 声明统一前移；补了一个"引用行号必须小于声明行号"的机械校验 |
| B4 | 注释里一个断行把 `enera_core.db` 掉进代码层，编译当表达式 | ✅ 修好 |
| B5 | 本喵写了个 `private fun Int.dpOrZero()` 无用件 | ✅ 删 |
| B6 | 收藏屏里一个只有一处调用的 `BoxScopeCenter` 包装 | ✅ 摊平 |
| B7 | `DesktopSourceCatalog` / `DesktopSourceNames` / `DesktopSourceRow` / `DesktopSourceRowUi` 是 `internal`，而 public 壳/装配点要用 ⇒ Kotlin 拒 public 暴露 internal | ✅ 改 public，并写明"是被签名逼的，不是随便挑的" |
| B8 | **侧栏切 tab 不退二级页**（既有缺口，本轮补入口时撞上）：`onTabSelected` 只赋 `currentTab` 不清 `route`，而分派是二级页优先 ⇒ 停在详情/阅读器/源管理时点侧栏，屏上不变而高亮跳走 | ✅ 回调里同时 `route = DesktopRoute.Tab`；尺 = `DesktopTabExitsSecondaryRouteTest`（3 条），**扰动实验做过**：改回旧写法 ⇒ 2 红 1 不红；改回来 ⇒ 全绿 |
| B9 | 源清单那颗 `LaunchedEffect(currentTab, sourceTick)` **键里没有 `route`**：本喵加了源管理入口之后，切进那一屏时 `currentTab` 没变 ⇒ effect 不重跑，屏上永远念"一个源都没有" | ✅ 键加 `route`（`LaunchedEffect(currentTab, route, sourceTick)`）；这一颗是**本喵自己引入的可撞性**（入口是本轮加的），不是既有缺口 —— 与 B8 分开记，别混成一个"顺手修的" |
| B10 | 详情屏收藏钮**第一版本喵写成了乐观翻转**：先 `detailFavorited = !(now ?: false)` 再 `favoriteTick++` ⇒ 重查读到的是**写之前**的库，标签闪回原样（肉眼可见地"没点上"） | ✅ 改成 `busy = true` → 等写完 → `favoriteTick++` → 重查自己定状态；失败时把成因念出来而不猜状态。这条是本喵抓到自己的设计错，不是编译错（编译一直是绿的） |

---

## 验证受限（重要，影响上面所有 ❌）

主人要求：**后台验证，不抢前台鼠标和窗口。**

本轮**之前**已经抢过 4 次前台（`click_at.py --foreground` 用 `SetForegroundWindow` +
`mouse_event` 真移光标），截图 `_qa/e2e-*.png` 都是那时候来的。
本轮**之后**不再用这条路。

可用的无头路（仓里已有先例）：`runComposeUiTest`
（`desktop/src/test/.../gallery/ui/DesktopReaderKeyTest.kt` 就在用，
`performKeyInput` / `onNodeWithText` 都够得着）。

⇒ §1 因 4、§2、§3 搜索/收藏、以及页图到底黑不黑，**都该用这条无头路补验**，
而不是再起一次真窗口。

---

## 6. 下一步 1–4 的执行结果（2026-10-21 收尾记账）

台账口径：**只有量出来的才写**，量不到的明写"没验"。

### 项 1 · 六颗新屏的无头测试 —— ✅ 补了 54 条

| 判据 | 条数 | 钉什么 |
|---|---|---|
| `screens/DesktopNewScreensSemanticsTest` | 26 | 六屏真按真点：收藏钮四档各念一句且按一次恰好一次；disabled 时 `performClick` 落 0 击；无写口时那枚钮**根本不摆**；章节计数念真值；页格实测有高度（`h > 100` 且 `w/h ≈ 0.7`）；滚动真把页码交回外面；三种空态/失败态各念各的原话；三处遮罩判定各是其是 |
| `architecture/DesktopEngineCallCoverageTest` | 5 | 引擎问数恰好 10 且每一问距 `desktopEngineCall` ≤2 行；锁键公式只有一处；**下载泵零调用点**（见下） |
| `architecture/DesktopSourceManageEntryTest` | 5 | 入口恰好一处、参数**不许有默认值**、`onBack` 真接上、effect 键含 `route` |
| `architecture/DesktopFavoriteWriteSideTest` | 7 | 写侧接 `isFavorite`/`addFavorite` 而**不许用 `toggleFavorite`**；`sourceName = r.sourceKey` 与安卓侧那一行成对；读侧等待口径没被改松 |
| `architecture/DesktopMaskJudgementSingleSourceTest` | 6 | 六屏里**不许出现** `coverMaskStateOf` / `nsfwMaskMode` 等字样（遮罩判定只有一处真源）；`coverMaskStateOf` 出现次数 = 壳 5 + 定义 1 |
| `architecture/DesktopTabExitsSecondaryRouteTest` | 3 | B8 那颗（切 tab 退二级页），**带扰动实验** |
| `architecture/DesktopEngineCallConcurrencyTest` | 2 | 项 2 那颗行为判据，见下 |

26 + 5 + 5 + 7 + 6 + 3 + 2 = **54**，与总数 1076 → 1130 对得上。

### 项 2 · 因 4（引擎串行化）—— ✅ 这一格今天才算"重跑验证"过

清单原来那句是 ⚠️「已改码 + 编译通过，但没重跑验证」。这一档**不是源码尺**，
是真引擎、真并发、真 `load()`（`load` 那一路不发网络，js 已在测试 classpath 上）：

```
D_串行     样本=24 峰值并发=1 抢走=0 抛=0 逃逸=0
D_裸调对照 样本=24 抢走=0 抛=18 收进本颗=0
  site=3: IllegalStateException: 装载后取不到 source key: jm
  site=4: IllegalStateException: 装载后取不到 source key: copy_manga
```

同一份负载：**撤掉那一层 24 趟抛 18 次**，抛的逐字就是 §1 因 4 记的那一条；
**装上之后 24 趟全过、峰值并发恰好 1** ⇒ 串行化这条修法作数。
②那颗**不断言"必须翻"**（把竞态写死成必然会在别人机器上随机红），只钉样本数完整 + 没翻时出声。

⚠️ 代价与事故，如实记：这颗判据**第一次上线时把不相干的 `DesktopAccentFillTest` 撞红了**
（`UncaughtExceptionsBeforeTest`）。因果做过，不是猜：把本文件移开跑全量 ⇒ 全绿；放回去 ⇒ 又红。
成因是②故意裸调抛出的那十几笔落到**线程默认未捕获处理器**，被 kotlinx 的全局处理器记到
"下一颗排到的测试"头上 —— 正是本仓「谁被撞全凭顺序运气」那一族。
修法是**收在本颗里**（自己造线程池，每枚线程带自己的 handler，逃逸笔并进读数），
**不是删掉②那格证据**。最终全量 1130 / 0 失败。

### 项 2 · 页图 —— ⚠️ 只验到布局层，字节层仍然没验

`DesktopNewScreensSemanticsTest` 量到页格 **1024×1463**（`w/h = 0.7`），
即 §1 因 2「裸 `AsyncImage` 高度塌成 0」那颗兜底**还在**、74 个 item 不会再全黑。
⚠️ 但这**不证字节取回来了**：那一档要真源 + 真网络，离屏够不着。
四张截图（`e2e-3`/`e2e-5`/`e2e-6`/`e2e-7`）页区全黑这件事，**仍未解决、仍未复测**。

### 项 3 · 源管理入口 —— ✅ 补上了（并多修一颗单行道）

见 §2「两个没解决的」第一条：探索屏顶栏那枚「源管理」+ 这屏从前没有的 `onBack`。
撞出来的既有缺口 B8（切 tab 不退二级页）也在这一项里修掉并配了尺。

### 项 4 · 详情屏收藏钮 —— ✅ 补上了

四档标签（`收藏` / `已收藏 · 点取消` / `正在读收藏…` / `正在写收藏…`），
`enabled = favorited != null && !favoriteBusy`；**没有写口时那枚钮整颗不摆**（不给假开关）。
接的是 `isFavorite` / `addFavorite` / `removeFavorite` 三颗，**不用 `toggleFavorite`** ——
它读 `_favoritesFlow.value`，而那颗 `init { refresh() }` 是发起即返回、初值 `emptyList()`
⇒ 冷启动头几下会把"还没读回来"当成"没收过"，**一点就删**。
⚠️ 这一颗也**没在真库里验过**（无头层只能钉参数形状与"不许写"），§3 那条等待坑的 ⚠️ 因此保留。

### 本项跑出来的新发现（不在原清单上）

- **下载泵一行 UI 都没接**：`desktopComicStartQueuePump` 在 `desktop/src/main` 里
  只有它**自己的定义**，零调用点（`DesktopEngineCallCoverageTest` 把它钉成
  `listOf("DesktopComicQueueRunner.kt")`）。⇒ 详情屏 KDoc 里"下载整话/队列入口"那句没做，
  不是"屏没画"这么简单：**泵本身没跑**，接了按钮也不会动。这条要单列进 §4/§5 那一项的前置。
- 台架事实（写判据时量出来的，别再踩）：
  - `performClick` 打在 **disabled** 的 `VeneraButton` 上**不**触发回调（所以它能钉"不许写"）；
    但 `performSemanticsAction(OnClick)` **会**绕过 disabled ⇒ 绝不用语义动作钉"不该写"。
  - `performClick` 打在 bounds 落在 1024×768 视口**外**的 `VeneraCard` 行上会**静默落 0 击**
    ⇒ 点击断言一律用 `WideScreenLayoutMode.Expanded`。
  - 单栏（Compact）时详情那屏 `DetailHead` 的封面占掉 1463px ⇒ 章节行第 1 条 `top = 768.0`，
    整棵树里读不到 ⇒ 那是**视口问题，不是屏没渲**（有一颗判据专门钉这个区分）。
- ⚠️ **一颗写法纪律**：本喵一度写成 `= runComposeUiTest { … runComposeUiTest { … } }`
  （两只 `runTest` 套一起），它就是第一次把 `DesktopAccentFillTest` 撞红的凶。
  **一颗测试体只许一层 `runComposeUiTest`**，要多档就平铺 —— 已在两颗判据的注释里写明并全仓扫净。

---

## 下一步（按本清单的缺口排，不按"好做"排）

| 序 | 做什么 | 为什么排这儿 |
|---|---|---|
| ~~**1**~~ ✅ 做完 | 给本轮 6 个新屏**补测试**（`runComposeUiTest` 无头） | 补了 **54 条**（1076 → 1130 全绿），逐类见 §6 项 1 |
| ~~**2**~~ ⚠️ 半做完 | 用无头路验 §1 因 4（引擎串行化）+ 页图 | 因 4 **已用真引擎真并发补验**（裸调抛 18/24、过锁 0/24）；**页图只验到布局层有高度，字节层仍未验** |
| ~~**3**~~ ✅ 做完 | 给源管理补**入口** | 探索屏顶栏那枚 + 顺手补了这屏从前没有的 `onBack`；撞上并修掉既有缺口 B8 |
| ~~**4**~~ ✅ 做完 | 详情页补**收藏按钮** | 四档标签 + 无写口时整颗不摆；**没在真库里验过**，§3 那条 ⚠️ 保留 |
| **5** | ~~提交一次基线~~ ✅ **就是本提交** | 提交前重新 `--rerun` 验过基线（1130 / 1795，0 失败）才敢按。⚠️ **没有用 `git add -A`**：`.gitignore` 只写了 `.gradle`，挡不住 `.gradle-home`（未跟踪量到 **16269** 个文件）—— 已补漏，未跟踪 16459 → 115。暂存范围 = `desktop/ app/ docs/ 两份台账 .gitignore`，276 条，零图零缓存零 log。本提交之前的 HEAD 是 `f9d2efec` |
| **6** | 第 4 项画廊子屏 | 14 个逻辑壳等着补 UI；pixiv 那三屏是 [术]，要单列。⚠️ **前置**：下载泵零调用点（§6 新发现），队列那一格得先让泵跑起来 |
| **7** | 第 5 项本地漫画 | 必须先从 `comic_info.json` 写侧起手 |

---

## 仍然开着的评审项（本轮一条没关，除 H4）

- **A 条（阻断）**：分区 2.5 语义未定（漫画推荐 vs 图片挑选）。
  两条独立理由挡着：本地阅读标签会外流到 `jm`；缺 nsfw 预设表。
  ⇒ **需要主人裁决**，本喵不再自己猜。
  ⚠️ 本轮把 `jm` 设成探索默认源，成人封面在 `OFF` 档下直接铺出来了 ——
  这正是 A 条担心的那件事，本喵把它做出来了。要么换默认源（`copy_manga`/`manhuagui`/`comick`
  在预设表里都是 `safe`），要么把默认档设成 `BLUR`。
  但默认档 `OFF` 是**照安卓抄的**（`ContentGuardManager.kt:89` 也是 `"OFF"`），
  擅自改会让两端分叉 ⇒ 这条也**待主人裁决**。
- R6 `onRetest = {}` 假开关 · R7 `comic_info.json` · R8 `onOpenHistory` 默认 `{}` 无处可去 ·
  R9 删掉的三把尺子 · R11 三套 `Text` 实现 · R12 exclude 表成了尺子输入 ·
  R13 `HistoryDao` 每次实例化漏一个 `CoroutineScope` ·
  F1/F2/F3 `EngineAssets` 键清洗 / 4 MB 上限 / `listSourceKeys` 空 vs 不可用 ·
  G1 下载写侧缺 canonical 包含检查 · H2 `openInBrowser` scheme 检查 · H3 `assetDir` CWD 兜底
- **H4 已关**（遮罩档写口接上源管理屏）—— 但因为源管理屏**没有入口**，
  实际效果仍是"用户够不着"。严格说 H4 只关了一半。
