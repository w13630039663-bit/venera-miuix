# 批 82.7 · 漫画域「此刻渲染哪一屏」（量具 → 缺陷 → 修法，2026-10-10）

**一句话**：`--comic=` 那颗种子接管落点时把用户点名的**行**一律交回默认 ⇒ 唯一能拍出「载荷设上了而那一屏根本没换」的路径被**量具自己**堵死。第一步把量具补正，一起来就拍到那条缺陷；第二步把详情与阅读两屏的渲染点**连同引擎句柄**从「探索」那一支提到漫画域 body 层。

`app/` 一字未动（改动全在 `desktop/src/main` 与 `desktop/src/test`）。

---

## 1. 第一步：补量具（先把那格拍出得来）

`ForensicFlags.comicLanding` 旧写法只有两档：没给 / 被抢走 ⇒ 原样交回；否则**一律** `ForensicLanding(DesktopDomain.COMIC, null)`，注释自己写着「漫画域第一行就是探索，行不用种」。这一格在**用户没点名行**时是对的，它同时把「行=阅读历史 且 详情=开」这一种组合**永远种不进去** —— 而那正是缺陷唯一会现形的形状。

改成三档：

- 用户已点名漫画域某一行 ⇒ **只补域、不动行**（要拍的那一格）；
- 带着上一域的键（如 `gallery-daily`）⇒ **原样交回不静默**（不替用户猜一行；根上 `?: rows.first()` 落回第一行，那一格由 `sectionNote` 照实念）；
- 从别的域接管 ⇒ 交回 `null`（与从前一致）。

接管那句正面读数多念一格「行留在〈某行〉」/「行交回第一行〈探索〉」，行名从 `desktopDomainRows` **现取**（改名或撤行时这句跟着变，不替已经不存在的行作伪证）。

判据 `DesktopComicSeedLandingTest` 5 → 8 条（+3：行保留 / 上一域的键原样交回 / 两档念法必须是两句话）。

## 2. 缺陷：读数与屏上不一致（第一次起窗就拍到）

命令（隔离数据目录 `_qa/b82_local`，不碰真库）：

```
LOCALAPPDATA='D:\venera-compose\_qa\b82_local' ./gradlew --no-daemon :desktop:app \
  -PstartDomain=COMIC -PstartSection=comic-history -Pcomic=1461316
```

stdout 念 `D_落点 域=漫画(启动) 行=阅读历史 … 详情=开(启动)` 而 **`D_详情` 一个字都没念**；帧上屏的是历史那一屏（一行 `1461316 / 第 1 话 · 第 8 页 / jm`）。形状与批 80 那一条同族（「读数说开着而那一屏根本没组合」），只是这次出在**行**这一层。

因在 `DesktopComicBody`：它只把 `comicTarget` / `readTarget` 交给「探索」那一支，而详情与阅读两屏渲染在 `ComicExplorePane` **里面** ⇒ 从「阅读历史」那一行按一行：载荷设上、屏不换、也不报错。批 82 给历史那一屏立的「点得动」判据只量到回调那一层，回调之后那一格没人管。

## 3. 修法：两档与所选

- **甲（小）**：点历史那一行时把行一起切到「探索」—— 详情立刻能出，代价是「← 返回列表」落在探索列表而不是历史，且留下「历史那一屏永远要借探索那一行」的形状。
- **乙（所选）**：渲染哪一屏**由载荷先定**，两屏连同引擎句柄提到域 body 层 —— 载荷本来就在应用根上，屏也该在域层。

本批按乙做（未提交，整批可一句话撤）：甲把缺陷换成一条依赖关系，乙把它换成一条判据；而且只有乙能让「`--read=` 那颗种子只给阅读载荷」那一档落得到屏（乙下面第 ② 条那一个用例）。

## 4. 第二步的形状（改了什么）

- `internal enum class DesktopComicScreen { READER, DETAIL, EXPLORE, FAVORITES, HISTORY }`
- `internal fun desktopComicScreen(content, hasComic, hasRead)`：`hasRead` → 阅读、`hasComic` → 详情，**两枚都没给才按行分岔**（三行各回自己那一屏）。
- `internal fun desktopComicEngineNeeded(screen)`：只有真要问源站的三屏起引擎；「收藏」「阅读历史」读本地库，白起一颗 GraalJS + 整源装载（30 张封面出网）是代价不是准备。
- `private class DesktopComicEngine(state, session, retry)` + `@Composable private fun rememberComicEngine(config, needed)`：装载、`session`、退场那颗 `DisposableEffect(session)` 一起搬到域层持有件里；`needed` 为假时**交回句柄**（置 `null` 触发 `onDispose` 关掉那一颗 context），于是「从详情退回历史那一屏」不留一颗没人用也没人关的引擎。`val closing = session` + `onDispose { closing?.close() }` 那一字未动（批 80 立的判据、批 66 的姊妹形）。
- `ComicExplorePane(engine, config, onOpenComic)` 只剩界面；「重取」那一枚从前摸函数内的 `attempt`，现在递的是 `engine.retry()`。
- 域 body 的 `when (screen)` 五支各挂一屏；载荷与判断对不上时 `requireNotNull` 炸得响，不许静默画一屏空壳。

## 5. 两把旧尺子改判（手册 §0 坑 ㉖ 第十五次）

这两条钉的都是**当时那一版实现的形状**，而那个形状正是缺陷本体 —— 不改判就是"改代码必红它"，而它红的是错话：

- `DesktopComicDetailWiringTest` ②：`ComicExplorePane(config,`（域 body 把落点绕道递给探索那一屏）→ 改钉**载荷进了那颗纯判断**（`hasComic = comicTarget != null` 恰好一处）+ `ComicExplorePane(engine,`（引擎现在是域层持有的那颗）。挂载点那一枚锚点同时退到**被调件名**那一层（`DesktopComicDetailPane(`），实参表长到换行不该红在这里。
- `DesktopComicReaderWiringTest` ⑦：`readTarget?.let` 与 `comicTarget?.let` 两块的**先后** → 优先级那半归纯判断，由新尺子 `DesktopComicRowDispatchTest` 钉（同一件事只许一把尺子钉措辞）；这一颗改成钉它自己那一份接线顺序：**引擎持有件的调用排在 `when` 之前**，加上两枚载荷都进了判断。
- `DesktopStaleClaimsTest` 里「桌面没有阅读器」那条对照（`DesktopComicReaderPane(session,`）一字未动仍然咬得住 —— 那是**行为**断言，不是形状。

新尺子 `DesktopComicRowDispatchTest` 5 条：① 开着详情时不许再看是哪一行（三行各喂一遍）② 阅读比详情高一层、两枚各管一档、三行各回自己那一屏 ③ 只有真要问源站的三屏起引擎 ④ 两屏的渲染点提到域层（各组合恰好一次 + **反向**钉探索那一屏的整张参数表里不许再出现 `comicTarget`）⑤ 退场那颗恰好一处且挂在持有件里。

⚠️ ④ 的反向锚点第一版写的是 `ComicExplorePane\([^)]*comicTarget`，那只咬得住"载荷排在参数表**前部**"——把它添在 `onOpenComic` **之后**就绕过去了（`[^)]*` 吃不过 `(DesktopComicTarget)` 里那个右括号）。台架 C7 就是专门拿来量这一格的，判式因此改成取整张参数表（函数头 → 第一行函数体）。

## 6. 新尺子咬得住吗（扰动台架 `sh _qa/perturb_driver.sh b827 13`）

逐笔"改坏一种像真的假动作 → 看红在哪把尺子上"，13 笔全有账（前 9 笔一趟、后 4 笔见下面的说明）：

| 笔 | 改坏的形状 | 红在（该红的那一把） |
|---|---|---|
| C1 | 阅读不再比详情高一层 | `DesktopComicRowDispatchTest` ② |
| C2 | 详情载荷落回探索屏 | 同上 ① |
| C3 | 行优先吃掉详情载荷（**本批缺陷原样装回去**） | 同上 ②（并牵动 ①） |
| C4 | 不要引擎的两屏也起引擎（白装 GraalJS + 30 张封面） | 同上 ③ |
| C5 | 要引擎的三屏也不起 | 同上 ③ |
| C6 | 退场那颗变成空动作（句柄不关） | `DesktopComicDetailWiringTest` 钉 `closing?.close()` 那一枚 + RowDispatch ⑤ |
| C7 | 探索那一支又吃回载荷（**故意排在参数表末尾**） | RowDispatch ④ ⇒ 改成"整张参数表"之后这一格封住了 |
| C8 | 详情挂载点写两处 | DetailWiring ② 与 RowDispatch ④ 各红一次 |
| C9 | 阅读载荷没进判断 | `DesktopComicReaderWiringTest` ⑦ |

⚠️ **C10–C13 第一趟没收到账**：C10 那一笔跑到一半时我把它的 Gradle JVM 强杀了（为了在预算内收尾），于是 `report` 读到的是被 `rm -rf` 清空的 `test-results/` 目录，念出来的"BAD 一笔都没红"是**量具自己没读数**，不是尺子不咬。锚点全回起点后从第 10 笔续跑（`sh _qa/perturb_driver.sh b827 13 10`），四笔的账现齐了：

| 笔 | 改坏的形状 | 红在 |
|---|---|---|
| C10 | 详情载荷没进判断 | `DesktopComicDetailWiringTest` ② **与** `DesktopComicReaderWiringTest` ⑦（两把都咬 ⇒ 不是只钉形状那一把在动） |
| C11 | 接管时吃掉用户点名的行（第一步那条旧写法） | `DesktopComicSeedLandingTest`：「不许吃掉那一行」+「上一域的键原样交回」两条同红 |
| C12 | 两档念法塌成一句 | 同上「接管那一句念得出行留在哪一格」 |
| C13 | 正面那句不再念行那一截 | 同上那一条 |

**13 笔逐笔咬住自己的尺子，收尾全量 0 红**：`suites=127 tests_sum=981 testcase=981 @Test=981 failures=0`（三方口径对上，文件 134 颗）。

## 7. 起窗复测（缺陷那一格的读数现在念得出了）

同一条命令、同一个隔离目录（`_qa/b82_local`），批 82.7 第二步之后：

```
D_落点 域=漫画(启动) 行=阅读历史 浮层=关(换域或换页自动收) 查看器=关 详情=开(启动) 阅读=关(启动) 种子=comic
D_详情 源=jm 号=1461316 态=上屏 话=15 简介=81字 封面=有 话号=1461316,1461318,1461319,1461321,1461322
```

对照第一步那一次（`D_落点` 同一格、而 `D_详情` **一个字都没念**，帧上是历史那一屏）：这一趟 `D_详情` 念出来了，且是 `态=上屏` —— 载荷优先于行那条路**在真引擎真数据上走通了**（站方答复 15 话、简介 81 字、封面有）。stdout 无 `Error` 框、无异常行；关窗后 `java.exe` 残留 **0** 颗，真库未碰（数据目录指在 `_qa/b82_local/venera/db/`）。

**像素那一格也补上了**（第二趟起窗，同一命令同一目录，窗口标题 `Venera Desktop R1-F 最小闭环 #46328`）：帧上左侧 pane 的高亮行是**「阅读历史」**，而内容区画的是**详情那一屏** ——「← 返回列表　衣冠禽獸　收藏」+ 简介一段 +「共 15 话」+ 第1话…第15话 十五行。这正是第一步那一帧的反面（当时行=阅读历史而屏上是历史列表，`D_详情` 一个字没念）。`relatedTransientWindows` 为空 ⇒ 没有 `Error` 框。关窗后 `java.exe` 残留 **0** 颗。

⚠️ 图证只到"这一屏真画出来了"这一格：**详情那一屏本身还没画封面**（`封面=有` 是读数那一侧的事实，屏上只有题名/简介/章节表），那是另一件（§18 挂着）。


## 8. 数字与副作用账（全部现量）

- `:desktop:test` **981 条 / 127 颗类 / 0 失败**（`desktop/build/test-results/test/TEST-*.xml` 逐颗 `tests=` 之和）；判据目录 0 漂移（表行 127、表合计 981 与 XML 对上）。
- `desktop/src/main` 19,884 → **19,944** 行（第一步 19,869 → 19,884）。
- `app/`（受保域）`git status --porcelain app/` = **0 行**。
- 未提交、未 push；交付包没重打（批 81/82/82.5/82.6/82.7 都还压在 `_qa/desktop-exe-2026-10-10-b80r/` 那一枚外面，等点头）。
- README 两份（467 / 467 行对账）本批没有一句因此变假 ⇒ 一字未动。
- 只欠一格：真人那一路"从历史那一行按进详情"的**最后一跳**（OS 级鼠标输入是禁的，本批量到的是"载荷进判断 + 那一屏真渲染"两层，中间那一按只能记成没拍成）。像素图证与台架 13 笔都已齐账。

