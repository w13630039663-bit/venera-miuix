# Explore 页面 —— 阶段 3 Design System Mapping + 阶段 4 页面结构设计

> 配套文档：docs/rounds/explore-audit-checklist.md（阶段 0~2 审计）、docs/rounds/explore-capability-matrix.md（能力矩阵）
> 分支：compose-migration ｜ 设计日期：2026-09-18 ｜ **本轮只产出设计文档，不写业务代码**
> 纠偏依据：修 Bug 与换组件不可割裂为两轮 —— VeneraCover 内置打码槽位（天然解 R6），
> VeneraEmptyView 内置操作按钮与空态（天然解 R4/R5）。先 Mapping，下一轮一次到位落地。

---

## 阶段 3：Design System Mapping（组件 / Token 精确属性映射）

### 3.1 组件来源（已读源码确认签名）

| 组件 | 包路径 | 关键参数 |
|---|---|---|
| VeneraCard | components/venera/VeneraCard.kt | (modifier, onClick?, content: ColumnScope.() -> Unit) —— 只给 surface/shape/interaction，无业务语义 |
| VeneraCover | components/venera/VeneraCover.kt | (url, contentDescription?, modifier, mask: VeneraCoverMask, shimmerWhileLoading, content: BoxScope.() -> Unit) —— **mask 槽天然解 R6**；content 覆盖层天然挂 SourceBadge（解 V6） |
| VeneraSourceBadge | components/venera/VeneraSourceBadge.kt | BoxScope.VeneraSourceBadge(name, modifier, onClick?) —— 封面左上角固定深色底板 |
| VeneraTagChip | components/venera/VeneraTagChip.kt | (text, modifier, selected, onClick?, enabled?) —— 基于 VeneraChip，variant=Tag |
| VeneraChip | components/venera/VeneraChip.kt | (text, modifier, selected, onClick?, leadingIcon?, trailingIcon?, enabled?, variant) —— 唯一 Chip 实现 |
| VeneraEmptyView | components/VeneraEmptyView.kt | (message, modifier, title?, icon=Inbox, actionText?, onAction?, iconSize) —— **title/message 解 R5，actionText+onAction 解 R4** |
| VeneraCoverMask | components/venera/VeneraCover.kt | enum { Visible, Masked } |
| ComicLayoutToggleButton | components/ComicTileLayout.kt | (displayMode: String, onToggle: (String) -> Unit) —— brief=双列 / detailed=单列 |
| ComicTileDetailed | components/ComicTileLayout.kt | (title, coverUrl, subtitle, description, tags, rating, badge, onClick, coverContent?, coverMaskState, likesCount) |
| exploreColumnCount | feature/explore/ExplorePolicy.kt L13 | (availableWidthDp: Int): Int = (w/120).coerceAtLeast(3) —— **已有单测，当前 prod 未调用（V4）** |

### 3.2 漫画卡片 → VeneraCard + VeneraCover 映射

新 ExploreComicCard / SectionComicCard 统一替换为：

    VeneraCard(onClick = { openComic(...) }) {
        VeneraCover(
            url = comic.cover,
            contentDescription = comic.title,
            mask = maskState,                 // R6：由 ContentGuard 决定 Visible / Masked
            modifier = Modifier.fillMaxWidth(),
        ) {
            VeneraSourceBadge(name = sourceName)   // V6：覆盖层挂源名徽章
        }
        Spacer(Modifier.height(tokens.spacing.space2))
        Text(comic.title, ...)             // 复用 VeneraTokens.type.itemTitle / textPrimary
    }

| 旧实现（裸写） | 新映射 | 解决 |
|---|---|---|
| miuix Card + cornerRadius 手写 8.dp | VeneraCard（cornerRadius = tokens.shape.card） | V5 |
| 裸 AsyncImage + aspectRatio(0.72f) + RoundedCornerShape(8.dp) | VeneraCover（aspectRatio = tokens.spacing.coverAspectRatio，统一占位/加载/错误） | V5 |
| 手写 Modifier.blur(18.dp) + R18 角标 | VeneraCover(mask = Masked)，打码由组件统一 | **R6** |
| 卡片无来源标记 | VeneraSourceBadge（封面 content 槽） | **V6** |
| 硬编码字号 13.sp/11.sp/颜色 Color(0xFFE53935) | VeneraTokens.type.* / color.* | V7 |

### 3.3 封面打码（R6）精确契约

- 调用方用 ContentGuardManager.coverMaskStateFor(title, subTitle, tags, id) 取 VISIBLE/BLURRED/HIDDEN。
- 映射：BLURRED -> VeneraCoverMask.Masked；其余 -> VeneraCoverMask.Visible。
- 一级页 UnifiedExploreScreen 当前**完全没有 ContentGuardManager**（R6 根因）。设计上：加载完成后对 ExploreContent 的 comics 调 guardManager.filterComicModels（HIDDEN 整条剔除）+ 对每张卡算 maskState（BLURRED 打码）。
- 与二级页 SourceSectionScreen（已接 filterComicModels）保持一致，一级页补上即可，逻辑同源。

### 3.4 来源徽章（V6）精确契约

- VeneraSourceBadge 是 BoxScope 扩展，必须放在 VeneraCover 的 content 插槽内（非外层 Column）。
- 仅当 sourceName.isNotBlank() 时渲染（组件内部已 return，调用方仍应保证非空）。
- 探索页是跨源入口，卡片上源名=用户区分来源的唯一标识，必须显示。

### 3.5 错误态 / 空态 → VeneraEmptyView 映射（解 R4/R5）

| 状态 | 旧实现 | 新映射（VeneraEmptyView） | 解决 |
|---|---|---|---|
| 一级页加载失败 | 裸 Text(contentError) 无重试 | VeneraEmptyView(title=探索内容加载失败, message=contentError, actionText=重试, onAction={ reload }) | **R4** |
| 一级页空源 | 一行 Text | VeneraEmptyView(title=暂无可用漫画源, message=请在漫画源管理中启用支持探索的漫画源, actionText=重新检测, onAction={ refreshTick++ }) | **R5** |
| 一级页无内容 | 一行 Text | VeneraEmptyView(message=该探索方式暂无内容) | V7 |
| 二级页加载失败 | Card+重试 | VeneraEmptyView(title=加载失败, message=error, actionText=重试, onAction={ load(1) }) | V7 |
| 二级页空内容 | Text | VeneraEmptyView(message=该分类下暂无漫画) | V7 |
| 分区加载中 | CircularProgressIndicator | 整页初始加载用 CenterLoader；卡片级用 VeneraCover 内置 shimmer | V7 |

> 所有颜色/字号/间距由 VeneraEmptyView 内部取 Token，调用方**不再传任何颜色或字号**（消除 R4/R5 硬编码）。

### 3.6 标签与选项 → VeneraTagChip / VeneraChip 映射（解 V5 部分）

| UI 元素 | 映射到 |
|---|---|
| 通用标签块 UnifiedTagBlock 每项 | VeneraTagChip(text=tag.label, onClick={ ... })（variant=Tag，描边 primary） |
| 源原生分类分区 NativeSectionBlock 每项 | VeneraChip(text=entry.label, onClick={ ... })（Assist 变体，描边 outline） |
| 二级页 optionList 筛选项 | VeneraChip(text=label, selected = isSelected, onClick={ ... })（Selected=primaryContainer） |
| 探索方式行 modes | VeneraChip(text=mode.label, selected = isSelected, onClick={ ... }) |
| 源选择器 SourcePicker | VeneraChip(text=sourceName, selected = isSelected, onClick={ ... }) |

> 统一用 VeneraChip 单一实现，禁止页面内手写 Surface+Text 模拟 Chip（V5）。

### 3.7 Token 使用纪律（消除硬编码）

- 间距 -> VeneraTokens.spacing.*（cardContentPadding / badgeInset / space2/4/8/9/11）。
- 形状 -> VeneraTokens.shape.*（card/medium/extraSmall/extraLarge/small）。
- 字号 -> VeneraTokens.type.*（itemTitle/caption/badge）。
- 颜色 -> VeneraTokens.color.*（textPrimary/secondary/disabled、primaryContainer/onPrimaryContainer、surfaceVariant、outline/outlineVariant）。
- 封面比例 -> VeneraTokens.spacing.coverAspectRatio（不写死 0.72）。
- 禁止：页面直接写 Color(0xFFE53935)、0.72f、8.dp、13.sp 等字面值。

---

## 阶段 4：页面结构设计

### 4.1 一级页（UnifiedExploreScreen）单垂直 Viewport

**强制约束：全页仅一个 LazyColumn**（杜绝把大量卡片塞进单个 item 的普通 Column 伪网格）。

结构（每个区块 = 一个 item 或 items，卡片用 LazyVerticalGrid 作为单个 item 嵌入）：

    Column(statusBarsPadding) {
        Row(顶栏: 标题 + ComicLayoutToggleButton + 刷新)     // 固定不滚动
        LazyColumn(bottom = bottomBarClearance) {
            item  source-picker     -> SourcePicker (VeneraChip 行)
            item  modes             -> ExploreModeRow (VeneraChip 行)
            items  nativeSections   -> NativeSectionBlock (VeneraChip/FlowRow)
            item  ranking           -> 排行榜入口 (VeneraChip)
            item  unified-tags      -> UnifiedTagBlock (VeneraTagChip)
            item  content-header     -> SectionHeader(内容)
            when {
                isLoadingContent -> item CenterLoader
                error != null    -> item VeneraEmptyView(重试)     // R4
                content.isEmpty  -> item VeneraEmptyView
                Sections  -> items parts -> ExplorePartView
                ComicList  -> item Grid(loaded.comics)            // 见 4.4
            }
        }
    }

网格渲染（4.4）：ComicList 与 Sections 里的卡片，在 LazyColumn 内用 LazyVerticalGrid（columns = GridCells.Fixed(columnCount)）作为单个 item 嵌入；每张卡 = VeneraCard+VeneraCover。

### 4.2 二级页（SourceSectionScreen）单垂直 Viewport

- 顶部 Row（返回 + 标题 + 源名 + 刷新）固定。
- 错误/空态走 VeneraEmptyView（4.1 映射）。
- 内容 LazyColumn(bottom=90.dp) 内 itemsIndexed(rows) 渲染卡片行；改接列数 + ComicTileDetailed（4.5）。
- 分页上一页/下一页保留（二级页已正确）。

### 4.3 分区与「查看更多 ›」（R3）下钻契约

- ExplorePagePart.viewMore: PageJumpTarget? 字段已存在（模型 L174），旧版渲染「查看更多 ›」，新版 ExplorePartView 没读 -> **R3**。
- 设计：分区标题行右侧，若 part.viewMore != null，渲染 Text(查看更多 ›, primary) 可点击 -> onOpenNativeSection(NativeSectionArgs(... viewMore ...))。
- 下钻契约：viewMore 是 PageJumpTarget(page=category|search, attributes)，与现有 onOpenNativeSection 通道一致，无需新 Route。点击跳 SourceSectionRoute，复用二级页加载逻辑（已支持 unifiedTag!=null 搜索降级）。

### 4.4 宽度自适应与单/双列布局（解 V4 + R2）

- 列数：用 exploreColumnCount(availableWidthDp)（ExplorePolicy L13，已有单测）。availableWidthDp 取自 BoxWithConstraints { maxWidth } 或 LocalConfiguration.screenWidthDp 减左右 padding(16.dp)。旧写死 chunked(2) -> chunked(columnCount)，columnCount 由宽度决定（>=3）。
- 布局切换：顶栏接入 ComicLayoutToggleButton(displayMode, onToggle)，displayMode 来自 VeneraPreferences.comicDisplayMode（StateFlow），写回 setComicDisplayMode。
  - brief(双列) -> VeneraCard+VeneraCover 网格，列数 = exploreColumnCount。
  - detailed(单列) -> 复用 ComicTileDetailed（左封面+右标题/副标题/标签/描述），封面同样走 VeneraCover(mask)。
  - 与全局偏好一致（FavoritesScreen 已接 ComicLayoutToggleButton，探索页之前丢了这个 -> R2）。
- 注：ComicTileDetailed 当前用裸 Card+MiuixTheme，未用 VeneraCard/VeneraTokens。Batch 2 评估是否让 ComicTileDetailed 改接 VeneraCard（严格统一可提独立小改动，但不在本页 scope 强求；优先保证 explore 卡片用 VeneraCover 解决 R6）。

### 4.5 分页加载方案（解 R1，结合 hasMore + mergeExploreParts）

- ExploreContent.ComicList 已有 hasMore 字段（L335/L349 赋值），但当前从未被读取 -> **R1**。
- 触底触发：LazyColumn 末尾加 item { LoadMoreFooter(page, hasMore, isLoading, onLoadMore) }。
- onLoadMore 调 loadMode(sourceManager, src, mode, nextPage)，返回追加的 parts/comics。
- 用 ExplorePolicy.mergeExploreParts(old, new) 把同名分区续接去重（已有单测，当前 prod 未调用 -> R1 一并接线）。
- hasMore 判定：newParts.isNotEmpty() && mode.kind != EXPLORE_MULTI_PART（旧版 L105 逻辑），或 ComicList 的 maxPage 未尽。
- 状态：加载中显示小 CircularProgressIndicator；无更多隐藏 footer；失败 footer 显示「加载失败，点击重试」。
- 二级页分页已正确（maxPage + 上下页），一级页补齐即可。

### 4.6 内容守卫接入点（R6，全页统一）

- 一级页：LaunchedEffect(refreshTick/mode) 加载后，对 ExploreContent 的 comics 调 guardManager.filterComicModels（HIDDEN 剔除），对每张卡算 coverMaskStateFor(...) 得 VeneraCoverMask。
- LaunchedEffect(guardRules)：规则变化时对当前已加载内容重放过滤（与二级页、旧版探索页一致）。
- 二级页已正确，本页仅补齐一级页，逻辑复制同源不新增。

---

## 实施拆解规划（供下一轮执行，本轮不写代码）

### Batch 1：卡片与状态统一落地（一步到位解决 R6+R3+R4+R5 与 V5/V6/V7）
范围：仅 feature/explore/UnifiedExploreScreen.kt 与 SourceSectionScreen.kt 的卡片/状态渲染部分。
1. 引入 VeneraCard / VeneraCover / VeneraSourceBadge / VeneraEmptyView / VeneraChip / VeneraTagChip 与 VeneraTokens。
2. ExploreComicCard、SectionComicCard 重写为 VeneraCard+VeneraCover(挂 SourceBadge)，接 ContentGuard mask（R6/V6/V5）。
3. SourcePicker / ExploreModeRow / NativeSectionBlock / UnifiedTagBlock / 二级页 optionList 全部改用 VeneraChip / VeneraTagChip（V5）。
4. 一级页错误态/空源态/空内容态、二级页错误态/空内容态 全部改 VeneraEmptyView（R4/R5/V7）。
5. 分区标题右侧接 viewMore「查看更多 ›」下钻（R3）。
6. 一级页补 ContentGuardManager 过滤 + LaunchedEffect(guardRules) 重放（R6）。
约束：不动 ViewModel（本页无）/Source/Navigation/BottomBar；不建第二套组件。

### Batch 2：网格自适应、布局切换与分页流（解决 R1+R2+V4）
范围：同上两个文件 + 复用 ExplorePolicy（已存在，仅接线）。
1. 接入 exploreColumnCount(availableWidthDp) 替换写死 chunked(2)（V4）。
2. 顶栏接入 ComicLayoutToggleButton + VeneraPreferences.comicDisplayMode（R2）；detailed 复用 ComicTileDetailed。
3. 一级页分页：接线 hasMore + mergeExploreParts + 触底 LoadMoreFooter（R1）。
约束：同样不越红线；V1/V2/V3 不在此混入。

### 推迟项（按纠偏确认）
- V1（排行榜 option 写死 day）、V2（notShowWhen/showWhen 未用）、V3（CategoryData.buttons 未用）-> 归「Source Capability 专题」，不在 Explore 页面重构里顺手改。

---

## 停止条件
- 本轮产出 docs/rounds/explore-design-mapping.md 即停止。
- 等待确认设计方案后，再按 Batch 1 / Batch 2 启动代码实施（各自明确 Scope + Stop Condition）。
- 不自动进入其他页面，不顺手重构无关代码。
