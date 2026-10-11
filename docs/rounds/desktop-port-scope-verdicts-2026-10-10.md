# 桌面端精简裁决 —— 哪些 Android 功能不照搬（2026-10-10 成文）

**这份文档是唯一一把尺子。** 在此之前，"桌面端要不要搬某一项"这件事散在三个地方：
`desktop/build.gradle.kts` 的 `exclude(...)` 注释里、`docs/rounds/desktop-basic-gaps-plan-2026-10-05.md` §3 那份
「这一轮明确不做」清单里、以及各批 rounds 文档中各自的临时结论。三处会各自漂，而漂了没人红 ——
**判据只核"排除清单与禁引集合双向对得上"，不核"这颗为什么被排"**（那是人的判断，尺子管不着）。
本档把这些收拢成一张表 + 一套登记规则。

来源：两条 AI 线共用的方案文档（WorkBuddy 那条线 2026-10-10 提出，用户当日拍板四项，见 §1 表头）。
**本轮范围 = 只成文（W0 + W1）**；W2~W6 用户明确不点头，W7 搁置。
⚠️ 同日重排的 `remaining-plan-and-reuse-2026-10-10.md` §4 把 **W5（画廊图片落盘）排进 批 85 并做完了**（手册行 132）——
那一格以 §5 现在的这句为准；**W2/W3/W4/W6 仍等点头**（W6 在 2026-10-10 批 86 复核过一次，见那一行）。

---

## 0. 纪律（三条，破坏任何一条这份文档就没意义）

1. **`[术]` 与 `[值]` 不许混写。** `[术]` = 技术上做不到或有硬缺口（要能指出是哪一行 import、哪颗平台服务）；
   `[值]` = 技术上便宜但桌面场景价值不足（那是**我们的选择**）。把"我们不想做"写成"技术上做不到"
   就是本仓一号原则禁的那种假话。
2. **每条理由都要能在现码里 grep 到。** 本档每一行的锚点（文件:行）都是 2026-10-10 逐条量过的；
   改判决时要跟着改锚点，不许留"文档这么说"。
3. **"不搬"不等于"坏了"。** 桌面侧根本没有入口的屏，不存在要向原告用户解释的"缺席"——
   见 §3 最后一行那条关键判断。

---

## 1. 裁决表（18 项 + 本批新增的第 19 组「平台接线族」见 §4）

括号里的行数与被排除文件的行数一律**现量**（`wc -l`，2026-10-10），与来源方案不符的两处已在「现量」列点名。

| # | 项（现量行数） | 裁决 | 理由 | 人天 | 缺席登记 |
| --- | --- | --- | --- | --- | --- |
| 1 | `feature/StatsScreen.kt`（731） | **廉价做一个简版**：总时长·总页数·本数 + 14 天趋势；不做 Top10 / 题材云 / 下钻 | `[值]` 纯本地 DB + 自绘 Canvas，零平台 API —— 按技术可行性它排最便宜一档 | 0.5 | 不登记（做成可点项） |
| 2 | `feature/LogViewerScreen.kt`（228） | **不建那屏**，只在设置加一行「导出日志 / 打开日志目录」 | `[值]` 桌面上日志的价值**高于**手机（报障要贴 stdout）；桌面已有 `DesktopLogger.kt`（`D_日志` 前缀） | 0.5 | 反向：做成可点项，登记走 `notOnScreen`（§3 第二套机制） |
| 3 | `feature/HistoryScreen.kt`（425） | **降级 · 等阅读器** | `[值]` 纯 DB 很便宜，但**没有阅读器就没有会写进去的那一笔**，先建屏 = 一屏空表 = 假读数。⚠️ **那半句的前提已被批 81 撤掉**（桌面今天有阅读那一屏了，见 §6）—— 裁决本身不变（仍降级到批 82），但"差的是哪一层"要重述：今天差的不是"没有那一屏"，而是那一屏**只读不写**（`comic_history` 的写口没接） | 0 | 暂不登记，批 82 之后判 |
| 4 | `feature/FavoriteImagesScreen.kt`（**565**；来源方案写 936 ⇒ 与现码不符） | **已覆盖，不新建屏** | `[值]` 桌面收藏域已转正，且批 77 接上 `toggle` / `removeAll` / `clear` 三枚写口，等价屏已存在 | 0 | 不登记 |
| 5 | `feature/NetworkFavoritesScreen.kt`（**746**；来源方案写 1113 ⇒ 与现码不符） | **延后** | `[术]` 屏本身无 Android 依赖、桌面也有 `DesktopJsHost`，但它要**登录态** ⇒ 挂在 #9b 那格之后 | 0 | 若加行 → 行级 reason |
| 6 | `feature/SyncBackupScreen.kt`（442）+ `sync/` 整目录（1,969） | **只做文件导入导出**，不做 WebDAV 自动同步 | `[术]` WebDAV 走 OkHttp、zip 走 `java.util.zip`，都平台无关；Android 味只在 `Uri` / `FileProvider` / `ACTION_SEND` + `cacheDir`（`sync/BackupManager.kt`）。⚠️ **这条不靠摘 exclude 实现**：`sync/` 与 `feature/` 都不在那 5 颗 srcDir 里（见 §4 开头"两本账不是一张"那句）⇒ 落点是桌面自持一颗 zip 件，形状照批 79 的 B 路 | 1.5 | 不登记 |
| 7 | 画廊图片落盘（`gallery/data/GallerySaver.kt` 242 行、已在排除清单第 103 行点名） | **最小落盘 ≈150 行** | `[术]` Android 那侧是通知（`DownloadNotificationChannel`）+ SAF 存储根（`download/ComicStorageRoot.kt:65` `resolveTreePath(context, treeUri)`）；桌面换成"选一个目录 + 写文件"。⚠️ **漫画章节下载不算在这里**（依赖阅读器，留给批 81-82） | 1.5 | **必须改写首页下载行那枚 reason**（现文案在 `DesktopGalleryHomeSections.kt:220` 一带，条件写在 `:176`） |
| 8 | `reader/VeneraReaderScreen.kt`（2,202）+ `feature/LocalComicScreen.kt`（948） | **随阅读器** | `[术]` 那一屏整体不在桌面编译面（`reader/` 与 `feature/` 都没有 srcDir），本档不裁它 —— 留给批 81-82。**顺手量到一条对批 81 有用的**：承载 `ReaderSession` 的那颗 `reader/ComicPageSource.kt`（190 行）**零枚** `android.` / `androidx.` / `coil` import ⇒ 平台无关，接的是那颗而不是那屏 | 0 | 不登记 |
| 9 | `feature/sourcemanage/` ① 文件/网络导入 `.js` + 源参数配置 | **必做**（用户要求优先） | `[术]` 只需文件对话框；源脚本随包分发那条路已通（`build.gradle.kts` 的 resources include 那一段第一条目就是 `sources/*.js`） | 0.5 | — |
| 9b | `feature/sourcemanage/WebLoginScreen.kt`（313）② WebView 网页登录 | **只剩 (a) 那一形**（2026-10-11 批 88 量后改判，(c) 那一形同日下半落地，见 `desktop-web-login-verdict-2026-10-11.md` 与其后的落地文档） | `[术]` `:9` 是 `import android.webkit.WebView`，配套的 `CookieManager` 同源 —— 这一句仍然真；**两次改判**：① 缺口不是"没买 JCEF"，而是 (a) 那一形的宿主**加** (c) 那一形缺的入口与落盘；② (c) 已在批 88·下半落地（源管理那一屏给得出字段名单的源都有一格粘贴、罐落盘 `DesktopCookieJar`），所以**今天的缺口只有 (a) 那一形的宿主** —— 桌面结构性没有内嵌 WebView，而那一形要的正是它自己的 cookie 罐（`ccc` 那一族另要 `localStorage` 那两枚 token，桌面桥有没有那一路仍未量） | — | **登记为已知缺口**（手册 §18；对外文档那句「要 JCEF」已在批 88 改判，尺子 `DesktopReadmeStaleClaimsTest` ③ —— 它现在两头都挡：既挡"只差一颗内核"回流，也挡"罐还在内存里 / 缺一个粘贴入口"回流） |
| 10 | `feature/settings/UpdateCheckUi.kt`（341） | **砍** | `[术]` 那颗整颗是 Android 接线（`:3-10` 一口气 import `Manifest` / `Context` / `Intent` / `Uri` / `Build` / `Toast` / `rememberLauncherForActivityResult`），它只服务"应用内装 APK"（`REQUEST_INSTALL_PACKAGES`、ABI 选包、通知栏进度）；桌面走 MSI | 0 | **不登记** |
| 11 | `data/db/FollowUpdatesWorker.kt`（61，已在排除清单第 63 行点名） | **砍** | `[值]` 后台每日检查吃 WorkManager（`:47-55` 那段 `PeriodicWorkRequestBuilder` + `enqueueUniquePeriodicWork`）；gradle 里那颗的注释本来就明写"本阶段桌面不做后台追更" ⇒ 本档与注释**同口径**，不是新裁决 | 0 | 不登记 |
| 12 | SAF / MediaStore（`GallerySaver.kt`、`download/ComicStorageRoot.kt`） | **砍**，换成"选目录" | `[术]` 平台服务，桌面没有对应物 | 并入 #7 | 见 #7 |
| 13 | 分享 `ACTION_SEND` | **已覆盖大半** | `[值]` 批 54 的「复制图片」+ 批 77 的 `DesktopOpenInBrowser.kt` 已到位；"分享到别的 App"在桌面上没有对应语义 | 0 | 不登记 |
| 14 | 运行时权限体系（6 项权限） | **砍** | `[术]` 桌面没有运行时权限模型，6 项全手机专属 | 0 | **不登记** |
| 15 | 折叠屏 / 挖孔适配 | **砍** | `[术]` 桌面无这两种形态。⚠️ 反向记一条：桌面有"窄窗 / 宽窗"断点，**别把折叠适配误当响应式** | 0 | 不登记 |
| 16 | 常亮 / 音量键 | **砍 · 已登记，保持现状** | `[术]` 两枚已在 `DesktopGallerySettingsSpec.kt:192-195` 的 `notOnScreen` 里各自点名（那两枚字段是 `galleryKeepScreenOn` / `galleryVolumeKeyTurn`） | 0 | 保持现状 |
| 17 | MSI 安装包 | **搁置到最后** | `[值]` `build.gradle.kts:225` 那行 `targetFormats(Exe, Msi)` 已配好，但仓库里 **0 个 .msi**（`find` 全仓无对应物）、`_qa/desktop-exe-*` 12 个交付目录全是便携版 | — | — |
| 18 | `packageName` 改名 | **搁置到最后** | `[值]` 现在仍是 `venera-desktop-probe`（探针名）—— 这是**过期读数**，不是技术缺口 | — | — |

**本轮不占人天**（W2~W6 未点头）。来源方案那句"本轮合计 ≈ 4.1 天"指的是 W2~W6 全部批准的情形。

**砍功能反而避免了解冻**：表里 #3 `HistoryScreen.kt`、#4 `FavoritesScreen.kt`、#5 `NetworkFavoritesScreen.kt`
三颗都在 `FREEZE-STATEMENT.md:8-15` 那 8 颗 FROZEN 名单上（现数：那张表 8 行）。不照搬它们 ⇒ **不用开解冻豁免**；
而 #1 统计简版那颗 `StatsScreen.kt` **不在**冻结表内（那份声明 `:74` 自己写明过），所以 W4 若要做得不到豁免这一步。

---

## 2. 两类理由怎么分（口径，不是感觉）

判据只有一条，**问"要搬得动它，缺的是代码还是缺的是决定"**：

- 缺的是**代码**（要新引一颗依赖、要新写一层控件、要绕开一枚平台服务）⇒ `[术]`，且必须指出**具体哪一行**
  或**哪一颗平台件**。答不出那一行就不是 `[术]`，是"还没量"。
- 缺的是**决定**（能做、也便宜，但桌面上没人会用 / 有更合适的替代形状）⇒ `[值]`。
  典型：#3 与 #11 —— 两者技术上都不难，难的是"没有阅读器就没有那一笔"和"桌面没有后台调度"。

**这条纪律有历史依据**：本仓至少有三次把"我们还没量"写成"技术上做不到"，事后都被推翻
（批 38 词典那三枚"结构性豁免"、批 42 "桌面刻意没接 pixiv"、批 45 `autoPlaySec` "接得住"却整批退回）。
⇒ 以后凡是 `[术]` 那一档，都要留一条**能把它证伪的量**（curl 状态码、`javap` 条目名、jar 里有没有那颗 class）。

---

## 3. 缺席登记的三套机制（都已存在，本档不造新的）

**先说结论：域级 `DesktopAbsence` 一枚都不挂。** 三条理由：

1. 七枚域今天全 wired（`DesktopDomains.kt` 里 `absence` 全为 `null`，`isWired` 在 `:94`），
   挂回任一枚会让 `DesktopDomainNavigationTest` 那两条护栏**同时红**
   （`absent.size` 那一条与 `isWired` 恰好 7 那一条）—— 为登记去改护栏是拿护栏换便利。
2. 本档砍的项没有一个能让整域进不去（落盘、阅读器都落在漫画域内部，而漫画域已 wired）。
3. `NO_CHAIN` 那形状历史上**零实例化**；第一次用它去描述一个**主动不做的决定**，
   会把"不照搬"歪曲成"这台机器上没有"—— 正是 §0 一号原则禁的那句假话。

**按"这道缺席会被谁观察到"分流**：

| 被谁观察到 | 用哪套机制 | 现成锚点 | 本档里的项 |
| --- | --- | --- | --- |
| 首页某一行点不动 | `DesktopGalleryHomeRow.reason` | 字段在 `DesktopGalleryHomeSections.kt:162`，兑现条件写在 `:170-176` | 下载行（#7） |
| 设置里某一档不在屏上 | `DesktopGallerySettingsSpec.notOnScreen` / `STRUCTURAL_ABSENT` | `:192-195`（两枚 `Absent`）与 `:198`（两枚结构性） | 常亮、音量键（#16）、日志导出（#2） |
| 漫画域内某一页没有 | **新建**行级 reason（形状照首页那一套） | 无 —— 这一类原有的两格都落地了，今天没有新项 | ~~章节可点~~（**批 81 已落地**：详情里那一行点得动，见 `desktop-comic-reader-screen-2026-10-10.md` §11）、~~阅读历史~~（**批 82 已落地**：写口 + 那一屏，见 `desktop-comic-history-write-2026-10-10.md`；**图库域那行「历史」不在此列** —— 它要的是 gallery 自己的浏览历史，今天仍未点亮） |
| **桌面侧根本没有入口** | **不登记** | — | Stats(#1)、UpdateCheck(#10)、权限(#14)、SAF(#12)、折叠(#15)、WebView 登录(#9b)、分享(#13) |

最后一行是本档的关键判断：**Android 有而桌面没有入口的屏，桌面侧不存在"缺席"要说明的问题**。
为一枚不存在的按钮道歉，等于把"没搬"说成"坏了"，而用户会去查一个不存在的设置项。

---

## 4. 排除清单逐颗对账（14 颗点名 + 1 枚 glob）

**现数（2026-10-10，`node _qa/shared_face_counts.js`，口径逐字照 `app/src/test/java/com/venera/compose/testsupport/DesktopFace.kt`）**：
5 颗 srcDir / srcDir 下候选 `.kt` 100 颗（`android/` 子目录不计）/ **点名排除 14 颗**
（另有 `exclude("android/**")` 一枚 glob，那颗是目录族、不逐颗计名）/ **进桌面编译面 86 颗 / 11,378 行** /
其中含 `import android.` 的 **0 颗**。

**先说清一件容易混的事：两本账不是一张。** 裁决表有 18 项而排除清单只有 14 颗 ——
因为 `feature/`（所有屏）、`sync/`、`reader/`、`data/update/` 这些目录**从来就不在那 5 颗 srcDir 里**，
它们没被"排除"过，只是从未被借出去。所以"某屏不搬"**不会**体现在 `exclude(...)` 里，
而"某颗共享文件上不了桌面"也**不等于**"那屏不搬"。把两张混成一张，就会去构建脚本里找一条根本不存在的行。

⚠️ 来源方案写的是「排除 24 颗后剩 86 文件 / 11,292 行」，两处与现码不符：
**86 颗对上**（说明那份数的也是"进编译面"那一头），而 **24 颗在构建脚本里没有对应物**
（`excludeEntries()` 数出 14，把那枚 glob 并进去也才 15）；11,292 行是旧数，之后各批往共享面加了 86 行。
⇒ **本档以后一律引 `shared_face_counts.js` 的现数**，那份方案里的这两个数作废，别再抄。

**对账尺子在 `:app` 侧**（`app/src/test/java/com/venera/compose/desktop/DesktopSharedFaceLedgerTest.kt`，2 条用例）：
一条钉"禁引文件与排除清单**双向**对得上"（缺排除 ⇒ 桌面编不过而编译器说不清成因；多排除 ⇒ 该摘行而没人摘），
一条钉"每颗被排出的文件都说得出被**哪一族禁引**挡住"（六族 `FORBIDDEN`：`android.` / `androidx.` / `coil` /
`data.prefs.` / `data.network.` / `…​.android.`）。
**判据是禁引集合，不是 `^import android`** —— 反例写在 gradle 注释里：`GalleryImageLoader.kt` 只 import 了一句
`android.content.Context`，真正钉死它的是间接吃的 `data.prefs` + `data.network`。

| gradle 行 | 排除的那颗 | 归哪一裁决行 | 标签 | 一句话 |
| --- | --- | --- | --- | --- |
| 61 | `VeneraDatabase.kt` | 本档之外的平台接线（见下面「第四组」） | `[术]` | Android `SQLiteOpenHelper`；桌面走 `JdbcSqliteDatabase`，这颗没有桌面调用方 |
| 62 | `FollowUpdatesRepository.kt` | #11 | `[值]` | 与 Worker 成对；构造吃 `Context` 且依赖 `ComicSourceManager` |
| 63 | `FollowUpdatesWorker.kt` | #11 | `[值]` | androidx.work；桌面没有后台调度面 |
| 93 | `GalleryAnimationGate.kt` | 第四组 | `[术]` | 整颗是 coil3 的 Extras/Decoder 管线；批 47 量过 `coil-gif` 3.6.2 **无 `-jvm` 变体** |
| 95 | `GalleryConnectivity.kt` | #14 | `[术]` | `ConnectivityManager` 是平台服务，桌面要另一套判据 |
| 97 | `GalleryFeedCache.kt` | 第四组 | `[术]` | 日榜在途缓存，形状是 `read(app)` / `write(app,…)` |
| 99 | `GalleryForYouCache.kt` | 第四组 | `[术]` | 同上那颗的另一枚；批 24 那句"跨进程没有快照"就是它 |
| 101 | `GalleryImageLoader.kt` | #7/#12 的邻位 | `[术]` | coil3 + `data.prefs` + `data.network`；批 79 桌面另起 `DesktopImagePipeline` 正是绕开它的办法 |
| 103 | `GallerySaver.kt` | **#7 / #12** | `[术]` | MediaStore + ContentValues + Environment —— 本档唯一"排除了而裁决要做"的一颗（换成"选目录 + 写文件"） |
| 107 | `GalleryTagDictionary.kt` | 第四组 | `[术]` | 词典库是 `assets` 复制出来的真实路径；批 40 桌面用 `DesktopTagDictionaryAsset` 自持补上 |
| 110 | `GelbooruAccount.kt` | 第四组 | `[术]` | `Context` + `AndroidKeyValueStore`；客户端只认它实现的 `GelbooruCredentials` |
| 112 | `SauceNaoAccount.kt` | 第四组 | `[术]` | 同上；批 34 量到"两颗同名接口成员并一棵会把 A 站凭据发给 B 站"，桌面自持独立件 |
| 115 | `GalleryFeedSource.kt` | 第四组 | `[术]` | 存在的唯一理由是 Android 那两处 `getInstance(context)` 零改动；编排本体在 `GalleryDailyFeed.kt`（共享面，已进编译） |
| 118 | `ContentGuardManager.kt` | 第四组 | `[术]` | assets 源级预设表 + `VeneraPreferences` + `StartupTrace`；桌面屏蔽直连 `data/db/GuardRuleStore` |
| 25 | `exclude("android/**")`（glob） | 本档之外 | `[术]` | 三颗 srcDir 里的 Android 接线子目录，整族不参与这本账 |

### 第四组「平台接线族」为什么不进裁决表

那 11 颗（61/93/97/99/107/110/112/115/118 + 25 那一族 + 61 那半）不是"Android 有的功能桌面要不要搬"，
而是**同一份逻辑在 Android 侧的那层接线**——逻辑本体（`GalleryDailyFeed`、`GuardRuleStore`、`TagDictionaryStore`、
`GalleryRankings` 那类）**早就在共享面里并进了桌面编译面**。对它们下"砍/做"的裁决是类别错误：
要裁决的永远是"屏"，不是"那层 `Context`"。⇒ 这张对账表把它们单列一组，只写"保持排除 + 被哪一族禁引挡住"。

### 收编 2026-10-05 那份「这一轮明确不做」六条（`desktop-basic-gaps-plan-2026-10-05.md:64-71`）

**必须收编，否则同一件事两把尺子。** 逐条重判的结果是：**六条里五条已经被后续批次推翻或改判**，
只有一条今天仍成立 —— 那份文档留着原样就是假读数。

| 10-05 那条 | 今天的状态 | 凭什么 |
| --- | --- | --- |
| 下载 / 另存为 / 批量下载 | **改判**：#7 最小落盘**已落地**（同日批 85，W5 那半）；漫画章节下载仍随阅读器，另存为对话框不做 | 本档 §1 第 7 行 |
| 历史那一行「没有本地历史存储」是实话 | **已推翻**：假在"没有存储"那半，`data/db` 里确实有 `comic_history`（`HistoryDao`），而桌面自己就有一颗 `HistoryDaoTest` 在读写它；屏上那句已在批 14 改写成"桌面没有阅读器，也就没有会记下阅读进度的那一笔"；**批 82 把那半句也改掉了** —— 写口今天有了，那一行改成点名"gallery 这一侧的浏览历史还没存过"（与 `comic_history` 是两回事） | `DesktopGalleryHomeSections.kt:170-174` 那条注释就是这一改判的现码记录；本档 #3 沿用 |
| 排行榜那一行的缺席原因也是实话 | **已推翻**：批 28 排行榜那一屏点亮（六档 / 三腿 / 历史期回看全做了），共享面 `GalleryRankings` 被真吃 | 手册 §57（批 28） |
| 以图 / MD5 反搜「桌面桩是直接抛」 | **已推翻**：批 34（键的输入口）+ 批 35（抛桩摘了、卡上入口与结果那一格）+ 批 77 下半（本机的一张图） | 手册 §63 / §64 / §112 |
| 清缓存 / 批量移除收藏 / 熔断复位 | **部分推翻**：批量移除收藏那枚 = 收藏那一屏的「清空」两步确认（批 77 上半）已落地；清缓存与熔断复位仍不做，且**桌面侧根本没有入口 ⇒ 按 §3 不登记** | 手册 §111 |
| 标签译名「词典 sqlite 不随包分发 ⇒ 结构性没有」 | **已推翻**：批 40 词典进包（jar +1,672,700 B、只读打开、79,415 行），三处译名消费点第一次真跑 | 手册 §69 |

⇒ **那张表从今天起取代 `desktop-basic-gaps-plan-2026-10-05.md` §3**；那份文档的 §3 不删（历史叙述要留在原地），
但接手方读到它时**以本档为准**。

---

## 5. 批次与冲突面（本轮只到 W1）

| 批 | 内容 | 状态 | 与漫画阅读器那条线（批 81-82）冲突面 |
| --- | --- | --- | --- |
| W0 | 判据目录去漂移（补登记 + 表头改成现数） | **已做**：`node _qa/check_ruler_catalog.js` = 0 处漂移，**878 条 / 112 类 / 0 失败**（批 80 收尾同一次实跑） | 零 |
| W1 | 本档成文 + 手册四处回写 + `desktop/build.gradle.kts` 文件头一行路标 | **本批** | 低（只碰文件头 KDoc；`exclude(...)` 块内一个字不加，那正是批 81 最可能增删条目的地方） |
| W2 | 源脚本导入 ①（#9） | **未点头** | 低 |
| W3 | 日志导出（#2） | **未点头** | 零 |
| W4 | 统计简版（#1） | **未点头** | 零 |
| W5 | 画廊图片落盘（#7 / #12） | **已落地**（同日**批 85**，手册行 132）：桌面自持 `DesktopImageSaver` + 浮层那颗「存到本机」+ 首页「下载」那一屏 + `--save` 种子。⚠️ 这一格从前写「未点头」，被同日重排的 `remaining-plan-and-reuse-2026-10-10.md` §4 排进 批 85 并做完 —— 两本账以本行现在这句为准 | 低（不碰漫画文件） |
| W6 | 备份导入导出（#6，桌面自持 zip，**不摘 exclude**） | **仍未点头**（2026-10-10 批 86 复核：排期表曾把它捆进 批 86，本批按这一行那句"明确不点头"退回来，改吃 批 89 的那半）。⚠️ 真要开工时这一行的"不摘 exclude"要**重判一次**：现量发现归档那颗件根本不必引库（`java.util.zip`），而 `sync/GalleryBackupRows` 那层"磁盘格式 == 归档格式"的定义若在桌面重述一遍就是两本账 | 低 |
| W7 | 搁置：MSI + `packageName`（#17/#18） | **搁置** | **高**（那颗就在 `build.gradle.kts` 的打包区） |

**不抢**：阅读器 / 章节可点 / 本地漫画 / 网络收藏 —— 全是批 81-82+ 的面。（⚠️ 2026-10-10 同日回写：**批 81 已把"阅读器 / 章节可点"这两格落地**，剩下"本地漫画 / 网络收藏"仍不抢。）
`DesktopComicDetail.kt`、`DesktopComicPane.kt` 与 `app/src/main/**` 本档一个字没改（8 颗 FROZEN 与导航层是受保域）。

---

## 6. 变更日志

- **2026-10-10 首成文**（W1）。全部锚点与行数现量：`wc -l` 逐颗、`node _qa/shared_face_counts.js`（共享面那笔账）、
  `desktop/build.gradle.kts` 逐行、`grep` 逐条理由里的符号名。
  与来源方案的两处数字差异（FavoriteImages 936→565、NetworkFavorites 1113→746）与两处口径差异
  （exclude 24 颗无对应物 → 现数 14 颗；11,292 行 → 11,378 行）已在本档点名并**按现码写**。
- **2026-10-10 W0**：`docs/rounds/desktop-ruler-catalog.md` 补登记 `DesktopComicSeedLandingTest.kt`、
  `DesktopComicDetailWiringTest.kt` 的条数从 6 改成实跑 8，表头改成 878 条 / 113 行；
  另加两条量具口径（那张表**按类名配对**，不是按文件名；跑对账前先确认 XML 是这一笔的）。
