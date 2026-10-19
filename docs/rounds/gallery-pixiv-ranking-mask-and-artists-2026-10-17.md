# 第八轮：排行榜「全是 R18」的真因 / 排行榜横滑 / 关注画师与推荐用户

日期：2026-10-17
触发：用户三张截图 + 三句话
1. 「排行榜这个为啥都说这样显示 r18，我这边屏蔽也没开，p站站点也没屏蔽 r18」
2. 「排行榜这些卡片应该是可以左右滑动的」
3. 「关注画师你参考 pixez-miuix 是怎么拿到的，我是想你做成显示关注画师，左右滑动可以看更多，也加上没用关注的推荐用户，但要区分开」

---

## 一、排行榜「全是 R18」：与屏蔽设置无关，是打码集合算错了

### 现象

排行榜前三张全部模糊 + 右上角 R18 角标；同一屏的「推荐」那两张**完全不糊**；用户确认
`nsfw_mode` 没开、pixiv 站点侧也没屏蔽 R18。

### 真因（一行）

```kotlin
// 改前
}.cards.mapTo(HashSet()) { it.post.uid }
```

`buildGalleryWall` 的实现里，**`masked = true` 与 `masked = false` 都进 `kept`**：

```kotlin
GalleryGuard.BLURRED -> kept += GalleryCard(post, masked = true)
else -> kept += GalleryCard(post, masked = false)
```

所以 `GalleryWall.cards` 是"这一墙**有哪几张**"，不是"哪几张**要打码**"。
把它的 uid 全部收进 `pixivRankingMasked` 之后，`maskedOf(post) = post.uid in 全集` **恒为 true** ——
于是这一排**每一张**都交给 `VeneraCoverMask.Masked`（模糊 + 暗遮罩 + "R18" 角标），
与 `post.rating`、与 `nsfw_mode` 全都无关。

这条也解释了为什么"推荐"不糊：推荐那一池直接用 `buildGalleryWall(...)` 的
`GalleryWall` + `card.masked`（走 `GalleryCardsGrid`），从来没有经过这个集合。

### 改法

```kotlin
}.cards.filter { it.masked }.mapTo(HashSet()) { it.post.uid }
```

判据：`GalleryPixivHomeWiringTest.排行榜的打码集合只收命中打码的那几张，不是整墙`
（钉 `.cards.filter { it.masked }.mapTo`，并禁止 `}.cards.mapTo(` 回来）。

### 顺带核过、**不是**问题的两处

- `PixivFeedParse.ranking()`（匿名 www 腿）本来就是 `is_masked` 判档，正确。
- app-api 的 `Illust` 里 `x_restrict` 是数字（0/1/2），`contentOrNull` 对非 `JsonNull` 一律返回内容，
  所以 `"0"`/`"1"` 读得到 —— 这一层没有类型坑。

---

## 二、排行榜卡片改成横滑

`PixivRankedWorkGrid` 从前恒是 `chunked(3)` 三列：第三张右边就是屏幕边，第 4 名**没有任何办法看到**，
而"查看全部"在节头右上角（读完三张得回顶上点）。

现在：
- **收起态** → `LazyRow`，卡片定宽 `tokens.spacing.historyCardWidth`（124dp，与历史那一排同档），
  右侧留一截 `screenHorizontal`；一屏会露出第三张的一部分 ⇒ "右边还有"在屏上看得出来。
- **展开态** → 仍是三列网格（用户已明说要看全部，网格更好扫）。

判据沿用 `GalleryPixivHomeWiringTest`（这一排仍在 `PixivRankedWorkGrid` 里）。

---

## 三、关注画师 / 推荐用户：照 pixez-miuix 的两条 app-api 腿

### pixez 怎么拿的（读的是它的 `compose-miuix` 分支）

`data/repository/UserRepository.kt`：

```kotlin
// 关注列表
apiClient.get("/v1/user/following") {
    parameter("filter", "for_android")
    parameter("user_id", userId)
    parameter("restrict", restrict)   // "public"
}
// 推荐用户
apiClient.get("/v1/user/recommended") {
    parameter("filter", "for_android")
}
```

两者答复**同族**（`data/model/UserPreview.kt`）：

```json
{ "user_previews": [ { "user": { "id": 1, "name": "…", "account": "…",
                                 "profile_image_urls": { "medium": "…" } },
                       "illusts": [], "novels": [], "is_muted": false } ],
  "next_url": "…?filter=for_android&offset=30" }
```

### 我们先前为什么量到 400 / 191

少了 `filter=for_android`。那一枚不是可选装饰：不带它站方回的是
`error 191`（无效参数那一族），当时被读成"这条端点不存在"，于是这一栏一直只摆一句空话。

### 落地的形状

| 层 | 件 | 内容 |
|---|---|---|
| 模型 | `gallery/data/PixivUser.kt` | `PixivUser(id,name,account,avatarUrl,muted)` + `PixivUserPage(users,nextUrl)` |
| 解析 | `gallery/data/PixivUserParse.kt` | `user_previews[]`；整份读不懂抛、N 条一条认不出也抛；`error` 对象念状态码与站方原话 |
| 取数 | `PixivAppClient.following / recommendedUsers` | Bearer + 官方客户端头；翻页只认 `next_url` |
| 契约 | `GalleryPixivWorks.following / recommendedUsers` | 票据由调用方现取，契约不碰登录态 |
| 界面 | `PixivArtistRail`（`GalleryPixivHome.kt`） | 圆形头像 + 名字，`LazyRow` 横滑，定宽 `artistAvatarSlotWidth` |
| 屏上 | `GalleryHomeSections` 的 `PIXIV_ARTISTS` | **两段、两个节头**：`关注画师` / `推荐画师` |
| 取数时机 | `GalleryScreen` | `LaunchedEffect(domain, pixivAuth, pixivWorks, pixivArtistsTick)` + 凭证 `users:$uid`；存档在 `GalleryPixivHomeStore` |

### 三条不能省的分寸

1. **两栏各存各的**：`following` 与 `recommendedUsers` 是两个字段、两套空/忙/失败文案。
   合成一排会让用户以为自己关注了根本没关注的人 —— 这是这一节最容易产生的误解。
2. **未登录不摆空列表**：按 `authState` 穷尽三档念话（Anonymous / NeedsRelogin / LoginRejected），
   与顶栏那颗头像同源。
3. **已登录但没存下 uid**（老版本登录记录）：那一档不能沉默 —— 顶栏说"已登录"、下面说"你还没关注人"
   会在屏上打架，所以直说"重新登录一次"。

### 点头像做什么

本仓没有 pixiv 画师主页那一屏（`GalleryArtistProfileScreen` 走的是
`GalleryRankings.searchQuery(site=…)`，pixiv 不吃这套查询语言）。所以点击 = **打开站方用户页**
（`https://www.pixiv.net/users/{id}`），先 Custom Tab、起不来才退回裸 VIEW ——
与登录那一跳同一套优先级。这是"按下去真能到那个人的全部作品"，而不是一枚停下来说"还没做"的假按钮。

---

## 四、数字

- 单测 **179 类 / 1,422 笔 / 0 失败**（新增 `PixivUserParseTest` 6 笔、`GalleryPixivHomeWiringTest` +3）
- arm64 包 `venera-arm64-debug-2026-10-17-p8.apk`（103,584,924 B）

## 五、没量到的

1. `/v1/user/following` 与 `/v1/user/recommended` 在真机票据下的**实际答复**（判据只喂了样本；
   第一次上机若回错，屏上会带状态码与站方原话，把它贴回来即可定位）。
2. 收起态横滑后一屏到底露出几张（124dp 定宽是按 token 推的，没有真机像素读数）。
3. 关注 / 推荐两栏同时在场时这一节的总高度（两排 88dp 卡 + 两个节头）。
