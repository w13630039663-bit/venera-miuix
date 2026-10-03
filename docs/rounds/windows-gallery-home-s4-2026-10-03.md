# S4 · Gelbooru 凭据与「缺席必须明说」（含 S5 稿件回改的已落地部分）

> 上级方案：`~/.qoder-cn/plans/wistful-inlet-snipe.md` §S4 / §S5
> 上一批：S1 `windows-gallery-home-s1-2026-10-03.md` · S2-A `…-s2a-wiring-…` · S2-B `…-s2b-shell-…` · S3 提交 `3f8c133`

## 一、这一批真正做掉的事

| 事项 | 落点 |
|---|---|
| 凭据判据抽成两端共用的一颗 | `app/…/gallery/data/GelbooruCredentialState.kt`（新增，进两端编译面） |
| Android 侧改成转发，`feature/settings` 零 diff | `gallery/data/GelbooruAccount.kt` |
| 桌面写侧接上判据件 + SauceNao 写侧 | `desktop/…/gallery/data/DesktopGalleryCredentials.kt` |
| 凭据面板（能真填、真校验、真注销） | `desktop/…/gallery/ui/DesktopGelbooruCredentialPanel.kt` |
| 缺席时把面板摆出来；配好后自动重跑 | `desktop/…/gallery/ui/DesktopDailyPane.kt` |
| 缺席判据从「自证集合」换成「打真 client」 | `DesktopFailureReadoutTest` / `DesktopAbsentSitesTest` / `FakeHttpEngine` |

## 二、S3 遗留的一处编译错（本批顺带收掉）

`DesktopDailyPane` 上一批的未提交改动里有三处编不过，根因都是"按记忆写 API"：

1. `post.imageUrl` —— `GalleryPost` **没有**这一枚。网格档按用途取 `previewUrl`
   （`GalleryPost.kt:66`「网格缩略档」三站同口径）。
2. `AsyncImage(placeholder = Color)` —— 那个形参收 `Painter?` 不是 `Color`。
3. 面板草稿的 `BasicTextField(enabled = false)` 被写在非组合上下文里，且 `Button` 没 import。

现在图位是：`previewUrl` 非空 → `AsyncImage`；**空串 → 说一句「站方没给缩略档」**。
不留 `largeUrl`/`fileUrl` 兜底 —— 那是原图（yande.re 单条实测可达 6.6 MB），一屏几十张就是几百 MB。

## 三、S4 的三处实测读数（都是撞出来的，不是设计出来的）

### 3.1 fluent 组件的参数名不照 material 那套

- `TextField(value, onValueChange, modifier, **enabled**, …)` —— 这一枚确实叫 `enabled`，可用；
- `AccentButton(onClick, modifier, **disabled**, colors, …, content)` —— 第三枚叫 **`disabled`**，
  没有 `enabled`。按 material 的肌肉记忆写会红（实测过）。
  常量池判据：`javap` 读 `fluent-desktop-0.1.0.jar` 的 `ButtonKt`，只有 `disabled`/`iconOnly`/`contentArrangement`。

### 3.2 `GelbooruAccount` 不能直接给桌面复用，得再抽一层判据

`GelbooruAccount` 整颗在 `desktop/build.gradle.kts` 的排除清单里（它吃 `Context` 与
`AndroidKeyValueStore`）。而"凭据通了没有"这件事**两端必须同一条判据** ——
桌面另写一份的后果是：桌面说"配好了"而下一轮取数照样 401，两处口径来回打，
症状读起来像"存了但没生效"。

于是按 S1 那条手法抽出 `GelbooruCredentialState`：
`class GelbooruCredentialState(store: KeyValueStore, engine: (() -> OkHttpClient)?)`。
Android 侧传 `AndroidKeyValueStore` + `VeneraNetworkClient`，桌面传 `JsonKeyValueStore` + `DesktopHttpEngine`。
`GelbooruAccount` 退化成 **Android 接线 + `StateFlow` 门面**，`feature/settings` 那几处 UI 签名一字未改。

四条判据照搬，不许在桌面"顺手优化"：

1. **两样都非空才算配过**（少了那条 → 界面显示已登录、请求带不上参数）；
2. **站方点头才落盘**（拿一次 401 去覆盖掉一组本来正确的凭据，是这个面板最坏的一种错）；
3. 401 那句话**只有一份**（`GelbooruCredentialState.REJECTED_HINT`），`GelbooruClient.failure` 也引它 ——
   实测这一站 401 **body 为空**，且错 key / 错 id / 不带凭据**三种情形给的是同一个答案**，
   所以谁也判不出是哪一半错；
4. User ID 脱敏比 key 更严：**只留末两位**。key 给末四位是常见做法，而 Gelbooru 的 User ID
   是**递增数字 id**，公开账号页一眼能对上一个号段，留四位等于把号段窗口缩到一万。

### 3.3 假站：拦截器打在 `HttpEngine` 那一层

三站域名是 `GallerySite.apiHost` 的**枚举常量**，URL 在 client 内部拼死，**没有注入点**。
所以"让请求去别处"只有两条路：改 `GallerySite`（生产面，S4 不许动），或在 `HttpEngine` 掐住末一跳。
选后者 —— 它正是为桌面留的那枚口，于是 URL 拼装 / 解信封 / 401 翻话 / `failures` **全部走生产代码**。

**不另起 `MockWebServer`**：那要改 host 解析（hosts 文件 / DNS 劫持），是机器级副作用；
拦截器是进程内、可撤销、跨平台的。

两个真踩的坑（都写进 `FakeHttpEngine` 的注释里）：

- **三颗 client 都是 `getInstance(engine, …)` 的进程内单例**，第一次装配就把 engine 与凭据锁进
  `INSTANCE` 了。每条用例各 new 一颗引擎，从第二条起请求就打到第一颗上 ⇒ 症状是
  「没有为这枚 URL 登记假响应」，真因与被测逻辑无关。所以 `FakeHttpEngine.shared` **全测试类共用一颗**，
  `fresh {}` 清簿记后复用。
- **fixture 少给一枚字段就会被 merge 滤掉**：`GalleryMerge.isDisplayable`（`:58`）要求
  「`fileExt` 在白名单 **且** `previewUrl` 非空」，缺一枚 → `GalleryMerge.kt:100` 当场抛
  「滤完 0 条可用」。那行抛得对（站方给了内容而我们一条没摆 = 判据错了），
  但报错读起来像"merge 有 bug"。三站 fixture 现都带 `file_ext` + `preview_*_url`。

## 四、判据从「自证」换成「真打」

前一版两处测试是**自证**：无论生产代码怎么坏它都绿。

- `DesktopFailureReadoutTest` 原本 `val renderedSites = failures` 再断言相等 —— 断言的是自己赋的值；
- `DesktopAbsentSitesTest` 原本 `val actualFailures = setOf("gelbooru")` 再断言它含 `"gelbooru"`；
  `pid 0 基` 那条是 `assertEquals(0, 0)`。

现在两条都经 `GalleryDailyFeed` / 三颗真 client，**拦住的是这些形态**：

| 形态 | 拦在哪条 |
|---|---|
| 少报一站（静默少一站） | `匿名时缺席名单恰好是 Gelbooru` |
| 多报一站 | `三站都答上时 failures 必须为空` |
| 只报第一个失败（三站挂两站读起来像挂一站） | `两站缺席时名单是两枚不是一枚` |
| 全空时只交笼统的"没有可用内容" | `三站全没货时整体失败且消息里带三句原因` |
| 缺席站被按 0 算进 `perSite` | `缺席站不进各站条数表` |
| 401 那句人话被改 / 被含糊 | `匿名时 Gelbooru 缺席…`、`401 那句话必须点名要凭据而不许含糊` |
| **把凭据丢掉**（不报错、永远 401 —— 这一站最坏的错） | `带凭据时 Gelbooru 出货且请求带上了两枚参数`（断言 URL 里真有 `api_key`/`user_id`） |
| `pid` 减 1 那行被删（**站方照回 200，不会有任何报错**） | `第 1 页发的是 pid 0 而不是 pid 1` |

## 五、S5 已落地与未落地

**已落地**：

- `README.md:219` / `README.en.md:219` 的「本分支是 Android 独占工程 / Android-only … not started」
  已按实码改成"Windows 桌面模块在推进，覆盖图库首页这一屏"；英文侧那张表
  （`README.en.md:242`）把 Windows 与 Linux/macOS/iOS **拆成两行** —— 原写法把四个平台并成一行，
  改正之后就不能再拿"Windows 未开工"当理由把另外三个也一并划掉。
- 稿件回改清单里**已被实测推翻**的两条已改正：列数按 `DesktopGalleryMetrics` 的读数是 **4 列**
  （不是稿上的 5）；"三站 · 本地无任务队列"（不是稿上的「34 源 · 2 个任务」）。

**未落地**（要 UI 出图后才好定稿）：

- 浅色档色值映射（`DesktopGalleryHomeSections.kt:270-281` 那几枚色值只给了深色档）；
- 稿上 `.pane{background:var(--layer)}` 的正式底色（现在 pane **刻意不涂底色**，理由写在那颗文件里：
  浅色主题下 fluent 自取色的主题文字会变成"深底深字"）；
- 精选摘要模块（稿上最上面那一条，**整块没做** —— 本批六项 pane 里没有它，缺口在此记明）；
- 浅色/深色两套真机出图对比。

**未验**：

- **凭据面板的运行期观感**（`--shot` 那一跑 App 起来了、接线读数都对，但自截图**自己报告无效**：
  `D_截图 无效：WindowFromPoint … 窗口不可见` —— 当前会话的窗口不在可见屏上。
  这是 `VeneraDesktop.kt` 自带的诚实判据（"本次没有可读的证据图，读数只认 stdout"），
  **不是截图失败**，更不能拿它当"面板画对了"的证据。判据层（4 条缺席断言 + 1 条 pid 0 基）已绿，
  面板的**视觉**仍需有人在有屏的会话里点一次。
- 桌面端**重启后收藏仍在**（`DesktopGalleryFavorites` 走 `JsonKeyValueStore`，
  逻辑上与 `JsonKeyValueStoreTest` 对过，但"重启进程"这一层没做成自动化用例）；
- 真机 / 打包产物里的热重启与状态保持（`packageName` 仍是 `venera-desktop-probe`）。

## 六、读数

`:desktop:compileKotlin` / `:app:compileDebugKotlin` BUILD SUCCESSFUL；
`:desktop:test` **169 用例全绿**（本批把 2 颗自证测试换成 10 条真判据）；
`:app:testDebugUnitTest` + `:app:assembleDebug` 见提交说明。
约束 6 机器核对：Android UI 五处命中数 **= 0**；冻结 8 颗与本批改动交集 **= 0**
（本批只在 `gallery/data` 与 `desktop/`）。
