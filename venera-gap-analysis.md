# Venera (Flutter) → 纯原生 Compose 复刻差距清单

> 生成时间 2026-09-16 · 基准仓库 `D:\venera-compose`（git 分支 `compose-migration` HEAD `e2303bd`，与 GitHub `origin` 一致，`:app:assembleDebug` BUILD SUCCESSFUL，APK 24.5 MB）
> 原版参照：`.reference/flutter-master/`（从 `master` 分支解出的 Flutter 源码，143 个 .dart / 1.77 MB / `lib/pages` 30318 物理行）
> 行数量纲：**物理行**；括号内为非空行。所有数字均为实读，无估算。

---

## 0. TL;DR（三句话）

1. **代码不是「Phase 3 没开始」，而是「Phase 2 的一半是假的」**：`QuickJsBridge.kt` 与 `BitmapSliceHelper.kt` 两个文件在全仓**零调用点**，因此文档承诺的「兼容原版 .js 扩展规则」与「长图视口切片防 OOM」实际都不存在。
2. **原版 33 个漫画源全部依赖 .js 规则脚本**（官方列表 `cdn.jsdelivr.net/gh/venera-app/venera-configs@main/index.json`：picacg / 禁漫 / nhentai / ehentai / jm / wnacg / hitomi / comick / komga / kavita / lanraragi / 少年Jump+ …），当前 Compose 侧靠 3 个手写爬虫，**规则源覆盖 0/33**。
3. **页面层完成度约 8.8%**（2653 / 30318 行），且缺两样全局地基：**导航栈**（0 处 `NavHost`）与 **ViewModel**（0 处）——52 个原版页面里有 **39 个既无入口也无路由**。

## 0.1 规模总览

| 领域 | 原版 (dart 物理行) | Compose 现有 (物理行) | 覆盖 |
| :--- | ---: | ---: | :--- |
| **全项目** | **143 文件 / 59859** (lib/) | **21 文件 / 4979** (app/) | **8.3%** |
| 页面 / 屏幕 `lib/pages/**` | 52 文件 / 30318 | 1814+566+187+86 = 2653 | **8.8%**，真数据接线仅 4 处 |
| 源引擎 + JS 协议 (`comic_source/`+`js_*`+`utils/image.dart`) | ≈5956 dart + 1520 JS | 1288 | 能力面 ≈10%，**规则源 0/33** |
| 数据 / 下载 / 同步 / 统计 (A 基础设施 8796 + B 页面 4883) | 31 文件 / 13679 | `data/` 6 文件 841；本域页面 0 | **6.1%** |
| 组件 / 视觉 `lib/components/**` | 27 文件 (`comic.dart` 2029 行最大) | 2 文件 / 273 | 见 §3 |

> 另有 **406 行「写了但没接上」的死代码**：`QuickJsBridge` 164 + `BitmapSliceHelper` 133 + `ComicSourceDao` 109，全库零引用；扣除后真实有效代码约 **4573 行**。

---

## 1. 全局架构阻塞项（已逐条 grep 核实）

| # | 阻塞项 | 证据 | 影响面 | 建议 |
| :--- | :--- | :--- | :--- | :--- |
| G-1 | **没有导航栈** | `MainActivity.kt` 中 `NavHost`/`rememberNavController` 出现 **0** 次；靠 `currentTab`/`selectedComic`/`activeReadingSession` 三个 `remember` 变量硬切（177-294 行） | 原版 52 页中 39 页无入口；无法深链、无法返回栈恢复、无预测性返回分层 | 引入 `androidx.navigation:navigation-compose` + `sealed interface Route`，先建骨架再搬页面 |
| G-2 | **没有 ViewModel/MVI** | 全仓 `ViewModel` **0** 次；`app/build.gradle.kts` 无 `lifecycle-viewmodel-compose` | 所有请求逻辑埋在 Composable 里，配置变更即重来；文档宣称的 MVI 完全未落地 | 每屏一个 ViewModel + `StateFlow<UiState>`，DAO 注入 |
| G-3 | **图片请求从不带 Referer/Cookie（防盗链必 403）** | `ImageLoader`/`ImageRequest`/`headers[` 全仓 **0** 命中；Coil 用默认 fetcher；`ChapterPages.headers` 字段有值但**无任何消费方**；`AndroidManifest` 未声明 `android:name`（无 `Application` 类可挂全局 ImageLoader） | 拷贝漫画/包子/bilibili 类源封面与内页全部加载失败——这是「真机能不能看图」的头号问题 | 新建 `VeneraApp : Application` + Coil3 `ImageLoader` 自定义 fetcher（OkHttp + 按 URL 前缀注入 headers） |
| G-4 | **QuickJS 桥是死代码** | `QuickJsBridge`/`executeScript` 除自身定义外 **0** 调用点 | 无法加载 33 个官方 .js 规则源 | 见 §2，按 `doc/js_api.md` + `assets/init.js` 重建脚本引擎 |
| G-5 | **长图切片器是死代码** | `BitmapSliceHelper` 除自身 `object` 声明外 **0** 引用；阅读器直接 `SubcomposeAsyncImage`(513/546 行) 整图铺 | 条漫超长图（>8192px）在低端机 GPU texture 溢出→白屏/OOM | 阅读器竖滑模式接 `BitmapRegionDecoder` 视口切片 |
| G-6 | **数据层实现与选型不符** | `VeneraDatabase` = 手写 `SQLiteOpenHelper` + 裸 SQL；`VeneraPreferences` = `SharedPreferences`；无 Room/KSP/DataStore 依赖 | 迁移/schema 升级/类型安全全靠手工 | 决策点：要么补 Room+KSP 正式化，要么在文档里把选型改掉（**不要两头不靠**） |
| G-7 | **源元数据表是孤儿** | `ComicSourceDao.kt`(99 行，含 `sourcesFlow`) 在 `ComicSourceManager`/`MainActivity` 中 **0** 引用；3 个源在 `ComicSourceManager.kt:42-48` 硬编码注册 | 无法启停/增删源，`comic_source` 表永远空 | 与 G-4 合并：Dao 作为脚本源注册表 |
| G-8 | **Coil 版本代差** | `libs.versions.toml: coil = "2.7.0"`，文档写「Coil 3」 | Coil3 的 `ImageRequest` headers/fetcher API 与 2.x 不同，G-3 的实现路径取决于此 | 升 Coil3 或明确留在 2.x 并改文档 |

---

## 2. 漫画源引擎与 JS 规则协议

### 2.0 引擎能力实测（本喵亲自反编译/取证，决定阶段 2.5 选型）

**原版协议的本质是一条异步总线**：`assets/init.js`（40031 字符 / 1520 行，已解出到 `.reference/flutter-master/assets/init.js`）中，所有宿主能力都经 `sendMessage({method, ...args})` 调用并 `.then()` 消费。**宿主侧只有 1 个入口、19 个 method**（本喵逐行核对 `.reference/flutter-master/lib/foundation/js_engine.dart:112-205` 的 `_messageReceiver` switch）：

| 宿主 method | init.js 内出现次数 | 职责 |
| :--- | ---: | :--- |
| `http` | 2（统一出口） | 全部网络请求（GET/POST/PUT/PATCH/DELETE + headers + ArrayBuffer body） |
| `convert` | 1（13 种类型分发） | md5/sha1/sha256/sha512/hmac/AES(ECB,CBC,CFB,OFB)/RSA/base64/hex/GBK，**以 ArrayBuffer 收发字节**（`js_engine.dart:153 → _convert`） |
| `html` | 21 个子命令 | `HtmlDocument/HtmlElement/HtmlNode` 句柄式 DOM（宿主用树解析器实现，docKey/elementKey 表 + 8 文档 LRU） |
| `compute` | 1 | **不是加密**！是把 JS 函数字符串丢进 4-Isolate `JSPool`(`js_pool.dart`) 的旁路计算 |
| `uuid` | 1 | Uuid v1 |
| `cookie` | 3 | get/set/delete cookies（源登录态） |
| `UI` | 7 | showMessage/showDialog/showLoading/showInputDialog/showSelectDialog/launchUrl |
| `save_data` / `load_data` / `delete_data` | 各 1 | **每个源独立的 KV 存储**（源用它存 token/书架/历史） |
| `random`(2)、`delay`、`log`、`getLocale`、`getPlatform`、`getClipboard`、`setClipboard`、`isLogged` | — | 杂项 |

`init.js` 自己在 JS 侧定义了 `Comic / ComicDetails / ComicSource / Comment / Cookie / HtmlDocument / HtmlElement / HtmlNode / Image / ImageLoadingConfig / createUuid / randomInt / randomDouble / setTimeout / setInterval / _Timer / log / compute`。也就是说：**只要宿主实现这 1 个 `sendMessage` 分发器并原样加载 init.js，JS 侧半壁江山是免费的**——这跟现在手写 40 行桩的思路完全不同。

**但当前依赖做不到。** 反编译本地 Gradle 缓存 `~/.gradle/caches/modules-2/files-2.1/app.cash.quickjs/quickjs-android/0.9.2/`（AAR 1470KB）得到的完整 public API：

```java
public final class app.cash.quickjs.QuickJs implements Closeable {
  public static QuickJs create();
  public Object evaluate(String);
  public Object evaluate(String, String);
  public <T> void set(String, Class<T>, T);   // 只能挂 Java 接口
  public <T> T  get(String, Class<T>);         // 可把 JS 对象取成 Java 接口代理
  public byte[] compile(String, String);
  public Object execute(byte[]);
  public void close();
}
```

致命限制（三条）：
1. **没有微任务泵**：无任何 `executePendingJobs` / job-queue 入口 ⇒ `Promise.resolve().then()` 永远不会被 drain ⇒ init.js 里所有 `.then()` 链直接失效。
2. **没有二进制通道**：`evaluate`/`set`/`get` 只走 String/基本类型 ⇒ `Convert.*(ArrayBuffer)`、`Network.fetchBytes`、`Image` 全部无法桥接（原版 `doc/js_api.md` 里 Convert 17 个方法**全部以 ArrayBuffer 收发字节**）。
3. **单实例非线程安全**：`QuickJs.create()` 一个 context，`runBlocking` 同步调用（现状即如此）会把源脚本里的网络阻塞在 JS 线程上，无并发源执行。

**结论与选型建议（阶段 2.5 的前置决策）**：

| 方案 | 说明 | 代价 | 判断 |
| :--- | :--- | :--- | :--- |
| A. 换 **`app.cash.zipline`** | 同厂同底层（QuickJS），提供 Kotlin↔JS 类型化接口、`suspend` 函数在 JS 侧天然呈现为 Promise、自带并发与脚本热更新/签名分发 | 引入 Gradle 插件 + Klib 工程结构，Android/Kotlin 版本敏感（其 README 明说「uses unstable APIs, sensitive to version updates」） | **推荐**：正好补齐「Promise + 二进制」两个硬缺口 |
| B. 自己 NDK 编 QuickJS 并导出 `JS_ExecutePendingJob`/`JS_Call`/ArrayBuffer | 完全可控，能精确复刻原版 `js_engine.dart` 的行为 | 需维护 native 构建、ABI×4、内存与崩溃边界 | 备选：A 受阻时上 |
| C. 保留 0.9.2，改「同步 thenable」假 Promise | 在 JS 侧手写微型 thenable + 让所有 host 调用同步返回 | 无法支持 `await`/定时器/重试逻辑，源脚本稍复杂即崩，且仍需 base64 中转字节 | ❌ 不建议 |
| D. 不做脚本引擎，继续手写 Kotlin 源 | 现状路线 | 官方 33 个规则源永远 0 覆盖，每个源要逆向维护 | ❌ 与「根据原项目复原」目标冲突 |

> 待验证项（阶段 2.5 第 0 步，需真机/模拟器）：`QuickJs.create().evaluate("Promise.resolve(1).then(v=>2)")` 在不 pump 的情况下是否会 resolve——用于给限制 1 补一个真机铁证。

### 2.1 复刻体量与权威依据（全部已解到工作区）

| 参照物 | 物理行 | 说明 |
| :--- | ---: | :--- |
| `doc/comic_source.md` | 739 | 源脚本协议总规范（**验收标准**） |
| `doc/js_api.md` | 513 | 逐函数 API 规格（Convert 17 / Network 10 / Html 20+ / UI 6 / Utils 3） |
| `doc/headless_doc.md` / `import_comic.md` | 180 / 74 | 无头调用与本地导入协议 |
| `assets/init.js` | 1520 | JS 侧运行时（**必须原样加载**，不要重写） |
| `foundation/js_engine.dart` | 736 | 宿主 dispatcher + _http/_html/_convert/_cookie |
| `foundation/js_pool.dart` / `js_ui.dart` / `utils/image.dart` | 163 / 258 / 324 | compute 池、UI 面板、图片改写引擎 |
| `foundation/comic_source/`（6 文件） | 2713 | `parser.dart` 1307 / `models.dart` 561 / `comic_source.dart` 543 / `category.dart` 143 / `favorites.dart` 66 / `types.dart` 93 |
| `lib/network/`（8 文件） | 1360 | app_dio 281 / images 324 / cookie_jar 243 / cache 230 / cloudflare 222 / proxy 60 |
| **源引擎域合计** | **≈7476** | 其余 53903 行 dart 属 UI/阅读器/下载/同步 |
| Compose 本次范围（9 文件） | **1288** | 覆盖率按能力算 ≈ **10%**（见 2.2） |

### 2.2 JS 侧脚本可见 API 覆盖（脚本能不能跑起来就看这张表）

| 协议面 | 原版成员数 | Compose 现有 | 缺口 |
| :--- | ---: | ---: | :--- |
| `Convert` | 22 | **5** | 缺 17：全部 AES(4 模式×2 向)、RSA、sha1、sha512、hmac(key,value,hash)、hexEncode、GBK 编解码、encodeUtf8/decodeUtf8；且现有 5 个是 **String 入 String 出**，协议规定 ArrayBuffer |
| `Network` | 10 + 全局 `fetch` | **2** | 缺 fetchBytes/sendRequest/put/patch/delete/setCookies/getCookies/deleteCookies；**`post(url,body,headers)` 参数顺序与协议 `post(url,headers,data,extra)` 冲突** |
| `Html` | `HtmlDocument`4 + `HtmlElement`13 + `HtmlNode`3 = 20 | **2 个扁平函数** | 返回的是**文本**不是节点句柄 → 无法 `querySelectorAll(...).map(e=>e.attributes.href)`，真实脚本 100% 跑不通 |
| `UI` | 7 | 0 | showMessage/showDialog(带 async callback)/launchUrl/showLoading/cancelLoading/showInputDialog/showSelectDialog |
| 类型构造器 | 6 | 0 | `new Comic({...})` 直接 ReferenceError（Comic/ComicDetails/Comment/Cookie/ImageLoadingConfig/Image） |
| `ComicSource` 基类 | 15 成员 | 3 | 缺 loadData/loadSetting/saveData/deleteData/isLogged/translation/translate/init/minAppVersion/url/sources |
| Utils | 12+ | 0 | `setTimeout`/`setInterval`(依赖宿主 `delay`)、createUuid、randomInt/Double、console、log、剪贴板、`APP{version,locale,platform}` |

### 2.3 `ComicSource` 协议能力差距（`parser.dart` 实测 30 个解析项，择要）

| 能力 | 原版 | Compose | 后果 |
| :--- | :--- | :--- | :--- |
| 搜索 | `search.load(keyword, options[], page)` + `loadNext(next 游标)` + `optionList`(select/multi-select/dropdown) + 标签建议 | `search(String, Int=1)` | 无筛选、无游标分页，长列表源不可用 |
| 探索 | `explore[]` 三型 `multiPartPage/multiPageComicList/mixed` + 多页签 + viewMore | 单一 `getExploreComics(page)`（且 UI 零调用） | 探索页只能继续 mock |
| 分类 | `category{parts: fixed/random/dynamic}` + `categoryComics{ranking, optionsLoader}` | 无接口 | 分类页/榜单页整体不存在 |
| 详情 | `ComicDetails` 20 字段 + `loadThumbnails` 分页 + recommend + commentCount | 7 字段 | 缩略条/推荐/评论区无处挂；**且 author/status/rating 三源全是硬编码**（CopyManga:165-171 甚至 title=comicId、cover=""、rating=4.9f） |
| 图片加载 | `onImageLoad(url,cid,eid) → ImageLoadingConfig{url,method,headers,data,onResponse,modifyImage,onLoadFailed}` + `onThumbnailLoad` | 只有静态 `ChapterPages.headers`，**且全库零消费者** | 加密图/换址图/防盗链图全废 |
| 评论 | `loadComments/sendComment/loadChapterComments/sendChapterComment/likeComment/voteComment/starRating/likeComic`(8) | 0 | 详情页与阅读器章评全无（连 Comment 模型都没有） |
| 网络收藏 | `favorites{multiFolder, addOrDelFavorite, loadFolders, addFolder, deleteFolder, loadComics, loadNext, allFavoritesId, isOldToNewSort}`(10) | 0 | 只有本地表，跨端收藏体系不存在 |
| 账号 | `account{login, loginWithWebview{url,checkStatus,onLoginSuccess}, loginWithCookies{fields[],validate}, logout, registerWebsite, loginWebsite, infoItems[]}`(7) + `isLogged` + **retryZone 失效自动重登**(parser:812-828) | 0 | **32 源中所有需登录源永不可用** |
| 每源设置/翻译 | `settings{select/switch/input/callback}` + `translation{locale:{k:v}}`+`translate()` | 0 | 源自带选项与文案本地化缺失 |
| 分享链接转 ID | `link{domains[], linkToId}` + `idMatch` + `onClickTag` | 0 | 粘贴链接直达无法复刻 |
| 每源 KV | `<dataPath>/comic_source/<key>.data` JSON + `saveData()` 去抖 + 拒写 `data_key=='setting'` | 无 | 脚本存不了 token/游标/书架 |
| 版本闸门 | `minAppVersion` + `compareSemVer` + 动态 `sources[]` + `contentWarning` | 无 | 装错版本直接崩，分级门禁缺失 |

### 2.4 源生态：官方 33 条规则源，当前覆盖 0/33

- **原版内置 .js 源数量 = 0**：`assets/` 实测只有 `app_icon.png / init.js / opencc.txt / source_content_warning.json / tags.json / tags_tw.json / translation.json`。源是运行时从 `<dataPath>/comic_source/*.js` 目录扫描加载（`comic_source.dart:56-73`），用户从清单仓库安装。
- 默认清单 `https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/index.json`（`appdata.dart:404-405`、`init.dart:119`）——**本喵实测拉取：33 条目 / 32 唯一 key**：copy_manga(标准+多账号)、Komiic、baozi、picacg、nhentai、wnacg、ehentai、jm、manga_dex、ikmmh、shonen_jump_plus、hitomi、comick、ykmh、zaimanhua、ManHuaGui、manwaba、lanraragi、komga、comic_walker、mh1234、ccc、goda、mh18、mxs、manhuaren、hcomic、jcomic、hot_manga、kavita、happy、mycomic。另有 `source_content_warning.json` 维护 31 个 key 的分级预设。
- Compose 侧：3 个 Kotlin 硬编码源；`ComicSourceDao`(109 行，本应做注册表) 与 `QuickJsBridge`(164 行) **全库零引用**；无仓库拉取 / 无 `.js` 导入 / 无更新检查 / 无启停删除。三个源的 `version` 抄成与 index.json 相同的 `1.2.0/1.4.2/1.1.6`——只是抄号，不是协议实现。
- 首页探索 Tab 硬编码里出现「哔咔 · 日榜 / 周榜」，但 **Picacg 源根本不存在**（属于 UI 层面的虚假宣传）喵。

### 2.5 网络与图片子系统缺口（原版全在宿主侧，脚本不感知）

| 机制 | 原版实现 | Compose 现状 |
| :--- | :--- | :--- |
| Cloudflare 过盾 | `cloudflare.dart:63-99` 检测 403 + `cf-mitigated: challenge` → 抛 `CloudflareException` → `passCloudflare()`(101-221) 开真 WebView 轮询 `#challenge-*` → 抓 `cf_clearance` + 真实 UA 回写 cookie 库 → pop 页面续跑；UI 用正则反解错误串显示「需通过 Cloudflare 验证 + 重试」 | **完全不存在**（无 WebView 依赖/Activity）→ ehentai/jm/mycomic 等必死 |
| UA 策略 | `app_dio:225-228` 无 UA 时补 `venera/v<ver>`；JS `_http:221-223` 补 webUA；过盾后用浏览器真实 UA 与 cf_clearance **绑定** | 全局硬编码 Chrome UA 且用 `.header()`(set 语义) **无条件覆盖脚本传入的 UA** |
| 图片管道 | `images.dart:124-241`：cacheKey=`imageKey@src@cid@eid`、retryLimit=5 且用 `onLoadFailed()` 换 config 重试、`onResponse` 改字节、`modifyImage` 在 Isolate 内 RGBA 改写→lodepng 重编码、同图并发去重、`cover.` 前缀回落、`//host` 补 `https:` | Coil 默认 ImageLoader，**无 fetcher 定制、无 headers 注入、无重试、无并发去重** |
| 传输层 | rhttp/libcurl 适配器、`headers[http_client]==dart:io` 可切系统 HttpClient、`prevent-parallel:true` 同 URL 互斥、NetworkCacheManager 响应缓存、SNI/ignoreBadCertificate、`enableDnsOverrides` 静态 DNS、代理 system/direct/自定义 | **DoH 与代理是假开关**：`okhttp-dnsoverhttps` 在依赖里但 `VeneraNetworkClient.kt:20-35` 从未 `.dns()`/`.proxy()`/`.cache()`；`enableDoH`/`proxyType`/`proxyHost`/`proxyPort` 四键只写 SharedPreferences 无消费者 ✔本喵复核 |
| 限速 | `loadDataWithRetry` 3 次、图片改写并发 >3 限流、429/403/404 可读化 | 无任何退避与限速；**MangaDex @home 未带 `Rate-Limit-Header`、未做 1req/s、图片无 Referer** → 违反官方政策且必 403 |

### 2.6 判决：现在的桥跑不了任何真实规则脚本（33/33 失败），三条独立死因

1. **引擎硬上限**（见 2.0 反编译证据）：0.9.2 的 JNI 导出只有 `createContext/destroyContext/evaluate/call/get/set/compile/execute`，**无 job 队列**、**无 ArrayBuffer 通道**、`set()` 只能绑 Java interface 并走反射代理 + 全 String 序列化（大 JSON 双份拷贝）。
2. **生命周期模型错**：`executeScript` 每次 `QuickJs.create() … finally close()`(51,148)。原版依赖**一个长驻引擎 + 全局注册表** `ComicSource.sources[key]`；每次关引擎，注册表、`this.data`、`_Timer`、闭包全蒸发。
3. **零接线 + 资源缺失**：`app/src/main/` 下**没有 `assets/` 目录**，即 `init.js` 从未进 APK；桥本身 0 调用点。

**因此：`QuickJsBridge.kt` 建议整体删除重写，而不是增量打补丁。换引擎（方案 A：Zipline）是阶段 2.5 的前置决策。**

### 2.7 阶段 2.5 分步实施清单（每步独立可验收）

| # | 步骤 | 验收标准 |
| :--- | :--- | :--- |
| 0 | **引擎 spike（阻断项，0.5d）** | 用候选库各写 20 行：`evaluate("Promise.resolve(1).then(v=>globalThis.x=v)")` 后能读到 `x==1`（证明有微任务泵）+ ArrayBuffer↔ByteArray 往返成功。**此步不过，2.1 之后全部结论作废** |
| 1 | 原样搬运 `init.js` → `app/src/main/assets/venera-init.js` | 逐字节不改；存 SHA-256 常量 + 单测比对，防协议被悄悄改写 |
| 2 | 单方法 dispatcher `JsBridge.send(JSONObject): Any?` | 覆盖 19 method；JS 侧只注入 `sendMessage` 与 `appVersion` 两个 global（对齐 `js_engine.dart:93-97`），不再手写 shim |
| 3 | 异步回灌 | `http/delay/UI.show*/compute` 返回真 Promise；宿主 `CoroutineScope(SupervisorJob()+Dispatchers.IO)`，**所有 QuickJS 调用串行到单线程** |
| 4 | 二进制通道 | `Network.fetchBytes` 可用（决定 Convert 全族能否工作） |
| 5 | html(21) + convert(13) 子命令 | Jsoup + docKey/elementKey 句柄表 + 8 文档 LRU；AES-CFB/OFB 自写块包装；RSA 私钥按 `_parsePrivateKey:527-543` PKCS#8 解析 |
| 6 | 每源 KV | `<key>.data` JSON + saveData 去抖 + 拒写 `setting` |
| 7 | 长驻引擎 + `ComicSourceParser` | 首行正则取类名(parser:88-97) → `(()=>{js; this.temp=new X()})()` → 抽全 30 项 + `compareSemVer` 闸门 |
| 8 | 源仓库与安装 | 拉 `index.json` → 列表页（安装/更新/删除/reload）+ `availableUpdates` |
| 9 | 网络中间件 | Cloudflare 过盾 + cf_clearance↔UA 绑定回写、`prevent-parallel`、429 退避、Coil 自定义 ImageLoader 消费 `ImageLoadingConfig` |
| 10 | account 面板 | login / loginWithCookies / loginWithWebview(轮询 checkStatus) / logout / isLogged / retryZone |
| 11 | 契约回归 | 取上游 `manga_dex.js`、`copy_manga.js`、`baozi.js` 做结构比对测试，再上 `nhentai`/`ehentai`（cookie+CF 组合考验） |

### 2.8 两条「不要照抄」清单

- **上游已知 bug（复刻时应修不应抄）**：① `HtmlNode.toElement()` 发的是 `function="node_toElement"`(`init.js:973`)，dart 只认 `"node_to_element"`(`js_engine.dart:338`) → 永远返回 null；② `Network.get`(init.js:523) 与 `delete`(:570) 把第 3 参 `extra` 当 `data` 传给 `sendRequest`。
- **原版并不存在的 API（别按这些名字验收）**：`toast`/`alert`/`confirm`/`onNewComment`/`networkInterceptor`/JS 侧 `cloudflare` 绕过/`localStorage`/宿主 `chapterImages`/`autoGetFavorite`/`getHistory`。它们的职责分别由 `UI.showMessage`、`UI.showDialog`、无、dart 侧拦截器（不经 JS）、仅 webview 抓取、`ComicDetails` 字段、无、Room 层 承担。

> 补充（本喵核对 `js_engine.dart:117-205` 后确认）：19 个 method 的准确清单 = `log, load_data, save_data, delete_data, http, html, convert, random, cookie, uuid, load_setting, isLogged, delay, UI, getLocale, getPlatform, setClipboard, getClipboard, compute`。原版注释里写明 `delay` 只是 `[setTimeout]` 的临时方案（TODO 要在 quickjs 里实现），且 `save_data` 明确禁止写 `setting` 键。

---

## 3. 组件层与视觉体系

**规模**：原版 `lib/components/` = **27 文件 / 11818 行**；Compose `components/` = **2 文件 / 273 行**（2.3%），其余通用件全部内联在 `MainActivity.kt` 里当私有 Composable。

### 3.1 组件差距表

| 原版组件 | 行数 | 提供的能力 | Compose 现状 | 缺口 |
| :--- | ---: | :--- | :--- | :--- |
| `comic.dart` | 2029 | 漫画卡片/列表项/瀑布流单元 + **`isBlocked` 屏蔽词判定**(:1222-1247)+列表级剔除(:1313-1322) + 收藏角标 + 长按多选菜单 + 标签翻译 | 内联于 MainActivity 的卡片 🟡 | 屏蔽体系整体不存在；长按多选、跨源同名识别缺失 |
| `navigation_bar.dart` | 1683 | `NaviPane` 悬浮胶囊 + `LiquidGlassLens` **真实背景模糊透镜** + 指示药丸形变 + 桌面 `NavigationRail` 双布局 | `VeneraFloatingNavBar.kt`(187) 🟡 | **毛玻璃是假的**（见 3.2）；无桌面双栏、无设置入口位置切换 |
| `appbar.dart` | 1060 | 折叠顶栏、流体搜索栏展开、更多菜单、滚动联动 | 只用了 Miuix `TopAppBar` 🟡 | 流体展开/收起与滚动折叠缺失 |
| `js_ui.dart` | 258 | **脚本宿主 UI 面板**：showMessage / showDialog(async callback + 按钮 loading) / showLoading(可取消) / showInputDialog(带 validator) / showSelectDialog / launchUrl | ❌ 0 行 | **直接阻塞阶段 2.5**（见 3.6） |
| `loading.dart` | 546 | `LoadingState` 状态机 + `loadDataWithRetry`(3 次) + **CF 异常识别与「需通过 Cloudflare 验证」重试卡**(25-68) | ❌ | 无统一加载态，错误被 `catch {}` 吞掉 |
| `message.dart` | 577 | 全局 Toast / Snackbar / 通知队列 | ❌ | 收藏成功、复制、登录结果无反馈（现在只振动） |
| `window_frame.dart` | 644 | CMP 桌面窗口边框/标题栏 | ❌ | desktop 模块另写一套，零共享 |
| `components.dart` / `select.dart` / `menu.dart` / `flyout.dart` / `pop_up_widget.dart` | 528/322/271/223/243 | 通用按钮、下拉/多选、弹出菜单、flyout、**统一弹窗中心** | 部分被 Miuix 顶掉 🟡 | 弹窗中心缺失 ⇒ 二级页没法弹收藏面板/设置面板 |
| `scroll.dart` / `custom_slider.dart` | 431/225 | 滚动条与回弹、阅读器进度滑条 | ❌ | 阅读器进度拖拽细节缺失 |
| `image.dart` | 406 | 带下载进度/占位/重试的图片组件 | Coil 直用 🟡 | 无进度百分比、无失败重试按钮 |
| `search_tag_bar.dart` | 402 | 标签建议栏、横向标签选择 | ❌ | 搜索标签体系缺失 |
| `rich_comment_content.dart` | 323 | 评论富文本（@、emoji、图、楼层折叠） | ❌ | 评论区的前置 |
| `side_bar.dart` | 187 | 收藏夹侧栏文件夹树 | ❌ | 见 §5 收藏体系 |
| `nsfw_cover.dart` | 130 | 分级遮罩 + 解锁交互（配合 `ContentGuard` 359 行、`nsfwMaskStrength`） | ❌ | 内容分级门禁完全缺失 |
| `background.dart` | 173 | `AppBackground._buildAmbient` 顶部 1.6 倍屏宽双色氛围光 | `VeneraAmbientBackground.kt`(86) 🟢 | **这条不算虚标**：Canvas 径向渐变与原版 CustomPaint 同思路、0 离屏开销；只差半径系数(0.95 vs 1.6)与暗化层数 |
| `code.dart` / `effects.dart` / `gesture.dart` / `cover_hero.dart` / `layout.dart` / `button.dart` / `consts.dart` | 383/31/72/52/227/389/3 | 日志高亮、动效、手势、封面 Hero、布局工具 | 🟡 部分内联 | 日志页无；Hero 只在 3 屏内硬切 |

### 3.2 「悬浮毛玻璃胶囊」目前是假的（grep 铁证）

- 全 `app/src` 搜 `Modifier.blur|renderEffect|RenderEffect|BlurEffect|rememberShaderBrush|RuntimeShader|Backdrop` ⇒ **0 命中**。
- 实际实现是 `Color(0xFFFCFCFD).copy(alpha=0.90f)`（浅）/`surface.copy(alpha=0.85f)`（深）+ `shadow(16.dp)` + 1dp border —— 是「半透明白板」，列表滚到底栏下方**不会被模糊**。原版 `navigation_bar.dart` 内 blur 32 次、Backdrop 7 次、CustomPaint 18 次。
- 三条真实现路径（按性价比）：① **Miuix 自带 `miuix-blur` 模块**（本地 Gradle 缓存已有 `miuix-blur.aar`，但 `app/build.gradle.kts:46` 只声明了 `miuix-ui`）→ 风格最一致，优先；② Compose `graphicsLayer { renderEffect = BlurEffect(...) }`（API 31+，minSdk 26 需降级路径）；③ Haze 类三方库。
- 公道话：弹簧与指示药丸动效是**真的**（`animateDpAsState` + `spring(dampingRatio=0.78f)`、Outlined↔Filled 切换、`HapticFeedbackConstants.KEYBOARD_TAP` 都在），只是「玻璃」这一层名不副实。

### 3.3 Miuix 只用掉 8 / ~30（自废武功）

- 实测 `import top.yukonga.miuix.kmp.*` 只有 **8 种**：`MiuixTheme, Surface, Text, Button, Card, Scaffold, TopAppBar, ButtonDefaults`。
- 反查 `miuix-ui.aar` 的 `basic/theme/utils` 包共 **59 个 Composable 类文件**，含 `Switch, Slider, ListPopup, Dropdown, TabRow, SearchBar, Search, Sidebar, NavigationBar, NavigationRail, FloatingActionButton, FloatingToolbar, PullToRefresh, ProgressIndicator, RadioButton, Checkbox, NumberPicker, Tooltip, Snackbar, Badge, Divider, ColorPicker, ThemeController, DynamicColors, MonetMapping` 等。
- 影响：设置页该用 `Preference`/`Switch`/`ListPopup`（现在是手写 `Card + clickable{}` 空壳）；底栏可用 `NavigationBar`；搜索用 `SearchBar`；下拉刷新用 `PullToRefresh`。
- **主题色两头不接**：`MiuixTheme{}` 未传自定义 `colorScheme`(MainActivity:168 用默认)，而 `themeMode` 偏好本身也是 8 个零消费键之一 ⇒ §1.2「主题色 / 深色跟随」无从生效。

### 3.4 图标与资源

- 现在用 `material-icons-extended`（`Icons.*` 18 处），**已不是 emoji**（比 UI 复刻方案那个阶段的自述有进步）。
- 但 Miuix 的 `miuix-icons`（缓存里有，1.8 MB）未声明依赖，底栏/详情按钮图标未逐个对齐原版形态。
- **资源性缺口**：`app/src/main/` 连 `assets/` 目录都不存在，而原版依赖 `init.js`(脚本运行时)、`tags.json` 1MB / `tags_tw.json` 1.3MB（标签翻译，配 `enableTagsTranslate`）、`opencc.txt`(简繁转换)、`translation.json`(i18n)、`source_content_warning.json`(31 源分级预设)。

### 3.5 转场与手势

- 已具备 `SharedTransitionScope`（`AndroidHomeScreen`/`AndroidComicDetailScreen` 签名里带着它，封面 Hero 思路对）+ `PredictiveBackHandler` + `enableEdgeToEdge`。
- 但**无 NavHost ⇒ 共享元素只能在 3 个硬切屏之间生效**；原版 `foundation/app_page_route.dart`(31KB) 自定义路由转场与桌面 `window_frame` 全缺；`shaders/preview_optical.frag`（光学转场着色器，master 上还有专门的 3 轮帧率优化提交 `2438a4e`）在 Compose 侧零使用。

### 3.6 一个容易被排错顺序的强耦合

`components/js_ui.dart`(258 行) 就是原版 `UI` 宿主 method 的实现体。**阶段 2.5 的脚本引擎验收必然连带这层面板**（带 async callback 的 showDialog、可取消 showLoading、带 validator 的输入框、选择框）——picacg / jm 这类要登录与配置弹窗的源，没有它就装不上。它属于 §2.7 第 9-10 步的前置，别当成「纯视觉组件」推到 W2 之后。

---

## 4. 数据层 / 持久化 / 下载 / 本地漫画 / 云同步 / 统计

**体量**：原版「数据基础设施」21 文件 / **8796 行** + 本域页面 10 文件 / **4883 行** = **13679 行**；Compose 侧 `data/` 6 文件 **841 行**（9.6%），本域页面 **0 行**（0%）→ 整域覆盖 **6.1%**。

### 4.1 数据模型字段级差距

| 原版实体 | Compose | 关键缺失 |
| :--- | :--- | :--- |
| `Comic` (models.dart:42-118) 10 字段 | `ComicSourceModels.kt:6-16` | 缺 `maxPage`(进度分母)、`language`、`favoriteId`、`stars`；`isFavorite` 应是查询态而非字段 |
| `ComicDetails` (:139-187) 29 字段 | :31-39 (7 字段) | **缺 22 项**：`comicId≠id`、`subId`、`tags:Map<命名空间,List>`、`ComicChapters`(分组)、`recommend`、`isLiked/likesCount/commentCount`、`uploader/uploadTime/url/stars/maxPage/comments`；**且 author/status/rating 三个源全部硬编码**（CopyManga:165-171 甚至 `title=comicId`、`cover=""`、`rating=4.9f`；Baozi:115-116；MangaDex:177） |
| `ComicChapters` (:334-449) | `ComicChapter`(4 字段) | 无卷分组/组内索引/倒序；`PageJumpTarget` 无对应 |
| `History` (history.dart:40-103) | `HistoryDao.kt:11-22` | 缺 `type`(ComicType)、`group`、`readEpisode:Set`、`maxPage`；**页码 0-based vs 原版 1-based 不可互导**；`author` 恒空、`sourceName` 硬编码「拷贝漫画」(reader:129/131) |
| `FavoriteItem`+`FolderInfo`+`UpdateInfo` (favorites.dart:24-203) | `FavoriteRecord` | 缺 `translated_tags`、`display_order`、`last_update_time`、`has_new_update`、`last_check_time`；`coverPath`(本地封面) 无处放 |
| `DownloadTask`/`ImagesDownloadTask` (download.dart:21-101) | **无** | 整实体缺失 |
| `LocalComic` (local.dart:19-174) | **无** | 整实体缺失（`ComicPageSource.LocalFile/ZipEntry` 声明后无人构造） |
| `ImageFavorite*` (image_favorites.dart:3-213) | **无** | 整实体缺失 |
| `Comment` (models.dart:3-20) 9 字段 | **无** | 整实体缺失（详情页评论数是字符串常量 "128"，MainActivity:979） |
| `ComicSource` 插件模型 (comic_source.dart:110-214) | `interface`(5 方法) | **缺 22 个能力槽**；无 `ComicType`(int↔sourceKey) → DB 无法表达跨源同名 ID |
| 分页游标 `Res<T>/subData`、`SearchNextFunction(keyword,next,options)` | `search(String, Int=1)` | 只有整数页码，无 `next` 游标（网络收藏 / eh 类源必需） |

### 4.2 SQLite schema 差距（原版 5 库 / 8 静态表 + N 动态表 vs Compose 1 库 / 3 表 / 27 列）

| 原版表 | 定义位置 | Compose | 差距 |
| :--- | :--- | :--- | :--- |
| `history`(11 列) | history.db | `comic_history`(**10 列**) | 缺 `type`/`readEpisode`/`max_page`/`chapter_group`；**PK 单列 `comic_id` vs 原版 `(id,type)` → 跨源同 id 互相覆盖**；原版用 PRAGMA+ALTER 幂等补列，Compose `onUpgrade` 空 + VERSION=1 → 加列即破坏性升级 |
| `read_stats`(PK date,cid,type + tags JSON) | history.dart:231-253 | **无表** | 阅读统计整条链路无处存 |
| `image_favorites`(11 列) | history.db | **无** | 图片收藏不存在 |
| `folder_order` / `folder_sync` | favorites.dart:230-242 | **无** | 收藏夹排序 + 「本地夹↔网络收藏夹」绑定无处存 |
| **每个收藏夹一张动态表**(9 列 + 追更 3 列) | favorites.dart:523-536, 1118-1147 | 单表 + `folder_name` 字符串 | 原版「表名即文件夹」设计（rename、每夹 `display_order`、每夹计数）完全丢失 |
| `comics`(10 列 PK id,comic_type) | local.db | **无** | 本地漫画库不存在 |
| `cache`(5 列 + md5 分桶 + MB 限额驱逐) | cache_manager.dart:74-112 | **无** | 自有缓存体系不存在，Coil 磁盘缓存零配置 |
| `cookies`(7 列 PK name,domain,path) | cookie_jar.dart:20-31 | `PersistentCookieJar` 用 SharedPreferences+Gson **按 `url.host` 分桶**(:84-98) | domain cookie 无法下发子域，path/secure 语义丢失 |
| `comic_source`(7 列) | — | `comic_source` 表存在但 **Dao 零引用** | 文档 §1.1「预置拷贝/哔咔/MangaDex 等主流源元数据」实为未接入 |

> 原版另有 6 类文件：`appdata.json` / `syncdata.json` / `implicitData.json` / `downloading_tasks.json` / `local_path` / `comic_source/*.js`。Compose 一侧不备。
> **设置项规模差距：原版 84 个键（appdata.dart:175-273 精确计数）↔ Compose 10 个键。**

### 4.3 偏好与网络开关的真实接线情况（本喵复核过 grep 结果）

- `VeneraPreferences` 类外**只有 3 个调用点**：读 `defaultReadingMode`(reader:85)、读 `pageGapDp`(:96)、写 `setDefaultReadingMode`(:370)。
- ⇒ **10 键中 8 个既不读也不写**：`keepScreenOn`、`volumeKeyTurn`、`autoCropBorders`、`themeMode`、`enableDoH`、`proxyType`、`proxyHost`、`proxyPort`。
- ⇒ `VeneraNetworkClient.kt:20-35` 无 `.proxy()` / 无 `.dns()` / 无 `Cache()`：总纲 §1.3 宣称的「DoH 与 SOCKS5 代理支持」与 §1.2 的「屏幕常亮/音量键翻页/智能切边/主题色/深色跟随」**均不成立**（`okhttp-dnsoverhttps` 只是躺在依赖里）。

### 4.4 下载 / 本地漫画（阶段 7）：0% 接入

- **注意原版并不用 WorkManager**：`ImagesDownloadTask` 在 Dart 层按 `downloadThreads`(默认 5) 并发，逐图 `_ImageDownloadWrapper` 重试 3 次(download.dart:519-524)，写 `LocalManager.path/<directory>/<章节目录>/` + `.nomedia`(local.dart:192-199)；SAF 走 `flutter_saf SAFTaskWorker`(init.dart:50、local.dart:663)；任务快照落 `downloading_tasks.json`，启动时 `restoreDownloadingTasks()`(local.dart:298,532-537)；完成转 `toLocalComic()` 入库。导入导出：CBZ 306 / EPUB 209 / PDF 407 / import_comic 428 行。
- Compose：无 WorkManager/Worker/前台服务/通知权限/队列持久化/zip/SAF；`AndroidManifest.xml` 只有 `INTERNET`+`VIBRATE`（**清单层面下载体系就不成立**）。首页「本地漫画」卡的「已缓存 14 部作品 · 0 个下载任务」(:591) 与「管理本地 ›」(:599 `clickable {}`) 全静态；详情页下载钮 onClick 仅振动(:952-955、:1006)。
- 决策点：**照抄 Dart 层自管并发**（行为 1:1）还是**按总纲改用 WorkManager**（系统可靠但断点续传/暂停恢复语义要重建）。当前文档写的是后者，代码连前者都没有。

### 4.5 WebDAV 同步 / 备份恢复 / 规则导入导出（阶段 8）：0% 接入

- 原版 `DataSync`(utils/data_sync.dart 230 行)：配置 `settings['webdav']=[url,user,pass]`、开关 `implicitData['webdavAutoSync']`；文件名 `epochDay-dataVersion.venera`，同日覆盖、>10 份删最旧(:131-152)；**远端 version 大于本地才导入**(:204-212)；收藏夹/源库变更即 `uploadData`(:23-37)；同步排除 `_disableSync` + 自定义 `disableSyncFields` → `syncdata.json`。
- `exportAppData` 打 zip：`history.db + local_favorite.db + appdata.json + cookie.db + comic_source/*.js`(utils/data.dart:18-42)；`importAppData` 关库→替换→重新 init(:47-112)；`importPicaData`(:115-268) 哔咔旧库迁移并重建 `folder_sync` 链接。
- Compose：无 webdav 依赖、无 zip、无 `dataVersion` 概念、无网络收藏、无账号。**一旦启用同步，现有 schema 与原版不兼容 → 必然要求重库/迁移方案**（见 4.2 的 PK 与分页游标差异）。

### 4.6 统计 / 图片收藏 / 追更 / 屏蔽词

| 功能 | 原版 | Compose |
| :--- | :--- | :--- |
| 阅读统计 | `addReadingStats`(history.dart:434-453) 阅读器累加；`dailyPages`/`pagesSince`/`readDates`/`topComics`/`comicsByType`/`readTagRows` → `stats_page` **1211 行**（30/365 天、月度趋势、时间轴、连续打卡） | 无表无写入；首页 42/286/12天(:441/447/453)、「6 部漫画 428 张图片」(:622)、标签·画师分布(:653-655) **全是字面量** |
| 图片收藏 | `quickCollectImage` + reader/scaffold 4 处增删 + `computeImageFavorites`(>100 走 Isolate) + 4 页面(541/289/253/101) | 零实现，无 `image_favorites` 表 |
| 追更 | `follow_updates.dart` 191（`last_check_time` 节流 + `UpdateProgress` 流 + `getUpdatedComicsAsJson`，`headless.dart:179,229` 可外部触发）+ 页面 600 + `countUpdates/getUpdates/markAsRead` | 只有 `hasUpdate:Boolean` + `latestChapter:String` 两列，无时间戳、无周期任务、无红点页；「追更」仅是 UI 过滤字符串(:1536) |
| 屏蔽词 / 分级 | `isBlocked`(comic.dart:1222-1247)+列表剔除(:1313-1322)、`blockedCommentWords`、`blockedTags/Artists/Comics`+3 闸、`ContentGuard`(359 行：nsfwMaskStrength/unlocked/forced/sourceWarningOverride + R18 正则表)、`blockAiWorks`、UI 654 | **设置键、模型字段、过滤函数、设置页四项全缺**；设置卡「内容屏蔽与过滤」是空 `clickable`(:1792-1812) |

### 4.7 本域必须先修的 6 个硬伤

1. **主线程全表扫描**：`HistoryDao`/`FavoriteDao` 的 `init{} → refresh()` 跑在构造线程；阅读器每翻一页 `LaunchedEffect` 触发 `saveHistory` → 再全表重读(VeneraReaderScreen:124-139 + HistoryDao:33-35) ⇒ **O(全表)/页**。
2. `comic_history` PK 无 `type` ⇒ 跨源同 ID 互相覆盖；且 `onUpgrade` 空、VERSION=1 ⇒ 加列即破坏性升级。
3. CookieJar 按 host 分桶丢 domain/path 语义 ⇒ 子域 cookie 不下发（会连带打挂源登录与 CF 过盾）。
4. **8/10 偏好键无消费者**，DoH/代理是假开关（见 4.3）。
5. **三块零引用死代码**：`ComicSourceDao`(109) / `QuickJsBridge`(164) / `BitmapSliceHelper`(133) = **406 行写了但没接上**。
6. **DB 记录回退到 MockData**：历史/收藏条目用 `sampleComics.find { it.id == record.comicId }` 取元数据(MainActivity:473,1593)，首页/探索/分类仍直出 `sampleComics`(:349,499,1498,1675) ⇒ 真实记录只要不在假数据里就丢标签/评级/章节。

---

## 5. 页面 / 屏幕层差距（逐页审计）

**规模**：原版 `lib/pages/**` = 52 文件 / **30318 物理行**；Compose 页面层 = 2653 行 ≈ **8.8%**。

| 原版页面文件 | dart 行数(物理/非空) | 核心功能点 | Compose 现状 | 对应 Compose 位置或缺口 |
| :--- | :--- | :--- | :--- | :--- |
| `main_page.dart` | 171/156 | NaviPane 6 标签 + 嵌套 Navigator + `settingsEntry` 位置切换 + `initialPage` | 🟢部分 | `MainActivity.kt:177-294` + `components/VeneraFloatingNavBar.kt`；无路由栈、无设置入口位置开关 |
| `home_page.dart` | 2477/2310 | 今日推荐→追更、历史、漫画源、阅读统计、本地/导入、图片收藏、同步卡片 | 🟡mock桩 | `MainActivity.kt:326-704`；6 分区齐全但 `onTap = { }` 空(344/431/467/577/599/617)，统计硬编码 42/286/12 天(441-455)，本地/图片收藏硬编码(591/622/653-655) |
| `search_page.dart` | 1324/1230 | 搜索建议、多源选择与源管理、URL 直读、标签栏、结果模式、分页、历史持久化 | 🟢部分 | `MainActivity.kt:1291-1527`；真调 `sourceManager.search`(1325)，但历史是内存 `mutableStateListOf`(1302)，无建议/分页/标签/源管理 |
| `search_result_page.dart` | 544/492 | 单源结果页：排序、标签过滤、分页、语言检测、建议浮层 | ❌缺失 | 无文件；`getOrDefault(emptyList())`(1326) 一次性铺平无分页 |
| `aggregated_search_page.dart` | 271/246 | 每源一个 Sliver，先返回先展示，占位骨架 | ❌缺失 | `ComicSourceManager.kt:87-95` 用 `awaitAll().flatten()` 等全部完成，与「流式逐源」语义相反 ✔本喵复核 |
| `categories_page.dart` | 720/678 | 按源分 Tab、动态分类矩阵、排行榜区、Tag 点击进分类漫画 | 🟡mock桩 | `MainActivity.kt:1702-1738`；4 组 19 个标签写死(1703-1708)，标签不可点 |
| `category_comics_page.dart` | 226/209 | 分类漫画瀑布流 + 多维筛选(排期/风格/字数) + 分页 | ❌缺失 | 无文件；`ComicSource` 接口无 `getCategories`/`getCategoryComics` |
| `explore_page.dart` | 765/688 | 每源 explore_widget 多 Tab、FAB 刷新、自动翻页、历史页缓存 | 🟡mock桩 | `MainActivity.kt:1632-1697`；5 个 Tab 名写死(1637-1643)，数据来自 `sampleComics`(1675)，`getExploreComics` 全库 0 调用 ✔本喵复核 |
| `ranking_page.dart` | 94/85 | 源内榜单类型切换 + 列表 | ❌缺失 | 无文件、无接口 |
| `comic_details_page/comic_page.dart` | 1523/1422 | LoadingState 详情、封面/信息/动作/简介/章节/缩略/推荐/评论 8 段 | 🟢部分 | `MainActivity.kt:709-1244`；真拉 `getComicDetails`(747)+`getChapterPages`(759)，但无缩略/推荐/错误态，封面评分仍 mock |
| `comic_details_page/chapters.dart` | 379/346 | 普通/分卷双模式、分组 Tab、已读置灰、长按批量下载 | 🟢部分 | `MainActivity.kt:1075-1204`；仅扁平列表+正倒序(1095-1126)，无分卷/已读标记/选章下载 |
| `comic_details_page/comments_page.dart` | 578/540 | 首屏/分页加载、点赞、楼中楼、发表、屏蔽词过滤 | ❌缺失 | 数据层无 Comment 模型（`ComicSourceModels.kt` 仅 Comic/Chapter/Details/Pages） |
| `comic_details_page/comments_preview.dart` | 175/163 | 详情页 2 条评论预览 + 查看全部 | 🟡mock桩 | `MainActivity.kt:1206-1237`；两条评论与 "(128)" 计数全是字符串常量 |
| `comic_details_page/favorite.dart` | 626/577 | 收藏面板：本地/网络双区、文件夹选择/新建、追更开关 | ❌缺失 | 只有 `toggleFavorite`(965) 直写默认文件夹；`FavoriteDao` 的 folder 能力(109/148) 无 UI |
| `comic_details_page/actions.dart` | 468/445 | 阅读/续读、下载、收藏、评论、分享、评分、标签点击、更多菜单 | 🟡mock桩 | `MainActivity.kt:933-1028`；5 钮仅收藏/开始阅读有实现，下载/分享/评论/`···` 只触发振动 |
| `comic_details_page/cover_viewer.dart` | 140/131 | 全屏封面查看 + 保存到相册 | ❌缺失 | 详情页仅 104×144 缩略(833-849)，无放大页 |
| `comic_details_page/thumbnails.dart` | 360/341 | 首话预览缩略条 + 分页续拉 | ❌缺失 | `ComicDetails.thumbnails` 字段已存在但**无任何消费点** |
| `favorites/favorites_page.dart` | 187/171 | 本地/网络双栈 + 侧栏文件夹 + 搜索 + 多选 + 追更入口 | 🟢部分 | `MainActivity.kt:1529-1628`；真 `favoritesFlow`(1534)+文件夹过滤(1564)，但 Tab 名写死(1536)、条目回落 `sampleComics`(1593) |
| `favorites/local_favorites_page.dart` | 1227/1162 | 关键词搜索、多选、下载、删改、手动排序 | ❌缺失 | 无文件；`FavoriteDao` 无 order 字段/排序 API |
| `favorites/network_favorites_page.dart` | 598/553 | 单/多文件夹源收藏、登录态、同步 | ❌缺失 | 无文件；`ComicSource` 接口无 favorites 方法 |
| `favorites/side_bar.dart` | 303/285 | 左栏文件夹树（本地+网络）、拖拽/滚动联动 | ❌缺失 | 无文件 |
| `favorites/favorite_actions.dart` | 510/479 | 新建/重命名/删除文件夹、移动、批量弹窗、导出 | ❌缺失 | 无文件 |
| `history_page.dart` | 338/315 | 今天/昨天/更早分组、多选删除、进度描述 | ❌缺失 | 无页面；仅首页横向条(463-517) 读同一 Flow，`HistoryDao` 删除/分组 API 无 UI |
| `follow_updates_page.dart` | 600/552 | 未配置引导、更新列表、排序、立即检查 | ❌缺失 | 无文件；无 last-read-chapter 比对逻辑 |
| `local_comics_page.dart` | 642/614 | SAF 导入、本地书架、多选、排序、`.cbz` 导出 | ❌缺失 | 无文件；Manifest 无 SAF/FileProvider |
| `downloading_page.dart` | 254/235 | 任务列表、进度、暂停/取消、并发数设置 | ❌缺失 | 无文件、无 WorkManager 依赖 |
| `stats_page.dart` | 1211/1124 | 周/月/总览、柱状图折线图、_tag 统计、分享图 | ❌缺失 | 无文件；首页统计卡为常量(441-455) |
| `comic_source_page.dart` | 1357/1262 | 源列表、启停、编辑、删除、导入 `.js`、更新、帮助 | ❌缺失 | 首页只有一块 ping 卡(519-572)；Dao 与 QuickJsBridge 全库零引用；3 源硬编码 |
| `image_favorites_page/*` (4 文件) | 541+289+253+101 | 图片收藏网格、排序/时段筛选、大图 Gallery、单元卡片 | ❌缺失 | 无文件、无 `ImageFavorite` 表；首页假统计(614-697) |
| `reader/reader.dart` | 850/722 | 页状态机、imagesPerPage、跨章翻页、音量键、历史写回 | 🟢部分 | `VeneraReaderScreen.kt:68-566`；章节切换(454-484)+历史自动写回(124-135) 真实；无 imagesPerPage/音量键 |
| `reader/scaffold.dart` | 1186/1104 | 顶栏、底部 Slider、章节抽屉、存图/收藏/分享、侧边设置 | 🟢部分 | `VeneraReaderScreen.kt:340-495`；顶栏+Slider(422-435)+上下章真实；无章节抽屉/存图/侧栏 |
| `reader/images.dart` | 1459/1338 | 5 种排版（连续纵/RTL/LTR/连续横/双页）、预加载、翻页动画 | 🟢部分 | `VeneraReaderScreen.kt:265-300`；`ReaderReadingMode` 只有 2 值(`ComicPageSource.kt:34-37`)，无 RTL/双页/预加载；`BitmapSliceHelper` 零引用 ✔本喵复核 |
| `reader/chapters.dart` | 242/226 | 章节抽屉列表 + 分卷视图 + 当前定位 | ❌缺失 | 无文件 |
| `reader/comic_image.dart` | 445/387 | 自绘 ImageProvider、分块渐进解码、反色、错误重试 | 🟢部分 | `VeneraReaderScreen.kt:499-566` 用 Coil 直铺；无 Referer/反色/切片/重试 |
| `reader/chapter_comments.dart` | 877/835 | 章内评论嵌入、发表、点赞、屏蔽词 | ❌缺失 | 无文件、无 Comment 模型 |
| `reader/gesture.dart` | 373/337 | 可配点击区、双击缩放、长按保存/复制、拖拽翻页 | 🟡mock桩 | `ReaderZoomState.kt` 仅缩放；点击区/长按菜单全缺 |
| `reader/loading.dart` | 122/106 | ReaderProps + 首屏 loading 骨架 | ❌缺失 | 无文件；无加载中态 |
| `settings/settings_page.dart` | 389/359 | 左分类右详情双栏、7 分类路由、桌面/移动双布局 | 🟡mock桩 | `MainActivity.kt:1742-1814`；7 张分类卡 `clickable { }`(1793) 空实现 |
| `settings/setting_components.dart` | 945/835 | Switch/Select/滑条/输入/颜色 等 15+ 共享控件 | ❌缺失 | 无 settings/components 包；`VeneraPreferences` 9 键仅 `defaultReadingMode` 被读 ✔本喵复核 |
| `settings/appearance.dart` | 675/652 | 主题色、深色模式、字体、设置入口位置 | ❌缺失 | `ThemeMode` 无 UI 写入，`MiuixTheme{}` 用默认值(168) |
| `settings/reader.dart` | 617/590 | 阅读模式/间距/常亮/音量键/切边/章评位置 | ❌缺失 | `pageGapDp`/`keepScreenOn`/`volumeKeyTurn`/`autoCropBorders` 无设置页也无消费方 |
| `settings/network.dart` | 376/357 | 代理类型、DoH 开关、自定义 DNS | ❌缺失 | `VeneraNetworkClient` 有 DoH/proxy 构造参数但无 UI 配置源 |
| `settings/app.dart` | 637/617 | 启动页、日志页、WebDAV 设置与自动同步 | ❌缺失 | 无任何同步代码 |
| `settings/blocking_settings.dart` | 654/604 | 屏蔽词/正则、强度、生效源、评论屏蔽 | ❌缺失 | 无文件、屏蔽未接入搜索/详情 |
| `settings/explore_settings.dart` | 380/365 | 每源探索 Tab 增删排序、主页显示开关 | ❌缺失 | 探索 Tab 硬编码(1637-1643) |
| `settings/about.dart` | 323/309 | 版本、检查更新、开源许可 | 🟡mock桩 | `MainActivity.kt:1759-1789`；「检查更新」(1781) 与「GitHub 源码」(1784) 无点击逻辑 |
| `settings/local_favorites.dart` | 73/70 | 本地收藏行为开关 | ❌缺失 | 无文件 |
| `auth_page.dart` | 71/64 | 源登录 WebView 回调 + cookie 落库 | ❌缺失 | 无文件；`PersistentCookieJar` 无登录入口驱动 |
| `webview.dart` | 372/328 | Cloudflare 过盾 WebView、cookie 回注 | ❌缺失 | 无 WebView 依赖与 Activity，过盾链路整体缺失 |

### 5.1 页面补齐优先级 Top 12（按依赖顺序）

| # | 目标 | 阻塞了谁 | 预估 Compose 行数 |
| :--- | :--- | :--- | ---: |
| 1 | 导航骨架：`Navigation` + `sealed Route` + 统一栈（替代 177-294 的三变量切换） | 39 个无入口页面 | 200 |
| 2 | ViewModel 基座 + Coil `Application` 注入 Referer/Cookie | 所有真数据页（详情/阅读/探索/搜索/收藏） | 180 |
| 3 | 设置控件库 + 首个设置子页 `appearance` | 其余 6 个设置子页、8 个未消费偏好 | 420 |
| 4 | `ComicSource` 接口扩展 + `ComicSourcePage`（分类/榜单/收藏/登录 + 接线 Dao/QuickJS + `.js` 导入） | categories、category_comics、ranking、explore、network_favorites、auth、webview | 450 |
| 5 | Comment 模型 + `comments_page` + `comments_preview` | 详情页评论段、章评、屏蔽词设置 | 340 |
| 6 | `category_comics_page` + `categories_page` 真接 | `ranking_page`、搜索标签栏、复用网格 | 280 |
| 7 | 聚合搜索逐源流式 + 单源结果分页/排序 + 历史持久化 | `search_page` 收尾、标签直达、屏蔽词验证 | 330 |
| 8 | `ExplorePage` 真接 `getExploreComics` + 分页刷新 | 首页探索入口、`explore_settings` | 240 |
| 9 | `HistoryPage` + `FollowUpdatesPage` | 首页两张卡、统计源、追更红点 | 300 |
| 10 | 收藏体系（面板 + 侧栏 + 文件夹动作 + 本地收藏页） | 详情收藏钮、选章下载、网络收藏、WebDAV | 520 |
| 11 | 阅读器补完（RTL/双页/连续横 + 预加载 + 切片接线 + 章节抽屉 + 点击区） | 详情起读、章评、时长统计 | 620 |
| 12 | 下载与本地（WorkManager 队列 + 下载页 + 本地漫画 + CBZ） | 详情下载钮、首页本地卡 | 470 |

> 排在 12 项之后：`stats_page`、`image_favorites_page/*`、`ranking_page`、`auth_page`、`webview.dart`、其余设置子页。

---

## 6. 修正后的推进顺序（取代原「8 阶段串行」）

原路线图 1→2→…→8 与代码实际、以及真实依赖关系都不吻合（阅读器早在阶段 1 之前就提交了，而阶段 2 的脚本引擎其实没做）。**建议按 5 个波次推进**，每波结束都是一个可安装、可验证的 APK。

| 波次 | 内容 | 为什么必须在这一波 | 验收 |
| :--- | :--- | :--- | :--- |
| **W0 地基手术**（不加新功能） | ① `navigation-compose` + `sealed Route`，把 `MainActivity.kt`(1814) 拆成 `feature/*`；② 每屏 `ViewModel` + `StateFlow<UiState>`；③ 新建 `VeneraApp : Application` 并在 Manifest 注册；④ Coil 自定义 `ImageLoader`（OkHttp fetcher + 按域注入 Referer/UA/Cookie）；⑤ 修 4 个硬伤：主线程全表读、`comic_history` PK 补 `type`、`onUpgrade` 空实现、DB 记录回退 `sampleComics` | 不做这 5 件，后面每加一屏都往单文件堆债；防盗链图源永远 403 | 拆分后构建通过；首屏/详情/阅读真链路回归；拷贝漫画封面可见 |
| **W1 脚本引擎（原阶段 2.5）** | §2.7 的 12 步，前置是第 0 步引擎 spike | 原版 33 源全靠它；不做则「按原项目复原」不成立，手写爬虫是永久负债 | 官方 `manga_dex.js` / `copy_manga.js` / `baozi.js` **原样加载**跑通搜索+详情+读图 |
| W1.5 网络中间件 | Cloudflare 过盾（WebView + `cf_clearance`↔UA 绑定回写）、`prevent-parallel`、429 退避、把 DoH/代理**真正接进** `OkHttpClient`、MangaDex @home 限速合规 | W1 装上就立刻需要；同时把「假开关」变实 | 需 CF 的源首开可用；设置改代理/DoH 后请求行为真的变 |
| **W2 页面补全** | 按 §5.1 优先级 3→10：设置控件库 → 源管理页 → 评论 → 分类/榜单 → 聚合搜索流式 → 探索真接 → 历史/追更页 | 依赖 W0 的导航/ViewModel 与 W1 的能力槽（分类/收藏/账号方法在接口里都还不存在） | 逐页对照原版截图核验；`sampleComics` 引用计数降到 **0** |
| **W3 阅读器 + 收藏（原阶段 4+6）** | 5 种排版（RTL/LTR/连续横/双页）+ 前瞻预加载 + `BitmapSliceHelper` 接线 + 章节抽屉 + 可配点击区 + 音量键 + 反色；收藏面板/侧栏/文件夹动作/本地收藏页 | 阅读器地基已在（`a39829b`），补的是体验；收藏体系是 W4 网络收藏与同步的前置 | 超长条漫不 OOM；页漫/条漫双模式可用；多收藏夹可增删改排序 |
| **W4 下载·本地·同步·统计（原阶段 7+8）** | 先定「自管并发 vs WorkManager」；CBZ/EPUB/**PDF** 导入导出；WebDAV `dataVersion` 双向同步 + 备份 zip；`read_stats` 表 + `stats_page`(1211 行等价物) | 必须最后做：schema 依赖前面所有实体字段补齐，否则同步一开就要重库 | 息屏/断点续传下载；多端同步冲突策略；统计图表与原版对齐 |
| W5 打磨发布 | Baseline Profiles、R8 收缩、冷启动与帧率基线、`contentWarning` 分级门禁、屏蔽词全局生效、84 设置项对齐 | 发布前必做，且依赖功能完整 | 基线报告 + 设置项 1:1 清单 |

### 6.1 需要你先拍板的 4 个决策

1. **脚本引擎**：Zipline（省掉大半桥接工作量，但引入 Gradle 插件、官方自称使用 unstable API）↔ 自建 quickjs JNI 封装（可控，但要养 native 构建）。§2.0 的反编译证据已说明**继续在 0.9.2 上打补丁是死路**。
2. **数据栈**：补 Room + KSP 正式化（与总纲一致、迁移安全）↔ 承认手写 SQLite 并接受其升级/同步约束。
3. **下载栈**：照抄原版应用层自管并发（行为 1:1）↔ WorkManager（系统可靠，但暂停/恢复/置顶语义要重建）。
4. **Desktop 模块去留**：`desktop/` 1513 行纯 Mock，与 Android 侧**零共享代码**。要么抽 `shared` 真做 CMP，要么明确降级为 UI 原型脚手架（别让它继续冒充进度）。

---

## 7. 可复现的验证手册

| 事项 | 命令 / 方法 |
| :--- | :--- |
| 构建 | `$env:JAVA_HOME="D:\jdk17\jdk-17.0.20.1+1"; .\gradlew.bat :app:assembleDebug`（当前 HEAD 实测 BUILD SUCCESSFUL 1m30s，APK 24.5 MB，2 条警告） |
| 真机链路 | 安装后验证：详情页真拉数据 → 阅读器出图（**拷贝漫画/包子当前必 403**，因为 headers 无消费者，这条能直接证明 G-3） |
| 死代码复核 | 全库搜 `QuickJsBridge` / `BitmapSliceHelper` / `ComicSourceDao` 的引用点：应只见自身定义 |
| 引擎能力复核 | 反编译 `~/.gradle/caches/modules-2/files-2.1/app.cash.quickjs` 的 AAR，`javap app.cash.quickjs.QuickJs` 只有 evaluate/set/get/compile/execute |
| 原版协议复核 | 读 `.reference/flutter-master/doc/js_api.md`（逐函数）与 `assets/init.js`（JS 侧实现）、`lib/foundation/js_engine.dart:112-205`（19 method dispatcher） |
| 源清单复核 | `curl https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/index.json` → 33 条目 |
| 覆盖率复核 | dart `lib/` 59859 行 vs kt `app/` 4979 行 = 8.3% |

---

## 8. 本次核查做了什么（工作区变更清单）

1. 把 `D:\venera\.git` 镜像进本工作区并 `reset --hard` 到 `compose-migration` HEAD，**工作区首次成为真正的 git 仓库**（原先只有一个 mock 原型文件、不是 git）。
2. 从 `master` 分支解出原版 Flutter 只读参照到 `.reference/flutter-master/`（143 dart + assets + doc + shaders），并写入 `.git/info/exclude`。
3. 修订 `venera-migration-plan.md`：新增「现实校准」章节，纠正 Room/DataStore/MVI/QuickJS 四处虚标、阶段 1→🟡 部分完成、阶段 2→已提交但 2.3 重开为 2.5、阶段 4→地基已在、阶段 5/7 补语义与选型警告、产物路径与工作区基准。
4. 重写 `README.md`（原文件被转义符啃坏：`top.yukonga` 变成 TAB+`op.yukonga`、`app/` 变 `pp/`），并改为反映真实现状与真实进度表。
5. 新增本文件 `venera-gap-analysis.md`。

> 尚未提交 git。建议首个提交：把 `venera-gap-analysis.md` / 修订后的 `venera-migration-plan.md` / `README.md` 一起入库，作为后续所有波次的基准。
