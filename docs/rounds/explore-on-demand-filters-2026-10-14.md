# 探索页改版 · 折叠态与按需展开（2026-10-14）

> 本轮总结：探索页首屏 chrome 从 866dp 压到 181dp，752dp 视口里第一次能进双列封面。
> 结构稿先行（闸门 A），用户点头后才动代码（闸门 B）。

## 1. 病因（改版前的现量读数）

主文件 `feature/explore/UnifiedExploreScreen.kt`（HEAD，单棵 LazyColumn）从上到下 9 段：

| 位置 | 块 | 读数 |
|---|---|---|
| 首 | `SourcePicker` | 源药丸 LazyRow，无渐隐 ⇒ 溢出无证据；`enabled = !isLoadingSources` 是假开关 |
| 2 | `SectionHeader("探索方式")` + `ExploreModeRow` | 一枚 25dp 的纯标题行 |
| 3 | `UnifiedTagBlock` | 上方压一行 21dp 提示文字 |
| 4 | `FilterActiveBar` | 筛选生效时**独占一行** |
| 5 | `SectionHeader("本源分类")` | 又一行纯标题 |
| 6-7 | 榜档组 + N 个分类组 | 每组再带组名标题 |
| 8 | `SectionMoreButton` | 又一行的出口 |
| 9 | `SectionHeader("内容")` | 再一行标题 |

禁漫天堂这类内容丰富的源：**首屏 chrome 866dp**，752dp 视口里**一张封面都露不出来**。
其中约 **190dp 完全不含任何可点之物**（4–5 行标题）。

MangaDex 那类空源：242dp chrome + 一行光杆标题 + 空态，读起来像"没加载完"。

**普查**（`_qa/explore-redesign-2026-10-14/census-sources.cjs`，纯 stdout 可复跑）：

- 随包 34 颗源，20 颗声明了探索页；另外 14 颗被 `ComicSourceManager.kt` 的
  `filter { !it.isEmpty }` 挡在门外（`SourceExploration.isEmpty`）。**这条闸门不许动。**
- 静态读数"无分类、无榜、不命中通用标签"只有 **3 颗**：Lanraragi / MangaDex / 优酷漫画。
  其中 2 颗是**运行时才算得出分类** ⇒ 静态普查分不出"真空"和"运行时算/网络失败"，
  隐藏源会连带吃掉能正常出内容的探索页。结论：**不隐藏任何源。**
- mode 标题与源名同名（只差空格/大小写）有 **6 颗**：Gelbooru / 禁漫天堂 / Komiic /
  Lanraragi / MangaDex / 紳士漫畫 ⇒ 这一行的第二枚药丸与源名重名，是"源模式两处各说一半"的成因。

## 2. 目标结构

**折叠态**（首屏，从上到下）：

1. **源下拉格**：官方 M3 `ExposedDropdownMenuBox`。锚点 `PrimaryNotEditable`，
   菜单显式传 `containerColor`（`MenuDefaults.containerColor` 是半透明 surface，会跟壁纸取色对不上）；
   锚点格吃本仓描边/圆角 token。**不引第三方依赖**（miuix 无对应件，`ListPopup` 锚定不兼容）。
2. **模式药丸行**：恒一行横滑 + 尾渐隐。新增判据 `exploreModeRowVisible(modes) = modes.size > 1`；
   去掉 `SectionHeader("探索方式")`。模式名交给顶栏副标题。
3. **动作行**：两枚 `VeneraChip`（`selected = 展开中`，尾部箭头）+ 右对齐状态槽。
   - 「筛选 · N」：`N = unifiedTagsFor(src).available.size`，**N == 0 不渲染**。
   - 「分类 · N」：`N = 分类入口总数含榜档`，**N == 0 不渲染**。
   - 右槽念生效中的筛选（含「清除」）—— `FilterActiveBar` 整行撤掉。
4. **顶栏副标题** = `源名 · 模式名`（真状态，`resolveMode` 取当前生效项）。
   配 `contentPadding.top` 的副标题地板补偿 `topBarSubtitleExtra = 24dp`
   （出处：miuix `body2` 只声明 `fontSize = 14.sp`、无 lineHeight ⇒ 平台默认行高约 1.2，
   加 `SubtitleBottomPadding 8dp − LargeTitleBottomPadding 4dp` 的 +4dp，取 24dp 含 3dp 余量）。
5. **内容头三条判据逐字不动** + 封面网格不动。

**展开态**：点「筛选」在动作行**下面**就地撑开 `FlowRow`（5 枚通用标签）；
点「分类」同一位置撑开榜档组 + 分类组，沿用 `SECTION_ENTRY_BUDGET = 8` /
`SECTION_ITEM_CAP = 20` 两道闸与区内「更多分类」。分类入口照旧跳 `SourceSectionRoute`。
**不加「确定」那一步，不加动画**。切源/切模式照旧清 `unifiedFilter`。

**模型估算（闸门 A 出的稿里量到的）**：

| 状态 | chrome dp |
|---|---|
| 折叠态 · 禁漫天堂 | **181** |
| 折叠态 · MangaDex（1 个 mode） | **138** |
| 展开态 · 筛选 | 224 |
| 展开态 · 分类 | 353 |

对现状 866dp。方案书原先估的 ≈310dp 偏保守（把展开态余量也算进了折叠态）。
**这是模型估，落地后必须真量一次才算数。**

## 3. 逐文件改动

| 文件 | 动作 |
|---|---|
| `feature/explore/UnifiedExploreScreen.kt` | 删 `SourcePicker` 调用与实现 → 新增 `SourceDropdownField`；`ExploreModeRow` 去节头 + `size<=1` 不渲染；`UnifiedTagBlock` 删提示文字、整块受 `filterExpanded && hasFilters` 门控；`FilterActiveBar` 删 → 新增 `ExploreActionRow`；`SectionHeader("本源分类")` 换成「分类 · N」按钮、整块受 `categoriesExpanded` 门控；`:495-499` 内容头三条判据逐字保住；顶栏加 `subtitle` 与其地板补偿；两条 manager 取用点逐字不动 |
| `feature/explore/ExploreViewModel.kt` | 新增 `filterExpanded` / `categoriesExpanded` 两枚 `mutableStateOf(false)`；`sectionsExpanded` 语义收窄为"区内超预算的组也摆出来" |
| `feature/explore/ExplorePolicy.kt` | 新增 `exploreModeRowVisible` / `filterEntryCount` / `categoryEntryCount` 三颗无 Compose 依赖纯函数 + `exploreFoldedChromeDp`（模型账） |
| `feature/Navigation.kt` | 删死条目 `CategoriesRoute`（定义/注释/`tabIndex()`/`currentTab`/注册块共 5 处；`navigate(CategoriesRoute)` 调用点全仓 0，测试零引用） |
| `ui/tokens/Spacing.kt` | 新增 `topBarSubtitleExtra = 24.dp`（带出处头注） |
| `feature/UiAuditBatch4Test.kt` | 第 ⑤ 条**改判**；渐隐成对表**不**收 `UnifiedExploreScreen.kt`（源选择器换成下拉格后本屏已无横向滚动，硬加一行就是自己喂饱自己 —— 一度加过，跑红后撤回） |
| `feature/ExplorePolicyTest.kt` | 三颗纯函数 + 模型账配 5 条用例 |
| `feature/UiAuditBatch7Test.kt`（新） | 4 条源码扫描判据 |
| `FREEZE-STATEMENT.md` | 追加本轮观感豁免一节 |
| `docs/rounds/explore-on-demand-filters-2026-10-14.md`（本文件） | 方案 + 读数 + 普查 caveats + 被否项清单 |

## 4. 判据与豁免

`UiAuditBatch4Test` 的 `探索页空区块与套层标题一起撤`（四条逐字符串）：

- ① `assertFalse(… contains("SectionHeader(\"快捷筛选\")"))` → **保留**。
- ② `nativeSections.orEmpty().filter { it.items.isNotEmpty() }` → **锚点逐字保住**。
- ③ `src.rankingSection?.takeIf { it.items.isNotEmpty() }?.let` → **保住**（挪进展开区里也算保住）。
- ④ `if (!isLoadingContent && contentError == null && !content.isEmpty) {` → **保住**。
- ⑤ `startsWith("if (visibleSections.isNotEmpty() || …")` → **改判**：节头换成按钮后句子形状变了。
  改钉"分类按钮的可见性判式同时看 `visibleSections` 与榜档非空"，精神不变。

新增 `UiAuditBatch7Test` 四条（源码扫描，编译器看不见的东西归它们管）：

1. **假开关不许回来**：两枚动作按钮的渲染被计数判式门控，判式认**整串表达式**
   （`if (filterCount > 0) {` / `if (categoryCount > 0) {`），不认标识符。
2. **副标题地板补偿项**必须在 `contentPadding.top` 的相加链里，且吃 token。
3. **`subtitle =` 只能是 `源名 · 模式名` 那颗真状态表达式**，不许退成固定装饰文案。
4. **`ExploreModeRow` 里不许再出现 `SectionHeader("探索方式")`**，
   且 `exploreModeRowVisible` 判式在 `ExplorePolicy.kt` 里。

一行都不许动的既有尺子：

- `data/api/BusinessApiBaseline.kt:107-108`（及 `:171/:197/:222` 三张表）——
  两条 manager 取用点是 `FROZEN_SCREEN` 档，逐字不动、不迁、不换成注入。
- `architecture/ViewModelAssemblyGuardTest.kt`（探索页在冻结名单内，全仓 `viewModel()` 站点 17 处）
  ⇒ **不给探索页新增 VM、不改注入方式**。
- `ui/tokens/TopBarFloorGuardTest.kt` —— `topBarFloor == 104.dp` 不许改值；
  每屏自加附加项的形状有先例（`SourceSectionScreen.kt` 的 `topBarFloor + chipsHeight`）。
- `ui/tokens/TokenBudgetFrozenTest.kt` —— 裸 dp/sp/Color(0x…) 只许减不许增。
- `UiAuditBatch6Test`、大屏那套列数判据：只许加不许减，Compact 档直接 return 原值。

## 5. 明确不做

- 不动 `desktop/**`；不动搜索页与网络收藏的源 chip 行（分叉已知，用户拍板只改探索页）。
- 不做「确定」按钮、连载中/完结、「全部源」、面包屑；不把分类做成独立二级页；
  不用弹层面板装筛选。
- **不隐藏任何源**；不动 `getSourceExplorations()` 的 `!it.isEmpty` 闸门。
- 不给 `UnifiedTagAvailability.isFallbackOnly` 那条恒假分支动手术（只记读数，见第 1 节）。
- 不引第三方依赖；不改 `topBarFloor` 值；不给探索页新增 ViewModel。
- 不 commit、不 push（除非用户在当轮明说）。

## 6. 验证与留待

- 编译：`:app:compileDebugKotlin` ✅
- 单测：`:app:testDebugUnitTest` **1122 颗 / 141 类 / 0 失败** ✅（基线 1112/140，只涨不跌）
- 组装：`:app:assembleDebug` ✅ —— universal 102,829,732 B（98.06 MiB）、arm64-v8a 102,779,659 B、
  x86_64 102,780,320 B、armeabi-v7a 102,776,817 B
- 两笔扰动都按方案第 9 节预期回红后还原：
  ① `subtitleExtraHeight` 从 `contentPadding.top` 相加链拆掉 ⇒ 红「副标题地板补偿项在相加链里」；
  ② `if (categoryCount > 0)` 改恒真 ⇒ 红「分类按钮的渲染被 categoryCount 计数判式门控」
     （原话「没有入口还摆一枚按钮就是假开关」）。还原后 `grep PERTURB` 零残留，全量回到 1122/0。
- **观感读数未经我手**：本轮未连设备（MuMu 走 `adb connect 127.0.0.1:16384`，2026-10-14 现量拒连），
  折起的 chrome 181dp 是模型估。落地后需按 `measure-bands.ps1` 复量一次并与 181dp 对账，
  另需确认副标题地板补偿 24dp 在真机上是否多留了白、以及下拉菜单与壁纸取色的实底对不对得上。
- `app/` 侧没有 `runComposeUiTest` 可用（`desktop/src/test` 才有），所以不声称"语义树验过了"。
