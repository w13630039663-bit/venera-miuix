# 大屏（平板 / 横向宽窗）三处单独优化 —— 方案

用户原话（2026-09-22，附 Pixel Tablet API 36.1 横向截图）：

> 优化下大屏状态下的悬浮导航栏 和双列漫画展示 以及漫画详情页的预览都针对大屏单独优化下

截图上的三条病灶：搜索页两张巨幅封面拉满 1280dp 宽；底部胶囊固定在 480dp 居中、与内容宽度脱节；预览区（本轮未截图但同源问题）三列在大屏上每格 ~240dp。

## 一、口径来源：master 分支自己就有大屏策略，不新造数

本仓 master（用户自己的 Flutter 版）已经写死了宽屏行为，逐字摘录：

| 位置 | master 的做法 | 现值 |
| --- | --- | --- |
| `lib/components/layout.dart` `getBriefModeLayout`→`getMiuixTwoColumnLayout` | 卡片网格列数 `crossItems = max(2, width ~/ 220)`，间距 12 | **每 220dp 一列，最少 2 列**（注释原文："手机双列；宽屏（平板/桌面窗口）按 220dp 一列自适应加列"） |
| 同文件 `getDetailedModeLayout` | 行卡列数 `crossItems = max(1, width ~/ 360)` | **每 360dp 一列，最少 1 列** |
| `lib/pages/comic_details_page/thumbnails.dart:296` | `SliverGridDelegateWithMaxCrossAxisExtent(maxCrossAxisExtent: 200, childAspectRatio: 0.68)`，格内 padding 窄屏 4 / 宽屏 8 | **预览格宽上限 200dp**，随宽度加列 |
| `lib/components/navigation_bar.dart:148` | `barW = min(_kGlassBarMaxWidth, width - _kGlassBarHorizontalPadding*2)`，`_kGlassBarMaxWidth = 540`、`_kGlassBarHorizontalPadding = 24` | 悬浮胶囊**上限 540dp**、两侧各让 24dp |
| 同文件 `targetFormContext` + `buildLeft` | 窗口宽 `> changePoint(600)` → 侧栏收起态 `_kFoldedSideBarWidth = 72`；`> changePoint2(1300)` → 侧栏展开态 `_kSideBarWidth = 224` | master 在宽窗**换成左侧 NavigationRail**，不是底部胶囊 |

（`changePoint` / `changePoint2` 见 `lib/foundation/consts.dart`。）

对照 compose 现状：

- `components/VeneraFloatingNavBar.kt:70` — `if (screenWidth > 600.dp) 480.dp else screenWidth - 28.dp`。窄屏口径 = 两侧各 14dp，宽屏口径 = 定宽 480dp。两个数都不等于 master 的 24 / 540。
- `components/ComicPresentationPolicy.kt:6` — `comicListColumnCount = if (detailed) 1 else 2`，**写死**，被 6 处网格消费（History / Favorites / FollowUpdates / NetworkFavorites / Search / Explore 两个页 + `LocalComicScreen` `DownloadScreen` 各留了一份 `if (detailed) 1 else 2` 的副本）。
- `feature/ComicDetailScreen.kt:166` — `previewThumbnails.chunked(3)`，`rowIdx * 3 + colIdx` 硬编码，列数与宽度无关。

顺带一处既有失真：`ui/tokens/Spacing.kt` 的 `comicCardMinWidth = 130.dp` 注释写着"供 GridCells.Adaptive 使用：列数由实际可用宽度推导"，但全仓没有 `GridCells.Adaptive`，这个 token 只被两个预览页当宽度用。130dp 若真拿来做列宽，1280dp 屏会算出 9 列，比 master 的 5 列多近一倍 —— 所以本轮**以 master 的 220/360 为准**，130dp 那条注释要改掉，不留第二个口径。

## 二、要拍板的两件事

### 决策 1：大屏底栏走哪条路

- **A 胶囊按 master 口径收口**（改 1 个数 + 1 个边距）：宽屏仍用底部悬浮胶囊，但宽度改成 `min(540dp, width - 24dp*2)`，与 master 的 MD3 / 液态玻璃两条路径完全同一几何。代价：1280dp 宽窗上胶囊仍明显窄于内容，只是不再是"我们自创的 480"，而是"master 本来的 540"。改动面：`VeneraFloatingNavBar.kt` 一处（液态玻璃那条已经是满宽，天然不受影响）。
- **B 宽窗换左侧 NavigationRail**（master 的真实大屏形态）：>600dp 用 72dp 收起侧栏，>1300dp 用 224dp 展开侧栏。代价很实在：内容留白契约要从"底部 `bottomBarClearance`"变成"左侧让宽"，`bottomBarClearance` 全仓 23 处、13 个文件（含 12 个页面的 `contentPadding` 与 `104.dp` 顶部留白口径）要重判；`Navigation.kt` 是保护域；共享元素飞行的两端度量、玻璃底栏的录制层都要跟着改。
- **C 胶囊随内容等比放大**（例如宽窗取内容宽的 1/2）：观感更"贴合大屏"，但 540 之外的任何系数都是新造数，master 和现成 token 都没有依据。

### 决策 2：`detailed`（单列行卡）模式要不要也跟着加列

master 在宽窗会把行卡排成 `width ~/ 360` 列（1280dp → 3 列并排行卡）。若照搬，改动同样只落在 `comicListColumnCount` 一个函数里；若不照搬，宽窗的 detailed 模式就是一张 1280dp 宽的横卡（现状）。

## 三、无争议的部分（不需要拍板，等批准即做）

1. `comicListColumnCount` 改为按可用宽度推导，brief = `max(2, width ~/ 220)`、detailed 依决策 2；`LocalComicScreen` / `DownloadScreen` 那两份 `if (detailed) 1 else 2` 副本删掉，统一走同一函数。
2. 列数要拿到"实际可用宽度"才能算，而当前这些页面只有 `LocalConfiguration.screenWidthDp`（不含窗口内边距、分屏时失真）。用 `BoxWithConstraints` / `onSizeChanged` 量网格自身的可用宽，避免拿屏幕宽当内容宽。
3. 详情页预览列数 = `(可用宽 / 200dp)` 向上取整、下限 3（手机上今天就是 3 列，必须一格不变），`chunked(previewColumns)` 与 `pageIndex = rowIdx * previewColumns + colIdx` 一起改；格内宽高比沿用现值 0.7（master 是 0.68，属既有口径差，本轮不动）。
4. 折叠态一次挂载的预览张数（`PREVIEW_LIMIT`）按新列数配平：列数变多后同一窗口铺不满一行会露出空位，需要给出对齐到整行的取值，避免出现"最后一行只有两张 + 一堆空位"。

## 四、验证口径

- 构建三项 QA（`compileDebugKotlin` / `testDebugUnitTest` / `assembleDebug`）只算编译门槛，不算已验证。
- 观感需要跑起来看：目前桌面上是一个 Pixel Tablet (API 36.1) 模拟器。既有规则是"真机只做读取不操作手机"，模拟器是否可代我操作、还是同样只由用户点，需要先确认。
- 手机侧回归优先级：`brief` 双列在 360/412dp 必须**逐像素不变**（列数下限 2 保证），详情页预览手机三列不变。

## 五、已拍板（2026-09-22）

1. **底栏走 A**：宽屏定宽从自创的 480dp 换成 master 的 `min(540dp, 宽 - 24×2)`；手机档**不动**（仍 `宽 - 28dp`）。master 的宽窗真实形态是左侧 NavigationRail（72 / 224dp），B 方案要打穿 `bottomBarClearance` 的 23 处契约与保护域 `Navigation.kt`，本轮不做。
2. **`detailed` 照搬 master 加列**：每 360dp 一列。为保持一致，chunked 型页面的详细模式也改成行级并排（搜索/网络收藏/探索/分类二级），不再是整宽单列。
3. **验证设备**：Pixel Tablet 模拟器**由用户操作**，我只截图与读日志；手机规则不变（仍只读、由用户点）。

## 六、实际落地

| 改动 | 位置 |
| --- | --- |
| 列数口径函数 + 宽度测量 helper | `components/ComicPresentationPolicy.kt`：`comicListColumnCount(mode, availableWidth)`、`comicPreviewColumnCount(width)`、`rememberContentWidth(hPadding)` |
| LazyVerticalGrid 三处接实测宽 | `HistoryScreen` / `FavoritesScreen` / `FollowUpdatesScreen` / `DownloadScreen`（后两者原先各留一份 `if (detailed) 1 else 2` 副本，已删） |
| chunked 行级网格四处 | `SearchScreen`（`SingleSourceResults` 改为收 `columns` 参数）、`NetworkFavoritesScreen`、`LocalComicScreen`、`explore/UnifiedExploreScreen` + `explore/SourceSectionScreen` |
| 详细模式并排化 | 上述 chunked 页各加一个 `*DetailedRow`（`ComicDetailedRow` / `ExploreDetailedRow`），末行补位统一成 `repeat(columns - row.size)`，不再是只补一格的 `if (row.size == 1)` |
| 详情页预览 | `ComicDetailScreen`：`chunked(3)` → `chunked(previewColumns)`，`pageIndex = rowIdx * previewColumns + colIdx`，测量挂在预览 Column 上 |
| 底栏 | 两种样式共用一个宽屏口径 `wideScreenChromeMaxWidth(screenWidth)`（**现行落点 `components/WideScreenPolicy.kt:152`；本轮落地时名为 `navBarWideScreenMaxWidth`，写在 `VeneraFloatingNavBar.kt` 顶层，后收口进 policy**）：>600dp 取 `min(540, 宽-48)`，手机档返回 `null` 表示各样式沿用现有几何。胶囊直接用它算 `barWidth`；液态玻璃在组件内部 `modifier.then(requiredWidth(cap))`，因此**没有动保护域 `Navigation.kt`** |
| 口径收口 | `ui/tokens/Spacing.kt` 的 `comicCardMinWidth` 注释原本自称"供 GridCells.Adaptive 推导列数"，实际无人这么用 —— 改为说明它只是参考宽度，列数唯一口径在 policy |
| 单测 | `app/src/test/.../components/ComicColumnPolicyTest.kt`：锁手机档（360/411/412dp → 2 列、预览 3 列）与宽屏档（1280dp → brief 5 列 / detailed 3 列 / 预览 6 列），另锁 0 宽兜底与 chunked 往返 |

未做（刻意）：`FavoriteImagesScreen` 的图片双列、`exploreColumnCount`（源入口小格，120dp 一档，与封面无关）。预览折叠量 `PREVIEW_LIMIT = 10` 未动：末行不足已由等宽 Spacer 补位，不需要按行配平（第三节第 4 条作废）。

> 更正（同日稍后）：本行原本还把「首页 Hero/历史横滚卡的宽度」列为刻意不做。用户随后单独提了这条，已改做 —— 见下节。

### 补记：首页「可能你感兴趣」宽屏形态（用户追加，两版）

病状：轮播整行 `fillMaxWidth()`，1280dp 上 hero 被算成约 **1040×220**，竖版封面横向裁成一坨放大碎片。
master 在首页只有一条宽屏规则（`home_page.dart:65`：`width > changePoint ? paddingHorizontal(8) : 原样`），
且 master 没有 Hero 轮播这个部件 —— **没有可照的宽屏形态**。

**第一版（540dp 收口）已被用户否掉**：540 是悬浮 chrome 的口径，套到内容块上两侧各留 394dp 空，
"空出来感觉太多了"。教训：**chrome 的收口口径不能直接当内容块的收口口径**。

**第二版（已落地）**：宽屏档不用轮播，改按漫画网格同一口径排**单行**封面卡 ——
`comicListColumnCount("brief", blockWidth)`，`columns >= 3` 才走网格，卡片本体仍复用
`RecommendCarouselItem`（共享元素飞行与封面遮罩判定因此与轮播完全一致），
封面比例取应用内现成的 `historyCardWidth : historyCoverHeight`（124 : 170），不新造数。

| 项 | 结果 |
| --- | --- |
| 1280dp 列数 | blockWidth ≈ 1252 → 1252/220 = 5.7 → **5 列**，截图实测 5 张铺满一行 |
| 手机档 | 411dp → blockWidth ≈ 383 → `coerceAtLeast(2)` = **2 列 < 3** → 仍走原轮播，**零改动** |
| 600dp 边界 | 572/220 = 2 → 仍是轮播，没有悬崖 |

刻意只做**一行**：首页首屏厚度是定过的（历史记录区正是从两行改成单行才不压首屏），
所以宽屏取 `columns` 张铺满一行就收，"换一批"重随机换这 5 张。

实现坑一条：`maskClip` 是 material3 轮播 `CarouselItemScope` 的**成员扩展**，
出了轮播作用域就不存在（编译报 Unresolved reference），网格卡改用普通 `clip`。

## 七、模拟器验证记录

设备：Pixel Tablet AVD（2560×1600 @320dpi = 1280×800dp，横屏）。持久化偏好为
`pref_navigation_bar_style = LIQUID_GLASS`、`pref_appearance_style = MD3`、`display_mode = brief`。

### 7.1 底栏（已验证）

第四节决策 1 的 A 方案原文写着"液态玻璃那条已经是满宽，天然不受影响"——**这句是错的**，
QA 时才暴露：`Navigation.kt:642-651` 给玻璃底栏的是 `.fillMaxWidth()`，而该文件 666-670 行
自己承诺"两条路径的 bottomBarClearance 契约天然一致……几何零差异"。只给胶囊加 540 上限，
就是把这条已写死的不变量打破：同一台设备上换样式，底栏宽度会跳变。

处置：抽出唯一口径 `wideScreenChromeMaxWidth()`（当时名 `navBarWideScreenMaxWidth()`），玻璃侧在**组件内部**收口（`requiredWidth`，
因为宿主链以 `fillMaxWidth()` 结尾、`widthIn` 会被钉死的 min 约束顶回去），
不触碰保护域 `Navigation.kt`。

实测（截图逐像素扫描，胶囊最宽一行）：

| 项 | 期望 | 实测 |
| --- | --- | --- |
| 底栏宽 | `min(540, 1280-48) = 540dp` | **1080px = 540.0dp** |
| 左右留白 | 对称 | **370dp / 370dp**（居中） |

手机档不受影响：`screenWidth <= 600dp` 时口径返回 `null`，两种样式各走原有 `宽-28` / `宽-16`。
