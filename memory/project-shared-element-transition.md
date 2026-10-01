---
name: project-shared-element-transition
description: 封面共享元素转场（列表→详情页）的现状与硬约束：两端 key 必须同串否则静默不飞、两端不同构会落地闪、compose 1.12 无现成 composition local 要自建、material3 里没有 SharedLayout
metadata:
  type: project
---

2026-09-20 起，「列表卡片 → 漫画详情页」的封面连贯转场已铺到收藏/搜索/历史/探索/分类五个页面。口径集中在 `components/ComicSharedTransition.kt`（key 拼法、曲线、占位尺寸、作用域 local）。

**版本与 API 真相（都实测过，别再重查一遍）**：

- `gradle/libs.versions.toml` 写 `compose = "1.7.8"`，但 `:app:dependencies --configuration debugRuntimeClasspath` 实际解析到 **ui / animation 1.12.0**（material3 被 Miuix 顶到 `1.5.0-alpha22`）。按 1.12 的 API 写。
- **material3 里没有 `SharedLayout`**：1.5.0-alpha22 的 classes.jar 1321 个类里零个 `shared*`，1.4.0 的 sources jar 同样为零。能用的只有 animation 层的 `SharedTransitionLayout` + `sharedElement` / `sharedBounds`。
- animation 1.12 **没有** `LocalSharedTransitionScope`，navigation-compose 2.8.9 **没有** `LocalAnimatedVisibilityScope` → 作用域只能自己传。做法：`Navigation.kt` 在 nav 条目处 provide 一个自建的 `LocalCoverTransitionScopes`，深层卡片直接读，避免把两个作用域沿「页面→分组→行→卡」逐层灌参数。
- `BoundsTransform(initial, target)` 返回**一条** `AnimationSpec<Rect>` —— 位置与尺寸共用曲线，1.7 时代 position/resize 分轴控制在这里写不出来。
- `placeholderSize` 只接受 `SharedTransitionScope.PlaceholderSize.AnimatedSize` / `ContentSize`，**不收 IntSize**。返回方向用 `AnimatedSize`（槽位跟着飞行一起收敛）。

**两条静默失效的坑（不报错，只是什么都不飞 / 闪一下）**：

1. **两端 key 必须逐字同串**。详情页只能用 route 带的 `(sourceName, id)`，所以列表侧必须用**它交给 `openComic` 的那个同一个串**。踩过的实例：搜索页卡片有 `comic.sourceKey`，但 `select()` 把 `event.sourceName`（显示名）交出去 → 对不上；已改成统一交 sourceKey。历史页 DB 只有一个 `sourceName` 字段、探索页交显示名 → 那两处直接沿用交出去的串最稳。连带修掉一个既存缺陷：详情页点标签下钻时源预选是 `find { it.name == 入参 }`，只认显示名，而收藏页交的是 key → 那条路一直没生效。
2. **两端不同构就别用 `sharedBounds` + `ResizeMode.RemeasureToBounds`**：落地那一帧会按目的地重测内容 → 观感就是"闪一下"。收藏页单列卡原本是 122×180（0.678）而详情是 0.72，改成两端同比例后统一挂 `sharedElement` 就不闪了。

**已接 / 未接清单**：收藏（本地+网络，单列+双列）、搜索（单列+双列）、历史（单列+双列）、探索页（单列+双列）、分类二级页 均已挂。**未接**：首页推荐、下载中心、追更、本地书架。

**已知观感代价（用户看过并明确说不用改）**：双列卡的源名徽章画在封面的内容槽里，会随封面一起飞、落地后消失。打码命中的封面一律不挂（飞行内容渲染进 overlay，等于绕开页面级裁剪）。

**跨窗口 / 跨 Activity 做不了共享元素（2026-09-22 查证，别再重开）**：animation 1.12.0 只有 `SharedTransitionLayout` / `sharedElement` / `sharedBounds` / `renderInSharedTransitionScopeOverlay`，全部建在 `LookaheadScope` 上，官方文档要求 `SharedTransitionLayout` 坐在层级里同一个顶点；1.12.1/1.13.0-alpha03 也没放出跨窗口 API。平台侧 `makeSceneTransitionAnimation`/`transitionName` 明确「No interoperability between Views and Compose」。Android 16 白送的是**整窗口**跨 activity 预测式动画，不是元素级。所以「详情页 Activity 化 + 保留封面飞入」只能自绘交接（bitmap/overlay）。参照项目 `FooIbar/EhViewer` 同样是单 Activity + 一个 `SharedTransitionLayout`。

**EhViewer 给的现成口径（已采用其页面转场）**：页面转场用官方 Material Motion 库 `io.github.fornewid:material-motion-compose-core:2.0.1`（`soup.compose.material.motion.animation`）的 **shared axis X** —— 300ms、`rememberSlideDistance()` 的 **30dp**、淡入按 0.35 阈值错峰；它比整屏横推更适合「列表→详情」，因为页面几乎不动、封面成为主语。它自己的飞行元素是 `Modifier.sharedBounds(key).clip(shape)` 且**不传 boundsTransform**（吃 `SharedTransitionDefaults.BoundsTransform = spring`，见 `SharedTransitionScope.kt:1684`），所以飞行与页面同轴。它用 `SETNodeGenerator.connectTo` 生成 syntheticKey 来隔离「同一本书出现在多个列表」的错配。**注意**：上面坑 2 记的「我们试过 sharedBounds 会闪」是两端比例不同构 + `RemeasureToBounds` 造成的，不等于 sharedBounds 本身不可用。

相关：[[project-nav-entry-recomposition]]、[[feedback-mirror-official-values]]、[[project-card-size-drivers]]
