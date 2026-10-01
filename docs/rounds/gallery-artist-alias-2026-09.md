# 画廊搜索：结果为 0 时的站内画师别名解析 —— 方案（批次 J，未开工）

日期：2026-09-30　模块：图库（画廊）搜索墙　形态：不新增目的地、不新增数据表
状态：**方案已定，未动代码。** 排期在「画廊首页重构」之后（用户 2026-09-30 指定顺序）。
探针全部走本机代理 `-x http://127.0.0.1:7890`，测量时间 2026-09-30；一次性产物在 `_probe/hub/`，可随时删。

## 〇、这份方案是从一份更大的提案改出来的，那条主张被实测推翻

外部提案的原话是：**把 Danbooru 当作画师身份的跨站枢纽**，用户输入的词先去 Danbooru 解析成
各站自己的 artist tag，再分站搜索；核心样例是
`Yande = setmen / Gelbooru = tokenbox / Danbooru = tokenbox`。

真实读数把这个样例**方向整个反了**（§一.1）。后果不是"枢纽精度差一点"，而是：
按提案跑它自己的 Case 1，Danbooru 给出的名字对 Gelbooru 是多余的（那一站本来就叫 setmen），
对 yande.re 是错的（送 setmen 过去 = 0 张）。**所以 Danbooru 不进这一轮**，
改造范围缩为：**各站用自己的元数据自解，且只在原始查询返回 0 张时触发。**

另两条现状：Danbooru 已不是接入站（`gallery/data/GallerySite.kt:20,31` 只有 YANDERE 与 GELBOORU），
画廊代码从未调用过任何画师/别名元数据端点（`/artist.json`、`artists.json`、`tag_get`、`alias` 全仓零命中）。

## 一、实测底账

### 1.1 提案样例的三站读数（`setmen` / `tokenbox`）

| 站 | 提案说 | 实测 |
|---|---|---|
| yande.re | setmen | `tags=setmen&limit=100` → **0 张**；`tags=tokenbox` → **100 张**（首页满）。画师记录 `setmen`(id 39892) 带 `alias_id=46523`，而 46523 就是 `tokenbox` 那条记录（`urls` 里是 pixiv 7075448 / twitter Setmen_uU / setmen.fanbox.cc） |
| Gelbooru | tokenbox | 匿名补全端点里**没有 `tokenbox` 这个标签**（返回空数组）；有 `setmen`，`post_count=362`，`category=artist` |
| Danbooru | tokenbox | 画师记录是 **`setmen`**(id 183883，`other_names` 为 `u_u` / `セトマン`)；标签 `tokenbox` 存在但 `post_count=0` |

真实分布：`yande = tokenbox`、`Gelbooru = setmen`、`Danbooru = setmen`。

### 1.2 两站画师名的真实分歧率（25 个头部标签，样本 = yande `tag.json?type=1&order=count&limit=25`）

| 读数 | 值 |
|---|---|
| 同名在 Gelbooru 与 Danbooru 都存在 | **22 / 25** |
| 同名存在但对方站上只有个位数（名字在、内容不在） | `tinkle`（yande 2252 / Gelbooru 6 / Danbooru 0）、`twinbox`（1739 / 7 / 0） |
| 该站独有、两站都查不到记录 | `harahera`（2190）、`ruzhai`（1884）—— **Danbooru 也 0 条记录，枢纽给不出答案** |
| 本轮取数失败，不计入读数 | `suzuhira_hiro` |

实质分歧约 **4~5 / 25（16~20%）**。
⚠️ 另外：**帖子上的画师标签本身就是正名** —— 抽 12 个头部画师记录，`alias_id` 全为 null。
所以"点卡片里的画师名"这条路径今天不会踩到错名，**只有用户手输才踩**。

### 1.3 站方的 tag 别名已经自动生效，我们不该重做

`tags=vocaloid_2&limit=100` → **100 张**，而 `vocaloid_2` 在 `tag.json?name=` 里查不到（它不是标签，只是别名）。
⇒ **标签级别名由站方在服务端解掉，本方案只管剩下那一类：画师记录名存在、但它不是标签别名**（`setmen` 正是这种）。

### 1.4 yande.re 的解析链（实测通，两笔请求）

```
GET /artist.json?name=setmen            → [{id 39892, name setmen, alias_id 46523}]
GET /artist/show/46523                  → 302 → /wiki/show?title=tokenbox（匿名可读，200）
正名直接从**落点 URL 的 title 参数**取，不用解析 HTML 正文
```

抽样 16 条 `alias_id` 全部跟到了 `wiki/show?title=<正名>`，无一例外。

**两条必须写进判据的坑**：
1. `artist.json?name=` 是**前缀匹配**（实测 `name=se` 回 16 条）。必须按 `name == 原词` **精确取记录**，否则会拿错人。
2. `artist.json` 的 `id=` / `ids=` / `show=` **全部被静默忽略**（回默认列表 25 条），
   `tag_alias.json` 的 `name=` / `search[name]=` / `query[name]=` 同样忽略。
   除 `name=` 前缀这一条外，没有更短的取正名路子。

### 1.5 收益与风险（抽样 11 条"别名记录"，逐条比 原词 vs 正名）

| 形态 | 条数 | 例 |
|---|---|---|
| 原词 0 张、正名有货（本方案的净收益） | **7 / 11** | `a.m.r`→`akane_makes_revolution`(100+)、`a4typhoon`→`fish.boy`(43)、`a.s.ヘルメス`→`a.s._hermes`(10) |
| 原词有货、正名更多 | 2 | `a4` 2 张 → `hy` 17 张 |
| **原词有货、正名反而更少** | 2 | `a-10` **12 张** → 正名 `fuwa_daisuke` 只有 **1 张**；`a3eilm2s2y` 4 → `vmax-ver` 5 |

最后那一行是本方案的**红线依据**：站方别名指针可能指向一个内容更少的名字，
所以**绝不能在非 0 张时替换查询词**。触发条件收紧为 0 张，正好把这条风险排除在外。

### 1.6 Gelbooru 这一轮接不了（候选端点逐个试过）

| 端点 | 结果 |
|---|---|
| `index.php?page=autocomplete2&term=&type=tag_order` | **匿名 200**，回 `name + post_count + category`，前缀匹配 —— 可用，但它只列**真有内容的标签**，`tokenbox` 回空数组，别名不在表内 |
| `page=tag_details&tags=`、`page=tag_list&do=alias` | 200 但只有 3383 字节的 JS 壳，无数据 |
| `/related_tags`、`/api/v2/tags/` | 404 |
| `dapi` 全家（`s=post` / `s=tag` / `s=tag_get`） | **匿名 401**（要 `api_key`+`user_id`，与 `GallerySite.kt:22-31` 注释一致） |

⇒ 这一轮**只做 yande.re**。Gelbooru 保持 0 张、界面上不装出"两站都会自动换名"。
有凭据的 DAPI 是否暴露画师别名**没测**（不从设备取凭据）。

### 1.7 随包词典帮不上（离线归一这条路不通）

`app/src/main/assets/gallery_tags_79415.sqlite` 只有一张表 `tags(name, category, cn)`，79415 行，**没有别名表**。
⇒ 不做离线归一，也不扩充这份资产。

## 二、拍板（用户 2026-09-30 原话为准）

> 在明确的 Artist 搜索场景中，某站原始查询返回 0 张时，查询该站 Artist 元数据，
> 尝试解析 canonical name / aliases / source-specific identifier；
> **只有获得明确站内关联时才重搜，否则保持 0 结果。**

这份方案把它落成 §三 的四条判据。其中"明确站内关联"= 该站自己的画师记录指针（yande 的 `alias_id`），
**不接受**名字相似度、不接受词典、不接受第三方站。

## 三、设计

### 3.1 触发判据（六个条件全满足才动，缺一不动）

现状可区分的"0 张"成因（已核）：站方空表 vs 无图被丢（`droppedNoImage`，`GallerySearchViewModel.kt:659,624`，
读数函数 `noImageNotice()` :909-911）vs 被屏蔽/AI/分级挡完（**不在 VM**，在 UI 派生层 `buildGalleryWall`，
`GalleryScreen.kt:1056-1079`，此时 `results` 非空只是 cards 空）vs 网络失败或熔断（`onFailure` :776-781 写 `searchError`）。

判据（纯函数，见 §3.5）：

```
searchError == null                     // 不是"没搜成"
&& nextPage == 1                        // 只认第 1 页
&& list.isEmpty()                       // 站方给了 0 行
&& droppedNoImage == 0                  // 不是"给了条目没给图"
&& 屏蔽后没有命中记录（blockedCount == 0）// 不是被自己的规则挡空
&& 本轮还没为（站 + 原词）解析过          // 防循环
```

⚠️ **不许把失败当空**：熔断/超时会走 `onFailure`，那一支绝不触发解析（否则给已经熔断的主机再压两笔）。
时间窗口档（`rankingWindowActive()`，空态读数在 `GalleryScreen.kt:711-715`）**照常触发**：窗口内 0 条与"这一串搜不到"
是两件事，但换名重搜在窗口语义下依然正确（同一窗口、换正名）。

### 3.2 "明确的 Artist 场景"怎么判定（不收 UI 新开关）

现有搜索没有"按画师搜"这一档，输入就是一串 booru 条件。判定用**该站自己的元数据**，不猜：

- 用户那一排 `effectiveFilters()` **只有 1 枚标签**时才解析（多条件时 0 张不一定是画师名的锅，
  而且会把另一枚条件的锅算到换名上）；
- 这一枚在 `artist.json?name=` 里能按**精确同名**取到一条画师记录 —— 这就是"明确的 Artist 场景"，
  判据来自站方，不来自我们的词表或字符串形状；
- 拿到的记录 `alias_id != null` 才算"明确站内关联"。

⚠️ 判据必须读 **filters，不能读发出去的 query**：那一串里混着排行伪标签
（`GalleryRankings.searchQuery`，调用点 `GallerySearchViewModel.kt:755-761`），按它枚数就错了。

### 3.3 动作

1. 两笔请求拿正名（§1.4）；
2. **正名 != 原词** 才用正名重跑第 1 页；
3. 重搜走 `setRanking`(`GallerySearchViewModel.kt:565-574`) 那套「原地重搜、不压栈、`contextRound++`」的现成写法，
   **不碰 `openContext`**（:361-390，压栈判据 `GallerySearchContextStack.plan`）—— 重搜不是一轮新搜索；
4. 每轮只解析一次（VM 记一个 `已解析(站, 原词)` 标记）。正名也 0 张 → 就停在那，**不再递归**。

### 3.4 展示与失败口径

- **历史与胶囊仍存用户原词**。现状是重搜会把历史写成 tokenbox（`saveHistory` 在 :775，取 `visibleQuery`），
  本方案要另开一条"发出串 ≠ 展示串"的通道。仓库里今天只有排行伪标签这一处这种形态（:755），没有第二条，需自己加。
- 落地后加一行读数，由 VM 提供 `artistAliasNotice()`，与 `noImageNotice()` / `budgetTrimNotice()` 同口径，
  **空态 message 那串（`GalleryScreen.kt:718-730` 的 `listOfNotNull`）与页尾两处都念**。
  文案（要过一遍去 AI 味的口径，不上内部黑话）：
  - 搜到了：`这一站把 setmen 记作 tokenbox 的别名，已按 tokenbox 搜索`
  - 仍为 0：`这一站把 setmen 记作 tokenbox 的别名，按 tokenbox 也没有图`
- 解析过程本身失败（非 2xx、非 JSON、没跟到重定向、精确同名取不到记录、`alias_id` 为空）：
  **屏上仍是那一屏 0 张**，不显示任何"已按 X 搜索"的暗示。
  这条不是错误，不能升成第二条红色读数；但也**不许静默**——按现口径归到"这一串标签没有可摆的图"，
  即用户看到的内容与不做这个功能时**完全一致**。

### 3.5 文件面与单测

| 文件 | 做什么 |
|---|---|
| `gallery/domain/GalleryArtistAlias.kt`（新） | 纯函数四枚：触发判据、精确同名取记录、从落点 URL 取正名、正名==原词不替换。没有 Robolectric，判据层必须可单测 |
| `gallery/data/YandeReClient.kt` | 两个元数据方法，照该类现有的取数形状 `requestList`(:109-125)：请求构造与 `NoInteractiveBypassTag` 在 :110-116，非 2xx `throw IOException("yande.re 返回 …")` 在 :121，200 但不是 JSON 也炸（:122-123 那条口径正好挡住"挂维护页被读成没有别名"）。返回 `Result`，**不许静默交 null** |
| `gallery/ui/GallerySearchViewModel.kt` | 落地处接线（:770-775 那一支）+ 已解析标记 + `artistAliasNotice()` + 展示串/发送串分离 |
| `gallery/ui/GalleryScreen.kt`、`GallerySearchArea.kt` | 空态 message 加一项、页尾加一项、胶囊仍显示原词 |

单测（**先写红的**）：
1. 六个触发条件逐个单独置假 → 不触发（重点：`searchError` 非空那一支必须拦住）；
2. 前缀列表里混着 15 条别的名字时，只取 `name` 全等的那条；全等取不到 → 不解析；
3. `wiki/show?title=tokenbox` 取到 `tokenbox`；落点是别处（如登录页）→ 交回"没有关联"；
4. 正名 == 原词 → 不重搜；
5. 正名也 0 张 → 不再解析第二次（循环闸）；
6. 多枚条件 → 不解析。

**缓存**：默认只做内存 `LruCache`，照 `GalleryTagCategories.kt:35`（键 = 原词，容量取 64 那一档口径，
**只有成功才入缓存**、无 TTL、纯逐出）。**不落磁盘档**、不建表。

## 四、明确不做

1. **不建 `GalleryArtist` / `GalleryArtistSourceMapping` 两张表**（提案 §三）。触发条件是"0 张"，
   每次两笔请求，频率极低，两张表换不来东西；且提案假设的 SQLDelight 在本仓不存在
   （实际是裸 `SQLiteOpenHelper`，而画廊刻意走 JSON 文件档，理由写在 `GalleryFavoritesStore.kt:17-22`）。
2. **不请回 Danbooru 做别名解析**（§〇）。附带一条现实约束：它的 `/tags.json?name=`、`/artists.json?url=` /
   `search[url]` **都被静默忽略**（回 200 + 合法 JSON 的最新列表），
   提案 §四 要的"相同 Pixiv user ID / 官方主页互指"那套 CONFIRMED 级证据，匿名路拿不到。
   ⚠️ **批次 L 给这一条开了一个例外，且只限一件事**：`/artists.json?name=` 按**精确同名**取那条记录的
   `urls`（画师的外链地址），用来给 Gelbooru 侧的画师摆平台入口 —— 那里 Gelbooru 自己给不出任何东西
   （DAPI `s=artist` 匿名 401；`page=artist&s=list&name=` 那个 `name=` 实测**根本不生效**：正例、
   不存在的名字、不带参数三次返回逐字节相同）。**别名解析仍然不请它**：这条例外不改变上面那句
   "CONFIRMED 级证据匿名拿不到"，它取的是"站方替这位记了哪些外链"，不是"这两个名字是不是同一个人"。
   另记一条当天状态：danbooru / safebooru / testbooru 三站从本机出口**全部 403 卡过盾页**，
   所以这一路的字段形态是按站方源码候选形态**全部兼容**实现的，真样例待过盾后回核。
3. **不做跨站 artist 映射**（提案 §六 的主链路）。跨站聚合搜索是另一件事，见 §五。
4. **不做图像相似度 / 作品反推**（提案 §十一）。`GalleryPost.kt:169-171` 的注释已写明：实测两站热池的
   `sourceKey` 交集为 **0**（沿用 2026-09 的读数，本轮未重测）。这条路对我们这两站已被量过。
5. **不给 Gelbooru 装假开关**（§1.6）。
6. **不扩充随包词典**（§1.7）。

## 五、这条修的是哪一格，没修的是哪一格

修的是：**用户在 yande.re 手输一个别名画师名**（`setmen` → 0 张）—— 低频、高确定性、两笔请求。

没修的是 §1.2 那 **16~20% 的跨站分歧**，以及提案 §十四 想要的「全部 / 各站」聚合展示。
后者需要的是**跨站聚合搜索**（同一串词并发打两站 + 分站失败态 + 分站张数），
骨架现成（`GalleryFeedSource.kt:62-78` 并发两站、`GalleryMerge.kt` 去重合并，日榜与推荐已在用），
而 88% 的头部画师本来就同名，不需要任何映射就能立住。
剩余分歧里 yande 独有（`harahera` / `ruzhai`）本来就该空着 —— 那是**真没有**，
硬做映射只会把"没有"伪装成 bug。同名不同人的风险也已实测到：Gelbooru 上内容多的
`tinkle_bell`(343) 与 yande 的 `tinkle` 不是同一个作者，Danbooru 的 `tinkle`/`twinbox` 画师记录都是 `is_deleted=true`。
⇒ 提案 §一 那句"不要靠字符串相似合并"是对的，只是它给的例子不成立。

## 六、还没测到 / 依赖

1. 普通标签（非画师）在两站的重合率**没测**，可能明显低于 §1.2 的 88%。本文只对画师这一类下结论。
2. 25 个样本只覆盖**头部**画师；长尾分歧率大概率更高，但本方案只在 0 张路径上做事，不受这个数影响。
3. Danbooru 匿名限流只连打了 12 次（全 200，响应头里无 `x-ratelimit` 类字段），**不能据此说它没有限流**。
4. Gelbooru 有凭据时 DAPI 是否暴露画师别名 —— 没测（§1.6）。
5. **排期依赖**：本方案的接线点在搜索落地那一支（`GallerySearchViewModel.kt:770-775`）与空态区
   （`GalleryScreen.kt:686-730`）。**画廊首页重构若改动这两处，本文的行号与 3.4 的挂点要重新核过再开工。**

## 七、开工时的验收 case（对齐提案 §二十，改成按真实读数可判的形式）

| # | 场景 | 期望 |
|---|---|---|
| 1 | yande.re 手输 `setmen` | 重搜为 `tokenbox`，墙上有图，页尾/空态念出别名那一行；胶囊与历史仍是 `setmen` |
| 2 | yande.re 手输 `a.s.ヘルメス` | 解析为 `a.s._hermes`（10 张），同 case 1 |
| 3 | yande.re 手输 `harahera`（该站独有、正名也是它） | 不解析或有指针但不替换；**保持 0 张**，无第二条错误读数 |
| 4 | yande.re 手输 `a-10`（原词有 12 张） | **绝不重搜**，屏上仍是 a-10 那 12 张 |
| 5 | yande.re 输 `setmen 1girl`（两枚条件） | 不触发解析（§3.2） |
| 6 | 关掉代理 / 让 yande 熔断后输 `setmen` | 走 `searchError` 那一支，**不发解析请求** |
| 7 | Gelbooru 输 `tokenbox` | 保持 0 张，界面不得出现任何"已自动换名"的暗示 |
| 8 | 点卡片里的画师名进去搜 | 与今天逐像素一致（帖子上的是正名，本功能不该改变这条路径的行为） |

真机要人看的重点：case 1 的那一行文案、case 4 的结果**没被换名**、case 8 无回归。

## 八、追加（2026-09-30）：并入批次 K，触发判据从「整轮」改成「按腿」

用户指定这一条与「画廊首页 + 搜索浮层统一改造」（批次 K）**同轮做**，不单独排期了。
批次 K 给搜索加了「全部 / Yande.re / Gelbooru」来源轴（`GallerySearchSource`，两腿并发），
这直接改了 §3.1 那条判据的前提：

- **原文**：`results.isEmpty()` —— 整轮屏上空着。
- **改后**：**按腿判**。`全部` 档下 Gelbooru 出了货、yande.re 那一腿回 0 行时，屏上**不是空的**，
  按原判据别名解析永远不会触发 —— 而那恰好是这条功能唯一要救的形态（`setmen` 在 yande 是别名、0 张）。
  所以触发条件落在"**这一腿回 0 行**"上，重搜也只重跑那一腿，另一腿的结果原样留着。

连带三条：

1. `searchError == null` 这一条仍然必须成立，且要**按腿**看：那一腿自己失败/超时/未配账号，
   不算"它给了 0 行"，不许触发（否则给已熔断主机再压两笔请求）。
2. §3.4 那行读数要带站名，否则「全部」档下用户分不清是哪一站换的名：
   `yande.re 把 setmen 记作 tokenbox 的别名，已按 tokenbox 搜索`。
3. §3.3 的"每轮只解析一次"改成"每轮**每条腿**只解析一次"（键 = 站 + 原词）。

挂点行号（§三 与 §六.5 引的那些）在批次 K 落地后全部作废，开工前按新码重核一遍再动。

## 九、落地记录（2026-09-30，随批次 K 同轮做完）

状态：**已落地**。下面一律用符号名给挂点（行号在批次 K 之后还会再漂，不再当凭据）。

### 9.1 实际挂点

| 方案里说的东西 | 现在在哪 |
|---|---|
| 判据层（该不该解析 / 精确同名 / 换名 / 读数） | `gallery/domain/GalleryArtistAlias.kt`：`supports`、`singleArtistToken`、`exactRecord`、`canonicalName`、`canonicalNameFor`、`substitute`、`notice`、`resolveToken` |
| 站方那两笔取数 | `gallery/data/YandeReClient.kt`：`resolveArtistAlias` → `requestArtistRecords`（`/artist.json?name=`）+ `requestRedirectTitle`（`/artist/show/<alias_id>`，**只读落点 URL 的 `title`**，正文一个字节都不解析） |
| 触发点（每轮每条腿一次） | `GallerySearchViewModel.runSearch` 里那条腿的 `async`：`resolveToken` → `canonicalArtist` → `substitute` → 只重跑那一腿 |
| 那句话上屏的三处 | `GallerySearchViewModel.artistAliasNotice` → 展开卡（`GallerySearchArea` 的 `SearchNoticeLine`）、页尾（`GallerySearchEnd(aliasNotice = …)`）、空态（`GalleryScreen` 空态那串 `listOfNotNull`） |
| 弹栈要跟着回去 | `GallerySearchContext.aliasNotice`（`snapshot()` 存、`popContext()` 抄） |
| 缓存 | `YandeReClient.aliasCache`（`android.util.LruCache`，`ALIAS_CACHE_SIZE = 64`，口径照 `GalleryTagCategories`：**只有解析成功才入缓存**，没别名指针与失败两类都不存） |

### 9.2 比方案多出来的两道闸（都是被实况逼出来的）

1. **重搜那一笔没答上时不念那句话**。§3.4 只有两档（换到了有货 / 换到了还是没货），但重搜自己
   也会超时、也会被打回 —— 那一刻念「按 tokenbox 也没有图」就是把"我们没问到"念成"站里没有"。
   所以 `notice` 只在 `GalleryLegOutcome.answered` 为真时才产出，缺席由 `legFailures` 去说。
2. **落点标题等于原词时不重搜**（`canonicalName` 那一档）。站方指针偶尔指回自己，那一档重搜只是
   白跑一笔，还会产出一句「已按 X 搜索」而 X 恰是用户自己打的词 —— 那是假读数。

另外 §3.3 的"重跑那一腿"实现成**另起一笔 12s 预算**（`GalleryLegGuard.guard` 第二次调用）：
拿同一份预算去框两笔，等于让第二笔必然超时，而超时那条腿会被记成"这一轮没赶上"，
把"确实换了名"这件事一起吞掉。

### 9.3 复跑的实测（2026-09-30，本机代理，匿名，原始响应照录）

```
GET /artist.json?name=setmen    → [{"id":39892,"name":"setmen","alias_id":46523,"group_id":null,"urls":[]}]
GET /artist.json?name=tokenbox  → [{"id":46523,"name":"tokenbox","alias_id":null,"group_id":null,"urls":[… pixiv / twitter / fanbox 三条]}]
GET /artist/show/46523          → 302 + location: https://yande.re/wiki/show?title=tokenbox
GET /post.json?tags=setmen      → []
GET /post.json?tags=tokenbox    → 非空（第一条 id 1240881）
```

三条与 §1.4 那次取证的差别，都要记下来：

- 回复的字段表比当初记的**更窄**：只有 `id` / `name` / `alias_id` / `group_id` / `urls` 五个键。
  `YandeReArtistDto` 因此只接前三个。
- 正名那条的 `alias_id` 是**显式 null**（键在、值为 null），不是省略。DTO 里给它带默认值只为
  站方哪天改成整键省略时也不炸，**不代表实测见过省略形态**。
- `followRedirects` 全仓没有任何一处关掉（grep 过），所以落点 URL 就是那条 `location`；
  这条如果不成立，`queryParameter("title")` 恒为 null，功能会安静地变成"永远不解析"。

### 9.4 单测

`app/src/test/java/com/venera/compose/gallery/GalleryArtistAliasTest.kt` 共 8 条，逐条先红后绿：
明确的画师场景（只一枚包含型）｜精确同名（前缀匹配那几条不算）｜没有别名指针不替换｜
正名 == 原词不重搜｜只换那一枚其余原样｜Gelbooru 那条腿不参与｜读数分两档｜
`resolveToken` 的四档门（有货、没答上、续页、不支持站各给一个反例）。

取数那两层（`YandeReClient` 与 ViewModel 的接线）**没有单测**：本项目单元测试没有 Robolectric，
ViewModel 摸不到。那两层的真凭据只能来自 §9.3 那种原始响应复跑与真机读数。

### 9.5 真机验收 case（补批次 K 那张清单）

① 只搜 `setmen`、来源档「全部」：yande.re 那一腿应换成 `tokenbox` 出货，屏上念出那句换名，
而**胶囊与历史仍是 `setmen`**；② 只搜一个两站都没有的词（例如乱造的名字）：什么都不许多念，
屏上与开工前逐字一致；③ 搜 Gelbooru 独有的画师别名形态（本站结构上不可能触发，见 §1.6）：
屏上不许出现任何"已按 X 搜索"；④ 换名后点卡片进大图页再返回：那句话还在（`aliasNotice` 随快照回来）。
