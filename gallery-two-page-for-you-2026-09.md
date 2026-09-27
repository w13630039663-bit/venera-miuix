# 画廊主 Tab 双页化（每日推荐 / 猜你喜欢）方案 —— 2026-09-28

参考实现：[breadboardapp/breadboard](https://github.com/breadboardapp/breadboard)。
本文对它的引用全部来自**源码原文**（本轮是走 HTTPS 直取 raw 文件读的，浅克隆那份已不在本机）。
上一轮的机制与算法判定在 `gallery-recommendations-from-favourites-2026-09.md`，本文**不重推导**，
只接它 §九 第 4 步与 §十.7 那条"等语料数出来再决定"的尾巴。

## 〇、这份文档要定什么

**目标**：画廊主 Tab 从"一面日榜墙"变成**两页可横滑的墙** ——
第 0 页 每日推荐（现有日榜，行为逐字不变）、第 1 页 猜你喜欢（收藏抽标签 → 现成搜索链路 → 分页混合墙）。

**Scope**：
1. 参照物的主页形态量准（§一）—— 借的是**形态**，不是数据；
2. 真机语料那个数终于量到了（§三）—— 它把设计问题从"会不会空"改成"会不会复读"；
3. 与既有裁决的冲突点（§五、§七）；
4. 新增判据层与分页语义定形（§六）；
5. 切分与不做的事（§十、§十一）。

**Stop Condition**：§四 的八条拍完即可按 §十 开工。

## 一、Breadboard 主页到底是什么形态（源码原文）

| 项 | 它的做法 | 出处 |
|---|---|---|
| 切页 | `HorizontalPager(state, key = { BrowseTab.entries[it].name }, beyondViewportPageCount = 1, verticalAlignment = Top, userScrollEnabled = !shouldShowLargeImage)` | `home/HomeScreen.kt` |
| 切换器 | `TagPageIndicator(label = tab.label, selected = pagerState.currentPage == index, onClick = { animateScrollToPage(index) })` 一排 ToggleButton，画在内容上方 | 同上 |
| 两页 | `BrowseTab` = **FOR_YOU + FOLLOWING**（For You 与画师关注流） | 同上 |
| For You 取数 | `RecommendationsProvider` → `provider.recommendImages()` | 同上 |
| 池子常量 | `DEFAULT_POOL_SIZE = 7` | `util/RecommendationsHelper.kt` |
| 空态 | 一句 `No recommendations right now.` 式的老实话，**不拿热门池顶包** | `HomeScreen.kt`；行号我只有上一轮浅克隆时记的 `RecommendationsSettingsScreen.kt:207-213`，本轮没复核，转引 |
| 默认页 | `defaultTab` / `prefs.defaultBrowseTab` | `HomeScreen.kt` |

⚠️ **它主页两页其实是 For You + 关注画师，没有"日榜"这一页。** 所以"每日推荐"是我们的东西，
只能借它的**形态**（pager + 顶部切换器 + 每页一面墙 + 老实空态），不能借它的数据。
它也没有"排掉已收藏"这一步（`RecommendationsHelper` 通篇没有 exclude 逻辑）——
这一条我们**必须有**，原因见 §三。

## 二、上一轮为什么排除这件事，现在凭什么能做

上一轮（2026-09-27）把"落地流换源"整条划出范围，两个理由：① 要碰 `GalleryViewModel`（保护域）；
② §九 第 0 步那个"收藏语料到底有几张"没量到（设备中途掉线）。

本轮两栏都清了：
- **不需要碰 `GalleryViewModel`**（§五 的结论，比原方案 B 更省）；
- 语料量到了（§三）。

## 三、真机语料实测（这个数字决定了本轮的第一性需求）

2026-09-28，一加 PJZ110（`8bfdaeb5`），**只读取回** `run-as … cat files/gallery_favorites.json`（5952 B，设备侧与本地字节数一致）：

| 读数 | 值 |
|---|---|
| 总收藏 | **5** |
| 按站 | yandere **3** / gelbooru **2** |
| 带 tags 的 | 5 / 5（没有空标签行） |
| 去重标签 | 123 枚，其中 **115 枚只出现 1 次** |
| 平均每张标签 | 27.0 |

把 `GalleryRecommendations` 的真实判据跑在这批语料上：

| 站 | 候选池（前 7） | 池内两两共现 >0 | 最大共现张数 | 抽出几枚 |
|---|---|---|---|---|
| yandere（3 张） | 满 7 枚 | 19 / 21 对 | 2 | 3 枚（成立） |
| gelbooru（2 张） | 词频全 1，全靠"同次数按名字定序"兜底才凑满 7 | 9 / 21 对 | **1** | 主标签之外的候选**只剩同一张图自己的其余标签** |

**所以当初担心的"上线即空页"不是这里的病**。真正的病是另一种更难看的：
**语料 5 张时猜你喜欢是"复读页"** —— 抽出的标签串基本就是用户收藏那几张的标签子集，
拿去站方一搜，顶回来的大概率正是已经收藏过的那几张。

由此得到本轮第一性需求：**推荐页必须排掉已收藏的图**（按 `uid`），并且页尾要把"排掉几张"如实报出来。
仓里此前**没有任何一处**做过"从列表里滤掉已收藏"（全量 grep 过，`contains(uid)` 只用于大图页那颗心），
所以这条判据要新建，而且要落在纯函数层以便上单测。

## 四、拍板记录（八条）

用户批准（2026-09-28，四项全选推荐项）：

1. **形态**：横滑 pager + 顶栏分段行**都做**（照 Breadboard）。代价认了：网格内横滑起手会切页，多一处手势面。
2. **换一批只换当前页**：For You 自持一个 seed，`GalleryViewModel.seed` 仍是**日榜唯一种子**。
   两页 seed **不联动** —— 在推荐页按"换一批"却把没在看的那屏日榜也换掉，是最容易被读成 bug 的联动。
3. **排掉已收藏 + 页尾报数**。代价认了：以现在 5 张的语料，滤完可能一页就到底，屏上会显得"没几张"。
4. **冷启动固定落每日推荐**，推荐页首次可见才取数。

我按既有裁决定死（不改口径，标出处）：

5. **两站混合**：推荐页与日榜同形 —— 两站各抽一份标签、各取一页、跨站去重后混摆。
   依据：`recommendBySite` 本就一站一份；只按一站会让另一站凭空消失，那是静默降级
   （同 `GalleryFeedSource` 那条"慢的站这一轮缺席，必须被说出来"）。
6. **系统返回**：停在推荐页按返回**直接关画廊**，不做"先回第 0 页"。
   依据：现 `BackHandler` 只服务搜索态（`GalleryScreen.kt:134-142`），横滑 pager 配两级返回最容易长成"按两下没反应"。
7. **分段行的槽位互斥**：搜索（`svm.active`）或反搜（`rvm.open`）开着时分段行**让位**给搜索区 ——
   `bottomContent` 那条槽是唯一的，两者不能同时画（`GalleryScreen.kt:693-729`）。
8. **空态四档必须不同脸**：`NoSeeds`（引导去收藏）／`NoUsableTags`（**我们的错**，念出命中规则）／
   取数失败（给重试）／**全被"已收藏"剔空**（新档）。
   依据：判据层早就把前两种分开了（`GalleryRecommendations` 头注"合成一个空列表会让两种失败长成同一张脸"），
   新增的第四种同理 —— 它既不是用户没干活，也不是我们算不出，是"口味已经全收完了"。

## 五、状态归属：为什么不复用那个 707 行的搜索 VM

上一轮我把"新增一个状态持有者"当成代价最小的选项，**这个推荐是错的**。读完全文后两条实测理由否掉了
"给 For You 再开一个 `GallerySearchViewModel` 实例（`viewModel(key = …)`）"这条路：

- 它是**单站引擎**：一个 `site` 字段 + 一份 `filters`（`GallerySearchViewModel.kt:63`/`:82`），
  两站混合要两个实例然后在外面再合一次 —— 而那"再合一次"正是新增判据层，等于绕了一圈；
- 它每次第 1 页成功都 `saveHistory`（`:519`）：机器生成的标签串会**污染用户的「最近搜索」**。
  那行 chips 与历史区是同一个入口，被我们自动生成的串灌满之后，用户自己搜的东西就看不见了。

所以新建 `gallery/ui/GalleryForYouViewModel.kt`，**只依赖纯/域层与两个 client**：
`GalleryRecommendations`（现成，不改）+ `GallerySearch.queryOf` / `isExhausted`（现成）+
`YandeReClient.searchPosts` / `GelbooruClient.searchPosts`（现成）+ 本轮新增的 `GalleryForYouMerge`。

⚠️ 顺带修掉一处**错牵**：`GalleryScreen.kt:177` 那行 chips 现在读 `vm.seed`，
于是"换一批日榜"会把搜索卡里的推荐标签一起换掉 —— 用户从没要求过这个联动，
而且它违反 chips 自己的注释意图（"返回时组合重建会重跑出一批不一样的推荐标签"是要防的事）。
chips 与推荐页是**同一次抽样**，seed 改读 `fvm.seed` 才是同源的。

## 六、新增判据层 `GalleryForYouMerge`

`GalleryMerge.mix` **不能复用**：它是"一次采 20、整屏打乱、无页"，
而且当初正是为了防"到底了还显示加载更多"才把翻页 `exclude` 删掉的（`GalleryMerge.kt:14-16`）。
推荐页要的是"每页 20×2、跨页去重、按站判到底"，不同形。

```kotlin
object GalleryForYouMerge {
    const val PER_SITE_PAGE = 20
    fun page(
        pools: Map<GallerySite, List<GalleryPost>>,
        seed: Long,
        page: Int,
        seenKeys: Set<String>,        // 前几页已上屏的去重键
        favouriteUids: Set<String>,   // 拍板第 3 条的落点
    ): Result<Paged>                  // posts / perSite / excludedFavourite / droppedUnusable / droppedDuplicates
}
```

链上四条判据全部**沿用现成的**，不另写一套：
`GalleryMerge.isDisplayable`（扩展名白名单 + `previewUrl` 非空 —— 这是历史上"搜得出条数、屏上一张都没有"的成因）→
剔 `favouriteUids` 并计数 → 跨站去重（键 uid / `md5:` / `src:`；`dedupKeys` 现在是 `GalleryMerge` 的
**private**（`:129`），提为 `internal` 供两处共用，**不重抄**）→ 按 `GallerySite.entries` 固定站序各取 20 →
`shuffled(Random(seed + page))` 保证组合重建后同序。

**"还有下一页吗"**：`returned < limit` 复用 `GallerySearch.isExhausted`，但**每站单独记 `sitesDone`** ——
某站先到底只停它，另一站继续；`exhausted = sitesDone.size == 2`。
混成一个全局标志就会出"一站还能出货、页尾写着到底了"的假读数。

## 七、顶栏 chrome 的几何（照收藏页 2026-09-27 那次拍板）

- 切换器用现成的 `VeneraSegmentedButton(options, selectedIndex, onSelect, modifier)`
  （`components/venera/VeneraSegmentedButton.kt:57`），**不画任何不透明底板** ——
  玻璃层里涂实心底会出一条横贯硬边（收藏页踩过，见记忆「玻璃顶栏上的内联展开区三条硬约束」）。
- 避让用**常量**，绝不逐帧量、绝不从 `TopAppBarState.heightOffsetLimit` 推
  （那是普通 `var` 且存负值，composition 期读不到，见记忆「miuix 顶栏 heightOffsetLimit 不可观察」）：
  `galleryTabsRowHeight = (segmentedHeight 48dp ｜ segmentedHeightWide 56dp) + space3 × 2`，
  与 `FavoritesScreen.kt:199-206` 逐字同式。
- 并进现成那条 `animateDpAsState`（`GalleryScreen.kt:359-363`）的 else 分支：
  `if (svm.active) topBarFloor + areaHeight + space3 else topBarFloor + galleryTabsRowHeight` ——
  分段行让位给搜索区时避让落回搜索区实测高度，两处仍像同一段动作（同 `motion.medium` 220ms）。

## 八、pager 机制（照 `FavoritesScreen.kt:169/241` 那一套）

- `rememberPagerState(initialPage = 0) { 2 }`、`beyondViewportPageCount = 1`、`key = { it }`、
  `flingBehavior = PagerDefaults.flingBehavior(..., spring(LowBouncy, StiffnessMediumLow))`，
  `settledPage ↔ 选中态` 双向同步（`snapshotFlow` + `animateScrollToPage`）。
- `userScrollEnabled = !svm.active && !rvm.open && !imeVisible` —— 对应它 `!shouldShowLargeImage` 的门控精神。
- **每页各一把 `LazyStaggeredGridState`**，各挂各的 `nestedScroll` + `blurBackdropSource`；
  **backdrop 只喂当前页**（`val bd = if (pagerState.currentPage == page) topBarBackdrop else null`，
  `FavoritesScreen.kt:256-257`）—— 两页共写一层背板，顶栏模糊会采到邻页的图。
- **选中态 `rememberSaveable`、数据住 VM**：导航条目被覆盖时组合会销毁，裸 `remember` 的页码会被打回第 0 页
  （记忆「导航条目会重建组合」，`FavoritesScreen.kt:161` 就是为此）。
- **续页那条 `snapshotFlow` 每页各一份**：现在它整块硬编码读 `svm`（`GalleryScreen.kt:399-421`），
  三道闸门原样保留（新一轮先滚回顶 / 续页失败不自动重试 / 在取时不并发）。
- **大图页左右翻页各交各的队列**：`GalleryViewerQueue.set(本页 displayCards.map { it.post })`，绝不跨页。

## 九、页尾读数与四档空态

页尾 `GalleryForYouEnd` 沿用 `GalleryFeedEnd` 的"报张数 + 点名成因"调性：
`这一轮猜你喜欢：yande.re X · Gelbooru Y（含 Z 个视频）` + `已排除 N 张你收藏过的图`（N > 0 才追加）
+ 缺席那站的原因（`failures`，复用 `GallerySourceNotice`）。

| 档 | title | 出口 |
|---|---|---|
| 两站都无收藏 `NoSeeds` | 「还没有能推荐的基础」——先去大图页点收藏几张，这里就照你的口味找 | 无按钮 |
| `NoUsableTags`（有收藏但标签全被规则滤光） | 「标签都被你的规则挡了」+ 念出命中的规则 | 无按钮（该去规则页） |
| 取数失败 / 两站都空手 | 「这一轮没取成」+ 原因 | 「重试」→ `fvm.refresh()`（**必须先清熔断**，否则按下去必然还是那句"熔断中"，是假按钮） |
| 全被"已收藏"剔空 | 「能推的都收藏过了」+ 排除张数 | 「去看每日推荐」切回第 0 页 |

Gelbooru 未配账号沿用现成那句 `NEEDS_GELBOORU_ACCOUNT` 的分支判据 —— 说「这一站还不能用」，
不说「没搜成」（一次请求都没发出去，说成"没搜成"会引导用户去重试、去查网络）。

## 十、切分（7 笔，一笔一个关切）

0. `docs(gallery)`：本文件；
1. `feat(gallery)`：`domain/GalleryForYouMerge.kt` + `dedupKeys` 提 internal；
2. `test(gallery)`：`GalleryForYouMergeTest`；
3. `feat(gallery)`：`ui/GalleryForYouViewModel.kt`；
4. `feat(gallery)`：`GalleryScreen` 拆两页（pager / 分段行 / 每页 gridState / backdrop 门控 / 续页 ×2 / 避让常量 / chips 种子改源）；
5. `feat(gallery)`：`GalleryForYouEnd` + 四档空态；
6. `feat(gallery)`：换一批按当前页路由。

## 十一、明确不做

- **不动 `GalleryViewModel.kt`**（原保护域，本轮确实不需要它）；**不动 `FavoritesScreen.kt`**（2026-09-18 冻结，只照抄几何）；
  **不动 `VeneraFloatingNavBar.kt`**（导航层保护域）；
- **不做标签分类权重**（上一轮 §九 第 3 步，数据面拿不到，与本轮无关）；
- **不做关注画师流**（Breadboard 的另一页；我们没有"关注"这个数据面，画廊只有收藏）；
- **不动搜索卡里那行 chips**（`GallerySearchArea.kt:1018-1091`）—— 它是"手动挑一枚推荐串"的入口，
  与自动整串开搜的推荐页是两件事，删掉它就删掉了"看看我们给你抽了什么"这扇窗；
- **不给推荐页做站切换 UI**（两站混摆已经定了，见 §四.5）。

## 十二、待真机验证 / 风险

1. **排除收藏后每页实际张数**：语料 5 张，若恒 < 10 再谈是否放宽 `SELECTION_SIZE`
   （放宽 = 标签更少 = 结果更泛，另一处取舍，本轮不动）；
2. `PER_SITE_PAGE = 20` 与搜索链路 `limit = 100` 不同形：倾向**沿用现成 limit、进屏裁 20**，
   代价是每页只用到返回的 1/5 —— 这是**取舍不是笔误**，要写在注释里；
3. 两站同一串标签跨站可能返回同一张图 → `src:` 键的覆盖度未实测；
4. 顶栏分段行在折平/让位两种动画下会不会出硬边或空带（收藏页那两种失败模式都在案，见
   记忆「收藏页二级分段器必须挂顶栏 chrome」的实测数字）；
5. 冷启动两页并存在 `HostCircuitBreaker` 上是否互相踩（日榜与推荐页打的是同两个 host）。

## 十三、落地记录

（按 §十 逐笔落地后回写：每笔提交号 + 真机验证结论 + 与本文不符处）
