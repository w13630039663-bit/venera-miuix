# Venera Windows 桌面版可行性评估（含路线对照与改动面清单）

> 状态：**评估完成 / 未开工 / 待拍板 P1–P6**
> 日期：2026-10-01　分支：`compose-migration`
> 范围：**只评估 Windows 桌面版。不考虑 Linux / macOS**（因此上游 CI 里 `Build_Linux`/`Build_MacOS` 那类跨平台包袱、以及"Linux 无障碍不支持"这类限制都不在本文件的决策范围内）。
> 本文件是评估产物，**不含任何已实施改动**；`FREEZE-STATEMENT.md` 未追加豁免，保护域未申请。

---

## 一、结论摘要

| | 观感档位 | 成本（agent 天） | 逻辑层复用 | 画廊能否跟着上桌面 | 会不会动 Android 构建 | 会不会被 Android 演进拖死 |
|---|---|---|---|---|---|---|
| **R1-F：Compose Desktop + compose-fluent-ui + WindowStyler** ← 推荐 | **75–85%** | **26–37** | 0 行改动 | ✅ 自动 | ⚠️ **会** | 不会 |
| **W：回到 Flutter 分支 + fluent_ui** | 55–70% | 46–72（**W0 单独 1–2 天**） | 0 行改动 | ❌ 19876 行从零新写 | ❌ 不会 | **会**（两套产品线） |
| **R2：原生 WinUI3（C#/XAML）** | ~95% | 40–58 | 32662 行重写为 C# | ❌ 随整体重写 | ❌ 不会 | **一定会** |
| R1：Compose Desktop 自造 Fluent | ~45% | 15–25 | 0 行 | ✅ | ⚠️ 会 | 不会 |

**推荐：先做 W0（1–2 天，不锁死路线、顺手采对账语料），再打 R1-F 的 S0-1 版本矩阵（它是 R1-F 唯一会动 Android 构建产物的一步，撞"Android 不能回归"硬线，必须排第一而不是最后）。**

三个支撑判断的实测事实：

1. **源脚本引擎可以按规格复刻，不是考古。** 上游有逐方法签名的 API 规格文档（`doc/js_api.md` 512 行），且本地 `master` 与上游的 `doc/comic_source.md` 739 行 **diff = 0**（fork 没改协议）。另外上游有 **`--headless` 无 UI 模式**，可直接在 Windows 上产出结构化 JSON 当**跨实现对拍 oracle**。（第二节）
2. **Compose Desktop 的真 Fluent 控件与真 Mica 都有现成依赖**，不是要我们自己仿：`compose-fluent/compose-fluent-ui`（737★、Apache-2.0、有 `fluent-desktop` 构件、实测 45 个组件）+ `com.mayakapps.compose:window-styler`（0.3.2，`WindowBackdrop.Mica/Acrylic/Tabbed`）。（第三节）
3. **Windows 版进不进得了这个仓库根本不是问题**：`origin` 就是本仓库，CI 里 `Build_Windows` job 已经在（`.github/workflows/main.yml:117-139`），上游 v1.6.3 官方 release 就在发 `Venera-1.6.3-windows-installer.exe`。（第四节）

---

## 二、源脚本协议：可复刻性（本项目的心脏）

### 2.1 规格文档清单

| 文档 | 行数 | 内容 | 对移植的意义 |
|---|---|---|---|
| `doc/js_api.md` | **512** | 运行期 API **逐方法签名** | 新宿主的功能清单，可直接打勾验收 |
| `doc/comic_source.md` | 739 | 源作者侧契约（`init`/`Account`/Explore/Category/Search `load`+`loadNext`/Favorites 6 法/Details/Settings/Translations） | 我们消费的字段形状权威定义 |
| `doc/headless_doc.md` | 180 | `--headless` 命令与**输出格式** | **对账 oracle** |
| `doc/import_comic.md` | — | 本地漫画导入格式 | Windows 侧照用 |

`js_api.md` 定义的方法面（新宿主必须逐条满足）：

- **Convert（16）**：`encodeUtf8` `decodeUtf8` `encodeBase64` `decodeBase64` `md5` `sha1` `sha256` `sha512` `hmac` `hmacString` `decryptAesEcb` `decryptAesCbc` `decryptAesCfb` `decryptAesOfb` `decryptRsa` `hexEncode`
- **Network（10 + 全局 `fetch`）**：`fetchBytes` `sendRequest` `get` `post` `put` `delete` `patch` `setCookies` `getCookies` `deleteCookies`
- **Html**：`HtmlDocument`(4) / `HtmlElement`(13) / `HtmlNode`(3)
- **UI（7）**：`showMessage` `showDialog` `launchUrl` `showLoading` `cancelLoading` `showInputDialog` `showSelectDialog`
- **Utils**：`createUuid` `randomInt` `randomDouble` + `console`
- **Types（6）**：`Cookie` `Comic` `ComicDetails` `Comment` `ImageLoadingConfig` `ComicSource`

### 2.2 决定性事实：源脚本不需要浏览器内核

| 探测项 | 实测 |
|---|---|
| `XMLHttpRequest` 在 34 个源里出现 10 次 | **全部是 HTTP 头字符串** `"X-Requested-With": "XMLHttpRequest"`，0 次是 API 调用 |
| `window.` / `navigator.` / `crypto.` / `TextEncoder` / `WebAssembly` | **各 0 次** |
| `document.querySelectorAll`（12 个文件、最多 `ehentai.js` 35 处） | 对象来自 `let document = new HtmlDocument(res.body)`，是**自家 Jsoup 垫片**（`engine/JsHtmlHandler.kt:248-369`，16 函数） |
| `fetch` / `setTimeout` | `venera-init.js:620,19` 自己定义的垫片，走宿主 `http` / `delay` |
| Jsoup 特有伪类 `:contains` `:matches` `:has` `:eq` | **全 0** → 换任何标准选择器引擎（AngleSharp/自研）都不会静默丢结果 |
| `async` / `await` / 可选链 | 448 / 600 / 356 → **不支持 async/await 的引擎（Rhino、Nashorn）直接出局** |

### 2.3 `--headless`：跑在目标平台上的对账 oracle

`master:lib/main.dart:22` 见 `--headless` 就 `runHeadlessMode(args); return;`（不进 `runApp`、不初始化 `windowManager`）；`lib/headless.dart` 244 行；命令 `webdav up|down`、`updatescript all`、`updatesubscribe [--update-comic-by-id-type <id> <type>]`；**所有输出是 `[CLI PRINT]` 前缀的 JSON**（固定 `status`/`message`，带 `data`；`Progress`/`ProgressError` 样例在文档 `:105-150`）。

三层价值：① 证明"JS 引擎在无 UI 下跑通真源"在上游已是**产品级事实**（我先前把它当待验假设，现已降级）；② 给 JVM/C# 新宿主一个**同 OS、同真源**的 ground-truth 采集器，比从 Android 侧录语料强；③ `updatesubscribe` 说明追更在 Flutter 侧有真入口，而 compose 侧**调度链是接通的**（`VeneraApp.kt:54-63` 随 `followUpdatesFolder` 启停 `FollowUpdatesScheduler.enable/disable`），**缺的是页面入口**——`AndroidFollowUpdatesScreen`（`feature/FollowUpdatesScreen.kt:56`）全仓只有它自己的声明和一处注释提及，没有任何导航指向它，所以用户没法指定要追更的收藏夹，功能事实上够不着。移植时该照前者。（更正：我先前写的是"`FollowUpdatesScheduler` 全仓无调用者=半接线"，那是错的。）

### 2.4 引擎选型（Windows JVM）

| 候选 | 判定 |
|---|---|
| **GraalJS**（`org.graalvm.polyglot:js`，纯 jar） | 主选。ES2022、async/await 完整；**微任务排干语义必须实测**（宿主 `eval` 回到 host 边界时是否自动跑 job） |
| **Node.js sidecar** | 保底。V8 + 真事件循环=该坑直接归零；代价 +~70MB 与进程管理 |
| `app.cash.quickjs:quickjs-jvm` | ❌ 0.9.2 目录无任何原生构件，且本仓 `app/build.gradle.kts:146` 注释自陈它"无 Promise 微任务泵" |
| `app.cash.zipline` | ❌ Maven 模块清单**无 windows 目标**（只有 macos/linux/ios/tvos/android/jvm/js） |
| Rhino / Nashorn | ❌ 无 async/await（2.2 的硬门槛） |
| JCEF | 只在"CF 也走 JCEF"时才考虑；`CefMessageRouter` **不能同步返回 JS 调用值**，与本仓 `venera-shim.js` 的同步 `sendMessage` 语义冲突 |

**两条必须一起搬的既有约束**（漏了就是静默功能崩）：① `setTimeout` 注入——`venera-shim.js:22-43` 记过 `sendMessage→setTimeout→sendMessage` 无限递归事故，受影响源点名 `copy_manga / copy_manga_multi_accounts / hot_manga / mxs`；② `getPlatform()` 现在硬编码 `"android"`（`VeneraJsEngine.kt:489`），**桌面侧继续返回 `"android"`** 并用单测锁住，避开源脚本里的平台分支。另外 Gson 信封的 `serializeNulls` 语义（`VeneraJsEngine.kt:51-66`）与 cookie 域名归一化（`:545-604`，注释记载"静默整批报废过一次"）都要原样保留。

---

## 三、R1-F 的两个依赖：实测读数

### 3.1 `compose-fluent/compose-fluent-ui`

737★、**Apache-2.0**、未归档、最近提交 2026-08-18、有官方文档站；Maven Central 上**直接有 `fluent-desktop` / `fluent-android` / `fluent-icons-extended`**（不用自己 build）。目标平台含 desktop(Linux/macOS/**Windows**)。

`commonMain/component` 实测 **45 个组件**，其中对我们有意义的是 Android 侧现为 0 的三样能力的原生形态：`ContextMenu`（**desktopMain 有专门实现**）、`PlatformScrollBar`（skiko 实现）、`NavigationView` / `SideNav` / `TopNav` / `CommandBar` / `CommandBarFlyout` / `MenuFlyout` / `MenuBar` / `Flyout` / `Popup` / `TooltipBox` / `Dialog`（desktop 实现）/ `InfoBar` / `Expander` / `FlipView` / `TabView` / `SegmentedControl` / `SelectorBar` / `PipsPager` / `BreadcrumbBar` / `AutoSuggestBox` / `ProgressRing` / `ProgressBar` / `ListItem` / `GridViewItem` / `Badge` / `ComboBox` / `Dropdown` / `TextField` / `CheckBox` / `Switcher` / `RadioButton` / `Slider` / `RatingControl` / `CalendarView` / `ColorPicker` / `LiteFilter` / `Icon` / `FontIcon` / `Text` / `Layer` / `Material` / `Elevation`。

- **不会撞我们最脆的雷**：`fluent-desktop` 的 pom 只拉 `org.jetbrains.compose.foundation:foundation-desktop:1.8.2` + `ui-util` + `icons-core` + `kotlinx-datetime` + `haze`，**完全不引用 material3** → 不参与 `gradle/libs.versions.toml:9-14` 那条"miuix 把 material3 顶到 1.5.0-alpha22、编译期/运行期不一致就 `NoSuchMethodError` 闪退"的版本战争。
- **必须接受的风险**：README 原文 `This library is experimental, any API would be changed in the future without any notification`，且 `there are lots of hard-coding and workarounds in our source code`；正式版**至今只有 v0.1.0（2025-08-10）**一个，之后一年只动仓库不发版 → 要用新东西得走 `-SNAPSHOT` 或自建。缓解：**一开始就用我们自己的组件契约包住它**（不把它 API 散进 69 个 miuix import 文件），并预备按 Apache-2.0 fork 进来自己维护。
- 它是拿 Compose 1.8.2 / Kotlin 2.2.0 编的，我们要跑在自己的 CMP 线上 → 二进制兼容是 S0-2。

### 3.2 `com.mayakapps.compose:window-styler`（0.3.2，214★）

README 明确提供 `WindowBackdrop.Mica`（**Win11 21H2+**）/ `Acrylic`（Win10 1803+）/ `Tabbed` / `Transparent(color)`，外加标题栏色、题注色、圆角控制，并写了降级链 `Tabbed → Mica → Acrylic → Transparent`。**警告原文要点：透明一旦被 hack 进窗口就不可逆**（S0-3 要专门验这条）。仓库最近推送 2025-02-26（约 19 个月未更新）。

⚠️ 一个易混点：`compose-fluent` 自己的 `Mica.kt` 是用 **haze 做的应用内模糊**（`fluent-desktop` 的 pom 里**没有** window-styler，该依赖只在它自己的版本目录里声明）。所以**控件语义找 compose-fluent，桌面系统材质找 WindowStyler，两件事分开取用**。

---

## 四、"能不能并入我们的 GitHub 仓库"——仓库拓扑与产物纪律

**已经是同一仓库**：`origin = github.com/w13630039663-bit/venera-miuix`，`master`（Flutter 全平台）与 `compose-migration`（Android Compose）是同仓库两条分支。master 的 CI 已有 `Build_Windows`（`:117-139`）且 Release job 依赖它（`:192,209`）；`windows/` 有 19 个文件（CMake + runner + `build.py` + Inno Setup `.iss`）。上游 v1.6.3/1.6.2/1.6.1 的 release 都在发 `Venera-*-windows-installer.exe` 与 `-windows.zip`。

**产物纪律（本轮新查）**：`.git` 体积 22M，`git ls-files` 里 **apk/exe/zip = 0 个** —— 工作树里那几个 `venera-*.apk` 是**未跟踪**产物。结论：**Windows 的 exe/zip 一律不进仓库，走 Releases**（与上游做法一致）。Flutter 路线还要注意 `build.py` 会**就地改写 `windows/build.iss`** 并下载 `ChineseSimplified.isl`，这两样都不该提交。

---

## 五、既存缺陷清单（与移植路线无关，现在就该知道）

### 5.1 Windows 的 Cloudflare 过盾走的是代码自己承认不可靠的那条路

`master:lib/network/cloudflare.dart`：`:122-123` 注释原文 `windows version of package flutter_inappwebview cannot get some cookies` / `Using DesktopWebview instead`；但 `:124` 的 DesktopWebview 分支判据是 **`if (App.isLinux)`**，于是 Windows 落进 `:162` 起的 `else` 用 `InAppWebview.getCookies(url)` 找 `cf_clearance`——**正是注释说拿不到的那条**。注释与代码不一致。这也解释了 compose 侧给 SauceNAO 装了只读探针后**一直没有读数**。**Windows 过盾从来就是缺的，不是桌面化新增风险。**

### 5.2 【新发现】`UI.*` 协议在现网 Android 侧有 5 个方法是空壳

`engine/VeneraJsEngine.kt:631-654`：`showMessage` 与 `launchUrl` 有真实现；而

```
"showLoading" -> 1              ← 返回自造的 id
"cancelLoading" -> null
"showInputDialog" -> null
"showSelectDialog" -> null
"showDialog" -> null
// 注释原文：对话框类 API 暂未接入原生 UI：返回 null 让源走"用户取消"分支，避免 Promise 永久 pending 导致整条调用链挂死
```

源脚本**真在调**：`UI.showDialog` **5 次 / 5 个源**（`ccc.js`、`copy_manga_multi_accounts.js`、**`ehentai.js`**、**`jm.js`**、`wnacg.js`）、`UI.cancelLoading` 3 次、`UI.showInputDialog` 1 次、`UI.showLoading` 1 次、`UI.launchUrl` 2 次、`UI.showMessage` 20 次/7 文件。

→ 形状正是"接口接了、返回自造值、源以为挂上了 loading 其实什么都没显示"。注释的取舍理由（防 Promise 永久挂死）成立，但**处置应该是接原生对话框或让源可见地失败，不是假装用户取消**。jm 与 ehentai 两个主力源受影响。**建议单独开一轮修，不混进移植**（P3）。

### 5.3 【新发现】compose 线完全没有本地化机制

| | compose-migration | master（Flutter） |
|---|---|---|
| `res/values-*/strings.xml` | **0 个**（`res/` 下无任何 `values-*` 目录） | — |
| `assets/translation.json` 引用 | **0 处** | 有（1116 行） |
| 用 `.tl` 的源文件 | — | **62 个 dart 文件** |
| 硬编码中文串的 Kotlin 文件 | **218 个** | — |

即：**界面文案全中文写死在代码里**。只考虑 Windows + 使用者是中文用户时这不致命，但它必须被写明为既有事实，否则会在移植中途变成"要抽 218 个文件的字符串"的意外工程。R1-F 继承这个缺口；W 继承 master 的完整 i18n。（P4）

### 5.4 源脚本不在仓库里，首启依赖外网

master 与上游 `assets/` 只有 6–7 个文件、**无任何 .js 源脚本**；内置源运行时从 CDN 拉（`master:lib/foundation/appdata.dart:405`、`lib/init.dart:119` → `https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/index.json`）。而本机直连该域名不通、只有 `-x http://127.0.0.1:7890` 通（系统代理开关是关的）。compose 分支已有现成离线兜底：`app/src/main/assets/sources/`（34 个 .js + `index.json`；`source/ComicSourceManager.kt:521` 自陈"离线兜底，与官方 index.json 逐字一致"，`:664` 另记官方 index.json **33 条记录只有 32 个唯一 key**（`copy_manga.js` 与 `copy_manga_multi_accounts.js` 撞 key）及处理）。**推论：Windows 版必须有代理设置项**（对应现有 `java.net.Proxy` 那套，`VeneraNetworkClient.kt:82-86`），否则开发期连一条源都验不了。

### 5.5 【新发现】"检查更新"会把用户导向错误的产物

compose 侧 `data/update/AppUpdateChecker.kt:33` 的发布通道指向本仓库 releases（注释：`本包的发布通道（检查更新与"去下载"都指向它）`）。Windows 版若原样继承，会出现"检查更新 → 提示下载一个 APK"。→ 移植时必须新增**按平台选产物**的判据（且要与第四节"产物只进 Releases"的纪律对齐）。

---

## 六、本轮复审补上的遗漏项

| # | 遗漏 | 实测/结论 |
|---|---|---|
| 1 | **compose 无 i18n** | 见 5.3（218 文件硬编码中文 vs master 1116 行译典 + 62 文件用 `.tl`） |
| 2 | **`UI.*` 协议空壳 5 个方法** | 见 5.2（含 jm、ehentai） |
| 3 | **检查更新的产物选择** | 见 5.5 |
| 4 | **桌面导航形态的真实改造面没量过** | `bottomBarClearance` 实测 **20 文件 / 32 处**。compose-fluent 有 `NavigationView/SideNav/TopNav`，桌面换侧栏就是这个数；而记忆里"大屏 B 方案（左 Rail）被否决"的理由正是这个契约面 + 保护域 `Navigation.kt`。**桌面不必保留 Android 的观感口径**，但"桌面侧栏 / Android 底栏"是否允许分叉要拍板（P5） |
| 5 | **6 个 Activity 到 Windows 窗口模型的映射没定** | Android 靠跨 Activity 拿系统预测式返回与 `blurBehindRadius`（`VeneraSubActivityBase.kt:65-76`）：Main / Settings / SettingsSub（16 个设置子页共用）/ GalleryPost / GalleryArtistProfile / CloudflareBypass。桌面若保持 6 个窗口，则**跨窗口玻璃必然没有**、任务栏归组与焦点管理变新问题；若收单窗口，返回栈与"独立页"语义要重做。**建议：阶段 1–5 一律单窗口**，把 `CloudflareBypass` 交给 helper/WebView2，其余内化为页面。（P6） |
| 6 | **我把两条"能做到"错记成"做不到"** | ① 系统级 Mica/Acrylic —— WindowStyler 可达（3.2）；② **屏幕防窥**——compose 有真实现（`FLAG_SECURE` 6 处，`MainActivity.kt:160` 注释说明"必须挂在每个 Activity"），Windows 对应物是 `SetWindowDisplayAffinity(WDA_EXCLUDEFROMCAPTURE)`，一次 JNA 调用即可，属**可实现**而非降级。生物识别：compose 侧 `androidx.biometric/BiometricPrompt` **0 处**，不构成缺口 |
| 7 | **跨端数据迁移通道其实已经存在** | `sync/BackupManager.kt:57,72` 导出**带 `version` 的 JSON**（`FavoriteBackupRows.CURRENT_VERSION`），且仓库里已有 `sync/DartStringHash.kt` —— 证明"跨实现字符串哈希分裂"这个坑**已经被处理过**（为了兼容 Flutter 版收藏夹表名）。→ 迁移走**备份 JSON**而不是搬数据库；`LocalFavoriteDatabase.kt:20-25` 那条 `sourceKey.hashCode()` 注释（与 Dart 不兼容）在 R1-F 的 JVM↔JVM 路径上天然安全 |
| 8 | **Windows 特有能力一直没人列进来** | 拖拽导入 CBZ/图片到窗口、文件关联（`.cbz` "打开方式"）、任务栏跳转列表/系统托盘、快捷键表与焦点导航（Android 侧现为 0）、右键上下文菜单（现仅长按 `DropdownMenu`）、悬停态（全仓 `isHover/hoverable/PointerIcon` **0 命中**）。这些不是移植成本而是桌面版该有的东西，且其中三项在 compose-fluent 里是现成组件 |
| 9 | 窗口尺寸记忆与单实例 | AWT/Compose Desktop 无第一方单实例锁 → 需文件锁或 JNA 命名互斥量；窗口尺寸/位置要持久化（Android 无此概念） |
| 10 | 字体与清晰度 | 全仓**零字体缩放处理**（无 `fontScale` 读取、无自持 `.ttf`）；桌面 Skia 无 ClearType，150%/200% DPI 下正文发虚是真验收项（S 项，非阻塞） |
| 11 | 磁盘缓存/数据目录位置 | 现落 `cacheDir`/`filesDir`（HTTP 缓存 `venera_http_cache`、画廊图 `gallery_img`、源数据 `filesDir/comic_source`）→ 桌面要重映射到 `%LOCALAPPDATA%`，并给用户可见的"打开数据目录"入口 |

---

## 七、R1-F 分阶段计划

### 阶段 0：Spike（否决项优先，任何一条不通就回来改路线）

| ID | 假设 | 判据（看什么算过） | 不通的后果 |
|---|---|---|---|
| **S0-1** | **版本矩阵**：`AGP 9.3.2 + Kotlin 2.4.10 + KMP + CMP + miuix-desktop + compose-fluent-desktop + material3 钉版` 同仓共存 | 一个 `FluentTheme { Text("hi") } }` 桌面窗口跑起来；**同时** `:app:assembleDebug` 的 APK 装真机、首页零回归；两边各跑 `dependencies` 对账 material3 解析结果 | **R1-F 整条作废**，退回 W 或 R2。R1-F 唯一会动 Android 构建产物的一步，故排第一 |
| **S0-2** | compose-fluent 跑在我们的 CMP 版本上（它是拿 1.8.2 编的） | 抽 8 个真用组件（`NavigationView`/`TextField`/`Flyout`/`MenuFlyout`/`ProgressBar`/`Switcher`/`TooltipBox`/`Dialog`）无 `NoSuchMethodError`；haze 与 miuix-blur/backdrop 同屏不打架 | 被迫锁 1.8.2 或降 Android Compose → 改选 W |
| **S0-3** | GraalJS 按 `js_api.md` 复刻后与上游一致 | 两级探针：`nhentai.js` 出 ≥10 条结果；`jm.js` 出详情 + 落盘**已还原**的图。**oracle**：同命令跑 `venera --headless updatesubscribe`，对 `[CLI PRINT]` JSON 逐字段对拍 | 心脏不通路线不存在；备胎 Node sidecar |
| **S0-4** | CF 过盾（5.1） | 拿到 `cf_clearance` 且后续请求 200、`ehentai` 出图 | 该源桌面标记不可用 + 给"浏览器登录后手动导入 cookie"的替代路径 |
| **S0-5** | **中文 IME** | 搜索框拼音打"禁漫"，候选窗落在光标下、可翻页、可上屏 | 不能降级（入口就是搜索框），不通即改路线 |
| **S0-6** | WindowStyler 真 Mica | `WindowBackdrop.Mica` 生效并随主题变色；并验"透明 hack 不可逆" | 退回 haze 应用内模糊，观感降档不否决 |
| **S0-7** | Fluent 档位映射 | `SurfaceMaterial{SOLID,LIQUID_GLASS}` 与 `NavigationBarStyle` 两轴重映射；`isRuntimeShaderSupported()` 在 desktop 的返回值明确——现有 **4 个消费点**会因它返回 false **整段静默跳过玻璃**（`VeneraTopAppBar.kt:122,199`、`VeneraTopBarPill.kt:66`、`InteractiveHighlight.kt:49`），按"降级必须可见"必须要么生效要么报错 | 桌面材质默认关并写明 |
| **S0-8** | 长列表与内存 | 条漫单列数千图的滚动帧时间与内存；`zoomable-image-coil3` 有无 desktop 变体（`flick-desktop`/`zoomable-desktop`/`sub-sampling-image-desktop` 已确认存在，coil3 那支未见） | 阅读器改分页制 + 超大图可见提示 |
| **S0-9** | 打包分发 | `nativeDistributions` 出 exe；任务栏真图标；数据落 `%LOCALAPPDATA%`；**含 `modules("jdk.accessibility")`**；**代理设置项在**（5.4）；产物只进 Releases（第四节） | 影响每阶段验收体验 |

顺序：S0-1 → S0-2 → S0-3 → S0-5 → S0-6 → S0-4 → S0-7 → S0-8 → S0-9。

### 阶段 1–6

| 阶段 | agent 天 | 要点 / 必须做 | 明确不做 |
|---|---|---|---|
| 1 壳层与首屏 | 4–6 | `FluentTheme` + `NavigationView` + WindowStyler；**右键/悬停/焦点/系统滚动条直接用库**；窗口尺寸记忆 + 单实例；代理设置项 | 玻璃、下载、阅读器、画廊 |
| 2 心脏接通 | 6–8 | 按 `js_api.md` 逐条实现宿主（**含 5.2 那 5 个 UI 对话框方法在桌面一次接齐**）；SQLite 兼容壳；`KeyValueStore` 收口 19 处 `getSharedPreferences` | 图片还原质量、CF |
| 3 图片与阅读 | 5–7 | `ImagePipelinePolicy.kt:208-250` 纯矩形重排机械搬（11 个单测桌面重跑绿）；下载逐图写盘 + 断点续下（纯 `java.io`，直接复用）；`ComicStorageRoot` 桌面语义 | 双页/RTL、子采样长图 |
| 4 收藏/同步/守卫/统计 | 4–5 | `LocalFavoritesManager` 原样（靠兼容壳）；WebDAV 纯 OkHttp 可搬；统计页自绘 Canvas 走 Skia；`gallery_tags_79415.sqlite` 资产解出；追更照 `updatesubscribe` 语义做**应用内**周期检查 | 系统级排程、开机自启 |
| 5 画廊全量 | 4–6 | **R1-F 相对 W 最大的省钱处**：`gallery/ui` 12477 行 + `gallery/domain` 33 文件本来就在 Kotlin 里。GIF 不能播则首帧+角标 | mp4 内嵌播放 |
| 6 Fluent 收口 + 分发 | 3–5 | 快捷键表/焦点导航/右键全量；防窥（`SetWindowDisplayAffinity`）；5.5 的检查更新按平台选产物；第六节 8 条 Windows 特有能力落位；降级文案一次性定稿 | 触摸板惯性、讲述人适配 |
| **合计** | **26–37**（含阶段 0） | 墙钟：主链路（0–3）6–9 周，全量 9–14 周 | |

**压不动的四件事**：工具链前置（本机 `flutter`/`dart`/`cmake` 全部 command not found、无 VS 2022 痕迹，**只有你能点安装器**）；验收回合（历史特征：一个批次 5–8 轮，首页推荐区打过 8 轮）；外部站点行为（CF 是否出挑战、上游改算法）；跨会话文档回写。

**可压缩项**：若放开"我用计算机操作通道回归我自己写的 Windows 界面"（Android 侧仍按既有规矩只读不代操作），验收回合从 5–8 轮压到 1–3 轮，省 2–4 周。**这条要你点头（改的是"验收由谁做"的既有裁决），且能力本身要先实测，我不预先断言。**

---

## 八、降级 / 做不到清单（原则：每格必须可见，不留能点却什么都不做的控件）

| 项 | 原因 | 降级形态与文案 |
|---|---|---|
| 系统讲述人（Narrator） | 官方口径：Windows 无障碍走 **Java Access Bridge**（默认关，需 `jabswitch /enable` + `modules("jdk.accessibility")`），NVDA/JAWS 可达；讲述人未确认 | 不写"支持无障碍"，写"支持 NVDA/JAWS 读取，未适配系统讲述人" |
| 跨窗口模糊与预测式跨 Activity 返回 | 6-Activity 架构的系统能力 | 单窗口 + Fluent 导航转场；曲线沿用 material-motion 现参，不发明新效果 |
| 内嵌 mp4 播放 | JVM 无第一方播放栈；只覆盖画廊约 5% 帖子 | 按钮就叫「用默认应用打开」，不叫「播放」；首次触发播报原因 |
| GIF 动图 | `coil-gif` 依赖 Android `ImageDecoder`；skiko 动画解码待实测 | 不能播则**首帧 + 角标"动图（桌面版未播放）"**，绝不允许看起来像坏图 |
| "仅 Wi-Fi 自动播 GIF" | `ConnectivityManager`（`GalleryConnectivity.kt:4-27`）无 JVM 对应 | 桌面恒一档 + 设置页写明，不做猜测 |
| 触摸板惯性/相态 | AWT 只有离散步进 | 步进滚动，不自造惯性曲线 |
| 系统级排程（追更） | WorkManager 语义无对应；且 Android 侧的追更页 `AndroidFollowUpdatesScreen` 本就无导航入口（调度链是通的，见 §2.3） | 只做应用内周期检查，**不加"开机自启"开关** |
| 分享面板 / MediaStore 存相册 | Android 概念 | 复制链接 / 在资源管理器中显示 / 保存到 `%USERPROFILE%\Pictures\Venera`（播报绝对路径） |
| 字体缩放跟随系统 | 全仓现状即零处理 | 保持现状，**不新增"支持字体缩放"的承诺** |
| ~~系统 Mica~~ / ~~防窥~~ | **不是降级**，见 3.2 与第六节第 6 条 | — |

---

## 九、仓库结构建议（R1-F；三步分开，绝不合成一个大改）

**先记本仓的失败先例**：上次桌面化留的 `desktop/` 模块是 11 文件 1937 行**纯 Mock、与 Android 零共享**，最终当死代码删除，并在 `docs/rounds/venera-stage-plan.md:337,347,367` 留下决策 D-4"若真要 CMP 再单开阶段"。**那次失败的根因不是技术，是没先拆模块。**

1. **步骤 A（零风险）**：只抽**不含 Compose** 的层为 `kotlin("jvm")` 模块——先搬框架零依赖的 **76 文件 / 7922 行**（包名不变，同包跨模块合法）。移动 76、改写 0。护栏：APK 做 **dex 类清单逐字节对账** + 真机回归清单跑一遍。
2. **步骤 B（中风险）**：抽 `:platform` 接口（`Context`、13 处 `assets.open`、19 处 `getSharedPreferences` → `KeyValueStore`、`android.database.sqlite` → `SqliteAccess` 兼容壳）。Android 实现=现有代码原样搬、**行为不变**（有利条件：偏好已有单一事实源 `data/prefs/VeneraPreferences.kt`，52 个 `pref_*` 键 / 105 个 StateFlow）。每类接口一个 PR。
3. **步骤 C（高风险，单独 PR）**：UI 上 CMP——material3 钉版风险**只在这一刻发生**。先在 `:ui:shared` 放 `ui/tokens`（6 文件 1425 行，零 android import）+ 3–5 个组件建最小闭环验证双 target，再逐屏搬。

**顺手删死依赖**：`androidx.paging` + `paging-compose`（**`import androidx.paging` 全仓 0 处**，grep 到的 `Pager` 全是 `HorizontalPager`）、`app.cash.quickjs`（**0 处使用**）、`okhttp-dnsoverhttps`（DoH 只 import 未接线，`VeneraNetworkClient.kt:11,20-21` 自陈）。

**Gradle 雷**：`:app` 保持 `com.android.application`；`:ui:shared` 要双 target 就得变 KMP 库模块（AGP 9 下推荐 `com.android.kotlin.multiplatform.library`，**该插件 × AGP 9.3.2 × CMP 的组合官方文档不覆盖**，这就是 S0-1）；`kotlin-compose` 插件必须与 KMP 同版本；`buildFeatures.compose`、ABI splits、`unitTests.isReturnDefaultValues` 都是 Android-only；R8 keep 规则（WebView 桥 / Gson 反射）桌面侧无对应物，桌面不做混淆即可。

---

## 十、代码规模底账（本轮自测，作估算基数）

`app/src/main/java/com/venera/compose` = **264 个 .kt / 71172 行**。

| 分面 | 文件 | 行 | 说明 |
|---|---|---|---|
| 含 `@Composable` | **96** | **38510** | 换 Fluent 后端的接触面 |
| 纯逻辑（无 Composable） | 168 | 32662 | R1-F 下 0 行改动 |
| **框架零依赖**（无任何 `android`/`androidx` import） | **76** | **7922** | 步骤 A 的首刀 |
| `import androidx.paging` | **0** | — | 死依赖 |
| `app.cash.quickjs` 使用 | **0** | — | 死依赖 |
| `translation.json` / `strings.xml` | **0 / 0** | — | 5.3 |
| 硬编码中文串的文件 | **218** | — | 5.3 |
| `bottomBarClearance` | 20 | 32 处 | 第六节第 4 条 |
| 直 import miuix 的文件 | 69 | — | 其中 51 个在组件库之外 |
| assets | 46 文件 / 6.9MB | — | 含 36 个 .js、3.2MB 标签词典 sqlite、`opencc.txt`(3980 对)、`tags.json`(1.04MB) |

master(Flutter) 对照：`lib/` 143 dart / **59845 行**（components 27/11816、foundation 33/10682、pages 52/30317、network 8/2538、utils 20/3776）。**跨平台已就绪 = foundation+network+utils ≈ 14996 行**；`App.isDesktop/isWindows` 分支已覆盖鼠标滚轮（`components/scroll.dart:52`）、桌面滚动条（`:240`）、窗口边框（`window_frame.dart`）、桌面侧栏（`navigation_bar.dart:376`）、阅读器桌面分支（`reader/reader.dart:775-800`）、桌面长按延迟、Windows 心跳（`init.dart:87`）、WebView2 环境（`webview.dart:113`）。

---

## 十一、待拍板项

| # | 待定 | 我的推荐 |
|---|---|---|
| **P1** | R1-F（26–37 天、75–85%、功能不分叉，但动 Android 构建）vs W0（1–2 天、完全不碰 Android、但只有 Flutter 线功能面且画廊缺失） | **不互斥：先 W0 摸真桌面手感 + 采 headless 语料，再打 S0-1** |
| **P2** | 能不能把观感地基压在自称 experimental、正式版只有 v0.1.0 的 compose-fluent 上 | 接受，但一开始就用自己的组件契约包住它，预备 fork |
| **P3** | 5.2 的 `UI.*` 空壳（含 jm、ehentai）是否单独开一轮修 | **开**。它不属于移植，且形状正是你反对的"接了但什么都不做" |
| **P4** | 5.3 的 i18n 缺失——Windows 版要不要补 | 只考虑 Windows + 中文使用者 → **不补**，但把"界面仅中文"写进设置页说明，不做"支持多语言"的承诺 |
| **P5** | 桌面用 `NavigationView` 侧栏、Android 保持底栏——允许布局分叉吗 | 允许。桌面强行沿用底栏会同时撞上 20 文件 / 32 处契约和保护域 `Navigation.kt`，收益为负 |
| **P6** | Windows 窗口模型：单窗口 or 对应 6 个 Activity 的多窗口 | **阶段 1–5 单窗口**（跨窗口玻璃一定没有、任务栏归组与焦点是新坑）；CF 交 helper |

---

## 十二、验证方式

1. **Android 零回归**（R1-F 每步必跑，缺一不可）：`:app:compileDebugKotlin` → `:app:testDebugUnitTest` → `:app:assembleDebug`，汇报写明 BUILD SUCCESSFUL；APK 做 dex 类清单前后对比；真机回归清单（5 主 Tab / 详情 / 阅读器 / 下载 / 画廊 / 设置 / CF / 登录）由你在 PJZ110 上点，我只读日志与截图（裸 adb，`-s` 会被拦）。
2. **协议对账**：以 `js_api.md` 的 36 个方法为清单逐条打勾；以官方 `venera --headless` 的 `[CLI PRINT]` JSON 为 oracle 做逐字段对拍；JM 图与 Android 结果做**像素对拍（阈值 0，因算法是纯矩形搬运）**，11 个 `ImagePipelinePolicyTest` 用例在桌面重跑绿。
3. **Windows 验收**：每阶段一个可运行 exe，装上去点；静态检查不替代真桌面 QA。
4. **降级必须可见**：做不到/取不到数据的分支要么报错要么界面写明原因，**禁止静默交回原样、禁止留能点但什么都不做的控件**。

---

## 十三、关键文件锚点

- `doc/js_api.md`（512 行，master 与上游逐字相同）——新宿主的方法级规格
- `doc/headless_doc.md` + `master:lib/headless.dart`(244) + `lib/main.dart:22`——headless 命令与 `[CLI PRINT]` 契约（对账 oracle）
- `engine/VeneraJsEngine.kt`——`:631-654` UI 空壳；`:310-503` 22 桥方法；`:260-271` 异步包法；`:51-66` Gson null 语义；`:545-604` cookie 归一化；`:489` `getPlatform`
- `assets/venera-init.js:19,620,647` / `venera-shim.js:22-43,299-308`——垫片，**零改动复用**
- `data/network/ImagePipelinePolicy.kt:208-250`——JM 去块重排（与 `jm.js` 逐字同串）
- `ui/tokens/SurfaceMaterialPolicy.kt:82` + `components/venera/VeneraControls.kt:61`——Fluent 后端落点与"参数取交集"纪律
- `data/prefs/VeneraPreferences.kt`（52 键 / 105 StateFlow）+ `data/db/LocalFavoriteDatabase.kt:20-25`（`hashCode` 表名，JVM↔JVM 安全）
- `sync/BackupManager.kt:57,72` + `sync/DartStringHash.kt`——跨端迁移通道与已处理过的哈希分裂
- `gradle/libs.versions.toml:9-14`——material3 钉版注释（S0-1 靶心）；`app/build.gradle.kts:113-114,146-148`——要删的死依赖
- `master:lib/network/cloudflare.dart:122-124,162+`——5.1 缺陷落点
- `FREEZE-STATEMENT.md`——真要开工时在**末尾新开一节**记豁免，不改写历史批次记录

---

## 十四、外部读数（本轮核实来源）

- [venera-app/venera — doc/comic_source.md](https://github.com/venera-app/venera/blob/master/doc/comic_source.md)、[doc/js_api.md](https://github.com/venera-app/venera/blob/master/doc/js_api.md)、[doc/headless_doc.md](https://github.com/venera-app/venera/blob/master/doc/headless_doc.md)
- [compose-fluent/compose-fluent-ui（737★，Apache-2.0，desktop 含 Windows）](https://github.com/compose-fluent/compose-fluent-ui) 与[文档站](https://compose-fluent.github.io/compose-fluent-ui/)、[Maven 构件](https://repo1.maven.org/maven2/io/github/compose-fluent/)
- [MayakaApps/ComposeWindowStyler（Mica/Acrylic/Tabbed）](https://github.com/MayakaApps/ComposeWindowStyler)
- [Kotlin 官方：Compose Multiplatform desktop 无障碍（Java Access Bridge）](https://kotlinlang.org/docs/multiplatform/compose-desktop-accessibility.html)
- [Compose Multiplatform 兼容性/版本口径](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)
- [miuix（KMP，含 `miuix-ui-desktop`/`miuix-blur-desktop`/`miuix-shader-desktop`）](https://repo1.maven.org/maven2/top/yukonga/miuix/kmp/)、[Kyant0/AndroidLiquidGlass（含 `backdrop-desktop`）](https://repo1.maven.org/maven2/io/github/kyant0/backdrop/)
- [bdlukaa/fluent_ui（Flutter 侧 Fluent，4.16.1）](https://github.com/bdlukaa/fluent_ui)
- [app.cash.zipline / app.cash.quickjs 的 Maven 目录（核实无 Windows 目标）](https://repo1.maven.org/maven2/app/cash/zipline/)
- 上游 release 产物：`Venera-1.6.3-windows-installer.exe` / `-windows.zip`（GitHub API `repos/venera-app/venera/releases`）

---

## 附：我对本评估否掉的路线

- **R2（原生 WinUI3）**：观感可达 ~95%，但要 C#/XAML 写 40–55k 行（38510 行 Compose 全作废），`sourceKey.hashCode()` 这类 JVM 语义要跨语言重定，且按当前 15 commit/天 的节奏桌面会永久落后 2–3 批次。Kotlin/Native 导出 DLL 的子方案已否（OkHttp/Gson/Jsoup 无 K/N 目标）。
- **R1（Compose Desktop 自造 Fluent）**：复用率与 R1-F 相同，但要自己在 token 层仿 Fluent 形状，观感只到 45%，而 compose-fluent 已把这段路走完——没有理由自造（也违背"有第一方标准组件就照标准、不加码"）。
- **R3（Flutter + fluent_ui）整体路线**：逻辑层已跨平台是它的优势，但**画廊那 80 文件 19876 行要在 Dart 里从零写**，且每个后续功能落两遍；只有当 S0-1 否掉 R1-F 时才回到它。其中 **W0 档被保留为推荐的第一步**。
