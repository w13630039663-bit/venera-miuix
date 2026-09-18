# 页面冻结声明（Stage 9 Freeze）

> 冻结日期：2026-09-18 ｜ 冻结 commit：7e681cc
> 真机验收：探索页打码与分页 ✅ ｜ 源级内容守卫（搜索/收藏/历史/追更）✅ ｜ 搜索网格行级虚拟化性能修复 ✅

| 对象 | 状态 |
|---|---|
| UnifiedExploreScreen.kt | **FROZEN** |
| SourceSectionScreen.kt | **FROZEN** |
| SearchScreen.kt | **FROZEN** |
| ContentGuardManager.kt | **FROZEN** |
| HistoryScreen.kt | 🧊 **FROZEN**（2026-09-18 第二批） |
| FavoritesScreen.kt | 🧊 **FROZEN**（2026-09-18 第三批） |
| NetworkFavoritesScreen.kt | 🧊 **FROZEN**（2026-09-18 第三批） |

允许：修实际 Bug、修明确回归。
禁止：无明确需求的视觉重构、架构重构、顺手拆文件、顺手改其他页面。
冻结目的：防止「改视觉 → 顺手改 Host → 功能回归 → Crash → 再修」循环（手册第 9 节）。

## 冻结范围说明
- 探索闭环：阶段 0~9 全流程完成（审计/设计/实施/QA/归档），文档见 explore-audit-checklist.md、explore-capability-matrix.md、explore-design-mapping.md。
- 源级守卫判定链：用户规则 > 源级预设（source_content_warning.json，33 源）> 显式 R18 正则兜底，LRU 缓存 + 别名解析。
- 后续模块（ComicSource / History / Detail 等页面重构）按手册标准流程另行启动。
- 顶栏大标题折叠 + 毛玻璃效果：已确认采用「页内自治」架构，不在外壳挂载；待后续模块实施。

## 第二批冻结（2026-09-18，commit：见 git log chore(freeze) 第二条）

> 真机验收：历史提升主 Tab ✅ ｜ 设置收口顶栏齿轮 ✅ ｜ 首页清理今日推荐 ✅ ｜ 历史页右上角对齐/空态/卡片 Token 化/多选删除预测返回 ✅

- **HistoryScreen.kt**：Batch 1 主 Tab 化接线 + Batch 2 规范化重构（VeneraEmptyView / VeneraCard / Token 化 / AnimatedContent 工具条）全部完成并验收。
- **导航层（Navigation.kt / VeneraFloatingNavBar.kt）**：Tab 枚举顺序 HOME → HISTORY → FAVORITES → SEARCH → EXPLORE 为本轮信息架构决策结果；枚举顺序、路由映射与顶栏齿轮入口**不允许顺手变更**（改动需重新评审）。
- **信息架构决策记录**：历史 = 高频主 Tab；设置 = 低频操作收口顶栏齿轮（子页形态，外层返回 + 内部 PredictiveBackStack 双层返回）。
- HistoryViewModel / HistoryDao 数据层未冻结但未改动；后续如需动历史数据结构，须连带评审守卫别名解析链（sourceName 显示名 → sourceKey）。

## 第三批冻结（2026-09-18）

> 真机验收：网络收藏手风琴原地展开 ✅ ｜ 横向源药丸切源 + 登录态圆点 ✅ ｜ 多文件夹源自动进默认分组（多夹假空态修复）✅ ｜ 布局切换按钮合并进工具行 ✅ ｜ 本地收藏多选工具条上移（不再被悬浮底栏遮挡）✅ ｜ 底栏双震动修复 ✅

- **FavoritesScreen.kt**：本地收藏规范化完成（多选工具条迁顶部分段区、Segmented Control 模式切换、预测返回跟手淡出、Token 化）并验收冻结。
- **NetworkFavoritesScreen.kt**：网络收藏「三级整屏下钻 → 单开手风琴」信息架构升级完成（唯一 LazyColumn + 行级虚拟化、触底自动加载、长按移除二次确认、VeneraEmptyView/Token 化）并验收冻结；配套 NetworkFavoritesViewModel 的 expandSource/collapseSource 手风琴状态机一并冻结。
- **共享组件连带改动**：VeneraCard 增加 onLongClick（miuix Card 官方可点击重载，外层叠 combinedClickable 会被 squircle 裁剪吞掉长按——真机实测教训）；ComicTileDetailed 增加 onLongClick；VeneraLiquidGlassNavBar 宿主回写抑制标志修复双震动。
- 至此**底栏 5 大主 Tab 页面（首页 / 历史 / 收藏 / 搜索 / 探索）全部实现冻结闭环**。
