# 图库模块（与漫画完全隔离）· 方案 · 2026-09-23

> **状态（2026-09-24 更新）**：§七 五条里 **P1/P2/P4/P5 已拍板并落地**，P0 的浏览部分已开工
> （`gallery/` 包：yande.re 客户端 + 独立 ImageLoader + 人气瀑布流 + 大图页 + 守卫只读接入）。
> 只剩 **P3（表放哪）未决 —— 因为本轮刻意没建表**（浏览不写库）。
> §五 那张端点表里的"待量"已经全部量掉，实测数字可以直接采信，别再靠 Danbooru 的端点表推断
> （`/post/hot.json` 与 `/post/{id}.json` 在 yande.re 上都是 404，`?id=` 会被静默忽略）。
> §一 那四个实测数字（三档 URL 的域名与尺寸、21 MB 原图、解码后 87 MB）已经验过，可以直接采信。

用户口径：**「得和现在的漫画全部隔离，全部单独出来才行」**。形态参照 `yueeng/moebooru`
（一个 Kotlin 写的图站客户端，服务 konachan / yande.re —— 注意它与 yande.re 背后那套 Rails 服务端
软件同名，我第一次搞混过）。

本文只定边界与分期，不含代码。§七 有 5 个拍板点。

## 一、为什么必须隔离（不是审美问题，是三处结构性冲突）

1. **`favorite_images` 表四列 `NOT NULL`**：`comic_id / comic_title / source_name / page_index`
   （`data/db/VeneraDatabase.kt:99-107`）。图库的图**没有母漫画**，并进这张表只能塞假值。
2. **`FavoriteImageItem` 的整条语义链假设母漫画存在**：`toComicItem()`、卡片上的"查看漫画"、
   "读到第几页"（`chapterTitle + pageIndex`）、作者靠本地真相反查。并进 = 用一条异常分支污染五条正常路径。
3. **解码尺寸差一个数量级**（实测一条 yande.re post）：

   | 档 | 字段 | 主机 | 尺寸 | 解码后位图（×4 B/px） |
   | --- | --- | --- | --- | --- |
   | 缩略 | `preview_url` | `assets.yande.re` | 105×150（2x 档 209×300） | ≈ 63 KB |
   | 中档 | `sample_url` | `files.yande.re` | 1047×1500 | ≈ 6.3 MB |
   | 大图 | `jpeg_url` | `files.yande.re` | 2442×3500 | ≈ 34 MB |
   | 原图 | `file_url` | `yande.re` | 3907×5600 / **21 MB PNG** | ≈ **87 MB** |

   一张原图解码后 87 MB —— 而我们现在**连一档都没分**：`VeneraApp.newImageLoader()` 里
   既没有 `memoryCache` 也没有 `diskCache` 设置，全部吃 Coil 默认值。图库一灌就是几百张 preview，
   漫画链路的缓存会被它挤掉；反过来给图库开大预算又会把漫画侧的默认口径带偏。**这是"共用一个
   ImageLoader"最实在的代价**，不是洁癖。

## 二、隔离边界（三档：不碰 / 只读共享 / 复用基础设施）

| 不碰（各走各的） | 只读共享（一个语义不能变两份） | 复用基础设施（纯技术层，不承载产品语义） |
| --- | --- | --- |
| `ComicSource` 接口（36 成员，漫画形状）与 JS 源脚本链 | **分级模式**偏好（同一个 NSFW 开关，不新造第二个） | `VeneraNetworkClient` 的 OkHttpClient（连接池 / Cookie / CF 过盾） |
| `comic_history` / `comic_favorite` / `favorite_images` / `reading_stats` | **屏蔽词规则**（图库里命中的 tag 同样剔除或打码） | `ImageFetchTag`（图片流豁免域名熔断，`HostCircuitBreaker.kt:110-127`） |
| 题材统计与 `TagNormalizer` 写入链（图站 tag 量级与分布完全不同，且存量假题材那条链有前科） | `VeneraTokens`（字号/间距/形状/颜色，观感一致的前提） | `VeneraImageLogger`、`VeneraShimmer` 骨架、`VeneraCard`、瀑布流 `LazyStaggeredGrid` 的既有用法 |
| `DownloadManager`（按 comic+chapter 建模） | | 跨 Activity 预测式返回那一套（09-22 的 `SettingsActivity`/`SettingsSubActivity` 模式） |
| 阅读器（章节-页模型） | | |

包结构：全部新代码进 `com.venera.compose.gallery/`（`data/ ui/ domain/`），
漫画侧**零文件改动**是验收标准之一 —— 除了入口那一处（§三）。

## 三、入口：三个冻结项挡路，以及唯一不动它们的走法

`FREEZE-STATEMENT.md` 的硬约束：

- `:43` `:49`：「至此**底栏 5 大主 Tab 页面全部实现冻结闭环**」；
- `:32`：「Tab 枚举顺序、路由映射与顶栏齿轮入口**不允许顺手变更**（改动需重新评审）」；
- 底栏之外还牵着 `tabSwipePager`（在 5 个主 Tab 间横滑翻页）与 `bottomBarClearance` 契约。

→ **不做第 6 个底栏 Tab**。三个候选入口，代价各不同：

1. **独立桌面图标**（`<activity-alias>` + `MAIN/LAUNCHER`）：底栏/顶栏/路由映射**一个都不碰**，
   真·零侵入；代价是桌面多一个图标，且它是个二级入口（用户容易找不到）。
2. **首页顶栏新增一个图标**：与 09-22 那轮"首页顶栏入口 → 跨 Activity"完全同类（是**新增**一个入口，
   不是变更那三项）；但首页是 FROZEN（用户点名豁免过一轮），要显式点头。
3. **设置首页加一条**：最不打扰，但图库是高频浏览行为，藏在设置里等于不做（我们对"假开关零容忍"
   的同一条理由也适用于"假入口"）。

页面本体照 2 的走法：`GalleryActivity`（子页再换 Activity 才拿得到系统预测式返回动画 + blur-behind）。

### ⚠️ 2026-09-24 更新：上面三个候选**作废**，底栏已腾出真位

用户改判「把底栏的历史去掉放回二级界面，然后新增一个画廊的页面」，历史已从主 Tab 降回二级页，
评审记录见 `FREEZE-STATEMENT.md` 的「信息架构改判」一节。当前底栏是 4 项
`HOME → FAVORITES → SEARCH → EXPLORE`，**第 3 位（收藏右侧）预留给画廊**，
`VeneraFloatingNavBar.kt` 的 `VeneraNavTab` 注释里钉着插入位。
→ 于是本节 1/2/3 的前提（"要不要碰那三个冻结项"）不复存在：占一个真 Tab 就是本轮的既定走法。

留给实现方重新拍板的两条（原方案没覆盖）：

- **画廊页本体放哪**：底栏 Tab 是壳（MainActivity 的 NavHost）里的目的地之一，而"再开一个 Activity 才有系统预测式返回动画 + blur-behind"这条仍然成立 ——
  两者在这里第一次真正冲突。要么画廊 Tab 只做一块**壳内的入口页**、下钻再换 Activity；要么接受它没有预测返回动画。按 2 的 `GalleryActivity` 走法直接套不上。
- **接上底栏 Tab 会连带牵到的四处**（都是"成为主 Tab"的必然后果，别漏）：
  `routeFor`/`titleFor` 各加分支、`currentTab` 判定（决定底栏与壳顶栏显隐）、
  `tabSwipePager` 的横滑循环会多一格、以及设置「启动页面」下拉要加 `GALLERY` 选项
  （**加了才不是假开关** —— 那一项目前是 4 个主 Tab 选启动页）。

## 四、数据层

图库自己的表（列都是图库语义，不给 comic_* 留位置）：

```
gallery_favorites(
  id INTEGER PK AUTOINCREMENT,
  site TEXT NOT NULL,          -- yandere | konachan，为将来第二站留着，不做抽象层
  post_id INTEGER NOT NULL,
  md5 TEXT NOT NULL,           -- 唯一索引：图站会重传/改文件，去重只认这个
  rating TEXT,                 -- 站方显式分级，直接喂守卫判定
  tags TEXT NOT NULL,          -- 站内是一个空格分隔串、无命名空间（实测）
  preview_url / sample_url / jpeg_url / file_url TEXT,
  width INTEGER, height INTEGER, file_size INTEGER,
  local_path TEXT,             -- 只有用户显式"存原图"才有值
  created_at INTEGER
)
```

DB 归属两选一（见 §七 P3）：进 `VeneraDatabase` 加版本号 + 一个 `if (oldVersion < N)` 迁移块
（现成模式），**或**独立 `gallery.db`（真隔离，不碰漫画库那个曾经 `DROP TABLE` 式的 onUpgrade）。

⚠️ 迁移不许学 v1→v2：那段是 `DROP TABLE comic_history/comic_favorite/comic_source` 再重建
（`VeneraDatabase.kt:131-137`），是历史遗留，不是可推广的升级策略。

## 五、数据源：原生 Kotlin，不走 JS 源脚本

"全部隔离"顺带否掉了"当漫画源接"那条便宜路（它要求把图集伪装成本子，且必须复用漫画侧的整条链）。
图库自己发请求，端点与鉴权情况：

| 端点 | 用途 | 匿名可读？ |
| --- | --- | --- |
| `/post.json?tags=&page=&limit=` | 检索/浏览 | ✅ **实测 200**；`limit` 100/200/**320** 全照数返回（不是 100 封顶） |
| `/post.json?tags=order:score` | **全站人气** | ✅ 翻页到 page=1/2/50/100/**700** 全 200，分数单调下降 → 可当可翻页的流 |
| `/post.json?tags=id:1269437` | 单条 | ✅ 实测正好 1 条 —— **这是唯一的单条取法** |
| `/post/{id}.json` | 单条 | ❌ **404** |
| `/post.json?id=1269437` | 单条 | ⚠️ **200 但 `id` 参数被静默忽略**，返回一整页默认列表。用它取单条会把"看这一张"变成"看一屏无关的图"，且没有任何错误信号 |
| `/post/hot.json?scale=week` | 人气（Danbooru 那套） | ❌ **404** —— 别照着 Danbooru 的端点表抄 |
| `/post/popular_by_day.json?date=` | 日榜 | ✅ 但**固定 24 条** |
| `/post/popular_by_week.json?date=` | 周榜 | ✅ **固定 40 条，`limit=320` 被忽略** → 一屏就到底，不能当可翻页的流 |
| `/post/popular_by_month.json?month=` | 月榜 | ✅ 同样固定 40 条 |
| `/pool.json` | 图集列表 | ✅（字段 `{id,name,post_count,...}`，**不含 post_ids**） |
| `/pool/{id}.json` | 图集成员 | 未测（P2 再说） |
| `/tag.json?name=&limit=` | tag 补全/计数 | 未测（P1 再说） |

**P0 的"待量"已全部量掉**（2026-09-24，node 直连探针）。另有三条一起量了的：

- **没有防盗链**：`assets.yande.re` / `files.yande.re` 的 preview / sample / jpeg 三档，
  带浏览器 UA、不带 UA、带 `Referer: https://yande.re/` 三种取法全部 200 且字节数一致
  → 图片链路**不需要** `onThumbnailLoad` 那套补头。
- **没看到限流**：并发 8 路 1.4 s 全 200；串行 25 次全 200。
  仍复用共享 OkHttpClient，所以 `RateLimitingInterceptor` 的每域名间隔照样生效，不用另做。
- **首屏天然就是成人页**：人气前 120 条实测 `e=84 / q=30 / s=6`。
  所以 §二 说的"只读共享分级模式"不是 P1 的润色，是**第一轮就得做**——
  否则等于在新界面上把应用里现成的「成人内容处理」开关变成摆设。
  落地面：`gallery/domain/GalleryGuard.kt`（判据 `rating != "s"` 即按成人内容处理，
  未知值宁可错打码；用户黑名单排在分级模式之前）。

图片档位规则（实测字节数）：网格吃 `preview_url`（单张约 **20 KB**）、大图页吃 `jpeg_url`
（实测 0.68 / 1.398 MB，3500px 级）、**只有用户点"存原图"才碰 `file_url`**
（实测 4.11 MB / 32 MB 两例）。`sample_url`（约 341 KB）本轮没有消费点，接进模型只为档位齐全。
预算要显式写：图库单独一个 `ImageLoader`（独立 diskCache 目录 + 自己的 memory 预算），
漫画侧那行默认配置一个字不改 —— 已落在 `gallery/data/GalleryImageLoader.kt`（内存 64 MB / 磁盘 512 MB）。

## 六、分期

- **P0（骨架可用）— 2026-09-24 已落地**：端点探针 → 独立 ImageLoader → yande.re 人气瀑布流
  （`preview_url`，触底翻页）+ **三级查看流**：二级 `GalleryPostScreen`（路由，`jpeg_url` + 信息卡 + 标签）
  → 三级 `GalleryFullViewer`（**overlay 不是路由**，`file_url` 原图）+ 分级守卫只读接入。
  三级的形态与档位划分照 `build/pixez/` 里 PixEz 的 `IllustDetailScreen` / `IllustFullScreenViewer`，
  比对记录与"为什么 overlay"写在 `FREEZE-STATEMENT.md` 09-24 那一节。
  **本轮刻意不含收藏**：浏览不写库，所以一张表都没建 —— 建了没人读就是本仓最忌的"实现了但零调用点"。
- **P1（下一步）**：`gallery.db` + 收藏/取消收藏 + 收藏墙（批量移除/长按定位，直接抄插图收藏那三轮的现成做法）
  + tag 抽屉/检索 + 周/月榜档位（实测固定 40 条，做成"一屏就到底"的一块，别按可翻页套）。
- **P2**：pool（图集）浏览 + 下载原图到图库自己的目录 + 按 artist 聚合。

**明确不做**（不变）：下载原图混进漫画的 `DownloadManager`、把图库并进 `favorite_images`、多站抽象层。

## 七、拍板点

- **P1 入口 —— 已定**：底栏**第 4 位**（搜索右侧）。09-23 腾位时按「收藏右侧第 3 位」预留，
  09-24 用户改口「放在搜索右侧第四个导航页」，**以第 4 位为准**；顺序 `HOME → FAVORITES → SEARCH → GALLERY → EXPLORE`。
  评审记录与连带四处（`routeFor`/`titleFor`/`currentTab`/横滑/启动页面下拉）见 `FREEZE-STATEMENT.md` 09-24 那一节。
  **壳内 vs 独立 Activity 这条也定了**：画廊 Tab 与大图页都在 MainActivity 的图上（`GalleryRoute` / `GalleryPostRoute`）——
  主 Tab 本来就必须是壳内目的地，"再换 Activity 才有预测返回"那条只对**从别处跳进来**的页面成立，这里不适用。
- **P2 首站 —— 已定**：**yande.re**（用户点名）。代价实测在 §五：人气前 120 条 `e=84/q=30/s=6`，
  所以守卫不是润色项而是前置项（已随 P0 落）。
- **P3 表放哪 —— 仍未决**（本轮没有表要放）。收藏那轮再定，但方向已明：
  `local_favorite.db` 已经是"第二个独立 SQLiteOpenHelper"的现成先例（`data/db/LocalFavoriteDatabase.kt`），
  走 `gallery.db` 才是真隔离，且完全不碰漫画库那个 `DROP TABLE` 式的历史迁移块。
- **P4 分级与屏蔽 —— 已定并落地**：只读共享一份「分级模式」+ 用户屏蔽规则，判据 `rating != "s"`
  （未知值宁可错打码），用户黑名单排在分级模式之前。单测锁住：`GalleryGuardTest`。
- **P5 ImageLoader —— 已定并落地**：单独一个，独立 `cacheDir/gallery_img` + 64 MB / 512 MB 显式预算；
  组件里挂 `VeneraImageFetcher.Factory` 以带 `ImageFetchTag`（图片流豁免域名熔断）。
  共享侧唯一改动：`VeneraCover` 加可选 `imageLoader` 参数（默认 null = 现行为，全部既有调用点源码不变）。

## 八、明确不做

- 不做多站并存的抽象层（`site` 只当一个列值，等有第二个站再说；现在抽接口就是猜）。
- 不进题材统计、不进漫画历史、不共用 `favorite_images`。
- 不做评论/点赞（图站要登录，且不是"刷图+收藏"这个核心诉求）。
- 不给图库做"阅读器"（图没有页序语义）。
- ~~底栏不加 Tab~~ —— **09-24 作废**：历史降回二级页腾出真位后，画廊就是底栏第 4 位（见 §七 P1）。
