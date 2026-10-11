# 批 87·下半 · 阅读那一屏的键位（2026-10-11）

接 `desktop-reader-paging-2026-10-11.md`（上半：整页翻那一档 + 模式那枚偏好）。
下半做的是**那一屏的键**：`←` `→` `PageUp` `PageDown` 接上，音量键**不接**而是先交一把量具
（`D_按键` 那一句把 AWT 原始码念出来），并把"按键接线只有一处"那条旧口径改成"一屏一处"。

`app/` 这一批仍 **0 行改动**；桌面编译面仍 **86 颗 / 11,378 行**（`node _qa/shared_face_counts.js` 现量）。

---

## 1. 先量真相：键这一族在对岸与在桌面上各是什么

### 1.1 对岸阅读那一屏只认音量键，而且默认关

`app/src/main/java/com/venera/compose/reader/VeneraReaderScreen.kt:602-625`（只读，未动）：

- 闸门两枚：`activePanel == ReaderPanel.NONE && volumeKeyTurn`，且 `nativeKeyEvent.action == ACTION_DOWN`；
- `KEYCODE_VOLUME_DOWN` → `turnToNextPage()`，翻到章末就 `switchToChapter(+1, 0)`；
  `KEYCODE_VOLUME_UP` → `turnToPrevPage()`，章首则回到上一章的最后一页 —— **跨章那一格桌面今天没有**；
- 方向键**根本不在对岸阅读那一屏上**（桌面这边的 `←/→` 是桌面自己的键位，没有对岸口径可回核）；
- 那枚偏好：`VeneraPreferences.kt:78` `getBoolean(KEY_VOLUME_KEY_TURN, false)` ⇒ **默认关**，
  键名 `pref_volume_key_turn`（`:662`），设置页有可见的开关（`feature/settings/ReaderSettings.kt:22`、
  阅读面板 `VeneraReaderScreen.kt:1514`）。画廊那一大图另有一枚同族键（`GalleryViewerPolicies.kt:16-21`，
  `isVolumeDown` 时 `currentPage + 1`）。

⇒ 桌面要接音量键，得同时凑齐"默认值口径 + 一枚看得见的开关 + 一条真通路"。前两条缺（桌面设置页没有那一枚），
第三条**一次都没量过** ⇒ 接上就是本仓最忌的假开关。

### 1.2 AWT 那一侧：本批新量到的一处纠正

上一批的笔记写的是 `androidx.compose.ui.awt.getAwtEventOrNull`。**那是错的** ——
`javap -v androidx.compose.ui.awt.AwtEvents_desktopKt`（`ui-desktop-1.12.1.jar`）里那条是
**扩展属性** `KeyEvent.awtEventOrNull`（`d2` 数组第一个串就是属性名 `awtEventOrNull`，
JVM 侧才是 `getAwtEventOrNull-ZmokQxo(Object)`）。写成函数调用的第一件事就是编译器报
`Unresolved reference 'getAwtEventOrNull'` —— **编译器又一次替人纠正笔记**。

同一颗 jar 里读到（`javap androidx.compose.ui.input.key.Key`）：
`access$getPageUp$cp` / `access$getPageDown$cp` / `access$getVolumeUp$cp` / `access$getVolumeDown$cp` 都在，
而音量那两枚与 `Key.DirectionRight` 不是一路货：前者是**合成哨兵**（上一批量到 `Key(-1000000019, 0, loc=2)`），
真按键的 `key` 永远对不上它们 ⇒ 音量键唯一可能的路是 `awtEventOrNull?.keyCode`（Windows VK 175/176）。

### 1.3 第三条路证：离屏能把键送进组合树

`javap androidx.compose.ui.test.ActionsKt`（`ui-test-desktop-1.12.1.jar`）里有
`performKeyInput(SemanticsNodeInteraction, Function1<KeyInjectionScope, Unit>)`，
`KeyInjectionScope` 带 `keyDown` / `keyUp` / `isKeyDown`，扩展上还有 `pressKey` / `withKeyDown` / `withKeysDown`。
仓库此前**从没用它验过键**（`grep withKeyInputProcessor|performKeyInput` 在 `desktop/src/test` 零命中），
批 50/66 那两批的键位只有"离线喂 `DesktopKeySignal`"（算式）与"源码文本接线"两条路，
`onDesktopZoomKey` 的注释里因此明写着「⚠️ 这一格**没有图证**」。

本批开出的这条路能证与不能证的边界（写进 `onDesktopReaderKey` 的注释里）：

| 能证 | 不能证 |
|---|---|
| 焦点要到了（`requestFocus()` 落地） | AWT 那一侧的原始码送不送得进来 |
| 链在收这一枚键、收完落到 `onStep` | 真按一次音量键 Windows 认不认 |
| 按一次只落一笔（`KeyUp` 不重复动作） | — |
| 不相干的键不吞 | — |

合成注入没有 `java.awt.event.KeyEvent` 可取 ⇒ 那一路 `D_按键` 必然念 `原始=无`。
**那一格念 `无` 是身份而不是失败**：把"我这儿的测试过了"读成"用户那儿能按"就是这一族最容易糊的地方。

---

## 2. 落地形状

| 件 | 做什么 | 钉它的尺子 |
|---|---|---|
| `DesktopKeyNavigation.kt` +`onDesktopReaderKey` | 键表：`←`/`PageUp` → `-1`，`→`/`PageDown` → `+1`，抬起与别的键 → `null` | `DesktopReaderKeyTest` ①②（4 条纯用例） |
| `DesktopReaderPaging.kt` +`desktopReaderKeyReading` | `D_按键 键=… 原始=… 落点=后一页/前一页/不接` | ② 那两条 + 算式处数 1 |
| `DesktopComicReader.kt` 展示件根上那条链 | `focusRequester` + `focusTarget` + `onKeyEvent`，每一枚 `KeyDown` 都念一句 | ③ 那四条（含"每一枚按下的键都念"） |
| 装载件 `stepPaged` → `stepReaderPage` | 一颗管两档：算式仍只调 `desktopReaderStepTarget` **一次**，滚哪面墙按档二选一 | ③ 的"键与按钮吃同一颗步进" |
| 焦点读数改成两半都念 | `KEYNAV=阅读那一屏 焦点=拿到 / 没拿到` | ③ 那两条 contains（成功也要有读数，否则每一帧只能靠"没念"反推） |
| `DesktopKeyNavigationTest` ⑥ 改判 | 「全模块一处」→「一屏两处、各在自己的那一屏」+ 钉住前提 | 见 §3 |
| `DesktopGalleryViewerWiringTest` ③ 搬走 | 槽位留着说明它去了哪 | 见 §3 |

三件刻意没做：**音量键那条分支**（没有读数）、**设置页那枚开关**（接上就是按不动的那一枚）、
**`Esc` 退回详情**（对岸阅读那一屏没有这条，而这一屏已有那枚按钮；不加判据能钉的行为不塞）。

---

## 3. 一条口径的改判与一次"两把尺子钉同一件事"

**改判**：`按键接线只有一处且三件齐` → `按键接线一屏一处而两处各自三件齐`。
它原来防的是"同一条链写两遍 ⇒ 两处各自要焦点、同一枚键被读两遍"，这一条**没有放宽**，
只是宿主从"一个模块"改成"一屏"：阅读那一屏是另一层，而它与画廊浮层**不共存** ——
前提住在 `VeneraDesktopApp.kt:322` 那句 `LaunchedEffect(domain, selectedKey) { selected = null; viewerOpen = false }`，
所以新尺子把这句**一起钉着**：哪天换域不收浮层，这两处就会同时站着抢同一枚键，尺子要红在那一刻。

**两把尺子**：改判时只改了 `DesktopKeyNavigationTest` ⑥，第一次全量红的是
`DesktopGalleryViewerWiringTest.仍只有一处按键链` —— 同一件事原本有**两把**尺子，
"改一把漏一把"正是本仓记过的那一形（措辞只许一把尺子钉）。处置：把那一处数量的判据**剥回 ⑥ 独家**，
`ViewerWiring` ③ 留槽位说明它搬走了，不写第二条判式。

**顺带补的两处尺子自己的病**（都由台架逼出来，见 §5）：
- ⑥ 那句前提原来写成 `app.contains("LaunchedEffect(domain, selectedKey)")`，而这一格里有**第二枚同键的
  `LaunchedEffect`**（`:339`）⇒ 撤掉第一枚的 `selectedKey` 也不会红（锚点被同屏另一处合法调用喂饱）。
  改成钉**相邻两行**（`LaunchedEffect(domain, selectedKey) {\nselected = null`）。
- `桌面不摆音量键那一枚开关` 第一版扫 `gallery/ui/` 里"音量键"三个字 —— 当场红在
  `DesktopGallerySettingsSpec.kt:194` 那句**本来就写对了的实话**上（那是大图页 `Absent` 那一格的说明，
  说"桌面不做这件事"）。判式吃错了对象：换成钉"阅读那一族的偏好件里只许有一枚键名"（`private const val \w+_KEY` 数 1），
  那才是音量键真会先进来的地方。

---

## 4. 判据清单（`DesktopReaderKeyTest` 16 条）

| 组 | 管什么 |
|---|---|
| ① | `←` / `→` / `PageUp` / `PageDown` 各落一步而方向跟着那两枚按钮；抬起的那一半不落也不吞；不归这一屏的键交回**空**而不是 0；键表只在判据件里一处定义（含"两位邻居还在同一颗件里"的对照） |
| ② | `D_按键` 那句：落点三态（后一页 / 前一页 / 不接）、`原始=` 有数与 `原始=无` 两档、算式只在判据件一处而展示件把它念出来 |
| ③ | 链三件齐 + 两条焦点读数（`焦点=拿到` 与 `焦点=没拿到` 都得在）；每一枚按下的键都念而不只念落到的那些（判式钉**相邻两行之间不含步进那一枚**，被 `delta` 拦住就红）；键与按钮吃同一颗步进（算式一处、回调两处、两面墙各一处）；键在两档都落而按钮仍只在整页翻那一档摆（链排在整页翻那一支**之前**） |
| ④ | 音量键那一路今天不许长出代码来：`pref_volume_key_turn` / `VK_VOLUME` / `VolumeUp` / `VolumeDown` / `175` / `176` 在桌面 main 里 0 处，三条对照（对岸那枚键名在、对岸 `KEYCODE_VOLUME_DOWN` 在、对岸那句默认值仍是 `false` —— 默认值改了这条要重判） |
| ④′ | 阅读那一族的偏好件里只许有一枚键名（`private const val \w+_KEY` 数 1）+ 对照（那枚声明读得到，防正则读空变恒真） |
| ⑤ | **离屏把键真送进组合树**：按一次 `→` 只落一笔（`KeyUp` 不重复）；纵向那一档 `PageUp` 也落到列表那面墙；不相干的键（`↑`）不落 |

另有两行**改判**（不添用例）：`DesktopKeyNavigationTest` ⑥（见 §3）与 `DesktopGalleryViewerWiringTest` ③（槽位留说明）。

---

## 5. 台架：18 笔（`_qa/b87low_perturb.js`，驱动 `sh _qa/perturb_driver.sh b87low 18`）

要求红的 16 笔**全部咬住**（合计 33 条红），要求不红的 2 笔等价改写**确实一笔没红** —— 后两笔的正确输出就是
`BAD 这一笔…要求不红，而它确实一笔没红 —— 这是正确输出`。逐笔还原后的收尾全量：
`EXIT=0` / `suites=142 tests_sum=1165 testcase=1165 @Test=1165 files=149 failures=0`。

| 笔 | 喂进去的病 | 咬住的尺子（红数） |
|---|---|---|
| P1 | 翻页键 `PageUp` 从表里撤掉 | 2：`方向键与翻页键各落一步…` + 离屏 `纵向那一档也吃同一对键…` |
| P2 | **等价改写**：或运算两侧倒过来 | **0**（正确输出） |
| P3 | 抬起的那一半反过来当按下 | 3（含离屏那两档） |
| P4 | `←` 与 `→` 互换 | 3 |
| P5 | 只念落到的那些键（`if (delta != null)` 拦住那一句） | 1：`每一枚按下的键都念…` |
| P6 | 原始码取不到时念成 `0` | 1：`合成那一路念得出原始等于无` |
| P7 | `不接` 那一档的词漂一格 | 1 |
| P8 | 成功那一半不再念出「焦点=拿到」 | 1（**改锚点后补跑一遍仍咬**，见 §6 的 ASCII 那一格） |
| P9 | 键交给别人的那张表（缩放表） | 6 |
| P10 | 链上撤掉 `.focusTarget()` | 2 —— **只红源码文本那两把，三条离屏用例全绿** ⇒ 见下面那条改判 |
| P11 | 同一颗文件里再挂一处按键接线 | 1：⑥ 那把（数到三处、文件名对不上） |
| P12 | 换域不再收浮层（`LaunchedEffect(domain)`） | 1：⑥ 的前提那一格 |
| P13 | 阅读那一族的偏好提前长出第二枚键名 | 2：`偏好今天只有一枚键` + `音量键那一路不许长出代码` |
| P14 | VK 175/176 那条分支提前接上 | 2 |
| P15 | 步进算式在按钮那一路另算一份 | 3 |
| P16 | 键那一路一次多翻一步（`delta * 2`） | 3 |
| P17 | 纵向那一档不再滚列表那面墙 | 1 |
| P18 | **等价改写**：那一句拼成四段再 `joinToString` | **0**（正确输出） |

三笔台架自己要记的：

- **P10 把 §1.3 那条断言改判了**。第一版这一节写的是"离屏那一路能证焦点要到了"，而 P10 撤掉 `.focusTarget()`
  之后三条离屏用例**照样全绿** ⇒ 那一路**量不到焦点**，它只证「链在树上、事件进得来、落到 `onStep`、抬起不重复」。
  焦点那一格改由窗口里的 `KEYNAV=… granted=1/0` 说（§6 的三帧 + 包里那一帧）。**判据件的 KDoc 已跟着改**，
  不许留着一条被自己台架推翻的话。
- **P12 教的又是"锚点被同屏另一处喂饱"**：⑥ 那句前提第一版只钉 `LaunchedEffect(domain, selectedKey)` 那一串，
  而这一格里有**第二枚同键的 effect**（`:339`）⇒ 撤掉第一枚的 `selectedKey` 也不红。改成钉**相邻两行**
  （那一句紧跟 `selected = null`）之后 P12 才咬到。
- **P8 与代码同时改过一次**：那两句读数后来补了 ASCII（`granted=1/0`，见 §6），锚点跟着换，
  这一笔**补跑了一遍**才写进本表（`sh _qa/perturb_driver.sh b87low 8 8`，输出 `RED … 没念「焦点=拿到」` + 收尾全量 0 红）。

耗时：23:08:33 起、23:33:09 收尾 = **24 分 36 秒**（18 笔 + 收尾全量，每笔都是全量 ≈1 分 22 秒）；
P8 补跑一笔 + 收尾 ≈ 3 分 42 秒。**台架合计 ≈28 分钟**。

---

## 6. 图证

起窗脚本 `_qa/b87low_frame.sh`（数据目录隔离在 `_qa/b87low_local` 与 `_qa/b87low_pkg_local`，每一趟 `--no-daemon`）。
⚠️ **量具自己先踩一脚**：第一版脚本起的是 `:desktop:run`，而那些取证种子是**程序参数**、只有 `:desktop:app`
那一颗 JavaExec 把它们拼进 `args` ⇒ 种子一个字都没到、屏上落在默认那一域而**不报错**
（反证：`_qa/b87low_frameD.log` 里既没有 `KEYNAV` 也没有 `--read-mode`，`D_落点 域=图库(画廊)`）。
脚本里已写明这一条，第一帧改名 `_qa/b87low_frameD2.log`。

| 帧 | 参数 | stdout 关键读数（log 文件） | 屏上 |
|---|---|---|---|
| D2 | `-Pread=1461316/1461318 -PreadMode=HORIZONTAL_LTR -PreadPage=2`（pid 29368） | `KEYNAV=阅读那一屏 焦点=拿到`（补 ASCII 之前的写法）、`D_阅读 起点 请求=2 量到=2 页表=89 试=1`、`D_历史 写 … 页=2/89`、`D_落点 域=漫画(启动) … 阅读=开(启动) 种子=read` | 「美漫·左至右」红、`‹ 上一页 › 下一页 ›`、「共 89 页 第 3 / 89 页」、那一页**画出来了**、红字那句种子实话 |
| E | 同上而**不给** `-PreadMode`，`-PreadPage=5`（pid 51648） | `KEYNAV=… 焦点=拿到`、`请求=5 量到=5 页表=89 试=4`、`D_历史 写 … 页=5/89` | 「条漫·连续」红、**没有**那两枚翻页钮（按钮仍只在整页翻那一档）、纵向流、「第 6 / 89 页」 |
| F | `-PreadMode=HORIZONTAL_RTL -PreadPage=7`（pid 48812） | `KEYNAV=阅读那一屏 焦点=拿到 granted=1：…`、`请求=7 量到=7 页表=89 试=1`、`D_历史 写 … 页=7/89` | 「日漫·右至左」红、那两枚翻页钮、「第 8 / 89 页」、那一页画出来了 |
| 包 | `_qa/desktop-exe-2026-10-11-b87low/` 直起 exe，程序参数 `--proxy=direct --read=… --read-mode=HORIZONTAL_RTL --read-page=4`（pid 42216） | **中文全成乱码而 `granted=1` 照样读得出来**（`KEYNAV=… granted=1…`）、`D_数据目录` 那行的路径格是 ASCII 的 `_qa\b87low_pkg_local\venera` | 「日漫·右至左」红、那两枚翻页钮、「共 89 页 第 5 / 89 页」；那一格此刻还是黑的（页没取成，本帧要量的是 chrome 与读数，不是取图） |

四帧各钉一件事：

- **D2 / E / F：焦点那一格在真人窗口里是量到的，不是推的**。两档（整页翻与纵向连续）都念 `焦点=拿到`，
  所以"链接了而屏上不认"那一族失败形状在这一屏没有发生 —— 这是**这一批唯一能在窗口里量的那半句**
  （按键本身属可见影响，本 agent 不许碰）。
- **包里那一帧是这一批的交付要点**：量具必须在**用户真按得到键的那一份**里。而包内中文 stdout 全乱码
  （批 87 上半量过的一条），所以那两句读数**补了一截 ASCII**（`granted=1` / `granted=0`）——
  没有这一截，包里那一趟只能看见 `KEYNAV=` 而读不出是拿到还是没拿到。判据跟着钉住两句都带 ASCII。
- **`试=` 这一格现在有三枚 pager 读数**（页 2 / 3 / 7 起 → `试=1`）与**一枚纵向读数**（页 5 → `试=4`）：
  仍只够说"这几趟如此"，**不许写成"pager 那面墙总是落得更快"**。
- 跑完包内那一趟，`_qa/b87low_pkg_local/venera/prefs/` 里 **0 颗文件** ⇒ "种子不写盘"在交付物里第四次成立。

两把包尺子现跑：`check_cfg.js` → `classpath=100 missing=0 orphan_jars=0`；
`check_pkg_jar_sha.js` → 包内 `desktop-dd9743f5….jar` **4,711,544 B** 与现产物 `desktop/build/libs/desktop.jar` sha256 逐字节同一颗。
包内那颗 jar 里 `DesktopReaderPagingKt` / `DesktopKeyNavigationKt` / `DesktopComicReaderKt` / `DesktopReaderMode` 各类都在（`unzip -l` 数到）。
旧包 `_qa/desktop-exe-2026-10-11-b87/` 与 `_qa/desktop-exe-2026-10-10-b829b/` 都**没动**（删除要走可逆路径，等点头）。

---

## 7. 下一件与欠的格

1. **真按一次音量键** —— 只有人能测。要读的正是 `D_按键 … 原始=` 那一格有没有数：
   有数 ⇒ 下一批接那条分支并补设置页那枚开关；念 `无` 或压根不念 ⇒ 桌面上这一族结构性没有通路，登记为"不做"。
   量具现在在 `_qa/desktop-exe-2026-10-11-b87low/` 那一包里（阅读那一屏，任意一档，按任意键都念）。
2. **三枚档位药丸仍没被点过**（上半遗留那一格）：`setReadingMode` 至今没有帧；换档时"落在摆过那一页"也没有帧。
3. **`reverseLayout` 的视觉方向仍未量**：帧上只看得见哪一枚药丸红与页号，"这一页画在左还是右"没有对证。
4. **离屏那一路量不到焦点**（P10 改判）：焦点只有"起窗读 `granted=`"一把尺子；真人机上按一次键才算闭环，挂在第 1 项那一趟。
5. **跨章翻页**：对岸音量键那一路到章末会 `switchToChapter`，桌面这一屏今天没有"下一章"这个动作 ⇒ 要接键翻页翻到章末时该做什么，是个待拍板而不是待实现。
6. **`Esc` 退回详情**本批刻意不做（对岸阅读那一屏没有这一条，而这一屏已有那枚按钮）。
7. **下一件**：批 88（排期表 `remaining-plan-and-reuse-2026-10-10.md` §4）。音量键那一格等人的一趟读数，不排批。
