# 探索页 · 第三轮：explore 类型解析修复 + 源下拉大卡片

日期：2026-10-14（p3）
前置：`docs/rounds/explore-redesign-2026-10-14-p2.md`、`docs/explore-page-audit.md`
验证：`:app:compileDebugKotlin` ✅ → `:app:testDebugUnitTest` **1134 用例 / 0 失败** ✅ → `:app:assembleDebug` ✅

---

## 0. 用户报的两件事

1. 截图（nhentai）：默认进去就是「探索内容加载失败 · exploadMixed is not a function」，
   排行榜等分类标签一个都没选中。用户原话：
   > 这种类型默认不选中的源应该默认选中可以查看的排行榜之类的，而不是直接显示没有东西，
   > 这两个源都是选中排行榜后就有内容的了，你全部源都看看
2. 源下拉要改成参考图那种**大卡片**，点击后下拉框**和这个框一样宽**。

---

## 1. 根因：不是「默认选中」，是引擎不认两个 `type`

用户看到的「默认没内容」不是选中策略的问题 —— 引擎对 explore 页 `type` 只认
`singlePageWithMultiPart` 一种「多分区」形态，另外两个被当成「分页列表」去解析：

| `type` | 用它的源 | 旧行为 | 后果 |
|---|---|---|---|
| `multiPartPage` | **10 颗**：禁漫天堂 / 紳士漫畫 / MangaDex / 漫画柜 / 漫画人 / mh18 / 漫小肆 / 优酷漫画 / goda / h-comic | 走 `else` 分支当分页列表解析 | 分区对象 `{title, comics}` 里没有 `id` → 逐条解析成 null → **整页空白** |
| `mixed` | nhentai | 调 `exp.loadMixed($page)` | 源脚本里**根本没有这个方法** → 真机报 `exploadMixed is not a function` |

`exploadMixed` 那个名字的由来：JS 侧变量叫 `exp`，拼上属性名 `loadMixed`，引擎把
`exp` + `loadMixed` 连读就是这一串 —— 不是拼写错误，是**调了一个不存在的成员**。

所以「默认选排行榜就有内容」只是因为排行榜走的是另一条端点（`loadCategoryRanking`），
绕开了这个坏分支。修好类型解析后，**默认那一页本身就有内容，且比排行榜内容更多**
（nhentai：Popular Now + New Uploads 两段；禁漫天堂：多个分区）。

---

## 2. 改动清单

### 2.1 引擎取数：`source/js/JsComicSource.kt` · `loadExplorePage`

JS 侧把四个 `type` 归一到三个解析口径：

```
singlePageWithMultiPart -> type "multiPart",  await exp.load()
multiPartPage           -> type "multiPart",  await exp.load(page)
mixed                   -> type "mixed",      await exp.load(page)   ← 修掉 loadMixed
multiPageComicList      -> type "list",       load(page) / loadNext(cursor)
其他                     -> type "list",       load(page)
```

Kotlin 侧新增/改写解析：

- `"multiPart"`：`{分区名: Comic[]}`（老式）与 `[{title, comics}]`（multiPartPage）两种形状共用一支；
- `"mixed"`：`{data: [Comic[] | {title, comics}], maxPage}` —— 带标题的收成命名分区，
  裸 `Comic[]` 收成**空标题**分区（续接规则见 2.3）；
- `"list"`：维持原样。

### 2.2 类型 → Kind：`source/explore/SourceExploration.kt`

四个 `type` 收敛到三种 `Kind`：

| `type` | `Kind` |
|---|---|
| `singlePageWithMultiPart` / `multiPartPage` | `EXPLORE_MULTI_PART` |
| `mixed` | **`EXPLORE_MIXED`（新增）** |
| `multiPageComicList` / 未知 | `EXPLORE_PAGE` |

`EXPLORE_MIXED` 为什么不并进另外两档：

- 并进 `EXPLORE_MULTI_PART` → 丢掉翻页（那一档 `hasMore` 恒 false），nhentai 的
  New Uploads 就没法续页了；
- 并进 `EXPLORE_PAGE` → 第 1 页的两个分区标题被压平成一个网格。

配套三处（`UnifiedExploreScreen.kt`）：
- `showAsSections`：`EXPLORE_MIXED` 与 `EXPLORE_MULTI_PART` 同等对待（第 2 页起是单分区且
  标题为空，会被既有判式自动打回平铺）；
- `hasMore`：`EXPLORE_MIXED` 可翻页（`!= EXPLORE_MULTI_PART` 天然成立）；
- `fetchModeParts`：`EXPLORE_MIXED` → `loadExplorePage`。

### 2.3 分页续接：`feature/explore/ExplorePolicy.kt` · `mergeExploreParts`

新增规则：**新页分区标题为空时折进上一段**。nhentai 第 2 页起返回裸列表（解析出空标题），
不折的话每翻一页就多出一个空标题段 —— `PartHeader` 会把空标题兜底成「推荐」，
读起来像凭空多了一栏。既有「同名续接」规则不变，原单测不受影响。

### 2.4 源下拉大卡片：`UnifiedExploreScreen.kt` · `SourceDropdownField`

- 锚点从一行细条改成**两行卡片**：书本图标（`Icons.AutoMirrored.Outlined.MenuBook`）
  + 源名（`itemTitle` + `weightBold`）+「当前漫画源 · 点击切换」（`caption` / tertiary）
  + 箭头（`ExpandMore`）。圆角 `shape.large`、描边 `hairlineThin` / `outlineVariant`。
- **菜单宽度 = 锚点宽度**：`onSizeChanged` 记锚点实测宽，按密度折回 dp 喂给 `DropdownMenu`
  的 `modifier`；首帧未量到时退回内容自适应，避免弹出 0dp 空菜单。
- 菜单补上 `shape` / `border`，与锚点同一套视觉语言。

**令牌纪律**：本轮没有新增任何 token，全部吃既有档 ——
`shape.large`、`hairlineThin`、`chipHorizontalPadding`、`space1/6/9/10`、`type.itemTitle/caption`、
`color.surfaceContainerHigh/surface/outlineVariant/primary/textPrimary/textTertiary`、
`sheetActionHeight`。图标尺寸沿用本仓既有做法（`space9`/`space10`，与 `VeneraEmptyView`
用 `space9/space11` 作图标尺寸同源）。

---

## 3. 全部源体检（用户要求「你全部源都看看」）

按 explore 页 `type` 分组，34 颗内置源：

| `type` | 颗数 | 源 | 修复前默认页 | 修复后 |
|---|---|---|---|---|
| `singlePageWithMultiPart` | 14 | 包子 / 拷贝 / 拷贝(多账号) / Comick / 漫画栈 / Happy / 热门漫画 / ikmmh / Kavita / Komga / 漫哇吧 / mh1234 / 少年Jump+ / comic_walker | ✅ 正常 | ✅ 不变 |
| `multiPartPage` | 10 | **禁漫天堂 / 紳士漫畫 / MangaDex / 漫画柜 / 漫画人 / mh18 / 漫小肆 / 优酷漫画 / goda / h-comic** | ❌ 空白 | ✅ 分区渲染 |
| `multiPageComicList` | 9 | ehentai / gelbooru / hitomi / jcomic / komiic / lanraragi / mycomic / picacg / zaimanhua | ✅ 正常 | ✅ 不变 |
| `mixed` | 1 | **nhentai** | ❌ 报错 | ✅ 分区渲染 + 可翻页 |

结论：受影响的是 **11 颗源**（10 颗 multiPartPage + nhentai），其余 23 颗本来正常。
修好之后「默认页空白」这一类症状在内置源里清零，不需要「默认改选排行榜」这种兜底。

---

## 4. 本轮明确**没做**的（留给下一轮，避免一次改太多）

- **`viewMore` 仍未接线（R3）**：`ExplorePagePart.viewMore` 字段与 `PartHeader` 的
  「查看更多 ›」UI 都在，但引擎从不填这个字段。填它要处理两种形态
  （jm/wnacg 的字符串 `category:名称@参数`、goda/mh18/mxs/manga_dex 的对象
  `{page, attributes}`），且 MangaDex 的 `viewMore → search` 带**三段 options**，
  必须原样传否则拼出 `undefined` —— 单独一批做。
- **ehentai 的 `loadNext` 游标语义**：`multiPageComicList` 分支对 `loadNext` 型源传的是
  页码字符串，而源期望 next URL 游标；`search` 适配层已有 `nextTokenCache` 可照搬。
  这一条是审查里的 P0，但**未真机验证**，留给下一轮。
- **空态不给排行榜出路**：空态现在只给「换一种方式 / 换一颗源」。

---

## 5. 未验证项

- 真机未跑（MuMu 模拟器本轮未起）。以上结论来自源码逐源核对 + 单测，
  **默认页是否真能出内容需要装 APK 实测**（重点：nhentai、禁漫天堂、MangaDex 三颗）。
- 下拉菜单等宽的观感（尤其长源名如「拷贝漫画(多账号)」「少年ジャンプ＋」在菜单里的换行/截断）
  需真机看。
