# 批 116′ + 批 117：pixiv 收藏/人气照 pixez，AI 角标接站方硬字段

2026-10-18 凌晨。触发：用户「搜索里人气和收藏 xxx+ 的筛选没有生效」「搜索历史经常会自动没了」
「开了 AI 角标没见到一个图片有角标」「人气/收藏照 pixez 的做法来，AI 按你的方案」。
**用户已声明 pixiv 账号是登录态** ⇒ "没登录"这一条解释当场排除。

---

## 一、本轮现量（每条都可复现，日期是 2026-10-18）

### 1. pixiv 的 `aiType` 语义定死了：**2 = AI 生成，1 = 非 AI**

| 请求（匿名 `www.pixiv.net/ajax/search/artworks/{词}`） | 条数 | `aiType` 分布 |
|---|---|---|
| `AI生成画像`（`s_mode=tag`） | 60 | **=2 有 59**、缺键 1 |
| `NovelAI` | 60 | **=2 有 58**、缺键 1 |
| `オリジナル`（对照，非 AI 词） | 60 | =1 有 37、=2 有 22，而这一页里**带 AI 味标签的 5 条全部 =2** |

复现：`curl --compressed -A '<桌面 Chrome UA>' -G "https://www.pixiv.net/ajax/search/artworks/<词>" --data-urlencode s_mode=tag --data-urlencode p=1`
⚠️ **必须带 `--compressed`**：不带时落盘的是截断 JSON（49,643 字节断在半个 token 上），`JSON.parse` 报 `Unterminated string`，
会被读成"站方坏了"。pixiv **直连就通**（与 `-x http://127.0.0.1:7897` 同一形状），不在"必须挂代理"那一类。

**顺带推翻一条既有假设**：pixiv 的原创/最新池里 AI 约占**三分之一**（22/60），
所以"首页池子里捞不到 AI"这句在 pixiv 上**不成立** ⇒ 用户一张角标都见不到，是判式与渲染面的锅，不是运气。

### 2. `{词} {N}users入り` 这条路**匿名就真生效**

| 请求 | 结果 |
|---|---|
| `猫` | 60 条里**只有 2 条**带 `*users入り` 标签 |
| `猫 100users入り` | 58 条里 **47 条带 `100users入り`** |

⇒ 收藏门槛**不需要登录、也不需要会员**，只要把它做成词尾标签（pixiv 的收藏数标签本来就是真标签）。

### 3. pixez 那一侧的档位表（GPL 档：**只出表与结论，一行码都不搬**）

来源 `Notsfsssf/pixez-flutter@master`：`lib/network/api_client.dart:438-464`、`lib/page/search/result_illust_list.dart`。

- `GET /v1/search/illust` 参数集：`filter`、`merge_plain_keyword_results=true`、`word`、`sort`、`search_target`、
  `start_date`/`end_date`、**`bookmark_num_min`/`bookmark_num_max`**、**`search_ai_type`**（0 = 含 AI、1 = 排除）。
- `sort` 五档：`date_desc`、`date_asc`、`popular_desc`、`popular_male_desc`、`popular_female_desc`。
  **后三档是会员档**：`popular_male/female` 只在 `isPremium == 1` 时才摆出来（`:556`）；
  点 `popular_desc` 时 `isPremium == 0` ⇒ **toast `not premium` 并 return，不发请求、不改已选排序**（`:689-693`）。
- 收藏门槛是**两套并行**：
  - `_buildStar()`（**恒摆，人人可用**）：选一档 `starNum = [0,100,250,500,1000,5000,7500,10000,20000,30000,50000,100000]`，
    效果是把搜索词改成 **`{原词} {N}users入り`**（`:343`），`bookmark_num` 传空。
  - `_buildPremiumStar()`（**只在 `isPremium == 1` 才摆**，`:201`）：选一组 `premiumStarNum = [[], [10000], [50000,99999], [10000,49999],
    [5000,9999], [1000,4999], [500,999], [300,499], [100,299], [50,99], [30,49], [10,29]]`，
    效果是发 **`bookmark_num_min`/`bookmark_num_max`**（真参数，不是编的）。

### 4. 由此定死的根因

**我们屏上那七枚「100+…10000+」发的是 `bookmark_num_min`，而那一枚是 pixiv 会员档。**
非会员点它 = 站方静默忽略 = "选了没变化"，**与票据在不在、登录不登录都无关**。
上一轮（01:43–01:48）把成因写成"票据那一刻没落地"，那只解释了三分之一（那条路是真的，但**不是本案的主因**）—— 就地改判。
`sort=popular_desc` 同理：非会员点了就该明说，不该发出去等站方拒绝。

---

## 二、批 116′ 改法（照 pixez）

1. **收藏两制并存**，按会员身份二选一：
   - 非会员 / 匿名腿 → 词尾 ` {N}users入り`（档值沿用 pixez 的 `starNum`，屏上仍是用户稿那七枚的形状）；
   - 会员 → 发 `bookmark_num_min`（**只做单边门槛**，区间不做：用户稿那七枚全是 `100+` 形状，区间要两枚数字，屏上摆不下也不常用）。
2. **人气**：非会员**不发**、不改已选条件，上屏一句「人气这一档要 pixiv 会员」（pixez 是 toast，我们走 notice 同屏）；
   会员才发 `sort=popular_desc`。`popular_male_desc`/`popular_female_desc` **不做**（用户稿只有三档）。
3. `is_premium` 落地：现在 `PixivAuthState.SignedIn` 只有 `userId`/`userName`，全仓零处解析会员格。
   从 `/v1/user/detail` 的 `is_premium` 取（`userId` 已有），存进 `SignedIn`，**取不到就当非会员**（宁可少给一档，不许假装筛过）。
4. 「收藏 xx+」从此**不要求登录**（词尾那条匿名就通）⇒ 面板不再拿登录态藏它，只藏"会员才有的那种筛法"。

## 三、批 117 改法（AI 角标）

1. `GalleryPost` 加一枚**站方 AI 位** `siteAiFlag: Boolean?`（`null` = 这一腿站方根本不给这一格）。
   - 匿名搜索腿：`aiType == 2` → `true`、`aiType == 1` → `false`、缺键 → `null`；
   - 匿名 `ranking.php` 那一腿：载荷**没有**这一格 → `null`；
   - 登录腿：`illust_ai_type == 2` → `true`（pixez 明写 ==2 是 AI 生成）；
   - booru 三站：`null`（结构性没有，只能靠标签）。
2. `GalleryAi.isAiMarked(post)` 改成 **硬字段优先、词表兜底**：`siteAiFlag == true || 词表命中`。
   那六枚 pixiv 补宽词从此退成"站方不给硬字段时"的兜底，不再是最硬的判据。
3. **角标补三处表面**：首页横滑行（`GalleryHomeSections.kt:398`）、画师行（`GalleryArtistRows.kt:466`）、大图页。
   **历史墙不补** —— 那一行盘上只存 `site/id/thumbUrl/at/ratio`（`GalleryHistoryStore.kt:84`），
   补角标就要改盘上形状，跨端漂移的风险大于收益；这一屏不带角标这件事在屏上与文档里都明说。
4. **自相消那一格**：「屏蔽 AI 生成的条目」开着时，唯一会带角标的那些条目被整条收起（`GalleryScreen.kt:2511`）⇒ 角标必然 0 枚。
   保留屏蔽计数行那枚 `AI_BLOCKED_RULE` 读数，并把两枚开关的关系写进文案（现在那句「两边用的词表是同一份」是**假话**：
   漫画侧收裸 `ai`，画廊侧剔 `ai` 又加六枚）。
5. 三把尺子：① 两腿的硬字段解码（`aiType` 三形 + `illust_ai_type`）；② 角标调用点**数出现次数**（存在性判式会被别处合法调用喂饱）；
   ③ `pref_gallery_ai_badge` 键名 / 默认值 / 可观察。yande.re 那种"站方没标签"的结构性 0 命中**不改判式**（放宽会把角色名 AI 误判进去）。

## 四、那两处「还等用户点的」—— 已定档（2026-10-18 凌晨，用户给的是两条自定义答案）

1. **搜索历史**：01:48 那版把"恢复"挂到进 ViewModel 就读盘（真根因是它从前只在搜索卡组合时跑一次）。
   还有一格会咬人：同一个词在 pixiv 与 booru 都搜过会被**并成一条**（`GallerySearch.kt:209-212`），
   而屏上按域过滤（`GallerySearchViewModel.kt:1575-1576`）⇒ booru 那一行**永久消失**。两档要选一档：
   - **分域各存一份键**：干净，但升级那一刻新键是空的 ⇒ 旧史整片不见（要么带一次性迁移，代价是读旧键）；
   - **同词多域各留一行**：盘上形状不动，`canonicalKey` 加域名，旧条目按它记录的 `sites` 摊开。
2. 要不要把 `search_ai_type=1` 做成一枚**真·排除 AI 作品**的筛选（站方硬过滤，比本地筛干净；只在登录腿有）。

**定档结果**：① 选了**分域各存一份键** —— 落地形状 `GallerySearch.historyKeyOf(domain)` → `history_booru` /
`history_pixiv`，两端同一个来源（桌面那颗 `DESKTOP_SEARCH_HISTORY_KEY` 直接吃 `BOORU` 那一枚）；旧那份混域档
在 ViewModel 起手时按 `belongsTo` 摊成两枚、摊完 `prefs.remove(legacyKey)`，不留两份。上面"01:48 那版"的
恢复时机改判照旧保留 —— 两件事都做了。
② 用户答「一起加」，两条腿各发一枚：匿名腿 `ai_type=1`、登录腿 `search_ai_type=1`；面板多一行「AI：全部 / 排除 AI」，
`activeCount` 与 `summary()` 都把它算进去。
⚠️ 顺手改判一处：清空最初跟着新键写成 `put(historyKey, 空表)`，那是**换了语义**（对岸与桌面都走 remove；写成空表
在盘上留下「v2」哨兵，读侧哪天改成"没这个键就带默认词"时会被读成"用户清过"）⇒ 已改回 `prefs.remove(historyKey(domain))`，
并由判据的负向那一支钉住不许退回。

## 五、收口（2026-10-18 03:46，四份 debug 包已出）

- **批 116′**：收藏门槛做成两轨（非会员 = 词尾星级标签，会员 = `bookmark_num_min`）+「人气」那一档点下去
  **不改条件**并念一句为什么 + `data/PixivPremium.kt` 一处缓存（`user.is_premium` 现量），搜索与收藏同步共用。
- **批 117**：`GalleryPost.siteAiFlag` 三腿都填（匿名搜索 / 标签作品 / 单张详情；榜单腿响应里没这一格，明写 null）
  + `GalleryAi.isAiMarked(硬位, 词表)` 硬位优先、裸 `ai` 仍不算 + 角标补到**首页推荐轨**与**看图器左上**
  （原先只有瀑布流卡与「关于这张」）+ 设置页三段文案按现量重写。
- **批 120**：本地先落、再打站方 `bookmark/add` / `bookmark/delete`；`restrict` 跟会员档（私密 / 公开）；
  三条交代各有句：没登录、站方那份没加上、已同步。非 pixiv 那条直接 return，不出声也不报错。
- **判据**：新增 `GalleryPixivAiBadgeAndSyncTest` 10 笔、`PixivSearchFilterTest` 扩到 26 笔（会员与非会员两轨分开钉）；
  全量 `:app:testDebugUnitTest` **1533 笔 / 190 颗类 / 0 失败 0 错误**、`:desktop:test` **1521 笔 / 173 颗类 / 0 失败 0 错误**。
- **顺带收掉的两把桌面旧账**（行 203 挂的那两枚）：`三句与android那侧同词` 按对岸新词同词化（原因原文留在
  stdout 那一行）；`档位表逐字回核对岸` 是**尺子把同一颗文件里的第二枚 enum 并进了这张表** —— 桌面方向档一枚不缺，
  真缺的是桌面没有「翻页特效」那一枚键，改挂账（不是这把尺子的对象）。
- **hero 那四把尺子按 01:32 的代码重钉**（并行那一路的改判一起入账）：让位类（大标题 / 折叠连接 / 玻璃进度）吃
  `heroActive`，落点类（墙顶留白 / 卡落点 / 跟着折叠收的那一截）只问"设没设过头图"；那句"搜索态那一屏没有头图"
  在 `GalleryScreen.kt` 的四处注释里一起撤掉。
- **没做的两格（都不静默）**：桌面那侧的收藏**不做站方同步**（桌面没有 pixiv 登录态那一层）；
  **存量本地收藏的批量补同步把手**没做 —— 今天只接"新收藏那一下"，旧的那几十张要等一枚带两步确认的把手，等点头。
