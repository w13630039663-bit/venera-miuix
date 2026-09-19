# 搜索页加载态 / 翻页落点 / 标签下钻带源

> 日期：2026-09-19 ｜ 分支：compose-migration ｜ 页面：`feature/SearchScreen.kt`、`feature/Navigation.kt`
> 状态：**代码完成，Build QA 三项全绿，真机 QA 待做**
> ⚠️ `SearchScreen.kt` 是 **FROZEN**（第一批）。本轮属用户点名豁免，已登记进 `FREEZE-STATEMENT.md`「冻结豁免记录（2026-09-19）第二批」。
> **两条需求没做**（§3），因为数据不存在或会丢功能，不是漏掉。

## 1. 已实施

| # | 需求 | 落点 | 改动 |
|---|---|---|---|
| 1 | 加载时那组「莫名其妙的双列卡片」去掉，换波浪环 | `SearchScreen.kt` | `ResultSkeleton` 原本画的是 **两张 shimmer 空卡**（封面比例 + 一条文字 shimmer）—— 看着像真搜到了两张空漫画。整体替换为 `SearchLoadingIndicator`：整页用 48dp 居中波浪环，行内（单源分区、筛选加载）用 28dp。5 个调用点全部覆盖 |
| 2 | 到底显示「加载更多」，加载时用波浪环 | `SingleSourceResults` | 新增可见的 `加载更多` 按钮（`canLoadMore && !loadingMore`）；翻页中显示 28dp 波浪环 + 「正在加载更多…」。**原有触底自动加载保留**（`:160` 的 `lastVisible >= total-2`）—— 去掉它是丢便利，不是简化 |
| 3 | 加载失败要显示原因 | `SingleSourceResults` | **这是既存缺陷修复**：`ui.error` 原先只在 `results.isEmpty()` 时渲染（`ss-error` 分支），所以「已经有一屏结果、再翻页失败」时 VM 写进 `error` 的 `"加载更多失败：…"` 被整个吞掉，界面什么都不说。现在在网格之后单独渲染原因 + 重试（重试走 `loadMore`，不是重新搜索） |
| 4 | 详情页点 tag 跳搜索要自动切到对应漫画源 | `Navigation.kt` + `SearchScreen.kt` | `TagSearchRoute` 加可选字段 `sourceName`；详情页 `onSearchTag` 传 `comic.sourceName`；搜索页 `LaunchedEffect(initialQuery, initialSourceName)` **先 `onSourceSelected` 再 `search`** |
| 5 | 表头要区分「已加载」与「总数」 | `SearchViewModel` + `SearchScreen.ResultHeader` | 「检索结果（已加载 N 个 · 约 M 个）」，源不声明页数时「… · 总数未知」。详见 §3.1（跨 Source / Model / ViewModel 三个保护域） |
| 6 | 拉到底要给「已经没有了」 | `SingleSourceResults` | `!canLoadMore && !loadingMore && hasSearched && error == null` 时在末尾显示居中「已经没有了」。源声明了 maxPage 时这个判定是**精确**的（VM 直接按 `nextPage < maxPage` 算，不再多发一次空请求）；有翻页错误时不显示，避免把「失败了」说成「没有了」 |

### 4 的实现口径（为什么传名字而不是 key）

`DetailRoute(comicId, sourceName)` **本身不携带 sourceKey**，`resolveDetailComic` 构造的 `ComicItem.sourceName` 来自路由。所以链路上唯一可靠的身份就是显示名。搜索页拿显示名去 `sources`（`viewModel.sourcesFlow`）里精确匹配 `it.name == initialSourceName`：

- 匹配到 → `onSourceSelected(key, name)`，再 `search(keyword)`；
- 匹配不到 → **保持默认目标（全网聚合），不猜 key**（同名异源是真实存在的，猜错会把搜索打到别的站）。

顺序不能反：`search()` 读的是 `selectedSourceKey` 的快照，先搜就落在全网聚合上了。

## 2. 顺带清掉的

`SearchScreen.kt` 里 `VeneraShimmer` import 随骨架一起失效，已删。`TagSearchPolicy` 一行未动。

## 3. 「总数」与「页长」两件事的结论

### 3.1 「全部 xx 个」—— 已实施为「约 N 个」

原状：`ResultHeader` 显示「检索结果（N 条）」，N 就是已加载数，看着像总数。

实测 33 个源的 `search` 返回形态：

| 能拿到什么 | 源数 | 说明 |
|---|---|---|
| 直接返回条目总数 `total` | **0** | 没有任何一个源往外返回总数 |
| 返回 `maxPage` | **26** | 页数精确；条数只能估 |
| 两者都不返回 | 7 | 含 **ehentai / nhentai / lanraragi / ccc** 等 |
| 游标型 `loadNext` | 若干（与上有重叠） | 协议上**没有**「总页数」概念，只有「还有下一页吗」 |

> jm 内部**有** `data.total`（`jm.js:656`），但它的 `search.load` 只往外返回 `maxPage = ceil(total/80)`。

**落地口径（用户选定）**：表头显示 **「检索结果（已加载 N 个 · 约 M 个）」**，源不声明页数时显示 **「已加载 N 个 · 总数未知」**。`M = maxPage × 首页条数`，**只会高估不会低估**（误差上限一个页长），所以「约」字不能省 —— 编一个看起来精确的数比不显示更糟。

**接线（跨三个保护域，用户已授权）**：

| 文件 | 改动 |
|---|---|
| `source/model/ComicSourceModels.kt` | 新增 `SearchPage(comics, maxPage: Int?)`；**刻意没有 total 字段** |
| `source/ComicSource.kt:37` | `search(): Result<List<Comic>>` → `Result<SearchPage>` |
| `source/js/JsComicSource.kt` | 解析 `res["maxPage"]`（此前被丢掉）并随 `SearchPage` 返回 |
| `source/baozi` / `copymanga` / `mangadex` | 三个原生源迁移。copymanga 读 `results.total`、mangadex 读顶层 `total`，都用 `optInt(…, 0)` 兜底 —— **字段缺失就留 null，不猜**；baozi 是 HTML 抓取，无总数概念，直接 `SearchPage(list)` |
| `source/ComicSourceManager.kt` | `search()` 返回 `SearchPage`；`all` 分支**故意不合并 maxPage**（各源页数加总既不是总页数也不是总条数）；`searchAggregatedStream` 相应解包 |
| `feature/SearchViewModel.kt` | `SearchUiState` 加 `sourceMaxPage` / `estimatedTotal`；`canLoadMore` 改为 **`nextPage < maxPage`**（源声明页数时），不再靠"多发一次空请求"才发现到底了；游标型源退回 `comics.isNotEmpty()` |
| `MainActivity.kt` ×2 | 自检代码解包 `.comics` |
| `feature/explore/SourceSectionScreen.kt` | **冻结页，只做保住原行为的最小适配**：`.map { page -> page.comics to null }`。接口现在能给真 maxPage，但启用它会改变该页的分页判定，属另一件事 |

### 3.2 「一次性最多加载 20 个」—— 不需要额外截断

澄清后确认：网格本来就**一次只放一页**，而多数源的页长就是 20 条（copymanga / mangadex 的 `limit=20`）。所以"卡片只有 20 个"已经是现状，不需要客户端再截断。jm 是 80 条/页 —— 那是源自己定的页长，截到 20 属于藏掉源已返回的结果，没做。

## 4. QA

**Build QA（2026-09-19）**：`:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleDebug` 三项 **BUILD SUCCESSFUL**。

**真机 QA**：
1. 全网聚合搜索：每个源分区加载时是 28dp 波浪环，不再出现两张空卡；某源失败仍单独显示原因 + 重试。
2. 单源搜索：首屏是 48dp 居中波浪环；有结果后到底出现「加载更多」按钮；点它或自动触发时换成环 + 「正在加载更多…」；**翻页失败时底部要显示失败原因**（本轮修的缺陷）。
3. 翻页后重试按钮走的是 `loadMore` 而不是重新搜索 —— 已加载的结果不能被清掉。
4. 详情页（哔咔 / 禁漫 / EH 各一本）点标签 → 搜索页顶部源药丸应选中**那本漫画的源**、关键词已填、结果只来自该源；返回后详情页状态正常。
5. 源显示名对不上时（自定义源、改名源）应退回默认目标而不是乱切。
6. 筛选选项面板（`SearchOptionsSheet`）的加载态也换成了小环，确认在弹层里不突兀。
7. LIGHT / DARK、360dp 窄屏、字体放大一档；列表与网格两种显示模式各测。
8. 表头「约 M 个」：挑一个声明 maxPage 的源（jm / picacg / copy_manga）确认有「约 N 个」；挑 **ehentai 或 nhentai** 确认显示「总数未知」而不是 0，也不是硬编一个数。
9. jm 的估算误差：jm 页长 80，`maxPage×80` 会比真实 total 最多多 79 —— 确认「约」字在，且不会被读成精确值。
10. 「已经没有了」：单源翻到最后一页后应出现，且**不会**再多发一次空请求（看 logcat 的网络计数）；游标型源（ehentai）则要等某页返回空后才出现；翻页失败时应显示失败原因而**不是**「已经没有了」。
11. 探索页 `SourceSectionScreen`（冻结页）：按统一标签下钻那条路径行为应与改造前完全一致（它仍把 maxPage 当 null 处理）。

## 5. 改动文件

| 文件 | 性质 |
|---|---|
| `feature/SearchScreen.kt` | 冻结页豁免：加载态换环 + 可见翻页落点 + 翻页失败原因（既存缺陷）+ 表头「已加载/约」+「已经没有了」+ `initialSourceName` |
| `feature/Navigation.kt` | `TagSearchRoute` 加可选 `sourceName` 字段并在详情页携带（未动 Tab 枚举 / 路由映射） |
| `source/model/ComicSourceModels.kt` | 新增 `SearchPage(comics, maxPage: Int?)` |
| `source/ComicSource.kt` | `search()` 返回类型换成 `SearchPage` |
| `source/js/JsComicSource.kt` | 解析并返回 `maxPage` |
| `source/baozi` / `copymanga` / `mangadex` | 三个原生源迁移（后两个从 API `total` 算 maxPage，缺失则 null） |
| `source/ComicSourceManager.kt` | `search()` / `searchAggregatedStream` 解包与传递 |
| `feature/SearchViewModel.kt` | `sourceMaxPage` / `estimatedTotal`；`canLoadMore` 改按页数判 |
| `MainActivity.kt` | 自检代码解包 `.comics`（2 处） |
| `feature/explore/SourceSectionScreen.kt` | 冻结页，**仅保住原行为的最小适配** |
| `ui/tokens/Spacing.kt` | 复用上一轮的 `loaderPage` / `loaderInline` |
| `FREEZE-STATEMENT.md` | 豁免登记 |

## 6. 过程事故记录（避免下次重演）

本轮开工前，`ComicSource.kt` / `JsComicSource.kt` / `ComicSourceModels.kt` 已被**另一个会话**改到一半：接口换成了 `Result<SearchPage>`，但 `JsComicSource` 漏 import、三个原生源与 `ComicSourceManager` 未迁，**整个工作区编译不过**。判据：上一次构建是绿的，而新 `SearchPage` 的 KDoc 原样引用了我上一条消息的实测数字。

→ 处置：经用户确认那边已停手后接手补完，并把接口变更扩散到的 `MainActivity`（自检块）与冻结页 `SourceSectionScreen` 一并适配。冻结页那处只改到能编译、**不启用**新能力。
