# 桌面线第三批：`--start-domain=` 取证参数 + 两条被推翻的读数（2026-10-04）

> 入口摘要在同日 `docs/HANDOFF-2026-10-04.md` §16。这份是完整账：判据原文、`javap` 判法、
> 编码那一条的前后读数、以及两件**没做**的事各自卡在哪。
> 改了 8 颗文件，全在 `desktop/`；Android 线零改动。

---

## 1. 这一批要解决的到底是什么

`desktop-gallery-favorites-and-search` 那批结的时候留了两处"没做完"（§13.2 那两处），
两处的成因是同一句：`--shot=` 只能落在写死的那一域。而替代路径是"请用户点鼠标"，
那条在 2026-10-04 被明令禁掉（验证禁令）。

⇒ 所以这一批的产物**不是功能**，是**一条能把观感变成读数的路**：
命令行上说清要看哪一域，程序自己落那张图。

它只解决"落在哪一域"。"拍到它"那一环仍然卡着，见 §6。

---

## 2. 三层各自落在哪

| 层 | 文件 | 内容 |
|---|---|---|
| Win32 半边 | `desktop/src/main/kotlin/com/venera/desktop/MicaProbe.kt` | 新增 `setTopmost`（`SetWindowPos(HWND_TOPMOST)` + 回读 `WS_EX_TOPMOST`）、`windowAtPoint`、`windowDescribe`（标题与类名）；`isPointOnWindow` 的父链从一层走到六层 |
| 判读（可测） | `desktop/src/main/kotlin/com/venera/desktop/ForensicFlags.kt` | `StartDomainReadout`（`NotGiven` / `Resolved(domain, raw)` / `Unresolved(raw)`）+ `startDomain(args)`；`KNOWN` 里登记 `--start-domain` |
| 接线 | `desktop/src/main/kotlin/com/venera/desktop/VeneraDesktop.kt` | `main()` 里 `when(readout)` 三档：默认域 / 打一条"认成「题名」" / 打两条读数后 `exitProcess(1)`；调用点 `startDomain = startDomain`；`selfShot` 加抬窗 + `finally` 两层落回 + 拒图时报出**谁**占着中心点 |
| 消费 | `desktop/src/main/kotlin/com/venera/desktop/gallery/ui/VeneraDesktopApp.kt` | 签名多一颗**必填** `startDomain: DesktopDomain`；当前域初值从写死 `GALLERY` 改成那颗参数 |
| 启动通道 | `desktop/build.gradle.kts` | `app` 任务加 `-PstartDomain=` 透传，并加三条 UTF-8 jvmArgs（§5） |
| 判据 | `ForensicFlagsTest.kt`（5 → 9）、`gallery/ui/DesktopDomainNavigationTest.kt`（13 → 14） | 见 §3 |
| 口径改正 | `gallery/ui/DesktopGalleryFavoritesPane.kt` 的类注释 | 把"桌面没有弹层宿主"那句换成 §4 的真实形状（**只改注释，代码一行未动**） |

`VeneraDesktopApp` 由 `public` 改 **`internal`**：`DesktopDomain` 是 internal 枚举，public 签名会把它
连带 `DesktopAbsence` / `DesktopAbsenceKind` 一起暴露。"集成方只认识这一颗"没有变，
集成方就是同模块 `VeneraDesktop.kt` 的 `main()`。

---

## 3. 六条新判据的原文与它们各自防的那次错

| 用例名 | 防的是什么 |
|---|---|
| `起始域对每枚域都认枚举名与中文题名` | **遍历七枚**而不是抽两枚 —— 将来加第八枚域只往 enum 里塞一行、忘了它能不能被认回来，症状是"那一域的取证静默不可用" |
| `没给起始域与认不出是两种读数且不塌回默认域` | 塌回默认域会产出**一张顶着目标域文件名的默认域截图**；文件名替它作了伪证 |
| `起始域只给名字不带值是认不出` | 与 `state()` 的形状对照：同一处"只给名字"在开关上是**按开处理**，在这一颗上必须是认不出 —— 两口径不同是有意的，用例把差异钉住防止被"统一"掉 |
| `起始域参数已登记而拼错那枚仍要报出来` | 带**对照组**：只断言"登记的不在 `unknown` 里"是半条判据，`KNOWN` 整颗清空它照样绿 |
| ⑭ `起始域参数必须真被交给应用根`（扫代码行） | 「实现了但零调用点」：判读层自己有用例，**它不会因为"没接上去"而变红**。断四件事 —— `main` 里调了 `ForensicFlags.startDomain(`、有 `exitProcess(1)`、有 `startDomain = startDomain`、应用根不再是 `mutableStateOf(DesktopDomain.GALLERY)` 而是 `mutableStateOf(startDomain)` |
| ⑮ `抢前台只发生在取证那一路`（扫代码行） | a 路抬窗的边界：`frame.toFront()` 全文件恰好一次且在 raise 内、**`requestFocus` 零次**（抢键盘焦点与"截一张图"不成比例）、`main()` 体内无 `alwaysOnTop`、`selfShot` 里 AWT 与 Win32 **两层落回**都在 `finally` 内（非局部 return 会绕过链式收尾） |

**红过一次**（§10 的三条规矩之一）：把调用点临时改成 `startDomain = DesktopDomain.GALLERY` ⇒
`DesktopDomainNavigationTest > 起始域参数必须真被交给应用根 FAILED`，`:desktop:test` BUILD FAILED（exit 1）；
改回来复绿。这条 ⑭ 确实能红，不是摆设。

---

## 4. ⚠️ 被推翻的那条：「桌面没有弹层宿主」

### 4.1 这句话是怎么长出来的

`DesktopGalleryFavoritesPane.kt:54` 与轮次文档 `desktop-gallery-favorites-and-search-2026-10-04.md:56-57`
写的是：`Dialog` 系弹层"在那批被判负，理由记在弹层几份轮次文档里"。去追那几份文档，
两条出处讲的都是 **Android / miuix**：

| 出处 | 原文说的事 |
|---|---|
| `miuix-glass-surface-material-2026-09.md:47,:54` | miuix `OverlayDialog` 依赖 miuix `Scaffold` 内的 `MiuixPopupHost`；设置页链路没有那颗 Scaffold ⇒ 点了没反应的假弹窗，故改用窗口级 `WindowDialog`。**Android 侧的组合根问题** |
| `explore-and-gallery-remediation-2026-10-04.md:457` | 玻璃采不到独立 Dialog 窗口 ⇒ **弹层只能实底**。**材质限制，不是宿主缺口** |

也就是：一句 Android 侧的宿主条件 + 一句 Android 侧的材质限制，被当成桌面侧的"没有弹层宿主"用了。
本仓的一号原则里就有"没取到 / 没有 / 没接是三句话"—— 这一条正是把后两句并成了一句。

### 4.2 桌面侧的两条独立读数

**API 面**（判法，可直接复跑）：

```bash
JAR=~/.gradle/caches/modules-2/files-2.1/io.github.compose-fluent/fluent-desktop/v0.1.0/*/fluent-desktop-v0.1.0.jar
JP=~/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2/bin/javap.exe
# 组件面有几颗（44 颗，含 Dialog / Flyout / MenuFlyout / CommandBarFlyout / Switcher / CheckBox …）
unzip -l "$JAR" | grep -oE "io/github/composefluent/component/[A-Za-z]+Kt\.class" | sed 's#.*/##; s/Kt\.class//' | sort -u
"$JP" -classpath "$JAR" -p -c io.github.composefluent.component.DialogKt          # FluentDialog 走 androidx.compose.ui.window.Popup
"$JP" -classpath "$JAR" -p io.github.composefluent.component.ContentDialogHostState  # show(...) 是 suspend，返回 ContentDialogButton
```

读到的形状：

- `FluentDialog(visible: Boolean, size: DialogSize, popupProperties: PopupProperties, content: @Composable …)`
  —— 由一个 `Boolean` 驱动，引用 `androidx.compose.ui.window.Popup` / `PopupProperties`。
- **`ContentDialogHostState`**：`show(标题, 主钮, 副钮, …, size): ContentDialogButton` 是 **suspend**，
  内部持 `Mutex` + `currentData`，**返回用户按了哪一颗**；配套 `ContentDialogHost(state)` 与
  `LocalContentDialog`（`ProvidableCompositionLocal`）。**这正是"移除收藏先确认"要的那一颗件**：
  一句 `val choice = host.show("移除这一张？", "移除", "取消")` 就能拿到三态结果。

**实测面**：`windows-r1f-s0-spike-2026-10-02.md:43,:50` —— S0 那轮的探针窗
（`desktop/src/main/kotlin/com/venera/desktop/SpikeWindow.kt`，`:59` `FlyoutContainer` /
`:62` `MenuFlyout` / `:65` `FluentDialog`）真组合了它们，运行期 `S02_COMPOSED_OK`、
零 `NoSuchMethodError`，截图核对记录是「Flyout 弹层、菜单项、**对话框**、侧栏汉堡按钮全部成形」。

⇒ **后果**：收藏域写侧（移除 / 清空）与设置页的确认层**没有结构性障碍**。仍然成立的只剩材质那一条
（实底，不带模糊）。口径已按这个改正：`DesktopGalleryFavoritesPane.kt` 类注释里那句"桌面今天没有弹层宿主"
换成了上面这段真实形状并指明原因为何（**只改注释，代码一行未动** —— 这一屏今天仍然只读，
但挂的原因从"拿不到宿主"改成了"这一批没做那一层"）。`DesktopDomains.kt` 里设置域那格本来就写的是
"缺那颗能改的设置页"，讲的是写侧而不是宿主，不用动。
至于这颗确认层与那颗按钮到底哪一批加 —— 那是收藏域/设置域各自的账，不在本批所有权里。

### 4.3 还没证的一小条

CMP desktop 的 `Popup` 是**同窗图层**还是**独立 HWND** —— 不影响 §4.2 的结论，
因为 `selfShot` 除了窗口矩形那张，还同屏再写一张 `$path.full.png` 对照（`VeneraDesktop.kt:381` 那段），
两种情形各有产物。要定它只需一次"窗口在前台"的 `-Pshot`（卡点见 §6）。
（缓存里 `androidx.compose.ui` 下没找到 desktop 变体的 jar，所以这一条没法用 `javap` 直接读出来，没硬凑。）

---

## 5. 新发现：中文读数经 `:desktop:app` 落文件原本一直是坏的

修前（`_qa/s3-badvalue.log`，同一条 `-PstartDomain=NOT_A_DOMAIN`）：

```
D_M-HM-!M-VM-$M-oM-?M-=M-oM-?M-=M-oM-?M-=M-oM-?M-= --start-domain M-oM-?M-=...  =NOT_A_DOMAIN...
```

只有 ASCII 段（`--start-domain`、`NOT_A_DOMAIN`、`GALLERY`、`DISCOVER`…）活下来，
中文全成了 `U+FFFD`（`cat -v` 下的 `M-oM-?M-=`）。

成因：JDK 18+ 的 `System.out` 跟的是 **`stdout.encoding`**，中文 Windows 上默认 GBK；
而 Gradle 按 UTF-8 解码子进程输出。两端编码不一致 ⇒ 取证链的**产物本身**半坏。

修法（`desktop/build.gradle.kts` 的 `app` 任务）：

```kotlin
jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
```

修后同一命令：

```
D_取证参数 --start-domain 认不出（值=NOT_A_DOMAIN）—— 不退到默认域：默认域那张图会顶着目标域的文件名成为证据
D_取证参数 可选值：GALLERY／图库 DISCOVER／发现 SEARCH／搜索 FAVORITES／收藏 ARTISTS／画师 COMIC／漫画 SETTINGS／设置
```

⚠️ **只加在 `app` 这一颗任务上**。`spike` / `ime` / `decodePerItem` 那几颗 JavaExec 没加，
将来它们要打中文读数会踩同一个坑。为什么以前没暴露：S0 的探针窗只打 ASCII（`S02_COMPOSED_OK`），
而 `D_` 这一族读数的判读大多是在 adb logcat 侧做的（那边编码另算）。

---

## 6. `-Pshot` 那一环：抬窗已实现（a 路），但**越不过去**；替代取证路已验证

用户拍板走 a（抬自己这扇窗）。实现落在 `VeneraDesktop.kt` 的 `selfShot` + `raiseOwnWindowForShot`，
Win32 那半边在 `MicaProbe.kt` 新增 `setTopmost`：

- **两层都抬**：AWT 的 `setAlwaysOnTop(true)` + `toFront()`，再 `SetWindowPos(HWND_TOPMOST)`；
  后者**回读 `GWL_EXSTYLE` 的 0x8 位**写进读数 —— 抬窗这件事要么有位要么没有，不接受"我调用了"。
- **只带 `SWP_NOSIZE|SWP_NOMOVE|SWP_NOACTIVATE`**，而且**不调 `requestFocus()`**：
  取证要的只是 z-order；把键盘焦点从用户正在打的字上抢走，与"截一张图"完全不成比例。
- **`finally` 里两层都落回**（`isAlwaysOnTop = false` + `setTopmost(hwnd, false)`）。
  选 `try/finally` 而不是链式收尾，是因为那三档"截不到"的 `return` 是 `runCatching` 里的
  **非局部 return**，会绕过 `.also { }` —— 只解一半的话这扇窗会一直压着用户的屏幕。
- 判据 ⑮ `抢前台只发生在取证那一路` 钉四件：`frame.toFront()` 恰好一次且在 raise 内、
  `requestFocus` 全文件**零次**、`main()` 体内不许出现 `alwaysOnTop`、`selfShot` 里两层落回都在 `finally` 内。

### 6.1 抬窗的实测结果：位上了，窗没过来

```
D_截图 抬窗 AWT 报了 isAlwaysOnTop=true ｜ Win32 on=true SetWindowPos=1（非 0 才算生效）位已上=true exstyle 0x108→0x108
D_截图 无效：中心点 (768,498) 落在 hwnd=524874 标题「粤韵风华之驾校宝典20【高速逼停】_哔哩哔哩_bilibili - Google Chrome」
            类名「Chrome_WidgetWin_1」，本窗 hwnd=3541916 标题「Venera Desktop R1-F 最小闭环 #50168」类名「SunAwtFrame」
```

`exstyle 0x108→0x108` 说明那颗 TOPMOST 位**在 AWT 那一步就已经是 1**（0x108 = TOPMOST|WINDOWEDGE），
`SetWindowPos` 只是重复确认；而中心点仍归 Chrome。对照 `list_windows` 的几何：
Chrome 是 `(-8,-8,2576,1404)` 铺满整屏，本窗是 `(48,48,1440,900)` —— **它把那点盖住了**。
⇒ **a 路已实现但没有解决这一环**，`-Pshot` 今天仍然拿不到 PNG（用户正在播视频这种状态下）。
这条如实记下来，别写成"抬窗已修好截图"。

顺带一条被这次实测**加强**的旧判断：`selfShot` 那句"多半已锁屏/切到别的虚拟桌面"原来是**猜**的，
现在改成报出**谁**占着那点（`windowAtPoint` + `windowDescribe`，标题与类名）—— 上面那两行就是这么读出来的。
`isPointOnWindow` 也顺手把父链从一层走到六层（原来只走一层 `GetParent`，
CMP 桌面窗的画布嵌得更深时会把"其实是我们这扇窗"判成"不是"）。

### 6.2 已经验证可用的替代取证路：**窗口捕获（occlusion-safe）**，不动鼠标也不抢前台

`computer-use` 的 `get_window_state` 走的是 **Windows Graphics Capture**
（读数里 `nativeProvider=rust-wgc`、`framePool=Direct3D11CaptureFramePool…`、**`occlusionSafe=true`**）
—— 按 HWND 取窗，**被遮挡也拿得到**，不需要前台、不需要抬窗、不需要 `PrintWindow`。

本轮用它拿到两张图证：

| 图 | 内容 | 对上什么 |
|---|---|---|
| `-PstartDomain=SETTINGS` 那一跑（PID 4840） | rail 底部「设」高亮，pane 一行「设置 · 未实现 · 读数都在，缺那颗能改的设置页」，主区「设置 ｜ 取数已通 · 缺界面」+ 逐颗点名的三件（含"桌面是 getter-only"那句） | **`--start-domain=` 在运行期真生效**（不只是 stdout 与判据） |
| 孤儿窗（PID 34056，旧构建） | 图库·今日：三站并行墙 + 精选推荐 + 每站 12s 预算 | 上一批的图库域仍然正常；也见 §6.3 那个坑 |

⇒ 建议把这条写进 §8 那侧的取证口径：**要看某一域长什么样，起窗 + 窗口捕获**，
不要再跟 z-order 较劲。`-Pshot` 那一条留着（它在窗口真的在前台时仍然有用，且它拒绝产出伪证的行为是对的）。

### 6.3 ⚠️ 孤儿窗：本轮差点据此误判"参数没生效"

第一次看图时屏上是**图库**，而 stdout 明明写着"认成「设置」"—— 当时几乎要判"接线有 bug"。
真相是那颗窗是**上一轮会话留下的孤儿 java 窗**（旧构建、不带参数），
而我那一跑的窗根本没在列表里。停掉后台任务后它还在（`taskkill /PID <pid>` 才清得掉）。

- **判法：按标题里的 PID 对**。`APP_TITLE` 带 `#${pid}` 正是为此（`VeneraDesktop.kt:57` 那条注释写着：
  标题一样时 `FindWindowW` 匹配到哪一扇全凭运气）。**先看 PID 再看内容**，别先看内容。
- 与旧账同族：「别假设"我打过包 = 设备上就是那份"」（2026-09-29 那条）。
- 本轮也顺手量到：`taskkill` 的中文输出走的是 GBK，在我的 shell 里同样是乱码 —— 与 §5 同一类成因，
  但那不是本仓的产物，不改。

**这一条不做也不影响本批的判据与产物**：参数、退出码、读数三样都有 stdout 证据，
窗口内容另有 §6.2 的图证；只有"应用自己截的那张 PNG"在这一台机器上、这种状态下拿不到。

---

## 7. 验证读数（实跑，非估计）



| 命令 / 判法 | 结果 |
|---|---|
| `:desktop:compileKotlin` + `:desktop:test` | **239 用例 / 0 失败 / 0 错误**（31 颗类；本批 233 → 239，净 +6）。汇总判法：读 `desktop/build/test-results/test/*.xml` 的 `tests/failures/errors` 属性求和，**不是**数 `@Test` 行数 |
| 故意红一次（调用点改成写死默认域） | `DesktopDomainNavigationTest > 起始域参数必须真被交给应用根 FAILED`，`GRADLE_EXIT=1`；改回复绿 |
| `:desktop:app -PstartDomain=NOT_A_DOMAIN` | 两条中文读数逐字可读 + `BUILD FAILED`，任务报 `finished with non-zero exit value 1`，**没开窗、没产图** |
| `:desktop:app -PstartDomain=SETTINGS -Pshot=…` | 「认成「设置」」通了；抬窗读数 `位已上=true exstyle 0x108→0x108`，但中心点仍归 Chrome ⇒ 如实报"无效"，**没有 PNG**（§6.1） |
| `:desktop:app -PstartDomain=SETTINGS` + 窗口捕获 | **图证拿到**：屏上是设置坦白页（rail「设」高亮 + 主区逐颗点名三件）⇒ 参数在运行期真生效，不只是 stdout 与判据（§6.2） |
| 窗口对身份 | 按标题里的 `#${pid}` 对（`APP_TITLE` 带 PID 就是为这个）；第一次看图时误把**上一轮留下的孤儿窗**当成本轮那扇，差点判成"接线有 bug"（§6.3） |
| Android 侧 | 一行未动（`git status` 只有 8 颗 `desktop/` 文件）。`:app:testDebugUnitTest` 那份 115 类 / 876 颗是 **Android 线自己那批**的数，不是本轮复跑的 |

## 8. 提交与所有权

按 §9 的规矩：提交只 `git add desktop/`（+ 本批与手册文档），**不碰 `app/`**。
本批不含 push。
