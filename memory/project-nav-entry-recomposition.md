---
name: project-nav-entry-recomposition
description: 导航条目有两种状态丢失（被覆盖=组合销毁但条目 VM 活着；被 pop=切主 Tab，条目 VM 也被清、saveState 救不了只能落盘）；退场期重组会二次 pop、槽位载荷只能放叶子目的地、同屏形变也丢 remember
metadata:
  type: project
---

**事实（2026-09-20 真机撞到；2026-09-30 批次 O 补第二型）**：`NavHost` 的条目状态有两种，**别混**：

- **被下一页覆盖**（进详情再返回）：组合**销毁**、返回时从零重建 ⇒ `remember { mutableStateOf(...) }` 全丢；
  但条目**还在栈上**，所以 `rememberSaveable`、`rememberLazyListState` 和**条目作用域的 `viewModel()`** 都活得下来。
- **被 pop 掉**（切主 Tab：`gotoTab` = `popUpTo(startDestination){saveState=true}`）：
  `NavBackStackEntry` 类头 KDoc 明写 "ViewModels will be cleared"，而 VMStore 按条目随机 `id` 取
  ⇒ **条目作用域 VM 活不下来，`saveState`/`restoreState` 只救 `rememberSaveable` 那一族**。
  所以"提进 VM 就治好了"这一型**治不了切 Tab** —— 切 Tab 要留数据只能**落盘**（日榜 `GalleryFeedCache`
  与猜你喜欢 `GalleryForYouCache` 都是为此存在的）。

三个已确认的症状（第一型）：

1. 收藏页 `mode` 用普通 `remember` → 从本地收藏点进详情、返回却落在**网络收藏**（默认值）。
2. 探索页 / 分类页 把选中源、探索方式、已加载内容、分页、筛选项全放在 `remember` → 每次返回都**重拉源列表 + 重拉第 1 页**，并跳回**第一个源的第一个探索方式**（picacg 的「随机」）。
3. 共享元素返回时"闪一下"：落地那张卡所属的那一支压根还没被组合出来，飞行元素无处可落。

**Why:** 用户明确要求「所有二级界面都能回到返回前所在的页面，并且**不要自动刷新**」（2026-09-20）。这不是打磨，是这条产品要求决定了状态必须往上提。

**同一根因的第二族症状（2026-09-22）：退场动画期间的重组会把"空值自动退出"分支点亮，造成二次 pop、连带弹掉上一页。** 阅读器条目当时是 `val session = shell.pendingSession`（ViewModel 里的**普通 var**）+ `if (session == null) LaunchedEffect(Unit) { popBackStack() }`，而 `onBack` 先写 `pendingSession = null` 再 pop。pop 的退场动画期间条目**仍会被重组**，重组回读到 null → 翻进自动退出分支 → 那个 `LaunchedEffect(Unit)` 是**新进入组合的节点**，会再 pop 一次，把详情页一起弹掉，用户看到的是"返回直接跳回主界面"。

- **判别实验（用户跑的，一次就定性）**：同一个返回动作，**点左上角箭头**跳详情页、**用系统返回手势**正常 —— 两条路径唯一差别就是箭头那条会先写 null。以后遇到"返回多跳了一层/跳过了上一页"，先做这个 A/B，比读日志快得多（Compose 导航层的 pop 在 logcat 里根本看不见）。
- **修法上的一个坑**：只加"进来时有没有 session"的一次性守卫**不够** —— 若仍在 pop 前清 null，`session` 变 null 会让内容在退场动画里**先空成一屏**再滑走。正解是**会话只取一次**（`remember { shell.pendingSession }`）+ **不在 `onBack` 里清**（清理交给 `onDispose`），双弹和空白帧一起堵掉。
- 全仓同类写法原本只此一处 —— **2026-09-23 我又造了第二处并被真机抓到**（见下面第三族）。
  2026-09-25 之后阅读器那条已按「会话只取一次 + 不在 onBack 清」改掉，全仓当前无已知同类写法。

**第三族症状（2026-09-23）：「取用一次即清」的槽位式载荷只能放在叶子目的地。** 插图预览页照抄了 `pendingSession` 那套（shell 槽位 + `remember` 取一次 + `onDispose` 清空 + 取不到就 `popBackStack`）。它**下面还能压详情页**，从详情页返回时这条目的地重新进组合 → `remember` 重跑 → 槽位已被自己上一次退场清空 → 读到 null：既渲染不出内容（**整屏 UI 消失**），又自行弹一次（那次自 pop 正赶上返回动画进行中，`popBackStack()` 被吞，空壳就一直挂着，用户看到的是"要再按一次返回才回得来"）。
- **判据**：往某条目的地放载荷前先问「它下面还会不会被压页」。会 → 载荷必须**自带**（进路由参数 / 条目作用域 VM / 按 id 现读），不能靠一次性槽位。`ReaderSession` 那种含非序列化对象的会话没得选，所以它必须是叶子。
- **第一版修法引入了新崩溃（我犯的，真机冷启动即闪）**：把整条 `FavoriteImageItem` 标 `@Serializable` 塞进导航参数 → 栈在 `RouteSerializerKt.generateRoutePattern`：`could not find any NavType for argument item ... typeMap received was {}`。**type-safe 导航传自定义对象要 safeargs 插件生成 NavType，本仓库没装**；`@Serializable` 只保证能序列化，不保证导航层能解析。
- **最终修法**：路由只带 `itemId: Long`（内建类型），载荷放 shell VM 里 `id → item` 的表，**不在 onDispose 清、目的地每次重组都回读**。既避开崩溃又堵住"取一次 vs 重进组合"的竞态；只有进程被杀后恢复才回读不到，那时如实 pop。

**第四族症状（2026-09-25）：跨 Activity 的反向交接不能等 `onResume`。** 画廊大图页是**透明窗口**（blur-behind），压在它下面的 MainActivity 只是 `onPause`、组合**并没有停** —— 所以"回壳后消费一次性槽位"这种写法在透明窗上下不可靠（收到/收不到取决于窗口是否可见）。正解：槽位字段用**快照状态**（`mutableStateOf`），消费方 `LaunchedEffect(槽位)` 读它当 key —— 另一个 Activity 写它时快照失效立刻叫醒这边活着的组合，与生命周期时机无关。现成的例子见 `gallery/ui/GallerySearchHandoff.kt`（方向与 `GalleryFlyIn` 相反：二级递回一级）。
仍然必须遵守第三族的两条：**取用一次即清** + 消费方**只把它写进活下来的条目作用域 VM、不挂任何"取不到就 pop"的自毁分支** —— 否则就是同一次搜索被切回 Tab 再发一遍 / 退场期二次 pop。

**第五族症状（2026-09-29）：同一控件在"两种形态"的两条分支里各画一次，`remember` 一样会丢 —— 这次不是导航，是同屏形变。** 画廊搜索的「排行」芯片自己记 `menuOpen`，展开态画在 `FlowRow` 里、收成一条态画在 `Row` 里。点它 → 焦点离开输入框 → 键盘下落 → "键盘收起就收成一条"那道 effect 换支 → 芯片成了**另一个组合位置** = 新实例 → `menuOpen` 归零 → **下拉菜单闪一下就没了**（收条态点它完全正常，因为那条路径没有键盘）。
- **判据**：任何"点开一层浮层"的开关，如果宿主控件会随形变换分支，状态就必须提到**两条分支之上**（外层 composable 的 `remember`，或条目作用域 VM），不能记在控件自己身上。
- 症状形状值得记住：**"A 态能用、B 态不能用"不等于两套实现有差异** —— 这里两支调的是同一个 `RankingChip`，差别只在它被放在树的哪个位置。

**第五族症状（2026-09-29）：滚动位置丢，不一定是组合销毁 —— 跨 Activity 往返时组合根本没停。**
画廊的「点图 → 大图页 → 点标签 → 回搜索页」返回后被打回顶部，先按本条查了 `remember`，
**结论是错的**：`GalleryPostActivity` 是另一个 Activity（透明窗 blur-behind），MainActivity 上那一条
目的地的组合**活着**，`rememberLazyStaggeredGridState` 与条目作用域 VM 都还在。真凶是页面自己那句
`LaunchedEffect(searchGeneration) { scrollToItem(0) }` —— 它同时服务两种落点：真发了一笔新查询
（该回顶部）与 `popContext()` 弹回上一轮（**不该**回顶部），而两者共用同一个信号。
- **判据**：看到"返回后位置/滚动丢了"，先分清**这一页是不是另一个 Activity**；若是，
  组合销毁那条不适用，去查**谁在返回路径上无条件滚回顶部**（信号是"结果换了一轮"的，最容易顺带把落点也定死）。
- 修法：把"该落在第几张"当成上下文的一部分存下来（`GallerySearchContext.scrollIndex`），
  实时值由 `snapshotFlow { gridState.layoutInfo…firstOrNull()?.index }` 报回 ——
  **不要挂 `onScroll`**：程序滚动与条目增删它都不报，会把 0 记成"用户看到的深度"。

**How to apply:**- 看到"返回后回到默认/重置了/每次都刷新"，第一反应查该页状态是不是裸 `remember`；不要先去怀疑数据层或网络。
- 看到"返回多跳了一层"，先查有没有"`if (x == null) LaunchedEffect { popBackStack() }`"这种**自毁分支**，以及谁在 pop 之前把 x 写空。
- 修法优先级：跨往返要留的**选择态与已加载数据 → 条目作用域 ViewModel**（`val vm: XViewModel = viewModel()`，字段用 `var x by vm::x` 委托，无需额外 import）；只有一两个值的 → `rememberSaveable`。
- 光把状态搬进 ViewModel 还不够：`LaunchedEffect` 在组合重建后会**再跑一次**，必须加"这一轮已经加载过就跳过"的守卫（用 tick / `sourceKey#modeId` 之类的 loadedKey 记录），否则状态留住了、请求照发。手动刷新按钮要参与 key 以便强制重发。
- 已治：收藏页 `mode`、`ExploreViewModel`（探索页）、`SourceSectionViewModel`（分类二级页）。**未审**：首页推荐、下载中心、追更、本地书架 —— 这些页面若也有"返回被打回"的反馈，先按同一根因查。

相关：[[project-shared-element-transition]]、[[project-multi-ai-regression-triage]]、[[venera-ui-refactor-authoritative-docs]]
