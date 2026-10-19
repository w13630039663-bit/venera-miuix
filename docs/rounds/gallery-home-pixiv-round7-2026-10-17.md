# 画廊首页第七轮：pixiv 标题 / 不重取 / 无限下拉 / 下拉指示器 / 每日热门先到先上屏

2026-10-17 第七轮。用户两张截图、六条反馈，全部落在画廊首页这一块。

## 一、用户原话与逐条对应

> 1：「pixiv 这边还是有很多东西没用读取到，推荐下面的图标题没了，直接用 p 站自己的标题就行，
> 另外不要每次切页面都刷新，手动下拉才刷新，另外这个推荐也是要做成瀑布流的无限下拉的」
> 2：「下拉刷新有了，但是下拉没有加载指示器，而且每日热门这个加载时间太长了」

| # | 反馈 | 真因 | 落点 |
|---|---|---|---|
| 1 | 推荐下面的图标题没了 | 墙上那行标题是 `GalleryCardTitle` **拿标签去离线词典算**的；pixiv 的标签是日文，词典里一条都没有 ⇒ 算不出来 ⇒ 整行不画 | `GalleryCardsGrid.stationTitles` + `pixivRecommendTitles` |
| 2 | 不要每次切页面都刷新 | `domain` 是两条 effect 的键，切域回来 effect 重跑、结果每次重发 | 排行榜按档缓存 + 推荐取数凭证 + 进程级存档 |
| 3 | 推荐要做成瀑布流无限下拉 | 站方推荐只取第一页；`PixivAppParse` 把 `next_url` 扔了 | `PixivAppPage` + 续页 effect（复用 `GalleryLoadMoreState`） |
| 4 | 下拉没有加载指示器 | 下拉那一行**只长高、内容是空的**（前一版就一个 `Box(height=…)`） | `GalleryForYouScreen` 那一行里加 `CircularWavyProgressIndicator`，高度/透明度跟手势 |
| 5 | 每日热门加载时间太长 | ① 三站并发但整轮等最慢那站；② 下拉刷新走 `vm.refresh()` 会**清屏**再等 | `loadDaily(onLeg)` 先到先上屏 + `refreshKeepingContent()` |
| 6 | 排行榜整档空着 | 匿名的 `www/ranking.php` 不给内容，而取数把异常 `getOrNull()` 吞了 ⇒ 屏上只有"没有答上" | 登录态走 `app-api/v1/illust/ranking`；失败把站方原话念出来 |

## 二、标题：站方原题**覆盖**算出来的那批

pixiv 每一行都带 `title`，booru 三站没有这一格（它们的标题是算的）。两边混成一层是最难解释的错配，
所以做成"**覆盖**"而不是"两行都摆"：

- `PixivWork.title` → `uid → title`（`GalleryScreen.pixivRecommendTitles`，空串也收）；
- `GalleryCardsGrid(stationTitles=…)`：`dict + station`，**站方给过的 uid 从词典那批里摘掉**，
  空串不进结果 ⇒ 卡上取到 null ⇒ 那一行整行不画（不是又摆回我们猜的那个）；
- 只有 `domain == PIXIV` 时传表，booru 那两屏与从前**逐字节同形**。

## 三、不重取：三层缓存，清的只有一处

1. **排行榜按档缓存**：键 `"${mode}#${app|www}"`。键里带"哪条腿"是必须的 —— 登录前后同一档
   是两条腿、两份内容，共用一个键会把匿名那批当成登录后的那批。
2. **推荐取数凭证**：`requestKey = "app"` 或 `"seeds:<种子表>"`。
   ⚠️ **凭证里不拼刷新计数**：拼了的话重新组合时计数归零、凭证永远对不上 ⇒ 每次回来都重取。
3. **进程级存档** `GalleryPixivHomeStore`：切主 Tab 走 `popUpTo(saveState)`，这条目的地被 pop，
   `remember` 全丢（`NavBackStackEntry`：ViewModels will be cleared；saveState 只救 `rememberSaveable`）。
   所以离开时 `onDispose` 把这一池写回存档，回来直接摆。
   - 首帧**不许清**：`LaunchedEffect(pixivAuth)` 加了 `pixivAuthSeen`，只有登录态**真的变了**才清。
     不加这一句，每次回到画廊都会把刚恢复的那一池抹掉。

清的只有一处：**下拉刷新**（`refreshWholePage` 里 `pixivRankingCache.clear()` + 凭证置 null），
外加登录态变化。

## 四、无限下拉：翻页只认站方给的地址

`/v1/illust/recommended` 的 `next_url` 是**完整地址**（自带 `offset` 与 `filter`）。
自己拼 `offset=` 等于猜站方的分页口径，猜错的表现是"第二页跟第一页一样"或"第 3 页开始 400"，
两种都不报错 —— 所以 `PixivAppPage(works, nextUrl)` 原样带出来，下一页只换地址、不叠参数。

- 站方推荐：`works.recommended(bearer, next)`；
- 种子那一跳：`p = 页码 + 1`，三枚种子各取一页；
- 追加**一律按 uid 去重**（站方推荐跨页会重复，它按兴趣打分、不按页切）；
- 判据复用 `GalleryLoadMoreState`：墙得是活的、到底了不判、在取时不并发。
- 页尾新增两档：`正在取下一屏…` / `pixiv 这一池到底了`（空/忙两档仍由读数给）。

## 五、下拉指示器

从前那一行是 `Box(height = pullRowHeight)` —— **只有高度，没有内容**：下拉时整页确实被推开，
屏上一个转的东西都没有，读起来是"页面被我拉歪了"。
现在里面是 `CircularWavyProgressIndicator(size = loaderInline)`，高度与不透明度都跟手势进度走
（刚拉一点点只是隐约一枚，松手后满高才完整）。

## 六、每日热门：先到先上屏 + 下拉不清屏

- `GalleryDailyFeed.loadDaily(seed, onLeg)`：三笔仍然 `async` 并发，但**哪一站先回来就先回调**，
  屏上 `GalleryViewModel.acceptPartial(pools)` 用同一把 `GalleryMerge.mix(pools, seed)` 先摆一屏；
  整轮收口时仍用最终 `Daily` 整屏替换（口径不变、最终结果与从前逐字一致）。
  - `acceptPartial` 刻意**不写** `feedLoadedKey`、**不写**磁盘快照：中途那一屏不是最终结果。
- 下拉刷新走 `vm.refreshKeepingContent()`：`refresh()` 会 `posts = emptyList()` ⇒ 整面墙换成骨架再等，
  那正是"加载时间太长"的观感来源。新的一颗只换种子 + 清 `feedLoadedKey`，旧屏留着，新批落地时整屏替换。

## 七、排行榜：登录着走 app-api

匿名的 `www.pixiv.net/ranking.php?format=json` 对这台机器不给内容，而旧代码
`.getOrNull().orEmpty()` 把异常吞了 —— 屏上只剩「pixiv 这一档这一轮没有答上」，
读不出是限流、票据过期，还是这一档真没图。

现在：登录态 → `app-api/v1/illust/ranking?mode=day|week|month|week_rookie|week_original`
（`PixivRankingMode.appParam`，与 www 的 `daily|weekly|…` **不通用**）；匿名仍走 www。
两档都把站方原话经 `PixivHomeState.rankingCaption` 念到屏上。

## 八、判据

| 文件 | 笔数 | 钉住什么 |
|---|---|---|
| `GalleryPixivHomeWiringTest` | 11 | 标题来源/覆盖、缓存先于取数、只有下拉清缓存、续页走 `next_url`、凭证不拼计数、存档与首帧不清、下拉行有指示器、`onLeg`/`refreshKeepingContent` |
| `GalleryDailyStreamingTest` | 2 | 快站先落屏（慢站用 `CompletableDeferred` 闸门，不靠 `delay` 猜时序）、默认参数行为不变 |
| `PixivAppParseTest` | +4 | `next_url` 原样/缺席、榜单读 `rank`、推荐腿不冒名次 |

## 九、没做的（如实）

- **pixiv「关注画师」那一栏仍是空引导**。它要的是"站方推荐用户"，而现量
  `app-api/v1/user/recommended` 回 400/191、www 那侧六条不同名字的路全 404 / 169 字节同一形状
  （逐条记在 `GalleryHomeSections.kt` 那条注释里）。没有可验证的端点就不摆列表 ——
  摆一排点不动的头像是本仓最忌的假开关。要接得先有一条真能打的端点。
- **上机自验没做**（agent 侧红线）：下拉指示器、推荐第二页、排行榜登录态这几条只能真机看。
