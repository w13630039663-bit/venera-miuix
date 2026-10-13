# Gelbooru 的时间窗口排行：2026-10-13 带凭据重量 + 纯函数层 + 移动端接线

模块：画廊搜索墙（`gallery/domain` 那颗定界件与两端消费点）
形态：上午那一轮只落**纯函数层 + 判据**；同日第二批把**移动端接线**落完了（§六），
桌面那一半是被用户叫停的（「先改移动端的，桌面端的别动」），不是 §五 那条成因还挂着。

## 〇、先把 09-29 那笔账改对

那份方案文档（`gallery-ranking-windows-2026-09.md`）对 Gelbooru 的判死有两条，今天一条仍然成立、一条被推翻：

| 09-29 的账 | 今天的现量 | 判定 |
|---|---|---|
| 站方不认任何时间伪标签（HTML 路，九种拼法） | DAPI 带凭据：`date:2026-10-06..2026-10-12` ⇒ `count=0` | **仍然成立**（两路同判） |
| DAPI 要靠"探 id 边界"换算窗口，而月/年**收不了口**（24 次预算不够） | 估点 + 日期差修正 + 二分：**拿今天的真读数手工喂状态机**是 周 2 笔、月 4 笔（⚠️ 同日桩子实跑是 天 5 / 周 6 / 月 6 / 年 6，见 §六 —— 两个模型的两个数，别混引，结论一致：**都在 18 笔预算内**） | **推翻** —— 成因是那枚日速常数 |

收不了口的真正原因是常数：那一批按 **1.2 万条/天** 估点，而现量是 **6.5k–8.4k 条/天**
（独立两路：`id:>=14994000` 与最新之间 13 天跨 84,094 条 ⇒ 6,469/天；
月档 `id:>=` 的 `count=194,606` ÷ 30 天 ⇒ 6,487/天）。估点偏后近一倍，
退让步数与二分深度全被撑大，24 笔就是这么烧掉的。**结论：窗口档在这一站做得到，代价是个位数请求，且按日缓存。**

## 一、探针底账（`page=dapi&s=post&q=index&json=1`，判据全用信封里的 `count`）

| # | tags | count | 首条 |
|---|---|---|---|
| 1 | （空） | 14,409,081 | id 15078216 @ `Mon Oct 12 13:46:29 -0500 2026` |
| 2 | `sort:score:desc` | 14,409,081 | id 197798 @ 2008-04-25（全站高分，含 18 年前的老号 ✓） |
| 3 | `date:2026-10-06..2026-10-12 sort:score:desc` | **0** | — |
| 4 | `id:14994000.. sort:score:desc` | 14,409,081 | 与 #2 **逐字节相同** ⇒ `id:A..B` 这种区间写法**被静默忽略** |
| 5 | `id:>14994000` | **83,867** | 生效 |
| 6 | `id:>=14994000` | **83,868** | 生效，且比 #5 恰好多一条 ⇒ 两枚边界语义自洽 |
| 7 | `id:>=14994000 sort:id:asc` | 83,868 | **id=14994000 @ `Tue Sep 29 17:30:32 -0500 2026`** |
| 8 | `sort:id:asc` | 14,409,081 | id=1 @ `Mon Jul 16 00:19:58 -0500 2007`（站脚） |
| 9 | `sort:updated:desc` | 14,409,081 | 存在且换序（第三方客户端文档里见过这枚，今天验真） |

三条要点：

1. **`count` 随窗口缩** —— 这补上了 09-29 那份文档 §五.6 写着"未实测"的那一格。
   于是这一档的成败有了**不依赖肉眼**的判据：窗口没生效时 `count` 会等于基线，
   而"200 + 合法 JSON"在这种场合是完全无用的读数。
2. **定界原语是一笔到位那条**：`id:>=M sort:id:asc&limit=1` 回"窗口里最老的一条 + 它的日期"。
   比 09-29 那版用的 `id:<M&limit=1`（`stampBefore`）好两处：
   ① 不受删帖留下的 id 空洞影响；② 语义直接是"M 这个位置起算哪天"，不用再做一次方向换算。
3. **`id:A..B` 被静默忽略而 `id:>=B` 生效**：这两枚长得像，行为差一整面墙。
   谁照抄别的站的区间拼法，做出的就是"点了周榜、发出去是全站高分"那种假开关。

## 二、单调性（09-29 从未在目标站量过的那一格）

匿名 HTML 帖子页的日期，四点独立采样：

```
id 15,078,094 → 2026-10-12
id 14,994,000 → 2026-09-29
id 14,718,000 → 2026-08-17
id 14,358,000 → 2026-06-24
```

id 增、日期不减 ✓（站方是追加式自增 id）。当年 §0.3 那五个样本点是 yande.re 的，
而定界器只给 Gelbooru 用 —— 原语的单调性在目标站上从来没量过，今天补上。

## 三、本轮落地的东西

新增 `app/src/main/java/com/venera/compose/gallery/domain/GelbooruWindowBoundary.kt`（**纯函数，不出网**）：

- `GelbooruWindowBoundary.IDS_PER_DAY = 7_000`（只用于估点，不参与判读）、
  `CONVERGENCE_GRANULARITY = IDS_PER_DAY/8 ≈ 875 条 ≈ 站方三来小时`、`MAX_PROBES = 18`；
- `estimateStartId(maxId, targetDay, todayUtc)`：最新 id 往回推「天数 + 1 天余量」，夹在 `[1, maxId]`；
- `gelbooruBoundaryStep(maxId, targetDay, todayUtc, readings) -> BoundaryStep`：
  一台**吃读数、吐下一步**的状态机 —— `Ask(id)` / `Resolved(startId)` / `GiveUp(reason)`。
  策略是「估点 → 按日期差修正括住 → 二分收口」。判式的方向是本颗的命门，写在测试里：
  **`f(M) ≥ 目标日` 才是上界**（`f` = "比 M 老不了多少的第一条"的日期），反了就会交出一个
  起点落在目标日之后的 id，屏上表现是"周榜少了开头一两天"，而每一条读数都看不出毛病；
- `parseGelbooruStampDay(raw)`：站方 `created_at` 是 Ruby asctime（`Mon Oct 12 13:46:29 -0500 2026`，
  **年在最后**、带偏移）。认不出的串返回 null ⇒ 调用方按"这一笔没读到"处理，
  绝不拿猜出来的日期去定边界（那会把窗口挪到错误的一天而屏上无声）。

判据 `app/src/test/java/com/venera/compose/gallery/domain/GelbooruWindowBoundaryTest.kt` **13 笔**，
fixture 全部是上面那批真实读数（含 `id=14994000 @ 2026-09-29` 与站脚 `id=1 @ 2007-07-16`）：
解析三种形态 + 认不出给 null、估点夹界、上/下界判式两个方向各一笔、第一笔问估点、
用今天四笔真实读数复现"已括住 ⇒ 问中点"、收口 ⇒ `Resolved`、
未来窗口 ⇒ `GiveUp`、探测上限 ⇒ `GiveUp`、拿不到最新 id ⇒ `GiveUp`。

全量读数：`:app:testDebugUnitTest` **965 笔 / 0 失败**（上一格 952，+13 全来自这一批）、
`:desktop:compileKotlin` 绿（这颗件在桌面共享 srcDir 的编译面里，一起过了）。

## 四、下一批要做完的接线（形状已经定死）

> 〔同日第二批改判〕这四条里 1/2/4 的移动端那半已按 §六 落地；**第 3 条的形状换了** ——
> 没翻 `supports()`，而是新立一把 `supportsForApp()`（桌面继续读旧那把），成因见 §六 那张表。

1. `GelbooruClient` 加两枚取数：`latestId()`（#1 那形状）与 `stampAtOrAbove(id)`（#7 那形状，
   要回 `created_at`）；`count` 一并取回来当**自检**：`Resolved` 出来的起点，
   `id:>=startId` 的 `count` 应≈天数×日速，差太远就是定界错了，宁可不发。
2. 一台按「(档, 站方今天)」缓存边界的外壳（同一天同档天然同键，失败**不**缓存）。
3. `GalleryRankings.supports(GELBOORU, DAY/WEEK/MONTH/YEAR)` 从 false 翻成 true，
   `suffix(GELBOORU, 窗口档, …)` 交 `id:>=startId sort:score:desc`；
   ⚠️ `GiveUp` 时**这一档不发**，落回默认档并把原因说在屏上 —— 不许原样交回全站那批。
4. 两端菜单（Android `GallerySearchArea` 的过滤、桌面 `DesktopRankingPolicy`）与
   两端判据（`app/…/GalleryRankingTest`、`desktop/…/DesktopRankingTest` / `DesktopRankingPeriodTest` /
   `DesktopStaleClaimsTest` 里"这一站四档判死"那几行）都要同步改口径。

## 五、为什么接线不放在这一批

`gallery/domain` 是**两端共享**的编译面（`desktop/build.gradle.kts` 的 srcDir 挂着它），
翻 `supports()` 必然同时翻桌面的菜单与桌面的判据。而今天 `:desktop:compileTestKotlin` 是红的，
红在另一条线**未跟踪**的 WIP 上（`desktop/src/test/…/DesktopComic*Test.kt`、`DesktopAccentFillTest.kt` 等，
报的是 `companion` 与 `Unresolved reference 'desktop'`）—— 整片测试源集编译不过，
桌面那三面判据改了也**量不到**。

按本仓"降级路径不许静默交错、判据改完要能现跑"的口径，
把跨端翻转压到桌面判据面能复跑之后，比现在赌一把交付更便宜。

⚠️ **本节那两条成因，同日第二批走的是第三条路**：跨端翻转**没有发生** —— 移动端另立一把尺子
（`supportsForApp`）与一颗新契约（`GelbooruWindowProbeSource`），桌面自己那半棵树一个字没改；
共享编译面那两颗（`GalleryBusinessApi.kt` / `GalleryPorts.kt`）改的是**源兼容形状** —— 见 §六 最后一条数字段。见 §六。

## 六、同日第二批：移动端接线落地（桌面被叫停，不是漏）

用户 2026-10-13 的口径是「**先改移动端的，桌面端的别动**」，所以 §四 那四步里有两步换了形状：

| §四 那一步 | 实际落地的形态 |
| --- | --- |
| 1. client 两枚取数 | `GelbooruClient.fetchLatestId()` 与 `internal probeWindowStart(atLeastId)`（后者回 `GelbooruWindowProbe(count, oldestId, createdAtRaw)`，这枚 DTO 从 client 的嵌套 `internal class` **提到 `GalleryBusinessApi.kt` 的公开 data class** —— 契约的返回类型不能是 internal） |
| 2. 按天缓存边界的外壳 | `GelbooruWindowFinder(latestId, probeAtLeast)`：成功按目标日缓存、失败不进缓存（凭据掉了是会好的事） |
| 3. 翻 `supports()` | **没翻**。改成两把尺子不合流：`supports()` 仍答「站方自己给不给」（桌面读它，Gelbooru 四档仍 false），新增 `supportsForApp()` 答「我们做不做得出」。拼串同理：`suffix()`/`searchQuery()` 逐字不动，新增 `searchQueryFor()` |
| 4. 两端菜单与判据 | 只动移动端：`GallerySearchArea` 的过滤改问 `supportsForApp`，`GallerySearchViewModel` 的 `switchSource`/`setRanking` 两道回落到同一把尺子；桌面菜单与那三面判据**一行没碰** |

**架构红线怎么咬的（这一条是本轮最该记住的）**：上午那版在 VM 里写
`GelbooruClient.getInstance(app)`，`BusinessApiBoundaryTest` 的 **A（UI import 实现类）/
B（UI 直连取单例）/ E（静态取用出处必须可数）三面同时红**，而它自己给出的处置就是那句
「要开口就加契约，别直连」。落法是：

- 新契约 `GelbooruWindowProbeSource`（两笔问句）；
- **不进 `ScoredBoard`** —— 那颗是 Gelbooru 与 Safebooru 共用的，往公共口加成员就逼另一站挂
  "永远失败"的假实现，这条否决的现成理由就在同一颗文件里 `YandeReBoard.fetchDailyPopular` 上；
- `GalleryPorts` 加**有默认值**的一位 `gelbooruWindows: GelbooruWindowProbeSource? = null`，
  Android 在 `GalleryPortsAndroid.createPorts` 里接 `AndroidGelbooruWindowProbe(context)`，
  桌面 `DesktopGalleryPorts` 少传一位即可（命名实参 ⇒ 源兼容，**桌面那颗文件字节不变**）；
- 判据面加一条**接线扫描**：`android/GalleryPortsAndroid.kt` 里必须出现
  `gelbooruWindows = AndroidGelbooruWindowProbe(` —— 默认值是 null，漏接的屏上表现正是
  「菜单列了四档、每一档点下去都发不出去」那一型，编译器管不到少传参数。

**桩子把两处真缺陷跑出来了**（都不是想出来的）：

1. **「按天」档差点永久发不出去**：目标日 = 今天 ⇒ 估点天然贴着最新 id ⇒ 往前一跳必然越界，
   旧写法在这里直接 `GiveUp`。改成**先问最新那一条**（它天然是一枚合法上界，问它必命中）。
2. **`count` 单调自检会把正常收口误杀**：旧写法比的是「这一笔 vs 上一笔」，而二分回退会问比
   上一笔**更小**的 id。改成**按位置比**（问得靠左的命中数不许更小、靠右的不许更大）。
   另加一条**原地打转守卫**：读数记的是"观测到的最老那条"而不是问出去的那枚，删帖留大空洞时
   相邻两问回同一条读数 ⇒ 当场认输（只花两笔，不烧光 18 笔预算）。

**探测笔数**（贴着今天锚点的稠密理想桩，`GelbooruWindowFinderTest` 里钉成等式而不是上界）：
**天 5 / 周 6 / 月 6 / 年 6 笔**。⚠️ §〇 与 §一 那句「周 2 笔 / 月 4 笔」是**拿今天的真读数
手工喂状态机**的步数，与桩子的步数不是同一个模型的两个数，别互相照抄；结论一致 —— 都在预算内，
月/年都收得到口。

**时间基准这条口径写进 KDoc 而不是含糊掉**：`parseGelbooruStampDay` 交回的是**站方自己日历上的
那一天**（今天两笔真读数都带 `-0500`；会不会随夏令时换偏移**没量过**），而窗口边界算的是 UTC 日切 ⇒
后果有界：窗口**起点最多晚五小时**，绝不会把标签窗口之外的条目摆进来。

**§四.1 里那枚"事后自检"没照原样落**：原文写的是「`Resolved` 出来的起点，`id:>=startId` 的
`count` 应 ≈ 天数 × 日速，差太远就是定界错了」。这枚判据**要用同一枚估点常数当尺子**，而它今天
现量的跨度是 6.5k–8.4k/天（±30%）—— 拿它判成败会把定对了的那一次读成错。落的是另一枚**不需要常数**
的自检：按位置比 `count` 的单调性（`id:>=` 被静默忽略时，命中总数会随问的 id 变大而**跟着变大**，
当场就反）。窗口对不对最终由"落到的那一天"判（`GelbooruWindowFinderTest` 里四条
`assertEquals(target, dayOf(start))`），而不是由一个估出来的数判。

**数字**：`:app:testDebugUnitTest` 全量 **990 笔 / 0 失败**（上一格 965，**+25** =
`GelbooruWindowFinderTest` 13 + `GelbooruWindowRankingTest` 11 + `GelbooruWindowBoundaryTest` 13→14）、
`:app:compileDebugKotlin` 绿、`:desktop:compileKotlin` 绿、`:desktop:compileTestKotlin` 仍红在
另一条线未跟踪的 WIP 上（本批没碰）。
「桌面没被改」这句**得有读数**：`git status` 分不出是谁改的，所以现量 mtime ——
`find desktop/src -name '*.kt' -newermt '-35 minutes'` **空**（本批这一窗），而
`-mmin -720` 有 **19 颗**（别人那批）。共享编译面的两处改动是**源兼容**的：
`GalleryPorts` 新加的是**带默认值**的最后一位（`DesktopGalleryPorts` 用命名实参，少传照编），
`GelbooruWindowProbeSource` 是新立的一颗、没人必须实现，而 `supports()` / `suffix()` /
`searchQuery()` 三句**逐字未变**（`GelbooruWindowRankingTest` 里 `nativeVerdictIsUnchangedForDesktop`
与 `otherSitesDelegateByteForByteToTheExistingBuilder` 两笔就是钉这一条的）。

**还欠的两格**：① 真机 —— 周/月档在设备上按下去要真出图（本机只有凭据那条量得到，
匿名面 401）；② 桌面那半 —— 等用户点头且桌面测试面能复跑，要动的清单是
`supports()` 翻转 + `DesktopRankingPolicy` 菜单 + `DesktopRankingTest` /
`DesktopRankingPeriodTest` / `DesktopStaleClaimsTest` 里"这一站四档判死"那几行。

## 七、同日第三批：屏上那句「没探成」要说得出断在哪一步

用户 2026-10-13 在平板上按「月排行」，屏上是这一句：

> Gelbooru 这一轮没答上
> Gelbooru：月排行：这一站的窗口要先把起止探出来，这一次没探成，所以这一腿这一轮不发

`GelbooruWindowFinder.startIdFor` 当时返回一个 `Long?`，**五种成因共用这一句**：
探测通道没接上、取最新号那一笔被拒、探针那一笔被拒、日期串读不出、命中总数不自洽、
原地打转、烧光上限 —— 全压成 null。用户与排查的人都分不出该去设置里补凭据还是在等站方缓过来。

### 取证读数（全程只读，没注入任何点击）

| 问的是哪一格 | 怎么量的 | 读数 |
| --- | --- | --- |
| 设备上凭据在不在 | `run-as … cat shared_prefs/venera_gelbooru_account.xml`，只念键名与**值长度**与 sha256 前缀 | `user_id` 7 位、`api_key` 64 位、`bb97ec0b` |
| 是不是对话里那枚 | 从会话记录里 `grep -oE 'api_key=[0-9a-f]{64}'` 程序化取用，只比哈希 | 前缀 `bb97ec0b` **逐位对上**（没抄错、没截断） |
| 站方认不认 | 按应用同形（UA `Venera/1.0 (Android)` + `Accept: application/json` + `tags=id:>=14989882 sort:id:asc&limit=1`）连打 10 笔 | **10 笔全 200** |
| `sort:id:asc` 到底生效没 | 同一笔读 `post[0]` | 首条 `id=14863000` / `Tue Sep 08 17:15:26 -0500 2026` ⇒ 生效 |
| `count` 在不在信封里 | `@attributes` | `count=216366`（不带 tags 时 14411678）⇒ 单调自检那把尺子有对象 |
| 机上失败的那一笔 | `logcat -d` | **13:45:50** 画师路径 401「Gelbooru 没接受这组凭据」 |
| 凭据什么时候落的盘 | `run-as … ls -l` 同一颗文件 | **mtime 13:48**，晚于那条 401 |

**结论（一半是读数一半是推断）**：站方侧、凭据侧、拼串侧都没问题；那一屏**最可能**是
"凭据还没落盘那一刻"拍的 —— 这条要用户点一次「重试」才算量到，现在不许当事实用。

### 规则

1. **降级路径要说得出原因**（本仓那条老规矩，这一格是它第三次咬人）：
   `startIdFor` 换成 `hunt(...) : GelbooruWindowHunt`，两枚取数 lambda 改吃 `Result<…>?`
   —— `null` 只表达"这颗契约没接上"，`failure` 把站方原话带到屏上。
   十枚 `GelbooruWindowBreak` 各占一步，UI 侧一句人话映射一枚。
2. **`startIdFor` 留着当 `hunt(...).startId` 的薄壳**：四档收口笔数那几条判据钉的是"落在哪一天、
   花几笔"，不必跟着结论换形；换形会把 13 笔测试全改成 `.startId` 的噪声 diff。
3. **结论跟着返回值走，不写共享状态**：多条腿并发，从协程里写一格"上一次为什么失败"会把别人的话盖掉
   （这颗文件里 `GalleryLegAttempt` 上那段早写死了同一条纪律）。
4. **认输的出口要钉到枚**：`BoundaryStep.GiveUp` 多带一枚 `at`，测试从 `is GiveUp` 改成
   `assertEquals(哪一枚, step.at)` —— 只钉"它认输了"抓不到"输错了地方"。

### 量具自己坏掉的那两笔（记下来，别再上当）

- 那两笔"直连也 401 / 走代理也 401"是**假的**：我生成的 curl 配置里凭据是**空的**
  （整行只有 96 字符 —— 长度是自审的第一道读数，先量长度再信读数）。
- `curl -K` 的配置项**会累积**：`write-out` / `output` / `proxy` 必须写在所属 `url` **之前**，
  否则四笔全被打上最后一个标签、四笔共用最后一个输出文件。
- `grep -v META-INF` 放在 `awk 'NR>3'` 之前会让两份读数都变成空表，而"空 vs 空"**判成一致** ——
  那次差点把"200 颗条目 CRC 相同"写成为真。数完要数条目数（200 = 200），不是只看 diff 没输出。
