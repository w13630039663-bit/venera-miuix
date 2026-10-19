# pixiv 账号系统（OAuth2 PKCE）方案与接缝（2026-10-17）

> 用户 2026-10-17 点名：**「可以先做下 pixiv 的账号系统，在顶栏的左边做圆形头像，要和其他按钮对齐」**。
> 同日两个选择都点过：**登录路 = OAuth2 PKCE（app-api + Bearer）**；**验真 = 用户稍后把凭据贴进对话**。
> 这一份把接缝钉死，代码按 §四 的分解一批一批走，每批都要过 `:app:testDebugUnitTest`。

## 一 为什么是这条路（现量，直连无代理，2026-10-16 深夜→17 日凌晨）

| 想解锁的东西 | 匿名读数 | 有凭据之后 |
|---|---|---|
| 站方**推荐用户**（用户点名要的那格） | `app-api.pixiv.net/v1/user/recommended` → **400 / 191**；退出的首页预载 `recommendUsers.users = null` | app-api 带 `Authorization: Bearer` ⇒ 200 |
| 「这位画师的新作」 | `www/ajax/user/{id}/illustrations` 404、`/illusts` 四种参数全 400、`ajax/illusts/search` 404、`app-api/web/v1/users/{id}/illusts` 404 | app-api `/v1/users/illusts?user_id=` 是官方那一条 |
| R-18 榜单 | `ranking.php?mode=daily_r18` → **403 整档墙** | 登录 + 站点侧设置允许之后才给；**这一档今天仍然不摆开关**（等真机登录后复量再定） |
| 关注 / 书签写口 | 全部要凭据 | app-api 有写口 |

⇒ 匿名能逛的只有榜单与按标签那一跳（已落地，见 `gallery-pixiv-placement-options-2026-10-16.md` §十一）。

## 二 现成件（本鱼逐颗读过，不重新发明）

| 要用的 | 现成在哪 | 怎么用 |
|---|---|---|
| 登录界面 | ~~`feature/sourcemanage/WebLoginScreen.kt:71`：直接把 `accounts.pixiv.net/authorize?...` 递进去；**`onComplete` 的第三个参数就是最终 URL** ⇒ 在那儿抓 `pixiv://account?code=…`~~ **2026-10-17 P4 量过之后判负**（下面那段是读数） | ~~改用自持的一小颗 WebView 宿主 `gallery/ui/GalleryPixivAccount.kt`：`shouldOverrideUrlLoading` 里截回跳~~ **同日 P6 又推翻这一格**：登录改交**外部浏览器**，回跳由 `AndroidManifest.xml` 的 intent-filter 接（读数与理由见 §八） |

> **为什么那条复用不成立**（立案时本鱼只读了签名，没读判定发在哪儿）：
> `WebLoginScreen` 的成功判定只从三处发出 —— `onPageFinished`（`:251`）、
> `doUpdateVisitedHistory`（`:266`）、标题变化（`:287`），而它的 `shouldOverrideUrlLoading`
> 是原样 `return false`（`:270-274`）。自定义 scheme 的回跳交回 WebView 之后**不会有"页面加载完成"**，
> 于是那一枚 `code` 压根进不了它的判定链 —— 表现是"登录成功、界面却停在登录页"。
> 另一半原因在措辞：它成功/失败那两句是给漫画源写的（"已识别到登录，但源那边没接上"），
> 而 §四 P4 要验收的是"风控 / 取消 / 网络"三种成因各念各的。

| 凭据存储的形状 | `gallery/data/GelbooruAccount.kt:45`（`private constructor(context)` + `AndroidKeyValueStore(appContext, PREFS_NAME)` + `signIn/refresh/signOut` + `getInstance`；契约与实现分两颗，契约要上桌面编译面、吃 `Context` 的那颗不上） | 照这一颗逐件对齐着写 `PixivAccount`，**不另立一套凭据形状** |
| 顶栏左槽与对齐 | `components/venera/VeneraTopAppBar.kt:103` 的 `navigationIcon: @Composable () -> Unit = {}`（画廊今天**没传**）；`components/venera/VeneraTopBarPill.kt:72/91` 那两枚尺寸 = 触达 `VeneraIconButtonTouchSize`、视觉 `tokens.spacing.iconButtonSize` | 头像走同一个 `navigationIcon` 槽 + **同一对尺寸** ⇒ "和其他按钮对齐"由现成口径保证，不造新数字（批次 H 的"视觉 40 + 触达 48"就是这一对） |
| 头像取数 | `gallery/data/PixivClient.kt` 的 `avatarUrl(userId)`（`www/ajax/user/{id}` 匿名 200）与 `GalleryArtistUrls.avatarUrl(imageBig, image)` 的两档挑选 | 登录后那枚圆座直接复用，不新写一跳 |

## 三 PKCE 这条路的四步（写死在代码里，别凭记忆）

1. 生成 `code_verifier`（43~128 位 URL-safe 随机）与 `state`；`code_challenge = base64url(sha256(verifier))`、`code_challenge_method=S256`。
2. 打开 `https://accounts.pixiv.net/authorize?client_id=…&response_type=code&redirect_uri=pixiv%3A%2F%2Faccount&code_challenge=…&code_challenge_method=S256&state=…&prompt=login`。
3. 从回跳 URL 取 `code`（并**核 `state` 一致**，不一致就是别人塞进来的），POST `https://oauth.secure.pixiv.net/token`（`grant_type=authorization_code` + `code_verifier` + `redirect_uri`）换 `access_token` / `refresh_token` / `expires_in` / `user`。
4. 之后所有 app-api 请求带 `Authorization: Bearer`；`refresh_token` 到期前换一次，**换失败要在界面上说"要重新登录"**，不许静默退回匿名（降级路径不许静默交错）。

⚠️ 三条要写进代码注释的实话：
- `client_id` / `client_secret` 是 **pixiv 官方 android 客户端里公开的那一对**，不是我们的秘密；写进源码不等于可以当日志用。
- token 与 `refresh_token` **不进日志、不进报错消息**（本仓 `HttpBodyVerdictTest` 那条"查询串里可能挂着票据"的口径同样适用于 header）。
- 第三方客户端登录可能被站方风控（触发两步验证或要求重新同意）。**这一条要在登录失败那一句话里说得出来**，不能读成"密码错了"。

## 四 分解（每批一个可验收的整块，不留半实现）

| 批 | 内容 | 验收 |
|---|---|---|
| **P1** | `gallery/data/PixivPkce.kt`：verifier / S256 challenge / state / authorize URL / token 答复解析（**纯函数，不碰网络**） | 单测：RFC 7636 那组已知向量对得上；URL 里 `redirect_uri` 与 `state` 转义逐字核；token 答复缺字段时抛而不是给空串 |
| **P2** | `PixivAccount`（照 `GelbooruAccount` 那颗的形状）+ 契约 `PixivCredentials`（`StateFlow<PixivAuthState>`：未登录 / 已登录(带 userId、名字) / 要重新登录）+ token 换取与刷新（这一批才碰网络） | 单测：三档各自念一句；过期判定喂两种时间；`signOut` 之后盘上不留 token |
| **P3** | 顶栏那颗**圆形头像**（`navigationIcon` 槽 + `VeneraTopBarPill` 那对尺寸）：未登录点它 = 开 P4 的登录面；已登录显示头像（复用 `avatarUrl`），长按 = 登出（**两步确认**，破坏性小把手的既有口径） | 判据：头像与右侧那排 pill 的 bounds 同轴（这条**测不到组合**，只能进真机清单）；未登录态那句是"点我登录"而不是空圈 |
| **P4** | 登录面：复用 `WebLoginScreen`，`onComplete` 里抓 `pixiv://account?code=` → 核 `state` → 换 token → 落 P2 | 真机：用户凭据登一次；失败要念出是风控/取消/网络哪一种 |
| **P5** | 解锁「关注画师」那一格：app-api `/v1/user/recommended` 带 Bearer；未登录时那一节仍念今天那句"要登录才给" | 登录后那一格出真列表；登出后回到那句话，**不留空墙** |

**P1 之前不动 `app/` 的界面**；P3/P4 是用户点名那一颗，做完才算"账号系统"落地。

## 五 明确不做

不做密码直连接口（pixiv 已撤，且会绕开两步验证）；不把 cookie 与 Bearer 两套并成一颗（选了 PKCE 这一条，`PHPSESSID` 那一路不接）；不做多账号；不把 token 写进任何日志、报错消息或 `Log.*`；不在登录之前摆 R-18 开关；不接关注/书签的**写**口（P5 只做读，写口等登录后复量再单独立项）。

## 六 P1 / P2 收口（2026-10-17 凌晨现量）

落了 `PixivPkce.kt`（8 笔判据）与 `PixivCredentialState.kt` + `android/PixivAccount.kt`（13 笔），
全量 `:app:testDebugUnitTest` **173 类 / 1358 笔 / 0 失败 0 错误**，`:desktop:compileKotlin` 绿。
流水见手册行 214、215。以下三条**改写了本文件原来的写法**：

1. **§三 第 3 步那句"从回跳 URL 取 `code`"不能按字面实现**。OkHttp 的 `HttpUrl` 只认
   `http/https/ws/wss` 四个 scheme，`pixiv://account?code=…` 走 `toHttpUrl()` 是**恒抛** ——
   照字面写的表现不是崩溃，而是"用户在浏览器里登录成功、我们这一侧每次都报地址读不懂"。
   现解：只截 `?` 之后那一段挂到占位基座再交给 `HttpUrl`（要的是百分号解码，
   `error_description` 里就有空格；不是要它把 scheme 关）。判据 = `回跳取码要核 state` 那一笔。
2. **§三 第 4 步"换失败要念出重新登录"之外，还有一条站方合法行为要接**。刷新答复里**可以没有**
   `refresh_token`（站方不轮换），而 P1 那条"缺 `refresh_token` 就抛"在首登是对的、在刷新会把
   正常答复读成故障。现解：`tokensOf(body, keepRefreshOnAbsent)` —— 首登传 null（仍然严格），
   刷新传手里那枚。判据 = `站方不换新的 refresh_token 时手里那枚留着`。
3. **`client_id` / `client_secret` 今天仍是空的，而这是当前默认档不是缺陷**。
   原计划"直接写成 pixiv 官方 android 客户端里公开的那一对"已**撤销**：本鱼不凭记忆转抄
   32/64 位十六进制（抄错一个字符的表现是"登录页一切正常、换 token 恒 400"，最难查的一族），
   而 B 档那颗同栈参考件里的常量也**不许搬**进我们的源码。
   ⇒ 填的位置就一处：`PixivOAuthCredentials.CLIENT_ID` / `.CLIENT_SECRET`（`gallery/data/PixivCredentialState.kt` 底部）。
   没填时 `isClientConfigured=false`、`beginLogin()` 回 null，界面念"这台机器还没配 pixiv 客户端凭据"
   —— 所以 **P3/P4 的"未配置"那一档是默认路径，不是错误分支**，头像点下去不能只准备"打开登录页"这一条出路。

另外一条给 P3 的接缝：判据件把凭据取用面做成了 `PixivClientCredentials` 接口（默认真件），
不为解耦好看，是因为**真件今天没填 ⇒ 会话压根起不来 ⇒ "核 `state` 排在出网之前"这条判据永远走不到那一行**。

## 七 P3 / P4 收口（2026-10-17 凌晨 02:00）

落了 `gallery/ui/GalleryPixivAccount.kt`（顶栏账号件 + 登录面）与 `GalleryPixivAccountWiringTest` 8 笔，
另给 §四 P2 那颗补了 1 笔回跳判定判据（现 14 笔）。全量 `:app:testDebugUnitTest`
**174 类 / 1367 笔 / 0 失败 0 错误**，`:desktop:compileKotlin` 绿，`assembleDebug` 出包
（universal **103,027,029** 字节）。手册行 216。

### §四 P4 那一栏按本文件 §二 的改判执行

登录面**不是**复用 `WebLoginScreen`，而是自持的一小颗 WebView 宿主（理由与读数见上面 §二 那段引用）。
其余四条接缝按原案：外壳共用 `VeneraTopBarPill`（触达 48 / 视觉 40 由它带，不抄数字）、
未登录两档各念一句 `contentDescription`、退出走 `VeneraDialog` 的 `confirmDestructive` 那一档、
凭据闸门排在 `beginLogin()` 之前。

### 两处只有量过才知道的事

1. **`indexOfFirst` 不是文件行号**。源码扫描类判据里它给的是**过滤后列表**的位置，
   而 `IndexedValue.index` 是文件行号 —— 两套坐标一混，"下一行"会跳到几百行之前。
   这条是**红出来的**（`退出登录排在确认那一格里` 第一遍就是这条错），不是想出来的。
   ⇒ 返回行号的辅助函数要写明"返回文件行号"，判据里不许出现列表位置。
2. **一句"还没有 X"的屏上话会在自己那一批里变假**。§四 P3 落地的同一次改动里，
   首页「关注画师」那一节原来那句"本 App 还没有 pixiv 账号系统"就当众成为假话
   （这一族在前几批已经活过四回）。现按登录态念两句，并由
   `关注画师那一节的话跟登录态同源，且那句「还没有账号系统」已经拔掉` 钉住不许回来。

### 仍挂在真机与凭据上的

- **上机四项**：头像与右侧那排 `bounds` 是否同轴（组合测不到）、40dp 圆座里首字母读不读得清、
  登录面里 pixiv 的账密页与两步验证屏出不出得来、回跳那一跳真机念的是哪一种失败。
- **P5**：`app-api/v1/user/recommended` 带 Bearer 那一跳 —— 挡在 client 凭据上。
  没填时 `beginLogin()` 恒 null、界面念"这台机器还没配 pixiv 客户端凭据"，**那是默认档不是缺陷**；
  而形状没量过之前不写解析器（§一 那张表里这一行只有 400/191 这一个匿名读数）。

## 八 P6 收口 · 登录改交外部浏览器（2026-10-17 上午，用户第三轮第 1 条）

**题面（原话）**：「可以先做 pixiv 的登录系统，要有跳转到外部网页登录的授权登录和 google 账号登录的办法」。

### 为什么把 P4 那版 WebView 撤了（能力核查，不是嫌它麻烦）

| 一条 | 核查到的 | 后果 |
|---|---|---|
| Google 允许在嵌入式 WebView 里走它的 OAuth 吗 | **不允许**：Google 认得出 WebView 的 user-agent，直接回 `disallowed_useragent` 一页报错（Google 自己的公开策略，`androidx.browser` 的 Custom Tabs 文档就是为这条路给的替代方案） | §二 那格原判的"自持 WebView 宿主"在**用户点名要的那一档上是物理不可达的** —— 不是没做，是做了必然红 |
| pixiv 的网页登录页有没有 Google 那一枚 | 有多份公开说明指向"注册/登录可选 邮箱、Google、Apple"（本鱼**没有真机凭据**，这一格只能算路线判断，端到端读数等 P5 那趟） | 交给真浏览器之后，密码 / Google / Apple 各走站方那条正常路径 |

⇒ 这一侧的职责收窄成两件事：**发 PKCE 那一跳** + **接住回跳**。站方页面里用哪种登录方式，本 app 一概不碰，也就不必为每一种登录方式写一条退化路径。

### 落地（五处）

1. `AndroidManifest.xml`：MainActivity 加一颗 `<data android:scheme="pixiv" android:host="account" />` 的 VIEW filter（自定义 scheme 不写 `autoVerify`，与漫画那批同一条理由）。
2. `MainActivity.handleEntryIntent`：ACTION_VIEW 先问 `PixivCredentialState.isLoginCallbackUrl(url)`，是回跳就投 `EntryIntent.PixivLoginCallback`，**剩下的才**交给漫画链接解析器。
3. `feature/Navigation.kt`：`EntryIntent` 加第三档（穷尽 `when` 编译期逼出这一支），消费点是 `account?.completeLogin(intent.url)` —— **不推页**，结果全落在 `authState` 那一条流上。
4. `gallery/ui/GalleryPixivAccount.kt`：`PixivLoginDialog`（整颗 WebView 宿主）删除，换成 `openPixivLoginInBrowser` = `Intent(ACTION_VIEW, authorizeUrl)` + `FLAG_ACTIVITY_NEW_TASK`，`runCatching` 之后打不开浏览器那句**上屏**（不静默）。
5. `PixivAuthState` 加第四档 `LoginRejected(reason)`，`completeLogin` 在**没有登录态可保**时推它；`GalleryHomeSections` 的「关注画师」那一行按 `screenLine` 念 —— `screenLine` 从此有消费者（P2/P3 时它是零调用点的那一枚）。

### 规则（这一批立下来的）

- **回跳与漫画链接共用 ACTION_VIEW ⇒ 分流必须排在解析器之前**。反了的表现不是崩，是一句 `认不出这条链接里的漫画：pixiv://account?code=…` 的 Toast —— 既把失败读成链接问题，**又把授权码原样念上屏**。
- **一次失败不许静默**：撤掉 WebView 之后屏上再无别的读点，所以 `completeLogin` 的失败必须自己推成一档。口径仍是"失败不许洗掉本来正确的凭据"——已登录时不推 `LoginRejected`（P2 那条判据的形状）。
- **判定收成一颗静态**：`isLoginCallbackUrl` 而不是契约方法。唯一调用点在冷启动主线程，为它实例化账号那颗件会多读一次 SharedPreferences；而界面里出现第二份 `pixiv://` 就是第二把尺子（判据改成**全仓扫代码行**）。
- **消息会先上屏，所以来源处就不许带凭据**：`回跳里没有 code` 只报**项名**、`state 不一致` 只说"带的是另一枚"、`tokensOf` 只在**非 JSON** 那一档截原文（能解成 JSON 的答复里就写着票据，一个字不带）。这条是本批唯一一处"为了读数而收紧"的改动。

### 数字

- 判据：`PixivCredentialStateTest` 14 → **16**（新两笔：失败必须推成 `LoginRejected`、已登录时一次失败不洗凭据），`GalleryPixivAccountWiringTest` 8 → **10**（撤掉 WebView 那笔，换成"交外部浏览器 + WebView/AndroidView/WebLoginScreen 四类零残留"、"清单与 `REDIRECT_URI` 同一个值"、"回跳只一个消费点（数道数）"，并把 scheme 负向尺改成**全仓代码行**扫描）。
- 台架 `_qa/b120_perturb.js` **6 笔全咬、逐笔对题、零残留**（K1 铺满、K2 清单 host、K3 同一条带道数、K4 推错档、K5 交浏览器那一跳、K6b 消费点复制）。⚠️ K6 第一遍**没咬**，根因是**笔形**不是尺子：终点串写成 `completeLogin(intent.url + "")`，needle 数不到它 ⇒ 判式没被喂到那一格。重下成"两行逐字相同"之后咬住（台账两份：`_qa/b120_perturb_ledger.txt`、`_qa/b120_perturb_ledger2.txt`）。
- 全量 `:app:testDebugUnitTest` **174 类 / 1,376 笔 / 0 失败 0 错误**（上一遍 1,371 + 5）、`:desktop:compileKotlin` 绿、`assembleDebug` 绿（universal 10:38 **103,797,029** 字节）。
- 量具自身两处复算：扰动第一遍六笔全 `BUILD_FAILED` 是 **`execFileSync('gradlew.bat')` 在 Windows 起不来**（`.bat` 要 `cmd /c`），日志 0 字节 = 根本没跑，不算被测代码坏；`--rerun` 在还原之后必须带，否则 gradle 判 UP-TO-DATE 不重写结果 XML。

### 未量（要上机，且挡在凭据上）

1. **整条回跳没在真机走过一次**：填上 `client_id` / `client_secret` 之后点头像 → 浏览器 → 回来是否真推成 `SignedIn`；这一格是本批评据测不到的唯一一段（离线判据用应用拦截器伪造 token 答复，能证"什么时候才发这笔"，证不了浏览器那一段）。
2. Google 那一枚在站方页面上出现与否、以及它在真浏览器里的授权步（本鱼只有外部说明，没有读数）。
3. 打不开浏览器那一句在设备上的实际措辞（`ActivityNotFoundException` 的原文长短不一，界面只截一句）。
4. 进程被回收之后回跳迟到 ⇒ `LoginRejected("没有进行中的登录…")` 这一档是否真在屏上念出来（可复现：登录后在浏览器里停留到系统回收 app）。

## 九 第四轮收口 · 用户点 B（2026-10-17 中午）

**题面**：「参考下 https://github.com/137458/pixez-miuix 他是怎么做登录的，我的要求是加上个原生谷歌登录（能拉起手机的谷歌账号那种的）」→ 本鱼给三选一，用户只回一个字 **「B」**。
B 的定义（当时口头写下、现按它交付）：**A 全量**（内置凭据 + 站方 App 登录页 + 深链回跳 + 粘贴兜底）**再加一枚原生 Google 账号选择器**，但后者只代表"本机 Google 身份"，界面上必须写明它**不等于** pixiv 登录。

### 一、pixez-miuix 那条登录链逐字读完之后的三处改判

| 原来我们写的 | pixez 那边实际是 | 改判理由 |
|---|---|---|
| 授权页 `https://accounts.pixiv.net/authorize?client_id=…&response_type=code&…`（标准 OAuth） | `https://app-api.pixiv.net/web/v1/login?code_challenge=…&code_challenge_method=S256&client=pixiv-android`（**站方 App 登录页**） | 标准 `/authorize` 要我们自己登记应用并过审；`client=pixiv-android` 是站方自己客户端那个标签 ⇒ 登录页按 App 那条路回跳。旧那四枚参数（`client_id` / `response_type` / `redirect_uri` / `prompt=login`）这一页不认，带着只会被忽略 |
| `redirect_uri = pixiv://account` | 换 token 时回填的是 **`https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback`** | 登记的地址与实际落到手机上的形态**是两件事**：换 token 要那一条 https（`pixiv-android` 在站方登记的就是它），而站方登完跳给手机的是 `pixiv://account/login?code=…`。所以常量拆成两枚，清单里两条 intent-filter 各接一条 |
| `client_id` / `client_secret` 空着 ⇒ 界面念"这台机器还没填凭据" | pixez 源码里那对公开常量 | 用户点名"按他那样做" ⇒ 填进 `PixivOAuthCredentials`。⚠️ 这**不是**我们的秘密，但也**不是**可以当日志用的东西（§三 那条红线不变） |

### 二、`oauth_client` 那一格：判据只能收紧成"给了却不等必须拒"

站方 App 登录页**不是**标准 OAuth 端点：pixez 那条压根不发 `state` 也能换成票据，说明它至少在某些回跳形态里不回声。
若坚持"缺了就拒"，表现会是"浏览器里明明登成功了、回 app 一句 state 不符" —— 而这一条在真机上没法预演（一改就得让用户重登一次）。
⇒ 现判据 `if (state != null && state != expectedState) throw`。**这不是放松安全性**：真正绑定这一枚码的是 PKCE 的 `code_verifier`（一次性、只活在本次进程内存里），别人塞一枚自己的码进来，我们拿自己的 verifier 去换，站方回 `invalid_grant`。核 `state` 在这里是加分项，不是唯一那道闸。

### 三、粘贴兜底：按"信息量从多到少"认三种形状

浏览器那一路会偶尔断在半路（回跳没交回来、或在别的机器上登的），所以弹层里第二件是粘贴框。`signInWithPasted` 的判序：

| 输入形状 | 走哪条 |
|---|---|
| 含 `code=` 且含 `://` | 当回跳链接 ⇒ `completeLogin`（与浏览器同一条路，会话在就一次到位，不在就如实失败） |
| `tokensOf(raw)` 能解 | 当一整套 token JSON ⇒ **一笔网都不发**，直接 persist |
| 其它非空串 | 当 refresh token ⇒ 打 `refreshForm` 换一套新票据 |

判据四条把每一支钉住，其中一条专钉**空串不算登录、也不许动已有凭据**（这一族最容易写成"清空框顺手把旧票据也清了"）。

### 四、本机 Google 账号：为什么只能当"身份标记"，以及界面那句护栏

| 一次核查 | 结论 |
|---|---|
| pixez-miuix 自己有没有原生 Google 登录 | **没有**。它的 Google 那一步在它拉起的浏览器页里点，我们照做即可 |
| 能不能用系统账号选择器换到 pixiv 的凭据 | **不能**。pixiv 那个 client 归站方（包名 + 站方签名 SHA-1），我们拉起的原生选择器换出来的 ID token 的 audience 是我们自己的 client —— 站方两个理由一起拒 |
| pixiv 有没有"拿 ID token 换会话"的端点 | **没有**（`/auth/token` 只有 password（已撤）与 refresh_token 两种 grant） |

⇒ 按 B 的定义落地：一枚 `AccountManager.newChooseAccountIntent(…)` 的系统账号选择器，**只把邮箱记下来**，落盘走独立配置（`PREFS_GOOGLE_IDENTITY`，与 `PREFS_PIXIV_ACCOUNT` 分开，因为 `signOut()` 是 `store.clear()`）。
界面里那句「只代表这台手机上的 Google 账号…「不等于」pixiv 登录」是**契约不是文案**：一枚写着 Google 的按钮躺在登录框里，不写明就一定会被读成"点它能登进 pixiv" —— 这正是本仓最忌讳那一类（开关在、看着像、按下去什么都不会发生）。`PixivLoginPathsTest` 钉着它**只许有一句**（多写一句就会与另一句分叉）。

### 五、两处只有编译过/跑过才知道的

1. **`AccountManager.newChooseAccountIntent` 是 Java 静态方法**，Kotlin 经实例调会报 `Unresolved reference`（`AccountManager.get(context).newChooseAccountIntent(…)` 不成立）；javac 编同一条只报 deprecated —— 很容易被骗成"能用"。写法必须是 `AccountManager.newChooseAccountIntent(…)` + `@Suppress("DEPRECATION")`，且它有 8 参（`ArrayList<Account>`）/7 参（`List<Account>`）两个重载，裸 `null` 判不出 ⇒ 每个 `null` 显式标类型。
2. **边界判据逼出一次分层**：第一版把 `AndroidKeyValueStore` 直接写在 `gallery/ui/` 里，`BusinessApiBoundaryTest` 三条（A/C/D）当场红。改法是落盘搬进 `gallery/data/LocalGoogleIdentity.kt`（契约）+ `gallery/data/android/GoogleIdentityAndroid.kt`（真件），界面那件只剩两件纯动作，存储从 `GalleryPorts.googleIdentity` 读 —— 与 `GelbooruAccount` 那一族的形状同源。
3. **`Text` 不解析 Markdown**：护栏那句先写成 `**不等于**`，屏幕上会连星号一起印出来。强调改走「」这一档。

### 六、数字

- 全量 `:app:testDebugUnitTest` **175 类 / 1,388 笔 / 0 失败 0 错误**（上一遍 174 / 1,376），`assembleDebug` 绿（arm64 **103,747,043** 字节，拷至根目录 `venera-arm64-debug-2026-10-17-p3.apk`）。
- 新增判据：`PixivLoginPathsTest` 6 笔、`PixivCredentialStateTest` +4（粘贴四种形状）、`PixivPkceTest` +2（登录页参数、state 两档）、`GalleryPixivAccountWiringTest` 改为遍历两条回跳地址。

### 七、仍挂在真机上（本批测不到的）

1. 浏览器 → 回跳 → `SignedIn` 整条链（离线判据只能证"什么时候才发这笔"）。
2. 系统账号选择器在本机能不能拉起（没有 Google 服务时会走 `NO_GOOGLE_PICKER` 那一句）。
3. 粘贴三种形状各一次。

### 八、Firebase 那份 `google-services.json` 的读数（同日 12:10，用户提供）

用户已在 Firebase 注册应用并下载配置。逐项核过：

| 项 | 值 | 判 |
|---|---|---|
| `android_client_info.package_name` | `com.github.w13630039663bit.venera.miuix` | ✅ 与 `app/build.gradle.kts` 的 `applicationId` **逐字相同** |
| `oauth_client` | **`[]`（空数组）** | ❌ **这一步还没成** |
| `api_key` | 有 | 只够初始化，不够登录 |

**`oauth_client: []` 意味着一枚 OAuth 客户端都没登记**，于是：没有 Web client（`client_type 3`）⇒ Credential Manager 的 `setServerClientId(…)` 无值可传；没有 Android client（`client_type 1`）⇒ Google 认不出调用方签名。官方文档原话是"下载更新后的配置文件，**它现在才包含登录所需的 OAuth client 信息**"。
⇒ 补齐两步（**顺序无所谓的，但两步都要**）：① 项目设置 → 常规 → 你的应用 → 添加 SHA-1 指纹；② Authentication → 登录方法 → 启用 Google（这一步才创建 Web client）。然后**重新下载**并覆盖。

本机两把钥匙的指纹（已量）：

- debug（`~/.android/debug.keystore`，`androiddebugkey`）：SHA-1 `28:B8:A4:A9:E2:6D:C9:B4:F8:EB:FA:A4:0D:75:9B:31:19:F4:F1:3A`
- release（`venera-miuix-release.jks`，SHA-1 `49:B4:70:…`、SHA-256 前缀 `62:B8:9C:49…` 与手册记的那一把一致）

⚠️ 即便配置补齐，**它解的也只是"本机 Google 身份"这一格**（§九·四 那两条理由不变：client 归属站方、无 ID token 换会话端点）。pixiv 的登录仍在浏览器里由站方那一页完成。

## 十 第五轮 · 把「Google 登 pixiv」落到唯一能成的那一段（2026-10-17 12:20）

**题面（原话）**：「好了我重新下载了我还是要 google 这样登录 pixiv 试试」。

### 一 配置这次齐了（逐项核过）

| client_type | client_id | 说明 |
|---|---|---|
| 1 | `…pg6mcj4jb6hq6f4edldovtr7s9c4bjjt` | `certificate_hash = 49b470db…` ⇒ 与我们**正式**签名那把的 SHA-1 逐字相同 |
| 1 | `…un2u1nd1ailpjsnjnc4t6lqicursk1t8` | `certificate_hash = 28b8a4a9…` ⇒ 与 **debug** 钥匙的 SHA-1 逐字相同 |
| 3 | `…0gvrig2j7qee48cmoa5h3dreav9jc1lt` | Web client（`default_web_client_id` 就是它） |

两把指纹都是本机实测后交给用户去登记的，回读一一对上 ⇒ 这一份**没有**抄错。

### 二 仍然不插 Firebase / Credential Manager（是判断，不是偷懒）

这份配置里我们真正用得上的只有那第三行（Web client id），而它服务的那条路换不到 pixiv 的会话：

1. `pixiv` 的 Google client 归站方（包名 + 站方签名），我们拉起的原生选择器换出来的 ID token，`aud` 是我们自己那枚 client；
2. `/auth/token` 只有 `password`（已撤）与 `refresh_token` 两种 grant，**没有"拿 ID token 换会话"的端点**；
3. 所以「用手机上那个 Google 账号登进 pixiv」这一步，协议上只能发生在**站方网页**里。

接进来的代价是 +1~2MB 依赖、AGP 9.3.2 上还要赌 `google-services` 插件的兼容性，换到的只是一枚
**按下去跟 pixiv 无关**的按钮（假开关那一族）。⇒ 配置留在手里，等真要做云端同步再插。

### 三 那这一轮改了什么：把"哪份浏览器会话"交给站方

Google 的账号列表跟着**浏览器自己那份会话**走，不跟系统账号框架走。裸 `ACTION_VIEW` 会交给用户
设的**默认**浏览器（夸克 / UC / Edge 都可能），那份会话里通常没有 Google；`CustomTabsIntent`
固定走 Chrome，Chrome 里已登录的 Google 账号才会在 pixiv 那一页点 Google 时直接列出来。
**这就是"能拉起手机上的谷歌账号"在这条链上唯一站得住的位置。**

退回顺序是有意的：**先 Custom Tab，起不来才裸 `ACTION_VIEW`**。反过来的话，凡是有默认浏览器的
机器就永远到不了 Chrome，这一改等于作废（而且屏上完全看不出来）。

弹层那句也改了措辞：「登录在浏览器里完成（**优先 Chrome**）……点 Google 会列出**这个浏览器里**
已登录的账号」—— 不写"Chrome"是因为退回档里未必是 Chrome，写成"Chrome 里"就成了假话。

### 四 顺带补掉一处静默：回跳失败要当面念

`LoginRejected` 只活在这条流上、不落盘，而屏上原本唯一那句在「关注画师」那一行 —— 那一行常在
屏幕外。用户刚从浏览器回来、什么都没看见，读起来就是"点完没反应"。
现加一条 `LaunchedEffect(state)`：`LoginRejected` 一出现就抬进 `notice`（那句"现在登不了 pixiv"的弹层）。
键取 `state` 而不是取 `rejected` 那一条：同一原因再失败一次也会再说一遍（能重复推就是真的又试了一次）。

### 五 判据与数字

- 新增两笔：`交浏览器那一跳先试 Custom Tab 再退回裸 VIEW，顺序不许反`（顺序即语义，反了静默失效）、
  `回跳失败要在屏上当面说，不许只丢给「关注画师」那一行`；
  `GalleryPixivAccountWiringTest.登录页交给外部浏览器…` 的那一枚针跟着换成两处
  （`CustomTabsIntent.Builder()` + `Intent(Intent.ACTION_VIEW, uri)` + `val uri = Uri.parse(authorizeUrl)`）。
- 依赖：`androidx.browser:browser:1.8.0`（唯一新增）。
- 全量 `:app:testDebugUnitTest` **175 类 / 1,390 笔 / 0 失败 0 错误**；`assembleDebug` 绿
  （arm64 **100,614,566** 字节 ⇒ 根目录 `venera-arm64-debug-2026-10-17-p5.apk`）。
- 包体反而比上一版小 3.1MB，核过内容：**条目一个没少**（p5 多 4 个 `androidx.browser` 的资源、
  `classes.dex` 多 270KB），压缩率差异，不是掉件。

### 六 上机清单（这一轮真正要看的）

1. 点「去浏览器登录」是不是**在 Chrome 里**打开（不是默认浏览器）。
2. pixiv 那一页点 Google，列不列得出这台设备的 Google 账号。
3. 登完能不能回来（`pixiv://account/login?code=…` 那一跳），回来是 `SignedIn` 还是弹一句失败原因。
4. 设备上要是压根没有 Chrome / 没有 Custom Tabs 提供者，走的是裸 VIEW 那一档 —— 那句文案里的
   "（优先 Chrome）"就正好对上。




