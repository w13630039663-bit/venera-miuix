# Explore 页面 —— Capability Matrix（Source 能力归属与 UI 处理规则）

配套文档：docs/rounds/explore-audit-checklist.md（阶段 0~2 完整审计）
分支：compose-migration ｜ 审计日期：2026-09-18 ｜ 纯只读审计

本矩阵用于后续 Explore UI 实施（阶段 3~5）：明确「每个能力该不该渲染、怎么渲染」。

---

## A. 能力归属总表（核心原则：不伪造源没有的能力）

| # | 能力 | 归属 | 是否写死 | UI 处理规则 | 证据 |
|---|---|---|---|---|---|
| 1 | 探索页列表 getExplorePages | Source-native 可选 | 否 | 源未声明则不出现在探索页 | ComicSource.getExplorePages L64 默认空 |
| 2 | 探索方式 | Source-native 可选 | 否 | 按源声明逐个渲染，无则整行隐藏 | SourceExplorationFactory.buildModes L125 |
| 3 | 分类矩阵 getCategoryData.parts | Source-native 可选 | 否 | 原样保留源命名，空则整块隐藏 | L105 |
| 4 | 排行榜 enableRankingPage | Source-native 可选 | 否 | 仅 true 才渲染入口 | L111 / L138 |
| 5 | 分类下钻筛选项 CategoryComicsOption | Source-native 可选 | 否 | 仅渲染当前分类真实返回的组 | SourceSectionScreen L135/L199 |
| 6 | 选项联动显隐 notShowWhen/showWhen | Source-native 可选 | 否 | 应读，按前置选项显隐（当前未实现 V2） | 模型 L217-218 |
| 7 | 额外按钮 CategoryData.buttons | Source-native 可选 | 否 | 当前未使用（V3 能力遗漏） | 模型 L194 |
| 8 | 通用标签 UnifiedTag | 应用层辅助 | 否 | 命中才显示，绝不覆盖源 Tag | UnifiedTags.kt |
| 9 | 分页 maxPage/hasMore | Source-native 可选 | 否 | 有才显示翻页（一级页 R1 未接） | 模型 L221-224 |

---

## B. 源能力对比表（内置源）

结论基于 source/explore/SourceExploration.kt 与各源实现读取；具体每个源的 explore 字段由 JS 脚本/原生实现运行时声明，以真机返回为准。以下为接口层能力边界。

| 能力 | MangaDex | CopyManga | Baozi | JS 源(通用) |
|---|---|---|---|---|
| getExplorePages | 是(按源脚本) | 是 | 是 | 取决于脚本 |
| 分类矩阵 getCategoryData | 是 | 是 | 是 | 取决于脚本 |
| 排行榜 enableRankingPage | 按源 | 按源 | 按源 | 按脚本 |
| 分类下钻 optionList | 按源 | 按源 | 按源 | 按脚本 |
| 排序/语言/状态等高级筛选 | 来自 optionList | 来自 optionList | 来自 optionList | 来自 optionList |

重要：排序项(day/week/month/人气/更新)、分类树、标签均为源脚本运行时决定，UI 必须源返回什么渲染什么，禁止强造排序项、翻译合并源 Tag、假造 Year/Language/Pages 筛选、假设统一 Genre 树。

---

## C. UI 渲染决策规则（实施阶段直接照用）

1. 探索方式行：遍历 src.modes，空则整行不渲染（已实现）。
2. 原生分类 block：遍历 src.nativeSections，空则不渲染（已实现）；分区名/项 label 原样显示。
3. 排行榜入口：仅 src.hasRanking（=enableRankingPage）为 true 时渲染（已实现，但 option 写死 day → V1 待修）。
4. 通用标签 block：仅 unifiedTagsFor(src) 命中时渲染（已实现）。
5. 下钻 optionList：仅 getCategoryComicsOptions 返回的组渲染（已实现，但联动显隐 V2 未做）。
6. 分页：二级页已实现翻页；一级页 ComicList 有 hasMore 字段但未读（R1 待修）。
7. 内容守卫：二级页已实现 filterComicModels；一级页完全缺失（R6 待修，优先级最高）。

---

## D. 死代码 / 未接线清单（实施时顺手清理或接线）

| 符号 | 位置 | 现状 |
|---|---|---|
| ExplorePolicy.exploreColumnCount | L13 | 有单测，prod 未调用 → V4 应接线 |
| ExplorePolicy.mergeExploreParts | L30 | 有单测，prod 未调用 → R1 应接线 |
| ExplorePolicy.reconcileExploreSelection | L19 | 有单测，prod 未调用 |
| ExplorePolicy.exploreKey / explorePageTitle | L16/L45 | 有单测，prod 未调用 |
| ExplorePolicy.ExploreRequestGate | L23 | 有单测，prod 未调用 |
| ExplorePagePart.viewMore | 模型 L174 | 有字段，UI 未读 → R3 待修 |
| CategoryComicsOption.notShowWhen/showWhen | 模型 L217-218 | 有字段，UI 未读 → V2 待修 |
| CategoryData.buttons | 模型 L194 | 有字段，UI 未读 → V3 待修 |

---

## E. 红线（实施阶段禁止）

- 不改 ViewModel/Data Model/Source/JS/Network/Reader/Navigation/BottomBar（本页无 VM，注意 Source 与 SourceExplorationFactory 不动）。
- 不建第二套 Component System；不用 Material3 完整 UI 组件当业务组件（V5 待在页面层修，仍用 miuix 即可）。
- 不伪造 Source 没有的能力（V1 写死 day 是轻微违规，应改为读源声明）。
- 不顺手重构其他页面，不改已稳定 Dialog/Sheet Host。
- 源无原生体系时，通用标签只作兜底，绝不污染原生分类（UnifiedTags.kt 已约束）。
