# 批次 P · 全项目代码深挖（2026-10-01）

用户 2026-09-30 的原话：「你再对整个项目的代码深挖下，看下有什么代码可[以]优化和看起来就像是 ai 的东西优化下」。
拍定范围：**行为缺陷 + 死代码与僵尸横幅清理**；注释**只删三类**（复述代码 / 无信息分隔横幅 / 自我表扬），
论证式 KDoc、`⚠️`、`##` 分节、被推翻的旧文字**一律保留**；**大文件本轮不拆**。

**本轮一行未碰**：`VeneraApp.onCreate` 的主线程 IO 异步化（dsh 的首屏性能轮）。
`MainActivity.kt`、`VeneraApp.kt` 也刻意绕开（那两处仍有内部任务号注释未清，见文末）。

---

## 一、审计方法：每条主张自己复核过才算数

子代理给的清单只当线索。逐条 grep + Read 复核的结果，**有三条我最初报错了**，写在下面，
免得下一轮又被翻出来当成缺口：

| 最初主张 | 复核结果 |
| --- | --- |
| 「用户能存进一条编译不过的正则，规则静默永不命中」 | **不成立**。`BlockingSettings` 写库前早就拦了（原话：坏模式会静默永不命中，比报错更难查）。真漏的只有**恢复备份**那半边 |
| 「`addRule` 的 `catch → -1L` 是静默失效」 | **不成立**。四个调用点全部检查 `>= 0` 并 Toast/置 error |
| 「改 `VeneraNetworkClient` 波及 23 个调用点」 | **数字错**。23 是"引用到这个类的文件数"。真正走那三个 helper 的是 **19 处调用 / 5 个文件** |

---

## 二、P1：三个取数口子不判 HTTP 状态

`data/network/VeneraNetworkClient.kt` 的 `get`/`post`/`downloadBytes` 原本都是
`response.body?.string() ?: ""` 这一句了事 —— 403 的挑战页、500 的错误页被当成正文交回上游。

两条具体后果：
- **漫画源**：解析器拿到一段 HTML，屏上呈现「无结果」或一句 gson 错，真相是那个源被封了。
- **画廊另存/分享**（`gallery/data/GallerySaver.kt`）：一段 HTML 以"非空正文"通过
  `bytes.isEmpty()` 那道关 → 落盘成一个叫 `.jpg` 的网页，而提示说的是「已保存」。

**为什么这条必须改（同仓对照）**：本仓其余每条 HTTP 路早就判了状态并 `use{}` 关响应 ——
`DownloadManager`、`VeneraImageFetcher`、`ImagePipelinePolicy`、`AppUpdateChecker`、
`WebDavClient` 六处、`ComicSourceViewModel`。不判的只有这一个类。**这是随本仓自己的既有约定。**

**做法**：判据抽成纯函数 `data/network/HttpBodyVerdict.kt`（只吃状态码整数，所以能脱离 gradle 单跑），
非 2xx 抛 `HttpRejectedException`，文案 = 方法 + **去掉查询串**的地址 + 状态码
（查询串里可能挂着过盾票据与临时签名，那句会流进日志与界面）。三处都补了 `use`。

### 波及面逐点确认（19 处，全是内置 Kotlin 源）

| 调用点 | 包在哪 | 改后表现 |
| --- | --- | --- |
| `MangaDexSource` 6 处 | `try{}` 1 + `runCatching{}` 5 | 该源该次失败，不崩 |
| `BaoziMangaSource` 5 处 | `try{}` 1 + `runCatching{}` 4 | 同上 |
| `CopyMangaSource` 6 处 | `try{}` 2 + `runCatching{}` 4 | 同上 |
| `GallerySaver` 2 处 | `runCatching{}` → `Result` | 变成「保存失败: HTTP 403 …」—— **本轮真正的收益点** |

**错误确实上屏**：`ComicSourceManager.searchAggregatedStream` 把 `exceptionOrNull()?.message` 放进
`SourceSearchResult.error`，而 `SearchScreen.kt:1030` 直接把它当空态文案渲染 —— 也就是说源被封时，
那一格现在说的是「GET https://… 返回 HTTP 403」，不再是「检索无结果」这条假读数。

**零收益但也没风险的点**：`isValid()` / `bookshelfCount` 那两处 catch 后回 `-1L`，
错误消息按既有设计丢弃（改前改后都是"这个源不可用"）；`CopyMangaSource` 详情那处 catch 是
「容错降级」，标题回退成 comicId —— 两条行为一字未变。**没有把任何"空白"变成崩溃。**

**不受影响的一条大路（要说清，否则像"全网关都修了"）**：JS 源走 `engine/JsHttpHandler.kt` 里的
`okHttpClient` 自己发请求，不经过这三个 helper；图片管线走 `ImagePipelinePolicy`，也早已判状态。

---

## 三、P2：正则可编译性判据抽层，恢复备份那半边补上

- 判据抽到 `security/guard/GuardRulePattern.kt`（`compiles()` 用 `match()` **真正会用的那组选项**
  去编译一次 —— 只判语法不判选项，校验通过而运行时抛开的口子就又开回来了）。
- `BlockingSettings` 改为调用它：行为一字不变，只是不再重复实现。
- `sync/BackupManager.kt` 的恢复循环加了同一道关：编译不过的 `is_regex=1` 行**不入库**，
  条数走 `BackupSummary.guardRulesCount` 旁边新增的 **`guardRulesSkipped`**（刻意**不给默认值** ——
  漏传不报错的参数，下一笔恢复就会静默变成"一条也没跳过"）。
- 三处恢复完成的话都跟上了那半句「另有 N 条屏蔽规则写法有误，没有恢复」
  （`AppSettings`、`SyncBackupScreen` 两处）。

顺带记下一条**没动**的观察：备份恢复是直接写库，写完没有 `invalidate()` 正在运行的
`ContentGuardManager` 判定缓存 —— 那是另一轮的口子，本轮不扩范围。

## 四、P3：关注名单的损坏告知从来没接上

`GalleryArtistFollowsStore` 的类 KDoc 自己承诺「解不开时先留档，并通过 `notice` 说一句」，
两句人话也真写了，但**全仓无人 collect** 那一份 `notice`（唯一的 `consumeNotice()` 收集点在
**收藏**那个 store）。名单档损坏时用户只看到"我关注的画师全没了"，而那份 `*.corrupt-<时间戳>`
留档其实已经做好了 —— 不说等于没做。

接点选在**首页那一栏**（`GalleryScreen.kt` 里收 `follows` 的那处），不是详情页的画师行：
档坏时首页只剩空引导，那里才是"屏上必须说得出为什么空"的位置。写法照
`GalleryFavoritesBody.kt` 那一份（`LaunchedEffect(Unit)` + 读走即清）。

---

## 五、死代码：可逆归档清单

| 撤下 | 判据 | 镜像 |
| --- | --- | --- |
| `reader/BitmapSliceHelper.kt`（整 object 零引用） | 长图防 OOM 切片一整套，但阅读器从没接进这条；真机/模拟器无 "Texture too large"。**用户拍：归档** | `_trash/reader-bitmap-slice-2026-10-01/`（新写 README，含"要接线得先补的那三件事"） |
| `gallery/domain/GalleryArtists.kt` + 它的测试 | 批次 L 首页换成「正在关注的画师」后零调用点。**用户拍：连测试一起归档** | 镜像 09-30 就在 `_trash/gallery-artists-from-favorites-2026-09-30/`，`diff -q` 确认与原件一字不差后才删原件；README 补了后记 |
| `VeneraPreferences` 的 `doubleTapZoom`（值 + setter + key 三处） | 全仓无人读值、设置页无入口；而它的注释写着「telephoto 的 ZoomableAsyncImage 已在用」—— 阅读器确实用 telephoto，**但从没有把这个开关递过去**，是条断线 | 注释型僵尸，随 git 历史可回 |
| `GalleryFollowedArtists.kt` 那条指向 `[GalleryArtists]` 的注释 | 文件撤了，链接就是虚空的 → 改成自述 | — |

`pref_double_tap_zoom` 这个键在用户设备上**留着没删**（删代码不删已写入的偏好值，
将来真做双击缩放时同一键名还能接上老用户的选择）。

## 六、注释：本轮实际删改的三类

- **说错话的横幅**（不是无用，是错）：`DetailActionButton.kt` 末尾那条「3. 搜索页 (SearchPage) 1:1 复刻」
  压在文件尾、下面没有搜索页；`FavoritesScreen.kt` 那条「5. 探索页 (ExplorePage) 1:1 复刻」
  压在**收藏排序菜单**上。两条删。
- **内部任务号**（读者查不到的黑话）：`S0-3 / S5-5 / S7 / S8 批次C` 这类轮次编号从注释里摘掉，
  **共动 80 行注释**（三遍：纯编号括号整段删 29 行、行首编号与「编号：说明」式 32 行、
  编号混在散文里的 19 处逐条替换表），每行的前后文都打印出来逐条核对过。
  带真信息的括号只摘编号、留下"对齐原版哪个文件""appdata.settings 同名键"那半句。
  **脚本只在注释行上下手**，代码行一律跳过。
- **自我表扬**：`生产级` / `工业级` 那批（最坏的一条是 `VeneraReaderScreen.kt` 的
  「Venera 生产级 Jetpack Compose 工业级漫画阅读器 (S3 升级）」）；
  `VeneraFloatingNavBar.kt` 的「1:1 复刻原版 Flutter [NaviPane] 与 [LiquidGlassLens]」改成
  「照原版 Flutter 那套（`NaviPane` + `LiquidGlassLens`）」—— 去向说明留着，成绩式措辞去掉，
  那两个 Flutter 类名在本仓是不可解析的 KDoc 链接，也顺手改成普通文字。

**明确没动**：所有论证式 KDoc（为什么这么写、踩过的坑、真机读数与日期）、`⚠️` 标注、
`##` 风格分节、被推翻但留着的旧文字（`GalleryHomeSections.kt` 里 M7 推翻 / N2 撤回那段往返记录照旧）。
`HomeScreen.kt` 的「分区 2/3/4/5」与阅读器那批章节横幅**如实描述了下面的代码**，属分节不属"无信息"。

---

## 八、验证

**判据层（脱离 gradle 单跑，`_probe/l0/run-judgment-tests.sh`）**
- 先红：新增 6 条用例里 4 条按预期失败（`HttpBodyVerdict` 3 条 —— 占位实现把 403 的正文交回来了；
  `GuardRulePattern` 1 条 —— 占位实现一律放行）。**是断言失败，不是编译错误**，红得对。
- 后绿：`OK (49 tests)`（原有画廊那 43 条一条没退）。

**全量（gradle）**
- `:app:testDebugUnitTest` → **549 条 / 0 失败 / 0 错误 / 0 跳过**（72 份结果文件）。
- 交叉核过：在场 `.kt` 测试源里的 `@Test` 注解静态总数 **正好 549**，两个数不是各说各话。
- `:app:assembleDebug` 通过，四档 ABI 的 APK 都出来了（universal 那枚 105,997,920 字节，00:20）。

**一条如实交代的历史读数差**：`FREEZE-STATEMENT.md` 里 dsh 那轮写的是「559 条」。本轮撤了
`GalleryArtistsTest`（**实际 10 条**，那份归档 README 原先写「8 条」是错的，已就地改正）、
新增 6 条 —— 但 559−10+6=555≠549。差的那 6 条**无法核算**：上一轮的逐类结果 XML 已被本轮覆盖，
而 559 那个数我也没有当时的明细。**不拿不可复现的读数当基线算差值** —— 只认本轮这两个可复现的数
（549 跑出来的 / 549 静态数出来的）。

**真机（待设备）**：装机与下面四条复现还挂着，页面由用户点、我只读数。
1. 画廊里另存一张 → 相册里那张要能正常打开成图（P1 的复现点；改前会存成一个叫 `.jpg` 的网页）。
2. 设置 → 内容屏蔽 → 开「按正则匹配」→ 输 `18+(` → 仍报「正则写法有误」（P2 未回归）。
3. 把 `files/gallery_artist_follows.json` 改成半个 JSON 再进画廊 → 出「已另存为 …corrupt-…」那一句（P3）。
4. MangaDex / 包子 / CopyManga 三个内置源各搜一次 → 不闪退；断网或换坏源时那一格说的是 HTTP 状态而不是「检索无结果」。


## 七、刻意留下的口子

1. `Navigation.kt`（5 处任务号注释、其中 :170 那句「S2 会改成…现在先保持行为一致」已经过期）、
   `MainActivity.kt`、`VeneraApp.kt`（各 2 处）**没动**：前者是保护域，后两者 dsh 正在改首屏。
   要清就得单独给一次 Navigation.kt 的注释级豁免。
2. `_probe/`、`_qa/`（含 274MB `artist-diag.log`）、`_trash/` 仍未进 `.gitignore`。
3. `data/network/CfBypassEvidence.kt` 取证层是否撤。
4. `HistoryDao` 翻页全表重读（性能项，为避免与 dsh 撞车另开一轮）。
5. 备份恢复后没有 `ContentGuardManager.invalidate()`（见第三节末）。
