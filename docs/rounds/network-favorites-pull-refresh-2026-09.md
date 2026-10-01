# 网络收藏页 —— 下拉刷新重做（波浪环跟手）

> 日期：2026-09-19 ｜ 分支：compose-migration ｜ 页面：`feature/NetworkFavoritesScreen.kt`
> 状态：**代码完成，Build QA 三项全绿，真机 QA 待做**
> ⚠️ 该页 **FROZEN**（2026-09-18 第三批）。本轮属**用户点名豁免的交互架构改动**，已登记进 `FREEZE-STATEMENT.md` 的「冻结豁免记录（2026-09-19）」。

## 0. 对象澄清

用户说的是「收藏页」。实测只有 **网络收藏**（`NetworkFavoritesScreen.kt`）有下拉刷新；本地收藏页 `FavoritesScreen.kt` 里 `grep refresh|PullToRefresh` **零命中**。且需求提到「漫画源下方」—— 只有网络收藏有源药丸栏。故按网络收藏实施。

## 1. 需求 → 机制

| 用户描述 | 实现 |
|---|---|
| 取消顶栏那个下拉刷新动画 | `PullToRefreshBox(..., indicator = {})` —— 用空实现替换 M3 默认的圆形箭头（它浮在状态栏区域，和折叠顶栏抢位置） |
| 手动下拉时卡片自然跟手下移 | 源栏**正下方**插一个 LazyItem，其高度 = `pullRefreshRowHeight(48dp) × pullState.distanceFraction`，1:1 跟手；卡片被这一行顶下去 |
| 在漫画源下方出现 `CircularWavyProgressIndicator` | 就是上面那一行的内容，28dp，`color = primary` / `trackColor = surfaceVariant` |
| 放手后刷新 | `onRefresh` 置 `pullInFlight = true` 并 `vm.refresh()`；`pullProgress` 锁在 1f → 行高停在满高，波浪环持续旋转 |
| 刷新完成收藏卡片上移出现新内容 | `pullInFlight` 归 false → 行高动画回 0，卡片上移；新数据此时已在 `vm.comics` 里，原位换入 |

**为什么用「行高」而不是 `graphicsLayer.translationY`**：用户要的是环出现在**源栏下方**（内容之间），不是浮在顶部空隙里。translationY 会把整列连同源栏一起推走，环只能浮在顶上 —— 语义不对。做成真实 LazyItem 后，「跟手下移 / 收回上移」和「环在源栏下方」是同一件事，不需要测量源栏高度，也不会双重位移。

## 2. 顺带修掉的两个既存缺陷

这两条本身就够「修实际 Bug」的标准，是本轮改动站得住的核心理由：

1. **`PullToRefreshState()` 没有被 remember**（原 `:130`）。它是普通工厂函数而非 `remember…`，每次重组都新建一个 state 对象 —— 下拉进度与动画归属在重组后即丢失。改为 `rememberPullToRefreshState()`。
2. **`isRefreshing` 直接吃 VM 的 `isLoading`**（原 `:132`）。`isLoading` 同时被 `refresh()`（`NetworkFavoritesViewModel:242`）和 `loadMore()`（`:274`）复用，所以滚到底加载更多时，刷新指示器也会被点亮。改为屏幕侧记一次「由用户下拉发起」的 `pullInFlight`，等 VM 的 loading 起落后再收回。

## 3. 刷新期间保留旧卡片（屏幕侧快照）

`vm.refresh()` 一进来就 `resetComicState()` → `comics = emptyList()`（VM:165）。若 UI 跟着清空，用户看到的是「卡片消失 → 转圈 → 卡片回来」的硬切，而不是「卡片被顶下去、新内容原位换进来」。

VM 属冻结范围，不改它；在屏幕侧做快照：

```kotlin
val shownComics = if (pullInFlight && comics.isEmpty()) pullSnapshot else comics
// onRefresh 里：pullSnapshot = comics  ← 必须在 vm.refresh() 之前
```

网格两个分支（`isDetailed` / 双列 chunked）与两个空态判定统一改吃 `shownComics`；`firstLoading` 分支加 `&& !pullInFlight`，避免波浪环下面再叠第二个 Loader。

### 3.1 第一版「闪一下」的两个根因（已修）

真机反馈「下拉手动加载完后页面还是会闪下」，查出来是两处，**都在这轮第一版里由我自己引入**：

1. **快照抓晚了**。`onRefresh` 是普通 lambda，`vm.refresh()` 里的 `resetComicState()` 是**同步**执行的；而第一版用 `LaunchedEffect(pullInFlight) { pullSnapshot = comics }` 抓快照 —— LaunchedEffect 在**重组之后**才跑，那时 `comics` 已经是空表，于是快照为空、卡片当场消失，数据回来再整块弹回来。改为在 `onRefresh` 内、调用 `refresh()` **之前**同步抓。
2. **收尾有 250ms 死等**。第一版用 `delay(250)` 兜 `refresh()` 来不及置 `isLoading` 的情况，结果是数据早就回来了，环还多挂 250ms 才塌 —— 视觉上就是「闪一下」。改为 `pullSawLoading` 标志：**先见过 loading 起来、再落下**才算本次刷新结束，立刻收回；只有「始终没见起」这种 `refresh()` 同步早退的情形才走 600ms 兜底。

## 3.2 同轮：本页加载环全部换成波浪形

`AccordionLoader`（整页/首屏加载，48dp）、`FolderChipRow` 的文件夹加载（28dp）、触底「上滑加载更多」footer（28dp）三处 M3 普通圆环 → `CircularWavyProgressIndicator`。详情页预览卡头部的加载环一并换掉，并从 16dp 提到 28dp（波浪低于约 24dp 就看不出来了）。

新 Token：`loaderPage = 48.dp`（整页/区块居中）、`loaderInline = 28.dp`（行内：下拉刷新行、分区头、footer）。原 `pullRefreshIndicatorSize` 与 `loaderInline` 重复，已合并。

## 4. 未动清单（冻结页的边界）

手风琴状态机 `expandSource` / `collapseSource`、`NetworkFavoritesViewModel` 全部、行级虚拟化契约（全页唯一 LazyColumn、chunked 行 = LazyItem、不嵌套同向 Lazy）、长按删除二次确认、触底自动加载 footer、`ON_RESUME` 自动刷新观察器。

## 5. QA

**Build QA（2026-09-19）**：`:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleDebug` 三项 **BUILD SUCCESSFUL**。

**待真机确认的点（含我自己没把握的）**：
1. 顶栏那枚 M3 箭头是否**真的消失了**（`indicator = {}` 只是把插槽内容换空，M3 是否还画容器需实机看）。
2. **跟手度**：`animateDpAsState` 用的是默认弹簧，追手指可能有轻微滞后。若觉得黏，改成下拉期间直接用 `48dp × distanceFraction`（不过动画）、只在放手/收回时走动画。
3. `pullState.distanceFraction` 是否被 M3 钳在 0..1（代码里 `coerceIn` 兜了底），过度下拉时行高不应超过 48dp。
4. 与折叠顶栏的 `scrollConnection` 共存：`pullToRefresh` 在外层、`nestedScroll` 在 LazyColumn 上，顺序与改造前一致，但下拉手势链路要实测（这是本轮最高风险项）。
5. **本轮重点回归**：下拉 → 放手 → 卡片是否**全程不消失**、数据回来后环是否**立刻**收回（不再闪）。若某源极慢仍出现提前收环，说明 `pullSawLoading` 判据不够，需要 VM 暴露专用标志（要再豁免一次）。
6. 多文件夹源：环出现在源栏与文件夹 Chips **之间**，确认观感不奇怪。
7. 首次展开就下拉（没有旧内容可留）：应只看到波浪环，不闪空态、不闪 Loader。
8. 刷新失败：环收回后应正常显示「收藏加载失败 + 重试」，不残留旧快照。
9. LIGHT / DARK、360dp 窄屏、双列与详细列表两种布局各测一遍。
10. 长按删除、点卡片进详情、切源、回到顶部按钮在改动后无回归。

## 6. 改动文件

| 文件 | 性质 |
|---|---|
| `feature/NetworkFavoritesScreen.kt` | 冻结页豁免：下拉刷新机制 + 两处既存缺陷 + 刷新期快照 |
| `ui/tokens/Spacing.kt` | 新增 `pullRefreshRowHeight = 48.dp` / `pullRefreshIndicatorSize = 28.dp` |
| `FREEZE-STATEMENT.md` | 登记 2026-09-19 豁免 |
