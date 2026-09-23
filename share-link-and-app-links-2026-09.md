# 分享带原站链接 + 应用链接（App Links）直达详情 · 2026-09-23

用户点名两件事：①分享要"标题 + 漫画在原站的链接"，现在只分享标题；②开启应用链接，让系统设置里
能管理本 app 支持的链接，点了直接跳详情页。并指定"这个功能 master 应该有"。

## 零、先纠正一个前提（master 的真相，已核）

**master 没有"分享链接"这个 JS 钩子。** 全树 `git grep -in sharable master` → 0 命中，
`doc/comic_source.md` 里也搜不到 分享/share。协议只给了**反方向**的 `comic.link.linkToId`
（url → id，`doc/comic_source.md:637-654`）。master 的分享全在 Dart：
`master:lib/pages/comic_details_page/actions.dart:89-110`，口径是
`text = comic.title`，`if (link.isNotEmpty) text += '\n$link'` —— 即 `"<标题>\n<url>"`，
无 url 时**就只有标题**。那个 url 取自详情返回字段 `ComicDetails.url`（`models.dart:180`，可空），
源没给时回落到一张**硬编码站点表**（`actions.dart:92-105`）：只有 `nhentai → https://nhentai.net/g/{id}/`
和 `jm/18comic → https://18comic.vip/album/{id}` 两条，其余源 `=> ''`。
注释里写明了为什么没有"网站根地址"这种通用兜底：源对象的 `url` 字段是**脚本下载地址**，
永远不是站点根（`comic_source.dart:175`）—— 这条我们也一样（`source/ComicSource.kt:22-23`
与 `ComicSourceManager.kt:1399-1404` 的 `RepoIndexEntry.url` 都是下载地址）。

**所以①不是"接一个我们漏掉的钩子"，而是"把 `details.url` 填上 + 把兜底表做成能用的规模"。**

## 一、我们这边的现状（已核，含"实现了但零调用点"型缺口）

| 环节 | 现状 | 证据 |
| --- | --- | --- |
| 分享文案 | `【标题】\n作者：x\n` + `details?.url ?: ""` —— **链接那一行早就在，只是恒空**；且没加载完详情时也是空 | `feature/ComicDetailScreen.kt:1768-1773`，调用点 `:671-684` 与顶栏 `:1454-1471` |
| `ComicDetails.url` | 字段有（`source/model/ComicSourceModels.kt:98`，默认 `""`），但只有 JS `loadInfo` 回了 `url` 才有值 —— 启发式扫 **13/33** 个源；native 三源构造时**一个都不填** | `source/js/JsComicSource.kt:587`；`source/baozi/BaoziMangaSource.kt:110-124`、`source/copymanga/CopyMangaSource.kt:214-227`、`source/mangadex/MangaDexSource.kt:169-184` |
| id→url 模板 | 仓库里唯一已成体系的域名/路径知识是**反方向**的 `data/network/ComicUrlMatcher.kt`（9 条硬编码正则 → sourceKey+comicId），已接到搜索框点条目跳详情（`feature/SearchViewModel.kt:123,252` → `feature/SearchScreen.kt:337-345`） | 见该文件 `:18-146` |
| JS `comic.link`（domains+linkToId） | 8 个内置源已声明（ehentai/hcomic/hitomi/jcomic/manga_dex/mycomic/nhentai + lanraragi 注释样例），**Kotlin 侧零读取 = 死数据** | `assets/sources/ehentai.js:1429-1442` 等 |
| 入站链接 | **完全没有**：manifest 只有 1 个 intent-filter（MAIN/LAUNCHER），无 `<data>`/`scheme`/`BROWSABLE`/`autoVerify`；`MainActivity` 从不读 `intent.data`，也没有 `onNewIntent`；全仓 `navDeepLink` 0 命中 | `app/src/main/AndroidManifest.xml:27-30`；`MainActivity.kt:26-41` |
| launchMode | 未声明 = 默认 `standard`（master 用 `singleTop`）→ 从浏览器跳一次压一个新栈 | `AndroidManifest.xml:23-26`；`feature/SettingsHost.kt:139` 注释依赖默认值 |
| ACTION_SEND 收文本 | master 有（收到文本走聚合搜索，`MainActivity.kt:51-77` + Dart `handle_text_share.dart`），我们无 | — |

## 二、能力边界：autoVerify 这条路走不通，但"系统设置管理链接"照样能做

已核官方文档（`developer.android.com/training/app-links/verify-applinks`）：验证成功的硬条件是
域名的 `https://<host>/.well-known/assetlinks.json` 由**该域名的控制者**发布，内容含本 app 的签名证书指纹。
我们不拥有 `nhentai.net` / `18comic.vip` / `e-hentai.org`，**这些域永远验证不过**。
文档同时写明第三-party 域的后果：不会被自动设为默认处理者，
"用户必须手动把域名与你的第三方应用关联"（Settings 里开）—— **而这正是用户要的那条路**：
只要声明了 `VIEW` + `BROWSABLE` + `DEFAULT` 的 https intent-filter，域名就会出现在系统
"打开支持的链接 / 支持的网页地址"清单里，用户手动开启后即可直接跳进来。

结论（建议，待拍板 P1）：**不写 `android:autoVerify`**。理由有二：
1. 写了也只会得到一个"验证失败"状态，换不来任何直达能力；master 同样一条都没写
   （`git grep -n autoVerify master -- android` → 0 命中）。
2. Android 15+ 会周期性后台重验（最长 7 天生效），对我们是纯噪声。
   代价：未验证域首次点击大概率仍会经过浏览器/系统那一次询问（Chrome 顶栏"用应用打开"或选择器），
   用户选"始终"后即持久生效 —— **这条以真机为准，见 §六验收步骤，不要凭文档断言。**

本仓库 `minSdk=33 / targetSdk=37`，即所有设备都在 Android 13+，Android 12 的严格规则一律适用。

## 三、①的方案：分享链接三层取值

1. **`details.url` 优先**（源给的真链接，口径与 master 一致）。native 三源补上这个字段：
   拷贝漫画 `https://copymanga.com/comic/{id}`、包子 `https://cn.baozimh.com/comic/{id}`、
   MangaDex `https://mangadex.org/title/{id}` —— 三条**逐字取自 `ComicUrlMatcher` 已有的域名与路径**，
   不新造格式（见下条同源化理由）。
2. **表兜底**：`ComicDetails.url` 为空时按 `sourceKey + comicId` 拼。表不从零写 ——
   与 `ComicUrlMatcher` **合并成一张双向表**（一个源一条：域名列表 + 路径模板 + 反向正则），
   分享侧用模板，入站侧用正则。分成两张表的必然结果是口径漂移（域名换镜像时只改一处）。
   覆盖范围先按 matcher 现成的 9 条（拷贝/包子/MangaDex/B站/禁漫/EH/nhentai/wnacg/Hitomi），
   是否再加 picacg（用户实际在用，但 matcher 与 JS `link` 都没有它的格式）→ 见 P2。
3. **两层都空 → 就只分享标题**（等于今天的现状），但**不再留那个尾随空行**：
   现在是 `"\n" + (details?.url ?: "")`，无链接时尾巴挂着 `\n`。照 master 的
   `if (link.isNotEmpty)` 收口。

顺带（同一处代码，不做第二条实现）：分享入口现在有两个（顶栏钮 + 辅助行），共用一个 helper，
保持一份；辅助行加"复制链接"（写 `ClipboardManager`）是否要 → P3。

## 四、②的方案：入站链接直达详情

### 4.1 解析器两级（源驱动优先，硬编码兜底）
- ⚠️ **先记一条结构性约束**：`intent-filter` 的 host 是**静态清单**，系统只会把 URL 交给我们
  声明过的域。所以"第一级走源 `link.domains`"只在**该域已写进 manifest** 时才可能被触发 ——
  它解决的是"同一个域用哪个格式解 id"，不解决"用户自导的新源也能点进来"。
  后者只有两条路：把域名逐个写进 manifest（受审核与包体无关，但每加一源要发版），
  或我们自己拥有一个域名做跳转页（发布 `assetlinks.json`，链接形状变成 `我们的域/?u=…`）。
  本轮按前者做，后者不碰。
- **第一级照 master**：遍历**已安装**源，命中 `comic.link.domains` 就用 `linkToId(url)` 换 id。
  实现上抄本轮刚落的批量钩子样板（`source/js/JsComicSource.kt:803-853` +
  `ComicSourceManager.kt:1028-1065` 的 LruCache/`withTimeoutOrNull` 超时/失败回空不冒错），
  **一条 JS 脚本问完整批源**，不逐源往返（引擎是 WebView、`evaluateJavascript` 全 post 主线程）。
- ⚠️ **不照抄 master 的 bug**：它在第一个域名匹配但 `linkToId` 返回 null 时直接 `return false`
  中断整个源循环（`utils/app_links.dart:27`），后面的源再也轮不到。我们继续遍历。
- **第二级兜底**：双向表的正则（覆盖 native 三源与没 `link` 块的 B站/wnacg）。

### 4.2 落地链路
- `AndroidManifest`：`.MainActivity` 加 `android:launchMode="singleTop"`（照 master `:13-16`），
  并为**每个要支持的域**加 `<intent-filter>` + `<data android:scheme="https" android:host=... android:pathPrefix=...>`。
  域名清单见 P4。⚠️ `pathPrefix` 会进 `AppInfo`，master 用的是站点路径段（`/g`）这一档。
- `MainActivity`：`onCreate` 读首启 `intent.data`，`onNewIntent` 读热启；两处统一塞进一个
  **"取用一次即清"的一次性槽位**。⚠️ 本仓库已知的坑：退场/返回时导航条目会**重新组合**，
  槽位若不清会二次触发跳转（见 `project-nav-entry-recomposition` 那类返工），
  且这类载荷只能放叶子目的地 —— 深链的目标正是详情页（叶子），成立。
- 导航：解析结果 → `DetailRoute(comicId, sourceName)`（`feature/Navigation.kt:133`），
  NavHost 已是 type-safe（`:396-401`），首启时 startDestination 之后立刻 `navigate`，
  热启时同理；`launchMode=singleTop` 保证不压第二份栈。
- ⚠️ **必须一起修的静默错源**：`ComicDetailViewModel.kt:938-942` 的源解析是
  `key 或 name` 忽略大小写匹配，**都不中就用 `activeSourceKey`**。列表点击时那没问题
  （名字总是对得上），但深链带进来一个**未安装/已删的源**时，这条回落等于
  **拿另一个源的同 id 漫画开详情** —— 那是完全不相干的一本，且没有任何提示。
  深链路径改成严格解析，源不在就如实提示"未安装该漫画源"，不猜（宁可错慢，不可静默交错）。

## 五、拍板记录（2026-09-23 用户点选）

- **P1 → 不写 `autoVerify`**，走"系统设置里手动开启该域"这条官方路径。
- **P4 → 声明双向表全部域**（定稿 10 源 / 33 条 host×pathPrefix 声明位）。
- **P2 → 再挖一轮 picacg**：第一轮**负向**（三条路都没依据），第二轮被用户给的链接推翻，
  现在 picacg **有**分享链接 —— 见下，那段把两轮依据与"为什么只声明一个域"都记全了。
- **顺带三项全做**：尾部空行收口 / 「复制链接」/ `ACTION_SEND` 收文本→搜索。

### P2 第一轮是负向、第二轮被用户给的链接推翻 —— picacg 现在**有**分享链接
**第一轮（我查的三条路都没有依据）**：
- 本地 `app/src/main/assets/sources/picacg.js`：只有 `defaultApiUrl = "https://picaapi.picacomic.com"`（`:12`）
  与一个注册页 `manhuabika.com/pregister/`（`:88`），**没有 `comic.link` 块**。
- 上游 `venera-app/venera-configs@main/picacg.js`：同样没有 `link/domains`；出现的
  `/comics/random`、`/comics/advanced-search` 全是 API 端点。
- master 的硬编码分享表里也没有 picacg。
→ 当时按"无依据就不编格式"处理，picacg 只分享标题。

**第二轮（用户给出 `https://manhuabika.com/comics/creator/58f649a80a48790773c7017c`，据此逆向）**：
那个站是 **PicaWeb**（Vite + React Router 的纯客户端 SPA，抓 HTML 拿不到链接，所以走渲染与
读它自己的 bundle 两条路）。三条依据凑齐才写进表：
1. **它自己的路由表**（`assets/js/index-ea_4Germ.js` 原文）：单本详情是
   `path:"/comic/:comicId"`（**单数**），兄弟路由 `"/comic/:comicId/comments"`、
   `"/comic/reader/:comicId/:order/:readerType?"`；而 `/comics`、`/comics/tag/:value`、
   `/comics/creator/:value`、`/comics/search`、`/comics/random`、`/comics/leaderboard/...`
   **全是列表页**。→ 单复数一混就会把列表页当漫画，正则与 `pathPrefix` 都按这条收口。
2. **点一本验证实体 URL**（用户已登录，我点的）：`https://manhuabika.com/comic/6a98591797fc3345c60aa7f1`，
   id 是 24 位 hex；**把这个地址单独打开**（不走站内跳转）详情容器照常渲染、无加载失败
   → 它是真 permalink，不是只有客户端状态。
3. **id 同形**：`picacg.js:96` 是 `id: comic._id`，与路由参数同一个 24 位 hex ObjectId
   → 我们手上的 comicId 直接就能拼出可点开的地址，不需要额外换算。

落地：表里加 `picacg` 一条（模板 `https://manhuabika.com/comic/%s`），manifest 加
`manhuabika.com` + `www.` 两域、`pathPrefix="/comic/"`（带尾斜杠，`/comics/…` 就吸不进来）。
单测锁三件事：分享↔识别双向一致、`reader`/`comments` 子路径认回同一本、
`/comics/creator/<id>` **不许**被认成一本漫画。

⚠️ **只声明了 manhuabika.com 这一条域**：它出现在用户给的链接里、也出现在 `picacg.js:88`，
是唯一有依据的那个。哔咔网页版镜像常年漂移，而 intent-filter 是静态清单 —— 换域必须发版，
所以真机若发现别的可用域，记回来再加（分享侧同理：模板域名跟着这条走）。

## 六、本轮明确不做

- 不自建域名做跳转页（那是唯一能让 `autoVerify` 真正验证过的路子，需要发布 `assetlinks.json`，
  与"分享原站链接"是两件事）。
- 不给用户自导源做动态 intent-filter：**intent-filter 是静态清单**，新源要能点进来必须改 manifest 重发版。
  §4.1 那条结构性约束就是这个意思。
- 不动 `ContentGuardManager`、不动 `FavoritesScreen`（冻结声明第三批）。

## 七、落地记录（2026-09-23，已编译 + 单测通过；真机未验）

### ① 分享链接三层
- `data/network/ComicUrlMatcher.kt` → **改名 `ComicUrlTable.kt` 并升成双向表**：一个源一条记录，
  `patterns`（认链接）/ `shareTemplate`（拼链接）/ `appLinks`（manifest 声明位）同源。
  匹配用的正字符逐字保留 S4 时期的写法（收紧会打断搜索框直达）。
- `ComicDetailScreen.shareLink()`：`details.url` 优先 → 表兜底 → **null**。
  `shareText()` 改成按行拼，无链接时不再留 `"\n" + ""` 那个尾随空行。
- 「复制链接」= 辅助行第五个钮（收藏/点赞/评论/分享/复制链接），
  `shareLink` 为 null 时**如实提示"这个源没有提供漫画页地址"**，不把空串写进剪贴板冒充成功。
- ⚠️ 顺手修了一条会让双向表自相矛盾的：EH 的正原本来要求 `/g/{gid}/{token}`，
  而分享拼不出 token（master 也只拼到 `/g/{gid}/`）→ 我们分享的链接自己认不回来。
  把 token 组改成**可选**，双向一致才成立（单测 `分享拼出的链接能被同一张表认回来` 就是锁这条）。

### ② 入站两级解析：顺序与 master 相反
- `source/ComicLinkResolver.kt`：**表优先，JS 兜底**。表是纯字符串匹配零成本；JS 引擎在主线程串行，
  能不吃就不吃。第二级不是冗余：源自己认的路径比我们的正则宽
  （`hitomi.js:1605` 认 `hitomi.la/<类型>/<名>-<id>.html` 全部类型，表里只写 `reader|galleries`）。
- `JsComicSource.resolveComicLinkHits(url, hosts)`：**一条脚本遍历 `ComicSource.sources` 全部源**，
  一次主线程往返（任意一个 JsComicSource 实例都能问全表，脚本读的是引擎级注册表）。
  两处刻意不照 master：master 在"域名匹配但 linkToId 回 null"的第一个源上就中断整循环
  （`app_links.dart:27`），这里收全部命中回 Kotlin 挑；master 只拿 `uri.host` 精确比对，
  域名清单多半写裸域，这里把去 `www.` 的变体一起喂进去。
- `ComicSourceManager.resolveComicLink(url)`：超时 `COMIC_LINK_TIMEOUT_MS = 8s`，
  与缩略图那级同口径；超时/抛错回**空列表**并留 Warn（这条的空结果会被消费侧如实提示，
  不像预览条那条可以原样退化）。

### ③ manifest 与 Activity
- `.MainActivity` 加 `launchMode="singleTop"`（master 同值）。**这条是必需项不是优化**：
  缺它时每点一次链接都新建一个 Activity 实例，返回栈里堆出好几层 app。
  ⚠️ 已知边界：`singleTop` 只在 MainActivity **正在栈顶**时复用实例。设置子树是另两个 Activity
  （`SettingsActivity`/`SettingsSubActivity`），若点链接时设置页正在上面，系统会在其上再建一个
  MainActivity —— 表现为"链接那本压在设置页上面"。master 同值同行为，本轮不改（改 `singleTask`
  会清掉设置子树那层，而"每页真换一个 Activity"正是预测式返回动画的前提）。真机若觉得别扭再议。
- 10 个源一组 `intent-filter`（`VIEW` + `DEFAULT` + `BROWSABLE` + `scheme=https` + 各自 host/pathPrefix），
  共 33 条声明位；`ACTION_SEND` + `text/plain` 一条（filter label「搜索」，仓库无 strings.xml，
  与 `android:label="venera-miuix"` 同一处写法）。
- **没写 `autoVerify`**，且 `ComicUrlTableAppLinkScopeTest` 用正则把这条钉住（连属性名都不许出现）。

### ④ 交接：通道，不是一次性槽位
- 冷启动 `onCreate` 与热启动 `onNewIntent` 都只往 `EntryIntentHandoff` **投消息**；
  NavHost 侧 `LaunchedEffect(Unit) { for (entry in …) handleEntryIntent(...) }` **常驻接收**。
- ⚠️ 一开始按 `SettingsEscapeHandoff` 那套"一次性 var + 进组合读一次"写的，那是错的：
  `singleTop` 热启动**不重新进组合**，那个 Effect 不会再跑 → 表现成
  "只有冷启动那一次有效，app 开着时点链接毫无反应"。缓冲队列把冷启动那条也接住了。
- 链接认不出/源没装/源没就绪 → Toast 说清是哪一种，**不猜源**。
  这条判据同时收口了搜索框那侧一个既存静默错开：表里有 `bilibili` 条目但本仓库压根没有这个源，
  原先粘贴 B 站链接会点进"当前活动源的同 id 漫画"。现 `SearchViewModel.matchedInstalled()`
  与解析器同一判据 —— 源没装就不出那张卡。

### ⑤ 待真机确认（只读，不代操作；不凭文档下结论）
1. **分享**：禁漫 / EH / 拷贝 / **哔咔**各一次，要求文案第三行是真链接，且**在浏览器里能打开对应漫画页**；
   另挑一个表里没格式的源（`goda`、`mh18` 都是现成样本），要求它**只分享标题、不带尾随空行**。
2. **复制链接**：有链接的源弹"已复制链接"、粘出来就是那条地址；表里没格式的那本要弹
   "这个源没有提供漫画页地址"，不能把空串复制出去冒充成功。
3. **哔咔专项**（第二轮逆向的成果，必须单独验）：从 app 分享出去的 `manhuabika.com/comic/<id>`
   在浏览器里要落在**详情页**；反向把站内那条 `/comics/creator/<id>` 列表页链接粘进搜索框，
   要求**不出**"识别到链接"那张卡（单复数混了就说明正则写歪）。
3. **直达**：`adb shell am start -a android.intent.action.VIEW -d "https://18comic.vip/album/<真id>/"`
   分别打**冷启动**与**app 已开着**两种状态（后者正是 ④ 那条坑的场景：一次性槽位收不到）。
   要求落到正确详情页、返回不弹回首页、返回栈里不堆出第二层 app。
4. **系统设置**：确认「打开支持的链接 / 支持的网页地址」清单是否按域列出这 33 条声明位，
   以及一加 ColorOS 的入口名称与 AOSP 是否不同（OEM 相关，别按文档下结论）。
5. **未验证域首次点击的实际路径**：浏览器顶部「用应用打开」条 / 每次询问的选择器 / 完全不提示。
   三种都是系统行为，我们改不了，只能把观测结果记回来再定下一步。
6. **没装源的那条**：粘一条 B 站漫画链接 → 现在不该出"识别到链接"那张卡；
   从浏览器点进来（若该域已声明）→ 要看到「未安装漫画源…」的 Toast，而不是静默停在首页。
