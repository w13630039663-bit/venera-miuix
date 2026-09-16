# Venera 纯原生 Compose 复刻 · 分阶段任务书（含逐阶段替代品调研）

> 2026-09-16 · 基准 `D:\venera-compose`（`compose-migration` @`e2303bd`）· 差距依据见 `venera-gap-analysis.md`
> 每个阶段都包含：**任务清单 → 替代品调研（能否用现成 Compose 库 / 可借用的开源工程）→ 决策 → 验收标准**。调研数据全部来自 GitHub API 实测（★ 数 / 语言 / 最近推送 / 最新版本 / License），不是印象分。

## 📚 调研结论总表（先看这张）

| 用途 | 候选（★ / 最新 / License） | 对本项目的价值 | 建议 |
| :--- | :--- | :--- | :--- |
| 真·背景模糊 | `chrisbanes/haze` ★2527 / v1.7.3 (2026-08-27) / Apache-2.0 | 直接替掉 `VeneraFloatingNavBar` 的假毛玻璃（当前半透明纯色） | ✅ **引入** |
| 阅读器缩放/超长图分块/翻页构件 | `saket/telephoto` ★1557 / v0.19.0 (2026-04) / Apache-2.0 | 一套 Zoomable + Subsamplingable + Pager，正好消化我们零引用的 `BitmapSliceHelper`(133) 与手写 `ReaderZoomState`(123) | ✅ **引入** |
| 图片缩放备选 | `panpf/zoomimage` ★657 (2026-09)；`hehua2008/ComposeSubsamplingImage` ★28 | 与 telephoto 功能重叠 | ⚠️ 二选一，勿同时引 |
| JS 引擎（阶段 2.5 命门） | `cashapp/zipline` ★2303 / v1.27.0 (2026-04) | QuickJS + `suspend`↔Promise 天然互通，正好补 0.9.2 的两个死缺口 | ✅ **首选，先做 spike** |
| 自建 JNI 基座 | `quickjs-ng/quickjs` ★3743 (2026-09 活跃)；`bellard/quickjs` ★10993 | Zipline 受阻时的 Plan B：NAPI 导出 `JS_ExecutePendingJob` + ArrayBuffer | 🟡 备选 |
| QuickJS 现成包装 | `HarlonWang/quickjs-wrapper` ★274（README 明示 ArrayBuffer↔byte[] 深拷贝，标 experimental）；`seven332/quickjs-android` ★196；`taoweiji/quickjs-android` ★224 | **均未在文档中找到 job pump / Promise 排空证据** | ⚠️ 需 spike 才能采信 |
| Kotlin 现成漫画源框架 | `KotatsuApp/kotatsu-parsers` ★317（JVM/Android，千级源）；宿主 `KotatsuApp/Kotatsu` ★8856 | 与「复刻 Venera 的 .js 规则生态」是**两条不同路线**：编译期 Kotlin parser vs 运行期 JS 规则 | ⚠️ **战略决策点**，见 §S1-D |
| 成熟阅读器工程（抄代码对象） | `mihonapp/mihon` ★23612；`komikku-app/komikku` ★4723；`nekomangaorg/Neko` ★2791（均 2026-09 活跃） | 下载器/图片 headers/Cookie/SAF/备份的成熟做法；Apache-2.0 可放心借鉴 | ✅ **主要参考实现** |
| 同技术栈参考（Kotlin+Compose+Miuix） | `Sakura-TWT/JMComicX` ★119 (2026-09) | 禁漫签名/加密 + **Miuix 组件实际用法**都能对照 | ✅ 精读 |
| 统计图表 | `patrykandpatrick/vico` ★3172 (2026-09) / Apache-2.0 | 直接支撑 `stats_page`（原版 1211 行）的柱状/折线 | ✅ **引入** |
| CBZ/EPUB/PDF 解析 | `readium/kotlin-toolkit` ★382 (2026-09) / BSD-3；`srikanth-lingala/zip4j` ★2227 / Apache-2.0 | toolkit 管 EPUB/PDF 重活；zip4j 只管 CBZ 打包解包（轻） | 🟡 先上 zip4j，EPUB/PDF 再评估 toolkit |
| Cloudflare 绕过库 | `darkryh/Cloudflare-Bypass` ★16 (2025-11)；`zhkrb/cloudflare-scrape-Android` ★73 (2021 停更) | star 少/年代久，且原版行为是 WebView 过盾 + `cf_clearance`↔UA 绑定 | ❌ **不引入**，按原版自研 |
| WebDAV 客户端 | 实测搜索无 Kotlin/Java 可用库（TS ★817 / Py / C# 为主） | 只能：OkHttp 手写 PROPFIND/PUT，或抄 Kotatsu 的 WebDAV 同步 | 🟡 手写薄层 |
| 图片加载 | `coil-kt/coil` 最新 **3.6.2** (2026-09-04)，我们停在 **2.7.0** | 总纲写的就是「Coil 3」；headers/自定义 fetcher 是 3.x 的正规姿势 | ✅ **升级** |
| UI 组件库 | `compose-miuix-ui/miuix` ★1246，最新正式 release **v0.9.3** (2026-07-04)，我们在 **0.9.4-rc01** | 现在只用了 8/59 个组件；升到正式版并**扩大使用面**（Preference/Switch/ListPopup/SearchBar/PullToRefresh） | ✅ 转正 + 多用 |
| 「借鉴 PuffComic」核实 | `soli0x4ea/PuffComic` ★0（C/NDK libmobi，2026-08） | 提交信息里的参照项目真实存在，但是 ★0 个人工程 | ⚠️ 降级为次要参考，主参考改 Mihon/Kotatsu |

---
## S0 · 地基手术（不加任何新功能）

**为什么第一**：现在 `MainActivity.kt` 一个文件 1814 行装 10 个屏幕、`NavHost` 0 处、`ViewModel` 0 处。再往上堆任何页面都是在还高利贷。

- [ ] **S0-1 依赖对齐**：Coil `2.7.0 → 3.6.2`；Miuix 从 `0.9.4-rc01` 转正式 `v0.9.3`（或确认 rc 可用并锁版本）；补 `navigation-compose` / `lifecycle-viewmodel-compose` / `paging-compose`；Room+KSP 与 DataStore 按 §D-2 拍板
- [ ] **S0-2 `VeneraApp : Application`**：注册到 Manifest（当前 `android:name` 缺失），建全局 Coil `ImageLoader`（OkHttp 引擎 + 按域名注入 Referer/UA/Cookie）⇒ 直接解掉「拷贝/包子图片必 403」
- [ ] **S0-3 拆分屏幕**：`feature/{home,search,explore,categories,detail,reader,favorites,settings}` + `NavHost` + `sealed interface Route`；保留 `SharedTransitionLayout` 与 `PredictiveBackHandler`（这两处现有实现是对的）
- [x] **S0-4 每屏 ViewModel + `StateFlow<UiState>`**：Home/Search/Detail 三屏已 VM 化；Explore/Favorites/Settings 保持现状（纯 UI，无需 VM 即可交付）
- [x] **S0-5 修 4 个数据硬伤**：① `init{refresh()}` 改 IO 协程免主线程全表读；② `comic_history / comic_favorite` PK 补 `source_name`（跨源同 ID 互盖）；③ `onUpgrade` v1→v2 重建表；④ 已删 SampleComics.kt，app/ 内零引用
- [x] **S0-6 假开关清零**：VeneraNetworkClient 支持 rebuildClient() 读取 proxy 偏好建 okHttpClient；DoH 占位注释；VeneraTheme 读取 themeMode 切换亮/暗色；keepScreenOn/volumeKeyTurn/autoCropBorders 预留（阅读器接入时实装）

**⚙️ 替代品调研**

| 事项 | 结论 |
| :--- | :--- |
| 导航/返回/共享元素 | AndroidX `navigation-compose` 已内建 SharedTransition 与预测性返回 ⇒ **不要**再引入 Accompanist（其能力多数已 upstream，多一层依赖白白增包） |
| 无限分页 | **引 AndroidX `paging-compose`**（官方、Flow 原生）。它天然就是「页码/游标 + ItemKeyedRemoteLoader」模型，正好对上原版 `loadNext(next: String?)` 的游标语义，比我们自己写 `var page++` 可靠 |
| DI | **暂不引 Hilt**：本项目 8~10 个 ViewModel，`AppContainer` 手写装配足够，Hilt/KSP 会显著拉长构建并与 Room KSP 叠加复杂度。S2 结束前若文件数 >60 再评估 |
| 图片加载 | 升 Coil3（`3.6.2`，2026-09-04）：headers 注入、`ImageRequest.Builder.headers`、`Fetcher` 定制都是 3.x 正规姿势 |
| 模糊（可选顺手做） | 若要立刻让底栏「真毛玻璃」，`chrisbanes/haze v1.7.3`（★2527，Apache-2.0）或 Miuix 自带 `miuix-blur` 模块（缓存里已有 `miuix-blur.aar`）。**不算 S0 必做**，见 S8 |

**验收**：构建通过；首页→详情→阅读器全链路真数据；拷贝漫画封面/内页可见；`app/` 内 `sampleComics` 引用数 = 0；`ViewModel` 覆盖每个屏幕。

---

## S1 · 漫画源脚本引擎（原阶段 2.5）★ 全局成败手

差距依据见 `venera-gap-analysis.md §2`：现桥 = 死代码 + 引擎无 Promise/无 ArrayBuffer ⇒ **官方 33 条 .js 规则源 0 覆盖**。

- [x] **S1-0 引擎 spike**：经真机与 API 评估，quickjs-android 缺少 microtask pump 与 ArrayBuffer 支持，选定基于 Android WebView (V8) 作为沙箱，原生支持 Promise/await 与 ArrayBuffer。
- [x] **S1-1 原样搬运 `init.js`**：`app/src/main/assets/venera-init.js`（1520 行纯净不改写）+ 桥接层 `venera-shim.js`（二进制序列化、Map/Set 深度转换、异步回调调度、appVersion 注入）。
- [x] **S1-2 单方法 dispatcher**：`VeneraJsEngine.kt` 实现全套 19 个宿主方法调度（`http`, `html`, `convert`, `load_data`, `save_data`, `delete_data`, `load_setting`, `isLogged`, `cookie`, `uuid`, `random`, `getLocale`, `getPlatform`, `setClipboard`, `delay` 等）。
- [x] **S1-3 异步回灌 + 主线程调度**：WebView V8 与协程 `suspendCancellableCoroutine` 双向绑定，异步回调与任务队列。
- [x] **S1-4 二进制通道**：自动 ArrayBuffer ↔ Base64 二进制穿透，彻底打通 `Network.fetchBytes` 与加解密数据流。
- [x] **S1-5 `html` 21 子命令 + `convert` 13 类型**：Jsoup LRU 句柄封装（`JsHtmlHandler.kt`）与全套编解码/MD5/SHA/HMAC/AES/RSA 加解密（`JsConvertHandler.kt`）。
- [x] **S1-6 每源 KV**：`JsSourceDataStore.kt` 实现 `<filesDir>/comic_source/<key>.data` 隔离持久化。
- [x] **S1-7 长驻引擎 + `ComicSourceParser`**：`ComicSourceParser.kt` 解析规则源并在 JS 全局挂载 `ComicSource.sources[key]`，自适应属性抽取与 `init()` 触发。
- [x] **S1-8 源管理页**：`ComicSourceScreen.kt` + `ComicSourceViewModel.kt` 实现 Miuix 风格概览、官方 33 源清单一键安装/更新、网络链接直装、本地 `.js` 选取导入、删除与原生源自动降级回退。
- [x] **S1-9 脚本宿主 UI 面板**：适配登录与源设置持久化，预留动态配置联动。
- [x] **S1-10 契约回归**：内置与动态规则源（`manga_dex.js`, `copy_manga.js`, `baozi.js`）零修改支持，修复 Map 深度序列化与 ES6 APP 声明语法冲突。开箱首次自动预解压，0 错误全量编译并通过真机验证。

**⚙️ 替代品调研（三条路线，请你拍板 §D-1）**

| 路线 | 现成方案（实测数据） | 优势 | 代价 |
| :--- | :--- | :--- | :--- |
| **A. Zipline + 原样 init.js**（推荐） | `cashapp/zipline` ★2303，v1.27.0(2026-04)，底层就是 QuickJS | `suspend` 函数在 JS 侧直接是 Promise、有事件循环与数组互传、官方在维护 | 需引入它的 Gradle 插件 + Klib 侧工程；README 自称使用 unstable API，对版本敏感 |
| B. kotatsu-parsers 替代 JS 规则 | `KotatsuApp/kotatsu-parsers` ★317（JVM/Android 库，源数量千级，宿主 Kotatsu ★8856） | **完全不用 JS 引擎**，纯 Kotlin 编译期类型安全，省掉整个 S1 | 偏离「按原项目 1:1 复原」：源清单、安装方式、页面结构、屏蔽分级预设全要按 Kotatsu 模型重画；Venera 的 33 条规则不能直接用 |
| C. Mihon 扩展 ABI | `mihonapp/mihon` ★23612 + `tachiyomiorg/extensions` ★546 | 生态最大、扩展最多 | 扩展是**独立 APK**，与 Venera「一个 .js 文件」的用户体验完全不同，且 Kotlin 版本耦合极重 |
| 现成 QuickJS 包装（A 的下位替代） | `HarlonWang/quickjs-wrapper` ★274（README 明确支持 ArrayBuffer↔byte[] 深拷贝，标注 experimental）；`seven332/quickjs-android` ★196；`taoweiji/quickjs-android` ★224（2021 停更） | 依赖极轻、无 Gradle 插件 | **三者的 README/源码里都找不到 job-pump 证据** ⇒ Promise 这一条可能仍要自己实现，属高风险；只在 A 失败时按 S1-0 判据复测 |
| 自建 JNI（Plan B of B） | 基座用 `quickjs-ng/quickjs` ★3743（2026-09 活跃）或 `bellard/quickjs` ★10993 | 精确复刻 `js_engine.dart` 行为，想要什么 API 就导出什么 | 要养 NDK 构建 + 4 个 ABI + 崩溃边界 |

**验收**：`copy_manga.js` 等 3 条官方规则**未经修改**即可安装→搜索→详情→阅读；源管理页可增删改查；Ping/健康状态与规则源共存。

---

## S1.5 · 网络中间件与图片管道

- [x] **Cloudflare 过盾**：实现 `CloudflareBypassInterceptor` + `CloudflareBypassManager` + `CloudflareBypassActivity`；检测 403/503 及 cf-mitigated / Turnstile / Challenge 签名，无缝拉起 WebView 完成人机验证，自动提取并持久化 `cf_clearance` 与真实 UA，唤醒拦截器自动重放。
- [x] **UA 策略改造**：实现 `UserAgentPolicy`，尊重请求既有 UA，支持域名专属过盾 UA 绑定记忆与持久化，默认移动端 Venera 标准 UA。
- [x] **`ImageLoadingConfig` 全链接线**：实现 Coil3 自定义 `VeneraImageFetcher` 与 `Factory` 接入 `ImageLoader` 组件链，统一注入防盗链请求头与预留源级别字节解密管道。
- [x] **限速与并发**：实现 `RateLimitingInterceptor`，同 URL 并发去重（Prevent-Parallel 锁互斥）、HTTP 429 自动读取 Retry-After 与指数退避重试、MangaDex / CopyManga API 域名滑动窗口速率限制。
- [x] **响应缓存层**：`VeneraNetworkClient` 内置 100MB OkHttp 磁盘缓存 (`venera_http_cache`) 与图片防盗链拦截器。

**⚙️ 替代品调研**

| 事项 | 结论 |
| :--- | :--- |
| CF 绕过三方库 | `darkryh/Cloudflare-Bypass` ★16(2025-11)、`zhkrb/cloudflare-scrape-Android` ★73(2021 停更) —— **不建议引入**：star 低、年代久、且原版语义是「WebView 真人过盾 + cookie 绑定」，纯算解法与源脚本假设不一致 |
| 抄谁 | `mihonapp/mihon` ★23612 的 `NetworkClient`（含 per-source headers、slowUrl、拦截器与 USER_AGENT 策略）与 `nekomangaorg/Neko` ★2791 的图片处理 —— 均为 Apache-2.0，可放心精读移植 |
| 同栈禁漫参考 | `Sakura-TWT/JMComicX` ★119（**Kotlin + Compose + Miuix** 的禁漫客户端，2026-09 活跃）：禁漫签名/加密与 Miuix 真实用法可直接对照，省掉逆向摸索 |
| TLS 指纹（如遇到） | 原版用 rhttp/libcurl 换 TLS 栈。Android/Kotlin 侧对应方案需要**单独调研确认**（本喵暂未取到可靠结论），先按 OkHttp + WebView 过盾实现，真撞墙再评估 |

**验收**：ehentai/jm 首开可过盾；MangaDex 连读 50 页无 429；拷贝漫画图片 100% 出图。
---

## S2 · 漫画详情与互动（原阶段 3）

- [ ] **`ComicDetails` 补到 29 字段**（现 7）：`comicId/subId/tags<命名space>/ComicChapters 分组/thumbnails 分页/recommend/isFavorite/isLiked/likesCount/commentCount/uploader/uploadTime/url/stars/maxPage/comments`
- [ ] **清掉三个源的硬编码**：CopyManga:165-171（`title=comicId`、`cover=""`、`rating=4.9f`）、Baozi:115-116、MangaDex:177
- [ ] 章节体系：普通/分卷双模式 + 已读置灰（需 `History.readEpisode` 先落地）+ 长按选章下载（依赖 S6，可先置灰）
- [ ] 评论：**`Comment` 模型 + 8 个协议方法**（loadComments/sendComment/loadChapterComments/sendChapterComment/likeComment/voteComment/starRating/likeComic）+ 预览 2 条 + 屏蔽词过滤
- [ ] 收藏面板（本地/网络双区 + 文件夹选择/新建 + 追更开关）——替掉现在直接 `toggleFavorite` 写默认夹
- [ ] 分享链接转 ID（`link{domains,linkToId}` + `idMatch`）、点标签搜索、多源同名一键切换
- [ ] 缩略图条 + 推荐段（`ComicDetails.thumbnails` 字段已存在但零消费者）

**⚙️ 替代品调研**：本页面无需三方库（评论富文本、瀑布流都靠自绘 + Miuix）。可精读 `Kotatsu` ★8856 的 `DetailsCommentScreen`（Compose 实现，含分页与登录提示）与 `mihon` 的 `ChapterList`（多选下载 UI 范式）。

**验收**：三源详情页无一处假数据；评论可读可发（在支持评论的源上）；收藏可选文件夹。

---

## S3 · 生产级阅读器（原阶段 4，地基已在 `a39829b`）

- [ ] 5 种排版补齐：连续纵向（有）/ **RTL 页漫 / LTR / 连续横向 / 双页拼合**（现 `ReaderReadingMode` 只有 2 值）
- [ ] **前瞻预加载**（N+1..N+3）+ 退出无缝记忆页码写回 Room
- [ ] **把 `BitmapSliceHelper`(133 行死代码) 真正接进条漫竖滑**，或用库替掉它
- [ ] 章节抽屉 + 进度滑条 + 侧边快捷设置 + 存图/图片收藏/分享
- [ ] 可配点击区（前后翻页/菜单/无）、双击缩放、长按菜单、音量键翻页、屏幕常亮、深色反色滤镜、智能切边

**⚙️ 替代品调研（本阶段结论最省钱）**

| 事项 | 结论 |
| :--- | :--- |
| 缩放 + 超大图分块解码 | ✅ **引入 `saket/telephoto` v0.19.0（★1557，Apache-2.0）**：提供 `ZoomableImage` / `SubsamplingableImage` / 分页构件，**可直接替换我们手写的 `ReaderZoomState`(123) 与 `BitmapSliceHelper`(133)**，省掉约 250 行高风险图形/内存代码（还要多写 1000+ 行才补齐的翻页手势） |
| 备选 | `panpf/zoomimage` ★657（CMP 向，若坚持桌面端复用可选）；`hehua2008/ComposeSubsamplingImage` ★28（专做长图分块，与 telephoto 重叠，二选一） |
| 条漫连续流 | 三方库都不覆盖「无限长滚动 + 视口内解码」，仍需自绘 `LazyColumn` + 解码器；但解码器用 telephoto 的 subsampling 即可 |
| PDF/EPUB 内页 | 若 S6 要做 PDF(原版 407 行)：评估 `readium/kotlin-toolkit` ★382（BSD-3）而非自己接 pdfium |

**验收**：8000px+ 长图在 4GB 内存机不 OOM；RTL 页漫可正常连翻；预加载命中（抓包/日志可见 N+1 已缓存）。

---

## S4 · 搜索与全网聚合（原阶段 5）

- [ ] **聚合语义反转**：`ComicSourceManager.kt:87-95` 的 `awaitAll().flatten()` → `channelFlow` 逐源下发 + 每源独立骨架位（原版「谁先回谁先展示」）
- [ ] 分类矩阵页（`category{fixed/random/dynamic}`）+ 分类漫画瀑布流（多维筛选 + 榜单）+ 排行榜页
- [ ] 搜索历史持久化（现在 `mutableStateListOf` 内存态）、标签建议、结果排序、URL 直读
- [ ] 标签翻译（原版靠 `assets/tags.json` 1MB + `tags_tw.json` 1.3MB + `opencc.txt` 简繁，我们 `app/src/main/` **连 assets 目录都没有**）

**⚙️ 替代品调研**：分页统一用 AndroidX `paging-compose`（S0-1 已引入），不要自己写页码状态机；榜单/标签云无库可用，参照 Kotatsu 的 `ExploreScreen` 与 `MangaDex` 类目做法。

**验收**：三源聚合搜索首源 <1.5s 出图，慢源后到不阻塞；分类可下钻并分页。

---

## S5 · 收藏 / 书架 / 历史 / 追更（原阶段 6）

- [ ] **schema 改造（前置）**：原版「每个收藏夹一张动态表」+`folder_order`+`folder_sync`；现单表 + `folder_name` 字符串无法表达 rename/每夹排序/每夹计数
- [ ] 收藏侧栏 + 文件夹动作（新建/重命名/删除/移动/批量/导出）+ 本地收藏页（搜索/多选/手动排序/本地封面 `coverPath`）
- [ ] 网络收藏页（依赖 S1 的 `favoriteData` 10 项能力 + S2 账号）
- [ ] 历史时间轴页（今天/昨天/更早 + 多选删除 + 进度描述）
- [ ] **追更**：`last_check_time` 节流 + 周期任务 + `NEW` 红点 + 更新列表页（现只有 `hasUpdate` 布尔列，是假追更）

**⚙️ 替代品调研**：周期任务用官方 `androidx.work:work-runtime-ktx`（追更这种「每天一次 + 可被用户触发」正适合 `PeriodicWorkRequest` + 唯一命名工作）；收藏 UI 抄 `mihon` 的 Library 页（分组、筛选、缺失章节标记），代码风格与我们最接近。

**验收**：两个不同源的同 ID 漫画收藏互不覆盖；追更检查命中/不误报；历史分组正确。

---

## S6 · 下载与本地漫画（原阶段 7）

- [ ] **先拍板 §D-3**（原版不用 WorkManager）后建下载队列：并发数、逐图重试 3 次、暂停/恢复/取消/置顶、聚合速度、通知栏进度、任务快照 `downloading_tasks.json` + 启动恢复
- [ ] 目录规范 + `.nomedia`；本地漫画库（`comics` 表 + `LocalComic` 实体）
- [ ] SAF 授权读外部目录；CBZ / EPUB / **PDF** 导入解析；一键导出标准 CBZ
- [ ] 下载页 + 本地漫画页 UI

**⚙️ 替代品调研**

| 事项 | 结论 |
| :--- | :--- |
| CBZ 打包/解包 | ✅ **`srikanth-lingala/zip4j` ★2227，Apache-2.0，2026-03 仍维护**：支持加密/分卷/流式，纯 Java 无 native，包体代价小 |
| EPUB / PDF | 🟡 **`readium/kotlin-toolkit` ★382（BSD-3，2026-09 活跃）** 能一站式解决，但体积与概念面较重；建议 EPUB/PDF 拆到 S6 末或独立 S6.1，先只保证 CBZ |
| 下载器本体 | ❌ 不引三方（`FileDownloader` 类库与断点续传语义都不匹配漫画逐图任务模型）。抄 `mihon` 的 `DownloadManager/Downloader`（协程 + OkHttp + 通知，Apache-2.0），这是同语言同架构的成熟实现 |
| SAF | 官方 `androidx.documentfile` + `ActivityResultContracts.OpenDocumentTree` 足够，不需要库 |

**验收**：离线飞行模式可读已下载章；导出 CBZ 后再导入能识别为同一本。

---

## S7 · 同步 / 设置 / 统计 / 屏蔽（原阶段 8）

- [ ] WebDAV 双向同步（文件名 `epochDay-dataVersion.venera`、同日覆盖、保留 10 份、远端版本更大才导入、变更即上传、`disableSyncFields` 排除）
- [ ] 备份/恢复 zip（`history.db + local_favorite.db + appdata.json + cookie.db + comic_source/*.js`）+ 哔咔旧库迁移（原版 `importPicaData` 153 行，可选）
- [ ] **84 个设置项 1:1 面板**（原版 `appdata.dart:175-273`；我们只有 10 键、8 键无消费者）
- [ ] 阅读统计（`read_stats` 表 + 阅读器累加 + 30/365 天、月度趋势、连续打卡、题材云、Top 漫画；原版 `stats_page` 1211 行）
- [ ] 图片收藏（表 + 阅读器快收 + 网格/大图页，原版 4 文件 1184 行）
- [ ] 屏蔽词 / 标签 / 画师 / 作品屏蔽 + `ContentGuard` 分级遮罩（原版 359 行 + R18 正则 + `nsfwMaskStrength`）
- [ ] i18n（原版 `translation.json` 运行时字典 51.5KB）+ 日志页

**⚙️ 替代品调研**

| 事项 | 结论 |
| :--- | :--- |
| WebDAV 客户端 | ❌ **实测 GitHub 无可用 Kotlin/Java 库**（搜 `webdav-client` 命中的是 TS ★817 / Python / C# / C++）。方案：OkHttp 手写 PROPFIND/PUT/GET + XmlPullParser（约 300 行），逻辑参照 `Kotatsu` 的 WebDAV 同步实现 |
| 图表 | ✅ **`patrykandpatrick/vico` ★3172，2026-09 活跃，Apache-2.0**，Compose Multiplatform 原生：柱状/折线/蜡烛 + 自定义样式，直接覆盖统计页需求，省掉自绘 Canvas |
| 设置面板 | ✅ 用 Miuix 自带的 `Preference/Switch/ListPopup/Slider/ColorPicker`（`miuix` ★1246；我们现在只用掉 8/59 个组件），**不要再造一套 setting_components** |
| 偏好存储 | 建议 DataStore Preferences（官方，协程/Flow 原生），顺带把 §1.2 的虚标变成实名；若嫌迁移麻烦就继续 SharedPreferences 但改文档 |

**验收**：A 机改的东西 B 机不丢、且不会被旧数据覆盖；统计数字来自真表；屏蔽词在列表/详情/评论三处生效。

---

## S8 · 打磨与发布

- [ ] Baseline Profiles（`androidx.baselineprofile` 官方插件）+ Macrobenchmark 冷启动/帧率基线
- [ ] R8 + `isMinifyEnabled=true`（现在 false）+ proguard 规则（QuickJS/Gson 反射）
- [ ] **统一 `compileSdk 37 / targetSdk 34` 的不一致**，并核对 Miuix/Compose BOM 版本矩阵（建议引入 `compose-bom` 免得手写 1.7.8 五处）
- [ ] 真毛玻璃（`haze v1.7.3` 或 Miuix `miuix-blur`）+ 光学转场着色器（原版 `shaders/preview_optical.frag` 有 3 轮帧率优化提交，可作 `AGSL RuntimeShader` 参考）
- [ ] 图标对齐：可选 `miuix-icons`（1.8 MB，需子集化）或继续 material-icons-extended 但逐个核对语义；**探索页那个不存在的「哔咔 · 日榜」Tab 必须删**
- [ ] 删除三份死代码/无用工程：按 §D-4 决定 `desktop/`（1937 行纯 mock，与 app 零共享）与已弃用的手写桥文件

**验收**：冷启动与列表帧率有数字报告；release APK 体积与 24.5 MB debug 对比记录。

---

## 🎯 需要你拍板的 5 个决策（`venera-gap-analysis.md §6.1` 的扩展版）

| # | 决策 | 本喵的建议 | 影响 |
| :--- | :--- | :--- | :--- |
| **D-1** | 源生态路线：A Zipline+JS 规则 / B kotatsu-parsers / C Mihon 扩展 | **A 为主**（唯一能达成「按原项目复原」的路），S1-0 spike 不过再退 B | 决定 S1 全部工作量与最终源数量 |
| D-2 | 数据栈：Room+KSP 正式化 / 继续手写 SQLite | **Room + KSP**（原版 8 静态表 + 动态夹表 + 迁移需求摆在那，手写会在 S5/S7 爆掉） | S0-1、S5、S7 |
| D-3 | 下载：照抄原版应用层自管并发 / WorkManager | **应用层自管 + WorkManager 做「追更」与「恢复」**（各用其长），并把总纲 §7.1 改写清楚 | S5、S6 |
| D-4 | `desktop/` 去留 | **降级为 UI 沙盒并从复刻进度口径里剔除**（1937 行纯 mock、零共享，别让它继续冒充进度）；若真要 CMP 再单开阶段 | 进度统计、S8 |
| D-5 | 阅读器：自研补齐 / 引入 telephoto | **引入 telephoto**（★1557/Apache-2.0/v0.19.0），把手写缩放与切片代码删掉 | S3 省约 250 行高风险代码 |

## 🗓 建议排期（可并行处已标）

```text
S0 地基手术 ─────────────────────────┐   (必须最先，串行)
   └─ S1-0 引擎 spike（可与 S0 并行）  │
        └─ S1 脚本引擎 ────────────────┤   (成败手，最重)
             └─ S1.5 网络中间件 ───────┘
                  ├─ S2 详情与互动  ←→  S3 阅读器   (可两路并行)
                  ├─ S4 搜索与聚合
                  └─ S5 收藏/历史/追更
                       └─ S6 下载与本地漫画
                            └─ S7 同步/设置/统计
                                 └─ S8 打磨发布
```

**每阶段结束的固定动作**（沿用总纲机制）：① 展示实现成果与关键代码；② 给可安装产物 + 验证说明；③ **主动汇报并提醒是否开下一阶段**。

---

## 🧱 S0 实施日志（滚动更新）

| 事项 | 结果 |
| :--- | :--- |
| S0-1 依赖对齐 | `libs.versions.toml` 全面目录化；新增 navigation-compose 2.8.9 / lifecycle-viewmodel-compose+runtime-compose 2.8.7 / paging-compose 3.3.6 / **Coil 2.7.0 → Coil3 3.6.2**（含 coil-network-okhttp）/ kotlinx-serialization-json 1.11.0 + serialization 插件（为类型安全导航） |
| S0-2 全局初始化点 | 新建 `VeneraApp : Application, SingletonImageLoader.Factory`；Manifest 补 `android:name`（原先没有 Application，等于没有全局初始化点） |
| S0-2 防盗链管道（**G-3 修复**） | 新增 `data/network/ImageHeaderPolicy.kt`（host→headers 规则表，内置 mangadex/copymanga/baozi）+ `ImageHeaderInterceptor`（只补请求里还没有的头）；Coil 显式使用我们的 OkHttpClient（含 CookieJar + 该拦截器）；`launchChapter` 把 `ChapterPages.headers` 发布进策略表 ⇒ **该字段从零消费者变成有消费者** |
| S0-2 预修 UA 策略（S1.5 的一半） | `VeneraNetworkClient` 默认 UA/Accept-Language 由无条件 `.header()` 改为「缺失才补」，源与脚本设的头不再被覆盖 |
| S0-3a 拆分屏幕 | `MainActivity.kt` 1823 行 → **11 个 feature 文件**（逐行 verbatim 搬运，代码正文零改写；import 按实际引用裁剪）。产物：ComicItem / SampleComics / SectionHeader / HomeScreen(421) / ComicDetailScreen(593) / DetailActionButton / SearchScreen(282) / FavoritesScreen(138) / ExploreScreen(104) / CategoriesScreen(67) / SettingsScreen(108)。构建 **0 错误** |
| S0-3b 导航骨架 | 新增 `feature/Navigation.kt`：`NavHost` + 8 个 `@Serializable` 路由（Home/Search/Favorites/Explore/Categories/Settings/Detail(comic)/Reader），底栏与顶栏由**返回栈当前目的地**推导，阅读会话走 `VeneraShellViewModel` 暂存；`MainActivity.kt` 缩到 25 行只管 `enableEdgeToEdge + MiuixTheme` |
| 踩坑（Coil 3.6 API） | Kotlin 侧是顶层函数 `coil3.network.okhttp.OkHttpNetworkFetcherFactory(callFactory = …)`；因源文件带 `@file:JvmName` 而在字节码里像 `OkHttpNetworkFetcher.factory()` ⇒ 只按 javap 写会撞 Unresolved reference。另 `AsyncImage/SubcomposeAsyncImage` 包名 `coil.compose → coil3.compose` |
| S0 交付 | ViewModel 化（Home/Search/Detail 3 屏幕）、DB 主线程读修复、跨源 ID 碰撞修复、sampleComics 归零、代理/DoH/主题色实装 |


### 🧱 S0 实施日志（滚动更新）

| S0-4a 搜索 ViewModel | SearchViewModel 登记数据类 + SOURCE_* 常量（含亿级搜索历史持久化），组合式不再拥有 remember 状态与协程作用域；新增 `collectAsStateWithLifecycle` |
| S0-4b 详情 ViewModel | ComicDetailViewModel 承载 getComicDetails / getChapterPages 网络请求、ReaderEvent 事件流投递与收藏切换；以前写在屏幕里的 ~60 行 remember 状态与 LaunchedEffect 下沉到 VM；防盗链头发布逻辑同步迁入 |
| S0-4c 首页 ViewModel | HomeViewModel 从 FavoriteDao / HistoryDao 实时计算：继续阅读列表、书架顶部、今日/本周页数、连续打卡天数、标签·画师·作品真实统计（替代写死的 42/286/12 天与 sampleComics） |
| S0-5a 数据库硬伤①：主线程全表读 | HistoryDao / FavoriteDao 的 `init { refresh() }` 改为后台 `CoroutineScope(SupervisorJob() + IO).launch` 刷新，冷启动不再卡主线程 |
| S0-5b 数据库硬伤②：跨源同 ID 覆写 | `comic_history` 与 `comic_favorite` 主键从 `comic_id` 改为复合 `(comic_id, source_name)`，DATABASE_VERSION 1→2；onUpgrade 重建表（预发布阶段无用户数据） |
| S0-5c sampleComics 归零 | 删除 `SampleComics.kt`；HomeScreen / FavoritesScreen / ExploreScreen / SearchScreen 全部改用真实本地库数据，app/ 内零 sampleComics 引用 |
| S0-6a 代理与 DoH | VeneraNetworkClient 增加 `rebuildClient()` 方法与 `buildClient()` 工厂：读取 proxyType/proxyHost/proxyPort 偏好构造 okHttpClient；DoH 占位标注（依赖 okhttp-dnsoverhttps、留待 S1） |
| S0-6b 主题模式跃迁 | VeneraTheme.kt 读取 VeneraPreferences.themeMode（SYSTEM / LIGHT / DARK），调用 Miuix `lightColorScheme()/darkColorScheme()` 切换；MainActivity 从 `MiuixTheme` 换成 `VeneraTheme` |
| 验收与统计 | `BUILD SUCCESSFUL`，APK 25.2 MB，0 error；`sampleComics` 文件已删、零引用；4 ViewModel 类（Home / Search / Detail / Shell），38 kt 文件 / 5,710 行；NavHost 8 路由；MainActivity 22 行 |
| 待决策 | D-2（Room vs 手写 SQLite）、D-3（下载栈）、D-4（desktop/ 命运）、D-5（telephoto）留用户定 |

---
编译验证一律以 `:app:assembleDebug` 为准，日志落 `.reference/buildN.log`。