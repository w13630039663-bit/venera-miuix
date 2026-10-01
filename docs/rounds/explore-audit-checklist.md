# Explore 页面 —— 阶段 0~2 审计报告

> 分支：compose-migration ｜ Package：com.venera.compose ｜ 审计日期：2026-09-18
> **本报告为纯只读审计产物，未修改任何一行业务代码。**
> 基线 commit：4270487（探索页按源重构）。对比对象：4270487^ 的旧 ExploreScreen.kt / CategoriesScreen.kt。

---

## 0. 阶段 0：上下文确认 —— 文件入口清单

| 层级 | 文件 | 作用 |
|---|---|---|
| Route | feature/Navigation.kt L79 ExploreRoute、L345 CategoriesRoute(重定向)、L362 SourceSectionRoute | 导航入口 |
| 一级页 | feature/explore/UnifiedExploreScreen.kt（637 行） | 探索主页（源→探索方式→原生分类→内容） |
| 二级页 | feature/explore/SourceSectionScreen.kt（374 行） | 单源分类/Tag 下钻页 |
| 状态 | feature/explore/ExploreState.kt（44 行） | ExploreUiState，按源分别保存筛选态 |
| 纯逻辑 | feature/explore/ExplorePolicy.kt（69 行） | 列数/key/翻页合并/标题翻译 |
| 内容模型 | feature/explore/ExploreContent.kt（38 行） | Empty / Sections / ComicList |
| 能力建模 | source/explore/SourceExploration.kt（165 行） | SourceExplorationFactory，源能力→UI 模型的唯一构建入口 |
| 通用标签 | source/explore/UnifiedTags.kt（76 行） | 应用层辅助标签，明确不覆盖源原生 Tag |
| 单测 | app/src/test/.../ExplorePolicyTest.kt（67 行，5 用例） | 列数/身份/过期请求/标题翻译/翻页去重 |

### 导航行为
- ExploreRoute 与 CategoriesRoute 指向同一个 UnifiedExploreScreen（旧分类页已合并进探索页）。
- 下钻：UnifiedExploreScreen → SourceSectionRoute(sourceKey, sourceTitle, category, param, unifiedTag)。
- 无 ViewModel：两页都用 remember + LaunchedEffect 就地管理状态（与手册「不修改 ViewModel」一致）。

### 数据契约（source/model/ComicSourceModels.kt）
- ExplorePageData(title, type, sourceKey, sourceName, pageIndex)
- ExplorePagePart(title, comics, **viewMore: PageJumpTarget?**) —— viewMore 即「查看更多」入口
- CategoryData(title, key, **enableRankingPage**, parts, buttons, sourceKey)
- CategoryComicsOption(label, options, **notShowWhen**, **showWhen**) —— 含联动显隐
- CategoryComicsResult(comics, maxPage, next)

**ComicSource 接口探索相关方法（source/ComicSource.kt）**：getExplorePages L64、loadExplorePage L70、
getCategoryData L76、getCategoryComicsOptions L82、loadCategoryComics L88、loadCategoryRanking L99
（均有默认空实现 → 天然 Source-native）。

---

## 1. 阶段 1：旧版功能审计

### 1.1 旧版能力来源
- 旧 ExploreScreen.kt(530 行) + 旧 CategoriesScreen.kt(769 行)，于 4270487 被删除。
- 旧一级页：全量源 explore 页 → 顶部 Tab（每个 Tab=一个源的某个 explore 页，显示「源名·页名」）→ 分区流/多页列表流。

### 1.2 Feature Parity Checklist（旧版真实功能 vs 新版）

| 类别 | 旧版功能 | 新版状态 |
|---|---|---|
| 入口 | ExploreRoute；底部导航第3 tab | 保留 |
| 入口 | 旧 CategoriesRoute 独立分类页 | 合并进探索页（有意变更） |
| 一级筛选 | 顶部 Tab 跨源平铺（源名·页名） | 结构变更：先选源→再选探索方式 |
| 一级筛选 | 记住上次选中探索方式 | 保留(ExploreUiState.perSourceMode) |
| 二级能力 | 源原生分类矩阵 | 保留(NativeSectionBlock 原样不合并) |
| 二级能力 | 排行榜入口(enableRankingPage=true) | 保留(条件渲染) |
| 二级能力 | 通用标签快捷入口 | 新增(旧版无) |
| 展示 | 分区大标题+2列卡片网格 | 保留(但见 R1/R2) |
| 展示 | 分区「查看更多 ›」(viewMore) | **丢失 R3** |
| 展示 | 单列/双列切换 | **丢失 R2** |
| 状态 | 初始 Loading/空源提示+重新检测按钮 | 降级 R5 |
| 状态 | 加载失败+错误消息+重试按钮 | **一级页丢失 R4**；二级页有 |
| 状态 | 空内容提示 | 有(简陋) |
| 数据 | 分页「加载下一页」 | **一级页丢失 R1**；二级页有 |
| 数据 | 翻页同名分区续接去重 | 已实现但未被调用(见 R1) |
| 交互 | 顶部刷新 | 保留 |
| 交互 | 点击→详情 / 下钻返回 | 保留 |
| 交互 | 二级页 optionList 筛选 | 保留 |
| 系统 | 内容守卫过滤+R18 打码 | **一级页丢失 R6**；二级页有 |
| 系统 | BottomBar clearance | 一级页用 VeneraSpacing.bottomBarClearance |

### 1.3 风险 / 回归清单（本轮最重要产出）

| ID | 问题 | 证据 | 影响 |
|---|---|---|---|
| R1 | 一级页丢失分页 | 旧版 L328-362 hasMore+加载下一页+loadContentForTab(page+1)；新版只调 loadMode(...,1)，ComicList.hasMore 赋值(L335/349)却从未读取，mergeExploreParts 从未调用 | 多页列表流只能看第一页 |
| R2 | 丢失单列/双列切换 | 旧版 L194 ComicLayoutToggleButton+comicDisplayMode+ComicTileDetailed；explore 目录 grep 无上述符号。组件仍在 components/ComicTileLayout.kt L39/L174，FavoritesScreen.kt L105 在用 | 与全局布局偏好不一致 |
| R3 | 丢失「查看更多」 | 旧版 L395 if(part.viewMore!=null) Text("查看更多 ›")；模型 ExplorePagePart.viewMore 存在(L174)，新版 ExplorePartView(L532)完全不读 | 分区无法下钻到完整列表 |
| R4 | 一级页丢失错误重试 | 旧版 L270-308 Card+错误消息+重试 Button；新版 L292-294 只 Text(contentError) 无重试入口 | 失败后端体验退化 |
| R5 | 空源态降级 | 旧版 L233-268 Card+引导文案+重新检测按钮；新版 L189-197 仅一行 Text | 无引导无重试 |
| R6 | 一级页丢失内容守卫+R18 打码 | 旧版 L88 filterExploreParts、L137 LaunchedEffect(guardRules) 重放、L486-523 coverMaskStateFor+blur+角标；新版 UnifiedExploreScreen 无 ContentGuardManager（仅 SourceSectionScreen L115/145 有 filterComicModels） | 屏蔽规则在探索主页失效，R18 不打码 = 实际 Bug，优先级最高 |

### 1.4 架构层做对的部分（务必保持）
1. SourceExplorationFactory 是唯一构建入口，UI 不直读原始结构 → 新增源无需改 UI。
2. nativeSections 原样保留源命名，不做同义归并。
3. 能力缺失即隐藏，不伪造空按钮。
4. 通用标签层严格隔离（UnifiedTags.kt）：绝不覆盖源 Tag，点击只做关键词搜索转发。
5. ExploreUiState 按源分别存筛选态，切换源不互相污染。

---

## 2. 阶段 2：Capability 审计

### 2.1 能力归属矩阵

| 能力 | 归属 | UI 处理规则 | 依据 |
|---|---|---|---|
| Explore 页面列表 getExplorePages | Source-native 可选 | 无声明则该源不出现在探索页 | ComicSource.getExplorePages L64 默认空 |
| 探索方式(随机/最新/H24/D7…) | Source-native 可选 | 按源声明渲染，无则整行隐藏 | SourceExplorationFactory.buildModes L125 |
| 分类矩阵 getCategoryData.parts | Source-native 可选 | 原样渲染，空则整块隐藏 | L105 |
| 排行榜 | Source-native 可选 | 仅 enableRankingPage=true 才渲染 | L111/L138 |
| 分类下钻筛选项 CategoryComicsOption | Source-native 可选 | 仅渲染当前分类真实返回的组 | SourceSectionScreen L135/L199 |
| 选项联动显隐 | Source-native 可选 | 应读 notShowWhen/showWhen | 见 V2 |
| 通用标签 | 应用层辅助 | 命中才显示，绝不覆盖源 Tag | UnifiedTags.kt |
| 分页 | Source-native 可选 | 有 maxPage/hasMore 才显示翻页 | 见 R1 |

### 2.2 官方语义对齐（符合）
- 排序/标签/高级筛选/分类/all-source 均未硬编码，全部来自源声明 → 符合手册 §11。
- explorePageTitle(ExplorePolicy L45) 只翻译通用页面名，品牌名原样保留 → 正确。

### 2.3 已发现的伪造能力 / 契约破坏违规点

| ID | 问题 | 位置 | 说明 |
|---|---|---|---|
| V1 | 排行榜 option 写死 "day" | UnifiedExploreScreen.kt L332 mode.rankingOption ?: "day"；L143 构造也写死 rankingOption="day" | 排行榜内部选项(day/week/month)应由源声明，写死属轻微伪造，且无入口让用户选 week/month |
| V2 | notShowWhen/showWhen 未使用 | SourceSectionScreen.kt L199-246 | 协议要求按前置选项联动显隐，当前所有组无条件平铺 |
| V3 | CategoryData.buttons 未使用 | 模型 L194 buttons:List<CategoryButtonData> | 源声明的额外按钮入口被忽略，能力遗漏非伪造 |
| V4 | 网格列数写死 2，未用宽度自适应 | UnifiedExploreScreen.kt L549 chunked(2)；SourceSectionScreen.kt L277 同样 | 违反手册 §6.2。ExplorePolicy.exploreColumnCount() 有单测覆盖却从未调用(死代码) |
| V5 | 未使用 Venera 设计系统组件 | explore 目录 grep：VeneraCard/Cover/Chip/TagChip/SourceBadge/Shimmer/EmptyView 全 0 命中 | 直接 miuix Card+裸 AsyncImage+手写 RoundedCornerShape → 违反 §4.2 组件纪律 |
| V6 | 卡片不显示来源标记 | ExploreComicCard L567-623 / SectionComicCard L332-374 | 未用 VeneraSourceBadge；探索页是跨源入口，卡片无源名易混淆 |
| V7 | 空态/骨架未用 VeneraEmptyView/VeneraShimmer | L291 CircularProgressIndicator、L295 裸 Text | 与冻结组件体系不一致 |

---

## 3. 审计结论 & 下一步建议

### 3.1 结论
- 架构层(§1.4)优秀，完全符合「Source-native capability 优先」，这部分不要动。
- 实现层存在 6 条功能回归(R1~R6)，其中 R6(内容守卫失效)属手册定义的「实际 Bug」，应最优先修复；R1(分页)/R3(查看更多)/R4(重试)是明确 Feature Parity 缺口。
- 7 条违规点(V1~V7)，V5/V6/V7 是设计系统落地问题，V4 是手册明确要求的宽度自适应未落地。

### 3.2 下一步（供决策，本轮未执行）
按手册 §12 模板拆两个独立任务，各自 Scope+Stop Condition：
- 任务 A(修 Bug/补 Parity)：R6 内容守卫+R18 打码、R1 分页、R3 查看更多、R4/R5 错误与空态重试。只改 feature/explore/ 两个 Screen，不动 ViewModel/Source/Navigation。
- 任务 B(设计系统落地)：V5/V6/V7 接入 Venera 组件、V4 接入 exploreColumnCount、R2 接入 ComicLayoutToggleButton。需先出 Design System Mapping 表。

V1/V2/V3 建议单独开「Source Capability 专题」，不要在页面重构里顺手改（手册 §13：Source Health semantic 单独专题）。

### 3.3 待确认项
- nativeSections 的 type(fixed/random/dynamic) 未参与 UI 决策 —— 是否需差异化渲染？需产品确认。
- CategoryData.buttons(V3) 当前内置源是否真有返回值？需真机验证再决定实现。
- 一级页列数：是否跟随全局 comicDisplayMode 偏好，还是固定双列？

---

## 附：审计方法
- 旧版对比：git show 4270487^:app/src/main/java/com/venera/compose/feature/ExploreScreen.kt 与 CategoriesScreen.kt。
- 差异核实：对 feature/explore/ 全目录 grep 指定符号。
- 组件可用性核实：components/ComicTileLayout.kt L39/L174、components/VeneraEmptyView.kt、components/venera/* 均存在。
