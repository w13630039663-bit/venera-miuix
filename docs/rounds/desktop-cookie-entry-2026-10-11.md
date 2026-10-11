# 批 88·下半 · 手填 cookie 那一形落地（罐接盘 → 校验 → 入口 → 取证种子）（2026-10-11）

上一批（批 88）量完"轻路没有对象"之后留下五条开工单（`desktop-web-login-verdict-2026-10-11.md` §4），
这一批把 (c) 那一形接到桌面上：**先把那颗罐落到盘上，再开粘贴的入口**，顺序没倒着走。
`app/` 一字未动（受保域）。

## 0. 一句话

**落盘那一半是本批的地基**：`markLoggedIn` 那格（`JsSourceDataStore.account`）早就落盘，而凭据那颗罐是
`ConcurrentHashMap` ⇒ 两半不同轨的形状正是假开关（屏上念「已登录」而重启后请求空手）。
所以第一格是 `DesktopCookieJar`，第五格才是包与帧。

## 1. 落地形状（每条都能回代码）

| 那一格 | 落在哪 | 口径出处（不是这一批定的） |
|---|---|---|
| 罐接盘 | `engine-probe/…/DesktopCookieJar.kt`（`:34` 那颗件；`:112` 写、`:141` 读、`:159` 坏档留档） | 三条口径逐字照对岸 `PersistentCookieJar`：桶按**请求 host** 分、`name + domain + path` 三者全同才算同一枚（新的赢）、登出**内存与盘一起清** |
| 落点 | `DesktopCookieJar.fileIn(dataDir)`（`:58`，全仓**唯一**一处写那个文件名 —— 判据 ⑧ 数它） | 批 85 那条纪律：件不自己选目录；递进来的目录随数据根走 ⇒ 隔离数据根把这罐一起隔离 |
| 不建目录 | `load()` 起手 `if (!store.isFile) return`（`:141`） | 批 86 那条"算落点不建目录"：读数不许自造证据 |
| 坏档 | 挪一份 `.bad-<毫秒>` + 念一行 `D_凭据罐 读=坏档 落点=…`（`:159`） | 画廊历史那颗同一处理（不留档 = 用户那句「我明明登录过」查不回去） |
| 宿主接线 | `DesktopJsHost.kt:51` 换成 `DesktopCookieJar(…)`，那颗 `InMemory` 纯内存罐**从树上删掉** | 判据 ⑦ 两头都数：那一颗构造恰好一处 + `:engine-probe` 整片里那颗旧名字 0 处（两轨不许） |
| 校验 | `EngineSession.loginWithCookies(values)`（`:321`）跑脚本自己的 `account.loginWithCookies.validate(values)`，`env["success"] == true` 且 `data.ok` 才 `markLoggedIn` | 信封判式照对岸 `JsComicSource.validateCookies`（`JsComicSource.kt:1838`）；"先 set 再 validate"由**脚本自己做**（ehentai 的 `validate` 里就带 `deleteCookies` + `setCookies`），桌面不代做 = 不留第二条轨 |
| 登录态落盘 | `DesktopJsHost.markLoggedIn`（`:159`）= `dataStore.saveAccount(key, "ok")` | 逐字照对岸 `ComicSourceManager.kt:1365-1367`，`isLogged` 那一问读的就是这一格 |
| 名单 | `DesktopSourceNames.cookieFieldsOf(script)`（`DesktopSourceCatalog.kt:57`，正则 `:62`） | 名单住在脚本里（`fields: [...]`），桌面**一个字都不重述** —— 判据 ③ 反过来数：四枚真名在两份 main 里 0 处 |
| 入口 | `DesktopComicSourcesScreen.kt:161` 那一屏：名单非空的行才摆「填凭据」那一枚，摊开是**每枚字段一格**，值走 `PasswordVisualTransformation`（不回显） | 批 88 工单第 3 条「只摆给得出的源」；`:63` 那句实话点名是"内嵌 WebView 那一形桌面没有宿主" |
| 回核 | `DesktopCookieLogin.run` 之后 `DesktopCookieJar(fileIn(dataDir)).tally(fields)`（`:101`）—— **另起一颗只读实例现读盘上那份** | 源回 `true` 只说明站方认了；"重启带不带得上"只有现读盘上那份才算量过（内存那份是刚才那一趟写的，拿它作证等于自己给自己签字） |
| 取证种子 | `--cookie=<host>:<名>=<值>`（`ForensicFlags.kt:578`）→ `DesktopJsHost` 第 4 枚必填参数（`:44`）→ `applySeedCookies`（`:126`）→ `DesktopCookieJar.seedFromRequest`（`:83`，**永不进 persist**） | 罐按请求 host 分桶 ⇒ 种子递的就是被量那一层的形状；只并本次装载（批 83.1 同口径：取证参数不许改用户的登录态） |

## 2. 三档句子里最要紧的那一档（这一批存在的理由）

`DesktopCookieLogin.screenNote`（`:91`）三档两两不同：

- 过了且落了盘 ⇒ 「已登录：…共 N 枚，重启也带得上」
- **过了但盘上一枚没落** ⇒ 「…一份都没落下来 —— 重启就带不上，先别当已经登录」
- 没过 ⇒ 「没登上：<源自己的那句说法>」

中间那一档是"降级路径不许静默交错"那条口径在这一格上的形状：站方点头 ≠ 本机记住。
判据 ⑦ 钉的就是"屏上绝不能在 `landed == 0` 时念出『已登录』那三个字"。

## 3. 顺序为什么不能倒

工单第 1 条写"先把落盘补齐再开入口，否则第一格就是假开关"。本批实际执行时另有一格同因：
入口那一屏**刻意不起引擎**（`desktopComicEngineNeeded` 里 SOURCES 那档是 false），
所以粘贴那趟是"按下登录那一刻才付的钱"（`DesktopCookieLogin.run` 自起宿主、`finally` 关掉）。
这也顺手保证了**登录用的那颗源就是用户点的那一颗**，而不是当前在用的那一颗 —— 拿在用的那颗去校验等于登错门。

## 4. 台架 `_qa/b88low_perturb.js`：21 笔（20 笔要求红 + 1 笔等价改写要求不红）

收尾全量（每轮跑完都复跑一次干净全量再认数）：**`EXIT=0` / `1193 判据 / 145 类 / 0 失败` / 三方对得上**
（`suites=145 tests_sum=1193 testcase=1193 @Test=1193 files=152 failures=0`），
`_qa/b88low_current.txt` 归 0，`git diff --stat -- app/` 空 —— **没有一笔打向受保域**
（名单那两条负向判据要红只需在桌面 main 里重述一次字段名单，P12 干的就是这件事）。
累计 **28 条 RED 断言**：14 笔一次咬住，六笔各同时红两三把（P1、P4、P9、P14、P17、P21），
另两笔**第一跑交出绿**，改判/收紧后复跑才咬住 —— 那一格才是台架的价值所在。

⚠️ **P1 的第一版是废笔**（编译面就红）：把 `gson.toJson(snapshot())` 改成
`gson.toJson(emptyMap<String, List<Map<String, Any?>>>())` 那种写法编不过，
`:desktop:test` 根本没跑，而驱动那句"要求红"照样报绿 —— 手册 §0 坑 ㉚ 那一族（**扰动要编译得过才算判据抓到**）在本仓的第一次自踩。
换成"写完不挪过去"（`Files.move` 那一道撤掉）之后：`写完盘上根本没有那颗文件` + 种子那一把同时红。

**知识①（这一批最值钱的一格）：一条判据可能由两道闸一起守，"撤一道不红"不等于判据是假的。**
P10 想量的是"换了请求的 host 就带不上上一桶的凭据"（`DesktopCookieJarTest` ②）。两个形状各跑一遍都绿：

- 撤**分桶**而留 `matches(url)` ⇒ 绿（域 cookie 的 domain 对不上那颗 host，`matches` 自己就挡了）；
- 撤 `matches` 那道过滤而留**分桶** ⇒ 绿（上一桶根本取不出来）。
- **两道一起撤**才红：`另一颗 host 的请求被喂上了上一桶的 cookie expected:<0> but was:<1>`。

所以 ② 那条的身份要说成"**两道闸（按 host 分桶 + RFC6265 的 matches）各自都够**"，
而不是"漏一道就漏凭据"。这正是手册 §0 坑 ㉞ 那一族的新形态：**新量具"能证到哪一格"要由扰动笔量出来**，
量不到的那一格要把"其实有两道闸"写进判据注释，否则下一个人会拿撤一道去证明它坏了。

**知识②：对照组与判式共用同一句读数，判式就恒真。**
P15 撤掉 `--cookie` 空值那一句 `println` 之后照绿 —— 因为我那条 assertTrue 核的是
"captured 里有没有 `D_取证参数 --cookie`"，而**报数那一句**（`收到=3 可用=1`）恒含它。
正解是把判式收紧到"**哪一档自己那句话**"（`--cookie 那一串是空的`），顺带把"不带值"那一档也补进来（`--cookie` 只给名字时
`removePrefix` 原样返回串，从前会被当成一枚值 —— 现在两档同句出声，与 `disable` 同形）。
收紧之后 P15 立刻红（量具第一次咬住这一格）。坑 ㉙ 的近亲：**判据被自己的另一条输出喂饱**。

**知识③（白捡的一格）：树上有"只剩测试引用"的守卫。**
P14 把屏上那句「网页登录那一形桌面没有宿主」撤掉之后，红的不是这一批新写的尺子，而是
`DesktopUnusedInternalTest` ①③ —— `desktopWebLoginAbsentLine` 变成只有测试在引用它。
这条同时说明：把句子做成**一颗件**（而不是内联在屏上）除了防"两处各写一遍"，还多了一道"没人念就红"的保险。

**知识④：修完的缺陷要用**同一串参数**复跑一遍，才算那一格闭上。**
帧 B 抓到的是"报 2/2 而真进罐 0 枚"，修法把解析搬进 `DesktopCookieJar.seed(raw)` 并让汇总句念 applied。
复跑（`_qa/b88low_frameB3.log`，参数与帧 B **逐字同一串** `-Pcookie=exhentai.org:ipb_member_id=42|…`）：
`D_凭据罐 种子=2/2 盘=未写 seeded=2/2`，而 `D_凭据种子 … 当不成 URL` 那两行**一个字都不再出现**
（`grep -c` 现量 0）。⚠️ 只改代码不复跑等于把"修好了"写在墙上 —— 那一格从前是**离屏全绿而屏上报假数**，
只有同一串参数再走一遍真窗口才闭上。

**知识⑤：屏上"只有一颗摆格"有两种成因，量掉那一种才许写下这一句。**
帧 A 里 34 行只有 `ehentai` 摆得出「填凭据」—— 这句话既可以读成"只有一颗源能填"，也可以读成
"另两颗在折叠线以下而我的正则认不出它们"（后者是**假阴性**，属于"实现了但接线漏"那一族）。
现量的读数：随包 34 颗 js 里 `loginWithCookies` 声明 **3 颗**（`ehentai`/`lanraragi`/`mycomic`），
而 `COOKIE_FIELDS` 那一条正则在同一批脚本上**命中 3/3**（字段分别 4 枚、`apiKey`、`cf_clearance`）
⇒ 屏上那一枚没摆错，另两行只是滚出画面。所以 §5 那句要写成"能产出的有 3 颗，帧里看得见 1 颗"，
而不是"桌面只有一颗源能填凭据"。

## 5. 图证（两帧 + 一帧刻意没拍）

起窗一律隔离数据目录（`LOCALAPPDATA=_qa/b88low_local`）+ `--no-daemon`（JavaExec 会继承 daemon 的旧 env），
用完按 PID `taskkill`，收尾残留 0 扇。脚本 `_qa/b88low_frame.sh`。

- **帧 A（源管理那一屏，`-PstartDomain=COMIC -PstartSection=comic-sources`）**：屏上念得出
  「清单共 34 颗，这一台机器上一颗都没停用」、那句实话
  「网页登录那一形靠的是应用内嵌的浏览器页面，桌面没有那一层宿主；这里只有手工粘贴凭据这一形。」，
  而 34 行里摆得出「填凭据」的那一行是 `ehentai`（其余看得见的 33 行没有 —— 名单空就不摆格那条判据在真窗口里成立）。
  ⚠️ 帧里只滚得到三颗能产出源之一，见知识⑤（3/3 那条现量）。不崩、那一枚与「停用」同排同尺寸。
- **帧 B（漫画域探索 + `-Pcookie=` 两枚）**：这一帧是**本批最值钱的一格**，因为它抓到一个离屏全绿的缺陷 ——
  stdout 同时念出 `D_凭据罐 种子=2/2 盘=未写` **和**两行 `D_凭据种子 那枚 host 当不成 URL：exhentai.org`：
  报的是"解析了几枚"，而真进罐的是 **0 枚**。裸 host 在 OkHttp 那里解不成 URL（要带 scheme），
  桥里 `handleCookie` 早就有 `"https://$urlStr"` 那一兜而种子这一路没抄。
  ⚠️ 离屏那 14 条一条都不红 —— 它们都从 `seedFromRequest(url, …)` 起，正好**绕过了"串 → url"那一环**
  （手册 §0 坑 ㉞ 那一族的新形态：**量具能证到哪一格要由扰动笔与图证量出来**）。
  修法：解析搬进 `DesktopCookieJar.seed(raw)`（带 scheme 兜底、剥前导点、**空名摘掉** —— 名为空那一枚
  OkHttp 的 Builder 不报错，进了罐就是请求头里多一段 `=v`，而桥里 `buildCookie` 同一条判据），
  汇总那一句改念 **applied**；补判据 ⑮ 与台架 P21（撤掉那一兜必须红）。
- **同一帧顺手量到"罐真落盘"**：跑完 jm 探索之后数据根里多出 `cookies.json`（739 B，站方回的 5 枚
  `www.cdnhjk.net`），而**种子的值一个字都不在里面** ⇒ 「只并本次装载、不写盘上那份」在真窗口里成立
  （从前那一格只有离屏读数）。
  复跑那一趟又补了两道独立佐证：`cookies.json` 的 **mtime 仍是上一趟的 15:56** 而本趟 16:43 结束
  （同形状≠同时刻 ⇒ 拿 mtime 量"这一趟有没有写盘"），以及**真人那份 `%LOCALAPPDATA%\venera\` 里
  `cookies.json` 一颗都没有**（隔离目录 + `--no-daemon` 那道前提在本批仍然成立）。
- **刻意没拍的一帧**：点「填凭据」摊开那几格。那一下要 OS 级点击，而 §8.1 的边界是"不许动用户光标/前台"；
  那一格由离屏语义树量过（`hasSetTextAction()` 数出两枚输入框、真填两枚值、按登录、交出去的次序）。
  ⚠️ 本批收尾时确实为这一格另起过一趟（`_qa/b88low_frameC.log`），到屏后**只核了残留 0 扇便按 PID 关窗，一个字都没点**
  —— 记这一笔是为了不让下一批把它读成"忘了拍"：那是守边界，不是漏拍。

## 6. 没做与量不到（如实记）

- **源那句 `validate` 至今没在桌面真跑过一次**：要真凭据 + 真网络，而屏上那一枚按不动（不许 OS 级点击）。
  所以本批只许说"**接缝通到脚本那一句，且信封判式照对岸**"，不许说"手填 cookie 这条路已经登进去过"。
- **`ccc` 那一族要的是 WebView `localStorage` 那两枚 token**：桌面桥有没有那条路**仍未量** ⇒ 那一族今天仍归 (a)，
  没给它摆格子（脚本里没有 `loginWithCookies` 的源一律不摆）。
- **账密那一形没接**（`jm.js` 与 `CopyMangaSource` 的 `loginWithPassword`）：工单第 3 条要的是"只摆 (c) 给得出的源"，
  账密是 (c) 的另一支，另排一批。
- 桌面 main 里 `WebView` 仍 0 处（批 88 那条负向照旧咬）；`jdk.httpserver` 那半条前提**没被推翻也没被用**：
  本批一行都没往 `modules(...)` 里添。
- Gelbooru / SauceNao 那两把 key 走画廊侧那份凭据件，与源登录**两条轨没并一颗**（批 88 那条要求照旧成立）。
- 挂着等点头的两条不变：源脚本导入、`ImageHeaderPolicy.kt:67,101`。**MSI 与正式包名那两枚 2026-10-12 用户定档** ——
  「等那些包完全做好之后再动」⇒ 它不再挂在"等人拍"那一列，改挂在"等前置收口"那一列（判据：包内起窗那一格量过、
  `-Psave=1` 与 `-Pdetail=1` 两趟在**交付物**里核过账）。

## 7. 交付包（工单第 5 条：本批动了 `:engine-probe` ⇒ 必须重打）

`./gradlew :desktop:createDistributable --rerun-tasks` 59 秒（`11 actionable tasks: 11 executed`）
→ 拷进 `_qa/desktop-exe-2026-10-11-b88low/`（215 MB）。
`node _qa/check_cfg.js <包>/app`：`classpath=100 missing=0`、`orphan_jars=0`。

⚠️ **这一批把 `_qa/check_pkg_jar_sha.js` 从"只核 `desktop.jar`"扩成两颗都核**（`desktop` + `engine-probe-*.jar`），
因为本批换掉的正是 `:engine-probe` 那颗 —— 旧尺子对它是**盲的**：包里的 engine-probe jar 哪怕还是上一批那一颗，
它也照样报"对得上"。这是手册 §0「量具与目标错配」那一族在本仓的又一次现身：
**判据核的是"我手上有的那颗产物"，而不是"这一批改的那颗产物"**。
现量（两颗都对得上，字节逐字相同）：

| 那颗 | 包内 | 大小 | sha256 前缀 |
| --- | --- | --- | --- |
| desktop | `desktop-b8c44534f0631b8ef5b8ad318889262.jar` | 4,745,374 B | `3b876664…` |
| engine-probe | `engine-probe-ae823d3bc2bcebf9bbbad29a925481d.jar` | 146,825 B | `269a5321…` |

包内起窗那一格本批**没做**（帧 A/B/C 都走 `:desktop:app`，即类路径而非交付物）；
交付物里"罐接盘"这条路径的读数仍只有上一批那一条（`D_凭据罐 读=…` 在包内未量），
下一批若要动包内凭据面，得连这一格一起补。



