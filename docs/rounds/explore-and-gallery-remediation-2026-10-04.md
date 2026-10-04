# 2026-10-04 九项整改（探索页 tag / 榜首入口 / 长 tag / 鬼畜 / 头像 / 字体 / 滚动 / 关于这张图 / hero 转场）

用户一次给出 9 条。目标端经确认 = **只 Android 手机（`:app`）**；桌面那条并行线不动。
授权口径："全部一起做，你自行评估，自行修改，自行复核，有分歧不需要我同意自行按照推荐做法"。
所以本文既是方案也是账：每条先记**实测读数**，再记**拍板**，落地后回写「已改」。

批次切法按耦合度，一批一次提交。

| 批 | 覆盖用户条目 | 主战文件 |
|---|---|---|
| A 探索页 tag 与榜 | 1 | `source/explore/SourceExploration.kt`、`source/js/JsComicSource.kt`、`feature/explore/UnifiedExploreScreen.kt` |
| B 长 tag 截断 | 4 | `components/venera/VeneraChip.kt` |
| C 画廊滚动位置 | 7 | `feature/Navigation.kt`、`gallery/ui/GalleryScreen.kt` |
| D 画师头像统一 | 5 | `gallery/ui/GalleryArtistRows.kt`、`gallery/ui/GalleryHomeSections.kt` |
| E miuix 字体字号 | 6 | `feature/VeneraTheme.kt`、`ui/tokens/*` |
| F 关于这张图 | 8、9 | `gallery/ui/GalleryInfoSheet.kt` |
| G 鬼畜（滚动中图片反复重建） | 3 | 待定位 |
| H hero 转场 + 长按菜单 | 2 | `gallery/ui/GalleryFlyGeometry.kt` 等 |

---

## 批 A：探索页 tag 过多（1）+ 榜首入口（1）

### A.1 实测读数（33 个源脚本全扫，脚本只做静态扫描，不执行源）

**全仓只有一个分区声明了 `type: "random"`**，而它正是用户点名的那个：

```
nhentai.js | Tags | random | randomNumber: 20 | Object.values(Nhentai.nhentaiTags)  ≈720 条
```

官方 master 有这条链路：`parser.dart:470-472` 把 `type=="random"` 建成 `RandomCategoryPart`，
`category.dart:82-86` 读数时取随机窗口 ——

```dart
if (randomNumber >= all.length) return all;
var start = math.Random().nextInt(all.length - randomNumber);
return all.sublist(start, start + randomNumber);
```

我们迁移时**把这个字段整个丢了**：`JsComicSource.kt` 的分类脚本只输出 `name/type/items`，
`CategoryPart`（`ComicSourceModels.kt:279`）也没有 `randomNumber` 位。所以 nh 的 720 条一枚不落全渲染。

大组清单（`type: "fixed"`，源没给窗口声明的）：

```
mh1234 题材 343 | ikmmh 分类 42 | picacg 主题 40 | copy_manga 主题 40(表达式)
manhuagui 类型 40 | mycomic 作品类型 39 | komiic 主题 38 | jcomic 分類 38
mxs 题材 24 | mycomic 出品年份 22 | jm 主題A漫 18 / 特殊PLAY 18 | hcomic 热门TAG 29
manhuaren 类型 29 | goda 标签 30 | mh18 标签 30 | wnacg 各分区 1..7
```

另有 4 个源在 **fixed** 分区上写了 `randomNumber: 5`（baozi 类型 27、ikmmh 分类 42、wnacg 全部、manga_dex Tags）。
**官方不读它** —— `parser.dart` 只在 `type == "random"` 分支取 `randomNumber`。
拍板：**照官方，不顺手扩读**，否则 baozi/ikmmh 会从"整组露出"变成"只露 5 枚"，那是官方没有的观感。

`type: "dynamic"` 的分区（kavita 4 个、komga 6 个、lanraragi 1 个、manga_dex 1 个）我们的实现**恒出 0 条**
（要调 `p.loader([])`，迁移没接）。记为**本轮不做**：它表现为"分类区少了这个组"，不是"tag 过多"，
和用户报的是两件事，硬塞进来会把 A 批的改动面从"显示口径"扩到"执行源脚本"。

### A.2 拍板

1. **补 `randomNumber`，逐字对齐官方**（`type=="random"` 才读，缺省当 1，`n >= size` 整组原样）。
   落在 `SourceExplorationFactory.toNativeSection()` 而不是 `JsComicSource`：那是 UI 的唯一构建入口
   （文件头契约「UI 永远不直接读 explore/category 原始结构」），纯函数、可单测，不必把 JS 引擎拖进测试。
   窗口在**每次重建 explorations 时定一次**（冷启动/手动刷新），不在组合期摇 —— 官方在 getter 里摇，
   我们的组合会随导航条目重建（[[project-nav-entry-recomposition]]），照抄那一条就变成"标签自己乱变"。
2. **不动 `SECTION_ENTRY_BUDGET` 的组级语义**。2026-09-20 已拍板「截断粒度是整组，组内切一半比切整组更难读，
   用户尚未拍」（[[project-explore-two-block-layout]]）。本轮加的是**第二道闸**：折叠态每组最多渲染
   `SECTION_ITEM_CAP = 20` 枚，超出与整组未露出的部分一起进 `hiddenEntryCount`，由既有的
   「更多分类（还有 N 个）」一并展开。20 不是造的数 —— 它是全仓唯一一处源自己声明的窗口尺寸（nh 的 20），
   定在它下面就会让 fixed 组比官方的 random 组更短。
3. **榜首进「本源分类」区，逐档一枚入口**，标签用源自己给的档名（jm：總排行/月排行/周排行/日排行；
   picacg：日/周/月；nhentai：Popular Today…），点它 = **页内切到该 RANKING 探索方式**（不跳二级页），
   并把 `unifiedFilter` 清掉 —— 与「快捷筛选」同一套互斥纪律。
   位置在分类区**首位且不吃预算**（榜只有 1–6 枚，且它是用户点名要够得着的东西；
   排在组预算里会被 mh1234 那种大组顶掉）。
4. **`nativeSections` 里不放合成的榜组**：那个字段的头一条契约就是「原样保留源声明的分区名、绝不合并」，
   而 zaimanhua 自己就有一个叫「排行榜」的 fixed 分区，合成组塞进去会撞名且破契约。改新开一个字段
   `SourceExploration.rankingSection: NativeSection?`。
5. **探索方式那行 chip 里去掉 `排行·X`**：同一动作有两个入口是噪声。`modes` 数据面**照旧保留** RANKING
   档（内容取数与选中态解析都靠它），只在 `ExploreModeRow` 的调用点过滤。
6. **删掉旧的单张「🏆 排行榜」卡**：它只会选 `modes` 里的**第一档**，而 jm 的第一档是「總排行」——
   用户要的「每日榜首」排在第 4 个，这正是"进分类区加每日榜首"这句反馈的真实成因。

### A.3 已改（2026-10-04）

| 文件 | 改动 |
|---|---|
| `source/model/ComicSourceModels.kt` | `CategoryPart` 加 `randomNumber: Int = 0` |
| `source/js/JsComicSource.kt` | 分类脚本输出 `randomNumber` 原值；Kotlin 读 `(pm["randomNumber"] as? Number)?.toInt()`（gson 把 JSON 数值解成 Double，不能直接 cast Int） |
| `source/explore/SourceExploration.kt` | `toNativeSection()` 对 `type=="random"` 走 `randomWindow(items, randomNumber.coerceAtLeast(1))`；新增 `rankingSection: NativeSection?` 与 `NativeEntry.rankingOption: String?` |
| `feature/explore/ExploreViewModel.kt` | 新增 `SECTION_ITEM_CAP = 20` |
| `feature/explore/UnifiedExploreScreen.kt` | 折叠态 `section.copy(items = items.take(CAP))`；榜组排「本源分类」首位、不吃预算、带选中态；`ExploreModeRow` 过滤 RANKING；删 `RankingEntry` 卡与其 composable（顺带去掉因此空转的 miuix `Surface` import） |
| `app/src/test/.../SourceExplorationTagWindowTest.kt` | 新增 11 颗用例 |

**已验证**：`:app:testDebugUnitTest --tests "com.venera.compose.source.explore.*"` EXIT=0，
新 11 颗 + 原有 `SourceExplorationRankingTest` 5 颗 + `SourceExplorationNativeEntryTest` 4 颗全绿
（`compileDebugKotlin` 通过 ⇒ UI 那侧的引用改动没有断）。
**真机未验**：按既有分工（设备由用户操作、我只读），nh 首屏应从 ≈720 枚降到 20 枚，
jm 的「本源分类」顶部应出现「總排行/月排行/周排行/日排行」四枚且点「日排行」就出日榜内容。

**一处对齐得比官方更严**：官方在 getter 里摇窗口，我们只在**每次重建 explorations**（冷启动/手动刷新）时摇一次。
原因是我们的组合会随导航条目重建（`project-nav-entry-recomposition` 那条既有结论），
照抄 getter 那一条就会让标签在页面上自己乱变 —— 那正是本文件 G 批要治的"内容自己变"那一型。

**官方那处开区间一起对齐了**：`nextInt(size - n)` 上界不含，所以全组最后一枚永远进不了窗口。
测试里用一条断言把它钉住，别将来被当成我们的 bug "修好"。

**冻结豁免**：`UnifiedExploreScreen.kt` 在 FROZEN 名单第一行，本轮动它的理由与"没动的东西"清单
记在 `FREEZE-STATEMENT.md` 的「2026-10-04 追加：九项整改对冻结域的触碰」。

## 批 B：tag 太长会被截断（4）

### B.1 实测读数

全仓只有一处渲染 tag 文本：`components/venera/VeneraChip.kt:265-271`，写死 `maxLines = 1` +
`TextOverflow.Ellipsis`（`VeneraTagChip` 只是转发它并固定 `variant = Tag`）。
`VeneraChip` 里没有 `widthIn`、也没有固定 `Modifier.width` —— 所以**截断不是宽度被写死**，
而是"一行放不下就省略"，长词在 FlowRow 里最多只能占满一行。

### B.2 拍板：不给组件改默认值，而是开一个逐点名的参数

`textMaxLines: Int = 1` 加在 `VeneraChip` / `VeneraTagChip` 上，默认 1 = **现有 12+ 个调用点一字不变**。

为什么不直接把默认改成 2：卡片高度不是封面决定的，而是**信息列最小高度 + 标签区**决定的
（`project-card-size-drivers` 那条既有结论：`tags.take(10)` + `FlowRow maxLines=3`，一枚 chip ≈32dp，
三行就 ≈116dp；曾经因为"卡片太大"来回改过 110→140→122→92 四轮）。
默认改 2 会让**每一面墙**的每张卡集体长高，而那不是用户报的问题。

于是失败模式分成两种，各配一档：

| 场景 | 档 | 为什么 |
|---|---|---|
| 列表卡里的只读标签（`ComicListPresentation.kt:174`、`FavoritesScreen.kt:1006`、`SearchScreen.kt:1329`） | 留 1 行 | 标签区就是整卡高度的驱动项 |
| 探索页分类/榜入口（`NativeSectionBlock`） | 2 | 入口即内容，读不到末了等于没有 |
| 探索页跨源快捷筛选（`UnifiedTagBlock`） | 2 | 同上 |
| 漫画详情页标签（`DetailTagChip`） | 2 | 页内自滚，不抢别的高度；漫画标签动辄七八十字符 |
| 画廊信息卡标签墙（`GalleryTagChip`） | 2 | 站方原文最长的一档（`red_rose_(flower)` 这种带括号的），且读不全也复制不全 |
| 画廊搜索已选条件 + 顶栏内联候选（`FilterChipItem` 等） | **留 1 行** | 那一屏每行都在抢卡片高度（玻璃顶栏三条硬约束），而且胶囊行是横滑的 |

超过 2 行仍省略：一枚 300 字的标签不该把 FlowRow 撑成一面墙，这是刻意的上界不是遗漏。

**已改**：`VeneraChip.kt`（参数 + 主 Text 用它）、`VeneraTagChip.kt`（转发 + KDoc）、
`UnifiedExploreScreen.kt` 两处、`ComicDetailScreen.kt:1792`、`GalleryInfoSheet.kt:966`。
**已验证**：`:app:compileDebugKotlin` EXIT=0（唯一告警 `VeneraChip.kt:307` 的 elvis 是既有代码，不属本轮）。

## 批 C：画廊搜索切走再切回不再回顶部（7）

### C.1 读数：两条独立成因，都不在"没保存"上

先把库的真相量掉，不靠记忆（`androidx.compose.foundation:foundation` 实测解析到 **1.12.0**，
不是 catalog 里写的 `compose = 1.7.8` —— 被 material3 1.5.0-alpha22 顶上去的）：
从 gradle 缓存的 sources jar 里逐行读 `LazyStaggeredGridState.kt:81-87`，

```kotlin
fun rememberLazyStaggeredGridState(...) : LazyStaggeredGridState =
    rememberSaveable(saver = LazyStaggeredGridState.Saver) { ... }
```

⇒ **那面墙的位置本来就是 saveable 的**，切 Tab 的 `popUpTo{saveState=true}` + `restoreState=true`
本该把它救回来。所以症状不是"没存"，而是"存了之后又被按回 0"，有两处会按：

**成因一：「已回顶凭据」记在 `remember` 里。**
`GalleryScreen.kt` 原先有两处 `var …ScrolledGeneration by remember { mutableIntStateOf(0) }`
（`:753` 搜索墙、`:498` 猜你喜欢墙）。而 `LaunchedEffect` 在组合重建后会**无条件再跑一次首帧** ——
凭据归零 ⇒ 守卫形同不存在 ⇒ 那条"新一轮结果落地 → `scrollToItem(0)`"每次都真跑一次。
这正是仓里既有结论「光把状态搬进 ViewModel 还不够，effect 重建后会再跑」的**反向形态**：
这次是守卫本身没搬进 VM，留在组合里。

**成因二：猜你喜欢那面墙根本没有"离开时看到第几张"的记录。**
搜索墙有（`svm.recordScroll`），主墙一条都没有，而它唯一的落点动作就是无条件回 0。

### C.2 拍板

1. 凭据搬进各自 VM：`GallerySearchViewModel.appliedGeneration`、`GalleryForYouViewModel.appliedGeneration`
   —— 与它守卫的那个 `generation` **住同一层**，否则重建后"这一轮已经落过点"这件事无从得知。
   续页判据里那两处 `settled` 一并改读 VM（同一个凭据，不留两份真相）。
2. 主墙的落点记在 **`rememberSaveable`**，不记在 VM：切 Tab 是被 **pop**，
   `NavBackStackEntry` 类头明写 "ViewModels will be cleared"，`saveState` 只救 saveable 那一族。
3. 落点必须**绑内容**，所以存的是 `(锚 = 当时墙上第一张的 id, 序号)` 二元组，判据抽成纯函数
   `gallery/domain/GalleryWallDepth.kt`。理由：换一批之后第 40 个序号指的是另一块内容，
   把人送去那儿既不是"回到原处"，还会让续页判据当场以为到底了。
4. 内容未落地时不落点（`currentAnchorId == null` 一律返回 null），并把 effect 的键补上
   `fvm.posts.size` —— 空墙上滚一次会被 Lazy 的夹取按成 0，而凭据一旦写上就不会重来。
5. 序号 0 与"没有落点要求"是两件事，判据返回值区分（`Int?`），测试钉住。

### C.3 明确不做（要做得用户点一句）

**切 Tab 时搜索那一屏整片消失**，本批没治。判据：`GallerySearchViewModel.active` 只能由用户动作置真
（`:484/:660/:727`），VM 被 pop 清空后没有任何恢复路径；条目作用域 VM 救不了切 Tab 是既有结论，
真要留只有**落盘**一条路（与日榜 `GalleryFeedCache`、猜你喜欢 `GalleryForYouCache` 同源）。
代价是把"搜索条件 + 已取回的结果 + 上下文栈"整体序列化，那是独立一批的活，且用户报的是位置不是内容，
所以先不扩。若真机上切回来发现搜索内容也没了，就是这条，说一声我按落盘做。

### C.4 已改 / 已验证

已改：`gallery/domain/GalleryWallDepth.kt`（新）、`GallerySearchViewModel.kt`、`GalleryForYouViewModel.kt`、
`GalleryScreen.kt`（两处凭据改读 VM + 主墙新增 recorder/落点 effect + 一个 `saveable` 二元组 + 两个 import）。

已验证：`:app:testDebugUnitTest --tests "...GalleryWallDepthTest"` EXIT=0（5 颗全绿），
`compileDebugKotlin` 执行并通过。

**未验（要真机）**：本环境按既定分工不代操作设备。要看的两条是
①画廊搜索滑到第 40 张 → 切首页 → 切回画廊，仍在那一屏附近；②点「换一批」之后**应该**回顶部
（这条必须还成立，否则是过度修复）。另外如果 ①切回来看到的是猜你喜欢而不是搜索结果，
那说明命中的是 C.3 那条未做的缺口。

## 批 D：统一全平台画师头像（5）

### D.1 读数：尺寸其实早就对齐了，没对齐的是**画法**

三处渲染点（Android 画廊）：信息卡画师行 `GalleryArtistRows.kt:271`、介绍页 hero
`GalleryArtistProfileScreen.kt:244`、首页「正在关注的画师」`GalleryHomeSections.kt:309`。
前两处调共用件 `GalleryArtistAvatar` 并各自传 `artistAvatarSize`(56dp)，
**首页那一处是自己手写的一份圆座**（`Box + clip(CircleShape) + VeneraCover` 或首字母 Text）。

于是真正的分叉有三条，都不是"尺寸"：

| 分叉 | 原值 | 为什么是坑 |
|---|---|---|
| 共用件的**默认档** | `sourceAvatarSize` = **34dp**（那是漫画源状态行的档） | 漏传 modifier 不报错，只会静默小一号；仓里已有「带默认值的新参数漏了没人发现」这条教训 |
| 首字母退路的字号 | 组件 14sp（`type.body`）vs 首页 22sp（`statNumber`） | 同一个人在两个页面两种退路长相，改一处忘另一处 |
| 脸径 | 两处显式传 56dp，一处硬编码在同一 token 上 | 值一致但**来源不一致**，将来谁改一处就分叉 |

档位（各站给哪一档）实测**已经是一致的**，不用动：`GalleryArtistUrls.avatarUrl` 是
「两档取大」（pixiv `imageBig` 的 `_170` 优先于 `_50`）、fanbox 单档 160、Mastodon 优先静态档
（`GalleryArtistEndpointParse.kt:106` 那条理由仍成立），且拿不到一律交 **null** 不交空串
（空串进 Coil 是"什么都不显示且没有原因"）。56dp 圆座在 3x 屏约 168px，取大档正好。

### D.2 已改

- `GalleryArtistAvatar`：默认档改成 `artistAvatarSize`；首字母字号统一成 `statNumber`(22sp，
  首页在真机上定下来的那一档）；头写清"全站唯一一处画师脸的画法"。
- 信息卡与介绍页两处**删掉显式尺寸**，一起吃默认档 —— 三处不再各传各的。
- 首页那一处手写的 `Box + VeneraCover + Text` 整块换成调这颗件；`faceOf` 的次序
  （真头像 > 名下最近一张收藏 > 首字母）留在 domain 不动，仍是调用方算好最终 URL 再传进来。
- 顺掉因此空转的 `CircleShape` import；修掉 `GalleryArtistEndpointParse.kt:106` 那句
  还写着"头像位只有 34dp"的过期注释（判据本身不变）。

### D.3 那条口子如实留着

首页退路里「名下收藏的缩略图」那条**不过屏蔽/分级那把**（2026-10-01 那轮就记着"口子变小但没关死"）。
本批没关，理由不是省事：收藏记录里存的是图的地址，**没有"该不该挡"的判据**，
要关得给 `GalleryFavoritesStore` 加一档落库时就算好的字段；而历史数据只能默认 false ——
按仓里那条既有规矩（**降级路径宁可错慢，不可静默交错**），把一屏旧收藏的头像全标成"不挡"
是静默错答，比留着这个口子更糟。所以本批做的是：把三条未保护的画法收成一条，
下一轮要上那把尺只需改一处。要现在就关，说一声，我按"读侧现判 + 判不了就不摆脸"做。

**已验证**：`:app:testDebugUnitTest` 全量 EXIT=0，**109 个类 834 颗用例 0 失败 0 错误**
（这一条同时是批 A–D 的回归读数）。真机读数交用户：三处头像应同尺寸同退路字号。

## 批 F：关于这张图（8 点不动 / 9 屏蔽画师）

「图片关于图片」= 画廊大图页右上角那枚 `Icons.Outlined.Info`（`contentDescription = "关于这张图"`，
`GalleryViewerToolbar.kt:185`）打开的 `GalleryInfoSheet`（面板标题就叫「关于这张图」`:298`）。

### F.9 屏蔽该画师 —— 已落地

**先确认这条不是假开关**：`GalleryInfoSheet.kt:243-262` 那条既有的 `blockTag` 存的是
`guard.addRule("TAG", tag)`，而它自己的注释写着实测结论 —— 画廊那面墙的屏蔽判据只喂 `post.tagList`，
yande.re / Gelbooru 两站的 `tags` 就是那串平铺标签（**没有** `tag_string_*` 分桶串），
画师名**就在这串里**；用 KEYWORD 反而会连作者名一起误伤。
所以「屏蔽画师」= 同一条链换个入口，不需要新规则类型，也不需要一个新存储。

改动：

- `GalleryArtistRows.kt`：`GalleryArtistCreditRows` 加两个**可空**回调
  `onBlockArtist` / `onCopyArtistName`；每位画师那张卡外面包一层 `Box` + `key(name)`，
  长按出 `DropdownMenu`（两项：**复制画师名「原名」** / **屏蔽该画师**（`StatusColors.Failing` 色，
  与那面标签墙的「屏蔽该标签」同一档）），长按前 `HapticFeedbackConstants.LONG_PRESS`，与 chip 那三项菜单同构。
- `GalleryInfoSheet.kt:316` 调用点把既有的 `blockTag` 与 `copyToClipboard` 接进去。

四条刻意的取舍：

1. **走长按不走明面按钮**：那张卡在 2026-10-01 第二次重排时定过口径 ——
   "行1右端只剩关注这一样可点物"，再加一枚明面胶囊就是把那次重排推翻，而信息卡"读起来像参数表"
   正是用户当轮报的问题。长按与同面板里那面标签墙的手势一致（点=搜索、长按=三项菜单）。
2. **锚点逐位各挂一个 `Box`**：与详情页 `DetailTagChip` 那条既有结论同因 —— 共用锚会让菜单
   永远从第一位的位置弹出来。
3. **`key(name)` 而不是裸 `remember`**：菜单开合态若按位置记忆，列表增减时会错位到别人身上
   （本项目已经因为"A 态能用 B 态不能用"的形态分叉踩过两次）。
4. **两个回调都不给时整块不挂长按**：摆一枚按出空菜单的卡，比没有长按更让人以为是自己按错了。
5. **复制与屏蔽都拿站方原串**，屏上摆译文 —— 与 `blockTag` 头注那条"译文会随字典更新失效，
   词表与检索词都存原值"同一条纪律。

**已验证**：`:app:compileDebugKotlin` EXIT=0。**未验**：真机上长按那张卡应弹出两项菜单、
选「屏蔽该画师」应出「已屏蔽「xxx」，可在设置 → 内容屏蔽管理」的 Toast，且回画廊墙那位的新图不再出现
（这三条都要读数，本环境不代操作设备）。

### F.8 「有时候无法点击，没有任何反应」—— 已修最强的一条，另两条等读数

只读勘察把整条链走了一遍（工具条 → `onInfo` → `infoOpen` → sheet 宿主条件 → Activity 窗口属性），
结论是**链上只有一处静默分支，其余"失灵"都是位置/可见性**问题：

| 候选 | 强度 | 证据 | 本轮处置 |
|---|---|---|---|
| **A. dock 居中重排让「i」换位置** | 高 | `GalleryViewerToolbar.kt:154` 是 `if (!post.isVideo)`，视频少一枚 HD 钮 ⇒ 整行窄一档 ⇒ 右边那几枚各往左挪约半枚（≈25dp）。用户从图片翻到视频时手是按**记忆**去的，那一下落在钮与钮的空隙里 | **已修**：视频档把那枚钮的位置留成 `Spacer`（留空位不是留假钮 —— 没有 clickable、没有图标、没有染色），占位尺寸走 `VeneraIconButtonTouchSize` 那个**唯一出口**，不在调用点写 48dp 字面值 |
| **B. 缩放期整条 dock 淡出** | 中-高 | `GalleryPostScreen.kt:1092-1096` `visible = showChrome && !zoomedIn`，而 `zoomedIn` 的判据是 `zoomFraction > 0f`（`:1352-1357`）—— 双指轻碰一下、没回落到 1x 也算"在缩放" | **不动**。这条判据要放宽就得引入一个阈值数，而仓里的规矩是不造没有出处的数字；且"缩放时收 chrome"本身是既定行为 |
| C. `infoOpen=true` 而 `current == null` | 事实存在、概率为推断 | 宿主是 `if (infoOpen && current != null)`（`:1291`）；`current` 为 null 只在单张模式那笔 `fetchById` 还在路上（`:656-674`） | **不改**：`infoOpen` 是 `rememberSaveable` 且不清，数据一落地 sheet 自己就冒出来，点击并没有丢 |

顺带量到的一条与本条无关但值得记：`GalleryInfoSheet.kt:190` 的
`rememberModalBottomSheetState()` 缺 `skipPartiallyExpanded`，而仓内另 3 处
（`VeneraReaderScreen.kt:1227/1341/1550`）都带 —— 这一张 sheet 因此只会**半展开**落地。
不是"点不动"的成因（它看得见），归到批 E 之后单独处理。

**要用户给的读数**（哪一条真的是用户遇到的）：停在一张**视频**上按「关于这张图」，
和停在**图片**上按，两次是否一次灵一次不灵 —— 若是，A 就是主因（本轮已修，修完两处位置一致）；
若是**双指放大过一下**再按才失灵，那是 B，需要用户点头才动那条判据。

---

## 批 E、G、H：待逐批回写

B/C/D/E/F/G/H 的现状读数已由代码勘察钉住（文件行号在各批文档段落里补），
每批落地后回写「已改 / 已验证 / 未做」。
