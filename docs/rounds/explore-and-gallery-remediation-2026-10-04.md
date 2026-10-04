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

## 批 G：鬼畜 = 滚动中图片反复重建（3）

用户确认的复现类型是**滚动中图片反复重建**。只读勘察按证据强度排了八条，
我复核后修的是**自激环那两条**，其余按事实记账。

### G.1 已修：每张可见卡每帧被重组一次

`GalleryScreen.kt:1620`（画廊卡封面那处 `onGloballyPositioned`）原来是无条件 `coverBounds = rect`。
两个事实叠起来就是风暴：

1. `coverBounds` 是 `remember { mutableStateOf(Rect.Zero) }`（`:1574`），
   `mutableStateOf` 默认按**引用**比较；
2. `it.boundsInWindow()` 每次返回一个**新的** `Rect` 实例，
   而 Lazy 项在窗口坐标系里的位置**每帧都在变** ⇒ 这个回调每帧都来一次。

于是滚动过程中每张可见卡每帧失效重组一次。改成 `if (rect != coverBounds) coverBounds = rect`
（`Rect` 的 equals 是结构相等）之后，只有几何真的变了才写。

同处顺带修的第二条来自 pixez 那轮踩过的坑（它的 fix 提交 `45d3a6ca` 把它写进了注释）：
`GalleryFlyIn.noteCardBounds` 原先照单覆盖，而 **Lazy 回收一个项之前的最后一次布局回调给的是 `0×0`** ——
那是组合噪声不是"这张卡没了"。照存的话，返回程查到的落点是空矩形。
改成**零尺寸只忽略、不注销**；项真没了仍由 `forgetCardBounds`（两处 `DisposableEffect.onDispose`：
`GalleryScreen.kt:1578`、`GalleryHomeSections.kt:192`）负责。

### G.2 已修（一支）：`chunked` 没 remember

`ComicDetailScreen.kt:208-211` 早就写着正确范式 ——「chunked 每次都会新建两层 List，
不 remember 就是每次重组换引用，把这一块的强跳过打掉」—— 而四处调用点全没遵守
（`SearchScreen.kt:1112/1128`、`UnifiedExploreScreen.kt:515/521/533/539`、
`NetworkFavoritesScreen.kt:331/346`、`LocalComicScreen.kt:252`）。

本轮只把探索页 **`ComicList` 那一支**提出去（`comicRows = remember(content, columns) { … }`），
因为它是随机/最新/各档排行实际走的那一支。`Sections`（singlePageWithMultiPart）那两支
要先按 part 预排一张行表才能离开 LazyListScope，动的是循环结构，**记为同一条修法的下一批**。
`SearchScreen` / `NetworkFavoritesScreen` / `LocalComicScreen` 三处同理未动（前两颗在冻结名单里，
且改动要逐页真机对照，不宜和这一批混在一起）。

### G.3 勘察提出、我复核后**降级或不修**的几条

| 条目 | 勘察的判断 | 我的复核 |
|---|---|---|
| 行 key 同时含 `rowIndex` 与行首 id，列数一变就换 key | 最强成因 | **降级**：列数真的变了行本来就要重组，去掉 `rowIndex` 收益很小；而只留行首 id 会在 id 为空时撞 key（Lazy 直接抛 "Key was already used"）—— 那个 `?: ""` 兜底正是当前写法带着 rowIndex 的理由 |
| 全仓没有一处传 `contentType` | 强 | **不修（记账）**：不同形态的槽位互相复用确实会整块拆建，但 `contentType` 必须按形态给稳定分组名，几十个 `item`/`items` 调用点逐个判"这属于哪一类"是另一批活，且错了会直接崩；本轮不动 |
| 打码判定每卡每重组都跑 | 事实 | **不是"闪"的成因**：`ContentGuardManager` 有 LRU（`CACHE_LIMIT=2000`），同一部漫画的判定是确定的，不会来回变；开销是 CPU + 一次全局锁，属性能账不属本条 |
| 画廊动图重解码 | — | **排除**：`GalleryAnimationGate.kt:39-41` 默认给 null（墙上恒静帧），而漫画侧那把 loader（`VeneraApp.kt:81-89`）**根本没注册** `AnimatedImageDecoder` |
| `FavoritesScreen.kt:796-803` 列表在 composition 里现算 `filter` | 事实 | **记账**：只在 NSFW=隐藏 那一档触发，且该文件在冻结名单里；一行 `remember` 能修，留与 G.2 同一批一起做 |

**已验证**：`:app:testDebugUnitTest` 全量 109 类 **834 颗 0 失败 0 错误**，`compileDebugKotlin` 通过。
**必须说的限制**：这一批是**代码级自激环**的修复，"滚动观感是否不再鬼畜"只有真机能定，
而本环境不代操作设备。若修完仍闪，下一个要读的是 `contentType` 那条（G.3 第二行）。

## 批 E：统一 miuix 字体与字号（6）

### E.1 读数

- **字型**：全仓零字体文件，`res/` 与 Manifest 都不声明 fontFamily，miuix 与 MD3 两边都落宿主系统
  sans-serif。pixez 那版也一样（`font-scale-pixez-alignment-2026-09.md` 第 1 节已实测）——
  **这一层本来就统一，没有东西可装可换**。
- **字阶**：`VeneraTheme.kt:135` 原先只把 colors 交给 `MiuixTheme`，字阶让库自带默认；
  而 `MiuixTypography` 是按官方值逐格对齐过的一份**独立副本**。
  两边今天数值相等，但那是**巧合式相等** —— 两份真相各写一处，改一处另一处不动，
  屏上就会出现"同屏两套字号"。
- **硬编码**：我复核过口径后重数了一遍，`fontSize = <字面量>.sp` 在 tokens 文件外是
  **126 处**（子代理最初给的 215 含了别的 `.sp` 语境，那个数我没采用）。
  分桶（`grep` 实测，取 `fontSize = N.sp` 这一条精确形态）：

  | 字面量 | 处数 | MIUIX 表里有这一格吗 | 处置 |
  |---|---|---|---|
  | 13.sp | 23 | 有，caption（唯一） | 可零差替换 |
  | 17.sp | 5 | 有，itemTitle（唯一） | 可零差替换 |
  | 16.sp | 6 | 有，但那一格叫 chevron（语义是「›」） | **不换**：值等而角色不对，换完表意就撒谎了 |
  | 14.sp | 13 | 有，两格同值（body / sectionTitle） | **不换**：选哪一格是语义判断，不是查找替换 |
  | 11.sp | 12 | 有，两格同值（overline / badge） | 同上 |
  | 12.sp | 32 | **没有**（官方集合无 12） | 不换。09 那份文档把 overline 从 12 判到 11，这 32 处要跟着降到 11 是**一次 −1sp 的观感改动**，必须逐页真机对照，不并进本轮 |
  | 10/15/18.sp | 12/11/5 | 没有 | 同上，要逐处判角色 |
  | 26/42/46/8/9.sp | 5 | 没有 | 特殊展示字号，本就不该进字阶表 |

### E.2 已做：把两份真相接成一份

新增 `ui/tokens/MiuixTextStyles.kt` —— `miuixTextStylesOf(t)` 把我们这张表的 14 个角色
映射到 miuix 的 `TextStyles`，`VeneraTheme` 那处以 `textStyles = remember { miuixTextStylesOf(MiuixTypography) }` 接进 `MiuixTheme`。

- **这是一次接线，不是一次改观感**：映射后的每一格数值都等于库自带默认
  （main 17 / body1 16 / body2 14 / footnote1 13 / footnote2 11 / subtitle 14+Bold /
  title1 32 / title2 24 / title3 20 / headline1 17 / headline2 16 / button 17 /
  paragraph 17+1.2em），出处是 gradle 缓存里 `miuix-ui-android-0.9.4-rc01-sources.jar`
  的 `theme/TextStyles.kt`（读到的，不是记忆）。
- **唯一没有对应格的一档**：库的 `title4` = 18sp，我们表里没有 18 这格。
  这里 `defaultTextStyles().title4` 原样沿用库默认，**不造数也不压成 16/20** —— 那是观感改动不是统一。
  写这颗函数时被编译器拦下一次：`MiuixTheme.textStyles` 的取值是 `@Composable` 的，
  用它会把纯映射函数拖成组合函数，所以走 `defaultTextStyles()`。
- `paragraph` 的 `lineHeight = 1.2f.em` 照抄库值（它是库里唯一带行高的角色，我们的表只管字号）。

### E.3 没做的那半，以及为什么不做

**126 处字面量的清扫本轮没跑。** 判据不是偷懒：按 E.1 那张表，只有 28 处（13/17 两桶）
是"默认外观档下零视觉差"的可换集；其余要么撞两个同值角色（14/11）、要么根本不在官方集合里
（12/10/15/18 —— 光 12.sp 就 32 处，跟 overline 走就是全站 −1sp）。
一次跨 30 多个文件、我**无法在真机上逐页对照**的批量换字，正是这个仓反复吃过回头的"顺手改"。
所以本轮交付的是：**同一份表已经接进 miuix**（这才是"统一"的机制），
加一张可执行的账（上面那张分桶表 + 每桶的判据），下一批按桶走、逐桶真机对照。

---

## 批 H：pixez 的 hero 转场 + 卡片长按（2）

### H.1 pixez 那轮到底做了什么（读 commit 与代码，不是猜）

真实时间线（按 parent 链，列表序会骗人）：`b658ec93`(09-30 基线) → `f49367c9`(10-01) →
`c98e0257` → `a603ddee` → `32be4be2` → **`d5482c25`（整轮 revert，正文没写原因）** →
`3a15df62` → `de346e07` → `45d3a6ca` → `336e8448` → `cdc6961d` → 发布。
重做时把那一捆（+1011/−66 的捆绑提交）**拆成两笔可独立验收的**：只搬布局、只搬预测性返回。
真正的病灶在 `45d3a6ca` 才修掉：第一版把"系统只派发进度事件没有 `onBackStarted`"当兜底，
于是 HyperOS 乱序补发的进度事件会**凭空启动一次假手势**，与常规转场抢同一份 movableContent
—— 症状是"首次返回误变侧滑样式、双层重影、返回后列表一片空白"。修法是加一位 `isBackConfirmed` 门闩。
`a603ddee`/`32be4be2` 那两条（转场期全幅对齐、收缩窗口只露作品本体）**被 revert 之后从未重做**，
今天的 pixez 代码里没有它们。

**它的 hero 不是共享元素、也不是截图**：全仓 `SharedTransitionLayout` / `sharedElement` / `PixelCopy`
零命中。做法是单 Activity 内 Decompose 栈 + 自写 `StackAnimator`，一个 `expansion: Float`
同时驱动两层；卡片把自己的矩形与圆角登记进 `SharedBoundsRegistry`，动画器每帧算
`baseScale = maxOf(liveScaleX, liveScaleY)` → 等比放大 + **双向比例裁切** + 圆角逆补偿。
"各尺寸统一"**没有尺寸档位表**，靠的就是这条 maxOf + 裁切把不同构吸收掉。

### H.2 我们能搬什么、不能搬什么（逐条判过）

| pixez 的东西 | 判定 | 依据 |
|---|---|---|
| `maxOf(scaleX, scaleY)` + 居中裁切 + 圆角逆补偿这套**几何** | **已经有，等价** | 我们的 `gallery/domain/GalleryFlyGeometry.kt:41-61` `coverSourceRect` 取的就是 `maxOf(dstW/imgW, dstH/imgH)` + 居中裁，且比例一致时逐像素等于整张（有用例钉）。**不搬不代表没查** —— 这条是本轮实测出来的结论 |
| 锚点表：**零尺寸上报只忽略、不注销** | **搬了** | 见批 G 的 `GalleryFlyIn.noteCardBounds`。pixez 在 `45d3a6ca` 里写明了它踩过的反例正是"据最后一次噪声回调注销锚点" |
| 整页一个 `expansion` 同相驱动两层 | **搬不过来** | 它要求列表与详情在**同一窗口同一棵 Decompose 栈**里；我们画廊大图页是独立 Activity（透明窗 blur-behind），跨窗口拿不到 movableContent 与对面的 graphicsLayer。这条是 2026-10-01 那轮已定案的方向（`gallery-hero-transition-and-artist-profile-2026-10-01.md` §十三），本批**不重开** |
| 预测性返回那套事件门闩 | **不适用** | 它靠 Decompose 自管 BackEvent 双层渲染；我们跨 Activity 的预览由系统在 `targetSdk≥36` 施加，已被 `makeCustomAnimation(0,0)` + 自绘飞回取代（同一文档 §十五，用户拍板过） |
| `WindowBottomSheet` 长按动作表这个**形态** | **搬了，但容器换成 m3 `ModalBottomSheet`** | 理由见 H.3 |

### H.3 已做：长按一张卡 = 五项动作表

新增 `gallery/ui/GalleryCardActionSheet.kt`（查看详情 / 收藏 / 复制图片地址 / 分享图片 / 查看画师），
挂在**共用件 `GalleryCardsGrid`** 上，所以搜索墙、猜你喜欢主墙、日榜二级页、画师介绍页的热门作品
四处一起有 —— 只给一面墙加就是假开关。收藏页自己传了 `onLongPress`（多选与区间反选），
**调用方传的优先**。

- **读数要如实说**：用户给的这五项里，pixez 那张表**只有**「复制图片 / 分享 / 复制链接」这一族，
  「查看详情」在它那儿是单击本身、「查看画师」走详情页头像、「收藏」在详情页心形 ——
  所以这五项是我们自己的动作集，照搬的只有"长按走底部动作表"这个形态。
- **容器选 m3 `ModalBottomSheet` 而不是 miuix `WindowBottomSheet`**：miuix 那一个是 `Dialog` +
  独立窗口，与本仓两条既有结论冲突（没有 miuix Scaffold 就不能用它的 Overlay 系；
  玻璃采不到独立 Dialog 窗口 ⇒ 弹层只能实底）。既然实底是必然，就与同族的「关于这张图」
  用同一个容器，不多养一套宿主。
- **`skipPartiallyExpanded = true`**：这张表统共五行，半展开会停在屏幕中间留一大片空
  （Reader 那三张短表都带这一参）。顺带把「关于这张图」缺这一参的事记在批 F.8 的表尾 —— 那张
  是长内容表，不带是对的，两者不是一回事。
- **这颗件不实现任何业务**：收藏走 `GalleryFavorites.toggle`、分享走新抽的
  `GalleryImageShare.shareGalleryPost`（从大图页整体搬出来，两处共用一份 ——
  那一段里有三条既有口径和一处"静默挑错重载、真机分享一次都没成功过"的坑，复制两份迟早漂）、
  查看画师走既有的 `artistProfileIntent`。空地址、空作者名这两种情况**要么出声要么不摆那一行**。

### H.4 验证与未验

已验证：`:app:compileDebugKotlin` EXIT=0；`:app:testDebugUnitTest` 全量 109 类 **834 颗 0 失败 0 错误**。
**未验**：动作表在真机上的观感（弹层实底、五行高度、震动反馈时机）、以及"打码卡长按也能出表"
这一条是否符合预期 —— 我的判断是符合（打码是墙的显示层，不是能力闸门，大图页本就允许打开），
但这需要用户点头。

---

## 批 F 补记 / 全轮收尾见文末「本轮总账」

B/C/D/E/F/G/H 的现状读数已由代码勘察钉住（文件行号在各批文档段落里补），
每批落地后回写「已改 / 已验证 / 未做」。

---

## 本轮总账（9 条逐条对账）

| 用户条目 | 批次 | 状态 | 一句话 |
|---|---|---|---|
| 1 探索页 tag 过多（要限制，全源看过）+ 每日榜首进分类区 | A | **已做** | 补回迁移丢掉的 `randomNumber`（全仓唯一一处 random 就是 nh 的 720→20）；榜逐档成入口、排「本源分类」首位不吃预算；大组另加 `SECTION_ITEM_CAP=20` 第二道闸。dynamic 分区（kavita/komga/lanraragi/manga_dex 恒 0 条）本轮**不做**，理由在 A.1 |
| 2 学 pixez hero 转场 + 长按卡片五项 + 长按 md3 动画 | H | **部分** | 长按五项已落地（挂共用件，四面墙一起有）；hero 的**几何我们本来就等价**（实测结论）；整页两层同相驱动与预测性返回**结构性搬不过来**（独立 Activity），且那是已定案方向，不重开。"长按 md3 动画"这半句我理解为**按压反馈要走各家自己的那套、不显式叠 ripple**（pixez 用的是 miuix `MiuixIndication` 整块压色，不是 md3 ripple；我们 chip 已是 `indication = null` 自持反馈，卡面走后端自己的）—— 若要的是别的，说一声 |
| 3 鬼畜现象（滚动中图片反复重建） | G | **已修两条自激环** | 每帧写新 `Rect` 实例导致每张可见卡每帧重组；`noteCardBounds` 被回收前的 0×0 噪声抹掉落点。`contentType` 全仓缺失、其余 key 稳定性问题**记账未做**（G.3 逐条有判据） |
| 4 tag 太长被截断 | B | **已做** | 组件默认仍单行（卡片高度由标签区驱动），「标签即内容」的四处放开两行 |
| 5 统一全平台画师头像 | D | **已做** | 三处收成一颗件、一个默认档（`artistAvatarSize`）、一套退路字号；各站档位实测已一致所以不动。首页退路"名下收藏图不过屏蔽那把"这条口子**如实留着**，理由在 D.3 |
| 6 统一 miuix 字体和字号 | E | **一半** | 字阶表已接进 `MiuixTheme`（两份真相合成一份，零视觉差）；126 处字面量的清扫**判为不该盲跑**，交付的是分桶账 + 判据（E.3） |
| 7 画廊搜索切走再切回回到顶部 | C | **已做（含一条挂账）** | 「已回顶凭据」搬出 `remember`；主墙补上绑内容的落点（存 `rememberSaveable`）。**切 Tab 时搜索那一屏整片消失**这条没治 —— 要做得落盘，方案在 C.3 |
| 8 关于这张图有时点不动 | F | **修了最强的一条** | 视频少一枚 HD 钮导致居中 dock 整体挪位（≈25dp）；另两条（缩放期 chrome 收起、`current==null` 那一瞬）判据与不修的理由都在 F.8 表里 |
| 9 关于这张图加屏蔽画师 | F | **已做** | 画师卡长按菜单：复制站方原名 + 屏蔽该画师，复用既有 TAG 规则链，不是新存储 |

**全轮验证口径**：`:app:compileDebugKotlin` 与 `:app:testDebugUnitTest`（109 类 834 颗）多次全绿；
新增用例 16 颗（批 A 的 11 + 批 C 的 5）。
**所有观感结论都待真机**——本环境按既定分工不代操作设备，需要用户跑的读数逐条写在 C.4 / D.3 / F.8 / G 末 / H.4。
提交切了 9 颗（A/B/C/D 各一颗；F 拆成「屏蔽画师」与「dock 挪位」两颗，因为两件事的判据不同；G/E/H 各一颗）。
逐颗是 `6f1db88` `ccb9a13` `2761c9f` `0d38d43` `be3b228` `2002e9f` `b6dea44` `b4fb84c` `283f231`，
冻结豁免记在 `FREEZE-STATEMENT.md` 的 2026-10-04 追加段。
