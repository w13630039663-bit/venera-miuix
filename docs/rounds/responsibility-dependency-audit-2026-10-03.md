# 职责 / 依赖审计（2026-10-03）

> 范围：`app/src/main/java/com/venera/compose`（405 颗 `.kt`）+ `:desktop`(32) + `:engine-probe`(7)。
> 性质：只读审计，不改行为。行号是 2026-10-03 的快照，复核式在每节末尾给出，条目级判定以代码为准不以本文档为准。
> 动手范围：本文档 §四（Part A 六批）是本轮唯一执行面；§五（Part B）是 Windows 移植阶段 2 的输入，本轮不动。

## 一、结论摘要

1. **依赖方向：没有底，只有一张网。** 包级图上 46 条粗边里 11 条存在反向边。真正干净的底层只有三处：`source/model`（21 条入边 / 0 条出边）、`ui/tokens`（45 入 / 1 出）、`engine/` 里除 `VeneraJsEngine.kt` 之外的 7 颗 handler（对 `com.venera.compose.*` 零 import，`:engine-probe` 就靠排除那一颗复用整目录）。`data/` 不是底层，是全仓公共总线。
2. **两个插座：** `data/prefs/VeneraPreferences`（46 个调用文件、跨 9 个顶层包）与 `security/guard/ContentGuardManager`（29 个文件、6 个包）。后者是画廊隔离被破的隐形主通道。
3. **职责：13 颗文件把 6-13 类职责并在一处**，最重的三处越界是「渲染文件里跑业务」与「状态持有者与渲染混体」，见 §二。
4. **重复：18 处顶栏地板零命名常量**、11 个偏好存储名有 10 个绕过常量、`"shared_images"` 在代码与 `FileProvider` 配置里各写一份。这些不是风格问题，其中三条是「改一处即崩 / 即丢用户数据」的路径。
5. **测试面与枢纽错位：** `app/src/test` 94 颗 / 773 个 `@Test`，集中在 policy、纯函数、DAO；`feature/` 只有 13 颗。`Navigation.kt`、`VeneraReaderScreen.kt`、`GalleryScreen.kt` 这三颗最大的枢纽**零测试**，而它们同时又直接 `getInstance` 取全局单例，是事实上无法独立测试的块。

## 二、依赖方向

### 2.1 包级互环（11 组，数字 = 双向各自的 import 条数）

`data↔source 1/9`、`data↔feature 1/93`、`data↔ui 1/4`、`data↔gallery 6/39`、`data↔components 7/2`、`source↔feature 3/58`、`security↔feature 1/6`、`sync↔feature 2/6`、`components↔feature 5/242`、`components↔gallery 1/69`、`feature↔gallery 17/5`。

`data` 一个包同时与 `source / feature / ui / gallery / components` 五个包成环。

### 2.2 逐条违规

| # | 违规 | 证据 | Android 侧当前有无症状 |
|---|---|---|---|
| 1 | 数据包里放 Activity | `data/network/CloudflareBypassActivity.kt:48` 是 `ComponentActivity`，:41 import `feature.VeneraTheme`、:28 import `components.venera.VeneraTextButton`；全仓引用面只有 `AndroidManifest.xml:207` 与 `data/network/CloudflareBypassManager.kt:80-82` | 无 |
| 2 | 安全层抓 UI 包 | `security/guard/ContentGuardManager.kt:9` import `feature.ComicItem`。那颗是 49 行、零 `@Composable`、纯 `@Serializable` 的数据类，:6-14 的 9 条通配 import 全部无用，还凭空造出 `feature→reader / →data.db / →data.prefs` 三条假编译边 | 无 |
| 3 | 源抽象层公开签名点名 UI 类型 | `source/ComicSource.kt:163,178`、`source/copymanga/CopyMangaSource.kt:284,290,296,306` 用全限定名 `com.venera.compose.feature.sourcemanage.SourceSettingItem`；`source/js/JsComicSource.kt:9-11` import 同包三枚 DTO | 无 |
| 4 | `data/db` 反向抓 `source` 单例 | `data/db/FollowUpdatesRepository.kt:4,44`。`desktop/build.gradle.kts:51-53` 的排除注释自述理由包含这条 import | 无（并行线已把这两颗排进阶段 2） |
| 5 | 偏好层抓 UI 与画廊 domain | `data/prefs/VeneraPreferences.kt:7-12` import 6 枚 `gallery.domain.*` 枚举、`:13` import `ui.tokens.ThemeSeedPresets`。而 `ui/tokens/Color.kt:462-467` 的注释写着「`data.prefs` 那层不该依赖 Compose 类型」——文档与代码互相打脸 | 仅注释矛盾 |
| 6 | 偏好层抓组件层 | `data/prefs/ComicListPreferences.kt:5` import `components.normalizeComicDisplayMode`（定义在 `components/ComicPresentationPolicy.kt:16`，一行的纯函数） | 无 |
| 7 | 数据层导出 `@Composable` | `data/tags/TagDisplay.kt:23-45`，用 `LocalContext`。消费点 `feature/ComicDetailScreen.kt:90` 与冻结的 `feature/SearchScreen.kt:56` | 无，且桌面明确不带 `opencc.txt`（`desktop/build.gradle.kts:69-70`）→ 判不做 |
| 8 | UI 直连基础设施 | `reader/VeneraReaderScreen.kt:445`（`LaunchedEffect` 里 `HistoryDao.getInstance(context).saveHistory`）、`feature/sourcemanage/ComicSourceViewModel.kt:569-571`（VM 内裸 `okhttp3.Request.Builder`）、`feature/ComicDetailViewModel.kt:175-177`（一颗 VM 抓 `ComicSourceManager`/`LocalFavoritesManager`/`VeneraPreferences`/`HistoryDao` 四把单例）、`gallery/ui/GalleryScreen.kt:411` 与 `GalleryDailyScreen.kt:118`（Composable 直调 `HostCircuitBreaker.reset`） | 有隐患无故障；拆它们要动渲染时序，见 §六 |
| 9 | 进程级可变全局 | `data/network/HostCircuitBreaker.kt:26,39`（`object` + `ConcurrentHashMap`）、`data/network/PreferredIpRuntime.kt:24,41-52`（双 `@Volatile var`）、`feature/Navigation.kt:264`（`object EntryIntentHandoff` 持 UNLIMITED Channel 作跨 Activity 深链总线） | 无 |

### 2.3 画廊隔离

项目口径是「图库模块必须与漫画完全隔离」，实际双向都破，且不是随机点位而是三类系统性共用：**偏好、内容守卫、存储根**。守住约束的只有两颗入口 Activity（`GalleryPostActivity`、`GalleryArtistProfileActivity`）。

- **漫画侧 → 画廊**：`feature/Navigation.kt:74-78`（含画廊内部状态类 `GalleryViewModel`，并在 :829-832 跨目的地复用它的缓存池）、`feature/FavoritesScreen.kt:12`（漫画收藏页内嵌 `gallery.ui.GalleryFavoritesBody`）、`feature/settings/GallerySettings.kt:25-31`、`feature/sourcemanage/{GalleryAccountCard.kt:45, SauceNaoKeyCard.kt:34}`、`feature/settings/PreferredIpSpeedTestScreen.kt:40-41`、`data/prefs/VeneraPreferences.kt:7-12`、`components/venera/VeneraGallerySourceMark.kt:18`、`sync/{BackupManager.kt:19-20, GalleryBackupRows.kt:3-5}`。
- **画廊 → 漫画侧**：`gallery/data/GallerySaver.kt:11` import `download.ComicStorageRoot`（共用漫画侧存储根）、`gallery/data/{GalleryImageLoader,GallerySaver}` import `data.prefs.VeneraPreferences`、`gallery/data/GalleryTagDictionary.kt:6` import `data.platform.android.openReadOnlySqlDatabase`、`gallery/domain/GalleryAi.kt:3` import `security.guard.AiTagKeys`、`gallery/ui` 4 处 import `feature.LocalVeneraDarkTheme` + 1 处 `feature.MiuixSectionHeader`（`GalleryHomeSections.kt:47`）+ 6 处 `security.guard.ContentGuardManager`、另有 14 条指向 `data.network`。
- 若把共享设计系统（`components/` 69 条 + `ui/tokens` 39 条）也算漫画侧，违规量翻倍。**本轮判定：设计系统算中性基建，可共享；`data.prefs` / `security.guard` / `download` 三条是真账。**

### 2.4 复核式

```
# 反向依赖（在 app/src/main/java/com/venera/compose 下）
Grep ^import com\.venera\.compose\.(feature|components|ui\.|gallery\.)  glob {data,source,engine,security,sync,download}/**/*.kt
Grep ^import com\.venera\.compose\.(feature|components|ui\.|source|download|reader|engine|security)  glob gallery/{data,domain}/**/*.kt
Grep ^import com\.venera\.compose\.(feature|components|ui\.|security)  glob gallery/ui/*.kt
Grep ^import com\.venera\.compose\.gallery\.  glob {feature,components,data,sync,download,reader,security}/**/*.kt
# 包级图与互环：把每条 import 的首段目录折叠成节点求反向边
```

## 三、职责

13 颗文件承担 6-13 类职责。行数为 `wc -l` 实测。

| 文件 | 行 | 职责构成 | 最重的一处越界 |
|---|---|---|---|
| `reader/VeneraReaderScreen.kt` | 2202 | 主体 `ReaderSessionContent` 152-1596 ／ 状态持有者 `DynamicPageState` 1602 + `rememberDynamicPageResolution` 1691 ／ 页件 1718,1881 ／ 落盘·收藏·分享 1970,2039,2130 | **全文件 `viewModel()` 出现 0 次**：:197-204 直订阅 prefs、:1383-1537 面板内直写 `prefs.setX`、:445 直调 `HistoryDao`、:2160 直写 `cacheDir/shared_images` |
| `feature/ComicDetailScreen.kt` | 2356 | 主体 124-1771 ／ 收藏面板 `FavoritePanelSheet`:2000 ／ 顶栏玻璃件 2241-2339 ／ 纯逻辑 `NUMERIC_ONLY_VALUE`:1772、`detailMaskState`:1848、`findReadTargetChapter`:2340 ／ 标签件 :1780 | 批量下载弹窗 1641-1771 在组合期逐章查盘（:1642 `downloadChapters(liveDetails, …)` + `downloadManager.isChapterDownloaded`）；:246 直读 prefs |
| `feature/Navigation.kt` | 1118 | 仅 3 个 `@Composable`。路由 DTO 109-223 ／ `CoverTransitionHost`:233 ／ 深链总线 :264 ／ `handleEntryIntent`:281 + 文案表 `entryLinkMessage`:313 ／ `VeneraShellViewModel`:322 ／ 转场策略 344-470 ／ `VeneraComposeApp` 475-1118（含 17 个 `composable<>` 目的地体 665-1050） | 导航文件里同步走网络：:288 起调 `ComicLinkResolver.getInstance(context).resolve(url)`，失败 :295 直接 `android.widget.Toast.makeText`；:322 的状态类持 `ReaderSession`、`ComicItem` 与一张**不清理的** `favoriteImagePayloads: MutableMap`；:491-497 根 Composable 直读偏好并做 `SDK_INT >= TIRAMISU` 判定 |
| `source/ComicSourceManager.kt` | 1466 | 注册表 + meta 落盘 81-494 ／ 仓库同步·安装·更新 495-834 ／ 聚合搜索 835-913 ／ 逐源代理 914-1233 ／ 图片与缩略图 LruCache + 并发去重 961-1094 ／ 链接解析 1095-1118 ／ ping + 熔断 1234-1275 ／ Web 登录 cookie·localStorage 持久化 1291-1381 | 一个进程级单例里住著 **6 颗 `MutableStateFlow` 当 UI 状态用**（:112-122、:495-510、:669）；`val jsEngine: VeneraJsEngine by lazy`:124 把引擎生命周期也收进来 |
| `gallery/ui/GalleryScreen.kt` | 1889 | 主体 154-1177 ／ 常量与 `GalleryLoadMoreState` 1178-1231 ／ 域过滤纯函数 `buildGalleryWall`:1249 ／ 共享墙 `GalleryCardsGrid`:1280 + `GalleryPostCard`:1487 ／ 预取 :1424 ／ 胶囊与提示件 1645-1773 ／ 页体 `GalleryDailyPage`:1775 | `GalleryDailyPage` 的唯一消费者是同目录 `GalleryDailyScreen.kt:143`，却留在 1889 行的屏文件里靠跨文件 `internal` 调用；:257-261 组合期 `remember` 跑 `GalleryRecommendations.recommendBySite`；:296-303 用 `SideEffect` 写全局 `GalleryChromeAutoHide`；:411 直调 `HostCircuitBreaker.reset` |
| `feature/SearchScreen.kt` | 1865 | 主体 108-505 ／ `SearchQuickDialog`:506 ／ 18 个局部件 623-1833 | 检索判据 `searchVisibleTags`:1834、`conceptKey`:1856、`literalKey`:1862、`WHITESPACE_RUN`:1865 住在冻结屏内部，而同目录早就有 `TagSearchPolicy.kt`、`SearchPagination.kt`——**冻结面与被测面不重合** |
| `source/js/JsComicSource.kt` | 1856 | 单类约 50 成员、9 段功能：翻译字典 68-97 ／ 收藏桥 99-321 ／ 搜索 322-442 ／ 详情 443-610 ／ 章节页 611-686 ／ 图片与缩略图配置解析 687-873 ／ 链接解析 874-920 ／ 探索 921-1062 ／ 分类 + 排行 1063-1351 ／ 评论 1352-1445 ／ 点赞评分 1446-1518 ／ 源设置 1519-1632 ／ 账号·登录·Cookie 1633-1847 | 见 §2.2 #3：import UI 包三枚 DTO |
| `gallery/ui/GallerySearchArea.kt` | 1620 | 主体 165-813 ／ `ReverseTrailingActions`:814 ／ 常量 856-892 ／ chips 三件 893-1123 ／ 建议·推荐·历史 6 件 1194-1496 ／ `formatTagCount`:1497 | :1095-1118 在对话框 lambda 内做 `DatePicker` → `Instant.ofEpochMilli` → `LocalDate` 换算；:290 组合内 debounce `delay` 后调 VM |
| `feature/sourcemanage/ComicSourceScreen.kt` | 1611 | **只有 1 个顶层 `@Composable`（:80），函数体 1531 行**：概览卡 181-437 ／ 源行 + 三种设置项渲染 438-973 ／ 对话框区 974-1270 ／ WebView 登录挂载 :1273 ／ 仓库·更新·编辑 1290-1611 | 18 处 `remember { mutableStateOf }` 弹窗状态住在渲染函数里；同目录 `WebLoginScreen.kt` 已存在（:1273 就在调它），却仍在 :108-127 自持 12 组登录/编辑会话状态 |
| `gallery/ui/GalleryPostScreen.kt` | 1823 | 主体 168-1338 ／ Viewer 页与计数 1339-1410 ／ 动画常量 1411-1465 ／ `GalleryViewerMedia` 1466-1692 ／ 手势常量 1693-1719 ／ 解码档位与加载环 1784,1797 | **取图管道策略写在 Screen 文件**：`galleryCacheKey`:1720、`galleryFastRequest`:1730、`galleryLargeRequest`:1743、`galleryFileRequest`:1770，被 `GalleryVideoViewer.kt:76` 反向消费 |
| `gallery/ui/GallerySearchViewModel.kt` | 1291 | 约 30 颗 `mutableStateOf` 80-300 ／ 上下文栈与快照 472-617 ／ 排名窗判据 533-560 ／ 取数编排 `runSearch` 854-1042 ／ 分页去重 770-808 ／ 建议 1083-1142 ／ 历史持久化 1143-1160 | VM 兼任 repository + DAO：`searchLeg`:1043、`canonicalArtist`:1057 直连站点，`gelbooruAccount`:286，:1144 `AndroidKeyValueStore(app, "venera_gallery_search")` |
| `data/prefs/VeneraPreferences.kt` | 714 | 6 枚枚举 18-58 ／ 阅读器组 69-104 ／ 隐私·启动·搜索·章节 106-124 ／ CF 优选 IP 132-161 ／ 存储路径 162-171 ／ 画廊组 173-352 ／ 主题 353-380 ／ 收藏 382-428 ／ 代理 401-410 ／ 更新检查 429-455 ／ 装饰图 456-472 ／ setter 群 474-638 ／ 47 条 KEY 常量 645-698 | 画廊的清空与回落逻辑被迫实现进漫画侧偏好类（见 §2.3）；`getInstance`:708 又新建一份 `AndroidKeyValueStore` |
| `data/db/LocalFavoritesManager.kt` | 852 | DB 生命周期 64-116 ／ 文件夹 CRUD + 排序 131-211 ／ 查询搜索 211-311 ／ 增删 312-422 ／ 移动复制重排改标签 423-525 ／ 阅读联动 526-561 ／ 网络同步关联 562-655 ／ 行映射 688-753 ／ legacy 迁移 `migrateLegacyFavorites`:754 | 一个类同时是 DAO、Repository、迁移器与可观察缓存（3 颗 StateFlow 在 :46-56） |
| 其余 | — | `data/network/PreferredIpRules.kt` 710（站点表 + 主机匹配 + CIDR 采样 + 用户输入解析 + 调度判据 + 读数格式化 + 7 颗 DTO，而同目录已有 `PreferredIpProbe/Routing/Runtime` 三颗）、`download/DownloadManager.kt` 761（通知渠道 + 队列 + 离线文件查询 + 目录布局 + 下载执行 + 任务 JSON 落盘）、`engine/VeneraJsEngine.kt` 687（WebView 生命周期 + eval + stdlib 加载 + 消息路由 + **Cookie 桥 523-618**（`handleCookie`:523、`buildJsCookie`:586 造 okhttp Cookie 并落盘）+ 剪贴板桥 + UI 桥）、`ui/tokens/Color.kt` 538（token 文件里塞 `GallerySheetSection` 枚举 :404 与三处策略查找 :365,:430,:510）、`feature/ComicDetailViewModel.kt` 969、`feature/FavoritesScreen.kt` 1327 | — |
| 相对干净 | — | `ui/tokens/Spacing.kt` 561：无函数无组件，问题只是一个桶装了 11 个功能的专属尺寸 | — |

「拆过一半」的痕迹（说明这些边界是人为的、不是设计出来的）：`WebLoginScreen.kt` 已存在但 `ComicSourceScreen.kt:108-127` 仍自持会话状态；`GalleryAccountCard`/`SauceNaoKeyCard` 已迁出源管理页；`GallerySheetBlock.kt` 已在 `gallery/ui/` 里，而 `GalleryInfoSheet.kt` 仍把 FactGrid / 源链接 / 标签墙 / 动作条四类件并排放在一颗文件；`TagSearchPolicy.kt` 与 `SearchPagination.kt` 已在 `feature/`，而标签去重判据还在 `SearchScreen.kt` 内部。

## 四、重复

### 表 A — 同一判据散落多处

| 概念 | 点位 | 可复用符号 | 现状 |
|---|---|---|---|
| 图片墙列数四行样板 `imageWallColumnCount(windowWidth, wideScreenLayoutMode(windowWidth), VeneraSpacing.screenHorizontal*2)` | `gallery/ui/GalleryScreen.kt:284-289`、`GalleryDailyScreen.kt:102-107`、`GalleryFavoritesBody.kt:108-112`、`feature/FavoriteImagesScreen.kt:163-165` | `components/WideScreenPolicy.kt:113` | 函数体有 13 条用例；**第三参口径没人钉**——`WideScreenPolicyTest.kt:122,131,143,145,147,153,154` 传字面量 `24.dp`，生产传 `screenHorizontal*2`。实测 `screenHorizontal = 12.dp`，两值今天相等，所以缺陷不是「值不同」而是「谁改 token 都不会红」 |
| **顶栏地板 `statusBarTop + 104.dp`** | **18 处 / 14 文件**：`HomeScreen:179`、`HistoryScreen:124,139`、`LocalComicScreen:181,192,217`、`StatsScreen:115,129`、`DownloadScreen:84`、`FavoritesScreen:225`、`SearchScreen:264`、`settings/SettingsHome:147`、`settings/SettingsComponents:352`、`explore/UnifiedExploreScreen:316`、`explore/SourceSectionScreen:208`、`sourcemanage/ComicSourceScreen:164`、`gallery/ui/GalleryDailyScreen:130`、`gallery/ui/GalleryScreen:544` | **无任何命名常量**（`Spacing.kt` 只有 `topBarCollapsedHeight=52`:311、`dockedSearchBarHeight=56`:419） | 零覆盖。`DownloadScreen:84` 已写成 `104+92`、`SourceSectionScreen:208` 写成 `104+chipsHeight`，地板与附加高混在一条相加链里 |
| 底栏避让 | `feature/FollowUpdatesScreen.kt:153`、`feature/sourcemanage/ComicSourceScreen.kt:164` 直写 `bottom = 96.dp`（两文件对 `LocalBottomBarClearance` 零命中） | `Spacing.kt:539` 的 `LocalBottomBarClearance`；`bottomBarClearance=76`:214、`space9=20`:28 | 零覆盖。`Spacing.kt:194` 的契约文本**明文**写着「任何页面都禁止再额外加 Spacer(这笔留白) 或写 88dp/96dp 这类魔法数」——这两处是在违反一条已写下的不变量 |
| `isWideScreen(...)` 裸调 | 7 处 / 5 文件：`components/venera/VeneraSegmentedButton.kt:60`、`gallery/ui/GalleryReverseSearchArea.kt:140`、`GalleryReverseResults.kt:167`、`feature/FavoritesScreen.kt:220,619,645`、`gallery/ui/GalleryHomeChrome.kt:215` | `WideScreenPolicy.kt:134-138` 已标 `Deprecated(WARNING)` + `ReplaceWith` | 只钉了「它是 `!= Compact` 的别名」（`WideScreenPolicyTest.kt:106`）。函数体逐字等价 ⇒ 替换零收益，见 §六 |
| 分段器 48 / 56 两档 | `VeneraSegmentedButton.kt:60-62`、`gallery/ui/GalleryHomeChrome.kt:215-216` | `Spacing.kt:290 segmentedHeight=48`、`:295 segmentedHeightWide=56` | `segmentedHeightWide` 在 `app/src/test` 零命中。**值没重复，只重复了选择表达式** |

### 表 B — 成段复制

| 重复双方 | 内容 | 判定 |
|---|---|---|
| `gallery/ui/GallerySearchArea.kt:1566-1618` ↔ `gallery/ui/GalleryForYouScreen.kt:312-362` | 页尾读数行 + `loadingMore / exhausted / loadMoreError / else` 四档串 +「下一页没取到：$loadMoreError」+ 重试 `VeneraChip`，逐字重复 | 同侧、无隔离理由 → 该抽（A6） |
| `GalleryScreen.kt:850,1738-1760,1846`、`GalleryFavoritesBody.kt:163-175`、`GalleryForYouScreen.kt:273` | 「命中屏蔽规则」「按「成人内容处理」收起」这类屏蔽/分级计数读数，实测 **6 处，全在画廊侧** | 该抽（A6）；一处改文案不再出现「猜你喜欢已说到底、搜索页还在转」 |
| `feature/FavoritesScreen.kt:613-634` ↔ `:636-663` | 同文件相邻两颗 composable，只差 fraction（`0.22f`:564 / `0.25f`:666） | **不合并**：冻结屏，且两个 fraction 是两条独立的用户真机口径，合并即焊成一条 |
| `feature/FavoriteImagesScreen.kt:215-341` ↔ `gallery/ui/GalleryFavoritesBody.kt:151-254` | 跨侧整页骨架（加载环 / 空态 / 墙 + `snapshotFlow` 滚动上报 + `BackHandler` + 多选条） | **保留隔离**（`docs/rounds/gallery-module-isolation-plan-2026-09.md:10` 是用户硬口径；选择状态机已经共享 `components/selection/`）。但 `CircularWavyProgressIndicator(` 两侧各写十余次且都传同一组 color/trackColor，属两侧各自都该抽 |
| `GallerySearchViewModel.kt`(145-215,354-418,836-1035,1070) ↔ `GalleryForYouViewModel.kt`(125-165,236-374,521-526) | 两个 VM 各写一份 `isLoading/isLoadingMore/error/loadMoreError` + `loadJob/moreJob` 成对 cancel + 成功凭证 + `retryLoadMore` | **不是重复而是行为差**：一边叫 `loadedKey` 一边叫 `generation`，重试闸门一边四道一边三道。统一必须选一个判据当准 = 改并发时序与失败面 |
| `feature/SearchViewModel.kt:136-423` ↔ `explore/ExploreViewModel.kt:41,98-99` ↔ `NetworkFavoritesViewModel.kt:98-119,226` | 各自再写一份 `isLoading/error/page/refresh` | `SearchPagination.kt` 已存在却只有 `SearchViewModel:357,423` 两处消费（`ExploreViewModel` 零命中）。同上，判为时序差，本轮不动 |

### 表 C — 常量与字面量

| 字面量 | 点位 | 权威出处 | 是否被绕过 |
|---|---|---|---|
| prefs 存储名（11 个） | `data/platform/PreferenceKeys.kt:20` 只有 `venera_preferences`；内联在 `data/prefs/ComicMetricsCache.kt:14`、`data/prefs/ComicListPreferences.kt:12`、`security/guard/ContentGuardManager.kt:105`（:104 的 trace 串里再抄一次）、`sync/WebDavSyncManager.kt:23`、`data/network/UserAgentPolicy.kt:27`、`data/network/PersistentCookieJar.kt:20`、`source/copymanga/CopyMangaSource.kt:39`、`feature/HomeViewModel.kt:136`、`gallery/ui/GallerySearchViewModel.kt:1144`；私有 const 在 `gallery/data/GelbooruAccount.kt:55,173`、`SauceNaoAccount.kt:31,55`、`source/ComicSourceManager.kt:103,1384` | `data/platform/PreferenceKeys.kt` | **10/11 绕过**。改名 = 该模块全部存量偏好静默回落默认 |
| `"shared_images"` | `reader/VeneraReaderScreen.kt:2160`、`gallery/ui/GalleryPostScreen.kt:850`、`app/src/main/res/xml/file_paths.xml:17` | 应有单一常量 | 是。代码与 `FileProvider` 配置各写一份，改一处分享即崩 |
| UA | `data/network/ImageHeaderPolicy.kt:94,98,102,106` 四条逐字 Chrome/128；`"Venera/1.0 (Android)"` 在 :56,:90 + `gallery/data/GelbooruClient.kt:219 API_USER_AGENT`；另 Chrome/119 一颗在 **`engine/JsHttpHandler.kt:62`**（不同值、不同用途，不参与收口） | `data/network/UserAgentPolicy.kt:20` | 是。`ImageHeaderPolicy.kt:87` 的注释自陈「只为与接口侧 `API_USER_AGENT` 保持一致」——这条不变量只有注释级证据 |
| gelbooru 域名 | `gallery/data/GallerySite.kt:31`（host 字段，天然单源）、`GelbooruClient.kt:230 BASE`、`GelbooruAccount.kt:185,191`、`data/network/ImageHeaderPolicy.kt:88-89`（跨侧，漫画侧基建） | `GallerySite.GELBOORU` | 是（画廊侧 3 处手写 URL） |
| `"block_ai"` | `security/guard/ContentGuardManager.kt:153,159` 内联；`GalleryGuard.kt:23` 与 `VeneraPreferences.kt:292` 的注释里再各抄一次 | 同文件 companion | 是；无单测 |
| 超时 | `gallery/domain/GalleryFeedSource.kt:105`(12s)、`source/ComicSourceManager.kt:1406`(20s)、`:1416,1425`（**两枚同值 8s 不同名**）、`PreferredIpRules.kt:48`(5s)、`ComicLinkResolver.kt:64`(10s)、`ReadingStatsManager.kt:316`(3s) | 各文件 companion（已算单点） | 未绕过，只 8s 双名 |
| 裸 `14.dp` | >30 处（`StatsScreen` 8 处、`SyncBackupScreen` 6 处、`ComicSourceScreen` 十余处、`FollowUpdatesScreen:71,80,153`、`ComicTileLayout:87,114`、`rememberContentWidth(14.dp*2)` 在 `DownloadScreen:110` 与 `FollowUpdatesScreen:149`） | `Spacing.kt:58 rowHorizontal` / `:26 space7` | 是。**判定不合并**：两枚 token 同值但语义不同（一档间距 vs 行左右内边距），合并会让未来任一方改值被迫连带 |

### 表 D — 文档与代码同源数字（漂移）

| 值 | 文档 | 代码 / 实测 | 状态 |
|---|---|---|---|
| 「92 个测试文件 / 749 个用例」 | `README.md:199,214,292,380` + `README.en.md` 同四行 | `app/src/test` 实测 **94 颗 `.kt` / 773 个 `@Test`** | **已漂移**，中英两份逐字一致地一起落后 |
| 「平板插图卡 3 列 / `StaggeredGridCells.Fixed(2)`」 | `docs/收藏页双栏分屏方案_2026-09.md:224,227,266` | `WideScreenPolicy.kt:113`：1280dp→6 列、1706dp→8 列（ba3e207 已推翻 3 列） | **已漂移**，只有 §8.7 记了一句、正文没改 |
| `navBarWideScreenMaxWidth()`（写死落点「`VeneraFloatingNavBar.kt` 顶层」） | `docs/rounds/large-screen-adaptation-2026-09.md:71,117`、`reader-large-screen-adaptation-2026-09.md:59` | 全仓零命中，真身 `components/WideScreenPolicy.kt:152 wideScreenChromeMaxWidth()` | **已漂移**：符号不存在 |
| `bottomBarClearance` 在 `Spacing.kt:206` | `docs/rounds/large-screen-adaptation-stage2-plan-2026-10-02.md:233`（同句还引 `:184-206` 契约文本与 `:193` 禁令） | 常量在 `:214`，契约块 `:185-198`，禁令在 `:194` | **行号漂移**（值 76dp 仍对） |
| `ContentGuardManager.kt:50,246,269,285` | `docs/rounds/settings-audit-2026-09.md:87` | `nsfw_mode` 读写实际在 `:111` 与 `:148`；那四个行号今日是无关代码 | **行号漂移**，改成符号锚 |
| 「34 个源脚本 / 33 唯一源」 | `README.md:51,105` 与英文版 | `assets/sources/` 实测 34 颗 `.js` + `index.json`；`ComicSourceManager.kt:299` 引导 4 个 | 未漂移 |

### 复核式

```
Grep imageWallColumnCount\(          Grep statusBarTop \+ 104\.dp      Grep isWideScreen\(
Grep bottom = [0-9]+\.dp             Grep AndroidKeyValueStore\(       Grep shared_images
Grep Mozilla/5\.0                    Grep "block_ai"                   Grep segmentedHeightWide --path app/src/test
```

## 五、Part A（本轮执行）

判据：Android 侧有可指认的故障或回归的才进 Part A。六批**零冻结文件改动**（`FREEZE-STATEMENT.md:18` 禁「无明确需求的架构重构、顺手拆文件」，本轮不需要追加豁免记录）。

| 批 | 动作 | 缺陷 |
|---|---|---|
| A1 | 本文档 + 表 D 六处回填（README 双语同批） | 文档在陈述错事实 |
| A2 | `WideScreenPolicy` 新增 `bottomReadoutPadding(clearance)`；`FollowUpdatesScreen:153`、`ComicSourceScreen:164` 的 `bottom=96.dp` 改调它 | 17bd774 ④ 迁了 15 颗漏这 2 颗；侧栏档底栏已不渲染却仍留 96dp 空带 = 用户可见回归；且直接违反 `Spacing.kt:194` 写下的禁令。Compact 档 76+20=96 逐字等值 |
| A3 | 五簇字面量收口：UA（`APP_USER_AGENT` / `DESKTOP_BROWSER_UA`）、`data/platform/SharedImageCache.kt`（`shared_images`）、10 处存储名 → `PreferenceKeys`（值逐字不变）、gelbooru 域名由 `GallerySite` 派生（跨侧那份不改引）、`ComicSourceManager` 两枚 8s 合一 | 三条真故障路径：`file_paths.xml` 与代码各写一份 = 分享崩；存储名绕过 = 丢偏好；UA 同值只有注释级证据 = 排障成本 |
| A4 | `VeneraSpacing.topBarFloor = 104.dp`，收 11 处非冻结点，7 处冻结点写进守卫用例白名单 | 18 处共用一条几何契约却零命名常量；大屏批次 2 正在改 chrome 几何，地板与附加高已混写 |
| A5 | 新 `components/ImageWallColumns.kt` 的 `rememberImageWallColumnCount()` + `imageWallHorizontalPadding()`；测试的 `24.dp` 改由 token 推导 | ba3e207 修了「判据写死」没修「判据入参写死」；测试写死字面量 ⇒ 列数回归抓不到 |
| A6 | `gallery/ui/` 内页尾读数件合一（6 处，仅本侧） | 屏蔽/分级计数读数 6 份，改一处即读数不一致 |

新增用例：`components/BottomReadoutPaddingTest.kt`、`ui/tokens/TopBarFloorGuardTest.kt`（源码扫描 + 白名单集合相等，读不到源文件即 fail）、`data/platform/SharedImageCacheDirContractTest.kt`（含读 `file_paths.xml`）、`data/platform/PreferenceStorageNamesTest.kt`（11 枚存储名化石）、`data/network/UserAgentPolicyTest.kt`、`gallery/data/GelbooruUrlsTest.kt`、`components/WideScreenPolicyTest.kt`（追加 token 推导与 320..2000 重扫）、`gallery/ui/GalleryFeedReadoutTextsTest.kt`。

落地读数（每批完成后回写，未回写视为未落地）：

- A1 待回写
- A2 待回写
- A3 待回写
- A4 待回写
- A5 待回写
- A6 待回写

## 六、Part B — Windows 移植阶段 2 再做

共同前置：**与 Windows 线协同、不并行编辑** `desktop/build.gradle.kts` 与 `docs/rounds/windows-r1f-stage1-plan-2026-10-02.md`（后者本轮开工时是脏的）。执行顺序 W1→W2→W3→W4→W6→W5（W5 碰用户偏好数据，风险最高放最后）。

| 批 | 动作 | 解锁条件 | 现在不做的理由 |
|---|---|---|---|
| W1 | `feature/ComicItem.kt` 类体 → `source/model/ComicItem.kt`，原位留公开 `typealias`；`ContentGuardManager:9`、`sync/`、`FavoriteImagesManager` 的 import 改指新路径；删 9 条无用通配 import | 阶段 2 把 `security/` 放进 `:desktop` srcDir 之前必须先断 `security→feature` | Android 无症状；typealias 的收益要到桌面消费方出现才存在 |
| W2 | 三枚源 DTO（`feature/sourcemanage/ComicSourceModels.kt:9` 的 `SourceSettingItem`/`SelectOption`/`SourceAccountInfo`）→ `source/model/`，原位留 `typealias`（同文件 `:7 typealias RepoSourceItem` 已是先例） | 阶段 2 把 `source/` 放进共享源目录的**硬前提** | 同 W1，且这条要动 `JsComicSource`/`CopyMangaSource` 的签名引用，与并行线的源层工作撞车面大 |
| W3 | 新 `LayeringEdgeTest.kt`（扫 `data/**`、`source/**`、`security/**`、`sync/**` 的 import，断言不出现 `feature.`/`gallery.`/`ui.tokens.`；基线白名单只允许 `sync/BackupManager.kt` 一族），随 W1/W2 起建、逐批删白名单 | 与 W1/W2 同批 | 守卫用例必须和被守卫的动作同批落地，否则现在建它等于埋一条常红用例 |
| W4 | `data/network/CloudflareBypassActivity.kt` → `feature/network/`，同步 `AndroidManifest.xml:207` 与 `CloudflareBypassManager.kt:80-82` | 阶段 2 把过盾纯逻辑（`CloudflareBypassManager`/`PreferredIp*`）放进共享面时 | 今天 Android 无症状；不做的话届时必须新增一条按文件名的 exclude，即主动扩大 `desktop/build.gradle.kts:39-41` 那条已记账的「静默假绿」面 |
| W5 | 新 `gallery/data/GalleryPreferences.kt`：搬 `VeneraPreferences` 的 15 组 gallery 字段与 setter（:173-220、:229-290、:328-348）+ 15 枚 `KEY_GALLERY_*`（:668-682），**存储名仍取 `PreferenceKeys.PREFS_NAME`、键名字面量逐字不变**；消费点 `gallery/ui`×7 + `feature/settings/GallerySettings.kt` + `feature/SettingsHost.kt:136,176`。同批 `ui/tokens/Color.kt:472 ThemeSeedPresets` → `data/prefs` | 阶段 2 把 `gallery/data` 放进共享面之前必须断的环 | 本轮风险最高的一批（碰用户偏好数据），而 Android 侧唯一可指的症状只是一句注释矛盾。化石用例（15 枚 `pref_gallery_*` + 存储名 + 「VeneraPreferences 已无 gallery import」）随这批一起落 |
| W6 | `data/db/FollowUpdatesRepository.kt:4,44` 的 `ComicSourceManager` 改构造参数注入（调用点 `feature/FollowUpdatesViewModel.kt:19`、`data/db/FollowUpdatesWorker.kt:31`），`desktop/build.gradle.kts:45-56` 注释按「理由从两条变一条」如实回写，**exclude 三行一行不撤** | 阶段 2 的「后台追更」决策——并行线在 stage1 文档里已把这两颗排进该阶段（R31 口径：属 `androidx.work`，桌面调用方为 0） | 断完这条边该文件仍被排除（还吃 `Context` + WorkManager），今天拿不到可验证收益 |

## 七、两部分都不做

- **13 颗巨型文件的完整拆分**（含 `Navigation.kt` 的业务下沉）。冻结声明明文禁「顺手拆文件」；且没有一颗存在能保证行为不变的切分线——`VeneraReaderScreen.kt:445` 在 `LaunchedEffect` 里直调 DAO、`ComicDetailScreen.kt:1641-1771` 在组合期逐章查盘，动它们必然改调度时序。
- **`SearchScreen.kt:1834-1865` 判据搬 `TagSearchPolicy.kt`**、**`data/tags/TagDisplay.kt` 的 `@Composable` 搬 `components/`**、`GalleryDailyPage`/`GallerySheetSection`/`normalizeComicDisplayMode` 归位。前两条要动冻结屏（或落在桌面不共享的目录），桌面侧 `desktop/build.gradle.kts:69-70` 已判定不读 `opencc.txt` ⇒ 零收益换冻结豁免。
- **VM 取数状态机统一**（5 份）。差异是行为差不是重复。
- **`ImagePipelinePolicy`/`ChineseVariantConverter`/`VeneraDatabase`/`BackupTransfers` 脱 `android.*`**、**`JsComicSource`/`ComicSourceManager`/`CopyMangaSource` 脱 `android.util.{Log,LruCache,Base64}`**。无桌面调用方；且 `android.util.Base64` 与 `java.util.Base64` 在换行/填充上不同值、`LruCache.removeEldestEntry` 语义注释保证不了等价——必须与「真加一条 srcDir」同批落地才能被消费方立即验证。`GallerySaver`/`VeneraReaderScreen` 的 `MediaStore` 分支已被并行线记成 R31 豁免，照其裁决不重开。
- **画廊隔离彻底解耦**（`sync/BackupManager:19-20`、`FavoritesScreen:12` 内嵌 `GalleryFavoritesBody`、`Navigation.kt:74-78,829-832` 复用 `GalleryViewModel`、`GallerySaver` 的 `ComicStorageRoot` 落点、`gallery/ui` 6 处抓 `ContentGuardManager`）。内嵌画廊墙与共用守卫是用户点名的产品裁决；换存储根会改用户文件落盘位置。
- **`isWideScreen` 去 deprecated 扫描**（函数体逐字等价）、**分段器 48/56 选择表达式合并**、**裸 `14.dp` 收口**。
- **`ContentGuardManager` 的 `"block_ai"` 与 `"venera_guard_prefs"` 双写**（冻结文件，且尚未发生「改一处漏另一处」的现实故障；A3 的存储名化石用例已能抓到漂移）。
- **`FavoritesScreen:613-663` 只差 fraction 的两颗 composable 合并**、**`FavoriteImagesScreen`↔`GalleryFavoritesBody` 整页骨架合并**。

## 八、本次审计中被推翻的读数（记账）

1. 「图片墙列数测试传 `24.dp` 与生产 `screenHorizontal*2` **不同值**」——错。实测 `Spacing.kt:33 screenHorizontal = 12.dp`，两值相等。真缺陷是「测试写死字面量、生产走 token，改 token 不会红」。
2. 「Chrome/119 在 `ImageHeaderPolicy.kt:62`」——错，在 `engine/JsHttpHandler.kt:62`。收口只统一同值的 Chrome/128 四份与 app UA 两份，那颗不动。
3. 「`GelbooruAccount.kt:185,191` 无域名字面量」——错，那两行就是手写 URL。域名实为 4 处（`GallerySite:31` 为天然单源、`GelbooruClient:230`、`GelbooruAccount:185,191`、`ImageHeaderPolicy:88-89`）。
4. 「屏蔽计数文案 4 处」——少报，实测 6 处（多出 `GalleryScreen:850,1846` 与 `GalleryForYouScreen:273`），且全在画廊侧，收口不需跨侧。
5. 「`GalleryDailyPage` 与 `GalleryDailyScreen.kt` 是两个并存的第二屏」——不准确。它是 `GalleryDailyScreen.kt:143` 唯一消费的页体，属「放错文件」而不是「功能重复」。
6. 「`FavoritePanelSheet` 起于 1899 行」——实际声明在 `ComicDetailScreen.kt:2000`。
7. 「顶栏地板 17 处」——少计，逐行清点为 **18 处 / 14 文件**（评论行不算）。
8. `docs/rounds/settings-audit-2026-09.md:87` 写 `nsfw_mode` 被「explore/detail 卡片」消费——**详情页不消费它**。实测消费面是 `explore/{SourceSectionScreen,UnifiedExploreScreen}`、`FavoritesScreen`、`SearchScreen`、`settings/BlockingSettings` 与 4 颗 `gallery/ui` 文件。修锚点时差点把这条陈旧断言一起抄过去，已按实测改写。
9. **本次审计自己造出来的两处错**（记下来因为它们是同一类失败）：① 回填收藏页方案文档时写了「历史见 §8.7」，而 `§8.7` 是 `docs/rounds/large-screen-adaptation-stage2-plan-2026-10-02.md` 里的节号、不是本文档的节——**正是本次审计要抓的「悬空引用」这一类**，已改成带文件路径的锚。② 表 A 原写「测试传 24.dp 共 5 处字面量」，实际是 2 处直接字面量 + 2 处 `val padding = 24.dp` 局部常量（`WideScreenPolicyTest.kt:141,152`），5 个调用点全部与生产不同源，结论不变但形状要说准。
