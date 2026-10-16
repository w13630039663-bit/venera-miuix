# 批 95 · 详情那枚主胶囊的填色不走库那条链（2026-10-12）

题面不是"想换个颜色"，是批 94 的包内图证抓到的一条**既有缺陷**：同一窗里描边与文字是藤紫，而整块涂色的那一枚拍出来是天蓝。本批先把它量到字节码级，再决定改法。

## 0. 这一批改了什么

| 层 | 文件 | 那一格 |
| --- | --- | --- |
| 新增 token | `desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopTheme.kt` | `AccentFill = Color(0xFF5B47A8)`（稿 `docs/designs/windows-gallery-home-touhou-2026-10-03.html:20` 的 `--accent-fill`），KDoc 写明它**不是** surface 阶梯的第六档灰、只许出现在"填充型强调"那一形 |
| 接线 | `desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopComicDetail.kt` | 那枚主胶囊从 fluent 的 `AccentButton` 换成仓里那形（`Column` + `background(AccentFill, RoundedCornerShape(4.dp))` + `clickable` + `padding(12/8)`），两行文字各显式吃 `DesktopTheme.TextPrimary`（稿 `--on-accent` = `#FFFFFF` 与既有最亮文字档同值 ⇒ 不新增色值）；`AccentButton` 的 import 撤掉 |
| 判据（新） | `desktop/src/test/kotlin/com/venera/desktop/gallery/ui/DesktopAccentFillTest.kt` | 五颗：① 现状读数（递进藤紫而那一格交回库默认那枚蓝）、② 两枚陌生色同格、③ 现数那张预置表**只有一颗键**、④ 六枚候选恒同一枚蓝、⑤ 量具能力边界（离屏渲染给得出尺寸而像素恒 `0x0` ⇒ 色值的像素级读数今天拿不到） |
| 判据（扩） | `desktop/src/test/kotlin/com/venera/desktop/gallery/ui/DesktopComicContinueReadingTest.kt` | 九颗 → 十一颗：⑩ 那枚胶囊的填色走自家档而 `AccentButton(` 零处、⑪ 那两行文字里不许出现 `.copy(alpha =` |
| 判据（扩） | `desktop/src/test/kotlin/com/venera/desktop/gallery/ui/DesktopCardOverlayTest.kt` | 加一条 argb 断言：`DesktopTheme.AccentFill` 必须等于稿里 `#5B47A8`（token 的值不许被"顺手调深一点"改掉） |
| 登记 | `desktop/src/test/kotlin/com/venera/desktop/gallery/ui/DesktopSurfaceLiterals.kt` | `"0xFF5B47A8"` 进"允许在 `DesktopTheme.kt` 出现"那张表（不登记就等着别人在别处再写一遍同值） |

`app/` 这一批**一字未动**（现查 `git status --porcelain app/` 空）。

## 1. 量到的事实（全部现量，逐条给读数）

**① 库那条链的走向（`javap -c` 到 `fluent-desktop-v0.1.0.jar`）**

`FluentThemeKt.darkColors-8_81llA(long accent)` → `ColorsKt.generateShades-8_81llA(long)`：

```
0: invokestatic  getAccentShades:()Ljava/util/Map;
7: Map.get(accent)
15: dup / ifnonnull 23
20: invokestatic  getDefaultShades()        // ← 不在表里就整条回落
```

**② 那张预置表的内容（本批的关键一格）**

`getAccentShades()` 自己的字节码是 `mapOf(<一颗> to <一个 Shades>)` —— `TuplesKt.to` 之后喂给 `MapsKt.mapOf` 的**只有一枚 Pair**，所以这张表**只有一颗键**。那颗键的常量是 `4278221012l` = `0xFF0078D4`（Windows 蓝），那一枚 `Shades` 的七个常量换算成 ARGB 是 `#0078D4 / #0093D9 / #60CCFE / #98ECFE / #005EB7 / #003D92 / #001968`（`Shades` 字段表：`base, light1, light2, light3, dark1, dark2, dark3`，由 `javap -p` 现读）。
而 `getDefaultShades()` 的字节码是 `accentShades.entrySet().first().getValue()` ⇒ **回落那一份就是这颗键自己的值**。

**③ 两句话合起来的推论**：深色档上 `fillAccent.default` 恒为 `#60CCFE`，**递进任何 accent 都动不了它** —— 命中那张表拿到的还是同一份，不命中回落到同一份。

**④ 进程内读数（`runComposeUiTest`，零窗口）**

| 判据 | stdout 读数 |
| --- | --- |
| ① 递进藤紫 | `D_主题 fill=0xff60ccfe00000000 文字强调=0xff98ecfe00000000 递进去的=0xffcbb6ff00000000` |
| ② 两枚陌生色 | `D_主题 两枚没见过的色 fill=0xff60ccfe00000000/0xff60ccfe00000000` |
| ③ 现数表内容 | `D_主题 表大小=1 命中默认蓝=true 命中藤紫=false` |
| ④ 六枚候选 | `D_主题 候选逐枚 fill=` 藤紫 / **表里唯一那枚蓝** / 红 / 绿 / 黄 / 棕 **六枚全是 `0xff60ccfe`** |
| ⑤ 离屏像素 | `D_主题 像素探针 宽=40 高=40 采样=0:0x0 20:0x0 39:0x0 39:0x0` —— **尺寸对而像素恒透明黑** ⇒ 这条路量不到屏上像素（见 §4 末段） |

④ 里"表里唯一那枚蓝"那一格是本批最值钱的一枚读数：它把"命中了就会跟着变"那一种解释也量掉了 —— **换色这条路根本不存在**，不是"我们没踩上表里的那一枚"。

**⑤ 稿那一侧的口径**（`docs/designs/windows-gallery-home-touhou-2026-10-03.html:20,133`）：`--accent-fill:#5B47A8`、`--on-accent:#FFFFFF`，而 `.btn.acc{background:var(--accent-fill); … color:var(--on-accent)}` —— 稿里那枚强调按钮本来就是**紫底白字**，走自家档不是"我们自己造一档"，是回到稿那一份。
⚠️ `--accent`（= `--fuji-lite`）与 `--accent-fill` **是两枚**：前者管文字与指示条（既有 `AccentFuji`），后者管底。把 `AccentFuji` 拿来涂底会亮得压不住白字。

## 2. 改法为什么选"不经过那条链"

拍板过的两条路（排期文档行 16 那两句）**其实是三条** —— 写这一节时 `javap -p` 现核了 `AccentButton` 的签名：

```
AccentButton(Function0<Unit> onClick, Modifier modifier, boolean disabled,
             VisualStateScheme<ButtonColor> color, MutableInteractionSource interactionSource,
             boolean allowShadow, Arrangement$Horizontal horizontalArrangement, Function3 content)
```

- ① **递一枚显式 `Colors` 给 fluent**：那颗件的 `darkColors` 只有 `accent` 一枚参数（`javap` 现核：`darkColors-8_81llA(long)` 与它的 `$default`，没有部分覆盖的重载），要改 `fillAccent` 就得自己拼整棵 `Colors`（几十格）—— 贵且下一次库改字段就崩。
- ② **`AccentButton` 自己那枚 `color: VisualStateScheme<ButtonColor>`**：这条路**存在**（本批之前我把它写成"没有入口"，那是**错的**，被自己仓库里那条记忆打脸 —— 见手册 §0.1 ㊭）。它要给出 normal/active/hover/focused/disabled 各态的 `ButtonColor`，等于**新造一整套色值对**，与「观感尺寸用现成口径、不造新数字」那条约束相冲。⚠️ 本批**没有实测它的构造代价**（只核到签名那一层），所以它记成"当时可选而未量"，不记成"不可行"。
- ③ **换成仓里那形**（选这条）：桌面已经有 `Column + background + clickable + padding` 那一族形状（同屏「重取」那枚、返回那枚、收藏那枚都是），层级差用**字号**给，色值只添一枚（稿里本来就写着的那枚）。代价 = 丢 fluent 那套焦点/悬停形状。

⇒ 判据 ⑩ 钉的是"**这一屏不经过 `fillAccent` 那条链**"，而不是"库那条链不可修"。哪天要接焦点环，② 那条路是 ③ 的替代方案，届时把 ⑩ 改判（不许删）。

⚠️ 这一条不是没有代价的选择，登记的欠账：详情那一屏的这枚胶囊从此**不吃键盘焦点环**（仓里那形没有 focus 视觉）。同一条链上另一处 `AccentButton` —— 源管理「登录」那枚（`DesktopComicSourcesScreen.kt:281`）—— 本批**没动**：它拍出来也是同一枚蓝，但那一屏今天没有图证需求，且换形要连带核它那条 `disabled` 行为。留作下一批的对照（不静默撤）。

另一条口径落进判据 ⑪：**不许用 alpha 压主色**。对岸那行小字写的是 `onPrimary.copy(alpha = 0.8f)`，本仓早定过"次要文字/禁用态别拿 alpha 压主色"（MD3 那条），所以两行文字都是 `TextPrimary` 全不透明，层级差交给 13.sp / 11.sp。

## 3. 台架（`_qa/b95_perturb.js`，6 笔，台账 `_qa/b95_ledger.log`）

每笔只改一处（P5 两枚编辑），`check` 阶段先要求起点在干净树上恰好命中一处、终点 0 处（读数：`6 笔各命中一处，可跑`）。

| 笔 | 改坏的那一格 | 红了谁 | 判 |
| --- | --- | --- | --- |
| P1 | 填色换回 `DesktopTheme.colors().fillAccent.default` | ⑩（`AccentFill` 计数 1→0）+ ⑪ | 两把都该红 —— ⑪ 用那枚 token 做**定位锚点**，token 一走它跟着红。这条依赖如实登记：⑪ 不是完全独立的一把，它测的是"锚点之后那 14 行"，锚点本身由 ⑩ 守 |
| P2 | 填色 `.copy(alpha = 0.5f)` | **只 ⑪** | 隔离成功 ⇒ ⑪ 量的是 alpha 那一族写法，不是 token 在不在 |
| P3 | 填色写成色值字面量 `Color(0xFF5B47A8)` | `DesktopCardOverlayTest`（"除 `DesktopTheme.kt` 之外不许出现 hex"）+ ⑩ + ⑪ | 一次红三把 ⇒ 那枚 token 不是摆设：同值直接写进调用点会被整目录扫描抓住 |
| P4 | 胶囊改成 `clickable(enabled = false)` | `DesktopComicDetailSemanticsTest` 两颗（`NoSuchElementException: List is empty`） | 本批最容易出错的那一格（形状与颜色全对而动作没跟上）量得到 —— 搬运 `onClick` 到 `Modifier.clickable` 之后确实还接得上 |
| P5 | 把库那颗控件请回这一屏（加 import + 一颗零调用者的 private 探针） | **只 ⑩** | ⑩ 的负锚点独立咬得住；那颗探针没被 `DesktopUnusedInternalTest` 抓（它明确不扫 private，"交给编译器"）—— 台架笔的形状这次没被邻居抓住 |
| P6 | 等价改写：token 定义多包一对括号 | 一笔没红 | 这是**正确输出** ⇒ 尺子钉的是值不是写法 |

收尾"还原后的全量"：`EXIT=0`、`suites=150 tests_sum=1262 testcase=1262 @Test=1262 files=157 failures=0`。

## 4. 包内与图证

包：`_qa/desktop-exe-2026-10-12-b95/`（216 MB / `node _qa/check_cfg.js …/app` 给 `classpath=100 missing=0 orphan_jars=0` / `node _qa/check_pkg_jar_sha.js` 现跑两颗都"对得上"：包内 `desktop-fc351697cb4a38221fd486477830775d.jar` **4,799,218 B** sha256 `a5b2b253…`、`engine-probe-ae823d3b…jar` 146,825 B sha `269a5321…`，两颗都与现产物逐字节同一颗）。

四趟包内往返（量具 `_qa/b94_pkg_resume.sh`，隔离数据目录 `_qa/b95_local`，绝不碰真人那份 `%LOCALAPPDATA%\venera`）：

| 趟 | 那一格 | 读数 |
| --- | --- | --- |
| b95d1 | 直连那一趟**没走通** | 等满 180 s 仍没念 `D_续读`，全文 39 行、`异常行=0`、收尾同名进程数=0。原因在 stdout 里写着：`HTTP 404 https://www.cdntwice.org/setting` + `[session] 信封失败 error=Invalid Status Code: 404` ⇒ 今天**直连打不开 jm**，不是续读没接上 |
| 修量具 | 代理那一格从前只测一枚端口 | 批 94 那一课（照抄死代理）之后加的是"现量 7890 在不在听"，而今天 7890 `TcpTestSucceeded=False`、**7897=True**（Clash Verge 的 mixed 端口）⇒ 已改成逐枚现量 `7890/7897` 并把**命中哪一枚**念出来（`代理=--proxy=127.0.0.1:7897（逐枚现量 7890/7897，命中端口=7897…）`）。同一族的新形状：**"没有代理"与"直连能用"是两枚前提，各自都要现量** |
| b95d2 | 带代理那一趟走通 | `D_续读 号=1461316 记录=无` + `D_详情 源=jm 号=1461316 态=上屏 话=15 简介=81字 封面=有`、`异常行=0`、收尾同名进程数=0 |
| b95d3 / b95d4 | 拍帧那一趟 | d3 被量具自己的"念完 12 s 就关窗"抢掉了（轮询式量具的"念完即关"与"留窗给人拍"天然冲突）⇒ d4 **直起 exe 不经量具**，窗留着拍完再按名关，收尾现数同名进程数=**0** |

**图证那一条**：`get_window_state`（`source=windows-graphics-capture`、`occlusionSafe=true`、`relatedTransientWindows=[]` ⇒ 没有 Error 弹层）拍到 `衣冠禽獸`（1461316）那一屏，顶上那枚整宽胶囊是**紫底白字**「开始阅读」—— 与批 94 那帧同一格的天蓝**正面对照**。⚠️ 那条路只回内联图，**不落盘**。

**像素那一格本批拿不到读数，三条路各自量过**：

1. `CopyFromScreen`（`_qa/b78_shotwin.ps1`）：拍回来 488,539 B，与批 94 那张 REJECTED **同字节数**，五点采样全是壁纸蓝（`#0727C4 / #0927C6 / #2152C8 / #0726C2 / #2251CA`）—— 那张已改名 `_qa/b95_frame_accent_REJECTED_wallpaper.png`，不许当证据。
2. 窗口捕获（上面那条）：真那一屏，但没有可采样的 PNG。
3. 离屏渲染 `captureToImage()`（本批新试的第三条路）：**尺寸给得对而每个像素都是 `0x0`**（`宽=40 高=40 采样=0:0x0 20:0x0 39:0x0 39:0x0`）⇒ 收成判据 ⑤，红了要升级成真判据而不是删。

⇒ 所以"那枚胶囊画出来是 `#5B47A8`"这一格，本批的最强证据链是：`DesktopCardOverlayTest` 的 argb 断言（token 的值）+ ⑩（那一屏的填充恰好吃这枚 token、且不吃库那颗控件）+ ④（六枚 accent 都动不了库那一格 ⇒ 换色无路）+ 一帧内联图（屏上真的紫了）。**像素级读数缺失，如实登记，不假装有。**

（重打后回填）

## 5. 数字

`:desktop:test` **1263 判据 / 150 类 / 0 失败**（`suites=150 tests_sum=1263 testcase=1263 @Test=1263 files=157 failures=0`，`node _qa/three_way_counts.js` 现跑"三方对得上"；批 94 那一跑是 1256 / 149 ⇒ 本批 +7 颗 = `DesktopAccentFillTest` 五颗 + `DesktopComicContinueReadingTest` 两颗）。
`desktop/src/main` **112 颗 / 23,498 行**（批 94 那一版 112 / 23,469 ⇒ 本批不添新件，只多 29 行）、`desktop/src/test` **157 颗 / 29,420 行**（新建一颗 `DesktopAccentFillTest.kt`）。
`app/` 受保域：`git status --porcelain app/` 现数 **0 行**。
台架 6 笔 + 包内四趟 + 交付包一份（§4）。

## 6. 欠账与下一步

- 源管理「登录」那枚 `AccentButton` 还是同一枚蓝（§2 末段），本批只把它**登记**下来。
- 仓里那形不吃焦点环（§2 末段），要么给它补 `onFocusEvent` 那一族，要么接受 —— 与全站其它手搭控件同一挂。
- 稿里 `--on-accent` 是纯白，而 `TextPrimary` 今天与它同值；若哪天外观轴把 `TextPrimary` 改成非白，⑪ 那两行字的对比度要重看。
- **屏上像素的色值读数本批拿不到**（§4 末段三条路各自量过）：要拿到它，得先有一条"能把窗口拍成 PNG"的量具（`CopyFromScreen` 在这台机器上今天仍只拍得到壁纸，而窗口捕获那条路不落盘）。⇒ 挂给下一批：**要么补一条 `windows-graphics-capture → PNG` 的量具，要么接受"色值由 argb 判据 + 接线判据钉"**。
- 轮询式量具（`b94_pkg_resume.sh`）"念完 12 s 就关窗"与"留窗给人拍帧"天然冲突 —— 本批第三趟就是这么错过的，第四趟改成直起 exe 才拍到。要么给那颗量具加一枚 `--hold` 参数，要么在文档里写明"拍帧要另起一趟"（本批选了后者）。
