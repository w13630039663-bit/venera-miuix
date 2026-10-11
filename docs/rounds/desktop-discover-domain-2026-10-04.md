# 桌面第九批：发现域转正 + 一条潜伏的整屏崩溃（2026-10-04）

范围：`desktop/`。**Android 侧零改动**（`git status --short app/` 空）。分支 `compose-migration`，未提交。

判据 285 → **302**（`:desktop:test` 302/0/0 实跑，40 颗类；净 +17，其中收尾补的两条见 §6.4）。

---

## 1. 这批做什么

- **`DISCOVER` 域转正**：rail 上最后一枚坦白页变成真页面 —— 「猜你喜欢」那一屏。
  **七枚导航域到此全部是真页面**，桌面今天没有坦白页了。
- **顺带抓出一条既存缺陷**：`DesktopGalleryFavoritesPane`（第二批落的）把那一面墙
  包在 `Column(Modifier.verticalScroll())` 里 —— 同一形状**会整屏崩**，
  只是这台机器上没有画廊收藏，所以它一直走的是空态那几档，从没被触发过。

## 2. 那条崩溃：`LazyVerticalGrid` 不能吃无限高

第一次跑 `-PstartDomain=DISCOVER -Pproxy=127.0.0.1:7890`：屏上是一枚**空白的 `Error` 对话框**，
日志里是（`Exception in thread "AWT-EventQueue-0"`）：

```
java.lang.IllegalStateException: Vertically scrollable component was measured with an
infinity maximum height constraints, which is disallowed. One of the common reasons is
nesting layouts like LazyColumn and Column(Modifier.verticalScroll()) …
    at …lazy.grid.LazyGridKt$rememberLazyGridMeasurePolicy$1$1.measure(LazyGrid.kt:202)
```

[DesktopGalleryPostWall] 里面是一颗 `LazyVerticalGrid` —— **它自己就是滚动容器**，
所以要吃有界高度。父层挂 `verticalScroll` 交下来的是无限高，那颗 Grid 当场抛。
不是"滚不动"，是**整屏崩**，而且弹层里那行字在窗口捕获里是**空白**的（判不了小件那一族）。

两种正确解法，这批各用其一：

| 屏 | 解法 |
|---|---|
| 发现 / 收藏 | 外层**不挂** `verticalScroll`，墙吃 `Modifier.fillMaxWidth().weight(1f)` 自己滚（搜索那一屏今天就是这个形状，实测过 148 张） |
| 漫画探索 | 保留外层滚动，但那颗 `LazyVerticalGrid` 拿的是**算出来的定高** `Modifier.height(estimatedGridHeight(…))`（`DesktopComicPane.kt:289`）—— 这是另一种正解，判据不许误伤它 |

**新判据 `DesktopWallScrollNestingTest`（+2）**：凡渲染那一面墙的文件都不许出现 `.verticalScroll(`，
并配一颗对照用例专门钉"漫画那一屏的定高解法不算违例"。

⚠️ 这条判据本身也出一条读数：它**第一版是撞到第一颗就停**（`forEach` + `assertTrue`），
于是只报了 `DesktopForYouPane.kt` 一颗 —— 差一点把"收藏那一屏同病"当成没有。
改成**一次列出全部违例**之后，读数变成 `[DesktopForYouPane.kt, DesktopGalleryFavoritesPane.kt]`。
**"负向扫描"的判据要一次报全，不要短路。**

## 3. 计划层与屏层各钉什么（+17）

| 层 | 类 | 条数 | 钉什么 |
|---|---|---|---|
| 计划（纯函数，真跑取值） | `DesktopForYouPlanTest` | 8 | 三站有货时三串都发得出且**逐字等于** `GallerySearch.queryOf` 那一串；一站有货整屏照发、另两站点名；两站都没收藏 ⇒ 那句是引导不是报错；**标签被滤光与没收藏是两句话**（站级那句也分开）；换种子真换抽样而同一颗种子不变序；计划件里不许出现 `joinToString(" ")` / `split(" ")` / `Regex(`；**句子不许写死站点数目**；**`isBlocked` 那一枚参数不许带默认值**（⑧，见 §6.4） |
| 屏（扫源码，配对照组） | `DesktopForYouPaneTest` | 6 | 墙要过第四批那层守卫（`maskMode` / `blockAi` / `ports.contentGuard` 三样都真传）；三枚偏好显式传给墙且墙拿有界高度；两句"没做"必须用**大白话**念在屏上（并禁实现笔记）；缺席逐站点名 + Gelbooru 那档转交 `GallerySearchSource.NEEDS_ACCOUNT_REASON`；「换一批」真换种子且种子在 `remember` 里；取数出事与一站没答上是两档 |
| 形状 | `DesktopWallScrollNestingTest` | 2 | §2 那条 |
| 表面 | `DesktopCallbackParamLedgerTest` | 1 | **收尾补的**：整棵桌面 UI 里带默认值的回调参数一处都不许有（§6.4 后半） |

## 4. 三档空态与"没做的两件事"

抽样那一层今天交回的是共享的 `GalleryRecommendation` sealed（`Tags` / `NoSeeds` / `NoUsableTags`），
桌面只分支不重判。三句话各自不同：

| 档 | 屏上那句 |
|---|---|
| 一站没收藏 | 「Gelbooru这一站还没有能推荐的标签：你还没收藏过它的图」 |
| 有收藏但标签全被滤掉 | 「…这一站**有收藏**，但标签全被屏蔽规则滤掉了 —— 是我们的判据没东西可发，不是你还没收藏」 |
| 整屏发不出去 | 「有收藏，但抽出来的标签全被屏蔽规则滤掉了 …」/「还没有可以推荐的收藏：…先去「收藏」那一屏…」 |

优先次序是判据：`NoUsableTags` 盖过 `NoSeeds` —— 反过来写会让"有收藏却被我们滤光"念成"快去收藏"，
用户照做了还是空的。

刻意没做的两件事也各有一句：只给第一轮的第一页、每次进来都重抽一轮（不沿用上次那一批）。
**成因（缺的是哪一层）写在代码注释与本文档里，不写进屏上那句** —— 见 §6.2。

## 5. 图证（窗口捕获，`occlusionSafe=true`）

取证条件：临时写 `gallery_favorites.json` 四条（yande.re 两条 / Safebooru 一条 / Gelbooru 一条，
标签是 `mountain_river landscape` 这类探针词，`preview_url` 一律留空），
跑 `-PstartDomain=DISCOVER -Pproxy=127.0.0.1:7890`。跑完镜像进
`_trash/gallery_favorites-probe-20261004-t9.json` 并移除（数据目录现在 `*.json` 计数 0）。

屏上读到的：段头「本轮从 4 条收藏里抽标签 · 每站各取一页」、一枚「换一批」、
**一面真墙 —— yande.re 与 Safebooru 两站的图混排在一起**（每卡右下角一枚站点徽标）、
页尾一行红字点名 Gelbooru 没配凭据（要用户去填 User-Id 与 API key 那里）、
最后一句是"每次进来都重新抽一轮，也只给第一轮的第一页"。

⚠️ 这条链要**代理**才通：本机直连 booru 两站全不通（既有账），`-Pproxy=127.0.0.1:7890` 才拿得到图。
不带代理那一跑不会崩，但墙上会是"图没加载出来"那一档。

### 5.1 收尾复验（改后四跑，全部窗口捕获 + 每跑 `grep "Exception in thread"`）

| 跑法 | 读到 |
|---|---|
| `DISCOVER` + **空**收藏 | 段头「本轮从 0 条收藏里抽标签」+ 那句引导，**不崩** ⇒ 空态分支也真走了一遍 |
| `DISCOVER` + 夹具 4 条 | 一面真墙（两站混排 + 站点徽标）+ 红字点名 Gelbooru，**不崩** |
| `FAVORITES` + 夹具 4 条（`preview_url` 留空）／夹具 3 条（有 `preview_url`、漏 `file_ext`） | 两跑同一句「收藏里有 N 条，但一张都摆不上屏」 |
| `FAVORITES` + 同上补 `file_ext: jpg` | 「本机存档 3 条 · 摆上屏 3 张 · Safebooru 3 张」+ 三张真图 ⇒ **收藏那一屏的那面墙今天第一次真被走到**，不崩 |

⚠️ 第三行是这批最容易被跳过的一种假通过：**夹具字段不全时屏上给的是空态，不是被测的那条分支**，
而它同样"不崩"。`GalleryMerge.isDisplayable`（`GalleryMerge.kt:58-59`）要的是
`fileExt ∈ 白名单` **且** `previewUrl` 非空两格 —— 造夹具前把字段表回读那颗 `@Serializable` 类
（`GalleryFavorite`），别照记忆写。五跑日志的异常计数各为 **0**。
夹具两份镜像在 `_trash/gallery_favorites-probe-20261004-t9{,b}.json`，数据目录 `*.json` 现为 0。

## 6. 四条过程读数

1. **同一颗判据第一版写坏两次**：② 那条最初用正则 `DesktopGalleryPostWall\(([^)]*)\)` 抓参数再
   `split(",")` 数参数位 —— 而 `modifier = Modifier.fillMaxWidth().weight(1f)` 里那个括号
   把捕获截断成半截，数出 5 个 ⇒ **假红**。改成"取调用点往后 320 字符 + 断言命名实参在"。
   同批另一条对照组写成 `.contains("internal fun").not()`（断言写反）。
   **自己刚写的判据要当缺陷审**，这条已经记在手册 §4.1 那一族里，这次是第三次。
2. **实现笔记进界面，我又犯了一次**：页尾那句最初写着
   「桌面没有那一层状态持有件，翻页要的那套在 Android 侧的 ViewModel 里…快照那颗件吃 Context，
   在桌面编译面的排除清单里」。这正是用户 09-30 点过名的那一类。
   改成「这一屏每次进来都重新抽一轮，也只给第一轮的第一页 —— 翻到更多、以及沿用上次看到的那一批，
   桌面这一版都还没有。」并给这条判据加了**字面量级**的禁词扫描（`ViewModel` / `Context` /
   `编译面` / `排除清单` / `GalleryForYouCache` 不许出现在字符串字面量里）。
   ⚠️ 禁词表里**不放** `ports` 之类会出现在"接线缺一件"诊断句里的名字 —— 那种句子是写给排查的人看的。
3. **崩溃的读数不一定在屏上**：那枚 `Error` 对话框捕获回来是**空白的**，
   真读数在 stdout 的栈里。⇒ 桌面这一侧"屏上没东西"不等于"没出事"，
   **每一跑都要 `grep "Exception in thread"` 那一日志文件**，别只看图。

4. **收尾补的两条：带默认值的参数一律改成必填 / 零消费者的回调删掉**（手册 §23.8）。
   - `desktopForYouPlan` 的 `isBlocked` 原本带默认值 `{ false }`，KDoc 还写着"屏上那条路径必须显式传"——
     注释不是约束。带默认值时的失败形状是：**将来第二处消费者漏传 ⇒ 屏蔽规则不参与抽样**，
     而屏上那句「是我们的判据空，不是你没干活」永远走不到。
     先加判据 ⑧ 跑出红（`8 tests completed, 1 failed`，红因正是那句默认值还在），
     再撤默认值并把 ⑤ 那三处调用点显式补成 `isBlocked = { false }`。
   - 顺着同一条尺子扫全棵桌面 UI，新判据 `DesktopCallbackParamLedgerTest` 只抓到一颗违例：
     `DesktopGelbooruCredentialPanel.kt` 的 `onCleared: () -> Unit = {}`，而**唯一的调用点没传它**
     （`GelbooruCredentialLauncher.kt:88`）。删参数 + 删那一处调用，行为零变更
     （清除凭据后屏上照旧跟着 `identity` 那条流变）。
   - ⚠️ **这条新判据自己贡献了一次假通过**：第一版判式 `^名字: \(.*\)\s*->\s*.*=$` 漏了行尾逗号，
     而真实的一行是 `onCleared: () -> Unit = {},` ⇒ 匹配不到 ⇒ **整条恒绿**，
     `GRADLE_EXIT=0` 看着像"扫过了、没有违例"。改成 `=.+[,;]?$` 才红出那一颗。
     **负向扫描光配"扫得到正例"的对照组不够，必须先红一次** —— 这批第三次（另两条见 §6.1 与手册 §4）。
   最终读数：`:desktop:test` **302/0/0**（40 颗类）；`packageUberJarForCurrentOS` EXIT=0，
   产物 **97,669,128 B**；`desktop/src` 16,344 行 / 101 颗 `.kt`。

## 7. 下一批的输入

1. **桌面今天没有坦白页了** ⇒ 缺席机制（`DesktopAbsence` / 坦白页渲染件 / `desktopDomainRows` 的
   `else` 分支）今天走不到。两条路等拍板：**撤干净**（本仓 09-30 那条"未实现连标题一起撤"的口径），
   还是**留着当回滚落点**（判据 `assertEquals(0, absent.size)` 现在钉着它）。
   这批选的是后者，并把选择写进了那颗用例的注释里。
2. **头像要不要现取**（画师那一屏，等拍板，§22.5.2）。
3. **`addRule` 仍零调用点**（§20.1.1）。
4. 建议词那一排要不要接成可点（§20.2.2）。
5. 发现那一屏的**续页**：要它就得给桌面造一颗状态持有件（分页、哪些站到底、去重键累计），
   那是独立的一批。
6. **「最新」那一行的屏上那句不准**（收尾实测出来的，全部读数在手册 §23.9）：
   那句写着「三站列表都没有按时间的排序参数」，而实测
   ① `SafebooruClient` 走的是 `safebooru.donmai.us`（`SafebooruClient.kt:137`），
   默认序 ids `12315048, 12315046, 12315044` = **最新在前**、`tags=order:id_asc` → `3, 8, 9` = **站方支持**；
   ② yande.re 的 `order:id_asc` / `order:asc` / `order:fav_count` 三种写法回来的 ids **与默认序逐字相同**
   （该族整站不换序）；③ **空标签两站都 200 非空** ⇒ 点亮「最新」不需要排序参数；
   ④ Gelbooru 匿名 **401**，这一站**没量**。
   真的那一半只是：共享接口 `searchPosts(tags, page, limit)` 没有排序参数（`GalleryBusinessApi.kt:122`）。
   两条候选（只改句子 / 连那一行一起点亮 = 第十批）与"别给共享接口加排序参数"那条写在手册 §23.9。

---

## 8. 续记：第十批 ⑤（同一族工作，接着这篇记）

「最新」那一行点亮 —— 输入就是本篇 §7 第 6 条那组实测读数。完整账在手册 §24，这里只记三条：

1. **实现"最新"的全部新逻辑 = 什么都不加**：查询串恒空 + 腿选择 + 逐站点名（`DesktopLatestPolicy.kt`），
   其余每一环转交现成判据。判据 ④ 把**本地排序判死**（只拿到每站第一页时排出来的不是最新）。
2. **判据 ⑥ 钉住共享接口不许被加 `sort` 形参**（那会牵动 Android 侧冻结面，而这一屏不需要它）。
3. **新取证参数 `-PstartSection=`**：`--start-domain=` 只到域，到不了 pane 里那一行，
   于是这一屏本来拍不到。五环链路各有判据（含 gradle 那一环）。

⚠️ **扰动实验第一次是"绿"过去的**：改坏 `gradleProperty("startSection")` 之后
`:desktop:test` 报 `UP-TO-DATE` + `GRADLE_EXIT=0` —— 测试根本没跑，因为 Gradle 不知道那条判据
在运行期读构建脚本。加 `--rerun-tasks` 才红。**修法是把依赖声明出来**：
`tasks.withType<Test> { inputs.file(project.buildFile) }`，然后不带 `--rerun-tasks` 复跑扰动 ⇒ 真红。
⇒ **凡"扫源码/扫配置"的判据，扫的东西必须是测试输入**；扫构建脚本、扫 `settings.gradle.kts`、
扫仓库外的文件都不安全。

---

## 9. 续记：第十批 ③（发现域接上续页）

判据 327 → **336**（+9，45 颗类）；uber jar **97,771,947 B**；`app/` 未再动。全账在手册 §26。

1. **缺的是状态不是网络**：`searchPosts(query, page, limit)` 今天就能翻页，
   `GalleryForYouMerge.page` 本来就收 `page` 与 `seenKeys`。所以这一批的全部新东西是那颗
   `DesktopGalleryBatch`（**不可变值 + 两颗纯函数**，七条用例全真跑），
   而不是在 composable 里散放五枚 `mutableStateOf`。
   散着放的形状下"换一批要同时清哪四样"只能靠记性。
2. **五条行为逐条照 Android 那颗 `applyPage`**：第 1 页替换 / 之后追加、**缺席不算到底**、
   去重键只累计上屏那批且跨页不清、`round != seed` 整笔作废、三枚张数逐页累加。
   `atEnd` 判的是「**发过的**站全到底」，且 `queries` 空 ⇒ 不叫到底（那叫空屏）。
3. **这颗同时是详情面板 P1 要的那颗**：面板要的"哪几张、当前第几张"与续页要的"第几页、哪些站到底"
   都从 `posts` 出发 ⇒ 详情面板那一批往这里加字段，**不许再造第二颗**。
4. **「下一页」只在没到底时摆**（到底了还摆一枚按下去没反应的就是假开关），
   而那一笔失败是**第三种相位**：已经摆出来的那些一张不少 —— 与"这一轮没取成"、
   "某一站没答上"是三句话。

⚠️ **本批最该记住的一条不是实现，是量具**：第九批那句「也只给第一轮的第一页」接上续页之后成了假话，
而旧判据 `code.contains("第一页")` **照样绿** —— 因为"正在取第一页…"里也有这三个字。
⇒ 屏上要说撤的话，判据必须钉**那颗句子本身**（本批提成 `DESKTOP_FOR_YOU_SNAPSHOT_LINE` 常量 +
一句负断言），扫一个关键词不算钉住。这是 §22.3 那条"能力接上了而那句'还没有'还活着"的近亲：
**句子活着，判据也活着，两边都以为没事**。

⚠️ 另一条自审：新判据 ⑦ 第一版把 `fetchForYouPage(` 的**声明行**也数成了调用点
（`expected:2 but was:3`）。那条红是判式取错了对象，不是实现错了 —— 改成"数不含 `private suspend fun` 的行"。
