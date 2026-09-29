# 画廊批次 C：大图页行为与观感档位（2026-09-29 第五轮）

用户指令：「批次c」→ 四条关键问题拍板（见 §〇）→「同意，按 C1 方案开工」。
七条按**风险**切两批：C1 四条在漫画阅读器里都有现成口径可照抄（零新层），C2 三条各要一层新东西。

- **C1（本轮做）**：屏幕常亮 · 音量键翻页 · 自动连播间隔 · 智能预加载
- **C2（下一条方案再做）**：动图自动播放三档 · AI 屏蔽与角标（画廊独立一把）· 背景颜色四档

## 〇、四条拍板（AskUserQuestion，全选推荐项，第三条给了自定义答案）

1. **背景颜色**：只做「现状 / 纯黑 / 深灰 / 纯白」四档。
   **不做取色** —— 用户原话里的"保留现在的样式（提取图片边缘颜色做渐变背景）"这一档**从来不存在**：
   现状是窗口 blur-behind 32dp 糊住底下那面墙 + 一层 `Black(0.45)` 压暗
   （`GalleryPostActivity.kt:32-39`、`GalleryPostScreen.kt:459` 与常量 `BACKDROP_SCRIM_ALPHA :968`）；
   而"边缘取色渐变"我们在 2026-09 做过一次，真机被否（"深色主题下头部变成一块边缘清晰的紫色矩形"，
   见 `detail-thumbnail-and-cover-tint-2026-09.md:188-192`），`CoverPalette.kt` 已删、androidx.palette 依赖已回退。
   重新引回一个"已知会被否一次"的东西不划算。
2. **动图三档只管动图**（GIF / 动图 WebP）：**视频照旧点击才播**。
   "始终"不该替用户决定什么时候花几十 MB 流量、什么时候出声。C2 落这条。
3. **AI 那一条：画廊和漫画分开**（用户自定义答案，不是我给的两个选项之一）。
   事实澄清：`block_ai` 这把**已经存在**（`security/guard/ContentGuardManager.kt:137`，默认 false），
   而且画廊与漫画**现在共用它**（画廊的屏蔽走 `ContentGuardManager.findGalleryBlockedRule`，
   入口在 `GalleryPostScreen.kt:338-345`）。所以"分开"= **开关分家**：画廊设置里自己放
   「屏蔽 AI」+「显示 AI 角标」两枚，漫画守卫页那枚只管漫画。
   ⚠️ 边界（我拍的，若不对请纠）：**只分 AI 这一把** —— NSFW 打码与自定义黑名单仍共用守卫页那一份规则表，
   AI 词表也**不复制第二份**（`AiTagKeys` 与整词判据 `GalleryBlockMatch.kt:22-31` 继续只有一处定义），
   漂的那一半通常是被抄的那份。
   → **落地时的修正（见 §七）**：画廊侧不是"照抄"而是"以那张表为准再**只收窄**"——
   复探针查出裸标签 `ai` 在 yande.re 是**角色名**，那一枚在画廊侧必须剔掉，漫画侧保留。
4. **批次节奏**：分两批，先做四条低风险的（就是这份文档的 C1）。

## 一、C1 四条的落点与口径

新设置组「**大图页**」挂在 `feature/settings/GallerySettings.kt` 现有四组之后
（①账号与密钥 ②浏览与布局 ③缓存 ④下载 → **⑤大图页** → ⑥内容与屏蔽）。
C2 的动图三档与背景四档也归⑤，AI 两枚归⑥。

### C1.1 屏幕常亮

- 偏好 `pref_gallery_keep_screen_on`，**默认 true**（与阅读器 `keepScreenOn` 同默认，`VeneraPreferences.kt:52-53`）。
- 实现照 `reader/VeneraReaderScreen.kt:434-444` 那段：`DisposableEffect(档)` 里
  `window.addFlags(FLAG_KEEP_SCREEN_ON)`，`onDispose` 一律 `clearFlags`。
  画廊这页本来就在独立 Activity（`GalleryPostActivity`）里，`LocalView.current` 已经在
  `GalleryPostScreen.kt:150` 取好了，走 `view.context as? Activity` 拿 window。
- **这条没有算式可抽 → 没有单测点**，验点全在真机（停在一张图上等它灭屏）。
  写进文档而不是含糊过去：四条里只有一条是"测不了只能看"的。

### C1.2 音量键翻页

- 偏好 `pref_gallery_volume_key`，**默认 false**。
  阅读器那把默认是开的，但画廊这页两点不同：窗口是透明玻璃窗（`Theme.Venera.GlassOverlay`）、
  根节点**今天没有任何 focus 件**（全文件 grep `focusable` / `focusRequester` 零命中）。
  默认开 = 把系统音量键抢过来走一条没验过的链，所以默认关。
- 实现照 `VeneraReaderScreen.kt:570-597`：根 `Box` 补 `focusRequester + .focusable() + .onKeyEvent{}`，
  只吃 `ACTION_DOWN`；下=下一张、上=上一张。
- **端点不循环、不弹回**：阅读器到章末会跨章继续，画廊没有"下一章"，`GalleryVolumeKeys` 在端点返回 false
  （事件交回系统 = 到顶按一下还能听见系统音量条，这是刻意的"我们没吃掉它"）。
- 判据抽 `GalleryVolumeKeys.shouldTurn(enabled, isVolumeDown, currentPage, pageCount): Int?`
  （返回目标页或 null）→ 4 条用例。

### C1.3 自动连播间隔

- 偏好 `pref_gallery_autoplay_sec`（Int，**0 = 关**，默认 0）。UI 用现成 `SettingsSlider`
  （`SettingsComponents.kt:250-258`，用例 `ReaderSettings.kt:45-46`），range `0f..30f` + `steps=29`
  = 整数 0~30，suffix「 秒」，0 那一格 summary 写"关闭连播"。
  （用户原话是"比如每 5 秒切一张和可以手动设置时间"，滑条比三档选择器更贴这句。）
- 语义 = 用户 09-29 已拍过的那条：**沿当前那面墙自动翻到下一张、到底停**。
- 停下条件（四条都进判据，不留在 composable 里）：`sec<=0`、信息弹层开着（`infoOpen`）、
  当前页是视频（按 §〇.2：视频不吃自动播）、当前页在缩放态（`zoomedIn`，
  `GalleryPostScreen.kt:185-190` 已经有这份状态）、已经是最后一页。
- 实现挂 `LaunchedEffect` 在 `pagerState.currentPage` 上；换页 = 计时器自然重启
  （key 变了旧协程取消），不做"暂停后恢复"那种额外状态。
- 判据抽 `GalleryAutoPlay.shouldAdvance(sec, infoOpen, isVideo, zoomed, currentPage, pageCount): Boolean`
  → 用例钉"到底停""视频那页不停""弹层开着不停"。

### C1.4 智能预加载

- 偏好 `pref_gallery_preload`，枚举 `GalleryPreloadMode{OFF,NEXT,BOTH_TWO}`，**默认 NEXT**（= 今天的实际行为）。
- 这一档**同时管两件事**（只管一件就是半假开关）：
  1. `HorizontalPager` 的 `beyondViewportPageCount`：`OFF→0 / NEXT→1 / BOTH→2`
     （现状硬编码 1，`GalleryPostScreen.kt:500-505`，注释已经写明它就是为了"翻过去能接上"）；
  2. 对允许范围内的页各发一笔 `imageLoader.enqueue(galleryLargeRequest(...))` ——
     **必须复用 `galleryLargeRequest`**（`GalleryPostScreen.kt:995-1010`），因为它同时带着
     `memoryCacheKey`/`diskCacheKey`（且视频条目会换成 `videoPosterUrl` + 另一个键名 `"poster"`）。
     自造一份请求就是"预加载与显示不同键"= 白下一遍。实现口径照阅读器那段
     （`VeneraReaderScreen.kt:388-401`，`enqueue` + 同一 cacheKey 由并发去重兜住）。
- **一条不藏着的话**：`OFF` 关的是"我们主动预取的那批 + 邻居预组合"，
  **不等于没有网络流量** —— 当前页自己的三档（fast/large/file）照旧按现状发。
  而 `NEXT`/`BOTH` 会让 large 档提前落地，**磁盘写入跟着涨**（那把 512 MB 缓存刚在第五轮才真正生效）。
  这一取舍写进设置页的 summary，不让它当假开关。
- 判据抽 `GalleryPreload.plan(mode, currentPage, pageCount): List<Int>`（夹边界、去重、不含当前页）→ 用例
  另加一条锁住映射：`GalleryPreload.beyondPages(mode)`。

## 二、C2 已定口径（下一条方案直接照这份写，不重开问题）

1. **动图自动播放三档**（仅 Wi-Fi / 始终 / 从不）：只管**大图页**的 GIF/动图 WebP；
   墙上的卡片**强制解首帧静图** —— 这是刚修完 OOM 之后最稳的口径，
   代价是要在解码器前插一层"按请求选静/动"的 `Decoder.Factory`（Coil 3.6.2 没有现成的 per-request 开关；
   `AnimatedImageDecoder.Factory(Boolean)` 那个参数是 `enforceMinimumFrameDelay`，**不是**动画开关，已实测确认）。
   还要新增：`ConnectivityManager` 网络类型判据（**全仓零现成实现**，grep 已确认）+ manifest 补
   `ACCESS_NETWORK_STATE`（普通权限，无运行时弹窗）。
2. **AI**：画廊独立的「屏蔽 AI」+「显示 AI 角标」两枚（词表与整词判据不复制，见 §〇.3）。
   角标位置与既有角标（视频时长药丸 `GalleryVideoPill`）同一条口径：没有信号就**不摆**，不编。
3. **背景颜色四档**：改的是 `:459` 那层 scrim 与是否保留 blur-behind
   （纯黑=不透明黑底、可以省掉 blur；现状=blur 32dp + 0.45 压暗）。
   `opaqueAmbientBackground` / `blurBehindDp` 已经是 `VeneraSubActivityBase` 的**子类可覆写项**
   （`GalleryPostActivity.kt:33,39`），所以这一档不需要动基类。

## 三、C1 明确不做的事

- 不做"自动连播时顺带切清晰度/换 HD 档"（与 `hdUids` 那套手动语义冲突）。
- 不给音量键加"长按连续翻页"（阅读器也没有，且会抢系统音量条）。
- 不在 C1 引入任何新依赖、新权限，不动保护域（`Navigation.kt`、底栏枚举）、不动 `GalleryImageLoader` 预算。

## 四、QA 判据

- 三条新判据全部纯函数 → `GalleryViewerPoliciesTest` 钉住；常亮那条例外的理由已在 §一.1 写明。
- 构建：`:app:testDebugUnitTest` 与 `:app:assembleDebug` 必须双绿（本轮基线：49 套 / 356 条 / 0 失败）。
- 真机待验四条：① 大图页停留会灭屏/不灭屏（跟档位）；② 开音量键后能否翻页、到端点还能不能调音量；
  ③ 连播是否到底就停、弹层开着是否不走、视频页是否不走；④ 三档预加载下翻页的接得上程度与
  设置页「当前占用」读数的变化。

## 五、C1 落地记录（同日，用户「同意，按 C1 方案开工」之后）

| 落点 | 文件:行 |
|---|---|
| 三条判据（音量键 / 连播 / 预加载） | `gallery/domain/GalleryViewerPolicies.kt`（新，`GalleryVolumeKeys` `GalleryAutoPlay` `GalleryPreload` + `GalleryPreloadMode`） |
| 12 条用例 | `app/src/test/java/com/venera/compose/gallery/GalleryViewerPoliciesTest.kt`（新） |
| 4 条偏好 + setter | `data/prefs/VeneraPreferences.kt:177-227` 与 KEY `:540-543`（常亮默认开、音量键默认关、连播默认 0=关且 setter 夹 0..30、预加载默认 NEXT） |
| 大图页接线 | `gallery/ui/GalleryPostScreen.kt:167-172`（读档）`:212-225`（常亮 flag）`:227-242`（连播）`:244-252`（预加载 enqueue）`:515-534`（音量键 + 焦点件）`:586`（`beyondViewportPageCount` 换成档位驱动） |
| 设置页新组 | `feature/settings/GallerySettings.kt:191` 起「大图页」四行（下载组之后、内容与屏蔽之前） |

三条实现上值得记的口径：

1. **预加载复用 `galleryLargeRequest`**，不另造 `ImageRequest`：那个函数同时带着 memory/disk 两个
   cacheKey（视频条目还会换成 `poster` 键）。自造请求 = 预加载与显示两个键 = 白下一遍。
2. **连播在 `delay` 之后又判一次**才真翻页：等的那几秒里人会放大、开面板、自己翻页，
   按旧读数把人翻走是最难查的那类"它自己在动"。
3. **音量键"关"= 一口不吃**：`targetPage` 返回 null → `onKeyEvent` 回 false → 事件交回系统，
   按音量键还是音量条。半吃（只吃 DOWN 不吃 UP）会被读成"这键坏了"。

写给自己的一条：`GalleryPreloadMode.OFF` 的设置页 summary 明确写了"不等于没有流量"——
这一档关的是主动预取与邻居预组合，当前页自己的三档照旧发。不写这一句它就是假开关。

构建：`:app:testDebugUnitTest` **50 套 / 368 条 / 0 失败 / 0 错误**（上一基线 49 / 356，
新增 12 条全在 `GalleryViewerPoliciesTest`），`:app:assembleDebug` BUILD SUCCESSFUL。
C1 四条没有一条在真机上跑过 —— §四那份待验清单原样有效，尤其②（透明玻璃窗 Activity 里的焦点链
能不能收到音量键，是这轮唯一"照抄了但环境不同"的一条）。

## 六、真机第六轮：三条读数其实是同一个根因

用户带着截图回来，四条：① 连播自动翻页"会这样"（截图 = 两页各露一半、中间一大片空白）；
② 大图页底栏要能直接控连播、且可调速度；③ 画廊有**两枚**加载图标，下拉那枚"已经加载好了还在转"；
④ "猜你喜欢他只摆了 40 张"。

### ③④ 同一个根因：`withContext(NonCancellable)` 换掉了 `coroutineContext[Job]`

`GalleryForYouViewModel` 那两处在 `finally` 里这样清在途标志：

```kotlin
finally { withContext(NonCancellable) { if (loadJob === coroutineContext[Job]) isLoading = false } }
```

意图是对的（被新一轮取代的旧笔不许替新的灭灯），但 **`NonCancellable` 本身就是一个 `Job` 元素**：
进了那个块，`coroutineContext[Job]` 读到的就是 `NonCancellable`，与 `loadJob` 恒不相等 →
守卫恒不成立 → `isLoading` / `isLoadingMore` **永远停在 true**。
库行为写成了用例钉住：`NonCancellableJobIdentityTest`（1 条，先跑它证明再改）。

一条根因解释两条读数，逐条对得上：

| 读数 | 通路 |
|---|---|
| 下拉环转不停 | `isRefreshing` 里含 `fvm.isLoading`，而它永不清 |
| 同时两枚加载图标 | 顶部下拉环 + 页尾 `isLoadingMore` 那枚，两个永真标志各画一遍 |
| 只摆 40 张 | `loadMore()` 第一道闸 `if (isLoading \|\| isLoadingMore) return` 永远进不去 → 第 2 页从不发；40 = 第 1 页两站各 `PER_SITE_PAGE = 20` |

修法：进协程先 `val self = coroutineContext[Job]` 抓住自身，块内比 `loadJob === self`。
`GallerySearchViewModel` 那两处同形守卫**没这个毛病**（它们在裸 `finally` 里比，外面没有 `withContext`）
—— 这也解释了为什么搜索那头翻页一直正常、只有猜你喜欢停住。

顺带把另一处独立的错一起修了：`isRefreshing = vm.isLoadingFeed \|\| fvm.isLoading` 绑的是**两页的并集**，
而 `onRefresh` 只派发给当前那一页；两页同时被组合（`beyondViewportPageCount = 1`），
所以在日榜下拉，环要等**另一页**取完才停。改成按当前页绑（`GalleryScreen.kt:763-768`）。

### ① 连播停在半页：effect 把自己发起的动画取消了

`LaunchedEffect` 的 key 里有 `pagerState.currentPage` 与 `current?.isVideo`（后者由前者推出）。
`animateScrollToPage` 滚过 50% 那一刻 `currentPage` 变 → key 变 → **effect 重启 → 正在跑的动画被取消**
→ pager 冻在 `offsetFraction≈0.5`，两页都不贴边。
修法：**计时归 effect（key 变就重来），动画归页面 scope** —— `scope.launch { animateScrollToPage(...) }`。
这条是我上一轮引入的，成因写在这里而不是悄悄改掉。

### ② 底栏第 6 颗：点 = 开关，长按 = 展开速度

形态按用户选的推荐项落地（`GalleryViewerToolbar.kt`）：
dock 右侧加第 6 颗 `▶/⏸`（开启染主色，与 HD 那颗同一条约定），**长按**在 dock 上方展开一条
`连播 ──●── N 秒` 的滑条（1~15 秒；上限比设置页那条 0~30 窄，那一条还要能表达"关"）。
`IconButton` 不吃 `onLongClick`，所以这颗是 48.dp 的 `Box + combinedClickable`
（仓库先例 `components/ComicTileLayout.kt:66`）。

**速度只管本次这一屏**（用户拍板）：进页取偏好默认值，页内改动用 `remember` 存，
退出即丢、不写回 `pref_gallery_autoplay_sec`；设置页那条的 summary 已补上这层关系。
关掉再点开回到上一次的速度（不是弹回默认值 —— 那会读成"我刚才调的那一下没生效"）。

### ④ "无限滑动"没有做成新需求

用户那句"他只摆了 40 张"是在**报现象**，不是要"到底重抽一批"。翻页链路本来就在
（`GalleryScreen.kt:576-598` 那道 snapshotFlow 闸门 + `fvm.loadMore()`），卡住的就是上面那个永真标志。
所以本轮不加"重抽标签"那套新东西 —— 修完标志，两站各 100 条一页的翻页就正常续下去了；
真到底（某站返回 < 100 条）时页尾仍如实念"已经到底"，那是站方给完了，不是我们停了。

### 本轮 QA

`:app:testDebugUnitTest` **51 套 / 369 条 / 0 失败 / 0 错误**（+1 = `NonCancellableJobIdentityTest`），
`:app:assembleDebug` BUILD SUCCESSFUL。
待真机复看四条：① 连播翻页是否贴边（不再留半页空白）；② 下拉环是否随当前页取数结束就停；
③ 猜你喜欢能否翻过 40 张继续往下；④ 底栏第 6 颗：点能开关、长按出滑条、染色跟状态走。

## 七、C2 四条落地（同日，用户「做好后接着做 c2」之后）

四条的口径全部沿用 §〇 与 §二，没有重开问题。判据仍一律抽进 `gallery/domain`（本项目单测没有 Robolectric）。

| 落点 | 文件:行 |
|---|---|
| 动图三档判据 | `gallery/domain/GalleryMotion.kt`（新，`animates(mode, unmetered)`；枚举首项 = 默认档 `WIFI_ONLY`） |
| 网络读数 | `gallery/data/GalleryConnectivity.kt`（新）+ `AndroidManifest.xml:6` 补 `ACCESS_NETWORK_STATE` |
| 解码闸门 | `gallery/data/GalleryAnimationGate.kt`（新）+ `gallery/data/GalleryImageLoader.kt:139`（`add(GalleryAnimationGate())` 取代裸注册的 `AnimatedImageDecoder.Factory()`） |
| AI 判据 | `gallery/domain/GalleryAi.kt`（新，以漫画侧 `AiTagKeys` 为准再收窄）+ `security/guard/ContentGuardManager.kt:40`（那张表改 `internal`） |
| 背景四档 | `gallery/domain/GalleryViewerBackdrop.kt`（新，深灰取 `0xFF121212` = 全仓唯一现成深灰口径 `components/backdrop/VeneraLiquidGlassNavBar.kt:119`） |
| 4 条偏好 | `data/prefs/VeneraPreferences.kt:231-276` 与 KEY `:593-596` |
| 大图页接线 | `gallery/ui/GalleryPostScreen.kt:177-186`（读档 + 网络**只问一次**）`:272-277`（预加载键带上 `animateGifs`）`:970-971`（两档请求透传）`:1141-1179`（`galleryLargeRequest` / `galleryFileRequest` 表态）`:570-577`（背景那一层按档位画） |
| 四面墙 AI | `gallery/ui/GalleryScreen.kt:1056-1061`（`buildGalleryWall` 多一个 `blockAi`）`:412,450,458`（日榜/搜索/推荐）`:232`（读档）+ `gallery/ui/GalleryFavoritesBody.kt:96,113-115`（收藏墙同一把判据） |
| AI 角标 | `gallery/ui/GalleryScreen.kt:1206-1208`（开关 + 命中判定）`:1243-1244`（`GalleryAiPill` = 复用既有 `GalleryCornerPill`，左上角）`:1248`（`AI_BLOCKED_RULE = "AI 生成"`） |
| 设置页 | `feature/settings/GallerySettings.kt:231-257`（「大图页」两组新 Select）`:259-280`（「内容与屏蔽」两枚 Toggle + 改写指路那行） |
| 12 条用例 | `app/src/test/java/com/venera/compose/gallery/GalleryViewerPoliciesC2Test.kt`（新） |

三条实现上值得记的口径：

1. **闸门不"降级成静态解码器"，而是"链上根本没有动图解码器"**。第一反应是给 `never` 档返回
   `StaticImageDecoder`，但那是错的：内置那两个静态解码器对动图会**双双拒绝**，请求就会顺着链
   落到动图解码器上，"从不"当场失效。所以 `GalleryAnimationGate` 没表态时返回 `null`，
   让内置那两个只出首帧的解码器接住 —— 默认静，是**结构**上的默认，不是一句 if。
2. **动/静只分内存键，不分磁盘键**（`large` ↔ `large-a`、`file` ↔ `file-a`，`poster` 不参与）。
   磁盘上是编码字节，与解法无关，分两份就是白占一倍的 512 MB；内存里存的却是解出来的
   `AnimatedImage` 或位图，共用一条键会出"从『始终』改成『从不』之后那张图还在动"。
3. **背景那三档不透明底是靠"盖住"模糊的，不是靠关 `FLAG_BLUR_BEHIND`**。§二.3 原计划改
   `opaqueAmbientBackground` / `blurBehindDp`，实施时发现那两个是 **Activity 起来时读一次**的覆写项，
   组合期改它没有读数可改（同一个坑见 `miuix-topbar-state-not-observable`）。代价如实写进了设置页文案：
   从"纯黑"改回"现状"要**重进大图页**才恢复模糊。这一条比"偷偷多做一层运行时切 flag"要诚实。

AI 那两枚按"命中一条屏蔽规则"记账（`AI_BLOCKED_RULE`），而不是新增第三个计数器：
页尾那句"被屏蔽规则收起 N 张"本来就按成因分行念，加一个平行读数只会让人数不明白。

### 复探针：把上一轮记的一条 AI 读数**改错回来**

本轮写 `GalleryAi.kt` 时，我在它的头注里记了一句「实测 yande.re 只有 `ai-generated`，83 条」。
收尾前重跑同一支探针，**这条复现不出来**，而且顺着查出一个真会误伤的形状：

| 探针（2026-09-29 复跑，走 `127.0.0.1:7890`） | 读数 |
|---|---|
| `yande.re/post.json?tags=ai-generated&limit=1` | 200，body `[]` |
| `yande.re/post.json?tags=ai_generated` / `generated_by_ai` / `ai_drawn` / `male:ai_generated` | 全部 0 条 |
| `yande.re/post.json?limit=1`（**不带 tags 的对照**） | 200，正常返回数据 → 请求与代理都是通的 |
| `yande.re/post.json?tags=ai&limit=100` | **19 条**，标签串是 `ai maid pointy_ears sage shuffle suzuhira_hiro tick_tack` |
| `gelbooru.com/…&tags=ai_generated` / `ai-generated` 第一页卡片数 | 各 **9** 张（阴性对照 `zzz_not_a_tag` = **0** 张） |
| `gelbooru.com/…&s=view&id=14985441` 标签链接 | 条目标签串上写的是**连字符**那一版 `ai-generated` |

同一批结论换了**一条独立通路**复核过（站方 HTML 列表页数去重卡片，不是只信 JSON API）：
`yande.re/post?tags=X` → `landscape` 40（页上限）、`ai` **19**、`ai-generated` **0**、`ai_generated` **0**、
`1girl` 0、`zzzqqxx` 0。

顺带一条自审：第一次跑 HTML 复核对时**所有标签都是 0**，包括 `landscape` —— 那是我照 Gelbooru 的
`a id="p<id>"` 去套 yande.re，它的卡片标记是 `/post/show/<id>`。
"整站全 0"这种形状第一反应应该是**我的标记错了**，不是站方空了；换标记后 `landscape` 立刻 40 张。
另：`1girl` 在两处都是 0 也印证了 yande.re 的标签面很窄（这一站只收风景/插画，人物通用词基本不存在），
所以"这一站没有 AI 标签"不是探针没打中，是它真的没人打。

三条结论，全部落进判据与用例：

1. **yande.re 现在根本没有人打 AI 标签**（四种写法全 0）。上一句"83 条"是错的，撤回。
2. 那一站的裸标签 `ai` 是**角色名**（《Artery Gear》的 AI）。漫画侧词表里的 `ai` 那枚是给 EH 用的，
   画廊侧照抄它就会把 19 张手工插画判成 AI 画 —— 所以 `GalleryAi` 在共用表的基础上
   **剔掉裸 `ai`**（只收窄不放宽），钉在 `裸标签 ai 不算 —— 它是角色名` 那两条断言上。
3. 归一 `_`↔`-` 仍然要留：Gelbooru 搜索侧两种写法都出图，而它写在条目上的那一版是连字符 ——
   不归一防的不是今天，是站方哪天换写法。

顺带一条自审：本轮中途我数出过「53 套」，与 §六 的「51 套 + 1」差一套 —— 查下来是
`test-results/` 里躺着一份**上一轮遗留**的 XML（早先删过的那个测试类）。第二次跑之后目录里
52 份全是同一时间戳，才是本轮真相。数结果要按时间戳数一遍，别拿目录里文件数当基线。

### 本轮 QA

`:app:testDebugUnitTest` **52 套 / 381 条 / 0 失败 / 0 错误**（§六 基线 51 / 369，本轮 +1 套 +12 条，
新增全在 `GalleryViewerPoliciesC2Test`），`:app:assembleDebug` BUILD SUCCESSFUL。
过程中先红后绿的一条：`buildGalleryWall` 把新参数 `blockAi` 加在函数类型参数**后面**，
于是那四处 `buildGalleryWall(...) { post -> }` 的尾随 lambda 不再绑到 `blockedRuleOf` ——
四个调用点全炸。改成放在函数类型参数**之前**（尾随 lambda 只能绑最后一个参数）。

### 待真机验（累计，本轮没有一条在真机上跑过）

C1 四条：常亮跟档位、音量键在透明窗 Activity 里收不收得到、连播到底是否停、三档预加载的接得上程度。
第六轮四条：连播翻页是否贴边、下拉环是否随当前页停、猜你喜欢能否翻过 40 张、底栏第 6 颗点与长按。
**C2 新增五条**：
① 「仅 Wi-Fi」下用移动网络时动图是否真的停在首帧、切回 Wi-Fi 重进是否动；
② 墙上卡片是否**恒静帧**（这一档与设置里那三档无关，是最容易做错的一半）；
③ 从"始终"改到"从不"后，那张已经动过的图是否立刻静下来（内存键分开的唯一证据）；
④ AI 角标在两种写法下都摆、且 `ai` 那 19 张角色图**不摆也不被屏蔽**；
⑤ 背景四档切换的观感，以及"改回现状要重进页面才恢复模糊"这条是否读得通。



