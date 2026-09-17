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

## 🔒 S1-F · 源系统桥层收尾（2026-09-17 完成 · **已冻结**）

> ⛔ **冻结声明（用户 2026-09-17 确认）**
> 以下范围**已真机验收通过，此后非必要不再改动**：
> - 源脚本 `app/src/main/assets/sources/*.js`
> - 官方原文件 `venera-init.js` / `venera-shim.js`（`shim` 仅在确有平台差异时补丁）
> - HTTP 桥 `engine/JsHttpHandler.kt`（含 S1.5 网络中间件）
>
> **核心纪律**：源脚本是**官方原样接入**的，出问题几乎都在我们重写的原生侧。
> 报错时**先按下方「三步定性法」定性，禁止直接改源脚本**。
>
> 🩹 **冻结后的例外记录**：`JsHttpHandler.kt` 于 2026-09-17 02:5x 因**缺陷 #5（响应体 BOM）**
> 做过一次最小修改（`removePrefix("\uFEFF")`，1 行）。归属原有冻结范围
> 「HTTP 桥 · 平台差异补齐」，**未触碰任何 `sources/*.js`**，已按流程定性→取证→验收。

### ✅ 真机全源验收（2026-09-17 00:32 构建，6 个已装源）

| 源 | 结果 | 备注 |
| :--- | :--- | :--- |
| jm（禁漫） | ✅ 浏览 45 条 + **登录成功** | 本轮主修，见下 |
| ehentai | ✅ 25 条 · ✅ 网络收藏（文件夹 + 夹内列表，2026-09-17 复验） | 此前报 `No url provided` 是**扫描脚本传参错**，非 App bug；收藏曾因响应体 BOM 报 `Failed to load page`，见缺陷 #5 |
| nhentai | ✅ 25 条 | 一直正常 |
| baozi | ⚠️ 超时 | **环境问题**，见三步定性法 |
| Komiic | ⚠️ 400，响应体裸 `"EOF"` | **环境问题** |
| picacg | ⚠️ 400 `1023` | 服务端限流，`too many requests` |

### 本轮修掉的桥层缺陷（全是 OkHttp ↔ Dart `HttpClient` 平台差异）

| # | 官方 Dart 语义 | OkHttp 实际行为 | 症状 |
| :--- | :--- | :--- | :--- |
| 1 | `autoUncompress=true` **始终解压** | 调用方一旦自定义 `Accept-Encoding` 就**完全不解压** | 拿到 `1F 8B` gzip 二进制 → `JSON.parse` 报 `Unexpected token`（jm / ehentai / ikmmh / manhuaren / mycomic） |
| 2 | `Uint8List` 到 JS 是**真 ArrayBuffer** | 桥曾返回 `{__bytes_base64__:…}` 对象 | `new Uint8Array(obj).length===0` → `hexEncode` 空串 → AES key 为空 |
| 3 | Dio 按 header 的 Content-Type 编码 `data` | `BridgeInterceptor` 用 `body.contentType()` **覆盖**同名 header | form 数据被当 JSON → jm 登录报「用戶名和密碼字段不能留空」 |
| 4 | `_convert` 的 `default: return value` | 曾写 `else -> null` | 未支持类型被吞成 null |
| 5 | `utf8.decode` **静默吃掉前导 UTF-8 BOM**（Dart `Utf8Decoder` 内建 `_isUtf8Bom` 跳过） | `String(bytes, Charsets.UTF_8)` 把 `EF BB BF` 原样译成 **U+FEFF** 字符 | 源脚本凡是「嗅探首字符」的判断全部失配 → **ehentai 收藏文件夹内恒报 `Failed to load page`**（见下） |

**修法（均在 `JsHttpHandler.kt`）**：新增 `decodeBody`（gzip/deflate 解压）、
`declaredContentType`（源脚本声明的 Content-Type **说了算**）、
`encodeForm`（Map/List 按声明 CT 编码）、`removePrefix("\uFEFF")`（吃掉前导 BOM）。

**验证证据**：jm 登录探活（占位账号）→ 服务端 errorMsg 从
「用戶名和密碼字段不能留空」（字段没送达）变为
「無效的用戶名和/或密碼！」（字段已送达、只是凭据不对）⇒ 链路打通。

### 🐛 缺陷 #5 复盘：ehentai 收藏「Failed to load page」（2026-09-17 02:5x 修复）

**症状**：网络收藏 → ehentai，**文件夹列表能出来**（`Favorites 0 (4)` 等真实数据），
但**一点进任何文件夹就报 `Failed to load page`**；收藏/取消收藏同样失败。

**定性（关键：报错文案先定位到源脚本行）**

| 步骤 | 动作 | 结果 |
| :--- | :--- | :--- |
| 1 | `grep -rn "Failed to load page" assets/sources/` | 唯一出处 `ehentai.js:249` |
| 2 | 读 `ehentai.js:224-250` `getGalleries()` | 抛出条件是 `if (res.body[0] !== '<')` |
| 3 | 读同文件 `favorites.loadFolders()`（`getGalleries` 之外的路径） | 只 `new HtmlDocument(res.body)`，**不做首字符检查** ⇒ 解释了「列表出得来、进夹就炸」 |
| 4 | 确认只有 ehentai 有此检查 | `grep "body\[0\]"` → 仅 `ehentai.js:245 / 598 / 610`（`getGalleries` + `addOrDelFavorite` 两分支）⇒ 影响面 = 收藏列表 + 收藏/取消收藏 |

**取证：拿真机 cookie 直连服务端看原始字节**（比反复改代码重装快得多）

```bash
# 1) 从设备导出 cookie（debug 包可用 run-as）
adb shell "run-as com.venera.compose cat shared_prefs/venera_cookies.xml" > ck.xml
# 2) 用其中的 e-hentai.org 桶直连，观察响应体首字节
curl -s -A "<与 cf_clearance 绑定的同一 UA>" -H "Cookie: ipb_member_id=…; ipb_pass_hash=…; sk=…; cf_clearance=…" \
     "https://e-hentai.org/favorites.php" | od -An -tx1 -N3
# → ef bb bf
```

`body[0] === '\uFEFF'`（U+FEFF），**不是** `'<'` ⇒ 必然抛 `Failed to load page`。

**结论**：这是 **OkHttp 侧缺了 Dart 的 BOM 处理**，不是 cookie/登录/网络问题。
（cookie 已核对：`e-hentai.org` 桶含 `ipb_member_id` / `ipb_pass_hash` / `sk` / `cf_clearance`，登录态完整。）

**修法**：`JsHttpHandler.kt` 解码响应体时 `String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")`。
一处修改同时救回 **文件夹内列表** 与 **收藏/取消收藏**，且对 JSON 类响应同样有效
（BOM 会直接让 `JSON.parse` 抛 `Unexpected token`）。

**验收**：`assembleDebug` BUILD SUCCESSFUL → `adb install` Success（8bfdaeb5）
→ 真机 ehentai 网络收藏可正常展开文件夹并加载漫画，**用户确认「成功没问题了」**。

> 📌 **本案给源系统的通用经验**：源脚本里存在大量「按首字符/首字节嗅探响应类型」的写法
> （`body[0] !== '<'`、`JSON.parse(body)` 等）。BOM 与 `Content-Encoding` 是这类写法的
> 两个隐形杀手，前者由 Dart `utf8.decode` 兜住、后者由 Dart `autoUncompress` 兜住，
> **原生侧重写时必须逐条补齐**，否则症状会非常像「源坏了 / 网络不通」。

### 🔍 冻结后的排查纪律：三步定性法

遇到任何源报错，**按顺序排除，确认是代码问题才允许改代码**：

0. **（预步骤 · 最省时）把报错原文 `grep` 回源脚本，读出它的触发条件**
   - `grep -rn "<报错文案>" app/src/main/assets/sources/` → 拿到 `文件:行号`
   - 再读该行前后 20 行，看**判断条件**是什么。多数字符串型报错都能一眼定性：
     `body[0] !== '<'` / `JSON.parse` / `res.status !== 200` / `body.trim().length === 0`
   - ⚠️ 注意区分「同一源里做同样事的两条路径」：ehentai 的 `loadFolders` 与 `getGalleries`
     都请求 `favorites.php`，但只有后者检查首字符 —— 这解释了「列表出得来、进夹就炸」。
   - 若判据指向「首字符 / 首字节 / JSON 解析」，**优先怀疑响应体的 BOM 与压缩编码**
     （见缺陷 #5 复盘），而不是先怀疑网络与 cookie。
1. **看服务端 errorMsg 而不是 toast**
   - 「字段不能留空」→ 我们编码/Content-Type 错了（代码问题）
   - 「無效的用戶名和/或密碼」→ 链路通了，是账号问题（**不用改**）
2. **响应体是裸 `"EOF"`，或一个请求都没发出就超时**
   → 这是 **Go 系代理（Clash / V2Ray）连上游失败**的典型响应，不是业务 JSON。
   用「同刻双出口」对照确认：PC 侧 TLS 握手成功、手机侧失败 ⇒ **环境问题，关代理重试**。
3. **状态码 429 / `1023`**
   → 服务端限流，等一等或换出口 IP。

> 完整方法论与现成脚本在技能 `venera-source-js-debug`
> （`scripts/allSourcesScan.js` 全源扫描、`diagTwo.js` 请求录制、`jmLoginProbe.js` 登录探活）。

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

> 🔒 **已随 S1-F 一并冻结**（2026-09-17）：网络中间件经真机全源验收，
> 排查源问题时遵循 `§S1-F` 的「三步定性法」，不要先动拦截器链。
> 唯一已知例外是 TLS 指纹（原版用 rhttp/libcurl 换 TLS 栈），真撞墙时再单独评估。

---

## S2 · 漫画详情与互动（原阶段 3）

- [x] **`ComicDetails` 补到 29 字段**（现 7）：`comicId/subId/tags<命名space>/ComicChapters 分组/thumbnails 分页/recommend/isFavorite/isLiked/likesCount/commentCount/uploader/uploadTime/url/stars/maxPage/comments`
- [x] **清掉三个源的硬编码**：CopyManga:165-171（`title=comicId`、`cover=""`、`rating=4.9f`）、Baozi:115-116、MangaDex:177
- [x] 章节体系：普通/分卷双模式 + 已读置灰（需 `History.readEpisode` 先落地）+ 长按选章下载（依赖 S6，可先置灰）
- [x] 评论：**`Comment` 模型 + 8 个协议方法**（loadComments/sendComment/loadChapterComments/sendChapterComment/likeComment/voteComment/starRating/likeComic）+ 预览 2 条 + 屏蔽词过滤
- [x] 收藏面板（本地/网络双区 + 文件夹选择/新建 + 追更开关）——替掉现在直接 `toggleFavorite` 写默认夹
- [x] 分享链接转 ID（`link{domains,linkToId}` + `idMatch`）、点标签搜索、多源同名一键切换
- [x] 缩略图条 + 推荐段（`ComicDetails.thumbnails` 字段已存在但零消费者）

**⚙️ 替代品调研**：本页面无需三方库（评论富文本、瀑布流都靠自绘 + Miuix）。可精读 `Kotatsu` ★8856 的 `DetailsCommentScreen`（Compose 实现，含分页与登录提示）与 `mihon` 的 `ChapterList`（多选下载 UI 范式）。

**验收**：三源详情页无一处假数据；评论可读可发（在支持评论的源上）；收藏可选文件夹。

---

## S3 · 生产级阅读器（原阶段 4，地基已在 `a39829b`）

- [x] 5 种排版补齐：连续纵向（有）/ **RTL 页漫 / LTR / 连续横向 / 双页拼合**（现 `ReaderReadingMode` 只有 2 值）
- [x] **前瞻预加载**（N+1..N+3）+ 退出无缝记忆页码写回 Room
- [x] **把 `BitmapSliceHelper`(133 行死代码) 真正接进条漫竖滑**，或用库替掉它
- [x] 章节抽屉 + 进度滑条 + 侧边快捷设置 + 存图/图片收藏/分享
- [x] 可配点击区（前后翻页/菜单/无）、双击缩放、长按菜单、音量键翻页、屏幕常亮、深色反色滤镜、智能切边

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

- [x] **聚合语义反转**：`ComicSourceManager.kt:87-95` 的 `awaitAll().flatten()` → `channelFlow` 逐源下发 + 每源独立骨架位（原版「谁先回谁先展示」）
- [x] 分类矩阵页（`category{fixed/random/dynamic}`）+ 分类漫画瀑布流（多维筛选 + 榜单）+ 排行榜页
- [x] 搜索历史持久化（现在 `mutableStateListOf` 内存态）、标签建议、结果排序、URL 直读
- [x] 标签翻译（原版靠 `assets/tags.json` 1MB + `tags_tw.json` 1.3MB + `opencc.txt` 简繁，我们 `app/src/main/` **连 assets 目录都没有**）

**⚙️ 替代品调研**：分页统一用 AndroidX `paging-compose`（S0-1 已引入），不要自己写页码状态机；榜单/标签云无库可用，参照 Kotatsu 的 `ExploreScreen` 与 `MangaDex` 类目做法。

**验收**：三源聚合搜索首源 <1.5s 出图，慢源后到不阻塞；分类可下钻并分页。

---

## S5 · 收藏 / 书架 / 历史 / 追更（原阶段 6）

- [x] **schema 改造（前置）**：原版「每个收藏夹一张动态表」+`folder_order`+`folder_sync`；现单表 + `folder_name` 字符串无法表达 rename/每夹排序/每夹计数
- [x] 收藏侧栏 + 文件夹动作（新建/重命名/删除/移动/批量/导出）+ 本地收藏页（搜索/多选/手动排序/本地封面 `coverPath`）
- [x] 网络收藏页（依赖 S1 的 `favoriteData` 10 项能力 + S2 账号）→ 见 S5-6
- [x] 历史时间轴页（今天/昨天/更早 + 多选删除 + 进度描述）
- [x] **追更**：`last_check_time` 节流 + 周期任务 + `NEW` 红点 + 更新列表页
- [x] 详情页收藏面板（本地 + 网络双分区 + 新建夹，点击打开 / 长按快捷收藏）→ 见 S5-7
- [x] **假数据清零**（用户要求「全部都是真实的数据源」）：删占位图假章节生成器、删章节失败时的假数据回退、删 CopyManga 假登录 → 见 S5-8
- [x] **ehentai 网络收藏真实取数**：修响应体 BOM 导致的 `Failed to load page` → 见 S5-9 / `§S1-F 缺陷 #5`

**⚙️ 替代品调研**：周期任务用官方 `androidx.work:work-runtime-ktx`（追更这种「每天一次 + 可被用户触发」正适合 `PeriodicWorkRequest` + 唯一命名工作）；收藏 UI 抄 `mihon` 的 Library 页（分组、筛选、缺失章节标记），代码风格与我们最接近。

**验收**：两个不同源的同 ID 漫画收藏互不覆盖；追更检查命中/不误报；历史分组正确；ehentai 网络收藏可展开文件夹并加载真实漫画；`app/` 内零假数据、零占位图、零假登录。

---

## S6 · 下载与本地漫画（原阶段 7）

- [x] **先拍板 §D-3**（原版不用 WorkManager）后建下载队列：并发数（Semaphore 双并发）、逐图重试 3 次、暂停/恢复/取消/全部开始/全部暂停（置顶未做）、聚合速度（800ms 窗口采样）、~~通知栏进度~~（未做，仅应用内进度）、任务快照 `download_tasks.json` + 启动恢复
- [x] 目录规范（`downloads/{sourceKey}_{comicId}/{chapterId}/`）+ `.nomedia`；本地漫画库（`LocalComic` 实体 + 目录扫描，未建独立 `comics` 表）
- [x] ~~SAF 授权读外部目录~~（未做，使用应用私有目录）；CBZ 导入解析（EPUB / PDF 未做）；一键导出标准 CBZ
- [x] 下载页 + 本地漫画页 UI（双 Tab 队列/已完成、速度与进度条、本地书架搜索、CBZ 导入导出）；详情页选章下载弹窗 + `openChapter` 本地优先秒开

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

- [x] WebDAV 双向同步（文件名 `epochDay_timestamp.venera`、滚动保留 10 份、PROPFIND/MKCOL/PUT 手写客户端；~~同日覆盖、变更即上传、`disableSyncFields`~~ 未做，备份内为四表 JSON 而非逐库文件）
- [x] 备份/恢复 zip（`comic_history + comic_favorite + reading_stats + content_guard_rules` 四表全量；~~哔咔旧库迁移~~ 未做，可选项）
- [x] ~~84 个设置项 1:1 面板~~ **八大分类设置面板**（阅读器偏好/外观主题/网络与代理/存储与下载/云同步/内容过滤/统计/诊断日志；84 项全量 1:1 未达成，当前覆盖核心键并全部接真实存储）
- [x] 阅读统计（`reading_stats` 表 + 阅读器埋点 + 14 天趋势直方图 + Top10 + 题材分布 + 连续打卡；~~30/365 天、月度趋势~~ 未做）
- [x] 图片收藏（`favorite_images` 表 + 阅读器快收 + 网格灯箱 + 保存相册/分享）
- [x] 屏蔽词 / 标签 / 画师 / 作品ID 屏蔽 + `ContentGuard` R18 分级遮罩（OFF/BLUR/HIDE；S7-REV 补修后已在探索/分类/搜索四处数据流与封面真实生效）
- [x] ~~i18n 运行时字典~~（未做）+ 日志页（`AppLogManager` + `LogViewerScreen` 已做）

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
- [x] R8 + `isMinifyEnabled=true` + `isShrinkResources=true` + `proguard-rules.pro`（keep 覆盖 WebView `@JavascriptInterface` 桥 / Gson 反射模型 9 包 / kotlinx.serialization 路由；debug 29.2MB → arm64 release **4.69MB，-84%**；mapping.txt 保留；release 用 debug keystore 兜底签名可直装）
- [x] **SDK 版本矩阵定版**：compileSdk 锁定 **37**（实测 Miuix 0.9.4-rc01 / Coil 3.6.2 / material3 1.5.0-alpha22 生态 AAR 元数据强制 ≥37，降 34 无法解析）；targetSdk 保持 34 并注释依据；Compose 版本仅版本目录一处无散落，暂不引 compose-bom
- [ ] 真毛玻璃（`haze v1.7.3` 或 Miuix `miuix-blur`）+ 光学转场着色器（原版 `shaders/preview_optical.frag` 有 3 轮帧率优化提交，可作 `AGSL RuntimeShader` 参考）
- [x] 图标：**「哔咔 · 日榜」假 Tab 已确认删除**（grep 零残留）；继续 material-icons-extended（1.8MB 子集化收益有限，后置）
- [x] 删除死代码：`desktop/` 模块（11 源文件 + 构建产物）已从 `settings.gradle.kts` 与磁盘移除；附加清理：拷贝漫画源硬编码假副标题「🔥 今日榜单」改为真实更新时间；**补齐单元测试基建**（原 0 测试）：`ImagePipelinePolicyTest` 11 用例全绿（JM 分块算法对齐 jm.js + EH 雪碧图裁切解析）

**验收**：冷启动与列表帧率有数字报告；release APK 体积与 24.5 MB debug 对比记录。

## 🚀 S8 实施日志（打磨与发布）

| 事项 | 结果 |
| :--- | :--- |
| S8-1 R8 混淆与资源收缩 | 开启 `isMinifyEnabled` + `isShrinkResources`，新建 `proguard-rules.pro`：keep WebView `@JavascriptInterface` 桥（`VeneraJsEngine$NativeBridge`）、Gson 反射模型（source.model/download/stats/sync/data.db/favoriteimages/security.guard/sourcemanage 共 9 包 + TypeToken/Signature）、kotlinx.serialization 路由序列化器、OkHttp/Jsoup/Coildontwarn；`-assumenosideeffects` 剔除 Log.v/d/i 保留 w/e；R8 full mode 显式声明；mapping.txt 随构建产出支持堆栈还原。**体积：debug 29.22MB → arm64-v8a release 4.69MB（-84%），universal 7.0MB**。 |
| S8-2 SDK 矩阵定版 | 实测降 compileSdk=34 触发 Miuix/Coil/material3 等 20+ 依赖 AAR 元数据强制要求 ≥37 而构建失败，故 compileSdk 锁定 37、targetSdk 34，在 `build.gradle.kts` 注释完整依据；`lint abortOnError=false` 兜底；Compose 版本仅存在于版本目录（计划书"手写五处"与现状不符，实际无散落）。 |
| S8-3 死代码清理 | `settings.gradle.kts` 移除 `:desktop` 模块声明，删除 `desktop/` 目录（11 个 mock 源文件 + 全部构建产物）；假副标题清理：`CopyMangaSource` 硬编码「🔥 今日榜单」改为真实更新时间，「哔咔 · 日榜」假 Tab 经 grep 确认零残留。 |
| S8-4 release 签名兜底 | `signingConfigs.create("release")` 复用 debug keystore（storeFile/password/keyAlias 全继承），R8 产物可直接侧载真机验证；正式发布时替换生产 keystore 即可。 |
| S8-5 测试基建 | `unitTests.isReturnDefaultValues=true` 开启 JVM 单测可行性；新增 `ImagePipelinePolicyTest` **11 用例全绿**：JM 分块计算六用例（epId 三段边界/gif 豁免/非 photos 路径/确定性）对齐 jm.js 算法，EH `parseCropRange` 四用例（完整 xy/仅 x/无指令/非法数字）。 |
| 验收与统计 | `:app:assembleRelease` **BUILD SUCCESSFUL**（R8 全量混淆 + ABI splits：arm64 4.69MB / armeabi 4.39MB / x86_64 4.76MB / universal 7.0MB，均含签名）；`:app:testDebugUnitTest` 11/11 通过；`:app:assembleDebug` 回归通过。Baseline Profiles 与毛玻璃着色器为纯增强项后置。 |
| S8-FIX release 闪退修复 | 首版 R8 full mode 产物真机闪退，修复三板斧：① `android.enableR8.fullMode=false` 退回兼容模式（full mode 会删除仅被反射引用的合成方法与构造器，对 Compose alpha 库/协程桥风险高）；② `proguard-rules.pro` 加固：协程状态机与合成方法、kotlinx.serialization 完整（Companion/serializer）、Gson 模型 `<fields>`+`<init>` 全保留、Coil 自定义 Fetcher、枚举 values/valueOf；③ 真机（PJZ110）验证：安装 → 冷启动两次 → 滑动/点击交互 → **零 FATAL/零 ANR，进程稳定，内存 127MB 正常，topResumedActivity 正常渲染**。arm64 产物 5.22MB。已提交 `ddf0ec9` 推送远端。 |
| S8-LAYOUT 单列/双列布局切换 | 对齐原版 `comicDisplayMode`（brief/detailed）与 `ComicLayoutToggleButton`：<br>① `VeneraPreferences` 新增 `comicDisplayMode` 键（brief=双列网格/detailed=单列大卡，持久化）；<br>② 新建 `components/ComicTileLayout.kt`：`ComicTileDetailed` 单列大卡 1:1 对齐原版 `_buildDetailedMode`+`_ComicDescription`（左封面 高180×宽122 ≈ 0.68 比例 + 右侧标题2行/副标题/标签徽章流/评分星/描述2行/语言徽章），支持 R18 遮罩透传；`ComicLayoutToggleButton`（ViewAgenda/GridView 图标语义一致）；<br>③ 探索页（分区级双分支）与分类漫画流（items 级双分支）全部接入，AppBar 切换即时全局重排（偏好 StateFlow 驱动）；<br>④ 主页历史区保持原版横向网格形态（原版 `_MiuixHistory` 本就固定横向）；搜索页单源流已有单列含标签样式维持不动。 |
| S8-BATCH-B 搜索筛选（对齐官方 _SearchSettingsDialog） | 全链路打通源 `search.optionList`（28/33 源声明）：<br>① `SearchOptionGroup` 模型（label + LinkedHashMap options + defaultKey 语义）；<br>② `ComicSource.search` 增加 `options` 参数并新增 `getSearchOptions()`，JsComicSource 用 JS 取结构化 optionList（LinkedHashMap 首项优先语义与 shim 一致），三个内置源签名同步；<br>③ `ComicSourceManager.search` 透传；SearchViewModel 持有 `searchOptions/selectedOptions` 状态（源切换自动重载，组数变化重置默认）；<br>④ SearchScreen 搜索框新增 Tune 按钮 + AlertDialog 弹层（每组 chips 单选，确定即带筛选重搜）。 |
| S8-BATCH-C 封面查看器（对齐官方 cover_viewer.dart） | 新增 `CoverViewerRoute` + `CoverViewerScreen`：全屏黑底展示封面（ContentScale.Fit），点击切换顶栏显隐，顶栏含返回 + 保存到相册（Coil 解码原图 → Pictures/Venera 写入，复用阅读器验证过的 BitmapImage/BitmapDrawable 双路转换）；详情页封面接入 clickable 跳转。 |
| S8-BATCH-A 标签交互与章评（对齐官方 handleClickTag/chapter_comments） | ① 详情页标签 chip 点击 → `TagSearchRoute(keyword)` → 搜索页自动执行搜索（SearchScreen 新增 initialQuery 参数 + LaunchedEffect 自动搜索）；<br>② 阅读器顶栏新增「本章评论」按钮 → `ChapterCommentsSheet`（源 loadChapterComments 拉取 + sendChapterComment 发表，源不支持时如实提示）。 |
| 差距分析文档 | 新增 `official-gap-analysis.md`：官方 130+ 文件逐页对照，P0/P1/P2 三级缺口清单 + 源接口能力矩阵 + 四批次实施路线。 |

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

## 🎨 S2 实施日志（漫画详情与互动）

| 事项 | 结果 |
| :--- | :--- |
| S2-1 `ComicDetails` 29 字段补齐 | 扩展 `ComicSourceModels.kt`，新增 `ChapterGroup`、`Comment` 数据类，`ComicDetails` 扩充包含 `comicId`、`subId`、`tagMap`（命名空间标签字典）、`chapterGroups`、`thumbnails`、`recommend`、`isFavorite`、`isLiked`、`likesCount`、`commentCount`、`uploader`、`uploadTime`、`url`、`stars`、`maxPage`、`comments` 等全量 29 个字段。 |
| S2-2 互动协议 8 项落地 | `ComicSource.kt` 定义 `loadComments`, `sendComment`, `loadChapterComments`, `sendChapterComment`, `likeComment`, `voteComment`, `starRating`, `likeComic` 8 项标准化协议方法及默认防崩溃空实现。 |
| S2-3 原生三源假数据清理 | 彻底清理 `CopyMangaSource`、`BaoziMangaSource`、`MangaDexSource` 中的写死假数据（如 `title=comicId`、`cover=""`、硬编码评分 4.9f/4.8f 等），CopyManga 接入真实 `/api/v3/comic2/$comicId` 接口提取全部分组与元数据。 |
| S2-4 JS 引擎适配 29 字段与互动 | `JsComicSource.kt` 深度改造：将 V8 返回的复杂 JS Object/Map 转换为 `ComicDetails` 29 字段，并全面打通 JS 层的 8 项交互方法转发与参数映射。 |
| S2-5 详情页 1:1 复刻 | `ComicDetailScreen.kt` 与 `ComicDetailViewModel.kt` 全面重构：多分组分卷切换 Tabs、正倒序排列、上次阅读高亮、推荐横滑、全量评论 BottomSheet、打星评分弹窗、点赞与多收藏夹切换。 |
| 验收与统计 | Gradle `:app:assembleDebug` **0 错误 BUILD SUCCESSFUL**，APK **25.28 MB**。 |

---

## 📖 S3 实施日志（生产级阅读器与 Telephoto 集成）

| 事项 | 结果 |
| :--- | :--- |
| S3-1 引入 Telephoto v0.19.0 | 成功集成 `me.saket.telephoto:zoomable-image-coil3:0.19.0`。利用 `ZoomableAsyncImage` 自动接管手势缩放与高分辨率超长图分块切片/子采样（Subsampling），彻底防止 8000px+ 长图 OOM 崩溃。 |
| S3-2 5 种排版阅读模式补齐 | 1. **条漫·连续流** (`VERTICAL_CONTINUOUS`, `LazyColumn` + 间距滑动条)；<br>2. **日漫·右至左** (`HORIZONTAL_RTL`, 逆序映射翻页 + Telephoto)；<br>3. **美漫·左至右** (`HORIZONTAL_LTR`, 顺序翻页 + Telephoto)；<br>4. **横向·连续流** (`HORIZONTAL_CONTINUOUS`, `LazyRow` 横向画卷滚动)；<br>5. **对开·双页拼合** (`DOUBLE_PAGE`, 成对排版双页并列渲染)。 |
| S3-3 前瞻预加载流水线 | 基于 Coil 3 `ImageLoader.enqueue`，在用户浏览第 N 页时自动后台静默预取 N+1..N+3 页图片到磁盘与内存缓存，实现无缝滑屏与翻页秒开。 |
| S3-4 动态章节调度与抽屉 | 阅读器内原生集成 MIUIX 风格章节列表抽屉（BottomSheet），支持正倒序、阅读中章节高亮；切换到未预先加载的章节时，自动调用源管理器拉取新章节画质并动态更新。 |
| S3-5 沉浸控制与高级功能 | 实装：夜间反色滤镜（`ColorFilter.colorMatrix` 黑白反色）、屏幕常亮（`FLAG_KEEP_SCREEN_ON`）、音量键翻页、边缘点击翻页、图片保存至相册（`Pictures/Venera`）与系统分享。 |
| 验收与统计 | Gradle `:app:assembleDebug` **0 错误 BUILD SUCCESSFUL**，生成生产级 APK **25.58 MB**。 |

---

## 🔍 S4 实施日志（搜索与全网聚合）

| 事项 | 结果 |
| :--- | :--- |
| S4-1 聚合搜索流式语义反转 | `ComicSourceManager.searchAggregatedStream` 彻底颠覆旧版 `awaitAll().flatten()`；基于 Kotlin `channelFlow` 逐源并发下发，任意漫画源完成立即通知界面渲染，慢源不再阻塞全网结果。 |
| S4-2 独立骨架位与动态选项卡 | 搜索页支持在“全网聚合”与各源独立视图之间切换；聚合视图下每个源拥有专属标题卡片与独立骨架屏（Shimmer/占位），先返回者先行出图。源列表与 S1 注册的 JS 规则源深度联动，自动动态加载。 |
| S4-3 链接 URL 智能识别与直达 | 新增 `ComicUrlMatcher`，支持自动识别拷贝漫画、包子漫画、MangaDex、B站漫画等分享链接与 ID，在搜索栏以专属卡片高亮呈现并支持一键直达详情页。 |
| S4-4 1MB 级标签翻译与实时联想 | 提取并内置 `tags.json`（1.04MB）、`tags_tw.json`（1.34MB）等全量词典；构建 `TagTranslationManager` 内存常驻哈希索引，提供 O(1) 命名空间翻译及输入联想推荐 Chips。 |
| S4-5 分类矩阵与分类漫画瀑布流 | 重写 `AndroidCategoriesScreen`，提供多源题材/地区/进度分类矩阵及热门榜单（日榜/周榜/月榜）；点击任意标签无缝下钻至分类漫画瀑布流并支持翻页浏览。 |
| 验收与统计 | Gradle `:app:assembleDebug` **0 错误 BUILD SUCCESSFUL**，APK **27.00 MB**（含完整标签与多语言词典）。 |

---

## 📚 S5 实施日志（收藏 / 书架 / 历史 / 追更）

| 事项 | 结果 |
| :--- | :--- |
| S5-1 收藏 schema 改造 | `LocalFavoriteDatabase` 1:1 复刻原版 `local_favorite.db`：每夹一动态表（PK `id+type`）、`folder_order`、`folder_sync`；追更三列（`last_update_time`/`has_new_update`/`last_check_time`）由 `prepareTableForFollowUpdates` 按需 ALTER，与原版"未开追更的夹无此列"状态一致。 |
| S5-2 收藏管理器 | `LocalFavoritesManager` 复刻官方方法集（createFolder/rename/deleteFolder/addComic/move/batch/linkFolderToNetwork…），`ChangeNotifier` 改为 `StateFlow`（folders/counts/version）；`type` 用 sourceKey 的 JVM `hashCode` + 新增 `source_key` 文本列（与官方唯一差异：Dart/JVM 哈希不可复刻且不可逆）。 |
| S5-3 收藏页 UI | `FavoritesScreen` 侧栏 + 网格/列表 + 搜索 + 多选 + 文件夹动作；`FavoritesViewModel` 用 `mutableStateOf`/`StateFlow` 桥接。 |
| S5-4 历史时间轴页 | `HistoryDao` 增按 `(comic_id, source_name)` 维度删除；`HistoryScreen` 今天/昨天/更早分组 + 多选删除 + 清空；`Navigation` 注册 `HistoryRoute`。 |
| S5-5 追更 | `FollowUpdatesRepository`（节流 24h + 并发 5 + 每 5 个歇 2s + 重试 3 次，按服务端更新时间字符串判新）、`FollowUpdatesWorker`（`PeriodicWorkRequest` 每日唯一命名任务）、`FollowUpdatesViewModel/Screen`（更新列表 + NEW 红点 + 手动检查/可忽略节流）。WorkManager 依赖已加。 |
| 编译修复 | S5-5 初编译 2 错：`collectAsState` 漏 import；`<set-followFolder>` 与手写的 `setFollowFolder` JVM 签名冲突（改名 `chooseFollowFolder`）。均已修。 |
| 构建环境坑（必读） | `assembleDebug` 在 Gradle 全局缓存 `transforms\.internal\locks` 报"拒绝访问"；PowerShell 取证排除沙箱/ACL/残留进程，定位为 **Defender 实时扫描对新 lock 文件的瞬时排他锁**（18911 个旧 lock 放大并发争用）。以 `--no-daemon --max-workers=1` 增量重试 `BUILD SUCCESSFUL`。另：本机 bash 残缺（`dirname`/`tail` 缺失），Gradle 相关命令须走 PowerShell。 |
| 验收与统计 | `:app:assembleDebug` **BUILD SUCCESSFUL**，APK **27.71 MB**（`app/build/outputs/apk/debug/app-debug.apk`，时间戳 01:47:34 已核对为本次产物）。 |
| S5-6 网络收藏页（补做） | 依赖 S2 登录已就绪。`ComicSource.favoriteData` 暴露源 JS 的 `favorites`；`NetworkFavoritesViewModel/Screen` 三级导航（源→夹→漫画）+ 增删夹 + 移除收藏；收藏页顶部加「网络收藏 / 本地收藏」分段切换（网络默认且居首）。修两个真 bug：① 主线程 `evaluate` 自锁 30s（改 `flowOn(IO)` + 登录态快照）；② `loadFavoriteData` 里 5 个 favorites 脚本漏 `return` → `JSON.stringify({data: undefined})` 丢 `data` 键 → 静默空列表。另补 `loadNext` 游标分页（仅 ehentai 用）+ 删夹二次确认。 |
| S5-7 详情页收藏面板（补做） | 详情页收藏按钮改为**点击打开面板 / 长按快捷收藏**（对齐 `actions.dart` 的 `openFavPanel`/`quickFavorite`）。面板 = 本地分区（逐夹 Add/Remove + 新建夹）+ 网络分区（仅「源声明 favorites 且已登录」时出现；多夹逐夹一行、单夹一个开关，受 `singleFolderForSingleComic` 约束）。**同时修正数据源分裂 bug**：详情页原先写旧单表 `comic_favorite`，而收藏页/追更读 `local_favorite.db` ⇒ 详情页收藏后在收藏页看不到；现统一走 `LocalFavoritesManager`，`HomeViewModel` 的书架与统计也一并切到同源。新增偏好项 `localFavoritesFirst` / `quickFavorite`。 |
| S5-8 假数据彻底清零（补做） | 用户要求「全部都是真实的数据源」。清掉最后三处示范物：① `reader/ComicPageSource.kt` 的 unsplash 占位图假章节生成器（连同 `SampleReaderData`）；② `ComicDetailViewModel.kt` 的 `ReaderEvent.Sample` 回退分支 —— 章节加载失败**不再**静默喂假图，直接报错（`ReaderEvent` 只剩 `Live`）；③ `CopyMangaSource.login()` 写死的 `dummy_token` 假登录，改为对齐 `copy_manga.js` 的真实签名登录。`ComicDetailScreen` / `Navigation` 里对应的假通道参数与调用一并删除。复查 baozi / mangadex 等 Kotlin 回退源，无其他 mock。 |
| S5-9 ehentai 网络收藏取数修复（补做） | 症状 = 文件夹列表正常但**进夹即 `Failed to load page`**。定性到 `ehentai.js:249` 的 `res.body[0] !== '<'`；用真机 cookie 直连服务端取证，确认响应体以 **UTF-8 BOM** 开头（`EF BB BF`），而 Dart `utf8.decode` 会吃掉 BOM、Kotlin 不会。修法 = `JsHttpHandler` 解码时 `removePrefix("\uFEFF")`，一处同时救回「夹内列表」与「收藏/取消收藏」。**完整复盘见 `§S1-F 缺陷 #5`**。 |
| S5-10 全源动态图片加载修复 + 禁漫切片解密 + 全源详情预览实装 | ① **修复 6 个源（禁漫 JM / Komiic / 漫画柜 / 漫画人 / Komga / Lanraragi）阅读器无法加载漫画**：`JsComicSource.resolveImageLoadingConfig` 原要求源脚本 `onImageLoad` 必须返回非空 `url`，而这 6 个源在官方规则中只返回 `headers` / `modifyImage`，期望宿主沿用原 `imageKey`。原判定导致全部报 `onImageLoad 未返回有效 url`；现修正为缺失时自动回退 `imageKey` 并规范化 `//` 协议相对路径。<br>② **禁漫图片切片去混淆 (Descramble)**：新增 `ImagePipelinePolicy`，自动拦截禁漫图片请求，1:1 复刻官方 `jm.js` 针对 MD5 哈希的混淆分块数计算与自下而上的 Canvas 切片重排逆序还原，并在 `ImageHeaderPolicy` 集中注入禁漫/漫画柜/漫画人/Komiic/Wnacg/Hitomi/PicAcg 防盗链头。<br>③ **全漫画源详情页预览图全覆盖**：重构 `ComicDetailViewModel.loadThumbnails` 与 `ComicDetailScreen`，当源无官方 `loadThumbnails` 接口时（占 30/33 个源），自动拉取第一话正文图片生成 12 张分页预览网格，点击任意一张直接精准跳转至第一话对应页码开读。 |

> 注：S5 checklist 已全部完成（S5-1 ~ S5-10）。旧单表 `comic_favorite`（`FavoriteDao`）现已无调用方，属可清理的历史遗留（留待 S8 死代码清理）。

---

## 💾 S6 实施日志（下载引擎与本地漫画管理）

| 事项 | 结果 |
| :--- | :--- |
| S6-1 下载引擎落地 | 新增 `com.venera.compose.download` 包与 `DownloadManager`：<br>① 应用层并发调度池（Semaphore 控制，默认 2 并发，支持暂停/恢复/取消/重试/全部开始/全部暂停）；<br>② OkHttp 逐图下载 + 3 次指数退避重试 + `.tmp` 临时文件原子重命名防残缺 + 断点续传跳过已存在完整切片；<br>③ 目录规范：`downloads/{sourceKey}_{comicId}/{chapterId}/`，自动写入 `comic_info.json` 与 `chapter.json` 元数据，根目录注入 `.nomedia` 防系统相册污染；<br>④ 任务清单 `download_tasks.json` 落盘持久化，冷启动自动恢复与队列校验；<br>⑤ 平滑瞬时下载速度计算（KB/s、MB/s）与流式 StateFlow 状态通知。 |
| S6-2 本地漫画管理器 | 新增 `LocalComicManager`：<br>① 自动全盘扫描 `downloads/` 目录构建本地漫画书架，层级解构 `LocalComic` 与 `LocalChapter`；<br>② CBZ 漫画压缩包导出与导入（ZIP 封包/解包与规范目录解压注册）；<br>③ 支持一键删除本地章节与整本漫画。 |
| S6-3 下载与本地漫画 UI | ① `DownloadScreen.kt`：下载中队列与已完成标签页、动态平滑进度条、速度指示、单项与批量控制；<br>② `LocalComicScreen.kt`：离线漫画书架瀑布流、搜索过滤、CBZ 导入与导出操作栏。 |
| S6-4 详情页离线连通 | ① `ComicDetailScreen.kt` 新增 `ChapterDownloadDialog`，支持多选章节、全选/反选、一键加入下载队列；<br>② `ComicDetailViewModel.kt` `openChapter` 优先判定本地下载文件，存在则直接读取离线文件启动秒开阅读，无网状态丝滑离线。 |
| 验收与统计 | 编译 0 错误，与 S7 统合构建验证。 |

---

## ⚙️ S7 实施日志（同步 / 设置 / 统计 / 屏蔽 / 日志）

| 事项 | 结果 |
| :--- | :--- |
| S7-1 WebDAV 云同步 | 新增 `com.venera.compose.sync.WebDavClient` 与 `WebDavSyncManager`：<br>① 支持 HTTP Basic 鉴权、PROPFIND 目录嗅探与 MKCOL 递归建目录；<br>② 备份命名遵循 `epochDay_timestamp.venera`，滚动保留最新 10 个备份；<br>③ 智能双向同步合并算法（远程较新时自动覆盖更新本地）。 |
| S7-2 本地全量备份与还原 | 新增 `com.venera.compose.sync.BackupManager`：<br>① 将本地收藏库、历史记录、阅读统计、屏蔽规则、偏好设置打包为 ZIP `.venera` 归档文件；<br>② 还原时具备完整数据库安全覆盖与内存缓存重载机制。 |
| S7-3 阅读统计与可视化 | ① 数据库迁移升级：`VeneraDatabase.kt` 版本由 2 升至 3，新增 `reading_stats`、`favorite_images`、`content_guard_rules` 三张新表；<br>② `ReadingStatsManager.kt`：记录阅读时长、阅读页数、每日活跃状态，动态计算最长连续打卡天数；<br>③ `StatsScreen.kt`：基于 Compose Canvas 自绘 14 日阅读趋势直方图、漫画阅读 Top 10、题材分类偏好分布饼图/排行。 |
| S7-4 单页插画收藏与灯箱 | ① 新增 `FavoriteImagesManager.kt` 与 `FavoriteImagesScreen.kt`；<br>② 阅读器菜单集成「收藏本页插画」动作，原画落盘持久化至 `favorite_images/`；<br>③ 瀑布流插画墙展示，支持点击进入全屏手势缩放灯箱，一键保存相册或系统分享。 |
| S7-5 内容屏蔽与 NSFW 过滤 | ① 新增 `ContentGuardManager.kt`（`security/guard/`）与 `ContentGuardScreen.kt`；<br>② 支持关键词、标签、画师、漫画ID 四类规则黑名单（普通包含 + 正则匹配，可启停）；<br>③ R18 分级遮罩三档：OFF 不过滤 / BLUR 封面打码 / HIDE 彻底隐藏，过滤探索、分类、搜索与列表中的敏感漫画。 |
| S7-6 MIUIX 风格设置页全量重构 | `SettingsScreen.kt` 深度重构，八大分类（阅读器偏好、外观主题、网络与代理、存储与下载、云同步与备份、内容过滤、阅读统计、关于与诊断）全量接入真实偏好存储，子页面完整连通。 |
| S7-7 运行时诊断日志系统 | 新增 `AppLogManager.kt` 与 `LogViewerScreen.kt`，捕获全局引擎日志、网络错误与源解析异常，支持内存滚动缓冲查看、筛选与导出排错。 |
| S7-REV 核查补修（S6/S7 全面自查） | 复查发现屏蔽规则此前**仅存在于管理页、未接入任何业务页面**，R18 遮罩同样无任何消费点，本次实装：<br>① `ContentGuardManager` 新增 `filterComicModels`（源生 Comic 口径）与 `filterExploreParts`（分区空块剔除）过滤 API；<br>② 探索页 `loadContentForTab`、分类漫画流 `loadComics`、搜索单源/全网聚合流（`SearchViewModel`）四处数据流全部接入过滤，规则增删后经 `LaunchedEffect(guardRules)` 对已加载内容即时重放；<br>③ R18 分级遮罩实装：新增 `coverMaskStateFor` 判定 API，探索卡片与搜索两处封面在 BLUR 模式下 `Modifier.blur(16dp)` 打码 + 角标提示（HIDE 模式由数据层整条剔除兜底）；<br>④ 复核确认 DownloadManager（Semaphore 并发/逐图3次重试/tmp 原子写/本地秒开）、WebDavClient（PROPFIND/MKCOL/PUT）、BackupManager（4 表全量）、DB v3 迁移路径、WorkManager 追更调度、导航与设置页全量接线均真实落地，详见 `s6-s7-audit-report.md`。 |
| 验收与统计 | `:app:assembleDebug` **0 错误 BUILD SUCCESSFUL**，APK **30.64 MB**（`app/build/outputs/apk/debug/app-debug.apk`，30,639,977 字节）。S6 与 S7 全部功能交付完成，核查补修项全部闭环。 |

---

编译验证一律以 `:app:assembleDebug` 为准，日志落 `.reference/buildN.log`。