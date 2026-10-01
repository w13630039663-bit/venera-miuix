# 批次 Q + R：画廊 hero 转场 · 跳转落点 · 画师介绍页（2026-10-01）

方案由 `tender-marsh-swallow.md` 那份计划批准落地。这一份是**改完之后的回写**：
做了什么、探针量到什么、哪几条与批准时的写法不同（不同处逐条写明，不粉饰）。

---

## 一、批量 0 + 3：hero 共享元素（预览行 ↔ 每日热门二级页）与预览行补截帧

改动四处，全部复用现成机制：

1. `feature/Navigation.kt`：`composable<GalleryRoute>` 与 `composable<GalleryDailyRoute>` 各包一层
   `CoverTransitionHost(animatedVisibilityScope = this) { … }`。
   **这就是"画廊页面切换没有过渡"的直接成因**：没包 ⇒ `LocalCoverTransitionScopes` 读到 null
   ⇒ `coverSharedElement` 那一句 `return this` 静默不挂（`ComicSharedTransition.kt:86-88`）。
2. 新建 `gallery/ui/GallerySharedTransition.kt`：`galleryCoverKey(uid) = "gallery-cover-$uid"`。
   两端都取 `post.uid`，**不能用 `id`** —— 两站各自编号，同 id 是两张不同的图。
   三份用例钉着（`GallerySharedTransitionTest`）：两站同 id 不撞 key / uid 含冒号稳定 / 不与漫画侧同形。
3. `gallery/ui/GalleryHomeSections.kt`：`GalleryPosterRow` 的封面挂 `coverSharedElement`，
   并**补上截帧**（`onGloballyPositioned` 拿窗口矩形 + `GalleryFlyIn.capture(view, bounds)`）。
   不做这条，"点预览行的图进大图页"永远 `canFly=false`、必然整页抬上来 ——
   这一条在批准前就已在代码里定案（`GalleryFlyIn.capture` 当时全仓只有一个调用点）。
4. `gallery/ui/GalleryScreen.kt`：`GalleryCardsGrid` 加可选参数 `sharedElementKey: ((GalleryPost) -> String)? = null`，
   透传给 `GalleryPostCard`。**只有 `GalleryDailyPage` 那条路径传**：首页主墙 / 搜索墙 / 收藏墙对面
   没有同 key 的落地槽位，挂了是白挂 + 飞行体没处去。

打码那两档都留了 `allowFly = !card.masked`（飞行体画进 overlay 会绕开页面级裁剪，遮罩不能被揭开）。

只做这一对的理由写进了新文件头注：这是画廊里唯一"两端都在 NavHost 内、且两端都拿得到同一 uid"的一对。
大图页那一侧是跨 Activity，共享元素在 animation 1.12 上结构性做不到（既有查证与"别再重开这条"在
`memory/project-shared-element-transition.md`）。

**不同构的取舍按计划走**：先用 `sharedElement`（两端都 Crop、构图一致），真机看落地那一帧闪不闪；
闪再换 `sharedBounds` + `RemeasureToBounds`（仓内先例与代价记在 `NetworkFavoritesScreen.kt:478-482`）。
本轮不预设。

## 二、批量 1：R2 取证（读数已拿到，结论修正过一次）

`GalleryFlyIn.capture` 的两个静默出口各一条 `Log`（`FlyProbe`），`GalleryPostScreen` 的
`canFly` 分支两条（三样输入 + 落点 `imageBounds` 到达耗时 vs 400ms 上限）。
读数（同一台 PJZ110，两次各点一张墙上的图）：

```
# 界面材质 = 液态玻璃
FlyProbe: exit=draw-threw IllegalArgumentException: Software rendering doesn't support RuntimeShader
FlyProbe: exit=ok payload=false origin=null
FlyProbe: canFly=false frame=nullxnull origin=null

# 界面材质切成非玻璃（用户 2026-10-01 02:33 实测。我原本预测"切了就能飞"—— 这条预测错了）
FlyProbe: exit=draw-threw IllegalArgumentException: Software rendering doesn't support hardware bitmaps
```

**根因比第一眼看到的更宽**：`view.draw(软件 canvas)` 这一条路有**两个各自独立的死因**，
换材质档只是把其中一个换成另一个，路本身走不通 ——
① 卡面液态玻璃走 `textureBlur` → `RuntimeShader`，软件渲染回放不了；
② Coil 那张封面位图是 `Bitmap.Config.HARDWARE`，软件 canvas 同样画不了。
两处抛出都被 `runCatching` 吞掉 ⇒ `payload`/`origin` 双双不置 ⇒ `canFly` 恒假 ⇒ 永远走"整页抬上来"。

这正是批准方案里**预批了退路**的那一档（"若 `view.draw` 确实截不到，改用
`PixelCopy.request(window, rect)`"），用户在两条候选里点了 PixelCopy。改法与两条附带结论
写进 `GalleryFlyIn` 的头注：为什么 PixelCopy 顺带把"打码卡不能飞"变成**由构造保证**
（截的是屏上像素，遮罩那层本来就在屏上），以及异步带来的 `WAITING` 档为什么必须让对面
等一等再定案（不等的话，像素到位那一刻那一帧会直接出现在**落点**上，观感是"图先闪一下"）。
探针暂留：PixelCopy 那一版的 `stage` 读数还没在真机上确认过。

**R1 那一头用户实测有 hero 动画**（节头 → 查看全部 → 返回那条飞得起来），
所以批量 0 的四处改动成立；他真正要的是**"点其他图片也有这个效果，也不单纯这个位置"** ——
那正是上面这条被硬件位图与 shader 挡住的路。取证期间装过一次 `HeroProbe`
（只盯 `gallery-cover-` 前缀），结论拿到即撤。

## 三、批量 2：④ 收藏页点 tag 直接落在画廊搜索

真实成因不是"收藏卡面没有 tag"，而是**交接落点错了**：画廊收藏挂在漫画侧收藏页的一个分段里
（`feature/FavoritesScreen.kt:548`），而消费 `GallerySearchHandoff.pending` 的那句 `LaunchedEffect`
挂在画廊 Tab 自己的组合中（`GalleryScreen.kt:211`）—— 从收藏页进大图页时画廊那屏不在组合里，
槽位写了没人取，于是退回收藏页；手动切到画廊才重新组合消费。与读数完全吻合。

改法：`Navigation.kt` 宿主层加一条 effect，**读目的地而不是 `currentTab`**
（`GalleryDailyRoute` 也算 GALLERY，用 currentTab 会把那条路判成"已经在画廊"而不跳）：

```kotlin
LaunchedEffect(GallerySearchHandoff.pending, destination) {
    if (GallerySearchHandoff.pending == null) return@LaunchedEffect
    if (destination?.hasRoute(GalleryRoute::class) == true) return@LaunchedEffect
    navController.gotoTab(VeneraNavTab.GALLERY)
}
```

计划里写的 `peekPending()` **没加**：`pending` 本来就是 `var … private set` 的公开只读快照，
再加一个窥视口是同一个东西的第二份名字。宿主层只读不 consume，消费仍只由 `GalleryScreen` 做
（它才拿得到 `svm`），所以"谁先到谁吃掉"那个风险不存在。

`Navigation.kt` 本轮的豁免具体给了两处：上面那两个 `CoverTransitionHost` 包裹 + 这一条 effect。
`VeneraNavTab` 枚举、路由映射、顶栏齿轮入口、新增目的地一律没动。

## 四、批量 4：② 画师行读得出可点 + 落点改判

成因不是"少了个按钮"而是**两枚东西的可见度反了**：右侧「关注」是带按压缩放的 `VeneraChip`，
而真正会跳的名字此前只是一段 `Text` 挂了个零提示的 clickable —— 看起来更像按钮的恰好不能跳。

- 头像 + 名字整块可点 → 进画师介绍页；按压那档照 `VeneraChip` / `VeneraTopBarPill` 的
  0.96 / 0.88，不造新数字；走向标用节标题那枚 `Text("›")` + `tokens.type.chevron`。
- 原"点 = 搜"这个动作下移一格，由介绍页里的「看 TA 全部作品」承接。
- 首页「正在关注的画师」：**单击仍直接搜**（批次 M 拍板 2 不动），**长按 = 进介绍页**。
  跨站合并的那位（`FollowedArtistRef.sites` 是列表）进页只走**第一个站**并在页上标那一站的徽标 ——
  介绍页的关注关系、别名、外链都是按站取的，没有任何一处判据说"两站并排摆在一页里"该怎么合。
- 平台徽标那排（开浏览器）语义没动。
- 红线保留：取不到 = 什么都不摆，且不说"没有"；失败那一路日志留话。

## 五、批量 5：③ 画师介绍页

**承载**：新建 `GalleryArtistProfileActivity`（照 `GalleryPostActivity` 全套：
`opaqueAmbientBackground=false`、`blurBehindDp=32f`、`Theme.Venera.GlassOverlay`、
`makeCustomAnimation(0, 0)` 只压开启动画），注册进 `AndroidManifest.xml`（`exported=false`）。
传参走 Intent extras（站点键 + 画师名 + 这张图的出处），不新建 handoff。

**返回栈不出现"返回两次"**：栈是 Main → Post → Profile。「看 TA 全部作品」= `setResult(OK, 站点+名字)` + finish；
`GalleryPostActivity` 用一个自己的 `ArtistProfileResultContract` 收结果（不取 `StartActivityForResult`
那个通用件：它交回整个 `ActivityResult`，调用点得再挖一遍 extras 再认站，而"认不出站点键就当没这回事"
正是这一层最不想要的形状），收到就写进 `GallerySearchHandoff` 并**自行 finish**。
从首页长按进时无人收结果，栈天然两层。

**数据面**（一次进页 3~5 笔，全走现成客户端，零新端点）：
- 外链：yande.re `artistLinks` / danbooru 供体 `artistCredits`（**外链与别名同一笔请求**）；
- 头像：复用详情面板那份 `resolveArtistAvatar`（pixiv 用户档 → fanbox → Mastodon → 首字母座），
  两边共用一份，不留迟早会漂的副本；
- 正名指针：yande.re 走批次 J 已有的 `resolveArtistAlias`，页上那句是"站方记的正名是 X"；
- Popular posts：`GalleryRankings.searchQuery(site, [画师名], ALL, null, todayUtc)`，
  两站后缀各是什么由那一处钉着（`order:score` / `sort:score:desc`），这里不互抄也不另造参数；
  卡用墙上的 `GalleryPostCard`（该件从 `private` 改 `internal`），屏蔽与分级走**同一把** `buildGalleryWall`。

**判据层**（`gallery/domain/GalleryArtistProfile.kt`，先红后绿）：
`profilePlan`（全平台、按身份去重、固定档序、OTHER 照摆且标签给域名）、
`aliasReadout`（去自身、去空白、大小写无关去重、上限 8 并交回 `hiddenCount`）、
`GalleryArtistLinks.chipLabel`（画师行与介绍页共用一份平台名）。
新增枚举档 INSTAGRAM / TUMBLR / YOUTUBE，判定形状按真样本收：
`instagram.com` **只认单段 handle**（样本里 3/3 全是这一形，`/reel/`、`/stories/`、`/share/profile/`
一条都没有 ⇒ 多段一律 OTHER，与 `/artworks/N` 判 OTHER 同一口径）；
tumblr 认子域（样本 1/1）；youtube 一条样本都没有，只收结构唯一的 `@handle` 与 `channel/UC…`。

## 六、别名探针的读数（批量 5 的前置取证，决定性）

`_probe/yande_re_alias_coverage.cjs`，读数同目录 `.txt`。

**第一版是坏的**：从 `post.json` 取 `tag_string_artist` 当画师名语料，实测**那个键根本不存在**
（post.json 的键表里只有 `tags`）→ 320 条帖子只捞出 1 个名字，那份 0/1 读数作废。
第二版改成按字母前缀取样（`artist.json?name=<单字母>` 本身就是前缀匹配，一批就是真实画师名清单）。

读数（19 个横跨字母表的名字）：

| 项 | 读数 |
| --- | --- |
| 可测 / 全等取到记录 | 18 / 18（f*cla 那一条 curl TLS 失败，没算） |
| 自己是别名记录（带 `alias_id`） | 2 |
| 同批凑得出别名表 | **0/18** |
| 结构天花板：别名名与正名同前缀 | **10/111 ≈ 9%** |
| 300 条扫到的记录里带 `alias_id` | 111（37%） |

**结论：yande.re 的别名列表整块不上**（计划里那句"对不上整块不摆"就是为这一档写的）。
屏上永不出现"这位没有别名"；yande.re 那侧只保留"站方记的正名是 X"那一句，走批次 J 已有的解析链。
别名列表只上 **Gelbooru 腿**，供体是 danbooru 记录里的 `other_names`
（真样本 `_probe/hub/dan_setmen.json`：`name=setmen` 挨着 `other_names:["u_u","セトマン"]`）。

顺手量到的两条站方事实（都是"200 + 合法 JSON ≠ 参数生效"那一类）：
- `artist.json` 的 **`limit=` 被静默忽略**（`limit=1` 与 `limit=40` 都回 25 条），`page=` 生效。
  按全名查时批次本来就短，不影响正确性，但**不要拿"批内条数"当分页预算用**。
- `tag.json?category=artist` 的 `category` **不生效**（回 type=0/3 的混集），不能拿来当画师名语料。

## 七、与批准时不同的地方（逐条写明）

1. **没有 `GalleryArtistProfileViewModel.kt`**。计划列了这份文件，实际把取数放在
   `GalleryArtistProfileScreen` 里（`LaunchedEffect` + `remember` 状态），与详情面板那排画师入口
   同一个既有口径：这一页是单用途 Activity，页面一关状态就该跟着没，VM 只是多一层名字。
2. **计划判据项 5 `popularSlots(posts, slotCount, blockedOf)` 没抽层**。卡位恒 6 这件事在屏上就是
   `(0 until 6)` 生成格子、`getOrNull(i)` 填卡，而 `GalleryCard` 这个类型活在 `ui/GalleryScreen.kt` 里 ——
   为一个 `getOrNull` 把 Compose 侧的数据类拖进可脱离 gradle 单跑的判据层，代价不对等。
   去重也没做：介绍页只查一站，同站内 `id` 唯一，"按带站 uid 去重"没有对手。
3. **计划判据项 6 `followState` / 项 7 `sectionsToHide` 没抽层**：前者就是现成的
   `GalleryArtistFollows.isFollowing(follows, site, name)`；后者三态里"未回来 / 答上了但空"
   在屏上确实同形（都不摆），唯一需要落字的是"失败要说这一站没答上"，那一条直接写在屏上
   并留了日志，没有第二个调用点，不构成一份判据。
4. **计划里"介绍页顶栏右侧更多菜单"没做**：那一枚当下没有任何动作可放（分享与"在站点打开"
   都够不着画师这个对象），先只留返回。
5. **`WEB` 枚举档刻意不加**（计划档序里写着 PIXIV>…>WEB>OTHER）。量过现成用例后：
   `rainboy.jp/`、`yunting.artstation.com/` 这类站点根在 2026-09-30 就被判死为 OTHER
   （"认不出的服务一律 OTHER 不猜"，`GalleryArtistLinksTest` 钉着），而 WEB 与 OTHER 在屏上
   长成同一个样子（标签都是域名）。加那一档等于把一条已经拍过的判据劈成两半，收益为零。
   这条决定写进了 `GalleryArtistLinkPlatform` 的头注，免得下一轮当没查过。
6. **平台图标（批量 6 / 任务 #111）还没做**：用户点名"带平台图标"已经记进
   `GalleryArtistRows.kt` 的头注（原来那句"今天没有拍板过"已改成已拍板 + 日期），
   但 `scripts/build_platform_icons.mjs` 与资产尚未落地，所以两处胶囊**现在仍是文字**。

## 八、顺带修掉的一个既有隐患

`GalleryArtistLinks.hostOf` 对**没有路径段的地址**（`https://mochida.tumblr.com` 这种站方真给的形态）
会把 authority 读成空串 —— 那行写的是 `substringBefore('/', "")`，无分隔符时交回的是 `""` 而不是整串。
后果是这条链接被静默判成 OTHER，屏上只少一枚胶囊，没人会来报。
改回用 Kotlin 默认值（无分隔符交回整串），并补了一条用例钉着不带路径那一形。

## 九、真机三轮：飞行收尾的两个独立成因（用户 2026-10-01 02:44 报"效果更差"之后）

去程能飞了之后，读数变成两条新的观感账：**「飞入后闪一下才归位」** 与 **「然后图立刻消失只剩背景模糊，连下面的栏都没了」**。
两条各有各的成因，不是一条盖一条：

**成因 A —— 整页消失：入场姿态拿了一个会翻的派生值当分支。**
`graphicsLayer` 那一条写的是 `if (canFly) alpha = fly.value else translationY = entrance.value * screenHeightPx`，
而 `canFly` 是**组合期派生值**（`payload != null && origin != null`）。飞行一结束就要把截帧交还
（`GalleryFlyIn.consume()`），`canFly` 当场翻回 false ⇒ 改走"抬页"那一支 —— 可飞这一程从没动过 `entrance`，
它还停在初值 `1f` = 一整屏位移 ⇒ 整页连底部那条工具栏被平移出屏，窗口 blur-behind 还在，
所以读起来是"只剩背景模糊"。与用户那句描述逐字对得上。
修法：入场姿态在 effect 里**闩锁**成 `fliesIn`（定一次不再翻），两个分支各自把 `alpha` 与
`translationY` **都写一遍**（不再依赖 GraphicsLayer 未写属性留不留旧值这种没查证的假设），
并且飞行那一支也把 `entrance.snapTo(0f)` —— 给另一支留一个 `1f` 的读数就是下一次翻车的引线。

**成因 B —— 闪一下：页面淡入占了整条飞行程。**
上一轮为了消掉"两份图"，把页面透明度从"另起一条 `tween(motion.short)`"改成直接挂 `fly.value`，
结果是**飞到一半时目的地已经半显形**，屏上仍是"正在飞的小图 + 半透明的大图"两份，只是比两条钟那版淡一点。
修法：页面淡入只占**飞行尾程 25%**，与飞行体的淡出写成互补的两半，两处共用同一个常量
`FLIGHT_FADE_FRACTION`（任何一刻两边 alpha 相加恒为 1，而这一段里飞行体已经压在落点那一框上）。
两侧不再各写各的 `0.25f` —— 那正是"漂回上一版"的引线。

## 十、首页「正在关注的画师」圆座改判（用户 2026-10-01 报"只有英文字母"）

**取证不是猜的**：从设备上只读拉了 `gallery_artist_follows.json` 与 `gallery_favorites.json`
（`_probe/follows/`），按这一栏原判据逐位算：

| 站 | 画师 | 名下收藏 | 原判据摆什么 |
| --- | --- | --- | --- |
| yandere | e-note | 0 张 | 首字母 |
| yandere | sakutaishi | 0 张 | 首字母 |
| gelbooru | setmen | 1 | 作品缩略图 |
| yandere | milkshake | 1 | 作品缩略图 |
| gelbooru | ohako_miyu | 1 | 作品缩略图 |
| yandere | tokenbox | 2 | 作品缩略图 |

根因不是加载失败，是**这一栏压根不摆头像**：批次 L · L11 定的卡面是"那位名下最近一张被收藏的图"，
所以**只关注、没收藏**的人必然落首字母座。而这一栏的语义是"人"，用户拿它跟详情页那张现取的脸一比，
读出来就是"缺图"。取法不是 bug（`tagsOf` 与小写比对那条链量过，能对上）。

**改判（用户选的档：全员现取头像）**：圆座次序 = 现取到的真头像 > 名下最近一张收藏 > 首字母座。
- 判据抽成 `GalleryFollowedArtists.faceOf(avatar, previewUrl)`，**先红后绿**（红的时候是
  `Unresolved reference 'faceOf'`），三条新用例钉"空串与空白都不算值"（老收藏里偶有空 `preview_url`，
  认了就是交出一张白圆 —— 与 `rowOf` 里那条同一口径）。
- 取法 `artistAvatarOnSite(context, site, name)`：站方外链 → `profilePlan` → pixiv / fanbox / mastodon。
  **用 `profilePlan` 而不是 `iconPlan`** —— 后者只留两枚展示用的胶囊，fanbox-only 的人会被截掉，
  而头像正是从那一档取的；少一枚图标不要紧，少一张脸就是 bug。
- 结果按 `站:名字` 记一次**进程内**缓存（空串 = "查过了，没有"，与"没查过"必须分得开）。
  刻意**不落盘**：批次 L 拍板过"头像地址会过期，存了就等于承诺离线也能看到"，那条理由今天不改；
  缓存只解决"切走再回画廊 Tab，为同一批人重发十几笔"。
- 代价如实记账：一位 2~3 笔、串行、进画廊首页一次；这一轮从 6 位起算约 12~18 笔，
  Coil 的图片缓存吃掉像素那一半，链路解析那一半靠上面那道进程内缓存。

**顺手量出来但本轮没动的一条**：这一栏在头像没回来之前摆的是**作品缩略图**，而它**不过屏蔽/分级那把**
（`VeneraCover(url = …)` 直接画）。改成"真头像优先"之后这个口子变小了（多数座位是脸），但没关死。
下一轮要不要给它上 `buildGalleryWall` 那把，单独拍。

## 十一、QA

- 判据层脱离 gradle 单跑：`bash _probe/l0/run-judgment-tests.sh` → **OK (69 tests)**
  （本轮 +11 条 profile 判据、+6 条 danbooru 记录解析；先红后绿，红的时候是 10 failures）。
  `faceOf` 那三条**不在这个 runner 里**：它所在的 `GalleryFollowedArtistsTest` 依赖 `GalleryFavorite`，
  而那个类在 `GalleryFavoritesStore.kt` 里、文件 import 了 `android.content.Context` ⇒ 脱离 gradle 编不动。
- 全量：`./gradlew :app:testDebugUnitTest` → **572 tests / 0 失败 / 0 错误**（本轮起点基线 569，
  加的 3 条是 `faceOf`；只涨没红）。
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL，已装 PJZ110（`8bfdaeb5`），日志缓冲已清。

## 十二、待办

1. **撤临时探针**（`GalleryFlyIn` 三条 `FlyProbe` + `GalleryPostScreen` 三条：`mode=`、`stage=…waited=`、
   `overlay composed`）—— 等这版飞行的观感过了再撤，撤之前它是唯一能读"到底走的是哪一支"的东西。
2. 平台图标资产（#111）。
3. 真机验收：hero 那一对飞不飞、落地还闪不闪（闪则按 §一 那条换 `sharedBounds`）、
   飞行结束后页面还在不在（= 本轮 §九 成因 A 的正身）、收藏页点 tag 的落点、画师行可点性、
   介绍页的返回只退一次、首页那栏几位圆座各是什么、深浅两色 × 玻璃/SOLID 两档各截一张。
4. **返回程反向动画**：已报价、红线是"打码命中的卡不能飞"，等拍板才动代码。
5. 首页那栏的作品缩略图要不要过屏蔽/分级那把（§十 末条）。

## 十三、交接：hero 这一路现在的真相（2026-10-01 12:00，交给下一个 AI 前写的）

**一句话现状**：三档姿态那版已装机（PJZ110 `8bfdaeb5`）**但还没被用户验收**；
它改的是"为什么永远等不到落点"这一条根，前面四轮全部在读数上被推翻过，所以下一位**先读真机日志再动代码**。

### 1. 机制：跨 Activity 做不了真共享元素，所以自建一层，两样输入自己递

大图页是**独立 Activity**（`GalleryPostActivity`，blur-behind 32dp + `Theme.Venera.GlassOverlay`），
`openGalleryPost()` 用 `makeCustomAnimation(0, 0)` 把系统**打开**动画压掉。
平台没有跨窗口的"共享图像"能力（本机 SDK `android.window` 里只有启动图那套，实测过 33~36），
经典 View scene transition 又要求真的 View 带 `transitionName`（卡片是 Composable，框架会去搬整个 `ComposeView`）。
所以飞行体自己画：**点击那一刻向窗口要卡片那一块像素 + 递窗口坐标矩形**，对面那页从矩形原位飞到画面框位置。

- `gallery/ui/GalleryFlyIn.kt` —— 载体。`capture(view, cardBounds)` 走 **`PixelCopy.request(window, …)`**（异步），
  状态四档 `GalleryFlyInStage{NONE,WAITING,READY,FAILED}`；`payload`=那一帧像素，`origin`=卡片窗口矩形（**同步先给**）。
  为什么不用 `view.draw(canvas)`：卡面上的液态玻璃走 `RuntimeShader`，软件 canvas 回放不了，
  真机抛 `IllegalArgumentException: Software rendering doesn't support RuntimeShader` 且被 `runCatching` 静默吞掉 ——
  那是"点墙上的图永远不飞"的第一条死因（另一条独立死因见 §二）。
- 页内那对（首页预览行 ↔ 每日热门二级页）走**真**共享元素：`Modifier.coverSharedElement(galleryCoverKey(post.uid))`
  + `Navigation.kt` 里 `GalleryRoute`/`GalleryDailyRoute` 各包一层 `CoverTransitionHost`。
  key 必须两端逐字同串（用 `post.uid`，**不能用 `id`** —— 两站各自编号会撞）。用户已确认这一对**可用**。

### 2. 大图页的入场姿态：`Entrance{PENDING,FLY,RISE}`，只在 effect 里定一次

`GalleryPostScreen.kt` 里那一处 `graphicsLayer` 现在按三档走，**每档把 `alpha` 与 `translationY` 都写一遍**：

| 档 | alpha | translationY | 用途 |
| --- | --- | --- | --- |
| `PENDING` | 0 | 0 | 等落点。**站在原位、只是全透明** |
| `FLY` | 尾程 25% 淡入 | 0 | 与飞行体淡出互补，相加恒为 1 |
| `RISE` | 1 | `entrance × 屏高` | 退路（深链/收藏/反搜、截帧失败、落点超时） |

深链那一档在 effect 开头**短路**（`GalleryFlyIn.origin == null` 直接抬页，不等任何落点）。

### 3. 四轮读数、四条被推翻的假设（这段是本轮真正的产出）

| 我当时押的 | 真机读数 | 结论 |
| --- | --- | --- |
| 「切非玻璃档就能飞」 | 不飞 | 错。`view.draw` 有两条独立死因，玻璃只是其中一条 |
| 「两条钟并行 = 目的地提前显形」 | 单钟仍闪 | 半对。真正的闪是**落点是 0×0**，飞行体缩向屏幕左上角 |
| 「整页消失是 `consume()` 翻了 `canFly`」 | 成立 | 对。姿态必须闩锁，不能拿组合期派生值当分支 |
| 「落点要 650ms，预算给短了」（我把预算 400→600 还不成） | `landing size=1016x1576` 恒定，而 `window` 高只有 99 | **错**。`boundsInWindow()` 报的是**裁到窗口可见部分**之后的矩形 —— 页面整片在屏幕外时落点必然是 `0×0`。**病是姿态，不是预算** |

⇒ 教训写进代码头注了：**"落点来得晚"这个读数本身是姿态错误的产物**，别拿它当基线去调超时。

### 4. 还欠什么（按优先级，位置都精确到行）

1. **验收三档姿态版**：请用户点墙上的图。要读的指纹是 `mode=PENDING → mode=FLY` 与
   `first landing rect=…` 的时刻差（修对了应在 20~40ms 内，而不是 650ms）。
2. **撤临时探针**（9 处，全在 `GalleryPostScreen.kt`，tag 都是 `FlyProbe`）：
   约 401 / 431 / 447(`flight end`) / 460(`consume`) / 478(`state`) / 500(`first landing`) /
   878(`overlay composed`) / 1098(`landing size`) / 1156(`tier`)。
   ⚠️ `GalleryFlyIn.kt` 里的 `Log.w` **不是探针**，那是失败留话（降级路径必须留话这条规矩），别一起撤。
3. **返回程 hero**（用户已点名要做，设计已定、未开工）：离场那一刻对画面那一框做一次 `PixelCopy`，
   **像素回来才 `finish()` 并压掉系统返回动画**（约一到数帧延迟；截不到就照旧走系统动画，绝不硬切）。
   落点用卡片**当前还在屏上的实时矩形**（`onGloballyPositioned` 每帧回写），卡片被滚走或换过一批就找不到 → 不飞。
   红线：**打码命中的卡两边都不飞**。未拍板的一条：系统返回/侧滑手势这一路要不要也接管
   （接管才覆盖全部出口，但预测式返回的手势预览可能被 `overridePendingTransition(0,0)` 切一下）。
4. 平台图标资产（#111）：枚举与标签已补齐（`GalleryArtistLinks` 加了 INSTAGRAM/TUMBLR/YOUTUBE + `chipLabel`），
   `scripts/build_platform_icons.mjs` 与图未做。
5. 首页那栏在头像没回来前摆的是**作品缩略图**，它**不过屏蔽/分级那把**（`VeneraCover(url=…)` 直接画）。
   头像优先后口子变小但没关死 —— 要不要上 `buildGalleryWall` 那把，单独拍。

### 5. 别踩的（这一轮的规矩，不是我加的）

- `Navigation.kt` 是保护域，本轮豁免**只给了两处**：那两个 `CoverTransitionHost` 包裹 + 宿主层一条
  `LaunchedEffect(GallerySearchHandoff.pending)`（④ 点 tag 的落点）。**不动** `VeneraTab` 枚举、路由映射、顶栏齿轮入口。
- `MainActivity.kt` / `VeneraApp.kt` 是 dsh 的（首屏主线程 IO 异步化），避让。
- 判据层先写失败用例；`faceOf` 那三条**不在** `_probe/l0/run-judgment-tests.sh` 里
  （`GalleryFavorite` 所在文件 import 了 `android.content.Context`，脱离 gradle 编不动）。
  QA 基线：runner `OK (69 tests)`、全量 **572 / 0 失败 / 0 错误**。
- 工作区是多 AI 共享的：`git status` 里那一大堆 M **不是本轮的**，提交要按轮挑文件。

---

## 十四、飞行体起点改判：从"整张卡"改成"封面那一块"（2026-10-01 12:00，用户报"包裹还会闪一下"）

**用户报的现象**（附真机截图）：卡片整个飞进大图页之后，"内容包裹"还是闪一下。
截图那一帧能读出关键：屏上是**被放大到画面框的整张卡** —— 图片外面一圈卡片圆角面板，
下面还拖着一条放大的署名行（"鸣潮" + 头像），然后它整体消失、只剩图。

### 成因：起点与终点不同构

飞行体的两样输入里，**矩形那一头取错了块**：

| | 取的是哪一块 | 实际形态 |
| --- | --- | --- |
| 起点（`capture` 的入参） | `GalleryPostCard` 里 `Card.onGloballyPositioned` | **整张卡**：封面 + 署名行 + 卡片面板圆角 |
| 落点（`imageBounds`） | `GalleryViewerPage` 里按原图比例定出来的画面框 | **只有图**（圆角 `shape.large`、无署名行） |

卡宽约半屏、画面框约一屏 ⇒ 飞行末段把整卡像素**放大近两倍**铺进画面框：署名行被放大成"大字 + 大头像"，
卡片那圈面板圆角也被一起放大。交棒那一刻整圈"包裹"消失 —— 这就是那个"闪"。

一句话判据：**共享元素的起点与终点必须同构**（同一比例的一张图 → 同一比例的一张图）。
页内那对真共享元素本来就守这条（`coverSharedElement` 挂的是**封面**，不是卡），跨 Activity 这一对漏了。

### 改了什么（两处起点 + 一处补判据）

1. `gallery/ui/GalleryScreen.kt` · `GalleryPostCard`：起点矩形从 `Card` 移到封面
   （`VeneraCover` 的 `onGloballyPositioned`），变量 `bounds` → `coverBounds`。
2. `gallery/ui/GalleryHomeSections.kt` · `GalleryPosterRow`：同一条口径（点的是那一行横卡的封面）。
   `GalleryArtistProfileScreen` 复用 `GalleryPostCard`（`internal`），自动跟上，没动它。
3. **顺带补上的一层**：起点与落点**比例并不总是同一档** —— 墙上的卡把比例夹在 `0.4~2.5`
   （防一张横长条把整列撑出屏幕），预览行那条横卡的封面是**固定 124×170**，而落点框按原图比例定。
   老写法把源位图整张**拉伸**到落点框：比例一致时对，不一致时整块内容被拉扯变形、交棒再把形状还回来，
   还是"闪"。改成 **cover 语义（保持像素比例、居中裁切）**，算式抽到判据层
   `gallery/domain/GalleryFlyGeometry.kt` 的 `coverSourceRect`。
   **比例一致时算出来的就是整张位图，与老写法逐像素等价** —— 这条由用例钉着，所以墙上卡的大多数行为没变。

### QA

- 判据层：`bash _probe/l0/run-judgment-tests.sh` → **OK (77 tests)**（基线 69，本轮 +8；
  `GalleryFlyGeometry.kt` / `GalleryFlyGeometryTest.kt` 已加进 runner 那三张表）。
- **先红验证做过**：把 `coverSourceRect` 的 `maxOf` 换成 `minOf`（= 错的口径）跑 runner → 2 条红
  （`目标框比源更宽时裁上下居中` / `目标框比源更高时裁左右居中`），改回即绿。用例不是摆设。
- 全量：`./gradlew :app:testDebugUnitTest` → **580 / 0 失败 / 0 错误**（基线 572）。
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL，`app-universal-debug.apk` 交用户真机验收
  （agent 不跑 adb 自验，2026-09-26 口径）。

### 这一轮**没有**动的东西（免得下一位以为漏了）

- 探针**没撤**（§十二 第 1 条那 9 处 `FlyProbe`）：这次改的正是"起点取哪一块"，而
  `FlyProbe` 的 `origin=` 与 `overlay composed start=` 两条恰好能读出它 ——
  **改对了的指纹是 `origin` 的高度 ≈ 卡宽 ÷ cardRatio（≈0.73 那个竖封面），不再是整卡高**。验收过一轮再撤。
  `GalleryFlyIn.kt` 里那三条 `Log.w` 是失败留话，不是探针。
- `Entrance` 三档姿态、`FLIGHT_FADE_FRACTION` 的互补淡入、`consume()` 的时机一律没动。
- 打码卡的飞行判据没动：起点像素仍是 PixelCopy 截的**屏上**像素，遮罩在不在屏上决定它在不在位图里。
- 预览行那一档的"封面框 124×170 与图上真实比例无关"是排版拍板的结果（固定高度那一行才齐），
  本轮没有去动它 —— 比例差得远时飞行体按 cover 裁切过渡，交棒读起来是"补回被裁的部分"而不是"换了形状"。

---

## 十五、真机第二轮（2026-10-01 12:09）：飞入"还是闪几下"的两处成因 + 返回程 hero

### 1. 先量，再改（两张截图逐像素量出来的）

用户给的 12:09 那两张截图，用 `_probe/fly_measure_2026-10-01.py`（行/列梯度能量投影片找块边界）
与 `_probe/fly_corners_2026-10-01.py`（裁四角放大）量的读数：

| 截图 | 画面块（窗口 px） | 尺寸 | 宽高比 | 四角 |
| --- | --- | --- | --- | --- |
| 12:09:24（到位态） | x[31,1047] y[334,1982] | 1016×1648 | 0.616 | **圆角** |
| 12:09:27（飞行中） | x[31,725] y[306,1429] | 694×1123 | 0.618 | **直角** |

两条结论：

1. 飞行体那一帧的比例与落点**同一档**（0.618 vs 0.616），且它的底边**没有署名行** ——
   说明"起点取封面、不取整卡"（§十四）已经生效，**用户装的是 12:07 那个包**。
   （到位态量到的 1016 宽与 §三 那条 `landing size=1016x1576` 读数逐位吻合，量法可信。）
2. **飞行体四角是直角，落到位四角是圆角** ⇒ 交棒那一刻有一次"方角 → 圆角"的跳变。

### 2. 闪的成因（两处，各自独立）

**成因 1 — 飞行体没裁圆角。**
`drawBehind` 里 `drawImage` 画出来的是矩形，而它要接替的那一框是
`clip(RoundedCornerShape(tokens.shape.large))`。形状不一致 ⇒ 交棒跳一下。
修法：飞行体走同一条圆角，半径按**当前宽 ÷ 落点宽**等比缩放 —— 落到终点时**逐像素等于框的圆角**。
（`tokens.shape` 是 @Composable 的 getter，绘制期读不到，所以半径的 Dp 在组合期先取出来，
`.toPx()` 留在 DrawScope 里 —— 这一条编译期就报错过，别再写回去。）

**成因 2 — 交棒交到"比垫帧还糊"的开门档。**
`onReadyChange`（= 交还垫帧那一刻）的老口径是"快照档**或**中档任一落位"。
而两站的快照档（开门档）都特别小：yande.re 的 `preview_url` 实测 **300×212**（`GalleryPost.kt` 头注那张表）。
把它铺进一屏宽的画面框要放大三倍多，**比手里这张垫帧（卡封面那一块的屏上像素）还糊** ——
于是交棒读起来是"先变糊一下"、中档回来再"变清晰一下"，正是用户说的"闪几下"里的一份。
修法：`onReadyChange` 与垫帧的撤销条件**都只认中档**（`sample`，这一屏的成品档）；
它取不到时 `onError` 也会把 `largeSettled` 置真，所以不会把位图永远攥着。

### 3. 返回程 hero（用户已点名，本轮做完）

与去程是同一套机制的镜像，两样输入自己现取：

| | 来源 |
| --- | --- |
| **起点** | 当前页画面框（`frameBounds[uid]`）—— 离场那一刻对它做一次 `PixelCopy` |
| **落点** | 墙上那张卡**此刻**的封面矩形（`GalleryFlyIn.cardBoundsOf(uid)`） |

- **落点表** `GalleryFlyIn.cardBounds`：一级的卡片在布局时按 `uid` 回写；被 LazyGrid 回收时
  卡片自己 `forgetCardBounds` 撤掉（留着一条过期矩形比"这一次不飞"糟得多）。
  **打码卡不回写** ⇒ 红线"打码那张两边都不飞"变成**结构性保证**（表里压根没有它），
  不靠调用方记得判。
  为什么敢读后台那一屏的值：大图页压上来之后 MainActivity 只是 `onStop`，View 树与布局原封不动
  （人也没法在大图页里滚那一面墙）—— 那份读数就是"离开那一刻的位置"，正好是返回要的落点。
  Activity 真被销毁重建时，整棵 Compose 树 dispose 会把表清空，随后新布局重新回写，没有幽灵值。
- **`imageBounds` 一份改成 `frameBounds` 按 uid 一份**：去程要"打开那一张"的落点，
  返回程要"**当前页**"的起点 —— 翻过页之后这两个不是同一张（框的尺寸还随比例变）。
  pager 本来就会预组左右邻页，按 uid 存才分得清谁是谁。
- **先截帧、后 finish**：像素到手才播动画；截不到（超出窗口 / 没 surface）就照旧 `finish()`
  走系统那套动画 —— **绝不硬切**。
- **离场第一件事是摘掉窗口模糊**（`clearFlags(FLAG_BLUR_BEHIND)`）：图要飞回的是**清晰**的那一屏，
  落在一张糊掉的卡片上不算"回去"。基类挂模糊走的是 `addFlags`，清掉即回到透明窗口底；
  "要重进页面才恢复模糊"在这里无所谓 —— 本页马上 finish。状态栏图标同理跟着翻回来
  （底下那屏是浅色主题，浅色图标压上去等于没有）。
- **画在本页窗口里**，不去碰 `MainActivity` / `VeneraApp`（那两个是 dsh 的）。
  离场 = 页面与压暗层在**首程**淡出（与去程尾程那半共用同一个 `FLIGHT_FADE_FRACTION`）
  + 飞行体从画面框飞到卡片封面；动画播完才 `finish()`。
- **飞行体全程不淡入也不淡出**：起点是屏上那一帧的真实像素（与它盖住的那张图**逐像素相同**），
  终点正好是卡片封面那一块，finish 之后卡片接住 —— 两头都无缝，中间加淡入淡出反而会露出身后的东西。
- **接管系统返回 / 侧滑**（`BackHandler(enabled = !infoOpen)`）。§十三 第 4 条那条"未拍板"
  由用户在这一轮拍板（"把返回的 hero 共享手势也一起做了"）。
  代价如实记账：预测式返回原本那半"边滑边看到整页缩小"的系统预览没有了，
  现在统一是"松手之后图飞回卡片"。
- **下滑关闭不走这条**（`closePage` 保持原样）：那时页面已经跟着手指滑出屏幕，
  再让图从屏幕外飞回来是自相矛盾的；那条仍由系统返回动画收尾。
- 探针 +2 条：`FlyProbe exit start=… target=…` / `exit end`（与那 9 处同批，验收完一起撤）。

### 4. QA

- 判据层：`bash _probe/l0/run-judgment-tests.sh` → **OK (77 tests)**（本轮判据层没动）。
- 全量：`./gradlew :app:testDebugUnitTest :app:assembleDebug` → **BUILD SUCCESSFUL**，
  **580 / 0 失败 / 0 错误**，`app-universal-debug.apk` 已交用户真机验收（agent 不跑 adb 自验）。
- 量截图那两个脚本落在 `_probe/` 下；裁出来的角图在 `_probe/fly_corners/` ——
  **是真人内容，别提交、别展示**。

### 5. 请在真机上过这几条（下一轮就看它）

1. 点墙上的图：交棒还闪不闪（本轮那两处成因都在这一跳上）。
2. 按返回 / 侧滑返回：图应该**飞回墙上那张卡**，不是瞬间切回去。
3. 翻一两页再返回：应飞回**翻到的那张**对应的卡（它还在墙上时）。
4. 深链 / 收藏点进来再返回：落在下面那一屏对应的那张卡上。
5. 打码那一张：进与出**都不飞**（两边的红线）。

## 十六、真机第三轮（2026-10-01 12:22）：那一下"闪"**不在飞行体上，在新窗口的第一帧**

用户第三次报"打开图片的时候还是会闪下"（这一轮明确"返回没问题"）。这次没有再猜：
设备连着，直接**录屏 + 逐帧量化**，一次定位。

### 1. 读数（修复前）

| 时刻 | 亮度 | 帧间差 | 说明 |
| --- | --- | --- | --- |
| t=1.200~1.433s | `174.0` | **0.00** | 点击后主界面**一动不动**（250ms） |
| **t=1.450s** | **173.8 → 106.1** | **67.94** | ← **一帧之内暗掉 39%** |
| t=1.45~1.78s | 106 → 147 | 8~20 | 图飞过来的 350ms |
| t≥1.80s | 147.2 | 0.00 | 稳定态 |

判据就一句：**"闪"= 单帧之内的整屏亮度暴跌**。

这三行读数否掉了前两轮的全部方向：

1. 点击后 250ms 主界面**完全静止** ⇒ 不是系统窗口动画（`makeCustomAnimation(0,0)` 生效），
   也不是主界面自己在动。
2. 暴跌**只有一帧**，而随后的飞行有 350ms 的连续运动 ⇒ 病在"运动开始**之前**"。
   飞行体、交棒、档位全都无辜 —— 前两轮改的那三处（起点矩形取封面、飞行体圆角、交棒只认中档）
   **都改对了，但不是它**。
3. 那 250ms 静止期屏上仍是**清晰的主界面** ⇒ 新窗口第一帧显示的就是它，
   而它一出现就把幕布（0.45 黑罩 + 32dp 系统模糊）**整块**盖下来 —— 而图还要过两帧才起飞。

### 2. 改了什么

判据一句话：**入场第一帧必须与点击前那一帧看起来一样**。

1. `GalleryPostScreen` · 压暗那一层改成**跟着图起飞一起渐入**（`backdropEntryAlpha()`）：
   `PENDING` = 0 / `FLY` = `fly.value / BACKDROP_ENTRY_FRACTION` / `RISE` = `1 - entrance.value`。
   与离场那半相乘（`exitFadeAlpha()`）—— 进与出不会重叠，但乘起来两条路都不必假设对方的状态。
2. `GalleryPostScreen` · 窗口模糊也**一并推迟**：新参数 `onEnterStart`，在"图起飞 / 整页抬起"
   那一刻回调一次（三条分支各一次，幂等）。
3. `VeneraSubActivityBase` · 新增 `deferredBlurBehind` + `armBlurBehind()`：推迟型页面在
   `onStart` 不挂 `FLAG_BLUR_BEHIND`，等页面说"可以了"再挂。
   ⚠️ `blurBehindArmed` 的初值**只能在 `onCreate` 里**按 `deferredBlurBehind` 定 ——
   写成属性初始化式是在**基类构造期**求值的，那时子类那个 `override val` 还没初始化，
   读到的必然是 `false`，推迟会**静默失效**（症状与原样一模一样）。
4. `GalleryPostActivity` · `override val deferredBlurBehind = true` + 把 `armBlurBehind()`
   接到 `onEnterStart`。

### 3. 旋钮是实测调出来的（`BACKDROP_ENTRY_FRACTION`）

第一版取 `0.35`，录屏量出来幕布只用 **67ms** 就落了满 —— `spring(StiffnessMediumLow)`
起步近似线性，临界阻尼 `x(t)=1-(1+ωt)e^(-ωt)`（ω=√400=20）在 t=0.067s 处已经到 0.387。
**67ms 的渐变仍然是"闪"**。档位加深到 `0.8` 之后：

| 版本 | 幕布落下用时 | 单帧最大帧间差 | 亮度轨迹 |
| --- | --- | --- | --- |
| 修复前 | **16ms（1 帧）** | **67.9** | 173.8 → 106.1 |
| 0.35 | 67ms（4 帧） | 43.1 | 175.5 → 106.5 |
| **0.8** | **133ms（9 帧）** | **31.0** | 174.0 → 111.9 |

0.8 那一版是递减步长的缓出曲线（-19.9, -11.7, -9.6, -7.2, -4.2, -3.1, -3.5, -1.4），
**没有单帧突变**。再嫌急就继续加深这个数（`1.0` ≈ 整个 317ms 行程都在降）。

### 4. 怎么复现这套取证（下次调动画直接抄）

```bash
# 1) 清 log + 录屏 + 点一下（放在同一条 shell 里，保证日志与画面可对齐）
adb logcat -c
adb shell 'screenrecord --time-limit 8 --bit-rate 16000000 /sdcard/x.mp4 & sleep 1.5; input tap <x> <y>; sleep 7'
adb pull /sdcard/x.mp4 _probe/x.mp4
adb logcat -d -s FlyProbe:* > _probe/x.log
# 2) 逐帧量化（脚本只读数值，不读画面内容）
python _probe/fly_recording_analyze.py _probe/x.mp4
```

四个坑：

- `screenrecord` 是**变帧率**的（静止不编码，实测报 5.82fps），拆帧**必须**走
  `-vf "fps=60,..."` 做 CFR 转换；`-vsync 0` / `-fps_mode passthrough` 会因为时间戳不单调
  直接 `Conversion failed!`。
- 本机**没有**系统 ffmpeg：`pip install imageio-ffmpeg`，再用 `imageio_ffmpeg.get_ffmpeg_exe()`。
- Git Bash 会把 `adb shell ... /sdcard/x.xml` 的路径转成 `C:/...`：命令前加 `MSYS_NO_PATHCONV=1`。
- 点哪张卡：`uiautomator dump` 后读 `content-desc` 里带 `#` 的节点（封面上的
  `"<站名> #<id>"`），拿到的就是**封面那一块**的精确 bounds。

### 5. QA

- `./gradlew :app:testDebugUnitTest :app:assembleDebug` → **BUILD SUCCESSFUL**，
  **584 / 0 失败 / 0 错误**。
- 入场时序（修复后真机读数）：`PENDING 31ms → FLY → flight end 298ms → consume`。
- 临时截图与录屏（真人内容）已**全部删除**；只留纯数值的分析脚本与 trace 日志。

### 6. 还欠

- 那 9 处 + 2 处 `FlyProbe` 探针仍在（用户真机验收完再一起撤）。
- 点击后那 250ms 的 Activity 冷启动"没反应"还在 —— 不是闪，但"点了没有任何即时反馈"
  可以单独一轮考虑（卡片自身的按下态在这一跳里没被看见）。



## 十七、真机第四轮（2026-10-01 12:38）："闪"在**交叉淡入的钟挂在弹簧的百分比上**

用户第三次报："打开图片的时候还是会闪下噢，**不是背景，是图片本身**。"
这一条把上一轮（§十六）的结论限定住了 —— 那一下幕布是修对了，但用户看的**不是它**。
换了尺子：不再看整屏亮度，而是量**画面框里面**的东西。

### 1. 量法（脚本留在 `_probe/`）

| 脚本 | 量什么 |
| --- | --- |
| `fly_ghost_test.py` | 每帧与"最终稳定帧"在画面框内的差 + 平移搜索（找"错位的第二份图"） |
| `fly_ab_compare.py` | 修复前/后同一时间窗内"差 > 25 的像素占比"逐帧曲线（**A/B 判据**） |
| `fly_sharp_regions.py` | 用**锐度**分离图片块与模糊背景（图片清晰、blur-behind 的背景糊） |
| `fly_contact.py` / `fly_contact_full.py` | 逐帧联络表（只裁画面框 / 整屏），看图用 |

`screenrecord` 是变帧率，静止时不编码 —— 拆帧必须 `-vf fps=60` 或按 `-ss` 单抽，
不要信 ffmpeg 报的"5.8 fps"。

### 2. 病因（两条，同一个根）

根：**页面的淡入挂在去程那条弹簧的百分比上**（`fly ≥ 0.75` 起淡），飞行体在同一个窗口里淡出。
它假设"这一段里飞行体已经压在落点那一框上" —— **弹簧的百分比不是路程**。
真机读数：`origin=496×803 → target=1016×1644`，`fly=0.75` 时飞行体还在**路程的 75%**，
离落点约 130px、面积小 13%。于是：

1. **重影**：同一张图两份同时可见 —— 页面那份在终点、飞行体那份在半路。两层都是 0.5 时，
   屏上就是两个错位的半透明副本。
2. **暗一下**：飞行体画在页面**之上**，总不透明度是 `a_body + (1-a_body)·a_page`。
   取 `a_page = f`、`a_body = 1-f` 时等于 `1-f+f²`，在 `f=0.5` 处掉到 **0.75** ——
   后面那层压暗的幕布从图里透上来。

两条合起来就是用户说的"**图片本身闪一下**"。

### 3. 判据（A/B 曲线，本轮真正的凭据）

画面框内"与最终帧差 > 25 的像素占比"：

| t(s) | 1.48 | 1.50 | 1.52 | 1.54 | 1.56 | 1.58 | 1.60 | 1.62 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 修复前 | 56.2 | **51.4** | **66.8** | **66.9** | **62.0** | 54.7 | 28.1 | 10.6 |
| 修复后 | 63.0 | 54.7 | 48.2 | 39.3 | 33.7 | 26.6 | 22.1 | 18.4 |

修复前**不降反升**（51 → 67），那一段就是重影 + 变暗；修复后**全程单调下降**，零回弹。
另：重影峰值落在 `fly=0.875`（交叉中点），与 `1-f+f²` 在 `f=0.5` 取最小值**逐位吻合**。

### 4. 改法

- **交棒不再与飞行重叠**。飞行全程不淡任何东西（飞行体就是不透明的起点像素，它本来就是那一帧屏上像素）。
- 新加一档 `handoff`（`Animatable`），**飞完才开始**，`HANDOFF_MS = 160ms`：
  前半程页面（含 chrome）`0 → 1`，飞行体**恒为 1**（图那一块被不透明的飞行体盖着）；
  后半程页面**恒为 1**，飞行体 `1 → 0`（此刻淡的只是"糊 → 清晰"，总不透明度恒为 1）。
- 算式的**判据层落点**：`gallery/domain/GalleryHandoff.kt` 的
  `handoffPageAlpha` / `handoffBodyAlpha` / `handoffCoverage` + `GalleryHandoffTest`（5 条）。
  守的是"**两层叠起来恒为不透明**"这一条 —— 任何"两层同时半透明"的写法都会让它掉下 1。
  做过先红验证：把 `handoffBodyAlpha` 改成 `1-p`（两层重叠）→ 当场 2 条红。
- `consume()` 加第二道门 `handoffDone`：老写法只看 `flightDone`，于是位图紧跟飞行结束就被清
  （真机读数 `pad=true` 只活 **10ms**）—— 一帧糊、又立刻变清晰，那是**第三个**"闪"。
- 飞行体只在 `entranceMode != RISE` 时画：它现在整程不透明，抬页那一档若不排除，
  会把"卡片封面僵在原位"亮出来。
- **踩过的坑记一笔**：`handoff` 必须在**起飞那一刻** `snapTo(0f)`。归零写晚一句，
  飞行体一开场就是 `handoffBodyAlpha(1) == 0` —— 全透明，屏上只剩幕布。

### 5. QA

- 判据层：`bash _probe/l0/run-judgment-tests.sh` → **OK (86 tests)**（基线 77，+9）。
- 全量：`testDebugUnitTest` → **593 / 0 失败 / 0 错误**（基线 584）。
- `assembleDebug` → BUILD SUCCESSFUL。
- 真机时序（修复后）：`stage=READY waited=3ms → FLY → flight end 321ms → consume（+185ms）`。
- 临时截图与录屏（真人内容）已**全部删除**；只留纯数值的分析脚本与 trace 日志。

### 6. 还欠

- 11 处 `FlyProbe` 探针仍在（用户真机验收完再一起撤）。
- 上面那三条**都不是**"图加载慢"或"Activity 冷启动"—— 那两个仍然存在（点下后约 250ms 无反馈），
  但它们是"慢"，不是"闪"，别再往这个方向修。



