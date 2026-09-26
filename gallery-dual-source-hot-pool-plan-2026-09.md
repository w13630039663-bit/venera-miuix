# 画廊第二轮：双源热门池 + 带权随机 + 来源胶囊

日期：2026-09-25。前置：第一轮（yande.re 单源三级查看流）已落地未提交。
本文所有"站方能力"均为**本轮探针实测**，不是照另一端点推断的。探针跑在开发机上（HK 出口），
真机出口不同，落地后仍要按 §八 的清单在设备上复验。

---

## 〇、一句话结论

Danbooru 官方 JSON API 可以匿名接，**但应用现有默认 UA 会被 Cloudflare 直接拦死**（API 和 CDN 都 403），
这是本轮唯一"接了会整屏黑"的坑；两站的月度热门都能拿到，但形态差别很大；
**感知哈希经实测判定为不值得做**（下面给数据），去重用 md5 + 规范化出处链接就够。

---

## 一、实测：Danbooru 能力表（2026-09-25）

| 项 | 实测结果 |
|---|---|
| `GET /posts.json` 匿名 | **200**，返回数组 |
| User-Agent 敏感性 | `okhttp/4.12.0` ✅ 200 / `Venera/1.0 (Android)` ✅ 200 / 移动端 Chrome UA ❌ **403 + `cf-mitigated: challenge`** / 无 UA ❌ 403 |
| CDN `cdn.donmai.us` 三档 | 同样**只认非浏览器 UA**：Chrome UA 连 180×180 缩略图都是 403 挑战页（返回 5.9 KB HTML）；`okhttp` UA 下同 URL 200 / 3.7 KB `image/jpeg` |
| `limit` | 200 照数返回；**400 被截成 200**（匿名上限 200，与 yande.re 的 320 不同） |
| 翻页 | `page=1/2/80/200`（limit=20）全 200 且有数据，分数单调下降 → 池子够深 |
| **月度热门** | **走用户点名的 Explore 那份**（同日第二次复测后改，见 §十）：`/explore/posts/popular.json?date=<yyyy-MM-dd>&scale=month&page=&limit=` → 200 条/页、page 1/2/7 **相邻两页零重叠**、响应与 `/posts.json` **同一套字段**、分数 827~299、`fav_count` 274~793。<br>备选 `posts.json?tags=order:rank` 实测也是当月（`created_at` 全落 2026-09、分数 9~416），但它是排名副产物、没有"哪一个月"的显式语义 → **不用它** |
| 字段名 | 与 yande.re **几乎全不同名**（见 §三对照表） |
| 质量信号 | `score` / `up_score` / `down_score` / **`fav_count`** / `image_width` / `image_height` / `file_size` 全有 |
| 分级取值 | `e` / `q` / `s` / **`g`** ← **多一档 `g`（general）**，yande.re 没有。Explore 月榜前 200 条实测 **e=122 / q=53 / s=25**（这批里没有 g）；按"最新"取的 20 条里 `g` 占 8 → 放宽取法时 `g` 会大量出现 |
| 质量信号量纲 | Explore 月榜 200 条：`score` 299~827（中位 ~400）、`fav_count` 274~793、百万像素中位 3.96 |
| 非图片条目 | **Explore 月榜第 1 页 200 条里 `mp4` 40 条 = 20%**（`jpg` 118 / `png` 38 / `gif` 3 / `webp` 1 → 可摆的只有 160 张）。`order:rank` 那份是 8/200=4%。必须滤 |
| 档位兜底 | 同一份池子里 **52/200 条没有 `sample` 变体**（小图不解 sample 档）→ 二级那档要"sample 变体 → 顶层 `large_file_url`"两级兜底，尺寸同理 |
| 服务端滤类型 | `type:jpg` ❌ **返回 0 条**、`file_ext:jpg` ❌ 0 条；`-type:video` 单用 ✅，但**与 `order:rank` 组合时被忽略**（mp4 照样回来）→ 只能客户端滤 |
| 成人内容可取性 | 匿名 API + CDN **拿得到 e 档原图**：e 档某条 preview/720/sample/original 四档全 200，original 2.8 MB |
| 单条取法 | `tags=id:N` ✅ 正好 1 条（与 yande.re 同一套语法） |
| 并发 | 连续 6 发全部 200，`Retry-After` 头始终没有，未见限流形态 |

## 二、实测：yande.re 侧要更正的两条

| 项 | 实测结果 |
|---|---|
| `fav_count` | **字段根本不存在**（44 个键里没有）→ "综合收藏数"这一路权重在 yande.re 无输入，不能当 0 算 |
| `popular_by_month.json?year=&month=` | 200，**固定 40 条、忽略 limit、无 page** → 月榜只能当"一屏到底的一块"，不是可翻页的流；8 月榜 e=31/q=9，9 月榜 e=25/q=15。**所以一轮要"当月 + 上月"两份**（80 条），只取当月会在配比里被 Danbooru 的 200 条压没 |

## 三、两站字段对照（模型层要按这张表归一）

| 用途 | yande.re | Danbooru |
|---|---|---|
| 缩略档 | `preview_url`（+`actual_preview_*` 尺寸） | `preview_file_url`（只有 `media_asset.variants[]` 里带宽高） |
| 开门档（大图页第一帧） | 就是 `preview_url`（300×212 / 13.7 KB，与网格同址 → 缓存命中） | `media_asset.variants[type=="360x360"]`（242×360 / 28.8 KB，兜底 `preview_file_url`） |
| 中档（大图页） | **`sample_url`** + `sample_width/height`（≤1500 / 209 KB）。~~`jpeg_url`~~ —— 见 §十五 | `large_file_url` + **`media_asset.variants[type=="sample"]` 的 width/height** |
| 原图档 | `file_url` + `file_size` | `file_url` + `file_size` |
| 卡片比例 | `sample_width/sample_height`（fallback 时用 `jpeg_*`） | `image_width/image_height` |
| 标签 | `tags`（空格分隔、不带命名空间前缀） | `tag_string`（也是空格分隔、**同样不带前缀**，另有 `tag_string_artist` / `_copyright` / `_character` 分好的三路） |
| 画师 | `author` 字段 | 无 `author`，要从 `tag_string_artist` 取（实测确实非空） |
| 单页地址 | `https://yande.re/post/show/{id}` | `https://danbooru.donmai.us/posts/{id}` |
| 收藏数 | 无 | `fav_count` |

## 四、必须先修的阻断项

1. **UA**：`UserAgentPolicy.DEFAULT_USER_AGENT`（`UserAgentPolicy.kt:19`）是移动端 Chrome 串，
   而 `VeneraNetworkClient.kt:68` 只在请求**没有** UA 时填它 → Danbooru 的 JSON 和**图片全 403**。
   两条路各有一个现成机制，不新造：
   - **图片**：`ImageHeaderPolicy.builtin` 加 `"cdn.donmai.us" to mapOf("User-Agent" to "okhttp/4.12.0")`。
     `VeneraImageFetcher.kt:51` 已经在读这张表（且用 `header()` 是覆盖式），
     仓内已有同型先例：`"picacg.com" to mapOf("User-Agent" to "okhttp/3.8.1")`。
     画廊 ImageLoader 注册的就是这个 fetcher，所以**只改这张表就够**，不用动画廊代码。
   - **JSON**：`DanbooruClient` 自己 `Request.Builder().header("User-Agent", …)` ——
     显式设了就不会被 `VeneraNetworkClient` 覆写。
   ⚠️ **不能**改 `UserAgentPolicy` 的全局默认：那是漫画侧共用的，改了会波及所有源。
2. **`g` 档**：`GalleryPost.isAdultMarked` 现为 `rating != "s"`，接 Danbooru 后会把
   general（最干净的一档）**误打码**。实测：月榜 200 条里只有 1 条 `g`（少），
   但按"最新"取时 20 条里 `g` 占 8 条（多）—— 一旦将来放宽取法，这处就会整屏白糊。
   改成只放行 `s` / `g`。
3. **`id` 不再是全局唯一**：`GalleryPostRoute(postId: Long)` 必须变成 `(site, postId)`，
   否则二级页会拿 A 站的 id 去 B 站取图（yande.re #12250188 会静默取到 Danbooru 的另一张图）。
   同时列表 `items(key = { it.post.id })` 也要换成组合键，不然两站同 id 直接崩 `LazyLayoutKey`。

## 五、去重：数据说感知哈希不值得做

需求原文希望"最好加图片感知哈希，避免两站出现同一张图"。实测三条：

1. **只有单边有哈希**：Danbooru 每条带 128-bit `media_asset.pixel_hash`；
   yande.re **没有** `pixel_hash` 字段。单边有、另一边没有 → 跨站比对无从下手，
   除非我们自己下载解码算一遍。
2. **跨站重叠实测为 0**：yande.re（40 条月榜 + 120 条 order:score 头部）× Danbooru（200 条月榜），
   共 160 × 200 = 32000 对：
   - `md5` 交集 **0**
   - 规范化 `source`（去协议/www/尾斜杠、转小写）交集 **0**
   - `pixiv_id` 交集 **0**（yande.re 该字段全空）
3. **同站池内也几乎无重**：Danbooru 自己那 200 条里，md5 重复 **0** 对；
   128-bit pixel_hash 两两 Hamming 距离 **≤14 的对数 = 0**（随机基线中位数 ≈ 64）。
   → "同一张图以轻微变形出现在池内"这件事**在池子层面不存在**，只有"同画师不同构图"。

结论：pHash 要为每张候选图额外下载并解码原图（实测原图字节 2.8 MB ~ 31.9 MB 一条），
换来的实测去重收益是 **0**。本轮**不做 pHash**，只做零成本的三层：
`(site, id)` → `md5` → 规范化 `source`。
若将来出现"两站同一张"的实例再上，且应走"只比已下载的缩略图像素、不额外下载"的路子
（Danbooru 的 `media_asset.variants[]` 还白给 128-bit `pixel_hash`，同站去重可以直接用它、零下载）。

## 六、混合与带权随机

### 6.1 取数
一次"换一批"并发 **4 个请求**（Danbooru 1 个 + yande.re 3 个），任一失败不整体失败（能出多少出多少 + 明示缺哪站）：

| 站 | 端点 | 量 |
|---|---|---|
| Danbooru | `/explore/posts/popular.json?date=<今天>&scale=month&limit=200&page=k` | 200 条/页，**站方自己的月榜页**；实测第 1 页可摆的 160 张（40 条 mp4 滤掉） |
| yande.re | `/post/popular_by_month.json?year&month` **当月 + 上月**各一份 | 40 + 40 条（实测固定、不可翻页，所以要两份） |
| yande.re | `/post.json?tags=order:score&limit=60&page=k` | 60 条，补足配比用（全站历史人气） |

### 6.2 权重（站内归一，不跨站比原始分）
两站的分数**根本不是同一个量**：yande.re `order:score` 是**全站历史**累计分（实测头部 649~1674、中位 731），
Danbooru Explore 月榜是**当月内**的热门位次（实测 299~827），`fav_count` 一个是 274~793、一个压根没这字段。
数值偶然落在同一量级 ≠ 可比 —— 一个 2019 年的老神图和一个本月新图在 yande 池里能同分。
→ 一切先转成**池内分位**：

```
quality = 0.45·rank(score) + 0.20·rank(fav_count) + 0.25·rank(megapixels) + 0.10·rank(file_size)
```
- `rank(x)` = 该条在本**站**候选池内的分位（0~1）。
- yande.re 无 `fav_count` → 该项权重按比例摊给其余三项（不是当 0 算，当 0 会系统性压低整站）。
- `megapixels` 用 `width*height`，先夹到 `[0.2, 40]` 百万像素（实测池内 0.48~55 MP，
  55 MP 那条是横幅长图，不夹会把分数维度冲淡）。

### 6.3 采样：热门池 + 随机，带权不放回
- **Efraimidis–Spirakis**：每条 `key = random()^(1/weight)`，按 key 降序取 ——
  等价于按权重不放回抽样，一行排序搞定，不会重复抽中同一条。
- 顶层再叠一层**新鲜度衰减**（仅对 Danbooru 有意义，它的池就是当月）：
  `weight *= 1 / (1 + age_days / 30)`，让"这个月刚火的"更容易出现，但不排老。
- **首批前缀锁定**：一屏 60 张里，前 12 张取权重最高的 12 个的**随机排列**，
  后 48 张纯带权随机。避免"随机到第一屏全是冷门"（用户明确要"保证质量"）。
- 翻页：`page` 只是把已生成序列切片，**不重跑采样**；下拉刷新才重抽。

### 6.4 双源配比：动态，不强制 50:50
按"内容量 × 质量"动态定，实测出来的量级差很大（yande.re 月榜 40 条 vs Danbooru 200 条/页）：

```
share(D) = supply(D)·quality(D) / Σ[supply·quality]，各站 supply 用"该页返回的**图片**条数"
```
- 结果夹在 `[0.3, 0.7]`：任一站挂掉或被限流时，另一站最多补到 70%，
  不会悄悄变成单源（那会让"两站混合"这件事名存实亡 —— 属于静默降级，本仓忌讳）。
- 一站内条目不足时，缺口由另一站按同规则补，并在空态/错误态里**明说**缺了谁。

### 6.5 缓存与节流
- 候选池 + 采样结果在内存里按"池键"缓存（与第一轮 ViewModel 同思路）；
- 池 TTL 10 分钟（月榜一天才变一次，但用户会杀进程重进，没必要落盘）；
- 同一 `(site, page)` 60 秒内不重发 —— 实测无限流形态，但两站各 200 条已经是够用的量，
  不做请求风暴。

## 七、UI：卡片左下小来源胶囊

- 现成的 `VeneraSourceBadge(name)` 是**左上角、`shape.extraSmall` 小圆角**，
  且被漫画侧 6 个页面共用 → 不改它（改了会一起动）。
- 本轮要的是"图片**左下**、很小、**胶囊**"。做法：画廊卡片走 `VeneraCover(content = {…})`
  那个 BoxScope 插槽（KDoc 里就写着"用于放置 SourceBadge 等"），
  放一个**画廊私有的**极小胶囊：`Alignment.BottomStart` + `shape.extraLarge`（胶囊）
  + `type.badge` 字号 + `StatusColors.BadgeSurface` 固定深色底（压任意封面都可读）。
  尺寸口径全部取现有 token（`badgeInset` / `badgeHorizontalPadding` / `badgeVerticalPadding`），
  **不新造数字**。
- 文案：`Danbooru` / `yande.re`。胶囊**不可点**（本轮没有"按源筛选"的落点，做了就是假按钮）。
- 卡片上其他文字（评分、尺寸等）本轮**继续不放** —— 用户要的是"只显示一个很小的来源"。

补充一条空态事实（实测）：两站热池合起来几乎没有安全内容
（Danbooru 月榜 e=85/s=75/q=39，yande.re `order:score` 头部 e=84/q=30/**s 只有 6**），
所以「成人内容处理 = 彻底隐藏」被选中时整屏会被剔空。
空态文案必须说清是**规则挡完的**，不能报成"没有图"（第一轮已定这条，两源后更要盯）。

## 八、落地顺序与验收

改动面（预计新增 4 个文件 + 改 6 个）：

| 文件 | 动作 |
|---|---|
| `gallery/data/DanbooruClient.kt` | 新增：月榜 + 单条，带非浏览器 UA，非 2xx/非 JSON 一律抛错 |
| `gallery/data/GallerySite.kt` | 新增：枚举（YANDERE / DANBOORU）+ 显示名 + 单页前缀 |
| `gallery/data/GalleryPost.kt` | 改：加 `site`、`favCount`、`variants` 取宽高的兜底；`isAdultMarked` 放行 `g`；`pageUrl` 按站分 |
| `gallery/domain/GalleryPool.kt` | 新增：滤非图片 + 去重 + 分位归一 + ES 加权采样 + 动态配比（纯函数，可单测） |
| `gallery/ui/GalleryViewModel.kt` | 改：持有"已采样序列"，翻页只切片 |
| `gallery/ui/GalleryScreen.kt` | 改：两源取数、组合键、左下来源胶囊、部分失败的明示 |
| `gallery/ui/GalleryPostScreen.kt` / `GalleryFullViewer.kt` | 改：按 `site` 取单条与三档地址 |
| `feature/Navigation.kt` | 改：`GalleryPostRoute(site, postId)` |
| `gallery/data/GalleryImageLoader.kt` | 改：`cdn.donmai.us` 走带 UA 的 fetcher |

单测（纯逻辑，不需要设备）：
1. `g` 不打码、`e/q` 打码、未知/空 打码；
2. Danbooru 样本解析出与 yande.re **同名**的归一字段（三档 URL、比例、标签、画师）；
3. 非图片条目（mp4/zip）被滤掉，且滤完数量不足时**报错**而不是交回短列表；
4. 两站同 `id` 不互相覆盖（组合键）；
5. md5 / 规范化 source 命中时只留一条，且保留的是**质量分更高**的那条；
6. 采样确定性：固定 seed → 同一序列（可测"不重复"和"前 12 是头部"）；
7. 一站返回空/失败时另一站占比不超过 0.7，且失败原因被带出。

真机验收（**只能由用户点**）：
1. 画廊首屏两站的图都出（Danbooru 不出 = UA 那一条没生效，第一时间能看出来）；
2. 下拉两次，两屏内容不同但都"眼熟是好图"（带权随机生效且没退化成冷门）；
3. 上滑翻页不出现同一张重复；
4. 卡片左下胶囊两站都显示、字号够小、压深色图仍可读；
5. 点 Danbooru 的图进二级、再点进三级 —— 标题栏 id/站点正确，取回的就是那张；
6. 打码模式下 `g` 档不被糊、`e/q` 被糊；
7. 断网/单站 502 时，页面说清缺哪站，而不是显示"没有图"。

## 九、拍板结果（2026-09-25 用户确认，三条全部选推荐项）

1. **pHash 不做**（§五有实测数据：三种键跨站交集全 0，同站内 Hamming ≤14 也 0 对）。
   去重只走 `(site, id)` → `md5` → 规范化 `source` 这三层零成本键。
2. **yande.re 月榜优先、不足由 `order:score` 补足**到目标配比。
3. **首屏前 12 张锁头部 + 两源配比夹在 `[0.3, 0.7]`**（§6.3 / §6.4 照此实现）。

另两条按 §四 直接做，不另行确认：`g` 档放行、路由与列表键改成 `(site, id)`。
Danbooru 的成人内容照常进池、交给现有分级守卫，不新造开关。

---

## 十、落地记录（2026-09-25 同日）

`compileDebugKotlin` / `testDebugUnitTest`（全仓 156 条，0 失败）/ `assembleDebug` 三项全绿。
画廊侧 32 条单测：`GalleryGuardTest` 5、`GalleryPostParsingTest` 6、`GalleryDanbooruParsingTest` 7、`GalleryPoolTest` 14。

实际改动比 §八 的表多两处、少零处：

| 文件 | 动作 |
|---|---|
| `gallery/data/GallerySite.kt` | 新增（显示名 / 单页前缀 / 路由键） |
| `gallery/data/GalleryPost.kt` | 改：去掉 `@Serializable`，改成**归一模型**（带 site、favCount 可空、large 档宽高、`uid`、`sourceKey`）；`isAdultMarked` 放行 `g` |
| `gallery/data/YandeReClient.kt` | 改：站方字段名收进 `YandeReDto`；新增 `fetchMonthlyHot()` |
| `gallery/data/DanbooruClient.kt` | 新增：月榜 + 单条 + `DanbooruDto`（含 `media_asset.variants`）；403 带 CF 特征时报"被 Cloudflare 拦截" |
| `gallery/domain/GalleryGuard.kt` | 未改（判据在模型侧，自动跟着 `g` 生效） |
| `gallery/domain/GalleryPool.kt` | 新增：纯函数池（滤非图片 / 三键去重 / 站内分位 / ES 带权不放回 / 动态配比 / 头部锁定 / 交错排布） |
| `gallery/domain/GalleryFeedSource.kt` | 新增：两站并发一轮 + "哪一站没给内容"的原因 |
| `gallery/ui/GalleryViewModel.kt` | 改：`round` / `seedBase` / `sourceNotice` / `shownKeys()` / `accept()` |
| `gallery/ui/GalleryScreen.kt` | 改：双源取数、`uid` 做列表 key、左下来源胶囊、单源退化提示条、空态文案按两站实测改写 |
| `gallery/ui/GalleryPostScreen.kt` / `GalleryFullViewer.kt` | 改：`site` 进签名与缓存 key；信息卡多一行"收藏"（**只在站方给了的时候**）；`g` 档标签 |
| `feature/Navigation.kt` | 改：`GalleryPostRoute(siteKey, postId)` |
| `data/network/ImageHeaderPolicy.kt` | 改：内置表加 `"donmai.us" → User-Agent`（图片那一路） |

### 与本文的三处偏差（都是刻意的）

1. **§6.3 的"新鲜度衰减"没做**。Danbooru 那一路本身就是当月池（实测 `created_at` 全在 2026-09），
   衰减是空操作；yande.re 那一路是**全站历史**人气，一衰减就把"人气"静默改成"本月人气"——
   两站语义不对称，加这层只会把混合池推向一边。要做就得先给两站统一"月龄"口径，留待有需求时。
2. **UA 字面量存在两处**（`DanbooruClient.API_USER_AGENT` 与 `ImageHeaderPolicy` 的 `"donmai.us"` 条目）。
   让漫画侧共用基础设施去 import 画廊包会破坏隔离方向，所以两边各留一份、互相注明"必须同串"。
   host 键用后缀 `donmai.us`（`headersFor` 是 `endsWith` 匹配），一并覆盖 `cdn.` 与主站。
3. **路由站点键是 String 不是枚举**，未知键 `requireNotNull` 直接炸。
   同一路由位置塞自定义对象曾让 `generateRoutePattern` 编译失败（§八 第一轮的坑），
   字符串键最稳；而"认不出站点"只可能是编码错，退回任一站都会把另一站同号码的画当答案交出去。

另外：卡片胶囊是画廊私有的 `GallerySourcePill`，**没有**动被漫画侧 6 个页面共用的 `VeneraSourceBadge`
（那枚的位置口径是左上小圆角，与这轮的"左下胶囊"不同）。

### 一条已知的交互面
共享 OkHttpClient 上挂着 `CloudflareBypassInterceptor`：任何 403/503 带 CF 特征都会 `runBlocking`
拉起过盾 WebView。本轮给 Danbooru 显式带非浏览器 UA 后实测不会 403，所以正常路径碰不到它；
一旦真机上弹出过盾页，就是这条链被触发（成因通常是出口 IP 变化），不是画廊自己的逻辑错。

### 尚未验证
全部只到"编译 + 单测 + 出包"。§八 那 7 条真机验收项**一条都还没看过** —— 尤其第 1 条
（两站的图都出得来）只能由用户在设备上点。

---

## 十一、同日第二次修正：月榜换成用户点名的 Explore 端点（2026-09-25 深夜）

用户反馈"我看还是没有 donmai 的源"，并给出 `https://danbooru.donmai.us/explore/posts/popular?date=2026-08-24&page=1&scale=month`
问能不能用、要与 `https://yande.re/post` 一起展示。两件事分别回答：

### 1. "没看到 donmai"的原因（当时判断，未推翻也未经真机确认）
`adb devices` 列表为空 —— 手机上装的还是这一轮之前的包（本轮包 02:16 才出）。
**这不是代码问题的证据，也不是反证**：真机一条都没跑过，UA 那条只在开发机出口验过。

### 2. 这个端点能不能用：能，而且比我选的更对（已换）
实测 `.json` 版（`/explore/posts/popular.json`）：

| 项 | 实测 |
|---|---|
| 匿名 + 非浏览器 UA | **200**，返回**完整 post 对象数组**（与 `/posts.json` 同一套字段，含 `media_asset.variants`）→ 现有 DTO 零改动直接复用 |
| `scale=month` + `date` | 语义就是"那个月的热门榜"，`date` 给区间内任意日期即可（用户 URL 里的 `date=2026-08-24` 取的就是 8 月榜，实测首条 `created_at` 在 08-07） |
| 翻页 | `page=1/2/7` 各 200 条、**相邻两页零重叠** → 池子真的能往深翻 |
| 分数/收藏 | 第 1 页 score 827~299、`fav_count` 274~793（比 `order:rank` 那份 9~416 高一个量级 —— 这份是"这个月最火的头部"，那份是"排名尾巴也带上"） |
| 成分 | 第 1 页 200 条里 **`mp4` 40 条 = 20%**（不是 `order:rank` 那份的 4%）→ 可摆的只有 160 张；另 **52 条没有 `sample` 变体** |

改动（三个文件）：
- `DanbooruClient.fetchMonthlyHot(page)` → 打 explore 那份，`date` 用当天、`scale=month`；`fetchById` 仍走 `/posts.json?tags=id:N`。
- `YandeReClient.fetchMonthlyHot(monthsAgo)` → 加"往前几个月"参数（站方这一档实测固定 40 条、不可翻页）。
- `GalleryFeedSource` 第一轮取 yande.re **当月 + 上月**两份（80 条）—— 只取当月那 40 条会在配比里被 Danbooru 的 200 条压没，那等于"两站混合"名存实亡。

顺带把 §一 / §二 / §6.1 / §6.2 里基于 `order:rank` 的实测数值全部改成 explore 的数字
（原来那句"两站分数差 24 倍"是 `order:rank` 的量纲，换端点后不成立，已改成
"两个根本不是同一个量：全站历史累计分 vs 当月位次分"，加权走池内分位这条结论不变）。

构建：`testDebugUnitTest`（156 条）+ `assembleDebug` 全绿，包 02:16。

---

## 十二、同日第三次改判：删掉权重与月榜，改成两站**最新**混搭（2026-09-25）

用户原话：「删掉之前我说的权重，和月度榜，改成 `https://danbooru.donmai.us/` 和
`https://yande.re/post` 这两个最新图片的混搭，比例均匀点就行」；
追一条排法口径：「**不采用严格交替**。总体数量比例保持接近均衡，单一来源允许连续出现 3~4 张，
但避免长时间连续来自同一来源。」

### 删掉的（本文 §五~§六 那套整体作废，保留原文只为留证据）

候选池、站内分位归一、四维加权、Efraimidis–Spirakis 带权不放回抽样、首屏锁头部、
动态配比夹 0.3~0.7、随机种子、以及为它服务的三个"月度"端点调用
（Danbooru `explore/posts/popular.json` 与 `posts.json?tags=order:rank`、
yande.re `popular_by_month.json`）。`GalleryPool.kt` 整个文件删除。
**感知哈希仍然不做**（§五 的实测结论没被推翻，只是这一版连"去重要不要 pHash"这个语境都撤了）。

### 换成什么

一轮两个请求：`yande.re/post.json?limit=30&page=k` 与 `danbooru.donmai.us/posts.json?limit=30&page=k`
（都不带 tags = 站方首页那份最新列表），各 30 条 → 合并 60 张一屏。

排法 `GalleryMerge`：**每一拍给两站同样长的一段**，段长取自固定序列 `[2,1,3,1,2,3,1,2]`（平均 2、最长 3）。
- 两站每拍拿到的张数**恒等** → 总量必然均衡（这就是"比例均匀点"）；
- 一拍之内是 `Y…Y D…D`，拍与拍相接处固定"先 yande 后 Danbooru" → 上一拍的尾巴（D）与下一拍的首部（Y）
  必然不同源，**不会拼出第 4 张连出**。这一点是被单测抓出来的：初版让两站轮流先出，
  边界就拼出了 4 连（用户给的上限是 3~4，但既然能做正好就做 3）。
- 没有任何随机、没有分数参与排序 → 同输入必得同序列，翻页/返回重建/重试都是同一屏。
- 一边先给完时，另一边只能连着排 —— 那是真的没有另一站可混了，不是排布缺陷。

### 保留的两条判据（都还在，因为它们是实测换来的、与权重无关）

1. **扩展名白名单**：`{jpg,jpeg,png,webp,gif}`。Danbooru 列表会混 `mp4`
   （月榜那份实测 20%，最新这份这次 0 条 —— **不能当成不存在**），
   而站方 `type:jpg` / `file_ext:jpg` 实测返回 0 条、`-type:video` 与 `order:rank` 组合时被忽略。
   整站被滤光时**报错**而不是交回空列表。
2. **去重**：`uid`（站点+id）→ `md5` → 规范化 `source`。翻页靠 `exclude` 已上屏身份 ——
   最新流是新图往前插的，第 2 页与第 1 页会漂移重叠，不排必然回看旧图（有单测）。

`g`（general）放行、两站 UA、路由带站点键、`favCount` 可空 —— 这四条都保留。
其中 `g` 这一版更要紧了：**Danbooru 最新 30 条实测 `g` 占 15 条**（半屏），
沿用第一轮 `rating != "s"` 会白糊一半。`favCount` 这一版不再参与任何排序，
只剩二级信息卡那一行"收藏"（只在站方给了才摆），所以字段留着。

### 两站"最新"的实测（2026-09-25，各 30 条 × 2 页）

| 站 | 排序 | 翻页 | 分级 | 扩展名 |
|---|---|---|---|---|
| Danbooru `/posts.json` | `created_at` 与 `id` 双递减 ✅ | page 1/2 零重叠 ✅ | **g 15** / e 7 / q 3 / s 5 | jpg 16 / png 14（无非图片） |
| yande.re `/post.json` | 同上 ✅ | 同上 ✅ | e 6 / q 12 / **s 12** | png 19 / jpg 10 / webp 1 |

### 落地文件与构建

改：`GalleryMerge.kt`（新增，替代 `GalleryPool.kt`）、`GalleryFeedSource.kt`（两路并发、无种子）、
`YandeReClient.kt`（`fetchLatest`，删 `fetchMonthlyHot`，PAGE_SIZE 60→30）、
`DanbooruClient.kt`（`fetchLatest` 打 `/posts.json`，删 explore 调用与日期拼装）、
`GalleryViewModel.kt`（删 `seedBase`/`seedFor`）、`GalleryScreen.kt`（文案与调用）。
删：`GalleryPool.kt`、`GalleryPoolTest.kt`；新增 `GalleryMergeTest.kt`（12 条，含三条排法口径）。
`testDebugUnitTest` 全仓 154 条 0 失败 / `assembleDebug` 全绿，包 02:34。**真机仍未验。**

---

## 十三、同日第四次改判:两站**上一天热门**各 20 张 + 打乱 + 放行 mp4（2026-09-25）

用户原话：「画廊改成 `https://yande.re/post/popular_recent?period=1d` 和
`https://danbooru.donmai.us/explore/posts/popular?date=2026-09-24&page=1&scale=day` 上一天的热门
**各取 20 张然后打乱**，如果 donmai 有 mp4 就引入 mp4」。
播放器选型经询问定了 **AndroidX Media3 ExoPlayer**（仓里此前零视频能力：
`media3`/`ExoPlayer`/`VideoView` 全仓 grep 为 0）。

### 1. 端点实测（2026-09-25，探针直打站方）

**yande.re**

| 请求 | 结果 |
|---|---|
| `post/popular_recent.json?period=1d` | 200 / **固定 40 条** |
| `…?period=1w` | 200 / 40 条，与 1d **只重叠 2 条** → `1w` 是真生效的另一批 |
| `…?period=1mo` | 200 / 40 条，与 1d **完全相同（40/40）** → 不认识的 period 值**静默退回 1d** |
| `…?period=1d&limit=500` | 仍 40 条 → `limit` 被忽略（与 `popular_by_month` 同一口径） |
| `post/popular_by_day.json` | 200 / 32 条，与 `popular_recent?period=1d` **零重叠** —— 两份不同的"日榜"算法，别混 |

**Danbooru** `explore/posts/popular.json?date=2026-09-24&scale=day&page=1&limit=200`
→ 200 条；字段与 `posts.json` 同一套（上一轮已实测过 page 间零重叠）。
⚠️ **`scale=day` 不带 `date` 直接回 0 条**（不报错、不 403）—— `date` 是必填。

当天池子构成：`jpg 128 / png 61 / `**`mp4 10`**` / zip 1`；分级 `e=87 / q=40 / s=72 / g=1`。
（月榜那份是 20% mp4，日榜只有 5%；分级守卫照旧要紧，`g` 照旧放行。）

**mp4 条目的字段形状**（与图片不同，不能照 `sample` 那套推）：

| 字段 | 实测值 |
|---|---|
| `has_large` | **false**；`large_file_url` == `file_url` == 原片 `.mp4` |
| `media_asset.variants` | `180x180.jpg` / `360x360.jpg` / **`720x720.webp`（静帧）** / `original.mp4` |
| 原片 | HEAD 200 `video/mp4`；样本 **16.1 MB / 42.77 s**、26.1 MB / 26.3 s ⇒ **没有更小的视频转码档** |
| 顶层 `duration` | **不存在**，只在 `media_asset.duration` |
| 顶层 `type` | 这份 200 条里**全为 undefined**（§早期记的 `type:jpg` 是 tag 查询语法，不是 JSON 字段） |

### 2. 换掉什么、删掉什么

- **删**：两站的"最新"分页取数（`fetchLatest(page)`）、`GalleryMerge` 的段交错与 `RUN_PATTERN`、
  翻页 `exclude` 排重、`hasMore` / `loadMore` 全套、页尾「上滑加载更多」。
  日榜是一屏到底的固定池子（yande 40 / danbooru 200），留着翻页机制就必然出现
  "到底了还显示加载更多"—— 与搜索页 §7 同一类缺陷，这次是源头就没有下一页。
- **留**：扩展名白名单（**改成含 `mp4`/`webm`**，`zip` 继续滤掉）、三键去重
  （`uid` / `md5` / 规范化 `source`）、每站失败原因如实进 `sourceNotice`、分级守卫、来源小胶囊。
- **新增**：日榜抽样 + 种子打乱；卡片视频角标；二级/三级页 ExoPlayer 播放。

### 3. 三条必须写住的坑

1. Danbooru `scale=day` 缺 `date` → **静默 0 条**；date 用本地"昨天"（`yyyy-MM-dd`）。
2. yande.re 的 `period` 只认 `1d`/`1w`，其它值静默退回 `1d`；固定 40 条、`limit`/`page` 无效。
3. **`cdn.donmai.us` 只认非浏览器 UA** —— media3 的 `DefaultHttpDataSource` 走自己的 OkHttp，
   既不过 `VeneraNetworkClient` 也拿不到 `ImageHeaderPolicy` 的 header 表。
   播放器必须自己带 `User-Agent: Venera/1.0 (Android)`，否则视频 403 + `cf-mitigated: challenge`
   —— 而图片是正常的，观感就是"缩略图能看到、点开播不了"，最难查。

### 4. 打乱必须锁种子

本项目导航条目被覆盖时**组合会销毁**（见记忆「导航条目会重建组合」）。裸 `shuffle()` 会让
"点进大图再返回"整屏换序。所以种子存在 `GalleryViewModel` 里，只有下拉刷新/错误重试才换种子。

### 5. 各取 20 的抽法

两站各**随机抽 20**（不是取热度前 20）：日榜一天内固定，固定取前 20 会让一天里每次进画廊都是同一屏。
抽样与打乱同用一个种子 → 同一次进入内稳定，刷新才变。

### 6. 视频的观感与流量口径

卡片用 `180x180.jpg` 静帧 + 「▶ N″」角标；**进页面不自动播**，点播放才拉原片 ——
一天池子里 5% 是视频、单条 16~26 MB，自动播等于随机替用户提前下载几十 MB 流量。
播放用 Media3 `PlayerView` + `MediaController`，离开组合必须 `release()`。

### 8. 落地记录（同日实现完毕）

- **数据层**：`YandeReClient.fetchDailyPopular()`（`popular_recent.json?period=1d`）、
  `DanbooruClient.fetchDailyPopular(date)`（`explore/posts/popular.json?date=&scale=day&limit=200`）。
  两站的 `fetchLatest(page)` 与 `PAGE_SIZE` 一并删除；两站都把**空列表当失败**抛出（`date` 缺省、
  CF 拦截、熔断都会长成 0 条，不能退成"今天没有热门"）。
- **模型**：`GalleryPost` 加 `durationSeconds` + `isVideo` / `videoUrl` / `durationLabel` +
  `VIDEO_EXTS`；`DanbooruDto.toPost()` 对视频把中间档换成 `720x720` 静帧（不换就会把 `.mp4`
  交给 Coil 解码，观感是"一张永远加载失败的图"）。
- **混排**：`GalleryMerge` 换成 `mix(pools, seed)` —— 白名单含视频、三键去重、各站 `shuffled(Random(seed+站点序))`
  抽 20、整体 `shuffle(Random(seed))`。交错额度与 `exclude` 翻页删除。
- **状态**：`GalleryViewModel` 去掉 `round/hasMore/isLoadingMore/loadMoreError/shownKeys`，
  改持 `seed` 与 `date`；`refresh()` 换种子，所以「刷新」真的换一批、换个顺序。
- **UI**：页尾从「上滑加载更多」换成一行到底读数（`GalleryFeedEnd`，样式照搜索页 `ss-end`）：
  「2026-09-24 的热门已全部显示（yande.re 20 · Danbooru 20，含 3 个视频）」；
  卡片右下角加 `▶ N″` 角标（与来源胶囊分角，共用同一套 badge token）；
  二级页视频就地播（`GalleryVideoViewer`），**不进三级**；刷新按钮文案从"重新拉最新"改"换一批"。
- **新依赖**：`androidx.media3:media3-exoplayer` + `media3-ui` **1.11.1**（最新稳定版，
  版本号是从 `dl.google.com` 的 maven-metadata 现查的，不是猜的）。
  **APK（universal）93,710,048 → 98,110,389 = +4.20 MB**。没引 `media3-session`/`cast`/`downloader`。
- **UA 分流**（实现时最容易写错的一条）：播放器按站点带 UA —— Danbooru 用
  `DanbooruClient.API_USER_AGENT`（非浏览器串），yande.re 用全局默认串（它的图片流量一直如此且实测正常）。
  media3 不过 `VeneraNetworkClient` 也拿不到 `ImageHeaderPolicy`，所以这一层必须自己带。
- **构建**：`:app:testDebugUnitTest` **178 条 0 失败**（`GalleryMergeTest` 重写为 13 条、
  `GalleryDanbooruParsingTest` 7→11 条含视频 fixture、新增 `GalleryFeedDateTest` 3 条钉"上一天"的算法）/
  `:app:assembleDebug` BUILD SUCCESSFUL（11:34 出包）。
- **真机未验**：① 视频是否真能播（尤其 yande.re 的 `webm` 那侧 UA 假设）；② 两站日榜是否都真给上内容；
  ③ 16~26 MB 原片在移动网络下的首帧等待；④ 打码态下视频画面是否同样被糊（代码上 `blur` 挂在容器上，未经眼验）。
- **刻意没做**：视频不进画廊那套 Coil 磁盘缓存（播放器自己拉，画廊 512 MB 预算不含视频）；
  不做"`date` 那天空了就自动往前退一天"的静默回退（页面上那行日期标签就会是假的）；
  视频不进三级缩放层。

---

## 十四、真机反馈「yande 只有 13 张 / Danbooru 一张都没有」的实测归因与判据修正（2026-09-25）

第十三节那四条「真机未验」里第 ② 条撞上了。用户给的表象是"图少了"，
另附一条 yande.re 隐藏图片的油猴脚本，怀疑站方藏图。**两条都不是成因**，实测如下。

### 1. 取证链：成因是用户自己那条关键字 `ai`

| 取到的东西 | 读法 | 结果 |
|---|---|---|
| `venera_guard_prefs.xml` | `nsfw_mode` | **`OFF`** → 分级模式不参与隐藏，排除"被 HIDE 剔掉" |
| `content_guard_rules` 表（run-as 拉库 + `node:sqlite` 读） | 4 条全部启用 | `TAG /ai/i`、`TAG /ai生成/i`、`KEYWORD "ai"`（**非正则**）、`KEYWORD "AI生成"` |
| `isComicBlocked` 源码 | 非正则走 `target.contains(pattern)` | 而画廊把**整条 tag 列表**喂了进去（`GalleryScreen` 卡片派生处） |
| 两站当天真实日榜 | 用同一套判据回放 | yande.re **12/40** 命中、Danbooru **199/200** 命中 |

命中的都是些啥：`long_hair`(144 条)、`hair_ornament`(49)、`very_long_hair`(43)、`tail`(39)、
`black_hair`(39)、`white_hairband`、`paid_reward_available`、`official_alternate_hairstyle` ——
下划线标识符里含子串 `ai` 的地方太多了。抽样后期望上屏 **yande 14.1 / Danbooru 0.1**，
与真机看到的 **13 / 0** 对上。

**结论**：两站请求**都成功了**，所以页面顶部那条来源提示是空的；屏上少的是判定链的效果，
而页尾那行读的是**守卫之前**的 `vm.posts`，于是写着「yande.re 20 · Danbooru 20」——
一条假读数把唯一的线索抹掉了。这属于「实现了但零调用点」的镜像病：**读数接错了源**。

### 2. yande.re 的「隐藏图片」：客户端不用做任何事

那个脚本（greasyfork 572984）实测**零网络请求**，纯 DOM：`li.classList.remove(className)`，
把 HTML 列表里被 CSS 藏掉的条目放出来。被藏的标志位在 JSON 里叫 **`is_shown_in_index`**，
今天这 40 条里有 **2 条是 `false`**。我们的取数走 JSON、**从不读这个字段**（全仓 grep 无命中）
→ 那 2 张本来就上屏了，没有可"复现"的手法。
另一类（`is_banned`）在匿名响应里根本不存在，站方没给就取不回，不是我们的过滤。

### 3. 判据修正：只有画廊走「词元整词」

用户拍板「只有画廊走整词（推荐）」，漫画侧一字未动 —— 那边的 tag 是自由文本
（`AI汉化`、`中文翻译`），中文没有分隔符，子串是唯一能用的判据，改成整词会把已挡的放出来。

新增 `security/guard/GalleryBlockMatch.kt`：
- **tag**：整串相等 **或** 按 `: _ - 空格` 切成的词元相等（正则用 `matches()` 整词，不是 `find`）
  → `ai` 命中 `ai` / `ai_generated` / `generated_by_ai` / `female:ai` / `ai-生成`，
  **不**命中 `long_hair` / `tail` / `paid_reward_available`。
- **author**：仍是子串（人名是自由文本，与漫画侧同口径）。
- `AUTHOR` 只比作者、`TAG` 只比标签、`COMIC_ID` 这类不参与（图站没有漫画身份）。
- 非法正则 → `false`（判定在滚动密度上，不能让一条坏规则炸列表）。
- `ContentGuardManager.findGalleryBlockedRule(author, tags)` 返回**命中的那条规则**，
  页面要说得出"是哪条挡的"。
- 一级与二级**同一把判据**（`GalleryPostScreen` 也从 `isComicBlocked` 换过来了），
  否则会出现"墙上被挡、点进来全裸"的分叉。

预期效果（同一批真机规则、同一份日榜回放）：**yande 0/40、Danbooru 0/200 被挡 → 20 + 20 全上屏**。

### 4. 页尾读数改成实际落屏，并把"少了"按成因分开报

`GalleryFeedEnd` 不再吃 `vm.posts`，改吃新的 `GalleryWall(cards, blockedCount, blockedRules, hiddenByRating)`：

```
2026-09-24 的热门已全部显示（yande.re 20 · Danbooru 18，含 3 个视频；另有 2 张命中屏蔽规则 ai）
```

- **各站张数含 0 也照报**（以前 0 会被 `mapNotNull` 整段省略，正好把事故藏起来）。
- 规则命中与「按分级收起」**分两截报** —— 用户能做的处置完全不同。
- 全被挡完时的空态文案把**规则原文念出来**（`命中屏蔽规则：ai、AI生成`），不再只给一句泛泛的"改设置"。

### 5. 落地记录

- 新增 `GalleryBlockMatch.kt` + `GalleryBlockMatchTest.kt`（7 条：不误伤/该挡仍挡/中文整串/作者子串/
  类型隔离/坏正则不炸/未知类型不参与）。
- `:app:testDebugUnitTest` **185 条 0 失败**（178 → +7）；`:app:assembleDebug` 绿，
  universal 包 98,110,389 → **98,118,361**（+7,972 B）。
- 待用户真机复看：应为 **yande.re 20 · Danbooru 20**，页尾那行与实际数得上一致。

## 十五、大图页四档加载（2026-09-26，用户真机反馈"打开图片骨架图加载很久"）

### 1. 归因：不是骨架本身慢，是**中档挂错了档位**

yande.re 的 `jpeg_url` 一直被当成"二级大图"那一档（对照表里就是这么写的）。实测推翻：

- `jpeg_width/jpeg_height` **恒等于** `width/height`（随机 30 条 30/30），
  即它是**原分辨率的 JPEG 重编码**，不是降档 —— 同一份池子里有 9600×5400、10240×5760；
- 单条字节量随原图走（月榜那批实测原图 2.8 MB ~ 31.9 MB），
  也就是说"打开一张图"要先下一个**原图大小**的文件，且翻回来重看还要重下一次（大图档没进磁盘缓存策略）。

站方真正的中档是 `sample_url`：长边 ≤1500，原图本来就小时站方把它回落成 `/image/` 原图
（实测 70/70 条非空 → 没有 404 风险，不需要额外兜底）。

### 2. 四档实测（`Content-Length`，逐个 HEAD）

| 站 / 条目 | 开门档 | 中档 | 原档 |
|---|---|---|---|
| yande.re #600000（原图 3508×2480 PNG） | `preview_url` 300×212 / **13.7 KB** | `sample_url` 1500×1060 / **209 KB** | `file_url` PNG / **6.6 MB**（`jpeg_url` 是 964.7 KB 的原分辨率重编码） |
| Danbooru #6000000（原图 860×1280 JPG） | `360x360` 242×360 / **28.8 KB**（`preview_file_url` 只有 121×180 / 9.3 KB） | `sample` 850×1265 / **164 KB** | `original` / **360 KB** |

Danbooru 变体齐整度（60 条抽样）：53 条 `180/360/720/sample/original`，
6 条（视频）`180/360/720/original`（**视频没有 sample**，中间档取 `720x720` 静帧），
1 条连 `variants` 都是空的（被删条目）→ 开门档必须有兜底链。

### 3. 落地

- `GalleryPost` 新增 `fastUrl`（开门档，"最便宜且铺满屏还认得出"的那一档）；
  `YandeReDto` 接 `sample_url`/`sample_width`/`sample_height`，`largeUrl` 改为 sample 档；
  `DanbooruDto` 的 `fastUrl` 取 `360x360`，兜底 `preview_file_url`。
- `GalleryPostScreen.GalleryViewerMedia` 改成**三档叠画**：开门档 → 中档 → 原档（HD 才有），
  谁先到谁先显形。缩放/点击改挂在**容器**上（原先只挂在最上面那一档，中档没回来时点画面切不动 chrome）。
- 加载态分两段：一档都没落位 → 整块骨架（全站口径）；已有画面在屏、更高的那一档还在飞 →
  只挂一个 M3 波浪环（中档未到时居中、HD 未到时右下角），**不再盖满屏骨架**。
- 环走**固定高对比那对色**（`StatusColors.BadgeSurface` 圆底 + `OnBadgeSurface` 环）：
  图的内容颜色不可控，主题色压在夜景插画上会读不出来 —— 与卡片角标同一条理由。
- 开门档请求**刻意不写 memory/disk cache key**：Coil 3.6.2 的默认键就是地址本身
  （`Keyer` 只认映射后的 Uri，尺寸不进键，读了 `coil-core` 的 `MemoryCacheService.key` 确认），
  不写键才白拿网格卡片那份缓存。
- ⚠️ **副作用**：yande.re 默认那一屏从"原分辨率 JPEG"降到"1500px sample"。
  1080~1440 宽的屏上两者目视一致（HD 钮仍给原图），换来的是打开从"等一个 1~3 MB 的文件"
  变成"等 209 KB"。若将来要在大屏上恢复默认原分辨率，做法是把 `jpeg_url` 作为**第四档**
  插在中档与原档之间，而不是把中档拨回去。
