# 画廊接入 pixiv：五套放法的调研记录（2026-10-16）

> 范围：只谈 `app/` 手机档"pixiv 的图片放在哪"，**本轮不动一行代码**。
> 依据：① 全站代码通读（每条判定带 file:line，本鱼逐行 `sed` 回读过）；② 今日对 pixiv 公开端点的 curl 现量（直连、无代理）。
> 口径：用户 2026-10-16 明确「暂时不用考虑 pixiv 的漫画，只需要图片」⇒ 多页组图与 ugoira 不建模，按 `illust_type` 滤掉。

## 〇 一句话结论

用户画的那张示意（搜索框下面三格 **综合 / 图库站 / Pixiv**）**落得下，而且比方案稿的"卡内再加一层分区"更干净**：
它是**内容域**切换（决定首页三节吃什么料），不是 2026-10-15 被摘掉的那排**来源轴**（全部/yande.re/Gelbooru/Safebooru），
所以来源轴继续只长在搜索卡里，首页这一排不与它构成"两套来源切换器"。

代价不在界面，在数据层：**pixiv 要进 `GallerySite` 枚举，而枚举一加，首页混合墙当场抛异常**（见第三节坑 1，编译器看不见）。

## 一 方案稿那三条前提的改判

| 方案稿（`gallery-pixiv-source-placement-2026-10-16.html`） | 实情 | 出处 |
|---|---|---|
| §三「在 chrome 下面加一层两格的分区选择器，它决定第二排是哪一根轴」 | 来源轴**不在 chrome 下面**，它长在展开的搜索卡里；首页那一排 2026-10-15 已被用户点名摘掉 | `gallery/ui/GallerySearchArea.kt:778`；`gallery/ui/GalleryHomeChrome.kt:51-67` |
| §二 那一排实得 337dp、4 格每格文字 56.8dp、5 格掉到 39.4dp | **账成立**，只是算的是卡内那一排（`space8` 16dp×2 内衬在 `GallerySearchArea.kt:765-769`），不是首页 | 见下节密度账 |
| §一 pixiv 排行档 = 日/周/月/新人/原创/男性向/女性向，且"匿名时 R-18 被静默过滤" | 匿名 JSON 的档名是 `daily/weekly/monthly/rookie/original/male/female`，**`day/week/month` 回 404**；R-18 那两档是 **403 整档拒绝**，不是静默过滤 | 今日现量，第四节 |

## 二 密度账：首页三格 vs 卡内四格

| 位置 | 实宽推导 | 每格 | 文字可用 | 判定 |
|---|---|---|---|---|
| **首页**（示意那一排） | 393 − `screenHorizontal` 12×2 = **369dp**（`ui/tokens/Spacing.kt:40`） | 3 格扣 2×`segmentedGap` 10dp ⇒ ≈**116dp**（`Spacing.kt:330`） | ≈**96dp** | 「图库站」「Pixiv」都放得下，不需要省略号 |
| **卡内那一排**（今天） | 369 − `space8` 16×2 = **337dp**（`GallerySearchArea.kt:765-769`） | 4 格扣 3×10dp ⇒ ≈**76.8dp** | ≈**56.8dp** | 已到临界（"Safebooru" 9 字符在边上），加第 5 格必大面积省略号 |

宽屏档另算：`isWideScreen`（>600dp）会把分段器高度升到 `segmentedHeightWide` 56dp、字号升到 `itemTitle`（`Spacing.kt:319`、`components/venera/VeneraSegmentedButton.kt:67-69`）——**字更大而格数不变**。

上面那套算术的四个零件全在 `components/venera/VeneraSegmentedButton.kt` 里，逐行对得上：
格间 `spacedBy(segmentedGap)`（`:74`）、每格 `weight(1f)` 等宽（`:89`）、文字左右 `padding(horizontal = space5)`（`:113`）、
`maxLines = 1` + `overflow = TextOverflow.Ellipsis`（`:110-112`）⇒ 组件**不自持宽度**，宽度只由调用方给的那条链决定。

### 这一排挂在哪儿，才不用碰那三把尺子

- 挂在**内容层第一行**：`gallery/ui/GalleryScreen.kt:814` 的 `galleryHomeSections(...)` 之前另起一节（或 `GalleryForYouPage` 的 `sections`/`chrome` 两槽之间，装配点在 `:1129-1174`）。
- 于是这三把尺子**一个字都不用改**：
  1. `galleryHomeChromeHeight()` = `space2 + dockedSearchBarHeight + space3` = **66dp**（`GalleryHomeChrome.kt:219-225`）——它只喂收栏阈值 `GalleryChromeHidePolicy`（`GalleryScreen.kt:328-329`），新排不进 chrome 就不影响它；
  2. `app/src/test/java/com/venera/compose/gallery/GalleryHomeChromeTest.kt:65-75`——断言 `GallerySearchArea.kt` 里 `VeneraSegmentedButton(` **恰好 1 处**，同文件另一颗断言钉 `GalleryHomeChrome.kt` 里为 0。新排用自持件画、且不住在这两个文件里 ⇒ 两条都恒绿；
  3. 搜索卡不加高、不摆第二颗分段器（原方案 B 那条"卡内净增约 40~58dp"的代价随之消失）。

## 三 加第 4 枚 `GallerySite` 的真实代价

分三档看。**只有第一档会编译报错，第二档是静默的**——第二档才是这一族的贵处。

### 3.1 静默档（编译器看不见，只在屏上/运行时报错）

| # | 位置 | 加第 4 枚之后发生什么 |
|---|---|---|
| 1 | `gallery/domain/GalleryMerge.kt:108-109` 按 `GallerySite.entries` 循环并 `usable.getValue(site)`，而 `gallery/domain/GalleryDailyFeed.kt:80-84` 只塞 YANDERE/GELBOORU/SAFEBOORU **三把键** | **每日热门与首页混合墙当场抛 `NoSuchElementException`**。对照：隔壁 `gallery/domain/GallerySearchMerge.kt:58` 用的是 `entries.filter { usable.containsKey(it) }`，`gallery/domain/GalleryForYouMerge.kt:123` 用的是 `usable[site].orEmpty()` —— 三处三把写法。契约在册：`app/src/test/java/com/venera/compose/gallery/GalleryMergeTest.kt:53`「生产端契约：每站一个键，这一轮缺席的站给空表」 |
| 2 | `gallery/data/GalleryPost.kt:150-151` `isAdultMarked = rating.lowercase() !in SAFE_RATINGS`，白名单在 `:246-251`（`s/safe/g/general/sensitive`） | **榜单**那一腿的载荷没有分级格（§4.1 已量出**搜索**那一腿有 `xRestrict`/`restrict`，两腿分开判）⇒ 走榜单进来的那批 `rating` 是空串 ⇒ **每张卡都被判成成人档**，现象是一面打码的黑墙而不是报错 |
| 3 | `gallery/domain/GalleryRanking.kt:71-72` `supports()` 里 `ranking == GalleryRanking.ALL` 对**任何站**短路为真，而 `suffix()` 末尾是 `else -> YANDERE_SCORE` | 若 pixiv 进了来源轴，"全部排行"这一档会静默给它发一串 `order:score`。本方案（内容域）里 pixiv 不进来源轴，暂时咬不到，但这一支要显式改死 |
| 4 | 页尾读数逐站拼串：`gallery/ui/GalleryForYouScreen.kt:354`、`gallery/ui/GalleryScreen.kt:2106`（都是 `GallerySite.entries.joinToString(" · ") { "${it.displayName} ${perSite[it] ?: 0}" }`） | 会念出「… · Pixiv 0」这种读数 |
| 5 | `gallery/ui/GalleryHistoryScreen.kt:512` `GallerySite.entries.forEach { site -> … }` 画筛选 chip | 多一枚点不出货的死 chip |
| 6 | `data/network/PreferredIpRules.kt:143` `GALLERY_SPEED_TEST_SOURCES: Map<String, …>`（按 `routeKey` 取，`:213` 消费） | 少 pixiv 那把键 = 那一站**永不参与优选 IP 测速**，且不报错 |
| 7 | ~~屏蔽 AI 在 p 站内容上静默失效~~ | **这条本鱼判错了，现量改判**：词表 `security/guard/GuardVocabulary.kt:42` 是 `setOf("ai","ai-generated","ai生成","ai绘图")`，而 `gallery/domain/GalleryAi.kt:30-33` 会把标签 `trim().lowercase()` 再把 `_` 折成 `-` ⇒ pixiv 的 `AI生成` **落得进这张表**。这一条不是坑 |
| 8 | 测试面：`app/src/test/java/com/venera/compose/gallery/GalleryRoundFailureReadoutTest.kt:103` 断言 `GallerySite.entries.toSet() == reasons.keys`（"每一站都要有自己的原因"）；`gallery/GallerySearchSourceTest.kt:120-141` 钉死分段器那四档的顺序与 label 字面 | 两把尺子会红（红得算诚实，但要显式表态）。⚠️ 另一条 `DesktopGalleryNetworkHygiene.kt:30` **不是断言**，它只是把 `GallerySite.entries.size` 打进一句 no-op 日志，别当尺子 |

### 3.2 编译强制档（逐条表态就能过，量大但不阴）

**现量普查**：按站（或按腿）分支的 `when` 共 **17 处**，其中 **16 处穷尽无 `else`**（加第 4 枚枚举直接编译失败，逼显式表态），
只有 **1 处带 `else`** —— `gallery/domain/GalleryRanking.kt:317` 的 `else -> YANDERE_SCORE`，那一处已归进 3.1 坑 3。

| 文件 | `when` 所在行 |
|---|---|
| `gallery/ui/GallerySearchViewModel.kt` | `:294`（`pageSizeFor`）、`:1068`、`:1277`（`when (leg)`） |
| `gallery/ui/GalleryForYouViewModel.kt` | `:208`（`limitOf`）、`:493` |
| `gallery/ui/GalleryArtistRows.kt` | `:162`、`:624` |
| `gallery/ui/GalleryArtistProfileScreen.kt` | `:172`、`:572` |
| `gallery/ui/GalleryHistoryScreen.kt` | `:282`（`when (row.site)`，三站各一分支，无 else） |
| `gallery/ui/GalleryPostScreen.kt` | `:693` |
| `gallery/domain/GalleryRanking.kt` | `:323`（时间窗那一支；`:317` 是带 else 的那一颗） |
| `gallery/domain/GallerySearch.kt` | `:75`（`tagBudget`） |
| `components/venera/VeneraGallerySourceMark.kt` | `:57`（站图标） |
| `gallery/domain/GallerySearchSource.kt` | `:176`（`sourceMarkLetter`，注释明写"不留 else"） |
| `desktop/.../gallery/ui/DesktopArtistsPolicy.kt` | `:172` |
| `desktop/.../gallery/ui/DesktopGallerySearchPane.kt` | `:881`（`boardOf`） |

接口接线三处：`gallery/data/GalleryBusinessApi.kt:197`（`interface GalleryBoards`，成员 `yandere`/`gelbooru`/`safebooru`）、
`gallery/data/android/GalleryAdaptersAndroid.kt:140`（`AndroidGalleryBoards`）、
`desktop/src/main/kotlin/com/venera/desktop/gallery/data/DesktopGalleryBoards.kt:22-29`。

「全部」那一档：`gallery/domain/GallerySearchSource.kt:104` `ALL = GallerySearchSource(GallerySite.entries.toList())`
⇒ **不改这一行，pixiv 自动进并**（`:116` 的 `options` 才会变 5 格）。

### 3.3 不用动的档

- **存储不需要迁移**：收藏/历史/关注是手搓 JSON 文件，站身份存 `routeKey` 字符串；读侧 `GallerySite.fromRouteKey(siteKey)` 认不出就是 null（`gallery/data/GalleryFavoritesStore.kt:67`），而两处入口都把 null 滤掉（`:223`、`:252`）⇒ 加新枚举值不动盘上格式，**但旧版 App 读到新站的收藏会整条静默丢掉**（这一条对"降级路径不许静默"那条口径是要登记的）；条目 uid = `"$routeKey:$id"`（`GalleryPost.kt:211`）。
- **卡片墙与大图页不用改**：pixiv 卡复用 `GalleryCardsGrid`，打开走 `openGalleryPost(site, postId)`（`GalleryPostActivity.kt:105`）。
- **底栏不用改**：若走"画廊下的二级页"，先例已存在——`feature/Navigation.kt:448-450`（`tabIndex`）与 `:540-542`（`currentTab`）已经把 `GalleryDailyRoute`/`GalleryHistoryRoute` 归到 GALLERY tab，加一张子页不动那 7 处映射。

### 3.4 一处命名撞车（接之前要单独拍板）

`gallery/domain/GalleryArtistLinks.kt:19-27` 已经把 `PIXIV` 用作**画师外链平台**（`GalleryArtistLinkPlatform.PIXIV`），
而关注关系的键是 `GalleryArtistFollows.uidOf(site, name) = "${site.routeKey}:${name.trim().lowercase()}"`（`gallery/domain/GalleryArtistFollows.kt:27`）——
**标签名身份**。pixiv 的画师身份是 `user_id`，两本账不同轨，直接接会出现"同名不同人"。

## 四 pixiv 匿名数据面（今日现量）

出口：**直连、无代理**，UA 为桌面 Chrome。时间 2026-10-16。

| 端点 | 状态 / 字节 | 原始响应前缀 | 判定 |
|---|---|---|---|
| `ranking.php?mode=daily&format=json` | 200 / 62490 | `{"contents":[{"title":"やる気…` | 匿名榜单可用 |
| 同 `weekly` / `monthly` / `rookie` / `original` / `male` / `female` | 200 / 62413、60051、59165、69547、62937、64856 | 同上 | 七档全通 |
| `ranking.php?mode=day&format=json`（`week`/`month` 同） | 404 / 108 | `{"error":"\u30e9\u30f3\u30ad\u30f3\u30b0\u304c…`（ランキングが見つかりません） | **档名不是 `day/week/month`** |
| `ranking.php?mode=daily&format=json&p=1` / `&p=2` | 200 / 62522、57516 | p1 首条 `"rank":1`；p2 首条 `"rank":51` | 分页**真生效**，每页 50 |
| `ranking.php?mode=daily_r18&format=json`（`male_r18` 同） | **403** / 8540、8675 | `<!DOCTYPE html><html lang="ja"…` | R-18 是整档登录墙，**不是静默过滤** |
| `ranking.php`（无参） | 200 | — | 页面在，只是榜单档名换了 |
| `ajax/search/illust?word=1girl&search_target=partial_match_for_tags&sort=date_desc`（GET；带 `Referer`+`Accept: application/json` 的 GET；同参 POST） | **404** / 169 | `{"error":true,"message":"\u30ea\u30af\u30a8\u30b9\u30c8\u3055\u308c\u305f\u30da\u30fc\u30b8\u304c…`（リクエストされたページが見つかりません） | ~~搜索这一腿今天不通~~ ⇒ **同日 22:5x 复跑推翻，见下一节"搜索腿改判"**：路径要带关键词那一段 |
| `web/v1/ranking/illust?mode=day`、`web/v1/search/illust?word=1girl` | 302 → 加斜杠后 **404** / 2649 | `<!DOCTYPE html>…<title>Error 404 (Not Found) [pixiv]` | 同上 |
| `search.php?word=1girl&s_mode=s_tag_partial&format=json` | 301 / 0 | （空） | 同上 |
| `ajax/user/8000315` | 200 / 380 | `{"error":…` 信封 | `/ajax/*` 家族活着（这个号不存在） |
| `ajax/illust/12345678/pages` | 404 / 37 | `{"error":true,"message":"","body":[]}` | 路由在，号不存在 ⇒ 要拿真号复测 |

**载荷字段表**（从 `mode=female` 那笔 62 KB 现取，`grep -o '"[a-zA-Z_]*":' | sort -u`）：
`ad_logo antisocial attr bl card content contents date date_range_text description drug footer furry grotesque h_canonical h_title header homosexuality illust_book_style illust_content_type illust_id illust_page_count illust_series*（8 枚） illust_type image is_masked lo logo mask_reason meta meta_ogp mode next next_date original page page_url prev prev_date profile_img rank rank_total rating_count religion sexual site tags thoughts title twitter_card url user_id user_name view_count violent width yes_rank yuri zoneConfig`
—— 榜单载荷里有 `illust_page_count` 与 `illust_type`，**没有分级格**（第三节坑 2 的成因）。

### 4.1 搜索腿改判（同日 22:5x 复跑，推翻上面那三行 404）

线索来自 `daydreamer-json/pixiv-ajax-api-docs`：**关键词要当路径段，不是 query 参数**。现量：

| 端点 | 状态 / 字节 | 读数 |
|---|---|---|
| `ajax/search/artworks/{原神}?word=…&order=desc&target=illust&s_mode=s_tag_full&p=1&merge=false` | **200 / 69,075** | `{"error":false,"body":{"illustManga":{"data":[{"id":"150930351"…` |
| 同端点 `&p=2` | 200 / 69,717 | 首条换成 `150920005` ⇒ **分页真生效** |
| `ajax/search/top/{原神}?word=…` | 200 / 80,276 | 含 novel 等其它体裁（`body.novel.data[0].id=29396024`） |
| `ajax/search/illust/{原神}?…` | **404** / 169 | 那一档不存在，`artworks` 才是正解 |
| 拉丁词 `ajax/search/artworks/1girl?…` | 200 / 71,747 | 68 条，`"total":6989`，每条带 `"xRestrict":0` 与 `"restrict":0` |

⇒ 三条结论要分开写，别合成一句：
1. **搜索匿名可用**（本文件先前那"四条路全灭"作废）；
2. **搜索载荷有分级格**（`xRestrict`/`restrict`），**榜单载荷没有** —— 坑 2 只对榜单那一腿成立；
3. 匿名样本里 `xRestrict` 恒 0 ⇒ 搜索侧确实是**过滤**（与 `ranking.php?mode=*_r18` 的 403 是两种表现，方案稿那句"静默过滤"只对搜索这一半）。

**两格未判定**（不许拿"200 + 合法 JSON"当参数生效）：`no_ai=1` 与 `no_ai=0` 的首三条 id 与条数完全相同；
`s_mode=s_tag_full` 与 `s_tag_partial` 对"原神"这个词也分不出差别 —— 都要换样本词复测才能定。

### 4.2 复现命令（逐字）

```bash
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
# 档名扫描（改这 7+3 个词就能复现整张状态表）
for m in day daily week weekly month monthly rookie original male female daily_r18 male_r18; do
  r=$(curl -m 20 -A "$UA" -s -o /tmp/m -w "%{http_code} %{size_download}" "https://www.pixiv.net/ranking.php?mode=$m&format=json")
  printf "%-10s %s | %.46s\n" "$m" "$r" "$(head -c 46 /tmp/m | tr -d '\r\n')"
done
# 分页是否真生效
curl -m 20 -A "$UA" -s "https://www.pixiv.net/ranking.php?mode=daily&format=json&p=1" | grep -o '"rank":1[,}]' | head -1
curl -m 20 -A "$UA" -s "https://www.pixiv.net/ranking.php?mode=daily&format=json&p=2" | grep -o '"rank":51[,}]' | head -1
```

## 五 与仓内既有读数的冲突与继承

- **作废**：`docs/rounds/direct-source-research-2026-10-01.md:26`「`www.pixiv.net` / `i.pximg.net` ❌ 被墙」——今日直连回的是 404/200/302，不是超时。同文档 `:27` 记的 `s.pximg.net` / `i.pixiv.re` 子域通，仍然成立。
- **继承**：`FREEZE-STATEMENT.md:1840`（`/ajax/user/{id}` 三档对照皆 200、687 字节）与 `:1841`（`i.pximg.net` 无 `Referer` → 403/146 字节，带 `Referer: https://www.pixiv.net/` → 200/5149 字节）——今日未复现，但 `data/network/ImageHeaderPolicy.kt:68-73` 那条 `"pximg.net" to mapOf("Referer" to "https://www.pixiv.net/")` 仍在内置表里，接 pixiv 取图这一跳直接复用，**不用新造规则**。
- **既有判负**：`docs/rounds/remaining-plan-and-reuse-2026-10-10.md:54` 记 pixiv 唯一像样的库 `xyz.cssxsh.pixiv:pixiv-client` 是 AGPL-3.0 ⇒ 判负。本方案不引库，走现成 OkHttp + `kotlinx.serialization`（与 `YandeReClient.kt:208-234` 同一路）。
- **桌面既有判据**：`docs/rounds/windows-gallery-home-s2a-wiring-2026-10-03.md:67` 明写 pixiv 在桌面返回 `Result.failure(PixivNotWiredOnDesktop)` 而**不是** `success(null)`——"降级路径不许静默交错"那条口径在这里的落地形。

## 六 五套放法对照

| 放法 | 形态 | 要动的尺子/冻结 | 今天能落地到什么程度 |
|---|---|---|---|
| **⑤ 首页内容域切换**（用户这张图） | 搜索框下面三格：综合 / 图库站 / Pixiv，决定首页三节吃什么料 | 只需改 `GalleryHomeChrome.kt:67` 那句注释并登记（要点头）；三把尺子不动 | 能。前提是先拆第三节坑 1、坑 2 |
| ① 卡内分区（方案 B 原意） | 展开搜索卡加一排 chip 分区，pixiv 区另起控件 | 卡净增高约 40dp；`ALL`（`GallerySearchSource.kt:104`）要显式剥 pixiv；16 处 `when` 全表态 | 能，但搜索腿只能挂"待测" |
| ② 画廊下的 pixiv 榜单二级页 | 新 `GalleryPixivRoute`，照 `GalleryDailyRoute` 先例挂 GALLERY tab | 零冻结改动；底栏 7 处映射不动 | 能。②a 不进枚举＝只能逛不能收藏；②b 进枚举＝要拆第三节的雷 |
| ③ 只从画师维度进 | 点 pixiv 画师时那面墙换成 p 站图（复用 `PixivClient` 匿名 200 那两路） | 枚举不动、`GalleryMerge` 不响 | 最省，但没榜单可逛；且 3.4 那处身份不同轨要拍板 |
| ④ 第六个底栏 Tab | 独立 Tab | 每格宽度是推导常量（`components/backdrop/VeneraLiquidGlassNavBar.kt:145-147`，另 `:387` 的 `fillMaxWidth(1f/tabsCount)`），6 格把今天 5 格一起缩；要改 7 处映射 + 两套弹性指示器（MD3 那套在 `components/VeneraFloatingNavBar.kt:117-124`） | **判负** |

「综合」要说清是哪一种综合：**各站各出自己的榜再合并**（今天每日热门就是这么合的，`GalleryDailyFeed` → `GalleryMerge.mix`，语义真），
**不是**"一句问句打四站"——后者是方案稿反对的假读数，pixiv 的 `word/search_target/sort/mode` 与 booru 的 `tags=… order:score` 根本不同语法。

## 七 还欠的量（本轮不动手）

1. 搜索腿到底要不要登录态/`x-csrftoken`——四条路今日全灭，这一条决定 pixiv 区有没有"搜索"这件事。
2. `/ajax/illust/{真实 id}/pages` 匿名可达性（今日只验到"路由在、号不存在"那一档）。
3. `illust_type` 在样本里的取值分布 ⇒ 定"只取单图"那道过滤怎么写（`illust_page_count` 是否恒 ≥1）。
4. 榜单里那枚 `image` 的图床 host 是否落进现成的 `"pximg.net"` 规则（`ImageHeaderPolicy.kt:73`）。
5. 连打 20 笔的限流观察——照 `docs/rounds/gallery-artist-alias-2026-09.md:220` 那条口径，**不得据"全 200"说它没限流**。
6. `mode=rookie` 首条 `"date":"1970年01…"` 是站方语义还是取值 bug——可疑读数不当结论用。

## 八 明确不做

不动卡片墙与大图页那套队列；不做第六个底栏 Tab；不把 pixiv 塞进来源轴的「全部」；不做鉴权（关注/书签/R-18——R-18 现量 403，摆一枚匿名必 403 的开关算假开关）；不做多页组图与 ugoira 建模（用户口径：只做图片）；不把 pixiv 接进漫画源与阅读器（要伪造 `comicId/sourceName/chapterTitle`，`feature/Navigation.kt:240-252`）；桌面侧照 `windows-gallery-home-s2a-wiring-2026-10-03.md:67` 显式不接线；设计稿与选型批准前不写客户端。

---

## 九 第二笔现量（同日傍晚，本鱼独立复测）与三处前提改判

> 同一台机器、同一个直连出口、同一枚桌面 Chrome UA。上一笔是"调研轮"，这一笔是"能不能接"的判定量。
> 全部读数可复现：`ranking.php?mode=daily&format=json` 取首条号，再打 `/ajax/illust/{号}` 与 `/pages`。

### 9.1 上一笔欠的量，现在销掉两格、改判一格

| 第七节那一格 | 现在的读数 |
|---|---|
| **#2 `/ajax/illust/{真实 id}/pages` 匿名可达性** | **通了**：真号 `150822243` ⇒ `/ajax/illust/150822243` **200 / 49,862 字节**、`/pages` **200 / 3,893 字节**，`body` 是**该作 8 页的数组**，每页带 `urls.{thumb_mini,small,regular,original}` + `width`/`height`。⇒ 大图页与多页取流都**不需要登录**。 |
| **#4 图床 host 是否落进现成规则** | **落进**：榜单给的是 `url`（**不是** `image` —— 上一笔写的是 `image`，字段名记错了），host = `i.pximg.net`。实测**无 `Referer` → 403 / 548 字节**、**带 `Referer: https://www.pixiv.net/` → 200 / 479,544 字节** ⇒ `ImageHeaderPolicy.kt:68-73` 那条 `"pximg.net"` 规则正好覆盖，**取流层一行不用改**。 |
| **#3 `illust_type` 取值分布** | **量出来了，而且把第一节那条前提推翻**（见 9.2①）。 |
| **#1 搜索腿要不要登录态** | ~~**仍然不通，且本鱼多试了三条上一笔没试的路**：`ajax/search/popular/illust/{tag}`、`ajax/search/top/illust/{tag}`、`ajax/search/newest/illust/{tag}` 加上上一笔的 `ajax/search/illust?word=` 与 `web/v1/search/illust` ⇒ **五路全灭**，前四条同一形状 **404 / 169 字节**~~ ⇒ **本鱼这一行判错了，23:5x 现量改判**：那五条是**同一个错路径名**打了五遍。真路是 `ajax/search/artworks/{词}`（关键词在路径里，本文件 §4.1 早已写下）⇒ **200 / 71,933 字节 / `"error":false` / 每页 60 条**，`p=2` 首条从 `150926978` 换到 `150611548`（分页真生效）。这一格**销掉**：搜索**不要登录态**。它照样不进来源轴，但理由换成"语法不同轴"（选型⑤ 是内容域），不是数据墙。 |

### 9.2 三处要改判的前提（都带读数）

**① 「按 `illust_type` 滤掉多页与 ugoira」不成立。**
日榜 50 条现量：`illust_type` 分布 `{0: 34, 1: 16}`，而 **type=0 的 34 条里有 12 条是多页**（`illust_page_count` 取 2/3/5/8/10/12/34/35），type=1 的 16 条里也有 5 条是单页。
⇒ 唯一干净的"只取单图"判据是 **`illust_page_count == 1`**，而它在今天这一页里只留下 **27/50 = 54%**。要凑满一屏就得翻页，或者改成"多页取第 0 页当图片"——**这是产品口径，不是技术口径，要拍板**。

**② 那两个字段在 JSON 里是字符串，不是数字。**
`typeof illust_type === "string"`（值 `"0"`）、`typeof illust_page_count === "string"`（值 `"8"`），而 `rating_count` 是 **number**。
⇒ `kotlinx.serialization` 的 data class 若把这两格声明成 `Int` 会**当场反序列化失败**（不是降级、是抛）。
⚠️ 本鱼第一版统计脚本就是因为写了 `e.illust_type === 0` 严格等于数字，量出"type=0 且多页 = **0** 条"这种**恒假的读数**——差一点就把正确结论当成"稿子判错了"写回去。所有"0 命中"先问一句量具。

**③ 第三节坑 2 有正解，但要说清测的是什么。**
pixiv 载荷确实**没有** `rating` 格（`"rating" in contents[0] === false`），沿用 `GalleryPost.kt:150` 的 `rating.lowercase() !in SAFE_RATINGS` 会把**每张 p 站卡判成成人档**（空串不在白名单里）——上一笔说的"一面打码的黑墙"是对的。
但分级材料不是没有，而是换了一格：`illust_content_type` 带 **13 枚旗标**（`sexual`（**计数**，样本首条 = 0）、`lo`/`grotesque`/`violent`/`homosexual`/`drug`/`thoughts`/`antisocial`/`religion`/`original`/`furry`/`bl`/`yuri`（布尔）），另有 `attr`（样本首条 = `""`）与 `is_masked`（false）。
⇒ pixiv 那一条 `isAdultMarked` 要**自己写来源**，并且**别把 `sexual` 当档位用** —— 它是"多少人标了性内容"的计数，不是站方分级。R-18 在匿名侧本来就拿不到（上一笔现量 `daily_r18` = 403 整档墙），所以这一档能测到的上限就是这些旗标，**要说清它是"启发式"而不是"分级"**。

### 9.3 一句话回答"能不能接入"

**能接半条腿，而且是值钱的那半。** 榜单 + 元数据 + 原图取流三跳今日全部匿名量通，骨架 `PixivClient.kt` 已在（`HttpEngine` 注入 + `NoInteractiveBypassTag` + 单例 + parse 分层，加一个 ranking 方法是顺形状不是新搭一套）；**搜索这一腿不通**，所以选型⑤ 那一排三格里的「Pixiv」**语义要当场收窄成"pixiv 榜单"**，否则搜索框对那一格不生效就是一枚假控件（本仓对假开关有判据）。
拦路的仍是第三节那两条，不因本轮复测而变轻：**坑 1**（`GalleryMerge.kt:109` 按 `entries` 循环 × `GalleryDailyFeed.kt:80-84` 只塞三把键 ⇒ 加第 4 枚枚举，首页混合墙与每日热门**当场抛**）与 **16 处穷尽 `when`** 要逐条表态。

---

## 十 首页四节 ↔ pixiv 匿名数据面（2026-10-16 深夜追加）

用户口径两句，合起来决定这一节的形状：**「先做首页画廊的推荐，先不做搜索，一步一步来」** +
**「不是替换，是切换到 pixiv 的话页面就换成这些排布，图库站保持不变」**。
⇒ 四节（**推荐 / 排行榜 / 最新作品 / 关注画师**）是**选中 Pixiv 那一格之后**的排布；
图库站那三节 `[ARTISTS, DAILY, FOR_YOU]` **一字不动**。

### 10.1 深夜新量（直连无代理，桌面 Chrome UA，23:3x–23:5x）

| 端点 | 状态 / 字节 | 读数 | 判定 |
|---|---|---|---|
| `ajax/search/artworks/{词}` | 200 / 71,933（词=1girl） | `error=false`，`body.illustManga.data` **60 条** | **搜索腿是通的** ⇒ §9.1 那行"五路全灭"就地作废（同一个错路径名打了五遍） |
| 同 `{词}?p=2` | 200 / 71,447 | 首条 `150926978` → `150611548` | 分页**真生效** |
| 同 `{词}?sort=date&order=desc`、`?sort=date`、`?order=desc`、`?sort=date_desc`、`?order=date_desc` | 200 | 五串**首三条一字不差** | 这些参数**被忽略**（默认序本来就是 `createDate` 降序，六份样本全部单调） |
| 同 `{词}?sort=desc&order=date` | 200 | 首条换成 `26575211` | 唯一换了结果的组合，形状可疑 ⇒ **不当结论用** |
| `ajax/search/artworks/all` | 200 / 69,930 | `extraData.meta.canonical` = `/tags/all`，60 条里只有 **34** 条真带 `all` | ⇒ 词**一律当标签名解释**，**不存在"全站最新"** |
| `body.popular.recent` | — | 词=1girl 时 **3 条**；词=all 时 **0 条**；且 3/3 与 `illustManga.data` 重叠 | 「推荐」拿它当料**又薄又复读** |
| `body.relatedTags` | — | **36 枚**，纯字符串数组 | 现成的**种子**来源 |
| `body` 键表 | — | `illustManga, suggestChips, popular, relatedTags, tagTranslation, zoneConfig, extraData` | 站方自己的参数写法在 `suggestChips[].url` 里：`/search?ai_type=0&s_mode=tag&order=popular_d&q={词}&type=artwork` |
| `ajax/user/129892029` | 200 / 674 | `error=false`，带 `userId`/`name` | 头像那一跳照旧（`PixivClient` 已在用） |
| `ajax/user/{id}/illustrations` | 404 / 169 | 带不带 `Referer`+`Accept` 都 404 | **「这位的新作」匿名取不到** |
| `ajax/user/{id}/illusts` | 400 / 103 | 裸、`?limit=5`、`+type=illust`、`+sort=date_desc` **四种一律 400**（不正なリクエスト） | 路由在但**不吃这套参数**，未通 —— 别写成"能取" |
| `ajax/illusts/search?user_id=…` | 404 / 169 | — | 同族另一支也不通 |
| `app-api.pixiv.net/web/v1/users/{id}/illusts?filter=for_android` | 404 / 57 | `指定されたエンドポイントは存在しません` | 同上（B 档那颗 pixez-miuix 走的就是这一族，我们够不着） |

**卡片要不要额外请求：不要。** 搜索那一腿自带 25 枚键
（`id,title,illustType,xRestrict,restrict,sl,url,tags,userId,userName,width,height,pageCount,isBookmarkable,bookmarkData,alt,createDate,updateDate,isUnlisted,isMasked,aiType,visibilityScope,profileImageUrl,isOriginal,is_howto`）：

- `url` 的 host = **`i.pximg.net`** ⇒ 落进 `data/network/ImageHeaderPolicy.kt` 现成的 `"pximg.net"` Referer 规则，取流层一个字不改（**销掉第七节待量 #4**）。
- **有 `xRestrict`** ⇒ 第三节坑 2 与 §9.2③ 那句"pixiv 载荷没有分级格"**只对 `ranking.php` 那一腿成立**；搜索这一腿自带分级。
- **有 `aiType`**（60 条里 12 枚 =1 / 47 枚 =2，而 `ai_type=0` 是站方自己在用的参数）⇒ 屏蔽 AI 有比 `GuardVocabulary` 标签词表更硬的字段可用。**这一格只登记不接**（既有口径：AI 汉化/翻译刻意不屏蔽）。
- **`pageCount` 才是多页判据**（49/60 单页，其余 2/3/4/5/6/9/11/12/15），`illustType` 这一腿恒 `"0"` ⇒ 与 9.2① 那条改判一致；用户已定「多页取第 0 页当图」，而 `url` 本来就是 `_p0` ⇒ **零额外请求**。
- 60 条里恒有 **1 条整行字段缺失**（广告/推广位）⇒ 过滤按"**字段缺不缺**"，不按值。

```bash
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
# 搜索腿 + 默认序是不是时间降序
curl -m 20 -A "$UA" -s "https://www.pixiv.net/ajax/search/artworks/1girl" -o /tmp/s.json
# 分页是否真生效（比首条 id）
curl -m 20 -A "$UA" -s "https://www.pixiv.net/ajax/search/artworks/1girl?p=2" -o /tmp/s2.json
# "词一律当标签解释"的证据：看 canonical
head -c 400 /tmp/s.json | grep -o '"canonical":"[^"]*"'
# 「这位的新作」三条全灭
for u in "ajax/user/129892029/illustrations?offset=0&limit=5" "ajax/user/129892029/illusts?limit=5" "ajax/illusts/search?user_id=129892029"; do
  printf "%-56s %s\n" "$u" "$(curl -m 20 -A "$UA" -s -o /tmp/x -w '%{http_code} %{size_download}' "https://www.pixiv.net/$u")"
done
```

### 10.2 四节各自有没有数据面（这才是"一步一步"的走法）

| 节 | 取数 | 今天落得了吗 | 要说清的代价 |
|---|---|---|---|
| **排行榜** | `ranking.php?mode={daily,weekly,monthly,rookie,original,male,female}&format=json`，`p=` 每页 50 | **能**（七档当日全 200，分页首条 rank 1→51） | 载荷 snake_case、**无分级格**、`illust_type`/`illust_page_count` 是**字符串** |
| **最新作品** | `ajax/search/artworks/{词}`（默认序即 createDate 降序） | **能，但必须挂在一个词上** | 没有"全站最新" ⇒ 词从哪儿来要拍板：候选 `relatedTags`、本地收藏的题材、首页选中那枚标签 |
| **推荐** | 同一跳的 `body.popular.recent` | **单独撑不起一节**（3 条，有时 0 条） | 与「最新」重叠 3/3 ⇒ 做成**复读页**（这病本仓犯过）。可用形状是多枚种子各取几张拼（36 枚 `relatedTags` 现成），代价是 N 笔请求 |
| **关注画师** | 名单在本地（`GalleryArtistFollowsStore`，**不与站方发生任何关注关系**），头像 `ajax/user/{id}` 匿名 200 | **只能做"名单 + 头像"** | ① 关注键是**标签名身份** `routeKey:name`，pixiv 的身份是 `user_id`，两本账不同轨（§3.4）；② 点进去那面墙匿名取不到（上表三条 404/400）⇒ 这一节**先没有下钻** |

⇒ 一步一分为：**先接「排行榜」+「最新作品」**（一跳一形状，都不等拍板）；「推荐」等种子策略点头；「关注画师」等 §3.4 那本账点头。

### 10.3 首页的形状：节清单从"一份常量"变成"按内容域取的一份表"

今天的唯一事实源是 `gallery/ui/GalleryHomeSections.kt:98-102` 那**一条**
`GALLERY_HOME_SECTION_ORDER = [ARTISTS, DAILY, FOR_YOU]`，装配在 `galleryHomeSections(...)`（`:490`，
调用点 `GalleryScreen.kt:814`），里面那个 `when(key)`（`:531-594`）**无 `else`**（加一节是编译期错误 —— 这条要留着）。

改成：次序 = `f(内容域)`，且 `f` 是**纯函数**（不进组合、单测测得到）：
- 图库站 → `[ARTISTS, DAILY, FOR_YOU]`，**逐字不变**（用户那句"图库站保持不变"的落地形）
- Pixiv → `[推荐, 排行榜, 最新作品, 关注画师]`

要显式表态的两把尺子（都是"数个数 / 钉顺序"那一族，改它们必须留一行为什么）：

1. `app/src/test/java/com/venera/compose/gallery/GalleryHomeSectionsTest.kt:30-47` —— 钉 `size == 3` 与那条顺序**字面量**
   ⇒ 改成按域取表：图库站那一档仍钉 3 与原字面量，Pixiv 那一档**另钉 4 与它自己的字面量**。
2. `app/src/test/java/com/venera/compose/gallery/UiAuditG3Test.kt:52-65` —— 数 `wallAdjacent = false` 恰好 **2** 处 / `true` 恰好 **1** 处（扫整颗 `GalleryHomeSections.kt`）
   ⇒ 四节一改计数就变；这把尺子要**按新排布重数一遍再改字面量**，不许顺手放宽成 `<=`。

一条红线继承，一条要防：

- `GalleryHomeSections.kt:94-96` 那句**节的数量与次序不许跟着数据变**仍然守 —— 现在它跟着**用户选中的域**变，
  那是人的动作不是数据，语义不同，要在注释里换成"随域、不随数据"。
- 但**切域 = 节数 3→4**，正是"第 0 项之后插入新项 ⇒ 视口锚点漂移"那一族（首页顶栏重叠那次真机缺陷的真根因）。
  ⇒ 切域那一刻要**整屏回到顶部**，且两域的节 key 各用各的、不复用。
  本项目 `app` 档没有 Robolectric/组合测试台，这条**测不到组合**，判据只能落在结构上（"次序是域的纯函数" + 两档各自钉字面量）。

另外两处措辞/键的防撞：
- 屏上那三节的现名是「正在关注的画师」(`:533`) →「每日热门」(`:562`) →「猜你喜欢」(`:580`)。
  Pixiv 域里**也有**「关注画师」那一节，但它是**另一条 key、另一份取数**（booru 那节的名字身份 vs pixiv 的 `user_id`），别复用同一个 key。
- 「排行榜」这三个字在 `gallery/` 里**现在一处都没有**；`GalleryRanking` 那枚枚举（`NEWEST/DAY/WEEK/MONTH/YEAR/ALL`）
  是**搜索卡里那排时间窗**，跟首页"排行榜"那一节不是一件事 —— 别拿它当数据源，也别让它复用同一把 `when`。

### 10.4 这一轮改判的四句话

1. **搜索腿通**（§9.1 那行作废）：`ajax/search/artworks/{词}` 匿名 200。⇒ pixiv 不进来源轴的理由换成**语法不同轴**（选型⑤ 走内容域），是**选择**不是数据墙。
2. **分级格在搜索这一腿有**（`xRestrict`）：坑 2 缩到只咬 `ranking.php` 那一腿。
3. **多页判据是 `pageCount`**，`illustType` 两腿都分不开单图/多页（9.2① 的重述）。
4. **「这位画师的新作」匿名取不到**（三条路各 404/400/404）⇒ 「关注画师」那一节先只能停在名单 + 头像，**不下钻**。

---

## 十一 落地那一版改了什么（2026-10-17 凌晨，用户两次改口径之后）

### 11.1 口径改动（都是用户点名，不是本鱼自己换的）

| 从前（本文件 §六 与那张示意） | 现在 | 出处 |
|---|---|---|
| 首页那一排三格：综合 / 图库站 / Pixiv | **两格**：图库站 / Pixiv | 用户 2026-10-17「不需要综合，只需要图库+pixiv这样切换」；本鱼的现量旁证：每日热门那台合并机器只吃 booru 三站，「综合」与「图库站」是**同一个读数** ⇒ 假开关 |
| Pixiv 域四节：推荐 / 排行榜 / 最新作品 / 关注画师 | **三节，次序 = 关注画师 / 排行榜 / 推荐**（**去掉**最新作品） | 用户 2026-10-17 第二次改口径 |
| 「关注画师」= 复用本地那份关注名单 | = **pixiv 站方的推荐用户**，不许拿图库站那份顶 | 用户 2026-10-17「直接用 pixiv 的就行不要用图库的关注画师」 |
| 匿名取不到的一律不摆 | **明说一句"需要登录 pixiv 账号"**，账号系统本轮不做 | 用户 2026-10-17「匿名走不了的，显示需要登录 pixiv 账号就行」⇒ 本节 §11.3 那句就是这条口径的落地形（与 §八「不做鉴权」不冲突：这里不接登录，只说实话） |

### 11.2 排行榜那一节的形状（照用户那张稿）

节头左「排行榜」+ 右「查看全部」；下面五枚档名药丸（日榜 / 周榜 / 月榜 / 新人 / 原创）；再下面**一行三张**：封面在上，下面两行是 `01 · 作品标题` 与画师名。

- **不用 `VeneraSegmentedButton`**：那颗是等宽分段器（每格 `weight(1f)`），稿上是跟着字长走的药丸 ⇒ 自持件画在 `gallery/ui/GalleryPixivHome.kt`。顺带绕开两把按**所在文件**算的尺子（`GalleryHomeChromeTest` 数分段器、`UiAuditG3Test` 数 `wallAdjacent`）。
- **「查看全部」今天不是二级页入口**（本轮没有新路由），行为是真的：三张 ⇄ 21 张，文案跟着变「收起」。接榜单二级页时那一枚改一个字即可。
- **序号取站方 `rank`**（1..500），不是"这一排第几枚" —— 展开、翻页、去重之后两者就不等了，拿位次冒充排名就是编读数。
- 这一排的卡**不参与共享元素飞行**（没接 `GalleryFlyIn.capture` + `coverSharedElement`）；表现是走"整页从下沿抬上来"那条既有退路，**不是缺陷**。要接得与 `GalleryPostActivity` 那侧 key 同串，不同串会静默不飞。

### 11.3 「关注画师」那一节今天只有一句话，理由量过

| 路 | 状态 / 字节 |
|---|---|
| `app-api.pixiv.net/v1/user/recommended?filter=for_android` | **400** / 191（要凭据；同栈那颗 pixez-miuix 走的正是这条，见引用稿 §2 B 档） |
| 退出的首页 HTML 预载 `recommendUsers` | `{"analizedUserId":null,"users":null,"followedUserIds":[],"usersWithoutUserId":[]}` ⇒ 匿名**恒空** |
| `ajax/user-rank/illust`、`ajax/rank/user`、`ajax/user/recommended`、`ajax/recommend_users`、`user/index/recommended-follows`、`showpixiv/user/recommend` | 六条**不同名字**的路全 **404 / 169** 同一形状（这次按上次的教训数过：不是同一个名字打六遍） |
| `ranking.php?mode=user&format=json` | 404 / 108（ユーザーランキング那档站方已撤） |

⇒ 节内那句是「这一栏要读 pixiv 的推荐用户，而站方那一页要登录才给 —— 本 App 还没有 pixiv 账号系统。」
**不摆**点不动的假列表，也**不拿**图库站那份名单顶（键是 `routeKey:标签名`，pixiv 的身份是 `user_id`，两本账不同轨 = §3.4）。

> ⚠️ **2026-10-17 就地改判（账号系统 P3 落地那一批）**：上面引号里那句后半当场变假话 ——
> pixiv 账号系统已经有了（顶栏左槽那颗头像，方案 `gallery-pixiv-account-pkce-2026-10-17.md`）。
> 那一节现在**跟登录态念两句话**：没登录 → 「…而站方那一页要登录才给 —— 点顶栏左边那颗头像登录 pixiv」；
> 已登录 → 「…那一条取数还没接上」（`app-api/v1/user/recommended` 带 Bearer 那一跳 = 方案 §四 P5，
> 等 client 凭据贴进来才量得动）。判据 `关注画师那一节的话跟登录态同源` 钉着那句假话不许回来。
> **前半段（匿名六条路全灭）不受影响，仍是那一节不摆列表的理由。**

### 11.4 「推荐」那一节的种子

`GalleryRecommendations.recommendBySite(收藏)` 从**图库站收藏**抽出前三枚推荐标签 → 每枚打一跳 `ajax/search/artworks/{词}` 取头两张 → 与排行榜那一排按 `uid` **去重** → 至多 6 张。
- 为什么借另一侧的语料：pixiv 今天还没有可收藏的卡（这一批才让它上墙），"你在 p 站收藏过什么"恒为空；而"你常看哪些标签"是真读数。
- 这一腿 `rank` 恒 null ⇒ 那一节**不摆序号**（摆就是编）。
- 种子为空 / 三枚词都没取回图 ⇒ 节内念那一句真话（带词名），不摆空墙。

### 11.5 结构改动与两把尺子

- `GALLERY_HOME_SECTION_ORDER`（一颗常量）→ `galleryHomeSectionOrder(domain)`（**纯函数**，两档都是四节）。图库站那一档除第一枚外**逐字不变**，并有 `切域不动图库站那三节也不改节数` 那笔钉着（两档节数相等是为了压"第 0 项之后插入新项 ⇒ 锚点漂移"那一族；切域另外 `scrollToItem(0)`）。
- `UiAuditG3Test` 那两枚计数 2/1 → **6/1**（`grep -c` 现量：`wallAdjacent = false` 六处、`true` 一处），**没有放宽成 `<=`**。
- 打码那把**复用 `buildGalleryWall`**（与首页其余三批同一颗），不在 pixiv 这两节里另立判式 —— 否则会长出"预览行上没被挡、主墙上全裸"那族分叉。
- 新端口槽 `GalleryPorts.pixiv: GalleryPixivWorks?`（`ranking` / `tagWorks` / `fetchById`），安卓侧接 `PixivClient`，桌面侧默认 null ⇒ 那一侧念"这一侧不接 pixiv"而不是悄悄交回空。
- 三处 `notWired` 改真调用：大图页单张补取、历史那一行按号回取（都走 `/ajax/illust/{id}`，现量 200 / 26,642 字节、五档图址齐、`original` 扩展名**逐张不同** ⇒ 从地址尾巴取）、卡片来源徽标（pixiv 是**字母药丸 "P"**，与 `sourceMarkLetter` 同字；没有随包站标资产，"拿一枚像的顶上"比"没有"更糟）。

### 11.6 本鱼自己抓到的一处假绿（记下来，别再犯）

`Pixiv 那一档的次序…` 与 `切域不动图库站那三节…` 头一遍**根本没跑**：批量插入时漏了 `@Test`，JUnit 静默不收，套件读数仍是 `failures="0"`。是数出来的（`tests="4"` 而文件里有六颗 `fun \``）才发现，不是红出来的。
⇒ 加判据之后**要数用例条数**，别只看"0 失败"。

## 十二 第二轮：切换器换形状、推荐那一池改走主墙（2026-10-17 凌晨 02:48，用户带截图点名）

用户原话两条：「**改下切换站的按钮，做成这样的会更好，现在是左对齐太浪费空间了**」
（附一张稿：居中的中型双段胶囊，选中那枚柔和底色，下面写着「中等宽度 · 居中 · 柔和选中底色」）
与「**pixiv 站的推荐应该是做成和图库站猜你喜欢那种的，不是现在的卡片状**」。
真机截图同时确认了顶栏左槽那颗头像已上屏、与右上两枚同轴。

### 改了什么

1. **切换器**（`GalleryPixivHome.kt` 的 `GalleryHomeDomainChips`）：从"两枚各自涂色的药丸 + `fillMaxWidth` 左对齐"
   换成**一颗居中的单容器分段器** —— 外层 `Box(fillMaxWidth, contentAlignment = Center)`，
   容器涂 `surfaceVariant` 一档、内衬 `space1`，**格子自己一颗都不涂**（旧版两枚在抢同一行的注意力，
   读不出哪个是选中的）。高度吃现成的 `tokens.spacing.segmentedHeight`（48dp 那一档），不另立数。
2. **选中底色会滑过去**：底色不是各格自涂，而是容器 `drawBehind` 里画的一枚圆角矩形，
   左/上/宽/高各走一条 `animateFloatAsState`。两格字长不同（「图库站」比「Pixiv」长），
   所以**不能按"等分 × 序号"算**，量的是每格放置后的真实矩形
   （`coords.parentCoordinates?.localBoundingBoxOf(coords)` —— Compose 的 `LayoutCoordinates`
   没有 `positionInParent` 这一颗）。四个数只在**绘制期**读，不进组合期。
3. **推荐那一池改走首页主墙**：`section-pixiv-recommend` 从"一排 `PixivRankedWorkGrid` 卡（六张、带标题行）"
   变成**只有节头**（`wallAdjacent = true`，与「猜你喜欢」同形），整池交给脚下那面墙去摆；
   每枚种子的取数从 2 张抬到 8 张（`RECOMMEND_ITEMS = 6` 那道总闸撤掉，改名 `RECOMMEND_PER_SEED = 8`）——
   六张铺不满一屏墙。
4. **首页那面墙的读数抽成一份**（`GalleryHomeWallReading`）：这一页从前摊开 `fvm` + `wall` +
   `favoriteCount` + `onGoToDaily` 四样、函数体里五处各读一遍。两档域共用一面墙之后，
   那种摊法会让"屏上是 pixiv 那一池、页尾还念 booru 两站的张数"成为默认写法。
   现在 `forYouWallReading(...)` 与 `pixivRecommendReading(...)` 各造一份，页里只读 `reading.*`。
   Pixiv 那一档**刻意不摆页尾**、`notices` 恒空：按站张数 / 第几页 / 排掉几张已收藏，
   这一池一条都成立不了 —— 宁可不念，也不拿别的账凑一句看起来像读数的话。
5. 大图页那一池的交接点从写死 `forYouWall` 改成 `homeWallReading.wall.cards`（**队列 = 屏上那一池**），
   否则切到 Pixiv 域就是"卡是 pixiv 的、左右滑过去全是 booru 的"—— 与本仓那族"复读页"同一形。

### 判据（都是重新数的，没有一条改松）

- `UiAuditG3Test`：`wallAdjacent` false **6→5**、true **1→2**（两档域各一面主墙）；
  新增 `首页那面墙只读一份读数，不在页里翻对岸的账`（页体切出来，`fvm.` 必须 0 处）；
  新增 `域切换那一排是居中的单容器分段器，未选那一格不涂色`（居中 / drawBehind / 四条动画 / 量真实矩形 /
  `.background(` 恰一处 / 吃 segmentedHeight / 零裸 dp，七条一起）；
  `pixiv 两节各自把整池交给大图页` 改判成"节里 recommend 交接点 0 处 + 墙那侧 1 处 + 旧写死 0 残留"。
  现量 **6 笔**（声明数 = 跑出来数）。

### 读数

全量 `:app:testDebugUnitTest` **174 类 / 1369 笔 / 0 失败 0 错误**（上批 1367 + 本轮新增 2 笔），
`174 颗结果 XML 全属本遍`；`:desktop:compileKotlin` 绿；`assembleDebug` universal 02:48 **103,049,397** 字节。

### 量具自身的三处坑（本鱼自己踩的，记下来免得再来）

- `Array.splice(i, 0, 数组)` 少写展开号 ⇒ 整块被 `Array.toString()` 用**逗号并成一行**塞进源码，
  编译器只报"unresolved reference"，看不出根因。⇒ 插多行一律 `splice(i, 0, ...arr)` 并**回读一次**。
- 本地 `val height` 会把 `Modifier.height` 遮掉（报 "Function invocation 'height(...)' expected"）⇒ 动画值一律带前缀。
- `Rect.width` / `Rect.height` 在 1.12 是**属性**不是函数（写成 `box.width()` 直接编译错）。

### 仍未量（要上机）

- 切换器在 PJZ110 上的实测：整排居中之后与搜索条的左右缘读不读得成一组、底色滑动跟不跟手；
- Pixiv 域那面墙首屏铺几张、与上面排行榜那排的**去重**还在不在（同一张图两处出现就是复读页那个症状）；
- 排行榜那五档（日榜/周榜/月榜/新人/原创）今天仍是"每枚各自涂色"的老形状 —— **用户没点它**，
  要不要一起换成同一颗单容器，等这一版上机看过再说（五档塞进一颗容器会很宽，不是白送的）。
  ⚠️ 第三轮（同日 07 点前后）之后这一条**部分作废**：域那一排已搬进搜索那一行，排行榜那五档仍未动。

## 十三 第三轮：Pixiv 空态吞掉整页（真机缺陷）+ 域那一排并进搜索那一行（2026-10-17 上午）

**题面（原话三条 + 两张图）**：「3：现在切换到 pixiv 会这样」（一张浅色档真机截图：整页只剩一句
「pixiv 这一轮没有回图 / 种子那几枚标签这一轮没从 pixiv 取回图（「halo、loli、nopan」）/ 重试」，
**域切换与三节一起不见了**）+「2：看下站源切换能不能做成这样」（一张稿：那颗分段器与搜索框**同一行**，
分段器在左、按字长收）。

### 一 · 吞页的成因（行 217 那一轮带进来的）

`GalleryForYouPage` 从前是三档 `when`：`cards.isEmpty() && pending` 摆一颗占满页面的居中波浪环、
`cards.isEmpty()` 摆一颗占满页面的 `VeneraEmptyView`、`else` 才是那颗 `GalleryCardsGrid`。
**前两档不把 `chrome` / `hero` / `sections` 交出去** —— 而「图库站 / Pixiv」那颗切换器就住在
`sections` 里（`section-domain`）。图库站那一档今天很少空，所以这条形状一直没被走到；
第二轮把 Pixiv 的推荐整池改成脚下那面墙之后，**Pixiv 域空池是常态**，于是当场走到：
用户切进 Pixiv → 池是空的 → 切换器没了 → **困在那一域**，屏上那句"稍后再刷一次"是唯一的出口。

改法：**只剩一条形状**。空/忙两档不再各自换页，而是占用 `GalleryCardsGrid` 的 `footer` 那一格
（= "卡片该在的那一格"），chrome、头图、三节、节头全部照常上屏。

```kotlin
footer = when {
    cards.isNotEmpty() -> reading.footer      // 图库站那份按站张数/页码；Pixiv 恒 null
    reading.pending -> loadingSlot            // 还没判过档 → 环
    else -> emptySlot                        // 真没内容 → 四档空态之一
}
```

`cards.isEmpty()` 那道前提**保留**（2026-09-30 第三轮那条理由一个字没改：换一批时不许把脚下的卡换成环）。

### 二 · 顺带抓到的一枚真缺陷：那一池**恒空**

截图那句"没有回图"本身也是假的。取数那一段写成了

```kotlin
val found = pixivWorks.tagWorks(seed, page = 1).getOrNull().orEmpty()
found.take(GalleryPixivHome.RECOMMEND_PER_SEED)   // ← 算完就丢，picked 永远是空
```

第二轮搬池子的时候把 `picked +=` 落下了（同一批里那一行还失去了缩进 —— 是本鱼那个 node 脚本的痕迹）。
⇒ 无论种子给不给图，Pixiv 那面墙都是 0 张。**修法**：`picked += found.take(…)`。
判据不钉符号名（`take` 在不在看得见这条死分支吗？看不见），钉**整串赋值表达式** +
"结果没人接的那一形状"数零：`GalleryPixivAccountWiringTest` 同族的 `UiAuditG3Test` 三笔（见下面"数字"）。

去重那半条口径同时改判：从前注释写着"与排行榜那一排去重"，代码里其实只有**种子之间**按 `uid` 去重。
现量两批取数不是同一次命中（排行榜=按分数、这池=按种子标签的最新作），且形状也不同
（上排是带标题的小卡、脚下是瀑布流），**注释改成真话而不是把代码扩成第二条判据**（本仓的规矩：
措辞要能被现量复算，不然下一轮就有人拿它当规格）。

### 三 · 域那一排并进搜索那一行

- `section-domain` 整节摘掉：`galleryHomeSectionOrder` 两档各从 4 节变 **3 节**（摘的是同一枚 key ⇒
  不是"某一档少一节"那种会漂锚点的形状）；`GalleryHomeSectionKey.DOMAIN` 常量一起删。
- `GalleryHomeChrome` 加两颗**必填**参数 `domain` / `onPickDomain`（不给默认值：漏传就是把切换器整排摘掉，
  而"切不回图库站"正是本轮刚修过那一族）。
- 摆法：那一行的外框高度常量 `galleryHomeChromeHeight()` **一个字没改**（仍 `space2 + dockedSearchBarHeight + space3`）；
  切换器套一圈 `padding(top = space2, bottom = space3) + height(dockedSearchBarHeight)` 的外框、内部 `Center` ⇒
  与搜索条**共用同一条 56dp 的带**（48dp 那档坐在里面），不写"往下挪 4dp"这种自造数字。
- `GalleryHomeDomainChips` 撤掉外层 `Box(fillMaxWidth, Center)`：**不铺满、不居中**，宽度交给那颗 Row。
- `PixivHomeState.onPickDomain` 一起删（它的唯一消费者搬走了；留着就是"实现了但零调用点"那族）。

### 四 · 尺子（每一条都下过笔）

| 判据 | 钉什么 |
|---|---|
| `UiAuditG3Test · 空档与加载档不许把域切换那一排摘走`（新） | 墙只有一条形状（`GalleryCardsGrid(` 数 1）、`sections = sections` 数 1、`chrome?.invoke()` 数 1、`hero?.let` 数 1、`VeneraEmptyView(` 数 1、空/忙两档走 `footer` 的三条表达式、两条旧判式各数零残留 |
| `UiAuditG3Test · pixiv 推荐那一池的种子必须真的进池`（新） | `picked += found.take(…)` 整串一处、`found.take(` 独立成行零处、`pixivRecommend = picked.distinctBy {…}` 一处 |
| `UiAuditG3Test · 首页各节对收口必须逐个表态` | `wallAdjacent` false 5 → **4**、true 仍 **2**（**重新 `grep -c` 数出来的**，没放宽） |
| `UiAuditG3Test · 域切换那一排是单容器分段器，不铺满也不各格涂色`（改判） | 第二轮那句"整排要居中"钉的是 `contentAlignment = Alignment.Center` —— 而这颗文件里**每一格**都在用同一句，第三轮把外框摘掉它照样绿（**锚点被同屏另一处合法调用喂饱**）。改判成负向尺：`fillMaxWidth` 数 **0** |
| `GalleryHomeSectionsTest · 内容域那一排在搜索那一行的左格，不再是一节`（新） | 两档清单都不许含 `domain`；chrome 里 `GalleryHomeDomainChips(` 数 1；`.height(tokens.spacing.dockedSearchBarHeight)` 数 **2**（条自己 + 切换器那圈 —— 只问"有没有"会被条那一处喂饱） |
| `GalleryHomeSectionsTest` 那四笔次序尺 | 两档各 3 节、`section-domain` 零、图库站那三节逐字不动（`booru.drop(1)` 改成 `booru`） |

台架 `_qa/b120_perturb.js` 共 **6 笔**：K1 把外框改回 `fillMaxWidth` / K2 把清单 host 改错一个字符 /
K3 把切换器那圈外框改成 `segmentedHeight` / K4 把失败推成 `NeedsRelogin` / K5 把 `ACTION_VIEW` 换成 `ACTION_SEND` /
K6b 复制第二个换票据消费点 ⇒ **6/6 咬住、逐笔对题、还原零残留**。
⚠️ K6 **第一遍没咬**：终点串写成 `completeLogin(intent.url + "")`，判式的 needle 数不到它 ⇒
是**夹具把那一格递错了形**，不是尺子不咬；重下成"两行逐字相同"之后咬住。

### 五 · 数字与读数

- 全量 `:app:testDebugUnitTest` **174 类 / 1,376 笔 / 0 失败 0 错误**（上批 1,369 → 吞页与种子尺 +2、
  登录批 +5）、`:desktop:compileKotlin` 绿、`assembleDebug` 绿（universal 10:38 **103,797,029** 字节）。
- 用户截图里那三枚种子（`halo`、`loli`、`nopan`）读数是**种子抽出来了、取数被那行死表达式丢掉**，
  不是站方没回图 —— 那句空态文案当时是假话。修完之后 Pixiv 那面墙应当是 3 枚种子 × 8 张、按 `uid` 去重。

### 六 · 仍未量（要上机）

1. **切换器与搜索条同一行的观感**：48dp 那颗坐在 56dp 带里读不读得成一组、左边那 11dp 的空够不够、
   窄屏（PJZ110）上"图库站 / Pixiv"加搜索条会不会把条挤得太短。
2. **展开搜索那一下的共享元素**：条现在比从前窄（让出了一格给切换器），而卡片是满幅的 ⇒
   "同一块面在长高"还读不读得出来（第三轮唯一一处可能退了的观感，本鱼离线证不了）。
3. Pixiv 那面墙首屏铺几张、`distinctBy` 之后是否还剩够一屏的量。
4. 排行榜那五档要不要也换成同一颗单容器（用户没点它，五档塞进一颗会很宽）。

