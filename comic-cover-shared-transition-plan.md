# 收藏卡片 → 详情页：封面共享元素转场 —— 可行性研究与动画方案

> 日期：2026-09-19 ｜ 分支：compose-migration ｜ 状态：**仅方案，未动代码**
> 结论先行：**技术上可行，脚手架已就位 80%；但有一个决定性前提没满足 —— 两端封面几乎等大、甚至源比目标更大，纯封面飞行没有 hero 感。** 另外要先更正我上一轮的口头判断。

## 0. 更正一处我上轮说错的话

上轮我说「先只做本地收藏版，零保护域改动、零冻结页豁免」。**后半句是错的**：`FREEZE-STATEMENT.md` 第三批明确列了 **FavoritesScreen.kt 与 NetworkFavoritesScreen.kt 双双 FROZEN**，冻结口径是「允许修实际 Bug / 明确回归，禁止无明确需求的视觉重构」。共享元素转场属新增视觉能力 → **两个收藏页都要豁免，没有免豁免的那条路**。

不过豁免面积可以很小：本地收藏侧的改动是「给一个已经存在的 cover 插槽加一个 modifier」，不动结构。

## 1. 现状事实（全部 file:line 实测）

| 事实 | 出处 |
|---|---|
| `SharedTransitionLayout` 已包住整个 NavHost | `Navigation.kt:210` |
| 详情页是 `SharedTransitionScope` 扩展并收 `animatedVisibilityScope` | `ComicDetailScreen.kt:79,83` |
| 收藏页同样是 `SharedTransitionScope` 扩展、同样收 scope | `FavoritesScreen.kt:108,110` |
| **但全仓库 `sharedElement` 只有 1 处** —— 详情页封面 | `ComicDetailScreen.kt:243`（grep 全仓仅此一处） |
| `FavoritesScreen` 收到的 `animatedVisibilityScope` 是**死参数**，除声明处零引用 | grep `animatedVisibilityScope` 在 FavoritesScreen 只有 `:110` |
| 收藏页默认入口是**网络收藏**，而它既非 SharedTransitionScope 扩展也没收 scope | `FavoritesScreen.kt:125`（mode 默认 `Network`）+ `NetworkFavoritesScreen.kt` 无该接收者 |
| 因此今天点收藏卡片走的是纯 300ms 横向滑动 + 淡入淡出 | `Navigation.kt:266-276` |

⚠️ `ComicDetailScreen.kt:239` 那句注释「与列表页卡片同一 shared key，过渡照常」描述的是一个**从未接上的意图**，实际不存在任何列表页注册过对应 key。本轮已实施改动时未动它；若决定做转场，这句要一并改掉。

## 2. 版本坑：catalog 与实际解析不一致（影响可用 API）

`gradle/libs.versions.toml:8` 写 `compose = "1.7.8"`，但实测解析：

```
androidx.compose.animation:animation:1.7.8 -> 1.12.0
```

（`./gradlew :app:dependencies --configuration debugRuntimeClasspath`）。**方案按 1.12.0 的 API 写**，从缓存里的 `animation-android-1.12.0-sources.jar` 核对：

- `Modifier.sharedElement(state, animatedVisibilityScope, boundsTransform, placeholderSize, renderInOverlayDuringTransition, zIndexInOverlay, clipInOverlayDuringTransition)` —— `SharedTransitionScope.kt:620`
- `Modifier.sharedBounds(..., enter, exit, boundsTransform, resizeMode, ...)` —— `:721`
- `ResizeMode.RemeasureToBounds` 存在（`:382`），文档原话它适合 "shared **images** of different sizes" → 正是我们的场景
- **关键约束**：1.12 的 `BoundsTransform.createAnimationSpec(initialBounds, targetBounds): FiniteAnimationSpec<Rect>`（`:265-274`）—— **位置与尺寸共用一条 spec**。1.7 时代 `BoundsAnimationSpec(position = …, resize = …)` 的分轴控制在这个签名里**不存在**。所以「位置先到位、尺寸慢慢收敛」这种经典做法无法直接写出来，只能靠单条曲线的形状间接调。
- 库默认：`BoundsTransform { _, _ -> spring(stiffness = StiffnessMediumLow, visibilityThreshold = Rect.VisibilityThreshold) }`（`:1610,1687`）
- `clipInOverlayDuringTransition` 默认 `ParentClip`（`:1612`）= 只继承父 sharedBounds 的裁剪，**不裁自己** → 圆角不会自动补间。

## 3. 决定性前提：两端几何

| 侧 | 渲染点 | 封面尺寸 | 长宽比 | 圆角 |
|---|---|---|---|---|
| **详情页封面**（目标） | `ComicDetailScreen.kt:240` + `detailCoverWidth=110` + `VeneraCover` 内部 `aspectRatio(0.72)` | **110 × 152.8dp** | 0.72 | `tokens.shape.medium`（MIUIX 14 / MD3 12） |
| 收藏·详细列表（源） | `ComicListPresentation.kt:68` `Box(width=122)` + `VeneraCover` | **122 × 169.4dp** | 0.72 | 同上 |
| 收藏·网格 2 列（源） | `comicListColumnCount=2`（`ComicPresentationPolicy.kt:6`） | **≈160–170 × 222–236dp** | 0.72 | 同上 |
| 网络收藏·详细卡（源） | `ComicTileLayout.kt:68-72`，**不走 VeneraCover**，裸 `AsyncImage` | **122 × 180dp**（定高） | 0.678 | `12.dp` 硬编码 |
| 网络收藏·网格卡（源） | `NetworkFavoritesScreen.kt:395` `VeneraCover` | 同网格 | 0.72 | medium |

**读法**：详细模式下源比目标**还大 10%**，网格模式下源比目标大 45%+。也就是说飞行动作是「**缩小 + 上移**」，而共享元素转场的观感收益几乎全部来自「放大展开」的位移感。这就是为什么单接 `sharedElement` 会让人觉得「就这？」。

→ 三条出路，见 §5。

## 4. key 方案（不实测就不能动手的地方）

建议：`"cover-$sourceName-$id"`

- **为什么不能沿用现在的 `"image-${comic.id}"`**：`DetailRoute(comic.id, comic.sourceName)` 本身就带 sourceName，说明 id 不跨源唯一；而本地收藏的 `gridItems(key = "${it.id}-${it.type}")`（`FavoritesScreen.kt:588`）进一步说明**同一源内**也可能因 type 不同而重复。key 撞了 Compose 会挑一个匹配，表现为「点 A 飞过来 B 的封面」。
- **两侧能不能算出同一个串**：列表侧 `ComicItem.sourceName`（本地收藏由 `FavoriteItem.sourceKey` 填入，见 `FavoritesScreen.kt:700` 的 `toComicItem`）；详情侧 `comic.sourceName` 来自 `resolveDetailComic(route, shell.selectedComic)`，route 里装的正是点进来那个 item 的 sourceName。
- ⚠️ **但这里有个已知裂缝**：`ComicDetailScreen.kt:229-232` 的注释写明守卫链拿的是「显示名 `comic.sourceName`」，还要靠「显示名 → sourceKey」的别名解析链去对齐 —— 说明**不同入口塞进 `ComicItem.sourceName` 的东西并不统一**（有的给 sourceKey，有的给显示名）。key 用它会直接对不上。
- **所以实施第 0 步必须是纯探针**：两侧各打一条 `Log.i("SharedKey", …)`，跑遍 本地收藏(详细/网格) / 网络收藏 / 搜索结果 / 历史 / 首页推荐 五类入口，逐源核对串是否逐字相同。**这一步不通过，后面全部作废。**

## 5. 让转场「值得做」的前置选择（三选一）

| 编号 | 做法 | 效果 | 代价 |
|---|---|---|---|
| **P1** | 详情页封面加大：`detailCoverWidth 110 → 140`（高 153→194） | 详细模式源(122)→目标(140) 变成**轻微放大**；网格模式仍略缩 | `Spacing.kt` 一行 + 真机看窄屏标题列是否被挤（右侧信息列只剩 ~200dp） |
| **P2** | 详情页顶部改**封面 banner**（即上轮被暂缓的 B2：全宽封面 + 渐变压暗，标题压在封面上） | 源(122~170)→目标(全宽 360+) 是真正的「展开」，转场收益最大 | 详情页头部结构重排（约 100 行），且需引入封面主色提取或固定渐变压暗；上轮以「官方无此效果」暂缓 —— **转场是重新评估它的新论据** |
| **P3** | 不改几何，转场只当「消除跳变」用 | 最省，但按 §3 的数字，观感收益接近无 | 可能白做一次豁免 |

**我的倾向**：P1 起、看真机再决定要不要走到 P2。P3 不建议 —— 收益配不上动两个冻结页。

## 6. 动画规格（本轮重点）

### 6.1 曲线三档

| 编号 | 规格 | 说明 |
|---|---|---|
| **M-A** | 库默认 `spring(StiffnessMediumLow)` | 零代码。但它是**为跟手设计**的，和整页 fade/slide 叠加时最容易糊 |
| **M-B（推荐）** | `tween<Rect>(400, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))` | 快出慢进。Rect 单 spec 下位移与缩放同曲线最稳；400ms 是「眼睛能确认是同一张图」的下限，再短就退化成跳变。MIUI/HyperOS 的开屏动效量级也在 350–450ms |
| **M-C** | `spring(dampingRatio = DampingRatioNoBouncy, stiffness = 800f, visibilityThreshold = Rect.VisibilityThreshold)` | 比 M-A 快、不回弹。适合真机觉得 tween「太机械」时替换 |

```kotlin
// 建议放在 ComicDetailScreen.kt 文件级 private，与 CoverBounds 一起
private val CoverBounds = BoundsTransform { _, _ ->
    tween<Rect>(durationMillis = 400, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
}
```

### 6.2 四个配套（少一个，观感会**比不做更差**）

1. **DetailRoute 宿主转场必须改成纯淡入。** 现在 `Navigation.kt:266-276` 是整页横向 slide，封面同时在飞 = 双重运动，读起来像画面在抖。
   - 好消息：**不必改全局**。`composable<DetailRoute>(enterTransition = { fadeIn(tween(400)) }, exitTransition = { fadeOut(tween(250)) }, popEnterTransition = { fadeIn(tween(400)) }, popExitTransition = { fadeOut(tween(250)) })` 可以按路由覆盖，改动面 4 行，不触碰其他页面的 slide 契约。
   - 但它仍在 `Navigation.kt` 里 = 手册保护域 → **需显式豁免**。
2. **顶栏要晚一步出现。** 详情页顶栏是页内自治的悬浮返回/分享钮（`ComicDetailScreen.kt:1073` 起）+ `DetailTopBarBackdrop`。封面从下方往右上飞，会**从返回键底下穿过去**。给这两个 overlay 钮一个 `alpha = if (isTransitionActive) 0f else 1f` 的淡入（`SharedTransitionScope.isTransitionActive` 现成可用），或 `fadeIn(tween(200, delay = 250))`。
3. **打码封面一律不参与飞行**（`maskState != "VISIBLE"` 时不挂 `sharedElement`）。这**不是观感问题而是合规问题**：`sharedElement` 默认把内容渲染进 `SharedTransitionLayout` 的 overlay 且 `ParentClip` 不裁自己，等于绕过了页面级裁剪。经核对 `VeneraCover` 的打码是**自身** `blur` + 自身遮罩（`VeneraCover.kt:95,100-104`），不是靠父级 clip，所以理论上安全 —— 但 `ComicTileLayout.kt:73` 那个 `Modifier.blur(18.dp)` 是挂在**封面容器 Box 自身**上的，网络收藏的详细卡尤其要实测一次飞行途中有没有露清帧。
4. **圆角差**：详细卡 12dp（硬编码在 `ComicTileLayout.kt:70`）vs 目标 medium 14dp。400ms 内 2dp 差肉眼基本不可辨，**本轮明确不做**（要做就得动冻结页里的硬编码 dp，性价比低）。记录为已知瑕疵。

### 6.3 用 sharedElement 还是 sharedBounds

| 场景 | 选择 | 理由 |
|---|---|---|
| 本地收藏（详细/网格）→ 详情 | `sharedElement` + `CoverBounds` | 两侧都是 `VeneraCover`、同 aspectRatio 0.72、同圆角来源 → 内容完全同构，纯缩放最平滑 |
| 网络收藏详细卡 → 详情 | `sharedBounds` + `resizeMode = RemeasureToBounds` | 源是 122×180（0.678）裸 AsyncImage、目标是 0.72 的 VeneraCover，**非同构**；用 sharedElement 会在飞行中途非等比拉伸。文档明确 RemeasureToBounds 适合不同尺寸的共享图 |
| 打码命中的任何卡 | 两者都不挂 | 见 §6.2 第 3 条 |

## 7. 已知风险（都需真机确认，静态推不出来）

- **玻璃底栏会压在飞行封面之上。** 底栏是 `SharedTransitionLayout` 的**兄弟节点且在其之后**绘制（`Navigation.kt:207-236` 的 Box 层次），飞行元素在其内部 overlay → 封面从底栏**下方**穿过。进入时若源卡片正好停在列表底部被底栏压住，会有前半程被遮。要修得把 overlay 提到底栏之上 = 动壳层结构，风险高，**本方案不做**。
- **详情页的毛玻璃采样源。** LazyColumn 整体挂了 `blurBackdropSource(detailBackdrop)`（`:188`），而飞行封面在 `SharedTransitionLayout` 的 overlay 里、是 NavHost 的**祖先** → 不进录制层，顶栏玻璃不会出现封面糊影。这是想要的结果，但要真机确认一次（手册明确警告过录制层的几何问题）。
- **预测返回不跟手。** 1.12 的共享转场不会随返回手势拖动；返回时是 pop 动画触发才飞。要做到「跟手拖回卡片」得改用 `sharedElementWithCallerManagedVisibility` + 手势驱动，**另立项**。
- **飞行结束后换图。** 本地收藏卡片用 `item.coverPath`（本地文件），详情页 `liveDetails.comic.cover` 到达后是远程 URL（`ComicDetailScreen.kt:227`）→ 可能出现落地瞬间清晰度/内容变化。属既存行为，转场只是让它显眼。

## 8. 落地顺序（每步都能独立真机验证，失败可停在原地）

1. **探针**：两侧打 key 日志，跑 5 类入口逐源核对（§4）。零行为改动。
2. **最小可视**：本地收藏**详细模式**接 `sharedElement` + M-B 曲线 + key 改名。只碰 `FavoritesScreen.kt`（加 modifier）与 `ComicDetailScreen.kt`（key 表达式 + 改掉 `:239` 那句失效注释）。**豁免：FavoritesScreen 一处。**
3. **真机判定**：值不值得继续？（P3 的话大概率在这一步停）
4. **补齐运动学**：DetailRoute 纯 fade（§6.2-1）+ 顶栏延迟淡入（§6.2-2）。**豁免：Navigation.kt 一处。**
5. **几何升级**：P1 或 P2，再决定是否扩到网络收藏。**豁免：NetworkFavoritesScreen 一处。**

## 9. 需要拍板的三件事

1. **目标端几何**：P1 封面加大到 140（推荐，一行 token）／ P2 顶部 banner 化（转场收益最大，但要重排头部 + 引入压暗）／ P3 不改几何
2. **豁免范围**：只到第 2 步（1 处豁免，先看效果）／ 到第 4 步（2 处，含 Navigation）／ 一路到第 5 步（3 处，含网络收藏）
3. **动画曲线**：M-A 库默认 spring ／ **M-B tween400 快出慢进（推荐）** ／ M-C spring NoBouncy 800
