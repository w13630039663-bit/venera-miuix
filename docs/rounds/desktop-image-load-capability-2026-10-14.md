# 批 109 · 源没声明 `onImageLoad` 时，桌面取图那一路要照对岸兜（方案，2026-10-14）

上一格是批 108（泵挂在表上等新的一笔，rounds `desktop-comic-queue-wake-2026-10-14.md`）。
本批做它 §7c 登记的那条：**包内那一趟顺手炸出来的取图缺陷**。

## 0. 缺陷那一形（读数 + 逐行现量，不是推测）

批 108 包内第二趟（`_qa/desktop-exe-2026-10-14-b108/`，goda 真号，隔离数据根）：

```
D_队列 笔=1 跑=1 …            ← 泵接走了（批 108 那一格成立）
D_下载进度 号=/manga/pandajunheleisajun-hiyaku 话=0 页=0/30
D_下载 … 全=否 因=取页失败 页=0 共=30
  因=onImageLoad 失败: (intermediate value).sources["goda"].comic.onImageLoad is not a function
D_队列 笔=1 跑=0 待=0 停=0 败=1 完=0 留档=无
```

现量的三方对照（2026-10-14 凌晨，命令可复核）：

| 事实 | 出处 |
|---|---|
| 随包源脚本 **34 份**，声明 `onImageLoad` 的 **18 份**，没声明的 **16 份** | `_qa/b109_probe_headers.js` 现数（goda / gelbooru / zaimanhua / wnacg / kavita / happy… 都在那 16 份里） |
| 对岸把这件事读成**能力位**而不是必需：`res.useOnImageLoad = !!(s.comic && s.comic.onImageLoad)` | `app/…/source/js/JsComicSource.kt:628`（那颗注释就在 `:626-627`：「comic.onImageLoad 时，loadEp 的每一项都是图片键」） |
| 对岸下载那一支缺声明时**把 item 原样当地址**：`if (chapterPages.useOnImageLoad && source is JsComicSource) {…} else { item }` | `app/…/download/DownloadManager.kt:417` 与 `:433` |
| 对岸阅读器那一支同形（缺省那支直接 `ComicPageSource.Network(url = u, …)`） | `app/…/reader/VeneraReaderScreen.kt:388` / `:404-405` |
| 协议里**没有**"缺 `onImageLoad` 就补一个默认实现"那一步 | 搜过 `checkPropTypeList` / `ComicSourceSchema` / `validate()` / `notImplemented`，`venera-init.js` 里 `onImageLoad` 只在 `:1236` 一句注释出现 ⇒ 兜法住在**调用点**，不在装载期 |
| 桌面**活的那一处是裸调** | `engine-probe/…/EngineSession.kt:272`（`pageImage`）写死 `ComicSource.sources['$key'].comic.onImageLoad(…)`；同文件 `:166-183` 的 `imageBytes` 也是同一裸调，但它全仓零调用点（见 §2）⇒ 本批撤掉而不是加守卫 |
| 桌面**同一颗文件里已经有对岸同款守卫**（缩略图那一颗） | `EngineSession.kt:198` `if (!s.comic || !s.comic.onThumbnailLoad) return { __none: true };` ⇒ 正解的形状本仓已经在用，不是新造 |
| goda 的 `loadEp` 交回 `{ images }`，每一项是 `this.imageUrl + i["url"]` 的**完整地址**，且**不带 headers** | `app/src/main/assets/sources/goda.js:500-507` |

后果不止下载：**阅读那一屏与预览大图也走 `pageImage`**（`DesktopComicPageFetcher.kt:116`），
所以那 16 族源在桌面上今天整话读不了。jm 那一族有 `onImageLoad`，所以前几批的真跑没撞上这一格。

## 1. 定档：把"要不要过 `onImageLoad`"读成能力位，缺就走原地址

JS 那一段换成 `coverBytes` 已经在用的那一形（**同一颗守卫，不加第三种说法**）：

```js
var s = ComicSource.sources['$key'];
if (!s.comic || !s.comic.onImageLoad) return { __none: true };
return await s.comic.onImageLoad(<key>, <comic>, <ep>, undefined);
```

Kotlin 那一段把"信封 → 要打的地址/请求头/切块数"这一跳**抽成一颗纯件**（这是本批唯一的新结构，理由见下）：

```kotlin
data class DesktopImageRequest(val url: String, val headers: Map<String, String>, val scrambleBlocks: Int)

fun desktopImageRequestOf(imageKey: String, env: Map<String, Any?>): DesktopImageRequest
```

- `env["__none"] == true` ⇒ `(imageKey, emptyMap(), 0)` —— 原样那一支，与对岸 `else { item }` 逐字同语义；
  `scrambleBlocks = 0` 是**结论而不是兜底**：去混淆那把 `const num` 只可能来自 `onImageLoad` 回的 `modifyImage`，
  没走过 `onImageLoad` 就没有它。
- `env["success"] == false` ⇒ **抛**（不许静默退回 item —— 声明了却调用失败是另一件事，那一条要响）；
- 有 `data` ⇒ 与今天同一套取 `url`/`headers`/`const num`。

**为什么这颗纯件必须是 public 且在 `:engine-probe`**：`internal` 只在**本模块**可见，
桌面测试够不着 engine-probe 的 internal；而这一跳是跨模块的唯一"能脱离 JS 真跑"的判断面。
抬成 public 的代价是它进了 `:engine-probe` 的门面 —— 换来的是**能写真判据**（本仓口径：判据要红在自己家才有信息量）。

## 2. 接缝现量（2026-10-14 复量，`grep -rn imageBytes` 可复核）

| 要动的地方 | 现量 |
|---|---|
| 裸调**活的那一处** | `EngineSession.kt:272`（`pageImage`） |
| `imageBytes` 是死件 | `EngineSession.kt:166-183` 全仓**零调用点**（只有它自己那行声明；`.worktrees/*` 与 `build/pixez` 那几处命中分别是别的分支副本与同名局部函数）⇒ **本批整颗撤掉**，这正是手册里那条「实现了而零调用点」的正向处置；判式 ⑦ 反向钉着它不许回来 |
| 守卫那形已有的先例 | `EngineSession.kt:198`（`coverBytes`，本批不动它，而 ⑥ 钉着它不许被顺手改掉） |
| 消费点 | `DesktopComicQueueRunner.kt:106`、`DesktopComicPageFetcher.kt:116`（阅读器与预览共用这一颗） |
| `pages()` 的返回 | `EngineSession.kt:241` `List<String>` —— **本批不改它的形状**，理由见 §4 |

## 3. 判式（新文件 `desktop/src/test/…/DesktopImageLoadCapabilityTest.kt`）

1. **行为**：`__none` 那一档 ⇒ 地址是 `imageKey` 原值、头为空、切块数 0（喂两种 `imageKey`，含带 `#crop` 后缀那一形）。
2. **行为**：声明了且回 `data` ⇒ 地址取 `data.url`、头取 `data.headers`、`modifyImage` 里 `const num = 8` 抽出 8（这一档防"把守卫写成恒 `__none`"）。
3. **行为**：声明了而 `success == false` ⇒ 抛，且**不许**退回 item（降级路径要响，那条红线）。
4. **行为**：没有 `data` ⇒ 抛（今天那句 `error("onImageLoad 没有 data")` 的语义不许漂）。
5. **形状**：`EngineSession.kt` 里那枚守卫串 `if (!s.comic || !s.comic.onImageLoad) return { __none: true };` 恰 **1 处**（只有活的那一处 `pageImage`）。
6. **形状**：裸调那一形 `return await ComicSource.sources['$key'].comic.onImageLoad(` 之前必有守卫 —— 数法：守卫串恰 1 处 且 `comic.onImageLoad(` 恰 2 处（守卫里判式一处 + 守卫后真调一处），二者相加就是那一份 JS 片段的全部；`coverBytes` 那枚 `onThumbnailLoad` 守卫仍恰 1 处（不许被顺手改宽）。
7. **接线与死件**：`pageImage` 改成吃那颗纯件（纯件名在 `EngineSession.kt` 里恰 **1** 处调用）；`fun imageBytes(` 在 `EngineSession.kt` 里**零处** —— 它全仓零调用点，本批整颗撤掉，这一枚反向钉住它不许回来。

## 4. 那一半格：**量完了，按本条自己的判据撤下来**（原登记为批 109b）

对岸下载那一支把 `chapterPages.headers` 当作请求头底座，缺 `onImageLoad` 时**也带它**；
桌面 `pages()` 今天把 `loadEp` 交回的 `headers` 那一格**整个丢掉**。
本批不动它，而当时留的条件是「先逐族量那 16 份脚本的 `loadEp` 返回里有没有 `headers`，有则补底座，无则把这条撤下来不占位」。

现量（`node _qa/b109b_probe_loadep_headers.js` → `_qa/b109b_probe.log`，2026-10-14）：
花括号配对取整颗 `loadEp` 函数体，只数**作键**出现的 `headers:`（第一版 `_qa/b109_probe_headers.js` 数的是"提到几次"，
会把请求选项读成答复字段 ⇒ 换掉）。

| 读数 | 值 |
|---|---|
| 随包源脚本 | **34 份** |
| 声明 `onImageLoad` | **18 份**（jm 系在里面） |
| 没声明 | **16 份**（goda / gelbooru / zaimanhua / wnacg / kavita / happy / hot_manga / mh18 / mxs / manwaba / mh1234 / hcomic / ykmh / copy_manga / copy_manga_multi_account / baozi） |
| 那 16 份里 `loadEp` **答复**带 `headers:` 键的 | **0 份** —— 唯一一处字面命中在 `ykmh.js`，逐行看是 `Network.get(chapterUrl, { headers: {…} })` 的**请求选项**（抓章节 HTML 用），它的答复是 `return { images: … }` |
| 同族另两颗裸调需不需要守卫 | 不需要：`loadEp` 与 `loadInfo` 在 34 份里 **34/34 全声明**（`grep -Li` 两份名单皆空） |

⇒ 补底座换到的是 **0 族**，而代价是改 `pages()` 的返回形状（`List<String>` → 带 headers 的一件），
牵 `DesktopComicPage` 那颗 Coil 键件与两把既有尺子（`DesktopComicReaderWiringTest` 点名叫过 `engine.pages(`）。
按本条自己那条"无则撤下来不占位"的判据，**批 109b 撤销**，不再排在排期表上。
如果以后引入新源（或某族脚本改版）开始回 headers，这一格要从"撤下来"变回"补底座" —— 到那时题面就是这份 §4，不必重新量。

## 5. 台架（预算 9 笔，逐笔要求红）

- P1 恒走 `__none`（守卫写反成 `if (s.comic.onImageLoad) return { __none: true }`）⇒ 咬 ⑤+⑥。
- P2 恒不走 `__none`（把守卫整行摘掉）⇒ 咬 ⑤+⑥。
  ⚠️ 这两笔**只**红在形状尺子上，是一格结构性盲区：①~④ 打的是抽出来的纯件，守卫住在那段 JS 字符串里，
  行为判式一口也咬不到它。与批 108 §注 那条「行为判式的盲区」同族；真跑那一头只有包内复验在管
  （同一枚 goda 号 败=1 → 完=1），别把它写成"行为判式覆盖了守卫"。
- P3 `success == false` 那一支改成退回 item（静默降级）⇒ 咬 ③。
- P4 `data` 缺失时给一张假信封（`?: mapOf("url" to imageKey)`）而不是抛 ⇒ 咬 ④。
- P5 切块数从错的那一栏抽（`data["modifyImage"]` 写成 `data["url"]`）⇒ 咬 ②。
  第一版笔是「`const num` 写成 `num`」，跑前自审发现它**不会红**：判式 ② 喂的那串 `modifyImage` 里
  `num = 8` 本来就在，粗化的正则照样抽得出 8 ⇒ 换成一栏之差那一形（同一格错误、尺子咬得住）。
- P6 `pageImage` 不走纯件、自己就地解析信封 ⇒ 咬 ⑦ 前半（纯件那一跳恰一处）。
- P7 把那颗零调用点的死件补回来（`fun imageBytes(` 随手挂一行转发）⇒ 咬 ⑦ 后半（它零处）。
- P8 顺手把邻座 `coverBytes` 那枚缩略图守卫改宽 ⇒ 咬 ⑥ 的第三枚。
- P9 缺声明那一档谎报切块数（`__none` 也回 8）⇒ 咬 ①。

## 6. 取证种子

不加新的：`--comic=` + `--download=` 那一族已经把这条路铺好了（批 107/108 两趟都在用），
包内复验的判据是**同一枚号**从 `败=1` 变成 `完=1` 且盘上真的长出页 —— 这一格是图证 + 读数 + 盘上快照三条。

## 7. 代价档

**中小**：一颗纯件（约 15 行，抬 public）+ 一处 JS 片段加一行守卫 + `pageImage` 一处调用改写 + 那颗死件整颗撤掉（约 −18 行）；
7 枚判式 + 9 笔台架 + 包内一趟复验。不动 `pages()` 形状、不动 Coil 键件、不动去混淆那一路的算式。

## 8. 落地账（2026-10-14 现跑）

### 8a. RED 那一格是**两遍**的，不是一遍编译失败

| 遍 | 状态 | 读数 |
|---|---|---|
| 第一遍 | 只有判式、没有实现 | `compileTestKotlin` 红：`Unresolved reference 'desktopImageRequestOf'` ×5 + `Cannot infer type parameter 'R'` ×2 ⇒ **新符号的 RED 只能到编译层**，本仓不拿它当行为证据 |
| 第二遍 | 只补纯件、**不补 JS 守卫也不接线** | `7 tests completed, 3 failed` —— 红的正是 ⑤（守卫 `expected:<1> but was:<0>`）、⑥（裸调 `expected:<1> but was:<2>`：今天 `imageBytes` 那一处还在）、⑦（纯件那一跳 `expected:<1> but was:<0>`）⇒ **三枚形状判式各自红在该红的原因上**，而 ①~④ 绿（纯件写得对） |
| 第三遍 | 守卫 + 接线 + 撤死件 | 新那颗 `BUILD SUCCESSFUL`；全量 `:desktop:test` + `:engine-probe:test` **exit=0** |

⇒ 第二遍那一跑就是 §5 里 P2/P6 两笔的"提前自证"：它证明这三枚尺子钉的是**这一批要立的那件事**，不是同义反复。

### 8b. 台架 9 笔逐笔终账（`_qa/b109_perturb.js` + `sh _qa/perturb_driver.sh b109 9`；生成器 `_qa/b109_ledger.js`）

**9 笔 = 9 笔咬住 + 0 笔不咬 + 0 笔 BUILD_FAILED**，逐笔都已还原（`_qa/b109_perturb_all.log` 现数 `已还原=9`、marker 归 0）。

| 笔 | 咬住哪枚判式 | 那句红话（截断） |
|---|---|---|
| P1 守卫写反 | ⑤ + ⑥ | `取图那一路的缺声明守卫恰一处 … expected:<1> but was:<0>` + `守卫必须存在` |
| P2 守卫整行摘掉 | ⑤ + ⑥ | 同 P1 两句 |
| P3 `success=false` 静默退回 | ③ | `success=false 必须抛，不许退回 item` |
| P4 缺 `data` 给假信封 | ④ | `没有 data 必须抛` |
| P5 切块数从 `url` 那一栏抽 | ② | `切块数只认 const num = N 那一形 expected:<8> but was:<0>` |
| P6 不走纯件、就地解析 | ⑦ 前半 | `pageImage 里对纯件的那一跳恰一处 expected:<1> but was:<0>` |
| P7 死件补回来 | ⑦ 后半 | `imageBytes 全仓零调用点，撤掉后不许回来 expected:<0> but was:<1>` |
| P8 邻座缩略图守卫改宽 | ⑥ 第三枚 | `coverBytes 那枚缩略图守卫仍恰一处 expected:<1> but was:<0>` |
| P9 `__none` 谎报切块数 | ① | `原样那一档不去混淆 expected:<0> but was:<8>` |

⇒ **七枚判式各自被至少一笔咬住**（① ←P9、② ←P5、③ ←P3、④ ←P4、⑤ ←P1/P2、⑥ ←P1/P2/P8、⑦ ←P6/P7），没有一枚是"只为绿而写"。

### 8c. 两处本批自己的改判（先记下来，免得下一轮当成事实引用）

1. **§5 的 P5 换过笔形**：原设计「`const num` 写成 `num`」跑前自审发现**恒不红** —— 判式 ② 喂的那串
   `modifyImage` 里本来就含 `num = 8`，粗化的正则照样抽得出 8 ⇒ 那是"笔太弱"而不是"尺子不咬"。
   换成「从错的那一栏抽」（`modifyImage` → `url`）后 ② 当场红 `expected:<8> but was:<0>`。
   这正是手册里那条「扰动跑前要自审笔能不能到达判式」的第六次样本。
2. **§5 的 P1/P2 归账从"咬行为判式 ①②"改成"只咬形状尺子 ⑤⑥"**：行为判式打的是抽出来的纯件，
   那段守卫住在 JS 字符串里 —— 纯件写得再对，调用点不守卫也一片绿。这是**结构性盲区**而不是本批疏漏，
   与批 108 §注那条同族；能管住它的只有 §6 那趟包内复验（同一枚 goda 号 败=1 → 完=1）。

## 9. 包内那一趟（§6 那张判据的对证，2026-10-14 现跑）

交付包 `_qa/desktop-exe-2026-10-14-b109/`（`:desktop:createDistributable --offline --rerun-tasks` = 11 actionable tasks: 11 executed，
`check_pkg_jar_sha.js` 念「两颗都对得上」，`check_cfg.js` 念 `files_in_dir=100 orphan_jars=0`）。
起窗走 `_qa/b109_pkg_run.sh`（等价于双击包内那枚 `try-goda-page-image.bat`），数据根 = 包内 `trial-data`，
**起跑前同名进程数现量 0**，跑完按镜像名收窗（关掉 PID 500 与 23440 两颗）⇒ **残留现量 0**。

### 9a. 同一枚号在两批之间的差（这就是本批要的那一格）

| 读数 | 批 108 那一趟（改之前） | 本批这一趟（改之后） |
|---|---|---|
| `D_队列` 终局 | `笔=1 跑=0 待=0 停=0 `**`败=1 完=0`** | `笔=1 跑=0 待=0 停=0 `**`败=0 完=1`** |
| 取页那一跳 | `因=onImageLoad 失败: (intermediate value).sources["goda"].comic.onImageLoad is not a function` | **日志里 `onImageLoad` / `取页失败` / `取图失败` 三类行数现量 = 0** |
| 逐页进度 | 停在 `页=0/30` | `页=25/30 → 30/30`，收尾一句 `D_下载 … 页=30/30 全=是` |
| 盘上（**进程停之后**才数） | 0 页 0 清单 | **30 颗页 / 1 个话目录** `goda/_manga_pandajunheleisajun-hiyaku/0000_第01话`，合计 **2,795,712 B** |
| 清单 | 无 | `chapter.json` `complete=true`、`chapterId=19777@1253812`（批 97 那格改判在这里第二次得到对证：递进去的是**话号**不是本子号） |
| 字节真伪 | —— | `_qa/b109_pkg_magic.js` 现量 **30/30 全是 WEBP 头**（`52 49 46 46 … 57 45 42 50`）；页名恒 `.jpg` 而内容是 WEBP —— 正是批 102 那句「站方常回别的」那一形，魔数闸认它 |

⚠️ 读数里 `comicId=undefined` 不是缺陷：桌面那份清单的字段表是 `chapterId / title / order / expectedPages / complete`，
**本来就没有** `comicId` 那一栏 —— 是快照脚本多问了一句。这条写下来是为了防下一轮把它读成"清单少了字段"。

### 9b. 这一趟踩到的两条量具坑（已在脚本里改判并留注释）

1. **包内 stdout 是 GBK，而判式串在脚本里是 UTF-8** ⇒ `grep -q "D_队列…"` **恒不命中**，轮询会白等到 420 秒超时，
   看上去像"应用没跑完"而其实那一行早就念过了。正解是先过 `iconv -f gbk -t utf-8 -c` 再 grep。
   （同一族第一次是批 90 那条"包内中文 stdout 会花"，第二次是"先看 stdout 的 `D_` 行再看像素"——这一次是**判式吃不下自己的读数**。）
2. **`$!` 不是 Windows 的 PID**（MSYS 编号）而那颗 exe 是**两颗进程** —— 批 91 记过，这次照它的正解把两道都上：
   先试 `taskkill //PID`，再按镜像名整批关，安全性由"起跑前同名进程数 = 0"兜。
   本趟现量：按名关掉 PID 500 / 23440 之后残留 **0**。
   ⚠️ 后面那趟取帧的收窗读数不一样：`taskkill //IM` 念的是「没有找到进程」而残留 = 0 ⇒ 那一扇窗在**取帧之后、我下手之前**就随起它的那颗短命 shell 一起被回收了。教训：取帧的起窗要用后台任务起，别用 `&` 挂在一次性的 shell 上（否则「窗自己没了」会被误读成「应用崩了」—— 这一趟是靠 `D_异常` 行数 0 才排除掉那种读法的）。

### 9c. 图证（阅读器那一屏真出图）

读数与盘上快照都有了，还缺**屏上真出图**这一条 ⇒ 补一帧：`--read=%2Fmanga%2Fpandajunheleisajun-hiyaku/19777@1253812` 起窗（同一颗交付包、同一份隔离数据根），窗口标题 `Venera Desktop R1-F 最小闭环 #29436`，`get_window_state` 走 windows-graphics-capture、`occlusionSafe=true`、`relatedTransientWindows=[]`（没有 Error 框）。帧里念到的是：顶上一行 `/manga/pandajunheleisajun-hiyaku 话 19777@1253812`、`共 30 页 第 1 / 30 页`、三枚档位把手与「上一页 / 下一页」，而**内容区是那一话第一页的真图**（熊猫与兔子那一格，台词「晚安—!」清晰可读，两栏连续流排到第 6 格）—— 改之前这一族在桌面上是**一页都出不来**的。配套读数：`D_阅读 源=goda … 态=上屏 页=30`、`D_阅读 起点 请求=0 量到=0 页表=30 试=0`、`D_历史 写 … 页=0/30`，整趟 `D_异常 / Exception / Error` 行数现量 **0**。
