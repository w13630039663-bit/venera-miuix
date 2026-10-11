# 批 83 · 源管理那一屏（档①：列表 + 选源 + 停用，2026-10-10）

**一句话**：桌面今天只认随包那 34 颗源脚本，而"用哪一颗"是命令行上的一枚 `-Pkey=` —— 屏上看不见有哪几颗、也关不掉哪一颗。这一批把那一格变成一屏，并让"停用"真参与到装载的决策里。

门是用户今天撤的：「动手前不用问，直接开工」（手册 §18 那行、计划文件那行都已就地改判）。
`app/` 一字未动，未提交、未 push。

---

## 1. 量级是现查的，不是估的

| 疑问 | 读数 | 出处 |
|---|---|---|
| 桌面能不能读共享源表 | 能，但 `ComicSourceDao` 在 `desktop/src/main` 里**零 import** | `grep ComicSourceDao desktop/src/main` = 空 |
| 那张表能不能当"有哪几颗源"的真值 | **不能**：它首跑只播种五条写死的默认源（copymanga / picacg / mangadex / ehentai / nhentai） | `app/…/data/db/ComicSourceDao.kt:41-47` |
| 那"有哪几颗"的真值在哪 | 随包的 js 清单：`D_资源 来源=包内 /sources/ 清单=34 颗` | `EngineAssets.listSourceKeys` |
| 不启引擎能不能拿到显示名 | 能：**34/34** 颗脚本顶层都有 `name = "显示名"`（`jm.js:5`）；冒号形式只有 30 颗且那是**分区名** | `app/src/main/assets/sources/` 逐颗扫 |
| 桌面有没有现成的文件选择路 | 无 `FileChooser`（0 处）⇒ 导入要新写一条，本批不做 | `grep FileChooser desktop/src/main` = 空 |

**所以这一档选的是排期表自己那行建议的档①**（列表 + 选源 + 启用/停用），导入/导出仍按它那句"js 执行面风险大"搁着 —— 不是我另挑的更小的活。

## 2. 两份存储刻意不同轨（并把这句话写进读数）

`comic_source` 是"手机上装过哪些源"的元数据（图标、排序、config_json），这一批那份是"这台机器上哪几颗不参与选源"。并成一颗等于把 A 站的凭据发给 B 站（本仓量过一次的那一族）。落点：`prefs/venera_desktop_sources.json`，复用 `JsonKeyValueStore`（它自带三条行为判据：文件不存在=空表、半截 JSON 抛而不当"没配置"、写走临时文件再整体替换）。

写口那一条特意**不**做"空集就删键"：删键与写空数组今天读回来都是空表，但"用户主动全开"与"这份文件从来没写过"是两件事，而 `remove` 走的是另一条落盘分支。

## 3. 落地形状

- 数据层（`gallery/data/`）：`DesktopSourceNames`（头部现取，取不到交回 `null` 让调用点念 key）、`DesktopSourceCatalog.rows`（停用**不是删除**）、`DesktopSourceSelection.resolve`（四档：`Exact` / `FellBack` / `UnknownKey` / `NoneEnabled`）、`toggle` / `canDisable`（最后一颗启用着的关不掉）、`DesktopSourceState`（盘上那份）。
- 域层（`DesktopComicPane.kt`）：第四行「源管理」（`comic-sources`）、`DesktopComicScreen.SOURCES`、`desktopComicEngineNeeded` 把它归到**不要引擎**那一档（为了列个表去起 GraalJS + 30 张封面，正是批 82.7 那条的反面）、`val activeConfig = config.copy(sourceKey = sources.activeKey)` —— 引擎/详情/阅读/历史四支都吃**折完之后**的那颗 key。
- 那一屏（`DesktopComicSourcesScreen.kt`）：一行一颗（名字 + key + 「在用」/「已停用」+ 两枚动作），点行=选它，点角标=停用/启用，两枚"没生效"的句子都上屏。

## 4. 判据

- 新增三颗文件：`DesktopSourceCatalogTest` 12 条（纯判据，含"取不到不猜名""分区名不算源名""停用不是删除""四档落空各一句""两档不许叠成两份""最后一颗关不掉"）、`DesktopSourceStateTest` 5 条（读写同轨 / 全新数据是空表 / 全开后读到空 / 落盘文件名自证是桌面本地 / 那句读数）、`DesktopComicSourcesSemanticsTest` 7 条（离屏语义树：三行各摆一格、在用与已停用各标各的、按一次恰好落一次、两句"没生效"上屏、零停用念人话、**任何 `D_` 字面量不许出现在这一屏**、接线钉 `config.copy(sourceKey = sources.activeKey)` 与 `picked = key` 各恰好一处）。
- 改判三条旧尺子（都是"钉的是当时那一版形状"那一族，手册 §0 坑 ㉖）：`DesktopComicHistoryScreenTest` 那"三行"改成"四行并逐行点名"（只数颗数的判据会放过内容漂移）；`DesktopComicReaderWiringTest` ⑦ 的 `rememberComicEngine(config` → `rememberComicEngine(`；`DesktopStaleClaimsTest` 与 ⑦ 的挂载点锚点退到**被调件名**那一层（实参表换行不该红在这里）。
- 五处 `when (row.content)` 的穷尽检查自动跟着新档走（Kotlin 在表达式位置才查，那几颗 `val rendered: Unit =` 就是钩子）。

## 5. 图证抓到的一格（文本判据全绿而屏上是黑话）

第一次起窗（`-PstartDomain=COMIC -PstartSection=comic-sources`，隔离目录 `_qa/b83_local`）拍回来的帧上，标题下面那一行是：

```
D_源 停用=0 清单=34 口径=桌面本地（与共享表 comic_source 刻意不同轨）
```

—— 我把 stdout 的内部读数**原样摆上了界面**。998 条判据那时全绿。修法：屏上走人话（「清单共 34 颗，这一台机器上一颗都没停用」/ 有停用时念「停用了 N 颗」），`D_` 那两句只进 stdout；并补一条负向判据钉住它（见 §4 那颗语义树）。这条与"图证的第二个用途是读观感"同族：**帧不只是用来确认"有没有"，也是用来确认"念得像不像人话"**。

## 6. 起窗读数与帧（第二次，修完之后）

```
D_资源 来源=包内 /sources/ 清单=34 颗
D_落点 域=漫画(启动) 行=源管理 浮层=关(换域或换页自动收) 查看器=关 详情=关(启动) 阅读=关(启动) 种子=无
D_源 用=jm
```

`D_源` 只念一次、`D_资源` 那一句也只多出一行 ⇒ 名字是**安静地**从头部读的（没把 34 行资源读数糊满 stdout，那是 `displayNameOfKey` 不走 `EngineAssets.readText` 的理由）。帧上：34 行一颗一行、「禁漫天堂　（jm）」那一行标着「在用」并整行高亮、每行右侧一枚「停用」、顶部一句人话的停用数与那句"只影响这台机器"。`relatedTransientWindows` 空 ⇒ 无 Error 框；关窗后带标题的 Venera 窗 **0** 扇。

## 7. 数字与副作用账

- `:desktop:test` **1005 条 / 130 颗类 / 0 失败**（批 82.7 收尾时是 981 / 127 ⇒ 本批 +24 条 / +3 颗文件）。⚠️ 这个 0 只属于 **12:54 复跑收尾那一笔**：先前那一笔的"0 失败"是**旧 XML 的读数**（那次之后我把 `picked` 那一格挪了文件却没回看钉它的断言 ⇒ 恒红），台架还原后全量当场念 `failures=1 / EXIT=1`，红的是本批自己那把坏尺子 —— 事故全程在 §9。
- `desktop/src/main` 19,944 → **20,367 行 / 100 颗文件**（2026-10-10 12:41 现量，含那次"屏上黑话改人话"的补丁）；`app/` `git status --porcelain app/` = **0 行**。
- 数据目录全程指在 `_qa/b83_local`（起窗那一跑）与 `_qa/b82_local`（判据那一跑），真库未碰。
- 未提交、未 push；交付包**这一批没单独重打** —— 与批 82.8 合并一次（`createDistributable --rerun-tasks` 那一趟要 10 分钟以上，六批（81/82/82.5/82.6/82.7/83）压在同一包里比各打一次省，且 `_qa/check_pkg_jar_sha.js` 那把尺子只认"包内那颗 == 现产物"，合并打不影响它的判法）。
- 共享面现数（`node _qa/shared_face_counts.js`，2026-10-10 12:41）：srcDir 5 个、进桌面编译面 **86 颗 / 11,378 行**、其中含 `import android.` 的 **0** 颗、点名排除 **14 颗 / 去重 14**、「有禁引而未点名排除」= 无 —— 与批 82.7 收尾时逐字相同 ⇒ 本批三颗新件全住 `desktop/`，没往共享面添一格。
- 对外文档同步（两份各两处，改一处必同步另一处）：`README.md` / `README.en.md` 的桌面能力那句添「源管理那一屏」（随包 34 颗全列出、逐颗停用或启用、「选它」换当前源、名单落本机 `venera_desktop_sources.json`）；缺口那行从「源管理与网页登录」改成「**网页登录与源脚本导入**」，并明写这一屏**不是**导入器、不带登录。判据目录补三行、合计改口 **1005 条 / 130 行**。

## 8. 欠账（写清楚，别让它看起来像做完了）

1. **导入 / 导出**（档②的后半）：按排期表那句"js 执行面风险大"没做；真要做还差一格 —— 引擎读的必须是**用户可写目录**（今天读的是包内），否则"加了源"根本不生效。
2. 台架 14 笔逐笔红单：**已齐账，见 §9**（含那一笔量具事故：一把恒红的尺子把 14 笔全染红 ⇒ 手册新坑 **㊞**）。
3. 真窗口里那两枚动作（「停用/启用」「选它」）今天没按过 —— ⚠️ **原因要写对**：不是"OS 输入禁"（手册 §8.1 于 2026-10-09 22:29 就放行了桌面鼠标键盘，10-10 又加了"能不碰前台就不碰"的三档顺序），而是那条**留下来的硬边界**：点击位置必须是"自己知道在哪"的控件，**不许靠像素反推位置**。这一屏是 34 行列表，那一枚药丸在哪一行第几列要靠量布局才知道 ⇒ 它归不进第三档。正解是**补一枚取证种子**（`--disable=copy_manga` 那一族：起窗时直接把某颗源置为停用），让那一格由②档的 stdout + 像素自证，而不是伸手去猜坐标。
4. **这枚"键"是文件名，不是脚本里的 `key` 字段**（2026-10-10 12:46 现量，34 颗全量）：`EngineAssets.listSourceKeys` 取的是 `nameWithoutExtension`，与脚本 `key` 有 **3 颗不同**（`copy_manga_multi_accounts → copy_manga` 撞同一枚键、`komiic → Komiic`、`manhuagui → ManHuaGui` 只差大小写）。屏上摆的是显示名、盘上记的是文件名 ⇒ 今天自洽（读写同轨），但"停用 `copy_manga` 那一行"到底关的是哪一颗，在两颗拷贝漫画之间是有歧义的。三把键的完整账在 `docs/rounds/desktop-history-source-matching-2026-10-10.md` §1.1，本批不改（改它要动对岸口径，那是受保域）。
5. 换域再回漫画域时，当前源会不会回到启动那颗（`picked` 住在 `DesktopComicSourcesScreen.kt:72` 那枚 `remember { mutableStateOf(config.sourceKey) }` 里，而持有件是从域 body 调的 ⇒ 换域把 body 的组合停掉时它会一起没了；盘上那份停用清单却是真持久）—— ⚠️ 这一条是**从代码形状推出来的，没量过**。要量得先起窗、换域、再换回来，看 `D_源 用=` 那一句会不会变回 `jm`；本批没跑那一趟，所以只登记不结论（口径：屏上每句行为断言都要答得出"哪次量的"）。

## 9. 台架 14 笔（`_qa/b83_perturb.js` + `sh _qa/perturb_driver.sh b83 14`）

**先记一条量具事故，它比红单本身重要**：第一趟 14 笔跑完，`_qa/b83_p<N>.report.txt` 里**每一笔都含同一枚 RED** —— `DesktopComicSourcesSemanticsTest :: 引擎吃的是折过停用之后那颗key而不是启动时那颗`，报错都是「『选它』那一下没真的改当前源 expected:1 but was:0」。查下去不是"14 笔都咬住了同一格"，而是**那把尺子自己坏了**：它拿 `DesktopComicPane.kt` 去扫 `picked = key`，而那一句实际住在 `DesktopComicSourcesScreen.kt:94`（持有件与屏同颗，域 body 只是调用它）⇒ clean 树里恒 0 命中 ⇒ **恒红**。两个后果都要认：

1. 本批先前记的"1005 / 130 / 0 失败"是**旧 XML 的读数**（那次之后我把 `picked` 那一格挪了文件却没回看这条断言）；还原后全量那一跑念的是 `suites=130 tests_sum=1005 failures=1` —— **红的就是这把坏尺子**，`EXIT=1`。已经写进 §7/手册行 126 的 0 失败当场作废并改口。
2. 恒红那一格把**每一笔的判定都污染了**：P12 那一笔的红单里只剩这一条（它的报错文本是断言 ① 「当前源没被折进装载」，确实咬住了，但同一颗尺子内别的断言在替它喊），P14 那一笔的红单也只剩这一条（而报错文本恰好是 `picked = key` 那一格 —— 看着像咬住，其实这笔**无论有没有改坏都会红**）。⇒ 判"某笔咬住"的标准要写成：**该笔至少有一条属于别的尺子的红**，或者报错文本命中它自己那一格的锚点。P1–P11 各有别的尺子红（下表），P12/P13 有 `DesktopUnusedInternalTest`，P14 没有 ⇒ **P12/P14 修完尺子后重跑**（`sh _qa/perturb_driver.sh b83 14 12`，含收尾全量）。

| 笔 | 改坏的是什么 | 咬住它的尺子（除污染那条以外） |
|---|---|---|
| P1 | 落回不看停用 | `DesktopSourceCatalogTest` 两条（`NoneEnabled` 塌成 `FellBack`、落点选到停用那颗） |
| P2 | 全停用还硬挑一颗 | `全被停用时不许硬挑一颗开着的要交回空手` |
| P3 | 两档落空塌成一档 | `请求的键压根不存在时念没这颗而不算回落` |
| P4 | `[:=]` 宽判式吃分区名 | `只认顶层那一行赋值而分区名不算`（念出「每週必看」） |
| P5 | 取不到名字就编一个 | `头部没有那一行时不猜名字` + ② 同时红 |
| P6 | 标题不退到 key | `停用只吃掉那一格而不把行抹掉`（空串对上 `goda`） |
| P7 | 停用做成删除 | `…而不把行抹掉`（2 行变 1 行）+ `读数念得出而屏上不静默` |
| P8 | 开关只加不减 | `停用与启用是同一颗开关的两档而不许叠成两份` |
| P9 | 最后一颗也关得掉 | `只剩一颗启用着的那一颗不许再关` |
| P10 | 读数少念停用数 | `DesktopSourceStateTest :: 那句读数念得出停用几颗与本地口径` |
| P11 | 内部读数上屏 | `DesktopComicSourcesSemanticsTest :: 数念得出而内部读数不许上屏`（另一条断言，同颗不同格） |
| P12 | 引擎吃启动那颗 | 断言 ① `config.copy(sourceKey = sources.activeKey)` 命中 ⇒ 咬住（**待重跑复核**） |
| P13 | 那一屏不挂分岔 | 断言 ③ 命中 + `DesktopUnusedInternalTest` ①/③ 双红 ⇒ 咬住 |
| P14 | 「选它」写回旧值 | 修好尺子后应命中 `picked = key` 那一格（**必须重跑才算**） |

⇒ 修完之后的复跑账（P12/P13/P14 + 还原全量）见本节末尾；`node _qa/b83_perturb.js check` 在两次跑之间都念「14 笔各命中一处，可跑」= 源码全回起点。

**复跑收尾（2026-10-10 12:54，`sh _qa/perturb_driver.sh b83 14 12`）**：三笔各自命中**自己那一格**，不再互相替喊 ——
P12 红在 `当前源没被折进装载那颗参数`；P13 红在 `源管理那一屏没挂分岔` **另加** `DesktopUnusedInternalTest` ①/③ 双红（那一屏不挂分岔等于那颗 pane 只剩测试引用，正是本仓最不肯要的形状）；P14 红在 `「选它」那一下没真的改当前源`（这一条在第一趟是被"恒红"污染的，复跑才是真咬）。
还原后全量：**`EXIT=0` / `suites=130 tests_sum=1005 testcase=1005 @Test=1005 failures=0`**，`node _qa/check_ruler_catalog.js` = **0 处漂移**（表行 130 = XML 类 130、表合计 1005 = XML 合计 1005）⇒ §7 那句"0 失败"到此以**这一笔**为准（先前那一笔是旧 XML 的读数，已作废并留在账上）。
