# 批 93 · 分块那一层「要不要换现成品」：四问量完，答案是量掉这一行（2026-10-12）

排期那一行（`docs/rounds/remaining-plan-and-reuse-2026-10-10.md` §4 的 批 93）写着"先量后接，四条"。
这一批把四条全部量完，**结论是不接现成品**，而落下的只有一格读数（`字节=`）——
那一句"我们省的是解码与内存、带宽仍是整张"当时只是我从代码读出来的推断，落成读数才算量过。

## §0 改动表

| 层 | 文件 | 改了什么 |
| --- | --- | --- |
| 读数算式 | `desktop/src/main/kotlin/com/venera/desktop/gallery/data/DesktopRegionDecode.kt` | 新增一颗 `internal fun desktopRegionBytesField(bytes: ByteArray): String`（只有"念这一格"这一件事，不碰解码） |
| 读数落点 | `desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopRegionStage.kt` | `D_分块 生效` 与 `D_分块 不换轨` 那两行各挂一处 `字节=`；四条 `D_分块 退化` 一条都不挂。另把 `不换轨` 那一行补上 `档=$tier`（它从前不自报档位，见 §1 ③） |
| 判据 | `desktop/src/test/kotlin/com/venera/desktop/gallery/data/DesktopRegionDecodeTest.kt` | 31 → **33** 条（真跑那颗件 + 接线计数与负向 + 六行各带一枚 `档=`） |
| 量具产物 | `_qa/b93_jar_measure.txt`、`_qa/b93_probe/` | 六颗 desktop jar + android aar + sources/javadoc jar 的现量清单（本文 §1 的每个数都能从那颗文件重跑出来） |
| 交付包 | `_qa/desktop-exe-2026-10-12-b93/` | 216 MB；`check_cfg.js` 现跑 `classpath=100 missing=0 orphan_jars=0`（包内那颗叫 `desktop-eacbc479cb6458a191e3913eeb8708f.jar`）；`check_pkg_jar_sha.js` 现跑：**两颗都对得上** —— `desktop/build/libs/desktop.jar` 4,786,699 B（sha256 `2391bbe5…`）与 `engine-probe` 146,825 B（sha `269a5321…`）逐字节同一颗 ⇒ 交付物与当前源码同步 |

`app/` 一字未动（受保域，现量 0 行改动），共享面 86 颗 / 11,378 行未动。

## §1 四问的量法与答案（每个数都带判式）

### ① 那颗 desktop jar 到底发了什么 —— 六个版本恒为「13 颗 class、零公开面」

现下 Maven Central 上 `me.saket.telephoto:sub-sampling-image-desktop` **全部六个已发布版本**逐颗数过
（`_qa/b93_jar_measure.txt`，判式：`unzip -l <jar> | awk '{print $4}' | grep -c '\.class$'`；
非公开面 = 同一份名单里 `grep -v '/internal/'` 后再数）：

| 版本 | 字节 | class 枚数 | 不在 `internal/` 下的 class |
| --- | --- | --- | --- |
| 0.15.0 | 26,301 | 13 | **0** |
| 0.15.1 | 26,301 | 13 | **0** |
| 0.16.0 | 29,216 | 13 | **0** |
| 0.17.0 | 29,887 | 13 | **0** |
| 0.18.0 | 29,887 | 13 | **0** |
| 0.19.0（latest/release） | 29,887 | 13 | **0** |

`-sources.jar`（7,455 B）里只有 **5 颗 `.kt`**，全在 `commonMain/me/saket/telephoto/subsamplingimage/internal/`：
`dimens.kt`、`imageFormats.kt`、`semantics.kt`、`tileGridGenerator.kt`、`tiles.kt`。
⇒ 桌面那颗 artifact 发的是 **commonMain 那半（排块网格、采样档、语义标签、魔数嗅探）**，
`SubSamplingImage` 那颗 composable、`SubSamplingImageState`、`SubSamplingImageSource`、`ImageRegionDecoder`
**一颗都不在里面**。

对岸 `sub-sampling-image-android-0.19.0.aar` 的 `classes.jar` 是另一回事：**139 颗 class**，
`s.../subsamplingimage/` 下非 internal 的有 78 颗（直接一层的公开类名 17 颗），
其中 `SubSamplingImageKt`、`RealSubSamplingImageState`、`FileImageSource`、`RawImageSource`、`ResourceImageSource` 全在 android 变体里。

**所以排期里那句"为什么公开那一半没随 desktop 发出来"不再是未定论**：
它不是"某一版被砍了"（六版逐颗数过，形状一致），而是**上游只把 public 面发在 android 变体**，
desktop 目标发的是 commonMain 那份内部算式。上游自己的追踪 issue **#9「Compose Multiplatform support」至今 open**
（现量：GitHub search API，`repo:saket/telephoto subsampling` 共 8 条，#9 = open；`desktop in:title` 唯一那条 #125 也 open，谈的是 zoom 手势而不是 subsampling）。
⇒ "等上游发 desktop"不是一个可排的方案。

⚠️ **一条量具坑（差点给出假数）**：`unzip -Z1 <jar> '*.class'` 在这台机器的 unzip 上恒报
`caution: filename not matched`，我第一遍据此数出 **class=0** —— 与"13 颗全在 internal"那条真读数差了整整一颗 jar。
判式换了通道（`unzip -l | awk '{print $4}'`）才对上。**"某东西不在里面"的结论要换一把通道再数一次**
（同族：裸 `grep -r` 的假空结果）。

### ② 它与现有那枚 `zoomable` 怎么叠 —— 叠不了，而且"只借解码层"没有对象

android 变体的 class 常量池里逐串数过（判式：`grep -rlao 'me/saket/telephoto/zoomable' … --include='*.class'`）：
**7 颗 class 引用、串共出现 37 次**，符号是这三颗：

- `me/saket/telephoto/zoomable/ZoomableState`
- `me/saket/telephoto/zoomable/ZoomableContentTransformation`
- `me/saket/telephoto/zoomable/ZoomableContentLocation`（+ `$Companion`）

引用者点名在 `RealSubSamplingImageState`（含 `viewportTiles$2` 那几颗内部类）与 `SubSamplingImageStateKt`
（`rememberSubSamplingImageState`）。⇒ **它的"该解哪一块"这件事是直接吃 Telephoto 自家 `ZoomState` 的变换算式的**，
不是一个可以喂任意缩放源的接口。

我们那一屏吃的是 `net.engawapg.lib:zoomable:2.16.0` 的 `ZoomState`（像素偏移 `offsetX/offsetY`，
符号约定由批 89 的 `DesktopZoomableOffsetTest` 进程内量定）。两套 `ZoomState` 是**两个不同类型**，
⇒ 排期那一行给的两条路里：
- **"只取它的解码层"**：没有对象 —— 那颗解码层（`ImageRegionDecoder` 与 `AndroidImageRegionDecoder`、
  `PooledAndroidImageRegionDecoder`、`ExifAwareBitmapPainter`、`ImageCache`、`FindActivityKt`）全住在 android 变体，
  而且 `internal` 包下的东西在 Kotlin 侧外部不可见；
- **"整屏换掉现那枚"**：要连手势层一起换（它自带 pan/zoom），而批 50 那一轮已经比过三支库并**选了 engawapg**
  （唯一把滚轮当一等公民的一支，`docs/rounds/desktop-image-viewer-design-2026-10-08.md` §2.1）。
  Telephoto 的 desktop 手势今天还有 #125 那条 open 的错报。

同一格还有一条**批 50 就量过**的旧读数可以直接用（`docs/rounds/desktop-image-viewer-design-2026-10-08.md` §2.1 那张表）：`zoomable-desktop-0.19.0-sources.jar`（59,656 B）里 **`Wheel` 关键字零命中** ⇒ 它的手势集不含鼠标滚轮，而桌面主要输入就是滚轮；`net.engawapg.lib:zoomable` 那一支把滚轮当一等公民（`MouseWheelZoom` 六档枚举）。当年那句「telephoto 那条线留到真要把两端查看器合成一件时再议」（同文档 §2.1 末尾），**本批就是那一格的答复：不合**。

⇒ 两条都不成立 ⇒ **这一行不接现成品**，继续走我们自持那三层（`DesktopVisibleRegion` / `DesktopRegionGain` / `DesktopRegionDecoder`）。

### ③ 三档地址里网络档与本机档各走不走得通分块 —— 本机档今天不存在，那一问没有对象

- `DesktopGalleryDetailPolicy.kt:81-85`：三档 = `wallUrl(post, PREVIEW)` / `wallUrl(post, LARGE)` / `post.fileUrl`，
  **三档全是站方 http(s) 地址**（"本机档"在桌面今天只有 `downloads/图库` 那条**存出去**的路，批 85，
  查看器不吃它）。
- `DesktopGalleryViewer.kt:91` 把这同一颗件的产物交给覆盖层（`:105-110` `DesktopRegionOverlay(url = regionUrl, …)`），
  `DesktopRegionStage.kt:82` 一律走 `desktopCoilBytes(context, url)`。
  ⇒ **三档走的是同一条取数轨**，"哪几档走得通分块"不由地址形状决定，而由那一枚增益门槛决定。
  本批把历次真日志按档重数了一遍（判式：`grep -h -aoE "D_分块 (生效|退化) 档=<那一档>" _qa/*.log`，
  去重后取不同 `增益=` 值）：

  | 档 | `生效` 真读数条数 | 出现过的增益 | `退化` |
  | --- | --- | --- | --- |
  | `ORIGINAL` | 2 | 5.22（批 92 包内）、6.53 | 0 |
  | `SAMPLE` | **7** | 1.11、1.96 | 0 |
  | `PREVIEW` | 0 | ——（它落在 `不换轨` 那一档） | 0 |

  ⚠️ **这一格是本文写错之后现查回来的**：初稿写的是"SAMPLE 档没有单独复跑过"，
  而 `_qa/b89_frameB/C/D.log` 里就有 7 条 `D_分块 生效 档=SAMPLE` —— 那句"没量过"是我把"批 92 那三趟没种它"
  写宽成了"今天没有读数"。**"没量过"这类负结论要按判式现数一遍再写**（同族：裸 `grep -r` 的假空结果）。

  另量到一处口径缺口：**`不换轨` 那一行从前不带 `档=` 字段**（现量三条都是从 `增益=` 直接跳到 `块=`），
  所以"预览档不换轨"这件事在 stdout 里是靠"我这一趟种了哪一档"反推的，而不是那一行自己说的
  ⇒ 本批顺带把 `档=` 补进那一行（与另外五行同一套写法）。

### ④ 判据能不能证"放大时不是一次解全图" —— 已经能证，不需要新立

这一问的答案在批 89 就落地了，本批只是回代码逐条点名（`DesktopRegionDecodeTest`）：
`jpeg按区域与采样档真裁一块而不是整图`、`png那一支也一样真裁`、
`只读头部拿尺寸而一个像素都不解`、`裁出来的那一块位置对得上而不是尺寸对而裁错地方`、
`任何一支reader要么按算式交回要么抛` —— 前两条钉的是**交回尺寸等于算式而不是整图**，
正是 `setSourceRegion` 那枚"建议性"参数最会骗人的那一格（reader 可以忽略它、交回整张、一个字不报错）。
⇒ 那一条不是假开关，**而且它已经有 33 条尺子在 `:desktop:test` 里真跑**。

## §2 落下的那一格读数：`字节=`

四问里唯一还没量过的**代价**在取数那一层，而它是这一批定档的依据，所以必须落成读数而不是留在注释里：

- `DesktopCoilBytes.kt:44-49` 的口径是**读整份编码文件**（`diskCache.openSnapshot(url)` → `Files.readAllBytes`），
  冷缓存时先 `loader.execute(...)` 走一趟网络（同一颗单例，批 47 钉过不许另起轨）。
  ⇒ 我们省的是**解码像素与内存**，**带宽仍是整张**。
  我们的三档都是网络地址（§1 ③），所以"整张下来多少字节"只有 `字节=` 念出来才可核 ——
  这句话在写本文之前只是我从代码读出来的推断。
- 新格挂在两行：`D_分块 生效 … 字节=1568026 块=0,0,744x972 档=1 全图=1944x2430 …` 与
  `D_分块 不换轨 档=PREVIEW 增益=1.00 字节=3949 块=…`（§4 那表是这两行的实测整行）。
  **四条 `退化` 一条都不挂**：`因=缓存没它` 那一档手上没有字节，念一个数就是假读数（读数必须从状态现取）。
- 顺带补的那枚 `档=` 让 `不换轨` 第一次自报档位：从前那句"预览档不换轨"的证据是
  "我这趟种的是 `--detail-tier=PREVIEW`"（把请求当结果），现在那一行自己说了。

## §3 台架（`_qa/b93_perturb.js`，7 笔；台账 `_qa/b93_ledgerB.log`）

起手 `node _qa/b93_perturb.js check` = **7 笔各命中一处，可跑**。每一笔只改一处、改完立刻逐字还原；
驱动在每一笔之后跑一次全量 `:desktop:test` 并按身份戳报告。

| 笔 | 改坏的那一格 | 实际红的是哪条 | 结果 |
| --- | --- | --- | --- |
| P1 | 生效那一行不念 `字节=`（换成写死的字符串） | ② 的计数那条（2 处 → 1 处） | 咬住 |
| P2 | `不换轨` 那一行退回旧形状 | ② 的计数那条（2 处 → 1 处） | 咬住 |
| P3 | 那一格挪进 `退化 因=读不动尺寸` 那一条 | ② 的计数那条（2 处 → 3 处，负向那半同时被踩） | 咬住 |
| P4 | 那颗件把数写死成 `字节=1024` | ① 空手那一格：`expected:<字节=[0]> but was:<字节=[1024]>` | 咬住 |
| P5 | 那一格改成 `%,d` 的写法 | ① 小的一档：`expected:<字节=1[]024> but was:<字节=1[,]024>` | 咬住 |
| P6 | `不换轨` 那一行撤掉 `档=$tier` | ② 补的那条：`expected:<6> but was:<5>` | 咬住 |
| P7 | **等价改写**（实参写成命名形式 `bytes = bytes`）—— 要求不红 | —— 确实一笔没红（正确输出） | ✅ |

收尾"还原后的全量"：`EXIT=0`、`suites=148 tests_sum=1244 testcase=1244 @Test=1244 files=155 failures=0`。

- **两遍台架的账**：第一遍台账 `_qa/b93_ledger.log`，6 笔（那时还没有"报档位"那枚锚点），
  5 笔要求红全部咬住、等价改写那笔确实不红，还原后全量 `EXIT=0`；
  第二遍是加完 `档=` 之后的 7 笔（上表）。**新立的那枚锚点自己配了 P6**，不是"顺带被别的笔覆盖"。
- ⚠️ **笔号与笔名的错位（本批自己的量具账）**：`_qa/b93_perturb.js` 里 P6（撤 `档=`）被插在 P7（等价改写）**之前**
  而当时仍叫它 P7 ⇒ 驱动打的是数组序号而不是名字，第一版日志里就有 `P6 apply` 配着"P7 …"的名字那两行。
  读数按名字读；已把两处名字改成与次序一致，并在脚本头部写了这条规矩（**数组次序就是笔号**）。
- ⚠️ ② 那条断言的消息里带整份文件正文（`+ stage`），所以红单很长而信息在尾部 —— 台架的 `report` 只截 150 字符，
  下一批若要读全消息，去读那颗 `TEST-*.xml` 的 `<failure>` 而不是日志行。


## §4 包内三档对比（`_qa/b93_pkg_tier.sh` 三趟，包 `_qa/desktop-exe-2026-10-12-b93/`）

| 档 | 那一趟念的整行 | 下载字节 | 请求的那一块 | 全图 | 解出占全图 |
| --- | --- | --- | --- | --- | --- |
| `ORIGINAL` | `D_分块 生效 档=ORIGINAL 增益=2.54 字节=1568026 块=0,0,744x972 档=1 全图=1944x2430 视口=766x821 偏移=574.5,615.75 倍=2.5 内容=138,0,1640x2053` | 1,568,026 B（1.50 MiB） | 744×972 | 1944×2430 | **15.31%** |
| `SAMPLE` | `D_分块 生效 档=SAMPLE 增益=1.96 字节=295923 块=0,0,600x401 档=1 全图=1500x1125 视口=766x821 偏移=574.5,615.75 倍=2.5 内容=0,309,1915x1435` | 295,923 B（0.28 MiB） | 600×401 | 1500×1125 | **14.26%** |
| `PREVIEW` | `D_分块 不换轨 档=PREVIEW 增益=1.00 字节=3949 块=0,0,38x72 档=1 全图=109x180` | 3,949 B | （**没解** —— 增益 ≤ 1 在解码之前就返回，`块=` 只是算出来的那一块） | 109×180 | —— |

- **这一表就是本批那一格的落点**：三行都自报档位（`不换轨` 那一行今天第一次带 `档=`），
  而 `字节=` 与 `块=` 并排 ⇒ "整张 1.5 MB 下来了而只解了 15.31% 的像素"这一句从今天起是**读数**不是推断。
- **一次免费的回归证据**：`SAMPLE` 那一趟与批 89 留在 `_qa/b89_frameB.log` 里的那一行**逐字相同**
  （增益 1.96、块 600x401、全图 1500x1125、内容 0,309,1915x1435 全一致），只多了 `字节=` 那一格
  ⇒ 加字段的动作没改动算式与摆位（同一张图、同一档视口、同一枚 zoom 种子）。
- **两趟 `生效` 都过核对工具复算**：`node _qa/b89_check_frame_alignment.js` 对
  `_qa/b93bytesoriginal.utf8.log` 与 `_qa/b93bytessample.utf8.log` 各报
  `复算内容=…｜屏上念的=…` 两串相同 + `复算通过：1 行全落进不变量`
  ⇒ 那把尺子的行解析是**按字段名**锚的（`块=` 起头），插进 `字节=` 不影响它（这一条是担心的，量完是不担心）。
- ⚠️ 那三趟里 `grep -oE '字节=[0-9]+'` 各还会命中 `字节=1014784`（SQLite 原生库落盘那句）与
  `字节=3257344`（词典副本落盘那句）—— 它们**不是**这一档的下载量，读数分家靠行首的 `D_分块`。
- 三趟各念一次、每趟独立隔离数据目录（`LOCALAPPDATA` 指到 `_qa/<读数名>_local` 并 `rm -rf`），
  起跑前与收尾后同名进程数都是 0；`D_异常行=0`、堆栈关键字 0。
- 判据目录对账：`node _qa/check_ruler_catalog.js` = **0 处漂移**（表行 148 / 表合计 1244 = XML 两份数）。


## §5 定档、改判与没量的那一格

- **排期 §4 那一行改判**：从"接现成品 ≈ 一批（新依赖 + 一屏换件 + 台架）"改成
  **不接 —— 四条已量完并各自给出否定依据**（① desktop artifact 零公开面且六版一致、② 状态算式吃 Telephoto 自家
  `ZoomState` 而解码层不可用、③ 三档同一轨而"本机档"没有对象、④ 那条判据已经有尺子）。
  自研那三层（批 89/90/92）继续走，**不重开**。
- **没量的那一格（不许写成已通）**：
  - 三档里 `PREVIEW` 从来没有一条 `生效` 读数（§1 ③ 那表里是 0 条）——那是**增益门槛的正确行为**而不是缺口，
    本批那一趟 `不换轨 档=PREVIEW 字节=3949`（§4）把"这一档确实只有 3.9 KB 的货"落成了一行自报档位的读数。
    **但只有一趟**，不许写成"预览档在任何一张图上都换来换去都不换轨"。
  - 带宽那一句是**结构性口径**（Coil 盘缓存就是整份文件），不是量过的"某一站给不给 Range"。
    要真省带宽得动取数层（HTTP Range + 缓存键跟着变），**风险面大、未拍板**，本批只登记不实施。
  - 上游 `#125`「The zoom on the Desktop does not work correctly」只读到标题与状态（GitHub API 随后限流），
    没读正文 ⇒ 不据此说"telephoto 的桌面手势坏了"，只说它是一条 open 的题。
