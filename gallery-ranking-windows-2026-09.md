# 画廊搜索的时间窗口排行（天/周/月/年/全部）—— 方案

日期：2026-09-29　模块：图库（画廊）搜索墙　形态：同屏，不新增目的地
（v2：参考 `yueeng/moebooru` 客户端的机制重做，**定界器不再用于 yande.re**，见 §0.5）

## 〇、实测底账（五批探针，本机走 `-x http://127.0.0.1:7890`）

**这一节决定五档能不能摆。** 站方给不给，是这条链上唯一的真话来源。

### 0.1 「排行榜」端点不吃标签 —— 拿它做"标签内周榜"一定是假开关

```
post/popular_recent.json?period=1w                     → 40 条，首条 id 1269553
post/popular_recent.json?period=1w&tags=touhou         → 同样 40 条、同样首条 1269553
post/popular_recent.json?period=1w&tags=nonexistent_tag_zzz → 还是那 40 条（40 条里 0 条带 touhou）
post/popular_by_day.json?year=2026&month=9&day=21      → 36 条
post/popular_by_day.json?...&tags=touhou               → 同样 36 条，一字不差
```

`tags=` 被**静默忽略**（`popular_recent` 与 `popular_by_*` 都是）。另外两个阴性对照：
`period=all` 与 `period=BOGUS` 都回落到 day 那一批（**站方没有"全部"这一档**），
`post.json?...order=rank` 与不带排序返回完全相同的一批（也被忽略）。
→ **这条路上没有"标签内 × 时间窗"的原生端点**，别照着站上"Popular"页去接。

### 0.2 `id:` 区间 + 分数排序两站都生效（Gelbooru 唯一可行的路子）

> ⚠️ **这一节的 Gelbooru 半边在 2026-09-29 第二轮被真机推翻了一半**：下面那些 `id:>` / `id:<` 的读数
> 全部来自**站内 HTML 搜索页**（`page=post&s=list`），而 App 走的是 **DAPI**（`page=dapi&s=post`）。
> 两者不必然同一套实现，而我当时把旁证当成了 DAPI 的结论写进了"唯一可行的路子"。
> 判决与撤档见 §十一。

| 站 | 实测 |
|---|---|
| yande.re | `tags=touhou order:score` → 分 940/898/876；`... order:score id:>1260000` → 分 126/125/96 且 id 全 >1260000 ✓ 组合生效 |
| Gelbooru（**HTML 路**） | `sort:score:desc` 生效（首条 6272407 老高分）；`sort:score:desc + id:>14900000` → 首条 14940140、最小 14908106 ✓ 窗口内高分；`id:<10000000` 铁证区间生效（首条 9999906） |

**不认的那一半**（别再猜）：Gelbooru **不认任何时间伪标签**，七种拼法逐个验过（每条都有阳性对照，
判据是页面长度：认的写法回 ~10 万字节、不认的恒回 ~21.8 KB 的"没有帖子"页）：
`date:` / `created:` / `time:` / `between:` / `newer_than:` / `date_range:` / `age:` / `posted:` / `created_at:`
**全部 0 结果**，而同批 `sort:score:desc`（130 KB）与 `id:>`（105 KB）正常返回。
它也不认 `order:score`（0 结果）。yande.re 不认 `age:<1d`、`ctime:<1m`（空数组），
`order:id_asc` 被**静默忽略**（与不带的返回同一条）。
未知伪标签在这两站是**匹配不到 → 空结果**，不是被忽略，所以"空"不能读成"窗口内没有图"（§四.4）。

### 0.3 定界原语可用且单调（只有 Gelbooru 用得上，留作第 2 阶段）

> ⚠️ 同样要更正：下面这五个样本点是 **yande.re 的 `post.json`**（id 量级 1.27e6 也印证了），
> 而这套定界器最后只给 Gelbooru 用 —— 也就是**原语的单调性在目标站上从来没量过**。
> 写的时候没注意，是第二轮复盘才看出来的。

```
post.json?limit=1&tags=id:<1269670 → id=1269669  2026-09-28   ← 最新
post.json?limit=1&tags=id:<1267500 → id=1267499  2026-08-19
post.json?limit=1&tags=id:<1265000 → id=1264999  2026-07-02
post.json?limit=1&tags=id:<1262000 → id=1261999  2026-05-17
post.json?limit=1&tags=id:<1240000 → id=1239999  2025-08-04
```

帖子被删会留 id 空洞，所以取 `id:<M`（"比 M 新的那一条"）而不是 `id:=M`；日期随 M 单调不增 ✓。
**id 增速实测约 54 条/天**（2170 条跨 40 天）→ 各窗口搜索域：天≈54、周≈380、月≈1600、年≈20000 个 id，
估点 + 局部二分 = **每档约 4~7 次 `limit=1` 轻请求**。

### 0.4 两处我自己的探针设计错误（记下来防重犯）

1. 第一次测 Gelbooru 用 `id:>40000000`，而那站 id 现在才 1.5 千万级 → "0 结果"是我造的。换 `id:<10000000` 才拿到铁证。
2. 第一次测 yande.re 下界用 `tags=id:>X&limit=1`，默认序新→旧，返回的永远是全局最新那条，与 X 无关。换 `id:<M` 才有效。
3. （v2 新增）**第一批我只试了 `age:` / `ctime:` 就下了"站方无时间窗口能力"的结论**，
   于是设计出一台不必要的定界器。是用户给的参考实现（§0.5）指出正确拼法 `date:A..B` 才翻案。
   教训：**"站方不支持"必须先读它的查询解析器或找一个成熟客户端对照，不能拿自己试过的两三个词当边界**
   （正是记忆里「别凭推断断言做不到」那条的复发）。

### 0.5 参考实现：`yueeng/moebooru`（Android 客户端，flavor 里就有 yande / konachan）

它的排行菜单正好是这五档（`res/menu/popular.xml`：day/week/month/year/all），而实现方式是
**把窗口拼成查询串里的伪标签**，不是走 popular 端点（`Model.kt:670-679`）：

```kotlin
fun popular_by_day(date)   = order(Order.score).date(date)                                  // date:2026-05-20
fun popular_by_week(date)  = order(Order.score).date(date.firstDayOfWeek(), date.lastDayOfWeek())  // date:A..B
fun popular_by_month(date) = order(Order.score).date(date.firstDayOfMonth(), date.lastDayOfMonth())
fun popular_by_year(date)  = order(Order.score).date(date.firstDayOfYear(), date.lastDayOfYear())
```

`Value.Op` 的区间写法（`Model.kt:546-551`）：`eq %s` / `bt %s..%s` / `ge %s..` / `le ..%s` / `gt >%s` / `lt <%s`。
它的 UI 是**按历史周期翻 ViewPager**（第 0 页 = 本期，往后 = 上一期，还能用日期选择器跳到任意一期）——
这条 UX 本轮不做（§六），但它证明了机制：**yande.re / konachan 这一系引擎认 `date:` 伪标签**。

**实测复核（关键翻案证据）**：

```
touhou date:2026-05-01..2026-05-31 order:score → 8 条，日期全在 5 月，分 126/125/96/77/76/75/65/61
touhou date:2026-08-01..           order:score → 8 条，全 ≥ 08-01（分 81/66/47…）
touhou date:..2026-02-28           order:score → 8 条，全 ≤ 02-28（分 940/898/876 = 历年高分那批）
touhou date:2026-05-20             order:score → 2 条，都是 05-20
touhou date:2026-09-21..2026-09-27 order:score → 0 条（这一周 touhou 确实没高分新增，见 §四.4 文案）
```

→ **yande.re 这一站的五档不需要定界器、不需要缓存、不多发一个请求**：窗口是本地日历算出来的两个日期。

## 一、档位与两站能力

必须有一个「默认（最新）」档，否则用户按了"周排行"没有回头路 —— 所以控件是 **6 档**。

| 档 | yande.re（`date:`，实测） | Gelbooru（~~靠 id 定界~~ → **第二轮撤掉**，见 §十一） |
|---|---|---|
| 默认（最新） | 不加后缀 | 不加后缀 |
| 按天排行 | `date:<今天>..<今天> order:score` | ~~`id:>=B sort:score:desc`~~ → **本站没有** |
| 按周排行 | `date:<周一>..<今天> order:score` | 同 → **本站没有** |
| 按月排行 | `date:<1号>..<今天> order:score` | 同 → **本站没有** |
| 按年排行 | `date:<1月1日>..<今天> order:score` | 同 → **本站没有** |
| 全部排行 | `order:score`（无需窗口） | `sort:score:desc`（无需边界）✓ 真机验过 |

日切口径：**按 UTC 算**（与站方 `created_at` 同一时区），下拉里每一项把**实际日期区间念出来**
（如「按周排行 2026-09-21 ~ 2026-09-28」），这样"本周"永远不是一个说不清的词。

## 二、Gelbooru 的定界器（本轮就做，不留到第二轮 —— 用户 2026-09-29 拍板）

> 这一节原来写的是"本轮置灰"。用户要求把定界器一起做进来，于是 Gelbooru 那四个窗口档
> 从"灰色说明"变成**真档**；代价与失败口径全在这节里。

落地位置 `gallery/data/GelbooruWindowBoundary.kt`，算法在 `gallery/domain/GalleryIdBoundary.kt`（纯函数、8 条单测）。

- **原语**：`GelbooruClient.stampBefore(belowId)` = 取 id 严格小于 `belowId` 的最新一条（`limit=1`，只要 id 与创建时刻）。
  用 `id:<M` 而不用 `id:=M`：被删的帖会留 id 空洞，精确取会探到空。
  站方 `created_at` 是 Ruby asctime 串（`Sat Sep 26 03:51:09 -0500 2026`），**带时区偏移**，
  解析必须吃掉整串（`ParsePosition` 验长度）—— 差半天就够把边界挪到错误的那一天。
- **算法**：指数扩界找"已经在窗口外"的那一侧，再二分收口。只依赖日期随 id 单调这一条实测事实，
  **不依赖任何增长速率假设**（速率只能拿来估点，估偏了会给出一个看起来正确的错边界）。
- **预算**：最多 24 次轻探测（每次约 1 KB）。按年这种最宽的档够用；探不完就交
  `GalleryBoundary.Unavailable`，**绝不硬给一个边界**。
- **缓存**：键就是 `windowStartMillis` 本身（UTC 日切的刻），所以同一天同档天然同键 ——
  **不需要 TTL，也不会跨天还拿着昨天的边界**。失败不写缓存（否则凭据修好了这一天也翻不了身）。
- **失败口径**：定不出来时**不发那一笔**，屏上是解释空态（「这一档没开起来」+ 原因），
  而不是拿全站历年高分冒充"本周"。这是本轮唯一一条"宁可错慢"的分支，也是它存在的理由。

⚠️ **一个仍未证实的前提**：DAPI 认不认 `id:` 我本机验不了（匿名一律 401，§0.2 那是站内 HTML 的旁证）。
真机第一次用 Gelbooru 的时间档就是一次判决：认则出图，不认则探针失败 → 走上面那句解释空态，
**不会**长出错结果。这一条留在 §八 的待验清单里。

## 三、UI：chips 行末一枚药丸 + 下拉（不新增一行）

- 控件：`VeneraChip`（`VeneraChipVariant.Filter` 现成变体，探索页「快捷筛选」同一件），文字 = 当前档
  （`排行：周`；默认档显示弱态 `排行：—`）。挂在 `GallerySearchArea` 的 chips 行**末尾**：
  展开态是 `FlowRow`（可换行），收成一条态是单行横滚 —— **两个态都常驻可见，一行高度都不多占**。
- 点击弹 `DropdownMenu`（现成先例：`FavoritesSortMenu`，收藏页顶栏那枚排序钮同式）：六行、
  当前档主色 + 尾 `Check`，行副标给实际日期区间；Gelbooru 那四档灰色 + 原因文案。
- 玻璃三条硬约束原样适用：**不涂不透明底板**（会拉出横贯硬边）、避让只用**常量**、
  不新开 `bottomContent` 那一格（它独占，搜索区与分段器已互斥占着）。
- 尺寸全走现成 token，不造新数字：`filterChipHeight`(32dp)、`chipIconSize`(16dp)、`space2/3/8`。
- 选中即重查（既定偏好「筛选即时生效」），并**打断上一笔**：现有 `runSearch(1)` 已 `searchJob?.cancel()`。
- 顶栏 🔍/✕ 与「换一批」的可见性判据不动；`Navigation.kt` 保护域不动。
- 不做参考实现那套"按历史周期翻 ViewPager"（§六）。

## 四、判据层与状态

1. 新增 `gallery/domain/GalleryRanking.kt`（**纯函数，全部可单测**）：
   - `enum class GalleryRanking { NEWEST, DAY, WEEK, MONTH, YEAR, ALL }` + `label` + `windowDays: Int?`。
   - `fun rankingSuffix(site, ranking, todayUtc: LocalDate): String?` ——
     yande.re 出 `date:2026-09-21..2026-09-28 order:score`；Gelbooru 只在 `ALL`/`NEWEST` 出串（`sort:score:desc` / null），
     其余档返回 `null` 并让 UI 走"置灰"支路（**判据在 domain，文案在 UI**）。
   - `fun supports(site, ranking): Boolean`、`fun windowRange(ranking, todayUtc): Pair<LocalDate, LocalDate>?`（下拉副标用）。
   - `fun searchQuery(site, filters, ranking, todayUtc)` = `queryOf(filters)` + 后缀。
   - 注入 `todayUtc` 而不是内部读时钟 —— 不然"周一的窗口起点"这类用例没法测。
2. **`queryOf` 本身不改**：它仍是"用户条件本体"（页尾读数、胶囊文字、复制到网页版、历史编码都读它）。
   新口径：**发出去的是 `searchQuery`，摆出来的是 `queryOf`**，两处各一条测试锁住。
3. 状态归属：`ranking` 住 `GallerySearchViewModel`（与 `site`/`filters` 同层），
   并**进上下文栈快照**（`GallerySearchContext` 加一个字段）——
   弹栈要回到"当时那一档"，否则「搜 touhou → 选周排行 → 点标签 → 返回」会悄悄变回默认档。
   这是本轮唯一动到上一轮那个域文件的地方。
4. **空窗口是合法结果，不是失败**：yande.re 对不认识的伪标签也给空数组，
   所以空态必须念出窗口："这一周（2026-09-21 ~ 2026-09-28）没有新增命中，试试更宽的窗口"，
   而不是笼统的「没有这个标签的图」。
5. **历史不带档**：历史记"搜过什么"，档位是"怎么看"。点历史回默认档，而当前档在药丸上常驻可见 → 不构成看不见的状态。
6. 不摆"本期共 N 张"：yande.re 无总数端点；Gelbooru 信封里的 `@attributes.count` 是否随窗口缩**未实测** → 不用它说话。

## 五、明确不做

- 不做全站排行榜那一屏、不改 `Navigation.kt` / 顶栏 chrome 行数 / 避让公式。
- 不用 §0.1 那批被静默忽略的写法（`popular_*?tags=`、`order=rank`、`order:id_asc`），
  也不用实测不认的那九种时间拼法与 `order:score`(Gelbooru 侧)。
- 不做"按历史周期翻档"（参考实现那套：它把每一期做成 ViewPager 一页、可跳到任意一天）。
  本轮只要"当前这一期"，翻期是另一条需求。
- 不做档位持久化到 prefs、不做左右滑动切档。
- 不重构 `GalleryScreen` 那个多支 `when`。

## 六、落点文件

| 文件 | 内容 |
|---|---|
| `gallery/domain/GalleryRanking.kt` | 新增。六档枚举（`label` 给菜单整行、`shortLabel` 给胶囊）+ `windowStart` + `windowLabel` + `windowStartMillis` + `needsBoundary` + `suffix` + `queryOfVisible` + `searchQuery`。**纯函数，`todayUtc` 由调用方注入**（判据层不读时钟，否则周一/周日边界那两条用例没法问倒它） |
| `gallery/domain/GalleryIdBoundary.kt` | 新增。`GalleryPostStamp` + `GalleryBoundary`（`Known` / `Unavailable`）+ `resolveWindowBoundary`：指数扩界找"已在窗口外"的一侧，再二分收口。探测函数注入，`maxProbes` 兜底 → **纯函数** |
| `gallery/data/GelbooruWindowBoundary.kt` | 新增。定界器外壳：`stampBefore` 探针 + 按 `windowStartMillis` 缓存（同一天同档天然同键，不需要 TTL；失败**不**缓存）+ 探针异常整台作废 |
| `gallery/data/GelbooruClient.kt` | 加 `stampBefore(belowId)`（`id:<M&limit=1`）与 DTO 的 `created_at` 字段 + `parseGelbooruCreatedAt`（三种形态、要求**整串吃完**才算解析成功） |
| `gallery/ui/GallerySearchViewModel.kt` | `ranking` / `isResolvingBoundary` 两个状态 + `setRanking()`（原地重搜、轮次 +1、不压栈）+ `openContext` 把新条件那一轮写回默认档 + `popContext` 把档随快照带回 + `runSearch`"要先定界就先定界、定不到就不发" + `rankingReading()` / `rankingWindowActive()` 两句读数；**历史仍存可见那一串**（`visibleQuery`），不掺伪标签 |
| `gallery/domain/GallerySearchContext.kt` | 快照加 `ranking` 一字段（默认 `NEWEST`，旧构造点不用改） |
| `gallery/ui/GallerySearchArea.kt` | chips 行末 `RankingChip`（`VeneraChip` Filter 变体 + 六行 `DropdownMenu`，副标带日期区间）+ 展开区那行"正在定位时间起点…" + `GallerySearchEnd` 多一个 `ranking` 读数 |
| `gallery/ui/GalleryScreen.kt` | 空态标题多两档分流（「这一档没开起来」/「这一档里没有新增命中」），消息里念出排行与日期区间 |

yande.re 侧 Client **不用改**：`searchPosts` 已把整串 `tags` 原样百分号编码发出（`YandeReClient.kt:105`），
`date:A..B order:score` 走的就是那个入口。

## 七、测试（本项目没有 Robolectric，判据必须是纯函数）

`GalleryRankingTest`（10 条，先跑红再实现 —— 第一次跑是 15 条 `Unresolved reference`）：
1. `默认档在两站都不产生任何后缀`；
2. `全部排行两站各用自己的那串实测生效的写法`（Gelbooru 那侧锁死**不是** `order:score`）；
3. `yande re 周档拼出 date 周一到今天 加 order score`（周日/周一两种 todayUtc）；
4. `日 月 年档的起点各按 UTC 日切`；
5. `周档起点落在周内不同日都退到本周一`（含 `ALL`/`NEWEST` 无区间）；
6. `只有 Gelbooru 的时间窗口档需要定界`；
7. `Gelbooru 窗口档拿到边界才出串 拿不到就不出`（**这条就是防"假开关"返祖**）；
8. `胶囊串里永远不含排序与窗口伪标签`；
9. `发出去的串是胶囊串加后缀 空条件时不留前导空格`；
10. `窗口副标念得出实际日期区间`。

`GalleryIdBoundaryTest`（8 条，夹具按 §0.3 的实测速率 54 条/天）：
`窗口内最早那条就是边界`、`日档的边界就是那一天最早的一条`、`id 有空洞时给的是真实存在的那条`、
`最新一条都比窗口起点旧时给的边界会让查询自然为空`（并断言**只探 1 次**）、
`年档的探测次数在上限内`、`超出探测上限就明说不硬给边界`、
`探针一条都拿不到时是失败不是空窗口`、`二分不靠速率常数也能对上真实日期轴`。

`GallerySearchContextStackTest` 补 1 条：`弹栈把排行档一起带回去`。

写测试时自己踩到的两个坑（都记在这儿，防下一轮重复）：
- 夹具最初照字面返回 `below - 1`，于是种子那一次探（`below = 1e8`）凭空造出一条"未来的帖子"，
  二分拿着它一路判"在窗口内" → 六条全红。**站点上比最新更大的 id 不存在，夹具必须夹到 `newestId`。**
- 期望值最初写的是"周档 = 上周一 ~ 今天(09-28)"，而调用传的 `todayUtc` 是 09-27（周日）——
  是**我的期望写错**、行为是对的（终点就该是传入那天）。这种红要先判清是谁错再改。

## 八、真机待验（设备由你点，我只读截图/logcat）

1. 默认档搜 `touhou` → 选「按年排行」：那一墙全是 2026 年内的图，且**分数明显低于**「全部排行」那批
   （全站历年高分 940/898… 不该出现在年榜里）。
2. 选「按周排行」：若落空，看到的必须是「这一档里没有新增命中」+ 消息里的日期区间，
   **不能**是「这一串标签没有可摆的图」，也不能掉回全站结果。
3. 下拉副标/页尾那串的日期区间，与站方网页版手动搜同样一串的结果一致（可复制到网页版比对）。
4. 切档后再按系统返回：**不能**掉出搜索层（换档 = 原地重搜，不压上下文栈）。
5. 「按周排行」→ 点图 → 点一枚标签 → 返回：新一轮是**默认档**（新条件=新一轮），
   而再返回到上一轮时档仍是**周**（随快照带回）。
6. **Gelbooru 的时间档 = 一次判决**（本机测不了的那条前提）：配好账号后选「按周排行」，
   先读「正在定位这一档的时间起点…Gelbooru 不认时间查询，要先探几次」，
   然后出图（说明 DAPI 认 `id:`）或读到「这一档没开起来」（说明不认）。
   **两种都必须是有解释的**，不许出现"静默摆出全站高分冒充本周"。
7. 同一档连点两次 / 切走再切回：当天第二次不再发探测（日志里探针条数应为 0）。
8. 换站往返（yande.re 周榜 → Gelbooru → 回 yande.re）：档位与日期区间都跟着站点重算，
   yande.re 那侧不该出现任何探测请求。
9. 定界失败后立刻重试：不留缓存（凭据修好当天就能翻身）。

## 九、实施顺序（照着做，与 Qoder 计划文件 `remote-beacon-stag.md` 同内容）

1. **先写测试** `app/src/test/java/com/venera/compose/gallery/GalleryRankingTest.kt`（§七 那 9 条），
   跑 `:app:testDebugUnitTest` 看它红 —— 预期是 `Unresolved reference`（域文件还不存在），红了才算真跑红。
2. 新增 `gallery/domain/GalleryRanking.kt` 让测试转绿（枚举 + `windowRange` + `suffix` + `supports` + `searchQuery`，
   `todayUtc` **注入**、不在内部读时钟，否则周一/周日边界那两条用例没法测）。
3. `gallery/domain/GallerySearchContext.kt` 快照加 `ranking` 一字段；
   `GallerySearchContextStackTest` 里那两条"逐字段一致"的用例跟着补上这一项。
4. `gallery/ui/GallerySearchViewModel.kt`：`ranking` 字段 + `setRanking()`（换档 = 原地重搜，
   `contextRound++` + `runSearch(1)`，**不压上下文栈**）；发串处从 `queryOf(sent)` 换成
   `searchQuery(site, sent, ranking, todayUtc)`；`snapshot()`/`restore()` 带上 ranking。
5. `gallery/ui/GallerySearchArea.kt`：chips 行末一枚 `VeneraChip`（`Filter` 变体）+ `DropdownMenu`
   （照 `feature/FavoritesScreen.kt:1440-1483` 的 `FavoritesSortMenu` 那式：当前档主色 + 尾 `Check`、
   副标实际日期区间、`supports=false` 的行灰色 + 原因）。
6. 三条构建命令实跑并回报输出：`:app:compileDebugKotlin` / `:app:testDebugUnitTest`（基线 307 → 目标 ≥316）/ `:app:assembleDebug`。
   两站 Client **一行都不用改**（后缀走现有那条整串百分号编码的 `tags=` 入口）。
7. 回写本文档"落地记录"一节 + `FREEZE-STATEMENT.md` 追加本轮（写明保护域未动、
   Gelbooru 四档置灰是**刻意的诚实降级**不是漏做）。
8. 沉淀两条记忆：项目侧「画廊数据面天花板实测」补进本轮实测（`date:` 四形式生效、
   `popular_*?tags=` 静默忽略、`order=rank` 被忽略、Gelbooru 认 `id:` 不认 `date:`/`order:score`、id≈54 条/天）；
   用户侧「别凭推断断言做不到」补这次复发过程（只试了两个词就断言无能力，因而白设计了一台定界器）。
9. 你在真机上点 §八 那 7 条，我只读截图与 logcat。


## 十、落地记录（2026-09-29 本轮实施）

### 10.1 与原方案的两处偏离

1. **Gelbooru 不再置灰**（用户当场改判："把定界器也加上，别留到第二轮"）。
   §二 那一节已按实现改写。附带换来的两个设计决定：
   - 缓存键用 `windowStartMillis` 本身，而不是原方案写的 `(站点,档) + TTL 30 分钟` ——
     窗口起点是 UTC 日切的刻，同一天同档天然同键，**TTL 这一层是多余的**，
     而且带 TTL 的缓存会有"跨天还拿着昨天的边界"这种读不出来的状态。
   - 原方案写的"总预算 12s"没实现，改成**条数上限 24 次探测**。理由：秒数依赖网络与代理，
     条数是可测的（`年档的探测次数在上限内` 直接断言），失败口径也更干净。
2. **定界器的算法从"速率估点 + 二分"改成"指数扩界 + 二分"**（`GalleryIdBoundary.kt`）。
   速率常数仍然只是初始步长（1024 个 id），但它**不参与正确性判断** ——
   估偏了只会多探几次或提前认输，不会给出一个错边界。这是有意的：
   速率是站方发帖量这种会变的量，拿它当判据的一半，等于把正确性押在一个没人维护的常数上。

### 10.2 QA 实跑

- `:app:compileDebugKotlin` **BUILD SUCCESSFUL**（新增两个域文件 + 一个 data 文件 + 三处接线）。
- `:app:testDebugUnitTest` **326 项全过、0 失败 0 错误**（46 个套件；上一步基线 307 →
  本轮 +10 排行 +8 定界 +1 弹栈带档）。
  这 18 条是**先看红**的：`GalleryRankingTest` / `GalleryIdBoundaryTest` 第一次跑是编译期
  `Unresolved reference`（域文件还不存在），实现后转绿；中间有 8 条红是**我的期望与夹具错**
  （见 §七 末尾那两条），不是判据错 —— 改的是测试，代码一行没退回。
- `:app:assembleDebug` **BUILD SUCCESSFUL**，universal 103,669,063 B（与上一轮同一尺寸 ——
  本轮没加资产，只加了几 KB 代码）。

### 10.3 尚未证实的一条

**Gelbooru DAPI 认不认 `id:`** —— §0.2 的证据来自站内 HTML（同一套检索引擎的旁证），
但 DAPI 匿名一律 401，本机拿不到凭据，没法直说它成立。真机第一次点 Gelbooru 的时间档就是判决：
认则出图；不认则"这一档没开起来"那句解释 + 空态，**不会**长出错结果。
这就是"宁可错慢不可静默交错"这条在本轮的具体形状：**前提没验，功能可以上，但失败必须有出口。**

## 十一、真机第二轮读数：Gelbooru 撤档 + 我上一轮判读错的一处

设备：PJZ110，用户点页面，我读截图与 logcat。

### 11.1 读数

| 档 | Gelbooru 上实际看到的 |
|---|---|
| 按周排行 | **正常**（出的是本周那批，不是全站高分） |
| 全部排行 | 正常 |
| 按月排行 / 按年排行 | **不行** —— 定不出窗口起点，那一档压根没发车 |
| yande.re 六档 | 全正常（原生 `date:`，无探测） |

另外从用户那里读到一句错归因：每日推荐那一屏空着的时候，标题写着「这一屏被你的规则挡完了」。

### 11.2 我上一轮写错的一句判读（公开纠正）

撤档之前我在代码注释里写过"DAPI 那一路真机读数不认 `id:>=`（周档出的与全部档同一批）" ——
**那句是我替用户补的，不是他说的**。他的原话是"周和全部正常，月年不行"，
即 **DAPI 认 `id:>=`**，机制是通的。真实成因是我给定界器定的预算：

- Gelbooru 的 id 增速实测约 **1.2 万条/天**（昨天 `id:>14900000` 那批、今天最新 14,986,924）；
- 月档要跨 ~36 万个 id：指数拓界 6 次（1024→4096→…→4,194,304）+ 二分 log2(4.2e6) ≈ 22 次 = **28 次**；
- 年档跨 ~440 万个 id，还要再多 2 次；
- 而 `MAX_PROBES = 24` → 这两档**必然**收不了口 → 交 `Unavailable` → "这一档没开起来"。
- 周档只跨 ~3 千 id：拓界 2 次 + 二分 ~12 次 = 15 次 < 24 ✓ 所以它是好的。

也就是说：**那两档"不行"是我的常数定的，不是站方给的天花板。** 这一点在拍板时我没说清，
用户是在"以为整条机制死了"的前提下面选了撤档。

### 11.3 处置（用户 2026-09-29 拍板：就按撤掉算）

- Gelbooru 只留 **默认** 与 **全部排行** 两档；菜单里那天/周/月/年四行留着但 `enabled = false`，
  写「本站没有」。藏掉那四行 = 用户分不清"站方没有"和"我们没做"；摆成能点的 = 假开关。
- 判据一把：`GalleryRankings.supports(site, ranking)`。菜单按它画行，`setRanking` 与换站
  （`switchSite`）按它落回默认档 —— 三处共用，才不会出现"点得动、发出去是全站结果"。
- 定界器整体撤下：`domain/GalleryIdBoundary.kt`、`data/GelbooruWindowBoundary.kt`、
  `GelbooruClient.stampBefore` + `parseGelbooruCreatedAt` + DTO 的 `created_at` 字段、
  `GalleryIdBoundaryTest`（8 条）、`isResolvingBoundary` 与 `BOUNDARY_UNAVAILABLE` 两处接线。
  **镜像在 `_trash/gallery-gelbooru-boundary-2026-09-29/`**，恢复只需把 `MAX_PROBES` 提到 40
  （年档首次约 28~30 笔串行轻探测，移动网络上 8~15 秒，当天同档只探一次），或按运行时测得的
  id 增速估点后再局部二分（约 12 笔）。**这条决定要改的话，先读 §11.2。**
- 每日推荐那屏的归因改成按实际张数说：`blockedCount > 0` 才提屏蔽规则、
  `hiddenByRating > 0` 才提「成人内容处理」，**两个都是 0 就说"站方这一轮没有回内容"**。
  原来那一句在池子本来就空的时候会说"这一轮的图全被判为成人内容"——那是凭空编的成因。

## 十二、第三项：月/年档选**具体哪一期**（2026-09-29 追加需求）

用户：「另外能不能做下像 moebooru 能选择具体年份 月份的」。

### 12.1 数据面（本轮实测，全部走 `-x http://127.0.0.1:7890`，零额外请求）

| 查询 | 回来的 |
|---|---|
| `touhou date:2024-03-01..2024-03-31 order:score` | 首条 1158988@2024-03-14，分 144/144/119 ✓ 全在 3 月 |
| `touhou date:2019-01-01..2019-12-31 order:score` | 首条 561904@2019-08，分 876/790/753 ✓ |
| `touhou date:2025-07-01..2025-07-31 order:score` | 分 86/69/45 ✓ |
| `date:..2006-06-01` | **0 条** |
| `date:..2008-01-01` | 有货（最早那批 id 1.1 万级，created_at 2008-01） |

→ yande.re 对**任意历史区间**都给原生 `date:`，所以"翻到 2019 年"和"看本月"是同一笔请求的成本。
存档起点夹在 2006 年中与 2008 年初之间，弹层的年份范围取 **2007..今年**（保守：宁可少给一年）。

### 12.2 三条拍板

1. **入口 = 下拉里加一行「选具体哪一期…」**，点开是日期弹层。不新增 chrome 行（同 §三 那条理由）。
2. **只有月/年两档能选历史期**。天/周回看是"上一天/上一周"那种一步的事，为它开一层弹层不值 ——
   技术上四档同样能拼（`GalleryRankingTest` 里 `周档给历史锚点也拼得出完整那一周` 那条就是证据），
   收着不开的是**入口**，不是判据。
3. **Gelbooru 不参与**（§十一）。

### 12.3 落地形状

- 判据层：`windowBounds(ranking, anchor, todayUtc)` 取代"起点 + 今天当终点"那套算法 ——
  终点 = `min(锚点所在期的最后一天, 今天)`。本期口径与原来逐字一致（老用例一条没改就过）。
  锚点落在未来时**夹回今天**：弹层的年份范围虽然不给未来的年，判据层也不能产出
  `date:2026-12-01..2026-09-29` 这种起在止之后的串。
- 状态：`periodAnchor: LocalDate?`（null = 本期）。它**随上下文栈快照一起存**，
  否则"翻到 2024-03 → 点一枚标签 → 返回"之后屏上还是那批三年前的图而读数写着本期。
- 换档一律清回本期；再点**当前那一档** = 回到本期（撤销历史期那一步的最近路径）；
  弹层里另给一枚「回到本期」（只在已经选了历史期时出现）。
- 胶囊上标期次：`排行：月 2024-03`。不点开下拉也读得出看的是哪一期。
- 空态换一句：历史期不说"没有**新增**命中"（那是本期的说法），说"这一期里没有命中的图"。
- 一天都没选（`selectedDateMillis == null`）= 用户只是打开看了一眼，**一笔请求都不发**。

## 十三、真机第三轮读数与三处改动（2026-09-29 凌晨）

用户一次给了三条，其中第 1 条是本轮功能自己的回归。

### 13.1 「展开态点排行，菜单闪一下就没了」

读数：展开那一态点芯片 → 菜单出现 → 立刻消失；**收成一条那一态点它正常**。

成因不是 `DropdownMenu`，是**组合位置**：芯片自己 `remember { mutableStateOf(menuOpen) }`，
而它在展开态画在 `FlowRow` 里、收条态画在 `Row` 里 —— 两个位置就是两个组合实例。
点芯片时焦点离开输入框 → 键盘下落 → 那道"键盘收起就收成一条"的 effect 把整块收掉 →
芯片换支、旧实例销毁、`menuOpen` 归零 → 菜单当场关掉。收条态没有键盘，所以不触发。

修法：把 `rankingMenuOpen` / `periodPickerOpen` **提到两种形态之上**（`GallerySearchArea` 里），
芯片只收参数。收条照收，菜单只是改挂到收条那一颗芯片上。
同一条口径也管住了弹层：弹层也从提上来的 `periodPickerOpen` 驱动，不再记在芯片里。

### 13.2 详情页「关于这张图」改成两列

画师 / 角色 / 作品三组从"信息卡里的一行 + 下面的标签墙"改成**与尺寸卡并排的右列**
（位置：站点那一排之下、出处之上）。署名三组在右列**不带计数**（窄列里"角色 1"是噪声），
标签墙那面照旧带数。三组都没有时右列不占位，左列自己铺满整行。
顺带修掉一处：`InfoRow` 的标签列原来是**定宽 24dp**，卡收到半屏之后「上传者」三个字折成两行
（真机截图上就是这样）—— 改成只设下限。

### 13.3 主页面内容区的左右滑切页整体撤掉

`Modifier.tabSwipePager(...)` 的唯一调用点（`Navigation.kt` 的 NavHost）摘掉，
`TabSwipePager.kt` 与首页轮播那处 `tabSwipeExcluded()` 一并撤下
（镜像在 `_trash/tab-swipe-pager-2026-09-29/`）。**底栏自己那一条拖动切 tab 保留**
（`VeneraLiquidGlassNavBar` 的 `DampedDragAnimation`，与刚撤的那条本来就是两套实现）。
收藏页内三段 pager、画廊日榜/推荐两页、大图页左右翻、阅读器翻页**全部不动**。
`Navigation.kt` 是记录在案的保护域，这次是用户当场点名要撤 —— 豁免写进 `FREEZE-STATEMENT.md`。
