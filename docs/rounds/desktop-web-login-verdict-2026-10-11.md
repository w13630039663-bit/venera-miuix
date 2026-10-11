# 批 88 · 桌面「网页登录」那一格：把那条前提量掉（2026-10-11）

**产品面零改动**（`desktop/src/main` 与 `app/` 一字未动），落的是**六条尺子 + 三处文档改判 + 下一批的开工单**。
排期表那一行（`remaining-plan-and-reuse-2026-10-10.md` §4 的「批 88 网页登录轻路」）自己写着
「先量轻路够不够用」—— 量完的结论是：**那半条轻路没有对象**，而真接缝早就在桥里，缺的是入口与落盘。

---

## 1. 对岸那一族今天有几形（每条都能回代码）

| 形 | 谁在用 | 决定的那一行 | 桌面能不能搬 |
| --- | --- | --- | --- |
| **(a) 内嵌 WebView 抓 cookie** | ehentai / ccc / manga_dex / mycomic / nhentai（脚本里 `loginWithWebview`） | `WebLoginScreen.kt:130` `CookieManager.getInstance().getCookie(url)`；成败靠**导航过程的 url/title 序列**（`JsComicSource.kt:1787-1794` 调 `loginWithWebview.checkStatus(url, title)`） | **搬不过来**：那颗 `CookieManager` 是 `android.webkit` 的平台件，桌面没有那层宿主（`CloudflareBypassActivity.kt:91,170,217` 同族，那是过盾不是登录） |
| **(b) 本地端口等回调** | **没有人** | 全仓（含 `app/`）`ServerSocket` / `NanoHTTPD` / `com.sun.net.httpserver` **各 0 处**；唯一的 `999999` 命中是 `CopyMangaSource.kt:48` 那串**设备 ID 随机数**，不是端口 | **没有对象**：站方不往 localhost 重定向，对岸也从来没等过那种回调 |
| **(c) 手工粘贴 cookie / 账密 / API key** | ehentai（`fields: ["ipb_member_id","ipb_pass_hash","igneous","star"]`，`ehentai.js:99-104`）、mycomic（`cf_clearance`，`:233`）、CopyManga 账密（`CopyMangaSource.kt:354-393`）、jm 账密（`jm.js:356-360`） | `ComicSourceViewModel.kt:300-310` `loginWithCookies(...)` → `src.validateCookies` 过了才 `markLoggedIn` | **能搬，而且落点已经在**（见 §3） |

顺带量到的一条**来路**：`ccc.js:142-154` 那两枚 token 是从 WebView 的 **localStorage** 里取的（`WebLoginScreen.kt:96-115` 那条快照路径），
所以对岸三形里 **ccc 那一族今天只能归 (a)** —— 手填那形给不出它要的 `accessToken/refreshToken`。

## 2. 那条轻路为什么不成立

排期那一行把「`Desktop.browse` + `jdk.httpserver` + 补 `modules(...)`」当成最省事的替代方案。逐条量之后：

- **`jdk.httpserver` 那一半**：没有一条流程在等回调（§1 (b) 那一行）。往 `modules(...)` 里添那颗模块，
  就是**给一条不存在的流程买票** —— 而且它是 jlink 那格的常客（批 82.9 的 `java.sql` 那一课刚付过账：清单里多一颗、少一颗都要现量）。
- **`Desktop.browse` 那一半**：系统浏览器里登进去，cookie 留在那台浏览器自己的罐里，
  本机**没有任何一条对岸流程会去拿它**；而"程序自己去读浏览器的 sqlite 罐"这一条是桌面**已拍板的红线**
  （`DesktopGelbooruCredentialPanel.kt:48`：那是把用户没声明来源的会话凭据搬到另一台机器上）。
  所以 `browse` 至多是"陪着人去看登录页"，它不是登录。
- ⇒ **JCEF 那一半也没被排期写成成因，但它被 README 当成了唯一成因**（§6 第 1 处改判）。

结论：这一格的正解不是"轻路 vs JCEF"那一档，而是**把 (c) 那一形接到桌面上** —— 它不需要 WebView、不需要端口、
不碰红线，而桌面这一侧的接缝今天就在。

## 3. 桌面这一侧今天有什么、缺什么（逐条行号）

**已在**（`:engine-probe`，`DesktopJsHost.kt`）：

- 桥认 `cookie` 那三档：`"get"`（`:306`）/ `"set"`（`:313`）/ `"delete"`（`:318`）—— 源脚本要的那三问都接得住；
- 桥认 `isLogged`（`:267`），背后是**盘上那份** `JsSourceDataStore(File(dataDir, "comic_source"))`（`:59`）⇒ `markLoggedIn` 写的那格 `account` 今天就在落盘；
- 那颗罐挂在客户端上：`InMemoryCookieJar()`（`:45`）→ `.cookieJar(cookieJar)`（`:48`）⇒ 写进去的 cookie 真会随请求发出去；
- `buildCookie`（`:326-344`）已经把站方那两种脏形状处理掉了：**前导点**（`while (d.startsWith("."))`，`:332`）、脏域（空格/斜杠/冒号/分号/逗号/@、纯数字点分串 ⇒ 当无效），
  而且**逐条容错**（坏的那枚跳过，整批不废）。

**缺的**只有两样：

1. **入口**：桌面「源管理」那一屏没有"手工粘贴 cookie"那一格（也没有源自己的字段名单上屏 —— 那份名单在脚本里，见 §1 表第三行）；
2. **落盘**：那颗罐是 `ConcurrentHashMap`（`:373`）**内存级** —— 今天**重启就丢**，而 `isLogged` 那格却已经落盘。
   这两半不同轨的形状就是**假开关**：屏上写着「已登录」而请求带不上凭据。

## 4. 下一批的开工单（(c) 那一形落地）

按 §3 那两样排，顺序不能倒（**先把落盘补齐再开入口**，否则第一格就是假开关）：

1. **把罐接盘**：桌面自持一份（与批 85/86 同一条处理 —— 对岸那颗 `PersistentCookieJar` 落的是 SharedPreferences，那颗件在 `app/` 且吃 `Context`）。
   ⚠️ 接盘时 `DesktopWebLoginVerdictTest` ② 里那条**现状读数**要**改账而不是删掉**：
   它的最后一条断言（"换一颗罐就读不到"）今天为真，接盘后为假 ⇒ 换成「内存与盘上必须同一颗件（两轨不许）」，
   并把用例名改成它那时真测的那件事。
2. **调 `validateCookies`**：`EngineSession` 已经有那颗私有 `call(script)`（`EngineSession.kt:113`，四处在用），
   加一枚"递 cookie 串进去、按源的 `loginWithCookies`/`validateCookies` 跑一遍"的方法即可 —— **口径不许在桌面重述**：
   要哪几枚字段、成功判据是什么，都从脚本现取（`fields: [...]` 那份名单），与批 83 那把 `name = "…"` 的读法同一族。
3. **入口上屏**：「源管理」那一屏每源一行"填 cookie"，**只摆 (c) 那一形给得出的源**（脚本里没 `loginWithCookies` 的源不许摆一枚按不动的框），
   并念一句"网页登录那一形桌面没有宿主"（那句必须是实话且点名 §1 (a)）。
4. **取证种子**：这一族最后那一跳要人手工粘贴 ⇒ 照 `--disable=` / `--reverse-file=` 那一族补一枚 `--cookie=<站>:<名>=<值>`，
   只并进本次装载、不写盘上那份（与批 83.1 同一口径：取证参数不许改用户的状态）。
5. 交付包要重打（这一批动了 `:engine-probe`，包内那颗 jar 会换）。

## 5. 本批落的六条尺子

`DesktopWebLoginVerdictTest` 5 条（新）+ `DesktopReadmeStaleClaimsTest` ③（新增一条）：

| # | 钉的是 | 扰动 |
| --- | --- | --- |
| ① | 本地服务那三颗名字在 `desktop/src/main`、`:engine-probe`、构建脚本模块清单里各 0 处（名字**拼出来**写的 —— 判式自己含那串就恒红，坑 ㉜ 第三次用到） | P1（模块清单添 `jdk.httpserver`）、P6（main 里长出 `ServerSocket`） |
| ①′ | 对照：桥那三档 `"get"/"set"/"delete"`、`"isLogged"`、`.cookieJar(` 都在代码行里（走 `codeText` —— 注释里写一句"没有罐"不算有罐） | P2（`"delete"` 换名） |
| ② | **真跑**那颗罐：同一枚键写第二遍仍是一枚且新的赢 / 清掉就空 / 跨 host 不串 / 换一颗罐读不到上一颗（**现状读数**，接盘时改账） | P4（去掉去重那一行）、P3（`clearForUrl` 成空操作）、P5（跨 host 兜底交回全表） |
| ③ | 那条红线那句不许漂 + 对照「手工粘贴在对岸本来就有」（`loginWithCookies`） | P7b（红线那句换成待定的说法） |
| ④ | (a) 那一形的引证（对岸 `CookieManager.getInstance().getCookie`）还在，而**桌面 main 里 `WebView` 0 处** | P7（main 里长出 `WebView`） |
| ⑤ | README 中英两份那一格：`JCEF` 那串两份各 0 处，而那一条必须点名「内嵌 WebView」那一形与「重启」那一档；对照走 `codeText` 读桥里那颗罐还挂在客户端上 | P8（中文那份退回旧那句） |

台架 `_qa/b88_perturb.js` **10 笔**：9 笔要求红 → 全红（各红自己那一条，报错文本命中该笔锚点）；
P9 是等价改写（`isLogged` 那一档先取局部再交回）**要求不红而确实一笔没红**；收尾全量 `EXIT=0`、
`1171 / 143 / 0`、`_qa/b88_current.txt` 归 0、`git diff --stat -- app/` 空（**没有一笔打向受保域** —— ④ 那条对岸引证要红只需在桌面 main 长出 `WebView`）。
**耗时（给画像表补一个下界）**：10 笔 08:24:42 → 08:36:15 = **≈12 分半**（每笔 ≈1 分 20 秒），
单独复跑 P4 + 收尾 08:38:07 → 08:40:30 = **≈2 分 30 秒**。比"改 main 的批"（每笔 ≈1 分 28 秒、14 笔 ≈33 分）快，
原因是这一批改坏的全是 `:engine-probe` 与构建脚本，**桌面 main 一字未动** ⇒ 每笔的编译面几乎全 UP-TO-DATE。

⚠️ **P4 第一次交出的是绿**，而且不是实现的问题：那颗罐只被写过**一枚**，
"同名不去重"这种缺陷在只写一遍的样例上永远数不出来（手册 §0 坑 ⑲ 那一族的**第五次**，也是"样例绕不过判式"这条的老债）。
补的形状是**再写一遍同一枚键**（名/域/径三者同、值不同），断言罐里仍是一枚且值是新的那一枚 —— 补完 P4 立刻红（`expected:<1> but was:<2>`）。

## 6. 三处文档改判

1. `README.md:258` / `README.en.md:258`：那句「WebView 登录要 JCEF —— 桌面两样都没引入」**把结构性缺口归给了一颗没引入的内核**，
   而真缺口是 §2 那两半（动线在平台件里 + 那颗罐不落盘）。两份同轨改成点名那一形与那一档（有尺子：⑤）。
2. `desktop-port-scope-verdicts-2026-10-10.md` 那一行 9b：`[术]` 那半句「桌面这一侧 JCEF 没引入 ⇒ 真缺口」改成
   「缺口是 (a) 那一形的宿主与 (c) 那一形的入口/落盘；JCEF 只是 (a) 的一种可能实现，量过之后不是必须先买的那一张」。
3. `remaining-plan-and-reuse-2026-10-10.md` §4 那一行「批 88」：`jdk.httpserver` 那一半划掉并写明没有对象，
   落地那一格改排下一批（本文件 §4 的开工单）。

## 7. 量不到 / 没做的（如实记）

- **没有起窗、没有帧**：本批产品面零改动，屏上没有一句因此变化（先例是批 84′）。
- **交付包没重打**，但不是"没管"：`_qa/check_pkg_jar_sha.js _qa/desktop-exe-2026-10-11-b87low/venera-desktop-probe`
  现量包内那颗 `desktop-dd9743f5c7bb309c0eee7bf9d1f5ea6.jar`（4,711,544 B）与现产物 `desktop/build/libs/desktop.jar` **sha256 逐字同一颗**（`59c8dcd8327df1e3…`）
  ⇒ 交付物与当前 main 同步这一格是**有读数的**，不是推断。下一批动 `:engine-probe` 时必须重打（§4 第 5 条）。
- **`validateCookies` 今天没在桌面上跑过一次**：`EngineSession` 那枚私有 `call(script)` 能不能带住"先 set cookie 再 validate"这一步
  要动手才知道（§4 第 2 条排的就是这一问）。所以本批不许写"手填 cookie 这条路已经通了"，只许写"接缝在"。
- **ccc 那一族的 localStorage 那两枚 token**：桌面桥今天有没有 `localStorage` 那一路**没量**（§1 只量到对岸是从 WebView 取的）。
- **Gelbooru / SauceNao 那两把 key 不在这一格**：它们走画廊侧那份凭据件，早已是"填了就用"的形状（批 34/42），与源登录两条轨，不许并一颗。
