# 批 96 · 漫画「把一整话存到本机」那一格（2026-10-12）

题面来自排期 `remaining-plan-and-reuse-2026-10-10.md` §4 那一行：**批 85 接上了存一张图，而「把一整话存到本机」还没接** —— 那句话本身就是要一起改掉的东西（本仓那一族"文案说没有而东西已在"，它活了四批）。

## 0. 这一批改了什么

| 件 | 改了什么 | 为什么是这一形 |
| --- | --- | --- |
| `gallery/data/DesktopComicChapterDownload.kt`（新建） | 命名口径 + 清单读写 + 「这一话齐不齐」那一判 + 那颗 runner（`desktopComicDownloadChapter`） | **不吃引擎句柄**：取页那一格由调用点递进来（`fetch: suspend (Int) -> ByteArray`），所以这颗件能被单测直接喂着跑，也能被离屏语义树渲出来 —— 与这一屏其余三格（目录、收藏、续读）同一条分家规矩 |
| `gallery/ui/DesktopComicDetail.kt` | 章表那一行从 `clickable` 换成 `combinedClickable(onClick, onLongClick)`；装载层多一颗 `runChapterDownload`（**全树唯一一颗** runner）；三档落空各有一句读数；`--download=` 的 `LaunchedEffect` | 行里再嵌一枚 `clickable` 会把行本身的点击吃掉（三颗既有的语义树判据当场红），而 `combinedClickable` 有 `onLongClick` **没有** `onRightClick` ⇒ 桌面第二枚动作只能挂长按，那句说明就必须上屏 |
| `gallery/ui/DesktopComicPane.kt` | `DesktopComicEngineConfig` 多一枚必填 `downloadSeed`；详情那一屏的挂载点**全改命名实参** | 那一屏的形参表今天十枚，而 `onBack` / `onFavorite` / `onOpenEpisode` / `onDownloadEpisode` 之间全是同形 lambda ⇒ 位置实参插一枚进去会安静地把种子递进别人的格子，编译照过 |
| `ForensicFlags.kt` | `--download=<漫画号>/<话号>` 判读 + `downloadNote` 落空那句 + 登记 `KNOWN` | 值用**话号**而不是行号：行号会漂是批 94 挂下的那条账（源站中间插一话，行号就指到别的话上去），拿行号当种子就是"种一次对、下一次错" |
| `desktop/build.gradle.kts` | `-Pdownload=` 那一跳拼进进程参数 | 本仓那条第十一次：**判读层与消费点都接好了也不算通**，漏这一跳就是"不报错也不做事" |
| `VeneraDesktop.kt` | 判读、落空那句、递进 config | 同上 |
| `README.md` / `README.en.md` | 「漫画章节下载」那一格改成"整话已接 + 还缺的是队列那一屏" | 两份同批改（口径：改一处必须同步另一处），并给那句旧假话补了一把尺子（`DesktopReadmeStaleClaimsTest` ④） |

## 1. 量到的事实（全部现量）

### 对岸那条动线（`app/src/main/java/com/venera/compose/download/DownloadManager.kt`，只读不动）

- **净化三处而两处不一致**：`:155` `val safeTitle = ch.title.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()`（**多一步 trim**），而 `:321` / `:322` 的 `safeKey` / `safeId` 同一张字符表**没有** `.trim()`。桌面**照抄这条不一致**而不"顺手统一" —— 统一了就会与对岸已经写在盘上的目录名不同轨，而那一档在用户眼里的形状是"同一本漫画有两个目录"。台架 P1 专门去打这一条。
- **章节目录名** `:156`：`${ch.order.toString().padStart(4, '0')}_$safeTitle`；**页名** `:412`：`${(idx + 1).toString().padStart(4, '0')}.jpg` —— 库里 0 基数而名从 1 数起，扩展名对岸是**写死 `.jpg`** 的，桌面不自己判断真实格式（判了就是另起一套名，台架 P2 打这一条）。
- **清单**：`chapter.json`，字段 `chapterId` / `title` / `order` / `expectedPages` / `complete`（`:502-511` 那份 `writeChapterMeta` 走 `chapter.json.tmp` + 替换改名）。
  - 🔴 **本行由第九十七批改判**（原文只核了**字段名**是同一份，没核**每格装的是哪一层的 id**）：对岸 `:505` 写的是 `put("chapterId", task.chapterId)` ⇒ 那格是**话号**，
    而桌面当天写进去的是 **comicId**（盘上实证 `_qa/b96_local/venera/downloads/jm/1461316/0001_第2話/chapter.json` 念 `"chapterId":"1461316"`，而那趟种的是 `--comic=1461316 --download=1461316/1461318`）。
    所以"清单五字段逐字同轨"这句**只在字段名上成立**，两端读同一份 `chapter.json` 时互认是断的（详 `desktop-comic-downloads-screen-2026-10-12.md` §6）。
    本节上面那两条（净化三处、目录名与页名）经复核**仍然成立** —— 那几处确实逐字对上了，包括对岸自己那处 `.trim()` 的不一致。
- ⚠️ 对岸 `:249` 那句注释是本批判据的直接来源：**「有图」不等于「下完」** —— 它的旧判据就是只看有没有文件，而 `chapter.json` 在开下载**之前**就写。所以桌面的「齐不齐」那一判两样都要看（清单说 `complete:true` **且** `0001..NNNN.jpg` 一颗不缺且非空），台架 P3 撤掉逐页那一档就会红。
- 一处**有意不同**：对岸的 `expectedPages` 可以为 `null`（清单先写、页数后补，`:378-380`），桌面的清单**永远带页数** —— 因为这一侧先问到页序列才动笔，没有"开下载之前"那一档。这不是统一口径而是形状不同，登记在此。

### 桌面已有的地基与那条真风险

- 桌面今天会"存一张图"（`desktopSaveImageRun`，只吃 `GalleryPost`）、会"读一页"（`engine.pageImage`），缺的正是中间那颗：**取一整话的页序列 → 逐页落盘 → 判齐不齐**。
- 🔴 **两条落盘路会不会互相扫**（本批只读量掉的一条真风险）：画廊那一格落点是 `数据根/downloads/图库`（`DesktopImageSaver.kt:50-51`），而漫画这一格是 `数据根/downloads/<站名键>/<号>/<话目录>/NNNN.jpg` —— 两者是**父子**关系。首页「下载」那一屏（`DesktopDownloadsPane` → `DesktopImageSaver.kt:221` 那颗枚举）**只数文件、不递归**，所以漫画那三层在盘上不会混进那一屏（`listFiles` 拿到的目录名不参与计数）。这一条是"量过所以放心"，不是"应该没问题"。
- 长按那一枚动作在语义树里量得到挂没挂上（`SemanticsActions.OnLongClick`），**量不到落盘**；真起窗量得到落盘而**走不到那一下**（不许动鼠标，手册 §8.1）⇒ 这就是本批必须补一枚取证种子的理由，而不是"顺手加的把手"。

## 2. 三处形状与它们的代价

1. **runner 只许一颗**：种子与长按都走 `runChapterDownload`，判式是**计数**（`runChapterDownload` 全 main 三处 = 声明 + 两处调用；`desktopComicChapterDir(` 两处 = 声明 + 一处调用）。为什么不用 `contains`：本仓那条"接线类判据要数出现次数"，而台架 P6 那一笔（假动作：读数照念、状态照设、一颗文件都不写）正是被计数判式抓住的形状。
2. **失败必抛**：`desktopComicDownloadChapter` 有一页取不到就先把清单写成 `complete=false` 再抛 `IllegalStateException`（带页号 + 站方原话）。已写的页留在盘上不清 —— 屏上那一行念「下载失败：…」，而下一趟会补缺的那几页而不是当成下全了。台架 P4 把 `throw` 换成 `return written` 就红在这一颗。
3. **已齐的那一趟一页都不取**（对岸那句"点一下就直接重下一遍"的坑），台架 P5 把早退换成恒假条件 ⇒ 取页调用次数从 0 变 2 就红。

## 3. 台架（`_qa/b96_perturb.js`，**16 笔**，台账 `_qa/b96_perturb.summary.txt`）

`check` 阶段先要求每笔起点在干净树上恰好命中一处、终点 0 处（读数：`16 笔各命中一处，可跑`）。
第一版只有 12 笔；**P5 那一笔跑出一句 `BAD 一笔都没红`**，查实之后本批补了 P12（两闸一起撤）、P13（新尺子的回流自审）、P14/P15（包内那一帧逼出来的两颗新锚点），等价改写挪到 P16。

| 笔 | 改坏的那一格 | 红了谁 | 判 |
| --- | --- | --- | --- |
| P1 | 净化 `sourceKey`/`comicId` 那一档也顺手 `.trim()` | **只** 数据层 ① | 那条"对岸自己不一致"的账独立咬得住（红的是 `expected:<[  jm  ]> but was:<[jm]>`） |
| P2 | 页名扩展名 `.jpg` 换成 `.png` | ③ + ⑤ + ⑦ + ⑧ 四颗 | 页名不是孤立的一句字符串断言：三颗在真临时目录里跑盘面的用例全靠它 |
| P3 | 「齐不齐」那一判只看清单、不逐页查盘 | **只 ⑤**（`清单说齐而盘上少一页那一档不算齐`） | 对岸 `DownloadManager.kt:249` 那句「有图不等于下完」的账，桌面这一判独立咬得住 |
| P4 | 取页失败那一档 `return written` 而不抛 | **只 ⑦**（`那一趟本该抛出异常而它成了：null`） | 那条降级口径的唯一消费者就是它，隔离得开 |
| P5 | 已齐那一档的早退换成恒假条件 | **一笔都没红** | ⚠️ 不是尺子瞎 —— 这一格今天有**两道闸**（`:149` 早退 + `:154` 逐页跳过），单撤任一道另一道照样把 2 次取页挡成 0 次 ⇒ 两闸互为冗余。由 P12 补证 |
| P6 | 种子那一笔改成"设一格状态而不下载"（假动作） | **只** 种子那颗 ⑤（`runChapterDownload` 计数 3→2） | 本批最要紧那一格的唯一证据：`contains` 那类锚点在这一笔上全绿，只有计数咬得住 |
| P7 | `private const val DOWNLOAD` 改成 `--dl` | 种子 ①②④ 三颗 | 参数名只许一处出处：改了常量就是"认的"与"登记的"两张皮 |
| P8 | `downloadNote` 不看 `detailOpens`（恒念） | 种子 ③ | 两个方向都钉得住：恒念会把正常起窗染成假实话，不念会把打错的参数读成"下载坏了" |
| P9 | 详情那一屏的形参给了默认值 | 种子 ④ | 本仓那条"漏传不报错的参数不要用默认值"第十一次 |
| P10 | 构建脚本那一跳被注释掉 | 种子 ④ | 判式**只看非注释行**才咬得住 —— 要说清证据级别："`readText()` 那一版不咬"是**设计时的自审**，从没跑过，不是量过的读数 |
| P11 | 对外文档那句旧假话搬回来 | `DesktopReadmeStaleClaimsTest` ④ | README 那一格从本批起有机械尺子（从前只有屏上的话有） |
| P12 | **两道闸一起撤** | 数据层 ⑧（`写的页数 expected:<0> but was:<2>`） | 证 ⑧ 真的管"不许整话重下"，而不是管一句写法 |
| P13 | `DesktopGalleryHomeSections.kt` 注释里那句旧假话逐字回流 | `DesktopStaleClaimsTest` ㉓ | 新量具"能证到哪一格"由扰动笔量出来（本仓那条，第四次） |
| P14 | 失败那一档的分母又塌成 `0` | `DesktopComicDetailWiringTest` ⑨（`expected:<89> but was:<0>`） | 这一笔打的是**包内 b96d1 真量到的那一形**（见 §4） |
| P15 | 调用点不再记页数（`knownPages = 0`） | ⑩（`页表之后没有先记下页数 expected:<1> but was:<0>`） | 收口那颗纯函数自己是对的，而调用点递 0 进去它照样错 —— 接线与算式各有各的尺子 |
| P16 | 等价改写：命中读数里 `${index + 1}` 换成 `${index.inc()}` | 一笔没红 | **正确输出** ⇒ 尺子钉的是值不是写法 |

收尾"还原后的全量"：`EXIT=0`、`suites=152 tests_sum=1287 testcase=1287 @Test=1287 files=159 failures=0`。

## 3.1 包内那一帧抓出来的一写真缺陷（不是取证缺口）

b96d1 那一趟念的是：

```
D_下载 号=1461316 话=1 页=0/0 全=否 因=取页失败 页=63 共=89 因=stream was reset: CANCEL
```

屏上那一行同形（「下载失败：…」），而**分母是假的**：那一话有 89 页，读数说 0 页里写了 0 页。成因在装载层那两行 `outcome.getOrNull()?.first ?: 0` —— 抛出时 `Result` 里连页表那一步答出来的数一起没了。
处置：收成一颗可测的 `desktopComicChapterOutcome(index, outcome, knownPages)`，页表一答出来就把页数记进 `knownPages`，失败分支吃它当分母；页表也没拿到时 `0/0` 才是实话（**不许编一个分母**）。判据 ⑨（三档输入真跑）+ ⑩（接线计数与负锚点），台架 P14/P15 各咬一条。
⚠️ 这条缺陷**单测永远不会抓到**：那颗 runner 需要真引擎与真网络，而离屏语义树只证"动作挂没挂上"。它是包内那一趟的量出来的 —— 又一次印证"交付物里起窗"那一格不是仪式。

## 4. 交付包与包内六趟

包两版（同一批，P0 那次包是在缺陷修好**之前**打的，如实分两版）：

| 版 | 目录 | 读数 |
| --- | --- | --- |
| b96（修复前） | `_qa/desktop-exe-2026-10-12-b96/` | 216 MB / `classpath=100 missing=0 orphan_jars=0` / `desktop-cee45515…jar` 与现产物逐字节同一颗；b96d1 那一趟就是它跑的（量到 `页=0/0` 那一形） |
| **b96b（交付版）** | `_qa/desktop-exe-2026-10-12-b96b/` | 216 MB / `classpath=100 missing=0 orphan_jars=0` / 包内 `desktop-42ae4be97f12bbd0d91116beeb2b10.jar` **4,827,313 B** sha256 `8543f116…`、`engine-probe-ae823d3b…jar` 146,825 B sha `269a5321…` ⇒ `check_pkg_jar_sha.js` 现跑**两颗都对得上** |

量具 `_qa/b96_pkg_download.sh`（新写的一颗：轮询到 `D_下载 ` 为止，上限 420 s；**判式不只看 stdout，还去隔离数据目录里数 jpg 的颗数、总字节、零字节颗数，并把 `chapter.json` 原文打出来**）。数据目录 `_qa/b96_local`（`LOCALAPPDATA` 指过去，绝不碰真人那份），代理逐枚现量 7890/7897 ⇒ 命中 **7897**。六趟都是起跑前同名进程数=0、收尾后同名进程数=0，异常行=0。

| 趟 | 那一格 | 读数（包内 stdout，GBK→UTF-8 后逐字还原） |
| --- | --- | --- |
| b96d1 | **半途中断那一档**（b96 包，修复前） | `D_下载种子 号=1461316 话=1461318 落在=第 2 话 目录=15 话` → `D_下载 号=1461316 话=1 页=0/0 全=否 因=取页失败 页=63 共=89 因=stream was reset: CANCEL`；盘上 **63 颗** jpg、清单 `complete:false`、零字节 0 颗 ⇒ 抛而不静默、已写的页留盘都是真的，而**分母是假的**（§3.1 那颗缺陷就是这一帧抓出来的） |
| b96d2 | 续跑补缺（b96b 包） | `D_下载 号=1461316 话=1 页=26/89 全=是`；盘上 **89 颗**、总字节 **24,905,629**、零字节 0 颗、清单 `{"chapterId":"1461316","title":"第2話","order":1,"expectedPages":89,"complete":true}` ⇒ 上一趟缺的 26 页被这一趟补齐，且**没重下已有的 63 页**；分母这次念得出 89 |
| b96d3 | 已齐那一档重跑 | `D_下载 号=1461316 话=1 页=0/89 全=是`，盘上颗数与总字节**与 b96d2 完全相同** ⇒ 「一页都不重取」在交付物里量到了（屏上那一行同档念「这一话本机已有 89 页」） |
| b96d4 | 话号不在目录里 | `D_下载种子 号=1461316 话=999999999 目录=15 话 落在=话号不在目录里`，盘上字节不变 ⇒ 不打错的参数读成"下载坏了" |
| b96d5 | 参数不是两截 | `D_取证参数 --download=1461316 不是两截（要写成 <漫画号>/<话号>，例如 1461316/1461318；本轮不下载）`，没有任何 `D_下载` 行、盘上不变 |
| b96d6 | 种子点的不是开着那一本 | `D_下载种子 要=7777777 开着=1461316 落在=不是这一本`，盘上不变 |

**盘上那三层的形状**（与对岸逐字同形，这是本批最硬的一条实物证据）：

```
_qa/b96_local/venera/downloads/jm/1461316/0001_第2話/{0001.jpg … 0089.jpg, chapter.json}
```

⚠️ `0001_` 是 `order=1` 补零四位（对岸的 `ch.order` 0 基 ⇒ 第 2 话就是 `0001_`），而 `D_下载 … 话=1` 念的是同一枚 0 基 order、`落在=第 2 话` 念的是 1 基人话 —— 三种写法各有各的口径，读数与目录名逐字沿用对岸，只有种子那一行走人话。

## 5. 数字

`:desktop:test` **1287 判据 / 152 类 / 0 失败**（`suites=152 tests_sum=1287 testcase=1287 @Test=1287 files=159 failures=0`，`node _qa/three_way_counts.js` 现跑「三方对得上」；批 95 那一跑是 1263 / 150 ⇒ 本批 **+24 颗 / +2 类**：`DesktopComicChapterDownloadTest` 10 + `DesktopComicDownloadSeedTest` 7 + `DesktopComicDetailSemanticsTest` 11→14（+3）+ `DesktopStaleClaimsTest` 22→23（㉓）+ `DesktopReadmeStaleClaimsTest` 3→4 + `DesktopComicDetailWiringTest` 8→10（⑨⑩））。
`desktop/src/main` **113 颗 / 23,931 行**（批 95 是 112 / 23,498 ⇒ 新建一颗 `gallery/data/DesktopComicChapterDownload.kt`，接线动 `DesktopComicDetail.kt` / `DesktopComicPane.kt` / `DesktopGalleryHomeSections.kt` / `ForensicFlags.kt` / `VeneraDesktop.kt` / `desktop/build.gradle.kts`），`desktop/src/test` **159 颗 / 30,047 行**（新建两颗测试类）。
⚠️ 这两枚是**收口时二次现量**的读数：第一次量的 23,914 / 30,009 是 §3.1 那处修复**之前**的形状，差 17 行与 38 行正是那一笔添的。
`app/` 受保域：`git status --porcelain app/` 现数 **0 行**（对岸 `DownloadManager.kt` 只被读、正则从它的源码文本现取）。
台架 16 笔 + 包内六趟 + 交付包两份（§4）+ 新量具一颗（`_qa/b96_pkg_download.sh`）。

## 6. 欠账与下一步

- **`order` 会漂那一格继承自对岸**：目录名与读数用的都是"那一话在章表里的第几行"，源站中间插一话，同一话就落进**另一个目录**（`0003_x` → `0004_x`），"已下过"变"没下过"、盘上长出两个目录。对岸同一条限制（`DownloadManager.kt:156` 用的就是 `ch.order`），所以这不是桌面新增缺陷；但与批 94 那条「要不要给 `comic_history` 加话号列」是**同一颗题**（两端共用口径），要改就得两端一起拍板 —— 挂着等人点。
- **已下载的整话在任何一屏都数不着**：盘上有 `chapter.json` 与 89 张 jpg，而首页「下载」那一屏只数 `downloads/图库` 的散图（`listFiles` 不递归，所以漫画那三层混不进来 —— 这是**量过的**，不是推断）。对岸那一形是「本地离线书架」（`app/…/feature/LocalComicScreen.kt:83`，行走 `LocalComicManager`）。⇒ **下一件（批 97）的头一项**。
- 屏上那一行只有"这一趟"的状态（`download` 是 `remember(target.comicId)` 的一格）：**换一屏回来就没了**，而盘上的清单还在。要显示"这本已经下过哪几话"就得读清单 —— 与上一条同一颗件。
- 没有进度：~~整话那一趟最长量到 **85 s / 26 s**（b96d1 / b96d2），中途屏上只有「正在写本机…」而**没有 N/M 的推进**（`written` 只在收口时才更新）~~
  **⚠️ 2026-10-13 批 100 已接**：那颗 runner 现在每写一页回调一次（`desktopComicChapterProgressLine` 念 `页=i/expected`，起手那一格交的是**盘上已有**的数而不是 0），屏上那一行念「正在写本机… 12/89 页」。这一格的历史读数（85 s / 26 s）保留 —— 它量的是**当时**那一版；代价档"中"也保留，因为批 100 落的正是这一档。
- 没有取消、~~没有删除~~、没有系统通知（README 那一格点名的就是这三样 + 队列那一屏）。
  **⚠️ 2026-10-13 逐格改判**：取消 = **批 100 已接**（协作式，`--download-cancel-after=` 与屏上那枚「取消」扳同一颗闸）；删除 = **批 98 已接**（那一屏现量回核再动手，两步确认）；**系统通知桌面结构性没有**（对岸那一形绑 `NotificationChannel` 与 `POST_NOTIFICATIONS`，桌面不搬，见批 101 方案 §1 表里那两行「弃」）。
- 队列那一屏（~~对岸 `DownloadQueueScreen` 那一形~~）：桌面的下载今天是**屏内一次性动作**，没有任务表 ⇒ 与「数得着」是两件事，先做前者。
  **⚠️ 2026-10-13 自审改判**：`DownloadQueueScreen` 这颗名字**对岸根本没有**（现量：全仓只在这一行出现过；对岸的队列 UI 是 `feature/DownloadScreen.kt`，执行与任务表在 `download/DownloadManager.kt` 那 738 行里）—— 它是本仓那一族「凭空造一颗符号名」（详 `docs/HANDOFF-2026-10-04.md` 那一族，枚数本批没重新数）的又一次，而这次的载体是**引用对岸**，比引自家符号更危险（会被当成事实转述）。登记在批 101 方案文档 §1，那一行的真实锚点以它为准。


