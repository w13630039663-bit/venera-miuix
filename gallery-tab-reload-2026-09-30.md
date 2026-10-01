# 画廊切回 Tab 就整页重取（2026-09-30 批次 O）

用户原话：「每次进入画廊整个页面都要重新加载，你整个上次已经加载好的数据，不刷新的话就不要主动刷新新内容了」。

## 一、根因（取证，不是猜）

**结构因**：切 Tab 走 `Navigation.kt:459-465` 的 `gotoTab` ——
`popUpTo(graph.findStartDestination().id) { saveState = true }` + `restoreState = true`。
画廊那条目的地在 start destination 之上，所以它是**被 pop 掉**的。

`androidx.navigation` 2.10.1 `NavBackStackEntry` 的类头 KDoc 明写：

> When an entry is popped off the back stack, the lifecycle will be destroyed,
> state will no longer be saved, and **ViewModels will be cleared**.

而它的 `viewModelStore` 是按条目 `id`（`randomUuid()`）向 provider 取的 —— 重新进来是一条**新条目、新 id、新 VMStore**。
⇒ **`saveState` / `restoreState` 只救 `rememberSaveable`，救不了 `viewModel()`。**
切走再切回，画廊那四个 VM（`GalleryViewModel` / `GalleryForYouViewModel` /
`GallerySearchViewModel` / `GalleryReverseViewModel`）全是新实例。

**为什么只有猜你喜欢那一屏在联网**：日榜那头 2026-09-29 就为同一件事治过 ——
`GalleryViewModel.init` 同步读 `GalleryFeedCache`，配
`shouldAutoLoad() = !hydratedFromCache || GalleryFeedRefreshPolicy.shouldRefetch(...)`
（`GalleryViewModel.kt:105-126`），今天存过就不联网。

**猜你喜欢这一屏从来没接上同一套**：
- `GalleryForYouViewModel` 无缓存、无同日门、无节流（全类只有 `loadedKey`，而它是"同一次组合"的守卫，新 VM 上恒为 null）；
- `GalleryScreen.kt` 那个 `LaunchedEffect(fvm.refreshTick)` 因此每次都落到 `fvm.load(...)`；
- 外加 `var seed = System.currentTimeMillis()` + `recommendations = remember(galleryFavorites, rules, fvm.seed)`
  ⇒ **新 VM 连推荐标签都重抽一遍**。所以用户读到的不只是"重新加载"，是"内容还换了一批"。

一句概括：**这不是回归，是 2026-09-29 那条既定口径（"同一天之内不再自动联网"）当时只落在日榜那一屏，漏了主墙。**

## 二、拍板（AskUserQuestion 三条）

| # | 问 | 定 |
| --- | --- | --- |
| 1 | 修法走哪条 | **A：照日榜那套给猜你喜欢补快照 + 同日门**（不碰保护域，判据可单测） |
| 2 | "不主动刷新"的有效期 | **同一天不自动取**（与日榜一致；跨天补拉一次） |
| 3 | 搜索那面墙的上下文栈也一起治？ | **先不动**，本轮只治首页重取 |

被否掉的两条留个记录：**B** 把画廊 VM 提到壳层常驻（连滚动与搜索栈一起留，但取 owner 要动
`Navigation.kt` 这个保护域，且两站池子会常驻内存）；**C** 改 `gotoTab` 不弹栈（一处改完五个 Tab
都不重建，但返回栈语义、返回键、底栏 `currentTab` 判据、切 Tab 方向动画全跟着变）。

## 三、落地

- **判据层（新增，可单测）**：`gallery/domain/GalleryForYouRefreshPolicy.kt`
  —— `canHydrate(postCount, seed)`（内容与种子必须**成对**在场）与
  `shouldAutoLoad(hydrated, cachedOnEpochDay, todayEpochDay)`。
  同日判断**复用** `GalleryFeedRefreshPolicy.shouldRefetch`，不留第二份。
- **存储层（新增）**：`gallery/data/GalleryForYouCache.kt` —— 照 `GalleryFeedCache` 的写法
  （构造侧同步读 / 先写 `*.tmp` 再改名 / `ignoreUnknownKeys`）。
  "按站一份"的几列存成**行式** `List<GalleryForYouSiteRow>` 而不是 `Map<枚举, String>`：
  一是不押 kotlinx 对枚举键 Map 的编码口径，二是站点认不出时可以逐行摘掉而不是整档作废
  （与日榜那头 `mapNotNull { fav.site?.let {...} }` 同一条口径）。
- **VM**：`GalleryForYouViewModel` 加 `init` 读盘恢复 + `shouldAutoLoad()`；
  `refresh()` 与那两个标记成对清（不清就把「换一批」拦成假按钮）；
  每一页成功都刷快照（只存第一页的话，翻到第 4 页再切回来会退回第 1 页那一屏）。
- **页面**：`GalleryScreen.kt` 那个 `LaunchedEffect` 补一道 `if (!force && !fvm.shouldAutoLoad()) return`。

### 三条落地时定死的口径

1. **`seenKeys` 不落盘，从恢复出来的那一屏重算。** 它本来就是从这些 post 累出来的；
   存第二份等于给"两份不一致"留位置（存了旧键、屏上换了新条，去重就会漏剔或误剔）。
2. **空快照一律不算"已铺好"**（`canHydrate` 里 `postCount > 0`）。这一条比看上去重要：
   空屏如果被认成已铺好，同日门就恒关 —— **一屏空白而门是关着的，永远不再取**，
   那比"每次都刷新"坏得多。落盘那头同样只存在到内容的那一轮，这是第二道闸。
3. **`stage` 也要铺成 READY。** 只恢复 `posts` 不恢复 `stage`，页面第一帧念的是 IDLE，
   而 IDLE 那一档摆的是整屏加载环 —— 快照存了个寂寞。

## 四、自记错误（本轮实现里被编译抓到的两处）

- **`init` 块放错位置**：先写在 `generation` 字段之后，而它要赋的 `seenKeys` / `sitesDone`
  声明在更下面 —— Kotlin 的 init 块**按声明顺序**跑，于是报
  `Variable cannot be initialized before declaration`。
  这个坑值得记：这个类里已经有一个 `init`（订阅 Gelbooru 账号），多个 init 块是**按位置串起来**的，
  不是"随便放哪都一样"。
- **两个 import 漏了**：`kotlinx.serialization.json.Json`（照抄 `GalleryFeedCache` 时漏了一行）
  与 `toPost` / `toFavorite`（那两个扩展在 `gallery.data` 包里，日榜那头同包所以不用 import，
  VM 在 `gallery.ui` 就得显式引）。

## 五、验证

- 判据层 8 条**先红后绿**：红是编译级（`Unresolved reference 'GalleryForYouRefreshPolicy'`，
  新 API 不可避免，与批次 M · M5 同一档），实现后 8 条全绿。
- 全量单测 **553 条 / 0 失败 / 0 错误**（545 + 新 8），`:app:assembleDebug` 通过（APK 20:42）。
- **装机未完成**：`adb devices` 空列表。

### 真机清单（页面由用户点，我只装机与读数）

1. **主判据**：进画廊取到内容 → 切到首页 → 切回画廊 ⇒ **不转圈、内容还是那一屏、次序没变**；
   `adb logcat` 里这一轮不该出现画廊搜索取数的网络日志。
2. 「换一批」**仍然真换**（同日门不能把它拦成假按钮）：按下去节头出那枚小波浪环、芯片禁用、
   新第一批到货才整批替换。
3. 翻两三页到底 → 切走再切回：应停在**已经取到的那一屏**（不是退回第 1 页那一屏），
   页尾"累计 X 张 / 已排除 N 张"那两个数**没归零**。
4. 跨天首进：补拉一次（这是对的，不是 bug —— 推荐内容跟收藏走，两天完全一样读起来像坏了）。
5. 手工把 `files/gallery_for_you_cache.json` 改成非 JSON → 重进画廊应**照旧联网取一次**，
   而不是停在空屏（读不出来就当没有，这一档存的不是用户产生的数据）。
6. 冷启动第一帧：不该是整屏加载环（快照同步读盘，第一帧就有内容）。
7. 双源混摆退化成一源时，那句**可见**提示在切回 Tab 之后**还在**（`failures` 是存进快照的，
   不存就会静默消失）。

### 本轮没治的同一根因症状

- **搜索那面墙的上下文栈**（`GallerySearchViewModel`）切 Tab 全丢 —— 用户拍"先不动"。
- **滚动位置**：`rememberLazyStaggeredGridState` 属 `rememberSaveable` 一族，
  理论上能活过 `saveState`；真机若发现切回来仍在顶部，那是另一条成因，别拿本条当解释。
