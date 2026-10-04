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

---

## 批 B–H：待逐批回写

B/C/D/E/F/G/H 的现状读数已由代码勘察钉住（文件行号在各批文档段落里补），
每批落地后回写「已改 / 已验证 / 未做」。
