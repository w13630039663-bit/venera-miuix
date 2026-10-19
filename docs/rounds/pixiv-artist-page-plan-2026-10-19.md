# pixiv 画师页改造 —— 规划（2026-10-19 · 只动安卓手机 + 平板档）

> 用户原话：「改造下并接上 pixiv 源（目前点击画师要么闪退要么进网页），并改成点击画师头像进入画师页，
> 要求 pixiv 站要横图大图显示作者自己设置的大图，如果没有设置则用回第一张图，单独针对 pixiv 源加入画师的
> 插画/漫画切换，并加入收藏页面，这个页面是显示画师的公开收藏。先规划看下怎么单独对 pixiv 源改。」
>
> 配套 UI 方案（可直接打开看）：`pixiv-artist-page-2026-10-19.html`

---

## 0. 一句话结论

**新开一屏 `PixivArtistScreen`，只在 `site == PIXIV` 时被分派过去**；图库三站那屏一行不改。
取数全部走 pixiv 自己的两条腿（`www.pixiv.net/ajax/*` 匿名 + `app-api.pixiv.net/*` 带票据），
**绝不落回** `GalleryRankings.searchQuery` 那套 booru 查询语言 —— 那条腿对 pixiv 是 `throw`，
这正是今天闪退的根因。

---

## 1. 先说病：为什么现在「要么闪退、要么进网页」

两个入口，两条完全不同的岔路，今天同时存在：

### 1.1 闪退：画师介绍页一进去就抛

```kotlin
// app/.../gallery/ui/GalleryArtistProfileScreen.kt:163
val query = GalleryRankings.searchQuery(              // ← 进来第一件事
    site = site, filters = listOf(GalleryTagFilter(artistName)),
    ranking = GalleryRanking.ALL, anchor = null, todayUtc = today,
)
```

`searchQuery` → `suffix(site = PIXIV, ranking = ALL)` → `GallerySite.notWired("排行后缀")`
→ `UnsupportedOperationException`。**这一句在 `LaunchedEffect` 里、在 `when(site)` 分支之外**，
所以 `when (GallerySite.PIXIV -> Result.failure(...))` 那一段「诚实的失败」根本没机会跑到 ——
屏还没画出来就先炸了。三条入口都会踩到：

| 入口 | 文件 | 结果 |
|---|---|---|
| 卡片长按 ›「看画师」 | `GalleryScreen.kt:2851` | 闪退 |
| 作品页画师行 | `PixivWorkScreen.kt:779` → `PixivWorkActivity.kt:55` | 闪退 |
| 大图页画师行 | `GalleryPostScreen.kt:1546` → `GalleryPostActivity.kt:85` | 闪退 |

### 1.2 进网页：首页画师横排 / 搜索画师分栏

```kotlin
// app/.../gallery/ui/GalleryPixivHome.kt:661
internal fun openPixivUserPage(context: Context, userId: Long) { …CustomTabs… }
```

`GalleryScreen.kt:1570` 与 `:1786` 都调它。这条路**不崩**，因为当时确实没有 pixiv 画师页
（那一句注释写着「现造一屏要再接一条 `/v1/user/illusts`」）。这次就是把它补上。

> 顺带一个隐患：`EXTRA_ARTIST` 传的是**画师显示名**（`userName`），而 pixiv 的身份是 `user_id`。
> 显示名可改、可重名，拿它当身份去取数会串人。新屏一律以 `source` 里的 `users/{id}` 取号为准。

---

## 2. 边界：pixiv 一屏，图库三站那屏零改动

| 层 | 图库三站 | pixiv（本次） |
|---|---|---|
| 宿主 | `GalleryArtistProfileActivity`（同一个） | 同一颗，`onCreate` 后按 site 分派 |
| 屏 | `GalleryArtistProfileScreen`（不动） | **新增** `PixivArtistScreen` |
| 查询语言 | `GalleryRankings.searchQuery` + `order:score` 后缀 | pixiv 自己的 `ajax/user/*`，**不碰那一套** |
| 头图 | 热门作品首张（糊 10dp 当氛围底） | 作者自设背景 `background.url`（1920×960 档，**清晰横图**） |
| 分栏 | 只有「按分数排的热门作品」一排 | 插画 / 漫画 / 收藏 三段 |
| 关注账 | `GalleryArtistFollows`（站+名） | 同一份账（**不新开第二本**） |

`GalleryArtistProfileScreen` 里只做一件事：把 `GalleryRankings.searchQuery` 那句**移进 `when` 的
非 pixiv 分支**（或直接 `if (site == PIXIV) return@PixivArtistScreen`）。**这是最小且必要的修**：
在分派做对之后，它仍然必须搬，否则任何漏网调用点还会炸。

---

## 3. 取数：三条匿名腿 + 三条票据腿 + 两条写腿

今天的实测（2026-10-19，本机直连 `www.pixiv.net`，不带任何头即可读 JSON）：

| # | 用途 | 端点 | 票据 | 读数 |
|---|---|---|---|---|
| 1 | 头部：名字 / 头像两档 / **自设背景** / 简介 / 外链 / 关注数 / 会员位 | `GET /ajax/user/{id}?full=1` | 匿名 | ✅ 200。`body.background.url` = `…/c/1920x960_80_a2_g5/background/img/…`（**天然横图**）；`body.imageBig`(170px)、`body.comment`、`body.social.{twitter,pawoo}`、`body.webpage`、`body.following`、`body.premium` |
| 2 | 作品索引：插画 / 漫画 id 表 + 公开收藏总数 | `GET /ajax/user/{id}/profile/all` | 匿名 | ✅ 200。`body.illusts{id:null}`、`body.manga{id:null}`、`body.bookmarkCount.public.illust` |
| 3 | 作品批次：按 id 取卡上那 9 格 | `GET /ajax/user/{id}/profile/illusts?ids[]=…&work_category=illust\|manga&is_first_page=0` | 匿名 | ✅ 200。单批 24 枚实测通过；每格给 `url`(250×250)/`width`/`height`/`pageCount`/`xRestrict`/`aiType`/`tags[]`/`createDate` |
| 4 | **作者的公开收藏** | `GET app-api /v1/user/bookmarks/illust?user_id={他}&restrict=public&filter=for_android` | 需票据 | ⚠️ 形状由 pixivpy `aapi.py:user_bookmarks_illust` 与站内现有 `bookmarkIllusts` 互证；**待真机验证** |
| 5 | 计数：粉丝 / 作品 / 漫画 / 公开收藏 | `GET app-api /v1/user/detail?user_id=` | 需票据 | ⚠️ `profile.{total_follow_users,total_illusts,total_manga,total_illust_bookmarks_public}`；待真机验证 |
| 6 | 兜底：按号整页取作品 | `app-api /v1/user/illusts?user_id=&type=illust\|manga` | 需票据 | ⚠️ 第 3 条挂了再走它；同样待验证 |

**一条否掉的腿**：`www.pixiv.net/ajax/user/{id}/illusts/bookmarks` 匿名 **400**（今日实测三次，
不同 user_id / limit / lang 全 400）—— 所以**「收藏」不能匿名读**。这不是缺陷，是站方边界，
屏上必须照实说（见 §6 的 F 档）。

> 分页：第 2 条一次给全量 id 表（本机实测一位 150 件的画师一次全给），第 3 条按 **24 枚一批**
> 往下取 —— 这就是"无限下拉"的游标，不自己拼 offset。

---

## 4. 导航：头像即入口

用户点名的规则：**点画师头像 → 进画师页**。六处调用点，逐处表态：

| # | 位置 | 今天 | 改成 |
|---|---|---|---|
| 1 | pixiv 墙卡片署名行的 24dp 圆头像（`GalleryCardCaption`） | 不可点 | **可点 → 画师页**（图库三站没有头像，天然不受影响） |
| 2 | 卡片长按菜单「看画师」 | 画师页（pixiv 会炸） | 画师页（分派后不炸） |
| 3 | 作品页画师行（头像 + 名字整行） | 画师页（pixiv 会炸） | 画师页 |
| 4 | 首页「画师」横排（已关注 / 发现） | 打开站方网页 | **画师页**（`PixivUser.id` 现成） |
| 5 | 搜索「画师」分栏每一行 | 打开站方网页 | **画师页** |
| 6 | 收藏 / 历史里的 pixiv 条目 | 大图页 → 画师行 | 同 #3 |

`openPixivUserPage` **不删**：新屏里留一枚「在 pixiv 打开」（那是"看站方原页"的正当出口：
看小说 / 看约稿 / 看全部收藏私密档）。只是它不再是点画师的唯一去处。

传参保持现有三枚 extra 不变（`EXTRA_SITE` / `EXTRA_ARTIST` / `EXTRA_SOURCE`），
pixiv 侧从 `EXTRA_SOURCE` 用现成的 `GalleryArtistLinks.pixivUserId(source)` 取号 —— 零新契约。

**取不到号那一档**（老收藏记录没存 source）：不猜、不按名字搜（同名不同人是真实存在的，
`GalleryArtistLinks` 头注里那条「作品号不是用户号」是同一族的教训）。屏上只说一句真话 +
一枚「在 pixiv 搜这个名字」，进去自己认人。

---

## 5. 页面结构（编号与 HTML 方案一一对应）

1. **返回 pill** —— 悬浮在 hero 左上，滚动后补一层玻璃底（现有 `VeneraTopBarPill`）。
2. **hero 横图** —— 作者自设背景（站方已裁成 1920×960）；**没有设置就回退到首张插画**；
   两者都没有 = 一块 `surfaceContainerHigh` 卡面（不留半截实现）。
   高度按宽度算再夹：`clamp(宽 × 0.5, 196dp, 340dp)` —— 不再用定高（定高在 1891dp 上被拉成 9.6:1，
   那是 `artistHeroHeightWide` 记下的旧账）。
3. **头像 76dp** —— 压进 hero 38dp（负 margin），2.5dp **页面底色**描边环（圆压在照片上必须有环，否则边缘糊）。
4. **名字 + @账号** —— 名字吃 `display`（MD3 28 / MIUIX 32），账号 `caption` + `textTertiary`。
5. **关注** —— 38dp 高 pill，收在名字右端；已关注走 tonal（`surfaceContainerHigh` + 发丝描边），
   未关注走 `primary` 实底。**写的是站方那一份**，见 §5.5。
6. **数据行** —— 粉丝 / 插画 / 漫画 / 公开收藏（票据腿）；取不到的格退成「—」，不拿 0 顶。
7. **简介 + 外链** —— 简介 2 行 + 「展开」；外链 chips 复用 `PlatformBadge` + `GalleryArtistLinks`。
8. **三段切换** —— 插画 / 漫画 / 收藏，复用 `PixivSegmentedRow`（轨道 + 白胶囊）紧凑档 34dp。
9. **网格 + 页尾读数** —— 瀑布流（不钉 3:4）；列数吃墙上那把尺子（手机 2 / 平板自适应 ≥3，
   且尊重用户的列数设置）；分页到页脚自动续，失败只念页脚那一行、**不清屏**。

### 5.5 关注：接站方那一份（用户 2026-10-19 点名）

**今天两套账已经对不上，这是接站方顺带修掉的一个真缺陷：**

| 谁 | 读/写 | 键 |
|---|---|---|
| 作品页那颗「关注」`PixivWorkInfoPanel.kt:143` | **写本地** `ports.follows.toggle(PIXIV, author)` | 画师**名字** |
| 画师介绍页那颗 | **写本地** `ports.follows.toggle(PIXIV, artistName)` | 画师**名字** |
| 首页「画师 · 已关注」那一栏 | **读站方** `/v1/user/following` | `user_id` |

⇒ 在作品页点了「已关注」，回首页那一栏**不会多出这个人**（它只认站方），而本地账里多了一条
按名字记下的条目 —— 屏上两处对同一个人给出相反读数。接站方之后这三处**统一到 `user_id`**。

**写侧**（pixivpy `aapi.py` 与 pixez 同形，form-urlencoded，只要 Bearer）：
- 关注 `POST https://app-api.pixiv.net/v1/user/follow/add`，`user_id` + `restrict=public`
- 取关 `POST https://app-api.pixiv.net/v1/user/follow/delete`，只带 `user_id`

**读侧**：`/v1/user/detail` 的 `user.is_followed`（票据腿，与 `is_premium` 同一份答复 ——
`PixivAppParse.userPremium` 那条解析**扩成一颗读两格**，不再多打一跳）。

**四档读数的取舍（这一条是关键）：**

| 档 | 关注钮 | 为什么 |
|---|---|---|
| 未登录 | 不看、不按、改成「登录后关注」 | 站方那一份读不到；摆一枚按下去 401 的钮就是假开关 |
| 已登录 + `is_followed` 已知 | 真读数 | — |
| 已登录 + 详情腿失败 | **摆「关注」但按下去先取真态** | 不许把"不知道"画成"未关注"猜中就算 |
| 写失败 | 回滚 + 说人话 | 401 让重登、403 说站方挡下，**不回滚成静默** |

**本地账怎么办**（不新开第二本，但也不能当场删）：`GalleryArtistFollows` 里那条按名字记的
pixiv 条目**保留读取**（首页那一排仍是站方腿，不受影响），但 pixiv 画师页**不再写它** ——
写一套就够。旧条目交给一次性的迁移：能对上 `user_id` 的并入，对不上的留着不删
（删了等于替用户取消关注）。

**连带改动**：`pixivArtistActions(author)` 那枚装配器现在只收名字，要再加**用户号**（写站方必需）；
`PixivWorkScreen` / `PixivWorkInfoPanel` 两处调用点跟着传 —— 这两处的按钮也就同时被修正了。

---

### 5.6 滚动：头部跟着滚走（用户 2026-10-19 拍板）

**结论：整页一条纵向滚动，hero 跟着滚走，不做钉住。** 三条理由，都不是口味：

1. **三段都长** —— 插画 150 件、收藏 860 件，任何"钉住"都会吃掉 200~300dp 的浏览高度；
   钉住只在"内容短、切换频繁"时才划得来，这一屏正好相反。
2. **钉住必须有折叠态** —— hero 钉在顶上、滚动时要缩成一条细 bar，那条 bar 上要再摆名字 +
   两枚动作 + 三段切换，等于把一屏塞两套 chrome。而本仓既有折叠顶栏（`VeneraTopAppBar` 的
   Miuix ScrollBehavior）是给**列表页**用的，硬套到"一个人 + 一面墙"会与本屏唯一的主视觉打架。
3. **返回 pill 已经是悬浮件** —— 滚过 hero 之后它自己补玻璃底（`VeneraTopBarPill` 现成），
   于是"我在哪 / 怎么回去"这两件事在没有顶栏的情况下仍然成立。

**实现口径**：`LazyVerticalStaggeredGrid` 整页一条流，各段都用 `item(span = FullLine)` ——
hero / 头像 / 名字 / 简介 / 数据行 / 外链 / 三段切换全是整行项，卡片才是网格项。
（这是 `GalleryScreen` 主墙已经在用的同一种写法，`header` 与节头都走 `FullLine`，不另造容器。）
**切换三段不清滚动位置**：三段共用同一把 `LazyStaggeredGridState`，切段时若不在顶部则
`animateScrollToItem(0)` —— 因为"收藏 860 件切到漫画 12 件"时沿用旧偏移会读到一片空白，
那是"点了没反应"的另一种长相。

---

## 6. 状态矩阵（每档说什么，不许混）

| 档 | 触发 | 屏上 |
|---|---|---|
| A | 头部在途 | hero 骨架 + 名字骨架；网格独立骨架，不叠成一屏转圈 |
| B | 头部成功、背景为空 | hero 换成**首张作品**；作品也空 → 卡面色块 |
| C | 插画成功、空 | 「这一位还没有插画作品」—— 真读数，不解释 |
| D | 漫画成功、空 | 「这一位没有漫画作品」—— 与 C 分开念（`profile/all` 的 `manga` 桶为空是合法答复） |
| E | 某一批取失败 | 页脚一行「这一批没取上 · 重试」，**已取到的留着**（按一下等于删一屏，那是另一种伤） |
| F | 收藏 + 未登录 / 无票据 | 「公开收藏要登录 pixiv 才读得到」+ 「去登录」（顶栏那颗头像同一入口）。**不置失败态** —— 摆一枚按下去还是同一句话的「重试」就是假开关 |
| G | 收藏 + 已登录、空 | 「这一位没有公开收藏」 |
| H | 收藏 + 401 / 403 | 分开念：401 = 票据过期要重登，403 = 站方挡下（不合并成"没取到"） |
| I | 取不到用户号 | 「这条记录没带 pixiv 用户号」+「在 pixiv 搜这个名字」 |
| J | 关注钮 + 未登录 | 不看、不按，那枚 pill 换成「登录后关注」并直通登录 |
| K | 关注写失败 | 回滚布尔 + 401 让重登 / 403 说站方挡下（**不许静默**） |

内容守卫照旧：`buildGalleryWall(posts, maskMode, blockAi) { guard.blockedGalleryRule(...) }` ——
**同一张图在首页、画师页、收藏页三处必须同样遮或不遮**（本仓反复立案的那族）。

---

## 7. 大屏（平板）

- hero 与正文一起受 `contentMaxWidthFor(mode)` 收口（Medium 720 / Expanded 840），
  高度取夹取后的 340dp 上限 ⇒ 840÷340 ≈ 2.47:1，与手机档 361÷196 ≈ 1.84 同一族，不再是被拉长的扁条。
- 网格列数吃 `rememberImageWallColumnCount()`（现成那把 200dp 预算的尺子），
  840dp 上排 4 列；卡片宽度随列而不是随屏。
- 三段切换与动作排同一宽度收口，不铺满整条 1891dp。
- 反例（不做）：左右双栏（左列表右详情）—— 这一屏只有"一个人"，拆两栏会让 hero 失去整宽主视觉。

---

## 8. 文件清单

**新增**

| 文件 | 作用 |
|---|---|
| `gallery/data/PixivUserHead.kt` | 头部模型：id / name / account / avatar(2 档) / backgroundUrl / comment / links / following / premium |
| `gallery/data/PixivUserWorksParse.kt` | `profile/all`、`profile/illusts`、`user/detail` 三份答复的解析（缺键留 null，绝不补 0） |
| `gallery/domain/PixivFollowPlan.kt` | 关注那四档的**纯判据**（未登录 / 已知 / 详情腿失败 / 写失败回滚），单测直接钉 |
| `gallery/ui/PixivArtistScreen.kt` | 这一屏（唯一的新 UI 文件） |
| 单测 ×4 | 见 §9 |

**改动**

| 文件 | 改什么 |
|---|---|
| `gallery/data/PixivClient.kt` | +`userHead` / +`profileAll` / +`worksByIds`（三条匿名腿） |
| `gallery/data/PixivAppClient.kt` | +`userPublicBookmarks` / +`userCounts` / +`userIllusts`（票据腿）；+`followAdd` / `followDelete`（写腿） |
| `gallery/data/PixivAppParse.kt` | `userPremium` 扩成 `userFlags`（一次读 `is_premium` + `is_followed`，不多打一跳） |
| `gallery/data/GalleryBusinessApi.kt` | `GalleryPixivWorks` 加八个方法（契约先表态） |
| `gallery/data/android/GalleryAdaptersAndroid.kt` | 转发八条 |
| `gallery/ui/PixivWorkInfoPanel.kt` | `pixivArtistActions(author)` 再加 `userId`；关注改走站方，**作品页那颗钮同时被修正** |
| `gallery/ui/GalleryArtistProfileScreen.kt` | 把 `searchQuery` 那一句移进非 pixiv 分支 / 提前返回 |
| `GalleryArtistProfileActivity.kt` | `site == PIXIV` 分派到新屏 |
| `gallery/ui/GalleryPixivHome.kt` | `openPixivUserPage` 保留，但点画师改走画师页（横排 / 搜索分栏两处） |
| `gallery/ui/GalleryScreen.kt` | 两处 `onOpenUser` 改跳画师页 |
| `gallery/ui/GalleryCardCaption.kt`、`GalleryScreen.kt` | 卡片署名头像可点（pixiv 墙） |
| `ui/tokens/Spacing.kt` | +`pixivArtistAvatarSize`(76dp)、+`pixivArtistHeroMin/MaxHeight` |

**删除**：无。`GalleryArtistProfileScreen` 一行都不删，只搬一句。

---

## 9. 判据（单测）与验证

1. `PixivUserHeadParseTest` —— `background` 为 `null` ⇒ `backgroundUrl = null`（不是空串）；
   `error=false` 但缺 `body` ⇒ 抛；`social` 缺键 ⇒ 空表；`premium` 缺键 ⇒ `false`。
2. `PixivArtistRouteTest` —— 钉住「`site == PIXIV` 的路径上不出现 `GalleryRankings.searchQuery`」：
   对 `PixivArtistScreen.kt` 做**声明扫描**（同 `GalleryPixivHomeWiringTest` 的做法），
   而不是跑一次真实组合 —— 那条腿会抛，跑不起来。
3. `PixivArtistTabsTest` —— A–K 十一档各给一句，且 C/D/F/J 四句**互不相同**（防止退化成同一句"没有内容"）。
4. `PixivFollowPlanTest` —— 四档关注判据逐条钉：未登录 ⇒ 不给按；详情腿失败 ⇒ 按前先取真态
   （**不许**画成"未关注"）；写失败 ⇒ 回滚；401 / 403 两句文案不同。
5. `TokenBudgetFrozenTest` —— 新屏不许出现裸 `dp`/`sp`/`Color(0x…)`；新 token 加在 `Spacing.kt`。
6. 真机（用户手动）：进页不闪退；关注点了首页「已关注」那栏真多一位；收藏未登录给得出出口；
   平板 840 收口；背景图缺失时回退首张；切段后回到顶部。

---

## 10. 这一轮不做 / 待真机验证

- **不做**：小说 tab（站方有，但本仓没有小说屏）、私密收藏（那是替用户擦掉他给自己划的界）、
  独立「详情」tab（简介 + 外链摆头部，第三格不空着）、
  钉住 hero（用户 2026-10-19 拍板跟着滚，理由见 §5.6）。
- **已拍板接上**（原「不做」清单里撤回的一条）：**关注改走站方** `/v1/user/follow/add|delete`，
  本地账不再写 pixiv 条目 —— 见 §5.5，它顺带修掉了"作品页点了关注、首页那一栏不认"那个真缺陷。
- **待真机**：§3 第 4/5/6 条票据腿的形状，以及 `follow/add|delete` 两条写腿（本机没有有效票据，
  只能对着 pixivpy `aapi.py` 与 pixez 互证）；`background.url` 在 `i.pximg.net` 上的取图
  （`ImageHeaderPolicy` 的 `pximg.net` 那条 Referer 规则已覆盖，但这一档是 `c/1920x960…` 的裁切路径，
  值得真机看一眼）。
